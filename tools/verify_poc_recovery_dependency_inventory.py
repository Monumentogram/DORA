#!/usr/bin/env python3
"""Verify the exact published Tink candidate closure without changing a Gradle graph."""

from __future__ import annotations

import argparse
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


def validate_static(inventory: dict[str, Any], license_notice: dict[str, Any]) -> None:
    require(inventory["rootCoordinate"] == "com.google.crypto.tink:tink-android:1.23.0", "Root coordinate drift")
    require(inventory["dependencyAdmission"] is False, "Inventory must not admit the dependency")
    require(inventory["runtimeGraphModified"] is False, "Inventory must not claim a runtime graph")
    require(len(inventory["artifacts"]) == 8, "Expected eight published external coordinates")
    require(len(inventory["graphEdges"]) == 8, "Expected eight publisher dependency edges")
    require(all(artifact["jar"]["nativeEntries"] == 0 for artifact in inventory["artifacts"]), "Native entry recorded")
    require(license_notice["reviewStatus"] == "EVIDENCE_COMPLETE_PACKAGE_REVIEW_PENDING", "License review status drift")
    require(license_notice["evaluationApproved"] is False, "Evaluation must not be approved yet")


def verify_online(inventory: dict[str, Any], license_notice: dict[str, Any]) -> None:
    expected_edges: dict[str, set[tuple[str, str]]] = {}
    for edge in inventory["graphEdges"]:
        expected_edges.setdefault(edge["from"], set()).add((edge["to"], edge["scope"]))
    licenses_by_coordinate = {
        component["coordinate"]: component
        for component in license_notice["components"]
        if not component["coordinate"].startswith("embedded:")
    }

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
    validate_static(inventory, license_notice)
    if args.online:
        verify_online(inventory, license_notice)
        print("Verified 8 exact external JAR/POM coordinates online; no native payload; temporary files removed")
    else:
        print("POC-RECOVERY-001 dependency inventory static validation passed (use --online for artifact download verification)")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, ET.ParseError, zipfile.BadZipFile, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
