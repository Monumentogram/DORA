"""Offline contract examples and negative mutations; no provider/network/audio mocks."""
import copy
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
CONTRACT = ROOT / 'docs/contracts/DORA_ALPHA_CLOUD_ASR_PROVIDER_CONTRACT_V0_1.json'
PROFILE = ROOT / 'docs/contracts/DORA_ALPHA_AWS_TRANSCRIBE_ADAPTER_PROFILE_V0_1.json'


def identity(n):
    return f'00000000-0000-4000-8000-{n:012d}'


def unknown():
    return {'availability': 'UNKNOWN', 'value': None}


def request():
    return {'processing_action_id': identity(1), 'processing_job_id': identity(2),
            'idempotency_key': identity(3), 'attempt_id': identity(4), 'trace_id': identity(5),
            'audio': {'kind': 'ORIGINAL_AUDIO', 'source': {'recording_id': identity(6),
                       'audio_asset_id': identity(7), 'sha256': '0' * 64}, 'access_ref': identity(8)},
            'properties': {'container': 'WAV', 'encoding': 'PCM_SIGNED_16_LE',
                           'duration_us': 1000000, 'sample_rate_hz': 16000, 'channels': 1},
            'configuration': {'language': 'ru-RU', 'configuration_ref': identity(9)},
            'authorization': {'authorization_ref': identity(10), 'consent_scope_ref': identity(11),
                              'ownership_binding_ref': identity(12)}}


def operation(r):
    return {'processing_action_id': r['processing_action_id'],
            'processing_job_id': r['processing_job_id'], 'attempt_id': r['attempt_id'],
            'source': copy.deepcopy(r['audio']['source']),
            'provider_namespace': {'route_id': 'synthetic-route', 'provider_id': 'synthetic-provider',
                                   'adapter_profile_id': 'synthetic-profile', 'adapter_profile_version': '0.1'},
            'provider': {'operation_ref': 'synthetic-provider-operation',
                         'request_ref': unknown(), 'result_ref': unknown()}}


def result(r):
    return {'transcript_id': identity(13),
            **{k: r[k] for k in ('processing_action_id', 'processing_job_id', 'idempotency_key', 'attempt_id')},
            'source': copy.deepcopy(r['audio']['source']), 'configuration': copy.deepcopy(r['configuration']),
            'processing': {'engine': 'CLOUD', 'route_id': 'synthetic-route', 'provider_id': 'synthetic-provider',
                           'provider_version': unknown(), 'model_id': unknown(), 'model_version': unknown(),
                           'adapter_profile_id': 'synthetic-profile', 'adapter_profile_version': '0.1'},
            'provider': operation(r)['provider'], 'text': '', 'segments': [],
            'created_at': '2026-09-30T00:00:00Z', 'processed_at': unknown(), 'usage_duration_us': unknown()}


class CloudContractTests(unittest.TestCase):
    def setUp(self):
        path = ROOT / 'tools/validate_cloud_asr_provider_contract.py'
        self.assertTrue(path.is_file(), 'Cloud contract validator is not implemented')
        spec = importlib.util.spec_from_file_location('cloud_contract_validator', path)
        self.v = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.v)
        self.c = json.loads(CONTRACT.read_text(encoding='utf-8'))
        self.p = json.loads(PROFILE.read_text(encoding='utf-8'))
        self.r = request()

    def test_frozen_contract_and_admitted_profile_validate(self):
        self.v.validate_contract(self.c)
        self.v.validate_profile(self.p, self.c, ROOT)

    def test_request_preserves_dora_identities(self):
        before = copy.deepcopy(self.r)
        self.v.validate_request(self.r, self.c)
        self.assertEqual(before, self.r)
        for field in ('processing_action_id', 'processing_job_id', 'idempotency_key', 'attempt_id', 'trace_id'):
            with self.subTest(field=field), self.assertRaises(ValueError):
                damaged = copy.deepcopy(self.r)
                del damaged[field]
                self.v.validate_request(damaged, self.c)

    def test_retry_keeps_action_key_source_config_and_changes_attempt(self):
        following = copy.deepcopy(self.r)
        following['attempt_id'] = identity(20)
        self.v.validate_retry_attempt(self.r, following, self.c)
        for field in ('processing_action_id', 'processing_job_id', 'idempotency_key'):
            damaged = copy.deepcopy(following)
            damaged[field] = identity(21)
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.v.validate_retry_attempt(self.r, damaged, self.c)
        with self.assertRaises(ValueError):
            self.v.validate_retry_attempt(self.r, self.r, self.c)

    def test_provider_operation_cannot_substitute_logical_job(self):
        bound = operation(self.r)
        bound['provider']['operation_ref'] = self.r['processing_job_id']
        with self.assertRaises(ValueError):
            self.v.validate_operation(bound, self.r, self.c)

    def test_exact_original_audio_is_mandatory(self):
        for change in ('missing_source', 'local_transcript', 'path'):
            r = copy.deepcopy(self.r)
            if change == 'missing_source':
                del r['audio']['source']['audio_asset_id']
            elif change == 'local_transcript':
                r['audio']['kind'] = 'LOCAL_TRANSCRIPT'
            else:
                r['audio']['access_ref'] = '/android/private/audio.wav'
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.v.validate_request(r, self.c)

    def test_unknown_versions_are_explicit_and_empty_is_rejected(self):
        self.v.validate_result(result(self.r), self.r, operation(self.r), self.c)
        for field in ('provider_version', 'model_version'):
            for damaged in ({'availability': 'KNOWN', 'value': ''}, {'availability': 'UNKNOWN', 'value': 'invented'}, None):
                out = result(self.r)
                out['processing'][field] = damaged
                with self.subTest(field=field, variant=damaged is None), self.assertRaises(ValueError):
                    self.v.validate_result(out, self.r, operation(self.r), self.c)

    def test_missing_and_partial_timestamps_do_not_invent_zero(self):
        out = result(self.r)
        stamp = {'availability': 'UNAVAILABLE', 'start_us': None, 'end_us': None, 'quality': 'UNKNOWN'}
        out['segments'] = [{'segment_id': identity(30), 'text': '', 'timestamp': stamp}]
        self.v.validate_result(out, self.r, operation(self.r), self.c)
        stamp['start_us'] = 0
        with self.assertRaises(ValueError):
            self.v.validate_result(out, self.r, operation(self.r), self.c)
        stamp['availability'] = 'PARTIAL'
        self.v.validate_result(out, self.r, operation(self.r), self.c)

    def test_invalid_timestamp_bounds_and_duplicate_segments_rejected(self):
        for start, end in ((-1, 10), (10, 1), (0, 1000001), (True, 5)):
            out = result(self.r)
            out['segments'] = [{'segment_id': identity(30), 'text': '', 'timestamp':
                                {'availability': 'KNOWN', 'start_us': start, 'end_us': end, 'quality': 'UNKNOWN'}}]
            with self.subTest(start=start, end=end), self.assertRaises(ValueError):
                self.v.validate_result(out, self.r, operation(self.r), self.c)

    def test_success_requires_exact_source_job_attempt_config_and_operation(self):
        for field in ('processing_action_id', 'processing_job_id', 'attempt_id', 'idempotency_key', 'source', 'configuration', 'provider'):
            out = result(self.r)
            if field == 'source':
                out[field]['audio_asset_id'] = identity(99)
            elif field == 'configuration':
                out[field]['configuration_ref'] = identity(99)
            elif field == 'provider':
                out[field]['operation_ref'] = 'another-operation'
            else:
                out[field] = identity(99)
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.v.validate_result(out, self.r, operation(self.r), self.c)

    def test_generic_schema_and_requests_reject_wire_and_credentials(self):
        for field in ('TranscriptionJobName', 'MediaFileUri', 'credentials', 'bucket', 'jwt'):
            c = copy.deepcopy(self.c)
            c['types']['CloudRequest'][field] = 'text'
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.v.validate_contract(c)
            r = copy.deepcopy(self.r)
            r[field] = 'synthetic'
            with self.assertRaises(ValueError):
                self.v.validate_request(r, self.c)

    def test_contract_cannot_weaken_unknown_reconciliation_or_scope(self):
        for key in ('unknown_submission_requires_reconciliation', 'server_side_only', 'provider_ids_are_provenance_only'):
            c = copy.deepcopy(self.c)
            c['rules'][key] = False
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.v.validate_contract(c)
        c = copy.deepcopy(self.c)
        c['non_execution']['7.2E'] = 'IMPLEMENTED'
        with self.assertRaises(ValueError):
            self.v.validate_contract(c)

    def test_permanent_errors_do_not_retry(self):
        for category in ('INVALID_REQUEST', 'UNSUPPORTED_FORMAT', 'UNSUPPORTED_LANGUAGE', 'SOURCE_UNAVAILABLE', 'ACCESS_DENIED', 'INVALID_MEDIA'):
            self.assertEqual('DO_NOT_RETRY', self.v.retry_disposition(category, 'DEFINITELY_REJECTED', 'submit'))

    def test_transient_definite_rejection_is_retryable(self):
        for category in ('RATE_LIMITED', 'PROVIDER_UNAVAILABLE', 'TRANSIENT_TRANSPORT', 'PROVIDER_INTERNAL'):
            self.assertEqual('RETRYABLE', self.v.retry_disposition(category, 'DEFINITELY_REJECTED', 'submit'))

    def test_possible_acceptance_overrides_retryable_and_permanent_errors(self):
        for category in ('RATE_LIMITED', 'TRANSIENT_TRANSPORT', 'INVALID_REQUEST', 'SUBMISSION_UNCERTAIN', 'DUPLICATE_CONFLICT'):
            self.assertEqual('RECONCILE_BEFORE_RETRY', self.v.retry_disposition(category, 'POSSIBLY_ACCEPTED', 'submit'))

    def test_unknown_poll_and_mapping_failure_cannot_be_success(self):
        for state in ('SUCCEEDED', 'OUTPUT_AVAILABLE'):
            obs = {'state': state, 'cancellation': 'NONE', 'operation': operation(self.r), 'result': None,
                   'failure': {'category': 'MAPPING_FAILURE', 'operation': 'result', 'dispatch_effect': 'ACCEPTED',
                               'retry_disposition': 'RECONCILE_BEFORE_RETRY', 'retry_after_us': unknown()}}
            with self.subTest(state=state), self.assertRaises(ValueError):
                self.v.validate_observation(obs, self.r, self.c)

    def test_accepted_operation_transport_failure_is_unknown_not_failed(self):
        for api_operation in ('status', 'result', 'cancel', 'reconcile'):
            obs = {'state': 'FAILED', 'cancellation': 'NONE', 'operation': operation(self.r), 'result': None,
                   'failure': {'category': 'TRANSIENT_TRANSPORT', 'operation': api_operation,
                               'dispatch_effect': 'ACCEPTED', 'retry_disposition': 'RECONCILE_BEFORE_RETRY',
                               'retry_after_us': unknown()}}
            with self.subTest(operation=api_operation), self.assertRaises(ValueError):
                self.v.validate_observation(obs, self.r, self.c)
            obs['state'] = 'UNKNOWN'
            self.v.validate_observation(obs, self.r, self.c)

    def test_result_provider_namespace_must_match_bound_dispatch(self):
        for field in ('route_id', 'provider_id', 'adapter_profile_id', 'adapter_profile_version'):
            out = result(self.r)
            out['processing'][field] = 'unrelated'
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.v.validate_result(out, self.r, operation(self.r), self.c)

    def test_cancel_request_requires_distinct_confirmation_evidence(self):
        obs = {'state': 'PROCESSING', 'cancellation': 'REQUESTED', 'operation': operation(self.r), 'failure': None, 'result': None}
        self.v.validate_observation(obs, self.r, self.c)
        self.assertEqual('REQUESTED', obs['cancellation'])
        obs['cancellation'] = 'CONFIRMED'
        with self.assertRaises(ValueError):
            self.v.validate_observation(obs, self.r, self.c)

    def test_confirmed_cancel_requires_bound_accepted_operation(self):
        obs = {'state': 'FAILED', 'cancellation': 'CONFIRMED', 'operation': operation(self.r), 'result': None,
               'failure': {'category': 'CANCELLED', 'operation': 'cancel', 'dispatch_effect': 'ACCEPTED',
                           'retry_disposition': 'DO_NOT_RETRY', 'retry_after_us': unknown()}}
        self.v.validate_observation(obs, self.r, self.c)
        obs['operation'] = None
        with self.assertRaises(ValueError):
            self.v.validate_observation(obs, self.r, self.c)

    def test_duplicate_segment_identity_rejected(self):
        out = result(self.r)
        segment = {'segment_id': identity(30), 'text': '', 'timestamp':
                   {'availability': 'UNAVAILABLE', 'start_us': None, 'end_us': None, 'quality': 'UNKNOWN'}}
        out['segments'] = [segment, copy.deepcopy(segment)]
        with self.assertRaises(ValueError):
            self.v.validate_result(out, self.r, operation(self.r), self.c)

    def test_profile_rejects_unadmitted_region_language_and_format(self):
        for key, value in (('region', 'us-east-1'), ('languages', ['en-GB']), ('mode', 'STREAMING')):
            p = copy.deepcopy(self.p)
            p['admitted'][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.v.validate_profile(p, self.c, ROOT)

    def test_profile_unknown_properties_cannot_be_promoted(self):
        for key, value in (('provider_native_idempotency', 'PROVEN'), ('cancellation_support', 'SUPPORTED'),
                           ('model_version', {'availability': 'KNOWN', 'value': 'invented'})):
            p = copy.deepcopy(self.p)
            p['metadata'][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                self.v.validate_profile(p, self.c, ROOT)

    def test_profile_rejects_unproven_state_and_conflict_retry(self):
        p = copy.deepcopy(self.p)
        p['status_mapping']['CANCELLED']['normalized'] = 'SUCCEEDED'
        with self.assertRaises(ValueError):
            self.v.validate_profile(p, self.c, ROOT)
        p = copy.deepcopy(self.p)
        p['error_mapping'][0]['retry'] = 'RETRYABLE'
        with self.assertRaises(ValueError):
            self.v.validate_profile(p, self.c, ROOT)

    def test_profile_version_and_evidence_tampering_fail_closed(self):
        for change in ('version', 'digest'):
            p = copy.deepcopy(self.p)
            if change == 'version':
                p['generic_contract_version'] = '2.0'
            else:
                p['authority'][0]['sha256_lf'] = '0' * 64
            with self.subTest(change=change), self.assertRaises(ValueError):
                self.v.validate_profile(p, self.c, ROOT)


if __name__ == '__main__':
    unittest.main()
