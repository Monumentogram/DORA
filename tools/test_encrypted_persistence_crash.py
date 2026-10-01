"""Reject crash receipts without a reached phase, killed process and fresh verifier."""
import unittest
import subprocess
import sys

import run_encrypted_persistence_crash as crash


def marker(kind, phase, pid):
    return (f'INSTRUMENTATION_STATUS: stream=DORA_PERSISTENCE_{kind}:{phase}:{pid}\n'
            'INSTRUMENTATION_STATUS_CODE: 2\n')


def completed():
    return ('INSTRUMENTATION_STATUS: class=' + crash.TEST.split('#')[0] + '\n'
            'INSTRUMENTATION_STATUS: test=' + crash.TEST.split('#')[1] + '\n'
            'INSTRUMENTATION_STATUS_CODE: 0\nOK (1 test)\nINSTRUMENTATION_CODE: -1\n')


class CrashProtocolTests(unittest.TestCase):
    def blocked_child(self):
        process = subprocess.Popen([sys.executable, '-c', 'import sys; sys.stdin.read()'], stdin=subprocess.PIPE)

        def cleanup():
            if process.poll() is None:
                process.kill()
            process.wait(timeout=5)
            process.stdin.close()

        self.addCleanup(cleanup)
        return process

    def test_failed_android_cleanup_still_reaps_real_host_child(self):
        process = self.blocked_child()
        def stop():
            raise ValueError('Synthetic cleanup failure')
        with self.assertRaisesRegex(ValueError, 'Synthetic cleanup failure'):
            crash.cleanup_process(process, stop, timeout=0.01)
        self.assertIsNotNone(process.poll(), 'Host child survived cleanup failure')

    def test_cleanup_failure_cannot_mask_original_failure(self):
        process = self.blocked_child()
        original = ValueError('Original phase failure')
        def stop():
            raise ValueError('Secondary cleanup failure')
        crash.cleanup_process(process, stop, timeout=0.01, primary=original)
        self.assertIsNotNone(process.poll(), 'Host child survived cleanup failure')
        self.assertTrue(original.__notes__)

    def test_exact_ready_marker_binds_phase_and_live_process(self):
        self.assertEqual(123, crash.ready_pid(marker('READY', 'INTENT', 123), 'INTENT'))
        for output in ('', marker('VERIFIED', 'INTENT', 123), marker('READY', 'COMMITTED', 123),
                       marker('READY', 'INTENT', 0), marker('READY', 'INTENT', 123) * 2,
                       marker('READY', 'INTENT', 123) + completed()):
            with self.assertRaises(ValueError):
                crash.ready_pid(output, 'INTENT')

    def test_verifier_requires_new_process_and_successful_exact_test(self):
        output = marker('VERIFIED', 'PUBLISHED', 456) + completed()
        self.assertEqual(456, crash.verified_pid(output, 'PUBLISHED', 123))
        for changed in (output.replace(':456', ':123'), output.replace('PUBLISHED', 'INTENT'),
                        output.replace('CODE: 0', 'CODE: -2'), marker('VERIFIED', 'PUBLISHED', 456),
                        output.replace(crash.TEST.split('#')[1], 'differentTest'),
                        marker('VERIFIED', 'PUBLISHED', 456) + output):
            with self.assertRaises(ValueError):
                crash.verified_pid(changed, 'PUBLISHED', 123)

    def test_unknown_status_is_not_removed_from_verification(self):
        output = marker('VERIFIED', 'COMMITTED', 456) + 'INSTRUMENTATION_STATUS_CODE: 3\n' + completed()
        with self.assertRaises(ValueError):
            crash.verified_pid(output, 'COMMITTED', 123)

    def test_death_requires_exact_pid_before_and_absence_after(self):
        crash.validate_death('123', '', 123)
        for before, after in (('', ''), ('124', ''), ('123 124', ''), ('123', '123'), ('123', '124')):
            with self.assertRaises(ValueError):
                crash.validate_death(before, after, 123)


if __name__ == '__main__':
    unittest.main()
