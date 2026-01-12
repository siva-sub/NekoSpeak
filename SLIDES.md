---
marp: true
theme: gaia
class: lead
backgroundColor: #fff
backgroundImage: url('https://marp.app/assets/hero-background.svg')
style: |
  section {
    font-family: 'Inter', sans-serif;
    font-size: 28px;
    padding: 40px;
  }
  h1 { color: #2D3E50; font-size: 1.6em; margin-bottom: 0.2em; }
  h2 { color: #E74C3C; font-size: 1.1em; margin-bottom: 0.5em; }
  strong { color: #2980B9; }
  blockquote { background: #f9f9f9; border-left: 10px solid #ccc; padding: 10px 20px; font-style: italic; font-size: 0.9em; }
  img { box-shadow: 0 4px 6px rgba(0,0,0,0.1); border-radius: 8px; background-color: transparent; }
  .columns { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; }
  .small-text { font-size: 0.7em; }
  .center { text-align: center; }
---

# **NekoSpeak** 🐱
## Engineering Intelligence at the Edge

**100% Offline • Ultra Low Latency • Privacy First**

<br>

**Sivasubramanian Ramanathan**
*Product Owner | Fintech & Digital Innovation*
*(Ex-BIS Innovation Hub Singapore)*

---

# **The Problem: AI has a "Last Mile" Issue**

In my work exploring **Fintech & RegTech**, I've seen how reliance on cloud APIs creates bottlenecks. For Voice AI on Android, this manifests as:

1.  🔴 **Latency**: Waiting for server responses breaks natural conversation flow.
2.  🔴 **Privacy Risks**: Sending sensitive audio data to the cloud is unacceptable for many use cases.
3.  🔴 **Robotic Fallback**: Traditional offline engines (`espeak`) sound unnatural.

> **Goal**: Build a "Zero-Compromise" engine that runs mostly on-device.

---

# **The Solution: NekoSpeak** 🚀

A drop-in replacement for the Android TTS ecosystem, bringing heavily quantized Large Audio Models (LAMs) to the mobile edge.

<div class="columns">
<div>

*   **Triple Engine Architecture**:
    *   🧠 **Kokoro (82M)**: Human-level expressiveness.
    *   ⚡ **Piper**: High-speed multilingual inference.
    *   🏎️ **Kitten (Nano)**: Ultra-lightweight fallback.
*   **100% Offline**: No internet required after download.

</div>
<div>

![w:450](screenshots/screenshot_onboarding_model.jpg)

</div>
</div>

---

# **Technical Architecture** ⚙️

I architected a custom pipeline using **ONNX Runtime** and **C++ JNI Bridges** to optimize performance on mobile CPUs.

![w:900](arch_overview.png)

*   **Smart Batching**: Dynamic buffering balances latency vs. context window.
*   **Native Bridge**: Custom C++ wrapper for `libespeak-ng` phonemization.

---

# **Deep System Integration** 🔄

Unlike simple "wrapper" apps, NekoSpeak integrates deep into the **Android Framework**.

![w:900](sequence_flow.png)

<p class="center small-text">It handles the full <code>CHECK_TTS_DATA</code> handshake, allowing it to power 3rd party apps (MoonReader, @Voice) system-wide.</p>

---

# **Product Showcase** 📱

Polished UX focusing on accessibility and ease of use.

| Voice Selection | Settings & Config |
|:---:|:---:|
| ![h:400](screenshots/screenshot_onboarding_voice.jpg) | ![h:400](screenshots/screenshot_settings.jpg) |

---

# **Engineering Philosophy & Impact** 🌟

This project reflects my approach to Product Engineering:

1.  **Solve Real Problems**: Bridges the gap between "Cool AI Demo" and "Daily Driver Utility".
2.  **Robust Engineering**: "Zero-Crash" architecture with graceful degradation (Cloud -> Local -> Nano).
3.  **User-Centric**: Privacy by default, with no hidden analytics.

*Similar to my work on the **Singapore Location Intelligence MCP** and **Client-Side OCR**.*

---

# **About the Builder** 👨‍💻

**Sivasubramanian Ramanathan**
*Product Owner | Fintech, RegTech & Digital Innovation*
*PMP | PSM II | PSPO II*

I specialize in taking messy, real-world complexity and structuring it into reliable products.

**I am looking for my next role in Singapore:** 🇸🇬
*   **Focus**: Product Management, Payment Infrastructure, Digital Assets.
*   **Value**: Bridging the gap between Policy, Tech, and Business.

---

# **Lets Connect** 🤝

I am ready to bring this level of engineering rigor and product thinking to your team.

*   🌐 **Portfolio**: [sivasub.com](https://sivasub.com)
*   💼 **LinkedIn**: [linkedin.com/in/sivasub987](https://www.linkedin.com/in/sivasub987/)
*   💻 **Code**: [github.com/siva-sub/NekoSpeak](https://github.com/siva-sub/NekoSpeak)

<br>

**Download NekoSpeak v1.0.10**:
[github.com/siva-sub/NekoSpeak/releases](https://github.com/siva-sub/NekoSpeak/releases)
