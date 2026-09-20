//
// llm_jni.cpp - AdaptIQ JNI bridge for MNN-LLM inference engine
//
// Bridges Alibaba MNN Transformer::Llm to Kotlin via JNI callbacks.
// Uses the official MNN LlmStreamBuffer pattern (custom std::streambuf)
// to intercept ostream writes from Llm::response() and convert them
// into per-token JNI callbacks to the Kotlin MnnBridge.
//

#include <jni.h>
#include <string>
#include <memory>
#include <mutex>
#include <atomic>
#include <functional>
#include <ostream>
#include <sstream>
#include <android/log.h>

#include "llm/llm.hpp"
#include "MNN/expr/ExecutorScope.hpp"

#define LOG_TAG "AdaptIQ-Native"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

using MNN::Transformer::Llm;
using MNN::Transformer::LlmContext;

// ═══════════════════════════════════════════════════════════════════════
// LlmStreamBuffer - Custom streambuf that intercepts ostream writes
// ═══════════════════════════════════════════════════════════════════════
// This is the core streaming mechanism. MNN's Llm::response() writes
// generated tokens to a std::ostream*. We subclass std::streambuf to
// intercept each write and forward it as a JNI callback to Kotlin.
//
// This is the same pattern used by the official MNN Chat Android app.
// ═══════════════════════════════════════════════════════════════════════
class LlmStreamBuffer : public std::streambuf {
public:
    using Callback = std::function<void(const char* str, size_t len)>;

    explicit LlmStreamBuffer(Callback callback)
        : callback_(std::move(callback)) {}

protected:
    std::streamsize xsputn(const char* s, std::streamsize n) override {
        if (callback_) {
            callback_(s, n);
        }
        return n;
    }

    int overflow(int c) override {
        if (c != EOF && callback_) {
            char ch = static_cast<char>(c);
            callback_(&ch, 1);
        }
        return c;
    }

private:
    Callback callback_ = nullptr;
};

// ═══════════════════════════════════════════════════════════════════════
// Global State
// ═══════════════════════════════════════════════════════════════════════
static JavaVM* g_jvm = nullptr;
static Llm* g_llm = nullptr;
static std::mutex g_mutex;
static std::atomic<bool> g_is_generating{false};

// ═══════════════════════════════════════════════════════════════════════
// JNI Lifecycle
// ═══════════════════════════════════════════════════════════════════════
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    g_jvm = vm;
    LOGI("JNI_OnLoad: JavaVM cached");
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL JNI_OnUnload(JavaVM* vm, void* reserved) {
    g_jvm = nullptr;
    LOGI("JNI_OnUnload: JavaVM cleared");
}

// Helper: Get JNIEnv for current thread (attach if needed)
static JNIEnv* getEnv(bool* didAttach) {
    JNIEnv* env = nullptr;
    *didAttach = false;
    if (g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) == JNI_EDETACHED) {
        g_jvm->AttachCurrentThread(&env, nullptr);
        *didAttach = true;
    }
    return env;
}

static void detachIfNeeded(bool didAttach) {
    if (didAttach) {
        g_jvm->DetachCurrentThread();
    }
}

// ═══════════════════════════════════════════════════════════════════════
// nativeCreateLLM — Initialize and load the MNN model
// ═══════════════════════════════════════════════════════════════════════
extern "C" JNIEXPORT jboolean JNICALL
Java_com_adaptiq_tutor_engine_MnnBridge_nativeCreateLLM(
    JNIEnv* env, jobject /* this */,
    jstring configPath) {

    std::lock_guard<std::mutex> lock(g_mutex);

    const char* path = env->GetStringUTFChars(configPath, nullptr);
    if (!path) {
        LOGE("nativeCreateLLM: configPath is null");
        return JNI_FALSE;
    }

    LOGI("nativeCreateLLM: Loading model from: %s", path);

    try {
        // Release any existing model
        if (g_llm) {
            Llm::destroy(g_llm);
            g_llm = nullptr;
        }

        // Create the LLM instance from config JSON path
        g_llm = Llm::createLLM(path);
        if (!g_llm) {
            LOGE("nativeCreateLLM: Llm::createLLM returned nullptr");
            env->ReleaseStringUTFChars(configPath, path);
            return JNI_FALSE;
        }

        // Load model weights into memory
        if (!g_llm->load()) {
            LOGE("nativeCreateLLM: g_llm->load() failed for: %s", path);
            Llm::destroy(g_llm);
            g_llm = nullptr;
            env->ReleaseStringUTFChars(configPath, path);
            return JNI_FALSE;
        }

        LOGI("nativeCreateLLM: Model loaded successfully");
        env->ReleaseStringUTFChars(configPath, path);
        return JNI_TRUE;

    } catch (const std::exception& e) {
        LOGE("nativeCreateLLM: Exception: %s", e.what());
        if (g_llm) {
            Llm::destroy(g_llm);
            g_llm = nullptr;
        }
        env->ReleaseStringUTFChars(configPath, path);
        return JNI_FALSE;
    }
}

// ═══════════════════════════════════════════════════════════════════════
// nativeSetConfig — Apply runtime configuration (precision, threads, etc.)
// ═══════════════════════════════════════════════════════════════════════
extern "C" JNIEXPORT jboolean JNICALL
Java_com_adaptiq_tutor_engine_MnnBridge_nativeSetConfig(
    JNIEnv* env, jobject /* this */,
    jstring configJson) {

    if (!g_llm) {
        LOGE("nativeSetConfig: Model not loaded");
        return JNI_FALSE;
    }

    const char* json = env->GetStringUTFChars(configJson, nullptr);
    bool success = g_llm->set_config(std::string(json));
    env->ReleaseStringUTFChars(configJson, json);

    LOGI("nativeSetConfig: %s", success ? "applied" : "failed");
    return success ? JNI_TRUE : JNI_FALSE;
}

// ═══════════════════════════════════════════════════════════════════════
// nativeGenerateStream — Stream generation with per-token JNI callbacks
// ═══════════════════════════════════════════════════════════════════════
// Uses LlmStreamBuffer to intercept MNN's ostream output per-token and
// call back to Kotlin's NativeTokenCallback.onToken(String) for each piece.
// ═══════════════════════════════════════════════════════════════════════
extern "C" JNIEXPORT void JNICALL
Java_com_adaptiq_tutor_engine_MnnBridge_nativeGenerateStream(
    JNIEnv* env, jobject /* this */,
    jstring prompt, jobject callback) {

    if (!g_llm) {
        LOGE("nativeGenerateStream: Model not loaded");
        // Call onError
        jclass cls = env->GetObjectClass(callback);
        jmethodID onError = env->GetMethodID(cls, "onError", "(Ljava/lang/String;)V");
        if (onError) {
            env->CallVoidMethod(callback, onError, env->NewStringUTF("Model not loaded"));
        }
        return;
    }

    if (g_is_generating.exchange(true)) {
        LOGE("nativeGenerateStream: Already generating, ignoring request");
        return;
    }

    // Convert Java string
    const char* promptStr = env->GetStringUTFChars(prompt, nullptr);
    std::string promptCpp(promptStr);
    env->ReleaseStringUTFChars(prompt, promptStr);

    // Grab global refs for the callback (survives across the native call)
    jobject callbackRef = env->NewGlobalRef(callback);
    jclass callbackCls = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackCls, "onToken", "([B)V");
    jmethodID onCompleteMethod = env->GetMethodID(callbackCls, "onComplete", "(Ljava/lang/String;)V");
    jmethodID onErrorMethod = env->GetMethodID(callbackCls, "onError", "(Ljava/lang/String;)V");

    if (!onTokenMethod || !onCompleteMethod || !onErrorMethod) {
        LOGE("nativeGenerateStream: Failed to resolve callback methods");
        env->DeleteGlobalRef(callbackRef);
        g_is_generating.store(false);
        return;
    }

    LOGI("nativeGenerateStream: Starting generation, prompt_len=%zu", promptCpp.size());

    try {
        std::string fullResponse;

        // ─── Core streaming mechanism ─────────────────────────────────
        // Create a custom streambuf that intercepts each token write
        // from Llm::response() and forwards it to the Kotlin callback.
        LlmStreamBuffer streamBuf([&](const char* str, size_t len) {
            if (!g_is_generating.load()) return;

            std::string token(str, len);
            fullResponse += token;

            bool didAttach = false;
            JNIEnv* cbEnv = getEnv(&didAttach);
            if (cbEnv) {
                jbyteArray jTokenArray = cbEnv->NewByteArray(len);
                if (jTokenArray) {
                    cbEnv->SetByteArrayRegion(jTokenArray, 0, len, reinterpret_cast<const jbyte*>(str));
                    cbEnv->CallVoidMethod(callbackRef, onTokenMethod, jTokenArray);
                    cbEnv->DeleteLocalRef(jTokenArray);
                }
            }
            detachIfNeeded(didAttach);
        });

        // Wrap the streambuf in a std::ostream and pass to MNN
        // Encode the raw prompt and pass input_ids to bypass MNN's prompt_template
        std::vector<int> inputIds = g_llm->tokenizer_encode(promptCpp);
        if (inputIds.empty()) {
            LOGE("nativeGenerateStream: inputIds is empty for prompt length %zu", promptCpp.size());
            bool didAttach = false;
            JNIEnv* cbEnv = getEnv(&didAttach);
            if (cbEnv) {
                jstring jErr = cbEnv->NewStringUTF("Failed to tokenize prompt");
                if (jErr) {
                    cbEnv->CallVoidMethod(callbackRef, onErrorMethod, jErr);
                    cbEnv->DeleteLocalRef(jErr);
                }
            }
            detachIfNeeded(didAttach);
            env->DeleteGlobalRef(callbackRef);
            g_is_generating.store(false);
            return;
        }

        std::ostream tokenStream(&streamBuf);
        g_llm->response(inputIds, &tokenStream);
        // ──────────────────────────────────────────────────────────────

        // Signal completion with the full assembled response
        {
            bool didAttach = false;
            JNIEnv* cbEnv = getEnv(&didAttach);
            if (cbEnv) {
                jstring jFull = cbEnv->NewStringUTF(fullResponse.c_str());
                if (jFull) {
                    cbEnv->CallVoidMethod(callbackRef, onCompleteMethod, jFull);
                    cbEnv->DeleteLocalRef(jFull);
                }
            }
            detachIfNeeded(didAttach);
        }

    } catch (const std::exception& e) {
        LOGE("nativeGenerateStream: Exception: %s", e.what());
        bool didAttach = false;
        JNIEnv* cbEnv = getEnv(&didAttach);
        if (cbEnv) {
            jstring jErr = cbEnv->NewStringUTF(e.what());
            if (jErr) {
                cbEnv->CallVoidMethod(callbackRef, onErrorMethod, jErr);
                cbEnv->DeleteLocalRef(jErr);
            }
        }
        detachIfNeeded(didAttach);
    }

    // Cleanup global ref
    {
        bool didAttach = false;
        JNIEnv* cbEnv = getEnv(&didAttach);
        if (cbEnv) {
            cbEnv->DeleteGlobalRef(callbackRef);
        }
        detachIfNeeded(didAttach);
    }

    g_is_generating.store(false);
    LOGI("nativeGenerateStream: Generation complete");
}

// ═══════════════════════════════════════════════════════════════════════
// nativeGenerateWithHistory — Multi-turn conversation with ChatMessages
// ═══════════════════════════════════════════════════════════════════════
// Uses MNN's native ChatMessages (vector<pair<string,string>>) API for
// proper multi-turn conversation with the model's built-in chat template.
// ═══════════════════════════════════════════════════════════════════════
extern "C" JNIEXPORT void JNICALL
Java_com_adaptiq_tutor_engine_MnnBridge_nativeGenerateWithHistory(
    JNIEnv* env, jobject /* this */,
    jobjectArray roles, jobjectArray contents,
    jobject callback) {

    if (!g_llm) {
        LOGE("nativeGenerateWithHistory: Model not loaded");
        return;
    }

    if (g_is_generating.exchange(true)) {
        LOGE("nativeGenerateWithHistory: Already generating");
        return;
    }

    // Build ChatMessages from Java arrays
    MNN::Transformer::ChatMessages chatMessages;
    jsize msgCount = env->GetArrayLength(roles);
    for (jsize i = 0; i < msgCount; i++) {
        jstring role = (jstring)env->GetObjectArrayElement(roles, i);
        jstring content = (jstring)env->GetObjectArrayElement(contents, i);
        const char* roleStr = env->GetStringUTFChars(role, nullptr);
        const char* contentStr = env->GetStringUTFChars(content, nullptr);
        chatMessages.emplace_back(std::string(roleStr), std::string(contentStr));
        env->ReleaseStringUTFChars(role, roleStr);
        env->ReleaseStringUTFChars(content, contentStr);
        env->DeleteLocalRef(role);
        env->DeleteLocalRef(content);
    }

    // Setup callback refs
    jobject callbackRef = env->NewGlobalRef(callback);
    jclass callbackCls = env->GetObjectClass(callback);
    jmethodID onTokenMethod = env->GetMethodID(callbackCls, "onToken", "([B)V");
    jmethodID onCompleteMethod = env->GetMethodID(callbackCls, "onComplete", "(Ljava/lang/String;)V");
    jmethodID onErrorMethod = env->GetMethodID(callbackCls, "onError", "(Ljava/lang/String;)V");

    LOGI("nativeGenerateWithHistory: %d messages", (int)chatMessages.size());

    try {
        std::string fullResponse;

        LlmStreamBuffer streamBuf([&](const char* str, size_t len) {
            if (!g_is_generating.load()) return;
            std::string token(str, len);
            fullResponse += token;

            bool didAttach = false;
            JNIEnv* cbEnv = getEnv(&didAttach);
            if (cbEnv) {
                jbyteArray jTokenArray = cbEnv->NewByteArray(len);
                if (jTokenArray) {
                    cbEnv->SetByteArrayRegion(jTokenArray, 0, len, reinterpret_cast<const jbyte*>(str));
                    cbEnv->CallVoidMethod(callbackRef, onTokenMethod, jTokenArray);
                    cbEnv->DeleteLocalRef(jTokenArray);
                }
            }
            detachIfNeeded(didAttach);
        });

        std::ostream tokenStream(&streamBuf);

        // Use ChatMessages overload — MNN applies the model's native chat template
        g_llm->response(chatMessages, &tokenStream, "<|im_end|>");

        LOGI("nativeGenerateWithHistory: Response completed, length=%zu", fullResponse.size());

        // Signal completion
        {
            bool didAttach = false;
            JNIEnv* cbEnv = getEnv(&didAttach);
            if (cbEnv) {
                jstring jFull = cbEnv->NewStringUTF(fullResponse.c_str());
                if (jFull) {
                    cbEnv->CallVoidMethod(callbackRef, onCompleteMethod, jFull);
                    cbEnv->DeleteLocalRef(jFull);
                }
            }
            detachIfNeeded(didAttach);
        }

    } catch (const std::exception& e) {
        LOGE("nativeGenerateWithHistory: Exception: %s", e.what());
        bool didAttach = false;
        JNIEnv* cbEnv = getEnv(&didAttach);
        if (cbEnv) {
            jstring jErr = cbEnv->NewStringUTF(e.what());
            if (jErr) {
                cbEnv->CallVoidMethod(callbackRef, onErrorMethod, jErr);
                cbEnv->DeleteLocalRef(jErr);
            }
        }
        detachIfNeeded(didAttach);
    }

    // Cleanup
    {
        bool didAttach = false;
        JNIEnv* cbEnv = getEnv(&didAttach);
        if (cbEnv) {
            cbEnv->DeleteGlobalRef(callbackRef);
        }
        detachIfNeeded(didAttach);
    }

    g_is_generating.store(false);
    LOGI("nativeGenerateWithHistory: Cleanup complete");
}

// ═══════════════════════════════════════════════════════════════════════
// nativeCancelGeneration — Signal the native engine to stop
// ═══════════════════════════════════════════════════════════════════════
extern "C" JNIEXPORT void JNICALL
Java_com_adaptiq_tutor_engine_MnnBridge_nativeCancelGeneration(
    JNIEnv* env, jobject /* this */) {
    LOGI("nativeCancelGeneration: Cancelling...");
    g_is_generating.store(false);
}

// ═══════════════════════════════════════════════════════════════════════
// nativeReset — Reset conversation state (clears KV cache & history)
// ═══════════════════════════════════════════════════════════════════════
extern "C" JNIEXPORT void JNICALL
Java_com_adaptiq_tutor_engine_MnnBridge_nativeReset(
    JNIEnv* env, jobject /* this */) {
    if (g_llm) {
        LOGI("nativeReset: Resetting conversation state");
        g_llm->reset();
    }
}

// ═══════════════════════════════════════════════════════════════════════
// nativeReleaseModel — Destroy the model and free all native memory
// ═══════════════════════════════════════════════════════════════════════
extern "C" JNIEXPORT void JNICALL
Java_com_adaptiq_tutor_engine_MnnBridge_nativeReleaseModel(
    JNIEnv* env, jobject /* this */) {
    std::lock_guard<std::mutex> lock(g_mutex);
    LOGI("nativeReleaseModel: Releasing model...");
    g_is_generating.store(false);
    if (g_llm) {
        Llm::destroy(g_llm);  // Use MNN's destroy for Windows RT compat
        g_llm = nullptr;
    }
    LOGI("nativeReleaseModel: Model released");
}

// ═══════════════════════════════════════════════════════════════════════
// nativeIsLoaded — Check if a model is currently loaded
// ═══════════════════════════════════════════════════════════════════════
extern "C" JNIEXPORT jboolean JNICALL
Java_com_adaptiq_tutor_engine_MnnBridge_nativeIsLoaded(
    JNIEnv* env, jobject /* this */) {
    return g_llm != nullptr ? JNI_TRUE : JNI_FALSE;
}

// ═══════════════════════════════════════════════════════════════════════
// nativeDumpConfig — Get the model's current runtime configuration
// ═══════════════════════════════════════════════════════════════════════
extern "C" JNIEXPORT jstring JNICALL
Java_com_adaptiq_tutor_engine_MnnBridge_nativeDumpConfig(
    JNIEnv* env, jobject /* this */) {
    if (!g_llm) {
        return env->NewStringUTF("{}");
    }
    return env->NewStringUTF(g_llm->dump_config().c_str());
}
