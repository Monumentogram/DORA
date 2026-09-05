"""Behavior and real-Git mutation tests for the bounded REC-I3 successor."""

from __future__ import annotations

import copy
import tempfile
import unittest
from dataclasses import replace
from pathlib import Path
from unittest.mock import patch

import validate_poc_recovery_governance as governance


class RecoveryI3GovernanceTests(unittest.TestCase):
    def test_authorized_worktree_reaches_the_dependency_inventory_entrypoint(self) -> None:
        try:
            accepted = governance.validate_current_rec_i2b_reviewed_successor()
        except ValueError as error:
            self.fail(f"Authorized partial REC-I3 must preserve the predecessor and dispatch: {error}")
        self.assertTrue(accepted)

    def test_exact_paths_in_each_layer_and_no_implicit_recovery_directory_permission(self) -> None:
        for layer in ("committed", "staged", "unstaged", "untracked"):
            changes = {name: [] for name in ("committed", "staged", "unstaged", "untracked")}
            changes[layer] = list(governance.REC_I3_ALLOWED_PATHS)
            governance.validate_rec_i3_changed_paths(changes)
            for forbidden in (
                "android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/controller/Writer.kt",
                "android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/crypto/RecoveryRunAead.kt",
                "android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/crypto/RecoveryRunAeadTest.kt",
                "android/poc/recovery/build.gradle.kts", "android/poc/recovery/gradle.lockfile",
                "android/poc/recovery/recovery-i2b-r8.pro", "android/gradle/verification-metadata.xml",
                "tools/verify_poc_recovery_dependency_inventory.py", "docs/evidence/poc-recovery-001/readiness.json",
                "docs/stage0/poc-recovery-protocol-stage0-v0.6.json", ".github/workflows/android-ci.yml",
                "android/poc/recovery/данные.bin", "android/poc/recovery/control\nname.bin",
                governance.REC_I3_SOURCE_PATHS[0] + ".extra", "../" + governance.REC_I3_SOURCE_PATHS[0],
            ):
                with self.subTest(layer=layer, forbidden=forbidden):
                    changes[layer] = [forbidden]
                    with self.assertRaisesRegex(ValueError, "escapes exact scope"):
                        governance.validate_rec_i3_changed_paths(changes)
        with self.assertRaisesRegex(ValueError, "incomplete"):
            governance.validate_rec_i3_changed_paths({"committed": []})

    def test_exact_local_and_verified_pr_contexts_reject_identity_spoofing(self) -> None:
        base = governance.PinnedCommitIdentity(governance.REC_I3_BASE, governance.REC_I3_BASE_TREE,
                                               (governance.REC_I3_BASE_PARENT,), True)
        scope = governance.PinnedCommitIdentity(governance.REC_I3_SCOPE_COMMIT, governance.REC_I3_SCOPE_TREE,
                                                (governance.REC_I3_SCOPE_PARENT,), True)
        local = governance.RecoveryLifecycleIdentity("c" * 40, governance.REC_I3_BRANCH,
                                                     None, None, None, None, (), None, None, False)
        context = governance.GitHubPullRequestContext(
            governance.GITHUB_REPOSITORY, governance.GITHUB_REPOSITORY, governance.REC_I3_BRANCH,
            "a" * 40, "main", governance.REC_I3_BASE, "refs/pull/99/merge", "c" * 40,
            99, True, "open", False,
        )
        pr = replace(local, github_pull_request_context=context)
        for valid in (local, pr):
            governance.validate_rec_i3_context(valid, base, scope)
        for branch in ("main", "", "codex/spoof", governance.REC_I2B_BRANCH):
            with self.subTest(branch=branch), self.assertRaises(ValueError):
                governance.validate_rec_i3_context(replace(local, branch=branch), base, scope)
        for identity_name, identity in (("base", base), ("scope", scope)):
            for field, value in (("commit", "0" * 40), ("tree", "0" * 40),
                                 ("parents", ("0" * 40,)), ("is_ancestor_of_head", False)):
                mutated = replace(identity, **{field: value})
                with self.subTest(pin=identity_name, field=field), self.assertRaises(ValueError):
                    governance.validate_rec_i3_context(local, mutated if identity_name == "base" else base,
                                                        mutated if identity_name == "scope" else scope)
        for field, value in (
            ("repository", "fork/DORA"), ("head_repository", "fork/DORA"),
            ("head_ref", "codex/spoof"), ("base_ref", "other"), ("base_sha", "d" * 40),
            ("head_sha", "invalid"), ("head_sha", governance.REC_I3_BASE), ("merge_sha", "b" * 40),
            ("merge_ref", "refs/heads/main"), ("number", 0), ("state", "closed"), ("merged", True),
        ):
            with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                governance.validate_rec_i3_context(replace(pr, github_pull_request_context=replace(context, **{field: value})),
                                                    base, scope)
        with self.assertRaisesRegex(ValueError, "lacks verified"):
            governance.select_lifecycle_branch("", governance.REC_I3_BRANCH)

    def evidence_fixture(self) -> tuple[dict, dict]:
        record = copy.deepcopy(governance.read_json(governance.REC_I3_EVIDENCE_PATH))
        hashes = dict.fromkeys(governance.REC_I3_SOURCE_PATHS, "a" * 64)
        record["implementationStatus"] = "LOCAL_VERIFIED"
        record["sourceFiles"] = hashes.copy()
        record["checks"] = [{
            "command": "gradlew.bat spotlessCheck detekt :poc:recovery:testDebugUnitTest "
                       ":poc:recovery:lintDebug :poc:recovery:recoveryI2bVerifyCryptoPolicy",
            "stage": "FINAL", "outcome": "PASS", "tests": 1, "failures": 0, "errors": 0, "skipped": 0,
        }]
        return record, hashes

    def test_evidence_requires_actual_scope_nonclaims_and_final_check_coverage(self) -> None:
        record, hashes = self.evidence_fixture()
        governance.validate_rec_i3_evidence(record, hashes, publication=True)
        mutations = [
            lambda r: r.__setitem__("fullRecI3Completed", True),
            lambda r: r.__setitem__("recoveryPreflightUnlocked", True),
            lambda r: r.__setitem__("readinessBlockersClosed", ["REC-RDY-07"]),
            lambda r: r.__setitem__("scopeFirstCommit", governance.REC_I3_BASE),
            lambda r: r.__setitem__("claimCeiling", "PASS_READY"),
            lambda r: r.__setitem__("schemaVersion", True),
            lambda r: r.__setitem__("implementationStatus", "IN_PROGRESS"),
            lambda r: r.__setitem__("sourceFiles", {}),
            lambda r: r["sourceFiles"].__setitem__(governance.REC_I3_SOURCE_PATHS[0], "b" * 64),
            lambda r: r["sourceFiles"].__setitem__("android/app/Injected.kt", "a" * 64),
            lambda r: r["review"].__setitem__("formalReviewer", True),
            lambda r: r["review"].__setitem__("accountable", "APPROVED"),
            lambda r: r["review"].__setitem__("independentAdvisory", "CLEAN"),
            lambda r: r["checks"][0].__setitem__("outcome", "FAIL"),
            lambda r: r["checks"][0].__setitem__("command", "gradlew.bat :app:testDebugUnitTest"),
            lambda r: r["checks"][0].__setitem__("stage", "BASELINE"),
            lambda r: r["checks"][0].__setitem__("failures", 1),
            lambda r: r["checks"][0].__setitem__("tests", 0),
            lambda r: r.__setitem__("newExecutionApproval", True),
        ]
        for group in ("authority", "execution"):
            for field, original in record[group].items():
                mutations.append(lambda r, group=group, field=field, original=original:
                                 r[group].__setitem__(field, not original))
                mutations.append(lambda r, group=group, field=field, original=original:
                                 r[group].__setitem__(field, int(original)))
        for index, mutate in enumerate(mutations):
            candidate = copy.deepcopy(record)
            mutate(candidate)
            with self.subTest(mutation=index), self.assertRaises(ValueError):
                governance.validate_rec_i3_evidence(candidate, hashes, publication=True)
        record["implementationStatus"] = "IN_PROGRESS"
        record["sourceFiles"] = {}
        governance.validate_rec_i3_evidence(record, {})

    def test_real_git_predecessor_changes_cannot_hide_in_reverts_renames_or_index(self) -> None:
        relative = "android/poc/recovery/reviewed.kt"
        for layer in ("committed", "staged", "unstaged", "untracked", "reverted", "renamed"):
            with self.subTest(layer=layer), tempfile.TemporaryDirectory(prefix="dora-rec-i3-") as temporary:
                repo, _ = governance.initialize_test_git_repo(Path(temporary))
                path = repo / relative
                path.parent.mkdir(parents=True)
                path.write_text("reviewed", encoding="utf-8")
                base = governance.commit_test_git_repo(repo, "reviewed base")
                if layer == "renamed":
                    governance.test_git(repo, "mv", relative, "renamed.kt")
                    governance.commit_test_git_repo(repo, "rename predecessor")
                elif layer == "untracked":
                    (path.parent / "unexpected.kt").write_text("extra", encoding="utf-8")
                else:
                    path.write_text("mutation", encoding="utf-8")
                    if layer == "staged":
                        governance.test_git(repo, "add", relative)
                    elif layer in ("committed", "reverted"):
                        governance.commit_test_git_repo(repo, "mutate predecessor")
                        if layer == "reverted":
                            path.write_text("reviewed", encoding="utf-8")
                            governance.commit_test_git_repo(repo, "restore predecessor")
                changes = governance.collect_post_merge_changes(root=repo, merged_anchor=base)
                with self.assertRaisesRegex(ValueError, "escapes exact scope"):
                    governance.validate_rec_i3_changed_paths(changes)

    def test_regular_source_file_rejects_git_symlink_and_missing_file(self) -> None:
        with tempfile.TemporaryDirectory(prefix="dora-rec-i3-mode-") as temporary:
            repo, _ = governance.initialize_test_git_repo(Path(temporary))
            relative = governance.REC_I3_SOURCE_PATHS[0]
            source = repo / relative
            source.parent.mkdir(parents=True)
            source.write_text("source", encoding="utf-8")
            with patch.object(governance, "ROOT", repo):
                governance.validate_rec_i3_regular_file(relative)
                blob = governance.test_git_text(repo, "hash-object", "-w", "--stdin", input_data=b"other.kt")
                governance.test_git(repo, "update-index", "--add", "--cacheinfo", f"120000,{blob},{relative}")
                with self.assertRaisesRegex(ValueError, "non-regular Git entry"):
                    governance.validate_rec_i3_regular_file(relative)
                source.unlink()
                with self.assertRaisesRegex(ValueError, "missing"):
                    governance.validate_rec_i3_regular_file(relative)

    def test_new_files_must_be_absent_at_base(self) -> None:
        with tempfile.TemporaryDirectory(prefix="dora-rec-i3-base-") as temporary:
            repo, base = governance.initialize_test_git_repo(Path(temporary))
            with patch.object(governance, "ROOT", repo), patch.object(governance, "REC_I3_BASE", base):
                governance.validate_rec_i3_additions_absent()
                source = repo / governance.REC_I3_SOURCE_PATHS[0]
                source.parent.mkdir(parents=True)
                source.write_text("preexisting", encoding="utf-8")
                occupied = governance.commit_test_git_repo(repo, "preexisting proposed new source")
                with patch.object(governance, "REC_I3_BASE", occupied), self.assertRaisesRegex(ValueError, "already exists"):
                    governance.validate_rec_i3_additions_absent()

    def test_scope_mutation_after_scope_first_commit_is_rejected_in_all_layers(self) -> None:
        for layer in ("committed", "staged", "unstaged", "untracked"):
            changes = {name: [] for name in ("committed", "staged", "unstaged", "untracked")}
            changes[layer] = [governance.REC_I3_SCOPE_PATH]
            with self.subTest(layer=layer), self.assertRaisesRegex(ValueError, "scope changed after"):
                governance.validate_rec_i3_scope_frozen(changes)

    def test_merge_only_predecessor_mutation_and_restore_is_rejected_for_local_and_pr_heads(self) -> None:
        with tempfile.TemporaryDirectory(prefix="dora-rec-i3-merge-") as temporary:
            repo, _ = governance.initialize_test_git_repo(Path(temporary))
            relative = "android/poc/recovery/frozen.kt"
            path = repo / relative
            path.parent.mkdir(parents=True)
            path.write_bytes(b"frozen\n")
            base = governance.commit_test_git_repo(repo, "reviewed base")
            original = governance.test_git_text(repo, "rev-parse", "HEAD^{tree}")

            def commit_tree(tree: str, parents: tuple[str, ...], message: str) -> str:
                arguments = ["-c", "user.name=Dora Validator Test", "-c",
                             "user.email=dora-validator@example.invalid", "commit-tree", tree]
                for parent in parents:
                    arguments.extend(("-p", parent))
                return governance.test_git_text(repo, *arguments, input_data=message.encode("ascii"))

            side1 = commit_tree(original, (base,), "first empty side")
            side2 = commit_tree(original, (base,), "second empty side")
            path.write_bytes(b"unauthorized merge resolution\n")
            governance.test_git(repo, "add", relative)
            modified = governance.test_git_text(repo, "write-tree")
            merge1 = commit_tree(modified, (base, side1), "mutate only in merge")
            merge2 = commit_tree(original, (merge1, side2), "restore only in merge")
            governance.test_git(repo, "update-ref", "refs/heads/main", merge2)
            path.write_bytes(b"frozen\n")
            governance.test_git(repo, "add", relative)
            changes = governance.collect_post_merge_changes(root=repo, merged_anchor=base)
            self.assertEqual({layer: [] for layer in ("committed", "staged", "unstaged", "untracked")}, changes)
            self.assertEqual(original, governance.test_git_text(repo, "rev-parse", "HEAD^{tree}"))
            self.assertEqual(relative, governance.test_git_text(repo, "diff", "--name-only", base, merge1))
            local = governance.RecoveryLifecycleIdentity(merge2, governance.REC_I3_BRANCH,
                                                         None, None, None, None, (), None, None, False)
            context = governance.GitHubPullRequestContext(
                governance.GITHUB_REPOSITORY, governance.GITHUB_REPOSITORY, governance.REC_I3_BRANCH,
                merge2, "main", base, "refs/pull/99/merge", "c" * 40, 99, True, "open", False,
            )
            with patch.object(governance, "ROOT", repo), patch.object(governance, "REC_I3_BASE", base):
                with self.assertRaisesRegex(ValueError, "candidate history must be linear"):
                    governance.validate_rec_i3_candidate_history(local)
                synthetic = commit_tree(original, (base, merge2), "synthetic GitHub merge")
                governance.test_git(repo, "update-ref", "refs/heads/main", synthetic)
                pr = replace(local, head=synthetic,
                             github_pull_request_context=replace(context, merge_sha=synthetic))
                with self.assertRaisesRegex(ValueError, "candidate history must be linear"):
                    governance.validate_rec_i3_candidate_history(pr)

                # A real two-parent CI checkout of a linear source head is valid.
                clean_synthetic = commit_tree(original, (base, side1), "clean synthetic GitHub merge")
                governance.test_git(repo, "update-ref", "refs/heads/main", clean_synthetic)
                clean_pr = replace(local, head=clean_synthetic,
                                   github_pull_request_context=replace(context, head_sha=side1, merge_sha=clean_synthetic))
                governance.validate_rec_i3_candidate_history(clean_pr)
                governance.test_git(repo, "update-ref", "refs/heads/main", side1)
                governance.validate_rec_i3_candidate_history(replace(local, head=side1))


if __name__ == "__main__":
    unittest.main()
