# ADR-001: NNAPI Execution Provider for Hardware Acceleration

**Status:** Rejected  
**Date:** 2026-01-26  
**Decision Makers:** @siva-sub  

## Context

NekoSpeak TTS uses ONNX Runtime for neural network inference on Android. Users requested GPU/NPU hardware acceleration to improve inference speed. Android's Neural Networks API (NNAPI) is the standard interface for hardware acceleration on Android devices.

## Investigation Summary

### What Was Tested

| Component | Description |
|-----------|-------------|
| NNAPI Execution Provider | Built into ONNX Runtime Android AAR |
| Kokoro 82M Model | INT8 quantized (3412 nodes) |
| Pocket-TTS Models | INT8 quantized + FP32 versions |

### Test Results

#### Device: OnePlus CPH2663 (Android 16)

```
ExecutionProviderFactory: NNAPI is available on this device ✓
```

#### Kokoro Model (INT8)
```
NnapiExecutionProvider::GetCapability
- Partitions supported by NNAPI: 103
- Nodes in graph: 3412
- Nodes supported by NNAPI: 903 (~26%)

FATAL: op_builder_helpers.cc:144 AddNnapiSplit count [0] 
       does not evenly divide dimension 1 [256]
```
**Result:** Session creation fails, fallback to CPU required.

#### Pocket-TTS Models

| Model | Nodes | NNAPI Supported | Result |
|-------|-------|-----------------|--------|
| mimi_encoder (FP32) | 216 | 3 (1.4%) | Compiled on nnapi-reference |
| flow_lm_main (FP32) | 1109 | 31 (2.8%) | ANEURALNETWORKS_BAD_DATA |
| flow_lm_flow (FP32) | 447 | 31 (6.9%) | ANEURALNETWORKS_BAD_DATA |
| mimi_decoder (FP32) | - | - | Falls back to CPU |

**Result:** FP32 models load but NNAPI compilation fails due to unsupported ops.

### Root Causes

1. **Dynamic Shapes**: Models use dynamic batch/sequence dimensions that NNAPI cannot handle
2. **Unsupported Ops**: 
   - `Split` with incompatible dimension division
   - `MatMul` with dynamic shapes
   - `DynamicQuantizeLinear` / `MatMulInteger` (INT8 quantization ops)
3. **Low Node Coverage**: Only 1-26% of model nodes are NNAPI-compatible
4. **Device Fallback**: Test device uses `nnapi-reference` (CPU-only reference implementation)

## Options Considered

### Option 1: Enable NNAPI with Automatic Fallback (Implemented & Reverted)

**Implementation:**
- `ExecutionProviderFactory.kt` - Cached NNAPI availability check
- `PrefsManager.kt` - `useNnapi` toggle preference
- `SettingsScreen.kt` - Conditional UI toggle based on device support
- Engine modifications - Try NNAPI, catch failure, retry with CPU

**Pros:**
- Graceful degradation when NNAPI fails
- Future-proofs for better device/model support

**Cons:**
- Adds complexity for minimal benefit today
- ~400MB extra FP32 model downloads for NNAPI mode
- 5-30% inference overhead from failed NNAPI attempts
- User confusion: "toggle exists but doesn't accelerate"

### Option 2: QNN Execution Provider (Qualcomm Neural Network SDK)

**Pros:**
- Full Qualcomm DSP/NPU acceleration
- Better op coverage than NNAPI

**Cons:**
- Requires custom ONNX Runtime build with QNN AAR
- Models need export with fixed shapes (no dynamic dimensions)
- Qualcomm-only (excludes MediaTek, Samsung Exynos)

### Option 3: XNNPACK Backend Optimization

**Pros:**
- Already working (CPU optimized)
- No model changes required
- Cross-device compatible

**Cons:**
- No GPU/NPU acceleration
- Limited by CPU thermal throttling

### Option 4: Vulkan Compute Shaders

**Pros:**
- Cross-platform GPU support
- Wide device compatibility

**Cons:**
- Requires custom inference implementation
- Significant development effort
- Not integrated with ONNX Runtime

## Decision

**Rejected NNAPI integration at this time.**

### Rationale

1. **Cost-Benefit**: Implementation complexity (~400 lines) vs. zero acceleration benefit on tested devices
2. **Model Incompatibility**: Current models have fundamental op incompatibilities with NNAPI
3. **Device Reality**: Most Android devices use `nnapi-reference` CPU fallback, not actual accelerators
4. **User Experience**: A non-functional toggle creates confusion

### Recommendations for Future

1. **Model Re-export**: If considering NNAPI again, re-export models from PyTorch with:
   - Fixed input shapes (no dynamic dimensions)
   - NNAPI-friendly ops only (avoid Split, Gather with dynamic indices)
   - Static quantization (not dynamic)

2. **QNN Investigation**: For Qualcomm devices specifically, QNN EP with fixed-shape models may provide real acceleration

3. **Thread Optimization**: Current CPU execution with configurable thread count (user setting) provides good performance without complexity

## Files Modified (Reverted)

| File | Change |
|------|--------|
| `ExecutionProviderFactory.kt` | New - centralized EP configuration |
| `PrefsManager.kt` | Added `useNnapi` preference |
| `SettingsScreen.kt` | Added NNAPI toggle UI |
| `ModelRepository.kt` | Added FP32 model download URLs |
| `PocketTtsEngine.kt` | Added FP32/INT8 model selection |
| `KokoroEngine.kt` | Added NNAPI session options |
| `PiperEngine.kt` | Added NNAPI session options |

**All changes reverted to original state.**

## References

- [ONNX Runtime NNAPI EP](https://onnxruntime.ai/docs/execution-providers/NNAPI-ExecutionProvider.html)
- [Android NNAPI](https://developer.android.com/ndk/guides/neuralnetworks)
- [Qualcomm QNN](https://developer.qualcomm.com/software/qualcomm-neural-network)
- [KevinAHM/pocket-tts-onnx](https://huggingface.co/KevinAHM/pocket-tts-onnx)
