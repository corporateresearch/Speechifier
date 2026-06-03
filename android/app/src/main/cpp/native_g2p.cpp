// JNI bridge for the native G2P engines.
//
// Milestone 2 wires espeak-ng here (Tier A). Until then this is a stub that
// reports it is not yet initialised, so the Kotlin side can fall back cleanly.
//
// Expected eventual surface (matching tts/EspeakPhonemizer.kt):
//   nativeInit(dataPath: String): Boolean
//   nativePhonemize(text: String, langCode: String): String   // space-separated IPA

#include <jni.h>
#include <string>

extern "C" JNIEXPORT jboolean JNICALL
Java_com_speechifier_tts_EspeakPhonemizer_nativeInit(
        JNIEnv* /*env*/, jobject /*thiz*/, jstring /*dataPath*/) {
    // TODO(Milestone 2): initialise espeak-ng with the bundled data path.
    return JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_speechifier_tts_EspeakPhonemizer_nativePhonemize(
        JNIEnv* env, jobject /*thiz*/, jstring /*text*/, jstring /*langCode*/) {
    // TODO(Milestone 2): return space-separated IPA phonemes from espeak-ng.
    return env->NewStringUTF("");
}
