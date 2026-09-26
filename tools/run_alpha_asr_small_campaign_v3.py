"""Frozen prospective SMALL v0.3 execution composition; CLI is information-only.

5.6C.2A.2 exercises generated files/fake devices only. The authorization string
is an API guard, not an owner decision. A later explicit execution task must pin
this published commit. Config contains locators only, never case bindings/pins.
"""
import copy
from dataclasses import dataclass
import datetime
import hashlib
import os
from pathlib import Path
import re
import subprocess
import sys
from types import MappingProxyType

import alpha_asr_small_campaign as c
import run_alpha_asr_small_campaign as v1
import run_alpha_asr_small_campaign_v2 as v2
from alpha_asr_campaign import Journal, Oracle, file_sha, save_new
from alpha_asr_campaign_device import Device

OPERATOR_ID='dora-alpha-asr-small-campaign-operator-v0.3'
AUTHORIZATION='OWNER_AUTHORIZED_5.6C.2B_V03'
BASELINE_HEAD='5c148a966a927842d15aa470d62b3629f62e354e'
BRANCH='chat/alpha-asr-runner-scope'
PROFILE_SHA='217b5662bfd22520779a2df2551f553cb17e97e11f0273e8238682a5f4b7f69f'
CONFIG_KEYS=frozenset(('operatorCommit','acceptance','work','model','native','runtimeSource','adb'))
NATIVE_NAMES=frozenset(('dora-asr-campaign','libc++_shared.so','libggml-base.so',
                        'libggml-cpu.so','libggml.so','libwhisper.so'))
EVIDENCE_PATH='docs/evidence/poc-asr-001/alpha-asr-small-campaign-execution-operator-stage0-v0.1.json'
OPERATOR_FILES=tuple(sorted((
    'tools/run_alpha_asr_small_campaign_v3.py','tools/test_alpha_asr_small_campaign_v3.py',
    'tools/run_alpha_asr_small_campaign_v2.py','tools/test_alpha_asr_small_campaign_v2.py',
    'tools/run_alpha_asr_small_campaign.py','tools/alpha_asr_small_campaign.py',
    'tools/alpha_asr_small_acceptance_manifest.py','tools/alpha_asr_pilot_manifest.py',
    'tools/test_alpha_asr_pilot_manifest.py','tools/alpha_asr_eval_text_contract.py',
    'tools/alpha_asr_campaign.py','tools/alpha_asr_campaign_device.py',
    'tools/run_alpha_asr_campaign.py','tools/alpha_asr_campaign_import.py',
    'tools/alpha_asr_campaign_native/CMakeLists.txt','tools/alpha_asr_campaign_native/main.cpp',
    'tools/alpha_asr_campaign_native/decode.c','tools/alpha_asr_campaign_native/CampaignOracle.java',
    'tools/asr_i1_synthetic_scoring_oracle/src/main/java/com/monumentogram/dora/stage0/asr/i1/AsrSyntheticScoringOracle.java')))


def config_snapshot(config):
    c.require(type(config) is dict and set(config)==CONFIG_KEYS,'CONFIG_FIELDS')
    c.require(all(type(config[k]) is str and config[k] for k in CONFIG_KEYS-{'native'}),'CONFIG_LOCATORS')
    c.require(re.fullmatch('[0-9a-f]{40}',config['operatorCommit']) is not None,'CONFIG_COMMIT')
    c.require(type(config['native']) is dict and set(config['native'])==NATIVE_NAMES
              and all(type(v) is str and v for v in config['native'].values()),'CONFIG_NATIVE')
    return copy.deepcopy(config)


def git(repo,*args):
    p=subprocess.run(['git','-C',str(repo),*args],capture_output=True,timeout=30)
    c.require(p.returncode==0,'REPOSITORY_COMMAND_UNAVAILABLE')
    return p.stdout.decode().strip()


def verify_repository(repo,expected_commit):
    c.require(type(expected_commit) is str and re.fullmatch('[0-9a-f]{40}',expected_commit), 'REPOSITORY_IDENTITY')
    c.require(git(repo,'rev-parse','HEAD')==expected_commit and git(repo,'branch','--show-current')==BRANCH
              and git(repo,'rev-parse','HEAD^')==BASELINE_HEAD and not git(repo,'status','--porcelain'), 'REPOSITORY_IDENTITY')
    record=v2.schema.parse_json((repo/EVIDENCE_PATH).read_bytes())
    c.require(record['operatorId']==OPERATOR_ID and set(record['operatorFiles'])==set(OPERATOR_FILES)
              and c.digest(record['operatorFiles'])==record['operatorCanonicalSha256'],'OPERATOR_IDENTITY')
    c.require(record['evaluationProfileSha256']==PROFILE_SHA==c.digest(c.profile()),'PROFILE_IDENTITY')
    c.validate_profile(c.profile())
    for name,pin in {**record['protectedHistoricalFiles'],**record['operatorFiles']}.items():
        v2.schema._path(name)
        raw=(repo/name).read_bytes()
        c.require(len(raw)==pin['bytes'] and hashlib.sha256(raw).hexdigest()==pin['sha256'],'OPERATOR_FILE_IDENTITY')


def no_reparse(path):
    c.require(not str(path).startswith(('\\\\','//')),'LOCAL_PATH_REQUIRED')
    for part in (path,*path.parents):
        c.require(not part.is_symlink() and not part.is_junction(),'PATH_REPARSE_POINT')


def require_private(path,repo):
    """Historical storage boundary with v3-only terminating ACL error handling."""
    root=Path(os.environ['LOCALAPPDATA'])/'DORA/private'
    no_reparse(path)
    c.require(path.resolve().is_relative_to(root.resolve()),'PRIVATE_STORAGE_CLASS_INVALID')
    c.require(not path.resolve().is_relative_to(repo.resolve()),'PRIVATE_STORAGE_INSIDE_REPOSITORY')
    c.require(not any((p/'.git').exists() for p in (path,*path.parents)),'PRIVATE_STORAGE_INSIDE_WORKTREE')
    for ancestor in (path,*path.parents):
        if not ancestor.resolve().is_relative_to(root.resolve()): break
        if not ancestor.exists(): continue
        env=dict(os.environ,DORA_PRIVATE_CHECK_PATH=str(ancestor))
        command=("$ErrorActionPreference='Stop'; $a=Get-Acl -LiteralPath $env:DORA_PRIVATE_CHECK_PATH; "
                 "if($null -eq $a){exit 2}; "
                 "$s=[System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value; "
                 "foreach($r in $a.Access){if($r.AccessControlType -eq 'Allow' "
                 "-and $r.IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value -ne $s){exit 2}}")
        p=subprocess.run(['powershell','-NoProfile','-NonInteractive','-Command',command],env=env,capture_output=True,timeout=30)
        c.require(p.returncode==0,'PRIVATE_STORAGE_ACL_INVALID')


def require_model_storage(path,repo):
    """Same owner-only local storage class as admitted 5.6A.1; model stays separate."""
    root=Path(os.environ['LOCALAPPDATA'])/'DORA/model-storage'
    no_reparse(path)
    c.require(path.resolve().is_relative_to(root.resolve())
              and path.parent.resolve()!=root.resolve()
              and not path.resolve().is_relative_to(repo.resolve())
              and not any((p/'.git').exists() for p in (path,*path.parents)), 'MODEL_STORAGE_CLASS')
    # The admitted DACL boundary is the store directory, not its ancestors.
    # Ancestors still receive the Git/reparse checks above.
    for part in (path,path.parent):
        env=dict(os.environ,DORA_MODEL_CHECK_PATH=str(part),DORA_MODEL_CHECK_DIRECTORY='1' if part==path.parent else '0')
        command=("$ErrorActionPreference='Stop'; $a=Get-Acl -LiteralPath $env:DORA_MODEL_CHECK_PATH; "
                 "$s=[System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value; "
                 "$r=@($a.GetAccessRules($true,$true,[System.Security.Principal.SecurityIdentifier])); "
                 "if($a.GetOwner([System.Security.Principal.SecurityIdentifier]).Value -ne $s -or $r.Count -ne 1 "
                 "-or $r[0].IdentityReference.Value -ne $s -or $r[0].AccessControlType -ne 'Allow' "
                 "-or $r[0].FileSystemRights -ne 'FullControl'){exit 2}; "
                 "if($env:DORA_MODEL_CHECK_DIRECTORY -eq '1' -and -not $a.AreAccessRulesProtected){exit 2}")
        p=subprocess.run(['powershell','-NoProfile','-NonInteractive','-Command',command],env=env,capture_output=True,timeout=30)
        c.require(p.returncode==0,'MODEL_STORAGE_ACL')


@dataclass(frozen=True)
class CaseInput:
    root: Path
    audio: Path
    reference: Path
    audio_bytes: int
    reference_bytes: int
    audio_sha: str
    reference_sha: str
    row_canonical: bytes
    position: int


@dataclass(frozen=True)
class Inputs:
    rows: list
    cases: object
    decoding: dict
    protocol: dict


def metadata_inputs(acceptance,repo):
    """Four fixed files, unchanged v0.2 authority; no caller case/path map."""
    raw=[]
    for relative in v2.METADATA_PATHS:
        path=acceptance/relative; no_reparse(path); require_private(path,repo)
        raw.append(v2.schema._read_local(str(path)))
    rows,bindings=v2.bind_metadata(*raw)
    root=acceptance/'materialized-5.6c11'; cases={}
    for position,(row,b) in enumerate(zip(rows,bindings),1):
        paths=[root/b[k+'RelativePath'] for k in ('audio','reference')]
        for path in paths:
            c.require(path.resolve().is_relative_to(root.resolve()),'CASE_PATH_ESCAPE')
            no_reparse(path); require_private(path,repo)
        cases[row['sampleId']]=CaseInput(root,*paths,b['audioBytes'],b['referenceBytes'],
            b['audioSha256'],b['referenceSha256'],c.canonical(row),position)
    return rows,MappingProxyType(cases)


def verify_file(path,size,sha):
    no_reparse(path)
    c.require(path.is_file() and path.stat().st_size==size and file_sha(path)==sha,'FILE_IDENTITY')


def verify_case_files(case,repo):
    for path,size,sha in ((case.audio,case.audio_bytes,case.audio_sha),
                          (case.reference,case.reference_bytes,case.reference_sha)):
        c.require(path.resolve().is_relative_to(case.root.resolve()),'CASE_PATH_ESCAPE')
        no_reparse(path); require_private(path,repo); verify_file(path,size,sha)


def verify_inputs(config):
    """Future authorized content precheck; never invoked on actual inputs in 2A.2.

    Every call repeats repository/source, storage, metadata, all content, model,
    native/runtime and profile checks. Historical run_sequence calls it before
    warmup, before each primary case, and after the final case.
    """
    config=config_snapshot(config); repo=Path(__file__).resolve().parents[1]
    verify_repository(repo,config['operatorCommit'])
    c.require(datetime.date.today()<=datetime.date(2026,10,25),'RETENTION_DEADLINE_EXCEEDED')
    acceptance=Path(config['acceptance']); work=Path(config['work']); model=Path(config['model'])
    for path in (acceptance,work): no_reparse(path); require_private(path,repo)
    require_model_storage(model,repo)
    # Output locators must never turn an admitted input into a writable work area.
    for path in (acceptance,model,Path(config['runtimeSource']),*(Path(p) for p in config['native'].values())):
        c.require(not work.resolve().is_relative_to(path.resolve())
                  and not path.resolve().is_relative_to(work.resolve()),'WORK_STORAGE_OVERLAP')
    rows,cases=metadata_inputs(acceptance,repo)
    for case in cases.values(): verify_case_files(case,repo)
    c.require(model.name==c.profile()['modelArtifact'],'MODEL_NAME')
    verify_file(model,c.MODEL_BYTES,c.MODEL_SHA)
    public=repo/'docs/evidence/poc-asr-001'
    # Public measured/decoding records contain frozen finite floating-point fields;
    # use the historical strict JSON reader here, not the metadata integer schema.
    historical=v1.strict_json((public/'alpha-asr-campaign-stage0-v0.1.json').read_bytes())
    native=historical['runtime']['nativeArtifacts']
    c.require(set(native)==NATIVE_NAMES,'NATIVE_AUTHORITY')
    for name,pin in native.items(): verify_file(Path(config['native'][name]),pin['bytes'],pin['sha256'])
    runtime=Path(config['runtimeSource']); no_reparse(runtime)
    c.require(git(runtime,'rev-parse','HEAD')==c.text.RUNTIME_COMMIT,'RUNTIME_IDENTITY')
    c.require(not git(runtime,'status','--porcelain'),'RUNTIME_SOURCE_DIRTY')
    decoding=v1.bound_json((public/'alpha-asr-decoding-profile-stage0-v0.1.json').read_bytes(),c.DECODING_SHA)
    protocol=v1.bound_json((public/'alpha-asr-measurement-protocol-stage0-v0.1.json').read_bytes(),c.PROTOCOL_SHA)
    governance=v1.strict_json((public/'alpha-asr-small-evaluation-governance-stage0-v0.1.json').read_bytes())
    c.require(c.digest(governance['resourceGates'])==c.GATES_SHA,'RESOURCE_GATES_IDENTITY')
    c.require(file_sha(runtime/'examples/miniaudio.h')==protocol['decoderSha256'],'DECODER_IDENTITY')
    return Inputs(rows,cases,decoding,protocol)


class SmallDriver(v1.SmallDriver):
    """Reuse codec/warmup/telemetry/cleanup; replace caller binding authority."""
    def __init__(self,config,device,decoding,oracle,inputs):
        # The historical parent only needs these two locators outside case().
        super().__init__({k:config[k] for k in ('work','runtimeSource')},device,copy.deepcopy(decoding),oracle)
        self.inputs=inputs
        self.repo=Path(__file__).resolve().parents[1]

    def case(self,row,position):
        case=self.inputs.cases.get(row['sampleId'])
        c.require(case is not None and case.position==position
                  and case.row_canonical==c.canonical(row),'CASE_BINDING_IDENTITY')
        verify_case_files(case,self.repo)
        reference=case.reference.read_bytes()
        c.require(len(reference)==case.reference_bytes and hashlib.sha256(reference).hexdigest()==case.reference_sha,
                  'REFERENCE_CHANGED')
        destination=self.work/'cases'/f'{position:02d}'
        self.device.execute(case.audio,row['locale'],destination)
        return v1.read_attempt(destination,row,'SUCCEEDED',self.oracle,reference.decode('utf-8'))


def campaign(config,authorization=None):
    """Only a separately owner-authorized future task may pass this guard for real."""
    c.require(authorization==AUTHORIZATION,'MEASURED_EXECUTION_NOT_AUTHORIZED')
    config=config_snapshot(config); inputs=verify_inputs(config)
    work=Path(config['work']); work.mkdir(parents=True,exist_ok=True)
    c.require(not any((work/name).exists() for name in ('campaign-started.json','attempts.sqlite','terminal-aggregate.json')),
              'EXISTING_CAMPAIGN_REQUIRES_OWNER_REVIEW')
    # Exclusive creation plus fsync is the one-shot claim; never remove this file.
    save_new(work/'campaign-started.json',{'operatorId':OPERATOR_ID,'operatorCommit':config['operatorCommit'],
             'profileSha256':c.digest(c.profile()),'privateFreezeSha256':c.FREEZE_SHA})
    journal=None; device=None; sequence_entered=False
    try:
        oracle=Oracle(work/'oracle'); oracle.compile()
        journal=Journal(work/'attempts.sqlite')
        c.require(not journal.records(),'EXISTING_ATTEMPTS_REQUIRE_OWNER_REVIEW')
        artifacts={**config['native'],'model.bin':config['model']}
        identity=c.digest({n:{'sha256':file_sha(p),'bytes':Path(p).stat().st_size} for n,p in artifacts.items()})
        device=Device(config['adb'],artifacts,identity)
        driver=SmallDriver(config,device,inputs.decoding,oracle,inputs)
        sequence_entered=True
        result=v1.run_sequence(inputs.rows,driver,journal,lambda:verify_inputs(config))
    except Exception:
        cleanup='UNVERIFIED'
        if device is not None and not sequence_entered:
            try: cleanup=device.cleanup()
            except Exception: pass
        result=c.evaluate(c.profile(),inputs.rows,[],cleanup,False)
    finally:
        if journal is not None: journal.close()
    # Exactly one terminal write, outside recovery handling. Write failure is not retried.
    save_new(work/'terminal-aggregate.json',result)
    return result


def main(argv=None):
    args=sys.argv[1:] if argv is None else argv
    if args not in ([],['profile']):
        print('{"result":"BLOCKED","code":"MEASURED_EXECUTION_REQUIRES_SEPARATE_OWNER_AUTHORITY"}')
        return 2
    print(c.canonical({'operatorId':OPERATOR_ID,'futureAuthorizationToken':AUTHORIZATION,
        'executionAuthorized':False,'configKeys':sorted(CONFIG_KEYS),'callerBindingsAllowed':False,
        'evaluationProfile':c.profile(),'evaluationProfileSha256':c.digest(c.profile())}).decode())
    return 0


if __name__=='__main__': sys.exit(main())
