---
marp: true
theme: gaia
class: lead
backgroundColor: #fff
backgroundImage: url('https://marp.app/assets/hero-background.svg')
style: |
  section {
    font-family: 'Inter', sans-serif;
    font-size: 30px; /* Reduced base font size */
  }
  h1 {
    color: #2D3E50;
    font-size: 1.5em;
  }
  h2 {
    color: #E74C3C;
    font-size: 1.2em;
  }
  strong {
    color: #2980B9;
  }
  img {
    border-radius: 8px;
    box-shadow: 0 4px 6px rgba(0,0,0,0.1);
    background-color: transparent;
  }
---

# **NekoSpeak** 🐱
## Next-Gen AI Voice Engine for Android

**100% Offline • Ultra Low Latency • Privacy First**

<br>

**Built by Sivasubramanian Ramanathan**
*Product Owner | Fintech & Digital Innovation*

---

# **The Problem** 📉

Existing text-to-speech solutions release on Android often force a compromise:

1.  **Robotic & Flat**: Older offline engines sound artificial (`espeak`).
2.  **Privacy Invasive**: High-quality usually means sending data to the cloud.
3.  **Latency**: Waiting for server responses breaks the reading flow.

> "AI should assist us without compromising our privacy or patience."

---

# **The Solution: NekoSpeak** 🚀

A drop-in replacement for system TTS that brings **State-of-the-Art AI** to the edge.

*   **Triple Engine Core**:
    *   🧠 **Kokoro (82M)**: Human-level expressiveness.
    *   ⚡ **Piper**: Fast, multilingual support.
    *   🏎️ **Kitten (Nano)**: Ultra-lightweight reliability.
*   **Privacy by Design**: Zero data leaves the device.
*   **Universal Compatibility**: Works with @Voice, MoonReader, etc.

---

# **Technical Engineering** ⚙️

Building for the "Edge" requires specialized architecture:

*   **ONNX Runtime**: Quantized models (int8) for CPU efficiency.
*   **Native C++ Bridge**: Custom JNI wrappers for `espeak-ng`.
*   **Smart Batching**: Dynamic token buffering.
*   **Kotlin Coroutines**: Non-blocking audio pipeline.

![w:900](arch_diagram.svg)

---

# **Why I Built This** 💡

My background in **Fintech & Innovation** (ex-BIS Innovation Hub) taught me that optimal products solve technical complexity with simple UX.

*   **Offline First**: Balancing model size vs. user experience.
*   **Accessibility**: High-quality AI voices for everyone, free of charge.
*   **Curiosity**: Pushing mobile CPU limits without cloud GPUs.

---

# **About the Builder** 👨‍💻

**Sivasubramanian Ramanathan**
*He/Him*

**Product Owner | Fintech, RegTech & Digital Innovation**
*Ex-BIS Innovation Hub Singapore | PMP | PSM II | PSPO II*

I specialize in bringing structure to complex, early-stage ideas and building products with real impact.

---

# **I'm Looking for My Next Role** 🤝

I am open to opportunities in **Singapore** 🇸🇬:

*   **Roles**: Product Management, Fintech, Payments, RegTech, Digital Assets.
*   **Focus**: Building simple, safe, and genuinely useful solutions.

> "Taking messy, real-world complexity and structuring it into reliable product experiences."

[LinkedIn](https://www.linkedin.com/in/sivasub987) | [Website](https://sivasub.com) | [GitHub](https://github.com/siva-sub/NekoSpeak)

---

### **Download NekoSpeak v1.0.10 Today**

Available now on GitHub Releases.

![w:150](https://img.shields.io/badge/Android-35-green?style=for-the-badge&logo=android) ![w:150](https://img.shields.io/badge/Kotlin-1.9.0-purple?style=for-the-badge&logo=kotlin)
