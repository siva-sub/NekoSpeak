#!/usr/bin/env python3
"""
Pre-compute Pocket-TTS voice embeddings from official WAV files.

This script downloads official voices from HuggingFace and extracts
the audio_prompt embeddings that can be loaded by our Android app.

Usage:
    pip install pocket-tts safetensors
    python precompute_voice_embeddings.py

Output:
    Creates .safetensors files for each voice in the output directory.
"""

import os
import json
from pathlib import Path

try:
    from pocket_tts import TTSModel
    import safetensors.torch
    import torch
except ImportError:
    print("Please install: pip install pocket-tts safetensors torch")
    exit(1)

# Official voice sources from kyutai/tts-voices
VOICES = {
    "alba": "hf://kyutai/tts-voices/alba-mackenna/casual.wav",
    "marius": "hf://kyutai/tts-voices/voice-donations/Selfie.wav",
    "javert": "hf://kyutai/tts-voices/voice-donations/Butter.wav",
    "jean": "hf://kyutai/tts-voices/ears/p010/freeform_speech_01.wav",
    "fantine": "hf://kyutai/tts-voices/vctk/p244_023.wav",
    "cosette": "hf://kyutai/tts-voices/expresso/ex04-ex02_confused_001_channel1_499s.wav",
    "eponine": "hf://kyutai/tts-voices/vctk/p262_023.wav",
    "azelma": "hf://kyutai/tts-voices/vctk/p303_023.wav",
}

# Voice metadata for Android app
VOICE_METADATA = {
    "alba": {"name": "Alba", "gender": "Female", "region": "British"},
    "marius": {"name": "Marius", "gender": "Male", "region": "French"},
    "javert": {"name": "Javert", "gender": "Male", "region": "French"},
    "jean": {"name": "Jean", "gender": "Male", "region": "French"},
    "fantine": {"name": "Fantine", "gender": "Female", "region": "British"},
    "cosette": {"name": "Cosette", "gender": "Female", "region": "American"},
    "eponine": {"name": "Eponine", "gender": "Female", "region": "British"},
    "azelma": {"name": "Azelma", "gender": "Female", "region": "British"},
}

OUTPUT_DIR = Path("pocket_voice_embeddings")


def main():
    print("Loading Pocket-TTS model...")
    model = TTSModel.load_model()
    
    OUTPUT_DIR.mkdir(exist_ok=True)
    
    all_voices = {}
    
    for voice_id, voice_url in VOICES.items():
        print(f"Processing voice: {voice_id} from {voice_url}")
        
        try:
            # Get audio prompt embedding from the model
            state = model.get_state_for_audio_prompt(voice_url)
            
            # The state contains KV cache and other info, but we need the audio_prompt
            # Let's extract it using the internal method
            from pocket_tts.utils.utils import download_if_necessary
            audio_path = download_if_necessary(voice_url)
            
            from pocket_tts.data.audio import audio_read
            from pocket_tts.data.audio_utils import convert_audio
            
            audio, sr = audio_read(audio_path)
            audio = convert_audio(audio, sr, model.config.mimi.sample_rate, 1)
            
            # Encode audio to get the speaker embedding
            with torch.no_grad():
                audio_prompt = model._encode_audio(audio.unsqueeze(0))
            
            # Save as safetensors with metadata
            output_path = OUTPUT_DIR / f"{voice_id}.safetensors"
            metadata = VOICE_METADATA.get(voice_id, {})
            
            safetensors.torch.save_file(
                {"audio_prompt": audio_prompt.squeeze(0)},  # [frames, dim]
                str(output_path),
                metadata={
                    "voice_id": voice_id,
                    "name": metadata.get("name", voice_id.title()),
                    "gender": metadata.get("gender", "Unknown"),
                    "region": metadata.get("region", "Unknown"),
                }
            )
            
            print(f"  -> Saved to {output_path}")
            print(f"     Shape: {audio_prompt.shape}, Size: {os.path.getsize(output_path) / 1024:.1f} KB")
            
            all_voices[voice_id] = {
                "file": f"{voice_id}.safetensors",
                **metadata,
                "shape": list(audio_prompt.shape),
            }
            
        except Exception as e:
            print(f"  -> ERROR: {e}")
            import traceback
            traceback.print_exc()
    
    # Save manifest
    manifest_path = OUTPUT_DIR / "manifest.json"
    with open(manifest_path, "w") as f:
        json.dump(all_voices, f, indent=2)
    print(f"\nManifest saved to {manifest_path}")
    
    # Also create a single combined file for easier download
    print("\nCreating combined voices file...")
    all_embeddings = {}
    for voice_id in VOICES.keys():
        voice_file = OUTPUT_DIR / f"{voice_id}.safetensors"
        if voice_file.exists():
            data = safetensors.torch.load_file(str(voice_file))
            all_embeddings[f"{voice_id}_audio_prompt"] = data["audio_prompt"]
    
    combined_path = OUTPUT_DIR / "pocket_voices_combined.safetensors"
    safetensors.torch.save_file(all_embeddings, str(combined_path))
    print(f"Combined file saved to {combined_path} ({os.path.getsize(combined_path) / 1024:.1f} KB)")
    
    print("\nDone! Upload these files to GitHub releases or HuggingFace.")


if __name__ == "__main__":
    main()
