@echo off
setlocal

echo =======================================================
echo Starting SpeechifyPDF (Backend and Frontend)
echo =======================================================

set CONDA_ENV=kokoro-pdf

echo [1/2] Starting FastAPI backend on http://127.0.0.1:8000 ...
start "SpeechifyPDF Backend" cmd /k "cd backend && conda activate %CONDA_ENV% && uvicorn app:app --host 127.0.0.1 --port 8000"

echo [2/2] Starting Vite frontend on http://localhost:5173 ...
start "SpeechifyPDF Frontend" cmd /k "cd frontend && npm run dev"

echo.
echo Servers are running in separate windows. 
echo To stop, simply close those command windows.
