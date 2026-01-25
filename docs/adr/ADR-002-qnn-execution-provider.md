# ADR-002: QNN (Qualcomm Neural Network) Execution Provider Investigation

**Status:** Not Pursued  
**Date:** 2026-01-26  
**Decision Makers:** @siva  

## Context

Following ADR-001's NNAPI investigation showing limited acceleration potential, QNN (Qualcomm Neural Network SDK) was evaluated as an alternative hardware acceleration path for Qualcomm Snapdragon devices.

## Investigation Summary

### QNN Overview

| Aspect | Details |
|--------|---------|
| SDK | Qualcomm AI Engine Direct (QAIRT) |
| Target Hardware | Hexagon DSP, Adreno GPU, NPU (Snapdragon 8 Gen+) |
| ONNX Runtime Support | Requires custom build with QNN EP AAR |
| Model Requirements | Fixed shapes, QNN-compatible ops |

### Work Performed

1. **Downloaded Qualcomm AI Stack (QAIRT)** from Qualcomm Developer Network
2. **Downloaded FP32 ONNX models** for analysis:
   - `kokoro_82m.onnx` (289MB) from onnx-community/Kokoro-82M-ONNX
   - `flow_lm_main.onnx` (289MB) from KevinAHM/pocket-tts-onnx
   - `flow_lm_flow.onnx`, `mimi_decoder.onnx`, etc.

3. **Analyzed model compatibility**:
   ```
   Kokoro 82M: 2469 nodes, no quantization ops
   Pocket-TTS flow_lm_main: 2165 nodes, no quantization ops
   ```

4. **Identified blockers**:
   - Models use dynamic shapes (variable sequence lengths)
   - QNN requires fixed input dimensions at compile time
   - Would need model re-export from PyTorch with fixed shapes

### Key Findings

#### Pros of QNN
- Native Qualcomm hardware acceleration (DSP/GPU/NPU)
- Better op coverage than Android NNAPI
- Supports INT8 and FP16 quantization
- Production-ready for Snapdragon devices

#### Blockers
1. **Dynamic Shapes**: Current models export with dynamic batch/sequence dims
2. **Custom ORT Build**: Requires building ONNX Runtime with QNN AAR
3. **Model Re-export**: Need to re-export models with fixed max sequence length
4. **Platform Lock-in**: Only works on Qualcomm chipsets

### Effort Estimate (Not Pursued)

| Task | Estimate |
|------|----------|
| Re-export models with fixed shapes | 2-4 hours |
| Build ORT with QNN AAR | 4-8 hours |
| Integration & testing | 4-8 hours |
| **Total** | **10-20 hours** |

## Decision

**Not pursued at this time.**

### Rationale

1. **Scope**: Significant effort (10-20 hours) for Qualcomm-only benefit
2. **Model Changes**: Requires upstream model changes or fork maintenance
3. **Platform Coverage**: Excludes MediaTek, Samsung Exynos, Google Tensor devices
4. **Current Performance**: CPU inference with thread optimization is acceptable

## Artifacts Cleaned Up

- `/home/siva/Projects/NekoSpeak/qnn_models/` - Removed (contained downloaded ONNX files)
  - `kokoro/kokoro_82m.onnx`
  - `pocket_originals/flow_lm_main.onnx`
  - `pocket_originals/mimi_decoder_int8.onnx`
  - `pocket_originals/text_conditioner.onnx`

## Recommendations for Future

If QNN acceleration becomes a priority:

1. **Fork models** and re-export with fixed max_seq_length (e.g., 512 tokens)
2. **Use qnn-model-converter** to convert ONNX → QNN serialized format
3. **Build ORT Android** with QNN EP enabled
4. **Target Snapdragon 8 Gen 2+** for best NPU performance

## References

- [Qualcomm AI Engine Direct](https://developer.qualcomm.com/software/qualcomm-ai-engine-direct-sdk)
- [ONNX Runtime QNN EP](https://onnxruntime.ai/docs/execution-providers/QNN-ExecutionProvider.html)
- [QNN Model Converter](https://docs.qualcomm.com/bundle/publicresource/topics/80-63442-50/overview.html)
