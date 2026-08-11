#!/usr/bin/env python3
"""Fail closed unless POC-RECOVERY-001 receives complete, explicit execution authority."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
STAGE0 = ROOT / "docs" / "stage0"
EVIDENCE = ROOT / "docs" / "evidence" / "poc-recovery-001"


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def main() -> int:
    gate = read_json(STAGE0 / "poc-recovery-gate-set-stage0-v0.1.json")
    protocol = read_json(STAGE0 / "poc-recovery-protocol-stage0-v0.1.json")
    readiness = read_json(EVIDENCE / "readiness.json")
    roles = read_json(EVIDENCE / "review-roles.json")["roles"]
    provenance = read_json(EVIDENCE / "sqlite-platform-provenance.json")
    license_notice = read_json(EVIDENCE / "license-notice-inventory.json")

    require(gate["executionAllowed"] is True, "executionAllowed=false in the recovery Gate Set")
    require(protocol["executionAllowed"] is True, "executionAllowed=false in the recovery protocol")
    require(readiness["executionAllowed"] is True, "executionAllowed=false in readiness evidence")
    require(gate["status"] == "APPROVED_FOR_AUTHORIZED_EXECUTION", "Gate Set lacks approved execution status")
    authorization = gate["executionAuthorization"]
    require(authorization["status"] == "AUTHORIZED_BY_PROJECT_OWNER", "Project-owner execution authorization absent")
    require(authorization["authorizedBy"] == "Project owner", "Unexpected execution authorizer")
    require(bool(authorization["authorizedOn"]) and bool(authorization["authorizationRecord"]), "Execution authorization record incomplete")
    require(not authorization["blockers"], "Execution authorization still lists blockers")

    require(roles["stage0ProductIp"]["status"] == "APPROVED_FOR_EXACT_STAGE0_EVALUATION", "Product/IP package review absent")
    independent = roles["independentRecoveryEngineeringSecurity"]
    require(independent["status"] == "APPROVED_FOR_EXACT_RECOVERY_EXECUTION", "Independent recovery Engineering/Security approval absent")
    require(isinstance(independent["reviewer"], str) and bool(independent["reviewer"]), "Independent reviewer unassigned")
    require(independent["replacesProductionSecurity"] is False, "Recovery review cannot claim Production Security approval")
    require(license_notice["evaluationApproved"] is True, "Exact Stage 0 Product/IP evaluation approval absent")

    candidates = {candidate["id"]: candidate for candidate in protocol["candidates"]}
    stream = candidates["REC-STREAM-TINK"]
    micro = candidates["REC-MICROFILE-TINK"]
    require(stream["streamingCheckpointProof"]["status"] == "APPROVED", "Streaming public-API checkpoint proof absent")
    require(bool(stream["proposedTemplate"]["approvedConstructionPath"]), "Streaming public construction path absent")
    require(micro["aeadTemplate"]["status"] == "APPROVED" and bool(micro["aeadTemplate"]["name"]), "Microfile AEAD template absent")
    require(micro["manifest"]["status"] == "APPROVED" and bool(micro["manifest"]["exactEncoding"]), "Manifest protocol absent")
    require(protocol["keyProtocol"]["status"] == "APPROVED", "Key protocol absent")
    require(protocol["sqliteJournal"]["journalModeStatus"] == "APPROVED", "SQLite durability profile absent")
    require(all(mapping["status"] == "APPROVED" for mapping in protocol["hardKillCampaign"]["candidateBarrierMappings"].values()), "Hard-kill barrier mappings absent")

    environments = {item["id"]: item for item in provenance["phaseA"]["environments"]}
    require(environments["PINNED_API36_X86_64_EMULATOR"]["runtimeIdentity"]["freshRecoveryPreflightRequired"] is False, "Fresh emulator preflight absent")
    require(all(value is not None for value in environments["PHYSICAL_D2"]["requiredFreshRuntimeEvidence"].values()), "Fresh D2 SQLite preflight absent")
    require(readiness["harnessImplemented"] is True, "Recovery harness absent")
    require(readiness["runtimeDependencyAdded"] is True, "Exact future harness-resolved graph absent")
    require(readiness["blockers"] == [], "Readiness blockers remain")

    print("POC-RECOVERY-001 execution readiness passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, StopIteration, json.JSONDecodeError) as error:
        print(f"BLOCKED {error}", file=sys.stderr)
        raise SystemExit(1)
