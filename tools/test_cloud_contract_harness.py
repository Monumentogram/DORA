"""Composed safety regressions; no provider, audio, socket or runtime fixture."""
import copy
import unittest
from unittest.mock import patch

try:
    import cloud_contract_harness as h
except ImportError:
    h = None


class CloudHarnessTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(h, 'composed Cloud harness is required')
        self.m = h.SyntheticControlPlaneLedger()

    def prepared(self):
        self.assertEqual(self.m.create_job(), 'ALLOWED')
        self.assertEqual(self.m.issue(), 'ALLOWED')

    def dispatched(self, mode='ACCEPTED'):
        self.prepared()
        self.assertEqual(self.m.part(400), 'ACCEPTED')
        self.m.submit(mode)

    def test_authorization_denials_count_zero_bytes(self):
        for mutation in ('no_credential', 'wrong_owner', 'no_consent', 'pending',
                         'deferred', 'revoked', 'expired', 'wrong_source',
                         'route', 'unavailable', 'wrong_job', 'wrong_action'):
            with self.subTest(mutation=mutation):
                self.m = h.SyntheticControlPlaneLedger(); self.prepared()
                self.m.mutate(mutation)
                self.assertNotEqual(self.m.part(100), 'ACCEPTED')
                self.assertEqual(self.m.counts['accepted_bytes'], 0)
                self.assertEqual(self.m.counts['rejected_bytes'], 100)
                self.assertEqual(self.m.counts['provider_submits'], 0)

    def test_ceiling_is_cumulative_across_parts_retries_and_reissue(self):
        self.prepared()
        self.assertEqual(self.m.part(400), 'ACCEPTED')
        self.assertEqual(self.m.part(400), 'ACCEPTED')
        self.assertEqual(self.m.part(300, retry=True), 'UPLOAD_BYTE_CEILING')
        self.assertEqual(self.m.issue(), 'ALLOWED')
        self.assertEqual(self.m.part(300), 'UPLOAD_BYTE_CEILING')
        self.assertEqual(self.m.counts['accepted_bytes'], 800)
        self.assertEqual(self.m.counts['rejected_bytes'], 600)

    def test_current_revision_mutation_fences_each_next_part(self):
        for mutation in ('credential_revoked', 'grant_revision', 'auth_revision',
                         'deletion_epoch', 'source_replacement', 'route', 'revoked',
                         'authority_expired', 'credential_generation', 'owner_revision'):
            with self.subTest(mutation=mutation):
                self.m = h.SyntheticControlPlaneLedger(); self.prepared()
                self.m.part(400); self.m.mutate(mutation)
                self.assertNotEqual(self.m.part(400, retry=True), 'ACCEPTED')
                self.assertEqual(self.m.counts['accepted_bytes'], 400)
                self.assertEqual(self.m.counts['rejected_bytes'], 400)

    def test_denied_create_cannot_bind_or_issue(self):
        self.m.mutate('no_consent')
        self.assertEqual(self.m.create_job(), 'NO_CONSENT')
        self.assertNotEqual(self.m.issue(), 'ALLOWED')
        self.m.part(100)
        self.assertEqual(self.m.counts['logical_jobs'], 0)
        self.assertEqual(self.m.counts['accepted_bytes'], 0)

    def test_uncertain_found_does_not_resubmit(self):
        self.dispatched('LOST_ACCEPTED')
        self.assertEqual(self.m.state, 'UNKNOWN')
        self.assertEqual(self.m.retry(), 'RECONCILE_BEFORE_RETRY')
        self.assertEqual(self.m.counts['provider_submits'], 1)
        self.assertEqual(self.m.reconcile(), 'FOUND')
        self.assertEqual(self.m.retry(), 'DO_NOT_RETRY')
        self.m.complete()
        self.assertEqual(self.m.counts['provider_submits'], 1)
        self.assertEqual(self.m.counts['logical_jobs'], 1)
        self.assertEqual(self.m.counts['results_published'], 1)

    def test_uncertain_proven_absent_allows_one_new_attempt(self):
        self.dispatched('LOST_NOT_ACCEPTED')
        original = copy.deepcopy(self.m.request)
        self.assertEqual(self.m.retry(), 'RECONCILE_BEFORE_RETRY')
        self.assertEqual(self.m.reconcile(), 'PROVEN_NOT_ACCEPTED')
        self.m.clock.advance(10)
        self.assertEqual(self.m.retry(), 'ACCEPTED')
        self.assertNotEqual(self.m.request['attempt_id'], original['attempt_id'])
        for key in ('processing_job_id', 'processing_action_id', 'idempotency_key', 'audio', 'configuration'):
            self.assertEqual(self.m.request[key], original[key])
        self.m.complete()
        self.assertEqual(self.m.counts['provider_submits'], 2)
        self.assertEqual(self.m.counts['attempts'], 2)
        self.assertEqual(self.m.counts['logical_jobs'], 1)
        self.assertEqual(self.m.counts['results_published'], 1)

    def test_retry_after_reconciliation_still_needs_current_permission(self):
        self.dispatched('LOST_NOT_ACCEPTED'); self.m.reconcile()
        self.m.clock.advance(10); self.m.mutate('revoked')
        self.assertEqual(self.m.retry(), 'CONSENT_REVOKED')
        self.assertEqual(self.m.counts['provider_submits'], 1)

    def test_output_available_is_not_a_transcript_and_duplicate_is_idempotent(self):
        self.dispatched(); self.m.observe('OUTPUT_AVAILABLE')
        self.assertEqual(self.m.counts['results_published'], 0)
        self.m.complete(); before = copy.deepcopy(self.m.transcript)
        self.m.complete()
        self.assertEqual(self.m.counts['results_published'], 1)
        self.assertEqual(self.m.transcript, before)

    def test_conflict_reconciles_never_direct_success(self):
        self.dispatched('CONFLICT')
        self.assertEqual(self.m.retry(), 'RECONCILE_BEFORE_RETRY')
        self.assertEqual(self.m.counts['results_published'], 0)
        self.assertEqual(self.m.reconcile(), 'FOUND')
        self.m.complete()
        self.assertEqual(self.m.counts['provider_submits'], 1)

    def test_failure_classes_and_backoff(self):
        for mode, want in [('INVALID_REQUEST','DO_NOT_RETRY'), ('SOURCE_UNAVAILABLE','DO_NOT_RETRY'),
                           ('TRANSIENT_REJECTED','RETRYABLE'), ('BEFORE_DISPATCH','RETRYABLE'),
                           ('LOST_ACCEPTED','RECONCILE_BEFORE_RETRY')]:
            with self.subTest(mode=mode):
                self.m = h.SyntheticControlPlaneLedger(); self.dispatched(mode)
                self.assertEqual(self.m.retry_disposition, want)
                if want == 'RETRYABLE':
                    self.assertEqual(self.m.retry(), 'BACKOFF')
                    self.m.clock.advance(10)
                    self.assertEqual(self.m.retry(), 'ACCEPTED')

    def test_late_edit_keeps_raw_versions_edit_and_pointer(self):
        self.dispatched(); self.m.add_edit()
        before = copy.deepcopy(self.m.transcript)
        self.m.complete()
        self.assertEqual(self.m.transcript['selection'], before['selection'])
        self.assertEqual(self.m.transcript['edits'], before['edits'])
        self.assertEqual(self.m.transcript['versions'][0], before['versions'][0])
        self.assertEqual(len(self.m.transcript['versions']), 2)
        self.assertEqual(len(self.m.transcript['proposals']), 1)

    def test_no_edit_allows_guarded_cloud_selection(self):
        self.dispatched(); local = copy.deepcopy(self.m.transcript['versions'][0])
        self.m.complete()
        self.assertEqual(self.m.counts['active_selection_moves'], 1)
        self.assertEqual(self.m.transcript['versions'][0], local)

    def test_manual_selection_blocks_late_automatic_callback(self):
        self.dispatched(); self.m.manual_select()
        selection = copy.deepcopy(self.m.transcript['selection'])
        self.m.complete()
        self.assertEqual(self.m.transcript['selection'], selection)
        self.assertEqual(self.m.counts['results_published'], 1)

    def test_stale_proposal_never_moves_selection(self):
        self.dispatched(); self.m.add_edit(); self.m.complete()
        self.m.prepare_merge()
        self.m.add_edit(); selection = copy.deepcopy(self.m.transcript['selection'])
        with patch.object(h.transcript, 'validate_decision', wraps=h.transcript.validate_decision) as guard:
            self.assertEqual(self.m.accept_proposal(), 'STALE')
            self.assertGreater(guard.call_count, 0)
        self.assertEqual(self.m.transcript['selection'], selection)

    def test_current_resolved_proposal_can_activate(self):
        self.dispatched(); self.m.add_edit(); self.m.complete(); self.m.prepare_merge()
        self.assertEqual(self.m.accept_proposal(), 'ACCEPTED')
        self.assertEqual(self.m.transcript['selection']['transcript_id'], h.f.uid(13))
        self.assertEqual(len(self.m.transcript['edits']), 1)

    def test_reconciliation_cannot_unlock_permanent_rejection(self):
        for mode in ('INVALID_REQUEST', 'SOURCE_UNAVAILABLE'):
            self.m = h.SyntheticControlPlaneLedger(); self.dispatched(mode)
            self.m.reconcile(); self.m.clock.advance(10)
            self.assertEqual(self.m.retry(), 'DO_NOT_RETRY')
            self.assertEqual(self.m.counts['provider_submits'], 1)

    def test_confirmed_cancel_is_monotonic(self):
        for outcome in ('REQUESTED', 'UNKNOWN', 'CONFIRMED'):
            self.m = h.SyntheticControlPlaneLedger(); self.dispatched()
            self.m.cancel('CONFIRMED'); self.m.cancel(outcome); self.m.reconcile()
            self.assertEqual(self.m.cancellation, 'CONFIRMED')
            self.assertEqual(self.m.complete(), 'FENCED')
            self.assertEqual(self.m.counts['results_published'], 0)

    def test_delayed_result_cannot_be_rebound_to_a_new_attempt(self):
        self.dispatched('LOST_NOT_ACCEPTED')
        self.m.network.enqueue('SUCCEEDED', delay=20)
        self.m.reconcile(); self.m.clock.advance(10); self.m.retry()
        self.m.clock.advance(10); self.m.deliver()
        self.assertEqual(self.m.counts['results_published'], 0)
        self.assertEqual(self.m.complete(), 'SUCCEEDED')

    def test_bound_namespace_cannot_redefine_dispatch_identity(self):
        self.dispatched()
        self.m.bound['provider_namespace']['provider_id'] = 'wrong-provider'
        self.assertEqual(self.m.complete(), 'MAPPING_FAILURE')
        self.assertEqual(self.m.counts['results_published'], 0)

    def test_network_request_duplicate_revalidates_and_dispatches_once(self):
        self.prepared(); self.m.part(400)
        self.m.network.enqueue('REQUEST_DELIVERED', delay=10, copies=2)
        self.m.clock.advance(10); self.m.deliver()
        self.assertEqual(self.m.counts['provider_submits'], 1)
        self.assertEqual(self.m.counts['attempts'], 1)
        self.m.network.enqueue('RESPONSE_DELIVERED', copies=2); self.m.deliver()
        self.assertEqual(self.m.counts['results_published'], 1)

    def test_reconnect_and_delayed_request_need_current_consent(self):
        self.prepared(); self.m.part(400)
        self.m.network.enqueue('REQUEST_DELIVERED', delay=10)
        self.m.mutate('revoked'); self.m.reconnect()
        self.m.clock.advance(10); self.m.deliver()
        self.assertEqual(self.m.counts['provider_submits'], 0)

    def test_processing_generation_cannot_reset_through_null(self):
        self.dispatched(); self.m.cancel('REQUESTED')
        before = copy.deepcopy(self.m.transcript)
        after = copy.deepcopy(before)
        after['expected_processing'] = copy.deepcopy(after['processing_history'][-1])
        with self.assertRaisesRegex(ValueError, 'PROCESSING_RESCHEDULE_REQUIRED'):
            h.transcript.validate_transition(before, after, self.m.tc)
        self.m.complete()
        self.assertEqual(self.m.counts['active_selection_moves'], 0)

    def test_composed_merge_history_cannot_cycle_or_fake_user_rejection(self):
        self.dispatched(); self.m.add_edit(); self.m.complete(); self.m.prepare_merge()
        before = copy.deepcopy(self.m.transcript)
        after = copy.deepcopy(before)
        after['versions'][-1]['merge_provenance']['parent_transcript_ids'][0] = h.f.uid(13)
        with self.assertRaises(ValueError):
            h.transcript.validate_transition(before, after, self.m.tc)
        decision = h.f.decision(before, 'REJECT_PROPOSAL')
        decision.update(decision_id=self.m.allocate(), actor='SYSTEM')
        self.assertEqual(self.m.activate(decision), 'STALE_OR_CONFLICT')
        self.assertEqual(self.m.transcript, before)

    def test_cancel_intent_is_not_confirmation_and_fences_late_activation(self):
        self.dispatched(); self.assertEqual(self.m.cancel('REQUESTED'), 'REQUESTED')
        self.m.complete()
        self.assertEqual(self.m.cancellation, 'REQUESTED')
        self.assertEqual(self.m.counts['active_selection_moves'], 0)
        self.assertEqual(self.m.counts['results_published'], 1)

    def test_revocation_still_allows_owned_protective_cancel(self):
        self.dispatched(); self.m.mutate('revoked')
        self.assertEqual(self.m.cancel('UNKNOWN'), 'UNKNOWN')
        self.assertEqual(self.m.retry(), 'DO_NOT_RETRY')
        self.assertEqual(self.m.cancel('CONFIRMED'), 'CONFIRMED')
        self.m.complete()
        self.assertEqual(self.m.counts['results_published'], 0)

    def test_tombstone_fences_publication_bytes_retry_and_resurrection(self):
        self.dispatched(); self.m.tombstone(); before = copy.deepcopy(self.m.transcript)
        self.m.complete(); self.m.part(200, retry=True); self.m.retry()
        self.assertEqual(self.m.transcript, before)
        self.assertEqual(self.m.counts['accepted_bytes'], 400)
        self.assertEqual(self.m.counts['results_published'], 0)

    def test_reconnect_cannot_supply_consent_or_resubmit_unknown(self):
        self.dispatched('LOST_ACCEPTED'); self.m.mutate('revoked')
        before = copy.deepcopy(self.m.snapshot)
        self.m.reconnect()
        self.assertEqual(self.m.snapshot, before)
        self.assertEqual(self.m.counts['provider_submits'], 1)

    def test_result_correlation_and_mapping_fail_closed(self):
        for mutation in ('namespace','source','job','attempt','configuration','mapping'):
            with self.subTest(mutation=mutation):
                self.m = h.SyntheticControlPlaneLedger(); self.dispatched()
                self.assertNotEqual(self.m.complete(mutation), 'SUCCEEDED')
                self.assertEqual(self.m.counts['results_published'], 0)
                self.assertEqual(self.m.counts['active_selection_moves'], 0)

    def test_delayed_duplicate_reordered_delivery_and_trace_repeat(self):
        def run():
            m = h.SyntheticControlPlaneLedger()
            m.create_job(); m.issue(); m.part(400); m.submit()
            m.network.enqueue('PROCESSING', delay=20)
            m.network.enqueue('OUTPUT_AVAILABLE', delay=10, copies=2)
            m.clock.advance(10); m.deliver(); m.complete()
            m.clock.advance(10); m.deliver()
            self.assertEqual(m.state, 'SUCCEEDED')
            self.assertEqual(m.counts['results_published'], 1)
            return m.report()
        self.assertEqual(run(), run())

    def test_structural_guard_and_catalogue(self):
        h.self_test()
        for source in ('import socket', 'from urllib import request', 'import boto3',
                       'import time', '__import__("socket")'):
            with self.subTest(source=source), self.assertRaises(ValueError):
                h.validate_source(source)

    def test_direct_retry_entry_cannot_bypass_reconciliation_or_backoff(self):
        self.dispatched('LOST_ACCEPTED')
        self.assertEqual(self.m.submit(retry=True), 'RECONCILE_BEFORE_RETRY')
        self.assertEqual(self.m.counts['provider_submits'], 1)

    def test_forged_issuance_allow_and_same_revision_mutation_accept_no_bytes(self):
        self.prepared()
        self.m.issuance_snapshot['grant'] = None
        self.assertNotEqual(self.m.part(100), 'ACCEPTED')
        self.assertEqual(self.m.counts['accepted_bytes'], 0)
        self.m = h.SyntheticControlPlaneLedger(); self.prepared()
        self.m.snapshot['grant']['scope']['disclosure_version'] += 1
        self.assertEqual(self.m.part(100), 'IMMUTABLE_EVIDENCE_REVISION')
        self.assertEqual(self.m.counts['accepted_bytes'], 0)

    def test_missing_authority_accepts_no_bytes(self):
        self.m.create_job()
        self.assertEqual(self.m.part(100), 'NO_UPLOAD_AUTHORITY')
        self.assertEqual(self.m.counts['accepted_bytes'], 0)

    def test_job_binding_forgery_and_provider_identity_substitution(self):
        self.prepared()
        for mutate, reason in ((lambda b: b.update(creation_decision_ref=h.f.uid(999)), 'JOB_CREATION'),
                               (lambda b: b.update(processing_job_id=h.f.uid(998)), 'JOB_CREATION_SCOPE')):
            b = copy.deepcopy(self.m.snapshot['job']); mutate(b)
            unbound = h.SyntheticControlPlaneLedger()
            with self.assertRaisesRegex(ValueError, reason):
                unbound.bind_job(b, self.m.creation, self.m.creation_snapshot)
            self.assertIsNone(unbound.snapshot['job'])
        b = copy.deepcopy(self.m.snapshot['job']); b['processing_job_id'] = 'synthetic-operation-1'
        with self.assertRaises(ValueError):
            h.SyntheticControlPlaneLedger().bind_job(b, self.m.creation, self.m.creation_snapshot)
        self.assertEqual(self.m.counts['logical_jobs'], 1)

    def test_catalogue_scenarios_and_repeat(self):
        first = h.run_catalogue()
        second = h.run_catalogue()
        self.assertEqual(first, second)
        self.assertGreaterEqual(len(first), 35)


if __name__ == '__main__':
    unittest.main()
