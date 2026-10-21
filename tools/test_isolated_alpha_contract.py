"""Successor scope supplements, rather than changes, the accepted limited recording profile."""
import copy
import unittest

from validate_stage8_alpha import isolated_scope


class ScopeTests(unittest.TestCase):
    def setUp(self):
        self.contract = {
            'baseline': '1442f0db8a6936920a692913035d100f68871f81',
            'application_id': 'com.monumentogram.dora.alpha.test',
            'physical_operations_authorized': False,
            'security_restoration': 'OPEN', 'stage_8_6': 'NOT_READY',
            'mandatory_additional_jobs': ['isolated-alpha-software', 'isolated-alpha-api28', 'isolated-alpha-api36'],
            'implementation_paths': ['tools/example.py'],
        }

    def test_narrow_scope(self):
        self.assertEqual({'tools/example.py'}, isolated_scope(self.contract))

    def test_no_physical_authorization_or_release_gate_change(self):
        for key, value in [('baseline', '0'*40), ('application_id', 'com.monumentogram.dora'),
                           ('physical_operations_authorized', True), ('security_restoration', 'PASS'),
                           ('stage_8_6', 'PASS'), ('mandatory_additional_jobs', [])]:
            row = copy.deepcopy(self.contract)
            row[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                isolated_scope(row)

    def test_no_wildcard_or_traversal_scope(self):
        for paths in (['**'], ['../other'], ['/tmp/file'], ['tools/a', 'tools/a']):
            row = copy.deepcopy(self.contract)
            row['implementation_paths'] = paths
            with self.subTest(paths=paths), self.assertRaises(ValueError):
                isolated_scope(row)


if __name__ == '__main__':
    unittest.main()
