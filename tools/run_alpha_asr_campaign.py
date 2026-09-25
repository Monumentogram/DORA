"""Private 5.4 operator entry point: preflight, then explicitly frozen campaign.

Usage: python -B tools/run_alpha_asr_campaign.py preflight|campaign PRIVATE_CONFIG
Only aggregate progress goes to stdout. Config, journals, hypotheses and receipts
must reside in the owner-only private store, outside every Git worktree.
"""
import datetime
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
import traceback
import wave

import alpha_asr_campaign as c
import alpha_asr_campaign_device as device
import alpha_asr_campaign_import as transfer
import alpha_asr_eval_text_contract as frozen


def utc(): return datetime.datetime.now(datetime.timezone.utc).isoformat()


def protocol(decoder_sha):
    return {'version':'dora-alpha-asr-measurement-v0.1',
        'duration':'Decoded mono float32 16000 Hz PCM frames; integer microseconds round-half-up; original manifest MP3 duration binding retained separately',
        'decoder':'miniaudio 0.11.24 from pinned whisper.cpp examples/miniaudio.h',
        'decoderSha256':decoder_sha,'decoderConfig':{'format':'float32','channels':1,'sampleRate':16000,
            'resampling':'miniaudio default linear resampler; no normalization, filtering, trimming or VAD'},
        'modelLoad':'steady_clock microseconds around whisper_init_from_file_with_params only; fresh process/context every case',
        'inference':'steady_clock microseconds around whisper_full only; excludes decode/load/text persistence',
        'rtf':'inferenceElapsedMicros / decodedAudioDurationMicros',
        'memory':'Sampled process PSS from /proc/self/smaps_rollup Pss KiB*1024; native allocated heap mallinfo().uordblks bytes',
        'memoryWindow':'Before decode until after whisper_free; synchronous samples around model load, after inference and before free',
        'memoryIntervalMs':100,'memoryMaxGapMs':1000,'memoryPeakMeaning':'MAXIMUM_OBSERVED_SAMPLE_NOT_CONTINUOUS_HIGH_WATER_MARK',
        'thermal':'ADB dumpsys thermalservice Thermal Status; 0..6; no override',
        'thermalPollDelayMs':1000,'thermalMaximumGapMs':3000,
        'power':'dumpsys battery before/after each case; initial/final environment, brightness and radio settings; no settings changed',
        'timeoutSeconds':600,'nativeSafetyAlarmSeconds':605,
        'termination':'No PID signalling; on timeout wait for owned native alarm and verify remote absence before cleanup',
        'retryPolicy':'No quality retry. At most one identical-config infrastructure retry permitted; this execution stops on first failure with all attempts retained, no automatic retry',
        'invalidation':'Any shared defect, missing required metric, frozen-file drift, oracle rejection or cleanup failure stops campaign; preserve original attempts',
        'order':'Selected manifest order: 24 ru followed by 24 en',
        'warmup':'One deterministic generated 3-second silent WAV full inference before corpus; no per-case warmup or repetitions',
        'preflightCodecCheck':'Pinned upstream jfk.mp3 decode-only; not corpus, not scored',
        'hypothesis':'Exact concatenation of whisper_full_get_segment_text in segment order; no trimming, separators or correction',
        'pssGate':'NOT_EVALUATED','nativeHeapGate':'NOT_EVALUATED','rtfGate':'NOT_EVALUATED',
        'thresholdState':'PROPOSED_NOT_APPROVED','trimEvents':'UNAVAILABLE_NATIVE_EXECUTABLE_HAS_NO_ANDROID_COMPONENT_CALLBACK',
        'oom':'Preserve native exit/result/error and incomplete evidence; do not infer OOM solely from a missing output or generic exit signal',
        'severeThermal':'Any observed status >=3 is retained as operational failure and stops the campaign after preserving the current attempt; no temperature threshold',
        'timestampQuality':'NOT_EVALUABLE'}


def setup(config):
    repo=Path(__file__).resolve().parents[1]; prior=Path(config['prior']); work=Path(config['work'])
    for name in ('work','corpus','archive'): require_private(Path(config[name]),repo)
    work.mkdir(parents=True,exist_ok=True)
    frozen.check_environment()
    old=json.loads((repo/'docs/evidence/poc-asr-001/alpha-asr-runner-preflight-stage0-v0.1.json').read_bytes())
    model=prior/'ggml-base-q5_1.bin'
    c.require(model.stat().st_size==59707625 and c.file_sha(model)==frozen.MODEL_SHA256,'MODEL_IDENTITY_MISMATCH')
    git=Path(config['git'])
    def gitout(path,*args):
        p=subprocess.run([str(git),'-c','safe.directory='+path.as_posix(),'-C',str(path),*args],capture_output=True,timeout=30)
        c.require(p.returncode==0,'GIT_IDENTITY_FAILED'); return p.stdout.decode().strip()
    c.require(gitout(repo,'rev-parse','HEAD')=='e91b45eefbad4609deb8a83d0823d7b090830700','BASELINE_CHANGED')
    c.require(gitout(prior/'source','rev-parse','HEAD')==frozen.RUNTIME_COMMIT
        and not gitout(prior/'source','status','--porcelain=v1'),'RUNTIME_SOURCE_CHANGED')
    for row in old['implementationPreservation']['files']:
        c.require(c.file_sha(repo/row['file'])==row['finalSha256'],'53C_CHANGED')
    for name,row in old['protectedFiles'].items():
        c.require(c.file_sha(repo/name)==row['workingCopySha256'],'PROTECTED_FILE_CHANGED')
    for name,sha in old['toolchain']['verifiedSha256'].items():
        c.require(c.file_sha(Path(config['sdk'])/'ndk/28.2.13676358'/name)==sha,'TOOLCHAIN_CHANGED')
    for name in ('cmake','ninja'):
        c.require(c.file_sha(prior/f'cmake-verified/bin/{name}.exe')==old['toolchain'][name+'Sha256'],'TOOLCHAIN_CHANGED')
    artifacts={n:prior/'build-1/bin'/n for n in old['build']['target']['files'] if n.endswith('.so')}
    for n,p in artifacts.items(): c.require(c.file_sha(p)==old['build']['target']['files'][n]['sha256'],'NATIVE_CANDIDATE_CHANGED')
    artifacts['model.bin']=model; artifacts['dora-asr-campaign']=Path(config['executable'])
    identity=c.digest({n:{'sha256':c.file_sha(p),'bytes':p.stat().st_size} for n,p in artifacts.items()})
    d=device.Device(Path(config['sdk'])/'platform-tools/adb.exe',artifacts,identity)
    return repo,prior,work,artifacts,d


def require_private(path,repo):
    root=Path(os.environ['LOCALAPPDATA'])/'DORA/private'
    c.require(path.resolve().is_relative_to(root.resolve()),'PRIVATE_STORAGE_CLASS_INVALID')
    c.require(not path.resolve().is_relative_to(repo.resolve()),'PRIVATE_STORAGE_INSIDE_REPOSITORY')
    c.require(not any((p/'.git').exists() for p in (path,*path.parents)),'PRIVATE_STORAGE_INSIDE_WORKTREE')
    c.require(not path.is_symlink(),'PRIVATE_STORAGE_SYMLINK')
    for ancestor in (path,*path.parents):
        if not ancestor.resolve().is_relative_to(root.resolve()): break
        if ancestor.exists():
            c.require(not ancestor.is_symlink() and not ancestor.is_junction(),'PRIVATE_STORAGE_REPARSE_POINT')
            env=dict(os.environ,DORA_PRIVATE_CHECK_PATH=str(ancestor))
            command="$a=Get-Acl -LiteralPath $env:DORA_PRIVATE_CHECK_PATH; $s=[System.Security.Principal.WindowsIdentity]::GetCurrent().User.Value; foreach($r in $a.Access){if($r.AccessControlType -eq 'Allow' -and $r.IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value -ne $s){exit 2}}"
            p=subprocess.run(['powershell','-NoProfile','-NonInteractive','-Command',command],env=env,capture_output=True,timeout=30)
            c.require(p.returncode==0,'PRIVATE_STORAGE_ACL_INVALID')


def preflight(config,config_path):
    require_private(Path(config_path),Path(__file__).resolve().parents[1])
    repo,prior,work,artifacts,d=setup(config)
    evidence=work/'preflight'; evidence.mkdir(exist_ok=False)
    lock=(work/'operator.lock').open('xb')
    cleanup='NOT_NEEDED'
    try:
        initial=d.connect(); c.save_new(evidence/'environment-before.json',initial)
        d.prepare()
        profiles={locale:d.profile(locale) for locale in ('ru','en')}
        c.require({k:v for k,v in profiles['ru'].items() if k!='language'}=={k:v for k,v in profiles['en'].items() if k!='language'},'DECODING_LOCALE_DRIFT')
        decoding={'version':'dora-alpha-asr-decoding-v0.1','base':'whisper_full_default_params(WHISPER_SAMPLING_GREEDY)',
            'runtimeCommit':frozen.RUNTIME_COMMIT,'resolvedWhisperFullParams':profiles,
            'contextParams':{'use_gpu':False,'flash_attn':True,'gpu_device':0,'dtw_token_timestamps':False,
                'dtw_aheads_preset':'WHISPER_AHEADS_NONE','dtw_n_top':-1,'dtw_aheads':{'n_heads':0,'heads':None},'dtw_mem_size':134217728}}
        measure=protocol(c.file_sha(prior/'source/examples/miniaudio.h'))
        c.save_new(work/'decoding-profile.json',decoding); c.save_new(work/'measurement-protocol.json',measure)
        synthetic=evidence/'generated-silence.wav'
        with wave.open(str(synthetic),'wb') as f:
            f.setnchannels(1); f.setsampwidth(2); f.setframerate(16000); f.writeframes(b'\0'*96000)
        # Complete MP3 decoder path is tested without corpus inference.
        codec=d.execute(prior/'source/samples/jfk.mp3','en',evidence/'codec',mode='decode')
        c.require(codec['metrics']['decodedFrames']>0,'SYNTHETIC_CODEC_FAILED')
        journal=c.Journal(evidence/'attempts.sqlite')
        req={'campaignVersion':c.VERSION,'decodingProfileSha256':c.digest(decoding),'measurementSha256':c.digest(measure),
            'nativeIdentity':d.identity,'syntheticAudioSha256':c.file_sha(synthetic)}
        journal.begin(1,'sample-0000000000000000','en',1,req)
        try:
            observation=d.execute(synthetic,'en',evidence/'inference')
            c.require(observation['thermal']['maximum']<3,'SEVERE_THERMAL_OBSERVED')
            c.require(observation['metrics']['decodedFrames']==48000 and observation['metrics']['inferenceElapsedMicros']>0,'SYNTHETIC_INFERENCE_FAILED')
            raw=(evidence/'inference/hypothesis.txt').read_bytes(); raw.decode('utf-8',errors='strict')
            journal.finish(1,{'state':'SUCCEEDED','observation':observation,'hypothesisSha256':hashlib.sha256(raw).hexdigest(),'qualityScored':False})
        except Exception as e:
            journal.finish(1,{'state':'FAILED','category':type(e).__name__}); raise
        finally: journal.close()
        reopened=c.Journal(evidence/'attempts.sqlite'); c.require(reopened.records()[0]['result']['state']=='SUCCEEDED','SYNTHETIC_PERSISTENCE_FAILED'); reopened.close()
        oracle=c.Oracle(work/'oracle'); oracle.compile()
        cleanup=d.cleanup()
        c.save_new(evidence/'cleanup.json',{'status':cleanup})
        files=[*repo.glob('tools/*asr*campaign*.py'),*repo.glob('tools/alpha_asr_campaign_native/*'),
            prior/'source/examples/miniaudio.h',Path(config_path),work/'decoding-profile.json',work/'measurement-protocol.json',
            *artifacts.values(),*list((work/'oracle').rglob('*.class'))]
        old=json.loads((repo/'docs/evidence/poc-asr-001/alpha-asr-runner-preflight-stage0-v0.1.json').read_bytes())
        files.extend(repo/r['file'] for r in old['implementationPreservation']['files'])
        files.extend(repo/name for name in old['protectedFiles'])
        c.save_new(work/'freeze.json',{'campaignVersion':c.VERSION,'frozenUtc':utc(),'baseCommit':'e91b45eefbad4609deb8a83d0823d7b090830700',
            'syntheticPreflight':'PASS','corpusAccessBeforeFreeze':0,'files':c.file_pins(files),
            'activeConfigPath':str(Path(config_path).resolve()),'activeConfigSha256':c.digest(config),
            'decodingProfileSha256':c.digest(decoding),'measurementProtocolSha256':c.digest(measure),
            'nativeCandidateSha256':d.identity,'manifestSha256':frozen.CORPUS_SHA256,'modelSha256':frozen.MODEL_SHA256})
        print('SYNTHETIC_PREFLIGHT=PASS; CODE_AND_PROFILES_FROZEN; CORPUS_ACCESS=0',flush=True)
    finally:
        if d.owned: cleanup=d.cleanup()
        lock.close(); (work/'operator.lock').unlink()


def scored_result(row,reference,hypothesis,observation,oracle):
    prepared=frozen.prepare_case({'profile':frozen.profile(),'caseId':row['sampleId'],'locale':row['locale'],
        'referenceText':reference,'hypothesisText':hypothesis})
    score=oracle.score(prepared); result=frozen.result_shell(row['sampleId'],row['locale'])
    result.update(attemptOrdinal=1,executionAttemptState='SUCCEEDED',
        rawReferenceTextSha256=prepared['rawReferenceTextSha256'],rawHypothesisTextSha256=prepared['rawHypothesisTextSha256'],
        rawTokenCounts={'reference':len(prepared['rawReferenceTokens']),'hypothesis':len(prepared['rawHypothesisTokens'])},
        normalizedTokenCounts={'reference':len(prepared['normalizedReferenceTokens']),'hypothesis':len(prepared['normalizedHypothesisTokens'])},
        oracleCounts={s:{k:score[s][k] for k in ('substitutions','deletions','insertions')} for s in ('raw','normalized')},
        normalizedWerContribution={'errors':sum(score['normalized'][k] for k in ('substitutions','deletions','insertions')),
            'referenceTokens':score['normalized']['referenceTokens']})
    m=observation['metrics']; duration=(m['decodedFrames']*1000000+8000)//16000
    result['timing'].update(state='OBSERVED',audioDurationMicros=duration,inferenceElapsedMicros=m['inferenceElapsedMicros'],modelLoadElapsedMicros=m['modelLoadElapsedMicros'])
    result['rtf'].update(state='OBSERVED',elapsedMicros=m['inferenceElapsedMicros'],audioMicros=duration)
    result['memory'].update(state='OBSERVED',peakPssBytes=observation['memory']['peakPssBytes'],peakNativeHeapBytes=observation['memory']['peakNativeHeapBytes'])
    result['thermal'].update(state='OBSERVED',initialStatus=frozen.THERMAL_STATES[observation['thermal']['initial']],maximumStatus=frozen.THERMAL_STATES[observation['thermal']['maximum']])
    frozen.validate_result(result)
    return result,prepared


def campaign(config,config_path):
    require_private(Path(config_path),Path(__file__).resolve().parents[1])
    repo,prior,work,artifacts,d=setup(config)
    freeze=json.loads((work/'freeze.json').read_bytes()); c.verify_pins(freeze['files'])
    c.require(str(Path(config_path).resolve())==freeze['activeConfigPath']
        and c.digest(config)==freeze['activeConfigSha256']
        and d.identity==freeze['nativeCandidateSha256'],'ACTIVE_CONFIGURATION_NOT_FROZEN')
    lock=(work/'operator.lock').open('xb')
    receipt=work/'import-receipt.json'; corpus=Path(config['corpus'])
    results=[]; journal=None; failure=None; cleanup='NOT_NEEDED'; initial=None; final=None
    try:
        initial=d.connect(); c.save_new(work/'campaign-environment-before.json',initial)
        m,refs,bindings,imported=transfer.import_package(Path(config['archive']),corpus,freeze,receipt)
        print('5.1C_TRANSFER_IMPORT=PASS; AUDIO=48/48; REFERENCES=48/48; DURATION_BINDINGS=48/48',flush=True)
        d.prepare(); oracle=c.Oracle(work/'oracle'); journal=c.Journal(work/'campaign-attempts.sqlite')
        for position,row in enumerate(m['samples'],1):
            c.verify_pins(freeze['files'])
            b=bindings[row['sampleId']]; audio=corpus/b['audioRelativePath']
            c.require(audio.stat().st_size==row['audioByteLength'] and c.file_sha(audio)==row['audioSha256'],'INPUT_BINDING_MISMATCH')
            reference=transfer.reference_text(refs,row['sampleId'],row['referenceTextSha256'])
            request={'campaignVersion':c.VERSION,'freezeSha256':c.digest(freeze),'audioSha256':row['audioSha256'],
                'referenceSha256':row['referenceTextSha256'],'locale':row['locale']}
            journal.begin(position,row['sampleId'],row['locale'],1,request)
            destination=work/'cases'/f'{position:02d}'
            try:
                observation=d.execute(audio,row['locale'],destination)
                hypothesis=(destination/'hypothesis.txt').read_bytes().decode('utf-8',errors='strict')
                result,prepared=scored_result(row,reference,hypothesis,observation,oracle)
                c.save_new(destination/'prepared-private.json',prepared)
                c.save_new(destination/'result-private.json',result)
                journal.finish(position,{'state':'SUCCEEDED','result':result,'observation':observation})
                results.append(result)
            except Exception as error:
                result=frozen.result_shell(row['sampleId'],row['locale'])
                result.update(attemptOrdinal=1,executionAttemptState='FAILED',errorCategory='RUNNER_TIMEOUT' if str(error)=='RUNNER_TIMEOUT' else 'RUNNER_FAILURE')
                frozen.validate_result(result)
                journal.finish(position,{'state':'FAILED','result':result,'privateError':str(error),'errorType':type(error).__name__})
                results.append(result); raise
            print('CAMPAIGN_PROGRESS='+str(position)+'/48; VALID_SCORED='+str(len(results)),flush=True)
            c.require(observation['thermal']['maximum']<3,'SEVERE_THERMAL_OBSERVED')
        c.verify_pins(freeze['files'])
        final=d.connect(); c.save_new(work/'campaign-environment-after.json',final)
    except Exception as e:
        failure={'type':type(e).__name__,'privateError':str(e)}
        raise
    finally:
        try: cleanup=d.cleanup()
        except Exception as e:
            cleanup='BLOCKED'; failure=failure or {'type':type(e).__name__,'privateError':str(e)}
        records=journal.records() if journal else []
        if journal: journal.close()
        summary={'campaignVersion':c.VERSION,'finishedUtc':utc(),'failure':failure,'cleanup':cleanup,
            'attemptCount':len(records),'successfulCases':sum(r['executionAttemptState']=='SUCCEEDED' for r in results),
            'severeThermalObserved':any(r['thermal']['maximumStatus'] in frozen.THERMAL_STATES[3:] for r in results),
            'initialEnvironment':initial,'finalEnvironment':final,'results':results,'freezeSha256':c.digest(freeze)}
        if 'm' in locals(): summary['languageAggregates']=c.aggregate(m['samples'],results)
        c.save_new(work/'campaign-terminal-private.json',summary)
        lock.close(); (work/'operator.lock').unlink()
    c.require(not failure and cleanup=='VERIFIED' and len(results)==48,'CAMPAIGN_INCOMPLETE')
    print('CAMPAIGN_EXECUTION=PASS / BOUNDED_POCO_48_CLIP_CAMPAIGN_COMPLETE; CLEANUP=VERIFIED',flush=True)


if __name__=='__main__':
    try:
        c.require(len(sys.argv)==3 and sys.argv[1] in ('preflight','campaign'),'USAGE')
        config=json.loads(Path(sys.argv[2]).read_bytes())
        if sys.argv[1]=='preflight': preflight(config,sys.argv[2])
        else: campaign(config,sys.argv[2])
    except Exception as error:
        if 'config' in locals():
            with (Path(config['work'])/('operator-error-'+str(time.time_ns())+'.txt')).open('x',encoding='utf-8') as diagnostic:
                diagnostic.write(traceback.format_exc())
        # Full evidence remains private. Do not echo paths, text or provider errors.
        print('CAMPAIGN_STOPPED; CATEGORY='+type(error).__name__,flush=True)
        sys.exit(1)
