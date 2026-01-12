# NekoSpeak

![Android](https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/kotlin-%237F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)

**NekoSpeak** is a high-performance, on-device Text-to-Speech (TTS) engine for Android, capable of running offline with low latency. It bridges the gap between modern AI voice synthesis and the standard Android TTS API.

## 👤 Author
**Sivasubramanian Ramanathan**
*   [LinkedIn](https://www.linkedin.com/in/sivasub987)
*   [Website](https://sivasub.com)

## 📸 Screenshots

| Onboarding | Voice Selection | Settings |
|:---:|:---:|:---:|
| ![Onboarding](screenshots/screenshot_onboarding_model.jpg) | ![Voice Selection](screenshots/screenshot_onboarding_voice.jpg) | ![Settings](screenshots/screenshot_settings.jpg) |

| Voice Downloader | System Selection |
|:---:|:---:|
| ![Downloader](screenshots/screenshot_voice_downloader.jpg) | ![System](screenshots/screenshot_system_selection.jpg) |

## ⬇️ Download
The app is available for download in the [Releases](https://github.com/yourusername/NekoSpeak/releases) section.



**NekoSpeak** is a high-performance, on-device Text-to-Speech (TTS) engine for Android, capable of running offline with low latency. It bridges the gap between modern AI voice synthesis and the standard Android TTS API, allowing it to be used as a drop-in replacement for Google TTS in any application (eLook reader, @Voice Aloud Reader, system accessibility, etc.).

## 🚀 Key Features

*   **Offline First**: No internet connection required after initial model download.
*   **Next-Gen Models**:
    *   **Kokoro**: High-quality, 82M parameter model offering natural prosody.
    *   **Kitten**: A nano-sized (35M), ultra-low latency variant of Kokoro, perfect for older devices.
    *   **Piper**: Fast, lightweight neural TTS supporting a wide range of voices.
*   **Android Integration**: Fully compliant `TextToSpeechService` implementation.
*   **Customizable**: Adjust speed, voices, and thread usage for performance tuning.
*   **Open Source**: Built on ONNX Runtime and eSpeak-ng.

## 🛠️ Technical Overview

NekoSpeak uses **ONNX Runtime** for inference and **JNI** to interface with native libraries like `espeak-ng` for phonemization.

For a detailed architectural breakdown, component analysis, and data flow diagrams, please refer to the **[Technical Deep Dive](TECHNICAL_DEEP_DIVE.md)**.

## 🏗️ Build Instructions

### Prerequisites
*   Android Studio Ladybug or newer.
*   JDK 17+.
*   Android NDK (Side-by-side) for building the C++ native wrappers.

### Compilation
1.  Clone the repository:
    ```bash
    git clone https://github.com/yourusername/NekoSpeak.git
    cd NekoSpeak
    ```
2.  Open in Android Studio.
3.  Sync Gradle project.
4.  Build and Run (`Shift + F10`) on your target device.

*Note: The project includes pre-built assets for the Kokoro model. Ensure your build has sufficient heap size if processing large assets.*

## 📦 Usage

1.  **Install** the APK.
2.  Go to Android **Settings** -> **System** -> **Languages & input** -> **Text-to-speech output**.
3.  Select **NekoSpeak** as the preferred engine.
4.  Tap the "Gear" icon to configure voices and models.

## 🤝 Contributing

Contributions are welcome! Please check the `TECHNICAL_DEEP_DIVE.md` to understand the architecture before submitting extensive changes.

## 📜 License

NekoSpeak is open-source software. See [LICENSE](LICENSE) for details.
