"""Exact frozen 5.1C transfer import; call only after campaign code/config freeze.

All inputs/outputs are private. Does not decode, resample, reselect or repair data.
"""
import datetime
import hashlib
import json
from pathlib import Path
import stat
import subprocess
import sys
import zipfile
import alpha_asr_pilot_manifest as manifest_contract
from alpha_asr_campaign import require,digest,file_sha,save_new,verify_pins

ARCHIVE_BYTES=2501103
ARCHIVE_SHA='6340c832280ba21345d4e5c9f30b82e67d44f905b2f9ae7cc7bde4a0d74a85ee'
INDEX_SHA='e2df8bbbdca979804e75e9d48f007ab4fb9d6b0aa650c9f07c9d7e9046945db4'
MANIFEST_SHA='5d9e12fa76e9db54bc69bbd22396dbec733348db97bba353361ac701af64061c'
INVENTORY_SHA='ad454b162fab7dea96c28612a374fcae79040346dae5ee2916b3c4482b3ec041'


def safe_names(names):
    require(len(set(s.casefold() for s in names))==len(names),'TRANSFER_DUPLICATE_PATH')
    for name in names: manifest_contract._path(name)


def reference_text(value,sample_id,sha):
    """Resolve the exact sample's bound string; never trim or rewrite it."""
    candidates=[]
    def strings(v):
        if isinstance(v,str):
            if v and hashlib.sha256(v.encode('utf-8')).hexdigest()==sha: candidates.append(v)
        elif isinstance(v,dict):
            for child in v.values(): strings(child)
        elif isinstance(v,list):
            for child in v: strings(child)
    def find(v):
        if isinstance(v,dict):
            if sample_id in v: strings(v[sample_id])
            elif v.get('sampleId')==sample_id: strings(v)
            else:
                for child in v.values(): find(child)
        elif isinstance(v,list):
            for child in v: find(child)
    find(value)
    require(len(candidates)==1,'CONTROLLED_REFERENCE_BINDING_FAILED')
    return candidates[0]


def import_package(archive,destination,freeze,receipt):
    archive=Path(archive); destination=Path(destination)
    require(archive.stat().st_size==ARCHIVE_BYTES and file_sha(archive)==ARCHIVE_SHA,
            'BLOCKED_TRANSFER_ARCHIVE_INTEGRITY_MISMATCH')
    require(freeze['syntheticPreflight']=='PASS' and freeze['corpusAccessBeforeFreeze']==0,'CAMPAIGN_NOT_FROZEN')
    verify_pins(freeze['files'])
    require(not destination.exists(),'IMPORT_DESTINATION_ALREADY_EXISTS')
    with zipfile.ZipFile(archive) as z:
        infos=z.infolist(); names=[i.filename for i in infos]
        safe_names(names)
        require(len(names)==58 and sum(i.file_size for i in infos)<64*1024*1024
                and all(not i.is_dir() and not stat.S_ISLNK(i.external_attr>>16) for i in infos),
                'BLOCKED_TRANSFER_INDEX_INTEGRITY_FAILURE')
        index=json.loads(z.read('TRANSFER_INDEX.private.json'))
        require(index['schemaVersion']==1 and index['profileId']=='dora-alpha-asr-5.1c-transfer-v0.1'
            and index['aggregateIndexSha256']==INDEX_SHA
            and digest({k:v for k,v in index.items() if k!='aggregateIndexSha256'})==INDEX_SHA,
            'BLOCKED_TRANSFER_INDEX_INTEGRITY_FAILURE')
        require(index['fileCountIncludingIndex']==58 and index['fileCountExcludingIndex']==57
            and len(index['files'])==57 and all(index[k] is False for k in ('rematerialized','reselected','transcoded','referenceTextModified')),
            'BLOCKED_TRANSFER_INDEX_INTEGRITY_FAILURE')
        expected={r['relativePath']:r for r in index['files']}
        require(len(expected)==57 and set(names)==set(expected)|{'TRANSFER_INDEX.private.json'},'TRANSFER_FILE_SET_MISMATCH')
        copy_life=index['copyLifecycle']
        require(copy_life['custodianOnly'] is True and copy_life['trainingAllowed'] is False
            and copy_life['publicRedistributionAllowed'] is False
            and copy_life['retention']=='ACTIVE_THROUGH_5_5_THEN_DELETE_WITHIN_30_CALENDAR_DAYS', 'LIFECYCLE_INVALID')
        validator=Path(manifest_contract.__file__)
        require(hashlib.sha256(validator.read_bytes().replace(b'\r\n',b'\n')).hexdigest()==index['approvedValidatorSha256'],'VALIDATOR_CHANGED')
        destination.mkdir(parents=False)
        # Opening selected bytes starts only here, after immutable freeze verification.
        save_new(receipt.with_name('first-corpus-access.json'),{'utc':datetime.datetime.now(datetime.timezone.utc).isoformat(),
            'freezeSha256':digest(freeze),'reason':'EXACT_TRANSFER_EXTRACTION_AND_HASH_VALIDATION'})
        for info in infos:
            target=destination/info.filename
            require(target.resolve().is_relative_to(destination.resolve()),'TRANSFER_PATH_ESCAPE')
            target.parent.mkdir(parents=True,exist_ok=True)
            data=z.read(info)
            if info.filename in expected:
                r=expected[info.filename]
                require(len(data)==r['bytes'] and hashlib.sha256(data).hexdigest()==r['sha256'],'TRANSFER_FILE_HASH_MISMATCH')
            with target.open('xb') as f: f.write(data)
    inventory=destination/'manifests/candidate-inventory.json'; selected=destination/'manifests/selected-manifest.json'
    result=manifest_contract.verify(inventory.read_bytes(),selected.read_bytes())
    require(result['candidateInventorySha256']==INVENTORY_SHA and result['manifestSha256']==MANIFEST_SHA,'TRANSFER_MANIFEST_MISMATCH')
    run=subprocess.run([sys.executable,'-B',str(manifest_contract.__file__),str(inventory),str(selected)],capture_output=True,timeout=30)
    require(run.returncode==0 and json.loads(run.stdout)==result,'TRANSFER_VALIDATOR_CLI_FAILED')
    m=json.loads(selected.read_bytes()); refs=json.loads((destination/'manifests/selected-references-private.json').read_bytes())
    bindings={b['sampleId']:b for b in index['selectedBindings']}
    require(len(bindings)==48 and set(bindings)=={r['sampleId'] for r in m['samples']},'TRANSFER_BINDING_SET_MISMATCH')
    for row in m['samples']:
        b=bindings[row['sampleId']]; duration=b['originalDurationBinding']
        require(b['locale']==row['locale'] and b['upstreamRelativePath']==row['upstreamRelativePath']
                and b['referenceKey']==row['sampleId']
                and b['referenceFile']=='manifests/selected-references-private.json','TRANSFER_BINDING_MISMATCH')
        require(b['audioRelativePath'] in expected,'TRANSFER_AUDIO_PATH_INVALID')
        audio=destination/b['audioRelativePath']
        require(audio.stat().st_size==row['audioByteLength'] and file_sha(audio)==row['audioSha256'],'TRANSFER_AUDIO_HASH_MISMATCH')
        reference_text(refs,row['sampleId'],row['referenceTextSha256'])
        require(duration['sampleId']==row['sampleId'] and duration['result']=='VALIDATED'
            and duration['failureCategory']=='NONE'
            and duration['probeDurationMs']==duration['providerDurationMs']==row['durationMs'],'TRANSFER_DURATION_BINDING_MISMATCH')
    life=json.loads((destination/'manifests/lifecycle.json').read_bytes())
    require(life['access']=='CUSTODIAN_ONLY' and life['storageClass']=='LOCAL_PRIVATE_CONTROLLED_STORAGE'
        and life['deletionState']=='ACTIVE_REQUIRED_FOR_BOUNDED_EVALUATION'
        and life['expiryState']=='PENDING_FINAL_5_5_ASSESSMENT'
        and life['retention']==copy_life['retention'],'LIFECYCLE_INVALID')
    receipt_value={'result':'PASS / VERIFIED_ON_LAPTOP','archiveFilename':archive.name,'archiveBytes':ARCHIVE_BYTES,
        'archiveSha256':ARCHIVE_SHA,'transferIndexVersion':index['profileId'],'transferIndexSha256':INDEX_SHA,
        'importUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'storageClass':'LOCAL_PRIVATE_CONTROLLED_STORAGE',
        'candidateInventorySha256':INVENTORY_SHA,'selectedManifestSha256':MANIFEST_SHA,'counts':result['selectedCounts'],
        'audioVerified':48,'referencesVerified':48,'durationBindingsVerified':48,'validator':result,
        'rematerialization':False,'reselection':False,'transcoding':False,'lifecycleValid':True}
    save_new(receipt,receipt_value)
    return m,refs,bindings,receipt_value
