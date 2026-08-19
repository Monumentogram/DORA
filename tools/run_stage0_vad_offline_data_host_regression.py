#!/usr/bin/env python3
"""Closed current-main regression runner for the Stage 0 VAD/Offline/Data host tools."""

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
from typing import Any, Callable, Iterable, Mapping, Sequence


SCHEMA_VERSION = "dora-stage0-vad-offline-data-host-regression-inventory-v0.1"
BASELINE_COMMIT = "671f594074b37bb2b5c8e4a4c1026de909acf339"
CLAIM_CEILING = "CURRENT_MAIN_VAD_OFFLINE_DATA_SYNTHETIC_HOST_MECHANICS_REVALIDATED"
HASH_PROFILE = "STRICT_UTF8_CANONICAL_LF_SHA256"
STDOUT_PROFILE = "EXACT_UTF8_DECLARED_LOGICAL_NEWLINES_TO_HOST_NATIVE"
INVENTORY_PATH = (
    "docs/evidence/stage0-vad-offline-data-host-regression/"
    "vad-offline-data-host-regression-inventory-stage0-v0.1.json"
)
EXPECTED_TRACKED_FILES = (
    ".github/workflows/stage0-vad-offline-data-host-regression.yml",
    INVENTORY_PATH,
    "docs/stage0/DORA_MVP1_VAD_OFFLINE_DATA_HOST_REGRESSION_SCOPE_STAGE0_V0_1.md",
    "tools/run_stage0_vad_offline_data_host_regression.py",
)
EXPECTED_COMMAND_COUNT = 11
EXPECTED_EXECUTION_COUNT = 21

PIN_SPECS = (
    (
        "docs/stage0/DORA_MVP1_VAD_OFFLINE_DATA_HOST_REGRESSION_SCOPE_STAGE0_V0_1.md",
        "VERSIONED_SCOPE_AND_CLAIM_CEILING",
    ),
    (
        "docs/evidence/poc-vad-001/p2-host-oracle-local-evidence-stage0-v0.1.json",
        "VAD_P2_LOCAL_EVIDENCE",
    ),
    (
        "tools/vad_p2_oracle/src/main/java/com/monumentogram/dora/stage0/vad/p2/"
        "VadBoundaryOracle.java",
        "VAD_P2_MAIN_SOURCE",
    ),
    (
        "tools/vad_p2_oracle/src/test/java/com/monumentogram/dora/stage0/vad/p2/"
        "VadBoundaryOracleTest.java",
        "VAD_P2_TEST_SOURCE",
    ),
    (
        "docs/evidence/poc-vad-001/"
        "p3-deterministic-integrated-replay-local-evidence-stage0-v0.1.json",
        "VAD_P3_LOCAL_EVIDENCE",
    ),
    (
        "tools/vad_p3_deterministic_replay/src/main/java/com/monumentogram/dora/stage0/vad/p3/"
        "VadDeterministicReplay.java",
        "VAD_P3_MAIN_SOURCE",
    ),
    (
        "tools/vad_p3_deterministic_replay/src/test/java/com/monumentogram/dora/stage0/vad/p3/"
        "VadDeterministicReplayTest.java",
        "VAD_P3_TEST_SOURCE",
    ),
    (
        "docs/evidence/poc-vad-001/"
        "p4-synthetic-pcm-rotation-local-evidence-stage0-v0.1.json",
        "VAD_P4_LOCAL_EVIDENCE",
    ),
    (
        "tools/vad_p4_pcm_rotation_oracle/src/main/java/com/monumentogram/dora/stage0/vad/p4/"
        "VadPcmRotationOracle.java",
        "VAD_P4_MAIN_SOURCE",
    ),
    (
        "tools/vad_p4_pcm_rotation_oracle/src/test/java/com/monumentogram/dora/stage0/vad/p4/"
        "VadPcmRotationOracleTest.java",
        "VAD_P4_TEST_SOURCE",
    ),
    (
        "docs/evidence/poc-offline-001/"
        "i1-host-oracle-implementation-evidence-stage0-v0.1.json",
        "OFFLINE_I1_LOCAL_EVIDENCE",
    ),
    (
        "tools/offline_i1_oracle/src/main/java/com/monumentogram/dora/stage0/offline/i1/"
        "OfflineI1Oracle.java",
        "OFFLINE_I1_MAIN_SOURCE",
    ),
    (
        "tools/offline_i1_oracle/src/test/java/com/monumentogram/dora/stage0/offline/i1/"
        "OfflineI1OracleTest.java",
        "OFFLINE_I1_TEST_SOURCE",
    ),
    (
        "docs/evidence/poc-offline-001/"
        "i2-integrated-synthetic-harness-evidence-stage0-v0.1.json",
        "OFFLINE_I2_LOCAL_EVIDENCE",
    ),
    (
        "tools/offline_i2_integrated_harness/src/main/java/com/monumentogram/dora/stage0/"
        "offline/i2/OfflineI2IntegratedHarness.java",
        "OFFLINE_I2_MAIN_SOURCE",
    ),
    (
        "tools/offline_i2_integrated_harness/src/test/java/com/monumentogram/dora/stage0/"
        "offline/i2/OfflineI2IntegratedHarnessTest.java",
        "OFFLINE_I2_TEST_SOURCE",
    ),
    (
        "docs/evidence/poc-offline-001/i3-static-call-ledger-local-evidence-stage0-v0.1.json",
        "OFFLINE_I3_LOCAL_EVIDENCE",
    ),
    (
        "tools/offline_i3_static_call_ledger/src/main/java/com/monumentogram/dora/stage0/"
        "offline/i3/OfflineI3StaticCallLedger.java",
        "OFFLINE_I3_MAIN_SOURCE",
    ),
    (
        "tools/offline_i3_static_call_ledger/src/test/java/com/monumentogram/dora/stage0/"
        "offline/i3/OfflineI3StaticCallLedgerTest.java",
        "OFFLINE_I3_TEST_SOURCE",
    ),
    (
        "docs/evidence/poc-data-001/"
        "synthetic-public-manifest-validator-local-evidence-stage0-v0.1.json",
        "DATA_MANIFEST_LOCAL_EVIDENCE",
    ),
    (
        "docs/stage0/poc-data-synthetic-public-projection-stage0-v0.1.schema.json",
        "DATA_MANIFEST_SCHEMA",
    ),
    (
        "tools/poc_data_manifest_validator/src/main/java/com/monumentogram/dora/stage0/data/"
        "manifest/SyntheticManifestValidator.java",
        "DATA_MANIFEST_MAIN_SOURCE",
    ),
    (
        "tools/poc_data_manifest_validator/src/test/java/com/monumentogram/dora/stage0/data/"
        "manifest/SyntheticManifestValidatorTest.java",
        "DATA_MANIFEST_TEST_SOURCE",
    ),
    (
        "docs/evidence/poc-data-001/"
        "control-plane-dry-run-local-evidence-stage0-v0.1.json",
        "DATA_CONTROL_PLANE_LOCAL_EVIDENCE",
    ),
    (
        "docs/evidence/poc-data-001/reviews/"
        "poc-data-control-plane-independent-advisory-closure-20260818.json",
        "DATA_CONTROL_PLANE_ADVISORY_CLOSURE",
    ),
    (
        "docs/stage0/DORA_MVP1_POC_DATA_CONTROL_PLANE.md",
        "DATA_CONTROL_PLANE_CONTRACT",
    ),
    (
        "docs/stage0/poc-data-control-plane-stage0-v0.1.schema.json",
        "DATA_CONTROL_PLANE_SCHEMA",
    ),
    (
        "docs/stage0/DORA_MVP1_DATASET_GOVERNANCE.md",
        "DATASET_GOVERNANCE_BOUNDARY",
    ),
    (
        "docs/evidence/poc-data-001/control-plane-synthetic-manifest-stage0-v0.1.json",
        "DATA_CONTROL_PLANE_SYNTHETIC_MANIFEST",
    ),
    (
        "tools/poc_data_control_plane/src/main/java/com/monumentogram/dora/stage0/data/"
        "controlplane/PocDataControlPlane.java",
        "DATA_CONTROL_PLANE_MAIN_SOURCE",
    ),
    (
        "tools/poc_data_control_plane/src/test/java/com/monumentogram/dora/stage0/data/"
        "controlplane/PocDataControlPlaneTest.java",
        "DATA_CONTROL_PLANE_TEST_SOURCE",
    ),
)


def _command(
    command_id: str,
    class_name: str,
    repeat: int,
    expected_stdout: str,
    args: tuple[str, ...] = (),
) -> dict[str, Any]:
    return {
        "id": command_id,
        "className": class_name,
        "args": args,
        "repeat": repeat,
        "expectedStdout": expected_stdout,
    }


PACKAGE_SPECS = (
    {
        "id": "VAD_P2",
        "pullRequest": 33,
        "evidenceFiles": (
            "docs/evidence/poc-vad-001/p2-host-oracle-local-evidence-stage0-v0.1.json",
        ),
        "supportFiles": (),
        "mainSources": (
            "tools/vad_p2_oracle/src/main/java/com/monumentogram/dora/stage0/vad/p2/"
            "VadBoundaryOracle.java",
        ),
        "testSources": (
            "tools/vad_p2_oracle/src/test/java/com/monumentogram/dora/stage0/vad/p2/"
            "VadBoundaryOracleTest.java",
        ),
        "commands": (
            _command(
                "tests",
                "com.monumentogram.dora.stage0.vad.p2.VadBoundaryOracleTest",
                2,
                "LOCAL_PASS vad-p2-frame-timing-host-oracle\n",
            ),
        ),
        "surfaceProfile": "NO_FILESYSTEM_NO_CONSOLE",
    },
    {
        "id": "VAD_P3",
        "pullRequest": 46,
        "evidenceFiles": (
            "docs/evidence/poc-vad-001/"
            "p3-deterministic-integrated-replay-local-evidence-stage0-v0.1.json",
        ),
        "supportFiles": (),
        "mainSources": (
            "tools/vad_p3_deterministic_replay/src/main/java/com/monumentogram/dora/stage0/"
            "vad/p3/VadDeterministicReplay.java",
        ),
        "testSources": (
            "tools/vad_p3_deterministic_replay/src/test/java/com/monumentogram/dora/stage0/"
            "vad/p3/VadDeterministicReplayTest.java",
        ),
        "commands": (
            _command(
                "tests",
                "com.monumentogram.dora.stage0.vad.p3.VadDeterministicReplayTest",
                2,
                "LOCAL_PASS vad-p3-deterministic-integrated-replay\n",
            ),
        ),
        "surfaceProfile": "NO_FILESYSTEM_NO_CONSOLE",
    },
    {
        "id": "VAD_P4",
        "pullRequest": 49,
        "evidenceFiles": (
            "docs/evidence/poc-vad-001/"
            "p4-synthetic-pcm-rotation-local-evidence-stage0-v0.1.json",
        ),
        "supportFiles": (),
        "mainSources": (
            "tools/vad_p3_deterministic_replay/src/main/java/com/monumentogram/dora/stage0/"
            "vad/p3/VadDeterministicReplay.java",
            "tools/vad_p4_pcm_rotation_oracle/src/main/java/com/monumentogram/dora/stage0/"
            "vad/p4/VadPcmRotationOracle.java",
        ),
        "testSources": (
            "tools/vad_p4_pcm_rotation_oracle/src/test/java/com/monumentogram/dora/stage0/"
            "vad/p4/VadPcmRotationOracleTest.java",
        ),
        "commands": (
            _command(
                "tests",
                "com.monumentogram.dora.stage0.vad.p4.VadPcmRotationOracleTest",
                2,
                "LOCAL_PASS vad-p4-synthetic-pcm-rotation\n",
            ),
        ),
        "surfaceProfile": "BOUNDED_FILESYSTEM_NO_CONSOLE",
    },
    {
        "id": "OFFLINE_I1",
        "pullRequest": 36,
        "evidenceFiles": (
            "docs/evidence/poc-offline-001/"
            "i1-host-oracle-implementation-evidence-stage0-v0.1.json",
        ),
        "supportFiles": (),
        "mainSources": (
            "tools/offline_i1_oracle/src/main/java/com/monumentogram/dora/stage0/offline/"
            "i1/OfflineI1Oracle.java",
        ),
        "testSources": (
            "tools/offline_i1_oracle/src/test/java/com/monumentogram/dora/stage0/offline/"
            "i1/OfflineI1OracleTest.java",
        ),
        "commands": (
            _command(
                "tests",
                "com.monumentogram.dora.stage0.offline.i1.OfflineI1OracleTest",
                2,
                "PASS offline-i1-host-oracle",
            ),
        ),
        "surfaceProfile": "NO_FILESYSTEM_NO_CONSOLE",
    },
    {
        "id": "OFFLINE_I2",
        "pullRequest": 37,
        "evidenceFiles": (
            "docs/evidence/poc-offline-001/"
            "i2-integrated-synthetic-harness-evidence-stage0-v0.1.json",
        ),
        "supportFiles": (),
        "mainSources": (
            "tools/offline_i1_oracle/src/main/java/com/monumentogram/dora/stage0/offline/"
            "i1/OfflineI1Oracle.java",
            "tools/offline_i2_integrated_harness/src/main/java/com/monumentogram/dora/stage0/"
            "offline/i2/OfflineI2IntegratedHarness.java",
        ),
        "testSources": (
            "tools/offline_i1_oracle/src/test/java/com/monumentogram/dora/stage0/offline/"
            "i1/OfflineI1OracleTest.java",
            "tools/offline_i2_integrated_harness/src/test/java/com/monumentogram/dora/stage0/"
            "offline/i2/OfflineI2IntegratedHarnessTest.java",
        ),
        "commands": (
            _command(
                "i1-regression",
                "com.monumentogram.dora.stage0.offline.i1.OfflineI1OracleTest",
                1,
                "PASS offline-i1-host-oracle",
            ),
            _command(
                "i2-tests",
                "com.monumentogram.dora.stage0.offline.i2.OfflineI2IntegratedHarnessTest",
                2,
                "PASS offline-i2-integrated-synthetic-harness",
            ),
        ),
        "surfaceProfile": "NO_FILESYSTEM_NO_CONSOLE",
    },
    {
        "id": "OFFLINE_I3",
        "pullRequest": 48,
        "evidenceFiles": (
            "docs/evidence/poc-offline-001/"
            "i3-static-call-ledger-local-evidence-stage0-v0.1.json",
        ),
        "supportFiles": (),
        "mainSources": (
            "tools/offline_i3_static_call_ledger/src/main/java/com/monumentogram/dora/stage0/"
            "offline/i3/OfflineI3StaticCallLedger.java",
        ),
        "testSources": (
            "tools/offline_i3_static_call_ledger/src/test/java/com/monumentogram/dora/stage0/"
            "offline/i3/OfflineI3StaticCallLedgerTest.java",
        ),
        "commands": (
            _command(
                "tests",
                "com.monumentogram.dora.stage0.offline.i3.OfflineI3StaticCallLedgerTest",
                2,
                "PASS offline-i3-static-call-ledger\n",
            ),
        ),
        "surfaceProfile": "NO_FILESYSTEM_NO_CONSOLE",
    },
    {
        "id": "DATA_MANIFEST",
        "pullRequest": 35,
        "evidenceFiles": (
            "docs/evidence/poc-data-001/"
            "synthetic-public-manifest-validator-local-evidence-stage0-v0.1.json",
        ),
        "supportFiles": (
            "docs/stage0/poc-data-synthetic-public-projection-stage0-v0.1.schema.json",
        ),
        "mainSources": (
            "tools/poc_data_manifest_validator/src/main/java/com/monumentogram/dora/stage0/"
            "data/manifest/SyntheticManifestValidator.java",
        ),
        "testSources": (
            "tools/poc_data_manifest_validator/src/test/java/com/monumentogram/dora/stage0/"
            "data/manifest/SyntheticManifestValidatorTest.java",
        ),
        "commands": (
            _command(
                "tests",
                "com.monumentogram.dora.stage0.data.manifest.SyntheticManifestValidatorTest",
                2,
                "LOCAL_PASS poc-data-synthetic-manifest-validator-tests\n",
            ),
        ),
        "surfaceProfile": "BOUNDED_FILESYSTEM_CLI",
    },
    {
        "id": "DATA_CONTROL_PLANE",
        "pullRequest": 44,
        "evidenceFiles": (
            "docs/evidence/poc-data-001/"
            "control-plane-dry-run-local-evidence-stage0-v0.1.json",
        ),
        "supportFiles": (
            "docs/evidence/poc-data-001/reviews/"
            "poc-data-control-plane-independent-advisory-closure-20260818.json",
            "docs/stage0/DORA_MVP1_POC_DATA_CONTROL_PLANE.md",
            "docs/stage0/poc-data-control-plane-stage0-v0.1.schema.json",
            "docs/stage0/DORA_MVP1_DATASET_GOVERNANCE.md",
            "docs/evidence/poc-data-001/control-plane-synthetic-manifest-stage0-v0.1.json",
        ),
        "mainSources": (
            "tools/poc_data_control_plane/src/main/java/com/monumentogram/dora/stage0/data/"
            "controlplane/PocDataControlPlane.java",
        ),
        "testSources": (
            "tools/poc_data_control_plane/src/test/java/com/monumentogram/dora/stage0/data/"
            "controlplane/PocDataControlPlaneTest.java",
        ),
        "commands": (
            _command(
                "tests",
                "com.monumentogram.dora.stage0.data.controlplane.PocDataControlPlaneTest",
                2,
                "LOCAL_PASS POC_DATA_CONTROL_PLANE_TESTS scenarios=15 assertions=180 "
                "readiness=NOT_READY overall=NOT_RUN collection=NOT_AUTHORIZED\n",
            ),
            _command(
                "validate",
                "com.monumentogram.dora.stage0.data.controlplane.PocDataControlPlane",
                2,
                "LOCAL_PASS POC_DATA_CONTROL_PLANE_SYNTHETIC_ONLY "
                "manifest_sha256=5ce259432d77c8876f6b7c5e8ab6981d4312c1fbc18cdf6c84a97023d450ec72 "
                "schema_sha256=b38ae362a5a804401878f56f139839dbd47009ec2fee59f1f05fdf08984b537e "
                "readiness=NOT_READY overall=NOT_RUN collection=NOT_AUTHORIZED\n",
                (
                    "validate",
                    "docs/evidence/poc-data-001/"
                    "control-plane-synthetic-manifest-stage0-v0.1.json",
                    "docs/stage0/poc-data-control-plane-stage0-v0.1.schema.json",
                ),
            ),
            _command(
                "dry-run",
                "com.monumentogram.dora.stage0.data.controlplane.PocDataControlPlane",
                2,
                "LOCAL_PASS POC_DATA_CONTROL_PLANE_SYNTHETIC_DRY_RUN "
                "manifest_sha256=5ce259432d77c8876f6b7c5e8ab6981d4312c1fbc18cdf6c84a97023d450ec72 "
                "schema_sha256=b38ae362a5a804401878f56f139839dbd47009ec2fee59f1f05fdf08984b537e "
                "scenarios=15 sentinel_deleted=true deletion_idempotent=true source_unchanged=true "
                "readiness=NOT_READY overall=NOT_RUN collection=NOT_AUTHORIZED\n",
                (
                    "dry-run",
                    "docs/evidence/poc-data-001/"
                    "control-plane-synthetic-manifest-stage0-v0.1.json",
                    "docs/stage0/poc-data-control-plane-stage0-v0.1.schema.json",
                ),
            ),
        ),
        "surfaceProfile": "BOUNDED_FILESYSTEM_CLI_TMPDIR_PROPERTY",
    },
)

TOP_LEVEL_KEYS = (
    "schemaVersion",
    "baselineCommit",
    "claimCeiling",
    "hashProfile",
    "stdoutProfile",
    "expectedCommandCount",
    "expectedExecutionCount",
    "expectedTrackedFiles",
    "pins",
    "packages",
)
PIN_KEYS = ("path", "role", "sha256")
PACKAGE_KEYS = (
    "id",
    "pullRequest",
    "evidenceFiles",
    "supportFiles",
    "mainSources",
    "testSources",
    "commands",
    "surfaceProfile",
    "expectedModules",
    "expectedClassMajor",
)
COMMAND_KEYS = ("id", "className", "args", "repeat", "expectedStdout")
HASH_RE = re.compile(r"[0-9a-f]{64}")
GIT_SHA1_RE = re.compile(r"[0-9a-f]{40}")
ROLE_RE = re.compile(r"[A-Z][A-Z0-9_]*")
CLASS_RE = re.compile(r"[a-z][A-Za-z0-9_]*(?:\.[A-Za-z_$][A-Za-z0-9_$]*)+")

SOURCE_FORBIDDEN_PATTERNS = (
    ("network", re.compile(r"\b(?:java|javax|sun)\.net\.")),
    (
        "system-input",
        re.compile(r"System\.(?:getenv|getProperty|currentTimeMillis|nanoTime)\s*\("),
    ),
    (
        "wall-clock",
        re.compile(
            r"\b(?:Instant|Clock|LocalDateTime|ZonedDateTime|OffsetDateTime)\.now\s*\("
        ),
    ),
    (
        "random-process-thread",
        re.compile(
            r"\b(?:Random|SecureRandom|Thread|ProcessBuilder|Executors|ForkJoinPool|"
            r"CompletableFuture)\b|Runtime\.getRuntime\s*\("
        ),
    ),
    (
        "reflection",
        re.compile(
            r"java\.lang\.reflect\.|Class\.forName\s*\(|"
            r"getDeclared(?:Field|Fields|Method|Methods|Constructor|Constructors)\s*\("
        ),
    ),
    ("android-gms", re.compile(r"\b(?:android\.|com\.google\.android\.gms\.)")),
    (
        "model-audio-runtime",
        re.compile(
            r"\b(?:onnxruntime|tensorflow|pytorch|tflite|AudioRecord|MediaRecorder)\b",
            re.IGNORECASE,
        ),
    ),
)
SOURCE_FILESYSTEM_RE = re.compile(
    r"\bjava\.io\.|\bjava\.nio\.file\.|\bRandomAccessFile\b|\bFileChannel\b"
)
SOURCE_CONSOLE_RE = re.compile(r"System\.(?:out|err)\b")

BYTECODE_FORBIDDEN_PATTERNS = (
    ("network", re.compile(r"(?:java|javax|sun)/net/")),
    (
        "system-input",
        re.compile(r"java/lang/System\.(?:getenv|getProperty|currentTimeMillis|nanoTime)"),
    ),
    (
        "wall-clock",
        re.compile(
            r"java/time/(?:Instant|Clock|LocalDateTime|ZonedDateTime|OffsetDateTime)\.now"
        ),
    ),
    (
        "random-process-thread",
        re.compile(
            r"java/util/Random|java/security/SecureRandom|java/lang/Thread|"
            r"java/lang/ProcessBuilder|java/lang/Runtime\.getRuntime|java/util/concurrent/"
        ),
    ),
    (
        "reflection",
        re.compile(
            r"java/lang/reflect/|java/lang/Class\.forName|"
            r"getDeclared(?:Field|Fields|Method|Methods|Constructor|Constructors)"
        ),
    ),
    ("android-gms", re.compile(r"(?:^|\W)(?:android/|com/google/android/gms/)")),
)
BYTECODE_FILESYSTEM_RE = re.compile(r"java/io/|java/nio/file/")
BYTECODE_CONSOLE_RE = re.compile(r"java/lang/System\.(?:out|err)")

PRIVACY_PATTERNS = (
    (
        "private-key",
        re.compile(r"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    ),
    (
        "credential-prefix",
        re.compile(
            r"\b(?:ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|"
            r"glpat-[A-Za-z0-9_-]{20,}|xox[baprs]-[A-Za-z0-9-]{10,}|"
            r"AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{30,})\b"
        ),
    ),
    (
        "assigned-secret",
        re.compile(
            r"(?i)\b(?:password|passwd|api[_-]?key|secret|token)\s*[:=]\s*"
            r"[\"'][^\"'\s]{8,}[\"']"
        ),
    ),
    (
        "email",
        re.compile(r"\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}\b"),
    ),
    (
        "absolute-local-path",
        re.compile(r"(?:\b[A-Za-z]:[\\/](?:Users|Documents|home)[\\/]|/(?:Users|home|root)/)"),
    ),
    (
        "private-ipv4",
        re.compile(
            r"(?<![0-9])(?:10\.(?:[0-9]{1,3}\.){2}[0-9]{1,3}|"
            r"192\.168\.(?:[0-9]{1,3}\.)[0-9]{1,3}|"
            r"172\.(?:1[6-9]|2[0-9]|3[01])\.(?:[0-9]{1,3}\.)[0-9]{1,3})(?![0-9])"
        ),
    ),
)
URL_RE = re.compile(r"https?://[^\s\"'<>]+")
ALLOWED_URLS = {"https://json-schema.org/draft/2020-12/schema"}


class RunnerError(RuntimeError):
    """Expected fail-closed validation error."""


def _duplicate_safe_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise RunnerError(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def _expect_keys(value: Mapping[str, Any], expected: tuple[str, ...], context: str) -> None:
    actual = tuple(value.keys())
    if actual != expected:
        raise RunnerError(f"{context}: key order or membership mismatch")


def _expect_exact(value: Any, expected: Any, context: str) -> None:
    if value != expected or type(value) is not type(expected):
        raise RunnerError(f"{context}: exact value mismatch")


def _expect_string(value: Any, context: str) -> str:
    if type(value) is not str or not value:
        raise RunnerError(f"{context}: expected non-empty string")
    return value


def _validate_relative_path(value: Any, context: str) -> str:
    path = _expect_string(value, context)
    if "\\" in path:
        raise RunnerError(f"{context}: backslash is forbidden")
    pure = PurePosixPath(path)
    if pure.is_absolute() or ".." in pure.parts or "." in pure.parts or str(pure) != path:
        raise RunnerError(f"{context}: unsafe repository-relative path")
    return path


def load_inventory(path: Path) -> dict[str, Any]:
    try:
        raw = path.read_bytes()
    except OSError as error:
        raise RunnerError("inventory cannot be read") from error
    if raw.startswith(b"\xef\xbb\xbf") or b"\x00" in raw:
        raise RunnerError("inventory encoding boundary failed")
    try:
        text = raw.decode("utf-8", "strict")
    except UnicodeDecodeError as error:
        raise RunnerError("inventory is not strict UTF-8") from error
    if "\r" in text or not text.endswith("\n") or text.endswith("\n\n"):
        raise RunnerError("inventory must use LF with exactly one final LF")
    try:
        value = json.loads(text, object_pairs_hook=_duplicate_safe_object)
    except (json.JSONDecodeError, RunnerError) as error:
        raise RunnerError("inventory JSON is invalid or non-unique") from error
    if type(value) is not dict:
        raise RunnerError("inventory root must be an object")
    return value


def validate_inventory(inventory: Mapping[str, Any]) -> None:
    _expect_keys(inventory, TOP_LEVEL_KEYS, "inventory")
    _expect_exact(inventory["schemaVersion"], SCHEMA_VERSION, "schemaVersion")
    _expect_exact(inventory["baselineCommit"], BASELINE_COMMIT, "baselineCommit")
    _expect_exact(inventory["claimCeiling"], CLAIM_CEILING, "claimCeiling")
    _expect_exact(inventory["hashProfile"], HASH_PROFILE, "hashProfile")
    _expect_exact(inventory["stdoutProfile"], STDOUT_PROFILE, "stdoutProfile")
    _expect_exact(
        inventory["expectedCommandCount"], EXPECTED_COMMAND_COUNT, "expectedCommandCount"
    )
    _expect_exact(
        inventory["expectedExecutionCount"],
        EXPECTED_EXECUTION_COUNT,
        "expectedExecutionCount",
    )
    if tuple(inventory["expectedTrackedFiles"]) != EXPECTED_TRACKED_FILES:
        raise RunnerError("expectedTrackedFiles mismatch")

    pins = inventory["pins"]
    if type(pins) is not list or len(pins) != len(PIN_SPECS):
        raise RunnerError("pins: exact length mismatch")
    for index, (pin, expected_spec) in enumerate(zip(pins, PIN_SPECS, strict=True)):
        if type(pin) is not dict:
            raise RunnerError(f"pins[{index}]: expected object")
        _expect_keys(pin, PIN_KEYS, f"pins[{index}]")
        path = _validate_relative_path(pin["path"], f"pins[{index}].path")
        _expect_exact(path, expected_spec[0], f"pins[{index}].path")
        role = _expect_string(pin["role"], f"pins[{index}].role")
        _expect_exact(role, expected_spec[1], f"pins[{index}].role")
        if ROLE_RE.fullmatch(role) is None:
            raise RunnerError(f"pins[{index}].role: invalid role")
        digest = _expect_string(pin["sha256"], f"pins[{index}].sha256")
        if HASH_RE.fullmatch(digest) is None:
            raise RunnerError(f"pins[{index}].sha256: invalid digest")

    packages = inventory["packages"]
    if type(packages) is not list or len(packages) != len(PACKAGE_SPECS):
        raise RunnerError("packages: exact length mismatch")
    command_count = 0
    execution_count = 0
    referenced: list[str] = []
    for index, (package, spec) in enumerate(zip(packages, PACKAGE_SPECS, strict=True)):
        context = f"packages[{index}]"
        if type(package) is not dict:
            raise RunnerError(f"{context}: expected object")
        _expect_keys(package, PACKAGE_KEYS, context)
        _expect_exact(package["id"], spec["id"], f"{context}.id")
        _expect_exact(package["pullRequest"], spec["pullRequest"], f"{context}.pullRequest")
        for field in ("evidenceFiles", "supportFiles", "mainSources", "testSources"):
            values = package[field]
            if type(values) is not list:
                raise RunnerError(f"{context}.{field}: expected array")
            normalized = tuple(
                _validate_relative_path(value, f"{context}.{field}") for value in values
            )
            _expect_exact(normalized, spec[field], f"{context}.{field}")
            referenced.extend(normalized)
        commands = package["commands"]
        if type(commands) is not list or len(commands) != len(spec["commands"]):
            raise RunnerError(f"{context}.commands: exact length mismatch")
        for command_index, (command, expected) in enumerate(
            zip(commands, spec["commands"], strict=True)
        ):
            command_context = f"{context}.commands[{command_index}]"
            if type(command) is not dict:
                raise RunnerError(f"{command_context}: expected object")
            _expect_keys(command, COMMAND_KEYS, command_context)
            for field in ("id", "className", "repeat", "expectedStdout"):
                _expect_exact(command[field], expected[field], f"{command_context}.{field}")
            if CLASS_RE.fullmatch(command["className"]) is None:
                raise RunnerError(f"{command_context}.className: invalid class")
            args = command["args"]
            if type(args) is not list or tuple(args) != expected["args"]:
                raise RunnerError(f"{command_context}.args: exact mismatch")
            for arg in args:
                if type(arg) is not str or "\x00" in arg or "\r" in arg or "\n" in arg:
                    raise RunnerError(f"{command_context}.args: unsafe argument")
            stdout = command["expectedStdout"]
            if (
                not stdout
                or "\r" in stdout
                or stdout.count("\n") > 1
                or ("\n" in stdout and not stdout.endswith("\n"))
            ):
                raise RunnerError(f"{command_context}.expectedStdout: line profile mismatch")
            try:
                stdout.encode("ascii", "strict")
            except UnicodeEncodeError as error:
                raise RunnerError(f"{command_context}.expectedStdout: must be ASCII") from error
            command_count += 1
            execution_count += command["repeat"]
        _expect_exact(
            package["surfaceProfile"], spec["surfaceProfile"], f"{context}.surfaceProfile"
        )
        if package["expectedModules"] != ["java.base"]:
            raise RunnerError(f"{context}.expectedModules: must be java.base only")
        _expect_exact(package["expectedClassMajor"], 61, f"{context}.expectedClassMajor")

    _expect_exact(command_count, EXPECTED_COMMAND_COUNT, "computed command count")
    _expect_exact(execution_count, EXPECTED_EXECUTION_COUNT, "computed execution count")
    expected_references = tuple(path for path, _role in PIN_SPECS[1:])
    if set(referenced) != set(expected_references):
        raise RunnerError("package file references do not cover the exact pin inventory")


def _run_process(
    argv: Sequence[str],
    *,
    cwd: Path,
    env: Mapping[str, str] | None,
    timeout_seconds: int,
    label: str,
) -> subprocess.CompletedProcess[bytes]:
    try:
        return subprocess.run(
            list(argv),
            cwd=cwd,
            env=None if env is None else dict(env),
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            check=False,
            timeout=timeout_seconds,
        )
    except (OSError, subprocess.TimeoutExpired) as error:
        raise RunnerError(f"{label}: process launch or timeout failure") from error


def discover_repository() -> Path:
    result = _run_process(
        ("git", "rev-parse", "--show-toplevel"),
        cwd=Path.cwd(),
        env=None,
        timeout_seconds=30,
        label="git root",
    )
    if result.returncode != 0 or result.stderr:
        raise RunnerError("cannot resolve repository root")
    try:
        root = Path(result.stdout.decode("utf-8", "strict").strip()).resolve(strict=True)
    except (UnicodeDecodeError, OSError) as error:
        raise RunnerError("repository root is invalid") from error
    return root


def verify_baseline(repository: Path) -> None:
    head = _run_process(
        ("git", "rev-parse", "HEAD"),
        cwd=repository,
        env=None,
        timeout_seconds=30,
        label="git head",
    )
    if (
        head.returncode != 0
        or head.stderr
        or GIT_SHA1_RE.fullmatch(head.stdout.decode().strip()) is None
    ):
        raise RunnerError("HEAD cannot be resolved")
    ancestry = _run_process(
        ("git", "merge-base", "--is-ancestor", BASELINE_COMMIT, "HEAD"),
        cwd=repository,
        env=None,
        timeout_seconds=30,
        label="baseline ancestry",
    )
    if ancestry.returncode != 0 or ancestry.stdout or ancestry.stderr:
        raise RunnerError("required current-main baseline is not an ancestor of HEAD")


def _canonical_text(path: Path, relative_path: str) -> tuple[bytes, str]:
    try:
        raw = path.read_bytes()
    except OSError as error:
        raise RunnerError(f"pin cannot be read: {relative_path}") from error
    if raw.startswith(b"\xef\xbb\xbf"):
        raise RunnerError(f"pin contains UTF-8 BOM: {relative_path}")
    if b"\x00" in raw:
        raise RunnerError(f"pin contains NUL: {relative_path}")
    try:
        text = raw.decode("utf-8", "strict")
    except UnicodeDecodeError as error:
        raise RunnerError(f"pin is not strict UTF-8: {relative_path}") from error
    without_crlf = text.replace("\r\n", "")
    if "\r" in without_crlf:
        raise RunnerError(f"pin contains lone CR: {relative_path}")
    if "\r\n" in text and "\n" in without_crlf:
        raise RunnerError(f"pin mixes LF and CRLF: {relative_path}")
    canonical_text = text.replace("\r\n", "\n")
    if not canonical_text.endswith("\n") or canonical_text.endswith("\n\n"):
        raise RunnerError(f"pin must have exactly one final LF: {relative_path}")
    return canonical_text.encode("utf-8"), canonical_text


def _scan_privacy(text: str, relative_path: str) -> None:
    for label, pattern in PRIVACY_PATTERNS:
        if pattern.search(text) is not None:
            raise RunnerError(f"privacy scan {label} failed: {relative_path}")
    for url in URL_RE.findall(text):
        if url not in ALLOWED_URLS:
            raise RunnerError(f"unexpected public URL: {relative_path}")


def verify_pins(
    inventory: Mapping[str, Any], repository: Path, *, scan_privacy: bool = True
) -> dict[str, str]:
    tracked_result = _run_process(
        ("git", "ls-files", "-z"),
        cwd=repository,
        env=None,
        timeout_seconds=30,
        label="tracked files",
    )
    if tracked_result.returncode != 0 or tracked_result.stderr:
        raise RunnerError("tracked file inventory cannot be read")
    try:
        tracked = set(tracked_result.stdout.decode("utf-8", "strict").split("\x00"))
    except UnicodeDecodeError as error:
        raise RunnerError("tracked path encoding is invalid") from error
    texts: dict[str, str] = {}
    for pin in inventory["pins"]:
        relative_path = pin["path"]
        if relative_path not in tracked:
            raise RunnerError(f"pin is not tracked: {relative_path}")
        candidate = repository.joinpath(*PurePosixPath(relative_path).parts)
        try:
            resolved = candidate.resolve(strict=True)
            resolved.relative_to(repository)
        except (OSError, ValueError) as error:
            raise RunnerError(f"pin escapes repository: {relative_path}") from error
        if candidate.is_symlink() or not resolved.is_file():
            raise RunnerError(f"pin is not a regular non-symlink file: {relative_path}")
        canonical, text = _canonical_text(resolved, relative_path)
        actual_digest = hashlib.sha256(canonical).hexdigest()
        if actual_digest != pin["sha256"]:
            raise RunnerError(f"pin digest mismatch: {relative_path}")
        if scan_privacy:
            _scan_privacy(text, relative_path)
        texts[relative_path] = text
    return texts


def _repository_status(repository: Path) -> bytes:
    result = _run_process(
        ("git", "status", "--porcelain=v1", "--untracked-files=all", "-z"),
        cwd=repository,
        env=None,
        timeout_seconds=30,
        label="repository status",
    )
    if result.returncode != 0 or result.stderr:
        raise RunnerError("repository status cannot be read")
    return result.stdout


def _generated_repository_artifacts(repository: Path) -> tuple[str, ...]:
    found: list[str] = []
    for path in repository.rglob("*"):
        if not path.is_file():
            continue
        name = path.name
        if path.suffix.lower() in {".class", ".pcm"} or name == "dora-poc-data-control-plane.synthetic":
            found.append(path.relative_to(repository).as_posix())
    return tuple(sorted(found))


def resolve_toolchain(repository: Path) -> dict[str, str]:
    tools: dict[str, str] = {}
    for name in ("java", "javac", "jdeps", "javap"):
        located = shutil.which(name)
        if located is None:
            raise RunnerError(f"required JDK tool is missing: {name}")
        tools[name] = str(Path(located).resolve(strict=True))
    parents = {str(Path(path).parent).casefold() for path in tools.values()}
    if len(parents) != 1:
        raise RunnerError("JDK tools do not resolve from one bin directory")

    clean_env = _clean_environment(None)
    version_checks = (
        ("javac", (tools["javac"], "-version"), re.compile(r"javac 17(?:\.\d+)*")),
        ("jdeps", (tools["jdeps"], "--version"), re.compile(r"17(?:\.\d+)*")),
        ("javap", (tools["javap"], "-version"), re.compile(r"17(?:\.\d+)*")),
    )
    for label, argv, pattern in version_checks:
        result = _run_process(
            argv,
            cwd=repository,
            env=clean_env,
            timeout_seconds=30,
            label=f"{label} version",
        )
        combined = result.stdout + result.stderr
        try:
            value = combined.decode("utf-8", "strict").strip()
        except UnicodeDecodeError as error:
            raise RunnerError(f"{label} version is not UTF-8") from error
        if result.returncode != 0 or pattern.fullmatch(value) is None:
            raise RunnerError(f"{label} is not strict JDK 17")

    java_version = _run_process(
        (tools["java"], "-version"),
        cwd=repository,
        env=clean_env,
        timeout_seconds=30,
        label="java version",
    )
    try:
        java_lines = (java_version.stdout + java_version.stderr).decode("utf-8", "strict").splitlines()
    except UnicodeDecodeError as error:
        raise RunnerError("java version is not UTF-8") from error
    if (
        java_version.returncode != 0
        or not java_lines
        or re.search(r'version "17(?:\.\d+)*"', java_lines[0]) is None
    ):
        raise RunnerError("java runtime is not strict JDK 17")
    return tools


def _clean_environment(java_temp: Path | None) -> dict[str, str]:
    env = dict(os.environ)
    for key in ("CLASSPATH", "JAVA_TOOL_OPTIONS", "JDK_JAVA_OPTIONS", "_JAVA_OPTIONS"):
        env.pop(key, None)
    env["TZ"] = "UTC"
    if os.name != "nt":
        env["LANG"] = "C.UTF-8"
        env["LC_ALL"] = "C.UTF-8"
    if java_temp is not None:
        temp_value = str(java_temp)
        env["TEMP"] = temp_value
        env["TMP"] = temp_value
        env["TMPDIR"] = temp_value
    return env


def _scan_main_sources(package: Mapping[str, Any], texts: Mapping[str, str]) -> None:
    for relative_path in package["mainSources"]:
        text = texts[relative_path]
        scan_text = text
        if package["surfaceProfile"] == "BOUNDED_FILESYSTEM_CLI_TMPDIR_PROPERTY":
            allowed_property = 'System.getProperty("java.io.tmpdir")'
            if text.count(allowed_property) != 1:
                raise RunnerError(
                    f"{package['id']}: exact java.io.tmpdir property surface mismatch"
                )
            scan_text = text.replace(allowed_property, "ALLOWED_JAVA_TMPDIR_PROPERTY")
        for label, pattern in SOURCE_FORBIDDEN_PATTERNS:
            if pattern.search(scan_text) is not None:
                raise RunnerError(f"{package['id']}: source surface {label} failed")
        if package["surfaceProfile"] == "NO_FILESYSTEM_NO_CONSOLE":
            if SOURCE_FILESYSTEM_RE.search(text) is not None:
                raise RunnerError(f"{package['id']}: undeclared filesystem source surface")
        if not package["surfaceProfile"].startswith("BOUNDED_FILESYSTEM_CLI"):
            if SOURCE_CONSOLE_RE.search(text) is not None:
                raise RunnerError(f"{package['id']}: undeclared main console source surface")


def _compile_package(
    package: Mapping[str, Any],
    repository: Path,
    package_root: Path,
    tools: Mapping[str, str],
    env: Mapping[str, str],
) -> tuple[Path, Path]:
    main_classes = package_root / "main-classes"
    test_classes = package_root / "test-classes"
    main_classes.mkdir()
    test_classes.mkdir()
    main_sources = [str(repository.joinpath(*PurePosixPath(path).parts)) for path in package["mainSources"]]
    test_sources = [str(repository.joinpath(*PurePosixPath(path).parts)) for path in package["testSources"]]
    main_compile = _run_process(
        (
            tools["javac"],
            "--release",
            "17",
            "-encoding",
            "UTF-8",
            "-Xlint:all",
            "-Werror",
            "-d",
            str(main_classes),
            *main_sources,
        ),
        cwd=repository,
        env=env,
        timeout_seconds=300,
        label=f"{package['id']} main compile",
    )
    if main_compile.returncode != 0 or main_compile.stdout or main_compile.stderr:
        raise RunnerError(f"{package['id']}: strict main javac failed or emitted output")
    test_compile = _run_process(
        (
            tools["javac"],
            "--release",
            "17",
            "-encoding",
            "UTF-8",
            "-Xlint:all",
            "-Werror",
            "-classpath",
            str(main_classes),
            "-d",
            str(test_classes),
            *test_sources,
        ),
        cwd=repository,
        env=env,
        timeout_seconds=300,
        label=f"{package['id']} test compile",
    )
    if test_compile.returncode != 0 or test_compile.stdout or test_compile.stderr:
        raise RunnerError(f"{package['id']}: strict test javac failed or emitted output")
    return main_classes, test_classes


def _verify_jdeps(
    package: Mapping[str, Any],
    repository: Path,
    main_classes: Path,
    test_classes: Path,
    tools: Mapping[str, str],
    env: Mapping[str, str],
) -> None:
    invocations = (
        (
            "main",
            (
                tools["jdeps"],
                "--multi-release",
                "17",
                "--recursive",
                "--print-module-deps",
                str(main_classes),
            ),
        ),
        (
            "test",
            (
                tools["jdeps"],
                "--multi-release",
                "17",
                "--recursive",
                "--print-module-deps",
                "--class-path",
                str(main_classes),
                str(test_classes),
            ),
        ),
    )
    for label, argv in invocations:
        result = _run_process(
            argv,
            cwd=repository,
            env=env,
            timeout_seconds=120,
            label=f"{package['id']} {label} jdeps",
        )
        try:
            lines = result.stdout.decode("utf-8", "strict").splitlines()
        except UnicodeDecodeError as error:
            raise RunnerError(f"{package['id']}: jdeps output is not UTF-8") from error
        if result.returncode != 0 or result.stderr or lines != ["java.base"]:
            raise RunnerError(f"{package['id']}: {label} jdeps is not exactly java.base")


def _verify_class_major(package: Mapping[str, Any], roots: Iterable[Path]) -> None:
    count = 0
    for root in roots:
        for class_file in sorted(root.rglob("*.class")):
            count += 1
            try:
                header = class_file.read_bytes()[:8]
            except OSError as error:
                raise RunnerError(f"{package['id']}: class header cannot be read") from error
            if len(header) != 8 or header[:4] != b"\xca\xfe\xba\xbe":
                raise RunnerError(f"{package['id']}: invalid class header")
            major = struct.unpack(">H", header[6:8])[0]
            if major != package["expectedClassMajor"]:
                raise RunnerError(f"{package['id']}: class major is not 61")
    if count == 0:
        raise RunnerError(f"{package['id']}: no class files emitted")


def _scan_compiled_main(
    package: Mapping[str, Any],
    repository: Path,
    main_classes: Path,
    tools: Mapping[str, str],
    env: Mapping[str, str],
) -> None:
    class_files = sorted(main_classes.rglob("*.class"))
    if not class_files:
        raise RunnerError(f"{package['id']}: compiled main is empty")
    for class_file in class_files:
        relative = class_file.relative_to(main_classes).as_posix()
        class_name = relative.removesuffix(".class").replace("/", ".")
        result = _run_process(
            (tools["javap"], "-c", "-p", "-classpath", str(main_classes), class_name),
            cwd=repository,
            env=env,
            timeout_seconds=60,
            label=f"{package['id']} javap",
        )
        try:
            output = result.stdout.decode("utf-8", "strict")
        except UnicodeDecodeError as error:
            raise RunnerError(f"{package['id']}: javap output is not UTF-8") from error
        if result.returncode != 0 or result.stderr:
            raise RunnerError(f"{package['id']}: javap failed or emitted stderr")
        for label, pattern in BYTECODE_FORBIDDEN_PATTERNS:
            if (
                label == "system-input"
                and package["surfaceProfile"] == "BOUNDED_FILESYSTEM_CLI_TMPDIR_PROPERTY"
            ):
                continue
            if pattern.search(output) is not None:
                raise RunnerError(f"{package['id']}: bytecode surface {label} failed")
        if package["surfaceProfile"] == "NO_FILESYSTEM_NO_CONSOLE":
            if BYTECODE_FILESYSTEM_RE.search(output) is not None:
                raise RunnerError(f"{package['id']}: undeclared filesystem bytecode surface")
        if not package["surfaceProfile"].startswith("BOUNDED_FILESYSTEM_CLI"):
            if BYTECODE_CONSOLE_RE.search(output) is not None:
                raise RunnerError(f"{package['id']}: undeclared main console bytecode surface")


def _expected_host_stdout(logical_stdout: str) -> bytes:
    return logical_stdout.replace("\n", os.linesep).encode("utf-8", "strict")


def _validate_command_args(
    command: Mapping[str, Any], repository: Path, pin_paths: set[str]
) -> list[str]:
    resolved_args: list[str] = []
    for arg in command["args"]:
        if "/" not in arg:
            resolved_args.append(arg)
            continue
        if arg not in pin_paths:
            raise RunnerError(f"{command['id']}: path argument is not pinned")
        candidate = repository.joinpath(*PurePosixPath(arg).parts)
        try:
            resolved = candidate.resolve(strict=True)
            resolved.relative_to(repository)
        except (OSError, ValueError) as error:
            raise RunnerError(f"{command['id']}: path argument escapes repository") from error
        resolved_args.append(str(resolved))
    return resolved_args


def _run_commands(
    package: Mapping[str, Any],
    repository: Path,
    main_classes: Path,
    test_classes: Path,
    java_temp: Path,
    tools: Mapping[str, str],
    env: Mapping[str, str],
    pin_paths: set[str],
) -> None:
    classpath = os.pathsep.join((str(main_classes), str(test_classes)))
    vm_args = (
        "-Duser.language=en",
        "-Duser.country=US",
        "-Duser.timezone=UTC",
        f"-Djava.io.tmpdir={java_temp}",
        "-XX:-UsePerfData",
        "-ea",
        "-Xverify:all",
    )
    for command in package["commands"]:
        expected = _expected_host_stdout(command["expectedStdout"])
        args = _validate_command_args(command, repository, pin_paths)
        observed: bytes | None = None
        for ordinal in range(1, command["repeat"] + 1):
            result = _run_process(
                (
                    tools["java"],
                    *vm_args,
                    "-cp",
                    classpath,
                    command["className"],
                    *args,
                ),
                cwd=repository,
                env=env,
                timeout_seconds=300,
                label=f"{package['id']} {command['id']} run {ordinal}",
            )
            if result.returncode != 0:
                raise RunnerError(f"{package['id']} {command['id']}: nonzero exit")
            if result.stderr:
                raise RunnerError(f"{package['id']} {command['id']}: stderr is not empty")
            if result.stdout != expected:
                raise RunnerError(f"{package['id']} {command['id']}: exact stdout mismatch")
            if observed is not None and result.stdout != observed:
                raise RunnerError(f"{package['id']} {command['id']}: repeat output drift")
            observed = result.stdout
            try:
                retained = tuple(java_temp.iterdir())
            except OSError as error:
                raise RunnerError(f"{package['id']}: Java temp cannot be inspected") from error
            if retained:
                raise RunnerError(f"{package['id']} {command['id']}: temporary files retained")


def run_regression(
    inventory: Mapping[str, Any],
    repository: Path,
    texts: Mapping[str, str],
) -> None:
    before_status = _repository_status(repository)
    before_artifacts = _generated_repository_artifacts(repository)
    if before_artifacts:
        raise RunnerError("repository already contains forbidden generated artifacts")
    tools = resolve_toolchain(repository)
    temp_parent_value = os.environ.get("RUNNER_TEMP")
    temp_parent = None
    if temp_parent_value:
        try:
            temp_parent = Path(temp_parent_value).resolve(strict=True)
        except OSError as error:
            raise RunnerError("RUNNER_TEMP is invalid") from error
        if not temp_parent.is_dir():
            raise RunnerError("RUNNER_TEMP is not a directory")
    try:
        temp_root = Path(
            tempfile.mkdtemp(prefix="dora-vad-offline-data-host-", dir=temp_parent)
        ).resolve(strict=True)
    except OSError as error:
        raise RunnerError("validator temporary root cannot be created") from error
    try:
        try:
            temp_root.relative_to(repository)
        except ValueError:
            pass
        else:
            raise RunnerError("validator temporary root must be outside the repository")

        pin_paths = {pin["path"] for pin in inventory["pins"]}
        for index, package in enumerate(inventory["packages"]):
            _scan_main_sources(package, texts)
            package_root = temp_root / f"package-{index:02d}-{package['id'].lower()}"
            package_root.mkdir()
            tool_temp = package_root / "tool-temp"
            tool_temp.mkdir()
            java_temp = package_root / "java-temp"
            java_temp.mkdir()
            tool_env = _clean_environment(tool_temp)
            java_env = _clean_environment(java_temp)
            main_classes, test_classes = _compile_package(
                package, repository, package_root, tools, tool_env
            )
            _verify_jdeps(
                package, repository, main_classes, test_classes, tools, tool_env
            )
            _verify_class_major(package, (main_classes, test_classes))
            _scan_compiled_main(package, repository, main_classes, tools, tool_env)
            _run_commands(
                package,
                repository,
                main_classes,
                test_classes,
                java_temp,
                tools,
                java_env,
                pin_paths,
            )
            shutil.rmtree(package_root)
            if package_root.exists():
                raise RunnerError(f"{package['id']}: package temp cleanup failed")
    finally:
        cleanup_error: OSError | None = None
        try:
            shutil.rmtree(temp_root)
        except OSError as error:
            cleanup_error = error
        if temp_root.exists() or cleanup_error is not None:
            raise RunnerError("validator top-level temporary cleanup failed") from cleanup_error

    after_status = _repository_status(repository)
    if after_status != before_status:
        raise RunnerError("repository status changed during validation")
    if _generated_repository_artifacts(repository):
        raise RunnerError("generated artifact appeared in repository")


def _expect_self_test_rejection(
    name: str,
    inventory: Mapping[str, Any],
    mutation: Callable[[dict[str, Any]], None],
    repository: Path,
    *,
    verify_files: bool = False,
) -> None:
    candidate = copy.deepcopy(inventory)
    mutation(candidate)
    try:
        validate_inventory(candidate)
        if verify_files:
            verify_pins(candidate, repository, scan_privacy=False)
    except RunnerError:
        return
    raise RunnerError(f"self-test did not reject mutation: {name}")


def _expect_runner_error(name: str, operation: Callable[[], None]) -> None:
    try:
        operation()
    except RunnerError:
        return
    raise RunnerError(f"self-test did not reject boundary: {name}")


def run_self_test(inventory: Mapping[str, Any], repository: Path) -> None:
    cases: tuple[tuple[str, Callable[[dict[str, Any]], None], bool], ...] = (
        ("extra-key", lambda value: value.__setitem__("unexpected", True), False),
        (
            "baseline",
            lambda value: value.__setitem__("baselineCommit", "0" * 40),
            False,
        ),
        (
            "claim",
            lambda value: value.__setitem__("claimCeiling", "POC_PASS"),
            False,
        ),
        (
            "package-order",
            lambda value: value["packages"].reverse(),
            False,
        ),
        (
            "repeat",
            lambda value: value["packages"][0]["commands"][0].__setitem__("repeat", 1),
            False,
        ),
        (
            "stdout",
            lambda value: value["packages"][0]["commands"][0].__setitem__(
                "expectedStdout", "LOCAL_PASS weakened\n"
            ),
            False,
        ),
        (
            "surface",
            lambda value: value["packages"][0].__setitem__(
                "surfaceProfile", "BOUNDED_FILESYSTEM_CLI"
            ),
            False,
        ),
        (
            "tracked-files",
            lambda value: value["expectedTrackedFiles"].pop(),
            False,
        ),
        (
            "path-traversal",
            lambda value: value["pins"][0].__setitem__("path", "../scope.md"),
            False,
        ),
        (
            "digest",
            lambda value: value["pins"][0].__setitem__("sha256", "0" * 64),
            True,
        ),
    )
    for name, mutation, verify_files in cases:
        _expect_self_test_rejection(
            name, inventory, mutation, repository, verify_files=verify_files
        )
    boundary_cases: tuple[tuple[str, Callable[[], None]], ...] = (
        (
            "privacy-email",
            lambda: _scan_privacy("owner" + "@" + "example.com", "self-test"),
        ),
        (
            "privacy-url",
            lambda: _scan_privacy("https" + "://private.example/path", "self-test"),
        ),
        (
            "source-network",
            lambda: _scan_main_sources(
                {
                    "id": "SELF_TEST",
                    "mainSources": ["self-test.java"],
                    "surfaceProfile": "NO_FILESYSTEM_NO_CONSOLE",
                },
                {"self-test.java": "import java." + "net.Socket;\n"},
            ),
        ),
        (
            "source-filesystem",
            lambda: _scan_main_sources(
                {
                    "id": "SELF_TEST",
                    "mainSources": ["self-test.java"],
                    "surfaceProfile": "NO_FILESYSTEM_NO_CONSOLE",
                },
                {"self-test.java": "import java.nio." + "file.Path;\n"},
            ),
        ),
    )
    for name, operation in boundary_cases:
        _expect_runner_error(name, operation)
    print(
        "SELF_TEST_OK stage0-vad-offline-data-host-regression "
        f"cases={len(cases) + len(boundary_cases)}",
        flush=True,
    )


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Revalidate the exact Stage 0 VAD/Offline/Data host package inventory."
    )
    parser.add_argument("--inventory", default=INVENTORY_PATH)
    parser.add_argument("--self-test", action="store_true")
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(sys.argv[1:] if argv is None else argv)
    try:
        repository = discover_repository()
        inventory_candidate = Path(args.inventory)
        inventory_path = (
            inventory_candidate
            if inventory_candidate.is_absolute()
            else repository.joinpath(*PurePosixPath(args.inventory).parts)
        )
        inventory_path = inventory_path.resolve(strict=True)
        try:
            inventory_relative = inventory_path.relative_to(repository).as_posix()
        except ValueError as error:
            raise RunnerError("inventory path escapes repository") from error
        if inventory_relative != INVENTORY_PATH:
            raise RunnerError("only the closed v0.1 inventory path is accepted")
        inventory = load_inventory(inventory_path)
        validate_inventory(inventory)
        verify_baseline(repository)
        texts = verify_pins(inventory, repository)
        if args.self_test:
            run_self_test(inventory, repository)
            return 0
        run_regression(inventory, repository, texts)
        print(
            f"{CLAIM_CEILING} packages={len(PACKAGE_SPECS)} "
            f"commands={EXPECTED_COMMAND_COUNT} executions={EXPECTED_EXECUTION_COUNT} "
            "temp_cleanup=true",
            flush=True,
        )
        return 0
    except (OSError, RunnerError) as error:
        print(f"VALIDATION_FAILED {error}", file=sys.stderr, flush=True)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
