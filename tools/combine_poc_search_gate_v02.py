#!/usr/bin/env python3
"""Validate and aggregate physical POC-SEARCH-001 stage0-v0.2 paired checkpoints."""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import re
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_GATE_SET = ROOT / "docs/stage0/poc-search-gate-set-stage0-v0.2.json"
PROFILES = ("D1", "D2", "D3")
BUILDS = (1, 2, 3)
PAIR_ORDERS = {
    1: ["CONTROL", "INDEXED"],
    2: ["INDEXED", "CONTROL"],
    3: ["CONTROL", "INDEXED"],
}
OPERATION_CLASSES = {
    "ADD_CONVERSATION_100": "bulk-100",
    "UPDATE_SEGMENT_TEXT_1": "single-row",
    "UPDATE_CONVERSATION_FILTER_1": "single-row",
    "DELETE_SEGMENT_1": "single-row",
    "DELETE_CONVERSATION_100": "bulk-100",
}


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--checkpoint", type=Path, action="append", required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--expected-commit", required=True)
    parser.add_argument("--gate-set", type=Path, default=DEFAULT_GATE_SET)
    return parser.parse_args()


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def digest(path: Path) -> str:
    return "sha256:" + hashlib.sha256(path.read_bytes()).hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def nearest_rank(values: list[float], percentile: float) -> float:
    require(bool(values), "Cannot aggregate an empty sample set")
    require(0 < percentile <= 1, "Percentile must be in (0, 1]")
    ordered = sorted(values)
    return ordered[math.ceil(percentile * len(ordered)) - 1]


def validate_gate_set(
    gate_set: dict[str, Any],
    *,
    require_execution_authorized: bool,
) -> dict[str, Any]:
    require(gate_set["gateSetVersion"] == "stage0-v0.2", "Gate-set version drift")
    require(gate_set["status"] == "APPROVED", "Gate set is not approved")
    require(gate_set["selectedOptionId"] == "B", "Option B is not selected")
    require(gate_set["selectedOption"]["id"] == "B", "Selected option object drift")
    require(gate_set["historicalResultsAffected"] is False, "Prospective gate rewrites history")
    require(
        gate_set["independence"]["historicalDoraMeasurementsUsedToSetThresholds"] is False,
        "Gate threshold selection used historical Dora measurements",
    )
    if require_execution_authorized:
        require(
            gate_set["benchmarkExecutionAllowed"] is True
            and gate_set["executionAuthorization"]["status"] == "AUTHORIZED_BY_PROJECT_OWNER",
            "Measured execution is not authorized by the Project owner",
        )
    return gate_set["selectedOption"]["thresholds"]


def validate_environment(environment: dict[str, Any], profile: str) -> None:
    require(environment["kind"] == "physical", f"{profile} checkpoint is not physical")
    require(environment["buildType"] == "benchmark", "Formal build is not benchmark")
    require(environment["applicationDebuggable"] is False, "Formal build is debuggable")
    require(environment["profileableByShellDeclared"] is True, "Profileable declaration missing")
    require(environment["compilationMode"] == "full_aot_recorded", "Full/AOT state missing")
    require(environment["abi"] == "arm64-v8a", f"{profile} ABI is not arm64-v8a")
    require(environment["airplaneMode"] is True, f"{profile} airplane mode was not active")
    require(environment["screenInteractive"] is True, f"{profile} screen was not on")
    require(environment["plugged"] is False, f"{profile} device was plugged in")
    require(40 <= environment["batteryPercent"] <= 80, f"{profile} battery range drift")
    require(
        0 <= environment["thermalStatus"] <= 1,
        f"{profile} thermal status is unavailable or exceeded LIGHT",
    )
    require(45 <= environment["screenBrightnessRaw"] <= 57, f"{profile} brightness is not 20%")
    for key in ("windowAnimationScale", "transitionAnimationScale", "animatorDurationScale"):
        require(environment[key] == 0.0, f"{profile} animation scale {key} is not zero")
    require(bool(environment["buildFingerprint"]), f"{profile} fingerprint missing")
    require(bool(environment["sqliteVersion"]), f"{profile} sqlite_version() missing")
    require(bool(environment["sqliteCompileOptions"]), f"{profile} SQLite compile options missing")


def validate_samples(
    observation: dict[str, Any],
    expected_visibility: bool,
) -> dict[str, dict[str, list[dict[str, Any]]]]:
    operations = {value["operationClass"]: value for value in observation["operations"]}
    require(set(operations) == set(OPERATION_CLASSES), "Operation-class inventory drift")
    for operation_id, group in OPERATION_CLASSES.items():
        value = operations[operation_id]
        require(value["group"] == group, f"{operation_id} group drift")
        require(len(value["warmupSamples"]) == 10, f"{operation_id} warm-up count drift")
        require(len(value["measuredSamples"]) == 100, f"{operation_id} repetition count drift")
        for sample in value["warmupSamples"] + value["measuredSamples"]:
            require(sample["commitNanos"] >= 0, f"{operation_id} negative commit time")
            if expected_visibility and sample["correctnessPassed"]:
                require(
                    sample["visibilityNanos"] is not None and sample["visibilityNanos"] >= 0,
                    f"{operation_id} successful indexed sample has no visibility time",
                )
            if not expected_visibility:
                require(sample["visibilityNanos"] is None, "Control sample reports FTS visibility")
    return operations


def validate_complete_checkpoint(
    checkpoint: dict[str, Any],
    expected_gate_digest: str,
    expected_commit: str,
) -> tuple[str, int]:
    require(checkpoint["checkpointSchemaVersion"] == 2, "Checkpoint schema drift")
    require(checkpoint["pocId"] == "POC-SEARCH-001", "Checkpoint PoC drift")
    require(checkpoint["gateSetVersion"] == "stage0-v0.2", "Checkpoint gate version drift")
    require(checkpoint["gateSetSha256"] == expected_gate_digest, "Checkpoint gate digest drift")
    require(checkpoint["selectedOptionId"] == "B", "Checkpoint option drift")
    require(checkpoint["benchmarkExecutionAllowedAtBuild"] is True, "Harness build was blocked")
    require(checkpoint["formalGateEvidence"] is True, "Smoke output cannot be gate evidence")
    require(checkpoint["commit"] == expected_commit, "Checkpoint commit drift")
    require(checkpoint["checkpointStatus"] in {"COMPLETE", "FAIL"}, "Checkpoint status drift")
    require(isinstance(checkpoint["complete"], bool), "Checkpoint completeness marker drift")
    if not checkpoint["complete"]:
        require(checkpoint["checkpointStatus"] == "FAIL", "Incomplete checkpoint is not FAIL")
        require(bool(checkpoint.get("failure", {}).get("code")), "Incomplete FAIL code missing")
        require(
            bool(re.fullmatch(r"[0-9a-f]{64}", checkpoint["failure"].get("messageSha256", ""))),
            "Incomplete FAIL message digest missing",
        )
    else:
        require(isinstance(checkpoint.get("allCorrect"), bool), "Complete checkpoint result missing")
        expected_status = "COMPLETE" if checkpoint["allCorrect"] else "FAIL"
        require(checkpoint["checkpointStatus"] == expected_status, "Checkpoint result/status drift")
    config = checkpoint["config"]
    profile = config["profileId"]
    build = config["freshBuildOrdinal"]
    require(profile in PROFILES, f"Unexpected profile {profile}")
    require(build in BUILDS, f"Unexpected build ordinal {build}")
    require(checkpoint["pairOrder"] == PAIR_ORDERS[build], f"Build {build} pair order drift")
    require(config["conversationCount"] == 10_000, "Conversation scale drift")
    require(config["transcriptSegmentCount"] == 1_000_000, "Segment scale drift")
    require(config["segmentsPerConversation"] == 100, "Fixture cardinality drift")
    require(config["warmupsPerClass"] == 10, "Warm-up contract drift")
    require(config["measuredOperationsPerClass"] == 100, "Repetition contract drift")
    require(config["smokeOnly"] is False, "Smoke checkpoint cannot enter aggregation")
    require(config["cooldownMinutesBeforeFreshBuild"] >= 10, "Cooldown contract drift")
    validate_environment(checkpoint["environment"], profile)
    return profile, build


def checkpoint_failures(checkpoint: dict[str, Any]) -> dict[str, int]:
    if not checkpoint.get("complete"):
        return {
            "incompleteCheckpoints": 1,
            "incorrectOperations": 0,
            "staleSuccessfulResponses": 0,
            "candidateCrashes": 1,
            "mappingErrors": 0,
            "normalizationOrPairingErrors": 1,
        }
    control = checkpoint["control"]
    indexed = checkpoint["indexed"]
    incorrect = 0
    stale = 0
    crashes = 0
    for side, observation in (("control", control), ("indexed", indexed)):
        operations = validate_samples(observation, side == "indexed")
        for value in operations.values():
            for sample in value["warmupSamples"] + value["measuredSamples"]:
                incorrect += int(not sample["correctnessPassed"])
                stale += int(sample["staleSuccessfulResponse"])
                if side == "indexed":
                    crashes += int(sample["crashed"])
    mapping_errors = sum(
        int(indexed.get(key) or 0)
        for key in ("finalMissingCanonicalMappings", "finalMissingIndexRows")
    ) + sum(
        int(indexed["storage"].get(key) or 0)
        for key in ("missingCanonicalMappings", "missingIndexRows")
    )
    normalization = int(
        not checkpoint["storageAndPairingCorrect"]
        or not control["deletedAfterRun"]
        or not indexed["deletedAfterRun"]
    )
    return {
        "incompleteCheckpoints": 0,
        "incorrectOperations": incorrect,
        "staleSuccessfulResponses": stale,
        "candidateCrashes": crashes,
        "mappingErrors": mapping_errors,
        "normalizationOrPairingErrors": normalization,
    }


def sum_failures(values: list[dict[str, int]]) -> dict[str, int]:
    keys = values[0].keys() if values else ()
    return {key: sum(value[key] for value in values) for key in keys}


def aggregate_complete(
    checkpoints: list[dict[str, Any]],
    thresholds: dict[str, Any],
) -> tuple[dict[str, Any], list[dict[str, Any]]]:
    storage_rows: list[dict[str, Any]] = []
    profiles: dict[str, Any] = {}
    for profile in PROFILES:
        profile_checkpoints = [value for value in checkpoints if value["config"]["profileId"] == profile]
        class_rows: dict[str, Any] = {}
        for operation_id, group in OPERATION_CLASSES.items():
            control_nanos: list[int] = []
            indexed_nanos: list[int] = []
            visibility_nanos: list[int] = []
            deltas_ms: list[float] = []
            for checkpoint in sorted(profile_checkpoints, key=lambda value: value["config"]["freshBuildOrdinal"]):
                control = {value["operationClass"]: value for value in checkpoint["control"]["operations"]}[operation_id]
                indexed = {value["operationClass"]: value for value in checkpoint["indexed"]["operations"]}[operation_id]
                for control_sample, indexed_sample in zip(
                    control["measuredSamples"], indexed["measuredSamples"], strict=True
                ):
                    control_nanos.append(control_sample["commitNanos"])
                    indexed_nanos.append(indexed_sample["commitNanos"])
                    if indexed_sample["visibilityNanos"] is not None:
                        visibility_nanos.append(indexed_sample["visibilityNanos"])
                    deltas_ms.append(
                        (indexed_sample["commitNanos"] - control_sample["commitNanos"]) / 1_000_000.0
                    )
            require(len(control_nanos) == 300, f"{profile}/{operation_id} control sample count drift")
            require(len(indexed_nanos) == 300, f"{profile}/{operation_id} indexed sample count drift")
            require(len(visibility_nanos) == 300, f"{profile}/{operation_id} visibility sample count drift")
            class_rows[operation_id] = {
                "group": group,
                "sampleCount": 300,
                "maintenanceDeltaP95Ms": nearest_rank(deltas_ms, 0.95),
                "maintenanceDeltaP99Ms": nearest_rank(deltas_ms, 0.99),
                "indexedCommitP95Ms": nearest_rank([value / 1_000_000.0 for value in indexed_nanos], 0.95),
                "indexedCommitP99Ms": nearest_rank([value / 1_000_000.0 for value in indexed_nanos], 0.99),
                "visibilityP95Ms": nearest_rank([value / 1_000_000.0 for value in visibility_nanos], 0.95),
                "visibilityP99Ms": nearest_rank([value / 1_000_000.0 for value in visibility_nanos], 0.99),
            }
        profiles[profile] = {"operationClasses": class_rows}
        for checkpoint in profile_checkpoints:
            storage_rows.append(
                {
                    "profileId": profile,
                    "freshBuildOrdinal": checkpoint["config"]["freshBuildOrdinal"],
                    **checkpoint["storageDelta"],
                }
            )

    maximums = {
        "indexIncrementalBytes": max(value["indexIncrementalBytes"] for value in storage_rows),
        "indexOverheadRatio": max(value["indexOverheadRatio"] for value in storage_rows),
        "indexOverheadBytesPerSegment": max(value["indexOverheadBytesPerSegment"] for value in storage_rows),
        "singleRowMaintenanceDeltaP95Ms": max(
            row["maintenanceDeltaP95Ms"]
            for profile in profiles.values()
            for row in profile["operationClasses"].values()
            if row["group"] == "single-row"
        ),
        "bulk100MaintenanceDeltaP95Ms": max(
            row["maintenanceDeltaP95Ms"]
            for profile in profiles.values()
            for row in profile["operationClasses"].values()
            if row["group"] == "bulk-100"
        ),
        "indexedCommitP99Ms": max(
            row["indexedCommitP99Ms"]
            for profile in profiles.values()
            for row in profile["operationClasses"].values()
        ),
        "visibilityP95Ms": max(
            row["visibilityP95Ms"]
            for profile in profiles.values()
            for row in profile["operationClasses"].values()
        ),
        "visibilityP99Ms": max(
            row["visibilityP99Ms"]
            for profile in profiles.values()
            for row in profile["operationClasses"].values()
        ),
    }
    predicates = [
        ("maxIndexIncrementalBytes", "indexIncrementalBytes"),
        ("maxIndexOverheadRatio", "indexOverheadRatio"),
        ("maxIndexOverheadBytesPerSegment", "indexOverheadBytesPerSegment"),
        ("maxSingleRowMaintenanceDeltaP95Ms", "singleRowMaintenanceDeltaP95Ms"),
        ("maxBulk100MaintenanceDeltaP95Ms", "bulk100MaintenanceDeltaP95Ms"),
        ("maxIndexedCommitP99Ms", "indexedCommitP99Ms"),
        ("maxVisibilityP95Ms", "visibilityP95Ms"),
        ("maxVisibilityP99Ms", "visibilityP99Ms"),
    ]
    outcomes = [
        {
            "thresholdId": threshold_id,
            "observedMetric": metric,
            "observed": maximums[metric],
            "maximumAllowed": thresholds[threshold_id],
            "passed": maximums[metric] <= thresholds[threshold_id],
        }
        for threshold_id, metric in predicates
    ]
    return {
        "storageBuilds": storage_rows,
        "profiles": profiles,
        "maximumNoAveraging": maximums,
        "aggregation": "nearest_rank_ceil_p_times_n; maximum across classes/profiles; no outlier removal",
    }, outcomes


def evaluate_checkpoints(
    checkpoints: list[dict[str, Any]],
    gate_set: dict[str, Any],
    gate_set_digest: str,
    expected_commit: str,
    *,
    require_execution_authorized: bool = True,
) -> dict[str, Any]:
    require(bool(re.fullmatch(r"[0-9a-f]{40}", expected_commit)), "Expected commit must be a full SHA")
    thresholds = validate_gate_set(
        gate_set,
        require_execution_authorized=require_execution_authorized,
    )
    seen: set[tuple[str, int]] = set()
    complete: list[dict[str, Any]] = []
    failures: list[dict[str, int]] = []
    inputs: list[dict[str, Any]] = []
    for checkpoint in checkpoints:
        profile, build = validate_complete_checkpoint(checkpoint, gate_set_digest, expected_commit)
        require((profile, build) not in seen, f"Duplicate checkpoint {profile}/{build}")
        seen.add((profile, build))
        failures.append(checkpoint_failures(checkpoint))
        if checkpoint.get("complete"):
            complete.append(checkpoint)
        inputs.append({"profileId": profile, "freshBuildOrdinal": build})

    expected = {(profile, build) for profile in PROFILES for build in BUILDS}
    missing = sorted(expected - seen)
    totals = sum_failures(failures)
    correctness_triggered = any(totals.values())
    aggregate: dict[str, Any] | None = None
    threshold_outcomes: list[dict[str, Any]] = []
    if not missing and len(complete) == 9 and not correctness_triggered:
        aggregate, threshold_outcomes = aggregate_complete(complete, thresholds)
    threshold_triggered = any(not value["passed"] for value in threshold_outcomes)
    if correctness_triggered or threshold_triggered:
        status = "FAIL"
        decision = "NO_GO"
    elif missing or len(complete) != 9:
        status = "INCONCLUSIVE"
        decision = "BLOCKED"
    else:
        status = "PASS"
        decision = "GO_STAGE0_EVALUATION_ONLY"
    return {
        "schemaVersion": 1,
        "pocId": "POC-SEARCH-001",
        "gateSetVersion": "stage0-v0.2",
        "gateSetSha256": gate_set_digest,
        "selectedOptionId": "B",
        "commit": expected_commit,
        "inputCheckpoints": sorted(inputs, key=lambda value: (value["profileId"], value["freshBuildOrdinal"])),
        "missingProfileBuilds": [
            {"profileId": profile, "freshBuildOrdinal": build} for profile, build in missing
        ],
        "failureCounts": totals,
        "aggregate": aggregate,
        "thresholdOutcomes": threshold_outcomes,
        "result": {"status": status, "decision": decision},
        "nonClaims": [
            "This Stage 0 evaluation does not admit SQLite FTS4 or any dependency to production.",
            "Production Legal and independent production Security approvals remain separate.",
            "Missing D1, D2, or D3 evidence can never produce PASS.",
        ],
    }


def main() -> int:
    args = parse_args()
    gate_set = read_json(args.gate_set)
    gate_digest = digest(args.gate_set)
    checkpoints = [read_json(path) for path in args.checkpoint]
    checkpoint_paths = {
        (value["config"]["profileId"], value["config"]["freshBuildOrdinal"]): path
        for value, path in zip(checkpoints, args.checkpoint, strict=True)
    }
    result = evaluate_checkpoints(checkpoints, gate_set, gate_digest, args.expected_commit)
    result["inputCheckpoints"] = [
        {
            **entry,
            "locator": str(path),
            "sha256": digest(path),
        }
        for entry in result["inputCheckpoints"]
        for path in [checkpoint_paths[(entry["profileId"], entry["freshBuildOrdinal"])]]
    ]
    write_json(args.output, result)
    print(f"Wrote {args.output} ({result['result']['status']})")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as error:
        print(f"FAIL {error}")
        raise SystemExit(1)
