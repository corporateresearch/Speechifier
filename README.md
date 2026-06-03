# Speechifier

A native Android app that reads PDFs aloud with **per-word highlighting**, powered by the Kokoro TTS model running entirely on-device.

## Features

- 📄 PDF text extraction and reflow
- 🎤 On-device Kokoro TTS (82M params, int8 ONNX)
- 🎯 Real-time per-word highlighting synchronized with audio
- 🌍 9 language voices (US/UK English, Spanish, French, Hindi, Italian, Japanese, Portuguese, Mandarin)
- ⚡ Fully offline — no server, no network required

## Build

See [android/README.md](android/README.md) for prerequisites and build instructions.

```bash
cd android
./gradlew test
./gradlew assembleDebug
```

## License

Apache 2.0 (wraps Kokoro, FastAPI, and other open-source libraries).
