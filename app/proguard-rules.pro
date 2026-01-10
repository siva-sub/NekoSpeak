# ProGuard rules for NekoSpeak TTS

# Keep ONNX Runtime
-keep class ai.onnxruntime.** { *; }
-keepclassmembers class ai.onnxruntime.** { *; }

# Keep Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep JNI Wrapper (Critical for native function lookup)
# Keep ALL Engine components (Piper, Misaki, Kokoro, Espeak, Common)
-keep class com.nekospeak.tts.engine.** { *; }

# Keep ALL Data models (Gson, Repo, etc)
-keep class com.nekospeak.tts.data.** { *; }

# Keep Material3 to prevent NoSuchMethodError in Release builds
-keep class androidx.compose.material3.** { *; }
-keep interface androidx.compose.material3.** { *; }
-dontwarn androidx.compose.material3.**

# Gson Specific Rules (CRITICAL for TypeToken)
-keepattributes Signature
-keepattributes *Annotation*
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
