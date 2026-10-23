#include <jni.h>
#ifndef DORA_TEST_X86_RUNTIME
#include <asm/hwcap.h>
#endif
#include <sys/auxv.h>
#include <dlfcn.h>
#include <atomic>
#include <cmath>
#include <cstring>
#include <memory>
#include <mutex>
#include <stdexcept>
#include <string>
#include <vector>
#include "whisper.h"

namespace {
struct Failure { const char * category; };
constexpr size_t kMaximumSamples = 300 * 16000;
constexpr size_t kMaximumTextBytes = 1024 * 1024;

struct Api {
    decltype(&whisper_context_default_params) context_params = nullptr;
    decltype(&whisper_full_default_params) full_params = nullptr;
    decltype(&whisper_init_from_file_with_params) init = nullptr;
    decltype(&whisper_full) full = nullptr;
    decltype(&whisper_full_n_segments) segment_count = nullptr;
    decltype(&whisper_full_get_segment_text) segment_text = nullptr;
    decltype(&whisper_free) free = nullptr;
    decltype(&whisper_log_set) log = nullptr;
};
Api api;
std::once_flag initialize;
std::mutex inference;
bool ready = false;

void quiet(ggml_log_level, const char *, void *) {}

bool available() {
    std::call_once(initialize, [] {
#ifndef DORA_TEST_X86_RUNTIME
        constexpr unsigned long required = HWCAP_ASIMD | HWCAP_FPHP | HWCAP_ASIMDHP | HWCAP_ASIMDDP;
        if ((getauxval(AT_HWCAP) & required) != required) return;
#endif
        void * library = dlopen("libwhisper.so", RTLD_NOW | RTLD_LOCAL);
        if (!library) return;
#define RESOLVE(member, name) api.member = reinterpret_cast<decltype(api.member)>(dlsym(library, #name))
        RESOLVE(context_params, whisper_context_default_params);
        RESOLVE(full_params, whisper_full_default_params);
        RESOLVE(init, whisper_init_from_file_with_params);
        RESOLVE(full, whisper_full);
        RESOLVE(segment_count, whisper_full_n_segments);
        RESOLVE(segment_text, whisper_full_get_segment_text);
        RESOLVE(free, whisper_free);
        RESOLVE(log, whisper_log_set);
#undef RESOLVE
        ready = api.context_params && api.full_params && api.init && api.full && api.segment_count &&
            api.segment_text && api.free && api.log;
        if (ready) api.log(quiet, nullptr);
        else dlclose(library);
        // Successful runtime stays loaded; callbacks and contexts never outlive their code.
    });
    return ready;
}

struct WipedSamples {
    std::vector<float> values;
    ~WipedSamples() {
        volatile float * data = values.data();
        for (size_t index = 0; index < values.size(); ++index) data[index] = 0;
    }
};

struct Cancellation {
    JavaVM * vm = nullptr;
    JNIEnv * caller = nullptr;
    jobject probe = nullptr;
    jmethodID method = nullptr;
    std::atomic<bool> stopped{false};

    Cancellation(JNIEnv * env, jobject value) : caller(env) {
        if (env->GetJavaVM(&vm) != JNI_OK) throw Failure{"ASR_CANCELLED"};
        jclass type = env->GetObjectClass(value);
        if (!type) throw Failure{"ASR_CANCELLED"};
        method = env->GetMethodID(type, "cancelled", "()Z");
        env->DeleteLocalRef(type);
        if (!method) throw Failure{"ASR_CANCELLED"};
        probe = env->NewGlobalRef(value);
        if (!probe) throw Failure{"ASR_CANCELLED"};
    }
    ~Cancellation() { if (probe) caller->DeleteGlobalRef(probe); }

    bool check() {
        if (stopped.load()) return true;
        JNIEnv * env = nullptr;
        bool attached = false;
        jint status = vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6);
        if (status == JNI_EDETACHED) {
            if (vm->AttachCurrentThread(&env, nullptr) != JNI_OK) {
                stopped.store(true);
                return true;
            }
            attached = true;
        } else if (status != JNI_OK) {
            stopped.store(true);
            return true;
        }
        const bool result = env->CallBooleanMethod(probe, method) == JNI_TRUE;
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
            stopped.store(true);
        }
        if (result) stopped.store(true);
        if (attached) vm->DetachCurrentThread();
        return stopped.load();
    }
};

bool abort_inference(void * value) { return static_cast<Cancellation *>(value)->check(); }
bool begin_encoder(whisper_context *, whisper_state *, void * value) { return !abort_inference(value); }

// JNI NewStringUTF expects modified UTF-8. Decode standard UTF-8 strictly to UTF-16 instead.
std::vector<jchar> utf16(const std::string & text) {
    std::vector<jchar> result;
    for (size_t index = 0; index < text.size();) {
        const auto first = static_cast<unsigned char>(text[index++]);
        uint32_t code = 0;
        int continuation = 0;
        uint32_t minimum = 0;
        if (first < 0x80) code = first;
        else if (first >= 0xc2 && first <= 0xdf) { code = first & 0x1f; continuation = 1; minimum = 0x80; }
        else if (first >= 0xe0 && first <= 0xef) { code = first & 0x0f; continuation = 2; minimum = 0x800; }
        else if (first >= 0xf0 && first <= 0xf4) { code = first & 0x07; continuation = 3; minimum = 0x10000; }
        else throw Failure{"ASR_TEXT_INVALID"};
        for (int count = 0; count < continuation; ++count) {
            if (index == text.size()) throw Failure{"ASR_TEXT_INVALID"};
            const auto next = static_cast<unsigned char>(text[index++]);
            if ((next & 0xc0) != 0x80) throw Failure{"ASR_TEXT_INVALID"};
            code = (code << 6) | (next & 0x3f);
        }
        if (code < minimum || code > 0x10ffff || (code >= 0xd800 && code <= 0xdfff))
            throw Failure{"ASR_TEXT_INVALID"};
        if (code <= 0xffff) result.push_back(static_cast<jchar>(code));
        else {
            code -= 0x10000;
            result.push_back(static_cast<jchar>(0xd800 | (code >> 10)));
            result.push_back(static_cast<jchar>(0xdc00 | (code & 0x3ff)));
        }
    }
    return result;
}

struct UtfChars {
    JNIEnv * env;
    jstring value;
    const char * chars;
    UtfChars(JNIEnv * environment, jstring input) : env(environment), value(input), chars(env->GetStringUTFChars(value, nullptr)) {
        if (!chars) throw Failure{"ASR_INPUT_INVALID"};
    }
    ~UtfChars() { env->ReleaseStringUTFChars(value, chars); }
};

void fail(JNIEnv * env, const char * category) {
    if (env->ExceptionCheck()) env->ExceptionClear();
    jclass type = env->FindClass("java/lang/IllegalStateException");
    if (type) {
        env->ThrowNew(type, category);
        env->DeleteLocalRef(type);
    }
}
} // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_com_monumentogram_dora_transcript_WhisperNative_nativeAvailable(JNIEnv *, jobject) {
    try { return available() ? JNI_TRUE : JNI_FALSE; }
    catch (...) { return JNI_FALSE; }
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_monumentogram_dora_transcript_WhisperNative_nativeRecognize(
    JNIEnv * env, jobject, jstring model, jfloatArray samples, jstring language, jobject probe) {
    try {
        if (!model || !samples || !language || !probe) throw Failure{"ASR_INPUT_INVALID"};
        if (!available()) throw Failure{"ASR_RUNTIME_UNAVAILABLE"};
        std::unique_lock<std::mutex> lock(inference, std::try_to_lock);
        if (!lock.owns_lock()) throw Failure{"ASR_BUSY"};
        Cancellation cancellation(env, probe);
        if (cancellation.check()) throw Failure{"ASR_CANCELLED"};
        const jsize count = env->GetArrayLength(samples);
        if (count <= 0 || static_cast<size_t>(count) > kMaximumSamples) throw Failure{"ASR_PCM_INVALID"};
        WipedSamples pcm;
        pcm.values.resize(count);
        env->GetFloatArrayRegion(samples, 0, count, pcm.values.data());
        if (env->ExceptionCheck()) throw Failure{"ASR_PCM_INVALID"};
        for (float value : pcm.values) {
            if (!std::isfinite(value) || value < -1 || value > 1) throw Failure{"ASR_PCM_INVALID"};
        }
        UtfChars path(env, model);
        UtfChars locale(env, language);
        if (std::strcmp(locale.chars, "ru") && std::strcmp(locale.chars, "en"))
            throw Failure{"ASR_LANGUAGE_UNSUPPORTED"};
        auto context_params = api.context_params();
        context_params.use_gpu = false;
        context_params.flash_attn = true;
        std::unique_ptr<whisper_context, decltype(api.free)> context(api.init(path.chars, context_params), api.free);
        if (!context) throw Failure{"ASR_MODEL_UNAVAILABLE"};
        if (cancellation.check()) throw Failure{"ASR_CANCELLED"};
        auto params = api.full_params(WHISPER_SAMPLING_GREEDY);
        params.n_threads = 4;
        params.translate = false;
        params.language = locale.chars;
        params.detect_language = false;
        params.no_context = true;
        params.initial_prompt = nullptr;
        params.prompt_tokens = nullptr;
        params.prompt_n_tokens = 0;
        params.print_special = false;
        params.print_progress = false;
        params.print_realtime = false;
        params.print_timestamps = false;
        params.temperature = 0;
        params.temperature_inc = 0;
        params.abort_callback = abort_inference;
        params.abort_callback_user_data = &cancellation;
        params.encoder_begin_callback = begin_encoder;
        params.encoder_begin_callback_user_data = &cancellation;
        const int status = api.full(context.get(), params, pcm.values.data(), count);
        if (cancellation.check()) throw Failure{"ASR_CANCELLED"};
        if (status != 0) throw Failure{"ASR_INFERENCE_FAILED"};
        std::string text;
        const int segments = api.segment_count(context.get());
        if (segments < 0 || segments > 10000) throw Failure{"ASR_TEXT_INVALID"};
        for (int index = 0; index < segments; ++index) {
            const char * segment = api.segment_text(context.get(), index);
            if (!segment) throw Failure{"ASR_TEXT_INVALID"};
            const size_t length = strnlen(segment, kMaximumTextBytes + 1);
            if (length > kMaximumTextBytes - text.size()) throw Failure{"ASR_TEXT_INVALID"};
            text.append(segment, length);
        }
        const auto decoded = utf16(text);
        if (cancellation.check()) throw Failure{"ASR_CANCELLED"};
        return env->NewString(decoded.data(), static_cast<jsize>(decoded.size()));
    } catch (const Failure & error) {
        fail(env, error.category); // Only fixed categories originate above; never library diagnostics.
    } catch (...) {
        fail(env, "ASR_NATIVE_FAILED");
    }
    return nullptr;
}
