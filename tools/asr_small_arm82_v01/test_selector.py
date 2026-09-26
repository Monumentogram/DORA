import unittest
try: import selector
except ImportError: selector=None

def row(n,locale='ru',**kw):
    return dict({'locale':locale,'datasetId':'dataset-'+locale,'release':'release','upstreamRelativePath':f'audios/{locale}-{n:03}.mp3',
       'participantSha256':f'{n+1000:064x}','audioSha256':f'{n+(100000 if locale=="en" else 0):064x}',
       'sourceSplit':'dev','durationMs':1000,'decodeResult':'VALIDATED','referenceTextPresent':True},**kw)

class SelectorTests(unittest.TestCase):
    def setUp(self):self.assertIsNotNone(selector,'separate deterministic selector is missing')
    def test_prior_participant_excludes_different_record(self):
        rows=[row(i) for i in range(1,26)]+[row(i,'en') for i in range(1,25)]
        chosen,audit=selector.select(rows,set(),set(),{row(25)['participantSha256']})
        self.assertEqual([r['locale'] for r in chosen],['ru']*24+['en']*24)
        self.assertNotIn(row(25),chosen);self.assertEqual(audit['eligibleCounts'],{'ru':24,'en':24})
    def test_audio_duplicates_all_excluded_not_one_representative(self):
        rows=[row(i) for i in range(1,27)]+[row(i,'en') for i in range(1,25)]
        rows[25]['audioSha256']=rows[24]['audioSha256']
        chosen,audit=selector.select(rows,set(),set(),set())
        self.assertEqual(audit['duplicateRows'],2)
        self.assertFalse(any(r['audioSha256']==row(25)['audioSha256'] for r in chosen))
    def test_source_or_content_reuse_forces_shortage(self):
        rows=[row(i) for i in range(1,25)]+[row(i,'en') for i in range(1,25)]
        for prior_source,prior_audio in [({selector.source_key(rows[0])},set()),(set(),{rows[0]['audioSha256']})]:
            with self.assertRaisesRegex(ValueError,'INSUFFICIENT'):
                selector.select(rows,prior_source,prior_audio,set())
    def test_missing_identity_or_invalid_duration_cannot_fill_quota(self):
        for change in [{'participantSha256':''},{'durationMs':999},{'durationMs':20001},{'decodeResult':'FAILED'},{'referenceTextPresent':False},{'sourceSplit':'test'}]:
            rows=[row(i) for i in range(1,25)]+[row(i,'en') for i in range(1,25)];rows[0].update(change)
            with self.assertRaisesRegex(ValueError,'INSUFFICIENT'):selector.select(rows,set(),set(),set())
    def test_input_order_does_not_change_selection(self):
        rows=[row(i,l) for l in ('ru','en') for i in range(1,31)]
        a,_=selector.select(rows,set(),set(),set());b,_=selector.select(list(reversed(rows)),set(),set(),set())
        self.assertEqual(a,b);self.assertEqual(len(a),48)

if __name__=='__main__':unittest.main()
