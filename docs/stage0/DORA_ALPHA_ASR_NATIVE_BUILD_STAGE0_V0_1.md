# DORA Alpha ASR isolated native build — Stage 0 v0.1

Task **5.3B**, 2026-09-24. **PASS / ISOLATED_WHISPER_CPP_ANDROID_16K_CANDIDATE_VERIFIED**.

Branch: chat/alpha-asr-runner-scope. Starting HEAD: a2b22ad1e350db387acb2367743194991ac5511e.
The owner authorized toolchain installation and reproducible SDK/cache cleanup, followed by one
atomic commit/push without PR or merge. This record admits no production dependency.

## Authority and claim ceiling

The [technical plan](../DORA_MVP1_TECHNICAL_PLAN.md), [PoC gates](DORA_MVP1_POC_GATES.md),
[readiness](../DORA_MVP1_IMPLEMENTATION_READINESS.md), [5.2 pin](DORA_ALPHA_ASR_MODEL_ADMISSION_STAGE0_V0_1.md)
and [5.3A contract](DORA_ALPHA_ASR_EVAL_CONTRACT_STAGE0_V0_1.md) remain unchanged.
The owner task is the execution plan: cleanup, exact source/toolchain checks, two isolated builds,
complete native inventory, ELF/package verification, sanitized evidence and publication.

PASS covers only static layout of this Android arm64/API28 candidate. It does not prove loading or
inference on a 16-KiB Android runtime, device support, model quality, production or Play readiness.
Full POC-ASR remains BLOCKED / NOT_READY / NOT_RUN. The [JSON evidence](../evidence/poc-asr-001/alpha-asr-native-build-16k-stage0-v0.1.json)
contains both full command vectors, executable/source pins, all dependencies and every PT_LOAD.

## Source and toolchain

| Identity | Value |
|---|---|
| Official source | ggml-org/whisper.cpp |
| Tag | v1.9.4 |
| Commit | 927cfce34f31707e17f2bff35c349632fb9e2c3a |
| Source state | Detached, clean before/after both builds, no patches |
| NDK | ndk;28.2.13676358, stable r28c |
| CMake package / executable | cmake;3.22.1 / 3.22.1-g37088a8-dirty |
| clang / clang++ / LLVM | 19.0.1, Android build 13624864 / r530567e |
| Ninja | 1.10.2 |
| sdkmanager / build-tools | 22.0 / 36.0.0 |
| Target | API28, arm64-v8a, armv8-a, Release, c++_shared |

The CMake suffix is the official SDK executable version string, not the whisper source state.
Toolchain packages use the existing SDK root; no system package manager or unrelated package was
installed. CMake was preserved. Personal paths/raw logs stay local. Symbolic command roots replace
only directory names: WORKSPACE is outside Dora/worktrees/sync folders; SDK_ROOT is the existing
SDK; DORA_ROOT is this checkout; PYTHON is CPython 3.12.14. Source acquisition is recorded in JSON.

## Disk cleanup

| Removed item | Logical bytes | Reason |
|---|---:|---|
| ${SDK_ROOT}/system-images/android-36/google_apis/x86_64 | 4585408955 | Historical SDK package; reinstallable, no emulator running. |
| ${SDK_ROOT}/emulator | 1082790554 | Historical SDK package; reinstallable, no emulator running. |
| ${USERPROFILE}/.gradle/caches/9.5.0 | 997315089 | Reproducible cache; no Java/Gradle process, configuration preserved. |
| ${USERPROFILE}/.gradle/caches/9.5.1 | 951001076 | Reproducible cache; no Java/Gradle process, configuration preserved. |
| ${USERPROFILE}/.gradle/caches/build-cache-1 | 370104990 | Reproducible cache; no Java/Gradle process, configuration preserved. |
| ${USERPROFILE}/.gradle/caches/modules-2 | 786541533 | Reproducible cache; no Java/Gradle process, configuration preserved. |

Removed logical bytes: **8773162197**. Free bytes: inventory **2519855104**,
immediately before cleanup **2509111296**, after cleanup **11333861376**,
after NDK installation **9053655040**. Preferred preinstall headroom of 10 GiB was met.
Free-space deltas include background activity.

The historical API36 x86_64 AVD configuration/userdata is retained. Removed packages were
system-images;android-36;google_apis;x86_64 revision 7 and emulator 37.1.11. Future authorized
emulator work requires reinstalling them. No Recovery evidence was removed or tests rerun.
Both Gradle distributions, configuration/keyrings, CMake, platform 36, platform-tools, build-tools,
cmdline-tools, source/worktrees, all earlier evidence/checksums, attachments, signing material
and user documents were preserved. Temp and Recycle Bin were not deleted. Windows Temp was
inaccessible without escalation and was left untouched. Earlier failed-install diagnostics remain local.

## Reproduce the build

Acquire the official tag, verify HEAD and tag both equal the full pin, require a clean source.
Create WORKSPACE/cmake-fixture/CMakeLists.txt outside the checkout:

~~~cmake
cmake_minimum_required(VERSION 3.22)
project(dora_stage0_native_candidate LANGUAGES C CXX)
add_subdirectory("${WHISPER_SOURCE}" whisper EXCLUDE_FROM_ALL)
~~~

Supported subdirectory mode avoids upstream standalone package.json generation in the source.
Only target whisper and its dependencies are built; unrelated parakeet is excluded. The explicit
CPU-only options are defined by the pinned source. No page-size override was used; actual ELF
checks establish alignment. Both output directories started absent. Full sanitized commands:

~~~text
SDK_ROOT/cmake/3.22.1/bin/cmake.exe -S WORKSPACE/cmake-fixture -B WORKSPACE/build-1 -G Ninja -DCMAKE_MAKE_PROGRAM=SDK_ROOT/cmake/3.22.1/bin/ninja.exe -DCMAKE_TOOLCHAIN_FILE=SDK_ROOT/ndk/28.2.13676358/build/cmake/android.toolchain.cmake -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-28 -DANDROID_STL=c++_shared -DCMAKE_BUILD_TYPE=Release -DWHISPER_SOURCE=WORKSPACE/source -DBUILD_SHARED_LIBS=ON -DWHISPER_BUILD_IS_DEV=OFF -DWHISPER_BUILD_TESTS=OFF -DWHISPER_BUILD_EXAMPLES=OFF -DWHISPER_BUILD_SERVER=OFF -DWHISPER_CURL=OFF -DWHISPER_SDL2=OFF -DWHISPER_COREML=OFF -DWHISPER_OPENVINO=OFF -DWHISPER_VITISAI=OFF -DGGML_NATIVE=OFF -DGGML_CPU=ON -DGGML_CPU_ARM_ARCH=armv8-a -DGGML_CPU_ALL_VARIANTS=OFF -DGGML_BACKEND_DL=OFF -DGGML_OPENMP=OFF -DGGML_CCACHE=OFF -DGGML_CUDA=OFF -DGGML_METAL=OFF -DGGML_ACCELERATE=OFF -DGGML_BLAS=OFF -DGGML_VULKAN=OFF -DGGML_OPENCL=OFF -DGGML_RPC=OFF -DGGML_BUILD_TESTS=OFF -DGGML_BUILD_EXAMPLES=OFF
SDK_ROOT/cmake/3.22.1/bin/cmake.exe --build WORKSPACE/build-1 --target whisper --parallel 2
~~~

Build 2 changes only build-1 to build-2; both full vectors are in JSON. Both configure and build
exit 0. Actual commands use --target=aarch64-none-linux-android28, -O3 -DNDEBUG and NDK-injected
-g; the CPU backend uses -march=armv8-a. OpenMP, compiler cache, host-native tuning, optional
accelerators, examples, tests and server are disabled. The source remains clean.

## Native dependency closure

Four libraries are built from pinned source. libc++_shared.so is the pinned NDK runtime from
SDK_ROOT/ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib/aarch64-linux-android/.
It is packaged, not assumed to be an Android platform library. NDK notices are retained locally;
this is not production redistribution/admission. Every library is ELF64 little-endian AArch64,
with SONAME equal to filename and no RPATH/RUNPATH.

| Filename | Bytes | Build 1 SHA-256 | DT_NEEDED |
|---|---:|---|---|
| libc++_shared.so | 9236352 | ab4e6c71b96b851de45a8a9bd86369e7dbc2130a44b3b4520564be94847910f2 | libc.so, libm.so, libdl.so |
| libggml-base.so | 5905736 | df51d9b82f478cce93a4e3b68b4fc046ad8ebec4d98135b8ff322c910c89c1ac | libm.so, libdl.so, libc++_shared.so, libc.so |
| libggml-cpu.so | 4467344 | 01842a3bc8436a2deda623238cb038171dc9576fc6b8f19c3c05f2b07b33600b | libggml-base.so, libm.so, libc++_shared.so, libdl.so, libc.so |
| libggml.so | 533824 | d578fa0e96f4409cf38e6567849f66594c9d28dd7d47fac0276c5aaf48392965 | libggml-cpu.so, libggml-base.so, libm.so, libc++_shared.so, libdl.so, libc.so |
| libwhisper.so | 7175280 | 79ffb4c9e33f32f31a39fbd36ba46809b3d74c043c8094406425e152cf6eeadc | libggml.so, libggml-cpu.so, libggml-base.so, libm.so, libc++_shared.so, libdl.so, libc.so |

Traversal from libwhisper.so reaches all five packaged libraries, with zero unexplained or extra
native dependencies. Only libc.so, libm.so and libdl.so remain external platform libraries;
API28 AArch64 NDK sysroot stubs were checked. The fixture contains no model.

## Actual ELF 16-KiB evidence

NDK llvm-readelf --file-header --program-headers --dynamic --wide inspected every library in
both builds, including libc++. The unchanged DORA ELF parser agreed. All rows have p_align=0x4000
and (p_vaddr-p_offset) modulo 16384 = 0. Build 2 has identical program headers.

| ELF | PT_LOAD offset | Virtual address | p_align | Result |
|---|---|---|---|---|
| libc++_shared.so | 0x000000 | 0x0000000000000000 | 0x4000 | PASS |
| libc++_shared.so | 0x0988a0 | 0x000000000009c8a0 | 0x4000 | PASS |
| libc++_shared.so | 0x127898 | 0x000000000012f898 | 0x4000 | PASS |
| libc++_shared.so | 0x131430 | 0x000000000013d430 | 0x4000 | PASS |
| libggml-base.so | 0x000000 | 0x0000000000000000 | 0x4000 | PASS |
| libggml-base.so | 0x0c52d0 | 0x00000000000c92d0 | 0x4000 | PASS |
| libggml-base.so | 0x0c7990 | 0x00000000000cf990 | 0x4000 | PASS |
| libggml-cpu.so | 0x000000 | 0x0000000000000000 | 0x4000 | PASS |
| libggml-cpu.so | 0x0d2b80 | 0x00000000000d6b80 | 0x4000 | PASS |
| libggml-cpu.so | 0x0d48e0 | 0x00000000000dc8e0 | 0x4000 | PASS |
| libggml.so | 0x000000 | 0x0000000000000000 | 0x4000 | PASS |
| libggml.so | 0x00d8f0 | 0x00000000000118f0 | 0x4000 | PASS |
| libggml.so | 0x00dea8 | 0x0000000000015ea8 | 0x4000 | PASS |
| libwhisper.so | 0x000000 | 0x0000000000000000 | 0x4000 | PASS |
| libwhisper.so | 0x079020 | 0x000000000007d020 | 0x4000 | PASS |
| libwhisper.so | 0x07ae18 | 0x0000000000082e18 | 0x4000 | PASS |

## Package fixture

This deterministic lib-only ZIP has no Android manifest, dex or signature: **it is not an
installable app**. All originals are stored uncompressed under lib/arm64-v8a/. Use this host recipe
with build bin directory, NDK directory and unaligned output filename as three arguments:

~~~python
from pathlib import Path
import sys, zipfile
bin_dir, ndk, output = map(Path, sys.argv[1:])
paths = list(bin_dir.glob("*.so"))
paths += [ndk / "toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib/aarch64-linux-android/libc++_shared.so"]
assert {p.name for p in paths} == {"libwhisper.so", "libggml.so", "libggml-base.so", "libggml-cpu.so", "libc++_shared.so"}
with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_STORED) as package:
    for path in sorted(paths, key=lambda p: p.name):
        info = zipfile.ZipInfo("lib/arm64-v8a/" + path.name, (1980, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_STORED
        info.create_system = 3
        info.external_attr = 0o100644 << 16
        package.writestr(info, path.read_bytes())
~~~

Create a temporary allowlist with one five-field line for each basename:
basename|isolated-stage0:asr-candidate|v0.1|license-label|Task-only inventory not production admission.
The license label is MIT for source-built libraries and NDK-distributed-notices for libc++.
This inventories only the fixture; android/native-libs-allowlist.txt is unchanged.

~~~text
SDK_ROOT/build-tools/36.0.0/zipalign.exe -P 16 4 WORKSPACE/native-fixture-1.unaligned.apk WORKSPACE/native-fixture-1.aligned.apk
SDK_ROOT/build-tools/36.0.0/zipalign.exe -c -P 16 -v 4 WORKSPACE/native-fixture-1.aligned.apk
PYTHON -B DORA_ROOT/tools/verify_apk_native_alignment.py WORKSPACE/native-fixture-1.aligned.apk --allowlist WORKSPACE/native-allowlist-1.txt
~~~

Both packages passed zipalign and Dora: **5 ABI entries / 5 basenames**. A separate local-header
calculation checked ZIP_STORED, CRC, content hashes and data offsets modulo 16384. Offsets in both
packages: 16384, 9256960, 15171584, 19644416, 20185088.

| Package | Bytes | SHA-256 | zipalign / DORA |
|---|---:|---|---|
| native-fixture-1.aligned.apk | 27360758 | e4c1e593b4f5dacbb5104962210170919d66f306fdbbfe75be353654c8ac5ebb | PASS / PASS |
| native-fixture-2.aligned.apk | 27360758 | 6be05721d8a41e89d9a99f52628be3e8c516db66bdb2840b92f4e192f4c0bd33 | PASS / PASS |

## Reproducibility

**NON_BIT_IDENTICAL_EXPLAINED**. Both builds use the same source/toolchain/configuration without
compiler cache. Names, sizes, architecture, SONAME, DT_NEEDED, PT_LOAD headers and verification
outcomes match. The NDK runtime is byte-identical.

| Source-built library | Build 2 SHA-256 |
|---|---|
| libggml-base.so | 966d5f150749b720c75a32e2d1b0cf3473ca06e6634f044cb870baab9ddfff47 |
| libggml-cpu.so | 98e34bf1fcedab26fa11d22d4f97c55c187cde1ea7a8882d130f739eff42a8f4 |
| libggml.so | f7e9e1a48f3e8bcac70ad01d48d83706c5211a92fce76823d61124da4c01763b |
| libwhisper.so | 8f4d569085a26bc9bbf89eca19bc2aa43228f4574693cfb1fe0f6643677ec01a |

For all four source-built files, only .debug_info, .debug_str and .note.gnu.build-id differ;
every other original byte is identical. Debug string multisets match after normalizing the two
build-directory paths. NDK Release retains -g; path/string-table layout changes affect DWARF and
derived build IDs. Separate comparison copies produced by
llvm-objcopy --strip-debug --remove-section=.note.gnu.build-id INPUT OUTPUT are byte-identical;
comparison hashes are in JSON. Packaged originals were not stripped or replaced. This establishes
no observed runtime-code drift, not raw-byte or cross-host reproducibility.

## Validation and remaining gates

Both actual native builds, closures, 16 PT_LOAD segments/build, uncompressed packages, zipalign
and unchanged DORA verifier passed. All 22 existing 5.3A synthetic host tests passed, including
unchanged Java oracle handoff. No real-corpus WER was measured. JSON/Markdown structure,
changed-file privacy/scope and staged diff checks accompany publication. CI is NOT_RUN because
this chat branch has no applicable push workflow and no PR is requested.

JSON pins working-copy and Git-LF hashes for the unchanged production allowlist, verifier, text
contract and Java oracle. Historical status/backlog content is preserved with additive 5.3B
summaries only. Binaries, SDK, upstream source copies, models and private logs stay outside Git.
Independent advisory review is not formal production admission.

5.3A remains PASS; 5.3B is the bounded PASS above; 5.3C stays NOT_STARTED; 5.4 NOT_AUTHORIZED.
NATIVE_RUNTIME_BINARY = BUILT_ISOLATED_NOT_PRODUCT_ADMITTED. MODEL_LOAD, ASR_INFERENCE,
real WER, RTF/PSS/native-heap/thermal, DEVICE_RUNTIME, POCO and Recovery are NOT_RUN;
MODEL_QUALITY = NOT_EVALUATED. Overall 5.3 is not complete. PR #86 is untouched.
No PR is created or merged. No bounded 5.3B P0/P1 blocker remains after the static gates;
further runner/device/quality/production work requires separately authorized scope.
