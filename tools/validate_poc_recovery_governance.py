#!/usr/bin/env python3
"""Validate the governance-only POC-RECOVERY-001 package without executing a PoC."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
STAGE0 = ROOT / "docs" / "stage0"
EVIDENCE = ROOT / "docs" / "evidence" / "poc-recovery-001"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def read_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"Missing required JSON: {path.relative_to(ROOT)}")
    return json.loads(path.read_text(encoding="utf-8"))


def read_text(path: Path) -> str:
    require(path.is_file(), f"Missing required document: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def validate_gate_and_protocol() -> None:
    gate = read_json(STAGE0 / "poc-recovery-gate-set-stage0-v0.1.json")
    protocol = read_json(STAGE0 / "poc-recovery-protocol-stage0-v0.1.json")

    require(gate["pocId"] == protocol["pocId"] == "POC-RECOVERY-001", "PoC ID drift")
    require(gate["gateSetVersion"] == "poc-recovery-stage0-v0.1", "Gate-set version drift")
    require(gate["status"] == "PROPOSED_REVIEW_REQUIRED", "Gate Set must remain review-pending")
    require(gate["ownerConstraintsFrozen"] is True, "Owner constraints must be frozen")
    require(gate["executionAllowed"] is False, "Recovery execution must remain disabled")
    require(protocol["executionAllowed"] is False, "Protocol execution must remain disabled")
    require(
        gate["executionAuthorization"]["status"]
        == "WITHHELD_PENDING_REVIEW_AND_SEPARATE_OWNER_AUTHORIZATION",
        "Owner execution authorization must remain withheld",
    )
    require(
        gate["executionAuthorization"]["authorizedBy"] is None
        and gate["executionAuthorization"]["authorizedOn"] is None,
        "No execution authorizer/date may be populated in this package",
    )
    scope = gate["scope"]
    for key in (
        "dependencyAdmission",
        "productionAdmission",
        "finalAdrAudio001",
        "implementationAllowed",
        "measurementAllowed",
        "gradleRuntimeDependencyAllowed",
        "recoveryModuleAllowed",
        "productionAppChangeAllowed",
    ):
        require(scope[key] is False, f"Forbidden recovery scope enabled: {key}")

    thresholds = gate["thresholds"]
    require(thresholds["committedLossBytesMaximum"] == 0, "Committed-loss gate weakened")
    require(thresholds["tailLossSecondsMaximum"] == 5.0, "Tail seconds gate weakened")
    require(thresholds["tailLossBytesMaximum"] == 160000, "Tail byte gate drift")
    require(thresholds["baseHardKillAttemptsPerCandidate"] == 120, "Base kill count drift")
    require(thresholds["validHardKillsPerCandidateMinimum"] == 100, "Valid kill minimum drift")
    require(thresholds["strataCount"] == 12, "Hard-kill strata count drift")
    require(thresholds["baseAttemptsPerStratumPerCandidate"] == 10, "Stratum allocation drift")
    require(thresholds["replacementAttemptsPerCandidateMaximum"] == 20, "Replacement cap drift")

    candidates = {candidate["id"]: candidate for candidate in gate["candidates"]}
    require(set(candidates) == {"REC-STREAM-TINK", "REC-MICROFILE-TINK"}, "Candidate set drift")
    stream = candidates["REC-STREAM-TINK"]
    micro = candidates["REC-MICROFILE-TINK"]
    require(stream["artifact"] == micro["artifact"] == "com.google.crypto.tink:tink-android:1.23.0", "Tink candidate drift")
    require(stream["template"] == "AES128_GCM_HKDF_4KB", "Streaming template drift")
    require(stream["templateStatus"].startswith("PROPOSED_"), "Streaming template was prematurely approved")
    require(micro["template"] is None, "Microfile AEAD template must remain unselected")
    require(micro["passEligibleCadencesSeconds"] == [5], "Only five-second microfiles may be PASS-eligible")
    require(micro["observationOnlyCadencesSeconds"] == [15, 30], "Observation cadence drift")

    require(gate["phaseA"]["passAllowed"] is False, "Phase A PASS must remain forbidden")
    require(gate["phaseA"]["allowedVerdicts"] == ["FAIL", "INCONCLUSIVE"], "Phase A verdict drift")
    require(
        gate["phaseA"]["plannedBaseAttemptsPerCandidate"]
        == {"PINNED_API36_X86_64_EMULATOR": 72, "PHYSICAL_D2": 48},
        "Phase A allocation drift",
    )
    require(
        gate["fullPhysicalVerdict"]["requiredPhysicalProfiles"] == ["D1", "D2", "D5"],
        "Full physical profile contract drift",
    )
    require(gate["fullPhysicalVerdict"]["phaseAAttemptsReusable"] is False, "Phase A cannot substitute for full campaign")
    require(gate["fullPhysicalVerdict"]["procurementRequiredNow"] is False, "D1/D5 procurement was prematurely required")

    hard_kill = protocol["hardKillCampaign"]
    require(hard_kill["baseAttemptsPerCandidate"] == 120, "Protocol base kill count drift")
    require(hard_kill["validHardKillsMinimumPerCandidate"] == 100, "Protocol valid kill count drift")
    require(hard_kill["signal"] == "SIGKILL", "Hard-kill signal drift")
    require(hard_kill["gracefulStopAllowed"] is False, "Graceful stop cannot satisfy hard-kill protocol")
    strata = hard_kill["strata"]
    require([item["id"] for item in strata] == [f"K{index:02d}" for index in range(1, 13)], "Strata IDs/order drift")
    require(
        hard_kill["phaseAAllocationPerStratum"]
        == {
            "PINNED_API36_X86_64_EMULATOR": [1, 2, 3, 4, 5, 6],
            "PHYSICAL_D2": [7, 8, 9, 10],
        },
        "Phase A per-stratum slots drift",
    )
    require(
        hard_kill["fullPhysicalAllocationPerStratum"]
        == {"D1": [1, 2, 3, 4], "D2": [5, 6, 7], "D5": [8, 9, 10]},
        "Full physical per-stratum slots drift",
    )
    require(hard_kill["replacement"]["automaticReplacementAllowed"] is False, "Automatic replacement must remain forbidden")
    require(hard_kill["replacement"]["maximumPerCandidate"] == 20, "Replacement maximum drift")
    require(hard_kill["replacement"]["candidateFailureReplaceable"] is False, "Candidate failures cannot be replaced")
    require(
        set(hard_kill["neverInvalid"])
        >= {"CANDIDATE_CRASH", "AUTHENTICATION_FAILURE", "COMMITTED_BYTE_LOSS", "TAIL_LOSS_GATE_FAILURE"},
        "Candidate failure outcomes are missing from never-invalid rules",
    )

    expected_fault_ids = {
        "COR-01", "COR-02", "COR-03", "TRU-01", "TRU-02", "TRU-03",
        "KEY-01", "KEY-02", "KEY-03", "SPL-01", "SPL-02", "SPL-03",
        "SPL-04", "SPL-05", "QUA-01", "QUA-02", "IDE-01", "IDE-02",
        "CLN-01", "CLN-02", "CLN-03",
    }
    faults = protocol["faultCampaign"]["cases"]
    require({case["id"] for case in faults} == expected_fault_ids, "Fault matrix ID drift")
    require(len(faults) == len(expected_fault_ids), "Duplicate fault matrix IDs")
    require(protocol["keyProtocol"]["androidKeystoreWrapping"] is True, "Keystore wrapping required")
    require(protocol["keyProtocol"]["runKeyUniquePerRun"] is True, "Run keys must be unique")
    require(protocol["keyProtocol"]["segmentKeySeparationRequired"] is True, "Segment key separation required")
    require(protocol["keyProtocol"]["keyLossTestMandatory"] is True, "Key-loss test is mandatory")
    for key in ("keyBytesAllowedInGit", "keysetsAllowedInGit", "keyBytesAllowedInLogs", "keyBytesAllowedInActionsArtifacts"):
        require(protocol["keyProtocol"][key] is False, f"Secret handling boundary weakened: {key}")
    sqlite = protocol["sqliteJournal"]
    require(sqlite["api"] == "android.database.sqlite", "Only platform SQLite is allowed")
    for key in ("roomAllowed", "sqlCipherAllowed", "workManagerAllowed", "productionSchemaAllowed", "productionMigrationAllowed"):
        require(sqlite[key] is False, f"Forbidden SQLite scope enabled: {key}")


def validate_evidence() -> None:
    inventory = read_json(EVIDENCE / "dependency-inventory.json")
    license_notice = read_json(EVIDENCE / "license-notice-inventory.json")
    security = read_json(EVIDENCE / "security-advisory-inventory.json")
    sqlite = read_json(EVIDENCE / "sqlite-platform-provenance.json")
    roles = read_json(EVIDENCE / "review-roles.json")
    readiness = read_json(EVIDENCE / "readiness.json")

    require(inventory["rootCoordinate"] == "com.google.crypto.tink:tink-android:1.23.0", "Inventory root drift")
    require(inventory["dependencyAdmission"] is False, "Dependency was prematurely admitted")
    require(inventory["runtimeGraphModified"] is False, "Runtime graph must remain untouched")
    require(inventory["resolution"]["externalCoordinateCountIncludingRoot"] == 8, "External closure count drift")
    require(inventory["resolution"]["projectGradleResolvedGraph"] is False, "No recovery Gradle graph may exist")
    require(inventory["resolution"]["futureExactResolutionRequired"] is True, "Future exact graph review required")
    require(len(inventory["artifacts"]) == 8, "Expected exactly eight external coordinates")
    require(len({artifact["coordinate"] for artifact in inventory["artifacts"]}) == 8, "Duplicate coordinates")
    require(all(artifact["jar"]["nativeEntries"] == 0 for artifact in inventory["artifacts"]), "Native payload found")
    require(inventory["composition"]["jniOrSharedLibrariesPresent"] is False, "Native composition drift")
    root = next(artifact for artifact in inventory["artifacts"] if artifact["coordinate"] == inventory["rootCoordinate"])
    require(root["jar"]["sha256"] == "c656918451b01c45ce5b20c7b6d4c388f956f61b3a3528e769048c8944c42f9e", "Tink JAR digest drift")
    require(root["pom"]["sha256"] == "a2d27e7207e6a25764859b62924fc7b972f41884ce272cead9b946c15a1f410f", "Tink POM digest drift")
    require(inventory["sourceRelease"]["commit"] == "1bedd75ae7161017c5f45b020395a72bbd40645d", "Tink source commit drift")
    embedded = inventory["embeddedComponents"]
    require(len(embedded) == 1 and embedded[0]["version"] == "4.33.6", "Shaded protobuf version drift")
    require(
        embedded[0]["entries"] == 540
        and embedded[0]["classEntries"] == 539
        and embedded[0]["nativeEntries"] == 0,
        "Shaded composition drift",
    )

    require(license_notice["reviewStatus"] == "EVIDENCE_COMPLETE_PACKAGE_REVIEW_PENDING", "License package state drift")
    require(license_notice["evaluationApproved"] is False, "Product/IP review was prematurely approved")
    require(license_notice["redistributionApproved"] is False, "Redistribution was prematurely approved")
    require(license_notice["productionLegalApproved"] is False, "Production Legal was prematurely approved")
    require(license_notice["summary"]["externalCoordinates"] == 8, "License coordinate count drift")
    require(len(license_notice["components"]) == 9, "Expected eight external plus one shaded component")
    require(license_notice["summary"]["externalJarLicenseEntries"] == 0, "Unexpected embedded license count")
    require(license_notice["summary"]["externalJarNoticeEntries"] == 0, "Unexpected embedded NOTICE count")

    exact_queries = security["exactVersionQueries"]
    require(len(exact_queries) == 8, "Security exact-version query count drift")
    require(all(not query["affectedPublishedAdvisories"] for query in exact_queries), "Recorded exact-version advisory affected")
    history = {entry["ghsaId"] for entry in security["relevantHistoricalAdvisories"]}
    require(
        history == {
            "GHSA-g5vf-v6wf-7w2r",
            "GHSA-4jrv-ppp4-jm57",
            "GHSA-cqj8-47ch-rvvq",
            "GHSA-2qp4-g3q3-f92w",
        },
        "Relevant advisory history drift",
    )
    require(security["status"] == "SNAPSHOT_COMPLETE_INDEPENDENT_REVIEW_PENDING", "Security review state drift")

    require(sqlite["platformApi"] == "android.database.sqlite", "Recovery provenance must use platform SQLite")
    require(sqlite["separateSQLiteLibraryDownloadedBundledOrRedistributed"] is False, "Separate SQLite is forbidden")
    for key in ("roomAllowed", "sqlCipherAllowed", "workManagerAllowed", "productionSchemaAllowed", "productionAdmission"):
        require(sqlite[key] is False, f"Forbidden platform scope enabled: {key}")
    environments = {item["id"]: item for item in sqlite["phaseA"]["environments"]}
    require(set(environments) == {"PINNED_API36_X86_64_EMULATOR", "PHYSICAL_D2"}, "Phase A provenance environments drift")
    emulator = environments["PINNED_API36_X86_64_EMULATOR"]
    require(emulator["archive"]["sha256"] == "b1bb0769d0bed7698e61f203d7dc9bf6e7c37cd01a39d0d8788a11186bc78160", "Emulator digest drift")
    d2_runtime = environments["PHYSICAL_D2"]["requiredFreshRuntimeEvidence"]
    require(all(value is None for value in d2_runtime.values()), "D2 runtime evidence must remain pending in governance task")

    role_map = roles["roles"]
    require(role_map["stage0ProductIp"]["reviewer"] == "Project owner", "Product/IP reviewer drift")
    require(role_map["stage0ProductIp"]["status"] == "ASSIGNED_REVIEW_PENDING", "Package review must remain pending")
    require(role_map["independentRecoveryEngineeringSecurity"]["reviewer"] is None, "Independent reviewer was populated without review evidence")
    require(role_map["independentRecoveryEngineeringSecurity"]["status"] == "UNASSIGNED_BLOCKING", "Independent review blocker drift")
    require(role_map["independentRecoveryEngineeringSecurity"]["replacesProductionSecurity"] is False, "Recovery review cannot replace Production Security")
    require(role_map["productionLegal"]["status"] == "UNASSIGNED_PRODUCTION_BLOCKED", "Production Legal boundary drift")
    require(role_map["executionAuthorizer"]["status"] == "AUTHORIZATION_WITHHELD", "Execution authorization must remain withheld")

    require(readiness["status"] == "BLOCKED_PACKAGE_REVIEW_PENDING", "Readiness status drift")
    require(readiness["executionAllowed"] is False, "Readiness unexpectedly allows execution")
    for key in (
        "implementationAllowedByThisPackage",
        "measuredExecutionAllowed",
        "runtimeDependencyAdded",
        "recoveryModuleExists",
        "harnessImplemented",
        "killCampaignExecuted",
        "deviceTestsExecuted",
        "benchmarksExecuted",
        "productionAppChanged",
    ):
        require(readiness[key] is False, f"Governance-only invariant violated: {key}")
    blocker_ids = {blocker["id"] for blocker in readiness["blockers"]}
    require(blocker_ids == {f"REC-RDY-{index:02d}-{suffix}" for index, suffix in [
        (1, "PACKAGE-REVIEW"),
        (2, "INDEPENDENT-ENGINEERING-SECURITY"),
        (3, "STREAMING-CHECKPOINT-PROOF"),
        (4, "MICROFILE-CRYPTO-PROTOCOL"),
        (5, "FUTURE-RESOLVED-GRAPH"),
        (6, "DEVICE-SQLITE-PREFLIGHT"),
        (7, "HARNESS-ABSENT"),
        (8, "OWNER-EXECUTION-AUTHORIZATION"),
        (9, "D1-D5-FULL-VERDICT"),
        (10, "PRODUCTION-LEGAL-SECURITY"),
    ]}, "Readiness blocker set drift")


def validate_documents_and_no_implementation() -> None:
    owner_record = read_text(STAGE0 / "DORA_MVP1_POC_RECOVERY_OWNER_DECISION_OD14.md")
    decision = read_text(STAGE0 / "DEC-044-POC-RECOVERY-EXPERIMENT.md")
    gate_markdown = read_text(STAGE0 / "DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_1.md")
    review = read_text(EVIDENCE / "ip-stage0-evaluation-review.md")
    require("Decision ID: `OD-14`" in owner_record, "Separate recovery owner record is missing OD-14")
    require("executionAllowed=false" in owner_record, "OD-14 must explicitly withhold execution")
    require("Status: **Proposed" in decision, "DEC-044 must remain Proposed")
    require("executionAllowed=false" in decision, "Decision must explicitly withhold execution")
    for required in (
        "Commit point", "Committed prefix", "Tail loss", "Valid hard kill",
        "Fault, quarantine, idempotency and cleanup matrix", "Phase A", "D1/D2/D5",
    ):
        require(required in gate_markdown, f"Recovery Gate Set is missing section/content: {required}")
    require("PRODUCT/IP AND INDEPENDENT ENGINEERING/SECURITY REVIEW PENDING" in review, "Review status drift")

    recovery_module = ROOT / "android" / "poc" / "recovery"
    require(not recovery_module.exists(), "android/poc/recovery must not exist in governance package")
    gradle_files = list((ROOT / "android").rglob("*.gradle")) + list((ROOT / "android").rglob("*.gradle.kts"))
    gradle_files += [ROOT / "android" / "gradle" / "libs.versions.toml"]
    tink_pattern = re.compile(r"com\.google\.crypto\.tink|tink-android", flags=re.IGNORECASE)
    contaminated = [str(path.relative_to(ROOT)) for path in gradle_files if path.is_file() and tink_pattern.search(path.read_text(encoding="utf-8"))]
    require(not contaminated, f"Tink appeared in Android Gradle/runtime configuration: {contaminated}")
    settings = read_text(ROOT / "android" / "settings.gradle.kts")
    require(":poc:recovery" not in settings, "Recovery module was included in Gradle settings")
    app_manifest = read_text(ROOT / "android" / "app" / "src" / "main" / "AndroidManifest.xml")
    require("android.permission.RECORD_AUDIO" not in app_manifest, "Production :app microphone permission is forbidden")

    device_matrix = read_text(STAGE0 / "device-matrix.yaml")
    require("recovery_owner_decision:" in device_matrix, "Recovery device contract is missing")
    require("execution_allowed: false" in device_matrix, "Device contract must withhold execution")
    backlog = read_text(ROOT / "docs" / "DORA_MVP1_IMPLEMENTATION_BACKLOG.md")
    recovery_row = next(line for line in backlog.splitlines() if line.startswith("| POC-RECOVERY-001 |"))
    require("| BLOCKED |" in recovery_row and "executionAllowed=false" in recovery_row, "Recovery backlog must remain BLOCKED")


def main() -> int:
    validate_gate_and_protocol()
    validate_evidence()
    validate_documents_and_no_implementation()
    print("POC-RECOVERY-001 governance package validation passed; executionAllowed=false")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, StopIteration, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
