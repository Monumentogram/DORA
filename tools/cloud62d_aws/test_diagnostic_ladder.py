"""Offline synthetic ladder boundaries; no credentials, AWS, or owned speech."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools.cloud62d_aws import aws_prepare as setup
from tools.cloud62d_aws.test_live_run import FakeApi
from tools.cloud62d_owned.corpus import digest, encoded
try:
    from tools.cloud62d_aws import diagnostic_ladder as ladder
except ImportError:
    ladder = None


class LadderApi(FakeApi):
    def __init__(self, root, outcome):
        super().__init__(root)
        self.outcome, self.requests, self.managed = outcome, [], {}

    def call(self, service, operation, *args):
        if operation != 'start-transcription-job':
            result=super().call(service, operation, *args)
            if operation=='head-object':
                result.update(ServerSideEncryption='aws:kms',SSEKMSKeyId='arn:aws:kms:eu-central-1:123456789012:key/22222222-0000-0000-0000-000000000000')
            return result
        request = json.loads(args[1]); step = len(self.requests) + 1
        assert (self.root / f'reservations/D{step}.json').is_file()
        assert (self.root / 'calls').is_dir()
        self.requests.append(request); self.starts.append(request['TranscriptionJobName'])
        if self.outcome == 'uncertain':
            raise TimeoutError('response lost')
        if step != self.outcome:
            raise setup.AwsError('BadRequestException', 'Exact private rejection')
        job = {**request, 'TranscriptionJobStatus': 'COMPLETED'}
        wire = encoded({'jobName': request['TranscriptionJobName'], 'status': 'COMPLETED',
                        'results': {'transcripts': [{'transcript': ''}], 'items': []}})
        if 'OutputBucketName' in request:
            self.objects[(request['OutputBucketName'], request['OutputKey'])] = wire
        else:
            url = 'https://s3.eu-central-1.amazonaws.com/aws-transcribe-eu-central-1-prod/123456789012/' + request['TranscriptionJobName'] + '/asrOutput.json?X-Amz-Signature=test'
            job['Transcript'] = {'TranscriptFileUri': url}; self.managed[url] = wire
        self.jobs[request['TranscriptionJobName']] = job
        return {'TranscriptionJob': job}


class LadderTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(ladder, 'prospective diagnostic ladder is missing')
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name)/'repo'; self.repo.mkdir(); (self.repo/'.git').mkdir()
        self.config = setup.new_config('123456789012', 'arn:aws:iam::123456789012:role/Owner', 'a1b2c3d4')
        self.config.update(write_deadline='2999-01-01T00:00:00+00:00', template_sha256=setup.digest_json(setup.template()))
        self.config['outputs'] = {kind+'Bucket': 'dora-62d-123456789012-a1b2c3d4-'+kind.lower() for kind in ('Input','Output')}
        self.config['outputs'].update({kind+'Key': 'arn:aws:kms:eu-central-1:123456789012:key/'+digit*8+'-0000-0000-0000-000000000000' for kind,digit in (('Input','1'),('Output','2'))})
        self.config['outputs'].update({kind+'Role': 'arn:aws:iam::123456789012:role/dora-62d-a1b2c3d4-'+kind.lower() for kind in ('Operator','Data')})
        self.config['live'] = {'pricing': {'usd_per_second':'0.0001','minimum_billable_seconds':0,'ancillary_tax_upper_usd':'8'}}
        self.paths = {}
        for name,raw in (('config',encoded(self.config)),('aws',b'cli'),('aws_config',b'sso')):
            self.paths[name+'_path'] = Path(self.temp.name)/(name+'.json'); self.paths[name+'_path'].write_bytes(raw)
        actual = Path(ladder.__file__).resolve().parents[2]
        for relative in ladder.SOURCES:
            path = self.repo/relative; path.parent.mkdir(parents=True,exist_ok=True); path.write_bytes((actual/relative).read_bytes())
        history=ladder.old.canonical_root(self.repo)/'receipts/01.json'; history.parent.mkdir(parents=True,exist_ok=True)
        history.write_bytes(encoded({'actual_dispatches':1,'attempt_status':'FAILED','cleanup_status':'VERIFIED_VISIBLE_EMPTY','service_flow_verified':False}))
        prior=self.repo/ladder.old.PRIOR_RESULT_PATH; prior.parent.mkdir(parents=True,exist_ok=True); prior.write_bytes((actual/ladder.old.PRIOR_RESULT_PATH).read_bytes())
        self.protocol = ladder.build_protocol(self.repo, **self.paths)
        self.publication = dict(repo=self.repo,protocol='docs/contracts/ladder.json',published_commit='a'*40,**self.paths)
        self.root = ladder.canonical_root(self.repo)
        self.proof = {'published_commit':'a'*40,'protocol_sha256':'b'*64,'binding':self.protocol['binding']}

    def execute(self, outcome):
        api = LadderApi(self.root,outcome)
        runner = ladder.LadderRunner(self.repo,self.config,api,api,self.publication,sleeper=lambda _:None,fetcher=lambda url:api.managed[url])
        with patch.object(ladder,'verify_publication',return_value=self.proof):
            report = runner.run()
        return api,runner,report

    def test_d1_success_stops_and_unlocks_only_after_cleanup(self):
        api,runner,report = self.execute(1)
        self.assertEqual(len(api.starts),1)
        self.assertEqual(report['successful_step'],'D1')
        self.assertTrue(report['human_speech_unlocked'])
        self.assertEqual(report['cleanup_status'],'VERIFIED_VISIBLE_EMPTY')
        req=api.requests[0]
        self.assertEqual(req['OutputEncryptionKMSKeyId'],self.config['outputs']['OutputKey'])
        self.assertEqual(req['JobExecutionSettings'],{'AllowDeferredExecution':False,'DataAccessRoleArn':self.config['outputs']['DataRole']})
        with patch.object(ladder,'verify_publication',return_value=self.proof):
            self.assertEqual(runner.run(),report)
        self.assertEqual(len(api.starts),1)

    def test_d2_success_omits_output_fields_preserves_input_role_and_never_unlocks(self):
        api,_,report = self.execute(2)
        self.assertEqual(report['diagnosis'],'OUTPUT_PATH_SPECIFIC')
        self.assertFalse(report['human_speech_unlocked']); self.assertFalse(report['service_flow_verified'])
        first,second=api.requests
        self.assertEqual(first['Media'],second['Media'])
        self.assertEqual(first['JobExecutionSettings'],second['JobExecutionSettings'])
        for field in ('OutputBucketName','OutputKey','OutputEncryptionKMSKeyId'):
            self.assertNotIn(field,second)

    def test_d3_omits_execution_settings_and_no_fourth_or_replay(self):
        api,runner,report = self.execute(3)
        self.assertEqual(report['diagnosis'],'DATA_ACCESS_ROLE_PATH_SPECIFIC')
        self.assertNotIn('JobExecutionSettings',api.requests[2])
        self.assertEqual(len({json.dumps(r['Media']) for r in api.requests}),1)
        self.assertFalse(report['human_speech_unlocked'])
        with patch.object(ladder,'verify_publication',return_value=self.proof): runner.run()
        self.assertEqual(len(api.starts),3)

    def test_three_failures_are_core_unresolved_and_preserve_private_errors(self):
        api,_,report = self.execute(0)
        self.assertEqual(len(api.starts),3)
        self.assertEqual(report['diagnosis'],'CORE_OR_INPUT_PATH_UNRESOLVED')
        errors=[json.loads(p.read_bytes()) for p in (self.root/'calls').glob('*/error.json')]
        self.assertEqual([e['private_detail'] for e in errors],['Exact private rejection']*3)

    def test_uncertain_submission_consumes_reservation_and_never_advances_or_replays(self):
        api,runner,report = self.execute('uncertain')
        self.assertEqual(len(api.starts),1); self.assertFalse(report['human_speech_unlocked'])
        self.assertEqual(report['diagnosis'],'UNRESOLVED_STOP')
        with patch.object(ladder,'verify_publication',return_value=self.proof): runner.run()
        self.assertEqual(len(api.starts),1)

    def test_publication_rejection_precedes_every_aws_call(self):
        api=LadderApi(self.root,1)
        runner=ladder.LadderRunner(self.repo,self.config,api,api,self.publication)
        with patch.object(ladder,'verify_publication',side_effect=ValueError('NOT_PUBLISHED')):
            with self.assertRaisesRegex(ValueError,'NOT_PUBLISHED'): runner.run()
        self.assertEqual(api.calls,[]); self.assertEqual(api.starts,[])

    def test_managed_url_rejects_foreign_host_region_account_and_job(self):
        name='d62d-a1b2c3d4-ladder-d2'
        valid='https://s3.eu-central-1.amazonaws.com/aws-transcribe-eu-central-1-prod/123456789012/'+name+'/asrOutput.json?X-Amz-Signature=abc'
        self.assertEqual(ladder.validate_managed_url(valid,self.config,name),valid)
        for bad in (valid.replace('https:','http:'),valid.replace('eu-central-1','us-east-1'),valid.replace('123456789012','000000000000'),valid.replace(name,'different'),valid.replace('amazonaws.com','amazonaws.com.evil.test'),valid.replace('/asrOutput.json','/%2e%2e/asrOutput.json')):
            with self.subTest(url=bad),self.assertRaises(ValueError): ladder.validate_managed_url(bad,self.config,name)

    def runtime_proofs(self):
        common={'published_commit':'a'*40,'config_sha256':self.protocol['binding']['config_sha256'],
                'template_sha256':self.config['template_sha256'],'resources_sha256':digest(encoded(self.config['outputs'])),
                'expires_at':'2999-01-01T00:00:00+00:00'}
        preflight={**common,'status':'SYNTHETIC_DIAGNOSTIC_PREFLIGHT_PASS','checks':{key:'PASS' for key in ladder.old.PREFLIGHT_KEYS},
                   'effective_output_kms':{principal:{'principal_arn':self.config['outputs'][role],'key_arn':self.config['outputs']['OutputKey'],
                   'actions':['kms:Decrypt','kms:Encrypt','kms:GenerateDataKey'],'identity_policy':True,'key_policy':True} for principal,role in (('operator','OperatorRole'),('data_role','DataRole'))}}
        direct={**common,'status':'DIRECT_S3_PROOF_PASS','checks':{key:'PASS' for key in ladder.DIRECT_CHECKS}}
        paths=[Path(self.temp.name)/name for name in ('preflight.json','direct.json')]
        for path,value in zip(paths,(preflight,direct)): path.write_bytes(encoded(value))
        return paths,preflight,direct

    def test_runtime_proof_requires_direct_cleanup_freshness_and_both_effective_principals(self):
        paths,preflight,direct=self.runtime_proofs()
        def verify(): return ladder.validate_runtime_proofs(self.config,self.protocol['binding'],*paths,published_commit='a'*40)
        self.assertIn('direct_s3_sha256',verify())
        for principal in ('operator','data_role'):
            preflight['effective_output_kms'][principal]['key_policy']=False; paths[0].write_bytes(encoded(preflight))
            with self.assertRaisesRegex(ValueError,'EFFECTIVE_OUTPUT_KMS_PROOF'): verify()
            preflight['effective_output_kms'][principal]['key_policy']=True
        paths[0].write_bytes(encoded(preflight))
        direct['checks']['output_cleanup_objects_versions_multipart']='BLOCKED'; paths[1].write_bytes(encoded(direct))
        with self.assertRaisesRegex(ValueError,'DIRECT_S3_PROOF_NOT_PASS'): verify()
        direct['checks']['output_cleanup_objects_versions_multipart']='PASS'; direct['expires_at']='2020-01-01T00:00:00+00:00'; paths[1].write_bytes(encoded(direct))
        with self.assertRaisesRegex(ValueError,'RUNTIME_PROOF_EXPIRED'): verify()

    def test_publication_checks_remote_source_bytes_and_clean_tree(self):
        paths,_,_=self.runtime_proofs()
        protocol_path=self.repo/self.publication['protocol']; protocol_path.parent.mkdir(parents=True,exist_ok=True); protocol_path.write_bytes(encoded(self.protocol))
        publication={**self.publication,'preflight_path':paths[0],'direct_s3_path':paths[1]}
        dirty=False; altered=False; calls=[]
        def git(repo,*args,**kwargs):
            calls.append(args)
            if args[0]=='status': return b' M file' if dirty else b''
            if args[0] in ('fetch','merge-base'): return b''
            if args[0]=='symbolic-ref': return b'chat/alpha-asr-runner-scope\n'
            if args[0]=='show':
                relative=args[1].split(':',1)[1]
                return b'changed' if altered and relative.endswith('diagnostic_ladder.py') else (self.repo/relative).read_bytes()
            return b'a'*40+b'\n'
        with patch.object(ladder.live,'git_output',side_effect=git):
            ladder.verify_publication(**publication)
            altered=True
            with self.assertRaisesRegex(ValueError,'PUBLISHED_SOURCE_MISMATCH'): ladder.verify_publication(**publication)
            dirty=True
            with self.assertRaisesRegex(ValueError,'DIRTY_TREE'): ladder.verify_publication(**publication)
        self.assertIn(('fetch','origin','chat/alpha-asr-runner-scope'),calls)

    def test_crashed_reservation_and_missing_journal_block_every_start(self):
        api=LadderApi(self.root,1)
        runner=ladder.LadderRunner(self.repo,self.config,api,api,self.publication)
        with patch.object(ladder,'verify_publication',return_value=self.proof),patch.object(runner,'_upload',side_effect=KeyboardInterrupt):
            with self.assertRaises(KeyboardInterrupt): runner.run()
        with patch.object(ladder,'verify_publication',return_value=self.proof):
            with self.assertRaisesRegex(ValueError,'CAMPAIGN_CONSUMED'): runner.run()
            (self.root/'campaign.json').unlink()
            with self.assertRaisesRegex(ValueError,'DIAGNOSTIC_JOURNAL_MISSING'): runner.run()
        self.assertEqual(api.starts,[])

    def test_malformed_result_stops_without_d2_and_cleanup_failure_never_unlocks(self):
        api=LadderApi(self.root,1)
        original=api.call
        def malformed(service,operation,*args):
            result=original(service,operation,*args)
            if operation=='start-transcription-job':
                request=api.requests[0]; api.objects[(request['OutputBucketName'],request['OutputKey'])]=b'{malformed'
            return result
        runner=ladder.LadderRunner(self.repo,self.config,api,api,self.publication)
        api.cleanup_denied=True
        with patch.object(ladder,'verify_publication',return_value=self.proof),patch.object(api,'call',side_effect=malformed): receipt=runner.run()
        self.assertEqual(len(api.starts),1); self.assertEqual(receipt['diagnosis'],'UNRESOLVED_STOP')
        self.assertFalse(receipt['human_speech_unlocked']); self.assertEqual(receipt['cleanup_status'],'CLEANUP_BLOCKED')

    def test_d1_wrong_output_cmk_stops_without_unlock(self):
        api=LadderApi(self.root,1); original=api.call
        def wrong_cmk(service,operation,*args):
            result=original(service,operation,*args)
            if operation=='head-object': result['SSEKMSKeyId']='foreign'
            return result
        runner=ladder.LadderRunner(self.repo,self.config,api,api,self.publication)
        with patch.object(ladder,'verify_publication',return_value=self.proof),patch.object(api,'call',side_effect=wrong_cmk): receipt=runner.run()
        self.assertFalse(receipt['human_speech_unlocked']); self.assertEqual(len(api.starts),1)

    def test_result_foreign_account_never_unlocks(self):
        api=LadderApi(self.root,1); original=api.call
        def foreign_account(service,operation,*args):
            result=original(service,operation,*args)
            if operation=='start-transcription-job':
                req=api.requests[0]; key=(req['OutputBucketName'],req['OutputKey'])
                wire=json.loads(api.objects[key]); wire['accountId']='000000000000'; api.objects[key]=encoded(wire)
            return result
        runner=ladder.LadderRunner(self.repo,self.config,api,api,self.publication)
        with patch.object(ladder,'verify_publication',return_value=self.proof),patch.object(api,'call',side_effect=foreign_account): receipt=runner.run()
        self.assertFalse(receipt['human_speech_unlocked']); self.assertEqual(len(api.starts),1)

    def malformed_structure(self,transform):
        api=LadderApi(self.root,1); original=api.call
        def malformed(service,operation,*args):
            result=original(service,operation,*args)
            if operation=='start-transcription-job':
                req=api.requests[0]; key=(req['OutputBucketName'],req['OutputKey'])
                api.objects[key]=encoded(transform(json.loads(api.objects[key])))
            return result
        runner=ladder.LadderRunner(self.repo,self.config,api,api,self.publication)
        with patch.object(ladder,'verify_publication',return_value=self.proof),patch.object(api,'call',side_effect=malformed): receipt=runner.run()
        self.assertEqual(receipt['cleanup_status'],'VERIFIED_VISIBLE_EMPTY')
        self.assertEqual(receipt['steps'][0]['status'],'MALFORMED_RESULT')
        self.assertFalse(receipt['human_speech_unlocked']); self.assertEqual(len(api.starts),1)

    def test_json_array_result_is_malformed_and_cleaned(self):
        self.malformed_structure(lambda wire:[])

    def test_json_string_timestamp_item_is_malformed_and_cleaned(self):
        def transform(wire): wire['results']['items']=['bad']; return wire
        self.malformed_structure(transform)

    def test_extreme_timestamp_exponent_is_malformed_and_cleaned(self):
        def transform(wire):
            wire['results']['items']=[{'type':'pronunciation','start_time':'1e999999','end_time':'1e999999','alternatives':[{'content':'word'}]}]
            return wire
        self.malformed_structure(transform)

    def test_job_delete_timeout_preserves_blocked_receipt_and_cleans_terminal_objects(self):
        api=LadderApi(self.root,1); original=api.call
        def timeout(service,operation,*args):
            if operation=='delete-transcription-job': raise TimeoutError('delete response lost')
            return original(service,operation,*args)
        runner=ladder.LadderRunner(self.repo,self.config,api,api,self.publication)
        with patch.object(ladder,'verify_publication',return_value=self.proof),patch.object(api,'call',side_effect=timeout): receipt=runner.run()
        self.assertEqual(receipt['cleanup_status'],'CLEANUP_BLOCKED'); self.assertFalse(receipt['human_speech_unlocked'])
        self.assertEqual(api.objects,{})
        self.assertTrue((self.root/'receipt.json').is_file())


if __name__=='__main__': unittest.main()
