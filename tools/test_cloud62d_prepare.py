"""Offline guard regressions; no credentials, real speech or network."""
import copy
import unittest
from decimal import Decimal

import cloud62d_prepare as p


class PreparationTests(unittest.TestCase):
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
