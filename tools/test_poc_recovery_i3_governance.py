"""Behavior and real-Git mutation tests for the bounded REC-I3 successor."""

from __future__ import annotations

import copy
import subprocess
import sys
import tempfile
import unittest
from dataclasses import replace
from pathlib import Path
from unittest.mock import patch

import validate_poc_recovery_governance as governance


class RecoveryI3GovernanceTests(unittest.TestCase):
    def test_streaming_sqlite_verifier_executes_exact_schema_and_migrations(self) -> None:
        verifier = governance.ROOT / "tools/verify_rec_i3_streaming_sqlite.py"
        self.assertTrue(verifier.is_file())
        completed = subprocess.run(
            [sys.executable, str(verifier)],
            cwd=governance.ROOT,
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)
        self.assertIn("PASS REC-I3 streaming SQLite schema v4", completed.stdout)

    def test_exact_streaming_persistence_profile_accepts_current_checkout(self) -> None:
        lifecycle = replace(
            governance.collect_recovery_lifecycle_identity(),
            branch=governance.REC_I3_STREAMING_PERSISTENCE_BRANCH,
        )
        with patch.object(governance, "validate_rec_i3_streaming_persistence") as validate:
            self.assertTrue(governance.validate_current_rec_i3_successor(lifecycle))
            validate.assert_called_once_with(lifecycle)

    def test_streaming_persistence_base_preserves_exact_streaming_integration(self) -> None:
        self.assertTrue(
            governance.git_is_ancestor(
                governance.REC_I3_STREAMING_INTEGRATION_HEAD,
                governance.REC_I3_STREAMING_PERSISTENCE_BASE,
            )
        )
        identity = governance.collect_pinned_commit_identity(
            governance.REC_I3_STREAMING_PERSISTENCE_BASE,
            governance.collect_recovery_lifecycle_identity().head,
        )
        governance.validate_pinned_commit_identity(
            identity,
            expected_commit=governance.REC_I3_STREAMING_PERSISTENCE_BASE,
            expected_tree="718eae8d8d619d17c25ac9d025e0e24db3d52f9e",
            expected_parents=(governance.REC_I3_STREAMING_INTEGRATION_HEAD,),
            label="REC-I3 streaming persistence combined base",
        )

    def test_streaming_integration_profile_rejects_branch_and_pr_identity_spoofing(self) -> None:
        base = governance.PinnedCommitIdentity(
            governance.REC_I3_STREAMING_INTEGRATION_BASE,
            governance.REC_I3_STREAMING_INTEGRATION_BASE_TREE,
            (governance.REC_I3_STREAMING_INTEGRATION_BASE_PARENT,),
            True,
        )
        local = governance.RecoveryLifecycleIdentity(
            "c" * 40,
            governance.REC_I3_STREAMING_INTEGRATION_BRANCH,
            None, None, None, None, (), None, None, False,
        )
        governance.validate_rec_i3_streaming_integration_context(local, base)
        for branch in (governance.REC_I3_BRANCH, "main", "", "codex/rec-i3-streaming-option-a"):
            with self.subTest(branch=branch), self.assertRaisesRegex(ValueError, "exact authorized branch"):
                governance.validate_rec_i3_streaming_integration_context(replace(local, branch=branch), base)
        context = governance.GitHubPullRequestContext(
            governance.GITHUB_REPOSITORY,
            governance.GITHUB_REPOSITORY,
            governance.REC_I3_STREAMING_INTEGRATION_BRANCH,
            governance.REC_I3_STREAMING_INTEGRATION_HEAD,
            governance.GITHUB_BASE_BRANCH,
            governance.REC_I3_STREAMING_INTEGRATION_BASE,
            "refs/pull/99/merge",
            "c" * 40,
            99, True, "open", False,
        )
        pr = replace(local, github_pull_request_context=context)
        governance.validate_rec_i3_streaming_integration_context(pr, base)
        for field, value in (
            ("repository", "fork/DORA"),
            ("head_ref", governance.REC_I3_BRANCH),
            ("base_sha", "d" * 40),
            ("head_sha", governance.REC_I3_STREAMING_INTEGRATION_BASE),
            ("merge_sha", "e" * 40),
            ("state", "closed"),
        ):
            with self.subTest(field=field), self.assertRaises(ValueError):
                governance.validate_rec_i3_streaming_integration_context(
                    replace(pr, github_pull_request_context=replace(context, **{field: value})), base
                )

    def test_streaming_integration_history_rejects_topology_trailer_blob_and_post_import_drift(self) -> None:
        head = governance.REC_I3_STREAMING_INTEGRATION_HEAD
        with patch.object(governance, "git_is_ancestor", return_value=False), self.assertRaisesRegex(
            ValueError, "omits or replaces"
        ):
            governance.validate_rec_i3_streaming_integration_history(head)

        original_output = governance.git_output
        first_commit = governance.REC_I3_STREAMING_INTEGRATION_IMPORTS[0][0]

        def changed_trailer(*args: str) -> str:
            if args == ("show", "-s", "--format=%B", first_commit):
                return "message without source trailer"
            return original_output(*args)

        with patch.object(governance, "git_output", side_effect=changed_trailer), self.assertRaisesRegex(
            ValueError, "source trailer drift"
        ):
            governance.validate_rec_i3_streaming_integration_history(head)

        protected = governance.REC_I3_STREAMING_INTEGRATION_PROTECTED_PATHS[0]

        def changed_blob(*args: str) -> str:
            if args == ("rev-parse", f"{head}:{protected}"):
                return "0" * 40
            return original_output(*args)

        with patch.object(governance, "git_output", side_effect=changed_blob), self.assertRaisesRegex(
            ValueError, "protected predecessor blob"
        ):
            governance.validate_rec_i3_streaming_integration_history(head)

        changes = {name: [] for name in ("committed", "staged", "unstaged", "untracked")}
        changes["unstaged"] = ["docs/evidence/poc-recovery-001/injected.json"]
        with patch.object(governance, "collect_post_merge_changes", return_value=changes), self.assertRaisesRegex(
            ValueError, "correction escapes exact scope"
        ):
            governance.validate_rec_i3_streaming_integration_history(head)

    def test_original_reconciliation_epoch_still_rejects_streaming_paths(self) -> None:
        with self.assertRaisesRegex(ValueError, "reconciliation epoch contains an undeclared path"):
            governance.validate_rec_i3_reconciliation_successor(publication=False)

    def test_reconciliation_round4_author_mapping_is_exact_and_current(self) -> None:
        record = copy.deepcopy(governance.read_json(governance.REC_I3_RECON_EVIDENCE_PATH))
        record["sourceFiles"] = {
            path: governance.canonical_lf_sha256(path)
            for path in governance.REC_I3_RECON_SOURCE_PATHS
        }
        record["round4Truth"]["authorVerification"] = {
            "status": "PASS",
            "acceptanceCases": copy.deepcopy(
                list(governance.REC_I3_RECON_ROUND4_ACCEPTANCE_CASES)
            ),
        }
        record["checks"] = [
            item
            for item in record["checks"]
            if item.get("stage") not in {"ROUND4_ANDROID_HOST", "ROUND4_GOVERNANCE"}
        ]
        record["checks"].extend(
            [
                copy.deepcopy(governance.REC_I3_RECON_ROUND4_ANDROID_CHECK),
                copy.deepcopy(governance.REC_I3_RECON_ROUND4_GOVERNANCE_CHECK),
            ]
        )
        with patch.object(governance, "read_json", return_value=record):
            governance.validate_rec_i3_reconciliation_successor(
                publication=False, epoch_head=governance.REC_I3_STREAMING_INTEGRATION_BASE
            )

        historical_cases = record["round3Correction"]["authorVerification"]["acceptanceCases"]
        mutations = {
            "round4-pass-without-cases": lambda r: r["round4Truth"].__setitem__(
                "authorVerification", {"status": "PASS"}
            ),
            "round4-acceptance-missing": lambda r: r["round4Truth"]["authorVerification"][
                "acceptanceCases"
            ].pop(),
            "round4-acceptance-duplicate": lambda r: r["round4Truth"]["authorVerification"][
                "acceptanceCases"
            ].__setitem__(3, copy.deepcopy(r["round4Truth"]["authorVerification"]["acceptanceCases"][0])),
            "round4-stale-contextual-copy": lambda r: r["round4Truth"]["authorVerification"][
                "acceptanceCases"
            ].__setitem__(0, copy.deepcopy(historical_cases[2])),
            "round4-stale-path-copy": lambda r: r["round4Truth"]["authorVerification"][
                "acceptanceCases"
            ].__setitem__(1, copy.deepcopy(historical_cases[7])),
            "round4-context-helper-only": lambda r: r["round4Truth"]["authorVerification"][
                "acceptanceCases"
            ][0].__setitem__("test", historical_cases[2]["test"]),
            "round4-artifact-role-missing": lambda r: r["round4Truth"]["authorVerification"][
                "acceptanceCases"
            ][0].__setitem__("assertion", "Manifest and unit ciphertext context omitted."),
            "round4-bootstrap-state-missing": lambda r: r["round4Truth"]["authorVerification"][
                "acceptanceCases"
            ][0].__setitem__("assertion", "ABSENT and PRESENT remain distinct."),
            "round4-cursor-helper-only": lambda r: r["round4Truth"]["authorVerification"][
                "acceptanceCases"
            ][2].__setitem__(
                "test",
                "AndroidRecoveryReconciliationSourceTest.bootstrap cursor decoder returns null for zero rows; bootstrap cursor decoder decodes the one stored identity at position zero; bootstrap cursor decoder rejects two rows as a structural journal failure",
            ),
            "round4-path-actual-entry-missing": lambda r: r["round4Truth"]["authorVerification"][
                "acceptanceCases"
            ][1].__setitem__(
                "test",
                "RecoveryQuarantineControllerTest.actual quarantine entry distinguishes unsafe paths from ordinary IO",
            ),
            "round4-production-entry-shortened": lambda r: r["round4Truth"]["authorVerification"][
                "acceptanceCases"
            ][0].__setitem__(
                "productionEntry", "RecoveryMicrofileReconciliationController.reconcile"
            ),
            "round4-current-android-check-missing": lambda r: r.__setitem__(
                "checks",
                [item for item in r["checks"] if item.get("stage") != "ROUND4_ANDROID_HOST"],
            ),
            "round4-current-governance-check-missing": lambda r: r.__setitem__(
                "checks",
                [item for item in r["checks"] if item.get("stage") != "ROUND4_GOVERNANCE"],
            ),
            "round4-current-check-failed": lambda r: next(
                item for item in r["checks"] if item.get("stage") == "ROUND4_ANDROID_HOST"
            ).__setitem__("failures", 1),
            "round4-stale-duplicate-android-check": lambda r: r["checks"].insert(
                next(
                    index
                    for index, item in enumerate(r["checks"])
                    if item.get("stage") == "ROUND4_ANDROID_HOST"
                ),
                {
                    **copy.deepcopy(governance.REC_I3_RECON_ROUND4_ANDROID_CHECK),
                    "outcome": "FAIL",
                },
            ),
            "round4-stale-duplicate-governance-check": lambda r: r["checks"].insert(
                next(
                    index
                    for index, item in enumerate(r["checks"])
                    if item.get("stage") == "ROUND4_GOVERNANCE"
                ),
                {
                    **copy.deepcopy(governance.REC_I3_RECON_ROUND4_GOVERNANCE_CHECK),
                    "outcome": "FAIL",
                },
            ),
            "round4-finding-removed": lambda r: r["round4Truth"]["openFindingIds"].pop(),
            "round4-effective-review-clean": lambda r: r["round4Truth"].__setitem__(
                "effectiveReview", {"status": "CLEAN", "counts": {"p0": 0, "p1": 0, "p2": 0}}
            ),
            "round4-independent-clean": lambda r: r["round4Truth"]["independentReview"].__setitem__(
                "status", "CLEAN"
            ),
            "round4-formal-reviewer": lambda r: r["round4Truth"]["independentReview"].__setitem__(
                "formalReviewer", True
            ),
            "round4-accountable-approved": lambda r: r["round4Truth"].__setitem__(
                "accountableReview", "APPROVED"
            ),
            "round4-overall-local-verified": lambda r: r.__setitem__(
                "implementationStatus", "LOCAL_VERIFIED"
            ),
        }
        for name, mutate in mutations.items():
            changed = copy.deepcopy(record)
            mutate(changed)
            with self.subTest(mutation=name), patch.object(
                governance, "read_json", return_value=changed
            ), self.assertRaises(ValueError):
                governance.validate_rec_i3_reconciliation_successor(
                    publication=False, epoch_head=governance.REC_I3_STREAMING_INTEGRATION_BASE
                )

        not_run = copy.deepcopy(record)
        not_run["round4Truth"]["authorVerification"] = {"status": "NOT_RUN"}
        not_run["checks"] = [
            item
            for item in not_run["checks"]
            if item.get("stage") not in {"ROUND4_ANDROID_HOST", "ROUND4_GOVERNANCE"}
        ]
        with patch.object(governance, "read_json", return_value=not_run), self.assertRaisesRegex(
            ValueError, "requires author checks"
        ):
            governance.validate_rec_i3_reconciliation_successor(
                publication=True, epoch_head=governance.REC_I3_STREAMING_INTEGRATION_BASE
            )

    def test_reconciliation_round3_truth_is_exact_and_open(self) -> None:
        record = governance.read_json(governance.REC_I3_RECON_EVIDENCE_PATH)
        self.assertEqual("IN_PROGRESS", record["implementationStatus"])
        self.assertEqual("REVISE", record["review"]["independentAdvisory"])
        self.assertEqual({"p0": 0, "p1": 7, "p2": 1}, record["review"]["counts"])
        self.assertEqual(
            "SUPERSEDED_BY_A020944_INDEPENDENT_REVISE",
            record["round2Correction"]["status"],
        )
        self.assertEqual(
            {"p0": 0, "p1": 4, "p2": 2},
            record["round2Correction"]["successorReview"]["counts"],
        )
        self.assertEqual(
            list(governance.REC_I3_RECON_ROUND3_FINDINGS),
            record["round3Correction"]["openFindingIds"],
        )
        self.assertFalse(record["round3Correction"]["actualEntryReviewClosureComplete"])
        self.assertEqual(
            {"p0": 0, "p1": 4, "p2": 0},
            record["round4Truth"]["effectiveReview"]["counts"],
        )
        self.assertEqual(
            "de735735ace6da3572c45dfdc58a8bbff98145b0",
            record["round4Truth"]["successorReview"]["targetCommit"],
        )
        self.assertEqual(
            list(governance.REC_I3_RECON_ROUND4_FINDINGS),
            record["round4Truth"]["openFindingIds"],
        )
        self.assertEqual("PENDING", record["round4Truth"]["independentReview"]["status"])
        self.assertFalse(record["round4Truth"]["independentReview"]["formalReviewer"])
        self.assertEqual("PASS", record["round3Correction"]["authorVerification"]["status"])
        self.assertEqual(
            list(governance.REC_I3_RECON_ROUND3_ACCEPTANCE_CASES),
            [
                item["id"]
                for item in record["round3Correction"]["authorVerification"]["acceptanceCases"]
            ],
        )
        record["sourceFiles"] = {
            path: governance.canonical_lf_sha256(path)
            for path in governance.REC_I3_RECON_SOURCE_PATHS
            if (governance.ROOT / path).exists()
        }
        with patch.object(governance, "read_json", return_value=record):
            governance.validate_rec_i3_reconciliation_successor(
                publication=False, epoch_head=governance.REC_I3_STREAMING_INTEGRATION_BASE
            )

        for mutation in ("verified", "closed", "missing", "clean", "review-closed"):
            changed = copy.deepcopy(record)
            if mutation == "verified":
                changed["implementationStatus"] = "LOCAL_VERIFIED"
            elif mutation == "closed":
                changed["round3Correction"]["actualEntryReviewClosureComplete"] = True
            elif mutation == "missing":
                changed["round3Correction"]["openFindingIds"].pop()
            elif mutation == "review-closed":
                changed["round3Correction"]["independentReview"]["findingIds"].pop()
            else:
                changed["round2Correction"]["successorReview"]["status"] = "CLEAN"
            with self.subTest(mutation=mutation), patch.object(
                governance,
                "read_json",
                return_value=changed,
            ), self.assertRaises(ValueError):
                governance.validate_rec_i3_reconciliation_successor(
                    publication=False, epoch_head=governance.REC_I3_STREAMING_INTEGRATION_BASE
                )

        for mutation in ("acceptance-missing", "acceptance-duplicate", "test-name-only"):
            changed = copy.deepcopy(record)
            cases = changed["round3Correction"]["authorVerification"]["acceptanceCases"]
            if mutation == "acceptance-missing":
                cases.pop()
            elif mutation == "acceptance-duplicate":
                cases[-1] = copy.deepcopy(cases[0])
            else:
                cases[0].pop("productionEntry")
                cases[0].pop("assertion")
            with self.subTest(mutation=mutation), patch.object(
                governance,
                "read_json",
                return_value=changed,
            ), self.assertRaisesRegex(ValueError, "production-entry acceptance mapping"):
                governance.validate_rec_i3_reconciliation_successor(
                    publication=False, epoch_head=governance.REC_I3_STREAMING_INTEGRATION_BASE
                )

    def test_reconciliation_correction_scope_and_new_files_are_exactly_declared(self) -> None:
        self.assertEqual(
            "e78571776d34756325289dcfcb3853c9696f3011",
            governance.REC_I3_RECON_CORRECTION_COMMIT,
        )
        self.assertIn(governance.REC_I3_RECON_CORRECTION_PATH, governance.REC_I3_ALLOWED_PATHS)
        expected_new = {
            "android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryReconciliationOutcomes.kt",
            "android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/journal/AndroidRecoveryReconciliationSource.kt",
            "android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryReconciliationAcceptanceTest.kt",
            "android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/journal/AndroidRecoveryReconciliationSourceTest.kt",
            "android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/storage/AndroidOsRecoveryReconciliationStorageTest.kt",
        }
        self.assertTrue(expected_new <= set(governance.REC_I3_RECON_SOURCE_PATHS))
        changes = {name: [] for name in ("committed", "staged", "unstaged", "untracked")}
        changes["untracked"] = list(expected_new)
        governance.validate_rec_i3_changed_paths(changes)
        changes["untracked"] = [next(iter(expected_new)) + ".undeclared"]
        with self.assertRaisesRegex(ValueError, "escapes exact scope"):
            governance.validate_rec_i3_changed_paths(changes)

    def test_reconciliation_epoch_exposes_only_declared_mutable_paths(self) -> None:
        self.assertIn(
            "android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/"
            "candidate/RecoveryMicrofileReconciliationController.kt",
            governance.REC_I3_CURRENT_MUTABLE_PATHS,
        )
        self.assertNotIn(
            governance.REC_I3_MICROFILE_FROZEN_BOOTSTRAP_PATHS[0],
            governance.REC_I3_CURRENT_MUTABLE_PATHS,
        )

    def test_sequential_microfile_successor_has_exact_scope_sources_and_nonclaims(self) -> None:
        self.assertEqual(
            "4eab3eae72b9196fbd114339b9fe96bba7705f00",
            governance.REC_I3_MICROFILE_SCOPE_COMMIT,
        )
        governance.validate_rec_i3_microfile_successor(publication=False)
        changes = {name: [] for name in ("committed", "staged", "unstaged", "untracked")}
        changes["untracked"] = list(governance.REC_I3_RECON_MUTABLE_PATHS)
        governance.validate_rec_i3_changed_paths(changes)
        changes["untracked"] = [
            "android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/candidate/FutureWriter.kt"
        ]
        with self.assertRaisesRegex(ValueError, "escapes exact scope"):
            governance.validate_rec_i3_changed_paths(changes)

    def test_microfile_successor_rejects_frozen_bootstrap_source_mutations(self) -> None:
        frozen = (
            "android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/bootstrap/AndroidRecoveryBootstrapCrypto.kt",
            "android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/bootstrap/AndroidRecoveryKeyBootstrap.kt",
            "android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/storage/RecoveryBootstrapPathPolicy.kt",
            "android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/storage/AndroidOsRecoveryBootstrapStorage.kt",
            "android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/bootstrap/RecoveryKeyBootstrapControllerTest.kt",
            "android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/storage/RecoveryBootstrapPathPolicyTest.kt",
        )
        for relative in frozen:
            changes = {name: [] for name in ("committed", "staged", "unstaged", "untracked")}
            changes["unstaged"] = [relative]
            with self.subTest(relative=relative), self.assertRaisesRegex(
                ValueError, "escapes exact scope"
            ):
                governance.validate_rec_i3_changed_paths(changes)

    def test_persistence_profile_bypasses_legacy_bootstrap_dispatch(self) -> None:
        with patch.object(governance, "validate_rec_i3_bootstrap_evidence") as validator, patch.object(
            governance, "validate_rec_i3_microfile_successor"
        ):
            self.assertTrue(governance.validate_current_rec_i3_successor())
        validator.assert_not_called()

    def test_frozen_bootstrap_source_is_checked_against_reviewed_blob(self) -> None:
        governance.validate_rec_i3_frozen_bootstrap_sources()
        original = governance.canonical_lf_sha256
        frozen = governance.REC_I3_MICROFILE_FROZEN_BOOTSTRAP_PATHS[0]
        with patch.object(
            governance,
            "canonical_lf_sha256",
            side_effect=lambda relative: "0" * 64 if relative == frozen else original(relative),
        ), self.assertRaisesRegex(ValueError, "frozen bootstrap source changed"):
            governance.validate_rec_i3_frozen_bootstrap_sources()

    def test_bootstrap_provider_witness_correction_has_exact_scope_lineage(self) -> None:
        self.assertEqual(
            "cd752f952666f414465c73bc55a0d7f7f20c4989",
            governance.REC_I3_BOOTSTRAP_WITNESS_SCOPE_COMMIT,
        )
        governance.validate_rec_i3_bootstrap_witness_scope_lineage()
        changes = {name: [] for name in ("committed", "staged", "unstaged", "untracked")}
        changes["committed"] = [governance.REC_I3_BOOTSTRAP_WITNESS_SCOPE_PATH]
        governance.validate_rec_i3_changed_paths(changes)

    def test_bootstrap_successor_has_exact_scope_first_identity_and_file_boundary(self) -> None:
        self.assertEqual(
            "f89ddba14d37efbdde5a99bf1fd169210ff189cb",
            governance.REC_I3_BOOTSTRAP_SCOPE_COMMIT,
        )
        governance.validate_rec_i3_bootstrap_scope_lineage()
        changes = {name: [] for name in ("committed", "staged", "unstaged", "untracked")}
        changes["committed"] = list(governance.REC_I3_BOOTSTRAP_SOURCE_PATHS)
        governance.validate_rec_i3_changed_paths(changes)
        changes["untracked"] = [
            "android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/bootstrap/Extra.kt"
        ]
        with self.assertRaisesRegex(ValueError, "escapes exact scope"):
            governance.validate_rec_i3_changed_paths(changes)

    def test_bootstrap_preparation_evidence_preserves_claim_ceiling_and_first_slice_hashes(self) -> None:
        record = copy.deepcopy(governance.read_json(governance.REC_I3_BOOTSTRAP_EVIDENCE_PATH))
        record["implementationStatus"] = "IN_PROGRESS"
        record["sourceFiles"] = {}
        governance.validate_rec_i3_bootstrap_evidence(record, {})
        mutations = [
            lambda r: r.__setitem__("fullRecI3Completed", True),
            lambda r: r.__setitem__("recoveryPreflightUnlocked", True),
            lambda r: r.__setitem__("readinessBlockersClosed", ["REC-RDY-07"]),
            lambda r: r.__setitem__("scopeFirstCommit", governance.REC_I3_SCOPE_COMMIT),
            lambda r: r["preservedFirstSliceSourceFiles"].__setitem__(
                governance.REC_I3_SOURCE_PATHS[0], "0" * 64
            ),
            lambda r: r["execution"].__setitem__("device", True),
            lambda r: r["review"].__setitem__("independentAdvisory", "CLEAN"),
            lambda r: r.__setitem__("implementationStatus", "LOCAL_VERIFIED"),
        ]
        for index, mutate in enumerate(mutations):
            candidate = copy.deepcopy(record)
            mutate(candidate)
            with self.subTest(mutation=index), self.assertRaises(ValueError):
                governance.validate_rec_i3_bootstrap_evidence(candidate, {})

    def test_bootstrap_scope_is_frozen_after_scope_first_commit(self) -> None:
        for layer in ("committed", "staged", "unstaged", "untracked"):
            changes = {name: [] for name in ("committed", "staged", "unstaged", "untracked")}
            changes[layer] = [governance.REC_I3_BOOTSTRAP_SCOPE_PATH]
            with self.subTest(layer=layer), self.assertRaisesRegex(ValueError, "bootstrap scope changed"):
                governance.validate_rec_i3_bootstrap_scope_frozen(changes)

    def test_authorized_worktree_reaches_the_dependency_inventory_entrypoint(self) -> None:
        try:
            accepted = governance.validate_current_rec_i2b_reviewed_successor()
        except ValueError as error:
            self.fail(f"Authorized partial REC-I3 must preserve the predecessor and dispatch: {error}")
        self.assertTrue(accepted)

    def test_exact_paths_in_each_layer_and_no_implicit_recovery_directory_permission(self) -> None:
        for layer in ("committed", "staged", "unstaged", "untracked"):
            changes = {name: [] for name in ("committed", "staged", "unstaged", "untracked")}
            changes[layer] = list(
                governance.REC_I3_ALLOWED_PATHS
                if layer == "committed"
                else governance.REC_I3_CURRENT_MUTABLE_PATHS
            )
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

    def test_frozen_bootstrap_edit_and_restore_is_rejected_from_source_head_history(self) -> None:
        with tempfile.TemporaryDirectory(prefix="dora-rec-i3-frozen-history-") as temporary:
            repo, _ = governance.initialize_test_git_repo(Path(temporary))
            relative = governance.REC_I3_MICROFILE_FROZEN_BOOTSTRAP_PATHS[0]
            path = repo / relative
            path.parent.mkdir(parents=True)
            path.write_bytes(b"reviewed bootstrap\n")
            base = governance.commit_test_git_repo(repo, "reviewed bootstrap checkpoint")
            path.write_bytes(b"transient unauthorized edit\n")
            governance.commit_test_git_repo(repo, "edit frozen bootstrap")
            path.write_bytes(b"reviewed bootstrap\n")
            restored = governance.commit_test_git_repo(repo, "restore frozen bootstrap")
            tree = governance.test_git_text(repo, "rev-parse", "HEAD^{tree}")
            synthetic = governance.test_git_text(
                repo, "-c", "user.name=Dora Validator Test", "-c",
                "user.email=dora-validator@example.invalid", "commit-tree", tree,
                "-p", base, "-p", restored, input_data=b"synthetic GitHub merge",
            )
            local = governance.RecoveryLifecycleIdentity(
                restored, governance.REC_I3_BRANCH, None, None, None, None, (), None, None, False,
            )
            context = governance.GitHubPullRequestContext(
                governance.GITHUB_REPOSITORY, governance.GITHUB_REPOSITORY, governance.REC_I3_BRANCH,
                restored, "main", base, "refs/pull/99/merge", "c" * 40, 99, True, "open", False,
            )
            pull_request = replace(
                local, head=synthetic,
                github_pull_request_context=replace(context, head_sha=restored, merge_sha=synthetic),
            )
            with (
                patch.object(governance, "ROOT", repo),
                patch.object(governance, "REC_I3_BASE", base),
                patch.object(governance, "REC_I3_MICROFILE_REVIEWED_BOOTSTRAP_COMMIT", base),
            ):
                with self.assertRaisesRegex(ValueError, "frozen bootstrap history"):
                    governance.validate_rec_i3_candidate_history(local)
                governance.test_git(repo, "update-ref", "refs/heads/main", synthetic)
                with self.assertRaisesRegex(ValueError, "frozen bootstrap history"):
                    governance.validate_rec_i3_candidate_history(pull_request)

    def test_frozen_bootstrap_staged_edit_cannot_hide_behind_restored_worktree_bytes(self) -> None:
        with tempfile.TemporaryDirectory(prefix="dora-rec-i3-frozen-index-") as temporary:
            repo, _ = governance.initialize_test_git_repo(Path(temporary))
            relative = governance.REC_I3_MICROFILE_FROZEN_BOOTSTRAP_PATHS[0]
            path = repo / relative
            path.parent.mkdir(parents=True)
            path.write_bytes(b"reviewed bootstrap\n")
            base = governance.commit_test_git_repo(repo, "reviewed bootstrap checkpoint")
            path.write_bytes(b"staged unauthorized edit\n")
            governance.test_git(repo, "add", relative)
            path.write_bytes(b"reviewed bootstrap\n")
            changes = governance.collect_post_merge_changes(root=repo, merged_anchor=base)
            self.assertIn(relative, changes["staged"])
            self.assertEqual(b"reviewed bootstrap\n", path.read_bytes())
            with self.assertRaisesRegex(ValueError, "staged delta escapes exact scope"):
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
            with (
                patch.object(governance, "ROOT", repo),
                patch.object(governance, "REC_I3_BASE", base),
                patch.object(governance, "REC_I3_MICROFILE_REVIEWED_BOOTSTRAP_COMMIT", base),
            ):
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


class RecoveryI3ResultBoundaryGovernanceTests(unittest.TestCase):
    def fixture(self) -> tuple[dict, dict]:
        return (
            copy.deepcopy(governance.read_json(governance.REC_I3_RESULT_BOUNDARY_GATE_PATH)),
            copy.deepcopy(governance.read_json(governance.REC_I3_RESULT_BOUNDARY_PROTOCOL_PATH)),
        )

    def reject(self, mutation) -> None:
        gate, protocol = self.fixture()
        governance.validate_rec_i3_result_boundary_contract(gate, protocol)
        mutation(gate, protocol)
        with self.assertRaises(ValueError):
            governance.validate_rec_i3_result_boundary_contract(gate, protocol)

    def test_v08_accepts_twenty_path_total_mapping_and_retry_only_exceptions(self) -> None:
        gate, protocol = self.fixture()
        governance.validate_rec_i3_result_boundary_contract(gate, protocol)
        boundary = protocol["streamingResultBoundaryV08"]
        self.assertEqual((6, 20, 20), (
            len(boundary["stages"]), len(boundary["classifications"]),
            len(boundary["resultMappings"]),
        ))
        by_class = {item["classification"]: item for item in boundary["resultMappings"]}
        self.assertEqual(["SQLITE"], by_class["JOURNAL_OPERATIONAL"]["safeExceptionTypes"])
        self.assertEqual(["SQLITE"], by_class["JOURNAL_COMMIT_STATE_UNRESOLVED"]["safeExceptionTypes"])
        self.assertEqual(["CRYPTO"], by_class["STREAM_CHECKPOINT_AUTHENTICATION_OPERATIONAL"]["safeExceptionTypes"])
        self.assertEqual(
            ["JOURNAL_AMBIGUOUS_COMMIT_WITH_NO_EXACT_INTENDED_STATE"],
            boundary["rejectedAliases"],
        )

    def test_v08_rejects_mapping_exception_alias_and_reference_drift(self) -> None:
        mutations = (
            lambda g, p: p["inheritsExactV07"]["sha256"].__setitem__("protocol", "0" * 64),
            lambda g, p: g.__setitem__("protocolLocator", "wrong.json"),
            lambda g, p: p["streamingPersistenceV07"]["database"].__setitem__(
                "userVersion", 5
            ),
            lambda g, p: p["streamingResultBoundaryV08"]["classifications"].pop(),
            lambda g, p: p["streamingResultBoundaryV08"]["resultMappings"].append(
                copy.deepcopy(p["streamingResultBoundaryV08"]["resultMappings"][0])
            ),
            lambda g, p: next(x for x in p["streamingResultBoundaryV08"]["resultMappings"]
                              if x["variant"] == "Fatal")["safeExceptionTypes"].append("NONE"),
            lambda g, p: next(x for x in p["streamingResultBoundaryV08"]["resultMappings"]
                              if x["classification"] == "JOURNAL_OPERATIONAL").__setitem__(
                                  "safeExceptionTypes", []),
            lambda g, p: p["streamingResultBoundaryV08"]["rejectedAliases"].append("JOURNAL_OPERATIONAL"),
            lambda g, p: p["streamingResultBoundaryV08"]["existingReferences"]["perClassification"][
                "STREAM_ACTIVE_RANGE_DENIED"
            ].append("STREAM_CHECKPOINT"),
            lambda g, p: p["streamingResultBoundaryV08"]["existingReferences"].__setitem__(
                "idByteOrder", "SIGNED_LEXICOGRAPHIC"),
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                self.reject(mutation)

    def test_strict_json_rejects_duplicate_keys_and_non_finite_numbers(self) -> None:
        with tempfile.TemporaryDirectory(prefix="dora-rec-i3-v08-json-") as temporary:
            root = Path(temporary)
            duplicate = root / "duplicate.json"
            duplicate.write_text('{"a":1,"a":2}', encoding="utf-8")
            non_finite = root / "non-finite.json"
            non_finite.write_text('{"a":NaN}', encoding="utf-8")
            with patch.object(governance, "ROOT", root):
                with self.assertRaisesRegex(ValueError, "Duplicate JSON key"):
                    governance.read_json("duplicate.json")
                with self.assertRaisesRegex(ValueError, "Non-finite JSON number"):
                    governance.read_json("non-finite.json")

    def test_v08_rejects_read_receipt_delivery_and_authority_drift(self) -> None:
        mutations = (
            lambda g, p: p["streamingResultBoundaryV08"]["behaviorAssertions"]["zeroProgress"].__setitem__("additionalRead", True),
            lambda g, p: p["streamingResultBoundaryV08"]["behaviorAssertions"]["readCrossesAcceptedEnd"].__setitem__("returnedBufferRetained", True),
            lambda g, p: p["streamingResultBoundaryV08"]["behaviorAssertions"]["activeRangeDenied"].__setitem__("sourceOpen", True),
            lambda g, p: p["streamingResultBoundaryV08"]["receipt"].__setitem__("coreCreatedAtExactReadback", False),
            lambda g, p: p["streamingResultBoundaryV08"]["receipt"].__setitem__("finalReceiptConstructedAfterSinkAttempt", False),
            lambda g, p: p["streamingResultBoundaryV08"]["receipt"].__setitem__("autonomousDeliveryGuarantee", True),
            lambda g, p: g["governancePatchAllowlist"].append("android/app/Injected.kt"),
            lambda g, p: g["campaignCounts"].__setitem__("mandatoryFaultRowCount", 45),
            lambda g, p: g["campaignCounts"].__setitem__("mandatoryFaultRowCount", True),
            lambda g, p: g["authority"].__setitem__("executionAllowed", True),
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                self.reject(mutation)

    def test_v08_rejects_paired_predecessor_policy_drift(self) -> None:
        mutations = (
            lambda g, p: (
                g["authority"].__setitem__("executionAllowed", True),
                p["unchangedV07"]["authority"].__setitem__("executionAllowed", True),
            ),
            lambda g, p: (
                g["readinessLocks"].__setitem__("campaignReady", True),
                p["unchangedV07"]["readinessLocks"].__setitem__("campaignReady", True),
            ),
            lambda g, p: (
                g["campaignCounts"].__setitem__("phaseAInjectionCount", 185),
                p["unchangedV07"]["campaignCounts"].__setitem__("phaseAInjectionCount", 185),
            ),
            lambda g, p: (
                g.__setitem__("status", "READY"),
                p.__setitem__("status", "READY"),
            ),
            lambda g, p: g["activeBlockers"].pop(),
            lambda g, p: g["historicalClosure"].__setitem__("REC-RDY-02", "OPEN"),
            lambda g, p: g["readinessLocks"].__setitem__("pocRecoveryStatus", "READY"),
            lambda g, p: g.__setitem__("decision", "DEC-000"),
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                self.reject(mutation)

    def test_v08_rejects_every_receipt_and_public_evidence_policy_mutation(self) -> None:
        _, protocol = self.fixture()
        receipt = protocol["streamingResultBoundaryV08"]["receipt"]
        mutations = []
        for key, value in receipt.items():
            if type(value) is bool:
                mutations.append(
                    lambda g, p, field=key, original=value: p["streamingResultBoundaryV08"][
                        "receipt"
                    ].__setitem__(field, not original)
                )
            else:
                mutations.append(
                    lambda g, p, field=key: p["streamingResultBoundaryV08"]["receipt"][
                        field
                    ].append("UNEXPECTED")
                )
        for key, value in protocol["streamingResultBoundaryV08"]["nonPersistableEvidence"].items():
            mutations.append(
                lambda g, p, field=key, original=value: p["streamingResultBoundaryV08"][
                    "nonPersistableEvidence"
                ].__setitem__(field, not original)
            )
        mutations.extend((
            lambda g, p: p["streamingResultBoundaryV08"]["publicEventForbiddenFields"].remove(
                "plaintext"
            ),
            lambda g, p: p["streamingResultBoundaryV08"]["behaviorAssertions"][
                "zeroProgress"
            ].__setitem__("unexpected", False),
            lambda g, p: p["inheritsExactV07"].__setitem__("unexpected", False),
        ))
        for index, mutation in enumerate(mutations):
            with self.subTest(mutation=index):
                self.reject(mutation)

    def test_real_git_v08_delta_rejects_dirty_rename_delete_and_revert(self) -> None:
        for mutation in ("staged", "unstaged", "untracked", "rename", "delete", "revert"):
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory(
                prefix="dora-rec-i3-v08-delta-"
            ) as temporary:
                repo, base = governance.initialize_test_git_repo(Path(temporary))
                for relative in governance.REC_I3_RESULT_BOUNDARY_PATHS:
                    path = repo / relative
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_text(relative, encoding="utf-8")
                candidate = governance.commit_test_git_repo(repo, "exact governance delta")
                target = repo / governance.REC_I3_RESULT_BOUNDARY_PATHS[0]
                if mutation == "staged":
                    target.write_text("staged", encoding="utf-8")
                    governance.test_git(repo, "add", str(target.relative_to(repo)))
                elif mutation == "unstaged":
                    target.write_text("unstaged", encoding="utf-8")
                elif mutation == "untracked":
                    (repo / "unexpected.txt").write_text("untracked", encoding="utf-8")
                elif mutation == "rename":
                    governance.test_git(repo, "mv", str(target.relative_to(repo)), "renamed.txt")
                    governance.commit_test_git_repo(repo, "rename governance file")
                elif mutation == "delete":
                    target.unlink()
                    governance.commit_test_git_repo(repo, "delete governance file")
                else:
                    governance.test_git(
                        repo, "-c", "user.name=Dora Validator Test", "-c",
                        "user.email=dora-validator@example.invalid",
                        "revert", "--no-edit", candidate,
                    )
                head = governance.test_git_text(repo, "rev-parse", "HEAD")
                changes = governance.collect_post_merge_changes(root=repo, merged_anchor=base)
                tree_paths = governance.git_path_records(
                    "diff", "--name-only", "--no-renames", "-z", base, head, "--", root=repo
                )
                history_paths = governance.git_path_records(
                    "log", "--format=", "--name-only", "--no-renames", "-z",
                    f"{base}..{head}", "--", root=repo
                )
                summary = governance.git_output(
                    "log", "--format=", "--summary", "--find-renames",
                    f"{base}..{head}", "--", root=repo,
                )
                with self.assertRaisesRegex(ValueError, "exact committed ten-path delta|rename/delete"):
                    governance.validate_rec_i3_result_boundary_delta(
                        changes, tree_paths, history_paths, summary
                    )

    def test_v08_profile_is_distinct_and_preserves_mocked_v07_dispatch(self) -> None:
        base = governance.RecoveryLifecycleIdentity(
            "c" * 40, governance.REC_I3_STREAMING_PERSISTENCE_BRANCH,
            None, None, None, None, (), None, None, False,
        )
        current = replace(base, branch=governance.REC_I3_RESULT_BOUNDARY_BRANCH)
        self.assertTrue(governance.rec_i3_streaming_persistence_candidate(base))
        self.assertFalse(governance.rec_i3_result_boundary_candidate(base))
        self.assertTrue(governance.rec_i3_result_boundary_candidate(current))
        self.assertFalse(governance.rec_i3_streaming_persistence_candidate(current))

    def test_exact_v08_profile_accepts_current_checkout(self) -> None:
        lifecycle = governance.collect_recovery_lifecycle_identity()
        self.assertEqual(governance.REC_I3_RESULT_BOUNDARY_BRANCH, lifecycle.branch)
        self.assertTrue(governance.validate_current_rec_i3_successor(lifecycle))

    def test_exact_v08_profile_rejects_changed_v07_worktree_blob(self) -> None:
        lifecycle = governance.collect_recovery_lifecycle_identity()
        target = next(iter(governance.REC_I3_RESULT_BOUNDARY_V07_SHA256))
        original = governance.sha256
        with patch.object(
            governance,
            "sha256",
            side_effect=lambda relative: "0" * 64 if relative == target else original(relative),
        ), self.assertRaisesRegex(ValueError, "immutable v0.7 blob changed"):
            governance.validate_rec_i3_result_boundary(lifecycle)

    def test_main_dispatches_v08_before_any_legacy_static_artifact_read(self) -> None:
        original = governance.read_json
        original_text = governance.read_text
        original_collect = governance.collect_post_merge_changes
        allowed = {
            governance.REC_I3_RESULT_BOUNDARY_GATE_PATH,
            governance.REC_I3_RESULT_BOUNDARY_PROTOCOL_PATH,
        }
        reads: list[str] = []

        def legacy_absent(relative: str) -> dict:
            reads.append(relative)
            if relative not in allowed:
                raise FileNotFoundError(f"synthetic absent legacy artifact: {relative}")
            return original(relative)

        def clean_committed_profile(**kwargs) -> dict[str, list[str]]:
            changes = original_collect(**kwargs)
            for layer in ("staged", "unstaged", "untracked"):
                changes[layer] = []
            return changes

        def legacy_text_absent(relative: str) -> str:
            if relative not in allowed:
                raise FileNotFoundError(f"synthetic absent legacy text artifact: {relative}")
            return original_text(relative)

        with (
            patch.object(governance, "read_json", side_effect=legacy_absent),
            patch.object(governance, "read_text", side_effect=legacy_text_absent),
            patch.object(governance, "collect_post_merge_changes", side_effect=clean_committed_profile),
            patch.object(sys, "argv", ["validate_poc_recovery_governance.py"]),
        ):
            self.assertEqual(0, governance.main())
        self.assertEqual(
            {
                governance.REC_I3_RESULT_BOUNDARY_GATE_PATH,
                governance.REC_I3_RESULT_BOUNDARY_PROTOCOL_PATH,
            },
            set(reads),
        )

    def test_main_v08_fast_path_rejects_protected_and_predecessor_mutations(self) -> None:
        original_read = governance.read_json
        original_collect = governance.collect_post_merge_changes

        def clean_committed_profile(**kwargs) -> dict[str, list[str]]:
            changes = original_collect(**kwargs)
            for layer in ("staged", "unstaged", "untracked"):
                changes[layer] = []
            return changes

        def mutated_gate(relative: str) -> dict:
            record = copy.deepcopy(original_read(relative))
            if relative == governance.REC_I3_RESULT_BOUNDARY_GATE_PATH:
                record["authority"]["executionAllowed"] = True
            return record

        with (
            patch.object(governance, "collect_post_merge_changes", side_effect=clean_committed_profile),
            patch.object(governance, "read_json", side_effect=mutated_gate),
            patch.object(sys, "argv", ["validate_poc_recovery_governance.py"]),
            self.assertRaisesRegex(ValueError, "authority"),
        ):
            governance.main()

        target = next(iter(governance.REC_I3_RESULT_BOUNDARY_V07_SHA256))
        original_sha = governance.sha256
        with (
            patch.object(governance, "collect_post_merge_changes", side_effect=clean_committed_profile),
            patch.object(
                governance, "sha256",
                side_effect=lambda relative: "0" * 64 if relative == target else original_sha(relative),
            ),
            patch.object(sys, "argv", ["validate_poc_recovery_governance.py"]),
            self.assertRaisesRegex(ValueError, "immutable v0.7 blob changed"),
        ):
            governance.main()


if __name__ == "__main__":
    unittest.main()
