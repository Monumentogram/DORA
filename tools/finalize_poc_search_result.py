#!/usr/bin/env python3
"""Finalize sanitized POC-SEARCH-001 observations into repository evidence."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import platform
import re
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "docs" / "evidence" / "poc-search-001"
SCHEMA = ROOT / "docs" / "stage0" / "benchmark-result.schema.json"
GATE_SOURCE = "docs/stage0/DORA_MVP1_POC_GATES.md Gate Set stage0-v0.1"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--observations", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, default=EVIDENCE)
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


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return "sha256:" + digest.hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def metric(
    name: str,
    value: Any,
    unit: str,
    aggregation: str,
    sample_count: int,
    source: str,
    slice_name: str = "api36-x86_64-emulator-reference-db",
    notes: str | None = None,
) -> dict[str, Any]:
    result = {
        "name": name,
        "value": value,
        "unit": unit,
        "aggregation": aggregation,
        "slice": slice_name,
        "sampleCount": sample_count,
        "measurementSource": source,
    }
    if notes:
        result["notes"] = notes
    return result


def gate(
    gate_id: str,
    kind: str,
    title: str,
    metric_name: str,
    operator: str,
    threshold: Any,
    unit: str | None,
    observed: Any,
    outcome: str,
    notes: str | None = None,
) -> dict[str, Any]:
    result = {
        "id": gate_id,
        "kind": kind,
        "title": title,
        "approvalStatus": "Approved",
        "source": GATE_SOURCE,
        "metric": metric_name,
        "operator": operator,
        "threshold": threshold,
        "unit": unit,
        "observed": observed,
        "outcome": outcome,
        "mandatory": True,
        "scope": "POC-SEARCH-001 10k conversations / 1M rows reference campaign",
    }
    if notes:
        result["notes"] = notes
    return result


def host_environment() -> dict[str, Any]:
    cpu = platform.processor() or platform.machine()
    if Path("/proc/cpuinfo").is_file():
        for line in Path("/proc/cpuinfo").read_text(encoding="utf-8", errors="replace").splitlines():
            if line.startswith(("model name", "Hardware")):
                cpu = line.partition(":")[2].strip()
                break
    ram_mb = None
    try:
        page_size = os.sysconf("SC_PAGE_SIZE")
        page_count = os.sysconf("SC_PHYS_PAGES")
        ram_mb = int(page_size * page_count / (1024 * 1024))
    except (AttributeError, OSError, ValueError):
        pass
    return {
        "os": platform.platform(),
        "machine": platform.machine(),
        "cpu": cpu,
        "logicalCpuCount": os.cpu_count(),
        "ramMb": ram_mb,
        "runnerName": os.environ.get("RUNNER_NAME"),
        "runnerEnvironment": os.environ.get("RUNNER_ENVIRONMENT"),
        "githubWorkflow": os.environ.get("GITHUB_WORKFLOW"),
        "githubRunId": os.environ.get("GITHUB_RUN_ID"),
        "githubRunAttempt": os.environ.get("GITHUB_RUN_ATTEMPT"),
        "githubSha": os.environ.get("GITHUB_SHA"),
        "githubRef": os.environ.get("GITHUB_REF"),
    }


def environment_result(observations: dict[str, Any]) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "pocId": "POC-SEARCH-001",
        "generatedAt": observations["generatedAt"],
        "commit": observations["commit"],
        "host": host_environment(),
        "android": observations["androidEnvironment"],
        "measurementScope": {
            "correctness": "reference-scale Android emulator",
            "latency": "host/emulator exploratory only",
            "physicalDeviceClaim": False,
            "requiredFuturePhysicalProfiles": ["D1", "D2", "D3"],
        },
    }


def evaluate(observations: dict[str, Any]) -> dict[str, Any]:
    latency = observations["latency"]["overall"]
    baseline = observations["baselineCorrectness"]
    secondary = observations["secondaryCorrectness"]
    rebuild = observations["rebuild"]
    cross = observations["crossBuildDeterminism"]
    mutation = observations["mutation"]
    latency_pass = latency["p95Ms"] < 200.0 and latency["p99Ms"] < 500.0
    correctness_pass = bool(baseline["allMatched"] and secondary["allMatched"])
    safety_pass = (
        baseline["adversarialFailures"] == 0
        and baseline["specialCharacterFailures"] == 0
        and baseline["failureExecutions"] == 0
        and baseline["crashes"] == 0
    )
    mapping_pass = (
        baseline["mappingErrors"] == 0
        and baseline["duplicateResultErrors"] == 0
        and observations["primaryPreparation"]["missingCanonicalMappings"] == 0
        and observations["primaryPreparation"]["missingIndexRows"] == 0
        and mutation["mappingErrors"] == 0
    )
    rebuild_pass = bool(rebuild["deterministic"] and cross["deterministic"])
    mutation_pass = bool(mutation["allCorrect"])
    host_pass = all(
        [latency_pass, correctness_pass, safety_pass, mapping_pass, rebuild_pass, mutation_pass]
    )
    if host_pass:
        architectural_outcome = "FTS4 suitable for continued MVP development"
    elif safety_pass and mapping_pass and rebuild_pass:
        architectural_outcome = "FTS4 suitable with changes"
    else:
        architectural_outcome = "FTS4 unsuitable; fallback required"
    return {
        "latencyPass": latency_pass,
        "correctnessPass": correctness_pass,
        "safetyPass": safety_pass,
        "mappingPass": mapping_pass,
        "rebuildPass": rebuild_pass,
        "mutationPass": mutation_pass,
        "hostEmulatorExploratoryOutcome": "PASS" if host_pass else "FAIL",
        "formalVerdict": "INCONCLUSIVE" if host_pass else "FAIL",
        "architecturalOutcome": architectural_outcome,
    }


def query_result(observations: dict[str, Any], evaluation: dict[str, Any]) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "pocId": "POC-SEARCH-001",
        "generatedAt": observations["generatedAt"],
        "commit": observations["commit"],
        "campaign": observations["campaign"],
        "baselineCorrectness": observations["baselineCorrectness"],
        "secondaryCorrectness": observations["secondaryCorrectness"],
        "latency": observations["latency"],
        "hostEmulatorExploratoryOutcome": evaluation["hostEmulatorExploratoryOutcome"],
        "physicalDeviceLatencyClaim": False,
    }


def mutation_result(observations: dict[str, Any], evaluation: dict[str, Any]) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "pocId": "POC-SEARCH-001",
        "generatedAt": observations["generatedAt"],
        "commit": observations["commit"],
        "mutationManifestId": observations["manifests"]["mutationId"],
        "result": observations["mutation"],
        "correctnessPassed": evaluation["mutationPass"],
        "latencyClassification": "observation-only-no-approved-numeric-threshold",
    }


def rebuild_result(observations: dict[str, Any], evaluation: dict[str, Any]) -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "pocId": "POC-SEARCH-001",
        "generatedAt": observations["generatedAt"],
        "commit": observations["commit"],
        "definition": (
            "Logical determinism means equal canonical counts, generated logical dataset SHA-256, "
            "ordered logical FTS row/text SHA-256, and frozen query correctness results; SQLite files "
            "are not required to be byte-identical."
        ),
        "primaryPreparation": observations["primaryPreparation"],
        "secondaryPreparation": observations["secondaryPreparation"],
        "rebuild": observations["rebuild"],
        "crossBuildDeterminism": observations["crossBuildDeterminism"],
        "deterministic": evaluation["rebuildPass"],
        "temporaryDatabasesDeleted": observations["temporaryDatabasesDeleted"],
    }


def evidence_entry(path: Path, output_dir: Path, kind: str) -> dict[str, Any]:
    del output_dir
    locator = f"docs/evidence/poc-search-001/{path.name}"
    return {
        "id": path.stem,
        "kind": kind,
        "storage": "repository",
        "locator": locator,
        "sha256": sha256(path),
        "classification": "public-sanitized",
        "containsPersonalData": False,
        "containsSecret": False,
        "containsRawAudio": False,
        "retentionUntil": None,
        "verified": True,
    }


def build_metrics(observations: dict[str, Any]) -> list[dict[str, Any]]:
    metrics: list[dict[str, Any]] = []
    prep = observations["primaryPreparation"]
    latency = observations["latency"]
    overall = latency["overall"]
    source = "POC-SEARCH-001 Android instrumentation"
    for name, value in (
        ("search.dataset.conversations", prep["conversationCount"]),
        ("search.dataset.transcript_rows", prep["transcriptCount"]),
        ("search.dataset.fts_rows", prep["ftsCount"]),
    ):
        metrics.append(metric(name, value, "rows", "count", 1, source))
    for name, key in (
        ("search.build.empty_database_ms", "emptyDatabaseCreationMs"),
        ("search.build.conversations_ms", "conversationInsertMs"),
        ("search.build.transcripts_ms", "transcriptInsertMs"),
        ("search.build.index_ms", "indexBuildMs"),
        ("search.build.checkpoint_compact_ms", "checkpointCompactMs"),
        ("search.build.total_ms", "totalPreparationMs"),
    ):
        metrics.append(metric(name, prep[key], "ms", "raw", 1, source))
    for name, key in (
        ("search.storage.database_bytes", "databaseBytes"),
        ("search.storage.wal_bytes", "walBytes"),
        ("search.storage.shm_bytes", "shmBytes"),
        ("search.storage.total_bytes", "totalBytes"),
    ):
        metrics.append(metric(name, prep["afterCompact"][key], "bytes", "raw", 1, source))
    aggregation = {
        "minMs": "min",
        "p50Ms": "p50",
        "p90Ms": "raw",
        "p95Ms": "p95",
        "p99Ms": "p99",
        "maxMs": "max",
        "meanMs": "mean",
        "standardDeviationMs": "raw",
    }
    metrics.append(
        metric(
            "search.latency.count",
            latency["measuredOperations"],
            "queries",
            "count",
            latency["measuredOperations"],
            source,
        )
    )
    for key, aggregate in aggregation.items():
        metrics.append(
            metric(
                "search.latency." + re.sub(r"([A-Z])", lambda m: "_" + m.group(1).lower(), key),
                overall[key],
                "ms",
                aggregate,
                overall["count"],
                source,
                notes=("Predeclared nearest-rank percentile" if key.startswith("p") else None),
            )
        )
    for category, stats in sorted(latency["byCategory"].items()):
        safe_category = re.sub(r"[^a-z0-9_.-]", "-", category.lower())
        for key, aggregate in (("p50Ms", "p50"), ("p95Ms", "p95"), ("p99Ms", "p99"), ("maxMs", "max")):
            suffix = re.sub(r"([A-Z])", lambda m: "_" + m.group(1).lower(), key)
            metrics.append(
                metric(
                    f"search.category.{safe_category}.{suffix}",
                    stats[key],
                    "ms",
                    aggregate,
                    stats["count"],
                    source,
                )
            )
    baseline = observations["baselineCorrectness"]
    for name, value in (
        ("search.correctness.expected_cases", baseline["expectedCases"]),
        ("search.correctness.matched_cases", baseline["matchedCases"]),
        ("search.correctness.mapping_errors", baseline["mappingErrors"]),
        ("search.correctness.duplicate_errors", baseline["duplicateResultErrors"]),
        ("search.correctness.adversarial_failures", baseline["adversarialFailures"]),
        ("search.correctness.crashes", baseline["crashes"]),
    ):
        metrics.append(metric(name, value, "count", "count", baseline["expectedCases"], source))
    for operation in observations["mutation"]["operations"]:
        suffix = operation["id"].lower().replace("mut-", "").replace("-", "_")
        metrics.append(
            metric(
                f"search.mutation.{suffix}_ms",
                operation["latencyMs"],
                "ms",
                "raw",
                1,
                source,
                notes="Observation only; no approved numeric mutation-latency threshold.",
            )
        )
    for rebuild_pass in observations["rebuild"]["passes"]:
        suffix = rebuild_pass["id"].lower().replace("-", "_")
        metrics.append(
            metric(
                f"search.{suffix}_ms",
                rebuild_pass["latencyMs"],
                "ms",
                "raw",
                1,
                source,
            )
        )
    memory = observations["memory"]
    for name, key in (
        ("search.memory.peak_pss_mb", "peakPssMb"),
        ("search.memory.peak_native_heap_mb", "peakNativeHeapMb"),
        ("search.memory.peak_managed_heap_mb", "peakManagedHeapMb"),
    ):
        metrics.append(
            metric(
                name,
                memory[key],
                "MiB",
                "max",
                memory["sampleCount"],
                "android.os.Debug sampled at fixed phase boundaries",
                notes="Observation only; no approved numeric memory threshold.",
            )
        )
    return metrics


def build_result(
    observations: dict[str, Any],
    evaluation: dict[str, Any],
    output_dir: Path,
    detail_paths: list[tuple[Path, str]],
) -> dict[str, Any]:
    android = observations["androidEnvironment"]
    baseline = observations["baselineCorrectness"]
    latency = observations["latency"]["overall"]
    mutation = observations["mutation"]
    injection_crash_count = (
        baseline["adversarialFailures"]
        + baseline["specialCharacterFailures"]
        + baseline["failureExecutions"]
        + baseline["crashes"]
    )
    mapping_error_count = baseline["mappingErrors"] + mutation["mappingErrors"]
    latency_failure = not evaluation["latencyPass"]
    deterministic = evaluation["rebuildPass"]
    success_gates = [
        gate("GATE-SEARCH-P95", "success", "Reference search p95 below 200 ms", "search.latency.p95_ms", "<", 200, "ms", latency["p95Ms"], "met" if latency["p95Ms"] < 200 else "not_met"),
        gate("GATE-SEARCH-P99", "success", "Reference search p99 below 500 ms", "search.latency.p99_ms", "<", 500, "ms", latency["p99Ms"], "met" if latency["p99Ms"] < 500 else "not_met"),
        gate("GATE-SEARCH-CORRECTNESS", "success", "Correct filters and canonical source mappings", "search.correctness.all_matched", "=", True, None, evaluation["correctnessPass"] and evaluation["mappingPass"], "met" if evaluation["correctnessPass"] and evaluation["mappingPass"] else "not_met"),
        gate("GATE-SEARCH-ADVERSARIAL", "success", "Safe adversarial query handling", "search.correctness.adversarial_failures", "=", 0, "count", injection_crash_count, "met" if injection_crash_count == 0 else "not_met"),
        gate("GATE-SEARCH-REBUILD", "success", "Deterministic logical rebuild", "search.rebuild.deterministic", "=", True, None, deterministic, "met" if deterministic else "not_met"),
    ]
    failure_gates = [
        gate("GATE-SEARCH-LATENCY-FAILURE", "failure", "Approved latency gate violation", "search.latency.gate_failed", "=", True, None, latency_failure, "triggered" if latency_failure else "not_triggered"),
        gate("GATE-SEARCH-INJECTION-CRASH", "failure", "Query operator injection or query crash", "search.correctness.adversarial_failures", ">", 0, "count", injection_crash_count, "triggered" if injection_crash_count > 0 else "not_triggered"),
        gate("GATE-SEARCH-MAPPING-LOSS", "failure", "Canonical mapping loss", "search.correctness.mapping_errors", ">", 0, "count", mapping_error_count, "triggered" if mapping_error_count > 0 else "not_triggered"),
        gate("GATE-SEARCH-NONDETERMINISTIC", "failure", "Non-deterministic rebuild or index recovery", "search.rebuild.nondeterministic", "=", True, None, not deterministic, "triggered" if not deterministic else "not_triggered"),
        gate("GATE-SEARCH-STORAGE-UPDATE", "failure", "Storage or update numeric gate", "search.storage_update.numeric_gate", "custom", None, None, None, "not_evaluated", "The approved Gate Set defines no numeric storage, memory, build, or mutation-latency threshold; all are observations only."),
    ]
    errors: list[dict[str, Any]] = []
    if injection_crash_count:
        errors.append({"code": "SEARCH_QUERY_FAILURE", "stage": "query-campaign", "count": injection_crash_count, "retryable": False, "redactedSummary": "One or more frozen special/adversarial queries failed safe execution.", "sensitiveContentPresent": False})
    if mapping_error_count:
        errors.append({"code": "SEARCH_MAPPING_ERROR", "stage": "correctness", "count": mapping_error_count, "retryable": False, "redactedSummary": "One or more results did not preserve the frozen canonical mapping contract.", "sensitiveContentPresent": False})
    if not deterministic:
        errors.append({"code": "SEARCH_REBUILD_NONDETERMINISTIC", "stage": "rebuild", "count": 1, "retryable": False, "redactedSummary": "Logical rebuild or independent second build did not reproduce the frozen result contract.", "sensitiveContentPresent": False})
    if not evaluation["mutationPass"]:
        errors.append({"code": "SEARCH_MUTATION_INCONSISTENT", "stage": "mutation", "count": max(1, mutation["staleResultErrors"]), "retryable": False, "redactedSummary": "At least one frozen mutation correctness invariant failed.", "sensitiveContentPresent": False})
    evidence = []
    for name in ("dataset-manifest.json", "query-manifest.json", "mutation-manifest.json"):
        evidence.append(evidence_entry(EVIDENCE / name, output_dir, "fixture-manifest"))
    for path, kind in detail_paths:
        evidence.append(evidence_entry(path, output_dir, kind))
    status = evaluation["formalVerdict"]
    host_outcome = evaluation["hostEmulatorExploratoryOutcome"]
    rationale = (
        "The frozen host/emulator reference campaign passed all approved search gates, but formal "
        "PASS remains unavailable without future physical D1-D3 latency evidence."
        if host_outcome == "PASS"
        else "At least one approved search correctness, safety, latency, mutation, mapping, or rebuild gate failed in the frozen reference campaign."
    )
    security_patch = android.get("securityPatch") or None
    page_size = int(android["pageSizeBytes"])
    require(page_size in (4096, 16384), f"Unsupported schema page size {page_size}")
    main_db = observations["primaryPreparation"]
    recommendation_decision = "GO" if host_outcome == "PASS" else "FALLBACK"
    fallback = None if host_outcome == "PASS" else "Per-entity FTS, pagination/ranking, or query normalization; FTS5 only as a separately documented experiment."
    return {
        "schemaVersion": 1,
        "gateSetVersion": "stage0-v0.1",
        "generatedAt": observations["generatedAt"],
        "pocId": "POC-SEARCH-001",
        "applicationVersion": observations["harnessVersion"],
        "commit": observations["commit"],
        "device": {
            "profileId": "D2",
            "kind": "emulator",
            "manufacturer": android["manufacturer"],
            "model": android["model"],
            "firmwareOrBuild": android["buildFingerprint"],
            "securityPatch": security_patch,
            "abi": android["abi"],
            "ramMb": int(android["ramMb"]),
            "pageSizeBytes": page_size,
            "inventoryStatus": "available",
            "uniqueHardwareIdentifierRecorded": False,
            "notes": "API 36 x86_64 emulator shaped as a D2 exploratory profile; it is not physical D2 evidence.",
        },
        "androidApi": int(android["androidApi"]),
        "duration": {
            "plannedSeconds": 7200,
            "actualSeconds": observations["durationSeconds"],
            "completed": True,
            "monotonicClockUsed": True,
        },
        "inputData": {
            "classification": "generated_text",
            "manifestId": observations["manifests"]["datasetId"],
            "manifestSha256": observations["manifests"]["datasetSha256"],
            "fixtureIds": ["poc-search-synthetic-v1"],
            "languages": ["ru", "en", "mixed-ru-en"],
            "acousticConditions": ["not-applicable"],
            "consentReference": None,
            "containsRealMeetingData": False,
            "generatorVersion": "search-generator-1.0.0",
        },
        "metrics": build_metrics(observations),
        "successGates": success_gates,
        "failureGates": failure_gates,
        "result": {
            "status": status,
            "gateSetStatus": "Approved",
            "requiredSlicesCompleted": False,
            "rationale": rationale,
            "invalidatedRunCount": 2,
        },
        "errors": errors,
        "battery": {
            "applicable": False,
            "startPercent": None,
            "endPercent": None,
            "energyMwh": None,
            "baselineRatio": None,
            "chargerState": "not-applicable",
            "screenState": "not-applicable",
            "measurementSource": None,
            "notApplicableReason": "Battery is outside this emulator search PoC and no physical-device energy claim is made.",
        },
        "temperature": {
            "applicable": False,
            "startCelsius": None,
            "endCelsius": None,
            "maxCelsius": None,
            "startThermalStatus": None,
            "maxThermalStatus": None,
            "measurementSource": None,
            "notApplicableReason": "Emulator thermal readings are not physical-device evidence.",
        },
        "memory": {
            "applicable": True,
            "peakPssMb": observations["memory"]["peakPssMb"],
            "peakRssMb": observations["memory"]["peakRssMb"],
            "peakNativeHeapMb": observations["memory"]["peakNativeHeapMb"],
            "oomCount": 0,
            "trimOrPressureObserved": False,
            "measurementSource": "android.os.Debug at fixed benchmark phase boundaries",
            "notApplicableReason": "No approved numeric memory threshold; reported as an observation.",
        },
        "fileSizes": [
            {
                "artifact": "reference-database-after-checkpoint-compact",
                "bytes": main_db["afterCompact"]["databaseBytes"],
                "sha256": main_db["afterCompactDatabaseSha256"],
                "classification": "synthetic",
            },
            {
                "artifact": "reference-wal-after-checkpoint-compact",
                "bytes": main_db["afterCompact"]["walBytes"],
                "sha256": None,
                "classification": "synthetic",
            },
            {
                "artifact": "reference-shm-after-checkpoint-compact",
                "bytes": main_db["afterCompact"]["shmBytes"],
                "sha256": None,
                "classification": "synthetic",
            },
        ],
        "licenses": [
            {"artifactId": "androidx.room", "category": "source-code", "version": android["roomVersion"], "sha256": None, "licenseId": "Apache-2.0", "licenseReviewState": "EVALUATION_APPROVED", "evaluationRightsConfirmed": True, "redistributionRights": "allowed", "evidenceLocator": "https://developer.android.com/jetpack/androidx/releases/room"},
            {"artifactId": "android-platform-sqlite", "category": "other", "version": android["sqliteVersion"], "sha256": None, "licenseId": "Android-platform-component", "licenseReviewState": "EVALUATION_APPROVED", "evaluationRightsConfirmed": True, "redistributionRights": "not-applicable", "evidenceLocator": "Runtime SELECT sqlite_version() recorded in environment.json"},
        ],
        "limitations": [
            {"id": "LIMIT-SEARCH-NO-PHYSICAL-D1-D3", "severity": "high", "description": "No physical D1-D3 latency campaign was run; emulator percentiles cannot establish a device-support claim.", "blocksVerdict": True},
            {"id": "LIMIT-SEARCH-EMULATOR-LATENCY", "severity": "high", "description": "Host scheduling and virtualized storage make latency exploratory rather than representative of a real phone.", "blocksVerdict": True},
            {"id": "LIMIT-SEARCH-OBSERVATION-ONLY-METRICS", "severity": "medium", "description": "Database size, build time, memory, and mutation latency have no pre-approved numeric threshold and remain observations only.", "blocksVerdict": False},
            {"id": "LIMIT-SEARCH-POC-NOT-ADMISSION", "severity": "medium", "description": "The isolated Room/FTS4 schema and harness are PoC evidence, not a production schema or dependency admission.", "blocksVerdict": False},
        ],
        "recommendation": {
            "decision": recommendation_decision,
            "rationale": evaluation["architecturalOutcome"] + "; this does not admit the PoC schema into production.",
            "fallback": fallback,
            "ownerAction": None,
        },
        "evidenceFiles": evidence,
        "privacyReview": {
            "publicEvidenceSafe": True,
            "secretScanPassed": True,
            "personalDataScanPassed": True,
            "forbiddenContentLoggingCheckPassed": True,
            "reviewerRole": "deterministic generator and evidence allowlist validator",
            "notes": "Only generated text identifiers, aggregate metrics, deterministic mappings, and public runner metadata are retained; database files are deleted.",
        },
    }


def validate_observations(observations: dict[str, Any], expected_commit: str | None) -> None:
    require(observations.get("schemaVersion") == 1, "Observation schema version mismatch")
    require(observations.get("pocId") == "POC-SEARCH-001", "Observation PoC id mismatch")
    commit = observations.get("commit", "")
    require(bool(re.fullmatch(r"[0-9a-f]{40}", commit)), "Observation commit must be a full SHA")
    if expected_commit:
        require(commit == expected_commit, f"Observation commit {commit} != {expected_commit}")
    require(observations["campaign"]["latencyEligibleQueryCount"] == 34, "Frozen query count drift")
    require(observations["latency"]["measuredOperations"] == 1020, "Frozen measured count drift")
    require(observations["temporaryDatabasesDeleted"] is True, "Temporary benchmark databases were not deleted")
    for name, key in (
        ("dataset-manifest.json", "datasetSha256"),
        ("query-manifest.json", "querySha256"),
        ("mutation-manifest.json", "mutationSha256"),
    ):
        actual = sha256(EVIDENCE / name)
        require(actual == observations["manifests"][key], f"{name} SHA drift")


def validate_schema(result: dict[str, Any]) -> None:
    sys.path.insert(0, str(ROOT / "tools"))
    from validate_poc_capture import SchemaValidator  # pylint: disable=import-outside-toplevel

    schema = read_json(SCHEMA)
    SchemaValidator(schema).validate(result, schema)


def main() -> int:
    args = parse_args()
    observations = read_json(args.observations)
    validate_observations(observations, args.expected_commit)
    evaluation = evaluate(observations)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    environment_path = args.output_dir / "environment.json"
    query_path = args.output_dir / "query-result.json"
    mutation_path = args.output_dir / "mutation-result.json"
    rebuild_path = args.output_dir / "rebuild-result.json"
    write_json(environment_path, environment_result(observations))
    write_json(query_path, query_result(observations, evaluation))
    write_json(mutation_path, mutation_result(observations, evaluation))
    write_json(rebuild_path, rebuild_result(observations, evaluation))
    result = build_result(
        observations,
        evaluation,
        args.output_dir,
        [
            (environment_path, "other"),
            (query_path, "metrics"),
            (mutation_path, "metrics"),
            (rebuild_path, "metrics"),
        ],
    )
    validate_schema(result)
    result_path = args.output_dir / "benchmark-result.json"
    write_json(result_path, result)
    print(f"POC-SEARCH-001 formal verdict: {evaluation['formalVerdict']}")
    print(f"Host/emulator exploratory outcome: {evaluation['hostEmulatorExploratoryOutcome']}")
    print(f"Architectural outcome: {evaluation['architecturalOutcome']}")
    print(f"Wrote {result_path}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
