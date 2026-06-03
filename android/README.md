# Speechifier — Android (fully offline, native Kotlin)

A native Android port of Speechifier that runs **Kokoro TTS entirely on-device** —
no server, no network for reading aloud. PDFs are extracted, read aloud with the
82M-param Kokoro model (int8 ONNX), and **each word highlights in sync** with playback.

This reimplements the Python backend's pipeline on-device. The server files under
`../backend/` are the behavioural spec being ported. See the approved plan at
`~/.claude/plans/i-want-to-create-mighty-charm.md`.

## Status

| Area | State |
|---|---|
| Gradle/Compose scaffold | ✅ done |
| `Segmenter` (port of `pdf_utils.py`) + unit tests | ✅ done |
| `WordTimer` (port of `tts.py` timing) + unit tests | ✅ done |
| `Voices` (all 9 languages) + `LangDetect` | ✅ done |
| `Phonemizer` interface + Tier A/B/C Kotlin + JNI bridges + `PhonemizerFactory` | 🟡 all 3 tiers scaffolded (Kotlin + JNI + CMake); native engines (espeak-ng / OpenJTalk / cppjieba) pending NDK build |
| Reader UI + ViewModel (highlight, controls, prefetch) | ✅ done — runs against interim stubs |
| `PdfExtractor` (PdfBox-Android) + SAF picker | ✅ done — wired as the default `PdfReader` |
| `KokoroEngine` (ONNX Runtime) + `AudioTrackPlayer` | ✅ code complete; **not wired** — needs model/voices/vocab assets (task #10) + real espeak-ng (task #5) |
| Tier-B Japanese (OpenJTalk) / Tier-C Mandarin (jieba) | ⬜ todo — NDK-blocked (phased last) |
| Asset download (Downloader/installer/catalog) + license screen | ✅ infra + UI done; download-prompt wiring waits on the wired engine |
| Error states (empty/unreadable/scanned PDF) | ✅ surfaced from `PdfExtractor` → `ErrorState` |

### Wiring the real engine (once assets exist)

`ReaderViewModelFactory` injects the `Synthesizer`; `ReaderViewModel` injects the
`TtsPlayer`. To go live, swap the two stubs for the real impls in those two places:

```kotlin
// ReaderViewModelFactory
synthesizer = KokoroEngine(modelPath, voicesDir, vocab, phonemizers)
// ReaderViewModel default
playerProvider = { AudioTrackPlayer(it) }
```

Both real impls are written; they need the downloaded model/voices/vocab and a
working espeak-ng (Tier A) before they can run. Inputs/outputs to verify against
the model are marked `VERIFY` in `KokoroEngine.kt`.

## Prerequisites (not installed in this environment yet)

The build needs the Android toolchain, which is **not present on this machine**:

- **JDK 17**
- **Android SDK** (compileSdk 35) + **NDK** + **CMake 3.22+**
- A device or emulator (`adb`)

Quickest setup is **Android Studio** (bundles SDK + NDK + JDK), or command-line:

```bash
# Example CLI setup (Linux)
sudo apt install openjdk-17-jdk
# Install cmdline-tools, then:
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0" \
           "ndk;27.0.12077973" "cmake;3.22.1"
echo "sdk.dir=$ANDROID_HOME" > android/local.properties
```

The Gradle **wrapper jar is not committed**. Generate it once with a local Gradle
(`gradle wrapper --gradle-version 8.11.1`) or let Android Studio do it on first sync.

## Build & test

```bash
cd android
./gradlew test            # runs SegmenterTest + WordTimerTest (pure JVM, no device)
./gradlew assembleDebug   # builds the APK (needs SDK + NDK)
./gradlew installDebug    # install on a connected device/emulator
```

## Runtime assets (downloaded on demand, not in the repo)

- `kokoro_int8_timestamped.onnx` (~80 MB) — from
  [`onnx-community/Kokoro-82M-v1.0-ONNX-timestamped`](https://huggingface.co/onnx-community/Kokoro-82M-v1.0-ONNX-timestamped)
  (the **timestamped** export, required for per-word highlighting).
- Per-voice style packs for all 9 languages.
- `espeak-ng-data/` (Tier A), Japanese `unidic` (Tier B), Chinese jieba/pinyin (Tier C).

These download per-language on first use to keep the APK small.

## Licensing note

espeak-ng (Tier A) is **GPL**; OpenJTalk/UniDic and the Chinese G2P data carry their
own terms. Kokoro weights are Apache-2.0. Audit all G2P/dictionary licenses before
distribution and surface attributions in-app.

## Layout

```
app/src/main/
  java/com/speechifier/
    text/Segmenter.kt        # port of pdf_utils.py
    text/LangDetect.kt       # voice -> language/G2P tier
    tts/WordTimer.kt         # port of tts.py word timing (language-agnostic)
    tts/Voices.kt            # 9-language Kokoro voice catalog
    tts/Phonemizer.kt        # G2P interface
    tts/EspeakPhonemizer.kt  # Tier A (espeak-ng JNI)
    ui/Theme.kt, MainActivity.kt, ReaderScreen.kt
  cpp/                       # NDK: native_g2p.cpp (+ espeak-ng/OpenJTalk/cppjieba)
  test/                      # JVM unit tests for the ports
```
