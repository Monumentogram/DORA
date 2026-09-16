#!/usr/bin/env python3
"""Protocol-derived private Recovery campaign planning and evidence evaluation.

The device adapter is a payload for an already verified task-owned emulator/ADB
session. This module never starts/adopts/stops an ADB server or emulator. Plans
are not execution authorization, and device observations are not human approval.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import importlib.util
import json
import os
import queue
import re
import runpy
import subprocess
import sys
import threading
import time
import uuid
from collections import Counter
from datetime import datetime, timezone
from functools import lru_cache
from pathlib import Path
from typing import Any

SCHEMA = "DORA_RECOVERY_CAMPAIGN_PLAN_V1"
REQUEST_SCHEMA = "DORA_RECOVERY_CAMPAIGN_REQUEST_V1"
PROTOCOL = "poc-recovery-protocol-stage0-v0.8"
SELECTOR = "com.monumentogram.dora.poc.recovery.candidate.RecoveryCampaignInstrumentedTest#execute"
PACKAGE = "com.monumentogram.dora.poc.recovery"
RUNNER = PACKAGE + ".test/androidx.test.runner.AndroidJUnitRunner"
MAX_BYTES = 115_200_000
INVALIDATORS = {
    "WRONG_PID", "STRATUM_BARRIER_NOT_REACHED",
    "CONTROLLER_DISCONNECTED_BEFORE_SIGNAL_CONFIRMATION",
    "DEVICE_REBOOT_OR_POWER_LOSS_OUTSIDE_ASSIGNED_FAULT",
    "PREFLIGHT_OR_PROTOCOL_IDENTITY_MISMATCH", "FIXTURE_DRIFT", "OPERATOR_INTERVENTION",
    "EXTERNAL_CONTROLLER_OR_PREFLIGHT_EVIDENCE_ENVELOPE_INCOMPLETE_OR_SCHEMA_INVALID",
}
KILL_PROOFS = (
    "scheduledBeforeExecution", "sourceAndPreflightMatch", "targetAliveAtPublicBarrier",
    "externalSigkillConfirmed", "deathConfirmedBeforeRecovery", "noGracefulFinalize",
    "externalEnvelopeComplete",
)
CANDIDATES = ("REC-STREAM-TINK", "REC-MICROFILE-TINK")
ALPHA_PREFLIGHT_PAYLOADS = frozenset({"SUPPLEMENTAL_SQLITE", "JOURNAL_CONNECTIONS", "PLATFORM_PREREQUISITES"})
ALPHA_DECISION_PATH = "docs/stage0/DORA_0D6_ALPHA_PREFLIGHT_OWNER_DECISION_20260914.md"
ALPHA_DECISION_SHA256 = "06dede52bd599ae12903dd9f336774c804bee97724cede208e4e64b9b5f64dde"
ALPHA_CAMPAIGN_DECISION_PATH = "docs/stage0/DORA_0D6_ALPHA_E36_CAMPAIGN_OWNER_DECISION_20260914.md"
ALPHA_CAMPAIGN_DECISION_SHA256 = "bce9a5eba35fba937ad589a0166a8ca1166fbb89e0b71c24407d2fed1601f96d"
ALPHA_PREFLIGHT_SOURCE = {
    "commit": "d3bc6aca800ac0dedd82124aabd8d25eff50c262",
    "tree": "1f4575a98ce02ecd01ae0cceaa1373e4f5ee41a9",
    "appApkSha256": "8b1f79aec975c02021c7f58f5218da91f9e9585dbe8bbbd0844647c5b0c1d2de",
    "testApkSha256": "effd7e29c7dc7a4d8adc7a7057b1d90f5a58340b11c8755aff26cf71c39e0dfe",
}
ALPHA_PREFLIGHT_RESULTS_SHA256 = "30eb1ec31e5a4d466d3ade60516ac5b2b2cb9ed63bba81f748191c1e6bc66c08"
ALPHA_PREFLIGHT_HANDOFF_SHA256 = "d54a7e954cc6180736258d8cab34dee97e0dc158a4ca1ee06c4df27b1aa8a5eb"
ALPHA_PREFLIGHT_EQUIVALENCE_SHA256 = "497e2f46b9b4853b12e0fb473c5f2e2f57ee3dbab4583f392db8096b59ac11e4"
ALPHA_PREFLIGHT_FULL_PLAN_RELATIVE_PATH = "execution-packet-d3bc6ac-v5/phase_a-d3bc6ac.json"
ALPHA_PREFLIGHT_FULL_PLAN_SHA256 = "a365d571394e76a7e29bc728a9013bcc58260a2953ef023188bdcc7ae5400876"
E36_FINGERPRINT = "google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys"
ALPHA_REDUCED_DECISION_ID = "DORA_0D6_ALPHA_REDUCED_SCOPE_20260915"
ALPHA_REDUCED_SCOPE = "INTERNAL_ALPHA_E36_REDUCED_114"
ALPHA_REDUCED_DECISION_PATH = "docs/stage0/DORA_0D6_ALPHA_REDUCED_SCOPE_OWNER_DECISION_20260915.md"
ALPHA_REDUCED_DECISION_SHA256 = "39d340cc6fe55ce8d467ccca7b13607127a7e7c4fdc9c5f00a010055e92a2091"
REDUCED_FAULT_CASES = (
    "COR-01", "COR-02", "COR-03", "COR-04", "COR-05", "COR-06",
    "TRU-01", "TRU-02", "TRU-03", "KEY-01", "KEY-02", "KEY-03",
    "KEY-04", "KEY-05", "KEY-06", "KEY-07", "SPL-01", "SPL-02",
    "SPL-03", "SPL-04", "SPL-05", "RBK-01", "RBK-02", "PAR-01",
    "QUA-01", "QUA-02", "QUA-03", "IDE-01", "IDE-02", "EVT-01",
    "CLN-01", "CLN-02", "CLN-03", "KCB-01", "KCB-02", "KCB-03",
    "KCB-04", "KCB-05", "KCB-06", "KCF-01", "KCF-02", "KCF-03",
    "KCF-04", "KCF-05", "KCF-06", "KCF-07",
)
REDUCED_HARD_KILL_STRATA = tuple(f"K{number:02d}" for number in range(1, 13))
REDUCED_MICRO_K08_BASE_ATTEMPT_ID = "PA-MICROFILE-K08-E36-GAPI-05"


def accepted_alpha_source() -> dict[str, str]:
    # Load the exact sibling even under Python -I; no ambient import path.
    validator = Path(__file__).resolve().with_name("validate_recovery_0d6_candidate.py")
    api = runpy.run_path(str(validator))
    require("ALPHA_REDUCED_REPAIR_BINDING" not in api and "ALPHA_PREFIX_REPAIR_BINDING" not in api,
            "Repaired source profile is restricted to reduced sourceRepair admission")
    return api["alpha_preflight_source"]()


def validate_alpha_prefix_preflight(plan: dict[str, Any], gate: dict[str, Any]) -> None:
    validator = runpy.run_path(str(Path(__file__).with_name("recovery_alpha_prefix_repair.py")))
    validator["validate_preflight"](plan, gate)


def validate_alpha_preflight(plan: dict[str, Any], gate: dict[str, Any], payload: str) -> None:
    """Integrity and scope of the owner's specific amendment, never human approval."""
    decision = gate.get("alphaPreflight")
    require(isinstance(decision, dict), "Alpha preflight decision binding missing")
    require(decision.get("decisionId") == "DORA_0D6_ALPHA_PREFLIGHT_20260914"
            and decision.get("scope") == "INTERNAL_ALPHA_E36_PREFLIGHT_ONLY", "Wrong alpha decision scope")
    require(decision.get("source") == plan["source"], "Alpha decision source/APK mismatch")
    if "sourceRepair" in decision:
        validate_alpha_prefix_preflight(plan, gate)
    else:
        require(plan["source"] == accepted_alpha_source(), "Alpha source/APKs are not the exact accepted successor")
    require(payload in ALPHA_PREFLIGHT_PAYLOADS, "Alpha decision cannot admit CAMPAIGN or another payload")
    allowed = gate.get("supportedPayloads")
    require(isinstance(allowed, list) and len(allowed) == 3 and set(allowed) == ALPHA_PREFLIGHT_PAYLOADS,
            "Alpha payload scope drift")
    require(gate.get("supportedAttemptIds") == [], "Alpha decision cannot admit campaign attempts")
    require(gate.get("environment") == "E36-GAPI" and gate.get("deviceFingerprint") ==
            "google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys",
            "Alpha decision requires exact E36 profile; physical profiles excluded")
    require(gate.get("physicalAuthorization", {}).get("authorized") is False, "Alpha physical scope forbidden")
    accountable = gate.get("accountableReview", {})
    require(gate.get("accountableReviewApproved") is False and accountable.get("formalReviewer") is False
            and accountable.get("reviewer") is None and accountable.get("reviewedCommit") is None,
            "Alpha decision must not fabricate accountable review")
    proof = gate.get("proofs", {}).get("ownerDecision", {})
    require(isinstance(proof.get("path"), str), "Missing fixed owner decision artifact")
    path = Path(proof["path"])
    expected = Path(__file__).resolve().parents[1] / ALPHA_DECISION_PATH
    require(path.resolve() == expected.resolve() and path.is_file()
            and proof.get("sha256") == ALPHA_DECISION_SHA256
            and hashlib.sha256(path.read_bytes()).hexdigest() == ALPHA_DECISION_SHA256,
            "Owner decision artifact is not the pinned repository decision")


def _alpha_preflight_root() -> Path:
    return Path(__file__).resolve().parents[3] / "0D6-CLOSURE-20260914" / "alpha-preflight-20260914"


def _verify_alpha_descriptor(root: Path, descriptor: Any, relative: str, expected_sha256: str) -> Path:
    require(isinstance(descriptor, dict) and descriptor.get("relativePath") == relative
            and descriptor.get("sha256") == expected_sha256, "Retained preflight descriptor mismatch " + relative)
    path = root / relative
    require(path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == expected_sha256,
            "Retained preflight artifact hash mismatch " + relative)
    return path


def _verify_preflight_artifact(root: Path, descriptor: Any) -> None:
    require(isinstance(descriptor, dict) and set(descriptor) == {"bytes", "path", "sha256"}, "Malformed retained preflight artifact descriptor")
    relative = descriptor["path"].replace("\\", "/")
    require(relative and not relative.startswith("/") and not any(part in ("", ".", "..") for part in relative.split("/")),
            "Unsafe retained preflight artifact path")
    path = root.joinpath(*relative.split("/"))
    require(path.is_file() and path.stat().st_size == descriptor["bytes"] and hashlib.sha256(path.read_bytes()).hexdigest() == descriptor["sha256"],
            "Retained preflight artifact hash mismatch " + relative)


def validate_retained_alpha_preflight(gate: dict[str, Any]) -> dict[str, Any]:
    """Verify the three immutable preflight assessments reused by alpha scopes."""
    retained = gate.get("retainedPreflight")
    root = _alpha_preflight_root()
    require(isinstance(retained, dict) and retained.get("root") == str(root.resolve()) and root.is_dir(),
            "Retained preflight root mismatch")
    handoff_path = _verify_alpha_descriptor(root, retained.get("handoff"), "CAMPAIGN-HANDOFF.json", ALPHA_PREFLIGHT_HANDOFF_SHA256)
    results_path = _verify_alpha_descriptor(root, retained.get("results"), "preflight-results.json", ALPHA_PREFLIGHT_RESULTS_SHA256)
    _verify_alpha_descriptor(root, retained.get("sourceBuildEquivalence"), "raw/source-build-equivalence.json", ALPHA_PREFLIGHT_EQUIVALENCE_SHA256)
    handoff = json.loads(handoff_path.read_bytes())
    results = json.loads(results_path.read_bytes())
    require(handoff.get("source") == results.get("source") == ALPHA_PREFLIGHT_SOURCE and handoff.get("preflightPassed") is True
            and handoff.get("faults") == {"planned": 270, "executed": 0} and handoff.get("hardKills") == {"planned": 144, "executed": 0},
            "Retained preflight handoff contents mismatch")
    expected_attempts = {
        "D3BC6AC-SUPPLEMENTAL_SQLITE-03": "SUPPLEMENTAL_SQLITE",
        "D3BC6AC-JOURNAL_CONNECTIONS-03": "JOURNAL_CONNECTIONS",
        "D3BC6AC-PLATFORM_PREREQUISITES-04": "PLATFORM_PREREQUISITES",
    }
    attempts = results.get("attempts")
    require(results.get("schema") == "DORA_0D6_ALPHA_PREFLIGHT_OBSERVED_RESULTS_V1" and results.get("source") == ALPHA_PREFLIGHT_SOURCE
            and results.get("aggregatePreflightPassed") is True and results.get("campaignAuthorized") is False
            and isinstance(attempts, list) and len(attempts) == 3, "Retained preflight assessments mismatch")
    require(handoff.get("preflightResults") == {"path": "preflight-results.json", "sha256": ALPHA_PREFLIGHT_RESULTS_SHA256},
            "Retained preflight handoff result binding mismatch")
    for descriptor in results.get("admissionPackets", []) + [results.get("packet")]:
        _verify_preflight_artifact(root, descriptor)
    seen = set()
    for attempt in attempts:
        identity = attempt.get("attemptId")
        require(identity in expected_attempts and identity not in seen and attempt.get("payload") == expected_attempts[identity]
                and attempt.get("status") == "PASS" and attempt.get("cleanupResult") == "VERIFIED"
                and attempt.get("errors") == attempt.get("failures") == attempt.get("skips") == 0
                and attempt.get("executedTests") == attempt.get("successfulTests") == 1
                and attempt.get("instrumentationNativeExit") == attempt.get("launcherNativeExit") == attempt.get("adbRootExit") == 0,
                "Retained preflight assessment invalid")
        seen.add(identity)
        for descriptor in attempt.get("evidence", []) + attempt.get("environmentReceipts", []) + [attempt.get("shutdownResolution"), attempt.get("packet")]:
            _verify_preflight_artifact(root, descriptor)
    require(seen == set(expected_attempts), "Retained preflight assessment coverage mismatch")
    return handoff


def validate_alpha_source_equivalence(plan: dict[str, Any], decision: dict[str, Any]) -> None:
    equivalence = decision.get("sourceEquivalence", {})
    require(equivalence.get("historicalSource") == ALPHA_PREFLIGHT_SOURCE
            and equivalence.get("successorSource") == plan["source"] and equivalence.get("apkPairUnchanged") is True,
            "Campaign source equivalence binding mismatch")
    successor = equivalence["successorSource"]
    require(successor["appApkSha256"] == ALPHA_PREFLIGHT_SOURCE["appApkSha256"]
            and successor["testApkSha256"] == ALPHA_PREFLIGHT_SOURCE["testApkSha256"], "Campaign APK equivalence mismatch")
    equivalence_proof = equivalence.get("proof", {})
    require(isinstance(equivalence_proof.get("path"), str) and Path(equivalence_proof["path"]).is_file()
            and hashlib.sha256(Path(equivalence_proof["path"]).read_bytes()).hexdigest() == equivalence_proof.get("sha256"),
            "Campaign source equivalence proof mismatch")
    equivalence_data = json.loads(Path(equivalence_proof["path"]).read_bytes())
    require(equivalence_data.get("historicalSource") == ALPHA_PREFLIGHT_SOURCE and equivalence_data.get("successorSource") == plan["source"]
            and equivalence_data.get("apkPairUnchanged") is True and equivalence_data.get("buildInputsUnchanged") is True
            and equivalence_data.get("androidTreeBefore") == equivalence_data.get("androidTreeAfter")
            and isinstance(equivalence_data.get("changedPaths"), list), "Campaign source equivalence contents mismatch")
    hex_value(equivalence_data["androidTreeBefore"], 40)


def validate_alpha_campaign(plan: dict[str, Any], gate: dict[str, Any]) -> None:
    """Verify the separate E36 campaign decision and immutable preflight history."""
    decision = gate.get("alphaCampaign")
    require(isinstance(decision, dict) and decision.get("decisionId") == "DORA_0D6_ALPHA_E36_CAMPAIGN_20260914"
            and decision.get("scope") == "INTERNAL_ALPHA_E36_CAMPAIGN", "Wrong alpha campaign decision scope")
    require(decision.get("source") == plan["source"] == accepted_alpha_source(), "Alpha campaign source/APKs are not the exact accepted successor")
    require(decision.get("historicalSource") == ALPHA_PREFLIGHT_SOURCE, "Historical preflight source mismatch")
    require(gate.get("supportedPayloads") == ["CAMPAIGN"], "Alpha campaign payload scope drift")
    require(gate.get("environment") == "E36-GAPI" and gate.get("deviceFingerprint") == E36_FINGERPRINT,
            "Alpha campaign requires exact E36 profile")
    require(gate.get("physicalAuthorization", {}).get("authorized") is False, "Alpha campaign physical scope forbidden")
    accountable = gate.get("accountableReview", {})
    require(gate.get("accountableReviewApproved") is False and accountable.get("formalReviewer") is False
            and accountable.get("reviewer") is None and accountable.get("reviewedCommit") is None,
            "Alpha campaign must not fabricate accountable review")
    proof = gate.get("proofs", {}).get("ownerDecision", {})
    expected_decision = Path(__file__).resolve().parents[1] / ALPHA_CAMPAIGN_DECISION_PATH
    require(isinstance(proof.get("path"), str) and Path(proof["path"]).resolve() == expected_decision.resolve()
            and expected_decision.is_file() and proof.get("sha256") == ALPHA_CAMPAIGN_DECISION_SHA256
            and hashlib.sha256(expected_decision.read_bytes()).hexdigest() == ALPHA_CAMPAIGN_DECISION_SHA256,
            "Owner decision artifact is not the pinned campaign decision")

    handoff = validate_retained_alpha_preflight(gate)
    validate_alpha_source_equivalence(plan, decision)

    require(plan.get("phase") == "PHASE_A" and isinstance(plan.get("executionId"), str), "Campaign namespace missing")
    execution_id = safe_id(plan["executionId"])
    entries = plan.get("entries", [])
    selected = [entry for entry in entries if entry.get("environment") == "E36-GAPI"]
    require(len(selected) == 414 and sum(entry.get("kind") == "FAULT" for entry in selected) == 270
            and sum(entry.get("kind") == "HARD_KILL" for entry in selected) == 144, "Campaign E36 population mismatch")
    original_entries = handoff.get("entries")
    normalized_entries = []
    for entry in selected:
        base = entry.get("baseAttemptId")
        legacy_run_id = hashlib.sha256(f"{plan['scheduleSeed']}:{base}".encode()).digest()[:16].hex()
        normalized = {key: value for key, value in entry.items() if key not in ("attemptId", "baseAttemptId", "runId")}
        normalized.update(attemptId=base, runId=legacy_run_id)
        normalized_entries.append(normalized)
    require(original_entries == normalized_entries, "Campaign entries differ from the retained original E36 schedule")
    attempt_ids = [entry.get("attemptId") for entry in selected]
    base_ids = [entry.get("baseAttemptId") for entry in selected]
    require(len(attempt_ids) == len(set(attempt_ids)) == len(base_ids) == len(set(base_ids)) == 414
            and gate.get("supportedAttemptIds") == attempt_ids,
            "Campaign supported attempt scope mismatch")
    all_base_ids = {entry.get("baseAttemptId") for entry in entries}
    for entry in entries:
        base = entry.get("baseAttemptId")
        legacy_run_id = hashlib.sha256(f"{plan['scheduleSeed']}:{base}".encode()).digest()[:16].hex()
        fresh_run_id = hashlib.sha256(f"{execution_id}:{base}:{legacy_run_id}".encode()).hexdigest()[:32]
        require(isinstance(base, str) and entry.get("attemptId") == execution_id + "-" + base
                and entry["attemptId"] not in all_base_ids and entry.get("runId") == fresh_run_id
                and entry["runId"] != legacy_run_id, "Campaign attempt identity is not fresh")



def require(condition: bool, reason: str) -> None:
    if not condition:
        raise ValueError(reason)


def canonical(value: Any) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode("ascii")


def digest_json(value: Any) -> str:
    return hashlib.sha256(canonical(value)).hexdigest()


def hex_value(value: str, length: int) -> str:
    require(isinstance(value, str) and re.fullmatch("[0-9a-f]{%d}" % length, value) is not None, "Invalid hexadecimal identity")
    return value


def safe_id(value: str) -> str:
    require(isinstance(value, str) and re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_-]{0,159}", value) is not None, "Unsafe attempt identity")
    return value


@lru_cache(maxsize=256)
def _fixture_period(seed_low_byte: int) -> bytes:
    return bytes((seed_low_byte + i * 31 + (i >> 8) * 17) & 255 for i in range(65536))


def fixture_bytes(seed: int, length: int, start: int = 0) -> bytes:
    require(type(seed) is int and 0 <= seed <= 0x7fffffff, "Invalid fixture seed")
    require(type(length) is int and type(start) is int and 0 <= start <= MAX_BYTES and 0 <= length <= MAX_BYTES - start, "Invalid fixture extent")
    # The exact byte formula repeats after 65536 bytes. Constructing one period
    # keeps the independent host oracle bounded even for maximum-length runs.
    period = _fixture_period(seed & 255)
    offset = start % 65536
    count = (offset + length + 65535) // 65536
    return (period * count)[offset:offset + length]


def fixture_digest(seed: int, length: int) -> str:
    require(type(seed) is int and 0 <= seed <= 0x7fffffff, "Invalid fixture seed")
    require(type(length) is int and 0 <= length <= MAX_BYTES, "Invalid fixture extent")
    return _fixture_digest(seed & 255, length)


@lru_cache(maxsize=1024)
def _fixture_digest(seed: int, length: int) -> str:
    digest = hashlib.sha256()
    for offset in range(0, length, 65536):
        digest.update(fixture_bytes(seed, min(65536, length - offset), offset))
    return digest.hexdigest()


def load_contract(root: Path) -> tuple[dict[int, Any], dict[str, str]]:
    contracts: dict[int, Any] = {}
    pins = {}
    for version in (3, 4, 5, 6, 7, 8):
        relative = f"docs/stage0/poc-recovery-protocol-stage0-v0.{version}.json"
        data = (root / relative).read_bytes()
        contracts[version] = json.loads(data)
        pins[relative] = hashlib.sha256(data).hexdigest()
    rows = contracts[6]["faultCampaign"]["activeEffectiveFaultMatrixV06"]["rows"]
    require(len(rows) == len({r["id"] for r in rows}) == 46, "Fault matrix is not 46 unique rows")
    require(sum(r["id"] == "KEY-04" for r in rows) == 1, "Duplicate effective KEY-04")
    require(contracts[8]["unchangedV07"]["campaignCounts"] == {
        "mandatoryFaultRowCount": 46, "phaseAInjectionCount": 184,
        "fullPhysicalInjectionCount": 138, "baseHardKillAttemptsPerCandidate": 120,
    }, "Unsupported changed campaign counts")
    require(contracts[3]["hardKillCampaign"]["baseAttemptsPerCandidate"] == 120, "Unsupported kill contract")
    return contracts, pins


def build_plan(root: Path, phase: str, source: dict[str, str], seed: int, execution_id: str | None = None) -> dict[str, Any]:
    require(phase in ("PHASE_A", "FULL_PHYSICAL"), "Unknown campaign phase")
    require(set(source) == {"commit", "tree", "appApkSha256", "testApkSha256"}, "Incomplete source binding")
    for key, value in source.items():
        hex_value(value, 40 if key in ("commit", "tree") else 64)
    require(type(seed) is int and 0 <= seed <= 0x7fffffff, "Invalid schedule seed")
    protocols, pins = load_contract(root)
    p3, p6, p8 = protocols[3], protocols[6], protocols[8]
    original = {r["id"]: r for r in p3["faultCampaign"]["cases"]}
    added = {r["id"]: r for r in protocols[4]["faultCampaign"]["addedCases"]}
    overrides = {r["id"]: r for r in p6["faultCampaign"]["inheritedV04CaseOracleOverrides"]}
    entries = []
    prefix = "PA" if phase == "PHASE_A" else "FP"

    def entry(candidate: str, kind: str, case: str, environment: str, slot: int) -> dict[str, Any]:
        short = "STREAM" if candidate == CANDIDATES[0] else "MICROFILE"
        identity = f"{prefix}-{short}-{case}-{environment}-{slot:02d}"
        derived = hashlib.sha256(f"{seed}:{identity}".encode()).digest()
        item = {
            "attemptId": identity, "candidateId": candidate, "kind": kind,
            "environment": environment, "slot": slot,
            "runId": derived[:16].hex(), "seed": int.from_bytes(derived[16:20], "big") & 0x7fffffff,
            "plaintextBytes": 480000, "mutationVariants": ["DEFAULT"],
            "expectedClassifications": [],
        }
        item["caseId" if kind == "FAULT" else "stratumId"] = case
        return item

    for candidate in CANDIDATES:
        for index, row in enumerate(p6["faultCampaign"]["activeEffectiveFaultMatrixV06"]["rows"]):
            case = row["id"]
            base = original.get(case, added.get(case, row))
            if base.get("appliesTo", "BOTH") not in ("BOTH", candidate):
                continue
            expected = overrides.get(case, base).get("expected", row.get("expectedClassification", ""))
            if case == "KEY-04":
                expected = row["expectedClassification"]
            environments = [("E36-GAPI", 3), ("D2", 1)] if phase == "PHASE_A" else [("D1", 1), ("D2", 1), ("D5", 1)]
            for environment, repetitions in environments:
                for slot in range(1, repetitions + 1):
                    item = entry(candidate, "FAULT", case, environment, slot)
                    item["sourcePointer"] = f"docs/stage0/poc-recovery-protocol-stage0-v0.6.json#/faultCampaign/activeEffectiveFaultMatrixV06/rows/{index}"
                    item["injection"] = base.get("injection", "See effective immutable protocol row")
                    item["expectedOracle"] = expected
                    if case.startswith(("KEY-", "KCF-", "KCB-")):
                        match = re.match(r"^[A-Z][A-Z0-9_]+", expected)
                        if match:
                            item["expectedClassifications"] = [match.group()]
                    if candidate == CANDIDATES[0] and case == "TRU-03":
                        item["mutationVariants"] = [f"APPEND_{size}" for size in p8["streamingPersistenceV07"]["tru03"]["appendBytes"]]
                        item["expectedOracle"] = "Exact v0.7 stream range matrix; unchanged source; zero streaming processing intents"
                    if case == "KCF-07":
                        item["mutationVariants"] = ["MALFORMED_PLAINTEXT", "WRONG_MAGIC", "WRONG_SCHEMA", "WRONG_PROTOCOL_ID", "WRONG_CANDIDATE_ID", "WRONG_RUN_ID", "WRONG_CANONICAL_ALIAS_SHA256"]
                    if case == "KCF-06":
                        item["mutationVariants"] = ["TEMP_ONLY", "FINAL_ONLY", "TEMP_AND_FINAL"]
                    if case == "QUA-03":
                        item["mutationVariants"] = ["Q01", "Q02", "Q03", "Q04", "Q05"]
                    if case == "COR-03":
                        item["mutationVariants"] = ["FILENAME", "LENGTH", "DIGEST", "RANGE"]
                    if case == "COR-05":
                        item["mutationVariants"] = ["DUPLICATE_ENTRY", "GAP_ENTRY", "REORDER_ENTRIES", "REMOVE_ENTRY"]
                    if case in ("COR-06", "KEY-06"):
                        item["mutationVariants"] = ["CROSS_GENERATION"]
                    if case == "KEY-05":
                        item["mutationVariants"] = ["WRONG_LENGTH", "WRONG_DIGEST", "INVALID_BINARY", "PARSER_INVALID"]
                    if case == "PAR-01":
                        item["mutationVariants"] = ["MALFORMED", "OVERSIZED", "TRAILING_BYTE", "UNSAFE_PATH", "TRAVERSAL_PATH", "SYMLINK"]
                    entries.append(item)
        for stratum in p3["hardKillCampaign"]["strata"]:
            for slot in range(1, 11):
                environment = ("E36-GAPI" if slot <= 6 else "D2") if phase == "PHASE_A" else ("D1" if slot <= 4 else "D2" if slot <= 7 else "D5")
                item = entry(candidate, "HARD_KILL", stratum["id"], environment, slot)
                item["publicBarrier"] = stratum[candidate]
                item["sourcePointer"] = "docs/stage0/poc-recovery-protocol-stage0-v0.3.json#/hardKillCampaign/strata"
                if stratum["id"] == "K12":
                    if candidate == CANDIDATES[0]:
                        k12 = p8["streamingPersistenceV07"]["k12"]
                        item.update(seedId=k12["seedId"], plaintextBytes=k12["A"], publicBarrier=k12["pause"], k12=k12)
                    else:
                        k12 = p3["hardKillCampaign"]["k12Seeds"][candidate]
                        item.update(seedId=k12["seedId"], plaintextBytes=k12["A"], k12=k12)
                entries.append(item)
    # Stable digest order provides reproducible randomization without a library-
    # dependent PRNG algorithm. Every slot remains allocated before execution.
    entries.sort(key=lambda item: hashlib.sha256(f"{seed}:{item['attemptId']}".encode()).digest())
    for item in entries:
        item["fixtureSha256"] = fixture_digest(item["seed"], item["plaintextBytes"])
    if execution_id is not None:
        execution_id = safe_id(execution_id)
        for item in entries:
            base_attempt_id, base_run_id = item["attemptId"], item["runId"]
            attempt_id = execution_id + "-" + base_attempt_id
            safe_id(attempt_id)
            item["baseAttemptId"] = base_attempt_id
            item["attemptId"] = attempt_id
            item["runId"] = hashlib.sha256(f"{execution_id}:{base_attempt_id}:{base_run_id}".encode()).hexdigest()[:32]
    plan = {"schema": SCHEMA, "phase": phase, "protocolId": PROTOCOL, "source": source,
            "scheduleSeed": seed, "fixtureAlgorithm": "BYTE_I_EQUALS_SEED_PLUS_31_I_PLUS_17_FLOOR_I_DIV_256_MOD_256",
            "protocolPins": pins, "canonicalCounts": p8["unchangedV07"]["campaignCounts"],
            "entries": entries, "automaticReplacementAllowed": False,
            "executionAuthorizedByPlan": False}
    if execution_id is not None:
        plan["executionId"] = execution_id
    return plan


def validate_plan(root: Path, plan: dict[str, Any]) -> None:
    expected = build_plan(root, plan["phase"], plan["source"], plan["scheduleSeed"], plan.get("executionId"))
    require(plan == expected, "Plan differs from immutable protocol-derived schedule or source pins")


def _base_attempt_id(entry: dict[str, Any]) -> str:
    base = entry.get("baseAttemptId", entry.get("attemptId"))
    return safe_id(base)


def _reduced_requirement_id(entry: dict[str, Any]) -> str:
    if entry.get("kind") == "FAULT":
        return "FAULT:%s:%s" % (entry.get("candidateId"), entry.get("caseId"))
    return "HARD_KILL:%s:%s" % (entry.get("candidateId"), entry.get("stratumId"))


def _reduced_entry_binding(original: dict[str, Any], execution: dict[str, Any]) -> dict[str, Any]:
    require(_base_attempt_id(original) == _base_attempt_id(execution), "Reduced source/execution base identity mismatch")
    original_fields = {key: value for key, value in original.items() if key not in ("attemptId", "baseAttemptId", "runId")}
    execution_fields = {key: value for key, value in execution.items() if key not in ("attemptId", "baseAttemptId", "runId")}
    require(original_fields == execution_fields, "Reduced execution recipe or fixture drift")
    return {
        "requirementId": _reduced_requirement_id(original),
        "originalBaseAttemptId": _base_attempt_id(original),
        "attemptId": execution["attemptId"],
        "runId": execution["runId"],
        "candidateId": original["candidateId"],
        "kind": original["kind"],
        "caseId" if original["kind"] == "FAULT" else "stratumId": original.get("caseId", original.get("stratumId")),
        "slot": original["slot"],
        "seed": original["seed"],
        "fixtureSha256": original["fixtureSha256"],
        "plaintextBytes": original["plaintextBytes"],
        "mutationVariants": original["mutationVariants"],
        "originalEntrySha256": digest_json(original),
        "executionEntrySha256": digest_json(execution),
    }


def build_reduced_e36_selection(original_plan: dict[str, Any], execution_plan: dict[str, Any],
                                completed_base_attempt_ids: tuple[str, ...] | list[str] = ()) -> dict[str, Any]:
    """Build the fixed 114-base E36 subset from its immutable 600-plan source."""
    require(original_plan.get("phase") == execution_plan.get("phase") == "PHASE_A", "Reduced scope requires PHASE_A plans")
    require("executionId" not in original_plan and isinstance(execution_plan.get("executionId"), str),
            "Reduced scope requires legacy source and fresh execution namespaces")
    require(original_plan.get("scheduleSeed") == execution_plan.get("scheduleSeed"),
            "Reduced source/execution schedule identity mismatch")
    originals = original_plan.get("entries")
    executions = execution_plan.get("entries")
    require(isinstance(originals, list) and isinstance(executions, list) and len(originals) == len(executions) == 600,
            "Reduced scope requires the complete canonical 600-plan")
    original_e36 = [entry for entry in originals if entry.get("environment") == "E36-GAPI"]
    require(len(original_e36) == 414, "Reduced source plan E36 population mismatch")
    original_by_base = {_base_attempt_id(entry): entry for entry in originals}
    execution_by_base = {_base_attempt_id(entry): entry for entry in executions}
    require(len(original_by_base) == len(originals) == len(execution_by_base)
            and set(original_by_base) == set(execution_by_base), "Reduced plan base mapping mismatch")
    completed = list(completed_base_attempt_ids)
    require(all(isinstance(value, str) for value in completed) and len(completed) == len(set(completed)),
            "Reduced completed base identities are malformed or duplicate")
    completed_set = set(completed)
    require(completed_set <= {_base_attempt_id(entry) for entry in original_e36}, "Reduced completed base identity is outside E36")

    selected: list[dict[str, Any]] = []
    for candidate in CANDIDATES:
        for case in REDUCED_FAULT_CASES:
            if candidate == CANDIDATES[0] and case in ("COR-04", "COR-05"):
                continue
            choices = [entry for entry in original_e36 if entry.get("kind") == "FAULT"
                       and entry.get("candidateId") == candidate and entry.get("caseId") == case]
            require(choices, "Reduced fault requirement missing from source plan")
            selected.append(next((entry for entry in choices if _base_attempt_id(entry) in completed_set), choices[0]))
        for stratum in REDUCED_HARD_KILL_STRATA:
            choices = [entry for entry in original_e36 if entry.get("kind") == "HARD_KILL"
                       and entry.get("candidateId") == candidate and entry.get("stratumId") == stratum]
            require(choices, "Reduced hard-kill requirement missing from source plan")
            if candidate == CANDIDATES[1] and stratum == "K08":
                selected.append(next(entry for entry in choices if _base_attempt_id(entry) == REDUCED_MICRO_K08_BASE_ATTEMPT_ID))
            else:
                selected.append(next((entry for entry in choices if _base_attempt_id(entry) in completed_set), choices[0]))
    order = {_base_attempt_id(entry): index for index, entry in enumerate(originals)}
    selected.sort(key=lambda entry: order[_base_attempt_id(entry)])
    selected_bases = [_base_attempt_id(entry) for entry in selected]
    require(len(selected_bases) == len(set(selected_bases)) == 114
            and sum(entry["kind"] == "FAULT" for entry in selected) == 90
            and sum(entry["kind"] == "HARD_KILL" for entry in selected) == 24
            and REDUCED_MICRO_K08_BASE_ATTEMPT_ID in selected_bases,
            "Reduced source selection population mismatch")
    bindings = [_reduced_entry_binding(entry, execution_by_base[_base_attempt_id(entry)]) for entry in selected]
    require(sum(len(binding["mutationVariants"]) for binding in bindings) == 165,
            "Reduced selection variant population mismatch")
    return {
        "schema": "DORA_RECOVERY_REDUCED_E36_SELECTION_V1",
        "scope": ALPHA_REDUCED_SCOPE,
        "originalPlanManifestSha256": digest_json(original_plan),
        "originalPlanSource": original_plan["source"],
        "originalScheduleSeed": original_plan["scheduleSeed"],
        "executionPlanManifestSha256": digest_json(execution_plan),
        "executionPlanSource": execution_plan["source"],
        "executionId": execution_plan["executionId"],
        "completedBaseAttemptIds": [
            _base_attempt_id(entry) for entry in original_e36 if _base_attempt_id(entry) in completed_set
        ],
        "selectedCompletedBaseAttemptIds": [base for base in selected_bases if base in completed_set],
        "originalBaseAttemptIds": selected_bases,
        "supportedAttemptIds": [binding["attemptId"] for binding in bindings],
        "faultBaseCount": 90,
        "hardKillBaseCount": 24,
        "variantCount": 165,
        "entries": bindings,
    }


def validate_reduced_e36_selection(original_plan: dict[str, Any], execution_plan: dict[str, Any], selection: dict[str, Any]) -> None:
    require(isinstance(selection, dict), "Reduced selection missing")
    expected = build_reduced_e36_selection(original_plan, execution_plan, selection.get("completedBaseAttemptIds", ()))
    require(selection == expected, "Reduced selection differs from immutable source mapping")


def validate_alpha_repair(plan: dict[str, Any], gate: dict[str, Any]) -> None:
    """Separate, exact repaired-APK applicability; never used for other scopes."""
    validator = runpy.run_path(str(Path(__file__).with_name("recovery_alpha_repair.py")))
    validator["validate"](plan, gate)


def validate_alpha_reduced(plan: dict[str, Any], gate: dict[str, Any]) -> None:
    """Admit only the separately authorized, source-bound E36 reduced subset."""
    require("alphaPreflight" not in gate and "alphaCampaign" not in gate,
            "Ambiguous simultaneous alpha admissions")
    decision = gate.get("alphaReduced")
    require(isinstance(decision, dict) and decision.get("decisionId") == ALPHA_REDUCED_DECISION_ID
            and decision.get("scope") == ALPHA_REDUCED_SCOPE, "Wrong alpha reduced decision scope")
    repair = "sourceRepair" in decision
    require(not repair or "sourceEquivalence" not in decision, "Ambiguous alpha reduced source admission")
    require(plan.get("source") == gate.get("source") == decision.get("source"),
            "Alpha reduced source/APKs are not the exact accepted successor")
    if not repair:
        require(plan.get("source") == accepted_alpha_source(),
                "Alpha reduced source/APKs are not the exact accepted successor")
    require(decision.get("historicalSource") == ALPHA_PREFLIGHT_SOURCE,
            "Alpha reduced historical preflight source mismatch")
    require(gate.get("supportedPayloads") == ["CAMPAIGN"], "Alpha reduced payload scope drift")
    require(gate.get("environment") == "E36-GAPI" and gate.get("deviceFingerprint") == E36_FINGERPRINT,
            "Alpha reduced scope requires exact E36 profile")
    require(gate.get("physicalAuthorization", {}).get("authorized") is False, "Alpha reduced physical scope forbidden")
    accountable = gate.get("accountableReview", {})
    require(gate.get("accountableReviewApproved") is False and accountable.get("formalReviewer") is False
            and accountable.get("reviewer") is None and accountable.get("reviewedCommit") is None,
            "Alpha reduced admission must not fabricate accountable review")
    root = Path(__file__).resolve().parents[1]
    proof = gate.get("proofs", {}).get("ownerDecision", {})
    decision_path = root / ALPHA_REDUCED_DECISION_PATH
    require(isinstance(proof.get("path"), str) and Path(proof["path"]).resolve() == decision_path.resolve()
            and decision_path.is_file() and proof.get("sha256") == ALPHA_REDUCED_DECISION_SHA256
            and hashlib.sha256(decision_path.read_bytes()).hexdigest() == ALPHA_REDUCED_DECISION_SHA256,
            "Alpha reduced owner decision proof mismatch")
    handoff = validate_retained_alpha_preflight(gate)
    if not repair:
        validate_alpha_source_equivalence(plan, decision)
    validate_plan(root, plan)
    selection = gate.get("reducedSelection")
    require(isinstance(selection, dict) and selection.get("scope") == ALPHA_REDUCED_SCOPE,
            "Alpha reduced selection missing")
    require(decision.get("selectionManifestSha256") == digest_json(selection)
            and decision.get("originalPlanManifestSha256") == selection.get("originalPlanManifestSha256"),
            "Alpha reduced decision selection binding mismatch")
    require(decision.get("originalPlanSource") == selection.get("originalPlanSource")
            and selection.get("executionPlanSource") == plan["source"],
            "Alpha reduced source plan binding mismatch")
    require(selection.get("originalPlanSource") == ALPHA_PREFLIGHT_SOURCE,
            "Alpha reduced original source plan mismatch")
    retained_root = _alpha_preflight_root()
    retained_original_path = retained_root / ALPHA_PREFLIGHT_FULL_PLAN_RELATIVE_PATH
    require(retained_original_path.is_file()
            and hashlib.sha256(retained_original_path.read_bytes()).hexdigest() == ALPHA_PREFLIGHT_FULL_PLAN_SHA256,
            "Alpha reduced pinned original full-plan evidence mismatch")
    original = json.loads(retained_original_path.read_bytes())
    validate_plan(root, original)
    require(digest_json(original) == selection.get("originalPlanManifestSha256")
            and original.get("source") == ALPHA_PREFLIGHT_SOURCE
            and original.get("scheduleSeed") == selection.get("originalScheduleSeed"),
            "Alpha reduced original plan manifest mismatch")
    original_e36 = [entry for entry in original["entries"] if entry.get("environment") == "E36-GAPI"]
    require(handoff.get("entries") == original_e36, "Alpha reduced original E36 schedule differs from retained handoff")
    original_proof = gate.get("proofs", {}).get("originalPlan", {})
    require(isinstance(original_proof.get("path"), str) and Path(original_proof["path"]).resolve() == retained_original_path.resolve()
            and original_proof.get("sha256") == ALPHA_PREFLIGHT_FULL_PLAN_SHA256
            and original_proof.get("manifestSha256") == selection.get("originalPlanManifestSha256"),
            "Alpha reduced original plan evidence proof mismatch")
    validate_reduced_e36_selection(original, plan, selection)
    require(gate.get("supportedAttemptIds") == selection["supportedAttemptIds"]
            and gate.get("supportedBaseAttemptIds") == selection["originalBaseAttemptIds"],
            "Alpha reduced supported attempt scope mismatch")
    if repair:
        validate_alpha_repair(plan, gate)


def request_for(plan: dict[str, Any], entry: dict[str, Any], operation: str, variant: str) -> dict[str, Any]:
    require(operation in ("PREPARE", "FAULT", "RECOVER", "WRITE_UNTIL_BARRIER", "CLEANUP"), "Unknown operation")
    require(variant in entry["mutationVariants"], "Unscheduled mutation variant")
    result = {key: entry[key] for key in ("attemptId", "candidateId", "runId", "seed", "plaintextBytes", "fixtureSha256")}
    result.update(schema=REQUEST_SCHEMA, operation=operation, protocolId=PROTOCOL, mutationVariant=variant)
    for optional in ("caseId", "stratumId", "seedId", "publicBarrier", "k12"):
        if optional in entry:
            result[optional] = entry[optional]
    if "externalAnchor" in entry:
        validate_external_anchor(entry["externalAnchor"], entry)
        result["externalAnchor"] = entry["externalAnchor"]
    if "hostRetentionReceipt" in entry:
        result["hostRetentionReceipt"] = entry["hostRetentionReceipt"]
    if "recoveryCheckpointSelection" in entry:
        result["recoveryCheckpointSelection"] = entry["recoveryCheckpointSelection"]
    return result


def validate_external_anchor(anchor: dict[str, Any], entry: dict[str, Any]) -> None:
    require(isinstance(anchor, dict) and set(anchor) == {"generation", "publicationCiphertextSha256", "committedEnd", "controllerEventAcknowledged"}, "Malformed external rollback anchor")
    require(type(anchor["generation"]) is int and 0 <= anchor["generation"] <= 0x7fffffffffffffff, "Invalid anchor generation")
    hex_value(anchor["publicationCiphertextSha256"], 64)
    require(type(anchor["committedEnd"]) is int and 0 <= anchor["committedEnd"] <= entry["plaintextBytes"], "Invalid anchor C")
    require(type(anchor["controllerEventAcknowledged"]) is bool, "Invalid controller event state")


def instrument_command(adb: Path, serial: str, request: dict[str, Any], commit: str, manifest_hash: str) -> list[str]:
    require(re.fullmatch(r"[A-Za-z0-9_.:-]{1,100}", serial) is not None, "Unsafe serial")
    hex_value(commit, 40)
    hex_value(manifest_hash, 64)
    safe_id(request["attemptId"])
    encoded = base64.b64encode(canonical(request)).decode("ascii")
    return [str(adb), "-s", serial, "shell", "am", "instrument", "-w", "-r",
            "-e", "class", SELECTOR, "-e", "recoveryCampaign", "true",
            "-e", "recoveryHarnessRevision", commit,
            "-e", "recoveryCampaignManifestSha256", manifest_hash,
            "-e", "recoveryCampaignRequest", encoded, RUNNER]


def compare_prefix(path: Path, entry: dict[str, Any], recovered: int) -> bool:
    if type(recovered) is not int or not 0 <= recovered <= entry["plaintextBytes"] or path.stat().st_size != recovered:
        return False
    with path.open("rb") as stream:
        for offset in range(0, recovered, 65536):
            length = min(65536, recovered - offset)
            if stream.read(length) != fixture_bytes(entry["seed"], length, offset):
                return False
    return True


def requires_external_kill(entry: dict[str, Any]) -> bool:
    return entry["kind"] == "HARD_KILL" or entry.get("caseId", "").startswith("KCB-") or entry.get("caseId") in ("QUA-02", "QUA-03", "IDE-02", "EVT-01", "CLN-01")


def validate_checkpoint_selection(before: dict[str, Any], after: dict[str, Any], entry: dict[str, Any]) -> None:
    fields = {"originalCheckpointIdentity", "checkpointIdentity", "checkpointGeneration", "checkpointPrefixBytes", "checkpointContextEnd", "acceptedEnd", "preFaultSourceBytes", "preFaultSourceSha256"}
    require(isinstance(before, dict) and isinstance(after, dict) and set(before) == set(after) == fields, "Checkpoint selection evidence shape invalid")
    require(entry.get("caseId") in ("KEY-05", "KEY-06", "PAR-01", "COR-06") and entry["candidateId"] == CANDIDATES[0], "Post-fault selection not permitted for this recipe")
    require(before["originalCheckpointIdentity"] == before["checkpointIdentity"], "Original checkpoint selection was already mutated")
    for field in fields - {"checkpointIdentity"}:
        require(before[field] == after[field], "Fault selection changed frozen oracle/source field " + field)
    for field in ("originalCheckpointIdentity", "checkpointIdentity", "preFaultSourceSha256"):
        hex_value(after[field], 64)
    require(type(after["acceptedEnd"]) is int and 0 <= after["checkpointContextEnd"] <= after["acceptedEnd"] <= entry["plaintextBytes"], "Selection watermark invalid")


def fault_predicate_failures(entry: dict[str, Any], observed: dict[str, Any], result: dict[str, Any]) -> list[str]:
    if entry["kind"] != "FAULT":
        return []
    case, variant = entry["caseId"], entry["mutationVariants"][0]
    failures: list[str] = []
    def check(condition: bool, reason: str) -> None:
        if not condition:
            failures.append(case + ":" + reason)
    facts = observed.get("artifactMutationFacts") or observed.get("confirmationMutationFacts") or {}
    classification = observed.get("classification", "")
    c, r, a = (observed.get(field) for field in ("committedEnd", "recoveredEnd", "acceptedEnd"))
    bounded = all(type(value) is int for value in (c, r, a))
    if case.startswith(("KEY-", "KCF-", "KCB-")):
        check(bool(entry["expectedClassifications"]) and classification in entry["expectedClassifications"], "EXACT_KEY_OR_BOOTSTRAP_CLASSIFICATION_REQUIRED")
        if case in ("KEY-04", "KCF-04", "KCF-05"):
            check(observed.get("keyAuthenticationFailureObserved") is True, "ACTUAL_DECRYPT_AUTH_OR_AAD_FAILURE_REQUIRED")
        if case in ("KCF-02", "KCF-03", "KEY-05"):
            check(observed.get("rejectedBeforeTargetDecrypt") is True, "PRE_DECRYPT_IDENTITY_OR_STRUCTURAL_REJECTION_REQUIRED")
        if case == "KCF-07":
            check(facts.get("correctKeyAndExactAadDecryptSucceeded") is True, "SUCCESSFUL_DECRYPT_BEFORE_PLAINTEXT_REJECTION_REQUIRED")
        if case in ("KEY-02", "KEY-03"):
            check(facts.get("missingArtifactRole") == ("PUBLICATION_KEY_ENVELOPE" if case == "KEY-02" else "DATA_KEY_ENVELOPE"), "DISTINCT_MISSING_ENVELOPE_ROLE_REQUIRED")
        if case == "KEY-03":
            check(bounded and type(facts.get("affectedPlaintextStart")) is int and 0 <= r <= facts["affectedPlaintextStart"],
                  "NO_PREFIX_ACROSS_UNAVAILABLE_DATA_ENVELOPE")
        if case in ("KEY-07", "KCF-06"):
            check(observed.get("existingArtifactsUnchanged") is True and observed.get("newPublicationCount") == 0, "COLLISION_MUST_PRESERVE_EXISTING_STATE")
        return failures
    if case in ("COR-01", "COR-02", "COR-03", "COR-04", "COR-05", "COR-06", "TRU-02", "SPL-02", "SPL-04"):
        check(bounded and 0 <= r < c <= a, "REJECTED_COMMITTED_REGION_AND_ORIGINAL_C_REQUIRED")
        check(classification not in ("VALID", "", "UNCLASSIFIED"), "EXPLICIT_CORRUPTION_OR_SPLIT_BRAIN_CLASSIFICATION_REQUIRED")
        if case in ("COR-01", "COR-04", "TRU-02", "SPL-02"):
            check(type(facts.get("affectedPlaintextStart")) is int and bounded and r <= facts["affectedPlaintextStart"], "NO_PREFIX_ACROSS_AFFECTED_REGION")
        if case in ("COR-03", "COR-05", "COR-06"):
            check(facts.get("originalPublicationAuthenticated") is True and facts.get("originalPublicationParsed") is True, "ORIGINAL_REAL_PUBLICATION_PRECONDITION_REQUIRED")
        if case == "COR-05":
            check(facts.get("faultPublicationDecryptSucceeded") is True, "STRICT_PARSER_REJECTION_MUST_FOLLOW_ACTUAL_DECRYPT")
    elif case == "PAR-01":
        expected = "UNSAFE_PATH" if variant in ("UNSAFE_PATH", "TRAVERSAL_PATH", "SYMLINK") else "OVERSIZED_MANIFEST" if variant == "OVERSIZED" else "MALFORMED_MANIFEST"
        check(classification == expected, "EXACT_" + expected + "_CLASSIFICATION_REQUIRED")
        check(observed.get("unsafePathOpens") == 0, "UNSAFE_OBJECT_MUST_NEVER_BE_OPENED")
        if variant != "SYMLINK":
            check(facts.get("faultPublicationDecryptSucceeded") is True, "INTENDED_AUTHENTICATED_PARSER_SEAM_REQUIRED")
    elif case == "TRU-01":
        check(bounded and c <= r <= a and observed.get("authenticated") is True, "COMMITTED_PREFIX_MUST_SURVIVE_TAIL_TRUNCATION")
        check(bool(observed.get("tailClassification")), "EXPLICIT_TAIL_CLASSIFICATION_REQUIRED")
    elif case == "TRU-03":
        if entry["candidateId"] == CANDIDATES[0]:
            size = int(variant.removeprefix("APPEND_"))
            source = observed.get("preFaultSourceBytes")
            end = observed.get("observedSourceBytes")
            check(type(source) is int and type(end) is int and end == source + size, "EXACT_APPEND_EXTENT_REQUIRED")
            check(observed.get("sourceUnchanged") is True and observed.get("processingIntentCount") == 0, "STREAM_SOURCE_AND_INTENT_BOUNDARY")
            start, certainty = observed.get("rangeStart"), observed.get("rangeCertainty")
            def boundary(value: Any) -> int | None:
                if type(value) is not int or value < 0:
                    return None
                if value == 0:
                    return 0
                return (1 + (value - 4056) // 4080) * 4096 if value >= 4056 and (value - 4056) % 4080 == 0 else None
            if classification == "VALID":
                b = boundary(r)
                check(bounded and c <= r <= a and a - r <= 8160, "EXACT_STREAM_ADMISSION_REQUIRED")
                check(b is not None and type(end) is int and b <= end, "CANONICAL_PUBLIC_READ_BOUNDARY_REQUIRED")
                if b is not None and type(end) is int and b < end:
                    check(start == b and observed.get("rangeEnd") == end and certainty == "EXACT_FORMAT_BOUNDARY", "EXACT_REMAINDER_RANGE_REQUIRED")
                else:
                    check(start is None and observed.get("rangeEnd") is None and certainty is None, "EMPTY_REMAINDER_MUST_NOT_CREATE_RANGE")
            elif classification == "STREAM_REMAINDER_BOUNDARY_UNPROVEN":
                b = boundary(c)
                check(observed.get("boundaryResult") in ("NON_CANONICAL_CANDIDATE_END", "BOUNDARY_EXCEEDS_OBSERVED_SOURCE"), "REJECTED_BOUNDARY_OBSERVATION_REQUIRED")
                if b is not None and type(end) is int and b < end:
                    check(start == b and observed.get("rangeEnd") == end and certainty == "CONSERVATIVE_PROVEN_CHECKPOINT_SUPERSET", "CHECKPOINT_SUPERSET_RANGE_REQUIRED")
                else:
                    check(start == 0 and observed.get("rangeEnd") == end and certainty == "CONSERVATIVE_WHOLE_SOURCE", "WHOLE_SOURCE_RANGE_REQUIRED")
            else:
                check(classification in ("STREAM_SOURCE_TRUNCATED", "STREAM_CHECKPOINT_PREFIX_OUTSIDE_WITNESS", "STREAM_SOURCE_PREFIX_IDENTITY_MISMATCH", "STREAM_RETURNED_BYTE_ORACLE_MISMATCH", "STREAM_RECOVERED_BELOW_CHECKPOINT"), "EXPLICIT_V07_DIAGNOSTIC_REQUIRED")
                check(start == 0 and observed.get("rangeEnd") == end and certainty == "CONSERVATIVE_WHOLE_SOURCE", "DIAGNOSTIC_WHOLE_SOURCE_RANGE_REQUIRED")
        else:
            check(bounded and r < c and classification != "VALID", "UNAUTHENTICATED_APPEND_MUST_NOT_BECOME_HEALTHY_UNIT")
    elif case in ("SPL-01", "SPL-03"):
        check(observed.get("implicitCommitCount") == 0, "ORPHAN_OR_AHEAD_PUBLICATION_MUST_NOT_COMMIT")
        check(observed.get("retainedForReconciliation") is True, "UNCOMMITTED_GENERATION_MUST_BE_RETAINED")
    elif case in ("SPL-05", "QUA-01", "QUA-02", "QUA-03", "IDE-02"):
        quarantine = observed.get("quarantineObservation") or {}
        check(quarantine.get("rowCount") == quarantine.get("uniqueIntentCount") == 1, "EXACTLY_ONE_QUARANTINE_INTENT_REQUIRED")
        check(quarantine.get("completedCount") == 1 and quarantine.get("sourceItemCount") == 0 and quarantine.get("destinationItemCount") == 1, "QUARANTINE_MUST_CONVERGE_WITHOUT_LOSS_OR_DUPLICATION")
        check(bounded and c <= r <= a, "QUARANTINE_MUST_RETAIN_COMMITTED_PREFIX")
    elif case in ("RBK-01", "RBK-02"):
        check(result.get("hostRollbackDetected") is True, "INDEPENDENT_RETAINED_ANCHOR_COMPARISON_REQUIRED")
        check(classification == "ROLLBACK_DETECTED" and observed.get("underlyingControllerDiagnostic") is not None, "LOCAL_RESULT_MUST_REMAIN_DISTINCT_FROM_EXTERNAL_DETECTION")
    elif case == "IDE-01":
        check(observed.get("repeatStable") is True and result.get("hostOracleEqual") is True, "INDEPENDENT_IDENTICAL_REPLAY_REQUIRED")
    elif case == "EVT-01":
        check(observed.get("controllerEventGapObserved") is True, "EVENT_GAP_MUST_BE_REPORTED")
        check(bounded and c <= r <= a and c == result.get("hostCommittedEnd"), "SUCCESSFUL_COMMIT_MUST_REMAIN_RECONSTRUCTIBLE")
    elif case.startswith("CLN-"):
        cleanup = observed.get("cleanupFacts") or {}
        check(result.get("hostPreCleanupOracleEqual") is True and result.get("preCleanupRetentionVerified") is True, "INDEPENDENT_PRE_CLEANUP_OUTCOME_AND_RAW_RETENTION_REQUIRED")
        check(cleanup.get("receiptContainsReferencesOnly") is True, "CLEANUP_RECEIPT_MUST_CONTAIN_REFERENCES_ONLY")
        if case == "CLN-01":
            check(observed.get("preCleanupOutcomeUsed") is True and cleanup.get("cleanupComplete") is True, "KILLED_CLEANUP_MUST_CONVERGE_USING_RETAINED_ORIGINAL_OUTCOME")
        elif case == "CLN-02":
            check(cleanup.get("deletionDeniedByPlatform") is True and cleanup.get("parentModeRestored") is True and cleanup.get("verdictAndEvidenceRetained") is True, "ACTUAL_PERMISSION_DENIAL_AND_RETAINED_OUTCOME_REQUIRED")
        else:
            check(classification == "KEY_UNAVAILABLE" and cleanup.get("keyAliasAbsent") is True and cleanup.get("dataAndJournalRetained") is True, "ACTUAL_ALIAS_DELETION_AFTER_RETENTION_REQUIRED")
    else:
        check(False, "UNKNOWN_FAULT_CASE")
    return failures


def evaluate_attempt(entry: dict[str, Any], result: dict[str, Any]) -> dict[str, Any]:
    require(result.get("attemptId") == entry["attemptId"], "Attempt identity mismatch")
    status = result.get("status")
    require(status in ("VALID", "INVALID", "UNTESTED"), "Unknown attempt status")
    if status == "INVALID":
        require(result.get("invalidReason") in INVALIDATORS, "Candidate failure cannot be an external invalidator")
        return {"verdict": "INCONCLUSIVE", "valid": False, "failures": [result["invalidReason"]]}
    if status == "UNTESTED":
        return {"verdict": "INCONCLUSIVE", "valid": False, "failures": ["UNTESTED"]}
    if len(entry["mutationVariants"]) > 1:
        branches = result.get("mutationVariantResults", [])
        names = [branch.get("variant") for branch in branches]
        require(len(names) == len(set(names)) and all(name in entry["mutationVariants"] for name in names), "Unexpected or duplicate mutation variant")
        assessments = [evaluate_attempt(dict(entry, mutationVariants=[branch["variant"]]), branch["result"]) for branch in branches]
        failures = [failure for assessment in assessments for failure in assessment["failures"]]
        if set(names) != set(entry["mutationVariants"]):
            failures.append("REQUIRED_MUTATION_VARIANT_EVIDENCE_MISSING")
        incomplete = any(assessment["verdict"] == "INCONCLUSIVE" for assessment in assessments)
        return {"verdict": "FAIL" if failures else "INCONCLUSIVE" if incomplete else "PASS", "valid": True, "failures": failures}
    if requires_external_kill(entry):
        require(all(result.get("kill", {}).get(key) is True for key in KILL_PROOFS), "Valid hard kill lacks independent external proof")
    failures = []
    observed = result.get("candidateResult")
    if not isinstance(observed, dict):
        return {"verdict": "FAIL", "valid": True, "failures": ["CANDIDATE_OUTPUT_MISSING_OR_INVALID"]}
    # A fault deliberately removing a key must fail closed while retaining the
    # pre-fault committed baseline. A later missing envelope can leave an earlier
    # authenticated prefix (v0.3 KEY03: no skip across a hole); whole-run key or
    # confirmation denial must return zero. Neither rewrites the retained C.
    fail_closed = entry.get("caseId") == "CLN-03" or (entry["kind"] == "FAULT" and bool(entry["expectedClassifications"]) and entry["expectedClassifications"][0] in {
        "KEY_UNAVAILABLE", "KEY_UNAVAILABLE_KEY_MISMATCH", "KEY_CONFIRMATION_MISSING",
        "CORRUPT_KEY_CONFIRMATION", "CORRUPT_KEY_ENVELOPE", "KEY_ENVELOPE_AUTH_FAILURE",
        "KEY_REF_COLLISION", "INCOMPLETE_KEY_BOOTSTRAP",
    })
    envelope_denial = entry.get("caseId") in ("KEY-02", "KEY-03", "KEY-05", "KEY-06")
    rollback = entry.get("caseId") in ("RBK-01", "RBK-02") and result.get("hostRollbackDetected") is True
    values = [observed.get(key) for key in ("committedEnd", "recoveredEnd", "acceptedEnd")]
    if not all(type(value) is int for value in values):
        failures.append("WATERMARKS_MISSING_OR_INVALID")
    else:
        committed, recovered, accepted = values
        if fail_closed:
            permitted_prefix = 0 <= recovered <= committed if envelope_denial else recovered == 0
            if not (0 <= committed <= accepted <= entry["plaintextBytes"] and permitted_prefix):
                failures.append("FAIL_CLOSED_RETURNED_PLAINTEXT_OR_INVALID_BASELINE")
        elif rollback:
            if not 0 <= recovered <= committed <= accepted <= entry["plaintextBytes"]:
                failures.append("INVALID_ROLLBACK_WATERMARKS")
        elif not 0 <= committed <= recovered <= accepted <= entry["plaintextBytes"]:
            failures.append("COMMITTED_LOSS_OR_NONCONTIGUOUS_WATERMARKS")
        if entry["kind"] == "HARD_KILL" and accepted - recovered > 160000:
            failures.append("TAIL_BOUND_EXCEEDED")
        if entry["kind"] == "HARD_KILL" and entry["candidateId"] == CANDIDATES[0] and accepted - recovered > 8160:
            failures.append("STREAM_DESIGN_TAIL_BOUND_EXCEEDED")
    no_plaintext_denial = fail_closed and observed.get("recoveredEnd") == 0
    for key in (("contiguous", "repeatStable", "caseOracleSatisfied") if no_plaintext_denial else ("authenticated", "contiguous", "repeatStable", "caseOracleSatisfied")):
        if observed.get(key) is not True:
            failures.append(key.upper() + "_NOT_PROVEN")
    if result.get("hostOracleEqual") is not True:
        failures.append("INDEPENDENT_HOST_ORACLE_NOT_EQUAL")
    for key in ("duplicateProcessingIntents", "missingProcessingIntents", "microphoneOpens", "unsafePathOpens"):
        if type(observed.get(key)) is not int or observed[key] != 0:
            failures.append(key.upper() + "_NONZERO_OR_MISSING")
    if entry["candidateId"] == CANDIDATES[0]:
        if type(observed.get("processingIntentCount")) is not int or observed["processingIntentCount"] != 0:
            failures.append("STREAM_PROCESSING_INTENT_FORBIDDEN")
        if observed.get("sourceUnchanged") is not True and not (entry.get("caseId") == "CLN-01" and result.get("preCleanupRetentionVerified") is True):
            failures.append("SOURCE_IMMUTABILITY_NOT_PROVEN")
    if entry["expectedClassifications"] and observed.get("classification") not in entry["expectedClassifications"]:
        failures.append("WRONG_CLASSIFICATION")
    if requires_external_kill(entry) and result.get("hostAcceptedEnd") != observed.get("acceptedEnd"):
        failures.append("ACCEPTED_WATERMARK_DIFFERS_FROM_PREFLIGHT_CONTROLLER_LEDGER")
    complete = result.get("cleanupResult") == "VERIFIED" and not result.get("controllerError")
    recipe_failures = fault_predicate_failures(entry, observed, result)
    failures.extend(recipe_failures)
    if entry.get("caseId") in ("SPL-02", "TRU-02"):
        failures.append("NORMATIVE_COMMITTED_LOSS_CASE_REQUIRES_PRODUCT_FAIL")
    return {"verdict": "FAIL" if failures else "PASS" if complete else "INCONCLUSIVE", "valid": True, "failures": failures,
            "recipeSatisfied": not recipe_failures, "evidenceAndCleanupComplete": complete}


def evaluate_campaign(plan: dict[str, Any], ledger: list[dict[str, Any]]) -> dict[str, Any]:
    scheduled = {e["attemptId"]: e for e in plan["entries"]}
    results = {}
    replacements = Counter()
    for result in ledger:
        identity = result.get("attemptId")
        require(identity not in results, "Duplicate attempt result")
        if identity not in scheduled:
            require(isinstance(identity, str) and identity.endswith("-R1"), "Unscheduled attempt")
            base_id = identity[:-3]
            require(base_id in scheduled and base_id in results, "Replacement lacks retained base disposition")
            require(scheduled[base_id]["kind"] == "HARD_KILL", "Hard-kill replacement policy does not cover fault rows")
            base = results[base_id]
            require(base.get("status") == "INVALID" and base.get("invalidReason") in INVALIDATORS, "Only external invalid base attempts can be replaced")
            require(result.get("replacementFor") == base_id and result.get("replacementLedgerAuthorization"), "Replacement requires explicit ledger authorization")
            candidate = scheduled[base_id]["candidateId"]
            replacements[candidate] += 1
            require(replacements[candidate] <= 20, "Replacement cap exceeded")
            scheduled[identity] = dict(scheduled[base_id], attemptId=identity)
        results[identity] = result
    evaluated = {}
    valid = Counter()
    untested = 0
    for identity, entry in scheduled.items():
        result = results.get(identity, {"attemptId": identity, "status": "UNTESTED"})
        outcome = evaluate_attempt(entry, result)
        evaluated[identity] = outcome
        if result["status"] == "UNTESTED":
            untested += 1
        if outcome["valid"]:
            valid[(entry["kind"], entry["candidateId"], entry["environment"], entry.get("stratumId"))] += 1
    failures = [identity for identity, outcome in evaluated.items() if outcome["verdict"] == "FAIL"]
    incomplete = [identity for identity, outcome in evaluated.items() if outcome["verdict"] == "INCONCLUSIVE"]
    coverage = []
    for candidate in CANDIDATES:
        kills = [entry for identity, entry in scheduled.items() if entry["kind"] == "HARD_KILL" and entry["candidateId"] == candidate and evaluated[identity]["valid"]]
        if len(kills) < 100:
            coverage.append(candidate + ":VALID_KILLS_BELOW_100")
        minimums = {"E36-GAPI": 60, "D2": 40} if plan["phase"] == "PHASE_A" else {"D1": 40, "D2": 30, "D5": 30}
        for environment, minimum in minimums.items():
            if sum(e["environment"] == environment for e in kills) < minimum:
                coverage.append(candidate + ":" + environment + ":VALID_MINIMUM")
        for index in range(1, 13):
            stratum = f"K{index:02d}"
            subset = [e for e in kills if e["stratumId"] == stratum]
            if len(subset) < 8:
                coverage.append(candidate + ":" + stratum + ":BELOW_8")
            for environment in minimums:
                minimum = 4 if environment == "E36-GAPI" else 2
                if sum(e["environment"] == environment for e in subset) < minimum:
                    coverage.append(candidate + ":" + stratum + ":" + environment + ":STRATUM_MINIMUM")
    fault_missing = any(entry["kind"] == "FAULT" and not evaluated[identity]["valid"] and not evaluated.get(identity + "-R1", {}).get("valid", False) for identity, entry in scheduled.items() if not identity.endswith("-R1"))
    # Invalid bases with valid explicit replacements remain in the ledger but
    # need not prevent coverage; incomplete executed evidence still prevents PASS.
    unresolved = [identity for identity in incomplete if not evaluated.get(identity + "-R1", {}).get("valid", False)]
    verdict = "FAIL" if failures else "INCONCLUSIVE" if untested or coverage or fault_missing or unresolved or plan["phase"] == "PHASE_A" else "PASS"
    return {"schema": "DORA_RECOVERY_CAMPAIGN_EVALUATION_V1", "manifestSha256": digest_json(plan), "phase": plan["phase"], "verdict": verdict,
            "scheduledBase": len(plan["entries"]), "observed": len(results), "untested": untested,
            "hardKillValid": sum(value for key, value in valid.items() if key[0] == "HARD_KILL"),
            "faultValid": sum(value for key, value in valid.items() if key[0] == "FAULT"),
            "invalid": sum(r["status"] == "INVALID" for r in results.values()),
            "replacements": sum(replacements.values()), "failedAttempts": failures,
            "incompleteAttempts": unresolved,
            "coverageFailures": coverage, "attemptEvaluations": evaluated,
            "fullRecoveryApprovalGranted": False, "stageClosureGranted": False}


def save_new(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("xb") as stream:
        stream.write(canonical(value) + b"\n")
        stream.flush()
        os.fsync(stream.fileno())


def validate_execution_gate(plan: dict[str, Any], gate: dict[str, Any], session: dict[str, Any] | None = None, payload: str = "CAMPAIGN") -> None:
    require(gate.get("schema") == "DORA_RECOVERY_CAMPAIGN_EXECUTION_GATE_V1", "Execution gate schema missing")
    require(gate.get("manifestSha256") == digest_json(plan), "Execution gate is not bound to this manifest")
    require(gate.get("source") == plan["source"], "Execution source/APK gate mismatch")
    require(gate.get("driverSha256") == hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), "Executing host driver is not the reviewed pinned file")
    parser_path = Path(__file__).with_name("recovery_instrumentation_status.py")
    require(parser_path.is_file() and hashlib.sha256(parser_path.read_bytes()).hexdigest() == gate.get("instrumentationParserSha256"), "Instrumentation parser is not the reviewed pinned file")
    require(payload in ("CAMPAIGN", "SUPPLEMENTAL_SQLITE", "PLATFORM_PREREQUISITES", "JOURNAL_CONNECTIONS"), "Unknown fixed payload")
    preflight = payload != "CAMPAIGN"
    alpha_preflight = "alphaPreflight" in gate
    alpha_campaign = "alphaCampaign" in gate
    alpha_reduced = "alphaReduced" in gate
    require(sum((alpha_preflight, alpha_campaign, alpha_reduced)) <= 1, "Ambiguous simultaneous alpha admissions")
    if alpha_preflight:
        validate_alpha_preflight(plan, gate, payload)
    if alpha_campaign:
        require(payload == "CAMPAIGN", "Alpha campaign cannot admit preflight payloads")
        validate_alpha_campaign(plan, gate)
    if alpha_reduced:
        require(payload == "CAMPAIGN", "Alpha reduced scope cannot admit preflight payloads")
        validate_alpha_reduced(plan, gate)
    if preflight:
        require(payload in gate.get("supportedPayloads", []), "Fixed preflight payload not reviewed")
    alpha = alpha_preflight or alpha_campaign or alpha_reduced
    review_flags = () if alpha else ("accountableReviewApproved",)
    for key in ("executionAuthorized", "implementationVerified", "independentReviewClean") + review_flags + ("exactHeadCiPassed", "graphAndR8Verified") + (() if preflight else ("preflightPassed",)):
        require(gate.get(key) is True, key + " gate unsatisfied")
    require(isinstance(gate.get("ownerInstructionReference"), str) and gate["ownerInstructionReference"], "Missing owner instruction source")
    accountable = gate.get("accountableReview", {})
    if not alpha:
        require(accountable.get("formalReviewer") is True and accountable.get("reviewedCommit") == plan["source"]["commit"] and bool(accountable.get("reviewer")), "Missing exact accountable review identity")
    # These files are the concrete reviewable prerequisite packet; booleans by
    # themselves never authorize execution. The coordinator supplies truthful
    # accepted evidence. This tool does not create a human attestation.
    proofs = gate.get("proofs", {})
    review_proofs = () if alpha else ("accountableReview",)
    for key in ("implementation", "independentReview") + review_proofs + ("ci", "graphAndR8", "ownerInstruction") + (() if preflight or alpha_campaign or alpha_reduced else ("preflight",)):
        proof = proofs.get(key, {})
        require(isinstance(proof.get("path"), str), "Missing prerequisite artifact " + key)
        path = Path(proof["path"])
        require(path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == proof.get("sha256"), "Prerequisite artifact mismatch " + key)
    if session is None:
        return
    require(session.get("schema") == "DORA_RECOVERY_OWNED_SESSION_V1" and session.get("ownershipVerified") is True, "Existing launcher ownership receipt required")
    require(session.get("source") == plan["source"], "Session source mismatch")
    environment = session.get("environment")
    require(environment in ("E36-GAPI", "D1", "D2", "D5"), "Unknown campaign environment")
    require(isinstance(session.get("serial"), str) and re.fullmatch(r"[A-Za-z0-9_.:-]{1,100}", session["serial"]) is not None, "Invalid exact device serial")
    if environment == "E36-GAPI":
        require(session.get("serial") == "emulator-5556" and session.get("avdName") == "dora_api36_recovery", "Unexpected owned emulator")
        require(session.get("deviceFingerprint") == "google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys", "Unpinned E36 fingerprint")
    else:
        physical = gate.get("physicalAuthorization", {})
        require(physical.get("authorized") is True and physical.get("serial") == session["serial"] and physical.get("profile") == environment, "Exact physical device operation authorization missing")
        require(physical.get("operations") == ["SYNTHETIC_RECOVERY_FAULTS", "APP_PROCESS_SIGKILL", "RUN_SCOPED_CLEANUP"], "Physical operation scope mismatch")
        proof = physical.get("proof", {})
        require(isinstance(proof.get("path"), str) and Path(proof["path"]).is_file(), "Physical authorization artifact missing")
        require(hashlib.sha256(Path(proof["path"]).read_bytes()).hexdigest() == proof.get("sha256"), "Physical authorization artifact mismatch")
    require(gate.get("environment") == session.get("environment"), "Gate environment mismatch")
    require(gate.get("deviceFingerprint") == session.get("deviceFingerprint"), "Device fingerprint gate mismatch")
    expiry = datetime.fromisoformat(session.get("expiresAtUtc", "").replace("Z", "+00:00"))
    remaining = (expiry - datetime.now(timezone.utc)).total_seconds()
    require(0 < remaining <= 300, "Ownership receipt expired or extends beyond five-minute launch window")
    require(bool(session.get("ownerSessionId")) and bool(session.get("launcherReceiptPath")), "Missing existing launcher receipt")
    receipt = Path(session["launcherReceiptPath"])
    require(receipt.is_file() and hashlib.sha256(receipt.read_bytes()).hexdigest() == session.get("launcherReceiptSha256"), "Launcher receipt identity mismatch")


def owned_adb_command(adb: Path, port: int, arguments: list[str]) -> list[str]:
    require(type(port) is int and 1 <= port <= 65535, "Invalid owned ADB server port")
    # AOSP treats only empty host/'localhost' as a local auto-start socket.
    # Explicit 127.0.0.1 uses the remote-server client path: a missing server
    # fails instead of launching one. Never fall back to the default socket.
    return [str(adb), "-H", "127.0.0.1", "-P", str(port), *arguments]


def validate_event(event: dict[str, Any], entry: dict[str, Any], operation: str) -> None:
    require(event.get("schema") == "DORA_RECOVERY_CAMPAIGN_EVENT_V1", "Unexpected event schema")
    for key in ("attemptId", "candidateId", "runId"):
        require(event.get(key) == entry[key], "Foreign event identity " + key)
    require(event.get("operation") == operation, "Foreign event operation")
    require(event.get("eventType") in ("READY", "BARRIER", "RESULT", "ERROR"), "Unexpected event type")
    if event.get("eventType") == "BARRIER":
        require(type(event.get("pid")) is int and event["pid"] > 1, "Invalid barrier PID")
        require(event.get("publicBarrier") == entry.get("publicBarrier", entry.get("caseId")), "Wrong public barrier")
        require(type(event.get("acceptedEnd")) is int and 0 <= event["acceptedEnd"] <= entry["plaintextBytes"], "Missing bounded accepted watermark at public barrier")


def kill_arguments(pid: int, observed_pid: str, observed_cmdline: str) -> list[str]:
    require(type(pid) is int and pid > 1, "Invalid target PID")
    require(observed_pid.strip() == str(pid), "Target PID identity changed or multiple processes exist")
    require(observed_cmdline.rstrip("\x00\r\n") == PACKAGE, "Target process is not the exact PoC application")
    return ["shell", "run-as", PACKAGE, "kill", "-9", str(pid)]


def readonly_retention_arguments(arguments: list[str]) -> bool:
    """The only idempotent app-private reads eligible for one client retry."""
    safe_path = lambda value: isinstance(value, str) and re.fullmatch(r"[A-Za-z0-9_./-]{1,300}", value) is not None and not any(part in ("", ".", "..") for part in value.split("/"))
    if arguments[:4] == ["shell", "run-as", PACKAGE, "stat"]:
        return len(arguments) == 7 and arguments[4:6] == ["-c", "%F"] and safe_path(arguments[6])
    if arguments[:4] == ["exec-out", "run-as", PACKAGE, "head"]:
        return len(arguments) == 7 and arguments[4] == "-c" and arguments[5].isdigit() and safe_path(arguments[6])
    return len(arguments) == 6 and arguments[:5] == ["exec-out", "run-as", PACKAGE, "readlink", "-n"] and safe_path(arguments[5])


class OwnedAdbTransport:
    """Bounded client commands only; existing launcher owns all host services."""

    def __init__(self, adb: Path, session: dict[str, Any], directory: Path):
        require(adb.is_file() and hashlib.sha256(adb.read_bytes()).hexdigest() == session.get("adbSha256"), "ADB executable pin mismatch")
        self.adb, self.session, self.directory = adb, session, directory
        self.sequence = 0

    def argv(self, arguments: list[str]) -> list[str]:
        return owned_adb_command(self.adb, self.session["adbPort"], ["-s", self.session["serial"], *arguments])

    def _predispatch_readonly_connection_failure(self, label: str) -> bool:
        prefix = self.directory / f"{self.sequence:03d}-{safe_id(label)}"
        result = json.loads(prefix.with_suffix(".result.json").read_text(encoding="utf-8"))
        stderr = prefix.with_suffix(".stderr").read_bytes().replace(b"\r\n", b"\n")
        expected = b"* daemon still not running\nerror: cannot connect to daemon at tcp:127.0.0.1:" + str(self.session["adbPort"]).encode("ascii") + b":"
        return result == {"nativeExit": 1, "timedOut": False} and not prefix.with_suffix(".stdout").read_bytes() and stderr.startswith(expected)

    def run(self, arguments: list[str], label: str, timeout: int = 30, require_success: bool = True,
            retry_readonly_connection_once: bool = False) -> subprocess.CompletedProcess:
        if retry_readonly_connection_once:
            require(require_success and readonly_retention_arguments(arguments), "Readonly connection retry is limited to successful raw-retention reads")
        self.sequence += 1
        prefix = self.directory / f"{self.sequence:03d}-{safe_id(label)}"
        argv = self.argv(arguments)
        save_new(prefix.with_suffix(".command.json"), {"argv": argv, "timeoutSeconds": timeout})
        try:
            completed = subprocess.run(argv, capture_output=True, timeout=timeout, check=False,
                                       creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        except subprocess.TimeoutExpired as error:
            prefix.with_suffix(".stdout").write_bytes(error.stdout or b"")
            prefix.with_suffix(".stderr").write_bytes(error.stderr or b"")
            save_new(prefix.with_suffix(".result.json"), {"nativeExit": None, "timedOut": True})
            raise ValueError("Bounded ADB operation timeout; preserve evidence and stop") from error
        prefix.with_suffix(".stdout").write_bytes(completed.stdout)
        prefix.with_suffix(".stderr").write_bytes(completed.stderr)
        save_new(prefix.with_suffix(".result.json"), {"nativeExit": completed.returncode, "timedOut": False})
        if require_success and not retry_readonly_connection_once:
            require(completed.returncode == 0, "ADB operation failed: " + label)
        if retry_readonly_connection_once and completed.returncode != 0:
            if self._predispatch_readonly_connection_failure(label):
                return self.run(arguments, label, timeout=timeout, require_success=True,
                                retry_readonly_connection_once=False)
            require(False, "ADB operation failed: " + label)
        return completed

    def verify_device(self, source: dict[str, str]) -> None:
        require(self.run(["get-state"], "device-state").stdout.strip() == b"device", "Owned device not online")
        fingerprint = self.run(["shell", "getprop", "ro.build.fingerprint"], "fingerprint").stdout.decode().strip()
        require(fingerprint == self.session["deviceFingerprint"], "Live device fingerprint mismatch")
        for package, key in ((PACKAGE, "appApkSha256"), (PACKAGE + ".test", "testApkSha256")):
            paths = self.run(["shell", "pm", "path", package], "package-path-" + key).stdout.decode().splitlines()
            require(len(paths) == 1 and paths[0].startswith("package:"), "Unexpected package split/path state")
            path = paths[0][8:]
            require(re.fullmatch(r"/data/app/[A-Za-z0-9_./=+~-]+\.apk", path) is not None, "Unsafe package path")
            digest = self.run(["shell", "sha256sum", path], "installed-sha-" + key).stdout.decode().split()[0]
            require(digest == source[key], "Installed APK digest mismatch")

    def extract_prefix(self, entry: dict[str, Any], event: dict[str, Any], label: str = "recovered") -> Path:
        expected = "campaign/" + safe_id(entry["attemptId"]) + "/recovered.pcm"
        require(event.get("returnedPrefixArtifact") == expected, "Unexpected private prefix path")
        recovered = event.get("recoveredEnd")
        require(type(recovered) is int and 0 <= recovered <= entry["plaintextBytes"], "Unbounded recovered extent")
        # One extra byte detects an overlong artifact without unbounded capture.
        result = self.run(["exec-out", "run-as", PACKAGE, "head", "-c", str(recovered + 1), "files/" + expected], "private-prefix-" + safe_id(label), timeout=60)
        target = self.directory / (safe_id(label) + ".pcm")
        with target.open("xb") as stream:
            stream.write(result.stdout)
        return target


def parse_events(output: bytes, entry: dict[str, Any], operation: str) -> list[dict[str, Any]]:
    events = []
    marker = "DORA_RECOVERY_CAMPAIGN_EVENT "
    for line in output.decode("utf-8", errors="strict").splitlines():
        if marker in line:
            event = json.loads(line.split(marker, 1)[1])
            validate_event(event, entry, operation)
            events.append(event)
    return events


def run_operation(transport: OwnedAdbTransport, plan: dict[str, Any], entry: dict[str, Any], operation: str, variant: str) -> dict[str, Any]:
    request = request_for(plan, entry, operation, variant)
    command = instrument_command(transport.adb, transport.session["serial"], request, plan["source"]["commit"], digest_json(plan))
    completed = transport.run(command[3:], operation.lower() + "-" + variant, timeout=180)
    parser_path = Path(__file__).with_name("recovery_instrumentation_status.py")
    require(hashlib.sha256(parser_path.read_bytes()).hexdigest() == transport.instrumentation_parser_sha256, "Instrumentation parser changed after gate verification")
    spec = importlib.util.spec_from_file_location("recovery_instrumentation_status", parser_path)
    require(spec is not None and spec.loader is not None, "Pinned instrumentation parser unavailable")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    module.validate_instrumentation_success(completed.stdout, SELECTOR)
    events = parse_events(completed.stdout, entry, operation)
    errors = [event for event in events if event["eventType"] == "ERROR"]
    require(not errors, "Android campaign adapter returned ERROR")
    results = [event for event in events if event["eventType"] == "RESULT"]
    require(len(results) == 1, "Missing or duplicate exact operation result")
    return results[0]


def acknowledgement_arguments(entry: dict[str, Any], event: dict[str, Any], previous_sequence: int) -> list[str]:
    sequence = event.get("sequence")
    require(event.get("eventAcknowledgementRequired") is True and type(sequence) is int and sequence == previous_sequence + 1, "Invalid controller acknowledgement sequence")
    expected = "campaign/" + safe_id(entry["attemptId"]) + "/ack-" + str(sequence)
    require(event.get("acknowledgementRelativePath") == expected, "Foreign controller acknowledgement path")
    return ["shell", "run-as", PACKAGE, "touch", "files/" + expected]


def retention_path(entry: dict[str, Any], artifact: dict[str, Any]) -> str:
    relative = artifact.get("relativePath")
    require(isinstance(relative, str) and re.fullmatch(r"[A-Za-z0-9_./-]{1,300}", relative) is not None and not any(part in ("", ".", "..") for part in relative.split("/")), "Unsafe retained artifact path")
    run_id = str(uuid.UUID(hex=hex_value(entry["runId"], 32)))
    roots = ("no_backup/poc-recovery/v1/runs/" + run_id + "/", "no_backup/poc-recovery/v1/quarantine/" + run_id + "/", "files/campaign/" + safe_id(entry["attemptId"]) + "/")
    require(relative.startswith(roots), "Artifact outside this exact run and attempt evidence boundary")
    require(type(artifact.get("bytes")) is int and 0 <= artifact["bytes"] <= 128 * 1024 * 1024, "Unbounded retained artifact")
    hex_value(artifact.get("sha256"), 64)
    require(artifact.get("kind", "REGULAR_FILE") in ("REGULAR_FILE", "SYMLINK_TARGET_TEXT"), "Unsupported retained object kind")
    return relative


def android_artifact_manifest_sha256(artifacts: list[dict[str, Any]]) -> str:
    """Match the Android adapter's explicit JSONObject.quote wire canonical form."""
    require(isinstance(artifacts, list), "Raw retention manifest missing")
    fields = []
    paths = []
    for artifact in artifacts:
        require(isinstance(artifact, dict), "Malformed raw retention artifact")
        relative = artifact.get("relativePath")
        role = artifact.get("role")
        size = artifact.get("bytes")
        digest = artifact.get("sha256")
        require(isinstance(relative, str) and isinstance(role, str) and type(size) is int and size >= 0, "Malformed raw retention artifact")
        hex_value(digest, 64)
        paths.append(relative)
        # Android JSONObject.quote escapes slash in this adapter's JSON wire
        # form.  Paths are ASCII-constrained by retention_path below.
        quote = lambda value: json.dumps(value, ensure_ascii=True, separators=(",", ":")).replace("/", "\\/")
        fields.append("{\"bytes\":" + str(size) + ",\"relativePath\":" + quote(relative) + ",\"role\":" + quote(role) + ",\"sha256\":" + quote(digest) + "}")
    require(paths == sorted(paths), "Raw retention manifest order differs from Android wire order")
    return hashlib.sha256(("[" + ",".join(fields) + "]").encode("ascii")).hexdigest()


def retain_evidence(transport: OwnedAdbTransport, plan: dict[str, Any], entry: dict[str, Any], observation: dict[str, Any], label: str) -> dict[str, Any]:
    artifacts = observation.get("retentionArtifacts")
    require(isinstance(artifacts, list) and 1 <= len(artifacts) <= 1024, "Raw retention manifest missing; cleanup forbidden")
    require(observation.get("retentionSnapshotConsistent") is True, "Consistent journal/evidence snapshot not proved; cleanup forbidden")
    require(observation.get("artifactManifestSha256") == android_artifact_manifest_sha256(artifacts), "Raw retention manifest digest differs from Android wire manifest")
    paths = [retention_path(entry, artifact) for artifact in artifacts]
    require(len(set(paths)) == len(paths) and sum(item["bytes"] for item in artifacts) <= 256 * 1024 * 1024, "Duplicate or oversized raw retention set")
    require(any(item.get("role") == "CONSISTENT_JOURNAL_SNAPSHOT" for item in artifacts), "Raw consistent journal snapshot missing; cleanup forbidden")
    directory = transport.directory / safe_id(label)
    directory.mkdir()
    index = []
    for number, (artifact, relative) in enumerate(zip(artifacts, paths)):
        # Parent directories and leaf must be ordinary app-private objects;
        # no symlink is dereferenced merely because its spelling is contained.
        parts = relative.split("/")
        for end in range(1, len(parts) + 1):
            current = "/".join(parts[:end])
            kind = transport.run(["shell", "run-as", PACKAGE, "stat", "-c", "%F", current], "retention-stat",
                                 retry_readonly_connection_once=True).stdout.decode().strip()
            leaf_kind = "symbolic link" if artifact.get("kind") == "SYMLINK_TARGET_TEXT" else "regular file"
            require(kind == (leaf_kind if end == len(parts) else "directory"), "Nonregular retained artifact or unexpected symlink")
        if artifact.get("kind") == "SYMLINK_TARGET_TEXT":
            require(artifact["bytes"] <= 4096, "Unbounded symbolic link target text")
            data = transport.run(["exec-out", "run-as", PACKAGE, "readlink", "-n", relative], "retention-link-text",
                                 retry_readonly_connection_once=True).stdout
        else:
            data = transport.run(["exec-out", "run-as", PACKAGE, "head", "-c", str(artifact["bytes"] + 1), relative], "retention-read",
                                 timeout=60, retry_readonly_connection_once=True).stdout
        require(len(data) == artifact["bytes"] and hashlib.sha256(data).hexdigest() == artifact["sha256"], "Retained raw artifact changed or digest mismatch")
        target = directory / (f"{number:04d}.bin")
        with target.open("xb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        index.append(dict(artifact, hostFile=target.name))
    receipt = {"schema": "DORA_RECOVERY_HOST_RETENTION_V1", "source": plan["source"], "manifestSha256": digest_json(plan),
               "attemptId": entry["attemptId"], "artifactManifestSha256": android_artifact_manifest_sha256(artifacts),
               "retainedArtifactCount": len(index), "outcome": observation.get("classification", "PREPARED"), "index": index}
    path = directory / "retention-receipt.json"
    save_new(path, receipt)
    return {key: receipt[key] for key in ("schema", "source", "manifestSha256", "attemptId", "artifactManifestSha256", "retainedArtifactCount", "outcome")} | {"hostReceiptSha256": hashlib.sha256(path.read_bytes()).hexdigest()}


def run_kill(transport: OwnedAdbTransport, plan: dict[str, Any], entry: dict[str, Any], variant: str) -> dict[str, bool]:
    operation = "WRITE_UNTIL_BARRIER"
    request = request_for(plan, entry, operation, variant)
    command = instrument_command(transport.adb, transport.session["serial"], request, plan["source"]["commit"], digest_json(plan))
    argv = transport.argv(command[3:])
    save_new(transport.directory / "kill-instrument.command.json", {"argv": argv, "timeoutSeconds": 180})
    lines: queue.Queue[bytes | None] = queue.Queue()
    stdout_path = transport.directory / "kill-instrument.stdout"
    stderr_path = transport.directory / "kill-instrument.stderr"
    proofs = {key: False for key in KILL_PROOFS}
    proofs["scheduledBeforeExecution"] = proofs["sourceAndPreflightMatch"] = True
    with stdout_path.open("xb") as stdout, stderr_path.open("xb") as stderr:
        process = subprocess.Popen(argv, stdout=subprocess.PIPE, stderr=stderr, creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
        def copy_output() -> None:
            try:
                assert process.stdout is not None
                for line in iter(process.stdout.readline, b""):
                    stdout.write(line)
                    stdout.flush()
                    lines.put(line)
            finally:
                lines.put(None)
        reader = threading.Thread(target=copy_output, daemon=True)
        reader.start()
        try:
            deadline = time.monotonic() + 180
            barrier = None
            acknowledged_sequence = 0
            while time.monotonic() < deadline:
                try:
                    line = lines.get(timeout=min(1, max(0.01, deadline - time.monotonic())))
                except queue.Empty:
                    continue
                if line is None:
                    break
                for event in parse_events(line, entry, operation):
                    require(event["eventType"] != "ERROR", "Candidate failed before barrier")
                    if event["eventType"] == "READY" and event.get("eventAcknowledgementRequired") is True:
                        arguments = acknowledgement_arguments(entry, event, acknowledged_sequence)
                        # The immutable receipt is flushed through fsync before
                        # the Android writer can observe the release marker.
                        save_new(transport.directory / ("ack-" + str(event["sequence"]) + ".json"), {"event": event, "hostDurableBeforeRelease": True})
                        transport.run(arguments, "event-ack-" + str(event["sequence"]))
                        acknowledged_sequence = event["sequence"]
                    if event["eventType"] == "BARRIER":
                        require(barrier is None, "Duplicate barrier")
                        barrier = event
                        break
                if barrier:
                    break
            require(barrier is not None, "STRATUM_BARRIER_NOT_REACHED")
            pid = barrier["pid"]
            observed_pid = transport.run(["shell", "pidof", PACKAGE], "target-pid").stdout.decode()
            cmdline = transport.run(["exec-out", "run-as", PACKAGE, "cat", f"/proc/{pid}/cmdline"], "target-cmdline").stdout.decode()
            arguments = kill_arguments(pid, observed_pid, cmdline)
            # Recheck the exact target immediately before the signal. The app
            # is paused at its public barrier, and no recovery process starts.
            again = transport.run(["shell", "pidof", PACKAGE], "target-pid-presignal").stdout.decode()
            kill_arguments(pid, again, cmdline)
            proofs["targetAliveAtPublicBarrier"] = True
            transport.run(arguments, "sigkill")
            proofs["externalSigkillConfirmed"] = True
            death_deadline = time.monotonic() + 10
            while time.monotonic() < death_deadline:
                readback = transport.run(["shell", "pidof", PACKAGE], "death-confirmation", require_success=False)
                require(readback.returncode in (0, 1), "Death confirmation transport failed")
                if readback.returncode == 1 and not readback.stdout.strip():
                    proofs["deathConfirmedBeforeRecovery"] = True
                    break
                time.sleep(0.1)
            require(proofs["deathConfirmedBeforeRecovery"], "Death not independently confirmed")
            process.wait(timeout=15)
            reader.join(timeout=5)
            require(not reader.is_alive(), "Instrumentation output incomplete")
            # This proof concerns the externally paused pre-finalize barrier,
            # not a claim inferred from exit code alone.
            proofs["noGracefulFinalize"] = barrier.get("gracefulFinalizeCompleted") is False
            proofs["externalEnvelopeComplete"] = True
            save_new(transport.directory / "kill-native-exit.json", {"nativeExit": process.returncode, "kill": proofs, "barrier": barrier})
            require(all(proofs.values()), "Kill envelope incomplete")
            return proofs
        finally:
            if process.poll() is None:
                # Only this exact Popen child ADB client is reaped. No host
                # service, process tree, emulator or unidentified PID is killed.
                process.kill()
                process.wait(timeout=5)
            reader.join(timeout=5)
            save_new(transport.directory / "kill-client-final.json", {"nativeExit": process.returncode, "readerComplete": not reader.is_alive(), "kill": proofs})


def execute_one(root: Path, plan: dict[str, Any], gate: dict[str, Any], session: dict[str, Any], adb: Path, attempt: str, output: Path) -> dict[str, Any]:
    validate_plan(root, plan)
    validate_execution_gate(plan, gate, session)
    matching = [entry for entry in plan["entries"] if entry["attemptId"] == safe_id(attempt)]
    require(len(matching) == 1, "Unscheduled attempt identity")
    entry = matching[0]
    require(entry["environment"] == session["environment"], "Attempt not allocated to this environment")
    require(attempt in gate.get("supportedAttemptIds", []), "Android adapter support not reviewed for this attempt")
    require(not output.exists(), "Attempt evidence directory already exists; identity is consumed")
    output.mkdir(parents=True)
    save_new(output / "scheduled.json", {"manifestSha256": digest_json(plan), "entry": entry, "gate": gate, "session": session})
    result: dict[str, Any] = {"attemptId": attempt, "status": "UNTESTED", "candidateResult": None, "rawRetentionComplete": False, "retentionReceipts": [], "preFaultRetentionReceipts": [], "startedVariants": []}
    subresults = []
    try:
        for variant in entry["mutationVariants"]:
            result["rawRetentionComplete"] = False
            result["startedVariants"].append(variant)
            directory = output / safe_id(variant)
            directory.mkdir()
            transport = OwnedAdbTransport(adb, session, directory)
            transport.instrumentation_parser_sha256 = gate["instrumentationParserSha256"]
            transport.verify_device(plan["source"])
            def cleanup_admission(prepared: dict[str, Any]) -> None:
                nonlocal entry
                baseline = prepared.get("preCleanupOutcome", prepared)
                prefix = transport.extract_prefix(entry, baseline, "pre-cleanup")
                result["hostPreCleanupOracleEqual"] = compare_prefix(prefix, entry, baseline.get("recoveredEnd"))
                require(result["hostPreCleanupOracleEqual"] and baseline.get("authenticated") is True, "Pre-cleanup authenticated oracle missing")
                expected_plan = prepared.get("cleanupPlanSha256")
                hex_value(expected_plan, 64)
                plan_path = re.compile(r"files/campaign/" + re.escape(entry["attemptId"]) + r"/retention-[0-9]+/cleanup-plan\.json")
                matching_plans = [artifact for artifact in prepared.get("retentionArtifacts", []) if plan_path.fullmatch(artifact.get("relativePath", "")) and artifact.get("sha256") == expected_plan]
                require(len(matching_plans) == 1, "Exact immutable cleanup plan not in raw retention set")
                receipt = retain_evidence(transport, plan, entry, prepared, "before-cleanup-fault")
                result["preFaultRetentionReceipts"].append({"variant": variant, "receipt": receipt})
                entry = dict(entry, hostRetentionReceipt=receipt)
                result["preCleanupRetentionVerified"] = True
                result["preCleanupOutcome"] = baseline
                save_new(directory / "pre-cleanup-host-oracle.json", {"outcome": baseline, "oracleEqual": True, "retentionReceipt": receipt})
            if requires_external_kill(entry):
                if entry.get("caseId") in ("QUA-02", "QUA-03", "IDE-02", "CLN-01"):
                    prepared = run_operation(transport, plan, entry, "PREPARE", variant)
                    save_new(directory / "pre-kill-fixture.json", prepared)
                    if entry.get("caseId") == "CLN-01":
                        cleanup_admission(prepared)
                    else:
                        result["preFaultRetentionReceipts"].append({"variant": variant, "receipt": retain_evidence(transport, plan, entry, prepared, "before-fault")})
                proofs = run_kill(transport, plan, entry, variant)
                result["kill"] = proofs
                barrier = json.loads((directory / "kill-native-exit.json").read_bytes())["barrier"]
                result["hostAcceptedEnd"] = barrier["acceptedEnd"]
                result["hostCommittedEnd"] = barrier.get("committedEnd")
            else:
                prepared = run_operation(transport, plan, entry, "PREPARE", variant)
                if prepared.get("externalAnchor") is not None:
                    validate_external_anchor(prepared["externalAnchor"], entry)
                    entry = dict(entry, externalAnchor=prepared["externalAnchor"])
                    save_new(directory / "external-anchor.json", prepared["externalAnchor"])
                require(entry.get("caseId") not in ("RBK-01", "RBK-02") or "externalAnchor" in entry, "Rollback case requires independently retained pre-fault anchor")
                if entry.get("caseId") in ("CLN-02", "CLN-03"):
                    cleanup_admission(prepared)
                else:
                    result["preFaultRetentionReceipts"].append({"variant": variant, "receipt": retain_evidence(transport, plan, entry, prepared, "before-fault")})
                fault = run_operation(transport, plan, entry, "FAULT", variant)
                if fault.get("recoveryCheckpointSelection") is not None:
                    selection = fault["recoveryCheckpointSelection"]
                    validate_checkpoint_selection(prepared.get("recoveryCheckpointSelection"), selection, entry)
                    save_new(directory / "recovery-checkpoint-selection.json", {"before": prepared["recoveryCheckpointSelection"], "after": selection, "originalOracleRetained": True})
                    entry = dict(entry, recoveryCheckpointSelection=selection)
            result["status"] = "VALID"
            observed = run_operation(transport, plan, entry, "RECOVER", variant)
            if entry.get("caseId") in ("RBK-01", "RBK-02"):
                anchor = entry["externalAnchor"]
                actual = observed.get("rollbackObservation") or {}
                require(type(actual.get("generation")) is int, "Actual rollback publication generation missing")
                hex_value(actual.get("publicationCiphertextSha256"), 64)
                result["hostRollbackDetected"] = actual["generation"] < anchor["generation"] or actual["publicationCiphertextSha256"] != anchor["publicationCiphertextSha256"]
                require(observed.get("committedEnd") == anchor["committedEnd"], "Rollback lowered original external committed baseline")
                save_new(directory / "rollback-oracle.json", {"externalAnchor": anchor, "observed": actual, "detected": result["hostRollbackDetected"], "globalAntiRollbackClaimed": False})
            prefix = transport.extract_prefix(entry, observed)
            equal = compare_prefix(prefix, entry, observed.get("recoveredEnd"))
            first_receipt = observed.get("receiptIdentity")
            replay = run_operation(transport, plan, entry, "RECOVER", variant)
            replay_prefix = transport.extract_prefix(entry, replay, "replayed")
            replay_equal = compare_prefix(replay_prefix, entry, replay.get("recoveredEnd"))
            observed["repeatStable"] = (bool(first_receipt) and replay.get("receiptIdentity") == first_receipt
                                        and replay.get("recoveredEnd") == observed.get("recoveredEnd")
                                        and replay.get("classification") == observed.get("classification")
                                        and replay_equal and prefix.read_bytes() == replay_prefix.read_bytes())
            branch = dict(result, candidateResult=observed, hostOracleEqual=equal)
            assessment = evaluate_attempt(dict(entry, mutationVariants=[variant]), branch)
            subresults.append({"variant": variant, "result": branch, "assessment": assessment})
            save_new(directory / "result-before-cleanup.json", subresults[-1])
            # Cleanup is a named protocol operation after complete preservation;
            # it affects only this synthetic run and never uninstalls packages.
            entry = dict(entry, hostRetentionReceipt=retain_evidence(transport, plan, entry, replay, "before-final-cleanup"))
            result["retentionReceipts"].append({"variant": variant, "receipt": entry["hostRetentionReceipt"]})
            result["rawRetentionComplete"] = len(result["retentionReceipts"]) == len(result["startedVariants"])
            branch["rawRetentionComplete"] = result["rawRetentionComplete"]
            cleanup = run_operation(transport, plan, entry, "CLEANUP", variant)
            save_new(directory / "cleanup.json", cleanup)
            require(cleanup.get("cleanupComplete") is True, "Run-scoped cleanup not verified")
            branch["cleanupResult"] = "VERIFIED"
            subresults[-1]["assessment"] = evaluate_attempt(dict(entry, mutationVariants=[variant]), branch)
            save_new(directory / "result.json", subresults[-1])
            result.update(branch)
        result["mutationVariantResults"] = subresults
        if len(subresults) != len(entry["mutationVariants"]) and not any(s["assessment"]["verdict"] == "FAIL" for s in subresults):
            result["status"] = "UNTESTED"
    except (ValueError, OSError, subprocess.SubprocessError) as error:
        result["controllerError"] = str(error)
        result["cleanupResult"] = "UNVERIFIED_STOP_NO_AUTOMATIC_RETRY"
        # A confirmed valid kill with absent candidate output is a valid failed
        # candidate outcome. Before a confirmed signal, retain an explicit
        # incomplete external envelope; never silently consume replacement slots.
        if not all(result.get("kill", {}).get(key) is True for key in KILL_PROOFS) and result["status"] != "VALID":
            result.update(status="INVALID", invalidReason="EXTERNAL_CONTROLLER_OR_PREFLIGHT_EVIDENCE_ENVELOPE_INCOMPLETE_OR_SCHEMA_INVALID")
    finally:
        save_new(output / "attempt-result.json", result)
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    build = commands.add_parser("plan")
    build.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    build.add_argument("--phase", choices=("PHASE_A", "FULL_PHYSICAL"), required=True)
    for field in ("commit", "tree", "app-apk-sha256", "test-apk-sha256"):
        build.add_argument("--" + field, required=True)
    build.add_argument("--seed", type=int, required=True)
    build.add_argument("--output", type=Path, required=True)
    evaluate = commands.add_parser("evaluate")
    evaluate.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    evaluate.add_argument("--plan", type=Path, required=True)
    evaluate.add_argument("--ledger", type=Path, required=True)
    evaluate.add_argument("--output", type=Path, required=True)
    execute = commands.add_parser("execute-one", help="Consume one reviewed scheduled emulator attempt in an existing owned session")
    execute.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    execute.add_argument("--plan", type=Path, required=True)
    execute.add_argument("--gate", type=Path, required=True)
    execute.add_argument("--owner-session", type=Path, required=True)
    execute.add_argument("--adb", type=Path, required=True)
    execute.add_argument("--attempt", required=True)
    execute.add_argument("--output", type=Path, required=True)
    check_gate = commands.add_parser("check-gate", help="Check immutable execution prerequisites without device or process lifecycle operations")
    check_gate.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    check_gate.add_argument("--plan", type=Path, required=True)
    check_gate.add_argument("--gate", type=Path, required=True)
    check_gate.add_argument("--output", type=Path, required=True)
    check_gate.add_argument("--payload", choices=("CAMPAIGN", "SUPPLEMENTAL_SQLITE", "PLATFORM_PREREQUISITES", "JOURNAL_CONNECTIONS"), default="CAMPAIGN")
    args = parser.parse_args()
    if args.command == "plan":
        plan = build_plan(args.root, args.phase, {"commit": args.commit, "tree": args.tree,
                          "appApkSha256": args.app_apk_sha256, "testApkSha256": args.test_apk_sha256}, args.seed)
        save_new(args.output, plan)
        print(json.dumps({"manifestSha256": digest_json(plan), "entries": len(plan["entries"]), "executionAuthorized": False}))
    elif args.command == "evaluate":
        plan = json.loads(args.plan.read_bytes())
        validate_plan(args.root, plan)
        ledger = [json.loads(line) for line in args.ledger.read_text(encoding="utf-8").splitlines() if line.strip()]
        report = evaluate_campaign(plan, ledger)
        save_new(args.output, report)
        print(json.dumps({key: report[key] for key in ("verdict", "untested", "hardKillValid", "faultValid")}))
    elif args.command == "check-gate":
        plan = json.loads(args.plan.read_bytes())
        validate_plan(args.root, plan)
        validate_execution_gate(plan, json.loads(args.gate.read_bytes()), payload=args.payload)
        save_new(args.output, {"prerequisitesVerified": True, "payload": args.payload, "manifestSha256": digest_json(plan), "deviceOperations": False, "preflightResult": "NOT_EXECUTED"})
    else:
        plan = json.loads(args.plan.read_bytes())
        result = execute_one(args.root, plan, json.loads(args.gate.read_bytes()),
                             json.loads(args.owner_session.read_bytes()), args.adb, args.attempt, args.output)
        entry = next(entry for entry in plan["entries"] if entry["attemptId"] == args.attempt)
        assessment = evaluate_attempt(entry, result)
        save_new(args.output / "attempt-assessment.json", assessment)
        print(json.dumps({"attemptId": result["attemptId"], "status": result["status"], "verdict": assessment["verdict"], "automaticRetry": False}))
        return 0 if assessment["verdict"] == "PASS" else 2
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except (ValueError, KeyError, OSError, json.JSONDecodeError) as error:
        print("BLOCKED: " + str(error), file=sys.stderr)
        sys.exit(2)
