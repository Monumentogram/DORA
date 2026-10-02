"""Prospective instant-control denominator, thresholds and successor negative controls."""
import unittest
from unittest.mock import patch
import validate_instant_recording as gate


class InstantRecordingAdmissionTests(unittest.TestCase):
    def test_predecessor_dispatch_requires_complete_successor_gate(self):
        import validate_recording_latency as parent
        with patch.object(gate, 'candidate', return_value=True), patch.object(gate, 'validate_checkout', side_effect=ValueError('invalid instant')):
            with self.assertRaisesRegex(ValueError, 'invalid instant'):
                parent.validate_checkout()

    def test_changed_or_missing_successor_ci_rejected(self):
        import instant_recording_ci_profile as ci
        parent = ci.ANCHOR
        upgraded = ci.upgrade(parent)
        self.assertEqual(parent, ci.normalize(upgraded))
        for invalid in (upgraded.replace('validate_instant_recording.py', 'echo.py'), upgraded + ci.GATE):
            with self.assertRaises(ValueError):
                ci.normalize(invalid)
    def test_thirty_samples_nearest_rank_and_outlier_retained(self):
        self.assertEqual(dict(min=1, median=15.5, p95=29, max=2000), gate.distribution(list(range(1, 30)) + [2000]))

    def test_missing_invalid_or_extra_samples_fail_closed(self):
        for values in ([1] * 29, [1] * 31, [1] * 29 + [None], [False] * 30, [1] * 29 + [float('nan')], [1] * 29 + [-1]):
            with self.assertRaises(ValueError):
                gate.distribution(values)

    def test_no_relaxed_pause_or_resume_thresholds(self):
        self.assertEqual({'pause_ack': 100, 'pause_admission': 50, 'pause_native': 200, 'pause_confirmed': 250,
                          'resume_ack': 100, 'resume_native': 150, 'resume_first_pcm': 300, 'resume_confirmed': 300}, gate.LIMITS_MS)
        measurements = {key: [1] * 30 for key in gate.LIMITS_MS}
        measurements['pause_confirmed'] = [251] * 30
        self.assertFalse(gate.p95_thresholds(measurements)[1])
        del measurements['resume_confirmed']
        with self.assertRaises(ValueError):
            gate.p95_thresholds(measurements)

    def test_p95_helper_cannot_claim_whole_publication_ready(self):
        self.assertFalse(hasattr(gate, 'physical_verdict'))
        self.assertIn('PENDING_FINAL_PUBLICATION', gate.STATUS_HEADER)
        self.assertIn('8.4 = NOT_STARTED', gate.STATUS_HEADER)


if __name__ == '__main__':
    unittest.main()
