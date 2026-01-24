# Release v1.1.0 - Pocket-TTS & Voice Cloning! 🎤

## ⭐ What's New

### Pocket-TTS Engine (Recommended!)
- **Zero-shot voice cloning** - Create custom TTS voices from any audio sample
- **Celebrity voices** - Download and use voices like Oprah Winfrey, Morgan Freeman, Greta Thunberg, and more
- **Batch & Streaming modes** - Choose between quality (batch) or low-latency (streaming) decoding
- **On-device processing** - All voice cloning happens locally, no data leaves your device

### Voice Recording & Encoding
- **Built-in voice recorder** - Record your voice directly in the app to create cloned voices
- **Audio file upload** - Upload existing WAV files for voice cloning
- **Processing indicators** - Visual feedback when encoding voices

### Improvements
- **Simplified model bundling** - Only Amy Low (Piper) is bundled; Pocket-TTS downloads on first use
- **Better UI feedback** - Processing status banners during voice operations
- **Various bug fixes** - Fixed voice distortion issues and streaming mode reliability

## 📥 Downloads

| Variant | Size | Description |
|---------|------|-------------|
| [app-universal-release.apk](https://github.com/siva-sub/NekoSpeak/releases/download/v1.1.0/app-universal-release.apk) | ~120MB | Works on all devices |
| [app-arm64-v8a-release.apk](https://github.com/siva-sub/NekoSpeak/releases/download/v1.1.0/app-arm64-v8a-release.apk) | ~80MB | Modern devices (Pixel, Samsung S-series) |
| [app-armeabi-v7a-release.apk](https://github.com/siva-sub/NekoSpeak/releases/download/v1.1.0/app-armeabi-v7a-release.apk) | ~75MB | Older/low-end devices |

## 🔧 Technical Changes
- Updated to Kotlin 2.0.0
- Gradle 9.2.0
- Fixed EMBED_DIM mismatch causing voice loading crashes
- Fixed batch mode decoder state initialization
- Improved streaming mode with adaptive chunking

## 💡 Recommendation
For the best experience, we strongly recommend using **Pocket-TTS** instead of Piper. The voice quality is significantly better, and you can create your own custom voices!

---
**Full Changelog**: [v1.0.10...v1.1.0](https://github.com/siva-sub/NekoSpeak/compare/v1.0.10...v1.1.0)
