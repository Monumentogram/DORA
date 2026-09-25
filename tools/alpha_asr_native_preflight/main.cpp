// Isolated Stage-0 model-init boundary. Never calls whisper_full or emits text.
#include "whisper.h"
#include <csignal>
#include <cstdio>
#include <cstring>
#include <dlfcn.h>
#include <unistd.h>

static void quiet_log(enum ggml_log_level, const char *, void *) {}

int main(int argc, char ** argv) {
    alarm(125); // Safety ceiling independent of the host connection; no benchmark.
    if (argc != 2 || (std::strcmp(argv[1], "init") && std::strcmp(argv[1], "hold"))) return 3;
    FILE * pid = std::fopen("pid", "wx");
    if (!pid) return 3;
    std::fprintf(pid, "%d\n", static_cast<int>(getpid()));
    if (std::fclose(pid)) return 3;
    void * library = dlopen("libwhisper.so", RTLD_NOW | RTLD_LOCAL);
    if (!library) return 3;
    const char * symbols[] = {"whisper_context_default_params", "whisper_init_from_file_with_params",
                             "whisper_free", "whisper_log_set"};
    for (const char * symbol : symbols) {
        if (!dlsym(library, symbol)) { dlclose(library); return 3; }
    }
    whisper_log_set(quiet_log, nullptr);
    std::puts("DORA_ASR_BOUNDARY_V1");
    std::fflush(stdout);
    if (!std::strcmp(argv[1], "hold")) {
        for (;;) pause(); // Test-only stop/timeout probe; no model initialization.
    }
    auto params = whisper_context_default_params();
    params.use_gpu = false;
    auto * context = whisper_init_from_file_with_params("model.bin", params);
    if (!context) {
        std::puts("MODEL_LOAD_FAILURE");
        dlclose(library);
        return 2;
    }
    whisper_free(context);
    dlclose(library);
    std::puts("MODEL_INIT_SUCCESS");
    return 0;
}
