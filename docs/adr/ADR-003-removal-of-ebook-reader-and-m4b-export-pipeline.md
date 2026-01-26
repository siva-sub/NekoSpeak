# ADR-003: Removal of eBook Reader and M4B Export Pipeline

## Status
**Implemented** — Feature developed locally, issues identified, and code removed. Never merged to main.

## Context
NekoSpeak initially aimed to be a complete audiobook creation solution. To achieve this, the following features were implemented locally:

1. **eBook Reader Module** (`com.nekospeak.tts.reader.*`)
   - EPUB parsing and rendering
   - Library management with Room database
   - Chapter navigation and bookmarking

2. **M4B Audiobook Export Pipeline**
   - `AudioGenerationService`: Background service for TTS generation
   - `M4bExporter`: FFmpeg-based audio concatenation and metadata embedding
   - `MediaOverlayGenerator`: SMIL-based text-audio synchronization
   - Chunking system for processing long books

## Problem
This feature set led to significant **feature bloat** and introduced critical usability and stability issues:

### 1. Unscalable Audio Generation
- Generating audio for an entire book (even a short one) required processing thousands of sentences.
- Each sentence required a full TTS inference pass, leading to **generation times of 30+ minutes** for a single chapter.
- Users had no practical way to pause/resume or see meaningful progress.

### 2. Out-of-Memory (OOM) Crashes
- The chunking system attempted to hold multiple audio buffers in memory.
- On devices with limited RAM, this caused frequent OOM crashes, especially during M4B concatenation.
- The background service was not aggressive enough with memory cleanup.

### 3. Scope Creep
- The core value proposition of NekoSpeak is **real-time, on-device TTS** for accessibility.
- The audiobook pipeline transformed it into a batch processing tool, which is a fundamentally different use case.
- Maintaining two codepaths (real-time TTS vs. batch export) increased complexity and bug surface.

## Decision
**Revert the eBook Reader and M4B Export Pipeline.**

The following modules are deprecated and will be removed in a future cleanup:
- `com.nekospeak.tts.reader.parser.EpubParser`
- `com.nekospeak.tts.reader.viewmodel.LibraryViewModel`
- `com.nekospeak.tts.reader.export.MediaOverlayGenerator`
- `com.nekospeak.tts.reader.export.M4bExporter`
- `com.nekospeak.tts.reader.service.AudioGenerationService`
- `com.nekospeak.tts.reader.data.*` (Room entities and repository)

NekoSpeak will refocus on its core strength: **fast, high-quality, on-device TTS** for real-time reading assistance.

## Consequences
- **Positive:** Reduced APK size, fewer OOM risks, simpler codebase, clearer product focus.
- **Negative:** Users who wanted full audiobook export will need to use external tools or wait for a future, better-architected solution.
- **Future Consideration:** If batch export is revisited, it should use a streaming/incremental approach with aggressive memory management, possibly offloading to a separate process or even a server-side component.
