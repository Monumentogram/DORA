#!/usr/bin/env python3
"""Fail-closed Stage 0 admission verifier for Kotlinx Coroutines 1.11.0."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from typing import Any, Callable


ROOT = Path(__file__).resolve().parents[1]
BASE_COMMIT = "da1d9bd13b71d609fe7ec4ea62fe1e984f726040"
BASE_TREE = "925bd08802fefc314742776d147771a92edfac70"
KSP_INTEGRATION_COMMIT = "1a1ef62b0db52be7ed73e58583d7d93c0a89aaf8"
KSP_INTEGRATION_TREE = "2290874500ee1b4715e18ac872188a110d77475f"
BRANCH = "codex/deps-kotlinx-coroutines-1.11.0-admission"
AUTHORIZATION = "OWNER-AUTH-BATCH-20260819-01"
CLAIM = (
    "KOTLINX_COROUTINES_1_11_0_EXACT_GRAPH_PROVENANCE_LICENSE_SECURITY_AND_"
    "OFFLINE_REPRODUCTION_LOCALLY_VERIFIED_PENDING_INDEPENDENT_ADMISSION_REVIEW_"
    "AND_EXACT_HEAD_CI"
)
SCOPE_PATH = (
    "docs/stage0/"
    "DORA_MVP1_KOTLINX_COROUTINES_1_11_0_ADMISSION_SCOPE_STAGE0_V0_1.md"
)
EVIDENCE_PATH = (
    "docs/evidence/poc-capture-001/"
    "kotlinx-coroutines-1.11.0-admission-stage0-v0.1.json"
)
CATALOG_PATH = "android/gradle/libs.versions.toml"
CAPTURE_LOCK_PATH = "android/poc/capture/gradle.lockfile"
METADATA_PATH = "android/gradle/verification-metadata.xml"
SEARCH_LOCK_PATH = "android/poc/search/gradle.lockfile"
KSP_EVIDENCE_PATH = (
    "docs/evidence/poc-search-001/build-tool-lock-overlay-ksp-2.3.11.json"
)
EXPECTED_PATHS = (
    ".github/workflows/android-ci.yml",
    CATALOG_PATH,
    METADATA_PATH,
    CAPTURE_LOCK_PATH,
    EVIDENCE_PATH,
    SCOPE_PATH,
    "tools/poc_search_dependency_inventory.py",
    "tools/validate_poc_recovery_governance.py",
    "tools/validate_poc_search.py",
    "tools/verify_kotlinx_coroutines_admission.py",
)
BASE_HASHES = {
    CATALOG_PATH: "230e8b8f5042b5e4852aa3ad05009e5b1d1336eb467d9d89f3d37a7f5104fc4c",
    CAPTURE_LOCK_PATH: "e02b8f90e83cd744bcd7703ad3b7f4b1538991d43e5ec0f5f2ec34b0a5285f3c",
    METADATA_PATH: "2d31104754fc8df67ff14d9f8fb613782170862d421202ad3132297793357f23",
    SEARCH_LOCK_PATH: "3e47b2a46c493245ad24399b8bb26c834bc79b52397a4e920d5895bec695ba8f",
    KSP_EVIDENCE_PATH: "511914d7e001c786ace199535d5e0d6f79bbae73052ae5523c9cdc817eb08b84",
}
CANDIDATE_HASHES = {
    CATALOG_PATH: "dc7d85be3bf534f8a2f7a9ab9a07e1b9d51a2ff7701338261b0df07d944caee7",
    CAPTURE_LOCK_PATH: "b1abf679a07a423de897fe833f4ce41a941e74f6f99b99f731e85b48d717a4c2",
    METADATA_PATH: "4c43f466346a5b72edd0c363b670e953982ccdc956fd75e5545130bb53e48b91",
}
METADATA_LF_BYTES = 303325
METADATA_BLOB = "16071c00ac76fbb35c62d88f9eb748e7c0046cb7"
KSP_FROZEN_BLOBS = {
    CATALOG_PATH: "d19f37077b1e90b5e15fdb3ddd67e1126b01e5bb",
    SEARCH_LOCK_PATH: "a7549c1b47744fefc18ad82dc499a956cf99c02d",
    KSP_EVIDENCE_PATH: "df532ff8720b3255405294b2da2e8efb737923fb",
    "android/gradle/verification-metadata.xml": "887c279e4e3cdccd4dd1c70c758333f27e089a11",
}
AFFECTED_CONFIGURATIONS = (
    "debugAndroidTestCompileClasspath",
    "debugAndroidTestLintChecksClasspath",
    "debugAndroidTestRuntimeClasspath",
    "debugCompileClasspath",
    "debugLintChecksClasspath",
    "debugRuntimeClasspath",
    "debugUnitTestCompileClasspath",
    "debugUnitTestLintChecksClasspath",
    "debugUnitTestRuntimeClasspath",
    "releaseCompileClasspath",
    "releaseLintChecksClasspath",
    "releaseRuntimeClasspath",
)
AFFECTED_GRADLE_TASKS = (
    ":poc:capture:dependencies",
    "spotlessCheck",
    "detekt",
    ":poc:capture:testDebugUnitTest",
    ":poc:capture:compileDebugAndroidTestKotlin",
    ":poc:capture:lintDebug",
    ":poc:capture:assembleDebug",
)


class AdmissionError(RuntimeError):
    """Raised when the exact governed admission contract is not satisfied."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AdmissionError(message)


def canonical_lf(payload: bytes, label: str) -> bytes:
    require(b"\x00" not in payload, f"NUL byte in {label}")
    normalized = payload.replace(b"\r\n", b"\n")
    require(b"\r" not in normalized, f"Bare CR in {label}")
    try:
        normalized.decode("utf-8", errors="strict")
    except UnicodeDecodeError as exc:
        raise AdmissionError(f"Non-UTF-8 {label}: {exc}") from exc
    return normalized


def sha256_bytes(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def file_bytes(relative: str) -> bytes:
    return (ROOT / relative).read_bytes()


def file_sha(relative: str) -> str:
    return sha256_bytes(canonical_lf(file_bytes(relative), relative))


def git(*args: str, check: bool = True) -> subprocess.CompletedProcess[bytes]:
    completed = subprocess.run(
        ["git", "-C", str(ROOT), *args],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if check and completed.returncode != 0:
        detail = completed.stderr.decode("utf-8", errors="replace").strip()
        raise AdmissionError(f"git {' '.join(args)} failed: {detail}")
    return completed


def git_bytes(*args: str) -> bytes:
    return git(*args).stdout


def git_text(*args: str) -> str:
    return git_bytes(*args).decode("utf-8", errors="strict").strip()


def git_blob(revision: str, relative: str) -> bytes:
    return git_bytes("show", f"{revision}:{relative}")


def parse_json_strict(payload: bytes, label: str) -> dict[str, Any]:
    def object_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, value in pairs:
            require(key not in result, f"Duplicate JSON key in {label}: {key}")
            result[key] = value
        return result

    try:
        value = json.loads(
            canonical_lf(payload, label).decode("utf-8"),
            object_pairs_hook=object_pairs,
            parse_constant=lambda item: (_ for _ in ()).throw(
                AdmissionError(f"Non-finite JSON value in {label}: {item}")
            ),
        )
    except json.JSONDecodeError as exc:
        raise AdmissionError(f"Malformed JSON in {label}: {exc}") from exc
    require(isinstance(value, dict), f"{label} root is not an object")
    return value


def canonical_json(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + "\n").encode(
        "utf-8"
    )


ARTIFACT_ROWS = (
    ("org.jetbrains.kotlin:kotlin-stdlib:2.2.20", "jar", 1761444, "8836ccffd3585fadda9901244b20d42901d2f3cd581058d8434e2ffabcf3a3e7", "5380b19fa1924399b62ce3a1faffebb2b4f82272", 689, "d0d01eb832e9f6f7151bbb3e267d758173ace17f93b58d27aed101102f98386c", "2025-09-08T21:09:40Z", "kotlin"),
    ("org.jetbrains.kotlin:kotlin-stdlib:2.2.20", "module", 12430, "c918f5214d021a72e3767f2756e97d103a526e04f1423da3663efdfb5847db95", "fb7b2a0499ac7fb1c611125b7de344b7cc998fa9", 689, "36ec5f300aa97b61549abd01b16279175a868ef168ab65cc404896677abd5c08", "2025-09-08T21:09:39Z", "kotlin"),
    ("org.jetbrains.kotlin:kotlin-stdlib:2.2.20", "pom", 2301, "4a8b086e6431bcf623637f52b2ff192e1adb913838742e5c0eea70a8dee429c4", "62f87c4f4a53f249cb42926a1ef24bffdf190557", 689, "4ce5d476edd9524e503f656382043aa0f3c95a9ae311496cb574dcd93f1abd26", "2025-09-08T21:09:39Z", "kotlin"),
    ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0", "jar", 17819, "c2cb206d27017c7d1bf5ff179787397543d13748dbabb0d7237e1585e0b29044", "51ffb50a6478872fbbac6cbd48f09553bf4e9e7d", 660, "0ce618365d88e4bec629dffeb1db3a794b0926aaede5f621c13c942b1b05a89c", "2026-05-07T13:59:15Z", "coroutines"),
    ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0", "module", 4387, "7b7d1dddf188817deaad738e92e11faa5abdf937c87120cdf036102566ad4be3", "531000e501e830dd477fea22c83eb62324b3404e", 660, "62bb33a6573718e345c1ab41f18db52be995326ddd724972a6e7d9818b20480b", "2026-05-07T13:59:15Z", "coroutines"),
    ("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0", "pom", 2194, "224567420d3e0ed5fef40f789fded45dfd51b162319e28d77d3c9a8dec4821b8", "c1803771dc96ca0454ed9aced9a218ed8cf37cbc", 660, "e7daa5fec1658ae0ce0848fe1a1b7d9ebb5315fd21d53017b8d317db6526d649", "2026-05-07T13:59:15Z", "coroutines"),
    ("org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.11.0", "pom", 4279, "f65860bce58a2bfadf05fdc516d38333421f0e387fef87e24449c0394c80d254", "b4ca3dd84d1f380f6ef1e8c1a81bdf9977a3a2d6", 660, "21c3f43de9477b65694b70547d89a5ed649e4a325cc203c0b99ae860810b5210", "2026-05-07T13:59:15Z", "coroutines"),
    ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0", "module", 47152, "1e19785d34728b7525d7a77ff32ead4547d51cc7a45d4ac44d4de608169b0c0d", "fae4828ee1cbcf8f9aaa54d655263fd253d94406", 660, "4b5c2655e774de1beb135dea34dd3cd92178df6a8e5622423584c3bf6ea5fc8b", "2026-05-07T13:59:42Z", "coroutines"),
    ("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0", "pom", 2030, "41b6e78c1b0d11bc99de795390df9e4ece64dc113f482d6e308f1b7a421c1d30", "d82d89f0543debc20787622e379033c2439a857a", 660, "3120ab8dcfb7d8d3272ec8def5cbba728dfad29afed073c6042d583e5c2b970a", "2026-05-07T13:59:42Z", "coroutines"),
    ("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0", "jar", 1577052, "d1d75aa01dffbb4d1c520e67e4c4e7f5f6174718e7cb4632412503f2f0e604fa", "3d57dc678bd8d72a60e7adf0eca5c54e3f4f4b79", 660, "cf1e4229aa49ec31430ff6b436619173a54e587947cde442b8b855904b7c11ad", "2026-05-07T13:59:42Z", "coroutines"),
    ("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0", "module", 4454, "5b255653ad6b1f21e0c46108605f129e3aa56ed9414849cc9f5b436441517528", "c96c61a5771d7f281fe7f0f86954e4b8d8931e58", 660, "3bc038f12d61d3010fb0e7939a7a1ea3f512368a3c553c6f1ad87791479faa39", "2026-05-07T13:59:42Z", "coroutines"),
    ("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0", "pom", 2168, "dd54b97aa3fab91fc24f5df2916c22be9429a339d2f45b7ff092332f6c19dc39", "2cb8283844ee479838d1a756a6a7f1c6f1acbb76", 660, "9556c1d652358bc6486787a2d3d2895ce7617db4d013c90e87f1f04b60eb9b40", "2026-05-07T13:59:42Z", "coroutines"),
    ("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0", "module", 47330, "ba1e78c5397aa934a74b9e799d290e6b769f604daadb30640f91ae6fcaf81d6d", "eda965c7969c35808f222112e1b8435d116acaa4", 660, "99369e3b9bfe2c2910dfde7e353b68c31b5d6c1f51b577d3703389ca6924ea8f", "2026-05-07T14:00:11Z", "coroutines"),
    ("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0", "pom", 2030, "9fe5cd4c606c785cc14fc4692ed1ec6cb4053c1f5e67fa8b3fa3adf1c71af9aa", "f6c6f6e97ec4e688820d56a20e882d31241401e8", 660, "38d3d617ee1715a2c3e7686fff084a93491d5c08fb76ed3796b43d479d795427", "2026-05-07T14:00:11Z", "coroutines"),
    ("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.11.0", "jar", 105295, "d4f7ee71da708af4719dceb6d3200c8f9dc5146ea1a812cb628ed53c64196f96", "1dfe8ce2b805d913922f283b386895bd0979dfcb", 660, "4770f0c587ac2c22cebf922fbf3981b131f41f6299e61b84d3c966e65a0a9d49", "2026-05-07T14:00:11Z", "coroutines"),
    ("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.11.0", "module", 4811, "83553592a446adf922dc66afb7565a3b00c52958708c210c284e7761d31d0f86", "f6cb9eaefd985f53257df55134a6793956161893", 660, "71e186e9229f36b5f473d7c880f532be387031b75b13ed425c2bb4bb9a7f56ce", "2026-05-07T14:00:11Z", "coroutines"),
    ("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.11.0", "pom", 2370, "84d119326de6496b8f20a985abe33d18e2d262b0cf2045305a91ebc57fb1f5b6", "645ccb28c1e4f8041b0532d55a06b2e785540417", 660, "66bf70eaed79e616a251357f19da82d7b16c8a2b3c03b8be483e344701ce6675", "2026-05-07T14:00:11Z", "coroutines"),
)


def artifact_records() -> list[dict[str, Any]]:
    records: list[dict[str, Any]] = []
    for coordinate, extension, size, sha256, sha1, asc_size, asc_sha, created, signer in ARTIFACT_ROWS:
        group, artifact, version = coordinate.split(":")
        filename = f"{artifact}-{version}.{extension}"
        url = (
            "https://repo.maven.apache.org/maven2/"
            + group.replace(".", "/")
            + f"/{artifact}/{version}/{filename}"
        )
        records.append(
            {
                "coordinate": coordinate,
                "extension": extension,
                "filename": filename,
                "bytes": size,
                "sha256": sha256,
                "sha1": sha1,
                "url": url,
                "signature": {
                    "bytes": asc_size,
                    "sha256": asc_sha,
                    "signatureCreatedUtc": created,
                    "signer": signer,
                    "url": url + ".asc",
                },
            }
        )
    return records


ARTIFACTS = artifact_records()
SIGNERS = {
    "kotlin": {
        "url": "https://keys.openpgp.org/vks/v1/by-keyid/6A0975F8B1127B83",
        "bytes": 12819,
        "sha256": "dd4e4696a9cea1c666733a72d9a136b2375980bfcb4167d90634e82ba7719e50",
        "primaryFingerprint": "2FBA29D08D2E25EE84C132C30729A0AFF8999A87",
        "signingFingerprint": "6F538074CCEBF35F28AF9B066A0975F8B1127B83",
        "signingKeyId": "6A0975F8B1127B83",
        "primaryUserIds": "Kotlin Release <kt-a@jetbrains.com>",
    },
    "coroutines": {
        "url": "https://keys.openpgp.org/vks/v1/by-keyid/3D5839A2262CBBFB",
        "bytes": 7127,
        "sha256": "cdd5a548f8c8d884cfa3bc502bcc752931f701aa2867df3c23a7dd266e1ebcd2",
        "primaryFingerprint": "BC900CD2FC9A9D906ECBA48BE3822B59020A349D",
        "signingFingerprint": "E7DC75FC24FB3C8DFE8086AD3D5839A2262CBBFB",
        "signingKeyId": "3D5839A2262CBBFB",
        "primaryUserIds": "",
    },
}
BOUNCY_CASTLE = {
    "bcprov-jdk18on-1.83.jar": (8492458, "82cf3a2af766c3bc874f6d36b9f20a8b99a8f09762dc776e8a227a45d8daaafb"),
    "bcpg-jdk18on-1.83.jar": (736491, "4077fd4517761c98a81944c70a376ce73f4eb3e44c03db1eb5d699fc28ab48aa"),
    "bcutil-jdk18on-1.83.jar": (707261, "ee7d0eb4e74de70a735f7fb36b604dd5c6ad35720d50b914604db042114a0185"),
}
UPSTREAM = {
    "coroutines": {
        "repository": "Kotlin/kotlinx.coroutines",
        "tag": "1.11.0",
        "commit": "8564f65764d3d05893cec026c6e94250e2b23874",
        "tree": "454b8c238b0b77bae9e5e3c2bff76da4e57e2f27",
        "releaseId": 319497021,
        "createdAt": "2026-05-07T08:06:21Z",
        "publishedAt": "2026-05-08T12:49:29Z",
        "releaseBodyBytes": 2718,
        "releaseBodySha256": "43d8e8132b13259c90e650fe16315a33959f0cfc85125b444996700765ddb56d",
        "licensePath": "LICENSE.txt",
        "licenseBlob": "9c308d958bf91eafe83f66106ca692ff414a4965",
        "licenseBytes": 11398,
        "licenseSha256": "b1febe6399dffb10d19d35e7663ab16300c93cb0476a94115df1cc0097a8ffd8",
        "notice": None,
    },
    "kotlin": {
        "repository": "JetBrains/kotlin",
        "tag": "v2.2.20",
        "commit": "693c44ee79f62895a9b92bdd60fdd7a9bc29a975",
        "tree": "ed2a6a9b5c7a46cc2be299cc2243c499ba8ce840",
        "releaseId": 245851945,
        "createdAt": "2025-08-28T12:25:38Z",
        "publishedAt": "2025-09-10T08:33:26Z",
        "releaseBodyBytes": 100225,
        "releaseBodySha256": "8c390142052ea24632a2fb9bd4dda6b4d24f9b26e02b22665e6ad266c3e065cd",
        "licensePath": "license/LICENSE.txt",
        "licenseBlob": "d645695673349e3947e8e5ae42332d0ac3164cd7",
        "licenseBytes": 11358,
        "licenseSha256": "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30",
        "notice": {
            "path": "license/NOTICE.txt",
            "bytes": 482,
            "sha256": "0b09a83d3ef7795c7dec65e815866597c066c53898f2852a76599b8f5552941a",
        },
    },
}
ARCHIVE_SCANS = {
    "kotlin-stdlib-2.2.20.jar": {"entries": 1037, "classes": 974, "majors": {"52": 973, "53": 1}},
    "kotlinx-coroutines-android-1.11.0.jar": {"entries": 28, "classes": 7, "majors": {"52": 6, "53": 1}},
    "kotlinx-coroutines-core-jvm-1.11.0.jar": {"entries": 899, "classes": 869, "majors": {"52": 868, "53": 1}},
    "kotlinx-coroutines-test-jvm-1.11.0.jar": {"entries": 68, "classes": 53, "majors": {"52": 52, "53": 1}},
}
EXPECTED_METADATA_ARTIFACTS = {
    ("org.jetbrains.kotlin", "kotlin-stdlib", "2.2.20"): {
        "kotlin-stdlib-2.2.20.jar": ARTIFACTS[0]["sha256"],
        "kotlin-stdlib-2.2.20.module": ARTIFACTS[1]["sha256"],
    },
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-android", "1.11.0"): {
        "kotlinx-coroutines-android-1.11.0.jar": ARTIFACTS[3]["sha256"],
        "kotlinx-coroutines-android-1.11.0.module": ARTIFACTS[4]["sha256"],
    },
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-bom", "1.11.0"): {
        "kotlinx-coroutines-bom-1.11.0.pom": ARTIFACTS[6]["sha256"],
    },
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.11.0"): {
        "kotlinx-coroutines-core-1.11.0.module": ARTIFACTS[7]["sha256"],
    },
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.11.0"): {
        "kotlinx-coroutines-core-jvm-1.11.0.jar": ARTIFACTS[9]["sha256"],
        "kotlinx-coroutines-core-jvm-1.11.0.module": ARTIFACTS[10]["sha256"],
    },
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-test", "1.11.0"): {
        "kotlinx-coroutines-test-1.11.0.module": ARTIFACTS[12]["sha256"],
    },
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-test-jvm", "1.11.0"): {
        "kotlinx-coroutines-test-jvm-1.11.0.jar": ARTIFACTS[14]["sha256"],
        "kotlinx-coroutines-test-jvm-1.11.0.module": ARTIFACTS[15]["sha256"],
    },
}


def validate_catalog_payload(base: bytes, candidate: bytes) -> None:
    base_lf = canonical_lf(base, "base catalog")
    candidate_lf = canonical_lf(candidate, "candidate catalog")
    require(sha256_bytes(base_lf) == BASE_HASHES[CATALOG_PATH], "Base catalog SHA mismatch")
    require(sha256_bytes(candidate_lf) == CANDIDATE_HASHES[CATALOG_PATH], "Candidate catalog SHA mismatch")
    expected = base_lf.replace(
        b'kotlinxCoroutines = "1.9.0"', b'kotlinxCoroutines = "1.11.0"', 1
    )
    require(expected != base_lf and expected == candidate_lf, "Catalog delta is not exact 1.9.0 -> 1.11.0")


def parse_lock(payload: bytes, label: str) -> dict[str, tuple[str, ...]]:
    normalized = canonical_lf(payload, label)
    require(normalized.endswith(b"\n"), f"{label} lacks final LF")
    result: dict[str, tuple[str, ...]] = {}
    previous = ""
    for raw in normalized.decode("utf-8").splitlines():
        if not raw or raw.startswith("#"):
            continue
        require(raw.count("=") == 1, f"Malformed {label} line: {raw}")
        coordinate, configurations = raw.split("=", 1)
        require(
            coordinate == "empty" or coordinate > previous,
            f"Unsorted or duplicate {label} coordinate: {coordinate}",
        )
        scopes = tuple(configurations.split(","))
        require(scopes == tuple(sorted(set(scopes))), f"Unsorted/duplicate {label} scopes: {coordinate}")
        result[coordinate] = scopes
        if coordinate != "empty":
            previous = coordinate
    return result


def validate_lock_payload(base: bytes, candidate: bytes) -> None:
    base_lf = canonical_lf(base, "base capture lock")
    candidate_lf = canonical_lf(candidate, "candidate capture lock")
    require(sha256_bytes(base_lf) == BASE_HASHES[CAPTURE_LOCK_PATH], "Base capture lock SHA mismatch")
    require(sha256_bytes(candidate_lf) == CANDIDATE_HASHES[CAPTURE_LOCK_PATH], "Candidate capture lock SHA mismatch")
    old = parse_lock(base_lf, "base capture lock")
    new = parse_lock(candidate_lf, "candidate capture lock")
    changed = {key for key in set(old) | set(new) if old.get(key) != new.get(key)}
    expected_changed = {
        "org.jetbrains.kotlin:kotlin-stdlib:2.2.10",
        "org.jetbrains.kotlin:kotlin-stdlib:2.2.20",
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.9.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.11.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.9.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.11.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0",
    }
    require(changed == expected_changed, f"Unexpected capture lock coordinate delta: {sorted(changed)}")
    affected = set(AFFECTED_CONFIGURATIONS)
    for coordinate in (
        "org.jetbrains.kotlin:kotlin-stdlib:2.2.20",
        "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.11.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0",
    ):
        require(set(new[coordinate]) == affected, f"Affected configuration projection drift: {coordinate}")
    android_tests = set(AFFECTED_CONFIGURATIONS[:3])
    for coordinate in (
        "org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.11.0",
        "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0",
    ):
        require(set(new[coordinate]) == android_tests, f"Test configuration projection drift: {coordinate}")
    require(
        set(new["org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.9.0"])
        == {"androidLintTool", "unified-test-platform-android-test-plugin-result-listener-gradle"},
        "Tooling-only Coroutines BOM 1.9.0 projection drift",
    )
    require(
        set(new["org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.9.0"])
        == {"androidLintTool", "unified-test-platform-android-test-plugin-result-listener-gradle"},
        "Tooling-only Coroutines core-jvm 1.9.0 projection drift",
    )
    require(
        set(new["org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0"])
        == {"androidLintTool", "unified-test-platform-android-test-plugin-result-listener-gradle"},
        "Tooling-only Coroutines core 1.9.0 projection drift",
    )
    require(
        "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2" not in changed,
        "Standalone VPN Coroutines 1.10.2 changed",
    )


def xml_shape(element: ET.Element) -> tuple[Any, ...]:
    tag = element.tag.rsplit("}", 1)[-1]
    return (
        tag,
        tuple(sorted(element.attrib.items())),
        (element.text or "").strip(),
        tuple(xml_shape(child) for child in element),
    )


def metadata_model(payload: bytes, label: str) -> tuple[tuple[Any, ...], dict[tuple[str, str, str], tuple[Any, ...]], dict[tuple[str, str, str], dict[str, str]]]:
    normalized = canonical_lf(payload, label)
    require(b"<!DOCTYPE" not in normalized.upper() and b"<!ENTITY" not in normalized.upper(), f"DTD/entity forbidden in {label}")
    try:
        root = ET.fromstring(normalized)
    except ET.ParseError as exc:
        raise AdmissionError(f"Malformed {label}: {exc}") from exc
    require(root.tag.rsplit("}", 1)[-1] == "verification-metadata", f"Bad {label} root")
    configuration = next((child for child in root if child.tag.rsplit("}", 1)[-1] == "configuration"), None)
    components = next((child for child in root if child.tag.rsplit("}", 1)[-1] == "components"), None)
    require(configuration is not None and components is not None, f"Incomplete {label}")
    model: dict[tuple[str, str, str], tuple[Any, ...]] = {}
    artifacts: dict[tuple[str, str, str], dict[str, str]] = {}
    for component in components:
        key = (component.attrib.get("group", ""), component.attrib.get("name", ""), component.attrib.get("version", ""))
        require(all(key) and key not in model, f"Duplicate/malformed {label} component: {key}")
        model[key] = xml_shape(component)
        artifact_map: dict[str, str] = {}
        for artifact in component:
            require(artifact.tag.rsplit("}", 1)[-1] == "artifact", f"Unexpected {label} component child")
            name = artifact.attrib.get("name", "")
            require(name and name not in artifact_map, f"Duplicate {label} artifact: {name}")
            checksums = [child for child in artifact if child.tag.rsplit("}", 1)[-1] == "sha256"]
            require(len(checksums) == 1, f"Expected one SHA-256 for {label} artifact: {name}")
            artifact_map[name] = checksums[0].attrib.get("value", "")
        artifacts[key] = artifact_map
    return xml_shape(configuration), model, artifacts


def validate_metadata_payload(base: bytes, candidate: bytes) -> None:
    base_lf = canonical_lf(base, "base verification metadata")
    candidate_lf = canonical_lf(candidate, "candidate verification metadata")
    require(sha256_bytes(base_lf) == BASE_HASHES[METADATA_PATH], "Base metadata SHA mismatch")
    require(len(candidate_lf) == METADATA_LF_BYTES, "Candidate metadata LF byte count mismatch")
    require(sha256_bytes(candidate_lf) == CANDIDATE_HASHES[METADATA_PATH], "Candidate metadata SHA mismatch")
    base_config, base_components, _base_artifacts = metadata_model(base_lf, "base verification metadata")
    candidate_config, candidate_components, candidate_artifacts = metadata_model(candidate_lf, "candidate verification metadata")
    require(base_config == candidate_config, "Verification configuration/trust semantics changed")
    require(set(base_components).issubset(candidate_components), "Pre-existing metadata component removed")
    for key, value in base_components.items():
        require(candidate_components[key] == value, f"Pre-existing metadata component changed: {key}")
    added = set(candidate_components) - set(base_components)
    require(added == set(EXPECTED_METADATA_ARTIFACTS), f"Unexpected metadata component delta: {sorted(added)}")
    for key, expected in EXPECTED_METADATA_ARTIFACTS.items():
        require(candidate_artifacts[key] == expected, f"Metadata artifact/checksum drift: {key}")


def validate_ksp_history() -> None:
    require(git_text("rev-parse", f"{KSP_INTEGRATION_COMMIT}^{{commit}}") == KSP_INTEGRATION_COMMIT, "KSP integration commit missing")
    require(git_text("rev-parse", f"{KSP_INTEGRATION_COMMIT}^{{tree}}") == KSP_INTEGRATION_TREE, "KSP integration tree drift")
    require(git("merge-base", "--is-ancestor", KSP_INTEGRATION_COMMIT, "HEAD", check=False).returncode == 0, "KSP integration is not an ancestor")
    for relative, blob in KSP_FROZEN_BLOBS.items():
        require(git_text("rev-parse", f"{KSP_INTEGRATION_COMMIT}:{relative}") == blob, f"KSP historical blob drift: {relative}")
    require(file_sha(SEARCH_LOCK_PATH) == BASE_HASHES[SEARCH_LOCK_PATH], "Search lock changed")
    require(file_sha(KSP_EVIDENCE_PATH) == BASE_HASHES[KSP_EVIDENCE_PATH], "KSP evidence changed")


def tracked_at(revision: str, prefix: str) -> dict[str, bytes]:
    names = git_bytes("ls-tree", "-r", "--name-only", "-z", revision, "--", prefix).split(b"\0")
    result: dict[str, bytes] = {}
    for raw in names:
        if raw:
            relative = raw.decode("utf-8", errors="strict")
            result[relative] = b""
    return result


def validate_recovery_frozen() -> None:
    for prefix in ("android/poc/recovery", "docs/evidence/poc-recovery-001"):
        expected = tracked_at(BASE_COMMIT, prefix)
        actual_names = {
            item.replace("\\", "/")
            for item in git_text("ls-files", "--", prefix).splitlines()
            if item
        }
        require(actual_names == set(expected), f"Recovery protected inventory drift: {prefix}")
        for relative in expected:
            require(
                git_text("hash-object", f"--path={relative}", relative)
                == git_text("rev-parse", f"{BASE_COMMIT}:{relative}"),
                f"Recovery protected payload drift: {relative}",
            )
    for relative in (
        "tools/verify_poc_recovery_dependency_inventory.py",
        "tools/check_poc_recovery_run_readiness.py",
        "android/build.gradle.kts",
    ):
        require(
            git_text("hash-object", f"--path={relative}", relative)
            == git_text("rev-parse", f"{BASE_COMMIT}:{relative}"),
            f"Recovery protected file drift: {relative}",
        )
    readiness = parse_json_strict(file_bytes("docs/evidence/poc-recovery-001/readiness.json"), "Recovery readiness")
    encoded = json.dumps(readiness, ensure_ascii=False)
    for forbidden in ('"executionAllowed": true', '"recI3MayProceedNow": true', '"measurementAuthorized": true'):
        require(forbidden not in encoded, f"Recovery authority escalated: {forbidden}")


def validate_other_lockfiles() -> None:
    names = [
        raw.decode("utf-8", errors="strict")
        for raw in git_bytes("ls-files", "-z", "*gradle.lockfile").split(b"\0")
        if raw
    ]
    require(CAPTURE_LOCK_PATH in names and len(names) >= 10, "Repository lock inventory incomplete")
    for relative in names:
        if relative != CAPTURE_LOCK_PATH:
            require(file_bytes(relative) == git_blob(BASE_COMMIT, relative), f"Unrelated lockfile changed: {relative}")


def validate_evidence_object(evidence: dict[str, Any]) -> None:
    require(
        set(evidence)
        == {
            "affectedConfigurations", "artifacts", "authorization", "base", "claimCeiling",
            "fileHashes", "graph", "nonClaims", "offlineContract", "schemaVersion", "securitySnapshot",
            "signers", "status", "upstream",
        },
        "Evidence top-level schema drift",
    )
    require(evidence["schemaVersion"] == 1, "Evidence schema version drift")
    require(evidence["authorization"] == AUTHORIZATION, "Evidence authorization drift")
    require(evidence["status"] == "LOCAL_VERIFICATION_PENDING_REVIEW_AND_CI", "Evidence status drift")
    require(evidence["claimCeiling"] == CLAIM, "Evidence claim escalation/drift")
    require(evidence["base"] == {"commit": BASE_COMMIT, "tree": BASE_TREE}, "Evidence base drift")
    require(evidence["affectedConfigurations"] == list(AFFECTED_CONFIGURATIONS), "Affected configurations evidence drift")
    require(evidence["artifacts"] == ARTIFACTS, "Exact Maven/signature evidence drift")
    require(evidence["signers"] == SIGNERS, "Signer evidence drift")
    require(evidence["upstream"] == UPSTREAM, "Upstream source/release/license evidence drift")
    require(
        evidence["fileHashes"]
        == {
            "base": BASE_HASHES,
            "candidate": CANDIDATE_HASHES,
            "candidateMetadataGitBlobSha1": METADATA_BLOB,
            "candidateMetadataLfBytes": METADATA_LF_BYTES,
        },
        "Evidence file hash pins drift",
    )
    graph = evidence["graph"]
    require(
        graph
        == {
            "changedLockfiles": [CAPTURE_LOCK_PATH],
            "newlySelectedCoordinates": [
                "org.jetbrains.kotlin:kotlin-stdlib:2.2.20",
                "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0",
                "org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.11.0",
                "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0",
                "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0",
                "org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.11.0",
                "org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0",
            ],
            "verificationMetadataAddedComponents": 7,
            "verificationMetadataAddedArtifacts": 11,
            "standaloneVpnCoroutinesVersion": "1.10.2_UNCHANGED",
            "kspClassification": "BUILD_TOOL_ONLY_UNCHANGED",
            "recoveryExecutionAllowed": False,
        },
        "Evidence graph facts drift",
    )
    require(
        evidence["offlineContract"]
        == {
            "affectedGradleTasks": list(AFFECTED_GRADLE_TASKS),
            "canonicalProjectionDefinition": (
                "SORTED_UTF8_LF_JSON_OF_EXACT_TASKS_AFFECTED_CONFIGURATIONS_"
                "LOCKED_GRAPH_AND_CATALOG_LOCK_METADATA_SHA256"
            ),
            "canonicalProjectionRuns": 2,
            "cleanupRequired": True,
            "gradleUserHome": "FRESH_JOB_SCOPED_BOUNDED_RUNNER_TEMP",
            "onlineSeedRequiredBeforeOffline": True,
        },
        "Offline reproduction contract drift",
    )
    require(
        evidence["securitySnapshot"]
        == {
            "checkedAt": "2026-08-19T09:42:45Z",
            "githubRepositoryAdvisoryCounts": {"JetBrains/kotlin": 0, "Kotlin/kotlinx.coroutines": 0},
            "globalMavenAdvisoryCountByCoordinate": {
                coordinate: 0
                for coordinate in sorted({row[0] for row in ARTIFACT_ROWS})
            },
            "claim": "POINT_IN_TIME_ZERO_RESULTS_NOT_A_BLANKET_VULNERABILITY_FREE_CLAIM",
        },
        "Security snapshot evidence drift",
    )
    require(
        evidence["nonClaims"]
        == [
            "No production Legal or Security approval.",
            "No blanket vulnerability-free claim.",
            "No Recovery execution, measurement, PASS, readiness, REC-I3, or admission claim.",
            "PoC-scoped dependency-family update only; no production dependency admission.",
            "No Search, Capture, VPN, KSP, Room, schema, model, backend, or product-feature admission.",
            "Parent PoC states and thresholds are unchanged.",
        ],
        "Evidence non-claims drift",
    )


def validate_evidence() -> str:
    payload = canonical_lf(file_bytes(EVIDENCE_PATH), EVIDENCE_PATH)
    evidence = parse_json_strict(payload, EVIDENCE_PATH)
    require(payload == canonical_json(evidence), "Evidence JSON is not canonical sorted UTF-8/LF")
    validate_evidence_object(evidence)
    return sha256_bytes(payload)


def validate_repository_snapshot() -> str:
    require(git_text("rev-parse", f"{BASE_COMMIT}^{{commit}}") == BASE_COMMIT, "Exact base commit unavailable")
    require(git_text("rev-parse", f"{BASE_COMMIT}^{{tree}}") == BASE_TREE, "Exact base tree drift")
    validate_catalog_payload(git_blob(BASE_COMMIT, CATALOG_PATH), file_bytes(CATALOG_PATH))
    validate_lock_payload(git_blob(BASE_COMMIT, CAPTURE_LOCK_PATH), file_bytes(CAPTURE_LOCK_PATH))
    validate_metadata_payload(git_blob(BASE_COMMIT, METADATA_PATH), file_bytes(METADATA_PATH))
    validate_other_lockfiles()
    validate_ksp_history()
    validate_recovery_frozen()
    require(not (ROOT / "android/local.properties").exists(), "android/local.properties is forbidden")
    return validate_evidence()


def validate_search_ksp_successor_layer(repo_root: Path) -> None:
    require(repo_root.resolve() == ROOT.resolve(), "Unexpected repository root for Search successor")
    validate_repository_snapshot()


def validate_recovery_successor_layer(repo_root: Path) -> None:
    require(repo_root.resolve() == ROOT.resolve(), "Unexpected repository root for Recovery successor")
    validate_repository_snapshot()


def event_base() -> str | None:
    event_path = os.environ.get("GITHUB_EVENT_PATH")
    if os.environ.get("GITHUB_EVENT_NAME") == "pull_request" and event_path:
        payload = parse_json_strict(Path(event_path).read_bytes(), "GitHub event")
        base = payload.get("pull_request", {}).get("base", {}).get("sha")
        require(isinstance(base, str) and re.fullmatch(r"[0-9a-f]{40}", base) is not None, "Malformed GitHub PR base SHA")
        return base
    remote = git("rev-parse", "--verify", "refs/remotes/origin/main^{commit}", check=False)
    return remote.stdout.decode("ascii").strip() if remote.returncode == 0 else None


def changed_paths(base: str, head: str) -> list[str]:
    return sorted(
        raw.decode("utf-8", errors="strict")
        for raw in git_bytes("diff", "--name-only", "--no-renames", "-z", base, head, "--").split(b"\0")
        if raw
    )


def validate_delivery_lifecycle() -> None:
    head = git_text("rev-parse", "HEAD")
    require(git("merge-base", "--is-ancestor", BASE_COMMIT, head, check=False).returncode == 0, "HEAD is not based on exact current main")
    base = event_base()
    if base is not None and git("cat-file", "-e", f"{base}:{EVIDENCE_PATH}", check=False).returncode != 0:
        require(base == BASE_COMMIT, f"Initial admission base drift: {base}")
        actual = changed_paths(base, head)
        require(actual == sorted(EXPECTED_PATHS), f"Initial admission is not exact ten-path scope: {actual}")
        commits = [line for line in git_text("rev-list", "--reverse", "--ancestry-path", f"{base}..{head}").splitlines() if line]
        require(commits, "Admission commit sequence is empty")
        first = commits[0]
        require(git_text("show", "-s", "--format=%P", first) == BASE_COMMIT, "Scope commit is not direct child of exact base")
        require(changed_paths(f"{first}^", first) == [SCOPE_PATH], "First commit is not scope-doc-only")
    status = git_bytes("status", "--porcelain=v1", "-z")
    require(not status, "Admission checkout is dirty")
    require(git_text("rev-parse", f"HEAD:{METADATA_PATH}") == METADATA_BLOB, "Candidate metadata blob is dirty/uncommitted")


def http_bytes(url: str, *, accept: str = "application/octet-stream") -> bytes:
    headers = {"Accept": accept, "User-Agent": "DORA-stage0-coroutines-admission"}
    token = os.environ.get("GITHUB_TOKEN")
    if token and url.startswith("https://api.github.com/"):
        headers["Authorization"] = f"Bearer {token}"
        headers["X-GitHub-Api-Version"] = "2022-11-28"
    request = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            return response.read()
    except Exception as exc:
        raise AdmissionError(f"Download failed: {url}: {exc}") from exc


def http_json(url: str) -> Any:
    payload = http_bytes(url, accept="application/vnd.github+json")
    try:
        return json.loads(payload.decode("utf-8", errors="strict"))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise AdmissionError(f"Malformed GitHub JSON: {url}: {exc}") from exc


def advisory_affects(coordinate: str) -> str:
    parts = coordinate.split(":")
    require(len(parts) == 3 and all(parts), f"Malformed Maven coordinate: {coordinate}")
    group, artifact, version = parts
    return f"{group}:{artifact}@{version}"


def write_download(url: str, path: Path, expected_bytes: int, expected_sha: str) -> None:
    payload = http_bytes(url)
    require(len(payload) == expected_bytes, f"Downloaded byte count mismatch: {url}")
    require(sha256_bytes(payload) == expected_sha, f"Downloaded SHA-256 mismatch: {url}")
    path.write_bytes(payload)


def run_checked(command: list[str], label: str, *, cwd: Path | None = None) -> str:
    completed = subprocess.run(
        command,
        cwd=cwd,
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        text=True,
        encoding="utf-8",
        errors="replace",
    )
    require(completed.returncode == 0, f"{label} failed: {completed.stdout[-2000:]} {completed.stderr[-2000:]}")
    return completed.stdout


def prepare_pgp(temp: Path) -> tuple[str, str]:
    java, javac, jdeps = shutil.which("java"), shutil.which("javac"), shutil.which("jdeps")
    require(bool(java and javac and jdeps), "JDK 17 java/javac/jdeps required")
    version = run_checked([javac, "-version"], "javac version")
    require(version.startswith("javac 17."), f"Strict JDK 17 required, got {version.strip()}")
    libraries: list[Path] = []
    for filename, (size, digest) in BOUNCY_CASTLE.items():
        path = temp / filename
        url = f"https://repo.maven.apache.org/maven2/org/bouncycastle/{filename.split('-jdk')[0]}-jdk18on/1.83/{filename}"
        write_download(url, path, size, digest)
        libraries.append(path)
    classes = temp / "classes"
    classes.mkdir()
    classpath = os.pathsep.join(str(path) for path in libraries)
    run_checked(
        [javac, "--release", "17", "-Xlint:all", "-Werror", "-cp", classpath, "-d", str(classes), str(ROOT / "tools/OpenPgpDetachedSignatureVerifier.java")],
        "strict OpenPGP verifier compilation",
    )
    class_payload = (classes / "OpenPgpDetachedSignatureVerifier.class").read_bytes()
    require(class_payload[:4] == b"\xca\xfe\xba\xbe" and struct.unpack(">H", class_payload[6:8])[0] == 61, "OpenPGP verifier class is not Java 17 major 61")
    modules = run_checked(
        [
            jdeps,
            "--multi-release",
            "17",
            "--ignore-missing-deps",
            "--print-module-deps",
            str(classes),
        ],
        "OpenPGP verifier jdeps",
    ).strip()
    require(modules == "java.base", f"Unexpected JDK module dependency: {modules}")
    return java, os.pathsep.join([str(classes), classpath])


def parse_pgp_output(output: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for line in output.splitlines():
        key, separator, value = line.partition("\t")
        require(separator == "\t" and key and key not in result, f"Malformed PGP verifier output: {line}")
        result[key] = value
    require(set(result) == {"verified", "primaryFingerprint", "signingFingerprint", "signingKeyId", "signatureCreatedUtc", "publicKeyAlgorithm", "hashAlgorithm", "primaryUserIds"}, "Incomplete PGP verifier output")
    return result


def verify_archive(path: Path, expected: dict[str, Any]) -> None:
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        class_names = [name for name in names if name.endswith(".class")]
        majors: dict[str, int] = {}
        for name in class_names:
            payload = archive.read(name)
            require(payload[:4] == b"\xca\xfe\xba\xbe", f"Malformed class in {path.name}: {name}")
            major = str(struct.unpack(">H", payload[6:8])[0])
            majors[major] = majors.get(major, 0) + 1
            if major == "53":
                require(name == "META-INF/versions/9/module-info.class", f"Unexpected Java 9 class: {name}")
        lowered = [name.lower() for name in names]
        native = [name for name in lowered if name.endswith((".so", ".dll", ".dylib", ".jnilib")) or "/jni/" in f"/{name}/"]
        license_entries = [name for name in lowered if re.search(r"(^|/)(license|notice|copying)(\..*)?$", name)]
        require(not native, f"Native/JNI surface in {path.name}: {native}")
        require(not license_entries, f"Unexpected embedded license/notice in {path.name}: {license_entries}")
        require({"entries": len(names), "classes": len(class_names), "majors": majors} == expected, f"Archive inventory drift: {path.name}")


def verify_pom(path: Path) -> None:
    root = ET.fromstring(path.read_bytes())
    namespace = root.tag.split("}", 1)[0] + "}" if "}" in root.tag else ""
    licenses = root.find(f"{namespace}licenses")
    require(licenses is not None, f"POM license absent: {path.name}")
    names = [item.findtext(f"{namespace}name", default="") for item in licenses.findall(f"{namespace}license")]
    require(names == ["Apache-2.0"], f"POM license drift: {path.name}: {names}")


def verify_online() -> None:
    with tempfile.TemporaryDirectory(prefix="dora-coroutines-admission-") as temporary:
        temp = Path(temporary)
        java, classpath = prepare_pgp(temp)
        keys: dict[str, Path] = {}
        for name, record in SIGNERS.items():
            path = temp / f"{name}.asc"
            write_download(record["url"], path, record["bytes"], record["sha256"])
            keys[name] = path
        for index, record in enumerate(ARTIFACTS):
            artifact = temp / f"artifact-{index}-{record['filename']}"
            signature = temp / f"artifact-{index}.asc"
            write_download(record["url"], artifact, record["bytes"], record["sha256"])
            signature_record = record["signature"]
            write_download(signature_record["url"], signature, signature_record["bytes"], signature_record["sha256"])
            official_sha1 = http_bytes(record["url"] + ".sha1").decode("ascii").strip().lower()
            require(official_sha1 == record["sha1"], f"Official Maven SHA-1 drift: {record['filename']}")
            signer = SIGNERS[signature_record["signer"]]
            output = run_checked([java, "-cp", classpath, "OpenPgpDetachedSignatureVerifier", str(keys[signature_record["signer"]]), str(signature), str(artifact)], f"PGP verification {record['filename']}")
            pgp = parse_pgp_output(output)
            require(
                pgp == {
                    "verified": "true",
                    "primaryFingerprint": signer["primaryFingerprint"],
                    "signingFingerprint": signer["signingFingerprint"],
                    "signingKeyId": signer["signingKeyId"],
                    "signatureCreatedUtc": signature_record["signatureCreatedUtc"],
                    "publicKeyAlgorithm": "1",
                    "hashAlgorithm": "SHA512",
                    "primaryUserIds": signer["primaryUserIds"],
                },
                f"PGP signer/signature fact drift: {record['filename']}",
            )
            if record["extension"] == "jar":
                verify_archive(artifact, ARCHIVE_SCANS[record["filename"]])
            if record["extension"] == "pom":
                verify_pom(artifact)
        for record in UPSTREAM.values():
            repository, tag = record["repository"], record["tag"]
            release = http_json(f"https://api.github.com/repos/{repository}/releases/tags/{urllib.parse.quote(tag, safe='')}")
            require(release["id"] == record["releaseId"] and release["created_at"] == record["createdAt"] and release["published_at"] == record["publishedAt"], f"GitHub release identity drift: {repository}")
            body = release.get("body", "").encode("utf-8")
            require(len(body) == record["releaseBodyBytes"] and sha256_bytes(body) == record["releaseBodySha256"], f"Release body drift: {repository}")
            reference = http_json(f"https://api.github.com/repos/{repository}/git/ref/tags/{urllib.parse.quote(tag, safe='')}")
            require(reference["object"]["type"] == "commit" and reference["object"]["sha"] == record["commit"], f"Tag target drift: {repository}")
            commit = http_json(f"https://api.github.com/repos/{repository}/git/commits/{record['commit']}")
            require(commit["tree"]["sha"] == record["tree"] and commit["verification"]["verified"] is False, f"Unsigned commit/tree fact drift: {repository}")
            license_payload = http_bytes(f"https://raw.githubusercontent.com/{repository}/{record['commit']}/{record['licensePath']}")
            require(len(license_payload) == record["licenseBytes"] and sha256_bytes(license_payload) == record["licenseSha256"], f"Source license drift: {repository}")
            blob = http_json(f"https://api.github.com/repos/{repository}/contents/{record['licensePath']}?ref={record['commit']}")
            require(blob["sha"] == record["licenseBlob"], f"Source license blob drift: {repository}")
            if record["notice"]:
                notice = record["notice"]
                notice_payload = http_bytes(f"https://raw.githubusercontent.com/{repository}/{record['commit']}/{notice['path']}")
                require(len(notice_payload) == notice["bytes"] and sha256_bytes(notice_payload) == notice["sha256"], f"Source NOTICE drift: {repository}")
            advisories = http_json(f"https://api.github.com/repos/{repository}/security-advisories?per_page=100")
            require(advisories == [], f"Repository advisory snapshot is no longer zero: {repository}")
        positive_control = urllib.parse.urlencode(
            {
                "ecosystem": "maven",
                "affects": advisory_affects("org.apache.logging.log4j:log4j-core:2.14.1"),
                "per_page": "100",
            }
        )
        control_advisories = http_json(f"https://api.github.com/advisories?{positive_control}")
        require(
            isinstance(control_advisories, list) and len(control_advisories) >= 1,
            "GitHub Advisory Database positive control unexpectedly returned zero",
        )
        for coordinate in sorted({row[0] for row in ARTIFACT_ROWS}):
            query = urllib.parse.urlencode(
                {
                    "ecosystem": "maven",
                    "affects": advisory_affects(coordinate),
                    "per_page": "100",
                }
            )
            advisories = http_json(f"https://api.github.com/advisories?{query}")
            require(advisories == [], f"Global Maven advisory snapshot is no longer zero: {coordinate}")


def canonical_offline_projection() -> bytes:
    lock = parse_lock(file_bytes(CAPTURE_LOCK_PATH), "candidate capture lock")
    affected = set(AFFECTED_CONFIGURATIONS)
    graph = {
        coordinate: [scope for scope in scopes if scope in affected]
        for coordinate, scopes in lock.items()
        if set(scopes).intersection(affected)
    }
    value = {
        "affectedConfigurations": list(AFFECTED_CONFIGURATIONS),
        "affectedGradleTasks": list(AFFECTED_GRADLE_TASKS),
        "catalogSha256": file_sha(CATALOG_PATH),
        "captureLockSha256": file_sha(CAPTURE_LOCK_PATH),
        "graph": graph,
        "verificationMetadataSha256": file_sha(METADATA_PATH),
    }
    return canonical_json(value)


def verify_offline_gradle() -> str:
    wrapper = ROOT / "android" / ("gradlew.bat" if os.name == "nt" else "gradlew")
    gradle_home = os.environ.get("GRADLE_USER_HOME", "")
    require(gradle_home, "GRADLE_USER_HOME must name a fresh bounded home")
    gradle_home_path = Path(gradle_home).resolve()
    require(gradle_home_path.is_dir(), "Bounded GRADLE_USER_HOME is absent")
    command = [
        str(wrapper),
        "--no-daemon",
        "--stacktrace",
        "--offline",
        "--no-configuration-cache",
        *AFFECTED_GRADLE_TASKS,
    ]
    projections: list[bytes] = []
    for run in (1, 2):
        run_checked(command, f"offline affected Capture command run {run}", cwd=ROOT / "android")
        validate_repository_snapshot()
        projections.append(canonical_offline_projection())
    require(projections[0] == projections[1], "Offline canonical graph projection differs across repeats")
    return sha256_bytes(projections[0])


def run_self_tests() -> int:
    base_catalog = git_blob(BASE_COMMIT, CATALOG_PATH)
    catalog = file_bytes(CATALOG_PATH)
    base_lock = git_blob(BASE_COMMIT, CAPTURE_LOCK_PATH)
    lock = file_bytes(CAPTURE_LOCK_PATH)
    base_metadata = git_blob(BASE_COMMIT, METADATA_PATH)
    metadata = file_bytes(METADATA_PATH)
    evidence = parse_json_strict(file_bytes(EVIDENCE_PATH), EVIDENCE_PATH)
    cases = 0

    def reject(label: str, action: Callable[[], None]) -> None:
        nonlocal cases
        try:
            action()
        except (AdmissionError, ET.ParseError, KeyError, TypeError, ValueError):
            cases += 1
            return
        raise AdmissionError(f"Negative self-test unexpectedly passed: {label}")

    validate_catalog_payload(base_catalog, catalog)
    validate_lock_payload(base_lock, lock)
    validate_metadata_payload(base_metadata, metadata)
    validate_evidence_object(evidence)
    for label, payload in (
        ("catalog-other-version", catalog.replace(b'ksp = "2.3.11"', b'ksp = "2.3.12"')),
        ("catalog-cr", catalog + b"\r"),
        ("catalog-extra", catalog + b"# spoof\n"),
    ):
        reject(label, lambda payload=payload: validate_catalog_payload(base_catalog, payload))
    for label, payload in (
        ("lock-extra", lock + b"example.invalid:spoof:1=debugRuntimeClasspath\n"),
        ("lock-remove", lock.replace(b"org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0=", b"org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.1=")),
        ("lock-bare-cr", lock + b"\r"),
    ):
        reject(label, lambda payload=payload: validate_lock_payload(base_lock, payload))
    for label, payload in (
        ("metadata-checksum", metadata.replace(ARTIFACTS[0]["sha256"].encode(), b"0" * 64, 1)),
        ("metadata-component", metadata.replace(b"kotlinx-coroutines-test-jvm", b"kotlinx-coroutines-test-jvn", 1)),
        ("metadata-doctype", b"<!DOCTYPE spoof>" + metadata),
        ("metadata-truncated", metadata[:100]),
    ):
        reject(label, lambda payload=payload: validate_metadata_payload(base_metadata, payload))
    for label, mutation in (
        ("evidence-claim", lambda value: value.__setitem__("claimCeiling", "READY")),
        ("evidence-status", lambda value: value.__setitem__("status", "PASS")),
        ("evidence-security", lambda value: value["securitySnapshot"].__setitem__("claim", "VULNERABILITY_FREE")),
        ("evidence-recovery", lambda value: value["graph"].__setitem__("recoveryExecutionAllowed", True)),
        ("evidence-artifact", lambda value: value["artifacts"][0].__setitem__("sha256", "0" * 64)),
        ("evidence-extra", lambda value: value.__setitem__("unexpected", True)),
    ):
        candidate = copy.deepcopy(evidence)
        mutation(candidate)
        reject(label, lambda candidate=candidate: validate_evidence_object(candidate))
    require(
        advisory_affects("org.apache.logging.log4j:log4j-core:2.14.1")
        == "org.apache.logging.log4j:log4j-core@2.14.1",
        "GitHub advisory package@version mapping drift",
    )
    cases += 1
    reject("advisory-malformed-coordinate", lambda: advisory_affects("group:artifact:version:extra"))
    require(cases == 18, f"Self-test count drift: {cases}")
    return cases


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    modes = parser.add_mutually_exclusive_group()
    modes.add_argument("--online", action="store_true")
    modes.add_argument("--offline", action="store_true")
    modes.add_argument("--self-test", action="store_true")
    modes.add_argument("--static", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    evidence_sha = validate_repository_snapshot()
    validate_delivery_lifecycle()
    mode = "static"
    suffix = ""
    if args.online:
        mode = "online"
        verify_online()
    elif args.offline:
        mode = "offline"
        suffix = f" runs=2 projectionSha256={verify_offline_gradle()}"
    elif args.self_test:
        mode = "self-test"
        suffix = f" cases={run_self_tests()}"
    print(f"PASS kotlinx-coroutines-admission mode={mode}{suffix} evidenceSha256={evidence_sha} claim={CLAIM}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (AdmissionError, OSError, KeyError, TypeError, ValueError, ET.ParseError, zipfile.BadZipFile) as error:
        print(f"FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
