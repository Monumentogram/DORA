// Test-only Stage-0 5.4. Fresh process/context per clip; private files only.
#include "whisper.h"
#include "miniaudio.h"
#include <algorithm>
#include <atomic>
#include <chrono>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <limits>
#include <malloc.h>
#include <mutex>
#include <string>
#include <thread>
#include <unistd.h>
#include <vector>

static void quiet(enum ggml_log_level, const char *, void *) {}
static long long now_us() {
    return std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::steady_clock::now().time_since_epoch()).count();
}
static whisper_full_params parameters(const char * locale) {
    auto p=whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.n_threads=4;
    p.translate=false; p.language=locale; p.detect_language=false; p.no_context=true;
    p.initial_prompt=nullptr; p.prompt_tokens=nullptr; p.prompt_n_tokens=0;
    p.print_special=false; p.print_progress=false; p.print_realtime=false; p.print_timestamps=false;
    p.temperature=0.0f; p.temperature_inc=0.0f; // Argmax only; no stochastic fallback.
    return p;
}
static void profile(const char * locale) {
    auto p=parameters(locale);
    std::cout << std::boolalpha << std::setprecision(std::numeric_limits<float>::max_digits10);
    std::cout << "{\"strategy\":\"WHISPER_SAMPLING_GREEDY\",\"language\":\"" << locale << "\"";
#define F(name) std::cout << ",\"" #name "\":" << p.name
    F(n_threads); F(n_max_text_ctx); F(offset_ms); F(duration_ms); F(translate); F(no_context);
    F(no_timestamps); F(single_segment); F(print_special); F(print_progress); F(print_realtime);
    F(print_timestamps); F(token_timestamps); F(thold_pt); F(thold_ptsum); F(max_len);
    F(split_on_word); F(max_tokens); F(debug_mode); F(audio_ctx); F(tdrz_enable);
    F(carry_initial_prompt); F(prompt_n_tokens); F(detect_language); F(suppress_blank); F(suppress_nst);
    F(temperature); F(max_initial_ts); F(length_penalty); F(temperature_inc); F(entropy_thold);
    F(logprob_thold); F(no_speech_thold); F(greedy.best_of); F(beam_search.beam_size); F(beam_search.patience);
    F(n_grammar_rules); F(i_start_rule); F(grammar_penalty); F(vad);
    F(vad_params.threshold); F(vad_params.min_speech_duration_ms); F(vad_params.min_silence_duration_ms);
    F(vad_params.max_speech_duration_s); F(vad_params.speech_pad_ms); F(vad_params.samples_overlap);
#undef F
    std::cout << ",\"suppress_regex\":null,\"initial_prompt\":null,\"prompt_tokens\":null,"
        "\"new_segment_callback\":null,\"new_segment_callback_user_data\":null,"
        "\"progress_callback\":null,\"progress_callback_user_data\":null,"
        "\"encoder_begin_callback\":null,\"encoder_begin_callback_user_data\":null,"
        "\"abort_callback\":null,\"abort_callback_user_data\":null,"
        "\"logits_filter_callback\":null,\"logits_filter_callback_user_data\":null,"
        "\"grammar_rules\":null,\"vad_model_path\":null}" << std::endl;
}
static bool sample(FILE * f) {
    std::ifstream in("/proc/self/smaps_rollup"); std::string line; unsigned long long pss=0;
    while(std::getline(in,line)) if(line.rfind("Pss:",0)==0) {
        if(std::sscanf(line.c_str(),"Pss: %llu",&pss)!=1) return false;
    }
    const auto heap=static_cast<unsigned long long>(mallinfo().uordblks);
    if(!pss || !heap) return false;
    return std::fprintf(f,"%lld,%llu,%llu\n",now_us(),pss*1024,heap)>0 && std::fflush(f)==0;
}
static bool sync_close(FILE * f) {
    bool ok=std::fflush(f)==0 && fsync(fileno(f))==0;
    return std::fclose(f)==0 && ok;
}
int main(int argc,char ** argv) {
    if(argc!=3 || (std::strcmp(argv[2],"ru") && std::strcmp(argv[2],"en"))) return 10;
    whisper_log_set(quiet,nullptr);
    if(!std::strcmp(argv[1],"profile")) { profile(argv[2]); return 0; }
    const bool decode_only=!std::strcmp(argv[1],"decode");
    if(!decode_only && std::strcmp(argv[1],"infer")) return 10;
    alarm(605);
    FILE * pid=std::fopen("pid","wx");
    if(!pid) return 11;
    std::fprintf(pid,"%d\n",static_cast<int>(getpid()));
    if(!sync_close(pid)) return 11;
    FILE * memory=std::fopen("memory.csv","wx"); if(!memory) return 11;
    std::atomic<bool> stop{false}, memory_ok{true};
    std::mutex sample_mutex;
    auto take_sample=[&] { std::lock_guard<std::mutex> guard(sample_mutex); if(!sample(memory)) memory_ok=false; };
    const auto measurement_start=now_us();
    take_sample();
    std::thread sampler([&] {
        do { take_sample();
             std::this_thread::sleep_for(std::chrono::milliseconds(100));
        } while(!stop);
    });
    int result=0; long long load_us=0,infer_us=0,decode_us=0;
    ma_decoder decoder; auto dc=ma_decoder_config_init(ma_format_f32,1,16000);
    std::vector<float> pcm; auto start=now_us();
    if(ma_decoder_init_file("clip.audio",&dc,&decoder)!=MA_SUCCESS) result=12;
    else {
        float buffer[4096]; ma_uint64 got=0; ma_result status=MA_SUCCESS;
        do {
            status=ma_decoder_read_pcm_frames(&decoder,buffer,4096,&got);
            pcm.insert(pcm.end(),buffer,buffer+got);
            if(pcm.size()>60*16000) { result=12; break; }
        } while(status==MA_SUCCESS && got);
        if(status!=MA_SUCCESS && status!=MA_AT_END) result=12;
        ma_decoder_uninit(&decoder);
        if(pcm.empty()) result=12;
    }
    decode_us=now_us()-start;
    if(!result && !decode_only) {
        auto cp=whisper_context_default_params(); cp.use_gpu=false;
        take_sample();
        start=now_us(); auto * ctx=whisper_init_from_file_with_params("model.bin",cp);
        load_us=now_us()-start;
        take_sample();
        if(!ctx) result=13;
        else {
            start=now_us(); const int rc=whisper_full(ctx,parameters(argv[2]),pcm.data(),static_cast<int>(pcm.size()));
            infer_us=now_us()-start;
            take_sample();
            if(rc) result=14;
            else {
                FILE * hypothesis=std::fopen("hypothesis.txt","wx");
                if(!hypothesis) result=11;
                else {
                    for(int i=0;i<whisper_full_n_segments(ctx);i++) {
                        const char * text=whisper_full_get_segment_text(ctx,i);
                        if(!text || std::fwrite(text,1,std::strlen(text),hypothesis)!=std::strlen(text)) { result=11; break; }
                    }
                    if(!sync_close(hypothesis)) result=11;
                }
            }
            take_sample(); whisper_free(ctx);
        }
    }
    stop=true; sampler.join();
    if(!sample(memory) || !sync_close(memory) || !memory_ok) result=15;
    const auto measurement_end=now_us();
    FILE * metrics=std::fopen("metrics.json","wx"); if(!metrics) return 11;
    std::fprintf(metrics,"{\"nativeCode\":%d,\"decodedFrames\":%zu,\"sampleRate\":16000,\"decodeElapsedMicros\":%lld,\"modelLoadElapsedMicros\":%lld,\"inferenceElapsedMicros\":%lld,\"measurementStartMicros\":%lld,\"measurementEndMicros\":%lld}\n",result,pcm.size(),decode_us,load_us,infer_us,measurement_start,measurement_end);
    if(!sync_close(metrics)) return 11;
    return result;
}
