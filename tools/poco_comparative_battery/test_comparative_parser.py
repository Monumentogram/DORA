import unittest
try:
    from .comparative_parser import parse_checkin, summarize_slice, baseline_covers_slice
except ImportError:
    from comparative_parser import parse_checkin, summarize_slice, baseline_covers_slice

HEADER='9,0,i,vers,36,213,build,build\n'
def row(ms,charge=10000,usb=False):
    return {'probeId':'A-01','phase':'MEASURING','elapsedRealtimeMs':ms,'windowStartMs':1000,'chargeCounterMicroAh':charge,'chargeCounterMicroAhSupported':True,'interactive':False,'AC powered':False,'USB powered':usb,'Wireless powered':False,'Dock powered':False,'thermalStatus':0}
def end():
    return {'probeId':'A-01','phase':'END','actualSampleSpanMs':600000,'samples':2,'screenOffUnpluggedObserved':True,'tenMinuteWindowComplete':True}
class ParserTests(unittest.TestCase):
    def test_uid_estimate_is_not_system_discharge_or_energy(self):
        result=parse_checkin(HEADER+'9,0,l,pwi,uid,99,1,0,0\n9,10300,l,pwi,uid,0.0123,0,0,0\n',10300)
        self.assertEqual(result['estimatedUidChargeMah'],'0.0123')
        self.assertEqual(result['semantics'],'MODEL_ESTIMATED_UID_CHARGE_NOT_MEASURED_ENERGY')
        self.assertNotIn('microWh',result)
    def test_other_uid_private_names_never_escape(self):
        result=parse_checkin(HEADER+'9,10301,l,pr,PRIVATE_CANARY,1,2,3\n9,10300,l,cpu,4,5,0\n',10300)
        self.assertNotIn('PRIVATE_CANARY',str(result));self.assertEqual(result['cpuMs'],{'user':4,'system':5})
    def test_absent_uid_is_missing_not_zero(self):
        self.assertIsNone(parse_checkin(HEADER,10300)['estimatedUidChargeMah'])
        self.assertIsNone(parse_checkin(HEADER,10300)['cpuMs'])
    def test_unknown_schema_rejected(self):
        with self.assertRaises(ValueError):parse_checkin(HEADER.replace('vers,36','vers,37'),10300)
    def test_invalid_estimate_rejected(self):
        for value in ('NaN','Infinity','-1','invalid'):
            with self.subTest(value=value),self.assertRaises(ValueError):parse_checkin(HEADER+f'9,10300,l,pwi,uid,{value},0,0,0\n',10300)
    def test_duplicate_target_estimate_rejected(self):
        line='9,10300,l,pwi,uid,1,0,0,0\n'
        with self.assertRaises(ValueError):parse_checkin(HEADER+line+line,10300)
    def test_observed_charge_delta_retains_units_and_no_acceptance(self):
        r=summarize_slice([row(1000),row(601000,9500),end()])
        self.assertEqual(r['candidateChargeDeltaMicroAh'],500)
        self.assertEqual(r['maxObservedGapMs'],600000)
        self.assertFalse(r['continuousPowerProof'])
        self.assertEqual(r['acceptance'],'NOT_EVALUATED')
    def test_charging_samples_invalidate_candidate_delta(self):
        self.assertIsNone(summarize_slice([row(1000),row(601000,9500,True)])['candidateChargeDeltaMicroAh'])
    def test_sentinel_increase_or_zero_are_not_usable_consumption(self):
        for value in (-9223372036854775808,11000,10000):
            with self.subTest(value=value):self.assertIsNone(summarize_slice([row(1000),row(601000,value)])['candidateChargeDeltaMicroAh'])
    def test_short_window_does_not_become_ten_minutes(self):
        r=summarize_slice([row(1000),row(300000,9500)])
        self.assertFalse(r['tenMinuteSampleSpan']);self.assertIsNone(r['candidateChargeDeltaMicroAh'])
    def test_missing_or_inconsistent_end_is_not_candidate(self):
        for terminal in ([],[{**end(),'tenMinuteWindowComplete':False}],[{**end(),'samples':3}],[{**end(),'probeId':'B-01'}],[end(),end()]):
            with self.subTest(terminal=terminal):self.assertIsNone(summarize_slice([row(1000),row(601000,9500)]+terminal)['candidateChargeDeltaMicroAh'])
    def test_mixed_probe_or_window_is_not_candidate(self):
        for change in ({'probeId':'B-01'},{'windowStartMs':2000}):
            with self.subTest(change=change):self.assertIsNone(summarize_slice([row(1000),{**row(601000,9500),**change},end()])['candidateChargeDeltaMicroAh'])
    def test_baseline_must_cover_entire_named_window(self):
        samples=[{**row(1000),'probeId':'B-01'},{**row(601000,9500),'probeId':'B-01'},{**end(),'probeId':'B-01'}]
        receipt={'mode':'MINIMAL_AUDIO_RECORD_FGS_BASELINE','startElapsedMs':900,'endElapsedMs':602000,'outcome':'STOPPED','readErrors':0,'framesReadAndDiscarded':9600000,'sampleRate':16000,'channels':1,'encoding':'PCM_S16LE','routeType':15,'audioSaved':False}
        self.assertTrue(baseline_covers_slice(receipt,samples))
        for change in ({'startElapsedMs':1001},{'endElapsedMs':600999},{'readErrors':1},{'outcome':'READ_ERROR'},{'framesReadAndDiscarded':0},{'sampleRate':48000},{'audioSaved':True},{'routeType':7}):
            with self.subTest(change=change):self.assertFalse(baseline_covers_slice({**receipt,**change},samples))
if __name__=='__main__':unittest.main()
