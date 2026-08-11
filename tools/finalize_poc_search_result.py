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
from pathlib import Path
from typing import Any

from poc_search_environment import require_consistent_android_runtime

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "docs" / "evidence" / "poc-search-001"
SCHEMA = ROOT / "docs" / "stage0" / "benchmark-result.schema.json"
GATE_SOURCE = "docs/stage0/DORA_MVP1_POC_GATES.md Gate Set stage0-v0.1"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--observations", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, default=EVIDENCE)
    parser.add_argument("--expected-commit")
    parser.add_argument("--invalidated-run-count", type=int, default=0)
    parser.add_argument(
        "--dependency-inventory",
        type=Path,
        default=EVIDENCE / "dependency-inventory.json",
    )
    parser.add_argument(
        "--license-notice-inventory",
        type=Path,
        default=EVIDENCE / "license-notice-inventory.json",
    )
    parser.add_argument(
        "--ip-evaluation",
        type=Path,
        default=EVIDENCE / "ip-evaluation.json",
    )
    return parser.parse_args()


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def write_json(path: Path, value: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", encoding="utf-8", newline="\n") as target:
        target.write(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n")


def sha256(path: Path) -> str:
    # Every caller supplies repository JSON, whose canonical `.gitattributes` form is LF.
    # Normalize a stale Windows CRLF worktree so finalization and later verification produce
    # the same evidence locator hashes as GitHub Actions and the committed blobs.
    canonical_bytes = path.read_bytes().replace(b"\r\n", b"\n")
    return "sha256:" + hashlib.sha256(canonical_bytes).hexdigest()


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
        "runnerOs": os.environ.get("RUNNER_OS"),
        "runnerArch": os.environ.get("RUNNER_ARCH"),
        "runnerImageOs": os.environ.get("ImageOS"),
        "runnerImageVersion": os.environ.get("ImageVersion"),
        "actionsRunnerVersion": os.environ.get("ACTIONS_RUNNER_VERSION"),
        "githubWorkflow": os.environ.get("GITHUB_WORKFLOW"),
        "githubRunId": os.environ.get("GITHUB_RUN_ID"),
        "githubRunAttempt": os.environ.get("GITHUB_RUN_ATTEMPT"),
        "githubSha": os.environ.get("GITHUB_SHA"),
        "githubRef": os.environ.get("GITHUB_REF"),
    }


def environment_result(observations: dict[str, Any]) -> dict[str, Any]:
    android = observations["androidEnvironment"]
    runtime_kind = require_consistent_android_runtime(android)
    return {
        "schemaVersion": 1,
        "pocId": "POC-SEARCH-001",
        "generatedAt": observations["generatedAt"],
        "commit": observations["commit"],
        "host": host_environment(),
        "android": android,
        "measurementScope": {
            "correctness": f"reference-scale Android {runtime_kind}",
            "latency": f"Android {runtime_kind}; support verdict still requires the approved matrix",
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
        and secondary["adversarialFailures"] == 0
        and secondary["specialCharacterFailures"] == 0
        and secondary["failureExecutions"] == 0
        and secondary["crashes"] == 0
    )
    mapping_pass = (
        baseline["mappingErrors"] == 0
        and baseline["duplicateResultErrors"] == 0
        and secondary["mappingErrors"] == 0
        and secondary["duplicateResultErrors"] == 0
        and observations["primaryPreparation"]["missingCanonicalMappings"] == 0
        and observations["primaryPreparation"]["missingIndexRows"] == 0
        and observations["primaryPreparation"]["duplicateCanonicalRows"] == 0
        and observations["secondaryPreparation"]["missingCanonicalMappings"] == 0
        and observations["secondaryPreparation"]["missingIndexRows"] == 0
        and observations["secondaryPreparation"]["duplicateCanonicalRows"] == 0
        and mutation["mappingErrors"] == 0
    )
    rebuild_pass = bool(rebuild["deterministic"] and cross["deterministic"])
    mutation_pass = bool(mutation["allCorrect"])
    evaluated_metrics_pass = all(
        [latency_pass, correctness_pass, safety_pass, mapping_pass, rebuild_pass, mutation_pass]
    )
    if evaluated_metrics_pass:
        architectural_outcome = (
            "Evaluated historical metrics passed, but no architectural GO is available because "
            "the prospective Option B storage/update contract was not measured and physical "
            "D1-D3 evidence is incomplete"
        )
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
        "evaluatedMetricsOutcome": "PASS" if evaluated_metrics_pass else "FAIL",
        "hostEmulatorExploratoryOutcome": "INCONCLUSIVE" if evaluated_metrics_pass else "FAIL",
        "formalVerdict": "INCONCLUSIVE" if evaluated_metrics_pass else "FAIL",
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
        "queryPlan": observations["queryPlan"],
        "checkpointExecution": observations["checkpointExecution"],
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
    try:
        locator = path.resolve().relative_to(ROOT.resolve()).as_posix()
    except ValueError:
        locator = f"docs/evidence/poc-search-001/{path.relative_to(output_dir).as_posix()}"
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


def build_license_entries(
    dependency_inventory: dict[str, Any],
    license_notice_inventory: dict[str, Any],
    ip_evaluation: dict[str, Any],
) -> list[dict[str, Any]]:
    require(dependency_inventory.get("pocId") == "POC-SEARCH-001", "Wrong dependency inventory PoC id")
    require(
        dependency_inventory.get("inventoryStatus") == "COMPLETE_UNREVIEWED",
        "The immutable dependency evidence packet must retain its generation-time inventory state",
    )
    require(
        ip_evaluation.get("evaluationStatus")
        in {
            "NOT_ESTABLISHED",
            "REVIEWERS_ASSIGNED_REVIEW_PENDING",
            "EXACT_EVIDENCE_COMPLETE_OWNER_APPROVAL_PENDING",
            "EVALUATION_APPROVED",
        },
        "Unsupported IP evaluation state",
    )
    evaluation_approved = ip_evaluation.get("evaluationStatus") == "EVALUATION_APPROVED"
    if evaluation_approved:
        require(
            ip_evaluation.get("ipPrecondition") == "SATISFIED_FOR_STAGE0"
            and ip_evaluation.get("stage0Approval", {}).get("status")
            == "EVALUATION_APPROVED",
            "Stage 0 IP approval record is incomplete",
        )
    require(
        license_notice_inventory.get("reviewStatus") == "EVIDENCE_COMPLETE",
        "Exact license/NOTICE evidence must be complete before result finalization",
    )
    license_reviews = {
        component["coordinate"]: component
        for component in license_notice_inventory["components"]
    }
    require(
        set(license_reviews)
        == {component["coordinate"] for component in dependency_inventory["components"]},
        "License/NOTICE component graph does not match the dependency inventory",
    )
    entries: list[dict[str, Any]] = []
    for component in dependency_inventory["components"]:
        coordinate = component["coordinate"]
        version = coordinate.rsplit(":", 1)[1]
        binaries = [
            artifact
            for artifact in component["artifacts"]
            if artifact["kind"] in ("aar", "jar", "klib")
        ]
        require(len(binaries) <= 1, f"Ambiguous primary artifacts for {coordinate}")
        primary_sha = f"sha256:{binaries[0]['sha256']}" if binaries else None
        effective_spdx = license_reviews[coordinate]["effectiveSpdx"]
        require(bool(effective_spdx), f"Effective license evidence missing for {coordinate}")
        license_id = " AND ".join(effective_spdx)
        only_build_tool = set(component["scopes"]).issubset(
            {
                "_agp_internal_benchmark_kspClasspath",
                "_agp_internal_debug_kspClasspath",
            }
        )
        entries.append(
            {
                "artifactId": coordinate,
                "category": "tool" if only_build_tool else "source-code",
                "version": version,
                "sha256": primary_sha,
                "licenseId": license_id[:160],
                "licenseReviewState": (
                    "EVALUATION_APPROVED" if evaluation_approved else "PROPOSED"
                ),
                "evaluationRightsConfirmed": evaluation_approved,
                "redistributionRights": "unknown",
                "evidenceLocator": (
                    "docs/evidence/poc-search-001/license-notice-inventory.json#" + coordinate
                ),
            }
        )
    for platform_artifact in ip_evaluation["platformArtifacts"]:
        entries.append(
            {
                "artifactId": platform_artifact["artifactId"],
                "category": "other",
                "version": platform_artifact["observedVersion"],
                "sha256": platform_artifact["sha256"],
                "licenseId": (
                    "NOASSERTION"
                    if platform_artifact["licenseReviewState"] == "EVALUATION_APPROVED"
                    else "UNREVIEWED-PLATFORM-COMPONENT"
                ),
                "licenseReviewState": platform_artifact["licenseReviewState"],
                "evaluationRightsConfirmed": platform_artifact["evaluationRightsConfirmed"],
                "redistributionRights": "unknown",
                "evidenceLocator": platform_artifact["evidenceLocator"],
            }
        )
    return entries


def build_result(
    observations: dict[str, Any],
    evaluation: dict[str, Any],
    output_dir: Path,
    detail_paths: list[tuple[Path, str]],
    dependency_inventory: dict[str, Any] | None = None,
    license_notice_inventory: dict[str, Any] | None = None,
    ip_evaluation: dict[str, Any] | None = None,
    invalidated_run_count: int = 0,
) -> dict[str, Any]:
    if dependency_inventory is None:
        dependency_inventory = read_json(EVIDENCE / "dependency-inventory.json")
    if license_notice_inventory is None:
        license_notice_inventory = read_json(EVIDENCE / "license-notice-inventory.json")
    if ip_evaluation is None:
        ip_evaluation = read_json(EVIDENCE / "ip-evaluation.json")
    android = observations["androidEnvironment"]
    baseline = observations["baselineCorrectness"]
    secondary = observations["secondaryCorrectness"]
    latency = observations["latency"]["overall"]
    mutation = observations["mutation"]
    injection_crash_count = (
        baseline["adversarialFailures"]
        + baseline["specialCharacterFailures"]
        + baseline["failureExecutions"]
        + baseline["crashes"]
        + secondary["adversarialFailures"]
        + secondary["specialCharacterFailures"]
        + secondary["failureExecutions"]
        + secondary["crashes"]
    )
    mapping_error_count = (
        baseline["mappingErrors"]
        + baseline["duplicateResultErrors"]
        + secondary["mappingErrors"]
        + secondary["duplicateResultErrors"]
        + observations["primaryPreparation"]["missingCanonicalMappings"]
        + observations["primaryPreparation"]["missingIndexRows"]
        + observations["primaryPreparation"]["duplicateCanonicalRows"]
        + observations["secondaryPreparation"]["missingCanonicalMappings"]
        + observations["secondaryPreparation"]["missingIndexRows"]
        + observations["secondaryPreparation"]["duplicateCanonicalRows"]
        + mutation["mappingErrors"]
    )
    update_error_count = (
        mutation["staleResultErrors"]
        + mutation["mappingErrors"]
        + mutation["crashes"]
        + sum(1 for operation in mutation["operations"] if not operation["correctnessPassed"])
    )
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
        gate("GATE-SEARCH-UPDATE-INTEGRITY", "failure", "Update leaves stale results, mapping loss, or a crash", "search.update.integrity_errors", ">", 0, "count", update_error_count, "triggered" if update_error_count > 0 else "not_triggered"),
        gate("GATE-SEARCH-STORAGE-UPDATE-OVERHEAD", "failure", "Storage/update overhead was not measured under the prospective contract", "search.storage_update.numeric_gate", "custom", None, None, None, "not_evaluated", "The historical campaign used v0.1, whose mandatory prose had no numeric predicate. Option B is now approved prospectively in v0.2, but it was not and may not be applied retrospectively to this campaign."),
    ]
    errors: list[dict[str, Any]] = []
    if injection_crash_count:
        errors.append({"code": "SEARCH_QUERY_FAILURE", "stage": "query-campaign", "count": injection_crash_count, "retryable": False, "redactedSummary": "One or more frozen special/adversarial queries failed safe execution.", "sensitiveContentPresent": False})
    if mapping_error_count:
        errors.append({"code": "SEARCH_MAPPING_ERROR", "stage": "correctness", "count": mapping_error_count, "retryable": False, "redactedSummary": "One or more results did not preserve the frozen canonical mapping contract.", "sensitiveContentPresent": False})
    if not deterministic:
        errors.append({"code": "SEARCH_REBUILD_NONDETERMINISTIC", "stage": "rebuild", "count": 1, "retryable": False, "redactedSummary": "Logical rebuild or independent second build did not reproduce the frozen result contract.", "sensitiveContentPresent": False})
    if not evaluation["mutationPass"]:
        errors.append({"code": "SEARCH_MUTATION_INCONSISTENT", "stage": "mutation", "count": max(1, update_error_count), "retryable": False, "redactedSummary": "At least one frozen mutation correctness invariant failed.", "sensitiveContentPresent": False})
    evidence = []
    for name in ("dataset-manifest.json", "query-manifest.json", "mutation-manifest.json"):
        evidence.append(evidence_entry(EVIDENCE / name, output_dir, "fixture-manifest"))
    for name in (
        "dependency-inventory.json",
        "license-notice-inventory.json",
        "ip-evaluation.json",
        "ip-stage0-evaluation-review.md",
        "android-system-image-provenance.json",
        "device-availability-stage0-v0.2.json",
        "gate-v02-harness-readiness.json",
        "evidence-ledger.json",
    ):
        evidence.append(evidence_entry(EVIDENCE / name, output_dir, "license-evidence"))
    for path in (
        ROOT / "docs/stage0/DEC-043-POC-SEARCH-STORAGE-UPDATE-GATES-DRAFT.md",
        ROOT / "docs/stage0/DORA_MVP1_POC_SEARCH_GATE_SET_STAGE0_V0_2_DRAFT.md",
        ROOT / "docs/stage0/poc-search-gate-set-stage0-v0.2.draft.json",
        ROOT / "docs/stage0/DEC-043-POC-SEARCH-STORAGE-UPDATE-GATES.md",
        ROOT / "docs/stage0/DORA_MVP1_POC_SEARCH_GATE_SET_STAGE0_V0_2.md",
        ROOT / "docs/stage0/poc-search-gate-set-stage0-v0.2.json",
        ROOT / "docs/stage0/DORA_MVP1_STAGE0_OWNER_DECISIONS.md",
    ):
        evidence.append(evidence_entry(path, output_dir, "other"))
    for path, kind in detail_paths:
        evidence.append(evidence_entry(path, output_dir, kind))
    status = evaluation["formalVerdict"]
    host_outcome = evaluation["hostEmulatorExploratoryOutcome"]
    rationale = (
        "The evaluated historical host/emulator metrics met their evaluated predicates, but the "
        "newly approved prospective Option B was not measured and physical D1-D3 evidence is "
        "incomplete. The exact IP package is approved only for Stage 0 evaluation and does not "
        "change the historical campaign, which remains INCONCLUSIVE."
        if host_outcome == "INCONCLUSIVE"
        else "At least one approved search correctness, safety, latency, mutation, mapping, or rebuild gate failed in the frozen reference campaign."
    )
    security_patch = android.get("securityPatch") or None
    runtime_kind = android["kind"]
    page_size = int(android["pageSizeBytes"])
    require(page_size in (4096, 16384), f"Unsupported schema page size {page_size}")
    main_db = observations["primaryPreparation"]
    recommendation_decision = "BLOCKED" if host_outcome == "INCONCLUSIVE" else "FALLBACK"
    fallback = (
        "Do not use the PoC for production integration; retain per-entity FTS or another separately evaluated fallback."
        if host_outcome == "INCONCLUSIVE"
        else "Per-entity FTS, pagination/ranking, or query normalization; FTS5 only as a separately documented experiment."
    )
    owner_action = (
        "If a later measured stage0-v0.2 campaign is scoped, provide physical D1 and D3 hardware, "
        "record a fresh exact-commit preflight, and separately authorize measured execution."
        if host_outcome == "INCONCLUSIVE"
        else None
    )
    license_entries = build_license_entries(
        dependency_inventory,
        license_notice_inventory,
        ip_evaluation,
    )
    return {
        "schemaVersion": 1,
        "gateSetVersion": "stage0-v0.1",
        "generatedAt": observations["generatedAt"],
        "pocId": "POC-SEARCH-001",
        "applicationVersion": observations["harnessVersion"],
        "commit": observations["commit"],
        "device": {
            "profileId": "D2",
            "kind": android["kind"],
            "manufacturer": android["manufacturer"],
            "model": android["model"],
            "firmwareOrBuild": android["buildFingerprint"],
            "securityPatch": security_patch,
            "abi": android["abi"],
            "ramMb": int(android["ramMb"]),
            "pageSizeBytes": page_size,
            "inventoryStatus": "available",
            "uniqueHardwareIdentifierRecorded": False,
            "notes": (
                "API 36 x86_64 emulator shaped as a D2 exploratory profile; it is not physical D2 evidence."
                if runtime_kind == "emulator"
                else "Automatically inventoried physical runtime; one device cannot establish D1-D3 support."
            ),
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
            "invalidatedRunCount": invalidated_run_count,
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
            "notApplicableReason": "Battery is outside this search PoC and no energy claim is made.",
        },
        "temperature": {
            "applicable": False,
            "startCelsius": None,
            "endCelsius": None,
            "maxCelsius": None,
            "startThermalStatus": None,
            "maxThermalStatus": None,
            "measurementSource": None,
            "notApplicableReason": "Thermal behavior is outside this search PoC verdict.",
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
        "licenses": license_entries,
        "limitations": [
            {"id": "LIMIT-SEARCH-NO-PHYSICAL-D1-D3", "severity": "high", "description": "No physical D1-D3 latency campaign was run; emulator percentiles cannot establish a device-support claim.", "blocksVerdict": True},
            {"id": "LIMIT-SEARCH-EMULATOR-LATENCY", "severity": "high", "description": "Host scheduling and virtualized storage make latency exploratory rather than representative of a real phone.", "blocksVerdict": True},
            {"id": "LIMIT-SEARCH-OBSERVATION-ONLY-METRICS", "severity": "medium", "description": "For the historical v0.1 campaign, database size, build time, memory, and mutation latency had no pre-approved numeric predicate and remain observations only. Prospective v0.2 thresholds cannot be applied retrospectively.", "blocksVerdict": False},
            {"id": "LIMIT-SEARCH-INCOMPLETE-STORAGE-UPDATE-GATE", "severity": "critical", "description": "The historical v0.1 contract made storage/update failure mandatory without numeric predicates. Option B is now approved prospectively in v0.2, but it was not measured and cannot reclassify that campaign.", "blocksVerdict": True},
            {"id": "LIMIT-SEARCH-POC-NOT-ADMISSION", "severity": "medium", "description": "The isolated Room/FTS4 schema and harness are PoC evidence, not a production schema or dependency admission.", "blocksVerdict": False},
        ],
        "recommendation": {
            "decision": recommendation_decision,
            "rationale": evaluation["architecturalOutcome"] + "; this does not admit the PoC schema into production.",
            "fallback": fallback,
            "ownerAction": owner_action,
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
    android_environment = observations["androidEnvironment"]
    require_consistent_android_runtime(android_environment)
    system_image = read_json(EVIDENCE / "android-system-image-provenance.json")
    require(
        android_environment["systemImagePackage"] == system_image["package"],
        "Observation system-image package drift",
    )
    require(
        android_environment["systemImageRevision"] == system_image["revision"],
        "Observation system-image revision drift",
    )
    require(
        android_environment["systemImageArchiveSha256"] == system_image["archive"]["sha256"],
        "Observation system-image archive SHA drift",
    )
    require(observations["campaign"]["latencyEligibleQueryCount"] == 34, "Frozen query count drift")
    require(observations["latency"]["measuredOperations"] == 1020, "Frozen measured count drift")
    require(observations["queryPlan"]["accepted"] is True, "FTS4-driving query plan not verified")
    require(
        observations["queryPlan"]["ftsIsDrivingTable"] is True
        and observations["queryPlan"]["canonicalLookupsUseRowId"] is True,
        "Q-SEARCH-SOURCE plan does not use FTS4 then canonical rowid lookups",
    )
    require(
        observations["checkpointExecution"]["completedPhases"]
        == ["query", "rebuild", "secondary"],
        "Full benchmark phase checkpoints are incomplete",
    )
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
    require(args.invalidated_run_count >= 0, "Invalidated run count cannot be negative")
    observations = read_json(args.observations)
    dependency_inventory = read_json(args.dependency_inventory)
    license_notice_inventory = read_json(args.license_notice_inventory)
    ip_evaluation = read_json(args.ip_evaluation)
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
        dependency_inventory=dependency_inventory,
        license_notice_inventory=license_notice_inventory,
        ip_evaluation=ip_evaluation,
        invalidated_run_count=args.invalidated_run_count,
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
