"""Negative controls for the Stage8.4 successor's exact scope and credential-free CI."""
import json
from pathlib import Path
import unittest
import vad_runtime_ci_profile as ci
import validate_vad_runtime as gate
import audit_vad_publication as audit


class RuntimeGovernanceTest(unittest.TestCase):
    def test_publication_canaries_and_clean_metadata(self):
        self.assertFalse(audit.findings('receipt.json', b'{"frames":16000}'))
        self.assertTrue(audit.findings('private.aar', b'x'))
        self.assertTrue(audit.findings('report.txt', b'gh' + b'p_' + b'A' * 36))
        self.assertTrue(audit.findings('report.txt', b'RIFF' + b'x' * 8))

    def test_missing_crash_gate_fails(self):
        parent = ci.ANCHOR + ci.CRASH_ANCHOR + ci.OLD_INVENTORY
        bad = ci.upgrade(parent).replace(ci.CRASH_GATE, '')
        with self.assertRaises(ValueError):
            ci.normalize(bad)

    def test_unrelated_file_is_rejected(self):
        with self.assertRaises(ValueError):
            gate.validate_paths({'approved.kt', 'unrelated.md'}, {'approved.kt'})

    def test_missing_approved_file_is_rejected(self):
        with self.assertRaises(ValueError):
            gate.validate_paths({'a.kt'}, {'a.kt', 'b.kt'})

    def test_exact_delta_passes(self):
        gate.validate_paths({'a.kt', 'b.kt'}, {'a.kt', 'b.kt'})

    def test_commit_subset_does_not_broaden_inventory(self):
        gate.validate_paths({'a.kt'}, {'a.kt', 'b.kt'}, complete=False)
        with self.assertRaises(ValueError):
            gate.validate_paths({'c.kt'}, {'a.kt', 'b.kt'}, complete=False)

    def test_private_binary_cannot_be_allowlisted(self):
        for suffix in ('.aar', '.onnx', '.wav', '.pcm', '.apk', '.so', '.mp3'):
            with self.subTest(suffix=suffix), self.assertRaises(ValueError):
                gate.validate_paths({'candidate' + suffix}, {'candidate' + suffix})

    def test_ci_change_is_exactly_reversible(self):
        parent = ci.ANCHOR + ci.CRASH_ANCHOR + 'old checks\n' + ci.OLD_INVENTORY
        self.assertEqual(parent, ci.normalize(ci.upgrade(parent)))

    def test_missing_ci_unit_tests_fail(self):
        parent = ci.ANCHOR + ci.CRASH_ANCHOR + ci.OLD_INVENTORY
        bad = ci.upgrade(parent).replace(':ml:vad-api:testDebugUnitTest', '')
        with self.assertRaises(ValueError):
            ci.normalize(bad)

    def test_missing_inventory_fails(self):
        with self.assertRaises(ValueError):
            ci.normalize(ci.GATE)

    def test_profile_freeze_is_unchanged(self):
        self.assertEqual(gate.PROFILE_SHA, gate.digest(gate.normalized(gate.ROOT / gate.PROFILE)))
        p = json.loads((gate.ROOT / gate.PROFILE).read_text())
        self.assertEqual((4800, 16000, 32000, 1440000, 9600000, 32000),
            tuple(p[k] for k in ('onsetFrames', 'hysteresisFrames', 'preRollFrames',
                                'semanticSilenceFrames', 'technicalCapFrames', 'overlapFrames')))

    def test_new_inventory_preserves_every_predecessor_test(self):
        old = json.loads((gate.ROOT / ci.OLD_INVENTORY).read_text())
        new = json.loads((gate.ROOT / ci.INVENTORY).read_text())
        self.assertLessEqual(set(old['tests']), set(new['tests']))
        self.assertEqual(old['no_credential_tests'], new['no_credential_tests'])


if __name__ == '__main__':
    unittest.main()
