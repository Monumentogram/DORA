import unittest
import logical_recording_ci_profile as ci
import validate_logical_recording as gate
import run_logical_recording_crash as crash


class LogicalRecordingAdmissionTest(unittest.TestCase):
    def test_status_preserves_exact_history_and_pending_publication(self):
        old = 'historical accepted status\n'
        self.assertEqual(old, gate.validate_status_projection(gate.STATUS_HEADER + old, old))
        for changed in (gate.STATUS_HEADER.replace('PENDING_FINAL_PUBLICATION', 'PASS') + old,
                        gate.STATUS_HEADER + old + 'edited'):
            with self.assertRaises(ValueError): gate.validate_status_projection(changed, old)

    def test_only_exact_paths_are_admitted(self):
        gate.validate_paths({'one'}, {'one'})
        with self.assertRaises(ValueError): gate.validate_paths({'one', 'extra'}, {'one'})
        with self.assertRaises(ValueError): gate.validate_paths(set(), {'one'})

    def test_binary_cannot_be_allowlisted(self):
        for suffix in ('.aar', '.onnx', '.wav', '.pcm', '.apk', '.so'):
            with self.subTest(suffix=suffix), self.assertRaises(ValueError): gate.validate_paths({'x' + suffix}, {'x' + suffix})

    def test_ci_changes_reverse_exactly(self):
        old = ci.ANCHOR + ci.CRASH_ANCHOR + ci.OLD_INVENTORY
        self.assertEqual(old, ci.normalize(ci.upgrade(old)))

    def test_missing_crash_gate_rejects(self):
        old = ci.ANCHOR + ci.CRASH_ANCHOR + ci.OLD_INVENTORY
        with self.assertRaises(ValueError): ci.normalize(ci.upgrade(old).replace(ci.CRASH_GATE, ''))

    def test_missing_unit_gate_rejects(self):
        old = ci.ANCHOR + ci.CRASH_ANCHOR + ci.OLD_INVENTORY
        with self.assertRaises(ValueError): ci.normalize(ci.upgrade(old).replace(ci.GATE, ''))

    def test_all_process_death_phases_are_present(self):
        self.assertEqual(('SOURCE_FINALIZED', 'PROJECTION_READ', 'METADATA_PARTIAL', 'DELETE_PENDING'), crash.PHASES)
        source = (gate.ROOT / 'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/persistence/LogicalRecordingProcessDeathTest.kt').read_text()
        for phase in crash.PHASES: self.assertIn('"' + phase + '"', source)


if __name__ == '__main__': unittest.main()
