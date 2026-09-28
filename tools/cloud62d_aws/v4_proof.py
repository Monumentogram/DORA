"""One published-v4 synthetic S3 default-CMK proof; never calls Transcribe.

The canonical reservation is consumed before PutObject. An ambiguous response,
failed cleanup, or interruption cannot be retried through this helper.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import re
import subprocess
import sys
from urllib.parse import unquote

from tools.cloud62d_aws import aws_prepare as setup, live_run as live
from tools.cloud62d_owned.corpus import CorpusStore, digest, encoded, require, utc_now
from tools.cloud62d_owned.server import _ProcessLock

PREFIX = 'output/__v4_default_kms_proof__/'
KEY = PREFIX + 'marker.bin'
CHECKS = ('output_put_no_sse_headers', 'output_head_exact_cmk', 'output_cleanup_objects_versions_multipart')
KMS_ACTIONS = ['kms:Decrypt', 'kms:Encrypt', 'kms:GenerateDataKey']
SOURCES = ('tools/cloud62d_aws/v4_proof.py', 'tools/cloud62d_aws/aws_prepare.py',
           'tools/cloud62d_aws/live_run.py', 'tools/cloud62d_aws/watchdog.py',
           'tools/cloud62d_owned/corpus.py', 'tools/cloud62d_owned/server.py')


def canonical_root(repo):
    return setup.private_path(Path(repo).resolve().parent / '.dora-62d-private' / 'v4-default-kms-proof')


def verify_publication(*, repo, config_path, published_commit, protocol, aws_path, aws_config_path, git_binary='git'):
    repo = Path(repo).resolve()
    require(isinstance(published_commit, str) and re.fullmatch('[a-f0-9]{40}', published_commit), 'PUBLICATION_COMMIT')
    require(isinstance(protocol, str) and protocol.startswith('docs/contracts/') and protocol.endswith('.json') and
            '..' not in Path(protocol).parts and '\\' not in protocol, 'PROTOCOL_PATH')
    git = lambda *args: live.git_output(repo, *args, binary=git_binary)
    require(not git('status', '--porcelain').strip(), 'DIRTY_TREE')
    require(git('symbolic-ref', '--short', 'HEAD').decode().strip() == 'chat/alpha-asr-runner-scope', 'WRONG_BRANCH')
    git('fetch', 'origin', 'chat/alpha-asr-runner-scope')
    require(git('rev-parse', 'HEAD').decode().strip() == published_commit ==
            git('rev-parse', 'refs/remotes/origin/chat/alpha-asr-runner-scope').decode().strip(), 'NOT_PUBLISHED_EXACT_COMMIT')
    source_hashes = {}
    executed = Path(__file__).resolve().parents[2]
    for source in SOURCES:
        raw = git('show', published_commit + ':' + source).replace(b'\r\n', b'\n')
        require(raw == (repo / source).read_bytes().replace(b'\r\n', b'\n') ==
                (executed / source).read_bytes().replace(b'\r\n', b'\n'), 'PUBLISHED_SOURCE_MISMATCH')
        source_hashes[source] = digest(raw)
    raw = setup.private_path(config_path).read_bytes()
    config = json.loads(raw); setup.validate_config(config)
    require(config['template_sha256'] == setup.digest_json(setup.template()), 'LOCAL_TEMPLATE_CHANGED')
    require(datetime.now(timezone.utc) < datetime.fromisoformat(config['write_deadline']), 'WRITE_DEADLINE_EXPIRED')
    statements = setup.resolved_template(config)['Resources']['OutputBucketPolicy']['Properties']['PolicyDocument']['Statement']
    require(len(statements) == 7 and not any('StringNotEqualsIfExists' in s['Condition'] for s in statements), 'V4_POLICY_REQUIRED')
    contract_raw = git('show', published_commit + ':' + protocol)
    require(contract_raw.replace(b'\r\n', b'\n') == (repo / protocol).read_bytes().replace(b'\r\n', b'\n'), 'PROTOCOL_PUBLICATION_BINDING')
    contract = json.loads(contract_raw); binding = contract['binding']
    require(contract.get('schema_version') == '0.2' and contract.get('all_synthetic') is True, 'PROTOCOL_PUBLICATION_BINDING')
    expected = {'config_sha256':digest(raw), 'template_sha256':config['template_sha256'],
                'resources_sha256':digest(encoded(config['outputs'])), 'aws_client_sha256':digest(Path(aws_path).read_bytes()),
                'aws_config_sha256':digest(setup.private_path(aws_config_path).read_bytes())}
    require(all(binding.get(key) == value for key, value in expected.items()) and
            all(binding.get('source_sha256_lf', {}).get(source) == value for source, value in source_hashes.items()), 'PROTOCOL_PUBLICATION_BINDING')
    return {'published_commit': published_commit, 'config_sha256': digest(raw),
            'template_sha256': config['template_sha256'], 'resources_sha256': digest(encoded(config['outputs'])),
            'source_sha256_lf': source_hashes, 'protocol_sha256':digest(contract_raw), 'verified_at': utc_now()}


def _document(value):
    return json.loads(unquote(value)) if isinstance(value, str) else value


def _items(value):
    return value if isinstance(value, list) else [value]


def assess_output_kms(api, config):
    """Fresh exact deployed-policy reads, not an execution/DescribeKey proof.

verify_resources checks every role policy/trust, key policy and attached policy.
Additional reads bind the reported actions to actual returned policy documents.
SCP/session/service constraints are not evaluated by this static observation.
"""
    setup.verify_resources(api, config)
    resources = setup.resolved_template(config)['Resources']
    key = config['outputs']['OutputKey']
    key_policy = _document(api.call('kms', 'get-key-policy', '--key-id', key, '--policy-name', 'default')['Policy'])
    setup.exact_policy(key_policy, resources['OutputKey']['Properties']['KeyPolicy'], 'KMS_POLICY_DRIFT')
    result = {}
    for name, logical in (('operator', 'OperatorRole'), ('data_role', 'DataRole')):
        props = resources[logical]['Properties']; principal = config['outputs'][logical]
        identity_actions, key_actions = set(), set()
        for policy in props['Policies']:
            actual = _document(api.call('iam', 'get-role-policy', '--role-name', props['RoleName'],
                                       '--policy-name', policy['PolicyName'])['PolicyDocument'])
            setup.exact_policy(actual, policy['PolicyDocument'], 'IAM_INLINE_POLICY_DRIFT')
            for statement in _items(actual['Statement']):
                if statement['Effect'] == 'Allow' and not statement.get('Condition') and key in _items(statement['Resource']):
                    identity_actions.update(_items(statement['Action']))
        for statement in _items(key_policy['Statement']):
            principals = statement.get('Condition', {}).get('ArnEquals', {}).get('aws:PrincipalArn', [])
            if (statement['Effect'] == 'Allow' and statement.get('Principal') == {'AWS': 'arn:aws:iam::' + config['account_id'] + ':root'}
                    and principal in _items(principals) and statement.get('Resource') == '*'):
                key_actions.update(_items(statement['Action']))
        require(set(KMS_ACTIONS) <= identity_actions and set(KMS_ACTIONS) <= key_actions, 'OUTPUT_KMS_ACTIONS_MISSING')
        result[name] = {'principal_arn': principal, 'key_arn': key, 'actions': KMS_ACTIONS.copy(),
                        'identity_policy': True, 'key_policy': True}
    return result


def _stack_ready(api, config):
    stacks = api.call('cloudformation', 'describe-stacks', '--stack-name', config['stack_name'])['Stacks']
    require(len(stacks) == 1 and stacks[0]['StackStatus'] == 'UPDATE_COMPLETE' and
            stacks[0]['StackName'] == config['stack_name'], 'V4_STACK_NOT_UPDATE_COMPLETE')
    stack = stacks[0]
    require({p['ParameterKey']: p['ParameterValue'] for p in stack['Parameters']} ==
            {'RunId': config['run_id'], 'OwnerPrincipalArn': config['owner_principal_arn'], 'WriteDeadline': config['write_deadline']}, 'STACK_PARAMETERS_CHANGED')
    require({p['OutputKey']: p['OutputValue'] for p in stack['Outputs']} == config['outputs'], 'STACK_OUTPUTS_CHANGED')


def _empty(api, config):
    base = ('--bucket', config['outputs']['OutputBucket'], '--expected-bucket-owner', config['account_id'], '--prefix', PREFIX)
    replies = {}
    for op, fields in (('list-objects-v2', ('Contents',)), ('list-object-versions', ('Versions', 'DeleteMarkers')),
                       ('list-multipart-uploads', ('Uploads',))):
        reply = api.call('s3api', op, *base)
        require(not any(reply.get(k) for k in ('IsTruncated', 'NextToken', 'NextContinuationToken', 'NextKeyMarker', 'NextUploadIdMarker', 'NextVersionIdMarker')), 'PROBE_READBACK_INCOMPLETE')
        require(not any(reply.get(field) for field in fields), 'PROBE_PREFIX_NOT_EMPTY')
        replies[op] = reply
    return replies


def run(proof_api, *, repo, config_path, published_commit, protocol, aws_path, aws_config_path, git_binary='git'):
    publication_args = dict(repo=repo, config_path=config_path, published_commit=published_commit, protocol=protocol,
                            aws_path=aws_path, aws_config_path=aws_config_path, git_binary=git_binary)
    publication = verify_publication(**publication_args)
    require(Path(proof_api.binary).resolve() == Path(aws_path).resolve() and proof_api.config_file is not None and
            Path(proof_api.config_file).resolve() == Path(aws_config_path).resolve(), 'PROOF_CLIENT_BINDING')
    disk = CorpusStore(canonical_root(repo), repo)
    lock = _ProcessLock(disk.root)
    operator = None
    try:
        require(not disk._path('reservation.json').exists() and not disk._path('receipt.json').exists(), 'V4_PROOF_CONSUMED')
        raw = setup.private_path(config_path).read_bytes(); config = json.loads(raw)
        require(digest(raw) == publication['config_sha256'], 'PROOF_CONFIG_CHANGED')
        setup.identity_and_optout(proof_api, config)
        _stack_ready(proof_api, config)
        kms = assess_output_kms(proof_api, config)
        base = ('--bucket', config['outputs']['OutputBucket'], '--expected-bucket-owner', config['account_id'])
        encryption = proof_api.call('s3api', 'get-bucket-encryption', *base)['ServerSideEncryptionConfiguration']['Rules']
        require(encryption == [{'ApplyServerSideEncryptionByDefault': {'SSEAlgorithm': 'aws:kms', 'KMSMasterKeyID': config['outputs']['OutputKey']}, 'BucketKeyEnabled': False}], 'OUTPUT_DEFAULT_CMK_DRIFT')
        versioning = proof_api.call('s3api', 'get-bucket-versioning', *base)
        require(not versioning.get('Status'), 'OUTPUT_VERSIONING_DRIFT')
        operator = setup.assume_operator(proof_api, config)
        identity = operator.call('sts', 'get-caller-identity')
        require(identity['Account'] == config['account_id'] and identity['Arn'].startswith(
            'arn:aws:sts::' + config['account_id'] + ':assumed-role/dora-62d-' + config['run_id'] + '-operator/'), 'OPERATOR_IDENTITY_MISMATCH')
        before = {'operator': _empty(operator, config), 'independent': _empty(proof_api, config)}
        require(datetime.now(timezone.utc) < datetime.fromisoformat(config['write_deadline']), 'WRITE_DEADLINE_EXPIRED')
        require(setup.private_path(config_path).read_bytes() == raw, 'PROOF_CONFIG_CHANGED')
        # Recheck exact publication after network reads, before the single write.
        require(verify_publication(**publication_args)['config_sha256'] == publication['config_sha256'], 'PROOF_CONFIG_CHANGED')
        disk._write('publication.json', publication, immutable=True)
        disk._write('prewrite.json', {'identity':identity, 'effective_output_kms':kms, 'empty':before, 'utc':utc_now()}, immutable=True)
        disk._write('marker.bin', b'DORA-v4-default-CMK-probe'.ljust(32, b'\0'), immutable=True)
        disk._write('reservation.json', {'maximum_puts':1, 'key':KEY, 'bucket':config['outputs']['OutputBucket'], 'published_commit':published_commit, 'utc':utc_now()}, immutable=True)
        checks = {key: 'FAIL' for key in CHECKS}; errors = []; calls = 0

        def call(op, *args):
            nonlocal calls
            number = calls; calls += 1; prefix = 'calls/' + f'{number:03}'
            disk._write(prefix + '/intent.json', {'operation':op, 'args':args, 'utc':utc_now()}, immutable=True)
            try:
                result = operator.call('s3api', op, *args)
                disk._write(prefix + '/response.json', result, immutable=True)
                return result
            except Exception as error:
                disk._write(prefix + '/error.json', {'type':type(error).__name__, 'code':str(error), 'private_detail':getattr(error,'detail',None)}, immutable=True)
                raise

        try:
            call('put-object', *base, '--key', KEY, '--body', str(disk._path('marker.bin')), '--content-type', 'application/octet-stream')
            checks[CHECKS[0]] = 'PASS'
            head = call('head-object', *base, '--key', KEY)
            require(head.get('ContentLength') == 32 and head.get('ServerSideEncryption') == 'aws:kms' and
                    head.get('SSEKMSKeyId') == config['outputs']['OutputKey'] and not head.get('BucketKeyEnabled', False), 'OUTPUT_HEAD_CMK_MISMATCH')
            checks[CHECKS[1]] = 'PASS'
        except Exception as error:
            errors.append({'phase':'put_or_head', 'type':type(error).__name__, 'code':str(error)})
        finally:
            try:
                call('delete-object', *base, '--key', KEY)
            except Exception as error:
                errors.append({'phase':'delete', 'type':type(error).__name__, 'code':str(error)})
            try:
                try:
                    call('head-object', *base, '--key', KEY)
                except setup.AwsError as error:
                    require(error.code in ('404', 'NoSuchKey', 'NotFound'), 'HEAD_ABSENCE_UNPROVEN')
                else:
                    raise ValueError('OUTPUT_OBJECT_STILL_PRESENT')
                after = {'operator':_empty(operator, config), 'independent':_empty(proof_api, config)}
                disk._write('cleanup.json', {'readbacks':after, 'head_absent':True, 'utc':utc_now()}, immutable=True)
                checks[CHECKS[2]] = 'PASS'
            except Exception as error:
                errors.append({'phase':'cleanup_readback', 'type':type(error).__name__, 'code':str(error)})
        now = datetime.now(timezone.utc)
        receipt = {k:publication[k] for k in ('published_commit', 'config_sha256', 'template_sha256', 'resources_sha256')}
        receipt.update(schema_version='v4-default-kms-proof-1', status='DIRECT_S3_PROOF_PASS' if not errors and all(v == 'PASS' for v in checks.values()) else 'V4_DEFAULT_KMS_PATH_FAILED',
                       checks=checks, effective_output_kms=kms, errors=errors, verified_at=now.isoformat(),
                       expires_at=min(now + timedelta(minutes=30), datetime.fromisoformat(config['write_deadline'])).isoformat(),
                       limits={'kms_policy':'OBSERVED_LIVE_EXACT_POLICY_NOT_FULL_EFFECTIVE_AUTHORIZATION', 'execution':'OPERATOR_S3_DEFAULT_CMK_ONLY', 'data_role_execution':'UNPROVEN', 'transcribe_execution':'UNPROVEN', 'start_calls':0})
        disk._write('receipt.json', receipt, immutable=True)
        return receipt
    finally:
        try:
            evidence = {'proof':getattr(proof_api, 'evidence', []), 'operator':getattr(operator, 'evidence', []),
                        'note':'AWS wrapper omits Credentials; full policy/stack/read replies remain private', 'utc':utc_now()}
            directory = disk._path('provider-read-evidence')
            number = len(list(directory.glob('*.json')))
            disk._write(f'provider-read-evidence/{number:04}.json', evidence, immutable=True)
        finally:
            lock.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[2])
    for name in ('config', 'aws', 'aws-config'): parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--published-commit', required=True); parser.add_argument('--git', default='git')
    parser.add_argument('--protocol', required=True)
    args = parser.parse_args()
    api = setup.Aws(args.aws, config_file=args.aws_config)
    receipt = run(api, repo=args.repo, config_path=args.config, published_commit=args.published_commit, protocol=args.protocol,
                  aws_path=args.aws, aws_config_path=args.aws_config, git_binary=args.git)
    print(json.dumps({'status':receipt['status'], 'receipt':str(canonical_root(args.repo) / 'receipt.json')}))
    if receipt['status'] != 'DIRECT_S3_PROOF_PASS': sys.exit(2)


if __name__ == '__main__':
    try: main()
    except (ValueError, setup.AwsError, OSError, subprocess.SubprocessError, KeyError, TypeError, RuntimeError):
        print(json.dumps({'status':'V4_DEFAULT_KMS_PATH_FAILED', 'error':'PROOF_BLOCKED_NO_RETRY'})); sys.exit(2)
