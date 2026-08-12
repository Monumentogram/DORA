#!/usr/bin/env python3
"""Validate the governance-only POC-RECOVERY-001 v0.3 package without executing a PoC."""

from __future__ import annotations

import hashlib
import json
import re
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
STAGE0 = ROOT / "docs" / "stage0"
EVIDENCE = ROOT / "docs" / "evidence" / "poc-recovery-001"
REVIEWED_V01_HEAD = "87f8c00c6afce0f658678a7a09b1a394b89a2454"
REVIEWED_V02_HEAD = "70cf26125dbecbb347311ca0bb9ce1ad5c637e18"
GATE_V03 = "poc-recovery-stage0-v0.3"
PROTOCOL_V03 = "poc-recovery-protocol-stage0-v0.3"
IMMUTABLE_AUDIT_HASHES = {
    "DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_1.md": "d891e033e3e58455dbafd03be5a41ca64cafda93182424357035c37d769ae46e",
    "poc-recovery-gate-set-stage0-v0.1.json": "78c1a8289f90b51a376b023673dc00b6cb35386b5b0a2dda9432b50b20216e11",
    "poc-recovery-protocol-stage0-v0.1.json": "b853295e6c66815c61566e930d30dafa0dfc72e805bb5ba38158688e084ead81",
    "DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_2.md": "d4fab2f47872f0b6c1c04c5b0b1022b047ae8782eb0130cd2f66825294455180",
    "poc-recovery-gate-set-stage0-v0.2.json": "f6384c7b1d4d493218a600722ddf0116f454e8356e7e247da74f03256cc69110",
    "poc-recovery-protocol-stage0-v0.2.json": "cfa06e624cbc0da37b68188d7b1739cdfb5ca12beeedc21f408897dc41b2081f",
}


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def read_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"Missing required JSON: {path.relative_to(ROOT)}")
    return json.loads(path.read_text(encoding="utf-8"))


def read_text(path: Path) -> str:
    require(path.is_file(), f"Missing required document: {path.relative_to(ROOT)}")
    return path.read_text(encoding="utf-8")


def validate_supersession(gate: dict[str, Any], protocol: dict[str, Any]) -> None:
    for filename, expected_sha256 in IMMUTABLE_AUDIT_HASHES.items():
        path = STAGE0 / filename
        require(path.is_file(), f"Superseded audit artifact missing: {filename}")
        actual_sha256 = hashlib.sha256(path.read_bytes()).hexdigest()
        require(actual_sha256 == expected_sha256, f"Superseded audit artifact changed: {filename}")

    require(gate["supersedes"] == {
        "gateSetVersion": "poc-recovery-stage0-v0.2",
        "disposition": "SUPERSEDED_AUDIT_ARTIFACT_NON_EXECUTABLE",
        "reviewedCommit": REVIEWED_V02_HEAD,
    }, "Gate Set v0.2 supersession record drift")
    require(protocol["supersedes"] == {
        "protocolId": "poc-recovery-protocol-stage0-v0.2",
        "disposition": "SUPERSEDED_AUDIT_ARTIFACT_NON_EXECUTABLE",
        "reviewedCommit": REVIEWED_V02_HEAD,
    }, "Protocol v0.2 supersession record drift")

    expected_gate_history = [
        {
            "gateSetVersion": "poc-recovery-stage0-v0.1",
            "disposition": "SUPERSEDED_AUDIT_ARTIFACT_NON_EXECUTABLE",
            "reviewedCommit": REVIEWED_V01_HEAD,
        },
        gate["supersedes"],
    ]
    expected_protocol_history = [
        {
            "protocolId": "poc-recovery-protocol-stage0-v0.1",
            "disposition": "SUPERSEDED_AUDIT_ARTIFACT_NON_EXECUTABLE",
            "reviewedCommit": REVIEWED_V01_HEAD,
        },
        protocol["supersedes"],
    ]
    require(gate["retainedAuditArtifacts"] == expected_gate_history, "Gate Set audit history drift")
    require(protocol["retainedAuditArtifacts"] == expected_protocol_history, "Protocol audit history drift")


def validate_gate(gate: dict[str, Any]) -> None:
    require(gate["schemaVersion"] == 3, "Gate Set schema drift")
    require(gate["pocId"] == "POC-RECOVERY-001", "PoC ID drift")
    require(gate["gateSetVersion"] == GATE_V03, "Active Gate Set must be v0.3")
    require(gate["protocolLocator"] == "docs/stage0/poc-recovery-protocol-stage0-v0.3.json", "Protocol locator drift")
    require(gate["findingsLedgers"] == [
        "docs/evidence/poc-recovery-001/review-findings-v0.1.json",
        "docs/evidence/poc-recovery-001/review-findings-v0.2.json",
    ], "Findings ledger locator drift")
    require(
        gate["status"] == "PROPOSED_OWNER_REMEDIATED_IMPLEMENTATION_VERIFICATION_REQUIRED",
        "Gate Set remediation state drift",
    )
    require(gate["ownerProtocolSemanticsFrozen"] is True, "Owner protocol semantics must be frozen")
    require(gate["executionAllowed"] is False, "Recovery execution must remain disabled")
    authorization = gate["executionAuthorization"]
    require(authorization["status"] == "WITHHELD_PENDING_SEPARATE_OWNER_AUTHORIZATION", "Execution authorization drift")
    require(
        authorization["authorizedBy"] is None
        and authorization["authorizedOn"] is None
        and authorization["authorizationRecord"] is None,
        "No execution authority may be populated",
    )
    require(authorization["implicitFlipForbidden"] is True, "Implicit execution authorization must be forbidden")
    approvals = gate["approvalState"]
    require(approvals["productIpFinalApproval"] is False, "Product/IP approval was prematurely recorded")
    require(approvals["approvedReviewer"] is None and approvals["approvedOn"] is None, "Product/IP approval identity/date must remain null")
    require(approvals["accountableIndependentEngineeringSecurityReviewer"] is None, "Independent reviewer was prematurely assigned")
    require(approvals["currentCodexReviewClaimedFormallyIndependent"] is False, "Codex remediation cannot claim formal independence")
    require(approvals["productionLegalReviewer"] is None and approvals["productionSecurityReviewer"] is None, "Production reviewers must remain unassigned")

    for key in (
        "dependencyAdmission",
        "productionAdmission",
        "finalAdrAudio001",
        "implementationAllowed",
        "measurementAllowed",
        "deviceExecutionAllowed",
        "gradleRuntimeDependencyAllowed",
        "recoveryModuleAllowed",
        "productionAppChangeAllowed",
    ):
        require(gate["scope"][key] is False, f"Forbidden recovery scope enabled: {key}")

    thresholds = gate["thresholds"]
    expected_thresholds = {
        "committedLossBytesMaximum": 0,
        "tailLossSecondsMaximum": 5.0,
        "tailLossBytesMaximum": 160000,
        "returnedBytesAuthenticatedPercentMinimum": 100.0,
        "baseHardKillAttemptsPerCandidate": 120,
        "validHardKillsPerCandidateMinimum": 100,
        "strataCount": 12,
        "baseAttemptsPerStratumPerCandidate": 10,
        "validHardKillsPerStratumMinimum": 8,
        "replacementAttemptsPerCandidateMaximum": 20,
        "duplicateProcessingIntentMaximum": 0,
        "missingProcessingIntentMaximum": 0,
        "automaticMicrophoneStartsMaximum": 0,
    }
    for key, expected in expected_thresholds.items():
        require(thresholds[key] == expected, f"Threshold drift: {key}")
    require("authenticatedRecoveredBytesPercentMinimum" not in thresholds, "Ambiguous legacy authentication threshold returned")
    require(thresholds["fixture"]["maxPlaintextBytesPerRun"] == 115200000, "Maximum run size drift")

    candidates = {candidate["id"]: candidate for candidate in gate["candidates"]}
    require(set(candidates) == {"REC-STREAM-TINK", "REC-MICROFILE-TINK"}, "Candidate set drift")
    stream = candidates["REC-STREAM-TINK"]
    require(stream["construction"] == "AES_GCM_HKDF_STREAMING_PARAMETERS", "Streaming construction drift")
    require((stream["inputKeyBytes"], stream["derivedAesGcmKeyBytes"], stream["hkdfHash"], stream["ciphertextSegmentBytes"]) == (16, 16, "SHA256", 4096), "Streaming parameter drift")
    require(stream["checkpointModel"] == "DURABLE_ONE_SEGMENT_LOOKAHEAD", "Streaming checkpoint model drift")
    require(stream["passEligibleCadencesSeconds"] == [], "Streaming cadence must not exist")
    require((stream["maximumBoundedTailBytes"], stream["maximumBoundedTailSeconds"]) == (8160, 0.255), "Streaming bounded-tail proof drift")
    require((stream["maxPlaintextBytesPerRun"], stream["maxSegmentsPerRun"]) == (115200000, 28236), "Streaming size/segment bound drift")
    require(stream["deprecatedStreamingAeadKeyTemplatesAllowed"] is False and stream["registryConfigurationRequired"] is True, "Streaming public API boundary drift")

    micro = candidates["REC-MICROFILE-TINK"]
    require(micro["template"] == "AES256_GCM_TINK_IV12_TAG16", "Microfile template drift")
    require((micro["aesKeyBytes"], micro["ivBytes"], micro["tagBytes"], micro["variant"]) == (32, 12, 16, "TINK"), "Microfile parameters drift")
    require(micro["freshKeysetPerMicrofile"] is True, "Each microfile requires a fresh keyset")
    require(micro["passEligibleCadencesSeconds"] == [5] and micro["observationOnlyCadencesSeconds"] == [15, 30], "Microfile cadence contract drift")
    require((micro["fullUnitPlaintextBytes"], micro["fullUnitCiphertextBytes"]) == (160000, 160033), "Five-second unit sizing drift")

    require(gate["phaseA"]["passAllowed"] is False, "Phase A PASS must remain forbidden")
    require(gate["phaseA"]["allowedVerdicts"] == ["FAIL", "INCONCLUSIVE"], "Phase A verdict drift")
    require(gate["phaseA"]["plannedBaseAttemptsPerCandidate"] == {"PINNED_API36_X86_64_EMULATOR": 72, "PHYSICAL_D2": 48}, "Phase A allocation drift")
    require(gate["fullPhysicalVerdict"]["requiredPhysicalProfiles"] == ["D1", "D2", "D5"], "Full physical profile contract drift")
    require(gate["fullPhysicalVerdict"]["phaseAAttemptsReusable"] is False, "Phase A cannot substitute for the physical campaign")
    require(gate["fullPhysicalVerdict"]["procurementRequiredNow"] is False, "D1/D5 procurement was prematurely required")
    require(gate["mandatoryFaultRowCount"] == 33, "Mandatory fault row count must be 33")
    require(len(gate["mandatoryFaultIds"]) == 33 and len(set(gate["mandatoryFaultIds"])) == 33, "Mandatory fault set must contain 33 unique cases")
    require([fault for fault in gate["mandatoryFaultIds"] if fault.startswith("KEY-")] == [f"KEY-{index:02d}" for index in range(1, 8)], "Gate Set key-fault range must be KEY-01 through KEY-07")
    require("REC-RDY-11-SUPPLY-CHAIN-AUTHENTICITY" in gate["blockers"], "Supply-chain authenticity blocker missing")


def validate_protocol(protocol: dict[str, Any]) -> None:
    require(protocol["schemaVersion"] == 3 and protocol["protocolId"] == PROTOCOL_V03, "Protocol v0.3 identity drift")
    require(protocol["pocId"] == "POC-RECOVERY-001", "Protocol PoC ID drift")
    require(protocol["executionAllowed"] is False, "Protocol execution must remain disabled")
    require(protocol["dependencyCandidate"]["runtimeGraphAdmission"] is False, "Tink was prematurely admitted")
    definitions = protocol["definitions"]
    require(definitions["semanticCommitPoint"].startswith("successful return of SQLite endTransaction()"), "Semantic commit point drift")
    require("not part of commit definition" in definitions["controllerCommitEvent"], "Controller event was folded into semantic commit")
    require(definitions["mandatoryOrderingInvariant"] == "0 <= C <= R <= A", "C/R/A invariant drift")
    require(definitions["committedLossBytesRequired"] == 0 and definitions["returnedBytesAuthenticatedPercentMinimum"] == 100.0, "Authentication/loss invariant drift")
    require("authenticated manifest/checkpoint" in definitions["committedPrefix"], "C must derive from SQLite and authenticated publication")

    candidates = {candidate["id"]: candidate for candidate in protocol["candidates"]}
    stream = candidates["REC-STREAM-TINK"]
    construction = stream["construction"]
    require(construction["parametersClass"].endswith("AesGcmHkdfStreamingParameters"), "Streaming parameters class drift")
    require((construction["inputKeyBytes"], construction["derivedAesGcmKeyBytes"], construction["ciphertextSegmentBytes"]) == (16, 16, 4096), "Streaming construction size drift")
    require(construction["hkdfHash"].endswith(".SHA256"), "Streaming HKDF drift")
    require("generateEntryFromParameters" in construction["keysetConstruction"] and "withRandomId()" in construction["keysetConstruction"] and "makePrimary()" in construction["keysetConstruction"], "Streaming public keyset construction drift")
    require(construction["primitiveConstruction"] == "keysetHandle.getPrimitive(RegistryConfiguration.get(), StreamingAead.class)", "Streaming primitive construction drift")
    key_model = stream["keyModel"]
    require(key_model["freshKeysetPerRun"] is True and key_model["separateDerivedKeyPerSegmentClaimed"] is False, "Streaming key model drift")
    require(key_model["segmentUniqueness"] == ["nonce prefix", "segment index", "last flag"], "Streaming nonce/segment model drift")
    require(key_model["associatedDataSetOnceForWholeStream"] is True, "Streaming AAD must be set once")
    checkpoint_model = stream["checkpointModel"]
    require(checkpoint_model["qDefinition"].startswith("count of fully output non-final 4096-byte"), "q definition drift")
    require(checkpoint_model["ciphertextPrefixBytesFormula"] == "q * 4096", "Ciphertext prefix formula drift")
    require(checkpoint_model["recoveredEndFormula"] == {"qLessThan2": 0, "qAtLeast2": "4056 + (q - 2) * 4080"}, "Recovered-end formula drift")
    require(checkpoint_model["durableLastNonFinalSegmentIsSacrificial"] is True and checkpoint_model["sacrificialSegmentIncludedInCommittedPrefix"] is False, "Sacrificial segment rule drift")
    require(checkpoint_model["passEligibleCadencesSeconds"] == [] and (checkpoint_model["maximumBoundedTailBytes"], checkpoint_model["maximumBoundedTailSeconds"]) == (8160, 0.255), "Streaming tail contract drift")
    reads = stream["recoveryReads"]
    require((reads["firstRequestedPlaintextBytes"], reads["subsequentRequestedPlaintextBytes"]) == (4056, 4080), "Recovery read sizes drift")
    require(reads["countOnlySuccessfullyCompletedReadReturnBytes"] is True and reads["discardEntireCallerBufferOnReadException"] is True, "Recovery read accounting drift")
    require(reads["minusOneMeaning"] == "AUTHENTICATED_NORMAL_EOF", "-1 EOF classification drift")
    require("ANY_OTHER_EXCEPTION" in reads["exceptionsNeverMeaningEof"], "Exception-as-EOF prohibition drift")
    require(stream["checkpoint"]["exactEncoding"] == "DORA_RECOVERY_STREAM_CHECKPOINT_V1_BINARY_BE" and stream["checkpoint"]["magicAscii"] == "DORARC01", "Checkpoint encoding drift")
    require(stream["checkpoint"]["aeadTemplate"] == "AES256_GCM_TINK_IV12_TAG16" and stream["checkpoint"]["freshKeysetPerGeneration"] is True, "Checkpoint publication AEAD drift")

    micro = candidates["REC-MICROFILE-TINK"]
    aead = micro["aeadTemplate"]
    require(aead["name"] == "AES256_GCM_TINK_IV12_TAG16", "Microfile template drift")
    require((aead["aesKeyBytes"], aead["ivBytes"], aead["tagBytes"], aead["variant"]) == (32, 12, 16, "AesGcmParameters.Variant.TINK"), "Microfile AEAD parameters drift")
    require(micro["freshKeysetPerMicrofile"] is True, "Fresh keyset per microfile required")
    require([(item["seconds"], item["passEligible"]) for item in micro["cadences"]] == [(5, True), (15, False), (30, False)], "Microfile cadence eligibility drift")
    require((micro["cadences"][0]["fullUnitPlaintextBytes"], micro["cadences"][0]["fullUnitCiphertextBytes"]) == (160000, 160033), "Microfile exact size drift")
    manifest = micro["manifest"]
    require(manifest["authenticatedAndEncrypted"] is True and manifest["exactEncoding"] == "DORA_RECOVERY_MANIFEST_V1_BINARY_BE", "Manifest encoding drift")
    require(manifest["aeadTemplate"] == "AES256_GCM_TINK_IV12_TAG16" and manifest["freshKeysetPerGeneration"] is True, "Manifest publication AEAD drift")
    require((manifest["magicAscii"], manifest["schemaVersion"], manifest["maximumEntries"], manifest["maximumPlaintextBytes"]) == ("DORARM01", 1, 721, 524288), "Manifest bounds drift")
    require(manifest["trailingBytesAllowed"] is False, "Manifest trailing bytes must be rejected")
    rules = manifest["entryRules"]
    require(rules["unitIndicesStrictlyIncreasingByOne"] is True and rules["plaintextRangesContiguous"] is True, "Manifest continuity/order drift")
    require(all(rules[key] is False for key in ("gapsAllowed", "duplicatesAllowed", "reorderingAllowed", "removalAllowed")), "Manifest mutation rule weakened")

    aad = protocol["associatedDataSchemas"]
    expected_aad = {
        "streaming": ("DORA_RECOVERY_STREAM_AAD_V1_BINARY_BE", "DORASA01"),
        "microfile": ("DORA_RECOVERY_MICROFILE_AAD_V1_BINARY_BE", "DORAMA01"),
        "manifestOrCheckpoint": ("DORA_RECOVERY_PUBLICATION_AAD_V1_BINARY_BE", "DORACP01"),
        "keyEnvelope": ("DORA_RECOVERY_KEY_ENVELOPE_AAD_V1_BINARY_BE", "DORAKE01"),
    }
    for name, (encoding, magic) in expected_aad.items():
        require((aad[name]["exactEncoding"], aad[name]["magicAscii"]) == (encoding, magic), f"{name} AAD schema drift")

    keys = protocol["keyProtocol"]
    require(keys["freshKeysetScopes"] == {"streaming": "one per run", "microfile": "one per microfile", "manifest": "one per manifest generation", "checkpoint": "one per checkpoint generation"}, "Fresh keyset scope drift")
    require(keys["aliasFormat"] == "android-keystore://dora.poc.recovery.v1.<lowercase-run-uuid>", "Keystore alias drift")
    require(keys["newRunCreationApi"] == "AndroidKeystoreKmsClient.generateNewAeadKey(alias)", "Keystore creation API drift")
    exact_access = "new AndroidKeystoreKmsClient.Builder().setKeyUri(alias).build().getAead(alias)"
    require(keys["keystoreClientConstruction"] == "new AndroidKeystoreKmsClient.Builder().setKeyUri(alias).build()", "Keystore Builder construction drift")
    require(keys["newRunAccessApi"] == exact_access and keys["recoveryAccessApi"] == exact_access, "Keystore access path must use the exact non-deprecated Builder chain")
    require(keys["newRunSequence"] == [
        "fail with KEY_REF_COLLISION if the alias or key-reference namespace is occupied",
        "AndroidKeystoreKmsClient.generateNewAeadKey(alias)",
        exact_access,
    ], "New-run Keystore sequence drift")
    require(keys["recoveryMayCallGenerateNewAeadKey"] is False and keys["recoveryMayCreateOrReplaceAlias"] is False, "Recovery must never generate or replace a Keystore key")
    require(keys["androidKeysetManagerAllowed"] is False and keys["getOrGenerateNewAeadKeyAllowed"] is False, "Forbidden key API enabled")
    require(keys["replacementKeyDuringRecoveryAllowed"] is False and keys["cleartextSecretKeysetSerializationAllowed"] is False, "Replacement/cleartext key handling enabled")
    require(keys["encryptedKeysetSerialization"].endswith("RegistryConfiguration.get())"), "Non-deprecated encrypted-keyset serialization drift")
    require(keys["encryptedKeysetParsing"].endswith("RegistryConfiguration.get())"), "Non-deprecated encrypted-keyset parsing drift")
    require(set(keys["classifications"]) == {"KEY_UNAVAILABLE", "KEY_UNAVAILABLE_KEY_MISMATCH", "CORRUPT_KEY_ENVELOPE", "KEY_ENVELOPE_AUTH_FAILURE", "KEY_REF_COLLISION"}, "Key classification drift")
    expected_precedence = [
        (1, "new run finds an existing alias or occupied key-reference namespace", "KEY_REF_COLLISION"),
        (2, "expected alias, key reference or key envelope is absent", "KEY_UNAVAILABLE"),
        (3, "key envelope is present but its expected length, SHA-256, binary encoding or parser validation fails", "CORRUPT_KEY_ENVELOPE"),
        (4, "alias is available, key confirmation succeeds and the structurally valid expected envelope fails AEAD, AAD or tag authentication", "KEY_ENVELOPE_AUTH_FAILURE"),
        (5, "alias resolves and the stored length/hash-valid key-confirmation ciphertext proves replacement or mismatch", "KEY_UNAVAILABLE_KEY_MISMATCH"),
    ]
    require(
        [(item["priority"], item["condition"], item["classification"]) for item in keys["classificationPrecedence"]] == expected_precedence,
        "Five-level key-classification precedence drift",
    )
    confirmation = keys["keyConfirmation"]
    require(confirmation["required"] is True and confirmation["ciphertextIdentityRecordedInSQLite"] is True and confirmation["ciphertextLengthAndSha256CheckedBeforeDecrypt"] is True, "Key-confirmation evidence contract drift")
    require(keys["ambiguousExpectedOutcomeAllowed"] is False, "Ambiguous key-fault outcomes are forbidden")
    for key in ("keyBytesAllowedInGit", "keysetsAllowedInGit", "keyBytesAllowedInLogs", "keyBytesAllowedInActionsArtifacts", "rawDatabaseAllowedInGitOrActions"):
        require(keys[key] is False, f"Secret/public-evidence boundary weakened: {key}")

    storage = protocol["storageProtocol"]
    require(storage["activeRoot"] == "context.noBackupFilesDir/poc-recovery/v1/runs/<runId>/" and storage["quarantineRoot"] == "context.noBackupFilesDir/poc-recovery/v1/quarantine/<runId>/", "Storage root drift")
    require(storage["canonicalContainmentRequired"] is True and storage["lstatEveryExistingComponentRequired"] is True and storage["regularFilesOnly"] is True, "Path containment/lstat contract drift")
    require(storage["symlinksAllowed"] is False and storage["existingFinalPathsOverwritten"] is False, "Unsafe path overwrite/symlink rule enabled")
    require(storage["publicAndroidSystemOsDurabilityCallsOnly"] == ["android.system.Os.open", "android.system.Os.fsync", "android.system.Os.rename", "android.system.Os.close"], "Filesystem durability API boundary drift")
    require("publicationOrder" not in storage, "Generic publication order must not replace candidate-specific sequences")
    require(storage["temporaryNameMapping"] == "finalRelativeName + '.tmp'", "Temporary-name mapping drift")
    require(storage["temporaryNamespaceAllowsOnlyMappedFinalNames"] is True, "Temporary namespace must be derived only from allowlisted final names")
    require(storage["exclusiveCreateFlagsForEveryNewTempOrFinalFile"] == "O_CREAT|O_EXCL|O_WRONLY|O_CLOEXEC", "Exclusive-create flags drift")
    require(storage["finalCollisionCheckRequiredImmediatelyBeforeRename"] is True, "Final collision check is required before rename")
    require(storage["collisionOrExistingTargetBlocksPublication"] is True and storage["renameMayOverwriteExistingTarget"] is False, "Collision/no-overwrite contract drift")
    require(storage["recoveryTempPromotionByNameAllowed"] is False, "Recovery must never promote a temp based on its name alone")
    final_patterns = storage["finalNamePatterns"]
    require("temporaryNamePatterns" not in storage, "Use the exact tempNamePatterns field name")
    temporary_patterns = storage["tempNamePatterns"]
    require(set(final_patterns) == set(temporary_patterns) and all(temporary_patterns[key] == final_patterns[key] + ".tmp" for key in final_patterns), "Every temp pattern must equal its allowlisted final pattern plus .tmp")
    path_coverage = storage["pathValidationCoverage"]
    require(set(path_coverage["finalPatternKeys"]) == set(final_patterns), "Final-name path-validator coverage drift")
    require(set(path_coverage["temporaryPatternKeys"]) == set(temporary_patterns), "Temporary-name path-validator coverage drift")
    require(path_coverage["requiredForEveryFinalAndTemporaryPattern"] == [
        "canonical containment beneath the active run root",
        "lstat every existing path component",
        "regular file at the leaf",
        "no symlink at any component or leaf",
    ], "Final/temp path-validator checks drift")
    expected_reconciliation_states = {
        "TEMP_ONLY": "uncommitted artifact; not published; not committed; after canonical containment, lstat, regular-file and no-symlink validation move it through the quarantine transaction",
        "TEMP_AND_FINAL": "collision; never overwrite the final; after canonical containment, lstat, regular-file and no-symlink validation move the temp through the quarantine transaction",
        "FINAL_ONLY": "validate against the authenticated manifest/checkpoint and exact SQLite identity after canonical containment, lstat, regular-file and no-symlink validation",
        "SQLITE_POINTS_TO_TEMP": "split-brain failure; the temp is not committed; never rename, relabel, publish or promote it by name",
        "UNKNOWN_OR_NON_ALLOWLISTED_NAME": "fail closed; after canonical containment, lstat, regular-file and no-symlink validation move a regular object through the quarantine transaction; unsafe path/type is an error",
    }
    require(storage["reconciliationStates"] == expected_reconciliation_states, "Exact reconciliation-state contract drift")

    expected_sequences = {
        "streamingSetup": [
            "SSET-01 exclusive-create and write stream key-envelope temp",
            "SSET-02 fsync stream key-envelope temp file",
            "SSET-03 collision-check then rename stream key-envelope temp to immutable final",
            "SSET-04 fsync stream key-envelope parent directory",
            "SSET-05 exclusive-create and write initial stream ciphertext temp",
            "SSET-06 fsync initial stream ciphertext temp file",
            "SSET-07 collision-check then rename stream ciphertext temp to immutable final",
            "SSET-08 fsync stream ciphertext parent directory",
            "SSET-09 continue all later writes through the append-only stream ciphertext descriptor that remained open across the temp-to-final rename",
        ],
        "streamingCheckpointGeneration": [
            "SCHK-01 fsync the open append-only stream ciphertext descriptor",
            "SCHK-02 create a fresh checkpoint keyset and serialize its encrypted envelope in memory",
            "SCHK-03 exclusive-create and write checkpoint key-envelope temp",
            "SCHK-04 fsync checkpoint key-envelope temp file",
            "SCHK-05 collision-check then rename checkpoint key-envelope temp to immutable final",
            "SCHK-06 fsync checkpoint key-envelope parent directory",
            "SCHK-07 encrypt checkpoint then exclusive-create and write checkpoint ciphertext temp",
            "SCHK-08 fsync checkpoint ciphertext temp file",
            "SCHK-09 collision-check then rename checkpoint ciphertext temp to immutable final",
            "SCHK-10 fsync checkpoint ciphertext parent directory",
            "SCHK-11 write exact final identities in the SQLite non-exclusive durable transaction",
            "SCHK-12 successful SQLite endTransaction return is the semantic commit",
            "SCHK-13 emit the controller evidence event after semantic commit",
        ],
        "microfileGeneration": [
            "MICRO-P01 create a fresh microfile keyset and serialize its encrypted envelope in memory",
            "MICRO-P02 exclusive-create and write microfile key-envelope temp",
            "MICRO-P03 fsync microfile key-envelope temp file",
            "MICRO-P04 collision-check then rename microfile key-envelope temp to immutable final",
            "MICRO-P05 fsync microfile key-envelope parent directory",
            "MICRO-P06 Aead.encrypt returns then exclusive-create and write microfile ciphertext temp",
            "MICRO-P07 fsync microfile ciphertext temp file",
            "MICRO-P08 collision-check then rename microfile ciphertext temp to immutable final",
            "MICRO-P09 fsync microfile ciphertext parent directory",
            "MICRO-P10 create a fresh manifest keyset and serialize its encrypted envelope in memory",
            "MICRO-P11 exclusive-create and write manifest key-envelope temp",
            "MICRO-P12 fsync manifest key-envelope temp file",
            "MICRO-P13 collision-check then rename manifest key-envelope temp to immutable final",
            "MICRO-P14 fsync manifest key-envelope parent directory",
            "MICRO-P15 encrypt manifest then exclusive-create and write manifest ciphertext temp",
            "MICRO-P16 fsync manifest ciphertext temp file",
            "MICRO-P17 collision-check then rename manifest ciphertext temp to immutable final",
            "MICRO-P18 fsync manifest ciphertext parent directory",
            "MICRO-P19 write both key envelopes, both ciphertext finals and exact hashes in the SQLite non-exclusive durable transaction",
            "MICRO-P20 successful SQLite endTransaction return is the semantic commit",
            "MICRO-P21 emit the controller evidence event after semantic commit",
        ],
    }
    require(storage["publicationSequences"] == expected_sequences, "Candidate-specific 9/13/21 publication sequences drift")
    require(storage["quarantineOrder"] == ["SQLite intent commit", "rename", "fsync source directory", "fsync destination directory", "SQLite completion commit"], "Quarantine order drift")

    sqlite = protocol["sqliteJournal"]
    require((sqlite["api"], sqlite["journalMode"], sqlite["synchronous"], sqlite["walAutocheckpoint"], sqlite["foreignKeys"]) == ("android.database.sqlite", "WAL", "FULL", 0, True), "SQLite durability profile drift")
    require(sqlite["transactionApi"] == "beginTransactionNonExclusive()" and sqlite["semanticCommitApi"] == "endTransaction() successful return", "SQLite transaction contract drift")
    require(sqlite["singleWriter"] is True and sqlite["controllerEventPartOfSemanticCommit"] is False, "SQLite writer/semantic commit drift")
    for key in ("roomAllowed", "sqlCipherAllowed", "workManagerAllowed", "bundledSQLiteAllowed", "productionSchemaAllowed", "productionMigrationAllowed", "abstractFileIdentityAllowed"):
        require(sqlite[key] is False, f"Forbidden SQLite scope enabled: {key}")
    require(set(sqlite["requiredUnitRowFields"]) >= {"ciphertextRelativeName", "ciphertextBytes", "ciphertextSha256", "keyEnvelopeRelativeName", "keyEnvelopeBytes", "keyEnvelopeSha256"}, "Exact unit file identity fields drift")
    require(set(sqlite["requiredPublicationRowFields"]) >= {"publicationRelativeName", "publicationBytes", "publicationSha256", "keyEnvelopeRelativeName", "keyEnvelopeBytes", "keyEnvelopeSha256", "previousPublicationCiphertextSha256"}, "Exact publication file identity fields drift")
    require(sqlite["processingIntentId"]["inputEncoding"].startswith("LP16(protocolId) || LP16(candidateId)"), "processingIntentId encoding drift")
    require(sqlite["processingIntentId"]["uniqueConstraintRequired"] is True, "processingIntentId must be UNIQUE")
    require(sqlite["freshPreflight"]["compileOptionsCanonicalization"] == "exact rows sorted lexicographically, UTF-8 encoded, joined by LF and terminated by one LF", "SQLite compile-options canonicalization drift")
    require(sqlite["freshPreflight"]["mismatchBlocksExecution"] is True, "SQLite preflight mismatch must block")

    campaign = protocol["hardKillCampaign"]
    require((campaign["signal"], campaign["gracefulStopAllowed"], campaign["baseAttemptsPerCandidate"], campaign["validHardKillsMinimumPerCandidate"]) == ("SIGKILL", False, 120, 100), "Hard-kill campaign drift")
    require([item["id"] for item in campaign["strata"]] == [f"K{index:02d}" for index in range(1, 13)], "K01-K12 strata drift")
    strata = {item["id"]: item for item in campaign["strata"]}
    expected_barrier_markers = {
        "K01": ("SCHK-01", "MICRO-P06"),
        "K02": ("SCHK-01", "MICRO-P06"),
        "K03": ("SCHK-01", "MICRO-P07"),
        "K04": ("SCHK-01", "MICRO-P07"),
        "K05": ("SCHK-06", "MICRO-P09"),
        "K06": ("SCHK-07", "MICRO-P14"),
        "K07": ("SCHK-08", "MICRO-P16"),
        "K08": ("SCHK-10", "MICRO-P18"),
        "K09": ("SCHK-11", "MICRO-P19"),
        "K10": ("SCHK-12 endTransaction() returned; SCHK-13 controller event not emitted", "MICRO-P20 endTransaction() returned; MICRO-P21 controller event not emitted"),
        "K11": ("SCHK-13 controller event durably acknowledged", "MICRO-P21 controller event durably acknowledged"),
        "K12": ("K12-STREAM-V0.3", "K12-MICROFILE-V0.3"),
    }
    for stratum_id, (stream_marker, micro_marker) in expected_barrier_markers.items():
        require(stream_marker in strata[stratum_id]["REC-STREAM-TINK"], f"{stratum_id} streaming barrier does not align to the v0.3 sequence")
        require(micro_marker in strata[stratum_id]["REC-MICROFILE-TINK"], f"{stratum_id} microfile barrier does not align to the v0.3 sequence")
    require("downstream ciphertext OutputStream callback" in strata["K02"]["REC-STREAM-TINK"] and strata["K02"]["REC-MICROFILE-TINK"].startswith("MICROFILE_AFTER_AEAD_RETURN_BEFORE_TEMP_WRITE:"), "Candidate-specific K02 barrier drift")
    stream_seed = campaign["k12Seeds"]["REC-STREAM-TINK"]
    micro_seed = campaign["k12Seeds"]["REC-MICROFILE-TINK"]
    require((stream_seed["seedId"], stream_seed["q"], stream_seed["A"], stream_seed["C"], stream_seed["R"]) == ("K12-STREAM-V0.3", 3, 12216, 8136, 8136), "Streaming K12 seed drift")
    require((micro_seed["seedId"], micro_seed["committedFullUnits"], micro_seed["A"], micro_seed["C"], micro_seed["R"]) == ("K12-MICROFILE-V0.3", 2, 360000, 320000, 320000), "Microfile K12 seed drift")
    require("one stable processing intent" in stream_seed["canonicalExpected"] and "two stable processing intents" in micro_seed["canonicalExpected"], "K12 canonical result drift")
    require(campaign["replacement"]["automaticReplacementAllowed"] is False and campaign["replacement"]["candidateFailureReplaceable"] is False, "Hard-kill replacement rule weakened")

    faults = protocol["faultCampaign"]
    expected_fault_ids = {
        "COR-01", "COR-02", "COR-03", "COR-04", "COR-05", "COR-06",
        "TRU-01", "TRU-02", "TRU-03",
        "KEY-01", "KEY-02", "KEY-03", "KEY-04", "KEY-05", "KEY-06", "KEY-07",
        "SPL-01", "SPL-02", "SPL-03", "SPL-04", "SPL-05",
        "RBK-01", "RBK-02", "PAR-01",
        "QUA-01", "QUA-02", "QUA-03",
        "IDE-01", "IDE-02", "EVT-01",
        "CLN-01", "CLN-02", "CLN-03",
    }
    require(faults["mandatoryFaultRowCount"] == 33, "Protocol mandatory fault row count must be 33")
    require({case["id"] for case in faults["cases"]} == expected_fault_ids and len(faults["cases"]) == 33, "Fault matrix ID drift")
    require(all(" or " not in case["expected"].lower() for case in faults["cases"]), "Fault expected outcomes must be singular and unambiguous")
    require(all(case["phaseARepetitionProfile"] == "PHASE_A_STANDARD" for case in faults["cases"]), "Every fault requires the standard Phase A repetition profile")
    require(faults["repetitionProfiles"]["PHASE_A_STANDARD"] == {"PINNED_API36_X86_64_EMULATOR": 3, "PHYSICAL_D2": 1}, "Phase A fault repetitions drift")
    rollback = protocol["rollbackModel"]
    require(rollback["externalPhaseAControllerLedgerIsRollbackAnchor"] is True and rollback["globalCryptographicAntiRollbackClaimAllowedWithoutExternalLedger"] is False, "Rollback claim boundary drift")


def validate_evidence() -> None:
    inventory = read_json(EVIDENCE / "dependency-inventory.json")
    license_notice = read_json(EVIDENCE / "license-notice-inventory.json")
    authenticity = read_json(EVIDENCE / "dependency-ip-authenticity-v0.3.json")
    security = read_json(EVIDENCE / "security-advisory-inventory.json")
    sqlite = read_json(EVIDENCE / "sqlite-platform-provenance.json")
    roles = read_json(EVIDENCE / "review-roles.json")
    readiness = read_json(EVIDENCE / "readiness.json")
    findings_v01 = read_json(EVIDENCE / "review-findings-v0.1.json")
    findings_v02 = read_json(EVIDENCE / "review-findings-v0.2.json")
    evidence_index = read_json(EVIDENCE / "evidence-index.json")

    require(inventory["schemaVersion"] == 2, "Dependency inventory schema drift")
    require(inventory["inventoryStatus"] == "VERIFIED_PUBLISHER_CLOSURE_AUTHENTICITY_VERIFIED_LICENSE_CONFLICT_PACKAGE_REVIEW_ONLY", "Dependency inventory authenticity/license state drift")
    require(inventory["rootCoordinate"] == "com.google.crypto.tink:tink-android:1.23.0", "Inventory root drift")
    require(inventory["dependencyAdmission"] is False and inventory["runtimeGraphModified"] is False, "Dependency/runtime graph was modified")
    require(inventory["resolution"]["externalCoordinateCountIncludingRoot"] == 8 and len(inventory["artifacts"]) == 8, "Tink closure count drift")
    require(inventory["resolution"]["projectGradleResolvedGraph"] is False and inventory["resolution"]["futureExactResolutionRequired"] is True, "Future Gradle graph boundary drift")
    require(all(artifact["jar"]["nativeEntries"] == 0 for artifact in inventory["artifacts"]), "Native payload found")
    root = next(artifact for artifact in inventory["artifacts"] if artifact["coordinate"] == inventory["rootCoordinate"])
    require(root["jar"]["sha256"] == "c656918451b01c45ce5b20c7b6d4c388f956f61b3a3528e769048c8944c42f9e", "Tink JAR digest drift")
    require(root["pom"]["sha256"] == "a2d27e7207e6a25764859b62924fc7b972f41884ce272cead9b946c15a1f410f", "Tink POM digest drift")
    require(inventory["sourceRelease"]["commit"] == "1bedd75ae7161017c5f45b020395a72bbd40645d", "Tink source commit drift")

    require(inventory["authenticityEvidence"]["locator"] == "docs/evidence/poc-recovery-001/dependency-ip-authenticity-v0.3.json", "Dependency authenticity locator drift")
    require(inventory["authenticityEvidence"]["status"] == "AUTHENTICITY_VERIFIED_LICENSE_CONFLICT_PRODUCT_IP_APPROVAL_BLOCKED", "Dependency authenticity summary drift")
    require(inventory["authenticityEvidence"]["coordinatesAuthenticityVerified"] == 8 and inventory["authenticityEvidence"]["coordinatesAuthenticityPending"] == 0 and inventory["authenticityEvidence"]["coordinatesWithLicenseConflict"] == 1, "Dependency authenticity/license counts drift")

    require(license_notice["schemaVersion"] == 3 and license_notice["reviewStatus"] == "AUTHENTICITY_VERIFIED_LICENSE_CONFLICT_PRODUCT_IP_APPROVAL_BLOCKED", "License package state drift")
    require(license_notice["coordinateEvidenceLocator"] == "docs/evidence/poc-recovery-001/dependency-ip-authenticity-v0.3.json", "License coordinate-evidence locator drift")
    require(license_notice["evaluationApproved"] is False and license_notice["redistributionApproved"] is False and license_notice["productionLegalApproved"] is False, "License/Product-IP approval was prematurely recorded")
    require(license_notice["approvedReviewer"] is None and license_notice["approvedOn"] is None, "License/Product-IP approval identity/date must remain null")
    require(license_notice["summary"]["externalCoordinates"] == 8 and len(license_notice["components"]) == 9, "License component count drift")
    require(license_notice["summary"]["externalCoordinatesAuthenticityVerified"] == 8 and license_notice["summary"]["externalCoordinatesWithAuthenticityPending"] == 0, "License authenticity counts drift")
    require(license_notice["summary"]["unresolvedLicenseConflicts"] == ["com.google.code.findbugs:jsr305:3.0.2"] and license_notice["summary"]["authenticityMustCloseBeforeProductIpApproval"] is False and license_notice["summary"]["licenseConflictMustCloseBeforeProductIpApproval"] is True, "License-conflict blocker drift")

    require(authenticity["schemaVersion"] == 2 and authenticity["overallStatus"] == "AUTHENTICITY_VERIFIED_LICENSE_CONFLICT_PRODUCT_IP_APPROVAL_BLOCKED", "Coordinate authenticity state drift")
    require(authenticity["summary"] == {
        "coordinates": 8,
        "jarSha256Verified": 8,
        "pomSha256Verified": 8,
        "publisherChecksumsMatched": 16,
        "detachedSignaturesPresent": 16,
        "detachedSignaturesCryptographicallyVerified": 16,
        "coordinatesWithUpstreamSignerTrustConfirmed": 2,
        "coordinatesWithExactSourceCorrespondenceConfirmed": 6,
        "coordinatesAuthenticityVerified": 8,
        "coordinatesAuthenticityPending": 0,
        "coordinatesWithLicenseConflict": 1,
        "dependencyAdmission": False,
        "productionAdmission": False,
    }, "Coordinate authenticity summary drift")
    artifacts_by_coordinate = {artifact["coordinate"]: artifact for artifact in inventory["artifacts"]}
    authentic_components = {component["coordinate"]: component for component in authenticity["components"]}
    require(set(authentic_components) == set(artifacts_by_coordinate), "Coordinate authenticity coverage drift")
    for coordinate, component in authentic_components.items():
        artifact = artifacts_by_coordinate[coordinate]
        require(component["licenseId"] and component["upstreamLicenseTextLocator"].startswith("https://") and len(component["licenseTextSha256"]) == 64, f"{coordinate} license evidence incomplete")
        require(component["copyrightEvidence"]["locator"].startswith("https://") and component["copyrightEvidence"]["status"], f"{coordinate} copyright evidence incomplete")
        require(component["notice"]["requirement"] and component["notice"]["locator"].startswith("https://") and component["notice"]["result"], f"{coordinate} NOTICE evidence incomplete")
        require(component["verifiedAtUtc"] == authenticity["verifiedAtUtc"] and component["verificationTool"] == authenticity["verificationTool"], f"{coordinate} timestamp/tool attribution drift")
        for kind in ("jar", "pom"):
            item = component[kind]
            require(item["sha256"] == artifact[kind]["sha256"], f"{coordinate} {kind} inventory/authenticity hash mismatch")
            checksum = item["publisherChecksum"]
            signature = item["detachedSignature"]
            require(checksum["locator"].startswith("https://") and checksum["result"] == "MATCH" and checksum["value"], f"{coordinate} {kind} publisher checksum incomplete")
            require(signature["locator"].startswith("https://") and signature["result"] == "CRYPTOGRAPHICALLY_VERIFIED" and re.fullmatch(r"[0-9A-F]{40}", signature["signerFingerprint"]) and re.fullmatch(r"[0-9A-F]{40}", signature["primaryKeyFingerprint"]), f"{coordinate} {kind} detached-signature evidence incomplete")
            require(signature["signatureCreatedUtc"].endswith("Z") and signature["hashAlgorithm"] in {"SHA1", "SHA256", "SHA512"}, f"{coordinate} {kind} signature metadata incomplete")
        require(component["jar"]["detachedSignature"]["signerFingerprint"] == component["pom"]["detachedSignature"]["signerFingerprint"], f"{coordinate} JAR/POM signer mismatch")
        require(component["signerIdentity"] and component["signerTrustStatus"] in {"CONFIRMED_BY_UPSTREAM_PUBLISHER_DOCUMENTATION", "CONFIRMED_BY_UPSTREAM_TAGGED_KEY_PUBLICATION", "NOT_CLAIMED_SOURCE_CORRESPONDENCE_ROUTE_USED"}, f"{coordinate} signer-trust evidence drift")
        require(component["sourceCorrespondence"]["repository"].startswith("https://") and component["sourceCorrespondence"]["evidenceLocator"].startswith("https://") and component["sourceCorrespondence"]["status"], f"{coordinate} source-correspondence evidence incomplete")
        require(component["authenticityStatus"] in {"AUTHENTICITY_VERIFIED_PUBLISHER_BOUND_SIGNATURE", "AUTHENTICITY_VERIFIED_EXACT_REPRODUCIBLE_SOURCE", "AUTHENTICITY_VERIFIED_MULTISOURCE_CORRESPONDENCE", "AUTHENTICITY_PENDING", "AUTHENTICITY_REJECTED"}, f"{coordinate} authenticity classification outside closed enum")
        if component["authenticityStatus"] == "AUTHENTICITY_VERIFIED_MULTISOURCE_CORRESPONDENCE":
            source_jar = component["sourceCorrespondence"]["sourceJar"]
            comparison = component["sourceCorrespondence"]["comparison"]
            reproducible = component["sourceCorrespondence"]["reproducibleBuild"]
            require(component["closurePath"] is None and component["signerTrustSource"] is None and component["signerTrustStatus"] == "NOT_CLAIMED_SOURCE_CORRESPONDENCE_ROUTE_USED", f"{coordinate} multisource trust boundary drift")
            require(source_jar["locator"].startswith("https://") and len(source_jar["sha256"]) == 64 and source_jar["signatureResult"] == "CRYPTOGRAPHICALLY_VERIFIED", f"{coordinate} source-JAR evidence incomplete")
            require(comparison["sourceEntries"] == comparison["exactCommitBlobMatches"] + comparison["declaredGeneratedEntries"] and comparison["unexplainedEntries"] == 0, f"{coordinate} source correspondence is not exhaustive")
            require(reproducible["byteForByteBinaryRebuildAttempted"] is False and reproducible["status"] == "NOT_CLAIMED" and reproducible["limitation"], f"{coordinate} reproducibility limitation missing")
        else:
            require(component["authenticityStatus"] == "AUTHENTICITY_VERIFIED_PUBLISHER_BOUND_SIGNATURE" and component["closurePath"] is None and component["signerTrustSource"].startswith("https://"), f"{coordinate} verified publisher-bound trust evidence drift")
    require(sum(component["authenticityStatus"] == "AUTHENTICITY_VERIFIED_MULTISOURCE_CORRESPONDENCE" for component in authentic_components.values()) == 6, "Exactly six coordinates must use multisource correspondence")
    require(sum(component["authenticityStatus"] == "AUTHENTICITY_VERIFIED_PUBLISHER_BOUND_SIGNATURE" for component in authentic_components.values()) == 2, "Exactly two coordinates must use publisher-bound signatures")
    require(not any(component["authenticityStatus"] in {"AUTHENTICITY_PENDING", "AUTHENTICITY_REJECTED"} for component in authentic_components.values()), "No coordinate may remain authenticity-pending/rejected")
    jsr305 = authentic_components["com.google.code.findbugs:jsr305:3.0.2"]
    require(jsr305["licenseId"] == "NOASSERTION" and jsr305["licenseEvidence"]["status"] == "LICENSE_CONFLICT_PRODUCT_IP_DECISION_REQUIRED", "JSR305 license-conflict state drift")
    require(jsr305["licenseEvidence"]["publishedSignedPom"]["declaredSpdx"] == "Apache-2.0" and jsr305["licenseEvidence"]["exactReleaseSource"]["declaredSpdx"] == "BSD-3-Clause", "JSR305 conflicting license declarations drift")
    require(jsr305["licenseEvidence"]["exactMissingExternalFact"] and len(jsr305["licenseEvidence"]["safeOwnerChoices"]) == 3, "JSR305 Product/IP closure boundary incomplete")
    require(authenticity["approvalBoundary"]["productIpApprovalAllowedWhileAnyCoordinatePending"] is False, "Product/IP approval must be blocked while authenticity is pending")
    require(authenticity["approvalBoundary"]["productIpApprovalAllowedWhileLicenseConflict"] is False and authenticity["approvalBoundary"]["productIpFinalApproval"] is False, "Product/IP approval must remain blocked by the license conflict")

    require(len(security["exactVersionQueries"]) == 8, "Security exact-version query count drift")
    require(all(not query["affectedPublishedAdvisories"] for query in security["exactVersionQueries"]), "Recorded exact version has a published affected advisory")
    require(security["status"] == "SNAPSHOT_COMPLETE_INDEPENDENT_REVIEW_PENDING", "Security inventory review state drift")
    risk_states = {item["id"]: item["mitigationState"] for item in security["templateAndProtocolRisks"]}
    require(set(risk_states) == {"SEC-REC-01", "SEC-REC-02", "SEC-REC-03", "SEC-REC-04"}, "Security protocol-risk set drift")
    require(all("V0_3_" in state and "REQUIRED" in state for state in risk_states.values()), "Security risk remediation state must target v0.3 without claiming implementation approval")

    require(sqlite["schemaVersion"] == 2 and sqlite["platformApi"] == "android.database.sqlite", "SQLite provenance schema/API drift")
    require(sqlite["phaseA"]["executionAllowed"] is False, "SQLite provenance cannot authorize execution")
    require(sqlite["separateSQLiteLibraryDownloadedBundledOrRedistributed"] is False, "Separate SQLite is forbidden")
    for key in ("roomAllowed", "sqlCipherAllowed", "workManagerAllowed", "productionSchemaAllowed", "productionAdmission"):
        require(sqlite[key] is False, f"Forbidden platform SQLite scope enabled: {key}")
    environments = {item["id"]: item for item in sqlite["phaseA"]["environments"]}
    require(set(environments) == {"PINNED_API36_X86_64_EMULATOR", "PHYSICAL_D2"}, "Phase A provenance environment drift")
    require(environments["PINNED_API36_X86_64_EMULATOR"]["archive"]["sha256"] == "b1bb0769d0bed7698e61f203d7dc9bf6e7c37cd01a39d0d8788a11186bc78160", "Emulator image digest drift")
    require(all(value is None for value in environments["PINNED_API36_X86_64_EMULATOR"]["requiredFreshRuntimeEvidence"].values()), "Emulator fresh preflight must remain pending")
    require(all(value is None for value in environments["PHYSICAL_D2"]["requiredFreshRuntimeEvidence"].values()), "D2 fresh preflight must remain pending")

    role_map = roles["roles"]
    require(role_map["stage0ProductIp"]["status"] == "ASSIGNED_FINAL_APPROVAL_BLOCKED_LICENSE_CONFLICT" and role_map["stage0ProductIp"]["finalApproved"] is False, "Product/IP final review state drift")
    require(role_map["stage0ProductIp"]["approvedReviewer"] is None and role_map["stage0ProductIp"]["approvedOn"] is None, "Product/IP approval identity/date must remain null")
    independent = role_map["independentRecoveryEngineeringSecurity"]
    require(independent["reviewer"] is None and independent["status"] == "UNASSIGNED_BLOCKING", "Accountable independent review blocker drift")
    require(independent["currentCodexRemediationClaimedFormallyIndependent"] is False and independent["replacesProductionSecurity"] is False, "Review independence boundary drift")
    require(role_map["executionAuthorizer"]["status"] == "AUTHORIZATION_WITHHELD", "Execution authorization must remain withheld")
    require(role_map["productionLegal"]["reviewer"] is None and role_map["productionSecurity"]["reviewer"] is None, "Production reviewers must remain unassigned")

    require(readiness["schemaVersion"] == 3 and readiness["status"] == "BLOCKED_REMEDIATED_PROTOCOL_V0_3_LICENSE_PRODUCT_IP_REVIEW_AND_IMPLEMENTATION_PENDING", "Readiness status drift")
    require(readiness["executionAllowed"] is False, "Readiness unexpectedly allows execution")
    for key in (
        "implementationAllowedByThisPackage", "measuredExecutionAllowed", "runtimeDependencyAdded",
        "recoveryModuleExists", "harnessImplemented", "killCampaignExecuted",
        "deviceTestsExecuted", "benchmarksExecuted", "productionAppChanged",
    ):
        require(readiness[key] is False, f"Governance-only invariant violated: {key}")
    require(readiness["packageArtifacts"]["activeGateSetVersion"] == GATE_V03 and readiness["packageArtifacts"]["activeProtocolId"] == PROTOCOL_V03, "Readiness active protocol locator drift")
    require(readiness["packageArtifacts"]["v01RetainedAsSupersededAuditArtifact"] is True and readiness["packageArtifacts"]["v01Executable"] is False, "v0.1 audit disposition drift")
    require(readiness["packageArtifacts"]["v02RetainedAsSupersededAuditArtifact"] is True and readiness["packageArtifacts"]["v02Executable"] is False, "v0.2 audit disposition drift")
    require(readiness["packageArtifacts"]["supplyChainAuthenticityStatus"] == "AUTHENTICITY_VERIFIED_LICENSE_CONFLICT_PRODUCT_IP_APPROVAL_BLOCKED", "Readiness authenticity state drift")
    expected_blockers = {
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
    }
    require({blocker["id"] for blocker in readiness["blockers"]} == expected_blockers, "Readiness blocker set drift")
    supply_chain_blocker = next(blocker for blocker in readiness["blockers"] if blocker["id"] == "REC-RDY-11-SUPPLY-CHAIN-AUTHENTICITY")
    require(supply_chain_blocker["status"] == "AUTHENTICITY_VERIFIED_LICENSE_CONFLICT_OPEN" and "jsr305:3.0.2" in supply_chain_blocker["condition"] and "Apache-2.0" in supply_chain_blocker["condition"] and "BSD-3-Clause" in supply_chain_blocker["condition"], "Supply-chain blocker disposition drift")

    require(findings_v01["schemaVersion"] == 2 and findings_v01["reviewedCommit"] == REVIEWED_V01_HEAD and findings_v01["reviewedGateSetVersion"] == "poc-recovery-stage0-v0.1", "First-review findings ledger identity drift")
    require(findings_v01["sanitized"] is True and findings_v01["reviewerIdentity"] is None and findings_v01["formalReviewer"] is False, "First-review findings ledger must remain sanitized and non-formal")
    require([finding["id"] for finding in findings_v01["findings"]] == [f"REC-GOV-V01-{index:03d}" for index in range(1, 12)], "First-review stable finding IDs drift")
    require([finding["severity"] for finding in findings_v01["findings"]] == ["P0"] * 7 + ["P1", "P1", "P1", "P2"], "First-review severity mapping drift")
    require(all(finding["formalReviewer"] is False for finding in findings_v01["findings"]), "First-review findings must not claim a formal reviewer")
    require(findings_v02["schemaVersion"] == 2 and findings_v02["reviewedCommit"] == REVIEWED_V02_HEAD and findings_v02["reviewedGateSetVersion"] == "poc-recovery-stage0-v0.2", "Current findings ledger identity drift")
    require(findings_v02["sanitized"] is True and findings_v02["reviewerIdentity"] is None and findings_v02["formalReviewer"] is False, "Current findings ledger must remain sanitized and non-formal")
    require([finding["id"] for finding in findings_v02["findings"]] == [f"F-{index:02d}" for index in range(1, 7)], "Current stable finding IDs drift")
    expected_current_statuses = ["CLOSED"] * 6
    require([finding["status"] for finding in findings_v02["findings"]] == expected_current_statuses and all(finding["formalReviewer"] is False for finding in findings_v02["findings"]), "Current findings disposition drift")
    f06 = findings_v02["findings"][-1]
    require(f06["authenticityDisposition"] == "ALL_EIGHT_COORDINATES_VERIFIED" and f06["closureBasis"] and "jsr305:3.0.2" in f06["postClosureProductIpBlocker"] and "Apache-2.0" in f06["postClosureProductIpBlocker"] and "BSD-3-Clause" in f06["postClosureProductIpBlocker"], "F-06 closure/Product-IP boundary drift")
    for ledger in (findings_v01, findings_v02):
        require(ledger["remediationCommitSemantics"].startswith("SELF means"), "Ledger remediation-commit semantics missing")
        for finding in ledger["findings"]:
            require(finding["sourceReviewDate"] == "2026-08-12", f"{finding['id']} source review date drift")
            require(finding["affectedArtifact"] and finding["finding"] and finding["remediationCommit"] == "SELF" and finding["remediationVersion"] == PROTOCOL_V03, f"{finding['id']} traceability fields incomplete")
            require(finding["status"] in {"CLOSED", "PARTIALLY_CLOSED", "OPEN"} and finding["evidenceLocator"], f"{finding['id']} disposition/evidence fields incomplete")

    require(evidence_index["activeGateSetVersion"] == GATE_V03 and evidence_index["activeProtocolId"] == PROTOCOL_V03 and evidence_index["executionAllowed"] is False, "Evidence index active protocol drift")
    require(evidence_index["status"] == "GOVERNANCE_V0_3_AUTHENTICITY_VERIFIED_LICENSE_CONFLICT_EXECUTION_BLOCKED", "Evidence-index status drift")
    indexed_artifacts = {item["id"]: item for item in evidence_index["artifacts"]}
    require(indexed_artifacts["REC-V03-REMEDIATION"]["status"] == "F01_F06_CLOSED_LICENSE_CONFLICT_PRODUCT_IP_BLOCKED", "Remediation indexed state drift")
    require(indexed_artifacts["REC-DEPENDENCY-IP-AUTHENTICITY-VERIFICATION-20260812"]["status"] == "CRYPTOGRAPHIC_AND_SOURCE_CORRESPONDENCE_EVIDENCE_RECORDED_NO_APPROVAL", "Authenticity verification report is not indexed")
    require(indexed_artifacts["REC-IP-REVIEW"]["status"] == "AUTHENTICITY_VERIFIED_LICENSE_CONFLICT_REVIEW_BLOCKED", "IP-review indexed state drift")
    indexed_hashes = {Path(item["locator"]).name: item["sha256"] for item in evidence_index["supersededAuditArtifacts"]}
    require(indexed_hashes == IMMUTABLE_AUDIT_HASHES, "Evidence index immutable audit hashes drift")


def validate_documents_and_no_implementation() -> None:
    owner_record = read_text(STAGE0 / "DORA_MVP1_POC_RECOVERY_OWNER_DECISION_OD14.md")
    decision = read_text(STAGE0 / "DEC-044-POC-RECOVERY-EXPERIMENT.md")
    gate_markdown = read_text(STAGE0 / "DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_3.md")
    remediation = read_text(EVIDENCE / "governance-remediation-v0.3.md")
    review = read_text(EVIDENCE / "ip-stage0-evaluation-review.md")
    require("Decision ID: `OD-14`" in owner_record and "executionAllowed=false" in owner_record and "v0.3" in owner_record, "OD-14 v0.3 boundary missing")
    require("Status: **Proposed" in decision and "executionAllowed=false" in decision, "DEC-044 must remain Proposed and non-executable")
    for required in (
        "DURABLE_ONE_SEGMENT_LOOKAHEAD", "DORA_RECOVERY_MANIFEST_V1_BINARY_BE",
        "DORASA01", "DORAMA01", "DORACP01", "DORAKE01",
        "0 <= C <= R <= A", "K12-STREAM-V0.3", "K12-MICROFILE-V0.3",
        "AndroidKeystoreKmsClient.Builder", "finalRelativeName + \".tmp\"",
        "TEMP_ONLY", "TEMP_AND_FINAL", "FINAL_ONLY", "SQLITE_POINTS_TO_TEMP", "UNKNOWN_OR_NON_ALLOWLISTED_NAME",
        "KEY-01", "KEY-07", "33",
    ):
        require(required in gate_markdown, f"Recovery Gate Set v0.3 is missing: {required}")
    require(REVIEWED_V02_HEAD in remediation and "executionAllowed=false" in remediation, "Remediation traceability/boundary missing")
    require("owner-remediated protocol v0.3" in review.lower() and "AUTHENTICITY VERIFIED" in review and "LICENSE CONFLICT" in review, "Product/IP final review/authenticity-license state drift")

    recovery_module = ROOT / "android" / "poc" / "recovery"
    require(not recovery_module.exists(), "android/poc/recovery must not exist in governance package")
    gradle_files = list((ROOT / "android").rglob("*.gradle")) + list((ROOT / "android").rglob("*.gradle.kts"))
    gradle_files += [ROOT / "android" / "gradle" / "libs.versions.toml"]
    tink_pattern = re.compile(r"com\.google\.crypto\.tink|tink-android", flags=re.IGNORECASE)
    contaminated = [str(path.relative_to(ROOT)) for path in gradle_files if path.is_file() and tink_pattern.search(path.read_text(encoding="utf-8"))]
    require(not contaminated, f"Tink appeared in Android Gradle/runtime configuration: {contaminated}")
    require(":poc:recovery" not in read_text(ROOT / "android" / "settings.gradle.kts"), "Recovery module was included in Gradle settings")
    app_manifest = read_text(ROOT / "android" / "app" / "src" / "main" / "AndroidManifest.xml")
    require("android.permission.RECORD_AUDIO" not in app_manifest, "Production :app microphone permission is forbidden")

    device_matrix = read_text(STAGE0 / "device-matrix.yaml")
    require("protocol: poc-recovery-protocol-stage0-v0.3" in device_matrix, "Recovery device contract must target v0.3")
    require("execution_allowed: false" in device_matrix, "Device contract must withhold execution")
    backlog = read_text(ROOT / "docs" / "DORA_MVP1_IMPLEMENTATION_BACKLOG.md")
    recovery_row = next(line for line in backlog.splitlines() if line.startswith("| POC-RECOVERY-001 |"))
    require("| BLOCKED |" in recovery_row and "executionAllowed=false" in recovery_row, "Recovery backlog must remain BLOCKED")


def main() -> int:
    gate = read_json(STAGE0 / "poc-recovery-gate-set-stage0-v0.3.json")
    protocol = read_json(STAGE0 / "poc-recovery-protocol-stage0-v0.3.json")
    validate_supersession(gate, protocol)
    validate_gate(gate)
    validate_protocol(protocol)
    validate_evidence()
    validate_documents_and_no_implementation()
    print("POC-RECOVERY-001 governance v0.3 validation passed; v0.1/v0.2 immutable and retained; executionAllowed=false")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, StopIteration, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
