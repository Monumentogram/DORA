"""Negative controls: timing denominator/thresholds and additive inherited admission."""
import unittest
from types import SimpleNamespace
from unittest.mock import patch
import recording_latency_ci_profile as ci
import validate_recording_latency as gate


class LatencyAdmissionTests(unittest.TestCase):
    def test_combined_policy_projection_is_exact_not_a_token_bypass(self):
        source = (gate.ROOT / gate.POLICY_PATH).read_text(encoding='utf-8')
        projected = gate.project_policy_sources({gate.POLICY_PATH: source})
        self.assertNotIn('POLICY_QUERY', projected[gate.POLICY_PATH])
        for old, new in [('opens.get() == 1', 'opens.get() >= 1'),
                         ('row.columnCount == EXPECTED_POLICY.size', 'true'),
                         ('"wal", "2", "1", "2"', '"wal", "1", "1", "2"'),
                         ('verify(db)', 'Unit')]:
            self.assertIn(old, source)
            with self.assertRaises(ValueError):
                gate.project_policy_sources({gate.POLICY_PATH: source.replace(old, new)})

    def test_nearest_rank_and_all_twenty_values_are_retained(self):
        values = list(range(1, 20)) + [2000]
        self.assertEqual(dict(min=1, median=10.5, p95=19, max=2000), gate.distribution(values))
        for invalid in (values[:19], values + [1], values[:19] + [None], values[:19] + [float('nan')], values[:19] + [-1], [False] * 20):
            with self.assertRaises(ValueError):
                gate.distribution(invalid)

    def test_slow_pause_and_missing_metric_cannot_be_certified(self):
        measurements = {key: [1] * 20 for key in gate.LIMITS_MS}
        measurements['pause_confirmed'] = [1600] * 20
        stats, passed = gate.p95_thresholds(measurements)
        self.assertFalse(passed)
        self.assertEqual(1600, stats['pause_confirmed']['p95'])
        del measurements['resume_first_pcm']
        with self.assertRaises(ValueError):
            gate.p95_thresholds(measurements)

    def test_p95_helper_does_not_offer_a_physical_verdict_and_retains_outlier(self):
        stats, thresholds_met = gate.p95_thresholds({key: [1] * 19 + [100000] for key in gate.LIMITS_MS})
        self.assertTrue(thresholds_met)
        self.assertEqual(100000, stats['pause_confirmed']['max'])
        self.assertFalse(hasattr(gate, 'physical_verdict'))

    def test_thresholds_are_exact(self):
        self.assertEqual([100, 250, 500, 100, 500, 750, 750], list(gate.LIMITS_MS.values()))
        self.assertIn('PENDING_FINAL_PUBLICATION', gate.STATUS_HEADER)
        self.assertIn('8.4 = NOT_STARTED', gate.STATUS_HEADER)

    def test_changed_or_missing_ci_gate_and_inventory_rejected(self):
        parent = ci.ANCHOR + '\n' + ci.OLD_INVENTORY
        updated = ci.upgrade(parent)
        self.assertEqual(parent, ci.normalize(updated))
        for invalid in (updated.replace('validate_recording_latency.py', 'echo.py'),
                        updated.replace(ci.GATE, ''), updated.replace(ci.INVENTORY, ci.OLD_INVENTORY), updated + ci.GATE):
            with self.assertRaises(ValueError):
                ci.normalize(invalid)

    def test_predecessor_and_recovery_dispatch_requires_full_latency_gate(self):
        import validate_product_recording as recording
        import validate_poc_recovery_governance as recovery
        import validate_encrypted_persistence as persistence
        with patch.object(gate, 'candidate', return_value=True), patch.object(gate, 'validate_checkout', side_effect=ValueError('invalid latency')):
            with self.assertRaisesRegex(ValueError, 'invalid latency'):
                recording.validate_checkout()
        with patch.object(persistence, 'validate_checkout', side_effect=ValueError('invalid latency')):
            with self.assertRaisesRegex(ValueError, 'invalid latency'):
                recovery.rec_clean_integrated_candidate(SimpleNamespace(branch=gate.BRANCH))


if __name__ == '__main__':
    unittest.main()
