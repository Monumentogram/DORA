"""Admission for the approved surviving STREAM checkpoint-prefix repair."""
from pathlib import Path
import hashlib
import json
import re
import runpy

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
    ('test','candidate/RecoveryCampaignParserClassificationTest.kt'),
    ('test','candidate/RecoveryCampaignConfirmationAccessTest.kt'),
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


def legacy_api():
    return runpy.run_path(str(ROOT/'tools/recovery_alpha_repair.py'))


def candidate_api():
    return runpy.run_path(str(ROOT/'tools/validate_recovery_0d6_candidate.py'))


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
    expected = applicability_facts(api,profile,binding)
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


def validate_preflight_origin(attempt, pin, native, launcher, source):
    require(pin.get('schema') == 'DORA_0D6_PRIVATE_LAUNCH_PIN_V1'
            and pin.get('ownerSessionId') == attempt['ownerSessionId']
            and isinstance(pin.get('sourceRoot'),str) and Path(pin['sourceRoot']).resolve() == ROOT.resolve(),
            'Foreign preflight pin schema, owner or source root')
    args = launcher['argv']
    require(len(args) == 13 and args[1:7] == ['-NoLogo','-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File']
            and args[8:] == ['-PinPath',attempt['pin']['path'],'-ApprovedPinSha256',attempt['pin']['sha256'],'-Execute'],
            'Foreign preflight launcher command')
    require(file_sha(args[0]) == POWERSHELL_SHA256 and file_sha(args[7]) == LAUNCHER_SHA256
            and pin.get('launcherSha256') == LAUNCHER_SHA256, 'Preflight launcher identity drift')
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
