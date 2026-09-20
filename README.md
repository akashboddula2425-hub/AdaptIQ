# AdaptIQ

An on-device AI tutor and adaptive learning lab for Android. A local SLM personalizes the curriculum, a native MNN engine runs the inference, and an interactive practice lab generates 3D flashcards, quizzes, and games — all on the phone, with no network.

Prototype. `arm64-v8a` + `x86_64`, 16KB page aligned, minSdk 29, targets Android 15 (API 35).

---

## What it does

| Feature | Description |
| :--- | :--- |
| **Local inference** | Alibaba MNN runs quantised Qwen checkpoints through a custom JNI bridge (`mnn_bridge.cpp`), streaming generated tokens back to the UI as a Kotlin coroutine `Flow<String>`. |
| **Adaptive AI Tutor** | Dynamic `PersonaEngine` and `AdaptiveDiagnosticManager` assess student mastery, dynamically adjusting tone, explanation depth, and conceptual complexity on the fly. |
| **Practice Lab** | Generates interactive study activities directly from chat context — 3D-flippable flashcards, multiple-choice quizzes with instant evaluation, and term-definition matching games. |
| **Voice & Speech** | Hands-free dictation with Android's offline `SpeechRecognizer` and real-time audio playback using native `TextToSpeech` with live speaking indicators. |
| **In-App Downloader** | Direct model downloader for Hugging Face MNN weights with live progress bars, background downloading, and local model deletion — no browser or external setup needed. |
| **Local Persistence** | Chat history saved locally via `kotlinx.serialization` (`chat_history.json`), and knowledge gap tracking backed by a local SQLite / Room database. |

---

## Layout

```text
app/src/main/java/com/adaptiq/tutor/
├── ui/              Compose screens (Tutor, Practice Lab, Pods, Model Selector)
├── viewmodel/       TutorViewModel MVI loop, PersonaEngine, history persistence
├── engine/          ModelDownloader, MnnBridge JNI wrapper, DiagnosticManager
├── data/            Room database, KnowledgeGapDao, LearnerProfile entities
app/src/main/cpp/     MNN native bridge (mnn_bridge.cpp), CMake build script
MNN/                 Alibaba MNN runtime source and transformer LLM engine
```

Inference runs strictly on a background `Dispatchers.IO` coroutine thread while MNN holds mmap'd model weights in native memory. Android 15's 16KB memory page size enforcement requires explicit `-Wl,-z,max-page-size=16384` linker alignment in CMake to prevent `UnsatisfiedLinkError` crashes on modern devices. Coroutine cancellation and streaming completion are safeguarded with `finally` blocks to guarantee UI state recovery even when hitting stop tokens or cancellation interrupts.

---

## Building

```bash
./gradlew :app:assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

### Requirements
* Android Studio with Android SDK 35
* JDK 17
* Android NDK `27.2.12479018`
* CMake 3.22.1 or newer
* ABIs: `arm64-v8a` (physical hardware) and `x86_64` (emulators)

The compiled APK will be generated at:
```text
app/build/outputs/apk/debug/app-debug.apk
```

---

## Native Runtime & 16KB Page Alignment

MNN is compiled directly from source with LLM engine support. Modern Android 15 devices enforce 16KB ELF alignment on all shared libraries:

```cmake
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,-z,max-page-size=16384")
```

Both `libMNN.so` and `libmnn_bridge.so` are built with 16KB page alignment and multi-ABI support (`arm64-v8a`, `x86_64`), preventing launch crashes on Android 15 and 64-bit emulators.

---

## Models

Four Qwen models are offered directly inside the in-app model selector, downloaded from Hugging Face on demand and cached locally:

| Model | Size | Notes |
| :--- | :--- | :--- |
| **Qwen 3.5 · 2B** | ~1.4 GB | Fastest iteration. Fits comfortably in mobile RAM alongside background apps. |
| **Qwen 3.5 · 4B** | ~2.8 GB | Better reasoning and cleaner structured JSON output for quiz generation. Needs ~3 GB free. |
| **Qwen 3.5 · 9B** | ~5.5 GB | Deep conceptual explanations and high-IQ tutoring. Needs ~7 GB free. |
| **Qwen 3.5 · 27B** | ~16 GB | Desktop-class pedagogy for high-end devices with 16GB+ RAM. |

Downloads stream directly to `Android/data/com.adaptiq.tutor/files/models/` using chunked byte streaming with live progress reporting.

### Runtime Configuration
The runtime config is tuned for educational pedagogy and structured JSON extraction rather than open-ended chat:
* **Sampler Parameters**: Temperature 0.7, topP 0.9, repetition penalty 1.1 to produce focused, factual explanations.
* **Structured UI Generation**: Flashcards, multiple-choice quizzes, and matching pairs are extracted using regex-anchored blocks and fault-tolerant JSON parsers, allowing smaller 2B models to reliably drive interactive UI components.
* **Dynamic Persona Injections**: System prompt envelopes dynamically switch between personas (Socratic, Einstein, Pirate, Friendly Tutor) while preserving existing conversation history.

---

## Notes

* **True Offline Privacy**: 100% on-device inference with zero cloud APIs, tracking, or telemetry. The device can operate permanently in airplane mode once weights are downloaded.
* **Storage**: Models live in app-specific external storage (`files/models/<model_id>/`), meaning they can be managed and cleared cleanly without leaving orphaned files.
* **Chat Persistence**: Conversations are written to `files/chat_history.json` and reloaded asynchronously during ViewModel initialization.