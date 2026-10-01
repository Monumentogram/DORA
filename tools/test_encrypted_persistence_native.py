"""Mutations of the actual pinned native bundle, not simulated runtime evidence."""
import copy
import json
from pathlib import Path
import unittest

import validate_encrypted_persistence_native as native

ROOT = Path(__file__).resolve().parents[1]


class NativeAdmissionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.inputs = native.read_inputs(ROOT)

    def test_exact_bundle_and_distributable_notices(self):
        native.validate_bundle(*self.inputs)

    def test_native_sbom_matches_sources_binaries_licenses_and_edges(self):
        bom = json.loads((ROOT / 'android/vendor/sqlcipher/native-components.cdx.json').read_text())
        native.validate_sbom(bom, self.inputs[2])
        mutations = []
        for field in ('components', 'dependencies'):
            changed = copy.deepcopy(bom)
            changed[field].pop()
            mutations.append(changed)
        changed = copy.deepcopy(bom)
        changed['components'][0]['licenses'] = [{'expression': 'MIT'}]
        mutations.append(changed)
        changed = copy.deepcopy(bom)
        changed['metadata']['component']['hashes'][0]['content'] = '0' * 64
        mutations.append(changed)
        changed = copy.deepcopy(bom)
        changed['components'][0]['properties'][0]['value'] = '0' * 40
        mutations.append(changed)
        for changed in mutations:
            with self.assertRaises(ValueError):
                native.validate_sbom(changed, self.inputs[2])

    def test_unadmitted_aar_bytes(self):
        aar, pom, provenance, assets = self.inputs
        with self.assertRaises(ValueError):
            native.validate_bundle(aar + b'changed', pom, provenance, assets)

    def test_changed_pom_cannot_add_dependency(self):
        aar, pom, provenance, assets = self.inputs
        with self.assertRaises(ValueError):
            native.validate_bundle(aar, pom + b'changed', provenance, assets)

    def test_missing_or_additional_native_abi_rejected(self):
        for change in ('remove', 'add'):
            aar, pom, provenance, assets = copy.deepcopy(self.inputs)
            if change == 'remove':
                provenance['native'].pop('armeabi-v7a')
            else:
                provenance['native']['riscv64'] = provenance['native']['x86_64']
            with self.assertRaises(ValueError):
                native.validate_bundle(aar, pom, provenance, assets)

    def test_wrong_core_source_or_classes_identity_rejected(self):
        for field in ('explicitCoreSource', 'classesJarSha256'):
            aar, pom, provenance, assets = copy.deepcopy(self.inputs)
            provenance[field] = '0' * len(provenance[field])
            with self.assertRaises(ValueError):
                native.validate_bundle(aar, pom, provenance, assets)

    def test_missing_or_modified_license_cannot_ship(self):
        for change in ('remove', 'change'):
            aar, pom, provenance, assets = copy.deepcopy(self.inputs)
            if change == 'remove':
                assets.pop('SQLite.txt')
            else:
                assets['SQLCipher.txt'] = b'not the upstream license'
            with self.assertRaises(ValueError):
                native.validate_bundle(aar, pom, provenance, assets)


if __name__ == '__main__':
    unittest.main()
