"""Synthetic fixtures only: retention, coverage, scoring and instrumentation."""
import copy
import importlib.util
import tempfile
from pathlib import Path
import unittest


class CampaignTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(importlib.util.find_spec('alpha_asr_campaign'), 'campaign missing')
        import alpha_asr_campaign
        self.c = alpha_asr_campaign

    def test_thermal_crlf_and_missing_status(self):
        self.assertEqual(0, self.c.thermal_status(b'Header\r\nThermal Status: 0\r\n'))
        self.assertEqual(3, self.c.thermal_status(b'Thermal Status: 3\n'))
        for bad in (b'', b'Thermal Status: 9\n', b'Thermal Status: 0\nThermal Status: 1\n'):
            with self.assertRaises(ValueError): self.c.thermal_status(bad)

    def test_memory_missing_sample_rejected(self):
        self.assertEqual({'peakPssBytes':4096,'peakNativeHeapBytes':300,'samples':2},
            self.c.memory_summary(b'100,2048,300\n200,4096,200\n'))
        for bad in (b'', b'100,0,2\n', b'100,4,0\n', b'200,4,2\n100,4,2\n', b'100,4,2\n1000101,4,2\n'):
            with self.assertRaises(ValueError): self.c.memory_summary(bad)

    def test_attempt_retains_failure_and_refuses_replay(self):
        with tempfile.TemporaryDirectory() as td:
            j=self.c.Journal(Path(td)/'attempts.sqlite')
            j.begin(1,'sample-0000000000000001','ru',1,{'pin':'a'})
            j.finish(1,{'state':'FAILED','error':'TIMEOUT'})
            j.close()
            j=self.c.Journal(Path(td)/'attempts.sqlite')
            self.assertEqual('FAILED',j.records()[0]['result']['state'])
            with self.assertRaises(ValueError): j.begin(1,'sample-0000000000000001','ru',1,{})
            j.begin(2,'sample-0000000000000001','ru',2,{'pin':'a'})
            j.finish(2,{'state':'SUCCEEDED'})
            with self.assertRaises(ValueError): j.finish(2,{'state':'FAILED'})
            with self.assertRaises(ValueError): j.begin(3,'sample-0000000000000001','ru',3,{})
            j.close()

    def test_interrupted_attempt_blocks_reentry(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)/'attempts.sqlite'
            j=self.c.Journal(p); j.begin(1,'sample-0000000000000001','en',1,{}); j.close()
            j=self.c.Journal(p)
            with self.assertRaises(ValueError): j.begin(2,'sample-0000000000000002','en',1,{})
            self.assertEqual('RUNNING',j.records()[0]['state']); j.close()

    def test_aggregation_requires_exact_membership_and_complete_language(self):
        selected=[{'sampleId':f'sample-{n:016x}','locale': 'ru' if n<24 else 'en'} for n in range(48)]
        def result(row):
            return {'caseId':row['sampleId'],'locale':row['locale'],'executionAttemptState':'SUCCEEDED',
                'oracleCounts':{s:{'substitutions':1,'deletions':0,'insertions':0} for s in ('raw','normalized')},
                'rawTokenCounts':{'reference':5,'hypothesis':5},
                'normalizedTokenCounts':{'reference':5,'hypothesis':5}}
        results=[result(r) for r in selected]
        a=self.c.aggregate(selected,results)
        self.assertEqual('PASS',a['ru']['gate']); self.assertEqual('FAIL',a['en']['gate'])
        self.assertEqual(24,a['ru']['normalized']['substitutions'])
        self.assertEqual(120,a['ru']['normalized']['referenceTokens'])
        self.assertEqual('NOT_EVALUABLE',self.c.aggregate(selected,results[:-1])['en']['gate'])
        with self.assertRaises(ValueError): self.c.aggregate(selected,results+[results[0]])
        changed=copy.deepcopy(results); changed[0]['locale']='en'
        with self.assertRaises(ValueError): self.c.aggregate(selected,changed)

    def test_code_freeze_rejects_changed_bytes(self):
        with tempfile.TemporaryDirectory() as td:
            p=Path(td)/'code'; p.write_bytes(b'frozen')
            pins=self.c.file_pins([p]); self.c.verify_pins(pins)
            p.write_bytes(b'changed')
            with self.assertRaises(ValueError): self.c.verify_pins(pins)

    def test_four_streams_reach_java_oracle_unchanged(self):
        import alpha_asr_eval_text_contract as f
        with tempfile.TemporaryDirectory() as td:
            bridge=self.c.Oracle(Path(td)); bridge.compile()
            prepared=f.prepare_case({'profile':f.profile(),'caseId':'sample-0000000000000001',
                'locale':'en','referenceText':'ONE two','hypothesisText':'one extra two'})
            out=bridge.score(prepared)
            self.assertEqual(1,out['raw']['substitutions'])
            self.assertEqual(1,out['raw']['insertions'])
            self.assertEqual(0,out['normalized']['substitutions'])
            self.assertEqual(1,out['normalized']['insertions'])

    def test_reference_binding_uses_exact_utf8_without_repair(self):
        import hashlib
        import alpha_asr_campaign_import as imp
        value={'references':{'sample-0000000000000001':{'text':' ONE two! ', 'locale':'en'}}}
        sha=hashlib.sha256(b' ONE two! ').hexdigest()
        self.assertEqual(' ONE two! ',imp.reference_text(value,'sample-0000000000000001',sha))
        with self.assertRaises(ValueError): imp.reference_text(value,'sample-0000000000000002',sha)
        with self.assertRaises(ValueError): imp.reference_text(value,'sample-0000000000000001',hashlib.sha256(b'ONE two!').hexdigest())

    def test_transfer_paths_reject_escape_alias_and_duplicate(self):
        import alpha_asr_campaign_import as imp
        for names in (['../outside'],['a','A'],['a','a'],['C:/outside'],['dir\\name']):
            with self.assertRaises(ValueError): imp.safe_names(names)
        imp.safe_names(['manifests/selected-manifest.json','audio/synthetic.mp3'])

    def test_ended_adb_does_not_prove_unstarted_remote_process(self):
        from unittest.mock import Mock
        from alpha_asr_campaign_device import Device
        d=Device('synthetic-adb',{},'a'*64)
        d.active=Mock(); d.active.poll.return_value=1
        d._state=lambda:'PENDING'
        with self.assertRaisesRegex(ValueError,'DEVICE_START_UNVERIFIED'): d.stop()

    def test_driver_scores_empty_hypothesis_and_preserves_metrics(self):
        import run_alpha_asr_campaign as driver
        import alpha_asr_eval_text_contract as frozen
        with tempfile.TemporaryDirectory() as td:
            oracle=self.c.Oracle(Path(td)); oracle.compile()
            observation={'metrics':{'decodedFrames':16001,'inferenceElapsedMicros':2500000,
                'modelLoadElapsedMicros':100000},'memory':{'peakPssBytes':400000000,'peakNativeHeapBytes':300000000},
                'thermal':{'initial':0,'maximum':3}}
            result,prepared=driver.scored_result({'sampleId':'sample-0000000000000001','locale':'en'},
                'ONE two','',observation,oracle)
            frozen.validate_result(result)
            self.assertEqual([],prepared['rawHypothesisTokens'])
            self.assertEqual(2,result['oracleCounts']['normalized']['deletions'])
            self.assertEqual(1000063,result['timing']['audioDurationMicros'])
            self.assertEqual('SEVERE',result['thermal']['maximumStatus'])

    def test_salvage_attempts_every_file_and_prevents_cleanup_on_failure(self):
        from unittest.mock import Mock
        from alpha_asr_campaign_device import Device
        d=Device('synthetic-adb',{},'a'*64); d.owned=True
        d.shell=Mock(side_effect=ValueError('disconnected'))
        with tempfile.TemporaryDirectory() as td:
            with self.assertRaisesRegex(ValueError,'PRIVATE_EVIDENCE_TRANSFER_UNVERIFIED'): d.salvage(td)
        self.assertEqual(4,d.shell.call_count)
        with self.assertRaisesRegex(ValueError,'PRIVATE_EVIDENCE_PRESERVED_ON_DEVICE'): d.cleanup()


if __name__=='__main__': unittest.main()
