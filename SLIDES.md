---
marp: true
theme: gaia
class: lead
backgroundColor: #fff
backgroundImage: url('https://marp.app/assets/hero-background.svg')
style: |
  section {
    font-family: 'Inter', sans-serif;
    font-size: 26px; /* Slightly reduced for more content */
    padding: 30px;
  }
  h1 { color: #2D3E50; font-size: 1.5em; margin-bottom: 0.1em; }
  h2 { color: #E74C3C; font-size: 1.1em; margin-bottom: 0.4em; }
  strong { color: #2980B9; }
  blockquote { background: #f9f9f9; border-left: 8px solid #ccc; padding: 10px 15px; font-style: italic; font-size: 0.9em; }
  img { box-shadow: 0 4px 6px rgba(0,0,0,0.1); border-radius: 8px; background-color: transparent; }
  .columns { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 1rem; align-items: center; }
  .small-text { font-size: 0.7em; }
  .center { text-align: center; }
  .profile-box { background: #f0f4f8; padding: 15px; border-radius: 8px; font-size: 0.85em; }
---

# **NekoSpeak** 🐱
## Engineering Intelligence at the Edge

<div class="columns">
<div>

**Sivasubramanian Ramanathan**
*Product Owner | Fintech & Innovation*
*Ex-BIS Innovation Hub Singapore*

**🌏 Seeking Opportunities in Singapore**
I am looking for roles in **Product Management, Fintech, Payments, RegTech**, and **Digital Assets**.

</div>
<div class="profile-box">

"I am not just a Product person. **I build.**"

I have worked across product delivery, user research, and cross-agency collaboration. I enjoy solving complex problems and bringing structure to early ideas.

**I care deeply about building products that create real impact.**

</div>
</div>

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

*   **Triple Engine Core**:
    *   🧠 **Kokoro (82M)**: Human-like.
    *   ⚡ **Piper**: Fast & Multilingual.
    *   🏎️ **Kitten (Nano)**: Lightweight.
*   **100% Offline**: No internet needed.

</div>
<div>

![h:350](screenshots/screenshot_onboarding_model.jpg)

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

![h:400](sequence_flow.png)

<p class="center small-text">It handles the full <code>CHECK_TTS_DATA</code> handshake, allowing it to power 3rd party apps (MoonReader, @Voice) system-wide.</p>

---

# **Product Showcase** 📱

Polished UX focusing on accessibility and ease of use.

| Voice Selection | Settings & Config |
|:---:|:---:|
| ![h:400](screenshots/screenshot_voice_downloader.jpg) | ![h:400](screenshots/screenshot_settings.jpg) |

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

**Open for roles that sit between policy, technology, and stakeholder engagement.**

---

# **Lets Connect** 🤝

I am ready to bring this level of engineering rigor and product thinking to your team.

*   🌐 **Portfolio**: [sivasub.com](https://sivasub.com)
*   💼 **LinkedIn**: [linkedin.com/in/sivasub987](https://www.linkedin.com/in/sivasub987/)
*   💻 **Code**: [github.com/siva-sub/NekoSpeak](https://github.com/siva-sub/NekoSpeak)

<br>

**Download NekoSpeak v1.0.10**:
[github.com/siva-sub/NekoSpeak/releases](https://github.com/siva-sub/NekoSpeak/releases)
