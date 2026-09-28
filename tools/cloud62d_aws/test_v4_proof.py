"""Offline one-shot default-CMK proof tests; synthetic API, no AWS."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools.cloud62d_aws import aws_prepare as setup
from tools.cloud62d_aws.test_aws import fixture_config, ResourceApi
from tools.cloud62d_owned.corpus import digest, encoded
try:
    from tools.cloud62d_aws import v4_proof as proof
except ImportError:
    proof = None


class ProbeApi(ResourceApi):
    def __init__(self, config, root, state, *, independent=False, fault=None):
        super().__init__(config)
        self.root, self.state, self.independent, self.probe_fault = root, state, independent, fault
        self.calls = []

    def call(self, service, op, *args):
        self.calls.append((service, op, args))
        if service == 'transcribe': raise AssertionError('Speech API forbidden')
        if op == 'describe-stacks':
            return {'Stacks': [{'StackName': self.c['stack_name'], 'StackStatus': self.probe_fault if self.probe_fault in ('UPDATE_IN_PROGRESS', 'CREATE_COMPLETE') else 'UPDATE_COMPLETE',
                    'Parameters': [{'ParameterKey': k, 'ParameterValue': v} for k, v in {'RunId':self.c['run_id'], 'OwnerPrincipalArn':self.c['owner_principal_arn'], 'WriteDeadline':self.c['write_deadline']}.items()],
                    'Outputs': [{'OutputKey': k, 'OutputValue': v} for k, v in self.c['outputs'].items()]}]}
        if op == 'get-caller-identity':
            role = 'Other' if self.probe_fault == 'identity' else 'dora-62d-a1b2c3d4-operator'
            return {'Account':self.c['account_id'], 'Arn':'arn:aws:sts::123456789012:assumed-role/'+role+'/probe'}
        if op == 'get-bucket-encryption':
            if hasattr(self, 'encryption_rules'):
                return {'ServerSideEncryptionConfiguration': {'Rules':self.encryption_rules}}
            return {'ServerSideEncryptionConfiguration': {'Rules': [{'ApplyServerSideEncryptionByDefault': {'SSEAlgorithm':'aws:kms', 'KMSMasterKeyID': self.c['outputs']['OutputKey'] if self.probe_fault != 'default' else 'wrong'}, 'BucketKeyEnabled':False}]}}
        if op == 'get-bucket-versioning': return {'Status':'Enabled'} if self.probe_fault == 'versioning' else {}
        if op in ('put-object','head-object','delete-object','list-objects-v2','list-object-versions','list-multipart-uploads'):
            assert args[args.index('--bucket')+1] == self.c['outputs']['OutputBucket']
            assert args[args.index('--expected-bucket-owner')+1] == self.c['account_id']
            if op == 'put-object':
                assert not self.independent
                assert (self.root/'reservation.json').is_file()
                assert not any('sse' in arg or 'server-side-encryption' in arg for arg in args)
                self.state['puts'] += 1
                self.state['object'] = Path(args[args.index('--body')+1]).read_bytes()
                if self.probe_fault == 'uncertain': raise TimeoutError('lost response')
                return {}
            if op == 'delete-object':
                self.state['deletes'] += 1
                if self.probe_fault == 'delete': raise setup.AwsError('AccessDenied')
                self.state.pop('object',None); return {}
            if op == 'head-object':
                if 'object' not in self.state:
                    if self.probe_fault == 'head-absence': raise setup.AwsError('AccessDenied')
                    raise setup.AwsError('404')
                return {'ContentLength':len(self.state['object']), 'ServerSideEncryption':'aws:kms', 'SSEKMSKeyId':self.c['outputs']['OutputKey'] if self.probe_fault != 'head' else 'wrong'}
            prefix=args[args.index('--prefix')+1]
            assert prefix == 'output/__v4_default_kms_proof__/'
            if self.independent and self.probe_fault == 'independent' and self.state['puts']: return {'Contents':[{'Key':prefix+'leftover'}]}
            if self.probe_fault == 'truncated': return {'IsTruncated':True}
            if self.probe_fault == 'after-'+op and self.state['puts']:
                return { {'list-objects-v2':'Contents','list-object-versions':'Versions','list-multipart-uploads':'Uploads'}[op]:[{'Key':prefix+'leftover'}]}
            if self.probe_fault == op: return { {'list-objects-v2':'Contents','list-object-versions':'DeleteMarkers','list-multipart-uploads':'Uploads'}[op]:[{'Key':prefix+'leftover'}]}
            return {}
        return super().call(service,op,*args)


class V4ProofTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(proof, 'v4 proof helper is missing')
        self.tmp=tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.repo=Path(self.tmp.name)/'repo'; self.repo.mkdir(); (self.repo/'.git').mkdir()
        self.config=fixture_config(); self.config_path=Path(self.tmp.name)/'config.json'; self.config_path.write_bytes(encoded(self.config))
        self.root=proof.canonical_root(self.repo); self.state={'puts':0,'deletes':0}
        self.pub={'published_commit':'a'*40,'config_sha256':digest(self.config_path.read_bytes()),'template_sha256':self.config['template_sha256'],'resources_sha256':digest(encoded(self.config['outputs']))}
        self.aws_path=Path(self.tmp.name)/'aws.exe'; self.aws_path.write_bytes(b'synthetic-cli')
        self.aws_config_path=Path(self.tmp.name)/'sso.ini'; self.aws_config_path.write_bytes(b'synthetic-sso')
        self.publication=dict(repo=self.repo,config_path=self.config_path,published_commit='a'*40,
                              aws_path=self.aws_path,aws_config_path=self.aws_config_path,protocol='docs/contracts/v02.json')

    def execute(self, fault=None, independent_fault=None, *, encryption_rules=None):
        self.api=ProbeApi(self.config,self.root,self.state,fault=fault)
        self.reader=ProbeApi(self.config,self.root,self.state,independent=True,fault=independent_fault or fault)
        self.reader.binary=str(self.aws_path); self.reader.config_file=self.aws_config_path
        if encryption_rules is not None: self.reader.encryption_rules=encryption_rules
        with patch.object(proof,'verify_publication',return_value=self.pub), patch.object(setup,'identity_and_optout',return_value={}), patch.object(setup,'assume_operator',return_value=self.api):
            return proof.run(self.reader,**self.publication)

    def test_exact_default_object_is_deleted_and_independently_verified_once(self):
        result=self.execute()
        self.assertEqual(result['status'],'DIRECT_S3_PROOF_PASS')
        self.assertEqual(result['checks'],{'output_put_no_sse_headers':'PASS','output_head_exact_cmk':'PASS','output_cleanup_objects_versions_multipart':'PASS'})
        self.assertEqual(self.state,{'puts':1,'deletes':1})
        self.assertEqual(json.loads((self.root/'receipt.json').read_bytes()),result)
        with self.assertRaisesRegex(ValueError,'CONSUMED'): self.execute()
        self.assertEqual(self.state['puts'],1)

    def test_known_sse_c_block_preserves_prewrite_evidence_and_allows_one_probe(self):
        for index,blocked in enumerate(({'EncryptionType':'SSE-C'}, {'EncryptionType':['SSE-C']})):
            with self.subTest(blocked=blocked):
                self.repo=Path(self.tmp.name)/str(index)/'repo'; self.repo.mkdir(parents=True); (self.repo/'.git').mkdir()
                self.root=proof.canonical_root(self.repo); self.publication['repo']=self.repo
                previous=self.root/'provider-read-evidence'/'0000.json'; previous.parent.mkdir(parents=True)
                previous.write_bytes(b'{"prewrite_only":true}\n')
                self.state={'puts':0,'deletes':0}
                rule={'ApplyServerSideEncryptionByDefault':{'SSEAlgorithm':'aws:kms','KMSMasterKeyID':self.config['outputs']['OutputKey']},
                      'BucketKeyEnabled':False,'BlockedEncryptionTypes':blocked}
                result=self.execute(encryption_rules=[rule])
                self.assertEqual(result['status'],'DIRECT_S3_PROOF_PASS')
                self.assertEqual(self.state,{'puts':1,'deletes':1})
                self.assertEqual(previous.read_bytes(),b'{"prewrite_only":true}\n')
                self.assertTrue((previous.parent/'0001.json').exists())

    def test_encryption_drift_unknown_fields_and_unblocking_fail_before_reservation(self):
        baseline={'ApplyServerSideEncryptionByDefault':{'SSEAlgorithm':'aws:kms','KMSMasterKeyID':self.config['outputs']['OutputKey']},'BucketKeyEnabled':False}
        invalid=[{**baseline,'BlockedEncryptionTypes':value} for value in
                 (None,{},'SSE-C',{'EncryptionType':'NONE'},{'EncryptionType':['NONE']},{'EncryptionType':[]},
                  {'EncryptionType':['SSE-C','NONE']},{'EncryptionType':['SSE-C','SSE-C']},
                  {'EncryptionType':'SSE-C','unknown':True})]
        invalid.extend(({**baseline,'unknown':True},{**baseline,'BucketKeyEnabled':True},
                        {**baseline,'ApplyServerSideEncryptionByDefault':{'SSEAlgorithm':'AES256','KMSMasterKeyID':self.config['outputs']['OutputKey']}},
                        {**baseline,'ApplyServerSideEncryptionByDefault':{'SSEAlgorithm':'aws:kms','KMSMasterKeyID':'wrong'}}))
        for rules in [[],[baseline,baseline],*([rule] for rule in invalid)]:
            with self.subTest(rules=rules),self.assertRaisesRegex(ValueError,'OUTPUT_DEFAULT_CMK_DRIFT'):
                self.execute(encryption_rules=rules)
            self.assertEqual(self.state['puts'],0)
            self.assertFalse((self.root/'reservation.json').exists())

    def test_uncertain_put_or_wrong_head_consumes_once_and_cleans_up(self):
        for fault in ('uncertain','head'):
            with self.subTest(fault=fault):
                # Separate canonical roots represent separate synthetic test worlds.
                self.repo=Path(self.tmp.name)/fault/'repo'; self.repo.mkdir(parents=True); (self.repo/'.git').mkdir()
                self.root=proof.canonical_root(self.repo); self.publication['repo']=self.repo
                self.state={'puts':0,'deletes':0}
                result=self.execute(fault)
                self.assertEqual(result['status'],'V4_DEFAULT_KMS_PATH_FAILED')
                self.assertEqual(self.state,{'puts':1,'deletes':1})
                with self.assertRaisesRegex(ValueError,'CONSUMED'): self.execute()

    def test_cleanup_denial_or_independent_nonempty_cannot_pass(self):
        for fault,independent in (('delete',None),('head-absence',None),(None,'independent'),
                                  ('after-list-objects-v2',None),('after-list-object-versions',None),('after-list-multipart-uploads',None)):
            with self.subTest(fault=fault,independent=independent):
                self.repo=Path(self.tmp.name)/(fault or independent)/'repo'; self.repo.mkdir(parents=True); (self.repo/'.git').mkdir()
                self.root=proof.canonical_root(self.repo); self.publication['repo']=self.repo
                self.state={'puts':0,'deletes':0}
                result=self.execute(fault,independent)
                self.assertEqual(result['status'],'V4_DEFAULT_KMS_PATH_FAILED')

    def test_stack_identity_defaults_and_nonempty_probes_block_before_put(self):
        for fault in ('UPDATE_IN_PROGRESS','CREATE_COMPLETE','identity','default','versioning','truncated','list-objects-v2','list-object-versions','list-multipart-uploads'):
            with self.subTest(fault=fault),self.assertRaises((ValueError,KeyError)):
                self.execute(fault)
            self.assertEqual(self.state['puts'],0)

    def test_unpublished_change_has_no_aws_calls(self):
        api=ProbeApi(self.config,self.root,self.state)
        with patch.object(proof,'verify_publication',side_effect=ValueError('NOT_PUBLISHED')):
            with self.assertRaisesRegex(ValueError,'NOT_PUBLISHED'): proof.run(api,**self.publication)
        self.assertEqual(api.calls,[])

    def test_actual_client_must_match_published_client_paths(self):
        api=ProbeApi(self.config,self.root,self.state)
        api.binary='foreign-cli'; api.config_file=self.aws_config_path
        with patch.object(proof,'verify_publication',return_value=self.pub),self.assertRaisesRegex(ValueError,'PROOF_CLIENT_BINDING'):
            proof.run(api,**self.publication)
        self.assertEqual(api.calls,[])

    def test_policy_proof_covers_both_principals_and_rejects_live_drift(self):
        api=ResourceApi(self.config)
        result=proof.assess_output_kms(api,self.config)
        for name,logical in (('operator','OperatorRole'),('data_role','DataRole')):
            self.assertEqual(result[name],{'principal_arn':self.config['outputs'][logical],'key_arn':self.config['outputs']['OutputKey'],'actions':['kms:Decrypt','kms:Encrypt','kms:GenerateDataKey'],'identity_policy':True,'key_policy':True})
        bad=ResourceApi(self.config)
        bad.r['OutputKey']['Properties']['KeyPolicy']['Statement'][2]['Action'].remove('kms:Encrypt')
        with self.assertRaisesRegex(ValueError,'KMS_POLICY_DRIFT'): proof.assess_output_kms(bad,self.config)

    def test_orphan_reservation_blocks_without_any_new_provider_call(self):
        self.root.mkdir(parents=True); (self.root/'reservation.json').write_text('{}')
        with self.assertRaisesRegex(ValueError,'CONSUMED'): self.execute()
        self.assertEqual(self.reader.calls,[]); self.assertEqual(self.api.calls,[])

    def test_publication_checks_clean_exact_remote_and_executed_source(self):
        actual=Path(proof.__file__).resolve().parents[2]
        for source in proof.SOURCES:
            destination=self.repo/source; destination.parent.mkdir(parents=True,exist_ok=True)
            destination.write_bytes((actual/source).read_bytes())
        contract={'schema_version':'0.2','all_synthetic':True,'binding':{**self.pub,
                  'aws_client_sha256':digest(self.aws_path.read_bytes()),'aws_config_sha256':digest(self.aws_config_path.read_bytes()),
                  'source_sha256_lf':{source:digest((actual/source).read_bytes().replace(b'\r\n',b'\n')) for source in proof.SOURCES}}}
        protocol_path=self.repo/self.publication['protocol']; protocol_path.parent.mkdir(parents=True,exist_ok=True)
        protocol_path.write_bytes(encoded(contract))
        def git(repo,*args,binary='git'):
            if args == ('status','--porcelain'): return b''
            if args == ('symbolic-ref','--short','HEAD'): return b'chat/alpha-asr-runner-scope\n'
            if args[:1] == ('fetch',): return b''
            if args[:1] == ('rev-parse',): return b'a'*40+b'\n'
            if args[:1] == ('show',):
                source=args[1].split(':',1)[1]
                return protocol_path.read_bytes() if source == self.publication['protocol'] else (actual/source).read_bytes()
            raise AssertionError(args)
        with patch.object(proof.live,'git_output',side_effect=git):
            result=proof.verify_publication(**self.publication)
        self.assertEqual(result['config_sha256'],digest(self.config_path.read_bytes()))
        for target in (self.aws_path,self.aws_config_path,self.config_path):
            before=target.read_bytes(); target.write_bytes(before+b' ')
            with self.subTest(target=target.name),patch.object(proof.live,'git_output',side_effect=git),self.assertRaisesRegex(ValueError,'PUBLICATION_BINDING'):
                proof.verify_publication(**self.publication)
            target.write_bytes(before)
        for fault in ('dirty','remote','branch','source'):
            def bad_git(repo,*args,binary='git'):
                if fault=='dirty' and args == ('status','--porcelain'): return b' M tracked.py'
                if fault=='remote' and args == ('rev-parse','refs/remotes/origin/chat/alpha-asr-runner-scope'): return b'b'*40
                if fault=='branch' and args == ('symbolic-ref','--short','HEAD'): return b'main'
                if fault=='source' and args[:1] == ('show',): return b'other'
                return git(repo,*args,binary=binary)
            with self.subTest(fault=fault),patch.object(proof.live,'git_output',side_effect=bad_git),self.assertRaises(ValueError):
                proof.verify_publication(**self.publication)
        self.config['template_sha256']='0'*64; self.config_path.write_bytes(encoded(self.config))
        with patch.object(proof.live,'git_output',side_effect=git),self.assertRaisesRegex(ValueError,'LOCAL_TEMPLATE_CHANGED'):
            proof.verify_publication(**self.publication)


if __name__=='__main__': unittest.main()
