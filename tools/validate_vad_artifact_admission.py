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
    return PATHS | HISTORICAL
