import unittest
import logical_recovery_ci_profile as ci
import validate_logical_recovery as gate
import run_logical_recovery_crash as crash


class LogicalRecoveryAdmissionTest(unittest.TestCase):
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
        self.assertEqual(21, len(crash.PHASES))
        self.assertEqual(len(set(crash.PHASES)), len(crash.PHASES))
        source = (gate.ROOT / 'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/persistence/LogicalRecoveryProcessDeathTest.kt').read_text()
        for phase in crash.PHASES: self.assertIn('"' + phase + '"', source)

    def test_ready_requires_exact_live_phase_marker(self):
        phase = crash.PHASES[0]
        good = f'INSTRUMENTATION_STATUS: stream=DORA_PERSISTENCE_READY:{phase}:123\nINSTRUMENTATION_STATUS_CODE: 2'
        self.assertEqual(123, crash.ready_pid(good, phase))
        for bad in (good + '\n' + good, good + '\nINSTRUMENTATION_CODE: -1', good.replace(':123', ':0')):
            with self.subTest(bad=bad), self.assertRaises(ValueError): crash.ready_pid(bad, phase)

    def test_death_requires_exact_pid_then_absence(self):
        crash.validate_death('123', '', 123)
        for before, after in [('124', ''), ('123', '123'), ('123 124', '')]:
            with self.subTest(before=before, after=after), self.assertRaises(ValueError):
                crash.validate_death(before, after, 123)

    def test_same_process_cannot_verify(self):
        phase = crash.PHASES[0]
        marker = f'INSTRUMENTATION_STATUS: stream=DORA_PERSISTENCE_VERIFIED:{phase}:123\nINSTRUMENTATION_STATUS_CODE: 2'
        with self.assertRaises(ValueError): crash.verified_pid(marker, phase, 123)


if __name__ == '__main__': unittest.main()
