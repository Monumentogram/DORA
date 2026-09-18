#!/usr/bin/env python3
"""Exact two-commit source-profile validation for the 0D.6 CI successor."""

from __future__ import annotations

import subprocess
import os
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
BRANCH = "codex/0d6-device-preflight-campaign"
REPOSITORY = "Monumentogram/DORA"
PR_BASE = "55940df0c95e919a00708ae57e1b8aa23d89b6de"
IMPLEMENTATION_COMMIT = "61016553581581cf654e77d80384ac1bd7c6c66d"
IMPLEMENTATION_TREE = "5c38f9fed86465fee823861d12cb677f8e84cc19"
ALPHA_PREFIX_REPAIR_BINDING = {
    "appApkSha256": "9dd8f1dfd05ad4404e9c52ad5e7b11b575e6ba03bcba87a176bd953ddc649a7c",
    "testApkSha256": "b7bd8022a462586cba3bb70aef1201e4fdfeaff2dcc9087bce2c9e3ed248cf6a",
    "applicabilitySha256": "1978ba5c03cc4a6ef38dd9d669798fa1b9874d06245478ada715c6d3ed10d9dd"
}
ALPHA_MICROFILE_TRU03_BINDING = {
    "proofSha256": "1978ba5c03cc4a6ef38dd9d669798fa1b9874d06245478ada715c6d3ed10d9dd"
}
ALPHA_STREAM_PATH_REPAIR_BINDING = {
    "proofSha256": "d68e2a70a058787a99a56fb5d178adc6d36b48548e2f539efb73b1a3a16eda27"
}
ALPHA_COLLECTOR_QUERY_BINDING = {
    "proofSha256": "5691aa633ee88b375b072b154e4a83c820f4ef59d2c9bc3576f5475a00c468e1",
    "ownedProcessModuleSha256": "95c5fd53561a5cb0a087aa0109ded39f6845c44c59cae3cdc4ae572e8f1ab45d"
}
ALPHA_GIT_QUERY_BINDING = {
    "proofSha256": "1e4e334cdad477495366a1f6b295f1da23455d5793246df22357829fc825acc6",
    "lifecycleLibrarySha256": "2a3fc3f1d84c24194fee78b6e8975f056f35ac71ce06110bd65f03b1fb5ab2c8"
}
ALPHA_MICROFILE_DISPOSITION_BINDING = {
    "proofSha256": "b71860d73c63bd0215590e9a916dbad3c131a9045da1de1bcb56f8a01e9ba315"
}
ALPHA_CAPTURE_REPAIR_BINDING = {
    "launcherSha256": "e05118d721969e3fa61c92de63295827e31be13332099cbc558e25713d2d8e20",
    "ownedProcessModuleSha256": "448156a49c923180d5d21556f1e55820e60e6ae4a0a0da89abc694a3d5847609",
    "proofSha256": "71aa2e83ba149e0303b4d40849f8054ffed566ce34a50a681906c8bfaf7354b5"
}
ALPHA_REDUCED_REPAIR_BINDING = {
    "appApkSha256": "28e2af3b5e1456081423567dbd43cc296f23200e8787beed98e892291ad146ae",
    "testApkSha256": "d97cf13791505de5b08c8046853937d0f094adf417213b915f9347ed2cfb347d",
    "applicabilitySha256": "7b5b906ce0d3234c2561f5e00e6c33fc294b1b3f67b212efb462b6bccb97f9f6"
}
# Pin the immutable implementation parent after source freeze; never pin this file's own commit.
MAINTENANCE_PATHS = frozenset({
    "tools/validate_recovery_0d6_candidate.py",
    "tools/test_validate_recovery_0d6_candidate.py",
})


@dataclass(frozen=True)
class Profile:
    implementation_commit: str
    implementation_tree: str
    maintenance_paths: frozenset[str]
    base_commit: str = PR_BASE


def git(*args: str, root: Path = ROOT) -> str:
    return subprocess.run(
        ["git", "-c", "safe.directory=" + root.resolve().as_posix(), *args],
        cwd=root, check=True, text=True, capture_output=True
    ).stdout.strip()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def active_profile() -> Profile | None:
    # A metadata child cannot pin its own commit/tree in its contents.
    # Recognition is therefore by the pinned immutable parent plus exact delta.
    return Profile(
        IMPLEMENTATION_COMMIT, IMPLEMENTATION_TREE, MAINTENANCE_PATHS,
    )


def validate(profile: Profile, *, root: Path = ROOT, head: str = "HEAD", pull_request=None) -> None:
    require(git("rev-parse", f"{profile.implementation_commit}^{{tree}}", root=root) == profile.implementation_tree,
            "0D.6 implementation tree drift")
    candidate = git("rev-parse", head, root=root)
    checkout = git("rev-parse", "HEAD", root=root)
    branch = git("branch", "--show-current", root=root)
    if pull_request is None:
        require(branch in {BRANCH, ""}, "0D.6 local branch drift")
        require(checkout == candidate, "0D.6 local checkout differs from candidate")
    else:
        pr = pull_request
        require(pr.repository == pr.head_repository == REPOSITORY and pr.head_ref == BRANCH
                and pr.base_ref == "main" and pr.base_sha == profile.base_commit
                and pr.head_sha == candidate and pr.merge_sha == checkout
                and pr.state == "open" and pr.merged is False
                and type(pr.draft) is bool and branch in {BRANCH, ""},
                "0D.6 PR context drift")
        require(git("show", "-s", "--format=%P", checkout, root=root).split()
                == [pr.base_sha, candidate], "0D.6 PR merge parents drift")
        require(git("rev-parse", f"{checkout}^{{tree}}", root=root)
                == git("rev-parse", f"{candidate}^{{tree}}", root=root),
                "0D.6 PR merge tree differs from candidate")
    require(git("show", "-s", "--format=%P", candidate, root=root) == profile.implementation_commit,
            "0D.6 metadata child is not direct")
    require(not git("rev-list", "--min-parents=2", f"{profile.implementation_commit}..{candidate}", root=root),
            "0D.6 metadata child history contains a merge")
    changed = set(git("diff-tree", "--no-commit-id", "--name-only", "--no-renames", "-z", "-r",
                      candidate, root=root).strip("\0").split("\0"))
    require(changed == profile.maintenance_paths, "0D.6 metadata child changes undeclared paths")
    for path in profile.maintenance_paths:
        require(git("ls-tree", candidate, "--", path, root=root).startswith("100644 blob "),
                f"0D.6 metadata child non-regular mode: {path}")
    require(not git("status", "--porcelain", root=root), "0D.6 candidate checkout is dirty")


def validate_current() -> bool:
    profile = active_profile()
    if profile is None:
        return False
    # Reuse the established parser; it verifies the event file, repository, refs and checkout.
    from validate_poc_recovery_governance import collect_recovery_lifecycle_identity
    lifecycle = collect_recovery_lifecycle_identity()
    pr = lifecycle.github_pull_request_context
    source = pr.head_sha if pr is not None else lifecycle.head
    parent = git("show", "-s", "--format=%P", source)
    is_named_scope = lifecycle.branch == BRANCH
    if parent != profile.implementation_commit:
        require(not is_named_scope, "0D.6 named branch has no exact metadata child")
        return False
    if pr is None:
        require(not any(os.environ.get(name) for name in (
            "GITHUB_EVENT_NAME", "GITHUB_REPOSITORY", "GITHUB_REF", "GITHUB_SHA", "GITHUB_HEAD_REF"
        )), "0D.6 unsupported GitHub event context")
    validate(profile, head=source, pull_request=pr)
    return True


def alpha_preflight_source(*, root: Path = ROOT, profile: Profile | None = None) -> dict[str, str]:
    """Actual clean exact metadata child and APKs; packet agreement alone is insufficient."""
    accepted = active_profile() if profile is None else profile
    require(accepted is not None, "No exact alpha source profile")
    validate(accepted, root=root)
    return {
        "commit": git("rev-parse", "HEAD", root=root),
        "tree": git("rev-parse", "HEAD^{tree}", root=root),
        "appApkSha256": "8b1f79aec975c02021c7f58f5218da91f9e9585dbe8bbbd0844647c5b0c1d2de",
        "testApkSha256": "effd7e29c7dc7a4d8adc7a7057b1d90f5a58340b11c8755aff26cf71c39e0dfe",
    }
