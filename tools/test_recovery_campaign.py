"""Host-only regression tests; never starts adb or an emulator."""
import copy
import importlib
import tempfile
import hashlib
import json
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


class AlphaPreflightAdmission(unittest.TestCase):
    """Real packet admission; fixture evidence never asserts a human review."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.proof = Path(self.temp.name) / "technical-evidence.txt"
        self.proof.write_text("synthetic technical evidence\n", encoding="utf-8")
        self.decision = ROOT / "docs/stage0/DORA_0D6_ALPHA_PREFLIGHT_OWNER_DECISION_20260914.md"
        self.payloads = ["SUPPLEMENTAL_SQLITE", "JOURNAL_CONNECTIONS", "PLATFORM_PREREQUISITES"]
        self.plan = {"source": SOURCE}
        # Isolate Git discovery only. The exact-profile validator has separate
        # real-Git tests; every packet field/proof is validated by real code here.
        source_probe = patch.object(campaign, "accepted_alpha_source", return_value=copy.deepcopy(SOURCE), create=True)
        source_probe.start()
        self.addCleanup(source_probe.stop)
        proof = {"path": str(self.proof), "sha256": hashlib.sha256(self.proof.read_bytes()).hexdigest()}
        self.gate = {
            "schema": "DORA_RECOVERY_CAMPAIGN_EXECUTION_GATE_V1",
            "source": copy.deepcopy(SOURCE), "manifestSha256": campaign.digest_json(self.plan),
            "driverSha256": hashlib.sha256(Path(campaign.__file__).read_bytes()).hexdigest(),
            "instrumentationParserSha256": hashlib.sha256((ROOT / "tools/recovery_instrumentation_status.py").read_bytes()).hexdigest(),
            "executionAuthorized": True, "implementationVerified": True, "independentReviewClean": True,
            "exactHeadCiPassed": True, "graphAndR8Verified": True, "accountableReviewApproved": False,
            "accountableReview": {"formalReviewer": False, "reviewer": None, "reviewedCommit": None},
            "preflightPassed": False, "physicalAuthorization": {"authorized": False},
            "ownerInstructionReference": "synthetic owner instruction fixture",
            "environment": "E36-GAPI",
            "deviceFingerprint": "google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys",
            "supportedPayloads": self.payloads, "supportedAttemptIds": [],
            "alphaPreflight": {"decisionId": "DORA_0D6_ALPHA_PREFLIGHT_20260914",
                               "scope": "INTERNAL_ALPHA_E36_PREFLIGHT_ONLY", "source": copy.deepcopy(SOURCE)},
            "proofs": {key: copy.deepcopy(proof) for key in
                       ("implementation", "independentReview", "ci", "graphAndR8", "ownerInstruction")},
        }
        self.gate["proofs"]["ownerDecision"] = {
            "path": str(self.decision), "sha256": hashlib.sha256(self.decision.read_bytes()).hexdigest()}

    def check(self, payload="SUPPLEMENTAL_SQLITE", session=None):
        campaign.validate_execution_gate(self.plan, self.gate, session, payload)

    def test_valid_alpha_packet_admits_exactly_three_preflights_without_human_review(self):
        for payload in self.payloads:
            with self.subTest(payload=payload):
                self.check(payload)

    def test_alpha_rejects_missing_wrong_scope_or_arbitrary_decision(self):
        original = copy.deepcopy(self.gate)
        for mutation in (lambda g: g["alphaPreflight"].pop("decisionId"),
                         lambda g: g["alphaPreflight"].update(scope="ALL_ALPHA"),
                         lambda g: g["proofs"].pop("ownerDecision"),
                         lambda g: g["proofs"]["ownerDecision"].update(path=str(self.proof)),
                         lambda g: g["proofs"]["ownerDecision"].update(sha256="0" * 64),
                         lambda g: g.update(alphaPreflight=True)):
            self.gate = copy.deepcopy(original)
            mutation(self.gate)
            with self.assertRaises(ValueError):
                self.check()

    def test_alpha_rejects_source_apk_manifest_driver_parser_and_proof_mismatches(self):
        original = copy.deepcopy(self.gate)
        for field in SOURCE:
            self.gate = copy.deepcopy(original)
            self.gate["alphaPreflight"]["source"][field] = "0" * len(SOURCE[field])
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.check()
        for field in ("manifestSha256", "driverSha256", "instrumentationParserSha256"):
            self.gate = copy.deepcopy(original)
            self.gate[field] = "0" * 64
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.check()
        for key in ("implementation", "independentReview", "ci", "graphAndR8", "ownerInstruction"):
            self.gate = copy.deepcopy(original)
            self.gate["proofs"][key]["sha256"] = "0" * 64
            with self.subTest(proof=key), self.assertRaises(ValueError):
                self.check()

    def test_alpha_rejects_campaign_physical_and_expanded_payload_scope(self):
        with self.assertRaises(ValueError):
            self.check("CAMPAIGN")
        original = copy.deepcopy(self.gate)
        for environment in ("D1", "D2", "D5"):
            self.gate = copy.deepcopy(original)
            self.gate["environment"] = environment
            with self.subTest(environment=environment), self.assertRaises(ValueError):
                self.check()
        self.gate = copy.deepcopy(original)
        self.gate["supportedPayloads"].append("CAMPAIGN")
        with self.assertRaises(ValueError):
            self.check()
        self.gate = copy.deepcopy(original)
        self.gate["supportedAttemptIds"] = ["PA-STREAM-K01-E36-GAPI-01"]
        with self.assertRaises(ValueError):
            self.check()

    def test_alpha_cannot_fabricate_human_approval_or_remove_technical_gates(self):
        original = copy.deepcopy(self.gate)
        for field in ("implementationVerified", "independentReviewClean", "exactHeadCiPassed", "graphAndR8Verified"):
            self.gate = copy.deepcopy(original)
            self.gate[field] = False
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.check()
        self.gate = copy.deepcopy(original)
        self.gate["accountableReviewApproved"] = True
        with self.assertRaises(ValueError):
            self.check()

    def test_legacy_missing_review_still_refused(self):
        self.gate.pop("alphaPreflight")
        with self.assertRaisesRegex(ValueError, "accountableReviewApproved gate unsatisfied"):
            self.check()

    def test_alpha_rejects_self_consistent_unrelated_source_or_apks(self):
        for field in SOURCE:
            with self.subTest(field=field):
                original = copy.deepcopy(self.gate)
                self.plan = {"source": copy.deepcopy(SOURCE)}
                self.plan["source"][field] = "0" * len(SOURCE[field])
                self.gate["source"] = copy.deepcopy(self.plan["source"])
                self.gate["alphaPreflight"]["source"] = copy.deepcopy(self.plan["source"])
                self.gate["manifestSha256"] = campaign.digest_json(self.plan)
                with self.assertRaisesRegex(ValueError, "exact accepted successor"):
                    self.check()
                self.gate = original


@unittest.skipIf(campaign is None, "Implementation not yet present")
class AlphaCampaignAdmission(unittest.TestCase):
    """The E36 campaign is a separate, bounded admission path."""

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.execution_id = "E36-CAMPAIGN-20260914"
        self.source = {"commit": "4" * 40, "tree": "9" * 40,
                       "appApkSha256": "8b1f79aec975c02021c7f58f5218da91f9e9585dbe8bbbd0844647c5b0c1d2de",
                       "testApkSha256": "effd7e29c7dc7a4d8adc7a7057b1d90f5a58340b11c8755aff26cf71c39e0dfe"}
        self.plan = campaign.build_plan(ROOT, "PHASE_A", self.source, 20260914, self.execution_id)
        self.selected = [entry for entry in self.plan["entries"] if entry["environment"] == "E36-GAPI"]
        self.decision = ROOT / "docs/stage0/DORA_0D6_ALPHA_E36_CAMPAIGN_OWNER_DECISION_20260914.md"
        proof_path = Path(self.temp.name) / "technical-evidence.txt"
        proof_path.write_text("synthetic technical evidence\n", encoding="utf-8")
        proof = {"path": str(proof_path), "sha256": hashlib.sha256(proof_path.read_bytes()).hexdigest()}
        historical = {
            "commit": "d3bc6aca800ac0dedd82124aabd8d25eff50c262",
            "tree": "1f4575a98ce02ecd01ae0cceaa1373e4f5ee41a9",
            "appApkSha256": "8b1f79aec975c02021c7f58f5218da91f9e9585dbe8bbbd0844647c5b0c1d2de",
            "testApkSha256": "effd7e29c7dc7a4d8adc7a7057b1d90f5a58340b11c8755aff26cf71c39e0dfe",
        }
        self.preflight_root = Path(self.temp.name) / "retained-preflight"
        self.unavailable_private_root = Path(self.temp.name) / "private-package-unavailable"

        def artifact(relative, value):
            path = self.preflight_root.joinpath(*relative.split("/"))
            path.parent.mkdir(parents=True, exist_ok=True)
            data = campaign.canonical(value) + b"\n"
            path.write_bytes(data)
            return {"bytes": len(data), "path": relative, "sha256": hashlib.sha256(data).hexdigest()}

        old_plan = campaign.build_plan(ROOT, "PHASE_A", historical, 20260914)
        old_entries = [entry for entry in old_plan["entries"] if entry["environment"] == "E36-GAPI"]
        source_equivalence = artifact("raw/source-build-equivalence.json", {"historical": historical})
        top_packet = artifact("packets/retained-packet.json", {"packet": "retained"})
        expected_attempts = (
            ("D3BC6AC-SUPPLEMENTAL_SQLITE-03", "SUPPLEMENTAL_SQLITE"),
            ("D3BC6AC-JOURNAL_CONNECTIONS-03", "JOURNAL_CONNECTIONS"),
            ("D3BC6AC-PLATFORM_PREREQUISITES-04", "PLATFORM_PREREQUISITES"),
        )
        attempts = []
        for number, (attempt_id, payload) in enumerate(expected_attempts):
            prefix = "raw/assessment-" + str(number)
            attempts.append({
                "attemptId": attempt_id, "payload": payload, "status": "PASS", "cleanupResult": "VERIFIED",
                "errors": 0, "failures": 0, "skips": 0, "executedTests": 1, "successfulTests": 1,
                "instrumentationNativeExit": 0, "launcherNativeExit": 0, "adbRootExit": 0,
                "evidence": [artifact(prefix + "-evidence.json", {"kind": "evidence", "attempt": attempt_id})],
                "environmentReceipts": [artifact(prefix + "-environment.json", {"kind": "environment", "attempt": attempt_id})],
                "shutdownResolution": artifact(prefix + "-cleanup.json", {"kind": "cleanup", "attempt": attempt_id}),
                "packet": artifact(prefix + "-packet.json", {"kind": "packet", "attempt": attempt_id}),
            })
        results = {
            "schema": "DORA_0D6_ALPHA_PREFLIGHT_OBSERVED_RESULTS_V1", "source": historical,
            "aggregatePreflightPassed": True, "campaignAuthorized": False, "attempts": attempts,
            "admissionPackets": [top_packet], "packet": top_packet,
        }
        results_descriptor = artifact("preflight-results.json", results)
        handoff = {
            "source": historical, "preflightPassed": True, "faults": {"planned": 270, "executed": 0},
            "hardKills": {"planned": 144, "executed": 0}, "entries": old_entries,
            "preflightResults": {"path": "preflight-results.json", "sha256": results_descriptor["sha256"]},
        }
        handoff_descriptor = artifact("CAMPAIGN-HANDOFF.json", handoff)
        equivalence_path = Path(self.temp.name) / "source-equivalence.json"
        equivalence = {
            "historicalSource": historical, "successorSource": copy.deepcopy(self.source),
            "apkPairUnchanged": True, "androidTreeBefore": "c" * 40, "androidTreeAfter": "c" * 40,
            "buildInputsUnchanged": True, "changedPaths": ["tools/recovery_campaign.py"],
        }
        equivalence_path.write_bytes(campaign.canonical(equivalence) + b"\n")
        self.gate = {
            "schema": "DORA_RECOVERY_CAMPAIGN_EXECUTION_GATE_V1",
            "source": copy.deepcopy(self.source), "manifestSha256": campaign.digest_json(self.plan),
            "driverSha256": hashlib.sha256(Path(campaign.__file__).read_bytes()).hexdigest(),
            "instrumentationParserSha256": hashlib.sha256((ROOT / "tools/recovery_instrumentation_status.py").read_bytes()).hexdigest(),
            "executionAuthorized": True, "implementationVerified": True, "independentReviewClean": True,
            "exactHeadCiPassed": True, "graphAndR8Verified": True, "preflightPassed": True,
            "accountableReviewApproved": False,
            "accountableReview": {"formalReviewer": False, "reviewer": None, "reviewedCommit": None},
            "physicalAuthorization": {"authorized": False}, "ownerInstructionReference": "owner campaign instruction",
            "environment": "E36-GAPI",
            "deviceFingerprint": "google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys",
            "supportedPayloads": ["CAMPAIGN"], "supportedAttemptIds": [entry["attemptId"] for entry in self.selected],
            "alphaCampaign": {
                "decisionId": "DORA_0D6_ALPHA_E36_CAMPAIGN_20260914", "scope": "INTERNAL_ALPHA_E36_CAMPAIGN",
                "source": copy.deepcopy(self.source), "historicalSource": historical,
                "sourceEquivalence": {"historicalSource": historical, "successorSource": copy.deepcopy(self.source),
                                      "apkPairUnchanged": True,
                                      "proof": {"path": str(equivalence_path),
                                                "sha256": hashlib.sha256(equivalence_path.read_bytes()).hexdigest()}},
            },
            "retainedPreflight": {
                "root": str(self.preflight_root),
                "handoff": {"relativePath": "CAMPAIGN-HANDOFF.json", "sha256": handoff_descriptor["sha256"]},
                "results": {"relativePath": "preflight-results.json", "sha256": results_descriptor["sha256"]},
                "sourceBuildEquivalence": {"relativePath": "raw/source-build-equivalence.json", "sha256": source_equivalence["sha256"]},
            },
            "proofs": {key: copy.deepcopy(proof) for key in ("implementation", "independentReview", "ci", "graphAndR8", "ownerInstruction")},
        }
        self.gate["proofs"]["ownerDecision"] = {"path": str(self.decision),
                                                    "sha256": hashlib.sha256(self.decision.read_bytes()).hexdigest()}
        source_probe = patch.object(campaign, "accepted_alpha_source", return_value=copy.deepcopy(self.source), create=True)
        source_probe.start()
        self.addCleanup(source_probe.stop)
        root_probe = patch.object(campaign, "_alpha_preflight_root", return_value=self.preflight_root)
        root_probe.start()
        self.addCleanup(root_probe.stop)
        for name, value in (("ALPHA_PREFLIGHT_RESULTS_SHA256", results_descriptor["sha256"]),
                            ("ALPHA_PREFLIGHT_HANDOFF_SHA256", handoff_descriptor["sha256"]),
                            ("ALPHA_PREFLIGHT_EQUIVALENCE_SHA256", source_equivalence["sha256"])):
            constant_probe = patch.object(campaign, name, value)
            constant_probe.start()
            self.addCleanup(constant_probe.stop)

    def check(self):
        campaign.validate_execution_gate(self.plan, self.gate, payload="CAMPAIGN")

    def test_valid_campaign_admits_exact_e36_base_population(self):
        self.assertFalse(self.unavailable_private_root.exists())
        self.assertEqual(414, len(self.selected))
        self.assertEqual(270, sum(entry["kind"] == "FAULT" for entry in self.selected))
        self.assertEqual(144, sum(entry["kind"] == "HARD_KILL" for entry in self.selected))
        self.check()

    def test_campaign_rehashes_tampered_retained_raw_witness(self):
        witness = self.preflight_root / "raw" / "assessment-0-evidence.json"
        witness.write_bytes(b"tampered")
        with self.assertRaises(ValueError):
            self.check()

    def test_campaign_rejects_missing_retained_raw_witness(self):
        witness = self.preflight_root / "raw" / "assessment-0-cleanup.json"
        witness.unlink()
        with self.assertRaises(ValueError):
            self.check()

    def test_campaign_rejects_missing_or_mismatched_decision_preflight_or_manifest(self):
        original = copy.deepcopy(self.gate)
        for mutation in (
                lambda g: g.pop("alphaCampaign"),
                lambda g: g["alphaCampaign"].update(scope="ALL_ALPHA"),
                lambda g: g.update(alphaPreflight={}),
                lambda g: g["retainedPreflight"].pop("results"),
                lambda g: g["retainedPreflight"]["handoff"].update(sha256="0" * 64),
                lambda g: g.update(manifestSha256="0" * 64)):
            self.gate = copy.deepcopy(original)
            mutation(self.gate)
            with self.assertRaises(ValueError):
                self.check()

    def test_campaign_rejects_source_apk_and_e36_profile_drift(self):
        original = copy.deepcopy(self.gate)
        for field in self.source:
            self.gate = copy.deepcopy(original)
            self.gate["alphaCampaign"]["source"][field] = "0" * len(SOURCE[field])
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.check()
            self.gate = copy.deepcopy(original)
            self.gate["source"][field] = "0" * len(SOURCE[field])
            with self.subTest(gate_field=field), self.assertRaises(ValueError):
                self.check()
        self.gate = copy.deepcopy(original)
        self.gate["environment"] = "D2"
        with self.assertRaises(ValueError):
            self.check()

    def test_campaign_rejects_foreign_duplicate_and_out_of_scope_attempts(self):
        original = copy.deepcopy(self.gate)
        for changed in (
                original["supportedAttemptIds"] + ["foreign-attempt"],
                original["supportedAttemptIds"] + [original["supportedAttemptIds"][0]],
                [entry["baseAttemptId"] for entry in self.selected],
        ):
            self.gate = copy.deepcopy(original)
            self.gate["supportedAttemptIds"] = changed
            with self.assertRaises(ValueError):
                self.check()

    def test_campaign_rejects_fresh_namespace_with_a_different_original_schedule(self):
        changed = campaign.build_plan(ROOT, "PHASE_A", self.source, 20260913, self.execution_id)
        self.plan = changed
        self.gate["manifestSha256"] = campaign.digest_json(changed)
        self.gate["supportedAttemptIds"] = [entry["attemptId"] for entry in changed["entries"] if entry["environment"] == "E36-GAPI"]
        with self.assertRaises(ValueError):
            self.check()

    def test_preflight_gate_rejects_campaign_payload(self):
        preflight = AlphaPreflightAdmission()
        preflight.setUp()
        self.addCleanup(preflight.doCleanups)
        with self.assertRaises(ValueError):
            preflight.check("CAMPAIGN")

    def test_execution_namespace_preserves_recipes_and_rejects_identity_drift(self):
        legacy = campaign.build_plan(ROOT, "PHASE_A", self.source, 20260914)
        self.assertNotIn("executionId", legacy)
        self.assertTrue(all("baseAttemptId" not in entry for entry in legacy["entries"]))
        by_base = {entry["attemptId"]: entry for entry in legacy["entries"]}
        self.assertEqual([entry["baseAttemptId"] for entry in self.plan["entries"]], [entry["attemptId"] for entry in legacy["entries"]])
        self.assertTrue(all(entry["attemptId"] not in by_base and entry["runId"] != by_base[entry["baseAttemptId"]]["runId"]
                            for entry in self.plan["entries"]))
        for entry in self.plan["entries"]:
            base = by_base[entry["baseAttemptId"]]
            self.assertEqual({key: entry[key] for key in base if key not in ("attemptId", "runId")},
                             {key: entry[key] for key in entry if key not in ("attemptId", "baseAttemptId", "runId")})
        campaign.validate_plan(ROOT, self.plan)
        drift = copy.deepcopy(self.plan)
        drift["entries"][0]["attemptId"] = drift["entries"][0]["baseAttemptId"]
        with self.assertRaises(ValueError):
            campaign.validate_plan(ROOT, drift)
        drift = copy.deepcopy(self.plan)
        drift["entries"][0]["runId"] = "0" * 32
        self.plan = drift
        self.gate["manifestSha256"] = campaign.digest_json(drift)
        with self.assertRaises(ValueError):
            self.check()


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

    def test_complete_product_failure_continues_each_preplanned_variant(self):
        entry = next(e for e in self.plan["entries"] if e.get("caseId") == "COR-03" and e["environment"] == "E36-GAPI")
        calls = []

        class Transport:
            def __init__(self, _adb, _session, directory):
                self.directory = directory

            def verify_device(self, _source):
                pass

            def extract_prefix(self, selected, observed, label="recovered"):
                path = self.directory / (label + ".pcm")
                path.write_bytes(campaign.fixture_bytes(selected["seed"], observed["recoveredEnd"]))
                return path

        def operation(_transport, _plan, _entry, operation_name, variant):
            calls.append((operation_name, variant))
            if operation_name == "RECOVER":
                return {"recoveredEnd": 0, "receiptIdentity": "stable-receipt", "classification": "CORRUPTION_REJECTED"}
            if operation_name == "CLEANUP":
                return {"cleanupComplete": True}
            return {}

        def retention(_transport, _plan, _entry, _observation, label):
            return {"label": label}

        with tempfile.TemporaryDirectory() as directory, \
                patch.object(campaign, "validate_plan"), patch.object(campaign, "validate_execution_gate"), \
                patch.object(campaign, "OwnedAdbTransport", Transport), \
                patch.object(campaign, "run_operation", operation), patch.object(campaign, "retain_evidence", retention):
            result = campaign.execute_one(ROOT, self.plan,
                {"supportedAttemptIds": [entry["attemptId"]], "instrumentationParserSha256": "a" * 64},
                {"environment": "E36-GAPI"}, Path("unused-adb"), entry["attemptId"], Path(directory) / "attempt")

        self.assertEqual(entry["mutationVariants"], result["startedVariants"])
        self.assertEqual(len(entry["mutationVariants"]), len(result["mutationVariantResults"]))
        self.assertTrue(all(item["assessment"]["verdict"] == "FAIL" for item in result["mutationVariantResults"]))
        self.assertEqual(len(entry["mutationVariants"]), sum(name == "CLEANUP" for name, _variant in calls))
        self.assertTrue(result["rawRetentionComplete"])
        self.assertEqual("VERIFIED", result["cleanupResult"])

    def test_controller_exception_stops_after_preserving_current_variant(self):
        entry = next(e for e in self.plan["entries"] if e.get("caseId") == "COR-03" and e["environment"] == "E36-GAPI")
        calls = []

        class Transport:
            def __init__(self, _adb, _session, directory):
                self.directory = directory

            def verify_device(self, _source):
                pass

            def extract_prefix(self, selected, observed, label="recovered"):
                path = self.directory / (label + ".pcm")
                path.write_bytes(campaign.fixture_bytes(selected["seed"], observed["recoveredEnd"]))
                return path

        def operation(_transport, _plan, _entry, operation_name, variant):
            calls.append((operation_name, variant))
            if operation_name == "RECOVER":
                return {"recoveredEnd": 0, "receiptIdentity": "stable-receipt", "classification": "CORRUPTION_REJECTED"}
            if operation_name == "CLEANUP":
                raise ValueError("synthetic controller cleanup failure")
            return {}

        with tempfile.TemporaryDirectory() as directory, \
                patch.object(campaign, "validate_plan"), patch.object(campaign, "validate_execution_gate"), \
                patch.object(campaign, "OwnedAdbTransport", Transport), \
                patch.object(campaign, "run_operation", operation), \
                patch.object(campaign, "retain_evidence", return_value={"receipt": "retained"}):
            output = Path(directory) / "attempt"
            result = campaign.execute_one(ROOT, self.plan,
                {"supportedAttemptIds": [entry["attemptId"]], "instrumentationParserSha256": "a" * 64},
                {"environment": "E36-GAPI"}, Path("unused-adb"), entry["attemptId"], output)
            self.assertTrue((output / "attempt-result.json").is_file())

        self.assertEqual([entry["mutationVariants"][0]], result["startedVariants"])
        self.assertEqual("UNVERIFIED_STOP_NO_AUTOMATIC_RETRY", result["cleanupResult"])
        self.assertIn("controller cleanup failure", result["controllerError"])
        self.assertFalse(any(variant != entry["mutationVariants"][0] for _name, variant in calls))

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
            observation = {"retentionSnapshotConsistent": True, "retentionArtifacts": [artifact],
                           "artifactManifestSha256": campaign.android_artifact_manifest_sha256([artifact])}
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


class OwnedAdbReadonlyRetry(unittest.TestCase):
    """All subprocess calls are mocked; no ADB server or device is contacted."""
    def make_transport(self):
        directory = Path(tempfile.mkdtemp())
        transport = object.__new__(campaign.OwnedAdbTransport)
        transport.adb, transport.session, transport.directory, transport.sequence = Path("adb"), {"adbPort": 5037, "serial": "emulator-5556"}, directory, 0
        return transport

    @staticmethod
    def completed(code, stdout=b"", stderr=b""):
        return subprocess.CompletedProcess([], code, stdout, stderr)

    def test_crlf_retry_preserves_two_native_receipts(self):
        transport = self.make_transport()
        args = ["shell", "run-as", campaign.PACKAGE, "stat", "-c", "%F", "files/campaign"]
        first = self.completed(1, b"", b"* daemon still not running\r\nerror: cannot connect to daemon at tcp:127.0.0.1:5037: cannot connect\r\n")
        with patch.object(campaign.subprocess, "run", side_effect=[first, self.completed(0, b"directory\n")]) as native:
            self.assertEqual(transport.run(args, "retention-stat", retry_readonly_connection_once=True).stdout, b"directory\n")
        self.assertEqual(native.call_count, 2)
        self.assertEqual(transport.sequence, 2)
        self.assertEqual(json.loads((transport.directory / "001-retention-stat.result.json").read_text()), {"nativeExit": 1, "timedOut": False})
        self.assertEqual(json.loads((transport.directory / "002-retention-stat.result.json").read_text()), {"nativeExit": 0, "timedOut": False})

    def test_repeated_failure_and_nonmatching_failures_stop_without_extra_retry(self):
        args = ["shell", "run-as", campaign.PACKAGE, "stat", "-c", "%F", "files/campaign"]
        eligible = self.completed(1, b"", b"* daemon still not running\r\nerror: cannot connect to daemon at tcp:127.0.0.1:5037: cannot connect\r\n")
        transport = self.make_transport()
        with patch.object(campaign.subprocess, "run", side_effect=[eligible, eligible]) as native:
            with self.assertRaises(ValueError): transport.run(args, "retention-stat", retry_readonly_connection_once=True)
        self.assertEqual(native.call_count, 2)
        for response in (self.completed(1, b"", b"* daemon still not running\r\nerror: cannot connect to daemon at tcp:127.0.0.1:5038: cannot connect\r\n"), self.completed(1, b"partial", b"* daemon still not running\r\nerror: cannot connect to daemon at tcp:127.0.0.1:5037: cannot connect\r\n"), self.completed(2, b"", b"* daemon still not running\r\nerror: cannot connect to daemon at tcp:127.0.0.1:5037: cannot connect\r\n")):
            transport = self.make_transport()
            with patch.object(campaign.subprocess, "run", return_value=response) as native:
                with self.assertRaises(ValueError): transport.run(args, "retention-stat", retry_readonly_connection_once=True)
            self.assertEqual(native.call_count, 1)

    def test_timeout_mutation_and_default_verification_do_not_retry(self):
        args = ["shell", "run-as", campaign.PACKAGE, "stat", "-c", "%F", "files/campaign"]
        transport = self.make_transport()
        with patch.object(campaign.subprocess, "run", side_effect=subprocess.TimeoutExpired(args, 30)) as native:
            with self.assertRaises(ValueError): transport.run(args, "retention-stat", retry_readonly_connection_once=True)
        self.assertEqual(native.call_count, 1)
        transport = self.make_transport()
        with patch.object(campaign.subprocess, "run") as native:
            with self.assertRaises(ValueError): transport.run(["shell", "run-as", campaign.PACKAGE, "kill", "-9", "42"], "sigkill", retry_readonly_connection_once=True)
        native.assert_not_called()
        transport = self.make_transport()
        failed = self.completed(1, b"", b"* daemon still not running\r\nerror: cannot connect to daemon at tcp:127.0.0.1:5037: cannot connect\r\n")
        with patch.object(campaign.subprocess, "run", return_value=failed) as native:
            with self.assertRaises(ValueError): transport.run(["get-state"], "device-state")
        self.assertEqual(native.call_count, 1)

    def test_all_three_eligible_shapes_and_unsafe_or_instrumentation_rejection(self):
        shapes = [
            ["shell", "run-as", campaign.PACKAGE, "stat", "-c", "%F", "files/campaign"],
            ["exec-out", "run-as", campaign.PACKAGE, "head", "-c", "1", "files/campaign/a"],
            ["exec-out", "run-as", campaign.PACKAGE, "readlink", "-n", "files/campaign/a"],
        ]
        failed = self.completed(1, b"", b"* daemon still not running\r\nerror: cannot connect to daemon at tcp:127.0.0.1:5037: cannot connect\r\n")
        for args in shapes:
            transport = self.make_transport()
            with patch.object(campaign.subprocess, "run", side_effect=[failed, self.completed(0)]) as native:
                transport.run(args, "retention-read", retry_readonly_connection_once=True)
            self.assertEqual(native.call_count, 2)
        for args in (["shell", "run-as", campaign.PACKAGE, "stat", "-c", "%F", "files/../campaign"], ["exec-out", "run-as", campaign.PACKAGE, "head", "-c", "-1", "files/campaign/a"], ["exec-out", "run-as", campaign.PACKAGE, "readlink", "files/campaign/a"], ["shell", "am", "instrument", "-w"]):
            transport = self.make_transport()
            with patch.object(campaign.subprocess, "run") as native:
                with self.assertRaises(ValueError): transport.run(args, "forbidden", retry_readonly_connection_once=True)
            native.assert_not_called()

    def test_retain_evidence_marks_stat_head_and_readlink_retryable(self):
        class Transport:
            def __init__(self, directory): self.directory, self.calls = directory, []
            def run(self, arguments, label, **kwargs):
                self.calls.append((arguments, kwargs))
                if "stat" in arguments:
                    value = b"symbolic link\n" if arguments[-1].endswith("a-link") else b"directory\n" if arguments[-1] != "files/campaign/id/z-rows.json" else b"regular file\n"
                else: value = b""
                return subprocess.CompletedProcess([], 0, value, b"")
        artifacts = [
            {"relativePath": "files/campaign/id/a-link", "bytes": 0, "sha256": hashlib.sha256(b"").hexdigest(), "role": "RETAINED_RAW_ARTIFACT", "kind": "SYMLINK_TARGET_TEXT"},
            {"relativePath": "files/campaign/id/z-rows.json", "bytes": 0, "sha256": hashlib.sha256(b"").hexdigest(), "role": "CONSISTENT_JOURNAL_SNAPSHOT"},
        ]
        observation = {"retentionArtifacts": artifacts, "retentionSnapshotConsistent": True, "artifactManifestSha256": campaign.android_artifact_manifest_sha256(artifacts), "classification": "VALID"}
        transport = Transport(Path(tempfile.mkdtemp()))
        campaign.retain_evidence(transport, {"source": SOURCE}, {"attemptId": "id", "runId": "0" * 32}, observation, "DEFAULT")
        self.assertTrue(any(call[0][0] == "shell" and call[1].get("retry_readonly_connection_once") is True for call in transport.calls))
        self.assertTrue(any("head" in call[0] and call[1].get("retry_readonly_connection_once") is True for call in transport.calls))
        self.assertTrue(any("readlink" in call[0] and call[1].get("retry_readonly_connection_once") is True for call in transport.calls))


class AndroidWireRetentionManifest(unittest.TestCase):
    def test_android_slash_escaped_wire_manifest_and_declared_digest_are_required(self):
        artifacts = [{"bytes": 0, "relativePath": "files/campaign/id/rows.json", "role": "CONSISTENT_JOURNAL_SNAPSHOT", "sha256": hashlib.sha256(b"").hexdigest()}]
        digest = campaign.android_artifact_manifest_sha256(artifacts)
        self.assertNotEqual(digest, campaign.digest_json(artifacts))
        self.assertEqual(digest, hashlib.sha256(b'[{"bytes":0,"relativePath":"files\\/campaign\\/id\\/rows.json","role":"CONSISTENT_JOURNAL_SNAPSHOT","sha256":"e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"}]').hexdigest())
        self.assertEqual(campaign.digest_json({"a": "/"}), hashlib.sha256(b'{"a":"/"}').hexdigest())
        with self.assertRaises(ValueError):
            campaign.retain_evidence(type("T", (), {"directory": Path(tempfile.mkdtemp())})(), {"source": SOURCE}, {"attemptId": "id", "runId": "0" * 32}, {"retentionArtifacts": artifacts, "retentionSnapshotConsistent": True, "artifactManifestSha256": "0" * 64}, "DEFAULT")


class ReducedE36Selection(unittest.TestCase):
    """The reduced internal scope is a manifest over the immutable 600-plan."""

    def setUp(self):
        self.campaign_fixture = AlphaCampaignAdmission()
        self.campaign_fixture.setUp()
        self.addCleanup(self.campaign_fixture.doCleanups)
        self.source = self.campaign_fixture.source
        historical = self.campaign_fixture.gate["alphaCampaign"]["historicalSource"]
        self.original = campaign.build_plan(ROOT, "PHASE_A", historical, 20260914)
        self.execution = campaign.build_plan(ROOT, "PHASE_A", self.source, 20260914, "E36RED01")
        self.original_packet = self.campaign_fixture.preflight_root / "execution-packet-d3bc6ac-v5" / "phase_a-d3bc6ac.json"
        self.original_packet.parent.mkdir(parents=True, exist_ok=True)
        self.original_packet.write_bytes(campaign.canonical(self.original))
        plan_pin = patch.object(campaign, "ALPHA_PREFLIGHT_FULL_PLAN_SHA256",
                                hashlib.sha256(self.original_packet.read_bytes()).hexdigest())
        plan_pin.start()
        self.addCleanup(plan_pin.stop)

    def test_selects_the_exact_reduced_population_and_maps_fresh_identity(self):
        selection = campaign.build_reduced_e36_selection(self.original, self.execution, ())
        self.assertEqual(selection["scope"], "INTERNAL_ALPHA_E36_REDUCED_114")
        self.assertEqual((selection["faultBaseCount"], selection["hardKillBaseCount"], selection["variantCount"]), (90, 24, 165))
        self.assertEqual(len(selection["entries"]), 114)
        anchor = next(entry for entry in selection["entries"] if entry["originalBaseAttemptId"] == "PA-MICROFILE-K08-E36-GAPI-05")
        self.assertEqual(anchor["attemptId"], "E36RED01-PA-MICROFILE-K08-E36-GAPI-05")
        self.assertEqual(selection["originalPlanManifestSha256"], campaign.digest_json(self.original))
        campaign.validate_reduced_e36_selection(self.original, self.execution, selection)

    def test_completed_original_bases_are_preferred_without_replacing_the_mandatory_k08_slot(self):
        choices = [entry["attemptId"] for entry in self.original["entries"]
                   if entry["environment"] == "E36-GAPI" and entry["kind"] == "FAULT"
                   and entry["candidateId"] == "REC-STREAM-TINK" and entry["caseId"] == "COR-01"]
        preferred = choices[-1]
        alternate_k08 = next(entry["attemptId"] for entry in self.original["entries"]
                             if entry["environment"] == "E36-GAPI" and entry["kind"] == "HARD_KILL"
                             and entry["candidateId"] == "REC-MICROFILE-TINK" and entry["stratumId"] == "K08"
                             and entry["attemptId"] != "PA-MICROFILE-K08-E36-GAPI-05")
        selection = campaign.build_reduced_e36_selection(self.original, self.execution, [preferred, alternate_k08])
        selected = next(entry for entry in selection["entries"] if entry["requirementId"] == "FAULT:REC-STREAM-TINK:COR-01")
        self.assertEqual(selected["originalBaseAttemptId"], preferred)
        self.assertIn("PA-MICROFILE-K08-E36-GAPI-05", selection["originalBaseAttemptIds"])
        self.assertIn(alternate_k08, selection["completedBaseAttemptIds"])
        self.assertNotIn(alternate_k08, selection["selectedCompletedBaseAttemptIds"])

    def test_rejects_unknown_duplicate_unselected_and_identity_drift(self):
        selection = campaign.build_reduced_e36_selection(self.original, self.execution, ())
        for mutation in (
            lambda s: s["originalBaseAttemptIds"].append("PA-STREAM-BOGUS-E36-GAPI-01"),
            lambda s: s["originalBaseAttemptIds"].append(s["originalBaseAttemptIds"][0]),
            lambda s: s["entries"].pop(),
            lambda s: s["entries"][0].update(attemptId="E36RED01-PA-STREAM-IDENTITY-DRIFT-E36-GAPI-01"),
        ):
            altered = copy.deepcopy(selection)
            mutation(altered)
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                campaign.validate_reduced_e36_selection(self.original, self.execution, altered)

    def test_reduced_admission_binds_the_selection_and_excludes_other_alpha_modes(self):
        selection = campaign.build_reduced_e36_selection(self.original, self.execution, ())
        proof_dir = Path(tempfile.mkdtemp())
        self.addCleanup(lambda: __import__("shutil").rmtree(proof_dir, ignore_errors=True))
        owner = ROOT / "docs/stage0/DORA_0D6_ALPHA_REDUCED_SCOPE_OWNER_DECISION_20260915.md"
        proof = {"path": str(owner), "sha256": hashlib.sha256(owner.read_bytes()).hexdigest()}
        original_plan = self.original_packet
        original_plan_proof = {"path": str(original_plan), "sha256": hashlib.sha256(original_plan.read_bytes()).hexdigest(),
                               "manifestSha256": campaign.digest_json(self.original)}
        gate = copy.deepcopy(self.campaign_fixture.gate)
        campaign_decision = gate.pop("alphaCampaign")
        gate.update({
            "manifestSha256": campaign.digest_json(self.execution),
            "supportedAttemptIds": selection["supportedAttemptIds"],
            "supportedBaseAttemptIds": selection["originalBaseAttemptIds"],
            "reducedSelection": selection,
            "alphaReduced": {
                "decisionId": "DORA_0D6_ALPHA_REDUCED_SCOPE_20260915",
                "scope": "INTERNAL_ALPHA_E36_REDUCED_114", "source": copy.deepcopy(self.source),
                "historicalSource": copy.deepcopy(campaign_decision["historicalSource"]),
                "sourceEquivalence": copy.deepcopy(campaign_decision["sourceEquivalence"]),
                "originalPlanSource": copy.deepcopy(self.original["source"]),
                "originalPlanManifestSha256": selection["originalPlanManifestSha256"],
                "selectionManifestSha256": campaign.digest_json(selection),
            },
        })
        gate["proofs"].update({"ownerDecision": proof, "originalPlan": original_plan_proof})
        campaign.validate_alpha_reduced(self.execution, gate)
        campaign.validate_execution_gate(self.execution, gate)
        alternate_seed = 20260913
        alternate_original = campaign.build_plan(ROOT, "PHASE_A", self.original["source"], alternate_seed)
        alternate_execution = campaign.build_plan(ROOT, "PHASE_A", self.source, alternate_seed, "E36RED02")
        alternate_selection = campaign.build_reduced_e36_selection(alternate_original, alternate_execution, ())
        alternate_plan = proof_dir / "self-consistent-wrong-original-plan.json"
        alternate_plan.write_bytes(campaign.canonical(alternate_original))
        alternate_gate = copy.deepcopy(gate)
        alternate_gate["manifestSha256"] = campaign.digest_json(alternate_execution)
        alternate_gate["supportedAttemptIds"] = alternate_selection["supportedAttemptIds"]
        alternate_gate["supportedBaseAttemptIds"] = alternate_selection["originalBaseAttemptIds"]
        alternate_gate["reducedSelection"] = alternate_selection
        alternate_gate["alphaReduced"].update(
            originalPlanManifestSha256=alternate_selection["originalPlanManifestSha256"],
            selectionManifestSha256=campaign.digest_json(alternate_selection))
        alternate_gate["proofs"]["originalPlan"] = {
            "path": str(alternate_plan), "sha256": hashlib.sha256(alternate_plan.read_bytes()).hexdigest(),
            "manifestSha256": alternate_selection["originalPlanManifestSha256"],
        }
        with self.assertRaises(ValueError):
            campaign.validate_alpha_reduced(alternate_execution, alternate_gate)
        for mutation in (
                lambda g: g["retainedPreflight"].pop("results"),
                lambda g: g["alphaReduced"]["source"].update(appApkSha256="0" * 64),
                lambda g: g["alphaReduced"].pop("sourceEquivalence"),
                lambda g: g["reducedSelection"]["entries"][0]["mutationVariants"].pop(),
                lambda g: g["supportedAttemptIds"].append("E36RED01-PA-FOREIGN-E36-GAPI-01"),
                lambda g: g["proofs"]["ownerDecision"].update(path=str(original_plan), sha256=hashlib.sha256(original_plan.read_bytes()).hexdigest())):
            altered = copy.deepcopy(gate)
            mutation(altered)
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                campaign.validate_alpha_reduced(self.execution, altered)
        historical_drift = copy.deepcopy(self.original)
        historical_drift["entries"][0]["seed"] += 1
        original_plan.write_bytes(campaign.canonical(historical_drift))
        gate["proofs"]["originalPlan"]["sha256"] = hashlib.sha256(original_plan.read_bytes()).hexdigest()
        with self.assertRaises(ValueError):
            campaign.validate_alpha_reduced(self.execution, gate)
        gate["alphaCampaign"] = {}
        with self.assertRaises(ValueError):
            campaign.validate_alpha_reduced(self.execution, gate)

    def test_selection_retains_a_distinct_historical_source_plan_binding(self):
        historical = {"commit": "5" * 40, "tree": "8" * 40,
                      "appApkSha256": "c" * 64, "testApkSha256": "d" * 64}
        original = campaign.build_plan(ROOT, "PHASE_A", historical, 20260914)
        selection = campaign.build_reduced_e36_selection(original, self.execution, ())
        self.assertEqual(selection["originalPlanSource"], historical)
        self.assertEqual(selection["executionPlanSource"], self.source)
        campaign.validate_reduced_e36_selection(original, self.execution, selection)


if __name__ == "__main__":
    unittest.main()
