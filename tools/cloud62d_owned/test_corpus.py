"""Private acquisition safety tests; fixtures contain synthesized audio only."""
import hashlib
import io
import json
from pathlib import Path
import struct
import tempfile
import unittest
from unittest.mock import patch
import wave

try:
    from . import corpus
except ImportError:
    from tools.cloud62d_owned import corpus


def wav(seed=1, seconds=20, rate=16000):
    out = io.BytesIO()
    with wave.open(out, 'wb') as w:
        w.setparams((1, 2, rate, 0, 'NONE', 'not compressed'))
        w.writeframes(struct.pack('<h', seed) * (rate * seconds))
    return out.getvalue()


def inventory():
    items = []
    for lang in ('ru', 'en'):
        for kind, count in (('READ', 15), ('SPONTANEOUS', 15), ('NOISY', 6)):
            for n in range(1, count + 1):
                items.append({'id': f'{lang}-{kind.lower()}-{n:02}', 'language': lang,
                              'speech_class': kind,
                              'material': ' '.join(['sample'] * 50) + f' {n}',
                              'condition': 'MODERATE_ROOM_NOISE' if kind == 'NOISY' else 'CLEAN'})
    return {'schema_version': '1.0', 'source_release': 'dora-owned-corpus-v1.0.0', 'items': items}


class CorpusTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(corpus, 'private corpus backend is not implemented')
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.base = Path(self.tmp.name)
        self.repo = self.base / 'repo'
        self.repo.mkdir()
        (self.repo / '.git').mkdir()
        self.store = corpus.CorpusStore(self.base / 'private', self.repo)
        self.store.initialize(inventory())

    def fill(self):
        self.store.attest(confirmed=True)
        for n, item in enumerate(inventory()['items'], 1):
            value = wav(n)
            self.store.save_capture(item['id'], value, value)
            self.store.verify_reference(item['id'], item['material'], confirmed=True)

    def test_no_capture_before_explicit_attestation(self):
        with self.assertRaisesRegex(ValueError, 'ATTESTATION_REQUIRED'):
            self.store.save_capture('ru-read-01', wav(), wav())
        with self.assertRaises(ValueError):
            self.store.attest(confirmed=False)
        self.assertEqual(self.store.public_summary()['recorded'], {'ru': 0, 'en': 0})

    def test_refuses_private_root_inside_any_git_tree(self):
        with self.assertRaisesRegex(ValueError, 'PRIVATE_ROOT_INSIDE_GIT'):
            corpus.CorpusStore(self.repo / 'secret', self.repo)
        other = self.base / 'other'
        other.mkdir()
        (other / '.git').write_text('gitdir: somewhere')
        with self.assertRaisesRegex(ValueError, 'PRIVATE_ROOT_INSIDE_GIT'):
            corpus.CorpusStore(other / 'secret', self.repo)

    def test_attestation_and_reference_never_inferred(self):
        self.store.attest(confirmed=True)
        self.store.save_capture('ru-read-01', wav(), wav())
        with self.assertRaisesRegex(ValueError, 'HUMAN_VERIFICATION_REQUIRED'):
            self.store.verify_reference('ru-read-01', 'actual speech', confirmed=False)
        self.store.verify_reference('ru-read-01', 'Actual speech!', confirmed=True)
        row = self.store.state()['items'][0]
        self.assertEqual(row['reference']['words'], ['actual', 'speech'])
        self.assertTrue(row['reference']['HUMAN_VERIFIED_REFERENCE'])
        with self.assertRaisesRegex(ValueError, 'CAPTURE_ALREADY_EXISTS'):
            self.store.save_capture('ru-read-01', wav(2), wav(2))

    def test_wav_truncation_format_and_duration_mismatch_rejected(self):
        self.store.attest(confirmed=True)
        for n, (source, final) in enumerate([(wav(), wav()[:-2]), (wav(), wav(rate=48000)),
                              (wav(seconds=21), wav()), (wav(seconds=1), wav(seconds=1))], 1):
            with self.assertRaises(ValueError):
                self.store.save_capture(f'ru-read-{n:02}', source, final)
        self.assertFalse((self.base / 'private' / 'audio' / 'ru-read-01.wav').exists())
        self.assertEqual(self.store.public_summary()['excluded_count'], 4)
        self.assertTrue((self.base / 'private' / 'rejected' / 'ru-read-01-source.wav').exists())

    def test_selection_requires_complete_inventory_and_duplicate_check(self):
        with self.assertRaises(ValueError):
            self.store.select()
        self.fill()
        self.store.exclude('ru-read-01', 'OTHER_LANGUAGE_OR_MIXED')
        picked = self.store.select()
        self.assertNotIn('ru-read-01', picked['quality']['ru'])
        self.assertEqual(len(picked['quality']['ru']), 24)
        self.assertEqual(len(picked['reserves']['ru']), 5)
        self.assertEqual(len(picked['timing']['en']), 6)
        expected = sorted([i for i in inventory()['items'] if i['language']=='en' and i['speech_class']=='READ'],
            key=lambda i: hashlib.sha256(('dora-owned-corpus-v1.0.0\n'+hashlib.sha256(wav(inventory()['items'].index(i)+1)).hexdigest()).encode()).hexdigest())
        self.assertEqual(picked['timing']['en'], [i['id'] for i in expected[:6]])
        with self.assertRaisesRegex(ValueError, 'SELECTION_FROZEN'):
            self.store.verify_reference(picked['quality']['ru'][0], 'different reference', confirmed=True)

    def test_timing_is_complete_human_blind_monotonic_and_bound_to_reference(self):
        self.fill()
        selected = self.store.select()
        case = selected['timing']['ru'][0]
        row = next(x for x in self.store.state()['items'] if x['id'] == case)
        words = [{'text': word, 'start_us': i * 1000, 'end_us': i * 1000 + 500}
                 for i, word in enumerate(row['reference']['words'])]
        with self.assertRaises(ValueError):
            self.store.save_timings(case, words, confirmed=False)
        with self.assertRaises(ValueError):
            self.store.save_timings(case, words[:-1], confirmed=True)
        bad = [dict(w) for w in words]
        bad[1]['start_us'] = -1
        with self.assertRaises(ValueError):
            self.store.save_timings(case, bad, confirmed=True)
        self.store.save_timings(case, words, confirmed=True)
        with self.assertRaisesRegex(ValueError, 'TIMING_COVERAGE_INCOMPLETE'):
            self.store.finalize()

    def test_inventory_and_audio_tampering_prevents_finalization(self):
        self.fill()
        audio = self.store.get_audio('ru-read-01')
        audio.write_bytes(wav(123))
        with self.assertRaisesRegex(ValueError, 'AUDIO_HASH_MISMATCH'):
            self.store.select()

    def test_duplicate_upload_excluded_only_by_explicit_preflight_record(self):
        self.fill()
        state_path = self.base / 'private' / 'state.json'
        state = json.loads(state_path.read_text())
        record = state['records']['ru-read-02']['recording']
        record['source_sha256'] = record['uploaded_wav_sha256'] = hashlib.sha256(wav(1)).hexdigest()
        for folder in ('audio', 'source'):
            (self.base / 'private' / folder / 'ru-read-02.wav').write_bytes(wav(1))
        state_path.write_text(json.dumps(state))
        with self.assertRaisesRegex(ValueError, 'DUPLICATE_SOURCE'):
            self.store.select()
        self.store.exclude('ru-read-02', 'DUPLICATE_SOURCE')
        self.assertNotIn('ru-read-02', self.store.select()['quality']['ru'])

    def test_selection_file_is_authoritative_on_resume(self):
        self.fill()
        self.store.select()
        state_path = self.base / 'private' / 'state.json'
        state = json.loads(state_path.read_text())
        state['selection']['quality']['ru'].reverse()
        state_path.write_text(json.dumps(state))
        with self.assertRaisesRegex(ValueError, 'SELECTION_HASH_MISMATCH'):
            self.store.finalize()

    def test_timing_draft_survives_reload_but_never_counts_as_gold(self):
        self.fill()
        selected = self.store.select()['timing']['ru'][0]
        row = next(r for r in self.store.state()['items'] if r['id'] == selected)
        draft = [{'text': text, 'start_us': None, 'end_us': None} for text in row['reference']['words']]
        draft[0]['start_us'] = 100
        self.store.save_timing_draft(selected, draft)
        fresh = corpus.CorpusStore(self.base / 'private', self.repo)
        row = next(r for r in fresh.state()['items'] if r['id'] == selected)
        self.assertEqual(row['timing_draft']['words'][0]['start_us'], 100)
        self.assertEqual(fresh.public_summary()['timing_clips']['ru'], 0)
        with self.assertRaisesRegex(ValueError, 'TIMING_COVERAGE_INCOMPLETE'):
            fresh.finalize()

    def test_changed_timing_draft_revokes_active_gold_until_reconfirmed(self):
        self.fill()
        selected = self.store.select()['timing']['ru'][0]
        row = next(r for r in self.store.state()['items'] if r['id'] == selected)
        words = [{'text': text, 'start_us': i * 1000, 'end_us': i * 1000 + 500}
                 for i, text in enumerate(row['reference']['words'])]
        self.store.save_timings(selected, words, confirmed=True)
        self.store.save_timing_draft(selected, words)
        self.assertEqual(self.store.public_summary()['timing_clips']['ru'], 1)
        revised = [dict(word) for word in words]
        revised[0]['start_us'] = 100
        self.store.save_timing_draft(selected, revised)
        fresh = corpus.CorpusStore(self.base / 'private', self.repo)
        row = next(r for r in fresh.state()['items'] if r['id'] == selected)
        self.assertIsNone(row['timing'])
        self.assertEqual(row['timing_draft']['words'][0]['start_us'], 100)
        self.assertEqual(fresh.public_summary()['timing_clips']['ru'], 0)
        with self.assertRaisesRegex(ValueError, 'TIMING_COVERAGE_INCOMPLETE'):
            fresh.finalize()
        state = json.loads((self.base / 'private' / 'state.json').read_text())
        self.assertEqual(state['records'][selected]['timing_revisions'][0]['words'], words)
        fresh.save_timings(selected, revised, confirmed=True)
        row = next(r for r in fresh.state()['items'] if r['id'] == selected)
        self.assertIsNone(row['timing_draft'])
        self.assertEqual(row['timing']['words'][0]['start_us'], 100)

    def test_whole_private_manifest_only_public_summary_and_exact_composites(self):
        self.fill()
        selection = self.store.select()
        for ids in selection['timing'].values():
            for case in ids:
                row = next(x for x in self.store.state()['items'] if x['id'] == case)
                words = [{'text': word, 'start_us': i * 1000, 'end_us': i * 1000 + 500}
                         for i, word in enumerate(row['reference']['words'])]
                self.store.save_timings(case, words, confirmed=True)
        real_save = self.store._save

        def fail_after_manifest(state, event, case_id=None):
            if event == 'MANIFEST_FINALIZED':
                raise OSError('simulated interrupted state commit')
            return real_save(state, event, case_id)

        with patch.object(self.store, '_save', side_effect=fail_after_manifest):
            with self.assertRaisesRegex(OSError, 'simulated interrupted state commit'):
                self.store.finalize()
        manifest_path = self.base / 'private' / 'manifest.json'
        interrupted_manifest_hash = hashlib.sha256(manifest_path.read_bytes()).hexdigest()
        self.store = corpus.CorpusStore(self.base / 'private', self.repo)
        summary = self.store.finalize()
        self.assertEqual(summary['manifest_sha256'], interrupted_manifest_hash)
        self.assertEqual(summary['status'], 'FINALIZED')
        self.assertEqual(summary['selected_quality'], {'ru': 24, 'en': 24})
        self.assertEqual(summary['timing_clips'], {'ru': 6, 'en': 6})
        self.assertNotIn('sample', json.dumps(summary))
        self.assertNotIn(str(self.base), json.dumps(summary))
        manifest = json.loads((self.base / 'private' / 'manifest.json').read_text())
        self.assertEqual([c['duration_us'] for c in manifest['composites']],
                         [300000000, 599999000, 300000000, 599999000])
        self.assertTrue(all(not c['independent_wer_sample'] for c in manifest['composites']))
        self.assertEqual(self.store.finalize(), summary)
        state_path = self.base / 'private' / 'state.json'
        original = state_path.read_bytes()
        state = json.loads(original)
        state['records']['ru-read-01']['timing'] = {'words': []}
        state_path.write_text(json.dumps(state))
        with self.assertRaisesRegex(ValueError, 'MANIFEST_STATE_MISMATCH'):
            self.store.validate_manifest()
        state_path.write_bytes(original)
        self.store.get_audio('ru-read-01').write_bytes(wav(100))
        with self.assertRaisesRegex(ValueError, 'AUDIO_HASH_MISMATCH'):
            self.store.finalize()


if __name__ == '__main__':
    unittest.main()
