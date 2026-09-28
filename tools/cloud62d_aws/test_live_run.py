"""Synthetic-only live operator tests: no AWS, login, microphone or private corpus."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from tools.cloud62d_owned.corpus import CorpusStore, digest, encoded
from tools.cloud62d_owned.test_corpus import inventory, easy_materials, wav
try:
    from tools.cloud62d_aws import live_run as live
except ImportError:
    live = None

PRICING = {'usd_per_second': '0.0004', 'minimum_billable_seconds': 15,
           'ancillary_tax_upper_usd': '8', 'source_url': 'https://aws.amazon.com/transcribe/pricing/',
           'verified_at': '2026-09-29T00:00:00+00:00'}


class FakeApi:
    def __init__(self, root):
        self.root, self.objects, self.jobs, self.starts = root, {}, {}, []
        self.uncertain = False
        self.cleanup_denied = False
        self.calls = []

    def call(self, service, operation, *args):
        self.calls.append((service, operation))
        # The externally visible boundary must already have a durable intent.
        assert list((self.root / 'calls').glob('*/intent.json'))
        values = dict(zip(args[::2], args[1::2]))
        if service == 's3api':
            key = (values.get('--bucket'), values.get('--key'))
            if operation == 'put-object':
                self.objects[key] = Path(values['--body']).read_bytes()
                return {'ETag': 'synthetic'}
            if operation == 'head-object':
                if key not in self.objects:
                    raise live.AwsError('NoSuchKey')
                return {'ContentLength': len(self.objects[key]), 'Metadata': {'sha256': digest(self.objects[key])}}
            if operation == 'get-object':
                Path(args[-1]).write_bytes(self.objects[key])
                return {'ContentLength': len(self.objects[key])}
            if operation == 'delete-object':
                if self.cleanup_denied:
                    raise live.AwsError('AccessDenied')
                self.objects.pop(key, None)
                return {}
            if operation in ('list-object-versions', 'list-objects-v2'):
                rows = [{'Key': k, 'VersionId': 'null'} for b, k in self.objects if b == values['--bucket']]
                return {'Versions' if operation == 'list-object-versions' else 'Contents': rows}
            if operation == 'list-multipart-uploads':
                return {}
        if service == 'transcribe':
            if operation == 'start-transcription-job':
                request = json.loads(values['--cli-input-json'])
                name = request['TranscriptionJobName']
                self.starts.append(name)
                if name in self.jobs:
                    raise live.AwsError('ConflictException')
                job = {**request, 'TranscriptionJobStatus': 'COMPLETED'}
                self.jobs[name] = job
                wire = {'jobName': name, 'status': 'COMPLETED', 'results': {
                    'transcripts': [{'transcript': 'one two three four'}],
                    'items': [{'type': 'pronunciation', 'start_time': '0.1', 'end_time': '0.5',
                               'alternatives': [{'content': 'one'}]}]}}
                self.objects[(request['OutputBucketName'], request['OutputKey'])] = encoded(wire)
                if self.uncertain:
                    cli_error = self.uncertain == 'cli'
                    self.uncertain = False
                    if cli_error: raise live.AwsError('CLI_FAILED_REDACTED', 'synthetic response lost')
                    raise TimeoutError('synthetic transport lost after accepted submit')
                return {'TranscriptionJob': job}
            if operation == 'get-transcription-job':
                name = values['--transcription-job-name']
                if name not in self.jobs:
                    raise live.AwsError('BadRequestException')
                return {'TranscriptionJob': self.jobs[name]}
            if operation == 'delete-transcription-job':
                self.jobs.pop(values['--transcription-job-name'], None)
                return {}
            if operation == 'list-transcription-jobs':
                return {'TranscriptionJobSummaries': list(self.jobs.values())}
        raise AssertionError((service, operation, args))


class LiveTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(live, 'bounded live operator missing')
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        base = Path(self.temp.name)
        self.repo = base / 'repo'; self.repo.mkdir(); (self.repo / '.git').mkdir()
        self.store = CorpusStore(base / 'corpus', self.repo)
        self.store.initialize(inventory()); self.store.migrate_reduced_eight()
        self.store.migrate_easy_english(easy_materials(), confirmed=True)
        self.store.attest(confirmed=True); self.store.resume_recording(confirmed=True)
        for n, row in enumerate(self.store.state()['items'], 1):
            self.store.save_capture(row['id'], wav(n), wav(n))
            self.store.verify_reference(row['id'], 'one two three four', confirmed=True)
        self.store.select(); self.store.finalize()
        self.root = base / 'run'
        self.api = FakeApi(self.root)
        self.config = {'account_id': '123456789012', 'region': 'eu-central-1', 'run_id': 'abc12345',
            'job_prefix': 'd62d-abc12345-', 'input_prefix': 'input/', 'output_prefix': 'output/',
            'outputs': {'InputBucket': 'synthetic-input', 'OutputBucket': 'synthetic-output',
                'InputKey': 'synthetic-input-key', 'OutputKey': 'synthetic-output-key', 'DataRole': 'synthetic-role'},
            'live': {'pricing': PRICING}}
        self.plan = live.build_plan(self.store, PRICING, technical=())
        self.publication = {'synthetic': True}

    def runner(self):
        return live.Runner(self.store, self.root, self.config, self.api, self.plan, self.publication,
                           sleeper=lambda _: None)

    def test_all_eight_serial_once_scores_only_primaries_and_cleans_after_durable_evidence(self):
        with patch.object(live, 'verify_publication', return_value={'published_commit': 'a'*40}):
            report = self.runner().run()
            rerun = self.runner().run()
        self.assertEqual(len(self.api.starts), 8)
        self.assertEqual(report['bounded8'], 'PASS')
        self.assertEqual(report['quality']['languages']['ru']['normalized']['reference_tokens'], 16)
        self.assertEqual(report['quality']['languages']['en']['speech_classes']['spontaneous']['bounded_quality'], 'NOT_EVALUATED')
        self.assertEqual(report['timestamp_accuracy'], 'NOT_EVALUATED')
        self.assertEqual(report['latency']['ru']['sample_status'], 'SMALL_SAMPLE_DESCRIPTIVE_ONLY')
        self.assertEqual(report['cleanup']['status'], 'VERIFIED_VISIBLE_EMPTY')
        self.assertFalse(self.api.objects); self.assertFalse(self.api.jobs)
        self.assertEqual(rerun['bounded8'], 'PASS')

    def test_publication_barrier_prevents_any_aws_call_or_corpus_marker(self):
        with patch.object(live, 'verify_publication', side_effect=ValueError('NOT_PUBLISHED_EXACT_COMMIT')):
            with self.assertRaisesRegex(ValueError, 'NOT_PUBLISHED'):
                self.runner().run()
        self.assertFalse(self.api.starts)
        self.assertFalse(self.store._path('provider-started.json').exists())

    def test_fresh_run_directory_cannot_reset_primary_dispatch_accounting(self):
        with patch.object(live, 'verify_publication', return_value={'published_commit': 'a'*40}):
            self.runner().run()
            other = live.Runner(self.store, self.root.parent/'other-run', self.config, self.api,
                                self.plan, self.publication, sleeper=lambda _: None)
            with self.assertRaisesRegex(ValueError, 'CANONICAL_RUN_BINDING|RUN_EVIDENCE_MISSING'):
                other.run()
        self.assertEqual(len(self.api.starts), 8)

    def test_rejected_resume_never_cleans_up_using_unverified_config(self):
        with patch.object(live, 'verify_publication', return_value={'published_commit': 'a'*40}):
            self.runner().run()
        before = len(self.api.calls)
        with patch.object(live, 'verify_publication', side_effect=ValueError('PUBLICATION_BINDING_MISMATCH')):
            with self.assertRaisesRegex(ValueError, 'PUBLICATION_BINDING_MISMATCH'):
                self.runner().run()
        self.assertEqual(len(self.api.calls), before)

    def test_unknown_cli_start_error_is_uncertain_and_durable_without_dispatch_retry(self):
        self.api.uncertain = 'cli'
        with patch.object(live, 'verify_publication', return_value={'published_commit': 'a'*40}):
            with self.assertRaisesRegex(ValueError, 'UNCERTAIN_SUBMISSION'):
                self.runner().run()
            interrupted = json.loads((self.root/'report-latest.json').read_bytes())
            self.assertEqual(interrupted['records'][0]['status'], 'UNRESOLVED')
            self.assertEqual(interrupted['cleanup']['status'], 'CLEANUP_BLOCKED')
            resumed = self.runner().run()
        self.assertEqual(len(self.api.starts), 8)
        self.assertEqual(resumed['records'][0]['status'], 'UNCERTAIN_SUBMISSION_RECONCILED')

    def test_uncertain_submission_reconciles_same_job_without_resubmission(self):
        self.api.uncertain = True
        with patch.object(live, 'verify_publication', return_value={'published_commit': 'a'*40}):
            with self.assertRaisesRegex(ValueError, 'UNCERTAIN_SUBMISSION'):
                self.runner().run()
            report = self.runner().run()
        self.assertEqual(len(self.api.starts), 8)
        self.assertEqual(report['quality']['languages']['ru']['succeeded_records'], 3)
        self.assertEqual(report['bounded8'], 'INCOMPLETE')
        self.assertEqual(report['records'][0]['status'], 'UNCERTAIN_SUBMISSION_RECONCILED')

    def test_cleanup_failure_stops_new_submissions_and_keeps_all_eight_accounted(self):
        self.api.cleanup_denied = True
        with patch.object(live, 'verify_publication', return_value={'published_commit': 'a'*40}):
            report = self.runner().run()
        self.assertEqual(len(self.api.starts), 1)
        self.assertEqual(len(report['records']), 8)
        self.assertEqual(report['bounded8'], 'INCOMPLETE')
        self.assertEqual(report['cleanup']['status'], 'CLEANUP_BLOCKED')

    def test_reservations_include_minimum_and_reject_overspend(self):
        planned = live.build_plan(self.store, PRICING, technical=('empty', 'near_empty', 'missing_s3'))
        self.assertEqual(planned['budget']['reserved_seconds'], 790)
        bad = {**PRICING, 'usd_per_second': '1'}
        with self.assertRaisesRegex(ValueError, 'ASR_RESERVATION_BOUND'):
            live.build_plan(self.store, bad, technical=())

    def test_changed_corpus_is_rejected_before_speech(self):
        self.store._path('audio/en-read-04.wav').write_bytes(wav(42))
        with patch.object(live, 'verify_publication', return_value={'published_commit': 'a'*40}):
            with self.assertRaisesRegex(ValueError, 'AUDIO_HASH_MISMATCH'):
                self.runner().run()
        self.assertEqual(self.api.starts, [])

    def test_technical_composite_is_excluded_from_quality_denominators(self):
        self.plan = live.build_plan(self.store, PRICING, technical=('ru_300',))
        with patch.object(live, 'verify_publication', return_value={'published_commit': 'a'*40}):
            report = self.runner().run()
        self.assertEqual(len(self.api.starts), 9)
        self.assertEqual(len(report['records']), 9)
        self.assertEqual(report['quality']['languages']['ru']['normalized']['reference_tokens'], 16)

    def test_nonempty_composite_without_pronunciation_items_cannot_pass(self):
        self.plan = live.build_plan(self.store, PRICING, technical=('ru_300',))
        real_call = self.api.call
        def missing_words(service, operation, *args):
            result = real_call(service, operation, *args)
            if operation == 'start-transcription-job':
                request = json.loads(dict(zip(args[::2], args[1::2]))['--cli-input-json'])
                if request['TranscriptionJobName'].endswith('tech-ru_300'):
                    key = (request['OutputBucketName'], request['OutputKey'])
                    wire = json.loads(self.api.objects[key]); wire['results']['items'] = []
                    self.api.objects[key] = encoded(wire)
            return result
        with patch.object(self.api, 'call', side_effect=missing_words), patch.object(live, 'verify_publication', return_value={'published_commit': 'a'*40}):
            report = self.runner().run()
        self.assertEqual(report['records'][-1]['status'], 'MALFORMED_RESULT')
        self.assertEqual(report['technical_cases'][-1]['outcome'], 'FAIL')
        self.assertEqual(report['bounded8'], 'FAIL')
        self.assertEqual(report['quality']['languages']['ru']['expected_records'], 4)

    def test_publication_independently_refetches_exact_branch_and_binds_actual_private_bytes(self):
        files = {}
        for name, value in [('config', b'private config'), ('aws', b'client'), ('aws_config', b'sso config'),
                            ('plan', encoded(self.plan)), ('preflight', encoded({'status': 'BOUNDED8_PREFLIGHT_PASS',
                                'bounded8_data_authority': 'PASS', 'broad_dataset_coverage': 'NOT_SATISFIED',
                                'aws_speech_calls': 0, 'expires_at': '2999-01-01T00:00:00+00:00',
                                'internal_alpha_checks': {f'OD-11C-26-{i:02}': 'PASS' for i in range(1, 13)},
                                'checks': {f'PREFLIGHT-{i:02}': 'PASS' for i in range(1, 13)}}))]:
            files[name] = Path(self.temp.name) / (name + '.json'); files[name].write_bytes(value)
        binding = {'scope': 'BOUNDED_EIGHT_CLIP_LIVE_EVALUATION', 'manifest_sha256': self.plan['manifest_sha256'],
            'config_sha256': digest(files['config'].read_bytes()), 'aws_client_sha256': digest(files['aws'].read_bytes()),
            'aws_config_sha256': digest(files['aws_config'].read_bytes()), 'plan_sha256': digest(files['plan'].read_bytes()),
            'preflight_sha256': digest(files['preflight'].read_bytes()),
            'operator_sha256': digest(Path(live.__file__).read_bytes().replace(b'\r\n', b'\n')),
            'aws_speech_calls_before_publication': 0, 'bounded8_data_authority': 'PASS',
            'broad_dataset_coverage': 'NOT_SATISFIED', 'thresholds': {'ru': 20, 'en': 18},
            'timestamp_accuracy': 'NOT_EVALUATED', 'noise_robustness': 'NOT_EVALUATED',
            'en_spontaneous': 'NOT_EVALUATED', 'provider_admission': 'NOT_ESTABLISHED'}
        phase = 'docs/contracts/bounded.json'
        target = self.repo / phase; target.parent.mkdir(parents=True); target.write_bytes(encoded({'live_binding': binding}))
        calls = []
        def git(repo, *args, **kwargs):
            calls.append(args)
            if args[0] in ('status', 'fetch'): return b''
            if args[0] == 'symbolic-ref': return b'chat/alpha-asr-runner-scope\n'
            if args[0] == 'show': return target.read_bytes()
            return ('a'*40+'\n').encode()
        args = dict(repo=self.repo, phase_a=phase, published_commit='a'*40, config_path=files['config'],
            aws_path=files['aws'], aws_config_path=files['aws_config'], preflight_path=files['preflight'], plan_path=files['plan'])
        with patch.object(live, 'git_output', side_effect=git):
            self.assertEqual(live.verify_publication(**args)['remote_head'], 'a'*40)
            original_proof = json.loads(files['preflight'].read_bytes())
            # Even a manually published, correctly hash-bound document must not
            # bypass the independent internal-Alpha first-audio checklist.
            for mutation in ('missing', 'blocked', 'extra'):
                changed_proof = copy.deepcopy(original_proof)
                if mutation == 'missing': del changed_proof['internal_alpha_checks']
                if mutation == 'blocked': changed_proof['internal_alpha_checks']['OD-11C-26-02'] = 'NOT_RUN'
                if mutation == 'extra': changed_proof['internal_alpha_checks']['OD-11C-26-13'] = 'PASS'
                files['preflight'].write_bytes(encoded(changed_proof))
                binding['preflight_sha256'] = digest(files['preflight'].read_bytes())
                target.write_bytes(encoded({'live_binding': binding}))
                with self.subTest(internal_alpha=mutation), self.assertRaisesRegex(ValueError, 'LIVE_PREFLIGHT_NOT_PASS'):
                    live.verify_publication(**args)
            files['preflight'].write_bytes(encoded(original_proof))
            binding['preflight_sha256'] = digest(files['preflight'].read_bytes())
            target.write_bytes(encoded({'live_binding': binding}))
            files['config'].write_bytes(b'changed')
            with self.assertRaisesRegex(ValueError, 'PUBLICATION_BINDING_MISMATCH'):
                live.verify_publication(**args)
        self.assertIn(('fetch', 'origin', 'chat/alpha-asr-runner-scope'), calls)


if __name__ == '__main__':
    unittest.main()
