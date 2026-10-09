"""Synthetic C3 parser/runner negative controls; no emulator or APK is used."""
import importlib.util
import json
import unittest


PREFIX = 'INSTRUMENTATION_STATUS: stream=C3 '
SOURCE = 'a' * 64


def records(variant='baseline', api=36, maximum=2000):
    loads = 5 if variant == 'baseline' else 4
    rows = [dict(event='BEGIN', api=api, maximum=maximum, variant=variant,
                 sourceSha256=SOURCE, pcmBytes=160000, groupBlocks=120)]
    for n in (10, 400, 1000, 2000):
        if n > maximum:
            break
        for kind, before in [('catalog_load', n)] * 3 + [('append', n+i) for i in range(7)]:
            append = kind == 'append'
            rows.append(dict(event='MEASURE', kind=kind, blocksBefore=before,
                elapsedNanos=1000, threadCpuNanos=500, processCpuMs=1,
                fullCatalogLoads=loads if append else 1,
                transactionOrder=['reservation','bootstrap','publication','catalogCommit'] if append else [],
                stageOrder=['reserve','bootstrap','publication','recovery','catalog_commit'] if append else [],
                stages={key: value for k in ('reserve','bootstrap','publication','recovery','catalog_commit') for key,value in ((k,100),(k+'_count',1))} | ({'sql_commit':100,'sql_commit_count': 2} if append else {}) if append else {},
                candidateFsyncCount=4 if append else 0, candidateParentFsyncCount=4 if append else 0))
        rows.append(dict(event='CHECKPOINT', target=n, actualBlocks=n+7))
    rows.append(dict(event='COMPLETE', blocks=maximum+7, authenticatedPcmFrames=(maximum+7)*80000,
                     reopenedAuthenticatedPcmFrames=(maximum+7)*80000, reopenedCatalogVerified=True))
    return rows


def encode(rows, junit=True):
    return '\n'.join(PREFIX+json.dumps(r) for r in rows) + ('\nOK (1 test)\n' if junit else '')


class C3ParserTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.available = importlib.util.find_spec('persistence_optimization_c3_results') is not None
        if cls.available:
            import persistence_optimization_c3_results
            cls.parser = staticmethod(persistence_optimization_c3_results.parse)
            cls.compare = staticmethod(persistence_optimization_c3_results.compare)

    def require_parser(self):
        self.assertTrue(self.available, 'C3 strict result parser is not implemented')

    def parse(self, rows=None, **kwargs):
        self.require_parser()
        return self.parser(encode(records() if rows is None else rows, **kwargs), 36, 'baseline', SOURCE)

    def test_complete_real_shape_requires_all_checkpoints_and_readbacks(self):
        self.assertEqual('COMPLETE', self.parse()['status'])

    def test_missing_junit_and_short_smoke_are_incomplete(self):
        self.assertEqual('INCOMPLETE', self.parse(junit=False)['status'])
        self.assertNotEqual('COMPLETE', self.parse(records(maximum=10))['status'])

    def test_wrong_api_variant_source_and_private_fields_fail_closed(self):
        for field, value in [('api',28), ('variant','optimized'), ('sourceSha256','b'*64), ('privateId','SECRET')]:
            rows = records(); rows[0][field] = value
            result = self.parse(rows)
            self.assertEqual('INVALID', result['status'])
            self.assertNotIn('SECRET', json.dumps(result))

    def test_wrong_order_loads_missing_sample_and_pcm_mismatch_rejected(self):
        for mutation in ('order','loads','sample','pcm'):
            rows = records()
            if mutation == 'order': rows[4]['transactionOrder'].reverse()
            if mutation == 'loads': rows[4]['fullCatalogLoads'] = 1
            if mutation == 'sample': rows.pop(5)
            if mutation == 'pcm': rows[-1]['reopenedAuthenticatedPcmFrames'] -= 1
            self.assertNotEqual('COMPLETE', self.parse(rows)['status'], mutation)

    def test_failure_after_complete_overrides_junit(self):
        self.require_parser()
        result = self.parser(encode(records())+'\nFAILURES!!!\n',36,'baseline',SOURCE)
        self.assertEqual('FAILED', result['status'])

    def test_missing_duration_or_unknown_counter_cannot_pass(self):
        for mutation in ('missing_duration', 'unknown'):
            rows = records()
            if mutation == 'missing_duration': del rows[4]['stages']['publication']
            else: rows[4]['stages']['PRIVATE_CANARY'] = 1
            result = self.parse(rows)
            self.assertEqual('INVALID', result['status'])
            self.assertNotIn('PRIVATE_CANARY', json.dumps(result))

    def test_duplicate_json_key_and_nonfinite_number_fail_closed(self):
        self.require_parser()
        for raw in (PREFIX+'{"event":"BEGIN","event":"BEGIN"}',
                    encode(records()).replace('"elapsedNanos": 1000','"elapsedNanos": NaN',1)):
            self.assertEqual('INVALID',self.parser(raw,36,'baseline',SOURCE)['status'])

    def test_comparison_requires_both_apis_cpu_threshold_and_no_wall_regression(self):
        self.require_parser()
        receipts = []
        for api in (28,36):
            for variant in ('baseline','optimized'):
                rows = records(variant,api)
                for row in rows:
                    if row['event'] == 'MEASURE' and variant == 'optimized': row['threadCpuNanos'] = 350
                receipts.append(self.parser(encode(rows),api,variant,SOURCE))
        self.assertEqual('USEFUL', self.compare(receipts)['status'])
        self.assertEqual('INCOMPLETE', self.compare(receipts[:-1])['status'])
        receipts[-1]['measurements'][0]['elapsedNanos'] = 100000000
        receipts[-1]['measurements'][1]['elapsedNanos'] = 100000000
        self.assertEqual('NOT_MET', self.compare(receipts)['status'])

    def test_no_cpu_improvement_does_not_meet_gate(self):
        self.require_parser()
        receipts = [self.parser(encode(records(variant,api)),api,variant,SOURCE)
                    for api in (28,36) for variant in ('baseline','optimized')]
        result = self.compare(receipts)
        self.assertEqual('NOT_MET',result['status'])
        self.assertTrue(all(not item['cpu_gate'] for item in result['comparisons']
                            if item['kind'] == 'append' and item['target'] >= 1000))


if __name__ == '__main__':
    unittest.main()
