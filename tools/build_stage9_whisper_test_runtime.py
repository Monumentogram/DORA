"""Explicit unadmitted x86_64 runtime for disposable-emulator functional testing.

This is NOT an admitted product runtime. It has a different ABI, new binaries,
and a test-only JNI capability guard. No model, device, audio, network or inference
operation occurs here. The ARM82 product admission helper rejects these binaries.
"""
import argparse
import json
import os
import pathlib
import re
import shutil
import subprocess

from build_stage9_whisper_adapter import COMMIT, NDK, output_is_external, pin, verify_elf


def build(args):
    if not args.explicit_unadmitted_emulator_test or os.environ.get("GITHUB_ACTIONS") == "true":
        raise ValueError("EXPLICIT_LOCAL_TEST_ONLY")
    source, sdk, output = (pathlib.Path(getattr(args, name)).resolve() for name in ("source", "sdk", "output"))
    if output.exists() or not output_is_external(output):
        raise ValueError("OUTPUT_MUST_BE_NEW_OUTSIDE_GIT")
    if subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip() != COMMIT:
        raise ValueError("SOURCE_COMMIT")
    if subprocess.check_output(["git", "-C", str(source), "status", "--porcelain", "--untracked-files=all"], text=True).strip():
        raise ValueError("SOURCE_NOT_CLEAN")
    ndk = sdk / "ndk" / NDK
    if f"Pkg.Revision = {NDK}" not in (ndk / "source.properties").read_text():
        raise ValueError("NDK_IDENTITY")
    adapter = pathlib.Path(__file__).resolve().parents[1] / "android/app/src/main/cpp"
    output.mkdir(parents=True)
    fixture = output / "fixture"
    fixture.mkdir()
    (fixture / "CMakeLists.txt").write_text(
        'cmake_minimum_required(VERSION 3.22)\nproject(dora_unadmitted_x86_test LANGUAGES C CXX)\n'
        'add_subdirectory("${WHISPER_SOURCE}" whisper EXCLUDE_FROM_ALL)\n', encoding="utf8")
    cmake = sdk / "cmake/3.22.1/bin/cmake.exe"
    common = ["-G", "Ninja", f"-DCMAKE_MAKE_PROGRAM={sdk / 'cmake/3.22.1/bin/ninja.exe'}",
              f"-DCMAKE_TOOLCHAIN_FILE={ndk / 'build/cmake/android.toolchain.cmake'}",
              "-DANDROID_ABI=x86_64", "-DANDROID_PLATFORM=android-28", "-DANDROID_STL=c++_shared",
              "-DCMAKE_BUILD_TYPE=Release", f"-DWHISPER_SOURCE={source}"]
    disabled = ["WHISPER_BUILD_IS_DEV", "WHISPER_BUILD_TESTS", "WHISPER_BUILD_EXAMPLES", "WHISPER_BUILD_SERVER",
                "WHISPER_CURL", "WHISPER_SDL2", "WHISPER_COREML", "WHISPER_OPENVINO", "WHISPER_VITISAI",
                "GGML_NATIVE", "GGML_CPU_ALL_VARIANTS", "GGML_BACKEND_DL", "GGML_OPENMP", "GGML_CCACHE",
                "GGML_CUDA", "GGML_METAL", "GGML_ACCELERATE", "GGML_BLAS", "GGML_VULKAN", "GGML_OPENCL",
                "GGML_RPC", "GGML_BUILD_TESTS", "GGML_BUILD_EXAMPLES", "GGML_SSE42", "GGML_AVX", "GGML_AVX2",
                "GGML_AVX512", "GGML_AVX_VNNI", "GGML_FMA", "GGML_F16C", "GGML_CPU_KLEIDIAI"]
    options = ["-DBUILD_SHARED_LIBS=ON", "-DGGML_CPU=ON", *[f"-D{name}=OFF" for name in disabled]]
    commands = [
        ("runtime-configure", [str(cmake), "-S", str(fixture), "-B", str(output / "runtime-build"), *common, *options]),
        ("runtime-build", [str(cmake), "--build", str(output / "runtime-build"), "--target", "whisper", "--parallel", "2"]),
        ("adapter-configure", [str(cmake), "-S", str(adapter), "-B", str(output / "adapter-build"), *common, "-DDORA_TEST_X86_RUNTIME=ON"]),
        ("adapter-build", [str(cmake), "--build", str(output / "adapter-build"), "--parallel", "2"]),
    ]
    for label, command in commands:
        result = subprocess.run(command, capture_output=True, text=True)
        (output / f"{label}.log").write_text(result.stdout + result.stderr, encoding="utf8")
        if result.returncode:
            raise ValueError(f"{label.upper()}_FAILED_SEE_LOCAL_LOG")
    packaged = output / "jniLibs/x86_64"
    packaged.mkdir(parents=True)
    candidates = list((output / "runtime-build").rglob("*.so"))
    for name in ("libwhisper.so", "libggml.so", "libggml-base.so", "libggml-cpu.so"):
        matches = [path for path in candidates if path.name == name]
        if len(matches) != 1:
            raise ValueError("RUNTIME_OUTPUT_AMBIGUOUS")
        shutil.copyfile(matches[0], packaged / name)
    shutil.copyfile(output / "adapter-build/libdora-whisper-jni.so", packaged / "libdora-whisper-jni.so")
    shutil.copyfile(ndk / "toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/lib/x86_64-linux-android/libc++_shared.so",
                    packaged / "libc++_shared.so")
    readelf = ndk / "toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe"
    artifacts = {}
    for path in sorted(packaged.iterdir()):
        inspection = subprocess.check_output([str(readelf), "--dynamic", "--dyn-syms", "--wide", str(path)], text=True)
        needed = re.findall(r"\(NEEDED\).*?\[(.*?)\]", inspection)
        if set(needed) - {entry.name for entry in packaged.iterdir()} - {"libc.so", "libdl.so", "libm.so"}:
            raise ValueError("DEPENDENCY_CLOSURE")
        if path.name == "libdora-whisper-jni.so" and any("whisper" in name or "ggml" in name for name in needed):
            raise ValueError("ADAPTER_EAGER_DEPENDENCY")
        artifacts[path.name] = {**pin(path), "loadAlignments": verify_elf(path.read_bytes(), machine=62), "needed": needed}
    notices = output / "notices"
    notices.mkdir()
    shutil.copyfile(source / "LICENSE", notices / "whisper-MIT.txt")
    shutil.copyfile(ndk / "NOTICE", notices / "NDK-NOTICE.txt")
    shutil.copyfile(ndk / "NOTICE.toolchain", notices / "NDK-NOTICE-toolchain.txt")
    receipt = {"result": "PASS_TEST_BUILD_STATIC_ONLY", "scope": "UNADMITTED_DISPOSABLE_EMULATOR_FUNCTIONAL_TEST_ONLY",
               "abi": "x86_64", "runtimeCommit": COMMIT, "ndk": NDK, "minimumApi": 28,
               "modelPackaged": False, "deviceRuntimeTested": False, "productionAdmission": False,
               "capabilityGuard": "EXPLICIT_COMPILE_TIME_TEST_VARIANT", "runtimeOptions": options,
               "sourcePins": {path.name: pin(path) for path in sorted(adapter.iterdir()) if path.is_file()},
               "artifacts": artifacts}
    (output / "test-build-receipt.json").write_text(json.dumps(receipt, sort_keys=True, indent=2) + "\n", encoding="utf8")
    print(json.dumps(receipt, sort_keys=True))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("source", "sdk", "output"):
        parser.add_argument(f"--{name}", required=True)
    parser.add_argument("--explicit-unadmitted-emulator-test", action="store_true")
    build(parser.parse_args())


if __name__ == "__main__":
    main()
