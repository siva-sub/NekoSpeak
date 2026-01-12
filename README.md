# NekoSpeak

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)

**NekoSpeak** is a high-performance, on-device Text-to-Speech (TTS) engine for Android, capable of running offline with low latency. It bridges the gap between modern AI voice synthesis and the standard Android TTS API.

## � Features

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

## 📸 Screenshots

| Onboarding | Voice Selection | Settings |
|:---:|:---:|:---:|
| ![Onboarding](screenshots/screenshot_onboarding_model.jpg) | ![Voice Selection](screenshots/screenshot_onboarding_voice.jpg) | ![Settings](screenshots/screenshot_settings.jpg) |

| Voice Downloader | System Selection |
|:---:|:---:|
| ![Downloader](screenshots/screenshot_voice_downloader.jpg) | ![System](screenshots/screenshot_system_selection.jpg) |

## 📥 Download

**v1.0.10 is now available!**

> **Why is the APK size large?**
> NekoSpeak comes pre-packaged with high-quality AI models (Kokoro-82M, Piper-Amy, Kitten-TTS-Nano) to ensure **100% offline functionality** right out of the box. No initial downloads required!

*   **Universal** (239 MB): Works on all devices.
    *   [Download apk](https://github.com/siva-sub/NekoSpeak/releases/download/v1.0.10/app-universal-release.apk)
*   **arm64-v8a** (191 MB): Optimized for modern devices (Pixel, Samsung S-series).
    *   [Download apk](https://github.com/siva-sub/NekoSpeak/releases/download/v1.0.10/app-arm64-v8a-release.apk)
*   **armeabi-v7a** (186 MB): Optimized for older/low-end devices.
    *   [Download apk](https://github.com/siva-sub/NekoSpeak/releases/download/v1.0.10/app-armeabi-v7a-release.apk)

[**View Full Release Notes**](https://github.com/siva-sub/NekoSpeak/releases/tag/v1.0.10)

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

## 🛠️ Technical Details

For a detailed architectural breakdown, component analysis, system integration diagrams, and ONNX implementation details, please refer to the **[Technical Deep Dive](TECHNICAL_DEEP_DIVE.md)**.

## 🏗️ Build Instructions

1.  Clone the repository.
2.  Open in Android Studio (Ladybug+).
3.  Build and Run (`Shift + F10`).
    *   *Note: Ensure NDK is installed for C++ builds.*

## Credits & Acknowledgements

We gratefully acknowledge the incredible work of the open-source AI community:

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

## Author

Developed by **Sivasubramanian Ramanathan**
*   [LinkedIn](https://www.linkedin.com/in/sivasub987/)
*   [Website](https://sivasub.com/)
