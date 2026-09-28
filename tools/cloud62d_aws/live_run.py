"""Bounded eight-clip operator. Private evidence only; no production admission.

Plan is offline. Run/resume require independently verified prospective Git publication.
No automatic Start retry: a lost submission is reconciled under the SAME job name,
retained as an uncertain primary, and never turned into a quality success.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
from decimal import Decimal
from fractions import Fraction
import hashlib
import json
import math
from pathlib import Path
import re
import secrets
import subprocess
import sys
import time
import types

from tools.cloud62d_owned.corpus import CorpusStore, EASY_EN_PROTOCOL, EASY_EN_IDS, digest, encoded, require, utc_now
from tools.cloud62d_owned.server import _ProcessLock
from tools.cloud62d_prepare import bounded_report
from tools.cloud62d_aws.aws_prepare import AwsError

REGION = 'eu-central-1'
HARNESS = 'docs/stage0/DORA_CLOUD_EVALUATION_HARNESS_V0_1.md'
HARNESS_SHA = '6e99e08561534cde5b6bc69a40fd3e8986d763c00456e7fdb32bf3346cceabd1'
TECHNICAL = ('empty', 'near_empty', 'malformed', 'truncated', 'wrong_format', 'missing_s3',
             'duplicate_name', 'ru_300', 'ru_600', 'en_300', 'en_600')
MAX_RESULT_BYTES = 5_000_000


def load_harness(repo):
    source = re.findall(r'```python\n(.*?)```', (Path(repo) / HARNESS).read_text(encoding='utf-8'), re.S)[0]
    require(digest(source.encode()) == HARNESS_SHA, 'FROZEN_HARNESS_CHANGED')
    name = '_dora_frozen_cloud62d_harness'
    module = types.ModuleType(name)
    sys.modules[name] = module
    exec(compile(source, HARNESS, 'exec'), module.__dict__)
    return module


def build_plan(store, pricing, *, technical=TECHNICAL):
    """Revalidate all original/final/ref/composite bytes; produce PRIVATE frozen plan."""
    summary = store.validate_manifest()
    state = store._load()
    require(state.get('protocol_version') == EASY_EN_PROTOCOL, 'EASY_EN_EIGHT_PROTOCOL_REQUIRED')
    require(tuple(sum((state['selection']['quality'][lang] for lang in ('ru', 'en')), [])) == EASY_EN_IDS,
            'PRIMARY_ORDER_MISMATCH')
    require(type(technical) in (tuple, list) and len(set(technical)) == len(technical) and
            set(technical) <= set(TECHNICAL), 'TECHNICAL_SELECTION')
    rate = Decimal(pricing['usd_per_second']); ancillary = Decimal(pricing['ancillary_tax_upper_usd'])
    minimum = pricing['minimum_billable_seconds']
    require(rate.is_finite() and rate > 0 and type(minimum) is int and 0 <= minimum <= 60 and
            ancillary.is_finite() and 0 <= ancillary <= 8, 'PRICING_INVALID')
    require(str(pricing['source_url']).startswith('https://') and pricing['verified_at'], 'VERIFIED_PRICING_REQUIRED')
    manifest = json.loads(store._path(store._artifact(state, 'manifest.json')).read_bytes())
    cases = []
    for case in EASY_EN_IDS:
        record = state['records'][case]
        cases.append({'id': case, 'primary': True, 'language': case[:2],
            'speech_class': record['speech_class'], 'duration_us': record['recording']['duration_us'],
            'path': record['recording']['upload_path'], 'source_sha256': record['recording']['source_sha256'],
            'uploaded_wav_sha256': record['recording']['uploaded_wav_sha256'],
            'reference_raw_sha256': record['reference']['raw_reference_sha256'],
            'reference_normalized_sha256': record['reference']['normalized_reference_sha256']})
    for case in technical:
        duration = {'empty': 0, 'near_empty': 250000, 'malformed': 600000000,
                    'truncated': 3000000, 'wrong_format': 3000000, 'missing_s3': 600000000,
                    'duplicate_name': 3000000}.get(case)
        row = {'id': 'tech-' + case, 'primary': False, 'language': case[:2] if case[:2] in ('ru', 'en') else 'en',
               'speech_class': 'TECHNICAL_ONLY', 'fixture': case, 'duration_us': duration}
        if case.endswith(('_300', '_600')):
            composite = next(c for c in manifest['composites'] if c['language'] == case[:2] and
                             (c['duration_us'] == 300000000) == case.endswith('_300'))
            row.update(path=composite['path'], duration_us=composite['duration_us'], uploaded_wav_sha256=composite['sha256'])
        cases.append(row)
    seconds = sum(max(minimum, math.ceil(c['duration_us'] / 1_000_000)) +
                  (600 if c.get('fixture') == 'duplicate_name' else 0) for c in cases)
    asr = rate * seconds
    require(asr <= 2, 'ASR_RESERVATION_BOUND')
    require(asr + ancillary <= 10, 'TOTAL_BUDGET_BOUND')
    return {'schema_version': '1.0', 'scope': 'BOUNDED_EIGHT_CLIP_LIVE_EVALUATION',
            'manifest_sha256': summary['manifest_sha256'], 'protocol': EASY_EN_PROTOCOL, 'cases': cases,
            'pricing': pricing, 'budget': {'reserved_seconds': seconds, 'asr_upper_usd': str(asr),
                                         'ancillary_tax_upper_usd': str(ancillary), 'total_upper_usd': str(asr+ancillary)},
            'serial': True, 'automatic_retries': 0, 'poll_seconds': 2, 'job_timeout_seconds': 1800,
            'maximum_api_calls': 20000, 'maximum_result_bytes': MAX_RESULT_BYTES,
            'maximum_start_dispatches': sum(2 if c.get('fixture') == 'duplicate_name' else 1 for c in cases),
            'maximum_upload_bytes': 65 * 1024 * 1024, 'maximum_download_bytes': 100 * 1024 * 1024,
            'throttle': 'DOCUMENTED_NOT_INDUCED', 'permission_failure': 'NOT_RUN_NO_ISOLATED_DENIAL_FIXTURE',
            'thresholds': {'ru': 20, 'en': 18}, 'timestamp_accuracy': 'NOT_EVALUATED',
            'noise_robustness': 'NOT_EVALUATED', 'en_spontaneous': 'NOT_EVALUATED',
            'provider_admission': 'NOT_ESTABLISHED'}


def git_output(repo, *args, binary='git'):
    return subprocess.run([binary, '-C', str(repo), *args], check=True, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, timeout=120).stdout


def verify_publication(*, repo, phase_a, published_commit, config_path, aws_path, aws_config_path,
                       preflight_path, plan_path, allow_expired=False, git_binary='git'):
    """Independently fetch/compare the branch and bytes, never trust a passed boolean."""
    repo = Path(repo).resolve()
    require(re.fullmatch('[0-9a-f]{40}', published_commit), 'PUBLICATION_COMMIT')
    git = lambda *args: git_output(repo, *args, binary=git_binary)
    require(not git('status', '--porcelain').strip(), 'DIRTY_TREE')
    branch = git('symbolic-ref', '--short', 'HEAD').decode().strip()
    require(branch == 'chat/alpha-asr-runner-scope', 'WRONG_BRANCH')
    git('fetch', 'origin', branch)
    local = git('rev-parse', 'HEAD').decode().strip()
    remote = git('rev-parse', 'refs/remotes/origin/' + branch).decode().strip()
    require(local == remote == published_commit, 'NOT_PUBLISHED_EXACT_COMMIT')
    require(type(phase_a) is str and phase_a.startswith('docs/contracts/') and
            '..' not in Path(phase_a).parts and phase_a.endswith('.json'), 'PHASE_A_PATH')
    raw = git('show', published_commit + ':' + phase_a)
    require((repo / phase_a).read_bytes().replace(b'\r\n', b'\n') == raw.replace(b'\r\n', b'\n'), 'PHASE_A_WORKTREE_CHANGED')
    phase = json.loads(raw)
    binding = phase['live_binding']
    plan_raw = Path(plan_path).read_bytes(); plan = json.loads(plan_raw)
    preflight_raw = Path(preflight_path).read_bytes(); preflight = json.loads(preflight_raw)
    required = {'scope': 'BOUNDED_EIGHT_CLIP_LIVE_EVALUATION', 'manifest_sha256': plan['manifest_sha256'],
                'config_sha256': digest(Path(config_path).read_bytes()), 'aws_client_sha256': digest(Path(aws_path).read_bytes()),
                'aws_config_sha256': digest(Path(aws_config_path).read_bytes()),
                'operator_sha256': digest(Path(__file__).read_bytes().replace(b'\r\n', b'\n')),
                'plan_sha256': digest(plan_raw), 'preflight_sha256': digest(preflight_raw),
                'aws_speech_calls_before_publication': 0, 'bounded8_data_authority': 'PASS',
                'broad_dataset_coverage': 'NOT_SATISFIED', 'thresholds': {'ru': 20, 'en': 18},
                'timestamp_accuracy': 'NOT_EVALUATED', 'noise_robustness': 'NOT_EVALUATED',
                'en_spontaneous': 'NOT_EVALUATED', 'provider_admission': 'NOT_ESTABLISHED'}
    require(all(binding.get(k) == v for k, v in required.items()), 'PUBLICATION_BINDING_MISMATCH')
    require(preflight.get('status') == 'BOUNDED8_PREFLIGHT_PASS' and
            preflight.get('bounded8_data_authority') == 'PASS' and
            preflight.get('broad_dataset_coverage') == 'NOT_SATISFIED' and
            preflight.get('aws_speech_calls') == 0 and
            preflight.get('internal_alpha_checks') == {f'OD-11C-26-{i:02}': 'PASS' for i in range(1, 13)} and
            preflight.get('checks') == {f'PREFLIGHT-{i:02}': 'PASS' for i in range(1, 13)}, 'LIVE_PREFLIGHT_NOT_PASS')
    require(allow_expired or datetime.now(timezone.utc) < datetime.fromisoformat(preflight['expires_at']), 'PREFLIGHT_EXPIRED')
    return {'published_commit': published_commit, 'phase_a_sha256': digest(raw),
            'verified_at': utc_now(), 'remote_head': remote, **required}


class Runner:
    def __init__(self, store, root, config, api, plan, publication, *, sleeper=time.sleep, proof_api=None):
        self.store, self.config, self.api, self.plan, self.publication = store, config, api, plan, publication
        self.disk = CorpusStore(root, store.repo)
        require(self.disk.root != store.root and not self.disk.root.is_relative_to(store.root), 'RUN_ROOT_MUST_BE_SEPARATE')
        self.core = load_harness(Path(__file__).resolve().parents[2])
        self.sleeper = sleeper
        self.proof_api = proof_api or api
        self.session = secrets.token_hex(16)
        self.disk.root.mkdir(parents=True, exist_ok=True)

    def _read(self, path):
        return json.loads(self.disk._path(path).read_bytes())

    def _call(self, case, service, operation, *args, api=None):
        calls = self.disk._path('calls'); calls.mkdir(exist_ok=True)
        seq = len(list(calls.iterdir()))
        require(seq < self.plan['maximum_api_calls'], 'API_CALL_BUDGET_EXHAUSTED')
        if operation in ('put-object', 'start-transcription-job'):
            if self.config.get('write_deadline'):
                require(datetime.now(timezone.utc) < datetime.fromisoformat(self.config['write_deadline']), 'WRITE_DEADLINE_EXPIRED')
            prior = [json.loads(p.read_bytes()) for p in calls.glob('*/intent.json')]
            starts = sum(c['operation'] == 'start-transcription-job' for c in prior)
            require(operation != 'start-transcription-job' or starts < self.plan['maximum_start_dispatches'], 'START_DISPATCH_BUDGET_EXHAUSTED')
            uploads = sum(c.get('upload_bytes', 0) for c in prior)
            size = Path(args[args.index('--body')+1]).stat().st_size if operation == 'put-object' else 0
            require(uploads + size <= self.plan['maximum_upload_bytes'], 'UPLOAD_BYTE_BUDGET_EXHAUSTED')
        else:
            size = 0
        base = f'calls/{seq:06}'
        intent = {'case_id': case, 'service': service, 'operation': operation, 'arguments': list(args),
                  'utc': utc_now(), 'monotonic_us': time.monotonic_ns() // 1000, 'session': self.session,
                  'upload_bytes': size}
        self.disk._write(base + '/intent.json', intent, immutable=True)
        try:
            response = (api or self.api).call(service, operation, *args)
        except (AwsError, subprocess.TimeoutExpired, TimeoutError) as error:
            code = error.code if isinstance(error, AwsError) else 'ClientTimeout'
            self.disk._write(base + '/error.json', {'code': code, 'utc': utc_now(),
                'private_detail': getattr(error, 'detail', None)}, immutable=True)
            raise
        self.disk._write(base + '/response.json', {'value': response, 'utc': utc_now(),
                         'monotonic_us': time.monotonic_ns() // 1000}, immutable=True)
        return response

    def _payload(self, case):
        if 'path' in case:
            path = self.store._path(case['path'])
            require(digest(path.read_bytes()) == case['uploaded_wav_sha256'], 'AUDIO_HASH_MISMATCH')
            return path
        kind = case['fixture']
        raw = self.core.wav_bytes(0 if kind == 'empty' else 4000 if kind == 'near_empty' else 48000)
        if kind == 'malformed': raw = b'DORA_SYNTHETIC_NOT_WAV'
        if kind == 'truncated': raw = raw[:-101]
        name = f'fixtures/{case["id"]}.wav'
        self.disk._write(name, raw, immutable=True)
        return self.disk._path(name)

    def _request(self, case):
        name = self.config['job_prefix'] + case['id']
        output = self.config['outputs']
        return {'TranscriptionJobName': name, 'LanguageCode': 'ru-RU' if case['language'] == 'ru' else 'en-US',
                'MediaFormat': 'mp3' if case.get('fixture') == 'wrong_format' else 'wav',
                'MediaSampleRateHertz': 16000,
                'Media': {'MediaFileUri': f's3://{output["InputBucket"]}/{self.config["input_prefix"]}{case["id"]}.wav'},
                'OutputBucketName': output['OutputBucket'], 'OutputKey': self.config['output_prefix'] + case['id'] + '.json',
                'OutputEncryptionKMSKeyId': output['OutputKey'],
                'JobExecutionSettings': {'AllowDeferredExecution': False, 'DataAccessRoleArn': output['DataRole']}}

    def _attempt(self, case):
        base = 'attempts/' + case['id']
        if self.disk._path(base + '/evidence.json').exists():
            return self._read(base + '/evidence.json')
        request = self._request(case)
        self.disk._write(base + '/request.json', request, immutable=True)
        uncertain = False
        if case.get('fixture') != 'missing_s3' and not self.disk._path(base + '/upload.complete.json').exists():
            path = self._payload(case); body = path.read_bytes()
            key = self.config['input_prefix'] + case['id'] + '.wav'
            if self.disk._path(base + '/upload.intent.json').exists():
                head = self._call(case['id'], 's3api', 'head-object', '--bucket', self.config['outputs']['InputBucket'],
                                  '--key', key, '--expected-bucket-owner', self.config['account_id'])
                require(head.get('ContentLength') == len(body) and head.get('Metadata', {}).get('sha256') == digest(body),
                        'UNCERTAIN_UPLOAD_REQUIRES_RECONCILIATION')
            else:
                self.disk._write(base + '/upload.intent.json', {'sha256': digest(body), 'utc': utc_now()}, immutable=True)
                self._call(case['id'], 's3api', 'put-object', '--bucket', self.config['outputs']['InputBucket'], '--key', key,
                    '--body', str(path), '--content-type', 'audio/wav', '--server-side-encryption', 'aws:kms',
                    '--ssekms-key-id', self.config['outputs']['InputKey'], '--expected-bucket-owner', self.config['account_id'],
                    '--metadata', json.dumps({'sha256': digest(body)}))
            self.disk._write(base + '/upload.complete.json', {'sha256': digest(body)}, immutable=True)
        submit_path = base + '/submit.intent.json'
        if not self.disk._path(submit_path).exists():
            submitted = {'session': self.session, 'monotonic_us': time.monotonic_ns() // 1000, 'utc': utc_now(),
                         'request_sha256': digest(encoded(request))}
            self.disk._write(submit_path, submitted, immutable=True)
            try:
                response = self._call(case['id'], 'transcribe', 'start-transcription-job', '--cli-input-json', json.dumps(request))
            except (subprocess.TimeoutExpired, TimeoutError):
                self.disk._write(base + '/uncertain.json', {'status': 'UNCERTAIN_SUBMISSION'}, immutable=True)
                raise ValueError('UNCERTAIN_SUBMISSION') from None
            except AwsError as error:
                if error.code not in ('BadRequestException', 'ConflictException', 'AccessDeniedException',
                                      'UnrecognizedClientException', 'ExpiredTokenException', 'InvalidSignatureException',
                                      'ThrottlingException', 'LimitExceededException', 'ValidationException'):
                    self.disk._write(base + '/uncertain.json', {'status': 'UNCERTAIN_SUBMISSION', 'code': error.code}, immutable=True)
                    raise ValueError('UNCERTAIN_SUBMISSION') from None
                return self._failure(case, base, error.code)
            self.disk._write(base + '/submit.response.json', response, immutable=True)
        else:
            submitted = self._read(submit_path)
            uncertain = (not self.disk._path(base + '/submit.response.json').exists() or
                         self.disk._path(base + '/uncertain.json').exists() or self.disk._path(base + '/timeout.json').exists())
        terminal_path = base + '/terminal.json'
        if self.disk._path(terminal_path).exists():
            terminal = self._read(terminal_path)
        else:
            started = time.monotonic()
            while True:
                try:
                    value = self._call(case['id'], 'transcribe', 'get-transcription-job',
                                       '--transcription-job-name', request['TranscriptionJobName'])['TranscriptionJob']
                except AwsError as error:
                    if uncertain:
                        raise ValueError('UNCERTAIN_SUBMISSION_REQUIRES_RECONCILIATION') from None
                    raise
                require(value['TranscriptionJobName'] == request['TranscriptionJobName'] and
                        value.get('LanguageCode') == request['LanguageCode'] and
                        value.get('Media', {}).get('MediaFileUri') == request['Media']['MediaFileUri'], 'RESULT_IDENTITY_MISMATCH')
                if value['TranscriptionJobStatus'] in ('COMPLETED', 'FAILED'):
                    terminal = {'job': value, 'session': self.session, 'monotonic_us': time.monotonic_ns() // 1000, 'utc': utc_now()}
                    self.disk._write(terminal_path, terminal, immutable=True)
                    break
                if time.monotonic() - started >= self.plan['job_timeout_seconds']:
                    self.disk._write(base + '/timeout.json', {'status': 'TIMEOUT', 'utc': utc_now()}, immutable=True)
                    raise ValueError('TIMEOUT_STOP_NEW_WORK_RECONCILE')
                self.sleeper(self.plan['poll_seconds'])
        if terminal['job']['TranscriptionJobStatus'] == 'FAILED':
            return self._failure(case, base, 'ProviderJobFailed', provider=terminal['job'])
        raw_path = base + '/raw-output.json'
        retrieval_start = time.monotonic_ns() // 1000
        if not self.disk._path(raw_path).exists():
            head = self._call(case['id'], 's3api', 'head-object', '--bucket', request['OutputBucketName'],
                              '--key', request['OutputKey'], '--expected-bucket-owner', self.config['account_id'])
            require(0 < head['ContentLength'] <= MAX_RESULT_BYTES, 'RESULT_SIZE_LIMIT')
            previous_downloads = sum(p.stat().st_size for p in self.disk._path('attempts').glob('*/retrieved-*.json'))
            require(previous_downloads + head['ContentLength'] <= self.plan['maximum_download_bytes'], 'DOWNLOAD_BYTE_BUDGET_EXHAUSTED')
            temporary = self.disk._path(base + '/retrieved-' + secrets.token_hex(8) + '.json')
            self._call(case['id'], 's3api', 'get-object', '--bucket', request['OutputBucketName'], '--key', request['OutputKey'],
                       '--expected-bucket-owner', self.config['account_id'], str(temporary))
            raw = temporary.read_bytes()
            require(len(raw) == head['ContentLength'] and len(raw) <= MAX_RESULT_BYTES, 'RESULT_SIZE_MISMATCH')
            self.disk._write(raw_path, raw, immutable=True)
        raw = self.disk._path(raw_path).read_bytes()
        retrieval_end = time.monotonic_ns() // 1000
        evidence = {**case, 'job_name': request['TranscriptionJobName'], 'aws_job_id': None,
                    'request_sha256': digest(encoded(request)), 'result_sha256': digest(raw),
                    'submit': submitted, 'terminal_observed': terminal,
                    'retrieval_duration_us': retrieval_end-retrieval_start, 'automatic_retry': False,
                    'status': 'UNCERTAIN_SUBMISSION_RECONCILED' if uncertain else 'SUCCEEDED',
                    'raw': None, 'normalized': None, 'timestamp_structure': None,
                    'service_turnaround_us': terminal['monotonic_us']-submitted['monotonic_us']
                    if submitted['session'] == terminal['session'] else None}
        try:
            wire = json.loads(raw)
            evidence['timestamp_structure'] = self.core.timestamp_audit(wire['results']['items'], case['duration_us'])
            result = self.core.AwsRecordAdapter().terminal(
                self.core.Request(request['TranscriptionJobName'], request['LanguageCode'], request['Media']['MediaFileUri'],
                                  evidence['request_sha256'], case['duration_us']),
                {'region': REGION, 'jobName': request['TranscriptionJobName'], 'language': request['LanguageCode'],
                 'input_ref': request['Media']['MediaFileUri'], 'config_sha256': evidence['request_sha256'],
                 'status': 'COMPLETED', 'output': wire})
            require(not evidence['timestamp_structure']['missing'], 'MISSING_TIMESTAMPS')
            require(not result.text.strip() or evidence['timestamp_structure']['pronunciation_items'] > 0,
                    'NONEMPTY_TRANSCRIPT_PRONUNCIATION_ITEMS_MISSING')
            require(not case['primary'] or evidence['timestamp_structure']['pronunciation_items'] > 0,
                    'PRIMARY_PRONUNCIATION_ITEMS_MISSING')
            if case.get('fixture') in ('empty', 'near_empty'):
                require(not result.text.strip(), 'SILENCE_RETURNED_NONEMPTY_TRANSCRIPT')
            evidence['transcript_raw_sha256'] = digest(result.text.encode())
            evidence['transcript_normalized_sha256'] = digest(self.core.normalize(result.text).encode())
            if case['primary'] and not uncertain:
                reference = self.store._load()['records'][case['id']]['reference']['text']
                require(digest(reference.encode()) == case['reference_raw_sha256'] and
                        digest(self.core.normalize(reference).encode()) == case['reference_normalized_sha256'],
                        'REFERENCE_CHANGED_AFTER_FREEZE')
                scored = self.core.score(reference, result.text)
                for mode in ('raw', 'normalized'):
                    evidence[mode] = {k: v for k, v in scored[mode].items() if k != 'matches'}
        except (ValueError, KeyError, TypeError, IndexError):
            evidence.update(status='MALFORMED_RESULT', error_class='malformed_result', raw=None, normalized=None)
        if case.get('fixture') == 'duplicate_name':
            if self.disk._path(base + '/duplicate.intent.json').exists():
                evidence['duplicate_job_name'] = 'UNRESOLVED_NO_REDISPATCH'
            else:
                duplicate = {**request, 'Media': {'MediaFileUri': request['Media']['MediaFileUri'] + '.intentionally-missing'}}
                self.disk._write(base + '/duplicate.request.json', duplicate, immutable=True)
                self.disk._write(base + '/duplicate.intent.json', {'request_sha256': digest(encoded(duplicate))}, immutable=True)
                try:
                    self._call(case['id'], 'transcribe', 'start-transcription-job', '--cli-input-json', json.dumps(duplicate))
                    evidence['duplicate_job_name'] = 'UNEXPECTED_ACCEPTANCE'
                except AwsError as error:
                    evidence['duplicate_job_name'] = 'PASS' if error.code == 'ConflictException' else 'FAIL'
        self.disk._write(base + '/evidence.json', evidence, immutable=True)
        return evidence

    def _failure(self, case, base, code, provider=None):
        error = self.core.error_map(code)
        request = self._read(base + '/request.json')
        evidence = {**case, 'status': 'FAILED', 'error_code': code, 'error_class': error['category'],
                    'retryable': error['retryable'], 'automatic_retry': False, 'provider_terminal': provider,
                    'raw': None, 'normalized': None, 'service_turnaround_us': None,
                    'job_name': request['TranscriptionJobName'], 'request_sha256': digest(encoded(request)),
                    'submit': self._read(base + '/submit.intent.json')}
        self.disk._write(base + '/evidence.json', evidence, immutable=True)
        return evidence

    def _cleanup_case(self, case):
        """Never called until evidence.json was atomically/fsync published."""
        require(self.disk._path('attempts/' + case['id'] + '/evidence.json').exists(), 'EVIDENCE_NOT_DURABLE')
        request = self._request(case)
        try:
            for bucket, key in ((self.config['outputs']['InputBucket'], self.config['input_prefix']+case['id']+'.wav'),
                                (request['OutputBucketName'], request['OutputKey'])):
                base = ('--bucket', bucket, '--expected-bucket-owner', self.config['account_id'])
                self._call(case['id'], 's3api', 'delete-object', '--bucket', bucket, '--key', key,
                           '--expected-bucket-owner', self.config['account_id'])
                versions = self._call(case['id'], 's3api', 'list-object-versions', *base, '--prefix', key)
                for obj in versions.get('Versions', []) + versions.get('DeleteMarkers', []):
                    if obj['Key'] == key:
                        self._call(case['id'], 's3api', 'delete-object', *base, '--key', key, '--version-id', obj['VersionId'])
                parts = self._call(case['id'], 's3api', 'list-multipart-uploads', *base, '--prefix', key)
                for part in parts.get('Uploads', []):
                    if part['Key'] == key:
                        self._call(case['id'], 's3api', 'abort-multipart-upload', *base, '--key', key, '--upload-id', part['UploadId'])
                remaining = self._call(case['id'], 's3api', 'list-object-versions', *base, '--prefix', key)
                require(not any(obj['Key'] == key for obj in remaining.get('Versions', []) + remaining.get('DeleteMarkers', [])),
                        'CASE_OBJECT_CLEANUP_NOT_VERIFIED')
            try:
                self._call(case['id'], 'transcribe', 'delete-transcription-job', '--transcription-job-name', request['TranscriptionJobName'])
            except AwsError as error:
                require(error.code in ('BadRequestException', 'NotFoundException'), 'JOB_CLEANUP_FAILED')
            remaining_jobs = self._call(case['id'], 'transcribe', 'list-transcription-jobs', '--job-name-contains', request['TranscriptionJobName'])
            require(not any(j['TranscriptionJobName'] == request['TranscriptionJobName']
                            for j in remaining_jobs.get('TranscriptionJobSummaries', [])), 'CASE_JOB_CLEANUP_NOT_VERIFIED')
            self.disk._write('attempts/' + case['id'] + '/cleanup.json', {'status': 'DELETE_REQUESTED', 'utc': utc_now()})
            return True
        except (AwsError, subprocess.TimeoutExpired, TimeoutError, ValueError):
            return False

    def cleanup(self):
        failures = []
        for case in self.plan['cases']:
            if self.disk._path('attempts/' + case['id'] + '/evidence.json').exists() and not self._cleanup_case(case):
                failures.append('CASE_CLEANUP_FAILED')
        try:
            for kind in ('Input', 'Output'):
                bucket = self.config['outputs'][kind+'Bucket']
                base = ('--bucket', bucket, '--expected-bucket-owner', self.config['account_id'])
                for op, keys in (('list-object-versions', ('Versions', 'DeleteMarkers')),
                                 ('list-multipart-uploads', ('Uploads',)), ('list-objects-v2', ('Contents',))):
                    value = self._call('cleanup', 's3api', op, *base, api=self.proof_api)
                    if any(value.get(key) for key in keys): failures.append('TASK_BUCKET_NOT_EMPTY')
            jobs = self._call('cleanup', 'transcribe', 'list-transcription-jobs', '--job-name-contains', self.config['job_prefix'], api=self.proof_api)
            if jobs.get('TranscriptionJobSummaries'): failures.append('TASK_JOBS_NOT_EMPTY')
        except (AwsError, subprocess.TimeoutExpired, TimeoutError):
            failures.append('CLEANUP_READBACK_FAILED')
        result = {'status': 'CLEANUP_BLOCKED' if failures else 'VERIFIED_VISIBLE_EMPTY', 'failures': failures,
                  'verified_at': utc_now(), 'provider_internal_purge': 'NOT_CLAIMED'}
        self.disk._write('cleanup-latest.json', result)
        return result

    def run(self):
        corpus_lock = _ProcessLock(self.store.root)
        lock = None
        gate_passed = False
        try:
            lock = _ProcessLock(self.disk.root)
            marker = self.store._path('provider-started.json')
            binding_path = self.disk._path('run-binding.json')
            if marker.exists():
                require(binding_path.exists(), 'RUN_EVIDENCE_MISSING')
                require(json.loads(marker.read_bytes()).get('run_root_sha256') == digest(str(self.disk.root).encode()),
                        'CANONICAL_RUN_BINDING_MISMATCH')
                require(json.loads(marker.read_bytes()).get('run_nonce') == self._read('run-binding.json')['run_nonce'],
                        'CANONICAL_RUN_BINDING_MISMATCH')
                require(self.disk._path('publication.json').exists(), 'RUN_EVIDENCE_MISSING')
            proof = verify_publication(**self.publication)
            self.store.validate_manifest()
            require(self.store.public_summary()['manifest_sha256'] == self.plan['manifest_sha256'], 'MANIFEST_BINDING_MISMATCH')
            expected = build_plan(self.store, self.plan['pricing'], technical=tuple(c['fixture'] for c in self.plan['cases'] if not c['primary']))
            require(self.plan == expected, 'FROZEN_PLAN_MISMATCH')
            self.disk._write('plan.json', self.plan, immutable=True)
            if not self.disk._path('publication.json').exists():
                require(not self.disk._path('calls').exists(), 'UNPUBLISHED_PRIOR_CALLS')
                self.disk._write('publication.json', proof, immutable=True)
            else:
                require(self._read('publication.json')['published_commit'] == proof['published_commit'], 'PUBLICATION_CHANGED')
            if not binding_path.exists():
                self.disk._write('run-binding.json', {'run_nonce': secrets.token_hex(16)}, immutable=True)
            self.store._write('provider-started.json', {'published_commit': proof['published_commit'],
                              'manifest_sha256': self.plan['manifest_sha256'], 'plan_sha256': digest(encoded(self.plan)),
                              'run_root_sha256': digest(str(self.disk.root).encode()),
                              'run_nonce': self._read('run-binding.json')['run_nonce']}, immutable=True)
            gate_passed = True
            for case in self.plan['cases']:
                self._attempt(case)
                if not self._cleanup_case(case): break
            cleanup = self.cleanup()
            self.store.validate_manifest()
            report = self.report(cleanup)
            self.disk._write('report-latest.json', report)
            return report
        except Exception:
            # Failed/unknown current attempts remain intact; cleanup only consumes
            # already durable terminal evidence, never deletes an in-flight job.
            if gate_passed:
                try:
                    self.disk._write('report-latest.json', self.report(self.cleanup()))
                except Exception:
                    self.disk._write('report-latest.json', self.report({'status': 'CLEANUP_BLOCKED'}))
            raise
        finally:
            if lock: lock.close()
            corpus_lock.close()

    def report(self, cleanup=None):
        records, rows = [], []
        for case in self.plan['cases']:
            path = 'attempts/' + case['id'] + '/evidence.json'
            record = self._read(path) if self.disk._path(path).exists() else {**case,
                'status': 'UNRESOLVED' if self.disk._path('attempts/'+case['id']+'/upload.intent.json').exists() or
                self.disk._path('attempts/'+case['id']+'/submit.intent.json').exists() else 'NOT_RUN',
                'raw': None, 'normalized': None}
            records.append(record)
            if case['primary']:
                state = 'SUCCEEDED' if record['status'] == 'SUCCEEDED' else 'NOT_RUN' if record['status'] == 'NOT_RUN' else 'FAILED'
                rows.append({'case_id': case['id'], 'state': state, 'raw': record['raw'], 'normalized': record['normalized']})
        quality = bounded_report(rows, protocol=EASY_EN_PROTOCOL)
        latency = {}
        for lang in ('ru', 'en'):
            measured = [r for r in records if r['primary'] and r['language'] == lang and
                        r['status'] == 'SUCCEEDED' and r.get('service_turnaround_us') is not None]
            ratios = [Fraction(r['service_turnaround_us'], r['duration_us']) for r in measured]
            absolute = [r['service_turnaround_us'] for r in measured]
            latency[lang] = {'expected_records': 4, 'measured_records': len(measured),
                'sample_status': 'SMALL_SAMPLE_DESCRIPTIVE_ONLY',
                'p50_ratio': str(self.core.quantile(ratios, 50)) if ratios else None,
                'p95_ratio': str(self.core.quantile(ratios, 95)) if ratios else None,
                'p50_us': self.core.quantile(absolute, 50) if absolute else None,
                'p95_us': self.core.quantile(absolute, 95) if absolute else None}
            latency[lang]['bounded_diagnostic'] = 'INCOMPLETE' if len(measured) != 4 else (
                'PASS' if self.core.quantile(ratios, 50) <= 1 and self.core.quantile(ratios, 95) <= 2 and
                self.core.quantile(absolute, 95) <= 120_000_000 else 'FAIL')
        cleanup = cleanup or (self._read('cleanup-latest.json') if self.disk._path('cleanup-latest.json').exists()
                              else {'status': 'NOT_VERIFIED'})
        verdicts = [quality['languages'][lang]['bounded_quality'] for lang in ('ru', 'en')]
        diagnostics = []
        for record in records:
            if record['primary']: continue
            kind = record['fixture']; status = record['status']
            if status in ('NOT_RUN', 'UNRESOLVED', 'UNCERTAIN_SUBMISSION_RECONCILED'):
                outcome = 'INCOMPLETE'
            elif kind == 'duplicate_name':
                outcome = 'PASS' if record.get('duplicate_job_name') == 'PASS' else 'FAIL'
            elif kind in ('malformed', 'wrong_format', 'missing_s3', 'truncated'):
                outcome = 'INCOMPLETE' if status == 'FAILED' else 'FAIL'
            else:
                outcome = 'PASS' if status == 'SUCCEEDED' else 'INCOMPLETE' if kind in ('empty', 'near_empty') and status == 'FAILED' else 'FAIL'
            diagnostics.append({'id': record['id'], 'outcome': outcome, 'provider_status': status})
        gates = verdicts + [v['bounded_diagnostic'] for v in latency.values()] + [r['outcome'] for r in diagnostics]
        verdict = 'PASS' if all(v == 'PASS' for v in gates) and cleanup['status'] == 'VERIFIED_VISIBLE_EMPTY' else (
            'FAIL' if 'FAIL' in gates else 'INCOMPLETE')
        calls = [json.loads(p.read_bytes()) for p in self.disk._path('calls').glob('*/intent.json')]
        return {'schema_version': '1.0', 'bounded8': verdict, 'quality': quality, 'records': records, 'latency': latency,
                'attempt_accounting': {'planned_primary_records': 8,
                    'start_dispatches': sum(c['operation'] == 'start-transcription-job' for c in calls),
                    'primary_start_dispatches': sum(c['operation'] == 'start-transcription-job' and c['case_id'] in EASY_EN_IDS for c in calls),
                    'api_calls': len(calls), 'automatic_retries': 0,
                    'unresolved_records': sum(r['status'] in ('UNRESOLVED', 'UNCERTAIN_SUBMISSION_RECONCILED') for r in records)},
                'cleanup': cleanup, 'budget': self.plan['budget'], 'technical_cases': diagnostics,
                'timestamp_accuracy': 'NOT_EVALUATED',
                'noise_robustness': 'NOT_EVALUATED', 'en_spontaneous': 'NOT_EVALUATED',
                'provider_admission': 'NOT_ESTABLISHED', 'stage_6_3': 'NOT_RUN',
                'manifest_sha256': self.plan['manifest_sha256'], 'private_report': True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('plan', 'run', 'resume', 'cleanup', 'report'))
    for flag in ('repo', 'corpus', 'run-root', 'config'):
        parser.add_argument('--'+flag, required=True)
    for flag in ('aws', 'aws-config', 'phase-a', 'published-commit'):
        parser.add_argument('--'+flag)
    parser.add_argument('--technical', default=','.join(TECHNICAL))
    parser.add_argument('--git', default='git')
    args = parser.parse_args()
    from tools.cloud62d_aws.aws_prepare import Aws, assume_operator, validate_config
    config_path = Path(args.config).resolve(); config = json.loads(config_path.read_bytes())
    store = CorpusStore(args.corpus, args.repo)
    disk = CorpusStore(args.run_root, args.repo)
    if args.action == 'plan':
        plan = build_plan(store, config['live']['pricing'], technical=tuple(filter(None, args.technical.split(','))))
        disk._write('plan.json', plan, immutable=True)
        print(json.dumps({'status': 'PRIVATE_PLAN_FROZEN', 'plan_sha256': digest(encoded(plan)), 'budget': plan['budget']}))
        return
    plan = json.loads(disk._path('plan.json').read_bytes())
    publication = dict(repo=args.repo, phase_a=args.phase_a, published_commit=args.published_commit,
        config_path=args.config, aws_path=args.aws, aws_config_path=args.aws_config,
        preflight_path=config['live']['preflight_path'], plan_path=str(disk._path('plan.json')), git_binary=args.git)
    if args.action == 'report':
        report = Runner(store, args.run_root, config, None, plan, publication).report()
        disk._write('report-latest.json', report)
    else:
        validate_config(config)
        verify_publication(**publication, allow_expired=args.action == 'cleanup')
        proof_api = Aws(args.aws, config_file=args.aws_config)
        api = assume_operator(proof_api, config)
        runner = Runner(store, args.run_root, config, api, plan, publication, proof_api=proof_api)
        if args.action == 'cleanup':
            corpus_lock = _ProcessLock(store.root)
            run_lock = None
            try:
                run_lock = _ProcessLock(disk.root)
                marker = json.loads(store._path('provider-started.json').read_bytes())
                require(marker['run_root_sha256'] == digest(str(disk.root).encode()) and
                        marker['run_nonce'] == runner._read('run-binding.json')['run_nonce'], 'CANONICAL_RUN_BINDING_MISMATCH')
                report = runner.cleanup()
                disk._write('report-latest.json', runner.report(report))
            finally:
                if run_lock: run_lock.close()
                corpus_lock.close()
        else:
            report = runner.run()
    print(json.dumps({'status': report.get('bounded8', report.get('status')), 'provider_admission': 'NOT_ESTABLISHED'}))


if __name__ == '__main__':
    try:
        main()
    except (ValueError, AwsError, OSError, subprocess.SubprocessError, KeyError) as error:
        code = str(error)
        print(json.dumps({'status': 'BLOCKED', 'error': code if re.fullmatch('[A-Z][A-Z0-9_]{0,100}', code) else 'PRIVATE_OPERATOR_ERROR'}))
        sys.exit(2)
