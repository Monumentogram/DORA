"""Local mobile package integration tests; no device, microphone or AWS calls."""
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
import zipfile
from unittest.mock import patch

from tools.cloud62d_owned.corpus import CorpusStore
from tools.cloud62d_owned.test_corpus import inventory, wav

try:
    from tools.cloud62d_owned import mobile_bridge as bridge
except ImportError:
    bridge = None


class MobileBridgeTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(bridge, 'private mobile bridge missing')
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.base = Path(self.temp.name)
        self.repo = self.base / 'repo'
        self.repo.mkdir()
        (self.repo / '.git').mkdir()
        self.store = CorpusStore(self.base / 'private', self.repo)
        self.store.initialize(inventory())
        self.store.attest(confirmed=True)
        self.store.migrate_reduced_eight()
        self.export_revision = 0

    def seed(self, ready=False):
        if ready:
            self.store.resume_recording(confirmed=True)
        result = bridge.create_seed(self.store)
        with zipfile.ZipFile(result['zip_path']) as archive:
            raw = archive.read('seed.json')
        seed = json.loads(raw)
        if ready:
            bridge.activate_seed(self.store, seed['seed_id'])
        return result, seed, hashlib.sha256(raw).hexdigest()

    def package(self, seed, seed_sha, records, *, extra=None, header=None):
        self.export_revision += 1
        body = {'schema_version': '1.0', 'app': 'DORA_OWNED8_ANDROID_V1', 'seed_id': seed['seed_id'],
                'seed_sha256': seed_sha, 'inventory_sha256': seed['inventory_sha256'],
                'protocol_overlay_sha256': seed['protocol_overlay_sha256'],
                'export_revision': self.export_revision, 'records': []}
        files = {}
        for case, audio, text, verified in records:
            filename = f'audio/{case}.wav'
            body['records'].append({'id': case, 'audio_path': filename,
                'audio_sha256': hashlib.sha256(audio).hexdigest(), 'reference_text': text,
                'human_verified_reference': verified})
            files[filename] = audio
        if header:
            body.update(header)
        data = io.BytesIO()
        with zipfile.ZipFile(data, 'w', compression=zipfile.ZIP_STORED) as archive:
            archive.writestr('export.json', json.dumps(body).encode())
            for name, value in {**files, **(extra or {})}.items():
                archive.writestr(name, value)
        path = self.base / 'incoming.zip'
        path.write_bytes(data.getvalue())
        return path

    def test_private_seed_preserves_consent_existing_capture_and_deferred_readiness(self):
        self.store.resume_recording(confirmed=True)
        self.store.save_capture('ru-read-01', wav(1), wav(1))
        self.store.verify_reference('ru-read-01', 'Existing spoken words', confirmed=True)
        # This test uses a ready synthetic store; real initialization stays deferred.
        result, seed, _ = self.seed()
        self.assertEqual(len(seed['items']), 8)
        self.assertTrue(seed['attestation']['confirmed'])
        self.assertFalse(seed['recording_enabled'])
        self.assertEqual(seed['items'][0]['reference']['text'], 'Existing spoken words')
        with zipfile.ZipFile(result['zip_path']) as archive:
            self.assertEqual(archive.read('audio/ru-read-01.wav'), wav(1))

    def test_disabled_seed_cannot_activate_or_import_new_capture_until_explicit_readiness(self):
        result, seed, sha = self.seed()
        self.assertFalse(seed['recording_enabled'])
        with self.assertRaisesRegex(ValueError, 'RECORDING_DEFERRED'):
            bridge.activate_seed(self.store, seed['seed_id'])
        package = self.package(seed, sha, [('ru-read-01', wav(1), None, False)])
        with self.assertRaisesRegex(ValueError, 'RECORDING_DEFERRED'):
            bridge.import_export(self.store, package)
        self.assertEqual(self.store.public_summary()['recorded']['ru'], 0)
        self.assertTrue(list((self.base / 'private/mobile/incoming').glob('*/package.zip')))
        self.store.resume_recording(confirmed=True)
        activation = json.loads(Path(bridge.activate_seed(self.store, seed['seed_id'])['activation_path']).read_bytes())
        self.assertEqual(activation['seed_sha256'], sha)
        self.assertTrue(activation['recording_enabled'])

    def test_all_eight_native_import_finalizes_without_timing_and_reimport_is_idempotent(self):
        result, seed, sha = self.seed(ready=True)
        records = [(item['id'], wav(n), item['material'], True) for n, item in enumerate(seed['items'], 1)]
        package = self.package(seed, sha, records)
        imported = bridge.import_export(self.store, package)
        state_bytes = (self.base / 'private/state.json').read_bytes()
        self.assertEqual(imported['imported_recordings'], 8)
        self.assertEqual(bridge.import_export(self.store, package)['status'], 'ALREADY_IMPORTED')
        self.assertEqual((self.base / 'private/state.json').read_bytes(), state_bytes)
        row = self.store.state()['items'][0]
        self.assertEqual(row['recording']['source_sha256'], row['recording']['uploaded_wav_sha256'])
        self.assertNotEqual(row['recording']['conversion_recipe_sha256'],
                            hashlib.sha256(bridge.encoded(bridge.BROWSER_CONVERSION_RECIPE)).hexdigest())
        self.store.select()
        self.assertEqual(self.store.finalize()['selected_quality'], {'ru': 4, 'en': 4})

    def test_partial_capture_then_reference_progress_imports_without_overwrite(self):
        _, seed, sha = self.seed(ready=True)
        first = self.package(seed, sha, [('ru-read-01', wav(1), None, False)])
        bridge.import_export(self.store, first)
        second = self.package(seed, sha, [('ru-read-01', wav(1), 'Actual words', True),
                                          ('en-read-01', wav(2), None, False)])
        result = bridge.import_export(self.store, second)
        self.assertEqual(result['imported_recordings'], 1)
        self.assertEqual(result['verified_references'], 1)
        row = self.store.state()['items'][0]
        self.assertEqual(row['reference']['text'], 'Actual words')
        with self.assertRaises(ValueError):
            self.store.select()

    def test_stale_host_reference_changes_and_export_reference_conflict_are_rejected(self):
        self.store.resume_recording(confirmed=True)
        self.store.save_capture('ru-read-01', wav(1), wav(1))
        self.store.verify_reference('ru-read-01', 'First reference', confirmed=True)
        _, seed, sha = self.seed()
        self.store.verify_reference('ru-read-01', 'Owner corrected reference', confirmed=True)
        package = self.package(seed, sha, [('ru-read-01', wav(1), 'First reference', True)])
        with self.assertRaisesRegex(ValueError, 'STALE_HOST_STATE'):
            bridge.import_export(self.store, package)
        self.assertEqual(self.store.state()['items'][0]['reference']['text'], 'Owner corrected reference')

    def test_zip_traversal_unknown_ids_hash_tamper_and_extra_files_never_write_corpus(self):
        _, seed, sha = self.seed(ready=True)
        cases = [self.package(seed, sha, [], extra={'../escape.wav': b'bad'}).read_bytes(),
                 self.package(seed, sha, [('ru-noisy-01', wav(1), None, False)]).read_bytes(),
                 self.package(seed, sha, [], header={'seed_sha256': '0' * 64}).read_bytes(),
                 self.package(seed, sha, [], extra={'unknown.txt': b'bad'}).read_bytes()]
        for raw in cases:
            package = self.base / 'bad.zip'
            package.write_bytes(raw)
            with self.assertRaises(ValueError):
                bridge.import_export(self.store, package)
        self.assertEqual(self.store.public_summary()['recorded'], {'ru': 0, 'en': 0})
        self.assertFalse((self.base / 'escape.wav').exists())

    def test_bridge_refuses_running_recorder(self):
        from tools.cloud62d_owned.server import make_server
        server = make_server(self.store, port=0)
        try:
            with self.assertRaisesRegex(RuntimeError, 'RECORDER_ALREADY_RUNNING'):
                bridge.create_seed(self.store)
        finally:
            server.server_close()

    def test_verified_phone_correction_preserves_previous_host_gold_and_private_audit(self):
        _, seed, sha = self.seed(ready=True)
        package = self.package(seed, sha, [('ru-read-01', wav(1), 'Initial words', True)])
        bridge.import_export(self.store, package)
        audit = json.dumps({'events': ['human correction'], 'draft_text': 'not gold'}).encode()
        package = self.package(seed, sha, [('ru-read-01', wav(1), 'Corrected words', True)],
                               extra={'audit.json': audit},
                               header={'audit_path': 'audit.json', 'audit_sha256': hashlib.sha256(audit).hexdigest()})
        result = bridge.import_export(self.store, package)
        self.assertEqual(result['verified_references'], 1)
        state = json.loads((self.base / 'private/state.json').read_bytes())
        record = state['records']['ru-read-01']
        self.assertEqual(record['reference']['text'], 'Corrected words')
        self.assertEqual(record['reference_revisions'][0]['text'], 'Initial words')
        with zipfile.ZipFile(self.base / 'private/mobile/incoming' / result['package_sha256'] / 'package.zip') as z:
            self.assertEqual(z.read('audit.json'), audit)

    def test_phone_draft_revokes_imported_gold_until_explicit_reconfirmation(self):
        _, seed, sha = self.seed(ready=True)
        rows = [(item['id'], wav(n), item['material'], True) for n, item in enumerate(seed['items'], 1)]
        bridge.import_export(self.store, self.package(seed, sha, rows))
        self.assertEqual(self.store.public_summary()['verified_references']['ru'], 4)
        draft = self.package(seed, sha, [('ru-read-01', wav(1), 'Edited but unconfirmed words', False)])
        bridge.import_export(self.store, draft)
        state = json.loads((self.base / 'private/state.json').read_bytes())
        record = state['records']['ru-read-01']
        self.assertIsNone(record['reference'])
        self.assertEqual(record['reference_revisions'][0]['text'], rows[0][2])
        self.assertEqual(record['reference_draft']['text'], 'Edited but unconfirmed words')
        self.assertEqual(self.store.public_summary()['verified_references']['ru'], 3)
        with self.assertRaisesRegex(ValueError, 'MISSING_HUMAN_REFERENCE'):
            self.store.select()
        confirmed = self.package(seed, sha, [('ru-read-01', wav(1), 'Edited and confirmed words', True)])
        bridge.import_export(self.store, confirmed)
        self.assertEqual(self.store.public_summary()['verified_references']['ru'], 4)
        state = json.loads((self.base / 'private/state.json').read_bytes())
        self.assertNotIn('reference_draft', state['records']['ru-read-01'])
        self.assertEqual(state['records']['ru-read-01']['reference_revisions'][0]['text'], rows[0][2])
        self.store.select()
        self.assertEqual(self.store.finalize()['selected_quality'], {'ru': 4, 'en': 4})

    def test_older_never_imported_phone_export_cannot_roll_back_newer_gold(self):
        _, seed, sha = self.seed(ready=True)
        old_bytes = self.package(seed, sha, [('ru-read-01', wav(1), 'Old words', True)]).read_bytes()
        newer = self.package(seed, sha, [('ru-read-01', wav(1), 'Newer words', True)])
        bridge.import_export(self.store, newer)
        old = self.base / 'old-export.zip'
        old.write_bytes(old_bytes)
        with self.assertRaisesRegex(ValueError, 'STALE_MOBILE_EXPORT_REVISION'):
            bridge.import_export(self.store, old)
        self.assertEqual(self.store.state()['items'][0]['reference']['text'], 'Newer words')
        self.assertEqual(bridge.import_export(self.store, newer)['status'], 'ALREADY_IMPORTED')

    def test_transaction_recovers_after_plan_and_after_atomic_state_commit(self):
        _, seed, sha = self.seed(ready=True)
        package = self.package(seed, sha, [('ru-read-01', wav(1), 'New words', True)])
        original_write = self.store._write

        def fail_pending(relative, value, **kwargs):
            if relative == 'mobile-import-pending.json':
                raise OSError('simulated power loss before pending marker')
            return original_write(relative, value, **kwargs)

        with patch.object(self.store, '_write', side_effect=fail_pending):
            with self.assertRaisesRegex(OSError, 'simulated power loss'):
                bridge.import_export(self.store, package)

        def fail_sync(relative, value, **kwargs):
            if relative.endswith('/sync.json'):
                raise OSError('simulated power loss after state commit')
            return original_write(relative, value, **kwargs)

        with patch.object(self.store, '_write', side_effect=fail_sync):
            with self.assertRaisesRegex(OSError, 'simulated power loss'):
                bridge.import_export(self.store, package)
        with self.assertRaisesRegex(ValueError, 'MOBILE_IMPORT_INCOMPLETE_RETRY_SAME_PACKAGE'):
            self.store.state()
        result = bridge.import_export(self.store, package)
        self.assertEqual(result['imported_recordings'], 1)
        self.assertEqual(self.store.public_summary()['recorded']['ru'], 1)
        state = json.loads((self.base / 'private/state.json').read_bytes())
        self.assertEqual(sum(e['event'] == 'MOBILE_PACKAGE_IMPORTED' for e in state['events']), 1)

    def test_duplicate_members_and_compressed_bomb_are_preserved_but_rejected(self):
        _, seed, sha = self.seed(ready=True)
        for kind in ('duplicate', 'bomb'):
            raw = io.BytesIO()
            with zipfile.ZipFile(raw, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
                archive.writestr('export.json', b'{}')
                if kind == 'duplicate':
                    archive.writestr('EXPORT.JSON', b'{}')
                else:
                    archive.writestr('audio/ru-read-01.wav', b'\0' * 1_000_000)
            package = self.base / 'unsafe.zip'
            package.write_bytes(raw.getvalue())
            with self.assertRaisesRegex(ValueError, 'DUPLICATE_ZIP_MEMBER|ZIP_COMPRESSION_RATIO_LIMIT'):
                bridge.import_export(self.store, package)
        self.assertEqual(self.store.public_summary()['recorded']['ru'], 0)

    def test_duplicate_audio_in_later_record_rejects_entire_import_without_partial_capture(self):
        _, seed, sha = self.seed(ready=True)
        package = self.package(seed, sha, [('ru-read-01', wav(1), None, False),
                                          ('ru-read-02', wav(1), None, False)])
        before = (self.base / 'private/state.json').read_bytes()
        with self.assertRaisesRegex(ValueError, 'DUPLICATE_SOURCE'):
            bridge.import_export(self.store, package)
        self.assertEqual((self.base / 'private/state.json').read_bytes(), before)
        self.assertFalse((self.base / 'private/audio/ru-read-01.wav').exists())


if __name__ == '__main__':
    unittest.main()
