"""Admission for the approved surviving STREAM checkpoint-prefix repair."""
from pathlib import Path
import ast
import datetime
import hashlib
import json
import re
import runpy
import stat

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
        require(after.get(p,{}).get('mode')=='100644' and after[p]['type']=='blob',
                'MICROFILE disposition source is deleted or nonregular')
        if p in MICROFILE_DISPOSITION_ADDED_PATHS:
            require(p not in before,'MICROFILE disposition added path already existed')
        else:
            require(before.get(p,{}).get('mode')=='100644' and before[p]['type']=='blob',
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


def legacy_api():
    return runpy.run_path(str(ROOT/'tools/recovery_alpha_repair.py'))


def candidate_api():
    metadata=ROOT/'tools/validate_recovery_0d6_candidate.py'
    stream_path_metadata_literal(metadata.read_text(encoding='utf-8'))
    collector_query_metadata_literal(metadata.read_text(encoding='utf-8'))
    git_query_metadata_literal(metadata.read_text(encoding='utf-8'))
    microfile_disposition_metadata_literal(metadata.read_text(encoding='utf-8'))
    return runpy.run_path(str(metadata))


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
    api = candidate_api()
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
    api=candidate_api()
    capture_binding(api)
    # The actual preflight gate below revalidates the entire source profile. This
    # independent origin check binds its private launcher path before gate entry.
    origin_gate=read_proof(dict(path=pin['gatePath'],sha256=pin['gateFileSha256']))
    source_proof_descriptor=origin_gate.get('alphaPreflight',{}).get('sourceRepair')
    require(isinstance(source_proof_descriptor,dict)
            and source_proof_descriptor.get('sha256')==api.get('ALPHA_PREFIX_REPAIR_BINDING',{}).get('applicabilitySha256'),
            'Preflight capture applicability pin mismatch')
    source_proof=read_proof(source_proof_descriptor)
    if microfile_disposition_binding(api) is not None:
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
