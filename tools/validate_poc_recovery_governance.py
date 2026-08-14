#!/usr/bin/env python3
"""Fail-closed validation for v0.6 governance plus authorized pure Recovery I1."""

from __future__ import annotations

import copy
import fnmatch
import hashlib
import json
import os
import re
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from collections.abc import Mapping
from dataclasses import dataclass, replace
from pathlib import Path
from typing import Any, Callable


ROOT = Path(__file__).resolve().parents[1]
GATE_PATH = "docs/stage0/poc-recovery-gate-set-stage0-v0.6.json"
PROTOCOL_PATH = "docs/stage0/poc-recovery-protocol-stage0-v0.6.json"
GATE_ID = "poc-recovery-stage0-v0.6"
PROTOCOL_ID = "poc-recovery-protocol-stage0-v0.6"
REVIEWED_V05_HEAD = "eca48ba62acd79007884710395cc40ea21a02611"
REVIEWED_V06_HEAD = "b5371f523e4471aca48a63a82b9ee4e1f9a7e0fd"
MERGED_V06_MAIN = "f14c6f37d7acb37590be875f176653c100f0ae20"
MERGED_V06_TREE = "1fd03fd489836c65f7ee043298f8f6d32df00c55"
FORMAL_REVIEW_BASE_MAIN = "5c97f09f3165a90afa5300b30499e0dcb36168f2"
POST_MERGE_EVIDENCE_PATH = "docs/evidence/poc-recovery-001/post-merge-advisory-rereview-2026-08-13.json"
POST_MERGE_EVIDENCE_SHA256 = "f9165ec41d6bd5a5f6286a8f95223802a7bc91272103da8da05937e4fa4b7d91"
ADVISORY_DOSSIER_PATH = "docs/evidence/poc-recovery-001/advisory-engineering-security-dossier-2026-08-13.md"
ADVISORY_DOSSIER_SHA256 = "619f33cbd795637853cfa51cc0ff76c1c5e642a1a5d13bb0582285844dc7462a"
ADVISORY_DOSSIER_SOURCE_SHA256 = "3cf168080a6733c0afb33ab618ddca8e724533b599ce72634e3cbe12bbb12f95"
FORMAL_REVIEW_PATH = "docs/evidence/poc-recovery-001/formal-engineering-security-review-2026-08-13.json"
FORMAL_REVIEW_SHA256 = "8a57f0603bbd4ec6ae2768007d11425a9fc5c4bdff58ca4408421e5712838960"
FORMAL_FINDINGS_LEDGER_PATH = "docs/evidence/poc-recovery-001/review-findings-v0.6.json"
FORMAL_FINDINGS_LEDGER_SHA256 = "776f3803e0237aea71532529c75268c10f1180cc23aa4aaa793382d2b72ac42f"
HISTORICAL_ADVISORY_LEDGER_PATH = "docs/evidence/poc-recovery-001/review-findings-v0.5.json"
HISTORICAL_ADVISORY_LEDGER_SHA256 = "bb848aba1324a2ee9c67eacd443d5855bb138af39532baddc92ea5a61b5d517c"
REVIEWER_NAME = "Novikova Katerina"
REVIEWER_CAPACITY = "individual professional capacity; Rambus listed only as affiliation"
FORMAL_DISPOSITION = "APPROVE_FOR_SEPARATE_IMPLEMENTATION_REVIEW"
REC_REV_02_CLOSURE = "CLOSED_BY_DISTINCT_ACCOUNTABLE_FORMAL_HUMAN_REVIEW"
REC_RDY_02_CLOSURE = "CLOSED_DISTINCT_ACCOUNTABLE_FORMAL_HUMAN_REVIEW"
PUBLIC_CONSENT = "Я согласна на публичное размещение моего имени, affiliation, project role, review date и formal disposition в публичном репозитории DORA."
WRITTEN_CONFIRMATION_METHOD = "Verbatim written response of Novikova Katerina, relayed by the Project owner in this Codex task on 2026-08-13."
FORMAL_QUESTION_SUBJECTS = [
    "DURABLE_ONE_SEGMENT_LOOKAHEAD, q/R, reads and EOF",
    "public non-deprecated streaming construction",
    "five-second AES256_GCM_TINK_IV12_TAG16 microfiles",
    "manifest, four exact AAD schemas and bounded rollback claim",
    "Android Keystore, key confirmation, ordered KEY taxonomy and KEY-04",
    "semantic commit and C/R/A accounting",
    "publication sequences, path families, reconciliation and quarantine",
    "SQLite contract and fresh preflight contract",
    "candidate-specific K01 through K12 barriers",
    "46 effective rows, KEY-04, KCF-07 and fault routing",
    "dependency, IP, authenticity and narrow R8 boundary",
    "separate campaign profiles, reuse criteria and canonical blockers",
]
BASE_HEAD = FORMAL_REVIEW_BASE_MAIN
SQLITE_STATUS = "RECOVERY_STAGE0_V0_6_SQLITE_PROFILE_SELECTED_FRESH_PREFLIGHT_INCOMPLETE"
AUTHORIZATION_PATH = "docs/evidence/poc-recovery-001/implementation-authorization-rec-i1-20260813-01.json"
AUTHORIZATION_ID = "REC-I1-AUTH-20260813-01"
AUTHORIZATION_SEMANTIC_SHA256 = "251e56db1b7046646ef55cf498fa54a1d1955201d4e24459aa470d89d50fa3be"
AUTHORIZED_BASE_HEAD = "9c4a798aa3c95877ff3f9aa66f18f94849b25cce"
AUTHORIZED_BASE_TREE = "e562dbd783eb17d59c8f94f43a728dd5c62e6e5b"
AUTHORIZED_BRANCH = "codex/poc-recovery-contract-kernel"
AUTHORIZED_REVIEWED_TREE = "1fd03fd489836c65f7ee043298f8f6d32df00c55"
REC_I1_REVIEWED_HEAD = "ee7bb00b09a282df7a8fb3b4d3481a5abd4d0177"
REC_I1_MERGED_ANCHOR = "f2bc8c95bbe8af0d010968fff2ca175851728bf2"
REC_I1_MERGED_TREE = "ac3dcf273fd447623fa8dbc5c71087acd6315830"
REC_I1_MERGED_PARENT = AUTHORIZED_BASE_HEAD
RECOVERY_MODULE = ROOT / "android" / "poc" / "recovery"
AUTHORIZED_PATHS = [
    "android/poc/recovery/**",
    "android/settings.gradle.kts",
    ".github/workflows/android-ci.yml",
    AUTHORIZATION_PATH,
    "docs/evidence/poc-recovery-001/readiness.json",
    "docs/evidence/poc-recovery-001/dependency-inventory.json",
    "docs/evidence/poc-recovery-001/evidence-index.json",
    "docs/evidence/poc-recovery-001/README.md",
    "docs/DORA_MVP1_IMPLEMENTATION_READINESS.md",
    "docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md",
    "docs/DORA_MVP1_STAGE_STATUS.md",
    "docs/stage0/DORA_MVP1_POC_EXECUTION_ORDER.md",
    "docs/stage0/device-matrix.yaml",
    "tools/validate_poc_recovery_governance.py",
    "tools/verify_poc_recovery_dependency_inventory.py",
    "tools/check_poc_recovery_run_readiness.py",
]

# The whole REC-I1 Android module is the implementation payload admitted by the
# reviewed/squash-merged tree. Post-merge descendants may change unrelated files,
# including this validator, but no file below this module may differ from the
# merged anchor in a commit, index, worktree, or relevant untracked state.
POST_MERGE_PROTECTED_PATHS = ["android/poc/recovery/**"]
GITHUB_REPOSITORY = "Monumentogram/DORA"
GITHUB_BASE_BRANCH = "main"
GITHUB_EVENT_MAX_BYTES = 10 * 1024 * 1024
FULL_SHA256_RE = re.compile(r"[0-9a-f]{40}")


@dataclass(frozen=True)
class GitHubPullRequestContext:
    repository: str
    head_repository: str
    head_ref: str
    head_sha: str
    base_ref: str
    base_sha: str
    merge_ref: str
    merge_sha: str
    number: int


@dataclass(frozen=True)
class RecoveryLifecycleIdentity:
    head: str
    branch: str
    authorized_base_tree: str | None
    authorized_merge_base: str | None
    merged_anchor_commit: str | None
    merged_anchor_tree: str | None
    merged_anchor_parents: tuple[str, ...]
    reviewed_implementation_commit: str | None
    reviewed_implementation_tree: str | None
    merged_anchor_is_ancestor: bool
    github_pull_request_context: GitHubPullRequestContext | None = None

NORMATIVE_V06_HASHES = {
    "docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_6.md": "5ab6d105fe6c94868d77c25d1be065a1688ccb083fcbdc0c3f43096e73909063",
    GATE_PATH: "6a5f1f994e5084836527fded9bdf762ac1ed982cb5022b6da64090a283717755",
    PROTOCOL_PATH: "9108cbffc3dc74a0e2a45868bf0c82b3827cb1e9023e1f0f12c53e7374c07a3d",
}

IMMUTABLE_AUDIT_HASHES = {
    "docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_1.md": "d891e033e3e58455dbafd03be5a41ca64cafda93182424357035c37d769ae46e",
    "docs/stage0/poc-recovery-gate-set-stage0-v0.1.json": "78c1a8289f90b51a376b023673dc00b6cb35386b5b0a2dda9432b50b20216e11",
    "docs/stage0/poc-recovery-protocol-stage0-v0.1.json": "b853295e6c66815c61566e930d30dafa0dfc72e805bb5ba38158688e084ead81",
    "docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_2.md": "d4fab2f47872f0b6c1c04c5b0b1022b047ae8782eb0130cd2f66825294455180",
    "docs/stage0/poc-recovery-gate-set-stage0-v0.2.json": "f6384c7b1d4d493218a600722ddf0116f454e8356e7e247da74f03256cc69110",
    "docs/stage0/poc-recovery-protocol-stage0-v0.2.json": "cfa06e624cbc0da37b68188d7b1739cdfb5ca12beeedc21f408897dc41b2081f",
    "docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_3.md": "7d24e5aa0c2dd0c65ef8def12e687d39f5d0bfc30a222be51f29bffd02c772a9",
    "docs/stage0/poc-recovery-gate-set-stage0-v0.3.json": "25a05a4d136f90e6b62005943585a27161517ea337573b71b5b1aaeca16bb80f",
    "docs/stage0/poc-recovery-protocol-stage0-v0.3.json": "376c6bec9d6632ff0824465ee890f953445c0843716b8a1b3a044f322d03a0c9",
    "docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_4.md": "03521bce76d123d463c86980f1db10b43667b39cea5114810977c8d4940dad0f",
    "docs/stage0/poc-recovery-gate-set-stage0-v0.4.json": "f89d5dff7bcfdcc7f96efd4d1c195b0054e262976db3b722b384c50e4440804c",
    "docs/stage0/poc-recovery-protocol-stage0-v0.4.json": "cfe9d19e7b0e409c1be6a33c4cd240ebdc03e014f0b1abe1b0776aae1ede5eaa",
    "docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_5.md": "ce4980468bfeb7bddadfa58b3ba71b702de4562cc8e264908d19f92fa7638f9c",
    "docs/stage0/poc-recovery-gate-set-stage0-v0.5.json": "3d1051c85076dda1d8b0c20812c08b7343a7c611ad8c6381d9e80d41724bef93",
    "docs/stage0/poc-recovery-protocol-stage0-v0.5.json": "9b469562aa8deff2f94402b6fe5093fb832d76ec48f5ef0fd081b76b322c3e9c",
}

CANONICAL_TAXONOMY = [
    "KEY_REF_COLLISION",
    "INCOMPLETE_KEY_BOOTSTRAP",
    "KEY_CONFIRMATION_MISSING",
    "CORRUPT_KEY_CONFIRMATION",
    "KEY_UNAVAILABLE",
    "KEY_UNAVAILABLE_KEY_MISMATCH",
    "CORRUPT_KEY_ENVELOPE",
    "KEY_ENVELOPE_AUTH_FAILURE",
]

CANONICAL_BLOCKERS = [
    "REC-RDY-01-PRODUCT-IP-FINAL-APPROVAL",
    "REC-RDY-02-ACCOUNTABLE-ENGINEERING-SECURITY-REVIEW",
    "REC-RDY-03-STREAMING-IMPLEMENTATION-VERIFICATION",
    "REC-RDY-04-MICROFILE-IMPLEMENTATION-VERIFICATION",
    "REC-RDY-05-FUTURE-RESOLVED-GRAPH",
    "REC-RDY-06-DEVICE-SQLITE-PREFLIGHT",
    "REC-RDY-07-HARNESS-ABSENT",
    "REC-RDY-08-OWNER-EXECUTION-AUTHORIZATION",
    "REC-RDY-09-D1-D5-FULL-VERDICT",
    "REC-RDY-10-PRODUCTION-LEGAL-SECURITY",
    "REC-RDY-11-SUPPLY-CHAIN-AUTHENTICITY",
]

EXPECTED_FAULT_IDS = [
    *(f"COR-{index:02d}" for index in range(1, 7)),
    *(f"TRU-{index:02d}" for index in range(1, 4)),
    *(f"KEY-{index:02d}" for index in range(1, 8)),
    *(f"SPL-{index:02d}" for index in range(1, 6)),
    "RBK-01", "RBK-02", "PAR-01",
    "QUA-01", "QUA-02", "QUA-03",
    "IDE-01", "IDE-02", "EVT-01",
    "CLN-01", "CLN-02", "CLN-03",
    *(f"KCB-{index:02d}" for index in range(1, 7)),
    *(f"KCF-{index:02d}" for index in range(1, 8)),
]

KEY04_PRECONDITIONS = [
    "A durable run row exists.",
    "The key-confirmation final exists.",
    "The key-confirmation path, type, recorded ciphertext length and recorded ciphertext SHA-256 fully match.",
    "The Android Keystore alias exists and is available through the approved Builder/getAead path.",
    "The exact confirmation AAD is computed under the active protocol.",
    "Before recovery, the fault controller replaces the underlying alias key with another valid AEAD key while preserving the previous confirmation ciphertext bytes and their recorded length and SHA-256.",
    "Recovery does not create or replace the key.",
    "Aead.decrypt(existingConfirmationCiphertext, exactAad) terminates with an authentication/AAD failure.",
]

KEY04_FORBIDDEN = {
    "SUCCESSFUL_DECRYPT",
    "MALFORMED_DECRYPTED_PLAINTEXT",
    "WRONG_MAGIC_AFTER_SUCCESSFUL_DECRYPT",
    "WRONG_SCHEMA_AFTER_SUCCESSFUL_DECRYPT",
    "WRONG_PROTOCOL_ID_AFTER_SUCCESSFUL_DECRYPT",
    "WRONG_CANDIDATE_ID_AFTER_SUCCESSFUL_DECRYPT",
    "WRONG_RUN_ID_AFTER_SUCCESSFUL_DECRYPT",
    "WRONG_CANONICAL_ALIAS_SHA256_AFTER_SUCCESSFUL_DECRYPT",
    "CIPHERTEXT_PATH_CORRUPTION",
    "CIPHERTEXT_TYPE_CORRUPTION",
    "CIPHERTEXT_RECORDED_LENGTH_CORRUPTION",
    "CIPHERTEXT_RECORDED_SHA256_CORRUPTION",
    "MISSING_ALIAS",
    "INVALIDATED_ALIAS",
    "UNUSABLE_ALIAS",
}

KCF07_POST_DECRYPT_FAILURES = [
    "malformed plaintext",
    "wrong magic",
    "wrong schema",
    "wrong protocolId",
    "wrong candidateId",
    "wrong runId",
    "wrong canonicalAliasSha256",
]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def read_text(relative: str) -> str:
    path = ROOT / relative
    require(path.is_file(), f"Missing required file: {relative}")
    return path.read_text(encoding="utf-8")


def read_json(relative: str) -> dict[str, Any]:
    return json.loads(read_text(relative))


def sha256(relative: str) -> str:
    return hashlib.sha256((ROOT / relative).read_bytes()).hexdigest()


def semantic_sha256(record: dict[str, Any]) -> str:
    canonical = json.dumps(record, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    return hashlib.sha256(canonical).hexdigest()


def git_output(*arguments: str, root: Path | None = None) -> str:
    return subprocess.run(
        ["git", *arguments],
        cwd=root or ROOT,
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="strict",
    ).stdout.strip()


def git_optional_output(*arguments: str, root: Path | None = None) -> str | None:
    try:
        return git_output(*arguments, root=root)
    except subprocess.CalledProcessError:
        return None


def decode_git_path_records(output: bytes, command: str) -> list[str]:
    if not output:
        return []
    require(output.endswith(b"\0"), f"Git path output is not NUL-terminated: {command}")
    try:
        records = output[:-1].split(b"\0")
        decoded = [record.decode("utf-8", errors="strict") for record in records]
    except UnicodeDecodeError as error:
        raise ValueError(f"Git path output is not valid UTF-8: {command}") from error
    require(all("\0" not in path for path in decoded), f"Git path output contains NUL: {command}")
    return [path for path in decoded if path]


def git_path_records(*arguments: str, root: Path | None = None) -> list[str]:
    result = subprocess.run(
        ["git", *arguments],
        cwd=root or ROOT,
        check=True,
        capture_output=True,
        text=False,
    )
    return decode_git_path_records(result.stdout, "git " + " ".join(arguments))


def git_is_ancestor(ancestor: str, descendant: str, root: Path | None = None) -> bool:
    result = subprocess.run(
        ["git", "merge-base", "--is-ancestor", ancestor, descendant],
        cwd=root or ROOT,
        check=False,
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="strict",
    )
    if result.returncode not in (0, 1):
        raise subprocess.CalledProcessError(
            result.returncode,
            result.args,
            output=result.stdout,
            stderr=result.stderr,
        )
    return result.returncode == 0


def validate_authorization_record(record: dict[str, Any] | None) -> None:
    require(record is not None, "Missing REC-I1 authorization")
    require(semantic_sha256(record) == AUTHORIZATION_SEMANTIC_SHA256, "REC-I1 authorization semantic contract drift")
    require(
        record["authorizationId"] == AUTHORIZATION_ID
        and record["status"] == "AUTHORIZED_EXACT_SCOPE_ONLY"
        and record["authorizedBy"] == {"role": "Project owner"}
        and record["repository"] == "Monumentogram/DORA",
        "REC-I1 authorization identity drift",
    )
    require(
        record["base"] == {
            "commit": AUTHORIZED_BASE_HEAD,
            "tree": AUTHORIZED_BASE_TREE,
            "mustMatchLiveProtectedMain": True,
        }
        and record["reviewedTechnicalTarget"] == {
            "commit": REVIEWED_V06_HEAD,
            "tree": AUTHORIZED_REVIEWED_TREE,
        },
        "REC-I1 base or reviewed technical target drift",
    )
    contract = record["contract"]
    require(
        contract["gateSetId"] == GATE_ID
        and contract["gateSetJsonSha256"] == NORMATIVE_V06_HASHES[GATE_PATH]
        and contract["protocolId"] == PROTOCOL_ID
        and contract["protocolJsonSha256"] == NORMATIVE_V06_HASHES[PROTOCOL_PATH]
        and contract["normativeMarkdownSha256"]
        == NORMATIVE_V06_HASHES["docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_6.md"]
        and contract["immutableInheritedAuditArtifactCount"] == 15,
        "REC-I1 contract hash boundary drift",
    )
    task = record["task"]
    require(
        task["id"] == "REC-I1"
        and task["allowedBranch"] == AUTHORIZED_BRANCH
        and task["module"] == ":poc:recovery"
        and task["modulePath"] == "android/poc/recovery"
        and task["applicationId"] == "com.monumentogram.dora.poc.recovery"
        and task["scopeKind"] == "PURE_NON_METRIC_NO_RUNTIME_CRYPTO"
        and record["allowedPaths"] == AUTHORIZED_PATHS,
        "REC-I1 task or path allowlist drift",
    )
    authority = record["authorityBoundary"]
    require(authority["taskScopedImplementationAuthorized"] is True, "REC-I1 task authorization missing")
    require(
        all(
            authority[field] is False
            for field in (
                "implementationAllowed",
                "implementationAllowedByThisPackage",
                "executionAllowed",
                "measuredExecutionAllowed",
                "dependencyAdmission",
                "productionAdmission",
                "phaseAAuthorized",
                "deviceExecutionAuthorized",
                "mergeAuthorized",
            )
        ),
        "REC-I1 authority escalated",
    )
    require(
        record["delivery"]["draftPullRequestAllowed"] is True
        and record["delivery"]["markReadyAllowed"] is False
        and record["delivery"]["mergeAllowed"] is False,
        "REC-I1 delivery boundary drift",
    )


def path_is_authorized(relative: str) -> bool:
    normalized = relative.replace("\\", "/")
    return any(
        normalized.startswith(pattern[:-3] + "/") if pattern.endswith("/**") else fnmatch.fnmatchcase(normalized, pattern)
        for pattern in AUTHORIZED_PATHS
    )


def validate_changed_paths(paths: list[str]) -> None:
    forbidden = sorted(path for path in paths if not path_is_authorized(path))
    require(not forbidden, f"REC-I1 tracked/untracked diff escapes authorization allowlist: {forbidden}")


def path_is_post_merge_protected(relative: str) -> bool:
    normalized = relative.replace("\\", "/")
    return any(
        normalized.startswith(pattern[:-3] + "/") if pattern.endswith("/**") else fnmatch.fnmatchcase(normalized, pattern)
        for pattern in POST_MERGE_PROTECTED_PATHS
    )


def validate_post_merge_protected_paths(changes: dict[str, list[str]]) -> None:
    expected_layers = {"committed", "staged", "unstaged", "untracked"}
    require(set(changes) == expected_layers, "Incomplete post-merge Recovery payload change inventory")
    protected_changes = {
        layer: sorted({path for path in paths if path_is_post_merge_protected(path)})
        for layer, paths in changes.items()
    }
    protected_changes = {layer: paths for layer, paths in protected_changes.items() if paths}
    require(
        not protected_changes,
        f"REC-I1 post-merge protected payload differs from merged anchor: {protected_changes}",
    )


def path_is_within(path: Path, parent: Path) -> bool:
    try:
        path.relative_to(parent)
        return True
    except ValueError:
        return False


def collect_github_pull_request_context(
    head: str,
    github_head_ref: str,
    *,
    environ: Mapping[str, str] | None = None,
    root: Path | None = None,
) -> GitHubPullRequestContext:
    env = os.environ if environ is None else environ
    repository_root = (root or ROOT).resolve(strict=True)
    require(env.get("GITHUB_EVENT_NAME") == "pull_request", "GITHUB_HEAD_REF requires a pull_request event")
    require(env.get("GITHUB_REPOSITORY") == GITHUB_REPOSITORY, "GitHub event repository mismatch")

    workspace_value = env.get("GITHUB_WORKSPACE", "")
    runner_temp_value = env.get("RUNNER_TEMP", "")
    event_path_value = env.get("GITHUB_EVENT_PATH", "")
    require(workspace_value and runner_temp_value and event_path_value, "Incomplete GitHub pull_request path context")
    workspace = Path(workspace_value)
    runner_temp = Path(runner_temp_value)
    event_path = Path(event_path_value)
    require(workspace.is_absolute() and runner_temp.is_absolute() and event_path.is_absolute(), "GitHub event paths must be absolute")
    require(workspace.resolve(strict=True) == repository_root, "GITHUB_WORKSPACE does not match the repository root")
    resolved_runner_temp = runner_temp.resolve(strict=True)
    require(event_path.is_file() and not event_path.is_symlink(), "GITHUB_EVENT_PATH is not a regular non-symlink file")
    resolved_event_path = event_path.resolve(strict=True)
    require(path_is_within(resolved_event_path, resolved_runner_temp), "GITHUB_EVENT_PATH escapes RUNNER_TEMP")
    event_size = resolved_event_path.stat().st_size
    require(0 < event_size <= GITHUB_EVENT_MAX_BYTES, "GitHub event payload size is invalid")
    try:
        event = json.loads(resolved_event_path.read_text(encoding="utf-8", errors="strict"))
    except (OSError, UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError("GitHub pull_request event payload is unreadable") from error
    require(isinstance(event, dict), "GitHub event payload root must be an object")

    try:
        repository = event["repository"]["full_name"]
        pull_request = event["pull_request"]
        number = event["number"]
        pull_request_number = pull_request["number"]
        head_record = pull_request["head"]
        base_record = pull_request["base"]
        head_repository = head_record["repo"]["full_name"]
        base_repository = base_record["repo"]["full_name"]
        head_ref = head_record["ref"]
        head_sha = head_record["sha"]
        base_ref = base_record["ref"]
        base_sha = base_record["sha"]
        event_merge_sha = pull_request["merge_commit_sha"]
    except (KeyError, TypeError) as error:
        raise ValueError("GitHub pull_request event payload is incomplete") from error

    require(isinstance(number, int) and number > 0 and pull_request_number == number, "GitHub pull_request number mismatch")
    require(repository == GITHUB_REPOSITORY and base_repository == GITHUB_REPOSITORY, "GitHub pull_request base repository mismatch")
    require(head_repository == GITHUB_REPOSITORY, "GitHub pull_request head repository is a fork")
    require(head_ref == github_head_ref == env.get("GITHUB_HEAD_REF"), "GitHub pull_request head ref mismatch")
    require(base_ref == GITHUB_BASE_BRANCH == env.get("GITHUB_BASE_REF"), "GitHub pull_request base ref mismatch")
    require(FULL_SHA256_RE.fullmatch(head_sha or "") is not None, "GitHub pull_request head SHA is invalid")
    require(FULL_SHA256_RE.fullmatch(base_sha or "") is not None, "GitHub pull_request base SHA is invalid")
    merge_ref = env.get("GITHUB_REF", "")
    merge_sha = env.get("GITHUB_SHA", "")
    require(merge_ref == f"refs/pull/{number}/merge", "GitHub pull_request ref is not the merge ref")
    require(FULL_SHA256_RE.fullmatch(merge_sha) is not None and merge_sha == head, "GitHub merge SHA does not match HEAD")
    # GitHub's pull_request payload may report merge_commit_sha as null for an
    # open pull request while GITHUB_SHA and refs/pull/<number>/merge already
    # identify the merge commit checked out by Actions. When the payload does
    # report a commit, retain the stricter exact binding.
    require(
        event_merge_sha is None or event_merge_sha == merge_sha,
        "GitHub event merge commit SHA conflicts with HEAD",
    )
    require(
        git_output("rev-parse", "--verify", f"{base_sha}^{{commit}}", root=repository_root) == base_sha
        and git_output("rev-parse", "--verify", f"{head_sha}^{{commit}}", root=repository_root) == head_sha,
        "GitHub pull_request base or head commit is missing",
    )
    merge_parents = tuple(git_output("show", "-s", "--format=%P", head, root=repository_root).split())
    require(merge_parents == (base_sha, head_sha), "GitHub merge-ref parent topology mismatch")
    require(git_output("merge-base", base_sha, head_sha, root=repository_root) == base_sha, "GitHub pull_request head is not based on its base SHA")

    return GitHubPullRequestContext(
        repository=repository,
        head_repository=head_repository,
        head_ref=head_ref,
        head_sha=head_sha,
        base_ref=base_ref,
        base_sha=base_sha,
        merge_ref=merge_ref,
        merge_sha=merge_sha,
        number=number,
    )


def select_lifecycle_branch(
    checked_out_branch: str,
    github_head_ref: str,
    github_pull_request_context: GitHubPullRequestContext | None = None,
) -> str:
    if github_head_ref:
        require(github_pull_request_context is not None, "GITHUB_HEAD_REF lacks verified pull_request context")
        require(
            github_pull_request_context.head_ref == github_head_ref,
            "Verified pull_request context conflicts with GITHUB_HEAD_REF",
        )
    require(
        not checked_out_branch or not github_head_ref or checked_out_branch == github_head_ref,
        "Checked-out branch conflicts with GITHUB_HEAD_REF",
    )
    return checked_out_branch or github_head_ref


def validate_recovery_lifecycle(
    identity: RecoveryLifecycleIdentity,
    pre_merge_changed_paths: list[str],
    post_merge_changes: dict[str, list[str]],
) -> str:
    post_merge_candidate = identity.head == REC_I1_MERGED_ANCHOR or identity.merged_anchor_is_ancestor
    if post_merge_candidate:
        require(
            identity.merged_anchor_commit == REC_I1_MERGED_ANCHOR,
            "REC-I1 merged anchor is missing or resolves to the wrong commit",
        )
        require(identity.merged_anchor_tree == REC_I1_MERGED_TREE, "REC-I1 merged anchor tree mismatch")
        require(
            identity.merged_anchor_parents == (REC_I1_MERGED_PARENT,),
            "REC-I1 merged anchor parent mismatch",
        )
        require(
            identity.reviewed_implementation_commit == REC_I1_REVIEWED_HEAD,
            "REC-I1 reviewed implementation head is missing or resolves to the wrong commit",
        )
        require(
            identity.reviewed_implementation_tree == REC_I1_MERGED_TREE,
            "REC-I1 reviewed implementation tree does not match the merged anchor tree",
        )
        require(
            identity.head == REC_I1_MERGED_ANCHOR or identity.merged_anchor_is_ancestor,
            "HEAD is neither the REC-I1 merged anchor nor its descendant",
        )
        validate_post_merge_protected_paths(post_merge_changes)
        return "post-merge"

    require(identity.authorized_base_tree == AUTHORIZED_BASE_TREE, "Authorized base tree mismatch")
    require(
        identity.authorized_merge_base == AUTHORIZED_BASE_HEAD,
        "HEAD is not based on the exact authorized base",
    )
    require(identity.branch == AUTHORIZED_BRANCH, f"REC-I1 is running on unauthorized branch: {identity.branch}")
    if identity.github_pull_request_context is not None:
        pull_request = identity.github_pull_request_context
        require(
            pull_request.repository == GITHUB_REPOSITORY
            and pull_request.head_repository == GITHUB_REPOSITORY
            and pull_request.head_ref == AUTHORIZED_BRANCH
            and pull_request.base_ref == GITHUB_BASE_BRANCH
            and pull_request.base_sha == AUTHORIZED_BASE_HEAD,
            "REC-I1 pre-merge pull_request context is not the authorized same-repository base",
        )
    validate_changed_paths(pre_merge_changed_paths)
    return "pre-merge"


def collect_recovery_lifecycle_identity() -> RecoveryLifecycleIdentity:
    head = git_output("rev-parse", "HEAD")
    checked_out_branch = git_output("branch", "--show-current")
    github_head_ref = os.environ.get("GITHUB_HEAD_REF", "")
    github_pull_request_context = (
        collect_github_pull_request_context(head, github_head_ref)
        if github_head_ref
        else None
    )
    branch = select_lifecycle_branch(checked_out_branch, github_head_ref, github_pull_request_context)

    merged_anchor_commit = git_optional_output("rev-parse", "--verify", f"{REC_I1_MERGED_ANCHOR}^{{commit}}")
    merged_anchor_tree = None
    merged_anchor_parents: tuple[str, ...] = ()
    if merged_anchor_commit is not None:
        merged_anchor_tree = git_optional_output("rev-parse", f"{REC_I1_MERGED_ANCHOR}^{{tree}}")
        parents = git_optional_output("show", "-s", "--format=%P", REC_I1_MERGED_ANCHOR)
        merged_anchor_parents = tuple(parents.split()) if parents is not None else ()

    reviewed_implementation_commit = git_optional_output(
        "rev-parse", "--verify", f"{REC_I1_REVIEWED_HEAD}^{{commit}}"
    )
    reviewed_implementation_tree = None
    if reviewed_implementation_commit is not None:
        reviewed_implementation_tree = git_optional_output("rev-parse", f"{REC_I1_REVIEWED_HEAD}^{{tree}}")

    return RecoveryLifecycleIdentity(
        head=head,
        branch=branch,
        authorized_base_tree=git_optional_output("rev-parse", f"{AUTHORIZED_BASE_HEAD}^{{tree}}"),
        authorized_merge_base=git_optional_output("merge-base", AUTHORIZED_BASE_HEAD, "HEAD"),
        merged_anchor_commit=merged_anchor_commit,
        merged_anchor_tree=merged_anchor_tree,
        merged_anchor_parents=merged_anchor_parents,
        reviewed_implementation_commit=reviewed_implementation_commit,
        reviewed_implementation_tree=reviewed_implementation_tree,
        merged_anchor_is_ancestor=(
            merged_anchor_commit is not None and git_is_ancestor(REC_I1_MERGED_ANCHOR, head)
        ),
        github_pull_request_context=github_pull_request_context,
    )


def collect_pre_merge_changed_paths(*, root: Path | None = None, authorized_base: str | None = None) -> list[str]:
    repository_root = root or ROOT
    base = authorized_base or AUTHORIZED_BASE_HEAD
    changed_paths = set(
        git_path_records("diff", "--name-only", "--no-renames", "-z", base, "--", root=repository_root)
    )
    untracked = git_path_records("ls-files", "--others", "--exclude-standard", "-z", root=repository_root)
    changed_paths.update(path for path in untracked if not path.startswith(".codex-remote-attachments/"))
    return sorted(path for path in changed_paths if path)


def collect_post_merge_changes(*, root: Path | None = None, merged_anchor: str | None = None) -> dict[str, list[str]]:
    repository_root = root or ROOT
    anchor = merged_anchor or REC_I1_MERGED_ANCHOR
    committed_tree_delta = git_path_records(
        "diff", "--name-only", "--no-renames", "-z", anchor, "HEAD", "--", root=repository_root
    )
    committed_history = git_path_records(
        "log", "--format=", "--name-only", "--no-renames", "-z", f"{anchor}..HEAD", "--", root=repository_root
    )
    return {
        "committed": sorted({path for path in (*committed_tree_delta, *committed_history) if path}),
        "staged": git_path_records("diff", "--cached", "--name-only", "--no-renames", "-z", "HEAD", "--", root=repository_root),
        "unstaged": git_path_records("diff", "--name-only", "--no-renames", "-z", "--", root=repository_root),
        "untracked": git_path_records("ls-files", "--others", "--exclude-standard", "-z", root=repository_root),
    }


def validate_recovery_build_text(content: str) -> None:
    require(content.count('id("dora.android.application")') == 1, "Recovery module must use the application convention")
    require("dora.android.library" not in content and "alias(libs.plugins" not in content, "Recovery module added another plugin")
    for fragment in (
        'namespace = "com.monumentogram.dora.poc.recovery"',
        'applicationId = "com.monumentogram.dora.poc.recovery"',
        "versionCode = 1",
        'versionName = "0.1.0-poc-recovery-i1"',
        "testImplementation(libs.junit4)",
    ):
        require(fragment in content, f"Recovery build contract missing: {fragment}")
    dependency_lines = [
        line.strip()
        for line in content.splitlines()
        if re.search(r"\b(?:api|implementation|compileOnly|runtimeOnly|testImplementation|androidTestImplementation|debugImplementation)\s*\(", line)
    ]
    require(dependency_lines == ["testImplementation(libs.junit4)"], f"Forbidden Recovery dependency declaration: {dependency_lines}")
    forbidden = ("tink", "jsr305", "project(", "files(", "fileTree(", "ksp(", "androidTestImplementation(")
    require(not any(value.lower() in content.lower() for value in forbidden), "Recovery build contains forbidden runtime/project dependency wiring")


def validate_manifest_text(content: str) -> None:
    root = ET.fromstring(content)
    require(root.tag == "manifest", "Recovery manifest root drift")
    children = list(root)
    require(len(children) == 1 and children[0].tag == "application", "Recovery manifest must contain only one application node")
    application = children[0]
    require(not application.attrib and not list(application), "Recovery manifest declares a component or runtime attribute")


def validate_ci_text(content: str) -> None:
    expected_tasks = [
        ":poc:recovery:testDebugUnitTest",
        ":poc:recovery:lintDebug",
        ":poc:recovery:assembleDebug",
    ]
    recovery_tasks = re.findall(r":poc:recovery:[A-Za-z][A-Za-z0-9]*", content)
    require(recovery_tasks == expected_tasks, f"Recovery CI task scope drift: {recovery_tasks}")


def validate_production_source_text(content: str, relative: str) -> None:
    forbidden_patterns = {
        r"(?m)^\s*import\s+android\.": "Android runtime import",
        r"(?m)^\s*import\s+com\.google\.crypto\.tink": "Tink import",
        r"\bjava\.(?:io|nio\.file)\.": "filesystem-capable Java API",
        r"\bkotlin\.io\.": "filesystem-capable Kotlin API",
        r"\bjavax\.crypto\.": "runtime crypto API",
        r"\bAndroidKeystoreKmsClient\b|\bKeyStore\b": "Keystore runtime API",
        r"\bSQLite(?:Database|OpenHelper)?\b|android\.database": "SQLite runtime API",
        r"\bStreamingAead\b|com\.google\.crypto": "runtime crypto API",
        r"(?i)\b(?:class|object|interface)\s+\w*(?:Harness|Controller|Benchmark|Instrumentation)\b": "harness or execution entry point",
    }
    for pattern, label in forbidden_patterns.items():
        require(re.search(pattern, content) is None, f"{relative} contains forbidden {label}")
    security_imports = [
        line.strip()
        for line in content.splitlines()
        if line.strip().startswith("import java.security.")
    ]
    require(
        all(line == "import java.security.MessageDigest" for line in security_imports),
        f"{relative} imports a forbidden java.security key or encryption API",
    )
    lowered_parts = [part.lower() for part in Path(relative).parts]
    require(
        not any(
            token in part
            for part in lowered_parts
            for token in ("harness", "controller", "benchmark", "instrumentation")
        ),
        f"{relative} introduces a forbidden harness or execution source path",
    )
    if "MessageDigest" in content:
        require(
            'MessageDigest.getInstance("SHA-256").digest(bytes)' in content
            and "Cipher" not in content
            and "KeyGenerator" not in content,
            f"{relative} uses MessageDigest outside exact SHA-256 identity calculation",
        )


def validate_immutable_history(gate: dict[str, Any], protocol: dict[str, Any]) -> None:
    require(len(IMMUTABLE_AUDIT_HASHES) == 15, "Historical hash registry must contain 15 artifacts")
    for relative, expected in IMMUTABLE_AUDIT_HASHES.items():
        require(sha256(relative) == expected, f"Superseded audit artifact changed: {relative}")

    retained = gate["retainedAuditArtifacts"]
    require(
        [item["version"] for item in retained]
        == [f"poc-recovery-stage0-v0.{version}" for version in range(1, 6)],
        "Gate Set v0.1-v0.5 retained history drift",
    )
    recorded: dict[str, str] = {}
    for item in retained:
        require(item["disposition"] == "SUPERSEDED_AUDIT_ARTIFACT_NON_EXECUTABLE", "Historical version became executable")
        recorded[item["markdownLocator"]] = item["markdownSha256"]
        recorded[item["gateLocator"]] = item["gateSha256"]
        recorded[item["protocolLocator"]] = item["protocolSha256"]
    require(recorded == IMMUTABLE_AUDIT_HASHES, "Recorded v0.1-v0.5 SHA-256 pins drift")

    gate_parent = gate["inheritsExactV05GateSet"]
    require(
        gate_parent["locator"] == "docs/stage0/poc-recovery-gate-set-stage0-v0.5.json"
        and gate_parent["sha256"] == IMMUTABLE_AUDIT_HASHES[gate_parent["locator"]]
        and gate_parent["allUnchangedSemanticsInherited"] is True,
        "v0.6 Gate Set inheritance drift",
    )
    protocol_parent = protocol["inheritsExactV05Contract"]
    require(
        protocol_parent["locator"] == "docs/stage0/poc-recovery-protocol-stage0-v0.5.json"
        and protocol_parent["sha256"] == IMMUTABLE_AUDIT_HASHES[protocol_parent["locator"]]
        and protocol_parent["allUnchangedSemanticsInherited"] is True,
        "v0.6 protocol inheritance drift",
    )
    historical = protocol["historicalProtocolV03"]
    require(
        historical["sha256"] == IMMUTABLE_AUDIT_HASHES[historical["locator"]]
        and historical["immutable"] is True
        and historical["historicalKey04Changed"] is False
        and historical["historicalRowsParticipateAsAdditionalActiveRows"] is False,
        "Historical v0.3/KEY-04 boundary drift",
    )


def validate_campaigns(campaigns: dict[str, Any]) -> None:
    phase = campaigns["phaseA"]
    require(
        phase["rows"] == 46
        and phase["perRow"] == {"PINNED_API36_X86_64_EMULATOR": 3, "PHYSICAL_D2": 1}
        and phase["emulatorInjections"] == 138
        and phase["physicalD2Injections"] == 46
        and phase["phaseATotalInjections"] == 184
        and phase["allowedVerdicts"] == ["FAIL", "INCONCLUSIVE"]
        and phase["passAllowed"] is False,
        "Phase A count/verdict drift",
    )
    full = campaigns["fullPhysicalCampaign"]
    require(
        full["rows"] == 46
        and full["perRow"] == {"PHYSICAL_D1": 1, "PHYSICAL_D2": 1, "PHYSICAL_D5": 1}
        and full["fullPhysicalTotalInjections"] == 138
        and full["passRequiresCompleteD1D2D5Profile"] is True,
        "Full physical count/profile drift",
    )
    require(
        campaigns.get("hardKillAttemptsPerCandidate", campaigns.get("hardKillCampaign", {}).get("attemptsPerCandidate")) == 120,
        "Hard-kill attempts/candidate drift",
    )
    separate = campaigns.get("separateFromHardKillDenominator")
    if separate is None:
        separate = campaigns["hardKillCampaign"]["separateFromFaultInjectionDenominators"]
    require(separate is True, "Hard-kill denominator was merged with fault injections")


def validate_gate(gate: dict[str, Any]) -> None:
    require(
        gate["schemaVersion"] == 6
        and gate["pocId"] == "POC-RECOVERY-001"
        and gate["gateSetVersion"] == GATE_ID
        and gate["protocolId"] == PROTOCOL_ID,
        "Active v0.6 Gate Set identity drift",
    )
    require(gate["implementationAllowed"] is False and gate["executionAllowed"] is False, "Gate Set authorized work")
    require(all(value is False for value in gate["scope"].values() if isinstance(value, bool)), "Governance-only scope widened")
    require(
        gate["supersedes"] == {
            "gateSetVersion": "poc-recovery-stage0-v0.5",
            "disposition": "SUPERSEDED_SHA256_PINNED_AUDIT_ARTIFACT_NON_EXECUTABLE",
            "reviewedCommit": REVIEWED_V05_HEAD,
        },
        "v0.5 supersession record drift",
    )
    require(gate["findingsLedgers"][-1].endswith("review-findings-v0.5.json"), "v0.5 review ledger missing")
    approval = gate["approvalState"]
    require(
        approval["actualFutureGraphProductIpDisposition"].startswith("OPEN_BLOCKED")
        and approval["productIpFinalApproval"] is False
        and approval["accountableIndependentEngineeringSecurityReviewer"] is None
        and approval["advisoryDocumentaryReviewIsFormal"] is False
        and approval["currentCodexReviewClaimedFormallyIndependent"] is False
        and approval["productionLegalReviewer"] is None
        and approval["productionSecurityReviewer"] is None,
        "Approval/reviewer boundary drift",
    )
    advisory = gate["advisoryReviewEvidence"]
    require(
        advisory == {
            "reviewer": "GPT-5.6 Sol",
            "organization": "OpenAI",
            "role": "AI documentary advisory reviewer",
            "reviewDate": "2026-08-12",
            "reviewedCommit": REVIEWED_V05_HEAD,
            "formalReviewer": False,
            "disposition": "CHANGES_REQUIRED",
            "closesRecRdy02": False,
        },
        "Advisory review evidence drift",
    )
    require(
        gate["reviewFindings"] == {
            "REC-REV-20260812-01": "CLOSED_BY_V0_6_EXACT_DECRYPT_FAILURE_OVERRIDE",
            "REC-REV-20260812-02": "OPEN_BLOCKING",
        },
        "Review finding dispositions drift",
    )
    ids = gate["mandatoryFaultIds"]
    require(gate["mandatoryFaultRowCount"] == 46 and ids == EXPECTED_FAULT_IDS, "Gate fault ID order/count drift")
    require(len(ids) == len(set(ids)) == 46 and ids.count("KEY-04") == 1, "Gate fault IDs are not 46 unique/one KEY-04")
    key_gate = gate["keyConfirmationGate"]
    require(
        key_gate["mandatoryFaultRowsAddedV06"] == 0
        and key_gate["effectiveFaultRowsOverriddenV06"] == 1
        and key_gate["effectiveKey04ExpectedClassification"] == "KEY_UNAVAILABLE_KEY_MISMATCH"
        and key_gate["effectiveKey04SuccessfulDecryptAllowed"] is False
        and key_gate["effectiveKey04PostDecryptPlaintextMismatchAllowed"] is False,
        "Gate KEY-04 summary drift",
    )
    matrix = gate["activeEffectiveFaultMatrix"]
    require(
        matrix["requiredUniqueRowCount"] == 46
        and matrix["key04RequiredOccurrenceCount"] == 1
        and matrix["historicalRowsAreAdditionalActiveRows"] is False,
        "Gate active matrix contract drift",
    )
    validate_campaigns(gate["faultCampaignProfiles"])
    require(gate["blockers"] == CANONICAL_BLOCKERS, "Gate blocker order drift")


def effective_rows(protocol: dict[str, Any]) -> list[dict[str, Any]]:
    return protocol["faultCampaign"]["activeEffectiveFaultMatrixV06"]["rows"]


def validate_key04_and_kcf07(protocol: dict[str, Any]) -> None:
    rows = effective_rows(protocol)
    ids = [row["id"] for row in rows]
    require(len(rows) == len(set(ids)) == 46 and ids == EXPECTED_FAULT_IDS, "Active matrix must have 46 unique canonical IDs")
    require(all(row["effective"] is True for row in rows), "Active matrix contains a non-effective row")
    require(ids.count("KEY-04") == 1, "Active matrix must contain KEY-04 exactly once")
    key04 = next(row for row in rows if row["id"] == "KEY-04")
    require(key04["effectiveSource"] == "V0_6_EXACT_DECRYPT_FAILURE_OVERRIDE", "KEY-04 is not the v0.6 override")
    require(key04["preconditions"] == KEY04_PRECONDITIONS, "KEY-04 exact preconditions drift")
    require(key04["decryptOutcome"] == "AUTHENTICATION_OR_AAD_FAILURE_ONLY", "KEY-04 permits a non-auth decrypt outcome")
    require(key04["successfulDecryptAllowed"] is False, "KEY-04 permits successful decrypt")
    require(key04["postDecryptParserOrPlaintextMismatchAllowed"] is False, "KEY-04 permits post-decrypt mismatch")
    require(
        key04["expectedClassification"] == "KEY_UNAVAILABLE_KEY_MISMATCH"
        and key04["expectedClassificationAlternativesAllowed"] is False,
        "KEY-04 expected classification is not strict",
    )
    require(set(key04["forbiddenInterpretations"]) == KEY04_FORBIDDEN, "KEY-04 forbidden interpretations drift")
    replacement = key04["replacesInheritedHistoricalRow"]
    require(
        replacement["historicalRowModified"] is False
        and replacement["historicalRowActiveInAdditionToThisRow"] is False
        and replacement["sha256"] == IMMUTABLE_AUDIT_HASHES[replacement["locator"]],
        "KEY-04 historical replacement boundary drift",
    )

    kcf07 = next(row for row in rows if row["id"] == "KCF-07")
    require(
        kcf07["effectiveSource"] == "V0_5_CASE_INHERITED_UNCHANGED"
        and "Aead.decrypt() succeeds" in kcf07["requiredObservation"]
        and kcf07["coveredPostDecryptFailures"] == KCF07_POST_DECRYPT_FAILURES
        and kcf07["expectedClassification"] == "CORRUPT_KEY_CONFIRMATION",
        "KCF-07 successful-decrypt malformed-plaintext oracle drift",
    )
    routing = protocol["faultCampaign"]["effectiveKey04Routing"]
    require(
        routing["successfulDecryptWithMalformedOrWrongPlaintext"] == {"faultRow": "KCF-07", "classification": "CORRUPT_KEY_CONFIRMATION"}
        and routing["ciphertextPathTypeLengthOrHashMismatch"] == {"stage": "PRE_DECRYPT", "classification": "CORRUPT_KEY_CONFIRMATION"}
        and routing["missingInvalidatedOrUnusableAlias"] == {"classification": "KEY_UNAVAILABLE"}
        and routing["structurallyValidLaterKeyEnvelopeAeadAadOrTagFailureAfterValidConfirmation"] == {"classification": "KEY_ENVELOPE_AUTH_FAILURE"},
        "KEY-04 neighboring failure routing drift",
    )


def validate_protocol(protocol: dict[str, Any]) -> None:
    require(
        protocol["schemaVersion"] == 6
        and protocol["protocolId"] == PROTOCOL_ID
        and protocol["pocId"] == "POC-RECOVERY-001"
        and protocol["implementationAllowed"] is False
        and protocol["executionAllowed"] is False,
        "Active v0.6 protocol identity/authority drift",
    )
    taxonomy = protocol["canonicalKeyTaxonomyV06"]
    require(
        taxonomy["uniqueClassifications"] == CANONICAL_TAXONOMY
        and taxonomy["uniqueClassificationCount"] == 8
        and taxonomy["plaintextMagicSchemaParserOrTrailingValidationBeforeDecryptAllowed"] is False
        and taxonomy["ambiguousExpectedOutcomeAllowed"] is False,
        "Canonical taxonomy drift",
    )
    algorithm = taxonomy["recoveryReconciliationAlgorithm"]
    require([step["step"] for step in algorithm] == list(range(1, 10)), "Recovery algorithm order drift")
    require([step["classification"] for step in algorithm] == [
        "INCOMPLETE_KEY_BOOTSTRAP", "KEY_CONFIRMATION_MISSING", "CORRUPT_KEY_CONFIRMATION",
        "KEY_UNAVAILABLE", "KEY_UNAVAILABLE_KEY_MISMATCH", "CORRUPT_KEY_CONFIRMATION",
        "KEY_UNAVAILABLE", "CORRUPT_KEY_ENVELOPE", "KEY_ENVELOPE_AUTH_FAILURE",
    ], "Recovery algorithm classification drift")
    validate_key04_and_kcf07(protocol)
    fault = protocol["faultCampaign"]
    require(
        fault["mandatoryFaultRowCount"] == 46
        and fault["addedV06MandatoryRows"] == 0
        and fault["overriddenV06EffectiveRows"] == 1,
        "Protocol fault count/override drift",
    )
    matrix_meta = fault["activeEffectiveFaultMatrixV06"]
    require(
        matrix_meta["rowCount"] == 46
        and matrix_meta["uniqueIdsRequired"] is True
        and matrix_meta["key04OccurrenceCountRequired"] == 1
        and matrix_meta["historicalRowsAreAdditionalActiveRows"] is False,
        "Active matrix metadata drift",
    )
    validate_campaigns(fault)
    require(protocol["canonicalBlockerIds"] == CANONICAL_BLOCKERS, "Protocol blocker IDs drift")
    evidence = protocol["reviewEvidence"]
    require(
        evidence["reviewer"] == "GPT-5.6 Sol"
        and evidence["organization"] == "OpenAI"
        and evidence["formalReviewer"] is False
        and evidence["disposition"] == "CHANGES_REQUIRED"
        and evidence["closesRecRdy02"] is False
        and evidence["recRev2026081201Disposition"] == "CLOSED_BY_V0_6_EXACT_DECRYPT_FAILURE_OVERRIDE"
        and evidence["recRev2026081202Disposition"] == "OPEN_BLOCKING",
        "Protocol review evidence drift",
    )


def validate_post_merge_evidence(record: dict[str, Any]) -> None:
    require(
        record["schemaVersion"] == 1
        and record["evidenceId"] == "POC-RECOVERY-001-POST-MERGE-ADVISORY-REREVIEW-20260813"
        and record["pocId"] == "POC-RECOVERY-001"
        and record["recordedOn"] == "2026-08-13"
        and record["scope"] == "GOVERNANCE_ONLY_POST_MERGE_RECONCILIATION",
        "Post-merge evidence identity drift",
    )
    merge = record["mergeEvidence"]
    require(
        merge["repository"] == "Monumentogram/DORA"
        and merge["pullRequest"] == 12
        and merge["url"] == "https://github.com/Monumentogram/DORA/pull/12"
        and merge["state"] == "CLOSED"
        and merge["merged"] is True
        and merge["draft"] is False
        and merge["mergedAt"] == "2026-08-13T06:03:14+03:00"
        and merge["timezone"] == "Europe/Moscow"
        and merge["mergeMethod"] == "PROTECTED_GITHUB_SQUASH_MERGE"
        and merge["previousMainCommit"] == REVIEWED_V05_HEAD
        and merge["pullRequestHeadCommit"] == REVIEWED_V06_HEAD
        and merge["mergedMainCommit"] == MERGED_V06_MAIN
        and merge["mergeCommitParents"] == [REVIEWED_V05_HEAD]
        and merge["mergeCommitTreeObjectId"] == MERGED_V06_TREE
        and merge["pullRequestHeadTreeObjectId"] == MERGED_V06_TREE
        and merge["mergeTreeMatchesPullRequestHeadTree"] is True
        and merge["sourceHeadBranch"] == "stage/0d-poc-recovery-key04-v06"
        and merge["sourceHeadBranchPreserved"] is True,
        "PR #12 merge evidence drift",
    )
    actions = record["postMergeActions"]
    require(
        actions["workflow"] == "Android CI"
        and actions["runId"] == 31662723278
        and actions["event"] == "push"
        and actions["headCommit"] == MERGED_V06_MAIN
        and actions["status"] == "COMPLETED"
        and actions["conclusion"] == "SUCCESS"
        and actions["jobs"] == [
            {"name": "android-bootstrap", "conclusion": "SUCCESS"},
            {"name": "search-smoke", "conclusion": "SUCCESS"},
        ],
        "PR #12 post-merge Actions evidence drift",
    )
    review = record["advisoryReReview"]
    require(
        review["reviewer"] == "OpenAI Codex (GPT-5)"
        and review["organization"] == "OpenAI"
        and review["role"] == "AI documentary advisory reviewer"
        and review["reviewDate"] == "2026-08-13"
        and review["timezone"] == "Europe/Moscow"
        and review["reviewedCommit"] == REVIEWED_V06_HEAD
        and review["reviewedTreeObjectId"] == MERGED_V06_TREE
        and review["formalReviewer"] is False
        and review["disposition"] == "NO_FURTHER_DOCUMENTARY_CHANGES_REQUIRED"
        and review["actionableFindings"] == []
        and review["publishedAsFormalGitHubReview"] is False
        and review["repeatAdvisoryReviewComplete"] is True
        and review["closesRecRev2026081202"] is False
        and review["closesRecRdy02"] is False,
        "Post-merge advisory re-review authority or disposition drift",
    )
    require(
        record["findingState"] == {
            "REC-REV-20260812-01": "CLOSED_BY_V0_6_EXACT_DECRYPT_FAILURE_OVERRIDE",
            "REC-REV-20260812-02": "OPEN_BLOCKING",
        },
        "Post-merge finding state drift",
    )
    boundary = record["readinessBoundary"]
    require(
        boundary["nextGate"] == "ASSIGN_DISTINCT_ACCOUNTABLE_RECOVERY_ENGINEERING_SECURITY_REVIEWER"
        and boundary["recRdy02Priority"] == "P0"
        and boundary["recRdy02Status"] == "OPEN_UNASSIGNED"
        and boundary["recRdy02Blocking"] is True
        and boundary["accountableEngineeringSecurityReviewer"] is None
        and all(boundary[field] is False for field in (
            "implementationAllowed", "implementationAllowedByThisPackage", "executionAllowed",
            "measuredExecutionAllowed", "recoveryDependencyWiringPresent", "recoveryModuleExists",
            "harnessExists", "recoveryOrDeviceTestsExecuted", "killCampaignExecuted",
            "benchmarkExecuted", "measuredExecutionPerformed", "dependencyAdmission",
            "productionAdmission",
        )),
        "Post-merge fail-closed readiness boundary drift",
    )


def validate_historical_advisory_ledger(record: dict[str, Any]) -> None:
    require(
        record["sourceReviewedCommit"] == REVIEWED_V05_HEAD
        and record["reviewedGateSetVersion"] == "poc-recovery-stage0-v0.5"
        and record["activeRemediationProtocolId"] == PROTOCOL_ID
        and record["review"]["reviewer"] == "GPT-5.6 Sol"
        and record["review"]["organization"] == "OpenAI"
        and record["review"]["formalReviewer"] is False
        and record["review"]["disposition"] == "CHANGES_REQUIRED"
        and record["closesRecRdy02"] is False,
        "Historical advisory findings ledger identity/authority drift",
    )
    findings = {item["id"]: item for item in record["findings"]}
    require(
        findings["REC-REV-20260812-01"]["severity"] == "P1"
        and findings["REC-REV-20260812-01"]["disposition"] == "CLOSED_BY_V0_6_EXACT_DECRYPT_FAILURE_OVERRIDE"
        and findings["REC-REV-20260812-02"]["severity"] == "P0"
        and findings["REC-REV-20260812-02"]["category"] == "governance"
        and findings["REC-REV-20260812-02"]["disposition"] == "OPEN_BLOCKING",
        "Historical advisory finding content drift",
    )


def validate_formal_human_review(record: dict[str, Any]) -> None:
    require(
        record["schemaVersion"] == 1
        and record["evidenceId"] == "POC-RECOVERY-001-FORMAL-HUMAN-ENGINEERING-SECURITY-REVIEW-20260813"
        and record["pocId"] == "POC-RECOVERY-001"
        and record["reviewType"] == "FORMAL_DISTINCT_ACCOUNTABLE_STAGE0_RECOVERY_ENGINEERING_SECURITY_REVIEW"
        and record["formalReviewer"] is True
        and record["reviewMode"] == "READ_ONLY_DOCUMENTARY_STATIC"
        and record["reviewDate"] == "2026-08-13",
        "Formal human review identity drift",
    )
    reviewer = record["reviewer"]
    require(
        reviewer["name"] == REVIEWER_NAME
        and reviewer["affiliation"] == "Rambus"
        and reviewer["affiliationOnly"] is True
        and reviewer["capacity"] == REVIEWER_CAPACITY
        and reviewer["projectRole"] == "Distinct accountable Stage 0 Recovery Engineering/Security reviewer"
        and reviewer["distinctFromPackageAuthor"] is True
        and reviewer["packageAuthor"] == "Codex"
        and reviewer["name"].casefold() != reviewer["packageAuthor"].casefold()
        and reviewer["rambusCorporateApprovalClaimed"] is False,
        "Formal reviewer identity, capacity, distinctness or Rambus affiliation-only boundary drift",
    )
    target = record["reviewedTarget"]
    require(
        target == {
            "packageCommit": REVIEWED_V06_HEAD,
            "packageTree": MERGED_V06_TREE,
            "gateSetVersion": GATE_ID,
            "protocolId": PROTOCOL_ID,
        },
        "Formal review target drift",
    )
    dossier = record["advisoryDossier"]
    require(
        dossier["locator"] == ADVISORY_DOSSIER_PATH
        and dossier["sourceAttachmentSha256"] == ADVISORY_DOSSIER_SOURCE_SHA256
        and dossier["formalReviewer"] is False
        and dossier["isNovikovaKaterinaDecision"] is False
        and dossier["closesRecRdy02"] is False,
        "AI dossier acquired formal human-review authority",
    )
    require(record["writtenConfirmationMethod"] == WRITTEN_CONFIRMATION_METHOD, "Written confirmation method drift")
    consent = record["publicRecordConsent"]
    require(consent["consented"] is True and consent["statement"] == PUBLIC_CONSENT, "Public-record consent missing or altered")
    responses = record["responses"]
    require(
        [item["id"] for item in responses] == [f"Q{index:02d}" for index in range(1, 13)]
        and len(responses) == 12
        and [item["subject"] for item in responses] == FORMAL_QUESTION_SUBJECTS
        and all(item["response"] == "ACCEPT" for item in responses),
        "All twelve formal review responses must be ACCEPT",
    )
    require(
        record["requiredChanges"] == "not stated in the verbatim response"
        and record["nonBlockingObservations"] == "not stated in the verbatim response"
        and record["disposition"] == FORMAL_DISPOSITION,
        "Formal disposition/change-observation record drift",
    )
    require(
        record["confirmations"] == {
            "personallyReadAdvisoryDossierAndReferencedEvidence": True,
            "consciouslyAcceptedOrCorrectedEachOfTwelveResponses": True,
            "acceptsAccountabilityForThisDisposition": True,
            "reviewWasReadOnly": True,
            "implementationPerformed": False,
            "executionPerformed": False,
            "measurementPerformed": False,
            "isProductionSecurityApproval": False,
            "isProductionLegalApproval": False,
            "isDependencyAdmission": False,
            "isExecutionAuthorization": False,
            "isImplementationAuthorization": False,
            "dispositionChangesAuthorityFlags": False,
        },
        "Formal reviewer confirmations/authority boundary drift",
    )
    require(
        record["findingClosures"] == {
            "REC-REV-20260812-02": REC_REV_02_CLOSURE,
            "REC-RDY-02-ACCOUNTABLE-ENGINEERING-SECURITY-REVIEW": REC_RDY_02_CLOSURE,
        },
        "Formal review finding-closure state drift",
    )
    boundary = record["authorityBoundary"]
    require(
        all(boundary[field] is False for field in (
            "implementationAllowed", "implementationAllowedByThisPackage", "executionAllowed",
            "measuredExecutionAllowed", "phaseAAuthorized", "dependencyAdmission",
            "productionSecurityApproval", "productionLegalApproval", "formalGitHubReviewClaimed",
        ))
        and boundary["separateImplementationReviewRequired"] is True
        and boundary["separateOwnerImplementationAuthorizationRequired"] is True
        and boundary["separateOwnerExecutionAuthorizationRequired"] is True,
        "Formal review improperly authorized implementation, Phase A, execution or production",
    )


def validate_formal_findings_ledger(record: dict[str, Any]) -> None:
    require(
        record["schemaVersion"] == 1
        and record["pocId"] == "POC-RECOVERY-001"
        and record["sourceLedger"] == HISTORICAL_ADVISORY_LEDGER_PATH
        and record["sourceLedgerUnchanged"] is True
        and record["reviewedPackageCommit"] == REVIEWED_V06_HEAD
        and record["reviewedPackageTree"] == MERGED_V06_TREE
        and record["reviewedGateSetVersion"] == GATE_ID
        and record["reviewedProtocolId"] == PROTOCOL_ID
        and record["formalReviewEvidenceLocator"] == FORMAL_REVIEW_PATH
        and record["formalReviewer"] is True
        and record["reviewer"] == REVIEWER_NAME
        and record["reviewerCapacity"] == REVIEWER_CAPACITY
        and record["disposition"] == FORMAL_DISPOSITION
        and record["closesRecRdy02"] is True,
        "Formal findings-ledger identity drift",
    )
    findings = {item["id"]: item for item in record["findings"]}
    require(
        findings["REC-REV-20260812-01"]["disposition"] == "CLOSED_BY_V0_6_EXACT_DECRYPT_FAILURE_OVERRIDE"
        and findings["REC-REV-20260812-01"]["changedByThisLedger"] is False
        and findings["REC-REV-20260812-02"]["priorDisposition"] == "OPEN_BLOCKING"
        and findings["REC-REV-20260812-02"]["disposition"] == REC_REV_02_CLOSURE
        and findings["REC-REV-20260812-02"]["closureEvidenceLocator"] == FORMAL_REVIEW_PATH
        and findings["REC-REV-20260812-02"]["reviewer"] == REVIEWER_NAME
        and findings["REC-REV-20260812-02"]["formalReviewer"] is True,
        "Formal finding closure drift",
    )
    boundary = record["authorityBoundary"]
    require(
        all(boundary[field] is False for field in (
            "implementationAllowed", "implementationAllowedByThisPackage", "executionAllowed",
            "measuredExecutionAllowed", "phaseAAuthorized", "dependencyAdmission",
            "productionSecurityApproval", "productionLegalApproval",
        )),
        "Formal findings ledger authorized prohibited work",
    )


def validate_readiness_and_evidence(gate: dict[str, Any]) -> None:
    readiness = read_json("docs/evidence/poc-recovery-001/readiness.json")
    roles = read_json("docs/evidence/poc-recovery-001/review-roles.json")
    historical_ledger = read_json(HISTORICAL_ADVISORY_LEDGER_PATH)
    formal_ledger = read_json(FORMAL_FINDINGS_LEDGER_PATH)
    formal_review = read_json(FORMAL_REVIEW_PATH)
    index = read_json("docs/evidence/poc-recovery-001/evidence-index.json")
    provenance = read_json("docs/evidence/poc-recovery-001/sqlite-platform-provenance.json")
    security = read_json("docs/evidence/poc-recovery-001/security-advisory-inventory.json")
    post_merge = read_json(POST_MERGE_EVIDENCE_PATH)

    require(sha256(POST_MERGE_EVIDENCE_PATH) == POST_MERGE_EVIDENCE_SHA256, "Post-merge evidence SHA-256 drift")
    require(sha256(HISTORICAL_ADVISORY_LEDGER_PATH) == HISTORICAL_ADVISORY_LEDGER_SHA256, "Historical advisory ledger changed")
    require(sha256(ADVISORY_DOSSIER_PATH) == ADVISORY_DOSSIER_SHA256, "Advisory Engineering/Security dossier SHA-256 drift")
    require(sha256(FORMAL_REVIEW_PATH) == FORMAL_REVIEW_SHA256, "Formal human-review evidence SHA-256 drift")
    require(sha256(FORMAL_FINDINGS_LEDGER_PATH) == FORMAL_FINDINGS_LEDGER_SHA256, "Formal findings-ledger SHA-256 drift")
    validate_post_merge_evidence(post_merge)
    validate_historical_advisory_ledger(historical_ledger)
    validate_formal_human_review(formal_review)
    validate_formal_findings_ledger(formal_ledger)
    dossier = read_text(ADVISORY_DOSSIER_PATH)
    require(
        "ADVISORY DRAFT FOR HUMAN REVIEWER" in dossier
        and "`formalReviewer` for this AI dossier | `false`" in dossier
        and "не является formal disposition Novikova Katerina" in dossier
        and "не закрывает `REC-RDY-02`" in dossier,
        "AI dossier formalReviewer/decision/readiness boundary drift",
    )

    require(
        readiness["schemaVersion"] == 10
        and readiness["status"].startswith("BLOCKED_")
        and all(readiness[field] is False for field in (
            "executionAllowed", "implementationAllowed", "implementationAllowedByThisPackage",
            "measuredExecutionAllowed", "runtimeDependencyAdded", "harnessImplemented",
            "nonMetricImplementationVerificationPassed",
            "exactFutureResolvedGraphReviewed", "killCampaignExecuted", "deviceTestsExecuted",
            "benchmarksExecuted", "productionAppChanged",
        ))
        and readiness["taskScopedImplementationAuthorized"] is True
        and readiness["authorizationId"] == AUTHORIZATION_ID
        and readiness["authorizationRecord"] == AUTHORIZATION_PATH
        and readiness["recoveryModuleExists"] is True,
        "Readiness authority/evidence boundary drift",
    )
    package = readiness["packageArtifacts"]
    require(
        package["activeGateSetVersion"] == GATE_ID
        and package["activeProtocolId"] == PROTOCOL_ID
        and package["governanceRemediationV06Present"] is True
        and package["v05RetainedAsSupersededAuditArtifact"] is True
        and package["v05Executable"] is False
        and package["reviewFindingsV05LedgerPresent"] is True
        and package["reviewFindingsV06LedgerPresent"] is True
        and package["advisoryEngineeringSecurityDossierPresent"] is True
        and package["formalAccountableEngineeringSecurityReviewPresent"] is True
        and package["recI1AuthorizationPresent"] is True
        and package["recI1ContractKernelPresent"] is True,
        "Readiness active package metadata drift",
    )
    narrow_i1 = readiness["recI1NonMetricVerification"]
    require(
        narrow_i1["scope"] == "PURE_NON_METRIC_NO_RUNTIME_CRYPTO"
        and narrow_i1["status"] in {"PENDING_REQUIRED_CHECKS", "PASSED_LOCAL_NON_METRIC_CHECKS"}
        and narrow_i1["passed"] is (narrow_i1["status"] == "PASSED_LOCAL_NON_METRIC_CHECKS")
        and narrow_i1["globalNonMetricImplementationVerificationSatisfied"] is False
        and narrow_i1["runtimeCryptoVerified"] is False
        and narrow_i1["harnessVerified"] is False
        and narrow_i1["executionEvidenceProduced"] is False,
        "Narrow I1 verification overclaims global/runtime evidence",
    )
    require(
        package["postMergeAdvisoryReReviewEvidencePresent"] is True
        and package["postMergeAdvisoryReReviewEvidenceLocator"] == POST_MERGE_EVIDENCE_PATH,
        "Readiness lacks post-merge advisory re-review evidence",
    )
    advisory = readiness["advisoryDocumentaryReview"]
    require(advisory["formalReviewer"] is False and advisory["closesRecRdy02"] is False, "Readiness treats advisory review as formal")
    rereview = readiness["advisoryDocumentaryReReviewEvidence"]
    require(
        rereview["locator"] == POST_MERGE_EVIDENCE_PATH
        and rereview["reviewer"] == post_merge["advisoryReReview"]["reviewer"]
        and rereview["reviewedCommit"] == REVIEWED_V06_HEAD
        and rereview["formalReviewer"] is False
        and rereview["disposition"] == "NO_FURTHER_DOCUMENTARY_CHANGES_REQUIRED"
        and rereview["actionableFindings"] == []
        and rereview["publishedAsFormalGitHubReview"] is False
        and rereview["repeatAdvisoryReviewComplete"] is True
        and rereview["closesRecRev2026081202"] is False
        and rereview["closesRecRdy02"] is False,
        "Readiness post-merge advisory re-review drift",
    )
    formal_summary = readiness["formalAccountableEngineeringSecurityReviewEvidence"]
    require(
        formal_summary["locator"] == FORMAL_REVIEW_PATH
        and formal_summary["reviewer"] == REVIEWER_NAME
        and formal_summary["affiliation"] == "Rambus"
        and formal_summary["capacity"] == REVIEWER_CAPACITY
        and formal_summary["reviewDate"] == "2026-08-13"
        and formal_summary["reviewedCommit"] == REVIEWED_V06_HEAD
        and formal_summary["reviewedTree"] == MERGED_V06_TREE
        and formal_summary["formalReviewer"] is True
        and formal_summary["disposition"] == FORMAL_DISPOSITION
        and formal_summary["recRev2026081202Disposition"] == REC_REV_02_CLOSURE
        and formal_summary["recRdy02Status"] == REC_RDY_02_CLOSURE
        and formal_summary["closesRecRev2026081202"] is True
        and formal_summary["closesRecRdy02"] is True
        and formal_summary["rambusCorporateApprovalClaimed"] is False
        and formal_summary["formalGitHubReviewClaimed"] is False
        and all(formal_summary[field] is False for field in (
            "implementationAllowed", "implementationAllowedByThisPackage", "executionAllowed",
            "measuredExecutionAllowed", "phaseAAuthorized",
        )),
        "Readiness formal accountable review summary drift",
    )
    blocker_ids = [item["id"] for item in readiness["blockers"]]
    active_blockers = [item for item in CANONICAL_BLOCKERS if item != "REC-RDY-02-ACCOUNTABLE-ENGINEERING-SECURITY-REVIEW"]
    require(gate["blockers"] == CANONICAL_BLOCKERS, "Immutable Gate blocker history drift")
    require(blocker_ids == active_blockers and len(set(blocker_ids)) == 10, "Current readiness blocker contract drift")
    require(len(readiness["closedBlockers"]) == 1, "Closed Recovery blocker ledger drift")
    rec02 = readiness["closedBlockers"][0]
    require(
        rec02["id"] == "REC-RDY-02-ACCOUNTABLE-ENGINEERING-SECURITY-REVIEW"
        and rec02["status"] == REC_RDY_02_CLOSURE
        and rec02["evidenceLocator"] == FORMAL_REVIEW_PATH,
        "REC-RDY-02 distinct accountable human-review closure drift",
    )
    require(
        readiness["phaseA"]["phaseATotalInjections"] == 184
        and readiness["phaseA"]["hardKillDenominatorSeparate"] is True
        and readiness["phaseA"]["authorizedNow"] is False
        and readiness["phaseA"]["authorizationGrantedByFormalReview"] is False
        and readiness["phaseA"]["executionAllowedNow"] is False,
        "Readiness Phase A authority/count drift",
    )
    require(readiness["fullVerdict"]["fullPhysicalTotalInjections"] == 138 and readiness["fullVerdict"]["deferred"] is True, "Readiness full physical drift")

    require(roles["schemaVersion"] == 9 and roles["activeGateSetVersion"] == GATE_ID and roles["activeProtocolId"] == PROTOCOL_ID, "Review role metadata drift")
    require(
        roles["advisoryReviewEvidenceLocators"] == [
            HISTORICAL_ADVISORY_LEDGER_PATH,
            POST_MERGE_EVIDENCE_PATH,
            ADVISORY_DOSSIER_PATH,
        ],
        "Review role advisory evidence history drift",
    )
    require(
        roles["formalReviewEvidenceLocators"] == [FORMAL_REVIEW_PATH, FORMAL_FINDINGS_LEDGER_PATH],
        "Review role formal evidence locators drift",
    )
    role_map = roles["roles"]
    require(role_map["packageAuthor"]["claimedFormallyIndependentReviewer"] is False, "Codex claimed formal independence")
    require(role_map["advisoryDocumentaryReviewer"]["formalReviewer"] is False and role_map["advisoryDocumentaryReviewer"]["closesRecRdy02"] is False, "AI advisory reviewer gained formal authority")
    independent = role_map["independentRecoveryEngineeringSecurity"]
    require(
        independent["reviewer"] == REVIEWER_NAME
        and independent["affiliation"] == "Rambus"
        and independent["affiliationOnly"] is True
        and independent["capacity"] == REVIEWER_CAPACITY
        and independent["status"] == FORMAL_DISPOSITION
        and independent["formalReviewer"] is True
        and independent["formalReviewEvidenceLocator"] == FORMAL_REVIEW_PATH
        and independent["reviewedCommit"] == REVIEWED_V06_HEAD
        and independent["reviewedTree"] == MERGED_V06_TREE
        and independent["recRev2026081202Disposition"] == REC_REV_02_CLOSURE
        and independent["recRdy02Status"] == REC_RDY_02_CLOSURE
        and independent["closesRecRdy02"] is True
        and independent["rambusCorporateApprovalClaimed"] is False
        and independent["formalGitHubReviewClaimed"] is False
        and independent["mayApproveImplementation"] is False
        and independent["mayAuthorizeExecution"] is False
        and all(independent[field] is False for field in (
            "implementationAllowed", "implementationAllowedByThisPackage", "executionAllowed",
            "measuredExecutionAllowed", "phaseAAuthorized",
        )),
        "Accountable formal reviewer assignment/disposition/authority drift",
    )
    require(role_map["stage0ProductIp"]["futureExactGraphDisposition"]["status"] == "OPEN_BLOCKED", "Future Product/IP graph disposition closed")
    require(role_map["productionLegal"]["reviewer"] is None and role_map["productionSecurity"]["reviewer"] is None, "Production review prematurely assigned")

    require(
        index["schemaVersion"] == 7
        and index["activeGateSetVersion"] == GATE_ID
        and index["activeProtocolId"] == PROTOCOL_ID
        and all(index[field] is False for field in (
            "implementationAllowed", "implementationAllowedByThisPackage", "executionAllowed",
            "measuredExecutionAllowed",
        ))
        and index["taskScopedImplementationAuthorized"] is True
        and index["authorizationId"] == AUTHORIZATION_ID
        and index["recoveryModuleExists"] is True
        and index["runtimeDependencyAdded"] is False
        and index["harnessImplemented"] is False
        and index["globalNonMetricImplementationVerificationPassed"] is False,
        "Evidence index metadata/authority drift",
    )
    require({item["locator"]: item["sha256"] for item in index["supersededAuditArtifacts"]} == IMMUTABLE_AUDIT_HASHES, "Evidence index historical hashes drift")
    artifact_ids = {item["id"] for item in index["artifacts"]}
    require({
        "REC-V06-GATE-MARKDOWN", "REC-V06-GATE-JSON", "REC-V06-PROTOCOL-JSON",
        "REC-V06-REMEDIATION", "REC-REVIEW-V05-LEDGER",
        "REC-ADVISORY-ENGINEERING-SECURITY-DOSSIER-20260813",
        "REC-FORMAL-HUMAN-ENGINEERING-SECURITY-REVIEW-20260813",
        "REC-REVIEW-V06-FORMAL-CLOSURE-LEDGER",
        "REC-I1-IMPLEMENTATION-AUTHORIZATION-20260813-01",
        "REC-I1-PURE-CONTRACT-KERNEL",
    }.issubset(artifact_ids), "Evidence index lacks formal-review artifacts")
    post_merge_index = next(item for item in index["artifacts"] if item["id"] == "REC-POST-MERGE-ADVISORY-REREVIEW-20260813")
    require(
        post_merge_index["locator"] == POST_MERGE_EVIDENCE_PATH
        and post_merge_index["sha256"] == POST_MERGE_EVIDENCE_SHA256
        and post_merge_index["sha256"] == sha256(post_merge_index["locator"])
        and post_merge_index["status"] == "HISTORICAL_PR12_SQUASH_MERGED_ADVISORY_REREVIEW_NO_FURTHER_DOCUMENTARY_CHANGES_FORMAL_REVIEWER_FALSE_REC_RDY_02_OPEN_AT_RECORD_TIME",
        "Evidence index post-merge advisory re-review pin drift",
    )
    indexed = {item["id"]: item for item in index["artifacts"]}
    historical_index = indexed["REC-REVIEW-V05-LEDGER"]
    require(
        historical_index["locator"] == HISTORICAL_ADVISORY_LEDGER_PATH
        and historical_index["sha256"] == HISTORICAL_ADVISORY_LEDGER_SHA256
        and historical_index["sha256"] == sha256(historical_index["locator"]),
        "Evidence index historical advisory ledger pin drift",
    )
    dossier_index = indexed["REC-ADVISORY-ENGINEERING-SECURITY-DOSSIER-20260813"]
    require(
        dossier_index["locator"] == ADVISORY_DOSSIER_PATH
        and dossier_index["sha256"] == ADVISORY_DOSSIER_SHA256
        and dossier_index["sourceAttachmentSha256"] == ADVISORY_DOSSIER_SOURCE_SHA256
        and dossier_index["sha256"] == sha256(dossier_index["locator"])
        and "FORMAL_REVIEWER_FALSE" in dossier_index["status"]
        and "DOES_NOT_CLOSE_REC_RDY_02" in dossier_index["status"],
        "Evidence index advisory dossier pin/authority drift",
    )
    formal_index = indexed["REC-FORMAL-HUMAN-ENGINEERING-SECURITY-REVIEW-20260813"]
    require(
        formal_index["locator"] == FORMAL_REVIEW_PATH
        and formal_index["sha256"] == FORMAL_REVIEW_SHA256
        and formal_index["sha256"] == sha256(formal_index["locator"])
        and FORMAL_DISPOSITION in formal_index["status"],
        "Evidence index formal review pin drift",
    )
    ledger_index = indexed["REC-REVIEW-V06-FORMAL-CLOSURE-LEDGER"]
    require(
        ledger_index["locator"] == FORMAL_FINDINGS_LEDGER_PATH
        and ledger_index["sha256"] == FORMAL_FINDINGS_LEDGER_SHA256
        and ledger_index["sha256"] == sha256(ledger_index["locator"])
        and "REC_RDY_02_CLOSED" in ledger_index["status"],
        "Evidence index formal closure-ledger pin drift",
    )

    require(provenance["status"] == SQLITE_STATUS and provenance["activeGateSetVersion"] == GATE_ID and provenance["activeProtocolId"] == PROTOCOL_ID and provenance["phaseA"]["executionAllowed"] is False, "SQLite provenance metadata drift")
    require(provenance["fullPhysicalVerdict"]["D1"].startswith("UNAVAILABLE") and provenance["fullPhysicalVerdict"]["D5"].startswith("UNAVAILABLE"), "D1/D5 no longer deferred")
    require(security["activeGateSetVersion"] == GATE_ID and security["activeProtocolId"] == PROTOCOL_ID and all(item["mitigationState"].startswith("V0_6_") for item in security["templateAndProtocolRisks"]), "Security metadata drift")


def validate_dependency_and_scope_boundary() -> None:
    authorization = read_json(AUTHORIZATION_PATH)
    validate_authorization_record(authorization)
    inventory = read_json("docs/evidence/poc-recovery-001/dependency-inventory.json")
    require(
        inventory["schemaVersion"] == 4
        and inventory["dependencyAdmission"] is False
        and inventory["runtimeGraphModified"] is False,
        "Dependency inventory admitted runtime wiring",
    )
    current = inventory["currentImplementationState"]
    require(
        current["authorizationId"] == AUTHORIZATION_ID
        and current["taskScopedImplementationAuthorized"] is True
        and current["recoveryModuleExists"] is True
        and current["module"] == ":poc:recovery"
        and current["moduleKind"] == "PURE_NON_METRIC_NO_RUNTIME_CRYPTO"
        and current["tinkAndroid123Wired"] is False
        and current["runtimeCryptoDependencyAdded"] is False
        and current["newExternalDependencyCoordinateAdded"] is False
        and current["actualRecoveryRuntimeGraphReviewed"] is False
        and current["dependencyAdmission"] is False
        and current["productionAdmission"] is False,
        "Current REC-I1 dependency boundary drift",
    )
    historical_boundary = inventory["recoveryBoundary"]
    require(
        historical_boundary["historicalSnapshot"] is True
        and historical_boundary["snapshotAssessedOn"] == "2026-08-12"
        and historical_boundary["currentTinkAndroid123Wired"] is False
        and historical_boundary["currentRecoveryModuleExists"] is False
        and historical_boundary["futureActualRecoveryGraphStatus"].startswith("OPEN_BLOCKED"),
        "Historical dependency-absence snapshot drift",
    )

    for relative, expected in NORMATIVE_V06_HASHES.items():
        require(sha256(relative) == expected, f"Normative v0.6 contract changed: {relative}")
    changed_normative = git_path_records(
        "diff", "--name-only", "--no-renames", "-z", AUTHORIZED_BASE_HEAD, "--", *NORMATIVE_V06_HASHES
    )
    require(not changed_normative, f"Normative v0.6 contract differs from formal-review base: {changed_normative}")

    require(git_output("rev-parse", f"{REVIEWED_V06_HEAD}^{{tree}}") == AUTHORIZED_REVIEWED_TREE, "Reviewed technical target tree mismatch")

    lifecycle_identity = collect_recovery_lifecycle_identity()
    post_merge_candidate = (
        lifecycle_identity.head == REC_I1_MERGED_ANCHOR or lifecycle_identity.merged_anchor_is_ancestor
    )
    lifecycle_mode = validate_recovery_lifecycle(
        lifecycle_identity,
        [] if post_merge_candidate else collect_pre_merge_changed_paths(),
        collect_post_merge_changes()
        if post_merge_candidate
        else {"committed": [], "staged": [], "unstaged": [], "untracked": []},
    )

    for historical_commit in (AUTHORIZED_BASE_HEAD, REVIEWED_V06_HEAD):
        module_snapshot = git_path_records(
            "ls-tree", "-r", "--name-only", "-z", historical_commit, "--", "android/poc/recovery"
        )
        require(not module_snapshot, f"Historical snapshot unexpectedly contains Recovery module: {historical_commit}")
        settings_snapshot = git_output("show", f"{historical_commit}:android/settings.gradle.kts")
        require(":poc:recovery" not in settings_snapshot, f"Historical snapshot unexpectedly includes Recovery: {historical_commit}")

    require(RECOVERY_MODULE.is_dir(), "Authorized Recovery module is missing")
    build_text = read_text("android/poc/recovery/build.gradle.kts")
    validate_recovery_build_text(build_text)
    settings = read_text("android/settings.gradle.kts")
    require(settings.count('include(":poc:recovery")') == 1, "Recovery module include missing or duplicated")
    validate_ci_text(read_text(".github/workflows/android-ci.yml"))
    validate_manifest_text(read_text("android/poc/recovery/src/main/AndroidManifest.xml"))
    require(not (RECOVERY_MODULE / "src" / "androidTest").exists(), "REC-I1 must not contain androidTest sources")
    require(not (RECOVERY_MODULE / "src" / "main" / "res").exists(), "REC-I1 must not contain UI/resources")

    production_sources = sorted((RECOVERY_MODULE / "src" / "main" / "kotlin").rglob("*.kt"))
    require(production_sources, "REC-I1 production contract sources are missing")
    production_text = "\n".join(path.read_text(encoding="utf-8") for path in production_sources)
    for source in production_sources:
        validate_production_source_text(source.read_text(encoding="utf-8"), source.relative_to(ROOT).as_posix())
    for magic in ("DORARM01", "DORARC01", "DORASA01", "DORAMA01", "DORACP01", "DORAKE01", "DORAKC01", "DORAKA01"):
        require(magic in production_text, f"REC-I1 source lacks codec magic {magic}")
    for classification in CANONICAL_TAXONOMY:
        require(classification in production_text, f"REC-I1 source lacks taxonomy value {classification}")
    for fault_id in EXPECTED_FAULT_IDS:
        require(f'"{fault_id}"' in production_text, f"REC-I1 source lacks fault ID {fault_id}")

    test_sources = sorted((RECOVERY_MODULE / "src" / "test" / "kotlin").rglob("*Test.kt"))
    require(len(test_sources) >= 5, "REC-I1 host-JVM test coverage is incomplete")
    test_text = "\n".join(path.read_text(encoding="utf-8") for path in test_sources)
    require("GOLDEN_HEX" in test_text and "Random(" in test_text, "REC-I1 lacks fixed golden and deterministic seeded coverage")

    for build_file in (ROOT / "android").rglob("build.gradle.kts"):
        if build_file == RECOVERY_MODULE / "build.gradle.kts":
            continue
        require(":poc:recovery" not in build_file.read_text(encoding="utf-8"), f"Production/module edge into Recovery: {build_file}")

    lockfile = RECOVERY_MODULE / "gradle.lockfile"
    require(current["moduleLockfilePresent"] is lockfile.is_file(), "Recovery lockfile presence claim mismatch")
    if lockfile.is_file():
        lock_text = lockfile.read_text(encoding="utf-8")
        require("tink" not in lock_text.lower() and "jsr305" not in lock_text.lower(), "Recovery lockfile contains forbidden runtime dependency")
        base_lock_coordinates: set[str] = set()
        base_lockfiles = git_path_records(
            "ls-tree", "-r", "--name-only", "-z", AUTHORIZED_BASE_HEAD, "--", "android"
        )
        for relative in base_lockfiles:
            if relative.endswith("gradle.lockfile"):
                snapshot = git_output("show", f"{AUTHORIZED_BASE_HEAD}:{relative}")
                base_lock_coordinates.update(
                    line.split("=", 1)[0]
                    for line in snapshot.splitlines()
                    if line and not line.startswith("#") and "=" in line
                )
        current_lock_coordinates = {
            line.split("=", 1)[0]
            for line in lock_text.splitlines()
            if line and not line.startswith("#") and "=" in line
        }
        require(current_lock_coordinates <= base_lock_coordinates, "Recovery lockfile contains a new coordinate")

    print(
        f"PASS REC-I1 {lifecycle_mode} lifecycle; post-merge protected paths: "
        f"{', '.join(POST_MERGE_PROTECTED_PATHS)}"
    )


def validate_active_metadata() -> None:
    fragments = {
        "docs/DORA_MVP1_TECHNICAL_PLAN.md": ["prospective protocol v0.6", "KEY_UNAVAILABLE_KEY_MISMATCH", "REC-RDY-02"],
        "docs/DORA_MVP1_PRODUCT_DECISIONS.md": [GATE_ID, PROTOCOL_ID, REC_REV_02_CLOSURE, REC_RDY_02_CLOSURE, REVIEWER_NAME],
        "docs/DORA_MVP1_TEST_STRATEGY.md": ["POC-RECOVERY-001` v0.6", "46 unique active fault rows", "KCF-07"],
        "docs/DORA_MVP1_STAGE_STATUS.md": [GATE_ID, PROTOCOL_ID, REC_RDY_02_CLOSURE, REVIEWER_NAME, AUTHORIZATION_ID, "Current Pull Request state is never a static document invariant"],
        "docs/stage0/DEC-044-POC-RECOVERY-EXPERIMENT.md": [GATE_ID, PROTOCOL_ID, "KEY_UNAVAILABLE_KEY_MISMATCH", "KCF-07", FORMAL_DISPOSITION, REVIEWER_CAPACITY],
        "docs/stage0/DORA_MVP1_POC_GATES.md": ["stage0-v0.6", "46 unique IDs", "KEY_UNAVAILABLE_KEY_MISMATCH"],
        "docs/stage0/DORA_MVP1_POC_EXECUTION_ORDER.md": ["stage0-v0.6", "46 unique active rows", "accountable formal review complete", AUTHORIZATION_ID],
        "docs/stage0/DORA_MVP1_IP_ASSET_POLICY.md": ["active protocol v0.6", "future actual recovery", "Engineering/Security reviewer"],
        "docs/stage0/DORA_MVP1_POC_RECOVERY_OWNER_DECISION_OD14.md": [GATE_ID, PROTOCOL_ID, "15 SHA-256 values", "formalReviewer=false", FORMAL_DISPOSITION, REVIEWER_NAME],
        "docs/stage0/DORA_MVP1_POC_RECOVERY_GATE_SET_STAGE0_V0_6.md": [
            *KEY04_PRECONDITIONS,
            "KEY_UNAVAILABLE_KEY_MISMATCH",
            "successful confirmation decrypt followed by malformed plaintext",
            "exactly 46 unique IDs",
            "46 × (3 pinned emulator + 1 D2)",
            "184 injections",
            "46 × (D1 + D2 + D5)",
            "138 injections",
            "120 attempts per candidate",
        ],
        "docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md": [GATE_ID, PROTOCOL_ID, "REC-REV-20260812-01", FORMAL_DISPOSITION, "implementationAllowed=false", AUTHORIZATION_ID],
        "docs/DORA_MVP1_IMPLEMENTATION_READINESS.md": [GATE_ID, PROTOCOL_ID, REC_RDY_02_CLOSURE, FORMAL_DISPOSITION, "executionAllowed=false", AUTHORIZATION_ID],
        "docs/evidence/poc-recovery-001/README.md": ["15 superseded audit artifacts", "GPT-5.6 Sol", "KCF-07", "NO_FURTHER_DOCUMENTARY_CHANGES_REQUIRED", FORMAL_DISPOSITION, REVIEWER_NAME, AUTHORIZATION_ID],
        "docs/evidence/poc-recovery-001/governance-remediation-v0.6.md": [REVIEWED_V05_HEAD, "CLOSED_BY_V0_6_EXACT_DECRYPT_FAILURE_OVERRIDE", "OPEN_BLOCKING"],
        "docs/evidence/poc-recovery-001/independent-engineering-security-review-task.md": ["POC-RECOVERY-001 v0.6", "KEY-04", "formalReviewer=false", "COMPLETED 2026-08-13"],
        "docs/evidence/poc-recovery-001/ip-stage0-evaluation-review.md": [GATE_ID, PROTOCOL_ID, FORMAL_DISPOSITION, REVIEWER_NAME],
    }
    for relative, required in fragments.items():
        content = read_text(relative)
        normalized_content = " ".join(content.replace("`", "").split())
        for fragment in required:
            require(
                fragment in content or " ".join(fragment.replace("`", "").split()) in normalized_content,
                f"{relative} active v0.6 metadata missing: {fragment}",
            )
    matrix = read_text("docs/stage0/device-matrix.yaml")
    for fragment in (f"gate_set: {GATE_ID}", f"protocol: {PROTOCOL_ID}", "mandatory_fault_rows: 46", "total_injections: 184", "total_injections: 138", f"authorization_id: {AUTHORIZATION_ID}", "recovery_module_exists: true", "implementation_allowed: false", "execution_allowed: false"):
        require(fragment in matrix, f"Device matrix active metadata missing: {fragment}")


def validate_all(gate: dict[str, Any], protocol: dict[str, Any], include_filesystem_hashes: bool = True) -> None:
    if include_filesystem_hashes:
        validate_immutable_history(gate, protocol)
    validate_gate(gate)
    validate_protocol(protocol)


def expect_negative(name: str, mutation: Callable[[dict[str, Any], dict[str, Any]], None], validate_history: bool = False) -> None:
    gate = read_json(GATE_PATH)
    protocol = read_json(PROTOCOL_PATH)
    mutation(gate, protocol)
    try:
        validate_all(gate, protocol, include_filesystem_hashes=validate_history)
    except (ValueError, KeyError, StopIteration):
        print(f"PASS negative {name}")
        return
    raise ValueError(f"Negative test unexpectedly passed: {name}")


def run_lifecycle_tests() -> None:
    def changes(**overrides: list[str]) -> dict[str, list[str]]:
        result = {"committed": [], "staged": [], "unstaged": [], "untracked": []}
        result.update(overrides)
        return result

    def expect_positive(
        name: str,
        identity: RecoveryLifecycleIdentity,
        expected_mode: str,
        pre_merge_paths: list[str] | None = None,
        post_merge_changes: dict[str, list[str]] | None = None,
    ) -> None:
        mode = validate_recovery_lifecycle(
            identity,
            pre_merge_paths or [],
            post_merge_changes or changes(),
        )
        require(mode == expected_mode, f"Lifecycle positive test selected the wrong mode: {name}")
        print(f"PASS positive lifecycle-{name}")

    def expect_lifecycle_negative(
        name: str,
        identity: RecoveryLifecycleIdentity,
        pre_merge_paths: list[str] | None = None,
        post_merge_changes: dict[str, list[str]] | None = None,
    ) -> None:
        try:
            validate_recovery_lifecycle(
                identity,
                pre_merge_paths or [],
                post_merge_changes or changes(),
            )
        except ValueError:
            print(f"PASS negative lifecycle-{name}")
            return
        raise ValueError(f"Negative lifecycle test unexpectedly passed: {name}")

    historical = RecoveryLifecycleIdentity(
        head=REC_I1_REVIEWED_HEAD,
        branch=AUTHORIZED_BRANCH,
        authorized_base_tree=AUTHORIZED_BASE_TREE,
        authorized_merge_base=AUTHORIZED_BASE_HEAD,
        merged_anchor_commit=None,
        merged_anchor_tree=None,
        merged_anchor_parents=(),
        reviewed_implementation_commit=None,
        reviewed_implementation_tree=None,
        merged_anchor_is_ancestor=False,
    )
    historical_paths = [
        "android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/RecoveryContract.kt",
        "tools/validate_poc_recovery_governance.py",
    ]
    expect_positive("historical-authorized-branch", historical, "pre-merge", historical_paths)
    historical_pull_request = GitHubPullRequestContext(
        repository=GITHUB_REPOSITORY,
        head_repository=GITHUB_REPOSITORY,
        head_ref=AUTHORIZED_BRANCH,
        head_sha=REC_I1_REVIEWED_HEAD,
        base_ref=GITHUB_BASE_BRANCH,
        base_sha=AUTHORIZED_BASE_HEAD,
        merge_ref="refs/pull/15/merge",
        merge_sha=REC_I1_REVIEWED_HEAD,
        number=15,
    )
    expect_positive(
        "historical-authorized-pull-request-context",
        replace(historical, github_pull_request_context=historical_pull_request),
        "pre-merge",
        historical_paths,
    )

    anchor = RecoveryLifecycleIdentity(
        head=REC_I1_MERGED_ANCHOR,
        branch="main",
        authorized_base_tree=AUTHORIZED_BASE_TREE,
        authorized_merge_base=AUTHORIZED_BASE_HEAD,
        merged_anchor_commit=REC_I1_MERGED_ANCHOR,
        merged_anchor_tree=REC_I1_MERGED_TREE,
        merged_anchor_parents=(REC_I1_MERGED_PARENT,),
        reviewed_implementation_commit=REC_I1_REVIEWED_HEAD,
        reviewed_implementation_tree=REC_I1_MERGED_TREE,
        merged_anchor_is_ancestor=True,
    )
    expect_positive("exact-merged-anchor-main", anchor, "post-merge")

    descendant = replace(
        anchor,
        head="d" * 40,
        branch="codex/unrelated-descendant",
    )
    expect_positive(
        "unrelated-descendant",
        descendant,
        "post-merge",
        post_merge_changes=changes(committed=["docs/unrelated.md"]),
    )
    expect_positive(
        "validator-only-remediation-descendant",
        replace(descendant, branch="codex/recovery-i1-post-merge-validator-remediation"),
        "post-merge",
        post_merge_changes=changes(committed=["tools/validate_poc_recovery_governance.py"]),
    )

    expect_lifecycle_negative(
        "historical-wrong-branch",
        replace(historical, branch="main"),
        historical_paths,
    )
    expect_lifecycle_negative(
        "historical-wrong-base-tree",
        replace(historical, authorized_base_tree="0" * 40),
        historical_paths,
    )
    expect_lifecycle_negative(
        "historical-wrong-merge-base",
        replace(historical, authorized_merge_base="0" * 40),
        historical_paths,
    )
    expect_lifecycle_negative(
        "historical-pull-request-wrong-base-sha",
        replace(
            historical,
            github_pull_request_context=replace(historical_pull_request, base_sha="0" * 40),
        ),
        historical_paths,
    )
    expect_lifecycle_negative(
        "non-descendant-post-merge-spoof",
        replace(anchor, head="e" * 40, merged_anchor_is_ancestor=False),
    )
    expect_lifecycle_negative("missing-anchor", replace(descendant, merged_anchor_commit=None))
    expect_lifecycle_negative("wrong-anchor-commit", replace(descendant, merged_anchor_commit="0" * 40))
    expect_lifecycle_negative("wrong-anchor-tree", replace(descendant, merged_anchor_tree="0" * 40))
    expect_lifecycle_negative("wrong-anchor-parent", replace(descendant, merged_anchor_parents=("0" * 40,)))
    expect_lifecycle_negative(
        "wrong-reviewed-implementation-head",
        replace(descendant, reviewed_implementation_commit="0" * 40),
    )
    expect_lifecycle_negative(
        "reviewed-tree-does-not-match-anchor",
        replace(descendant, reviewed_implementation_tree="0" * 40),
    )

    protected_kotlin = (
        "android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/RecoveryContract.kt"
    )
    expect_lifecycle_negative(
        "descendant-committed-protected-kotlin-mutation",
        descendant,
        post_merge_changes=changes(committed=[protected_kotlin]),
    )
    for layer, relative in (
        ("staged", protected_kotlin),
        ("unstaged", protected_kotlin),
        (
            "untracked",
            "android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/Injected.kt",
        ),
    ):
        expect_lifecycle_negative(
            f"descendant-{layer}-protected-mutation",
            descendant,
            post_merge_changes=changes(**{layer: [relative]}),
        )
    expect_lifecycle_negative(
        "branch-spoof-cannot-bypass-protected-mutation",
        replace(descendant, branch=AUTHORIZED_BRANCH),
        post_merge_changes=changes(committed=[protected_kotlin]),
    )
    try:
        select_lifecycle_branch("", AUTHORIZED_BRANCH)
    except ValueError:
        print("PASS negative lifecycle-environment-only-branch-spoof")
    else:
        raise ValueError("Negative lifecycle test unexpectedly passed: environment-only-branch-spoof")
    try:
        select_lifecycle_branch("codex/wrong-branch", AUTHORIZED_BRANCH, historical_pull_request)
    except ValueError:
        print("PASS negative lifecycle-checked-out-branch-conflicts-with-event")
    else:
        raise ValueError("Negative lifecycle test unexpectedly passed: checked-out-branch-conflicts-with-event")


def test_git(repo: Path, *arguments: str, input_data: bytes | None = None) -> bytes:
    return subprocess.run(
        ["git", *arguments],
        cwd=repo,
        input=input_data,
        check=True,
        capture_output=True,
        text=False,
    ).stdout


def test_git_text(repo: Path, *arguments: str, input_data: bytes | None = None) -> str:
    return test_git(repo, *arguments, input_data=input_data).decode("utf-8", errors="strict").strip()


def initialize_test_git_repo(parent: Path) -> tuple[Path, str]:
    repo = parent / "repo"
    repo.mkdir()
    test_git(repo, "init", "-q", "--initial-branch=main")
    (repo / "seed.txt").write_bytes(b"seed")
    return repo, commit_test_git_repo(repo, "base")


def commit_test_git_repo(repo: Path, message: str) -> str:
    test_git(repo, "add", "-A")
    test_git(
        repo,
        "-c",
        "user.name=Dora Validator Test",
        "-c",
        "user.email=dora-validator@example.invalid",
        "commit",
        "-q",
        "-m",
        message,
    )
    return test_git_text(repo, "rev-parse", "HEAD")


def expect_real_git_protected_path_rejection(
    name: str,
    changes: dict[str, list[str]],
    expected_layer: str,
    expected_path: str,
) -> None:
    require(expected_path in changes[expected_layer], f"Real-Git collector missed {name}: {changes}")
    try:
        validate_post_merge_protected_paths(changes)
    except ValueError:
        print(f"PASS negative real-git-{name}")
        return
    raise ValueError(f"Real-Git protected path mutation unexpectedly passed: {name}")


def run_git_path_collector_tests() -> None:
    unicode_relative = "android/poc/recovery/данные.bin"
    for layer in ("committed", "staged", "unstaged", "untracked"):
        with tempfile.TemporaryDirectory(prefix=f"dora-recovery-path-{layer}-") as temporary:
            repo, anchor = initialize_test_git_repo(Path(temporary))
            path = repo / Path(unicode_relative)
            path.parent.mkdir(parents=True)
            if layer == "committed":
                path.write_bytes(b"committed")
                commit_test_git_repo(repo, "committed unicode protected path")
            elif layer == "staged":
                path.write_bytes(b"staged")
                test_git(repo, "add", "--", unicode_relative)
            elif layer == "unstaged":
                path.write_bytes(b"original")
                anchor = commit_test_git_repo(repo, "track unicode protected path")
                path.write_bytes(b"unstaged")
            else:
                path.write_bytes(b"untracked")
            changes = collect_post_merge_changes(root=repo, merged_anchor=anchor)
            expect_real_git_protected_path_rejection(
                f"unicode-{layer}", changes, layer, unicode_relative
            )

    with tempfile.TemporaryDirectory(prefix="dora-recovery-path-control-") as temporary:
        repo, anchor = initialize_test_git_repo(Path(temporary))
        control_relative = "android/poc/recovery/control\nname.bin"
        blob = test_git_text(repo, "hash-object", "-w", "--stdin", input_data=b"control")
        control_name = control_relative.rsplit("/", 1)[1]
        recovery_tree = test_git_text(
            repo,
            "mktree",
            "-z",
            input_data=f"100644 blob {blob}\t".encode("ascii") + control_name.encode("utf-8") + b"\0",
        )
        poc_tree = test_git_text(
            repo,
            "mktree",
            "-z",
            input_data=f"040000 tree {recovery_tree}\trecovery\0".encode("ascii"),
        )
        android_tree = test_git_text(
            repo,
            "mktree",
            "-z",
            input_data=f"040000 tree {poc_tree}\tpoc\0".encode("ascii"),
        )
        tree = test_git_text(
            repo,
            "mktree",
            "-z",
            input_data=f"040000 tree {android_tree}\tandroid\0".encode("ascii"),
        )
        commit = test_git_text(
            repo,
            "-c",
            "user.name=Dora Validator Test",
            "-c",
            "user.email=dora-validator@example.invalid",
            "commit-tree",
            tree,
            "-p",
            anchor,
            input_data=b"control-character path\n",
        )
        test_git(repo, "update-ref", "refs/heads/main", commit)
        changes = collect_post_merge_changes(root=repo, merged_anchor=anchor)
        expect_real_git_protected_path_rejection(
            "control-character-committed-tree", changes, "committed", control_relative
        )

    for change_kind in ("delete", "rename", "type-change"):
        with tempfile.TemporaryDirectory(prefix=f"dora-recovery-path-{change_kind}-") as temporary:
            repo, _initial = initialize_test_git_repo(Path(temporary))
            relative = "android/poc/recovery/payload.bin"
            path = repo / Path(relative)
            path.parent.mkdir(parents=True)
            path.write_bytes(b"payload")
            anchor = commit_test_git_repo(repo, "protected payload base")
            if change_kind == "delete":
                path.unlink()
                commit_test_git_repo(repo, "delete protected payload")
            elif change_kind == "rename":
                destination = repo / "docs" / "renamed-payload.bin"
                destination.parent.mkdir()
                test_git(repo, "mv", "--", relative, destination.relative_to(repo).as_posix())
                commit_test_git_repo(repo, "rename protected payload")
            else:
                link_blob = test_git_text(repo, "hash-object", "-w", "--stdin", input_data=b"target")
                test_git(repo, "update-index", "--cacheinfo", f"120000,{link_blob},{relative}")
                tree = test_git_text(repo, "write-tree")
                commit = test_git_text(
                    repo,
                    "-c",
                    "user.name=Dora Validator Test",
                    "-c",
                    "user.email=dora-validator@example.invalid",
                    "commit-tree",
                    tree,
                    "-p",
                    anchor,
                    input_data=b"type change\n",
                )
                test_git(repo, "update-ref", "refs/heads/main", commit)
            changes = collect_post_merge_changes(root=repo, merged_anchor=anchor)
            expect_real_git_protected_path_rejection(
                f"{change_kind}-preserved", changes, "committed", relative
            )

    try:
        decode_git_path_records(b"android/poc/recovery/\xff\0", "synthetic invalid UTF-8")
    except ValueError:
        print("PASS negative git-path-invalid-utf8-fails-closed")
    else:
        raise ValueError("Invalid UTF-8 Git path output unexpectedly passed")

    with tempfile.TemporaryDirectory(prefix="dora-recovery-path-git-error-") as temporary:
        repo, _anchor = initialize_test_git_repo(Path(temporary))
        try:
            git_path_records("diff", "--name-only", "-z", "missing-revision", "--", root=repo)
        except subprocess.CalledProcessError:
            print("PASS negative git-path-command-error-fails-closed")
        else:
            raise ValueError("Failed Git path command unexpectedly passed")


def write_test_pull_request_event(
    event_path: Path,
    *,
    number: int,
    head_ref: str,
    head_sha: str,
    base_sha: str,
    merge_sha: str | None,
    head_repository: str = GITHUB_REPOSITORY,
) -> None:
    event = {
        "number": number,
        "repository": {"full_name": GITHUB_REPOSITORY},
        "pull_request": {
            "number": number,
            "merge_commit_sha": merge_sha,
            "head": {
                "ref": head_ref,
                "sha": head_sha,
                "repo": {"full_name": head_repository},
            },
            "base": {
                "ref": GITHUB_BASE_BRANCH,
                "sha": base_sha,
                "repo": {"full_name": GITHUB_REPOSITORY},
            },
        },
    }
    event_path.write_text(json.dumps(event, ensure_ascii=False), encoding="utf-8")


def run_github_pull_request_context_tests() -> None:
    with tempfile.TemporaryDirectory(prefix="dora-recovery-pr-context-") as temporary:
        parent = Path(temporary)
        repo, base = initialize_test_git_repo(parent)
        authorized_relative = "tools/validate_poc_recovery_governance.py"
        authorized_path = repo / Path(authorized_relative)
        authorized_path.parent.mkdir()
        authorized_path.write_bytes(b"change")
        pull_request_head = commit_test_git_repo(repo, "pull request head")
        head_tree = test_git_text(repo, "rev-parse", f"{pull_request_head}^{{tree}}")
        merge = test_git_text(
            repo,
            "-c",
            "user.name=Dora Validator Test",
            "-c",
            "user.email=dora-validator@example.invalid",
            "commit-tree",
            head_tree,
            "-p",
            base,
            "-p",
            pull_request_head,
            input_data=b"synthetic GitHub merge ref\n",
        )
        test_git(repo, "checkout", "--detach", "-q", merge)

        runner_temp = parent / "runner-temp"
        runner_temp.mkdir()
        event_path = runner_temp / "event.json"
        number = 15

        def environment(head_ref: str, path: Path = event_path) -> dict[str, str]:
            return {
                "GITHUB_EVENT_NAME": "pull_request",
                "GITHUB_REPOSITORY": GITHUB_REPOSITORY,
                "GITHUB_WORKSPACE": str(repo.resolve()),
                "RUNNER_TEMP": str(runner_temp.resolve()),
                "GITHUB_EVENT_PATH": str(path.resolve()),
                "GITHUB_HEAD_REF": head_ref,
                "GITHUB_BASE_REF": GITHUB_BASE_BRANCH,
                "GITHUB_REF": f"refs/pull/{number}/merge",
                "GITHUB_SHA": merge,
            }

        write_test_pull_request_event(
            event_path,
            number=number,
            head_ref=AUTHORIZED_BRANCH,
            head_sha=pull_request_head,
            base_sha=base,
            merge_sha=merge,
        )
        context = collect_github_pull_request_context(
            merge,
            AUTHORIZED_BRANCH,
            environ=environment(AUTHORIZED_BRANCH),
            root=repo,
        )
        require(
            select_lifecycle_branch("", AUTHORIZED_BRANCH, context) == AUTHORIZED_BRANCH,
            "Verified historical pull_request context did not select the authorized branch",
        )
        print("PASS positive github-historical-authorized-pr-merge-ref")

        remediation_branch = "codex/recovery-i1-post-merge-validator-remediation"
        write_test_pull_request_event(
            event_path,
            number=number,
            head_ref=remediation_branch,
            head_sha=pull_request_head,
            base_sha=base,
            merge_sha=None,
        )
        remediation_context = collect_github_pull_request_context(
            merge,
            remediation_branch,
            environ=environment(remediation_branch),
            root=repo,
        )
        require(
            select_lifecycle_branch("", remediation_branch, remediation_context) == remediation_branch,
            "Verified post-merge remediation pull_request context was rejected",
        )
        print("PASS positive github-current-post-merge-pr-merge-ref")

        write_test_pull_request_event(
            event_path,
            number=number,
            head_ref=AUTHORIZED_BRANCH,
            head_sha=pull_request_head,
            base_sha=base,
            merge_sha="not-a-commit-sha",
        )
        try:
            collect_github_pull_request_context(
                merge,
                AUTHORIZED_BRANCH,
                environ=environment(AUTHORIZED_BRANCH),
                root=repo,
            )
        except ValueError:
            print("PASS negative github-malformed-event-merge-sha")
        else:
            raise ValueError("Malformed GitHub event merge SHA unexpectedly passed")

        write_test_pull_request_event(
            event_path,
            number=number,
            head_ref=AUTHORIZED_BRANCH,
            head_sha=pull_request_head,
            base_sha=base,
            merge_sha=merge,
            head_repository="untrusted-fork/DORA",
        )
        try:
            collect_github_pull_request_context(
                merge,
                AUTHORIZED_BRANCH,
                environ=environment(AUTHORIZED_BRANCH),
                root=repo,
            )
        except ValueError:
            print("PASS negative github-same-name-fork")
        else:
            raise ValueError("Same-name fork pull_request context unexpectedly passed")

        write_test_pull_request_event(
            event_path,
            number=number,
            head_ref=AUTHORIZED_BRANCH,
            head_sha=base,
            base_sha=base,
            merge_sha=merge,
        )
        try:
            collect_github_pull_request_context(
                merge,
                AUTHORIZED_BRANCH,
                environ=environment(AUTHORIZED_BRANCH),
                root=repo,
            )
        except ValueError:
            print("PASS negative github-merge-ref-topology-mismatch")
        else:
            raise ValueError("Mismatched GitHub merge-ref topology unexpectedly passed")

        escaped_event = parent / "escaped-event.json"
        write_test_pull_request_event(
            escaped_event,
            number=number,
            head_ref=AUTHORIZED_BRANCH,
            head_sha=pull_request_head,
            base_sha=base,
            merge_sha=merge,
        )
        try:
            collect_github_pull_request_context(
                merge,
                AUTHORIZED_BRANCH,
                environ=environment(AUTHORIZED_BRANCH, escaped_event),
                root=repo,
            )
        except ValueError:
            print("PASS negative github-event-path-escape")
        else:
            raise ValueError("Escaped GitHub event path unexpectedly passed")


def run_negative_tests() -> None:
    def key04(protocol: dict[str, Any]) -> dict[str, Any]:
        return next(row for row in effective_rows(protocol) if row["id"] == "KEY-04")

    def kcf07(protocol: dict[str, Any]) -> dict[str, Any]:
        return next(row for row in effective_rows(protocol) if row["id"] == "KCF-07")

    tests: list[tuple[str, Callable[[dict[str, Any], dict[str, Any]], None]]] = [
        ("key04-successful-decrypt", lambda _g, p: key04(p).__setitem__("successfulDecryptAllowed", True)),
        ("key04-post-decrypt-parser-mismatch", lambda _g, p: key04(p).__setitem__("postDecryptParserOrPlaintextMismatchAllowed", True)),
        ("key04-non-auth-decrypt-outcome", lambda _g, p: key04(p).__setitem__("decryptOutcome", "SUCCESS_OR_AUTH_FAILURE")),
        ("key04-classification-alternative", lambda _g, p: key04(p).__setitem__("expectedClassification", "CORRUPT_KEY_CONFIRMATION")),
        ("kcf07-malformed-plaintext-classification", lambda _g, p: kcf07(p).__setitem__("expectedClassification", "KEY_UNAVAILABLE_KEY_MISMATCH")),
        ("kcf07-requires-successful-decrypt", lambda _g, p: kcf07(p).__setitem__("requiredObservation", "decrypt fails")),
        ("kcf07-malformed-plaintext-coverage", lambda _g, p: kcf07(p)["coveredPostDecryptFailures"].pop(0)),
        ("active-matrix-count", lambda _g, p: effective_rows(p).pop()),
        ("active-matrix-unique-ids", lambda _g, p: effective_rows(p)[1].__setitem__("id", "COR-01")),
        ("active-matrix-key04-once", lambda _g, p: effective_rows(p)[13].__setitem__("id", "KEY-04")),
        ("active-matrix-effective-rows", lambda _g, p: effective_rows(p)[0].__setitem__("effective", False)),
        ("phase-a-count", lambda g, _p: g["faultCampaignProfiles"]["phaseA"].__setitem__("phaseATotalInjections", 183)),
        ("full-physical-count", lambda g, _p: g["faultCampaignProfiles"]["fullPhysicalCampaign"].__setitem__("fullPhysicalTotalInjections", 137)),
    ]
    for name, mutation in tests:
        expect_negative(name, mutation)

    gate = read_json(GATE_PATH)
    protocol = read_json(PROTOCOL_PATH)
    mutated = copy.deepcopy(gate)
    mutated["retainedAuditArtifacts"][4]["protocolSha256"] = "0" * 64
    try:
        validate_immutable_history(mutated, protocol)
    except ValueError:
        print("PASS negative historical-v0.1-v0.5-hash-pin")
    else:
        raise ValueError("Negative test unexpectedly passed: historical-v0.1-v0.5-hash-pin")

    post_merge_tests: list[tuple[str, Callable[[dict[str, Any]], None]]] = [
        ("post-merge-rereview-formal-authority", lambda record: record["advisoryReReview"].__setitem__("formalReviewer", True)),
        ("post-merge-rereview-closes-rec-rdy-02", lambda record: record["advisoryReReview"].__setitem__("closesRecRdy02", True)),
        ("post-merge-implementation-authority", lambda record: record["readinessBoundary"].__setitem__("implementationAllowed", True)),
    ]
    for name, mutation in post_merge_tests:
        record = read_json(POST_MERGE_EVIDENCE_PATH)
        mutation(record)
        try:
            validate_post_merge_evidence(record)
        except (ValueError, KeyError):
            print(f"PASS negative {name}")
        else:
            raise ValueError(f"Negative test unexpectedly passed: {name}")

    formal_tests: list[tuple[str, Callable[[dict[str, Any]], None]]] = [
        ("formal-review-formalReviewer-false", lambda record: record.__setitem__("formalReviewer", False)),
        ("formal-review-missing-reviewer-name", lambda record: record["reviewer"].__setitem__("name", "")),
        ("formal-review-reviewer-equals-package-author", lambda record: record["reviewer"].__setitem__("name", "Codex")),
        ("formal-review-wrong-reviewed-target", lambda record: record["reviewedTarget"].__setitem__("packageCommit", "0" * 40)),
        ("formal-review-wrong-capacity", lambda record: record["reviewer"].__setitem__("capacity", "authorized representative of Rambus")),
        ("formal-review-false-rambus-corporate-approval", lambda record: record["reviewer"].__setitem__("rambusCorporateApprovalClaimed", True)),
        ("formal-review-non-accept-answer", lambda record: record["responses"][5].__setitem__("response", "CHANGES_REQUIRED")),
        ("formal-review-wrong-disposition", lambda record: record.__setitem__("disposition", "APPROVE_FOR_IMPLEMENTATION")),
        ("formal-review-missing-public-consent", lambda record: record["publicRecordConsent"].__setitem__("consented", False)),
        ("formal-review-advisory-dossier-closes-rec-rdy-02", lambda record: record["advisoryDossier"].__setitem__("closesRecRdy02", True)),
    ]
    for field in ("implementationAllowed", "implementationAllowedByThisPackage", "executionAllowed", "measuredExecutionAllowed"):
        formal_tests.append((f"formal-review-authority-{field}-true", lambda record, key=field: record["authorityBoundary"].__setitem__(key, True)))
    for name, mutation in formal_tests:
        record = read_json(FORMAL_REVIEW_PATH)
        mutation(record)
        try:
            validate_formal_human_review(record)
        except (ValueError, KeyError):
            print(f"PASS negative {name}")
        else:
            raise ValueError(f"Negative test unexpectedly passed: {name}")

    historical = read_json(HISTORICAL_ADVISORY_LEDGER_PATH)
    historical["review"]["formalReviewer"] = True
    try:
        validate_historical_advisory_ledger(historical)
    except (ValueError, KeyError):
        print("PASS negative historical-advisory-record-modified")
    else:
        raise ValueError("Negative test unexpectedly passed: historical-advisory-record-modified")

    formal_ledger = read_json(FORMAL_FINDINGS_LEDGER_PATH)
    formal_ledger["sourceLedgerUnchanged"] = False
    try:
        validate_formal_findings_ledger(formal_ledger)
    except (ValueError, KeyError):
        print("PASS negative historical-advisory-source-rewritten")
    else:
        raise ValueError("Negative test unexpectedly passed: historical-advisory-source-rewritten")

    authorization_negative_tests: list[tuple[str, Callable[[dict[str, Any]], None]]] = [
        ("authorization-tampered", lambda record: record.__setitem__("status", "AUTHORIZED")),
        ("authorization-base-mismatch", lambda record: record["base"].__setitem__("commit", "0" * 40)),
        ("authorization-hash-mismatch", lambda record: record["contract"].__setitem__("protocolJsonSha256", "0" * 64)),
        ("authorization-flag-escalation", lambda record: record["authorityBoundary"].__setitem__("executionAllowed", True)),
    ]
    try:
        validate_authorization_record(None)
    except ValueError:
        print("PASS negative authorization-missing")
    else:
        raise ValueError("Negative test unexpectedly passed: authorization-missing")
    for name, mutation in authorization_negative_tests:
        record = read_json(AUTHORIZATION_PATH)
        mutation(record)
        try:
            validate_authorization_record(record)
        except (ValueError, KeyError):
            print(f"PASS negative {name}")
        else:
            raise ValueError(f"Negative test unexpectedly passed: {name}")

    pure_boundary_negative_tests: list[tuple[str, Callable[[], None]]] = [
        (
            "authorization-forbidden-dependency",
            lambda: validate_recovery_build_text(
                read_text("android/poc/recovery/build.gradle.kts")
                + '\ndependencies { implementation("com.google.crypto.tink:tink-android:1.23.0") }\n'
            ),
        ),
        (
            "authorization-forbidden-component",
            lambda: validate_manifest_text(
                '<?xml version="1.0" encoding="utf-8"?><manifest><application><activity /></application></manifest>'
            ),
        ),
        ("authorization-forbidden-path", lambda: validate_changed_paths(["android/app/build.gradle.kts"])),
        (
            "authorization-forbidden-android-import",
            lambda: validate_production_source_text("package invalid\nimport android.content.Context\n", "synthetic.kt"),
        ),
        (
            "authorization-forbidden-harness",
            lambda: validate_production_source_text("package invalid\nobject RecoveryHarness\n", "synthetic.kt"),
        ),
    ]
    for name, operation in pure_boundary_negative_tests:
        try:
            operation()
        except (ValueError, ET.ParseError):
            print(f"PASS negative {name}")
        else:
            raise ValueError(f"Negative test unexpectedly passed: {name}")


def main() -> int:
    gate = read_json(GATE_PATH)
    protocol = read_json(PROTOCOL_PATH)
    validate_all(gate, protocol)
    validate_readiness_and_evidence(gate)
    validate_dependency_and_scope_boundary()
    validate_active_metadata()
    if "--self-test" in sys.argv[1:]:
        run_lifecycle_tests()
        run_git_path_collector_tests()
        run_github_pull_request_context_tests()
        run_negative_tests()
    print(
        "POC-RECOVERY-001 governance v0.6 validation passed; 15 v0.1-v0.5 audit artifacts immutable, "
        "46 unique effective rows with one KEY-04, KEY-04 authentication/AAD failure only -> "
        "KEY_UNAVAILABLE_KEY_MISMATCH, KCF-07 successful-decrypt malformed plaintext -> "
        "CORRUPT_KEY_CONFIRMATION, Phase A=184, full physical=138, hard kills=120/candidate separate, "
        "formal accountable review complete with REC-RDY-02 closed, exact REC-I1 authorization and pure module valid, "
        "10 current blockers remain, implementationAllowed=false, "
        "implementationAllowedByThisPackage=false, executionAllowed=false, measuredExecutionAllowed=false"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, StopIteration, json.JSONDecodeError, subprocess.CalledProcessError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
