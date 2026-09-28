"""Offline guard regressions; no credentials, real speech or network."""
import copy
import unittest
import json
from decimal import Decimal

import cloud62d_prepare as p


class PreparationTests(unittest.TestCase):
    def test_mobile_ui_revision_cannot_change_corpus_or_infer_human_authority(self):
        record = {'application_id': 'com.monumentogram.dora.stage0.ownedcorpus',
                  'protocol_id': 'dora-owned-reduced8-v2', 'active_ids': list(p.EIGHT_IDS),
                  'wer_threshold_percent': {'ru': 20, 'en': 18},
                  'timestamp_accuracy': 'NOT_EVALUATED', 'noise_robustness': 'NOT_EVALUATED',
                  'phase_a_successor': 'NOT_CREATED', 'phase_b': 'NOT_RUN',
                  'budget_usd': {'total': 10, 'asr': 2, 'ancillary_tax': 8},
                  'automatic_microphone_start': False,
                  'activation': 'EXISTING_OWNER_READINESS_AND_MATCHING_SEED_REQUIRED',
                  'reference_confirmation': 'EXPLICIT_HUMAN',
                  'primary_action': 'FIXED_OUTSIDE_SCROLLING_CONTENT',
                  'owner_reset': 'ARCHIVE_OLD_PRESERVE_CONSENT_MATERIALS_AND_EXISTING_READINESS'}
        self.assertTrue(hasattr(p, 'validate_mobile_ui'), 'UI revision guard is missing')
        p.validate_mobile_ui(record)
        for key, value in [('active_ids', list(p.EIGHT_IDS[:-1])),
                           ('automatic_microphone_start', True), ('activation', 'AUTOMATIC_AUTHORITY'),
                           ('reference_confirmation', 'INFERRED_FROM_READINESS'),
                           ('phase_a_successor', 'PASS'), ('phase_b', 'PASS'),
                           ('noise_robustness', 'PASS'), ('owner_reset', 'DELETE_HISTORY')]:
            with self.subTest(key=key), self.assertRaises(ValueError):
                p.validate_mobile_ui({**record, key: value})

    def test_mobile_cannot_unlock_capture_or_promote_unmeasured_properties(self):
        record = json.loads((p.REPO / p.AMENDMENT).read_text(encoding='utf-8'))
        record.update(application_id='com.monumentogram.dora.stage0.ownedcorpus',
                      initial_seed_enabled=False, accepted_capture_replacement='FORBIDDEN',
                      technical_retry='EXPLICIT_BEFORE_ACCEPTANCE_WITH_ALL_ATTEMPTS_RETAINED',
                      real_microphone_test='NOT_RUN', broad_admission='NOT_ESTABLISHED')
        p.validate_mobile(record)
        for key, value in [('initial_seed_enabled', True), ('real_microphone_test', 'PASS'),
                           ('accepted_capture_replacement', 'ALLOWED'),
                           ('technical_retry', 'UNRESTRICTED'), ('broad_admission', 'PASS'),
                           ('application_id', 'production')]:
            with self.subTest(key=key), self.assertRaises(ValueError):
                p.validate_mobile({**record, key: value})

    def test_amendment_cannot_promote_untested_properties_or_expand_corpus(self):
        amendment = {'protocol_id': 'dora-owned-reduced8-v2',
                     'active_ids': list(p.EIGHT_IDS),
                     'wer_threshold_percent': {'ru': 20, 'en': 18},
                     'timestamp_accuracy': 'NOT_EVALUATED', 'noise_robustness': 'NOT_EVALUATED',
                     'phase_a_successor': 'NOT_CREATED', 'phase_b': 'NOT_RUN',
                     'recording': 'DEFERRED_UNTIL_EXPLICIT_OWNER_READY',
                     'budget_usd': {'total': 10, 'asr': 2, 'ancillary_tax': 8}}
        p.validate_amendment(amendment)
        for key, value in [('active_ids', list(p.EIGHT_IDS[:-1])),
                           ('timestamp_accuracy', 'PASS'), ('noise_robustness', 'PASS'),
                           ('wer_threshold_percent', {'ru': 21, 'en': 18}),
                           ('phase_a_successor', 'PASS'), ('phase_b', 'PASS'),
                           ('recording', 'READY'), ('budget_usd', {'total': 11})]:
            invalid = copy.deepcopy(amendment); invalid[key] = value
            with self.assertRaises(ValueError):
                p.validate_amendment(invalid)

    def test_eight_report_micro_denominators_and_separate_languages(self):
        rows = []
        for lang in ('ru', 'en'):
            for kind in ('read', 'spontaneous'):
                for n in (1, 2):
                    counts = {'substitutions': 1 if n == 1 else 0,
                              'deletions': 0, 'insertions': 0,
                              'reference_tokens': 1 if n == 1 else 99}
                    rows.append({'case_id': f'{lang}-{kind}-{n:02}',
                                 'state': 'SUCCEEDED', 'raw': counts,
                                 'normalized': counts})
        report = p.bounded_report(rows)
        self.assertEqual(len(report['records']), 8)
        self.assertEqual(report['languages']['ru']['normalized']['errors'], 2)
        self.assertEqual(report['languages']['ru']['normalized']['reference_tokens'], 200)
        self.assertEqual(report['languages']['ru']['normalized']['wer_percent'], 1)
        self.assertEqual(report['languages']['en']['threshold_percent'], 18)
        self.assertEqual(report['languages']['ru']['bounded_quality'], 'PASS')
        self.assertEqual(report['timestamp_accuracy'], 'NOT_EVALUATED')
        self.assertEqual(report['noise_robustness'], 'NOT_EVALUATED')
        self.assertEqual(report['broad_admission'], 'NOT_ESTABLISHED')
        self.assertNotIn('combined', report)

    def test_eight_report_never_hides_missing_failed_or_invalid_rows(self):
        report = p.bounded_report([])
        self.assertEqual(report['languages']['ru']['bounded_quality'], 'NOT_RUN')
        self.assertIsNone(report['languages']['ru']['normalized']['wer_percent'])
        failed = {'case_id': 'ru-read-01', 'state': 'FAILED', 'raw': None, 'normalized': None}
        report = p.bounded_report([failed])
        self.assertEqual(report['languages']['ru']['failed_records'], 1)
        self.assertEqual(report['languages']['ru']['bounded_quality'], 'INCOMPLETE')
        self.assertEqual(len(report['records']), 8)
        for rows in ([failed, failed], [{**failed, 'case_id': 'ru-read-03'}],
                     [{**failed, 'state': 'SUCCEEDED'}],
                     [{**failed, 'raw': {'reference_tokens': 1}}]):
            with self.assertRaises(ValueError):
                p.bounded_report(rows)

    def test_eight_report_exact_language_and_class_thresholds(self):
        rows = []
        for case_id in p.EIGHT_IDS:
            counts = {'substitutions': 20 if case_id.startswith('ru') else 18,
                      'deletions': 0, 'insertions': 0, 'reference_tokens': 100}
            rows.append({'case_id': case_id, 'state': 'SUCCEEDED',
                         'raw': dict(counts), 'normalized': dict(counts)})
        self.assertTrue(all(v['bounded_quality'] == 'PASS'
                            for v in p.bounded_report(rows)['languages'].values()))
        rows[4]['normalized']['insertions'] = 1
        report = p.bounded_report(rows)
        self.assertEqual(report['languages']['ru']['bounded_quality'], 'PASS')
        self.assertEqual(report['languages']['en']['bounded_quality'], 'FAIL')
        rows[5]['normalized']['substitutions'] = 0
        rows[4]['normalized']['substitutions'] = 37
        rows[4]['normalized']['insertions'] = 0
        rows[6]['normalized']['substitutions'] = rows[7]['normalized']['substitutions'] = 0
        # Overall EN37/400 passes numerically, but READ37/200 fails the required slice.
        result = p.bounded_report(rows)['languages']['en']
        self.assertEqual(result['normalized']['wer_percent'], 9.25)
        self.assertEqual(result['bounded_quality'], 'FAIL')

    def test_ancillary_and_tax_cannot_disappear_from_budget(self):
        self.assertEqual(p.quote([1_000_001, 20_000_000], Decimal('8')),
                         {'billable_seconds': 22, 'asr_usd': '0.0022',
                          'ancillary_tax_upper_usd': '8', 'upper_total_usd': '8.0022'})
        for reserve in (None, Decimal('-1'), Decimal('NaN'), Decimal('8.01')):
            with self.assertRaises(ValueError):
                p.quote([20_000_000], reserve)

    def test_one_second_ceil_without_minimum_and_every_retry_count(self):
        self.assertEqual(p.quote([15_000_001] * 2, Decimal('8'))['billable_seconds'], 32)
        with self.assertRaises(ValueError):
            p.quote([600_000_000] * 34, Decimal('8'))
        with self.assertRaises(ValueError):
            p.quote([15_000_000] * 97, Decimal('8'))
        for duration in (-1, True, 1.5, 600_000_001):
            with self.assertRaises(ValueError):
                p.quote([duration], Decimal('8'))

    def test_dag_rejects_missing_dependency_and_cycle(self):
        p.validate_dag([{'gate_id':'a','dependencies':[]},
                        {'gate_id':'b','dependencies':['a']}])
        for rows in ([{'gate_id':'a','dependencies':['b']}],
                     [{'gate_id':'a','dependencies':['a']}],
                     [{'gate_id':'a','dependencies':[]},{'gate_id':'a','dependencies':[]}]):
            with self.assertRaises(ValueError):
                p.validate_dag(rows)

    def test_public_summary_is_allowlisted_not_copied_from_private_manifest(self):
        private = {'inventory_sha256':'a'*64, 'manifest_sha256':None,
                   'candidate_counts':{'ru':36,'en':36},
                   'recorded_counts':{'ru':0,'en':0},
                   'selected_counts':{'ru':0,'en':0},
                   'timing_counts':{'ru':0,'en':0},
                   'private_path':'DO_NOT_EXPORT','transcript':'DO_NOT_EXPORT'}
        result=p.public_corpus_summary(private)
        self.assertNotIn('DO_NOT_EXPORT', str(result))
        bad=copy.deepcopy(private);bad['recorded_counts']['ru']='PRIVATE_VALUE'
        with self.assertRaises(ValueError):p.public_corpus_summary(bad)

    def test_publication_gate_requires_exact_commit_all_checks_and_zero_results(self):
        state={'phase_a':'PASS','local_head':'b'*40,'fetched_remote_head':'b'*40,
               'published_commit':'b'*40,'tree_clean':True,'aws_result_count':0,
               'preflight':{f'PREFLIGHT-{i:02d}':'PASS' for i in range(1,13)},
               'manifest_sha256':'c'*64,'config_sha256':'d'*64,
               'client_sha256':'e'*64,'published_manifest_sha256':'c'*64,
               'published_config_sha256':'d'*64,'published_client_sha256':'e'*64}
        p.assert_prospective_publication(state)
        for key,value in [('phase_a','PARTIAL'),('tree_clean',False),
                          ('aws_result_count',1),('aws_result_count',False),
                          ('fetched_remote_head','f'*40),('manifest_sha256','f'*64),
                          ('client_sha256',None)]:
            bad=copy.deepcopy(state);bad[key]=value
            with self.assertRaises(ValueError):p.assert_prospective_publication(bad)
        for change in ('missing','failed','extra'):
            bad=copy.deepcopy(state)
            if change=='missing':del bad['preflight']['PREFLIGHT-01']
            elif change=='failed':bad['preflight']['PREFLIGHT-01']='BLOCKED'
            else:bad['preflight']['PREFLIGHT-13']='PASS'
            with self.assertRaises(ValueError):p.assert_prospective_publication(bad)


if __name__=='__main__':unittest.main()
