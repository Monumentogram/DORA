#!/usr/bin/env python3
"""Fail closed unless POC-SEARCH-001 is authorized for a new measured campaign."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "docs" / "evidence" / "poc-search-001"


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def main() -> int:
    index = read_json(EVIDENCE / "evidence-index.json")
    ip_evaluation = read_json(EVIDENCE / "ip-evaluation.json")
    require(index["gateContract"]["complete"] is True, index["gateContract"]["blocker"])
    require(
        index["gateContract"]["version"] != "stage0-v0.1",
        "A new run cannot reuse the incomplete stage0-v0.1 storage/update contract",
    )
    require(
        ip_evaluation["evaluationStatus"] == "EVALUATION_APPROVED",
        "Exact dependency and platform evaluation rights are not approved",
    )
    require(
        ip_evaluation["futureMeasuredExecution"] == "ALLOWED",
        "IP assessment still blocks measured execution",
    )
    require(
        all(isinstance(value, str) and value for value in ip_evaluation["reviewers"].values()),
        "Named Product/Legal/IP and Engineering/Security reviewers are required",
    )
    require(bool(ip_evaluation["reviewEvidenceLocator"]), "IP review evidence locator is required")
    for artifact in ip_evaluation["platformArtifacts"]:
        require(artifact["licenseReviewState"] == "EVALUATION_APPROVED", f"{artifact['artifactId']} is not approved")
        require(artifact["evaluationRightsConfirmed"] is True, f"{artifact['artifactId']} rights are unconfirmed")
        require(isinstance(artifact["sha256"], str), f"{artifact['artifactId']} digest is missing")
    print("POC-SEARCH-001 measured-run readiness passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"BLOCKED {error}", file=sys.stderr)
        raise SystemExit(1)
