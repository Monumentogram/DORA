#!/usr/bin/env python3
"""Fail-closed validation for the governance-only POC-RECOVERY-001 v0.4 package."""

from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
STAGE0 = ROOT / "docs" / "stage0"
EVIDENCE = ROOT / "docs" / "evidence" / "poc-recovery-001"
GATE_ID = "poc-recovery-stage0-v0.4"
PROTOCOL_ID = "poc-recovery-protocol-stage0-v0.4"
REVIEWED_V03_HEAD = "c61603d30c01c72347aa205c247729ad534c2882"
BASE_HEAD = "849d9d0406a619b334c9b707a4b6b42b34885b4b"
JSR_POLICY_ID = "REC-JSR305-EXCLUDE-001"
JSR_COORDINATE = "com.google.code.findbugs:jsr305:3.0.2"
TINK_COORDINATE = "com.google.crypto.tink:tink-android:1.23.0"
JETBRAINS_COMMIT = "f92ce9af0629ee8dcc8743dcc2c1ca297aaacc7c"
JETBRAINS_LICENSE_URL = (
    "https://github.com/JetBrains/intellij-community/blob/"
    f"{JETBRAINS_COMMIT}/LICENSE.txt"
)
JETBRAINS_NOTICE_URL = (
    "https://github.com/JetBrains/intellij-community/blob/"
    f"{JETBRAINS_COMMIT}/NOTICE.txt"
)
JETBRAINS_LICENSE_SHA256 = (
    "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30"
)
JETBRAINS_NOTICE_SHA256 = (
    "0479f6a86003002dec1da1667f2f8320253c7225c6ffffc05cf7e0988bd8c72c"
)
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
}
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
ADDED_FAULT_IDS = {
    *(f"KCB-{index:02d}" for index in range(1, 7)),
    *(f"KCF-{index:02d}" for index in range(1, 7)),
}


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
    require([item["version"] for item in retained] == [
        "poc-recovery-stage0-v0.1",
        "poc-recovery-stage0-v0.2",
        "poc-recovery-stage0-v0.3",
    ], "Gate Set v0.1-v0.3 retained history drift")
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
    require(recorded == IMMUTABLE_AUDIT_HASHES, "Gate Set immutable history hashes drift")
    inherited = protocol["inheritsExactV03Contract"]
    require(
        inherited == {
            "locator": "docs/stage0/poc-recovery-protocol-stage0-v0.3.json",
            "sha256": IMMUTABLE_AUDIT_HASHES[
                "docs/stage0/poc-recovery-protocol-stage0-v0.3.json"
            ],
            "allUnchangedSemanticsInherited": True,
            "disposition": "INCORPORATED_BY_IMMUTABLE_REFERENCE_AND_SUPERSEDED_AS_STANDALONE_EXECUTION_INPUT",
        },
        "v0.4 exact v0.3 inheritance record drift",
    )


def validate_gate(gate: dict[str, Any]) -> None:
    require(gate["schemaVersion"] == 4, "Recovery Gate Set schema drift")
    require(gate["pocId"] == "POC-RECOVERY-001", "Recovery Gate Set PoC identity drift")
    require(gate["gateSetVersion"] == GATE_ID, "Active recovery Gate Set is not v0.4")
    require(gate["protocolId"] == PROTOCOL_ID, "Active recovery protocol link drift")
    require(gate["executionAllowed"] is False, "Recovery Gate Set authorized execution")
    require(gate["implementationAllowed"] is False, "Recovery Gate Set authorized implementation")
    require(gate["supersedes"] == {
        "gateSetVersion": "poc-recovery-stage0-v0.3",
        "disposition": "SUPERSEDED_AUDIT_ARTIFACT_NON_EXECUTABLE",
        "reviewedCommit": REVIEWED_V03_HEAD,
    }, "v0.3 supersession record drift")
    require(gate["findingsLedgers"][-1].endswith("review-findings-v0.3.json"), "Final advisory ledger missing")
    require(all(value is False for value in gate["scope"].values() if isinstance(value, bool)), "Governance-only scope widened")
    authorization = gate["executionAuthorization"]
    require(
        authorization["status"] == "WITHHELD_PENDING_SEPARATE_OWNER_AUTHORIZATION"
        and authorization["authorizedBy"] is None
        and authorization["authorizedOn"] is None
        and authorization["authorizationRecord"] is None
        and authorization["implicitFlipForbidden"] is True,
        "Execution authorization boundary drift",
    )
    approvals = gate["approvalState"]
    require(
        approvals["prospectivePolicyApproved"] is True
        and approvals["governanceAuthenticityAndLicenseEvidenceVerified"] is True
        and approvals["actualFutureGraphProductIpDisposition"]
        == "OPEN_BLOCKED_UNTIL_AUTHORIZED_IMPLEMENTATION_GRAPH_EXISTS",
        "Three-state Product/IP model drift",
    )
    for key in (
        "productIpFinalApproval",
        "currentCodexReviewClaimedFormallyIndependent",
    ):
        require(approvals[key] is False, f"Approval boundary widened: {key}")
    for key in (
        "approvedReviewer",
        "approvedOn",
        "accountableIndependentEngineeringSecurityReviewer",
        "productionLegalReviewer",
        "productionSecurityReviewer",
    ):
        require(approvals[key] is None, f"Unassigned reviewer field populated: {key}")

    key_gate = gate["keyConfirmationGate"]
    require(key_gate == {
        "fileFamilyCount": 9,
        "requiredFinalRelativeName": "key-confirmation/run.kc",
        "requiredTemporaryRelativeName": "key-confirmation/run.kc.tmp",
        "runBootstrapStepCount": 13,
        "publicationBeforeDurableBootstrapCommitAllowed": False,
        "existingTemporaryOrFinalMayBeOverwritten": False,
        "mandatoryFaultRowsAdded": 12,
    }, "Key-confirmation Gate Set summary drift")
    boundary = gate["dependencyBoundary"]
    require(boundary["policyId"] == JSR_POLICY_ID, "Recovery exclusion policy ID drift")
    require(boundary["rootCoordinate"] == TINK_COORDINATE, "Recovery Tink coordinate drift")
    require(boundary["forbiddenResolvedCoordinate"] == JSR_COORDINATE, "Recovery JSR305 coordinate drift")
    require(boundary["currentRecoveryGraphClaimed"] is False, "A current recovery graph was claimed")
    require(boundary["currentRepositoryWideAbsenceClaimed"] is False, "Repository-wide absence was claimed")
    require(boundary["futureRecoveryResolvedComponentCountRequired"] == 0, "Future JSR305 zero-count rule weakened")
    require(boundary["futurePackagedJsr305ClassDefinitionCountRequired"] == 0, "Future package-zero rule weakened")
    require(boundary["requiredR8Rules"] == R8_RULES and boundary["broaderDontwarnAllowed"] is False, "Exact R8 rule drift")
    require(boundary["releaseR8UnresolvedMissingClassesAllowed"] is False, "Unresolved release R8 missing classes allowed")

    expected_faults = INHERITED_FAULT_IDS | ADDED_FAULT_IDS
    require(gate["mandatoryFaultRowCount"] == 45, "Gate Set fault row total must be 45")
    require(set(gate["mandatoryFaultIds"]) == expected_faults and len(gate["mandatoryFaultIds"]) == 45, "Gate Set fault IDs drift")
    require(gate["faultRepetitions"] == {
        "perRow": {"PINNED_API36_X86_64_EMULATOR": 3, "PHYSICAL_D2": 1},
        "rows": 45,
        "emulatorInjections": 135,
        "physicalD2Injections": 45,
        "totalInjections": 180,
        "separateFromHardKillDenominator": True,
    }, "Fault repetition totals drift")
    ready11 = gate["readinessStates"]["REC-RDY-11"]
    require(
        ready11["prospectivePolicy"] == "CLOSED_APPROVED"
        and ready11["governanceAuthenticityLicenseEvidence"] == "CLOSED_VERIFIED_FOR_EXACT_PACKET"
        and ready11["actualFutureGraphVerificationAndProductIpDisposition"] == "OPEN_BLOCKED"
        and ready11["jsr305UseOrDistributionApproved"] is False,
        "REC-RDY-11 three-state contract drift",
    )


def validate_protocol(protocol: dict[str, Any]) -> None:
    require(protocol["schemaVersion"] == 4 and protocol["protocolId"] == PROTOCOL_ID, "Protocol v0.4 identity drift")
    require(protocol["executionAllowed"] is False and protocol["implementationAllowed"] is False, "Protocol authorized implementation/execution")
    primitives = protocol["binaryEncodingPrimitives"]
    require(primitives["unsignedIntegers"] == "network byte order / big-endian", "Binary endianness drift")
    require(primitives["LP16"].startswith("u16be byteLength"), "LP16 definition drift")
    require(primitives["trailingBytesAllowed"] is False, "Protocol allows trailing bytes")

    confirmation = protocol["keyConfirmation"]
    require(confirmation["required"] is True and confirmation["fileFamilyOrdinal"] == 9, "Ninth key-confirmation family missing")
    require(
        confirmation["finalRelativeName"] == "key-confirmation/run.kc"
        and confirmation["temporaryRelativeName"] == "key-confirmation/run.kc.tmp",
        "Key-confirmation name mapping drift",
    )
    alias = confirmation["canonicalAlias"]
    require(
        alias["format"] == "android-keystore://dora.poc.recovery.v1.<lowercase-run-uuid>"
        and alias["asciiOnly"] is True
        and alias["minimumUtf8Bytes"] == alias["maximumUtf8Bytes"] == 76
        and alias["digest"] == "SHA-256 over the exact canonical alias UTF-8 bytes",
        "Canonical alias/digest contract drift",
    )
    plaintext = confirmation["plaintextSchema"]
    aad = confirmation["associatedDataSchema"]
    require(
        plaintext["name"] == "DORA_RECOVERY_KEY_CONFIRMATION_PLAINTEXT_V1_BINARY_BE"
        and plaintext["magicAscii"] == "DORAKC01"
        and plaintext["schemaVersionU16be"] == 1,
        "Key-confirmation plaintext schema drift",
    )
    require(
        aad["name"] == "DORA_RECOVERY_KEY_CONFIRMATION_AAD_V1_BINARY_BE"
        and aad["magicAscii"] == "DORAKA01"
        and aad["schemaVersionU16be"] == 1,
        "Key-confirmation AAD schema drift",
    )
    expected_encoding = "magic[8] || u16be(1) || LP16(protocolId) || LP16(candidateId) || runId[16] || canonicalAliasSha256[32]"
    for schema in (plaintext, aad):
        require(
            schema["exactEncoding"] == expected_encoding
            and schema["protocolIdUtf8MaximumBytes"] == 96
            and schema["candidateIdUtf8MaximumBytes"] == 64
            and schema["encodedBytesMaximum"] == 222
            and schema["trailingBytesAllowed"] is False,
            "Key-confirmation bounded encoding drift",
        )
    require(plaintext["magicAscii"] != aad["magicAscii"], "Plaintext and AAD magic must be separate")
    identifiers = confirmation["identifierRules"]
    require(
        identifiers["candidateId"].endswith("REC-MICROFILE-TINK")
        and identifiers["crossRunOrCrossCandidateReuseAllowed"] is False,
        "Key-confirmation run/candidate binding drift",
    )
    sequence = confirmation["runBootstrapSequence"]
    require(len(sequence) == 13, "Key-confirmation bootstrap must contain exactly 13 steps")
    require([item.split(" ", 1)[0] for item in sequence] == [f"KC-{index:02d}" for index in range(1, 14)], "Key-confirmation bootstrap step IDs drift")
    for fragment in (
        "generateNewAeadKey(alias)",
        "new AndroidKeystoreKmsClient.Builder().setKeyUri(alias).build().getAead(alias)",
        "O_CREAT|O_EXCL|O_WRONLY|O_CLOEXEC",
        "fsync the temp file descriptor",
        "rename key-confirmation/run.kc.tmp to key-confirmation/run.kc without overwrite",
        "fsync the key-confirmation parent directory",
        "canonical alias SHA-256 and confirmation state",
        "successful endTransaction() return",
        "encrypted keyset, ciphertext, checkpoint or manifest publication begin",
    ):
        require(any(fragment in item for item in sequence), f"Bootstrap contract missing: {fragment}")
    order = confirmation["newRunOrder"]
    require(all(order.values()), "Key confirmation is not ordered before every publication family")
    require(confirmation["existingTemporaryOrFinalMayBeOverwritten"] is False, "Key confirmation overwrite enabled")
    require(confirmation["recoveryMayGenerateOrReplaceAlias"] is False, "Recovery alias replacement enabled")

    storage = protocol["storageAmendment"]
    require(storage["fileFamilyCount"] == 9, "Storage family count drift")
    require(storage["addedFinalNamePattern"] == {"keyConfirmation": "key-confirmation/run.kc"}, "Final key-confirmation pattern drift")
    require(storage["addedTempNamePattern"] == {"keyConfirmation": "key-confirmation/run.kc.tmp"}, "Temp key-confirmation pattern drift")
    require(storage["temporaryNameMapping"] == "finalRelativeName + '.tmp'", "Temp mapping drift")
    require(len(storage["allowlistedFinalFamilies"]) == 9 and storage["allowlistedFinalFamilies"][-1] == "keyConfirmation", "Nine-family allowlist drift")
    require(len(storage["pathValidationRequired"]) == 5, "Key-confirmation path validation coverage drift")
    require("KC-01 through KC-12" in storage["publicationSequences"]["runBootstrapPrefix"], "Publication sequence lacks durable bootstrap prefix")

    sqlite = protocol["sqliteAmendment"]
    require(sqlite["requiredRunRowFields"] == [
        "runId", "candidateId", "keyConfirmationRelativeName", "keyConfirmationBytes",
        "keyConfirmationSha256", "canonicalAliasSha256", "keyConfirmationState",
    ], "SQLite key-confirmation row identity drift")
    require(sqlite["abstractFileIdentityAllowed"] is False, "Abstract SQLite file identity enabled")
    require(sqlite["semanticCommitApi"] == "endTransaction() successful return" and sqlite["controllerEventPartOfSemanticCommit"] is False, "Bootstrap semantic commit drift")

    expected_taxonomy = [
        "KEY_REF_COLLISION", "INCOMPLETE_KEY_BOOTSTRAP", "KEY_CONFIRMATION_MISSING",
        "CORRUPT_KEY_CONFIRMATION", "KEY_UNAVAILABLE_KEY_MISMATCH", "KEY_UNAVAILABLE",
        "CORRUPT_KEY_ENVELOPE", "KEY_ENVELOPE_AUTH_FAILURE",
    ]
    taxonomy = protocol["recoveryTaxonomyV04"]
    require(
        [item["priority"] for item in taxonomy["classificationPrecedence"]] == list(range(1, 9))
        and [item["classification"] for item in taxonomy["classificationPrecedence"]] == expected_taxonomy
        and taxonomy["ambiguousExpectedOutcomeAllowed"] is False,
        "v0.4 key taxonomy precedence drift",
    )
    reconciliation = protocol["reconciliationAmendment"]
    require(set(reconciliation) == {
        "ALIAS_NO_CONFIRMATION_NO_DURABLE_ROW",
        "KEY_CONFIRMATION_TEMP_ONLY",
        "KEY_CONFIRMATION_FINAL_NO_ROW",
        "KEY_CONFIRMATION_ROW_MISSING_FINAL",
        "KEY_CONFIRMATION_ROW_LENGTH_HASH_OR_PARSER_MISMATCH",
        "KEY_CONFIRMATION_AUTHENTICATION_MISMATCH",
        "VALID_CONFIRMATION_LATER_ENVELOPE_AUTH_FAILURE",
        "TEMP_AND_FINAL_COLLISION",
    }, "Key-confirmation reconciliation states drift")
    require("never replace" in reconciliation["ALIAS_NO_CONFIRMATION_NO_DURABLE_ROW"], "Incomplete bootstrap replacement prohibition missing")
    require("authenticated-orphan" in reconciliation["KEY_CONFIRMATION_FINAL_NO_ROW"], "Authenticated-orphan handling missing")

    barriers = protocol["hardKillBarrierAmendment"]
    require(barriers["barrierIds"] == [f"K{index:02d}" for index in range(1, 13)], "K01-K12 coverage drift")
    require("KC-12" in barriers["requiredBeforeEveryBarrierCanArm"], "K01-K12 lack durable bootstrap prerequisite")
    require(barriers["controllerBootstrapEventRequiredForSemanticCommit"] is False, "Controller event folded into bootstrap commit")

    faults = protocol["faultCampaign"]
    require((faults["inheritedV03MandatoryRows"], faults["addedV04MandatoryRows"], faults["mandatoryFaultRowCount"]) == (33, 12, 45), "Protocol fault totals drift")
    require(faults["repetitionProfile"] == {"PINNED_API36_X86_64_EMULATOR": 3, "PHYSICAL_D2": 1}, "Protocol fault repetitions drift")
    require(faults["totalInjections"] == 180 and faults["separateFromHardKillDenominator"] is True, "Protocol fault injection total drift")
    cases = faults["addedCases"]
    require({item["id"] for item in cases} == ADDED_FAULT_IDS and len(cases) == 12, "Added key-confirmation fault IDs drift")
    require(all(item["repetitions"] == faults["repetitionProfile"] for item in cases), "Added fault repetition drift")
    expected_prefixes = {
        "KCB-01": "INCOMPLETE_KEY_BOOTSTRAP",
        "KCB-02": "KEY_CONFIRMATION_TEMP_ONLY",
        "KCB-03": "KEY_CONFIRMATION_TEMP_ONLY",
        "KCB-04": "fail-closed final-or-absent reconciliation",
        "KCB-05": "KEY_CONFIRMATION_FINAL_NO_ROW",
        "KCB-06": "durable bootstrap remains committed",
        "KCF-01": "KEY_CONFIRMATION_MISSING",
        "KCF-02": "CORRUPT_KEY_CONFIRMATION",
        "KCF-03": "CORRUPT_KEY_CONFIRMATION",
        "KCF-04": "KEY_UNAVAILABLE_KEY_MISMATCH",
        "KCF-05": "KEY_UNAVAILABLE_KEY_MISMATCH",
        "KCF-06": "KEY_REF_COLLISION",
    }
    require(
        all(item["expected"].startswith(expected_prefixes[item["id"]]) for item in cases),
        "Added fault expected classification/state drift",
    )
    require(any("alias creation" in item["injection"] and "temp creation" in item["injection"] for item in cases), "Alias-before-temp kill missing")
    require(any("controller event" in item["injection"] for item in cases), "Commit-before-controller-event kill missing")
    require(any("bit-flip" in item["injection"] for item in cases), "Bit-flip fault missing")
    require(any("truncate" in item["injection"] for item in cases), "Truncation fault missing")
    require(any("another run or candidate" in item["injection"] for item in cases), "Cross-run swap fault missing")
    require(any("replace the run alias" in item["injection"] for item in cases), "Alias-replacement fault missing")
    require(any("precreate key-confirmation temp, final or both" in item["injection"] for item in cases), "Temp/final collision fault missing")

    cleanup = protocol["cleanupAndQuarantine"]
    require(cleanup["quarantineOrder"] == [
        "SQLite intent commit", "rename without overwrite", "fsync source directory",
        "fsync destination directory", "SQLite completion commit",
    ], "Quarantine transaction drift")
    require(cleanup["silentDeletionAllowed"] is False and cleanup["aliasDeletionDuringReconciliationAllowed"] is False and cleanup["replacementAliasDuringRecoveryAllowed"] is False, "Cleanup/key safety boundary weakened")

    dependency = protocol["dependencyBoundary"]
    require(dependency["policyId"] == JSR_POLICY_ID, "Protocol dependency policy drift")
    require(dependency["prospectivePolicyState"] == "CLOSED_APPROVED", "Prospective policy is not closed")
    require(dependency["governanceAuthenticityLicenseEvidenceState"] == "CLOSED_VERIFIED_FOR_EXACT_PACKET", "Packet evidence is not closed")
    require(dependency["actualFutureRecoveryGraphState"].startswith("OPEN_BLOCKED"), "Future actual graph is not blocked")
    require(dependency["currentTinkAndroid123Wired"] is False and dependency["currentRecoveryModuleExists"] is False, "Current recovery graph/module overclaim")
    require(dependency["repositoryWideAbsenceClaimed"] is False, "Protocol claims repository-wide absence")
    require(dependency["coveredFutureModule"] == ":poc:recovery" and len(dependency["coveredFutureInputs"]) == 8, "Exact future recovery coverage drift")
    require(dependency["requiredResolvedJsr305ComponentCount"] == 0 and dependency["requiredPackagedJsr305ClassDefinitionCount"] == 0, "Recovery zero-JSR305 rule weakened")
    require(dependency["requiredR8Rules"] == R8_RULES and dependency["broaderDontwarnAllowed"] is False and dependency["releaseR8UnresolvedMissingClassesAllowed"] is False, "Recovery R8 boundary drift")
    require(dependency["jsr305UseOrDistributionApproved"] is False, "Excluded JSR305 use/distribution was approved")


def validate_license_notice() -> None:
    authenticity = read_json("docs/evidence/poc-recovery-001/dependency-ip-authenticity-v0.3.json")
    license_notice = read_json("docs/evidence/poc-recovery-001/license-notice-inventory.json")
    inventory = read_json("docs/evidence/poc-recovery-001/dependency-inventory.json")
    require(authenticity["schemaVersion"] == 3, "Active authenticity evidence schema drift")
    require(
        authenticity["overallStatus"]
        == "EXACT_GOVERNANCE_PACKET_AUTHENTICITY_LICENSE_NOTICE_VERIFIED_FUTURE_ACTUAL_GRAPH_PRODUCT_IP_BLOCKED",
        "Authenticity three-state status drift",
    )
    jetbrains = next(item for item in authenticity["components"] if item["coordinate"] == "org.jetbrains:annotations:13.0")
    require(jetbrains["upstreamLicenseTextLocator"] == JETBRAINS_LICENSE_URL, "JetBrains immutable LICENSE locator drift")
    require(jetbrains["licenseTextSha256"] == JETBRAINS_LICENSE_SHA256, "JetBrains LICENSE SHA-256 drift")
    require(jetbrains["notice"] == {
        "requirement": "PRESERVE_IMMUTABLE_UPSTREAM_NOTICE_IN_FUTURE_STAGE0_NOTICES_PACKET_IF_ARTIFACT_ENTERS_A_SEPARATELY_APPROVED_RESOLVED_GRAPH",
        "locator": JETBRAINS_NOTICE_URL,
        "sha256": JETBRAINS_NOTICE_SHA256,
        "result": "IMMUTABLE_NOTICE_VERIFIED_AND_PRESERVATION_REQUIRED",
    }, "JetBrains immutable NOTICE evidence drift")
    verification = jetbrains["licenseAndNoticeVerification"]
    require(
        verification["verifiedAtUtc"] == "2026-08-12T14:38:33Z"
        and verification["verificationTool"] == "gh api GitHub Contents API + System.Security.Cryptography.SHA256"
        and verification["licenseBytes"] == 11358
        and verification["noticeBytes"] == 127
        and verification["governancePacketEvidenceAccepted"] is True
        and verification["futureActualGraphApproved"] is False
        and verification["dependencyAdmission"] is False
        and verification["redistributionApproved"] is False
        and verification["productionLegalApproved"] is False,
        "JetBrains verification timestamp/tool/scope drift",
    )
    boundary = authenticity["approvalBoundary"]
    require(
        boundary["prospectivePolicyStatus"] == "CLOSED_APPROVED"
        and boundary["governancePacketEvidenceStatus"] == "CLOSED_VERIFIED"
        and boundary["futureActualGraphProductIpDisposition"] == "OPEN_BLOCKED"
        and boundary["jsr305UseOrDistributionApproved"] is False
        and boundary["dependencyAdmission"] is False
        and boundary["productionAdmission"] is False,
        "Authenticity approval boundary drift",
    )
    require(license_notice["schemaVersion"] == 4, "License/NOTICE inventory schema drift")
    require(license_notice["summary"]["jetbrainsAnnotationsImmutableLicenseNoticeVerified"] is True, "License inventory lacks immutable JetBrains closure")
    license_component = next(item for item in license_notice["components"] if item["coordinate"] == "org.jetbrains:annotations:13.0")
    require(
        license_component["immutableLicenseLocator"] == JETBRAINS_LICENSE_URL
        and license_component["immutableLicenseSha256"] == JETBRAINS_LICENSE_SHA256
        and license_component["immutableNoticeLocator"] == JETBRAINS_NOTICE_URL
        and license_component["immutableNoticeSha256"] == JETBRAINS_NOTICE_SHA256
        and license_component["governancePacketEvidenceAccepted"] is True
        and license_component["futureActualGraphApproved"] is False
        and "PRESERVE" in license_component["noticePreservationRequirement"],
        "License inventory JetBrains evidence/preservation drift",
    )
    require(inventory["dependencyAdmission"] is False and inventory["runtimeGraphModified"] is False, "Dependency inventory admitted a runtime graph")
    require(inventory["recoveryBoundary"]["repositoryWideAbsenceClaimed"] is False, "Dependency inventory claims repository-wide absence")

    forbidden = re.compile(r"EXACT_SOURCE[^\n\"]*PENDING|JetBrains/java-annotations/master|raw\.githubusercontent\.com/JetBrains/[^\s\"]*/master")
    active_files = [
        EVIDENCE / "dependency-ip-authenticity-v0.3.json",
        EVIDENCE / "license-notice-inventory.json",
        EVIDENCE / "dependency-ip-authenticity-verification-2026-08-12.md",
        EVIDENCE / "jetbrains-annotations-license-notice-verification-2026-08-12.md",
    ]
    for path in active_files:
        require(forbidden.search(path.read_text(encoding="utf-8")) is None, f"Mutable or pending JetBrains evidence remains: {path.name}")


def current_lockfile_occurrences() -> set[tuple[str, str]]:
    result: set[tuple[str, str]] = set()
    pattern = re.compile(r"^(com\.google\.(?:code\.findbugs:jsr305|crypto\.tink:tink):[^=]+)=")
    for path in (ROOT / "android").rglob("gradle.lockfile"):
        for line in path.read_text(encoding="utf-8").splitlines():
            match = pattern.match(line)
            if match:
                result.add((path.relative_to(ROOT).as_posix(), match.group(1)))
    return result


def validate_recovery_boundary() -> None:
    base = read_json("docs/evidence/poc-recovery-001/base-lockfile-tooling-inventory-2026-08-12.json")
    require(base["schemaVersion"] == 1 and base["boundaryId"] == JSR_POLICY_ID, "Base lockfile inventory identity drift")
    require(base["baseCommit"] == BASE_HEAD and base["reviewedHead"] == REVIEWED_V03_HEAD and base["pullRequest"] == 11, "Base lockfile inventory commit/PR drift")
    facts = base["facts"]
    require(
        facts == {
            "tinkAndroid123Wired": False,
            "recoveryModuleExists": False,
            "recoveryModuleIncludedInSettings": False,
            "pullRequestChangedLockfiles": False,
            "repositoryWideTinkOrJsr305AbsenceClaimed": False,
            "baseOtherModuleOccurrencesAreRecoveryAdmissionEvidence": False,
        },
        "Base recovery boundary facts drift",
    )
    recorded = {(item["lockfile"], item["coordinate"]) for item in base["existingBaseLockfileOccurrences"]}
    require(recorded == current_lockfile_occurrences(), "Base Tink/JSR305 lockfile occurrence inventory drift")
    require(len(recorded) == 10, "Expected ten exact base lockfile coordinate occurrences")
    require(len(base["futureRecoveryCoveredInputs"]) == 8, "Future recovery configuration coverage drift")
    require(base["excludedCurrentInputs"] == [
        "buildscript, AGP, UTP, lint and tooling configurations of other existing modules",
        "existing app, capture and search lockfiles",
    ], "Excluded current tooling boundary drift")

    require(not (ROOT / "android" / "poc" / "recovery").exists(), "android/poc/recovery exists in governance-only package")
    settings = read_text("android/settings.gradle.kts")
    require(":poc:recovery" not in settings, "Recovery module is included in Gradle settings")
    gradle_inputs = [
        *(ROOT / "android").rglob("*.gradle"),
        *(ROOT / "android").rglob("*.gradle.kts"),
        ROOT / "android" / "gradle" / "libs.versions.toml",
    ]
    contaminated = [
        path.relative_to(ROOT).as_posix()
        for path in gradle_inputs
        if path.is_file() and re.search(r"tink-android\s*[:=]?\s*1\.23\.0|com\.google\.crypto\.tink:tink-android:1\.23\.0", path.read_text(encoding="utf-8"), re.IGNORECASE)
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
    require(diff.returncode == 0, f"Unable to compare base lockfiles: {diff.stderr.strip()}")
    changed_lockfiles = [line for line in diff.stdout.splitlines() if line.endswith("gradle.lockfile")]
    require(not changed_lockfiles, f"PR/worktree changed lockfiles: {changed_lockfiles}")

    analysis = read_json("docs/evidence/poc-recovery-001/jsr305-exclusion-analysis-2026-08-12.json")
    require(analysis["schemaVersion"] == 3, "JSR305 analysis schema drift")
    current = analysis["currentRepositoryBoundary"]
    require(current["boundaryId"] == JSR_POLICY_ID and current["repositoryWideAbsenceClaimed"] is False and current["baseOtherModuleToolingOccurrencesAreRecoveryAdmissionEvidence"] is False, "JSR305 current boundary overclaim")
    policy = analysis["prospectivePolicy"]
    require(policy["status"] == "APPROVED_PROSPECTIVE_POLICY_ONLY" and policy["coveredFutureModule"] == ":poc:recovery", "JSR305 prospective policy state drift")
    require(policy["requiredResolvedComponentCount"] == 0 and policy["requiredR8Rules"] == R8_RULES and policy["broaderJavaxAnnotationDontwarnAllowed"] is False, "JSR305 exclusion/R8 policy drift")
    states = analysis["licenseDisposition"]
    require(
        states["stateAProspectivePolicy"] == "CLOSED_APPROVED"
        and states["stateBGovernanceAuthenticityLicenseEvidence"].startswith("CLOSED_VERIFIED")
        and states["stateCFutureActualGraphProductIpDisposition"].startswith("OPEN_BLOCKED")
        and states["jsr305UseOrDistributionApproved"] is False,
        "JSR305 three-state license disposition drift",
    )


def validate_readiness_roles_and_traceability() -> None:
    readiness = read_json("docs/evidence/poc-recovery-001/readiness.json")
    roles = read_json("docs/evidence/poc-recovery-001/review-roles.json")
    ledger = read_json("docs/evidence/poc-recovery-001/review-findings-v0.3.json")
    index = read_json("docs/evidence/poc-recovery-001/evidence-index.json")

    require(readiness["schemaVersion"] == 5 and readiness["status"].startswith("BLOCKED_"), "Readiness v0.4 status drift")
    for key in (
        "executionAllowed", "implementationAllowedByThisPackage", "measuredExecutionAllowed",
        "runtimeDependencyAdded", "recoveryModuleExists", "harnessImplemented",
        "nonMetricImplementationVerificationPassed", "exactFutureResolvedGraphReviewed",
        "killCampaignExecuted", "deviceTestsExecuted", "benchmarksExecuted", "productionAppChanged",
    ):
        require(readiness[key] is False, f"Governance-only readiness invariant violated: {key}")
    package = readiness["packageArtifacts"]
    require(package["activeGateSetVersion"] == GATE_ID and package["activeProtocolId"] == PROTOCOL_ID, "Readiness active v0.4 locator drift")
    require(package["v03RetainedAsSupersededAuditArtifact"] is True and package["v03Executable"] is False, "Readiness v0.3 audit disposition drift")
    require(package["jetbrainsAnnotationsImmutableLicenseNoticeVerified"] is True and package["baseLockfileToolingInventoryPresent"] is True, "Readiness immutable license/base inventory evidence missing")
    approvals = readiness["approvals"]
    require(
        approvals["prospectivePolicyProductIpApproved"] is True
        and approvals["governanceAuthenticityLicenseEvidenceVerified"] is True
        and approvals["futureActualGraphProductIpDisposition"] == "OPEN_BLOCKED"
        and approvals["productIpFinalApproved"] is False
        and approvals["approvedReviewer"] is None
        and approvals["accountableEngineeringSecurityReviewer"] is None
        and approvals["currentCodexReviewClaimedFormallyIndependent"] is False,
        "Readiness approval state drift",
    )
    policy = readiness["dependencyExclusionPolicy"]
    require(policy["policyId"] == JSR_POLICY_ID and policy["coveredFutureModule"] == ":poc:recovery", "Readiness recovery boundary ID/module drift")
    require(policy["allCoveredRecoveryInputsRequired"] is True, "Readiness does not require all covered recovery inputs")
    require(policy["repositoryWideAbsenceClaimed"] is False and policy["excludedCurrentInputsAreRecoveryAdmissionEvidence"] is False, "Readiness repository-wide absence/admission overclaim")
    require(policy["requiredResolvedComponentCount"] == 0 and policy["requiredR8Rules"] == R8_RULES and policy["broaderDontwarnAllowed"] is False and policy["unresolvedR8MissingClassesAllowed"] is False, "Readiness zero-JSR305/R8 rule drift")
    ready11 = next(item for item in readiness["blockers"] if item["id"] == "REC-RDY-11-SUPPLY-CHAIN-AUTHENTICITY")
    require(ready11["status"] == "POLICY_CLOSED_PACKET_EVIDENCE_CLOSED_ACTUAL_FUTURE_GRAPH_OPEN_BLOCKED", "REC-RDY-11 state drift")
    require(all(fragment in ready11["condition"] for fragment in ("CLOSED/APPROVED", "CLOSED/VERIFIED", "OPEN/BLOCKED", "not approval to use")), "REC-RDY-11 three-state explanation incomplete")

    require(roles["schemaVersion"] == 5, "Review-role schema drift")
    role_map = roles["roles"]
    require(role_map["packageAuthor"]["claimedFormallyIndependentReviewer"] is False, "Codex claimed formal independence")
    product_ip = role_map["stage0ProductIp"]
    require(product_ip["status"] == "PROSPECTIVE_POLICY_AND_EXACT_GOVERNANCE_PACKET_EVIDENCE_APPROVED_ONLY" and product_ip["finalApproved"] is False, "Product/IP role state drift")
    require(product_ip["governancePacketEvidenceDisposition"]["status"] == "CLOSED_VERIFIED" and product_ip["futureExactGraphDisposition"]["status"] == "OPEN_BLOCKED", "Role three-state split drift")
    require(product_ip["approvedReviewer"] is None and product_ip["approvedOn"] is None, "Future graph Product/IP approval populated")
    independent = role_map["independentRecoveryEngineeringSecurity"]
    require(independent["reviewer"] is None and independent["status"] == "UNASSIGNED_BLOCKING" and independent["currentCodexRemediationClaimedFormallyIndependent"] is False, "Accountable reviewer boundary drift")
    require(role_map["executionAuthorizer"]["status"] == "AUTHORIZATION_WITHHELD", "Execution authorizer state drift")
    require(role_map["productionLegal"]["reviewer"] is None and role_map["productionSecurity"]["reviewer"] is None, "Production reviewer unexpectedly assigned")

    require(ledger["schemaVersion"] == 3 and ledger["reviewedCommit"] == REVIEWED_V03_HEAD, "Final advisory ledger identity drift")
    require(ledger["reviewedGateSetVersion"] == "poc-recovery-stage0-v0.3" and ledger["remediationVersion"] == PROTOCOL_ID, "Final advisory ledger version drift")
    require(ledger["sanitized"] is True and ledger["reviewerIdentity"] is None and ledger["formalReviewer"] is False, "Final advisory ledger claimed formal reviewer")
    findings = ledger["findings"]
    require([item["id"] for item in findings] == [f"REC-GOV-V03-{index:03d}" for index in range(1, 5)], "Final advisory stable finding IDs drift")
    require([item["severity"] for item in findings] == ["P0", "P0", "P1", "P1"], "Final advisory severity mapping drift")
    require(all(item["remediationVersion"] == PROTOCOL_ID and item["remediationCommit"] == "SELF" and item["formalReviewer"] is False and item["disposition"].startswith("CLOSED") for item in findings), "Final advisory remediation traceability incomplete")
    f2 = findings[1]
    require("F_06" in f2["disposition"] and "IMMUTABLE" in f2["disposition"], "F-06 immutable closure linkage missing")

    require(index["schemaVersion"] == 2 and index["activeGateSetVersion"] == GATE_ID and index["activeProtocolId"] == PROTOCOL_ID and index["executionAllowed"] is False, "Evidence index active v0.4 state drift")
    indexed_hashes = {item["locator"]: item["sha256"] for item in index["supersededAuditArtifacts"]}
    require(indexed_hashes == IMMUTABLE_AUDIT_HASHES, "Evidence index immutable v0.1-v0.3 hashes drift")
    artifact_ids = {item["id"] for item in index["artifacts"]}
    require({
        "REC-V04-GATE-MARKDOWN", "REC-V04-GATE-JSON", "REC-V04-PROTOCOL-JSON",
        "REC-V04-REMEDIATION", "REC-REVIEW-V03-LEDGER",
        "REC-JETBRAINS-ANNOTATIONS-LICENSE-NOTICE-VERIFICATION-20260812",
        "REC-BASE-LOCKFILE-TOOLING-INVENTORY-20260812",
    }.issubset(artifact_ids), "Evidence index lacks v0.4 artifacts")


def validate_documents_and_no_implementation() -> None:
    required_fragments = {
        "docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_4.md": [
            "key-confirmation/run.kc", "DORAKC01", "DORAKA01", "KC-13", "45 rows",
            "REC-GOV-V03-001", "REC-GOV-V03-004", "implementationAllowed=false",
            "future actual graph", "formal accountable reviewer",
        ],
        "docs/stage0/DORA_MVP1_POC_RECOVERY_OWNER_DECISION_OD14.md": [
            "protocol v0.4", "future `:poc:recovery`", "NOTICE", "open and blocking",
            "executionAllowed=false",
        ],
        "docs/stage0/DEC-044-POC-RECOVERY-EXPERIMENT.md": [
            "protocol v0.4", REVIEWED_V03_HEAD, "key-confirmation/run.kc", "45 rows",
            "implementationAllowed=false",
        ],
        "docs/stage0/DORA_MVP1_IP_ASSET_POLICY.md": [
            "protocol v0.4", "No repository-wide Tink/JSR-305 absence is claimed", JETBRAINS_COMMIT,
        ],
        "docs/stage0/DORA_MVP1_POC_EXECUTION_ORDER.md": [
            "stage0-v0.4", "45 fault rows", "actual graph are mandatory",
        ],
        "docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md": [
            "stage0-v0.4", "key-confirmation/run.kc", "Existing other-module",
        ],
        "docs/DORA_MVP1_STAGE_STATUS.md": [
            "poc-recovery-stage0-v0.4", "v0.1/v0.2/v0.3", "45-row fault contract",
        ],
        "docs/evidence/poc-recovery-001/README.md": [
            "PROTOCOL v0.4", "base-lockfile-tooling-inventory-2026-08-12.json", "no repository-wide",
        ],
        "docs/evidence/poc-recovery-001/governance-remediation-v0.4.md": [
            "REC-GOV-V03-001", "REC-GOV-V03-004", "implementationAllowed=false", "executionAllowed=false",
        ],
    }
    for relative, fragments in required_fragments.items():
        text = read_text(relative)
        for fragment in fragments:
            require(fragment in text, f"{relative} is missing aligned v0.4 text: {fragment}")

    require(not (ROOT / "android" / "poc" / "recovery").exists(), "Recovery module exists")
    require(":poc:recovery" not in read_text("android/settings.gradle.kts"), "Recovery module included")
    app_manifest = read_text("android/app/src/main/AndroidManifest.xml")
    require("android.permission.RECORD_AUDIO" not in app_manifest, "Production :app microphone permission appeared")
    device_matrix = read_text("docs/stage0/device-matrix.yaml")
    require(f"protocol: {PROTOCOL_ID}" in device_matrix and "execution_allowed: false" in device_matrix, "Device matrix v0.4/execution boundary drift")


def main() -> int:
    gate = read_json("docs/stage0/poc-recovery-gate-set-stage0-v0.4.json")
    protocol = read_json("docs/stage0/poc-recovery-protocol-stage0-v0.4.json")
    validate_immutable_history(gate, protocol)
    validate_gate(gate)
    validate_protocol(protocol)
    validate_license_notice()
    validate_recovery_boundary()
    validate_readiness_roles_and_traceability()
    validate_documents_and_no_implementation()
    print(
        "POC-RECOVERY-001 governance v0.4 validation passed; v0.1-v0.3 immutable, "
        "durable key-confirmation contract closed prospectively, exact JetBrains LICENSE/NOTICE "
        "verified, REC-JSR305-EXCLUDE-001 bounded to future :poc:recovery, "
        "implementationAllowed=false, executionAllowed=false"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (
        OSError,
        ValueError,
        KeyError,
        StopIteration,
        json.JSONDecodeError,
    ) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
