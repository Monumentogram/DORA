#!/usr/bin/env python3
"""Fail-closed validation for v0.6 governance and reviewed Recovery payloads."""

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
REC_I2B_PROFILE = "REC-I2B_REVIEWED_SUCCESSOR"
REC_I2B_MERGED_MAIN_PROFILE = "REC-I2B_SQUASH_MERGED_MAIN_SUCCESSOR"
REC_I2B_MERGED_MAIN_ANCHOR = "a7e23c9a2758a3ee2cc8aba26be397b07ffc8f5b"
REC_I2B_MERGED_MAIN_TREE = "4b07b00b247decfed3b1bd6155ca9bc98701a196"
REC_I2B_MERGED_MAIN_PARENT = "e48305ee3b2d18a5612e14aa6cbd4c1c289de9c7"
REC_I2B_MERGED_MAIN_REMEDIATION_AUTHORIZATION = (
    "REC-I2B-POST-MERGE-MAIN-SUCCESSOR-DISPATCH-REMEDIATION-AUTH-20260819-01"
)
REC_I2B_MERGED_MAIN_REMEDIATION_BRANCH = "codex/rec-i2b-main-successor-dispatch"
REC_I2B_MERGED_MAIN_VALIDATOR_PATH = "tools/validate_poc_recovery_governance.py"
REC_I2B_BRANCH = "codex/poc-recovery-i2a-graph-probe"
REC_I2B_PULL_REQUEST_NUMBER = 38
REC_I2B_RUNTIME_IMPLEMENTATION_HEAD = "dca745929b59b656dfa8bb210884e3c1c4bdad0f"
REC_I2B_RUNTIME_IMPLEMENTATION_TREE = "482f0e57ad3ce9aba2b91d3cd8a5e21092757e9f"
REC_I2B_RUNTIME_IMPLEMENTATION_PARENT = "ed9a6e72578f06b4712250cfcc034dc1851ff23f"
REC_I2B_RUNTIME_EVIDENCE_HEAD = "6eb055d9fdb83445ec1dcf27898bba07af2d4951"
REC_I2B_RUNTIME_EVIDENCE_TREE = "5cb9357700a9868c70c21ff987c8261882deaa1b"
REC_I2B_RUNTIME_CLOSURE_HEAD = "87a45d69b4a35854f7904895bf8e10968265983b"
REC_I2B_RUNTIME_CLOSURE_TREE = "dd1173626b73413fb9077ebf9046c11ba26496ac"
REC_I2B_METADATA_IMPLEMENTATION_HEAD = "c194fec9ecce5ee77ffca0c162d2b78c1c58b715"
REC_I2B_METADATA_IMPLEMENTATION_TREE = "d6f84aeef27dab1e1de5c77e9722259c40b9653c"
REC_I2B_METADATA_IMPLEMENTATION_PARENT = "c65fe0718f082aec97a38d2cc567e1d56c6da8d1"
REC_I2B_METADATA_EVIDENCE_HEAD = "5eaadeb77c444bf0af2afadc82e1804a7c71bc43"
REC_I2B_METADATA_EVIDENCE_TREE = "4524cdf21ea9c22d5d8b9af6b4a4864c800d50b0"
REC_I2B_METADATA_CLOSURE_HEAD = "9bb64e58caa1a83e7b4bf4ac58a5a5077a986a57"
REC_I2B_METADATA_CLOSURE_TREE = "eb5d9d0873d4c6b23ed11abd55859286076b460c"
REC_I2B_MODULE_TREE = "c92d921b376cf17d67dae1af82e37a7931a3897d"
REC_I2A_DISPOSITION_PATH = (
    "docs/evidence/poc-recovery-001/rec-i2a-actual-graph-product-ip-disposition-2026-08-17.json"
)
REC_I2A_DISPOSITION_SHA256 = "106060feb7b3afe634c2e3698e04f83fea2165a21d50a7168dab95dc058bc804"
REC_I2B_RUNTIME_EVIDENCE_PATH = (
    "docs/evidence/poc-recovery-001/rec-i2b-runtime-crypto-implementation-evidence-2026-08-17.json"
)
REC_I2B_ACCOUNTABLE_PACKET_PATH = (
    "docs/evidence/poc-recovery-001/rec-i2b-accountable-engineering-security-review-packet-2026-08-17.json"
)
REC_I2B_METADATA_CLOSURE_PATH = (
    "docs/evidence/poc-recovery-001/reviews/"
    "rec-i2b-fresh-cache-metadata-independent-delta-review-closure-2026-08-18.json"
)
REC_I2B_METADATA_CLOSURE_SHA256 = "fc0c5a9f759eb836bbc81f2a64cc55a034786465a8473679f244528a3e17e714"
REC_I2B_CLAIM_CEILING = "IMPLEMENTED_AND_LOCALLY_VERIFIED_PENDING_ACCOUNTABLE_ENGINEERING_SECURITY_REVIEW"
REC_I2B_ACCOUNTABLE_CLAIM_CEILING = (
    "ACCOUNTABLE_ENGINEERING_SECURITY_REVIEW_COMPLETE_PENDING_SEPARATE_REC_I3_ACTIVATION_DECISION"
)
REC_I2B_ACCOUNTABLE_STATUS = REC_I2B_ACCOUNTABLE_CLAIM_CEILING
REC_I2B_ACCOUNTABLE_RECORDING_BASE_HEAD = "f64c5c150865901352889b607a075425c93e6cb4"
REC_I2B_ACCOUNTABLE_RECORDING_BASE_TREE = "389b43a20381fdfd4a86f8328dba05439d4bf5f6"
REC_I2B_ACCOUNTABLE_CLOSURE_AUTHORIZATION = (
    "REC-I2B-ACCOUNTABLE-HUMAN-REVIEW-SANITIZED-CLOSURE-AUTH-20260818-01"
)
REC_I2B_ACCOUNTABLE_VALIDATOR_AUTHORIZATION = (
    "REC-I2B-ACCOUNTABLE-HUMAN-REVIEW-VALIDATOR-PHASE-AUTH-20260818-01"
)
REC_I2B_ACCOUNTABLE_CLOSURE_PATH = (
    "docs/evidence/poc-recovery-001/reviews/"
    "rec-i2b-accountable-human-engineering-security-review-closure-2026-08-18.json"
)
REC_I2B_ACCOUNTABLE_CLOSURE_BYTES = 5045
REC_I2B_ACCOUNTABLE_CLOSURE_SHA256 = (
    "999bd50d7d913a9d299514d132df96d1ee22034ddc810a56503875774f006848"
)
REC_I2B_ACCOUNTABLE_REVIEW_DATE = "2026-08-18"
REC_I2B_ACCOUNTABLE_RECORDED_AT = "2026-08-18T22:54:06+03:00"
REC_I2B_ACCOUNTABLE_DISPOSITION = (
    "APPROVE_EXACT_REC_I2B_FOR_SEPARATE_REC_I3_ACTIVATION_DECISION"
)
REC_I2B_ACCOUNTABLE_METADATA_BASE_HEAD = "50bb75d1069d72e843d2c253afabd6c2d28b78e1"
REC_I2B_ACCOUNTABLE_METADATA_BASE_TREE = "5a7bdec7b6117286778429065540daa124bd64f2"
REC_I2B_ACCOUNTABLE_INTEGRATION_AUTHORIZATION = (
    "REC-I2B-ACCOUNTABLE-CLOSURE-INTEGRATION-AUTH-20260818-01"
)
REC_I2B_ACCOUNTABLE_METADATA_CLOSURE_AUTHORIZATION = (
    "REC-I2B-ACCOUNTABLE-INDEPENDENT-METADATA-CLOSURE-AUTH-20260818-02"
)
REC_I2B_ACCOUNTABLE_METADATA_CLOSURE_PATH = (
    "docs/evidence/poc-recovery-001/reviews/"
    "rec-i2b-accountable-closure-independent-metadata-review-closure-2026-08-18.json"
)
REC_I2B_ACCOUNTABLE_METADATA_CLOSURE_BYTES = 7439
REC_I2B_ACCOUNTABLE_METADATA_CLOSURE_SHA256 = (
    "b08bc7d3725b190b8be42dc14349db4beca70286f3e9597820d036283f1ca22e"
)
REC_I2B_ACCOUNTABLE_METADATA_CLOSURE_SEMANTIC_SHA256 = (
    "107278d1cd261d56ef33ab56aa8ec285b2984d7810a21e778337f7f98fd3c021"
)
REC_I2B_ACCOUNTABLE_METADATA_RECORDED_AT = "2026-08-18T23:30:49+03:00"
REC_I2B_ACCOUNTABLE_METADATA_CLEAN_REMEDIATION_STATE = (
    "ACCOUNTABLE_HUMAN_REVIEW_SANITIZED_CLOSURE_INDEPENDENT_METADATA_CLEAN_"
    "DRAFT_PR38_INTEGRATION_PENDING"
)
REC_I2B_SUCCESSOR_REMEDIATION_AUTHORIZATION = (
    "REC-I2B-REVIEWED-SUCCESSOR-VALIDATOR-AND-CI-METADATA-REMEDIATION-AUTH-20260818-01"
)
REC_I2B_SUCCESSOR_JAR_METADATA_AUTHORIZATION = (
    "REC-I2B-REVIEWED-SUCCESSOR-CI-JAR-METADATA-REMEDIATION-AUTH-20260818-02"
)
REC_I2B_SUCCESSOR_SPOTLESS_METADATA_AUTHORIZATION = (
    "REC-I2B-REVIEWED-SUCCESSOR-SPOTLESS-METADATA-REMEDIATION-AUTH-20260818-03"
)
REC_I2B_SUCCESSOR_SELFTEST_REMEDIATION_AUTHORIZATION = (
    "REC-I2B-REVIEWED-SUCCESSOR-EVIDENCE-PHASE-SELFTEST-REMEDIATION-AUTH-20260818-04"
)
REC_I2B_AAPT2_LINUX_METADATA_REMEDIATION_AUTHORIZATION = (
    "REC-I2B-AAPT2-LINUX-VERIFICATION-METADATA-REMEDIATION-AUTH-20260818-05"
)
REC_I2B_AAPT2_VALIDATOR_PIN_CLARIFICATION = (
    "REC-I2B-AUTH05-VALIDATOR-PIN-BINDING-CLARIFICATION-20260818-01"
)
REC_I2B_SUCCESSOR_INITIAL_IMPLEMENTATION_HEAD = "fa87d87abd3d1ddad2ee0a3c1669127fc288b4f8"
REC_I2B_SUCCESSOR_INITIAL_IMPLEMENTATION_TREE = "7c1add0f5ee3cad3463a22a636c16e0c2b322f5e"
REC_I2B_SUCCESSOR_EVIDENCE_PHASE_IMPLEMENTATION_HEAD = (
    "3195c2fa79db6360a5844a8ce02ea419a74e2982"
)
REC_I2B_SUCCESSOR_EVIDENCE_PHASE_IMPLEMENTATION_TREE = (
    "00b04684d7ceaac7685070e783c37e1178131b7b"
)
REC_I2B_SUCCESSOR_REVIEWED_EVIDENCE_HEAD = "b642c5206e2e9fc3493dbb78b801a371f2dd0868"
REC_I2B_SUCCESSOR_REVIEWED_EVIDENCE_TREE = "b9ebf29a654d3cb218289e8c23cdaffa03e36fb5"
REC_I2B_SUCCESSOR_REVIEW_CLOSURE_HEAD = "e4a9b55c81c529ef1eee6dcd27a76046dc0a21ef"
REC_I2B_SUCCESSOR_REVIEW_CLOSURE_TREE = "cc46703736072311982fe9a02476b90f0544432d"
REC_I2B_SUCCESSOR_IMPLEMENTATION_PATHS = (
    "android/gradle/verification-metadata.xml",
    "tools/validate_poc_recovery_governance.py",
    "tools/verify_poc_recovery_dependency_inventory.py",
)
REC_I2B_SUCCESSOR_HISTORICAL_METADATA_CANONICAL_SHA256 = (
    "35046c96e293d56cd36ba5894c0fd286cf6e96167df5a3d6e6cd9c3de379f3ac"
)
REC_I2B_AAPT2_METADATA_CANONICAL_SHA256 = (
    "61f71c6652faec73063a200bedcfa1e6fc07d5f969b426f3365aafd9fc217bb1"
)
REC_I2B_SUCCESSOR_REVIEW_CLOSURE_PATH = (
    "docs/evidence/poc-recovery-001/reviews/"
    "rec-i2b-reviewed-successor-validator-ci-metadata-independent-delta-review-closure-2026-08-18.json"
)
REC_I2B_AAPT2_REVIEW_CLOSURE_PATH = (
    "docs/evidence/poc-recovery-001/reviews/"
    "rec-i2b-aapt2-linux-verification-metadata-independent-delta-review-closure-2026-08-18.json"
)
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
REC_I2B_MERGED_MAIN_PROTECTED_PATHS = [
    "android/poc/recovery/**",
    "docs/evidence/poc-recovery-001/**",
    "android/build.gradle.kts",
    "android/gradle/verification-metadata.xml",
    "tools/verify_poc_recovery_dependency_inventory.py",
]
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
    draft: bool
    state: str
    merged: bool


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


@dataclass(frozen=True)
class PinnedCommitIdentity:
    commit: str | None
    tree: str | None
    parents: tuple[str, ...]
    is_ancestor_of_head: bool


@dataclass(frozen=True)
class RecoveryI2bSuccessorIdentity:
    head: str
    branch: str
    head_module_tree: str | None
    runtime_implementation: PinnedCommitIdentity
    runtime_evidence: PinnedCommitIdentity
    runtime_closure: PinnedCommitIdentity
    metadata_implementation: PinnedCommitIdentity
    metadata_evidence: PinnedCommitIdentity
    metadata_closure: PinnedCommitIdentity
    metadata_closure_is_ancestor_of_pull_request_head: bool
    github_pull_request_context: GitHubPullRequestContext | None = None


@dataclass(frozen=True)
class RecoveryI2bMergedMainIdentity:
    head: str
    branch: str
    merged_anchor_commit: str | None
    merged_anchor_tree: str | None
    merged_anchor_parents: tuple[str, ...]
    merged_anchor_is_ancestor: bool
    head_module_tree: str | None
    remediation_head_merge_base: str | None
    pull_request_base_contains_merged_anchor: bool
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


def git_blob_bytes(revision_path: str, *, root: Path | None = None) -> bytes:
    return subprocess.run(
        ["git", "show", revision_path],
        cwd=root or ROOT,
        check=True,
        capture_output=True,
        text=False,
    ).stdout


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


def path_is_rec_i2b_merged_main_protected(relative: str) -> bool:
    normalized = relative.replace("\\", "/")
    return any(
        normalized.startswith(pattern[:-3] + "/") if pattern.endswith("/**") else fnmatch.fnmatchcase(normalized, pattern)
        for pattern in REC_I2B_MERGED_MAIN_PROTECTED_PATHS
    )


def validate_post_merge_protected_paths(
    changes: dict[str, list[str]],
    *,
    profile: str = "REC-I1",
) -> None:
    expected_layers = {"committed", "staged", "unstaged", "untracked"}
    require(set(changes) == expected_layers, "Incomplete post-merge Recovery payload change inventory")
    protected_changes = {
        layer: sorted({path for path in paths if path_is_post_merge_protected(path)})
        for layer, paths in changes.items()
    }
    protected_changes = {layer: paths for layer, paths in protected_changes.items() if paths}
    message = (
        f"REC-I1 post-merge protected payload differs from merged anchor: {protected_changes}"
        if profile == "REC-I1"
        else f"{profile} protected payload differs from its reviewed anchor: {protected_changes}"
    )
    require(
        not protected_changes,
        message,
    )


def validate_rec_i2b_merged_main_protected_paths(
    changes: dict[str, list[str]],
    *,
    protect_validator: bool = False,
) -> None:
    expected_layers = {"committed", "staged", "unstaged", "untracked"}
    require(set(changes) == expected_layers, "Incomplete REC-I2B squash-main protected change inventory")
    protected_changes = {
        layer: sorted(
            {
                path
                for path in paths
                if path_is_rec_i2b_merged_main_protected(path)
                or (protect_validator and path == REC_I2B_MERGED_MAIN_VALIDATOR_PATH)
            }
        )
        for layer, paths in changes.items()
    }
    protected_changes = {layer: paths for layer, paths in protected_changes.items() if paths}
    require(
        not protected_changes,
        f"REC-I2B squash-main reviewed payload/evidence differs from merged anchor: {protected_changes}",
    )


def validate_rec_i2b_merged_main_remediation_paths(changes: dict[str, list[str]]) -> None:
    expected_layers = {"committed", "staged", "unstaged", "untracked"}
    require(set(changes) == expected_layers, "Incomplete REC-I2B squash-main remediation change inventory")
    changed_paths = sorted({path for paths in changes.values() for path in paths})
    require(
        changed_paths == [REC_I2B_MERGED_MAIN_VALIDATOR_PATH],
        f"REC-I2B squash-main remediation is not the exact single validator-path delta: {changed_paths}",
    )
    validate_rec_i2b_merged_main_protected_paths(changes)


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
        draft = pull_request["draft"]
        state = pull_request["state"]
        merged = pull_request["merged"]
    except (KeyError, TypeError) as error:
        raise ValueError("GitHub pull_request event payload is incomplete") from error

    require(isinstance(number, int) and number > 0 and pull_request_number == number, "GitHub pull_request number mismatch")
    require(isinstance(draft, bool), "GitHub pull_request draft state is invalid")
    require(
        isinstance(state, str) and state in {"open", "closed"},
        "GitHub pull_request state is invalid",
    )
    require(isinstance(merged, bool), "GitHub pull_request merged state is invalid")
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
    # GitHub's pull_request payload may report merge_commit_sha as null or as a
    # stale synthetic merge commit for an open pull request. Treat that field as
    # non-authoritative metadata; GITHUB_SHA, refs/pull/<number>/merge, and the
    # exact base/head parent topology below bind the checkout used by Actions.
    require(
        event_merge_sha is None
        or (isinstance(event_merge_sha, str) and FULL_SHA256_RE.fullmatch(event_merge_sha) is not None),
        "GitHub event merge commit SHA is malformed",
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
        draft=draft,
        state=state,
        merged=merged,
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


def collect_pinned_commit_identity(commit: str, head: str) -> PinnedCommitIdentity:
    resolved = git_optional_output("rev-parse", "--verify", f"{commit}^{{commit}}")
    if resolved is None:
        return PinnedCommitIdentity(None, None, (), False)
    tree = git_optional_output("rev-parse", f"{commit}^{{tree}}")
    parent_text = git_optional_output("show", "-s", "--format=%P", commit)
    parents = tuple(parent_text.split()) if parent_text is not None else ()
    return PinnedCommitIdentity(
        commit=resolved,
        tree=tree,
        parents=parents,
        is_ancestor_of_head=git_is_ancestor(commit, head),
    )


def collect_rec_i2b_merged_main_identity(
    lifecycle: RecoveryLifecycleIdentity | None = None,
) -> RecoveryI2bMergedMainIdentity:
    current = lifecycle or collect_recovery_lifecycle_identity()
    pull_request = current.github_pull_request_context
    merged_anchor_commit = git_optional_output(
        "rev-parse",
        "--verify",
        f"{REC_I2B_MERGED_MAIN_ANCHOR}^{{commit}}",
    )
    merged_anchor_tree = None
    merged_anchor_parents: tuple[str, ...] = ()
    if merged_anchor_commit is not None:
        merged_anchor_tree = git_optional_output("rev-parse", f"{REC_I2B_MERGED_MAIN_ANCHOR}^{{tree}}")
        parents = git_optional_output("show", "-s", "--format=%P", REC_I2B_MERGED_MAIN_ANCHOR)
        merged_anchor_parents = tuple(parents.split()) if parents is not None else ()
    remediation_head_merge_base = (
        git_optional_output("merge-base", REC_I2B_MERGED_MAIN_ANCHOR, pull_request.head_sha)
        if merged_anchor_commit is not None and pull_request is not None
        else None
    )
    pull_request_base_contains_merged_anchor = (
        merged_anchor_commit is not None
        and pull_request is not None
        and git_is_ancestor(REC_I2B_MERGED_MAIN_ANCHOR, pull_request.base_sha)
    )
    return RecoveryI2bMergedMainIdentity(
        head=current.head,
        branch=current.branch,
        merged_anchor_commit=merged_anchor_commit,
        merged_anchor_tree=merged_anchor_tree,
        merged_anchor_parents=merged_anchor_parents,
        merged_anchor_is_ancestor=(
            merged_anchor_commit is not None
            and git_is_ancestor(REC_I2B_MERGED_MAIN_ANCHOR, current.head)
        ),
        head_module_tree=git_optional_output("rev-parse", f"{current.head}:android/poc/recovery"),
        remediation_head_merge_base=remediation_head_merge_base,
        pull_request_base_contains_merged_anchor=pull_request_base_contains_merged_anchor,
        github_pull_request_context=pull_request,
    )


def rec_i2b_merged_main_candidate(identity: RecoveryI2bMergedMainIdentity) -> bool:
    return identity.head == REC_I2B_MERGED_MAIN_ANCHOR or identity.merged_anchor_is_ancestor


def validate_rec_i2b_merged_main_lifecycle(
    identity: RecoveryI2bMergedMainIdentity,
    post_merge_changes: dict[str, list[str]],
) -> str:
    require(
        identity.merged_anchor_commit == REC_I2B_MERGED_MAIN_ANCHOR,
        "REC-I2B squash-main merged anchor is missing or mismatched",
    )
    require(
        identity.merged_anchor_tree == REC_I2B_MERGED_MAIN_TREE,
        "REC-I2B squash-main merged anchor tree mismatch",
    )
    require(
        identity.merged_anchor_parents == (REC_I2B_MERGED_MAIN_PARENT,),
        "REC-I2B squash-main merged anchor parent mismatch",
    )
    require(
        rec_i2b_merged_main_candidate(identity),
        "HEAD is neither the exact REC-I2B squash-main anchor nor its descendant",
    )
    require(
        identity.head_module_tree == REC_I2B_MODULE_TREE,
        "REC-I2B squash-main current module subtree differs from the reviewed source tree",
    )

    pull_request = identity.github_pull_request_context
    if pull_request is not None:
        same_repository_open_main = (
            pull_request.repository == GITHUB_REPOSITORY
            and pull_request.head_repository == GITHUB_REPOSITORY
            and pull_request.base_ref == GITHUB_BASE_BRANCH
            and pull_request.state == "open"
            and pull_request.merged is False
            and identity.branch == pull_request.head_ref
        )
        if pull_request.head_ref == REC_I2B_MERGED_MAIN_REMEDIATION_BRANCH:
            require(
                same_repository_open_main
                and pull_request.base_sha == REC_I2B_MERGED_MAIN_ANCHOR
                and pull_request.draft is True
                and identity.remediation_head_merge_base == REC_I2B_MERGED_MAIN_ANCHOR,
                "REC-I2B squash-main remediation pull_request context is not the exact same-repository Draft/main lineage",
            )
            validate_rec_i2b_merged_main_remediation_paths(post_merge_changes)
            return "rec-i2b-squash-main-remediation-pr"
        require(
            same_repository_open_main and identity.pull_request_base_contains_merged_anchor,
            "REC-I2B squash-main descendant pull_request context is not same-repository protected-main lineage",
        )
        validate_rec_i2b_merged_main_protected_paths(post_merge_changes)
        return "rec-i2b-squash-main-descendant-pr"

    if identity.branch == GITHUB_BASE_BRANCH:
        validate_rec_i2b_merged_main_protected_paths(post_merge_changes)
        return "rec-i2b-squash-main"

    if identity.branch == REC_I2B_MERGED_MAIN_REMEDIATION_BRANCH:
        require(
            REC_I2B_MERGED_MAIN_REMEDIATION_AUTHORIZATION
            == "REC-I2B-POST-MERGE-MAIN-SUCCESSOR-DISPATCH-REMEDIATION-AUTH-20260819-01",
            "REC-I2B squash-main remediation authority drift",
        )
        validate_rec_i2b_merged_main_remediation_paths(post_merge_changes)
        return "rec-i2b-squash-main-remediation-local"
    validate_rec_i2b_merged_main_protected_paths(post_merge_changes)
    return "rec-i2b-squash-main-descendant-local"


def collect_rec_i2b_successor_identity(
    lifecycle: RecoveryLifecycleIdentity | None = None,
) -> RecoveryI2bSuccessorIdentity:
    current = lifecycle or collect_recovery_lifecycle_identity()
    pull_request = current.github_pull_request_context
    return RecoveryI2bSuccessorIdentity(
        head=current.head,
        branch=current.branch,
        head_module_tree=git_optional_output("rev-parse", f"{current.head}:android/poc/recovery"),
        runtime_implementation=collect_pinned_commit_identity(REC_I2B_RUNTIME_IMPLEMENTATION_HEAD, current.head),
        runtime_evidence=collect_pinned_commit_identity(REC_I2B_RUNTIME_EVIDENCE_HEAD, current.head),
        runtime_closure=collect_pinned_commit_identity(REC_I2B_RUNTIME_CLOSURE_HEAD, current.head),
        metadata_implementation=collect_pinned_commit_identity(REC_I2B_METADATA_IMPLEMENTATION_HEAD, current.head),
        metadata_evidence=collect_pinned_commit_identity(REC_I2B_METADATA_EVIDENCE_HEAD, current.head),
        metadata_closure=collect_pinned_commit_identity(REC_I2B_METADATA_CLOSURE_HEAD, current.head),
        metadata_closure_is_ancestor_of_pull_request_head=(
            pull_request is not None
            and git_is_ancestor(REC_I2B_METADATA_CLOSURE_HEAD, pull_request.head_sha)
        ),
        github_pull_request_context=pull_request,
    )


def rec_i2b_successor_candidate(identity: RecoveryI2bSuccessorIdentity) -> bool:
    return any(
        item.is_ancestor_of_head
        for item in (
            identity.runtime_implementation,
            identity.runtime_evidence,
            identity.runtime_closure,
            identity.metadata_implementation,
            identity.metadata_evidence,
            identity.metadata_closure,
        )
    )


def validate_pinned_commit_identity(
    identity: PinnedCommitIdentity,
    *,
    expected_commit: str,
    expected_tree: str,
    expected_parents: tuple[str, ...],
    label: str,
) -> None:
    require(identity.commit == expected_commit, f"{label} commit is missing or mismatched")
    require(identity.tree == expected_tree, f"{label} tree mismatch")
    require(identity.parents == expected_parents, f"{label} parent topology mismatch")
    require(identity.is_ancestor_of_head, f"{label} is not an ancestor of HEAD")


def validate_rec_i2b_successor_identity(identity: RecoveryI2bSuccessorIdentity) -> None:
    validate_pinned_commit_identity(
        identity.runtime_implementation,
        expected_commit=REC_I2B_RUNTIME_IMPLEMENTATION_HEAD,
        expected_tree=REC_I2B_RUNTIME_IMPLEMENTATION_TREE,
        expected_parents=(REC_I2B_RUNTIME_IMPLEMENTATION_PARENT,),
        label="REC-I2B reviewed runtime implementation",
    )
    validate_pinned_commit_identity(
        identity.runtime_evidence,
        expected_commit=REC_I2B_RUNTIME_EVIDENCE_HEAD,
        expected_tree=REC_I2B_RUNTIME_EVIDENCE_TREE,
        expected_parents=(REC_I2B_RUNTIME_IMPLEMENTATION_HEAD,),
        label="REC-I2B reviewed runtime evidence",
    )
    validate_pinned_commit_identity(
        identity.runtime_closure,
        expected_commit=REC_I2B_RUNTIME_CLOSURE_HEAD,
        expected_tree=REC_I2B_RUNTIME_CLOSURE_TREE,
        expected_parents=(REC_I2B_RUNTIME_EVIDENCE_HEAD,),
        label="REC-I2B runtime independent closure",
    )
    validate_pinned_commit_identity(
        identity.metadata_implementation,
        expected_commit=REC_I2B_METADATA_IMPLEMENTATION_HEAD,
        expected_tree=REC_I2B_METADATA_IMPLEMENTATION_TREE,
        expected_parents=(REC_I2B_METADATA_IMPLEMENTATION_PARENT,),
        label="REC-I2B reviewed metadata implementation",
    )
    validate_pinned_commit_identity(
        identity.metadata_evidence,
        expected_commit=REC_I2B_METADATA_EVIDENCE_HEAD,
        expected_tree=REC_I2B_METADATA_EVIDENCE_TREE,
        expected_parents=(REC_I2B_METADATA_IMPLEMENTATION_HEAD,),
        label="REC-I2B reviewed metadata evidence",
    )
    validate_pinned_commit_identity(
        identity.metadata_closure,
        expected_commit=REC_I2B_METADATA_CLOSURE_HEAD,
        expected_tree=REC_I2B_METADATA_CLOSURE_TREE,
        expected_parents=(REC_I2B_METADATA_EVIDENCE_HEAD,),
        label="REC-I2B metadata independent closure",
    )
    require(identity.head_module_tree == REC_I2B_MODULE_TREE, "REC-I2B reviewed module subtree mismatch")

    pull_request = identity.github_pull_request_context
    if pull_request is not None:
        require(
            pull_request.repository == GITHUB_REPOSITORY
            and pull_request.head_repository == GITHUB_REPOSITORY
            and pull_request.head_ref == REC_I2B_BRANCH
            and pull_request.base_ref == GITHUB_BASE_BRANCH
            and pull_request.number == REC_I2B_PULL_REQUEST_NUMBER
            and pull_request.draft is True
            and pull_request.state == "open"
            and pull_request.merged is False
            and identity.branch == REC_I2B_BRANCH,
            "REC-I2B pull_request context is not exact same-repository Draft PR 38 lineage",
        )
        require(
            identity.metadata_closure_is_ancestor_of_pull_request_head,
            "REC-I2B metadata closure is present only through the pull_request base or merge ref",
        )
    else:
        require(
            identity.branch in {REC_I2B_BRANCH, GITHUB_BASE_BRANCH},
            f"REC-I2B reviewed successor is running on an unauthorized branch: {identity.branch}",
        )


def validate_rec_i2b_successor_lifecycle(
    identity: RecoveryI2bSuccessorIdentity,
    post_implementation_changes: dict[str, list[str]],
) -> str:
    validate_rec_i2b_successor_identity(identity)
    validate_post_merge_protected_paths(post_implementation_changes, profile=REC_I2B_PROFILE)
    return "rec-i2b-reviewed-successor"


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


def canonical_lf_sha256(relative: str) -> str:
    payload = (ROOT / relative).read_bytes()
    require(not payload.startswith(b"\xef\xbb\xbf"), f"UTF-8 BOM is forbidden: {relative}")
    text = payload.decode("utf-8", errors="strict")
    canonical = text.replace("\r\n", "\n")
    require("\r" not in canonical, f"Bare CR line ending is forbidden: {relative}")
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def rec_i2b_successor_implementation_phase(terminal_commit: str) -> str:
    if terminal_commit in {
        REC_I2B_SUCCESSOR_INITIAL_IMPLEMENTATION_HEAD,
        REC_I2B_SUCCESSOR_EVIDENCE_PHASE_IMPLEMENTATION_HEAD,
    }:
        return "reviewed-successor"
    require(
        git_output("show", "-s", "--format=%P", terminal_commit)
        == REC_I2B_SUCCESSOR_REVIEW_CLOSURE_HEAD,
        "REC-I2B AUTH-05 implementation is not the single direct successor of the reviewed closure",
    )
    changed_paths = git_path_records(
        "diff-tree",
        "--no-commit-id",
        "--name-only",
        "--no-renames",
        "-r",
        "-z",
        terminal_commit,
        "--",
    )
    require(
        sorted(changed_paths)
        == [
            "android/gradle/verification-metadata.xml",
            "tools/validate_poc_recovery_governance.py",
        ],
        "REC-I2B AUTH-05 implementation commit changed paths outside exact metadata/pin scope",
    )
    return "aapt2-linux"


def validate_rec_i2b_successor_metadata_sha256(
    terminal_commit: str,
    actual_sha256: str,
) -> str:
    phase = rec_i2b_successor_implementation_phase(terminal_commit)
    expected_sha256 = (
        REC_I2B_AAPT2_METADATA_CANONICAL_SHA256
        if phase == "aapt2-linux"
        else REC_I2B_SUCCESSOR_HISTORICAL_METADATA_CANONICAL_SHA256
    )
    require(
        actual_sha256 == expected_sha256,
        "REC-I2B reviewed-successor exact phase-bound verification-metadata payload drift",
    )
    return phase


def rec_i2b_expected_authority(*, successor: bool, aapt2_linux: bool = False) -> dict[str, Any]:
    authority: dict[str, Any] = {
        "actualGraphProductIpDisposition": "REC-I2A-ACTUAL-GRAPH-PRODUCT-IP-DISPOSITION-20260817-01",
        "conditionalOwnerAuthorization": "STAGE0-OWNER-UNLOCK-BATCH-20260817-02",
        "technicalDelegation": "STAGE0-TECHNICAL-REMEDIATION-DELEGATION-20260817-01",
        "advisoryRemediationAuthorization": "REC-I2B-ADVISORY-REMEDIATION-AUTH-20260817-01",
        "independentDeltaReviewClosureRecord": "REC-I2B-INDEPENDENT-DELTA-REVIEW-CLOSURE-AUTH-20260817-01",
        "draftPublicationAuthorization": "REC-I2B-DRAFT-PUBLICATION-AUTH-20260817-01",
        "verificationMetadataBomRemediationAuthorization": "REC-I2B-VERIFICATION-METADATA-BOM-REMEDIATION-AUTH-20260817-01",
        "verificationMetadataTransitiveArtifactsRemediationAuthorization": (
            "REC-I2B-VERIFICATION-METADATA-TRANSITIVE-ARTIFACTS-REMEDIATION-AUTH-20260817-02"
        ),
        "verificationMetadataGuava3331PomRemediationAuthorization": (
            "REC-I2B-VERIFICATION-METADATA-GUAVA-33_3_1-POM-REMEDIATION-AUTH-20260817-03"
        ),
        "verificationMetadataFreshCacheClosureAuthorization": (
            "REC-I2B-VERIFICATION-METADATA-FRESH-CACHE-CLOSURE-AUTH-20260817-04"
        ),
        "freshCacheMetadataIndependentDeltaReviewClosureRecord": (
            "REC-I2B-FRESH-CACHE-METADATA-INDEPENDENT-DELTA-REVIEW-CLOSURE-20260818-01"
        ),
        "designCheckpointApproved": True,
        "accountableEngineeringSecurityReviewCompleted": False,
        "runtimeCryptoImplementationAllowed": True,
        "deviceOrEmulatorExecutionAllowed": False,
        "measuredExecutionAllowed": False,
        "harnessOrCampaignAllowed": False,
        "recI3Allowed": False,
        "dependencyAdmissionAllowed": False,
        "productionAdmissionAllowed": False,
        "draftPublicationAuthorizedAndPerformed": True,
        "currentReviewedMetadataClosureDraftPublicationAllowed": not successor,
        "draftPr38NonForcePushAllowed": True,
        "readyOrMergeAllowed": False,
        "mergeAllowed": False,
    }
    if successor:
        authority.update(
            {
                "reviewedSuccessorValidatorAndCiMetadataRemediationAuthorization": (
                    REC_I2B_SUCCESSOR_REMEDIATION_AUTHORIZATION
                ),
                "reviewedSuccessorCiJarMetadataRemediationAuthorization": (
                    REC_I2B_SUCCESSOR_JAR_METADATA_AUTHORIZATION
                ),
                "reviewedSuccessorSpotlessMetadataRemediationAuthorization": (
                    REC_I2B_SUCCESSOR_SPOTLESS_METADATA_AUTHORIZATION
                ),
                "reviewedSuccessorEvidencePhaseSelftestRemediationAuthorization": (
                    REC_I2B_SUCCESSOR_SELFTEST_REMEDIATION_AUTHORIZATION
                ),
                "reviewedSuccessorDraftPr38NonForceUpdateAllowed": True,
            }
        )
    if aapt2_linux:
        authority.update(
            {
                "aapt2LinuxVerificationMetadataRemediationAuthorization": (
                    REC_I2B_AAPT2_LINUX_METADATA_REMEDIATION_AUTHORIZATION
                ),
                "aapt2LinuxValidatorPinBindingClarification": (
                    REC_I2B_AAPT2_VALIDATOR_PIN_CLARIFICATION
                ),
            }
        )
    return authority


def rec_i2b_expected_common_review_truth() -> dict[str, Any]:
    return {
        "priorAdvisoryFindingCounts": {"P0": 0, "P1": 3, "P2": 2},
        "historicalPostRemediationIndependentFindingCounts": {"P0": 0, "P1": 0, "P2": 0},
        "historicalIndependentCleanReviewedImplementationCommit": REC_I2B_RUNTIME_IMPLEMENTATION_HEAD,
        "historicalIndependentCleanReviewedEvidenceCommit": REC_I2B_RUNTIME_EVIDENCE_HEAD,
        "historicalIndependentCleanClosureRecordCommit": REC_I2B_RUNTIME_CLOSURE_HEAD,
        "historicalIndependentCleanReviewCoversCurrentTarget": False,
        "freshGraphVerificationMetadataAdvisoryFindingCounts": {"P0": 0, "P1": 1, "P2": 0},
        "freshGraphVerificationMetadataReviewedImplementationCommit": (
            "adc422236003403d15581c10e31e2dc797ccf9bb"
        ),
        "freshGraphVerificationMetadataReviewedEvidenceCommit": (
            "c65fe0718f082aec97a38d2cc567e1d56c6da8d1"
        ),
        "freshGraphVerificationMetadataDisposition": "CHANGES_REQUIRED",
        "freshGraphVerificationMetadataFindingId": (
            "REC-I2B-FRESH-GRAPH-VERIFICATION-METADATA-P1-001"
        ),
        "freshGraphVerificationMetadataReviewCoversCurrentTarget": False,
    }


def rec_i2b_historical_successor_review_truth() -> dict[str, Any]:
    return {
        "reviewedImplementationCommit": REC_I2B_SUCCESSOR_EVIDENCE_PHASE_IMPLEMENTATION_HEAD,
        "reviewedImplementationTree": REC_I2B_SUCCESSOR_EVIDENCE_PHASE_IMPLEMENTATION_TREE,
        "reviewedEvidenceCommit": REC_I2B_SUCCESSOR_REVIEWED_EVIDENCE_HEAD,
        "reviewedEvidenceTree": REC_I2B_SUCCESSOR_REVIEWED_EVIDENCE_TREE,
        "closureRecord": {
            "path": REC_I2B_SUCCESSOR_REVIEW_CLOSURE_PATH,
            "bytes": 13547,
            "sha256": "678974a985fe49addd7132eb1553e317e4cb9656cd92868567a1dd0a8e7f905a",
        },
        "findingCounts": {"P0": 0, "P1": 0, "P2": 0},
        "disposition": "CLEAN",
        "completed": True,
        "required": False,
        "coversCurrentTarget": False,
        "durableClosureCommit": REC_I2B_SUCCESSOR_REVIEW_CLOSURE_HEAD,
        "durableClosureTree": REC_I2B_SUCCESSOR_REVIEW_CLOSURE_TREE,
    }


def rec_i2b_aapt2_triggering_ci() -> dict[str, Any]:
    return {
        "workflow": "Android CI",
        "runId": 32122399476,
        "url": "https://github.com/Monumentogram/DORA/actions/runs/32122399476",
        "exactHead": REC_I2B_SUCCESSOR_REVIEW_CLOSURE_HEAD,
        "conclusion": "failure",
        "androidBootstrapJob": {
            "jobId": 95665487159,
            "conclusion": "failure",
            "failedStep": "Unit tests, instrumentation compilation, lint, and debug assembly",
        },
        "searchSmokeJob": {
            "jobId": 95665487083,
            "conclusion": "failure",
            "failedStep": (
                "Run Room FTS4 schema, correctness, safety, mapping, and mutation smoke tests"
            ),
            "searchEmulatorStarted": True,
            "recoveryDeviceOrEmulatorExecution": False,
        },
        "missingArtifact": (
            "com.android.tools.build:aapt2:9.3.1-15703166/"
            "aapt2-9.3.1-15703166-linux.jar"
        ),
        "preservedAsHistoricalFailedRun": True,
    }


def rec_i2b_aapt2_local_verification() -> dict[str, Any]:
    return {
        "artifactProof": {
            "coordinate": "com.android.tools.build:aapt2:9.3.1-15703166",
            "artifact": "aapt2-9.3.1-15703166-linux.jar",
            "bytes": 2369543,
            "sha256": "e772a3dae8354764f1b0793903218427f483982445207f2e4ffc8c2026755bd4",
            "sha1": "5287feac13566b9165cdf02c77565e68aec3aad0",
            "isolatedGradleCacheBytesMatchCanonicalGoogleMavenBytes": True,
            "publishedGoogleMavenSha1Matches": True,
        },
        "freshEmptyCacheSearchSmoke": {
            "result": "PASS",
            "durationSeconds": 329.997,
            "configurationCache": "disabled",
            "refreshDependencies": True,
            "missingVerificationArtifactCount": 0,
            "deviceOrEmulatorStarted": False,
        },
        "freshEmptyCacheOwnedConfigurationGraph": {
            "result": "PASS",
            "durationSeconds": 617.241,
            "actionableTasks": 81,
            "executedTasks": 81,
            "configurationCount": 61,
            "policyCoveredConfigurationCount": 34,
            "outsidePolicyBoundaryToolingConfigurationCount": 27,
            "missingVerificationArtifactCount": 0,
        },
        "fullStaticBuildAndUnitBlock": {
            "result": "PASS",
            "durationSeconds": 235.633,
            "actionableTasks": 104,
            "executedTasks": 104,
            "unitTests": "91/91 across 15 suites; zero failures, errors or skips",
        },
        "forcedReleaseR8AndPackage": {
            "result": "PASS",
            "durationSeconds": 163.901,
            "actionableTasks": 46,
            "executedTasks": 46,
        },
        "graphSha256": "3ab12ef698e59f323e71431496d72debd5741669b395b8052ad41b9a8b8b34a7",
        "recoveryLockSha256": "2e0de74c01b452476223a757ffa52ccc7f3f1ab74e9736e36bcd4a2ff0e8f08c",
        "runtimeGraphChanged": False,
        "recoveryLockChanged": False,
        "nativePackagingChanged": False,
        "licenseOrAdmissionDecisionChanged": False,
        "verificationMetadataTrustWeakened": False,
    }


def rec_i2b_successor_metadata_artifacts(*, aapt2_linux: bool = False) -> list[dict[str, Any]]:
    artifacts = [
        {
            "coordinate": "com.google.guava:guava-parent:33.2.1-jre",
            "artifact": "guava-parent-33.2.1-jre.pom",
            "bytes": 19431,
            "sha256": "9095f6d8ee3765950207510785830cc2280e781067915fa99cfe1740d4865c14",
            "sha1": "fc55955fab23e86fad230acc81bf570deb5660bd",
        },
        {
            "coordinate": "org.jetbrains.kotlinx:kotlinx-coroutines-bom:1.10.2",
            "artifact": "kotlinx-coroutines-bom-1.10.2.pom",
            "bytes": 4279,
            "sha256": "faf0c6538e53ddc0499a63664d8e763c216580b2e18e722ccbdf1b431a6afe26",
            "sha1": "bc3b109335d4fa0106a1e17b7863e388ef93c553",
        },
        {
            "coordinate": "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2",
            "artifact": "kotlinx-coroutines-core-jvm-1.10.2.module",
            "bytes": 4450,
            "sha256": "e9e4a74b4dbfe0f5ebeed88d49f3546c3ec3089419b20e5250403135c2c64c53",
            "sha1": "4287c0f64d2ae79ae0645440c8e787be1e4c9305",
        },
        {
            "coordinate": "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2",
            "artifact": "kotlinx-coroutines-core-jvm-1.10.2.jar",
            "bytes": 1479317,
            "sha256": "5ca175b38df331fd64155b35cd8cae1251fa9ee369709b36d42e0a288ccce3fd",
            "sha1": "4a9f78ef49483748e2c129f3d124b8fa249dafbf",
        },
        {
            "coordinate": "com.google.devtools.ksp:symbol-processing-aa-embeddable:2.3.9",
            "artifact": "symbol-processing-aa-embeddable-2.3.9.pom",
            "bytes": 1910,
            "sha256": "d5dd46ba75df65ccf43c093f0b7134903f9d68299975dd20851dfeb1600b8296",
            "sha1": "36ab1d7dcc45e1879b3135fc8be3e5985fa76744",
        },
        {
            "coordinate": "com.google.devtools.ksp:symbol-processing-aa-embeddable:2.3.9",
            "artifact": "symbol-processing-aa-embeddable-2.3.9.jar",
            "bytes": 84501468,
            "sha256": "b3536a0e599eaf1c150a42ab089d8d82aeec35fb2546c937f4d37f4c5ecb7998",
            "sha1": "214defea18ed4a0030e3d129c1831e266072b7ec",
        },
        {
            "coordinate": "com.google.guava:guava-parent:33.5.0-jre",
            "artifact": "guava-parent-33.5.0-jre.pom",
            "bytes": 23932,
            "sha256": "68719e687c6e4c9ff3e0fecbef7bd20896f0f4f7b314743ed33c72f962568215",
            "sha1": "c911af9ef688aeb809f86a864741cd91df184092",
        },
    ]
    if aapt2_linux:
        artifacts.append(
            {
                "coordinate": "com.android.tools.build:aapt2:9.3.1-15703166",
                "artifact": "aapt2-9.3.1-15703166-linux.jar",
                "bytes": 2369543,
                "sha256": "e772a3dae8354764f1b0793903218427f483982445207f2e4ffc8c2026755bd4",
                "sha1": "5287feac13566b9165cdf02c77565e68aec3aad0",
                "repository": "Google Maven",
                "toolingOnly": True,
            }
        )
    return artifacts


def rec_i2b_successor_implementation_manifest(terminal_commit: str) -> list[dict[str, Any]]:
    aapt2_linux = rec_i2b_successor_implementation_phase(terminal_commit) == "aapt2-linux"
    manifest: list[dict[str, Any]] = []
    for path in REC_I2B_SUCCESSOR_IMPLEMENTATION_PATHS:
        payload = git_blob_bytes(f"{terminal_commit}:{path}")
        item: dict[str, Any] = {
            "path": path,
            "gitBlobSha1": git_output("rev-parse", f"{terminal_commit}:{path}"),
        }
        if path == "android/gradle/verification-metadata.xml":
            item.update(
                {
                    "rawGitLfBytes": len(payload),
                    "rawGitLfSha256": hashlib.sha256(payload).hexdigest(),
                    "windowsWorkingBytes": 302390 if aapt2_linux else 302121,
                    "windowsWorkingSha256": (
                        "d5fe5ea69db9a544442892d4b38269acb14fa180ede3de1ed6651b2f2622c4ef"
                        if aapt2_linux
                        else "cfd4e04db9b2aed7471f38ba5274d8462784944420d2b8b001bb0b9478ad884e"
                    ),
                }
            )
        else:
            item.update(
                {
                    "rawGitBytes": len(payload),
                    "rawGitSha256": hashlib.sha256(payload).hexdigest(),
                }
            )
        manifest.append(item)
    return manifest


def rec_i2b_successor_precommit_review_history(terminal_commit: str) -> list[dict[str, Any]]:
    history: list[dict[str, Any]] = [
        {"disposition": "CHANGES_REQUIRED", "P0": 0, "P1": 2, "P2": 0},
        {"disposition": "CHANGES_REQUIRED", "P0": 0, "P1": 1, "P2": 0},
        {
            "disposition": "CLEAN",
            "P0": 0,
            "P1": 0,
            "P2": 0,
            "coversCommittedImplementation": False,
        },
    ]
    if terminal_commit != REC_I2B_SUCCESSOR_INITIAL_IMPLEMENTATION_HEAD:
        history.append(
            {
                "authorization": REC_I2B_SUCCESSOR_SELFTEST_REMEDIATION_AUTHORIZATION,
                "scope": "EVIDENCE_PHASE_SELFTEST_SOURCE_ONLY",
                "disposition": "CLEAN",
                "P0": 0,
                "P1": 0,
                "P2": 0,
                "coversCommittedImplementation": False,
            }
        )
    return history


def validate_rec_i2b_successor_advisory_remediation(
    evidence: dict[str, Any],
    *,
    terminal_commit: str,
    terminal_tree: str,
) -> None:
    remediation = evidence["advisoryRemediation"]["reviewedSuccessorValidatorAndCiMetadataRemediation"]
    current_review = evidence["reviewAndGateTruth"]["currentReviewedSuccessorIndependentDelta"]
    clean = current_review["disposition"] == "CLEAN"
    aapt2_linux = rec_i2b_successor_implementation_phase(terminal_commit) == "aapt2-linux"
    if terminal_commit == REC_I2B_SUCCESSOR_INITIAL_IMPLEMENTATION_HEAD:
        expected_parent = "95304992407392c82ee86870dff72529cc70e5c8"
    elif aapt2_linux:
        expected_parent = REC_I2B_SUCCESSOR_REVIEW_CLOSURE_HEAD
    else:
        expected_parent = REC_I2B_SUCCESSOR_INITIAL_IMPLEMENTATION_HEAD
    expected_authorizations = [
        REC_I2B_SUCCESSOR_REMEDIATION_AUTHORIZATION,
        REC_I2B_SUCCESSOR_JAR_METADATA_AUTHORIZATION,
        REC_I2B_SUCCESSOR_SPOTLESS_METADATA_AUTHORIZATION,
        REC_I2B_SUCCESSOR_SELFTEST_REMEDIATION_AUTHORIZATION,
    ]
    if aapt2_linux:
        expected_authorizations.extend(
            [
                REC_I2B_AAPT2_LINUX_METADATA_REMEDIATION_AUTHORIZATION,
                REC_I2B_AAPT2_VALIDATOR_PIN_CLARIFICATION,
            ]
        )
    require(
        remediation["authorizations"] == expected_authorizations
        and remediation["implementationStatus"]
        == (
            "IMPLEMENTED_LOCALLY_VERIFIED_AND_INDEPENDENTLY_CLEAN"
            if clean
            else "IMPLEMENTED_AND_LOCALLY_VERIFIED_PENDING_FRESH_INDEPENDENT_DELTA_REVIEW"
        )
        and remediation["implementationCommit"] == terminal_commit
        and remediation["implementationTree"] == terminal_tree
        and remediation["implementationParent"] == expected_parent
        and remediation["implementationParent"]
        == git_output("show", "-s", "--format=%P", terminal_commit)
        and remediation["implementationFiles"]
        == rec_i2b_successor_implementation_manifest(terminal_commit)
        and remediation["metadataArtifacts"]
        == rec_i2b_successor_metadata_artifacts(aapt2_linux=aapt2_linux)
        and remediation["metadataArtifactCount"] == (8 if aapt2_linux else 7)
        and remediation["metadataComponentCount"] == (6 if aapt2_linux else 5),
        "REC-I2B reviewed-successor remediation identity/artifact boundary drift",
    )
    triggering_ci = remediation["triggeringRequiredCi"]
    require(
        triggering_ci
        == {
            "workflow": "Android CI",
            "runId": 32108343415,
            "url": "https://github.com/Monumentogram/DORA/actions/runs/32108343415",
            "exactHead": "95304992407392c82ee86870dff72529cc70e5c8",
            "conclusion": "failure",
            "androidBootstrapJob": {
                "jobId": 95622292823,
                "conclusion": "failure",
                "failedStep": "Validate recovery governance package",
            },
            "searchSmokeJob": {
                "jobId": 95622292927,
                "conclusion": "failure",
                "failedStep": (
                    "Run Room FTS4 schema, correctness, safety, mapping, and mutation smoke tests"
                ),
                "recoveryDeviceOrEmulatorExecution": False,
            },
            "preservedAsHistoricalFailedRun": True,
        },
        "REC-I2B reviewed-successor historical failed-CI provenance drift",
    )
    expected_root_causes = [
        (
            "The Recovery governance validator dispatched the published REC-I2B successor through "
            "the historical REC-I1 anchor instead of an additive exact reviewed-successor profile."
        ),
        (
            "The clean GitHub runner exposed four already-resolved build/search tooling artifacts "
            "absent from strict verification metadata: guava-parent 33.2.1-jre POM, "
            "kotlinx-coroutines-bom 1.10.2 POM, kotlinx-coroutines-core-jvm 1.10.2 module, and "
            "symbol-processing-aa-embeddable 2.3.9 POM."
        ),
        (
            "Subsequent genuinely empty-cache closure exposed exactly the two corresponding tooling "
            "JARs and the Spotless guava-parent 33.5.0-jre POM; no eighth missing artifact appeared."
        ),
    ]
    if aapt2_linux:
        expected_root_causes.append(
            "Required Linux CI resolved the existing tooling-only aapt2 9.3.1-15703166 classifier "
            "whose exact JAR checksum was absent from strict verification metadata."
        )
    require(
        remediation["rootCauses"] == expected_root_causes
        and remediation["isolatedGradleBytesCanonicalMavenCentralBytesAndPublishedDigestMatched"] is True
        and remediation["verificationMetadataTrustWeakened"] is False
        and remediation["coordinateOrSelectedVersionChanged"] is False
        and remediation["runtimeGraphChanged"] is False
        and remediation["recoveryLockChanged"] is False
        and remediation["nativePackagingChanged"] is False
        and remediation["licenseOrAdmissionDecisionChanged"] is False
        and remediation["freshEmptyCacheSearchSmoke"]
        == {
            "result": "PASS",
            "duration": "5m 52s",
            "configurationCache": "stored",
            "missingVerificationArtifactCount": 0,
            "deviceOrEmulatorStarted": False,
        }
        and remediation["freshEmptyCacheOwnedConfigurationGraph"]
        == {
            "result": "PASS",
            "duration": "4m 26s",
            "actionableTasks": 81,
            "executedTasks": 81,
            "configurationCount": 61,
            "policyCoveredConfigurationCount": 34,
            "missingVerificationArtifactCount": 0,
        }
        and remediation["fullStaticBuildAndUnitBlock"]
        == {
            "result": "PASS",
            "actionableTasks": 104,
            "executedTasks": 104,
            "duration": "2m 27s",
            "unitTests": "91/91 across 15 suites; zero failures, errors or skips",
        }
        and remediation["forcedReleaseR8AndPackage"]
        == {
            "result": "PASS",
            "actionableTasks": 46,
            "executedTasks": 46,
            "duration": "2m 29s",
        }
        and remediation["governanceAndInventory"]
        == {
            "governanceSelfTest": "PASS",
            "offlineInventory": "PASS",
            "precommitIndependentReviewHistory": rec_i2b_successor_precommit_review_history(
                terminal_commit
            ),
        }
        and remediation["freshIndependentPostCommitDeltaReviewRequired"] is (not clean)
        and remediation["accountableEngineeringSecurityReviewCompleted"] is False
        and remediation["recI3Activated"] is False
        and evidence["advisoryRemediation"]["currentZeroFindingsClaimed"] is clean,
        "REC-I2B reviewed-successor verification/admission boundary drift",
    )
    if aapt2_linux:
        require(
            remediation["aapt2LinuxVerificationMetadataRemediation"]
            == {
                "authorization": REC_I2B_AAPT2_LINUX_METADATA_REMEDIATION_AUTHORIZATION,
                "validatorPinBindingClarification": REC_I2B_AAPT2_VALIDATOR_PIN_CLARIFICATION,
                "triggeringRequiredCi": rec_i2b_aapt2_triggering_ci(),
                "historicalReviewedSuccessorIndependentDelta": (
                    rec_i2b_historical_successor_review_truth()
                ),
                "implementationCommit": terminal_commit,
                "implementationTree": terminal_tree,
                "implementationParent": REC_I2B_SUCCESSOR_REVIEW_CLOSURE_HEAD,
                "metadataCanonicalLfSha256": REC_I2B_AAPT2_METADATA_CANONICAL_SHA256,
                "localVerification": rec_i2b_aapt2_local_verification(),
                "freshIndependentDeltaReview": current_review,
                "accountableEngineeringSecurityReviewCompleted": False,
                "recI3Activated": False,
            },
            "REC-I2B AUTH-05 aapt2 remediation proof/review boundary drift",
        )
    else:
        require(
            "aapt2LinuxVerificationMetadataRemediation" not in remediation,
            "REC-I2B historical reviewed-successor profile acquired AUTH-05 state",
        )


def validate_rec_i2b_successor_review_closure(
    record: dict[str, Any],
    *,
    terminal_commit: str,
    terminal_tree: str,
) -> tuple[str, str]:
    aapt2_linux = rec_i2b_successor_implementation_phase(terminal_commit) == "aapt2-linux"
    expected_path = (
        REC_I2B_AAPT2_REVIEW_CLOSURE_PATH
        if aapt2_linux
        else REC_I2B_SUCCESSOR_REVIEW_CLOSURE_PATH
    )
    expected_record_id = (
        "REC-I2B-AAPT2-LINUX-VERIFICATION-METADATA-INDEPENDENT-DELTA-REVIEW-CLOSURE-20260818-01"
        if aapt2_linux
        else (
            "REC-I2B-REVIEWED-SUCCESSOR-VALIDATOR-CI-METADATA-INDEPENDENT-DELTA-REVIEW-"
            "CLOSURE-20260818-01"
        )
    )
    require(
        record["path"] == expected_path
        and isinstance(record["bytes"], int)
        and record["bytes"] > 0
        and re.fullmatch(r"[0-9a-f]{64}", record["sha256"] or "") is not None,
        "REC-I2B successor independent closure reference is malformed",
    )
    closure_path = ROOT / record["path"]
    require(closure_path.is_file(), "REC-I2B successor independent closure is missing")
    closure_bytes = closure_path.read_bytes()
    require(
        len(closure_bytes) == record["bytes"]
        and hashlib.sha256(closure_bytes).hexdigest() == record["sha256"],
        "REC-I2B successor independent closure byte/hash pin drift",
    )
    closure = json.loads(closure_bytes.decode("utf-8", errors="strict"))
    reviewed = closure["exactReviewedState"]
    verdict = closure["verdict"]
    reviewer = closure["reviewer"]
    authority = closure["authorityBoundary"]
    evidence_commit = reviewed["evidenceCommit"]
    evidence_tree = reviewed["evidenceTree"]
    require(
        closure["recordId"] == expected_record_id
        and reviewer
        == {
            "product": "OpenAI Codex",
            "organization": "OpenAI",
            "role": "INDEPENDENT_ADVISORY_IMPLEMENTATION_REVIEWER",
            "formalReviewer": False,
            "signature": None,
        }
        and reviewed["implementationCommit"] == terminal_commit
        and reviewed["implementationTree"] == terminal_tree
        and FULL_SHA256_RE.fullmatch(evidence_commit or "") is not None
        and FULL_SHA256_RE.fullmatch(evidence_tree or "") is not None
        and verdict
        == {
            "disposition": "CLEAN",
            "P0": 0,
            "P1": 0,
            "P2": 0,
            "exactMinimalFix": None,
            "newFindings": [],
        }
        and authority
        == {
            "formalAccountableEngineeringSecurityReview": False,
            "accountableReviewerIdentityOrSignatureRecorded": False,
            "activatesRecI3": False,
            "authorizesDeviceOrEmulatorExecution": False,
            "authorizesHarnessOrMeasurement": False,
            "authorizesDependencyOrProductionAdmission": False,
            "authorizesReadyOrMerge": False,
        },
        "REC-I2B successor independent closure semantics/authority drift",
    )
    require(
        git_output("rev-parse", "--verify", f"{evidence_commit}^{{commit}}") == evidence_commit
        and git_output("rev-parse", f"{evidence_commit}^{{tree}}") == evidence_tree
        and git_is_ancestor(terminal_commit, evidence_commit)
        and git_is_ancestor(evidence_commit, git_output("rev-parse", "HEAD")),
        "REC-I2B successor reviewed evidence commit/tree/lineage mismatch",
    )
    return evidence_commit, evidence_tree


def validate_rec_i2b_runtime_evidence(
    evidence: dict[str, Any],
    *,
    terminal_commit: str,
    terminal_tree: str,
) -> str:
    baseline = terminal_commit == REC_I2B_METADATA_IMPLEMENTATION_HEAD
    aapt2_linux = (
        not baseline
        and rec_i2b_successor_implementation_phase(terminal_commit) == "aapt2-linux"
    )
    require(
        evidence["reportId"] == "REC-I2B-RUNTIME-CRYPTO-IMPLEMENTATION-EVIDENCE-20260817-01"
        and evidence["status"] == REC_I2B_CLAIM_CEILING
        and evidence["claimCeiling"] == REC_I2B_CLAIM_CEILING,
        "REC-I2B implementation evidence identity/status/claim ceiling drift",
    )
    require(
        evidence["authority"]
        == rec_i2b_expected_authority(successor=not baseline, aapt2_linux=aapt2_linux),
        "REC-I2B implementation evidence exact authority boundary drift",
    )
    git_record = evidence["git"]
    expected_commits = [
        "7b90db3403f165bb268daa6a727c5a33c3524a2b",
        "6557add0d1d74d9029bd0d547b5493e72cdf7bac",
        REC_I2B_RUNTIME_IMPLEMENTATION_HEAD,
        "adc422236003403d15581c10e31e2dc797ccf9bb",
        REC_I2B_METADATA_IMPLEMENTATION_HEAD,
    ]
    if not baseline:
        require(
            git_output("rev-parse", f"{REC_I2B_SUCCESSOR_INITIAL_IMPLEMENTATION_HEAD}^{{tree}}")
            == REC_I2B_SUCCESSOR_INITIAL_IMPLEMENTATION_TREE
            and git_is_ancestor(REC_I2B_SUCCESSOR_INITIAL_IMPLEMENTATION_HEAD, terminal_commit),
            "REC-I2B initial reviewed-successor implementation anchor/tree/lineage drift",
        )
        expected_commits.append(REC_I2B_SUCCESSOR_INITIAL_IMPLEMENTATION_HEAD)
        if terminal_commit != REC_I2B_SUCCESSOR_INITIAL_IMPLEMENTATION_HEAD:
            expected_commits.append(terminal_commit)
    require(
        git_record["branch"] == REC_I2B_BRANCH
        and git_record["implementationCommits"] == expected_commits
        and git_record["terminalImplementationCommit"] == terminal_commit
        and git_record["terminalImplementationTree"] == terminal_tree
        and git_record["published"] is True
        and git_record["publishedHeadBeforeLocalMetadataRemediation"]
        == (
            REC_I2B_SUCCESSOR_REVIEW_CLOSURE_HEAD
            if aapt2_linux
            else REC_I2B_RUNTIME_CLOSURE_HEAD
        )
        and git_record["terminalImplementationPublished"] is False
        and git_record["pullRequestCreated"] is True
        and git_record["pullRequestNumber"] == REC_I2B_PULL_REQUEST_NUMBER
        and git_record["pullRequestDraft"] is True
        and git_record["merged"] is False,
        "REC-I2B implementation evidence Git/PR target drift",
    )
    require(
        semantic_sha256(evidence["exactFileManifest"])
        == "1970579738c9e347d33305421ef2c0959437e1715c3721e262dc7b2f2e30d9e3"
        and semantic_sha256(evidence["implementation"])
        == "41355672ed5dc3ffd5a705d0282cedd1cc05933ea94ec493da26aaba40e2be39",
        "REC-I2B exact runtime file manifest or crypto implementation contract drift",
    )

    actual_graph = evidence["dependencyGraph"]
    require(
        actual_graph["allResolvableConfigurationsEnumeratedAndResolved"] is True
        and actual_graph["allResolvableConfigurationCount"] == 61
        and actual_graph["policyCoveredConfigurationCount"] == 34
        and actual_graph["outsidePolicyBoundaryToolingConfigurationCount"] == 27
        and actual_graph["policyCoveredJsr305ResolvedComponentCount"] == 0
        and actual_graph["policyCoveredPackagedJsr305ClassDefinitionCount"] == 0
        and len(actual_graph["runtimeArtifacts"]) == 6
        and len(actual_graph["testOnlyArtifacts"]) == 2
        and actual_graph["graphDriftFromApprovedSixRuntimeAndTwoTestOnlyArtifacts"] is False,
        "REC-I2B current actual-graph evidence drift",
    )

    common_truth = rec_i2b_expected_common_review_truth()
    if baseline:
        expected_truth = {
            **common_truth,
            "currentFreshCacheMetadataIndependentFindingCounts": {"P0": 0, "P1": 0, "P2": 0},
            "currentIndependentCleanReviewedImplementationCommit": REC_I2B_METADATA_IMPLEMENTATION_HEAD,
            "currentIndependentCleanReviewedEvidenceCommit": REC_I2B_METADATA_EVIDENCE_HEAD,
            "currentIndependentCleanClosureRecord": REC_I2B_METADATA_CLOSURE_PATH,
            "currentZeroFindingsClaimed": True,
            "independentDeltaReviewCompleted": True,
            "independentDeltaReviewDisposition": "CLEAN",
            "independentDeltaReviewRequired": False,
            "distinctAccountableEngineeringSecurityReviewRequired": True,
            "accountableEngineeringSecurityReviewRoutingMayBeRebuilt": True,
            "delegationMaySubstituteAccountableReview": False,
            "recI3MayProceedNow": False,
            "openStageGate": "REC-I2B-ACCOUNTABLE-ENGINEERING-SECURITY-REVIEW",
        }
        require(
            evidence["remediationState"]
            == "FRESH_CACHE_METADATA_CLOSURE_INDEPENDENT_CLEAN_ACCOUNTABLE_REVIEW_PENDING"
            and evidence["reviewAndGateTruth"] == expected_truth,
            "REC-I2B baseline current independent-review/gate truth drift",
        )
        phase = "baseline-clean"
    else:
        validate_rec_i2b_successor_advisory_remediation(
            evidence,
            terminal_commit=terminal_commit,
            terminal_tree=terminal_tree,
        )
        current_review = evidence["reviewAndGateTruth"]["currentReviewedSuccessorIndependentDelta"]
        disposition = current_review["disposition"]
        require(disposition in {"PENDING", "CLEAN"}, "REC-I2B successor independent disposition is invalid")
        clean = disposition == "CLEAN"
        evidence_commit: str | None = None
        evidence_tree: str | None = None
        closure_reference: dict[str, Any] | None = None
        if clean:
            closure_reference = current_review["closureRecord"]
            evidence_commit, evidence_tree = validate_rec_i2b_successor_review_closure(
                closure_reference,
                terminal_commit=terminal_commit,
                terminal_tree=terminal_tree,
            )
        expected_current_review = {
            "reviewedImplementationCommit": terminal_commit,
            "reviewedImplementationTree": terminal_tree,
            "reviewedEvidenceCommit": evidence_commit,
            "reviewedEvidenceTree": evidence_tree,
            "closureRecord": closure_reference,
            "findingCounts": {"P0": 0, "P1": 0, "P2": 0} if clean else None,
            "disposition": "CLEAN" if clean else "PENDING",
            "completed": clean,
            "required": not clean,
            "coversCurrentTarget": clean,
        }
        expected_truth = {
            **common_truth,
            "priorFreshCacheMetadataIndependentFindingCounts": {"P0": 0, "P1": 0, "P2": 0},
            "priorFreshCacheMetadataIndependentReviewedImplementationCommit": (
                REC_I2B_METADATA_IMPLEMENTATION_HEAD
            ),
            "priorFreshCacheMetadataIndependentReviewedEvidenceCommit": REC_I2B_METADATA_EVIDENCE_HEAD,
            "priorFreshCacheMetadataIndependentClosureRecord": REC_I2B_METADATA_CLOSURE_PATH,
            "priorFreshCacheMetadataIndependentDisposition": "CLEAN",
            "priorFreshCacheMetadataIndependentReviewCoversCurrentTarget": False,
            **(
                {
                    "historicalReviewedSuccessorIndependentDelta": (
                        rec_i2b_historical_successor_review_truth()
                    )
                }
                if aapt2_linux
                else {}
            ),
            "currentReviewedSuccessorIndependentDelta": expected_current_review,
            "currentZeroFindingsClaimed": clean,
            "independentDeltaReviewCompleted": clean,
            "independentDeltaReviewDisposition": "CLEAN" if clean else "PENDING",
            "independentDeltaReviewRequired": not clean,
            "distinctAccountableEngineeringSecurityReviewRequired": True,
            "accountableEngineeringSecurityReviewRoutingMayBeRebuilt": False,
            "delegationMaySubstituteAccountableReview": False,
            "recI3MayProceedNow": False,
            "openStageGate": (
                "REC-I2B-DRAFT-PR38-EXACT-HEAD-CI_THEN_ACCOUNTABLE-ENGINEERING-SECURITY-REVIEW"
                if clean
                else "REC-I2B-INDEPENDENT-ADVISORY-DELTA-REVIEW"
            ),
        }
        require(
            evidence["remediationState"]
            == (
                (
                    "AAPT2_LINUX_VERIFICATION_METADATA_INDEPENDENT_CLEAN_DRAFT_CI_PENDING"
                    if clean
                    else "AAPT2_LINUX_VERIFICATION_METADATA_LOCALLY_VERIFIED_INDEPENDENT_DELTA_PENDING"
                )
                if aapt2_linux
                else (
                    "REVIEWED_SUCCESSOR_VALIDATOR_AND_CI_METADATA_INDEPENDENT_CLEAN_DRAFT_CI_PENDING"
                    if clean
                    else "REVIEWED_SUCCESSOR_VALIDATOR_AND_CI_METADATA_REMEDIATED_LOCALLY_VERIFIED_INDEPENDENT_DELTA_PENDING"
                )
            )
            and evidence["reviewAndGateTruth"] == expected_truth,
            "REC-I2B successor independent-review/gate truth drift",
        )
        phase = "successor-clean" if clean else "successor-pending"

    forbidden_claims = evidence["notPerformedOrClaimed"]
    expected_forbidden_fields = {
        "deviceOrEmulatorExecution",
        "androidKeystoreRuntimeExecution",
        "harnessImplementationOrExecution",
        "orchestratedKillRecoveryCampaign",
        "networkExecution",
        "measurement",
        "metricPass",
        "pocPass",
        "readinessClosure",
        "dependencyAdmission",
        "productionAdmission",
        "recI3Activation",
        "merge",
        "googleSheetMutation",
    }
    require(
        expected_forbidden_fields.issubset(forbidden_claims)
        and all(forbidden_claims[field] is False for field in expected_forbidden_fields),
        "REC-I2B forbidden execution/admission/PASS/merge claim became true or disappeared",
    )
    return phase


def validate_rec_i2b_accountable_packet(
    packet: dict[str, Any],
    *,
    evidence: dict[str, Any],
    evidence_bytes: bytes,
    terminal_commit: str,
    terminal_tree: str,
    phase: str,
) -> None:
    baseline = phase == "baseline-clean"
    aapt2_linux = (
        not baseline
        and rec_i2b_successor_implementation_phase(terminal_commit) == "aapt2-linux"
    )
    require(
        packet["packetId"] == "REC-I2B-ACCOUNTABLE-ENGINEERING-SECURITY-REVIEW-PACKET-20260817-01"
        and packet["claimCeiling"] == REC_I2B_CLAIM_CEILING,
        "REC-I2B accountable packet identity/claim ceiling drift",
    )
    expected_authority = {
        "implementationAuthority": [
            "REC-I2A-ACTUAL-GRAPH-PRODUCT-IP-DISPOSITION-20260817-01",
            "STAGE0-OWNER-UNLOCK-BATCH-20260817-02",
            "STAGE0-TECHNICAL-REMEDIATION-DELEGATION-20260817-01",
            "REC-I2B-ADVISORY-REMEDIATION-AUTH-20260817-01",
            "REC-I2B-INDEPENDENT-DELTA-REVIEW-CLOSURE-AUTH-20260817-01",
            "REC-I2B-DRAFT-PUBLICATION-AUTH-20260817-01",
            "REC-I2B-VERIFICATION-METADATA-BOM-REMEDIATION-AUTH-20260817-01",
            "REC-I2B-VERIFICATION-METADATA-TRANSITIVE-ARTIFACTS-REMEDIATION-AUTH-20260817-02",
            "REC-I2B-VERIFICATION-METADATA-GUAVA-33_3_1-POM-REMEDIATION-AUTH-20260817-03",
            "REC-I2B-VERIFICATION-METADATA-FRESH-CACHE-CLOSURE-AUTH-20260817-04",
            "REC-I2B-FRESH-CACHE-METADATA-INDEPENDENT-DELTA-REVIEW-CLOSURE-20260818-01",
        ],
        "thisPacketIsFormalApproval": False,
        "technicalDelegationMaySubstituteAccountableReview": False,
        "reviewerIdentityRecorded": False,
        "reviewerSignatureRecorded": False,
        "formalApprovalRecorded": False,
        "recI3Activated": False,
        "deviceOrEmulatorExecutionAuthorized": False,
        "harnessOrCampaignAuthorized": False,
        "measurementAuthorized": False,
        "dependencyOrProductionAdmissionAuthorized": False,
        "publicationOrMergeAuthorizedByIndependentReview": False,
        "draftPublicationAlreadyAuthorizedAndPerformed": True,
        "currentReviewedClosureDraftPr38NonForceUpdateAuthorized": baseline,
        "readyOrMergeAuthorized": False,
    }
    if not baseline:
        expected_authority["implementationAuthority"].extend(
            [
                REC_I2B_SUCCESSOR_REMEDIATION_AUTHORIZATION,
                REC_I2B_SUCCESSOR_JAR_METADATA_AUTHORIZATION,
                REC_I2B_SUCCESSOR_SPOTLESS_METADATA_AUTHORIZATION,
                REC_I2B_SUCCESSOR_SELFTEST_REMEDIATION_AUTHORIZATION,
            ]
        )
        expected_authority["reviewedSuccessorDraftPr38NonForceUpdateAuthorized"] = True
    if aapt2_linux:
        expected_authority["implementationAuthority"].extend(
            [
                REC_I2B_AAPT2_LINUX_METADATA_REMEDIATION_AUTHORIZATION,
                REC_I2B_AAPT2_VALIDATOR_PIN_CLARIFICATION,
            ]
        )
        expected_authority["aapt2LinuxDraftPr38NonForceUpdateAuthorizedAfterClean"] = True
    require(
        packet["authorityBoundary"] == expected_authority,
        "REC-I2B accountable packet exact authority boundary drift",
    )

    target = packet["reviewTarget"]
    expected_commits = evidence["git"]["implementationCommits"]
    require(
        target["branch"] == REC_I2B_BRANCH
        and target["publishedAtPacketRefresh"] is False
        and target["targetedForExistingDraftPr38Publication"] is True
        and target["publishedDraftBaselineHead"]
        == (
            REC_I2B_SUCCESSOR_REVIEW_CLOSURE_HEAD
            if aapt2_linux
            else REC_I2B_RUNTIME_CLOSURE_HEAD
        )
        and target["pullRequestNumber"] == REC_I2B_PULL_REQUEST_NUMBER
        and target["pullRequestDraft"] is True
        and target["implementationCommits"] == expected_commits
        and target["terminalImplementationCommit"] == terminal_commit
        and target["terminalImplementationTree"] == terminal_tree,
        "REC-I2B accountable packet exact branch/PR/terminal target drift",
    )
    evidence_reference = target["implementationEvidence"]
    require(
        evidence_reference
        == {
            "path": REC_I2B_RUNTIME_EVIDENCE_PATH,
            "bytes": len(evidence_bytes),
            "sha256": hashlib.sha256(evidence_bytes).hexdigest(),
        },
        "REC-I2B accountable packet implementation-evidence byte/hash pin drift",
    )
    require(
        packet["exactFileManifest"] == evidence["exactFileManifest"],
        "REC-I2B accountable packet exact runtime file manifest cross-pin drift",
    )
    historical_closure = target["independentDeltaReviewClosure"]
    require(
        historical_closure
        == {
            "path": "docs/evidence/poc-recovery-001/reviews/rec-i2b-independent-delta-review-closure-2026-08-17.json",
            "bytes": 8534,
            "sha256": "3a3817150fa984a2caf6b4bf24e1ca677574bfc17d1d24916a2d35db343cdc04",
            "historicalOnlyForCurrentTarget": True,
        },
        "REC-I2B accountable packet historical runtime closure pin drift",
    )
    prior_metadata_closure = target["freshCacheMetadataIndependentDeltaReviewClosure"]
    require(
        prior_metadata_closure["path"] == REC_I2B_METADATA_CLOSURE_PATH
        and prior_metadata_closure["bytes"] == 8427
        and prior_metadata_closure["sha256"] == REC_I2B_METADATA_CLOSURE_SHA256
        and prior_metadata_closure["reviewedImplementationCommit"] == REC_I2B_METADATA_IMPLEMENTATION_HEAD
        and prior_metadata_closure["reviewedEvidenceCommit"] == REC_I2B_METADATA_EVIDENCE_HEAD,
        "REC-I2B accountable packet fresh-cache historical closure pin drift",
    )

    advisory_gate = packet["advisoryRemediationGate"]
    require(
        advisory_gate["priorVerdict"]
        == {
            "formalReviewer": False,
            "disposition": "CHANGES_REQUIRED",
            "P0": 0,
            "P1": 3,
            "P2": 2,
        }
        and advisory_gate["localRemediation"]
        == {
            "P1FindingsImplementedAndVerified": 3,
            "P2FindingsImplementedAndVerified": 2,
            "closureClaimed": True,
        }
        and advisory_gate["independentDeltaReview"]
        == {
            "completed": True,
            "reviewer": {
                "product": "OpenAI Codex",
                "organization": "OpenAI",
                "role": "INDEPENDENT_ADVISORY_IMPLEMENTATION_REVIEWER",
                "formalReviewer": False,
            },
            "disposition": "CLEAN",
            "P0": 0,
            "P1": 0,
            "P2": 0,
            "record": (
                "docs/evidence/poc-recovery-001/reviews/"
                "rec-i2b-independent-delta-review-closure-2026-08-17.json"
            ),
            "reviewedImplementationCommit": REC_I2B_RUNTIME_IMPLEMENTATION_HEAD,
            "reviewedImplementationTree": REC_I2B_RUNTIME_IMPLEMENTATION_TREE,
            "reviewedEvidenceCommit": REC_I2B_RUNTIME_EVIDENCE_HEAD,
            "reviewedEvidenceTree": REC_I2B_RUNTIME_EVIDENCE_TREE,
            "durableClosureRecordCommit": REC_I2B_RUNTIME_CLOSURE_HEAD,
            "coversCurrentTerminalImplementation": False,
        }
        and advisory_gate["freshGraphVerificationMetadataAdvisoryVerdict"]
        == {
            "reviewedImplementationCommit": "adc422236003403d15581c10e31e2dc797ccf9bb",
            "reviewedImplementationTree": "27e9f6b956e5818daae32949f978e42c0c1644b2",
            "reviewedEvidenceCommit": "c65fe0718f082aec97a38d2cc567e1d56c6da8d1",
            "reviewedEvidenceTree": "d6227920a0b2e852c74f3aa5623ca853b3aa0b4b",
            "disposition": "CHANGES_REQUIRED",
            "P0": 0,
            "P1": 1,
            "P2": 0,
            "formalAccountableReview": False,
            "findingId": "REC-I2B-FRESH-GRAPH-VERIFICATION-METADATA-P1-001",
            "coversCurrentTerminalImplementation": False,
        }
        and advisory_gate["freshIndependentDeltaReview"]
        == {
            "required": False,
            "completed": True,
            "reviewer": {
                "product": "OpenAI Codex",
                "organization": "OpenAI",
                "role": "INDEPENDENT_ADVISORY_IMPLEMENTATION_REVIEWER",
                "formalReviewer": False,
            },
            "disposition": "CLEAN",
            "P0": 0,
            "P1": 0,
            "P2": 0,
            "reviewedImplementationCommit": REC_I2B_METADATA_IMPLEMENTATION_HEAD,
            "reviewedImplementationTree": REC_I2B_METADATA_IMPLEMENTATION_TREE,
            "reviewedEvidenceCommit": REC_I2B_METADATA_EVIDENCE_HEAD,
            "reviewedEvidenceTree": REC_I2B_METADATA_EVIDENCE_TREE,
            "record": {
                "recordId": (
                    "REC-I2B-FRESH-CACHE-METADATA-INDEPENDENT-DELTA-REVIEW-CLOSURE-20260818-01"
                ),
                "path": REC_I2B_METADATA_CLOSURE_PATH,
                "bytes": 8427,
                "sha256": REC_I2B_METADATA_CLOSURE_SHA256,
            },
        },
        "REC-I2B accountable packet duplicated advisory review pins/closures drift",
    )
    if not baseline:
        current_review = evidence["reviewAndGateTruth"]["currentReviewedSuccessorIndependentDelta"]
        successor_remediation = evidence["advisoryRemediation"][
            "reviewedSuccessorValidatorAndCiMetadataRemediation"
        ]
        require(
            advisory_gate["reviewedSuccessorValidatorAndCiMetadataRemediation"]
            == {
                "authorizations": (
                    [
                        REC_I2B_SUCCESSOR_REMEDIATION_AUTHORIZATION,
                        REC_I2B_SUCCESSOR_JAR_METADATA_AUTHORIZATION,
                        REC_I2B_SUCCESSOR_SPOTLESS_METADATA_AUTHORIZATION,
                        REC_I2B_SUCCESSOR_SELFTEST_REMEDIATION_AUTHORIZATION,
                    ]
                    + (
                        [
                            REC_I2B_AAPT2_LINUX_METADATA_REMEDIATION_AUTHORIZATION,
                            REC_I2B_AAPT2_VALIDATOR_PIN_CLARIFICATION,
                        ]
                        if aapt2_linux
                        else []
                    )
                ),
                "implementationStatus": successor_remediation["implementationStatus"],
                "implementationCommit": terminal_commit,
                "implementationTree": terminal_tree,
                "implementationEvidence": evidence_reference,
                "localVerification": {
                    "governanceSelfTest": "PASS",
                    "offlineInventory": "PASS",
                    "fullStaticBuildAndUnit": "104/104 PASS",
                    "ownedConfigurationGraph": "81/81 PASS; 61/61 configurations",
                    "forcedR8AndPackage": "46/46 PASS",
                },
                "precommitIndependentReviewHistory": rec_i2b_successor_precommit_review_history(
                    terminal_commit
                ),
                "freshIndependentDeltaReview": current_review,
                **(
                    {
                        "aapt2LinuxVerificationMetadataRemediation": successor_remediation[
                            "aapt2LinuxVerificationMetadataRemediation"
                        ]
                    }
                    if aapt2_linux
                    else {}
                ),
                "accountableEngineeringSecurityReviewCompleted": False,
                "recI3Activated": False,
            },
            "REC-I2B accountable packet reviewed-successor remediation/gate drift",
        )

    findings = packet["findingsTruth"]
    require(
        findings["priorAdvisory"] == {"P0": 0, "P1": 3, "P2": 2}
        and findings["historicalPostRemediationIndependentDelta"] == {"P0": 0, "P1": 0, "P2": 0}
        and findings["freshGraphVerificationMetadataAdvisory"]
        == {
            "reviewedImplementationCommit": "adc422236003403d15581c10e31e2dc797ccf9bb",
            "reviewedEvidenceCommit": "c65fe0718f082aec97a38d2cc567e1d56c6da8d1",
            "disposition": "CHANGES_REQUIRED",
            "findingId": "REC-I2B-FRESH-GRAPH-VERIFICATION-METADATA-P1-001",
            "P0": 0,
            "P1": 1,
            "P2": 0,
            "coversCurrentTarget": False,
        },
        "REC-I2B accountable packet duplicated historical findings truth drift",
    )
    if not baseline:
        current_review = evidence["reviewAndGateTruth"]["currentReviewedSuccessorIndependentDelta"]
        require(
            target["reviewedSuccessorIndependentDeltaReviewClosure"]
            == current_review["closureRecord"]
            and (
                target.get("historicalReviewedSuccessorIndependentDelta")
                == rec_i2b_historical_successor_review_truth()
                if aapt2_linux
                else "historicalReviewedSuccessorIndependentDelta" not in target
            ),
            "REC-I2B accountable packet successor closure cross-pin drift",
        )

    checklist = packet["reviewChecklist"]
    expected_independent_answer = None if phase == "successor-pending" else "YES"
    checklist_contract = [
        {
            "id": item["id"],
            "question": item["question"],
            "requiredAnswer": item["requiredAnswer"],
        }
        for item in checklist
    ]
    require(
        len(checklist) == 17
        and semantic_sha256(checklist_contract)
        == "012910c90618ac65dc13cad4894d1919c5e2cbda09a8daa506de9a8296f10bee"
        and [item["id"] for item in checklist]
        == [f"I2B-REV-{index:02d}" for index in range(1, 18)]
        and all(set(item) == {
            "id",
            "question",
            "requiredAnswer",
            "implementationEvidenceAnswer",
            "independentDeltaReviewerAnswer",
            "accountableReviewerAnswer",
        } for item in checklist)
        and all(item["requiredAnswer"] == "YES" for item in checklist)
        and all(item["implementationEvidenceAnswer"] == "YES" for item in checklist)
        and all(
            item["independentDeltaReviewerAnswer"] == expected_independent_answer
            for item in checklist
        )
        and all(item["accountableReviewerAnswer"] is None for item in checklist),
        "REC-I2B accountable packet exact 17-item checklist/answer boundary drift",
    )
    require(
        semantic_sha256(packet["decisionOptions"])
        == "b1555d8e620e882175460326ee5363f52c744adfcbaa12c339871f27a06bc45d",
        "REC-I2B accountable packet decision-option boundary drift",
    )
    invariants = packet["frozenDesignInvariants"]
    require(
        len(invariants) == 17
        and semantic_sha256(invariants[:16])
        == "67da375509adad6b902809a04ef844ba36fa0493137543f48cc4217d42fb3bc1",
        "REC-I2B accountable packet frozen design invariants 1-16 drift",
    )
    if baseline:
        expected_last_invariant = {
            "id": "I2B-INV-17",
            "invariant": (
                "The independently cleaned 87a baseline is published only in Draft PR 38; the current "
                "fresh-cache metadata closure remains local, unpublished and nonmetric, with no device, "
                "emulator, network, harness, campaign, REC-I3 activation, PASS, Ready, admission, additional "
                "push or merge."
            ),
        }
    elif phase == "successor-clean":
        expected_last_invariant = {
            "id": "I2B-INV-17",
            "invariant": (
                (
                    "The independently cleaned e4a successor remains the published Draft PR 38 head. "
                    if aapt2_linux
                    else "The independently cleaned 87a baseline remains the only published Draft PR 38 head. "
                )
                + f"Exact reviewed-successor implementation {terminal_commit[:7]} and its evidence have a "
                "fresh independent CLEAN 0/0/0 closure and remain local pending the authorized non-force "
                "Draft PR 38 update and exact-head CI; no Recovery device, emulator, network, harness, "
                "campaign, REC-I3 activation, PASS, Ready, admission or merge is claimed."
            ),
        }
    else:
        expected_last_invariant = {
            "id": "I2B-INV-17",
            "invariant": (
                (
                    "The independently cleaned e4a successor remains the published Draft PR 38 head. "
                    if aapt2_linux
                    else "The independently cleaned 87a baseline remains the only published Draft PR 38 head. "
                )
                + f"Exact reviewed-successor implementation {terminal_commit[:7]} and this pending evidence "
                "remain local and unpublished until a fresh independent CLEAN delta closure; no Recovery "
                "device, emulator, network, harness, campaign, REC-I3 activation, PASS, Ready, admission, "
                "push or merge is claimed."
            ),
        }
    require(
        invariants[16] == expected_last_invariant,
        "REC-I2B accountable packet phase-aware publication/claim invariant drift",
    )

    verification_evidence = copy.deepcopy(packet["verificationEvidence"])
    if baseline:
        require(
            semantic_sha256(verification_evidence)
            == "d2ba48bcbaa381f80c91ea39a65019c5ccf9373276fc610494bef056239c9531",
            "REC-I2B accountable packet baseline verification evidence drift",
        )
    else:
        clean = phase == "successor-clean"
        aapt2_verification = verification_evidence.pop(
            "aapt2LinuxVerificationMetadataRemediation",
            None,
        )
        successor_delta = verification_evidence["governanceAndInventory"].pop(
            "freshIndependentDeltaReview",
            None,
        )
        require(
            semantic_sha256(verification_evidence)
            == "60f4bdf56e20c2ba305138c21fc7c86a0aaaca8047e3ba04d8f2e36154566830",
            "REC-I2B accountable packet reviewed-successor local verification evidence drift",
        )
        current_review = evidence["reviewAndGateTruth"]["currentReviewedSuccessorIndependentDelta"]
        if aapt2_linux:
            historical_review = rec_i2b_historical_successor_review_truth()
            expected_successor_delta = {
                "result": "CLEAN",
                "findingCounts": historical_review["findingCounts"],
                "reviewedImplementationCommit": historical_review["reviewedImplementationCommit"],
                "reviewedImplementationTree": historical_review["reviewedImplementationTree"],
                "reviewedEvidenceCommit": historical_review["reviewedEvidenceCommit"],
                "reviewedEvidenceTree": historical_review["reviewedEvidenceTree"],
                "closureRecord": historical_review["closureRecord"],
            }
            require(
                aapt2_verification
                == {
                    "authorization": REC_I2B_AAPT2_LINUX_METADATA_REMEDIATION_AUTHORIZATION,
                    "validatorPinBindingClarification": REC_I2B_AAPT2_VALIDATOR_PIN_CLARIFICATION,
                    "triggeringRequiredCi": rec_i2b_aapt2_triggering_ci(),
                    "metadataGitBlobSha1": git_output(
                        "rev-parse",
                        f"{terminal_commit}:android/gradle/verification-metadata.xml",
                    ),
                    "metadataCanonicalLfSha256": REC_I2B_AAPT2_METADATA_CANONICAL_SHA256,
                    "localVerification": rec_i2b_aapt2_local_verification(),
                    "freshIndependentDeltaReview": current_review,
                },
                "REC-I2B accountable packet AUTH-05 verification proof drift",
            )
        else:
            expected_successor_delta = (
                {
                    "result": "CLEAN",
                    "findingCounts": {"P0": 0, "P1": 0, "P2": 0},
                    "reviewedImplementationCommit": terminal_commit,
                    "reviewedImplementationTree": terminal_tree,
                    "reviewedEvidenceCommit": current_review["reviewedEvidenceCommit"],
                    "reviewedEvidenceTree": current_review["reviewedEvidenceTree"],
                    "closureRecord": current_review["closureRecord"],
                }
                if clean
                else None
            )
            require(
                aapt2_verification is None,
                "REC-I2B historical reviewed-successor verification acquired AUTH-05 state",
            )
        require(
            successor_delta == expected_successor_delta,
            "REC-I2B accountable packet phase-aware independent verification evidence drift",
        )
    if baseline:
        require(
            packet["status"] == "INDEPENDENT_CLEAN_DRAFT_PUBLICATION_AND_EXACT_HEAD_CI_PENDING"
            and packet["routingEligibility"]
            == {
                "eligibleNow": False,
                "eligibleAfterDraftPr38ExactHeadCiGreen": True,
                "blockingPrecondition": "DRAFT_PR38_EXACT_HEAD_CI_GREEN",
                "historicalIndependentAdvisoryDeltaReviewDisposition": "CLEAN",
                "historicalCleanReviewCoversCurrentTarget": False,
                "laterFreshGraphVerificationMetadataDisposition": "CHANGES_REQUIRED",
                "laterChangesRequiredReviewCoversCurrentTarget": False,
                "currentIndependentAdvisoryDeltaReviewDisposition": "CLEAN",
                "currentIndependentCleanReviewCoversExactImplementationAndReviewedEvidence": True,
                "recI3MayProceedNow": False,
            }
            and findings["currentFreshCacheMetadataIndependentDelta"]
            == {
                "reviewedImplementationCommit": REC_I2B_METADATA_IMPLEMENTATION_HEAD,
                "reviewedEvidenceCommit": REC_I2B_METADATA_EVIDENCE_HEAD,
                "disposition": "CLEAN",
                "P0": 0,
                "P1": 0,
                "P2": 0,
                "record": REC_I2B_METADATA_CLOSURE_PATH,
            }
            and findings["currentZeroFindingsClaimed"] is True
            and findings["openP0StageGate"]
            == "REC-I2B-DRAFT-PR38-EXACT-HEAD-CI_THEN_ACCOUNTABLE-ENGINEERING-SECURITY-REVIEW"
            and findings["recI3MayProceedNow"] is False,
            "REC-I2B accountable packet baseline status/routing/findings drift",
        )
    else:
        clean = phase == "successor-clean"
        current = evidence["reviewAndGateTruth"]["currentReviewedSuccessorIndependentDelta"]
        require(
            packet["status"]
            == (
                (
                    "AAPT2_LINUX_METADATA_INDEPENDENT_CLEAN_DRAFT_PR38_EXACT_HEAD_CI_PENDING"
                    if clean
                    else "LOCAL_AAPT2_LINUX_METADATA_REMEDIATION_VERIFIED_INDEPENDENT_DELTA_REVIEW_PENDING"
                )
                if aapt2_linux
                else (
                    "INDEPENDENT_CLEAN_DRAFT_PR38_EXACT_HEAD_CI_PENDING"
                    if clean
                    else "LOCAL_REVIEWED_SUCCESSOR_REMEDIATION_VERIFIED_INDEPENDENT_DELTA_REVIEW_PENDING"
                )
            )
            and packet["routingEligibility"]
            == {
                "eligibleNow": False,
                "eligibleAfterDraftPr38ExactHeadCiGreen": clean,
                "blockingPrecondition": (
                    "DRAFT_PR38_EXACT_HEAD_CI_GREEN"
                    if clean
                    else "FRESH_INDEPENDENT_ADVISORY_DELTA_REVIEW_CLEAN"
                ),
                "historicalIndependentAdvisoryDeltaReviewDisposition": "CLEAN",
                "historicalCleanReviewCoversCurrentTarget": False,
                "laterFreshGraphVerificationMetadataDisposition": "CHANGES_REQUIRED",
                "laterChangesRequiredReviewCoversCurrentTarget": False,
                "priorFreshCacheMetadataIndependentDeltaDisposition": "CLEAN",
                "priorFreshCacheMetadataCleanReviewCoversCurrentTarget": False,
                **(
                    {
                        "priorReviewedSuccessorIndependentDeltaDisposition": "CLEAN",
                        "priorReviewedSuccessorCleanReviewCoversCurrentTarget": False,
                    }
                    if aapt2_linux
                    else {}
                ),
                "currentIndependentAdvisoryDeltaReviewDisposition": "CLEAN" if clean else "PENDING",
                "currentIndependentCleanReviewCoversExactImplementationAndReviewedEvidence": clean,
                "recI3MayProceedNow": False,
            }
            and findings["priorFreshCacheMetadataIndependentDelta"]
            == {
                "reviewedImplementationCommit": REC_I2B_METADATA_IMPLEMENTATION_HEAD,
                "reviewedEvidenceCommit": REC_I2B_METADATA_EVIDENCE_HEAD,
                "disposition": "CLEAN",
                "P0": 0,
                "P1": 0,
                "P2": 0,
                "record": REC_I2B_METADATA_CLOSURE_PATH,
                "coversCurrentTarget": False,
            }
            and (
                findings.get("historicalReviewedSuccessorIndependentDelta")
                == rec_i2b_historical_successor_review_truth()
                if aapt2_linux
                else "historicalReviewedSuccessorIndependentDelta" not in findings
            )
            and findings["currentReviewedSuccessorIndependentDelta"] == current
            and findings["currentZeroFindingsClaimed"] is clean
            and findings["openP0StageGate"]
            == (
                "REC-I2B-DRAFT-PR38-EXACT-HEAD-CI_THEN_ACCOUNTABLE-ENGINEERING-SECURITY-REVIEW"
                if clean
                else "REC-I2B-INDEPENDENT-ADVISORY-DELTA-REVIEW"
            )
            and findings["recI3MayProceedNow"] is False,
            "REC-I2B accountable packet successor status/routing/findings drift",
        )

    common_completion_requirements = {
        "independentExactCommitAndTreeAccountableReviewAfterDeltaClosureRequired": True,
        "allChecklistAnswersByAccountableReviewerRequired": True,
        "reviewerIdentityAndCapacityInSeparateFinalReviewRecordRequired": True,
        "reviewerSignatureOrEquivalentAttestationInSeparateFinalReviewRecordRequired": True,
        "reviewTimestampInSeparateFinalReviewRecordRequired": True,
        "p0P1P2DispositionInSeparateFinalReviewRecordRequired": True,
        "formalDecisionInSeparateFinalReviewRecordRequired": True,
        "thisPreparedPacketMustNotBeEditedIntoAnApproval": True,
    }
    if baseline:
        require(
            packet["recommendedDefault"]
            == {
                "now": "PUBLISH_ONLY_TO_EXISTING_DRAFT_PR38_RUN_EXACT_HEAD_CI_THEN_ROUTE_ACCOUNTABLE_REVIEW",
                "independentDeltaClosure": "CURRENT_FRESH_CACHE_CLEAN_RECORDED",
                "historicalIndependentDeltaClosure": "HISTORICAL_CLEAN_PRESERVED",
                "freshGraphVerificationMetadataReview": (
                    "HISTORICAL_CHANGES_REQUIRED_PRESERVED_AND_CLOSED_BY_CURRENT_CLEAN_REVIEW"
                ),
                "delegatedApprovalRecordedHere": False,
            }
            and packet["reviewCompletionRequirements"]
            == {
                "freshIndependentAdvisoryDeltaReview": {"required": False, "completed": True},
                **common_completion_requirements,
            }
            and packet["terminalGate"]
            == (
                "REC-I3 is not activated. The current fresh-cache metadata closure has an independent "
                "advisory CLEAN 0/0/0 verdict, but Draft PR 38 exact-head CI and the distinct accountable "
                "Engineering/Security review remain required; all accountable answers, identity, signature "
                "and formal decision remain unrecorded."
            ),
            "REC-I2B accountable packet baseline recommendation/completion/terminal gate drift",
        )
    else:
        clean = phase == "successor-clean"
        require(
            packet["recommendedDefault"]
            == {
                "now": (
                    "PUBLISH_ONLY_TO_EXISTING_DRAFT_PR38_RUN_EXACT_HEAD_CI_THEN_ROUTE_ACCOUNTABLE_REVIEW"
                    if clean
                    else "OBTAIN_FRESH_INDEPENDENT_DELTA_REVIEW_BEFORE_ANY_DRAFT_PR38_UPDATE"
                ),
                "independentDeltaClosure": (
                    (
                        "AAPT2_LINUX_METADATA_CLEAN_RECORDED"
                        if clean
                        else "AAPT2_LINUX_METADATA_DELTA_PENDING"
                    )
                    if aapt2_linux
                    else (
                        "REVIEWED_SUCCESSOR_CLEAN_RECORDED"
                        if clean
                        else "REVIEWED_SUCCESSOR_DELTA_PENDING"
                    )
                ),
                "priorFreshCacheIndependentDeltaClosure": "HISTORICAL_CLEAN_PRESERVED",
                **(
                    {"priorReviewedSuccessorIndependentDeltaClosure": "HISTORICAL_CLEAN_PRESERVED"}
                    if aapt2_linux
                    else {}
                ),
                "historicalIndependentDeltaClosure": "HISTORICAL_CLEAN_PRESERVED",
                "freshGraphVerificationMetadataReview": (
                    "HISTORICAL_CHANGES_REQUIRED_PRESERVED_AND_CLOSED_BY_PRIOR_FRESH_CACHE_CLEAN_REVIEW"
                ),
                "delegatedApprovalRecordedHere": False,
            }
            and packet["reviewCompletionRequirements"]
            == {
                "priorFreshCacheIndependentAdvisoryDeltaReview": {
                    "required": False,
                    "completed": True,
                    "coversCurrentTarget": False,
                },
                "reviewedSuccessorIndependentAdvisoryDeltaReview": {
                    "required": not clean,
                    "completed": clean,
                },
                **(
                    {
                        "priorReviewedSuccessorIndependentAdvisoryDeltaReview": {
                            "required": False,
                            "completed": True,
                            "coversCurrentTarget": False,
                        }
                    }
                    if aapt2_linux
                    else {}
                ),
                **common_completion_requirements,
            }
            and packet["terminalGate"]
            == (
                (
                    f"REC-I3 is not activated. Exact implementation {terminal_commit[:7]} and its evidence "
                    "have an independent advisory CLEAN 0/0/0 closure, but Draft PR 38 exact-head CI and "
                    "the distinct accountable Engineering/Security review remain required; all 17 "
                    "accountable answers, identity, signature and formal decision remain unrecorded."
                )
                if clean
                else (
                    f"REC-I3 is not activated. Exact implementation {terminal_commit[:7]} and this pending "
                    "evidence require a fresh independent advisory delta review before any Draft PR 38 "
                    "update or exact-head CI. The distinct accountable Engineering/Security review remains "
                    "incomplete; all 17 accountable answers, identity, signature and formal decision remain "
                    "unrecorded."
                )
            ),
            "REC-I2B accountable packet successor recommendation/completion/terminal gate drift",
        )


def rec_i2b_accountable_closure_reference() -> dict[str, Any]:
    return {
        "path": REC_I2B_ACCOUNTABLE_CLOSURE_PATH,
        "bytes": REC_I2B_ACCOUNTABLE_CLOSURE_BYTES,
        "sha256": REC_I2B_ACCOUNTABLE_CLOSURE_SHA256,
    }


def rec_i2b_accountable_reviewer() -> dict[str, Any]:
    return {
        "name": "Novikova Katerina",
        "capacity": "individual professional capacity",
        "projectRole": "Distinct accountable Stage 0 REC-I2B Engineering/Security reviewer",
        "corporateApproval": False,
    }


def rec_i2b_accountable_review_summary() -> dict[str, Any]:
    return {
        "recordId": "REC-I2B-ACCOUNTABLE-HUMAN-REVIEW-SANITIZED-CLOSURE-20260818-01",
        "record": rec_i2b_accountable_closure_reference(),
        "reviewer": rec_i2b_accountable_reviewer(),
        "reviewDate": REC_I2B_ACCOUNTABLE_REVIEW_DATE,
        "channel": "SAME_AUTHORIZED_PRIVATE_EMAIL_VERIFIED",
        "answerCount": 17,
        "allAnswers": "YES",
        "findingCounts": {"P0": 0, "P1": 0, "P2": 0},
        "disposition": REC_I2B_ACCOUNTABLE_DISPOSITION,
        "accountableReviewCompleted": True,
        "recI3Activated": False,
        "separateRecI3ActivationDecisionRequired": True,
    }


def rec_i2b_accountable_exact_head_ci() -> dict[str, Any]:
    return {
        "workflow": "Android CI",
        "runId": 32143053829,
        "head": REC_I2B_ACCOUNTABLE_RECORDING_BASE_HEAD,
        "conclusion": "SUCCESS",
        "androidBootstrapJobId": 95730094951,
        "searchSmokeJobId": 95730095098,
    }


def expected_rec_i2b_accountable_closure() -> dict[str, Any]:
    return {
        "schemaVersion": 1,
        "recordId": "REC-I2B-ACCOUNTABLE-HUMAN-REVIEW-SANITIZED-CLOSURE-20260818-01",
        "pocId": "POC-RECOVERY-001",
        "stage": "STAGE_0_ISOLATED_POC",
        "reviewType": (
            "FORMAL_DISTINCT_ACCOUNTABLE_REC_I2B_IMPLEMENTATION_ENGINEERING_SECURITY_REVIEW"
        ),
        "formalReviewer": True,
        "reviewMode": "READ_ONLY_EXACT_IMPLEMENTATION_AND_EVIDENCE_REVIEW",
        "reviewDate": REC_I2B_ACCOUNTABLE_REVIEW_DATE,
        "recordedAt": REC_I2B_ACCOUNTABLE_RECORDED_AT,
        "recordedTimeZone": "Europe/Moscow",
        "authority": {
            "sanitizedClosureAuthorization": REC_I2B_ACCOUNTABLE_CLOSURE_AUTHORIZATION,
            "validatorPhaseAuthorization": REC_I2B_ACCOUNTABLE_VALIDATOR_AUTHORIZATION,
            "routingAuthorization": "REC-I2B-ACCOUNTABLE-REVIEW-ROUTING-AUTH-20260817-01",
        },
        "reviewer": {
            **rec_i2b_accountable_reviewer(),
            "distinctFromImplementationAndEvidenceAuthor": True,
            "acceptsAccountabilityForDisposition": True,
        },
        "receipt": {
            "channel": "SAME_AUTHORIZED_PRIVATE_EMAIL_VERIFIED",
            "canonicalCorrectedResponse": True,
            "equivalentWrittenAttestationRecorded": True,
            "onlySanitizedNormalizedFactsRecorded": True,
        },
        "reviewedTarget": {
            "branch": REC_I2B_BRANCH,
            "pullRequestNumber": REC_I2B_PULL_REQUEST_NUMBER,
            "publishedReviewPackageHead": REC_I2B_ACCOUNTABLE_RECORDING_BASE_HEAD,
            "publishedReviewPackageTree": REC_I2B_ACCOUNTABLE_RECORDING_BASE_TREE,
            "terminalImplementationCommit": "1aedccf477fb4d3ff20a48a1834b22b317c8c880",
            "terminalImplementationTree": "744ee822d42b7caccd7abcc68f03dca1c24f5945",
            "independentReviewedEvidenceCommit": "991d61d34129f0a6afba2940a7297cb5207143cc",
            "independentReviewedEvidenceTree": "691ecc5ff43e78da79d3bdff3a3f94b4e817d8ae",
            "implementationEvidenceSnapshot": {
                "path": REC_I2B_RUNTIME_EVIDENCE_PATH,
                "gitBlobSha1": "155166d81206b73254cb163946201d23a10d8686",
                "bytes": 56992,
                "sha256": "5c933573d972cf21de4719f636b9e721905336789babf72921dcdd903b724c42",
            },
            "accountableReviewPacketSnapshot": {
                "path": REC_I2B_ACCOUNTABLE_PACKET_PATH,
                "gitBlobSha1": "a1b5e90198d8047efefa9e89da915196e84620db",
                "bytes": 52810,
                "sha256": "ffdebc8fe8eec7b338563b5e80ccbc771bbba2219f4f9f3c91371642e32b049a",
            },
            "independentAdvisoryCleanClosure": {
                "path": REC_I2B_AAPT2_REVIEW_CLOSURE_PATH,
                "bytes": 10680,
                "sha256": "659fe48a810a872de10941575d90a6161d112ca728f364b45831220eff7c0ed6",
                "disposition": "CLEAN",
                "findingCounts": {"P0": 0, "P1": 0, "P2": 0},
            },
        },
        "responses": [
            {"id": f"I2B-REV-{index:02d}", "answer": "YES"} for index in range(1, 18)
        ],
        "findings": {"P0": 0, "P1": 0, "P2": 0, "items": []},
        "disposition": REC_I2B_ACCOUNTABLE_DISPOSITION,
        "reviewEffect": {
            "accountableRecI2bImplementationReviewCompleted": True,
            "implementationAndEvidenceAcceptedAsInputToSeparateRecI3Decision": True,
            "recI3Activated": False,
            "separateRecI3ActivationDecisionRequired": True,
        },
        "authorityBoundary": {
            "productionSecurityApproval": False,
            "productionLegalApproval": False,
            "corporateApproval": False,
            "dependencyAdmission": False,
            "productionAdmission": False,
            "deviceOrEmulatorExecutionAuthorization": False,
            "androidKeystoreRuntimeExecutionAuthorization": False,
            "harnessOrCampaignAuthorization": False,
            "measurementAuthorization": False,
            "metricPassOrPocPass": False,
            "readinessClosure": False,
            "readyForReviewTransition": False,
            "mergeAuthorization": False,
            "recI3Activation": False,
        },
        "privacy": {
            "emailAddressRecorded": False,
            "messageOrThreadIdentifierRecorded": False,
            "mailHeaderRecorded": False,
            "rawOrQuotedMessageContentRecorded": False,
            "deliveryMetadataRecorded": False,
            "credentialOrSecretRecorded": False,
            "userSpecificAbsolutePathRecorded": False,
        },
    }


def validate_rec_i2b_accountable_closure(
    closure_override: dict[str, Any] | None = None,
) -> dict[str, Any]:
    payload = (ROOT / REC_I2B_ACCOUNTABLE_CLOSURE_PATH).read_bytes()
    require(
        len(payload) == REC_I2B_ACCOUNTABLE_CLOSURE_BYTES
        and hashlib.sha256(payload).hexdigest() == REC_I2B_ACCOUNTABLE_CLOSURE_SHA256
        and not payload.startswith(b"\xef\xbb\xbf")
        and b"\r" not in payload,
        "REC-I2B accountable human closure byte/hash/UTF-8-LF boundary drift",
    )
    closure = closure_override or json.loads(payload.decode("utf-8", errors="strict"))
    require(
        closure == expected_rec_i2b_accountable_closure(),
        "REC-I2B accountable human closure exact sanitized semantics drift",
    )
    return closure


def expected_rec_i2b_accountable_evidence(prior: dict[str, Any]) -> dict[str, Any]:
    expected = copy.deepcopy(prior)
    expected["lastUpdatedAt"] = REC_I2B_ACCOUNTABLE_RECORDED_AT
    expected["status"] = REC_I2B_ACCOUNTABLE_STATUS
    expected["remediationState"] = (
        "ACCOUNTABLE_HUMAN_REVIEW_SANITIZED_CLOSURE_RECORDED_PENDING_INDEPENDENT_METADATA_REVIEW"
    )
    expected["claimCeiling"] = REC_I2B_ACCOUNTABLE_CLAIM_CEILING
    expected["scope"] = (
        "LOCAL_UNPUBLISHED_SANITIZED_ACCOUNTABLE_REVIEW_CLOSURE_PENDING_INDEPENDENT_METADATA_REVIEW"
    )
    authority = expected["authority"]
    authority.update(
        {
            "accountableHumanReviewSanitizedClosureAuthorization": (
                REC_I2B_ACCOUNTABLE_CLOSURE_AUTHORIZATION
            ),
            "accountableHumanReviewValidatorPhaseAuthorization": (
                REC_I2B_ACCOUNTABLE_VALIDATOR_AUTHORIZATION
            ),
            "accountableEngineeringSecurityReviewCompleted": True,
            "draftPr38NonForcePushAllowed": False,
            "accountableClosureDraftPr38NonForceUpdateAllowedAfterIndependentReview": True,
        }
    )
    git_record = expected["git"]
    git_record.update(
        {
            "terminalImplementationPublished": True,
            "publishedDraftHeadAtAccountableReviewReceipt": REC_I2B_ACCOUNTABLE_RECORDING_BASE_HEAD,
            "publishedDraftTreeAtAccountableReviewReceipt": REC_I2B_ACCOUNTABLE_RECORDING_BASE_TREE,
            "recordingBaseHead": REC_I2B_ACCOUNTABLE_RECORDING_BASE_HEAD,
            "recordingBaseTree": REC_I2B_ACCOUNTABLE_RECORDING_BASE_TREE,
            "exactHeadCiAtAccountableReviewReceipt": rec_i2b_accountable_exact_head_ci(),
        }
    )
    successor = expected["advisoryRemediation"][
        "reviewedSuccessorValidatorAndCiMetadataRemediation"
    ]
    successor["aapt2LinuxVerificationMetadataRemediation"][
        "accountableEngineeringSecurityReviewCompleted"
    ] = True
    successor["accountableEngineeringSecurityReviewCompleted"] = True
    expected["accountableHumanReview"] = rec_i2b_accountable_review_summary()
    truth = expected["reviewAndGateTruth"]
    truth["distinctAccountableEngineeringSecurityReviewRequired"] = False
    truth["accountableEngineeringSecurityReviewCompleted"] = True
    truth["accountableHumanReviewClosure"] = {
        **rec_i2b_accountable_closure_reference(),
        "reviewDate": REC_I2B_ACCOUNTABLE_REVIEW_DATE,
        "findingCounts": {"P0": 0, "P1": 0, "P2": 0},
        "disposition": REC_I2B_ACCOUNTABLE_DISPOSITION,
    }
    truth["openStageGate"] = "REC-I3-SEPARATE-ACTIVATION-DECISION_REQUIRED"
    expected["nextGate"] = (
        "Obtain a fresh independent read-only metadata review of this sanitized accountable closure. "
        "After that review is CLEAN, a separate owner decision is still required to activate REC-I3; "
        "no REC-I3, execution, admission, Ready or merge authority is recorded here."
    )
    return expected


def expected_rec_i2b_accountable_packet(
    prior: dict[str, Any],
    *,
    evidence_bytes: bytes,
) -> dict[str, Any]:
    expected = copy.deepcopy(prior)
    expected["refreshedAt"] = REC_I2B_ACCOUNTABLE_RECORDED_AT
    expected["status"] = REC_I2B_ACCOUNTABLE_STATUS
    expected["claimCeiling"] = REC_I2B_ACCOUNTABLE_CLAIM_CEILING
    expected["routingEligibility"].update(
        {
            "eligibleAfterDraftPr38ExactHeadCiGreen": False,
            "accountableReviewRoutingCompleted": True,
            "blockingPrecondition": "SEPARATE_REC_I3_ACTIVATION_DECISION",
            "accountableEngineeringSecurityReviewDisposition": REC_I2B_ACCOUNTABLE_DISPOSITION,
            "accountableEngineeringSecurityReviewCompleted": True,
        }
    )
    target = expected["reviewTarget"]
    target.update(
        {
            "publishedAtPacketRefresh": True,
            "publishedDraftBaselineHead": REC_I2B_ACCOUNTABLE_RECORDING_BASE_HEAD,
            "publishedDraftBaselineTree": REC_I2B_ACCOUNTABLE_RECORDING_BASE_TREE,
            "historicalPreparedPacketSnapshot": {
                "commit": REC_I2B_ACCOUNTABLE_RECORDING_BASE_HEAD,
                "path": REC_I2B_ACCOUNTABLE_PACKET_PATH,
                "gitBlobSha1": "a1b5e90198d8047efefa9e89da915196e84620db",
                "bytes": 52810,
                "sha256": (
                    "ffdebc8fe8eec7b338563b5e80ccbc771bbba2219f4f9f3c91371642e32b049a"
                ),
            },
            "accountableHumanReviewClosure": rec_i2b_accountable_closure_reference(),
            "exactHeadCiAtAccountableReviewReceipt": rec_i2b_accountable_exact_head_ci(),
        }
    )
    target["implementationEvidence"] = {
        "path": REC_I2B_RUNTIME_EVIDENCE_PATH,
        "bytes": len(evidence_bytes),
        "sha256": hashlib.sha256(evidence_bytes).hexdigest(),
    }
    authority = expected["authorityBoundary"]
    authority["implementationAuthority"].extend(
        [
            REC_I2B_ACCOUNTABLE_CLOSURE_AUTHORIZATION,
            REC_I2B_ACCOUNTABLE_VALIDATOR_AUTHORIZATION,
        ]
    )
    authority.update(
        {
            "reviewerIdentityRecorded": True,
            "reviewerSignatureRecorded": False,
            "reviewerEquivalentAttestationRecorded": True,
            "formalApprovalRecorded": True,
            "accountableEngineeringSecurityReviewCompleted": True,
            "accountableClosureDraftPr38NonForceUpdateAuthorizedAfterIndependentReview": True,
        }
    )
    for item in expected["reviewChecklist"]:
        item["accountableReviewerAnswer"] = "YES"
    expected["recommendedDefault"].update(
        {
            "now": (
                "OBTAIN_FRESH_INDEPENDENT_METADATA_REVIEW_THEN_REQUIRE_SEPARATE_REC_I3_"
                "ACTIVATION_DECISION"
            ),
            "accountableHumanReviewRecordedInSeparateClosure": True,
            "recI3Activated": False,
        }
    )
    expected["findingsTruth"]["accountableHumanReview"] = {
        "record": rec_i2b_accountable_closure_reference(),
        "reviewDate": REC_I2B_ACCOUNTABLE_REVIEW_DATE,
        "answerCount": 17,
        "allAnswers": "YES",
        "findingCounts": {"P0": 0, "P1": 0, "P2": 0},
        "disposition": REC_I2B_ACCOUNTABLE_DISPOSITION,
        "completed": True,
        "recI3Activated": False,
    }
    expected["findingsTruth"]["openP0StageGate"] = (
        "REC-I3-SEPARATE-ACTIVATION-DECISION_REQUIRED"
    )
    completion = expected["reviewCompletionRequirements"]
    del completion["thisPreparedPacketMustNotBeEditedIntoAnApproval"]
    completion.update(
        {
            "originalPreparedPacketMustNotBeEditedIntoAnApproval": True,
            "originalPreparedPacketPreservedByExactGitAndHashSnapshot": True,
            "completion": {
                "independentExactCommitAndTreeAccountableReviewAfterDeltaClosureCompleted": True,
                "allChecklistAnswersByAccountableReviewerCompleted": True,
                "reviewerIdentityAndCapacityInSeparateFinalReviewRecordCompleted": True,
                "reviewerSignatureOrEquivalentAttestationInSeparateFinalReviewRecordCompleted": True,
                "reviewTimestampInSeparateFinalReviewRecordCompleted": True,
                "p0P1P2DispositionInSeparateFinalReviewRecordCompleted": True,
                "formalDecisionInSeparateFinalReviewRecordCompleted": True,
            },
        }
    )
    expected["terminalGate"] = (
        "The distinct accountable Engineering/Security review of exact REC-I2B is complete with "
        "17/17 YES, P0/P1/P2=0/0/0 and disposition "
        "APPROVE_EXACT_REC_I2B_FOR_SEPARATE_REC_I3_ACTIVATION_DECISION. REC-I3 is not activated; "
        "a fresh independent metadata review of this sanitized closure and a separate REC-I3 "
        "activation decision remain required. No device, emulator, harness, campaign, measurement, "
        "PASS, admission, Ready or merge authority is recorded."
    )
    return expected


def validate_rec_i2b_accountable_recording_scope() -> None:
    require(
        git_output("rev-parse", f"{REC_I2B_ACCOUNTABLE_RECORDING_BASE_HEAD}^{{tree}}")
        == REC_I2B_ACCOUNTABLE_RECORDING_BASE_TREE,
        "REC-I2B accountable recording base tree drift",
    )
    exact_paths = sorted(
        [
            REC_I2B_RUNTIME_EVIDENCE_PATH,
            REC_I2B_ACCOUNTABLE_PACKET_PATH,
            REC_I2B_ACCOUNTABLE_CLOSURE_PATH,
            "tools/validate_poc_recovery_governance.py",
        ]
    )
    head = git_output("rev-parse", "HEAD")
    if head == REC_I2B_ACCOUNTABLE_RECORDING_BASE_HEAD:
        status = subprocess.run(
            ["git", "status", "--porcelain=v1", "--untracked-files=all"],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
        ).stdout.splitlines()
        paths = sorted(line[3:].replace("\\", "/") for line in status)
        require(paths == exact_paths, "REC-I2B accountable precommit scope drift")
        return
    require(
        git_is_ancestor(REC_I2B_ACCOUNTABLE_RECORDING_BASE_HEAD, head),
        "REC-I2B accountable closure head does not descend from exact recording base",
    )
    closure_commits = git_output(
        "rev-list",
        "--reverse",
        f"{REC_I2B_ACCOUNTABLE_RECORDING_BASE_HEAD}..{head}",
        "--",
        *exact_paths,
    ).splitlines()
    require(
        len(closure_commits) == 1,
        "REC-I2B accountable closure paths changed outside one exact recording commit",
    )
    closure_commit = closure_commits[0]
    require(
        git_output("show", "-s", "--format=%P", closure_commit)
        == REC_I2B_ACCOUNTABLE_RECORDING_BASE_HEAD,
        "REC-I2B accountable closure recording commit is not a direct child of exact base",
    )
    changed = sorted(
        git_path_records(
            "diff-tree",
            "--no-commit-id",
            "--name-only",
            "--no-renames",
            "-r",
            "-z",
            closure_commit,
            "--",
        )
    )
    require(changed == exact_paths, "REC-I2B accountable closure commit path scope drift")
    require(
        subprocess.run(
            ["git", "diff", "--quiet", closure_commit, head, "--", *exact_paths],
            cwd=ROOT,
            check=False,
        ).returncode
        == 0,
        "REC-I2B accountable closure bytes changed after exact recording commit",
    )
    dirty = subprocess.run(
        ["git", "status", "--porcelain=v1", "--untracked-files=all", "--", *exact_paths],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    ).stdout
    require(not dirty, "REC-I2B accountable closure paths are dirty after recording")


def validate_rec_i2b_accountable_phase(
    evidence: dict[str, Any],
    *,
    evidence_bytes: bytes,
    packet: dict[str, Any],
    closure_override: dict[str, Any] | None = None,
    validate_scope: bool = True,
) -> str:
    if validate_scope:
        validate_rec_i2b_accountable_recording_scope()
    prior_evidence_bytes = git_blob_bytes(
        f"{REC_I2B_ACCOUNTABLE_RECORDING_BASE_HEAD}:{REC_I2B_RUNTIME_EVIDENCE_PATH}"
    )
    prior_packet_bytes = git_blob_bytes(
        f"{REC_I2B_ACCOUNTABLE_RECORDING_BASE_HEAD}:{REC_I2B_ACCOUNTABLE_PACKET_PATH}"
    )
    require(
        len(prior_evidence_bytes) == 56992
        and hashlib.sha256(prior_evidence_bytes).hexdigest()
        == "5c933573d972cf21de4719f636b9e721905336789babf72921dcdd903b724c42"
        and len(prior_packet_bytes) == 52810
        and hashlib.sha256(prior_packet_bytes).hexdigest()
        == "ffdebc8fe8eec7b338563b5e80ccbc771bbba2219f4f9f3c91371642e32b049a",
        "REC-I2B historical prepared evidence/packet snapshot drift",
    )
    prior_evidence = json.loads(prior_evidence_bytes.decode("utf-8", errors="strict"))
    prior_packet = json.loads(prior_packet_bytes.decode("utf-8", errors="strict"))
    terminal_commit = "1aedccf477fb4d3ff20a48a1834b22b317c8c880"
    terminal_tree = "744ee822d42b7caccd7abcc68f03dca1c24f5945"
    prior_phase = validate_rec_i2b_runtime_evidence(
        prior_evidence,
        terminal_commit=terminal_commit,
        terminal_tree=terminal_tree,
    )
    require(prior_phase == "successor-clean", "REC-I2B historical prepared phase drift")
    validate_rec_i2b_accountable_packet(
        prior_packet,
        evidence=prior_evidence,
        evidence_bytes=prior_evidence_bytes,
        terminal_commit=terminal_commit,
        terminal_tree=terminal_tree,
        phase=prior_phase,
    )
    validate_rec_i2b_accountable_closure(closure_override)
    require(
        evidence == expected_rec_i2b_accountable_evidence(prior_evidence),
        "REC-I2B accountable implementation evidence overlay drift",
    )
    require(
        packet == expected_rec_i2b_accountable_packet(prior_packet, evidence_bytes=evidence_bytes),
        "REC-I2B accountable review packet overlay drift",
    )
    require(
        evidence["notPerformedOrClaimed"]["recI3Activation"] is False
        and packet["routingEligibility"]["recI3MayProceedNow"] is False
        and packet["authorityBoundary"]["recI3Activated"] is False,
        "REC-I2B accountable closure improperly activated REC-I3",
    )
    return "accountable-clean"


def rec_i2b_accountable_metadata_closure_reference() -> dict[str, Any]:
    return {
        "path": REC_I2B_ACCOUNTABLE_METADATA_CLOSURE_PATH,
        "bytes": REC_I2B_ACCOUNTABLE_METADATA_CLOSURE_BYTES,
        "sha256": REC_I2B_ACCOUNTABLE_METADATA_CLOSURE_SHA256,
    }


def rec_i2b_accountable_metadata_review_summary() -> dict[str, Any]:
    return {
        "record": rec_i2b_accountable_metadata_closure_reference(),
        "reviewedCommit": REC_I2B_ACCOUNTABLE_METADATA_BASE_HEAD,
        "reviewedTree": REC_I2B_ACCOUNTABLE_METADATA_BASE_TREE,
        "findingCounts": {"P0": 0, "P1": 0, "P2": 0},
        "disposition": "CLEAN",
        "formalReviewer": False,
        "completed": True,
    }


def validate_rec_i2b_accountable_metadata_closure(
    closure_override: dict[str, Any] | None = None,
) -> dict[str, Any]:
    payload = (ROOT / REC_I2B_ACCOUNTABLE_METADATA_CLOSURE_PATH).read_bytes()
    require(
        len(payload) == REC_I2B_ACCOUNTABLE_METADATA_CLOSURE_BYTES
        and hashlib.sha256(payload).hexdigest() == REC_I2B_ACCOUNTABLE_METADATA_CLOSURE_SHA256
        and not payload.startswith(b"\xef\xbb\xbf")
        and b"\r" not in payload,
        "REC-I2B accountable independent metadata closure byte/hash/UTF-8-LF boundary drift",
    )
    closure = (
        closure_override
        if closure_override is not None
        else json.loads(payload.decode("utf-8", errors="strict"))
    )
    require(
        semantic_sha256(closure) == REC_I2B_ACCOUNTABLE_METADATA_CLOSURE_SEMANTIC_SHA256,
        "REC-I2B accountable independent metadata closure exact sanitized semantics drift",
    )
    reviewed = closure["exactReviewedState"]
    require(
        reviewed["reviewedHead"] == REC_I2B_ACCOUNTABLE_METADATA_BASE_HEAD
        and reviewed["reviewedTree"] == REC_I2B_ACCOUNTABLE_METADATA_BASE_TREE
        and reviewed["reviewedParent"] == REC_I2B_ACCOUNTABLE_RECORDING_BASE_HEAD
        and git_output(
            "rev-parse", f"{REC_I2B_ACCOUNTABLE_METADATA_BASE_HEAD}^{{tree}}"
        )
        == REC_I2B_ACCOUNTABLE_METADATA_BASE_TREE
        and git_output("show", "-s", "--format=%P", REC_I2B_ACCOUNTABLE_METADATA_BASE_HEAD)
        == REC_I2B_ACCOUNTABLE_RECORDING_BASE_HEAD,
        "REC-I2B accountable independent metadata reviewed commit/tree/parent drift",
    )
    for reviewed_file in reviewed["reviewedFiles"]:
        path = reviewed_file["path"]
        commit = reviewed_file["commit"]
        raw = git_blob_bytes(f"{commit}:{path}")
        require(
            commit == REC_I2B_ACCOUNTABLE_METADATA_BASE_HEAD
            and git_output("rev-parse", f"{commit}:{path}") == reviewed_file["gitBlobSha1"]
            and len(raw) == reviewed_file["rawGitBytes"]
            and hashlib.sha256(raw).hexdigest() == reviewed_file["rawGitSha256"],
            f"REC-I2B accountable independent metadata reviewed file drift: {path}",
        )
    return closure


def expected_rec_i2b_accountable_metadata_evidence(prior: dict[str, Any]) -> dict[str, Any]:
    expected = copy.deepcopy(prior)
    expected["lastUpdatedAt"] = REC_I2B_ACCOUNTABLE_METADATA_RECORDED_AT
    expected["remediationState"] = REC_I2B_ACCOUNTABLE_METADATA_CLEAN_REMEDIATION_STATE
    expected["scope"] = (
        "LOCAL_UNPUBLISHED_SANITIZED_ACCOUNTABLE_REVIEW_CLOSURE_INDEPENDENT_METADATA_CLEAN_"
        "DRAFT_PR38_INTEGRATION_PENDING"
    )
    expected["authority"].update(
        {
            "accountableClosureIntegrationAuthorization": (
                REC_I2B_ACCOUNTABLE_INTEGRATION_AUTHORIZATION
            ),
            "accountableClosureIndependentMetadataReviewAuthorization": (
                REC_I2B_ACCOUNTABLE_METADATA_CLOSURE_AUTHORIZATION
            ),
            "draftPr38NonForcePushAllowed": True,
            "accountableClosureIndependentMetadataReviewCompleted": True,
            "accountableClosureDraftPr38NonForceUpdateAllowedNow": True,
        }
    )
    expected["git"].update(
        {
            "accountableHumanReviewClosureCommit": REC_I2B_ACCOUNTABLE_METADATA_BASE_HEAD,
            "accountableHumanReviewClosureTree": REC_I2B_ACCOUNTABLE_METADATA_BASE_TREE,
        }
    )
    expected["accountableHumanReview"][
        "independentMetadataReview"
    ] = rec_i2b_accountable_metadata_review_summary()
    expected["reviewAndGateTruth"][
        "accountableClosureIndependentMetadataReview"
    ] = {
        **rec_i2b_accountable_metadata_review_summary(),
        "coversCurrentAccountableClosure": True,
    }
    expected["reviewAndGateTruth"]["openStageGate"] = (
        "REC-I2B-DRAFT-PR38-INTEGRATION_AND_EXACT_HEAD_CI_THEN_SEPARATE_REC_I3_"
        "ACTIVATION_DECISION"
    )
    expected["nextGate"] = (
        "Under REC-I2B-ACCOUNTABLE-CLOSURE-INTEGRATION-AUTH-20260818-01, independently read back "
        "this durable metadata closure, then integrate existing Draft PR 38 by normal non-force "
        "ancestry sync and require exact-head CI plus a fresh no-drift gate. REC-I3 remains "
        "inactive and requires a separate exact activation decision after terminal REC-I2B "
        "integration; no execution, admission, PASS or readiness authority is recorded here."
    )
    return expected


def expected_rec_i2b_accountable_metadata_packet(
    prior: dict[str, Any],
    *,
    evidence_bytes: bytes,
) -> dict[str, Any]:
    expected = copy.deepcopy(prior)
    expected["refreshedAt"] = REC_I2B_ACCOUNTABLE_METADATA_RECORDED_AT
    expected["routingEligibility"].update(
        {
            "blockingPrecondition": (
                "DRAFT_PR38_INTEGRATION_AND_EXACT_HEAD_CI_THEN_SEPARATE_REC_I3_"
                "ACTIVATION_DECISION"
            ),
            "accountableClosureIndependentMetadataReviewDisposition": "CLEAN",
            "accountableClosureIndependentMetadataReviewCompleted": True,
        }
    )
    expected["reviewTarget"]["implementationEvidence"] = {
        "path": REC_I2B_RUNTIME_EVIDENCE_PATH,
        "bytes": len(evidence_bytes),
        "sha256": hashlib.sha256(evidence_bytes).hexdigest(),
    }
    expected["reviewTarget"][
        "accountableClosureIndependentMetadataReview"
    ] = rec_i2b_accountable_metadata_review_summary()
    expected["authorityBoundary"]["implementationAuthority"].extend(
        [
            REC_I2B_ACCOUNTABLE_INTEGRATION_AUTHORIZATION,
            REC_I2B_ACCOUNTABLE_METADATA_CLOSURE_AUTHORIZATION,
        ]
    )
    expected["authorityBoundary"].update(
        {
            "accountableClosureIndependentMetadataReviewCompleted": True,
            "accountableClosureDraftPr38NonForceUpdateAuthorizedNow": True,
        }
    )
    expected["recommendedDefault"].update(
        {
            "now": (
                "INDEPENDENTLY_READ_BACK_THIS_DURABLE_CLOSURE_THEN_INTEGRATE_DRAFT_PR38_RUN_"
                "EXACT_HEAD_CI_AND_REQUIRE_SEPARATE_REC_I3_ACTIVATION_DECISION"
            ),
            "accountableClosureIndependentMetadataReview": (
                "CLEAN_RECORDED_PENDING_DURABLE_CLOSURE_READBACK"
            ),
        }
    )
    expected["findingsTruth"]["accountableClosureIndependentMetadataReview"] = {
        **rec_i2b_accountable_metadata_review_summary(),
        "recI3Activated": False,
    }
    expected["findingsTruth"]["openP0StageGate"] = (
        "REC-I2B-DRAFT-PR38-INTEGRATION_AND_EXACT_HEAD_CI_THEN_SEPARATE_REC_I3_"
        "ACTIVATION_DECISION"
    )
    expected["reviewCompletionRequirements"]["completion"].update(
        {
            "accountableClosureIndependentMetadataReviewCompleted": True,
            "accountableClosureIndependentMetadataReviewDisposition": "CLEAN",
        }
    )
    expected["terminalGate"] = (
        "The distinct accountable Engineering/Security review of exact REC-I2B is complete with "
        "17/17 YES, P0/P1/P2=0/0/0 and disposition "
        "APPROVE_EXACT_REC_I2B_FOR_SEPARATE_REC_I3_ACTIVATION_DECISION. The sanitized closure has "
        "an independent advisory metadata CLEAN 0/0/0 review of exact commit "
        "50bb75d1069d72e843d2c253afabd6c2d28b78e1. After this durable metadata record receives an "
        "independent readback, Draft PR 38 integration and exact-head CI remain required. REC-I3 "
        "is not activated and still requires a separate exact activation decision after terminal "
        "REC-I2B integration; no device, emulator, harness, campaign, measurement, PASS, admission, "
        "readiness or current Ready/merge state is recorded."
    )
    return expected


def validate_rec_i2b_accountable_metadata_recording_scope() -> None:
    require(
        git_output("rev-parse", f"{REC_I2B_ACCOUNTABLE_METADATA_BASE_HEAD}^{{tree}}")
        == REC_I2B_ACCOUNTABLE_METADATA_BASE_TREE,
        "REC-I2B accountable independent metadata recording base tree drift",
    )
    exact_paths = sorted(
        [
            REC_I2B_RUNTIME_EVIDENCE_PATH,
            REC_I2B_ACCOUNTABLE_PACKET_PATH,
            REC_I2B_ACCOUNTABLE_METADATA_CLOSURE_PATH,
            "tools/validate_poc_recovery_governance.py",
        ]
    )
    head = git_output("rev-parse", "HEAD")
    if head == REC_I2B_ACCOUNTABLE_METADATA_BASE_HEAD:
        status = subprocess.run(
            ["git", "status", "--porcelain=v1", "--untracked-files=all"],
            cwd=ROOT,
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
        ).stdout.splitlines()
        paths = sorted(line[3:].replace("\\", "/") for line in status)
        require(paths == exact_paths, "REC-I2B accountable independent metadata precommit scope drift")
        return
    require(
        git_is_ancestor(REC_I2B_ACCOUNTABLE_METADATA_BASE_HEAD, head),
        "REC-I2B accountable independent metadata head does not descend from exact base",
    )
    closure_commits = git_output(
        "rev-list",
        "--reverse",
        f"{REC_I2B_ACCOUNTABLE_METADATA_BASE_HEAD}..{head}",
        "--",
        *exact_paths,
    ).splitlines()
    require(
        len(closure_commits) == 1,
        "REC-I2B accountable independent metadata paths changed outside one exact commit",
    )
    closure_commit = closure_commits[0]
    require(
        git_output("show", "-s", "--format=%P", closure_commit)
        == REC_I2B_ACCOUNTABLE_METADATA_BASE_HEAD,
        "REC-I2B accountable independent metadata commit is not a direct child of exact base",
    )
    changed = sorted(
        git_path_records(
            "diff-tree",
            "--no-commit-id",
            "--name-only",
            "--no-renames",
            "-r",
            "-z",
            closure_commit,
            "--",
        )
    )
    require(changed == exact_paths, "REC-I2B accountable independent metadata commit scope drift")
    require(
        subprocess.run(
            ["git", "diff", "--quiet", closure_commit, head, "--", *exact_paths],
            cwd=ROOT,
            check=False,
        ).returncode
        == 0,
        "REC-I2B accountable independent metadata bytes changed after exact commit",
    )
    dirty = subprocess.run(
        ["git", "status", "--porcelain=v1", "--untracked-files=all", "--", *exact_paths],
        cwd=ROOT,
        check=True,
        capture_output=True,
        text=True,
        encoding="utf-8",
    ).stdout
    require(not dirty, "REC-I2B accountable independent metadata paths are dirty after recording")


def validate_rec_i2b_accountable_metadata_phase(
    evidence: dict[str, Any],
    *,
    evidence_bytes: bytes,
    packet: dict[str, Any],
    closure_override: dict[str, Any] | None = None,
) -> str:
    validate_rec_i2b_accountable_metadata_recording_scope()
    prior_evidence_bytes = git_blob_bytes(
        f"{REC_I2B_ACCOUNTABLE_METADATA_BASE_HEAD}:{REC_I2B_RUNTIME_EVIDENCE_PATH}"
    )
    prior_packet_bytes = git_blob_bytes(
        f"{REC_I2B_ACCOUNTABLE_METADATA_BASE_HEAD}:{REC_I2B_ACCOUNTABLE_PACKET_PATH}"
    )
    require(
        len(prior_evidence_bytes) == 59521
        and hashlib.sha256(prior_evidence_bytes).hexdigest()
        == "42058ce6d68269fbcdd33b5ddd8c0589c2e97cb5896e601f6327b311c1260352"
        and len(prior_packet_bytes) == 55895
        and hashlib.sha256(prior_packet_bytes).hexdigest()
        == "beb19b72910b3f6dc925a8483f9f8ad83d39139e66385cbcdf320fa135e3c6a3",
        "REC-I2B accountable reviewed evidence/packet snapshot drift",
    )
    prior_evidence = json.loads(prior_evidence_bytes.decode("utf-8", errors="strict"))
    prior_packet = json.loads(prior_packet_bytes.decode("utf-8", errors="strict"))
    require(
        validate_rec_i2b_accountable_phase(
            prior_evidence,
            evidence_bytes=prior_evidence_bytes,
            packet=prior_packet,
            validate_scope=False,
        )
        == "accountable-clean",
        "REC-I2B accountable pre-metadata-clean phase drift",
    )
    validate_rec_i2b_accountable_metadata_closure(closure_override)
    require(
        evidence == expected_rec_i2b_accountable_metadata_evidence(prior_evidence),
        "REC-I2B accountable independent metadata evidence overlay drift",
    )
    require(
        packet
        == expected_rec_i2b_accountable_metadata_packet(
            prior_packet,
            evidence_bytes=evidence_bytes,
        ),
        "REC-I2B accountable independent metadata packet overlay drift",
    )
    require(
        evidence["authority"]["recI3Allowed"] is False
        and evidence["notPerformedOrClaimed"]["recI3Activation"] is False
        and packet["routingEligibility"]["recI3MayProceedNow"] is False
        and packet["authorityBoundary"]["recI3Activated"] is False
        and packet["authorityBoundary"]["readyOrMergeAuthorized"] is False,
        "REC-I2B accountable independent metadata closure escalated blocked authority",
    )
    return "accountable-metadata-clean"


def validate_rec_i2b_evidence_boundary() -> None:
    require(
        sha256(REC_I2A_DISPOSITION_PATH) == REC_I2A_DISPOSITION_SHA256,
        "REC-I2A actual-graph Product/IP disposition hash drift",
    )
    disposition = read_json(REC_I2A_DISPOSITION_PATH)
    require(
        disposition["decisionId"] == "REC-I2A-ACTUAL-GRAPH-PRODUCT-IP-DISPOSITION-20260817-01"
        and disposition["status"] == "EVALUATION_APPROVED_FOR_EXACT_REC_I2B_IMPLEMENTATION_INPUT"
        and disposition["authority"]["delegationId"]
        == "STAGE0-TECHNICAL-REMEDIATION-DELEGATION-20260817-01"
        and disposition["authority"]["conditionalImplementationAuthorizationId"]
        == "STAGE0-OWNER-UNLOCK-BATCH-20260817-02",
        "REC-I2A actual-graph Product/IP authority drift",
    )
    approved_graph = disposition["exactGraph"]
    require(
        approved_graph["rootCoordinate"] == "com.google.crypto.tink:tink-android:1.23.0"
        and len(approved_graph["runtimeArtifacts"]) == 6
        and len(approved_graph["testOnlyArtifacts"]) == 2
        and approved_graph["resolvableConfigurationCount"] == 61
        and approved_graph["policyCoveredConfigurationCount"] == 34
        and approved_graph["outsidePolicyBoundaryToolingConfigurationCount"] == 27
        and approved_graph["policyCoveredJsr305ResolvedComponentCount"] == 0
        and approved_graph["policyCoveredPackagedJsr305ClassDefinitionCount"] == 0
        and approved_graph["debugAndReleaseApkJsr305ClassDefinitionCount"] == 0
        and approved_graph["repositoryWideJsr305AbsenceClaimed"] is False
        and approved_graph["nativeOrJniArtifactsPresent"] is False
        and approved_graph["releaseR8PassedWithExactThreeRules"] is True
        and approved_graph["broadDontwarnUsed"] is False
        and approved_graph["unresolvedR8MissingClassCount"] == 0,
        "REC-I2A approved actual graph boundary drift",
    )
    decision = disposition["decision"]
    require(
        decision["claimCeiling"] == "EVALUATION_APPROVED_FOR_EXACT_REC_I2B_IMPLEMENTATION_INPUT"
        and decision["recI2bImplementationInputApproved"] is True
        and decision["recI2bConditionalImplementationAuthorizationActivated"] is True
        and decision["exactGraphDriftRequiresFreshDeltaReview"] is True,
        "REC-I2A decision no longer authorizes only the exact REC-I2B input",
    )
    require(
        all(value is False for value in disposition["notAuthorized"].values()),
        "REC-I2A disposition acquired forbidden execution/admission/merge authority",
    )

    require(
        sha256(REC_I2B_METADATA_CLOSURE_PATH) == REC_I2B_METADATA_CLOSURE_SHA256,
        "REC-I2B immutable metadata independent-closure hash drift",
    )
    closure = read_json(REC_I2B_METADATA_CLOSURE_PATH)
    reviewed = closure["exactReviewedState"]
    require(
        closure["recordId"]
        == "REC-I2B-FRESH-CACHE-METADATA-INDEPENDENT-DELTA-REVIEW-CLOSURE-20260818-01"
        and closure["reviewer"]["product"] == "OpenAI Codex"
        and closure["reviewer"]["formalReviewer"] is False
        and closure["reviewer"]["signature"] is None
        and reviewed["implementationCommit"] == REC_I2B_METADATA_IMPLEMENTATION_HEAD
        and reviewed["implementationTree"] == REC_I2B_METADATA_IMPLEMENTATION_TREE
        and reviewed["evidenceCommit"] == REC_I2B_METADATA_EVIDENCE_HEAD
        and reviewed["evidenceTree"] == REC_I2B_METADATA_EVIDENCE_TREE
        and closure["verdict"] == {
            "disposition": "CLEAN",
            "P0": 0,
            "P1": 0,
            "P2": 0,
            "exactMinimalFix": None,
            "newFindings": [],
        },
        "REC-I2B immutable metadata independent-closure semantics drift",
    )

    evidence_bytes = (ROOT / REC_I2B_RUNTIME_EVIDENCE_PATH).read_bytes()
    evidence = json.loads(evidence_bytes.decode("utf-8", errors="strict"))
    accountable_phase = evidence.get("status") == REC_I2B_ACCOUNTABLE_STATUS
    accountable_metadata_clean = (
        evidence.get("remediationState")
        == REC_I2B_ACCOUNTABLE_METADATA_CLEAN_REMEDIATION_STATE
    )
    terminal_commit = (
        "1aedccf477fb4d3ff20a48a1834b22b317c8c880"
        if accountable_phase
        else git_output(
            "log",
            "-1",
            "--format=%H",
            "--",
            *REC_I2B_SUCCESSOR_IMPLEMENTATION_PATHS,
        )
    )
    terminal_tree = git_output("rev-parse", f"{terminal_commit}^{{tree}}")
    successor_phase = validate_rec_i2b_successor_metadata_sha256(
        terminal_commit,
        canonical_lf_sha256("android/gradle/verification-metadata.xml"),
    )
    require(
        git_is_ancestor(REC_I2B_METADATA_IMPLEMENTATION_HEAD, terminal_commit)
        and git_is_ancestor(terminal_commit, git_output("rev-parse", "HEAD"))
        and git_output("rev-parse", f"{terminal_commit}:android/poc/recovery") == REC_I2B_MODULE_TREE,
        "REC-I2B reviewed-successor terminal commit/tree/module lineage drift",
    )
    if successor_phase == "aapt2-linux":
        require(
            sha256(REC_I2B_SUCCESSOR_REVIEW_CLOSURE_PATH)
            == "678974a985fe49addd7132eb1553e317e4cb9656cd92868567a1dd0a8e7f905a"
            and git_output(
                "rev-parse",
                "--verify",
                f"{REC_I2B_SUCCESSOR_REVIEW_CLOSURE_HEAD}^{{commit}}",
            )
            == REC_I2B_SUCCESSOR_REVIEW_CLOSURE_HEAD
            and git_output(
                "rev-parse",
                f"{REC_I2B_SUCCESSOR_REVIEW_CLOSURE_HEAD}^{{tree}}",
            )
            == REC_I2B_SUCCESSOR_REVIEW_CLOSURE_TREE,
            "REC-I2B historical reviewed-successor CLEAN closure drift",
        )
    packet = read_json(REC_I2B_ACCOUNTABLE_PACKET_PATH)
    if accountable_metadata_clean:
        validate_rec_i2b_accountable_metadata_phase(
            evidence,
            evidence_bytes=evidence_bytes,
            packet=packet,
        )
    elif accountable_phase:
        validate_rec_i2b_accountable_phase(
            evidence,
            evidence_bytes=evidence_bytes,
            packet=packet,
        )
    else:
        phase = validate_rec_i2b_runtime_evidence(
            evidence,
            terminal_commit=terminal_commit,
            terminal_tree=terminal_tree,
        )
        validate_rec_i2b_accountable_packet(
            packet,
            evidence=evidence,
            evidence_bytes=evidence_bytes,
            terminal_commit=terminal_commit,
            terminal_tree=terminal_tree,
            phase=phase,
        )


def validate_rec_i2b_merged_main_nonclaims(
    evidence: dict[str, Any],
    packet: dict[str, Any],
) -> None:
    require(
        evidence["status"] == REC_I2B_ACCOUNTABLE_STATUS
        and evidence["remediationState"] == REC_I2B_ACCOUNTABLE_METADATA_CLEAN_REMEDIATION_STATE,
        "REC-I2B squash-main accountable review/evidence state drift",
    )
    require(
        all(
            evidence["authority"][field] is False
            for field in (
                "deviceOrEmulatorExecutionAllowed",
                "measuredExecutionAllowed",
                "harnessOrCampaignAllowed",
                "recI3Allowed",
                "dependencyAdmissionAllowed",
                "productionAdmissionAllowed",
                "readyOrMergeAllowed",
                "mergeAllowed",
            )
        ),
        "REC-I2B squash-main evidence escalated blocked authority",
    )
    require(
        all(
            evidence["notPerformedOrClaimed"][field] is False
            for field in (
                "deviceOrEmulatorExecution",
                "harnessImplementationOrExecution",
                "orchestratedKillRecoveryCampaign",
                "measurement",
                "metricPass",
                "pocPass",
                "readinessClosure",
                "dependencyAdmission",
                "productionAdmission",
                "recI3Activation",
            )
        ),
        "REC-I2B squash-main evidence acquired a forbidden execution/PASS/admission claim",
    )
    require(
        packet["routingEligibility"]["recI3MayProceedNow"] is False
        and all(
            packet["authorityBoundary"][field] is False
            for field in (
                "recI3Activated",
                "deviceOrEmulatorExecutionAuthorized",
                "harnessOrCampaignAuthorized",
                "measurementAuthorized",
                "dependencyOrProductionAdmissionAuthorized",
                "publicationOrMergeAuthorizedByIndependentReview",
                "readyOrMergeAuthorized",
            )
        ),
        "REC-I2B squash-main packet escalated REC-I3/execution/PASS/admission authority",
    )


def validate_rec_i2b_merged_main_evidence_boundary() -> None:
    require(
        git_output("rev-parse", f"{REC_I2B_MERGED_MAIN_ANCHOR}^{{tree}}")
        == REC_I2B_MERGED_MAIN_TREE,
        "REC-I2B squash-main evidence anchor tree drift",
    )
    post_merge_changes = collect_post_merge_changes(merged_anchor=REC_I2B_MERGED_MAIN_ANCHOR)
    validate_rec_i2b_merged_main_protected_paths(post_merge_changes)

    protected_anchor_paths = [
        path
        for path in git_path_records(
            "ls-tree",
            "-r",
            "--name-only",
            "-z",
            REC_I2B_MERGED_MAIN_ANCHOR,
            "--",
        )
        if path_is_rec_i2b_merged_main_protected(path)
    ]
    require(protected_anchor_paths, "REC-I2B squash-main protected anchor inventory is empty")
    for relative in protected_anchor_paths:
        require(
            git_optional_output("rev-parse", f"HEAD:{relative}")
            == git_output("rev-parse", f"{REC_I2B_MERGED_MAIN_ANCHOR}:{relative}"),
            f"REC-I2B squash-main protected blob differs from merged anchor: {relative}",
        )

    evidence = read_json(REC_I2B_RUNTIME_EVIDENCE_PATH)
    packet = read_json(REC_I2B_ACCOUNTABLE_PACKET_PATH)
    validate_rec_i2b_merged_main_nonclaims(evidence, packet)


def validate_rec_i2b_current_module_boundary(*, merged_main: bool = False) -> None:
    build_text = read_text("android/poc/recovery/build.gradle.kts")
    for fragment in (
        'versionName = "0.1.0-poc-recovery-i2b"',
        'implementation("com.google.crypto.tink:tink-android:1.23.0")',
        'exclude(group = "com.google.code.findbugs", module = "jsr305")',
        "testImplementation(libs.junit4)",
    ):
        require(fragment in build_text, f"REC-I2B build contract missing: {fragment}")
    lock_text = read_text("android/poc/recovery/gradle.lockfile").lower()
    require(
        "com.google.crypto.tink:tink-android:1.23.0" in lock_text,
        "REC-I2B lockfile lacks exact Tink 1.23.0",
    )
    require("jsr305" not in lock_text, "REC-I2B lockfile resolves forbidden JSR305")
    if merged_main:
        validate_rec_i2b_merged_main_evidence_boundary()
    else:
        validate_rec_i2b_evidence_boundary()


def validate_current_rec_i2b_reviewed_successor() -> bool:
    lifecycle = collect_recovery_lifecycle_identity()
    merged_main_identity = collect_rec_i2b_merged_main_identity(lifecycle)
    if rec_i2b_merged_main_candidate(merged_main_identity):
        validate_rec_i2b_merged_main_lifecycle(
            merged_main_identity,
            collect_post_merge_changes(merged_anchor=REC_I2B_MERGED_MAIN_ANCHOR),
        )
        validate_rec_i2b_current_module_boundary(merged_main=True)
        return True
    identity = collect_rec_i2b_successor_identity(lifecycle)
    if not rec_i2b_successor_candidate(identity):
        return False
    validate_rec_i2b_successor_lifecycle(
        identity,
        collect_post_merge_changes(merged_anchor=REC_I2B_RUNTIME_IMPLEMENTATION_HEAD),
    )
    validate_rec_i2b_current_module_boundary()
    return True


def validate_dependency_and_scope_boundary() -> bool:
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

    lifecycle_identity = collect_recovery_lifecycle_identity()
    rec_i2b_merged_main_identity = collect_rec_i2b_merged_main_identity(lifecycle_identity)
    rec_i2b_merged_main_mode = rec_i2b_merged_main_candidate(rec_i2b_merged_main_identity)
    reviewed_v06_tree = git_optional_output("rev-parse", f"{REVIEWED_V06_HEAD}^{{tree}}")
    if rec_i2b_merged_main_mode:
        require(
            reviewed_v06_tree in {None, AUTHORIZED_REVIEWED_TREE}
            and git_output("rev-parse", f"{MERGED_V06_MAIN}^{{tree}}") == AUTHORIZED_REVIEWED_TREE,
            "Historical reviewed v0.6 source/merged tree identity mismatch",
        )
    else:
        require(reviewed_v06_tree == AUTHORIZED_REVIEWED_TREE, "Reviewed technical target tree mismatch")
    if rec_i2b_merged_main_mode:
        lifecycle_mode = validate_rec_i2b_merged_main_lifecycle(
            rec_i2b_merged_main_identity,
            collect_post_merge_changes(merged_anchor=REC_I2B_MERGED_MAIN_ANCHOR),
        )
        rec_i2b_mode = True
    else:
        rec_i2b_identity = collect_rec_i2b_successor_identity(lifecycle_identity)
        rec_i2b_mode = rec_i2b_successor_candidate(rec_i2b_identity)
        if rec_i2b_mode:
            lifecycle_mode = validate_rec_i2b_successor_lifecycle(
                rec_i2b_identity,
                collect_post_merge_changes(merged_anchor=REC_I2B_RUNTIME_IMPLEMENTATION_HEAD),
            )
        else:
            post_merge_candidate = (
                lifecycle_identity.head == REC_I1_MERGED_ANCHOR
                or lifecycle_identity.merged_anchor_is_ancestor
            )
            lifecycle_mode = validate_recovery_lifecycle(
                lifecycle_identity,
                [] if post_merge_candidate else collect_pre_merge_changed_paths(),
                collect_post_merge_changes()
                if post_merge_candidate
                else {"committed": [], "staged": [], "unstaged": [], "untracked": []},
            )

    historical_v06_commit = REVIEWED_V06_HEAD if reviewed_v06_tree is not None else MERGED_V06_MAIN
    for historical_commit in (AUTHORIZED_BASE_HEAD, historical_v06_commit):
        module_snapshot = git_path_records(
            "ls-tree", "-r", "--name-only", "-z", historical_commit, "--", "android/poc/recovery"
        )
        require(not module_snapshot, f"Historical snapshot unexpectedly contains Recovery module: {historical_commit}")
        settings_snapshot = git_output("show", f"{historical_commit}:android/settings.gradle.kts")
        require(":poc:recovery" not in settings_snapshot, f"Historical snapshot unexpectedly includes Recovery: {historical_commit}")

    require(RECOVERY_MODULE.is_dir(), "Authorized Recovery module is missing")
    build_text = read_text("android/poc/recovery/build.gradle.kts")
    if rec_i2b_mode:
        validate_rec_i2b_current_module_boundary(merged_main=rec_i2b_merged_main_mode)
    else:
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
    if not rec_i2b_mode:
        for source in production_sources:
            validate_production_source_text(
                source.read_text(encoding="utf-8"),
                source.relative_to(ROOT).as_posix(),
            )
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
        build_text = build_file.read_text(encoding="utf-8")
        if rec_i2b_mode:
            recovery_project_edge = re.search(
                r'''project\s*\(\s*(?:path\s*=\s*)?["']:poc:recovery["']''',
                build_text,
            ) or re.search(r"projects\s*\.\s*poc\s*\.\s*recovery", build_text)
            require(not recovery_project_edge, f"Production/module edge into Recovery: {build_file}")
        else:
            require(":poc:recovery" not in build_text, f"Production/module edge into Recovery: {build_file}")

    lockfile = RECOVERY_MODULE / "gradle.lockfile"
    if rec_i2b_mode:
        require(lockfile.is_file(), "REC-I2B Recovery lockfile is missing")
    else:
        require(current["moduleLockfilePresent"] is lockfile.is_file(), "Recovery lockfile presence claim mismatch")
    if lockfile.is_file() and not rec_i2b_mode:
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

    if rec_i2b_merged_main_mode:
        profile = REC_I2B_MERGED_MAIN_PROFILE
    elif rec_i2b_mode:
        profile = REC_I2B_PROFILE
    else:
        profile = "REC-I1"
    protected_paths = (
        REC_I2B_MERGED_MAIN_PROTECTED_PATHS
        if rec_i2b_merged_main_mode
        else POST_MERGE_PROTECTED_PATHS
    )
    print(f"PASS {profile} {lifecycle_mode} lifecycle; protected paths: {', '.join(protected_paths)}")
    return rec_i2b_mode


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
        draft=True,
        state="open",
        merged=False,
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


def run_rec_i2b_successor_tests() -> None:
    def pin(commit: str, tree: str, parent: str) -> PinnedCommitIdentity:
        return PinnedCommitIdentity(commit, tree, (parent,), True)

    def changes(**overrides: list[str]) -> dict[str, list[str]]:
        result = {"committed": [], "staged": [], "unstaged": [], "untracked": []}
        result.update(overrides)
        return result

    pull_request = GitHubPullRequestContext(
        repository=GITHUB_REPOSITORY,
        head_repository=GITHUB_REPOSITORY,
        head_ref=REC_I2B_BRANCH,
        head_sha="a" * 40,
        base_ref=GITHUB_BASE_BRANCH,
        base_sha="b" * 40,
        merge_ref=f"refs/pull/{REC_I2B_PULL_REQUEST_NUMBER}/merge",
        merge_sha="c" * 40,
        number=REC_I2B_PULL_REQUEST_NUMBER,
        draft=True,
        state="open",
        merged=False,
    )
    exact = RecoveryI2bSuccessorIdentity(
        head="c" * 40,
        branch=REC_I2B_BRANCH,
        head_module_tree=REC_I2B_MODULE_TREE,
        runtime_implementation=pin(
            REC_I2B_RUNTIME_IMPLEMENTATION_HEAD,
            REC_I2B_RUNTIME_IMPLEMENTATION_TREE,
            REC_I2B_RUNTIME_IMPLEMENTATION_PARENT,
        ),
        runtime_evidence=pin(
            REC_I2B_RUNTIME_EVIDENCE_HEAD,
            REC_I2B_RUNTIME_EVIDENCE_TREE,
            REC_I2B_RUNTIME_IMPLEMENTATION_HEAD,
        ),
        runtime_closure=pin(
            REC_I2B_RUNTIME_CLOSURE_HEAD,
            REC_I2B_RUNTIME_CLOSURE_TREE,
            REC_I2B_RUNTIME_EVIDENCE_HEAD,
        ),
        metadata_implementation=pin(
            REC_I2B_METADATA_IMPLEMENTATION_HEAD,
            REC_I2B_METADATA_IMPLEMENTATION_TREE,
            REC_I2B_METADATA_IMPLEMENTATION_PARENT,
        ),
        metadata_evidence=pin(
            REC_I2B_METADATA_EVIDENCE_HEAD,
            REC_I2B_METADATA_EVIDENCE_TREE,
            REC_I2B_METADATA_IMPLEMENTATION_HEAD,
        ),
        metadata_closure=pin(
            REC_I2B_METADATA_CLOSURE_HEAD,
            REC_I2B_METADATA_CLOSURE_TREE,
            REC_I2B_METADATA_EVIDENCE_HEAD,
        ),
        metadata_closure_is_ancestor_of_pull_request_head=True,
        github_pull_request_context=pull_request,
    )

    require(rec_i2b_successor_candidate(exact), "Exact REC-I2B reviewed successor was not detected")
    require(
        validate_rec_i2b_successor_lifecycle(
            exact,
            changes(committed=["docs/evidence/poc-recovery-001/unrelated.json"]),
        )
        == "rec-i2b-reviewed-successor",
        "Exact REC-I2B pull_request successor selected the wrong lifecycle",
    )
    print("PASS positive lifecycle-rec-i2b-exact-pr38-reviewed-successor")
    main_descendant = replace(
        exact,
        branch=GITHUB_BASE_BRANCH,
        metadata_closure_is_ancestor_of_pull_request_head=False,
        github_pull_request_context=None,
    )
    require(
        validate_rec_i2b_successor_lifecycle(main_descendant, changes())
        == "rec-i2b-reviewed-successor",
        "Exact REC-I2B main descendant selected the wrong lifecycle",
    )
    print("PASS positive lifecycle-rec-i2b-main-descendant")

    def expect_negative(
        name: str,
        identity: RecoveryI2bSuccessorIdentity,
        delta: dict[str, list[str]] | None = None,
    ) -> None:
        try:
            validate_rec_i2b_successor_lifecycle(identity, delta or changes())
        except ValueError:
            print(f"PASS negative lifecycle-rec-i2b-{name}")
            return
        raise ValueError(f"Negative REC-I2B successor test unexpectedly passed: {name}")

    for field in (
        "runtime_implementation",
        "runtime_evidence",
        "runtime_closure",
        "metadata_implementation",
        "metadata_evidence",
        "metadata_closure",
    ):
        value = getattr(exact, field)
        expect_negative(f"{field}-commit", replace(exact, **{field: replace(value, commit="0" * 40)}))
        expect_negative(f"{field}-tree", replace(exact, **{field: replace(value, tree="0" * 40)}))
        expect_negative(f"{field}-parent", replace(exact, **{field: replace(value, parents=("0" * 40,))}))
        expect_negative(
            f"{field}-not-ancestor",
            replace(exact, **{field: replace(value, is_ancestor_of_head=False)}),
        )

    expect_negative("module-subtree", replace(exact, head_module_tree="0" * 40))
    expect_negative("branch", replace(exact, branch="codex/spoof"))
    expect_negative(
        "repository",
        replace(exact, github_pull_request_context=replace(pull_request, repository="attacker/DORA")),
    )
    expect_negative(
        "head-repository",
        replace(exact, github_pull_request_context=replace(pull_request, head_repository="attacker/DORA")),
    )
    expect_negative(
        "head-ref",
        replace(exact, github_pull_request_context=replace(pull_request, head_ref="codex/spoof")),
    )
    expect_negative(
        "base-ref",
        replace(exact, github_pull_request_context=replace(pull_request, base_ref="not-main")),
    )
    expect_negative(
        "pr-number",
        replace(
            exact,
            github_pull_request_context=replace(pull_request, number=REC_I2B_PULL_REQUEST_NUMBER + 1),
        ),
    )
    expect_negative(
        "ready-pr",
        replace(exact, github_pull_request_context=replace(pull_request, draft=False)),
    )
    expect_negative(
        "closed-pr",
        replace(exact, github_pull_request_context=replace(pull_request, state="closed")),
    )
    expect_negative(
        "merged-pr",
        replace(exact, github_pull_request_context=replace(pull_request, merged=True)),
    )
    expect_negative(
        "head-lineage-spoof",
        replace(exact, metadata_closure_is_ancestor_of_pull_request_head=False),
    )

    protected = "android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/crypto/Injected.kt"
    for layer in ("committed", "staged", "unstaged", "untracked"):
        expect_negative(f"{layer}-module-mutation", exact, changes(**{layer: [protected]}))
    expect_negative("committed-reverted-module-mutation", exact, changes(committed=[protected]))


def run_rec_i2b_merged_main_tests() -> None:
    def changes(**overrides: list[str]) -> dict[str, list[str]]:
        result = {"committed": [], "staged": [], "unstaged": [], "untracked": []}
        result.update(overrides)
        return result

    def expect_positive(
        name: str,
        identity: RecoveryI2bMergedMainIdentity,
        delta: dict[str, list[str]],
        expected_mode: str,
    ) -> None:
        require(
            validate_rec_i2b_merged_main_lifecycle(identity, delta) == expected_mode,
            f"REC-I2B squash-main positive test selected the wrong mode: {name}",
        )
        print(f"PASS positive lifecycle-rec-i2b-squash-main-{name}")

    def expect_negative(
        name: str,
        identity: RecoveryI2bMergedMainIdentity,
        delta: dict[str, list[str]] | None = None,
    ) -> None:
        try:
            validate_rec_i2b_merged_main_lifecycle(identity, delta or changes())
        except ValueError:
            print(f"PASS negative lifecycle-rec-i2b-squash-main-{name}")
            return
        raise ValueError(f"Negative REC-I2B squash-main test unexpectedly passed: {name}")

    exact_main = RecoveryI2bMergedMainIdentity(
        head=REC_I2B_MERGED_MAIN_ANCHOR,
        branch=GITHUB_BASE_BRANCH,
        merged_anchor_commit=REC_I2B_MERGED_MAIN_ANCHOR,
        merged_anchor_tree=REC_I2B_MERGED_MAIN_TREE,
        merged_anchor_parents=(REC_I2B_MERGED_MAIN_PARENT,),
        merged_anchor_is_ancestor=True,
        head_module_tree=REC_I2B_MODULE_TREE,
        remediation_head_merge_base=None,
        pull_request_base_contains_merged_anchor=False,
    )
    require(
        rec_i2b_merged_main_candidate(exact_main),
        "Exact REC-I2B squash-main anchor was not detected",
    )
    expect_positive("exact-anchor", exact_main, changes(), "rec-i2b-squash-main")

    post_remediation_main = replace(exact_main, head="d" * 40)
    expect_positive(
        "post-remediation-descendant",
        post_remediation_main,
        changes(committed=[REC_I2B_MERGED_MAIN_VALIDATOR_PATH]),
        "rec-i2b-squash-main",
    )
    remediation_local = replace(
        post_remediation_main,
        branch=REC_I2B_MERGED_MAIN_REMEDIATION_BRANCH,
    )
    expect_positive(
        "exact-local-remediation",
        remediation_local,
        changes(unstaged=[REC_I2B_MERGED_MAIN_VALIDATOR_PATH]),
        "rec-i2b-squash-main-remediation-local",
    )

    pull_request = GitHubPullRequestContext(
        repository=GITHUB_REPOSITORY,
        head_repository=GITHUB_REPOSITORY,
        head_ref=REC_I2B_MERGED_MAIN_REMEDIATION_BRANCH,
        head_sha="e" * 40,
        base_ref=GITHUB_BASE_BRANCH,
        base_sha=REC_I2B_MERGED_MAIN_ANCHOR,
        merge_ref="refs/pull/99/merge",
        merge_sha="f" * 40,
        number=99,
        draft=True,
        state="open",
        merged=False,
    )
    remediation_pr = replace(
        remediation_local,
        head=pull_request.merge_sha,
        remediation_head_merge_base=REC_I2B_MERGED_MAIN_ANCHOR,
        pull_request_base_contains_merged_anchor=True,
        github_pull_request_context=pull_request,
    )
    expect_positive(
        "exact-draft-pr-remediation",
        remediation_pr,
        changes(committed=[REC_I2B_MERGED_MAIN_VALIDATOR_PATH]),
        "rec-i2b-squash-main-remediation-pr",
    )
    unrelated_local = replace(post_remediation_main, branch="codex/unrelated-main-descendant")
    expect_positive(
        "unrelated-local-descendant",
        unrelated_local,
        changes(committed=[REC_I2B_MERGED_MAIN_VALIDATOR_PATH, "docs/unrelated.md"]),
        "rec-i2b-squash-main-descendant-local",
    )
    unrelated_pull_request = replace(
        pull_request,
        head_ref="codex/unrelated-main-descendant",
        draft=False,
    )
    unrelated_pr = replace(
        unrelated_local,
        head=unrelated_pull_request.merge_sha,
        remediation_head_merge_base=REC_I2B_MERGED_MAIN_ANCHOR,
        pull_request_base_contains_merged_anchor=True,
        github_pull_request_context=unrelated_pull_request,
    )
    expect_positive(
        "unrelated-pr-descendant",
        unrelated_pr,
        changes(committed=[REC_I2B_MERGED_MAIN_VALIDATOR_PATH, "docs/unrelated.md"]),
        "rec-i2b-squash-main-descendant-pr",
    )

    source_identity = RecoveryI2bSuccessorIdentity(
        head=REC_I2B_MERGED_MAIN_ANCHOR,
        branch=GITHUB_BASE_BRANCH,
        head_module_tree=REC_I2B_MODULE_TREE,
        runtime_implementation=PinnedCommitIdentity(
            REC_I2B_RUNTIME_IMPLEMENTATION_HEAD,
            REC_I2B_RUNTIME_IMPLEMENTATION_TREE,
            (REC_I2B_RUNTIME_IMPLEMENTATION_PARENT,),
            False,
        ),
        runtime_evidence=PinnedCommitIdentity(
            REC_I2B_RUNTIME_EVIDENCE_HEAD,
            REC_I2B_RUNTIME_EVIDENCE_TREE,
            (REC_I2B_RUNTIME_IMPLEMENTATION_HEAD,),
            False,
        ),
        runtime_closure=PinnedCommitIdentity(
            REC_I2B_RUNTIME_CLOSURE_HEAD,
            REC_I2B_RUNTIME_CLOSURE_TREE,
            (REC_I2B_RUNTIME_EVIDENCE_HEAD,),
            False,
        ),
        metadata_implementation=PinnedCommitIdentity(
            REC_I2B_METADATA_IMPLEMENTATION_HEAD,
            REC_I2B_METADATA_IMPLEMENTATION_TREE,
            (REC_I2B_METADATA_IMPLEMENTATION_PARENT,),
            False,
        ),
        metadata_evidence=PinnedCommitIdentity(
            REC_I2B_METADATA_EVIDENCE_HEAD,
            REC_I2B_METADATA_EVIDENCE_TREE,
            (REC_I2B_METADATA_IMPLEMENTATION_HEAD,),
            False,
        ),
        metadata_closure=PinnedCommitIdentity(
            REC_I2B_METADATA_CLOSURE_HEAD,
            REC_I2B_METADATA_CLOSURE_TREE,
            (REC_I2B_METADATA_EVIDENCE_HEAD,),
            False,
        ),
        metadata_closure_is_ancestor_of_pull_request_head=False,
        github_pull_request_context=None,
    )
    require(
        not rec_i2b_successor_candidate(source_identity)
        and rec_i2b_merged_main_candidate(exact_main),
        "Protected squash-main dispatch still depends on deleted source-branch ancestry",
    )
    print("PASS positive lifecycle-rec-i2b-squash-main-source-objects-unreachable")

    expect_negative("anchor-missing", replace(exact_main, merged_anchor_commit=None))
    expect_negative("anchor-tree", replace(exact_main, merged_anchor_tree="0" * 40))
    expect_negative("anchor-parent", replace(exact_main, merged_anchor_parents=("0" * 40,)))
    expect_negative(
        "non-descendant",
        replace(exact_main, head="0" * 40, merged_anchor_is_ancestor=False),
    )
    expect_negative("module-tree", replace(exact_main, head_module_tree="0" * 40))
    expect_negative(
        "protected-module-path",
        exact_main,
        changes(committed=["android/poc/recovery/build.gradle.kts"]),
    )

    for name, mutated_context in (
        ("pr-repository", replace(pull_request, repository="attacker/DORA")),
        ("pr-head-repository", replace(pull_request, head_repository="attacker/DORA")),
        ("pr-head-ref", replace(pull_request, head_ref="codex/spoof")),
        ("pr-base-ref", replace(pull_request, base_ref="not-main")),
        ("pr-base-sha", replace(pull_request, base_sha="0" * 40)),
        ("pr-ready", replace(pull_request, draft=False)),
        ("pr-closed", replace(pull_request, state="closed")),
        ("pr-merged", replace(pull_request, merged=True)),
    ):
        expect_negative(
            name,
            replace(remediation_pr, github_pull_request_context=mutated_context),
            changes(committed=[REC_I2B_MERGED_MAIN_VALIDATOR_PATH]),
        )
    expect_negative(
        "pr-merge-base",
        replace(remediation_pr, remediation_head_merge_base="0" * 40),
        changes(committed=[REC_I2B_MERGED_MAIN_VALIDATOR_PATH]),
    )
    expect_negative(
        "pr-extra-path",
        remediation_pr,
        changes(committed=[REC_I2B_MERGED_MAIN_VALIDATOR_PATH, "docs/unrelated.md"]),
    )


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

    with tempfile.TemporaryDirectory(prefix="dora-recovery-path-reverted-") as temporary:
        repo, _initial = initialize_test_git_repo(Path(temporary))
        relative = "android/poc/recovery/reverted.bin"
        path = repo / Path(relative)
        path.parent.mkdir(parents=True)
        path.write_bytes(b"reviewed")
        anchor = commit_test_git_repo(repo, "reviewed protected payload")
        path.write_bytes(b"mutated")
        commit_test_git_repo(repo, "mutate protected payload")
        path.write_bytes(b"reviewed")
        commit_test_git_repo(repo, "restore protected payload bytes")
        changes = collect_post_merge_changes(root=repo, merged_anchor=anchor)
        expect_real_git_protected_path_rejection(
            "reverted-committed-history", changes, "committed", relative
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
    draft: bool = True,
    state: str = "open",
    merged: bool = False,
) -> None:
    event = {
        "number": number,
        "repository": {"full_name": GITHUB_REPOSITORY},
        "pull_request": {
            "number": number,
            "merge_commit_sha": merge_sha,
            "draft": draft,
            "state": state,
            "merged": merged,
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

        stale_merge = test_git_text(
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
            input_data=b"older synthetic GitHub merge ref\n",
        )
        require(stale_merge != merge, "Synthetic stale merge fixture unexpectedly reused current merge SHA")
        write_test_pull_request_event(
            event_path,
            number=number,
            head_ref=remediation_branch,
            head_sha=pull_request_head,
            base_sha=base,
            merge_sha=stale_merge,
        )
        collect_github_pull_request_context(
            merge,
            remediation_branch,
            environ=environment(remediation_branch),
            root=repo,
        )
        print("PASS positive github-stale-event-merge-sha-is-non-authoritative")

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


def run_rec_i2b_accountable_phase_tests(*, historical: bool = False) -> None:
    if historical:
        evidence_bytes = git_blob_bytes(
            f"{REC_I2B_ACCOUNTABLE_METADATA_BASE_HEAD}:{REC_I2B_RUNTIME_EVIDENCE_PATH}"
        )
        evidence = json.loads(evidence_bytes.decode("utf-8", errors="strict"))
        packet = json.loads(
            git_blob_bytes(
                f"{REC_I2B_ACCOUNTABLE_METADATA_BASE_HEAD}:{REC_I2B_ACCOUNTABLE_PACKET_PATH}"
            ).decode("utf-8", errors="strict")
        )
    else:
        evidence_bytes = (ROOT / REC_I2B_RUNTIME_EVIDENCE_PATH).read_bytes()
        evidence = json.loads(evidence_bytes.decode("utf-8", errors="strict"))
        packet = read_json(REC_I2B_ACCOUNTABLE_PACKET_PATH)
    closure = read_json(REC_I2B_ACCOUNTABLE_CLOSURE_PATH)
    validate_rec_i2b_accountable_phase(
        evidence,
        evidence_bytes=evidence_bytes,
        packet=packet,
        validate_scope=not historical,
    )

    def expect_accountable_negative(
        name: str,
        *,
        evidence_mutation: Callable[[dict[str, Any]], None] | None = None,
        packet_mutation: Callable[[dict[str, Any]], None] | None = None,
        closure_mutation: Callable[[dict[str, Any]], None] | None = None,
    ) -> None:
        candidate_evidence = copy.deepcopy(evidence)
        candidate_packet = copy.deepcopy(packet)
        candidate_closure = copy.deepcopy(closure)
        if evidence_mutation is not None:
            evidence_mutation(candidate_evidence)
        if packet_mutation is not None:
            packet_mutation(candidate_packet)
        if closure_mutation is not None:
            closure_mutation(candidate_closure)
        try:
            validate_rec_i2b_accountable_phase(
                candidate_evidence,
                evidence_bytes=evidence_bytes,
                packet=candidate_packet,
                closure_override=candidate_closure,
                validate_scope=not historical,
            )
        except (ValueError, KeyError):
            print(f"PASS negative rec-i2b-accountable-{name}")
            return
        raise ValueError(f"Negative REC-I2B accountable mutation unexpectedly passed: {name}")

    closure_mutations: list[tuple[str, Callable[[dict[str, Any]], None]]] = [
        ("reviewer-name", lambda record: record["reviewer"].__setitem__("name", "Spoofed")),
        (
            "reviewer-capacity",
            lambda record: record["reviewer"].__setitem__("capacity", "corporate capacity"),
        ),
        (
            "corporate-approval",
            lambda record: record["reviewer"].__setitem__("corporateApproval", True),
        ),
        (
            "receipt-channel",
            lambda record: record["receipt"].__setitem__("channel", "UNVERIFIED"),
        ),
        ("answer", lambda record: record["responses"][0].__setitem__("answer", "NO")),
        ("finding", lambda record: record["findings"].__setitem__("P1", 1)),
        ("disposition", lambda record: record.__setitem__("disposition", "REJECT")),
        (
            "rec-i3-activation",
            lambda record: record["authorityBoundary"].__setitem__("recI3Activation", True),
        ),
        (
            "privacy-email",
            lambda record: record["privacy"].__setitem__("emailAddressRecorded", True),
        ),
    ]
    for name, mutation in closure_mutations:
        expect_accountable_negative(name, closure_mutation=mutation)

    evidence_mutations: list[tuple[str, Callable[[dict[str, Any]], None]]] = [
        (
            "evidence-closure-sha",
            lambda record: record["accountableHumanReview"]["record"].__setitem__(
                "sha256", "0" * 64
            ),
        ),
        (
            "evidence-review-completion",
            lambda record: record["authority"].__setitem__(
                "accountableEngineeringSecurityReviewCompleted", False
            ),
        ),
        (
            "evidence-rec-i3-authority",
            lambda record: record["authority"].__setitem__("recI3Allowed", True),
        ),
        (
            "evidence-review-truth",
            lambda record: record["reviewAndGateTruth"].__setitem__(
                "accountableEngineeringSecurityReviewCompleted", False
            ),
        ),
    ]
    for name, mutation in evidence_mutations:
        expect_accountable_negative(name, evidence_mutation=mutation)

    packet_mutations: list[tuple[str, Callable[[dict[str, Any]], None]]] = [
        (
            "packet-answer",
            lambda record: record["reviewChecklist"][0].__setitem__(
                "accountableReviewerAnswer", "NO"
            ),
        ),
        (
            "packet-closure-sha",
            lambda record: record["reviewTarget"]["accountableHumanReviewClosure"].__setitem__(
                "sha256", "0" * 64
            ),
        ),
        (
            "packet-formal-approval",
            lambda record: record["authorityBoundary"].__setitem__(
                "formalApprovalRecorded", False
            ),
        ),
        (
            "packet-rec-i3-authority",
            lambda record: record["authorityBoundary"].__setitem__("recI3Activated", True),
        ),
        (
            "packet-historical-snapshot",
            lambda record: record["reviewTarget"]["historicalPreparedPacketSnapshot"].__setitem__(
                "sha256", "0" * 64
            ),
        ),
        (
            "packet-evidence-sha",
            lambda record: record["reviewTarget"]["implementationEvidence"].__setitem__(
                "sha256", "0" * 64
            ),
        ),
        ("packet-status", lambda record: record.__setitem__("status", "REC_I3_ACTIVE")),
    ]
    for name, mutation in packet_mutations:
        expect_accountable_negative(name, packet_mutation=mutation)


def run_rec_i2b_accountable_metadata_phase_tests() -> None:
    evidence_bytes = (ROOT / REC_I2B_RUNTIME_EVIDENCE_PATH).read_bytes()
    evidence = json.loads(evidence_bytes.decode("utf-8", errors="strict"))
    packet = read_json(REC_I2B_ACCOUNTABLE_PACKET_PATH)
    closure = read_json(REC_I2B_ACCOUNTABLE_METADATA_CLOSURE_PATH)
    validate_rec_i2b_accountable_metadata_phase(
        evidence,
        evidence_bytes=evidence_bytes,
        packet=packet,
    )

    def expect_metadata_negative(
        name: str,
        *,
        evidence_mutation: Callable[[dict[str, Any]], None] | None = None,
        packet_mutation: Callable[[dict[str, Any]], None] | None = None,
        closure_mutation: Callable[[dict[str, Any]], None] | None = None,
    ) -> None:
        candidate_evidence = copy.deepcopy(evidence)
        candidate_packet = copy.deepcopy(packet)
        candidate_closure = copy.deepcopy(closure)
        if evidence_mutation is not None:
            evidence_mutation(candidate_evidence)
        if packet_mutation is not None:
            packet_mutation(candidate_packet)
        if closure_mutation is not None:
            closure_mutation(candidate_closure)
        try:
            validate_rec_i2b_accountable_metadata_phase(
                candidate_evidence,
                evidence_bytes=evidence_bytes,
                packet=candidate_packet,
                closure_override=candidate_closure,
            )
        except (ValueError, KeyError):
            print(f"PASS negative rec-i2b-accountable-metadata-{name}")
            return
        raise ValueError(
            f"Negative REC-I2B accountable metadata mutation unexpectedly passed: {name}"
        )

    closure_mutations: list[tuple[str, Callable[[dict[str, Any]], None]]] = [
        (
            "reviewer-role",
            lambda record: record["reviewer"].__setitem__("role", "FORMAL_REVIEWER"),
        ),
        (
            "formal-reviewer",
            lambda record: record["reviewer"].__setitem__("formalReviewer", True),
        ),
        (
            "reviewed-head",
            lambda record: record["exactReviewedState"].__setitem__(
                "reviewedHead", "0" * 40
            ),
        ),
        ("verdict", lambda record: record["verdict"].__setitem__("P1", 1)),
        (
            "rec-i3-authority",
            lambda record: record["authorityBoundary"].__setitem__("activatesRecI3", True),
        ),
        (
            "mail-identifier",
            lambda record: record["sanitization"].__setitem__(
                "containsMailMessageOrThreadIdentifier", True
            ),
        ),
    ]
    for name, mutation in closure_mutations:
        expect_metadata_negative(name, closure_mutation=mutation)

    evidence_mutations: list[tuple[str, Callable[[dict[str, Any]], None]]] = [
        (
            "evidence-review-sha",
            lambda record: record["accountableHumanReview"]["independentMetadataReview"][
                "record"
            ].__setitem__("sha256", "0" * 64),
        ),
        (
            "evidence-reviewed-commit",
            lambda record: record["reviewAndGateTruth"][
                "accountableClosureIndependentMetadataReview"
            ].__setitem__("reviewedCommit", "0" * 40),
        ),
        (
            "evidence-rec-i3",
            lambda record: record["authority"].__setitem__("recI3Allowed", True),
        ),
        (
            "evidence-stage-gate",
            lambda record: record["reviewAndGateTruth"].__setitem__("openStageGate", "REC-I3"),
        ),
    ]
    for name, mutation in evidence_mutations:
        expect_metadata_negative(name, evidence_mutation=mutation)

    packet_mutations: list[tuple[str, Callable[[dict[str, Any]], None]]] = [
        (
            "packet-review-sha",
            lambda record: record["reviewTarget"][
                "accountableClosureIndependentMetadataReview"
            ]["record"].__setitem__("sha256", "0" * 64),
        ),
        (
            "packet-evidence-sha",
            lambda record: record["reviewTarget"]["implementationEvidence"].__setitem__(
                "sha256", "0" * 64
            ),
        ),
        (
            "packet-review-completion",
            lambda record: record["authorityBoundary"].__setitem__(
                "accountableClosureIndependentMetadataReviewCompleted", False
            ),
        ),
        (
            "packet-ready",
            lambda record: record["authorityBoundary"].__setitem__(
                "readyOrMergeAuthorized", True
            ),
        ),
        (
            "packet-rec-i3",
            lambda record: record["authorityBoundary"].__setitem__("recI3Activated", True),
        ),
        (
            "packet-stage-gate",
            lambda record: record["findingsTruth"].__setitem__("openP0StageGate", "REC-I3"),
        ),
        (
            "packet-human-closure-sha",
            lambda record: record["reviewTarget"]["accountableHumanReviewClosure"].__setitem__(
                "sha256", "0" * 64
            ),
        ),
    ]
    for name, mutation in packet_mutations:
        expect_metadata_negative(name, packet_mutation=mutation)


def run_rec_i2b_merged_main_evidence_boundary_tests() -> None:
    evidence = read_json(REC_I2B_RUNTIME_EVIDENCE_PATH)
    packet = read_json(REC_I2B_ACCOUNTABLE_PACKET_PATH)
    validate_rec_i2b_merged_main_evidence_boundary()

    def expect_negative(
        name: str,
        *,
        evidence_mutation: Callable[[dict[str, Any]], None] | None = None,
        packet_mutation: Callable[[dict[str, Any]], None] | None = None,
    ) -> None:
        candidate_evidence = copy.deepcopy(evidence)
        candidate_packet = copy.deepcopy(packet)
        if evidence_mutation is not None:
            evidence_mutation(candidate_evidence)
        if packet_mutation is not None:
            packet_mutation(candidate_packet)
        try:
            validate_rec_i2b_merged_main_nonclaims(candidate_evidence, candidate_packet)
        except (ValueError, KeyError):
            print(f"PASS negative rec-i2b-squash-main-evidence-{name}")
            return
        raise ValueError(f"Negative REC-I2B squash-main evidence mutation unexpectedly passed: {name}")

    expect_negative(
        "rec-i3-authority",
        evidence_mutation=lambda record: record["authority"].__setitem__("recI3Allowed", True),
    )
    expect_negative(
        "device-authority",
        evidence_mutation=lambda record: record["authority"].__setitem__(
            "deviceOrEmulatorExecutionAllowed", True
        ),
    )
    expect_negative(
        "measurement-claim",
        evidence_mutation=lambda record: record["notPerformedOrClaimed"].__setitem__(
            "measurement", True
        ),
    )
    expect_negative(
        "pass-claim",
        evidence_mutation=lambda record: record["notPerformedOrClaimed"].__setitem__("pocPass", True),
    )
    expect_negative(
        "admission-authority",
        packet_mutation=lambda record: record["authorityBoundary"].__setitem__(
            "dependencyOrProductionAdmissionAuthorized", True
        ),
    )
    expect_negative(
        "rec-i3-routing",
        packet_mutation=lambda record: record["routingEligibility"].__setitem__(
            "recI3MayProceedNow", True
        ),
    )


def run_rec_i2b_evidence_boundary_tests() -> None:
    working_evidence = read_json(REC_I2B_RUNTIME_EVIDENCE_PATH)
    accountable_phase = working_evidence.get("status") == REC_I2B_ACCOUNTABLE_STATUS
    accountable_metadata_clean = (
        working_evidence.get("remediationState")
        == REC_I2B_ACCOUNTABLE_METADATA_CLEAN_REMEDIATION_STATE
    )
    if accountable_phase:
        evidence_bytes = git_blob_bytes(
            f"{REC_I2B_ACCOUNTABLE_RECORDING_BASE_HEAD}:{REC_I2B_RUNTIME_EVIDENCE_PATH}"
        )
        evidence = json.loads(evidence_bytes.decode("utf-8", errors="strict"))
        terminal_commit = "1aedccf477fb4d3ff20a48a1834b22b317c8c880"
    else:
        evidence = working_evidence
        evidence_bytes = (ROOT / REC_I2B_RUNTIME_EVIDENCE_PATH).read_bytes()
        terminal_commit = git_output(
            "log",
            "-1",
            "--format=%H",
            "--",
            *REC_I2B_SUCCESSOR_IMPLEMENTATION_PATHS,
        )
    terminal_tree = git_output("rev-parse", f"{terminal_commit}^{{tree}}")
    validate_rec_i2b_successor_metadata_sha256(
        REC_I2B_SUCCESSOR_EVIDENCE_PHASE_IMPLEMENTATION_HEAD,
        REC_I2B_SUCCESSOR_HISTORICAL_METADATA_CANONICAL_SHA256,
    )
    for name, commit, candidate_sha256 in (
        (
            "historical-profile-rejects-auth05-sha",
            REC_I2B_SUCCESSOR_EVIDENCE_PHASE_IMPLEMENTATION_HEAD,
            REC_I2B_AAPT2_METADATA_CANONICAL_SHA256,
        ),
        (
            "historical-profile-rejects-wrong-sha",
            REC_I2B_SUCCESSOR_EVIDENCE_PHASE_IMPLEMENTATION_HEAD,
            "0" * 64,
        ),
    ):
        try:
            validate_rec_i2b_successor_metadata_sha256(commit, candidate_sha256)
        except ValueError:
            print(f"PASS negative rec-i2b-metadata-{name}")
        else:
            raise ValueError(f"Negative REC-I2B metadata pin unexpectedly passed: {name}")
    if rec_i2b_successor_implementation_phase(terminal_commit) == "aapt2-linux":
        validate_rec_i2b_successor_metadata_sha256(
            terminal_commit,
            REC_I2B_AAPT2_METADATA_CANONICAL_SHA256,
        )
        for name, candidate_sha256 in (
            ("auth05-rejects-historical-sha", REC_I2B_SUCCESSOR_HISTORICAL_METADATA_CANONICAL_SHA256),
            ("auth05-rejects-wrong-sha", "0" * 64),
        ):
            try:
                validate_rec_i2b_successor_metadata_sha256(terminal_commit, candidate_sha256)
            except ValueError:
                print(f"PASS negative rec-i2b-metadata-{name}")
            else:
                raise ValueError(f"Negative REC-I2B metadata pin unexpectedly passed: {name}")
    phase = validate_rec_i2b_runtime_evidence(
        evidence,
        terminal_commit=terminal_commit,
        terminal_tree=terminal_tree,
    )

    def expect_evidence_negative(name: str, mutation: Callable[[dict[str, Any]], None]) -> None:
        candidate = copy.deepcopy(evidence)
        mutation(candidate)
        try:
            validate_rec_i2b_runtime_evidence(
                candidate,
                terminal_commit=terminal_commit,
                terminal_tree=terminal_tree,
            )
        except (ValueError, KeyError):
            print(f"PASS negative rec-i2b-evidence-{name}")
            return
        raise ValueError(f"Negative REC-I2B evidence mutation unexpectedly passed: {name}")

    evidence_mutations: list[tuple[str, Callable[[dict[str, Any]], None]]] = [
        ("status", lambda record: record.__setitem__("status", "PASS_READY_MERGED")),
        ("branch", lambda record: record["git"].__setitem__("branch", "attacker/spoof")),
        ("pr-number", lambda record: record["git"].__setitem__("pullRequestNumber", 999)),
        ("pr-not-draft", lambda record: record["git"].__setitem__("pullRequestDraft", False)),
        ("merged", lambda record: record["git"].__setitem__("merged", True)),
        (
            "terminal-commit",
            lambda record: record["git"].__setitem__("terminalImplementationCommit", "0" * 40),
        ),
        (
            "terminal-tree",
            lambda record: record["git"].__setitem__("terminalImplementationTree", "0" * 40),
        ),
        (
            "runtime-manifest-hash",
            lambda record: record["exactFileManifest"][0].__setitem__("rawGitSha256", "0" * 64),
        ),
        (
            "caller-controlled-configuration",
            lambda record: record["implementation"]["registryAndPrimitiveBoundary"].__setitem__(
                "callerSuppliedConfigurationAccepted", True
            ),
        ),
        (
            "cleartext-key-material-exposure",
            lambda record: record["implementation"]["registryAndPrimitiveBoundary"].__setitem__(
                "cleartextKeyMaterialExposedSerializedStoredOrLogged", True
            ),
        ),
        (
            "review-disposition",
            lambda record: record["reviewAndGateTruth"].__setitem__(
                "independentDeltaReviewDisposition", "REJECTED"
            ),
        ),
        (
            "open-stage-gate",
            lambda record: record["reviewAndGateTruth"].__setitem__("openStageGate", "REC-I3"),
        ),
        (
            "rec-i3-gate",
            lambda record: record["reviewAndGateTruth"].__setitem__("recI3MayProceedNow", True),
        ),
    ]
    if phase == "baseline-clean":
        evidence_mutations.extend(
            [
                (
                    "reviewed-implementation",
                    lambda record: record["reviewAndGateTruth"].__setitem__(
                        "currentIndependentCleanReviewedImplementationCommit", "0" * 40
                    ),
                ),
                (
                    "reviewed-evidence",
                    lambda record: record["reviewAndGateTruth"].__setitem__(
                        "currentIndependentCleanReviewedEvidenceCommit", "0" * 40
                    ),
                ),
                (
                    "review-closure",
                    lambda record: record["reviewAndGateTruth"].__setitem__(
                        "currentIndependentCleanClosureRecord", "docs/evidence/spoof.json"
                    ),
                ),
            ]
        )
    else:
        successor_review = "currentReviewedSuccessorIndependentDelta"
        evidence_mutations.extend(
            [
                (
                    "successor-reviewed-implementation",
                    lambda record: record["reviewAndGateTruth"][successor_review].__setitem__(
                        "reviewedImplementationCommit", "0" * 40
                    ),
                ),
                (
                    "successor-reviewed-tree",
                    lambda record: record["reviewAndGateTruth"][successor_review].__setitem__(
                        "reviewedImplementationTree", "0" * 40
                    ),
                ),
                (
                    "successor-reviewed-evidence",
                    lambda record: record["reviewAndGateTruth"][successor_review].__setitem__(
                        "reviewedEvidenceCommit", "0" * 40
                    ),
                ),
                (
                    "successor-review-closure",
                    lambda record: record["reviewAndGateTruth"][successor_review].__setitem__(
                        "closureRecord", {"path": "docs/evidence/spoof.json"}
                    ),
                ),
                (
                    "successor-remediation-authorization",
                    lambda record: record["advisoryRemediation"][
                        "reviewedSuccessorValidatorAndCiMetadataRemediation"
                    ]["authorizations"].__setitem__(0, "REC-I2B-SPOOF"),
                ),
                (
                    "successor-remediation-implementation",
                    lambda record: record["advisoryRemediation"][
                        "reviewedSuccessorValidatorAndCiMetadataRemediation"
                    ].__setitem__("implementationCommit", "0" * 40),
                ),
                (
                    "successor-remediation-artifact",
                    lambda record: record["advisoryRemediation"][
                        "reviewedSuccessorValidatorAndCiMetadataRemediation"
                    ]["metadataArtifacts"][0].__setitem__("sha256", "0" * 64),
                ),
                (
                    "successor-remediation-manifest",
                    lambda record: record["advisoryRemediation"][
                        "reviewedSuccessorValidatorAndCiMetadataRemediation"
                    ]["implementationFiles"][1].__setitem__("rawGitSha256", "0" * 64),
                ),
                (
                    "successor-remediation-full-verification",
                    lambda record: record["advisoryRemediation"][
                        "reviewedSuccessorValidatorAndCiMetadataRemediation"
                    ]["fullStaticBuildAndUnitBlock"].__setitem__("executedTasks", 103),
                ),
                (
                    "successor-remediation-zero-findings",
                    lambda record: record["advisoryRemediation"].__setitem__(
                        "currentZeroFindingsClaimed", phase != "successor-clean"
                    ),
                ),
            ]
        )
    for field in (
        "accountableEngineeringSecurityReviewCompleted",
        "deviceOrEmulatorExecutionAllowed",
        "measuredExecutionAllowed",
        "harnessOrCampaignAllowed",
        "recI3Allowed",
        "dependencyAdmissionAllowed",
        "productionAdmissionAllowed",
        "readyOrMergeAllowed",
        "mergeAllowed",
    ):
        evidence_mutations.append(
            (
                f"authority-{field}",
                lambda record, key=field: record["authority"].__setitem__(key, True),
            )
        )
    for name, mutation in evidence_mutations:
        expect_evidence_negative(name, mutation)

    packet = (
        json.loads(
            git_blob_bytes(
                f"{REC_I2B_ACCOUNTABLE_RECORDING_BASE_HEAD}:{REC_I2B_ACCOUNTABLE_PACKET_PATH}"
            ).decode("utf-8", errors="strict")
        )
        if accountable_phase
        else read_json(REC_I2B_ACCOUNTABLE_PACKET_PATH)
    )
    validate_rec_i2b_accountable_packet(
        packet,
        evidence=evidence,
        evidence_bytes=evidence_bytes,
        terminal_commit=terminal_commit,
        terminal_tree=terminal_tree,
        phase=phase,
    )

    def expect_packet_negative(name: str, mutation: Callable[[dict[str, Any]], None]) -> None:
        candidate = copy.deepcopy(packet)
        mutation(candidate)
        try:
            validate_rec_i2b_accountable_packet(
                candidate,
                evidence=evidence,
                evidence_bytes=evidence_bytes,
                terminal_commit=terminal_commit,
                terminal_tree=terminal_tree,
                phase=phase,
            )
        except (ValueError, KeyError):
            print(f"PASS negative rec-i2b-packet-{name}")
            return
        raise ValueError(f"Negative REC-I2B packet mutation unexpectedly passed: {name}")

    packet_mutations: list[tuple[str, Callable[[dict[str, Any]], None]]] = [
        ("status", lambda record: record.__setitem__("status", "FORMALLY_APPROVED_AND_READY")),
        ("branch", lambda record: record["reviewTarget"].__setitem__("branch", "attacker/spoof")),
        ("pr-number", lambda record: record["reviewTarget"].__setitem__("pullRequestNumber", 999)),
        ("pr-not-draft", lambda record: record["reviewTarget"].__setitem__("pullRequestDraft", False)),
        (
            "terminal-commit",
            lambda record: record["reviewTarget"].__setitem__("terminalImplementationCommit", "0" * 40),
        ),
        (
            "terminal-tree",
            lambda record: record["reviewTarget"].__setitem__("terminalImplementationTree", "0" * 40),
        ),
        (
            "evidence-bytes",
            lambda record: record["reviewTarget"]["implementationEvidence"].__setitem__("bytes", 0),
        ),
        (
            "evidence-sha256",
            lambda record: record["reviewTarget"]["implementationEvidence"].__setitem__(
                "sha256", "0" * 64
            ),
        ),
        (
            "runtime-manifest-hash",
            lambda record: record["exactFileManifest"][0].__setitem__("rawGitSha256", "0" * 64),
        ),
        (
            "frozen-crypto-invariant",
            lambda record: record["frozenDesignInvariants"][0].__setitem__(
                "invariant", "Caller-controlled crypto configuration is permitted."
            ),
        ),
        (
            "verification-crypto-policy",
            lambda record: record["verificationEvidence"]["cryptoPolicy"].__setitem__(
                "result", "FAIL"
            ),
        ),
        (
            "verification-device-execution",
            lambda record: record["verificationEvidence"]["freshSearchSmokeResolution"].__setitem__(
                "emulatorOrDeviceStarted", True
            ),
        ),
        (
            "decision-option-rec-i3-merge",
            lambda record: record["decisionOptions"][0].__setitem__(
                "effect", "Immediately activates REC-I3 and permits merge."
            ),
        ),
        (
            "runtime-review-implementation-commit",
            lambda record: record["advisoryRemediationGate"]["independentDeltaReview"].__setitem__(
                "reviewedImplementationCommit", "0" * 40
            ),
        ),
        (
            "runtime-review-implementation-tree",
            lambda record: record["advisoryRemediationGate"]["independentDeltaReview"].__setitem__(
                "reviewedImplementationTree", "0" * 40
            ),
        ),
        (
            "runtime-review-evidence-commit",
            lambda record: record["advisoryRemediationGate"]["independentDeltaReview"].__setitem__(
                "reviewedEvidenceCommit", "0" * 40
            ),
        ),
        (
            "runtime-review-evidence-tree",
            lambda record: record["advisoryRemediationGate"]["independentDeltaReview"].__setitem__(
                "reviewedEvidenceTree", "0" * 40
            ),
        ),
        (
            "runtime-review-record",
            lambda record: record["advisoryRemediationGate"]["independentDeltaReview"].__setitem__(
                "record", "docs/evidence/spoof.json"
            ),
        ),
        (
            "changes-required-review-implementation-commit",
            lambda record: record["advisoryRemediationGate"][
                "freshGraphVerificationMetadataAdvisoryVerdict"
            ].__setitem__("reviewedImplementationCommit", "0" * 40),
        ),
        (
            "changes-required-review-implementation-tree",
            lambda record: record["advisoryRemediationGate"][
                "freshGraphVerificationMetadataAdvisoryVerdict"
            ].__setitem__("reviewedImplementationTree", "0" * 40),
        ),
        (
            "changes-required-review-evidence-commit",
            lambda record: record["advisoryRemediationGate"][
                "freshGraphVerificationMetadataAdvisoryVerdict"
            ].__setitem__("reviewedEvidenceCommit", "0" * 40),
        ),
        (
            "changes-required-review-evidence-tree",
            lambda record: record["advisoryRemediationGate"][
                "freshGraphVerificationMetadataAdvisoryVerdict"
            ].__setitem__("reviewedEvidenceTree", "0" * 40),
        ),
        (
            "fresh-clean-review-implementation-commit",
            lambda record: record["advisoryRemediationGate"]["freshIndependentDeltaReview"].__setitem__(
                "reviewedImplementationCommit", "0" * 40
            ),
        ),
        (
            "fresh-clean-review-implementation-tree",
            lambda record: record["advisoryRemediationGate"]["freshIndependentDeltaReview"].__setitem__(
                "reviewedImplementationTree", "0" * 40
            ),
        ),
        (
            "fresh-clean-review-evidence-commit",
            lambda record: record["advisoryRemediationGate"]["freshIndependentDeltaReview"].__setitem__(
                "reviewedEvidenceCommit", "0" * 40
            ),
        ),
        (
            "fresh-clean-review-evidence-tree",
            lambda record: record["advisoryRemediationGate"]["freshIndependentDeltaReview"].__setitem__(
                "reviewedEvidenceTree", "0" * 40
            ),
        ),
        (
            "fresh-clean-review-record-path",
            lambda record: record["advisoryRemediationGate"]["freshIndependentDeltaReview"][
                "record"
            ].__setitem__("path", "docs/evidence/spoof.json"),
        ),
        (
            "fresh-clean-review-record-sha256",
            lambda record: record["advisoryRemediationGate"]["freshIndependentDeltaReview"][
                "record"
            ].__setitem__("sha256", "0" * 64),
        ),
        (
            "changes-required-findings-implementation",
            lambda record: record["findingsTruth"]["freshGraphVerificationMetadataAdvisory"].__setitem__(
                "reviewedImplementationCommit", "0" * 40
            ),
        ),
        (
            "changes-required-findings-evidence",
            lambda record: record["findingsTruth"]["freshGraphVerificationMetadataAdvisory"].__setitem__(
                "reviewedEvidenceCommit", "0" * 40
            ),
        ),
        (
            "open-stage-gate",
            lambda record: record["findingsTruth"].__setitem__("openP0StageGate", "REC-I3"),
        ),
        ("routing-now", lambda record: record["routingEligibility"].__setitem__("eligibleNow", True)),
        (
            "rec-i3-routing",
            lambda record: record["routingEligibility"].__setitem__("recI3MayProceedNow", True),
        ),
        (
            "checklist-question",
            lambda record: record["reviewChecklist"][0].__setitem__("question", "Spoofed question"),
        ),
        (
            "checklist-accountable-answer",
            lambda record: record["reviewChecklist"][0].__setitem__("accountableReviewerAnswer", "YES"),
        ),
        (
            "checklist-independent-answer",
            lambda record: record["reviewChecklist"][0].__setitem__(
                "independentDeltaReviewerAnswer",
                "YES" if phase == "successor-pending" else None,
            ),
        ),
        (
            "review-completion",
            lambda record: record["reviewCompletionRequirements"].__setitem__(
                "formalDecisionInSeparateFinalReviewRecordRequired", False
            ),
        ),
        (
            "recommended-default",
            lambda record: record["recommendedDefault"].__setitem__("now", "REC-I3"),
        ),
        ("terminal-gate", lambda record: record.__setitem__("terminalGate", "REC-I3 activated")),
    ]
    if phase == "baseline-clean":
        current_findings_key = "currentFreshCacheMetadataIndependentDelta"
        packet_mutations.extend(
            [
                (
                    "reviewed-implementation",
                    lambda record: record["findingsTruth"][current_findings_key].__setitem__(
                        "reviewedImplementationCommit", "0" * 40
                    ),
                ),
                (
                    "reviewed-evidence",
                    lambda record: record["findingsTruth"][current_findings_key].__setitem__(
                        "reviewedEvidenceCommit", "0" * 40
                    ),
                ),
                (
                    "review-disposition",
                    lambda record: record["findingsTruth"][current_findings_key].__setitem__(
                        "disposition", "REJECTED"
                    ),
                ),
                (
                    "current-findings-record",
                    lambda record: record["findingsTruth"][current_findings_key].__setitem__(
                        "record", "docs/evidence/spoof.json"
                    ),
                ),
            ]
        )
    else:
        current_findings_key = "currentReviewedSuccessorIndependentDelta"
        successor_gate_key = "reviewedSuccessorValidatorAndCiMetadataRemediation"
        packet_mutations.extend(
            [
                (
                    "successor-reviewed-implementation",
                    lambda record: record["findingsTruth"][current_findings_key].__setitem__(
                        "reviewedImplementationCommit", "0" * 40
                    ),
                ),
                (
                    "successor-reviewed-tree",
                    lambda record: record["findingsTruth"][current_findings_key].__setitem__(
                        "reviewedImplementationTree", "0" * 40
                    ),
                ),
                (
                    "successor-reviewed-evidence",
                    lambda record: record["findingsTruth"][current_findings_key].__setitem__(
                        "reviewedEvidenceCommit", "0" * 40
                    ),
                ),
                (
                    "successor-review-closure",
                    lambda record: record["findingsTruth"][current_findings_key].__setitem__(
                        "closureRecord", {"path": "docs/evidence/spoof.json"}
                    ),
                ),
                (
                    "successor-target-closure",
                    lambda record: record["reviewTarget"].__setitem__(
                        "reviewedSuccessorIndependentDeltaReviewClosure",
                        {"path": "docs/evidence/spoof.json"},
                    ),
                ),
                (
                    "successor-gate-implementation",
                    lambda record: record["advisoryRemediationGate"][successor_gate_key].__setitem__(
                        "implementationCommit", "0" * 40
                    ),
                ),
                (
                    "successor-gate-evidence",
                    lambda record: record["advisoryRemediationGate"][successor_gate_key][
                        "implementationEvidence"
                    ].__setitem__("sha256", "0" * 64),
                ),
                (
                    "successor-gate-current-review",
                    lambda record: record["advisoryRemediationGate"][successor_gate_key][
                        "freshIndependentDeltaReview"
                    ].__setitem__("disposition", "REJECTED"),
                ),
                (
                    "successor-completion-required",
                    lambda record: record["reviewCompletionRequirements"][
                        "reviewedSuccessorIndependentAdvisoryDeltaReview"
                    ].__setitem__("required", phase == "successor-clean"),
                ),
            ]
        )
    for field in (
        "thisPacketIsFormalApproval",
        "technicalDelegationMaySubstituteAccountableReview",
        "reviewerIdentityRecorded",
        "reviewerSignatureRecorded",
        "formalApprovalRecorded",
        "recI3Activated",
        "deviceOrEmulatorExecutionAuthorized",
        "harnessOrCampaignAuthorized",
        "measurementAuthorized",
        "dependencyOrProductionAdmissionAuthorized",
        "publicationOrMergeAuthorizedByIndependentReview",
        "readyOrMergeAuthorized",
    ):
        packet_mutations.append(
            (
                f"authority-{field}",
                lambda record, key=field: record["authorityBoundary"].__setitem__(key, True),
            )
        )
    for name, mutation in packet_mutations:
        expect_packet_negative(name, mutation)
    if accountable_metadata_clean:
        run_rec_i2b_accountable_phase_tests(historical=True)
        run_rec_i2b_accountable_metadata_phase_tests()
    elif accountable_phase:
        run_rec_i2b_accountable_phase_tests()


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
    rec_i2b_mode = validate_dependency_and_scope_boundary()
    validate_active_metadata()
    if "--self-test" in sys.argv[1:]:
        run_lifecycle_tests()
        run_rec_i2b_successor_tests()
        run_rec_i2b_merged_main_tests()
        run_git_path_collector_tests()
        run_github_pull_request_context_tests()
        merged_main_identity = collect_rec_i2b_merged_main_identity()
        if rec_i2b_merged_main_candidate(merged_main_identity):
            run_rec_i2b_merged_main_evidence_boundary_tests()
        else:
            run_rec_i2b_evidence_boundary_tests()
        run_negative_tests()
    if rec_i2b_mode:
        current_evidence = read_json(REC_I2B_RUNTIME_EVIDENCE_PATH)
        merged_main_mode = rec_i2b_merged_main_candidate(collect_rec_i2b_merged_main_identity())
        accountable_review_complete = current_evidence.get("status") == REC_I2B_ACCOUNTABLE_STATUS
        accountable_metadata_clean = (
            current_evidence.get("remediationState")
            == REC_I2B_ACCOUNTABLE_METADATA_CLEAN_REMEDIATION_STATE
        )
        profile_summary = (
            (
                "exact REC-I2B squash-merged main anchor/module/evidence boundary valid; distinct "
                "accountable Engineering/Security review and its independent metadata review are "
                "complete, while separate REC-I3 activation decision, execution and admission remain "
                "blocked"
            )
            if merged_main_mode
            else
            (
                "exact REC-I2B reviewed-successor chain/module/evidence boundary valid; distinct "
                "accountable Engineering/Security review and its independent metadata review are "
                "complete, while Draft PR integration, exact-head CI, separate REC-I3 activation "
                "decision, execution and admission remain blocked"
            )
            if accountable_metadata_clean
            else (
                "exact REC-I2B reviewed-successor chain/module/evidence boundary valid; distinct "
                "accountable Engineering/Security review complete, while fresh independent metadata "
                "review, separate REC-I3 activation decision, execution, admission and merge remain "
                "blocked"
            )
            if accountable_review_complete
            else (
                "exact REC-I2B reviewed-successor chain/module/evidence boundary valid; accountable "
                "Engineering/Security review, REC-I3, execution, admission and merge remain blocked"
            )
        )
    else:
        profile_summary = "exact REC-I1 authorization and pure module valid"
    print(
        "POC-RECOVERY-001 governance v0.6 validation passed; 15 v0.1-v0.5 audit artifacts immutable, "
        "46 unique effective rows with one KEY-04, KEY-04 authentication/AAD failure only -> "
        "KEY_UNAVAILABLE_KEY_MISMATCH, KCF-07 successful-decrypt malformed plaintext -> "
        "CORRUPT_KEY_CONFIRMATION, Phase A=184, full physical=138, hard kills=120/candidate separate, "
        f"formal accountable protocol review complete with REC-RDY-02 closed, {profile_summary}; "
        "10 current protocol-execution blockers remain, implementationAllowedByThisPackage=false, "
        "executionAllowed=false, measuredExecutionAllowed=false"
    )
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, StopIteration, json.JSONDecodeError, subprocess.CalledProcessError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        raise SystemExit(1)
