"""Synthetic metadata only: no private data, audio, model, device or network."""
import copy
import contextlib
import hashlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools import alpha_asr_pilot_manifest as old
from tools.test_alpha_asr_pilot_manifest import fixture, sample, encode, key, inventory_hash, seal

try:
    from tools import alpha_asr_small_acceptance_manifest as new
except ImportError:
    new = None


def authority(count=80):
    inv, original = fixture()
    inv['candidates'] = [sample(loc, i) for loc in ('ru', 'en') for i in range(1, count+1)]
    inv['candidateCount'] = len(inv['candidates'])
    original['samples'] = []
    for loc in ('ru', 'en'):
        rows = sorted((r for r in inv['candidates'] if r['locale']==loc), key=lambda r:(bytes.fromhex(key(r)), r['upstreamRelativePath'].encode()))[:24]
        original['samples'].extend({**r,'rank':i,'selectionKey':key(r),'eligibilityResult':'ELIGIBLE'} for i,r in enumerate(rows,1))
    seal(inv,original)
    ledger={'schemaVersion':1,'profileId':'dora-alpha-asr-prior-use-v0.1',
            'inventorySha256':inventory_hash(inv),'originalManifestSha256':original['manifestSha256'],
            'developmentState':'NO_SEPARATE_DEVELOPMENT_OR_TUNING_SET_EXISTS',
            'participantDisjointness':'NOT_APPLICABLE_NO_DEVELOPMENT_SET','records':[]}
    return inv,original,ledger


class SmallManifestTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(new, 'Successor validator is not implemented')
        self.inv,self.orig,self.ledger=authority()

    def prepare(self):
        with patch.object(new,'INVENTORY_SHA256',inventory_hash(self.inv)), patch.object(new,'ORIGINAL_SHA256',self.orig['manifestSha256']):
            return new.prepare(encode(self.inv),encode(self.orig),encode(self.ledger))

    def verify(self,manifest):
        with patch.object(new,'INVENTORY_SHA256',inventory_hash(self.inv)), patch.object(new,'ORIGINAL_SHA256',self.orig['manifestSha256']):
            return new.verify(encode(self.inv),encode(self.orig),encode(self.ledger),encode(manifest))

    def update_authority(self):
        seal(self.inv,self.orig)
        self.ledger['inventorySha256']=inventory_hash(self.inv)
        self.ledger['originalManifestSha256']=self.orig['manifestSha256']

    def fresh(self,loc='ru'):
        ids={r['sampleId'] for r in self.orig['samples']}
        return [r for r in self.inv['candidates'] if r['locale']==loc and r['sampleId'] not in ids]

    def test_known_answer_and_output_privacy(self):
        manifest,audit=self.prepare()
        expected=[]
        for loc in ('ru','en'):
            expected.extend(sorted(self.fresh(loc),key=lambda r:(bytes.fromhex(key(r)),r['upstreamRelativePath'].encode()))[:24])
        self.assertEqual([r['sampleId'] for r in expected],[r['sampleId'] for r in manifest['samples']])
        public=self.verify(manifest)
        self.assertEqual({'ru':24,'en':24},public['selectedCounts'])
        self.assertEqual({'ru':56,'en':56},public['eligibleCounts'])
        self.assertEqual(48,public['exclusionCounts']['originalSource'])
        self.assertNotIn('sample-',json.dumps(public)); self.assertNotIn('synthetic/',json.dumps(public))

    def test_permutation_invariance(self):
        first=self.prepare()
        self.inv['candidates'].reverse(); self.ledger['records'].reverse()
        self.assertEqual(first,self.prepare())

    def test_original_source_exclusion(self):
        m,a=self.prepare(); original={r['upstreamRelativePath'] for r in self.orig['samples']}
        self.assertFalse(original & {r['upstreamRelativePath'] for r in m['samples']})
        self.assertEqual(48,a['summary']['exclusionCounts']['originalSource'])

    def test_original_audio_digest_exclusion(self):
        row=self.fresh()[0]; row['audioSha256']=self.orig['samples'][0]['audioSha256']; self.update_authority()
        m,a=self.prepare()
        self.assertNotIn(row['sampleId'],[r['sampleId'] for r in m['samples']])
        self.assertEqual(49,a['summary']['exclusionCounts']['originalAudio'])

    def test_prior_ledger_exclusion_and_order(self):
        rows=self.fresh()[:2]
        self.ledger['records']=[{k:r[k] for k in ('locale','datasetId','upstreamRelativePath','audioSha256')} | {'useClass':'EVALUATION'} for r in rows]
        m,a=self.prepare(); self.assertEqual(2,a['summary']['exclusionCounts']['priorUse'])
        self.assertFalse({r['sampleId'] for r in rows}&{r['sampleId'] for r in m['samples']})
        self.ledger['records'].reverse(); self.assertEqual((m,a),self.prepare())

    def test_duplicate_all_members_after_prior_exclusion(self):
        x,y=self.fresh()[:2]; y['audioSha256']=x['audioSha256']; self.update_authority()
        m,a=self.prepare(); self.assertEqual(2,a['summary']['exclusionCounts']['duplicateAudio'])
        self.assertFalse({x['sampleId'],y['sampleId']}&{r['sampleId'] for r in m['samples']})
        self.ledger['records']=[{k:x[k] for k in ('locale','datasetId','upstreamRelativePath','audioSha256')}|{'useClass':'EVALUATION'}]
        m,a=self.prepare(); self.assertEqual(0,a['summary']['exclusionCounts']['duplicateAudio'])
        self.assertEqual(2,a['summary']['exclusionCounts']['priorUse'])

    def test_duration_boundaries(self):
        rows=self.fresh()[:4]
        for r,d in zip(rows,[1000,20000,999,20001]): r['durationMs']=d
        self.update_authority(); m,a=self.prepare()
        pool={r['sampleId'] for r in a['eligiblePool']}
        self.assertTrue({r['sampleId'] for r in rows[:2]}<=pool)
        self.assertFalse({r['sampleId'] for r in rows[2:]}&pool)

    def test_insufficient_each_language(self):
        for loc in ('ru','en'):
            with self.subTest(loc=loc):
                self.inv,self.orig,self.ledger=authority()
                for r in self.fresh(loc)[23:]: r['decodeResult']='NOT_RUN'
                self.update_authority()
                with self.assertRaisesRegex(old.ValidationError,'E_INSUFFICIENT_FRESH_HOLDOUT'): self.prepare()

    def test_exact_utf8_tiebreak(self):
        rows=[{'upstreamRelativePath':p} for p in ['z','é','Z','e\u0301']]
        with patch.object(new,'selection_key',return_value=b'x'*32):
            self.assertEqual(['Z','e\u0301','z','é'],[r['upstreamRelativePath'] for r in sorted(rows,key=new.order_key)])

    def test_manifest_tampering_rejected(self):
        valid,_=self.prepare()
        changes=[lambda m:m.update(selectionContractId='changed'),lambda m:m.update(selectionContractSha256='a'*64),
                 lambda m:m.update(manifestSha256='a'*64),lambda m:m['samples'].__setitem__(1,copy.deepcopy(m['samples'][0])),
                 lambda m:m['samples'][0].update(sampleId='sample-'+'f'*16),lambda m:m['samples'][0].update(rank=2),
                 lambda m:m['samples'].reverse(),lambda m:m.update(extra='private-marker'),
                 lambda m:m['samples'][0].update(extra='private-marker')]
        for change in changes:
            with self.subTest(change=changes.index(change)):
                m=copy.deepcopy(valid); change(m)
                with self.assertRaises(old.ValidationError): self.verify(m)

    def test_manual_substitution_even_resealed(self):
        m,a=self.prepare(); row=a['eligiblePool'][-1]
        m['samples'][0]={**row,'rank':1,'selectionKey':key(row),'eligibilityResult':'ELIGIBLE'}
        m['manifestSha256']=old.digest({k:v for k,v in m.items() if k!='manifestSha256'})
        with self.assertRaises(old.ValidationError): self.verify(m)

    def test_wrong_authority_digests(self):
        with self.assertRaisesRegex(old.ValidationError,'E_INVENTORY_AUTHORITY'):
            new.prepare(encode(self.inv),encode(self.orig),encode(self.ledger))
        with patch.object(new,'INVENTORY_SHA256',inventory_hash(self.inv)):
            with self.assertRaisesRegex(old.ValidationError,'E_ORIGINAL_AUTHORITY'):
                new.prepare(encode(self.inv),encode(self.orig),encode(self.ledger))

    def test_extra_ledger_and_bad_provenance(self):
        self.ledger['extra']='private-marker'
        with self.assertRaises(old.ValidationError): self.prepare()
        self.ledger.pop('extra'); self.inv['datasets'][0]['release']='wrong'
        with self.assertRaises(old.ValidationError): self.prepare()

    def test_cli_content_free_failure(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'private-marker.json'; p.write_text('{"private-marker":1}')
            out,err=io.StringIO(),io.StringIO()
            with contextlib.redirect_stdout(out),contextlib.redirect_stderr(err): code=new.main([str(p)]*4)
            self.assertEqual(1,code); self.assertNotIn('private-marker',out.getvalue()+err.getvalue())
            self.assertEqual({'result','code'},set(json.loads(err.getvalue())))


if __name__=='__main__': unittest.main()
