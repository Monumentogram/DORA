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
IMPLEMENTATION_COMMIT = "f9a02c960630c400b869af400b7a54c0b6328266"
IMPLEMENTATION_TREE = "91301784cfdd135c823a75f97fb00a132f8a77ca"
ALPHA_PREFIX_REPAIR_BINDING = {
    "appApkSha256": "a2d2bd3728f596a835a2095e6f0cdbbed8ea6c42d2444b9a9c3f3c9c842365f0",
    "testApkSha256": "1db6307c2e412460e5ae8876122422d223f085c2df7a13101aaa22308012ba33",
    "applicabilitySha256": "d9f636ca7b104d24fa7f28b7f3d34142425a0fa2647f5bbe6e6e7a69cfe41498"
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
