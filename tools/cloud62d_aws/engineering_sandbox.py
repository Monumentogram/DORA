"""Task-scoped CloudShell harness. Root provisions; task Lambda runs synthetic S0.

No credential files or secrets in outputs. Run stages explicitly; start never retries.
"""
import hashlib
import base64
import io
import json
import sys
import time
import wave
import zipfile
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path
from urllib.parse import urlparse
from urllib.request import urlopen

from engineering_ledger import reserve, finish

REGION = 'eu-central-1'
RUN = 'eng-20260929-72c300a1'
ROOT = Path.home() / ('dora-62d-' + RUN)
LEDGER_ROOT = Path.home() / 'dora-62d-20260929-shared-diagnostics'
WAV_HASH = '3197cdd90229d945aefa33f0429fbe0849387df2f80291d761e16406139457d2'
FIXTURE = Path(__file__).resolve().parent / 'fixtures' / 'engineering-synthetic-ru.wav'


def sha(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, default=str).encode()).hexdigest()


def save(name, value):
    ROOT.mkdir(exist_ok=True)
    with (ROOT / name).open('x', encoding='utf-8') as f:
        json.dump(value, f, sort_keys=True, indent=2, default=str)


def read(name):
    return json.loads((ROOT / name).read_text())


def budget_guard():
    """Conservative combined ceiling; diagnostic calls are already in ASR $2."""
    existing = Decimal('8.4069')
    key_months = Decimal(2) / Decimal(3)  # Two extra CMKs, each used <=10 days.
    cmk = (key_months * Decimal('1')).quantize(Decimal('0.0001'))
    vat = Decimal('0.1334')
    api_contingency = Decimal('0.0999')
    ancillary = cmk + vat + api_contingency
    total = existing + ancillary
    diagnostic = Decimal('12') * Decimal('0.01')
    assert diagnostic <= Decimal('1') and diagnostic <= Decimal('2')
    assert ancillary == Decimal('0.9000') and total <= Decimal('10')
    return {'existing_usd': str(existing), 'ancillary_usd': str(ancillary),
            'prospective_usd': str(total), 'diagnostic_max_usd': str(diagnostic)}


def verify_input_controls(snapshot, desired):
    rules = snapshot['get_bucket_encryption']['ServerSideEncryptionConfiguration']['Rules']
    assert isinstance(rules, list) and len(rules) == 1
    rule = dict(rules[0])
    if 'BlockedEncryptionTypes' in rule:
        assert rule.pop('BlockedEncryptionTypes') in (
            {'EncryptionType': 'SSE-C'}, {'EncryptionType': ['SSE-C']})
    if 'BucketKeyEnabled' in rule:
        assert rule.pop('BucketKeyEnabled') is False
    assert {'Rules': [rule]} == desired['encryption']
    assert snapshot['get_bucket_lifecycle_configuration']['Rules'] == desired['lifecycle']['Rules']


def list_scoped_jobs(client, prefix):
    jobs, token = [], None
    while True:
        args = {'JobNameContains': prefix}
        if token:
            args['NextToken'] = token
        page = client.list_transcription_jobs(**args)
        jobs.extend(j for j in page.get('TranscriptionJobSummaries', [])
                    if j['TranscriptionJobName'].startswith(prefix))
        token = page.get('NextToken')
        if not token:
            return jobs


def known_job_not_found(error):
    detail = getattr(error, 'response', {}).get('Error', {})
    message = detail.get('Message', '').lower()
    return detail.get('Code') == 'BadRequestException' and 'job' in message and (
        'not found' in message or "couldn't be found" in message)


def exact_job(client, name):
    try:
        return client.get_transcription_job(TranscriptionJobName=name)['TranscriptionJob']
    except Exception as error:
        if known_job_not_found(error):
            return None
        raise


def jobs_for_cleanup(client, prefix, exact_name):
    assert exact_name.startswith(prefix)
    jobs = list_scoped_jobs(client, prefix)
    observed = exact_job(client, exact_name)
    if observed:
        jobs = [job for job in jobs if job['TranscriptionJobName'] != exact_name]
        jobs.append({'TranscriptionJobName': exact_name,
                     'TranscriptionJobStatus': observed['TranscriptionJobStatus']})
    return jobs


def bucket_inventory(client, c):
    base = {'Bucket': c['bucket'], 'ExpectedBucketOwner': c['account']}
    found = {'objects': [], 'versions': [], 'delete_markers': [], 'uploads': []}
    token = None
    while True:
        page = client.list_objects_v2(**base, **({'ContinuationToken': token} if token else {}))
        found['objects'].extend(x['Key'] for x in page.get('Contents', []))
        if not page.get('IsTruncated'):
            break
        token = page['NextContinuationToken']
    key_marker = version_marker = None
    while True:
        marker = {'KeyMarker': key_marker} if key_marker else {}
        if version_marker:
            marker['VersionIdMarker'] = version_marker
        page = client.list_object_versions(**base, **marker)
        found['versions'].extend({'key': x['Key'], 'version': x['VersionId']} for x in page.get('Versions', []))
        found['delete_markers'].extend({'key': x['Key'], 'version': x['VersionId']} for x in page.get('DeleteMarkers', []))
        if not page.get('IsTruncated'):
            break
        key_marker, version_marker = page['NextKeyMarker'], page.get('NextVersionIdMarker')
    key_marker = upload_marker = None
    while True:
        marker = {'KeyMarker': key_marker} if key_marker else {}
        if upload_marker:
            marker['UploadIdMarker'] = upload_marker
        page = client.list_multipart_uploads(**base, **marker)
        found['uploads'].extend({'key': x['Key'], 'upload': x['UploadId']} for x in page.get('Uploads', []))
        if not page.get('IsTruncated'):
            break
        key_marker, upload_marker = page['NextKeyMarker'], page.get('NextUploadIdMarker')
    return found


def bucket_is_empty(inventory):
    return all(not values for values in inventory.values())


def missing_resource(error):
    code = getattr(error, 'response', {}).get('Error', {}).get('Code')
    return code in ('NoSuchEntity', 'NoSuchBucket', '404', 'NotFound', 'ResourceNotFoundException')


def session():
    import boto3
    from botocore.config import Config
    s = boto3.Session(region_name=REGION)
    cfg = Config(retries={'total_max_attempts': 1}, connect_timeout=15, read_timeout=30)
    return s, cfg


def build_trust():
    return {'Version': '2012-10-17', 'Statement': [
        {'Effect': 'Allow', 'Principal': {'Service': 'lambda.amazonaws.com'},
         'Action': 'sts:AssumeRole'}]}


def create_function_ready(client, request):
    for attempt in range(6):
        try:
            client.create_function(**request)
            break
        except Exception as error:
            detail = getattr(error, 'response', {}).get('Error', {})
            message = detail.get('Message', '').lower()
            propagation = (detail.get('Code') == 'InvalidParameterValueException' and
                           'role' in message and 'cannot be assumed' in message and 'lambda' in message)
            if not propagation or attempt == 5:
                raise
            time.sleep(2)
    for _ in range(15):
        state = client.get_function_configuration(FunctionName=request['FunctionName'])
        if state.get('State') == 'Active' and state.get('LastUpdateStatus') in (None, 'Successful'):
            return
        if state.get('State') == 'Failed' or state.get('LastUpdateStatus') == 'Failed':
            raise RuntimeError('task Lambda activation failed')
        time.sleep(2)
    raise TimeoutError('task Lambda did not become active')


class RuntimeClient:
    def __init__(self, invocation, function, service):
        self.invocation, self.function, self.service = invocation, function, service

    def __getattr__(self, method):
        def call(**params):
            if 'Body' in params:
                params['BodyBase64'] = base64.b64encode(params.pop('Body')).decode('ascii')
            payload = json.dumps({'service': self.service, 'method': method, 'params': params}).encode()
            assert len(payload) <= 1048576
            reply = self.invocation.invoke(FunctionName=self.function, InvocationType='RequestResponse', Payload=payload)
            result = json.loads(reply['Payload'].read(1048577))
            assert not reply.get('FunctionError') and isinstance(result, dict)
            if 'error' in result:
                error = result['error']
                if error.get('response'):
                    from botocore.exceptions import ClientError
                    raise ClientError(error['response'], method)
                raise RuntimeError(error.get('type', 'RuntimeError') + ': ' + error.get('detail', ''))
            value = result['result']
            if 'BodyBase64' in value:
                value['Body'] = io.BytesIO(base64.b64decode(value.pop('BodyBase64'), validate=True))
            return value
        return call


class RuntimeSession:
    def __init__(self, invocation, function):
        self.invocation, self.function = invocation, function

    def client(self, service, config=None):
        assert service in ('sts', 's3', 'transcribe')
        return RuntimeClient(self.invocation, self.function, service)


def assume(c):
    s, cfg = session()
    runtime = RuntimeSession(s.client('lambda', config=cfg), c['function'])
    identity = runtime.client('sts', config=cfg).get_caller_identity()
    assert identity['Arn'].startswith('arn:aws:sts::' + c['account'] + ':assumed-role/' + c['role'] + '/')
    return runtime, cfg


def provision():
    save('budget-reservation.json', budget_guard())
    s, cfg = session()
    identity = s.client('sts', config=cfg).get_caller_identity()
    account = identity['Account']
    assert identity['Arn'] == 'arn:aws:iam::' + account + ':root', 'Provisioning expects existing console authority only'
    org = s.client('organizations', config=cfg).describe_effective_policy(PolicyType='AISERVICES_OPT_OUT_POLICY')['EffectivePolicy']
    services = json.loads(org['PolicyContent'])['services']
    assert org['TargetId'] == account and services.get('transcribe', services.get('default', {})).get('opt_out_policy') == 'optOut'
    c = {'account': account, 'bucket': 'dora-62d-' + RUN + '-input', 'role': 'dora-62d-' + RUN + '-operator', 'function': 'dora-62d-' + RUN + '-runtime', 'job_prefix': 'd62d-' + RUN + '-', 'deadline': (datetime.now(timezone.utc) + timedelta(hours=4)).isoformat(), 'region': REGION}
    c['role_arn'] = 'arn:aws:iam::' + account + ':role/' + c['role']
    save('config.json', c)
    save('effective-optout.json', org)
    bucketarn = 'arn:aws:s3:::' + c['bucket']
    trust = build_trust()
    active = {'StringEquals': {'aws:RequestedRegion': REGION}, 'DateLessThan': {'aws:CurrentTime': c['deadline']}}
    policy = {'Version': '2012-10-17', 'Statement': [
        {'Effect': 'Allow', 'Action': ['s3:GetObject', 's3:DeleteObject'], 'Resource': bucketarn + '/synthetic-ru.wav'},
        {'Effect': 'Allow', 'Action': 's3:PutObject', 'Resource': bucketarn + '/synthetic-ru.wav', 'Condition': active},
        {'Effect': 'Allow', 'Action': ['s3:ListBucket', 's3:GetBucketLocation', 's3:DeleteBucket'], 'Resource': bucketarn},
        {'Effect': 'Allow', 'Action': 'transcribe:StartTranscriptionJob', 'Resource': '*', 'Condition': active},
        {'Effect': 'Allow', 'Action': ['transcribe:GetTranscriptionJob', 'transcribe:DeleteTranscriptionJob'], 'Resource': 'arn:aws:transcribe:' + REGION + ':' + account + ':transcription-job/' + c['job_prefix'] + '*'}]}
    policy['Statement'].append({'Effect': 'Allow', 'Action': 'sts:GetCallerIdentity', 'Resource': '*'})
    bp = {'Version': '2012-10-17', 'Statement': [
        {'Effect': 'Deny', 'Principal': '*', 'Action': 's3:*', 'Resource': [bucketarn, bucketarn + '/*'], 'Condition': {'Bool': {'aws:SecureTransport': 'false'}}},
        {'Effect': 'Deny', 'Principal': '*', 'Action': 's3:PutObject', 'Resource': bucketarn + '/*', 'Condition': {'DateGreaterThanEquals': {'aws:CurrentTime': c['deadline']}}}]}
    encryption = {'Rules': [{'ApplyServerSideEncryptionByDefault': {'SSEAlgorithm': 'AES256'}}]}
    lifecycle = {'Rules': [{'ID': 'SyntheticFallback', 'Status': 'Enabled', 'Filter': {'Prefix': ''}, 'Expiration': {'Days': 1}, 'AbortIncompleteMultipartUpload': {'DaysAfterInitiation': 1}}]}
    save('desired-policies.json', {'trust': trust, 'operator': policy, 'bucket': bp, 'encryption': encryption, 'lifecycle': lifecycle})
    tags = [{'Key': 'Project', 'Value': 'DORA'}, {'Key': 'Purpose', 'Value': 'ENGINEERING_SANDBOX'}, {'Key': 'Run', 'Value': RUN}]
    iam = s.client('iam', config=cfg)
    iam.create_role(RoleName=c['role'], AssumeRolePolicyDocument=json.dumps(trust), MaxSessionDuration=3600, Tags=tags)
    iam.put_role_policy(RoleName=c['role'], PolicyName='S0SyntheticOnly', PolicyDocument=json.dumps(policy))
    s3 = s.client('s3', config=cfg)
    s3.create_bucket(Bucket=c['bucket'], CreateBucketConfiguration={'LocationConstraint': REGION}, ObjectOwnership='BucketOwnerEnforced')
    s3.put_public_access_block(Bucket=c['bucket'], PublicAccessBlockConfiguration={k: True for k in ('BlockPublicAcls','IgnorePublicAcls','BlockPublicPolicy','RestrictPublicBuckets')})
    s3.put_bucket_encryption(Bucket=c['bucket'], ServerSideEncryptionConfiguration=encryption)
    s3.put_bucket_policy(Bucket=c['bucket'], Policy=json.dumps(bp))
    s3.put_bucket_tagging(Bucket=c['bucket'], Tagging={'TagSet': tags})
    s3.put_bucket_lifecycle_configuration(Bucket=c['bucket'], LifecycleConfiguration=lifecycle)
    runtime_source = (Path(__file__).resolve().parent / 'engineering_runtime.py').read_bytes()
    archive = io.BytesIO()
    with zipfile.ZipFile(archive, 'w', compression=zipfile.ZIP_DEFLATED) as bundle:
        bundle.writestr('lambda_function.py', runtime_source)
    create_function_ready(s.client('lambda', config=cfg), dict(
        FunctionName=c['function'], Runtime='python3.13', Role=c['role_arn'],
        Handler='lambda_function.handler', Code={'ZipFile': archive.getvalue()},
        Timeout=30, MemorySize=128, Environment={'Variables': {
            'DORA_BUCKET': c['bucket'], 'DORA_JOB_NAME': c['job_prefix'] + 's0',
            'DORA_ACCOUNT': c['account']}},
        Tags={'Project': 'DORA', 'Purpose': 'ENGINEERING_SANDBOX', 'Run': RUN}))
    save('provisioned.json', {'at': datetime.now(timezone.utc), 'status': 'PROVISIONED_NOT_YET_RUNTIME_PROVEN'})
    print('S0_PROVISIONED')


def input_proof():
    c = read('config.json')
    s, cfg = session()
    s3 = s.client('s3', config=cfg)
    iam = s.client('iam', config=cfg)
    snap = {'role': iam.get_role(RoleName=c['role'])['Role'], 'inline': iam.get_role_policy(RoleName=c['role'], PolicyName='S0SyntheticOnly')['PolicyDocument']}
    for op in ['get_bucket_location','get_public_access_block','get_bucket_ownership_controls','get_bucket_encryption','get_bucket_policy','get_bucket_policy_status','get_bucket_versioning','get_bucket_lifecycle_configuration']:
        snap[op] = getattr(s3, op)(Bucket=c['bucket'], ExpectedBucketOwner=c['account'])
    assert snap['get_bucket_location']['LocationConstraint'] == REGION
    assert all(snap['get_public_access_block']['PublicAccessBlockConfiguration'].values())
    assert not snap['get_bucket_policy_status']['PolicyStatus']['IsPublic']
    assert not snap['get_bucket_versioning'].get('Status')
    assert snap['get_bucket_ownership_controls']['OwnershipControls']['Rules'] == [{'ObjectOwnership': 'BucketOwnerEnforced'}]
    desired = read('desired-policies.json')
    assert snap['role']['AssumeRolePolicyDocument'] == desired['trust']
    assert snap['inline'] == desired['operator']
    assert json.loads(snap['get_bucket_policy']['Policy']) == desired['bucket']
    verify_input_controls(snap, desired)
    observed_config = {
        'trust': snap['role']['AssumeRolePolicyDocument'], 'operator': snap['inline'],
        'bucket': json.loads(snap['get_bucket_policy']['Policy']),
        'encryption': snap['get_bucket_encryption']['ServerSideEncryptionConfiguration'],
        'lifecycle': {'Rules': snap['get_bucket_lifecycle_configuration']['Rules']},
        'ownership': snap['get_bucket_ownership_controls']['OwnershipControls'],
        'public_access': snap['get_public_access_block']['PublicAccessBlockConfiguration'],
        'versioning': snap['get_bucket_versioning'].get('Status'),
        'location': snap['get_bucket_location']['LocationConstraint'],
    }
    audio = FIXTURE.read_bytes()
    assert hashlib.sha256(audio).hexdigest() == WAV_HASH
    with wave.open(str(FIXTURE)) as w:
        duration = w.getnframes() / w.getframerate()
        assert 2 <= duration <= 5 and w.getnchannels() == 1 and w.getsampwidth() == 2 and w.getframerate() == 16000
    runtime, cfg = assume(c)
    client = runtime.client('s3', config=cfg)
    client.put_object(Bucket=c['bucket'], Key='synthetic-ru.wav', Body=audio, ServerSideEncryption='AES256', ExpectedBucketOwner=c['account'])
    head = client.head_object(Bucket=c['bucket'], Key='synthetic-ru.wav', ExpectedBucketOwner=c['account'])
    assert head['ServerSideEncryption'] == 'AES256' and head['ContentLength'] == len(audio)
    returned = client.get_object(Bucket=c['bucket'], Key='synthetic-ru.wav', ExpectedBucketOwner=c['account'])['Body'].read()
    assert returned == audio
    save('input-proof.json', {'status': 'INPUT_PASS', 'snapshot': snap, 'observed_config': observed_config,
                              'config_sha256': sha(observed_config), 'audio_sha256': WAV_HASH,
                              'duration': duration, 'head': head})
    print('S0_INPUT_PASS duration=' + str(duration))


def start():
    c, proof = read('config.json'), read('input-proof.json')
    budget_guard()
    assert datetime.now(timezone.utc) < datetime.fromisoformat(c['deadline'])
    assert proof['config_sha256'] and proof['duration'] >= 2 and proof['duration'] <= 5
    runtime, cfg = assume(c)
    req = {'TranscriptionJobName': c['job_prefix'] + 's0', 'Media': {'MediaFileUri': 's3://' + c['bucket'] + '/synthetic-ru.wav'}, 'LanguageCode': 'ru-RU'}
    marked = {**req, 'mode': 'ENGINEERING_SANDBOX', 'audio_kind': 'synthetic', 'audio_sha256': WAV_HASH, 'config_sha256': proof['config_sha256']}
    attempt = reserve(LEDGER_ROOT, marked, 'Private SSE-S3 input and service-managed output work with scoped caller', 'Fresh minimal S0; no Output fields; no DataAccessRole; Start resource wildcard per SAR', proof['duration'])
    save('dispatch-binding.json', {'attempt': str(attempt), 'request_sha256': sha(req), 'submitted_at': datetime.now(timezone.utc)})
    client = runtime.client('transcribe', config=cfg)
    try:
        reply = client.start_transcription_job(**req)
    except Exception as e:
        save('start-error.json', {'type': type(e).__name__, 'detail': str(e), 'response': getattr(e, 'response', None)})
        reconciliation = {'status': 'UNKNOWN', 'job_name': req['TranscriptionJobName']}
        try:
            reconciliation['exact_job_response'] = client.get_transcription_job(TranscriptionJobName=req['TranscriptionJobName'])
        except Exception as lookup_error:
            reconciliation['lookup_error'] = {'type': type(lookup_error).__name__, 'detail': str(lookup_error)}
        save('start-reconcile.json', reconciliation)
        print('S0_START_UNKNOWN_RECONCILE ' + type(e).__name__)
        return
    save('start-reply.json', reply)
    print('S0_ACCEPTED')


def poll():
    c = read('config.json')
    runtime, cfg = assume(c)
    client = runtime.client('transcribe', config=cfg)
    for count in range(60):
        r = client.get_transcription_job(TranscriptionJobName=c['job_prefix'] + 's0')
        job = r['TranscriptionJob']
        save('poll-' + datetime.now(timezone.utc).strftime('%H%M%S%f') + '.json', r)
        if job['TranscriptionJobStatus'] in ('COMPLETED', 'FAILED'):
            save('terminal.json', {'observed_at': datetime.now(timezone.utc), 'response': r})
            attempt = Path(read('dispatch-binding.json')['attempt'])
            if job['TranscriptionJobStatus'] == 'COMPLETED':
                uri = job['Transcript']['TranscriptFileUri']
                parsed = urlparse(uri)
                assert parsed.scheme == 'https' and parsed.hostname.endswith('.amazonaws.com')
                with urlopen(uri, timeout=30) as response:
                    data = response.read(1048577)
                assert len(data) <= 1048576
                transcript = json.loads(data)
                assert transcript['jobName'] == c['job_prefix'] + 's0' and transcript['status'] == 'COMPLETED'
                assert isinstance(transcript['results']['transcripts'], list) and isinstance(transcript['results']['items'], list)
                save('transcript.json', transcript)
                finish(attempt, {'status': 'COMPLETED', 'transcript_sha256': sha(transcript)}, True, True)
                print('CORE_TRANSCRIBE_PATH_PASS text=' + json.dumps(transcript['results']['transcripts'], ensure_ascii=False))
            else:
                finish(attempt, {'status': 'FAILED', 'reason': job.get('FailureReason')}, True, False)
                print('S0_JOB_FAILED ' + job.get('FailureReason', ''))
            return
        time.sleep(5)
    print('S0_PENDING_NO_REDISPATCH')


def cleanup():
    c = read('config.json')
    s, cfg = session()
    root_identity = s.client('sts', config=cfg).get_caller_identity()
    assert root_identity['Arn'] == 'arn:aws:iam::' + c['account'] + ':root'
    root_t = s.client('transcribe', config=cfg)
    root_s3 = s.client('s3', config=cfg)
    root_iam = s.client('iam', config=cfg)
    root_lambda = s.client('lambda', config=cfg)
    role = root_iam.get_role(RoleName=c['role'])['Role']
    role_tags = {tag['Key']: tag['Value'] for tag in role['Tags']}
    bucket_tags = {tag['Key']: tag['Value'] for tag in root_s3.get_bucket_tagging(Bucket=c['bucket'], ExpectedBucketOwner=c['account'])['TagSet']}
    assert role_tags.get('Run') == RUN and bucket_tags.get('Run') == RUN
    runtime, cfg = assume(c)
    t = runtime.client('transcribe', config=cfg)
    s3 = runtime.client('s3', config=cfg)
    exact_name = c['job_prefix'] + 's0'
    jobs = jobs_for_cleanup(root_t, c['job_prefix'], exact_name)
    for job in jobs:
        assert job['TranscriptionJobStatus'] in ('COMPLETED', 'FAILED'), 'IN_FLIGHT_DO_NOT_RETIRE'
        t.delete_transcription_job(TranscriptionJobName=job['TranscriptionJobName'])
    s3.delete_object(Bucket=c['bucket'], Key='synthetic-ru.wav', ExpectedBucketOwner=c['account'])
    inventory = bucket_inventory(root_s3, c)
    remaining_jobs = jobs_for_cleanup(root_t, c['job_prefix'], exact_name)
    assert bucket_is_empty(inventory) and not remaining_jobs, 'WHOLE_RESOURCE_NOT_EMPTY_DO_NOT_RETIRE'
    save('cleanup-empty-proof.json', {'inventory': inventory, 'jobs': remaining_jobs, 'at': datetime.now(timezone.utc)})
    s3.delete_bucket(Bucket=c['bucket'], ExpectedBucketOwner=c['account'])
    root_lambda.delete_function(FunctionName=c['function'])
    root_iam.delete_role_policy(RoleName=c['role'], PolicyName='S0SyntheticOnly')
    root_iam.delete_role(RoleName=c['role'])
    absent = {}
    for label, call in [('bucket', lambda: root_s3.head_bucket(Bucket=c['bucket'], ExpectedBucketOwner=c['account'])),
                        ('role', lambda: root_iam.get_role(RoleName=c['role'])),
                        ('function', lambda: root_lambda.get_function(FunctionName=c['function']))]:
        try:
            call()
        except Exception as error:
            assert missing_resource(error), 'UNEXPECTED_ABSENCE_CHECK_FAILURE'
            absent[label] = True
        else:
            raise AssertionError(label + ' still exists')
    absent['jobs'] = not jobs_for_cleanup(root_t, c['job_prefix'], exact_name)
    assert all(absent.values())
    save('cleanup.json', {'status': 'ABSENT', 'root_readback': absent, 'at': datetime.now(timezone.utc)})
    print('S0_CLEANUP_PASS_RESOURCES_ABSENT')


if __name__ == '__main__':
    {'provision': provision, 'input': input_proof, 'start': start, 'poll': poll, 'cleanup': cleanup}[sys.argv[1]]()
