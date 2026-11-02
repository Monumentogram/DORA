"""Explicit v9 source accounting cannot waive historical or current qualification gates."""
import copy
import importlib
import json
from pathlib import Path
import unittest

import validate_encrypted_persistence as persistence
from validate_recording_latency import POLICY_PATH

ROOT = Path(__file__).resolve().parents[1]


class OwnerV9InventoryTests(unittest.TestCase):
    def setUp(self):
        self.v9 = importlib.import_module('owner_v9_inventory')

    def discovered(self):
        directory = ROOT / 'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/persistence'
        return persistence.discover_device_tests({p.relative_to(directory).as_posix():
            p.read_text(encoding='utf-8-sig') for p in directory.rglob('*.kt')})

    def predecessor(self):
        return json.loads((ROOT / self.v9.PREDECESSOR).read_text())

    def test_all_current_methods_have_disjoint_explicit_disposition_without_inherited_removal(self):
        contract = self.v9.read_inventory(ROOT)
        selected = self.v9.validate_accounting(contract, self.discovered(), self.predecessor())
        self.assertEqual(188, len(selected))
        self.assertEqual([], contract['deferred_tests'])
        self.assertEqual(4, len(contract['separate_process_tests']))
        self.assertEqual(2, len(contract['qualification_only_tests']))
        for field in ('tests', 'historical_not_qualified', 'separate_process_tests', 'qualification_only_tests'):
            broken = copy.deepcopy(contract)
            broken[field].pop()
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.v9.validate_accounting(broken, self.discovered(), self.predecessor())
        for field in ('tests', 'deferred_tests', 'historical_not_qualified'):
            broken = copy.deepcopy(contract)
            broken[field].append(contract['qualification_only_tests'][0])
            with self.subTest(extra=field), self.assertRaises(ValueError):
                self.v9.validate_accounting(broken, self.discovered(), self.predecessor())
        with self.assertRaises(ValueError):
            self.v9.validate_accounting(contract, self.discovered() | {'invented#test'}, self.predecessor())

    def test_archived_fixture_opt_in_cannot_become_ordinary_or_lose_its_explicit_flag(self):
        contract = self.v9.read_inventory(ROOT)
        method = 'com.monumentogram.dora.audio.persistence.ArchivedStorageRecoveryTest#preservedFullDiskFixtureCheckpointsAndAuthenticatesItsExactCommittedPrefix'
        self.assertIn(method, contract['qualification_only_tests'])
        self.assertNotIn(method, contract['tests'])
        broken = copy.deepcopy(contract)
        broken['qualification_only_tests'].remove(method)
        broken['tests'].append(method)
        with self.assertRaises(ValueError):
            self.v9.validate_accounting(broken, self.discovered(), self.predecessor())
        for arguments in (None, {}, {'doraRecoverFailed24h': 'false'}):
            broken = copy.deepcopy(contract)
            broken['archived_recovery_arguments'] = arguments
            with self.subTest(arguments=arguments), self.assertRaises(ValueError):
                self.v9.validate_accounting(broken, self.discovered(), self.predecessor())

    def test_reviewed_sources_are_exact_and_line_endings_do_not_change_admission(self):
        sources = self.v9.read_sources(ROOT)
        self.v9.validate_sources(ROOT, sources)
        self.v9.validate_sources(ROOT, {path: value.replace('\n', '\r\n') for path, value in sources.items()})
        for path in sources:
            broken = dict(sources)
            broken[path] += '\n// unreviewed change\n'
            with self.subTest(path=path), self.assertRaises(ValueError):
                self.v9.validate_sources(ROOT, broken)
        broken = dict(sources)
        broken[POLICY_PATH] = broken[POLICY_PATH].replace('PRAGMA synchronous=FULL', 'PRAGMA synchronous=OFF')
        with self.assertRaises(ValueError):
            self.v9.validate_sources(ROOT, broken)
        with self.assertRaises(ValueError):
            self.v9.validate_sources(ROOT, {path: text for path, text in sources.items() if path != POLICY_PATH})
        schema = self.v9.schema_path(6)
        broken = dict(sources)
        broken[schema] = broken[schema].replace('"version": 6', '"version": 4, "version": 6')
        self.assertNotEqual(sources[schema], broken[schema])
        with self.assertRaises(ValueError):
            self.v9.validate_sources(ROOT, broken)

    def test_projection_preserves_legacy_checks_and_rejects_current_policy_drift(self):
        from owner_arm82_inventory import project_stage9_policy
        current = (ROOT / POLICY_PATH).read_text(encoding='utf-8')
        historical = persistence.git(ROOT, 'show', '23ae744867e7ab41f5f75bc5afb4f15e0a365b28:' + POLICY_PATH).decode()
        project_stage9_policy({POLICY_PATH: historical}, ROOT)
        with self.assertRaises(ValueError):
            project_stage9_policy({POLICY_PATH: current}, ROOT)
        files = persistence.read_product_sources(ROOT)
        projected = self.v9.project_policy_sources(files, ROOT)
        self.assertEqual(set(files), set(projected))
        self.assertEqual({key: value for key, value in files.items() if key != POLICY_PATH},
                         {key: value for key, value in projected.items() if key != POLICY_PATH})
        persistence.validate_product_sources(projected)
        with self.assertRaises(ValueError):
            self.v9.project_policy_sources({**files, POLICY_PATH: current + '// unreviewed'}, ROOT)

    def test_historical_inventory_is_unchanged_and_still_rejects_unknown_new_methods(self):
        from owner_arm82_inventory import validate_accounting
        old = self.predecessor()
        original = persistence.git(ROOT, 'show', '23ae744867e7ab41f5f75bc5afb4f15e0a365b28:' + self.v9.PREDECESSOR)
        self.assertEqual(original.replace(b'\r\n', b'\n'),
                         (ROOT / self.v9.PREDECESSOR).read_bytes().replace(b'\r\n', b'\n'))
        legacy = json.loads((ROOT / 'docs/contracts/pin-free-local-access-device-tests-v1.json').read_text())
        old_methods = set().union(*(set(old[field]) for field in (
            'tests', 'historical_not_qualified', 'separate_process_tests', 'deferred_tests')))
        validate_accounting(old, old_methods, set(legacy['tests']), set(legacy['deferred_tests']))
        with self.assertRaises(ValueError):
            validate_accounting(old, self.discovered(), set(legacy['tests']), set(legacy['deferred_tests']))

    def test_schema_chain_preserves_old_entities_and_only_adds_the_reviewed_v6_index(self):
        schemas = {version: json.loads((ROOT / self.v9.schema_path(version)).read_text()) for version in (4, 5, 6)}
        self.v9.validate_schema_chain(schemas)
        mutations = (
            (5, lambda db: db['entities'].pop(0)),
            (5, lambda db: db['entities'][0]['fields'].pop()),
            (5, lambda db: db.update(version=6)),
            (6, lambda db: next(row for row in db['entities'] if row['tableName'] == 'deletion_target')['indices'].pop()),
            (6, lambda db: next(row for row in db['entities'] if row['tableName'] == 'deletion_target')['indices'][-1].update(unique=True)),
            (6, lambda db: db['entities'][0].update(createSql='DROP TABLE vault_binding')),
        )
        for version, mutate in mutations:
            broken = copy.deepcopy(schemas)
            mutate(broken[version]['database'])
            with self.subTest(version=version), self.assertRaises(ValueError):
                self.v9.validate_schema_chain(broken)


if __name__ == '__main__':
    unittest.main()
