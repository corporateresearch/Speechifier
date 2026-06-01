// Thin API client. The dev server proxies /api to the FastAPI backend.

async function asJson(res) {
  if (!res.ok) {
    let detail = `${res.status} ${res.statusText}`;
    try {
      const body = await res.json();
      if (body && body.detail) detail = body.detail;
    } catch {
      /* ignore */
    }
    throw new Error(detail);
  }
  return res.json();
}

export async function fetchVoices() {
  return asJson(await fetch("/api/voices"));
}

export async function uploadPdf(file) {
  const form = new FormData();
  form.append("file", file);
  return asJson(await fetch("/api/upload", { method: "POST", body: form }));
}

export async function fetchSentence(docId, sentenceId, voice, speed) {
  const url = `/api/tts/${docId}/${sentenceId}?voice=${encodeURIComponent(
    voice
  )}&speed=${encodeURIComponent(speed)}`;
  return asJson(await fetch(url));
}
