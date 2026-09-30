"""Synthetic non-production values adapted from frozen contract test fixtures. No I/O except catalogue reads."""
import copy
import json
import validate_cloud_identity_auth_contract as v
NOW = "2026-09-30T01:00:00Z"

def uid(n):
    return f'00000000-0000-0000-0000-{n:012d}'

def authorization_fixture(operation='DISPATCH_ASR'):
    source = dict(recording_id=uid(1), audio_asset_id=uid(2), sha256='a'*64)
    route = dict(route_id=uid(3), provider_id=uid(4), profile_id=uid(5),
                 profile_version=1, destination_id=uid(6), region_id=uid(7))
    start, now, end = '2026-09-30T00:00:00Z', '2026-09-30T01:00:00Z', '2026-09-30T02:00:00Z'
    principal = dict(principal_id=uid(10), installation_ref=uid(11), kind='INSTALLATION',
                     invitation='VERIFIED', key_binding_ref=uid(12))
    credential = dict(credential_ref=uid(13), principal_id=uid(10), issuer='DORA_CONTROL_PLANE',
                      issued_at=start, expires_at=end, status='ACTIVE', generation=1,
                      audience='DORA_CLOUD_CONTROL_PLANE', operations=list(v.contract()['enums']['Operation']),
                      replaces_ref=None, verification='VERIFIED', proof_request_id=uid(14), replay_check='VERIFIED')
    owner = dict(binding_ref=uid(15), principal_id=uid(10), source=source, version=1,
                 effective_at=start, state='ACTIVE', provenance_ref=uid(16), verification='VERIFIED')
    scope = dict(scope_ref=uid(17), disclosure_version=1, purpose='CLOUD_ASR',
                 selection='EXACT_SNAPSHOT', sources=[source], route=route,
                 operations=list(v.contract()['enums']['Operation']))
    grant = dict(grant_ref=uid(18), principal_id=uid(10), scope=scope, granted_at=start,
                 revision=1, state='ACTIVE', informed='VERIFIED', basis='EXPLICIT_RECORDING', verification='VERIFIED')
    auth = dict(authorization_ref=uid(19), principal_id=uid(10), source=source,
                state='EXPLICITLY_APPROVED', grant_ref=uid(18), scope_ref=uid(17), revision=1,
                decision_at=start, prompt_count=1, prompted_at=start, batch_ref=None,
                validity='ACTIVE', verification='VERIFIED')
    job = dict(binding_ref=uid(20), principal_id=uid(10), source=source,
               processing_action_id=uid(21), processing_job_id=uid(22), configuration_ref=uid(23),
               route=route, creation_decision_ref=uid(24), created_at=start, version=1,
               state='ACTIVE', verification='VERIFIED')
    request = dict(request_id=uid(14), principal_id=uid(10), credential_ref=uid(13),
                   ownership_binding_ref=uid(15), recording_authorization_ref=uid(19), grant_ref=uid(18),
                   source=source, processing_action_id=uid(21), processing_job_id=uid(22),
                   configuration_ref=uid(23), route=route, operation=operation, now=now,
                   attempt_id=uid(25), prior_attempt_id=None, trigger='USER')
    snapshot = dict(principal=principal, credential=credential, ownership=owner, grant=grant,
                    recording_authorization=auth, policy=dict(policy='ASK_EACH_RECORDING', revision=1),
                    job=None if operation=='CREATE_CLOUD_JOB' else job, batch=None,
                    current_source=source, source_availability='AVAILABLE', deletion_epoch=0,
                    recording_lifecycle='LIVE', current_route=route, configuration_ref=uid(23),
                    operation_policy='VERIFIED', resolved_at=now, attempt_history=[])
    # Break aliases so a single mutation never silently changes other evidence.
    return json.loads(json.dumps(request)), json.loads(json.dumps(snapshot))

def unknown():
    return {'availability': 'UNKNOWN', 'value': None}

def stamp():
    return {'availability': 'UNAVAILABLE', 'start_us': None, 'end_us': None, 'quality': 'UNKNOWN'}

def source():
    return {'recording_id': uid(1), 'audio_asset_id': uid(2), 'sha256': 'a' * 64}

def revision(n):
    return {'recording_id': uid(1), 'ordinal': n}

def version(n, engine):
    processing = {k: unknown() for k in ('implementation_version', 'route_id', 'provider_id',
        'provider_version', 'model_id', 'model_version', 'adapter_profile_id', 'adapter_profile_version')}
    processing['implementation_id'] = 'synthetic-engine'
    if engine == 'LOCAL':
        for k in ('route_id', 'provider_id', 'provider_version', 'adapter_profile_id', 'adapter_profile_version'):
            processing[k] = {'availability': 'NOT_APPLICABLE', 'value': None}
    return {'transcript_id': uid(10 + n), 'recording_id': uid(1), 'source': source(), 'version_order': n,
            'engine': engine, 'processing_status': 'VALID', 'processing': processing,
            'raw_provenance': {'processing_action_id': uid(40+n), 'processing_job_id': uid(50+n),
                'idempotency_key': uid(60+n), 'attempt_id': uid(70+n), 'configuration_ref': uid(80+n),
                'generation': n, 'provider_operation_ref': unknown()}, 'merge_provenance': None,
            'created_at': NOW, 'processed_at': unknown(), 'text': 'alpha',
            'segments': [{'segment_id': uid(30), 'transcript_id': uid(10+n), 'order': 0,
                          'source': source(), 'text': 'alpha', 'timestamp': stamp()}]}

def anchor(transcript=11):
    return {'source': source(), 'base_transcript_id': uid(transcript),
            'segment_ref': {'transcript_id': uid(transcript), 'segment_id': uid(30)},
            'timestamp': stamp(), 'context': {'before': '', 'target': 'alpha', 'after': '', 'boundary': 'NONE'},
            'char_range': None}

def edit(n=1, base=11):
    return {'edit_id': uid(100+n), 'recording_id': uid(1), 'base_transcript_id': uid(base),
            'base_edit_revision': revision(n-1), 'revision': revision(n), 'actor': 'USER',
            'created_at': NOW, 'kind': 'REPLACE', 'anchor': anchor(base), 'payload': 'beta',
            'mapping_state': 'EXACT', 'conflict': 'NONE'}

def initial():
    d = {'decision_id': uid(200), 'recording_id': uid(1), 'from_transcript_id': None,
         'target_transcript_id': uid(11), 'proposal_id': None, 'base_edit_revision': revision(0),
         'base_selection_revision': 0, 'actor': 'SYSTEM', 'created_at': NOW,
         'kind': 'AUTO_INITIAL', 'outcome': 'ACCEPTED', 'reason': 'EXPECTED_RESULT', 'resolution_ids': []}
    return {'recording_id': uid(1), 'lifecycle': 'LIVE',
            'sources': [{'source': source(), 'duration_us': 1000000, 'availability': 'AVAILABLE'}],
            'current_source': source(), 'versions': [version(1, 'LOCAL'), version(2, 'CLOUD')],
            'edits': [], 'edit_revision': revision(0), 'alignments': [], 'proposals': [],
            'resolutions': [], 'decisions': [d],
            'selection': {'recording_id': uid(1), 'transcript_id': uid(11), 'selection_revision': 1,
                          'decision_id': uid(200), 'mode': 'AUTOMATIC'},
            'processing_history': [{'source': source(), 'processing_action_id': uid(41), 'generation': 1},
                                   {'source': source(), 'processing_action_id': uid(42), 'generation': 2}],
            'expected_processing': {'source': source(), 'processing_action_id': uid(42), 'generation': 2}}

def proposed(state='EXACT'):
    s = initial()
    s['edits'] = [edit()]
    s['edit_revision'] = revision(1)
    conflict = {'EXACT': 'NONE', 'MAPPED': 'NONE', 'AMBIGUOUS': 'AMBIGUOUS_MAPPING',
                'UNMAPPED': 'UNMAPPED_EDIT', 'CONFLICTED': 'MANUAL_REVIEW_REQUIRED'}[state]
    safe = state in ('EXACT', 'MAPPED')
    s['alignments'] = [{'alignment_id': uid(300), 'recording_id': uid(1), 'source': source(),
        'source_transcript_ids': [uid(11)], 'target_transcript_id': uid(12),
        'base_edit_revision': revision(1), 'method_id': 'synthetic-declared-evidence',
        'method_version': unknown(), 'mappings': [{'edit_id': uid(101),
            'target_anchor': anchor(12) if state != 'UNMAPPED' else None, 'state': state,
            'conflict': conflict, 'basis': 'UNIQUE_CONTEXT' if safe else 'INSUFFICIENT'}]}]
    s['proposals'] = [{'proposal_id': uid(400), 'recording_id': uid(1), 'source': source(),
        'base_transcript_id': uid(11), 'candidate_transcript_id': uid(12),
        'base_edit_revision': revision(1), 'base_selection_revision': 1,
        'alignment_id': uid(300), 'mapped_edit_ids': [uid(101)] if safe else [],
        'unmapped_edit_ids': [uid(101)] if state == 'UNMAPPED' else [],
        'conflicted_edit_ids': [uid(101)] if state in ('AMBIGUOUS', 'CONFLICTED') else [],
        'created_at': NOW, 'creation_state': 'READY' if safe else 'NEEDS_REVIEW'}]
    return s

def merged():
    s = proposed()
    v = version(3, 'MERGED')
    v['raw_provenance'] = None
    v['merge_provenance'] = {'proposal_id': uid(400), 'base_edit_revision': revision(1),
                           'applied_edit_ids': [uid(101)], 'resolution_ids': [],
                           'parent_transcript_ids': [uid(11), uid(12)]}
    v['text'] = 'beta'
    v['segments'][0]['text'] = 'beta'
    s['versions'].append(v)
    return s

def decision(s, kind='AUTO_CLOUD_REPLACE', target=12):
    return {'decision_id': uid(201), 'recording_id': uid(1),
            'from_transcript_id': s['selection']['transcript_id'],
            'target_transcript_id': uid(target) if target else None,
            'proposal_id': uid(400) if kind in ('ACCEPT_PROPOSAL', 'REJECT_PROPOSAL') else None,
            'base_edit_revision': copy.deepcopy(s['edit_revision']),
            'base_selection_revision': s['selection']['selection_revision'],
            'actor': 'SYSTEM' if kind.startswith('AUTO') else 'USER', 'created_at': NOW,
            'kind': kind, 'outcome': 'REJECTED' if kind == 'REJECT_PROPOSAL' else 'ACCEPTED',
            'reason': {'AUTO_CLOUD_REPLACE': 'EXPECTED_RESULT', 'AUTO_INITIAL': 'EXPECTED_RESULT',
                       'ACCEPT_PROPOSAL': 'USER_ACCEPTED', 'REJECT_PROPOSAL': 'USER_REJECTED',
                       'MANUAL_SELECT': 'USER_SELECTED_VERSION', 'CLEAR_SELECTION': 'USER_CLEARED'}[kind],
            'resolution_ids': []}

def applied(s, d):
    after = copy.deepcopy(s)
    after['decisions'].append(d)
    if d['outcome'] == 'ACCEPTED':
        after['selection'] = {'recording_id': uid(1), 'transcript_id': d['target_transcript_id'],
            'selection_revision': s['selection']['selection_revision'] + 1,
            'decision_id': d['decision_id'], 'mode': 'AUTOMATIC' if d['actor'] == 'SYSTEM' else 'MANUAL'}
    return after
