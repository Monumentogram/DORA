#!/usr/bin/env python3
"""Verify the exact published Tink closure and v0.3 IP/authenticity evidence without Gradle changes."""

from __future__ import annotations

import argparse
import base64
import hashlib
import json
import re
import sys
import tempfile
import time
import urllib.error
import urllib.request
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "docs" / "evidence" / "poc-recovery-001"
INVENTORY_PATH = EVIDENCE / "dependency-inventory.json"
LICENSE_PATH = EVIDENCE / "license-notice-inventory.json"
AUTHENTICITY_PATH = EVIDENCE / "dependency-ip-authenticity-v0.3.json"
MAVEN_NS = {"m": "http://maven.apache.org/POM/4.0.0"}
USER_AGENT = "Dora-POC-RECOVERY-001-inventory-verifier/1.0"
NATIVE_SUFFIXES = (".so", ".dll", ".dylib")


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


def validate_static(inventory: dict[str, Any], license_notice: dict[str, Any], authenticity: dict[str, Any]) -> None:
    require(inventory["schemaVersion"] == 2, "Dependency inventory schema drift")
    require(inventory["rootCoordinate"] == "com.google.crypto.tink:tink-android:1.23.0", "Root coordinate drift")
    require(inventory["dependencyAdmission"] is False, "Inventory must not admit the dependency")
    require(inventory["runtimeGraphModified"] is False, "Inventory must not claim a runtime graph")
    require(len(inventory["artifacts"]) == 8, "Expected eight published external coordinates")
    require(len(inventory["graphEdges"]) == 8, "Expected eight publisher dependency edges")
    require(all(artifact["jar"]["nativeEntries"] == 0 for artifact in inventory["artifacts"]), "Native entry recorded")
    require(inventory["inventoryStatus"] == "VERIFIED_PUBLISHER_CLOSURE_AUTHENTICITY_PENDING_PACKAGE_REVIEW_ONLY", "Inventory authenticity status drift")
    require(inventory["authenticityEvidence"]["locator"] == "docs/evidence/poc-recovery-001/dependency-ip-authenticity-v0.3.json", "Inventory authenticity locator drift")
    require(license_notice["schemaVersion"] == 2 and license_notice["reviewStatus"] == "AUTHENTICITY_PENDING_PRODUCT_IP_APPROVAL_BLOCKED", "License review status drift")
    require(license_notice["evaluationApproved"] is False, "Evaluation must not be approved yet")
    require(license_notice["approvedReviewer"] is None and license_notice["approvedOn"] is None, "Product/IP approval identity/date must remain null")

    require(authenticity["schemaVersion"] == 1 and authenticity["pocId"] == "POC-RECOVERY-001", "Authenticity evidence identity drift")
    require(authenticity["overallStatus"] == "AUTHENTICITY_PENDING_PRODUCT_IP_APPROVAL_BLOCKED", "Authenticity must remain pending")
    require(authenticity["summary"]["coordinates"] == 8 and len(authenticity["components"]) == 8, "Authenticity coordinate count drift")
    require(authenticity["summary"]["publisherChecksumsMatched"] == 16, "Publisher checksum count drift")
    require(authenticity["summary"]["detachedSignaturesCryptographicallyVerified"] == 16, "Detached-signature verification count drift")
    require(authenticity["summary"]["coordinatesWithUpstreamSignerTrustConfirmed"] == 2 and authenticity["summary"]["coordinatesAuthenticityPending"] == 6, "Signer-trust/authenticity counts drift")
    require(authenticity["approvalBoundary"]["productIpApprovalAllowedWhileAnyCoordinatePending"] is False, "Product/IP approval must fail closed on pending authenticity")
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
        require(component["licenseId"] == license_component["licenseSpdx"] == "Apache-2.0", f"{coordinate} license identifier drift")
        require(component["upstreamLicenseTextLocator"].startswith("https://") and re.fullmatch(r"[0-9a-f]{64}", component["licenseTextSha256"]), f"{coordinate} upstream license evidence incomplete")
        require(component["copyrightEvidence"]["locator"].startswith("https://") and component["copyrightEvidence"]["status"], f"{coordinate} copyright evidence incomplete")
        require(component["notice"]["requirement"] and component["notice"]["locator"].startswith("https://") and component["notice"]["result"], f"{coordinate} NOTICE evidence incomplete")
        require(component["verifiedAtUtc"] == authenticity["verifiedAtUtc"] and component["verificationTool"] == authenticity["verificationTool"], f"{coordinate} verification timestamp/tool attribution drift")
        for kind in ("jar", "pom"):
            item = component[kind]
            require(item["sha256"] == artifact[kind]["sha256"], f"{coordinate} {kind} SHA-256 evidence drift")
            require(item["publisherChecksum"]["result"] == "MATCH" and item["publisherChecksum"]["locator"].startswith("https://"), f"{coordinate} {kind} publisher checksum missing")
            require(item["detachedSignature"]["result"] == "CRYPTOGRAPHICALLY_VERIFIED" and item["detachedSignature"]["locator"].startswith("https://"), f"{coordinate} {kind} detached signature missing")
            require(re.fullmatch(r"[0-9A-F]{40}", item["detachedSignature"]["signerFingerprint"]), f"{coordinate} {kind} signer fingerprint drift")
        require(component["jar"]["detachedSignature"]["signerFingerprint"] == component["pom"]["detachedSignature"]["signerFingerprint"], f"{coordinate} JAR/POM signer mismatch")
        require(component["signerIdentity"] and component["signerTrustStatus"] and component["sourceCorrespondence"]["status"], f"{coordinate} signer/source evidence incomplete")
        if component["authenticityStatus"] == "AUTHENTICITY_PENDING":
            require(component["closurePath"] and component["signerTrustStatus"] == "UNCONFIRMED_BY_COORDINATE_SPECIFIC_UPSTREAM_KEY_PUBLICATION", f"{coordinate} pending authenticity lacks an exact closure path")
        else:
            require(component["authenticityStatus"] == "VERIFIED_FOR_PACKAGE_REVIEW" and component["signerTrustSource"].startswith("https://") and component["closurePath"] is None, f"{coordinate} verified authenticity evidence drift")
    require(sum(component["authenticityStatus"] == "AUTHENTICITY_PENDING" for component in authentic_by_coordinate.values()) == 6, "Exactly six coordinates must remain authenticity-pending")


def verify_online(inventory: dict[str, Any], license_notice: dict[str, Any], authenticity: dict[str, Any]) -> None:
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
        for index, artifact in enumerate(inventory["artifacts"], start=1):
            coordinate = artifact["coordinate"]
            jar_path = temp_root / f"{index:02d}.jar"
            pom_path = temp_root / f"{index:02d}.pom"
            download(artifact["jar"]["url"], jar_path)
            download(artifact["pom"]["url"], pom_path)
            verify_file(jar_path, artifact["jar"], f"{coordinate} JAR")
            verify_file(pom_path, artifact["pom"], f"{coordinate} POM")
            verify_jar(jar_path, artifact)

            authenticity_record = authenticity_by_coordinate[coordinate]
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
    validate_static(inventory, license_notice, authenticity)
    if args.online:
        verify_online(inventory, license_notice, authenticity)
        print("Verified 8 exact external JAR/POM coordinates online: artifact hashes, publisher checksums, detached-signature issuer fingerprints, POM graph/licenses and no native payload; temporary files removed")
    else:
        print("POC-RECOVERY-001 v0.3 dependency/IP/authenticity static validation passed (use --online for publisher checksum and detached-signature revalidation)")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, ET.ParseError, zipfile.BadZipFile, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
