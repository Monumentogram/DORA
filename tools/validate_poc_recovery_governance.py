#!/usr/bin/env python3
"""Validate the governance-only POC-RECOVERY-001 v0.2 package without executing a PoC."""

from __future__ import annotations

import json
import re
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
STAGE0 = ROOT / "docs" / "stage0"
EVIDENCE = ROOT / "docs" / "evidence" / "poc-recovery-001"
REVIEWED_HEAD = "87f8c00c6afce0f658678a7a09b1a394b89a2454"
GATE_V02 = "poc-recovery-stage0-v0.2"
PROTOCOL_V02 = "poc-recovery-protocol-stage0-v0.2"


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
    legacy_paths = (
        STAGE0 / "DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_1.md",
        STAGE0 / "poc-recovery-gate-set-stage0-v0.1.json",
        STAGE0 / "poc-recovery-protocol-stage0-v0.1.json",
    )
    require(all(path.is_file() for path in legacy_paths), "v0.1 superseded audit artifacts must be retained")
    require(gate["supersedes"] == {
        "gateSetVersion": "poc-recovery-stage0-v0.1",
        "disposition": "SUPERSEDED_AUDIT_ARTIFACT_NON_EXECUTABLE",
        "reviewedCommit": REVIEWED_HEAD,
    }, "Gate Set v0.1 supersession record drift")
    require(protocol["supersedes"] == {
        "protocolId": "poc-recovery-protocol-stage0-v0.1",
        "disposition": "SUPERSEDED_AUDIT_ARTIFACT_NON_EXECUTABLE",
        "reviewedCommit": REVIEWED_HEAD,
    }, "Protocol v0.1 supersession record drift")


def validate_gate(gate: dict[str, Any]) -> None:
    require(gate["schemaVersion"] == 2, "Gate Set schema drift")
    require(gate["pocId"] == "POC-RECOVERY-001", "PoC ID drift")
    require(gate["gateSetVersion"] == GATE_V02, "Active Gate Set must be v0.2")
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
    require(len(gate["mandatoryFaultIds"]) == 31 and len(set(gate["mandatoryFaultIds"])) == 31, "Mandatory fault set must contain 31 unique cases")


def validate_protocol(protocol: dict[str, Any]) -> None:
    require(protocol["schemaVersion"] == 2 and protocol["protocolId"] == PROTOCOL_V02, "Protocol v0.2 identity drift")
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
    require(keys["recoveryAccessApi"] == "AndroidKeystoreKmsClient.getAead(alias)", "Keystore recovery API drift")
    require(keys["androidKeysetManagerAllowed"] is False and keys["getOrGenerateNewAeadKeyAllowed"] is False, "Forbidden key API enabled")
    require(keys["replacementKeyDuringRecoveryAllowed"] is False and keys["cleartextSecretKeysetSerializationAllowed"] is False, "Replacement/cleartext key handling enabled")
    require(keys["encryptedKeysetSerialization"].endswith("RegistryConfiguration.get())"), "Non-deprecated encrypted-keyset serialization drift")
    require(keys["encryptedKeysetParsing"].endswith("RegistryConfiguration.get())"), "Non-deprecated encrypted-keyset parsing drift")
    require(set(keys["classifications"]) == {"KEY_UNAVAILABLE", "KEY_UNAVAILABLE_KEY_MISMATCH", "CORRUPT_KEY_ENVELOPE", "KEY_ENVELOPE_AUTH_FAILURE", "KEY_REF_COLLISION"}, "Key classification drift")
    for key in ("keyBytesAllowedInGit", "keysetsAllowedInGit", "keyBytesAllowedInLogs", "keyBytesAllowedInActionsArtifacts", "rawDatabaseAllowedInGitOrActions"):
        require(keys[key] is False, f"Secret/public-evidence boundary weakened: {key}")

    storage = protocol["storageProtocol"]
    require(storage["activeRoot"] == "context.noBackupFilesDir/poc-recovery/v1/runs/<runId>/" and storage["quarantineRoot"] == "context.noBackupFilesDir/poc-recovery/v1/quarantine/<runId>/", "Storage root drift")
    require(storage["canonicalContainmentRequired"] is True and storage["lstatEveryExistingComponentRequired"] is True and storage["regularFilesOnly"] is True, "Path containment/lstat contract drift")
    require(storage["symlinksAllowed"] is False and storage["existingFinalPathsOverwritten"] is False, "Unsafe path overwrite/symlink rule enabled")
    require(storage["publicAndroidSystemOsDurabilityCallsOnly"] == ["android.system.Os.open", "android.system.Os.fsync", "android.system.Os.rename", "android.system.Os.close"], "Filesystem durability API boundary drift")
    require(storage["publicationOrder"][-2:] == ["SQLite durable transaction and successful endTransaction return", "controller evidence event"], "Publication/commit order drift")
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
    k02 = campaign["strata"][1]
    require("downstream ciphertext OutputStream callback" in k02["REC-STREAM-TINK"] and k02["REC-MICROFILE-TINK"].startswith("MICROFILE_AFTER_AEAD_RETURN_BEFORE_TEMP_WRITE:"), "Candidate-specific K02 barrier drift")
    for index in range(4, 8):
        require("fsync" in " ".join(campaign["strata"][index].values()).lower() or "durable" in " ".join(campaign["strata"][index].values()).lower(), f"K{index + 1:02d} durability barrier drift")
    stream_seed = campaign["k12Seeds"]["REC-STREAM-TINK"]
    micro_seed = campaign["k12Seeds"]["REC-MICROFILE-TINK"]
    require((stream_seed["seedId"], stream_seed["q"], stream_seed["A"], stream_seed["C"], stream_seed["R"]) == ("K12-STREAM-V0.2", 3, 12216, 8136, 8136), "Streaming K12 seed drift")
    require((micro_seed["seedId"], micro_seed["committedFullUnits"], micro_seed["A"], micro_seed["C"], micro_seed["R"]) == ("K12-MICROFILE-V0.2", 2, 360000, 320000, 320000), "Microfile K12 seed drift")
    require("one stable processing intent" in stream_seed["canonicalExpected"] and "two stable processing intents" in micro_seed["canonicalExpected"], "K12 canonical result drift")
    require(campaign["replacement"]["automaticReplacementAllowed"] is False and campaign["replacement"]["candidateFailureReplaceable"] is False, "Hard-kill replacement rule weakened")

    faults = protocol["faultCampaign"]
    expected_fault_ids = {
        "COR-01", "COR-02", "COR-03", "COR-04", "COR-05", "COR-06",
        "TRU-01", "TRU-02", "TRU-03",
        "KEY-01", "KEY-02", "KEY-03", "KEY-04", "KEY-05",
        "SPL-01", "SPL-02", "SPL-03", "SPL-04", "SPL-05",
        "RBK-01", "RBK-02", "PAR-01",
        "QUA-01", "QUA-02", "QUA-03",
        "IDE-01", "IDE-02", "EVT-01",
        "CLN-01", "CLN-02", "CLN-03",
    }
    require({case["id"] for case in faults["cases"]} == expected_fault_ids and len(faults["cases"]) == 31, "Fault matrix ID drift")
    require(all(case["phaseARepetitionProfile"] == "PHASE_A_STANDARD" for case in faults["cases"]), "Every fault requires the standard Phase A repetition profile")
    require(faults["repetitionProfiles"]["PHASE_A_STANDARD"] == {"PINNED_API36_X86_64_EMULATOR": 3, "PHYSICAL_D2": 1}, "Phase A fault repetitions drift")
    rollback = protocol["rollbackModel"]
    require(rollback["externalPhaseAControllerLedgerIsRollbackAnchor"] is True and rollback["globalCryptographicAntiRollbackClaimAllowedWithoutExternalLedger"] is False, "Rollback claim boundary drift")


def validate_evidence() -> None:
    inventory = read_json(EVIDENCE / "dependency-inventory.json")
    license_notice = read_json(EVIDENCE / "license-notice-inventory.json")
    security = read_json(EVIDENCE / "security-advisory-inventory.json")
    sqlite = read_json(EVIDENCE / "sqlite-platform-provenance.json")
    roles = read_json(EVIDENCE / "review-roles.json")
    readiness = read_json(EVIDENCE / "readiness.json")

    require(inventory["rootCoordinate"] == "com.google.crypto.tink:tink-android:1.23.0", "Inventory root drift")
    require(inventory["dependencyAdmission"] is False and inventory["runtimeGraphModified"] is False, "Dependency/runtime graph was modified")
    require(inventory["resolution"]["externalCoordinateCountIncludingRoot"] == 8 and len(inventory["artifacts"]) == 8, "Tink closure count drift")
    require(inventory["resolution"]["projectGradleResolvedGraph"] is False and inventory["resolution"]["futureExactResolutionRequired"] is True, "Future Gradle graph boundary drift")
    require(all(artifact["jar"]["nativeEntries"] == 0 for artifact in inventory["artifacts"]), "Native payload found")
    root = next(artifact for artifact in inventory["artifacts"] if artifact["coordinate"] == inventory["rootCoordinate"])
    require(root["jar"]["sha256"] == "c656918451b01c45ce5b20c7b6d4c388f956f61b3a3528e769048c8944c42f9e", "Tink JAR digest drift")
    require(root["pom"]["sha256"] == "a2d27e7207e6a25764859b62924fc7b972f41884ce272cead9b946c15a1f410f", "Tink POM digest drift")
    require(inventory["sourceRelease"]["commit"] == "1bedd75ae7161017c5f45b020395a72bbd40645d", "Tink source commit drift")

    require(license_notice["reviewStatus"] == "EVIDENCE_COMPLETE_PACKAGE_REVIEW_PENDING", "License package state drift")
    require(license_notice["evaluationApproved"] is False and license_notice["redistributionApproved"] is False and license_notice["productionLegalApproved"] is False, "License/Product-IP approval was prematurely recorded")
    require(license_notice["approvedReviewer"] is None and license_notice["approvedOn"] is None, "License/Product-IP approval identity/date must remain null")
    require(license_notice["summary"]["externalCoordinates"] == 8 and len(license_notice["components"]) == 9, "License component count drift")
    require(len(security["exactVersionQueries"]) == 8, "Security exact-version query count drift")
    require(all(not query["affectedPublishedAdvisories"] for query in security["exactVersionQueries"]), "Recorded exact version has a published affected advisory")
    require(security["status"] == "SNAPSHOT_COMPLETE_INDEPENDENT_REVIEW_PENDING", "Security inventory review state drift")
    risk_states = {item["id"]: item["mitigationState"] for item in security["templateAndProtocolRisks"]}
    require(set(risk_states) == {"SEC-REC-01", "SEC-REC-02", "SEC-REC-03", "SEC-REC-04"}, "Security protocol-risk set drift")
    require(all("V0_2_" in state and "REQUIRED" in state for state in risk_states.values()), "Security risk remediation state must target v0.2 without claiming implementation approval")

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
    require(role_map["stage0ProductIp"]["status"] == "ASSIGNED_FINAL_APPROVAL_PENDING" and role_map["stage0ProductIp"]["finalApproved"] is False, "Product/IP final review state drift")
    require(role_map["stage0ProductIp"]["approvedReviewer"] is None and role_map["stage0ProductIp"]["approvedOn"] is None, "Product/IP approval identity/date must remain null")
    independent = role_map["independentRecoveryEngineeringSecurity"]
    require(independent["reviewer"] is None and independent["status"] == "UNASSIGNED_BLOCKING", "Accountable independent review blocker drift")
    require(independent["currentCodexRemediationClaimedFormallyIndependent"] is False and independent["replacesProductionSecurity"] is False, "Review independence boundary drift")
    require(role_map["executionAuthorizer"]["status"] == "AUTHORIZATION_WITHHELD", "Execution authorization must remain withheld")
    require(role_map["productionLegal"]["reviewer"] is None and role_map["productionSecurity"]["reviewer"] is None, "Production reviewers must remain unassigned")

    require(readiness["schemaVersion"] == 2 and readiness["status"] == "BLOCKED_REMEDIATED_PROTOCOL_V0_2_REVIEW_AND_IMPLEMENTATION_PENDING", "Readiness status drift")
    require(readiness["executionAllowed"] is False, "Readiness unexpectedly allows execution")
    for key in (
        "implementationAllowedByThisPackage", "measuredExecutionAllowed", "runtimeDependencyAdded",
        "recoveryModuleExists", "harnessImplemented", "killCampaignExecuted",
        "deviceTestsExecuted", "benchmarksExecuted", "productionAppChanged",
    ):
        require(readiness[key] is False, f"Governance-only invariant violated: {key}")
    require(readiness["packageArtifacts"]["activeGateSetVersion"] == GATE_V02 and readiness["packageArtifacts"]["activeProtocolId"] == PROTOCOL_V02, "Readiness active protocol locator drift")
    require(readiness["packageArtifacts"]["v01RetainedAsSupersededAuditArtifact"] is True and readiness["packageArtifacts"]["v01Executable"] is False, "v0.1 audit disposition drift")
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
    }
    require({blocker["id"] for blocker in readiness["blockers"]} == expected_blockers, "Readiness blocker set drift")


def validate_documents_and_no_implementation() -> None:
    owner_record = read_text(STAGE0 / "DORA_MVP1_POC_RECOVERY_OWNER_DECISION_OD14.md")
    decision = read_text(STAGE0 / "DEC-044-POC-RECOVERY-EXPERIMENT.md")
    gate_markdown = read_text(STAGE0 / "DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_2.md")
    remediation = read_text(EVIDENCE / "governance-remediation-v0.2.md")
    review = read_text(EVIDENCE / "ip-stage0-evaluation-review.md")
    require("Decision ID: `OD-14`" in owner_record and "executionAllowed=false" in owner_record, "OD-14 v0.2 boundary missing")
    require("Status: **Proposed" in decision and "executionAllowed=false" in decision, "DEC-044 must remain Proposed and non-executable")
    for required in (
        "DURABLE_ONE_SEGMENT_LOOKAHEAD", "DORA_RECOVERY_MANIFEST_V1_BINARY_BE",
        "DORASA01", "DORAMA01", "DORACP01", "DORAKE01",
        "0 <= C <= R <= A", "K12-STREAM-V0.2", "K12-MICROFILE-V0.2",
    ):
        require(required in gate_markdown, f"Recovery Gate Set v0.2 is missing: {required}")
    require(REVIEWED_HEAD in remediation and "executionAllowed=false" in remediation, "Remediation traceability/boundary missing")
    require("OWNER-REMEDIATED PROTOCOL v0.2 RE-REVIEW PENDING" in review, "Product/IP final review state drift")

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
    require("protocol: poc-recovery-protocol-stage0-v0.2" in device_matrix, "Recovery device contract must target v0.2")
    require("execution_allowed: false" in device_matrix, "Device contract must withhold execution")
    backlog = read_text(ROOT / "docs" / "DORA_MVP1_IMPLEMENTATION_BACKLOG.md")
    recovery_row = next(line for line in backlog.splitlines() if line.startswith("| POC-RECOVERY-001 |"))
    require("| BLOCKED |" in recovery_row and "executionAllowed=false" in recovery_row, "Recovery backlog must remain BLOCKED")


def main() -> int:
    gate = read_json(STAGE0 / "poc-recovery-gate-set-stage0-v0.2.json")
    protocol = read_json(STAGE0 / "poc-recovery-protocol-stage0-v0.2.json")
    validate_supersession(gate, protocol)
    validate_gate(gate)
    validate_protocol(protocol)
    validate_evidence()
    validate_documents_and_no_implementation()
    print("POC-RECOVERY-001 governance v0.2 validation passed; v0.1 retained; executionAllowed=false")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, StopIteration, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
