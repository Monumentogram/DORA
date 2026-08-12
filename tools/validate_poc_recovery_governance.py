#!/usr/bin/env python3
"""Fail-closed validation for the governance-only POC-RECOVERY-001 v0.5 package."""

from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any, Callable


ROOT = Path(__file__).resolve().parents[1]
GATE_ID = "poc-recovery-stage0-v0.5"
PROTOCOL_ID = "poc-recovery-protocol-stage0-v0.5"
REVIEWED_V04_HEAD = "c3eae5c3fbe5cba6a96ad827441cfe4e3f1bfc55"
BASE_HEAD = "849d9d0406a619b334c9b707a4b6b42b34885b4b"
SQLITE_STATUS = "RECOVERY_STAGE0_V0_5_SQLITE_PROFILE_SELECTED_FRESH_PREFLIGHT_INCOMPLETE"
JSR_POLICY_ID = "REC-JSR305-EXCLUDE-001"
TINK_COORDINATE = "com.google.crypto.tink:tink-android:1.23.0"
JSR_COORDINATE = "com.google.code.findbugs:jsr305:3.0.2"
R8_RULES = [
    "-dontwarn javax.annotation.Nullable",
    "-dontwarn javax.annotation.concurrent.GuardedBy",
    "-dontwarn javax.annotation.concurrent.ThreadSafe",
]

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

EXPECTED_BLOCKER_OBJECTS = [
    {
        "id": "REC-RDY-01-PRODUCT-IP-FINAL-APPROVAL",
        "priority": "P0",
        "status": "PROSPECTIVE_POLICY_AND_EXACT_PACKET_EVIDENCE_CLOSED_ACTUAL_GRAPH_FINAL_DISPOSITION_PENDING",
        "owner": "Project owner",
        "condition": "Project owner / Stage 0 Product-IP approved REC-JSR305-EXCLUDE-001 and the exact governance packet evidence is verified. The future actual recovery graph/package/R8 evidence and its scoped Product/IP disposition remain open; dependency admission, excluded JSR-305 use/distribution, implementation and execution remain unapproved.",
    },
    {
        "id": "REC-RDY-02-ACCOUNTABLE-ENGINEERING-SECURITY-REVIEW",
        "priority": "P0",
        "status": "OPEN_UNASSIGNED",
        "owner": "Project owner",
        "condition": "Assign a distinct accountable recovery Engineering/Security reviewer and record a read-only v0.5 disposition including the canonical eight-class KEY algorithm and separate campaign profiles; current Codex remediation is not claimed formally independent.",
    },
    {
        "id": "REC-RDY-03-STREAMING-IMPLEMENTATION-VERIFICATION",
        "priority": "P0",
        "status": "OPEN_BY_DESIGN",
        "owner": "Future recovery implementation and accountable review tasks",
        "condition": "Implement and non-metrically verify exact public construction, DURABLE_ONE_SEGMENT_LOOKAHEAD, q/R math, read accounting, EOF handling and public barriers.",
    },
    {
        "id": "REC-RDY-04-MICROFILE-IMPLEMENTATION-VERIFICATION",
        "priority": "P0",
        "status": "OPEN_BY_DESIGN",
        "owner": "Future recovery implementation and accountable review tasks",
        "condition": "Implement and non-metrically verify exact microfile parameters, manifest/AAD parsers, 13-step durable key-confirmation bootstrap, canonical post-decrypt-aware KEY reconciliation, Keystore envelopes, durability, SQLite, path, quarantine and 46-row fault contracts.",
    },
    {
        "id": "REC-RDY-05-FUTURE-RESOLVED-GRAPH",
        "priority": "P0",
        "status": "OPEN_BY_DESIGN",
        "owner": "Future recovery implementation task",
        "condition": "After separately authorized :poc:recovery scaffolding, enumerate all resolvable compile, runtime, unit-test, androidTest, benchmark and release configurations, packaging/runtime-artifact inputs, dependency locks and verification metadata owned by that module; prove zero resolved com.google.code.findbugs:jsr305:3.0.2 components and zero packaged JSR-305 javax.annotation definitions; verify the Tink-local exclude and exact three-line R8 rule with no broader dontwarn; pass release R8 with no unresolved missing classes and independently resolve javax.lang.model.element.Modifier if present. Existing other-module buildscript/AGP/UTP/lint/tooling and app/capture/search lockfile occurrences are outside this boundary and alone do not block recovery. Any occurrence inside recovery scope fails closed.",
    },
    {
        "id": "REC-RDY-06-DEVICE-SQLITE-PREFLIGHT",
        "priority": "P0",
        "status": "OPEN",
        "owner": "Future authorized preflight task",
        "condition": "Record exact-commit emulator and D2 SQLite, Keystore and filesystem preflight evidence, including effective WAL/FULL/zero-autocheckpoint/foreign-key values, sqlite_version(), sqlite_source_id() and canonical compile-options digest; any mismatch blocks execution.",
    },
    {
        "id": "REC-RDY-07-HARNESS-ABSENT",
        "priority": "P0",
        "status": "OPEN_BY_OWNER_INSTRUCTION",
        "owner": "Future separately scoped implementation task",
        "condition": "Obtain separate owner implementation scope, then implement and safely non-metrically verify an isolated common harness only after repeat exact-HEAD read-only review and accountable v0.5 Engineering/Security review.",
    },
    {
        "id": "REC-RDY-08-OWNER-EXECUTION-AUTHORIZATION",
        "priority": "P0",
        "status": "WITHHELD",
        "owner": "Project owner",
        "condition": "After all other Phase A prerequisites, record a separate authorization that explicitly changes executionAllowed to true.",
    },
    {
        "id": "REC-RDY-09-D1-D5-FULL-VERDICT",
        "priority": "P1",
        "status": "DEFERRED",
        "owner": "Project owner",
        "condition": "Provide physical D1 and D5 only when a full 138-injection D1/D2/D5 fault profile is later desired; no purchase is required now, and PASS is forbidden while D1/D5 are deferred.",
    },
    {
        "id": "REC-RDY-10-PRODUCTION-LEGAL-SECURITY",
        "priority": "P1",
        "status": "OUTSIDE_STAGE0_UNASSIGNED",
        "owner": "Project owner",
        "condition": "Assign Production Legal and Production Security before any production admission or redistribution; recovery-scoped approval does not satisfy this.",
    },
    {
        "id": "REC-RDY-11-SUPPLY-CHAIN-AUTHENTICITY",
        "priority": "P0",
        "status": "POLICY_CLOSED_PACKET_EVIDENCE_CLOSED_ACTUAL_FUTURE_GRAPH_OPEN_BLOCKED",
        "owner": "Project owner / Stage 0 Product-IP",
        "condition": "State A prospective REC-JSR305-EXCLUDE-001 is CLOSED/APPROVED. State B exact governance authenticity/LICENSE/NOTICE evidence is CLOSED/VERIFIED, including immutable JetBrains annotations LICENSE/NOTICE; excluded jsr305:3.0.2 terms remain uninterpreted and use/distribution unapproved. State C future actual :poc:recovery graph/package/R8 evidence and scoped Product/IP disposition is OPEN/BLOCKED. Readiness requires absence in recovery scope, not approval to use the excluded artifact; no dependency or production admission is implied.",
    },
]

INHERITED_FAULT_IDS = {
    *(f"COR-{index:02d}" for index in range(1, 7)),
    *(f"TRU-{index:02d}" for index in range(1, 4)),
    *(f"KEY-{index:02d}" for index in range(1, 8)),
    *(f"SPL-{index:02d}" for index in range(1, 6)),
    "RBK-01", "RBK-02", "PAR-01",
    "QUA-01", "QUA-02", "QUA-03",
    "IDE-01", "IDE-02", "EVT-01",
    "CLN-01", "CLN-02", "CLN-03",
}
V04_FAULT_IDS = {
    *(f"KCB-{index:02d}" for index in range(1, 7)),
    *(f"KCF-{index:02d}" for index in range(1, 7)),
}
EXPECTED_FAULT_IDS = INHERITED_FAULT_IDS | V04_FAULT_IDS | {"KCF-07"}


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
    for relative, expected in IMMUTABLE_AUDIT_HASHES.items():
        require(sha256(relative) == expected, f"Superseded audit artifact changed: {relative}")

    retained = gate["retainedAuditArtifacts"]
    require(
        [item["version"] for item in retained]
        == [f"poc-recovery-stage0-v0.{version}" for version in range(1, 5)],
        "Gate Set v0.1-v0.4 retained history drift",
    )
    recorded: dict[str, str] = {}
    for item in retained:
        require(
            item["disposition"] == "SUPERSEDED_AUDIT_ARTIFACT_NON_EXECUTABLE",
            "A retained recovery version became executable",
        )
        for locator_key, digest_key in (
            ("markdownLocator", "markdownSha256"),
            ("gateLocator", "gateSha256"),
            ("protocolLocator", "protocolSha256"),
        ):
            recorded[item[locator_key]] = item[digest_key]
    require(recorded == IMMUTABLE_AUDIT_HASHES, "Gate Set immutable v0.1-v0.4 hashes drift")

    inherited_gate = gate["inheritsExactV04GateSet"]
    require(
        inherited_gate["locator"] == "docs/stage0/poc-recovery-gate-set-stage0-v0.4.json"
        and inherited_gate["sha256"] == IMMUTABLE_AUDIT_HASHES[inherited_gate["locator"]]
        and inherited_gate["allUnchangedSemanticsInherited"] is True
        and inherited_gate["overriddenSections"]
        == [
            "active identity and metadata",
            "retained audit artifacts and findings ledgers",
            "key-confirmation taxonomy summary",
            "mandatory fault rows and campaign profiles",
            "canonical blocker IDs",
        ],
        "v0.5 exact v0.4 Gate Set inheritance boundary drift",
    )

    inherited = protocol["inheritsExactV04Contract"]
    require(
        inherited["locator"] == "docs/stage0/poc-recovery-protocol-stage0-v0.4.json"
        and inherited["sha256"] == IMMUTABLE_AUDIT_HASHES[inherited["locator"]]
        and inherited["allUnchangedSemanticsInherited"] is True
        and inherited["overriddenSections"]
        == ["recoveryTaxonomyV04", "reconciliationAmendment", "faultCampaign"],
        "v0.5 exact v0.4 inheritance boundary drift",
    )


def validate_blockers(gate: dict[str, Any], readiness: dict[str, Any]) -> None:
    gate_ids = gate["blockers"]
    readiness_ids = [item["id"] for item in readiness["blockers"]]
    require(len(gate_ids) == len(set(gate_ids)), "Duplicate Gate Set blocker ID")
    require(len(readiness_ids) == len(set(readiness_ids)), "Duplicate readiness blocker ID")
    require(gate_ids == CANONICAL_BLOCKERS, "Gate Set blocker IDs are not exact canonical order")
    require(readiness_ids == CANONICAL_BLOCKERS, "Gate Set/readiness blocker ID lists differ")
    require(
        set(gate_ids) == set(CANONICAL_BLOCKERS),
        "Unknown, extra or missing blocker ID",
    )
    require(
        gate["blockerStateSource"] == "docs/evidence/poc-recovery-001/readiness.json",
        "Gate Set blocker state source drift",
    )
    require(
        readiness["blockers"] == EXPECTED_BLOCKER_OBJECTS,
        "Readiness blocker status/priority/owner/condition objects drift",
    )


def validate_gate(gate: dict[str, Any]) -> None:
    require(
        gate["schemaVersion"] == 5
        and gate["pocId"] == "POC-RECOVERY-001"
        and gate["gateSetVersion"] == GATE_ID
        and gate["protocolId"] == PROTOCOL_ID,
        "Active v0.5 Gate Set identity drift",
    )
    require(
        gate["executionAllowed"] is False and gate["implementationAllowed"] is False,
        "Gate Set authorized implementation/execution",
    )
    require(
        gate["supersedes"]
        == {
            "gateSetVersion": "poc-recovery-stage0-v0.4",
            "disposition": "SUPERSEDED_AUDIT_ARTIFACT_NON_EXECUTABLE",
            "reviewedCommit": REVIEWED_V04_HEAD,
        },
        "v0.4 supersession record drift",
    )
    require(
        gate["findingsLedgers"][-1].endswith("review-findings-v0.4.json"),
        "v0.4 advisory ledger missing",
    )
    require(
        all(value is False for value in gate["scope"].values() if isinstance(value, bool)),
        "Governance-only scope widened",
    )
    authorization = gate["executionAuthorization"]
    require(
        authorization
        == {
            "status": "WITHHELD_PENDING_SEPARATE_OWNER_AUTHORIZATION",
            "authorizedBy": None,
            "authorizedOn": None,
            "authorizationRecord": None,
            "implicitFlipForbidden": True,
        },
        "Execution authorization boundary drift",
    )
    approvals = gate["approvalState"]
    require(
        approvals["prospectivePolicyApproved"] is True
        and approvals["governanceAuthenticityAndLicenseEvidenceVerified"] is True
        and approvals["actualFutureGraphProductIpDisposition"]
        == "OPEN_BLOCKED_UNTIL_AUTHORIZED_IMPLEMENTATION_GRAPH_EXISTS"
        and approvals["productIpFinalApproval"] is False
        and approvals["accountableIndependentEngineeringSecurityReviewer"] is None
        and approvals["currentCodexReviewClaimedFormallyIndependent"] is False
        and approvals["productionLegalReviewer"] is None
        and approvals["productionSecurityReviewer"] is None,
        "Approval/reviewer fail-closed state drift",
    )
    product = gate["productIpStates"]
    require(
        product
        == {
            "stateAProspectivePolicy": "CLOSED_APPROVED",
            "stateBGovernanceEvidence": "CLOSED_VERIFIED",
            "stateCFutureActualGraphPackageR8Disposition": "OPEN_BLOCKED",
            "jsr305UseOrDistributionApproved": False,
            "dependencyAdmission": False,
            "productionAdmission": False,
        },
        "Three-state Product/IP contract drift",
    )
    key_gate = gate["keyConfirmationGate"]
    require(
        key_gate["canonicalUniqueClassificationCount"] == 8
        and key_gate["recoveryDecisionStepCount"] == 9
        and key_gate["plaintextValidationPhase"] == "POST_DECRYPT_ONLY"
        and key_gate["inheritedV04MandatoryFaultRows"] == 12
        and key_gate["mandatoryFaultRowsAddedV05"] == 1,
        "Key-confirmation Gate Set summary drift",
    )
    require(
        gate["mandatoryFaultRowCount"] == 46
        and len(gate["mandatoryFaultIds"]) == 46
        and set(gate["mandatoryFaultIds"]) == EXPECTED_FAULT_IDS,
        "Gate Set mandatory 46-row fault IDs drift",
    )
    validate_campaigns(gate["faultCampaignProfiles"])


def validate_taxonomy(protocol: dict[str, Any]) -> None:
    taxonomy = protocol["canonicalKeyTaxonomyV05"]
    classifications = taxonomy["uniqueClassifications"]
    require(classifications == CANONICAL_TAXONOMY, "Canonical KEY taxonomy order drift")
    require(len(classifications) == len(set(classifications)) == 8, "Canonical taxonomy must have exactly eight unique classifications")
    require(taxonomy["uniqueClassificationCount"] == 8, "Canonical classification count drift")
    new_run = taxonomy["newRunCreation"]
    require(
        new_run["mustExecuteBeforeCreatingRun"] is True
        and len(new_run["occupiedNamespacesChecked"]) == 4
        and new_run["anyOccupiedClassification"] == "KEY_REF_COLLISION"
        and new_run["overwriteOrReplacementAllowed"] is False,
        "New-run collision algorithm drift",
    )
    algorithm = taxonomy["recoveryReconciliationAlgorithm"]
    require([item["step"] for item in algorithm] == list(range(1, 10)), "Recovery algorithm step order drift")
    require(
        [item["classification"] for item in algorithm]
        == [
            "INCOMPLETE_KEY_BOOTSTRAP",
            "KEY_CONFIRMATION_MISSING",
            "CORRUPT_KEY_CONFIRMATION",
            "KEY_UNAVAILABLE",
            "KEY_UNAVAILABLE_KEY_MISMATCH",
            "CORRUPT_KEY_CONFIRMATION",
            "KEY_UNAVAILABLE",
            "CORRUPT_KEY_ENVELOPE",
            "KEY_ENVELOPE_AUTH_FAILURE",
        ],
        "Recovery algorithm classification order drift",
    )
    require(algorithm[2]["decryptAllowed"] is False, "Stored-identity mismatch must forbid decrypt")
    require(algorithm[4]["replacementKeyAllowed"] is False, "Decrypt mismatch must forbid replacement key")
    require(algorithm[5]["validationPhase"] == "POST_DECRYPT_ONLY", "Plaintext validation moved before decrypt")
    require(
        taxonomy["plaintextMagicSchemaParserOrTrailingValidationBeforeDecryptAllowed"] is False
        and taxonomy["ambiguousExpectedOutcomeAllowed"] is False,
        "Taxonomy allows pre-decrypt plaintext validation or ambiguity",
    )
    conditions = " ".join(item["condition"] for item in algorithm)
    for fragment in (
        "durable run row is absent",
        "recorded ciphertext length",
        "alias is absent, invalidated or unusable",
        "Aead.decrypt() ends in authentication or AAD failure",
        "bounded plaintext parser, magic, schema, no-trailing-bytes",
        "mandatory subsequent key reference or key envelope is absent",
        "length, SHA-256, encoding or parser validation fails",
        "AEAD, AAD or tag verification fails",
    ):
        require(fragment in conditions, f"Recovery algorithm is missing: {fragment}")

    reconciliation = protocol["reconciliationV05"]
    require(len(reconciliation) == 10, "Reconciliation state coverage drift")
    require("POST_DECRYPT" not in " ".join(reconciliation).upper(), "Reconciliation key names must remain state-oriented")
    require(
        "only after successful decrypt" in reconciliation["CONFIRMATION_DECRYPTED_PLAINTEXT_INVALID"],
        "Post-decrypt reconciliation boundary missing",
    )


def validate_campaigns(campaigns: dict[str, Any]) -> None:
    phase = campaigns["phaseA"]
    full = campaigns["fullPhysicalCampaign"]
    reuse = campaigns["d2Reuse"]
    require(
        phase["rows"] == 46
        and phase["perRow"] == {"PINNED_API36_X86_64_EMULATOR": 3, "PHYSICAL_D2": 1}
        and phase["emulatorInjections"] == 46 * 3 == 138
        and phase["physicalD2Injections"] == 46
        and phase["phaseATotalInjections"] == 46 * 4 == 184
        and phase["allowedVerdicts"] == ["FAIL", "INCONCLUSIVE"]
        and phase["passAllowed"] is False,
        "Phase A profile/arithmetic drift",
    )
    require(
        full["rows"] == 46
        and full["perRow"] == {"PHYSICAL_D1": 1, "PHYSICAL_D2": 1, "PHYSICAL_D5": 1}
        and full["fullPhysicalTotalInjections"] == 46 * 3 == 138
        and full["passRequiresCompleteD1D2D5Profile"] is True
        and full["D1AndD5Deferred"] is True,
        "Full physical campaign profile/arithmetic drift",
    )
    require(
        all(value is True for key, value in reuse.items() if key.startswith("allowedOnlyIf"))
        and reuse["otherwiseRepeatD2"] is True
        and reuse["additionalD1D5InjectionsWithValidReuse"] == 46 * 2 == 92,
        "D2 reuse criteria/arithmetic drift",
    )
    require(
        campaigns["hardKillAttemptsPerCandidate"] == 120
        and campaigns["separateFromHardKillDenominator"] is True
        and campaigns["existingRepetitionsValidityCriteriaAndFailureGatesWeakened"] is False,
        "Hard-kill denominator or inherited gates drift",
    )


def validate_protocol(protocol: dict[str, Any]) -> None:
    require(
        protocol["schemaVersion"] == 5
        and protocol["protocolId"] == PROTOCOL_ID
        and protocol["pocId"] == "POC-RECOVERY-001",
        "Active v0.5 protocol identity drift",
    )
    require(
        protocol["implementationAllowed"] is False and protocol["executionAllowed"] is False,
        "Protocol authorized implementation/execution",
    )
    validate_taxonomy(protocol)
    fault = protocol["faultCampaign"]
    require(
        (fault["inheritedV03MandatoryRows"], fault["addedV04KeyConfirmationRows"], fault["addedV05MandatoryRows"], fault["mandatoryFaultRowCount"])
        == (33, 12, 1, 46),
        "Protocol fault row totals drift",
    )
    case = fault["newV05Case"]
    require(
        case["id"] == "KCF-07"
        and "malformed key-confirmation plaintext" in case["injection"]
        and "correct current alias and exact AAD" in case["injection"]
        and "outer ciphertext length and SHA-256" in case["injection"]
        and "Aead.decrypt() succeeds" in case["requiredObservation"]
        and case["expected"] == "post-decrypt exact plaintext validation returns CORRUPT_KEY_CONFIRMATION"
        and case["replacementKeyAllowed"] is False,
        "KCF-07 fault oracle drift",
    )
    overrides = fault["inheritedV04CaseOracleOverrides"]
    require(
        [item["id"] for item in overrides]
        == [*(f"KCB-{index:02d}" for index in range(1, 7)), *(f"KCF-{index:02d}" for index in range(1, 7))],
        "Inherited v0.4 fault-oracle override IDs drift",
    )
    override_map = {item["id"]: item["expected"] for item in overrides}
    require(
        all(override_map[f"KCB-{index:02d}"].startswith("INCOMPLETE_KEY_BOOTSTRAP") for index in range(1, 6))
        and override_map["KCB-06"].startswith("VALID_DURABLE_BOOTSTRAP")
        and override_map["KCF-01"].startswith("KEY_CONFIRMATION_MISSING")
        and all("decrypt forbidden" in override_map[f"KCF-{index:02d}"] for index in (2, 3))
        and all(override_map[f"KCF-{index:02d}"].startswith("KEY_UNAVAILABLE_KEY_MISMATCH") for index in (4, 5))
        and "no exact-plaintext alternative" in override_map["KCF-04"]
        and override_map["KCF-06"].startswith("KEY_REF_COLLISION"),
        "Inherited v0.4 fault-oracle classifications drift",
    )
    validate_protocol_campaigns(fault)
    require(protocol["canonicalBlockerIds"] == CANONICAL_BLOCKERS, "Protocol canonical blockers drift")
    require(
        protocol["evidencePolicy"]
        == {
            "formalReviewer": False,
            "accountableEngineeringSecurityReviewer": None,
            "productionLegalReviewer": None,
            "productionSecurityReviewer": None,
            "futureExecutionEvidenceAllowedByThisProtocol": False,
        },
        "Protocol reviewer/evidence boundary drift",
    )


def validate_protocol_campaigns(fault: dict[str, Any]) -> None:
    phase = fault["phaseA"]
    full = fault["fullPhysicalCampaign"]
    reuse = fault["phaseAD2ReuseForFullPhysicalCampaign"]
    require(
        phase["rows"] == 46
        and phase["perRow"] == {"PINNED_API36_X86_64_EMULATOR": 3, "PHYSICAL_D2": 1}
        and phase["phaseATotalInjections"] == 184
        and phase["allowedVerdicts"] == ["FAIL", "INCONCLUSIVE"]
        and phase["passAllowed"] is False,
        "Protocol Phase A drift",
    )
    require(
        full["rows"] == 46
        and full["perRow"] == {"PHYSICAL_D1": 1, "PHYSICAL_D2": 1, "PHYSICAL_D5": 1}
        and full["fullPhysicalTotalInjections"] == 138
        and full["D1Status"] == full["D5Status"] == "DEFERRED"
        and full["passRequiresCompleteD1D2D5Profile"] is True,
        "Protocol full physical profile drift",
    )
    require(
        reuse["allowedOnlyWhenAllMatch"]
        == [
            "exact commit",
            "protocol and Gate Set version",
            "fixture digest",
            "injection definition",
            "device identity and profile",
            "fresh preflight",
            "validity criteria",
        ]
        and reuse["otherwiseRepeatD2"] is True
        and reuse["additionalD1D5InjectionsWhenReuseAllowed"] == 92,
        "Protocol D2 reuse contract drift",
    )
    require(
        fault["hardKillCampaign"] == {
            "attemptsPerCandidate": 120,
            "separateFromFaultInjectionDenominators": True,
        }
        and fault["existingRepetitionsValidityCriteriaAndFailureGatesWeakened"] is False,
        "Protocol hard-kill/inherited fault gates drift",
    )


def current_lockfile_occurrences() -> set[tuple[str, str]]:
    result: set[tuple[str, str]] = set()
    pattern = re.compile(r"^(com\.google\.(?:code\.findbugs:jsr305|crypto\.tink:tink):[^=]+)=")
    for path in (ROOT / "android").rglob("gradle.lockfile"):
        for line in path.read_text(encoding="utf-8").splitlines():
            match = pattern.match(line)
            if match:
                result.add((path.relative_to(ROOT).as_posix(), match.group(1)))
    return result


def validate_dependency_boundary() -> None:
    inventory = read_json("docs/evidence/poc-recovery-001/dependency-inventory.json")
    base = read_json("docs/evidence/poc-recovery-001/base-lockfile-tooling-inventory-2026-08-12.json")
    analysis = read_json("docs/evidence/poc-recovery-001/jsr305-exclusion-analysis-2026-08-12.json")
    authenticity = read_json("docs/evidence/poc-recovery-001/dependency-ip-authenticity-v0.3.json")
    require(inventory["dependencyAdmission"] is False and inventory["runtimeGraphModified"] is False, "Dependency inventory admitted a runtime graph")
    require(base["boundaryId"] == JSR_POLICY_ID and base["baseCommit"] == BASE_HEAD, "Base recovery boundary identity drift")
    require(
        {(item["lockfile"], item["coordinate"]) for item in base["existingBaseLockfileOccurrences"]}
        == current_lockfile_occurrences(),
        "Base Tink/JSR305 lockfile occurrence inventory drift",
    )
    require(
        analysis["prospectivePolicy"]["status"] == "APPROVED_PROSPECTIVE_POLICY_ONLY"
        and analysis["prospectivePolicy"]["requiredResolvedComponentCount"] == 0
        and analysis["prospectivePolicy"]["requiredR8Rules"] == R8_RULES,
        "Prospective JSR305 exclusion policy drift",
    )
    require(
        authenticity["approvalBoundary"]["futureActualGraphProductIpDisposition"] == "OPEN_BLOCKED"
        and authenticity["approvalBoundary"]["dependencyAdmission"] is False
        and authenticity["approvalBoundary"]["productionAdmission"] is False,
        "Authenticity approval boundary drift",
    )
    require(not (ROOT / "android" / "poc" / "recovery").exists(), "android/poc/recovery exists in governance-only package")
    require(":poc:recovery" not in read_text("android/settings.gradle.kts"), "Recovery module is included in settings")
    gradle_inputs = [
        *(ROOT / "android").rglob("*.gradle"),
        *(ROOT / "android").rglob("*.gradle.kts"),
        ROOT / "android" / "gradle" / "libs.versions.toml",
    ]
    contaminated = [
        path.relative_to(ROOT).as_posix()
        for path in gradle_inputs
        if path.is_file()
        and TINK_COORDINATE in path.read_text(encoding="utf-8")
    ]
    require(not contaminated, f"tink-android:1.23.0 was wired: {contaminated}")
    diff = subprocess.run(
        ["git", "diff", "--name-only", BASE_HEAD, "--", "android"],
        cwd=ROOT,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        check=False,
    )
    require(diff.returncode == 0, f"Unable to compare Android scope: {diff.stderr.strip()}")
    require(not diff.stdout.strip(), f"Governance PR changed Android scope: {diff.stdout.splitlines()}")


def validate_readiness_roles_and_traceability(gate: dict[str, Any]) -> None:
    readiness = read_json("docs/evidence/poc-recovery-001/readiness.json")
    roles = read_json("docs/evidence/poc-recovery-001/review-roles.json")
    ledger = read_json("docs/evidence/poc-recovery-001/review-findings-v0.4.json")
    index = read_json("docs/evidence/poc-recovery-001/evidence-index.json")
    provenance = read_json("docs/evidence/poc-recovery-001/sqlite-platform-provenance.json")
    security = read_json("docs/evidence/poc-recovery-001/security-advisory-inventory.json")

    require(readiness["schemaVersion"] == 6 and readiness["status"].startswith("BLOCKED_"), "Readiness v0.5 status drift")
    for key in (
        "executionAllowed", "implementationAllowed", "implementationAllowedByThisPackage", "measuredExecutionAllowed",
        "runtimeDependencyAdded", "recoveryModuleExists", "harnessImplemented",
        "nonMetricImplementationVerificationPassed", "exactFutureResolvedGraphReviewed",
        "killCampaignExecuted", "deviceTestsExecuted", "benchmarksExecuted", "productionAppChanged",
    ):
        require(readiness[key] is False, f"Governance-only readiness invariant violated: {key}")
    package = readiness["packageArtifacts"]
    require(
        package["activeGateSetVersion"] == GATE_ID
        and package["activeProtocolId"] == PROTOCOL_ID
        and package["governanceRemediationV05Present"] is True
        and package["v04RetainedAsSupersededAuditArtifact"] is True
        and package["v04Executable"] is False,
        "Readiness active v0.5 package metadata drift",
    )
    validate_blockers(gate, readiness)
    phase = readiness["phaseA"]
    full = readiness["fullVerdict"]
    require(
        phase["faultRows"] == 46
        and phase["phaseATotalInjections"] == 184
        and phase["possibleVerdicts"] == ["FAIL", "INCONCLUSIVE"]
        and phase["passAllowed"] is False
        and phase["hardKillAttemptsPerCandidate"] == 120
        and phase["hardKillDenominatorSeparate"] is True,
        "Readiness Phase A state drift",
    )
    require(
        full["requiredPhysicalProfiles"] == ["D1", "D2", "D5"]
        and full["fullPhysicalTotalInjections"] == 138
        and full["additionalD1D5InjectionsWithValidD2Reuse"] == 92
        and full["otherwiseRepeatD2"] is True
        and full["passAllowedWithoutCompleteD1D2D5Profile"] is False
        and full["deferred"] is True,
        "Readiness full physical state drift",
    )

    require(
        roles["schemaVersion"] == 6
        and roles["activeGateSetVersion"] == GATE_ID
        and roles["activeProtocolId"] == PROTOCOL_ID
        and roles["canonicalBlockerIds"] == CANONICAL_BLOCKERS,
        "Review-role active metadata/blocker list drift",
    )
    role_map = roles["roles"]
    require(role_map["packageAuthor"]["claimedFormallyIndependentReviewer"] is False, "Codex claimed formal independence")
    independent = role_map["independentRecoveryEngineeringSecurity"]
    require(
        independent["reviewer"] is None
        and independent["status"] == "UNASSIGNED_BLOCKING"
        and independent["currentCodexRemediationClaimedFormallyIndependent"] is False,
        "Accountable Engineering/Security reviewer boundary drift",
    )
    require(
        role_map["stage0ProductIp"]["futureExactGraphDisposition"]["status"] == "OPEN_BLOCKED"
        and role_map["productionLegal"]["reviewer"] is None
        and role_map["productionSecurity"]["reviewer"] is None
        and role_map["executionAuthorizer"]["status"] == "AUTHORIZATION_WITHHELD",
        "Role fail-closed states drift",
    )

    require(
        ledger["schemaVersion"] == 1
        and ledger["sourceReviewedCommit"] == REVIEWED_V04_HEAD
        and ledger["reviewedGateSetVersion"] == "poc-recovery-stage0-v0.4"
        and ledger["activeRemediationProtocolId"] == PROTOCOL_ID
        and ledger["remediationVersion"] == "v0.5"
        and ledger["sanitized"] is True
        and ledger["formalReviewer"] is False,
        "REC-ADV-V04 ledger identity drift",
    )
    findings = ledger["findings"]
    require([item["id"] for item in findings] == [f"REC-ADV-V04-{index:03d}" for index in range(1, 5)], "REC-ADV finding IDs drift")
    require([item["severity"] for item in findings] == ["P0", "P1", "P1", "P2"], "REC-ADV severity mapping drift")
    require(
        all(
            item["sourceReviewedCommit"] == REVIEWED_V04_HEAD
            and item["remediationVersion"] == "v0.5"
            and item["disposition"].startswith("CLOSED")
            and item["evidenceLocator"]
            and item["affectedArtifacts"]
            and item["formalReviewer"] is False
            for item in findings
        ),
        "REC-ADV remediation traceability incomplete",
    )

    require(
        index["schemaVersion"] == 3
        and index["activeGateSetVersion"] == GATE_ID
        and index["activeProtocolId"] == PROTOCOL_ID
        and index["implementationAllowed"] is False
        and index["executionAllowed"] is False,
        "Evidence index active metadata drift",
    )
    require(
        {item["locator"]: item["sha256"] for item in index["supersededAuditArtifacts"]}
        == IMMUTABLE_AUDIT_HASHES,
        "Evidence index immutable v0.1-v0.4 hashes drift",
    )
    artifact_ids = {item["id"] for item in index["artifacts"]}
    require(
        {
            "REC-V05-GATE-MARKDOWN", "REC-V05-GATE-JSON", "REC-V05-PROTOCOL-JSON",
            "REC-V05-REMEDIATION", "REC-REVIEW-V04-LEDGER",
        }.issubset(artifact_ids),
        "Evidence index lacks v0.5 artifacts",
    )
    require(
        provenance["schemaVersion"] == 3
        and provenance["status"] == SQLITE_STATUS
        and provenance["activeGateSetVersion"] == GATE_ID
        and provenance["activeProtocolId"] == PROTOCOL_ID
        and provenance["phaseA"]["executionAllowed"] is False
        and provenance["fullPhysicalVerdict"]["D1"] == "UNAVAILABLE_PROVENANCE_UNKNOWN"
        and provenance["fullPhysicalVerdict"]["D5"] == "UNAVAILABLE_PROVENANCE_UNKNOWN",
        "SQLite provenance active/fail-closed state drift",
    )
    require(
        security["schemaVersion"] == 2
        and security["activeGateSetVersion"] == GATE_ID
        and security["activeProtocolId"] == PROTOCOL_ID
        and security["status"] == "SNAPSHOT_COMPLETE_INDEPENDENT_REVIEW_PENDING"
        and all(item["mitigationState"].startswith("V0_5_") for item in security["templateAndProtocolRisks"]),
        "Security evidence active v0.5 metadata drift",
    )


def validate_active_metadata() -> None:
    exact_active_lines = {
        "docs/DORA_MVP1_STAGE_STATUS.md": [
            "Stage state: **GOVERNANCE REMEDIATION v0.5 — PROSPECTIVE KEY TAXONOMY/CAMPAIGN/BLOCKER/METADATA REMEDIATION; IMPLEMENTATION AND EXECUTION BLOCKED**",
        ],
        "docs/stage0/DEC-044-POC-RECOVERY-EXPERIMENT.md": [
            "Gate Set: `poc-recovery-stage0-v0.5`\\",
            "Protocol: `poc-recovery-protocol-stage0-v0.5`\\",
        ],
        "docs/stage0/DORA_MVP1_POC_RECOVERY_OWNER_DECISION_OD14.md": [
            "Active Gate Set: `poc-recovery-stage0-v0.5`\\",
            "Active protocol: `poc-recovery-protocol-stage0-v0.5`",
        ],
        "docs/evidence/poc-recovery-001/README.md": [
            "Status: **PROSPECTIVE PROTOCOL v0.5 — POLICY AND EXACT GOVERNANCE EVIDENCE CLOSED; ACTUAL GRAPH, IMPLEMENTATION AND EXECUTION BLOCKED**\\",
        ],
    }
    for relative, expected_lines in exact_active_lines.items():
        lines = read_text(relative).splitlines()
        for expected_line in expected_lines:
            require(expected_line in lines, f"{relative} exact active metadata line drift: {expected_line}")

    exact_fragments = {
        "docs/DORA_MVP1_STAGE_STATUS.md": [
            "Stage state: **GOVERNANCE REMEDIATION v0.5",
            "poc-recovery-stage0-v0.5",
            "46-row fault contract",
        ],
        "docs/stage0/DEC-044-POC-RECOVERY-EXPERIMENT.md": [
            "Gate Set: `poc-recovery-stage0-v0.5`",
            "Protocol: `poc-recovery-protocol-stage0-v0.5`",
            "46 × 4 = 184",
            "46 × 3 = 138",
        ],
        "docs/stage0/DORA_MVP1_POC_RECOVERY_OWNER_DECISION_OD14.md": [
            "Active Gate Set: `poc-recovery-stage0-v0.5`",
            "Active protocol: `poc-recovery-protocol-stage0-v0.5`",
            "Valid reuse leaves 92 D1/D5 injections",
        ],
        "docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md": [
            "Gate Set `poc-recovery-stage0-v0.5` / protocol `poc-recovery-protocol-stage0-v0.5`",
            "Phase A 184 injections",
            "full physical 138 injections",
        ],
        "docs/DORA_MVP1_IMPLEMENTATION_READINESS.md": [
            "Active recovery Gate Set: `poc-recovery-stage0-v0.5`; active recovery protocol: `poc-recovery-protocol-stage0-v0.5`",
            "46 corruption/replay/rollback",
            "Phase A 184, full physical 138",
        ],
        "docs/evidence/poc-recovery-001/README.md": [
            "PROSPECTIVE PROTOCOL v0.5",
            "immutable v0.1–v0.4 SHA-256 pins",
            "poc-recovery-gate-set-stage0-v0.5.json",
            "poc-recovery-protocol-stage0-v0.5.json",
        ],
        "docs/evidence/poc-recovery-001/governance-remediation-v0.5.md": [
            REVIEWED_V04_HEAD,
            "formalReviewer=false",
            SQLITE_STATUS,
        ],
        "docs/evidence/poc-recovery-001/independent-engineering-security-review-task.md": [
            "POC-RECOVERY-001 v0.5",
            "все 46 строк",
            "Full physical: 46 × (D1 + D2 + D5) = 138",
        ],
        "docs/evidence/poc-recovery-001/ip-stage0-evaluation-review.md": [
            "Active Gate Set: `poc-recovery-stage0-v0.5`",
            "Active protocol: `poc-recovery-protocol-stage0-v0.5`",
        ],
    }
    for relative, fragments in exact_fragments.items():
        content = read_text(relative)
        for fragment in fragments:
            require(fragment in content, f"{relative} active v0.5 metadata missing: {fragment}")
    matrix = read_text("docs/stage0/device-matrix.yaml")
    require(
        f"gate_set: {GATE_ID}" in matrix
        and f"protocol: {PROTOCOL_ID}" in matrix
        and "total_injections: 184" in matrix
        and "total_injections: 138" in matrix
        and "additional_d1_d5_injections_with_valid_d2_reuse: 92" in matrix
        and "implementation_allowed: false" in matrix
        and "execution_allowed: false" in matrix,
        "Device matrix active v0.5 campaign metadata drift",
    )


def validate_no_implementation() -> None:
    require(not (ROOT / "android" / "poc" / "recovery").exists(), "Recovery module exists")
    require(":poc:recovery" not in read_text("android/settings.gradle.kts"), "Recovery module included")
    require("android.permission.RECORD_AUDIO" not in read_text("android/app/src/main/AndroidManifest.xml"), "Production :app microphone permission appeared")


def expect_negative(name: str, mutation: Callable[[dict[str, Any], dict[str, Any], dict[str, Any]], None]) -> None:
    gate = read_json("docs/stage0/poc-recovery-gate-set-stage0-v0.5.json")
    protocol = read_json("docs/stage0/poc-recovery-protocol-stage0-v0.5.json")
    readiness = read_json("docs/evidence/poc-recovery-001/readiness.json")
    mutation(gate, protocol, readiness)
    try:
        validate_gate(gate)
        validate_protocol(protocol)
        validate_blockers(gate, readiness)
    except (ValueError, KeyError):
        print(f"PASS negative {name}")
        return
    raise ValueError(f"Negative test unexpectedly passed: {name}")


def run_negative_tests() -> None:
    def duplicate_blocker(gate: dict[str, Any], _protocol: dict[str, Any], _readiness: dict[str, Any]) -> None:
        gate["blockers"][1] = gate["blockers"][0]

    def unknown_blocker(_gate: dict[str, Any], _protocol: dict[str, Any], readiness: dict[str, Any]) -> None:
        readiness["blockers"][10]["id"] = "REC-RDY-11-UNKNOWN"

    def missing_blocker(gate: dict[str, Any], _protocol: dict[str, Any], _readiness: dict[str, Any]) -> None:
        gate["blockers"].pop()

    def extra_blocker(_gate: dict[str, Any], _protocol: dict[str, Any], readiness: dict[str, Any]) -> None:
        readiness["blockers"].append(
            {
                "id": "REC-RDY-12-UNKNOWN",
                "priority": "P0",
                "status": "OPEN",
                "owner": "Nobody",
                "condition": "Invalid mutation",
            }
        )

    def blocker_state(_gate: dict[str, Any], _protocol: dict[str, Any], readiness: dict[str, Any]) -> None:
        readiness["blockers"][8]["status"] = "OPEN"

    def blocker_priority(_gate: dict[str, Any], _protocol: dict[str, Any], readiness: dict[str, Any]) -> None:
        readiness["blockers"][8]["priority"] = "P0"

    def blocker_owner(_gate: dict[str, Any], _protocol: dict[str, Any], readiness: dict[str, Any]) -> None:
        readiness["blockers"][5]["owner"] = "Unknown"

    def blocker_condition(_gate: dict[str, Any], _protocol: dict[str, Any], readiness: dict[str, Any]) -> None:
        readiness["blockers"][0]["condition"] += " Mutated."

    def taxonomy_duplicate(_gate: dict[str, Any], protocol: dict[str, Any], _readiness: dict[str, Any]) -> None:
        protocol["canonicalKeyTaxonomyV05"]["uniqueClassifications"][7] = "KEY_UNAVAILABLE"

    def taxonomy_order(_gate: dict[str, Any], protocol: dict[str, Any], _readiness: dict[str, Any]) -> None:
        algorithm = protocol["canonicalKeyTaxonomyV05"]["recoveryReconciliationAlgorithm"]
        algorithm[2], algorithm[5] = algorithm[5], algorithm[2]

    def taxonomy_classification(_gate: dict[str, Any], protocol: dict[str, Any], _readiness: dict[str, Any]) -> None:
        protocol["canonicalKeyTaxonomyV05"]["recoveryReconciliationAlgorithm"][3]["classification"] = "KEY_UNAVAILABLE_KEY_MISMATCH"

    def predecrypt_plaintext(_gate: dict[str, Any], protocol: dict[str, Any], _readiness: dict[str, Any]) -> None:
        protocol["canonicalKeyTaxonomyV05"]["plaintextMagicSchemaParserOrTrailingValidationBeforeDecryptAllowed"] = True

    def phase_arithmetic(gate: dict[str, Any], _protocol: dict[str, Any], _readiness: dict[str, Any]) -> None:
        gate["faultCampaignProfiles"]["phaseA"]["phaseATotalInjections"] = 183

    def full_arithmetic(gate: dict[str, Any], _protocol: dict[str, Any], _readiness: dict[str, Any]) -> None:
        gate["faultCampaignProfiles"]["fullPhysicalCampaign"]["fullPhysicalTotalInjections"] = 137

    def reuse_arithmetic(gate: dict[str, Any], _protocol: dict[str, Any], _readiness: dict[str, Any]) -> None:
        gate["faultCampaignProfiles"]["d2Reuse"]["additionalD1D5InjectionsWithValidReuse"] = 91

    tests = {
        "duplicate-blocker-id": duplicate_blocker,
        "unknown-blocker-id": unknown_blocker,
        "missing-blocker-id": missing_blocker,
        "extra-blocker-id": extra_blocker,
        "blocker-state": blocker_state,
        "blocker-priority": blocker_priority,
        "blocker-owner": blocker_owner,
        "blocker-condition": blocker_condition,
        "taxonomy-duplicate": taxonomy_duplicate,
        "taxonomy-order": taxonomy_order,
        "taxonomy-classification": taxonomy_classification,
        "predecrypt-plaintext-validation": predecrypt_plaintext,
        "phase-a-arithmetic": phase_arithmetic,
        "full-physical-arithmetic": full_arithmetic,
        "d2-reuse-arithmetic": reuse_arithmetic,
    }
    for name, mutation in tests.items():
        expect_negative(name, mutation)

    metadata = read_json("docs/evidence/poc-recovery-001/readiness.json")
    metadata["packageArtifacts"]["activeProtocolId"] = "poc-recovery-protocol-stage0-v0.4"
    try:
        gate = read_json("docs/stage0/poc-recovery-gate-set-stage0-v0.5.json")
        validate_readiness_metadata_only(gate, metadata)
    except ValueError:
        print("PASS negative active-version-metadata")
    else:
        raise ValueError("Negative test unexpectedly passed: active-version-metadata")

    gate_metadata = read_json("docs/stage0/poc-recovery-gate-set-stage0-v0.5.json")
    readiness_metadata = read_json("docs/evidence/poc-recovery-001/readiness.json")
    gate_metadata["gateSetVersion"] = "poc-recovery-stage0-v0.4"
    try:
        validate_readiness_metadata_only(gate_metadata, readiness_metadata)
    except ValueError:
        print("PASS negative active-gate-metadata")
    else:
        raise ValueError("Negative test unexpectedly passed: active-gate-metadata")

    provenance_metadata = read_json("docs/evidence/poc-recovery-001/sqlite-platform-provenance.json")
    provenance_metadata["status"] = "RECOVERY_STAGE0_V0_4_SQLITE_PROFILE_SELECTED_FRESH_PREFLIGHT_INCOMPLETE"
    try:
        validate_provenance_metadata_only(provenance_metadata)
    except ValueError:
        print("PASS negative sqlite-status-metadata")
    else:
        raise ValueError("Negative test unexpectedly passed: sqlite-status-metadata")


def validate_readiness_metadata_only(gate: dict[str, Any], readiness: dict[str, Any]) -> None:
    package = readiness["packageArtifacts"]
    require(
        package["activeGateSetVersion"] == gate["gateSetVersion"] == GATE_ID
        and package["activeProtocolId"] == gate["protocolId"] == PROTOCOL_ID,
        "Active readiness metadata mismatch",
    )


def validate_provenance_metadata_only(provenance: dict[str, Any]) -> None:
    require(
        provenance["status"] == SQLITE_STATUS
        and provenance["activeGateSetVersion"] == GATE_ID
        and provenance["activeProtocolId"] == PROTOCOL_ID,
        "Active SQLite provenance metadata mismatch",
    )


def main() -> int:
    gate = read_json("docs/stage0/poc-recovery-gate-set-stage0-v0.5.json")
    protocol = read_json("docs/stage0/poc-recovery-protocol-stage0-v0.5.json")
    validate_immutable_history(gate, protocol)
    validate_gate(gate)
    validate_protocol(protocol)
    validate_dependency_boundary()
    validate_readiness_roles_and_traceability(gate)
    validate_active_metadata()
    validate_no_implementation()
    if "--self-test" in sys.argv[1:]:
        run_negative_tests()
    print(
        "POC-RECOVERY-001 governance v0.5 validation passed; 12 v0.1-v0.4 audit artifacts immutable, "
        "eight-class KEY algorithm and 46 fault rows exact, Phase A=184, full physical=138, "
        "implementationAllowed=false, executionAllowed=false"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, StopIteration, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
