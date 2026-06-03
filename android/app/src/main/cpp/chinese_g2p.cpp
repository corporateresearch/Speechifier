// JNI bridge for Tier-C Mandarin G2P (cppjieba + pinyin lexicon).
//
// Integration (Milestone 9):
//   1. Vendor cppjieba (header-only) under cpp/cppjieba/ and add its include dir
//      to CMake.
//   2. nativeInit: construct cppjieba::Jieba from the dict files in jiebaDir;
//      load the pinyin lexicon (hanzi -> toned pinyin) from pinyinLexicon.
//   3. nativePhonemize: segment text -> per-word hanzi -> pinyin (with tone) ->
//      Kokoro Chinese phoneme set, returned space-separated.
//
// Until then these report not-ready so ChinesePhonemizer falls back cleanly.

#include <jni.h>
#include <string>

extern "C" JNIEXPORT jboolean JNICALL
Java_com_speechifier_tts_ChinesePhonemizer_nativeInit(
        JNIEnv* /*env*/, jobject /*thiz*/, jstring /*jiebaDir*/, jstring /*pinyinLexicon*/) {
    // TODO(Milestone 9): build cppjieba + load pinyin lexicon.
    return JNI_FALSE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_speechifier_tts_ChinesePhonemizer_nativePhonemize(
        JNIEnv* env, jobject /*thiz*/, jstring /*text*/) {
    // TODO(Milestone 9): jieba segment -> pinyin -> Kokoro phonemes.
    return env->NewStringUTF("");
}
