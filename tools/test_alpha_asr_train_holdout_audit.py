"""Synthetic host-only train audit fixtures; no corpus or ASR access."""
import copy
import unittest
from unittest.mock import patch

import alpha_asr_train_holdout_audit as train
from asr_small_arm82_v01 import selector as frozen
from test_alpha_asr_prospective_reference_eligibility import fixtures, replace_reference


def inputs():
    rows, refs = fixtures()
    for row in rows:
        row.update(sourceSplit='train', sourceValid=True)
    return rows, refs


class TrainAuditTests(unittest.TestCase):
    def test_sequential_first_reason_and_all_duplicate_members(self):
        rows, refs = inputs()
        rows[0]['participantSha256'] = rows[2]['participantSha256']
        rows[1]['participantSha256'] = rows[2]['participantSha256']
        replace_reference(rows[2], refs, b'...')
        replace_reference(rows[3], refs, b'[annotation]')
        rows[4].update(sourceValid=False, durationMs=999)
        rows[5]['durationMs'] = 20001
        rows[7]['audioSha256'] = rows[6]['audioSha256']
        selected, audit = train.audit(rows, refs, {frozen.source_key(rows[0])},
                                     {rows[1]['audioSha256']}, {rows[2]['participantSha256']})
        self.assertEqual([s['remaining']['ru'] for s in audit['steps']],
                         [30, 29, 28, 27, 26, 25, 24, 22])
        self.assertEqual([s['removed']['ru'] for s in audit['steps'][1:]], [1, 1, 1, 1, 1, 1, 2])
        self.assertEqual(selected, [])
        self.assertEqual(audit['verdict'], train.BLOCKED)

    def test_shortage_never_ranks_either_language(self):
        rows, refs = inputs()
        for subset in (rows[:23] + rows[30:], rows[:30] + rows[30:53], rows[30:], []):
            with patch.object(frozen, 'rank', side_effect=AssertionError('RANK_FORBIDDEN')):
                selected, audit = train.audit(subset, refs, set(), set(), set())
            self.assertEqual(selected, [])
            self.assertFalse(audit['rankingOccurred'])

    def test_exact_rank_determinism_boundaries_and_immutability(self):
        rows, refs = inputs()
        rows[0]['durationMs'] = 1000
        rows[1]['durationMs'] = 20000
        baseline = copy.deepcopy((rows, refs))
        first = train.audit(rows, refs, set(), set(), set())
        second = train.audit(list(reversed(rows)), refs, set(), set(), set())
        self.assertEqual(first, second)
        self.assertEqual((rows, refs), baseline)
        expected = [r for loc in ('ru', 'en') for r in
                    sorted([r for r in rows if r['locale'] == loc], key=frozen.rank)[:24]]
        self.assertEqual([frozen.source_key(r) for r in first[0]],
                         [frozen.source_key(r) for r in expected])
        self.assertTrue(all(r['normalizedReferenceTokenCount'] >= 1 for r in first[0]))
        self.assertEqual(first[1]['eligibleParticipants'], {'ru': 30, 'en': 30})

    def test_binding_failure_and_invalid_inventory_fail_closed(self):
        for mode in ('missing', 'hash', 'utf8', 'duplicate', 'split', 'locale'):
            rows, refs = inputs()
            if mode == 'missing': del refs[frozen.source_key(rows[0])]
            if mode == 'hash': rows[0]['referenceTextSha256'] = '0' * 64
            if mode == 'utf8': replace_reference(rows[0], refs, b'\xff')
            if mode == 'duplicate': rows.append(dict(rows[0]))
            if mode == 'split': rows[0]['sourceSplit'] = 'dev'
            if mode == 'locale': rows[0]['locale'] = 'fr'
            with self.assertRaises(ValueError):
                train.audit(rows, refs, set(), set(), set())

    def test_source_raw_decode_duration_and_participant_validity(self):
        changes = [dict(sourceValid=False), dict(referenceTextPresent=False),
                   dict(decodeResult='FAILED'), dict(participantSha256=''),
                   dict(durationMs=999), dict(durationMs=20001), dict(durationMs=True)]
        rows, refs = inputs()
        for row, change in zip(rows, changes): row.update(change)
        selected, audit = train.audit(rows, refs, set(), set(), set())
        self.assertEqual(selected, [])
        self.assertEqual(audit['eligibleCounts'], {'ru': 23, 'en': 30})
        self.assertEqual(audit['steps'][5]['removed']['ru'], 4)
        self.assertEqual(audit['steps'][6]['removed']['ru'], 3)

    def test_cross_language_duplicate_and_historical_isolation(self):
        rows, refs = inputs()
        rows[30]['audioSha256'] = rows[0]['audioSha256']
        rows[31]['participantSha256'] = rows[1]['participantSha256']
        selected, audit = train.audit(rows, refs, {frozen.source_key(rows[2])},
                                     {rows[3]['audioSha256']}, {rows[1]['participantSha256']})
        self.assertEqual(audit['steps'][-1]['removed'], {'ru': 1, 'en': 1})
        self.assertEqual(audit['eligibleCounts'], {'ru': 26, 'en': 28})
        self.assertTrue(all(r['audioSha256'] != rows[0]['audioSha256'] and
                            r['participantSha256'] != rows[1]['participantSha256'] and
                            frozen.source_key(r) != frozen.source_key(rows[2]) and
                            r['audioSha256'] != rows[3]['audioSha256'] for r in selected))


if __name__ == '__main__':
    unittest.main()
