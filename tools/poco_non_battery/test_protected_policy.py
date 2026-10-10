"""Synthetic derivation checks; no private identities or physical authenticity claim."""
import copy
import hashlib
import json
from pathlib import Path
import sys
import unittest
import uuid

sys.path.insert(0, str(Path(__file__).parents[1]))
from poco_non_battery.protected_policy import derive


def identity(n):
    return str(uuid.UUID(int=n))


class ProtectedPolicyTest(unittest.TestCase):
    def setUp(self):
        self.value = {'format': 'DORA_PRIVATE_CATALOG_V1', 'schemaVersion': 3,
                      'keys': {'synthetic': {}}, 'keyProofs': {'synthetic': {}},
                      'verifiedHistoricalKeyProofs': 1, 'tables': {
                          'vault_binding': {'columns': ['ownerId', 'vaultId'], 'rows': [[identity(1), identity(2)]]},
                          'deletion_tombstone': {'columns': ['assetId'], 'rows': []},
                          'audio_asset': {'columns': ['recordingId', 'assetId', 'sessionId', 'ownerId', 'vaultId'],
                                          'rows': [[identity(100 + 3*n), identity(101 + 3*n), identity(102 + 3*n), identity(1), identity(2)] for n in range(47)]}}}
        self.profile = {'package': 'synthetic.package', 'model': 'synthetic', 'api': 34, 'firmware': 'synthetic'}

    def result(self, value=None, files=()):
        raw = json.dumps(value or self.value).encode()
        return json.loads(derive(raw, hashlib.sha256(raw).hexdigest(), self.profile, files))

    def test_exact47_and_file_only_namespaces(self):
        output = self.result(files=['runs/' + identity(999) + '/ciphertext'])
        self.assertEqual(len(output['protectedSources']), 47)
        self.assertIn(identity(999), output['historicalIdentifiers'])

    def test_deterministic(self):
        self.assertEqual(self.result(), self.result(copy.deepcopy(self.value)))

    def test_digest_mismatch(self):
        with self.assertRaises(ValueError):
            derive(json.dumps(self.value).encode(), '0' * 64, self.profile, [])

    def test_missing_source(self):
        self.value['tables']['audio_asset']['rows'].pop()
        with self.assertRaises(ValueError): self.result()

    def test_deleted_source_not_counted_as_live(self):
        self.value['tables']['deletion_tombstone']['rows'] = [[identity(101)]]
        with self.assertRaises(ValueError): self.result()

    def test_vault_mismatch(self):
        self.value['tables']['audio_asset']['rows'][0][-1] = identity(77)
        with self.assertRaises(ValueError): self.result()

    def test_missing_key_proof(self):
        self.value['keyProofs'] = {}
        with self.assertRaises(ValueError): self.result()

    def test_noncanonical_identity(self):
        self.value['tables']['audio_asset']['rows'][0][0] = '*'
        with self.assertRaises(ValueError): self.result()


if __name__ == '__main__':
    unittest.main()
