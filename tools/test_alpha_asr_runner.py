"""Synthetic Stage-0 runner tests; never opens a model, corpus or device."""
import copy
from contextlib import closing
import hashlib
import importlib.util
import json
from pathlib import Path
import sqlite3
import tempfile
import unittest

import alpha_asr_eval_text_contract as frozen


class RunnerTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(importlib.util.find_spec("alpha_asr_runner"),
                             "bounded runner implementation missing")
        import alpha_asr_runner as runner
        self.r = runner
        self.temp = tempfile.TemporaryDirectory(prefix="dora-asr-synthetic-")
        self.addCleanup(self.temp.cleanup)
        self.db = Path(self.temp.name) / "attempts.sqlite"
        self.native = "a" * 64
        self.request = runner.request_shell(self.native, "sample-0000000000000001", "en")
        self.store = runner.AttemptStore(self.db)
        self.addCleanup(self.store.close)
        self.clock = Clock()
        self.engine = FakeEngine()

    def run_request(self, request=None, engine=None, cancelled=lambda: False):
        return self.r.run(request or self.request, self.native, self.store,
                          engine or self.engine, cancelled=cancelled,
                          monotonic=self.clock.now, sleep=self.clock.sleep)

    def reject(self, request):
        with self.assertRaises(self.r.RunnerError):
            self.run_request(request)
        self.assertEqual(0, self.engine.calls, "identity rejected after engine invocation")
        self.assertEqual(0, self.store.count())

    def test_valid_request_success_persisted_without_scoring(self):
        result = self.run_request()
        self.assertEqual("SUCCESS", result["outcome"])
        self.assertEqual("FINISHED", result["state"])
        self.assertEqual(1, result["request"]["attemptOrdinal"])
        self.assertEqual("NOT_RUN", result["evaluationResult"]["executionAttemptState"])
        self.assertEqual(0, result["evaluationResult"]["attemptOrdinal"])
        self.assertIsNone(result["evaluationResult"]["normalizedWerContribution"])
        frozen.validate_result(result["evaluationResult"])
        self.assertEqual(1, self.store.count())
        self.assertEqual(1, self.engine.calls)

    def test_model_sha_mismatch(self):
        self.request["model"]["sha256"] = "b" * 64
        self.reject(self.request)

    def test_model_size_mismatch(self):
        self.request["model"]["bytes"] += 1
        self.reject(self.request)

    def test_runtime_mismatch(self):
        self.request["runtimeCommit"] = "b" * 40
        self.reject(self.request)

    def test_native_candidate_mismatch(self):
        self.request["nativeCandidateSha256"] = "b" * 64
        self.reject(self.request)

    def test_profile_mismatch(self):
        self.request["profile"]["normalizationVersion"] = "unknown"
        self.reject(self.request)

    def test_locale_mismatch(self):
        self.request["locale"] = "EN"
        self.reject(self.request)

    def test_case_id_malformed(self):
        self.request["caseId"] = "../PRIVATE_SENTINEL"
        self.reject(self.request)

    def test_input_identity_malformed_or_wrong(self):
        for value in (None, "z" * 64, "b" * 64):
            request = copy.deepcopy(self.request)
            request["input"]["sha256"] = value
            self.reject(request)

    def test_unknown_missing_and_wrong_type_fields_at_every_level(self):
        for path in ((), ("model",), ("input",), ("timeout",), ("cancellation",), ("profile",)):
            for mutation in ("unknown", "missing", "mistyped"):
                request = copy.deepcopy(self.request)
                area = request
                for key in path:
                    area = area[key]
                if mutation == "unknown":
                    area["PRIVATE_SENTINEL"] = True
                elif mutation == "missing":
                    del area[next(iter(area))]
                else:
                    area[next(iter(area))] = []
                with self.subTest(path=path, mutation=mutation):
                    self.reject(request)

    def test_boolean_is_not_integer(self):
        for key in ("attemptOrdinal",):
            self.request[key] = True
            self.reject(self.request)

    def test_native_probe_operation_is_explicit_and_closed(self):
        self.request["operation"] = "LIFECYCLE_HOLD_ONLY"
        self.r.validate_request(self.request, self.native)
        self.request["operation"] = "INFERENCE"
        self.reject(self.request)

    def test_attempt_sequencing_and_failed_attempt_retention(self):
        self.request["attemptOrdinal"] = 2
        self.reject(self.request)
        self.request["attemptOrdinal"] = 1
        first = self.run_request(engine=FakeEngine("ENGINE_FAILURE"))
        self.request["attemptOrdinal"] = 2
        second = self.run_request()
        self.assertEqual("ENGINE_FAILURE", first["outcome"])
        self.assertEqual("SUCCESS", second["outcome"])
        self.assertEqual(2, self.store.count())

    def test_exact_replay_never_calls_engine_twice(self):
        first = self.run_request()
        self.assertEqual(first, self.run_request())
        self.assertEqual(1, self.engine.calls)

    def test_changed_replay_rejected(self):
        self.run_request()
        self.request["locale"] = "ru"
        with self.assertRaisesRegex(self.r.RunnerError, "REPLAY_MISMATCH"):
            self.run_request()
        self.assertEqual(1, self.engine.calls)

    def test_cancellation_before_start(self):
        self.request["cancellation"]["requested"] = True
        result = self.run_request()
        self.assertEqual("CANCELLED", result["outcome"])
        self.assertEqual(0, self.engine.calls)
        self.assertEqual("RUNNER_FAILURE", result["evaluationResult"]["errorCategory"])

    def test_cancellation_during_execution_terminates_engine(self):
        engine = FakeEngine(pending=True)
        result = self.run_request(engine=engine, cancelled=lambda: self.clock.value >= 0.02)
        self.assertEqual("CANCELLED", result["outcome"])
        self.assertEqual(["CANCELLED"], engine.session.stops)

    def test_timeout_terminates_engine(self):
        engine = FakeEngine(pending=True)
        self.request["timeout"]["milliseconds"] = 20
        result = self.run_request(engine=engine)
        self.assertEqual("TIMEOUT", result["outcome"])
        self.assertEqual(["TIMEOUT"], engine.session.stops)
        self.assertEqual("RUNNER_TIMEOUT", result["evaluationResult"]["errorCategory"])

    def test_model_load_failure(self):
        self.assert_failure("MODEL_LOAD_FAILURE", "FAILED", "RUNNER_FAILURE")

    def test_engine_failure(self):
        self.assert_failure("ENGINE_FAILURE", "FAILED", "RUNNER_FAILURE")

    def test_input_failure(self):
        self.assert_failure("INPUT_FAILURE", "INVALID_INPUT", "INPUT_BINDING_MISMATCH")

    def assert_failure(self, outcome, state, code):
        result = self.run_request(engine=FakeEngine(outcome))
        self.assertEqual(outcome, result["outcome"])
        self.assertEqual(state, result["evaluationResult"]["executionAttemptState"])
        self.assertEqual(code, result["evaluationResult"]["errorCategory"])
        self.assertIsNone(result["evaluationResult"]["oracleCounts"])
        self.assertIsNone(result["evaluationResult"]["normalizedWerContribution"])
        frozen.validate_result(result["evaluationResult"])

    def test_native_exception_content_never_persisted(self):
        self.engine.error = RuntimeError("PRIVATE_SENTINEL /private/model")
        result = self.run_request()
        self.assertEqual("ENGINE_FAILURE", result["outcome"])
        self.assertNotIn("PRIVATE_SENTINEL", self.r.canonical(result))
        self.assertNotIn(b"PRIVATE_SENTINEL", self.db.read_bytes())

    def test_unknown_engine_outcome_fails_closed(self):
        result = self.run_request(engine=FakeEngine("PRIVATE_SENTINEL"))
        self.assertEqual("ENGINE_FAILURE", result["outcome"])

    def test_persistence_failure_prevents_engine_start(self):
        self.store.close()
        with self.assertRaisesRegex(self.r.RunnerError, "PERSISTENCE_FAILURE"):
            self.run_request()
        self.assertEqual(0, self.engine.calls)

    def test_final_persistence_failure_cannot_report_success(self):
        original = self.store.finish
        def fail(*args):
            raise self.r.RunnerError("PERSISTENCE_FAILURE")
        self.store.finish = fail
        with self.assertRaisesRegex(self.r.RunnerError, "PERSISTENCE_FAILURE"):
            self.run_request()
        self.store.finish = original
        self.store.close()
        self.store = self.r.AttemptStore(self.db)
        self.addCleanup(self.store.close)
        result = self.run_request()
        self.assertEqual("ENGINE_FAILURE", result["outcome"])
        self.assertEqual(1, self.engine.calls)

    def test_restart_reopen_completed_attempt(self):
        first = self.run_request()
        self.store.close()
        self.store = self.r.AttemptStore(self.db)
        self.addCleanup(self.store.close)
        self.assertEqual(first, self.run_request())
        self.assertEqual(1, self.engine.calls)

    def test_no_double_finalization(self):
        first = self.run_request()
        with self.assertRaisesRegex(self.r.RunnerError, "ALREADY_FINALIZED"):
            self.store.finish(self.request, "ENGINE_FAILURE")
        self.assertEqual(first, self.run_request())

    def test_store_schema_drift_rejected(self):
        self.store.close()
        with closing(sqlite3.connect(self.db)) as db:
            db.execute("PRAGMA user_version=99")
        with self.assertRaisesRegex(self.r.RunnerError, "PERSISTENCE_SCHEMA_MISMATCH"):
            self.r.AttemptStore(self.db)

    def test_same_version_wrong_sql_types_and_constraints_rejected(self):
        self.store.close()
        with closing(sqlite3.connect(self.db)) as db:
            db.execute("DROP TABLE attempts")
            db.execute("CREATE TABLE attempts (case_id TEXT, ordinal TEXT, request TEXT, state TEXT, result TEXT)")
            db.commit()
        with self.assertRaisesRegex(self.r.RunnerError, "PERSISTENCE_SCHEMA_MISMATCH"):
            opened = self.r.AttemptStore(self.db)
            self.addCleanup(opened.close)

    def test_concurrent_store_owner_rejected(self):
        with self.assertRaisesRegex(self.r.RunnerError, "STORE_BUSY"):
            self.r.AttemptStore(self.db)

    def test_public_projection_has_only_allowlisted_summary(self):
        private = self.run_request()
        public = self.r.public_projection([private])
        self.assertEqual({"SUCCESS": 1}, public["outcomeCounts"])
        text = self.r.canonical(public)
        for forbidden in ("caseId", "sample-", "attemptOrdinal", "input", "evaluationResult", "PRIVATE_SENTINEL"):
            self.assertNotIn(forbidden, text)
        self.assertEqual("NOT_RUN", public["wer"])

    def test_canonical_serialization_and_duplicate_json_keys(self):
        self.assertEqual('{"a":1,"b":2}', self.r.canonical({"b": 2, "a": 1}))
        self.assertEqual(self.request, self.r.parse_request(self.r.canonical(self.request)))
        with self.assertRaises(self.r.RunnerError):
            self.r.parse_request('{"a":1,"a":2}')

    def test_interrupted_attempt_reopens_as_failure_without_execution(self):
        self.store.begin(self.request)
        self.store.close()
        self.store = self.r.AttemptStore(self.db)
        self.addCleanup(self.store.close)
        result = self.run_request()
        self.assertEqual("ENGINE_FAILURE", result["outcome"])
        self.assertEqual(0, self.engine.calls)

    def test_mutated_persisted_request_cannot_be_replayed(self):
        self.run_request()
        self.store.close()
        with closing(sqlite3.connect(self.db)) as db:
            db.execute("UPDATE attempts SET request='{}'")
            db.commit()
        with self.assertRaises(self.r.RunnerError):
            self.r.AttemptStore(self.db)

    def test_cancelled_timeout_failed_results_are_all_unscored(self):
        for outcome in ("CANCELLED", "TIMEOUT", "MODEL_LOAD_FAILURE", "INPUT_FAILURE", "ENGINE_FAILURE"):
            record = self.r.attempt_record(self.request, outcome)
            frozen.validate_result(record["evaluationResult"])
            self.assertIsNone(record["evaluationResult"]["oracleCounts"])
            self.assertIsNone(record["evaluationResult"]["normalizedWerContribution"])

    def test_delayed_start_must_recheck_deadline_before_native_launch(self):
        clock = self.clock
        class DelayedEngine(FakeEngine):
            def start(self, request, control):
                clock.sleep(1)
                control.check()
                return super().start(request, control)
        engine = DelayedEngine()
        self.request["timeout"]["milliseconds"] = 1
        self.assertEqual("TIMEOUT", self.run_request(engine=engine)["outcome"])
        self.assertEqual(0, engine.calls)


class ArtifactTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(importlib.util.find_spec("alpha_asr_android_preflight"),
                             "Android adapter missing")
        import alpha_asr_android_preflight as adapter
        self.a = adapter

    def test_missing_or_wrong_model_refused_before_native(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "synthetic.bin"
            for content in (None, b"wrong"):
                if content is not None:
                    path.write_bytes(content)
                with self.assertRaisesRegex(self.a.RunnerError, "MODEL_IDENTITY_MISMATCH"):
                    self.a.verify_model(path)

    def test_manifest_exact_runtime_target_and_native_inventory(self):
        manifest = self.a.manifest_shell({name: {"bytes": 1, "sha256": "a" * 64}
                                          for name in self.a.NATIVE_NAMES})
        self.a.validate_manifest(manifest)
        for mutation in ("runtime", "abi", "unknown", "bool", "missing_library"):
            changed = copy.deepcopy(manifest)
            if mutation == "runtime":
                changed["runtimeCommit"] = "b" * 40
            elif mutation == "abi":
                changed["abi"] = "x86_64"
            elif mutation == "unknown":
                changed["extra"] = True
            elif mutation == "bool":
                changed["files"]["libwhisper.so"]["bytes"] = True
            else:
                del changed["files"]["libggml.so"]
            with self.assertRaises(self.a.RunnerError):
                self.a.validate_manifest(changed)

    def test_actual_native_bytes_verified_against_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            paths = {}
            rows = {}
            for name in self.a.NATIVE_NAMES:
                path = Path(directory) / name
                path.write_bytes(b"synthetic artifact")
                paths[name] = path
                rows[name] = {"bytes": 18, "sha256": hashlib.sha256(b"synthetic artifact").hexdigest()}
            manifest = self.a.manifest_shell(rows)
            self.a.verify_native_files(paths, manifest)
            paths["libwhisper.so"].write_bytes(b"altered native")
            with self.assertRaisesRegex(self.a.RunnerError, "NATIVE_IDENTITY_MISMATCH"):
                self.a.verify_native_files(paths, manifest)

    def test_native_protocol_only_accepts_exact_content_free_receipt(self):
        self.assertEqual("SUCCESS", self.a.parse_native_receipt(0, b"DORA_ASR_BOUNDARY_V1\nMODEL_INIT_SUCCESS\n"))
        self.assertEqual("MODEL_LOAD_FAILURE", self.a.parse_native_receipt(2, b"DORA_ASR_BOUNDARY_V1\nMODEL_LOAD_FAILURE\n"))
        for rc, data in ((0, b"PRIVATE_SENTINEL"), (1, b"DORA_ASR_BOUNDARY_V1\nMODEL_INIT_SUCCESS\n"),
                         (0, b"DORA_ASR_BOUNDARY_V1\nMODEL_INIT_SUCCESS\nPRIVATE_SENTINEL")):
            self.assertEqual("ENGINE_FAILURE", self.a.parse_native_receipt(rc, data))

    def test_adapter_snapshots_manifest_and_paths(self):
        manifest = self.a.manifest_shell({name: {"bytes": 1, "sha256": "a" * 64}
                                          for name in self.a.NATIVE_NAMES})
        paths = {name: Path(name) for name in self.a.NATIVE_NAMES}
        adapter = self.a.AdbEngine("unused-adb", "unused-model", paths, manifest)
        manifest["files"]["libwhisper.so"]["sha256"] = "b" * 64
        paths["libwhisper.so"] = Path("replacement")
        self.assertEqual("a" * 64, adapter.manifest["files"]["libwhisper.so"]["sha256"])
        self.assertEqual(Path("libwhisper.so"), adapter.paths["libwhisper.so"])

    def test_remote_process_state_reads_content_not_proc_file_size(self):
        self.assertEqual("LIVE", self.a.parse_process_state(b"LIVE\n"))
        self.assertEqual("GONE", self.a.parse_process_state(b"GONE\n"))
        for value in (b"", b"PRIVATE_SENTINEL", b"GONE\nLIVE\n"):
            with self.assertRaises(self.a.RunnerError):
                self.a.parse_process_state(value)

    def test_windows_adb_crlf_native_receipts_remain_exact(self):
        self.assertEqual("SUCCESS", self.a.parse_native_receipt(
            0, b"DORA_ASR_BOUNDARY_V1\r\nMODEL_INIT_SUCCESS\r\n"))
        self.assertEqual("MODEL_LOAD_FAILURE", self.a.parse_native_receipt(
            2, b"DORA_ASR_BOUNDARY_V1\r\nMODEL_LOAD_FAILURE\r\n"))
        for output in (b"DORA_ASR_BOUNDARY_V1\nMODEL_INIT_SUCCESS\r\n",
                       b"DORA_ASR_BOUNDARY_V1\rMODEL_INIT_SUCCESS\r",
                       b"DORA_ASR_BOUNDARY_V1\r\nMODEL_INIT_SUCCESS\r\nPRIVATE_SENTINEL"):
            self.assertEqual("ENGINE_FAILURE", self.a.parse_native_receipt(0, output))

    def test_windows_adb_crlf_process_states_remain_exact(self):
        for state in ("LIVE", "GONE", "PENDING"):
            self.assertEqual(state, self.a.parse_process_state((state + "\r\n").encode("ascii")))
        for output in (b"GONE\r", b"GONE\r\r\n", b"GONE\r\nLIVE\r\n"):
            with self.assertRaises(self.a.RunnerError):
                self.a.parse_process_state(output)


class Clock:
    value = 0.0
    def now(self):
        return self.value
    def sleep(self, duration):
        self.value += duration


class FakeEngine:
    """Test-only start/poll/terminate implementation, no model/native IO."""
    def __init__(self, outcome="SUCCESS", pending=False):
        self.outcome, self.pending = outcome, pending
        self.calls, self.error = 0, None
        self.session = FakeSession(outcome, pending)
    def start(self, request, control):
        control.check()
        self.calls += 1
        if self.error:
            raise self.error
        return self.session


class FakeSession:
    def __init__(self, outcome, pending):
        self.outcome, self.pending, self.stops = outcome, pending, []
    def poll(self):
        return None if self.pending else self.outcome
    def terminate(self, reason):
        self.stops.append(reason)


if __name__ == "__main__":
    unittest.main()
