"""Generate a prospective bounded protocol from independently collected private proofs.

This tool is offline. A draft never authorizes dispatch. Finalization verifies proof
bindings and corpus bytes; it does not substitute for the operator's actual AWS
checks or for the independent pushed/refetched publication barrier in live_run.
"""
from __future__ import annotations

import argparse
import copy
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re

from tools.cloud62d_owned.corpus import CorpusStore, EASY_EN_IDS, EASY_EN_PROTOCOL
from tools.cloud62d_aws import live_run
from tools.cloud62d_aws.aws_prepare import validate_config

NAME = 'DORA_CLOUD_EVALUATION_PHASE_A_BOUNDED8_V0_2'
ORIGINAL = 'docs/contracts/DORA_CLOUD_EVALUATION_PHASE_A_V0_1.json'
READY = 'docs/contracts/DORA_CLOUD_62D_CORPUS_READY_V0_1.json'
MANIFEST = '27ea1593a46c9c17de0a568df6537dbe55df56c4def59559d758cb9f07ecbe65'
BINDING_HASH_KEYS = ('manifest_sha256', 'config_sha256', 'plan_sha256',
                     'aws_client_sha256', 'aws_config_sha256', 'operator_sha256')
EVIDENCE_LABELS = ('owner_authority', 'identity_optout', 'resource_preflight',
                   'retention_probe', 'cleanup_readback', 'pricing', 'account_jurisdiction')
ALPHA_CHECKS = {
    f'OD-11C-26-{i:02}': requirement for i, requirement in enumerate((
        'Participant belongs to approved closed internal Alpha population',
        'Current disclosure received', 'Explicit recording/Cloud-test opt-in',
        'No unapproved third-party voices intentionally included',
        'Amazon Transcribe selected configuration', 'Region eu-central-1',
        'No automatic region fallback', 'Actual account effective AI-services/Transcribe opt-out',
        'DORA S3/audio retention controls configured', 'Encryption and key custody configured',
        'No permanent AWS credentials in Android', 'Current consent/version durably recorded'), 1)}


def require(condition, code):
    if not condition:
        raise ValueError(code)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def normalized_sha(path):
    return sha(Path(path).read_bytes().replace(b'\r\n', b'\n'))


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def public_environment(config):
    """Allowlist only. Never pass through account, resource names, paths or ARNs."""
    result = {'platform': 'AWS', 'service': 'Amazon Transcribe',
              'mode': 'STANDARD_BATCH_FILE_ASR', 'region': 'eu-central-1',
              'endpoint': 'transcribe.eu-central-1.amazonaws.com',
              'profile': 'dora-62d-alpha', 'execution': 'TASK_SCOPED_SHORT_LIVED_ASSUMED_ROLE',
              'model': 'PROVIDER_MANAGED_MODEL_VERSION_NOT_DISCLOSED',
              'account_and_resources': 'PRIVATE_HASH_BOUND_ONLY'}
    version = config.get('client_version', '')
    match = re.match(r'^aws-cli/(\d+\.\d+\.\d+)(?:\s|$)', version)
    result['client_version'] = 'aws-cli/' + match[1] if match else 'UNVERIFIED'
    return result


def _pointer(value, pointer):
    for part in pointer.strip('/').split('/'):
        value = value[int(part)] if isinstance(value, list) else value[part]
    return value


def build_draft(repo):
    repo = Path(repo)
    original = json.loads((repo / ORIGINAL).read_bytes())
    ready = json.loads((repo / READY).read_bytes())
    require(ready['whole_manifest_sha256'] == MANIFEST and ready['recordings'] == 8 and
            ready['human_verified_references'] == 8, 'ACCEPTED_CORPUS_READY_CHANGED')
    require(len(original['effective_gate_statuses']) == 39 and
            original['effective_gate_statuses']['CLD-ADM-PROVIDER-001'] != 'SATISFIED', 'FROZEN_GATES')
    live_run.load_harness(repo)
    changes = []
    amendments = (
        ('/protocol/quality/required_slices', 'Exactly four owner clips per language; RU 2 READ + 2 SPONTANEOUS, EN 4 BASIC READ. No population inference.'),
        ('/protocol/quality/aggregation', 'Unchanged micro formula and RU20/EN18 thresholds apply to language totals; class micros are descriptive, with every required class and clip accounted.'),
        ('/protocol/selection/population', 'The accepted finalized private eight-clip manifest replaces the empty population for this bounded run only.'),
        ('/protocol/selection/rule', 'All eight fixed IDs in manifest order, RU then EN, without sorting or subsampling. No replacements/reserves.'),
        ('/protocol/selection/missing_coverage', 'Missing broad coverage blocks full admission; all eight bounded primaries and references are mandatory for bounded completion.'),
        ('/protocol/quality/noise', 'No noisy/speakerphone evaluation in this run; NOT_EVALUATED, no waiver or inference.'),
        ('/protocol/timestamps/coverage', 'No independent human timing exists. Check output structure only; timestamp accuracy and its coverage remain NOT_EVALUATED.'),
        ('/protocol/timestamps/reference', 'No generated, interpolated or forced-alignment timing may serve as truth; no accuracy scoring is performed.'),
        ('/protocol/latency/gates', 'Retain p50 ratio<=1, p95 ratio<=2, short-clip p95 absolute<=120s as bounded diagnostics for n=4 per language, without general reliability claims.'),
        ('/execution_envelope/preflight/8', 'PREFLIGHT-09 is BOUNDED8_DATA_AUTHORITY=PASS only after private proof. BROAD_DATASET_COVERAGE remains NOT_SATISFIED.'),
        ('/execution_envelope/preflight/11', 'Only explicitly authorized Project Owner speech and approved synthetic fixtures; customer and third-party speech prohibited.'),
    )
    for pointer, replacement in amendments:
        changes.append({'original_pointer': pointer, 'original_value': copy.deepcopy(_pointer(original, pointer)),
                        'bounded_replacement': replacement,
                        'broad_requirement_disposition': 'UNCHANGED_AND_UNSATISFIED_BY_BOUNDED8'})
    sources = [ORIGINAL, 'docs/stage0/DORA_CLOUD_EVALUATION_PHASE_A_V0_1.md',
               'docs/contracts/DORA_CLOUD_62D_EIGHT_CLIP_PROTOCOL_V0_1.json',
               'docs/contracts/DORA_CLOUD_62D_EASY_ENGLISH_V0_1.json', READY, live_run.HARNESS]
    sources.append('docs/contracts/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_4.json')
    technical = [
        {'case': case, 'status': 'NOT_RUN', 'purpose': purpose, 'expected': expected}
        for case, purpose, expected in (
            ('empty', 'Zero-frame synthetic WAV', 'Attributable input rejection or completed empty transcript; record exact actual behavior.'),
            ('near_empty', '250ms synthetic silence', 'Attributable input rejection or completed empty transcript; nonempty text is unexpected.'),
            ('malformed', 'Non-WAV synthetic bytes', 'Attributable invalid input rejection; infrastructure failures are INCOMPLETE.'),
            ('truncated', 'Synthetic WAV with truncated declared PCM', 'Attributable invalid input rejection; infrastructure failures are INCOMPLETE.'),
            ('wrong_format', 'Synthetic WAV declared MP3', 'Attributable format rejection; infrastructure failures are INCOMPLETE.'),
            ('missing_s3', 'Absent object in owned input prefix', 'Attributable missing input rejection; infrastructure failures are INCOMPLETE.'),
            ('duplicate_name', 'Reuse one synthetic job name with changed missing URI', 'Second Start returns ConflictException; original job identity remains unchanged.'),
            ('ru_300', 'Derived RU composite 300s', 'Readable terminal success, structurally valid timestamps; no independent WER.'),
            ('ru_600', 'Derived RU composite 599.999s', 'Readable terminal success, structurally valid timestamps; no independent WER.'),
            ('en_300', 'Derived EN composite 300s', 'Readable terminal success, structurally valid timestamps; no independent WER.'),
            ('en_600', 'Derived EN composite 599.999s', 'Readable terminal success, structurally valid timestamps; no independent WER.'),
        )]
    return {
        'schema_version': '0.2', 'package': NAME, 'scope': 'BOUNDED_EIGHT_CLIP_LIVE_EVALUATION',
        'phase_a': 'DRAFT_AWAITING_VERIFIED_PREFLIGHT', 'phase_b': 'NOT_RUN', 'live_binding': None,
        'accepted_starting_commit': 'b6c1893c2b1076b183da2892f339a7ec6073a080',
        'branch': 'chat/alpha-asr-runner-scope',
        'original_phase_a_commit': 'd8d5c90516b6a9afa96c21e703dfe7481e64e589',
        'sources': [{'path': p, 'sha256_lf': normalized_sha(repo / p)} for p in sources],
        'authority': 'Explicit owner request for this private corpus, paid bounded AWS evaluation <=USD10, and separately approved task-scoped infrastructure; no broad admission authority.',
        'dataset': {'protocol': EASY_EN_PROTOCOL, 'whole_manifest_sha256': MANIFEST,
            'composition': ready['composition'], 'clips': 8, 'human_verified_references': 8,
            'language_aggregates': {lang: {k: ready['languages'][lang][k] for k in
                ('records', 'reference_words', 'duration_us')} for lang in ('ru', 'en')},
            'total_duration_us': ready['total_duration_us'], 'selection': 'ALL_EIGHT_FIXED_MANIFEST_IDS_RU_THEN_EN',
            'quality_scope': 'ONE_SPEAKER_RU_READ_AND_SPONTANEOUS_EN_BASIC_READ_ONLY',
            'technical_composites': ready['technical_composites'],
            'privacy': 'Speech, references, per-record hashes, identities, resources and raw results remain private outside Git.'},
        'prospective_supersessions': changes,
        'additional_execution_authority': {
            'original_pointer': '/execution_envelope/budget/ancillary',
            'bounded_change': 'Owner separately approved exact reviewed dedicated test stack, proof permissions and deletion probes. No unrelated subscription, shared-resource mutation or production access.',
            'original_broad_quality_requirements': 'UNCHANGED'},
        'normalization': copy.deepcopy(original['protocol']['normalization']),
        'harness': copy.deepcopy(original['harness']),
        'configuration': {**copy.deepcopy(original['configuration']),
            'unbound_actual_fields': ['private_verified_environment'], 'unbound_effect': 'BLOCKED_UNTIL_FINALIZATION'},
        'metrics': {'threshold_percent': {'ru': 20, 'en': 18},
            'wer': 'Raw and normalized unit-cost S,D,I,N per clip and per language/class; micro=sum(S+D+I)/sum(N); exact integer threshold comparison. Never average clip percentages or RU and EN.',
            'class_completeness': 'RU requires both two-clip classes; EN requires four READ clips. Missing/failed/unscored clips remain in all-eight accounting and prevent PASS.',
            'timestamp_structure': 'For every pronunciation item count missing/malformed/nonmonotonic timestamps; finite numeric 0<=start<=end<=duration; starts and ends nondecreasing. Nonempty text with no pronunciation items is invalid.',
            'timestamp_accuracy': 'NOT_EVALUATED', 'noise_robustness': 'NOT_EVALUATED', 'en_spontaneous': 'NOT_EVALUATED',
            'latency': copy.deepcopy(original['protocol']['latency']),
            'latency_inference': 'n=4 per language is descriptive only; nearest-rank p95 is maximum. Failed, uncertain and missing observations remain visible, not omitted from admission.',
            'measurements': 'NOT_RUN'},
        'attempt_policy': copy.deepcopy(original['protocol']['repeat_failure']),
        'live_plan': {'status': 'AWAITING_PRIVATE_PLAN_BINDING', 'serial': True, 'automatic_retries': 0,
            'primary_runs_per_clip': 1, 'operator_diagnostic_retry': 'NONE_PLANNED; any addition requires a prospective bound reservation before dispatch and never changes primary outcome.'},
        'technical_cases': technical,
        'additional_technical_limits': {'permission_failure': 'NOT_RUN_NO_ISOLATED_DENIAL_FIXTURE',
            'throttle': 'DOCUMENTED_NOT_INDUCED', 'composite_quality': 'NOT_AN_INDEPENDENT_WER_SAMPLE',
            'generic_negative_failure': 'INCOMPLETE_UNTIL_ATTRIBUTABLE_INPUT_CAUSE; auth/quota/service failure is not expected-format PASS'},
        'budget': {'total_usd_max': 10, 'asr_reserved_usd_max': 2, 'ancillary_and_tax_usd_max': 8,
            'free_tier_assumed': False, 'actual_spend': 'NOT_MEASURED',
            'reservation': 'Before every potentially billed dispatch, including failures and uncertain submissions; no release without billing proof.'},
        'environment': public_environment({}),
        'preflight': {'status': 'NOT_VERIFIED', 'checks': {f'PREFLIGHT-{i:02}': 'NOT_VERIFIED' for i in range(1,13)},
            'bounded8_data_authority': 'NOT_VERIFIED', 'broad_dataset_coverage': 'NOT_SATISFIED'},
        'internal_alpha_preflight': {'authority': 'OD-11C-26',
            'distinct_from_evaluation_preflight': True, 'requirements': ALPHA_CHECKS,
            'checks': {key: 'NOT_VERIFIED' for key in ALPHA_CHECKS}},
        'owner_notice_and_jurisdiction': {
            'required': 'Actual delivered notice and durable existing explicit opt-in; verified private operator/rights/credential-loss route; current AWS agreement and service terms snapshots; separately reviewed jurisdiction and account eligibility.',
            'legal_certification': 'NOT_CLAIMED',
            'country_inference': 'Participant residence does not establish AWS Account Country or automatically create a household exemption.',
            'status': 'AWAITING_PRIVATE_EVIDENCE'},
        'retention': copy.deepcopy(original['execution_envelope']['retention']),
        'cleanup': {'procedure': copy.deepcopy(original['execution_envelope']['cleanup']),
            'independent_proof': 'After exact owned-prefix deletion, separate proof principal inspects whole dedicated buckets (contents, versions, markers, multipart) and owned job prefix; denied read is unresolved.',
            'failure': 'Fence new runs; preserve private evidence, reconcile, and report PENDING. No PASS while cleanup is unresolved.',
            'provider_internal_physical_purge': 'NOT_CLAIMED', 'actual': 'NOT_RUN'},
        'publication_barrier': {'required': ['One atomic successor commit before first speech PutObject or any StartTranscriptionJob',
            'Push then independently fetch; exact remote HEAD equals local HEAD, clean tree and branch',
            'Bind manifest, plan, config, AWS binary/config, operator and live preflight digests',
            'Durable zero-prior-speech evidence; canonical corpus lock and run-root binding prevent ledger reset',
            'Separate subsequent result commit; never amend/squash/rewrite prospective evidence'],
            'publication_identity': 'ENCLOSING_COMMIT; independent run journal records the actual verified remote commit and time',
            'status': 'NOT_PUBLISHED'},
        'result_rules': {'bounded8': 'PASS only if all eight primary quality/accounting, structural integrity, bounded latency, required planned technical cases and verified cleanup succeed; otherwise FAIL or INCOMPLETE with reasons.',
            'full_provider_admission': 'NOT_ESTABLISHED', 'stage_6_2D': 'Never full PASS from this run; after completed evaluation PARTIAL / BOUNDED8_COMPLETE_FULL_ADMISSION_PENDING.',
            'recommendation': 'EXPAND_CORPUS_FOR_FULL_6_2D only after both language thresholds and integrity pass; otherwise name measured blocker. Do not collect more data automatically.',
            'stage_6_3': 'NOT_RUN / BLOCKED', 'no_generalization': ['arbitrary speakers', 'EN spontaneous', 'noise/speakerphone', 'timestamp accuracy', 'general population', 'statistical reliability']},
        'replacement_feasibility': 'Frozen AWS/NonAWSFake shared offline contract evidence only; no live alternative-provider quality equivalence claim.',
        'effective_gate_statuses': copy.deepcopy(original['effective_gate_statuses']),
        'effective_status_counts': copy.deepcopy(original['effective_status_counts']), 'frozen_gate_count': 39,
        'preserved': ['Original Phase A v0.1 and all broad thresholds/coverage', 'Android production', 'Recovery', 'Local Stage5', 'PR #86', 'main'],
    }


def validate_preflight(proof, bindings, evidence_hashes):
    require(set(bindings) == set(BINDING_HASH_KEYS) and all(re.fullmatch('[0-9a-f]{64}', v) for v in bindings.values()), 'BINDING_HASHES')
    require(set(evidence_hashes) == set(EVIDENCE_LABELS) and all(re.fullmatch('[0-9a-f]{64}', v) for v in evidence_hashes.values()), 'EVIDENCE_HASHES')
    require(proof.get('status') == 'BOUNDED8_PREFLIGHT_PASS' and
            proof.get('checks') == {f'PREFLIGHT-{i:02}': 'PASS' for i in range(1,13)} and
            proof.get('internal_alpha_checks') == {key: 'PASS' for key in ALPHA_CHECKS} and
            proof.get('bounded8_data_authority') == 'PASS' and
            proof.get('broad_dataset_coverage') == 'NOT_SATISFIED' and
            type(proof.get('aws_speech_calls')) is int and proof['aws_speech_calls'] == 0, 'PREFLIGHT_NOT_VERIFIED')
    require(proof.get('bindings') == bindings and proof.get('evidence_sha256') == evidence_hashes, 'PREFLIGHT_PROOF_BINDING')
    expiry = datetime.fromisoformat(proof['expires_at'].replace('Z', '+00:00'))
    require(expiry.tzinfo is not None and datetime.now(timezone.utc) < expiry, 'PREFLIGHT_EXPIRED')


def validate_owner_authority(receipt):
    """Require a recorded disclosure and existing authority, never invent a new grant."""
    for key in ('notice_sha256', 'service_terms_sha256', 'customer_agreement_sha256'):
        require(isinstance(receipt.get(key), str) and re.fullmatch('[0-9a-f]{64}', receipt[key]), 'OWNER_NOTICE_HASHES')
    for key in ('explicit_authorization', 'rights_and_credential_loss_route'):
        require(isinstance(receipt.get(key), str) and bool(receipt[key].strip()), 'OWNER_AUTHORITY_OR_RIGHTS_ROUTE')
    require(isinstance(receipt.get('delivered_at'), str), 'NOTICE_NOT_DELIVERED')
    delivered = datetime.fromisoformat(receipt['delivered_at'].replace('Z', '+00:00'))
    require(delivered.tzinfo is not None and delivered <= datetime.now(timezone.utc), 'NOTICE_NOT_DELIVERED')


def finalize_bundle(*, repo, corpus_root, config_path, plan_path, preflight_path, aws_path, aws_config_path, evidence_paths):
    """Read-only verification returning public-safe files in memory, never publishing."""
    repo = Path(repo).resolve()
    paths = {k: Path(v).resolve() for k, v in {
        'config_sha256': config_path, 'plan_sha256': plan_path, 'aws_client_sha256': aws_path,
        'aws_config_sha256': aws_config_path}.items()}
    require(set(evidence_paths) == set(EVIDENCE_LABELS), 'EVIDENCE_LABELS')
    for path in [*paths.values(), Path(preflight_path), *(Path(v) for v in evidence_paths.values())]:
        require(not path.resolve().is_relative_to(repo), 'PRIVATE_INPUT_INSIDE_GIT')
    record = build_draft(repo)
    store = CorpusStore(corpus_root, repo)
    summary = store.validate_manifest()
    require(summary['manifest_sha256'] == MANIFEST, 'MANIFEST_CHANGED')
    require(not store._path('provider-started.json').exists(), 'PRIOR_PROVIDER_RUN')
    config = json.loads(paths['config_sha256'].read_bytes()); validate_config(config)
    require(config.get('outputs') and config.get('template_sha256') and config.get('write_deadline'), 'ACTUAL_ENVIRONMENT_UNBOUND')
    require(datetime.now(timezone.utc) < datetime.fromisoformat(config['write_deadline'].replace('Z', '+00:00')), 'WRITE_DEADLINE_EXPIRED')
    environment = public_environment(config)
    require(environment['client_version'] != 'UNVERIFIED', 'CLIENT_VERSION_UNVERIFIED')
    plan = json.loads(paths['plan_sha256'].read_bytes())
    require(plan == live_run.build_plan(store, plan['pricing']), 'FROZEN_PLAN_CHANGED_OR_CASES_MISSING')
    bindings = {k: sha(v.read_bytes()) for k, v in paths.items()}
    bindings.update(manifest_sha256=MANIFEST, operator_sha256=normalized_sha(repo / 'tools/cloud62d_aws/live_run.py'))
    evidence = {k: sha(Path(v).read_bytes()) for k, v in evidence_paths.items()}
    owner_receipt = json.loads(Path(evidence_paths['owner_authority']).read_bytes())
    validate_owner_authority(owner_receipt)
    raw_proof = Path(preflight_path).read_bytes(); proof = json.loads(raw_proof)
    validate_preflight(proof, bindings, evidence)
    record['phase_a'] = 'BOUNDED8_PREFLIGHT_VERIFIED_AWAITING_PUBLICATION'
    record['live_binding'] = {**bindings, 'preflight_sha256': sha(raw_proof), 'scope': record['scope'],
        'aws_speech_calls_before_publication': 0, 'bounded8_data_authority': 'PASS',
        'broad_dataset_coverage': 'NOT_SATISFIED', 'thresholds': {'ru':20, 'en':18},
        'timestamp_accuracy': 'NOT_EVALUATED', 'noise_robustness': 'NOT_EVALUATED',
        'en_spontaneous': 'NOT_EVALUATED', 'provider_admission': 'NOT_ESTABLISHED'}
    record['preflight'] = {k: proof[k] for k in ('status', 'checks', 'bounded8_data_authority',
        'broad_dataset_coverage', 'expires_at', 'aws_speech_calls')}
    record['preflight']['private_evidence_sha256'] = evidence
    record['internal_alpha_preflight']['checks'] = proof['internal_alpha_checks']
    record['owner_notice_and_jurisdiction'].update(status='PRIVATE_EVIDENCE_BOUND',
        notice_sha256=owner_receipt['notice_sha256'],
        service_terms_sha256=owner_receipt['service_terms_sha256'],
        customer_agreement_sha256=owner_receipt['customer_agreement_sha256'])
    record['environment'] = environment
    record['configuration']['unbound_actual_fields'] = []
    record['configuration']['unbound_effect'] = 'EXACT_ACTUAL_ENVIRONMENT_PRIVATE_HASH_BOUND'
    record['harness']['state'] = 'OFFLINE_CORE_UNCHANGED / LIVE_TRANSPORT_HASH_BOUND'
    record['live_plan'].update({k: plan[k] for k in ('serial', 'automatic_retries', 'poll_seconds',
        'job_timeout_seconds', 'maximum_api_calls', 'maximum_result_bytes', 'maximum_start_dispatches',
        'maximum_upload_bytes', 'maximum_download_bytes')})
    record['live_plan'].update(status='FROZEN_PRIVATE_PLAN_BOUND', primary_cases=8, technical_cases=len(plan['cases'])-8,
        primary_billable_rounded_seconds=sum((c['duration_us'] + 999999)//1000000 for c in plan['cases'] if c['primary']))
    record['budget']['frozen_reservation'] = plan['budget']
    record['budget']['pricing'] = {k: plan['pricing'][k] for k in ('usd_per_second', 'minimum_billable_seconds', 'source_url', 'verified_at')}
    return {'record': record, 'markdown': render(record)}


def render(record):
    return ('# DORA Cloud evaluation — prospective bounded eight-clip Phase A v0.2\n\n'
            'This protocol permits evidence collection only after the verified publication barrier. '
            'Original broad requirements and all 39 gate states remain unchanged. '
            'Eight clips cannot establish full provider admission, timestamp accuracy, noise robustness or English spontaneous quality.\n\n'
            'The complete normative record follows. Its JSON is identical to the companion contract; private inputs are represented only by approved aggregate facts and whole-artifact hashes.\n\n'
            '```json\n' + json_bytes(record).decode() + '```\n')


def parse_rendered(markdown):
    blocks = re.findall(r'```json\n(.*?)```', markdown, re.S)
    require(len(blocks) == 1, 'MARKDOWN_PARITY')
    return json.loads(blocks[0])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('command', choices=('draft', 'finalize'))
    parser.add_argument('--repo', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True, help='Private staging directory outside Git; publication is separate')
    for name in ('corpus', 'config', 'plan', 'preflight', 'aws', 'aws-config', 'evidence-map'):
        parser.add_argument('--' + name, type=Path)
    args = parser.parse_args()
    require(not args.output_dir.resolve().is_relative_to(args.repo.resolve()), 'OUTPUT_MUST_BE_PRIVATE_STAGING')
    if args.command == 'draft':
        record = build_draft(args.repo); bundle = {'record': record, 'markdown': render(record)}
    else:
        require(all(getattr(args, k) for k in ('corpus','config','plan','preflight','aws','aws_config','evidence_map')), 'FINAL_INPUTS_REQUIRED')
        bundle = finalize_bundle(repo=args.repo, corpus_root=args.corpus, config_path=args.config,
            plan_path=args.plan, preflight_path=args.preflight, aws_path=args.aws,
            aws_config_path=args.aws_config, evidence_paths=json.loads(args.evidence_map.read_bytes()))
    require(parse_rendered(bundle['markdown']) == bundle['record'], 'MARKDOWN_PARITY')
    disk = CorpusStore(args.output_dir, args.repo)
    disk._write(NAME + '.json', json_bytes(bundle['record']), immutable=True)
    disk._write(NAME + '.md', bundle['markdown'].encode(), immutable=True)
    print(json.dumps({'status': bundle['record']['phase_a'], 'phase_b': 'NOT_RUN', 'files': 2,
                      'json_sha256': sha(json_bytes(bundle['record']))}))


if __name__ == '__main__':
    main()
