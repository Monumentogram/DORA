"""Publication notices and embedded constituents cannot disappear from the product inventory."""
import copy
import hashlib
import unittest

import persistence_release_inventory as inventory


class PersistenceReleaseInventoryTests(unittest.TestCase):
    def fixture(self):
        asset = inventory.ASSETS + '/Apache-2.0.txt'
        record = {'components': [{'coordinate': inventory.TINK, 'license_expression': 'Apache-2.0'}],
                  'native_composite': {'coordinate': inventory.NATIVE},
                  'embedded_components': [{'coordinate': inventory.PROTOBUF, 'parent': inventory.TINK,
                                           'license_expression': 'BSD-3-Clause'}],
                  'distributed_assets': {asset: hashlib.sha256(b'license').hexdigest()},
                  'jsr305': 'EXCLUDED'}
        return record, {asset: b'license'}

    def test_exact_added_components_and_distributed_texts_are_required(self):
        record, assets = self.fixture()
        added = {inventory.TINK, inventory.NATIVE}
        inventory.validate(record, added, assets)
        for mutate in (lambda r: r['components'].clear(),
                       lambda r: r['components'].append(r['components'][0]),
                       lambda r: r['embedded_components'].clear(),
                       lambda r: r['distributed_assets'].update({next(iter(assets)): '0' * 64}),
                       lambda r: r.update(jsr305='ADMITTED')):
            bad = copy.deepcopy(record)
            mutate(bad)
            with self.assertRaises(ValueError):
                inventory.validate(bad, added, assets)
        with self.assertRaises(ValueError):
            inventory.validate(record, added, {})

    def test_sbom_retains_graph_and_adds_shaded_and_native_constituent_references(self):
        record, _ = self.fixture()
        bom = {'components': [{'bom-ref': 'maven:' + inventory.TINK}, {'bom-ref': 'maven:' + inventory.NATIVE}],
               'dependencies': [{'ref': 'maven:' + inventory.TINK, 'dependsOn': []},
                                {'ref': 'maven:' + inventory.NATIVE, 'dependsOn': []}]}
        original = copy.deepcopy(bom)
        enriched = inventory.enrich_sbom(bom, record, 'a' * 64)
        self.assertEqual(original, bom)
        self.assertEqual('BSD-3-Clause', enriched['components'][-1]['licenses'][0]['expression'])
        self.assertIn(inventory.PROTOBUF, enriched['dependencies'][0]['dependsOn'])
        self.assertEqual('urn:sha256:' + 'a' * 64, enriched['components'][1]['externalReferences'][0]['url'])


if __name__ == '__main__':
    unittest.main()
