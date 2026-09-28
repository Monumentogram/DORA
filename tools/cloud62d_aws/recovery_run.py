"""Prospective integration-repair campaign; original journal and operator stay frozen.

This is not a best-of retry. Four attributable pre-job configuration failures must
already be published; no accepted original job/output is eligible for this path.
All network behavior is inherited from the hash-bound original Runner.
"""
from __future__ import annotations

import argparse
from contextlib import ExitStack
from decimal import Decimal
import json
from pathlib import Path
import re
import secrets
import subprocess
import sys

from tools.cloud62d_aws import live_run as live
from tools.cloud62d_owned.corpus import CorpusStore, EASY_EN_IDS, digest, encoded, require, utc_now
from tools.cloud62d_owned.server import _ProcessLock

MARKER = 'provider-recovery-started-v03.json'
CORE_SHA256 = 'a3bb95b40f22f71ff6a81077b6d9f5b49943e96622214b6dcfeafa0444e2205e'
JOURNAL_ALGORITHM = 'SHA256_CANONICAL_JSON_SORTED_POSIX_PATH_SIZE_SHA256_EXCLUDING_ROOT_PROCESS_LOCK_V1'


def journal_manifest(root):
    """Every regular file's exact bytes, sorted by relative POSIX path; no mtimes.

    Only the root .recorder.lock is excluded because OS locking is operational.
    Symlinks/junction escapes are rejected, including linked directories.
    """
    root = Path(root).resolve()
    require(root.is_dir(), 'PRIOR_RUN_MISSING')
    rows = []
    for path in root.rglob('*'):
        require(not path.is_symlink() and path.resolve().is_relative_to(root), 'JOURNAL_LINK')
        if path.is_file() and path.relative_to(root).as_posix() != '.recorder.lock':
            raw = path.read_bytes()
            rows.append({'path': path.relative_to(root).as_posix(), 'size':len(raw), 'sha256':digest(raw)})
    return sorted(rows, key=lambda row:row['path'])


def journal_digest(root):
    return digest(encoded(journal_manifest(root)))


def _read(path):
    return json.loads(Path(path).read_bytes())


def _public_path(value):
    require(isinstance(value, str) and value.startswith('docs/contracts/') and
            value.endswith('.json') and '..' not in Path(value).parts and '\\' not in value,
            'RECOVERY_RESULT_PATH')
    return value


def _prior_facts(store, prior_root, plan):
    prior_root = Path(prior_root).resolve()
    require(prior_root != store.root and not prior_root.is_relative_to(store.root), 'PRIOR_RUN_PATH')
    old_plan = _read(prior_root/'plan.json')
    require(old_plan['cases'] == plan['cases'] and old_plan['manifest_sha256'] == plan['manifest_sha256'],
            'RECOVERY_CORPUS_OR_CASES_CHANGED')
    marker = _read(store._path('provider-started.json'))
    prior_phase = _read(prior_root/'publication.json')['published_commit']
    require(marker.get('published_commit') == prior_phase and marker.get('manifest_sha256') == plan['manifest_sha256'] and
            marker.get('plan_sha256') == digest(encoded(old_plan)) and
            marker.get('run_root_sha256') == digest(str(prior_root).encode()) and
            marker.get('run_nonce') == _read(prior_root/'run-binding.json')['run_nonce'], 'PRIOR_CANONICAL_BINDING')
    failed_ids = list(EASY_EN_IDS[:4])
    attempts = prior_root/'attempts'
    require(sorted(p.name for p in attempts.iterdir() if p.is_dir()) == sorted(failed_ids), 'PRIOR_ATTEMPT_SET')
    for case in plan['cases'][:4]:
        base = attempts/case['id']; evidence = _read(base/'evidence.json')
        require(all(evidence.get(k) == value for k, value in case.items()), 'PRIOR_CASE_CHANGED')
        require(evidence.get('status') == 'FAILED' and evidence.get('error_code') == 'BadRequestException' and
                all(evidence.get(k) is None for k in ('raw','normalized','provider_terminal','result_sha256')),
                'PRIOR_ACCEPTED_OR_UNCERTAIN_START')
        require((base/'submit.intent.json').is_file() and
                not any((base/name).exists() for name in ('submit.response.json','terminal.json','uncertain.json','timeout.json','raw-output.json')) and
                not list(base.glob('retrieved-*')), 'PRIOR_ACCEPTED_OR_UNCERTAIN_START')
    starts = []
    for path in sorted((prior_root/'calls').glob('*/intent.json')):
        call = _read(path)
        if call.get('operation') != 'start-transcription-job':
            continue
        require(call.get('service') == 'transcribe' and (path.parent/'error.json').is_file() and
                _read(path.parent/'error.json').get('code') == 'BadRequestException' and
                not (path.parent/'response.json').exists(), 'PRIOR_ACCEPTED_OR_UNCERTAIN_START')
        starts.append(call['case_id'])
    require(starts == failed_ids, 'PRIOR_START_SET')
    reserved = sum((case['duration_us']+999999)//1000000 for case in plan['cases'][:4])
    return prior_phase, reserved


def build_recovery_binding(store, plan, *, prior_root, cleanup_path, diagnostic_path,
                           prior_result_commit, prior_result_path, config_path, verify_corpus=True):
    """Offline read-only binding. Publish this exact object in prospective v0.3."""
    require(re.fullmatch('[0-9a-f]{40}', prior_result_commit), 'PRIOR_RESULT_COMMIT')
    _public_path(prior_result_path)
    require(digest(Path(live.__file__).read_bytes().replace(b'\r\n', b'\n')) == CORE_SHA256, 'FROZEN_OPERATOR_CHANGED')
    if verify_corpus:
        require(plan == live.build_plan(store, plan['pricing']), 'RECOVERY_PLAN_CHANGED')
    require(len(plan['cases']) == 19, 'RECOVERY_PLAN_CHANGED')
    require(plan['pricing']['minimum_billable_seconds'] == 0, 'RECOVERY_PRICING_MINIMUM_CHANGED')
    prior_phase, reserved = _prior_facts(store, prior_root, plan)
    cleanup = _read(cleanup_path)
    readbacks = cleanup.get('independent_readbacks', [])
    require(cleanup.get('status') == 'VERIFIED_VISIBLE_EMPTY' and cleanup.get('runtime_prefix_empty') is True and
            len(readbacks) == 2 and all(row.get('status') == 'VERIFIED_VISIBLE_EMPTY' and
                re.fullmatch('[0-9a-f]{64}', row.get('evidence_sha256','')) for row in readbacks) and
            len({row['evidence_sha256'] for row in readbacks}) == 2, 'PRIOR_CLEANUP_UNVERIFIED')
    diagnostic = _read(diagnostic_path)
    require(diagnostic.get('schema_version') == '0.2' and
            diagnostic.get('all_synthetic') is True and diagnostic.get('maximum_dispatches') == 3 and
            diagnostic.get('maximum_duration_seconds') == 3 and type(diagnostic.get('actual_dispatches')) is int and
            diagnostic['actual_dispatches'] == 1 and diagnostic.get('reserved_seconds') == 9 and
            diagnostic.get('successful_step') == 'D1' and
            diagnostic.get('diagnosis') == 'DORA_TARGET_SERVICE_FLOW_VERIFIED' and
            diagnostic.get('service_flow_verified') is True and diagnostic.get('human_speech_unlocked') is True and
            diagnostic.get('completed_result_retrieved') is True and
            diagnostic.get('cleanup_status') == 'VERIFIED_VISIBLE_EMPTY',
            'DIAGNOSTIC_RESERVATION_UNVERIFIED')
    config_raw = Path(config_path).read_bytes()
    config = json.loads(config_raw)
    require(diagnostic.get('config_sha256') == digest(config_raw) and
            re.fullmatch('[0-9a-f]{64}', config.get('template_sha256', '')) and
            diagnostic.get('template_sha256') == config['template_sha256'],
            'DIAGNOSTIC_TARGET_CONFIG_BINDING')
    # Retain the entire historical 24-second reservation. It covers the failed
    # three-second v0.1 Start plus the new ladder's maximum nine seconds.
    total = reserved + plan['budget']['reserved_seconds'] + 24
    asr = Decimal(plan['pricing']['usd_per_second']) * total
    ancillary = Decimal(plan['pricing']['ancillary_tax_upper_usd'])
    require(asr <= 2 and ancillary <= 8 and asr + ancillary <= 10, 'RECOVERY_TOTAL_BUDGET')
    return {'version':'integration-repair-v03', 'authority':'PROSPECTIVE_CONFIGURATION_REPAIR_NOT_QUALITY_RETRY',
        'zero_prior_calls_scope':'RECOVERY_CAMPAIGN_ONLY', 'prior_phase_commit':prior_phase,
        'prior_result_commit':prior_result_commit, 'prior_result_path':prior_result_path,
        'prior_result_sha256':digest((store.repo/prior_result_path).read_bytes().replace(b'\r\n',b'\n')),
        'journal_algorithm':JOURNAL_ALGORITHM, 'prior_run_journal_sha256':journal_digest(prior_root),
        'prior_marker_sha256':digest(store._path('provider-started.json').read_bytes()),
        'prior_cleanup_sha256':digest(Path(cleanup_path).read_bytes()),
        'diagnostic_receipt_sha256':digest(Path(diagnostic_path).read_bytes()),
        'diagnostic_successful_step':'D1', 'diagnostic_config_sha256':digest(config_raw),
        'diagnostic_template_sha256':config['template_sha256'],
        'recovery_driver_sha256':digest(Path(__file__).read_bytes().replace(b'\r\n',b'\n')),
        'core_operator_sha256':CORE_SHA256, 'prior_failed_primary_ids':list(EASY_EN_IDS[:4]),
        'prior_unattempted_primary_ids':list(EASY_EN_IDS[4:]), 'prior_failed_starts':4,
        'prior_accepted_jobs':0, 'prior_results':0, 'prior_reserved_seconds':reserved,
        'diagnostic_reserved_seconds':24, 'max_diagnostic_dispatches':4,
        'historical_diagnostic_reserved_capacity':8,
        'historical_diagnostic_dispatches':1, 'maximum_new_diagnostic_dispatches':3,
        'total_reserved_seconds':total, 'total_asr_upper_usd':str(asr),
        'ancillary_tax_upper_usd':str(ancillary), 'total_upper_usd':str(asr+ancillary)}


def verify_recovery_publication(publication, binding, *, allow_expired=False):
    """Reuse the original barrier, additionally bind the published failure ancestry."""
    proof = live.verify_publication(**publication, allow_expired=allow_expired)
    require(proof.get('config_sha256') == binding['diagnostic_config_sha256'],
            'DIAGNOSTIC_TARGET_CONFIG_BINDING')
    repo = Path(publication['repo']).resolve()
    git = lambda *args: live.git_output(repo, *args, binary=publication.get('git_binary','git'))
    phase = json.loads(git('show', proof['published_commit']+':'+publication['phase_a']))
    require(phase.get('recovery_binding') == binding, 'RECOVERY_PUBLICATION_BINDING')
    require(binding['prior_result_commit'] != proof['published_commit'] and
            binding['prior_phase_commit'] != binding['prior_result_commit'], 'RECOVERY_COMMIT_ORDER')
    git('merge-base','--is-ancestor',binding['prior_phase_commit'],binding['prior_result_commit'])
    git('merge-base','--is-ancestor',binding['prior_result_commit'],proof['published_commit'])
    prior = git('show', binding['prior_result_commit']+':'+_public_path(binding['prior_result_path']))
    require(digest(prior.replace(b'\r\n',b'\n')) == binding['prior_result_sha256'], 'PRIOR_RESULT_NOT_PUBLISHED')
    require(digest(Path(__file__).read_bytes().replace(b'\r\n',b'\n')) == binding['recovery_driver_sha256'],
            'RECOVERY_DRIVER_CHANGED')
    return {**proof, 'recovery_binding':binding}


class RecoveryRunner(live.Runner):
    def __init__(self, *args, recovery_inputs, recovery_binding, **kwargs):
        super().__init__(*args, **kwargs)
        self.recovery_inputs = recovery_inputs
        self.recovery_binding = recovery_binding
        old = Path(recovery_inputs['prior_root']).resolve()
        require(self.disk.root != old and not self.disk.root.is_relative_to(old) and
                not old.is_relative_to(self.disk.root), 'RECOVERY_RUN_OVERLAPS_PRIOR')

    def _admit(self, *, allow_expired=False):
        # Deletion must remain possible after loss/removal of a local audio file.
        # Its authority is still the exact published plan, marker and prior proof.
        expected = build_recovery_binding(self.store, self.plan, **self.recovery_inputs,
                                          verify_corpus=not allow_expired)
        require(self.config == _read(self.recovery_inputs['config_path']), 'RECOVERY_RUNTIME_CONFIG_CHANGED')
        require(expected == self.recovery_binding, 'RECOVERY_FROZEN_BINDING_CHANGED')
        proof = verify_recovery_publication(self.publication, expected, allow_expired=allow_expired)
        marker = self.store._path(MARKER)
        binding_path = self.disk._path('run-binding.json')
        if marker.exists():
            require(binding_path.exists() and self.disk._path('publication.json').exists(), 'RECOVERY_EVIDENCE_MISSING')
            saved = _read(marker)
            require(saved.get('run_root_sha256') == digest(str(self.disk.root).encode()) and
                    saved.get('run_nonce') == self._read('run-binding.json')['run_nonce'] and
                    saved.get('recovery_binding_sha256') == digest(encoded(expected)), 'RECOVERY_CANONICAL_BINDING')
        else:
            require(not self.disk._path('calls').exists(), 'UNPUBLISHED_RECOVERY_CALLS')
        self.disk._write('plan.json', self.plan, immutable=True)
        if self.disk._path('publication.json').exists():
            require(self._read('publication.json')['published_commit'] == proof['published_commit'], 'RECOVERY_PUBLICATION_CHANGED')
        else:
            self.disk._write('publication.json', proof, immutable=True)
        if not binding_path.exists():
            self.disk._write('run-binding.json', {'run_nonce':secrets.token_hex(16)}, immutable=True)
        self.store._write(MARKER, {'published_commit':proof['published_commit'],
            'manifest_sha256':self.plan['manifest_sha256'], 'recovery_binding_sha256':digest(encoded(expected)),
            'run_root_sha256':digest(str(self.disk.root).encode()),
            'run_nonce':self._read('run-binding.json')['run_nonce']}, immutable=True)

    def _locks(self, stack):
        for path in (self.store.root, Path(self.recovery_inputs['prior_root']).resolve(), self.disk.root):
            lock = _ProcessLock(path); stack.callback(lock.close)

    def run(self):
        admitted = False
        with ExitStack() as stack:
            self._locks(stack)
            try:
                self._admit(); admitted = True
                if not self.disk._path('campaign-halted.json').exists():
                    for case in self.plan['cases']:
                        record = self._attempt(case)
                        if case['primary'] and record['status'] != 'SUCCEEDED':
                            self.disk._write('campaign-halted.json', {'case_id':case['id'],
                                'status':record['status'], 'utc':utc_now()}, immutable=True)
                            break
                        if not self._cleanup_case(case):
                            self.disk._write('campaign-halted.json', {'case_id':case['id'],
                                'status':'CLEANUP_BLOCKED', 'utc':utc_now()}, immutable=True)
                            break
                cleanup = self.cleanup()
                self.store.validate_manifest()
                report = self.report(cleanup)
                self.disk._write('report-latest.json', report)
                return report
            except Exception:
                if admitted:
                    if not self.disk._path('campaign-halted.json').exists():
                        self.disk._write('campaign-halted.json', {'status':'UNRESOLVED_STOP_NEW_WORK','utc':utc_now()}, immutable=True)
                    try:
                        self.disk._write('report-latest.json', self.report(self.cleanup()))
                    except Exception:
                        self.disk._write('report-latest.json', self.report({'status':'CLEANUP_BLOCKED'}))
                raise

    def cleanup_verified(self):
        with ExitStack() as stack:
            self._locks(stack)
            require(self.store._path(MARKER).exists(), 'RECOVERY_NOT_STARTED')
            self._admit(allow_expired=True)
            result = self.report(self.cleanup())
            self.disk._write('report-latest.json', result)
            return result

    def report(self, cleanup=None):
        report = super().report(cleanup)
        report['budget'] = {**report['budget'],
            'all_campaign_reserved_seconds':self.recovery_binding['total_reserved_seconds'],
            'all_campaign_asr_upper_usd':self.recovery_binding['total_asr_upper_usd'],
            'all_campaign_total_upper_usd':self.recovery_binding['total_upper_usd']}
        report['recovery'] = {**self.recovery_binding,
            'original_run_verdict':'INCOMPLETE_PRESERVED_NOT_REPLACED',
            'all_campaign_primary_starts':4+report['attempt_accounting']['primary_start_dispatches'],
            'recovery_campaign_primary_starts':report['attempt_accounting']['primary_start_dispatches'],
            'campaign_halted':self.disk._path('campaign-halted.json').exists()}
        return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('binding','run','resume','cleanup','report'))
    for name in ('repo','corpus','run-root','plan','prior-run-root','prior-cleanup','diagnostics','prior-result-commit','prior-result-path'):
        parser.add_argument('--'+name, required=True)
    for name in ('config','aws','aws-config','phase-a','published-commit','preflight'):
        parser.add_argument('--'+name)
    parser.add_argument('--git', default='git')
    args = parser.parse_args()
    store = CorpusStore(args.corpus,args.repo); plan = _read(args.plan)
    require(args.config, 'RECOVERY_CONFIG_REQUIRED')
    inputs = dict(prior_root=args.prior_run_root, cleanup_path=args.prior_cleanup, diagnostic_path=args.diagnostics,
                  prior_result_commit=args.prior_result_commit, prior_result_path=args.prior_result_path,
                  config_path=args.config)
    binding = build_recovery_binding(store,plan,**inputs,verify_corpus=args.action!='cleanup')
    if args.action == 'binding':
        print(json.dumps(binding)); return
    require(all(getattr(args,name) for name in ('config','aws','aws_config','phase_a','published_commit','preflight')),
            'RECOVERY_LIVE_INPUTS_REQUIRED')
    config = _read(args.config)
    publication = dict(repo=args.repo, phase_a=args.phase_a, published_commit=args.published_commit,
        config_path=args.config,aws_path=args.aws,aws_config_path=args.aws_config,
        preflight_path=args.preflight,plan_path=args.plan,git_binary=args.git)
    from tools.cloud62d_aws.aws_prepare import Aws, assume_operator, validate_config
    api = proof_api = None
    if args.action != 'report':
        validate_config(config)
        verify_recovery_publication(publication,binding,allow_expired=args.action=='cleanup')
        proof_api = Aws(args.aws,config_file=args.aws_config)
        api = assume_operator(proof_api,config)
    runner = RecoveryRunner(store,args.run_root,config,api,plan,publication,proof_api=proof_api,
                            recovery_inputs=inputs,recovery_binding=binding)
    result = runner.report() if args.action=='report' else runner.cleanup_verified() if args.action=='cleanup' else runner.run()
    print(json.dumps({'bounded8':result['bounded8'],'provider_admission':'NOT_ESTABLISHED',
                      'original_run':'INCOMPLETE_PRESERVED'}))


if __name__ == '__main__':
    try:
        main()
    except (ValueError,live.AwsError,OSError,subprocess.SubprocessError,KeyError) as error:
        code = str(error)
        print(json.dumps({'status':'BLOCKED','error':code if re.fullmatch('[A-Z][A-Z0-9_]{0,100}',code) else 'PRIVATE_RECOVERY_ERROR'}))
        sys.exit(2)
