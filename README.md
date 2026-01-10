![Android](https://img.shields.io/badge/Android-35-green?style=flat&logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-1.9.0-purple?style=flat&logo=kotlin)
[![GitHub Release](https://img.shields.io/github/v/release/siva-sub/NekoSpeak)](https://github.com/siva-sub/NekoSpeak/releases)
[![License](https://img.shields.io/github/license/siva-sub/NekoSpeak)](https://github.com/siva-sub/NekoSpeak/blob/main/LICENSE)
[![GitHub stars](https://img.shields.io/github/stars/siva-sub/NekoSpeak?style=social)](https://github.com/siva-sub/NekoSpeak/stargazers)
![ONNX Runtime](https://img.shields.io/badge/ONNX%20Runtime-1.18.0-blue)
![CPU](https://img.shields.io/badge/CPU-supported-brightgreen)
[![Download APK](https://img.shields.io/badge/Download-APK%20(v1.0.6)-blue?style=for-the-badge&logo=android)](https://github.com/siva-sub/NekoSpeak/releases)

**NekoSpeak** is a private, on-device AI Text-to-Speech (TTS) engine for Android. It combines the expressive power of **Kokoro v1.0**, the efficiency of **Kitten TTS Nano**, and the versatility of **Piper** into a single, seamless experience.

<p align="center">
  <img src="screenshot_voices.png" width="250" alt="Voice Selection">
  <img src="screenshot_settings.png" width="250" alt="Settings Screen">
</p>

### 📥 Download
**v1.0.6 is now available!**
*   **Universal**: Works on all devices (larger size due to bundled models).
*   **arm64-v8a**: Optimized for modern devices (Pixel, Samsung S-series).
*   **armeabi-v7a**: Optimized for older/low-end devices.

> **Why is the APK size large?**
> NekoSpeak comes pre-packaged with high-quality AI models (Kokoro-82M, Piper-Amy) to ensure **100% offline functionality** right out of the box. No initial downloads required!

[**Download from Releases**](https://github.com/siva-sub/NekoSpeak/releases)

## Author
Developed by **Sivasubramanian Ramanathan**
*   [LinkedIn](https://www.linkedin.com/in/sivasub987/)
*   [Website](https://sivasub.com/)

## Features

*   **Triple Engine Support**:
    *   **Kokoro v1.0 (82M)**: State-of-the-art expressive narration. Best for audiobooks and reading.
    *   **Piper**: Fast, efficient, and multilingual. Supports hundreds of community voices (English, Tamil, Spanish, etc.).
    *   **Kitten TTS Nano**: Extremely lightweight and fast. Ideal for older devices.
*   **Privacy First**: All processing happens 100% on-device. No data is ever sent to the cloud.
*   **System-Wide Integration**: Works with any Android app that supports TTS (MoonReader, @Voice, etc.).
*   **Advanced Voice Management**:
    *   **Cloud Voice Store**: Browse and download hundreds of Piper voices directly within the app.
    *   **Quality Filters**: Filter voices by quality (x_low to high).
    *   **Persistence**: Remembers your preferred voice and speed settings.

## Architecture

**NekoTtsService** vs **Engines**:
*   **NekoTtsService**: The **Face** of the app. Handles Android TTS API requests and audio streaming.
*   **Engines (Kokoro/Piper/Kitten)**: The **The Brains**. Handle Model/ONNX inference and raw audio synthesis.

### Technical Deep Dive: The Hybrid Pipeline

The following diagram illustrates the data flow through the application, supporting multiple engines:

```mermaid
graph TD
    subgraph Input [Input Processing]
        RawText["Raw Text (String)"] -->|NekoTtsService| Routing{Engine Router}
        Routing -->|Kokoro| K_G2P[Misaki G2P]
        Routing -->|Piper| P_G2P[Misaki + Espeak Fallback]
        Routing -->|Kitten| E_G2P[Espeak Only]
    end

    subgraph Core [Inference Core]
        K_G2P -->|Tokens| K_ONNX[Kokoro ONNX (82M)]
        P_G2P -->|IPA Phonemes| P_ONNX[Piper ONNX]
        E_G2P -->|Phoneme IDs| Kit_ONNX[Kitten Nano ONNX]
        
        VoiceFile["Voice Style / Config"] -.-> K_ONNX
        VoiceFile -.-> P_ONNX
        
        SpeedParam["Speed (Float)"] -.-> K_ONNX
        SpeedParam -.-> P_ONNX
    end

    subgraph Output [Audio Synthesis]
        K_ONNX -->|Float32| PCM[PCM Converter]
        P_ONNX -->|Float32| PCM
        Kit_ONNX -->|Float32| PCM
        PCM -->|16-bit Stream| AudioTrack[Android AudioTrack]
    end
```

### Component Breakdown

*   **`NekoTtsService.kt`**: Application Entry Point. Managing lifecycle and dispatching requests to the active engine.
*   **`PiperEngine.kt`**:
    *   **Hybrid Phonemization**: Uses **Misaki** for high-quality English phonemes (converted to IPA) and falls back to **Espeak-NG** for other languages (e.g., Tamil, Arabic).
    *   **On-Demand Loading**: Loads voices dynamically from storage.
*   **`KokoroEngine.kt`**:
    *   **Style Vectors**: Slices specific voice styles from a massive `voices.bin` file.

## 📂 Project Structure
```text
.
├── app
│   ├── src
│   │   ├── main
│   │   │   ├── java/com/nekospeak/tts  (Kotlin Source)
│   │   │   ├── cpp/                    (Native C++ / JNI)
│   │   │   ├── assets/                 (Bundled Models)
│   │   │   └── res/                    (UI Resources)
│   ├── build.gradle.kts                (App Build Config)
├── gradle                              (Gradle Wrapper)
├── build.gradle.kts                    (Root Build Config)
└── README.md                           (Documentation)
```

## Credits & Acknowledgements

We gratefully acknowledge the incredibly work of the open-source AI community:

*   **[Piper](https://github.com/rhasspy/piper)** & **[Piper Voices](https://huggingface.co/rhasspy/piper-voices)**
    *   Thanks to the Rhasspy team for the amazing Piper architecture and the massive collection of high-quality voices.
*   **[Piper Tamil Voice (Valluvar)](https://huggingface.co/datasets/Jeyaram-K/piper-tamil-voice)**
    *   Special thanks to **Jeyaram-K** for training and providing the high-quality Tamil "Valluvar" model.
*   **[Kokoro-ONNX](https://github.com/thewh1teagle/kokoro-onnx)**
    *   Thanks to [thewh1teagle](https://github.com/thewh1teagle) for the inspiration and ONNX export work.
*   **[KittenTTS](https://github.com/KittenML/KittenTTS)**
    *   Thanks to the KittenML team for their work on efficient TTS architectures.
*   **[Misaki](https://github.com/hexgrad/misaki)**
    *   G2P logic ported from this excellent library.
*   **[Espeak-NG](https://github.com/espeak-ng/espeak-ng)**
    *   The backbone of multilingual phonemization.

## License

**NekoSpeak** is licensed under the **MIT License**.

> **Note**: While the NekoSpeak application code is MIT, it bundles dependencies with their own licenses:
> *   **Espeak-NG**: GPL v3.0
> *   **ONNX Models**: Apache 2.0 / CC-BY-4.0 (Check specific model licenses)
