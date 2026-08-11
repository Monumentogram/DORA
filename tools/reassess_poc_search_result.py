#!/usr/bin/env python3
"""Issue a versioned review assessment without mutating completed POC-SEARCH-001 evidence."""

from __future__ import annotations

import argparse
import copy
import json
import sys
from pathlib import Path
from typing import Any

import finalize_poc_search_result as finalizer
from poc_search_environment import classify_android_runtime


ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "docs" / "evidence" / "poc-search-001"
SOURCE_RUN_ID = "31487775567"
ASSESSMENT_ID = "review-2026-08-11-v3"
ASSESSMENT_ISSUED_AT = "2026-08-11T16:30:09Z"


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def corrected_environment(source: dict[str, Any], system_image: dict[str, Any]) -> dict[str, Any]:
    value = copy.deepcopy(source)
    android = value["android"]
    recorded_kind = android["kind"]
    derived_kind = classify_android_runtime(android)
    if recorded_kind == derived_kind:
        raise ValueError("Expected the immutable source environment to contain the reviewed kind defect")
    android["kind"] = derived_kind
    android["systemImagePackage"] = system_image["package"]
    android["systemImageRevision"] = system_image["revision"]
    android["systemImageArchiveSha256"] = system_image["archive"]["sha256"]
    value["evidenceRevision"] = 3
    value["sourceEvidence"] = (
        f"docs/evidence/poc-search-001/runs/{SOURCE_RUN_ID}/environment.json"
    )
    value["correction"] = {
        "field": "android.kind",
        "recorded": recorded_kind,
        "corrected": derived_kind,
        "basis": [
            android["buildFingerprint"],
            android["model"],
            android["device"],
            android["product"],
            android.get("cpuSummary"),
        ],
        "metricsChanged": False,
    }
    value["measurementScope"] = {
        "correctness": "reference-scale Android emulator",
        "latency": "Android emulator exploratory evidence only",
        "physicalDeviceClaim": False,
        "requiredFuturePhysicalProfiles": ["D1", "D2", "D3"],
    }
    return value


def render_assessment(repo_root: Path, output_dir: Path) -> dict[Path, dict[str, Any]]:
    evidence = repo_root / "docs" / "evidence" / "poc-search-001"
    source_run = evidence / "runs" / SOURCE_RUN_ID
    observations = read_json(source_run / "benchmark-observations.json")
    system_image = read_json(evidence / "android-system-image-provenance.json")
    corrected_observations = copy.deepcopy(observations)
    corrected_observations["androidEnvironment"]["kind"] = classify_android_runtime(
        corrected_observations["androidEnvironment"]
    )
    corrected_observations["androidEnvironment"]["systemImagePackage"] = system_image["package"]
    corrected_observations["androidEnvironment"]["systemImageRevision"] = system_image["revision"]
    corrected_observations["androidEnvironment"]["systemImageArchiveSha256"] = system_image["archive"]["sha256"]
    finalizer.validate_observations(corrected_observations, observations["commit"])
    evaluation = finalizer.evaluate(corrected_observations)
    dependency_inventory = read_json(evidence / "dependency-inventory.json")
    ip_evaluation = read_json(evidence / "ip-evaluation.json")
    environment_path = output_dir / "environment.json"
    query_path = output_dir / "query-result.json"
    mutation_path = output_dir / "mutation-result.json"
    rebuild_path = output_dir / "rebuild-result.json"
    values: dict[Path, dict[str, Any]] = {
        environment_path: corrected_environment(
            read_json(source_run / "environment.json"),
            system_image,
        ),
        query_path: finalizer.query_result(corrected_observations, evaluation),
        mutation_path: finalizer.mutation_result(corrected_observations, evaluation),
        rebuild_path: finalizer.rebuild_result(corrected_observations, evaluation),
    }
    output_dir.mkdir(parents=True, exist_ok=True)
    for path, value in values.items():
        finalizer.write_json(path, value)
    result = finalizer.build_result(
        corrected_observations,
        evaluation,
        output_dir,
        [
            (environment_path, "other"),
            (query_path, "metrics"),
            (mutation_path, "metrics"),
            (rebuild_path, "metrics"),
            (source_run / "benchmark-observations.json", "metrics"),
            (source_run / "targeted-scale-observations.json", "metrics"),
            (source_run / "benchmark-result.json", "json-result"),
        ],
        dependency_inventory=dependency_inventory,
        ip_evaluation=ip_evaluation,
        invalidated_run_count=3,
    )
    result["generatedAt"] = ASSESSMENT_ISSUED_AT
    result_path = output_dir / "benchmark-result.json"
    values[result_path] = result
    finalizer.validate_schema(result)
    finalizer.write_json(result_path, result)
    gate_set_locator = "docs/stage0/poc-search-gate-set-stage0-v0.2.draft.json"
    gate_set = read_json(repo_root / gate_set_locator)
    index = {
        "schemaVersion": 2,
        "pocId": "POC-SEARCH-001",
        "currentAssessment": {
            "assessmentId": ASSESSMENT_ID,
            "issuedAt": ASSESSMENT_ISSUED_AT,
            "locator": result_path.relative_to(repo_root).as_posix(),
            "sha256": finalizer.sha256(result_path),
            "resultStatus": result["result"]["status"],
            "recommendation": result["recommendation"]["decision"],
            "newMeasurementRun": False,
            "sourceActionsRunId": SOURCE_RUN_ID,
        },
        "gateContract": {
            "version": "stage0-v0.1",
            "status": "INCOMPLETE_HISTORICAL_CONTRACT",
            "complete": False,
            "blocker": (
                "Draft stage0-v0.2 options exist, but the Project owner has selected none. "
                "No measured campaign is authorized."
            ),
            "decisionLocator": (
                "docs/stage0/DEC-043-POC-SEARCH-STORAGE-UPDATE-GATES-DRAFT.md"
            ),
            "candidate": {
                "version": gate_set["gateSetVersion"],
                "status": gate_set["status"],
                "documentLocator": (
                    "docs/stage0/DORA_MVP1_POC_SEARCH_GATE_SET_STAGE0_V0_2_DRAFT.md"
                ),
                "machineLocator": gate_set_locator,
                "selectedOptionId": gate_set["selectedOptionId"],
                "benchmarkExecutionAllowed": gate_set["benchmarkExecutionAllowed"],
            },
        },
        "ipEvaluation": {
            "locator": "docs/evidence/poc-search-001/ip-evaluation.json",
            "status": ip_evaluation["evaluationStatus"],
            "futureMeasuredExecution": ip_evaluation["futureMeasuredExecution"],
        },
        "completedEvidencePolicy": {
            "immutableRunsLocator": "docs/evidence/poc-search-001/runs",
            "ledgerLocator": "docs/evidence/poc-search-001/evidence-ledger.json",
            "rootV1FilesSuperseded": True,
        },
    }
    index_path = evidence / "evidence-index.json"
    values[index_path] = index
    finalizer.write_json(index_path, index)
    return values


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=ROOT)
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=EVIDENCE / "assessments" / ASSESSMENT_ID,
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    repo_root = args.root.resolve()
    output_dir = args.output_dir
    if not output_dir.is_absolute():
        output_dir = repo_root / output_dir
    values = render_assessment(repo_root, output_dir)
    print(f"Wrote {len(values)} versioned assessment files for {ASSESSMENT_ID}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, json.JSONDecodeError) as error:
        print(f"FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
