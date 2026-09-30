"""Offline validation of the frozen server-side interface; never a provider client."""
from __future__ import annotations

from datetime import datetime
import hashlib
import json
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
CONTRACT_PATH = 'docs/contracts/DORA_ALPHA_CLOUD_ASR_PROVIDER_CONTRACT_V0_1.json'
PROFILE_PATH = 'docs/contracts/DORA_ALPHA_AWS_TRANSCRIBE_ADAPTER_PROFILE_V0_1.json'

# Closed v0.1 interface: changing field roles requires explicit contract review/versioning.
EXPECTED_TYPES = {'SourceReference': {'recording_id': 'opaque_id', 'audio_asset_id': 'opaque_id', 'sha256': 'digest'},
 'AuthorizedAudioSource': {'kind': 'SourceKind', 'source': 'SourceReference', 'access_ref': 'opaque_id'},
 'AudioProperties': {'container': 'nonempty',
                     'encoding': 'nonempty',
                     'duration_us': 'positive_int',
                     'sample_rate_hz': 'positive_int',
                     'channels': 'positive_int'},
 'Configuration': {'language': 'nonempty', 'configuration_ref': 'opaque_id'},
 'AuthorizationReferences': {'authorization_ref': 'opaque_id',
                             'consent_scope_ref': 'opaque_id',
                             'ownership_binding_ref': 'opaque_id'},
 'CloudRequest': {'processing_action_id': 'opaque_id',
                  'processing_job_id': 'opaque_id',
                  'idempotency_key': 'opaque_id',
                  'attempt_id': 'opaque_id',
                  'trace_id': 'opaque_id',
                  'audio': 'AuthorizedAudioSource',
                  'properties': 'AudioProperties',
                  'configuration': 'Configuration',
                  'authorization': 'AuthorizationReferences'},
 'KnownMetadata': {'availability': 'Availability', 'value': '?nonempty'},
 'KnownTime': {'availability': 'Availability', 'value': '?utc'},
 'KnownUsage': {'availability': 'Availability', 'value': '?nonnegative_int'},
 'ProcessingIdentity': {'engine': 'Engine',
                        'route_id': 'nonempty',
                        'provider_id': 'nonempty',
                        'provider_version': 'KnownMetadata',
                        'model_id': 'KnownMetadata',
                        'model_version': 'KnownMetadata',
                        'adapter_profile_id': 'nonempty',
                        'adapter_profile_version': 'nonempty'},
 'ProviderProvenance': {'operation_ref': 'nonempty',
                        'request_ref': 'KnownMetadata',
                        'result_ref': 'KnownMetadata'},
 'BoundOperation': {'processing_action_id': 'opaque_id',
                    'processing_job_id': 'opaque_id',
                    'attempt_id': 'opaque_id',
                    'source': 'SourceReference',
                    'provider': 'ProviderProvenance',
                    'provider_namespace': 'ProviderNamespace'},
 'Timestamp': {'availability': 'TimestampAvailability',
               'start_us': '?nonnegative_int',
               'end_us': '?nonnegative_int',
               'quality': 'TimestampQuality'},
 'Segment': {'segment_id': 'opaque_id', 'text': 'text', 'timestamp': 'Timestamp'},
 'CloudResult': {'transcript_id': 'opaque_id',
                 'processing_action_id': 'opaque_id',
                 'processing_job_id': 'opaque_id',
                 'idempotency_key': 'opaque_id',
                 'attempt_id': 'opaque_id',
                 'source': 'SourceReference',
                 'configuration': 'Configuration',
                 'processing': 'ProcessingIdentity',
                 'provider': 'ProviderProvenance',
                 'text': 'text',
                 'segments': '[]Segment',
                 'created_at': 'utc',
                 'processed_at': 'KnownTime',
                 'usage_duration_us': 'KnownUsage'},
 'NormalizedFailure': {'category': 'FailureCategory',
                       'operation': 'Operation',
                       'dispatch_effect': 'DispatchEffect',
                       'retry_disposition': 'RetryDisposition',
                       'retry_after_us': 'KnownUsage'},
 'Observation': {'state': 'JobState',
                 'cancellation': 'CancellationState',
                 'operation': '?BoundOperation',
                 'failure': '?NormalizedFailure',
                 'result': '?CloudResult'},
 'ReconcileRequest': {'request': 'CloudRequest', 'operation': '?BoundOperation'},
 'ProviderNamespace': {'route_id': 'nonempty',
                       'provider_id': 'nonempty',
                       'adapter_profile_id': 'nonempty',
                       'adapter_profile_version': 'nonempty'}}
EXPECTED_ENUMS = json.loads(r'''{"Engine":["CLOUD"],"SourceKind":["ORIGINAL_AUDIO"],"Availability":["KNOWN","UNKNOWN","NOT_SUPPLIED","NOT_APPLICABLE"],"TimestampAvailability":["KNOWN","PARTIAL","UNAVAILABLE"],"TimestampQuality":["UNKNOWN","VALIDATED"],"JobState":["ACCEPTED","PROCESSING","OUTPUT_AVAILABLE","SUCCEEDED","FAILED","UNKNOWN"],"CancellationState":["NONE","REQUESTED","CONFIRMED","UNKNOWN"],"RetryDisposition":["DO_NOT_RETRY","RETRYABLE","RECONCILE_BEFORE_RETRY"],"DispatchEffect":["NOT_DISPATCHED","DEFINITELY_REJECTED","POSSIBLY_ACCEPTED","ACCEPTED"],"Operation":["submit","status","result","cancel","reconcile"],"FailureCategory":["INVALID_REQUEST","UNSUPPORTED_FORMAT","UNSUPPORTED_LANGUAGE","SOURCE_UNAVAILABLE","ACCESS_DENIED","INVALID_MEDIA","RATE_LIMITED","PROVIDER_UNAVAILABLE","TRANSIENT_TRANSPORT","PROVIDER_INTERNAL","SUBMISSION_UNCERTAIN","DUPLICATE_CONFLICT","CANCELLED","CANCELLATION_UNCERTAIN","MAPPING_FAILURE","UNKNOWN"]}''')
EXPECTED_RULES = json.loads(r'''{"server_side_only":true,"control_plane_authorization_required":true,"opaque_references_are_not_authorization_proof":true,"original_audio_only":true,"provider_ids_are_provenance_only":true,"stable_action_job_idempotency_source_config_across_retries":true,"new_attempt_per_dispatch":true,"unknown_submission_requires_reconciliation":true,"cancellation_request_is_not_confirmation":true,"success_requires_validated_correlated_result":true,"late_results_never_activate_or_resurrect":true,"unknown_metadata_never_invented":true,"no_raw_error_or_payload_logging":true}''')
NON_EXECUTION = json.loads(r'''{"7.2D":"NOT_STARTED","7.2E":"NOT_STARTED","7.3":"NOT_STARTED","7.3C":"NOT_STARTED","stage8":"NOT_STARTED","backend_runtime":"NOT_IMPLEMENTED","worker_runtime":"NOT_IMPLEMENTED","adapter_runtime":"NOT_IMPLEMENTED","android_cloud_api":"NOT_IMPLEMENTED","transcript_persistence":"NOT_IMPLEMENTED","auth_runtime":"NOT_IMPLEMENTED","ownership_runtime":"NOT_IMPLEMENTED","audio_upload":"NOT_RUN","real_audio":"NOT_USED","first_real_product_audio":"NOT_READY","aws":"NOT_CALLED","aws_spend_by_task":0,"recovery_integration":"NOT_RUN"}''')
AUTHORITIES = json.loads(r'''[{"id":"closure","path":"docs/evidence/cloud-6.2d-duplicate-name-closure-result-v0.1.json","sha256_lf":"56c3e836e3208a151e4add38d7b728b6acb494e90d46b438ab6958492a73682f"},{"id":"technical","path":"docs/evidence/cloud-6.2d-owner-technical-result-v0.1.json","sha256_lf":"c3d667c604e4ee12652cb314bacf4d780006e9f6ce5dd7e0c04b38556329b770"},{"id":"configuration","path":"docs/contracts/DORA_CLOUD_EVALUATION_PHASE_A_BOUNDED8_V0_3.json","sha256_lf":"f5a498c7e77bfd21c070257c834d53accffdb3460f813ab2388123a8bcde2e97"},{"id":"protocol","path":"docs/contracts/DORA_CLOUD_62D_TECHNICAL_CLOSURE_PROTOCOL_V0_1.json","sha256_lf":"e6a335889f43b1bc84749dc00228cca16a3870e865ea65e5fe02108913c095e8"},{"id":"harness","path":"docs/stage0/DORA_CLOUD_EVALUATION_HARNESS_V0_1.md","sha256_lf":"b9763f38cd473c7a7babf9965fdf23a602efbfdd6769c6a703f4325ee72f00d7"}]''')
PROFILE_METADATA = json.loads(r'''{"provider_version":{"availability":"UNKNOWN","value":null},"model_id":{"availability":"NOT_SUPPLIED","value":null},"model_version":{"availability":"NOT_SUPPLIED","value":null},"timestamp_accuracy":"NOT_EVALUATED","provider_native_idempotency":"NOT_PROVEN","cancellation_support":"NOT_YET_MAPPED","provider_result_id":"NOT_YET_MAPPED","usage_billing_mapping":"NOT_YET_MAPPED","processing_timestamp_mapping":"NOT_YET_MAPPED"}''')
RESULT_MAPPING = json.loads(r'''{"level":"RETAINED_OFFLINE_NORMALIZATION_AND_LIVE_IDENTITY_PROOF","basis":["harness:AwsRecordAdapter.terminal","technical:result_identity","closure:technical_closure.case_table.result_identity"],"text_path":"results.transcripts[0].transcript","segment_items_path":"results.items","segment_text_path":"alternatives[0].content","lexical_item_type":"pronunciation","punctuation":"preserve full text; no invented word timing","start_path":"start_time","end_path":"end_time","source_unit":"decimal_seconds","target_unit":"integer_microseconds","conversion":"exact finite decimal * 1000000; reject fractional microseconds, negative/out-of-range/reversed/nonmonotonic bounds","missing":"null boundaries with explicit availability; partially supplied bounds remain PARTIAL only if otherwise valid","quality":"UNKNOWN","historical_harness_difference":"The frozen harness rejected partial pairs; this generic contract represents partial availability without claiming current provider emits it.","identity":"bind DORA request/source/config independently; validate provider output jobName/status against bound operation before mapping; never use provider jobName as DORA job ID"}''')
STATUS_MAPPING = json.loads(r'''{"COMPLETED":{"normalized":"OUTPUT_AVAILABLE","basis":"closure:technical_closure.first_job; technical:required_technical_cases.tech-en_300"},"FAILED":{"normalized":"FAILED","basis":"technical:required_technical_cases.tech-malformed; closure:technical_closure.case_table.tech-malformed"},"QUEUED":{"normalized":"UNKNOWN","basis":"NOT_YET_MAPPED"},"IN_PROGRESS":{"normalized":"UNKNOWN","basis":"NOT_YET_MAPPED"},"CANCELLED":{"normalized":"UNKNOWN","basis":"NOT_YET_MAPPED"}}''')
ERROR_MAPPING = json.loads(r'''[{"case":"same_valid_request_name_collision","provider_signal":"ConflictException","failure":"DUPLICATE_CONFLICT","retry":"RECONCILE_BEFORE_RETRY","evidence":"closure:technical_closure.new_duplicate_name_collision","ceiling":"Collision only; no native idempotency guarantee"},{"case":"missing_source_with_retained_context","provider_signal":"BadRequestException plus independently proven missing input","failure":"SOURCE_UNAVAILABLE","retry":"DO_NOT_RETRY","evidence":"technical:independent_review_findings.tech-missing_s3; closure:technical_closure.case_table.tech-missing_s3","ceiling":"Never classify from message/400 alone"},{"case":"access_denial_with_retained_context","provider_signal":"BadRequestException plus independently proven isolated access denial","failure":"ACCESS_DENIED","retry":"DO_NOT_RETRY","evidence":"technical:independent_review_findings.tech-permission_denial; closure:technical_closure.case_table.tech-permission_denial","ceiling":"Never classify from message/400 alone"},{"case":"invalid_media_preflight","provider_signal":"local canonical preflight rejection before provider dispatch","failure":"INVALID_MEDIA","retry":"DO_NOT_RETRY","evidence":"protocol:preflight; closure:technical_closure.current_local_controls","ceiling":"Adapter obligation, not proof provider rejects every malformed input"},{"case":"uncertain_submit","provider_signal":"no conclusive submit response","failure":"SUBMISSION_UNCERTAIN","retry":"RECONCILE_BEFORE_RETRY","evidence":"protocol:duplicate_protocol.uncertain_start","ceiling":"Prospective safety rule; no induced network fault claim"},{"case":"throttle","provider_signal":"NOT_YET_MAPPED","failure":"RATE_LIMITED","retry":"RETRYABLE","evidence":"closure:technical_closure.throttle","ceiling":"DOCUMENTED_NOT_INDUCED; exact live mapping unavailable; possible acceptance overrides retry"},{"case":"unmapped_response","provider_signal":"NOT_YET_MAPPED","failure":"UNKNOWN","retry":"RECONCILE_BEFORE_RETRY","evidence":"ADR-0009 uncertain outcomes","ceiling":"Do not guess acceptance, cancellation or generic HTTP semantics"}]''')
IDENTITY_ALIASES = json.loads(r'''{"recording_id":"RecordingId","audio_asset_id":"AudioAssetId/source_audio_version","processing_job_id":"RecognitionJobId","transcript_id":"TranscriptId/asr_result_id"}''')
FORBIDDEN_ASSUMPTIONS = json.loads(r'''["provider_native_idempotency","timeout_means_failure","cancel_request_means_cancelled","delete_means_cancelled","provider_completion_means_valid_result","client_policy_label_means_authorization","missing_timestamp_is_zero","service_name_is_model_version","local_transcript_is_audio","provider_id_is_dora_identity","selected_storage_vendor","automatic_transcript_activation"]''')
PROVENANCE_RULES = json.loads(r'''{"provider_ids":"PROVENANCE_ONLY","logical_ids":"DORA_CONTROL_PLANE_ONLY","wire_fields":"ADAPTER_ONLY","privileged_credentials":"SERVER_SIDE_ONLY_NOT_CONTRACT_FIELDS","storage_binding":"OPAQUE_GENERIC_REFERENCE_RESOLVED_INSIDE_FUTURE_ADAPTER"}''')
ARCHITECTURE = ["DORA_ANDROID","DORA_CLOUD_CONTROL_PLANE","CLOUD_ASR_WORKER","CLOUD_ASR_PROVIDER","PROVIDER_ADAPTER","ADMITTED_PROVIDER"]
CONTRACT_KEYS = set('schema_version contract_version name scope status type_language architecture identity_aliases enums types operations rules forbidden_assumptions adapter_profile non_execution'.split())
PROFILE_KEYS = set('schema_version profile_id profile_version generic_contract_version scope runtime authority admitted status_mapping result_mapping metadata error_mapping provenance_rules limitations non_execution'.split())
UUID = re.compile(r'[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}')
PERMANENT = {'INVALID_REQUEST', 'UNSUPPORTED_FORMAT', 'UNSUPPORTED_LANGUAGE',
             'SOURCE_UNAVAILABLE', 'ACCESS_DENIED', 'INVALID_MEDIA', 'CANCELLED'}
TRANSIENT = {'RATE_LIMITED', 'PROVIDER_UNAVAILABLE', 'TRANSIENT_TRANSPORT', 'PROVIDER_INTERNAL'}


def require(condition, code):
    if not condition:
        # Codes only: do not interpolate any payload, path, ID, exception or transcript.
        raise ValueError(code)


def load_json(path):
    return json.loads(path.read_text(encoding='utf-8'))


def validate_contract(contract):
    require(type(contract) is dict and set(contract) == CONTRACT_KEYS, 'CONTRACT_FIELDS')
    require(contract['schema_version'] == 'dora-cloud-asr-provider-catalog-v0.1'
            and contract['contract_version'] == '0.1'
            and contract['name'] == 'CloudAsrProvider', 'CONTRACT_VERSION')
    require(contract['scope'] == 'SERVER_SIDE_CONTRACT_ONLY', 'SCOPE')
    require(contract['status'] in ('CONTRACT_DEFINED_AWAITING_EXACT_SHA_CI',
                                   'PASS / REPLACEABLE_CLOUD_ASR_PROVIDER_CONTRACT_READY'), 'VERDICT')
    require(contract['architecture'] == ARCHITECTURE, 'TRUST_BOUNDARY')
    require(contract['identity_aliases'] == IDENTITY_ALIASES, 'IDENTITY_ROLES')
    require(contract['types'] == EXPECTED_TYPES, 'CLOSED_INTERFACE_TYPES')
    require(contract['enums'] == EXPECTED_ENUMS, 'CLOSED_ENUMS')
    require(contract['rules'] == EXPECTED_RULES, 'OBLIGATIONS')
    require(contract['forbidden_assumptions'] == FORBIDDEN_ASSUMPTIONS, 'FORBIDDEN_ASSUMPTIONS')
    expected = {name: {'input': ('CloudRequest' if name == 'submit' else
                                'ReconcileRequest' if name == 'reconcile' else 'BoundOperation'),
                       'output': 'Observation'}
                for name in ('submit', 'status', 'result', 'cancel', 'reconcile')}
    require(contract['operations'] == expected, 'LIFECYCLE')
    require(contract['adapter_profile'] == PROFILE_PATH, 'PROFILE_LINK')
    require(contract['non_execution'] == NON_EXECUTION, 'NON_EXECUTION')


def validate_value(kind, value, contract):
    if kind.startswith('?'):
        if value is not None:
            validate_value(kind[1:], value, contract)
        return
    if kind.startswith('[]'):
        require(type(value) is list, 'ARRAY_TYPE')
        for item in value:
            validate_value(kind[2:], item, contract)
        return
    if kind in contract['enums']:
        require(type(value) is str and value in contract['enums'][kind], 'ENUM_VALUE')
    elif kind in contract['types']:
        fields = contract['types'][kind]
        require(type(value) is dict and set(value) == set(fields), 'RECORD_FIELDS')
        for field, field_kind in fields.items():
            validate_value(field_kind, value[field], contract)
        if kind in ('KnownMetadata', 'KnownTime', 'KnownUsage'):
            require((value['availability'] == 'KNOWN') == (value['value'] is not None),
                    'METADATA_AVAILABILITY')
        if kind == 'Timestamp':
            count = sum(value[k] is not None for k in ('start_us', 'end_us'))
            require(count == {'KNOWN': 2, 'PARTIAL': 1, 'UNAVAILABLE': 0}[value['availability']],
                    'TIMESTAMP_AVAILABILITY')
            require(value['quality'] != 'VALIDATED' or count > 0, 'TIMESTAMP_QUALITY')
    elif kind in ('text', 'nonempty', 'opaque_id', 'digest', 'utc'):
        require(type(value) is str, 'STRING_TYPE')
        if kind != 'text':
            require(bool(value.strip()), 'EMPTY_VALUE')
        if kind == 'opaque_id':
            require(UUID.fullmatch(value) is not None, 'OPAQUE_ID')
        if kind == 'digest':
            require(re.fullmatch(r'[0-9a-f]{64}', value) is not None, 'DIGEST')
        if kind == 'utc':
            require(re.fullmatch(r'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,6})?Z', value)
                    is not None, 'UTC_TIME')
            try:
                datetime.fromisoformat(value.replace('Z', '+00:00'))
            except ValueError:
                raise ValueError('UTC_TIME') from None
    elif kind in ('positive_int', 'nonnegative_int'):
        require(type(value) is int and value >= (1 if kind == 'positive_int' else 0), 'INTEGER_RANGE')
    else:
        raise ValueError('UNRECOGNIZED_TYPE')


def validate_request(request, contract):
    validate_contract(contract)
    validate_value('CloudRequest', request, contract)


def validate_retry_attempt(previous, following, contract):
    validate_request(previous, contract)
    validate_request(following, contract)
    for field in ('processing_action_id', 'processing_job_id', 'idempotency_key', 'configuration', 'properties'):
        require(previous[field] == following[field], 'RETRY_IDENTITY')
    require(previous['audio']['source'] == following['audio']['source'], 'RETRY_SOURCE')
    require(previous['attempt_id'] != following['attempt_id'], 'ATTEMPT_REUSE')


def validate_operation(operation, request, contract):
    validate_request(request, contract)
    validate_value('BoundOperation', operation, contract)
    for field in ('processing_action_id', 'processing_job_id', 'attempt_id'):
        require(operation[field] == request[field], 'OPERATION_CORRELATION')
    require(operation['source'] == request['audio']['source'], 'OPERATION_SOURCE')
    require(operation['provider']['operation_ref'] not in
            (request['processing_action_id'], request['processing_job_id'], request['idempotency_key']),
            'PROVIDER_ID_IS_NOT_DORA_ID')


def validate_result(result, request, operation, contract):
    validate_operation(operation, request, contract)
    validate_value('CloudResult', result, contract)
    for field in ('processing_action_id', 'processing_job_id', 'idempotency_key', 'attempt_id', 'configuration'):
        require(result[field] == request[field], 'RESULT_CORRELATION')
    require(result['source'] == request['audio']['source'], 'RESULT_SOURCE')
    require(result['provider']['operation_ref'] == operation['provider']['operation_ref'], 'RESULT_OPERATION')
    for field, value in operation['provider_namespace'].items():
        require(result['processing'][field] == value, 'RESULT_PROVIDER_NAMESPACE')
    ids = [segment['segment_id'] for segment in result['segments']]
    require(len(ids) == len(set(ids)), 'DUPLICATE_SEGMENT')
    prior = {'start_us': -1, 'end_us': -1}
    for segment in result['segments']:
        stamp = segment['timestamp']
        for field in prior:
            value = stamp[field]
            if value is not None:
                require(prior[field] <= value <= request['properties']['duration_us'], 'TIMESTAMP_BOUNDS')
                prior[field] = value
        if stamp['start_us'] is not None and stamp['end_us'] is not None:
            require(stamp['start_us'] <= stamp['end_us'], 'REVERSED_TIMESTAMP')


def retry_disposition(category, dispatch_effect, operation):
    require(category in EXPECTED_ENUMS['FailureCategory']
            and dispatch_effect in EXPECTED_ENUMS['DispatchEffect']
            and operation in EXPECTED_ENUMS['Operation'], 'FAILURE_CLASSIFICATION')
    if category in ('SUBMISSION_UNCERTAIN', 'DUPLICATE_CONFLICT', 'CANCELLATION_UNCERTAIN', 'UNKNOWN'):
        return 'RECONCILE_BEFORE_RETRY'
    if dispatch_effect == 'POSSIBLY_ACCEPTED':
        return 'RECONCILE_BEFORE_RETRY'
    # Read/cancel failures refer to an existing operation; never advise resubmitting it.
    if dispatch_effect == 'ACCEPTED' and category not in PERMANENT:
        return 'RECONCILE_BEFORE_RETRY'
    if category in PERMANENT or category == 'MAPPING_FAILURE':
        return 'DO_NOT_RETRY'
    if category in TRANSIENT:
        return 'RETRYABLE'
    raise ValueError('FAILURE_CLASSIFICATION')


def validate_observation(observation, request, contract):
    validate_request(request, contract)
    validate_value('Observation', observation, contract)
    bound, failure, result = (observation[k] for k in ('operation', 'failure', 'result'))
    if bound is not None:
        validate_operation(bound, request, contract)
    state, cancellation = observation['state'], observation['cancellation']
    if state in ('ACCEPTED', 'PROCESSING', 'OUTPUT_AVAILABLE', 'SUCCEEDED'):
        require(bound is not None, 'MISSING_BOUND_OPERATION')
    if failure is not None:
        expected = retry_disposition(failure['category'], failure['dispatch_effect'], failure['operation'])
        require(failure['retry_disposition'] == expected, 'UNSAFE_RETRY')
    if state == 'SUCCEEDED':
        require(result is not None and failure is None and cancellation != 'CONFIRMED', 'INVALID_SUCCESS')
        validate_result(result, request, bound, contract)
    else:
        require(result is None, 'NON_SUCCESS_WITH_RESULT')
    if state in ('ACCEPTED', 'PROCESSING', 'OUTPUT_AVAILABLE'):
        require(failure is None, 'PROGRESS_WITH_FAILURE')
    if state == 'FAILED':
        require(failure is not None and failure['dispatch_effect'] != 'POSSIBLY_ACCEPTED'
                and failure['retry_disposition'] != 'RECONCILE_BEFORE_RETRY'
                and failure['category'] not in ('SUBMISSION_UNCERTAIN', 'CANCELLATION_UNCERTAIN',
                                                'DUPLICATE_CONFLICT', 'UNKNOWN'), 'UNCERTAIN_NOT_FAILED')
    if cancellation == 'CONFIRMED':
        require(bound is not None and state == 'FAILED' and failure is not None
                and failure['category'] == 'CANCELLED'
                and failure['dispatch_effect'] == 'ACCEPTED', 'CANCEL_NOT_CONFIRMED')
    if failure is not None and failure['category'] == 'CANCELLED':
        require(cancellation == 'CONFIRMED', 'CANCEL_EVIDENCE_REQUIRED')


def validate_profile(profile, contract, root=ROOT):
    validate_contract(contract)
    require(type(profile) is dict and set(profile) == PROFILE_KEYS, 'PROFILE_FIELDS')
    require(profile['schema_version'] == 'dora-cloud-asr-adapter-profile-v0.1'
            and profile['generic_contract_version'] == contract['contract_version']
            and profile['profile_version'] == '0.1'
            and profile['profile_id'] == 'AWS_TRANSCRIBE_STANDARD_BATCH_OWNER_ALPHA', 'PROFILE_VERSION')
    require(profile['scope'] == 'OWNER_ONLY_CLOSED_INTERNAL_ALPHA'
            and profile['runtime'] == 'NOT_IMPLEMENTED'
            and profile['non_execution'] == NON_EXECUTION, 'PROFILE_SCOPE')
    require(profile['authority'] == AUTHORITIES, 'AUTHORITY_BINDINGS')
    evidence = {}
    for entry in AUTHORITIES:
        path = root / entry['path']
        require(path.is_file(), 'AUTHORITY_MISSING')
        text = path.read_text(encoding='utf-8')
        require(hashlib.sha256(text.encode()).hexdigest() == entry['sha256_lf'], 'AUTHORITY_DIGEST')
        if path.suffix == '.json':
            evidence[entry['id']] = json.loads(text)
    closure, technical, config, protocol = (evidence[k] for k in ('closure', 'technical', 'configuration', 'protocol'))
    require(closure['stage_6_2D'] == 'PASS / OWNER_ONLY_CLOSED_INTERNAL_ALPHA_PROVIDER_ADMITTED',
            'FINAL_ADMISSION_REQUIRED')
    require(closure['source_binding']['historical_technical_result_sha256'] == AUTHORITIES[1]['sha256_lf']
            and closure['source_binding']['protocol_sha256'] == AUTHORITIES[3]['sha256_lf'], 'ADMISSION_LINEAGE')
    frozen, preflight = config['configuration'], protocol['preflight']
    expected_admitted = {
        'provider': frozen['service'], 'mode': frozen['mode'], 'region': frozen['region'],
        'languages': [frozen['languages']['ru'], frozen['languages']['en']],
        'input': {'container': frozen['input']['container'], 'encoding': preflight['codec'],
                  'channels': preflight['channels'], 'sample_rate_hz': preflight['sample_rate_hz'],
                  'min_duration_us': preflight['duration_us_min'], 'max_duration_us': preflight['duration_us_max']},
        'disabled': frozen['disabled'], 'fallback': frozen['fallback']}
    require(profile['admitted'] == expected_admitted, 'UNADMITTED_CONFIGURATION')
    require(technical['target_configuration']['region'] == expected_admitted['region']
            and technical['target_configuration']['languages'] == expected_admitted['languages'], 'CONFIG_LINEAGE')
    require(profile['metadata'] == PROFILE_METADATA, 'UNPROVEN_METADATA')
    require(profile['status_mapping'] == STATUS_MAPPING, 'UNPROVEN_STATE_MAPPING')
    require(profile['result_mapping'] == RESULT_MAPPING, 'RESULT_MAPPING')
    require(profile['error_mapping'] == ERROR_MAPPING, 'ERROR_MAPPING')
    require(profile['provenance_rules'] == PROVENANCE_RULES, 'PROFILE_BOUNDARY')
    require(closure['technical_closure']['new_duplicate_name_collision'] == 'PASS_TYPED_CONFLICT_SAME_VALID_REQUEST',
            'CONFLICT_PROOF')
    require(closure['technical_closure']['throttle'] == 'DOCUMENTED_NOT_INDUCED', 'THROTTLE_CEILING')


def main():
    contract = load_json(ROOT / CONTRACT_PATH)
    validate_contract(contract)
    validate_profile(load_json(ROOT / PROFILE_PATH), contract)
    print('PASS CloudAsrProvider v0.1: closed generic types, boundaries and evidence-bound adapter profile')
    print('AWS/NETWORK/AUDIO/RUNTIME: NOT_RUN; downstream stages NOT_STARTED')


if __name__ == '__main__':
    main()
