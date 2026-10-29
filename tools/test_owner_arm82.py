"""Successor packaging rejects incomplete, substituted and leaking payloads."""
import copy
import importlib
from pathlib import Path
import tempfile
import unittest


class OwnerArm82Tests(unittest.TestCase):
    def setUp(self):
        self.audit = importlib.import_module('owner_arm82')

    def test_closed_native_inventory_requires_every_pin_and_rejects_extra_abi(self):
        expected = self.audit.native_pins()
        self.assertEqual(10, len(expected))
        self.audit.check_payload(expected)
        for broken in (
            {k: v for k, v in expected.items() if not k.endswith('libwhisper.so')},
            {**expected, 'lib/arm64-v8a/libwhisper.so': '0' * 64},
            {**expected, 'lib/arm64-v8a/libextra.so': '0' * 64},
            {**expected, 'lib/x86_64/libwhisper.so': '0' * 64},
            {**expected, 'assets/renamed.dat': self.audit.MODEL_SHA},
            {**expected, 'assets/ggml-small-q8_0.bin': '0' * 64},
        ):
            with self.subTest(broken=broken), self.assertRaises(ValueError):
                self.audit.check_payload(broken)

    def test_native_dependency_closure_rejects_unbundled_and_eager_arm82(self):
        self.audit.check_needed('libwhisper.so', {'libggml.so', 'libggml-cpu.so', 'libggml-base.so', 'libc++_shared.so', 'libc.so'})
        for name, deps in (
            ('libwhisper.so', {'libmissing.so'}),
            ('libdora-whisper-jni.so', {'libwhisper.so', 'libc.so'}),
        ):
            with self.assertRaises(ValueError):
                self.audit.check_needed(name, deps)

    def test_private_model_is_external_exact_on_future_dates(self):
        from datetime import datetime, timezone
        from unittest.mock import patch
        import hashlib
        # Patch the clock source used by the old release gate without changing OS time.
        for date in ('2026-11-02', '2026-11-03', '2026-11-04', '2026-11-05',
                     '2027-01-01', '2030-01-01', '2100-01-01'):
            with self.subTest(date=date), tempfile.TemporaryDirectory() as temporary:
                model = Path(temporary) / 'model.bin'
                model.write_bytes(b'synthetic model')
                with patch.object(self.audit, 'datetime', create=True) as clock, \
                     patch.object(self.audit, 'MODEL_BYTES', model.stat().st_size), \
                     patch.object(self.audit, 'MODEL_SHA', hashlib.sha256(model.read_bytes()).hexdigest()):
                    clock.now.return_value = datetime.fromisoformat(date).replace(tzinfo=timezone.utc)
                    self.audit.check_model(model)
        with tempfile.TemporaryDirectory() as temporary:
            model = Path(temporary) / 'model.bin'
            model.write_bytes(b'wrong model')
            with self.assertRaises(ValueError):
                self.audit.check_model(model)

    def test_sbom_records_asr_not_bundled_model_and_matches_current_sources(self):
        bom = {'components': [], 'dependencies': [{'ref': 'project::app', 'dependsOn': []}],
               'metadata': {'properties': []}}
        result = self.audit.enrich_sbom(copy.deepcopy(bom), 'a' * 40)
        components = result['components']
        self.assertEqual(6, len(components))
        adapter = next(c for c in components if c['name'] == 'libdora-whisper-jni.so')
        self.assertEqual('a' * 40, adapter['version'])
        self.assertNotIn('licenses', adapter)
        self.assertIn({'name': 'dora:adapter-source-sha256', 'value':
            self.audit.receipt()['sourcePins']['whisper_jni.cpp']['sha256']}, adapter['properties'])
        self.assertEqual(6, len(result['dependencies'][0]['dependsOn']))
        self.assertFalse(any(c['type'] == 'file' and c['name'].endswith('.bin') for c in components))
        self.assertEqual(self.audit.MODEL_SHA, next(p['value'] for p in result['metadata']['properties']
                         if p['name'] == 'dora:external-asr-model-sha256'))
        self.audit.check_sources()

    def test_successor_inventory_cannot_hide_regressions_or_add_skips(self):
        import json
        from owner_arm82_inventory import validate_accounting
        import validate_encrypted_persistence as persistence
        root = self.audit.ROOT
        directory = root / 'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/persistence'
        discovered = persistence.discover_device_tests({p.relative_to(directory).as_posix():
            p.read_text(encoding='utf-8-sig') for p in directory.rglob('*.kt')})
        contract = json.loads((root / 'docs/contracts/owner-arm82-persistence-tests-v1.json').read_text())
        legacy = json.loads((root / 'docs/contracts/pin-free-local-access-device-tests-v1.json').read_text())
        validate_accounting(contract, discovered, set(legacy['tests']), set(legacy['deferred_tests']))
        self.assertEqual(167, len(contract['tests']))
        for field in ('tests', 'historical_not_qualified', 'separate_process_tests', 'deferred_tests'):
            broken = copy.deepcopy(contract)
            broken[field].pop()
            with self.subTest(field=field), self.assertRaises(ValueError):
                validate_accounting(broken, discovered, set(legacy['tests']), set(legacy['deferred_tests']))

    def test_sql_projection_rejects_any_unreviewed_storage_policy_change(self):
        from owner_arm82_inventory import project_stage9_policy
        from validate_recording_latency import POLICY_PATH
        root = self.audit.ROOT
        source = (root / POLICY_PATH).read_text(encoding='utf-8')
        project_stage9_policy({POLICY_PATH: source}, root)
        for changed in (source.replace('PRAGMA synchronous=FULL', 'PRAGMA synchronous=OFF'),
                        source.replace('TRANSCRIPT_SCHEMA_VERSION)', '999)'), source + '// unreviewed'):
            with self.assertRaises(ValueError):
                project_stage9_policy({POLICY_PATH: changed}, root)

    def test_actual_cyrillic_pin_resource_prompts_are_rejected(self):
        from owner_alpha_pilot_artifact import check_pin_resources
        check_pin_resources('Локальная расшифровка')
        for prompt in ('Разблокировать', 'Разблокируйте DORA', 'Требуется разблокировка', 'Настройте PIN'):
            with self.subTest(prompt=prompt), self.assertRaises(ValueError):
                check_pin_resources('resource string: ' + prompt)


if __name__ == '__main__':
    unittest.main()
