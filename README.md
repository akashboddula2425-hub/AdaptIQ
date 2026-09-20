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

The bundled model files in `model_download/` are stored with Git LFS. They are several hundred megabytes in total, so a Git LFS installation and sufficient GitHub LFS quota are required for a complete checkout.

### Qwen3.5 Downloads

Users can download an official Qwen3.5 model based on their available memory and performance needs. The links below provide the original Hugging Face Transformers checkpoints:

| Model | Parameters | Suggested use |
| --- | ---: | --- |
| [Qwen3.5-0.8B](https://huggingface.co/Qwen/Qwen3.5-0.8B) | 0.8B | Small devices and quick experiments |
| [Qwen3.5-2B](https://huggingface.co/Qwen/Qwen3.5-2B) | 2B | Lightweight local inference |
| [Qwen3.5-4B](https://huggingface.co/Qwen/Qwen3.5-4B) | 4B | General local use |
| [Qwen3.5-9B](https://huggingface.co/Qwen/Qwen3.5-9B) | 9B | Higher-quality local inference |
| [Qwen3.5-27B](https://huggingface.co/Qwen/Qwen3.5-27B) | 27B | Workstations and powerful servers |
| [Qwen3.5-35B-A3B](https://huggingface.co/Qwen/Qwen3.5-35B-A3B) | 35B total, 3B active | MoE inference on capable hardware |

Download a selected model with the Hugging Face CLI:

```bash
pip install -U huggingface_hub
huggingface-cli download Qwen/Qwen3.5-2B --local-dir model_download/qwen3.5-2B
```

Replace `Qwen/Qwen3.5-2B` with any model name from the table. The Qwen3.5 checkpoints are large and are provided in Transformers format. They are not directly compatible with AdaptIQ's current MNN runtime until they are converted to an MNN-compatible format and wired into the app. For the currently bundled MNN model and conversion details, see [model_download/README.md](model_download/README.md).

Qwen3.5 models are multimodal and support text, image, and video inputs in supported inference frameworks. Check the individual model card for hardware requirements, quantized variants, and framework-specific instructions.

## Permissions

AdaptIQ declares internet access for model-related functionality and microphone access for audio-based interactions.

## Project Structure

```text
app/             Android application and Kotlin source
app/src/main/cpp Native MNN/JNI integration
MNN/             MNN inference runtime source
model_download/  Local model configuration and weights
```