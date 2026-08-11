#!/usr/bin/env python3
"""Fail closed unless POC-SEARCH-001 is authorized for a new measured campaign."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "docs" / "evidence" / "poc-search-001"
EXECUTION_WITHHELD_MESSAGE = (
    "Option B is approved, but measured execution is withheld pending explicit Stage 0 "
    "component/license/NOTICE approval, verified paired-harness evidence, confirmed physical "
    "D1-D3 availability, and a later Project owner execution authorization."
)


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def validate_gate_set(gate_set: dict[str, Any]) -> None:
    require(gate_set["gateSetVersion"] == "stage0-v0.2", "Gate-set version drift")
    require(gate_set["status"] == "APPROVED", "The machine-readable gate set is still a draft")
    require(gate_set["selectedOptionId"] == "B", "The approved prospective option is not B")
    require(gate_set["benchmarkExecutionAllowed"] is True, EXECUTION_WITHHELD_MESSAGE)
    require(
        gate_set["executionAuthorization"]["status"] == "AUTHORIZED_BY_PROJECT_OWNER",
        "The Project owner has not recorded a measured-execution authorization",
    )
    require(
        gate_set["executionAuthorization"]["authorizedBy"] == "Project owner"
        and isinstance(gate_set["executionAuthorization"]["authorizedOn"], str)
        and bool(gate_set["executionAuthorization"]["authorizedOn"])
        and gate_set["executionAuthorization"]["blockers"] == [],
        "Measured-execution authorization record is incomplete or still lists blockers",
    )
    require(
        gate_set["selectedOptionId"] == gate_set["selectedOption"]["id"] == "B",
        "Selected gate option does not match the frozen approved Option B contract",
    )
    require(
        isinstance(gate_set["approvedBy"], str) and bool(gate_set["approvedBy"]),
        "Gate-set approver is missing",
    )
    require(
        isinstance(gate_set["approvedOn"], str) and bool(gate_set["approvedOn"]),
        "Gate-set approval date is missing",
    )
    require(
        gate_set["historicalResultsAffected"] is False,
        "A prospective gate cannot reclassify historical runs",
    )


def main() -> int:
    index = read_json(EVIDENCE / "evidence-index.json")
    ip_evaluation = read_json(EVIDENCE / "ip-evaluation.json")
    gate_contract = index["gateContract"]
    require(gate_contract["complete"] is True, gate_contract["blocker"])
    require(
        gate_contract["version"] == "stage0-v0.2",
        "A new run requires the approved stage0-v0.2 storage/update contract",
    )
    require(gate_contract["status"] == "APPROVED", "The prospective gate contract is not approved")
    gate_set_path = ROOT / gate_contract["machineLocator"]
    gate_set = read_json(gate_set_path)
    validate_gate_set(gate_set)
    require(
        ip_evaluation["evaluationStatus"] == "EVALUATION_APPROVED",
        "Exact dependency and platform evaluation rights are not approved",
    )
    require(
        ip_evaluation["futureMeasuredExecution"] == "ALLOWED",
        "IP assessment still blocks measured execution",
    )
    require(
        ip_evaluation["dependencyInventory"]["status"] == "EVALUATION_APPROVED_STAGE0",
        "The exact locked dependency inventory has no completed Stage 0 review",
    )
    license_notice = read_json(EVIDENCE / "license-notice-inventory.json")
    require(
        license_notice["reviewStatus"] == "EVIDENCE_COMPLETE"
        and not license_notice["summary"]["unresolvedLicenseCoordinates"]
        and not license_notice["summary"]["unresolvedLicenseFiles"]
        and not license_notice["summary"]["unresolvedNoticeFiles"],
        "The exact license/NOTICE evidence is incomplete",
    )
    harness = read_json(EVIDENCE / "gate-v02-harness-readiness.json")
    require(
        harness["status"] == "VERIFIED" and harness["formalBenchmarkExecuted"] is False,
        "The stage0-v0.2 paired harness has not completed non-measurement verification",
    )
    devices = read_json(EVIDENCE / "device-availability-stage0-v0.2.json")
    require(
        devices["allRequiredPhysicalProfilesAvailable"] is True,
        "Physical D1, D2, and D3 availability is not confirmed",
    )
    assignments = ip_evaluation["reviewAssignments"]
    for role in ("product", "ipPolicy", "engineeringSecurity"):
        assignment = assignments[role]
        require(
            assignment["status"] == "ASSIGNED"
            and isinstance(assignment["reviewer"], str)
            and bool(assignment["reviewer"]),
            f"Named {role} Stage 0 reviewer is required",
        )
    require(
        assignments["ipPolicy"]["replacesProductionLegal"] is False
        and assignments["productionLegal"]["status"] == "UNASSIGNED_PRODUCTION_BLOCKED",
        "Stage 0 IP review must not claim production Legal approval",
    )
    require(
        assignments["engineeringSecurity"]["replacesIndependentProductionSecurityReview"] is False
        and assignments["productionSecurity"]["status"]
        == "INDEPENDENT_REVIEW_REQUIRED_BEFORE_PRODUCTION",
        "Stage 0 Engineering/Security review must not replace production Security review",
    )
    review_evidence = ROOT / ip_evaluation["reviewEvidenceLocator"]
    require(review_evidence.is_file(), "IP review evidence locator is missing")
    for artifact in ip_evaluation["platformArtifacts"]:
        require(artifact["licenseReviewState"] == "EVALUATION_APPROVED", f"{artifact['artifactId']} is not approved")
        require(artifact["evaluationRightsConfirmed"] is True, f"{artifact['artifactId']} rights are unconfirmed")
        require(isinstance(artifact["sha256"], str), f"{artifact['artifactId']} digest is missing")
    platform = {artifact["artifactId"]: artifact for artifact in ip_evaluation["platformArtifacts"]}
    system_image = platform["android-system-image-google-apis-x86_64-api36"]
    sqlite = platform["android-platform-sqlite"]
    require(
        sqlite["sha256"] == system_image["sha256"]
        and sqlite["digestScope"] == "containing-system-image"
        and sqlite["separateBinaryDigestRequiredForStage0"] is False,
        "Stage 0 SQLite provenance must use the approved containing-system-image boundary",
    )
    require(
        sqlite["runtimeIdentity"]
        == {
            "imageId": "system-images;android-36;google_apis;x86_64",
            "imageRevision": 7,
            "buildFingerprint": (
                "google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.F3/"
                "13894323:userdebug/dev-keys"
            ),
            "androidApi": 36,
            "abi": "x86_64",
            "sqliteVersion": "3.44.3",
        },
        "Stage 0 SQLite runtime identity is incomplete",
    )
    print("POC-SEARCH-001 measured-run readiness passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"BLOCKED {error}", file=sys.stderr)
        raise SystemExit(1)
