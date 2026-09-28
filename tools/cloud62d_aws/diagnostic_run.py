"""One synthetic integration attempt per invocation, never owned speech or WER.

Eight lifetime reservations share one canonical private journal. A published exact
protocol is mandatory; changing infrastructure requires a newly bound publication.
The original live operator and its failed journal remain untouched.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
from decimal import Decimal
import json
from pathlib import Path
import re
import subprocess
import sys
import time

from tools.cloud62d_aws import aws_prepare as setup, live_run as live
from tools.cloud62d_owned.corpus import CorpusStore, digest, encoded, require, utc_now
from tools.cloud62d_owned.server import _ProcessLock

PRIOR_RESULT_COMMIT = '39cc24f3dc5675a84cafd246a730f711c00ffa89'
PRIOR_RESULT_PATH = 'docs/contracts/DORA_CLOUD_62D_BOUNDED8_FIRST_RUN_RESULT_V0_1.json'
CORE_SHA256 = 'a3bb95b40f22f71ff6a81077b6d9f5b49943e96622214b6dcfeafa0444e2205e'
SOURCES = ('tools/cloud62d_aws/diagnostic_run.py', 'tools/cloud62d_aws/live_run.py',
           'tools/cloud62d_aws/aws_prepare.py', 'tools/cloud62d_aws/watchdog.py',
           'tools/cloud62d_owned/corpus.py', 'tools/cloud62d_owned/server.py', 'tools/cloud62d_prepare.py')
PREFLIGHT_KEYS = tuple(f'PREFLIGHT-{i:02}' for i in (1,3,4,5,6,7,8))
SCOPE = 'SYNTHETIC_NON_SPEECH_INTEGRATION_DIAGNOSTIC_ONLY'


def canonical_root(repo):
    return setup.private_path(Path(repo).resolve().parent / '.dora-62d-private' / 'synthetic-integration-diagnostic-v1')


def build_protocol(repo, *, config_path, aws_path, aws_config_path, preflight_path):
    """Offline public-safe prospective binding; contains digests, never private values."""
    repo = Path(repo).resolve()
    executed_repo = Path(__file__).resolve().parents[2]
    for relative in SOURCES:
        require((repo/relative).read_bytes().replace(b'\r\n',b'\n') == (executed_repo/relative).read_bytes().replace(b'\r\n',b'\n'), 'EXECUTED_SOURCE_MISMATCH')
    files = {name:Path(path) for name,path in (('config',config_path),('aws_client',aws_path),('aws_config',aws_config_path),('preflight',preflight_path))}
    config = json.loads(files['config'].read_bytes()); setup.validate_config(config)
    require(config.get('template_sha256') == setup.digest_json(setup.template()), 'DIAGNOSTIC_TEMPLATE_CHANGED')
    require(digest(Path(live.__file__).read_bytes().replace(b'\r\n',b'\n')) == CORE_SHA256, 'FROZEN_OPERATOR_CHANGED')
    pricing = config['live']['pricing']
    rate, ancillary = Decimal(pricing['usd_per_second']), Decimal(pricing['ancillary_tax_upper_usd'])
    require(rate.is_finite() and rate > 0 and ancillary.is_finite() and 0 <= ancillary <= 8 and pricing['minimum_billable_seconds'] == 0, 'DIAGNOSTIC_PRICING')
    total = rate * 4069 + ancillary
    require(rate * 4069 <= 2, 'DIAGNOSTIC_ASR_BUDGET')
    require(total <= 10, 'DIAGNOSTIC_TOTAL_BUDGET')
    binding = {name+'_sha256':digest(path.read_bytes()) for name,path in files.items()}
    binding.update(template_sha256=config['template_sha256'],
        canonical_journal_path_sha256=digest(str(canonical_root(repo)).encode()),
        source_sha256_lf={path:digest((repo/path).read_bytes().replace(b'\r\n',b'\n')) for path in SOURCES},
        prior_result_sha256_lf=digest((repo/PRIOR_RESULT_PATH).read_bytes().replace(b'\r\n',b'\n')))
    return {'schema_version':'1.0', 'scope':SCOPE, 'status':'PROSPECTIVE_SYNTHETIC_ONLY',
        'prior_result_commit':PRIOR_RESULT_COMMIT, 'prior_result_path':PRIOR_RESULT_PATH,
        'prior_primary_failures_preserved':4, 'prior_unattempted_english_preserved':4,
        'all_synthetic':True, 'maximum_dispatches':8, 'maximum_duration_seconds':3,
        'fixture':'48000 zero-valued mono PCM16 frames at 16000 Hz; WAV container',
        'per_invocation_dispatches':1, 'automatic_retries':0,
        'reservation':{'prior_failed_seconds':152,'future_recovery_seconds':3893,'diagnostic_seconds':24,
            'total_seconds':4069,'usd_per_second':str(rate),'ancillary_tax_upper_usd':str(ancillary),'total_upper_usd':str(total),'cap_usd':'10'},
        'success':'Accepted COMPLETED job, retrieved structurally valid result, durable private evidence and independently verified empty task buckets/jobs',
        'quality':'NOT_EVALUATED', 'human_speech_authorized':False, 'provider_admission':'NOT_ESTABLISHED',
        'binding':binding}


def render_protocol(protocol):
    return '# DORA 6.2D — bounded non-speech integration diagnostic v0.1\n\n' + (
        'Prospective permission diagnosis only. This does not authorize another human recording upload. '
        'The four original failed primary attempts and four unattempted English records remain historical facts.\n\n'
        'One explicit invocation can submit one three-second silent WAV. All eight possible attempts remain reserved, '
        'including failures and uncertain submissions. No automatic retry or reset is available. '
        'The canonical private journal keeps every reservation, API intent, response, result and cleanup observation. '
        'A failed or uncertain cleanup blocks subsequent attempts. A verified service flow ends diagnostics.\n\n'
        'Before execution, publish the fully bound JSON with the matching code, independently refetch its exact commit, '
        'and verify current private configuration and preflight digests. A later configuration change requires a new '
        'published binding and consumes the next reservation in the same journal. The JSON below is authoritative.\n\n'
        'These diagnostics do not measure WER, timing accuracy or quality, grant admission, complete block 6, or open 6.3. '
        'Actual service integration remains unverified until the receipt proves a completed retrieved result and cleanup.\n\n'
        '```json\n' + json.dumps(protocol,ensure_ascii=False,indent=2) + '\n```\n')


def verify_publication(*, repo, protocol, published_commit, config_path, aws_path, aws_config_path, preflight_path, git_binary='git'):
    repo = Path(repo).resolve()
    require(re.fullmatch('[0-9a-f]{40}',published_commit), 'PUBLICATION_COMMIT')
    require(isinstance(protocol,str) and protocol.startswith('docs/contracts/') and protocol.endswith('.json') and '..' not in Path(protocol).parts and '\\' not in protocol, 'PROTOCOL_PATH')
    git = lambda *args:live.git_output(repo,*args,binary=git_binary)
    require(not git('status','--porcelain').strip(),'DIRTY_TREE')
    require(git('symbolic-ref','--short','HEAD').decode().strip()=='chat/alpha-asr-runner-scope','WRONG_BRANCH')
    git('fetch','origin','chat/alpha-asr-runner-scope')
    require(git('rev-parse','HEAD').decode().strip()==git('rev-parse','refs/remotes/origin/chat/alpha-asr-runner-scope').decode().strip()==published_commit,'NOT_PUBLISHED_EXACT_COMMIT')
    git('merge-base','--is-ancestor',PRIOR_RESULT_COMMIT,published_commit)
    raw = git('show',published_commit+':'+protocol)
    require((repo/protocol).read_bytes().replace(b'\r\n',b'\n')==raw.replace(b'\r\n',b'\n'),'PROTOCOL_WORKTREE_CHANGED')
    expected = build_protocol(repo,config_path=config_path,aws_path=aws_path,aws_config_path=aws_config_path,preflight_path=preflight_path)
    require(json.loads(raw)==expected,'DIAGNOSTIC_PUBLICATION_BINDING')
    prior = git('show',PRIOR_RESULT_COMMIT+':'+PRIOR_RESULT_PATH)
    require(digest(prior.replace(b'\r\n',b'\n'))==expected['binding']['prior_result_sha256_lf'],'PRIOR_RESULT_BINDING')
    proof = json.loads(Path(preflight_path).read_bytes())
    require(proof.get('status')=='SYNTHETIC_DIAGNOSTIC_PREFLIGHT_PASS' and proof.get('config_sha256')==expected['binding']['config_sha256'] and proof.get('template_sha256')==expected['binding']['template_sha256'] and proof.get('checks')=={key:'PASS' for key in PREFLIGHT_KEYS},'DIAGNOSTIC_PREFLIGHT_NOT_PASS')
    require(datetime.now(timezone.utc)<datetime.fromisoformat(proof['expires_at']),'DIAGNOSTIC_PREFLIGHT_EXPIRED')
    return {'published_commit':published_commit,'protocol_sha256':digest(raw),'verified_at':utc_now(),'binding':expected['binding']}


class AppendOnlyDisk(CorpusStore):
    def _write(self, relative, value, *, immutable=False):
        # Original Runner emits mutable convenience cleanup summaries. Keep each
        # as an immutable snapshot; its API intents/evidence already are immutable.
        if not immutable:
            folder = self._path('snapshots/'+relative)
            relative = 'snapshots/'+relative+f'/{len(list(folder.glob("*.json"))):06}.json'
        return super()._write(relative,value,immutable=True)


class DiagnosticRunner(live.Runner):
    def __init__(self, repo, config, api, proof_api, publication, *, sleeper=time.sleep):
        root = canonical_root(repo)
        cases = [{'id':f'diag-silence-{n:02}','primary':False,'language':'en','speech_class':'TECHNICAL_ONLY','fixture':'silence','duration_us':3000000} for n in range(1,9)]
        plan = {'cases':cases,'maximum_api_calls':4000,'maximum_start_dispatches':8,
            'maximum_upload_bytes':8*96044,'maximum_download_bytes':8*live.MAX_RESULT_BYTES,
            'job_timeout_seconds':180,'poll_seconds':2}
        super().__init__(CorpusStore(root.parent/'synthetic-only-anchor',repo),root,config,api,plan,publication,sleeper=sleeper,proof_api=proof_api)
        self.disk = AppendOnlyDisk(root,repo)

    def run_once(self, number):
        require(type(number) is int and 1<=number<=8,'ATTEMPT_NUMBER')
        proof = verify_publication(**self.publication)
        require(digest(encoded(self.config))==digest(encoded(json.loads(Path(self.publication['config_path']).read_bytes()))),'DIAGNOSTIC_CONFIG_CHANGED')
        with_lock = _ProcessLock(self.disk.root)
        try:
            anchor = CorpusStore(self.disk.root.parent,self.store.repo)
            marker = 'synthetic-diagnostic-anchor-v1.json'
            if anchor._path(marker).exists():
                require(self.disk._path('campaign.json').exists(),'DIAGNOSTIC_JOURNAL_MISSING')
                require(json.loads(anchor._path(marker).read_bytes())=={'journal_path_sha256':digest(str(self.disk.root).encode()),'scope':SCOPE},'DIAGNOSTIC_CANONICAL_JOURNAL')
            receipt_path = f'receipts/{number:02}.json'
            if self.disk._path(receipt_path).exists():
                previous = self._read(receipt_path)
                require(previous['config_sha256']==proof['binding']['config_sha256'] and previous['template_sha256']==proof['binding']['template_sha256'],'ATTEMPT_CONFIGURATION_CHANGED')
                return previous
            reservations = sorted(self.disk._path('reservations').glob('*.json'))
            require([p.name for p in reservations]==[f'{n:02}.json' for n in range(1,number)],'ATTEMPT_ALREADY_RESERVED_OR_OUT_OF_ORDER')
            for n in range(1,number):
                previous = self._read(f'receipts/{n:02}.json')
                require(previous['cleanup_status']=='VERIFIED_VISIBLE_EMPTY' and previous['attempt_status']!='UNRESOLVED','PRIOR_CLEANUP_OR_RECONCILIATION_REQUIRED')
                require(not previous['service_flow_verified'],'DIAGNOSTIC_ALREADY_VERIFIED')
            self.disk._write('campaign.json',{'scope':SCOPE,'maximum_dispatches':8,'maximum_duration_seconds':3,'reserved_seconds':24},immutable=True)
            anchor._write(marker,{'journal_path_sha256':digest(str(self.disk.root).encode()),'scope':SCOPE},immutable=True)
            self.disk._write(f'publications/{number:02}.json',proof,immutable=True)
            self.disk._write(f'reservations/{number:02}.json',{'attempt':number,'reserved_seconds':3,'lifetime_reserved_seconds':24,'utc':utc_now(),'config_sha256':proof['binding']['config_sha256'],'template_sha256':proof['binding']['template_sha256']},immutable=True)
            case = self.plan['cases'][number-1]
            record = None
            try:
                record = self._attempt(case)
            except (ValueError,setup.AwsError,subprocess.SubprocessError,OSError,TimeoutError,KeyError) as error:
                code = str(error)
                self.disk._write(f'interruptions/{number:02}.json',{'status':'UNRESOLVED','error':code if re.fullmatch('[A-Z][A-Z0-9_]{0,100}',code) else 'PRIVATE_DIAGNOSTIC_ERROR','utc':utc_now()},immutable=True)
            original_cases = self.plan['cases']
            try:
                self.plan['cases'] = [case]
                cleanup = self.cleanup()
            except (ValueError,setup.AwsError,subprocess.SubprocessError,OSError,TimeoutError,KeyError):
                cleanup = {'status':'CLEANUP_BLOCKED'}
            finally:
                self.plan['cases'] = original_cases
            starts = sum(json.loads(p.read_bytes()).get('operation')=='start-transcription-job' for p in self.disk._path('calls').glob('*/intent.json'))
            succeeded = bool(record and record['status']=='SUCCEEDED' and record.get('result_sha256') and record.get('terminal_observed',{}).get('job',{}).get('TranscriptionJobStatus')=='COMPLETED')
            receipt = {'all_synthetic':True,'maximum_dispatches':8,'maximum_duration_seconds':3,'actual_dispatches':starts,
                'reserved_seconds':24,'attempt':number,'attempt_status':record['status'] if record else 'UNRESOLVED',
                'cleanup_status':cleanup['status'],'service_flow_verified':succeeded and cleanup['status']=='VERIFIED_VISIBLE_EMPTY',
                'completed_result_retrieved':succeeded,'result_sha256':record.get('result_sha256') if record else None,
                'evidence_sha256':digest(self.disk._path('attempts/'+case['id']+'/evidence.json').read_bytes()) if record else None,
                'config_sha256':proof['binding']['config_sha256'],'template_sha256':proof['binding']['template_sha256'],
                'diagnostic_driver_sha256':proof['binding']['source_sha256_lf']['tools/cloud62d_aws/diagnostic_run.py'],
                'protocol_sha256':proof.get('protocol_sha256'),
                'protocol_path':self.publication['protocol'],
                'published_commit':proof['published_commit'],'verified_at':utc_now(),'quality':'NOT_EVALUATED','provider_admission':'NOT_ESTABLISHED'}
            self.disk._write(receipt_path,receipt,immutable=True)
            return receipt
        finally:
            with_lock.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=['draft','run-one'])
    parser.add_argument('--repo',type=Path,default=Path(__file__).resolve().parents[2])
    parser.add_argument('--config',type=Path,required=True); parser.add_argument('--aws',type=Path,required=True)
    parser.add_argument('--aws-config',type=Path,required=True); parser.add_argument('--preflight',type=Path,required=True)
    parser.add_argument('--protocol',required=True); parser.add_argument('--published-commit'); parser.add_argument('--attempt',type=int)
    parser.add_argument('--git',default='git')
    args = parser.parse_args()
    paths = dict(config_path=args.config,aws_path=args.aws,aws_config_path=args.aws_config,preflight_path=args.preflight)
    if args.action=='draft':
        result = build_protocol(args.repo,**paths)
        destination=args.repo/args.protocol
        require(destination.resolve().is_relative_to((args.repo/'docs/contracts').resolve()) and destination.suffix=='.json','PROTOCOL_PATH')
        require(not destination.exists(),'PROTOCOL_ALREADY_EXISTS')
        companion=args.repo/'docs/stage0'/destination.with_suffix('.md').name
        require(not companion.exists(),'PROTOCOL_ALREADY_EXISTS')
        destination.write_bytes(encoded(result))
        companion.write_text(render_protocol(result),encoding='utf-8',newline='\n')
        print('Prospective synthetic diagnostic draft saved; no AWS call made.'); return
    publication = dict(repo=args.repo,protocol=args.protocol,published_commit=args.published_commit,git_binary=args.git,**paths)
    verify_publication(**publication)
    config = json.loads(args.config.read_bytes())
    proof = setup.Aws(str(args.aws),config_file=args.aws_config)
    runtime = setup.assume_operator(proof,config)
    receipt=DiagnosticRunner(args.repo,config,runtime,proof,publication).run_once(args.attempt)
    print(json.dumps({key:receipt[key] for key in ('attempt','actual_dispatches','attempt_status','cleanup_status','service_flow_verified')}))


if __name__=='__main__':
    try: main()
    except (ValueError,setup.AwsError,OSError,subprocess.SubprocessError,KeyError,TypeError) as error:
        code=str(error)
        print(json.dumps({'status':'BLOCKED','error':code if re.fullmatch('[A-Z][A-Z0-9_]{0,100}',code) else 'PRIVATE_DIAGNOSTIC_ERROR'})); sys.exit(2)
