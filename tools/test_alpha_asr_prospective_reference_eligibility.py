"""Generated host fixtures only; never reads private references or runs ASR."""
import copy
import hashlib
import pathlib
import unittest
from unittest.mock import patch

import alpha_asr_eval_text_contract as contract
import alpha_asr_prospective_reference_eligibility as prospective
from asr_small_arm82_v01 import selector as historical


def fixtures():
    rows, references = [], {}
    for locale in ('ru', 'en'):
        for index in range(30):
            raw = ('Пример речи' if locale == 'ru' else 'Generated speech').encode()
            row = dict(locale=locale, datasetId='generated-' + locale, release='generated',
                       upstreamRelativePath=f'audios/{locale}-{index:03}.mp3',
                       participantSha256=hashlib.sha256(f'{locale}-{index}'.encode()).hexdigest(),
                       audioSha256=hashlib.sha256(f'audio-{locale}-{index}'.encode()).hexdigest(),
                       sourceSplit='dev', durationMs=1000, decodeResult='VALIDATED',
                       referenceTextPresent=True, referenceTextSha256=hashlib.sha256(raw).hexdigest())
            rows.append(row)
            references[historical.source_key(row)] = raw
    return rows, references


def replace_reference(row, references, raw):
    references[historical.source_key(row)] = raw
    row['referenceTextSha256'] = hashlib.sha256(raw).hexdigest()


class ProspectiveReferenceTests(unittest.TestCase):
    def test_raw_nonempty_normalized_empty_is_ineligible(self):
        for raw in (b'[generated annotation]', b'(generated annotation)', b'... !'):
            rows, refs = fixtures()
            replace_reference(rows[0], refs, raw)
            eligible, audit = prospective.admit_references(rows, refs)
            self.assertTrue(raw.strip())
            self.assertNotIn(rows[0], eligible)
            self.assertEqual(audit['normalizedEmptyCounts'], {'ru': 1, 'en': 0})

    def test_valid_unicode_reference_uses_exact_frozen_contract(self):
        rows, refs = fixtures()
        for raw in ('[noise] Привет, мир!', 'ＡＢＣ １２ (aside) test', 'Ёж е\u0308л', 'a[drop>b'):
            replace_reference(rows[0], refs, raw.encode())
            eligible, audit = prospective.admit_references(rows[:1], refs)
            self.assertEqual(eligible, rows[:1])
            self.assertEqual(audit['tokenCounts'][0]['normalizedReferenceTokenCount'],
                             len(contract.normalized_tokens(raw)))

    def test_calls_production_normalized_tokens_before_any_ranking(self):
        rows, refs = fixtures()
        with patch.object(contract, 'normalized_tokens', wraps=contract.normalized_tokens) as normalize:
            original_rank = historical.rank
            def checked_rank(row):
                self.assertEqual(normalize.call_count, len(rows))
                return original_rank(row)
            with patch.object(historical, 'rank', side_effect=checked_rank):
                selected, _ = prospective.select(rows, refs, set(), set(), set())
        self.assertEqual(len(selected), 48)

    def test_selection_deterministic_and_inputs_unchanged(self):
        rows, refs = fixtures()
        replace_reference(rows[0], refs, b'[generated annotation]')
        baseline = copy.deepcopy((rows, refs))
        a, _ = prospective.select(rows, refs, set(), set(), set())
        b, _ = prospective.select(list(reversed(rows)), refs, set(), set(), set())
        self.assertEqual(a, b)
        self.assertEqual((rows, refs), baseline)
        self.assertEqual([r['locale'] for r in a], ['ru'] * 24 + ['en'] * 24)

    def test_zero_reference_cannot_fill_language_quota(self):
        rows, refs = fixtures()
        rows = rows[:24] + rows[30:54]
        replace_reference(rows[0], refs, b'[generated annotation]')
        with self.assertRaisesRegex(ValueError, 'INSUFFICIENT_FRESH_HOLDOUT'):
            prospective.select(rows, refs, set(), set(), set())

    def test_prior_participants_sources_audio_and_duplicate_groups_still_excluded(self):
        rows, refs = fixtures()
        rows[4]['audioSha256'] = rows[3]['audioSha256']
        selected, audit = prospective.select(rows, refs, {historical.source_key(rows[0])},
                                             {rows[1]['audioSha256']}, {rows[2]['participantSha256']})
        self.assertTrue(all(r not in selected for r in rows[:5]))
        self.assertEqual(audit['duplicateRows'], 2)
        self.assertEqual(audit['eligibleCounts'], {'ru': 25, 'en': 30})

    def test_reference_binding_fail_closed(self):
        for mode in ('missing', 'hash', 'utf8', 'duplicate_source'):
            rows, refs = fixtures()
            if mode == 'missing': del refs[historical.source_key(rows[0])]
            if mode == 'hash': rows[0]['referenceTextSha256'] = '0' * 64
            if mode == 'utf8': replace_reference(rows[0], refs, b'\xff')
            if mode == 'duplicate_source': rows.append(dict(rows[0]))
            with self.assertRaises(ValueError):
                prospective.admit_references(rows, refs)

    def test_historical_manifest_and_results_are_immutable_generated_inputs(self):
        rows, refs = fixtures()
        # Historical records retain their old eligibility and scores, even when
        # a future reference check would reject their raw reference.
        replace_reference(rows[0], refs, b'[generated annotation]')
        manifest = {'samples': rows, 'historicalEligibility': 'RAW_NONEMPTY'}
        results = {'errors': 73, 'referenceTokens': 349, 'candidate': 'VALID_FAIL'}
        before = (historical.canonical(manifest), historical.canonical(results))
        prospective.select(manifest['samples'], refs, set(), set(), set())
        self.assertEqual(before, (historical.canonical(manifest), historical.canonical(results)))

    def test_accepted_manifest_bindings_and_result_files_stay_byte_identical(self):
        repo = pathlib.Path(__file__).resolve().parents[1]
        files = list((repo / 'docs/evidence/poc-asr-001').glob('*.json'))
        files += list((repo / 'docs/evidence/poc-data-001').glob('*manifest*.json'))
        files += [repo / 'tools/asr_small_arm82_v01/selector.py']
        self.assertGreater(len(files), 5)
        before = {path: path.read_bytes() for path in files}
        rows, refs = fixtures()
        prospective.select(rows, refs, set(), set(), set())
        self.assertEqual(before, {path: path.read_bytes() for path in files})


if __name__ == '__main__':
    unittest.main()
