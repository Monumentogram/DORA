#!/usr/bin/env python3
"""Generate the frozen synthetic contracts for POC-SEARCH-001.

The Python oracle is deliberately independent from Room and SQLite FTS. It derives
expected counts and canonical mappings directly from the deterministic generation
contract. The Android harness must match these pre-run expectations.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import math
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable


ROOT = Path(__file__).resolve().parents[1]
EVIDENCE_DIR = ROOT / "docs" / "evidence" / "poc-search-001"

DATASET_VERSION = "poc-search-synthetic-v1"
GENERATOR_VERSION = "search-generator-1.0.0"
ORACLE_VERSION = "search-oracle-1.0.0"
SEED = 2_026_081_001
CONVERSATION_COUNT = 10_000
SEGMENTS_PER_CONVERSATION = 100
TRANSCRIPT_ROW_COUNT = CONVERSATION_COUNT * SEGMENTS_PER_CONVERSATION
BASE_STARTED_AT_MS = 1_735_689_600_000
CONVERSATION_INTERVAL_MS = 3_600_000
SOURCE_TYPES = ("IN_PERSON", "VIDEO", "VOICE_NOTE", "WORKSHOP")
LANGUAGES = ("ru", "en", "mixed-ru-en")
TECHNICAL_TERMS = ("sqlite", "room", "fts4", "kotlin", "unicode", "индекс")
RESULT_LIMIT = 25
EXPECTED_MAPPING_PREFIX = 10

MAX_QUERY_LENGTH = 512
MAX_QUERY_TOKENS = 12
MAX_QUERY_TOKEN_LENGTH = 64
MIN_PREFIX_LENGTH = 2


def canonical_json(value: Any) -> bytes:
    return json.dumps(
        value,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    ).encode("utf-8")


def digest_bytes(value: bytes) -> str:
    return f"sha256:{hashlib.sha256(value).hexdigest()}"


def digest_text(value: str) -> str:
    return digest_bytes(value.encode("utf-8"))


def conversation_started_at_ms(conversation_id: int) -> int:
    return BASE_STARTED_AT_MS + (conversation_id - 1) * CONVERSATION_INTERVAL_MS


def conversation_source(conversation_id: int) -> str:
    return SOURCE_TYPES[(conversation_id + SEED) % len(SOURCE_TYPES)]


def conversation_title(conversation_id: int) -> str:
    return (
        f"Синтетический разговор {conversation_id:05d} "
        f"Synthetic workspace {(conversation_id * 13 + SEED) % 41:02d}"
    )


def participant_label(conversation_id: int) -> str:
    ru_id = (conversation_id * 31 + SEED) % 97
    en_id = (conversation_id * 17 + SEED) % 89
    return f"СинтУчастник{ru_id:02d}|SynthParticipant{en_id:02d}"


def segment_id(conversation_id: int, sequence: int) -> int:
    return (conversation_id - 1) * SEGMENTS_PER_CONVERSATION + sequence + 1


def segment_coordinates(row_id: int) -> tuple[int, int]:
    return ((row_id - 1) // SEGMENTS_PER_CONVERSATION + 1, (row_id - 1) % 100)


def language_for(row_id: int) -> str:
    return LANGUAGES[(row_id + SEED) % len(LANGUAGES)]


def generated_segment(row_id: int) -> dict[str, Any]:
    conversation_id, sequence = segment_coordinates(row_id)
    language = language_for(row_id)
    ru_participant = (conversation_id * 31 + SEED) % 97
    en_participant = (conversation_id * 17 + SEED) % 89
    company = (conversation_id * 13 + SEED) % 41
    task = (sequence * 7 + conversation_id + SEED) % 23
    number = (row_id * 37 + SEED) % 1000
    day = (conversation_id + SEED) % 28 + 1
    technical_term = TECHNICAL_TERMS[(row_id + SEED) % len(TECHNICAL_TERMS)]

    if language == "ru":
        parts = [
            "команда",
            "обсуждает",
            "проект",
            "задача",
            "срок",
            "отчёт",
            "синтетический",
            f"синтимя{ru_participant:02d}",
            f"компаниясигма{company:02d}",
            f"задачаметка{task:02d}",
            technical_term,
            f"номер{number:03d}",
            f"дата2026-08-{day:02d}",
        ]
    elif language == "en":
        parts = [
            "team",
            "discusses",
            "project",
            "task",
            "deadline",
            "report",
            "synthetic",
            f"synthname{en_participant:02d}",
            f"orionlabs{company:02d}",
            f"tasklabel{task:02d}",
            technical_term,
            f"number{number:03d}",
            f"date2026-08-{day:02d}",
        ]
    else:
        parts = [
            "команда",
            "project",
            "обсуждает",
            "task",
            "срок",
            "deadline",
            "synthetic",
            f"синтимя{ru_participant:02d}",
            f"synthname{en_participant:02d}",
            f"компаниясигма{company:02d}",
            f"orionlabs{company:02d}",
            f"задачаметка{task:02d}",
            f"tasklabel{task:02d}",
            technical_term,
            f"номер{number:03d}",
            f"number{number:03d}",
        ]

    filler_count = ((row_id + SEED) // 7) % 6
    parts.extend(("контекст", "detail", "проверка", "review", "данные", "data")[:filler_count])

    if row_id % 10 == 0:
        parts.extend(("проект", "проект", "project", "project"))
    if row_id % 65_537 == 0:
        parts.append("hypergraphdelta")
    if row_id % 99_991 == 0:
        parts.append("редкословоаврора")
    if row_id == 424_242:
        parts.append("uniquemarkerquasar")
    if row_id % 10_007 == 0:
        parts.extend(("красный", "спутник"))
    if row_id % 10_009 == 0:
        parts.extend(("silent", "harbor"))
    if row_id % 10_037 == 0:
        parts.extend(("проект", "nebula"))
    if row_id % 997 == 0:
        parts.append("квантовыйконтур")
    if row_id % 991 == 0:
        parts.append("hyperprotocol")
    if row_id % 5_003 == 0:
        parts.extend(
            (
                "alpha-beta",
                "colon:value",
                'quote"token',
                "apostrophe'token",
            )
        )
    if row_id % 12_347 == 0:
        parts.extend(("ёжик", "café", "東京", "rocketemoji🚀"))
    if row_id == 123_456:
        parts.append("mutationbeforemarker")
    if row_id == 234_567:
        parts.append("segmentdeletemarker")
    if row_id == segment_id(7_777, 0):
        parts.append("conversationdeletemarker")

    start_ms = sequence * 45_000
    duration_ms = 4_000 + ((row_id + SEED) % 12) * 750
    return {
        "segmentId": row_id,
        "conversationId": conversation_id,
        "sequence": sequence,
        "startMs": start_ms,
        "endMs": start_ms + duration_ms,
        "language": language,
        "text": " ".join(parts),
    }


def _is_token_character(character: str) -> bool:
    return character.isalnum() or unicodedata.category(character).startswith("M")


def tokenize(value: str) -> list[str]:
    tokens: list[str] = []
    current: list[str] = []
    for character in value.lower():
        if _is_token_character(character):
            current.append(character)
        elif current:
            tokens.append("".join(current))
            current.clear()
    if current:
        tokens.append("".join(current))
    return tokens


@dataclass(frozen=True)
class CompiledQuery:
    status: str
    tokens: tuple[str, ...]
    compiled_match: str | None
    rejection_code: str | None


def compile_query(raw_query: str, mode: str, has_filters: bool) -> CompiledQuery:
    if len(raw_query) > MAX_QUERY_LENGTH:
        return CompiledQuery("REJECTED", (), None, "QUERY_TOO_LONG")

    tokens = tuple(tokenize(raw_query.strip()))
    if not tokens:
        if has_filters:
            return CompiledQuery("FILTER_ONLY", (), None, None)
        return CompiledQuery("EMPTY", (), None, None)
    if len(tokens) > MAX_QUERY_TOKENS:
        return CompiledQuery("REJECTED", (), None, "TOO_MANY_TOKENS")
    if any(len(token) > MAX_QUERY_TOKEN_LENGTH for token in tokens):
        return CompiledQuery("REJECTED", (), None, "TOKEN_TOO_LONG")
    if mode == "PREFIX" and any(len(token) < MIN_PREFIX_LENGTH for token in tokens):
        return CompiledQuery("REJECTED", (), None, "PREFIX_TOO_SHORT")

    if mode == "PHRASE":
        compiled = '"' + " ".join(tokens) + '"'
    elif mode == "PREFIX":
        compiled = " AND ".join(f'"{token}"*' for token in tokens)
    else:
        compiled = " AND ".join(f'"{token}"' for token in tokens)
    return CompiledQuery("READY", tokens, compiled, None)


def query_matches(text_tokens: list[str], compiled: CompiledQuery, mode: str) -> bool:
    if compiled.status == "FILTER_ONLY":
        return True
    if compiled.status != "READY":
        return False
    if mode == "PHRASE":
        length = len(compiled.tokens)
        return any(
            tuple(text_tokens[index : index + length]) == compiled.tokens
            for index in range(len(text_tokens) - length + 1)
        )
    if mode == "PREFIX":
        return all(
            any(text_token.startswith(query_token) for text_token in text_tokens)
            for query_token in compiled.tokens
        )
    text_token_set = set(text_tokens)
    return all(query_token in text_token_set for query_token in compiled.tokens)


def has_filters(filters: dict[str, Any]) -> bool:
    return any(value is not None for value in filters.values())


def filter_matches(conversation_id: int, filters: dict[str, Any]) -> bool:
    if filters["conversationId"] is not None and conversation_id != filters["conversationId"]:
        return False
    if filters["sourceType"] is not None and conversation_source(conversation_id) != filters["sourceType"]:
        return False
    started_at = conversation_started_at_ms(conversation_id)
    if filters["startedAtFromMs"] is not None and started_at < filters["startedAtFromMs"]:
        return False
    if filters["startedAtToMs"] is not None and started_at >= filters["startedAtToMs"]:
        return False
    return True


def default_filters(**overrides: Any) -> dict[str, Any]:
    result = {
        "conversationId": None,
        "sourceType": None,
        "startedAtFromMs": None,
        "startedAtToMs": None,
    }
    result.update(overrides)
    return result


def query_case(
    query_id: str,
    category: str,
    raw_query: str,
    mode: str = "EXACT",
    filters: dict[str, Any] | None = None,
    latency_eligible: bool = True,
) -> dict[str, Any]:
    return {
        "id": query_id,
        "category": category,
        "rawQuery": raw_query,
        "mode": mode,
        "filters": filters or default_filters(),
        "latencyEligible": latency_eligible,
    }


def base_query_cases() -> list[dict[str, Any]]:
    source_42 = conversation_source(42)
    alternative_source = next(source for source in SOURCE_TYPES if source != source_42)
    date_from = conversation_started_at_ms(1_000)
    date_to = conversation_started_at_ms(1_100)
    cases = [
        query_case("Q-EXACT-RU-COMMON", "exact-common", "проект"),
        query_case("Q-EXACT-EN-COMMON", "exact-common", "project"),
        query_case("Q-EXACT-RU-TASK", "exact-common", "задача"),
        query_case("Q-EXACT-EN-DEADLINE", "exact-common", "deadline"),
        query_case("Q-EXACT-ROOM", "exact-common", "room"),
        query_case("Q-EXACT-NUMBER", "exact-common", "number042"),
        query_case("Q-RARE-HYPERGRAPH", "exact-rare", "hypergraphdelta"),
        query_case("Q-RARE-RU", "exact-rare", "редкословоаврора"),
        query_case("Q-RARE-UNIQUE", "exact-rare", "uniquemarkerquasar"),
        query_case("Q-RARE-RU-PARTICIPANT", "exact-rare", "синтимя17"),
        query_case("Q-RARE-EN-PARTICIPANT", "exact-rare", "synthname23"),
        query_case("Q-PHRASE-RU-MARKER", "phrase", "красный спутник", "PHRASE"),
        query_case("Q-PHRASE-EN-MARKER", "phrase", "silent harbor", "PHRASE"),
        query_case("Q-PHRASE-MIXED-MARKER", "phrase", "проект nebula", "PHRASE"),
        query_case("Q-PHRASE-RU-COMMON", "phrase", "команда обсуждает", "PHRASE"),
        query_case("Q-PHRASE-EN-COMMON", "phrase", "team discusses", "PHRASE"),
        query_case("Q-PHRASE-MIXED-COMMON", "phrase", "команда project", "PHRASE"),
        query_case("Q-PREFIX-RU-QUANT", "prefix", "квант", "PREFIX"),
        query_case("Q-PREFIX-EN-HYPER", "prefix", "hyperpro", "PREFIX"),
        query_case("Q-PREFIX-RU-RARE", "prefix", "редкослово", "PREFIX"),
        query_case("Q-PREFIX-EN-SYNTH", "prefix", "synth", "PREFIX"),
        query_case("Q-PREFIX-RU-PROJECT", "prefix", "проек", "PREFIX"),
        query_case(
            "Q-FILTER-CONVERSATION",
            "filters",
            "",
            filters=default_filters(conversationId=42),
        ),
        query_case(
            "Q-FILTER-SOURCE",
            "filters",
            "",
            filters=default_filters(sourceType="VIDEO"),
        ),
        query_case(
            "Q-FILTER-DATE",
            "filters",
            "",
            filters=default_filters(startedAtFromMs=date_from, startedAtToMs=date_to),
        ),
        query_case(
            "Q-SEARCH-CONVERSATION",
            "filters",
            "project",
            filters=default_filters(conversationId=42),
        ),
        query_case(
            "Q-SEARCH-SOURCE",
            "filters",
            "project",
            filters=default_filters(sourceType="WORKSHOP"),
        ),
        query_case(
            "Q-SEARCH-DATE",
            "filters",
            "проект",
            filters=default_filters(startedAtFromMs=date_from, startedAtToMs=date_to),
        ),
        query_case(
            "Q-SEARCH-PHRASE-SOURCE",
            "filters",
            "silent harbor",
            "PHRASE",
            default_filters(sourceType="VIDEO"),
        ),
        query_case(
            "Q-SEARCH-MULTI-FILTER",
            "filters",
            "проект",
            filters=default_filters(
                conversationId=42,
                sourceType=source_42,
                startedAtFromMs=conversation_started_at_ms(42),
                startedAtToMs=conversation_started_at_ms(43),
            ),
        ),
        query_case(
            "Q-SEARCH-MULTI-FILTER-NO-MATCH",
            "filters",
            "проект",
            filters=default_filters(conversationId=42, sourceType=alternative_source),
        ),
        query_case("Q-NEGATIVE-WORD", "negative", "wordthatdoesnotexist"),
        query_case("Q-NEGATIVE-PHRASE", "negative", "missing synthetic phrase", "PHRASE"),
        query_case("Q-NEGATIVE-NUMBER", "negative", "number9999"),
        query_case("Q-EMPTY", "negative", "", latency_eligible=False),
        query_case("Q-WHITESPACE", "negative", "   \t\n  ", latency_eligible=False),
        query_case("Q-SPECIAL-QUOTES", "special-characters", '"project"', latency_eligible=False),
        query_case("Q-SPECIAL-APOSTROPHE", "special-characters", "apostrophe'token", latency_eligible=False),
        query_case("Q-SPECIAL-PARENS", "special-characters", "(project)", latency_eligible=False),
        query_case("Q-SPECIAL-COLON", "special-characters", "colon:value", latency_eligible=False),
        query_case("Q-SPECIAL-HYPHEN", "special-characters", "alpha-beta", latency_eligible=False),
        query_case("Q-SPECIAL-STAR", "special-characters", "project*", latency_eligible=False),
        query_case("Q-SPECIAL-QUESTION", "special-characters", "project?", latency_eligible=False),
        query_case("Q-SPECIAL-UNICODE", "special-characters", "ёжик café 東京", latency_eligible=False),
        query_case("Q-SPECIAL-EMOJI", "special-characters", "🚀", latency_eligible=False),
        query_case("Q-SPECIAL-LONG", "special-characters", "x" * 600, latency_eligible=False),
        query_case("Q-ADVERSARIAL-OPERATORS", "adversarial", "OR AND NOT NEAR", latency_eligible=False),
        query_case("Q-ADVERSARIAL-OR", "adversarial", "project OR synthetic", latency_eligible=False),
        query_case("Q-ADVERSARIAL-AND", "adversarial", "project AND synthetic", latency_eligible=False),
        query_case("Q-ADVERSARIAL-NOT", "adversarial", "project NOT synthetic", latency_eligible=False),
        query_case("Q-ADVERSARIAL-NEAR", "adversarial", "project NEAR synthetic", latency_eligible=False),
        query_case("Q-ADVERSARIAL-MALFORMED", "adversarial", 'project" OR "synthetic', latency_eligible=False),
        query_case("Q-ADVERSARIAL-DROP", "adversarial", "'); DROP TABLE transcript_segments; --", latency_eligible=False),
        query_case("Q-ADVERSARIAL-UNION", "adversarial", "project UNION SELECT text FROM transcript_segments", latency_eligible=False),
        query_case("Q-ADVERSARIAL-WILDCARD", "adversarial", "project**************** OR *", latency_eligible=False),
        query_case("Q-ADVERSARIAL-COMMENT", "adversarial", "project -- synthetic", latency_eligible=False),
        query_case("Q-ADVERSARIAL-SEMICOLON", "adversarial", "project; DELETE FROM conversations", latency_eligible=False),
        query_case("Q-ADVERSARIAL-MANY-TOKENS", "adversarial", " ".join(f"token{i}" for i in range(13)), latency_eligible=False),
        query_case("Q-ADVERSARIAL-LONG-TOKEN", "adversarial", "z" * 65, latency_eligible=False),
        query_case("Q-ADVERSARIAL-PREFIX-ONE", "adversarial", "x", "PREFIX", latency_eligible=False),
        query_case("Q-ADVERSARIAL-CONTROL", "adversarial", "project\u0000synthetic", latency_eligible=False),
    ]
    identifiers = [case["id"] for case in cases]
    if len(identifiers) != len(set(identifiers)):
        raise ValueError("Duplicate query identifier")
    return cases


def logical_digest_and_query_oracle(
    query_cases: list[dict[str, Any]],
) -> tuple[str, dict[str, dict[str, Any]]]:
    digest = hashlib.sha256()
    for conversation_id in range(1, CONVERSATION_COUNT + 1):
        digest.update(
            (
                "C|"
                f"{conversation_id}|{conversation_title(conversation_id)}|"
                f"{conversation_started_at_ms(conversation_id)}|"
                f"{conversation_source(conversation_id)}|"
                f"{participant_label(conversation_id)}\n"
            ).encode("utf-8")
        )

    compiled_by_id: dict[str, CompiledQuery] = {}
    oracle: dict[str, dict[str, Any]] = {}
    for case in query_cases:
        compiled = compile_query(case["rawQuery"], case["mode"], has_filters(case["filters"]))
        compiled_by_id[case["id"]] = compiled
        oracle[case["id"]] = {"expectedCount": 0, "expectedFirstMappings": []}

    for row_id in range(1, TRANSCRIPT_ROW_COUNT + 1):
        segment = generated_segment(row_id)
        digest.update(
            (
                "S|"
                f"{segment['segmentId']}|{segment['conversationId']}|{segment['sequence']}|"
                f"{segment['startMs']}|{segment['endMs']}|{segment['language']}|"
                f"{segment['text']}\n"
            ).encode("utf-8")
        )
        text_tokens = tokenize(segment["text"])
        for case in query_cases:
            compiled = compiled_by_id[case["id"]]
            if compiled.status not in {"READY", "FILTER_ONLY"}:
                continue
            if not filter_matches(segment["conversationId"], case["filters"]):
                continue
            if not query_matches(text_tokens, compiled, case["mode"]):
                continue
            expected = oracle[case["id"]]
            expected["expectedCount"] += 1
            if len(expected["expectedFirstMappings"]) < EXPECTED_MAPPING_PREFIX:
                expected["expectedFirstMappings"].append(
                    {
                        "segmentId": segment["segmentId"],
                        "conversationId": segment["conversationId"],
                        "startMs": segment["startMs"],
                        "endMs": segment["endMs"],
                        "textSha256": digest_text(segment["text"]),
                    }
                )

    return f"sha256:{digest.hexdigest()}", oracle


def build_dataset_manifest(logical_digest: str) -> dict[str, Any]:
    contract = {
        "datasetVersion": DATASET_VERSION,
        "generatorVersion": GENERATOR_VERSION,
        "seed": SEED,
        "conversationCount": CONVERSATION_COUNT,
        "transcriptRowCount": TRANSCRIPT_ROW_COUNT,
        "segmentsPerConversation": SEGMENTS_PER_CONVERSATION,
        "baseStartedAtEpochMs": BASE_STARTED_AT_MS,
        "conversationIntervalMs": CONVERSATION_INTERVAL_MS,
        "sourceTypes": list(SOURCE_TYPES),
        "languages": list(LANGUAGES),
        "idContract": "conversationId=1..10000; segmentId=(conversationId-1)*100+sequence+1",
        "textContract": "repository-owned formulas in tools/poc_search_contract.py and SyntheticDatasetGenerator.kt",
        "batchContract": {
            "conversationBatchSize": 1_000,
            "transcriptBatchSize": 5_000,
            "allRowsInMemoryForbidden": True,
        },
    }
    return {
        "schemaVersion": 1,
        "manifestId": "poc-search-001-reference-dataset-v1",
        "contract": contract,
        "generationContractSha256": digest_bytes(canonical_json(contract)),
        "expected": {
            "conversationCount": CONVERSATION_COUNT,
            "transcriptRowCount": TRANSCRIPT_ROW_COUNT,
            "logicalDatasetSha256": logical_digest,
        },
        "contentCoverage": [
            "ru",
            "en",
            "mixed-ru-en",
            "numbers",
            "dates",
            "synthetic-company-labels",
            "synthetic-participant-labels",
            "task-labels",
            "technical-terms",
            "variable-length",
            "repeated-words",
            "rare-words",
            "punctuation",
            "unicode",
            "cyrillic",
            "latin",
            "emoji",
        ],
        "privacy": {
            "classification": "generated_text",
            "containsRealMeetingData": False,
            "containsPersonalData": False,
            "containsCopyrightCorpus": False,
            "networkRequired": False,
        },
        "generatedDatabasePolicy": {
            "trackedInGit": False,
            "retainedAsActionsArtifact": False,
            "deletedAfterBenchmark": True,
        },
    }


def build_query_manifest(
    cases: list[dict[str, Any]], oracle: dict[str, dict[str, Any]]
) -> dict[str, Any]:
    completed_cases: list[dict[str, Any]] = []
    for case in cases:
        compiled = compile_query(case["rawQuery"], case["mode"], has_filters(case["filters"]))
        completed = dict(case)
        completed["normalization"] = {
            "status": compiled.status,
            "compiledMatch": compiled.compiled_match,
            "rejectionCode": compiled.rejection_code,
            "tokens": list(compiled.tokens),
        }
        completed["oracle"] = oracle[case["id"]]
        completed_cases.append(completed)

    contract = {
        "oracleVersion": ORACLE_VERSION,
        "datasetManifestId": "poc-search-001-reference-dataset-v1",
        "normalization": {
            "maxRawLength": MAX_QUERY_LENGTH,
            "maxTokens": MAX_QUERY_TOKENS,
            "maxTokenLength": MAX_QUERY_TOKEN_LENGTH,
            "minimumPrefixLength": MIN_PREFIX_LENGTH,
            "operatorPolicy": "Unicode letter/digit tokens only; every token is quoted; operators are literals",
            "bindingPolicy": "MATCH expression and every filter value are bound parameters",
        },
        "campaign": {
            "resultLimit": RESULT_LIMIT,
            "expectedMappingPrefix": EXPECTED_MAPPING_PREFIX,
            "warmupPerLatencyEligibleQuery": 5,
            "measuredRepetitionsPerLatencyEligibleQuery": 30,
            "safetyRepetitionsPerNonLatencyQuery": 3,
            "schedule": "deterministic seeded rotation over manifest order",
            "scheduleSeed": 42_420_017,
            "clock": "SystemClock.elapsedRealtimeNanos monotonic",
            "percentileDefinition": "nearest-rank: sorted[ceil(p*N)-1]",
            "standardDeviation": "population",
            "gateCampaign": "all and only latencyEligible=true query cases",
        },
        "queries": completed_cases,
    }
    return {
        "schemaVersion": 1,
        "manifestId": "poc-search-001-query-campaign-v1",
        "contract": contract,
        "contractSha256": digest_bytes(canonical_json(contract)),
        "integrityRule": "Query mix, eligibility, ordering, repetitions, percentiles and gates are frozen before the first full run.",
    }


def build_mutation_manifest() -> dict[str, Any]:
    conversation_42_source = conversation_source(42)
    updated_source = next(source for source in SOURCE_TYPES if source != conversation_42_source)
    contract = {
        "datasetManifestId": "poc-search-001-reference-dataset-v1",
        "operations": [
            {
                "id": "MUT-ADD-CONVERSATION",
                "type": "ADD_CONVERSATION_WITH_SEGMENTS",
                "conversationId": 10_001,
                "segmentIds": [1_000_001, 1_000_002, 1_000_003, 1_000_004, 1_000_005],
                "marker": "newconversationmarker",
                "expectedAfter": {"conversationCount": 10_001, "transcriptRowCount": 1_000_005, "markerCount": 5},
            },
            {
                "id": "MUT-UPDATE-SEGMENT",
                "type": "UPDATE_SEGMENT_TEXT",
                "segmentId": 123_456,
                "oldMarker": "mutationbeforemarker",
                "newMarker": "mutationaftermarker",
                "expectedAfter": {"oldMarkerCount": 0, "newMarkerCount": 1},
            },
            {
                "id": "MUT-UPDATE-METADATA",
                "type": "UPDATE_CONVERSATION_SOURCE",
                "conversationId": 42,
                "oldSourceType": conversation_42_source,
                "newSourceType": updated_source,
                "expectedAfter": {"newSourceRows": 100, "oldSourceRows": 0},
            },
            {
                "id": "MUT-DELETE-SEGMENT",
                "type": "DELETE_SEGMENT",
                "segmentId": 234_567,
                "marker": "segmentdeletemarker",
                "expectedAfter": {"transcriptRowCount": 1_000_004, "markerCount": 0},
            },
            {
                "id": "MUT-DELETE-CONVERSATION",
                "type": "DELETE_CONVERSATION_CASCADE",
                "conversationId": 7_777,
                "marker": "conversationdeletemarker",
                "deletedSegmentCount": 100,
                "expectedAfter": {"conversationCount": 10_000, "transcriptRowCount": 999_904, "markerCount": 0},
            },
        ],
        "latencyPolicy": "Record each deterministic operation as an observation; no numeric mutation threshold is approved.",
        "correctnessPolicy": "After every operation verify canonical row, FTS result, filters, mapping and stale-result invariants.",
    }
    return {
        "schemaVersion": 1,
        "manifestId": "poc-search-001-mutations-v1",
        "contract": contract,
        "contractSha256": digest_bytes(canonical_json(contract)),
    }


def write_json(path: Path, value: Any) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )


def generate_contracts() -> tuple[dict[str, Any], dict[str, Any], dict[str, Any]]:
    cases = base_query_cases()
    logical_digest, oracle = logical_digest_and_query_oracle(cases)
    return (
        build_dataset_manifest(logical_digest),
        build_query_manifest(cases, oracle),
        build_mutation_manifest(),
    )


def print_summary(dataset: dict[str, Any], queries: dict[str, Any], mutations: dict[str, Any]) -> None:
    query_cases = queries["contract"]["queries"]
    eligible = sum(1 for query in query_cases if query["latencyEligible"])
    measured = eligible * queries["contract"]["campaign"]["measuredRepetitionsPerLatencyEligibleQuery"]
    print(f"dataset logical digest: {dataset['expected']['logicalDatasetSha256']}")
    print(f"query cases: {len(query_cases)}; latency eligible: {eligible}; measured operations: {measured}")
    print(f"mutation operations: {len(mutations['contract']['operations'])}")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true", help="Write frozen manifests under docs/evidence/poc-search-001")
    parser.add_argument("--summary", action="store_true", help="Print the generated contract summary")
    arguments = parser.parse_args()
    if not arguments.write and not arguments.summary:
        parser.error("select --write and/or --summary")

    dataset, queries, mutations = generate_contracts()
    if arguments.write:
        write_json(EVIDENCE_DIR / "dataset-manifest.json", dataset)
        write_json(EVIDENCE_DIR / "query-manifest.json", queries)
        write_json(EVIDENCE_DIR / "mutation-manifest.json", mutations)
    if arguments.summary:
        print_summary(dataset, queries, mutations)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
