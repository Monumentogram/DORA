#!/usr/bin/env python3
"""Combine independently retained POC-SEARCH-001 phase checkpoints."""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--query", type=Path, required=True)
    parser.add_argument("--rebuild", type=Path, required=True)
    parser.add_argument("--secondary", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--expected-commit")
    return parser.parse_args()


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def validate_checkpoint(
    checkpoint: dict[str, Any],
    expected_phase: str,
    expected_commit: str | None,
) -> None:
    require(checkpoint.get("checkpointSchemaVersion") == 1, "Checkpoint schema mismatch")
    require(checkpoint.get("pocId") == "POC-SEARCH-001", "Checkpoint PoC mismatch")
    require(checkpoint.get("checkpoint") == expected_phase, f"Expected {expected_phase} checkpoint")
    require(
        checkpoint.get("completedPhases") == [expected_phase],
        f"{expected_phase} completion marker mismatch",
    )
    commit = checkpoint.get("commit", "")
    require(bool(re.fullmatch(r"[0-9a-f]{40}", commit)), "Checkpoint commit must be a full SHA")
    if expected_commit:
        require(commit == expected_commit, f"Checkpoint commit {commit} != {expected_commit}")
    require(
        checkpoint.get("temporaryDatabaseDeleted") is True,
        f"{expected_phase} checkpoint retained a benchmark database",
    )


def validate_preparation(preparation: dict[str, Any], label: str) -> None:
    require(preparation["conversationCount"] == 10_000, f"{label} conversation count drift")
    require(preparation["transcriptCount"] == 1_000_000, f"{label} transcript count drift")
    require(preparation["ftsCount"] == 1_000_000, f"{label} FTS count drift")
    require(preparation["sqliteIntegrity"] == "ok", f"{label} integrity failure")
    require(preparation["missingCanonicalMappings"] == 0, f"{label} canonical mapping loss")
    require(preparation["missingIndexRows"] == 0, f"{label} missing FTS rows")
    require(preparation["duplicateCanonicalRows"] == 0, f"{label} duplicate canonical rows")
    require(
        preparation["expectedLogicalDigest"] == preparation["databaseLogicalDigest"],
        f"{label} logical dataset digest mismatch",
    )


def combine_memory(checkpoints: list[dict[str, Any]]) -> dict[str, Any]:
    memories = [checkpoint["memory"] for checkpoint in checkpoints]
    rss_values = [value["peakRssMb"] for value in memories if value.get("peakRssMb") is not None]
    return {
        "peakPssMb": max(value["peakPssMb"] for value in memories),
        "peakNativeHeapMb": max(value["peakNativeHeapMb"] for value in memories),
        "peakManagedHeapMb": max(value["peakManagedHeapMb"] for value in memories),
        "peakRssMb": max(rss_values) if rss_values else None,
        "sampleCount": sum(value["sampleCount"] for value in memories),
    }


def environment_identity(environment: dict[str, Any]) -> dict[str, Any]:
    keys = (
        "kind",
        "manufacturer",
        "model",
        "device",
        "product",
        "buildFingerprint",
        "securityPatch",
        "androidApi",
        "abi",
        "ramMb",
        "pageSizeBytes",
        "sqliteVersion",
        "roomVersion",
        "ftsCreateSql",
        "buildType",
        "monotonicClock",
    )
    return {key: environment.get(key) for key in keys}


def combine_checkpoints(
    query: dict[str, Any],
    rebuild: dict[str, Any],
    secondary: dict[str, Any],
    expected_commit: str | None = None,
) -> dict[str, Any]:
    checkpoints = [query, rebuild, secondary]
    for checkpoint, phase in zip(checkpoints, ("query", "rebuild", "secondary"), strict=True):
        validate_checkpoint(checkpoint, phase, expected_commit)

    for key in ("commit", "harnessVersion", "manifests", "campaign"):
        require(
            all(checkpoint[key] == query[key] for checkpoint in checkpoints[1:]),
            f"Checkpoint {key} drift",
        )
    query_environment = environment_identity(query["androidEnvironment"])
    require(
        all(
            environment_identity(checkpoint["androidEnvironment"]) == query_environment
            for checkpoint in checkpoints[1:]
        ),
        "Checkpoint Android/SQLite environment drift",
    )

    validate_preparation(query["primaryPreparation"], "query primary")
    validate_preparation(rebuild["rebuildPreparation"], "rebuild primary")
    validate_preparation(secondary["secondaryPreparation"], "secondary")
    require(query["queryPlan"]["accepted"] is True, "Target query plan is not accepted")
    require(query["queryPlan"]["ftsIsDrivingTable"] is True, "FTS4 is not the driving table")
    require(
        query["queryPlan"]["canonicalLookupsUseRowId"] is True,
        "Canonical lookups do not use rowid",
    )
    require(query["baselineCorrectness"]["allMatched"] is True, "Query baseline failed")
    require(
        rebuild["rebuildBaselineCorrectness"]["allMatched"] is True,
        "Rebuild baseline failed",
    )
    require(secondary["secondaryCorrectness"]["allMatched"] is True, "Secondary baseline failed")
    require(query["latency"]["measuredOperations"] == 1_020, "Frozen latency count drift")
    require(len(rebuild["rebuild"]["passes"]) == 2, "Expected two full-scale rebuilds")
    require(
        [value["id"] for value in rebuild["rebuild"]["passes"]]
        == ["REBUILD-1", "REBUILD-2"],
        "Rebuild pass identity drift",
    )
    require(rebuild["mutation"]["manifestId"] == query["manifests"]["mutationId"], "Mutation drift")

    baseline_query_digest = query["baselineCorrectness"]["queryResultSha256"]
    require(
        rebuild["rebuildBaselineCorrectness"]["queryResultSha256"] == baseline_query_digest,
        "Independent rebuild baseline query digest drift",
    )
    require(
        rebuild["rebuild"]["baselineQueryResultSha256"] == baseline_query_digest,
        "Rebuild comparison baseline query digest drift",
    )
    require(
        rebuild["rebuildBaselineIndexLogicalSha256"] == query["primaryIndexLogicalSha256"],
        "Independent rebuild baseline index digest drift",
    )
    require(
        rebuild["rebuild"]["baselineIndexLogicalSha256"] == query["primaryIndexLogicalSha256"],
        "Rebuild comparison baseline index digest drift",
    )

    primary = query["primaryPreparation"]
    second = secondary["secondaryPreparation"]
    cross_build = {
        "primaryDatasetLogicalSha256": primary["databaseLogicalDigest"],
        "secondaryDatasetLogicalSha256": second["databaseLogicalDigest"],
        "primaryIndexLogicalSha256": query["primaryIndexLogicalSha256"],
        "secondaryIndexLogicalSha256": secondary["secondaryIndexLogicalSha256"],
        "primaryQueryResultSha256": baseline_query_digest,
        "secondaryQueryResultSha256": secondary["secondaryCorrectness"]["queryResultSha256"],
        "countsMatched": (
            primary["conversationCount"] == second["conversationCount"]
            and primary["transcriptCount"] == second["transcriptCount"]
            and primary["ftsCount"] == second["ftsCount"]
        ),
        "logicalDatasetMatched": (
            primary["databaseLogicalDigest"] == second["databaseLogicalDigest"]
        ),
        "logicalIndexMatched": (
            query["primaryIndexLogicalSha256"] == secondary["secondaryIndexLogicalSha256"]
        ),
        "queryResultsMatched": (
            query["baselineCorrectness"]["allMatched"]
            and secondary["secondaryCorrectness"]["allMatched"]
            and baseline_query_digest == secondary["secondaryCorrectness"]["queryResultSha256"]
        ),
    }
    cross_build["deterministic"] = all(
        cross_build[key]
        for key in (
            "countsMatched",
            "logicalDatasetMatched",
            "logicalIndexMatched",
            "queryResultsMatched",
        )
    )

    return {
        "schemaVersion": 1,
        "pocId": "POC-SEARCH-001",
        "harnessVersion": query["harnessVersion"],
        "commit": query["commit"],
        "generatedAt": max(checkpoint["generatedAt"] for checkpoint in checkpoints),
        "durationSeconds": sum(checkpoint["durationSeconds"] for checkpoint in checkpoints),
        "manifests": query["manifests"],
        "campaign": query["campaign"],
        "androidEnvironment": query["androidEnvironment"],
        "primaryPreparation": primary,
        "secondaryPreparation": second,
        "baselineCorrectness": query["baselineCorrectness"],
        "secondaryCorrectness": secondary["secondaryCorrectness"],
        "latency": query["latency"],
        "rebuild": rebuild["rebuild"],
        "mutation": rebuild["mutation"],
        "crossBuildDeterminism": cross_build,
        "memory": combine_memory(checkpoints),
        "queryPlan": query["queryPlan"],
        "checkpointExecution": {
            "mode": "three-independent-jobs",
            "completedPhases": ["query", "rebuild", "secondary"],
            "phaseDurationsSeconds": {
                checkpoint["checkpoint"]: checkpoint["durationSeconds"]
                for checkpoint in checkpoints
            },
        },
        "temporaryDatabasesDeleted": True,
    }


def main() -> int:
    args = parse_args()
    combined = combine_checkpoints(
        read_json(args.query),
        read_json(args.rebuild),
        read_json(args.secondary),
        args.expected_commit,
    )
    write_json(args.output, combined)
    print(f"Combined POC-SEARCH-001 checkpoints into {args.output}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"FAIL {error}")
        raise SystemExit(1)
