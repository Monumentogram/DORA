"""Synthetic policy tests; no device or fake physical acceptance evidence."""
import copy
import unittest

from alpha_protocol import keyguard_action, watchdog_reason, validate_reduced_cycles, seal, verify_seal
from test_receipts import cycle, long_run


class AlphaPolicyTest(unittest.TestCase):
    def rows(self):
        rows = [cycle(n) for n in range(1, 61)]
        for row in rows:
            long = long_run()
            row.update(startConfirmedElapsedMs=1000, stopRequestedElapsedMs=6000,
                       admittedFrames=80000, durableFrames=80000, readbackFrames=80000,
                       vadProof=long['vadProof'], vadRuntime=long['vadRuntime'], vadInference=10,
                       sourceVersion=1, authorizationUnits=1, sourceFingerprint='b' * 64,
                       capCount=0, chunks=[dict(first=0, end=80000, processingFirst=0,
                           open='START', close='STOP', epochFingerprint='c' * 64,
                           chunkFingerprint='d' * 64, profileSha256=long['chunks'][0]['profileSha256'])])
        return rows

    def test_only_sixty_complete_real_cycles_pass(self):
        self.assertTrue(validate_reduced_cycles(self.rows())['pass'])
        for size in (59, 61, 200):
            with self.assertRaises(ValueError):
                validate_reduced_cycles([cycle(n) for n in range(1, size + 1)])

    def test_protected47_requires_explicit_governed_count_and_every_receipt(self):
        rows = self.rows()
        for row in rows:
            row['deletion']['ownerRecordingsPreserved'] = 47
        self.assertFalse(validate_reduced_cycles(rows)['pass'])
        self.assertTrue(validate_reduced_cycles(rows, protected_count=47)['pass'])
        rows[17]['deletion']['ownerRecordingsPreserved'] = 46
        self.assertFalse(validate_reduced_cycles(rows, protected_count=47)['pass'])

    def test_single_failure_remains_in_denominator(self):
        rows = self.rows()
        rows[0]['finalized'] = False
        result = validate_reduced_cycles(rows)
        self.assertEqual(result['attempts'], 60)
        self.assertFalse(result['pass'])

    def test_shortened_capture_cannot_pass(self):
        rows = self.rows()
        rows[0]['stopRequestedElapsedMs'] = 5999
        self.assertFalse(validate_reduced_cycles(rows)['pass'])

    def test_timed_rows_without_real_audio_vad_or_logical_proof_cannot_pass(self):
        for defect in ('frames', 'vad', 'chunk', 'source'):
            rows = self.rows(); row = rows[0]
            if defect == 'frames':
                row.update(admittedFrames=1, durableFrames=1, readbackFrames=1)
                row['chunks'][0]['end'] = 1
            elif defect == 'vad':
                row['vadInference'] = 0
            elif defect == 'chunk':
                row['chunks'][0]['first'] = 1
            else:
                row['authorizationUnits'] = 2
            self.assertFalse(validate_reduced_cycles(rows)['pass'], defect)

    def test_only_proven_nonsecure_keyguard_may_be_dismissed(self):
        self.assertEqual(keyguard_action(False, True), 'DISMISS_NONSECURE')
        self.assertEqual(keyguard_action(False, False), 'NONE')
        for secure in (True, None, 'false'):
            with self.assertRaises(ValueError):
                keyguard_action(secure, True)

    def healthy(self):
        return dict(adbOnline=True, supervisorAlive=True, hostHeartbeatAgeMs=0,
                    elapsedMs=1000, hardDeadlineMs=10000, thermal=0,
                    freeBytes=1000000000, recordingExpected=True, fgsPresent=True)

    def test_watchdog_classifies_each_failure(self):
        self.assertIsNone(watchdog_reason(self.healthy()))
        for key, value, reason in [
            ('adbOnline', False, 'ADB_UNAVAILABLE'),
            ('supervisorAlive', False, 'ORCHESTRATOR_EXITED'),
            ('hostHeartbeatAgeMs', 30001, 'HOST_HEARTBEAT_EXPIRED'),
            ('elapsedMs', 10001, 'HARD_DEADLINE'),
            ('thermal', 3, 'THERMAL_SEVERE'),
            ('freeBytes', 16777215, 'STORAGE_RESERVE_EXHAUSTED'),
            ('fgsPresent', False, 'RECORDING_FGS_MISSING')]:
            row = self.healthy(); row[key] = value
            self.assertEqual(watchdog_reason(row), reason)

    def test_missing_safety_evidence_never_becomes_zero(self):
        for key in self.healthy():
            row = self.healthy(); row[key] = None
            with self.assertRaises(ValueError):
                watchdog_reason(row)

    def test_protocol_changes_require_a_new_explicit_seal(self):
        protocol = {'cycles': 60, 'durationMs': 5000, 'longMs': 3600000,
                    'ownerDecision': 'OD-86B-REDUCED-AUTONOMOUS-ALPHA-ACCEPTANCE'}
        digest = seal(protocol)
        verify_seal(copy.deepcopy(protocol), digest)
        for key in ('cycles', 'durationMs', 'longMs'):
            changed = copy.deepcopy(protocol); changed[key] -= 1
            with self.assertRaises(ValueError):
                verify_seal(changed, digest)


if __name__ == '__main__':
    unittest.main()
