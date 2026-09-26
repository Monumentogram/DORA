"""Isolated ARM82 successor binding. Historical evaluator/sequencer stay unchanged.

Config supplies locators only. The separately frozen envelope hash is required
authority for data, binaries and source files. Import performs no device I/O.
"""
import copy
import datetime
import importlib.util
import json
import os
from pathlib import Path
import re
import subprocess

import alpha_asr_small_campaign as historical
import run_alpha_asr_small_campaign as historical_operator
import run_alpha_asr_small_campaign_v3 as storage
from alpha_asr_campaign import Journal, Oracle, file_sha, save_new
from alpha_asr_campaign_device import Device

OPERATOR_ID='dora-asr-small-arm82-01-operator-v0.1'
AUTHORIZATION='OWNER_AUTHORIZED_ASR_SMALL_ARM82_01_REVISED_DATA'
BASE='cfe27f84a0b843ea33f1fe7a89dd13601a555a63'
IDENTITY_FIELDS=frozenset(('profileId','campaignVersion','selectedManifestSha256',
    'transferIndexSha256','privateFreezeSha256','selectionContractSha256'))
CONFIG_KEYS=frozenset(('repo','bundle','acceptance','work','model','native','runtimeSource','adb'))
require=historical.require
digest=historical.digest
canonical=historical.canonical


def load_module(path,name):
    spec=importlib.util.spec_from_file_location(name,path)
    module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
    return module


def check_semantics(profile,original):
    require(set(profile)==set(original),'SEMANTIC_PROFILE_DELTA')
    require(all(profile[k]==original[k] for k in original if k not in IDENTITY_FIELDS),
            'SEMANTIC_PROFILE_DELTA')


def composition(repo,pins):
    require(set(pins)=={'manifest','transfer','freeze','selection'} and
            all(re.fullmatch('[0-9a-f]{64}',v) for v in pins.values()),'BINDING_PINS')
    c=load_module(Path(repo)/'tools/alpha_asr_small_campaign.py','_arm82_evaluator')
    op=load_module(Path(repo)/'tools/run_alpha_asr_small_campaign.py','_arm82_sequencer')
    c.VERSION='dora-asr-small-arm82-01-campaign-v0.1'
    c.PROFILE_ID='dora-asr-small-arm82-01-eval-profile-v0.1'
    c.MANIFEST_SHA,c.TRANSFER_SHA,c.FREEZE_SHA=(pins[k] for k in ('manifest','transfer','freeze'))
    original_profile=c.profile
    def bound_profile():
        p=original_profile();p['selectionContractSha256']=pins['selection'];return p
    c.profile=bound_profile;op.c=c
    check_semantics(c.profile(),historical.profile());c.validate_profile(c.profile())
    return c,op


def claim(work,receipt):
    require(not any((work/n).exists() for n in
            ('campaign-started.json','attempts.sqlite','terminal-aggregate.json')),
            'CAMPAIGN_ALREADY_CLAIMED')
    save_new(work/'campaign-started.json',receipt)


def check_case(row,position,case):
    require(case is not None and position==case['position'] and
            canonical(row)==canonical(case['row']),'CASE_BINDING_IDENTITY')


def private_batch(paths,repo):
    """Same v0.3 owner-only ACL predicates, batched to avoid redundant host shells."""
    root=(Path(os.environ['LOCALAPPDATA'])/'DORA/private').resolve();checks=set()
    for path in paths:
        path=Path(path);storage.no_reparse(path)
        require(path.resolve().is_relative_to(root),'PRIVATE_STORAGE_CLASS_INVALID')
        require(not path.resolve().is_relative_to(repo.resolve()) and
                not any((p/'.git').exists() for p in (path,*path.parents)),'PRIVATE_STORAGE_INSIDE_WORKTREE')
        for p in (path,*path.parents):
            if not p.resolve().is_relative_to(root):break
            if p.exists():checks.add(str(p))
    command=("$ErrorActionPreference='Stop'; $paths=@(ConvertFrom-Json -InputObject ([Console]::In.ReadToEnd())); "
       "$s=[System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value; "
       "foreach($p in $paths){$a=Get-Acl -LiteralPath $p; if($null -eq $a){exit 2}; "
       "foreach($r in $a.Access){if($r.AccessControlType -eq 'Allow' -and "
       "$r.IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value -ne $s){exit 2}}}")
    p=subprocess.run(['powershell','-NoProfile','-NonInteractive','-Command',command],
        input=json.dumps(sorted(checks)).encode(),capture_output=True,timeout=90)
    require(p.returncode==0,'PRIVATE_STORAGE_ACL_INVALID')


def pin_file(path,pin):
    storage.no_reparse(path)
    require(Path(path).is_file() and Path(path).stat().st_size==pin['bytes'] and
            file_sha(path)==pin['sha256'],'FILE_IDENTITY')


def document(path,sha):
    return historical_operator.bound_json(Path(path).read_bytes(),sha)


def verify_inputs(config,freeze_sha):
    require(type(config) is dict and set(config)==CONFIG_KEYS,'CONFIG_FIELDS')
    require(set(config['native'])==storage.NATIVE_NAMES,'NATIVE_SET')
    require(re.fullmatch('[0-9a-f]{64}',freeze_sha) is not None,'ENVELOPE_PIN')
    repo=Path(config['repo']);bundle=Path(config['bundle']);root=Path(config['acceptance']);work=Path(config['work'])
    freeze=document(bundle/'execution-freeze.json',freeze_sha)
    require(freeze['operatorId']==OPERATOR_ID and freeze['baseCommit']==BASE,'OPERATOR_AUTHORITY')
    require(storage.git(repo,'rev-parse','HEAD')==BASE and
            storage.git(repo,'branch','--show-current')=='chat/alpha-asr-runner-scope' and
            not storage.git(repo,'status','--porcelain=v1','--untracked-files=all'),'REPOSITORY_IDENTITY')
    require(datetime.date.today()<=datetime.date(2026,10,25),'RETENTION_DEADLINE_EXCEEDED')
    for name,pin in freeze['historicalFiles'].items():pin_file(repo/name,pin)
    for name,pin in freeze['operatorFiles'].items():pin_file(bundle/name,pin)
    require(file_sha(__file__)==freeze['operatorFiles'][Path(__file__).name]['sha256'],'LOADED_BINDING_IDENTITY')
    for p in (root,Path(config['model']),Path(config['runtimeSource']),bundle,*map(Path,config['native'].values())):
        require(not work.resolve().is_relative_to(p.resolve()) and not p.resolve().is_relative_to(work.resolve()),'WORK_STORAGE_OVERLAP')
    docs={name:document(root/name,sha) for name,sha in freeze['privateDocuments'].items()}
    data=docs['data-freeze.json'];manifest=docs['selected-manifest.json'];authority=docs['data-authority.json']
    require(data['manifestSha256']==digest(manifest) and data['dataAuthoritySha256']==digest(authority)
            and data['transferSha256']==digest(docs['transfer-index.json']),'DATA_FREEZE_CHAIN')
    require(manifest['dataAuthoritySha256']==digest(authority) and
            authority['parentContractSha256']==freeze['parentContractSha256'],'DATA_AUTHORITY_CHAIN')
    require(authority['partition']=='dev' and authority['selection']=={'ru':24,'en':24,'order':'RU24_THEN_EN24',
            'shortage':'STOP_NO_SUBSTITUTION','manualReplacement':False,'durationBalancing':False},'DATA_SELECTION_CONTRACT')
    inventory=docs['candidate-inventory.json'];audit=docs['exclusion-audit.json']
    selector=load_module(bundle/'selector.py','_arm82_selector')
    chosen,computed=selector.select(inventory['rows'],set(map(tuple,audit['priorSourceRecords'])),
                                  set(audit['priorAudio']),set(audit['priorParticipants']))
    require(computed['eligiblePoolSha256']==manifest['eligiblePoolSha256']==data['eligiblePoolSha256'] and
            computed['exclusionSetSha256']==manifest['exclusionSetSha256'] and
            computed['priorParticipantsSha256']==manifest['priorParticipantsSha256'],'EXCLUSIONS_IDENTITY')
    expected=[dict(r,rank=i%24+1,selectionKey=selector.rank(r)[0].hex(),eligibilityResult='ELIGIBLE') for i,r in enumerate(chosen)]
    require(canonical(expected)==canonical(manifest['samples']),'SELECTION_IDENTITY')
    pins={'manifest':digest(manifest),'transfer':digest(docs['transfer-index.json']),
          'freeze':digest(data),'selection':digest(authority)}
    c,op=composition(repo,pins);require(digest(c.profile())==freeze['profileSha256'],'PROFILE_IDENTITY')
    rows=manifest['samples'];c.validate_selection(rows);cases={};private_paths=[root,work]
    require(len(manifest['bindings'])==48,'CASE_BINDINGS')
    for position,(row,b) in enumerate(zip(rows,manifest['bindings']),1):
        require(row['sampleId']==b['sampleId'],'CASE_BINDING_IDENTITY')
        item={'row':row,'position':position}
        for kind in ('audio','reference'):
            rel=b[kind+'RelativePath'];storage.v2.schema._path(rel)
            p=root/'materialized'/rel
            require(p.resolve().is_relative_to((root/'materialized').resolve()),'CASE_PATH_ESCAPE')
            pin_file(p,b[kind]);private_paths.append(p);item[kind]=p;item[kind+'Pin']=b[kind]
            require(b[kind]['sha256']==row['audioSha256' if kind=='audio' else 'referenceTextSha256'],'CONTENT_BINDING')
        cases[row['sampleId']]=item
    for entry in docs['transfer-index.json']['files']:pin_file(root/'materialized'/entry['path'],entry)
    require(len(docs['transfer-index.json']['files'])==96,'TRANSFER_COUNT')
    private_paths.extend(root/name for name in docs);private_batch(private_paths,repo)
    model=Path(config['model']);storage.require_model_storage(model,repo)
    require(model.name==c.profile()['modelArtifact'],'MODEL_NAME')
    pin_file(model,{'bytes':c.MODEL_BYTES,'sha256':c.MODEL_SHA})
    build=document(bundle/'build-summary.json',freeze['buildSha256'])
    require(build['result']=='PASS_FROZEN_ARM82_BINARY_ADMISSION' and build['runtimeBuildCount']==1,'BUILD_ADMISSION')
    for n,pin in build['artifacts'].items():pin_file(Path(config['native'][n]),pin)
    runtime=Path(config['runtimeSource']);storage.no_reparse(runtime)
    require(storage.git(runtime,'rev-parse','HEAD')==c.text.RUNTIME_COMMIT and
            not storage.git(runtime,'status','--porcelain'),'RUNTIME_IDENTITY')
    public=repo/'docs/evidence/poc-asr-001'
    decoding=document(public/'alpha-asr-decoding-profile-stage0-v0.1.json',c.DECODING_SHA)
    protocol=document(public/'alpha-asr-measurement-protocol-stage0-v0.1.json',c.PROTOCOL_SHA)
    require(file_sha(runtime/'examples/miniaudio.h')==protocol['decoderSha256'],'DECODER_IDENTITY')
    governance=historical_operator.strict_json((public/'alpha-asr-small-evaluation-governance-stage0-v0.1.json').read_bytes())
    require(digest(governance['resourceGates'])==c.GATES_SHA,'RESOURCE_GATES_IDENTITY')
    isa=freeze['isaAdmission'];cap=isa['capabilities']
    require(cap['readErrno']==0 and cap['admitted'] is True and cap['requiredMask']==1050114 and
            cap['atHwcap']&1050114==1050114 and isa['cleanupVerified'] is True,'ISA_NOT_ADMITTED')
    return {'c':c,'op':op,'rows':rows,'cases':cases,'decoding':decoding,'freeze':freeze}


def driver(inputs,config,device,oracle):
    c,op=inputs['c'],inputs['op'];repo=Path(config['repo'])
    class BoundDriver(op.SmallDriver):
        def case(self,row,position):
            item=inputs['cases'].get(row['sampleId']);check_case(row,position,item)
            for kind in ('audio','reference'):pin_file(item[kind],item[kind+'Pin'])
            private_batch([item['audio'],item['reference']],repo)
            reference=item['reference'].read_bytes()
            require(__import__('hashlib').sha256(reference).hexdigest()==row['referenceTextSha256'],'REFERENCE_CHANGED')
            destination=self.work/'cases'/f'{position:02d}'
            self.device.execute(item['audio'],row['locale'],destination)
            return op.read_attempt(destination,row,'SUCCEEDED',self.oracle,reference.decode('utf8'))
    return BoundDriver({k:config[k] for k in ('work','runtimeSource')},device,inputs['decoding'],oracle)


def campaign(config,freeze_sha,authorization=None):
    require(authorization==AUTHORIZATION,'MEASURED_EXECUTION_NOT_AUTHORIZED')
    config=copy.deepcopy(config);inputs=verify_inputs(config,freeze_sha);c,op=inputs['c'],inputs['op']
    work=Path(config['work']);require(work.is_dir(),'WORK_DIRECTORY_REQUIRED')
    claim(work,{'operatorId':OPERATOR_ID,'executionFreezeSha256':freeze_sha,'profileSha256':digest(c.profile()),
                'baseCommit':BASE,'apiInvocationCount':1})
    journal=None;device=None;entered=False
    try:
        oracle=Oracle(work/'oracle');oracle.compile();journal=Journal(work/'attempts.sqlite')
        require(not journal.records(),'EXISTING_ATTEMPTS_REQUIRE_OWNER_REVIEW')
        artifacts={**config['native'],'model.bin':config['model']}
        identity=digest({n:{'sha256':file_sha(p),'bytes':Path(p).stat().st_size} for n,p in artifacts.items()})
        device=Device(config['adb'],artifacts,identity)
        runner=driver(inputs,config,device,oracle);entered=True
        result=op.run_sequence(inputs['rows'],runner,journal,lambda:verify_inputs(config,freeze_sha))
    except Exception:
        cleanup='UNVERIFIED'
        if device is not None and not entered:
            try:cleanup=device.cleanup()
            except Exception:pass
        result=c.evaluate(c.profile(),inputs['rows'],[],cleanup,False)
    finally:
        if journal is not None:journal.close()
    save_new(work/'terminal-aggregate.json',result)
    return result
