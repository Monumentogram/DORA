import copy
import unittest
from validate_stage8_alpha import owner_pilot_scope


class OwnerPilotScopeTests(unittest.TestCase):
    def setUp(self):
        self.contract = {
            'baseline': '52e4ce8bdf074ec021ae52c3c38ad9d26c24e128',
            'application_id': 'com.monumentogram.dora.alpha.pilot',
            'physical_operations_authorized': False,
            'security_restoration': 'OPEN', 'stage_8_6': 'NOT_READY',
            'long02_root_cause': 'NOT_PROVEN', 'production_upgrade_safety': 'UNPROVEN',
            'distribution': 'BLOCKED',
            'implementation_paths': ['tools/owner_alpha_pilot_artifact.py'],
        }

    def test_software_only_exact_package_scope(self):
        self.assertEqual(set(self.contract['implementation_paths']), owner_pilot_scope(self.contract))

    def test_physical_or_historical_gate_changes_are_rejected(self):
        for key, value in [('baseline', '0' * 40), ('application_id', 'com.monumentogram.dora'),
                           ('physical_operations_authorized', True), ('security_restoration', 'CLOSED'),
                           ('stage_8_6', 'READY'), ('long02_root_cause', 'PROVEN'),
                           ('production_upgrade_safety', 'PASS'), ('distribution', 'APPROVED')]:
            changed = copy.deepcopy(self.contract)
            changed[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                owner_pilot_scope(changed)

    def test_scope_cannot_hide_other_changes(self):
        for paths in [[], ['../x'], ['*'], ['/x'], ['C:/x'], ['x', 'x']]:
            changed = copy.deepcopy(self.contract)
            changed['implementation_paths'] = paths
            with self.subTest(paths=paths), self.assertRaises(ValueError):
                owner_pilot_scope(changed)


if __name__ == '__main__':
    unittest.main()
