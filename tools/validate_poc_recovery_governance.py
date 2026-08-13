#!/usr/bin/env python3
"""Fail-closed validation for the governance-only POC-RECOVERY-001 v0.6 package."""

from __future__ import annotations

import copy
import hashlib
import json
import subprocess
import sys
from pathlib import Path
from typing import Any, Callable


ROOT = Path(__file__).resolve().parents[1]
GATE_PATH = "docs/stage0/poc-recovery-gate-set-stage0-v0.6.json"
PROTOCOL_PATH = "docs/stage0/poc-recovery-protocol-stage0-v0.6.json"
GATE_ID = "poc-recovery-stage0-v0.6"
PROTOCOL_ID = "poc-recovery-protocol-stage0-v0.6"
REVIEWED_V05_HEAD = "eca48ba62acd79007884710395cc40ea21a02611"
BASE_HEAD = REVIEWED_V05_HEAD
SQLITE_STATUS = "RECOVERY_STAGE0_V0_6_SQLITE_PROFILE_SELECTED_FRESH_PREFLIGHT_INCOMPLETE"

IMMUTABLE_AUDIT_HASHES = {
    "docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_1.md": "d891e033e3e58455dbafd03be5a41ca64cafda93182424357035c37d769ae46e",
    "docs/stage0/poc-recovery-gate-set-stage0-v0.1.json": "78c1a8289f90b51a376b023673dc00b6cb35386b5b0a2dda9432b50b20216e11",
    "docs/stage0/poc-recovery-protocol-stage0-v0.1.json": "b853295e6c66815c61566e930d30dafa0dfc72e805bb5ba38158688e084ead81",
    "docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_2.md": "d4fab2f47872f0b6c1c04c5b0b1022b047ae8782eb0130cd2f66825294455180",
    "docs/stage0/poc-recovery-gate-set-stage0-v0.2.json": "f6384c7b1d4d493218a600722ddf0116f454e8356e7e247da74f03256cc69110",
    "docs/stage0/poc-recovery-protocol-stage0-v0.2.json": "cfa06e624cbc0da37b68188d7b1739cdfb5ca12beeedc21f408897dc41b2081f",
    "docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_3.md": "7d24e5aa0c2dd0c65ef8def12e687d39f5d0bfc30a222be51f29bffd02c772a9",
    "docs/stage0/poc-recovery-gate-set-stage0-v0.3.json": "25a05a4d136f90e6b62005943585a27161517ea337573b71b5b1aaeca16bb80f",
    "docs/stage0/poc-recovery-protocol-stage0-v0.3.json": "376c6bec9d6632ff0824465ee890f953445c0843716b8a1b3a044f322d03a0c9",
    "docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_4.md": "03521bce76d123d463c86980f1db10b43667b39cea5114810977c8d4940dad0f",
    "docs/stage0/poc-recovery-gate-set-stage0-v0.4.json": "f89d5dff7bcfdcc7f96efd4d1c195b0054e262976db3b722b384c50e4440804c",
    "docs/stage0/poc-recovery-protocol-stage0-v0.4.json": "cfe9d19e7b0e409c1be6a33c4cd240ebdc03e014f0b1abe1b0776aae1ede5eaa",
    "docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_5.md": "ce4980468bfeb7bddadfa58b3ba71b702de4562cc8e264908d19f92fa7638f9c",
    "docs/stage0/poc-recovery-gate-set-stage0-v0.5.json": "3d1051c85076dda1d8b0c20812c08b7343a7c611ad8c6381d9e80d41724bef93",
    "docs/stage0/poc-recovery-protocol-stage0-v0.5.json": "9b469562aa8deff2f94402b6fe5093fb832d76ec48f5ef0fd081b76b322c3e9c",
}

CANONICAL_TAXONOMY = [
    "KEY_REF_COLLISION",
    "INCOMPLETE_KEY_BOOTSTRAP",
    "KEY_CONFIRMATION_MISSING",
    "CORRUPT_KEY_CONFIRMATION",
    "KEY_UNAVAILABLE",
    "KEY_UNAVAILABLE_KEY_MISMATCH",
    "CORRUPT_KEY_ENVELOPE",
    "KEY_ENVELOPE_AUTH_FAILURE",
]

CANONICAL_BLOCKERS = [
    "REC-RDY-01-PRODUCT-IP-FINAL-APPROVAL",
    "REC-RDY-02-ACCOUNTABLE-ENGINEERING-SECURITY-REVIEW",
    "REC-RDY-03-STREAMING-IMPLEMENTATION-VERIFICATION",
    "REC-RDY-04-MICROFILE-IMPLEMENTATION-VERIFICATION",
    "REC-RDY-05-FUTURE-RESOLVED-GRAPH",
    "REC-RDY-06-DEVICE-SQLITE-PREFLIGHT",
    "REC-RDY-07-HARNESS-ABSENT",
    "REC-RDY-08-OWNER-EXECUTION-AUTHORIZATION",
    "REC-RDY-09-D1-D5-FULL-VERDICT",
    "REC-RDY-10-PRODUCTION-LEGAL-SECURITY",
    "REC-RDY-11-SUPPLY-CHAIN-AUTHENTICITY",
]

EXPECTED_FAULT_IDS = [
    *(f"COR-{index:02d}" for index in range(1, 7)),
    *(f"TRU-{index:02d}" for index in range(1, 4)),
    *(f"KEY-{index:02d}" for index in range(1, 8)),
    *(f"SPL-{index:02d}" for index in range(1, 6)),
    "RBK-01", "RBK-02", "PAR-01",
    "QUA-01", "QUA-02", "QUA-03",
    "IDE-01", "IDE-02", "EVT-01",
    "CLN-01", "CLN-02", "CLN-03",
    *(f"KCB-{index:02d}" for index in range(1, 7)),
    *(f"KCF-{index:02d}" for index in range(1, 8)),
]

KEY04_PRECONDITIONS = [
    "A durable run row exists.",
    "The key-confirmation final exists.",
    "The key-confirmation path, type, recorded ciphertext length and recorded ciphertext SHA-256 fully match.",
    "The Android Keystore alias exists and is available through the approved Builder/getAead path.",
    "The exact confirmation AAD is computed under the active protocol.",
    "Before recovery, the fault controller replaces the underlying alias key with another valid AEAD key while preserving the previous confirmation ciphertext bytes and their recorded length and SHA-256.",
    "Recovery does not create or replace the key.",
    "Aead.decrypt(existingConfirmationCiphertext, exactAad) terminates with an authentication/AAD failure.",
]

KEY04_FORBIDDEN = {
    "SUCCESSFUL_DECRYPT",
    "MALFORMED_DECRYPTED_PLAINTEXT",
    "WRONG_MAGIC_AFTER_SUCCESSFUL_DECRYPT",
    "WRONG_SCHEMA_AFTER_SUCCESSFUL_DECRYPT",
    "WRONG_PROTOCOL_ID_AFTER_SUCCESSFUL_DECRYPT",
    "WRONG_CANDIDATE_ID_AFTER_SUCCESSFUL_DECRYPT",
    "WRONG_RUN_ID_AFTER_SUCCESSFUL_DECRYPT",
    "WRONG_CANONICAL_ALIAS_SHA256_AFTER_SUCCESSFUL_DECRYPT",
    "CIPHERTEXT_PATH_CORRUPTION",
    "CIPHERTEXT_TYPE_CORRUPTION",
    "CIPHERTEXT_RECORDED_LENGTH_CORRUPTION",
    "CIPHERTEXT_RECORDED_SHA256_CORRUPTION",
    "MISSING_ALIAS",
    "INVALIDATED_ALIAS",
    "UNUSABLE_ALIAS",
}

KCF07_POST_DECRYPT_FAILURES = [
    "malformed plaintext",
    "wrong magic",
    "wrong schema",
    "wrong protocolId",
    "wrong candidateId",
    "wrong runId",
    "wrong canonicalAliasSha256",
]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def read_text(relative: str) -> str:
    path = ROOT / relative
    require(path.is_file(), f"Missing required file: {relative}")
    return path.read_text(encoding="utf-8")


def read_json(relative: str) -> dict[str, Any]:
    return json.loads(read_text(relative))


def sha256(relative: str) -> str:
    return hashlib.sha256((ROOT / relative).read_bytes()).hexdigest()


def validate_immutable_history(gate: dict[str, Any], protocol: dict[str, Any]) -> None:
    require(len(IMMUTABLE_AUDIT_HASHES) == 15, "Historical hash registry must contain 15 artifacts")
    for relative, expected in IMMUTABLE_AUDIT_HASHES.items():
        require(sha256(relative) == expected, f"Superseded audit artifact changed: {relative}")

    retained = gate["retainedAuditArtifacts"]
    require(
        [item["version"] for item in retained]
        == [f"poc-recovery-stage0-v0.{version}" for version in range(1, 6)],
        "Gate Set v0.1-v0.5 retained history drift",
    )
    recorded: dict[str, str] = {}
    for item in retained:
        require(item["disposition"] == "SUPERSEDED_AUDIT_ARTIFACT_NON_EXECUTABLE", "Historical version became executable")
        recorded[item["markdownLocator"]] = item["markdownSha256"]
        recorded[item["gateLocator"]] = item["gateSha256"]
        recorded[item["protocolLocator"]] = item["protocolSha256"]
    require(recorded == IMMUTABLE_AUDIT_HASHES, "Recorded v0.1-v0.5 SHA-256 pins drift")

    gate_parent = gate["inheritsExactV05GateSet"]
    require(
        gate_parent["locator"] == "docs/stage0/poc-recovery-gate-set-stage0-v0.5.json"
        and gate_parent["sha256"] == IMMUTABLE_AUDIT_HASHES[gate_parent["locator"]]
        and gate_parent["allUnchangedSemanticsInherited"] is True,
        "v0.6 Gate Set inheritance drift",
    )
    protocol_parent = protocol["inheritsExactV05Contract"]
    require(
        protocol_parent["locator"] == "docs/stage0/poc-recovery-protocol-stage0-v0.5.json"
        and protocol_parent["sha256"] == IMMUTABLE_AUDIT_HASHES[protocol_parent["locator"]]
        and protocol_parent["allUnchangedSemanticsInherited"] is True,
        "v0.6 protocol inheritance drift",
    )
    historical = protocol["historicalProtocolV03"]
    require(
        historical["sha256"] == IMMUTABLE_AUDIT_HASHES[historical["locator"]]
        and historical["immutable"] is True
        and historical["historicalKey04Changed"] is False
        and historical["historicalRowsParticipateAsAdditionalActiveRows"] is False,
        "Historical v0.3/KEY-04 boundary drift",
    )


def validate_campaigns(campaigns: dict[str, Any]) -> None:
    phase = campaigns["phaseA"]
    require(
        phase["rows"] == 46
        and phase["perRow"] == {"PINNED_API36_X86_64_EMULATOR": 3, "PHYSICAL_D2": 1}
        and phase["emulatorInjections"] == 138
        and phase["physicalD2Injections"] == 46
        and phase["phaseATotalInjections"] == 184
        and phase["allowedVerdicts"] == ["FAIL", "INCONCLUSIVE"]
        and phase["passAllowed"] is False,
        "Phase A count/verdict drift",
    )
    full = campaigns["fullPhysicalCampaign"]
    require(
        full["rows"] == 46
        and full["perRow"] == {"PHYSICAL_D1": 1, "PHYSICAL_D2": 1, "PHYSICAL_D5": 1}
        and full["fullPhysicalTotalInjections"] == 138
        and full["passRequiresCompleteD1D2D5Profile"] is True,
        "Full physical count/profile drift",
    )
    require(
        campaigns.get("hardKillAttemptsPerCandidate", campaigns.get("hardKillCampaign", {}).get("attemptsPerCandidate")) == 120,
        "Hard-kill attempts/candidate drift",
    )
    separate = campaigns.get("separateFromHardKillDenominator")
    if separate is None:
        separate = campaigns["hardKillCampaign"]["separateFromFaultInjectionDenominators"]
    require(separate is True, "Hard-kill denominator was merged with fault injections")


def validate_gate(gate: dict[str, Any]) -> None:
    require(
        gate["schemaVersion"] == 6
        and gate["pocId"] == "POC-RECOVERY-001"
        and gate["gateSetVersion"] == GATE_ID
        and gate["protocolId"] == PROTOCOL_ID,
        "Active v0.6 Gate Set identity drift",
    )
    require(gate["implementationAllowed"] is False and gate["executionAllowed"] is False, "Gate Set authorized work")
    require(all(value is False for value in gate["scope"].values() if isinstance(value, bool)), "Governance-only scope widened")
    require(
        gate["supersedes"] == {
            "gateSetVersion": "poc-recovery-stage0-v0.5",
            "disposition": "SUPERSEDED_SHA256_PINNED_AUDIT_ARTIFACT_NON_EXECUTABLE",
            "reviewedCommit": REVIEWED_V05_HEAD,
        },
        "v0.5 supersession record drift",
    )
    require(gate["findingsLedgers"][-1].endswith("review-findings-v0.5.json"), "v0.5 review ledger missing")
    approval = gate["approvalState"]
    require(
        approval["actualFutureGraphProductIpDisposition"].startswith("OPEN_BLOCKED")
        and approval["productIpFinalApproval"] is False
        and approval["accountableIndependentEngineeringSecurityReviewer"] is None
        and approval["advisoryDocumentaryReviewIsFormal"] is False
        and approval["currentCodexReviewClaimedFormallyIndependent"] is False
        and approval["productionLegalReviewer"] is None
        and approval["productionSecurityReviewer"] is None,
        "Approval/reviewer boundary drift",
    )
    advisory = gate["advisoryReviewEvidence"]
    require(
        advisory == {
            "reviewer": "GPT-5.6 Sol",
            "organization": "OpenAI",
            "role": "AI documentary advisory reviewer",
            "reviewDate": "2026-08-12",
            "reviewedCommit": REVIEWED_V05_HEAD,
            "formalReviewer": False,
            "disposition": "CHANGES_REQUIRED",
            "closesRecRdy02": False,
        },
        "Advisory review evidence drift",
    )
    require(
        gate["reviewFindings"] == {
            "REC-REV-20260812-01": "CLOSED_BY_V0_6_EXACT_DECRYPT_FAILURE_OVERRIDE",
            "REC-REV-20260812-02": "OPEN_BLOCKING",
        },
        "Review finding dispositions drift",
    )
    ids = gate["mandatoryFaultIds"]
    require(gate["mandatoryFaultRowCount"] == 46 and ids == EXPECTED_FAULT_IDS, "Gate fault ID order/count drift")
    require(len(ids) == len(set(ids)) == 46 and ids.count("KEY-04") == 1, "Gate fault IDs are not 46 unique/one KEY-04")
    key_gate = gate["keyConfirmationGate"]
    require(
        key_gate["mandatoryFaultRowsAddedV06"] == 0
        and key_gate["effectiveFaultRowsOverriddenV06"] == 1
        and key_gate["effectiveKey04ExpectedClassification"] == "KEY_UNAVAILABLE_KEY_MISMATCH"
        and key_gate["effectiveKey04SuccessfulDecryptAllowed"] is False
        and key_gate["effectiveKey04PostDecryptPlaintextMismatchAllowed"] is False,
        "Gate KEY-04 summary drift",
    )
    matrix = gate["activeEffectiveFaultMatrix"]
    require(
        matrix["requiredUniqueRowCount"] == 46
        and matrix["key04RequiredOccurrenceCount"] == 1
        and matrix["historicalRowsAreAdditionalActiveRows"] is False,
        "Gate active matrix contract drift",
    )
    validate_campaigns(gate["faultCampaignProfiles"])
    require(gate["blockers"] == CANONICAL_BLOCKERS, "Gate blocker order drift")


def effective_rows(protocol: dict[str, Any]) -> list[dict[str, Any]]:
    return protocol["faultCampaign"]["activeEffectiveFaultMatrixV06"]["rows"]


def validate_key04_and_kcf07(protocol: dict[str, Any]) -> None:
    rows = effective_rows(protocol)
    ids = [row["id"] for row in rows]
    require(len(rows) == len(set(ids)) == 46 and ids == EXPECTED_FAULT_IDS, "Active matrix must have 46 unique canonical IDs")
    require(all(row["effective"] is True for row in rows), "Active matrix contains a non-effective row")
    require(ids.count("KEY-04") == 1, "Active matrix must contain KEY-04 exactly once")
    key04 = next(row for row in rows if row["id"] == "KEY-04")
    require(key04["effectiveSource"] == "V0_6_EXACT_DECRYPT_FAILURE_OVERRIDE", "KEY-04 is not the v0.6 override")
    require(key04["preconditions"] == KEY04_PRECONDITIONS, "KEY-04 exact preconditions drift")
    require(key04["decryptOutcome"] == "AUTHENTICATION_OR_AAD_FAILURE_ONLY", "KEY-04 permits a non-auth decrypt outcome")
    require(key04["successfulDecryptAllowed"] is False, "KEY-04 permits successful decrypt")
    require(key04["postDecryptParserOrPlaintextMismatchAllowed"] is False, "KEY-04 permits post-decrypt mismatch")
    require(
        key04["expectedClassification"] == "KEY_UNAVAILABLE_KEY_MISMATCH"
        and key04["expectedClassificationAlternativesAllowed"] is False,
        "KEY-04 expected classification is not strict",
    )
    require(set(key04["forbiddenInterpretations"]) == KEY04_FORBIDDEN, "KEY-04 forbidden interpretations drift")
    replacement = key04["replacesInheritedHistoricalRow"]
    require(
        replacement["historicalRowModified"] is False
        and replacement["historicalRowActiveInAdditionToThisRow"] is False
        and replacement["sha256"] == IMMUTABLE_AUDIT_HASHES[replacement["locator"]],
        "KEY-04 historical replacement boundary drift",
    )

    kcf07 = next(row for row in rows if row["id"] == "KCF-07")
    require(
        kcf07["effectiveSource"] == "V0_5_CASE_INHERITED_UNCHANGED"
        and "Aead.decrypt() succeeds" in kcf07["requiredObservation"]
        and kcf07["coveredPostDecryptFailures"] == KCF07_POST_DECRYPT_FAILURES
        and kcf07["expectedClassification"] == "CORRUPT_KEY_CONFIRMATION",
        "KCF-07 successful-decrypt malformed-plaintext oracle drift",
    )
    routing = protocol["faultCampaign"]["effectiveKey04Routing"]
    require(
        routing["successfulDecryptWithMalformedOrWrongPlaintext"] == {"faultRow": "KCF-07", "classification": "CORRUPT_KEY_CONFIRMATION"}
        and routing["ciphertextPathTypeLengthOrHashMismatch"] == {"stage": "PRE_DECRYPT", "classification": "CORRUPT_KEY_CONFIRMATION"}
        and routing["missingInvalidatedOrUnusableAlias"] == {"classification": "KEY_UNAVAILABLE"}
        and routing["structurallyValidLaterKeyEnvelopeAeadAadOrTagFailureAfterValidConfirmation"] == {"classification": "KEY_ENVELOPE_AUTH_FAILURE"},
        "KEY-04 neighboring failure routing drift",
    )


def validate_protocol(protocol: dict[str, Any]) -> None:
    require(
        protocol["schemaVersion"] == 6
        and protocol["protocolId"] == PROTOCOL_ID
        and protocol["pocId"] == "POC-RECOVERY-001"
        and protocol["implementationAllowed"] is False
        and protocol["executionAllowed"] is False,
        "Active v0.6 protocol identity/authority drift",
    )
    taxonomy = protocol["canonicalKeyTaxonomyV06"]
    require(
        taxonomy["uniqueClassifications"] == CANONICAL_TAXONOMY
        and taxonomy["uniqueClassificationCount"] == 8
        and taxonomy["plaintextMagicSchemaParserOrTrailingValidationBeforeDecryptAllowed"] is False
        and taxonomy["ambiguousExpectedOutcomeAllowed"] is False,
        "Canonical taxonomy drift",
    )
    algorithm = taxonomy["recoveryReconciliationAlgorithm"]
    require([step["step"] for step in algorithm] == list(range(1, 10)), "Recovery algorithm order drift")
    require([step["classification"] for step in algorithm] == [
        "INCOMPLETE_KEY_BOOTSTRAP", "KEY_CONFIRMATION_MISSING", "CORRUPT_KEY_CONFIRMATION",
        "KEY_UNAVAILABLE", "KEY_UNAVAILABLE_KEY_MISMATCH", "CORRUPT_KEY_CONFIRMATION",
        "KEY_UNAVAILABLE", "CORRUPT_KEY_ENVELOPE", "KEY_ENVELOPE_AUTH_FAILURE",
    ], "Recovery algorithm classification drift")
    validate_key04_and_kcf07(protocol)
    fault = protocol["faultCampaign"]
    require(
        fault["mandatoryFaultRowCount"] == 46
        and fault["addedV06MandatoryRows"] == 0
        and fault["overriddenV06EffectiveRows"] == 1,
        "Protocol fault count/override drift",
    )
    matrix_meta = fault["activeEffectiveFaultMatrixV06"]
    require(
        matrix_meta["rowCount"] == 46
        and matrix_meta["uniqueIdsRequired"] is True
        and matrix_meta["key04OccurrenceCountRequired"] == 1
        and matrix_meta["historicalRowsAreAdditionalActiveRows"] is False,
        "Active matrix metadata drift",
    )
    validate_campaigns(fault)
    require(protocol["canonicalBlockerIds"] == CANONICAL_BLOCKERS, "Protocol blocker IDs drift")
    evidence = protocol["reviewEvidence"]
    require(
        evidence["reviewer"] == "GPT-5.6 Sol"
        and evidence["organization"] == "OpenAI"
        and evidence["formalReviewer"] is False
        and evidence["disposition"] == "CHANGES_REQUIRED"
        and evidence["closesRecRdy02"] is False
        and evidence["recRev2026081201Disposition"] == "CLOSED_BY_V0_6_EXACT_DECRYPT_FAILURE_OVERRIDE"
        and evidence["recRev2026081202Disposition"] == "OPEN_BLOCKING",
        "Protocol review evidence drift",
    )


def validate_readiness_and_evidence(gate: dict[str, Any]) -> None:
    readiness = read_json("docs/evidence/poc-recovery-001/readiness.json")
    roles = read_json("docs/evidence/poc-recovery-001/review-roles.json")
    ledger = read_json("docs/evidence/poc-recovery-001/review-findings-v0.5.json")
    index = read_json("docs/evidence/poc-recovery-001/evidence-index.json")
    provenance = read_json("docs/evidence/poc-recovery-001/sqlite-platform-provenance.json")
    security = read_json("docs/evidence/poc-recovery-001/security-advisory-inventory.json")

    require(
        readiness["schemaVersion"] == 7
        and readiness["status"].startswith("BLOCKED_")
        and all(readiness[field] is False for field in (
            "executionAllowed", "implementationAllowed", "implementationAllowedByThisPackage",
            "measuredExecutionAllowed", "runtimeDependencyAdded", "recoveryModuleExists",
            "harnessImplemented", "nonMetricImplementationVerificationPassed",
            "exactFutureResolvedGraphReviewed", "killCampaignExecuted", "deviceTestsExecuted",
            "benchmarksExecuted", "productionAppChanged",
        )),
        "Readiness authority/evidence boundary drift",
    )
    package = readiness["packageArtifacts"]
    require(
        package["activeGateSetVersion"] == GATE_ID
        and package["activeProtocolId"] == PROTOCOL_ID
        and package["governanceRemediationV06Present"] is True
        and package["v05RetainedAsSupersededAuditArtifact"] is True
        and package["v05Executable"] is False
        and package["reviewFindingsV05LedgerPresent"] is True,
        "Readiness active package metadata drift",
    )
    advisory = readiness["advisoryDocumentaryReview"]
    require(advisory["formalReviewer"] is False and advisory["closesRecRdy02"] is False, "Readiness treats advisory review as formal")
    blocker_ids = [item["id"] for item in readiness["blockers"]]
    require(gate["blockers"] == blocker_ids == CANONICAL_BLOCKERS and len(set(blocker_ids)) == 11, "Readiness blocker contract drift")
    rec02 = readiness["blockers"][1]
    require(rec02["status"] == "OPEN_UNASSIGNED" and "does not close REC-RDY-02" in rec02["condition"], "REC-RDY-02 was closed by advisory review")
    require(readiness["phaseA"]["phaseATotalInjections"] == 184 and readiness["phaseA"]["hardKillDenominatorSeparate"] is True, "Readiness Phase A drift")
    require(readiness["fullVerdict"]["fullPhysicalTotalInjections"] == 138 and readiness["fullVerdict"]["deferred"] is True, "Readiness full physical drift")

    require(roles["schemaVersion"] == 7 and roles["activeGateSetVersion"] == GATE_ID and roles["activeProtocolId"] == PROTOCOL_ID, "Review role metadata drift")
    role_map = roles["roles"]
    require(role_map["packageAuthor"]["claimedFormallyIndependentReviewer"] is False, "Codex claimed formal independence")
    require(role_map["advisoryDocumentaryReviewer"]["formalReviewer"] is False and role_map["advisoryDocumentaryReviewer"]["closesRecRdy02"] is False, "AI advisory reviewer gained formal authority")
    independent = role_map["independentRecoveryEngineeringSecurity"]
    require(independent["reviewer"] is None and independent["status"] == "UNASSIGNED_BLOCKING", "Accountable reviewer assignment drift")
    require(role_map["stage0ProductIp"]["futureExactGraphDisposition"]["status"] == "OPEN_BLOCKED", "Future Product/IP graph disposition closed")
    require(role_map["productionLegal"]["reviewer"] is None and role_map["productionSecurity"]["reviewer"] is None, "Production review prematurely assigned")

    require(
        ledger["sourceReviewedCommit"] == REVIEWED_V05_HEAD
        and ledger["reviewedGateSetVersion"] == "poc-recovery-stage0-v0.5"
        and ledger["activeRemediationProtocolId"] == PROTOCOL_ID
        and ledger["review"]["reviewer"] == "GPT-5.6 Sol"
        and ledger["review"]["organization"] == "OpenAI"
        and ledger["review"]["formalReviewer"] is False
        and ledger["review"]["disposition"] == "CHANGES_REQUIRED"
        and ledger["closesRecRdy02"] is False,
        "Review findings ledger identity drift",
    )
    findings = {item["id"]: item for item in ledger["findings"]}
    require(
        findings["REC-REV-20260812-01"]["severity"] == "P1"
        and findings["REC-REV-20260812-01"]["disposition"] == "CLOSED_BY_V0_6_EXACT_DECRYPT_FAILURE_OVERRIDE"
        and findings["REC-REV-20260812-02"]["severity"] == "P0"
        and findings["REC-REV-20260812-02"]["category"] == "governance"
        and findings["REC-REV-20260812-02"]["disposition"] == "OPEN_BLOCKING",
        "Finding severity/disposition drift",
    )

    require(index["schemaVersion"] == 4 and index["activeGateSetVersion"] == GATE_ID and index["activeProtocolId"] == PROTOCOL_ID, "Evidence index metadata drift")
    require({item["locator"]: item["sha256"] for item in index["supersededAuditArtifacts"]} == IMMUTABLE_AUDIT_HASHES, "Evidence index historical hashes drift")
    artifact_ids = {item["id"] for item in index["artifacts"]}
    require({"REC-V06-GATE-MARKDOWN", "REC-V06-GATE-JSON", "REC-V06-PROTOCOL-JSON", "REC-V06-REMEDIATION", "REC-REVIEW-V05-LEDGER"}.issubset(artifact_ids), "Evidence index lacks v0.6 artifacts")

    require(provenance["status"] == SQLITE_STATUS and provenance["activeGateSetVersion"] == GATE_ID and provenance["activeProtocolId"] == PROTOCOL_ID and provenance["phaseA"]["executionAllowed"] is False, "SQLite provenance metadata drift")
    require(provenance["fullPhysicalVerdict"]["D1"].startswith("UNAVAILABLE") and provenance["fullPhysicalVerdict"]["D5"].startswith("UNAVAILABLE"), "D1/D5 no longer deferred")
    require(security["activeGateSetVersion"] == GATE_ID and security["activeProtocolId"] == PROTOCOL_ID and all(item["mitigationState"].startswith("V0_6_") for item in security["templateAndProtocolRisks"]), "Security metadata drift")


def validate_dependency_and_scope_boundary() -> None:
    inventory = read_json("docs/evidence/poc-recovery-001/dependency-inventory.json")
    require(inventory["dependencyAdmission"] is False and inventory["runtimeGraphModified"] is False, "Dependency inventory admitted runtime wiring")
    boundary = inventory["recoveryBoundary"]
    require(boundary["currentTinkAndroid123Wired"] is False and boundary["currentRecoveryModuleExists"] is False and boundary["futureActualRecoveryGraphStatus"].startswith("OPEN_BLOCKED"), "Recovery dependency boundary drift")
    require(not (ROOT / "android" / "poc" / "recovery").exists(), "Recovery module exists")
    require(":poc:recovery" not in read_text("android/settings.gradle.kts"), "Recovery module included")
    changed_android = subprocess.run(
        ["git", "diff", "--name-only", BASE_HEAD, "--", "android"],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    ).stdout.splitlines()
    require(not changed_android, f"Android/dependency surface changed: {changed_android}")


def validate_active_metadata() -> None:
    fragments = {
        "docs/DORA_MVP1_TECHNICAL_PLAN.md": ["prospective protocol v0.6", "KEY_UNAVAILABLE_KEY_MISMATCH", "REC-RDY-02"],
        "docs/DORA_MVP1_PRODUCT_DECISIONS.md": [GATE_ID, PROTOCOL_ID, "CLOSED_BY_V0_6_EXACT_DECRYPT_FAILURE_OVERRIDE"],
        "docs/DORA_MVP1_TEST_STRATEGY.md": ["POC-RECOVERY-001` v0.6", "46 unique active fault rows", "KCF-07"],
        "docs/DORA_MVP1_STAGE_STATUS.md": ["GOVERNANCE REMEDIATION v0.6", GATE_ID, PROTOCOL_ID, "REC-REV-20260812-02"],
        "docs/stage0/DEC-044-POC-RECOVERY-EXPERIMENT.md": [GATE_ID, PROTOCOL_ID, "KEY_UNAVAILABLE_KEY_MISMATCH", "KCF-07"],
        "docs/stage0/DORA_MVP1_POC_GATES.md": ["stage0-v0.6", "46 unique IDs", "KEY_UNAVAILABLE_KEY_MISMATCH"],
        "docs/stage0/DORA_MVP1_POC_EXECUTION_ORDER.md": ["stage0-v0.6", "46 unique active rows", "REC-RDY-02"],
        "docs/stage0/DORA_MVP1_IP_ASSET_POLICY.md": ["active protocol v0.6", "future actual recovery", "Engineering/Security reviewer"],
        "docs/stage0/DORA_MVP1_POC_RECOVERY_OWNER_DECISION_OD14.md": [GATE_ID, PROTOCOL_ID, "15 SHA-256 values", "formalReviewer=false"],
        "docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_6.md": [
            *KEY04_PRECONDITIONS,
            "KEY_UNAVAILABLE_KEY_MISMATCH",
            "successful confirmation decrypt followed by malformed plaintext",
            "exactly 46 unique IDs",
            "46 × (3 pinned emulator + 1 D2)",
            "184 injections",
            "46 × (D1 + D2 + D5)",
            "138 injections",
            "120 attempts per candidate",
        ],
        "docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md": [GATE_ID, PROTOCOL_ID, "REC-REV-20260812-01", "implementationAllowed=false"],
        "docs/DORA_MVP1_IMPLEMENTATION_READINESS.md": [GATE_ID, PROTOCOL_ID, "REC-RDY-02", "executionAllowed=false"],
        "docs/evidence/poc-recovery-001/README.md": ["PROSPECTIVE PROTOCOL v0.6", "15 superseded audit artifacts", "GPT-5.6 Sol", "KCF-07"],
        "docs/evidence/poc-recovery-001/governance-remediation-v0.6.md": [REVIEWED_V05_HEAD, "CLOSED_BY_V0_6_EXACT_DECRYPT_FAILURE_OVERRIDE", "OPEN_BLOCKING"],
        "docs/evidence/poc-recovery-001/independent-engineering-security-review-task.md": ["POC-RECOVERY-001 v0.6", "KEY-04", "formalReviewer=false"],
        "docs/evidence/poc-recovery-001/ip-stage0-evaluation-review.md": [GATE_ID, PROTOCOL_ID, "does not\nclose `REC-RDY-02`"],
    }
    for relative, required in fragments.items():
        content = read_text(relative)
        normalized_content = " ".join(content.replace("`", "").split())
        for fragment in required:
            require(
                fragment in content or " ".join(fragment.replace("`", "").split()) in normalized_content,
                f"{relative} active v0.6 metadata missing: {fragment}",
            )
    matrix = read_text("docs/stage0/device-matrix.yaml")
    for fragment in (f"gate_set: {GATE_ID}", f"protocol: {PROTOCOL_ID}", "mandatory_fault_rows: 46", "total_injections: 184", "total_injections: 138", "implementation_allowed: false", "execution_allowed: false"):
        require(fragment in matrix, f"Device matrix active metadata missing: {fragment}")


def validate_all(gate: dict[str, Any], protocol: dict[str, Any], include_filesystem_hashes: bool = True) -> None:
    if include_filesystem_hashes:
        validate_immutable_history(gate, protocol)
    validate_gate(gate)
    validate_protocol(protocol)


def expect_negative(name: str, mutation: Callable[[dict[str, Any], dict[str, Any]], None], validate_history: bool = False) -> None:
    gate = read_json(GATE_PATH)
    protocol = read_json(PROTOCOL_PATH)
    mutation(gate, protocol)
    try:
        validate_all(gate, protocol, include_filesystem_hashes=validate_history)
    except (ValueError, KeyError, StopIteration):
        print(f"PASS negative {name}")
        return
    raise ValueError(f"Negative test unexpectedly passed: {name}")


def run_negative_tests() -> None:
    def key04(protocol: dict[str, Any]) -> dict[str, Any]:
        return next(row for row in effective_rows(protocol) if row["id"] == "KEY-04")

    def kcf07(protocol: dict[str, Any]) -> dict[str, Any]:
        return next(row for row in effective_rows(protocol) if row["id"] == "KCF-07")

    tests: list[tuple[str, Callable[[dict[str, Any], dict[str, Any]], None]]] = [
        ("key04-successful-decrypt", lambda _g, p: key04(p).__setitem__("successfulDecryptAllowed", True)),
        ("key04-post-decrypt-parser-mismatch", lambda _g, p: key04(p).__setitem__("postDecryptParserOrPlaintextMismatchAllowed", True)),
        ("key04-non-auth-decrypt-outcome", lambda _g, p: key04(p).__setitem__("decryptOutcome", "SUCCESS_OR_AUTH_FAILURE")),
        ("key04-classification-alternative", lambda _g, p: key04(p).__setitem__("expectedClassification", "CORRUPT_KEY_CONFIRMATION")),
        ("kcf07-malformed-plaintext-classification", lambda _g, p: kcf07(p).__setitem__("expectedClassification", "KEY_UNAVAILABLE_KEY_MISMATCH")),
        ("kcf07-requires-successful-decrypt", lambda _g, p: kcf07(p).__setitem__("requiredObservation", "decrypt fails")),
        ("kcf07-malformed-plaintext-coverage", lambda _g, p: kcf07(p)["coveredPostDecryptFailures"].pop(0)),
        ("active-matrix-count", lambda _g, p: effective_rows(p).pop()),
        ("active-matrix-unique-ids", lambda _g, p: effective_rows(p)[1].__setitem__("id", "COR-01")),
        ("active-matrix-key04-once", lambda _g, p: effective_rows(p)[13].__setitem__("id", "KEY-04")),
        ("active-matrix-effective-rows", lambda _g, p: effective_rows(p)[0].__setitem__("effective", False)),
        ("phase-a-count", lambda g, _p: g["faultCampaignProfiles"]["phaseA"].__setitem__("phaseATotalInjections", 183)),
        ("full-physical-count", lambda g, _p: g["faultCampaignProfiles"]["fullPhysicalCampaign"].__setitem__("fullPhysicalTotalInjections", 137)),
    ]
    for name, mutation in tests:
        expect_negative(name, mutation)

    gate = read_json(GATE_PATH)
    protocol = read_json(PROTOCOL_PATH)
    mutated = copy.deepcopy(gate)
    mutated["retainedAuditArtifacts"][4]["protocolSha256"] = "0" * 64
    try:
        validate_immutable_history(mutated, protocol)
    except ValueError:
        print("PASS negative historical-v0.1-v0.5-hash-pin")
    else:
        raise ValueError("Negative test unexpectedly passed: historical-v0.1-v0.5-hash-pin")


def main() -> int:
    gate = read_json(GATE_PATH)
    protocol = read_json(PROTOCOL_PATH)
    validate_all(gate, protocol)
    validate_readiness_and_evidence(gate)
    validate_dependency_and_scope_boundary()
    validate_active_metadata()
    if "--self-test" in sys.argv[1:]:
        run_negative_tests()
    print(
        "POC-RECOVERY-001 governance v0.6 validation passed; 15 v0.1-v0.5 audit artifacts immutable, "
        "46 unique effective rows with one KEY-04, KEY-04 authentication/AAD failure only -> "
        "KEY_UNAVAILABLE_KEY_MISMATCH, KCF-07 successful-decrypt malformed plaintext -> "
        "CORRUPT_KEY_CONFIRMATION, Phase A=184, full physical=138, hard kills=120/candidate separate, "
        "implementationAllowed=false, executionAllowed=false"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, StopIteration, json.JSONDecodeError, subprocess.CalledProcessError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
