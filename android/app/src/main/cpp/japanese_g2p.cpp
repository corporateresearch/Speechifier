// JNI bridge for Tier-B Japanese G2P (OpenJTalk + UniDic).
//
// Integration (Milestone 9):
//   1. Add OpenJTalk's components to CMake: mecab, njd, jpcommon, text2mecab,
//      mecab2njd, njd2jpcommon (vendored under cpp/openjtalk/).
//   2. nativeInit: Mecab_initialize/Mecab_load with the UniDic dictionary at
//      dictDir; build the NJD/JPCommon pipeline.
//   3. nativePhonemize: text -> full-context labels -> Kokoro Japanese phoneme
//      set (with pitch-accent handling), returned space-separated.
//
// Until then these report not-ready so JapanesePhonemizer falls back cleanly.

#include <jni.h>
#include <string>

extern "C" JNIEXPORT jboolean JNICALL
Java_com_speechifier_tts_JapanesePhonemizer_nativeInit(
        JNIEnv* /*env*/, jobject /*thiz*/, jstring /*dictDir*/) {
    // TODO(Milestone 9): initialise OpenJTalk + UniDic from dictDir.
    return JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_speechifier_tts_JapanesePhonemizer_nativePhonemize(
        JNIEnv* env, jobject /*thiz*/, jstring /*text*/) {
    // TODO(Milestone 9): OpenJTalk full-context labels -> Kokoro phonemes.
    return env->NewStringUTF("");
}
