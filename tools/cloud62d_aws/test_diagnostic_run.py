"""Offline non-speech diagnostic safety regressions; no AWS or owned corpus."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools.cloud62d_aws.test_live_run import FakeApi
from tools.cloud62d_aws import aws_prepare as setup
from tools.cloud62d_owned.corpus import encoded
try:
    from tools.cloud62d_aws import diagnostic_run as diagnostic
except ImportError:
    diagnostic = None


class DiagnosticTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(diagnostic, 'synthetic diagnostic operator missing')
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.repo = Path(self.temp.name) / 'repo'; self.repo.mkdir(); (self.repo/'.git').mkdir()
        actual_repo = Path(diagnostic.__file__).resolve().parents[2]
        for relative in diagnostic.SOURCES:
            target = self.repo/relative; target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes((actual_repo/relative).read_bytes())
        result = self.repo/diagnostic.PRIOR_RESULT_PATH
        result.parent.mkdir(parents=True, exist_ok=True); result.write_bytes(b'{}\n')
        self.config = setup.new_config('123456789012', 'arn:aws:iam::123456789012:role/TestOwner', 'a1b2c3d4')
        self.config.update(write_deadline='2999-01-01T00:00:00+00:00', template_sha256=setup.digest_json(setup.template()))
        self.config['outputs'] = {kind+'Bucket':'dora-62d-123456789012-a1b2c3d4-'+kind.lower() for kind in ('Input','Output')}
        self.config['outputs'].update({kind+'Key':'arn:aws:kms:eu-central-1:123456789012:key/'+digit*8+'-0000-0000-0000-000000000000' for kind,digit in (('Input','1'),('Output','2'))})
        self.config['outputs'].update({kind+'Role':'arn:aws:iam::123456789012:role/dora-62d-a1b2c3d4-'+kind.lower() for kind in ('Data','Operator')})
        self.config['live'] = {'pricing':{'usd_per_second':'0.0001','minimum_billable_seconds':0,'ancillary_tax_upper_usd':'8'}}
        self.paths = {}
        for name, raw in (('config',encoded(self.config)),('aws',b'synthetic-cli'),('aws_config',b'synthetic-sso')):
            self.paths[name+'_path'] = Path(self.temp.name)/(name+'.json'); self.paths[name+'_path'].write_bytes(raw)
        self.paths['preflight_path'] = Path(self.temp.name)/'preflight.json'
        self.paths['preflight_path'].write_bytes(encoded({'status':'SYNTHETIC_DIAGNOSTIC_PREFLIGHT_PASS', 'expires_at':'2999-01-01T00:00:00+00:00', 'config_sha256':diagnostic.digest(encoded(self.config)), 'template_sha256':self.config['template_sha256'], 'checks':{key:'PASS' for key in diagnostic.PREFLIGHT_KEYS}}))
        self.protocol = diagnostic.build_protocol(self.repo, **self.paths)
        self.protocol_path = 'docs/contracts/diagnostic.json'
        (self.repo/self.protocol_path).write_bytes(encoded(self.protocol))
        self.publication = dict(repo=self.repo, protocol=self.protocol_path, published_commit='a'*40, **self.paths)
        self.root = diagnostic.canonical_root(self.repo)
        self.api = FakeApi(self.root)
        class Proof:
            def call(_, *args): return self.api.call(*args)
        self.proof = Proof()

    def runner(self):
        return diagnostic.DiagnosticRunner(self.repo, self.config, self.api, self.proof, self.publication, sleeper=lambda _:None)

    def gate(self):
        return patch.object(diagnostic, 'verify_publication', return_value={'published_commit':'a'*40, 'binding':self.protocol['binding']})

    def test_one_silent_attempt_validates_result_and_cleanup_without_overwriting_history(self):
        with self.gate():
            receipt = self.runner().run_once(1)
            before = {str(p.relative_to(self.root)):p.read_bytes() for p in self.root.rglob('*') if p.is_file() and p.name!='.recorder.lock'}
            again = self.runner().run_once(1)
            with self.assertRaisesRegex(ValueError, 'ALREADY_VERIFIED'): self.runner().run_once(2)
        self.assertTrue(receipt['service_flow_verified'])
        self.assertEqual(receipt['actual_dispatches'],1)
        self.assertEqual(receipt['cleanup_status'],'VERIFIED_VISIBLE_EMPTY')
        self.assertEqual(receipt,again); self.assertEqual(len(self.api.starts),1)
        self.assertTrue(all((self.root/name).read_bytes()==raw for name,raw in before.items()))
        import wave
        with wave.open(str(self.root/'fixtures/diag-silence-01.wav')) as wav:
            self.assertEqual((wav.getframerate(),wav.getnchannels(),wav.getsampwidth(),wav.getnframes()),(16000,1,2,48000))
            self.assertEqual(wav.readframes(48000),b'\0'*96000)

    def test_eight_lifetime_failures_are_reserved_without_automatic_retries(self):
        call = self.api.call
        def denied(service, op, *args):
            if op == 'start-transcription-job':
                self.api.starts.append('denied'); raise setup.AwsError('BadRequestException')
            return call(service,op,*args)
        with patch.object(self.api,'call',side_effect=denied), self.gate():
            for number in range(1,9):
                receipt = self.runner().run_once(number)
                self.assertFalse(receipt['service_flow_verified'])
                self.assertEqual(len(self.api.starts),number)
            with self.assertRaisesRegex(ValueError,'ATTEMPT_NUMBER'): self.runner().run_once(9)
        self.assertEqual(len(list((self.root/'reservations').glob('*.json'))),8)
        self.assertEqual(receipt['reserved_seconds'],24)

    def test_uncertain_or_cleanup_failure_blocks_later_attempt_and_never_restarts(self):
        self.api.uncertain = True
        with self.gate():
            receipt = self.runner().run_once(1)
            self.assertFalse(receipt['service_flow_verified'])
            self.assertEqual(receipt['cleanup_status'],'CLEANUP_BLOCKED')
            self.runner().run_once(1)
            with self.assertRaisesRegex(ValueError,'PRIOR_CLEANUP'): self.runner().run_once(2)
        self.assertEqual(len(self.api.starts),1)

    def test_publication_rejection_prevents_every_api_call_and_reservation(self):
        with patch.object(diagnostic,'verify_publication',side_effect=ValueError('NOT_PUBLISHED_EXACT_COMMIT')):
            with self.assertRaisesRegex(ValueError,'NOT_PUBLISHED'): self.runner().run_once(1)
        self.assertEqual(self.api.calls,[])
        self.assertFalse((self.root/'reservations').exists())

    def test_diagnostic_budget_cannot_spend_unused_ancillary_reserve_on_asr(self):
        self.config['live']['pricing'].update(usd_per_second='0.001',ancillary_tax_upper_usd='0')
        self.paths['config_path'].write_bytes(encoded(self.config))
        with self.assertRaisesRegex(ValueError,'DIAGNOSTIC_ASR_BUDGET'):
            diagnostic.build_protocol(self.repo,**self.paths)

    def test_declared_checkout_cannot_differ_from_actual_executed_sources(self):
        for relative in ('tools/cloud62d_aws/diagnostic_run.py','tools/cloud62d_aws/aws_prepare.py'):
            path=self.repo/relative; original=path.read_bytes(); path.write_bytes(original+b'\n# changed checkout\n')
            with self.assertRaisesRegex(ValueError,'EXECUTED_SOURCE_MISMATCH'):
                diagnostic.build_protocol(self.repo,**self.paths)
            path.write_bytes(original)

    def test_old_success_receipt_cannot_be_reused_for_changed_configuration(self):
        with self.gate(): self.runner().run_once(1)
        self.config['private_revision']='different'
        self.paths['config_path'].write_bytes(encoded(self.config))
        self.protocol['binding']['config_sha256']=diagnostic.digest(encoded(self.config))
        with self.gate(),self.assertRaisesRegex(ValueError,'ATTEMPT_CONFIGURATION_CHANGED'):
            self.runner().run_once(1)
        self.assertEqual(len(self.api.starts),1)

    def test_durable_campaign_anchor_rejects_missing_journal_without_new_start(self):
        with self.gate():
            self.runner().run_once(1)
            # Simulate lost local evidence, never actual private journal mutation.
            import shutil
            shutil.rmtree(self.root)
            with self.assertRaisesRegex(ValueError,'DIAGNOSTIC_JOURNAL_MISSING'): self.runner().run_once(1)
        self.assertEqual(len(self.api.starts),1)

    def test_malformed_result_or_denied_cleanup_never_verifies_service_flow(self):
        call = self.api.call
        def malformed(service,op,*args):
            reply = call(service,op,*args)
            if op=='start-transcription-job':
                request=json.loads(dict(zip(args[::2],args[1::2]))['--cli-input-json'])
                self.api.objects[(request['OutputBucketName'],request['OutputKey'])]=b'{bad-json'
            return reply
        with patch.object(self.api,'call',side_effect=malformed),self.gate():
            receipt=self.runner().run_once(1)
        self.assertFalse(receipt['service_flow_verified'])
        self.assertFalse(receipt['completed_result_retrieved'])
        self.assertEqual(receipt['attempt_status'],'MALFORMED_RESULT')
        self.api.cleanup_denied=True
        with self.gate(): receipt=self.runner().run_once(2)
        self.assertTrue(receipt['completed_result_retrieved'])
        self.assertFalse(receipt['service_flow_verified'])
        self.assertEqual(receipt['cleanup_status'],'CLEANUP_BLOCKED')

    def test_publication_refetches_exact_commit_and_rejects_changed_bound_bytes(self):
        calls=[]
        def git(repo,*args,**kwargs):
            calls.append(args)
            if args[0] in ('status','fetch','merge-base'): return b''
            if args[0]=='symbolic-ref': return b'chat/alpha-asr-runner-scope\n'
            if args[0]=='show': return (self.repo/(diagnostic.PRIOR_RESULT_PATH if args[1].startswith(diagnostic.PRIOR_RESULT_COMMIT) else self.protocol_path)).read_bytes()
            return b'a'*40+b'\n'
        with patch.object(diagnostic.live,'git_output',side_effect=git):
            diagnostic.verify_publication(**self.publication)
            preflight=json.loads(self.paths['preflight_path'].read_bytes())
            preflight['checks']['PREFLIGHT-08']='BLOCKED'
            self.paths['preflight_path'].write_bytes(encoded(preflight))
            (self.repo/self.protocol_path).write_bytes(encoded(diagnostic.build_protocol(self.repo,**self.paths)))
            with self.assertRaisesRegex(ValueError,'PREFLIGHT_NOT_PASS'): diagnostic.verify_publication(**self.publication)
            preflight['checks']['PREFLIGHT-08']='PASS'
            self.paths['preflight_path'].write_bytes(encoded(preflight))
            (self.repo/self.protocol_path).write_bytes(encoded(diagnostic.build_protocol(self.repo,**self.paths)))
            self.paths['aws_path'].write_bytes(b'changed-cli')
            with self.assertRaisesRegex(ValueError,'BINDING'): diagnostic.verify_publication(**self.publication)
        self.assertIn(('fetch','origin','chat/alpha-asr-runner-scope'),calls)
        self.assertIn(('merge-base','--is-ancestor',diagnostic.PRIOR_RESULT_COMMIT,'a'*40),calls)


if __name__ == '__main__': unittest.main()
