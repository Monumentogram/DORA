"""Offline contract/fixture checker. NOT a runtime auth or cryptographic evaluator.

Inputs are synthetic server-resolved facts, never client trust assertions. No I/O
other than reading the public catalogue; diagnostics contain closed codes only.
"""
from __future__ import annotations

import hashlib
import json
from datetime import datetime
from pathlib import Path

from validate_cloud_asr_provider_contract import require, validate_value

ROOT = Path(__file__).resolve().parents[1]
CONTRACT_PATH = 'docs/contracts/DORA_ALPHA_CLOUD_IDENTITY_AUTH_OWNERSHIP_CONTRACT_V0_1.json'
CATALOG_SHA256 = 'f592fef66e47e72eadbf057f2dcbf55c56ac0e64dc4459ecc7819397fe969a2c'


def load_json(path):
    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, 'DUPLICATE_JSON_KEY')
            result[key] = value
        return result
    return json.loads(Path(path).read_text(encoding='utf-8'), object_pairs_hook=pairs)


def contract():
    return load_json(ROOT / CONTRACT_PATH)


def validate_contract(c):
    require(type(c) is dict, 'CATALOG_TYPE')
    body = {k: val for k, val in c.items() if k != 'status'}
    digest = hashlib.sha256(json.dumps(body, sort_keys=True, separators=(',', ':'), ensure_ascii=True).encode()).hexdigest()
    require(digest == CATALOG_SHA256, 'FROZEN_CATALOG_STRUCTURE')
    require(c.get('status') in ('CONTRACT_DEFINED_AWAITING_EXACT_SHA_CI',
            'PASS / CLOUD_IDENTITY_AUTHORIZATION_OWNERSHIP_CONTRACT_READY'), 'CATALOG_STATUS')


def instant(value):
    return datetime.fromisoformat(value.replace('Z', '+00:00'))


def distinct(values):
    return len(values) == len(set(values))


def generation_vector(s):
    return dict(credential=s['credential']['generation'] if s['credential'] else 0,
                ownership=s['ownership']['version'], job=s['job']['version'] if s['job'] else 0,
                grant=s['grant']['revision'] if s['grant'] else 0,
                recording_authorization=s['recording_authorization']['revision'] if s['recording_authorization'] else 0,
                policy=s['policy']['revision'], deletion=s['deletion_epoch'],
                route=s['current_route'], source=s['current_source'])


def _case(r, s, c):
    now = instant(r['now'])
    principal, credential, owner = s['principal'], s['credential'], s['ownership']
    grant, auth, job = s['grant'], s['recording_authorization'], s['job']
    op = r['operation']
    rules = c['operations'][op]
    require(s['resolved_at'] == r['now'], 'STALE_AUTHORIZATION')
    require(principal['principal_id'] == r['principal_id'] and principal['invitation'] == 'VERIFIED'
            and credential is not None, 'NOT_AUTHENTICATED')
    require(credential['principal_id'] == r['principal_id']
            and credential['credential_ref'] == r['credential_ref']
            and credential['verification'] == credential['replay_check'] == 'VERIFIED'
            and credential['proof_request_id'] == r['request_id'], 'NOT_AUTHENTICATED')
    for state, reason in [('REVOKED', 'CREDENTIAL_REVOKED'), ('EXPIRED', 'CREDENTIAL_EXPIRED'),
                          ('SUPERSEDED', 'CREDENTIAL_SUPERSEDED')]:
        require(credential['status'] != state, reason)
    require(credential['expires_at'] is not None and instant(credential['issued_at']) <= now,
            'NOT_AUTHENTICATED')
    require(now < instant(credential['expires_at']), 'CREDENTIAL_EXPIRED')
    require(op in credential['operations'] and distinct(credential['operations']), 'INVALID_OPERATION')
    require(owner['principal_id'] == r['principal_id'] and owner['binding_ref'] == r['ownership_binding_ref']
            and owner['state'] == 'ACTIVE' and owner['verification'] == 'VERIFIED'
            and instant(owner['effective_at']) <= now, 'OWNERSHIP_MISMATCH')
    require(r['source'] == owner['source'] == s['current_source'], 'SOURCE_MISMATCH')
    require(s['recording_lifecycle'] == 'LIVE' or op == 'CANCEL_JOB', 'SOURCE_UNAVAILABLE')
    require(r['route'] == s['current_route'] and r['configuration_ref'] == s['configuration_ref'],
            'PROVIDER_SCOPE_MISMATCH')
    if rules['job_binding']:
        require(job is not None and job['verification'] == 'VERIFIED' and job['state'] == 'ACTIVE'
                and job['principal_id'] == r['principal_id'] and instant(job['created_at']) <= now
                and all(job[k] == r[k] for k in ('source', 'processing_action_id', 'processing_job_id',
                                                'configuration_ref', 'route')), 'JOB_MISMATCH')
    else:
        # Job identity reservation is not itself an ownership grant.
        require(job is None, 'JOB_MISMATCH')
    if op in ('RETRY_UPLOAD', 'RETRY_ASR'):
        require(r['prior_attempt_id'] is not None and r['prior_attempt_id'] != r['attempt_id'],
                'STALE_AUTHORIZATION')
        history = s['attempt_history']
        require(distinct([a['attempt_id'] for a in history])
                and r['attempt_id'] not in [a['attempt_id'] for a in history], 'STALE_AUTHORIZATION')
        prior = [a for a in history if a['attempt_id'] == r['prior_attempt_id']]
        require(len(prior) == 1 and prior[0]['verification'] == 'VERIFIED'
                and all(prior[0][k] == r[k] for k in ('source', 'processing_action_id',
                        'processing_job_id', 'configuration_ref', 'route')), 'STALE_AUTHORIZATION')
    if rules['consent']:
        require(grant is not None and auth is not None, 'NO_CONSENT')
        require(grant['verification'] == grant['informed'] == auth['verification'] == 'VERIFIED', 'NO_CONSENT')
        require(grant['state'] == auth['validity'] == 'ACTIVE', 'CONSENT_REVOKED')
        require(auth['state'] != 'PENDING', 'RECORDING_PENDING')
        require(auth['state'] != 'DEFERRED', 'RECORDING_DEFERRED')
        require(grant['principal_id'] == auth['principal_id'] == r['principal_id']
                and grant['grant_ref'] == auth['grant_ref'] == r['grant_ref']
                and auth['authorization_ref'] == r['recording_authorization_ref']
                and auth['source'] == r['source'], 'CONSENT_SCOPE_MISMATCH')
        scope = grant['scope']
        require(scope['scope_ref'] == auth['scope_ref'] and op in scope['operations']
                and distinct(scope['operations']), 'CONSENT_SCOPE_MISMATCH')
        require(instant(grant['granted_at']) <= instant(auth['decision_at']) <= now,
                'STALE_AUTHORIZATION')
        require(scope['route'] == r['route'], 'PROVIDER_SCOPE_MISMATCH')
        require(auth['prompt_count'] in (0, 1)
                and (auth['prompt_count'] == 1) == (auth['prompted_at'] is not None), 'NO_CONSENT')
        if auth['prompted_at'] is not None:
            require(instant(auth['prompted_at']) <= instant(auth['decision_at']), 'NO_CONSENT')
        if auth['state'] == 'INHERITED_ALWAYS':
            require(s['policy']['policy'] == 'ALWAYS' and grant['basis'] == 'INFORMED_ALWAYS', 'NO_CONSENT')
        elif auth['state'] == 'MANUAL_USER_ACTION':
            require(grant['basis'] == 'MANUAL_RECORDING' and scope['selection'] == 'EXACT_SNAPSHOT'
                    and scope['sources'] == [r['source']], 'CONSENT_SCOPE_MISMATCH')
        else:
            require(grant['basis'] in ('EXPLICIT_RECORDING', 'BATCH_SELECTION')
                    and scope['selection'] == 'EXACT_SNAPSHOT', 'CONSENT_SCOPE_MISMATCH')
        if scope['selection'] == 'ELIGIBLE_FUTURE':
            require(auth['state'] == 'INHERITED_ALWAYS' and scope['sources'] == [], 'CONSENT_SCOPE_MISMATCH')
        else:
            require(r['source'] in scope['sources']
                    and distinct([x['recording_id'] for x in scope['sources']]), 'CONSENT_SCOPE_MISMATCH')
        if s['policy']['policy'] == 'MANUAL_ONLY':
            require(r['trigger'] != 'AUTOMATIC', 'NO_CONSENT')
        batch = s['batch']
        if grant['basis'] == 'BATCH_SELECTION' or auth['batch_ref'] is not None:
            require(batch is not None, 'CONSENT_SCOPE_MISMATCH')
            presented = [x['recording_id'] for x in batch['presented']]
            require(batch['verification'] == 'VERIFIED' and batch['batch_ref'] == auth['batch_ref']
                    and batch['principal_id'] == r['principal_id']
                    and batch['grant_ref'] == grant['grant_ref'] and batch['scope_ref'] == scope['scope_ref']
                    and distinct(presented) and distinct(batch['selected'])
                    and set(batch['selected']) <= set(presented)
                    and r['source']['recording_id'] in batch['selected']
                    and r['source'] in batch['presented']
                    and instant(batch['presented_at']) <= instant(batch['decided_at']) <= now
                    and auth['prompt_count'] == 1 and auth['prompted_at'] is not None
                    and instant(auth['prompted_at']) <= instant(batch['presented_at'])
                    and (batch['presentation'] == 'USER' or auth['prompted_at'] == batch['presented_at'])
                    and auth['decision_at'] == grant['granted_at'] == batch['decided_at'],
                    'CONSENT_SCOPE_MISMATCH')
            selected_sources = [x for x in batch['presented'] if x['recording_id'] in batch['selected']]
            require(scope['sources'] == selected_sources, 'CONSENT_SCOPE_MISMATCH')
    if rules['source_available']:
        require(s['source_availability'] == 'AVAILABLE', 'SOURCE_UNAVAILABLE')
    if rules['operation_policy']:
        require(s['operation_policy'] == 'VERIFIED', 'OPERATION_POLICY_DENIED')


def check_case(request, snapshot, c):
    """Closed reason for an inert case; no credentials verified or authority issued."""
    validate_contract(c)
    if type(request) is dict and request.get('operation') not in c['enums']['Operation']:
        return 'INVALID_OPERATION'
    try:
        validate_value('AuthorizationRequest', request, c)
        validate_value('TrustedSnapshot', snapshot, c)
        _case(request, snapshot, c)
        return 'ALLOWED'
    except ValueError as error:
        code = str(error)
        return code if code in c['enums']['Reason'] and code != 'ALLOWED' else 'UNKNOWN'


def validate_decision(d, snapshot, c):
    validate_contract(c)
    validate_value('AuthorizationDecision', d, c)
    validate_value('TrustedSnapshot', snapshot, c)
    r = d['request']
    require(d['evaluated_at'] == r['now'] == snapshot['resolved_at'], 'STALE_AUTHORIZATION')
    require(d['vector'] == generation_vector(snapshot), 'STALE_AUTHORIZATION')
    require(d['credential_ref'] == r['credential_ref']
            and d['ownership_binding_ref'] == r['ownership_binding_ref']
            and d['consent_ref'] == r['grant_ref']
            and d['recording_authorization_ref'] == r['recording_authorization_ref'], 'DECISION_EVIDENCE')
    expected_scope = snapshot['grant']['scope']['scope_ref'] if snapshot['grant'] else None
    require(d['scope_ref'] == expected_scope, 'DECISION_SCOPE')
    reason = check_case(r, snapshot, c)
    require(d['reason'] == reason and d['outcome'] == ('ALLOW' if reason == 'ALLOWED' else 'DENY'),
            'DECISION_NOT_DERIVED')


def validate_upload_authority(a, issued, issuance_snapshot, current, snapshot, accepted_bytes, next_bytes, c):
    """Verify metadata preconditions only. Byte counts are inert numbers, no upload."""
    validate_contract(c)
    validate_value('ScopedUploadAuthority', a, c)
    validate_decision(issued, issuance_snapshot, c)
    validate_decision(current, snapshot, c)
    require(current['outcome'] == issued['outcome'] == 'ALLOW'
            and issued['reason'] == 'ALLOWED'
            and issued['request']['operation'] == 'ISSUE_UPLOAD_AUTHORITY'
            and a['issue_decision_ref'] == issued['decision_id'], 'UPLOAD_ISSUE_DECISION')
    require(issued['evaluated_at'] == issued['request']['now'] == a['issued_at'], 'UPLOAD_ISSUE_TIME')
    # The resolved issuance receipt remains immutable. Current independent checks
    # and the unchanged vector prevent historical permission being reused forever.
    require(a['vector'] == issued['vector'] == current['vector'], 'STALE_AUTHORIZATION')
    for field in ('principal', 'ownership', 'job', 'grant', 'recording_authorization', 'policy', 'batch'):
        require(issuance_snapshot[field] == snapshot[field], 'IMMUTABLE_EVIDENCE_REVISION')
    for field in set(snapshot['credential']) - {'proof_request_id', 'replay_check', 'verification'}:
        require(issuance_snapshot['credential'][field] == snapshot['credential'][field],
                'IMMUTABLE_EVIDENCE_REVISION')
    for field in ('principal_id', 'source', 'processing_action_id', 'processing_job_id'):
        require(a[field] == issued['request'][field] == current['request'][field], 'UPLOAD_RESOURCE_SCOPE')
    for field in ('credential_ref', 'ownership_binding_ref', 'recording_authorization_ref',
                  'grant_ref', 'configuration_ref', 'route'):
        require(issued['request'][field] == current['request'][field], 'UPLOAD_CONTEXT_SCOPE')
    require(a['grant_ref'] == issued['consent_ref'] == current['consent_ref'], 'UPLOAD_CONSENT')
    require(a['validity'] == 'ACTIVE' and distinct(a['operations'])
            and set(a['operations']) <= {'UPLOAD_AUDIO', 'RETRY_UPLOAD'}
            and current['request']['operation'] in a['operations'], 'UPLOAD_OPERATION')
    now = instant(current['evaluated_at'])
    require(instant(a['issued_at']) <= now < instant(a['expires_at'])
            and instant(a['expires_at']) <= instant(snapshot['credential']['expires_at']), 'UPLOAD_EXPIRY')
    require(type(accepted_bytes) is int and accepted_bytes >= 0
            and type(next_bytes) is int and next_bytes > 0
            and a['max_bytes'] is not None and accepted_bytes + next_bytes <= a['max_bytes'], 'UPLOAD_BYTE_CEILING')


def validate_prompt_transition(before, after, event, c):
    validate_contract(c)
    for record in (before, after):
        validate_value('PromptRecord', record, c)
        require(record['prompt_count'] in (0, 1)
                and (record['prompt_count'] == 1) == (record['prompted_at'] is not None), 'PROMPT_MARKER')
    require(before['source'] == after['source'], 'PROMPT_SOURCE')
    if event in ('RESTART', 'POLICY_TOGGLE', 'RECONNECT', 'CHUNK'):
        require(before == after, 'PROMPT_HISTORY_PRESERVED')
    else:
        require(event == 'PRESENT' and before['state'] == after['state'] == 'PENDING'
                and before['prompt_count'] == 0 and after['prompt_count'] == 1, 'PROMPT_ONCE')


def validate_batch_transition(batch, before, after, presentation, c):
    """All presented records, not just the one whose operation is requested."""
    validate_contract(c)
    validate_value('BatchAuthorizationSnapshot', batch, c)
    validate_value('[]PromptRecord', before, c)
    validate_value('[]PromptRecord', after, c)
    require(presentation == batch['presentation'] and batch['verification'] == 'VERIFIED', 'BATCH_EVIDENCE')
    sources = batch['presented']
    ids = [s['recording_id'] for s in sources]
    require(bool(ids) and distinct(ids) and distinct(batch['selected'])
            and set(batch['selected']) <= set(ids), 'BATCH_SELECTION')
    require([p['source'] for p in before] == sources == [p['source'] for p in after], 'BATCH_SNAPSHOT')
    require(instant(batch['presented_at']) <= instant(batch['decided_at']), 'BATCH_TIME')
    for old, new in zip(before, after):
        require(old['prompt_count'] in (0, 1)
                and (old['prompt_count'] == 1) == (old['prompted_at'] is not None), 'PROMPT_MARKER')
        if presentation == 'AUTOMATIC':
            require(old['state'] == 'PENDING' and old['prompt_count'] == 0, 'PROMPT_ONCE')
        else:
            require(old['state'] in ('PENDING', 'DEFERRED'), 'BATCH_PRIOR_STATE')
        expected_time = old['prompted_at'] or batch['presented_at']
        require(new['prompt_count'] == 1 and new['prompted_at'] == expected_time
                and instant(expected_time) <= instant(batch['decided_at']), 'BATCH_PROMPT_HISTORY')
        expected_state = 'EXPLICITLY_APPROVED' if old['source']['recording_id'] in batch['selected'] else 'DEFERRED'
        require(new['state'] == expected_state, 'BATCH_RECORDING_STATE')


def validate_job_binding(binding, creation, creation_snapshot, c):
    """Check a proposed immutable binding derives from authorized creation."""
    validate_contract(c)
    validate_value('CloudJobOwnershipBinding', binding, c)
    validate_decision(creation, creation_snapshot, c)
    require(creation['outcome'] == 'ALLOW' and creation['request']['operation'] == 'CREATE_CLOUD_JOB'
            and binding['creation_decision_ref'] == creation['decision_id']
            and binding['verification'] == 'VERIFIED' and binding['state'] == 'ACTIVE'
            and binding['version'] == 1 and binding['created_at'] == creation['evaluated_at'], 'JOB_CREATION')
    require(all(binding[k] == creation['request'][k] for k in ('principal_id', 'source',
                'processing_action_id', 'processing_job_id', 'configuration_ref', 'route')), 'JOB_CREATION_SCOPE')


def main():
    try:
        validate_contract(contract())
    except (ValueError, OSError):
        print('FAIL / CLOUD_IDENTITY_CONTRACT_INVALID')
        return 1
    print('PASS / CLOUD_IDENTITY_LOGICAL_CATALOG_VALID; RUNTIME_NOT_IMPLEMENTED')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())
