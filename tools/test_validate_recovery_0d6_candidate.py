"""Real-Git regressions for the exact 0D.6 implementation/metadata boundary."""
from __future__ import annotations

import subprocess
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace

import validate_recovery_0d6_candidate as subject


class CandidateProfileTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.email", "fixture@example.invalid")
        self.git("config", "user.name", "Fixture")
        self.write("runtime.txt", "immutable implementation\n")
        self.commit("implementation")
        self.source = self.git("rev-parse", "HEAD")
        self.tree = self.git("rev-parse", "HEAD^{tree}")
        self.git("switch", "-q", "-c", subject.BRANCH)
        self.paths = frozenset({"metadata.txt", "validator.txt"})
        self.write("metadata.txt", "source profile only\n")
        self.write("validator.txt", "validator\n")
        self.commit("metadata")
        self.candidate = self.git("rev-parse", "HEAD")
        self.profile = subject.Profile(self.source, self.tree, self.paths, self.source)

    def git(self, *args):
        return subprocess.run(["git", "-c", "safe.directory=" + self.root.as_posix(), *args],
                              cwd=self.root, check=True, capture_output=True, text=True).stdout.strip()

    def write(self, path, content):
        target = self.root / path
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")

    def commit(self, message):
        self.git("add", "--all")
        self.git("commit", "-qm", message)

    def check(self, **kwargs):
        subject.validate(self.profile, root=self.root, **kwargs)

    def test_accepts_exact_local_and_detached_candidate(self):
        self.check()
        self.git("switch", "-q", "--detach", self.candidate)
        self.check()

    def test_rejects_wrong_source_tree(self):
        self.profile = subject.Profile(self.source, "0" * 40, self.paths, self.source)
        with self.assertRaisesRegex(ValueError, "tree drift"):
            self.check()

    def test_alpha_source_comes_from_actual_clean_exact_candidate(self):
        actual = subject.alpha_preflight_source(root=self.root, profile=self.profile)
        self.assertEqual(self.candidate, actual["commit"])
        self.assertEqual(self.git("rev-parse", "HEAD^{tree}"), actual["tree"])
        self.write("runtime.txt", "unreviewed runtime\n")
        with self.assertRaisesRegex(ValueError, "dirty"):
            subject.alpha_preflight_source(root=self.root, profile=self.profile)

    def test_alpha_source_rejects_later_candidate_even_with_unchanged_tree(self):
        self.git("commit", "--allow-empty", "-qm", "unreviewed successor")
        with self.assertRaisesRegex(ValueError, "not direct"):
            subject.alpha_preflight_source(root=self.root, profile=self.profile)

    def test_rejects_campaign_diagnostic_change_in_metadata_commit(self):
        path = "android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignInstrumentedTest.kt"
        self.write(path, "unreviewed campaign diagnostic\n")
        self.git("add", path)
        self.git("commit", "--amend", "--no-edit", "-q")
        with self.assertRaisesRegex(ValueError, "undeclared paths"):
            self.check()

    def test_rejects_owner_decision_change_in_metadata_child(self):
        self.write("docs/stage0/DORA_0D6_ALPHA_PREFLIGHT_OWNER_DECISION_20260914.md",
                   "unreviewed replacement decision\n")
        self.git("add", "docs")
        self.git("commit", "--amend", "--no-edit", "-q")
        with self.assertRaisesRegex(ValueError, "undeclared paths"):
            subject.alpha_preflight_source(root=self.root, profile=self.profile)

    def test_rejects_missing_maintenance_file(self):
        self.git("rm", "-q", "metadata.txt")
        self.git("commit", "--amend", "--no-edit", "-q")
        with self.assertRaisesRegex(ValueError, "undeclared paths"):
            self.check()

    def test_rejects_campaign_decision_change_in_metadata_child(self):
        self.write("docs/stage0/DORA_0D6_ALPHA_E36_CAMPAIGN_OWNER_DECISION_20260914.md",
                   "unreviewed expanded campaign authority\n")
        self.git("add", "docs")
        self.git("commit", "--amend", "--no-edit", "-q")
        with self.assertRaisesRegex(ValueError, "undeclared paths"):
            subject.alpha_preflight_source(root=self.root, profile=self.profile)

    def test_rejects_campaign_controller_change_in_metadata_child(self):
        self.write("tools/recovery_campaign.py", "unreviewed campaign behavior\n")
        self.git("add", "tools")
        self.git("commit", "--amend", "--no-edit", "-q")
        with self.assertRaisesRegex(ValueError, "undeclared paths"):
            subject.alpha_preflight_source(root=self.root, profile=self.profile)

    def test_rejects_reduced_scope_decision_change_in_metadata_child(self):
        self.write("docs/stage0/DORA_0D6_ALPHA_REDUCED_SCOPE_OWNER_DECISION_20260915.md",
                   "unreviewed replacement for the reduced selection authority\n")
        self.git("add", "docs")
        self.git("commit", "--amend", "--no-edit", "-q")
        with self.assertRaisesRegex(ValueError, "undeclared paths"):
            subject.alpha_preflight_source(root=self.root, profile=self.profile)

    def test_rejects_grandchild_even_with_unchanged_tree(self):
        self.git("commit", "--allow-empty", "-qm", "not direct")
        with self.assertRaisesRegex(ValueError, "not direct"):
            self.check()

    def test_rejects_instrumentation_parser_change_in_metadata_child(self):
        self.write("tools/recovery_instrumentation_status.py", "unreviewed cleanup parser\n")
        self.git("add", "tools")
        self.git("commit", "--amend", "--no-edit", "-q")
        with self.assertRaisesRegex(ValueError, "undeclared paths"):
            subject.alpha_preflight_source(root=self.root, profile=self.profile)

    def test_rejects_repair_applicability_behavior_change_in_metadata_child(self):
        self.write("tools/recovery_alpha_repair.py", "unreviewed repaired-APK admission\n")
        self.git("add", "tools")
        self.git("commit", "--amend", "--no-edit", "-q")
        with self.assertRaisesRegex(ValueError, "undeclared paths"):
            self.check()

    def test_rejects_capture_admission_behavior_change_in_metadata_child(self):
        self.write("tools/recovery_alpha_prefix_repair.py", "unreviewed capture admission\n")
        self.git("add", "tools")
        self.git("commit", "--amend", "--no-edit", "-q")
        with self.assertRaisesRegex(ValueError, "undeclared paths"):
            self.check()

    def test_rejects_stream_path_crypto_change_in_metadata_child(self):
        self.write("android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryStreamingTinkPrerequisiteCrypto.kt",
                   "unreviewed checkpoint authentication change\n")
        self.git("add", "android")
        self.git("commit", "--amend", "--no-edit", "-q")
        with self.assertRaisesRegex(ValueError, "undeclared paths"):
            self.check()

    def test_rejects_collector_admission_test_replacement_in_metadata_child(self):
        self.write("tools/test_recovery_alpha_prefix_repair.py", "unreviewed collector acceptance tests\n")
        self.git("add", "tools")
        self.git("commit", "--amend", "--no-edit", "-q")
        with self.assertRaisesRegex(ValueError, "undeclared paths"):
            self.check()

    def test_rejects_git_query_native_admission_change_in_metadata_child(self):
        self.write("tools/recovery_alpha_prefix_repair.py", "unreviewed Git query lifecycle admission\n")
        self.git("add", "tools")
        self.git("commit", "--amend", "--no-edit", "-q")
        with self.assertRaisesRegex(ValueError, "undeclared paths"):
            self.check()

    def test_rejects_schema6_implementation_change_in_metadata_child(self):
        self.write("android/poc/recovery/schema6-unreviewed.kt", "unreviewed schema6 mutation\n")
        self.git("add", "android")
        self.git("commit", "--amend", "--no-edit", "-q")
        with self.assertRaisesRegex(ValueError, "undeclared paths"):
            self.check()

    def test_rejects_tru03_harness_change_in_metadata_child(self):
        self.write("android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignInstrumentedTest.kt",
                   "unreviewed TRU03 harness change\n")
        self.git("add", "android")
        self.git("commit", "--amend", "--no-edit", "-q")
        with self.assertRaisesRegex(ValueError, "undeclared paths"):
            self.check()

    def test_rejects_executable_metadata_mode(self):
        self.git("update-index", "--chmod=+x", "validator.txt")
        self.git("commit", "--amend", "--no-edit", "-q")
        with self.assertRaisesRegex(ValueError, "non-regular mode"):
            self.check()

    def test_rejects_each_dirty_layer(self):
        for layer in ("untracked", "unstaged", "staged"):
            with self.subTest(layer=layer):
                if layer == "untracked":
                    self.write("unexpected.txt", "untracked")
                else:
                    self.write("metadata.txt", "dirty")
                    if layer == "staged":
                        self.git("add", "metadata.txt")
                with self.assertRaisesRegex(ValueError, "dirty"):
                    self.check()
                # Only this isolated temporary fixture is restored.
                if layer == "untracked":
                    (self.root / "unexpected.txt").unlink()
                else:
                    self.git("restore", "--staged", "--worktree", "metadata.txt")

    def test_rejects_unrelated_local_branch(self):
        self.git("switch", "-q", "-c", "unrelated")
        with self.assertRaisesRegex(ValueError, "branch"):
            self.check()

    def pull_request(self):
        merge = self.git("commit-tree", self.git("rev-parse", "HEAD^{tree}"),
                         "-p", self.source, "-p", self.candidate, "-m", "PR merge")
        self.git("switch", "-q", "--detach", merge)
        return SimpleNamespace(repository=subject.REPOSITORY, head_repository=subject.REPOSITORY,
                               head_ref=subject.BRANCH, base_ref="main", base_sha=self.source,
                               head_sha=self.candidate, merge_sha=merge, state="open", merged=False,
                               draft=True)

    def test_accepts_exact_draft_pr_merge_without_claiming_approval(self):
        pr = self.pull_request()
        self.check(head=pr.head_sha, pull_request=pr)

    def test_rejects_pr_context_drift(self):
        pr = self.pull_request()
        for field, value in (("head_repository", "third/party"), ("head_ref", "other"),
                             ("base_ref", "other"), ("base_sha", self.candidate),
                             ("merge_sha", self.candidate), ("merged", True)):
            previous = getattr(pr, field)
            setattr(pr, field, value)
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.check(head=pr.head_sha, pull_request=pr)
            setattr(pr, field, previous)

    def test_rejects_pr_merge_with_changed_runtime_tree(self):
        pr = self.pull_request()
        self.write("runtime.txt", "merge-only behavior\n")
        self.commit("altered checkout")
        with self.assertRaises(ValueError):
            self.check(head=pr.head_sha, pull_request=pr)


if __name__ == "__main__":
    unittest.main()
