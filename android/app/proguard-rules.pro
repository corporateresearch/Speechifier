# ONNX Runtime ships JNI bindings reached via reflection — keep them.
-keep class ai.onnxruntime.** { *; }

# PdfBox-Android relies on some reflective font/encoding lookups.
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**

# Native G2P JNI entry points (espeak-ng / OpenJTalk / cppjieba wrappers).
-keepclasseswithmembernames class * {
    native <methods>;
}
