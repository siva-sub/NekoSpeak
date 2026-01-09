#include <jni.h>
#include <string.h>
#include <android/log.h>
#include <espeak-ng/speak_lib.h> 

#define TAG "EspeakJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

JNIEXPORT jint JNICALL
Java_com_nekospeak_tts_engine_EspeakWrapper_initialize(JNIEnv *env, jobject thiz, jstring dataPath) {
    const char *path = (*env)->GetStringUTFChars(env, dataPath, 0);
    int result = espeak_Initialize(AUDIO_OUTPUT_SYNCHRONOUS, 0, path, 0);
    (*env)->ReleaseStringUTFChars(env, dataPath, path);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_nekospeak_tts_engine_EspeakWrapper_textToPhonemes(JNIEnv *env, jobject thiz, jstring text, jstring language) {
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
    const char *phonemes = espeak_TextToPhonemes(&text_ptr, espeakCHARS_UTF8, 0x02); // 0x02 = espeakPHONEMES_IPA
    
    jstring result = (*env)->NewStringUTF(env, phonemes ? phonemes : "");
    
    (*env)->ReleaseStringUTFChars(env, text, c_text);
    (*env)->ReleaseStringUTFChars(env, language, c_lang);
    return result;
}
