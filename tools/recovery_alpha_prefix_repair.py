"""Admission for the approved surviving STREAM checkpoint-prefix repair."""
from pathlib import Path
import ast
import datetime
import hashlib
import json
import re
import runpy
import stat
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PREFLIGHT_SELECTORS = {
    'SUPPLEMENTAL_SQLITE': 'com.monumentogram.dora.poc.recovery.candidate.RecoveryE36GapiPreflightInstrumentedTest#supplementalCanonicalSqliteCompileOptionsPreflight',
    'JOURNAL_CONNECTIONS': 'com.monumentogram.dora.poc.recovery.journal.RecoveryJournalConnectionConfigurationTest#primaryAndConcurrentReaderKeepConfigurationAfterReopen',
    'PLATFORM_PREREQUISITES': 'com.monumentogram.dora.poc.recovery.candidate.RecoveryPlatformPrerequisitesInstrumentedTest#syntheticKeystoreLifecycleAndFilesystemPrerequisites',
}
PREFLIGHT_MARKERS = {
    'SUPPLEMENTAL_SQLITE': ('INSTRUMENTATION_SUPPLEMENTAL_SQLITE_PREFLIGHT_OBSERVATION ',
                            'INSTRUMENTATION_SUPPLEMENTAL_SQLITE_PREFLIGHT_STATUS '),
    'PLATFORM_PREREQUISITES': ('INSTRUMENTATION_PLATFORM_PREREQUISITES_OBSERVATION ',),
    'JOURNAL_CONNECTIONS': (),
}
PREFLIGHT_FLAGS = {'SUPPLEMENTAL_SQLITE': 'pocRecoveryE36GapiSupplementalSqlitePreflight',
                   'PLATFORM_PREREQUISITES': 'pocRecoveryPlatformPrerequisites',
                   'JOURNAL_CONNECTIONS': None}
FINGERPRINT = 'google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys'
BASELINE_COMMIT = 'c334470f835c27e235313005155f6e71a67a9696'
BASELINE_TREE = '04f3de4bf8953b8631c89467dc54b5d69f631858'
PREFIX = 'android/poc/recovery/src/'
PACKAGE = 'kotlin/com/monumentogram/dora/poc/recovery/'
ANDROID_REPAIR_PATHS = frozenset(PREFIX+part+'/'+PACKAGE+path for part,path in (
    ('androidTest','candidate/RecoveryCampaignInstrumentedTest.kt'),
    ('androidTest','candidate/RecoveryCampaignRunSnapshot.kt'),
    ('androidTest','journal/RecoveryJournalConnectionConfigurationTest.kt'),
    ('androidTest','journal/RecoveryStreamPrefixMigrationVerification.kt'),
    ('main','candidate/RecoveryArtifactSizeLimitObservation.kt'),
    ('main','candidate/RecoveryReconciliationOutcomes.kt'),
    ('main','candidate/RecoveryStreamingReconciliationController.kt'),
    ('main','contract/RecoveryStreamingPersistence.kt'),
    ('main','journal/AndroidRecoveryJournalDatabase.kt'),
    ('main','journal/AndroidRecoveryReconciliationSource.kt'),
    ('main','journal/RecoveryStreamPrefixSchema.kt'),
    ('main','storage/AndroidOsRecoveryReconciliationStorage.kt'),
    ('sharedTest','candidate/RecoveryCampaignParserClassification.kt'),
    ('sharedTest','candidate/RecoveryCampaignConfirmationAccess.kt'),
    ('sharedTest','candidate/RecoveryCampaignJournalRows.kt'),
    ('test','candidate/RecoveryCampaignParserClassificationTest.kt'),
    ('test','candidate/RecoveryCampaignConfirmationAccessTest.kt'),
    ('test','candidate/RecoveryCampaignJournalRowsTest.kt'),
    ('test','candidate/RecoveryStreamPrefixControllerCryptoTest.kt'),
    ('test','candidate/RecoveryStreamingSurvivingPrefixTest.kt'),
    ('test','journal/AndroidRecoveryReconciliationSourceTest.kt'),
    ('test','journal/RecoveryJournalSchemaPlanTest.kt'),
    ('test','journal/RecoveryStreamPrefixSchemaTest.kt'),
    ('test','storage/AndroidOsRecoveryReconciliationStorageTest.kt'),
))
DECISION_PATH = 'docs/adr/ADR-0008-stream-surviving-checkpoint-prefix.md'
HOST_REPAIR_PATHS = frozenset({
    'tools/recovery_campaign.py','tools/recovery_alpha_repair.py','tools/test_recovery_campaign.py',
    'tools/recovery_alpha_prefix_repair.py','tools/test_recovery_alpha_prefix_repair.py',
    'tools/test_rec_stream_prefix_schema.py','tools/test_recovery_alpha_repair.py',
    'tools/validate_recovery_0d6_candidate.py','tools/test_validate_recovery_0d6_candidate.py',
})
SCOPE = 'INTERNAL_ALPHA_E36_REDUCED_114'
LAUNCHER_SHA256 = '51d6a19a2b6361c2b91a15fe02df7dc337469177913b8361c6397acc07707541'
ADB_SHA256 = 'b4a6b455702684652cccf7b46258b29e653538904359a58fd4931cf3ef286b3f'
POWERSHELL_SHA256 = '8bb6fa8c283b4d92120b1ef249a9b311b0f804d4cabbe9981159976c8be76a5e'
PYTHON_SHA256 = '1a03cb7cb09e29053ae71a0d0e28555fe0ff3d22bb3a476eb9cc0d3899e73456'
CAPTURE_BASELINE_COMMIT = 'eda7a904fde8e1de211fa666b8e7d09e8d252b0d'
CAPTURE_BASELINE_TREE = '92c1db823a7d9f966a26e8a934f19c5601048dd0'
CAPTURE_HOST_PATHS = frozenset({'tools/recovery_alpha_prefix_repair.py',
                                'tools/test_recovery_alpha_prefix_repair.py'})
CAPTURE_APK_PAIR = dict(appApkSha256='2283d9d7dfad04c23df93b726e67864be9158013908d1b8404de12d2b706477a',
                        testApkSha256='0a0fee0650e3cad105cd675c685e4d002d6c59da5d7efced4cfa30f412ddd48b')
CAPTURE_OLD_CONTROLS = {
    'Invoke-0D6Campaign.ps1': LAUNCHER_SHA256,
    'Attempt05-Lifecycle-Functions.ps1': 'c9f34eddb60f4d28b6979dc5dd21ae20125fe003ffee4a92f71b92b5e7558256',
    'Campaign-Checkpoint.ps1': 'a18a9803bf583f5559f36cf6296a4757e333a3abafcab18400bd595c12aaaf79',
    'Logcat-Capture.ps1': '285040dbb3b2a9db2b0c21ba6871e09ceee8394f7b8f842d08b94e2753daeddb',
    'rec_i3_owned_process.psm1': '2c39aeb771cab4a2cdf4b8663365ecda2c1675e3f36d026d01065de7657cfdcf',
    'Shutdown-Member-Resolution.ps1': '9d004cf9184f0c7d222bd88131e8abfa7c5697502288a2f28a4e7b768ef0e6ac',
}


STREAM_PATH_BASELINE_COMMIT = 'e4e6e7a7c0268dd887466966eb67281daebe0fcd'
STREAM_PATH_BASELINE_TREE = 'cfd0c417c613101007d77952812b60a433668bec'
STREAM_PATH_OLD_IMPLEMENTATION_COMMIT = '3dfa2c64cf5081db2476f97feb99f0646b8ac3b2'
STREAM_PATH_OLD_IMPLEMENTATION_TREE = '174c33111dac169a859957d818d442353ecfd2f4'
STREAM_PATH_OLD_PREFIX_BINDING = dict(CAPTURE_APK_PAIR,
    applicabilitySha256='14753576a41201dbc852aecb454effe3ba002bfa6aaa7f30c784c25ffa651f34')
STREAM_PATH_OLD_CAPTURE_BINDING = dict(
    launcherSha256='e05118d721969e3fa61c92de63295827e31be13332099cbc558e25713d2d8e20',
    ownedProcessModuleSha256='448156a49c923180d5d21556f1e55820e60e6ae4a0a0da89abc694a3d5847609',
    proofSha256='71aa2e83ba149e0303b4d40849f8054ffed566ce34a50a681906c8bfaf7354b5')
STREAM_PATH_ANDROID_PATHS = frozenset(PREFIX+part+'/'+PACKAGE+name for part,name in (
    ('main','contract/RecoveryBinary.kt'), ('main','contract/RecoveryRecords.kt'),
    ('main','candidate/RecoveryStreamingTinkPrerequisiteCrypto.kt'),
    ('test','candidate/RecoveryStreamingTinkPrerequisiteCryptoTest.kt')))

COLLECTOR_BASELINE_COMMIT = '45d31930d7810e1e92fdecac22eed46084907ad3'
COLLECTOR_BASELINE_TREE = '556ff38870e372a93a8f11ee9d19f7ad6d883815'
COLLECTOR_OLD_IMPLEMENTATION_COMMIT = '5189299c68a2fcd095d8bdaeb4f26648d6f2d8fd'
COLLECTOR_OLD_IMPLEMENTATION_TREE = 'ea4a064183fb27312a88045a9d46d3c39225cbed'
COLLECTOR_OLD_PREFIX_BINDING = dict(
    appApkSha256='c9c88abbd2b043d70e3db092f208d3a493ac62c05b94d0187fd63b4ceaa61ed1',
    testApkSha256='0a0fee0650e3cad105cd675c685e4d002d6c59da5d7efced4cfa30f412ddd48b',
    applicabilitySha256='d68e2a70a058787a99a56fb5d178adc6d36b48548e2f539efb73b1a3a16eda27')
COLLECTOR_NATIVE_SCENARIOS = frozenset(('delayed','missed','capacity','lifetime','owner','image',
                                      'startup','deadline','identity','streams','historical','query'))

GIT_QUERY_BASELINE_COMMIT = 'cd24fa9eeab36909e3712ccf01953beba4946dd1'
GIT_QUERY_BASELINE_TREE = 'f76cfb837a24f9a73826dbe439481ea439072a12'
GIT_QUERY_OLD_IMPLEMENTATION_COMMIT = '908dc6f563823280a2fe7d60428b164aa6e72fc4'
GIT_QUERY_OLD_IMPLEMENTATION_TREE = '453c73bc90328b5f37f16c972c2a1c9ac53eddd8'
GIT_QUERY_OLD_PREFIX_BINDING = dict(COLLECTOR_OLD_PREFIX_BINDING,
    applicabilitySha256='5691aa633ee88b375b072b154e4a83c820f4ef59d2c9bc3576f5475a00c468e1')
GIT_QUERY_OLD_COLLECTOR_BINDING = dict(
    proofSha256=GIT_QUERY_OLD_PREFIX_BINDING['applicabilitySha256'],
    ownedProcessModuleSha256='95c5fd53561a5cb0a087aa0109ded39f6845c44c59cae3cdc4ae572e8f1ab45d')


# MICROFILE schema 6 is a new Android lineage, not a host-only Git-query repair.
MICROFILE_DISPOSITION_BASELINE_COMMIT = '88ea99747da4178aa60aca04b73d16679d603cd1'
MICROFILE_DISPOSITION_BASELINE_TREE = 'aedbff90ad4fbd68bd377df364b66463519219f4'
MICROFILE_DISPOSITION_OLD_IMPLEMENTATION_COMMIT = '0e83515058615cffde58335724fa14a466c48bf6'
MICROFILE_DISPOSITION_OLD_IMPLEMENTATION_TREE = '98123ff75274cb5b78eb77152ed2ddccb659f9a8'
MICROFILE_DISPOSITION_OLD_PREFIX_BINDING = dict(COLLECTOR_OLD_PREFIX_BINDING,
    applicabilitySha256='1e4e334cdad477495366a1f6b295f1da23455d5793246df22357829fc825acc6')
MICROFILE_DISPOSITION_OLD_GIT_BINDING = dict(
    proofSha256=MICROFILE_DISPOSITION_OLD_PREFIX_BINDING['applicabilitySha256'],
    lifecycleLibrarySha256='2a3fc3f1d84c24194fee78b6e8975f056f35ac71ce06110bd65f03b1fb5ab2c8')
MICROFILE_DISPOSITION_PROPOSAL_SHA256 = 'b4731330482017700d77a1348aec436b3859a6aac47bcc1ea8deb72114ddb4c1'
MICROFILE_DISPOSITION_DECISION_SHA256 = '80ec478bd6078520fceec075f42c26d2797a7078ac9824bba64616562d7713b1'
MICROFILE_DISPOSITION_ANDROID_PATHS = frozenset(PREFIX+part+'/'+PACKAGE+name for part,name in (
    ('androidTest','journal/RecoveryJournalConnectionConfigurationTest.kt'),
    ('androidTest','journal/RecoveryStreamPrefixMigrationVerification.kt'),
    ('androidTest','journal/RecoveryMicrofileDispositionMigrationVerification.kt'),
    ('main','candidate/RecoveryMicrofileReconciliationController.kt'),
    ('main','candidate/RecoveryMicrofileReferencedArtifacts.kt'),
    ('main','contract/RecoveryQuarantineIntent.kt'),
    ('main','journal/AndroidRecoveryJournalDatabase.kt'),
    ('main','journal/AndroidRecoveryQuarantineJournal.kt'),
    ('main','journal/AndroidRecoveryReconciliationSource.kt'),
    ('main','journal/RecoveryMicrofileDispositionSchema.kt'),
    ('main','storage/AndroidOsRecoveryReconciliationStorage.kt'),
    ('test','candidate/RecoveryMicrofileReferencedIntentTest.kt'),
    ('test','journal/AndroidRecoveryReconciliationSourceTest.kt'),
    ('test','journal/RecoveryJournalSchemaPlanTest.kt'),
    ('test','journal/RecoveryStreamPrefixSchemaTest.kt'),
    ('test','journal/RecoveryMicrofileDispositionSchemaTest.kt'),
    ('test','journal/RecoveryMicrofileQuarantineReadbackTest.kt'),
    ('test','storage/AndroidOsRecoveryReconciliationStorageTest.kt'),
))
MICROFILE_DISPOSITION_ADDED_PATHS = frozenset(PREFIX+part+'/'+PACKAGE+name for part,name in (
    ('androidTest','journal/RecoveryMicrofileDispositionMigrationVerification.kt'),
    ('main','candidate/RecoveryMicrofileReferencedArtifacts.kt'),
    ('main','journal/RecoveryMicrofileDispositionSchema.kt'),
    ('test','candidate/RecoveryMicrofileReferencedIntentTest.kt'),
    ('test','journal/RecoveryMicrofileDispositionSchemaTest.kt'),
    ('test','journal/RecoveryMicrofileQuarantineReadbackTest.kt'),
)) | frozenset({'docs/adr/ADR-0009-microfile-referenced-quarantine.md',
               'tools/test_rec_microfile_disposition_schema.py'})
MICROFILE_DISPOSITION_PATHS = MICROFILE_DISPOSITION_ANDROID_PATHS | CAPTURE_HOST_PATHS | frozenset({
    'docs/adr/ADR-0009-microfile-referenced-quarantine.md',
    'docs/DORA_MVP1_PRODUCT_DECISIONS.md',
    'docs/DORA_MVP1_STAGE_STATUS.md',
    'docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md',
    'tools/test_rec_microfile_disposition_schema.py',
    'tools/validate_stage00.py',
})


def microfile_disposition_binding(api):
    key='ALPHA_MICROFILE_DISPOSITION_BINDING'
    if key not in api:return None
    value=api[key]
    require(isinstance(value,dict) and set(value)=={'proofSha256'}
            and isinstance(value['proofSha256'],str) and re.fullmatch('[0-9a-f]{64}',value['proofSha256']),
            'Malformed MICROFILE disposition binding')
    return value


def microfile_disposition_metadata_literal(text):
    parsed=ast.parse(text);key='ALPHA_MICROFILE_DISPOSITION_BINDING'
    writes=[n for n in ast.walk(parsed) if isinstance(n,ast.Name) and n.id==key and isinstance(n.ctx,ast.Store)]
    if not writes:return
    assignments=[n for n in parsed.body if isinstance(n,ast.Assign) and len(n.targets)==1
                 and isinstance(n.targets[0],ast.Name) and n.targets[0].id==key]
    require(len(writes)==len(assignments)==1,'Ambiguous MICROFILE disposition metadata binding')
    require(isinstance(assignments[0].value,ast.Dict) and len(assignments[0].value.keys)==1,
            'MICROFILE disposition binding must be an exact one-key literal')
    try:value=ast.literal_eval(assignments[0].value)
    except (ValueError,TypeError,SyntaxError) as exc:raise ValueError('Nonliteral MICROFILE disposition binding') from exc
    microfile_disposition_binding({key:value})


def microfile_disposition_facts(api, profile, binding, historical_descriptor, owner_proposal,
                               owner_decision, build, apks, review):
    """Recompute immutable ancestry; return static facts, never runtime or coverage admission.

    build is the actual closed build manifest, not a synthetic PASS assertion. The
    independently pinned technical review must bind it and both rehashed APK files
    to this exact implementation. Build/APK provenance is reviewed before metadata
    freeze; this function checks those exact links, not Gradle logs heuristically.
    """
    require(microfile_disposition_binding(api) is not None,'Missing MICROFILE disposition binding')
    require(git_query_binding(api)==MICROFILE_DISPOSITION_OLD_GIT_BINDING,
            'Historical Git query binding changed')
    git=api['git'];legacy=legacy_api()
    require(git('rev-parse',MICROFILE_DISPOSITION_BASELINE_COMMIT+'^{tree}',root=ROOT)
                ==MICROFILE_DISPOSITION_BASELINE_TREE
            and git('merge-base',MICROFILE_DISPOSITION_BASELINE_COMMIT,profile.implementation_commit,root=ROOT)
                ==MICROFILE_DISPOSITION_BASELINE_COMMIT
            and git('rev-parse',profile.implementation_commit+'^{tree}',root=ROOT)==profile.implementation_tree,
            'MICROFILE disposition baseline, ancestry or implementation tree mismatch')
    before=legacy['tree_entries'](git,MICROFILE_DISPOSITION_BASELINE_COMMIT)
    after=legacy['tree_entries'](git,profile.implementation_commit)
    paths=sorted(p for p in before.keys()|after.keys() if before.get(p)!=after.get(p))
    require(set(paths)==MICROFILE_DISPOSITION_PATHS,'MICROFILE disposition differs from exact source delta')
    for p in paths:
        mode='100755' if p=='tools/validate_stage00.py' else '100644'
        require(after.get(p,{}).get('mode')==mode and after[p]['type']=='blob',
                'MICROFILE disposition source is deleted or nonregular')
        if p in MICROFILE_DISPOSITION_ADDED_PATHS:
            require(p not in before,'MICROFILE disposition added path already existed')
        else:
            require(before.get(p,{}).get('mode')==mode and before[p]['type']=='blob',
                    'MICROFILE disposition original source is missing or nonregular')
    require(isinstance(historical_descriptor,dict)
            and historical_descriptor.get('sha256')==MICROFILE_DISPOSITION_OLD_PREFIX_BINDING['applicabilitySha256'],
            'Historical Git query applicability pin mismatch')
    historical=capture_json(historical_descriptor)
    old_profile=type('HistoricalProfile',(),dict(
        implementation_commit=MICROFILE_DISPOSITION_OLD_IMPLEMENTATION_COMMIT,
        implementation_tree=MICROFILE_DISPOSITION_OLD_IMPLEMENTATION_TREE))()
    require(git('rev-parse',old_profile.implementation_commit+'^{tree}',root=ROOT)==old_profile.implementation_tree,
            'Historical Git query implementation tree changed')
    expected,frozen,controls=git_query_facts(api,old_profile,MICROFILE_DISPOSITION_OLD_PREFIX_BINDING,
        historical.get('historicalApplicability'),historical.get('newControls'),historical.get('nativeTest'),
        historical.get('independentReview'))
    capture_metadata_shape(api,old_profile,metadata_head=MICROFILE_DISPOSITION_BASELINE_COMMIT,
                           stream_path=True,collector_query=True,git_query=True)
    require(historical==expected,'Historical Git query applicability differs from original source facts')
    # Exact historical descriptors and six tested bytes: no new launcher derivation.
    for descriptor in controls.values():capture_file(descriptor)
    require(isinstance(owner_proposal,dict) and owner_proposal.get('sha256')==MICROFILE_DISPOSITION_PROPOSAL_SHA256,
            'MICROFILE disposition owner proposal pin mismatch')
    capture_file(owner_proposal)
    require(isinstance(owner_decision,dict) and owner_decision.get('sha256')==MICROFILE_DISPOSITION_DECISION_SHA256,
            'MICROFILE disposition owner decision pin mismatch')
    decision=capture_json(owner_decision)
    require(decision.get('approvedProposal')==owner_proposal,'MICROFILE disposition owner proposal link changed')
    require(decision.get('schema')=='DORA_EXPLICIT_OWNER_DECISION_V1'
            and decision.get('exactUserReply')=='Одобряю предложение schema 6'
            and decision.get('schemaVersion')==6 and decision.get('sharedSchema5Preserved') is True
            and decision.get('runtimeAcceptanceGranted') is False
            and decision.get('productFailuresReclassified') is False
            and decision.get('scope')==['MICROFILE TRU-03','MICROFILE COR-01','MICROFILE COR-04','MICROFILE TRU-02'],
            'MICROFILE disposition explicit owner decision scope mismatch')
    capture_file(build)
    pair_keys={'appApkSha256','testApkSha256'}
    require(isinstance(apks,dict) and set(apks)==pair_keys,'MICROFILE disposition exact APK descriptors required')
    pair={}
    for key,descriptor in apks.items():
        capture_file(descriptor)
        pair[key]=descriptor['sha256']
    require(pair=={key:binding.get(key) for key in pair_keys},'MICROFILE disposition APK pair differs from binding')
    validate_microfile_disposition_build(api,after,build,pair,owner_decision)
    implementation=dict(commit=profile.implementation_commit,tree=profile.implementation_tree)
    technical=capture_json(review)
    require(technical==dict(schema='DORA_MICROFILE_DISPOSITION_TECHNICAL_REVIEW_V1',
        implementation=implementation,ownerProposal=owner_proposal,ownerDecision=owner_decision,
        build=build,apks=apks,independentTechnicalReview=True,buildAndApkSourceVerified=True,
        historicalCoverageAutomaticallyGranted=False,runtimeAdmissionGranted=False),
        'MICROFILE disposition technical review source/build/APK linkage mismatch')
    methods=[]
    for p,method in sorted(legacy['PREFLIGHT_METHODS'].items()):
        require(after.get(p,{}).get('mode')=='100644' and after[p]['type']=='blob','Preflight method source missing')
        methods.append(dict(path=p,method=method,source=after[p]))
    require(len(methods)==3,'MICROFILE disposition requires three fresh preflight methods')
    result=dict(schema='DORA_RECOVERY_MICROFILE_DISPOSITION_APPLICABILITY_V1',scope=SCOPE,
        baseline=dict(commit=MICROFILE_DISPOSITION_BASELINE_COMMIT,tree=MICROFILE_DISPOSITION_BASELINE_TREE),
        implementation=implementation,apkPair=pair,apks=apks,build=build,
        sourceDelta=[dict(path=p,before=before.get(p),after=after[p]) for p in paths],
        historicalApplicability=historical_descriptor,controls=controls,
        ownerProposal=owner_proposal,ownerDecision=owner_decision,independentReview=review,
        frozenSelection=historical['frozenSelection'],historicalPreflightReusable=False,
        requiredFreshPreflight=methods,contract='MICROFILE_REFERENCED_DISPOSITION_SHARED_SCHEMA_6',
        historicalCoverageAutomaticallyGranted=False)
    return result,frozen,controls


def validate_microfile_disposition_proof(api, profile, binding, proof, descriptor, review):
    new_binding=microfile_disposition_binding(api)
    require(new_binding is not None and descriptor['sha256']==new_binding['proofSha256']
            ==binding['applicabilitySha256'],'MICROFILE disposition applicability pin mismatch')
    expected,frozen,controls=microfile_disposition_facts(api,profile,binding,
        proof.get('historicalApplicability'),proof.get('ownerProposal'),proof.get('ownerDecision'),
        proof.get('build'),proof.get('apks'),review)
    capture_metadata_shape(api,profile,stream_path=True,collector_query=True,git_query=True,
                           microfile_disposition=True)
    require(proof==expected,'MICROFILE disposition applicability differs from recomputed source facts')
    return frozen,controls


def validate_microfile_disposition_build(api, after, descriptor, pair, owner_decision):
    """Read only frozen copies; current source must match those tested bytes and Git blobs."""
    import xml.etree.ElementTree as ET
    build=capture_json(descriptor);base=Path(descriptor['path']).resolve().parent
    require(build.get('schema')=='DORA_MICROFILE_SCHEMA6_LOCAL_PASS_SNAPSHOT_V1'
            and build.get('baselineCommit')==MICROFILE_DISPOSITION_BASELINE_COMMIT
            and Path(build.get('sourceRoot','')).resolve()==ROOT.resolve()
            and build.get('schemaVersion')==6 and build.get('hostSchemaTests')==10
            and build.get('deviceCoverageGranted') is False and build.get('ciPassed') is False
            and build.get('apkPair')==pair
            and build.get('sourcePaths')==sorted(MICROFILE_DISPOSITION_ANDROID_PATHS),
            'MICROFILE disposition build scope/source/APK mismatch')
    owner=build.get('ownerDecision',{})
    require({k:owner.get(k) for k in ('path','sha256')}==owner_decision,'Build owner decision differs')
    files=build.get('files');require(isinstance(files,list) and 20<=len(files)<=512,'Unbounded build inventory')
    copies={};seen=set()
    for row in files:
        require(isinstance(row,dict) and set(row)=={'source','copy'},'Malformed build inventory row')
        source=row['source'];copy=row['copy']
        require(all(isinstance(d,dict) and set(d)=={'path','bytes','sha256'}
                    and type(d['bytes']) is int and d['bytes']>=0 for d in (source,copy)),
                'Malformed build file descriptor')
        p=capture_file({k:copy[k] for k in ('path','sha256')})
        require(p.resolve().is_relative_to(base) and p.resolve() not in seen
                and p.stat().st_size==copy['bytes']==source['bytes']
                and copy['sha256']==source['sha256'],'Build copied bytes or location differ')
        seen.add(p.resolve());original=Path(source['path'])
        require(original.is_absolute() and original not in copies,'Duplicate or relative build original')
        copies[original]=(p,source)
    for relative in sorted(MICROFILE_DISPOSITION_ANDROID_PATHS|{'tools/test_rec_microfile_disposition_schema.py'}):
        source=ROOT/relative
        require(source in copies,'Build missing exact source snapshot: '+relative)
        p,original=copies[source]
        capture_file({k:original[k] for k in ('path','sha256')})
        # Git's path-aware clean conversion handles the repository's unchanged
        # CRLF policy; --hash-object has no -w and never changes the object store.
        require(api['git']('hash-object','--path='+relative,str(p),root=ROOT)==after[relative]['object'],
                'Build source snapshot differs from implementation blob: '+relative)
    for key,relative in (
        ('appApkSha256','android/poc/recovery/build/outputs/apk/debug/recovery-debug.apk'),
        ('testApkSha256','android/poc/recovery/build/outputs/apk/androidTest/debug/recovery-debug-androidTest.apk')):
        require(ROOT/relative in copies and copies[ROOT/relative][1]['sha256']==pair[key],
                'Build APK copy differs from installed pair')
    tasks=['spotlessCheck','detekt',':poc:recovery:testDebugUnitTest',
        ':poc:recovery:compileDebugAndroidTestKotlin',':poc:recovery:lintDebug',
        ':poc:recovery:assembleDebug',':poc:recovery:assembleDebugAndroidTest']
    phase=build.get('phase');require(isinstance(phase,str) and re.fullmatch('verify-[0-9]{2}',phase),'Build phase invalid')
    def named(name):
        found=[(p,s) for original,(p,s) in copies.items() if original.name==name]
        require(len(found)==1,'Missing or ambiguous build evidence: '+name)
        return found[0]
    def receipt(stem,cwd):
        p,_=named(stem+'.receipt.json');value=json.loads(p.read_text(encoding='utf-8-sig'))
        require(type(value.get('nativeExitCode')) is int and value['nativeExitCode']==0
                and value.get('timedOut',False) is False and Path(value.get('cwd','')).resolve()==cwd.resolve(),
                'Build native receipt failure or cwd mismatch')
        capture_native_interval(value)
        p,_=named(stem+'.started.json');started=json.loads(p.read_text(encoding='utf-8-sig'))
        require(all(started.get(k)==value.get(k) for k in ('argv','cwd','startedAtUtc')),'Build native started/receipt mismatch')
        named(stem+'.stderr.log');named(stem+'.stdout.log')
        return value
    native=receipt('schema6-'+phase,ROOT/'android');argv=native.get('argv',[])
    require(len(argv)==17 and Path(argv[0]).name.lower()=='cmd.exe'
            and argv[1:9]==['/d','/c','gradlew.bat','--no-daemon','--offline','--no-configuration-cache','--max-workers=2','--init-script']
            and argv[10:]==tasks and Path(argv[9]) in copies,'Build native task invocation mismatch')
    p,_=named('schema6-'+phase+'.stdout.log');stdout=p.read_text(encoding='utf-8-sig')
    require('BUILD SUCCESSFUL' in stdout and all('> Task '+(t if t.startswith(':') else ':'+t) in stdout for t in tasks),
            'Build task completion missing')
    host=receipt('schema6-host-green-03',ROOT);argv=host.get('argv',[])
    require(len(argv)==11 and argv[1:]==['-X','utf8','-m','unittest','discover','-s','tools','-p','test_rec_*schema.py','-v'],
            'Host schema test invocation mismatch')
    p,_=named('schema6-host-green-03.stderr.log');text=p.read_text(encoding='utf-8-sig')
    require(re.search(r'Ran 10 tests in .*\r?\n\r?\nOK\s*$',text) is not None,'Host schema test result missing')
    counts=dict(tests=0,failures=0,errors=0,skipped=0);xml_count=0
    report_root=ROOT/'android/poc/recovery/build/test-results/testDebugUnitTest'
    for original,(p,_) in copies.items():
        if original.parent==report_root and original.name.startswith('TEST-') and original.suffix=='.xml':
            suite=ET.parse(p).getroot();xml_count+=1
            require(suite.tag=='testsuite','Foreign JVM report')
            for key in counts:counts[key]+=int(suite.attrib[key])
    require(xml_count>0 and counts==build.get('counts') and counts['tests']>0
            and all(counts[k]==0 for k in ('failures','errors','skipped')),'Build JVM report counts mismatch')


def git_query_binding(api):
    key='ALPHA_GIT_QUERY_BINDING'
    if key not in api:return None
    value=api[key]
    require(isinstance(value,dict) and set(value)=={'proofSha256','lifecycleLibrarySha256'}
            and all(isinstance(v,str) and re.fullmatch('[0-9a-f]{64}',v) for v in value.values()),
            'Malformed Git query binding')
    return value


def git_query_metadata_literal(text):
    parsed=ast.parse(text);key='ALPHA_GIT_QUERY_BINDING'
    writes=[n for n in ast.walk(parsed) if isinstance(n,ast.Name) and n.id==key and isinstance(n.ctx,ast.Store)]
    if not writes:return
    assignments=[n for n in parsed.body if isinstance(n,ast.Assign) and len(n.targets)==1
                 and isinstance(n.targets[0],ast.Name) and n.targets[0].id==key]
    require(len(writes)==len(assignments)==1,'Ambiguous Git query metadata binding')
    require(isinstance(assignments[0].value,ast.Dict) and len(assignments[0].value.keys)==2,
            'Git query binding must be an exact two-key literal')
    try:value=ast.literal_eval(assignments[0].value)
    except (ValueError,TypeError,SyntaxError) as exc:raise ValueError('Nonliteral Git query binding') from exc
    git_query_binding({key:value})


def git_query_launcher_bytes(old_controls, library_sha256):
    """Preserve the launcher except its exact lifecycle library digest."""
    data=capture_file(old_controls['Invoke-0D6Campaign.ps1']).read_bytes()
    old=old_controls['Attempt05-Lifecycle-Functions.ps1']['sha256'].upper().encode('ascii')
    require(data.count(old)==1,'Historical launcher lifecycle literal is not unique')
    return data.replace(old,library_sha256.upper().encode('ascii'))


def git_query_facts(api, profile, binding, historical_descriptor, controls, native_test, review):
    query_binding=git_query_binding(api)
    require(query_binding is not None,'Missing Git query binding')
    require(collector_query_binding(api)==GIT_QUERY_OLD_COLLECTOR_BINDING,
            'Historical collector binding changed')
    git=api['git'];legacy=legacy_api()
    require(git('rev-parse',GIT_QUERY_BASELINE_COMMIT+'^{tree}',root=ROOT)==GIT_QUERY_BASELINE_TREE
            and git('merge-base',GIT_QUERY_BASELINE_COMMIT,profile.implementation_commit,root=ROOT)==GIT_QUERY_BASELINE_COMMIT,
            'Git query source baseline or ancestry mismatch')
    before=legacy['tree_entries'](git,GIT_QUERY_BASELINE_COMMIT)
    after=legacy['tree_entries'](git,profile.implementation_commit)
    paths=sorted(p for p in before.keys()|after.keys() if before.get(p)!=after.get(p))
    require(set(paths)==CAPTURE_HOST_PATHS,'Git query repair differs from exact host-only delta')
    for p in paths:
        require(all(tree.get(p,{}).get('mode')=='100644' and tree[p]['type']=='blob' for tree in (before,after)),
                'Git query source is deleted or nonregular')
    pair={k:GIT_QUERY_OLD_PREFIX_BINDING[k] for k in ('appApkSha256','testApkSha256')}
    require({k:binding.get(k) for k in pair}==pair,'Git query repair changes APK pair')
    require(isinstance(historical_descriptor,dict)
            and historical_descriptor.get('sha256')==GIT_QUERY_OLD_PREFIX_BINDING['applicabilitySha256'],
            'Historical collector applicability pin mismatch')
    historical=capture_json(historical_descriptor)
    old_profile=type('HistoricalProfile',(),dict(implementation_commit=GIT_QUERY_OLD_IMPLEMENTATION_COMMIT,
                                              implementation_tree=GIT_QUERY_OLD_IMPLEMENTATION_TREE))()
    require(git('rev-parse',old_profile.implementation_commit+'^{tree}',root=ROOT)==old_profile.implementation_tree,
            'Historical collector implementation tree changed')
    expected,frozen,old_controls=collector_query_facts(api,old_profile,GIT_QUERY_OLD_PREFIX_BINDING,
        historical.get('historicalApplicability'),historical.get('newControls'),historical.get('nativeTests'),
        historical.get('independentReview'))
    capture_metadata_shape(api,old_profile,metadata_head=GIT_QUERY_BASELINE_COMMIT,stream_path=True,collector_query=True)
    require(historical==expected,'Historical collector applicability differs from source facts')
    require(isinstance(controls,dict) and set(controls)==set(old_controls),'Git query controls must be exact six siblings')
    launcher_hash=hashlib.sha256(git_query_launcher_bytes(old_controls,query_binding['lifecycleLibrarySha256'])).hexdigest()
    parents=set()
    for name,d in controls.items():
        p=capture_file(d);require(p.name==name,'Git query control basename mismatch');parents.add(p.parent.resolve())
        expected_hash=(query_binding['lifecycleLibrarySha256'] if name=='Attempt05-Lifecycle-Functions.ps1'
                       else launcher_hash if name=='Invoke-0D6Campaign.ps1' else old_controls[name]['sha256'])
        require(d['sha256']==expected_hash,'Unrelated Git query control changed')
    require(len(parents)==1 and parents.isdisjoint({Path(d['path']).resolve().parent for d in old_controls.values()})
            and controls['Attempt05-Lifecycle-Functions.ps1']['sha256']!=old_controls['Attempt05-Lifecycle-Functions.ps1']['sha256'],
            'Git query successor overwrites or reuses historical library')
    validate_capture_native_tests([native_test],controls)
    capture_file(review)
    result=dict(schema='DORA_RECOVERY_GIT_QUERY_REPAIR_V1',scope=SCOPE,sourceRoot=str(ROOT),
        baseline=dict(commit=GIT_QUERY_BASELINE_COMMIT,tree=GIT_QUERY_BASELINE_TREE),
        implementation=dict(commit=profile.implementation_commit,tree=profile.implementation_tree),apkPair=pair,
        sourceDelta=[dict(path=p,before=before[p],after=after[p]) for p in paths],
        historicalApplicability=historical_descriptor,oldControls=old_controls,newControls=controls,
        nativeTest=native_test,independentReview=review,frozenSelection=historical['frozenSelection'],
        historicalPreflightReusable=False,requiredFreshPreflight=historical['requiredFreshPreflight'])
    return result,frozen,controls


def validate_git_query_proof(api, profile, binding, proof, descriptor, review):
    query_binding=git_query_binding(api)
    require(query_binding is not None and descriptor['sha256']==query_binding['proofSha256']
            ==binding['applicabilitySha256'],'Git query applicability pin mismatch')
    expected,frozen,controls=git_query_facts(api,profile,binding,proof.get('historicalApplicability'),
        proof.get('newControls'),proof.get('nativeTest'),review)
    capture_metadata_shape(api,profile,stream_path=True,collector_query=True,git_query=True)
    require(proof==expected,'Git query applicability differs from recomputed source facts')
    return frozen,controls


def collector_query_binding(api):
    key='ALPHA_COLLECTOR_QUERY_BINDING'
    if key not in api:return None
    value=api[key]
    require(isinstance(value,dict) and set(value)=={'proofSha256','ownedProcessModuleSha256'}
            and all(isinstance(v,str) and re.fullmatch('[0-9a-f]{64}',v) for v in value.values()),
            'Malformed collector query binding')
    return value


def collector_query_metadata_literal(text):
    parsed=ast.parse(text);key='ALPHA_COLLECTOR_QUERY_BINDING'
    writes=[n for n in ast.walk(parsed) if isinstance(n,ast.Name) and n.id==key and isinstance(n.ctx,ast.Store)]
    if not writes:return
    assignments=[n for n in parsed.body if isinstance(n,ast.Assign) and len(n.targets)==1
                 and isinstance(n.targets[0],ast.Name) and n.targets[0].id==key]
    require(len(writes)==len(assignments)==1,'Ambiguous collector query metadata binding')
    require(isinstance(assignments[0].value,ast.Dict) and len(assignments[0].value.keys)==2,
            'Collector query binding must be an exact two-key literal')
    try:value=ast.literal_eval(assignments[0].value)
    except (ValueError,TypeError,SyntaxError) as exc:raise ValueError('Nonliteral collector query binding') from exc
    collector_query_binding({key:value})


def collector_launcher_bytes(old_controls, module_sha256):
    """The launcher changes only its literal embedded module digest."""
    data=capture_file(old_controls['Invoke-0D6Campaign.ps1']).read_bytes()
    old=old_controls['rec_i3_owned_process.psm1']['sha256'].upper().encode('ascii')
    require(data.count(old)==1,'Historical launcher module literal is not unique')
    return data.replace(old,module_sha256.upper().encode('ascii'))


def collector_query_facts(api, profile, binding, historical_descriptor, controls, tests, review):
    """Admit only a host collector derivative; rehash the unchanged Android lineage."""
    query_binding=collector_query_binding(api)
    require(query_binding is not None, 'Missing collector query binding')
    require(stream_path_binding(api)==dict(proofSha256=COLLECTOR_OLD_PREFIX_BINDING['applicabilitySha256']),
            'Historical STREAM path binding changed')
    git=api['git'];legacy=legacy_api()
    require(git('rev-parse',COLLECTOR_BASELINE_COMMIT+'^{tree}',root=ROOT)==COLLECTOR_BASELINE_TREE
            and git('merge-base',COLLECTOR_BASELINE_COMMIT,profile.implementation_commit,root=ROOT)==COLLECTOR_BASELINE_COMMIT,
            'Collector source baseline or ancestry mismatch')
    before=legacy['tree_entries'](git,COLLECTOR_BASELINE_COMMIT)
    after=legacy['tree_entries'](git,profile.implementation_commit)
    paths=sorted(p for p in before.keys()|after.keys() if before.get(p)!=after.get(p))
    require(set(paths)==CAPTURE_HOST_PATHS,'Collector repair differs from exact host-only delta')
    for p in paths:
        require(all(tree.get(p,{}).get('mode')=='100644' and tree[p]['type']=='blob' for tree in (before,after)),
                'Collector source is deleted or nonregular')
    pair={k:COLLECTOR_OLD_PREFIX_BINDING[k] for k in ('appApkSha256','testApkSha256')}
    require({k:binding.get(k) for k in pair}==pair,'Collector repair changes APK pair')
    require(isinstance(historical_descriptor,dict)
            and historical_descriptor.get('sha256')==COLLECTOR_OLD_PREFIX_BINDING['applicabilitySha256'],
            'Historical STREAM path applicability pin mismatch')
    historical=capture_json(historical_descriptor)
    old_profile=type('HistoricalProfile',(),dict(implementation_commit=COLLECTOR_OLD_IMPLEMENTATION_COMMIT,
                                              implementation_tree=COLLECTOR_OLD_IMPLEMENTATION_TREE))()
    require(git('rev-parse',old_profile.implementation_commit+'^{tree}',root=ROOT)==old_profile.implementation_tree,
            'Historical STREAM path implementation tree changed')
    expected,frozen,old_controls=stream_path_facts(api,old_profile,COLLECTOR_OLD_PREFIX_BINDING,
        historical.get('historicalApplicability'),historical.get('captureRepair'),historical.get('independentReview'))
    capture_metadata_shape(api,old_profile,metadata_head=COLLECTOR_BASELINE_COMMIT,stream_path=True)
    require(historical==expected,'Historical STREAM path applicability differs from source facts')
    require(isinstance(controls,dict) and set(controls)==set(old_controls),'Collector controls must be exact six siblings')
    launcher_hash=hashlib.sha256(collector_launcher_bytes(old_controls,query_binding['ownedProcessModuleSha256'])).hexdigest()
    parents=set()
    for name,d in controls.items():
        p=capture_file(d);require(p.name==name,'Collector control basename mismatch');parents.add(p.parent.resolve())
        expected_hash=(query_binding['ownedProcessModuleSha256'] if name=='rec_i3_owned_process.psm1'
                       else launcher_hash if name=='Invoke-0D6Campaign.ps1' else old_controls[name]['sha256'])
        require(d['sha256']==expected_hash,'Unrelated collector control changed')
    require(len(parents)==1 and parents.isdisjoint({Path(d['path']).resolve().parent for d in old_controls.values()})
            and controls['rec_i3_owned_process.psm1']['sha256']!=old_controls['rec_i3_owned_process.psm1']['sha256'],
            'Collector successor overwrites or reuses historical module')
    require(isinstance(tests,dict) and set(tests)==COLLECTOR_NATIVE_SCENARIOS,'Missing exact collector native scenarios')
    validate_capture_native_tests(list(tests.values()),controls)
    capture_file(review)
    result=dict(schema='DORA_RECOVERY_COLLECTOR_QUERY_REPAIR_V1',scope=SCOPE,sourceRoot=str(ROOT),
        baseline=dict(commit=COLLECTOR_BASELINE_COMMIT,tree=COLLECTOR_BASELINE_TREE),
        implementation=dict(commit=profile.implementation_commit,tree=profile.implementation_tree),apkPair=pair,
        sourceDelta=[dict(path=p,before=before[p],after=after[p]) for p in paths],
        historicalApplicability=historical_descriptor,oldControls=old_controls,newControls=controls,
        nativeTests=tests,independentReview=review,frozenSelection=historical['frozenSelection'],
        historicalPreflightReusable=False,requiredFreshPreflight=historical['requiredFreshPreflight'])
    return result,frozen,controls


def validate_collector_query_proof(api, profile, binding, proof, descriptor, review):
    query_binding=collector_query_binding(api)
    require(query_binding is not None and descriptor['sha256']==query_binding['proofSha256']
            ==binding['applicabilitySha256'],'Collector applicability pin mismatch')
    expected,frozen,controls=collector_query_facts(api,profile,binding,proof.get('historicalApplicability'),
        proof.get('newControls'),proof.get('nativeTests'),review)
    capture_metadata_shape(api,profile,stream_path=True,collector_query=True)
    require(proof==expected,'Collector applicability differs from recomputed source facts')
    return frozen,controls


def stream_path_binding(api):
    key='ALPHA_STREAM_PATH_REPAIR_BINDING'
    if key not in api:return None
    value=api[key]
    require(isinstance(value,dict) and set(value)=={'proofSha256'}
            and isinstance(value['proofSha256'],str) and re.fullmatch('[0-9a-f]{64}',value['proofSha256']),
            'Malformed STREAM path repair binding')
    return value


def stream_path_metadata_literal(text):
    """Reject executable/ambiguous new binding before candidate_api executes metadata."""
    parsed=ast.parse(text)
    key='ALPHA_STREAM_PATH_REPAIR_BINDING'
    writes=[n for n in ast.walk(parsed) if isinstance(n,ast.Name) and n.id==key and isinstance(n.ctx,ast.Store)]
    if not writes:return
    assignments=[n for n in parsed.body if isinstance(n,ast.Assign) and len(n.targets)==1
                 and isinstance(n.targets[0],ast.Name) and n.targets[0].id==key]
    require(len(writes)==len(assignments)==1,'Ambiguous STREAM path metadata binding')
    require(isinstance(assignments[0].value,ast.Dict) and len(assignments[0].value.keys)==1,
            'STREAM path binding must be an exact one-key literal')
    try:value=ast.literal_eval(assignments[0].value)
    except (ValueError,TypeError,SyntaxError) as exc:raise ValueError('Nonliteral STREAM path metadata binding') from exc
    stream_path_binding({key:value})


def validate_stream_path_proof(api, profile, binding, proof, descriptor, review):
    """Current successor facts and immutable historical proofs have distinct contexts."""
    path_binding=stream_path_binding(api)
    require(path_binding is not None and descriptor['sha256']==path_binding['proofSha256']
            ==binding['applicabilitySha256'], 'STREAM path applicability pin mismatch')
    expected,frozen,controls=stream_path_facts(api,profile,binding,proof.get('historicalApplicability'),
                                             proof.get('captureRepair'),review)
    capture_metadata_shape(api,profile,stream_path=True)
    require(proof==expected,'STREAM path applicability differs from recomputed source facts')
    return frozen,controls


def stream_path_facts(api, profile, binding, historical_descriptor, capture_descriptor, review):
    """Build inert facts before metadata freeze; not candidate or runtime admission."""
    require(capture_binding(api)==STREAM_PATH_OLD_CAPTURE_BINDING, 'Historical capture binding changed')
    require(all(isinstance(binding.get(k),str) and re.fullmatch('[0-9a-f]{64}',binding[k])
                for k in ('appApkSha256','testApkSha256')), 'Malformed new APK pair')
    legacy=legacy_api();git=api['git']
    require(git('rev-parse',STREAM_PATH_BASELINE_COMMIT+'^{tree}',root=ROOT)==STREAM_PATH_BASELINE_TREE
            and git('merge-base',STREAM_PATH_BASELINE_COMMIT,profile.implementation_commit,root=ROOT)==STREAM_PATH_BASELINE_COMMIT,
            'STREAM path baseline or ancestry mismatch')
    before=legacy['tree_entries'](git,STREAM_PATH_BASELINE_COMMIT)
    after=legacy['tree_entries'](git,profile.implementation_commit)
    paths=sorted(p for p in before.keys()|after.keys() if before.get(p)!=after.get(p))
    require(set(paths)==STREAM_PATH_ANDROID_PATHS|CAPTURE_HOST_PATHS, 'STREAM path repair differs from exact six-file delta')
    for p in paths:
        require(all(tree.get(p,{}).get('mode')=='100644' and tree[p]['type']=='blob' for tree in (before,after)),
                'STREAM path source is deleted or nonregular')
    require(isinstance(historical_descriptor,dict)
            and historical_descriptor.get('sha256')==STREAM_PATH_OLD_PREFIX_BINDING['applicabilitySha256'],
            'Historical prefix applicability pin mismatch')
    historical=capture_json(historical_descriptor)
    require(isinstance(historical,dict), 'Malformed historical prefix applicability')
    old_profile=type('HistoricalProfile',(),dict(implementation_commit=STREAM_PATH_OLD_IMPLEMENTATION_COMMIT,
                                              implementation_tree=STREAM_PATH_OLD_IMPLEMENTATION_TREE))()
    require(git('rev-parse',old_profile.implementation_commit+'^{tree}',root=ROOT)==old_profile.implementation_tree,
            'Historical implementation tree changed')
    old_capture=historical.get('captureRepair')
    require(old_capture==capture_descriptor, 'Historical capture descriptor changed')
    capture_document=capture_json(old_capture)
    # The fixed historical digest authenticates its old root as provenance. It
    # never claims that the current source checkout has the historical path.
    old_root=capture_document.get('sourceRoot')
    require(isinstance(old_root,str) and Path(old_root).is_absolute(), 'Historical capture root missing')
    controls=_validate_capture_repair_at(api,old_profile,STREAM_PATH_OLD_PREFIX_BINDING,old_capture,
                                       old_root,STREAM_PATH_BASELINE_COMMIT)
    old_expected=applicability_facts(api,old_profile,STREAM_PATH_OLD_PREFIX_BINDING)
    frozen_descriptor=historical.get('frozenSelection')
    frozen=json.loads(legacy['read_proof'](frozen_descriptor,legacy['FROZEN_SELECTION_SHA256']))
    old_review=historical.get('independentReview');capture_file(old_review)
    old_expected.update(captureRepair=old_capture,frozenSelection=frozen_descriptor,independentReview=old_review)
    require(historical==old_expected,'Historical prefix applicability differs from original source facts')
    capture_file(review)
    methods=[]
    for p,method in sorted(legacy['PREFLIGHT_METHODS'].items()):
        require(after.get(p,{}).get('mode')=='100644' and after[p]['type']=='blob','Preflight method source missing')
        methods.append(dict(path=p,method=method,source=after[p]))
    expected=dict(schema='DORA_RECOVERY_STREAM_PATH_REPAIR_APPLICABILITY_V1',scope=SCOPE,
        baseline=dict(commit=STREAM_PATH_BASELINE_COMMIT,tree=STREAM_PATH_BASELINE_TREE),
        implementation=dict(commit=profile.implementation_commit,tree=profile.implementation_tree),
        apkPair={k:binding[k] for k in ('appApkSha256','testApkSha256')},
        sourceDelta=[dict(path=p,before=before[p],after=after[p]) for p in paths],
        historicalApplicability=historical_descriptor,captureRepair=old_capture,frozenSelection=frozen_descriptor,
        independentReview=review,historicalPreflightReusable=False,requiredFreshPreflight=methods,
        contract='AUTHENTICATED_CHECKPOINT_EMBEDDED_PATH_TO_EXISTING_UNSAFE_PATH')
    return expected,frozen,controls


# Explicit TRU03 successor. Historical schema validators above remain unchanged.
TRU03_BASELINE_COMMIT = '3abf0f45ae637c5dedde5550b4bd19eb99f1ac5e'
TRU03_BASELINE_TREE = 'e85965dd6058b242a70c87a8c48c34ecbf20312b'
TRU03_OLD_IMPLEMENTATION = dict(commit='4279bcd7ad50d5d3f284640602f3e1fc6f351a31',
    tree='f2d8ee2ec9a721f963855421a2d5f61f6ba9f9e3')
TRU03_OLD_SOURCE = dict(commit=TRU03_BASELINE_COMMIT,tree=TRU03_BASELINE_TREE,
    appApkSha256='7cce368663e0de0ae287a38c234bab2c6140588a8fa2af3dbf1e883a18f8f064',
    testApkSha256='5ed8ca5ede2e0ca12dc23824833ee1c00cbaaf665f78847114f3cb8fc9a2a4b5')
TRU03_OLD_PROOF_SHA = 'b71860d73c63bd0215590e9a916dbad3c131a9045da1de1bcb56f8a01e9ba315'
TRU03_OLD_BUILD_SHA = 'a3cd848a3dd2419dfe5c911d35d2d44b013b39886a235420dfacf734c66ce78c'
TRU03_OLD_REVIEW_SHA = 'd31587e94d5099bad722c90613dfd360a8e92b42e9c587ea7c83fd3c10045d49'
TRU03_VALIDATION_FILES = {
    'tools/recovery_alpha_prefix_repair.py':'bdb4866a127c09e587925d67de78fcbd6101f8c70234aa29c3c1928ec62910fe',
    'tools/validate_recovery_0d6_candidate.py':'089217e05054b89b87aaf5c753e802b56c42d07757f9ad2dfcd28383f3ed8705',
    'tools/recovery_alpha_repair.py':'d21c97942dace722c83bd3a6564c53682351ec869efa0f324f3ecff2f5f62468'}
TRU03_IMPLEMENTATION_PATHS = frozenset({
    PREFIX+'androidTest/'+PACKAGE+'candidate/RecoveryCampaignInstrumentedTest.kt',
    'tools/recovery_campaign.py','tools/test_recovery_microfile_tru03.py',
    'tools/recovery_alpha_prefix_repair.py','tools/test_recovery_alpha_prefix_repair.py'})
TRU03_METADATA_PATHS = frozenset({'tools/validate_recovery_0d6_candidate.py',
    'tools/test_validate_recovery_0d6_candidate.py'})
TRU03_BINDING_KEY = 'ALPHA_MICROFILE_TRU03_BINDING'
TRU03_SCHEMA = 'DORA_RECOVERY_MICROFILE_TRU03_APPLICABILITY_V1'


def _tru03_git(*args, root):
    """Native bootstrap: never acquire Git code or root from repository metadata."""
    import subprocess
    try:
        return subprocess.run(['git','-c','safe.directory='+Path(root).resolve().as_posix(),*args],
            cwd=root,check=True,capture_output=True,text=True,
            env=dict(__import__('os').environ,GIT_OPTIONAL_LOCKS='0')).stdout.rstrip('\r\n')
    except (OSError,subprocess.CalledProcessError) as error:
        raise ValueError('TRU03_NATIVE_GIT_FAILED') from error


def _tru03_regular(path):
    path=Path(path)
    require(path.is_absolute() and '..' not in path.parts,'TRU03_ABSOLUTE_PATH')
    for item in (path,*path.parents):
        metadata=item.lstat()
        require(not stat.S_ISLNK(metadata.st_mode)
            and not getattr(metadata,'st_file_attributes',0)&stat.FILE_ATTRIBUTE_REPARSE_POINT,
            'TRU03_REPARSE_PATH')
    require(stat.S_ISREG(path.stat().st_mode),'TRU03_REGULAR_FILE')
    return path


def _tru03_json(descriptor):
    path=capture_file(descriptor)
    require(path.stat().st_size<=16*1024*1024,'TRU03_BOUNDED_JSON')
    def unique(pairs):
        value={}
        for key,item in pairs:
            require(key not in value,'TRU03_DUPLICATE_JSON_KEY');value[key]=item
        return value
    raw=path.read_bytes()
    require(hashlib.sha256(raw).hexdigest()==descriptor['sha256'],'TRU03_JSON_CHANGED')
    def nonfinite(value):raise ValueError('TRU03_NONFINITE_JSON')
    return json.loads(raw,object_pairs_hook=unique,parse_constant=nonfinite)


def _tru03_document_equal(actual,expected):
    """Exact new-schema scalar types: JSON false/true are never integer 0/1."""
    if type(actual) is not type(expected):return False
    if isinstance(expected,dict):
        return actual.keys()==expected.keys() and all(_tru03_document_equal(actual[k],v) for k,v in expected.items())
    if isinstance(expected,list):
        return len(actual)==len(expected) and all(_tru03_document_equal(a,b) for a,b in zip(actual,expected))
    return actual==expected


def _tru03_tree(revision,root):
    entries={}
    for record in _tru03_git('ls-tree','-rz',revision,root=root).split('\0'):
        if not record:continue
        header,path=record.split('\t',1);mode,kind,object_id=header.split()
        require(path not in entries and re.fullmatch('[0-9a-f]{40}',object_id), 'TRU03_TREE_ENTRY')
        entries[path]=dict(mode=mode,type=kind,object=object_id)
    return entries


def _tru03_source_identity(root):
    root=Path(root)
    require(root.is_absolute() and root.resolve()==root,'TRU03_CANONICAL_ROOT')
    require(Path(_tru03_git('rev-parse','--show-toplevel',root=root))==root,'TRU03_NATIVE_ROOT')
    require(not _tru03_git('status','--porcelain',root=root),'TRU03_DIRTY_SOURCE')
    head=_tru03_git('rev-parse','HEAD',root=root);tree=_tru03_git('rev-parse','HEAD^{tree}',root=root)
    require(re.fullmatch('[0-9a-f]{40}',head) and re.fullmatch('[0-9a-f]{40}',tree),'TRU03_NATIVE_IDENTITY')
    return dict(commit=head,tree=tree)


def _tru03_file_blob(path,relative,entry,root):
    path=_tru03_regular(path)
    require(entry is not None and entry['mode']=='100644' and entry['type']=='blob','TRU03_SOURCE_MODE')
    require(_tru03_git('hash-object','--path='+relative,str(path),root=root)==entry['object'],'TRU03_SOURCE_BLOB')
    return dict(path=str(path),sha256=file_sha(path))


def _tru03_repair_anchor(root):
    """One fixed pushed history anchor, inspected natively before metadata load."""
    implementation = dict(commit='3b29e249aeaeb945341b0c5b62d5a059a40ff79a',
        tree='85ac1791142715b05c852e81191726f4256abd6e')
    metadata_commit = '887111ee020f4073a2a40989f3bba99d3115304f'
    metadata_tree = '85b5432772bbf22c2854fb100c89e68bb925a3bf'
    require(_tru03_git('show','-s','--format=%P',metadata_commit,root=root)==implementation['commit']
        and _tru03_git('rev-parse',metadata_commit+'^{tree}',root=root)==metadata_tree,
        'TRU03_REPAIR_ANCHOR_IDENTITY')
    baseline,original,_ = _tru03_implementation(implementation,root)
    anchor = _tru03_tree(metadata_commit,root)
    changed = {p for p in original.keys()|anchor.keys() if original.get(p)!=anchor.get(p)}
    require(changed==TRU03_METADATA_PATHS and all(anchor[p]['mode']=='100644'
        and anchor[p]['type']=='blob' for p in changed),'TRU03_REPAIR_ANCHOR_METADATA_PATHS')
    path = 'tools/validate_recovery_0d6_candidate.py'
    before = _tru03_git('show',implementation['commit']+':'+path,root=root)
    after = _tru03_git('show',metadata_commit+':'+path,root=root)
    _tru03_metadata_shape_text(before,after,implementation)
    return baseline,anchor


def _tru03_implementation(implementation,root):
    require(isinstance(implementation,dict) and set(implementation)=={'commit','tree'}
        and all(isinstance(v,str) and re.fullmatch('[0-9a-f]{40}',v) for v in implementation.values()),
        'TRU03_IMPLEMENTATION_IDENTITY')
    commit=implementation['commit']
    parent = _tru03_git('show','-s','--format=%P',commit,root=root)
    require(_tru03_git('rev-parse',TRU03_BASELINE_COMMIT+'^{tree}',root=root)==TRU03_BASELINE_TREE
        and parent in (TRU03_BASELINE_COMMIT,'887111ee020f4073a2a40989f3bba99d3115304f')
        and _tru03_git('rev-parse',commit+'^{tree}',root=root)==implementation['tree'],'TRU03_IMPLEMENTATION_PARENT_TREE')
    before=_tru03_tree(TRU03_BASELINE_COMMIT,root);after=_tru03_tree(commit,root)
    if parent != TRU03_BASELINE_COMMIT:
        baseline,anchor = _tru03_repair_anchor(root)
        require(before==baseline and all(after.get(p)==before.get(p) for p in TRU03_METADATA_PATHS),
            'TRU03_REPAIR_METADATA_RESET')
        changed = {p for p in anchor.keys()|after.keys() if anchor.get(p)!=after.get(p)}
        require(changed==TRU03_METADATA_PATHS|{'tools/recovery_alpha_prefix_repair.py',
            'tools/test_recovery_alpha_prefix_repair.py'},'TRU03_REPAIR_EXACT_PARENT_DELTA')
    paths=sorted(p for p in before.keys()|after.keys() if before.get(p)!=after.get(p))
    require(set(paths)==TRU03_IMPLEMENTATION_PATHS,'TRU03_IMPLEMENTATION_DELTA')
    for path in paths:
        require(after.get(path,{}).get('mode')=='100644' and after[path]['type']=='blob','TRU03_IMPLEMENTATION_MODE')
        if path=='tools/test_recovery_microfile_tru03.py':
            require(path not in before,'TRU03_IMPLEMENTATION_ADDED_PATH')
        else:
            require(before.get(path,{}).get('mode')=='100644' and before[path]['type']=='blob','TRU03_IMPLEMENTATION_MODE')
    return before,after,[dict(path=p,before=before.get(p),after=after[p]) for p in paths]


def microfile_tru03_binding(api):
    if TRU03_BINDING_KEY not in api:return None
    value=api[TRU03_BINDING_KEY]
    require(isinstance(value,dict) and set(value)=={'proofSha256'} and isinstance(value['proofSha256'],str)
        and re.fullmatch('[0-9a-f]{64}',value['proofSha256']),'TRU03_METADATA_LITERAL')
    return value


def microfile_tru03_metadata_literal(text):
    parsed=ast.parse(text);names=[node for node in ast.walk(parsed) if isinstance(node,ast.Name) and node.id==TRU03_BINDING_KEY]
    if not names:return None
    assignments=[node for node in parsed.body if isinstance(node,ast.Assign) and len(node.targets)==1
        and isinstance(node.targets[0],ast.Name) and node.targets[0].id==TRU03_BINDING_KEY]
    require(len(names)==len(assignments)==1 and isinstance(names[0].ctx,ast.Store),'TRU03_METADATA_LITERAL')
    value=assignments[0].value
    require(isinstance(value,ast.Dict) and len(value.keys)==1,'TRU03_METADATA_LITERAL')
    try:return microfile_tru03_binding({TRU03_BINDING_KEY:ast.literal_eval(value)})
    except (ValueError,TypeError,SyntaxError) as error:raise ValueError('TRU03_METADATA_LITERAL') from error


def _tru03_metadata_body(text,names):
    parsed=ast.parse(text);values={};kept=[]
    for node in parsed.body:
        if isinstance(node,ast.Assign) and len(node.targets)==1 and isinstance(node.targets[0],ast.Name) and node.targets[0].id in names:
            key=node.targets[0].id;require(key not in values,'TRU03_METADATA_LITERAL')
            try:values[key]=ast.literal_eval(node.value)
            except (ValueError,TypeError,SyntaxError) as error:raise ValueError('TRU03_METADATA_LITERAL') from error
        else:kept.append(node)
    parsed.body=kept
    return ast.dump(parsed,include_attributes=False),values


def validate_tru03_metadata_shape(api,profile,metadata_head='HEAD'):
    # api is intentionally not a source of executable bootstrap helpers.
    root=ROOT;implementation=dict(commit=profile.implementation_commit,tree=profile.implementation_tree)
    before=_tru03_git('show',implementation['commit']+':tools/validate_recovery_0d6_candidate.py',root=root)
    after=_tru03_git('show',metadata_head+':tools/validate_recovery_0d6_candidate.py',root=root)
    return _tru03_metadata_shape_text(before,after,implementation)


def _tru03_metadata_shape_text(before,after,implementation):
    names={'IMPLEMENTATION_COMMIT','IMPLEMENTATION_TREE','ALPHA_PREFIX_REPAIR_BINDING',TRU03_BINDING_KEY}
    require(microfile_tru03_metadata_literal(before) is None,'TRU03_IMPLEMENTATION_ALREADY_BOUND')
    binding=microfile_tru03_metadata_literal(after)
    require(binding is not None,'TRU03_METADATA_BINDING_REQUIRED')
    old_body,old_values=_tru03_metadata_body(before,names);new_body,new_values=_tru03_metadata_body(after,names)
    require(set(old_values)==names-{TRU03_BINDING_KEY} and set(new_values)==names,'TRU03_METADATA_LITERAL')
    require(old_body==new_body,'TRU03_METADATA_BEHAVIOR')
    require(new_values['IMPLEMENTATION_COMMIT']==implementation['commit']
        and new_values['IMPLEMENTATION_TREE']==implementation['tree'],'TRU03_METADATA_IMPLEMENTATION')
    prefix=new_values['ALPHA_PREFIX_REPAIR_BINDING']
    require(isinstance(prefix,dict) and set(prefix)=={'appApkSha256','testApkSha256','applicabilitySha256'}
        and all(isinstance(v,str) and re.fullmatch('[0-9a-f]{64}',v) for v in prefix.values()),'TRU03_METADATA_PREFIX')
    require(prefix['applicabilitySha256']==binding['proofSha256'],'TRU03_METADATA_PROOF_HASH')
    return new_values


def bootstrap_current_metadata(proof=None):
    root=ROOT;identity=_tru03_source_identity(root);head=identity['commit']
    entries=_tru03_tree(head,root);path='tools/validate_recovery_0d6_candidate.py'
    descriptor=_tru03_file_blob(root/path,path,entries.get(path),root)
    raw=(root/path).read_bytes();require(len(raw)<=1024*1024,'TRU03_METADATA_SIZE')
    text=raw.decode('utf-8');parsed=ast.parse(text)
    claimed=any(isinstance(n,ast.Name) and n.id==TRU03_BINDING_KEY for n in ast.walk(parsed))
    proof_claim=isinstance(proof,dict) and proof.get('schema')==TRU03_SCHEMA
    ancestor=_tru03_git('merge-base',TRU03_BASELINE_COMMIT,head,root=root)
    descendant=head!=TRU03_BASELINE_COMMIT and ancestor==TRU03_BASELINE_COMMIT
    if not descendant:
        require(not claimed and not proof_claim,'TRU03_CONTEXT_MISMATCH')
        require(_tru03_source_identity(root)==identity and file_sha(root/path)==descriptor['sha256'],'TRU03_SOURCE_CHANGED')
        return dict(route='HISTORICAL',source=identity,metadata=descriptor)
    parent=_tru03_git('show','-s','--format=%P',head,root=root)
    require(re.fullmatch('[0-9a-f]{40}',parent) is not None,'TRU03_METADATA_PARENT')
    implementation=dict(commit=parent,tree=_tru03_git('rev-parse',parent+'^{tree}',root=root))
    _,after,delta=_tru03_implementation(implementation,root)
    changed={p for p in entries.keys()|after.keys() if entries.get(p)!=after.get(p)}
    require(changed==TRU03_METADATA_PATHS and all(entries[p]['mode']=='100644'
        and entries[p]['type']=='blob' for p in changed),'TRU03_METADATA_PATHS')
    before=_tru03_git('show',parent+':'+path,root=root)
    values=_tru03_metadata_shape_text(before,text,implementation)
    require(_tru03_source_identity(root)==identity and file_sha(root/path)==descriptor['sha256'],'TRU03_SOURCE_CHANGED')
    return dict(route='TRU03',source=identity,implementation=implementation,sourceDelta=delta,
        metadata=descriptor,literals=values)


def load_tru03_predecessor_context(predecessor):
    require(isinstance(predecessor,dict) and set(predecessor)=={'sourceRoot','finalSource','implementation','validationFiles','applicability'},'TRU03_PREDECESSOR_SHAPE')
    require(predecessor['finalSource']==TRU03_OLD_SOURCE and predecessor['implementation']==TRU03_OLD_IMPLEMENTATION
        and predecessor['applicability'].get('sha256')==TRU03_OLD_PROOF_SHA,'TRU03_PREDECESSOR_PINS')
    proof=_tru03_json(predecessor['applicability'])
    require(proof['build']['sha256']==TRU03_OLD_BUILD_SHA and proof['independentReview']['sha256']==TRU03_OLD_REVIEW_SHA,'TRU03_PREDECESSOR_COMPONENT_PINS')
    build=_tru03_json(proof['build']);root=Path(build['sourceRoot'])
    require(predecessor['sourceRoot']==str(root) and root.is_absolute() and root.resolve()==root
        and root!=ROOT.resolve(),'TRU03_PREDECESSOR_ROOT')
    require(predecessor['validationFiles']=={p:dict(path=str(root/p),sha256=s) for p,s in TRU03_VALIDATION_FILES.items()},'TRU03_PREDECESSOR_LOADER_SET')
    identity=_tru03_source_identity(root)
    require(identity==dict(commit=TRU03_BASELINE_COMMIT,tree=TRU03_BASELINE_TREE),'TRU03_PREDECESSOR_SOURCE')
    entries=_tru03_tree(TRU03_BASELINE_COMMIT,root)
    for relative,descriptor in predecessor['validationFiles'].items():
        capture_file(descriptor);_tru03_file_blob(root/relative,relative,entries.get(relative),root)
    # Only after all three native/blob/hash checks. Natural __file__, no init_globals.
    old=runpy.run_path(str(root/'tools/recovery_alpha_prefix_repair.py'))
    api=old['candidate_api']();profile=api['active_profile']()
    require(old['ROOT']==root and api['ROOT']==root
        and profile.implementation_commit==TRU03_OLD_IMPLEMENTATION['commit']
        and profile.implementation_tree==TRU03_OLD_IMPLEMENTATION['tree']
        and profile.maintenance_paths==TRU03_METADATA_PATHS
        and profile.base_commit=='55940df0c95e919a00708ae57e1b8aa23d89b6de'
        and TRU03_BINDING_KEY not in api,'TRU03_PREDECESSOR_PROFILE')
    api['validate'](profile,root=root,head=TRU03_BASELINE_COMMIT)
    return dict(root=root,identity=identity,old=old,api=api,profile=profile,proof=proof,predecessor=predecessor)


def validate_tru03_predecessor(predecessor):
    context=load_tru03_predecessor_context(predecessor);old=context['old'];api=context['api'];proof=context['proof']
    frozen,controls=old['validate_microfile_disposition_proof'](api,context['profile'],
        api['ALPHA_PREFIX_REPAIR_BINDING'],proof,predecessor['applicability'],proof['independentReview'])
    require(_tru03_source_identity(context['root'])==context['identity'],'TRU03_PREDECESSOR_CHANGED')
    entries=_tru03_tree(TRU03_BASELINE_COMMIT,context['root'])
    for relative,descriptor in predecessor['validationFiles'].items():
        capture_file(descriptor);_tru03_file_blob(context['root']/relative,relative,entries.get(relative),context['root'])
    return dict(frozen=frozen,controls=controls,proof=proof,
        inheritedBindings={k:v for k,v in api.items() if k.startswith('ALPHA_') and k.endswith('_BINDING')})


def tru03_route_required(api,proof,profile):
    binding=microfile_tru03_binding(api)
    claimed=isinstance(proof,dict) and proof.get('schema')==TRU03_SCHEMA
    # Every public entry reaches candidate_api's independent native ancestry
    # bootstrap first; this predicate cannot downgrade a rejected descendant.
    require(not claimed or binding is not None,'TRU03_METADATA_BINDING_REQUIRED')
    if binding is not None:
        require(claimed,'TRU03_PROOF_SCHEMA');return True
    return False


def validate_tru03_authorization(descriptor,predecessor):
    value=_tru03_json(descriptor)
    reduced=value.get('reducedOwnerDecision')
    require(isinstance(reduced,dict) and reduced.get('sha256')==
        '39d340cc6fe55ce8d467ccca7b13607127a7e7c4fdc9c5f00a010055e92a2091','TRU03_INHERITED_OWNER')
    capture_file(reduced)
    proof=predecessor['proof'];decision=_tru03_json(proof['ownerDecision'])
    require(proof['ownerDecision']['sha256']==MICROFILE_DISPOSITION_DECISION_SHA256
        and proof['ownerProposal']['sha256']==MICROFILE_DISPOSITION_PROPOSAL_SHA256
        and decision['approvedProposal']==proof['ownerProposal']
        and decision['schema']=='DORA_EXPLICIT_OWNER_DECISION_V1'
        and decision['exactUserReply']=='Одобряю предложение schema 6'
        and type(decision['schemaVersion']) is int and decision['schemaVersion']==6
        and decision['sharedSchema5Preserved'] is True and decision['runtimeAcceptanceGranted'] is False
        and decision['productFailuresReclassified'] is False
        and decision['scope']==['MICROFILE TRU-03','MICROFILE COR-01','MICROFILE COR-04','MICROFILE TRU-02'],
        'TRU03_INHERITED_SCHEMA6_AUTHORITY')
    capture_file(proof['ownerProposal'])
    expected=dict(schema='DORA_MICROFILE_TRU03_INHERITED_AUTHORIZATION_V1',scope=SCOPE,
        baseline=dict(commit=TRU03_BASELINE_COMMIT,tree=TRU03_BASELINE_TREE),
        reducedOwnerDecision=reduced,schema6OwnerDecision=proof['ownerDecision'],schema6OwnerProposal=proof['ownerProposal'],
        repairScope='MICROFILE_TRU03_AUTHENTICATED_RETAINED_APPEND',
        sourceBoundary='FIXED_FIVE_PATH_TRU03_IMPLEMENTATION_AND_TWO_PATH_METADATA',
        prerequisite='CLOSE_AND_PRESERVE_113_FROZEN_SOURCE_REQUIREMENTS',schemaVersion=6,
        sharedSchema5Preserved=True,originalProductFailuresPreserved=True,newOwnerApprovalRequired=False,
        runtimeAdmissionGranted=False,historicalCoverageAutomaticallyGranted=False,prMergeAuthorized=False,
        privateEvidencePublicationAuthorized=False)
    require(_tru03_document_equal(value,expected),'TRU03_AUTHORIZATION_SHAPE')
    return value


# Strict B replay segment copied from independently prepared inert parser.
TRU03_BASELINE = dict(commit='3abf0f45ae637c5dedde5550b4bd19eb99f1ac5e', tree='e85965dd6058b242a70c87a8c48c34ecbf20312b')
TRU03_HARNESS = 'android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignInstrumentedTest.kt'
TRU03_B_IMPLEMENTATION_PATHS = tuple(sorted((TRU03_HARNESS, 'tools/recovery_campaign.py', 'tools/test_recovery_microfile_tru03.py', 'tools/recovery_alpha_prefix_repair.py', 'tools/test_recovery_alpha_prefix_repair.py')))
TRU03_B_METADATA_PATHS = ('tools/test_validate_recovery_0d6_candidate.py', 'tools/validate_recovery_0d6_candidate.py')
TRU03_TASKS = ('spotlessCheck','detekt',':poc:recovery:testDebugUnitTest',':poc:recovery:compileDebugAndroidTestKotlin',':poc:recovery:lintDebug',':poc:recovery:assembleDebug',':poc:recovery:assembleDebugAndroidTest')
TRU03_HOST_MODULES = ('test_recovery_microfile_tru03','test_recovery_campaign','test_rec_microfile_disposition_schema','test_recovery_alpha_prefix_repair','test_recovery_alpha_repair','test_validate_recovery_0d6_candidate')
TRU03_BUILD_KEYS = set('schema phase sourceRoot baseline sourceState authorization androidSchemaVersion implementationPaths androidDeltaPaths sourceFiles inputFiles localProperties evidence tools environment detektTransport jvmCounts jvmEvidence hostCounts apkPair generator deviceCoverageGranted ciPassed runtimeAdmissionGranted'.split())
TRU03_ENV_KEYS = set('schema sourceRoot androidHome javaHome pythonPath pythonPathOverride pythonHomeOverride gradleUserHome userGradleProperties userInitScripts'.split())
TRU03_APK_PATHS = dict(appApkSha256='android/poc/recovery/build/outputs/apk/debug/recovery-debug.apk', testApkSha256='android/poc/recovery/build/outputs/apk/androidTest/debug/recovery-debug-androidTest.apk')


def tru03_require(ok, label):
    if not ok:
        raise ValueError(label)


def tru03_exact(value, keys, label):
    tru03_require(type(value) is dict and set(value) == set(keys), label)


def tru03_json_bytes(raw):
    tru03_require(type(raw) is bytes and len(raw) <= 32 * 1024 * 1024, 'JSON_BOUND')
    def pairs(items):
        result = {}
        for key, value in items:
            tru03_require(key not in result, 'JSON_DUPLICATE_KEY')
            result[key] = value
        return result
    def bad_constant(value):
        raise ValueError('JSON_NONFINITE')
    try:
        return json.loads(raw.decode('utf-8-sig'), object_pairs_hook=pairs, parse_constant=bad_constant)
    except (UnicodeError, json.JSONDecodeError) as error:
        raise ValueError('JSON_INVALID') from error


def tru03_regular(path):
    tru03_require(type(path) is str and Path(path).is_absolute() and '..' not in Path(path).parts, 'FILE_ABSOLUTE_PATH')
    p = Path(path)
    for part in (p, *p.parents):
        s = part.lstat()
        tru03_require(not stat.S_ISLNK(s.st_mode) and not (getattr(s, 'st_file_attributes', 0) & 0x400), 'FILE_REPARSE_COMPONENT')
    tru03_require(stat.S_ISREG(p.stat().st_mode), 'FILE_REGULAR')
    tru03_require(p.resolve() == p, 'FILE_CANONICAL')
    return p


def tru03_file(d, sized=True):
    tru03_exact(d, ('path','bytes','sha256') if sized else ('path','sha256'), 'FILE_DESCRIPTOR')
    tru03_require(type(d['sha256']) is str and re.fullmatch('[0-9a-f]{64}', d['sha256']), 'FILE_DESCRIPTOR')
    if sized:
        tru03_require(type(d['bytes']) is int and d['bytes'] >= 0, 'FILE_DESCRIPTOR')
    p = tru03_regular(d['path']); before = p.stat(); data = p.read_bytes(); after = p.stat()
    tru03_require((before.st_dev,before.st_ino,before.st_size,before.st_mtime_ns) == (after.st_dev,after.st_ino,after.st_size,after.st_mtime_ns), 'FILE_READ_DRIFT')
    tru03_require(hashlib.sha256(data).hexdigest() == d['sha256'] and (not sized or len(data) == d['bytes']), 'FILE_DIGEST_OR_LENGTH')
    return data


def tru03_desc(p, sized=True):
    p = tru03_regular(str(p)); data = p.read_bytes()
    result = dict(path=str(p), sha256=hashlib.sha256(data).hexdigest())
    if sized: result['bytes'] = len(data)
    return result


def tru03_time(text):
    try:
        value = datetime.datetime.fromisoformat(text.replace('Z', '+00:00'))
        tru03_require(value.tzinfo is not None and value.utcoffset() is not None, 'NATIVE_TIME')
        return value
    except (TypeError, AttributeError, ValueError) as error:
        raise ValueError('NATIVE_TIME') from error


def tru03_native(started, receipt, argv, cwd):
    tru03_exact(started, ('argv','cwd','startedAtUtc'), 'NATIVE_KEYS')
    tru03_exact(receipt, ('argv','cwd','startedAtUtc','nativeExitCode','endedAtUtc'), 'NATIVE_KEYS')
    tru03_require(type(receipt['nativeExitCode']) is int and receipt['nativeExitCode'] == 0, 'NATIVE_EXIT')
    tru03_require(type(argv) is list and all(type(a) is str for a in argv) and started['argv'] == receipt['argv'] == argv and started['cwd'] == receipt['cwd'] == cwd and started['startedAtUtc'] == receipt['startedAtUtc'], 'NATIVE_CONTEXT')
    begin, end = tru03_time(receipt['startedAtUtc']), tru03_time(receipt['endedAtUtc'])
    tru03_require(begin <= end, 'NATIVE_TIME')
    return begin, end


def tru03_jvm_xml(documents):
    tru03_require(type(documents) is list and documents, 'JVM_XML_EMPTY')
    total = dict(tests=0, failures=0, errors=0, skipped=0); suites = set(); identities = set()
    for raw in documents:
        tru03_require(b'<!DOCTYPE' not in raw and b'<!ENTITY' not in raw, 'JVM_XML_ENTITY')
        try: suite = ET.fromstring(raw)
        except ET.ParseError as error: raise ValueError('JVM_XML_INVALID') from error
        tru03_require(suite.tag == 'testsuite' and suite.get('name') and suite.get('name') not in suites, 'JVM_DUPLICATE_SUITE')
        suites.add(suite.get('name')); tests = suite.findall('testcase')
        tru03_require(len(tests) > 0 and len(suite.findall('.//testcase')) == len(tests), 'JVM_CASE_STRUCTURE')
        counts = {}
        for key in total:
            text = suite.get(key, '')
            tru03_require(re.fullmatch('[0-9]+', text) is not None, 'JVM_COUNT_TYPE')
            counts[key] = int(text)
        tru03_require(counts == dict(tests=len(tests), failures=0, errors=0, skipped=0), 'JVM_COUNTS')
        for case in tests:
            identity = (case.get('classname'), case.get('name'))
            tru03_require(all(identity) and identity not in identities, 'JVM_DUPLICATE_CASE')
            identities.add(identity)
            tru03_require(not any(case.findall(tag) for tag in ('failure','error','skipped')) and not any(suite.findall('.//'+tag) for tag in ('failure','error','skipped')), 'JVM_FAILED_CASE')
        total['tests'] += len(tests)
    return total


def tru03_host_counts(log):
    tru03_require(type(log) is str, 'HOST_TEXT')
    # Semantic parsing only. The original complete log bytes remain hash-bound.
    log = log.replace('\r\n', '\n')
    rows = re.findall(r'^([^\n]+) \(([^\n]+)\) \.\.\. (.+)$', log, re.M)
    totals = re.findall(r'^Ran ([0-9]+) tests? in .+$', log, re.M)
    tru03_require(len(totals) == 1 and int(totals[0]) == len(rows) > 0 and log.rstrip().endswith('\nOK'), 'HOST_TERMINAL_COUNT')
    tru03_require(all(outcome == 'ok' for _,_,outcome in rows), 'HOST_NONPASS')
    for module in TRU03_HOST_MODULES:
        tru03_require(any(identity.startswith(module+'.') for _,identity,_ in rows), 'HOST_MISSING_MODULE:'+module)
    tru03_require('_FailedTest' not in log and 'Traceback (most recent call last)' not in log, 'HOST_LOADER_ERROR')
    return dict(methods=len(rows), failures=0, errors=0, skipped=0)


def tru03_gradle_log(log, evidence):
    tru03_exact(evidence, ('kind','taskOutcome','origin','executionPolicy'), 'JVM_EVIDENCE_KEYS')
    tru03_require(evidence == dict(kind='CURRENT_EXECUTION',taskOutcome='EXECUTED',origin=None,executionPolicy='FORCE_RECOVERY_JVM_TASK'), 'JVM_CURRENT_ONLY')
    tru03_require(re.search(r'^BUILD SUCCESSFUL(?: .*)?$', log, re.M) and 'BUILD FAILED' not in log, 'GRADLE_SUCCESS')
    for task in TRU03_TASKS:
        name = task if task.startswith(':') else ':'+task
        matches = re.findall(r'^> Task '+re.escape(name)+r'(?: ([A-Z-]+))?\s*$', log, re.M)
        tru03_require(len(matches) == 1, 'GRADLE_TASK:'+task)
        if task == ':poc:recovery:testDebugUnitTest':
            tru03_require(matches == [''], 'JVM_NOT_EXECUTED')
        else:
            tru03_require(matches[0] not in ('FAILED','SKIPPED'), 'GRADLE_TASK:'+task)


def tru03_tree_delta(before, after):
    tru03_require(type(before) is dict and type(after) is dict, 'IMPLEMENTATION_TREE_TYPE')
    delta = sorted(p for p in set(before)|set(after) if before.get(p) != after.get(p))
    tru03_require(delta == list(TRU03_B_IMPLEMENTATION_PATHS), 'IMPLEMENTATION_EXACT_FIVE_PATHS')
    for path in delta:
        old, new = before.get(path), after.get(path)
        for item in (old,new):
            if item is not None:
                tru03_exact(item, ('mode','type','object'), 'IMPLEMENTATION_ENTRY')
                tru03_require(item['mode']=='100644' and item['type']=='blob' and re.fullmatch('[0-9a-f]{40}',item['object']), 'IMPLEMENTATION_MODE_BLOB')
        tru03_require(new is not None and ((old is None) == (path=='tools/test_recovery_microfile_tru03.py')), 'IMPLEMENTATION_BASELINE_PRESENCE')
    return [dict(path=p,before=before.get(p),after=after[p]) for p in delta]


def tru03_dependency_paths(root, tree):
    """Fixed source/config rules; data never supplies an input allowlist."""
    root = Path(root); android = root/'android'
    regular = []
    for p in android.rglob('*'):
        if p.is_file() and 'build' not in p.relative_to(android).parts:
            regular.append(p.relative_to(root).as_posix())
    kotlin = sorted(p for p in regular if '/src/' in p and p.endswith('.kt'))
    gradle = sorted(p for p in regular if p.endswith('.gradle.kts'))
    required = set(kotlin+gradle)
    required.update(p for p in regular if p.startswith(('android/poc/recovery/src/','android/build-logic/src/')))
    required.update(p for p in tree if p.startswith('android/') and (p.endswith('.lockfile') or '/gradle/dependency-locks/' in p))
    required.update(p for p in tree if Path(p).name=='.gitattributes')
    required.update('android/'+p for p in ('settings.gradle.kts','build.gradle.kts','gradle.properties','gradlew','gradlew.bat','gradle/wrapper/gradle-wrapper.jar','gradle/wrapper/gradle-wrapper.properties','gradle/libs.versions.toml','gradle/verification-metadata.xml','config/detekt/detekt.yml','build-logic/settings.gradle.kts','build-logic/build.gradle.kts','poc/vpn-contract-kernel/settings.gradle.kts','poc/vpn-contract-kernel/build.gradle.kts'))
    required.update('tools/'+m+'.py' for m in TRU03_HOST_MODULES)
    required.update('tools/'+m+'.py' for m in ('recovery_campaign','recovery_alpha_prefix_repair','recovery_alpha_repair','validate_recovery_0d6_candidate','recovery_instrumentation_status','test_recovery_instrumentation_status','test_rec_stream_prefix_schema','verify_rec_i3_streaming_sqlite'))
    required.update('docs/stage0/'+p for p in ('DORA_0D6_ALPHA_PREFLIGHT_OWNER_DECISION_20260914.md','DORA_0D6_ALPHA_E36_CAMPAIGN_OWNER_DECISION_20260914.md','DORA_0D6_ALPHA_REDUCED_SCOPE_OWNER_DECISION_20260915.md'))
    required.update('docs/stage0/poc-recovery-protocol-stage0-v0.'+str(n)+'.json' for n in range(3,9))
    required.add('docs/adr/ADR-0005-poc-recovery-streaming-persistence-and-range-quarantine.md')
    tru03_require(set(kotlin+gradle) <= set(tree), 'INPUT_UNTRACKED_SELECTED_SOURCE')
    tru03_require(required <= set(tree), 'INPUT_REQUIRED_PATH_MISSING:'+','.join(sorted(required-set(tree))))
    for path in required: tru03_regular(str(root/path))
    return dict(inputs=sorted(required-set(TRU03_B_IMPLEMENTATION_PATHS)),detekt=kotlin,spotlessKotlin=kotlin,spotlessGradle=gradle)


class Tru03BuildContext:
    """Trusted-code boundary, not a document schema or proof-controlled policy.

    native_check verifies actual source HEAD/parent/tree/delta/index and metadata
    phase; blob_for_copy performs native hash-object --path without -w. Policy
    descriptors/bytes must be fixed by the independently reviewed caller.
    """
    def __init__(self, *, root, snapshot_root, raw_root, authorization, generator,
                 baseline_entries, target_entries, blob_for_copy, native_check,
                 tools, environment, init_bytes, metadata_current=None):
        self.root=Path(root); self.snapshot_root=Path(snapshot_root); self.raw_root=Path(raw_root)
        self.authorization=authorization; self.generator=generator
        self.baseline_entries=baseline_entries; self.target_entries=target_entries
        self.blob_for_copy=blob_for_copy; self.native_check=native_check
        self.tools=tools; self.environment=environment; self.init_bytes=init_bytes
        self.metadata_current={} if metadata_current is None else metadata_current


def tru03_pair(pair, source, copy):
    tru03_exact(pair, ('source','copy'), 'COPY_PAIR_KEYS')
    tru03_require(pair['source']['path']==str(source) and pair['copy']['path']==str(copy), 'COPY_EXACT_PATH')
    original=tru03_file(pair['source']); copied=tru03_file(pair['copy'])
    tru03_require(original==copied, 'COPY_BYTES_DIFFER')
    return copied


def validate_microfile_tru03_build(build, context):
    """Return recomputed B facts. CURRENT_EXECUTION only; no I/V/P/M in B."""
    c=context; root=c.root; snapshot=c.snapshot_root
    c.native_check()
    tru03_exact(build, TRU03_BUILD_KEYS, 'BUILD_EXACT_KEYS')
    tru03_require(build['schema']=='DORA_MICROFILE_TRU03_TESTED_BUILD_SNAPSHOT_V1' and re.fullmatch('verify-[0-9]{2}',build['phase']), 'BUILD_SCHEMA_PHASE')
    tru03_require(build['baseline']==TRU03_BASELINE and build['sourceState']=='PRECOMMIT_EXACT_FIVE_PATH_DELTA' and build['sourceRoot']==str(root) and type(build['androidSchemaVersion']) is int and build['androidSchemaVersion']==6, 'BUILD_SOURCE_CONTEXT')
    tru03_require(root.is_absolute() and snapshot.is_absolute() and root!=snapshot and not snapshot.is_relative_to(root), 'BUILD_ROOT_SEPARATION')
    tru03_require(build['implementationPaths']==list(TRU03_B_IMPLEMENTATION_PATHS) and build['androidDeltaPaths']==[TRU03_HARNESS], 'BUILD_IMPLEMENTATION_SCOPE')
    tru03_require(all(build[k] is False for k in ('deviceCoverageGranted','ciPassed','runtimeAdmissionGranted')), 'BUILD_NO_COVERAGE')
    tru03_require(build['authorization']==c.authorization and build['generator']==c.generator, 'BUILD_TRUSTED_AUTHORITY_GENERATOR')
    tru03_file(build['authorization'],False);tru03_file(build['generator'])
    delta=tru03_tree_delta(c.baseline_entries,c.target_entries)
    selection=tru03_dependency_paths(root,c.target_entries)
    for key,paths,subdir in [('sourceFiles',list(TRU03_B_IMPLEMENTATION_PATHS),'source'),('inputFiles',selection['inputs'],'inputs')]:
        rows=build[key];tru03_require(type(rows) is list and [x.get('path') for x in rows]==paths, 'BUILD_EXACT_'+key)
        for row in rows:
            tru03_exact(row,('path','before','after','source','copy'),'SOURCE_ROW_KEYS');p=row['path']
            tru03_require(row['before']==c.baseline_entries.get(p) and row['after']==c.target_entries[p], 'SOURCE_TREE_ENTRY')
            tru03_require(key!='inputFiles' or row['before']==row['after'], 'INPUT_CHANGED')
            tru03_require(row['source']['path']==str(root/p) and row['copy']['path']==str(snapshot/subdir/p), 'SOURCE_COPY_PATH')
            copied=tru03_file(row['copy'])
            if p in c.metadata_current:
                tru03_require(p in TRU03_B_METADATA_PATHS and c.metadata_current[p]==tru03_desc(root/p), 'METADATA_CURRENT_BINDING')
                tru03_require(row['source']['bytes']==len(copied) and row['source']['sha256']==hashlib.sha256(copied).hexdigest(), 'METADATA_IMPLEMENTATION_COPY')
            else:tru03_require(tru03_file(row['source'])==copied,'SOURCE_COPY_DRIFT')
            tru03_require(c.blob_for_copy(p,Path(row['copy']['path']))==row['after']['object'], 'SOURCE_PATH_AWARE_BLOB')
    tru03_require(build['tools']==c.tools, 'BUILD_TOOL_POLICY')
    tru03_exact(build['tools'],('python','cmd','java','recorder','runner','androidSdkRoot','javaHome'),'BUILD_TOOL_KEYS')
    for k in ('python','cmd','java','recorder','runner'):tru03_file(build['tools'][k])
    environment=tru03_json_bytes(tru03_file(build['environment']))
    tru03_exact(environment,TRU03_ENV_KEYS,'ENVIRONMENT_KEYS')
    tru03_require(environment==c.environment and environment['schema']=='DORA_MICROFILE_TRU03_BUILD_ENVIRONMENT_V1' and environment['sourceRoot']==str(root), 'ENVIRONMENT_TRUSTED_CURRENT_POLICY')
    tru03_require(environment['androidHome']==c.tools['androidSdkRoot'] and environment['javaHome']==c.tools['javaHome'] and environment['pythonPath']==c.tools['python']['path'], 'ENVIRONMENT_TOOL_CONTEXT')
    for k in ('pythonPathOverride','pythonHomeOverride'):tru03_require(environment[k] is None or type(environment[k]) is str,'ENVIRONMENT_OVERRIDE_TYPE')
    tru03_require(type(environment['gradleUserHome']) is str and Path(environment['gradleUserHome']).is_absolute(),'ENVIRONMENT_EXISTING_GRADLE_HOME')
    if environment['userGradleProperties'] is not None:tru03_file(environment['userGradleProperties'])
    scripts=environment['userInitScripts'];tru03_require(type(scripts) is list and [x['path'] for x in scripts]==sorted(set(x['path'] for x in scripts)),'ENVIRONMENT_INIT_ORDER')
    for script in scripts:tru03_file(script)
    lp=root/'android/local.properties'
    if lp.exists():tru03_pair(build['localProperties'],lp,snapshot/'inputs/android/local.properties')
    else:tru03_require(build['localProperties'] is None,'LOCAL_PROPERTIES_ABSENT')
    transport=build['detektTransport'];tru03_exact(transport,('manifest','initScript','argumentFile'),'TRANSPORT_KEYS')
    for item in transport.values():tru03_require(Path(item['path']).parent==snapshot/'transport','TRANSPORT_COPY_PATH')
    init=tru03_file(transport['initScript']);tru03_require(init==c.init_bytes,'TRANSPORT_REVIEWED_INIT')
    tr=tru03_json_bytes(tru03_file(transport['manifest']))
    tru03_exact(tr,('schema','originalMain','originalArguments','argumentFile','classpath','sourceCount','repositoryConfigurationChanged','sourceSelectionChanged'),'TRANSPORT_MANIFEST_KEYS')
    tru03_require(tr['schema']=='DORA_DETEKT_EXACT_ARGUMENT_TRANSPORT_V1' and tr['originalMain']=='io.gitlab.arturbosch.detekt.cli.Main' and tr['repositoryConfigurationChanged'] is False and tr['sourceSelectionChanged'] is False,'TRANSPORT_SEMANTICS')
    args=tr['originalArguments'];tru03_require(type(args) is list and len(args)==5 and args[0]=='--input' and args[2:] == ['--config',str(root/'android/config/detekt/detekt.yml'),'--build-upon-default-config'],'TRANSPORT_ARGUMENTS')
    selected=args[1].split(',');tru03_require(len(selected)==len(set(selected)) and set(selected)=={str(root/p) for p in selection['detekt']} and type(tr['sourceCount']) is int and tr['sourceCount']==len(selected),'TRANSPORT_ACTUAL_SELECTION')
    tru03_require(tr['argumentFile']==str(c.raw_root/('tru03-detekt-'+build['phase']+'.arguments.txt')),'TRANSPORT_EXACT_ORIGINAL_ARGUMENT_PATH')
    quote=lambda value:'"'+value.replace('\\','\\\\').replace('"','\\"')+'"'
    expected=('\n'.join(quote(x) for x in [tr['originalMain']]+args)+'\n').encode()
    tru03_require(tru03_file(transport['argumentFile'])==expected==tru03_regular(tr['argumentFile']).read_bytes(),'TRANSPORT_ARGUMENT_FILE')
    tru03_require(type(tr['classpath']) is list and tr['classpath'] and len(tr['classpath'])==len(set(tr['classpath'])),'TRANSPORT_CLASSPATH')
    for path in tr['classpath']:tru03_regular(path)
    evidence=build['evidence'];tru03_exact(evidence,('gradle','host','jvmXml','lint','apks'),'EVIDENCE_KEYS')
    runs={};times={}
    phase=build['phase'];host_stem='tru03-host-'+phase;gradle_stem='tru03-'+phase
    for kind,stem in [('gradle',gradle_stem),('host',host_stem)]:
        record=evidence[kind];tru03_exact(record,('started','receipt','stdout','stderr'),'RUN_KEYS');data={}
        for key,suffix in [('started','.started.json'),('receipt','.receipt.json'),('stdout','.stdout.log'),('stderr','.stderr.log')]:
            tru03_require(record[key]['path']==str(snapshot/'raw'/(stem+suffix)),'RUN_COPY_STEM')
            data[key]=tru03_file(record[key]);tru03_require(data[key]==tru03_regular(str(c.raw_root/(stem+suffix))).read_bytes(),'RUN_ORIGINAL_COPY')
        started=tru03_json_bytes(data['started']);receipt=tru03_json_bytes(data['receipt'])
        if kind=='gradle':
            argv=[c.tools['cmd']['path'],'/d','/c','gradlew.bat','--no-daemon','--offline','--no-configuration-cache','--max-workers=2','--init-script']
            tru03_require(len(receipt['argv'])==17,'GRADLE_ARGV_LENGTH');original_init=receipt['argv'][9]
            tru03_require(original_init==str(c.raw_root/('tru03-detekt-'+phase+'.init.gradle')),'INIT_EXACT_ORIGINAL_PATH')
            tru03_require(tru03_regular(original_init).read_bytes()==init,'INIT_ORIGINAL_COPY')
            argv += [original_init]+list(TRU03_TASKS);cwd=str(root/'android')
        else:argv=[c.tools['python']['path'],'-X','utf8','-B','-m','unittest','-v']+list(TRU03_HOST_MODULES);cwd=str(root/'tools')
        times[kind]=tru03_native(started,receipt,argv,cwd);runs[kind]=(data,receipt)
    tru03_gradle_log(runs['gradle'][0]['stdout'].decode('utf-8-sig'),build['jvmEvidence'])
    host_counts=tru03_host_counts((runs['host'][0]['stdout']+runs['host'][0]['stderr']).decode('utf-8-sig'))
    tru03_require(build['hostCounts']==host_counts and all(type(v) is int for v in build['hostCounts'].values()),'HOST_COUNTS_EXACT')
    env_time=Path(build['environment']['path']).stat().st_mtime
    tru03_require(env_time<=min(t[0].timestamp() for t in times.values()),'ENVIRONMENT_NOT_PREEXECUTION')
    xmlroot=root/'android/poc/recovery/build/test-results/testDebugUnitTest';xmls=sorted(xmlroot.glob('TEST-*.xml'))
    tru03_require(xmls and [x['source']['path'] for x in evidence['jvmXml']]==[str(x) for x in xmls],'JVM_EXACT_REPORT_CENSUS')
    documents=[]
    for row,p in zip(evidence['jvmXml'],xmls):
        documents.append(tru03_pair(row,p,snapshot/'reports/jvm'/p.name))
        tru03_require(times['gradle'][0].timestamp()<=p.stat().st_mtime<=times['gradle'][1].timestamp(),'JVM_REPORT_NOT_FROM_RUN')
    jvm_counts=tru03_jvm_xml(documents)
    tru03_require(build['jvmCounts']==jvm_counts and all(type(v) is int for v in build['jvmCounts'].values()),'JVM_COUNTS_EXACT')
    lintroot=root/'android/poc/recovery/build/reports';lintfiles=sorted(lintroot.glob('lint-results-debug.*'))
    tru03_require(lintfiles and [x['source']['path'] for x in evidence['lint']]==[str(x) for x in lintfiles] and any(p.suffix=='.xml' for p in lintfiles),'LINT_EXACT_REPORT_CENSUS')
    for row,p in zip(evidence['lint'],lintfiles):
        raw=tru03_pair(row,p,snapshot/'reports/lint'/p.name)
        if p.suffix=='.xml':
            tru03_require(b'<!DOCTYPE' not in raw and b'<!ENTITY' not in raw,'LINT_ENTITY');xml=ET.fromstring(raw)
            tru03_require(xml.tag=='issues' and all(x.get('severity','').lower() not in ('error','fatal') for x in xml.findall('issue')),'LINT_ERRORS')
    tru03_exact(evidence['apks'],TRU03_APK_PATHS,'APK_KEYS');apk_pair={};apks={}
    for key,relative in TRU03_APK_PATHS.items():
        p=root/relative;raw=tru03_pair(evidence['apks'][key],p,snapshot/'apks'/p.name)
        tru03_require(raw,'APK_EMPTY');apk_pair[key]=hashlib.sha256(raw).hexdigest();apks[key]={k:v for k,v in evidence['apks'][key]['copy'].items() if k!='bytes'}
    tru03_require(build['apkPair']==apk_pair,'APK_PAIR_EXACT')
    c.native_check()
    return dict(sourceDelta=delta,jvmCounts=jvm_counts,jvmEvidence=build['jvmEvidence'],hostCounts=host_counts,apkPair=apk_pair,apks=apks,hostReceipt={k:v for k,v in evidence['host']['receipt'].items() if k!='bytes'},gradleReceipt={k:v for k,v in evidence['gradle']['receipt'].items() if k!='bytes'},newJvmMethodExecutions=jvm_counts['tests'],hostInvocationCount=host_counts['methods'],hostUniqueSemanticMethods=None)




TRU03_INIT_TEMPLATE_SHA='8e672f6930595c31fa1760e07198a26554e09635646fa91bd174203ea388fdb4'
TRU03_INIT_OLD_ROOT='C:/Users/vinzer/Documents/DORA-Android-Test/.worktrees/reduced114-stream-path-repair/android'
TRU03_INIT_OLD_RAW='C:/Users/vinzer/Documents/DORA-Android-Test/0D6-E36-CAMPAIGN-20260914/raw'
# Reviewed machine/control constants. No policy document is executed or trusted at runtime.
TRU03_FIXED_MACHINE_POLICY = {'controlReviewSha256': '63cc93b49a22a92d6bb7c9fd9169f93409dffefbac9771b6fbca72f8320a5241',
 'environment': {'androidHome': 'C:\\Users\\vinzer\\AppData\\Local\\Android\\Sdk',
                 'gradleUserHome': 'C:\\Users\\vinzer\\.gradle',
                 'javaHome': 'C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.20.101-hotspot',
                 'pythonHomeOverride': None,
                 'pythonPath': 'C:\\Users\\vinzer\\.cache\\codex-runtimes\\codex-primary-runtime\\dependencies\\python\\python.exe',
                 'pythonPathOverride': None,
                 'schema': 'DORA_MICROFILE_TRU03_BUILD_ENVIRONMENT_V1',
                 'sourceRoot': 'C:\\Users\\vinzer\\Documents\\DORA-Android-Test\\.worktrees\\reduced114-micro-tru03',
                 'userGradleProperties': None,
                 'userInitScripts': []},
 'exactThreeOverrides': {'ANDROID_HOME': 'C:\\Users\\vinzer\\AppData\\Local\\Android\\Sdk',
                         'JAVA_HOME': 'C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.20.101-hotspot',
                         'PYTHONUTF8': '1'},
 'generator': {'bytes': 12405,
               'path': 'C:\\Users\\vinzer\\Documents\\DORA-Android-Test\\m605-host\\held04\\tru03-build-freeze-candidate-01\\private_freezers_tru03_03.py',
               'sha256': 'c541a0499b57fce199fa353c16cdf05ec6d6c8a755b339b621c4060b072be3c7'},
 'identity': {'Home': None, 'Identity': 'ASUS-TUF-F15\\vinzer', 'UserProfile': 'C:\\Users\\vinzer'},
 'independentReviewSha256': '8db1587e04521cb4d1ee5767a775272d8c1f398745cacf093d9604a1249d3d8f',
 'java': {'javaHome': 'C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.20.101-hotspot',
          'userHome': 'C:\\Users\\vinzer',
          'version': '17.0.20.1'},
 'jdkRelease': {'bytes': 1674,
                'path': 'C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.20.101-hotspot\\release',
                'sha256': '171abb6be50980aeed01365f3032cb7a1bc6126ca48a5eed5ba87ac3e000f7a5'},
 'planned': {'allPathsAbsent': True,
             'created': False,
             'phase': 'verify-05',
             'rawRoot': 'C:\\Users\\vinzer\\Documents\\DORA-Android-Test\\0D6-E36-CAMPAIGN-20260914\\reduced-114\\tru03-source-iteration-20260917-03\\raw\\verify-05',
             'snapshotRoot': 'C:\\Users\\vinzer\\Documents\\DORA-Android-Test\\m605-host\\held04\\tru03-b05',
             'sourceRoot': 'C:\\Users\\vinzer\\Documents\\DORA-Android-Test\\.worktrees\\reduced114-micro-tru03'},
 'renderedInitSha256': 'a1e5083249f682f00d556d5469fd2672b4d367094f28a1a8010c2a3003b717f4',
 'template': {'bytes': 2235,
              'path': 'C:\\Users\\vinzer\\Documents\\DORA-Android-Test\\0D6-E36-CAMPAIGN-20260914\\reduced-114\\resume-20260916-main-01\\schema6-detekt-verify-02.init.gradle',
              'sha256': '8e672f6930595c31fa1760e07198a26554e09635646fa91bd174203ea388fdb4'},
 'tools': {'androidSdkRoot': 'C:\\Users\\vinzer\\AppData\\Local\\Android\\Sdk',
           'cmd': {'bytes': 344064,
                   'path': 'C:\\Windows\\System32\\cmd.exe',
                   'sha256': '97ac98b1a92c286054cce55239cfccdfc23a5517bd07fe693072c9ca96c7dabb'},
           'java': {'bytes': 50296,
                    'path': 'C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.20.101-hotspot\\bin\\java.exe',
                    'sha256': '1977f302375adbb920d41dac65c7e22eb9c2ed8e1e8d6258964154ff16f14406'},
           'javaHome': 'C:\\Program Files\\Eclipse Adoptium\\jdk-17.0.20.101-hotspot',
           'python': {'bytes': 107312,
                      'path': 'C:\\Users\\vinzer\\.cache\\codex-runtimes\\codex-primary-runtime\\dependencies\\python\\python.exe',
                      'sha256': '1a03cb7cb09e29053ae71a0d0e28555fe0ff3d22bb3a476eb9cc0d3899e73456'},
           'recorder': {'bytes': 1270,
                        'path': 'C:\\Users\\vinzer\\Documents\\DORA-Android-Test\\0D6-E36-CAMPAIGN-20260914\\run_recorded.py',
                        'sha256': 'c1c212f3f1a3e3fd9b289db0e804b7912867dd3d859bda550ffae98f3add7184'},
           'runner': {'bytes': 5717,
                      'path': 'C:\\Users\\vinzer\\Documents\\DORA-Android-Test\\m605-host\\held04\\tru03-build-freeze-candidate-01\\build_runner_tru03_03.py',
                      'sha256': '66fc860efbb7be81a7987e0227a74460f9c4ed82f13d4e5ab12da830f0b97986'}},
 'userConfiguration': {'effectiveScripts': [],
                       'initGradleKtsPresent': False,
                       'initGradlePresent': False,
                       'initializerDirectories': [{'entries': [],
                                                   'exists': False,
                                                   'path': 'C:\\Users\\vinzer\\.gradle\\init.d'},
                                                  {'entries': [{'file': {'bytes': 119,
                                                                         'path': 'C:\\Users\\vinzer\\.gradle\\wrapper\\dists\\gradle-9.5.0-bin\\bvnork1r7n8i6kp5cnkibsc9q\\gradle-9.5.0\\init.d\\readme.txt',
                                                                         'sha256': '16cf9450804c97d225bac3e2512583b628a139179fe9c6151d1a23166b66cd23'},
                                                                'kind': 'file',
                                                                'name': 'readme.txt'}],
                                                   'exists': True,
                                                   'path': 'C:\\Users\\vinzer\\.gradle\\wrapper\\dists\\gradle-9.5.0-bin\\bvnork1r7n8i6kp5cnkibsc9q\\gradle-9.5.0\\init.d'}],
                       'userGradleProperties': None},
 'wrapper': {'cacheHash': 'bvnork1r7n8i6kp5cnkibsc9q',
             'cacheHashAlgorithm': 'positive BigInteger(MD5(safe distribution URI ASCII string encoded '
                                   'UTF-8)).toString(36)',
             'cacheInventory': {'entries': [{'file': None, 'kind': 'directory', 'name': 'gradle-9.5.0'},
                                            {'file': {'bytes': 0,
                                                      'path': 'C:\\Users\\vinzer\\.gradle\\wrapper\\dists\\gradle-9.5.0-bin\\bvnork1r7n8i6kp5cnkibsc9q\\gradle-9.5.0-bin.zip.lck',
                                                      'sha256': 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'},
                                             'kind': 'file',
                                             'name': 'gradle-9.5.0-bin.zip.lck'},
                                            {'file': {'bytes': 0,
                                                      'path': 'C:\\Users\\vinzer\\.gradle\\wrapper\\dists\\gradle-9.5.0-bin\\bvnork1r7n8i6kp5cnkibsc9q\\gradle-9.5.0-bin.zip.ok',
                                                      'sha256': 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'},
                                             'kind': 'file',
                                             'name': 'gradle-9.5.0-bin.zip.ok'}],
                                'exists': True,
                                'path': 'C:\\Users\\vinzer\\.gradle\\wrapper\\dists\\gradle-9.5.0-bin\\bvnork1r7n8i6kp5cnkibsc9q'},
             'distributionSha256Sum': '553c78f50dafcd54d65b9a444649057857469edf836431389695608536d6b746',
             'distributionUrl': 'https://services.gradle.org/distributions/gradle-9.5.0-bin.zip',
             'exactSingleDirectory': True,
             'exactSingleLauncher': True,
             'files': {'gradle/wrapper/gradle-wrapper.jar': {'bytes': 48462,
                                                             'path': 'C:\\Users\\vinzer\\Documents\\DORA-Android-Test\\.worktrees\\reduced114-stream-path-repair\\android\\gradle\\wrapper\\gradle-wrapper.jar',
                                                             'sha256': '497c8c2a7e5031f6aa847f88104aa80a93532ec32ee17bdb8d1d2f67a194a9c7'},
                       'gradle/wrapper/gradle-wrapper.properties': {'bytes': 370,
                                                                    'path': 'C:\\Users\\vinzer\\Documents\\DORA-Android-Test\\.worktrees\\reduced114-stream-path-repair\\android\\gradle\\wrapper\\gradle-wrapper.properties',
                                                                    'sha256': '5d6b7e5139b01fed9bed54b04c55e5910115897d370eb451b5f597c9ad0b51cd'},
                       'gradlew': {'bytes': 8654,
                                   'path': 'C:\\Users\\vinzer\\Documents\\DORA-Android-Test\\.worktrees\\reduced114-stream-path-repair\\android\\gradlew',
                                   'sha256': 'ab5c0cad16305af2e619c159c1f58dd68d07fab9c11e36701e109c0277407f7a'},
                       'gradlew.bat': {'bytes': 2846,
                                       'path': 'C:\\Users\\vinzer\\Documents\\DORA-Android-Test\\.worktrees\\reduced114-stream-path-repair\\android\\gradlew.bat',
                                       'sha256': '475c4f08cd57cf2faa819e7f36d72aa93f0ad646ea23a8f7fa3ef54dee1cbc52'}},
             'fullExpandedDistributionAuthenticated': False,
             'inventoryScope': 'exact selected cached root, wrapper launch/configuration and initializer '
                               'policy; not full expanded-cache content',
             'launcher': {'bytes': 347634,
                          'path': 'C:\\Users\\vinzer\\.gradle\\wrapper\\dists\\gradle-9.5.0-bin\\bvnork1r7n8i6kp5cnkibsc9q\\gradle-9.5.0\\lib\\gradle-launcher-9.5.0.jar',
                          'sha256': 'bf27d49e9a613436ecf1df1c8c926e3f804023ad00b66933b57340067443b2ed'},
             'okMarker': {'bytes': 0,
                          'path': 'C:\\Users\\vinzer\\.gradle\\wrapper\\dists\\gradle-9.5.0-bin\\bvnork1r7n8i6kp5cnkibsc9q\\gradle-9.5.0-bin.zip.ok',
                          'sha256': 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855'},
             'projectGradleJavaHomePresent': False,
             'projectJvmArgsHomeOverridePresent': False,
             'projectProperties': {'bytes': 276,
                                   'path': 'C:\\Users\\vinzer\\Documents\\DORA-Android-Test\\.worktrees\\reduced114-stream-path-repair\\android\\gradle.properties',
                                   'sha256': '326161064800d2647a4518ba6f4af06875a6cb164745c205202da738dc4aa260'},
             'projectSystemPropertyKeys': [],
             'retainedZip': None,
             'selectedCacheRoot': 'C:\\Users\\vinzer\\.gradle\\wrapper\\dists\\gradle-9.5.0-bin\\bvnork1r7n8i6kp5cnkibsc9q',
             'selectedDistribution': 'C:\\Users\\vinzer\\.gradle\\wrapper\\dists\\gradle-9.5.0-bin\\bvnork1r7n8i6kp5cnkibsc9q\\gradle-9.5.0',
             'selectedUserHome': 'C:\\Users\\vinzer\\.gradle',
             'selectionBasis': 'normal Java user.home; no CLI -g/-D, no project systemProp override, no '
                               'environment override',
             'zipChecksumRevalidated': False}}

def _tru03_render_init(template, source_root, raw_root, phase):
    require(hashlib.sha256(template).hexdigest()==TRU03_INIT_TEMPLATE_SHA,'INIT_TEMPLATE_PIN')
    require(re.fullmatch('verify-[0-9]{2}',phase) and Path(source_root).is_absolute() and Path(raw_root).is_absolute(),'INIT_PATH_PHASE')
    paths=[(Path(source_root)/'android').as_posix(),Path(raw_root).as_posix()]
    require(all(not any(x in p for x in ("'",'\n','\r','\\')) for p in paths),'INIT_PATH_QUOTING')
    text=template.decode('utf-8')
    for before,after in [(TRU03_INIT_OLD_ROOT,paths[0]),(TRU03_INIT_OLD_RAW,paths[1]),('schema6-detekt-verify-02','tru03-detekt-'+phase)]:
        require(before in text,'INIT_TEMPLATE_SUBSTITUTION');text=text.replace(before,after)
    newline='\r\n' if '\r\n' in text else '\n'
    # Existing Detekt transport remains byte-equivalent modulo three literal
    # substitutions. Only the explicitly reviewed recovery JVM task is forced.
    block='''gradle.projectsEvaluated {
    def expectedRoot = new File('%s').canonicalFile
    if (gradle.rootProject.projectDir.canonicalFile != expectedRoot) return
    def recoveryJvm = gradle.rootProject.project(':poc:recovery').tasks.named('testDebugUnitTest').get()
    recoveryJvm.outputs.upToDateWhen { false }
    recoveryJvm.outputs.doNotCacheIf("TRU03 current JVM execution") { true }
}
''' % paths[0]
    return (text+newline+block.replace('\n',newline)).encode('utf-8')


def _tru03_policy_directory(path):
    path=Path(path)
    require(path.is_absolute() and path.resolve()==path and '..' not in path.parts,'TRU03_POLICY_DIRECTORY')
    for item in (path,*path.parents):
        info=item.lstat()
        require(stat.S_ISDIR(info.st_mode) and not stat.S_ISLNK(info.st_mode)
            and not getattr(info,'st_file_attributes',0)&stat.FILE_ATTRIBUTE_REPARSE_POINT,'TRU03_POLICY_REPARSE_DIRECTORY')
    return path


def _tru03_policy_inventory(path):
    path=Path(path)
    if not path.exists():
        require(not path.is_symlink(),'TRU03_POLICY_REPARSE_DIRECTORY')
        _tru03_policy_directory(path.parent)
        return dict(path=str(path),exists=False,entries=[])
    _tru03_policy_directory(path);entries=[]
    for child in sorted(path.iterdir(),key=lambda p:p.name):
        info=child.lstat()
        require(not stat.S_ISLNK(info.st_mode) and not getattr(info,'st_file_attributes',0)&stat.FILE_ATTRIBUTE_REPARSE_POINT,'TRU03_POLICY_REPARSE_DIRECTORY')
        if child.is_dir():
            _tru03_policy_directory(child);kind='directory';descriptor=None
        else:
            kind='file';descriptor=tru03_desc(_tru03_regular(child))
        entries.append(dict(name=child.name,kind=kind,file=descriptor))
    return dict(path=str(path),exists=True,entries=entries)


def _tru03_normal_identity():
    # Native token identity, not USERNAME/USERDOMAIN supplied by a document.
    import ctypes
    require(__import__('os').name=='nt','TRU03_NORMAL_WINDOWS_IDENTITY')
    size=ctypes.c_ulong(512);buffer=ctypes.create_unicode_buffer(size.value)
    require(bool(ctypes.windll.secur32.GetUserNameExW(2,buffer,ctypes.byref(size))),'TRU03_NORMAL_WINDOWS_IDENTITY')
    return buffer.value


def _tru03_java_properties(java,root,environment):
    import subprocess
    result=subprocess.run([java,'-XshowSettings:properties','-version'],cwd=root,env=environment,
        capture_output=True,check=False,creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
    require(result.returncode==0,'TRU03_JAVA_POLICY_PROBE')
    result_text=(result.stdout+result.stderr).decode('utf-8',errors='strict');values={}
    for key in ('user.home','java.home','java.version'):
        matches=re.findall(r'^\s*'+re.escape(key)+r' = (.+?)\s*$',result_text,re.MULTILINE)
        require(len(matches)==1,'TRU03_JAVA_POLICY_PROPERTIES');values[key]=matches[0]
    return values


def _tru03_fixed_build_policy():
    """Fresh native/configuration checks against code constants, never B policy.

    The selected wrapper cache is launch/configuration policy, not an assertion
    that every expanded distribution byte was authenticated. No wrapper runs.
    """
    import os
    p=json.loads(json.dumps(TRU03_FIXED_MACHINE_POLICY));root=Path(p['planned']['sourceRoot'])
    require(ROOT==root and root.resolve()==root,'TRU03_FIXED_SOURCE_ROOT')
    _tru03_policy_directory(root)
    require(_tru03_normal_identity()==p['identity']['Identity'],'TRU03_NORMAL_WINDOWS_IDENTITY')
    require(os.environ.get('USERPROFILE')==p['identity']['UserProfile'],'TRU03_USER_PROFILE')
    for key in ('HOME','GRADLE_USER_HOME','GRADLE_HOME','PYTHONPATH','PYTHONHOME','JAVA_OPTS','GRADLE_OPTS','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS'):
        require(os.environ.get(key) is None,'TRU03_UNREVIEWED_ENVIRONMENT:'+key)
    for key in ('python','cmd','java','recorder','runner'):tru03_file(p['tools'][key])
    for key in ('generator','template','jdkRelease'):tru03_file(p[key])
    environment=dict(os.environ);environment.update(p['exactThreeOverrides'])
    java=_tru03_java_properties(p['tools']['java']['path'],root,environment)
    require(java=={'user.home':p['java']['userHome'],'java.home':p['java']['javaHome'],'java.version':p['java']['version']},'TRU03_JAVA_POLICY_PROPERTIES')
    wrapper=p['wrapper']
    # Exact unchanged wrapper/project inputs fix URL and precedence; selected
    # cached path was derived independently from Java home + MD5/base36 URI.
    for relative,descriptor in wrapper['files'].items():
        observed=dict(descriptor,path=str(root/'android'/relative));tru03_file(observed)
    tru03_file(dict(wrapper['projectProperties'],path=str(root/'android/gradle.properties')))
    require(not (root/'android/local.properties').exists() and not (root/'android/local.properties').is_symlink(),'TRU03_UNREVIEWED_LOCAL_PROPERTIES')
    require(_tru03_policy_inventory(wrapper['selectedCacheRoot'])==wrapper['cacheInventory'],'TRU03_WRAPPER_CACHE_SELECTION')
    distribution=_tru03_policy_directory(wrapper['selectedDistribution'])
    launchers=sorted(distribution.joinpath('lib').glob('gradle-launcher-*.jar'))
    require([str(x) for x in launchers]==[wrapper['launcher']['path']],'TRU03_WRAPPER_SINGLE_LAUNCHER')
    tru03_file(wrapper['launcher']);tru03_file(wrapper['okMarker'])
    home=_tru03_policy_directory(wrapper['selectedUserHome'])
    for name in ('gradle.properties','init.gradle','init.gradle.kts'):
        path=home/name;require(not path.exists() and not path.is_symlink(),'TRU03_UNREVIEWED_USER_CONFIGURATION')
    for inventory in p['userConfiguration']['initializerDirectories']:
        require(_tru03_policy_inventory(inventory['path'])==inventory,'TRU03_INITIALIZER_INVENTORY')
    template=tru03_file(p['template'])
    init_bytes=_tru03_render_init(template,root,Path(p['planned']['rawRoot']),p['planned']['phase'])
    require(hashlib.sha256(init_bytes).hexdigest()==p['renderedInitSha256'],'TRU03_RENDERED_INIT_PIN')
    return dict(sourceRoot=str(root),snapshotRoot=p['planned']['snapshotRoot'],rawRoot=p['planned']['rawRoot'],
        phase=p['planned']['phase'],tools=p['tools'],environment=p['environment'],generator=p['generator'],
        initBytes=init_bytes,wrapperDistribution=wrapper['selectedDistribution'])


def _tru03_build_context(build_descriptor,implementation,before,after,authorization):
    policy=_tru03_fixed_build_policy();root=Path(policy['sourceRoot'])
    snapshot=Path(policy['snapshotRoot']);raw=Path(policy['rawRoot'])
    require(type(build_descriptor) is dict and set(build_descriptor)=={'path','sha256'}
        and build_descriptor['path']==str(snapshot/'MANIFEST.json'),'TRU03_FIXED_BUILD_DESCRIPTOR')
    document=_tru03_json(build_descriptor)
    require(document.get('phase')==policy['phase'],'TRU03_FIXED_BUILD_PHASE')
    identity=_tru03_source_identity(root)
    native_before,native_after,_=_tru03_implementation(implementation,root)
    require(before==native_before and after==native_after,'TRU03_CONTEXT_NATIVE_TREES')
    metadata_current={}
    if identity==implementation:
        # Administrative construction at clean I: unchanged old metadata data;
        # no candidate_api, no bootstrap requiring M, no temporary monkeypatch.
        pass
    else:
        metadata=bootstrap_current_metadata()
        require(metadata['route']=='TRU03' and metadata['source']==identity
            and metadata['implementation']==implementation,'TRU03_CONTEXT_METADATA_CHILD')
        for relative in sorted(TRU03_METADATA_PATHS):
            metadata_current[relative]=tru03_desc(_tru03_regular(root/relative))
    def native_check():
        require(_tru03_fixed_build_policy()==policy,'TRU03_BUILD_POLICY_CHANGED')
        require(_tru03_source_identity(root)==identity,'TRU03_BUILD_SOURCE_CHANGED')
        b,a,_=_tru03_implementation(implementation,root)
        require(b==before and a==after,'TRU03_CONTEXT_NATIVE_TREES')
        if metadata_current:
            metadata=bootstrap_current_metadata()
            require(metadata['source']==identity and metadata['implementation']==implementation,'TRU03_CONTEXT_METADATA_CHILD')
            for relative,descriptor in metadata_current.items():
                require(tru03_desc(root/relative)==descriptor,'TRU03_CONTEXT_METADATA_CHANGED')
        require(_tru03_json(build_descriptor)==document,'TRU03_BUILD_DOCUMENT_CHANGED')
    def blob_for_copy(relative,path):
        require(type(relative) is str and relative in after,'TRU03_COPY_NATIVE_PATH')
        subdir='source' if relative in TRU03_IMPLEMENTATION_PATHS else 'inputs'
        require(Path(path)==snapshot/subdir/relative,'TRU03_COPY_FIXED_PATH')
        path=_tru03_regular(path)
        result=_tru03_git('hash-object','--path='+relative,str(path),root=root)
        require(re.fullmatch('[0-9a-f]{40}',result),'TRU03_COPY_NATIVE_BLOB')
        return result
    result=Tru03BuildContext(root=root,snapshot_root=snapshot,raw_root=raw,authorization=authorization,
        generator=policy['generator'],baseline_entries=before,target_entries=after,blob_for_copy=blob_for_copy,
        native_check=native_check,tools=policy['tools'],environment=policy['environment'],init_bytes=policy['initBytes'],
        metadata_current=metadata_current)
    result.wrapper_distribution=Path(policy['wrapperDistribution']) if 'wrapperDistribution' in policy else None
    native_check()
    return result



def microfile_tru03_facts(api,profile,prefix_binding,predecessor,build,apks,authorization,review):
    """Construct P from I and components before M; no metadata mutation/import.

    api remains an interface-compatibility argument, never bootstrap authority.
    Runtime proof/binding equality belongs in validate_microfile_tru03_proof.
    """
    implementation=dict(commit=profile.implementation_commit,tree=profile.implementation_tree)
    before,after,delta=_tru03_implementation(implementation,ROOT)
    identity=_tru03_source_identity(ROOT)
    if identity['commit']==implementation['commit']:
        require(identity['tree']==implementation['tree'],'TRU03_IMPLEMENTATION_CHECKOUT')
    else:
        context=bootstrap_current_metadata()
        require(context['route']=='TRU03' and context['implementation']==implementation,'TRU03_FACTS_CONTEXT')
    historical=validate_tru03_predecessor(predecessor)
    # The implementation's metadata is unchanged baseline data, parsed inertly.
    baseline_text=_tru03_git('show',TRU03_BASELINE_COMMIT+':tools/validate_recovery_0d6_candidate.py',root=ROOT)
    all_names=set(historical['inheritedBindings'])
    _,inherited=_tru03_metadata_body(baseline_text,all_names)
    require(inherited==historical['inheritedBindings'],'TRU03_INHERITED_BINDINGS')
    validate_tru03_authorization(authorization,historical)
    pair_keys={'appApkSha256','testApkSha256'}
    require(isinstance(prefix_binding,dict) and set(prefix_binding) in (pair_keys,pair_keys|{'applicabilitySha256'})
        and isinstance(apks,dict) and set(apks)==pair_keys,'TRU03_APK_SHAPE')
    for descriptor in apks.values():capture_file(descriptor)
    pair={k:apks[k]['sha256'] for k in pair_keys}
    require(pair=={k:prefix_binding[k] for k in pair_keys},'TRU03_APK_PAIR')
    document=_tru03_json(build)
    build_context=_tru03_build_context(build,implementation,before,after,authorization)
    observed=validate_microfile_tru03_build(document,build_context)
    require(observed['sourceDelta']==delta and observed['apkPair']==pair and observed['apks']==apks,'TRU03_BUILD_COMPONENTS')
    technical=_tru03_json(review)
    expected_review=dict(schema='DORA_MICROFILE_TRU03_TECHNICAL_REVIEW_V1',
        baseline=dict(commit=TRU03_BASELINE_COMMIT,tree=TRU03_BASELINE_TREE),implementation=implementation,
        predecessorApplicability=predecessor['applicability'],authorization=authorization,build=build,
        sourceDelta=delta,apkPair=pair,apks=apks,hostReceipt=observed['hostReceipt'],gradleReceipt=observed['gradleReceipt'],
        jvmCounts=observed['jvmCounts'],jvmEvidence=observed['jvmEvidence'],hostCounts=observed['hostCounts'],
        independentTechnicalReview=True,testedCopiesMatchImplementation=True,historicalProofRevalidated=True,
        originalProductFailuresPreserved=True,runtimeAdmissionGranted=False,historicalCoverageAutomaticallyGranted=False)
    require(_tru03_document_equal(technical,expected_review),'TRU03_TECHNICAL_REVIEW')
    methods=[]
    for path,method in sorted(legacy_api()['PREFLIGHT_METHODS'].items()):
        require(after.get(path,{}).get('mode')=='100644' and after[path]['type']=='blob','TRU03_PREFLIGHT_METHOD')
        methods.append(dict(path=path,method=method,source=after[path]))
    require(len(methods)==3,'TRU03_PREFLIGHT_METHODS')
    for descriptor in historical['controls'].values():capture_file(descriptor)
    require(_tru03_source_identity(ROOT)==identity,'TRU03_FACTS_SOURCE_CHANGED')
    expected=dict(schema=TRU03_SCHEMA,scope=SCOPE,
        contract='MICROFILE_TRU03_AUTHENTICATED_RETAINED_APPEND_SHARED_SCHEMA_6',
        baseline=dict(commit=TRU03_BASELINE_COMMIT,tree=TRU03_BASELINE_TREE),implementation=implementation,
        sourceDelta=delta,predecessor=predecessor,build=build,apks=apks,apkPair=pair,
        authorization=authorization,independentReview=review,controls=historical['controls'],
        frozenSelection=historical['proof']['frozenSelection'],requiredFreshPreflight=methods,
        historicalPreflightReusable=False,historicalCoverageAutomaticallyGranted=False,runtimeAdmissionGranted=False)
    return expected,historical['frozen'],historical['controls']


def validate_microfile_tru03_proof(api,profile,binding,proof,descriptor,review):
    value=microfile_tru03_binding(api)
    require(value is not None and isinstance(descriptor,dict)
        and descriptor.get('sha256')==value['proofSha256']==binding['applicabilitySha256'],'TRU03_PROOF_HASH')
    actual=_tru03_json(descriptor)
    require(actual==proof and proof.get('schema')==TRU03_SCHEMA
        and proof.get('independentReview')==review,'TRU03_PROOF_SCHEMA_OR_REVIEW')
    expected,frozen,controls=microfile_tru03_facts(api,profile,binding,proof.get('predecessor'),
        proof.get('build'),proof.get('apks'),proof.get('authorization'),review)
    validate_tru03_metadata_shape(api,profile)
    require(_tru03_document_equal(proof,expected),'TRU03_EXACT_PROOF')
    return frozen,controls


def _tru03_route_hint(descriptor):
    if descriptor is None:return None
    proof=read_proof(descriptor)
    return _tru03_json(descriptor) if isinstance(proof,dict) and proof.get('schema')==TRU03_SCHEMA else proof


def legacy_api():
    return runpy.run_path(str(ROOT/'tools/recovery_alpha_repair.py'))


def candidate_api(proof=None):
    context=bootstrap_current_metadata(proof)
    require(context['route']!='TRU03' or isinstance(proof,dict) and proof.get('schema')==TRU03_SCHEMA,
        'TRU03_SOURCE_REPAIR_REQUIRED')
    metadata=ROOT/'tools/validate_recovery_0d6_candidate.py'
    stream_path_metadata_literal(metadata.read_text(encoding='utf-8'))
    collector_query_metadata_literal(metadata.read_text(encoding='utf-8'))
    git_query_metadata_literal(metadata.read_text(encoding='utf-8'))
    microfile_disposition_metadata_literal(metadata.read_text(encoding='utf-8'))
    microfile_tru03_metadata_literal(metadata.read_text(encoding='utf-8'))
    require(file_sha(metadata)==context['metadata']['sha256'],'TRU03_SOURCE_CHANGED')
    result=runpy.run_path(str(metadata))
    require(file_sha(metadata)==context['metadata']['sha256'],'TRU03_SOURCE_CHANGED')
    return result


def applicability_facts(api, profile, binding):
    git = api['git']
    require(git('rev-parse', BASELINE_COMMIT+'^{tree}', root=ROOT) == BASELINE_TREE, 'Prefix baseline tree drift')
    require(git('merge-base', BASELINE_COMMIT, profile.implementation_commit, root=ROOT) == BASELINE_COMMIT,
            'Prefix implementation is not a baseline descendant')
    legacy = legacy_api()
    before = legacy['tree_entries'](git, BASELINE_COMMIT)
    after = legacy['tree_entries'](git, profile.implementation_commit)
    paths = sorted(p for p in before.keys() | after.keys() if before.get(p) != after.get(p))
    require({p for p in paths if p.startswith('android/')} == ANDROID_REPAIR_PATHS,
            'Prefix Android delta differs from the exact reviewed files')
    require(DECISION_PATH in paths and set(paths) <= ANDROID_REPAIR_PATHS | HOST_REPAIR_PATHS | {DECISION_PATH},
            'Prefix repair changes unrelated repository/build/dependency inputs or lacks its decision')
    for p in paths:
        require(after.get(p, {}).get('mode') == '100644' and after[p]['type'] == 'blob'
                and (p not in before or before[p]['mode'] == '100644' and before[p]['type'] == 'blob'),
                'Prefix repair deletes or changes nonregular source: '+p)
    methods = []
    for p, method in sorted(legacy['PREFLIGHT_METHODS'].items()):
        require(after.get(p, {}).get('mode') == '100644' and after[p]['type'] == 'blob', 'Preflight method source missing')
        methods.append(dict(path=p, method=method, source=after[p]))
    return dict(schema='DORA_RECOVERY_PREFIX_REPAIR_APPLICABILITY_V1', scope=SCOPE,
        baseline=dict(commit=BASELINE_COMMIT, tree=BASELINE_TREE),
        implementation=dict(commit=profile.implementation_commit, tree=profile.implementation_tree),
        apkPair={k:binding[k] for k in ('appApkSha256','testApkSha256')},
        sourceDelta=[dict(path=p, before=before.get(p), after=after[p]) for p in paths],
        historicalPreflightReusable=False, requiredFreshPreflight=methods,
        streamDecision=dict(path=DECISION_PATH, source=after[DECISION_PATH]),
        microfileDispositionDecisionIncluded=False)


def validate_source(plan, gate, decision_key):
    hint=_tru03_route_hint(gate.get(decision_key,{}).get('sourceRepair'))
    api = candidate_api(hint)
    capture_binding(api)
    path_binding=stream_path_binding(api)
    query_binding=collector_query_binding(api)
    binding = api.get('ALPHA_PREFIX_REPAIR_BINDING')
    require(isinstance(binding, dict) and set(binding) == {'appApkSha256','testApkSha256','applicabilitySha256'}
            and all(isinstance(x,str) and re.fullmatch('[0-9a-f]{64}',x) for x in binding.values()),
            'Exact prefix repair pins are not installed')
    profile = api['active_profile']()
    require(profile is not None, 'Missing prefix repair source profile')
    api['validate'](profile, root=ROOT)
    source = dict(commit=api['git']('rev-parse','HEAD',root=ROOT),tree=api['git']('rev-parse','HEAD^{tree}',root=ROOT),
                  appApkSha256=binding['appApkSha256'],testApkSha256=binding['testApkSha256'])
    decision = gate.get(decision_key, {})
    require(plan.get('source') == gate.get('source') == decision.get('source') == source,
            'Prefix repair source/APKs differ from actual accepted profile')
    proof_descriptor = decision.get('sourceRepair')
    require(isinstance(proof_descriptor,dict) and proof_descriptor.get('sha256') == binding['applicabilitySha256'],
            'Prefix applicability pin mismatch')
    proof = read_proof(proof_descriptor)
    require(isinstance(proof,dict), 'Malformed prefix applicability')
    if tru03_route_required(api,proof,profile):
        frozen,_=validate_microfile_tru03_proof(api,profile,binding,proof,proof_descriptor,
            gate.get('proofs',{}).get('independentReview'))
        return source,frozen
    if microfile_disposition_binding(api) is not None:
        frozen,_=validate_microfile_disposition_proof(api,profile,binding,proof,proof_descriptor,
                                                   gate.get('proofs',{}).get('independentReview'))
        return source,frozen
    if git_query_binding(api) is not None:
        frozen,_=validate_git_query_proof(api,profile,binding,proof,proof_descriptor,
                                        gate.get('proofs',{}).get('independentReview'))
        return source,frozen
    if query_binding is not None:
        frozen,_=validate_collector_query_proof(api,profile,binding,proof,proof_descriptor,
                                              gate.get('proofs',{}).get('independentReview'))
        return source,frozen
    if path_binding is not None:
        frozen,_=validate_stream_path_proof(api,profile,binding,proof,proof_descriptor,
                                            gate.get('proofs',{}).get('independentReview'))
        return source,frozen
    expected = applicability_facts(api,profile,binding)
    capture = proof.get('captureRepair')
    validate_capture_repair(api,profile,binding,capture)
    expected['captureRepair'] = capture
    legacy = legacy_api()
    frozen_descriptor = proof.get('frozenSelection')
    frozen = json.loads(legacy['read_proof'](frozen_descriptor,legacy['FROZEN_SELECTION_SHA256']))
    review = gate.get('proofs',{}).get('independentReview')
    legacy['read_proof'](review)
    expected.update(frozenSelection=frozen_descriptor, independentReview=review)
    require(proof == expected, 'Prefix applicability differs from recomputed source facts')
    return source, frozen


def validate_preflight(plan, gate):
    require('alphaReduced' not in gate and 'alphaCampaign' not in gate
            and gate.get('environment') == 'E36-GAPI' and gate.get('supportedAttemptIds') == []
            and gate.get('physicalAuthorization',{}).get('authorized') is False,
            'Prefix preflight scope mismatch')
    validate_source(plan,gate,'alphaPreflight')


def validate(plan, gate):
    decision = gate.get('alphaReduced',{})
    require(isinstance(decision,dict) and decision.get('scope') == SCOPE
            and 'alphaPreflight' not in gate and 'alphaCampaign' not in gate
            and 'sourceEquivalence' not in decision and gate.get('supportedPayloads') == ['CAMPAIGN']
            and gate.get('environment') == 'E36-GAPI'
            and gate.get('physicalAuthorization',{}).get('authorized') is False,
            'Prefix applicability is restricted to reduced E36 campaign scope')
    source,frozen = validate_source(plan,gate,'alphaReduced')
    selection = gate.get('reducedSelection')
    legacy = legacy_api()
    require(isinstance(selection,dict) and legacy['selection_identity'](selection) == legacy['selection_identity'](frozen),
            'Prefix repair selection changes original 114 slots/variants/preference')
    require(selection.get('executionId') and selection['executionId'] != frozen.get('executionId'),
            'Prefix repair requires a fresh execution namespace')
    validate_fresh_preflight(decision.get('freshPreflight'),source)


def require(value, message):
    if not value:
        raise ValueError(message)


def read_proof(descriptor):
    require(isinstance(descriptor, dict) and set(descriptor) == {'path', 'sha256'}, 'Malformed descriptor')
    require(isinstance(descriptor['path'], str) and isinstance(descriptor['sha256'], str)
            and re.fullmatch('[0-9a-f]{64}', descriptor['sha256']), 'Malformed descriptor values')
    p = Path(descriptor['path'])
    require(p.is_absolute() and p.is_file() and not p.is_symlink() and p.stat().st_size <= 16*1024*1024,
            'Missing or unbounded preflight evidence')
    data = p.read_bytes()
    require(hashlib.sha256(data).hexdigest() == descriptor['sha256'], 'Preflight evidence hash drift')
    return json.loads(data)


def file_sha(path):
    p = Path(path)
    require(p.is_absolute() and p.is_file() and not p.is_symlink() and p.stat().st_size <= 128*1024*1024,
            'Missing, unsafe or unbounded pinned file')
    with p.open('rb') as stream:
        return hashlib.file_digest(stream,'sha256').hexdigest()


def capture_binding(api):
    binding = api.get('ALPHA_CAPTURE_REPAIR_BINDING')
    require(isinstance(binding,dict) and set(binding) == {'launcherSha256','ownedProcessModuleSha256','proofSha256'}
            and all(isinstance(v,str) and re.fullmatch('[0-9a-f]{64}',v) for v in binding.values()),
            'Exact capture repair pins are not installed')
    return binding


def capture_file(descriptor):
    require(isinstance(descriptor,dict) and set(descriptor) == {'path','sha256'}
            and isinstance(descriptor['path'],str) and isinstance(descriptor['sha256'],str)
            and re.fullmatch('[0-9a-f]{64}',descriptor['sha256']), 'Malformed capture descriptor')
    path = Path(descriptor['path'])
    require(path.is_absolute() and '..' not in path.parts, 'Foreign capture path')
    for p in (path,*path.parents):
        require(p.exists(), 'Missing capture path')
        metadata = p.lstat()
        require(not stat.S_ISLNK(metadata.st_mode)
                and not getattr(metadata,'st_file_attributes',0) & stat.FILE_ATTRIBUTE_REPARSE_POINT,
                'Reparse capture path')
    require(stat.S_ISREG(path.stat().st_mode) and file_sha(path) == descriptor['sha256'],
            'Capture file hash or regular-file mismatch')
    return path


def capture_json(descriptor):
    capture_file(descriptor)
    return read_proof(descriptor)


def capture_metadata_shape(api, profile, *, metadata_head='HEAD', stream_path=False, collector_query=False, git_query=False, microfile_disposition=False):
    names = {'IMPLEMENTATION_COMMIT','IMPLEMENTATION_TREE','ALPHA_PREFIX_REPAIR_BINDING','ALPHA_CAPTURE_REPAIR_BINDING'}
    if stream_path:names.add('ALPHA_STREAM_PATH_REPAIR_BINDING')
    if collector_query:names.add('ALPHA_COLLECTOR_QUERY_BINDING')
    if git_query:names.add('ALPHA_GIT_QUERY_BINDING')
    if microfile_disposition:names.add('ALPHA_MICROFILE_DISPOSITION_BINDING')
    def body(revision):
        parsed = ast.parse(api['git']('show',revision+':tools/validate_recovery_0d6_candidate.py',root=ROOT))
        kept=[];seen=set()
        for node in parsed.body:
            if isinstance(node,ast.Assign) and len(node.targets)==1 and isinstance(node.targets[0],ast.Name) and node.targets[0].id in names:
                require(node.targets[0].id not in seen, 'Duplicate capture metadata assignment')
                seen.add(node.targets[0].id)
                # Metadata must be inert constants, never a call evaluated during import.
                ast.literal_eval(node.value)
            else:kept.append(node)
        parsed.body=kept
        return ast.dump(parsed,include_attributes=False)
    require(body(profile.implementation_commit) == body(metadata_head), 'Capture metadata behavior differs from implementation')


def capture_native_interval(receipt):
    times=[]
    for key in ('startedAtUtc','endedAtUtc'):
        value=receipt.get(key)
        require(isinstance(value,str), 'Capture native timestamp missing')
        try:
            parsed=datetime.datetime.fromisoformat(value.replace('Z','+00:00'))
        except ValueError as exc:
            raise ValueError('Invalid capture native UTC timestamp') from exc
        require(parsed.utcoffset()==datetime.timedelta(0), 'Capture native timestamp is not UTC')
        times.append(parsed)
    require(times[0]<=times[1], 'Reversed capture native interval')


def validate_capture_repair(api, profile, prefix_binding, descriptor):
    return _validate_capture_repair_at(api,profile,prefix_binding,descriptor,str(ROOT),'HEAD')


def validate_capture_native_tests(tests, new):
    require(isinstance(tests,list) and 0<len(tests)<=16, 'Missing bounded capture native tests')
    manifest_paths=set();receipt_paths=set()
    for d in tests:
        test=capture_json(d)
        manifest_path=Path(d['path']).resolve()
        require(manifest_path not in manifest_paths, 'Duplicate capture native manifest')
        manifest_paths.add(manifest_path)
        require(isinstance(test,dict) and set(test)=={'schema','controlsBefore','controlsAfter','testFilesBefore',
                    'testFilesAfter','receipt','redEvidence'} and test['schema']=='DORA_CAPTURE_NATIVE_TEST_V1'
                and test['controlsBefore']==test['controlsAfter']==new, 'Capture tested controls differ from final embedded controls')
        files=test['testFilesBefore']
        require(isinstance(files,list) and 0<len(files)<=32 and files==test['testFilesAfter'], 'Capture test script identity drift')
        script_paths=[str(capture_file(f)) for f in files]
        require(len(set(script_paths))==len(script_paths), 'Duplicate capture test script')
        receipt=capture_json(test['receipt'])
        receipt_path=Path(test['receipt']['path']).resolve()
        require(receipt_path not in receipt_paths, 'Duplicate capture native receipt')
        receipt_paths.add(receipt_path)
        require(isinstance(receipt,dict) and type(receipt.get('nativeExitCode')) is int and receipt['nativeExitCode']==0
                and receipt.get('timedOut',False) is False and isinstance(receipt.get('argv'),list)
                and all(isinstance(arg,str) for arg in receipt['argv'])
                and any(p in receipt['argv'] for p in script_paths)
                and isinstance(receipt.get('startedAtUtc'),str) and isinstance(receipt.get('endedAtUtc'),str),
                'Capture native test failed or command lacks pinned script')
        argv=receipt['argv']
        require(len(argv)>=10 and argv[1:7]==['-NoLogo','-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File']
                and argv.count('-File')==1 and argv[7] in script_paths and Path(argv[7]).suffix.lower()=='.ps1',
                'Capture native test invocation differs from exact PowerShell script origin')
        capture_file(dict(path=argv[0],sha256=POWERSHELL_SHA256))
        require(argv.count('-ModulePath')==1 and argv.index('-ModulePath')+1<len(argv)
                and argv[argv.index('-ModulePath')+1]==new['rec_i3_owned_process.psm1']['path'],
                'Capture native tested module differs from final embedded module')
        capture_native_interval(receipt)
        red=test['redEvidence']
        require(isinstance(red,list) and 0<len(red)<=32, 'Capture original RED provenance missing')
        for f in red:capture_file(f)


def _validate_capture_repair_at(api, profile, prefix_binding, descriptor, source_root, metadata_head):
    binding = capture_binding(api)
    require(isinstance(descriptor,dict) and descriptor.get('sha256') == binding['proofSha256'], 'Capture proof pin mismatch')
    proof = capture_json(descriptor)
    git=api['git']
    require(git('rev-parse',CAPTURE_BASELINE_COMMIT+'^{tree}',root=ROOT) == CAPTURE_BASELINE_TREE
            and git('merge-base',CAPTURE_BASELINE_COMMIT,profile.implementation_commit,root=ROOT) == CAPTURE_BASELINE_COMMIT,
            'Capture source baseline or ancestry mismatch')
    legacy=legacy_api()
    before=legacy['tree_entries'](git,CAPTURE_BASELINE_COMMIT)
    after=legacy['tree_entries'](git,profile.implementation_commit)
    paths=sorted(p for p in before.keys() | after.keys() if before.get(p) != after.get(p))
    require(set(paths) == CAPTURE_HOST_PATHS, 'Capture repair differs from exact host-only delta')
    for p in paths:
        require(all(tree.get(p,{}).get('mode')=='100644' and tree[p]['type']=='blob' for tree in (before,after)),
                'Capture source is deleted or nonregular')
    require({k:prefix_binding.get(k) for k in CAPTURE_APK_PAIR} == CAPTURE_APK_PAIR, 'Capture APK context changed')
    capture_metadata_shape(api,profile,metadata_head=metadata_head)
    require(isinstance(proof,dict), 'Malformed capture proof')
    old=proof.get('oldControls');new=proof.get('newControls')
    parents=[]
    for controls in (old,new):
        require(isinstance(controls,dict) and set(controls)==set(CAPTURE_OLD_CONTROLS), 'Capture controls must be the exact six siblings')
        group=set()
        for name,d in controls.items():
            p=capture_file(d)
            require(p.name==name, 'Capture control basename mismatch')
            group.add(p.parent.resolve())
        require(len(group)==1, 'Capture controls have foreign parents')
        parents.append(group.pop())
    require(parents[0]!=parents[1], 'Capture successor overwrites historical controls')
    require({n:d['sha256'] for n,d in old.items()}==CAPTURE_OLD_CONTROLS, 'Historical capture controls changed')
    expected_hashes=dict(CAPTURE_OLD_CONTROLS)
    expected_hashes.update({'Invoke-0D6Campaign.ps1':binding['launcherSha256'],
                           'rec_i3_owned_process.psm1':binding['ownedProcessModuleSha256']})
    require(all(expected_hashes[n]!=CAPTURE_OLD_CONTROLS[n] for n in ('Invoke-0D6Campaign.ps1','rec_i3_owned_process.psm1'))
            and {n:d['sha256'] for n,d in new.items()}==expected_hashes,
            'Capture successor identity mismatch or historical launcher fallback')
    tests=proof.get('nativeTests')
    validate_capture_native_tests(tests,new)
    capture_file(proof.get('independentReview'))
    expected=dict(schema='DORA_RECOVERY_CAPTURE_REPAIR_V1',sourceRoot=source_root,
        baseline=dict(commit=CAPTURE_BASELINE_COMMIT,tree=CAPTURE_BASELINE_TREE),
        implementation=dict(commit=profile.implementation_commit,tree=profile.implementation_tree),apkPair=CAPTURE_APK_PAIR,
        sourceDelta=[dict(path=p,before=before[p],after=after[p]) for p in paths],
        oldControls=old,newControls=new,nativeTests=tests,independentReview=proof['independentReview'])
    require(proof==expected, 'Capture proof differs from exact source and control facts')
    return new


def validate_preflight_origin(attempt, pin, native, launcher, source):
    require(pin.get('schema') == 'DORA_0D6_PRIVATE_LAUNCH_PIN_V1'
            and pin.get('ownerSessionId') == attempt['ownerSessionId']
            and isinstance(pin.get('sourceRoot'),str) and Path(pin['sourceRoot']).resolve() == ROOT.resolve(),
            'Foreign preflight pin schema, owner or source root')
    args = launcher['argv']
    require(len(args) == 13 and args[1:7] == ['-NoLogo','-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File']
            and args[8:] == ['-PinPath',attempt['pin']['path'],'-ApprovedPinSha256',attempt['pin']['sha256'],'-Execute'],
            'Foreign preflight launcher command')
    origin_gate=read_proof(dict(path=pin['gatePath'],sha256=pin['gateFileSha256']))
    hint=_tru03_route_hint(origin_gate.get('alphaPreflight',{}).get('sourceRepair'))
    api=candidate_api(hint)
    capture_binding(api)
    # The actual preflight gate below revalidates the entire source profile. This
    # independent origin check binds its private launcher path before gate entry.
    origin_gate=read_proof(dict(path=pin['gatePath'],sha256=pin['gateFileSha256']))
    source_proof_descriptor=origin_gate.get('alphaPreflight',{}).get('sourceRepair')
    require(isinstance(source_proof_descriptor,dict)
            and source_proof_descriptor.get('sha256')==api.get('ALPHA_PREFIX_REPAIR_BINDING',{}).get('applicabilitySha256'),
            'Preflight capture applicability pin mismatch')
    source_proof=read_proof(source_proof_descriptor)
    if tru03_route_required(api,source_proof,api['active_profile']()):
        _,controls=validate_microfile_tru03_proof(api,api['active_profile'](),api['ALPHA_PREFIX_REPAIR_BINDING'],
            source_proof,source_proof_descriptor,origin_gate.get('proofs',{}).get('independentReview'))
    elif microfile_disposition_binding(api) is not None:
        _,controls=validate_microfile_disposition_proof(api,api['active_profile'](),api['ALPHA_PREFIX_REPAIR_BINDING'],
            source_proof,source_proof_descriptor,origin_gate.get('proofs',{}).get('independentReview'))
    elif git_query_binding(api) is not None:
        _,controls=validate_git_query_proof(api,api['active_profile'](),api['ALPHA_PREFIX_REPAIR_BINDING'],
            source_proof,source_proof_descriptor,origin_gate.get('proofs',{}).get('independentReview'))
    elif collector_query_binding(api) is not None:
        _,controls=validate_collector_query_proof(api,api['active_profile'](),api['ALPHA_PREFIX_REPAIR_BINDING'],
            source_proof,source_proof_descriptor,origin_gate.get('proofs',{}).get('independentReview'))
    elif stream_path_binding(api) is not None:
        _,controls=validate_stream_path_proof(api,api['active_profile'](),api['ALPHA_PREFIX_REPAIR_BINDING'],
            source_proof,source_proof_descriptor,origin_gate.get('proofs',{}).get('independentReview'))
    else:
        controls=validate_capture_repair(api,api['active_profile'](),api['ALPHA_PREFIX_REPAIR_BINDING'],source_proof.get('captureRepair'))
    control=controls['Invoke-0D6Campaign.ps1']
    require(Path(args[7])==Path(control['path']) and file_sha(args[0]) == POWERSHELL_SHA256
            and file_sha(args[7]) == control['sha256'] and pin.get('launcherSha256') == control['sha256'],
            'Preflight launcher identity drift')
    require(file_sha(native['argv'][0]) == ADB_SHA256, 'Preflight ADB identity drift')
    require(pin.get('pythonSha256') == PYTHON_SHA256 and file_sha(pin['pythonPath']) == PYTHON_SHA256,
            'Preflight Python identity drift')
    require(file_sha(pin['appApkPath']) == source['appApkSha256']
            and file_sha(pin['testApkPath']) == source['testApkSha256'], 'Preflight installed-APK pin drift')
    require(pin.get('driverSha256') == file_sha(ROOT/'tools/recovery_campaign.py'), 'Preflight driver drift')
    plan = read_proof(dict(path=pin['planPath'],sha256=pin['planFileSha256']))
    gate = read_proof(dict(path=pin['gatePath'],sha256=pin['gateFileSha256']))
    require(plan.get('source') == source and gate.get('source') == source
            and 'alphaPreflight' in gate and 'alphaReduced' not in gate and 'alphaCampaign' not in gate,
            'Foreign preflight plan or gate')
    campaign = runpy.run_path(str(ROOT/'tools/recovery_campaign.py'))
    # This is the fixed preflight gate, which validates only static source proof.
    # It cannot recurse into campaign fresh-preflight evidence validation.
    campaign['validate_execution_gate'](plan,gate,payload=attempt['payload'])


def instrument_arguments(payload, source):
    args = ['-H', '127.0.0.1', '-P', '5037', '-s', 'emulator-5556', 'shell', 'am', 'instrument',
            '-w', '-r', '-e', 'class', PREFLIGHT_SELECTORS[payload], '-e', 'recoveryHarnessRevision', source['commit']]
    if PREFLIGHT_FLAGS[payload]:
        args += ['-e', PREFLIGHT_FLAGS[payload], 'true']
    return args + ['com.monumentogram.dora.poc.recovery.test/androidx.test.runner.AndroidJUnitRunner']


def validate_preflight_stdout(text, payload, source):
    require(isinstance(text, str), 'Missing raw instrumentation output')
    observations = {}
    lines = []
    for line in text.splitlines():
        prefix = 'INSTRUMENTATION_STATUS: stream='
        if line.startswith(prefix):
            value = line[len(prefix):]
            matches = [m for m in PREFLIGHT_MARKERS[payload] if value.startswith(m)]
            if matches:
                marker = matches[0]
                require(marker not in observations, 'Duplicate preflight observation')
                observation = json.loads(value[len(marker):])
                require(isinstance(observation, dict), 'Malformed preflight observation')
                observations[marker] = observation
                # Only known marker names are normalized. Test identity, status, ordering,
                # original JSON and runner completion pass through the unchanged parser.
                line = prefix + 'DORA_RECOVERY_CAMPAIGN_EVENT ' + value[len(marker):]
        lines.append(line)
    parser = runpy.run_path(str(ROOT/'tools/recovery_instrumentation_status.py'))
    parser['validate_instrumentation_success'](('\n'.join(lines)+'\n').encode(), PREFLIGHT_SELECTORS[payload])
    require(set(observations) == set(PREFLIGHT_MARKERS[payload]), 'Missing preflight observation')
    for value in observations.values():
        require(value.get('harnessRevision') == source['commit']
                and value.get('protocolId') == 'poc-recovery-protocol-stage0-v0.8', 'Foreign preflight revision')
        if payload == 'SUPPLEMENTAL_SQLITE':
            require(value.get('apks') == {'targetSha256': source['appApkSha256'], 'testSha256': source['testApkSha256']}
                    and value.get('device', {}).get('sdk') == 36
                    and value.get('device', {}).get('fingerprint') == FINGERPRINT,
                    'Foreign preflight APK or device')
            require(value.get('sqlitePragmaObservations') == {'journal_mode':'wal', 'synchronous':'2',
                    'wal_autocheckpoint':'0', 'foreign_keys':'1'}, 'SQLite configuration preflight failed')
        else:
            require(value.get('targetApkSha256') == source['appApkSha256']
                    and value.get('testApkSha256') == source['testApkSha256']
                    and value.get('api') == 36 and value.get('deviceFingerprint') == FINGERPRINT
                    and value.get('status') == 'PASS' and value.get('powerLossDurabilityProven') is False,
                    'Platform preflight failed or foreign')


def validate_fresh_preflight(descriptor, source):
    proof = read_proof(descriptor)
    require(isinstance(proof, dict) and proof.get('schema') == 'DORA_RECOVERY_FRESH_PREFLIGHT_V1'
            and proof.get('source') == source and proof.get('environment') == 'E36-GAPI'
            and type(proof.get('campaignAttempts')) is int and proof['campaignAttempts'] == 0
            and proof.get('humanReview') is False, 'Fresh preflight source or scope mismatch')
    attempts = proof.get('attempts')
    require(isinstance(attempts, list) and len(attempts) == 3, 'Three fresh preflights required')
    seen = set()
    roots = set()
    for attempt in attempts:
        payload = attempt.get('payload')
        require(payload in PREFLIGHT_SELECTORS and payload not in seen, 'Duplicate or foreign preflight payload')
        seen.add(payload)
        pin = read_proof(attempt.get('pin'))
        require(pin.get('source') == source and pin.get('payload') == payload and pin.get('attemptIds') == [],
                'Preflight pin source or scope mismatch')
        root = Path(pin['outputRoot']).resolve()
        require(root.is_absolute() and root.name == attempt.get('ownerSessionId') and root not in roots,
                'Preflight owner/output mismatch')
        roots.add(root)
        for name in ('instrumentationReceipt', 'terminal'):
            require(Path(attempt[name]['path']).resolve().parent == root, 'Foreign preflight raw root')
        native = read_proof(attempt['instrumentationReceipt'])
        require(type(native.get('nativeExitCode')) is int and native['nativeExitCode'] == 0
                and native.get('timedOut') is False and native.get('rawStderr') == '', 'Preflight native failure')
        argv = native.get('argv')
        require(isinstance(argv, list) and len(argv) > 1 and Path(argv[0]).name.lower() in ('adb', 'adb.exe')
                and argv[1:] == instrument_arguments(payload, source), 'Foreign preflight native command')
        validate_preflight_stdout(native.get('rawStdout'), payload, source)
        terminal = read_proof(attempt['terminal'])
        require(terminal.get('schema') == 'DORA_0D6_PRIVATE_LAUNCH_RESULT_V1'
                and terminal.get('cleanupResult') == 'VERIFIED' and terminal.get('failure') is None
                and terminal.get('completed') == [payload] and terminal.get('deviceExecutionRequested') is True
                and terminal.get('campaignApprovalGranted') is False, 'Preflight cleanup or completion invalid')
        launcher = read_proof(attempt['launcherReceipt'])
        require(type(launcher.get('nativeExitCode')) is int and launcher['nativeExitCode'] == 0,
                'Preflight launcher failed')
        args = launcher.get('argv', [])
        for flag, value in (('-PinPath', attempt['pin']['path']), ('-ApprovedPinSha256', attempt['pin']['sha256'])):
            require(args.count(flag) == 1 and args.index(flag)+1 < len(args) and args[args.index(flag)+1] == value,
                    'Preflight launcher pin mismatch')
        require(args.count('-Execute') == 1, 'Preflight was not executed')
        validate_preflight_origin(attempt,pin,native,launcher,source)
