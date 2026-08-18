#!/usr/bin/env python3
"""Generate or verify the locked dependency inventory for POC-SEARCH-001."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path
from typing import Any


POC_ID = "POC-SEARCH-001"
SCHEMA_VERSION = 1
LOCK_CONFIGURATIONS = (
    "_agp_internal_benchmark_kspClasspath",
    "_agp_internal_debug_kspClasspath",
    "benchmarkAndroidTestRuntimeClasspath",
    "benchmarkRuntimeClasspath",
    "debugAndroidTestRuntimeClasspath",
    "debugRuntimeClasspath",
)
ARTIFACT_SUFFIXES = {".aar", ".jar", ".klib", ".pom"}
IGNORED_CLASSIFIERS = ("-javadoc", "-sources")
LOCK_RELATIVE_PATH = Path("android/poc/search/gradle.lockfile")
CATALOG_RELATIVE_PATH = Path("android/gradle/libs.versions.toml")
INVENTORY_RELATIVE_PATH = Path("docs/evidence/poc-search-001/dependency-inventory.json")
OVERLAY_RELATIVE_PATH = Path(
    "docs/evidence/poc-search-001/build-tool-lock-overlay-ksp-2.3.11.json"
)
OVERLAY_PROFILE_ID = "DORA-KSP-2_3_11-BUILD-TOOL-LOCK-OVERLAY"
OVERLAY_AUTHORIZATION = "DORA-KSP-BUILD-TOOL-LOCK-OVERLAY-REMEDIATION-AUTH-20260818-03"
ROOM_PROCESSOR_API = "com.google.devtools.ksp:symbol-processing-api:2.0.10-1.0.24"
KSP_PLUGIN_API_OLD = "com.google.devtools.ksp:symbol-processing-api:2.3.9"
KSP_PLUGIN_API_NEW = "com.google.devtools.ksp:symbol-processing-api:2.3.11"
PROJECTION_ENCODING = (
    "utf8-lf-maven-group-segments-module-hyphen-before-terminator-"
    "coordinate-equals-comma-sorted-scopes-v1"
)
EXPECTED_NON_CLAIMS = [
    "POC-SEARCH-001 remains FORMAL INCONCLUSIVE with recommendation BLOCKED.",
    "benchmarkExecutionAllowed remains false; this profile authorizes no measured execution.",
    "The KSP 2.3.11 selection is build-tool maintenance only and is outside the immutable OD-13 evaluated projection.",
    "This profile does not admit KSP, Room, FTS4, a schema, a runtime dependency, "
    "a native artifact, or a product feature into production.",
    "Observed source, license, release, and advisory metadata is engineering evidence, "
    "not legal advice or production Legal/Security approval.",
]
SHA256_PATTERN = re.compile(r"^[0-9a-f]{64}$")
GIT_SHA1_PATTERN = re.compile(r"^[0-9a-f]{40}$")
COORDINATE_PATTERN = re.compile(r"^[^:=\s]+:[^:=\s]+:[^:=\s]+$")
CONFIGURATION_PATTERN = re.compile(r"^[A-Za-z0-9_.-]+$")
MAINTENANCE_BASE_COMMIT = "1203ed7959ed5191df9b4e4bd7dcdf982e0f8f8a"
MAINTENANCE_BASE_TREE = "1283ef75c7878e74e3cabbe89395289fdbe8847a"
MAINTENANCE_CATALOG_BLOB = "67da6c7c06db99f7bbfba46493bee86592a3c0e3"
MAINTENANCE_CATALOG_SHA256 = "ff17fff2c7a369593ea8c0269575cea437ebef07f5c81508a6d33f95839565e3"
HISTORICAL_EVIDENCE_COMMIT = "849d9d0406a619b334c9b707a4b6b42b34885b4b"
HISTORICAL_EVIDENCE_TREE = "65b412e86665ddcd38a0828fce1e4f1197490e18"
HISTORICAL_LOCK_BLOB = "fc9248df3bd082c6e2307fd33000571d66734c27"
HISTORICAL_LOCK_SHA256 = "d912e782f3f01452f9f785b6cf0971d1a76a6c6a984c5ea5e03ba1b311049704"
CURRENT_CATALOG_SHA256 = "230e8b8f5042b5e4852aa3ad05009e5b1d1336eb467d9d89f3d37a7f5104fc4c"
CURRENT_LOCK_SHA256 = "3e47b2a46c493245ad24399b8bb26c834bc79b52397a4e920d5895bec695ba8f"
EVALUATED_PROJECTION_SHA256 = "ce4431bf4364a203db42674dd1a0aa88c264233ec67f26180a4d06af251acbc9"
KSP_PLUGIN_CONFIGURATIONS = (
    "kotlinCompilerPluginClasspathBenchmark",
    "kotlinCompilerPluginClasspathBenchmarkAndroidTest",
    "kotlinCompilerPluginClasspathBenchmarkUnitTest",
    "kotlinCompilerPluginClasspathDebug",
    "kotlinCompilerPluginClasspathDebugAndroidTest",
    "kotlinCompilerPluginClasspathDebugUnitTest",
    "kotlinCompilerPluginClasspathRelease",
    "kspPluginClasspath",
    "kspPluginClasspathNonEmbeddable",
)
EMPTY_PROCESSOR_CONFIGURATIONS = (
    "kspBenchmarkAndroidTestKotlinProcessorClasspath",
    "kspBenchmarkUnitTestKotlinProcessorClasspath",
    "kspDebugAndroidTestKotlinProcessorClasspath",
    "kspDebugUnitTestKotlinProcessorClasspath",
)
EMPTY_PROCESSOR_REMOVED_COORDINATES = (
    "androidx.annotation:annotation-jvm:1.9.1",
    "androidx.annotation:annotation:1.9.1",
    "androidx.room:room-common-jvm:2.8.4",
    "androidx.room:room-common:2.8.4",
    "androidx.room:room-compiler-processing:2.8.4",
    "androidx.room:room-compiler:2.8.4",
    "androidx.room:room-external-antlr:2.8.4",
    "androidx.room:room-migration-jvm:2.8.4",
    "androidx.room:room-migration:2.8.4",
    "com.google.auto.value:auto-value-annotations:1.6.3",
    "com.google.auto:auto-common:1.2.1",
    "com.google.code.findbugs:jsr305:3.0.2",
    "com.google.devtools.ksp:symbol-processing-api:2.0.10-1.0.24",
    "com.google.errorprone:error_prone_annotations:2.26.1",
    "com.google.guava:failureaccess:1.0.2",
    "com.google.guava:guava:33.2.1-jre",
    "com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava",
    "com.intellij:annotations:12.0",
    "com.squareup:javapoet:1.13.0",
    "com.squareup:kotlinpoet-javapoet:2.0.0",
    "com.squareup:kotlinpoet-jvm:2.0.0",
    "com.squareup:kotlinpoet:2.0.0",
    "commons-codec:commons-codec:1.15",
    "org.checkerframework:checker-qual:3.42.0",
    "org.jetbrains.kotlin:kotlin-metadata-jvm:2.2.0",
    "org.jetbrains.kotlin:kotlin-reflect:2.0.10",
    "org.jetbrains.kotlin:kotlin-stdlib:2.2.0",
    "org.jetbrains.kotlinx:kotlinx-serialization-bom:1.8.1",
    "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.8.1",
    "org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1",
    "org.jetbrains.kotlinx:kotlinx-serialization-json-jvm:1.8.1",
    "org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1",
    "org.jetbrains:annotations:13.0",
    "org.jspecify:jspecify:1.0.0",
    "org.xerial:sqlite-jdbc:3.41.2.2",
)
IMMUTABLE_EVIDENCE_SHA256 = {
    "docs/evidence/poc-search-001/assessments/review-2026-08-11-v4/benchmark-result.json": (
        "5fc5bd2d8340d11bbaf7b88fe3c26e1859d57ef84409f2c580158d2a3609a6b2"
    ),
    "docs/evidence/poc-search-001/assessments/review-2026-08-11-v5/benchmark-result.json": (
        "76ea73bf251cc3b136e0c780b63c9ba346e092e6817bd7f37b4b8bc5c698c660"
    ),
    "docs/evidence/poc-search-001/dependency-inventory.json": (
        "63a2a3dadfbfe072770d914a74cbd40d6adbd517548bda4ba0331dd314ca6a98"
    ),
    "docs/evidence/poc-search-001/evidence-index.json": (
        "25fc2d6d6a89e2c496ef1d8538582252357ca0410521f0868910fc452397ac43"
    ),
    "docs/evidence/poc-search-001/ip-evaluation.json": (
        "61c900ab976f07824b40077c3bb33c47888b30a74a92fc710045fca682509e9a"
    ),
    "docs/evidence/poc-search-001/ip-stage0-evaluation-review.md": (
        "87e4ef5406c2a1ea3466d16abc07737b0df4c65b4d6ff2f91ada3797deb333a0"
    ),
    "docs/evidence/poc-search-001/license-notice-inventory.json": (
        "8b80fa573a2674cb32fe08446683f5b3d05ce4721b6bcb018edec51cf9fbeb50"
    ),
    "docs/stage0/DORA_MVP1_STAGE0_OWNER_DECISIONS.md": (
        "6ad739370b6c0fd38cfd708426deff623759f69927fda5978b804b2ce0cc7a9a"
    ),
}
EXPECTED_UPSTREAM_FACTS = {
    "apiPom": {
        "observedSpdx": "Apache-2.0",
        "sha256": "2acd178df7ec7ac69e14a26c3d134b12a70dba8e942c8e2f6d112cdbac4ddd61",
        "url": (
            "https://repo1.maven.org/maven2/com/google/devtools/ksp/"
            "symbol-processing-api/2.3.11/symbol-processing-api-2.3.11.pom"
        ),
    },
    "implementationPom": {
        "observedSpdx": "Apache-2.0",
        "sha256": "c7bef317cf96ea74894b2738c20a50f75ecaffd382c1e37fec6728c5f5d4220d",
        "url": (
            "https://plugins.gradle.org/m2/com/google/devtools/ksp/"
            "symbol-processing-gradle-plugin/2.3.11/"
            "symbol-processing-gradle-plugin-2.3.11.pom"
        ),
    },
    "license": {
        "bytes": 11398,
        "gitBlobSha1": "9c308d958bf91eafe83f66106ca692ff414a4965",
        "observedSpdx": "Apache-2.0",
        "sha256": "b1febe6399dffb10d19d35e7663ab16300c93cb0476a94115df1cc0097a8ffd8",
        "url": "https://github.com/google/ksp/blob/2.3.11/LICENSE",
    },
    "pluginMarkerPom": {
        "observedSpdx": "Apache-2.0",
        "sha256": "1663b2590469e60ac9b225b27e838a3db8d762e1f459fe81e0988b471f4e0966",
        "url": (
            "https://plugins.gradle.org/m2/com/google/devtools/ksp/"
            "com.google.devtools.ksp.gradle.plugin/2.3.11/"
            "com.google.devtools.ksp.gradle.plugin-2.3.11.pom"
        ),
    },
    "release": {
        "publishedOn": "2026-08-03",
        "tagCommit": "c44fd9a91192679e07a1d905dda022796e32bbbe",
        "url": "https://github.com/google/ksp/releases/tag/2.3.11",
        "version": "2.3.11",
    },
    "securitySnapshot": {
        "checkedOn": "2026-08-18",
        "globalMavenAdvisoryCount": 0,
        "repositorySecurityAdvisoryCount": 0,
        "securityPolicyAtTag": "NOT_PRESENT",
    },
}


class InventoryError(RuntimeError):
    """Raised when the inventory cannot be generated reproducibly."""


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _strict_json_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    value: dict[str, Any] = {}
    for key, item in pairs:
        if key in value:
            raise InventoryError(f"Duplicate JSON key in build-tool overlay: {key}")
        value[key] = item
    return value


def read_json_strict(path: Path) -> dict[str, Any]:
    try:
        value = json.loads(path.read_text(encoding="utf-8"), object_pairs_hook=_strict_json_object)
    except json.JSONDecodeError as exc:
        raise InventoryError(f"Cannot parse build-tool overlay {path}: {exc}") from exc
    if not isinstance(value, dict):
        raise InventoryError(f"Build-tool overlay root must be an object: {path}")
    return value


def require(condition: bool, message: str) -> None:
    if not condition:
        raise InventoryError(message)


def require_exact_keys(value: Any, expected: set[str], label: str) -> dict[str, Any]:
    require(isinstance(value, dict), f"{label} must be an object")
    actual = set(value)
    require(
        actual == expected,
        f"{label} keys mismatch; missing={sorted(expected - actual)}, extra={sorted(actual - expected)}",
    )
    return value


def require_sha256(value: Any, label: str) -> str:
    require(isinstance(value, str) and bool(SHA256_PATTERN.fullmatch(value)), f"Bad SHA-256: {label}")
    return value


def require_git_sha1(value: Any, label: str) -> str:
    require(isinstance(value, str) and bool(GIT_SHA1_PATTERN.fullmatch(value)), f"Bad Git SHA-1: {label}")
    return value


def require_sorted_unique_strings(value: Any, label: str) -> list[str]:
    require(
        isinstance(value, list)
        and bool(value)
        and all(isinstance(item, str) and item for item in value),
        f"{label} must be a non-empty string list",
    )
    require(value == sorted(set(value)), f"{label} must be sorted and duplicate-free")
    return value


def parse_lock_strict_text(text: str, label: str) -> dict[str, tuple[str, ...]]:
    require(text.endswith("\n"), f"{label} must end with LF")
    require("\r" not in text, f"{label} must use LF line endings")
    components: dict[str, tuple[str, ...]] = {}
    data_coordinates: list[str] = []
    for line_number, raw_line in enumerate(text.splitlines(), start=1):
        if not raw_line or raw_line.startswith("#"):
            continue
        require(raw_line == raw_line.strip(), f"{label}:{line_number} has surrounding whitespace")
        require(raw_line.count("=") == 1, f"{label}:{line_number} is not a lock selection")
        coordinate, raw_configurations = raw_line.split("=", 1)
        require(
            coordinate == "empty" or bool(COORDINATE_PATTERN.fullmatch(coordinate)),
            f"{label}:{line_number} has malformed coordinate {coordinate!r}",
        )
        require(coordinate not in components, f"{label}:{line_number} duplicates {coordinate}")
        configurations = raw_configurations.split(",")
        require(
            bool(configurations)
            and all(bool(CONFIGURATION_PATTERN.fullmatch(item)) for item in configurations),
            f"{label}:{line_number} has malformed configuration list",
        )
        require(
            configurations == sorted(set(configurations)),
            f"{label}:{line_number} configurations are not sorted and duplicate-free",
        )
        components[coordinate] = tuple(configurations)
        data_coordinates.append(coordinate)
    require(bool(components), f"{label} has no dependency selections")
    expected_order = sorted(item for item in data_coordinates if item != "empty")
    if "empty" in components:
        expected_order.append("empty")
    require(data_coordinates == expected_order, f"{label} coordinates are not in canonical order")
    return components


def selected_projection(components: dict[str, tuple[str, ...]]) -> dict[str, tuple[str, ...]]:
    selected: dict[str, tuple[str, ...]] = {}
    for coordinate, configurations in components.items():
        scopes = tuple(sorted(set(configurations).intersection(LOCK_CONFIGURATIONS)))
        if scopes:
            selected[coordinate] = scopes
    return selected


def projection_coordinate_sort_key(coordinate: str) -> tuple[tuple[str, ...], str, str]:
    group, module, version = coordinate.split(":")
    # This explicit, locale-independent ordering preserves the frozen OD-13 66-row
    # fingerprint. Group segments sort hierarchically; within a module, a hyphenated
    # suffix sorts before the unsuffixed module (for example, collection-jvm before
    # collection). The terminal marker is ordered after the hyphen marker.
    module_key = module.replace("-", "\x00") + "\x01"
    return tuple(group.split(".")), module_key, version


def canonical_projection_text(components: dict[str, tuple[str, ...]]) -> str:
    projection = selected_projection(components)
    return "".join(
        f"{coordinate}={','.join(projection[coordinate])}\n"
        for coordinate in sorted(projection, key=projection_coordinate_sort_key)
    )


def lock_pairs(components: dict[str, tuple[str, ...]]) -> set[str]:
    return {
        f"{coordinate}|{configuration}"
        for coordinate, configurations in components.items()
        for configuration in configurations
    }


def git_bytes(repo_root: Path, *args: str) -> bytes:
    completed = subprocess.run(
        ["git", "-C", str(repo_root), *args],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if completed.returncode != 0:
        detail = completed.stderr.decode("utf-8", errors="replace").strip()
        raise InventoryError(f"Git command failed ({' '.join(args)}): {detail}")
    return completed.stdout


def git_text(repo_root: Path, *args: str) -> str:
    return git_bytes(repo_root, *args).decode("utf-8").strip()


def require_ancestor(repo_root: Path, ancestor: str, descendant: str, label: str) -> None:
    completed = subprocess.run(
        ["git", "-C", str(repo_root), "merge-base", "--is-ancestor", ancestor, descendant],
        check=False,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    require(completed.returncode == 0, f"{label} is not an ancestor of {descendant}")


def validate_overlay_schema(profile: dict[str, Any]) -> None:
    require_exact_keys(
        profile,
        {
            "schemaVersion",
            "profileId",
            "pocId",
            "classification",
            "maintenanceAuthorization",
            "maintenanceBase",
            "historicalEvidenceAnchor",
            "currentFiles",
            "evaluatedProjection",
            "roomProcessorApiInvariant",
            "pairDelta",
            "upstreamFacts",
            "nonClaims",
        },
        "overlay",
    )
    require(profile["schemaVersion"] == 1, "Build-tool overlay schema version drift")
    require(profile["profileId"] == OVERLAY_PROFILE_ID, "Build-tool overlay profile id drift")
    require(profile["pocId"] == POC_ID, "Build-tool overlay PoC id drift")
    require(profile["classification"] == "BUILD_TOOL_ONLY_LOCK_OVERLAY", "Build-tool overlay classification drift")
    require(profile["maintenanceAuthorization"] == OVERLAY_AUTHORIZATION, "Build-tool overlay authorization drift")

    base = require_exact_keys(
        profile["maintenanceBase"],
        {"commit", "tree", "catalog"},
        "maintenanceBase",
    )
    require_git_sha1(base["commit"], "maintenanceBase.commit")
    require_git_sha1(base["tree"], "maintenanceBase.tree")
    require(
        base["commit"] == MAINTENANCE_BASE_COMMIT and base["tree"] == MAINTENANCE_BASE_TREE,
        "Maintenance base identity drift",
    )
    base_catalog = require_exact_keys(
        base["catalog"],
        {"path", "gitBlobSha1", "sha256"},
        "maintenanceBase.catalog",
    )
    require(base_catalog["path"] == CATALOG_RELATIVE_PATH.as_posix(), "Maintenance catalog path drift")
    require_git_sha1(base_catalog["gitBlobSha1"], "maintenanceBase.catalog.gitBlobSha1")
    require_sha256(base_catalog["sha256"], "maintenanceBase.catalog.sha256")
    require(
        base_catalog["gitBlobSha1"] == MAINTENANCE_CATALOG_BLOB
        and base_catalog["sha256"] == MAINTENANCE_CATALOG_SHA256,
        "Maintenance-base catalog identity drift",
    )

    anchor = require_exact_keys(
        profile["historicalEvidenceAnchor"],
        {"commit", "tree", "lock"},
        "historicalEvidenceAnchor",
    )
    require_git_sha1(anchor["commit"], "historicalEvidenceAnchor.commit")
    require_git_sha1(anchor["tree"], "historicalEvidenceAnchor.tree")
    require(
        anchor["commit"] == HISTORICAL_EVIDENCE_COMMIT
        and anchor["tree"] == HISTORICAL_EVIDENCE_TREE,
        "Historical evidence anchor identity drift",
    )
    anchor_lock = require_exact_keys(
        anchor["lock"],
        {"path", "gitBlobSha1", "sha256"},
        "historicalEvidenceAnchor.lock",
    )
    require(anchor_lock["path"] == LOCK_RELATIVE_PATH.as_posix(), "Historical lock path drift")
    require_git_sha1(anchor_lock["gitBlobSha1"], "historicalEvidenceAnchor.lock.gitBlobSha1")
    require_sha256(anchor_lock["sha256"], "historicalEvidenceAnchor.lock.sha256")
    require(
        anchor_lock["gitBlobSha1"] == HISTORICAL_LOCK_BLOB
        and anchor_lock["sha256"] == HISTORICAL_LOCK_SHA256,
        "Historical lock identity drift",
    )

    current = require_exact_keys(profile["currentFiles"], {"catalog", "lock"}, "currentFiles")
    for name, expected_path in (("catalog", CATALOG_RELATIVE_PATH), ("lock", LOCK_RELATIVE_PATH)):
        item = require_exact_keys(current[name], {"path", "sha256"}, f"currentFiles.{name}")
        require(item["path"] == expected_path.as_posix(), f"Current {name} path drift")
        require_sha256(item["sha256"], f"currentFiles.{name}.sha256")
    require(
        current["catalog"]["sha256"] == CURRENT_CATALOG_SHA256
        and current["lock"]["sha256"] == CURRENT_LOCK_SHA256,
        "Current catalog or lock identity drift",
    )

    projection = require_exact_keys(
        profile["evaluatedProjection"],
        {"configurations", "componentCount", "canonicalEncoding", "sha256", "immutableEvidence"},
        "evaluatedProjection",
    )
    require(projection["configurations"] == list(LOCK_CONFIGURATIONS), "Evaluated configuration order drift")
    require(projection["componentCount"] == 66, "Evaluated component count drift")
    require(projection["canonicalEncoding"] == PROJECTION_ENCODING, "Projection encoding drift")
    require_sha256(projection["sha256"], "evaluatedProjection.sha256")
    require(projection["sha256"] == EVALUATED_PROJECTION_SHA256, "Projection SHA-256 drift")
    evidence = projection["immutableEvidence"]
    require(isinstance(evidence, list) and bool(evidence), "immutableEvidence must be a non-empty list")
    expected_evidence_paths = [
        "docs/evidence/poc-search-001/assessments/review-2026-08-11-v4/benchmark-result.json",
        "docs/evidence/poc-search-001/assessments/review-2026-08-11-v5/benchmark-result.json",
        "docs/evidence/poc-search-001/dependency-inventory.json",
        "docs/evidence/poc-search-001/evidence-index.json",
        "docs/evidence/poc-search-001/ip-evaluation.json",
        "docs/evidence/poc-search-001/ip-stage0-evaluation-review.md",
        "docs/evidence/poc-search-001/license-notice-inventory.json",
        "docs/stage0/DORA_MVP1_STAGE0_OWNER_DECISIONS.md",
    ]
    actual_evidence_paths: list[str] = []
    for index, item in enumerate(evidence):
        entry = require_exact_keys(item, {"path", "sha256"}, f"immutableEvidence[{index}]")
        require(isinstance(entry["path"], str) and entry["path"], f"Bad immutable evidence path at {index}")
        require_sha256(entry["sha256"], f"immutableEvidence[{index}].sha256")
        actual_evidence_paths.append(entry["path"])
    require(actual_evidence_paths == expected_evidence_paths, "Immutable evidence locator order or scope drift")
    require(
        {item["path"]: item["sha256"] for item in evidence} == IMMUTABLE_EVIDENCE_SHA256,
        "Immutable evidence digest set drift",
    )

    room = require_exact_keys(
        profile["roomProcessorApiInvariant"],
        {"coordinate", "evaluatedConfigurations", "kspPluginVersionSubstitutionAllowed"},
        "roomProcessorApiInvariant",
    )
    require(room["coordinate"] == ROOM_PROCESSOR_API, "Room processor API coordinate drift")
    require(
        room["evaluatedConfigurations"]
        == ["_agp_internal_benchmark_kspClasspath", "_agp_internal_debug_kspClasspath"],
        "Room processor API evaluated scopes drift",
    )
    require(room["kspPluginVersionSubstitutionAllowed"] is False, "Room processor API substitution must be forbidden")

    delta = require_exact_keys(
        profile["pairDelta"],
        {"pluginApiReplacement", "emptyProcessorClasspaths", "addedSelectionCount", "removedSelectionCount"},
        "pairDelta",
    )
    replacement = require_exact_keys(
        delta["pluginApiReplacement"],
        {"fromCoordinate", "toCoordinate", "configurations"},
        "pairDelta.pluginApiReplacement",
    )
    require(replacement["fromCoordinate"] == KSP_PLUGIN_API_OLD, "Old KSP plugin API coordinate drift")
    require(replacement["toCoordinate"] == KSP_PLUGIN_API_NEW, "New KSP plugin API coordinate drift")
    require_sorted_unique_strings(replacement["configurations"], "pluginApiReplacement.configurations")
    require(
        replacement["configurations"] == list(KSP_PLUGIN_CONFIGURATIONS),
        "KSP plugin API configuration set drift",
    )
    emptied = require_exact_keys(
        delta["emptyProcessorClasspaths"],
        {"configurations", "removedCoordinates", "replacementCoordinate"},
        "pairDelta.emptyProcessorClasspaths",
    )
    require_sorted_unique_strings(emptied["configurations"], "emptyProcessorClasspaths.configurations")
    require_sorted_unique_strings(emptied["removedCoordinates"], "emptyProcessorClasspaths.removedCoordinates")
    require(
        emptied["configurations"] == list(EMPTY_PROCESSOR_CONFIGURATIONS)
        and emptied["removedCoordinates"] == list(EMPTY_PROCESSOR_REMOVED_COORDINATES),
        "Empty processor classpath delta drift",
    )
    require(emptied["replacementCoordinate"] == "empty", "Empty processor replacement coordinate drift")
    require(
        isinstance(delta["addedSelectionCount"], int) and delta["addedSelectionCount"] > 0,
        "Bad added selection count",
    )
    require(
        isinstance(delta["removedSelectionCount"], int)
        and delta["removedSelectionCount"] > 0,
        "Bad removed selection count",
    )
    require(
        delta["addedSelectionCount"] == 13 and delta["removedSelectionCount"] == 149,
        "Exact pair-delta counts drift",
    )

    facts = require_exact_keys(
        profile["upstreamFacts"],
        {"release", "license", "pluginMarkerPom", "implementationPom", "apiPom", "securitySnapshot"},
        "upstreamFacts",
    )
    release = require_exact_keys(
        facts["release"],
        {"version", "url", "tagCommit", "publishedOn"},
        "upstreamFacts.release",
    )
    require(release["version"] == "2.3.11", "Upstream release version drift")
    require(release["url"] == "https://github.com/google/ksp/releases/tag/2.3.11", "Upstream release URL drift")
    require_git_sha1(release["tagCommit"], "upstreamFacts.release.tagCommit")
    require(release["publishedOn"] == "2026-08-03", "Upstream release date drift")
    license_fact = require_exact_keys(
        facts["license"],
        {"observedSpdx", "url", "gitBlobSha1", "bytes", "sha256"},
        "upstreamFacts.license",
    )
    require(license_fact["observedSpdx"] == "Apache-2.0", "Observed KSP license metadata drift")
    require_git_sha1(license_fact["gitBlobSha1"], "upstreamFacts.license.gitBlobSha1")
    require(
        isinstance(license_fact["bytes"], int) and license_fact["bytes"] > 0,
        "Observed KSP license byte count missing",
    )
    require_sha256(license_fact["sha256"], "upstreamFacts.license.sha256")
    for name in ("pluginMarkerPom", "implementationPom", "apiPom"):
        pom = require_exact_keys(facts[name], {"url", "observedSpdx", "sha256"}, f"upstreamFacts.{name}")
        require(pom["observedSpdx"] == "Apache-2.0", f"Observed SPDX drift for {name}")
        require(isinstance(pom["url"], str) and pom["url"].startswith("https://"), f"Bad URL for {name}")
        require_sha256(pom["sha256"], f"upstreamFacts.{name}.sha256")
    security = require_exact_keys(
        facts["securitySnapshot"],
        {"checkedOn", "repositorySecurityAdvisoryCount", "globalMavenAdvisoryCount", "securityPolicyAtTag"},
        "upstreamFacts.securitySnapshot",
    )
    require(
        security
        == {
            "checkedOn": "2026-08-18",
            "repositorySecurityAdvisoryCount": 0,
            "globalMavenAdvisoryCount": 0,
            "securityPolicyAtTag": "NOT_PRESENT",
        },
        "Observed security snapshot drift",
    )
    require(facts == EXPECTED_UPSTREAM_FACTS, "Exact observed upstream fact set drift")
    require(profile["nonClaims"] == EXPECTED_NON_CLAIMS, "Build-tool overlay non-claims drift")


def expected_pair_delta(profile: dict[str, Any]) -> tuple[set[str], set[str]]:
    delta = profile["pairDelta"]
    replacement = delta["pluginApiReplacement"]
    emptied = delta["emptyProcessorClasspaths"]
    removed = {
        f"{replacement['fromCoordinate']}|{configuration}"
        for configuration in replacement["configurations"]
    }
    removed.update(
        f"{coordinate}|{configuration}"
        for coordinate in emptied["removedCoordinates"]
        for configuration in emptied["configurations"]
    )
    added = {
        f"{replacement['toCoordinate']}|{configuration}"
        for configuration in replacement["configurations"]
    }
    added.update(
        f"{emptied['replacementCoordinate']}|{configuration}"
        for configuration in emptied["configurations"]
    )
    require(len(added) == delta["addedSelectionCount"], "Recorded added selection count mismatch")
    require(len(removed) == delta["removedSelectionCount"], "Recorded removed selection count mismatch")
    return added, removed


def validate_catalog_transition(historical_text: str, current_text: str) -> None:
    expected = historical_text.replace('ksp = "2.3.9"', 'ksp = "2.3.11"', 1)
    require(expected != historical_text, "Historical catalog lacks the exact KSP 2.3.9 pin")
    require('ksp = "2.3.9"' not in expected, "Historical catalog has duplicate KSP pins")
    require(current_text == expected, "Catalog changed outside the exact KSP 2.3.9 -> 2.3.11 pin")


def validate_lock_transition(
    profile: dict[str, Any],
    historical_lock: dict[str, tuple[str, ...]],
    current_lock: dict[str, tuple[str, ...]],
) -> None:
    expected_added, expected_removed = expected_pair_delta(profile)
    historical_pairs = lock_pairs(historical_lock)
    current_pairs = lock_pairs(current_lock)
    require(current_pairs - historical_pairs == expected_added, "Unexpected added lock selection")
    require(historical_pairs - current_pairs == expected_removed, "Unexpected removed lock selection")

    historical_projection_text = canonical_projection_text(historical_lock)
    current_projection_text = canonical_projection_text(current_lock)
    projection = profile["evaluatedProjection"]
    require(historical_projection_text == current_projection_text, "OD-13 evaluated projection changed")
    require(
        len(selected_projection(current_lock)) == projection["componentCount"],
        "OD-13 evaluated projection component count changed",
    )
    require(
        sha256_bytes(current_projection_text.encode("utf-8")) == projection["sha256"],
        "OD-13 evaluated projection fingerprint changed",
    )

    room_scopes = set(current_lock.get(ROOM_PROCESSOR_API, ())).intersection(LOCK_CONFIGURATIONS)
    require(
        room_scopes == set(profile["roomProcessorApiInvariant"]["evaluatedConfigurations"]),
        "Room processor API evaluated configuration scope changed",
    )
    require(KSP_PLUGIN_API_OLD not in current_lock, "Stale KSP 2.3.9 plugin API remains in the final lock")
    replacement = profile["pairDelta"]["pluginApiReplacement"]
    require(
        set(current_lock.get(KSP_PLUGIN_API_NEW, ())) == set(replacement["configurations"]),
        "KSP 2.3.11 plugin API configuration scope drift",
    )
    require(
        not set(current_lock[KSP_PLUGIN_API_NEW]).intersection(LOCK_CONFIGURATIONS),
        "KSP 2.3.11 build plugin API leaked into the OD-13 evaluated projection",
    )


def validate_immutable_evidence(
    profile: dict[str, Any],
    immutable_digests: dict[str, str],
) -> None:
    for item in profile["evaluatedProjection"]["immutableEvidence"]:
        path = item["path"]
        require(immutable_digests.get(path) == item["sha256"], f"Immutable OD-13 evidence changed: {path}")


def validate_overlay_model(
    profile: dict[str, Any],
    historical_lock_bytes: bytes,
    current_lock_bytes: bytes,
    historical_catalog_bytes: bytes,
    current_catalog_bytes: bytes,
    immutable_digests: dict[str, str],
) -> None:
    validate_overlay_schema(profile)
    anchor = profile["historicalEvidenceAnchor"]
    current = profile["currentFiles"]
    require(sha256_bytes(historical_lock_bytes) == anchor["lock"]["sha256"], "Historical lock SHA mismatch")
    require(sha256_bytes(current_lock_bytes) == current["lock"]["sha256"], "Current lock SHA mismatch")
    require(
        sha256_bytes(historical_catalog_bytes) == profile["maintenanceBase"]["catalog"]["sha256"],
        "Maintenance-base catalog SHA mismatch",
    )
    require(sha256_bytes(current_catalog_bytes) == current["catalog"]["sha256"], "Current catalog SHA mismatch")

    try:
        historical_lock_text = historical_lock_bytes.decode("utf-8")
        current_lock_text = current_lock_bytes.decode("utf-8")
        historical_catalog_text = historical_catalog_bytes.decode("utf-8")
        current_catalog_text = current_catalog_bytes.decode("utf-8")
    except UnicodeDecodeError as exc:
        raise InventoryError(f"Overlay input is not UTF-8: {exc}") from exc
    validate_catalog_transition(historical_catalog_text, current_catalog_text)

    historical_lock = parse_lock_strict_text(historical_lock_text, "historical lock")
    current_lock = parse_lock_strict_text(current_lock_text, "current lock")
    validate_lock_transition(profile, historical_lock, current_lock)
    validate_immutable_evidence(profile, immutable_digests)


def validate_build_tool_overlay_profile(
    repo_root: Path,
    profile: dict[str, Any],
    inventory_path: Path | None = None,
) -> dict[str, Any]:
    validate_overlay_schema(profile)

    anchor = profile["historicalEvidenceAnchor"]
    base = profile["maintenanceBase"]
    head = git_text(repo_root, "rev-parse", "HEAD")
    require(
        git_text(repo_root, "rev-parse", f"{anchor['commit']}^{{commit}}") == anchor["commit"],
        "Historical anchor commit is unavailable",
    )
    require(
        git_text(repo_root, "rev-parse", f"{anchor['commit']}^{{tree}}") == anchor["tree"],
        "Historical anchor tree mismatch",
    )
    require(
        git_text(repo_root, "rev-parse", f"{base['commit']}^{{commit}}") == base["commit"],
        "Maintenance base commit is unavailable",
    )
    require(
        git_text(repo_root, "rev-parse", f"{base['commit']}^{{tree}}") == base["tree"],
        "Maintenance base tree mismatch",
    )
    require_ancestor(repo_root, anchor["commit"], base["commit"], "Historical evidence anchor")
    require_ancestor(repo_root, base["commit"], head, "Maintenance base")

    require(
        git_text(repo_root, "rev-parse", f"{anchor['commit']}:{anchor['lock']['path']}")
        == anchor["lock"]["gitBlobSha1"],
        "Historical lock Git blob mismatch",
    )
    require(
        git_text(repo_root, "rev-parse", f"{base['commit']}:{base['catalog']['path']}")
        == base["catalog"]["gitBlobSha1"],
        "Maintenance-base catalog Git blob mismatch",
    )
    historical_catalog = git_bytes(repo_root, "show", f"{base['commit']}:{base['catalog']['path']}")
    historical_lock = git_bytes(repo_root, "show", f"{anchor['commit']}:{anchor['lock']['path']}")
    current_catalog = (repo_root / CATALOG_RELATIVE_PATH).read_bytes()
    current_lock = (repo_root / LOCK_RELATIVE_PATH).read_bytes()
    immutable_digests = {
        item["path"]: sha256_file(repo_root / item["path"])
        for item in profile["evaluatedProjection"]["immutableEvidence"]
    }
    validate_overlay_model(
        profile,
        historical_lock,
        current_lock,
        historical_catalog,
        current_catalog,
        immutable_digests,
    )
    if inventory_path is not None:
        dependency_path = str(INVENTORY_RELATIVE_PATH.as_posix())
        expected_inventory_sha = next(
            item["sha256"]
            for item in profile["evaluatedProjection"]["immutableEvidence"]
            if item["path"] == dependency_path
        )
        require(sha256_file(inventory_path) == expected_inventory_sha, "Reviewed dependency inventory digest drift")
    return profile


def validate_build_tool_overlay(
    repo_root: Path,
    inventory_path: Path | None = None,
) -> dict[str, Any] | None:
    profile_path = repo_root / OVERLAY_RELATIVE_PATH
    if not profile_path.is_file():
        return None
    profile = read_json_strict(profile_path)
    require(
        profile_path.read_bytes() == canonical_json(profile).encode("utf-8"),
        "Build-tool overlay JSON is not canonical",
    )
    return validate_build_tool_overlay_profile(repo_root, profile, inventory_path)


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


def inventory_for_reviewed_output(
    generated: dict[str, Any],
    overlay: dict[str, Any] | None,
) -> dict[str, Any]:
    if overlay is None:
        return generated
    rendered = dict(generated)
    rendered["lock"] = dict(generated["lock"])
    rendered["lock"]["sha256"] = overlay["historicalEvidenceAnchor"]["lock"]["sha256"]
    return rendered


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
    reviewed_inventory_path = repo_root / INVENTORY_RELATIVE_PATH
    try:
        overlay = validate_build_tool_overlay(repo_root, reviewed_inventory_path)
        generated = build_inventory(repo_root, args.gradle_user_home.resolve())
        is_reviewed_output = output_path.resolve() == reviewed_inventory_path.resolve()
        if overlay is not None and is_reviewed_output and not args.check:
            raise InventoryError(
                "The active build-tool lock overlay forbids overwriting the immutable reviewed "
                "dependency inventory; write any current-lock diagnostic to a different path"
            )
        candidate = inventory_for_reviewed_output(
            generated,
            overlay if is_reviewed_output else None,
        )
        rendered = canonical_json(candidate)
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
