"""Hermetic synthetic 7.3C composition model. NOT backend/runtime/authentication.

Only frozen catalogue/source reads and optional sanitized report writes use files.
All scenario time, network, byte transfer, provider and ledger state is in memory.
"""
from __future__ import annotations

import argparse
import ast
from copy import deepcopy
from datetime import datetime, timedelta
import hashlib
import json
from pathlib import Path

import cloud_contract_fixtures as f
import validate_cloud_identity_auth_contract as auth
import validate_cloud_asr_provider_contract as provider
import validate_transcript_versioning_contract as transcript

ROOT = Path(__file__).resolve().parents[1]
CATALOG = ROOT / 'docs/contracts/DORA_ALPHA_CLOUD_CONTRACT_HARNESS_V0_1.json'
require = provider.require
COUNTERS = ('logical_actions', 'logical_jobs', 'attempts', 'provider_submits',
            'upload_authorities', 'accepted_bytes', 'rejected_bytes',
            'results_published', 'active_selection_moves')


def digest(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


class FakeClock:
    """Manual logical microsecond clock; never samples wall time."""
    def __init__(self, initial='2026-09-30T01:00:00Z'):
        self.initial = datetime.fromisoformat(initial.replace('Z', '+00:00'))
        self.ticks = 0

    def advance(self, microseconds):
        require(type(microseconds) is int and microseconds >= 0, 'CLOCK_ADVANCE')
        self.ticks += microseconds

    def utc(self, offset=0):
        return (self.initial + timedelta(microseconds=self.ticks + offset)).isoformat().replace('+00:00', 'Z')


class SyntheticNetwork:
    """Inert ordered event queue. No sockets, connection or HTTP implementation."""
    def __init__(self, clock, capture):
        self.clock, self.events, self.sequence = clock, [], 0
        self.capture = capture

    def enqueue(self, event, delay=0, copies=1):
        require(event in ('PROCESSING', 'OUTPUT_AVAILABLE', 'SUCCEEDED', 'RECONNECT',
            'REQUEST_DELIVERED', 'REQUEST_REJECTED', 'RESPONSE_LOST', 'RESPONSE_DELIVERED',
            'TRANSPORT_BEFORE_DISPATCH', 'TRANSPORT_AFTER_ACCEPTANCE'), 'NETWORK_EVENT')
        require(type(delay) is int and delay >= 0 and type(copies) is int and copies > 0, 'NETWORK_ORDER')
        for _ in range(copies):
            self.sequence += 1
            self.events.append((self.clock.ticks + delay, self.sequence, event, self.capture()))

    def ready(self):
        ready = sorted(e for e in self.events if e[0] <= self.clock.ticks)
        self.events = [e for e in self.events if e[0] > self.clock.ticks]
        return [(e[2], e[3]) for e in ready]


class SyntheticByteStore:
    """Conceptual byte counts only. One cumulative ledger per logical source/job."""
    def __init__(self):
        self.accepted = 0
        self.parts = []

    def record(self, authority, request, size, reason):
        accepted = size if reason == 'ACCEPTED' else 0
        self.accepted += accepted
        self.parts.append(dict(authority_id=authority['authority_ref'] if authority else None,
            source=deepcopy(request['source']), accepted_bytes=accepted, rejected_bytes=size-accepted,
            cumulative_bytes=self.accepted, part_number=len(self.parts)+1,
            attempt=request['attempt_id'], generation=deepcopy(authority['vector']) if authority else None,
            expiry=authority['expires_at'] if authority else None))


class SyntheticProvider:
    """In-memory provider fact table; NOT an SDK or provider idempotency promise."""
    def __init__(self):
        self.operations = {}
        self.nonacceptance = set()
        self.submits = 0
        self.reconciliations = 0

    def dispatch(self, request, namespace, mode):
        modes = {'ACCEPTED','LOST_ACCEPTED','LOST_NOT_ACCEPTED','CONFLICT',
                 'INVALID_REQUEST','SOURCE_UNAVAILABLE','TRANSIENT_REJECTED','BEFORE_DISPATCH'}
        require(mode in modes, 'SUBMIT_MODE')
        if mode == 'BEFORE_DISPATCH':
            return None
        self.submits += 1
        attempt = request['attempt_id']
        if mode in ('ACCEPTED','LOST_ACCEPTED','CONFLICT'):
            bound = {k: request[k] for k in ('processing_action_id','processing_job_id','attempt_id')}
            bound.update(source=deepcopy(request['audio']['source']), provider_namespace=deepcopy(namespace),
                provider=dict(operation_ref='synthetic-operation-'+str(self.submits),
                              request_ref=f.unknown(), result_ref=f.unknown()))
            provider.validate_operation(bound, request, provider.load_json(ROOT / provider.CONTRACT_PATH))
            self.operations[attempt] = deepcopy(bound)
            return bound
        self.nonacceptance.add(attempt)
        return None

    def reconcile(self, request):
        self.reconciliations += 1
        attempt = request['attempt_id']
        if attempt in self.operations:
            return 'FOUND', deepcopy(self.operations[attempt])
        if attempt in self.nonacceptance:
            return 'PROVEN_NOT_ACCEPTED', None
        return 'UNKNOWN', None


class SyntheticControlPlaneLedger:
    """Test-only serialized composition; no durable queue, service or auth runtime."""
    def __init__(self, scenario='synthetic'):
        self.scenario = scenario
        self.clock = FakeClock()
        self.network = SyntheticNetwork(self.clock, self.capture_delivery)
        self.store = SyntheticByteStore()
        self.provider = SyntheticProvider()
        self.ac = auth.contract()
        self.pc = provider.load_json(ROOT / provider.CONTRACT_PATH)
        self.tc = transcript.load_json(ROOT / transcript.CONTRACT_PATH)
        self.context, self.snapshot = f.authorization_fixture('CREATE_CLOUD_JOB')
        self.transcript = f.initial()
        self.transcript['versions'] = self.transcript['versions'][:1]
        self.transcript['processing_history'][1]['processing_action_id'] = self.context['processing_action_id']
        self.transcript['expected_processing'] = deepcopy(self.transcript['processing_history'][1])
        transcript.validate_snapshot(self.transcript, self.tc)
        self.counts = dict.fromkeys(COUNTERS, 0)
        self.state, self.cancellation = 'NEW', 'NONE'
        self.retry_disposition, self.retry_at = 'DO_NOT_RETRY', 0
        self.authority = self.issued = self.issuance_snapshot = None
        self.request = self.bound = self.winner = None
        self.saved_activation = None
        self.requests = {}
        self.dispatch_namespaces = {}
        self.trace = []
        self.serial = 1000

    def allocate(self):
        self.serial += 1
        return f.uid(self.serial)

    def event(self, kind, reason):
        self.trace.append(dict(event=kind, at=self.clock.ticks, reason=reason,
            state=self.state, cancellation=self.cancellation,
            accepted_bytes=self.counts['accepted_bytes'], rejected_bytes=self.counts['rejected_bytes']))
        return reason

    def authorize(self, operation, retry=False):
        r = deepcopy(self.context)
        r.update(operation=operation, now=self.clock.utc(), request_id=self.allocate())
        if retry:
            prior = self.context['attempt_id']
            r.update(attempt_id=self.allocate(), prior_attempt_id=prior)
            history = {k:deepcopy(self.context[k]) for k in ('source','processing_action_id',
                       'processing_job_id','configuration_ref','route')}
            history.update(attempt_id=prior, verification='VERIFIED')
            if not any(a['attempt_id'] == prior for a in self.snapshot['attempt_history']):
                self.snapshot['attempt_history'].append(history)
        s = deepcopy(self.snapshot)
        s['resolved_at'] = r['now']
        if s['credential'] is not None:
            s['credential']['proof_request_id'] = r['request_id']
        reason = auth.check_case(r, s, self.ac)
        d = dict(decision_id=self.allocate(), request=r, credential_ref=r['credential_ref'],
            ownership_binding_ref=r['ownership_binding_ref'], consent_ref=r['grant_ref'],
            scope_ref=s['grant']['scope']['scope_ref'] if s['grant'] else None,
            recording_authorization_ref=r['recording_authorization_ref'],
            vector=auth.generation_vector(s), evaluated_at=r['now'],
            outcome='ALLOW' if reason == 'ALLOWED' else 'DENY', reason=reason)
        auth.validate_decision(d, s, self.ac)
        self.event(operation, reason)
        return d, s

    def create_job(self):
        if self.snapshot['job'] is not None:
            return self.event('CREATE_CLOUD_JOB', 'ALREADY_BOUND')
        d, s = self.authorize('CREATE_CLOUD_JOB')
        if d['outcome'] != 'ALLOW': return d['reason']
        binding = {k:deepcopy(d['request'][k]) for k in ('principal_id','source',
                   'processing_action_id','processing_job_id','configuration_ref','route')}
        binding.update(binding_ref=self.allocate(), creation_decision_ref=d['decision_id'],
                       created_at=d['evaluated_at'], version=1, state='ACTIVE', verification='VERIFIED')
        self.bind_job(binding, d, s)
        self.creation, self.creation_snapshot = deepcopy(d), deepcopy(s)
        self.counts['logical_actions'] += 1; self.counts['logical_jobs'] += 1
        self.state = 'CREATED'
        return 'ALLOWED'

    def bind_job(self, binding, decision, snapshot):
        auth.validate_job_binding(binding, decision, snapshot, self.ac)
        require(self.snapshot['job'] is None, 'JOB_ALREADY_BOUND')
        self.snapshot['job'] = deepcopy(binding)

    def issue(self):
        d, s = self.authorize('ISSUE_UPLOAD_AUTHORITY')
        if d['outcome'] != 'ALLOW': return d['reason']
        a = {k:deepcopy(d['request'][k]) for k in ('principal_id','source','processing_action_id','processing_job_id','grant_ref')}
        a.update(authority_ref=self.allocate(), operations=['UPLOAD_AUDIO','RETRY_UPLOAD'], max_bytes=1000,
            issued_at=d['evaluated_at'], expires_at=self.clock.utc(1800_000_000),
            issue_decision_ref=d['decision_id'], vector=deepcopy(d['vector']), validity='ACTIVE')
        auth.validate_value('ScopedUploadAuthority', a, self.ac)
        self.authority, self.issued, self.issuance_snapshot = a, deepcopy(d), deepcopy(s)
        self.counts['upload_authorities'] += 1
        return 'ALLOWED'

    def part(self, size, retry=False):
        require(type(size) is int and size > 0, 'PART_SIZE')
        d, s = self.authorize('RETRY_UPLOAD' if retry else 'UPLOAD_AUDIO', retry)
        reason = d['reason']
        if reason == 'ALLOWED':
            try:
                require(self.authority is not None, 'NO_UPLOAD_AUTHORITY')
                auth.validate_upload_authority(self.authority, self.issued, self.issuance_snapshot,
                    d, s, self.store.accepted, size, self.ac)
                reason = 'ACCEPTED'
            except ValueError as error:
                reason = str(error)
        self.store.record(self.authority, d['request'], size, reason)
        self.counts['accepted_bytes'] = self.store.accepted
        if reason != 'ACCEPTED': self.counts['rejected_bytes'] += size
        if retry and reason == 'ACCEPTED': self.context['attempt_id'] = d['request']['attempt_id']
        return self.event('PART', reason)

    def namespace(self):
        route = self.context['route']
        return dict(route_id=route['route_id'], provider_id=route['provider_id'],
            adapter_profile_id=route['profile_id'], adapter_profile_version=str(route['profile_version']))

    def submit(self, mode='ACCEPTED', retry=False):
        if self.cancellation != 'NONE' or self.winner is not None:
            return self.event('SUBMIT', 'DO_NOT_RETRY')
        if self.request is not None and not retry:
            return self.event('SUBMIT', 'ALREADY_DISPATCHED')
        if retry:
            if self.retry_disposition != 'RETRYABLE':
                return self.event('SUBMIT', self.retry_disposition)
            if self.clock.ticks < self.retry_at:
                return self.event('SUBMIT', 'BACKOFF')
        d, s = self.authorize('RETRY_ASR' if retry else 'DISPATCH_ASR', retry)
        if d['outcome'] != 'ALLOW': return d['reason']
        require(self.store.accepted > 0, 'SOURCE_NOT_UPLOADED')
        r = dict(processing_action_id=d['request']['processing_action_id'],
            processing_job_id=d['request']['processing_job_id'], idempotency_key=f.uid(900),
            attempt_id=d['request']['attempt_id'], trace_id=self.allocate(),
            audio=dict(kind='ORIGINAL_AUDIO', source=deepcopy(d['request']['source']), access_ref=f.uid(901)),
            properties=dict(container='SYNTHETIC', encoding='BYTE_COUNT_ONLY', duration_us=1000000, sample_rate_hz=16000, channels=1),
            configuration=dict(language='ru-RU', configuration_ref=d['request']['configuration_ref']),
            authorization=dict(authorization_ref=d['decision_id'], consent_scope_ref=d['scope_ref'],
                               ownership_binding_ref=d['ownership_binding_ref']))
        provider.validate_request(r, self.pc)
        if self.request is not None: provider.validate_retry_attempt(self.request, r, self.pc)
        self.request = deepcopy(r); self.requests[r['attempt_id']] = deepcopy(r)
        self.dispatch_namespaces[r['attempt_id']] = deepcopy(self.namespace())
        self.context['attempt_id'] = r['attempt_id']
        self.counts['attempts'] += 1
        if self.saved_activation is None:
            self.saved_activation = f.decision(self.transcript)
            self.saved_activation['decision_id'] = self.allocate()
        bound = self.provider.dispatch(r, self.namespace(), mode)
        self.counts['provider_submits'] = self.provider.submits
        self.bound = deepcopy(bound) if mode == 'ACCEPTED' else None
        if mode == 'ACCEPTED':
            self.retry_disposition = 'DO_NOT_RETRY'
            return self.observe('ACCEPTED')
        category, effect = {
            'LOST_ACCEPTED':('SUBMISSION_UNCERTAIN','POSSIBLY_ACCEPTED'),
            'LOST_NOT_ACCEPTED':('SUBMISSION_UNCERTAIN','POSSIBLY_ACCEPTED'),
            'CONFLICT':('DUPLICATE_CONFLICT','POSSIBLY_ACCEPTED'),
            'INVALID_REQUEST':('INVALID_REQUEST','DEFINITELY_REJECTED'),
            'SOURCE_UNAVAILABLE':('SOURCE_UNAVAILABLE','DEFINITELY_REJECTED'),
            'TRANSIENT_REJECTED':('RATE_LIMITED','DEFINITELY_REJECTED'),
            'BEFORE_DISPATCH':('TRANSIENT_TRANSPORT','NOT_DISPATCHED')}[mode]
        return self.failure(category, effect, 'submit')

    def failure(self, category, effect, operation):
        retry = provider.retry_disposition(category, effect, operation)
        state = 'UNKNOWN' if retry == 'RECONCILE_BEFORE_RETRY' else 'FAILED'
        failure = dict(category=category, operation=operation, dispatch_effect=effect,
                       retry_disposition=retry, retry_after_us=dict(availability='KNOWN', value=10))
        o = dict(state=state, cancellation=self.cancellation, operation=self.bound, failure=failure, result=None)
        provider.validate_observation(o, self.request, self.pc)
        self.state, self.retry_disposition = state, retry
        self.retry_at = self.clock.ticks + 10
        return self.event('FAILURE', retry)

    def retry(self):
        if self.cancellation != 'NONE' or self.winner is not None:
            return self.event('RETRY', 'DO_NOT_RETRY')
        if self.retry_disposition != 'RETRYABLE':
            return self.event('RETRY', self.retry_disposition)
        if self.clock.ticks < self.retry_at: return self.event('RETRY', 'BACKOFF')
        return self.submit(retry=True)

    def reconcile(self):
        require(self.request is not None, 'NO_DISPATCH')
        d, _ = self.authorize('READ_JOB_STATUS')
        if d['outcome'] != 'ALLOW': return d['reason']
        outcome, bound = self.provider.reconcile(self.request)
        if outcome == 'FOUND':
            provider.validate_operation(bound, self.request, self.pc)
            require(bound['provider_namespace'] == self.namespace(), 'PROVIDER_NAMESPACE')
            self.bound = bound; self.retry_disposition = 'DO_NOT_RETRY'
            self.observe('ACCEPTED')
        elif (outcome == 'PROVEN_NOT_ACCEPTED' and self.bound is None and self.winner is None
              and self.cancellation == 'NONE' and self.retry_disposition == 'RECONCILE_BEFORE_RETRY'):
            self.retry_disposition = 'RETRYABLE'; self.retry_at = self.clock.ticks + 10
        return self.event('RECONCILE', outcome)

    def observe(self, state):
        if self.state in ('SUCCEEDED',) or self.cancellation == 'CONFIRMED':
            return self.event('OBSERVATION', 'TERMINAL_RETAINED')
        o = dict(state=state, cancellation=self.cancellation, operation=self.bound, failure=None, result=None)
        provider.validate_observation(o, self.request, self.pc)
        ranks = {'ACCEPTED':1, 'PROCESSING':2, 'OUTPUT_AVAILABLE':3}
        if ranks.get(state, 0) < ranks.get(self.state, 0):
            return self.event('OBSERVATION', 'STALE_OBSERVATION')
        self.state = state
        return self.event('OBSERVATION', state)

    def cloud_result(self):
        require(self.bound is not None, 'NO_BOUND_OPERATION')
        return dict(transcript_id=f.uid(12),
            **{k:self.request[k] for k in ('processing_action_id','processing_job_id','idempotency_key','attempt_id')},
            source=deepcopy(self.request['audio']['source']), configuration=deepcopy(self.request['configuration']),
            processing=dict(engine='CLOUD', **self.bound['provider_namespace'], provider_version=f.unknown(),
                            model_id=f.unknown(), model_version=f.unknown()),
            provider=deepcopy(self.bound['provider']), text='', segments=[],
            created_at=f.NOW, processed_at=f.unknown(), usage_duration_us=f.unknown())

    def complete(self, mutation=None, received=None):
        if self.cancellation == 'CONFIRMED' or self.snapshot['recording_lifecycle'] == 'TOMBSTONED':
            return self.event('RESULT', 'FENCED')
        if self.bound is None: return self.event('RESULT', 'NO_BOUND_OPERATION')
        out = self.cloud_result() if received is None else deepcopy(received['result'])
        if mutation == 'namespace': out['processing']['provider_id'] = 'other-provider'
        elif mutation == 'source': out['source']['audio_asset_id'] = f.uid(999)
        elif mutation == 'job': out['processing_job_id'] = f.uid(999)
        elif mutation == 'attempt': out['attempt_id'] = f.uid(999)
        elif mutation == 'configuration': out['configuration']['configuration_ref'] = f.uid(999)
        elif mutation == 'mapping': out.pop('text')
        try:
            operation = self.bound if received is None else received['bound']
            require(operation is not None and out is not None, 'NO_BOUND_RESULT')
            require(operation['provider_namespace'] == self.dispatch_namespaces[self.request['attempt_id']],
                    'DISPATCH_NAMESPACE')
            observation = dict(state='SUCCEEDED', cancellation=self.cancellation,
                               operation=operation, failure=None, result=out)
            provider.validate_observation(observation, self.request, self.pc)
        except ValueError:
            return self.event('RESULT', 'MAPPING_FAILURE')
        if self.winner is not None:
            return self.event('RESULT', 'DUPLICATE' if self.winner == out else 'RESULT_CONFLICT')
        before = self.transcript
        after = deepcopy(before)
        version = f.version(2, 'CLOUD')
        version.update(transcript_id=out['transcript_id'], source=deepcopy(out['source']),
                       text=out['text'], segments=[], created_at=out['created_at'])
        raw = version['raw_provenance']
        raw.update({k:out[k] for k in ('processing_action_id','processing_job_id','idempotency_key','attempt_id')})
        raw.update(configuration_ref=out['configuration']['configuration_ref'], generation=2,
                   provider_operation_ref=dict(availability='KNOWN', value=out['provider']['operation_ref']))
        for k in ('route_id','provider_id','adapter_profile_id','adapter_profile_version'):
            version['processing'][k] = dict(availability='KNOWN', value=out['processing'][k])
        for k in ('provider_version','model_id','model_version'):
            version['processing'][k] = deepcopy(out['processing'][k])
        after['versions'].append(version)
        transcript.validate_transition(before, after, self.tc)
        self.transcript = after; self.winner = deepcopy(out)
        self.counts['results_published'] += 1; self.state = 'SUCCEEDED'
        if after['edits']:
            self.propose()
        elif self.cancellation == 'NONE':
            self.activate(deepcopy(self.saved_activation))
        return self.event('RESULT', 'SUCCEEDED')

    def activate(self, decision):
        try:
            transcript.validate_decision(decision, self.transcript, self.tc)
            after = f.applied(self.transcript, decision)
            transcript.validate_transition(self.transcript, after, self.tc)
        except ValueError:
            return self.event('ACTIVATE', 'STALE_OR_CONFLICT')
        self.transcript = after
        self.counts['active_selection_moves'] += 1
        return self.event('ACTIVATE', 'ACCEPTED')

    def add_edit(self):
        after = deepcopy(self.transcript)
        n = len(after['edits'])+1
        after['edits'].append(f.edit(n)); after['edit_revision'] = f.revision(n)
        transcript.validate_transition(self.transcript, after, self.tc)
        self.transcript = after
        return self.event('EDIT', 'RETAINED')

    def manual_select(self):
        d = f.decision(self.transcript, 'MANUAL_SELECT', 11)
        d['decision_id'] = self.allocate()
        return self.activate(d)

    def propose(self):
        after = deepcopy(self.transcript)
        template = f.proposed('UNMAPPED')
        alignment, proposal = template['alignments'][0], template['proposals'][0]
        revision = deepcopy(after['edit_revision'])
        alignment['base_edit_revision'] = revision
        alignment['mappings'] = [dict(edit_id=e['edit_id'], target_anchor=None, state='UNMAPPED',
                                     conflict='UNMAPPED_EDIT', basis='INSUFFICIENT') for e in after['edits']]
        proposal.update(base_edit_revision=revision, base_selection_revision=after['selection']['selection_revision'],
                        base_transcript_id=after['selection']['transcript_id'],
                        unmapped_edit_ids=[e['edit_id'] for e in after['edits']])
        after['alignments'].append(alignment); after['proposals'].append(proposal)
        transcript.validate_transition(self.transcript, after, self.tc)
        self.transcript = after
        return self.event('PROPOSAL', 'NEEDS_REVIEW')

    def prepare_merge(self):
        """Explicit synthetic user resolutions, never a merge algorithm or real user choice."""
        after = deepcopy(self.transcript)
        p = after['proposals'][-1]
        require(transcript.proposal_state(p, after) == 'NEEDS_REVIEW', 'PROPOSAL_REVIEW_STATE')
        resolutions = []
        for eid in p['unmapped_edit_ids']:
            resolutions.append(dict(resolution_id=self.allocate(), recording_id=after['recording_id'],
                edit_id=eid, proposal_id=p['proposal_id'], target_transcript_id=p['candidate_transcript_id'],
                base_edit_revision=deepcopy(p['base_edit_revision']), base_selection_revision=p['base_selection_revision'],
                actor='USER', created_at=f.NOW, kind='USE_CANDIDATE_TEXT', target_anchor=None,
                reason='USER_CHOSE_CANDIDATE_TEXT'))
        after['resolutions'].extend(resolutions)
        merged = f.version(3, 'MERGED')
        merged.update(text='', segments=[], raw_provenance=None,
            merge_provenance=dict(proposal_id=p['proposal_id'], base_edit_revision=deepcopy(p['base_edit_revision']),
                applied_edit_ids=[], resolution_ids=[r['resolution_id'] for r in resolutions],
                parent_transcript_ids=[p['base_transcript_id'], p['candidate_transcript_id']]))
        after['versions'].append(merged)
        transcript.validate_transition(self.transcript, after, self.tc)
        self.transcript = after
        return self.event('SYNTHETIC_USER_RESOLUTION', 'READY')

    def accept_proposal(self):
        require(bool(self.transcript['proposals']), 'NO_PROPOSAL')
        p = self.transcript['proposals'][-1]
        state = transcript.proposal_state(p, self.transcript)
        d = f.decision(self.transcript, 'ACCEPT_PROPOSAL', 13)
        d.update(decision_id=self.allocate(), base_edit_revision=deepcopy(p['base_edit_revision']),
            base_selection_revision=p['base_selection_revision'], from_transcript_id=p['base_transcript_id'],
            resolution_ids=[r['resolution_id'] for r in self.transcript['resolutions'] if r['proposal_id'] == p['proposal_id']])
        result = self.activate(d)
        return self.event('PROPOSAL_ACCEPT', 'ACCEPTED' if result == 'ACCEPTED' else state)

    def cancel(self, outcome):
        require(outcome in ('REQUESTED','UNKNOWN','CONFIRMED'), 'CANCEL_OUTCOME')
        if self.cancellation == 'CONFIRMED' or self.winner is not None:
            return self.event('CANCEL', 'TERMINAL_RETAINED')
        d, _ = self.authorize('CANCEL_JOB')
        if d['outcome'] != 'ALLOW': return d['reason']
        require(self.bound is not None, 'NO_BOUND_OPERATION')
        self.cancellation = outcome
        after = deepcopy(self.transcript); after['expected_processing'] = None
        transcript.validate_transition(self.transcript, after, self.tc); self.transcript = after
        if outcome == 'CONFIRMED': self.failure('CANCELLED','ACCEPTED','cancel')
        elif outcome == 'UNKNOWN': self.failure('CANCELLATION_UNCERTAIN','ACCEPTED','cancel')
        return self.event('CANCEL', outcome)

    def tombstone(self):
        self.snapshot['recording_lifecycle'] = 'TOMBSTONED'
        self.snapshot['deletion_epoch'] += 1
        after = deepcopy(self.transcript)
        after.update(lifecycle='TOMBSTONED', expected_processing=None)
        transcript.validate_transition(self.transcript, after, self.tc)
        self.transcript = after
        return self.event('DELETE', 'TOMBSTONED')

    def reconnect(self):
        return self.event('RECONNECT', 'REVALIDATION_REQUIRED')

    def capture_delivery(self):
        return dict(context=deepcopy(self.context), request=deepcopy(self.request), bound=deepcopy(self.bound),
                    result=self.cloud_result() if self.bound is not None else None)

    def deliver(self):
        request_modes = dict(REQUEST_DELIVERED='ACCEPTED', REQUEST_REJECTED='TRANSIENT_REJECTED',
            RESPONSE_LOST='LOST_ACCEPTED', TRANSPORT_BEFORE_DISPATCH='BEFORE_DISPATCH',
            TRANSPORT_AFTER_ACCEPTANCE='LOST_ACCEPTED')
        for event, captured in self.network.ready():
            if event == 'RECONNECT': self.reconnect()
            elif event in request_modes:
                if captured['context'] != self.context: self.event('DELIVERY', 'STALE_REQUEST')
                else: self.submit(request_modes[event])
            elif captured['request'] != self.request:
                self.event('DELIVERY', 'STALE_ATTEMPT')
            elif event in ('SUCCEEDED', 'RESPONSE_DELIVERED'): self.complete(received=captured)
            else:
                try:
                    require(captured['bound'] is not None, 'NO_BOUND_OPERATION')
                    provider.validate_operation(captured['bound'], self.request, self.pc)
                    require(captured['bound'] == self.bound, 'OPERATION_CORRELATION')
                    self.observe(event)
                except ValueError: self.event('DELIVERY', 'MAPPING_FAILURE')

    def mutate(self, kind):
        s, r = self.snapshot, self.context
        if kind == 'no_credential': s['credential'] = None
        elif kind == 'wrong_owner': s['ownership']['principal_id'] = f.uid(999)
        elif kind == 'no_consent': s['grant'] = None
        elif kind in ('pending','deferred'): s['recording_authorization']['state'] = kind.upper(); r['trigger'] = 'AUTOMATIC'
        elif kind == 'revoked': s['grant']['state'] = 'REVOKED'
        elif kind == 'credential_revoked': s['credential']['status'] = 'REVOKED'
        elif kind == 'expired': self.clock.advance(3600_000_000)
        elif kind == 'authority_expired': self.clock.advance(1800_000_000)
        elif kind == 'wrong_source': r['source']['recording_id'] = f.uid(999)
        elif kind == 'source_replacement': s['current_source']['audio_asset_id'] = f.uid(999)
        elif kind == 'route': s['current_route']['region_id'] = f.uid(999)
        elif kind == 'unavailable': s['source_availability'] = 'DELETED'
        elif kind == 'wrong_job': r['processing_job_id'] = f.uid(999)
        elif kind == 'wrong_action': r['processing_action_id'] = f.uid(999)
        elif kind == 'grant_revision': s['grant']['revision'] += 1
        elif kind == 'auth_revision': s['recording_authorization']['revision'] += 1
        elif kind == 'deletion_epoch': s['deletion_epoch'] += 1
        elif kind == 'credential_generation': s['credential']['generation'] += 1
        elif kind == 'owner_revision': s['ownership']['version'] += 1
        else: raise ValueError('MUTATION_KIND')
        return self.event('MUTATE', kind.upper())

    def report(self):
        return dict(counts=deepcopy(self.counts), state=self.state, cancellation=self.cancellation,
            active=self.transcript['selection']['transcript_id'],
            selection_revision=self.transcript['selection']['selection_revision'],
            edits=len(self.transcript['edits']), proposals=len(self.transcript['proposals']),
            trace_sha256=digest(self.trace))


ALLOWED_IMPORTS = {'__future__','argparse','ast','copy','datetime','hashlib','json','pathlib','re',
                  'cloud_contract_fixtures','validate_cloud_identity_auth_contract',
                  'validate_cloud_asr_provider_contract','validate_transcript_versioning_contract'}


def validate_source(source):
    for node in ast.walk(ast.parse(source)):
        if isinstance(node, (ast.Import, ast.ImportFrom)):
            modules = [a.name for a in node.names] if isinstance(node, ast.Import) else [node.module]
            require(all(m in ALLOWED_IMPORTS for m in modules), 'NON_HERMETIC_IMPORT')
        if isinstance(node, ast.Call):
            name = node.func.id if isinstance(node.func, ast.Name) else node.func.attr if isinstance(node.func, ast.Attribute) else ''
            regex_compile = (name == 'compile' and isinstance(node.func, ast.Attribute)
                             and isinstance(node.func.value, ast.Name) and node.func.value.id == 're')
            require(regex_compile or name not in ('__import__','eval','exec','compile','now','today','utcnow','sleep'), 'DYNAMIC_OR_REALTIME_CALL')


def self_test():
    for name in ('cloud_contract_harness.py','cloud_contract_fixtures.py',
                 'validate_cloud_identity_auth_contract.py','validate_cloud_asr_provider_contract.py',
                 'validate_transcript_versioning_contract.py'):
        validate_source((ROOT / 'tools' / name).read_text(encoding='utf-8'))
    auth.validate_contract(auth.contract())
    provider.validate_contract(provider.load_json(ROOT / provider.CONTRACT_PATH))
    transcript.validate_contract(transcript.load_json(ROOT / transcript.CONTRACT_PATH))
    catalogue = auth.load_json(CATALOG)
    require(catalogue['scope'] == 'HERMETIC_SYNTHETIC_HOST_ONLY', 'HARNESS_SCOPE')
    require(catalogue['status'] in ('IMPLEMENTATION_PENDING / EXACT_SHA_CI_REQUIRED',
        'PASS / DETERMINISTIC_CLOUD_CONTRACT_HARNESS_READY'), 'HARNESS_STATUS')
    require(len({s['id'] for s in catalogue['scenarios']}) == len(catalogue['scenarios']), 'SCENARIO_IDENTITY')
    require({s['category'] for s in catalogue['scenarios']} >= {'AUTH','UPLOAD','RETRY',
        'RECONCILIATION','IDEMPOTENCY','CANCELLATION','RESULT','TRANSCRIPT','EDIT_CONFLICT',
        'LATE_CALLBACK','DELETION_FENCE'}, 'SCENARIO_COVERAGE')


def run_catalogue():
    self_test()
    results = []
    allowed = {'create_job','issue','part','submit','retry','reconcile','observe','complete',
               'add_edit','manual_select','prepare_merge','accept_proposal','cancel','tombstone','reconnect','mutate'}
    for scenario in auth.load_json(CATALOG)['scenarios']:
        m = SyntheticControlPlaneLedger(scenario['id'])
        for event in scenario['events']:
            operation = event['call']
            if operation == 'advance': result = m.clock.advance(*event['args'])
            elif operation == 'enqueue': result = m.network.enqueue(*event['args'], **event.get('kwargs', {}))
            elif operation == 'deliver': result = m.deliver()
            else:
                require(operation in allowed, 'SCENARIO_OPERATION')
                result = getattr(m, operation)(*event.get('args', []), **event.get('kwargs', {}))
            if 'expect' in event:
                require(result == event['expect'], 'SCENARIO_DECISION_'+scenario['id'])
        report = m.report()
        require(report['counts'] == scenario['expected_counts'], 'SCENARIO_COUNTERS_'+scenario['id'])
        for key, value in scenario['expected_final'].items():
            require(report[key] == value, 'SCENARIO_FINAL_'+scenario['id'])
        results.append(dict(id=scenario['id'], outcome='PASS', expected_counts=scenario['expected_counts'],
                            actual=report))
    return results


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--self-test', action='store_true')
    parser.add_argument('--verify-determinism', action='store_true')
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    self_test()
    results = run_catalogue()
    if args.verify_determinism:
        require(results == run_catalogue(), 'CLOUD_CONTRACT_HARNESS_NONDETERMINISTIC')
    report = dict(scenarios=results, scenario_count=len(results), digest=digest(results),
                  scope='SYNTHETIC_ONLY', network='NOT_USED', audio='NOT_USED', aws='NOT_CALLED')
    if args.report:
        args.report.write_text(json.dumps(report, indent=2)+'\n', encoding='utf-8')
    print('PASS synthetic Cloud composition:',len(results),'scenarios; digest',report['digest'])


if __name__ == '__main__':
    main()
