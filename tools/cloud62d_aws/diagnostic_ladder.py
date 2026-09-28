"""Prospective v0.2 synthetic-only D1/D2/D3 ladder, at most three new Starts.

One invocation consumes the campaign. Interrupted reservations cannot be resumed,
reset or retried. Historical primary and synthetic journals remain untouched.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
from decimal import Decimal, DecimalException
import json
from pathlib import Path
import re
import subprocess
import sys
import time
from urllib.parse import unquote, urlsplit
from urllib.request import HTTPRedirectHandler, build_opener

from tools.cloud62d_aws import aws_prepare as setup, diagnostic_run as old, live_run as live
from tools.cloud62d_aws.recovery_run import journal_digest
from tools.cloud62d_owned.corpus import CorpusStore, digest, encoded, require, utc_now
from tools.cloud62d_owned.server import _ProcessLock

SCOPE = old.SCOPE
SOURCES = old.SOURCES + ('tools/cloud62d_aws/diagnostic_ladder.py', 'tools/cloud62d_aws/recovery_run.py', 'tools/cloud62d_aws/v4_proof.py')
MARKER = 'synthetic-diagnostic-ladder-v02-started.json'
DIRECT_CHECKS = ('output_put_no_sse_headers', 'output_head_exact_cmk', 'output_cleanup_objects_versions_multipart')
INPUT_ID = 'ladder-silence-v02'


def canonical_root(repo):
    return setup.private_path(Path(repo).resolve().parent/'.dora-62d-private'/'synthetic-integration-diagnostic-v02')


def build_protocol(repo, *, config_path, aws_path, aws_config_path):
    """Offline prospective requirements; runtime proofs do not exist yet."""
    repo = Path(repo).resolve(); executed = Path(__file__).resolve().parents[2]
    for source in SOURCES:
        require((repo/source).read_bytes().replace(b'\r\n',b'\n') == (executed/source).read_bytes().replace(b'\r\n',b'\n'), 'EXECUTED_SOURCE_MISMATCH')
    config = json.loads(Path(config_path).read_bytes()); setup.validate_config(config)
    require(config['template_sha256'] == setup.digest_json(setup.template()), 'DIAGNOSTIC_TEMPLATE_CHANGED')
    require(digest(Path(live.__file__).read_bytes().replace(b'\r\n',b'\n')) == old.CORE_SHA256, 'FROZEN_OPERATOR_CHANGED')
    pricing = config['live']['pricing']; rate = Decimal(pricing['usd_per_second']); ancillary = Decimal(pricing['ancillary_tax_upper_usd'])
    # Keep the original 24-second diagnostic envelope: old 3 + new 9 fits it.
    require(rate.is_finite() and rate > 0 and ancillary.is_finite() and 0 <= ancillary <= 8 and pricing['minimum_billable_seconds'] == 0, 'DIAGNOSTIC_PRICING')
    require(rate*4069 <= 2 and rate*4069+ancillary <= 10, 'DIAGNOSTIC_BUDGET')
    historical=old.canonical_root(repo); historic_raw=(historical/'receipts/01.json').read_bytes(); historic=json.loads(historic_raw)
    require(historic.get('actual_dispatches')==1 and historic.get('attempt_status')=='FAILED' and historic.get('cleanup_status')=='VERIFIED_VISIBLE_EMPTY' and historic.get('service_flow_verified') is False,'HISTORIC_SYNTHETIC_FACTS')
    binding = {name+'_sha256':digest(Path(path).read_bytes()) for name,path in
               (('config',config_path),('aws_client',aws_path),('aws_config',aws_config_path))}
    binding.update(template_sha256=config['template_sha256'], resources_sha256=digest(encoded(config['outputs'])),
                   canonical_journal_path_sha256=digest(str(canonical_root(repo)).encode()),
                   historic_synthetic_receipt_sha256=digest(historic_raw),historic_synthetic_journal_sha256=journal_digest(historical),
                   prior_primary_result_sha256_lf=digest((repo/old.PRIOR_RESULT_PATH).read_bytes().replace(b'\r\n',b'\n')),
                   source_sha256_lf={source:digest((repo/source).read_bytes().replace(b'\r\n',b'\n')) for source in SOURCES})
    return {'schema_version':'0.2','scope':SCOPE,'status':'PROSPECTIVE_SYNTHETIC_ONLY',
            'all_synthetic':True,'maximum_dispatches':3,'maximum_duration_seconds':3,'reserved_seconds':9,
            'historical_primary_failed_starts':4,'historical_synthetic_failed_starts':1,
            'reservation':{'prior_failed_seconds':152,'future_recovery_seconds':3893,'diagnostic_envelope_seconds':24,'historical_synthetic_seconds':3,'new_ladder_seconds':9,'total_reserved_seconds':4069,'usd_per_second':str(rate),'asr_upper_usd':str(rate*4069),'ancillary_tax_upper_usd':str(ancillary),'total_upper_usd':str(rate*4069+ancillary),'cap_usd':'10'},
            'fixture':'48000 zero-valued mono PCM16 frames at 16000 Hz; WAV container',
            'same_input_object_language_and_caller':True,'automatic_retries':0,'resume_or_fourth_start':False,
            'steps':[
                {'step':'D1','output':'EXACT_DORA_BUCKET_KEY_CMK','execution_settings':'AllowDeferredExecution=false; exact DataRole','success':'DORA_TARGET_SERVICE_FLOW_VERIFIED; STOP'},
                {'step':'D2','requires':'D1 definite failure','output':'SERVICE_MANAGED_SYNTHETIC_ONLY','execution_settings':'unchanged from D1','success':'OUTPUT_PATH_SPECIFIC; STOP'},
                {'step':'D3','requires':'D2 definite failure','output':'SERVICE_MANAGED_SYNTHETIC_ONLY','execution_settings':'OMITTED; operator input authority','success':'DATA_ACCESS_ROLE_PATH_SPECIFIC; STOP','failure':'CORE_OR_INPUT_PATH_UNRESOLVED'}],
            'runtime_gates':{'preflight':'fresh read-only exact config/template and both principal KMS policy proofs',
                             'direct_s3_checks':list(DIRECT_CHECKS),'proofs_created_after_publication':True},
            'success':'COMPLETED identity-matched retrieved result with valid timestamp structure and verified cleanup',
            'human_speech_unlocked':'ONLY_D1_SUCCESS_AND_VERIFIED_CLEANUP',
            'quality':'NOT_EVALUATED','provider_admission':'NOT_ESTABLISHED','binding':binding}


def validate_runtime_proofs(config, binding, preflight_path, direct_s3_path, *, published_commit):
    proofs = [json.loads(setup.private_path(path).read_bytes()) for path in (preflight_path,direct_s3_path)]
    for proof in proofs:
        require(proof.get('config_sha256') == binding['config_sha256'] and proof.get('template_sha256') == binding['template_sha256'] and proof.get('resources_sha256') == binding['resources_sha256'], 'RUNTIME_PROOF_BINDING')
        require(proof.get('published_commit') == published_commit, 'PROOF_PUBLICATION_ORDER')
        require(datetime.now(timezone.utc) < datetime.fromisoformat(proof['expires_at']), 'RUNTIME_PROOF_EXPIRED')
    preflight,direct = proofs
    require(preflight.get('status') == 'SYNTHETIC_DIAGNOSTIC_PREFLIGHT_PASS' and preflight.get('checks') == {key:'PASS' for key in old.PREFLIGHT_KEYS}, 'DIAGNOSTIC_PREFLIGHT_NOT_PASS')
    for principal,role in (('operator','OperatorRole'),('data_role','DataRole')):
        row=preflight.get('effective_output_kms',{}).get(principal,{})
        require(row.get('principal_arn') == config['outputs'][role] and row.get('key_arn') == config['outputs']['OutputKey'] and
                sorted(row.get('actions',[])) == ['kms:Decrypt','kms:Encrypt','kms:GenerateDataKey'] and
                row.get('identity_policy') is True and row.get('key_policy') is True, 'EFFECTIVE_OUTPUT_KMS_PROOF')
    require(direct.get('status') == 'DIRECT_S3_PROOF_PASS' and direct.get('checks') == {key:'PASS' for key in DIRECT_CHECKS}, 'DIRECT_S3_PROOF_NOT_PASS')
    return {'preflight_sha256':digest(Path(preflight_path).read_bytes()),'direct_s3_sha256':digest(Path(direct_s3_path).read_bytes())}


def verify_publication(*, repo, protocol, published_commit, config_path, aws_path, aws_config_path,
                       preflight_path, direct_s3_path, git_binary='git'):
    repo=Path(repo).resolve()
    require(isinstance(published_commit,str) and re.fullmatch('[0-9a-f]{40}',published_commit), 'PUBLICATION_COMMIT')
    require(isinstance(protocol,str) and protocol.startswith('docs/contracts/') and protocol.endswith('.json') and '..' not in Path(protocol).parts and '\\' not in protocol, 'PROTOCOL_PATH')
    git=lambda *args:live.git_output(repo,*args,binary=git_binary)
    require(not git('status','--porcelain').strip(),'DIRTY_TREE')
    require(git('symbolic-ref','--short','HEAD').decode().strip() == 'chat/alpha-asr-runner-scope','WRONG_BRANCH')
    git('fetch','origin','chat/alpha-asr-runner-scope')
    require(git('rev-parse','HEAD').decode().strip() == git('rev-parse','refs/remotes/origin/chat/alpha-asr-runner-scope').decode().strip() == published_commit,'NOT_PUBLISHED_EXACT_COMMIT')
    git('merge-base','--is-ancestor',old.PRIOR_RESULT_COMMIT,published_commit)
    expected=build_protocol(repo,config_path=config_path,aws_path=aws_path,aws_config_path=aws_config_path)
    raw=git('show',published_commit+':'+protocol)
    require((repo/protocol).read_bytes().replace(b'\r\n',b'\n') == raw.replace(b'\r\n',b'\n') and json.loads(raw) == expected,'DIAGNOSTIC_PUBLICATION_BINDING')
    require(digest(git('show',old.PRIOR_RESULT_COMMIT+':'+old.PRIOR_RESULT_PATH).replace(b'\r\n',b'\n'))==expected['binding']['prior_primary_result_sha256_lf'],'PRIOR_RESULT_BINDING')
    for source,sha in expected['binding']['source_sha256_lf'].items():
        require(digest(git('show',published_commit+':'+source).replace(b'\r\n',b'\n')) == sha,'PUBLISHED_SOURCE_MISMATCH')
    proofs=validate_runtime_proofs(json.loads(Path(config_path).read_bytes()),expected['binding'],preflight_path,direct_s3_path,published_commit=published_commit)
    return {'published_commit':published_commit,'protocol_sha256':digest(raw),'binding':expected['binding'],'verified_at':utc_now(),**proofs}


def validate_managed_url(url, config, job_name):
    require(isinstance(url,str) and len(url) < 16384 and not any(ord(c)<33 for c in url),'MANAGED_RESULT_URL')
    parsed=urlsplit(url); region=config['region']; buckets=('aws-transcribe-'+region,'aws-transcribe-'+region+'-prod')
    require(parsed.scheme=='https' and parsed.username is None and parsed.password is None and parsed.port in (None,443) and not parsed.fragment,'MANAGED_RESULT_URL')
    path=unquote(parsed.path)
    require('%' not in path and '\\' not in path and all(p not in ('.','..') for p in path.split('/')),'MANAGED_RESULT_PATH')
    parts=path.strip('/').split('/')
    if parsed.hostname in ('s3.'+region+'.amazonaws.com','s3-'+region+'.amazonaws.com'):
        require(parts.pop(0) in buckets,'MANAGED_RESULT_BUCKET')
    else:
        require(parsed.hostname in tuple(bucket+'.s3.'+region+'.amazonaws.com' for bucket in buckets),'MANAGED_RESULT_HOST')
    require(len(parts)>=3 and parts[0]==config['account_id'] and parts[1]==job_name and parts[-1].endswith('.json'),'MANAGED_RESULT_IDENTITY')
    return url


def fetch_managed(url):
    class NoRedirect(HTTPRedirectHandler):
        def redirect_request(self,*args,**kwargs): return None
    with build_opener(NoRedirect).open(url,timeout=30) as response:
        require(response.geturl()==url and response.status==200,'MANAGED_RESULT_REDIRECT_OR_STATUS')
        raw=response.read(live.MAX_RESULT_BYTES+1)
    require(0<len(raw)<=live.MAX_RESULT_BYTES,'RESULT_SIZE_LIMIT')
    return raw


class LadderRunner(live.Runner):
    def __init__(self,repo,config,api,proof_api,publication,*,sleeper=time.sleep,fetcher=fetch_managed):
        root=canonical_root(repo)
        cases=[{'id':'ladder-d'+str(n),'step':'D'+str(n),'primary':False,'language':'en','speech_class':'TECHNICAL_ONLY','fixture':'silence','duration_us':3000000} for n in (1,2,3)]
        plan={'cases':cases,'maximum_api_calls':2000,'maximum_start_dispatches':3,'maximum_upload_bytes':96044,'maximum_download_bytes':3*live.MAX_RESULT_BYTES,'job_timeout_seconds':180,'poll_seconds':2}
        super().__init__(CorpusStore(root.parent/'synthetic-only-anchor',repo),root,config,api,plan,publication,sleeper=sleeper,proof_api=proof_api)
        self.disk=old.AppendOnlyDisk(root,repo); self.fetcher=fetcher

    def _request(self,case):
        request=super()._request(case)
        request['Media']['MediaFileUri']=f's3://{self.config["outputs"]["InputBucket"]}/{self.config["input_prefix"]}{INPUT_ID}.wav'
        if case['step']!='D1':
            for key in ('OutputBucketName','OutputKey','OutputEncryptionKMSKeyId'): request.pop(key)
        if case['step']=='D3': request.pop('JobExecutionSettings')
        return request

    def _upload(self):
        case={**self.plan['cases'][0],'id':INPUT_ID}; path=self._payload(case)
        self._call('shared-input','s3api','put-object','--bucket',self.config['outputs']['InputBucket'],'--key',self.config['input_prefix']+INPUT_ID+'.wav','--body',str(path),'--content-type','audio/wav','--server-side-encryption','aws:kms','--ssekms-key-id',self.config['outputs']['InputKey'],'--expected-bucket-owner',self.config['account_id'],'--metadata',json.dumps({'sha256':digest(path.read_bytes())}))

    def _attempt(self,case):
        base='attempts/'+case['id']; request=self._request(case)
        self.disk._write(base+'/request.json',request,immutable=True)
        self.disk._write('reservations/'+case['step']+'.json',{'step':case['step'],'reserved_seconds':3,'request_sha256':digest(encoded(request)),'utc':utc_now()},immutable=True)
        self.disk._write(base+'/submit.intent.json',{'request_sha256':digest(encoded(request)),'utc':utc_now()},immutable=True)
        try:
            response=self._call(case['id'],'transcribe','start-transcription-job','--cli-input-json',json.dumps(request))
        except setup.AwsError as error:
            if error.code not in ('BadRequestException','AccessDeniedException','ValidationException'): raise
            return self._failure(case,base,error.code)
        self.disk._write(base+'/submit.response.json',response,immutable=True)
        started=time.monotonic()
        while True:
            job=self._call(case['id'],'transcribe','get-transcription-job','--transcription-job-name',request['TranscriptionJobName'])['TranscriptionJob']
            require(job.get('TranscriptionJobName')==request['TranscriptionJobName'] and job.get('LanguageCode')==request['LanguageCode'] and job.get('Media')==request['Media'],'RESULT_IDENTITY_MISMATCH')
            if job['TranscriptionJobStatus'] in ('COMPLETED','FAILED'): break
            require(time.monotonic()-started < self.plan['job_timeout_seconds'],'TIMEOUT_STOP_NEW_WORK')
            self.sleeper(self.plan['poll_seconds'])
        self.disk._write(base+'/terminal.json',{'job':job,'utc':utc_now()},immutable=True)
        if job['TranscriptionJobStatus']=='FAILED': return self._failure(case,base,'ProviderJobFailed',provider=job)
        if case['step']=='D1':
            args=('--bucket',request['OutputBucketName'],'--key',request['OutputKey'],'--expected-bucket-owner',self.config['account_id'])
            head=self._call(case['id'],'s3api','head-object',*args)
            require(head.get('ServerSideEncryption')=='aws:kms' and head.get('SSEKMSKeyId')==self.config['outputs']['OutputKey'],'RESULT_OUTPUT_CMK_MISMATCH')
            require(0<head['ContentLength']<=live.MAX_RESULT_BYTES,'RESULT_SIZE_LIMIT')
            temporary=self.disk._path(base+'/retrieved-result.json')
            self._call(case['id'],'s3api','get-object',*args,str(temporary)); raw=temporary.read_bytes()
            require(len(raw)==head['ContentLength'],'RESULT_SIZE_MISMATCH')
        else:
            url=validate_managed_url(job['Transcript']['TranscriptFileUri'],self.config,request['TranscriptionJobName'])
            self.disk._write(base+'/managed-retrieval.intent.json',{'url':url,'utc':utc_now()},immutable=True)
            raw=self.fetcher(url)
        require(isinstance(raw,bytes) and 0<len(raw)<=live.MAX_RESULT_BYTES,'RESULT_SIZE_LIMIT')
        self.disk._write(base+'/raw-output.json',raw,immutable=True)
        evidence={**case,'status':'MALFORMED_RESULT','result_sha256':digest(raw),'job_name':request['TranscriptionJobName'],'terminal_observed':{'job':job},'timestamp_structure':None}
        try:
            wire=json.loads(raw)
            require(isinstance(wire,dict) and isinstance(wire.get('results'),dict),'RESULT_STRUCTURE')
            items=wire['results'].get('items')
            require(isinstance(items,list) and all(isinstance(item,dict) for item in items),'RESULT_ITEMS_STRUCTURE')
            require(wire.get('jobName')==request['TranscriptionJobName'] and wire.get('status')=='COMPLETED','RESULT_IDENTITY_MISMATCH')
            require('accountId' not in wire or str(wire['accountId'])==self.config['account_id'],'RESULT_ACCOUNT_MISMATCH')
            audit=self.core.timestamp_audit(wire['results']['items'],case['duration_us'])
            evidence['timestamp_structure']=audit
            require(not any(audit[key] for key in ('missing','malformed','non_monotonic')),'INVALID_TIMESTAMP_AUDIT')
            result=self.core.AwsRecordAdapter().terminal(self.core.Request(request['TranscriptionJobName'],request['LanguageCode'],request['Media']['MediaFileUri'],digest(encoded(request)),case['duration_us']),{'region':live.REGION,'jobName':request['TranscriptionJobName'],'language':request['LanguageCode'],'input_ref':request['Media']['MediaFileUri'],'config_sha256':digest(encoded(request)),'status':'COMPLETED','output':wire})
            require(not audit['missing'] and (not result.text.strip() or audit['pronunciation_items']>0),'MISSING_TIMESTAMPS')
            evidence.update(status='SUCCEEDED',timestamp_structure=audit)
        except (ValueError,KeyError,TypeError,IndexError,AttributeError,DecimalException): pass
        self.disk._write(base+'/evidence.json',evidence,immutable=True)
        return evidence

    def _delete_object(self,bucket,key):
        base=('--bucket',bucket,'--expected-bucket-owner',self.config['account_id'])
        self._call('cleanup','s3api','delete-object',*base,'--key',key)
        versions=self._call('cleanup','s3api','list-object-versions',*base,'--prefix',key)
        for row in versions.get('Versions',[])+versions.get('DeleteMarkers',[]):
            if row['Key']==key: self._call('cleanup','s3api','delete-object',*base,'--key',key,'--version-id',row['VersionId'])
        parts=self._call('cleanup','s3api','list-multipart-uploads',*base,'--prefix',key)
        for row in parts.get('Uploads',[]):
            if row['Key']==key: self._call('cleanup','s3api','abort-multipart-upload',*base,'--key',key,'--upload-id',row['UploadId'])

    def cleanup(self):
        # Delete jobs only with durable definitive rejection or observed terminal.
        safe=True; failures=[]
        for case in self.plan['cases']:
            base='attempts/'+case['id']
            if not self.disk._path(base+'/submit.intent.json').exists(): continue
            if not self.disk._path(base+'/evidence.json').exists(): safe=False; continue
            evidence=self._read(base+'/evidence.json')
            if evidence['status']!='FAILED' and not self.disk._path(base+'/terminal.json').exists(): safe=False; continue
            request=self._request(case)
            try:
                self._call(case['id'],'transcribe','delete-transcription-job','--transcription-job-name',request['TranscriptionJobName'])
            except setup.AwsError as error:
                if error.code not in ('BadRequestException','NotFoundException'): failures.append('JOB_CLEANUP_FAILED')
            except (ValueError,OSError,subprocess.SubprocessError,TimeoutError): failures.append('JOB_CLEANUP_FAILED')
        if safe:
            request=self._request(self.plan['cases'][0])
            for bucket,key in ((self.config['outputs']['InputBucket'],self.config['input_prefix']+INPUT_ID+'.wav'),(request['OutputBucketName'],request['OutputKey'])):
                try: self._delete_object(bucket,key)
                except (ValueError,setup.AwsError,OSError,subprocess.SubprocessError,TimeoutError): failures.append('OBJECT_CLEANUP_FAILED')
        else: failures.append('UNRESOLVED_JOB_RETAINED')
        try:
            for kind in ('Input','Output'):
                args=('--bucket',self.config['outputs'][kind+'Bucket'],'--expected-bucket-owner',self.config['account_id'])
                for op,keys in (('list-object-versions',('Versions','DeleteMarkers')),('list-multipart-uploads',('Uploads',)),('list-objects-v2',('Contents',))):
                    value=self._call('cleanup','s3api',op,*args,api=self.proof_api)
                    require(not value.get('IsTruncated') and not value.get('NextToken'),'INCOMPLETE_CLEANUP_READBACK')
                    if any(value.get(key) for key in keys): failures.append('TASK_BUCKET_NOT_EMPTY')
            jobs=self._call('cleanup','transcribe','list-transcription-jobs','--job-name-contains',self.config['job_prefix'],api=self.proof_api)
            if jobs.get('TranscriptionJobSummaries') or jobs.get('NextToken'): failures.append('TASK_JOBS_NOT_EMPTY')
        except (ValueError,setup.AwsError,OSError,subprocess.SubprocessError,TimeoutError): failures.append('CLEANUP_READBACK_FAILED')
        result={'status':'CLEANUP_BLOCKED' if failures else 'VERIFIED_VISIBLE_EMPTY','failures':failures,'verified_at':utc_now(),'provider_internal_purge':'NOT_CLAIMED'}
        self.disk._write('cleanup-latest.json',result)
        return result

    def run(self):
        proof=verify_publication(**self.publication)
        require(encoded(self.config)==encoded(json.loads(Path(self.publication['config_path']).read_bytes())),'DIAGNOSTIC_CONFIG_CHANGED')
        lock=_ProcessLock(self.disk.root)
        try:
            anchor=CorpusStore(self.disk.root.parent,self.store.repo)
            marker={'journal_path_sha256':digest(str(self.disk.root).encode()),'protocol_sha256':proof['protocol_sha256'],'published_commit':proof['published_commit']}
            if anchor._path(MARKER).exists():
                require(json.loads(anchor._path(MARKER).read_bytes())==marker,'LADDER_PUBLICATION_CHANGED')
                require(self.disk._path('campaign.json').exists(),'DIAGNOSTIC_JOURNAL_MISSING')
                if self.disk._path('receipt.json').exists(): return self._read('receipt.json')
                raise ValueError('CAMPAIGN_CONSUMED_RECONCILIATION_REQUIRED')
            require(not self.disk._path('calls').exists() and not self.disk._path('reservations').exists(),'UNBOUND_PRIOR_CALLS')
            anchor._write(MARKER,marker,immutable=True)
            self.disk._write('campaign.json',{'maximum_dispatches':3,'reserved_seconds':9,'scope':SCOPE},immutable=True)
            self.disk._write('publication.json',proof,immutable=True)
            records=[]; diagnosis='CORE_OR_INPUT_PATH_UNRESOLVED'; successful=None; active_case=None
            try:
                self._upload()
                for case in self.plan['cases']:
                    active_case=case
                    record=self._attempt(case); records.append(record)
                    if record['status']=='SUCCEEDED':
                        successful=case['step']; diagnosis={'D1':'DORA_TARGET_SERVICE_FLOW_VERIFIED','D2':'OUTPUT_PATH_SPECIFIC','D3':'DATA_ACCESS_ROLE_PATH_SPECIFIC'}[successful]; break
                    if record['status']!='FAILED': diagnosis='UNRESOLVED_STOP'; break
            except (ValueError,setup.AwsError,OSError,subprocess.SubprocessError,TimeoutError,KeyError,TypeError) as error:
                diagnosis='UNRESOLVED_STOP'
                self.disk._write('interruption.json',{'utc':utc_now(),'private_error':str(error)},immutable=True)
                if active_case is not None:
                    base='attempts/'+active_case['id']
                    if not self.disk._path(base+'/evidence.json').exists():
                        record={**active_case,'status':'UNRESOLVED','result_sha256':None}
                        self.disk._write(base+'/evidence.json',record,immutable=True); records.append(record)
            cleanup=self.cleanup(); target=successful=='D1' and cleanup['status']=='VERIFIED_VISIBLE_EMPTY'
            starts=sum(json.loads(p.read_bytes()).get('operation')=='start-transcription-job' for p in self.disk._path('calls').glob('*/intent.json'))
            receipt={'schema_version':'0.2','scope':SCOPE,'all_synthetic':True,'maximum_dispatches':3,'maximum_duration_seconds':3,'reserved_seconds':9,'actual_dispatches':starts,
                     'successful_step':successful,'diagnosis':diagnosis,'service_flow_verified':target,'human_speech_unlocked':target,'completed_result_retrieved':successful is not None,
                     'cleanup_status':cleanup['status'],'steps':records,'config_sha256':proof['binding']['config_sha256'],'template_sha256':proof['binding']['template_sha256'],
                     'diagnostic_driver_sha256':proof['binding']['source_sha256_lf']['tools/cloud62d_aws/diagnostic_ladder.py'],'published_commit':proof['published_commit'],'protocol_sha256':proof['protocol_sha256'],'protocol_path':self.publication['protocol'],
                     'quality':'NOT_EVALUATED','provider_admission':'NOT_ESTABLISHED','verified_at':utc_now()}
            self.disk._write('receipt.json',receipt,immutable=True)
            return receipt
        finally: lock.close()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=('binding','run'))
    parser.add_argument('--repo',type=Path,default=Path(__file__).resolve().parents[2])
    for name in ('config','aws','aws-config'): parser.add_argument('--'+name,type=Path,required=True)
    for name in ('protocol','published-commit','preflight','direct-s3'): parser.add_argument('--'+name)
    parser.add_argument('--git',default='git'); args=parser.parse_args()
    paths=dict(config_path=args.config,aws_path=args.aws,aws_config_path=args.aws_config)
    if args.action=='binding': print(json.dumps(build_protocol(args.repo,**paths))); return
    publication=dict(repo=args.repo,protocol=args.protocol,published_commit=args.published_commit,preflight_path=args.preflight,direct_s3_path=args.direct_s3,git_binary=args.git,**paths)
    verify_publication(**publication)
    config=json.loads(args.config.read_bytes()); proof=setup.Aws(args.aws,config_file=args.aws_config); api=setup.assume_operator(proof,config)
    receipt=LadderRunner(args.repo,config,api,proof,publication).run()
    print(json.dumps({key:receipt[key] for key in ('diagnosis','actual_dispatches','successful_step','cleanup_status','human_speech_unlocked')}))


if __name__=='__main__':
    try: main()
    except (ValueError,setup.AwsError,OSError,subprocess.SubprocessError,KeyError,TypeError) as error:
        code=str(error)
        print(json.dumps({'status':'BLOCKED','error':code if re.fullmatch('[A-Z][A-Z0-9_]{0,100}',code) else 'PRIVATE_LADDER_ERROR'})); sys.exit(2)
