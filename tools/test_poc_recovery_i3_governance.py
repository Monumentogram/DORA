"""Behavior and real-Git mutation tests for the bounded REC-I3 successor."""

from __future__ import annotations

import copy
import ctypes
import io
import json
import os
import re
import signal
import shutil
import subprocess
import sys
import tempfile
import time
import unittest
from dataclasses import replace
from contextlib import ExitStack, contextmanager, redirect_stdout
from pathlib import Path
from unittest.mock import patch

import validate_poc_recovery_governance as governance
import validate_stage00 as stage00


V8_REPAIRED_HOST_TEST = (
    "android/poc/recovery/src/test/kotlin/com/monumentogram/dora/poc/recovery/"
    "storage/AndroidOsRecoveryStreamingSourceTest.kt"
)
V10_CHECKPOINT_FIXTURE = (
    "android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/"
    "candidate/RecoveryCheckpointAndroidTestFixture.kt"
)
V10_PLAN = "docs/superpowers/plans/2026-09-09-rec-i3-v8-host-run-contract-repair.md"
V10_CANDIDATE_PATHS = (
    V10_CHECKPOINT_FIXTURE,
    "tools/test_poc_recovery_i3_governance.py",
    "tools/validate_poc_recovery_governance.py",
    V10_PLAN,
)
V11_JOURNAL = (
    "android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/"
    "journal/AndroidRecoveryJournalDatabase.kt"
)
V11_PREFLIGHT = (
    "android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/"
    "candidate/RecoveryE36GapiPreflightInstrumentedTest.kt"
)
V11_SQLITE_VERIFIER = "tools/verify_rec_i3_streaming_sqlite.py"
V11_PLAN = "docs/superpowers/plans/2026-09-10-rec-i3-v11-sqlite-openparams-full.md"
V11_CANDIDATE_PATHS = (
    V11_JOURNAL,
    V11_PREFLIGHT,
    V11_SQLITE_VERIFIER,
    "tools/test_poc_recovery_i3_governance.py",
    "tools/validate_poc_recovery_governance.py",
    V11_PLAN,
)


class RecoveryI3GovernanceTests(unittest.TestCase):
    @contextmanager
    def v11_repository(self):
        """Exact V11 FULL-sync OpenParams correction above immutable V10."""
        source = governance.ROOT
        with tempfile.TemporaryDirectory(prefix="dora-v11-governance-") as temporary:
            repo = Path(temporary) / "repo"
            git_pointer = source / ".git"
            git_directory = (
                source / git_pointer.read_text(encoding="utf-8").strip().removeprefix("gitdir: ")
            ).resolve() if git_pointer.is_file() else git_pointer.resolve()
            common_pointer = git_directory / "commondir"
            common_directory = (
                git_directory / common_pointer.read_text(encoding="utf-8").strip()
            ).resolve() if common_pointer.is_file() else git_directory
            repo.mkdir()
            governance.test_git(repo, "init", "-q")
            (repo / ".git/objects/info/alternates").write_text(
                (common_directory / "objects").as_posix() + "\n",
                encoding="utf-8",
                newline="\n",
            )
            governance.test_git(
                repo,
                "checkout",
                "-q",
                "-B",
                "codex/rec-i3-v11-sqlite-openparams-full",
                "e0e8b0e2e4ae210dc72b4042c42c526fec003b6a",
            )
            for relative in V11_CANDIDATE_PATHS:
                target = repo / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source / relative, target)
                if relative in {
                    "tools/test_poc_recovery_i3_governance.py",
                    "tools/validate_poc_recovery_governance.py",
                }:
                    with target.open("a", encoding="utf-8") as stream:
                        stream.write("\n# V11 governance fixture\n")
            governance.commit_test_git_repo(repo, "synthetic bounded V11 OpenParams candidate")
            with patch.object(governance, "ROOT", repo), patch.dict(os.environ, {
                key: "" for key in governance.REC_I3_V8_GITHUB_CONTEXT_KEYS
            }):
                yield repo

    def assert_v11_rejected(self, lifecycle=None) -> None:
        lifecycle = lifecycle or governance.collect_recovery_lifecycle_identity()
        self.assertFalse(governance.rec_i3_v11_candidate(lifecycle))
        with self.assertRaises((ValueError, subprocess.CalledProcessError)):
            governance.validate_rec_i3_v11(lifecycle)

    def test_v11_exact_openparams_successor_is_admitted_and_pinned(self) -> None:
        self.assertTrue(
            callable(getattr(governance, "rec_i3_v11_source_candidate", None)),
            "V11 source candidate missing",
        )
        with self.v11_repository() as repo:
            lifecycle = governance.collect_recovery_lifecycle_identity()
            base = "e0e8b0e2e4ae210dc72b4042c42c526fec003b6a"
            self.assertEqual(set(V11_CANDIDATE_PATHS), set(governance.REC_I3_V11_PATHS))
            self.assertEqual(
                set(V11_CANDIDATE_PATHS),
                set(governance.git_path_records(
                    "diff", "--name-only", "--no-renames", "-z", base, lifecycle.head, "--"
                )),
            )
            self.assertEqual(
                [V11_PREFLIGHT, V11_JOURNAL],
                governance.git_path_records(
                    "diff", "--name-only", "--no-renames", "-z", base, lifecycle.head,
                    "--", "android",
                ),
            )
            for relative, expected in governance.REC_I3_V11_PINNED_BLOBS.items():
                self.assertEqual(expected, governance.git_output("rev-parse", f"HEAD:{relative}"))
            self.assertEqual(
                governance.REC_I3_V10_FIXTURE_BLOB,
                governance.git_output("rev-parse", f"HEAD:{V10_CHECKPOINT_FIXTURE}"),
            )
            self.assertEqual(
                governance.git_output("rev-parse", f"{base}:.github"),
                governance.git_output("rev-parse", "HEAD:.github"),
            )
            self.assertTrue(governance.rec_i3_v11_source_candidate(lifecycle.head))
            self.assertTrue(governance.rec_i3_v11_candidate(lifecycle))
            governance.validate_rec_i3_v11(lifecycle)
            with patch.object(sys, "argv", ["governance"]), redirect_stdout(io.StringIO()) as output:
                self.assertEqual(0, governance.main())
            self.assertIn("V11 SQLite OpenParams FULL correction", output.getvalue())
            self.assertIn("V10 remains FAIL", output.getvalue())

    def test_v11_sqlite_diagnostic_is_complete_pre_assert_and_fail_closed(self) -> None:
        """Missing, late, unsafe, or success-bearing pre-assert diagnostics are rejected."""
        validator = getattr(governance, "validate_rec_i3_v11_diagnostic_text", None)
        self.assertTrue(callable(validator), "V11 diagnostic governance admission missing")
        validator(governance.read_text(V11_PREFLIGHT))

    def test_v11_sqlite_diagnostic_rejects_contract_mutations(self) -> None:
        """Each required diagnostic/query/failure/requirement boundary is mutation-sensitive."""
        source = governance.read_text(V11_PREFLIGHT)
        validator = getattr(governance, "validate_rec_i3_v11_diagnostic_text", None)
        self.assertTrue(callable(validator), "V11 diagnostic governance admission missing")
        mutations = {
            "diagnostic-after-assertion": source.replace(
                'println("INSTRUMENTATION_SQLITE_PRAGMAS_DIAGNOSTIC $sqliteDiagnostic")\n'
                '        val pragmaFailures',
                'val pragmaFailures',
            ).replace(
                'require(pragmas["journal_mode"].equals("wal", true))',
                'require(pragmas["journal_mode"].equals("wal", true))\n'
                '        println("INSTRUMENTATION_SQLITE_PRAGMAS_DIAGNOSTIC $sqliteDiagnostic")',
            ),
            "omitted-field": source.replace(
                '.put("queryContext", "ordinary_rawQuery")',
                '',
            ),
            "success-on-query-failure": source.replace(
                'require(pragmaFailures.isEmpty()) {',
                'if (pragmaFailures.isNotEmpty()) println("query-failure-ignored")\n        if (false) {',
            ),
            "unsafe-database-path": source.replace(
                '.put("connectionIdentity", "UNOBSERVED")',
                '.put("databasePath", sqlite.path)\n'
                '                .put("connectionIdentity", "UNOBSERVED")',
            ),
            "unsafe-exception-text": source.replace(
                '.put("connectionIdentity", "UNOBSERVED")',
                '.put("failureDetail", error.message)\n'
                '                .put("connectionIdentity", "UNOBSERVED")',
            ),
            "weakened-autocheckpoint": source.replace(
                'require(pragmas["wal_autocheckpoint"] == "0")',
                'require(pragmas["wal_autocheckpoint"] != null)',
            ),
            "omitted-query": source.replace(
                '"journal_mode", "synchronous", "wal_autocheckpoint", "foreign_keys"',
                '"journal_mode", "synchronous", "wal_autocheckpoint"',
            ),
            "duplicated-query": source.replace(
                '"journal_mode", "synchronous", "wal_autocheckpoint", "foreign_keys"',
                '"journal_mode", "synchronous", "wal_autocheckpoint", "foreign_keys", "foreign_keys"',
            ),
            "success-bearing-marker": source.replace(
                '"INSTRUMENTATION_SQLITE_PRAGMAS_DIAGNOSTIC $sqliteDiagnostic"',
                '"INSTRUMENTATION_SQLITE_PRAGMAS_DIAGNOSTIC PASS $sqliteDiagnostic"',
            ),
            "additional-direct-target-query": source.replace(
                "val queryFailureClassifications =",
                'val unexpectedPragma = pragma(sqlite, "journal_mode")\n'
                '        val queryFailureClassifications =',
            ),
            "pragma-assertion-before-diagnostic": source.replace(
                "val queryFailureClassifications =",
                'check(pragmaObservations.getValue("journal_mode").value != null)\n'
                '        val queryFailureClassifications =',
            ),
            "unapproved-database-field": source.replace(
                '.put("connectionIdentity", "UNOBSERVED")',
                '.put("database", sqlite)\n'
                '                .put("connectionIdentity", "UNOBSERVED")',
            ),
            "additional-direct-target-scalar-query": source.replace(
                "val queryFailureClassifications =",
                'val unexpectedPragma = scalar(sqlite, "PRAGMA journal_mode")\n'
                '        val queryFailureClassifications =',
            ),
            "unapproved-database-accumulate": source.replace(
                '.put("connectionIdentity", "UNOBSERVED")',
                '.accumulate("database", sqlite)\n'
                '                .put("connectionIdentity", "UNOBSERVED")',
            ),
        }
        for mutation, candidate in mutations.items():
            with self.subTest(mutation=mutation):
                self.assertNotEqual(source, candidate, "mutation fixture did not alter source")
                with self.assertRaises(ValueError):
                    validator(candidate)

    def test_v11_pull_request_binds_current_main_and_rejects_identity_topology_drift(self) -> None:
        with self.v11_repository() as repo:
            source = governance.git_output("rev-parse", "HEAD")
            tree = governance.git_output("rev-parse", "HEAD^{tree}")
            base = "55940df0c95e919a00708ae57e1b8aa23d89b6de"
            merge = governance.test_git_text(
                repo,
                "-c", "user.name=Dora Test",
                "-c", "user.email=dora@example.invalid",
                "commit-tree", tree, "-p", base, "-p", source,
                input_data=b"synthetic V11 PR merge\n",
            )
            governance.test_git(repo, "checkout", "-q", "--detach", merge)
            event_path = repo.parent / "event.json"
            governance.write_test_pull_request_event(
                event_path,
                number=91,
                head_ref="codex/rec-i3-v11-sqlite-openparams-full",
                head_sha=source,
                base_sha=base,
                merge_sha=merge,
                draft=False,
            )
            environment = {
                "GITHUB_EVENT_NAME": "pull_request",
                "GITHUB_REPOSITORY": "Monumentogram/DORA",
                "GITHUB_WORKSPACE": str(repo),
                "GITHUB_REF": "refs/pull/91/merge",
                "GITHUB_SHA": merge,
                "GITHUB_HEAD_REF": "codex/rec-i3-v11-sqlite-openparams-full",
                "GITHUB_BASE_REF": "main",
                "RUNNER_TEMP": str(repo.parent),
                "GITHUB_EVENT_PATH": str(event_path),
            }
            with patch.dict(os.environ, environment):
                lifecycle = governance.collect_recovery_lifecycle_identity()
                self.assertTrue(governance.rec_i3_v11_source_candidate(source))
                self.assertTrue(governance.rec_i3_v11_candidate(lifecycle))
                governance.validate_rec_i3_v11(lifecycle)
                context = lifecycle.github_pull_request_context
                for field, value in (
                    ("base_sha", governance.REC_I3_V11_BASE),
                    ("base_ref", "codex/stacked"),
                    ("head_ref", governance.REC_I3_V10_BRANCH),
                ):
                    with self.subTest(field=field):
                        self.assert_v11_rejected(replace(
                            lifecycle,
                            github_pull_request_context=replace(context, **{field: value}),
                        ))
                for label, merge_tree, parents in (
                    ("reversed parents", tree, (source, base)),
                    ("wrong tree", governance.git_output("rev-parse", f"{base}^{{tree}}"),
                     (base, source)),
                ):
                    bad_merge = governance.test_git_text(
                        repo,
                        "-c", "user.name=Dora Test",
                        "-c", "user.email=dora@example.invalid",
                        "commit-tree", merge_tree,
                        "-p", parents[0], "-p", parents[1],
                        input_data=b"invalid synthetic V11 PR merge\n",
                    )
                    governance.test_git(repo, "checkout", "-q", "--detach", bad_merge)
                    try:
                        with self.subTest(label=label), patch.dict(
                            os.environ, {"GITHUB_SHA": bad_merge}
                        ):
                            self.assertEqual(
                                bad_merge,
                                governance.git_output("rev-parse", "HEAD"),
                                "topology negative must execute from its constructed merge",
                            )
                            bad_lifecycle = replace(
                                lifecycle,
                                head=bad_merge,
                                github_pull_request_context=replace(
                                    context, merge_sha=bad_merge
                                ),
                            )
                            self.assert_v11_rejected(bad_lifecycle)
                    finally:
                        governance.test_git(repo, "checkout", "-q", "--detach", merge)

    def test_v11_dependency_entry_validates_profile_and_static_mutations(self) -> None:
        import verify_poc_recovery_dependency_inventory as inventory

        with self.v11_repository() as repo, ExitStack() as stack:
            original_root = inventory.ROOT
            for name, value in tuple(vars(inventory).items()):
                if isinstance(value, Path) and value.is_relative_to(original_root):
                    stack.enter_context(patch.object(
                        inventory, name, repo / value.relative_to(original_root)
                    ))
            stack.enter_context(patch.object(sys, "argv", ["inventory"]))
            with redirect_stdout(io.StringIO()) as output:
                self.assertEqual(0, inventory.main())
            self.assertIn("V11 SQLite OpenParams FULL correction", output.getvalue())
            self.assertIn("dependency/IP static validation passed", output.getvalue())
            documents = [inventory.read_json(path) for path in (
                inventory.INVENTORY_PATH,
                inventory.LICENSE_PATH,
                inventory.AUTHENTICITY_PATH,
                inventory.JSR305_EXCLUSION_PATH,
                inventory.READINESS_PATH,
                inventory.REVIEW_ROLES_PATH,
            )]
            for mutation in ("admission", "graph", "native", "signature", "hash"):
                records = copy.deepcopy(documents)
                if mutation == "admission":
                    records[0]["dependencyAdmission"] = True
                elif mutation == "graph":
                    records[0]["graphEdges"].pop()
                elif mutation == "native":
                    records[0]["artifacts"][0]["jar"]["nativeEntries"] = 1
                elif mutation == "signature":
                    records[2]["components"][0]["jar"]["detachedSignature"]["result"] = "INVALID"
                elif mutation == "hash":
                    records[0]["artifacts"][0]["jar"]["sha256"] = "0" * 64
                with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                    inventory.validate_static(*records)

    def test_v11_rejects_pinned_source_and_out_of_scope_drift(self) -> None:
        mutations = {
            "journal": V11_JOURNAL,
            "verifier": V11_SQLITE_VERIFIER,
            "plan": V11_PLAN,
            "instrumentation": V11_PREFLIGHT,
            "workflow": ".github/workflows/android-ci.yml",
            "extra": "unauthorized.txt",
        }
        for mutation, relative in mutations.items():
            with self.subTest(mutation=mutation), self.v11_repository() as repo:
                target = repo / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                with target.open("a", encoding="utf-8") as stream:
                    stream.write("\n// unauthorized V11 drift\n")
                governance.commit_test_git_repo(repo, mutation)
                self.assertFalse(governance.rec_i3_v11_source_candidate(
                    governance.git_output("rev-parse", "HEAD")
                ))
                self.assert_v11_rejected()

    def test_v10_genesis_fixture_uses_zero_predecessor_for_identity_and_row(self) -> None:
        fixture = governance.read_text(V10_CHECKPOINT_FIXTURE)
        identity_input = re.search(
            r"val input\s*=\s*RecoveryStreamingCheckpointIdentityInput\((.*?)\n\s*\)",
            fixture,
            re.DOTALL,
        )
        self.assertIsNotNone(identity_input, "shared checkpoint identity input missing")
        self.assertEqual(1, identity_input.group(1).count("Sha256Value.ZERO"))
        self.assertRegex(identity_input.group(1), r"Sha256Value\.ZERO,\s*$")
        self.assertNotIn("Sha256Value.calculate(ByteArray(32))", fixture)
        self.assertIn(
            "input.previousCheckpointSha256,\n"
            "            RecoveryStreamingIdentity.checkpoint(input),",
            fixture,
        )

    @contextmanager
    def v10_repository(self):
        """Exact V10 fixture correction above the immutable V8/V9 source base."""
        source = governance.ROOT
        with tempfile.TemporaryDirectory(prefix="dora-v10-governance-") as temporary:
            repo = Path(temporary) / "repo"
            git_pointer = source / ".git"
            git_directory = (
                source / git_pointer.read_text(encoding="utf-8").strip().removeprefix("gitdir: ")
            ).resolve() if git_pointer.is_file() else git_pointer.resolve()
            common_pointer = git_directory / "commondir"
            common_directory = (
                git_directory / common_pointer.read_text(encoding="utf-8").strip()
            ).resolve() if common_pointer.is_file() else git_directory
            repo.mkdir()
            governance.test_git(repo, "init", "-q")
            (repo / ".git/objects/info/alternates").write_text(
                (common_directory / "objects").as_posix() + "\n",
                encoding="utf-8",
                newline="\n",
            )
            governance.test_git(
                repo,
                "checkout",
                "-q",
                "-B",
                "codex/rec-i3-v10-genesis-fixture-fix",
                "6a33fc5d9560c163e34f840faf60ab9f86f2ad1b",
            )
            for relative in V10_CANDIDATE_PATHS:
                target = repo / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source / relative, target)
                if relative != V10_CHECKPOINT_FIXTURE:
                    with target.open("a", encoding="utf-8") as stream:
                        stream.write("\n<!-- V10 fixture -->\n" if target.suffix == ".md" else "\n# V10 fixture\n")
            governance.commit_test_git_repo(repo, "synthetic bounded V10 fixture candidate")
            with patch.object(governance, "ROOT", repo), patch.dict(os.environ, {
                key: "" for key in governance.REC_I3_V8_GITHUB_CONTEXT_KEYS
            }):
                yield repo

    def assert_v10_rejected(self, lifecycle=None) -> None:
        lifecycle = lifecycle or governance.collect_recovery_lifecycle_identity()
        self.assertFalse(governance.rec_i3_v10_candidate(lifecycle))
        with self.assertRaises((ValueError, subprocess.CalledProcessError)):
            governance.validate_rec_i3_v10(lifecycle)

    def test_v10_exact_cumulative_fixture_correction_is_admitted_and_pinned(self) -> None:
        self.assertTrue(
            callable(getattr(governance, "rec_i3_v10_source_candidate", None)),
            "V10 source candidate missing",
        )
        with self.v10_repository() as repo:
            lifecycle = governance.collect_recovery_lifecycle_identity()
            base = "6a33fc5d9560c163e34f840faf60ab9f86f2ad1b"
            self.assertEqual(set(V10_CANDIDATE_PATHS), set(governance.REC_I3_V10_PATHS))
            self.assertEqual(
                set(V10_CANDIDATE_PATHS),
                set(governance.git_path_records(
                    "diff", "--name-only", "--no-renames", "-z", base, lifecycle.head, "--"
                )),
            )
            self.assertEqual(
                [V10_CHECKPOINT_FIXTURE],
                governance.git_path_records(
                    "diff", "--name-only", "--no-renames", "-z", base, lifecycle.head,
                    "--", "android",
                ),
            )
            self.assertEqual(
                governance.REC_I3_V10_FIXTURE_BLOB,
                governance.git_output("rev-parse", f"HEAD:{V10_CHECKPOINT_FIXTURE}"),
            )
            self.assertEqual(
                governance.git_output("rev-parse", f"{base}:.github"),
                governance.git_output("rev-parse", "HEAD:.github"),
            )
            self.assertTrue(governance.rec_i3_v10_source_candidate(lifecycle.head))
            self.assertTrue(governance.rec_i3_v10_candidate(lifecycle))
            governance.validate_rec_i3_v10(lifecycle)
            with patch.object(sys, "argv", ["governance"]), redirect_stdout(io.StringIO()) as output:
                self.assertEqual(0, governance.main())
            self.assertIn("V10 genesis fixture correction", output.getvalue())

    def test_v10_pull_request_binds_current_main_and_rejects_identity_topology_drift(self) -> None:
        with self.v10_repository() as repo:
            source = governance.git_output("rev-parse", "HEAD")
            tree = governance.git_output("rev-parse", "HEAD^{tree}")
            base = "55940df0c95e919a00708ae57e1b8aa23d89b6de"
            merge = governance.test_git_text(
                repo,
                "-c", "user.name=Dora Test",
                "-c", "user.email=dora@example.invalid",
                "commit-tree", tree, "-p", base, "-p", source,
                input_data=b"synthetic V10 PR merge\n",
            )
            governance.test_git(repo, "checkout", "-q", "--detach", merge)
            event_path = repo.parent / "event.json"
            governance.write_test_pull_request_event(
                event_path,
                number=90,
                head_ref="codex/rec-i3-v10-genesis-fixture-fix",
                head_sha=source,
                base_sha=base,
                merge_sha=merge,
                draft=False,
            )
            environment = {
                "GITHUB_EVENT_NAME": "pull_request",
                "GITHUB_REPOSITORY": "Monumentogram/DORA",
                "GITHUB_WORKSPACE": str(repo),
                "GITHUB_REF": "refs/pull/90/merge",
                "GITHUB_SHA": merge,
                "GITHUB_HEAD_REF": "codex/rec-i3-v10-genesis-fixture-fix",
                "GITHUB_BASE_REF": "main",
                "RUNNER_TEMP": str(repo.parent),
                "GITHUB_EVENT_PATH": str(event_path),
            }
            with patch.dict(os.environ, environment):
                lifecycle = governance.collect_recovery_lifecycle_identity()
                self.assertTrue(governance.rec_i3_v10_source_candidate(source))
                self.assertTrue(governance.rec_i3_v10_candidate(lifecycle))
                governance.validate_rec_i3_v10(lifecycle)
                context = lifecycle.github_pull_request_context
                for field, value in (
                    ("base_sha", governance.REC_I3_V10_BASE),
                    ("base_ref", "codex/stacked"),
                    ("head_ref", governance.REC_I3_V8_BRANCH),
                ):
                    with self.subTest(field=field):
                        self.assert_v10_rejected(replace(
                            lifecycle,
                            github_pull_request_context=replace(context, **{field: value}),
                        ))
                for label, merge_tree, parents in (
                    ("reversed parents", tree, (source, base)),
                    ("wrong tree", governance.git_output("rev-parse", f"{base}^{{tree}}"),
                     (base, source)),
                ):
                    bad_merge = governance.test_git_text(
                        repo,
                        "-c", "user.name=Dora Test",
                        "-c", "user.email=dora@example.invalid",
                        "commit-tree", merge_tree,
                        "-p", parents[0], "-p", parents[1],
                        input_data=b"invalid synthetic V10 PR merge\n",
                    )
                    governance.test_git(repo, "checkout", "-q", "--detach", bad_merge)
                    try:
                        bad_lifecycle = replace(
                            lifecycle,
                            head=bad_merge,
                            github_pull_request_context=replace(context, merge_sha=bad_merge),
                        )
                        with self.subTest(label=label), patch.dict(
                            os.environ, {"GITHUB_SHA": bad_merge}
                        ):
                            self.assertEqual(
                                bad_merge,
                                governance.git_output("rev-parse", "HEAD"),
                                "topology negative must execute from its constructed merge",
                            )
                            self.assertFalse(governance.rec_i3_v10_candidate(bad_lifecycle))
                            with self.assertRaisesRegex(
                                ValueError, "REC-I3 V10 pull_request identity drift"
                            ):
                                governance.validate_rec_i3_v10(bad_lifecycle)
                    finally:
                        governance.test_git(repo, "checkout", "-q", "--detach", merge)

    def test_v10_dependency_entry_validates_profile_and_static_mutations(self) -> None:
        import verify_poc_recovery_dependency_inventory as inventory

        with self.v10_repository() as repo, ExitStack() as stack:
            original_root = inventory.ROOT
            for name, value in tuple(vars(inventory).items()):
                if isinstance(value, Path) and value.is_relative_to(original_root):
                    stack.enter_context(patch.object(
                        inventory, name, repo / value.relative_to(original_root)
                    ))
            stack.enter_context(patch.object(sys, "argv", ["inventory"]))
            with redirect_stdout(io.StringIO()) as output:
                self.assertEqual(0, inventory.main())
            self.assertIn("V10 genesis fixture correction", output.getvalue())
            self.assertIn("dependency/IP static validation passed", output.getvalue())
            documents = [inventory.read_json(path) for path in (
                inventory.INVENTORY_PATH,
                inventory.LICENSE_PATH,
                inventory.AUTHENTICITY_PATH,
                inventory.JSR305_EXCLUSION_PATH,
                inventory.READINESS_PATH,
                inventory.REVIEW_ROLES_PATH,
            )]
            for mutation in ("admission", "graph", "native", "signature", "hash"):
                records = copy.deepcopy(documents)
                if mutation == "admission":
                    records[0]["dependencyAdmission"] = True
                elif mutation == "graph":
                    records[0]["graphEdges"].pop()
                elif mutation == "native":
                    records[0]["artifacts"][0]["jar"]["nativeEntries"] = 1
                elif mutation == "signature":
                    records[2]["components"][0]["jar"]["detachedSignature"]["result"] = "INVALID"
                elif mutation == "hash":
                    records[0]["artifacts"][0]["jar"]["sha256"] = "0" * 64
                with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                    inventory.validate_static(*records)

    def test_v10_rejects_fixture_and_out_of_scope_drift(self) -> None:
        mutations = {
            "fixture": V10_CHECKPOINT_FIXTURE,
            "production": (
                "android/poc/recovery/src/main/kotlin/com/monumentogram/dora/poc/recovery/"
                "journal/AndroidRecoveryStreamingJournal.kt"
            ),
            "workflow": ".github/workflows/android-ci.yml",
            "extra": "unauthorized.txt",
        }
        for mutation, relative in mutations.items():
            with self.subTest(mutation=mutation), self.v10_repository() as repo:
                target = repo / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                with target.open("a", encoding="utf-8") as stream:
                    stream.write("\n// unauthorized V10 drift\n")
                governance.commit_test_git_repo(repo, mutation)
                self.assertFalse(governance.rec_i3_v10_source_candidate(
                    governance.git_output("rev-parse", "HEAD")
                ))
                self.assert_v10_rejected()

    @contextmanager
    def v8_repository(self):
        """Real baseline objects, eight tooling files and the exact host-test repair."""
        source = governance.ROOT
        paths = (
            "tools/run_rec_i3_v8.ps1",
            "tools/test_run_rec_i3_v8.py",
            "tools/rec_i3_preserve_and_cleanup.ps1",
            "tools/test_rec_i3_preserve_and_cleanup.py",
            "tools/rec_i3_owned_process.psm1",
            "tools/test_rec_i3_owned_process.ps1",
            "tools/validate_poc_recovery_governance.py",
            "tools/test_poc_recovery_i3_governance.py",
            "tools/verify_poc_recovery_dependency_inventory.py",
            "docs/superpowers/plans/2026-09-09-rec-i3-v8-host-run-contract-repair.md",
        )
        with tempfile.TemporaryDirectory(prefix="dora-v8-governance-") as temporary:
            repo = Path(temporary) / "repo"
            git_pointer = source / ".git"
            git_directory = (source / git_pointer.read_text(encoding="utf-8").strip().removeprefix("gitdir: ")).resolve() if git_pointer.is_file() else git_pointer.resolve()
            common_pointer = git_directory / "commondir"
            common_directory = (git_directory / common_pointer.read_text(encoding="utf-8").strip()).resolve() if common_pointer.is_file() else git_directory
            repo.mkdir()
            governance.test_git(repo, "init", "-q")
            # A fixture-local alternate reads exact accepted objects without upload-pack,
            # shared-worktree ownership exceptions, network, or source Git mutations.
            (repo / ".git/objects/info/alternates").write_text(
                (common_directory / "objects").as_posix() + "\n", encoding="utf-8", newline="\n")
            governance.test_git(repo, "checkout", "-q", "-B", "codex/rec-i3-v8-host-runner-fix",
                                "55940df0c95e919a00708ae57e1b8aa23d89b6de")
            for relative in paths:
                target = repo / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(source / relative, target)
                # A harmless fixture comment makes the exact delta independent of dirty/clean execution.
                with target.open("a", encoding="utf-8") as stream:
                    stream.write("\n<!-- V8 fixture -->\n" if target.suffix == ".md" else "\n# V8 fixture\n")
            # The approved Android blob must remain exact, without the tooling fixture comments.
            shutil.copyfile(source / V8_REPAIRED_HOST_TEST, repo / V8_REPAIRED_HOST_TEST)
            governance.commit_test_git_repo(repo, "synthetic bounded V8 tooling candidate")
            with patch.object(governance, "ROOT", repo), patch.dict(os.environ, {
                key: "" for key in (
                    "GITHUB_EVENT_NAME", "GITHUB_HEAD_REF", "GITHUB_BASE_REF", "GITHUB_REF",
                    "GITHUB_SHA", "GITHUB_REPOSITORY", "GITHUB_WORKSPACE", "GITHUB_ACTIONS",
                    "GITHUB_EVENT_PATH",
                )
            }):
                yield repo

    def assert_v8_rejected(self, lifecycle=None) -> None:
        lifecycle = lifecycle or governance.collect_recovery_lifecycle_identity()
        self.assertFalse(governance.rec_i3_v8_candidate(lifecycle))
        with self.assertRaises((ValueError, subprocess.CalledProcessError)):
            governance.validate_rec_i3_v8(lifecycle)

    def test_v8_host_test_publication_repair_is_exact(self) -> None:
        # Admit the reviewed repair while proving all other Android and workflow bytes stay baseline.
        with self.v8_repository() as repo:
            lifecycle = governance.collect_recovery_lifecycle_identity()
            base = "55940df0c95e919a00708ae57e1b8aa23d89b6de"
            self.assertEqual([V8_REPAIRED_HOST_TEST], governance.git_path_records(
                "diff", "--name-only", "--no-renames", "-z", base, lifecycle.head, "--", "android"))
            self.assertEqual(governance.git_output("rev-parse", f"{base}:.github"),
                             governance.git_output("rev-parse", "HEAD:.github"))
            self.assertNotEqual(governance.git_output("rev-parse", f"{base}:{V8_REPAIRED_HOST_TEST}"),
                                governance.git_output("rev-parse", f"HEAD:{V8_REPAIRED_HOST_TEST}"))
            self.assertTrue(governance.rec_i3_v8_source_candidate(lifecycle.head))
            self.assertTrue(governance.rec_i3_v8_candidate(lifecycle))
            governance.validate_rec_i3_v8(lifecycle)
            self.assertEqual(9, len(governance.REC_I3_V8_PATHS))
            self.assertEqual(governance.REC_I3_V8_ANDROID_TREE,
                             governance.git_output("rev-parse", "HEAD:android"))
            self.assertEqual(governance.REC_I3_V8_HOST_TEST_BLOB,
                             governance.git_output("rev-parse", f"HEAD:{V8_REPAIRED_HOST_TEST}"))

    def test_v8_rejects_unapproved_android_and_workflow_blobs(self) -> None:
        # A path exception must not admit the old race, another repair, or any other Android change.
        mutations = {
            "baseline-host-test": V8_REPAIRED_HOST_TEST,
            "changed-repaired-host-test": V8_REPAIRED_HOST_TEST,
            "other-host-test": "android/poc/recovery/src/test/kotlin/com/monumentogram/dora/"
                               "poc/recovery/storage/RecoveryCandidatePathPolicyTest.kt",
            "runtime": "android/poc/recovery/src/main/kotlin/com/monumentogram/dora/"
                       "poc/recovery/storage/AndroidOsRecoveryStreamingSource.kt",
            "instrumentation": "android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/"
                               "poc/recovery/candidate/RecoveryE36GapiPreflightInstrumentedTest.kt",
            "shared-test": "android/poc/recovery/src/sharedTest/kotlin/com/monumentogram/dora/"
                           "poc/recovery/candidate/RecoveryE36GapiDeviceIdentityGuard.kt",
            "workflow": ".github/workflows/android-ci.yml",
        }
        for mutation, relative in mutations.items():
            with self.subTest(mutation=mutation), self.v8_repository() as repo:
                target = repo / relative
                if mutation == "baseline-host-test":
                    target.write_bytes(governance.git_blob_bytes(
                        f"55940df0c95e919a00708ae57e1b8aa23d89b6de:{relative}"))
                else:
                    target.write_bytes(target.read_bytes() + b"\n// unauthorized blob mutation\n")
                governance.commit_test_git_repo(repo, mutation)
                lifecycle = governance.collect_recovery_lifecycle_identity()
                self.assertFalse(governance.rec_i3_v8_source_candidate(lifecycle.head))
                self.assert_v8_rejected(lifecycle)

    def test_v8_detached_host_run_contract(self) -> None:
        # Omitting V8 dispatch or accepting detached only in candidate selection breaks this test.
        self.assertTrue(callable(getattr(governance, "validate_rec_i3_v8", None)), "V8 profile missing")
        with self.v8_repository() as repo:
            for branch in ("codex/rec-i3-v8-host-runner-fix", "main", ""):
                with self.subTest(branch=branch):
                    governance.test_git(repo, "checkout", "-q", *( ["-B", branch] if branch else ["--detach"] ))
                    lifecycle = governance.collect_recovery_lifecycle_identity()
                    self.assertEqual(branch, lifecycle.branch)
                    self.assertTrue(governance.rec_i3_v8_source_candidate(lifecycle.head))
                    self.assertTrue(governance.rec_i3_v8_candidate(lifecycle))
                    governance.validate_rec_i3_v8(lifecycle)
                    with patch.object(sys, "argv", ["governance"]), redirect_stdout(io.StringIO()) as output:
                        self.assertEqual(0, governance.main())
                    self.assertIn("V8 bounded tooling profile", output.getvalue())

    def test_v8_serial_routing_rejects_agp_serial_reintroduction(self) -> None:
        # Reintroducing the defective AGP --serial pair must fail the real V8 validator.
        with self.v8_repository() as repo:
            lifecycle = governance.collect_recovery_lifecycle_identity()
            governance.validate_rec_i3_v8(lifecycle)
            runner = repo / "tools/run_rec_i3_v8.ps1"
            repaired = runner.read_text(encoding="utf-8")
            marker = '        ":poc:recovery:connectedDebugAndroidTest",\n'
            self.assertEqual(1, repaired.count(marker))
            runner.write_text(
                repaired.replace(marker, marker + '        "--serial", $Serial,\n'),
                encoding="utf-8",
            )
            governance.commit_test_git_repo(repo, "reintroduce defective AGP serial route")
            mutated = governance.collect_recovery_lifecycle_identity()
            self.assertTrue(governance.rec_i3_v8_candidate(mutated))
            with self.assertRaisesRegex(ValueError, "AGP --serial option"):
                governance.validate_rec_i3_v8(mutated)

    def test_v8_rejects_wrong_branch_and_each_nonlocal_detached_context(self) -> None:
        # A source-only bypass must not authorize an arbitrary branch or partial CI identity.
        self.assertTrue(callable(getattr(governance, "rec_i3_v8_candidate", None)), "V8 candidate missing")
        with self.v8_repository() as repo:
            governance.test_git(repo, "checkout", "-q", "-B", "codex/unrelated")
            self.assert_v8_rejected()
            governance.test_git(repo, "checkout", "-q", "--detach")
            lifecycle = governance.collect_recovery_lifecycle_identity()
            for key in ("GITHUB_EVENT_NAME", "GITHUB_HEAD_REF", "GITHUB_BASE_REF", "GITHUB_REF",
                        "GITHUB_SHA", "GITHUB_REPOSITORY", "GITHUB_WORKSPACE", "GITHUB_ACTIONS"):
                with self.subTest(key=key), patch.dict(os.environ, {key: "non-local"}):
                    self.assert_v8_rejected(lifecycle)
            with patch.dict(os.environ, {"GITHUB_TOKEN": "synthetic-unused"}):
                self.assertTrue(governance.rec_i3_v8_candidate(lifecycle))

    def test_v8_rejects_dirty_staged_untracked_and_spoofed_lifecycle(self) -> None:
        # Clean commit identity cannot hide an index/worktree mutation or spoofed lifecycle head.
        self.assertTrue(callable(getattr(governance, "rec_i3_v8_candidate", None)), "V8 candidate missing")
        with self.v8_repository() as repo:
            lifecycle = governance.collect_recovery_lifecycle_identity()
            self.assert_v8_rejected(replace(lifecycle, head="55940df0c95e919a00708ae57e1b8aa23d89b6de"))
            target = repo / "tools/run_rec_i3_v8.ps1"
            original = target.read_bytes()
            target.write_bytes(original + b"\n# dirty\n")
            self.assert_v8_rejected(lifecycle)
            governance.test_git(repo, "add", "tools/run_rec_i3_v8.ps1")
            self.assert_v8_rejected(lifecycle)
            target.write_bytes(original)
            governance.test_git(repo, "add", "tools/run_rec_i3_v8.ps1")
            (repo / "untracked-source.py").write_text("# unauthorized\n", encoding="utf-8")
            self.assert_v8_rejected(lifecycle)

    def test_v8_rejects_extra_missing_mode_protected_and_history_paths(self) -> None:
        # Exact path/mode and cumulative history restrictions must hold in both entry layers.
        self.assertTrue(callable(getattr(governance, "rec_i3_v8_candidate", None)), "V8 candidate missing")
        for mutation in ("extra", "missing", "mode", "production", "instrumentation", "dependency", "workflow", "reverted"):
            with self.subTest(mutation=mutation), self.v8_repository() as repo:
                target = "tools/run_rec_i3_v8.ps1"
                if mutation == "missing":
                    (repo / target).unlink()
                elif mutation == "mode":
                    governance.test_git(repo, "update-index", "--chmod=+x", target)
                    governance.test_git(repo, "-c", "user.name=Dora Test", "-c", "user.email=dora@example.invalid",
                                        "commit", "-q", "-m", "mode mutation")
                else:
                    target = {
                        "production": "android/poc/recovery/src/main/injected.kt",
                        "instrumentation": "android/poc/recovery/src/androidTest/injected.kt",
                        "dependency": "android/poc/recovery/gradle.lockfile",
                        "workflow": ".github/workflows/android-ci.yml",
                    }.get(mutation, "unauthorized.txt")
                    with (repo / target).open("a", encoding="utf-8") as stream:
                        stream.write("\n# unauthorized mutation\n")
                if mutation != "mode":
                    governance.commit_test_git_repo(repo, "out-of-scope mutation")
                if mutation == "reverted":
                    (repo / target).unlink()
                    governance.commit_test_git_repo(repo, "revert unauthorized path")
                lifecycle = governance.collect_recovery_lifecycle_identity()
                self.assertFalse(governance.rec_i3_v8_source_candidate(lifecycle.head))
                self.assert_v8_rejected(lifecycle)

    def test_v8_rejects_wrong_ancestry_and_base_identity(self) -> None:
        # Matching payload paths alone cannot replace the accepted base commit/tree/parent.
        self.assertTrue(callable(getattr(governance, "rec_i3_v8_candidate", None)), "V8 candidate missing")
        with self.v8_repository() as repo:
            lifecycle = governance.collect_recovery_lifecycle_identity()
            for name in ("REC_I3_V8_BASE_TREE", "REC_I3_V8_BASE_PARENT", "REC_I3_V8_BASE"):
                with self.subTest(name=name), patch.object(governance, name, "0" * 40):
                    self.assertFalse(governance.rec_i3_v8_source_candidate(lifecycle.head))
                    self.assert_v8_rejected(lifecycle)
            orphan = governance.test_git_text(repo, "-c", "user.name=Dora Test", "-c",
                "user.email=dora@example.invalid", "commit-tree",
                governance.git_output("rev-parse", "HEAD^{tree}"), input_data=b"unrelated root\n")
            governance.test_git(repo, "checkout", "-q", "--detach", orphan)
            self.assert_v8_rejected()

    def test_v8_main_github_context_is_bound(self) -> None:
        # Missing or mismatched push/workflow context must fail even on exact integrated main.
        self.assertTrue(callable(getattr(governance, "rec_i3_v8_candidate", None)), "V8 candidate missing")
        with self.v8_repository() as repo:
            governance.test_git(repo, "checkout", "-q", "-B", "main")
            lifecycle = governance.collect_recovery_lifecycle_identity()
            environment = {"GITHUB_EVENT_NAME": "push", "GITHUB_REPOSITORY": "Monumentogram/DORA",
                           "GITHUB_WORKSPACE": str(repo), "GITHUB_REF": "refs/heads/main",
                           "GITHUB_SHA": lifecycle.head, "GITHUB_ACTIONS": "true"}
            with patch.dict(os.environ, environment):
                for event in ("push", "workflow_dispatch"):
                    with patch.dict(os.environ, {"GITHUB_EVENT_NAME": event}):
                        self.assertTrue(governance.rec_i3_v8_candidate(lifecycle))
                        governance.validate_rec_i3_v8(lifecycle)
                for key in ("GITHUB_EVENT_NAME", "GITHUB_REPOSITORY", "GITHUB_WORKSPACE", "GITHUB_REF", "GITHUB_SHA"):
                    for value in ("", "wrong"):
                        with self.subTest(key=key, value=value), patch.dict(os.environ, {key: value}):
                            self.assert_v8_rejected(lifecycle)

    def test_v8_pull_request_context_and_merge_topology_are_bound(self) -> None:
        # A verified same-repository PR still needs exact state, base, source tree and merge parents.
        self.assertTrue(callable(getattr(governance, "rec_i3_v8_candidate", None)), "V8 candidate missing")
        with self.v8_repository() as repo:
            source = governance.git_output("rev-parse", "HEAD")
            tree = governance.git_output("rev-parse", "HEAD^{tree}")
            base = "55940df0c95e919a00708ae57e1b8aa23d89b6de"
            merge = governance.test_git_text(repo, "-c", "user.name=Dora Test", "-c", "user.email=dora@example.invalid",
                "commit-tree", tree, "-p", base, "-p", source, input_data=b"synthetic PR merge\n")
            governance.test_git(repo, "checkout", "-q", "--detach", merge)
            event_path = repo.parent / "event.json"
            governance.write_test_pull_request_event(event_path, number=88, head_ref="codex/rec-i3-v8-host-runner-fix",
                head_sha=source, base_sha=base, merge_sha=merge, draft=False)
            environment = {"GITHUB_EVENT_NAME": "pull_request", "GITHUB_REPOSITORY": "Monumentogram/DORA",
                "GITHUB_WORKSPACE": str(repo), "GITHUB_REF": "refs/pull/88/merge", "GITHUB_SHA": merge,
                "GITHUB_HEAD_REF": "codex/rec-i3-v8-host-runner-fix", "GITHUB_BASE_REF": "main",
                "RUNNER_TEMP": str(repo.parent), "GITHUB_EVENT_PATH": str(event_path)}
            with patch.dict(os.environ, environment):
                lifecycle = governance.collect_recovery_lifecycle_identity()
                self.assertTrue(governance.rec_i3_v8_candidate(lifecycle))
                governance.validate_rec_i3_v8(lifecycle)
                context = lifecycle.github_pull_request_context
                for field, value in (("repository", "fork/DORA"), ("head_repository", "fork/DORA"),
                    ("head_ref", "codex/other"), ("base_ref", "other"), ("base_sha", source),
                    ("draft", True), ("state", "closed"), ("merged", True), ("merge_sha", source),
                    ("head_sha", base), ("merge_ref", "refs/pull/89/merge")):
                    with self.subTest(field=field):
                        self.assert_v8_rejected(replace(lifecycle, github_pull_request_context=replace(context, **{field: value})))
                with patch.dict(os.environ, {"GITHUB_SHA": source}):
                    self.assert_v8_rejected(lifecycle)
                for label, merge_tree, parents in (
                    ("reversed parents", tree, (source, base)),
                    ("wrong tree", governance.git_output("rev-parse", f"{base}^{{tree}}"), (base, source)),
                ):
                    bad_merge = governance.test_git_text(repo, "-c", "user.name=Dora Test", "-c",
                        "user.email=dora@example.invalid", "commit-tree", merge_tree,
                        "-p", parents[0], "-p", parents[1], input_data=b"invalid synthetic merge\n")
                    governance.test_git(repo, "checkout", "-q", "--detach", bad_merge)
                    bad_context = replace(context, merge_sha=bad_merge)
                    with self.subTest(label=label), patch.dict(os.environ, {"GITHUB_SHA": bad_merge}):
                        self.assert_v8_rejected(replace(lifecycle, head=bad_merge, github_pull_request_context=bad_context))

    def test_v8_dependency_dispatch_retains_static_assertions(self) -> None:
        # V8 dispatch must validate the real packet and reject admission/native/graph/signature drift.
        self.assertTrue(callable(getattr(governance, "rec_i3_v8_candidate", None)), "V8 candidate missing")
        import verify_poc_recovery_dependency_inventory as inventory
        with self.v8_repository() as repo, ExitStack() as stack:
            original_root = inventory.ROOT
            for name, value in tuple(vars(inventory).items()):
                if isinstance(value, Path) and value.is_relative_to(original_root):
                    stack.enter_context(patch.object(inventory, name, repo / value.relative_to(original_root)))
            stack.enter_context(patch.object(sys, "argv", ["inventory"]))
            with redirect_stdout(io.StringIO()) as output:
                self.assertEqual(0, inventory.main())
            self.assertIn("V8 bounded tooling profile", output.getvalue())
            self.assertIn("no dependency or production admission", output.getvalue())
            documents = [inventory.read_json(path) for path in (inventory.INVENTORY_PATH, inventory.LICENSE_PATH,
                inventory.AUTHENTICITY_PATH, inventory.JSR305_EXCLUSION_PATH, inventory.READINESS_PATH, inventory.REVIEW_ROLES_PATH)]
            for mutation in ("admission", "graph", "native", "signature", "hash", "jsr305"):
                records = copy.deepcopy(documents)
                if mutation == "admission": records[0]["dependencyAdmission"] = True
                if mutation == "graph": records[0]["graphEdges"].pop()
                if mutation == "native": records[0]["artifacts"][0]["jar"]["nativeEntries"] = 1
                if mutation == "signature": records[2]["components"][0]["jar"]["detachedSignature"]["result"] = "INVALID"
                if mutation == "hash": records[0]["artifacts"][0]["jar"]["sha256"] = "0" * 64
                if mutation == "jsr305": records[3]["prospectivePolicy"]["requiredResolvedComponentCount"] = 1
                with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                    inventory.validate_static(*records)

    def test_v8_preserves_v7_candidate_and_validator_dispatch(self) -> None:
        # Adding V8 must leave the accepted historical V7 profile reachable and valid.
        import verify_poc_recovery_dependency_inventory as inventory
        with self.v8_repository() as repo:
            governance.test_git(repo, "checkout", "-q", "-B", "main",
                                "55940df0c95e919a00708ae57e1b8aa23d89b6de")
            lifecycle = governance.collect_recovery_lifecycle_identity()
            self.assertFalse(governance.rec_i3_v8_candidate(lifecycle))
            self.assertTrue(governance.rec_i3_v7_candidate(lifecycle))
            governance.validate_rec_i3_v7(lifecycle)
            with patch.object(sys, "argv", ["governance"]), redirect_stdout(io.StringIO()) as output:
                self.assertEqual(0, governance.main())
            self.assertIn("V7 bootstrap/preservation repair profile", output.getvalue())
            with ExitStack() as stack:
                original_root = inventory.ROOT
                for name, value in tuple(vars(inventory).items()):
                    if isinstance(value, Path) and value.is_relative_to(original_root):
                        stack.enter_context(patch.object(inventory, name, repo / value.relative_to(original_root)))
                stack.enter_context(patch.object(sys, "argv", ["inventory"]))
                with redirect_stdout(io.StringIO()) as output:
                    self.assertEqual(0, inventory.main())
                self.assertIn("V7 dependency/IP static validation passed", output.getvalue())

    @unittest.skipUnless(os.name == "nt", "PowerShell/robocopy host tools are Windows-only")
    def test_v8_host_tools_execute_regressions(self) -> None:
        # Execute isolated fake-tool suites; runner fixture governance is a stub, avoiding self-test recursion.
        for script in ("test_rec_i3_preserve_and_cleanup.py", "test_run_rec_i3_v8.py"):
            completed = subprocess.run([sys.executable, str(governance.ROOT / "tools" / script), "-v"],
                cwd=governance.ROOT, capture_output=True, text=True, timeout=1800)
            print(completed.stdout, end="")
            print(completed.stderr, end="")
            self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)

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
        self.assertIn(
            "orphan checkpoint rejected; bootstrap parent accepted",
            completed.stdout,
        )

    def test_v7_preflight_bootstraps_before_checkpoint_and_cleans_parent(self) -> None:
        preflight = (
            governance.ROOT
            / "android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/"
            "candidate/RecoveryE36GapiPreflightInstrumentedTest.kt"
        ).read_text(encoding="utf-8")
        bootstrap = "RecoveryCheckpointAndroidTestFixture.bootstrap(context, runId)"
        insert = "val checkpointInsert = journal.insertCheckpoint(checkpoint)"
        diagnostic = 'println("INSTRUMENTATION_CHECKPOINT_INSERT $checkpointInsertDiagnostic")'
        receipt = "assertTrue(checkpointInsert is RecoveryStreamingJournalResult.CheckpointReceipt)"
        self.assertEqual(1, preflight.count("@Test"))
        self.assertLess(preflight.index(bootstrap), preflight.index(insert))
        self.assertLess(preflight.index(diagnostic), preflight.index(receipt))
        self.assertNotIn("isCheckpointReceipt", preflight)

        fixture = (
            governance.ROOT
            / "android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/"
            "candidate/RecoveryCheckpointAndroidTestFixture.kt"
        ).read_text(encoding="utf-8")
        self.assertIn("AndroidRecoveryKeyBootstrap", fixture)
        self.assertIn("KeyConfirmationValue(RecoveryCandidate.STREAM, runId)", fixture)
        deletes = [
            'database.delete("recovery_stream_range_quarantine_v4"',
            'database.delete("recovery_stream_outcome_v4"',
            'database.delete("recovery_stream_checkpoint_v4"',
            'database.delete("recovery_run_bootstrap_v1"',
        ]
        positions = [fixture.index(value) for value in deletes]
        self.assertEqual(sorted(positions), positions)

    @unittest.skipUnless(os.name == "nt", "PowerShell/robocopy regression is Windows-only")
    def test_v7_long_path_preservation_and_independent_cleanup(self) -> None:
        test_script = governance.ROOT / "tools/test_rec_i3_preserve_and_cleanup.py"
        completed = subprocess.run(
            [sys.executable, str(test_script), "-v"],
            cwd=governance.ROOT,
            check=False,
            capture_output=True,
            text=True,
        )
        self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)

    def test_v7_dependency_inventory_dispatches_through_the_v7_profile(self) -> None:
        inventory = governance.ROOT / "tools/verify_poc_recovery_dependency_inventory.py"
        source = inventory.read_text(encoding="utf-8")
        self.assertIn("if rec_i3_v7_candidate(lifecycle):", source)
        self.assertIn("validate_rec_i3_v7(lifecycle)", source)
        self.assertIn(
            "tools/verify_poc_recovery_dependency_inventory.py",
            governance.REC_I3_V7_PATHS,
        )

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
        lifecycle = replace(
            governance.collect_recovery_lifecycle_identity(),
            branch=governance.REC_I3_STREAMING_PERSISTENCE_BRANCH,
        )
        with (
            patch.object(governance, "validate_rec_i3_bootstrap_evidence") as validator,
            patch.object(governance, "validate_rec_i3_microfile_successor"),
            patch.object(governance, "validate_rec_i3_streaming_persistence"),
        ):
            self.assertTrue(governance.validate_current_rec_i3_successor(lifecycle))
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
            governance.commit_test_git_repo(repo, "add regular source")
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
    RETAINED_VALIDATOR_TIMEOUT_SECONDS = 180.0
    WINDOWS_CREATE_SUSPENDED = 0x00000004
    INTEGRATED_CORRECTION_HEAD: str | None = None

    @classmethod
    def integrated_correction_head(cls) -> str:
        if cls.INTEGRATED_CORRECTION_HEAD is not None:
            return cls.INTEGRATED_CORRECTION_HEAD
        correction_root = Path(governance.__file__).resolve().parents[1]
        return subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=correction_root,
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()

    def create_windows_validator_job(self) -> int:
        from ctypes import wintypes

        class JobObjectBasicLimitInformation(ctypes.Structure):
            _fields_ = [
                ("per_process_user_time_limit", ctypes.c_longlong),
                ("per_job_user_time_limit", ctypes.c_longlong),
                ("limit_flags", wintypes.DWORD),
                ("minimum_working_set_size", ctypes.c_size_t),
                ("maximum_working_set_size", ctypes.c_size_t),
                ("active_process_limit", wintypes.DWORD),
                ("affinity", ctypes.c_size_t),
                ("priority_class", wintypes.DWORD),
                ("scheduling_class", wintypes.DWORD),
            ]

        class IoCounters(ctypes.Structure):
            _fields_ = [
                ("read_operation_count", ctypes.c_ulonglong),
                ("write_operation_count", ctypes.c_ulonglong),
                ("other_operation_count", ctypes.c_ulonglong),
                ("read_transfer_count", ctypes.c_ulonglong),
                ("write_transfer_count", ctypes.c_ulonglong),
                ("other_transfer_count", ctypes.c_ulonglong),
            ]

        class JobObjectExtendedLimitInformation(ctypes.Structure):
            _fields_ = [
                ("basic_limit_information", JobObjectBasicLimitInformation),
                ("io_info", IoCounters),
                ("process_memory_limit", ctypes.c_size_t),
                ("job_memory_limit", ctypes.c_size_t),
                ("peak_process_memory_used", ctypes.c_size_t),
                ("peak_job_memory_used", ctypes.c_size_t),
            ]

        kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
        kernel32.CreateJobObjectW.argtypes = [ctypes.c_void_p, wintypes.LPCWSTR]
        kernel32.CreateJobObjectW.restype = wintypes.HANDLE
        kernel32.SetInformationJobObject.argtypes = [
            wintypes.HANDLE,
            ctypes.c_int,
            ctypes.c_void_p,
            wintypes.DWORD,
        ]
        kernel32.SetInformationJobObject.restype = wintypes.BOOL
        kernel32.CloseHandle.argtypes = [wintypes.HANDLE]
        kernel32.CloseHandle.restype = wintypes.BOOL

        job = kernel32.CreateJobObjectW(None, None)
        if not job:
            raise ctypes.WinError(ctypes.get_last_error())
        limits = JobObjectExtendedLimitInformation()
        limits.basic_limit_information.limit_flags = 0x00002000
        if not kernel32.SetInformationJobObject(
            job,
            9,
            ctypes.byref(limits),
            ctypes.sizeof(limits),
        ):
            error = ctypes.get_last_error()
            kernel32.CloseHandle(job)
            raise ctypes.WinError(error)
        return int(job)

    def assign_and_resume_windows_validator_child(
        self, job: int, process: subprocess.Popen[str]
    ) -> None:
        from ctypes import wintypes

        kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
        kernel32.AssignProcessToJobObject.argtypes = [wintypes.HANDLE, wintypes.HANDLE]
        kernel32.AssignProcessToJobObject.restype = wintypes.BOOL
        process_handle = wintypes.HANDLE(int(process._handle))
        if not kernel32.AssignProcessToJobObject(wintypes.HANDLE(job), process_handle):
            raise ctypes.WinError(ctypes.get_last_error())
        ntdll = ctypes.WinDLL("ntdll")
        ntdll.NtResumeProcess.argtypes = [wintypes.HANDLE]
        ntdll.NtResumeProcess.restype = wintypes.LONG
        status = ntdll.NtResumeProcess(process_handle)
        if status != 0:
            raise OSError(f"NtResumeProcess failed with NTSTATUS 0x{status & 0xffffffff:08x}")

    def close_windows_validator_job(self, job: int, *, terminate: bool) -> None:
        from ctypes import wintypes

        kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
        if terminate:
            kernel32.TerminateJobObject.argtypes = [wintypes.HANDLE, wintypes.UINT]
            kernel32.TerminateJobObject.restype = wintypes.BOOL
            if not kernel32.TerminateJobObject(wintypes.HANDLE(job), 1):
                raise ctypes.WinError(ctypes.get_last_error())
        kernel32.CloseHandle.argtypes = [wintypes.HANDLE]
        kernel32.CloseHandle.restype = wintypes.BOOL
        if not kernel32.CloseHandle(wintypes.HANDLE(job)):
            raise ctypes.WinError(ctypes.get_last_error())

    def terminate_validator_child_tree(
        self,
        process: subprocess.Popen[str],
        windows_job: int | None,
    ) -> tuple[str, str]:
        if os.name == "nt":
            if windows_job is not None:
                self.close_windows_validator_job(windows_job, terminate=True)
        else:
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
        if process.poll() is None:
            process.kill()
        try:
            return process.communicate(timeout=10)
        except subprocess.TimeoutExpired:
            process.kill()
            return process.communicate()

    def run_bounded_validator_child(
        self,
        *,
        cwd: Path,
        environment: dict[str, str],
        timeout_seconds: float = RETAINED_VALIDATOR_TIMEOUT_SECONDS,
        command: list[str] | None = None,
    ) -> subprocess.CompletedProcess[str]:
        child_command = command or [
            sys.executable,
            "tools/validate_poc_recovery_governance.py",
            "--self-test",
        ]
        windows_job = self.create_windows_validator_job() if os.name == "nt" else None
        try:
            process = subprocess.Popen(
                child_command,
                cwd=cwd,
                env=environment,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True,
                encoding="utf-8",
                errors="strict",
                start_new_session=os.name != "nt",
                creationflags=(
                    subprocess.CREATE_NEW_PROCESS_GROUP | self.WINDOWS_CREATE_SUSPENDED
                    if os.name == "nt"
                    else 0
                ),
            )
        except BaseException:
            if windows_job is not None:
                self.close_windows_validator_job(windows_job, terminate=True)
            raise
        if windows_job is not None:
            try:
                self.assign_and_resume_windows_validator_child(windows_job, process)
            except BaseException:
                self.terminate_validator_child_tree(process, windows_job)
                raise
        try:
            stdout, stderr = process.communicate(timeout=timeout_seconds)
        except subprocess.TimeoutExpired:
            stdout, stderr = self.terminate_validator_child_tree(process, windows_job)
            windows_job = None
            self.fail(
                f"validator child timed out after {timeout_seconds:g} seconds\n"
                f"stdout:\n{stdout}\nstderr:\n{stderr}"
            )
        except BaseException:
            self.terminate_validator_child_tree(process, windows_job)
            raise
        if process.returncode != 0:
            self.terminate_validator_child_tree(process, windows_job)
            windows_job = None
        elif windows_job is not None:
            self.close_windows_validator_job(windows_job, terminate=False)
        return subprocess.CompletedProcess(
            child_command,
            process.returncode,
            stdout,
            stderr,
        )

    def fixture(self) -> tuple[dict, dict]:
        return (
            copy.deepcopy(governance.read_json(governance.REC_I3_RESULT_BOUNDARY_GATE_PATH)),
            copy.deepcopy(governance.read_json(governance.REC_I3_RESULT_BOUNDARY_PROTOCOL_PATH)),
        )

    def local_result_boundary_lifecycle(self) -> governance.RecoveryLifecycleIdentity:
        current = governance.collect_recovery_lifecycle_identity()
        source_head = (
            current.github_pull_request_context.head_sha
            if current.github_pull_request_context is not None
            else current.head
        )
        return replace(
            current,
            head=source_head,
            branch=governance.REC_I3_RESULT_BOUNDARY_BRANCH,
            github_pull_request_context=None,
        )

    def reject(self, mutation) -> None:
        gate, protocol = self.fixture()
        governance.validate_rec_i3_result_boundary_contract(gate, protocol)
        mutation(gate, protocol)
        with self.assertRaises(ValueError):
            governance.validate_rec_i3_result_boundary_contract(gate, protocol)

    def observable_controller_evidence(self) -> dict:
        return {
            "schemaVersion": 1,
            "scopeId": "rec-i3-streaming-observable-controller-stage0-v0.1",
            "taskId": "REC-I3",
            "branch": governance.REC_I3_OBSERVABLE_CONTROLLER_BRANCH,
            "baseCommit": governance.REC_I3_OBSERVABLE_CONTROLLER_BASE,
            "status": "LOCAL_VERIFIED",
            "contractCounts": {
                "publicResultVariants": 4,
                "stages": 6,
                "classifications": 20,
                "retrySafeExceptionTypes": 4,
            },
            "sourceFiles": {
                path: governance.canonical_lf_sha256(path)
                for path in governance.REC_I3_OBSERVABLE_CONTROLLER_SOURCE_PATHS
            },
            "checks": [
                {"stage": stage, "command": command, "outcome": "PASS"}
                for stage, command in
                governance.REC_I3_OBSERVABLE_CONTROLLER_CHECK_COMMANDS.items()
            ],
            "limitations": ["Independent immutable review remains pending."],
            "nonActions": {
                "pushed": False,
                "pullRequestOpenedOrEdited": False,
                "merged": False,
                "deviceOrEmulatorRun": False,
                "preflightOrCampaignRun": False,
                "productionWork": False,
                "nextSliceStarted": False,
            },
        }

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
            lambda g, p: (
                g.__setitem__("journalSchemaVersion", 4.0),
                p["unchangedV07"].__setitem__("journalSchemaVersion", 4.0),
            ),
            lambda g, p: p["streamingPersistenceV07"]["database"].__setitem__(
                "userVersion", 4.0
            ),
            lambda g, p: p["streamingPersistenceV07"]["database"].__setitem__(
                "preserveExactV1V2Objects", 1
            ),
            lambda g, p: p["unchangedV07"].__setitem__("unexpected", False),
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
                clean_head = governance.test_git_text(repo, "rev-parse", "HEAD")
                clean_changes = governance.collect_post_merge_changes(
                    root=repo, merged_anchor=base
                )
                clean_tree_paths = governance.git_path_records(
                    "diff", "--name-only", "--no-renames", "-z", base, clean_head,
                    "--", root=repo,
                )
                clean_history_paths = governance.git_path_records(
                    "log", "--format=", "--name-only", "--no-renames", "-z",
                    f"{base}..{clean_head}", "--", root=repo,
                )
                clean_summary = governance.git_output(
                    "log", "--format=", "--summary", "--find-renames",
                    f"{base}..{clean_head}", "--", root=repo,
                )
                governance.validate_rec_i3_result_boundary_delta(
                    clean_changes, clean_tree_paths, clean_history_paths, clean_summary
                )
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

    def test_real_git_v08_history_summary_rejects_final_tree_neutral_delete_revert(self) -> None:
        with tempfile.TemporaryDirectory(prefix="dora-rec-i3-v08-history-") as temporary:
            repo, base = governance.initialize_test_git_repo(Path(temporary))
            for relative in governance.REC_I3_RESULT_BOUNDARY_PATHS:
                path = repo / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(relative, encoding="utf-8")
            governance.commit_test_git_repo(repo, "exact governance delta")

            def profile() -> tuple[dict[str, list[str]], list[str], list[str], str]:
                head = governance.test_git_text(repo, "rev-parse", "HEAD")
                return (
                    governance.collect_post_merge_changes(root=repo, merged_anchor=base),
                    governance.git_path_records(
                        "diff", "--name-only", "--no-renames", "-z", base, head, "--",
                        root=repo,
                    ),
                    governance.git_path_records(
                        "log", "--format=", "--name-only", "--no-renames", "-z",
                        f"{base}..{head}", "--", root=repo,
                    ),
                    governance.git_output(
                        "log", "--format=", "--summary", "--find-renames",
                        f"{base}..{head}", "--", root=repo,
                    ),
                )

            governance.validate_rec_i3_result_boundary_delta(*profile())
            target = repo / governance.REC_I3_RESULT_BOUNDARY_PATHS[0]
            target.unlink()
            deleted = governance.commit_test_git_repo(repo, "delete governance file")
            governance.test_git(
                repo, "-c", "user.name=Dora Validator Test", "-c",
                "user.email=dora-validator@example.invalid",
                "revert", "--no-edit", deleted,
            )
            changes, tree_paths, history_paths, summary = profile()
            self.assertEqual(set(governance.REC_I3_RESULT_BOUNDARY_PATHS), set(tree_paths))
            self.assertEqual(set(governance.REC_I3_RESULT_BOUNDARY_PATHS), set(history_paths))
            self.assertFalse(changes["staged"] or changes["unstaged"] or changes["untracked"])
            with self.assertRaisesRegex(
                ValueError, "committed delta contains rename/delete/mode drift"
            ):
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

    def test_observable_controller_delta_requires_exact_clean_allowlist(self) -> None:
        expected = list(governance.REC_I3_OBSERVABLE_CONTROLLER_PATHS)
        clean = {
            "committed": expected,
            "staged": [],
            "unstaged": [],
            "untracked": [],
        }
        governance.validate_rec_i3_observable_controller_delta(
            clean,
            expected,
            expected,
            "",
        )
        missing_stage00 = [
            path
            for path in expected
            if path != governance.REC_I3_STAGE00_VALIDATOR_PATH
        ]
        with self.assertRaisesRegex(ValueError, "exact committed path delta"):
            governance.validate_rec_i3_observable_controller_delta(
                {**clean, "committed": missing_stage00},
                missing_stage00,
                missing_stage00,
                "",
            )
        for layer in ("committed", "staged", "unstaged", "untracked"):
            with self.subTest(layer=layer):
                changed = {key: list(value) for key, value in clean.items()}
                changed[layer].append("unexpected.txt")
                with self.assertRaisesRegex(ValueError, "exact committed path delta"):
                    governance.validate_rec_i3_observable_controller_delta(
                        changed,
                        expected,
                        expected,
                        "",
                    )

    def test_observable_controller_stage00_validator_is_exactly_pinned_and_invoked(self) -> None:
        self.assertEqual(
            "tools/validate_stage00.py",
            governance.REC_I3_STAGE00_VALIDATOR_PATH,
        )
        self.assertEqual(16, len(governance.REC_I3_OBSERVABLE_CONTROLLER_PATHS))
        self.assertIn(
            governance.REC_I3_STAGE00_VALIDATOR_PATH,
            governance.REC_I3_OBSERVABLE_CONTROLLER_PATHS,
        )
        self.assertEqual(
            "python tools/validate_stage00.py",
            governance.REC_I3_OBSERVABLE_CONTROLLER_CHECK_COMMANDS["STAGE00"],
        )
        governance.validate_rec_i3_regular_file(
            governance.REC_I3_STAGE00_VALIDATOR_PATH,
            expected_mode="100755",
        )
        success = subprocess.CompletedProcess(
            args=[],
            returncode=0,
            stdout="Stage 00 artifact validation passed\n",
            stderr="",
        )
        with patch.object(governance.subprocess, "run", return_value=success) as run:
            governance.validate_rec_i3_stage00_integrity()
        run.assert_called_once_with(
            [
                sys.executable,
                str(governance.ROOT / governance.REC_I3_STAGE00_VALIDATOR_PATH),
            ],
            cwd=governance.ROOT,
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="strict",
        )

        failure = subprocess.CompletedProcess(
            args=[],
            returncode=1,
            stdout="",
            stderr="synthetic Stage 00 failure",
        )
        with (
            patch.object(governance.subprocess, "run", return_value=failure),
            self.assertRaisesRegex(ValueError, "synthetic Stage 00 failure"),
        ):
            governance.validate_rec_i3_stage00_integrity()

    def test_stage00_decision_metadata_requires_exact_anchored_ordered_fields(self) -> None:
        text = stage00.read_text("docs/DORA_MVP1_PRODUCT_DECISIONS.md")
        stage00.validate_decisions()

        marker = "## DEC-048. REC-I3 proven rollback of semantic VALID"
        prefix, separator, decision = text.partition(marker)
        self.assertEqual(marker, separator)

        def mutate_decision(pattern: str, replacement) -> str:
            changed, count = re.subn(
                pattern,
                replacement,
                decision,
                count=1,
                flags=re.MULTILINE,
            )
            self.assertEqual(1, count, pattern)
            return prefix + separator + changed

        moved_to_prose = mutate_decision(r"^Status:.*\n", "").replace(
            "When a semantic VALID persistence attempt",
            "This prose mentions Status: without defining a metadata field.\n\n"
            "When a semantic VALID persistence attempt",
            1,
        )
        mutations = {
            "required-label-moved-into-prose": moved_to_prose,
            "prefixed-label": mutate_decision(r"^Status:", "Not Status:"),
            "misspelled-label": mutate_decision(r"^Status:", "Statu:"),
            "missing-label": mutate_decision(r"^Decision record:.*\n", ""),
            "extra-metadata-label": mutate_decision(
                r"^Scope:.*\n",
                lambda match: match.group(0) + "Unexpected field: forbidden\\\n",
            ),
            "historical-prefixed-label": re.sub(
                r"^Статус:",
                "Не Статус:",
                text,
                count=1,
                flags=re.MULTILINE,
            ),
        }
        for name, changed in mutations.items():
            self.assertNotEqual(text, changed, name)
            with (
                self.subTest(mutation=name),
                patch.object(stage00, "read_text", return_value=changed),
                self.assertRaises(ValueError),
            ):
                stage00.validate_decisions()

        with (
            patch.object(
                stage00,
                "read_text",
                return_value=text + "\n## DEC-049. Unapproved implicit extension\n",
            ),
            self.assertRaisesRegex(ValueError, "DEC-001 through DEC-048"),
        ):
            stage00.validate_decisions()

    def test_observable_controller_profile_is_additive_to_v08(self) -> None:
        old = governance.RecoveryLifecycleIdentity(
            "c" * 40,
            governance.REC_I3_RESULT_BOUNDARY_BRANCH,
            None,
            None,
            None,
            None,
            (),
            None,
            None,
            False,
        )
        current = replace(old, branch=governance.REC_I3_OBSERVABLE_CONTROLLER_BRANCH)
        self.assertTrue(governance.rec_i3_result_boundary_candidate(old))
        self.assertFalse(governance.rec_i3_observable_controller_candidate(old))
        self.assertTrue(governance.rec_i3_observable_controller_candidate(current))
        self.assertFalse(governance.rec_i3_result_boundary_candidate(current))

    def test_observable_controller_evidence_is_exact_and_mutation_sensitive(self) -> None:
        record = self.observable_controller_evidence()
        governance.validate_rec_i3_observable_controller_evidence(record)
        mutations = (
            lambda item: item["contractCounts"].__setitem__("classifications", 19),
            lambda item: item["sourceFiles"].__setitem__(
                governance.REC_I3_OBSERVABLE_CONTROLLER_SOURCE_PATHS[0], "0" * 64
            ),
            lambda item: item["checks"].pop(),
            lambda item: item["checks"][0].__setitem__("command", "weaker check"),
            lambda item: item["nonActions"].__setitem__("pushed", True),
        )
        for mutation in mutations:
            with self.subTest(mutation=mutation):
                changed = copy.deepcopy(record)
                mutation(changed)
                with self.assertRaises(ValueError):
                    governance.validate_rec_i3_observable_controller_evidence(changed)

    def test_observable_controller_dispatches_before_v08(self) -> None:
        lifecycle = replace(
            governance.collect_recovery_lifecycle_identity(),
            branch=governance.REC_I3_OBSERVABLE_CONTROLLER_BRANCH,
        )
        with patch.object(
            governance, "validate_rec_i3_observable_controller"
        ) as validate:
            self.assertTrue(governance.validate_current_rec_i3_successor(lifecycle))
            validate.assert_called_once_with(lifecycle)

    def test_observable_controller_profile_enforces_delta_pins_contract_and_evidence(self) -> None:
        lifecycle = replace(
            governance.collect_recovery_lifecycle_identity(),
            branch=governance.REC_I3_OBSERVABLE_CONTROLLER_BRANCH,
        )
        expected = list(governance.REC_I3_OBSERVABLE_CONTROLLER_PATHS)
        changes = {
            "committed": expected,
            "staged": [],
            "unstaged": [],
            "untracked": [],
        }
        original_read = governance.read_json

        def read_profile(relative: str) -> dict:
            if relative == governance.REC_I3_OBSERVABLE_CONTROLLER_EVIDENCE_PATH:
                return self.observable_controller_evidence()
            return original_read(relative)

        def git_profile(*args: str) -> str:
            if args[0] == "rev-parse":
                return governance.REC_I3_OBSERVABLE_CONTROLLER_BASE_TREE
            return ""

        with (
            patch.object(governance, "git_is_ancestor", return_value=True),
            patch.object(governance, "git_output", side_effect=git_profile),
            patch.object(governance, "git_path_records", return_value=expected),
            patch.object(governance, "collect_post_merge_changes", return_value=changes),
            patch.object(governance, "validate_rec_i3_regular_file"),
            patch.object(governance, "read_json", side_effect=read_profile),
            patch.object(governance, "validate_rec_i3_observable_controller_delta") as delta,
            patch.object(governance, "validate_rec_i3_stage00_integrity") as stage00,
            patch.object(governance, "validate_rec_i3_result_boundary_contract") as contract,
            patch.object(governance, "validate_rec_i3_observable_controller_evidence") as evidence,
        ):
            governance.validate_rec_i3_observable_controller(lifecycle)
        delta.assert_called_once_with(changes, expected, expected, "")
        stage00.assert_called_once_with()
        contract.assert_called_once()
        evidence.assert_called_once_with(self.observable_controller_evidence())

    def test_exact_observable_controller_profile_accepts_current_checkout(self) -> None:
        lifecycle = governance.collect_recovery_lifecycle_identity()
        self.assertEqual(governance.REC_I3_OBSERVABLE_CONTROLLER_BRANCH, lifecycle.branch)
        self.assertTrue(governance.validate_current_rec_i3_successor(lifecycle))

    def test_exact_squash_merged_main_topology_passes_validator_entrypoint(self) -> None:
        integrated_main = "be37378ca88e0bd4aee1f2fe0c54362798bdef9d"
        reviewed_source = "89551b17a84bc090ccf1cd36d48aeb59afc403fa"
        reviewed_tree = "4519cbf6fda95f8e39a36e3b2c0bf62fef4981db"
        integrated_parent = "da1d9bd13b71d609fe7ec4ea62fe1e984f726040"
        governance.validate_rec_i3_reviewed_source_provenance()
        self.assertEqual(
            reviewed_tree,
            governance.git_output("rev-parse", f"{reviewed_source}^{{tree}}"),
        )
        self.assertEqual(
            reviewed_tree,
            governance.git_output("rev-parse", f"{integrated_main}^{{tree}}"),
        )
        self.assertEqual(
            integrated_parent,
            governance.git_output("show", "-s", "--format=%P", integrated_main),
        )
        self.assertFalse(governance.git_is_ancestor(reviewed_source, integrated_main))
        self.assertFalse(governance.git_is_ancestor(governance.REC_I3_SCOPE_COMMIT, integrated_main))

        with tempfile.TemporaryDirectory(prefix="dora-rec-i3-squash-main-") as temporary:
            parent = Path(temporary)
            repo = parent / "repo"
            governance.test_git(
                parent,
                "clone",
                "--shared",
                "--no-checkout",
                str(governance.ROOT),
                str(repo),
            )
            governance.test_git(repo, "checkout", "-q", "-B", "main", integrated_main)
            child_environment = os.environ.copy()
            for key in tuple(child_environment):
                if key.startswith("GITHUB_") or key == "RUNNER_TEMP":
                    child_environment.pop(key)
            child_environment.update(
                {
                    "GITHUB_EVENT_NAME": "push",
                    "GITHUB_REPOSITORY": governance.GITHUB_REPOSITORY,
                    "GITHUB_WORKSPACE": str(repo.resolve()),
                    "GITHUB_REF": "refs/heads/main",
                    "GITHUB_SHA": integrated_main,
                }
            )
            validator_source = str(Path(governance.__file__).resolve())
            child_code = (
                "import importlib.util, os, pathlib, sys; "
                "source = pathlib.Path(sys.argv[1]); root = pathlib.Path(sys.argv[2]); "
                "spec = importlib.util.spec_from_file_location('squash_main_governance', source); "
                "module = importlib.util.module_from_spec(spec); "
                "sys.modules[spec.name] = module; spec.loader.exec_module(module); "
                "module.ROOT = root; os.chdir(root); "
                "sys.argv = ['validate_poc_recovery_governance.py']; "
                "raise SystemExit(module.main())"
            )
            completed = self.run_bounded_validator_child(
                cwd=repo,
                environment=child_environment,
                command=[sys.executable, "-c", child_code, validator_source, str(repo)],
            )
            self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)
            self.assertIn("squash-merged main", completed.stdout)

            wrong_ref_environment = child_environment.copy()
            wrong_ref_environment["GITHUB_REF"] = "refs/heads/not-main"
            rejected = self.run_bounded_validator_child(
                cwd=repo,
                environment=wrong_ref_environment,
                command=[sys.executable, "-c", child_code, validator_source, str(repo)],
            )
            self.assertNotEqual(0, rejected.returncode)
            self.assertIn(
                "REC-I3 squash-main GitHub push identity drift",
                rejected.stdout + rejected.stderr,
            )

    def test_source_equal_squash_merged_correction_main_is_exact_and_terminal(self) -> None:
        integrated_main = "be37378ca88e0bd4aee1f2fe0c54362798bdef9d"
        correction_root = Path(governance.__file__).resolve().parents[1]
        correction_source = self.integrated_correction_head()
        correction_tree = subprocess.run(
            ["git", "show", "-s", "--format=%T", correction_source],
            cwd=correction_root,
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
        validator_source = str(Path(governance.__file__).resolve())
        child_code = (
            "import importlib.util, os, pathlib, sys; "
            "source = pathlib.Path(sys.argv[1]); root = pathlib.Path(sys.argv[2]); "
            "spec = importlib.util.spec_from_file_location('correction_main_governance', source); "
            "module = importlib.util.module_from_spec(spec); "
            "sys.modules[spec.name] = module; spec.loader.exec_module(module); "
            "module.ROOT = root; os.chdir(root); "
            "sys.argv = ['validate_poc_recovery_governance.py']; "
            "raise SystemExit(module.main())"
        )
        with tempfile.TemporaryDirectory(prefix="dora-rec-i3-correction-main-") as temporary:
            parent = Path(temporary)
            repo = parent / "repo"
            governance.test_git(
                parent,
                "clone",
                "--shared",
                "--no-checkout",
                str(governance.ROOT),
                str(repo),
            )
            squash_commit = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                correction_tree,
                "-p",
                integrated_main,
                input_data=b"synthetic source-equal squash correction\n",
            )
            governance.test_git(repo, "checkout", "-q", "-B", "main", squash_commit)
            self.assertEqual(
                correction_tree,
                governance.test_git_text(repo, "rev-parse", f"{squash_commit}^{{tree}}"),
            )
            self.assertEqual(
                integrated_main,
                governance.test_git_text(repo, "show", "-s", "--format=%P", squash_commit),
            )
            self.assertEqual(
                set(governance.REC_I3_SQUASH_MAIN_CORRECTION_PATHS),
                set(
                    governance.test_git_text(
                        repo,
                        "diff",
                        "--name-only",
                        integrated_main,
                        squash_commit,
                        "--",
                    ).splitlines()
                ),
            )
            child_environment = os.environ.copy()
            for key in tuple(child_environment):
                if key.startswith("GITHUB_") or key == "RUNNER_TEMP":
                    child_environment.pop(key)
            local = self.run_bounded_validator_child(
                cwd=repo,
                environment=child_environment,
                command=[sys.executable, "-c", child_code, validator_source, str(repo)],
            )
            self.assertEqual(0, local.returncode, local.stdout + local.stderr)
            self.assertIn("squash-merged main", local.stdout)

            child_environment.update(
                {
                    "GITHUB_EVENT_NAME": "push",
                    "GITHUB_REPOSITORY": governance.GITHUB_REPOSITORY,
                    "GITHUB_WORKSPACE": str(repo.resolve()),
                    "GITHUB_REF": "refs/heads/main",
                    "GITHUB_SHA": squash_commit,
                }
            )
            github = self.run_bounded_validator_child(
                cwd=repo,
                environment=child_environment,
                command=[sys.executable, "-c", child_code, validator_source, str(repo)],
            )
            self.assertEqual(0, github.returncode, github.stdout + github.stderr)

            later_descendant = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                correction_tree,
                "-p",
                squash_commit,
                input_data=b"synthetic later descendant\n",
            )
            governance.test_git(repo, "checkout", "-q", "-B", "main", later_descendant)
            child_environment["GITHUB_SHA"] = later_descendant
            rejected = self.run_bounded_validator_child(
                cwd=repo,
                environment=child_environment,
                command=[sys.executable, "-c", child_code, validator_source, str(repo)],
            )
            self.assertNotEqual(0, rejected.returncode)
            self.assertNotIn("squash-merged main validation passed", rejected.stdout)

    def test_correction_pull_request_binds_head_entries_and_clean_layers(self) -> None:
        integrated_main = "be37378ca88e0bd4aee1f2fe0c54362798bdef9d"
        correction_branch = "codex/rec-i3-squash-main-governance-v01"
        correction_root = Path(governance.__file__).resolve().parents[1]
        correction_head = self.integrated_correction_head()
        correction_tree = subprocess.run(
            ["git", "show", "-s", "--format=%T", correction_head],
            cwd=correction_root,
            check=True,
            capture_output=True,
            text=True,
        ).stdout.strip()
        validator_source = str(Path(governance.__file__).resolve())
        child_code = (
            "import importlib.util, os, pathlib, sys; "
            "source = pathlib.Path(sys.argv[1]); root = pathlib.Path(sys.argv[2]); "
            "spec = importlib.util.spec_from_file_location('correction_pr_governance', source); "
            "module = importlib.util.module_from_spec(spec); "
            "sys.modules[spec.name] = module; spec.loader.exec_module(module); "
            "module.ROOT = root; os.chdir(root); "
            "sys.argv = ['validate_poc_recovery_governance.py']; "
            "raise SystemExit(module.main())"
        )
        for case in ("clean", "unstaged", "merge-blob"):
            with self.subTest(case=case), tempfile.TemporaryDirectory(
                prefix="dora-rec-i3-correction-pr-"
            ) as temporary:
                parent = Path(temporary)
                repo = parent / "repo"
                governance.test_git(
                    parent,
                    "clone",
                    "--shared",
                    "--no-checkout",
                    str(governance.ROOT),
                    str(repo),
                )
                merge_tree = correction_tree
                if case == "merge-blob":
                    governance.test_git(repo, "checkout", "--detach", "-q", correction_head)
                    test_path = repo / "tools/test_poc_recovery_i3_governance.py"
                    test_path.write_bytes(test_path.read_bytes() + b"\n# synthetic merge-only drift\n")
                    governance.test_git(repo, "add", "--", "tools/test_poc_recovery_i3_governance.py")
                    merge_tree = governance.test_git_text(repo, "write-tree")
                merge_head = governance.test_git_text(
                    repo,
                    "-c",
                    "user.name=Dora Validator Test",
                    "-c",
                    "user.email=dora-validator@example.invalid",
                    "commit-tree",
                    merge_tree,
                    "-p",
                    integrated_main,
                    "-p",
                    correction_head,
                    input_data=b"synthetic correction pull request merge\n",
                )
                governance.test_git(repo, "checkout", "--detach", "-q", merge_head)
                if case == "unstaged":
                    test_path = repo / "tools/test_poc_recovery_i3_governance.py"
                    test_path.write_bytes(test_path.read_bytes() + b"\n# synthetic unstaged drift\n")

                runner_temp = parent / "runner-temp"
                runner_temp.mkdir()
                event_path = runner_temp / "event.json"
                pull_request_number = 68
                governance.write_test_pull_request_event(
                    event_path,
                    number=pull_request_number,
                    head_ref=correction_branch,
                    head_sha=correction_head,
                    base_sha=integrated_main,
                    merge_sha=merge_head,
                )
                child_environment = os.environ.copy()
                for key in tuple(child_environment):
                    if key.startswith("GITHUB_") or key == "RUNNER_TEMP":
                        child_environment.pop(key)
                child_environment.update(
                    {
                        "GITHUB_EVENT_NAME": "pull_request",
                        "GITHUB_REPOSITORY": governance.GITHUB_REPOSITORY,
                        "GITHUB_WORKSPACE": str(repo.resolve()),
                        "RUNNER_TEMP": str(runner_temp.resolve()),
                        "GITHUB_EVENT_PATH": str(event_path.resolve()),
                        "GITHUB_HEAD_REF": correction_branch,
                        "GITHUB_BASE_REF": governance.GITHUB_BASE_BRANCH,
                        "GITHUB_REF": f"refs/pull/{pull_request_number}/merge",
                        "GITHUB_SHA": merge_head,
                    }
                )
                completed = self.run_bounded_validator_child(
                    cwd=repo,
                    environment=child_environment,
                    command=[sys.executable, "-c", child_code, validator_source, str(repo)],
                )
                if case == "clean":
                    self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)
                    self.assertIn("squash-merged main", completed.stdout)
                else:
                    self.assertNotEqual(
                        0,
                        completed.returncode,
                        f"{case} correction PR unexpectedly passed\n"
                        + completed.stdout
                        + completed.stderr,
                    )
                    expected_error = (
                        "REC-I3 correction integration checkout is dirty"
                        if case == "unstaged"
                        else "REC-I3 correction pull_request merge entry differs"
                    )
                    self.assertIn(
                        expected_error,
                        completed.stdout + completed.stderr,
                    )

    def test_integrated_profiles_do_not_require_deleted_review_source_object(self) -> None:
        integrated_main = "be37378ca88e0bd4aee1f2fe0c54362798bdef9d"
        reviewed_source = "89551b17a84bc090ccf1cd36d48aeb59afc403fa"
        correction_branch = "codex/rec-i3-squash-main-governance-v01"
        harness_branch = "codex/rec-i3-e36-gapi-preflight-v01"
        harness_head = "7a7036513f2eb460ed72136e1784d712c4aae42d"
        correction_root = Path(governance.__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory(prefix="dora-rec-i3-remote-objects-") as temporary:
            parent = Path(temporary)
            builder = parent / "correction-builder"
            governance.test_git(
                parent,
                "clone",
                "--shared",
                "--no-checkout",
                str(correction_root),
                str(builder),
            )
            builder_parent = governance.test_git_text(correction_root, "rev-parse", "HEAD")
            governance.test_git(builder, "checkout", "--detach", "-q", builder_parent)
            for relative in governance.REC_I3_SQUASH_MAIN_CORRECTION_PATHS:
                target = builder / relative
                target.write_bytes((correction_root / relative).read_bytes())
            governance.test_git(
                builder,
                "add",
                "--",
                *governance.REC_I3_SQUASH_MAIN_CORRECTION_PATHS,
            )
            correction_tree = governance.test_git_text(builder, "write-tree")
            correction_head = governance.test_git_text(
                builder,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                correction_tree,
                "-p",
                builder_parent,
                input_data=b"checkout-local correction source\n",
            )
            remote = parent / "remote.git"
            governance.test_git(parent, "init", "--bare", str(remote))
            subprocess.run(
                [
                    "git",
                    "push",
                    str(remote),
                    f"{integrated_main}:refs/heads/main",
                    f"{correction_head}:refs/heads/correction",
                    f"{harness_head}:refs/heads/harness",
                ],
                cwd=builder,
                check=True,
                capture_output=True,
            )

            for case in (
                "correction-runtime",
                "correction-self-test",
                "e36-runtime",
                "e36-self-test",
            ):
                with self.subTest(case=case):
                    repo = parent / case
                    governance.test_git(
                        parent,
                        "clone",
                        "--no-local",
                        "--no-checkout",
                        str(remote),
                        str(repo),
                    )
                    missing_source = subprocess.run(
                        ["git", "cat-file", "-e", f"{reviewed_source}^{{commit}}"],
                        cwd=repo,
                        check=False,
                        capture_output=True,
                    )
                    self.assertNotEqual(0, missing_source.returncode)
                    self.assertFalse((repo / ".git/objects/info/alternates").exists())

                    if case.startswith("correction"):
                        merge_head = governance.test_git_text(
                            repo,
                            "-c",
                            "user.name=Dora Validator Test",
                            "-c",
                            "user.email=dora-validator@example.invalid",
                            "commit-tree",
                            correction_tree,
                            "-p",
                            integrated_main,
                            "-p",
                            correction_head,
                            input_data=b"minimal correction pull request merge\n",
                        )
                        governance.test_git(repo, "checkout", "--detach", "-q", merge_head)
                        head_ref = correction_branch
                        head_sha = correction_head
                        base_ref = governance.GITHUB_BASE_BRANCH
                    else:
                        governance.test_git(
                            repo, "checkout", "-q", "-B", correction_branch, correction_head
                        )
                        governance.test_git(
                            repo,
                            "-c",
                            "user.name=Dora Validator Test",
                            "-c",
                            "user.email=dora-validator@example.invalid",
                            "merge",
                            "--no-ff",
                            "--no-edit",
                            harness_head,
                        )
                        merge_head = governance.test_git_text(repo, "rev-parse", "HEAD")
                        governance.test_git(repo, "checkout", "--detach", "-q", merge_head)
                        head_ref = harness_branch
                        head_sha = harness_head
                        base_ref = correction_branch

                    runner_temp = parent / f"{case}-runner-temp"
                    runner_temp.mkdir()
                    event_path = runner_temp / "event.json"
                    pull_request_number = 68
                    event_path.write_text(
                        json.dumps(
                            {
                                "number": pull_request_number,
                                "repository": {"full_name": governance.GITHUB_REPOSITORY},
                                "pull_request": {
                                    "number": pull_request_number,
                                    "merge_commit_sha": merge_head,
                                    "draft": True,
                                    "state": "open",
                                    "merged": False,
                                    "head": {
                                        "ref": head_ref,
                                        "sha": head_sha,
                                        "repo": {"full_name": governance.GITHUB_REPOSITORY},
                                    },
                                    "base": {
                                        "ref": base_ref,
                                        "sha": (
                                            integrated_main
                                            if case.startswith("correction")
                                            else correction_head
                                        ),
                                        "repo": {"full_name": governance.GITHUB_REPOSITORY},
                                    },
                                },
                            }
                        ),
                        encoding="utf-8",
                    )
                    child_environment = os.environ.copy()
                    for key in tuple(child_environment):
                        if key.startswith("GITHUB_") or key == "RUNNER_TEMP":
                            child_environment.pop(key)
                    child_environment.update(
                        {
                            "GITHUB_EVENT_NAME": "pull_request",
                            "GITHUB_REPOSITORY": governance.GITHUB_REPOSITORY,
                            "GITHUB_WORKSPACE": str(repo.resolve()),
                            "RUNNER_TEMP": str(runner_temp.resolve()),
                            "GITHUB_EVENT_PATH": str(event_path.resolve()),
                            "GITHUB_HEAD_REF": head_ref,
                            "GITHUB_BASE_REF": base_ref,
                            "GITHUB_REF": f"refs/pull/{pull_request_number}/merge",
                            "GITHUB_SHA": merge_head,
                        }
                    )
                    command = [
                        sys.executable,
                        str(repo / "tools/validate_poc_recovery_governance.py"),
                    ]
                    if case.endswith("self-test"):
                        command.append("--self-test")
                    completed = self.run_bounded_validator_child(
                        cwd=repo,
                        environment=child_environment,
                        timeout_seconds=360.0,
                        command=command,
                    )
                    self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)
                    expected = (
                        "exact E36-GAPI harness"
                        if case.startswith("e36")
                        else "source-equal squash-merged main"
                    )
                    self.assertIn(expected, completed.stdout)

    def test_e36_post_merge_governance_is_object_closed_and_rejects_drift(self) -> None:
        if os.environ.get("REC_I3_OBJECT_CLOSURE_CHILD") == "1":
            return

        integrated_harness = "3e7ce71410b9504bd08bfbcd1407c2e1f80ab8c6"
        historical_harness = "7a7036513f2eb460ed72136e1784d712c4aae42d"
        governance_root = Path(governance.__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory(prefix="dora-rec-i3-e36-object-closure-") as temporary:
            parent = Path(temporary)
            builder = parent / "builder"
            governance.test_git(
                parent,
                "clone",
                "--shared",
                "--no-checkout",
                str(governance_root),
                str(builder),
            )
            governance.test_git(builder, "checkout", "--detach", "-q", integrated_harness)
            for relative in governance.REC_I3_SQUASH_MAIN_CORRECTION_PATHS:
                target = builder / relative
                target.write_bytes((governance_root / relative).read_bytes())
            governance.test_git(
                builder,
                "add",
                "--",
                *governance.REC_I3_SQUASH_MAIN_CORRECTION_PATHS,
            )
            source_tree = governance.test_git_text(builder, "write-tree")
            source_head = governance.test_git_text(
                builder,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                source_tree,
                "-p",
                integrated_harness,
                input_data=b"synthetic post-merge governance source\n",
            )
            self.assertTrue(
                governance.rec_i3_e36_post_merge_governance_source_candidate(
                    source_head,
                    integrated_harness,
                    root=builder,
                )
            )

            squash_head = governance.test_git_text(
                builder,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                source_tree,
                "-p",
                integrated_harness,
                input_data=b"synthetic post-merge governance squash\n",
            )
            self.assertTrue(
                governance.rec_i3_e36_post_merge_governance_commit_candidate(
                    squash_head,
                    root=builder,
                )
            )

            governance.test_git(builder, "checkout", "--detach", "-q", source_head)
            extra = builder / "synthetic-extra.txt"
            extra.write_text("extra\n", encoding="utf-8")
            governance.test_git(builder, "add", "--", extra.name)
            extra_tree = governance.test_git_text(builder, "write-tree")
            extra_head = governance.test_git_text(
                builder,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                extra_tree,
                "-p",
                integrated_harness,
                input_data=b"synthetic post-merge governance extra path\n",
            )
            self.assertFalse(
                governance.rec_i3_e36_post_merge_governance_commit_candidate(
                    extra_head,
                    root=builder,
                )
            )

            governance.test_git(builder, "checkout", "--detach", "-q", source_head)
            drift_path = builder / governance.REC_I3_E36_GAPI_PATHS[0]
            drift_path.write_bytes(drift_path.read_bytes() + b"\n// synthetic drift\n")
            governance.test_git(
                builder, "add", "--", governance.REC_I3_E36_GAPI_PATHS[0]
            )
            drift_tree = governance.test_git_text(builder, "write-tree")
            drift_head = governance.test_git_text(
                builder,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                drift_tree,
                "-p",
                integrated_harness,
                input_data=b"synthetic post-merge governance harness drift\n",
            )
            self.assertFalse(
                governance.rec_i3_e36_post_merge_governance_commit_candidate(
                    drift_head,
                    root=builder,
                )
            )

            remote = parent / "remote.git"
            governance.test_git(parent, "init", "--bare", str(remote))
            governance.test_git(
                builder,
                "push",
                str(remote),
                f"{squash_head}:refs/heads/main",
            )
            checkout = parent / "checkout"
            governance.test_git(
                parent,
                "clone",
                "--no-local",
                "--single-branch",
                "--branch",
                "main",
                str(remote),
                str(checkout),
            )
            self.assertFalse((checkout / ".git/objects/info/alternates").exists())
            self.assertNotEqual(
                0,
                subprocess.run(
                    ["git", "cat-file", "-e", f"{historical_harness}^{{commit}}"],
                    cwd=checkout,
                    check=False,
                    capture_output=True,
                ).returncode,
            )
            self.assertNotEqual(
                0,
                subprocess.run(
                    ["git", "cat-file", "-e", f"{source_head}^{{commit}}"],
                    cwd=checkout,
                    check=False,
                    capture_output=True,
                ).returncode,
            )
            self.assertNotEqual(
                0,
                subprocess.run(
                    ["git", "show-ref", "--verify", "refs/heads/codex/rec-i3-e36-gapi-preflight-v01"],
                    cwd=checkout,
                    check=False,
                    capture_output=True,
                ).returncode,
            )
            refs = governance.test_git_text(
                checkout, "for-each-ref", "--format=%(refname)"
            ).splitlines()
            self.assertFalse(any(ref.startswith("refs/pull/") for ref in refs))
            self.assertFalse(any(governance.REC_I3_E36_GAPI_BRANCH in ref for ref in refs))
            reflog_commits = governance.test_git_text(
                checkout, "reflog", "--all", "--format=%H"
            ).splitlines()
            self.assertNotIn(source_head, reflog_commits)
            self.assertNotIn(historical_harness, reflog_commits)
            child_environment = os.environ.copy()
            for key in tuple(child_environment):
                if key.startswith("GITHUB_") or key == "RUNNER_TEMP":
                    child_environment.pop(key)
            child_environment.update(
                {
                    "REC_I3_OBJECT_CLOSURE_CHILD": "1",
                    "GITHUB_EVENT_NAME": "push",
                    "GITHUB_REPOSITORY": governance.GITHUB_REPOSITORY,
                    "GITHUB_WORKSPACE": str(checkout.resolve()),
                    "GITHUB_REF": "refs/heads/main",
                    "GITHUB_SHA": squash_head,
                }
            )
            completed = self.run_bounded_validator_child(
                cwd=checkout,
                environment=child_environment,
                timeout_seconds=360.0,
            )
            self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)
            self.assertIn("exact E36-GAPI harness", completed.stdout)

    def test_e36_guard_remediation_is_object_closed_and_squash_safe(self) -> None:
        governance_root = Path(governance.__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory(prefix="dora-rec-i3-e36-guard-remediation-") as temporary:
            parent = Path(temporary)
            builder = parent / "builder"
            governance.test_git(
                parent,
                "clone",
                "--shared",
                "--no-checkout",
                str(governance_root),
                str(builder),
            )
            governance.test_git(
                builder,
                "checkout",
                "--detach",
                "-q",
                governance.REC_I3_E36_GUARD_BASE,
            )
            for relative in governance.REC_I3_E36_GUARD_IMPLEMENTATION_PATHS:
                target = builder / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes((governance_root / relative).read_bytes())
            governance.test_git(
                builder,
                "add",
                "--",
                *governance.REC_I3_E36_GUARD_IMPLEMENTATION_PATHS,
            )
            implementation_tree = governance.test_git_text(builder, "write-tree")
            implementation_commit = governance.test_git_text(
                builder,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                implementation_tree,
                "-p",
                governance.REC_I3_E36_GUARD_BASE,
                input_data=b"synthetic E36 guard implementation\n",
            )
            original_implementation_commit = (
                governance.REC_I3_E36_GUARD_IMPLEMENTATION_COMMIT
            )
            self.addCleanup(
                setattr,
                governance,
                "REC_I3_E36_GUARD_IMPLEMENTATION_COMMIT",
                original_implementation_commit,
            )
            governance.REC_I3_E36_GUARD_IMPLEMENTATION_COMMIT = implementation_commit
            self.assertTrue(
                governance.rec_i3_e36_guard_implementation_commit_candidate(
                    governance.REC_I3_E36_GUARD_IMPLEMENTATION_COMMIT,
                    root=builder,
                )
            )

            governance.test_git(
                builder,
                "checkout",
                "--detach",
                "-q",
                governance.REC_I3_E36_GUARD_IMPLEMENTATION_COMMIT,
            )
            for relative in governance.REC_I3_E36_GUARD_GOVERNANCE_PATHS:
                target = builder / relative
                target.write_bytes((governance_root / relative).read_bytes())
            governance.test_git(
                builder,
                "add",
                "--",
                *governance.REC_I3_E36_GUARD_GOVERNANCE_PATHS,
            )
            source_tree = governance.test_git_text(builder, "write-tree")
            source_head = governance.test_git_text(
                builder,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                source_tree,
                "-p",
                governance.REC_I3_E36_GUARD_IMPLEMENTATION_COMMIT,
                input_data=b"synthetic E36 guard governance successor\n",
            )
            self.assertTrue(
                governance.rec_i3_e36_guard_remediation_source_candidate(
                    source_head,
                    governance.REC_I3_E36_GUARD_BASE,
                    root=builder,
                )
            )

            squash_head = governance.test_git_text(
                builder,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                source_tree,
                "-p",
                governance.REC_I3_E36_GUARD_BASE,
                input_data=b"synthetic E36 guard squash main\n",
            )
            self.assertTrue(
                governance.rec_i3_e36_guard_remediation_main_candidate(
                    squash_head,
                    root=builder,
                )
            )
            self.assertFalse(
                governance.rec_i3_e36_guard_remediation_source_candidate(
                    source_head,
                    governance.REC_I3_E36_GAPI_INTEGRATED,
                    root=builder,
                )
            )

            governance.test_git(builder, "checkout", "--detach", "-q", source_head)
            extra = builder / "synthetic-extra.txt"
            extra.write_text("extra\n", encoding="utf-8")
            governance.test_git(builder, "add", "--", extra.name)
            extra_tree = governance.test_git_text(builder, "write-tree")
            extra_source = governance.test_git_text(
                builder,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                extra_tree,
                "-p",
                governance.REC_I3_E36_GUARD_IMPLEMENTATION_COMMIT,
                input_data=b"synthetic E36 guard extra path\n",
            )
            self.assertFalse(
                governance.rec_i3_e36_guard_remediation_source_candidate(
                    extra_source,
                    governance.REC_I3_E36_GUARD_BASE,
                    root=builder,
                )
            )

    def test_e36_sqlite_pragma_remediation_is_object_closed_and_squash_safe(self) -> None:
        governance_root = Path(governance.__file__).resolve().parents[1]
        with tempfile.TemporaryDirectory(prefix="dora-rec-i3-e36-sqlite-remediation-") as temporary:
            parent = Path(temporary)
            builder = parent / "builder"
            governance.test_git(
                parent,
                "clone",
                "--shared",
                "--no-checkout",
                str(governance_root),
                str(builder),
            )
            governance.test_git(
                builder,
                "checkout",
                "--detach",
                "-q",
                governance.REC_I3_E36_SQLITE_BASE,
            )
            runtime_pin_prelude = b'''E36_PREFLIGHT = ROOT / (\n    "android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/"\n    "candidate/RecoveryE36GapiPreflightInstrumentedTest.kt"\n)\nE36_SQLITE_RUNTIME_PIN = "4c318c054da39768340f059db5687051dde8a843"\n\n'''
            runtime_pin_assertion = b'''    preflight = E36_PREFLIGHT.read_text(encoding="utf-8")\n    assert f'.put("integratedRuntimePin", "{E36_SQLITE_RUNTIME_PIN}")' in preflight, (\n        "E36 evidence must identify the exact SQLite-remediation runtime commit"\n    )\n'''
            for relative in governance.REC_I3_E36_SQLITE_IMPLEMENTATION_PATHS:
                target = builder / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                content = (governance_root / relative).read_bytes().replace(b"\r\n", b"\n")
                if relative == "tools/verify_rec_i3_streaming_sqlite.py":
                    content = content.replace(runtime_pin_prelude, b"\n").replace(
                        runtime_pin_assertion, b""
                    )
                target.write_bytes(content)
            governance.test_git(
                builder,
                "add",
                "--",
                *governance.REC_I3_E36_SQLITE_IMPLEMENTATION_PATHS,
            )
            implementation_tree = governance.test_git_text(builder, "write-tree")
            implementation_commit = governance.test_git_text(
                builder,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                implementation_tree,
                "-p",
                governance.REC_I3_E36_SQLITE_BASE,
                input_data=b"synthetic E36 SQLite implementation\n",
            )
            original_implementation_commit = (
                governance.REC_I3_E36_SQLITE_IMPLEMENTATION_COMMIT
            )
            self.addCleanup(
                setattr,
                governance,
                "REC_I3_E36_SQLITE_IMPLEMENTATION_COMMIT",
                original_implementation_commit,
            )
            governance.REC_I3_E36_SQLITE_IMPLEMENTATION_COMMIT = implementation_commit
            self.assertTrue(
                governance.rec_i3_e36_sqlite_implementation_commit_candidate(
                    implementation_commit,
                    root=builder,
                )
            )

            governance.test_git(builder, "checkout", "--detach", "-q", implementation_commit)
            for relative in governance.REC_I3_E36_SQLITE_EVIDENCE_PATHS:
                target = builder / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes((governance_root / relative).read_bytes())
            governance.test_git(
                builder,
                "add",
                "--",
                *governance.REC_I3_E36_SQLITE_EVIDENCE_PATHS,
            )
            evidence_tree = governance.test_git_text(builder, "write-tree")
            evidence_commit = governance.test_git_text(
                builder,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                evidence_tree,
                "-p",
                implementation_commit,
                input_data=b"synthetic E36 SQLite evidence identity\n",
            )
            original_evidence_commit = governance.REC_I3_E36_SQLITE_EVIDENCE_COMMIT
            self.addCleanup(
                setattr,
                governance,
                "REC_I3_E36_SQLITE_EVIDENCE_COMMIT",
                original_evidence_commit,
            )
            governance.REC_I3_E36_SQLITE_EVIDENCE_COMMIT = evidence_commit
            self.assertTrue(
                governance.rec_i3_e36_sqlite_evidence_commit_candidate(
                    evidence_commit,
                    root=builder,
                )
            )

            governance.test_git(builder, "checkout", "--detach", "-q", evidence_commit)
            for relative in governance.REC_I3_E36_SQLITE_GOVERNANCE_PATHS:
                target = builder / relative
                target.write_bytes((governance_root / relative).read_bytes())
            governance.test_git(
                builder,
                "add",
                "--",
                *governance.REC_I3_E36_SQLITE_GOVERNANCE_PATHS,
            )
            source_tree = governance.test_git_text(builder, "write-tree")
            source_head = governance.test_git_text(
                builder,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                source_tree,
                "-p",
                evidence_commit,
                input_data=b"synthetic E36 SQLite governance successor\n",
            )
            self.assertTrue(
                governance.rec_i3_e36_sqlite_remediation_source_candidate(
                    source_head,
                    governance.REC_I3_E36_SQLITE_BASE,
                    root=builder,
                )
            )

            squash_head = governance.test_git_text(
                builder,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                source_tree,
                "-p",
                governance.REC_I3_E36_SQLITE_BASE,
                input_data=b"synthetic E36 SQLite squash main\n",
            )
            self.assertTrue(
                governance.rec_i3_e36_sqlite_remediation_main_candidate(
                    squash_head,
                    root=builder,
                )
            )

            governance.test_git(builder, "checkout", "--detach", "-q", source_head)
            extra = builder / "synthetic-extra.txt"
            extra.write_text("extra\n", encoding="utf-8")
            governance.test_git(builder, "add", "--", extra.name)
            extra_tree = governance.test_git_text(builder, "write-tree")
            extra_source = governance.test_git_text(
                builder,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                extra_tree,
                "-p",
                evidence_commit,
                input_data=b"synthetic E36 SQLite extra path\n",
            )
            self.assertFalse(
                governance.rec_i3_e36_sqlite_remediation_source_candidate(
                    extra_source,
                    governance.REC_I3_E36_SQLITE_BASE,
                    root=builder,
                )
            )

    def test_e36_gapi_exact_local_and_stacked_pr_topologies_are_reachable(self) -> None:
        integrated_main = "be37378ca88e0bd4aee1f2fe0c54362798bdef9d"
        harness_head = "7a7036513f2eb460ed72136e1784d712c4aae42d"
        harness_tree = "04fa791843f2a0cb6ea4fe0ce3be1002b8ab2f3e"
        harness_branch = "codex/rec-i3-e36-gapi-preflight-v01"
        correction_branch = "codex/rec-i3-squash-main-governance-v01"
        validator_source = str(Path(governance.__file__).resolve())
        child_code = (
            "import importlib.util, os, pathlib, sys; "
            "source = pathlib.Path(sys.argv[1]); root = pathlib.Path(sys.argv[2]); "
            "spec = importlib.util.spec_from_file_location('e36_governance', source); "
            "module = importlib.util.module_from_spec(spec); "
            "sys.modules[spec.name] = module; spec.loader.exec_module(module); "
            "module.ROOT = root; os.chdir(root); "
            "sys.argv = ['validate_poc_recovery_governance.py']; "
            "raise SystemExit(module.main())"
        )
        self.assertEqual(
            harness_tree,
            governance.git_output("rev-parse", f"{harness_head}^{{tree}}"),
        )

        with tempfile.TemporaryDirectory(prefix="dora-rec-i3-e36-profile-") as temporary:
            parent = Path(temporary)
            repo = parent / "repo"
            governance.test_git(
                parent,
                "clone",
                "--shared",
                "--no-checkout",
                str(governance.ROOT),
                str(repo),
            )
            governance.test_git(repo, "checkout", "-q", "-B", harness_branch, harness_head)
            child_environment = os.environ.copy()
            for key in tuple(child_environment):
                if key.startswith("GITHUB_") or key == "RUNNER_TEMP":
                    child_environment.pop(key)
            local = self.run_bounded_validator_child(
                cwd=repo,
                environment=child_environment,
                command=[sys.executable, "-c", child_code, validator_source, str(repo)],
            )
            self.assertEqual(0, local.returncode, local.stdout + local.stderr)
            self.assertIn("exact E36-GAPI harness", local.stdout)

            correction_root = Path(governance.__file__).resolve().parents[1]
            correction_head = self.integrated_correction_head()
            governance.test_git(repo, "checkout", "-q", "-B", correction_branch, correction_head)
            governance.test_git(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "merge",
                "--no-ff",
                "--no-edit",
                harness_head,
            )
            merge_head = governance.test_git_text(repo, "rev-parse", "HEAD")
            governance.test_git(repo, "checkout", "--detach", "-q", merge_head)
            runner_temp = parent / "runner-temp"
            runner_temp.mkdir()
            event_path = runner_temp / "event.json"
            pull_request_number = 67
            event_path.write_text(
                json.dumps(
                    {
                        "number": pull_request_number,
                        "repository": {"full_name": governance.GITHUB_REPOSITORY},
                        "pull_request": {
                            "number": pull_request_number,
                            "merge_commit_sha": merge_head,
                            "draft": True,
                            "state": "open",
                            "merged": False,
                            "head": {
                                "ref": harness_branch,
                                "sha": harness_head,
                                "repo": {"full_name": governance.GITHUB_REPOSITORY},
                            },
                            "base": {
                                "ref": correction_branch,
                                "sha": correction_head,
                                "repo": {"full_name": governance.GITHUB_REPOSITORY},
                            },
                        },
                    }
                ),
                encoding="utf-8",
            )
            child_environment.update(
                {
                    "GITHUB_EVENT_NAME": "pull_request",
                    "GITHUB_REPOSITORY": governance.GITHUB_REPOSITORY,
                    "GITHUB_WORKSPACE": str(repo.resolve()),
                    "RUNNER_TEMP": str(runner_temp.resolve()),
                    "GITHUB_EVENT_PATH": str(event_path.resolve()),
                    "GITHUB_HEAD_REF": harness_branch,
                    "GITHUB_BASE_REF": correction_branch,
                    "GITHUB_REF": f"refs/pull/{pull_request_number}/merge",
                    "GITHUB_SHA": merge_head,
                }
            )
            stacked = self.run_bounded_validator_child(
                cwd=repo,
                environment=child_environment,
                command=[sys.executable, "-c", child_code, validator_source, str(repo)],
            )
            self.assertEqual(0, stacked.returncode, stacked.stdout + stacked.stderr)
            self.assertIn("exact E36-GAPI harness", stacked.stdout)

            correction_tree = subprocess.run(
                ["git", "show", "-s", "--format=%T", correction_head],
                cwd=correction_root,
                check=True,
                capture_output=True,
                text=True,
            ).stdout.strip()
            integrated_correction = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                correction_tree,
                "-p",
                integrated_main,
                input_data=b"synthetic integrated correction\n",
            )
            governance.test_git(repo, "checkout", "-q", "-B", "main", integrated_correction)
            governance.test_git(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "merge",
                "--no-ff",
                "--no-edit",
                harness_head,
            )
            main_base_merge = governance.test_git_text(repo, "rev-parse", "HEAD")
            governance.test_git(repo, "checkout", "--detach", "-q", main_base_merge)
            event_path.write_text(
                json.dumps(
                    {
                        "number": pull_request_number,
                        "repository": {"full_name": governance.GITHUB_REPOSITORY},
                        "pull_request": {
                            "number": pull_request_number,
                            "merge_commit_sha": main_base_merge,
                            "draft": True,
                            "state": "open",
                            "merged": False,
                            "head": {
                                "ref": harness_branch,
                                "sha": harness_head,
                                "repo": {"full_name": governance.GITHUB_REPOSITORY},
                            },
                            "base": {
                                "ref": "main",
                                "sha": integrated_correction,
                                "repo": {"full_name": governance.GITHUB_REPOSITORY},
                            },
                        },
                    }
                ),
                encoding="utf-8",
            )
            child_environment.update(
                {
                    "GITHUB_BASE_REF": "main",
                    "GITHUB_SHA": main_base_merge,
                }
            )
            main_base = self.run_bounded_validator_child(
                cwd=repo,
                environment=child_environment,
                command=[sys.executable, "-c", child_code, validator_source, str(repo)],
            )
            self.assertEqual(0, main_base.returncode, main_base.stdout + main_base.stderr)
            self.assertIn("exact E36-GAPI harness", main_base.stdout)

            unrelated = replace(
                governance.collect_recovery_lifecycle_identity(),
                head=harness_head,
                branch="codex/unrelated-descendant",
                github_pull_request_context=None,
            )
            self.assertFalse(governance.rec_i3_squash_main_candidate(unrelated))

    def test_e36_gapi_squash_main_is_exact_and_terminal(self) -> None:
        integrated_correction = "02f71246e4024ce6a8246c853c2f08d989d99b01"
        harness_head = "7a7036513f2eb460ed72136e1784d712c4aae42d"
        validator_source = str(Path(governance.__file__).resolve())
        child_code = (
            "import importlib.util, os, pathlib, sys; "
            "source = pathlib.Path(sys.argv[1]); root = pathlib.Path(sys.argv[2]); "
            "spec = importlib.util.spec_from_file_location('e36_main_governance', source); "
            "module = importlib.util.module_from_spec(spec); "
            "sys.modules[spec.name] = module; spec.loader.exec_module(module); "
            "module.ROOT = root; os.chdir(root); "
            "sys.argv = ['validate_poc_recovery_governance.py']; "
            "raise SystemExit(module.main())"
        )
        with tempfile.TemporaryDirectory(prefix="dora-rec-i3-e36-main-") as temporary:
            parent = Path(temporary)
            repo = parent / "repo"
            governance.test_git(
                parent,
                "clone",
                "--shared",
                "--no-checkout",
                str(governance.ROOT),
                str(repo),
            )
            governance.test_git(repo, "checkout", "-q", "-B", "main", integrated_correction)
            governance.test_git(
                repo,
                "checkout",
                harness_head,
                "--",
                *governance.REC_I3_E36_GAPI_PATHS,
            )
            integrated_tree = governance.test_git_text(repo, "write-tree")
            integrated_harness = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                integrated_tree,
                "-p",
                integrated_correction,
                input_data=b"synthetic E36-GAPI squash main\n",
            )
            governance.test_git(repo, "checkout", "-q", "-B", "main", integrated_harness)
            child_environment = os.environ.copy()
            for key in tuple(child_environment):
                if key.startswith("GITHUB_") or key == "RUNNER_TEMP":
                    child_environment.pop(key)
            child_environment.update(
                {
                    "GITHUB_EVENT_NAME": "push",
                    "GITHUB_REPOSITORY": governance.GITHUB_REPOSITORY,
                    "GITHUB_WORKSPACE": str(repo.resolve()),
                    "GITHUB_REF": "refs/heads/main",
                    "GITHUB_SHA": integrated_harness,
                }
            )
            completed = self.run_bounded_validator_child(
                cwd=repo,
                environment=child_environment,
                command=[sys.executable, "-c", child_code, validator_source, str(repo)],
            )
            self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)
            self.assertIn("exact E36-GAPI harness", completed.stdout)

            later_descendant = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                integrated_tree,
                "-p",
                integrated_harness,
                input_data=b"synthetic later E36 descendant\n",
            )
            governance.test_git(repo, "checkout", "-q", "-B", "main", later_descendant)
            child_environment["GITHUB_SHA"] = later_descendant
            rejected = self.run_bounded_validator_child(
                cwd=repo,
                environment=child_environment,
                command=[sys.executable, "-c", child_code, validator_source, str(repo)],
            )
            self.assertNotEqual(0, rejected.returncode)
            self.assertNotIn("exact E36-GAPI harness validation passed", rejected.stdout)

            governance.test_git(repo, "checkout", "--detach", "-q", "-f", integrated_correction)
            governance.test_git(
                repo,
                "checkout",
                harness_head,
                "--",
                *governance.REC_I3_E36_GAPI_PATHS,
            )
            drift_path = repo / governance.REC_I3_E36_GAPI_PATHS[0]
            drift_path.write_bytes(drift_path.read_bytes() + b"\n// synthetic drift\n")
            governance.test_git(repo, "add", "--", governance.REC_I3_E36_GAPI_PATHS[0])
            drift_tree = governance.test_git_text(repo, "write-tree")
            drift_commit = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                drift_tree,
                "-p",
                integrated_correction,
                input_data=b"synthetic E36 blob drift\n",
            )
            self.assertFalse(
                governance.rec_i3_integrated_e36_gapi_commit_candidate(
                    drift_commit,
                    root=repo,
                )
            )

            governance.test_git(repo, "checkout", "--detach", "-q", "-f", integrated_correction)
            governance.test_git(
                repo,
                "checkout",
                harness_head,
                "--",
                *governance.REC_I3_E36_GAPI_PATHS,
            )
            governance.test_git(
                repo,
                "update-index",
                "--chmod=+x",
                governance.REC_I3_E36_GAPI_PATHS[0],
            )
            mode_tree = governance.test_git_text(repo, "write-tree")
            mode_commit = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                mode_tree,
                "-p",
                integrated_correction,
                input_data=b"synthetic E36 mode drift\n",
            )
            self.assertFalse(
                governance.rec_i3_integrated_e36_gapi_commit_candidate(
                    mode_commit,
                    root=repo,
                )
            )

    def test_harness_main_governance_transition_carries_exact_e36_squash(self) -> None:
        integrated_correction = "02f71246e4024ce6a8246c853c2f08d989d99b01"
        harness_head = "7a7036513f2eb460ed72136e1784d712c4aae42d"
        governance_root = Path(governance.__file__).resolve().parents[1]
        validator_source = str(Path(governance.__file__).resolve())
        child_code = (
            "import importlib.util, os, pathlib, sys; "
            "source = pathlib.Path(sys.argv[1]); root = pathlib.Path(sys.argv[2]); "
            "spec = importlib.util.spec_from_file_location('governance_transition', source); "
            "module = importlib.util.module_from_spec(spec); "
            "sys.modules[spec.name] = module; spec.loader.exec_module(module); "
            "module.ROOT = root; os.chdir(root); "
            "sys.argv = ['validate_poc_recovery_governance.py']; "
            "raise SystemExit(module.main())"
        )
        with tempfile.TemporaryDirectory(prefix="dora-rec-i3-governance-transition-") as temporary:
            parent = Path(temporary)
            repo = parent / "repo"
            governance.test_git(
                parent,
                "clone",
                "--shared",
                "--no-checkout",
                str(governance_root),
                str(repo),
            )
            governance.test_git(repo, "checkout", "--detach", "-q", integrated_correction)
            first_path, second_path = governance.REC_I3_SQUASH_MAIN_CORRECTION_PATHS
            (repo / first_path).write_bytes((governance_root / first_path).read_bytes())
            governance.test_git(
                repo,
                "add",
                "--",
                first_path,
            )
            intermediate_tree = governance.test_git_text(repo, "write-tree")
            intermediate_source = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                intermediate_tree,
                "-p",
                integrated_correction,
                input_data=b"synthetic harness-main governance intermediate\n",
            )
            governance.test_git(
                repo, "checkout", "--detach", "-q", "-f", intermediate_source
            )
            (repo / second_path).write_bytes((governance_root / second_path).read_bytes())
            governance.test_git(repo, "add", "--", second_path)
            governance_tree = governance.test_git_text(repo, "write-tree")
            governance_source = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                governance_tree,
                "-p",
                intermediate_source,
                input_data=b"synthetic harness-main governance source\n",
            )
            governance.test_git(
                repo,
                "checkout",
                "-q",
                "-B",
                governance.REC_I3_HARNESS_MAIN_GOVERNANCE_BRANCH,
                governance_source,
            )
            clean_environment = os.environ.copy()
            for key in tuple(clean_environment):
                if key.startswith("GITHUB_") or key == "RUNNER_TEMP":
                    clean_environment.pop(key)
            local = self.run_bounded_validator_child(
                cwd=repo,
                environment=clean_environment,
                command=[sys.executable, "-c", child_code, validator_source, str(repo)],
            )
            self.assertEqual(0, local.returncode, local.stdout + local.stderr)
            self.assertIn("squash-merged main", local.stdout)

            merge_head = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                governance_tree,
                "-p",
                integrated_correction,
                "-p",
                governance_source,
                input_data=b"synthetic harness-main governance pull request\n",
            )
            governance.test_git(repo, "checkout", "--detach", "-q", merge_head)
            runner_temp = parent / "runner-temp"
            runner_temp.mkdir()
            event_path = runner_temp / "event.json"
            governance.write_test_pull_request_event(
                event_path,
                number=69,
                head_ref=governance.REC_I3_HARNESS_MAIN_GOVERNANCE_BRANCH,
                head_sha=governance_source,
                base_sha=integrated_correction,
                merge_sha=merge_head,
            )
            pull_request_environment = clean_environment.copy()
            pull_request_environment.update(
                {
                    "GITHUB_EVENT_NAME": "pull_request",
                    "GITHUB_REPOSITORY": governance.GITHUB_REPOSITORY,
                    "GITHUB_WORKSPACE": str(repo.resolve()),
                    "RUNNER_TEMP": str(runner_temp.resolve()),
                    "GITHUB_EVENT_PATH": str(event_path.resolve()),
                    "GITHUB_HEAD_REF": governance.REC_I3_HARNESS_MAIN_GOVERNANCE_BRANCH,
                    "GITHUB_BASE_REF": governance.GITHUB_BASE_BRANCH,
                    "GITHUB_REF": "refs/pull/69/merge",
                    "GITHUB_SHA": merge_head,
                }
            )
            pull_request = self.run_bounded_validator_child(
                cwd=repo,
                environment=pull_request_environment,
                command=[sys.executable, "-c", child_code, validator_source, str(repo)],
            )
            self.assertEqual(
                0,
                pull_request.returncode,
                pull_request.stdout + pull_request.stderr,
            )

            governance_main = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                governance_tree,
                "-p",
                integrated_correction,
                input_data=b"synthetic harness-main governance squash\n",
            )
            governance.test_git(repo, "checkout", "-q", "-B", "main", governance_main)
            push_environment = clean_environment.copy()
            push_environment.update(
                {
                    "GITHUB_EVENT_NAME": "push",
                    "GITHUB_REPOSITORY": governance.GITHUB_REPOSITORY,
                    "GITHUB_WORKSPACE": str(repo.resolve()),
                    "GITHUB_REF": "refs/heads/main",
                    "GITHUB_SHA": governance_main,
                }
            )
            integrated = self.run_bounded_validator_child(
                cwd=repo,
                environment=push_environment,
                command=[sys.executable, "-c", child_code, validator_source, str(repo)],
            )
            self.assertEqual(0, integrated.returncode, integrated.stdout + integrated.stderr)
            self.assertTrue(
                governance.rec_i3_integrated_governance_base_candidate(
                    governance_main,
                    root=repo,
                )
            )
            self.assertTrue(
                governance.rec_i3_harness_main_governance_commit_candidate(
                    governance_main,
                    root=repo,
                )
            )
            self.assertFalse(
                governance.rec_i3_harness_main_governance_commit_candidate(
                    integrated_correction,
                    root=repo,
                )
            )

            governance.test_git(
                repo,
                "checkout",
                harness_head,
                "--",
                *governance.REC_I3_E36_GAPI_PATHS,
            )
            harness_tree = governance.test_git_text(repo, "write-tree")
            harness_main = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                harness_tree,
                "-p",
                governance_main,
                input_data=b"synthetic E36-GAPI squash after governance\n",
            )
            governance.test_git(repo, "checkout", "-q", "-B", "main", harness_main)
            push_environment["GITHUB_SHA"] = harness_main
            harness = self.run_bounded_validator_child(
                cwd=repo,
                environment=push_environment,
                command=[sys.executable, "-c", child_code, validator_source, str(repo)],
            )
            self.assertEqual(0, harness.returncode, harness.stdout + harness.stderr)
            self.assertIn("exact E36-GAPI harness", harness.stdout)
            self.assertTrue(
                governance.rec_i3_integrated_e36_gapi_commit_candidate(
                    harness_main,
                    root=repo,
                )
            )

    def test_e36_reviewed_head_allows_only_exact_main_sync_wrapper(self) -> None:
        governance_base = "56d6ac509b4ddbee5ded5b99fcbb5c1315e52c3d"
        harness_head = "7a7036513f2eb460ed72136e1784d712c4aae42d"
        governance_root = Path(governance.__file__).resolve().parents[1]
        validator_source = str(Path(governance.__file__).resolve())
        child_code = (
            "import importlib.util, os, pathlib, sys; "
            "source = pathlib.Path(sys.argv[1]); root = pathlib.Path(sys.argv[2]); "
            "spec = importlib.util.spec_from_file_location('e36_sync_governance', source); "
            "module = importlib.util.module_from_spec(spec); "
            "sys.modules[spec.name] = module; spec.loader.exec_module(module); "
            "module.ROOT = root; os.chdir(root); "
            "sys.argv = ['validate_poc_recovery_governance.py']; "
            "raise SystemExit(module.main())"
        )
        with tempfile.TemporaryDirectory(prefix="dora-rec-i3-e36-sync-") as temporary:
            parent = Path(temporary)
            repo = parent / "repo"
            governance.test_git(
                parent,
                "clone",
                "--shared",
                "--no-checkout",
                str(governance_root),
                str(repo),
            )
            governance.test_git(repo, "checkout", "--detach", "-q", governance_base)
            for relative in governance.REC_I3_SQUASH_MAIN_CORRECTION_PATHS:
                path = repo / relative
                path.write_bytes(path.read_bytes() + b"\n# synthetic E36 sync governance\n")
            governance.test_git(
                repo,
                "add",
                "--",
                *governance.REC_I3_SQUASH_MAIN_CORRECTION_PATHS,
            )
            governance_tree = governance.test_git_text(repo, "write-tree")
            governance_successor = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                governance_tree,
                "-p",
                governance_base,
                input_data=b"synthetic E36 sync governance successor\n",
            )
            self.assertTrue(
                governance.rec_i3_integrated_governance_base_candidate(
                    governance_successor,
                    root=repo,
                )
            )

            governance.test_git(repo, "checkout", "--detach", "-q", governance_successor)
            governance.test_git(
                repo,
                "checkout",
                harness_head,
                "--",
                *governance.REC_I3_E36_GAPI_PATHS,
            )
            sync_tree = governance.test_git_text(repo, "write-tree")
            sync_head = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                sync_tree,
                "-p",
                harness_head,
                "-p",
                governance_successor,
                input_data=b"synthetic merge main into reviewed E36 head\n",
            )
            self.assertTrue(
                governance.rec_i3_e36_gapi_review_head_candidate(
                    sync_head,
                    governance_successor,
                    root=repo,
                )
            )

            wrong_parent_order = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                sync_tree,
                "-p",
                governance_successor,
                "-p",
                harness_head,
                input_data=b"synthetic wrong parent order\n",
            )
            self.assertFalse(
                governance.rec_i3_e36_gapi_review_head_candidate(
                    wrong_parent_order,
                    governance_successor,
                    root=repo,
                )
            )
            later_descendant = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                sync_tree,
                "-p",
                sync_head,
                input_data=b"synthetic later reviewed-head descendant\n",
            )
            self.assertFalse(
                governance.rec_i3_e36_gapi_review_head_candidate(
                    later_descendant,
                    governance_successor,
                    root=repo,
                )
            )
            governance.test_git(repo, "checkout", "--detach", "-q", sync_head)
            extra_path = repo / "synthetic-e36-sync-extra.txt"
            extra_path.write_text("extra\n", encoding="utf-8")
            governance.test_git(repo, "add", "--", extra_path.name)
            extra_tree = governance.test_git_text(repo, "write-tree")
            extra_head = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                extra_tree,
                "-p",
                harness_head,
                "-p",
                governance_successor,
                input_data=b"synthetic extra path\n",
            )
            self.assertFalse(
                governance.rec_i3_e36_gapi_review_head_candidate(
                    extra_head,
                    governance_successor,
                    root=repo,
                )
            )
            extra_path.unlink()
            governance.test_git(repo, "reset", "-q", sync_head)

            governance_pull_request = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                governance_tree,
                "-p",
                governance_base,
                "-p",
                governance_successor,
                input_data=b"synthetic sync-governance pull request\n",
            )
            governance.test_git(repo, "checkout", "--detach", "-q", governance_pull_request)
            runner_temp = parent / "runner-temp"
            runner_temp.mkdir()
            event_path = runner_temp / "event.json"
            governance.write_test_pull_request_event(
                event_path,
                number=71,
                head_ref=governance.REC_I3_HARNESS_SYNC_GOVERNANCE_BRANCH,
                head_sha=governance_successor,
                base_sha=governance_base,
                merge_sha=governance_pull_request,
            )
            clean_environment = os.environ.copy()
            for key in tuple(clean_environment):
                if key.startswith("GITHUB_") or key == "RUNNER_TEMP":
                    clean_environment.pop(key)
            governance_pr_environment = clean_environment.copy()
            governance_pr_environment.update(
                {
                    "GITHUB_EVENT_NAME": "pull_request",
                    "GITHUB_REPOSITORY": governance.GITHUB_REPOSITORY,
                    "GITHUB_WORKSPACE": str(repo.resolve()),
                    "RUNNER_TEMP": str(runner_temp.resolve()),
                    "GITHUB_EVENT_PATH": str(event_path.resolve()),
                    "GITHUB_HEAD_REF": governance.REC_I3_HARNESS_SYNC_GOVERNANCE_BRANCH,
                    "GITHUB_BASE_REF": governance.GITHUB_BASE_BRANCH,
                    "GITHUB_REF": "refs/pull/71/merge",
                    "GITHUB_SHA": governance_pull_request,
                }
            )
            governance_pr = self.run_bounded_validator_child(
                cwd=repo,
                environment=governance_pr_environment,
                command=[sys.executable, "-c", child_code, validator_source, str(repo)],
            )
            self.assertEqual(
                0,
                governance_pr.returncode,
                governance_pr.stdout + governance_pr.stderr,
            )

            governance_main = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                governance_tree,
                "-p",
                governance_base,
                input_data=b"synthetic sync-governance squash main\n",
            )
            self.assertTrue(
                governance.rec_i3_integrated_governance_base_candidate(
                    governance_main,
                    root=repo,
                )
            )
            governance.test_git(repo, "checkout", "--detach", "-q", governance_main)
            governance.test_git(
                repo,
                "checkout",
                harness_head,
                "--",
                *governance.REC_I3_E36_GAPI_PATHS,
            )
            synced_tree = governance.test_git_text(repo, "write-tree")
            synced_source = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                synced_tree,
                "-p",
                harness_head,
                "-p",
                governance_main,
                input_data=b"synthetic protected update of reviewed E36 head\n",
            )
            synced_pull_request = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                synced_tree,
                "-p",
                governance_main,
                "-p",
                synced_source,
                input_data=b"synthetic refreshed E36 pull request\n",
            )
            governance.test_git(repo, "checkout", "--detach", "-q", synced_pull_request)
            governance.write_test_pull_request_event(
                event_path,
                number=67,
                head_ref=governance.REC_I3_E36_GAPI_BRANCH,
                head_sha=synced_source,
                base_sha=governance_main,
                merge_sha=synced_pull_request,
                draft=False,
            )
            e36_pr_environment = governance_pr_environment.copy()
            e36_pr_environment.update(
                {
                    "GITHUB_HEAD_REF": governance.REC_I3_E36_GAPI_BRANCH,
                    "GITHUB_REF": "refs/pull/67/merge",
                    "GITHUB_SHA": synced_pull_request,
                }
            )
            e36_pr = self.run_bounded_validator_child(
                cwd=repo,
                environment=e36_pr_environment,
                command=[sys.executable, "-c", child_code, validator_source, str(repo)],
            )
            self.assertEqual(0, e36_pr.returncode, e36_pr.stdout + e36_pr.stderr)

            integrated_harness = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                synced_tree,
                "-p",
                governance_main,
                input_data=b"synthetic protected E36 squash main\n",
            )
            governance.test_git(repo, "checkout", "-q", "-B", "main", integrated_harness)
            push_environment = clean_environment.copy()
            push_environment.update(
                {
                    "GITHUB_EVENT_NAME": "push",
                    "GITHUB_REPOSITORY": governance.GITHUB_REPOSITORY,
                    "GITHUB_WORKSPACE": str(repo.resolve()),
                    "GITHUB_REF": "refs/heads/main",
                    "GITHUB_SHA": integrated_harness,
                }
            )
            e36_main = self.run_bounded_validator_child(
                cwd=repo,
                environment=push_environment,
                command=[sys.executable, "-c", child_code, validator_source, str(repo)],
            )
            self.assertEqual(0, e36_main.returncode, e36_main.stdout + e36_main.stderr)

    def test_squash_main_rejects_new_names_in_every_protected_change_layer(self) -> None:
        empty = {layer: [] for layer in ("committed", "staged", "unstaged", "untracked")}
        for layer in empty:
            changes = copy.deepcopy(empty)
            changes[layer] = [
                "android/poc/recovery/src/main/synthetic-protected-bypass.kt"
            ]
            with self.subTest(layer=layer), self.assertRaisesRegex(
                ValueError, "protected namespace"
            ):
                governance.validate_rec_i3_squash_main_protected_changes(
                    changes,
                    allowed_paths=set(governance.REC_I3_SQUASH_MAIN_CORRECTION_PATHS),
                )

    def test_squash_main_github_push_rejects_missing_workspace(self) -> None:
        integrated_main = "be37378ca88e0bd4aee1f2fe0c54362798bdef9d"
        with tempfile.TemporaryDirectory(prefix="dora-rec-i3-missing-workspace-") as temporary:
            parent = Path(temporary)
            repo = parent / "repo"
            governance.test_git(
                parent,
                "clone",
                "--shared",
                "--no-checkout",
                str(governance.ROOT),
                str(repo),
            )
            governance.test_git(repo, "checkout", "-q", "-B", "main", integrated_main)
            child_environment = os.environ.copy()
            for key in tuple(child_environment):
                if key.startswith("GITHUB_") or key == "RUNNER_TEMP":
                    child_environment.pop(key)
            child_environment.update(
                {
                    "GITHUB_EVENT_NAME": "push",
                    "GITHUB_REPOSITORY": governance.GITHUB_REPOSITORY,
                    "GITHUB_REF": "refs/heads/main",
                    "GITHUB_SHA": integrated_main,
                }
            )
            validator_source = str(Path(governance.__file__).resolve())
            child_code = (
                "import importlib.util, os, pathlib, sys; "
                "source = pathlib.Path(sys.argv[1]); root = pathlib.Path(sys.argv[2]); "
                "spec = importlib.util.spec_from_file_location('workspace_governance', source); "
                "module = importlib.util.module_from_spec(spec); "
                "sys.modules[spec.name] = module; spec.loader.exec_module(module); "
                "module.ROOT = root; os.chdir(root); "
                "sys.argv = ['validate_poc_recovery_governance.py']; "
                "raise SystemExit(module.main())"
            )
            rejected = self.run_bounded_validator_child(
                cwd=repo,
                environment=child_environment,
                command=[sys.executable, "-c", child_code, validator_source, str(repo)],
            )
            self.assertNotEqual(0, rejected.returncode)
            self.assertIn(
                "REC-I3 squash-main GitHub push identity drift",
                rejected.stdout + rejected.stderr,
            )

    def test_required_regular_file_rejects_missing_index_entry(self) -> None:
        with tempfile.TemporaryDirectory(prefix="dora-rec-i3-missing-index-") as temporary:
            parent = Path(temporary)
            repo = parent / "repo"
            governance.test_git(
                parent,
                "clone",
                "--shared",
                str(governance.ROOT),
                str(repo),
            )
            relative = "tools/validate_poc_recovery_governance.py"
            governance.test_git(repo, "rm", "--cached", "--", relative)
            original_root = governance.ROOT
            try:
                governance.ROOT = repo
                with self.assertRaisesRegex(ValueError, "non-regular Git entry"):
                    governance.validate_rec_i3_regular_file(relative)
            finally:
                governance.ROOT = original_root

    def test_exact_v08_profile_dispatch_is_preserved(self) -> None:
        lifecycle = self.local_result_boundary_lifecycle()
        with patch.object(governance, "validate_rec_i3_result_boundary") as validate:
            self.assertTrue(governance.validate_current_rec_i3_successor(lifecycle))
            validate.assert_called_once_with(lifecycle)

    def test_retained_validator_child_is_bounded_and_pr_context_cannot_reenter(self) -> None:
        current = governance.collect_recovery_lifecycle_identity()
        with (
            patch.dict(
                os.environ,
                {
                    "GITHUB_EVENT_NAME": "pull_request",
                    "GITHUB_HEAD_REF": governance.REC_I3_OBSERVABLE_CONTROLLER_BRANCH,
                },
                clear=False,
            ),
            patch.object(
                governance,
                "collect_recovery_lifecycle_identity",
                return_value=replace(current, github_pull_request_context=None),
            ),
            patch.object(
                tempfile,
                "TemporaryDirectory",
                side_effect=AssertionError("recursive local orchestration entered"),
            ),
            self.assertRaisesRegex(AssertionError, "verified pull_request context"),
        ):
            self.test_local_v08_fixture_restores_verified_pull_request_source_head()

        with tempfile.TemporaryDirectory(
            prefix="dora-rec-i3-retained-timeout-"
        ) as temporary:
            parent = Path(temporary)
            escaped_marker = parent / "descendant-escaped.txt"
            descendant_code = (
                "import time; from pathlib import Path; time.sleep(1.0); "
                f"Path({str(escaped_marker)!r}).write_text('escaped', encoding='utf-8')"
            )
            parent_code = (
                "import subprocess, sys, time; "
                f"subprocess.Popen([sys.executable, '-c', {descendant_code!r}]); "
                "time.sleep(60)"
            )
            with self.assertRaisesRegex(
                AssertionError, "validator child timed out after 0.2 seconds"
            ):
                self.run_bounded_validator_child(
                    cwd=parent,
                    environment=os.environ.copy(),
                    timeout_seconds=0.2,
                    command=[sys.executable, "-c", parent_code],
                )
            time.sleep(1.2)
            self.assertFalse(escaped_marker.exists())

        with tempfile.TemporaryDirectory(
            prefix="dora-rec-i3-retained-nonzero-"
        ) as temporary:
            failed_parent = Path(temporary)
            escaped_marker = failed_parent / "nonzero-descendant-escaped.txt"
            descendant_code = (
                "import time; from pathlib import Path; time.sleep(1.0); "
                f"Path({str(escaped_marker)!r}).write_text('escaped', encoding='utf-8')"
            )
            parent_code = (
                "import subprocess, sys; "
                f"subprocess.Popen([sys.executable, '-c', {descendant_code!r}], "
                "stdin=subprocess.DEVNULL, stdout=subprocess.DEVNULL, "
                "stderr=subprocess.DEVNULL, close_fds=True); "
                "raise SystemExit(7)"
            )
            failed = self.run_bounded_validator_child(
                cwd=failed_parent,
                environment=os.environ.copy(),
                timeout_seconds=5.0,
                command=[sys.executable, "-c", parent_code],
            )
            self.assertEqual(7, failed.returncode)
            time.sleep(1.2)
            self.assertFalse(escaped_marker.exists())
        self.assertFalse(failed_parent.exists())

    def test_local_v08_fixture_restores_verified_pull_request_source_head(self) -> None:
        current = governance.collect_recovery_lifecycle_identity()
        pull_request = current.github_pull_request_context
        if os.environ.get("GITHUB_HEAD_REF") or os.environ.get("GITHUB_EVENT_NAME") == "pull_request":
            self.assertIsNotNone(
                pull_request,
                "retained validator child requires verified pull_request context",
            )
        if pull_request is not None:
            parents = tuple(
                governance.git_output("show", "-s", "--format=%P", current.head).split()
            )
            self.assertEqual((pull_request.base_sha, pull_request.head_sha), parents)
            self.assertNotEqual(current.head, pull_request.head_sha)
            self.assertEqual(
                governance.git_output("rev-parse", f"{current.head}^{{tree}}"),
                governance.git_output("rev-parse", f"{pull_request.head_sha}^{{tree}}"),
            )
            self.assertEqual(
                "",
                governance.git_output(
                    "rev-list",
                    "--min-parents=2",
                    f"{governance.REC_I3_OBSERVABLE_CONTROLLER_BASE}..{pull_request.head_sha}",
                ),
            )

            simulated = self.local_result_boundary_lifecycle()
            self.assertEqual(pull_request.head_sha, simulated.head)
            self.assertIsNone(simulated.github_pull_request_context)

            wrong_event_path = Path(os.environ["RUNNER_TEMP"]) / "wrong-head-event.json"
            governance.write_test_pull_request_event(
                wrong_event_path,
                number=pull_request.number,
                head_ref=pull_request.head_ref,
                head_sha=pull_request.base_sha,
                base_sha=pull_request.base_sha,
                merge_sha=pull_request.merge_sha,
                draft=pull_request.draft,
                state=pull_request.state,
                merged=pull_request.merged,
            )
            wrong_environment = os.environ.copy()
            wrong_environment["GITHUB_EVENT_PATH"] = str(wrong_event_path.resolve())
            rejected = self.run_bounded_validator_child(
                cwd=governance.ROOT,
                environment=wrong_environment,
            )
            self.assertNotEqual(0, rejected.returncode)
            self.assertIn(
                "GitHub merge-ref parent topology mismatch",
                rejected.stdout + rejected.stderr,
            )

            with self.assertRaisesRegex(
                ValueError, "REC-I3 result-boundary governance history must be linear"
            ):
                governance.validate_rec_i3_result_boundary(
                    replace(simulated, head=current.head)
                )
            print("PASS retained observable-controller merge-ref source-head restoration")
            return

        source_head = current.head
        main_head = governance.git_output("rev-parse", "refs/remotes/origin/main")
        verified_base = governance.git_output("merge-base", main_head, source_head)
        self.assertEqual(main_head, verified_base)
        with tempfile.TemporaryDirectory(prefix="dora-rec-i3-retained-merge-ref-") as temporary:
            parent = Path(temporary)
            repo = parent / "repo"
            governance.test_git(
                parent,
                "clone",
                "--shared",
                "--no-checkout",
                str(governance.ROOT),
                str(repo),
            )
            source_tree = governance.test_git_text(
                repo, "rev-parse", f"{source_head}^{{tree}}"
            )
            merge_head = governance.test_git_text(
                repo,
                "-c",
                "user.name=Dora Validator Test",
                "-c",
                "user.email=dora-validator@example.invalid",
                "commit-tree",
                source_tree,
                "-p",
                verified_base,
                "-p",
                source_head,
                input_data=b"retained synthetic GitHub merge ref\n",
            )
            governance.test_git(repo, "checkout", "--detach", "-q", merge_head)

            runner_temp = parent / "runner-temp"
            runner_temp.mkdir()
            event_path = runner_temp / "event.json"
            pull_request_number = 66
            governance.write_test_pull_request_event(
                event_path,
                number=pull_request_number,
                head_ref=governance.REC_I3_OBSERVABLE_CONTROLLER_BRANCH,
                head_sha=source_head,
                base_sha=verified_base,
                merge_sha=merge_head,
                draft=False,
            )
            child_environment = os.environ.copy()
            for key in tuple(child_environment):
                if key.startswith("GITHUB_") or key == "RUNNER_TEMP":
                    child_environment.pop(key)
            child_environment.update({
                "GITHUB_EVENT_NAME": "pull_request",
                "GITHUB_REPOSITORY": governance.GITHUB_REPOSITORY,
                "GITHUB_WORKSPACE": str(repo.resolve()),
                "RUNNER_TEMP": str(runner_temp.resolve()),
                "GITHUB_EVENT_PATH": str(event_path.resolve()),
                "GITHUB_HEAD_REF": governance.REC_I3_OBSERVABLE_CONTROLLER_BRANCH,
                "GITHUB_BASE_REF": governance.GITHUB_BASE_BRANCH,
                "GITHUB_REF": f"refs/pull/{pull_request_number}/merge",
                "GITHUB_SHA": merge_head,
            })
            completed = self.run_bounded_validator_child(
                cwd=repo,
                environment=child_environment,
            )
            self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)
            self.assertIn(
                "PASS retained observable-controller merge-ref source-head restoration",
                completed.stdout,
            )

    def test_v08_profile_rejects_current_observable_pull_request_identity(self) -> None:
        lifecycle = self.local_result_boundary_lifecycle()
        current_pull_request = governance.GitHubPullRequestContext(
            repository=governance.GITHUB_REPOSITORY,
            head_repository=governance.GITHUB_REPOSITORY,
            head_ref=governance.REC_I3_OBSERVABLE_CONTROLLER_BRANCH,
            head_sha=lifecycle.head,
            base_ref=governance.GITHUB_BASE_BRANCH,
            base_sha=governance.REC_I3_OBSERVABLE_CONTROLLER_BASE,
            merge_ref="refs/pull/66/merge",
            merge_sha=lifecycle.head,
            number=66,
            draft=False,
            state="open",
            merged=False,
        )
        with self.assertRaisesRegex(ValueError, "result-boundary pull_request identity drift"):
            governance.validate_rec_i3_result_boundary(
                replace(lifecycle, github_pull_request_context=current_pull_request)
            )

    def test_exact_v08_profile_rejects_changed_v07_worktree_blob(self) -> None:
        lifecycle = self.local_result_boundary_lifecycle()
        target = next(iter(governance.REC_I3_RESULT_BOUNDARY_V07_SHA256))
        original = governance.sha256
        with (
            patch.object(
                governance,
                "sha256",
                side_effect=lambda relative: "0" * 64 if relative == target else original(relative),
            ),
            patch.object(governance, "validate_rec_i3_result_boundary_delta"),
            patch.object(governance, "validate_rec_i3_regular_file"),
            self.assertRaisesRegex(ValueError, "immutable v0.7 blob changed"),
        ):
            governance.validate_rec_i3_result_boundary(lifecycle)

    def test_main_dispatches_observable_controller_before_v08_and_legacy(self) -> None:
        lifecycle = replace(
            governance.collect_recovery_lifecycle_identity(),
            branch=governance.REC_I3_OBSERVABLE_CONTROLLER_BRANCH,
        )
        with (
            patch.object(governance, "collect_recovery_lifecycle_identity", return_value=lifecycle),
            patch.object(governance, "validate_rec_i3_observable_controller") as validate,
            patch.object(governance, "validate_rec_i3_result_boundary_fast_path") as old,
            patch.object(governance, "read_json", side_effect=AssertionError("legacy read")),
            patch.object(sys, "argv", ["validate_poc_recovery_governance.py"]),
        ):
            self.assertEqual(0, governance.main())
        validate.assert_called_once_with(lifecycle)
        old.assert_not_called()

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
            patch.object(
                governance,
                "collect_recovery_lifecycle_identity",
                return_value=self.local_result_boundary_lifecycle(),
            ),
            patch.object(governance, "read_json", side_effect=legacy_absent),
            patch.object(governance, "read_text", side_effect=legacy_text_absent),
            patch.object(governance, "collect_post_merge_changes", side_effect=clean_committed_profile),
            patch.object(governance, "validate_rec_i3_result_boundary_delta"),
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
            patch.object(
                governance,
                "collect_recovery_lifecycle_identity",
                return_value=self.local_result_boundary_lifecycle(),
            ),
            patch.object(governance, "collect_post_merge_changes", side_effect=clean_committed_profile),
            patch.object(governance, "validate_rec_i3_result_boundary_delta"),
            patch.object(governance, "read_json", side_effect=mutated_gate),
            patch.object(sys, "argv", ["validate_poc_recovery_governance.py"]),
            self.assertRaisesRegex(ValueError, "authority"),
        ):
            governance.main()

        target = next(iter(governance.REC_I3_RESULT_BOUNDARY_V07_SHA256))
        original_sha = governance.sha256
        with (
            patch.object(
                governance,
                "collect_recovery_lifecycle_identity",
                return_value=self.local_result_boundary_lifecycle(),
            ),
            patch.object(governance, "collect_post_merge_changes", side_effect=clean_committed_profile),
            patch.object(governance, "validate_rec_i3_result_boundary_delta"),
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
