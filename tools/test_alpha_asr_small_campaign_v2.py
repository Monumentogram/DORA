"""Generated fixtures only; never open the controlled acceptance package."""
import contextlib
import copy
import datetime
import hashlib
import io
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import alpha_asr_small_campaign as c
import alpha_asr_small_acceptance_manifest as selection
from tools.test_alpha_asr_pilot_manifest import sample

try:
    import run_alpha_asr_small_campaign_v2 as op
except ImportError:
    op = None


def seal(doc):
    doc['manifestSha256'] = c.digest({k: v for k, v in doc.items() if k != 'manifestSha256'})


def fixture():
    rows = []
    for locale in ('ru', 'en'):
        group = sorted([sample(locale, i) for i in range(1, 25)], key=selection.order_key)
        rows += [{**r, 'rank': i, 'selectionKey': selection.selection_key(r).hex(),
                  'eligibilityResult': 'ELIGIBLE'} for i, r in enumerate(group, 1)]
    selected = dict(schemaVersion=1, profileId=selection.PROFILE,
        selectionContractId=selection.CONTRACT_ID, selectionContractSha256=selection.CONTRACT_SHA256,
        candidateInventorySha256=selection.INVENTORY_SHA256, originalManifestSha256=selection.ORIGINAL_SHA256,
        priorUseLedgerSha256='1'*64, eligiblePoolSha256='2'*64,
        exclusionDigests={k: '3'*64 for k in ('combinedExclusionSet', 'duplicateGroups',
                                           'originalAudio', 'originalSource', 'priorUse')},
        sampleCount=48, selectedCounts={'ru':24, 'en':24}, samples=rows)
    bindings = []
    for r in rows:
        bindings.append({k:r[k] for k in ('sampleId','locale','rank','selectionKey','durationMs','audioSha256')} | {
            'sourcePath':r['upstreamRelativePath'], 'audioBytes':r['audioByteLength'],
            'referenceBytes':10, 'referenceSha256':r['referenceTextSha256'],
            'audioRelativePath':f"audio/{r['locale']}/{r['rank']:02}.mp3",
            'referenceRelativePath':f"references/{r['locale']}/{r['rank']:02}.txt"})
    materialized = dict(schemaVersion=1, profileId='dora-alpha-asr-small-materialized-holdout-v0.1',
        metadataSelectedManifestSha256='', selectionContractSha256=selection.CONTRACT_SHA256,
        combinedExclusionSetSha256='3'*64, eligiblePoolSha256='2'*64, selectedCounts={'ru':24,'en':24},
        sourceIndexes=[dict(locale=loc,providerRows=100,testRows=50,indexSha256='4'*64,archiveSha256='5'*64)
                       for loc in ('ru','en')], bindings=bindings, audioTranscoded=False, referenceTextModified=False)
    transfer = dict(schemaVersion=1, profileId='dora-alpha-asr-small-acceptance-transfer-v0.1',
                    materializedManifestSha256='', files=[])
    freeze = dict(schemaVersion=1,profileId='dora-alpha-asr-small-pre-inference-freeze-v0.1',
        selectionContractId=selection.CONTRACT_ID,selectionContractSha256=selection.CONTRACT_SHA256,
        combinedExclusionSetSha256='3'*64,eligiblePoolSha256='2'*64,metadataSelectedManifestSha256='',
        materializedSelectedManifestSha256='',transferIndexSha256='',sampleCount=48,selectedCounts={'ru':24,'en':24},
        candidate={'filename':'ggml-small-q5_1.bin','bytes':c.MODEL_BYTES,'sha256':c.MODEL_SHA},
        runtimeCommit=c.text.RUNTIME_COMMIT,decodingProfileSha256=c.DECODING_SHA,
        measurementProtocolSha256=c.PROTOCOL_SHA,resourceGatesSha256=c.GATES_SHA,
        governanceCommit=c.GOVERNANCE_COMMIT,governanceGitBlob='961ed67b3da21967497a9ed5c1be176f27fcde27',
        dataRetentionAuthority='5.6B_OWNER_DECISION_AND_5.6C.1.1_EXPLICIT_SOURCE_RECOVERY_INSTRUCTION',
        absoluteDeletionDeadline='2026-10-25',completedUtc='2026-09-25T10:00:00+00:00',asrInferenceBeforeFreeze=0,
        copyClasses=['EXACT_SOURCE_ARCHIVES','ARCHIVE_MEMBER_METADATA','SELECTED_AUDIO_48',
                     'SELECTED_REFERENCES_48','MATERIALIZED_MANIFEST','TRANSFER_INDEX','PRE_INFERENCE_FREEZE'])
    docs = [selected, materialized, transfer, freeze]
    refresh(docs, rebuild_transfer=True)
    return docs


def refresh(docs, rebuild_transfer=False):
    s,m,t,f = docs
    seal(s); m['metadataSelectedManifestSha256'] = s['manifestSha256']; seal(m)
    t['materializedManifestSha256'] = m['manifestSha256']
    if rebuild_transfer:
        t['files'] = [dict(path=b[k+'RelativePath'],bytes=b[k+'Bytes'],sha256=b[k+'Sha256'])
                      for b in m['bindings'] for k in ('audio','reference')]
        t['files'].append(dict(path='materialized-manifest.json',bytes=0,sha256=''))
    for r in t['files']:
        if r['path'] == 'materialized-manifest.json':
            raw = c.canonical(m)+b'\n'; r.update(bytes=len(raw),sha256=hashlib.sha256(raw).hexdigest())
    t['files'].sort(key=lambda r:r['path'])
    f.update(metadataSelectedManifestSha256=s['manifestSha256'],
             materializedSelectedManifestSha256=m['manifestSha256'],transferIndexSha256=c.digest(t))


def pins(docs):
    return tuple(d['manifestSha256'] if i<2 else c.digest(d) for i,d in enumerate(docs))


class AdmittedDate(datetime.date):
    @classmethod
    def today(cls): return cls(2026,9,26)


class CompatibilityTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(op, 'Additive successor metadata operator is missing')
        self.docs = fixture()

    def verify(self, docs=None):
        docs = self.docs if docs is None else docs
        with patch.object(op, 'DOCUMENT_PINS', pins(docs)):
            return op.verify_metadata(*(c.canonical(d)+b'\n' for d in docs))

    def bad(self, mutate, reseal=True):
        mutate(self.docs)
        if reseal: refresh(self.docs)
        with self.assertRaises(ValueError): self.verify()

    def test_separate_schemas_bind_exactly_48(self):
        self.assertNotIn('samples', self.docs[1])
        report = self.verify()
        self.assertEqual('PASS',report['result'])
        self.assertEqual(48,report['crossBoundCases'])
        self.assertEqual(97,report['transferRecords'])
        self.assertEqual({'ru':24,'en':24}, report['selectedCounts'])
        for r in self.docs[0]['samples']:
            for k in ('sampleId','audioSha256','referenceTextSha256','upstreamRelativePath'):
                self.assertNotIn(r[k], c.canonical(report).decode())

    def test_wrong_each_document_sha(self):
        for i in range(4):
            with self.subTest(document=i):
                wrong = list(pins(self.docs)); wrong[i]='f'*64
                with patch.object(op,'DOCUMENT_PINS',tuple(wrong)), self.assertRaisesRegex(ValueError,'DOCUMENT_IDENTITY'):
                    op.verify_metadata(*(c.canonical(d)+b'\n' for d in self.docs))

    def test_missing_or_extra_top_fields_each_document(self):
        for i in range(4):
            for extra in (True,False):
                self.docs=fixture()
                with self.subTest(document=i,extra=extra):
                    self.bad(lambda ds: ds[i].update(unexpected=1) if extra else ds[i].pop('profileId'))

    def test_wrong_selected_count(self): self.bad(lambda ds:ds[0].update(sampleCount=47))
    def test_wrong_locale_counts(self): self.bad(lambda ds:ds[0]['selectedCounts'].update(ru=23,en=25))
    def test_wrong_locale_order(self): self.bad(lambda ds:ds[0]['samples'].reverse())
    def test_wrong_within_locale_order(self):
        self.bad(lambda ds:ds[0]['samples'].__setitem__(slice(0,2), ds[0]['samples'][0:2][::-1]))
    def test_duplicate_selected(self): self.bad(lambda ds:ds[0]['samples'].__setitem__(1,copy.deepcopy(ds[0]['samples'][0])))
    def test_missing_materialized(self): self.bad(lambda ds:ds[1]['bindings'].pop())
    def test_extra_materialized(self): self.bad(lambda ds:ds[1]['bindings'].append(copy.deepcopy(ds[1]['bindings'][0])))
    def test_missing_transfer(self): self.bad(lambda ds:ds[2]['files'].pop(0))
    def test_extra_transfer(self): self.bad(lambda ds:ds[2]['files'].append(dict(path='extra',bytes=1,sha256='1'*64)))
    def test_duplicate_transfer_path(self): self.bad(lambda ds:ds[2]['files'].__setitem__(1,copy.deepcopy(ds[2]['files'][0])))
    def test_missing_manifest_transfer_record(self):
        self.bad(lambda ds:ds[2].update(files=[r for r in ds[2]['files'] if r['path']!='materialized-manifest.json']))
    def test_materialized_binding_fields(self):
        for key in ('sampleId','locale','rank','selectionKey','sourcePath','durationMs','audioBytes','audioSha256','referenceSha256'):
            self.docs=fixture()
            with self.subTest(field=key):
                self.bad(lambda ds:ds[1]['bindings'][0].update({key:ds[1]['bindings'][0][key]+1 if type(ds[1]['bindings'][0][key]) is int else 'wrong'}))
    def test_transfer_hash_mismatch(self): self.bad(lambda ds:ds[2]['files'][0].update(sha256='f'*64))
    def test_transfer_size_mismatch(self): self.bad(lambda ds:ds[2]['files'][0].update(bytes=999))
    def test_manifest_transfer_binds_actual_raw_bytes(self):
        with patch.object(op,'DOCUMENT_PINS',pins(self.docs)), self.assertRaisesRegex(ValueError,'TRANSFER_BINDING'):
            op.verify_metadata(c.canonical(self.docs[0]),c.canonical(self.docs[1]),
                               c.canonical(self.docs[2]),c.canonical(self.docs[3]))
    def test_path_override(self):
        for path in ('../x','C:/x','audio/ru/02.mp3','audio/ru/01.mp3:stream','/x','audio/CON.mp3'):
            self.docs=fixture()
            with self.subTest(path=path): self.bad(lambda ds:ds[1]['bindings'][0].update(audioRelativePath=path))
    def test_nested_extra_field(self): self.bad(lambda ds:ds[1]['bindings'][0].update(bindingsOverride={}))
    def test_freeze_binding_mismatch(self):
        for key in ('selectionContractSha256','metadataSelectedManifestSha256','materializedSelectedManifestSha256',
                    'transferIndexSha256','eligiblePoolSha256','runtimeCommit','resourceGatesSha256'):
            self.docs=fixture()
            with self.subTest(field=key): self.bad(lambda ds:ds[3].update({key:'f'*64}),reseal=False)
    def test_materialized_selection_authority_mismatch(self):
        self.bad(lambda ds:ds[1].update(metadataSelectedManifestSha256='f'*64),reseal=False)
    def test_changed_model_metadata(self): self.bad(lambda ds:ds[3]['candidate'].update(bytes=1))
    def test_boolean_count_rejected(self): self.bad(lambda ds:ds[3].update(sampleCount=True))
    def test_json_fail_closed(self):
        for raw in (b'{',b'{"a":1,"a":2}',b'{"x":NaN}',b'{"x":Infinity}',b'{"x":1e400}', b'[]'):
            with self.subTest(raw=raw), self.assertRaises(ValueError): op.verify_metadata(raw,*[c.canonical(d) for d in self.docs[1:]])
    def test_wrong_operator_identity(self):
        with self.assertRaisesRegex(ValueError,'OPERATOR_IDENTITY'):
            op.verify_metadata(*(c.canonical(d) for d in self.docs),operator_id='v0.1')
    def test_profile_evaluator_reused(self):
        self.assertEqual('217b5662bfd22520779a2df2551f553cb17e97e11f0273e8238682a5f4b7f69f',c.digest(op.c.profile()))
        self.assertIs(op.c.evaluate,c.evaluate)
    def test_measured_api_always_blocked(self):
        with self.assertRaisesRegex(ValueError,'NOT_AUTHORIZED'):
            op.campaign({},'OWNER_AUTHORIZED_5.6C.2B')
    def test_cli_does_not_open_arbitrary_path(self):
        output=io.StringIO()
        with patch.object(Path,'read_bytes',side_effect=AssertionError('No reads')),contextlib.redirect_stdout(output):
            self.assertEqual(2,op.main(['run','sensitive-path']))
        self.assertNotIn('sensitive-path',output.getvalue())
    def test_config_override_before_reads(self):
        for key in ('bindings','selectionSha256','model','audio','operatorId'):
            with self.subTest(key=key),patch.object(Path,'read_bytes',side_effect=AssertionError('No reads')):
                with self.assertRaisesRegex(ValueError,'CONFIG_FIELDS'):
                    op.metadata_preflight({'acceptance':'unused','operatorCommit':'a'*40,key:'override'})
    def test_wrong_repo_head_before_private_reads(self):
        with patch.object(op.subprocess,'run',return_value=subprocess.CompletedProcess([],0,b'b'*40+b'\n',b'')), \
             self.assertRaisesRegex(ValueError,'REPOSITORY_IDENTITY'):
            op.verify_repository(Path('.'),'a'*40)
    def test_dirty_repo_before_private_reads(self):
        def git(args,**kwargs):
            tail=args[3:]
            value={'rev-parse':'a'*40,'branch':'chat/alpha-asr-runner-scope','status':'?? unexpected'}[tail[0]]
            return subprocess.CompletedProcess(args,0,value.encode(),b'')
        with patch.object(op.subprocess,'run',side_effect=git),self.assertRaisesRegex(ValueError,'REPOSITORY_IDENTITY'):
            op.verify_repository(Path('.'),'a'*40)

    def test_file_preflight_reads_only_four_fixed_documents(self):
        seen=[]
        def read(path):
            seen.append(Path(path).as_posix())
            return c.canonical(self.docs[len(seen)-1])+b'\n'
        with patch.object(op.datetime,'date',AdmittedDate), \
             patch.object(op,'DOCUMENT_PINS',pins(self.docs)),patch.object(op,'verify_repository'), \
             patch('run_alpha_asr_campaign.require_private'),patch.object(op.schema,'_read_local',side_effect=read):
            result=op.metadata_preflight({'acceptance':'synthetic','operatorCommit':'a'*40})
        self.assertEqual([f'synthetic/{p}' for p in op.METADATA_PATHS],seen)
        self.assertEqual(4,result['metadataReadCount'])
        self.assertEqual('BLOCKED / NOT_AUTHORIZED',result['measuredExecution'])

    def test_repository_rejection_stops_before_private_reads(self):
        with patch.object(op,'verify_repository',side_effect=ValueError('REPOSITORY_IDENTITY')), \
             patch.object(op.schema,'_read_local',side_effect=AssertionError('No reads')), \
             self.assertRaisesRegex(ValueError,'REPOSITORY_IDENTITY'):
            op.metadata_preflight({'acceptance':'synthetic','operatorCommit':'a'*40})

    def test_storage_rejection_stops_before_private_reads(self):
        with patch.object(op.datetime,'date',AdmittedDate),patch.object(op,'verify_repository'), \
             patch('run_alpha_asr_campaign.require_private',side_effect=ValueError('PRIVATE_STORAGE')), \
             patch.object(op.schema,'_read_local',side_effect=AssertionError('No reads')), \
             self.assertRaisesRegex(ValueError,'PRIVATE_STORAGE'):
            op.metadata_preflight({'acceptance':'synthetic','operatorCommit':'a'*40})

    def repository_fixture(self,root):
        files={}
        for name in (*op.OPERATOR_FILES,'historical.txt'):
            path=root/name; path.parent.mkdir(parents=True,exist_ok=True)
            raw=('generated '+name).encode(); path.write_bytes(raw)
            files[name]={'bytes':len(raw),'sha256':hashlib.sha256(raw).hexdigest()}
        protected={'historical.txt':files.pop('historical.txt')}
        record={'operatorId':op.OPERATOR_ID,'operatorFiles':files,'operatorCanonicalSha256':c.digest(files),
                'protectedHistoricalFiles':protected}
        path=root/op.EVIDENCE_PATH; path.parent.mkdir(parents=True,exist_ok=True)
        path.write_bytes(c.canonical(record))
        return record

    @staticmethod
    def admitted_git(args,**kwargs):
        command=args[3:]
        value=op.BASELINE_HEAD if command==['rev-parse','HEAD^'] else (
            'a'*40 if command==['rev-parse','HEAD'] else
            'chat/alpha-asr-runner-scope' if command==['branch','--show-current'] else '')
        return subprocess.CompletedProcess(args,0,value.encode(),b'')

    def test_repository_frozen_record_and_file_identity(self):
        with tempfile.TemporaryDirectory() as tmp,patch.object(op.subprocess,'run',side_effect=self.admitted_git):
            root=Path(tmp); self.repository_fixture(root)
            op.verify_repository(root,'a'*40)
            (root/op.OPERATOR_FILES[-1]).write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError,'OPERATOR_FILE_IDENTITY'): op.verify_repository(root,'a'*40)

    def test_repository_record_operator_identity(self):
        for key,value in (('operatorId','old'),('operatorCanonicalSha256','f'*64),('operatorFiles',{})):
            with self.subTest(key=key),tempfile.TemporaryDirectory() as tmp, \
                 patch.object(op.subprocess,'run',side_effect=self.admitted_git):
                root=Path(tmp); record=self.repository_fixture(root); record[key]=value
                (root/op.EVIDENCE_PATH).write_bytes(c.canonical(record))
                with self.assertRaisesRegex(ValueError,'OPERATOR_IDENTITY'): op.verify_repository(root,'a'*40)

    def test_retention_blocks_before_private_reads(self):
        class ExpiredDate(datetime.date):
            @classmethod
            def today(cls): return cls(2026,10,26)
        with patch.object(op.datetime,'date',ExpiredDate),patch.object(op,'verify_repository'), \
             patch.object(op.schema,'_read_local',side_effect=AssertionError('No reads')), \
             self.assertRaisesRegex(ValueError,'RETENTION_DEADLINE'):
            op.metadata_preflight({'acceptance':'synthetic','operatorCommit':'a'*40})


if __name__=='__main__': unittest.main()
