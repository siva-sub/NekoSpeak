#include <android/log.h>
#include <espeak-ng/speak_lib.h>
#include <jni.h>
#include <stdlib.h>
#include <string.h>

#define TAG "EspeakJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

JNIEXPORT jint JNICALL Java_com_nekospeak_tts_engine_EspeakWrapper_initialize(
    JNIEnv *env, jobject thiz, jstring dataPath) {
  const char *path = (*env)->GetStringUTFChars(env, dataPath, 0);
  int result = espeak_Initialize(AUDIO_OUTPUT_SYNCHRONOUS, 0, path, 0);
  (*env)->ReleaseStringUTFChars(env, dataPath, path);
  return result;
}

JNIEXPORT jstring JNICALL
Java_com_nekospeak_tts_engine_EspeakWrapper_textToPhonemes(JNIEnv *env,
                                                           jobject thiz,
                                                           jstring text,
                                                           jstring language) {
  const char *c_text = (*env)->GetStringUTFChars(env, text, 0);
  const char *c_lang = (*env)->GetStringUTFChars(env, language, 0);

  // Set voice
  if (espeak_SetVoiceByName(c_lang) != EE_OK) {
    LOGE("Failed to set voice: %s", c_lang);
    (*env)->ReleaseStringUTFChars(env, text, c_text);
    (*env)->ReleaseStringUTFChars(env, language, c_lang);
    return (*env)->NewStringUTF(env, "");
  }

  // Convert to phonemes
  // espeak_TextToPhonemes(const void **textptr, int textmode, int phonememode)
  // textmode: espeakCHARS_UTF8 (1)
  // phonememode: espeakPHONEMES_IPA (0x02)

  const void *text_ptr = c_text;

// Buffer for accumulated phonemes
// 16KB should be sufficient for most mobile TTS paragraphs
#define MAX_PHONEME_BUFFER 16384
  char *buffer = (char *)malloc(MAX_PHONEME_BUFFER);
  if (buffer == NULL) {
    LOGE("Failed to allocate phoneme buffer");
    (*env)->ReleaseStringUTFChars(env, text, c_text);
    (*env)->ReleaseStringUTFChars(env, language, c_lang);
    return (*env)->NewStringUTF(env, "");
  }
  buffer[0] = '\0';
  size_t current_len = 0;

  while (text_ptr != NULL) {
    // 0x02 = espeakPHONEMES_IPA
    const char *phonemes =
        espeak_TextToPhonemes(&text_ptr, espeakCHARS_UTF8, 0x02);

    if (phonemes == NULL || strlen(phonemes) == 0)
      break;

    size_t len = strlen(phonemes);
    // Add space separator between chunks (except for first chunk)
    if (current_len > 0 && current_len + len + 2 < MAX_PHONEME_BUFFER) {
      strcat(buffer, " ");
      current_len += 1;
    }

    if (current_len + len + 1 < MAX_PHONEME_BUFFER) {
      strcat(buffer, phonemes);
      current_len += len;
    } else {
      LOGE("Phoneme buffer overflow");
      break;
    }

    // espeak_TextToPhonemes updates text_ptr.
    // If it points to null terminator or we are not making progress (logic
    // check), break? Usually it returns NULL when done, but let's be safe.
    // Wait, typical usage relies on text_ptr being updated to NULL eventually?
    // Actually the docs say: "Returns NULL if end of text."
    // So the phonemes check above handles the break.
    // BUT, sometimes it returns "" and advances?
  }

  jstring result = (*env)->NewStringUTF(env, buffer);

  free(buffer);
  (*env)->ReleaseStringUTFChars(env, text, c_text);
  (*env)->ReleaseStringUTFChars(env, language, c_lang);
  return result;
}
