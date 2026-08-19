#!/usr/bin/env python3
"""Closed Java 17 runner for the Decision I4 generated provenance/source fixture."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import struct
import subprocess
import sys
import tempfile
from typing import Any, Callable, Mapping, Sequence


BASELINE_COMMIT = "9256db3d95fa20bc0d98aa35b48734ffeeb2623c"
BASELINE_TREE = "6e17701efbdc80e14bd0778535f187c1424ed047"
SCOPE_COMMIT = "e9a67daf625ba81925fba8dd876c0998647da3e5"
PROFILE_VERSION = "decision-i4-provenance-source-validator-stage0-v0.1"
ENVELOPE_SCHEMA_VERSION = "DORA_DECISION_I4_PROVENANCE_ENVELOPE_STAGE0_V0_1"
SOURCE_SET_VERSION = "DORA_DECISION_I4_GENERATED_SOURCE_SET_STAGE0_V0_1"
RANGE_UNIT = "UTF8_BYTE_OFFSETS_HALF_OPEN_STAGE0_V0_1"
CLAIM_CEILING = "DECISION_I4_SYNTHETIC_PROVENANCE_SOURCE_MECHANICS_EXERCISED"
EXPECTED_CANONICAL_SHA256 = (
    "eae9b3bd500e99660dfd07092f339a2f49ddd2fef5e2befe5bd24e489b6350ba"
)
TEST_CLASS = (
    "com.monumentogram.dora.stage0.decision.i4."
    "DecisionProvenanceSourceValidatorTest"
)
TEST_REPEAT_COUNT = 3
EXPECTED_TEST_STDOUT = (
    "POC_DECISION_I4_PROVENANCE_SOURCE_VALIDATOR_TESTS_OK "
    "cases=14 accepted=3 rejected=11 "
    "canonicalSha256=eae9b3bd500e99660dfd07092f339a2f49ddd2fef5e2befe5bd24e489b6350ba "
    "claim=DECISION_I4_SYNTHETIC_PROVENANCE_SOURCE_MECHANICS_EXERCISED\n"
).encode("utf-8")
EXPECTED_TEST_STDOUT_SHA256 = (
    "ef8a1ed3f3855a79ad320d9ba522e508db5e8e1e5c8ced3424faf15ee6e8b318"
)
EXPECTED_RUNNER_STDOUT = (
    "DECISION_I4_SYNTHETIC_PROVENANCE_SOURCE_MECHANICS_EXERCISED "
    "cases=14 accepted=3 rejected=11 repeats=3 jdeps=java.base "
    "class_major=61 temp_cleanup=true\n"
)
EXPECTED_RUNNER_STDOUT_SHA256 = (
    "fd2ca8d7341670e281fb5df38fd945b9980bfd9e4e55a5f78294ca4136e11e9f"
)

WORKFLOW_PATH = ".github/workflows/stage0-decision-i4-provenance-source-validator.yml"
EVIDENCE_PATH = (
    "docs/evidence/poc-decision-001/"
    "decision-i4-provenance-source-validator-local-evidence-stage0-v0.1.json"
)
SCOPE_PATH = (
    "docs/stage0/"
    "DORA_MVP1_DECISION_I4_PROVENANCE_SOURCE_VALIDATOR_SCOPE_STAGE0_V0_1.md"
)
MAIN_SOURCE_PATH = (
    "tools/decision_i4_provenance_source_validator/src/main/java/com/monumentogram/"
    "dora/stage0/decision/i4/DecisionProvenanceSourceValidator.java"
)
TEST_SOURCE_PATH = (
    "tools/decision_i4_provenance_source_validator/src/test/java/com/monumentogram/"
    "dora/stage0/decision/i4/DecisionProvenanceSourceValidatorTest.java"
)
RUNNER_PATH = "tools/run_stage0_decision_i4_provenance_source_validator.py"

EXPECTED_TRACKED_FILES = (
    WORKFLOW_PATH,
    EVIDENCE_PATH,
    SCOPE_PATH,
    MAIN_SOURCE_PATH,
    TEST_SOURCE_PATH,
    RUNNER_PATH,
)

PIN_HASHES = {
    SCOPE_PATH: "7a594ad9e0f3e9b8cb7b5438ab28ec2be156972c3f47e4250feeaf51bc1f466a",
    EVIDENCE_PATH: "4fc4c5168d4109deaeed491928c2a6b575ecabdbe91aabd5b2bb1f8b9948f2bd",
    MAIN_SOURCE_PATH: "c128cb8404e220523366529c80fc02e8a42aad934d7228dfb5c8698ddb7f96ee",
    TEST_SOURCE_PATH: "09e8b7e6edcaaabc26b3427bb5e1bad9784f1f494a2545a99e80071365fc9907",
    (
        "docs/evidence/poc-decision-001/"
        "decision-deterministic-synthetic-harness-local-evidence-stage0-v0.1.json"
    ): "ee64af394111744b26d7c4dac11cfc8a07aa301b4bdfa7191d8a0883a84d5d59",
    (
        "tools/decision_deterministic_synthetic_harness/src/main/java/com/monumentogram/"
        "dora/stage0/decision/synthetic/DecisionDeterministicSyntheticHarness.java"
    ): "2adb2a4e6706212f5b7530a647e7ef90bd5d423f388e988f845c2e3690740411",
    (
        "tools/decision_deterministic_synthetic_harness/src/test/java/com/monumentogram/"
        "dora/stage0/decision/synthetic/DecisionDeterministicSyntheticHarnessTest.java"
    ): "064d4ad5b00a18d1746fe2f6e29367aad9b0473b99e8cce5746f3d0b8a2e8fec",
    (
        "docs/evidence/poc-decision-001/"
        "decision-i3-synthetic-campaign-local-evidence-stage0-v0.1.json"
    ): "5540efbc37574cb93de709c4a6d92069725b6f72191d68903d62b951e661c6d1",
    (
        "tools/decision_i3_synthetic_campaign/src/main/java/com/monumentogram/dora/"
        "stage0/decision/i3/DecisionSyntheticCampaign.java"
    ): "9243aecc97380f9188eb6e7c1d8307b9a010b7562a4b2ba0e0c229d91362b2a4",
    (
        "tools/decision_i3_synthetic_campaign/src/test/java/com/monumentogram/dora/"
        "stage0/decision/i3/DecisionSyntheticCampaignTest.java"
    ): "0e5896e71270293c92bdedec7f99a6b4ce8ed7463ff7f423433c0d64a9db781d",
}

EXPECTED_SOURCE_PINS = [
    {
        "sourceId": "SOURCE_RU",
        "language": "RU",
        "utf8Bytes": 63,
        "wholeSourceSha256": (
            "33ad03befe0d15cdb282314005ab9a6a5703e6b7d9135a22ec47c1befd1ae9f5"
        ),
        "excerptStartInclusive": 20,
        "excerptEndExclusive": 34,
        "excerptSha256": (
            "924a6e352c6e520050fd01842d1cc04963d2a7e3546d7adaf08921a044497e40"
        ),
    },
    {
        "sourceId": "SOURCE_EN",
        "language": "EN",
        "utf8Bytes": 45,
        "wholeSourceSha256": (
            "d713a2e2ff20860d7a8eed4979b69baf553aa7fb4cde2ac3e03899c7b23b8519"
        ),
        "excerptStartInclusive": 11,
        "excerptEndExclusive": 31,
        "excerptSha256": (
            "8f5b63d4dc280da2dd6edccb14496d8957d1acfd729980ee0cbb56f007e5a5a9"
        ),
    },
    {
        "sourceId": "SOURCE_MIXED",
        "language": "MIXED_RU_EN",
        "utf8Bytes": 48,
        "wholeSourceSha256": (
            "a68a839e1fd0b824ae6dbf8f5d87832cfeaf44a660abde39e6746b8fbfb79372"
        ),
        "excerptStartInclusive": 11,
        "excerptEndExclusive": 25,
        "excerptSha256": (
            "924a6e352c6e520050fd01842d1cc04963d2a7e3546d7adaf08921a044497e40"
        ),
    },
]

EXPECTED_ACCEPTED_CASES = [
    "CASE_ACCEPT_RU",
    "CASE_ACCEPT_EN",
    "CASE_ACCEPT_MIXED",
]
EXPECTED_REJECTED_CASES = [
    {"caseId": "CASE_REJECT_FORGED_SOURCE_ID", "diagnostic": "UNKNOWN_SOURCE_ID"},
    {
        "caseId": "CASE_REJECT_WHOLE_SHA",
        "diagnostic": "WHOLE_SOURCE_SHA256_MISMATCH",
    },
    {
        "caseId": "CASE_REJECT_EXCERPT_SHA",
        "diagnostic": "EXCERPT_SHA256_MISMATCH",
    },
    {"caseId": "CASE_REJECT_NEGATIVE_RANGE", "diagnostic": "RANGE_NEGATIVE"},
    {
        "caseId": "CASE_REJECT_OUT_OF_RANGE",
        "diagnostic": "RANGE_OUT_OF_BOUNDS",
    },
    {"caseId": "CASE_REJECT_REVERSED_RANGE", "diagnostic": "RANGE_REVERSED"},
    {"caseId": "CASE_REJECT_EMPTY_RANGE", "diagnostic": "RANGE_EMPTY"},
    {
        "caseId": "CASE_REJECT_MID_UTF8_START",
        "diagnostic": "RANGE_NOT_UTF8_BOUNDARY",
    },
    {
        "caseId": "CASE_REJECT_MID_UTF8_END",
        "diagnostic": "RANGE_NOT_UTF8_BOUNDARY",
    },
    {
        "caseId": "CASE_REJECT_SCHEMA_VERSION",
        "diagnostic": "SCHEMA_VERSION_MISMATCH",
    },
    {
        "caseId": "CASE_REJECT_SOURCE_VERSION",
        "diagnostic": "SOURCE_VERSION_MISMATCH",
    },
]

FORBIDDEN_SOURCE_PATTERNS = (
    ("network", re.compile(r"\bjava\.(?:net|http)\b|\bjavax\.net\b")),
    ("filesystem", re.compile(r"\bjava\.(?:io|nio\.file)\b")),
    (
        "clock",
        re.compile(
            r"System\.(?:currentTimeMillis|nanoTime)|Instant\.now|Clock\.system"
        ),
    ),
    ("random", re.compile(r"\b(?:SecureRandom|Random|ThreadLocalRandom)\b")),
    ("environment", re.compile(r"System\.(?:getenv|getProperty|setProperty)")),
    ("process", re.compile(r"\b(?:ProcessBuilder|Runtime\.getRuntime)\b")),
    ("thread", re.compile(r"\b(?:Thread|Executor|ForkJoin|CompletableFuture)\b")),
    ("reflection", re.compile(r"\bjava\.lang\.reflect\b|Class\.forName")),
    ("android", re.compile(r"\b(?:android|androidx|com\.google)\.")),
)
FORBIDDEN_CLASS_BYTES = (
    b"java/net/",
    b"java/net/http/",
    b"javax/net/",
    b"java/io/File",
    b"java/io/InputStream",
    b"java/io/OutputStream",
    b"java/nio/file/",
    b"java/lang/Thread",
    b"java/lang/ProcessBuilder",
    b"java/util/Random",
    b"java/util/concurrent/",
    b"java/lang/reflect/",
    b"android/",
    b"androidx/",
    b"com/google/",
)
EMAIL_RE = re.compile(r"(?<![A-Za-z0-9._%+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
URL_RE = re.compile(r"https?://[^\s)\]}>\"']+")
PRIVATE_IPV4_RE = re.compile(
    r"\b(?:10(?:\.\d{1,3}){3}|127(?:\.\d{1,3}){3}|169\.254(?:\.\d{1,3}){2}|"
    r"192\.168(?:\.\d{1,3}){2}|172\.(?:1[6-9]|2\d|3[01])(?:\.\d{1,3}){2})\b"
)
ABSOLUTE_LOCAL_PATH_RE = re.compile(
    r"(?:[A-Za-z]:[\\/](?:Users|Documents|AppData|home)[\\/]|/(?:home|Users|private|tmp)/)"
)
SECRET_RE = re.compile(
    r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|"
    r"\b(?:ghp|github_pat|sk-[A-Za-z0-9]{8})_[A-Za-z0-9_]{16,}\b"
)


class RunnerError(RuntimeError):
    """Fail-closed validation error."""


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RunnerError(message)


def _run_process(
    command: Sequence[str],
    *,
    cwd: Path,
    env: Mapping[str, str] | None = None,
    timeout_seconds: int = 180,
    label: str,
) -> subprocess.CompletedProcess[bytes]:
    try:
        return subprocess.run(
            tuple(command),
            cwd=cwd,
            env=None if env is None else dict(env),
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
            timeout=timeout_seconds,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise RunnerError(f"{label} could not complete") from error


def _git(repository: Path, *args: str) -> subprocess.CompletedProcess[bytes]:
    return _run_process(("git", *args), cwd=repository, label="git " + " ".join(args))


def discover_repository() -> Path:
    script_repository = Path(__file__).resolve().parent.parent
    result = _git(script_repository, "rev-parse", "--show-toplevel")
    require(result.returncode == 0 and not result.stderr, "repository root cannot be resolved")
    try:
        git_repository = Path(result.stdout.decode("utf-8", "strict").strip()).resolve(strict=True)
    except (OSError, UnicodeError) as error:
        raise RunnerError("repository root is invalid") from error
    require(git_repository == script_repository, "runner is not in the repository root")
    return git_repository


def _resolve_repository_file(repository: Path, relative_path: str) -> Path:
    pure = PurePosixPath(relative_path)
    require(not pure.is_absolute() and ".." not in pure.parts, "repository path is unsafe")
    candidate = repository.joinpath(*pure.parts)
    try:
        resolved = candidate.resolve(strict=True)
        resolved.relative_to(repository)
    except (OSError, ValueError) as error:
        raise RunnerError(f"repository path escapes or is missing: {relative_path}") from error
    require(not candidate.is_symlink(), f"symlink is forbidden: {relative_path}")
    require(resolved.is_file(), f"expected regular file: {relative_path}")
    return resolved


def _canonical_text_bytes(raw: bytes, label: str) -> tuple[str, bytes]:
    require(not raw.startswith(b"\xef\xbb\xbf"), f"{label}: UTF-8 BOM is forbidden")
    require(b"\x00" not in raw, f"{label}: NUL is forbidden")
    try:
        text = raw.decode("utf-8", "strict")
    except UnicodeDecodeError as error:
        raise RunnerError(f"{label}: strict UTF-8 decode failed") from error
    canonical_text = text.replace("\r\n", "\n")
    require("\r" not in canonical_text, f"{label}: lone carriage return is forbidden")
    require(canonical_text.endswith("\n"), f"{label}: final LF is required")
    return canonical_text, canonical_text.encode("utf-8", "strict")


def _read_canonical_file(path: Path, label: str) -> tuple[str, bytes]:
    try:
        raw = path.read_bytes()
    except OSError as error:
        raise RunnerError(f"{label}: cannot read file") from error
    return _canonical_text_bytes(raw, label)


def _sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _reject_duplicate_keys(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise RunnerError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def load_evidence(path: Path) -> dict[str, Any]:
    text, _ = _read_canonical_file(path, EVIDENCE_PATH)
    try:
        value = json.loads(
            text,
            object_pairs_hook=_reject_duplicate_keys,
            parse_constant=lambda constant: (_ for _ in ()).throw(
                RunnerError(f"non-finite JSON value: {constant}")
            ),
        )
    except (json.JSONDecodeError, TypeError) as error:
        raise RunnerError("evidence JSON is invalid") from error
    require(isinstance(value, dict), "evidence root must be an object")
    return value


def _require_keys(value: Mapping[str, Any], expected: set[str], label: str) -> None:
    require(set(value) == expected, f"{label}: exact key set drifted")


def validate_evidence(evidence: Mapping[str, Any]) -> None:
    _require_keys(
        evidence,
        {
            "recordType",
            "recordVersion",
            "recordedDate",
            "authority",
            "scope",
            "generatedSourcePins",
            "caseMatrix",
            "contextPins",
            "fileDigestBasis",
            "files",
            "verification",
            "dataAndPublicationSafety",
            "nonClaims",
            "outcome",
        },
        "evidence",
    )
    require(
        evidence["recordType"]
        == "DORA_STAGE0_POC_DECISION_I4_SYNTHETIC_PROVENANCE_SOURCE_VALIDATOR_LOCAL_EVIDENCE",
        "record type drifted",
    )
    require(evidence["recordVersion"] == "0.1", "record version drifted")
    require(evidence["recordedDate"] == "2026-08-19", "recorded date drifted")
    require(
        evidence["fileDigestBasis"] == "STRICT_UTF8_CANONICAL_LF_SHA256",
        "digest basis drifted",
    )

    authority = evidence["authority"]
    require(isinstance(authority, dict), "authority must be an object")
    _require_keys(
        authority,
        {
            "authorization",
            "backlogId",
            "branch",
            "baselineCommit",
            "baselineTree",
            "scopeFrozenCommit",
            "implementationAuthority",
            "executionAuthority",
            "mergeAuthority",
        },
        "authority",
    )
    expected_authority = {
        "authorization": "OWNER-AUTH-BATCH-20260819-01_DECISION_I4_PRIORITY",
        "backlogId": "POC-DECISION-001",
        "branch": "codex/poc-decision-i4-provenance-source-validator",
        "baselineCommit": BASELINE_COMMIT,
        "baselineTree": BASELINE_TREE,
        "scopeFrozenCommit": SCOPE_COMMIT,
        "implementationAuthority": "PURE_HOST_GENERATED_FIXTURE_ONLY",
        "executionAuthority": "LOCAL_AND_CI_STATIC_HOST_ONLY",
        "mergeAuthority": "WITHHELD_PENDING_EXACT_HEAD_INDEPENDENT_REVIEW",
    }
    require(authority == expected_authority, "authority values drifted")

    scope = evidence["scope"]
    require(isinstance(scope, dict), "scope must be an object")
    _require_keys(
        scope,
        {
            "profile",
            "executionClass",
            "dependencyBoundary",
            "envelopeSchemaVersion",
            "sourceSetVersion",
            "rangeUnit",
            "rangeUnitIsProductContract",
            "productSchemaCreated",
            "generatedSourceCount",
            "generatedCaseCount",
            "governedBenchmarkCaseCount",
            "adjudicatedCorpusCaseCount",
            "realMeetingCount",
            "participantCount",
            "audioCount",
            "modelCount",
            "deviceCount",
            "networkOperationCount",
            "filesystemPersistenceOperationCount",
            "androidModuleCount",
            "dependencyAdditionCount",
        },
        "scope",
    )
    require(scope["profile"] == PROFILE_VERSION, "scope profile drifted")
    require(
        scope["executionClass"] == "PURE_HOST_LOCAL_GENERATED_PROVENANCE_MECHANICS",
        "execution class drifted",
    )
    require(scope["dependencyBoundary"] == "JDK17_JAVA_BASE_ONLY", "boundary drifted")
    require(scope["envelopeSchemaVersion"] == ENVELOPE_SCHEMA_VERSION, "schema drifted")
    require(scope["sourceSetVersion"] == SOURCE_SET_VERSION, "source version drifted")
    require(scope["rangeUnit"] == RANGE_UNIT, "range unit drifted")
    require(scope["rangeUnitIsProductContract"] is False, "range became product contract")
    require(scope["productSchemaCreated"] is False, "product schema claim drifted")
    require(scope["generatedSourceCount"] == 3, "generated source count drifted")
    require(scope["generatedCaseCount"] == 14, "generated case count drifted")
    for zero_key in (
        "governedBenchmarkCaseCount",
        "adjudicatedCorpusCaseCount",
        "realMeetingCount",
        "participantCount",
        "audioCount",
        "modelCount",
        "deviceCount",
        "networkOperationCount",
        "filesystemPersistenceOperationCount",
        "androidModuleCount",
        "dependencyAdditionCount",
    ):
        require(scope[zero_key] == 0, f"scope count elevated: {zero_key}")

    require(evidence["generatedSourcePins"] == EXPECTED_SOURCE_PINS, "source pins drifted")
    matrix = evidence["caseMatrix"]
    require(isinstance(matrix, dict), "case matrix must be an object")
    _require_keys(
        matrix,
        {
            "accepted",
            "rejected",
            "acceptedCount",
            "rejectedCount",
            "eachRejectedCaseHasExactlyOneDiagnostic",
            "invalidRangeSlicingPerformed",
            "inputOrderChangesCanonicalOutput",
            "candidateApplicationPerformed",
            "stateMutationPerformed",
        },
        "case matrix",
    )
    require(matrix["accepted"] == EXPECTED_ACCEPTED_CASES, "accepted cases drifted")
    require(matrix["rejected"] == EXPECTED_REJECTED_CASES, "rejected cases drifted")
    require(matrix["acceptedCount"] == 3, "accepted count drifted")
    require(matrix["rejectedCount"] == 11, "rejected count drifted")
    require(matrix["eachRejectedCaseHasExactlyOneDiagnostic"] is True, "diagnostic count drifted")
    for false_key in (
        "invalidRangeSlicingPerformed",
        "inputOrderChangesCanonicalOutput",
        "candidateApplicationPerformed",
        "stateMutationPerformed",
    ):
        require(matrix[false_key] is False, f"case matrix ceiling elevated: {false_key}")

    context = evidence["contextPins"]
    require(isinstance(context, dict), "context pins must be an object")
    _require_keys(context, {"scope", "decisionI2", "decisionI3"}, "context pins")
    require(
        context["scope"]
        == {"path": SCOPE_PATH, "canonicalLfSha256": PIN_HASHES[SCOPE_PATH]},
        "scope pin drifted",
    )
    require(context["decisionI2"]["modified"] is False, "I2 modification claim drifted")
    require(context["decisionI3"]["modified"] is False, "I3 modification claim drifted")
    require(
        context["decisionI2"]["relationship"]
        == "UNCHANGED_CONTEXT_ONLY_NO_I2_REDEFINITION",
        "I2 relationship drifted",
    )
    require(
        context["decisionI3"]["relationship"]
        == "UNCHANGED_CONTEXT_ONLY_NO_I3_CAMPAIGN_REPEAT",
        "I3 relationship drifted",
    )

    files = evidence["files"]
    require(isinstance(files, list) and len(files) == 2, "file declarations drifted")
    expected_file_values = {
        MAIN_SOURCE_PATH: (
            "DEPENDENCY_FREE_GENERATED_PROVENANCE_ENVELOPE_MECHANICS",
            PIN_HASHES[MAIN_SOURCE_PATH],
            23280,
        ),
        TEST_SOURCE_PATH: (
            "EXACT_GENERATED_MATRIX_ADVERSARIAL_JAVA17_TEST",
            PIN_HASHES[TEST_SOURCE_PATH],
            16134,
        ),
    }
    for file_entry in files:
        require(isinstance(file_entry, dict), "file declaration must be an object")
        _require_keys(
            file_entry,
            {
                "path",
                "role",
                "canonicalLfSha256",
                "canonicalLfBytes",
                "asciiOnlySourceRepresentation",
            },
            "file declaration",
        )
        path = file_entry["path"]
        require(path in expected_file_values, "unexpected file declaration")
        role, digest, byte_count = expected_file_values[path]
        require(file_entry["role"] == role, f"role drifted: {path}")
        require(file_entry["canonicalLfSha256"] == digest, f"digest drifted: {path}")
        require(file_entry["canonicalLfBytes"] == byte_count, f"byte count drifted: {path}")
        require(file_entry["asciiOnlySourceRepresentation"] is True, "Java source is not ASCII")
    require({entry["path"] for entry in files} == set(expected_file_values), "file set drifted")

    verification = evidence["verification"]
    require(isinstance(verification, dict), "verification must be an object")
    require(verification["compileExitCode"] == 0, "compile result drifted")
    require(verification["compilerStdoutBytes"] == 0, "compiler stdout claim drifted")
    require(verification["compilerStderrBytes"] == 0, "compiler stderr claim drifted")
    require(verification["classMajorVersion"] == 61, "class major drifted")
    require(verification["dependencySummary"] == "I4_MAIN_AND_TEST_CLASSES -> java.base", "jdeps drifted")
    require(verification["testExitCode"] == 0, "test result drifted")
    require(verification["testStdout"].encode("utf-8") == EXPECTED_TEST_STDOUT, "test stdout drifted")
    require(
        verification["testStdoutSha256"] == EXPECTED_TEST_STDOUT_SHA256,
        "test stdout hash drifted",
    )
    require(verification["testStderrBytes"] == 0, "test stderr claim drifted")
    require(verification["runnerRepeatCountPerInvocation"] == TEST_REPEAT_COUNT, "repeat drifted")
    require(verification["uniqueRepeatStdoutHashesPerInvocation"] == 1, "repeat hash drifted")
    require(verification["requiredLocalFullRunnerInvocations"] == 2, "local runs drifted")
    require(verification["runnerStdout"] == EXPECTED_RUNNER_STDOUT, "runner stdout drifted")
    require(
        verification["runnerStdoutSha256"] == EXPECTED_RUNNER_STDOUT_SHA256,
        "runner stdout hash drifted",
    )
    require(
        verification["canonicalOutputSha256"] == EXPECTED_CANONICAL_SHA256,
        "canonical digest drifted",
    )
    require(
        verification["forbiddenRuntimeSurfaceCheck"] == "SUCCESS_NONE_FOUND",
        "surface result drifted",
    )
    require(
        verification["privacyAndClaimCeilingCheck"] == "SUCCESS_NONE_FOUND",
        "privacy result drifted",
    )
    require(verification["temporaryCleanupRequired"] is True, "cleanup requirement drifted")
    require(
        verification["repositoryStatusUnchangedRequired"] is True,
        "repository cleanliness requirement drifted",
    )
    require(verification["ciAtEvidenceCreation"] == "NOT_RUN", "CI history claim drifted")

    safety = evidence["dataAndPublicationSafety"]
    require(isinstance(safety, dict), "safety must be an object")
    require(safety["usesOnlyProgrammaticallyGeneratedText"] is True, "synthetic boundary drifted")
    for false_key in (
        "evidenceContainsGeneratedSourceOrExcerptText",
        "containsPersonalData",
        "containsSecret",
        "containsCredential",
        "containsPrivateAddressOrChannel",
        "containsRealPersonOrOrganization",
        "containsRealMeeting",
        "containsTranscriptOrAudio",
        "containsModelOrWeight",
        "containsDatasetOrTrainingData",
        "containsDeviceIdentifier",
        "containsLocalAbsolutePath",
        "cloudTransmissionPerformed",
        "modelTrainingPerformed",
    ):
        require(safety[false_key] is False, f"safety ceiling elevated: {false_key}")
    require(
        safety["repositoryPublicationState"] == "LOCAL_UNPUBLISHED_AT_EVIDENCE_CREATION",
        "publication history drifted",
    )
    non_claims = evidence["nonClaims"]
    require(isinstance(non_claims, list) and len(non_claims) == 8, "non-claims drifted")
    require(all(isinstance(item, str) and item for item in non_claims), "non-claim is invalid")

    outcome = evidence["outcome"]
    require(isinstance(outcome, dict), "outcome must be an object")
    expected_outcome = {
        "claimCeiling": CLAIM_CEILING,
        "pocVerdict": "NOT_RUN",
        "pocReadiness": "BLOCKED_UNCHANGED",
        "fullSourceValidatorGapClosed": False,
        "governedScoringGapClosed": False,
        "qualityClaim": False,
        "readinessElevation": False,
        "productAdmission": False,
        "sharedBacklogOrStageStatusModified": False,
        "independentReviewRequired": True,
        "mergePerformed": False,
    }
    require(outcome == expected_outcome, "outcome or claim ceiling drifted")


def verify_pins(repository: Path) -> dict[str, str]:
    texts: dict[str, str] = {}
    for relative_path, expected_hash in PIN_HASHES.items():
        path = _resolve_repository_file(repository, relative_path)
        text, canonical = _read_canonical_file(path, relative_path)
        require(_sha256(canonical) == expected_hash, f"canonical hash mismatch: {relative_path}")
        tracked = _git(repository, "ls-files", "--error-unmatch", "--", relative_path)
        require(tracked.returncode == 0, f"pin is not tracked: {relative_path}")
        texts[relative_path] = text
    require(all(byte < 128 for byte in texts[MAIN_SOURCE_PATH].encode("utf-8")), "main source is not ASCII")
    require(all(byte < 128 for byte in texts[TEST_SOURCE_PATH].encode("utf-8")), "test source is not ASCII")
    return texts


def _scan_privacy(text: str, label: str) -> None:
    for name, pattern in (
        ("email", EMAIL_RE),
        ("URL", URL_RE),
        ("private IPv4", PRIVATE_IPV4_RE),
        ("absolute local path", ABSOLUTE_LOCAL_PATH_RE),
        ("secret material", SECRET_RE),
    ):
        require(pattern.search(text) is None, f"{label}: forbidden {name}")


def _scan_main_source(text: str) -> None:
    for name, pattern in FORBIDDEN_SOURCE_PATTERNS:
        require(pattern.search(text) is None, f"main source uses forbidden {name} surface")
    require("pocVerdict=PASS" not in text, "main source contains a PoC PASS path")
    require("pocReadiness=READY" not in text, "main source contains a READY path")
    require("System.out" not in text and "System.err" not in text, "main source writes console")


def validate_public_files(repository: Path, pin_texts: Mapping[str, str]) -> None:
    generated_source_texts = (
        "\u0421\u0418\u041d\u0422\u0415\u0422\u0418\u041a\u0410: "
        + "\u0440\u0435\u0448\u0435\u043d\u0438\u0435"
        + " \u043f\u043e\u0441\u043b\u0435 \u043f\u0440\u043e\u0432\u0435\u0440\u043a\u0438.",
        "SYNTHETIC: " + "generated_" + "en_excerpt" + " after review.",
        "SYNTHETIC: "
        + "\u0440\u0435\u0448\u0435\u043d\u0438\u0435"
        + " accepted after review.",
    )
    generated_excerpts = (
        "\u0440\u0435\u0448\u0435\u043d\u0438\u0435",
        "generated_" + "en_excerpt",
    )
    for relative_path in EXPECTED_TRACKED_FILES:
        path = _resolve_repository_file(repository, relative_path)
        text, _ = _read_canonical_file(path, relative_path)
        _scan_privacy(text, relative_path)
        if relative_path not in (MAIN_SOURCE_PATH, TEST_SOURCE_PATH):
            for source_text in generated_source_texts:
                require(source_text not in text, f"{relative_path}: generated source text leaked")
            for excerpt in generated_excerpts:
                require(excerpt not in text, f"{relative_path}: generated excerpt leaked")
    _scan_main_source(pin_texts[MAIN_SOURCE_PATH])
    require(
        CLAIM_CEILING in pin_texts[SCOPE_PATH],
        "scope does not bind the exact claim ceiling",
    )
    workflow_text, _ = _read_canonical_file(
        _resolve_repository_file(repository, WORKFLOW_PATH), WORKFLOW_PATH
    )
    required_workflow_fragments = (
        "pull_request:",
        "push:",
        "workflow_dispatch:",
        "contents: read",
        "persist-credentials: false",
        "--self-test",
        RUNNER_PATH,
        "DORA_I4_DIFF_BASE",
    )
    for fragment in required_workflow_fragments:
        require(fragment in workflow_text, f"workflow requirement missing: {fragment}")
    for forbidden in ("pull_request_target:", "contents: write", "actions/upload-artifact"):
        require(forbidden not in workflow_text, f"workflow forbidden surface: {forbidden}")


def _repository_status(repository: Path) -> bytes:
    result = _git(repository, "status", "--porcelain=v1", "--untracked-files=all")
    require(result.returncode == 0 and not result.stderr, "git status failed")
    return result.stdout


def _validate_commit_id(value: str, label: str) -> str:
    require(re.fullmatch(r"[0-9a-f]{40}", value) is not None, f"{label} is not a full commit ID")
    return value


def verify_repository_snapshot(repository: Path, diff_base: str) -> None:
    require(_repository_status(repository) == b"", "full validation requires a clean worktree")
    baseline = _git(repository, "cat-file", "-e", BASELINE_COMMIT + "^{commit}")
    require(baseline.returncode == 0, "baseline commit is missing")
    tree = _git(repository, "show", "-s", "--format=%T", BASELINE_COMMIT)
    require(tree.returncode == 0 and not tree.stderr, "baseline tree cannot be read")
    require(tree.stdout.decode("ascii").strip() == BASELINE_TREE, "baseline tree drifted")
    ancestor = _git(repository, "merge-base", "--is-ancestor", BASELINE_COMMIT, "HEAD")
    require(ancestor.returncode == 0, "baseline is not an ancestor of HEAD")
    diff_base = _validate_commit_id(diff_base, "diff base")
    base_exists = _git(repository, "cat-file", "-e", diff_base + "^{commit}")
    require(base_exists.returncode == 0, "diff base commit is missing")
    base_ancestor = _git(repository, "merge-base", "--is-ancestor", diff_base, "HEAD")
    require(base_ancestor.returncode == 0, "diff base is not an ancestor of HEAD")
    diff = _git(
        repository,
        "diff",
        "--name-status",
        "--no-renames",
        diff_base,
        "HEAD",
        "--",
    )
    require(diff.returncode == 0 and not diff.stderr, "changed-file diff failed")
    try:
        lines = diff.stdout.decode("utf-8", "strict").splitlines()
    except UnicodeDecodeError as error:
        raise RunnerError("changed-file diff is not UTF-8") from error
    expected_lines = {"A\t" + path for path in EXPECTED_TRACKED_FILES}
    require(set(lines) == expected_lines and len(lines) == len(expected_lines), "exact file allowlist drifted")
    diff_check = _git(repository, "diff", "--check", diff_base, "HEAD", "--")
    require(diff_check.returncode == 0 and not diff_check.stdout and not diff_check.stderr, "git diff --check failed")
    for relative_path in EXPECTED_TRACKED_FILES:
        tracked = _git(repository, "ls-files", "--error-unmatch", "--", relative_path)
        require(tracked.returncode == 0, f"expected file is not tracked: {relative_path}")


def resolve_toolchain(repository: Path) -> dict[str, str]:
    tools: dict[str, str] = {}
    for name in ("java", "javac", "jdeps", "javap"):
        resolved = shutil.which(name)
        require(resolved is not None, f"required tool is missing: {name}")
        tools[name] = str(Path(resolved).resolve())
    for name in ("javac", "jdeps", "javap"):
        result = _run_process((tools[name], "-version"), cwd=repository, label=name + " version")
        require(result.returncode == 0, f"{name} version failed")
        version_text = (result.stdout + result.stderr).decode("utf-8", "strict").strip()
        match = re.search(r"(?:javac\s+)?(\d+)(?:\.\d+)*", version_text)
        require(match is not None and int(match.group(1)) == 17, f"{name} is not major 17")
    java_version = _run_process((tools["java"], "-version"), cwd=repository, label="java version")
    require(java_version.returncode == 0, "java version failed")
    java_text = (java_version.stdout + java_version.stderr).decode("utf-8", "strict")
    require(re.search(r'version "17(?:\.|\")', java_text) is not None, "java is not major 17")
    return tools


def _clean_environment(temp_root: Path) -> dict[str, str]:
    environment = dict(os.environ)
    for name in ("JAVA_TOOL_OPTIONS", "_JAVA_OPTIONS", "JDK_JAVA_OPTIONS", "CLASSPATH"):
        environment.pop(name, None)
    environment.update(
        {
            "TMP": str(temp_root),
            "TEMP": str(temp_root),
            "TMPDIR": str(temp_root),
            "TZ": "UTC",
            "PYTHONDONTWRITEBYTECODE": "1",
        }
    )
    return environment


def _repository_class_files(repository: Path) -> tuple[str, ...]:
    return tuple(
        sorted(
            path.relative_to(repository).as_posix()
            for path in repository.rglob("*.class")
            if ".git" not in path.parts
        )
    )


def _verify_class_major(class_files: Sequence[Path]) -> None:
    require(class_files, "javac emitted no class files")
    for class_file in class_files:
        try:
            header = class_file.read_bytes()[:8]
        except OSError as error:
            raise RunnerError("class file cannot be read") from error
        require(len(header) == 8 and header[:4] == b"\xca\xfe\xba\xbe", "invalid class header")
        major = struct.unpack(">H", header[6:8])[0]
        require(major == 61, f"class major is not 61: {class_file.name}")


def _verify_compiled_surfaces(class_files: Sequence[Path]) -> None:
    for class_file in class_files:
        try:
            content = class_file.read_bytes()
        except OSError as error:
            raise RunnerError("compiled class cannot be scanned") from error
        for forbidden in FORBIDDEN_CLASS_BYTES:
            require(forbidden not in content, f"compiled forbidden surface: {forbidden!r}")


def _verify_jdeps(
    repository: Path, classes: Path, tools: Mapping[str, str], environment: Mapping[str, str]
) -> None:
    result = _run_process(
        (tools["jdeps"], "-J-XX:-UsePerfData", "-s", "-R", str(classes)),
        cwd=repository,
        env=environment,
        label="jdeps",
    )
    require(result.returncode == 0 and not result.stderr, "jdeps failed or emitted stderr")
    try:
        output = result.stdout.decode("utf-8", "strict")
    except UnicodeDecodeError as error:
        raise RunnerError("jdeps output is not UTF-8") from error
    dependencies = set(re.findall(r"->\s+([A-Za-z0-9_.]+)\s*$", output, re.MULTILINE))
    require(dependencies == {"java.base"}, f"jdeps boundary drifted: {sorted(dependencies)}")


def run_full_validation(
    repository: Path, tools: Mapping[str, str], before_status: bytes
) -> None:
    before_classes = _repository_class_files(repository)
    require(not before_classes, "repository already contains class files")
    temp_parent_value = os.environ.get("RUNNER_TEMP")
    temp_parent: Path | None = None
    if temp_parent_value:
        try:
            temp_parent = Path(temp_parent_value).resolve(strict=True)
        except OSError as error:
            raise RunnerError("RUNNER_TEMP is invalid") from error
        require(temp_parent.is_dir(), "RUNNER_TEMP is not a directory")
    try:
        temp_root = Path(
            tempfile.mkdtemp(prefix="dora-decision-i4-", dir=temp_parent)
        ).resolve(strict=True)
    except OSError as error:
        raise RunnerError("temporary root cannot be created") from error
    try:
        try:
            temp_root.relative_to(repository)
        except ValueError:
            pass
        else:
            raise RunnerError("temporary root must be outside the repository")
        classes = temp_root / "classes"
        tool_temp = temp_root / "tool-temp"
        java_temp = temp_root / "java-temp"
        classes.mkdir()
        tool_temp.mkdir()
        java_temp.mkdir()
        tool_environment = _clean_environment(tool_temp)
        java_environment = _clean_environment(java_temp)
        main_source = _resolve_repository_file(repository, MAIN_SOURCE_PATH)
        test_source = _resolve_repository_file(repository, TEST_SOURCE_PATH)
        compile_result = _run_process(
            (
                tools["javac"],
                "-J-XX:-UsePerfData",
                "--release",
                "17",
                "-encoding",
                "UTF-8",
                "-Xlint:all",
                "-Werror",
                "-d",
                str(classes),
                str(main_source),
                str(test_source),
            ),
            cwd=repository,
            env=tool_environment,
            label="strict javac",
        )
        require(compile_result.returncode == 0, "strict javac failed")
        require(not compile_result.stdout, "strict javac emitted stdout")
        require(not compile_result.stderr, "strict javac emitted stderr")
        class_files = tuple(sorted(classes.rglob("*.class")))
        _verify_class_major(class_files)
        _verify_compiled_surfaces(class_files)
        _verify_jdeps(repository, classes, tools, tool_environment)

        observed_stdout: bytes | None = None
        classpath = str(classes)
        for repeat in range(1, TEST_REPEAT_COUNT + 1):
            test_result = _run_process(
                (
                    tools["java"],
                    "-ea",
                    "-Xverify:all",
                    "-XX:-UsePerfData",
                    "-Dfile.encoding=UTF-8",
                    "-Duser.language=en",
                    "-Duser.country=US",
                    "-Duser.timezone=UTC",
                    f"-Djava.io.tmpdir={java_temp}",
                    "-cp",
                    classpath,
                    TEST_CLASS,
                ),
                cwd=repository,
                env=java_environment,
                label=f"Decision I4 test repeat {repeat}",
            )
            require(test_result.returncode == 0, f"test repeat {repeat} failed")
            require(not test_result.stderr, f"test repeat {repeat} emitted stderr")
            require(test_result.stdout == EXPECTED_TEST_STDOUT, "exact test stdout drifted")
            require(
                _sha256(test_result.stdout) == EXPECTED_TEST_STDOUT_SHA256,
                "test stdout hash drifted",
            )
            if observed_stdout is not None:
                require(test_result.stdout == observed_stdout, "repeat stdout drifted")
            observed_stdout = test_result.stdout
            require(not any(java_temp.iterdir()), "test retained Java temporary files")
    finally:
        cleanup_error: OSError | None = None
        try:
            shutil.rmtree(temp_root)
        except OSError as error:
            cleanup_error = error
        if temp_root.exists() or cleanup_error is not None:
            raise RunnerError("temporary cleanup failed") from cleanup_error
    require(_repository_status(repository) == before_status, "repository status changed")
    require(_repository_class_files(repository) == before_classes, "class artifact leaked")


def _expect_evidence_rejection(
    name: str,
    evidence: Mapping[str, Any],
    mutation: Callable[[dict[str, Any]], None],
) -> None:
    candidate = copy.deepcopy(evidence)
    mutation(candidate)
    try:
        validate_evidence(candidate)
    except RunnerError:
        return
    raise RunnerError(f"self-test did not reject evidence mutation: {name}")


def _expect_boundary_rejection(name: str, operation: Callable[[], None]) -> None:
    try:
        operation()
    except RunnerError:
        return
    raise RunnerError(f"self-test did not reject boundary: {name}")


def run_self_test(evidence: Mapping[str, Any]) -> None:
    evidence_cases: tuple[tuple[str, Callable[[dict[str, Any]], None]], ...] = (
        ("extra-key", lambda value: value.__setitem__("unexpected", True)),
        (
            "baseline",
            lambda value: value["authority"].__setitem__("baselineCommit", "0" * 40),
        ),
        (
            "schema-version",
            lambda value: value["scope"].__setitem__("envelopeSchemaVersion", "V2"),
        ),
        (
            "source-version",
            lambda value: value["scope"].__setitem__("sourceSetVersion", "V2"),
        ),
        (
            "source-pin",
            lambda value: value["generatedSourcePins"][0].__setitem__(
                "wholeSourceSha256", "0" * 64
            ),
        ),
        (
            "accepted-case",
            lambda value: value["caseMatrix"]["accepted"].pop(),
        ),
        (
            "diagnostic",
            lambda value: value["caseMatrix"]["rejected"][0].__setitem__(
                "diagnostic", "ACCEPTED"
            ),
        ),
        (
            "source-file-hash",
            lambda value: value["files"][0].__setitem__("canonicalLfSha256", "0" * 64),
        ),
        (
            "stdout",
            lambda value: value["verification"].__setitem__("testStdout", "WEAKENED\n"),
        ),
        (
            "repeat",
            lambda value: value["verification"].__setitem__(
                "runnerRepeatCountPerInvocation", 1
            ),
        ),
        (
            "class-major",
            lambda value: value["verification"].__setitem__("classMajorVersion", 65),
        ),
        (
            "claim",
            lambda value: value["outcome"].__setitem__("claimCeiling", "POC_PASS"),
        ),
        (
            "readiness",
            lambda value: value["outcome"].__setitem__("pocReadiness", "READY"),
        ),
        (
            "merge",
            lambda value: value["outcome"].__setitem__("mergePerformed", True),
        ),
    )
    for name, mutation in evidence_cases:
        _expect_evidence_rejection(name, evidence, mutation)
    boundary_cases: tuple[tuple[str, Callable[[], None]], ...] = (
        ("privacy-email", lambda: _scan_privacy("owner" + "@" + "example.com", "self-test")),
        ("privacy-url", lambda: _scan_privacy("https" + "://private.example/x", "self-test")),
        ("privacy-path", lambda: _scan_privacy("C:" + "\\Users\\private", "self-test")),
        (
            "source-network",
            lambda: _scan_main_source("import java." + "net.Socket;\n"),
        ),
        (
            "source-filesystem",
            lambda: _scan_main_source("import java.nio." + "file.Path;\n"),
        ),
        (
            "source-clock",
            lambda: _scan_main_source("long now = System." + "nanoTime();\n"),
        ),
        (
            "source-random",
            lambda: _scan_main_source("new java.util." + "Random();\n"),
        ),
        (
            "source-environment",
            lambda: _scan_main_source("System." + "getenv();\n"),
        ),
        (
            "utf8-bom",
            lambda: _canonical_text_bytes(b"\xef\xbb\xbf{}\n", "self-test"),
        ),
        (
            "lone-cr",
            lambda: _canonical_text_bytes(b"{}\rX\n", "self-test"),
        ),
    )
    for name, operation in boundary_cases:
        _expect_boundary_rejection(name, operation)
    print(
        "SELF_TEST_OK stage0-decision-i4-provenance-source-validator "
        f"cases={len(evidence_cases) + len(boundary_cases)}",
        flush=True,
    )


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Validate the exact Decision I4 generated provenance/source fixture."
    )
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--diff-base", default=None)
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    try:
        repository = discover_repository()
        evidence_file = _resolve_repository_file(repository, EVIDENCE_PATH)
        evidence = load_evidence(evidence_file)
        validate_evidence(evidence)
        pin_texts = verify_pins(repository)
        validate_public_files(repository, pin_texts)
        if args.self_test:
            run_self_test(evidence)
            return 0
        diff_base = args.diff_base or os.environ.get("DORA_I4_DIFF_BASE") or BASELINE_COMMIT
        verify_repository_snapshot(repository, diff_base)
        before_status = _repository_status(repository)
        tools = resolve_toolchain(repository)
        run_full_validation(repository, tools, before_status)
        require(
            _sha256(EXPECTED_RUNNER_STDOUT.encode("utf-8"))
            == EXPECTED_RUNNER_STDOUT_SHA256,
            "runner stdout hash constant drifted",
        )
        sys.stdout.write(EXPECTED_RUNNER_STDOUT)
        sys.stdout.flush()
        return 0
    except (OSError, RunnerError, UnicodeError) as error:
        print(f"VALIDATION_FAILED {error}", file=sys.stderr, flush=True)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
