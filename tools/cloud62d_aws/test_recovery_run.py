"""Synthetic recovery campaign checks; never touches AWS or the owned corpus."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools.cloud62d_aws import live_run as live
from tools.cloud62d_aws.test_live_run import FakeApi
from tools.cloud62d_owned.corpus import CorpusStore, EASY_EN_IDS, digest, encoded
from tools.cloud62d_owned.test_corpus import inventory, easy_materials, wav

try:
    from tools.cloud62d_aws import recovery_run as recovery
except ImportError:
    recovery = None


class RecoveryTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(recovery, 'Separate recovery driver is required')
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.base = Path(temporary.name)
        self.repo = self.base / 'repo'; self.repo.mkdir(); (self.repo / '.git').mkdir()
        self.store = CorpusStore(self.base / 'corpus', self.repo)
        self.store.initialize(inventory()); self.store.migrate_reduced_eight()
        self.store.migrate_easy_english(easy_materials(), confirmed=True)
        self.store.attest(confirmed=True); self.store.resume_recording(confirmed=True)
        for index, row in enumerate(self.store.state()['items'], 1):
            self.store.save_capture(row['id'], wav(index), wav(index))
            self.store.verify_reference(row['id'], 'one two three four', confirmed=True)
        self.store.select(); self.store.finalize()
        pricing = {'usd_per_second': '0.0001', 'minimum_billable_seconds': 0,
            'ancillary_tax_upper_usd': '8', 'source_url': 'https://aws.amazon.com/transcribe/pricing/',
            'verified_at': '2026-09-28T00:00:00+00:00'}
        self.plan = live.build_plan(self.store, pricing)
        self.old = CorpusStore(self.base / 'prior', self.repo)
        self.old._write('plan.json', self.plan)
        self.old._write('publication.json', {'published_commit': 'a'*40})
        self.old._write('run-binding.json', {'run_nonce': 'a'*32})
        marker = {'published_commit': 'a'*40, 'manifest_sha256': self.plan['manifest_sha256'],
            'plan_sha256': digest(encoded(self.plan)), 'run_root_sha256': digest(str(self.old.root).encode()),
            'run_nonce': 'a'*32}
        self.store._write('provider-started.json', marker, immutable=True)
        for index, case in enumerate(self.plan['cases'][:4]):
            self.old._write('attempts/'+case['id']+'/evidence.json', {**case, 'status':'FAILED',
                'error_code':'BadRequestException', 'raw':None, 'normalized':None,
                'provider_terminal':None})
            self.old._write('attempts/'+case['id']+'/submit.intent.json', {'utc':'2026-09-28T00:00:00+00:00'})
            self.old._write(f'calls/{index:06}/intent.json', {'case_id':case['id'],
                'service':'transcribe','operation':'start-transcription-job'})
            self.old._write(f'calls/{index:06}/error.json', {'code':'BadRequestException'})
        self.cleanup = self.base / 'prior-cleanup.json'
        self.cleanup.write_bytes(encoded({'status':'VERIFIED_VISIBLE_EMPTY', 'runtime_prefix_empty':True,
            'independent_readbacks':[{'status':'VERIFIED_VISIBLE_EMPTY','evidence_sha256':'b'*64},
                                    {'status':'VERIFIED_VISIBLE_EMPTY','evidence_sha256':'c'*64}]}))
        self.diagnostics = self.base / 'diagnostics.json'
        self.result_path = 'docs/contracts/first-result.json'
        path = self.repo/self.result_path; path.parent.mkdir(parents=True); path.write_bytes(encoded({'bounded8':'INCOMPLETE'}))
        self.root = self.base / 'recovery'
        self.api = FakeApi(self.root)
        self.config = {'account_id':'123456789012','region':'eu-central-1','run_id':'abc12345',
            'job_prefix':'d62d-abc12345-','input_prefix':'input/','output_prefix':'output/',
            'outputs':{'InputBucket':'synthetic-input','OutputBucket':'synthetic-output',
                'InputKey':'synthetic-input-key','OutputKey':'synthetic-output-key','DataRole':'synthetic-role'}}
        self.config['template_sha256'] = 'd'*64
        self.config_path = self.base/'config.json'
        self.config_path.write_bytes(encoded(self.config))
        self.diagnostics.write_bytes(encoded({'schema_version':'0.2', 'all_synthetic':True,
            'maximum_dispatches':3, 'maximum_duration_seconds':3, 'actual_dispatches':1,
            'reserved_seconds':9, 'successful_step':'D1',
            'diagnosis':'DORA_TARGET_SERVICE_FLOW_VERIFIED', 'service_flow_verified':True,
            'human_speech_unlocked':True, 'completed_result_retrieved':True,
            'cleanup_status':'VERIFIED_VISIBLE_EMPTY',
            'config_sha256':digest(self.config_path.read_bytes()), 'template_sha256':'d'*64}))
        self.inputs = dict(prior_root=self.old.root, cleanup_path=self.cleanup, diagnostic_path=self.diagnostics,
            prior_result_commit='b'*40, prior_result_path=self.result_path, config_path=self.config_path)

    def binding(self):
        return recovery.build_recovery_binding(self.store, self.plan, **self.inputs)

    def runner(self, root=None):
        return recovery.RecoveryRunner(self.store, root or self.root, self.config, self.api,
            self.plan, {'synthetic':True}, recovery_inputs=self.inputs, recovery_binding=self.binding(), sleeper=lambda _:None)

    def test_journal_binds_all_bytes_but_ignores_only_process_lock(self):
        first = recovery.journal_digest(self.old.root)
        (self.old.root/'.recorder.lock').write_bytes(b'lock')
        self.assertEqual(first, recovery.journal_digest(self.old.root))
        self.old._write('new-evidence.json', {'value':1})
        self.assertNotEqual(first, recovery.journal_digest(self.old.root))

    def test_predecessor_admission_and_budget_reject_results_unknown_or_changed_corpus(self):
        binding = self.binding()
        self.assertEqual(binding['prior_failed_starts'], 4)
        self.assertEqual(binding['prior_reserved_seconds'], 80)
        self.assertEqual(binding['diagnostic_reserved_seconds'], 24)
        self.assertEqual(binding['max_diagnostic_dispatches'], 4)
        self.assertEqual(binding['maximum_new_diagnostic_dispatches'], 3)
        self.assertEqual(binding['total_reserved_seconds'], self.plan['budget']['reserved_seconds']+104)
        case = self.plan['cases'][0]['id']; evidence = 'attempts/'+case+'/evidence.json'
        original = json.loads(self.old._path(evidence).read_bytes())
        for change in ({'status':'SUCCEEDED'}, {'provider_terminal':{'TranscriptionJobStatus':'FAILED'}},
                       {'result_sha256':'d'*64}, {'error_code':'CLI_FAILED_REDACTED'}):
            self.old._write(evidence, {**original, **change})
            with self.subTest(change=change), self.assertRaises(ValueError): self.binding()
        self.old._write(evidence, original)
        self.old._write('calls/000000/response.json', {'value':{}})
        with self.assertRaisesRegex(ValueError, 'PRIOR_ACCEPTED_OR_UNCERTAIN_START'): self.binding()

    def test_first_primary_failure_halts_permanently_and_preserves_prior_marker(self):
        marker = self.store._path('provider-started.json').read_bytes()
        journal = recovery.journal_digest(self.old.root)
        original = self.api.call
        def fail(service, operation, *args):
            if operation == 'start-transcription-job':
                self.api.starts.append('failed')
                raise live.AwsError('BadRequestException')
            return original(service, operation, *args)
        with patch.object(recovery, 'verify_recovery_publication', return_value={'published_commit':'c'*40}), patch.object(self.api, 'call', side_effect=fail):
            report = self.runner().run()
            self.runner().run()
            with self.assertRaisesRegex(ValueError, 'RECOVERY_CANONICAL|RECOVERY_EVIDENCE'):
                self.runner(self.base/'fresh').run()
        self.assertEqual(len(self.api.starts), 1)
        self.assertEqual([r['status'] for r in report['records'][:8]], ['FAILED']+['NOT_RUN']*7)
        self.assertEqual(report['recovery']['prior_failed_starts'], 4)
        self.assertEqual(report['recovery']['all_campaign_primary_starts'], 5)
        self.assertEqual(report['budget'].get('all_campaign_reserved_seconds'), self.plan['budget']['reserved_seconds']+104)
        self.assertEqual(self.store._path('provider-started.json').read_bytes(), marker)
        self.assertEqual(recovery.journal_digest(self.old.root), journal)

    def test_publication_rejection_never_creates_recovery_marker_or_calls_aws(self):
        with patch.object(recovery, 'verify_recovery_publication', side_effect=ValueError('RECOVERY_NOT_PUBLISHED')):
            with self.assertRaisesRegex(ValueError, 'RECOVERY_NOT_PUBLISHED'): self.runner().run()
        self.assertFalse(self.api.calls)
        self.assertFalse(self.store._path('provider-recovery-started-v03.json').exists())

    def test_eight_recovery_primaries_never_repeat_on_resume(self):
        with patch.object(recovery, 'verify_recovery_publication', return_value={'published_commit':'c'*40}):
            first = self.runner().run()
            second = self.runner().run()
        self.assertEqual(first['attempt_accounting']['primary_start_dispatches'], 8)
        self.assertEqual(second['attempt_accounting']['start_dispatches'], 20)
        self.assertEqual(first['recovery']['all_campaign_primary_starts'], 12)
        self.assertEqual(len(self.api.starts), 20)
        self.assertEqual(first['quality']['languages']['ru']['normalized']['reference_tokens'], 16)

    def test_custom_publication_binds_previous_result_ancestry_and_driver(self):
        binding = self.binding()
        phase = {'recovery_binding':copy.deepcopy(binding)}
        publication = {'repo':self.repo,'phase_a':'docs/contracts/recovery.json','git_binary':'git'}
        calls = []
        def git(repo, *args, **kwargs):
            calls.append(args)
            if args[0] == 'merge-base': return b''
            if args[-1].endswith('recovery.json'): return encoded(phase)
            return (self.repo/self.result_path).read_bytes()
        with patch.object(live,'verify_publication',return_value={'published_commit':'c'*40,
                'config_sha256':digest(self.config_path.read_bytes())}), patch.object(live,'git_output',side_effect=git):
            verified = recovery.verify_recovery_publication(publication,binding)
            self.assertEqual(verified['recovery_binding']['prior_failed_starts'],4)
            self.assertIn(('merge-base','--is-ancestor','a'*40,'b'*40), calls)
            self.assertIn(('merge-base','--is-ancestor','b'*40,'c'*40), calls)
            phase['recovery_binding']['prior_run_journal_sha256'] = '0'*64
            with self.assertRaisesRegex(ValueError,'RECOVERY_PUBLICATION_BINDING'):
                recovery.verify_recovery_publication(publication,binding)
            phase['recovery_binding'] = copy.deepcopy(binding)
            (self.repo/self.result_path).write_bytes(encoded({'changed':True}))
            with self.assertRaisesRegex(ValueError,'PRIOR_RESULT_NOT_PUBLISHED'):
                recovery.verify_recovery_publication(publication,binding)

    def test_verified_cleanup_does_not_require_deleted_local_audio(self):
        runner = self.runner()
        original = self.api.call
        def fail(service, operation, *args):
            if operation == 'start-transcription-job': raise live.AwsError('BadRequestException')
            return original(service,operation,*args)
        with patch.object(recovery,'verify_recovery_publication',return_value={'published_commit':'c'*40}), patch.object(self.api,'call',side_effect=fail):
            runner.run()
            self.store._path(self.plan['cases'][0]['path']).unlink()
            cleaned = runner.cleanup_verified()
        self.assertEqual(cleaned['cleanup']['status'],'VERIFIED_VISIBLE_EMPTY')
        self.assertFalse(self.api.objects)

    def test_recovery_requires_successful_live_synthetic_service_flow(self):
        baseline = json.loads(self.diagnostics.read_bytes())
        for change in ({'actual_dispatches':0}, {'service_flow_verified':False}, {'actual_dispatches':9}):
            self.diagnostics.write_bytes(encoded({**baseline,**change}))
            with self.subTest(change=change), self.assertRaisesRegex(ValueError,'DIAGNOSTIC_RESERVATION_UNVERIFIED'):
                self.binding()

    def test_only_d1_exact_target_configuration_can_unlock_recovery(self):
        baseline = json.loads(self.diagnostics.read_bytes())
        self.binding()
        for change in ({'successful_step':'D2'}, {'successful_step':'D3'},
                       {'diagnosis':'OUTPUT_PATH_SPECIFIC'}, {'human_speech_unlocked':False},
                       {'actual_dispatches':2}, {'actual_dispatches':3},
                       {'completed_result_retrieved':False}, {'schema_version':'1.0'},
                       {'maximum_dispatches':8}, {'actual_dispatches':True},
                       {'reserved_seconds':24}, {'config_sha256':'0'*64},
                       {'template_sha256':'0'*64}):
            self.diagnostics.write_bytes(encoded({**baseline, **change}))
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.binding()
        self.diagnostics.write_bytes(encoded(baseline))
        self.config_path.write_bytes(encoded({**self.config, 'changed':True}))
        with self.assertRaisesRegex(ValueError,'DIAGNOSTIC_TARGET_CONFIG_BINDING'):
            self.binding()

    def test_diagnostic_config_must_match_published_and_executed_recovery_config(self):
        binding = self.binding()
        with patch.object(live, 'verify_publication', return_value={
                'published_commit':'c'*40, 'config_sha256':'0'*64}):
            with self.assertRaisesRegex(ValueError,'DIAGNOSTIC_TARGET_CONFIG_BINDING'):
                recovery.verify_recovery_publication({}, binding)
        runner = self.runner()
        runner.config = {**self.config, 'changed':True}
        with self.assertRaisesRegex(ValueError,'RECOVERY_RUNTIME_CONFIG_CHANGED'):
            runner.run()
        self.assertFalse(self.api.calls)
        self.assertFalse(self.store._path(recovery.MARKER).exists())


if __name__ == '__main__':
    unittest.main()
