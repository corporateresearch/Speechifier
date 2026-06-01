#!/usr/bin/env bash
# Dev launcher: starts the FastAPI backend (:8000) and the Vite dev server (:5173).
# The Vite server proxies /api to the backend, so just open http://localhost:5173
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONDA_ENV="kokoro-pdf"

cleanup() { kill 0 2>/dev/null || true; }
trap cleanup EXIT INT TERM

echo "▶ Starting backend (Kokoro TTS) on http://127.0.0.1:8000 ..."
( cd "$ROOT/backend" && conda run --no-capture-output -n "$CONDA_ENV" \
    uvicorn app:app --host 127.0.0.1 --port 8000 ) &

echo "▶ Starting frontend on http://localhost:5173 ..."
( cd "$ROOT/frontend" && npm run dev ) &

wait
