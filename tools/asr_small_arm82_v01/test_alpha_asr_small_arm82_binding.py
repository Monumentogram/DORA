"""Generated fixtures only; no actual data, device, or campaign API calls."""
import copy
from pathlib import Path
import tempfile
import unittest
import alpha_asr_small_campaign as historical
import alpha_asr_small_arm82_binding as binding

REPO=Path(historical.__file__).resolve().parents[1]
PINS={k:str(i)*64 for i,k in enumerate(('manifest','transfer','freeze','selection'),1)}

class BindingTests(unittest.TestCase):
    def test_isolated_profile_and_functions_preserve_history(self):
        before=historical.canonical(historical.profile())
        c,op=binding.composition(REPO,PINS)
        self.assertIsNot(c,historical)
        self.assertIs(op.c,c)
        self.assertEqual(historical.canonical(historical.profile()),before)
        self.assertEqual(c.profile()['selectedManifestSha256'],PINS['manifest'])
        self.assertEqual(c.profile()['resourceGates'],historical.profile()['resourceGates'])
        c.validate_profile(c.profile())
        self.assertEqual(c.case_resources.__code__.co_code,historical.case_resources.__code__.co_code)
        self.assertEqual(c.evaluate.__code__.co_code,historical.evaluate.__code__.co_code)

    def test_only_identity_fields_can_change(self):
        c,_=binding.composition(REPO,PINS)
        bad=c.profile();bad['resourceGates']['maximumRtf']=7
        with self.assertRaisesRegex(ValueError,'SEMANTIC_PROFILE_DELTA'):
            binding.check_semantics(bad,historical.profile())
        bad=c.profile();bad['languageGates']['ru']['errors']=21
        with self.assertRaisesRegex(ValueError,'SEMANTIC_PROFILE_DELTA'):
            binding.check_semantics(bad,historical.profile())

    def test_claim_is_durable_and_second_claim_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d);binding.claim(p,{'identity':'generated'})
            original=(p/'campaign-started.json').read_bytes()
            with self.assertRaises(ValueError):binding.claim(p,{'identity':'second'})
            self.assertEqual((p/'campaign-started.json').read_bytes(),original)

    def test_case_binding_rejects_swapped_ordinal_and_changed_row(self):
        row={'sampleId':'sample-0000000000000001','locale':'ru'}
        case={'position':1,'row':copy.deepcopy(row)}
        binding.check_case(row,1,case)
        with self.assertRaisesRegex(ValueError,'CASE_BINDING_IDENTITY'):binding.check_case(row,2,case)
        with self.assertRaisesRegex(ValueError,'CASE_BINDING_IDENTITY'):binding.check_case(dict(row,locale='en'),1,case)

    def test_default_api_never_enters_verification(self):
        with self.assertRaisesRegex(ValueError,'MEASURED_EXECUTION_NOT_AUTHORIZED'):
            binding.campaign({},'0'*64)

    def test_complete_and_resource_failed_results_match_frozen_evaluator(self):
        from test_alpha_asr_small_campaign import selected,attempt
        c,_=binding.composition(REPO,PINS)
        rows=selected();attempts=[attempt(r) for r in rows]
        for failed in (False,True):
            if failed:attempts[18]['inferenceMicros']=6000001;attempts=attempts[:19]
            old=historical.evaluate(historical.profile(),rows,attempts,'VERIFIED')
            new=c.evaluate(c.profile(),rows,attempts,'VERIFIED')
            for key in old:
                if key not in ('profileId','profileSha256'):self.assertEqual(new[key],old[key],key)

    def test_inherited_sequencer_stops_on_first_resource_failure_without_retry(self):
        from test_alpha_asr_small_campaign import selected,attempt
        from alpha_asr_campaign import Journal
        c,op=binding.composition(REPO,PINS);rows=selected();calls=[]
        class Fake:
            def warmup(self):return 'VERIFIED'
            def case(self,row,position):
                calls.append(position);r=attempt(row);r['inferenceMicros']=6000001;return r
            def cleanup(self):return 'VERIFIED'
        with tempfile.TemporaryDirectory() as td:
            j=Journal(Path(td)/'attempts.sqlite')
            try:
                result=op.run_sequence(rows,Fake(),j,lambda:None)
                self.assertEqual(calls,[1]);self.assertEqual(len(j.records()),1)
                self.assertEqual(result['resourceFailures'],['RTF_MAXIMUM'])
                with self.assertRaises(ValueError):op.run_sequence(rows,Fake(),j,lambda:None)
                self.assertEqual(calls,[1])
            finally:j.close()

if __name__=='__main__':unittest.main()
