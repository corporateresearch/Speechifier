import { useCallback, useEffect, useRef, useState } from "react";
import { fetchSentence } from "./api";

/**
 * Drives sentence-by-sentence streaming playback with word-level highlighting.
 *
 * - One <audio> element, fed one sentence of WAV at a time.
 * - Sentences are fetched on demand and cached; the next sentence is prefetched
 *   while the current one plays, so playback is gap-free on a CPU.
 * - A rAF loop maps audio.currentTime -> the active word index.
 *
 * Returns UI state ({ playing, sid, wid, loading, error }) and controls.
 */
export function usePlayer(doc, voice, speed) {
  const [playing, setPlaying] = useState(false);
  const [sid, setSid] = useState(-1); // active sentence index
  const [wid, setWid] = useState(-1); // active word index within sentence
  const [words, setWords] = useState([]); // word timings for the active sentence
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  const audioRef = useRef(null);
  const cacheRef = useRef(new Map()); // sid -> sentence data
  const inflightRef = useRef(new Map()); // sid -> promise
  const curDataRef = useRef(null); // data for the sentence currently loaded in <audio>
  const targetRef = useRef(-1); // sentence the user most recently asked for
  const wordCursorRef = useRef(0); // search hint for the rAF loop
  const rafRef = useRef(0);
  const genRef = useRef(0); // invalidation token for voice/speed/doc changes

  // Keep latest params reachable from stable callbacks.
  const paramsRef = useRef({ doc, voice, speed });
  paramsRef.current = { doc, voice, speed };

  // Create the audio element once.
  useEffect(() => {
    const a = new Audio();
    a.preload = "auto";
    audioRef.current = a;
    return () => {
      a.pause();
      a.src = "";
    };
  }, []);

  const stopRaf = useCallback(() => {
    if (rafRef.current) cancelAnimationFrame(rafRef.current);
    rafRef.current = 0;
  }, []);

  // Reset everything when the document, voice, or speed changes.
  useEffect(() => {
    genRef.current += 1;
    cacheRef.current = new Map();
    inflightRef.current = new Map();
    curDataRef.current = null;
    targetRef.current = -1;
    wordCursorRef.current = 0;
    stopRaf();
    const a = audioRef.current;
    if (a) {
      a.pause();
      a.removeAttribute("src");
      a.load();
    }
    setPlaying(false);
    setSid(-1);
    setWid(-1);
    setWords([]);
    setError(null);
  }, [doc, voice, speed, stopRaf]);

  const ensure = useCallback((index) => {
    const { doc, voice, speed } = paramsRef.current;
    if (!doc || index < 0 || index >= doc.sentence_count) return null;
    const cache = cacheRef.current;
    if (cache.has(index)) return cache.get(index);
    const inflight = inflightRef.current;
    if (inflight.has(index)) return inflight.get(index);
    const gen = genRef.current;
    const p = fetchSentence(doc.doc_id, index, voice, speed)
      .then((data) => {
        if (gen === genRef.current) cacheRef.current.set(index, data);
        inflightRef.current.delete(index);
        return data;
      })
      .catch((e) => {
        inflightRef.current.delete(index);
        throw e;
      });
    inflight.set(index, p);
    return p;
  }, []);

  // The rAF loop: highlight the last word whose start <= currentTime.
  const tick = useCallback(() => {
    const a = audioRef.current;
    const data = curDataRef.current;
    if (!a || !data) return;
    const t = a.currentTime;
    const words = data.words;
    let i = wordCursorRef.current;
    if (i >= words.length || (i > 0 && t < words[i].start)) i = 0;
    let active = i > 0 ? i - 1 : -1;
    for (; i < words.length; i++) {
      if (words[i].start <= t) active = i;
      else break;
    }
    wordCursorRef.current = Math.max(0, active);
    setWid((prev) => (prev === active ? prev : active));
    rafRef.current = requestAnimationFrame(tick);
  }, []);

  const startRaf = useCallback(() => {
    stopRaf();
    rafRef.current = requestAnimationFrame(tick);
  }, [stopRaf, tick]);

  // Play sentence `index` from its start (fetching if needed).
  const playFrom = useCallback(
    async (index) => {
      const { doc } = paramsRef.current;
      if (!doc) return;
      index = Math.max(0, Math.min(index, doc.sentence_count - 1));
      targetRef.current = index;
      setError(null);
      setSid(index);
      setWid(-1);
      setPlaying(true);
      setLoading(!cacheRef.current.has(index));

      let data;
      try {
        data = await ensure(index);
      } catch (e) {
        setError(`Couldn't synthesize audio: ${e.message}`);
        setPlaying(false);
        setLoading(false);
        return;
      }
      // The user moved on while we were fetching.
      if (targetRef.current !== index) return;
      setLoading(false);

      // Warm the next sentence so playback stays gap-free.
      const next = ensure(index + 1);
      if (next && typeof next.catch === "function") next.catch(() => {});

      if (!data || !data.words.length || data.duration === 0) {
        setWords([]);
        advance(index); // nothing to say — skip ahead
        return;
      }

      const a = audioRef.current;
      curDataRef.current = data;
      wordCursorRef.current = 0;
      setWords(data.words);
      a.src = data.audio;
      a.onended = () => advance(index);
      try {
        await a.play();
      } catch {
        /* autoplay can reject; the UI stays in "playing" and user can retry */
      }
      startRaf();
    },
    // advance is defined below; it's stable via ref pattern, so we disable the lint.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [ensure, startRaf]
  );

  const advanceRef = useRef(() => {});
  const advance = useCallback((fromIndex) => advanceRef.current(fromIndex), []);
  advanceRef.current = (fromIndex) => {
    const { doc } = paramsRef.current;
    const next = fromIndex + 1;
    if (!doc || next >= doc.sentence_count) {
      stopRaf();
      setPlaying(false);
      return;
    }
    playFrom(next);
  };

  const pause = useCallback(() => {
    const a = audioRef.current;
    if (a) a.pause();
    stopRaf();
    setPlaying(false);
  }, [stopRaf]);

  const resume = useCallback(async () => {
    const a = audioRef.current;
    if (!a) return;
    if (a.src && curDataRef.current) {
      setPlaying(true);
      try {
        await a.play();
      } catch {
        /* ignore */
      }
      startRaf();
    } else {
      playFrom(sid >= 0 ? sid : 0);
    }
  }, [playFrom, sid, startRaf]);

  const toggle = useCallback(() => {
    if (playing) pause();
    else resume();
  }, [playing, pause, resume]);

  // Seek to a specific word within the currently loaded sentence.
  const seekWord = useCallback(
    (index, wordIndex) => {
      const data = cacheRef.current.get(index);
      if (sid === index && curDataRef.current && data && data.words[wordIndex]) {
        const a = audioRef.current;
        a.currentTime = data.words[wordIndex].start + 0.001;
        wordCursorRef.current = wordIndex;
        if (!playing) resume();
      } else {
        playFrom(index);
      }
    },
    [sid, playing, playFrom, resume]
  );

  return {
    playing,
    sid,
    wid,
    words,
    loading,
    error,
    playFrom,
    toggle,
    pause,
    seekWord,
  };
}
