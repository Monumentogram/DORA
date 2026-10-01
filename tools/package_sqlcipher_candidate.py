#!/usr/bin/env python3
"""Deterministically package the Stage 8.2 composite SQLCipher candidate.

Packaging is not admission. Runtime, provenance and release gates remain required.
Input directory contains the original published AAR and the pinned, patched NDK
build described in android/vendor/sqlcipher/README.md.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZIP_DEFLATED, ZipFile, ZipInfo

from verify_apk_native_alignment import PAGE_SIZE, load_segments

ORIGINAL_SHA256 = "44fc40c33d1de597c8339072a71fa0ff20e12d01ab352d6abe4ad5df668ead94"
ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
VERSION = "4.17.0-dora.1"
GROUP = "com.monumentogram.dora.thirdparty"


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def package(inputs: Path, destination: Path, ndk: Path) -> dict:
    original = inputs / "sqlcipher-android-4.17.0.aar"
    if digest(original.read_bytes()) != ORIGINAL_SHA256:
        raise ValueError("Unrecognized published SQLCipher artifact")
    entries = {}
    with ZipFile(original) as archive:
        for name in archive.namelist():
            if not name.startswith("jni/") and not name.endswith("/"):
                entries[name] = archive.read(name)
    native = {}
    for abi in ABIS:
        payload = (inputs / f"sqlcipher-source/sqlcipher/libs/{abi}/libsqlcipher.so").read_bytes()
        segments = load_segments(payload, abi)
        if any(align < PAGE_SIZE or (addr - offset) % PAGE_SIZE for offset, addr, align in segments):
            raise ValueError(f"Invalid 16 KiB ELF layout: {abi}")
        if b"__android_log_" in payload:
            raise ValueError(f"Native Android logging import remains: {abi}")
        entries[f"jni/{abi}/libsqlcipher.so"] = payload
        native[abi] = {"sha256": digest(payload), "bytes": len(payload), "loadAlignment": [s[2] for s in segments]}
    license_paths = {
        "SQLCipher-Android.txt": "sqlcipher-source/LICENSE",
        "SQLCipher.txt": "sqlcipher-core-4.17.0/LICENSE.md",
        "SQLite.txt": "sqlcipher-core-4.17.0/SQLITE_LICENSE.md",
        "LibTomCrypt.txt": "sqlcipher-source/sqlcipher/src/main/jni/libtomcrypt/src/LICENSE",
    }
    for name, relative in license_paths.items():
        entries[f"META-INF/LICENSES/{name}"] = (inputs / relative).read_bytes()
    entries["META-INF/LICENSES/Android-NDK-NOTICE.txt"] = (ndk / "NOTICE").read_bytes()
    llvm_notices = list((ndk / "toolchains/llvm/prebuilt").glob("*/NOTICE"))
    if len(llvm_notices) != 1:
        raise ValueError("Exactly one NDK host LLVM notice is required")
    entries["META-INF/LICENSES/LLVM-NOTICE.txt"] = llvm_notices[0].read_bytes()
    directory = destination / GROUP.replace(".", "/") / "sqlcipher-android" / VERSION
    directory.mkdir(parents=True, exist_ok=True)
    artifact = directory / f"sqlcipher-android-{VERSION}.aar"
    with ZipFile(artifact, "w", ZIP_DEFLATED, compresslevel=9) as archive:
        for name, data in sorted(entries.items()):
            entry = ZipInfo(name, date_time=(1980, 1, 1, 0, 0, 0))
            entry.compress_type = ZIP_DEFLATED
            entry.create_system = 3
            entry.external_attr = 0o100644 << 16
            archive.writestr(entry, data, compresslevel=9)
    pom = directory / f"sqlcipher-android-{VERSION}.pom"
    pom.write_text(f'''<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>{GROUP}</groupId>
  <artifactId>sqlcipher-android</artifactId>
  <version>{VERSION}</version>
  <packaging>aar</packaging>
  <name>DORA SQLCipher Android composite</name>
  <description>Explicit core 4.17.0 with native logging and profiling disabled.</description>
  <licenses><license><name>BSD-3-Clause AND Apache-2.0 AND WTFPL AND Apache-2.0 WITH LLVM-exception</name></license></licenses>
  <dependencies><dependency>
    <groupId>androidx.sqlite</groupId><artifactId>sqlite</artifactId>
    <version>2.6.2</version><scope>runtime</scope>
  </dependency></dependencies>
</project>
''', encoding="utf-8", newline="\n")
    return {
        "status": "CANDIDATE_PENDING_RUNTIME_ADMISSION",
        "coordinate": f"{GROUP}:sqlcipher-android:{VERSION}",
        "originalAarSha256": ORIGINAL_SHA256,
        "classesJarSha256": digest(entries["classes.jar"]),
        "aarSha256": digest(artifact.read_bytes()),
        "pomSha256": digest(pom.read_bytes()),
        "androidSource": "ae57a61052d8c41ce35cd48319b2f6f20f4de6bf",
        "androidTagOriginalCore": "e2a6040f2ae5cfff2b3e08eb3320007d93cdf3fc",
        "explicitCoreSource": "810db22f575ee7cf94ea96a3e91622b5fcece3dc",
        "libtomcrypt": "476a9579ae94f32b9ea9e2747bfb04b302370259",
        "ndk": "28.2.13676358",
        "androidApiFloor": 28,
        "amalgamationSha256": digest((inputs / "sqlcipher-source/sqlcipher/src/main/jni/sqlcipher/sqlite3.c").read_bytes()),
        "headerSha256": digest((inputs / "sqlcipher-source/sqlcipher/src/main/jni/sqlcipher/sqlite3.h").read_bytes()),
        "native": native,
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inputs", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--receipt", type=Path, required=True)
    parser.add_argument("--ndk", type=Path, required=True)
    args = parser.parse_args()
    receipt = package(args.inputs, args.destination, args.ndk)
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(receipt, indent=2))


if __name__ == "__main__":
    main()
