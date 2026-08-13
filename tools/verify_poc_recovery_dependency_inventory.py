#!/usr/bin/env python3
"""Verify the exact published Tink closure and v0.6 governance/IP boundary without Gradle changes."""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import os
import re
import shutil
import struct
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from typing import Any

from validate_poc_recovery_governance import (
    AUTHORIZATION_ID,
    AUTHORIZATION_PATH,
    validate_authorization_record,
    validate_recovery_build_text,
)


ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "docs" / "evidence" / "poc-recovery-001"
INVENTORY_PATH = EVIDENCE / "dependency-inventory.json"
LICENSE_PATH = EVIDENCE / "license-notice-inventory.json"
AUTHENTICITY_PATH = EVIDENCE / "dependency-ip-authenticity-v0.3.json"
JSR305_EXCLUSION_PATH = EVIDENCE / "jsr305-exclusion-analysis-2026-08-12.json"
JSR305_CLASS_LIST_PATH = EVIDENCE / "jsr305-reference-classes-2026-08-12.txt"
READINESS_PATH = EVIDENCE / "readiness.json"
REVIEW_ROLES_PATH = EVIDENCE / "review-roles.json"
RECOVERY_BUILD_PATH = ROOT / "android" / "poc" / "recovery" / "build.gradle.kts"
RECOVERY_LOCK_PATH = ROOT / "android" / "poc" / "recovery" / "gradle.lockfile"
OWNER_DECISION_PATH = ROOT / "docs" / "stage0" / "DORA_MVP1_POC_RECOVERY_OWNER_DECISION_OD14.md"
OWNER_DECISION_INPUT_HEAD = "eb312feb2a0d5e5b24b45fcd045bacca94e8c9da"
GATE_ID = "poc-recovery-stage0-v0.6"
PROTOCOL_ID = "poc-recovery-protocol-stage0-v0.6"
MAVEN_NS = {"m": "http://maven.apache.org/POM/4.0.0"}
USER_AGENT = "Dora-POC-RECOVERY-001-inventory-verifier/1.0"
JETBRAINS_COMMIT = "f92ce9af0629ee8dcc8743dcc2c1ca297aaacc7c"
JETBRAINS_LICENSE_URL = f"https://github.com/JetBrains/intellij-community/blob/{JETBRAINS_COMMIT}/LICENSE.txt"
JETBRAINS_NOTICE_URL = f"https://github.com/JetBrains/intellij-community/blob/{JETBRAINS_COMMIT}/NOTICE.txt"
JETBRAINS_LICENSE_SHA256 = "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30"
JETBRAINS_NOTICE_SHA256 = "0479f6a86003002dec1da1667f2f8320253c7225c6ffffc05cf7e0988bd8c72c"
R8_RULES = [
    "-dontwarn javax.annotation.Nullable",
    "-dontwarn javax.annotation.concurrent.GuardedBy",
    "-dontwarn javax.annotation.concurrent.ThreadSafe",
]
NATIVE_SUFFIXES = (".so", ".dll", ".dylib")
OPENPGP_VERIFIER_SOURCE = ROOT / "tools" / "OpenPgpDetachedSignatureVerifier.java"
BOUNCY_CASTLE_LIBRARIES = {
    "bcprov-jdk18on-1.83.jar": {
        "url": "https://repo.maven.apache.org/maven2/org/bouncycastle/bcprov-jdk18on/1.83/bcprov-jdk18on-1.83.jar",
        "bytes": 8_492_458,
        "sha256": "82cf3a2af766c3bc874f6d36b9f20a8b99a8f09762dc776e8a227a45d8daaafb",
    },
    "bcpg-jdk18on-1.83.jar": {
        "url": "https://repo.maven.apache.org/maven2/org/bouncycastle/bcpg-jdk18on/1.83/bcpg-jdk18on-1.83.jar",
        "bytes": 736_491,
        "sha256": "4077fd4517761c98a81944c70a376ce73f4eb3e44c03db1eb5d699fc28ab48aa",
    },
    "bcutil-jdk18on-1.83.jar": {
        "url": "https://repo.maven.apache.org/maven2/org/bouncycastle/bcutil-jdk18on/1.83/bcutil-jdk18on-1.83.jar",
        "bytes": 707_261,
        "sha256": "ee7d0eb4e74de70a735f7fb36b604dd5c6ad35720d50b914604db042114a0185",
    },
}
AUTHENTICITY_STATUSES = {
    "AUTHENTICITY_VERIFIED_PUBLISHER_BOUND_SIGNATURE",
    "AUTHENTICITY_VERIFIED_EXACT_REPRODUCIBLE_SOURCE",
    "AUTHENTICITY_VERIFIED_MULTISOURCE_CORRESPONDENCE",
    "AUTHENTICITY_PENDING",
    "AUTHENTICITY_REJECTED",
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def download(url: str, target: Path) -> None:
    require(url.startswith("https://"), f"Non-HTTPS artifact URL: {url}")
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    last_error: Exception | None = None
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=90) as response, target.open("wb") as output:
                require(response.status == 200, f"Unexpected HTTP {response.status} for {url}")
                while chunk := response.read(1024 * 1024):
                    output.write(chunk)
            return
        except (OSError, urllib.error.URLError) as error:
            last_error = error
            if attempt < 2:
                time.sleep(attempt + 1)
    raise OSError(f"Failed to download {url}: {last_error}")


def github_content_bytes(repository: str, path: str, commit: str) -> bytes:
    url = f"https://raw.githubusercontent.com/{repository}/{commit}/{path}"
    request = urllib.request.Request(
        url,
        headers={"User-Agent": USER_AGENT},
    )
    with urllib.request.urlopen(request, timeout=90) as response:
        require(response.status == 200, f"Unexpected HTTP {response.status} for {url}")
        return response.read()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        while chunk := handle.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def file_digest(path: Path, algorithm: str) -> str:
    normalized = algorithm.lower().replace("-", "")
    require(normalized in {"sha1", "sha256"}, f"Unsupported publisher checksum algorithm: {algorithm}")
    digest = hashlib.new(normalized)
    with path.open("rb") as handle:
        while chunk := handle.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def parse_publisher_checksum(path: Path, algorithm: str) -> str:
    length = 40 if algorithm.upper() == "SHA-1" else 64
    matches = re.findall(rf"(?i)(?<![0-9a-f])[0-9a-f]{{{length}}}(?![0-9a-f])", path.read_text(encoding="ascii"))
    require(len(set(value.lower() for value in matches)) == 1, f"Publisher checksum file is ambiguous: {path.name}")
    return matches[0].lower()


def read_packet_length(data: bytes, offset: int, new_format: bool, old_length_type: int = 0) -> tuple[int, int]:
    first = data[offset]
    if not new_format:
        if old_length_type == 0:
            return first, offset + 1
        if old_length_type == 1:
            return int.from_bytes(data[offset:offset + 2], "big"), offset + 2
        if old_length_type == 2:
            return int.from_bytes(data[offset:offset + 4], "big"), offset + 4
        raise ValueError("Indeterminate-length OpenPGP packets are forbidden")
    if first < 192:
        return first, offset + 1
    if first < 224:
        return ((first - 192) << 8) + data[offset + 1] + 192, offset + 2
    if first == 255:
        return int.from_bytes(data[offset + 1:offset + 5], "big"), offset + 5
    raise ValueError("Partial-length OpenPGP packets are unsupported")


def openpgp_packets(data: bytes) -> list[tuple[int, bytes]]:
    packets: list[tuple[int, bytes]] = []
    offset = 0
    while offset < len(data):
        header = data[offset]
        require(header & 0x80 != 0, "Malformed OpenPGP packet header")
        offset += 1
        new_format = header & 0x40 != 0
        if new_format:
            tag = header & 0x3F
            length, offset = read_packet_length(data, offset, True)
        else:
            tag = (header >> 2) & 0x0F
            length, offset = read_packet_length(data, offset, False, header & 0x03)
        require(offset + length <= len(data), "Truncated OpenPGP packet")
        packets.append((tag, data[offset:offset + length]))
        offset += length
    return packets


def dearmor_signature(path: Path) -> bytes:
    text = path.read_text(encoding="ascii")
    require("-----BEGIN PGP SIGNATURE-----" in text and "-----END PGP SIGNATURE-----" in text, f"Detached signature armor missing: {path.name}")
    body = text.split("-----BEGIN PGP SIGNATURE-----", 1)[1].split("-----END PGP SIGNATURE-----", 1)[0]
    lines = [line.strip() for line in body.replace("\r", "").split("\n")]
    encoded: list[str] = []
    for line in lines:
        if not line or line.startswith("=") or ":" in line:
            continue
        encoded.append(line)
    require(encoded, f"Detached signature payload missing: {path.name}")
    return base64.b64decode("".join(encoded), validate=True)


def subpacket_length(data: bytes, offset: int) -> tuple[int, int]:
    first = data[offset]
    if first < 192:
        return first, offset + 1
    if first < 255:
        return ((first - 192) << 8) + data[offset + 1] + 192, offset + 2
    return int.from_bytes(data[offset + 1:offset + 5], "big"), offset + 5


def signature_issuer_identifiers(path: Path) -> set[str]:
    identifiers: set[str] = set()
    signature_packets = [payload for tag, payload in openpgp_packets(dearmor_signature(path)) if tag == 2]
    require(len(signature_packets) == 1, f"Expected exactly one OpenPGP signature packet: {path.name}")
    payload = signature_packets[0]
    if payload[0] == 3:
        require(len(payload) >= 15, f"Truncated v3 signature packet: {path.name}")
        identifiers.add(payload[7:15].hex().upper())
        return identifiers
    require(payload[0] in {4, 5}, f"Unsupported OpenPGP signature version in {path.name}: {payload[0]}")
    hashed_length = int.from_bytes(payload[4:6], "big")
    hashed = payload[6:6 + hashed_length]
    unhashed_start = 6 + hashed_length
    require(unhashed_start + 2 <= len(payload), f"Truncated signature subpacket area: {path.name}")
    unhashed_length = int.from_bytes(payload[unhashed_start:unhashed_start + 2], "big")
    unhashed = payload[unhashed_start + 2:unhashed_start + 2 + unhashed_length]
    for area in (hashed, unhashed):
        offset = 0
        while offset < len(area):
            length, body_start = subpacket_length(area, offset)
            require(length > 0 and body_start + length <= len(area), f"Malformed signature subpacket: {path.name}")
            body = area[body_start:body_start + length]
            packet_type = body[0] & 0x7F
            if packet_type == 16 and len(body) == 9:
                identifiers.add(body[1:].hex().upper())
            elif packet_type == 33 and len(body) >= 22:
                identifiers.add(body[2:].hex().upper())
            offset = body_start + length
    require(identifiers, f"Detached signature has no issuer identifier: {path.name}")
    return identifiers


def run_checked(command: list[str], label: str) -> str:
    completed = subprocess.run(
        command,
        cwd=ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    require(
        completed.returncode == 0,
        f"{label} failed ({completed.returncode}): {completed.stdout.strip()} {completed.stderr.strip()}",
    )
    return completed.stdout


def prepare_openpgp_verifier(temp_root: Path) -> tuple[str, str, list[Path]]:
    java = shutil.which("java")
    javac = shutil.which("javac")
    require(java is not None and javac is not None, "OpenJDK java/javac are required for detached-signature verification")
    require(OPENPGP_VERIFIER_SOURCE.is_file(), "OpenPGP verifier source is missing")

    libraries: list[Path] = []
    for filename, record in BOUNCY_CASTLE_LIBRARIES.items():
        path = temp_root / filename
        download(record["url"], path)
        require(path.stat().st_size == record["bytes"], f"Pinned verifier dependency length mismatch: {filename}")
        require(sha256(path) == record["sha256"], f"Pinned verifier dependency SHA-256 mismatch: {filename}")
        libraries.append(path)

    classes = temp_root / "openpgp-verifier-classes"
    classes.mkdir()
    run_checked(
        [javac, "-cp", os.pathsep.join(str(path) for path in libraries), "-d", str(classes), str(OPENPGP_VERIFIER_SOURCE)],
        "OpenPGP verifier compilation",
    )
    return java, os.pathsep.join([str(classes), *(str(path) for path in libraries)]), libraries


def parse_verifier_output(output: str) -> dict[str, str]:
    result: dict[str, str] = {}
    for line in output.splitlines():
        key, separator, value = line.partition("\t")
        require(separator == "\t" and key and key not in result, f"Malformed OpenPGP verifier output: {line!r}")
        result[key] = value
    required = {
        "verified",
        "primaryFingerprint",
        "signingFingerprint",
        "signingKeyId",
        "signatureCreatedUtc",
        "publicKeyAlgorithm",
        "hashAlgorithm",
        "primaryUserIds",
    }
    require(set(result) == required, f"Incomplete OpenPGP verifier output: {sorted(result)}")
    require(result["verified"] == "true", "Detached OpenPGP signature did not verify")
    return result


def verify_detached_signature(
    java: str,
    classpath: str,
    public_key_path: Path,
    signature_path: Path,
    artifact_path: Path,
    record: dict[str, Any],
    expected_signer_identity: str,
    label: str,
) -> None:
    output = run_checked(
        [
            java,
            "-cp",
            classpath,
            "OpenPgpDetachedSignatureVerifier",
            str(public_key_path),
            str(signature_path),
            str(artifact_path),
        ],
        f"{label} detached OpenPGP verification",
    )
    result = parse_verifier_output(output)
    require(result["primaryFingerprint"] == record["primaryKeyFingerprint"], f"{label} primary fingerprint mismatch")
    require(result["signingFingerprint"] == record["signerFingerprint"], f"{label} signing fingerprint mismatch")
    require(result["signatureCreatedUtc"] == record["signatureCreatedUtc"], f"{label} signature timestamp mismatch")
    require(result["hashAlgorithm"] == record["hashAlgorithm"], f"{label} signature hash algorithm mismatch")
    if result["primaryUserIds"]:
        require(
            expected_signer_identity in result["primaryUserIds"].split(" | "),
            f"{label} recorded signer identity is absent from the full-fingerprint key; actual UIDs: {result['primaryUserIds']}",
        )


def verify_file(path: Path, record: dict[str, Any], label: str) -> None:
    require(path.stat().st_size == record["bytes"], f"{label} byte length mismatch")
    require(sha256(path) == record["sha256"], f"{label} SHA-256 mismatch")


def license_or_notice_entries(names: list[str]) -> list[str]:
    result: list[str] = []
    for name in names:
        basename = name.rstrip("/").split("/")[-1].lower()
        if re.fullmatch(r"(?:license|notice|copying)(?:\..*)?", basename):
            result.append(name)
    return result


def native_entries(names: list[str]) -> list[str]:
    result: list[str] = []
    for name in names:
        lowered = name.lower()
        parts = lowered.split("/")
        if lowered.endswith(NATIVE_SUFFIXES) or "jni" in parts or parts[:1] == ["lib"]:
            result.append(name)
    return result


def verify_jar(path: Path, artifact: dict[str, Any]) -> None:
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
    classes = [name for name in names if name.endswith(".class")]
    natives = native_entries(names)
    legal_entries = license_or_notice_entries(names)
    jar = artifact["jar"]
    require(len(classes) == jar["classEntries"], f"{artifact['coordinate']} class-entry count mismatch")
    require(len(natives) == jar["nativeEntries"], f"{artifact['coordinate']} native-entry count mismatch: {natives}")
    require(
        len(legal_entries) == jar["embeddedLicenseOrNoticeEntries"],
        f"{artifact['coordinate']} embedded LICENSE/NOTICE count mismatch: {legal_entries}",
    )
    if artifact["coordinate"] == "com.google.crypto.tink:tink-android:1.23.0":
        shaded = [name for name in names if name.startswith("com/google/crypto/tink/shaded/protobuf/")]
        shaded_classes = [name for name in shaded if name.endswith(".class")]
        require(len(shaded) == 540, "Tink shaded protobuf entry count mismatch")
        require(len(shaded_classes) == 539, "Tink shaded protobuf class count mismatch")


def scan_class_jsr305_metadata(data: bytes) -> tuple[list[str], list[str], list[str], list[str]]:
    require(data[:4] == b"\xca\xfe\xba\xbe", "Malformed Java class magic")
    position = 8

    def read_u2() -> int:
        nonlocal position
        value = struct.unpack_from(">H", data, position)[0]
        position += 2
        return value

    def read_u4() -> int:
        nonlocal position
        value = struct.unpack_from(">I", data, position)[0]
        position += 4
        return value

    constant_pool_count = read_u2()
    values: list[Any] = [None] * constant_pool_count
    tags = [0] * constant_pool_count
    index = 1
    while index < constant_pool_count:
        tag = data[position]
        position += 1
        tags[index] = tag
        if tag == 1:
            length = read_u2()
            values[index] = data[position:position + length].decode("utf-8", errors="replace")
            position += length
        elif tag in {3, 4}:
            position += 4
        elif tag in {5, 6}:
            position += 8
            index += 1
        elif tag in {7, 8, 16, 19, 20}:
            values[index] = read_u2()
        elif tag in {9, 10, 11, 12, 17, 18}:
            values[index] = (read_u2(), read_u2())
        elif tag == 15:
            values[index] = (data[position], struct.unpack_from(">H", data, position + 1)[0])
            position += 3
        else:
            raise ValueError(f"Unsupported Java class constant-pool tag: {tag}")
        index += 1

    annotation_descriptors = [
        value
        for value in values
        if isinstance(value, str) and "javax/annotation" in value
    ]
    class_references = [
        values[values[index]]
        for index, tag in enumerate(tags)
        if tag == 7
        and isinstance(values[index], int)
        and "javax/annotation" in values[values[index]]
    ]
    symbolic_descriptors: list[str] = []
    for index, tag in enumerate(tags):
        if tag == 12 and isinstance(values[index], tuple):
            descriptor_index = values[index][1]
            if "javax/annotation" in values[descriptor_index]:
                symbolic_descriptors.append(values[descriptor_index])
        elif tag == 16 and isinstance(values[index], int):
            if "javax/annotation" in values[values[index]]:
                symbolic_descriptors.append(values[values[index]])

    position += 6  # access_flags, this_class, super_class
    interface_count = read_u2()
    position += 2 * interface_count
    definition_descriptors: list[str] = []
    for _ in range(2):  # fields, methods
        member_count = read_u2()
        for _ in range(member_count):
            position += 2  # access_flags
            read_u2()  # name_index
            descriptor = values[read_u2()]
            if "javax/annotation" in descriptor:
                definition_descriptors.append(descriptor)
            attribute_count = read_u2()
            for _ in range(attribute_count):
                read_u2()  # attribute_name_index
                attribute_length = read_u4()
                position += attribute_length

    return annotation_descriptors, class_references, definition_descriptors, symbolic_descriptors


def verify_tink_jsr305_bytecode(path: Path, analysis: dict[str, Any]) -> None:
    evidence = analysis["bytecodeAndSourceEvidence"]
    expected_descriptors = {
        "Ljavax/annotation/Nullable;": "javax.annotation.Nullable",
        "Ljavax/annotation/concurrent/GuardedBy;": "javax.annotation.concurrent.GuardedBy",
        "Ljavax/annotation/concurrent/ThreadSafe;": "javax.annotation.concurrent.ThreadSafe",
    }
    classes: list[str] = []
    descriptor_classes: dict[str, list[str]] = {name: [] for name in expected_descriptors.values()}
    class_references: list[tuple[str, str]] = []
    definition_descriptors: list[tuple[str, str]] = []
    symbolic_descriptors: list[tuple[str, str]] = []
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        for name in names:
            if not name.endswith(".class"):
                continue
            descriptors, class_refs, definition_refs, symbolic_refs = scan_class_jsr305_metadata(archive.read(name))
            require(set(descriptors).issubset(expected_descriptors), f"Unexpected JSR305 descriptor in {name}: {descriptors}")
            if not descriptors:
                continue
            class_name = name.removesuffix(".class").replace("/", ".")
            classes.append(class_name)
            for descriptor in descriptors:
                descriptor_classes[expected_descriptors[descriptor]].append(class_name)
            class_references.extend((class_name, value) for value in class_refs)
            definition_descriptors.extend((class_name, value) for value in definition_refs)
            symbolic_descriptors.extend((class_name, value) for value in symbolic_refs)

        consumer_rule_files = sorted(
            name
            for name in names
            if name.startswith("META-INF/proguard/") and not name.endswith("/")
        )
        require(consumer_rule_files == evidence["tinkConsumerRules"]["files"], "Tink consumer-rule file set drift")
        for name in consumer_rule_files:
            content = archive.read(name).lower()
            require(b"jsr305" not in content and b"javax.annotation" not in content and b"javax/annotation" not in content, f"Tink consumer rule references JSR305: {name}")

    classes.sort()
    expected_classes = JSR305_CLASS_LIST_PATH.read_text(encoding="utf-8").splitlines()
    require(classes == expected_classes, "Tink JSR305 descriptor class list drift")
    require(len(classes) == evidence["classesContainingAnyJsr305Descriptor"] == 182, "Tink JSR305 descriptor class count drift")
    require(
        {name: len(values) for name, values in descriptor_classes.items()}
        == evidence["descriptorPresenceByClassConstantPool"],
        "Tink JSR305 descriptor-type counts drift",
    )
    require(len(class_references) == evidence["constantClassReferencesToJsr305"] == 0, f"Tink JSR305 CONSTANT_Class references found: {class_references}")
    require(len(definition_descriptors) == evidence["fieldOrMethodDefinitionDescriptorsToJsr305"] == 0, f"Tink JSR305 field/method descriptors found: {definition_descriptors}")
    require(len(symbolic_descriptors) == evidence["symbolicNameAndTypeOrMethodTypeDescriptorsToJsr305"] == 0, f"Tink JSR305 symbolic member descriptors found: {symbolic_descriptors}")


def verify_tink_jsr305_sources(path: Path, analysis: dict[str, Any]) -> None:
    evidence = analysis["bytecodeAndSourceEvidence"]
    imports = {
        "javax.annotation.Nullable": set(),
        "javax.annotation.concurrent.GuardedBy": set(),
        "javax.annotation.concurrent.ThreadSafe": set(),
    }
    class_for_name_targets: list[str] = []
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            if not name.endswith(".java"):
                continue
            source = archive.read(name).decode("utf-8", errors="replace")
            for annotation in imports:
                if f"import {annotation};" in source:
                    imports[annotation].add(name)
            class_for_name_targets.extend(re.findall(r'Class\.forName\("([^"]+)"\)', source))
            require('Class.forName("javax.annotation' not in source, f"Tink source reflectively loads JSR305: {name}")
            require(not re.search(r"get(?:Declared)?Annotation\s*\([^)]*javax\.annotation", source), f"Tink source reflectively queries JSR305: {name}")

    require({name: len(files) for name, files in imports.items()} == evidence["sourceImportFileCounts"], "Tink JSR305 source import counts drift")
    require(len(set().union(*imports.values())) == evidence["sourceFilesImportingAnyJsr305Type"] == 157, "Tink JSR305 source import union drift")
    require(len(class_for_name_targets) == evidence["tinkClassForNameCallSites"] == 2, "Tink Class.forName call-site count drift")
    require(sorted(set(class_for_name_targets)) == evidence["tinkClassForNameCalls"] == ["org.conscrypt.Conscrypt"], "Tink Class.forName target drift")


def pom_properties(root: ET.Element) -> dict[str, str]:
    properties = root.find("m:properties", MAVEN_NS)
    if properties is None:
        return {}
    return {child.tag.split("}", 1)[-1]: (child.text or "").strip() for child in properties}


def resolve_version(raw: str, properties: dict[str, str]) -> str:
    match = re.fullmatch(r"\$\{([^}]+)\}", raw)
    if not match:
        return raw
    key = match.group(1)
    require(key in properties, f"Unresolved POM version property: {key}")
    return properties[key]


def parse_project_dependencies(path: Path) -> set[tuple[str, str]]:
    root = ET.parse(path).getroot()
    properties = pom_properties(root)
    dependencies: set[tuple[str, str]] = set()
    project_dependencies = root.find("m:dependencies", MAVEN_NS)
    if project_dependencies is None:
        return dependencies
    for dependency in project_dependencies.findall("m:dependency", MAVEN_NS):
        group = (dependency.findtext("m:groupId", default="", namespaces=MAVEN_NS)).strip()
        artifact = (dependency.findtext("m:artifactId", default="", namespaces=MAVEN_NS)).strip()
        version_raw = (dependency.findtext("m:version", default="", namespaces=MAVEN_NS)).strip()
        scope = (dependency.findtext("m:scope", default="compile-default", namespaces=MAVEN_NS)).strip()
        optional = (dependency.findtext("m:optional", default="false", namespaces=MAVEN_NS)).strip().lower()
        if scope in {"test", "provided", "system"} or optional == "true":
            continue
        require(group and artifact and version_raw, f"Incomplete runtime POM dependency in {path.name}")
        dependencies.add((f"{group}:{artifact}:{resolve_version(version_raw, properties)}", scope))
    return dependencies


def parse_pom_license(path: Path) -> tuple[str, str]:
    root = ET.parse(path).getroot()
    licenses = root.find("m:licenses", MAVEN_NS)
    require(licenses is not None, f"POM has no licenses element: {path.name}")
    entries = licenses.findall("m:license", MAVEN_NS)
    require(len(entries) == 1, f"Expected one POM license entry in {path.name}")
    name = (entries[0].findtext("m:name", default="", namespaces=MAVEN_NS)).strip()
    url = (entries[0].findtext("m:url", default="", namespaces=MAVEN_NS)).strip()
    require(name and url, f"Incomplete POM license entry in {path.name}")
    return name, url


def _validate_static_v03_superseded(
    inventory: dict[str, Any],
    license_notice: dict[str, Any],
    authenticity: dict[str, Any],
    jsr305_exclusion: dict[str, Any],
    readiness: dict[str, Any],
    review_roles: dict[str, Any],
) -> None:
    require(inventory["schemaVersion"] == 3, "Superseded v0.3 dependency inventory schema drift")
    require(inventory["rootCoordinate"] == "com.google.crypto.tink:tink-android:1.23.0", "Root coordinate drift")
    require(inventory["dependencyAdmission"] is False, "Inventory must not admit the dependency")
    require(inventory["runtimeGraphModified"] is False, "Inventory must not claim a runtime graph")
    require(inventory["prospectiveExclusionAnalysis"] == {
        "locator": "docs/evidence/poc-recovery-001/jsr305-exclusion-analysis-2026-08-12.json",
        "status": "TECHNICAL_EXCLUSION_PROVEN_WITH_NARROW_R8_RULE_OWNER_PRODUCT_IP_POLICY_APPROVED",
        "publishedClosureStillIncludesJsr305": True,
        "futureProjectGraphClaimed": False,
        "recommendedTreatment": "A_CONDITIONED_COMPLETE_EXCLUSION",
        "requiredFutureResolvedJsr305ComponentCount": 0,
        "productIpAccepted": True,
        "acceptedBy": "Project owner",
        "acceptedOn": "2026-08-12",
        "acceptanceScope": "PROSPECTIVE_POLICY_AND_REVIEWED_GOVERNANCE_PACKAGE_ONLY",
    }, "Dependency inventory prospective JSR305 boundary drift")
    require(len(inventory["artifacts"]) == 8, "Expected eight published external coordinates")
    require(len(inventory["graphEdges"]) == 8, "Expected eight publisher dependency edges")
    require(all(artifact["jar"]["nativeEntries"] == 0 for artifact in inventory["artifacts"]), "Native entry recorded")
    require(jsr305_exclusion["schemaVersion"] == 2 and jsr305_exclusion["pocId"] == "POC-RECOVERY-001", "JSR305 exclusion evidence identity drift")
    require(jsr305_exclusion["status"] == "TECHNICAL_EXCLUSION_PROVEN_WITH_NARROW_R8_RULE_OWNER_PRODUCT_IP_POLICY_APPROVED", "JSR305 exclusion evidence/current policy status drift")
    upstream = jsr305_exclusion["upstreamDependencyReason"]
    require(upstream["directFromRootPom"] is True and upstream["viaCoordinate"] is None and upstream["mavenScope"] == "compile-default" and upstream["optional"] is False, "JSR305 direct compile/non-optional edge evidence drift")
    bytecode = jsr305_exclusion["bytecodeAndSourceEvidence"]
    require(bytecode["classesContainingAnyJsr305Descriptor"] == 182 and bytecode["constantClassReferencesToJsr305"] == 0 and bytecode["fieldOrMethodDefinitionDescriptorsToJsr305"] == 0 and bytecode["symbolicNameAndTypeOrMethodTypeDescriptorsToJsr305"] == 0 and bytecode["tinkReflectionOrClassForNameCallsTargetingJsr305"] == 0, "JSR305 annotation-only bytecode evidence drift")
    require(JSR305_CLASS_LIST_PATH.is_file() and sha256(JSR305_CLASS_LIST_PATH) == bytecode["classListSha256"] == "325211cef459ba96a3c5721e5d754bff3464d15461e0c9f6da4a66a1f7ee2045", "JSR305 exact class-list evidence drift")
    class_names = JSR305_CLASS_LIST_PATH.read_text(encoding="utf-8").splitlines()
    require(len(class_names) == 182 and len(set(class_names)) == 182 and class_names == sorted(class_names), "JSR305 exact class list is incomplete or unsorted")
    probes = jsr305_exclusion["nonRepositoryProbes"]
    exact_rules = [
        "-dontwarn javax.annotation.Nullable",
        "-dontwarn javax.annotation.concurrent.GuardedBy",
        "-dontwarn javax.annotation.concurrent.ThreadSafe",
    ]
    require(probes["kotlinConsumerCompile"]["exitCode"] == 0 and probes["jvmLoadAndReflection"]["failures"] == 0 and probes["d8"]["exitCode"] == 0, "JSR305 no-artifact compile/load/D8 probe drift")
    require(probes["r8BareExclusion"]["exitCode"] == 1 and probes["r8NarrowRule"]["exitCode"] == 0 and probes["r8NarrowRule"]["rules"] == exact_rules, "JSR305 R8 conditioned-exclusion evidence drift")
    policy = jsr305_exclusion["prospectivePolicy"]
    require(policy["policyId"] == "REC-JSR305-EXCLUDE-001" and policy["status"] == "APPROVED_PROSPECTIVE_POLICY_ONLY" and policy["forbiddenResolvedCoordinate"] == "com.google.code.findbugs:jsr305:3.0.2" and policy["requiredResolvedComponentCount"] == 0, "JSR305 zero-component policy drift")
    require(policy["acceptedBy"] == "Project owner" and policy["acceptedOn"] == "2026-08-12" and policy["decisionInputGovernanceHead"] == OWNER_DECISION_INPUT_HEAD, "JSR305 owner policy acceptance record drift")
    require(policy["compileOnlyOrAlternatePathToForbiddenCoordinateAllowed"] is False and policy["requiredR8Rules"] == exact_rules and policy["futureResolvedGraphReportPresent"] is False, "JSR305 prospective policy weakened")
    license_disposition = jsr305_exclusion["licenseDisposition"]
    require(license_disposition["underlyingArtifactConflictResolved"] is False and license_disposition["stage0ProductIpFinalApproval"] is False, "JSR305 evidence overclaims actual graph/Product-IP closure")
    require(license_disposition["prospectivePolicyApproved"] is True and license_disposition["prospectivePolicyApprovedBy"] == "Project owner" and license_disposition["jsr305UseOrDistributionApproved"] is False and license_disposition["ownerDecisionRequired"] is False, "JSR305 scoped owner policy/license boundary drift")
    current_policy = readiness["dependencyExclusionPolicy"]
    require(readiness["schemaVersion"] == 4 and readiness["executionAllowed"] is False, "Current owner disposition/readiness boundary drift")
    require(current_policy["policyId"] == policy["policyId"] == "REC-JSR305-EXCLUDE-001", "Current JSR305 owner policy identity drift")
    require(current_policy["status"] == "APPROVED_PROSPECTIVE_POLICY_ONLY" and current_policy["productIpAccepted"] is True, "Owner/Product-IP prospective JSR305 policy approval is absent")
    require(current_policy["acceptedBy"] == "Project owner" and current_policy["acceptedOn"] == "2026-08-12", "Owner/Product-IP prospective policy identity/date drift")
    require(current_policy["rootCoordinate"] == policy["rootCoordinate"] == "com.google.crypto.tink:tink-android:1.23.0", "Current policy root coordinate drift")
    require(current_policy["forbiddenResolvedCoordinate"] == policy["forbiddenResolvedCoordinate"] == "com.google.code.findbugs:jsr305:3.0.2", "Current policy excluded coordinate drift")
    require(current_policy["requiredResolvedComponentCount"] == 0 and current_policy["compileOnlyOrAlternatePathAllowed"] is False, "Current policy no longer requires complete exclusion")
    require(current_policy["coveredConfigurationFamilies"] == ["compile", "runtime", "benchmark", "test", "packaging"], "Current policy Gradle configuration coverage drift")
    require(current_policy["requiredR8Rules"] == exact_rules and current_policy["broaderDontwarnAllowed"] is False, "Current policy exact R8 rules were weakened")
    require(current_policy["unresolvedR8MissingClassesAllowed"] is False and current_policy["independentModifierResolutionRequired"] is True, "Current policy release R8/Modifier boundary drift")
    require(current_policy["recurrenceBlocksImplementationVerificationAndExecution"] is True, "Current policy is not fail-closed on JSR305 recurrence")
    require(current_policy["underlyingArtifactLicenseConflictResolved"] is False and current_policy["jsr305UseOrDistributionApproved"] is False, "Current policy overclaims JSR305 license/use approval")
    require(current_policy["futureResolvedGraphReportPresent"] is False and not (ROOT / current_policy["futureResolvedGraphReportLocator"]).exists(), "Future resolved graph appeared without separate implementation scope")
    require(review_roles["schemaVersion"] == 4, "Current review-role schema drift")
    product_ip = review_roles["roles"]["stage0ProductIp"]
    disposition = product_ip["approvedProspectiveDisposition"]
    require(product_ip["status"] == "PROSPECTIVE_POLICY_AND_REVIEWED_GOVERNANCE_PACKAGE_APPROVED_ONLY" and product_ip["finalApproved"] is False, "Scoped Product/IP role disposition drift")
    require(disposition["accepted"] is True and disposition["acceptedBy"] == "Project owner" and disposition["acceptedOn"] == "2026-08-12", "Scoped Product/IP acceptance record incomplete")
    require(disposition["decisionInputGovernanceHead"] == OWNER_DECISION_INPUT_HEAD and disposition["conflictingArtifactTreatment"] == "EXCLUDED_TERMS_NOT_INTERPRETED" and disposition["jsr305UseOrDistributionApproved"] is False, "Scoped Product/IP conflict treatment drift")
    owner_decision = OWNER_DECISION_PATH.read_text(encoding="utf-8")
    require(OWNER_DECISION_INPUT_HEAD in owner_decision and all(rule in owner_decision for rule in exact_rules), "Owner decision lacks exact input HEAD or narrow R8 rules")
    require(all(family in owner_decision for family in ("compile", "runtime", "benchmark", "test", "packaging")) and "fails closed" in owner_decision, "Owner decision lacks configuration coverage or fail-closed recurrence rule")
    require("does not approve use or distribution of JSR-305" in owner_decision, "Owner decision overclaims JSR305 use/distribution approval")
    require(inventory["inventoryStatus"] == "VERIFIED_PUBLISHER_CLOSURE_AUTHENTICITY_VERIFIED_LICENSE_CONFLICT_PACKAGE_REVIEW_ONLY", "Inventory authenticity status drift")
    require(inventory["authenticityEvidence"]["locator"] == "docs/evidence/poc-recovery-001/dependency-ip-authenticity-v0.3.json", "Inventory authenticity locator drift")
    require(license_notice["schemaVersion"] == 3 and license_notice["reviewStatus"] == "AUTHENTICITY_VERIFIED_LICENSE_CONFLICT_PRODUCT_IP_APPROVAL_BLOCKED", "License review status drift")
    require(license_notice["evaluationApproved"] is False, "Evaluation must not be approved yet")
    require(license_notice["approvedReviewer"] is None and license_notice["approvedOn"] is None, "Product/IP approval identity/date must remain null")
    require(license_notice["summary"]["externalCoordinatesAuthenticityVerified"] == 8, "License inventory authenticity count drift")
    require(license_notice["summary"]["externalCoordinatesWithAuthenticityPending"] == 0, "License inventory retains an authenticity-pending coordinate")
    require(license_notice["summary"]["unresolvedLicenseConflicts"] == ["com.google.code.findbugs:jsr305:3.0.2"], "Exact license conflict drift")

    require(authenticity["schemaVersion"] == 2 and authenticity["pocId"] == "POC-RECOVERY-001", "Authenticity evidence identity drift")
    require(authenticity["overallStatus"] == "AUTHENTICITY_VERIFIED_LICENSE_CONFLICT_PRODUCT_IP_APPROVAL_BLOCKED", "Authenticity/license state drift")
    require(authenticity["summary"]["coordinates"] == 8 and len(authenticity["components"]) == 8, "Authenticity coordinate count drift")
    require(authenticity["summary"]["publisherChecksumsMatched"] == 16, "Publisher checksum count drift")
    require(authenticity["summary"]["detachedSignaturesCryptographicallyVerified"] == 16, "Detached-signature verification count drift")
    require(authenticity["summary"]["coordinatesWithUpstreamSignerTrustConfirmed"] == 2, "Publisher-bound coordinate count drift")
    require(authenticity["summary"]["coordinatesWithExactSourceCorrespondenceConfirmed"] == 6, "Source-correspondence coordinate count drift")
    require(authenticity["summary"]["coordinatesAuthenticityVerified"] == 8 and authenticity["summary"]["coordinatesAuthenticityPending"] == 0, "Authenticity closure counts drift")
    require(authenticity["summary"]["coordinatesWithLicenseConflict"] == 1, "License conflict count drift")
    require(authenticity["verificationRecordLocator"] == "docs/evidence/poc-recovery-001/dependency-ip-authenticity-verification-2026-08-12.md", "Verification record locator drift")
    require((ROOT / authenticity["verificationRecordLocator"]).is_file(), "Verification record is missing")
    require(authenticity["approvalBoundary"]["productIpApprovalAllowedWhileAnyCoordinatePending"] is False, "Product/IP approval must fail closed on pending authenticity")
    require(authenticity["approvalBoundary"]["productIpApprovalAllowedWhileLicenseConflict"] is False, "Product/IP approval must fail closed on license conflict")
    require(authenticity["approvalBoundary"]["productIpFinalApproval"] is False and authenticity["approvalBoundary"]["approvedReviewer"] is None and authenticity["approvalBoundary"]["approvedOn"] is None, "Authenticity evidence prematurely approves Product/IP")
    require(authenticity["approvalBoundary"]["dependencyAdmission"] is False and authenticity["approvalBoundary"]["productionAdmission"] is False, "Authenticity evidence admitted a dependency or production use")

    artifacts_by_coordinate = {artifact["coordinate"]: artifact for artifact in inventory["artifacts"]}
    license_by_coordinate = {
        component["coordinate"]: component
        for component in license_notice["components"]
        if not component["coordinate"].startswith("embedded:")
    }
    authentic_by_coordinate = {component["coordinate"]: component for component in authenticity["components"]}
    require(set(artifacts_by_coordinate) == set(license_by_coordinate) == set(authentic_by_coordinate), "Per-coordinate evidence coverage drift")
    for coordinate, component in authentic_by_coordinate.items():
        artifact = artifacts_by_coordinate[coordinate]
        license_component = license_by_coordinate[coordinate]
        if coordinate == "com.google.code.findbugs:jsr305:3.0.2":
            require(component["licenseId"] == license_component["licenseSpdx"] == "NOASSERTION", "JSR305 must retain the unresolved license conflict")
            require(component["licenseEvidence"]["status"] == "LICENSE_CONFLICT_PRODUCT_IP_DECISION_REQUIRED", "JSR305 conflict evidence drift")
            require(license_component["publishedPomLicenseSpdx"] == "Apache-2.0" and license_component["exactReleaseSourceLicenseSpdx"] == "BSD-3-Clause", "JSR305 Apache/BSD conflict drift")
        else:
            require(component["licenseId"] == license_component["licenseSpdx"] == "Apache-2.0", f"{coordinate} license identifier drift")
        require(component["upstreamLicenseTextLocator"].startswith("https://") and re.fullmatch(r"[0-9a-f]{64}", component["licenseTextSha256"]), f"{coordinate} upstream license evidence incomplete")
        require(component["copyrightEvidence"]["locator"].startswith("https://") and component["copyrightEvidence"]["status"], f"{coordinate} copyright evidence incomplete")
        require(component["notice"]["requirement"] and component["notice"]["locator"].startswith("https://") and component["notice"]["result"], f"{coordinate} NOTICE evidence incomplete")
        require(component["verifiedAtUtc"] == authenticity["verifiedAtUtc"] and component["verificationTool"] == authenticity["verificationTool"], f"{coordinate} verification timestamp/tool attribution drift")
        key = component["openPgpKey"]
        require(re.fullmatch(r"[0-9A-F]{40}", key["primaryFingerprint"]) and re.fullmatch(r"[0-9A-F]{40}", key["signingFingerprint"]), f"{coordinate} full OpenPGP fingerprint evidence incomplete")
        require(key["keyMaterialLocator"].startswith("https://") and key["identitySource"].startswith("https://"), f"{coordinate} OpenPGP key/identity source missing")
        for kind in ("jar", "pom"):
            item = component[kind]
            require(item["sha256"] == artifact[kind]["sha256"], f"{coordinate} {kind} SHA-256 evidence drift")
            require(item["publisherChecksum"]["result"] == "MATCH" and item["publisherChecksum"]["locator"].startswith("https://"), f"{coordinate} {kind} publisher checksum missing")
            require(item["detachedSignature"]["result"] == "CRYPTOGRAPHICALLY_VERIFIED" and item["detachedSignature"]["locator"].startswith("https://"), f"{coordinate} {kind} detached signature missing")
            require(item["detachedSignature"]["signerFingerprint"] == key["signingFingerprint"], f"{coordinate} {kind} signing fingerprint drift")
            require(item["detachedSignature"]["primaryKeyFingerprint"] == key["primaryFingerprint"], f"{coordinate} {kind} primary fingerprint drift")
            require(item["detachedSignature"]["signatureCreatedUtc"].endswith("Z") and item["detachedSignature"]["hashAlgorithm"], f"{coordinate} {kind} signature metadata incomplete")
        require(component["jar"]["detachedSignature"]["signerFingerprint"] == component["pom"]["detachedSignature"]["signerFingerprint"], f"{coordinate} JAR/POM signer mismatch")
        require(component["signerIdentity"] and component["signerTrustStatus"] and component["sourceCorrespondence"]["status"], f"{coordinate} signer/source evidence incomplete")
        require(component["authenticityStatus"] in AUTHENTICITY_STATUSES, f"{coordinate} uses an invalid authenticity classification")
        require(component["authenticityStatus"] not in {"AUTHENTICITY_PENDING", "AUTHENTICITY_REJECTED"} and component["closurePath"] is None, f"{coordinate} authenticity is not closed")
        if component["authenticityStatus"] == "AUTHENTICITY_VERIFIED_MULTISOURCE_CORRESPONDENCE":
            source = component["sourceCorrespondence"]
            comparison = source["comparison"]
            require(source["repository"].startswith("https://") and source["commit"] and source["sourceJar"]["signatureResult"] == "CRYPTOGRAPHICALLY_VERIFIED", f"{coordinate} source evidence incomplete")
            require(re.fullmatch(r"[0-9a-f]{64}", source["sourceJar"]["sha256"]) and source["sourceJar"]["bytes"] > 0, f"{coordinate} source JAR identity incomplete")
            require(comparison["exactCommitBlobMatches"] + comparison["declaredGeneratedEntries"] == comparison["sourceEntries"] and comparison["unexplainedEntries"] == 0, f"{coordinate} source comparison has an unexplained entry")
            require(source["reproducibleBuild"]["byteForByteBinaryRebuildAttempted"] is False and source["reproducibleBuild"]["status"] == "NOT_CLAIMED", f"{coordinate} reproducibility limitation drift")
        else:
            require(component["authenticityStatus"] == "AUTHENTICITY_VERIFIED_PUBLISHER_BOUND_SIGNATURE", f"{coordinate} unexpected verified classification")
            require(component["signerTrustSource"].startswith("https://"), f"{coordinate} publisher signer binding is missing")
    require(sum(component["authenticityStatus"] == "AUTHENTICITY_VERIFIED_MULTISOURCE_CORRESPONDENCE" for component in authentic_by_coordinate.values()) == 6, "Exactly six coordinates must use multisource correspondence")
    require(sum(component["authenticityStatus"] == "AUTHENTICITY_VERIFIED_PUBLISHER_BOUND_SIGNATURE" for component in authentic_by_coordinate.values()) == 2, "Exactly two coordinates must use publisher-bound signatures")


def validate_static(
    inventory: dict[str, Any],
    license_notice: dict[str, Any],
    authenticity: dict[str, Any],
    jsr305_exclusion: dict[str, Any],
    readiness: dict[str, Any],
    review_roles: dict[str, Any],
) -> None:
    """Validate the active v0.6 packet and its exact recovery-only boundary."""

    require(inventory["schemaVersion"] == 4, "Dependency inventory schema drift")
    require(inventory["rootCoordinate"] == "com.google.crypto.tink:tink-android:1.23.0", "Root coordinate drift")
    require(inventory["dependencyAdmission"] is False and inventory["runtimeGraphModified"] is False, "Inventory admitted a runtime graph")
    require(len(inventory["artifacts"]) == 8 and len(inventory["graphEdges"]) == 8, "Published closure count drift")
    require(all(artifact["jar"]["nativeEntries"] == 0 for artifact in inventory["artifacts"]), "Native entry recorded")
    current_i1 = inventory["currentImplementationState"]
    require(
        current_i1["authorizationId"] == AUTHORIZATION_ID
        and current_i1["taskScopedImplementationAuthorized"] is True
        and current_i1["recoveryModuleExists"] is True
        and current_i1["module"] == ":poc:recovery"
        and current_i1["moduleKind"] == "PURE_NON_METRIC_NO_RUNTIME_CRYPTO"
        and current_i1["tinkAndroid123Wired"] is False
        and current_i1["runtimeCryptoDependencyAdded"] is False
        and current_i1["newExternalDependencyCoordinateAdded"] is False
        and current_i1["moduleLockfilePresent"] is RECOVERY_LOCK_PATH.is_file()
        and current_i1["actualRecoveryRuntimeGraphReviewed"] is False
        and current_i1["dependencyAdmission"] is False
        and current_i1["productionAdmission"] is False,
        "Current REC-I1 module/dependency state drift",
    )
    validate_authorization_record(read_json(ROOT / AUTHORIZATION_PATH))
    require(RECOVERY_BUILD_PATH.is_file(), "Authorized REC-I1 module build file is missing")
    validate_recovery_build_text(RECOVERY_BUILD_PATH.read_text(encoding="utf-8"))
    settings = (ROOT / "android" / "settings.gradle.kts").read_text(encoding="utf-8")
    require(settings.count('include(":poc:recovery")') == 1, "REC-I1 module include drift")
    if RECOVERY_LOCK_PATH.is_file():
        recovery_lock = RECOVERY_LOCK_PATH.read_text(encoding="utf-8").lower()
        require("tink" not in recovery_lock and "jsr305" not in recovery_lock, "Forbidden dependency in REC-I1 lockfile")

    boundary = inventory["recoveryBoundary"]
    require(
        boundary["boundaryId"] == "REC-JSR305-EXCLUDE-001"
        and boundary["historicalSnapshot"] is True
        and boundary["snapshotAssessedOn"] == "2026-08-12"
        and boundary["currentTinkAndroid123Wired"] is False
        and boundary["currentRecoveryModuleExists"] is False
        and boundary["repositoryWideAbsenceClaimed"] is False
        and boundary["baseOtherModuleToolingOccurrencesAreRecoveryAdmissionEvidence"] is False
        and boundary["futureActualRecoveryGraphStatus"].startswith("OPEN_BLOCKED"),
        "Dependency inventory recovery-only boundary drift",
    )
    require((ROOT / boundary["baseInventoryLocator"]).is_file(), "Base lockfile/tooling inventory missing")

    require(jsr305_exclusion["schemaVersion"] == 3 and jsr305_exclusion["pocId"] == "POC-RECOVERY-001", "JSR305 analysis identity drift")
    current = jsr305_exclusion["currentRepositoryBoundary"]
    require(
        current["boundaryId"] == "REC-JSR305-EXCLUDE-001"
        and current["tinkAndroid123Wired"] is False
        and current["recoveryModuleExists"] is False
        and current["repositoryWideAbsenceClaimed"] is False
        and current["pullRequest11ChangedLockfiles"] is False
        and current["baseOtherModuleToolingOccurrencesAreRecoveryAdmissionEvidence"] is False,
        "JSR305 current-repository boundary overclaim",
    )
    policy = jsr305_exclusion["prospectivePolicy"]
    require(
        policy["policyId"] == "REC-JSR305-EXCLUDE-001"
        and policy["status"] == "APPROVED_PROSPECTIVE_POLICY_ONLY"
        and policy["rootCoordinate"] == "com.google.crypto.tink:tink-android:1.23.0"
        and policy["forbiddenResolvedCoordinate"] == "com.google.code.findbugs:jsr305:3.0.2"
        and policy["coveredFutureModule"] == ":poc:recovery"
        and policy["requiredResolvedComponentCount"] == 0
        and policy["compileOnlyOrAlternatePathToForbiddenCoordinateAllowed"] is False
        and policy["requiredR8Rules"] == R8_RULES
        and policy["broaderJavaxAnnotationDontwarnAllowed"] is False
        and policy["futureResolvedGraphReportPresent"] is False,
        "JSR305 prospective exclusion policy drift",
    )
    require(policy["excludedCurrentInputsAreRecoveryAdmissionEvidence"] is False, "Existing tooling paths were treated as recovery admission")
    require(not (ROOT / policy["futureResolvedGraphReportLocator"]).exists(), "Future resolved graph appeared before authorized implementation")
    bytecode = jsr305_exclusion["bytecodeAndSourceEvidence"]
    require(bytecode["classesContainingAnyJsr305Descriptor"] == 182, "Tink JSR305 descriptor class count drift")
    require(JSR305_CLASS_LIST_PATH.is_file() and sha256(JSR305_CLASS_LIST_PATH) == bytecode["classListSha256"], "JSR305 class-list hash drift")
    require(
        bytecode["constantClassReferencesToJsr305"] == 0
        and bytecode["fieldOrMethodDefinitionDescriptorsToJsr305"] == 0
        and bytecode["symbolicNameAndTypeOrMethodTypeDescriptorsToJsr305"] == 0
        and bytecode["tinkReflectionOrClassForNameCallsTargetingJsr305"] == 0,
        "JSR305 is no longer annotation-metadata-only",
    )
    probes = jsr305_exclusion["nonRepositoryProbes"]
    require(probes["r8BareExclusion"]["exitCode"] == 1, "Bare exclusion unexpectedly accepted")
    require(probes["r8NarrowRule"]["exitCode"] == 0 and probes["r8NarrowRule"]["rules"] == R8_RULES, "Exact narrow R8 proof drift")
    states = jsr305_exclusion["licenseDisposition"]
    require(
        states["stateAProspectivePolicy"] == "CLOSED_APPROVED"
        and states["stateBGovernanceAuthenticityLicenseEvidence"].startswith("CLOSED_VERIFIED")
        and states["stateCFutureActualGraphProductIpDisposition"].startswith("OPEN_BLOCKED")
        and states["underlyingArtifactConflictResolved"] is False
        and states["jsr305UseOrDistributionApproved"] is False,
        "JSR305 three-state license disposition drift",
    )

    require(authenticity["schemaVersion"] == 3 and authenticity["pocId"] == "POC-RECOVERY-001", "Authenticity evidence identity drift")
    require(
        authenticity["overallStatus"]
        == "EXACT_GOVERNANCE_PACKET_AUTHENTICITY_LICENSE_NOTICE_VERIFIED_FUTURE_ACTUAL_GRAPH_PRODUCT_IP_BLOCKED",
        "Authenticity packet/actual-graph state drift",
    )
    require(authenticity["summary"]["coordinates"] == 8 and len(authenticity["components"]) == 8, "Authenticity coordinate count drift")
    require(authenticity["summary"]["publisherChecksumsMatched"] == 16, "Publisher checksum count drift")
    require(authenticity["summary"]["detachedSignaturesCryptographicallyVerified"] == 16, "Detached signature count drift")
    require(authenticity["summary"]["coordinatesAuthenticityVerified"] == 8 and authenticity["summary"]["coordinatesAuthenticityPending"] == 0, "Authenticity closure count drift")
    approval = authenticity["approvalBoundary"]
    require(
        approval["prospectivePolicyStatus"] == "CLOSED_APPROVED"
        and approval["governancePacketEvidenceStatus"] == "CLOSED_VERIFIED"
        and approval["futureActualGraphProductIpDisposition"] == "OPEN_BLOCKED"
        and approval["productIpFinalApproval"] is False
        and approval["jsr305UseOrDistributionApproved"] is False
        and approval["dependencyAdmission"] is False
        and approval["productionAdmission"] is False,
        "Authenticity approval boundary drift",
    )

    artifacts_by_coordinate = {artifact["coordinate"]: artifact for artifact in inventory["artifacts"]}
    authentic_by_coordinate = {component["coordinate"]: component for component in authenticity["components"]}
    require(set(artifacts_by_coordinate) == set(authentic_by_coordinate), "Authenticity coverage drift")
    for coordinate, component in authentic_by_coordinate.items():
        artifact = artifacts_by_coordinate[coordinate]
        require(component["authenticityStatus"] in AUTHENTICITY_STATUSES - {"AUTHENTICITY_PENDING", "AUTHENTICITY_REJECTED"}, f"{coordinate} authenticity is not verified")
        require(component["jar"]["sha256"] == artifact["jar"]["sha256"] and component["pom"]["sha256"] == artifact["pom"]["sha256"], f"{coordinate} inventory/authenticity hash drift")
        for kind in ("jar", "pom"):
            require(component[kind]["publisherChecksum"]["result"] == "MATCH", f"{coordinate} {kind} checksum not matched")
            require(component[kind]["detachedSignature"]["result"] == "CRYPTOGRAPHICALLY_VERIFIED", f"{coordinate} {kind} signature not verified")

    jetbrains = authentic_by_coordinate["org.jetbrains:annotations:13.0"]
    require(jetbrains["upstreamLicenseTextLocator"] == JETBRAINS_LICENSE_URL, "JetBrains LICENSE locator is not immutable")
    require(jetbrains["licenseTextSha256"] == JETBRAINS_LICENSE_SHA256, "JetBrains LICENSE digest drift")
    require(
        jetbrains["notice"]["locator"] == JETBRAINS_NOTICE_URL
        and jetbrains["notice"]["sha256"] == JETBRAINS_NOTICE_SHA256
        and jetbrains["notice"]["result"] == "IMMUTABLE_NOTICE_VERIFIED_AND_PRESERVATION_REQUIRED"
        and "PRESERVE" in jetbrains["notice"]["requirement"],
        "JetBrains NOTICE evidence/preservation drift",
    )
    verification = jetbrains["licenseAndNoticeVerification"]
    require(
        verification["verifiedAtUtc"] == "2026-08-12T14:38:33Z"
        and verification["verificationTool"] == "gh api GitHub Contents API + System.Security.Cryptography.SHA256"
        and verification["licenseBytes"] == 11358
        and verification["noticeBytes"] == 127
        and verification["governancePacketEvidenceAccepted"] is True
        and verification["futureActualGraphApproved"] is False
        and verification["redistributionApproved"] is False,
        "JetBrains immutable verification record drift",
    )
    forbidden = re.compile(r"EXACT_SOURCE[^\n\"]*PENDING|JetBrains/java-annotations/master|raw\.githubusercontent\.com/JetBrains/[^\s\"]*/master")
    require(forbidden.search(json.dumps(authenticity)) is None, "Mutable/PENDING JetBrains evidence remains")

    require(license_notice["schemaVersion"] == 4, "License/NOTICE inventory schema drift")
    require(license_notice["summary"]["jetbrainsAnnotationsImmutableLicenseNoticeVerified"] is True, "License/NOTICE inventory lacks JetBrains closure")
    license_jetbrains = next(item for item in license_notice["components"] if item["coordinate"] == "org.jetbrains:annotations:13.0")
    require(
        license_jetbrains["immutableLicenseLocator"] == JETBRAINS_LICENSE_URL
        and license_jetbrains["immutableLicenseSha256"] == JETBRAINS_LICENSE_SHA256
        and license_jetbrains["immutableNoticeLocator"] == JETBRAINS_NOTICE_URL
        and license_jetbrains["immutableNoticeSha256"] == JETBRAINS_NOTICE_SHA256
        and license_jetbrains["governancePacketEvidenceAccepted"] is True
        and license_jetbrains["futureActualGraphApproved"] is False
        and "PRESERVE" in license_jetbrains["noticePreservationRequirement"],
        "License/NOTICE inventory JetBrains record drift",
    )

    require(
        readiness["schemaVersion"] == 10
        and readiness["taskScopedImplementationAuthorized"] is True
        and readiness["authorizationId"] == AUTHORIZATION_ID
        and readiness["recoveryModuleExists"] is True
        and readiness["runtimeDependencyAdded"] is False
        and readiness["executionAllowed"] is False
        and readiness["implementationAllowed"] is False
        and readiness["implementationAllowedByThisPackage"] is False
        and readiness["measuredExecutionAllowed"] is False,
        "Readiness authority boundary drift",
    )
    require(
        readiness["packageArtifacts"]["activeGateSetVersion"] == GATE_ID
        and readiness["packageArtifacts"]["activeProtocolId"] == PROTOCOL_ID,
        "Readiness active v0.6 metadata drift",
    )
    readiness_policy = readiness["dependencyExclusionPolicy"]
    require(
        readiness_policy["policyId"] == "REC-JSR305-EXCLUDE-001"
        and readiness_policy["coveredFutureModule"] == ":poc:recovery"
        and readiness_policy["allCoveredRecoveryInputsRequired"] is True
        and readiness_policy["currentRecoveryModuleExists"] is True
        and readiness_policy["currentTinkAndroid123Wired"] is False
        and readiness_policy["repositoryWideAbsenceClaimed"] is False
        and readiness_policy["requiredResolvedComponentCount"] == 0
        and readiness_policy["requiredR8Rules"] == R8_RULES
        and readiness_policy["broaderDontwarnAllowed"] is False
        and readiness_policy["unresolvedR8MissingClassesAllowed"] is False
        and readiness_policy["futureResolvedGraphReportPresent"] is False,
        "Readiness recovery-only graph/R8 policy drift",
    )
    require(
        review_roles["schemaVersion"] == 9
        and review_roles["activeGateSetVersion"] == GATE_ID
        and review_roles["activeProtocolId"] == PROTOCOL_ID,
        "Review-role active v0.6 metadata drift",
    )
    product_ip = review_roles["roles"]["stage0ProductIp"]
    require(
        product_ip["governancePacketEvidenceDisposition"]["status"] == "CLOSED_VERIFIED"
        and product_ip["futureExactGraphDisposition"]["status"] == "OPEN_BLOCKED"
        and product_ip["finalApproved"] is False
        and product_ip["approvedReviewer"] is None,
        "Product/IP packet/actual-graph role split drift",
    )
    accountable = review_roles["roles"]["independentRecoveryEngineeringSecurity"]
    require(
        accountable["reviewer"] == "Novikova Katerina"
        and accountable["capacity"] == "individual professional capacity; Rambus listed only as affiliation"
        and accountable["formalReviewer"] is True
        and accountable["status"] == "APPROVE_FOR_SEPARATE_IMPLEMENTATION_REVIEW"
        and accountable["closesRecRdy02"] is True
        and accountable["rambusCorporateApprovalClaimed"] is False
        and accountable["mayApproveImplementation"] is False
        and accountable["mayAuthorizeExecution"] is False
        and all(accountable[field] is False for field in (
            "implementationAllowed", "implementationAllowedByThisPackage", "executionAllowed",
            "measuredExecutionAllowed", "phaseAAuthorized",
        )),
        "Accountable review/authority boundary drift",
    )
    require(
        review_roles["roles"]["advisoryDocumentaryReviewer"]["formalReviewer"] is False
        and review_roles["roles"]["advisoryDocumentaryReviewer"]["closesRecRdy02"] is False,
        "AI advisory review acquired formal/accountable authority",
    )
    rereview = readiness["advisoryDocumentaryReReviewEvidence"]
    require(
        rereview["formalReviewer"] is False
        and rereview["disposition"] == "NO_FURTHER_DOCUMENTARY_CHANGES_REQUIRED"
        and rereview["actionableFindings"] == []
        and rereview["closesRecRev2026081202"] is False
        and rereview["closesRecRdy02"] is False,
        "AI advisory re-review acquired formal/accountable authority",
    )


def verify_online(
    inventory: dict[str, Any],
    license_notice: dict[str, Any],
    authenticity: dict[str, Any],
    jsr305_exclusion: dict[str, Any],
) -> None:
    expected_edges: dict[str, set[tuple[str, str]]] = {}
    for edge in inventory["graphEdges"]:
        expected_edges.setdefault(edge["from"], set()).add((edge["to"], edge["scope"]))
    licenses_by_coordinate = {
        component["coordinate"]: component
        for component in license_notice["components"]
        if not component["coordinate"].startswith("embedded:")
    }
    authenticity_by_coordinate = {component["coordinate"]: component for component in authenticity["components"]}

    with tempfile.TemporaryDirectory(prefix="dora-recovery-deps-") as temporary:
        temp_root = Path(temporary)
        java, verifier_classpath, _ = prepare_openpgp_verifier(temp_root)
        public_keys: dict[str, Path] = {}
        identity_sources: dict[str, str] = {}
        for index, artifact in enumerate(inventory["artifacts"], start=1):
            coordinate = artifact["coordinate"]
            jar_path = temp_root / f"{index:02d}.jar"
            pom_path = temp_root / f"{index:02d}.pom"
            download(artifact["jar"]["url"], jar_path)
            download(artifact["pom"]["url"], pom_path)
            verify_file(jar_path, artifact["jar"], f"{coordinate} JAR")
            verify_file(pom_path, artifact["pom"], f"{coordinate} POM")
            verify_jar(jar_path, artifact)
            if coordinate == "com.google.crypto.tink:tink-android:1.23.0":
                verify_tink_jsr305_bytecode(jar_path, jsr305_exclusion)

            authenticity_record = authenticity_by_coordinate[coordinate]
            key_record = authenticity_record["openPgpKey"]
            key_locator = key_record["keyMaterialLocator"]
            if key_locator not in public_keys:
                key_path = temp_root / f"key-{len(public_keys) + 1:02d}.asc"
                download(key_locator, key_path)
                public_keys[key_locator] = key_path
            public_key_path = public_keys[key_locator]
            identity_locator = key_record["identitySource"]
            if identity_locator not in identity_sources:
                identity_path = temp_root / f"identity-{len(identity_sources) + 1:02d}.html"
                download(identity_locator, identity_path)
                identity_sources[identity_locator] = identity_path.read_text(encoding="utf-8", errors="replace")
            identity_text = identity_sources[identity_locator]
            identity_email = re.fullmatch(r".*<([^<>]+)>", authenticity_record["signerIdentity"])
            require(identity_email is not None and identity_email.group(1) in identity_text, f"{coordinate} signer identity email is absent from the full-fingerprint index metadata")
            require(key_record["primaryFingerprint"].lower() in identity_text.lower(), f"{coordinate} primary fingerprint is absent from the identity-source response")
            for kind, artifact_path in (("jar", jar_path), ("pom", pom_path)):
                file_record = authenticity_record[kind]
                checksum_record = file_record["publisherChecksum"]
                checksum_path = temp_root / f"{index:02d}.{kind}.checksum"
                download(checksum_record["locator"], checksum_path)
                publisher_value = parse_publisher_checksum(checksum_path, checksum_record["algorithm"])
                require(publisher_value == checksum_record["value"].lower(), f"{coordinate} {kind} publisher checksum evidence drift")
                require(file_digest(artifact_path, checksum_record["algorithm"]) == publisher_value, f"{coordinate} {kind} publisher checksum mismatch")

                signature_record = file_record["detachedSignature"]
                signature_path = temp_root / f"{index:02d}.{kind}.asc"
                download(signature_record["locator"], signature_path)
                issuer_identifiers = signature_issuer_identifiers(signature_path)
                fingerprint = signature_record["signerFingerprint"]
                require(
                    fingerprint in issuer_identifiers or fingerprint[-16:] in {identifier[-16:] for identifier in issuer_identifiers},
                    f"{coordinate} {kind} detached-signature issuer does not match recorded fingerprint: {sorted(issuer_identifiers)}",
                )
                verify_detached_signature(
                    java,
                    verifier_classpath,
                    public_key_path,
                    signature_path,
                    artifact_path,
                    signature_record,
                    authenticity_record["signerIdentity"],
                    f"{coordinate} {kind}",
                )

            if authenticity_record["authenticityStatus"] == "AUTHENTICITY_VERIFIED_MULTISOURCE_CORRESPONDENCE":
                source_record = authenticity_record["sourceCorrespondence"]["sourceJar"]
                source_path = temp_root / f"{index:02d}.sources.jar"
                source_signature_path = temp_root / f"{index:02d}.sources.jar.asc"
                download(source_record["locator"], source_path)
                download(source_record["detachedSignatureLocator"], source_signature_path)
                verify_file(source_path, source_record, f"{coordinate} sources JAR")
                source_signature_record = {
                    "primaryKeyFingerprint": key_record["primaryFingerprint"],
                    "signerFingerprint": key_record["signingFingerprint"],
                    "signatureCreatedUtc": source_record["signatureCreatedUtc"],
                    "hashAlgorithm": authenticity_record["jar"]["detachedSignature"]["hashAlgorithm"],
                }
                verify_detached_signature(
                    java,
                    verifier_classpath,
                    public_key_path,
                    source_signature_path,
                    source_path,
                    source_signature_record,
                    authenticity_record["signerIdentity"],
                    f"{coordinate} sources JAR",
                )
                if coordinate == "com.google.crypto.tink:tink-android:1.23.0":
                    verify_tink_jsr305_sources(source_path, jsr305_exclusion)

            actual_dependencies = parse_project_dependencies(pom_path)
            require(
                actual_dependencies == expected_edges.get(coordinate, set()),
                f"{coordinate} POM dependency edges mismatch: {actual_dependencies}",
            )
            license_record = licenses_by_coordinate[coordinate]
            require(
                parse_pom_license(pom_path)
                == (license_record["pomLicenseName"], license_record["pomLicenseUrl"]),
                f"{coordinate} POM license declaration mismatch",
            )

            module = artifact["gradleModuleMetadata"]
            if module is not None:
                module_path = temp_root / f"{index:02d}.module"
                download(module["url"], module_path)
                verify_file(module_path, module, f"{coordinate} Gradle module metadata")

        jetbrains_license = github_content_bytes(
            "JetBrains/intellij-community", "LICENSE.txt", JETBRAINS_COMMIT
        )
        jetbrains_notice = github_content_bytes(
            "JetBrains/intellij-community", "NOTICE.txt", JETBRAINS_COMMIT
        )
        require(len(jetbrains_license) == 11358, "JetBrains immutable LICENSE byte length mismatch")
        require(len(jetbrains_notice) == 127, "JetBrains immutable NOTICE byte length mismatch")
        require(
            hashlib.sha256(jetbrains_license).hexdigest() == JETBRAINS_LICENSE_SHA256,
            "JetBrains immutable LICENSE SHA-256 mismatch",
        )
        require(
            hashlib.sha256(jetbrains_notice).hexdigest() == JETBRAINS_NOTICE_SHA256,
            "JetBrains immutable NOTICE SHA-256 mismatch",
        )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--online",
        action="store_true",
        help="Download every published JAR/POM/metadata file to a temporary directory and verify it.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    inventory = read_json(INVENTORY_PATH)
    license_notice = read_json(LICENSE_PATH)
    authenticity = read_json(AUTHENTICITY_PATH)
    jsr305_exclusion = read_json(JSR305_EXCLUSION_PATH)
    readiness = read_json(READINESS_PATH)
    review_roles = read_json(REVIEW_ROLES_PATH)
    validate_static(inventory, license_notice, authenticity, jsr305_exclusion, readiness, review_roles)
    if args.online:
        verify_online(inventory, license_notice, authenticity, jsr305_exclusion)
        print("Verified 8 exact external JAR/POM coordinates online plus immutable JetBrains LICENSE/NOTICE bytes: artifact hashes, publisher checksums, full-fingerprint detached OpenPGP cryptography and identity metadata, signed source JARs for the six multisource coordinates, POM graph/licenses, no native payload, exact Tink JSR305 annotation-only classification, and exact-commit LICENSE/NOTICE SHA-256; temporary files removed")
    else:
        print("POC-RECOVERY-001 v0.6 dependency/IP static validation passed; authorized pure REC-I1 module is present with no Tink/runtime-crypto wiring or new coordinate, exact governance packet authenticity/LICENSE/NOTICE evidence and prospective REC-JSR305-EXCLUDE-001 are closed, future actual runtime graph/Product-IP disposition remains blocked, repository-wide absence is not claimed, and excluded JSR305 use/distribution is unapproved (use --online for artifact/signature and immutable LICENSE/NOTICE revalidation)")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, ET.ParseError, zipfile.BadZipFile, struct.error, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
