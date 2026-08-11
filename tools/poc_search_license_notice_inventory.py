#!/usr/bin/env python3
"""Generate or verify exact license/NOTICE evidence for POC-SEARCH-001."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from typing import Any


POC_ID = "POC-SEARCH-001"
SCHEMA_VERSION = 1
ARCHIVE_SUFFIXES = {".aar", ".jar"}

PARENT_LICENSE_EVIDENCE = {
    "com.google.guava:failureaccess:1.0.2": (
        "com.google.guava:guava-parent:26.0-android",
        "Apache-2.0",
    ),
    "com.google.guava:guava:33.2.1-jre": (
        "com.google.guava:guava-parent:33.2.1-jre",
        "Apache-2.0",
    ),
    "com.google.guava:listenablefuture:1.0": (
        "com.google.guava:guava-parent:26.0-android",
        "Apache-2.0",
    ),
    "com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava": (
        "com.google.guava:guava-parent:26.0-android",
        "Apache-2.0",
    ),
    "org.hamcrest:hamcrest-core:1.3": (
        "org.hamcrest:hamcrest-parent:1.3",
        "BSD-3-Clause",
    ),
}

POM_HEADER_LICENSE_EVIDENCE = {
    "com.google.auto.value:auto-value-annotations:1.6.3": "Apache-2.0",
    "commons-codec:commons-codec:1.15": "Apache-2.0",
}

UPSTREAM_TAG_LICENSE_EVIDENCE = {
    "com.google.auto.value:auto-value-annotations:1.6.3": {
        "kind": "upstream-release-tag-license",
        "repository": "google/auto",
        "tag": "auto-value-1.6.3",
        "filename": "LICENSE.txt",
        "sha256": "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30",
        "sourceUrl": (
            "https://raw.githubusercontent.com/google/auto/"
            "auto-value-1.6.3/LICENSE.txt"
        ),
    }
}

OBLIGATIONS = {
    "Apache-2.0": {
        "evaluationDisposition": "compatible_with_internal_stage0_evaluation",
        "distributionReview": (
            "Retain the license; preserve attributable NOTICE content when supplied; "
            "mark modified files when applicable; no trademark permission is inferred."
        ),
    },
    "BSD-3-Clause": {
        "evaluationDisposition": "compatible_with_internal_stage0_evaluation",
        "distributionReview": (
            "Retain copyright, conditions, and disclaimer; do not imply endorsement."
        ),
    },
    "BSD-2-Clause": {
        "evaluationDisposition": "compatible_with_internal_stage0_evaluation",
        "distributionReview": "Retain copyright, conditions, and disclaimer.",
    },
    "EPL-1.0": {
        "evaluationDisposition": "test_scope_only_reviewed_for_stage0",
        "distributionReview": (
            "Production Legal must review source-availability and modification obligations "
            "before any distribution."
        ),
    },
    "MIT": {
        "evaluationDisposition": "compatible_with_internal_stage0_evaluation",
        "distributionReview": "Retain the copyright and license notice in distributed copies.",
    },
}


class ReviewEvidenceError(RuntimeError):
    """Raised when exact artifact review evidence cannot be derived."""


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def canonical_json(value: dict[str, Any]) -> str:
    return json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n"


def child_text(element: ET.Element, name: str) -> str | None:
    child = element.find(f"{{*}}{name}")
    if child is None or child.text is None:
        return None
    text = child.text.strip()
    return text or None


def declared_license_names(pom: Path) -> list[str]:
    root = ET.parse(pom).getroot()
    return [
        value
        for license_element in root.findall(".//{*}licenses/{*}license")
        if (value := child_text(license_element, "name")) is not None
    ]


def spdx_id(name: str) -> str:
    normalized = " ".join(name.lower().replace("_", " ").split())
    if "apache" in normalized and ("2.0" in normalized or normalized.endswith(" 2")):
        return "Apache-2.0"
    if normalized in {"bsd-3-clause", "new bsd license"}:
        return "BSD-3-Clause"
    if normalized == "eclipse public license 1.0":
        return "EPL-1.0"
    if "mit" in normalized:
        return "MIT"
    raise ReviewEvidenceError(f"Unmapped declared license name: {name}")


def embedded_license_spdx(data: bytes) -> str:
    text = " ".join(data.decode("utf-8", errors="replace").lower().split())
    if "apache license" in text and "version 2.0" in text:
        return "Apache-2.0"
    if "eclipse public license - v 1.0" in text or "eclipse public license 1.0" in text:
        return "EPL-1.0"
    if "permission is hereby granted, free of charge" in text:
        return "MIT"
    if (
        "redistribution and use in source and binary forms" in text
        and "redistributions of source code must retain" in text
        and "redistributions in binary form must reproduce" in text
    ):
        if "neither the name" in text:
            return "BSD-3-Clause"
        return "BSD-2-Clause"
    raise ReviewEvidenceError(
        f"Unmapped embedded license text ({sha256_bytes(data)})"
    )


def coordinate_path(cache_root: Path, coordinate: str) -> Path:
    group, module, version = coordinate.split(":")
    path = cache_root / group / module / version
    if not path.is_dir():
        raise ReviewEvidenceError(f"Gradle cache entry is missing: {path}")
    return path


def exact_cached_file(root: Path, filename: str, expected_sha256: str) -> Path:
    matches = [
        path
        for path in root.rglob(filename)
        if path.is_file() and sha256_file(path) == expected_sha256
    ]
    if len(matches) != 1:
        raise ReviewEvidenceError(
            f"Expected one exact cached {filename} ({expected_sha256}), found {len(matches)}"
        )
    return matches[0]


def maven_url(coordinate: str, filename: str) -> str:
    group, module, version = coordinate.split(":")
    return (
        "https://repo1.maven.org/maven2/"
        f"{group.replace('.', '/')}/{module}/{version}/{filename}"
    )


def parent_evidence(cache_root: Path, coordinate: str, expected_spdx: str) -> dict[str, Any]:
    root = coordinate_path(cache_root, coordinate)
    poms = sorted(root.rglob("*.pom"))
    if len(poms) != 1:
        raise ReviewEvidenceError(f"Expected one cached parent POM for {coordinate}")
    pom = poms[0]
    ids = sorted({spdx_id(name) for name in declared_license_names(pom)})
    if ids != [expected_spdx]:
        raise ReviewEvidenceError(f"Parent POM license drift for {coordinate}: {ids}")
    return {
        "kind": "parent-pom-declaration",
        "coordinate": coordinate,
        "filename": pom.name,
        "sha256": sha256_file(pom),
        "sourceUrl": maven_url(coordinate, pom.name),
    }


def embedded_entries(path: Path) -> list[dict[str, Any]]:
    if path.suffix.lower() not in ARCHIVE_SUFFIXES:
        return []
    try:
        with zipfile.ZipFile(path) as archive:
            entries = []
            for name in sorted(archive.namelist()):
                base = Path(name).name.upper()
                if not base.startswith(("LICENSE", "NOTICE", "COPYING")):
                    continue
                data = archive.read(name)
                entries.append(
                    {
                        "entry": name,
                        "kind": "notice" if base.startswith("NOTICE") else "license",
                        "bytes": len(data),
                        "sha256": sha256_bytes(data),
                        **(
                            {"spdx": embedded_license_spdx(data)}
                            if not base.startswith("NOTICE")
                            else {}
                        ),
                    }
                )
            return entries
    except zipfile.BadZipFile as exc:
        raise ReviewEvidenceError(f"Cannot inspect archive {path}: {exc}") from exc


def component_review(
    cache_root: Path,
    component: dict[str, Any],
) -> dict[str, Any]:
    coordinate = component["coordinate"]
    component_root = coordinate_path(cache_root, coordinate)
    archive_reviews = []
    module_pom = None
    for artifact in component["artifacts"]:
        cached = exact_cached_file(component_root, artifact["filename"], artifact["sha256"])
        if artifact["kind"] == "pom":
            module_pom = cached
        if artifact["kind"] in {"aar", "jar"}:
            archive_reviews.append(
                {
                    "filename": artifact["filename"],
                    "sha256": artifact["sha256"],
                    "entries": embedded_entries(cached),
                }
            )
    if module_pom is None:
        raise ReviewEvidenceError(f"Exact module POM missing for {coordinate}")

    declared = component["declaredLicenses"]
    evidence: list[dict[str, Any]] = []
    if declared:
        ids = sorted({spdx_id(item["name"]) for item in declared})
        pom_artifact = next(item for item in component["artifacts"] if item["kind"] == "pom")
        evidence.append(
            {
                "kind": "module-pom-declaration",
                "filename": pom_artifact["filename"],
                "sha256": pom_artifact["sha256"],
                "sourceUrl": pom_artifact["sourceUrl"],
            }
        )
    elif coordinate in PARENT_LICENSE_EVIDENCE:
        parent_coordinate, expected_spdx = PARENT_LICENSE_EVIDENCE[coordinate]
        ids = [expected_spdx]
        evidence.append(parent_evidence(cache_root, parent_coordinate, expected_spdx))
    elif coordinate in POM_HEADER_LICENSE_EVIDENCE:
        expected_spdx = POM_HEADER_LICENSE_EVIDENCE[coordinate]
        text = module_pom.read_text(encoding="utf-8")
        if "Apache License, Version 2.0" not in text:
            raise ReviewEvidenceError(f"Expected Apache-2.0 POM header missing for {coordinate}")
        ids = [expected_spdx]
        pom_artifact = next(item for item in component["artifacts"] if item["kind"] == "pom")
        evidence.append(
            {
                "kind": "exact-module-pom-license-header",
                "filename": pom_artifact["filename"],
                "sha256": pom_artifact["sha256"],
                "sourceUrl": pom_artifact["sourceUrl"],
            }
        )
        if coordinate in UPSTREAM_TAG_LICENSE_EVIDENCE:
            evidence.append(UPSTREAM_TAG_LICENSE_EVIDENCE[coordinate])
    else:
        raise ReviewEvidenceError(f"No effective license evidence for {coordinate}")

    embedded_ids = {
        entry["spdx"]
        for archive in archive_reviews
        for entry in archive["entries"]
        if entry["kind"] == "license"
    }
    ids = sorted(set(ids) | embedded_ids)
    if any(identifier not in OBLIGATIONS for identifier in ids):
        raise ReviewEvidenceError(f"Unreviewed license obligation for {coordinate}: {ids}")
    return {
        "coordinate": coordinate,
        "scopes": component["scopes"],
        "artifactSha256": {
            item["filename"]: item["sha256"] for item in component["artifacts"]
        },
        "effectiveSpdx": ids,
        "licenseEvidence": evidence,
        "embeddedLicenseNoticeEntries": archive_reviews,
        "stage0EvaluationDisposition": "ACCEPTABLE_PENDING_OWNER_APPROVAL",
        "productionAdmission": "NOT_REVIEWED",
    }


def build_inventory(repo_root: Path, gradle_user_home: Path) -> dict[str, Any]:
    dependency_path = repo_root / "docs/evidence/poc-search-001/dependency-inventory.json"
    dependency = json.loads(dependency_path.read_text(encoding="utf-8"))
    cache_root = gradle_user_home / "caches/modules-2/files-2.1"
    components = [component_review(cache_root, item) for item in dependency["components"]]
    license_counts: dict[str, int] = {}
    license_entry_count = 0
    notice_count = 0
    for component in components:
        for identifier in component["effectiveSpdx"]:
            license_counts[identifier] = license_counts.get(identifier, 0) + 1
        for archive in component["embeddedLicenseNoticeEntries"]:
            license_entry_count += sum(
                1 for entry in archive["entries"] if entry["kind"] == "license"
            )
            notice_count += sum(1 for entry in archive["entries"] if entry["kind"] == "notice")
    return {
        "schemaVersion": SCHEMA_VERSION,
        "pocId": POC_ID,
        "scope": "Exact locked Stage 0 evaluation graph; no production admission",
        "sourceDependencyInventory": {
            "locator": "docs/evidence/poc-search-001/dependency-inventory.json",
            "sha256": sha256_file(dependency_path),
            "componentCount": dependency["componentCount"],
        },
        "reviewStatus": "EVIDENCE_COMPLETE",
        "stage0Use": "internal synthetic evaluation only",
        "productionLegal": "UNASSIGNED_BLOCKED",
        "components": components,
        "summary": {
            "componentCount": len(components),
            "componentsWithEffectiveLicense": len(components),
            "effectiveLicenseCounts": dict(sorted(license_counts.items())),
            "embeddedLicenseEntryCount": license_entry_count,
            "embeddedNoticeEntryCount": notice_count,
            "unresolvedLicenseCoordinates": [],
            "unresolvedLicenseFiles": [],
            "unresolvedNoticeFiles": [],
        },
        "obligations": OBLIGATIONS,
        "nonClaims": [
            "This engineering inventory is not legal advice.",
            "Owner approval is still required before EVALUATION_APPROVED.",
            "Production Legal and independent production Security review remain separate.",
            "No production redistribution or dependency admission is approved.",
        ],
    }


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
        default=Path("docs/evidence/poc-search-001/license-notice-inventory.json"),
    )
    parser.add_argument("--check", action="store_true")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    repo_root = args.root.resolve()
    output = args.output if args.output.is_absolute() else repo_root / args.output
    try:
        rendered = canonical_json(build_inventory(repo_root, args.gradle_user_home.resolve()))
    except (OSError, KeyError, ValueError, ET.ParseError, ReviewEvidenceError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    if args.check:
        if not output.is_file() or output.read_text(encoding="utf-8") != rendered:
            print("ERROR: license/NOTICE inventory is missing or stale", file=sys.stderr)
            return 1
        print(f"Validated {output.relative_to(repo_root)}")
        return 0
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(rendered, encoding="utf-8", newline="\n")
    print(f"Wrote {output.relative_to(repo_root)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
