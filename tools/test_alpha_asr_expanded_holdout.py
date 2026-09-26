"""Synthetic authority and selection tests; no corpus, network or model access."""
import copy
import hashlib
import unittest
from unittest.mock import patch

import alpha_asr_expanded_holdout as expanded
from asr_small_arm82_v01 import selector as frozen
from test_alpha_asr_prospective_reference_eligibility import fixtures, replace_reference

def inputs():
    authority = {
        'ru': dict(datasetId='cmu5x45pn00dao107j4o9w2yv',
                   release='cv-corpus-27.0-2026-09-11', providerLocale='ru',
                   sourceSplit='test', isolationMode='participant'),
        'en': dict(datasetId='cmu5nqn1h00vwmi07b4dbk085',
                   release='sps-corpus-5.0-2026-09-11', providerLocale='en',
                   sourceSplit='train', isolationMode='participant'),
    }
    rows, old_refs = fixtures()
    refs = {}
    for row in rows:
        raw = old_refs[frozen.source_key(row)]
        row.update({k: v for k, v in authority[row['locale']].items()
                    if k != 'isolationMode'})
        row['sourceValid'] = True
        refs[frozen.source_key(row)] = raw
    sources = {('historical-sps', 'old', 'en', f'old/{i}') for i in range(144)}
    audio = {hashlib.sha256(f'old-audio-{i}'.encode()).hexdigest() for i in range(144)}
    people = {hashlib.sha256(f'old-person-{i}'.encode()).hexdigest() for i in range(55)}
    return rows, refs, sources, audio, people, authority


def run_audit(args, **kwargs):
    rows, refs, sources, audio, people, authority = args
    return expanded.audit(rows, refs, sources, audio, people,
                          provider_authority=authority, **kwargs)


def replace_history(values, value):
    values.remove(sorted(values)[0])
    values.add(value)


class ExpandedHoldoutTests(unittest.TestCase):
    def test_exact_rank_quotas_determinism_and_no_input_mutation(self):
        args = inputs()
        before = copy.deepcopy(args)
        selected, audit = run_audit(args)
        reversed_args = (list(reversed(args[0])), *args[1:])
        self.assertEqual((selected, audit), run_audit(reversed_args))
        self.assertEqual(args, before)
        self.assertEqual([r['locale'] for r in selected], ['ru'] * 24 + ['en'] * 24)
        expected = []
        for locale in ('ru', 'en'):
            paths = [r['upstreamRelativePath'] for r in args[0] if r['locale'] == locale]
            expected.extend(sorted(paths, key=lambda path: (
                hashlib.sha256(b'dora-alpha-asr-v0.1\0' + locale.encode()
                               + b'\0' + path.encode()).digest(), path.encode()))[:24])
        self.assertEqual([r['upstreamRelativePath'] for r in selected], expected)
        self.assertEqual(audit['eligibleParticipants'], {'ru': 30, 'en': 30})
        self.assertEqual(audit['historicalCounts'], {'sources': 144, 'audio': 144, 'participants': 55})
        self.assertEqual(audit['crossProviderRealWorldParticipantEquality'], 'UNKNOWN')
        # Returned nested metadata cannot modify input authority or rows.
        selected[0]['sourceValid'] = False
        audit['providerAuthority']['ru']['sourceSplit'] = 'changed'
        self.assertEqual(args, before)

    def test_shortage_never_ranks_either_language(self):
        for subset in (slice(0, 23), slice(0, 0)):
            args = list(inputs())
            args[0] = args[0][subset] + args[0][30:]
            with patch.object(frozen, 'rank', side_effect=AssertionError('RANK_FORBIDDEN')):
                selected, audit = run_audit(args)
            self.assertEqual(selected, [])
            self.assertFalse(audit['rankingOccurred'])
            self.assertEqual(audit['verdict'], expanded.BLOCKED)

    def test_ordered_exclusions_and_all_duplicate_members(self):
        args = inputs()
        rows, refs, sources, audio, people, _ = args
        en = rows[30:]
        replace_history(sources, frozen.source_key(en[0]))
        replace_history(audio, en[1]['audioSha256'])
        replace_history(people, en[2]['participantSha256'])
        replace_reference(en[3], refs, b'[annotation]')
        en[4].update(sourceValid=False, durationMs=999)
        en[5]['durationMs'] = 20001
        en[7]['audioSha256'] = en[6]['audioSha256']
        selected, audit = run_audit(args)
        self.assertEqual([s['remaining']['en'] for s in audit['steps']],
                         [30, 29, 28, 27, 26, 25, 24, 22])
        self.assertEqual(selected, [])
        self.assertEqual(audit['steps'][-1]['removed'], {'ru': 0, 'en': 2})

    def test_authority_reference_and_history_drift_fail_closed(self):
        for mode in ('split', 'locale', 'release', 'dataset', 'provider_locale',
                     'config_drift', 'missing_ref', 'hash', 'utf8', 'duplicate', 'history'):
            with self.subTest(mode=mode):
                args = inputs()
                rows, refs, sources, _, _, authority = args
                if mode == 'split': rows[0]['sourceSplit'] = 'train'
                if mode == 'locale': rows[0]['locale'] = 'fr'
                if mode == 'release': rows[0]['release'] = 'new'
                if mode == 'dataset': rows[0]['datasetId'] = 'other'
                if mode == 'provider_locale': rows[0]['providerLocale'] = 'en'
                if mode == 'config_drift': authority['ru']['sourceSplit'] = 'train'
                if mode == 'missing_ref': del refs[frozen.source_key(rows[0])]
                if mode == 'hash': rows[0]['referenceTextSha256'] = '0' * 64
                if mode == 'utf8': replace_reference(rows[0], refs, b'\xff')
                if mode == 'duplicate': rows.append(dict(rows[0]))
                if mode == 'history': sources.pop()
                with patch.object(frozen, 'rank', side_effect=AssertionError('RANK_FORBIDDEN')):
                    with self.assertRaises(ValueError):
                        run_audit(args)

    def test_cross_language_audio_duplicate_and_known_participant_exclusion(self):
        args = inputs()
        rows, _, _, _, people, _ = args
        rows[30]['audioSha256'] = rows[0]['audioSha256']
        replace_history(people, rows[1]['participantSha256'])
        selected, audit = run_audit(args)
        self.assertEqual(audit['eligibleCounts'], {'ru': 28, 'en': 29})
        excluded = {frozen.source_key(r) for r in (rows[0], rows[1], rows[30])}
        self.assertTrue(excluded.isdisjoint(frozen.source_key(r) for r in selected))

    def test_larger_authoritative_history_wins_and_excludes_additions(self):
        args = inputs()
        _, baseline = run_audit(args)
        rows, _, sources, audio, people, _ = args
        sources.add(frozen.source_key(rows[0]))
        audio.add(rows[1]['audioSha256'])
        people.add(rows[2]['participantSha256'])
        selected, audit = run_audit(args)
        self.assertEqual(audit['historicalCounts'], {'sources': 145, 'audio': 145, 'participants': 56})
        self.assertEqual(audit['eligibleCounts'], {'ru': 27, 'en': 30})
        excluded = {frozen.source_key(r) for r in rows[:3]}
        self.assertTrue(excluded.isdisjoint(frozen.source_key(r) for r in selected))
        for key, values in (('priorSourcesSha256', sources), ('priorAudioSha256', audio),
                            ('priorParticipantsSha256', people)):
            self.assertNotEqual(audit[key], baseline[key])
            self.assertEqual(audit[key], frozen.digest(sorted(values)))

    def test_raw_decode_duration_and_missing_participant_fail_eligibility(self):
        args = inputs()
        changes = [dict(sourceValid=False), dict(referenceTextPresent=False),
                   dict(decodeResult='FAILED'), dict(participantSha256=''),
                   dict(durationMs=999), dict(durationMs=20001), dict(durationMs=True)]
        for row, change in zip(args[0], changes):
            row.update(change)
        args[0][7]['durationMs'] = 1000
        args[0][8]['durationMs'] = 20000
        selected, audit = run_audit(args)
        self.assertEqual(selected, [])
        self.assertEqual(audit['eligibleCounts'], {'ru': 23, 'en': 30})
        self.assertEqual(audit['steps'][5]['removed']['ru'], 4)
        self.assertEqual(audit['steps'][6]['removed']['ru'], 3)

    def test_unadmitted_cohort_mode_and_row_fail_closed(self):
        args = inputs()
        args[0][0]['isolationCohort'] = 'google/fleurs:all-locales-all-splits-all-versions'
        with self.assertRaises(ValueError):
            run_audit(args)
        args = inputs()
        args[-1]['ru']['isolationMode'] = 'corpus_cohort'
        with self.assertRaises(ValueError):
            run_audit(args)


if __name__ == '__main__':
    unittest.main()
