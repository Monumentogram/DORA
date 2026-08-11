#!/usr/bin/env python3
"""Static and host-SQLite smoke validation for POC-SEARCH-001."""

from __future__ import annotations

import hashlib
import json
import re
import sqlite3
import sys
import tempfile
from copy import deepcopy
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "docs" / "evidence" / "poc-search-001"
sys.path.insert(0, str(ROOT / "tools"))

import poc_search_contract as contract  # noqa: E402
import combine_poc_search_checkpoints as checkpoint_combiner  # noqa: E402
import combine_poc_search_gate_v02 as gate_v02_combiner  # noqa: E402
import check_poc_search_run_readiness as run_readiness  # noqa: E402
import finalize_poc_search_result as finalizer  # noqa: E402
import poc_search_evidence_ledger as evidence_ledger  # noqa: E402
from poc_search_environment import (  # noqa: E402
    classify_android_runtime,
    require_consistent_android_runtime,
)
from validate_poc_capture import SchemaValidator  # noqa: E402


def fail(message: str) -> None:
    raise ValueError(message)


def require(condition: bool, message: str) -> None:
    if not condition:
        fail(message)


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def canonical_digest(value: Any) -> str:
    encoded = json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")
    return "sha256:" + hashlib.sha256(encoded).hexdigest()


def file_digest(path: Path) -> str:
    # Evidence JSON is committed with `.gitattributes` `eol=lf`. A pre-existing Windows
    # worktree can still contain CRLF bytes until Git refreshes it, while Actions and the
    # Android asset copy use the canonical LF blob. Verify the repository representation so
    # the same immutable evidence hashes validate on every checkout.
    canonical_bytes = path.read_bytes().replace(b"\r\n", b"\n")
    return "sha256:" + hashlib.sha256(canonical_bytes).hexdigest()


def validate_manifests() -> None:
    dataset = read_json(EVIDENCE / "dataset-manifest.json")
    queries = read_json(EVIDENCE / "query-manifest.json")
    mutations = read_json(EVIDENCE / "mutation-manifest.json")
    require(dataset["manifestId"] == "poc-search-001-reference-dataset-v1", "Dataset manifest id drift")
    require(queries["manifestId"] == "poc-search-001-query-campaign-v1", "Query manifest id drift")
    require(mutations["manifestId"] == "poc-search-001-mutations-v1", "Mutation manifest id drift")
    require(dataset["contract"]["conversationCount"] == 10_000, "Reference conversation count drift")
    require(dataset["contract"]["transcriptRowCount"] == 1_000_000, "Reference row count drift")
    require(dataset["contract"]["seed"] == 2_026_081_001, "Reference seed drift")
    require(dataset["contract"]["generatorVersion"] == "search-generator-1.0.0", "Generator version drift")
    require(
        dataset["generationContractSha256"] == canonical_digest(dataset["contract"]),
        "Dataset generation contract SHA mismatch",
    )
    require(queries["contractSha256"] == canonical_digest(queries["contract"]), "Query contract SHA mismatch")
    require(mutations["contractSha256"] == canonical_digest(mutations["contract"]), "Mutation contract SHA mismatch")
    query_cases = queries["contract"]["queries"]
    eligible = [case for case in query_cases if case["latencyEligible"]]
    campaign = queries["contract"]["campaign"]
    require(len(query_cases) == 61, "Frozen query case count drift")
    require(len(eligible) == 34, "Frozen latency-eligible case count drift")
    require(campaign["warmupPerLatencyEligibleQuery"] == 5, "Warmup drift")
    require(campaign["measuredRepetitionsPerLatencyEligibleQuery"] == 30, "Repetition drift")
    require(len(eligible) * campaign["measuredRepetitionsPerLatencyEligibleQuery"] == 1_020, "Measured campaign drift")
    require(len(mutations["contract"]["operations"]) == 5, "Mutation count drift")
    expected_logical = "sha256:a3ad26892fc9f3abfcce26c3338a44a27dbe55e5048148f49edfc36c8fb8310a"
    require(dataset["expected"]["logicalDatasetSha256"] == expected_logical, "Logical dataset digest drift")
    sample = contract.generated_segment(424_242)["text"].encode("utf-8")
    require(
        hashlib.sha256(sample).hexdigest()
        == "d610a938b342af29d918450f37a6e7d880d8da2bd34a11731f7fef3106e467af",
        "Independent sample digest drift",
    )


def validate_gate_v02_draft() -> None:
    decision_path = ROOT / "docs/stage0/DEC-043-POC-SEARCH-STORAGE-UPDATE-GATES-DRAFT.md"
    document_path = ROOT / "docs/stage0/DORA_MVP1_POC_SEARCH_GATE_SET_STAGE0_V0_2_DRAFT.md"
    machine_path = ROOT / "docs/stage0/poc-search-gate-set-stage0-v0.2.draft.json"
    for path in (decision_path, document_path, machine_path):
        require(path.is_file(), f"Missing prospective gate artifact: {path.name}")
    decision_text = decision_path.read_text(encoding="utf-8")
    document_text = document_path.read_text(encoding="utf-8")
    require("Draft / Proposed" in decision_text, "DEC-043 must remain an unapproved draft")
    require("Owner decision record — intentionally blank" in decision_text, "DEC-043 decision block is missing")
    require("Status: **DRAFT_UNAPPROVED**" in document_text, "Gate Set v0.2 is not marked draft")
    require("Measured execution allowed: **no**" in document_text, "Draft gate set appears to allow execution")
    forbidden_historical_values = {
        "97.537984",
        "144.481302",
        "247.435677",
        "270.842256",
        "31484440781",
        "31486943815",
        "31487775567",
    }
    combined_draft = decision_text + document_text + machine_path.read_text(encoding="utf-8")
    for value in forbidden_historical_values:
        require(value not in combined_draft, f"Prospective draft references historical result value {value}")
    gate_set = read_json(machine_path)
    require(gate_set["gateSetVersion"] == "stage0-v0.2", "Draft gate-set version drift")
    require(
        gate_set["status"] == "DRAFT_UNAPPROVED"
        and gate_set["selectedOptionId"] is None
        and gate_set["approvedBy"] is None
        and gate_set["approvedOn"] is None
        and gate_set["benchmarkExecutionAllowed"] is False,
        "An owner selection or benchmark authorization was invented",
    )
    require(gate_set["historicalResultsAffected"] is False, "Draft gate set changes historical results")
    require(
        gate_set["independence"]["historicalDoraMeasurementsUsedToSetThresholds"] is False
        and gate_set["independence"]["historicalDoraResultsMayBeReclassified"] is False,
        "Prospective threshold independence is not explicit",
    )
    sources = gate_set["independence"]["sources"]
    require(
        len(sources) == 5
        and all(source.startswith(("https://www.sqlite.org/", "https://developer.android.com/")) for source in sources),
        "Draft rationale must use only primary SQLite/Android sources",
    )
    protocol = gate_set["protocol"]
    require(
        protocol["scale"]
        == {
            "conversationCount": 10_000,
            "transcriptSegmentCount": 1_000_000,
            "segmentsPerConversation": 100,
            "inputClassification": "deterministic_synthetic_text",
        },
        "Gate v0.2 reference scale drift",
    )
    require(protocol["requiredPhysicalProfiles"] == ["D1", "D2", "D3"], "Physical profile scope drift")
    require(
        protocol["emulatorRole"]["maySatisfyPhysicalPerformanceGate"] is False,
        "Emulator cannot satisfy the physical storage/update performance gate",
    )
    require(
        protocol["build"]["freshPairedBuildsPerProfile"] == 3
        and protocol["warmupsPerClassDatabaseBuild"] == 10
        and protocol["measuredOperationsPerClassDatabaseBuild"] == 100
        and protocol["measuredSamplesPerClassProfile"] == 300,
        "Gate v0.2 repetition contract drift",
    )
    require(
        protocol["aggregation"]["percentileMethod"] == "nearest_rank_ceil_p_times_n"
        and protocol["aggregation"]["acrossProfilesAndClasses"] == "maximum_no_averaging"
        and protocol["aggregation"]["outlierRemovalAllowed"] is False,
        "Gate v0.2 aggregation contract drift",
    )
    require(
        [operation["id"] for operation in protocol["operationClasses"]]
        == [
            "ADD_CONVERSATION_100",
            "UPDATE_SEGMENT_TEXT_1",
            "UPDATE_CONVERSATION_FILTER_1",
            "DELETE_SEGMENT_1",
            "DELETE_CONVERSATION_100",
        ],
        "Gate v0.2 operation-class inventory drift",
    )
    expected_thresholds = {
        "A": {
            "maxIndexIncrementalBytes": 268_435_456,
            "maxIndexOverheadRatio": 0.5,
            "maxIndexOverheadBytesPerSegment": 256,
            "maxSingleRowMaintenanceDeltaP95Ms": 16,
            "maxBulk100MaintenanceDeltaP95Ms": 100,
            "maxIndexedCommitP99Ms": 250,
            "maxVisibilityP95Ms": 100,
            "maxVisibilityP99Ms": 250,
        },
        "B": {
            "maxIndexIncrementalBytes": 536_870_912,
            "maxIndexOverheadRatio": 1.0,
            "maxIndexOverheadBytesPerSegment": 512,
            "maxSingleRowMaintenanceDeltaP95Ms": 50,
            "maxBulk100MaintenanceDeltaP95Ms": 250,
            "maxIndexedCommitP99Ms": 500,
            "maxVisibilityP95Ms": 250,
            "maxVisibilityP99Ms": 1000,
        },
        "C": {
            "maxIndexIncrementalBytes": 805_306_368,
            "maxIndexOverheadRatio": 1.5,
            "maxIndexOverheadBytesPerSegment": 768,
            "maxSingleRowMaintenanceDeltaP95Ms": 100,
            "maxBulk100MaintenanceDeltaP95Ms": 500,
            "maxIndexedCommitP99Ms": 700,
            "maxVisibilityP95Ms": 1000,
            "maxVisibilityP99Ms": 5000,
        },
    }
    actual_thresholds = {option["id"]: option["thresholds"] for option in gate_set["options"]}
    require(actual_thresholds == expected_thresholds, "Gate v0.2 option predicates drift")
    require(gate_set["draftEngineeringPreference"] == "B", "Draft preference must remain non-selected Option B")

    approved_decision_path = ROOT / "docs/stage0/DEC-043-POC-SEARCH-STORAGE-UPDATE-GATES.md"
    approved_document_path = ROOT / "docs/stage0/DORA_MVP1_POC_SEARCH_GATE_SET_STAGE0_V0_2.md"
    approved_machine_path = ROOT / "docs/stage0/poc-search-gate-set-stage0-v0.2.json"
    for path in (approved_decision_path, approved_document_path, approved_machine_path):
        require(path.is_file(), f"Missing approved prospective gate artifact: {path.name}")
    approved_decision = approved_decision_path.read_text(encoding="utf-8")
    approved_document = approved_document_path.read_text(encoding="utf-8")
    approved = read_json(approved_machine_path)
    require("Status: **Approved" in approved_decision, "DEC-043 approval state missing")
    require("Selected option: **B**" in approved_document, "Gate Set does not name Option B")
    require(
        "Measured execution allowed: **no**" in approved_document,
        "Contract approval was confused with execution authorization",
    )
    require(
        approved["status"] == "APPROVED"
        and approved["selectedOptionId"] == "B"
        and approved["approvedBy"] == "Project owner"
        and approved["approvedOn"] == "2026-08-11"
        and approved["benchmarkExecutionAllowed"] is False,
        "Approved Option B or its execution hold drifted",
    )
    require(
        approved["selectedOption"]["thresholds"] == expected_thresholds["B"],
        "Approved Option B predicates differ from the prospective draft",
    )
    require(
        approved["executionAuthorization"]
        == {
            "status": "WITHHELD_BY_PROJECT_OWNER",
            "authorizedBy": None,
            "authorizedOn": None,
            "requiresLaterRecordedOwnerAuthorization": True,
            "blockers": [
                "EXACT_COMPONENT_LICENSE_NOTICE_REVIEW_NOT_APPROVED",
                "GATE_V02_PAIRED_HARNESS_NOT_VERIFIED",
                "PHYSICAL_D1_D2_D3_AVAILABILITY_NOT_CONFIRMED",
            ],
        },
        "Measured-execution blocker set drift",
    )
    require(
        approved["independence"]["historicalDoraMeasurementsUsedToSetThresholds"] is False
        and approved["historicalResultsAffected"] is False,
        "Approved prospective decision is not independent of historical results",
    )
    approved_text = approved_decision + approved_document + approved_machine_path.read_text(encoding="utf-8")
    for value in forbidden_historical_values:
        require(value not in approved_text, f"Approved prospective contract references historical result {value}")
    require(
        file_digest(approved_machine_path)
        == "sha256:7898293eae8d896b72b04dddc222a0cf2e726afd41375ae8a8402b996ef70858",
        "Approved gate-set digest drift",
    )


def validate_module_wiring() -> None:
    settings = (ROOT / "android" / "settings.gradle.kts").read_text(encoding="utf-8")
    catalog = (ROOT / "android" / "gradle" / "libs.versions.toml").read_text(encoding="utf-8")
    build = (ROOT / "android" / "poc" / "search" / "build.gradle.kts").read_text(encoding="utf-8")
    repository = (
        ROOT
        / "android/poc/search/src/main/kotlin/com/monumentogram/dora/poc/search/query/SearchRepository.kt"
    ).read_text(encoding="utf-8")
    compiler = (
        ROOT
        / "android/poc/search/src/main/kotlin/com/monumentogram/dora/poc/search/query/SafeFtsQueryCompiler.kt"
    ).read_text(encoding="utf-8")
    workflow = (ROOT / ".github/workflows/poc-search-full.yml").read_text(encoding="utf-8")
    android_ci = (ROOT / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")
    require('include(":poc:search")' in settings, "Search PoC module not included")
    require('room = "2.8.4"' in catalog, "Room version is not pinned")
    require("androidx.room.runtime" in build and "androidx.room.compiler" in build, "Room dependencies missing")
    require("SimpleSQLiteQuery" in repository and "MATCH ?" in repository, "MATCH is not parameter-bound")
    require("arguments += it" in repository, "Compiled MATCH argument is not bound")
    require(
        "FROM transcript_segments_fts" in repository
        and "CROSS JOIN transcript_segments AS s" in repository
        and "CROSS JOIN conversations AS c" in repository,
        "Filtered MATCH queries must keep FTS4 as the driving table",
    )
    require(
        'SimpleSQLiteQuery("EXPLAIN QUERY PLAN $sql", arguments)' in repository,
        "Bound EXPLAIN QUERY PLAN support is missing",
    )
    require(
        'SearchMode.EXACT -> tokens.joinToString(" AND ")' in compiler
        and 'SearchMode.PREFIX -> tokens.joinToString(" AND ")' in compiler,
        "Literal token compilation policy missing",
    )
    forbidden = ("fts5", "elasticsearch", "meilisearch", "algolia", "embedding", "vector database")
    combined = (catalog + build).lower()
    require(not any(item in combined for item in forbidden), "Forbidden search dependency added")
    ignore = (ROOT / ".gitignore").read_text(encoding="utf-8")
    require("android/poc/search/benchmark-output/" in ignore, "Benchmark output is not ignored")
    required_files = (
        "QueryCampaignRunner.kt",
        "MutationRunner.kt",
        "RebuildRunner.kt",
        "FullSearchBenchmarkInstrumentedTest.kt",
        "BenchmarkCheckpointWriter.kt",
        "FtsQueryPlanPolicy.kt",
        "TargetedScaleQueryPlanInstrumentedTest.kt",
        "StorageUpdateGateV02Contract.kt",
        "StorageUpdateGateV02Harness.kt",
        "StorageUpdateGateV02Writer.kt",
        "StorageUpdateGateV02InstrumentedTest.kt",
    )
    source_root = ROOT / "android/poc/search/src/androidTest/kotlin/com/monumentogram/dora/poc/search"
    for name in required_files:
        require((source_root / name).is_file(), f"Missing benchmark source {name}")
    plan_policy = (source_root / "FtsQueryPlanPolicy.kt").read_text(encoding="utf-8")
    checkpoint_writer = (source_root / "BenchmarkCheckpointWriter.kt").read_text(encoding="utf-8")
    targeted_test = (source_root / "TargetedScaleQueryPlanInstrumentedTest.kt").read_text(
        encoding="utf-8"
    )
    require(
        "readableDatabase.query(query)" in plan_policy,
        "EXPLAIN must use the cursor query path rather than Room statement execution",
    )
    require(
        "context.filesDir" in checkpoint_writer
        and "context.filesDir" in targeted_test
        and 'run-as "${POC_SEARCH_TEST_PACKAGE}"' in workflow
        and ":poc:search:assembleDebugAndroidTest" in workflow
        and "shell am instrument -w -r" in workflow
        and "files/poc-search-001/" in workflow
        and "files/poc-search-benchmark/" not in workflow
        and ":poc:search:connectedDebugAndroidTest" not in workflow,
        "Sanitized checkpoints must survive instrumentation for run-as extraction",
    )
    require(
        'json.load(open(sys.argv[1], encoding="utf-8"))' in workflow
        and 'assert value["passed"] is True' in workflow,
        "Targeted observation is not parsed and validated before artifact upload",
    )
    require(
        (ROOT / "tools/combine_poc_search_checkpoints.py").is_file(),
        "Missing checkpoint combiner",
    )
    require(
        (ROOT / "tools/combine_poc_search_gate_v02.py").is_file(),
        "Missing stage0-v0.2 paired-checkpoint combiner",
    )
    require(
        "TargetedScaleQueryPlanInstrumentedTest" in workflow
        and "timeout-minutes: 10" in workflow,
        "Full-scale source-filter preflight is not hard-bounded",
    )
    require(
        "needs: targeted-scale-query-plan" in workflow
        and "fail-fast: false" in workflow
        and all(f"phase: {phase}" in workflow for phase in ("query", "rebuild", "secondary")),
        "Full benchmark is not gated and checkpointed as three independent jobs",
    )
    require(
        "combine_poc_search_checkpoints.py" in workflow
        and "timeout-minutes: 360" not in workflow,
        "Checkpoint finalization or bounded runtime policy missing",
    )
    require(
        "check_poc_search_run_readiness.py" in workflow,
        "Measured workflow does not fail closed on gate/IP readiness",
    )
    gate_test = (source_root / "StorageUpdateGateV02InstrumentedTest.kt").read_text(encoding="utf-8")
    gate_writer = (source_root / "StorageUpdateGateV02Writer.kt").read_text(encoding="utf-8")
    gate_harness = (source_root / "StorageUpdateGateV02Harness.kt").read_text(encoding="utf-8")
    require(
        'buildConfigField("boolean", "GATE_V02_EXECUTION_ALLOWED", "false")' in build
        and 'create("benchmark")' in build
        and 'initWith(getByName("release"))' in build,
        "Option B build binding or release-like benchmark variant drift",
    )
    require(
        "check(BuildConfig.GATE_V02_EXECUTION_ALLOWED)" in gate_test
        and gate_test.index("check(BuildConfig.GATE_V02_EXECUTION_ALLOWED)")
        < gate_test.index("StorageUpdateGateV02Harness(targetContext).run(config)"),
        "Formal harness does not fail before database work when execution is withheld",
    )
    require(
        "writeFailure" in gate_test
        and 'put("checkpointStatus", "FAIL")' in gate_writer
        and "warmupSamples" in gate_writer
        and "measuredSamples" in gate_writer,
        "FAIL or raw paired samples are not durably checkpointed",
    )
    require(
        "GateV02DatabaseKind.CONTROL" in gate_harness
        and "GateV02DatabaseKind.INDEXED" in gate_harness
        and "PRAGMA wal_checkpoint(TRUNCATE)" in gate_harness
        and 'database.execSQL("VACUUM")' in gate_harness,
        "Paired control/indexed storage normalization is incomplete",
    )
    require(
        "ubuntu-latest" not in workflow
        and workflow.count("runs-on: ubuntu-24.04") == 4
        and workflow.count('java-version: "17.0.19+10"') == 3,
        "Measured workflow host/JDK environment is not pinned",
    )
    require(
        workflow.count("verify_poc_search_android_sdk.py") == 2
        and "SYSTEM_IMAGE_ARCHIVE_SHA256" in build,
        "Measured workflow does not verify the pinned system-image provenance",
    )
    require(
        workflow.count("retention-days: 90") >= 3,
        "Actions evidence buffer is shorter than the durable-import review window",
    )
    require(
        "poc_search_dependency_inventory.py --check" in workflow
        and "poc_search_dependency_inventory.py --check" in android_ci,
        "Resolved dependency artifact digests are not verified in CI",
    )
    require(
        "poc_search_license_notice_inventory.py --check" in workflow
        and "poc_search_license_notice_inventory.py --check" in android_ci,
        "Exact license/NOTICE inventory is not verified in CI",
    )


def validate_approved_contract_still_blocks_measured_run() -> None:
    expected = run_readiness.EXECUTION_WITHHELD_MESSAGE
    try:
        run_readiness.main()
    except ValueError as error:
        require(str(error) == expected, "Measured-run readiness blocker drift")
    else:
        fail("Approved Option B must still block execution without later owner authorization")
    future_authorized = deepcopy(
        read_json(ROOT / "docs/stage0/poc-search-gate-set-stage0-v0.2.json")
    )
    future_authorized["benchmarkExecutionAllowed"] = True
    future_authorized["executionAuthorization"].update(
        {
            "status": "AUTHORIZED_BY_PROJECT_OWNER",
            "authorizedBy": "Project owner",
            "authorizedOn": "2099-01-01",
            "blockers": [],
        }
    )
    run_readiness.validate_gate_set(future_authorized)


def locked_components(lock_path: Path) -> dict[str, list[str]]:
    selected_configurations = {
        "_agp_internal_benchmark_kspClasspath",
        "_agp_internal_debug_kspClasspath",
        "benchmarkAndroidTestRuntimeClasspath",
        "benchmarkRuntimeClasspath",
        "debugAndroidTestRuntimeClasspath",
        "debugRuntimeClasspath",
    }
    components: dict[str, list[str]] = {}
    for raw_line in lock_path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        coordinate, raw_configurations = line.split("=", 1)
        scopes = sorted(selected_configurations.intersection(raw_configurations.split(",")))
        if scopes:
            components[coordinate] = scopes
    return components


def validate_dependency_and_ip_inventory() -> None:
    lock_path = ROOT / "android/poc/search/gradle.lockfile"
    inventory_path = EVIDENCE / "dependency-inventory.json"
    ip_path = EVIDENCE / "ip-evaluation.json"
    require(lock_path.is_file(), "POC-SEARCH-001 dependency lock is missing")
    require(inventory_path.is_file(), "POC-SEARCH-001 dependency inventory is missing")
    require(ip_path.is_file(), "POC-SEARCH-001 IP assessment is missing")
    expected = locked_components(lock_path)
    require(bool(expected), "Search dependency lock has no measured-run configurations")
    inventory = read_json(inventory_path)
    require(inventory["pocId"] == "POC-SEARCH-001", "Dependency inventory PoC id mismatch")
    require(
        inventory["inventoryStatus"] == "COMPLETE_UNREVIEWED",
        "Dependency inventory must not claim an unrecorded review",
    )
    lock_sha = hashlib.sha256(lock_path.read_bytes().replace(b"\r\n", b"\n")).hexdigest()
    require(inventory["lock"]["sha256"] == lock_sha, "Dependency inventory lock SHA mismatch")
    require(
        set(inventory["lock"]["configurations"])
        == {
            "_agp_internal_benchmark_kspClasspath",
            "_agp_internal_debug_kspClasspath",
            "benchmarkAndroidTestRuntimeClasspath",
            "benchmarkRuntimeClasspath",
            "debugAndroidTestRuntimeClasspath",
            "debugRuntimeClasspath",
        },
        "Dependency inventory configuration scope drift",
    )
    components = {component["coordinate"]: component for component in inventory["components"]}
    require(len(components) == inventory["componentCount"], "Dependency inventory count mismatch")
    require(set(components) == set(expected), "Dependency inventory does not exactly cover the lock")
    sha_pattern = re.compile(r"^[0-9a-f]{64}$")
    for coordinate, scopes in expected.items():
        component = components[coordinate]
        require(component["scopes"] == scopes, f"Dependency scope mismatch: {coordinate}")
        require(component["licenseMetadataStatus"] == "PRESENT", f"Missing POM metadata: {coordinate}")
        require(bool(component["artifacts"]), f"Missing artifact digests: {coordinate}")
        filenames: set[str] = set()
        for artifact in component["artifacts"]:
            require(artifact["filename"] not in filenames, f"Duplicate artifact filename: {coordinate}")
            filenames.add(artifact["filename"])
            require(bool(sha_pattern.fullmatch(artifact["sha256"])), f"Bad artifact SHA: {coordinate}")
            require(artifact["bytes"] > 0, f"Empty dependency artifact: {coordinate}")
            require(
                artifact["sourceUrl"].startswith("https://")
                and "latest" not in artifact["sourceUrl"].lower(),
                f"Unpinned dependency source: {coordinate}",
            )
    ip_evaluation = read_json(ip_path)
    require(ip_evaluation["pocId"] == "POC-SEARCH-001", "IP assessment PoC id mismatch")
    require(
        ip_evaluation["dependencyInventory"]["sha256"] == file_digest(inventory_path),
        "IP assessment references the wrong dependency inventory",
    )
    require(
        ip_evaluation["dependencyInventory"]["componentCount"] == len(expected),
        "IP assessment dependency count mismatch",
    )
    license_path = EVIDENCE / "license-notice-inventory.json"
    require(license_path.is_file(), "Exact license/NOTICE inventory is missing")
    license_inventory = read_json(license_path)
    require(
        license_inventory["pocId"] == "POC-SEARCH-001"
        and license_inventory["reviewStatus"] == "EVIDENCE_COMPLETE",
        "License/NOTICE evidence status drift",
    )
    require(
        license_inventory["sourceDependencyInventory"]
        == {
            "locator": "docs/evidence/poc-search-001/dependency-inventory.json",
            "sha256": file_digest(inventory_path).removeprefix("sha256:"),
            "componentCount": 66,
        },
        "License/NOTICE evidence is not bound to the exact dependency inventory",
    )
    reviewed_components = {
        component["coordinate"]: component for component in license_inventory["components"]
    }
    require(set(reviewed_components) == set(components), "License review graph is incomplete")
    for coordinate, reviewed in reviewed_components.items():
        require(reviewed["scopes"] == expected[coordinate], f"Reviewed scope drift: {coordinate}")
        require(bool(reviewed["effectiveSpdx"]), f"Effective license missing: {coordinate}")
        require(bool(reviewed["licenseEvidence"]), f"License evidence missing: {coordinate}")
        expected_artifacts = {
            value["filename"]: value["sha256"] for value in components[coordinate]["artifacts"]
        }
        require(
            reviewed["artifactSha256"] == expected_artifacts,
            f"License review artifact digest drift: {coordinate}",
        )
        require(
            reviewed["stage0EvaluationDisposition"] == "ACCEPTABLE_PENDING_OWNER_APPROVAL"
            and reviewed["productionAdmission"] == "NOT_REVIEWED",
            f"Immutable license packet generation state drift: {coordinate}",
        )
        for archive in reviewed["embeddedLicenseNoticeEntries"]:
            for entry in archive["entries"]:
                if entry["kind"] == "license":
                    require(bool(entry.get("spdx")), f"Embedded license unclassified: {coordinate}")
                    require(
                        entry["spdx"] in reviewed["effectiveSpdx"],
                        f"Embedded license omitted from effective set: {coordinate}",
                    )
    require(
        license_inventory["summary"]
        == {
            "componentCount": 66,
            "componentsWithEffectiveLicense": 66,
            "effectiveLicenseCounts": {
                "Apache-2.0": 62,
                "BSD-2-Clause": 1,
                "BSD-3-Clause": 2,
                "EPL-1.0": 1,
                "MIT": 1,
            },
            "embeddedLicenseEntryCount": 19,
            "embeddedNoticeEntryCount": 3,
            "unresolvedLicenseCoordinates": [],
            "unresolvedLicenseFiles": [],
            "unresolvedNoticeFiles": [],
        },
        "Exact license/NOTICE summary drift",
    )
    require(
        reviewed_components[
            "org.xerial:sqlite-jdbc:3.41.2.2"
        ]["effectiveSpdx"]
        == ["Apache-2.0", "BSD-2-Clause"],
        "sqlite-jdbc embedded BSD-2-Clause evidence was lost",
    )
    auto_value_evidence = reviewed_components[
        "com.google.auto.value:auto-value-annotations:1.6.3"
    ]["licenseEvidence"]
    require(
        any(
            item.get("kind") == "upstream-release-tag-license"
            and item.get("tag") == "auto-value-1.6.3"
            and item.get("sha256")
            == "cfc7749b96f63bd31c3c42b5c471bf756814053e847c10f3eb003417bc523d30"
            for item in auto_value_evidence
        ),
        "Auto Value exact tagged upstream license evidence is missing",
    )
    require(
        ip_evaluation["unresolvedLicenseCoordinates"] == [],
        "Resolved effective-license gaps remain marked unresolved",
    )
    require(
        ip_evaluation["schemaVersion"] == 3
        and ip_evaluation["evaluationStatus"] == "EVALUATION_APPROVED"
        and ip_evaluation["ipPrecondition"] == "SATISFIED_FOR_STAGE0"
        and ip_evaluation["historicalExecutionPrecondition"]
        == "NOT_PROVEN_NOT_RETROACTIVE"
        and ip_evaluation["futureMeasuredExecution"]
        == "BLOCKED_PENDING_PHYSICAL_D1_D3_AND_OWNER_EXECUTION_AUTHORIZATION",
        "Stage 0 IP approval or its non-retroactive execution boundary drift",
    )
    require(
        ip_evaluation["dependencyInventory"]["status"]
        == "EVALUATION_APPROVED_STAGE0"
        and ip_evaluation["dependencyInventory"]["licenseNoticeInventory"]
        == {
            "locator": "docs/evidence/poc-search-001/license-notice-inventory.json",
            "sha256": file_digest(license_path),
            "componentsWithEffectiveLicense": 66,
            "unresolvedLicenseCoordinates": 0,
            "unresolvedLicenseFiles": 0,
            "unresolvedNoticeFiles": 0,
        },
        "IP assessment does not bind the exact license/NOTICE evidence",
    )
    require(
        ip_evaluation["ownerDecision"]
        == {
            "id": "OD-13",
            "approvedOn": "2026-08-11",
            "approvedBy": "Project owner",
            "scope": "Stage 0 evaluation only",
        },
        "OD-13 Stage 0 IP approval scope drift",
    )
    require(
        ip_evaluation["roleAssignmentDecision"]
        == {"id": "OD-11", "approvedOn": "2026-08-11", "scope": "Stage 0 evaluation only"},
        "OD-11 reviewer/provenance scope drift",
    )
    require(
        ip_evaluation["stage0Approval"]
        == {
            "status": "EVALUATION_APPROVED",
            "approvedBy": "Project owner",
            "approvedOn": "2026-08-11",
            "componentCount": 66,
            "reviewedDependencyInventorySha256": file_digest(inventory_path),
            "reviewedLicenseNoticeInventorySha256": file_digest(license_path),
            "reviewedSystemImageSha256": (
                "sha256:b1bb0769d0bed7698e61f203d7dc9bf6e7c37cd01a39d0d8788a11186bc78160"
            ),
            "restrictions": [
                "Internal synthetic POC-SEARCH-001 research in Stage 0 only.",
                "This decision does not authorize a measured benchmark; physical D1/D3 and later owner execution authorization remain required.",
                "This decision is not production Legal or independent production Security approval.",
                "FTS4, the PoC schema, and every reviewed dependency remain outside production admission.",
            ],
        },
        "Exact OD-13 review object or restriction set drift",
    )
    require(
        ip_evaluation["dependencyReviewBlockers"] == [],
        "Completed Stage 0 IP review still carries an artifact-approval blocker",
    )
    assignments = ip_evaluation["reviewAssignments"]
    for role in ("product", "ipPolicy", "engineeringSecurity"):
        require(
            assignments[role]["reviewer"] == "Project owner"
            and assignments[role]["status"] == "ASSIGNED"
            and assignments[role]["scope"] == "Stage 0 evaluation only",
            f"Stage 0 reviewer assignment drift: {role}",
        )
    require(
        assignments["ipPolicy"]["replacesProductionLegal"] is False
        and assignments["productionLegal"]
        == {"reviewer": None, "status": "UNASSIGNED_PRODUCTION_BLOCKED"},
        "Stage 0 IP role must not imply production Legal approval",
    )
    require(
        assignments["engineeringSecurity"]["replacesIndependentProductionSecurityReview"]
        is False
        and assignments["productionSecurity"]
        == {
            "reviewer": None,
            "status": "INDEPENDENT_REVIEW_REQUIRED_BEFORE_PRODUCTION",
        },
        "Stage 0 Engineering/Security role must not replace production review",
    )
    review_locator = ip_evaluation["reviewEvidenceLocator"]
    require(
        review_locator == "docs/evidence/poc-search-001/ip-stage0-evaluation-review.md"
        and (ROOT / review_locator).is_file(),
        "Stage 0 IP review record is missing",
    )
    review_text = (ROOT / review_locator).read_text(encoding="utf-8")
    require(
        "EVALUATION_APPROVED — STAGE 0 ONLY" in review_text
        and "Artifact approval decision: `OD-13`" in review_text
        and "This completed block removes only the Stage 0 IP precondition" in review_text,
        "Completed Stage 0 IP review wording drift",
    )
    owner_registry_text = (
        ROOT / "docs/stage0/DORA_MVP1_STAGE0_OWNER_DECISIONS.md"
    ).read_text(encoding="utf-8")
    require(
        "## OD-13." in owner_registry_text
        and file_digest(inventory_path) in owner_registry_text
        and file_digest(license_path) in owner_registry_text
        and "sha256:b1bb0769d0bed7698e61f203d7dc9bf6e7c37cd01a39d0d8788a11186bc78160"
        in owner_registry_text,
        "OD-13 owner registry record is missing or not bound to the exact package",
    )
    platform_artifacts = {artifact["artifactId"]: artifact for artifact in ip_evaluation["platformArtifacts"]}
    system_image = platform_artifacts["android-system-image-google-apis-x86_64-api36"]
    provenance = read_json(EVIDENCE / "android-system-image-provenance.json")
    require(provenance["package"] == "system-images;android-36;google_apis;x86_64", "System image package drift")
    require(provenance["revision"] == 7, "System image revision drift")
    require(provenance["archive"]["bytes"] == 1_895_447_397, "System image archive size drift")
    require(
        provenance["archive"]["officialSha1"] == "c6bf44bdcd885bb902b4ba752d111a073ad7a817",
        "System image official SHA-1 drift",
    )
    require(
        provenance["archive"]["sha256"]
        == "b1bb0769d0bed7698e61f203d7dc9bf6e7c37cd01a39d0d8788a11186bc78160",
        "System image SHA-256 drift",
    )
    require(
        system_image["sha256"] == "sha256:" + provenance["archive"]["sha256"],
        "IP assessment system-image digest mismatch",
    )
    require(
        system_image["evidenceLocator"]
        == "docs/evidence/poc-search-001/android-system-image-provenance.json",
        "IP assessment system-image provenance locator mismatch",
    )
    runtime_identity = provenance["runtimeIdentity"]
    require(
        runtime_identity
        == {
            "imageId": "system-images;android-36;google_apis;x86_64",
            "buildFingerprint": (
                "google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.F3/"
                "13894323:userdebug/dev-keys"
            ),
            "androidApi": 36,
            "abi": "x86_64",
            "sqliteVersion": "3.44.3",
            "digestScope": "containing-system-image",
            "separateSQLiteLibraryDownloadedOrRedistributed": False,
            "separateSQLiteBinaryDigestRequiredForStage0": False,
            "decisionId": "OD-11",
            "productionReconsiderationRequired": True,
            "runtimeEvidenceLocator": (
                "docs/evidence/poc-search-001/runs/31487775567/environment.json"
            ),
        },
        "Pinned system-image runtime identity or SQLite boundary drift",
    )
    for artifact in platform_artifacts.values():
        require(
            artifact["licenseReviewState"] == "EVALUATION_APPROVED"
            and artifact["evaluationRightsConfirmed"] is True
            and artifact["approvalDecisionId"] == "OD-13",
            "Platform artifact is not bound to the Stage 0-only OD-13 approval",
        )
    sqlite_artifact = platform_artifacts["android-platform-sqlite"]
    require(
        sqlite_artifact["sha256"] == system_image["sha256"]
        and sqlite_artifact["digestScope"] == "containing-system-image"
        and sqlite_artifact["separateBinaryDigestRequiredForStage0"] is False
        and sqlite_artifact["provenanceMethodDecision"] == "APPROVED_FOR_STAGE0_BY_OD_11"
        and sqlite_artifact["productionReconsiderationRequired"] is True,
        "SQLite must use the owner-approved Stage 0 containing-system-image provenance boundary",
    )
    require(
        sqlite_artifact["runtimeIdentity"]
        == {
            "imageId": runtime_identity["imageId"],
            "imageRevision": 7,
            "buildFingerprint": runtime_identity["buildFingerprint"],
            "androidApi": runtime_identity["androidApi"],
            "abi": runtime_identity["abi"],
            "sqliteVersion": runtime_identity["sqliteVersion"],
        },
        "SQLite Stage 0 runtime identity is incomplete",
    )


def validate_gate_v02_readiness_evidence() -> None:
    gate_path = ROOT / "docs/stage0/poc-search-gate-set-stage0-v0.2.json"
    gate_digest = file_digest(gate_path)
    devices = read_json(EVIDENCE / "device-availability-stage0-v0.2.json")
    require(
        devices["gateSetVersion"] == "stage0-v0.2"
        and devices["status"] == "BLOCKED_MISSING_D1_D3"
        and devices["allRequiredPhysicalProfilesAvailable"] is False
        and devices["benchmarkExecutionEligible"] is False,
        "Physical D1-D3 availability blocker drift",
    )
    profiles = {value["profileId"]: value for value in devices["profiles"]}
    require(set(profiles) == {"D1", "D2", "D3"}, "D1-D3 availability inventory incomplete")
    require(
        profiles["D1"]["availability"] == "unknown"
        and profiles["D3"]["availability"] == "unknown"
        and profiles["D2"]["availability"] == "available"
        and profiles["D2"]["inventoryId"] == "owner-phone-001"
        and profiles["D2"]["sanitizedDevice"]["model"] == "SM-S908B",
        "Physical availability was invented or known D2 was omitted",
    )
    require(
        all(value["gateExecutionEligible"] is False for value in profiles.values()),
        "A device was marked gate-ready before the fresh authorized preflight",
    )

    harness = read_json(EVIDENCE / "gate-v02-harness-readiness.json")
    require(
        harness["gateSetVersion"] == "stage0-v0.2"
        and harness["gateSetSha256"] == gate_digest
        and harness["selectedOptionId"] == "B"
        and harness["formalBenchmarkExecuted"] is False
        and harness["formalCheckpointCount"] == 0
        and harness["benchmarkExecutionAllowed"] is False,
        "Harness readiness evidence overstates formal execution",
    )
    require(
        harness["status"]
        in {"IMPLEMENTED_COMPILE_VERIFIED_RUNTIME_SMOKE_PENDING", "VERIFIED"},
        "Unknown harness verification state",
    )
    checks = {value["id"]: value for value in harness["checks"]}
    require(
        checks["debug-androidtest-compile"]["outcome"] == "PASS"
        and checks["benchmark-androidtest-compile"]["outcome"] == "PASS",
        "Committed harness compilation evidence is incomplete",
    )
    if harness["status"] == "VERIFIED":
        require(
            bool(re.fullmatch(r"[0-9a-f]{40}", harness["implementationCommit"]))
            and checks["paired-harness-runtime-smoke"]["outcome"] == "PASS",
            "VERIFIED harness lacks commit-bound runtime smoke evidence",
        )
    else:
        require(
            harness["implementationCommit"] is None
            and checks["paired-harness-runtime-smoke"]["outcome"] == "PENDING_CI",
            "Pending harness evidence invented a committed runtime result",
        )


def validate_durable_evidence_ledger() -> None:
    ledger_path = EVIDENCE / "evidence-ledger.json"
    require(ledger_path.is_file(), "Durable evidence ledger is missing")
    expected = evidence_ledger.build_ledger(ROOT)
    require(read_json(ledger_path) == expected, "Durable evidence ledger is stale or incomplete")
    records = expected["records"]
    require(any(record["classification"] == "VALID_FAIL" for record in records), "Valid FAIL not retained")
    require(any(record["classification"] == "TARGETED_PASS" for record in records), "Targeted PASS not retained")
    for record in records:
        audit = record.get("environmentAudit")
        if audit and audit["correctionRequired"]:
            require(audit["derivedKinds"] == ["emulator"], "Historical environment correction drift")
            require(audit["recordedKinds"] == ["physical"], "Historical raw kind unexpectedly changed")


def index_digest(connection: sqlite3.Connection) -> str:
    digest = hashlib.sha256()
    for row_id, text in connection.execute("SELECT rowid, text FROM transcript_segments_fts ORDER BY rowid"):
        digest.update(f"F|{row_id}|{text}\n".encode("utf-8"))
    return "sha256:" + digest.hexdigest()


def host_sqlite_smoke() -> None:
    with tempfile.TemporaryDirectory(prefix="dora-poc-search-") as temporary:
        path = Path(temporary) / "smoke.db"
        connection = sqlite3.connect(path)
        try:
            options = {row[0] for row in connection.execute("PRAGMA compile_options")}
            require(any("ENABLE_FTS3" in option or "ENABLE_FTS4" in option for option in options), "Host SQLite lacks FTS4")
            connection.executescript(
                """
                PRAGMA foreign_keys=ON;
                CREATE TABLE conversations(
                    conversation_id INTEGER PRIMARY KEY,
                    title TEXT NOT NULL,
                    started_at_ms INTEGER NOT NULL,
                    source_type TEXT NOT NULL,
                    participant_label TEXT NOT NULL
                );
                CREATE TABLE transcript_segments(
                    segment_id INTEGER PRIMARY KEY,
                    conversation_id INTEGER NOT NULL REFERENCES conversations(conversation_id) ON DELETE CASCADE,
                    sequence INTEGER NOT NULL,
                    start_ms INTEGER NOT NULL,
                    end_ms INTEGER NOT NULL,
                    language TEXT NOT NULL,
                    text TEXT NOT NULL
                );
                CREATE INDEX index_conversations_source_type ON conversations(source_type);
                CREATE INDEX index_transcript_segments_conversation_id ON transcript_segments(conversation_id);
                CREATE VIRTUAL TABLE transcript_segments_fts USING fts4(text, tokenize=unicode61 "remove_diacritics=0");
                """
            )
            conversations = [
                (
                    conversation_id,
                    contract.conversation_title(conversation_id),
                    contract.conversation_started_at_ms(conversation_id),
                    contract.conversation_source(conversation_id),
                    contract.participant_label(conversation_id),
                )
                for conversation_id in range(1, 101)
            ]
            connection.executemany("INSERT INTO conversations VALUES(?,?,?,?,?)", conversations)
            segments = [contract.generated_segment(row_id) for row_id in range(1, 10_001)]
            connection.executemany(
                "INSERT INTO transcript_segments VALUES(?,?,?,?,?,?,?)",
                [
                    (
                        row["segmentId"],
                        row["conversationId"],
                        row["sequence"],
                        row["startMs"],
                        row["endMs"],
                        row["language"],
                        row["text"],
                    )
                    for row in segments
                ],
            )
            connection.execute(
                "INSERT INTO transcript_segments_fts(rowid,text) SELECT segment_id,text FROM transcript_segments ORDER BY segment_id"
            )
            connection.commit()
            compiled = contract.compile_query("project", "EXACT", False)
            actual = [
                row[0]
                for row in connection.execute(
                    "SELECT s.segment_id FROM transcript_segments s JOIN transcript_segments_fts f ON f.rowid=s.segment_id WHERE f.text MATCH ? ORDER BY s.segment_id",
                    (compiled.compiled_match,),
                )
            ]
            expected = [
                row["segmentId"]
                for row in segments
                if contract.query_matches(contract.tokenize(row["text"]), compiled, "EXACT")
            ]
            require(actual == expected, "Host FTS4 search differs from independent small-dataset oracle")
            source_query = next(
                case
                for case in read_json(EVIDENCE / "query-manifest.json")["contract"]["queries"]
                if case["id"] == "Q-SEARCH-SOURCE"
            )
            source_compiled = contract.compile_query(
                source_query["rawQuery"], source_query["mode"], True
            )
            source_sql = (
                "SELECT COUNT(*) FROM transcript_segments_fts "
                "CROSS JOIN transcript_segments AS s "
                "ON s.segment_id = transcript_segments_fts.rowid "
                "CROSS JOIN conversations AS c "
                "ON c.conversation_id = s.conversation_id "
                "WHERE transcript_segments_fts MATCH ? AND c.source_type = ?"
            )
            source_arguments = (
                source_compiled.compiled_match,
                source_query["filters"]["sourceType"],
            )
            plan = [
                row[3]
                for row in connection.execute(
                    "EXPLAIN QUERY PLAN " + source_sql,
                    source_arguments,
                )
            ]
            require(
                len(plan) >= 3
                and "transcript_segments_fts" in plan[0]
                and "VIRTUAL TABLE" in plan[0].upper(),
                f"Host plan is not FTS4-driving: {plan}",
            )
            require(
                "SEARCH S USING INTEGER PRIMARY KEY" in plan[1].upper()
                and "SEARCH C USING INTEGER PRIMARY KEY" in plan[2].upper(),
                f"Host plan lost canonical rowid lookups: {plan}",
            )
            expected_source_count = sum(
                1
                for row in segments
                if contract.query_matches(
                    contract.tokenize(row["text"]), source_compiled, source_query["mode"]
                )
                and contract.conversation_source(row["conversationId"])
                == source_query["filters"]["sourceType"]
            )
            require(
                connection.execute(source_sql, source_arguments).fetchone()[0]
                == expected_source_count,
                "FTS4-driving source-filter count differs from the independent oracle",
            )
            query_manifest = read_json(EVIDENCE / "query-manifest.json")
            for case in query_manifest["contract"]["queries"]:
                if case["category"] not in ("adversarial", "special-characters"):
                    continue
                normalized = contract.compile_query(
                    case["rawQuery"], case["mode"], any(value is not None for value in case["filters"].values())
                )
                require(normalized.status == case["normalization"]["status"], f"Compiler status drift: {case['id']}")
                require(normalized.compiled_match == case["normalization"]["compiledMatch"], f"Compiler expression drift: {case['id']}")
                if normalized.status == "READY":
                    connection.execute(
                        "SELECT COUNT(*) FROM transcript_segments_fts WHERE text MATCH ?",
                        (normalized.compiled_match,),
                    ).fetchone()
            require(
                connection.execute("SELECT COUNT(*) FROM conversations").fetchone()[0] == 100,
                "Adversarial query changed canonical tables",
            )
            update_id = 1_234
            updated_text = contract.generated_segment(update_id)["text"] + " smokeupdatedmarker"
            connection.execute("UPDATE transcript_segments SET text=? WHERE segment_id=?", (updated_text, update_id))
            connection.execute("UPDATE transcript_segments_fts SET text=? WHERE rowid=?", (updated_text, update_id))
            require(
                connection.execute(
                    "SELECT COUNT(*) FROM transcript_segments_fts WHERE text MATCH ?", ('"smokeupdatedmarker"',)
                ).fetchone()[0]
                == 1,
                "FTS update did not become visible",
            )
            connection.execute("DELETE FROM transcript_segments_fts WHERE rowid=2345")
            connection.execute("DELETE FROM transcript_segments WHERE segment_id=2345")
            require(
                connection.execute("SELECT COUNT(*) FROM transcript_segments").fetchone()[0]
                == connection.execute("SELECT COUNT(*) FROM transcript_segments_fts").fetchone()[0],
                "Delete left stale FTS rows",
            )
            connection.execute("DELETE FROM transcript_segments_fts")
            connection.execute(
                "INSERT INTO transcript_segments_fts(rowid,text) SELECT segment_id,text FROM transcript_segments ORDER BY segment_id"
            )
            first_digest = index_digest(connection)
            connection.execute("DELETE FROM transcript_segments_fts")
            connection.execute(
                "INSERT INTO transcript_segments_fts(rowid,text) SELECT segment_id,text FROM transcript_segments ORDER BY segment_id"
            )
            second_digest = index_digest(connection)
            require(first_digest == second_digest, "Host FTS4 logical rebuild is non-deterministic")
            require(connection.execute("PRAGMA integrity_check").fetchone()[0] == "ok", "Host SQLite integrity failed")
        finally:
            connection.close()


def gate_v02_checkpoint_fixture(profile: str, build: int, gate_digest: str) -> dict[str, Any]:
    def sample(commit_nanos: int, visibility_nanos: int | None) -> dict[str, Any]:
        return {
            "commitNanos": commit_nanos,
            "visibilityNanos": visibility_nanos,
            "correctnessPassed": True,
            "staleSuccessfulResponse": False,
            "crashed": False,
        }

    def operations(indexed: bool) -> list[dict[str, Any]]:
        return [
            {
                "operationClass": operation_id,
                "group": group,
                "cardinality": 100 if group == "bulk-100" else 1,
                "warmupSamples": [
                    sample(20_000_000 if indexed else 10_000_000, 5_000_000 if indexed else None)
                    for _ in range(10)
                ],
                "measuredSamples": [
                    sample(20_000_000 if indexed else 10_000_000, 5_000_000 if indexed else None)
                    for _ in range(100)
                ],
            }
            for operation_id, group in gate_v02_combiner.OPERATION_CLASSES.items()
        ]

    def observation(indexed: bool) -> dict[str, Any]:
        return {
            "kind": "INDEXED" if indexed else "CONTROL",
            "storage": {
                "mainDatabaseBytes": 1_342_177_280 if indexed else 1_073_741_824,
                "pageCount": 327_680 if indexed else 262_144,
                "pageSizeBytes": 4096,
                "walBytesAfterClose": 0,
                "shmBytesAfterClose": 0,
                "integrityCheck": "ok",
                "conversationCount": 10_000,
                "transcriptSegmentCount": 1_000_000,
                "ftsRowCount": 1_000_000 if indexed else None,
                "canonicalLogicalSha256": "sha256:" + "b" * 64,
                "missingCanonicalMappings": 0 if indexed else None,
                "missingIndexRows": 0 if indexed else None,
                "firstCheckpointBusy": 0,
                "secondCheckpointBusy": 0,
                "mainFileMatchesPageGeometry": True,
                "transientFilesCleared": True,
                "freeStorageBytesAfterNormalization": 10_000_000_000,
            },
            "operations": operations(indexed),
            "sqliteVersion": "3.44.3",
            "compileOptions": ["ENABLE_FTS3", "ENABLE_FTS4"],
            "finalIntegrityCheck": "ok",
            "finalConversationCount": 10_000,
            "finalTranscriptSegmentCount": 999_890,
            "finalFtsRowCount": 999_890 if indexed else None,
            "finalMissingCanonicalMappings": 0 if indexed else None,
            "finalMissingIndexRows": 0 if indexed else None,
            "deletedAfterRun": True,
        }

    control = observation(False)
    indexed = observation(True)
    return {
        "checkpointSchemaVersion": 2,
        "pocId": "POC-SEARCH-001",
        "gateSetVersion": "stage0-v0.2",
        "gateSetSha256": gate_digest,
        "selectedOptionId": "B",
        "benchmarkExecutionAllowedAtBuild": True,
        "harnessVersion": "0.2.0-poc-search-001",
        "commit": "a" * 40,
        "generatedAt": "2026-08-11T00:00:00Z",
        "formalGateEvidence": True,
        "config": {
            "profileId": profile,
            "freshBuildOrdinal": build,
            "conversationCount": 10_000,
            "transcriptSegmentCount": 1_000_000,
            "segmentsPerConversation": 100,
            "warmupsPerClass": 10,
            "measuredOperationsPerClass": 100,
            "smokeOnly": False,
            "compilationMode": "full_aot_recorded",
            "cooldownMinutesBeforeFreshBuild": 10,
        },
        "environment": {
            "kind": "physical",
            "buildType": "benchmark",
            "applicationDebuggable": False,
            "profileableByShellDeclared": True,
            "compilationMode": "full_aot_recorded",
            "abi": "arm64-v8a",
            "airplaneMode": True,
            "screenInteractive": True,
            "plugged": False,
            "batteryPercent": 60.0,
            "thermalStatus": 0,
            "screenBrightnessRaw": 51,
            "windowAnimationScale": 0.0,
            "transitionAnimationScale": 0.0,
            "animatorDurationScale": 0.0,
            "buildFingerprint": f"synthetic/{profile}/{build}",
            "sqliteVersion": "3.44.3",
            "sqliteCompileOptions": ["ENABLE_FTS3", "ENABLE_FTS4"],
        },
        "checkpointStatus": "COMPLETE",
        "complete": True,
        "pairOrder": gate_v02_combiner.PAIR_ORDERS[build],
        "control": control,
        "indexed": indexed,
        "storageDelta": {
            "indexIncrementalBytes": 268_435_456,
            "indexOverheadRatio": 0.25,
            "indexOverheadBytesPerSegment": 268.435456,
        },
        "storageAndPairingCorrect": True,
        "allCorrect": True,
    }


def validate_gate_v02_combiner() -> None:
    gate_path = ROOT / "docs/stage0/poc-search-gate-set-stage0-v0.2.json"
    gate = read_json(gate_path)
    gate_digest = file_digest(gate_path)
    require(
        gate_v02_combiner.nearest_rank([float(value) for value in range(1, 101)], 0.95)
        == 95.0
        and gate_v02_combiner.nearest_rank(
            [float(value) for value in range(1, 101)], 0.99
        )
        == 99.0,
        "Nearest-rank implementation drift",
    )
    try:
        gate_v02_combiner.validate_gate_set(gate, require_execution_authorized=True)
    except ValueError as error:
        require(
            str(error) == "Measured execution is not authorized by the Project owner",
            "Combiner execution guard drift",
        )
    else:
        fail("CLI combiner accepted the currently withheld gate set")

    checkpoints = [
        gate_v02_checkpoint_fixture(profile, build, gate_digest)
        for profile in gate_v02_combiner.PROFILES
        for build in gate_v02_combiner.BUILDS
    ]
    passing = gate_v02_combiner.evaluate_checkpoints(
        checkpoints,
        gate,
        gate_digest,
        "a" * 40,
        require_execution_authorized=False,
    )
    require(passing["result"]["status"] == "PASS", "Valid paired fixture did not pass")
    require(
        passing["aggregate"]["maximumNoAveraging"]["singleRowMaintenanceDeltaP95Ms"]
        == 10.0,
        "Paired maintenance delta aggregation drift",
    )
    inconclusive = gate_v02_combiner.evaluate_checkpoints(
        checkpoints[:-3],
        gate,
        gate_digest,
        "a" * 40,
        require_execution_authorized=False,
    )
    require(
        inconclusive["result"]["status"] == "INCONCLUSIVE"
        and len(inconclusive["missingProfileBuilds"]) == 3,
        "Missing physical profile did not remain INCONCLUSIVE",
    )
    failing_checkpoints = deepcopy(checkpoints)
    failing_sample = failing_checkpoints[0]["indexed"]["operations"][0]["measuredSamples"][0]
    failing_sample["correctnessPassed"] = False
    failing_sample["staleSuccessfulResponse"] = True
    failed = gate_v02_combiner.evaluate_checkpoints(
        failing_checkpoints,
        gate,
        gate_digest,
        "a" * 40,
        require_execution_authorized=False,
    )
    require(
        failed["result"]["status"] == "FAIL"
        and failed["failureCounts"]["staleSuccessfulResponses"] == 1,
        "Stale successful response was not retained as FAIL",
    )
    incomplete_checkpoints = deepcopy(checkpoints)
    incomplete_checkpoints[0] = {
        key: value
        for key, value in incomplete_checkpoints[0].items()
        if key not in {"control", "indexed", "storageDelta", "storageAndPairingCorrect", "allCorrect"}
    }
    incomplete_checkpoints[0].update(
        {
            "checkpointStatus": "FAIL",
            "complete": False,
            "failure": {
                "code": "GATE_V02_HARNESS_EXCEPTION",
                "type": "java.lang.IllegalStateException",
                "messageSha256": "c" * 64,
            },
        }
    )
    incomplete = gate_v02_combiner.evaluate_checkpoints(
        incomplete_checkpoints,
        gate,
        gate_digest,
        "a" * 40,
        require_execution_authorized=False,
    )
    require(
        incomplete["result"]["status"] == "FAIL"
        and incomplete["failureCounts"]["incompleteCheckpoints"] == 1
        and incomplete["failureCounts"]["candidateCrashes"] == 1,
        "Durable incomplete harness exception was not retained as FAIL",
    )
    unavailable_thermal = deepcopy(checkpoints)
    unavailable_thermal[0]["environment"]["thermalStatus"] = -1
    try:
        gate_v02_combiner.evaluate_checkpoints(
            unavailable_thermal,
            gate,
            gate_digest,
            "a" * 40,
            require_execution_authorized=False,
        )
    except ValueError as error:
        require(
            "thermal status is unavailable or exceeded LIGHT" in str(error),
            "Thermal evidence rejection reason drift",
        )
    else:
        fail("Unavailable API 28 thermal evidence was accepted silently")


def validate_result_if_present() -> None:
    index_path = EVIDENCE / "evidence-index.json"
    require(index_path.is_file(), "Evidence index is missing")
    evidence_index = read_json(index_path)
    require(evidence_index["pocId"] == "POC-SEARCH-001", "Evidence index PoC id mismatch")
    require(evidence_index["schemaVersion"] == 2, "Evidence index governance version drift")
    gate_contract = evidence_index["gateContract"]
    require(
        gate_contract["version"] == "stage0-v0.2"
        and gate_contract["status"] == "APPROVED"
        and gate_contract["complete"] is True
        and gate_contract["selectedOptionId"] == "B"
        and gate_contract["approvedBy"] == "Project owner"
        and gate_contract["approvedOn"] == "2026-08-11"
        and gate_contract["benchmarkExecutionAllowed"] is False,
        "Approved contract or separate execution hold drift",
    )
    require(
        gate_contract["historicalAssessment"]
        == {
            "version": "stage0-v0.1",
            "status": "INCOMPLETE_HISTORICAL_CONTRACT",
            "reclassified": False,
        },
        "Historical v0.1 assessment was reclassified",
    )
    require(
        gate_contract["blocker"] == run_readiness.EXECUTION_WITHHELD_MESSAGE,
        "Evidence-index execution blocker drift",
    )
    require(
        evidence_index["currentAssessment"]["assessmentId"] == "review-2026-08-11-v5"
        and evidence_index["currentAssessment"]["newMeasurementRun"] is False
        and evidence_index["currentAssessment"]["supersedes"]
        == {
            "assessmentId": "review-2026-08-11-v4",
            "locator": (
                "docs/evidence/poc-search-001/assessments/"
                "review-2026-08-11-v4/benchmark-result.json"
            ),
            "sha256": (
                "sha256:5fc5bd2d8340d11bbaf7b88fe3c26e1859d57ef84409f2c580158d2a3609a6b2"
            ),
            "reason": (
                "Stage 0 IP approval and owner-accepted INCONCLUSIVE closure; no measurements changed."
            ),
        },
        "Review reassessment must not claim a new benchmark run",
    )
    prior_result_path = (
        EVIDENCE
        / "assessments"
        / "review-2026-08-11-v4"
        / "benchmark-result.json"
    )
    require(
        file_digest(prior_result_path)
        == "sha256:5fc5bd2d8340d11bbaf7b88fe3c26e1859d57ef84409f2c580158d2a3609a6b2",
        "Immutable v4 assessment was altered while recording the closure",
    )
    require(
        evidence_index["ipEvaluation"]["status"]
        == "EVALUATION_APPROVED"
        and evidence_index["ipEvaluation"]["ipPrecondition"]
        == "SATISFIED_FOR_STAGE0"
        and evidence_index["ipEvaluation"]["futureMeasuredExecution"]
        == "BLOCKED_PENDING_PHYSICAL_D1_D3_AND_OWNER_EXECUTION_AUTHORIZATION",
        "Evidence index IP review status drift",
    )
    require(
        evidence_index["executionPrerequisites"]
        == {
            "licenseNoticeApproval": "EVALUATION_APPROVED_STAGE0",
            "pairedHarness": read_json(EVIDENCE / "gate-v02-harness-readiness.json")["status"],
            "physicalD1D2D3": "BLOCKED_MISSING_D1_D3",
            "laterOwnerExecutionAuthorization": "WITHHELD",
        },
        "Evidence index execution prerequisites drift",
    )
    require(
        evidence_index["stageClosure"]
        == {
            "status": "COMPLETE_INCONCLUSIVE",
            "acceptedBy": "Project owner",
            "acceptedOn": "2026-08-11",
            "newMeasurementRun": False,
            "measuredBenchmark": "DEFERRED_NOT_AUTHORIZED",
            "physicalD1D3": "DEFERRED_UNRECORDED",
            "productionAdmission": False,
            "pullRequestDisposition": "DRAFT_PENDING_FINAL_REVIEW",
        },
        "Stage 0C INCONCLUSIVE closure record drift",
    )
    result_path = ROOT / evidence_index["currentAssessment"]["locator"]
    require(result_path.is_file(), "Current versioned assessment is missing")
    require(
        file_digest(result_path) == evidence_index["currentAssessment"]["sha256"],
        "Evidence index assessment SHA mismatch",
    )
    schema = read_json(ROOT / "docs/stage0/benchmark-result.schema.json")
    result = read_json(result_path)
    SchemaValidator(schema).validate(result, schema)
    prior_result = read_json(prior_result_path)
    for measurement_field in (
        "commit",
        "device",
        "androidApi",
        "duration",
        "inputData",
        "metrics",
        "successGates",
        "failureGates",
        "errors",
        "battery",
        "temperature",
        "memory",
        "fileSizes",
    ):
        require(
            result[measurement_field] == prior_result[measurement_field],
            f"Non-measurement closure changed frozen field {measurement_field}",
        )
    prior_assessment_dir = prior_result_path.parent
    current_assessment_dir = result_path.parent
    for detail_name in (
        "query-result.json",
        "mutation-result.json",
        "rebuild-result.json",
    ):
        require(
            read_json(current_assessment_dir / detail_name)
            == read_json(prior_assessment_dir / detail_name),
            f"Non-measurement closure changed frozen detail {detail_name}",
        )
    current_environment = read_json(current_assessment_dir / "environment.json")
    prior_environment = read_json(prior_assessment_dir / "environment.json")
    current_environment.pop("evidenceRevision")
    prior_environment.pop("evidenceRevision")
    require(
        current_environment == prior_environment,
        "Non-measurement closure changed the corrected environment evidence",
    )
    require(result["pocId"] == "POC-SEARCH-001", "Wrong PoC result id")
    require(result["gateSetVersion"] == "stage0-v0.1", "Historical assessment must retain its gate version")
    require(result["result"]["status"] == "INCONCLUSIVE", "Stage 0C closure must remain INCONCLUSIVE")
    require(
        result["recommendation"]["decision"] == "BLOCKED",
        "INCONCLUSIVE Stage 0C closure cannot recommend production GO",
    )
    require(result["device"]["kind"] == "emulator", "Search result must disclose emulator environment")
    require(result["inputData"]["containsRealMeetingData"] is False, "Real meeting data is forbidden")
    gate_ids = [gate["id"] for gate in result["successGates"] + result["failureGates"]]
    require(len(gate_ids) == len(set(gate_ids)), "Duplicate result gate ids")
    required_gate_ids = {
        "GATE-SEARCH-P95",
        "GATE-SEARCH-P99",
        "GATE-SEARCH-CORRECTNESS",
        "GATE-SEARCH-ADVERSARIAL",
        "GATE-SEARCH-REBUILD",
        "GATE-SEARCH-LATENCY-FAILURE",
        "GATE-SEARCH-INJECTION-CRASH",
        "GATE-SEARCH-MAPPING-LOSS",
        "GATE-SEARCH-NONDETERMINISTIC",
        "GATE-SEARCH-UPDATE-INTEGRITY",
        "GATE-SEARCH-STORAGE-UPDATE-OVERHEAD",
    }
    require(set(gate_ids) == required_gate_ids, "Search gate inventory is incomplete or drifted")
    mandatory_gates = [
        gate
        for gate in result["successGates"] + result["failureGates"]
        if gate["mandatory"] and gate["approvalStatus"] == "Approved"
    ]
    triggered = [gate for gate in result["failureGates"] if gate["outcome"] == "triggered"]
    not_evaluated = [gate for gate in mandatory_gates if gate["outcome"] == "not_evaluated"]
    if triggered:
        require(result["result"]["status"] == "FAIL", "Triggered approved failure gate must yield FAIL")
    elif not_evaluated:
        require(
            result["result"]["status"] == "INCONCLUSIVE",
            "Unevaluated mandatory approved gate must yield INCONCLUSIVE",
        )
        require(
            result["recommendation"]["decision"] == "BLOCKED"
            and bool(result["recommendation"]["ownerAction"]),
            "Unevaluated mandatory gate cannot produce GO or omit owner action",
        )
    require(
        [gate["id"] for gate in not_evaluated] == ["GATE-SEARCH-STORAGE-UPDATE-OVERHEAD"],
        "Exactly the unresolved storage/update overhead predicate must remain unevaluated",
    )
    update_gate = next(gate for gate in result["failureGates"] if gate["id"] == "GATE-SEARCH-UPDATE-INTEGRITY")
    mutation_errors = sum(
        error["count"] for error in result["errors"] if error["code"] == "SEARCH_MUTATION_INCONSISTENT"
    )
    require(
        (update_gate["outcome"] == "triggered") == (mutation_errors > 0),
        "Mutation correctness failure is not reflected in the update failure gate",
    )
    inventory = read_json(EVIDENCE / "dependency-inventory.json")
    ip_evaluation = read_json(EVIDENCE / "ip-evaluation.json")
    expected_license_ids = {component["coordinate"] for component in inventory["components"]} | {
        artifact["artifactId"] for artifact in ip_evaluation["platformArtifacts"]
    }
    actual_license_ids = {license_value["artifactId"] for license_value in result["licenses"]}
    require(actual_license_ids == expected_license_ids, "Result license inventory is incomplete")
    require(
        all(
            license_value["licenseReviewState"] == "EVALUATION_APPROVED"
            and license_value["evaluationRightsConfirmed"] is True
            and license_value["redistributionRights"] == "unknown"
            for license_value in result["licenses"]
        ),
        "Result does not preserve the Stage 0-only evaluation approval boundary",
    )
    require(
        all(
            license_value["licenseId"] == "NOASSERTION"
            for license_value in result["licenses"]
            if license_value["artifactId"]
            in {"android-system-image-google-apis-x86_64-api36", "android-platform-sqlite"}
        ),
        "Platform evaluation approval must not invent an SPDX license identifier",
    )
    limitation_ids = {limitation["id"] for limitation in result["limitations"]}
    require(
        "LIMIT-SEARCH-IP-EVALUATION-NOT-ESTABLISHED" not in limitation_ids
        and "LIMIT-SEARCH-NO-PHYSICAL-D1-D3" in limitation_ids
        and "LIMIT-SEARCH-INCOMPLETE-STORAGE-UPDATE-GATE" in limitation_ids,
        "Current assessment carries a resolved IP blocker or hides an unresolved measurement blocker",
    )
    require(
        "physical D1 and D3" in result["recommendation"]["ownerAction"]
        and "separately authorize measured execution" in result["recommendation"]["ownerAction"]
        and "approve or reject" not in result["recommendation"]["ownerAction"]
        and "harness verification" not in result["recommendation"]["ownerAction"],
        "Owner action still requests completed IP/harness work or omits deferred execution prerequisites",
    )
    evidence_locators = {evidence["locator"] for evidence in result["evidenceFiles"]}
    require(
        {
            "docs/stage0/DEC-043-POC-SEARCH-STORAGE-UPDATE-GATES.md",
            "docs/stage0/DORA_MVP1_POC_SEARCH_GATE_SET_STAGE0_V0_2.md",
            "docs/stage0/poc-search-gate-set-stage0-v0.2.json",
            "docs/evidence/poc-search-001/license-notice-inventory.json",
            "docs/evidence/poc-search-001/device-availability-stage0-v0.2.json",
            "docs/evidence/poc-search-001/gate-v02-harness-readiness.json",
            "docs/evidence/poc-search-001/ip-stage0-evaluation-review.md",
            "docs/stage0/DORA_MVP1_STAGE0_OWNER_DECISIONS.md",
        }.issubset(evidence_locators),
        "Current assessment omits approved-gate or readiness governance evidence",
    )
    environment_locator = next(
        evidence["locator"]
        for evidence in result["evidenceFiles"]
        if evidence["id"] == "environment"
    )
    environment = read_json(ROOT / environment_locator)
    require_consistent_android_runtime(environment["android"])
    require(environment["evidenceRevision"] == 5, "Current assessment evidence revision drift")
    require(environment["correction"]["metricsChanged"] is False, "Environment correction changed metrics")
    require(result["device"]["kind"] == environment["android"]["kind"], "Result/environment kind mismatch")
    require(
        result["device"]["firmwareOrBuild"] == environment["android"]["buildFingerprint"],
        "Result/environment fingerprint mismatch",
    )
    for evidence in result["evidenceFiles"]:
        if evidence["storage"] != "repository":
            continue
        path = ROOT / evidence["locator"]
        require(path.is_file(), f"Missing result evidence {evidence['locator']}")
        require(file_digest(path) == evidence["sha256"], f"Evidence SHA mismatch: {evidence['locator']}")


def combine_checkpoint_fixture(observations: dict[str, Any]) -> dict[str, Any]:
    common = {
        "checkpointSchemaVersion": 1,
        "pocId": observations["pocId"],
        "harnessVersion": observations["harnessVersion"],
        "commit": observations["commit"],
        "generatedAt": observations["generatedAt"],
        "durationSeconds": observations["durationSeconds"] / 3,
        "manifests": observations["manifests"],
        "campaign": observations["campaign"],
        "androidEnvironment": observations["androidEnvironment"],
        "memory": observations["memory"],
        "temporaryDatabaseDeleted": True,
    }
    query = {
        **common,
        "checkpoint": "query",
        "completedPhases": ["query"],
        "primaryPreparation": observations["primaryPreparation"],
        "baselineCorrectness": observations["baselineCorrectness"],
        "latency": observations["latency"],
        "primaryIndexLogicalSha256": observations["rebuild"]["baselineIndexLogicalSha256"],
        "queryPlan": {
            "queryId": "Q-SEARCH-SOURCE",
            "details": [
                "SCAN transcript_segments_fts VIRTUAL TABLE INDEX 4:",
                "SEARCH s USING INTEGER PRIMARY KEY (rowid=?)",
                "SEARCH c USING INTEGER PRIMARY KEY (rowid=?)",
            ],
            "ftsLoopIndex": 0,
            "segmentLoopIndex": 1,
            "conversationLoopIndex": 2,
            "ftsIsDrivingTable": True,
            "canonicalLookupsUseRowId": True,
            "sourceIndexIsNotDriving": True,
            "accepted": True,
        },
    }
    rebuild = {
        **common,
        "checkpoint": "rebuild",
        "completedPhases": ["rebuild"],
        "rebuildPreparation": observations["primaryPreparation"],
        "rebuildBaselineCorrectness": observations["baselineCorrectness"],
        "rebuildBaselineIndexLogicalSha256": observations["rebuild"][
            "baselineIndexLogicalSha256"
        ],
        "rebuild": observations["rebuild"],
        "mutation": observations["mutation"],
    }
    secondary = {
        **common,
        "checkpoint": "secondary",
        "completedPhases": ["secondary"],
        "secondaryPreparation": observations["secondaryPreparation"],
        "secondaryCorrectness": observations["secondaryCorrectness"],
        "secondaryIndexLogicalSha256": observations["rebuild"][
            "baselineIndexLogicalSha256"
        ],
    }
    combined = checkpoint_combiner.combine_checkpoints(
        query,
        rebuild,
        secondary,
        observations["commit"],
    )
    require(
        combined["checkpointExecution"]["completedPhases"]
        == ["query", "rebuild", "secondary"],
        "Checkpoint completion contract drift",
    )
    require(combined["crossBuildDeterminism"]["deterministic"], "Checkpoint digest drift")
    return combined


def validate_finalizer_contract() -> None:
    digest = "sha256:" + "a" * 64
    stats = {
        "count": 1_020,
        "minMs": 1.0,
        "p50Ms": 2.0,
        "p90Ms": 3.0,
        "p95Ms": 4.0,
        "p99Ms": 5.0,
        "maxMs": 6.0,
        "meanMs": 2.5,
        "standardDeviationMs": 0.5,
    }
    preparation = {
        "emptyDatabaseCreationMs": 1.0,
        "conversationInsertMs": 2.0,
        "transcriptInsertMs": 3.0,
        "indexBuildMs": 4.0,
        "checkpointCompactMs": 5.0,
        "logicalDigestReadMs": 6.0,
        "totalPreparationMs": 21.0,
        "beforeCompact": {"databaseBytes": 100, "walBytes": 20, "shmBytes": 10, "totalBytes": 130},
        "afterCompact": {"databaseBytes": 110, "walBytes": 0, "shmBytes": 10, "totalBytes": 120},
        "afterCompactDatabaseSha256": digest,
        "conversationCount": 10_000,
        "transcriptCount": 1_000_000,
        "ftsCount": 1_000_000,
        "expectedLogicalDigest": digest,
        "databaseLogicalDigest": digest,
        "sqliteIntegrity": "ok",
        "missingCanonicalMappings": 0,
        "missingIndexRows": 0,
        "duplicateCanonicalRows": 0,
    }
    correctness = {
        "label": "fixture",
        "expectedCases": 61,
        "matchedCases": 61,
        "compilerErrors": 0,
        "countErrors": 0,
        "mappingErrors": 0,
        "duplicateResultErrors": 0,
        "adversarialFailures": 0,
        "specialCharacterFailures": 0,
        "failureExecutions": 0,
        "crashes": 0,
        "canonicalRowsBefore": 1_000_000,
        "canonicalRowsAfter": 1_000_000,
        "conversationsBefore": 10_000,
        "conversationsAfter": 10_000,
        "queryResultSha256": digest,
        "allMatched": True,
        "queries": [],
    }
    observations = {
        "schemaVersion": 1,
        "pocId": "POC-SEARCH-001",
        "harnessVersion": "0.1.0-poc-search-001",
        "commit": "a" * 40,
        "generatedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "durationSeconds": 100.0,
        "manifests": {
            "datasetId": "poc-search-001-reference-dataset-v1",
            "datasetSha256": file_digest(EVIDENCE / "dataset-manifest.json"),
            "queryId": "poc-search-001-query-campaign-v1",
            "querySha256": file_digest(EVIDENCE / "query-manifest.json"),
            "mutationId": "poc-search-001-mutations-v1",
            "mutationSha256": file_digest(EVIDENCE / "mutation-manifest.json"),
        },
        "campaign": {
            "warmupPerQuery": 5,
            "repetitionsPerQuery": 30,
            "safetyRepetitions": 3,
            "scheduleSeed": 42_420_017,
            "resultLimit": 25,
            "latencyEligibleQueryCount": 34,
        },
        "androidEnvironment": {
            "kind": "emulator",
            "manufacturer": "Google",
            "model": "sdk_gphone64_x86_64",
            "brand": "google",
            "device": "emu64xa",
            "product": "sdk_gphone64_x86_64",
            "hardware": "ranchu",
            "buildFingerprint": "google/sdk_gphone64_x86_64/test:userdebug/test-keys",
            "securityPatch": "2026-01-01",
            "androidApi": 36,
            "abi": "x86_64",
            "ramMb": 4096,
            "pageSizeBytes": 4096,
            "cpuSummary": "synthetic fixture",
            "sqliteVersion": "3.50.0",
            "roomVersion": "2.8.4",
            "systemImagePackage": "system-images;android-36;google_apis;x86_64",
            "systemImageRevision": 7,
            "systemImageArchiveSha256": "b1bb0769d0bed7698e61f203d7dc9bf6e7c37cd01a39d0d8788a11186bc78160",
            "ftsCreateSql": "CREATE VIRTUAL TABLE transcript_segments_fts USING FTS4(text)",
            "buildType": "debug-androidTest",
            "monotonicClock": "SystemClock.elapsedRealtimeNanos",
        },
        "primaryPreparation": preparation,
        "secondaryPreparation": preparation,
        "baselineCorrectness": correctness,
        "secondaryCorrectness": correctness,
        "latency": {
            "warmupOperations": 170,
            "measuredOperations": 1_020,
            "scheduleSeed": 42_420_017,
            "checksum": 1,
            "overall": stats,
            "byCategory": {"exact-common": stats},
        },
        "rebuild": {
            "baselineIndexLogicalSha256": digest,
            "baselineQueryResultSha256": digest,
            "passes": [
                {"id": "REBUILD-1", "latencyMs": 10.0, "indexLogicalSha256": digest, "queryResultSha256": digest, "queryCorrectnessPassed": True, "conversationCount": 10_000, "transcriptCount": 1_000_000, "ftsCount": 1_000_000, "mappingsHealthy": True},
                {"id": "REBUILD-2", "latencyMs": 11.0, "indexLogicalSha256": digest, "queryResultSha256": digest, "queryCorrectnessPassed": True, "conversationCount": 10_000, "transcriptCount": 1_000_000, "ftsCount": 1_000_000, "mappingsHealthy": True},
            ],
            "recovery": {},
            "finalCorrectness": correctness,
            "deterministic": True,
        },
        "mutation": {
            "manifestId": "poc-search-001-mutations-v1",
            "operations": [
                {"id": f"MUT-FIXTURE-{index}", "type": "FIXTURE", "latencyMs": float(index), "correctnessPassed": True, "observations": {}}
                for index in range(1, 6)
            ],
            "staleResultErrors": 0,
            "mappingErrors": 0,
            "crashes": 0,
            "finalConversationCount": 10_000,
            "finalTranscriptCount": 999_904,
            "finalFtsCount": 999_904,
            "allCorrect": True,
        },
        "crossBuildDeterminism": {"deterministic": True},
        "memory": {"peakPssMb": 100.0, "peakNativeHeapMb": 20.0, "peakManagedHeapMb": 30.0, "peakRssMb": 120.0, "sampleCount": 10},
        "temporaryDatabasesDeleted": True,
    }
    require(
        classify_android_runtime(observations["androidEnvironment"]) == "emulator",
        "Google sdk_gphone environment must classify as emulator",
    )
    misclassified_environment = {**observations["androidEnvironment"], "kind": "physical"}
    try:
        require_consistent_android_runtime(misclassified_environment)
    except ValueError:
        pass
    else:
        fail("Environment cross-check accepted an emulator reported as physical")
    observations = combine_checkpoint_fixture(observations)
    finalizer.validate_observations(observations, "a" * 40)
    evaluation = finalizer.evaluate(observations)
    with tempfile.TemporaryDirectory(prefix="dora-search-finalizer-") as temporary:
        output = Path(temporary)
        environment_path = output / "environment.json"
        query_path = output / "query-result.json"
        mutation_path = output / "mutation-result.json"
        rebuild_path = output / "rebuild-result.json"
        finalizer.write_json(environment_path, finalizer.environment_result(observations))
        finalizer.write_json(query_path, finalizer.query_result(observations, evaluation))
        finalizer.write_json(mutation_path, finalizer.mutation_result(observations, evaluation))
        finalizer.write_json(rebuild_path, finalizer.rebuild_result(observations, evaluation))
        result = finalizer.build_result(
            observations,
            evaluation,
            output,
            [(environment_path, "other"), (query_path, "metrics"), (mutation_path, "metrics"), (rebuild_path, "metrics")],
        )
        schema = read_json(ROOT / "docs/stage0/benchmark-result.schema.json")
        SchemaValidator(schema).validate(result, schema)
        require(result["result"]["status"] == "INCONCLUSIVE", "Passing emulator fixture must remain INCONCLUSIVE")
        require(result["recommendation"]["decision"] == "BLOCKED", "Incomplete gate fixture cannot recommend GO")
        require(
            any(
                gate["id"] == "GATE-SEARCH-STORAGE-UPDATE-OVERHEAD"
                and gate["outcome"] == "not_evaluated"
                for gate in result["failureGates"]
            ),
            "Finalizer omitted the unresolved mandatory storage/update overhead gate",
        )
        require(len(result["licenses"]) == 68, "Finalizer did not emit the exact dependency/platform inventory")
        require(
            all(
                value["licenseReviewState"] == "EVALUATION_APPROVED"
                and value["evaluationRightsConfirmed"] is True
                and value["redistributionRights"] == "unknown"
                for value in result["licenses"]
            ),
            "Finalizer lost the Stage 0-only IP approval boundary",
        )
        require(
            all(
                value["licenseId"] == "NOASSERTION"
                for value in result["licenses"]
                if value["artifactId"]
                in {"android-system-image-google-apis-x86_64-api36", "android-platform-sqlite"}
            ),
            "Finalizer invented a platform SPDX license identifier",
        )


def validate_no_generated_database() -> None:
    database_files = [
        path
        for path in ROOT.rglob("*")
        if path.is_file() and path.suffix.lower() in (".db", ".sqlite", ".sqlite3")
    ]
    require(not database_files, f"Generated database must not be retained: {database_files[:3]}")


def main() -> int:
    checks = (
        validate_manifests,
        validate_gate_v02_draft,
        validate_module_wiring,
        validate_approved_contract_still_blocks_measured_run,
        validate_dependency_and_ip_inventory,
        validate_gate_v02_readiness_evidence,
        validate_durable_evidence_ledger,
        host_sqlite_smoke,
        validate_gate_v02_combiner,
        validate_finalizer_contract,
        validate_result_if_present,
        validate_no_generated_database,
    )
    for check in checks:
        check()
        print(f"PASS {check.__name__}")
    print("POC-SEARCH-001 validation passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, sqlite3.Error, json.JSONDecodeError) as error:
        print(f"FAIL {error}", file=sys.stderr)
        raise SystemExit(1)
