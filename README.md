# AdaptIQ

AdaptIQ is an Android learning companion that uses an on-device MNN language model to provide adaptive tutoring and practice. It tracks knowledge gaps, maintains a learner profile, and adjusts the tutoring experience to the learner.

## Highlights

- Adaptive diagnostic and practice flows
- Knowledge-gap tracking backed by Room
- Learner preferences and profile persistence
- On-device inference through MNN and a JNI bridge
- Jetpack Compose UI
- Qwen2.5-0.5B-Instruct model in MNN format

## Requirements

- Android Studio with Android SDK 35
- JDK 17
- Android NDK `27.2.12479018`
- CMake 3.22.1 or newer
- Git LFS
- An Android device or emulator with API 29 or newer

The native build currently targets the `arm64-v8a` ABI.

## Getting Started

Clone the repository with Git LFS enabled so the model files are downloaded:

```bash
git lfs install
git clone https://github.com/akashboddula2425-hub/AdaptIQ.git
cd AdaptIQ
git lfs pull
```

Open the project in Android Studio, allow Gradle to sync, and ensure the required SDK, NDK, and CMake versions are installed.

To build a debug APK from Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

The generated APK is written to:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Install it on a connected device with:

```powershell
.\gradlew.bat installDebug
```

## Model Files

The model files in `model_download/` are stored with Git LFS. They are several hundred megabytes in total, so a Git LFS installation and sufficient GitHub LFS quota are required for a complete checkout.

The model is based on [Qwen2.5-0.5B-Instruct](https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct) and exported to MNN format. See [model_download/README.md](model_download/README.md) for the original model download and conversion details.

## Permissions

AdaptIQ declares internet access for model-related functionality and microphone access for audio-based interactions.

## Project Structure

```text
app/             Android application and Kotlin source
app/src/main/cpp Native MNN/JNI integration
MNN/             MNN inference runtime source
model_download/  Local model configuration and weights
```