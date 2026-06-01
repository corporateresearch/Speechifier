import { useEffect, useMemo, useRef, useState } from "react";
import { fetchVoices, uploadPdf } from "./api";
import { usePlayer } from "./usePlayer";

export default function App() {
  const [doc, setDoc] = useState(null);
  const [voices, setVoices] = useState([]);
  const [voice, setVoice] = useState("af_heart");
  const [speed, setSpeed] = useState(1.0);
  const [uploading, setUploading] = useState(false);
  const [uploadError, setUploadError] = useState(null);

  useEffect(() => {
    fetchVoices()
      .then((v) => {
        setVoices(v.voices);
        setVoice(v.default);
      })
      .catch(() => {});
  }, []);

  const handleFile = async (file) => {
    if (!file) return;
    setUploading(true);
    setUploadError(null);
    try {
      const data = await uploadPdf(file);
      setDoc(data);
    } catch (e) {
      setUploadError(e.message);
    } finally {
      setUploading(false);
    }
  };

  if (!doc) {
    return (
      <Landing
        onFile={handleFile}
        uploading={uploading}
        error={uploadError}
      />
    );
  }

  return (
    <Reader
      key={doc.doc_id}
      doc={doc}
      voices={voices}
      voice={voice}
      speed={speed}
      onVoice={setVoice}
      onSpeed={setSpeed}
      onClose={() => setDoc(null)}
    />
  );
}

function Landing({ onFile, uploading, error }) {
  const [drag, setDrag] = useState(false);
  const inputRef = useRef(null);

  return (
    <div className="landing">
      <div className="landing-inner">
        <div className="brand">
          <span className="brand-mark">▦</span>
          <h1>Kokoro PDF Reader</h1>
        </div>
        <p className="tagline">
          Drop in a PDF and listen — every word lights up as it’s read aloud,
          synthesized locally on your CPU.
        </p>

        <label
          className={`dropzone ${drag ? "drag" : ""}`}
          onDragOver={(e) => {
            e.preventDefault();
            setDrag(true);
          }}
          onDragLeave={() => setDrag(false)}
          onDrop={(e) => {
            e.preventDefault();
            setDrag(false);
            onFile(e.dataTransfer.files?.[0]);
          }}
        >
          <input
            ref={inputRef}
            type="file"
            accept="application/pdf,.pdf"
            hidden
            onChange={(e) => onFile(e.target.files?.[0])}
          />
          {uploading ? (
            <span className="dz-main">Reading PDF…</span>
          ) : (
            <>
              <span className="dz-icon">⇪</span>
              <span className="dz-main">Drop a PDF here</span>
              <span className="dz-sub">or click to choose a file</span>
            </>
          )}
        </label>

        {error && <div className="error">{error}</div>}
      </div>
    </div>
  );
}

function Reader({ doc, voices, voice, speed, onVoice, onSpeed, onClose }) {
  const player = usePlayer(doc, voice, speed);
  const { playing, sid, wid, words, loading, error, toggle, playFrom, seekWord } =
    player;

  // Build a fast lookup of words for the active sentence.
  const activeWords = sid >= 0 ? words : [];

  const progress = doc.sentence_count
    ? Math.min(100, Math.round(((sid + 1) / doc.sentence_count) * 100))
    : 0;

  // Keyboard: space toggles, arrows move sentence.
  useEffect(() => {
    const onKey = (e) => {
      if (e.target.tagName === "SELECT" || e.target.tagName === "INPUT") return;
      if (e.code === "Space") {
        e.preventDefault();
        toggle();
      } else if (e.code === "ArrowRight") {
        playFrom((sid < 0 ? -1 : sid) + 1);
      } else if (e.code === "ArrowLeft") {
        playFrom(Math.max(0, (sid < 0 ? 0 : sid) - 1));
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [toggle, playFrom, sid]);

  return (
    <div className="reader">
      <header className="topbar">
        <button className="ghost" onClick={onClose} title="Open another PDF">
          ‹ Library
        </button>
        <h2 className="doc-title" title={doc.title}>
          {doc.title}
        </h2>
        <div className="spacer" />
        <div className="controls">
          <select
            value={voice}
            onChange={(e) => onVoice(e.target.value)}
            title="Voice"
          >
            {voices.map((v) => (
              <option key={v.id} value={v.id}>
                {v.label}
              </option>
            ))}
          </select>
          <label className="speed">
            <span>{speed.toFixed(2)}×</span>
            <input
              type="range"
              min="0.5"
              max="1.75"
              step="0.05"
              value={speed}
              onChange={(e) => onSpeed(parseFloat(e.target.value))}
            />
          </label>
        </div>
      </header>

      <div className="progress">
        <div className="progress-bar" style={{ width: `${progress}%` }} />
      </div>

      <main className="page">
        <article className="prose">
          {doc.paragraphs.map((para, pi) => (
            <p key={pi}>
              {para.map((s) => (
                <Sentence
                  key={s}
                  index={s}
                  text={doc.sentences[s]}
                  active={s === sid}
                  words={s === sid ? activeWords : null}
                  wid={s === sid ? wid : -1}
                  onPlay={() => playFrom(s)}
                  onWord={(w) => seekWord(s, w)}
                />
              ))}
            </p>
          ))}
        </article>
      </main>

      <footer className="playbar">
        <button className="play" onClick={toggle}>
          {playing ? "❚❚" : "►"}
          <span>{playing ? "Pause" : sid < 0 ? "Read aloud" : "Resume"}</span>
        </button>
        <div className="status">
          {error ? (
            <span className="status-error">{error}</span>
          ) : loading ? (
            <span className="status-loading">Synthesizing…</span>
          ) : sid >= 0 ? (
            <span>
              Sentence {sid + 1} of {doc.sentence_count}
            </span>
          ) : (
            <span>{doc.sentence_count} sentences · press space to start</span>
          )}
        </div>
      </footer>
    </div>
  );
}

function Sentence({ index, text, active, words, wid, onPlay, onWord }) {
  const ref = useRef(null);

  // Auto-scroll the active word into view as it changes.
  useEffect(() => {
    if (active && wid >= 0 && ref.current) {
      const el = ref.current.querySelector(`[data-w="${wid}"]`);
      if (el) el.scrollIntoView({ block: "center", behavior: "smooth" });
    }
  }, [active, wid]);

  // Active sentence with word timings: render per-word spans.
  if (active && words && words.length) {
    return (
      <span ref={ref} className="sentence active">
        {words.map((w) => (
          <span key={w.i}>
            <span
              data-w={w.i}
              className={`word ${w.i === wid ? "lit" : ""}`}
              onClick={() => onWord(w.i)}
            >
              {w.text}
            </span>{" "}
          </span>
        ))}
      </span>
    );
  }

  // Inactive (or not-yet-synthesized) sentence: plain, clickable text.
  return (
    <span
      className={`sentence ${active ? "active" : ""}`}
      onClick={onPlay}
      title="Click to read from here"
    >
      {text}{" "}
    </span>
  );
}
