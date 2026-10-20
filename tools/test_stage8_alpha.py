"""Negative controls for the limited inventory, mandatory gates and real process-death proof."""
import copy
import json
from pathlib import Path
import unittest

import run_encrypted_persistence_device as device
import run_logical_recovery_crash as crash
import validate_stage8_alpha as alpha


class AlphaProfileTests(unittest.TestCase):
    def test_inventory_retains_every_inherited_short_security_test(self):
        root = Path(__file__).resolve().parents[1]
        old = json.loads((root / "docs/contracts/persistence-8.6c3-device-tests.json").read_text())
        current = json.loads((root / alpha.INVENTORY).read_text())
        alpha.validate_inventory(current, old)
        for mode in ("missing", "extra", "duplicate", "credential", "deferral"):
            bad = copy.deepcopy(current)
            if mode == "missing":
                bad["tests"].pop()
            elif mode == "extra":
                bad["tests"].append("example.Other#unexpected")
            elif mode == "duplicate":
                bad["tests"].append(bad["tests"][0])
            elif mode == "credential":
                bad["no_credential_tests"] = []
            else:
                bad["deferred_tests"].append("another")
            with self.subTest(mode=mode), self.assertRaises(ValueError):
                alpha.validate_inventory(bad, old)

    def test_profile_cannot_increase_cap_or_self_certify_release(self):
        profile = json.loads((alpha.ROOT / alpha.PROFILE).read_text())
        alpha.validate_profile(profile)
        changes = {"max_alpha_frames": 4_800_001, "outstanding_frames": 256_001,
                   "physical_smoke": "PASS", "security_release_gate": "CLOSED",
                   "signed_alpha_release": "ALLOWED", "historical_stage_8_6": "PASS",
                   "long02_physical_protection": "INSTALLED", "long02_root_cause": "PROVEN"}
        for key, value in changes.items():
            with self.subTest(key=key), self.assertRaises(ValueError):
                alpha.validate_profile({**profile, key: value})

    def test_exact_inventory_selection_does_not_expand_package(self):
        names = {"example.B#two", "example.A#one"}
        self.assertEqual(["-e", "class", "example.A#one,example.B#two"],
                         device.selectors(names, exact_inventory=True))
        self.assertEqual(["-e", "package", device.PACKAGE], device.selectors(names))

    def test_only_four_long_cap_phases_are_deferred(self):
        self.assertEqual({"BEFORE_CAP", "CAP_CLOSED", "CAP_OPEN_EMPTY", "CAP_OVERLAP"},
                         set(crash.PHASES) - set(crash.ALPHA_PHASES))
        self.assertEqual(18, len(crash.ALPHA_PHASES))

    def test_alpha_process_marker_must_match_phase_and_unique_pid(self):
        text = ("INSTRUMENTATION_STATUS: stream=DORA_PERSISTENCE_READY:ALPHA_299:42\n"
                "INSTRUMENTATION_STATUS_CODE: 2\n")
        self.assertEqual(42, crash.ready_pid(text, crash.ALPHA_LIMIT_PHASE))
        for bad in (text + text, text.replace("ALPHA_299", "COMMITTED_OPEN"),
                    text + "INSTRUMENTATION_CODE: -1\n"):
            with self.assertRaises(ValueError):
                crash.ready_pid(bad, crash.ALPHA_LIMIT_PHASE)

    def test_alpha_verification_requires_new_process_and_exact_test(self):
        text = ("INSTRUMENTATION_STATUS: stream=DORA_PERSISTENCE_VERIFIED:ALPHA_299:43\n"
                "INSTRUMENTATION_STATUS_CODE: 2\n"
                f"INSTRUMENTATION_STATUS: class={crash.ALPHA_TEST.split('#')[0]}\n"
                f"INSTRUMENTATION_STATUS: test={crash.ALPHA_TEST.split('#')[1]}\n"
                "INSTRUMENTATION_STATUS_CODE: 0\nOK (1 test)\nINSTRUMENTATION_CODE: -1\n")
        self.assertEqual(43, crash.verified_pid(text, crash.ALPHA_LIMIT_PHASE, 42,
                                              expected_test=crash.ALPHA_TEST))
        for previous, test in ((43, crash.ALPHA_TEST), (42, crash.TEST)):
            with self.assertRaises(ValueError):
                crash.verified_pid(text, crash.ALPHA_LIMIT_PHASE, previous, expected_test=test)


if __name__ == "__main__":
    unittest.main()
