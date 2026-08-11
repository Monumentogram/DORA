#!/usr/bin/env python3
"""Generate or verify the locked dependency inventory for POC-SEARCH-001."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any


POC_ID = "POC-SEARCH-001"
SCHEMA_VERSION = 1
LOCK_CONFIGURATIONS = (
    "_agp_internal_debug_kspClasspath",
    "debugAndroidTestRuntimeClasspath",
    "debugRuntimeClasspath",
)
ARTIFACT_SUFFIXES = {".aar", ".jar", ".klib", ".pom"}
IGNORED_CLASSIFIERS = ("-javadoc", "-sources")


class InventoryError(RuntimeError):
    """Raised when the inventory cannot be generated reproducibly."""


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_lock(lock_path: Path) -> dict[str, list[str]]:
    components: dict[str, list[str]] = {}
    for raw_line in lock_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        coordinate, raw_configurations = line.split("=", 1)
        configurations = set(raw_configurations.split(","))
        selected = sorted(configurations.intersection(LOCK_CONFIGURATIONS))
        if selected:
            components[coordinate] = selected
    if not components:
        raise InventoryError(f"No selected configurations found in {lock_path}")
    return components


def repository_base(group: str) -> str:
    if group.startswith("androidx."):
        return "https://dl.google.com/dl/android/maven2"
    return "https://repo1.maven.org/maven2"


def artifact_source_url(group: str, module: str, version: str, filename: str) -> str:
    base = repository_base(group)
    group_path = group.replace(".", "/")
    return f"{base}/{group_path}/{module}/{version}/{filename}"


def child_text(element: ET.Element, name: str) -> str | None:
    child = element.find(f"{{*}}{name}")
    if child is None or child.text is None:
        return None
    value = child.text.strip()
    return value or None


def declared_licenses(pom_paths: list[Path]) -> list[dict[str, str | None]]:
    licenses: set[tuple[str | None, str | None, str | None]] = set()
    for pom_path in pom_paths:
        try:
            root = ET.parse(pom_path).getroot()
        except ET.ParseError as exc:
            raise InventoryError(f"Cannot parse Maven POM {pom_path}: {exc}") from exc
        for license_element in root.findall(".//{*}licenses/{*}license"):
            licenses.add(
                (
                    child_text(license_element, "name"),
                    child_text(license_element, "url"),
                    child_text(license_element, "distribution"),
                )
            )
    return [
        {"name": name, "url": url, "distribution": distribution}
        for name, url, distribution in sorted(
            licenses,
            key=lambda value: tuple(part or "" for part in value),
        )
    ]


def component_files(
    cache_root: Path,
    group: str,
    module: str,
    version: str,
) -> list[Path]:
    component_root = cache_root / group / module / version
    if not component_root.is_dir():
        raise InventoryError(f"Gradle cache entry is missing: {component_root}")
    files = [
        path
        for path in component_root.rglob("*")
        if path.is_file()
        and path.suffix.lower() in ARTIFACT_SUFFIXES
        and not any(classifier in path.stem for classifier in IGNORED_CLASSIFIERS)
    ]
    if not files:
        raise InventoryError(f"No artifact or metadata files found under {component_root}")
    return sorted(files, key=lambda value: (value.name, sha256_file(value)))


def build_inventory(repo_root: Path, gradle_user_home: Path) -> dict[str, Any]:
    lock_path = repo_root / "android" / "poc" / "search" / "gradle.lockfile"
    if not lock_path.is_file():
        raise InventoryError(f"Dependency lock is missing: {lock_path}")
    locked_components = read_lock(lock_path)
    cache_root = gradle_user_home / "caches" / "modules-2" / "files-2.1"
    components: list[dict[str, Any]] = []
    for coordinate, scopes in sorted(locked_components.items()):
        parts = coordinate.split(":")
        if len(parts) != 3 or not all(parts):
            raise InventoryError(f"Unsupported locked coordinate: {coordinate}")
        group, module, version = parts
        files = component_files(cache_root, group, module, version)
        file_entries = [
            {
                "bytes": path.stat().st_size,
                "filename": path.name,
                "kind": path.suffix.lower().lstrip("."),
                "sha256": sha256_file(path),
                "sourceUrl": artifact_source_url(group, module, version, path.name),
            }
            for path in files
        ]
        pom_paths = [path for path in files if path.suffix.lower() == ".pom"]
        components.append(
            {
                "artifacts": file_entries,
                "coordinate": coordinate,
                "declaredLicenses": declared_licenses(pom_paths),
                "licenseMetadataStatus": "PRESENT" if pom_paths else "MISSING",
                "repository": repository_base(group),
                "scopes": scopes,
            }
        )
    relative_lock = lock_path.relative_to(repo_root).as_posix()
    return {
        "schemaVersion": SCHEMA_VERSION,
        "pocId": POC_ID,
        "generator": "tools/poc_search_dependency_inventory.py",
        "lock": {
            "configurations": list(LOCK_CONFIGURATIONS),
            "path": relative_lock,
            "sha256": sha256_file(lock_path),
        },
        "inventoryStatus": "COMPLETE_UNREVIEWED",
        "componentCount": len(components),
        "components": components,
        "review": {
            "evaluationApproval": "NOT_ESTABLISHED",
            "note": (
                "This file records resolved provenance and declared Maven metadata only. "
                "It is not a Product/Legal/IP or Engineering/Security approval."
            ),
        },
    }


def canonical_json(value: dict[str, Any]) -> str:
    return json.dumps(value, indent=2, sort_keys=True, ensure_ascii=False) + "\n"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument(
        "--gradle-user-home",
        type=Path,
        default=Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")),
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("docs/evidence/poc-search-001/dependency-inventory.json"),
    )
    parser.add_argument("--check", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    repo_root = args.root.resolve()
    output_path = args.output
    if not output_path.is_absolute():
        output_path = repo_root / output_path
    try:
        rendered = canonical_json(build_inventory(repo_root, args.gradle_user_home.resolve()))
    except InventoryError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1
    if args.check:
        if not output_path.is_file():
            print(f"ERROR: dependency inventory is missing: {output_path}", file=sys.stderr)
            return 1
        if output_path.read_text(encoding="utf-8") != rendered:
            print(
                "ERROR: dependency inventory does not match the lock and resolved Gradle cache; "
                "regenerate it before review",
                file=sys.stderr,
            )
            return 1
        print(f"Validated {output_path.relative_to(repo_root)}")
        return 0
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(rendered, encoding="utf-8", newline="\n")
    print(f"Wrote {output_path.relative_to(repo_root)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
