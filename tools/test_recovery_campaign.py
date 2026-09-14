"""Host-only regression tests; never starts adb or an emulator."""
import copy
import importlib
import tempfile
import hashlib
import subprocess
import unittest
from unittest.mock import patch
from pathlib import Path

try:
    campaign = importlib.import_module("tools.recovery_campaign")
except ModuleNotFoundError:
    try:
        campaign = importlib.import_module("recovery_campaign")
    except ModuleNotFoundError:
        campaign = None

ROOT = Path(__file__).resolve().parents[1]
SOURCE = {"commit": "4" * 40, "tree": "9" * 40, "appApkSha256": "a" * 64, "testApkSha256": "b" * 64}


class CampaignPresence(unittest.TestCase):
    def test_host_campaign_contract_is_implemented(self):
        self.assertIsNotNone(campaign, "Missing protocol-derived campaign planner/evaluator")


@unittest.skipIf(campaign is None, "Implementation not yet present")
class CampaignContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.plan = campaign.build_plan(ROOT, "PHASE_A", SOURCE, 20260914)

    def test_expanded_candidates_keep_separate_denominators(self):
        entries = self.plan["entries"]
        faults = [e for e in entries if e["kind"] == "FAULT"]
        kills = [e for e in entries if e["kind"] == "HARD_KILL"]
        self.assertEqual(360, len(faults))
        self.assertEqual(240, len(kills))
        self.assertEqual(270, sum(e["environment"] == "E36-GAPI" for e in faults))
        self.assertEqual(144, sum(e["environment"] == "E36-GAPI" for e in kills))
        self.assertFalse(any(e["candidateId"] == "REC-STREAM-TINK" and e.get("caseId") in ("COR-04", "COR-05") for e in entries))

    def test_manifest_tampering_and_missing_case_are_rejected(self):
        changed = copy.deepcopy(self.plan)
        changed["entries"].pop()
        with self.assertRaises(ValueError):
            campaign.validate_plan(ROOT, changed)
        changed = copy.deepcopy(self.plan)
        changed["entries"][0]["expectedClassifications"] = ["PASS"]
        with self.assertRaises(ValueError):
            campaign.validate_plan(ROOT, changed)

    def test_exact_k12_stream_override_and_variants(self):
        k12 = next(e for e in self.plan["entries"] if e["candidateId"] == "REC-STREAM-TINK" and e.get("stratumId") == "K12")
        self.assertEqual(8137, k12["plaintextBytes"])
        self.assertEqual("K12-STREAM-V0.7", k12["seedId"])
        tru = next(e for e in self.plan["entries"] if e["candidateId"] == "REC-STREAM-TINK" and e.get("caseId") == "TRU-03")
        self.assertEqual(["APPEND_1", "APPEND_4096", "APPEND_8192"], tru["mutationVariants"])

    def test_fixture_bytes_and_chunk_boundary_are_independent(self):
        value = campaign.fixture_bytes(17, 70000)
        for offset in (0, 255, 256, 65535, 65536, 69999):
            self.assertEqual((17 + offset * 31 + (offset >> 8) * 17) & 255, value[offset])

    def test_prefix_comparison_rejects_one_bad_byte(self):
        entry = self.plan["entries"][0]
        expected = campaign.fixture_bytes(entry["seed"], 4096)
        with tempfile.TemporaryDirectory() as temporary:
            target = Path(temporary) / "prefix.pcm"
            target.write_bytes(expected)
            self.assertTrue(campaign.compare_prefix(target, entry, 4096))
            target.write_bytes(expected[:-1] + bytes([expected[-1] ^ 1]))
            self.assertFalse(campaign.compare_prefix(target, entry, 4096))

    def test_candidate_failure_cannot_be_discarded_as_external_invalid(self):
        entry = next(e for e in self.plan["entries"] if e["kind"] == "HARD_KILL")
        result = {"attemptId": entry["attemptId"], "status": "INVALID", "invalidReason": "CANDIDATE_CRASH"}
        with self.assertRaises(ValueError):
            campaign.evaluate_attempt(entry, result)

    def test_missing_candidate_output_after_confirmed_kill_is_valid_failure(self):
        entry = next(e for e in self.plan["entries"] if e["kind"] == "HARD_KILL")
        result = {"attemptId": entry["attemptId"], "status": "VALID", "kill": {key: True for key in campaign.KILL_PROOFS}, "candidateResult": None}
        self.assertEqual("FAIL", campaign.evaluate_attempt(entry, result)["verdict"])

    def test_empty_ledger_is_inconclusive_and_never_phase_a_pass(self):
        summary = campaign.evaluate_campaign(self.plan, [])
        self.assertEqual("INCONCLUSIVE", summary["verdict"])
        self.assertEqual(600, summary["untested"])
        self.assertEqual(0, summary["hardKillValid"])

    def test_duplicate_attempt_and_fabricated_replacement_are_rejected(self):
        entry = self.plan["entries"][0]
        result = {"attemptId": entry["attemptId"], "status": "UNTESTED"}
        with self.assertRaises(ValueError):
            campaign.evaluate_campaign(self.plan, [result, result])
        result["attemptId"] += "-R1"
        with self.assertRaises(ValueError):
            campaign.evaluate_campaign(self.plan, [result])

    def test_adb_arguments_are_exact_and_request_not_shell_interpolated(self):
        entry = self.plan["entries"][0]
        request = campaign.request_for(self.plan, entry, "RECOVER", "DEFAULT")
        argv = campaign.instrument_command(Path("adb.exe"), "emulator-5556", request, SOURCE["commit"], campaign.digest_json(self.plan))
        self.assertIn("recoveryCampaignRequest", argv)
        self.assertIn(campaign.SELECTOR, argv)
        self.assertEqual(["adb.exe", "-s", "emulator-5556", "shell", "am", "instrument"], argv[:6])
        with self.assertRaises(ValueError):
            campaign.instrument_command(Path("adb.exe"), "bad;serial", request, SOURCE["commit"], campaign.digest_json(self.plan))

    def test_execution_gate_cannot_replace_accountable_review_with_authority(self):
        self.assertTrue(callable(getattr(campaign, "validate_execution_gate", None)), "Execution gate missing")
        with self.assertRaises(ValueError):
            campaign.validate_execution_gate(self.plan, {"executionAuthorized": True}, {})

    def test_failed_baseline_retention_prevents_fault_mutation(self):
        entry = next(e for e in self.plan["entries"] if e.get("caseId") == "KEY-01" and e["environment"] == "E36-GAPI")
        calls = []
        class Transport:
            def __init__(self, *args):
                pass
            def verify_device(self, source):
                pass
        def operation(transport, plan, selected, name, variant):
            calls.append(name)
            return {}
        def retention(*args):
            calls.append("RETAIN")
            raise ValueError("Synthetic incomplete private evidence")
        with tempfile.TemporaryDirectory() as directory, \
                patch.object(campaign, "validate_plan"), patch.object(campaign, "validate_execution_gate"), \
                patch.object(campaign, "OwnedAdbTransport", Transport), \
                patch.object(campaign, "run_operation", operation), patch.object(campaign, "retain_evidence", retention):
            result = campaign.execute_one(ROOT, self.plan,
                {"supportedAttemptIds": [entry["attemptId"]], "instrumentationParserSha256": "a" * 64},
                {"environment": "E36-GAPI"}, Path("unused-adb"), entry["attemptId"], Path(directory) / "attempt")
        self.assertEqual(["PREPARE", "RETAIN"], calls)
        self.assertFalse(result["rawRetentionComplete"])
        self.assertEqual("UNVERIFIED_STOP_NO_AUTOMATIC_RETRY", result["cleanupResult"])

    def test_transport_never_uses_local_server_autostart_path(self):
        self.assertTrue(callable(getattr(campaign, "owned_adb_command", None)), "Owned transport missing")
        argv = campaign.owned_adb_command(Path("adb.exe"), 5037, ["-s", "emulator-5556", "get-state"])
        self.assertEqual(["adb.exe", "-H", "127.0.0.1", "-P", "5037"], argv[:5])
        with self.assertRaises(ValueError):
            campaign.owned_adb_command(Path("adb.exe"), 0, ["devices"])

    def test_foreign_barrier_event_rejected_before_pid_use(self):
        self.assertTrue(callable(getattr(campaign, "validate_event", None)), "Event validation missing")
        entry = self.plan["entries"][0]
        with self.assertRaises(ValueError):
            campaign.validate_event({"schema": "DORA_RECOVERY_CAMPAIGN_EVENT_V1", "attemptId": "foreign"}, entry, "WRITE_UNTIL_BARRIER")

    def test_sigkill_rejects_pid_mismatch(self):
        self.assertTrue(callable(getattr(campaign, "kill_arguments", None)), "Exact PID kill guard missing")
        with self.assertRaises(ValueError):
            campaign.kill_arguments(123, "124", campaign.PACKAGE)
        with self.assertRaises(ValueError):
            campaign.kill_arguments(123, "123", "other.application")
        self.assertEqual(["shell", "run-as", campaign.PACKAGE, "kill", "-9", "123"], campaign.kill_arguments(123, "123", campaign.PACKAGE))

    def test_fault_cannot_borrow_hard_kill_replacement_policy(self):
        entry = next(e for e in self.plan["entries"] if e["kind"] == "FAULT")
        base = {"attemptId": entry["attemptId"], "status": "INVALID", "invalidReason": "FIXTURE_DRIFT"}
        replacement = {"attemptId": entry["attemptId"] + "-R1", "replacementFor": entry["attemptId"], "replacementLedgerAuthorization": "explicit-ledger", "status": "UNTESTED"}
        with self.assertRaises(ValueError):
            campaign.evaluate_campaign(self.plan, [base, replacement])

    def test_multivariant_row_requires_all_variant_evidence(self):
        entry = next(e for e in self.plan["entries"] if e["candidateId"] == campaign.CANDIDATES[0] and e.get("caseId") == "TRU-03")
        record = {"attemptId": entry["attemptId"], "status": "VALID", "hostOracleEqual": True, "candidateResult": {
            "acceptedEnd": 100, "committedEnd": 100, "recoveredEnd": 100,
            "authenticated": True, "contiguous": True, "repeatStable": True, "caseOracleSatisfied": True,
            "duplicateProcessingIntents": 0, "missingProcessingIntents": 0,
            "microphoneOpens": 0, "unsafePathOpens": 0, "processingIntentCount": 0,
            "sourceUnchanged": True,
        }}
        self.assertEqual("FAIL", campaign.evaluate_attempt(entry, record)["verdict"])

    def test_expected_fail_closed_key_fault_preserves_committed_baseline(self):
        entry = next(e for e in self.plan["entries"] if e["candidateId"] == campaign.CANDIDATES[0] and e.get("caseId") == "KEY-01")
        observed = {"committedEnd": 100, "recoveredEnd": 0, "acceptedEnd": 100,
                    "authenticated": False, "contiguous": True, "repeatStable": True,
                    "caseOracleSatisfied": True, "classification": "KEY_UNAVAILABLE",
                    "duplicateProcessingIntents": 0, "missingProcessingIntents": 0,
                    "microphoneOpens": 0, "unsafePathOpens": 0, "processingIntentCount": 0,
                    "sourceUnchanged": True}
        result = {"attemptId": entry["attemptId"], "status": "VALID", "candidateResult": observed,
                  "hostOracleEqual": True, "cleanupResult": "VERIFIED"}
        self.assertEqual("PASS", campaign.evaluate_attempt(entry, result)["verdict"])
        observed["recoveredEnd"] = 1
        self.assertEqual("FAIL", campaign.evaluate_attempt(entry, result)["verdict"])

    def test_ack_path_requires_exact_identity_and_increasing_sequence(self):
        entry = next(e for e in self.plan["entries"] if e.get("stratumId") == "K11")
        event = {"eventAcknowledgementRequired": True, "sequence": 1,
                 "acknowledgementRelativePath": "campaign/" + entry["attemptId"] + "/ack-1"}
        self.assertTrue(callable(getattr(campaign, "acknowledgement_arguments", None)))
        self.assertEqual(["shell", "run-as", campaign.PACKAGE, "touch", "files/" + event["acknowledgementRelativePath"]], campaign.acknowledgement_arguments(entry, event, 0))
        with self.assertRaises(ValueError):
            campaign.acknowledgement_arguments(entry, event, 1)
        event["acknowledgementRelativePath"] = "campaign/../other/ack-1"
        with self.assertRaises(ValueError):
            campaign.acknowledgement_arguments(entry, event, 0)

    def test_missing_later_data_envelope_preserves_only_authenticated_earlier_prefix(self):
        entry = next(e for e in self.plan["entries"] if e["candidateId"] == campaign.CANDIDATES[1] and e.get("caseId") == "KEY-03")
        observed = {"committedEnd": 480000, "recoveredEnd": 160000, "acceptedEnd": 480000,
                    "authenticated": True, "contiguous": True, "repeatStable": True,
                    "caseOracleSatisfied": True, "classification": "KEY_UNAVAILABLE",
                    "duplicateProcessingIntents": 0, "missingProcessingIntents": 0,
                    "microphoneOpens": 0, "unsafePathOpens": 0,
                    "artifactMutationFacts": {"missingArtifactRole": "DATA_KEY_ENVELOPE", "affectedPlaintextStart": 160000}}
        result = {"attemptId": entry["attemptId"], "status": "VALID", "candidateResult": observed,
                  "hostOracleEqual": True, "cleanupResult": "VERIFIED"}
        self.assertEqual("PASS", campaign.evaluate_attempt(entry, result)["verdict"])
        observed["authenticated"] = False
        self.assertEqual("FAIL", campaign.evaluate_attempt(entry, result)["verdict"])
        observed["authenticated"] = True
        observed["recoveredEnd"] = 160001
        self.assertEqual("FAIL", campaign.evaluate_attempt(entry, result)["verdict"])

    def test_stream_append_requires_exact_canonical_retained_range(self):
        entry = next(e for e in self.plan["entries"] if e["candidateId"] == campaign.CANDIDATES[0] and e.get("caseId") == "TRU-03")
        entry = dict(entry, mutationVariants=["APPEND_1"])
        observed = {"committedEnd": 4056, "recoveredEnd": 8136, "acceptedEnd": 8137,
                    "classification": "VALID", "preFaultSourceBytes": 8192, "observedSourceBytes": 8193,
                    "sourceUnchanged": True, "processingIntentCount": 0, "rangeStart": 8192,
                    "rangeEnd": 8193, "rangeCertainty": "EXACT_FORMAT_BOUNDARY"}
        self.assertEqual([], campaign.fault_predicate_failures(entry, observed, {}))
        observed["rangeStart"] = 0
        self.assertTrue(campaign.fault_predicate_failures(entry, observed, {}))
        observed["rangeStart"] = 8192
        observed["rangeCertainty"] = "INDETERMINATE"
        self.assertTrue(campaign.fault_predicate_failures(entry, observed, {}))

    def test_fault_kills_are_required_without_changing_denominators(self):
        for case in ("KCB-01", "KCB-06", "QUA-02", "QUA-03", "IDE-02", "EVT-01", "CLN-01"):
            entry = next(e for e in self.plan["entries"] if e.get("caseId") == case)
            self.assertEqual("FAULT", entry["kind"])
            self.assertTrue(campaign.requires_external_kill(entry))
            with self.assertRaises(ValueError):
                campaign.evaluate_attempt(dict(entry, mutationVariants=[entry["mutationVariants"][0]]), {"attemptId": entry["attemptId"], "status": "VALID"})

    def test_raw_retention_requires_exact_bytes_and_contained_nonsymlink_path(self):
        entry = self.plan["entries"][0]
        data = b'{"typedRows":[]}'
        path = "files/campaign/" + entry["attemptId"] + "/rows.json"
        artifact = {"relativePath": path, "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest(), "role": "CONSISTENT_JOURNAL_SNAPSHOT"}
        class FakeTransport:
            def run(self, arguments, label, **kwargs):
                if "stat" in arguments:
                    output = b"regular file\n" if arguments[-1] == path else b"directory\n"
                else:
                    output = data
                return subprocess.CompletedProcess(arguments, 0, output, b"")
        with tempfile.TemporaryDirectory() as temporary:
            transport = FakeTransport()
            transport.directory = Path(temporary)
            observation = {"retentionSnapshotConsistent": True, "retentionArtifacts": [artifact]}
            receipt = campaign.retain_evidence(transport, self.plan, entry, observation, "retained")
            self.assertEqual(1, receipt["retainedArtifactCount"])
            self.assertEqual(data, (Path(temporary) / "retained/0000.bin").read_bytes())
            artifact["sha256"] = "0" * 64
            with self.assertRaises(ValueError):
                campaign.retain_evidence(transport, self.plan, entry, observation, "changed")
            artifact["relativePath"] = "files/campaign/../foreign/rows.json"
            with self.assertRaises(ValueError):
                campaign.retention_path(entry, artifact)
            artifact["relativePath"] = "no_backup/poc-recovery/v1/runs/00000000-0000-0000-0000-000000000001/file.bin"
            with self.assertRaises(ValueError):
                campaign.retention_path(entry, artifact)

    def test_named_fault_predicates_reject_generic_success_flags(self):
        for case in ("QUA-01", "PAR-01", "CLN-01", "CLN-02", "CLN-03", "EVT-01", "RBK-01", "KEY-05"):
            entry = next(e for e in self.plan["entries"] if e.get("caseId") == case)
            failures = campaign.fault_predicate_failures(dict(entry, mutationVariants=[entry["mutationVariants"][0]]),
                {"caseOracleSatisfied": True, "classification": "VALID", "acceptedEnd": 100, "committedEnd": 100, "recoveredEnd": 100}, {})
            self.assertTrue(failures, case)

    def test_checkpoint_selection_cannot_rewrite_frozen_source_or_oracle(self):
        entry = next(e for e in self.plan["entries"] if e.get("caseId") == "KEY-05" and e["candidateId"] == campaign.CANDIDATES[0])
        before = {"originalCheckpointIdentity": "a" * 64, "checkpointIdentity": "a" * 64,
                  "checkpointGeneration": 1, "checkpointPrefixBytes": 8192, "checkpointContextEnd": 4056,
                  "acceptedEnd": 8137, "preFaultSourceBytes": 8192, "preFaultSourceSha256": "b" * 64}
        after = dict(before, checkpointIdentity="c" * 64)
        campaign.validate_checkpoint_selection(before, after, entry)
        for field in ("checkpointGeneration", "checkpointPrefixBytes", "checkpointContextEnd", "acceptedEnd", "preFaultSourceBytes"):
            changed = dict(after)
            changed[field] += 1
            with self.assertRaises(ValueError):
                campaign.validate_checkpoint_selection(before, changed, entry)

    def test_full_synthetic_coverage_still_fails_one_committed_loss(self):
        plan = campaign.build_plan(ROOT, "FULL_PHYSICAL", SOURCE, 20260914)
        self.assertEqual(510, len(plan["entries"]))
        ledger = []
        for entry in plan["entries"]:
            observed = {"acceptedEnd": 100, "committedEnd": 100, "recoveredEnd": 100,
                        "authenticated": True, "contiguous": True, "repeatStable": True,
                        "caseOracleSatisfied": True, "duplicateProcessingIntents": 0,
                        "missingProcessingIntents": 0, "microphoneOpens": 0, "unsafePathOpens": 0,
                        "processingIntentCount": 0, "sourceUnchanged": True,
                        "classification": (entry["expectedClassifications"] or ["EXPECTED_ROW_OUTCOME"])[0]}
            result = {"attemptId": entry["attemptId"], "status": "VALID", "candidateResult": observed,
                      "hostOracleEqual": True, "cleanupResult": "VERIFIED", "hostAcceptedEnd": 100,
                      "kill": {key: True for key in campaign.KILL_PROOFS}}
            if entry["kind"] == "FAULT" and entry["expectedClassifications"] and entry["expectedClassifications"][0] != "VALID_DURABLE_BOOTSTRAP":
                observed["recoveredEnd"] = 0
                observed["authenticated"] = False
            if len(entry["mutationVariants"]) > 1:
                result["mutationVariantResults"] = [{"variant": variant, "result": copy.deepcopy(result)} for variant in entry["mutationVariants"]]
            ledger.append(result)
        # The deliberately lost committed artifact cases require product FAIL,
        # even with every synthetic recipe reporting its expected classification.
        self.assertEqual("FAIL", campaign.evaluate_campaign(plan, ledger)["verdict"])
        target_entry = next(entry for entry in plan["entries"] if entry["kind"] == "HARD_KILL")
        target = next(result for result in ledger if result["attemptId"] == target_entry["attemptId"])
        self.assertEqual("PASS", campaign.evaluate_attempt(target_entry, target)["verdict"])
        target["candidateResult"]["recoveredEnd"] = 99
        self.assertEqual("FAIL", campaign.evaluate_attempt(target_entry, target)["verdict"])
        target["candidateResult"]["recoveredEnd"] = 100
        target["cleanupResult"] = "UNKNOWN"
        self.assertEqual("INCONCLUSIVE", campaign.evaluate_attempt(target_entry, target)["verdict"])


def load_tests(loader, tests, pattern):
    # The existing governance --self-test includes this module; therefore the
    # independently owned raw instrumentation transcript suite is included too.
    import importlib.util
    path = Path(__file__).with_name("test_recovery_instrumentation_status.py")
    spec = importlib.util.spec_from_file_location("test_recovery_instrumentation_status", path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return unittest.TestSuite([tests, loader.loadTestsFromModule(module)])


if __name__ == "__main__":
    unittest.main()
