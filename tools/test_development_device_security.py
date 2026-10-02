"""Negative controls for the bounded owner-authorized development successor."""
import copy
import unittest
from unittest.mock import patch
import validate_development_device_security as gate


class DevelopmentDeviceSecurityAdmissionTests(unittest.TestCase):
    def test_parent_must_dispatch_to_complete_successor(self):
        import validate_instant_recording as parent
        with patch.object(gate, 'candidate', return_value=True), patch.object(gate, 'validate_checkout', side_effect=ValueError('invalid successor')):
            with self.assertRaisesRegex(ValueError, 'invalid successor'):
                parent.validate_checkout()

    def test_ci_cannot_omit_release_negative_tests(self):
        import development_device_security_ci_profile as ci
        upgraded = ci.upgrade(ci.ANCHOR)
        self.assertEqual(ci.ANCHOR, ci.normalize(upgraded))
        for invalid in (upgraded.replace(':core:audio:testReleaseUnitTest', ':core:audio:testDebugUnitTest'), upgraded + ci.GATE):
            with self.assertRaises(ValueError):
                ci.normalize(invalid)

    def test_current_restoration_blocker_cannot_close_alpha(self):
        for changed in ({}, {'state': 'CLOSED'}, {'alpha_security_acceptance': True}):
            blocker = copy.deepcopy(gate.RESTORATION_BLOCKER)
            blocker.update(changed)
            with self.assertRaises(ValueError):
                gate.require_alpha_security_restored(blocker)

    def test_all_ten_restoration_checks_and_independent_receipt_are_required(self):
        blocker = copy.deepcopy(gate.RESTORATION_BLOCKER)
        blocker.update(state='CLOSED', alpha_security_acceptance=True, evidence_sha256='a' * 64)
        blocker['checks'] = {key: True for key in blocker['checks']}
        gate.require_alpha_security_restored(blocker)
        for key in blocker['checks']:
            missing = copy.deepcopy(blocker)
            missing['checks'][key] = False
            with self.assertRaises(ValueError):
                gate.require_alpha_security_restored(missing)
        blocker['evidence_sha256'] = None
        with self.assertRaises(ValueError):
            gate.require_alpha_security_restored(blocker)

    def test_historical_status_cannot_be_rewritten(self):
        self.assertEqual('history\n', gate.validate_status_projection(gate.STATUS_HEADER + 'history\n', 'history\n'))
        with self.assertRaises(ValueError):
            gate.validate_status_projection(gate.STATUS_HEADER + 'rewritten\n', 'history\n')


if __name__ == '__main__':
    unittest.main()
