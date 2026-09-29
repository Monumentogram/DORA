"""Task-owned Lambda API bridge; no credentials, logs, or public endpoint."""

import base64
import binascii
import hashlib
import json
import os
from types import SimpleNamespace


OPERATIONS = {
    'sts': {'get_caller_identity'},
    's3': {'put_object', 'head_object', 'get_object', 'delete_object', 'delete_bucket'},
    'transcribe': {'start_transcription_job', 'get_transcription_job', 'delete_transcription_job'},
}
MAX_BODY = 1048576
WAV_HASH = '3197cdd90229d945aefa33f0429fbe0849387df2f80291d761e16406139457d2'


def _scope(event, environment):
    service, method, params = event['service'], event['method'], event['params']
    bucket = environment['DORA_BUCKET']
    job = environment['DORA_JOB_NAME']
    if service == 's3':
        expected = {'Bucket': bucket, 'ExpectedBucketOwner': environment['DORA_ACCOUNT']}
        if method == 'put_object':
            if set(params) != set(expected) | {'Key', 'BodyBase64', 'ServerSideEncryption'}:
                raise ValueError('unexpected Put parameter')
            if any(params[key] != value for key, value in expected.items()):
                raise ValueError('out-of-scope bucket or owner')
            if params['Key'] != 'synthetic-ru.wav' or params['ServerSideEncryption'] != 'AES256':
                raise ValueError('out-of-scope object or encryption')
            try:
                audio = base64.b64decode(params['BodyBase64'], validate=True)
            except (TypeError, ValueError, binascii.Error) as error:
                raise ValueError('invalid synthetic audio encoding') from error
            if len(audio) > MAX_BODY or hashlib.sha256(audio).hexdigest() != WAV_HASH:
                raise ValueError('synthetic audio does not match authored fixture')
        else:
            if method != 'delete_bucket':
                expected['Key'] = 'synthetic-ru.wav'
            if params != expected:
                raise ValueError('out-of-scope S3 request')
    elif service == 'transcribe':
        if method == 'start_transcription_job':
            expected = {'TranscriptionJobName': job,
                        'Media': {'MediaFileUri': 's3://' + bucket + '/synthetic-ru.wav'},
                        'LanguageCode': 'ru-RU'}
            if params != expected:
                raise ValueError('out-of-scope Start request')
        elif params != {'TranscriptionJobName': job}:
            raise ValueError('out-of-scope job')
    elif params:
        raise ValueError('STS identity takes no parameters')


def dispatch(event, client_factory, config=None):
    service, method = event.get('service'), event.get('method')
    if service not in OPERATIONS or method not in OPERATIONS[service]:
        raise ValueError('operation is not allowed')
    params = dict(event.get('params') or {})
    if 'BodyBase64' in params:
        if not (service == 's3' and method == 'put_object'):
            raise ValueError('body is not allowed here')
        params['Body'] = base64.b64decode(params.pop('BodyBase64'), validate=True)
        if len(params['Body']) > MAX_BODY:
            raise ValueError('body exceeds one MiB')
    response = getattr(client_factory(service, config=config or SimpleNamespace(retries={'total_max_attempts': 1})), method)(**params)
    if service == 's3' and method == 'get_object':
        body = response.pop('Body').read(MAX_BODY + 1)
        if len(body) > MAX_BODY:
            raise ValueError('response body exceeds one MiB')
        response['BodyBase64'] = base64.b64encode(body).decode('ascii')
    return {'result': json.loads(json.dumps(response, default=str))}


def handler(event, context):
    import boto3
    from botocore.config import Config
    try:
        _scope(event, os.environ)
        config = Config(retries={'total_max_attempts': 1}, connect_timeout=10, read_timeout=20)
        return dispatch(event, lambda service, config: boto3.client(service, region_name=os.environ['AWS_REGION'], config=config), config)
    except Exception as error:
        return {'error': {'type': type(error).__name__, 'detail': str(error),
                          'response': getattr(error, 'response', None)}}
