"""Exact Stage8.4 admission-only successor; cannot certify native/physical gates."""
import hashlib
import json
from pathlib import Path

PARENT = '23adb618a39a014e5090ee2f32e27015de485f07'
INITIAL = 'fc1d362d4d3b692a55b5b954841276b247f4b456'
BRANCH = 'stage/8.4-vad-segmentation-rotation'
PREFIX = 'docs/evidence/vad-8.4-remediation/'
RECORD = PREFIX + 'candidate.json'
HISTORICAL = {'docs/evidence/vad-8.4-admission/' + name for name in ('README.md','candidate.json','native-inventory.json')}
PATHS = {PREFIX + name for name in ('README.md','candidate.json','license-inventory.json','NOTICE.txt',
          'sbom.cdx.json','runtime-smoke.json','build-inputs.json')} | {
    'tools/vad_admission/CMakeLists.txt', 'tools/vad_admission/build.py',
    'tools/vad_admission/SmokeActivity.java', 'tools/vad_admission/AndroidManifest.xml',
    'tools/validate_vad_artifact_admission.py', 'tools/test_vad_artifact_admission.py',
    'tools/validate_recording_alpha_acceptance.py', 'tools/validate_development_device_security.py',
    'tools/test_development_device_security.py'}
PATHS.add('docs/adr/ADR-VAD-001-isolated-vad-only-runtime-admission.md')
PATHS.add('.gitattributes')
PATHS.add('tools/validate_poc_recovery_governance.py')
CLOSURE_PREFIX = 'docs/evidence/vad-8.4-closure/'
PATHS |= {CLOSURE_PREFIX + n for n in ('README.md','supply-chain.json','retrieval.json','runtime-smoke.json')}
PATHS.add('tools/vad_admission/custody.py')
NOTICE_ATTRIBUTE = '\n# Preserve the exact isolated VAD candidate attribution bytes.\ndocs/evidence/vad-8.4-remediation/NOTICE.txt -text\ntools/vad_admission/SmokeActivity.java text eol=lf\n'


def require(value, message):
    if not value:
        raise ValueError(message)


def candidate(root):
    return (root / RECORD).exists()


def base_record():
    return dict(status='PENDING_FINAL_PUBLICATION', parentSha=PARENT, initialHead=INITIAL,
                sherpaSourceSha='11afbd009a7f8c08f4bcf2fc1b265d0df4670fbf',
                modelSha256='1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3',
                productionVadIntegration='NOT_STARTED', securityRestorationBlocker='OPEN',
                **{'8.4C':'NOT_STARTED','8.5':'NOT_STARTED'})


def validate_record(record):
    for key,value in base_record().items():
        require(record.get(key) == value, 'Admission identity/scope changed: ' + key)


def validate_paths(paths, *, complete=True):
    require(set(paths) <= PATHS and (not complete or set(paths) == PATHS),
            'Only exact VAD admission evidence/tool paths are allowed')


def validate_attributes(current, historical):
    require(current.replace(b'\r\n',b'\n') == historical.replace(b'\r\n',b'\n') + NOTICE_ATTRIBUTE.encode(),
            'Only exact VAD NOTICE/harness byte-preservation attributes are allowed')


def validate_bundle(inputs, licenses, sbom):
    inventory=inputs['artifactInventory']
    expected={'libsherpa-onnx-jni.so','libonnxruntime.so'}
    require(set(inventory['native'])==expected,'Unexpected packaged native inventory')
    require({p for p in inventory['entries'] if p.endswith('.so')} == {'jni/arm64-v8a/'+name for name in expected}, 'Native entry mismatch')
    require(inventory['sourceSha']==base_record()['sherpaSourceSha'] and inventory['abi']==['arm64-v8a'],'Source/ABI drift')
    require(inputs['effectiveCMakeBooleanOptions']['SHERPA_ONNX_ENABLE_TTS']=='OFF','TTS must be disabled')
    require(inputs['effectiveCMakeBooleanOptions']['SHERPA_ONNX_ENABLE_JNI']=='OFF','Generic JNI must not be built')
    require(licenses['status']=='ENGINEERING_LICENSE_INVENTORY_COMPLETE' and not licenses['material_review_remaining'],'Unresolved engineering license inventory')
    require(len({c['name'] for c in licenses['components']})==len(licenses['components']),'Duplicate license component')
    require(all(c['license'] and 'UNKNOWN' not in c['license'] and c['source'] and c['version'] for c in licenses['components']),'Unresolved component identity/license')
    require(sbom['bomFormat']=='CycloneDX' and sbom['specVersion']=='1.6','Unexpected SBOM format')
    require(sbom['metadata']['component']['hashes']==[{'alg':'SHA-256','content':inventory['artifact']['sha256']}],'AAR/SBOM identity drift')
    require(len(sbom['components'])==len(licenses['components']),'Missing/phantom SBOM component')
    by_name={c['name']:c for c in sbom['components']}
    require(len(by_name)==len(sbom['components']),'Duplicate SBOM component')
    for c in licenses['components']:
        require(c['name'] in by_name,'Missing SBOM component')
        actual=by_name[c['name']]
        require(actual['version']==c['version'] and actual['licenses']==[{'expression':c['license']}],'SBOM version/license drift')
        properties={p['name']:p['value'] for p in actual['properties']}
        require(properties['dora:distributedInAar']==str(c['distributed']).lower(),'Phantom runtime distribution')
    packaged={p['value']:c for c in sbom['components'] for p in c['properties'] if p['name']=='dora:packagedPath'}
    require(set(packaged)=={'classes.jar',*('jni/arm64-v8a/'+name for name in expected)},'Missing/phantom packaged SBOM file')
    for path,c in packaged.items():
        require(c['hashes']==[{'alg':'SHA-256','content':inventory['entries'][path]['sha256']}],'Packaged file/SBOM hash drift')
    for name,native in inventory['native'].items():
        require(native['sha256']==inventory['entries']['jni/arm64-v8a/'+name]['sha256'],'Native hash mismatch')
        require(native['architecture']=='AArch64' and native['static16KiB']=='STATIC_16K_ALIGNMENT_COMPATIBLE','Native architecture/alignment drift')
        require(native['loads'] and all(p['alignment']>=16384 and p['offset']%16384==p['address']%16384 for p in native['loads']),'Invalid16KiB LOAD')
    require(licenses['packaged_notice']['sha256']==inventory['entries']['META-INF/LICENSES/NOTICE.txt']['sha256'],'NOTICE identity drift')


def validate_external_bindings(record, inputs, smoke, notice):
    expected=inputs['artifactInventory']['entries']['META-INF/LICENSES/NOTICE.txt']
    require(len(notice)==expected['bytes'] and hashlib.sha256(notice).hexdigest()==expected['sha256'],
            'Repository NOTICE bytes differ from frozen AAR NOTICE')
    require(smoke['aarSha256']==record['artifact']['sha256'] and smoke['modelSha256']==record['modelSha256'],
            'Smoke evidence uses another runtime/model identity')


def validate_successor(root):
    from validate_encrypted_persistence import git
    require(git(root,'merge-base',INITIAL,'HEAD').decode().strip() == INITIAL, 'Wrong admission baseline')
    require(git(root,'rev-parse',INITIAL+'^').decode().strip() == PARENT, 'Wrong admission parent')
    require(set(git(root,'diff','--name-only',PARENT,INITIAL).decode().splitlines()) == HISTORICAL,
            'Historical admission inventory changed')
    for path in HISTORICAL:
        # These paths have existing text eol=lf attributes: compare exact Git blob
        # bytes without rewriting the original Windows working-copy line endings.
        require((root/path).read_bytes().replace(b'\r\n',b'\n') == git(root,'show',INITIAL+':'+path), 'Historical rejected candidate changed')
    validate_attributes((root/'.gitattributes').read_bytes(),git(root,'show',INITIAL+':.gitattributes'))
    actual=set(git(root,'diff','--name-only','--no-renames',INITIAL).decode().splitlines())
    actual.update(git(root,'ls-files','--others','--exclude-standard').decode().splitlines())
    validate_paths(actual)
    for line in git(root,'rev-list','--reverse','--parents',INITIAL+'..HEAD').decode().splitlines():
        parts=line.split()
        require(len(parts)==2,'Admission merge history rejected')
        paths=set(git(root,'diff','--name-only','--no-renames',parts[1],parts[0]).decode().splitlines())
        require(paths,'Empty admission commit rejected')
        validate_paths(paths,complete=False)
    record=json.loads((root/RECORD).read_text(encoding='utf-8'))
    validate_record(record)
    files=record['repositoryFiles']
    require(set(files)==PATHS - {RECORD},'Exact repository supply-chain inventory required')
    for path,digest in files.items():
        require(hashlib.sha256((root/path).read_bytes()).hexdigest()==digest,'Admission file digest mismatch: '+path)
    artifact=record['artifact']
    require(artifact['abi']==['arm64-v8a'] and len(artifact['sha256'])==64 and artifact['bytes']>0,'Invalid binary identity')
    require(record['modelBytes']==2327524 and record['modelLicense']=='MIT','Model identity changed')
    require(record['ciRebuildsNativeBinary'] is False,'CI metadata verification is not a native rebuild')
    require(record['physical16KiBRuntime'] == 'NOT_RUN','Static16KiB is not physical proof')
    inputs=json.loads((root/PREFIX/'build-inputs.json').read_text(encoding='utf-8'))
    licenses=json.loads((root/PREFIX/'license-inventory.json').read_text(encoding='utf-8'))
    sbom=json.loads((root/PREFIX/'sbom.cdx.json').read_text(encoding='utf-8'))
    validate_bundle(inputs,licenses,sbom)
    smoke=json.loads((root/PREFIX/'runtime-smoke.json').read_text(encoding='utf-8'))
    validate_external_bindings(record,inputs,smoke,(root/PREFIX/'NOTICE.txt').read_bytes())
    for name,identity in inputs['artifactInventory']['recipe'].items():
        require(hashlib.sha256((root/'tools/vad_admission'/name).read_bytes()).hexdigest()==identity['sha256'],
                'Rebuild recipe digest does not match the artifact receipt')
    require(record['artifact']['sha256']==inputs['artifactInventory']['artifact']['sha256'],'Candidate AAR identity mismatch')
    require(record['artifact']['bytes']==inputs['artifactInventory']['artifact']['bytes'],'Candidate AAR length mismatch')
    manifest,retrieval,runtime=[json.loads((root/CLOSURE_PREFIX/n).read_text(encoding='utf-8'))
                              for n in ('supply-chain.json','retrieval.json','runtime-smoke.json')]
    validate_closure_data(manifest,retrieval,runtime)
    recipes={n:hashlib.sha256((root/'tools/vad_admission'/n).read_bytes()).hexdigest()
             for n in ('build.py','CMakeLists.txt')}
    require(manifest['buildRecipeFiles']==recipes and manifest['buildRecipeSha256']==
            hashlib.sha256(json.dumps(recipes,sort_keys=True,separators=(',',':')).encode()).hexdigest(),
            'Custody recipe identity mismatch')
    for field,path in (('noticeSha256','NOTICE.txt'),('sbomSha256','sbom.cdx.json')):
        require(manifest[field]==hashlib.sha256((root/PREFIX/path).read_bytes()).hexdigest(),
                'Custody NOTICE/SBOM identity mismatch')
    return PATHS | HISTORICAL


def validate_closure_data(manifest,retrieval,runtime):
    from vad_admission import custody
    custody.validate_manifest(manifest)
    require(manifest['repositoryBaseline']=='7b3a4b494df0c1d18c8f7bcd8e064c21393ea040', 'Wrong closure baseline')
    require(manifest['historicalRevisionRetentionGuaranteed'] is False,'Drive revision retention is not WORM')
    require(manifest['recoveryKit']=={'fileId':'1JrGUbWRJYIGpGQY5cgYsSzENlhNOjsY_',
            'name':'vad-runtime-recovery-kit-v1.zip','bytes':653358,
            'sha256':'a8be2542f96dbff97e09ff8a9e34070021bd514b636e9d1ec84c54cdc39a9e21'},'Recovery kit identity drift')
    require(retrieval['status']=='PASS_PRIVATE_OWNER_RETRIEVAL_AND_HARNESS_CONSUMPTION'
            and retrieval['aarFileId']==manifest['custody']['fileId']
            and retrieval['aarBytes']==manifest['artifact']['bytes']
            and retrieval['aarSha256']==manifest['artifact']['sha256']
            and retrieval['modelSha256']==manifest['sileroSha256'],'Retrieval candidate identity drift')
    require(retrieval['originalBuildOutputUsed'] is False and retrieval['physicalRuntimeProved'] is False
            and retrieval['storageEnforcedNoOverwrite'] is False
            and retrieval['ciPrivateArtifactRetrieval']=='NOT_ENABLED','Retrieval scope inflated')
    require(retrieval['permissionsReadback']=={'shared':False,'roles':['owner'],'publicOrDomainGrant':False},
            'Private custody permissions not established')
    kit=retrieval['recoveryKitRetrieval']
    require(kit['source']=='AUTHENTICATED_GOOGLE_DRIVE_FETCH' and kit['result']=='PASS'
            and kit['bytes']==manifest['recoveryKit']['bytes'] and kit['sha256']==manifest['recoveryKit']['sha256']
            and kit['indexedFilesVerified']==14,'Recovery kit retrieval not verified')
    require(runtime['status']=='PASS_BOUNDED_SAME_PROCESS_RUNTIME_SMOKE'
            and runtime['repositoryDisposition']=='PENDING_FINAL_PUBLICATION'
            and runtime['harnessInstalled'] is True
            and runtime['installedApkSha256']==retrieval['harnessApkSha256']==
            '3f3b20e4c09068ba276bf747dda6734a837d3349f90ef7f1ae277f5b2946b756'
            and runtime['microphoneUsed'] is False and runtime['productionIntegration'] is False,
            'Physical receipt identity or scope drift')
    require(runtime['aarSha256']==manifest['artifact']['sha256'] and runtime['modelSha256']==manifest['sileroSha256'],
            'Executed runtime/model identity drift')
    require(runtime['installation']['exitCode']==0 and 'Success' in runtime['installation']['output']
            and runtime['installationHistory']['harnessInstalled'] is False,
            'Installation success or historical blocker lost')
    native={'libonnxruntime.so':'33847ad43bffe204699fd4a27f7f3603452a8cdaf2f9a44983a0bc31ffcf2da1',
            'libsherpa-onnx-jni.so':'2e7412da436e02b705cea92d6fe6e3d2f2bf97a483b12d9ae2cc65ad6b723cc7'}
    require(runtime['packagedNativeSha256']==native
            and runtime['loadedCandidateLibraries']==sorted(native)
            and runtime['nativeFilesOnDeviceMatchedPackagedHashes'] is True
            and runtime['unexpectedNonPlatformLibraries']==[]
            and runtime['crashBufferErrors']==0 and runtime['processAbsentAfterCleanup'] is True,
            'Native dependency, crash or cleanup evidence mismatch')
    require(runtime['campaignStartsFromStoppedProcess'] is True
            and runtime['initializationReleaseCycles']==6 and len(runtime['runs'])==6
            and runtime['inferenceWindowsPerCycle']==572,'Incomplete bounded campaign')
    for index,run in enumerate(runtime['runs']):
        require(run['index']==index and run['samePid'] is True and run['freshReceiptAfterDeletion'] is True,
                'Stale receipt or process identity gap')
        result=run['runtime']
        expected={'sherpaVersion':'1.13.8','upstreamEmbeddedGitLabel':'8c8e275d','onnxruntimeVersion':'1.28.2',
                  'abi':'arm64-v8a','api':34,'modelSha256':manifest['sileroSha256'],
                  'runtimeAarSha256':manifest['artifact']['sha256'],'initialized':True,
                  'silenceWindows':100,'silencePositiveWindows':0,'speechWindows':372,
                  'speechPositiveWindows':262,'speechSegments':3,'resetToSilence':True,
                  'loadedCandidateLibraries':sorted(native),'closed':True,
                  'fixtureSha256':'1e83d3eb15660d4776c3497d61e9d8a718a0be5a4c0ec780f1a0b042efda25a7',
                  'result':'PASS_ISOLATED_RUNTIME_SMOKE'}
        require(result==expected,'Frozen physical receipt changed')
        require(all(type(run[k]) is int and run[k]>0 for k in ('totalPssKiB','fdCount','threadCount')),
                'Missing bounded resource observations')
