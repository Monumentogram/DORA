"""Build only the optional baseline JNI adapter; reuse byte-pinned ARM82 runtime.

No download, model copy, device operation, audio input, or native runtime rebuild.
Output must be a new directory outside every Git checkout. Local paths never enter
the sanitized receipt. Invoke explicitly with --source, --native, --sdk, --output.
"""
import argparse
import hashlib
import json
import os
import pathlib
import re
import shutil
import struct
import subprocess

COMMIT = "927cfce34f31707e17f2bff35c349632fb9e2c3a"
NDK = "28.2.13676358"
PINS = {
    "libc++_shared.so": (9236352, "ab4e6c71b96b851de45a8a9bd86369e7dbc2130a44b3b4520564be94847910f2"),
    "libggml-base.so": (5904504, "ef2fab9ca78cb998feed953755c394d46cd5182b355909032415e79ad522e049"),
    "libggml-cpu.so": (4699840, "ab61e958ed28b6ec9ef1c92bd141f99149bb02e6147c1d710824565bdb2c7a14"),
    "libggml.so": (533264, "032f86f3f42ba9dedeac0f7f0b69c818917c6ddab6da98b677f2cef6dbea4f97"),
    "libwhisper.so": (7176544, "b67a219f3610f9085a75fe4dd7c2c11a18edea60068e63c2f4b63c330d5f96e3"),
}


def pin(path):
    with path.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    return {"bytes": path.stat().st_size, "sha256": digest}


def verify_file(path, size, sha256):
    if path.is_symlink() or not path.is_file() or pin(path) != {"bytes": size, "sha256": sha256}:
        raise ValueError("ARTIFACT_IDENTITY")


def verify_elf(raw, machine=183):
    if len(raw) < 64 or raw[:6] != b"\x7fELF\x02\x01" or struct.unpack_from("<H", raw, 18)[0] != machine:
        raise ValueError("ELF_IDENTITY")
    offset = struct.unpack_from("<Q", raw, 32)[0]
    size, count = struct.unpack_from("<HH", raw, 54)
    if size != 56 or offset + size * count > len(raw):
        raise ValueError("ELF_HEADERS")
    alignments = []
    for index in range(count):
        kind, _, file_offset, virtual, _, _, _, alignment = struct.unpack_from("<IIQQQQQQ", raw, offset + index * size)
        if kind == 1:
            if alignment < 16384 or (virtual - file_offset) % 16384:
                raise ValueError("ELF_ALIGNMENT")
            alignments.append(alignment)
    if not alignments:
        raise ValueError("ELF_SEGMENTS_MISSING")
    return alignments


def output_is_external(path):
    return not any((ancestor / ".git").exists() for ancestor in (path, *path.parents))


def build(args):
    source, native, sdk, output = (pathlib.Path(getattr(args, name)).resolve() for name in ("source", "native", "sdk", "output"))
    if os.environ.get("GITHUB_ACTIONS") == "true":
        raise ValueError("CONTROLLED_LOCAL_ONLY")
    if output.exists() or not output_is_external(output):
        raise ValueError("OUTPUT_MUST_BE_NEW_OUTSIDE_GIT")
    if subprocess.check_output(["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip() != COMMIT:
        raise ValueError("SOURCE_COMMIT")
    if subprocess.check_output(["git", "-C", str(source), "status", "--porcelain", "--untracked-files=all"], text=True).strip():
        raise ValueError("SOURCE_NOT_CLEAN")
    for name, identity in PINS.items():
        verify_file(native / name, *identity)
        verify_elf((native / name).read_bytes())
    ndk = sdk / "ndk" / NDK
    if f"Pkg.Revision = {NDK}" not in (ndk / "source.properties").read_text():
        raise ValueError("NDK_IDENTITY")
    repository = pathlib.Path(__file__).resolve().parents[1]
    adapter = repository / "android/app/src/main/cpp"
    cmake = sdk / "cmake/3.22.1/bin/cmake.exe"
    output.mkdir(parents=True)
    configure = [str(cmake), "-S", str(adapter), "-B", str(output / "build"), "-G", "Ninja",
                 f"-DCMAKE_MAKE_PROGRAM={sdk / 'cmake/3.22.1/bin/ninja.exe'}",
                 f"-DCMAKE_TOOLCHAIN_FILE={ndk / 'build/cmake/android.toolchain.cmake'}",
                 "-DANDROID_ABI=arm64-v8a", "-DANDROID_PLATFORM=android-28", "-DANDROID_STL=c++_shared",
                 "-DCMAKE_BUILD_TYPE=Release", f"-DWHISPER_SOURCE={source}"]
    for label, command in (("configure", configure), ("build", [str(cmake), "--build", str(output / "build"), "--parallel", "2"])):
        result = subprocess.run(command, capture_output=True, text=True)
        (output / f"{label}.log").write_text(result.stdout + result.stderr, encoding="utf8")
        if result.returncode:
            raise ValueError(f"ADAPTER_{label.upper()}_FAILED_SEE_LOCAL_LOG")
    packaged = output / "jniLibs/arm64-v8a"
    packaged.mkdir(parents=True)
    for name, identity in PINS.items():
        shutil.copyfile(native / name, packaged / name)
        verify_file(packaged / name, *identity)
    built = output / "build/libdora-whisper-jni.so"
    shutil.copyfile(built, packaged / built.name)
    readelf = ndk / "toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe"
    inspection = subprocess.check_output([str(readelf), "--dynamic", "--dyn-syms", "--wide", str(built)], text=True)
    needed = re.findall(r"\(NEEDED\).*?\[(.*?)\]", inspection)
    if set(needed) - {"libc++_shared.so", "libdl.so", "libm.so", "libc.so"}:
        raise ValueError("ADAPTER_EAGER_RUNTIME_DEPENDENCY")
    exports = ["Java_com_monumentogram_dora_transcript_WhisperNative_nativeAvailable",
               "Java_com_monumentogram_dora_transcript_WhisperNative_nativeRecognize"]
    if not all(re.search(r"\b" + symbol + r"\b", inspection) for symbol in exports):
        raise ValueError("JNI_EXPORT_MISSING")
    notices = output / "notices"
    notices.mkdir()
    shutil.copyfile(source / "LICENSE", notices / "whisper-MIT.txt")
    shutil.copyfile(ndk / "NOTICE", notices / "NDK-NOTICE.txt")
    shutil.copyfile(ndk / "NOTICE.toolchain", notices / "NDK-NOTICE-toolchain.txt")
    receipt = {"result": "PASS_BUILD_AND_STATIC_LAYOUT_ONLY", "runtimeCommit": COMMIT,
               "ndk": NDK, "abi": "arm64-v8a", "minimumApi": 28, "modelPackaged": False,
               "runtimeRebuilt": False, "deviceRuntimeTested": False,
               "adapterNeeded": needed, "adapterExports": exports,
               "sourcePins": {path.name: pin(path) for path in sorted(adapter.iterdir()) if path.is_file()},
               "artifacts": {path.name: {**pin(path), "loadAlignments": verify_elf(path.read_bytes())}
                             for path in sorted(packaged.iterdir())}}
    (output / "build-receipt.json").write_text(json.dumps(receipt, sort_keys=True, indent=2) + "\n", encoding="utf8")
    print(json.dumps(receipt, sort_keys=True))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("source", "native", "sdk", "output"):
        parser.add_argument(f"--{name}", required=True)
    build(parser.parse_args())


if __name__ == "__main__":
    main()
