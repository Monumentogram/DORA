"""5.6C.2A.1 successor: metadata compatibility only, no measured execution.

The selection document owns case order; materialized bindings own relative file
locators; the transfer index independently binds those files. No config supplies
case bindings or authority digests. Pure verification accepts owned byte snapshots
and publishes aggregates only. The file API requires a clean, frozen successor
checkout. This remediation deliberately cannot start a campaign.
"""
import datetime
import hashlib
from pathlib import Path
import re
import subprocess
import sys

import alpha_asr_small_campaign as c
import alpha_asr_small_acceptance_manifest as selection

schema = selection.old
OPERATOR_ID = 'dora-alpha-asr-small-campaign-operator-v0.2'
BASELINE_HEAD = 'bdbdaca73c8e0fd9b297bde560cda4442892bd6d'
DOCUMENT_PINS = ('695f1e147fe968c8f75e7bea5e7a6edfd20f50e939c0bdbc2064d3a7db7d1947',
                 c.MANIFEST_SHA, c.TRANSFER_SHA, c.FREEZE_SHA)
DOCUMENT_NAMES = ('selection', 'materialized', 'transfer', 'freeze')
METADATA_PATHS = ('selected-manifest.metadata-only.json',
                  'materialized-5.6c11/materialized-manifest.json',
                  'materialized-5.6c11/transfer-index.json',
                  'materialized-5.6c11/pre-inference-freeze.json')
OPERATOR_FILES = ('tools/alpha_asr_small_campaign.py',
                  'tools/alpha_asr_small_acceptance_manifest.py',
                  'tools/alpha_asr_pilot_manifest.py',
                  'tools/run_alpha_asr_small_campaign_v2.py',
                  'tools/test_alpha_asr_small_campaign_v2.py')
EVIDENCE_PATH = 'docs/evidence/poc-asr-001/alpha-asr-small-campaign-schema-remediation-stage0-v0.1.json'


def fields(value, names):
    schema._fields(value, set(names.split()))


def equal(actual, expected, code):
    # Canonical comparison also distinguishes booleans from integer counts.
    c.require(c.canonical(actual) == c.canonical(expected), code)


def document(raw, pin, self_digest=False):
    value = schema.parse_json(raw)
    c.require(type(value) is dict, 'DOCUMENT_SCHEMA')
    if self_digest:
        c.require(value.get('manifestSha256') == pin, 'DOCUMENT_IDENTITY')
        value_for_hash = {k:v for k,v in value.items() if k != 'manifestSha256'}
    else:
        value_for_hash = value
    c.require(c.digest(value_for_hash) == pin, 'DOCUMENT_IDENTITY')
    return value


def counts(value):
    equal(value, {'ru':24,'en':24}, 'SELECTED_COUNTS')


def validate_selected(s):
    fields(s, 'schemaVersion profileId selectionContractId selectionContractSha256 candidateInventorySha256 '
           'originalManifestSha256 priorUseLedgerSha256 exclusionDigests eligiblePoolSha256 sampleCount '
           'selectedCounts samples manifestSha256')
    equal(s['schemaVersion'], 1, 'SCHEMA_VERSION')
    c.require(s['profileId'] == selection.PROFILE and s['selectionContractId'] == selection.CONTRACT_ID
              and s['selectionContractSha256'] == selection.CONTRACT_SHA256
              and s['candidateInventorySha256'] == selection.INVENTORY_SHA256
              and s['originalManifestSha256'] == selection.ORIGINAL_SHA256, 'SELECTION_AUTHORITY')
    equal(s['sampleCount'],48,'SELECTED_COUNT'); counts(s['selectedCounts'])
    fields(s['exclusionDigests'], 'combinedExclusionSet duplicateGroups originalAudio originalSource priorUse')
    for value in [s['eligiblePoolSha256'],s['priorUseLedgerSha256'],*s['exclusionDigests'].values()]: schema._hash(value)
    rows = s['samples']
    schema._rows(rows, True); c.validate_selection(rows)
    for position,row in enumerate(rows):
        c.require(row['rank'] == position % 24 + 1
                  and row['selectionKey'] == selection.selection_key(row).hex(), 'SELECTION_ORDER')
    for start in (0,24):
        equal(rows[start:start+24], sorted(rows[start:start+24],key=selection.order_key), 'SELECTION_ORDER')
    return rows


def bind_metadata(selection_raw, materialized_raw, transfer_raw, freeze_raw, *, operator_id=OPERATOR_ID):
    """Return PRIVATE rows/bindings derived only from independently pinned docs.

    No paths are opened and no content, model, device, marker or journal is touched.
    The return value must never be logged or published. It is not execution authority.
    """
    c.require(operator_id == OPERATOR_ID, 'OPERATOR_IDENTITY')
    c.validate_profile(c.profile())
    s,m,t,f = [document(raw,pin,i<2) for i,(raw,pin) in enumerate(zip(
        (selection_raw,materialized_raw,transfer_raw,freeze_raw), DOCUMENT_PINS))]
    rows = validate_selected(s)
    fields(m, 'schemaVersion profileId metadataSelectedManifestSha256 selectionContractSha256 '
           'combinedExclusionSetSha256 eligiblePoolSha256 selectedCounts sourceIndexes bindings '
           'audioTranscoded referenceTextModified manifestSha256')
    equal(m['schemaVersion'],1,'SCHEMA_VERSION')
    c.require(m['profileId']=='dora-alpha-asr-small-materialized-holdout-v0.1', 'MATERIALIZED_PROFILE')
    c.require(m['metadataSelectedManifestSha256']==DOCUMENT_PINS[0]
              and m['selectionContractSha256']==selection.CONTRACT_SHA256
              and m['combinedExclusionSetSha256']==s['exclusionDigests']['combinedExclusionSet']
              and m['eligiblePoolSha256']==s['eligiblePoolSha256'], 'MATERIALIZED_AUTHORITY')
    counts(m['selectedCounts'])
    c.require(m['audioTranscoded'] is False and m['referenceTextModified'] is False, 'CONTENT_TRANSFORMED')
    c.require(type(m['sourceIndexes']) is list and len(m['sourceIndexes'])==2, 'SOURCE_INDEX_SCHEMA')
    for index,locale in zip(m['sourceIndexes'],('ru','en')):
        fields(index, 'locale providerRows testRows indexSha256 archiveSha256')
        c.require(index['locale']==locale,'SOURCE_INDEX_LOCALE')
        schema._int(index['providerRows'],24,2**63-1); schema._int(index['testRows'],24,index['providerRows'])
        schema._hash(index['indexSha256']); schema._hash(index['archiveSha256'])
    bindings = m['bindings']
    c.require(type(bindings) is list and len(bindings)==48, 'MATERIALIZED_COUNT')
    expected_files = {}
    for row,b in zip(rows,bindings):
        fields(b, 'sampleId locale rank selectionKey sourcePath durationMs audioRelativePath audioBytes '
               'audioSha256 referenceRelativePath referenceBytes referenceSha256')
        expected = {k:row[k] for k in ('sampleId','locale','rank','selectionKey','durationMs','audioSha256')}
        expected.update(sourcePath=row['upstreamRelativePath'],audioBytes=row['audioByteLength'],
                        referenceSha256=row['referenceTextSha256'])
        equal({k:b[k] for k in expected},expected,'MATERIALIZED_BINDING')
        for kind,folder,suffix in (('audio','audio','mp3'),('reference','references','txt')):
            path=b[kind+'RelativePath']; schema._path(path)
            # These locators are the admitted materializer's actual schema.
            c.require(path==f"{folder}/{row['locale']}/{row['rank']:02}.{suffix}", 'MATERIALIZED_PATH')
            schema._int(b[kind+'Bytes'],1,2**63-1)
            schema._hash(b[kind+'Sha256'])
            c.require(path not in expected_files, 'DUPLICATE_MATERIALIZED_PATH')
            expected_files[path]={'bytes':b[kind+'Bytes'],'sha256':b[kind+'Sha256']}
    expected_files['materialized-manifest.json']={'bytes':len(materialized_raw),
                                                 'sha256':hashlib.sha256(materialized_raw).hexdigest()}
    fields(t,'schemaVersion profileId materializedManifestSha256 files')
    equal(t['schemaVersion'],1,'SCHEMA_VERSION')
    c.require(t['profileId']=='dora-alpha-asr-small-acceptance-transfer-v0.1'
              and t['materializedManifestSha256']==DOCUMENT_PINS[1], 'TRANSFER_AUTHORITY')
    c.require(type(t['files']) is list and len(t['files'])==97, 'TRANSFER_COUNT')
    observed={}
    for item in t['files']:
        fields(item,'path bytes sha256'); schema._path(item['path'])
        schema._int(item['bytes'],1,2**63-1); schema._hash(item['sha256'])
        c.require(item['path'] not in observed, 'DUPLICATE_TRANSFER_PATH')
        observed[item['path']]={k:item[k] for k in ('bytes','sha256')}
    equal(observed,expected_files,'TRANSFER_BINDING')
    validate_freeze(f,s)
    return rows, bindings


def validate_freeze(f,s):
    fields(f,'schemaVersion profileId selectionContractId selectionContractSha256 combinedExclusionSetSha256 '
           'eligiblePoolSha256 metadataSelectedManifestSha256 materializedSelectedManifestSha256 transferIndexSha256 '
           'selectedCounts sampleCount candidate runtimeCommit decodingProfileSha256 measurementProtocolSha256 '
           'resourceGatesSha256 governanceCommit governanceGitBlob dataRetentionAuthority absoluteDeletionDeadline '
           'completedUtc asrInferenceBeforeFreeze copyClasses')
    expected = dict(schemaVersion=1,profileId='dora-alpha-asr-small-pre-inference-freeze-v0.1',
        selectionContractId=selection.CONTRACT_ID,selectionContractSha256=selection.CONTRACT_SHA256,
        combinedExclusionSetSha256=s['exclusionDigests']['combinedExclusionSet'],eligiblePoolSha256=s['eligiblePoolSha256'],
        metadataSelectedManifestSha256=DOCUMENT_PINS[0],materializedSelectedManifestSha256=DOCUMENT_PINS[1],
        transferIndexSha256=DOCUMENT_PINS[2],selectedCounts={'ru':24,'en':24},sampleCount=48,
        candidate={'filename':c.profile()['modelArtifact'],'bytes':c.MODEL_BYTES,'sha256':c.MODEL_SHA},
        runtimeCommit=c.text.RUNTIME_COMMIT,decodingProfileSha256=c.DECODING_SHA,measurementProtocolSha256=c.PROTOCOL_SHA,
        resourceGatesSha256=c.GATES_SHA,governanceCommit=c.GOVERNANCE_COMMIT,
        governanceGitBlob='961ed67b3da21967497a9ed5c1be176f27fcde27',
        dataRetentionAuthority='5.6B_OWNER_DECISION_AND_5.6C.1.1_EXPLICIT_SOURCE_RECOVERY_INSTRUCTION',
        absoluteDeletionDeadline='2026-10-25',asrInferenceBeforeFreeze=0,
        copyClasses=['EXACT_SOURCE_ARCHIVES','ARCHIVE_MEMBER_METADATA','SELECTED_AUDIO_48','SELECTED_REFERENCES_48',
                     'MATERIALIZED_MANIFEST','TRANSFER_INDEX','PRE_INFERENCE_FREEZE'])
    equal({k:f[k] for k in expected},expected,'FREEZE_BINDING')
    c.require(type(f['completedUtc']) is str,'FREEZE_TIMESTAMP')
    try: completed=datetime.datetime.fromisoformat(f['completedUtc'])
    except ValueError: raise ValueError('FREEZE_TIMESTAMP') from None
    c.require(completed.utcoffset()==datetime.timedelta(0)
              and completed.date()<=datetime.date(2026,10,25), 'FREEZE_TIMESTAMP')


def verify_metadata(*raw_documents, operator_id=OPERATOR_ID):
    bind_metadata(*raw_documents,operator_id=operator_id)
    return {'result':'PASS','claim':'PINNED_PRIVATE_PACKAGE_METADATA_COMPATIBLE',
            'operatorId':OPERATOR_ID,'documentCanonicalSha256':dict(zip(DOCUMENT_NAMES,DOCUMENT_PINS)),
            'sampleCount':48,'selectedCounts':{'ru':24,'en':24},'crossBoundCases':48,
            'materializedBindings':48,'contentFileBindings':96,'transferRecords':97,
            'selectionOrder':'RU24_THEN_EN24_FROZEN_RANK_ORDER',
            'evaluationProfileSha256':c.digest(c.profile()),'contentIntegrityReverified':False,
            'measuredExecution':'BLOCKED / NOT_AUTHORIZED'}


def verify_repository(repo, expected_commit):
    c.require(type(expected_commit) is str and re.fullmatch('[0-9a-f]{40}',expected_commit), 'REPOSITORY_IDENTITY')
    def git(*args):
        p=subprocess.run(['git','-C',str(repo),*args],capture_output=True,timeout=30)
        c.require(p.returncode==0,'REPOSITORY_IDENTITY_UNAVAILABLE')
        return p.stdout.decode().strip()
    c.require(git('rev-parse','HEAD')==expected_commit
              and git('branch','--show-current')=='chat/alpha-asr-runner-scope'
              and not git('status','--porcelain'), 'REPOSITORY_IDENTITY')
    c.require(git('rev-parse','HEAD^')==BASELINE_HEAD,'REPOSITORY_PARENT')
    record=schema.parse_json((repo/EVIDENCE_PATH).read_bytes())
    c.require(record['operatorId']==OPERATOR_ID and set(record['operatorFiles'])==set(OPERATOR_FILES), 'OPERATOR_IDENTITY')
    equal(c.digest(record['operatorFiles']),record['operatorCanonicalSha256'],'OPERATOR_IDENTITY')
    for name,pin in {**record['protectedHistoricalFiles'],**record['operatorFiles']}.items():
        schema._path(name)
        raw=(repo/name).read_bytes()
        c.require(len(raw)==pin['bytes'] and hashlib.sha256(raw).hexdigest()==pin['sha256'], 'OPERATOR_FILE_IDENTITY')


def metadata_preflight(config):
    """Published-checkout file API. Four fixed metadata files; no config authority."""
    c.require(type(config) is dict and set(config)=={'acceptance','operatorCommit'},'CONFIG_FIELDS')
    repo=Path(__file__).resolve().parents[1]
    verify_repository(repo,config['operatorCommit'])
    c.require(datetime.date.today()<=datetime.date(2026,10,25),'RETENTION_DEADLINE_EXCEEDED')
    from run_alpha_asr_campaign import require_private
    root=Path(config['acceptance']); require_private(root,repo)
    raw=[]
    for relative in METADATA_PATHS:
        path=root/relative; require_private(path,repo)
        raw.append(schema._read_local(str(path)))
    result=verify_metadata(*raw)
    result['metadataReadCount']=4
    return result


def campaign(config, authorization):
    raise ValueError('MEASURED_EXECUTION_NOT_AUTHORIZED')


def main(argv=None):
    args=sys.argv[1:] if argv is None else argv
    if args not in ([],['profile']):
        print('{"result":"BLOCKED","code":"MEASURED_EXECUTION_NOT_AUTHORIZED"}')
        return 2
    print(c.canonical({'operatorId':OPERATOR_ID,'evaluationProfile':c.profile(),
                       'evaluationProfileSha256':c.digest(c.profile())}).decode())
    return 0


if __name__=='__main__': sys.exit(main())
