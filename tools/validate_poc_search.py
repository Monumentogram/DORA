#!/usr/bin/env python3
"""Static and host-SQLite smoke validation for POC-SEARCH-001."""

from __future__ import annotations

import hashlib
import json
import sqlite3
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
EVIDENCE = ROOT / "docs" / "evidence" / "poc-search-001"
sys.path.insert(0, str(ROOT / "tools"))

import poc_search_contract as contract  # noqa: E402
import combine_poc_search_checkpoints as checkpoint_combiner  # noqa: E402
import finalize_poc_search_result as finalizer  # noqa: E402
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
    return "sha256:" + hashlib.sha256(path.read_bytes()).hexdigest()


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
        and 'run-as "${POC_SEARCH_TEST_PACKAGE}"' in workflow,
        "Sanitized checkpoints must survive instrumentation for run-as extraction",
    )
    require(
        (ROOT / "tools/combine_poc_search_checkpoints.py").is_file(),
        "Missing checkpoint combiner",
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


def validate_result_if_present() -> None:
    result_path = EVIDENCE / "benchmark-result.json"
    if not result_path.is_file():
        return
    schema = read_json(ROOT / "docs/stage0/benchmark-result.schema.json")
    result = read_json(result_path)
    SchemaValidator(schema).validate(result, schema)
    require(result["pocId"] == "POC-SEARCH-001", "Wrong PoC result id")
    require(result["result"]["status"] in ("INCONCLUSIVE", "FAIL"), "Emulator evidence cannot be PASS")
    require(result["device"]["kind"] == "emulator", "Search result must disclose emulator environment")
    require(result["inputData"]["containsRealMeetingData"] is False, "Real meeting data is forbidden")
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
            "device": "emu64xa",
            "product": "sdk_gphone64_x86_64",
            "buildFingerprint": "google/sdk_gphone64_x86_64/test:userdebug/test-keys",
            "securityPatch": "2026-01-01",
            "androidApi": 36,
            "abi": "x86_64",
            "ramMb": 4096,
            "pageSizeBytes": 4096,
            "cpuSummary": "synthetic fixture",
            "sqliteVersion": "3.50.0",
            "roomVersion": "2.8.4",
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
        validate_module_wiring,
        host_sqlite_smoke,
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
