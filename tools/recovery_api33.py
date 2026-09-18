#!/usr/bin/env python3
"""Exact seven-check API33 successor; existing E36 execution remains unchanged.

The private retained-handle launcher owns all processes. This module only consumes
an expiring owned session and a reviewed, hash-bound packet. It never starts an
ADB server/emulator and never installs or uninstalls packages.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import uuid

# Resolve only the repository's sibling implementations, including under -I.
# Check immutable dependencies before executing any sibling module code.
for _name, _sha in {
    'recovery_campaign.py': '7bd416d5cbdb90b6b6a30680f0300a143eb89306050f34ea2631c97e0205b8fa',
    'recovery_instrumentation_status.py': '984d515734d96aca7134466fba060af6941cd28cc47da7222bfe8d5afe12dc97',
}.items():
    if hashlib.sha256(Path(__file__).with_name(_name).read_bytes()).hexdigest() != _sha:
        raise ValueError('Immutable API33 dependency changed before import: ' + _name)
sys.path.insert(0, str(Path(__file__).resolve().parent))
import recovery_campaign as campaign
from recovery_instrumentation_status import validate_instrumentation_success

SCHEMA = 'DORA_API33_SEVEN_V1'
APP_COMMIT = '79d930d73ce7836c3cf5bec10be85f800936e829'
APP_SHA = '9dd8f1dfd05ad4404e9c52ad5e7b11b575e6ba03bcba87a176bd953ddc649a7c'
PACKAGE = campaign.PACKAGE
PREFIX = 'com.monumentogram.dora.poc.recovery.'
PAYLOADS = {
    'F33-01': (PREFIX + 'candidate.RecoveryE36GapiPreflightInstrumentedTest#supplementalCanonicalSqliteCompileOptionsPreflight',
               'pocRecoveryE36GapiSupplementalSqlitePreflight',
               ('INSTRUMENTATION_SUPPLEMENTAL_SQLITE_PREFLIGHT_OBSERVATION ', 'INSTRUMENTATION_SUPPLEMENTAL_SQLITE_PREFLIGHT_STATUS ')),
    'F33-02': (PREFIX + 'journal.RecoveryJournalConnectionConfigurationTest#primaryAndConcurrentReaderKeepConfigurationAfterReopen', None, ()),
    'F33-03': (PREFIX + 'candidate.RecoveryPlatformPrerequisitesInstrumentedTest#syntheticKeystoreLifecycleAndFilesystemPrerequisites',
               'pocRecoveryPlatformPrerequisites', ('INSTRUMENTATION_PLATFORM_PREREQUISITES_OBSERVATION ',)),
    'F33-04': ('NORMAL', campaign.CANDIDATES[0], ()),
    'F33-05': ('NORMAL', campaign.CANDIDATES[1], ()),
    'F33-06': ('HARD_KILL', campaign.CANDIDATES[0], ()),
    'F33-07': ('HARD_KILL', campaign.CANDIDATES[1], ()),
}
require = campaign.require


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path: Path):
    return json.loads(path.read_text(encoding='utf-8-sig'))


def descriptor(path: Path) -> dict:
    return dict(path=str(path.resolve()), sha256=sha(path))


def verify_file(item: dict) -> Path:
    path = Path(item['path'])
    require(path.is_absolute() and path.is_file(), 'Missing absolute pinned file')
    require(all(not p.is_symlink() and not p.is_junction() for p in (path, *path.parents)), 'Reparse path denied')
    require(sha(path) == item['sha256'], 'Pinned file digest mismatch: ' + path.name)
    return path


def validate_contract(packet: dict) -> None:
    require(packet.get('schema') == SCHEMA, 'Wrong API33 packet schema')
    require(re.fullmatch(r'[A-Za-z0-9_-]{1,24}', packet.get('executionId', '')) is not None, 'Unsafe execution ID')
    require(packet.get('appSourceCommit') == APP_COMMIT, 'Accepted app source changed')
    source = packet.get('source', {})
    require(set(source) == {'commit', 'tree', 'appApkSha256', 'testApkSha256'}, 'Exact source fields required')
    for key, value in source.items(): campaign.hex_value(value, 40 if key in ('commit', 'tree') else 64)
    require(source['appApkSha256'] == APP_SHA, 'Accepted app APK changed')
    require(source['commit'] == packet.get('harnessSourceCommit'), 'Harness/source commit alias mismatch')
    env = packet.get('environment', {})
    require(env.get('profile') == 'API33-GAPI' and type(env.get('api')) is int and env['api'] == 33, 'Exact API33 required')
    require(env.get('abi') == 'x86_64', 'API33 x86_64 required')
    for key in ('fingerprint', 'product', 'avdName'):
        require(isinstance(env.get(key), str) and bool(env[key]) and re.fullmatch(r'[A-Za-z0-9_./:+-]+', env[key]) is not None, 'Invalid exact ' + key)
    require(re.fullmatch(r'emulator-[0-9]{4,5}', env.get('serial', '')) is not None, 'Exact emulator serial required')
    require(type(env.get('adbPort')) is int and 1024 <= env['adbPort'] <= 65535, 'Invalid owned ADB port')
    require(packet.get('payloads') == list(PAYLOADS), 'Seven ordered payloads required')


def validate_live(packet: dict, live: dict) -> None:
    for key in ('api', 'abi', 'fingerprint', 'product', 'serial', 'avdName', 'adbPort'):
        require(live.get(key) == packet['environment'][key], 'Live identity mismatch: ' + key)


def validate_expiry(value: str, now: datetime | None = None) -> None:
    expiry = datetime.fromisoformat(value.replace('Z', '+00:00'))
    remaining = (expiry - (now or datetime.now(timezone.utc))).total_seconds()
    require(0 < remaining <= 300, 'Session expired or exceeds five minutes')


def validate_session_endpoint(packet: dict, session: dict) -> None:
    validate_live(packet, session['environment'])
    for flat, key in [('serial', 'serial'), ('adbPort', 'adbPort'), ('deviceFingerprint', 'fingerprint'), ('avdName', 'avdName')]:
        require(session.get(flat) == packet['environment'][key], 'Session endpoint mismatch: ' + flat)
    require(session.get('adbSha256') == packet['adb']['sha256'], 'Session ADB digest mismatch')


def build_plan(root: Path, packet: dict) -> dict:
    validate_contract(packet)
    inherited = campaign.build_plan(root, 'PHASE_A', packet['source'], 20260918)
    entries = []
    for identity, (kind, candidate, _) in list(PAYLOADS.items())[3:]:
        base = next(e for e in inherited['entries'] if e['kind'] == 'HARD_KILL' and e['candidateId'] == candidate
                    and e['stratumId'] == 'K08' and e['environment'] == 'E36-GAPI' and e['slot'] == 1)
        entry = dict(base, baseAttemptId=base['attemptId'], attemptId=packet['executionId'] + '-' + identity,
                     environment='API33-GAPI', kind=kind)
        entry['runId'] = uuid.uuid5(uuid.NAMESPACE_URL, SCHEMA + ':' + entry['attemptId']).hex
        # K08 is the unchanged Android fixture admission. NORMAL never invokes FAULT
        # or WRITE_UNTIL_BARRIER; PREPARE(false) closes the full writer ordinarily.
        entries.append(entry)
    return dict(schema=SCHEMA, source=packet['source'], protocolId=campaign.PROTOCOL,
                appSourceCommit=APP_COMMIT, harnessSourceCommit=packet['harnessSourceCommit'],
                environment=packet['environment'], entries=entries)


def git(root: Path, *args: str) -> str:
    return subprocess.run(['git', '-c', 'safe.directory=' + root.resolve().as_posix(), '-C', str(root), *args],
                          check=True, capture_output=True, text=True).stdout.strip()


def validate_admission(root: Path, packet: dict) -> None:
    validate_contract(packet)
    require(git(root, 'rev-parse', 'HEAD') == packet['harnessSourceCommit'], 'Harness checkout mismatch')
    require(git(root, 'rev-parse', 'HEAD^{tree}') == packet['source']['tree'], 'Harness tree mismatch')
    require(not git(root, 'status', '--porcelain=v1'), 'Harness source dirty')
    # Every product input remains byte-identical to the app's accepted source.
    changed = git(root, 'diff', '--name-only', APP_COMMIT, 'HEAD', '--', 'android').splitlines()
    require(all('/src/androidTest/' in n or '/src/sharedTest/' in n or '/src/test/' in n for n in changed), 'Product/build inputs changed')
    for name in ('recovery_campaign.py', 'recovery_instrumentation_status.py'):
        require(not git(root, 'diff', APP_COMMIT, 'HEAD', '--', 'tools/' + name), 'Inherited runner/oracle changed')
    require(not git(root, 'diff', APP_COMMIT, 'HEAD', '--', 'docs/stage0/poc-recovery-protocol-*'), 'Protocol recipe changed')
    for item in packet['files']: verify_file(item)
    require(sha(verify_file(packet['appApk'])) == APP_SHA, 'App artifact mismatch')
    require(sha(verify_file(packet['testApk'])) == packet['source']['testApkSha256'], 'Test artifact mismatch')
    for path in (Path(__file__), root / 'tools/recovery_campaign.py', root / 'tools/recovery_instrumentation_status.py'):
        require(descriptor(path) in packet['files'], 'Runner/parser not bound')
    proofs = {key: read(verify_file(value)) for key, value in packet['proofs'].items()}
    require(proofs['ci']['head_sha'] == packet['harnessSourceCommit'] and proofs['ci']['conclusion'] == 'success'
            and proofs['ci']['status'] == 'completed', 'Exact harness CI missing')
    require(proofs['review']['verdict'] == 'APPROVED' and proofs['review']['humanApprovalClaimed'] is False,
            'Independent technical review missing')
    for item in proofs['review']['files']: verify_file(item)
    require(descriptor(Path(__file__)) in proofs['review']['files'], 'API33 runner not reviewed')
    require(proofs['checks']['passed'] is True, 'Required local checks incomplete')
    for item in proofs['checks']['evidence']: verify_file(item)
    signatures = proofs['signatures']
    require(signatures['appSha256'] == APP_SHA and signatures['testSha256'] == packet['source']['testApkSha256']
            and signatures['appCertificateSha256'] == signatures['testCertificateSha256']
            and re.fullmatch(r'[0-9a-f]{64}', signatures['appCertificateSha256']) is not None
            and signatures['targetPackage'] == PACKAGE, 'APK target/signature mismatch')
    for item in signatures['evidence']: verify_file(item)
    require(packet['plan'] == build_plan(root, packet), 'API33 schedule/fixture/recipe drift')
    preflight_packet(packet)


def validate_fixed_status(output: bytes, payload: str) -> list[dict]:
    require(payload in list(PAYLOADS)[:3], 'Unknown fixed payload')
    selector, _, markers = PAYLOADS[payload]
    observations = []
    replacement = output
    for marker in markers:
        prefix = 'INSTRUMENTATION_STATUS: stream=' + marker
        found = [line[len(prefix):] for line in output.decode('utf-8').splitlines() if line.startswith(prefix)]
        require(len(found) == 1, 'Missing or duplicate fixed observation')
        value = json.loads(found[0]); require(isinstance(value, dict), 'Malformed observation')
        observations.append(value)
        replacement = replacement.replace(prefix.encode(), b'INSTRUMENTATION_STATUS: stream=API33_OBSERVATION ')
    validate_instrumentation_success(replacement, selector, 'API33_OBSERVATION ')
    return observations


def fixed_command(packet: dict, payload: str) -> list[str]:
    selector, flag, _ = PAYLOADS[payload]
    env = packet['environment']
    args = ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', selector]
    values = dict(recoveryHarnessRevision=packet['harnessSourceCommit'], recoveryDeviceProfile='API33-GAPI',
                  recoveryExpectedFingerprint=env['fingerprint'], recoveryExpectedProduct=env['product'],
                  recoveryExpectedAppSha256=APP_SHA, recoveryExpectedTestSha256=packet['source']['testApkSha256'])
    if flag: values[flag] = 'true'
    for key, value in values.items(): args.extend(['-e', key, value])
    return args + [campaign.RUNNER]


def verify_observations(packet: dict, payload: str, observations: list[dict]) -> None:
    if payload == 'F33-01':
        require(observations[0] == observations[1], 'SQLite observation/status differ')
        value = observations[0]
        require(value['device']['sdk'] == 33 and value['device']['fingerprint'] == packet['environment']['fingerprint'], 'SQLite environment drift')
        require(value['apks'] == {'targetSha256': APP_SHA, 'testSha256': packet['source']['testApkSha256']}, 'SQLite APK drift')
        require(value['harnessRevision'] == packet['harnessSourceCommit'], 'SQLite revision drift')
        options = value['sqliteCompileOptionsRaw']
        require(isinstance(options, list) and all(isinstance(row, str) for row in options)
                and value['sqliteCompileOptionsQueryFailed'] is False, 'SQLite metadata query or rows invalid')
        require(type(value['sqliteCompileOptionsCount']) is int and value['sqliteCompileOptionsCount'] == len(options),
                'SQLite option count mismatch')
        canonical = ('\n'.join(sorted(options)) + '\n').encode('utf-8')
        require(value['sqliteCompileOptionsCanonicalSha256'] == hashlib.sha256(canonical).hexdigest(),
                'SQLite canonical digest mismatch')
        for field in ('sqliteVersion', 'sqliteSourceId'):
            require(isinstance(value[field], str) and bool(value[field]) and value[field + 'QueryFailed'] is False,
                    'SQLite version/source query failed')
        pragmas = value['sqlitePragmaObservations']
        require(set(value['sqlitePragmaQueryFailures']) == {'journal_mode', 'synchronous', 'wal_autocheckpoint', 'foreign_keys'}
                and all(v is None for v in value['sqlitePragmaQueryFailures'].values()), 'SQLite pragma query failed')
        require(pragmas['journal_mode'].lower() == 'wal' and pragmas['synchronous'] in ('2', 'full')
                and pragmas['wal_autocheckpoint'] == '0' and pragmas['foreign_keys'] == '1', 'SQLite pragma mismatch')
    elif payload == 'F33-03':
        value = observations[0]
        require(value['status'] == 'PASS' and value['api'] == 33 and value['deviceFingerprint'] == packet['environment']['fingerprint'], 'Platform prerequisites failed')
        require(value['targetApkSha256'] == APP_SHA and value['testApkSha256'] == packet['source']['testApkSha256']
                and value['harnessRevision'] == packet['harnessSourceCommit'], 'Platform source drift')
        require(value['keystore']['cleanupAliasAbsent'] is True and value['filesystem']['cleanupOwnedNamespaceAbsent'] is True, 'Platform synthetic cleanup incomplete')


SQLITE_PREFLIGHT_PACKET_SHA = '88b7096ccc8f6a89ec3f8149770aad2400361d8afea151bcd6e5717e41334d58'
SQLITE_PREFLIGHT_COMMIT = 'fd3f46b3f8b07250c6e981ed05c7998fb640c643'


def preflight_packet(packet: dict) -> dict:
    if 'preflightReuse' not in packet:
        return packet
    proof_descriptor = packet['preflightReuse']
    proof = read(verify_file(proof_descriptor))
    require(proof.get('schema') == 'DORA_API33_PREFLIGHT_REUSE_V1', 'Wrong preflight reuse schema')
    prior_descriptor = proof['priorPacket']
    require(prior_descriptor['sha256'] == SQLITE_PREFLIGHT_PACKET_SHA, 'Unreviewed predecessor packet')
    prior = read(verify_file(prior_descriptor))
    validate_contract(prior)
    require(prior['harnessSourceCommit'] == SQLITE_PREFLIGHT_COMMIT, 'Wrong predecessor source')
    require(prior['environment'] == packet['environment'], 'Preflight reuse environment drift')
    for field in ('appApkSha256', 'testApkSha256'):
        require(prior['source'][field] == packet['source'][field], 'Preflight reuse APK drift')
    require(prior['launcher'] == packet['launcher'], 'Preflight reuse launcher drift')
    require(proof_descriptor in packet['files'] and prior_descriptor in packet['files'], 'Preflight reuse proof not pinned')
    review = read(verify_file(packet['proofs']['review']))
    require(review['verdict'] == 'APPROVED' and proof_descriptor in review['files']
            and prior_descriptor in review['files'], 'Preflight reuse not reviewed')
    root = Path(__file__).resolve().parents[1]
    git(root, 'merge-base', '--is-ancestor', SQLITE_PREFLIGHT_COMMIT, packet['harnessSourceCommit'])
    allowed = {'tools/recovery_api33.py', 'tools/test_recovery_api33.py',
               'tools/validate_recovery_0d6_candidate.py', 'tools/test_validate_recovery_0d6_candidate.py'}
    changed = set(git(root, 'diff', '--name-only', SQLITE_PREFLIGHT_COMMIT, packet['harnessSourceCommit']).splitlines())
    require(bool(changed) and changed <= allowed, 'Preflight reuse changed Android or other contract inputs')
    return prior


def verify_preflights(packet: dict, prerequisites: list[dict]) -> None:
    require(len(prerequisites) == 3, 'All three API33 preflights required')
    expected = preflight_packet(packet)
    reuse = 'preflightReuse' in packet
    for payload, item in zip(list(PAYLOADS)[:3], prerequisites):
        record_path = verify_file(item['record'])
        terminal_path = verify_file(item['terminal'])
        raw_path = verify_file(item['instrumentationStdout'])
        require(record_path.parent.name == 'p' and terminal_path == record_path.parent.parent / 'terminal.json'
                and raw_path.parent == record_path.parent, 'Mixed preflight attempt evidence')
        record = read(record_path)
        terminal = read(terminal_path)
        observations = validate_fixed_status(raw_path.read_bytes(), payload)
        verify_observations(expected, payload, observations)
        corrected_empty = (reuse and payload == 'F33-01' and record['result'] == 'FAIL'
                           and record.get('error') == 'SQLite metadata absent'
                           and observations[0]['sqliteCompileOptionsRaw'] == []
                           and terminal['failure'] == 'PAYLOAD_EXIT:2')
        require(terminal['cleanupResult'] == 'VERIFIED' and (terminal['failure'] is None or corrected_empty)
                and terminal['bootstrap'] is False and terminal['payload'] == payload
                and terminal['source'] == expected['source']
                and terminal['packetSha256'] == record['packetSha256'], 'Preflight launcher cleanup incomplete')
        if reuse:
            require(record['packetSha256'] == SQLITE_PREFLIGHT_PACKET_SHA, 'Preflight predecessor packet drift')
        require(record['payload'] == payload and (record['result'] == 'PASS' or corrected_empty)
                and record['executed'] is True and record['source'] == expected['source']
                and record['environment'] == expected['environment']
                and record['rawRetentionComplete'] is True, 'Missing applicable API33 preflight')

def assess(entry: dict, result: dict) -> dict:
    assessment = campaign.evaluate_attempt(entry, result)
    observed = result.get('candidateResult') or {}
    if entry['kind'] == 'NORMAL' and result['status'] == 'VALID':
        full = all(observed.get(k) == entry['plaintextBytes'] for k in ('acceptedEnd', 'recoveredEnd'))
        if entry['candidateId'] == campaign.CANDIDATES[0]: full = full and observed.get('streamTerminal') == 'AUTHENTICATED_EOF'
        if not full:
            assessment['failures'].append('NORMAL_FULL_EXTENT_NOT_RECOVERED'); assessment['verdict'] = 'FAIL'
    return assessment


def run_operation(transport, plan: dict, entry: dict, operation: str, variant: str) -> dict:
    if entry['kind'] != 'NORMAL':
        return campaign.run_operation(transport, plan, entry, operation, variant)
    require(operation in ('PREPARE', 'RECOVER', 'CLEANUP'), 'Normal flow cannot dispatch fault or barrier')
    request = campaign.request_for(plan, entry, operation, variant)
    request['normalCompletion'] = True
    command = campaign.instrument_command(transport.adb, transport.session['serial'], request,
                                          plan['source']['commit'], campaign.digest_json(plan))
    completed = transport.run(command[3:], operation.lower() + '-' + variant, timeout=180)
    require(sha(Path(__file__).with_name('recovery_instrumentation_status.py')) == transport.instrumentation_parser_sha256,
            'Instrumentation parser changed after admission')
    validate_instrumentation_success(completed.stdout, campaign.SELECTOR)
    events = campaign.parse_events(completed.stdout, entry, operation)
    require(not any(e['eventType'] == 'ERROR' for e in events), 'Android normal adapter returned ERROR')
    results = [e for e in events if e['eventType'] == 'RESULT']
    require(len(results) == 1, 'Missing or duplicate exact normal operation result')
    return results[0]


def run_payload(root: Path, packet: dict, packet_sha: str, session: dict, payload: str, output: Path,
                prerequisites: list[dict]) -> dict:
    validate_admission(root, packet)
    require(payload in PAYLOADS, 'Payload outside seven-check scope')
    require(session.get('schema') == 'DORA_API33_OWNED_SESSION_V1' and session.get('ownershipVerified') is True, 'Owned session required')
    require(session.get('packetSha256') == packet_sha and session.get('payload') == payload
            and session.get('source') == packet['source'], 'Session authority mismatch')
    validate_expiry(session['expiresAtUtc']); validate_session_endpoint(packet, session)
    receipt = read(verify_file(session['launcherReceipt']))
    require(receipt['packetSha256'] == packet_sha and receipt['payload'] == payload
            and receipt['launcherSha256'] == packet['launcher']['sha256'], 'Launcher receipt mismatch')
    verify_file(packet['launcher'])
    if payload in list(PAYLOADS)[3:]: verify_preflights(packet, prerequisites)
    require(not output.exists(), 'Attempt identity consumed; no implicit retry')
    output.mkdir(parents=True)
    campaign.save_new(output / 'scheduled.json', dict(packetSha256=packet_sha, payload=payload, session=session))
    record = dict(payload=payload, packetSha256=packet_sha, appSourceCommit=APP_COMMIT, harnessSourceCommit=packet['harnessSourceCommit'],
                  source=packet['source'], environment=packet['environment'], executed=False,
                  result='INCONCLUSIVE', rawRetentionComplete=False, cleanupResult='UNVERIFIED',
                  actualAndroidCommands=0, atUtc=datetime.now(timezone.utc).isoformat())
    transport = campaign.OwnedAdbTransport(verify_file(packet['adb']), session, output)
    transport.instrumentation_parser_sha256 = sha(root / 'tools/recovery_instrumentation_status.py')
    result = None
    entry = None
    try:
        transport.verify_device(packet['source'])
        for prop, expected in [('ro.build.version.sdk', '33'), ('ro.product.name', packet['environment']['product']),
                               ('ro.product.cpu.abi', 'x86_64')]:
            require(transport.run(['shell', 'getprop', prop], prop.replace('.', '-')).stdout.decode().strip() == expected, 'Runtime identity drift')
        validate_expiry(session['expiresAtUtc'])
        if payload in list(PAYLOADS)[:3]:
            record['actualAndroidCommands'] = 1
            completed = transport.run(fixed_command(packet, payload), 'instrumentation', timeout=180, require_success=False)
            selector = PAYLOADS[payload][0]
            cls, method = selector.split('#')
            record['executed'] = (f'INSTRUMENTATION_STATUS: class={cls}'.encode() in completed.stdout
                                  and f'INSTRUMENTATION_STATUS: test={method}'.encode() in completed.stdout
                                  and b'INSTRUMENTATION_STATUS_CODE: 1' in completed.stdout
                                  and b'INSTRUMENTATION_STATUS_CODE: -3' not in completed.stdout)
            record['rawRetentionComplete'] = True
            require(completed.returncode == 0, 'Instrumentation native failure')
            observations = validate_fixed_status(completed.stdout, payload)
            campaign.save_new(output / 'observations.json', dict(observations=observations))
            verify_observations(packet, payload, observations)
            record.update(result='PASS', cleanupResult='PAYLOAD_COMPLETE_PACKAGE_CLEANUP_BY_LAUNCHER')
        else:
            entry = next(e for e in packet['plan']['entries'] if e['attemptId'] == packet['executionId'] + '-' + payload)
            plan = packet['plan']; variant = 'DEFAULT'
            result = dict(attemptId=entry['attemptId'], status='UNTESTED', candidateResult=None,
                          rawRetentionComplete=False, startedVariants=[variant], retentionReceipts=[])
            if entry['kind'] == 'NORMAL':
                record['actualAndroidCommands'] += 1
                prepared = run_operation(transport, plan, entry, 'PREPARE', variant)
                record['executed'] = True
                campaign.save_new(output / 'prepared.json', prepared)
                campaign.retain_evidence(transport, plan, entry, prepared, 'before-recovery')
            else:
                record['actualAndroidCommands'] += 1
                result['kill'] = campaign.run_kill(transport, plan, entry, variant)
                record['executed'] = True
                barrier = read(output / 'kill-native-exit.json')['barrier']
                result.update(hostAcceptedEnd=barrier['acceptedEnd'], hostCommittedEnd=barrier.get('committedEnd'))
            result['status'] = 'VALID'
            record['actualAndroidCommands'] += 1
            observed = run_operation(transport, plan, entry, 'RECOVER', variant)
            campaign.save_new(output / 'first-recovery.json', observed)
            prefix = transport.extract_prefix(entry, observed)
            equal = campaign.compare_prefix(prefix, entry, observed.get('recoveredEnd'))
            record['actualAndroidCommands'] += 1
            replay = run_operation(transport, plan, entry, 'RECOVER', variant)
            campaign.save_new(output / 'replay.json', replay)
            replay_prefix = transport.extract_prefix(entry, replay, 'replayed')
            replay_equal = campaign.compare_prefix(replay_prefix, entry, replay.get('recoveredEnd'))
            observed['repeatStable'] = bool(observed.get('receiptIdentity')) and replay.get('receiptIdentity') == observed['receiptIdentity'] \
                and replay.get('recoveredEnd') == observed.get('recoveredEnd') and replay.get('classification') == observed.get('classification') \
                and replay_equal and prefix.read_bytes() == replay_prefix.read_bytes()
            result.update(candidateResult=observed, hostOracleEqual=equal)
            campaign.save_new(output / 'result-before-cleanup.json', result)
            retention = campaign.retain_evidence(transport, plan, entry, replay, 'before-final-cleanup')
            result.update(rawRetentionComplete=True, retentionReceipts=[dict(variant=variant, receipt=retention)])
            record['rawRetentionComplete'] = True
            record['actualAndroidCommands'] += 1
            cleanup = run_operation(transport, plan, dict(entry, hostRetentionReceipt=retention), 'CLEANUP', variant)
            campaign.save_new(output / 'cleanup.json', cleanup)
            require(cleanup.get('cleanupComplete') is True, 'Run-scoped cleanup incomplete')
            result['cleanupResult'] = 'VERIFIED'
            assessment = assess(entry, result)
            campaign.save_new(output / 'assessment.json', assessment)
            record.update(result=assessment['verdict'], cleanupResult='VERIFIED', assessment=assessment)
    except (ValueError, OSError, subprocess.SubprocessError, KeyError) as error:
        record['error'] = str(error)
        if result is not None:
            result.update(controllerError=str(error), cleanupResult='UNVERIFIED_STOP_NO_AUTOMATIC_RETRY')
            if not all(result.get('kill', {}).get(k) is True for k in campaign.KILL_PROOFS) and result['status'] != 'VALID':
                result.update(status='INVALID', invalidReason='EXTERNAL_CONTROLLER_OR_PREFLIGHT_EVIDENCE_ENVELOPE_INCOMPLETE_OR_SCHEMA_INVALID')
            assessment = assess(entry, result)
            record.update(result=assessment['verdict'], assessment=assessment)
            campaign.save_new(output / 'assessment.json', assessment)
        if record['executed'] and record['rawRetentionComplete'] and payload in list(PAYLOADS)[:3]:
            record['result'] = 'FAIL'
    finally:
        if result is not None: campaign.save_new(output / 'attempt-result.json', result)
        campaign.save_new(output / 'record.json', record)
        files = sorted(p for p in output.rglob('*') if p.is_file())
        (output / 'SHA256SUMS.txt').write_text(''.join(f'{sha(p)}  {p.relative_to(output).as_posix()}\n' for p in files), encoding='utf-8')
    return record


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('check', 'run'))
    parser.add_argument('--packet', type=Path, required=True)
    parser.add_argument('--packet-sha256', required=True)
    parser.add_argument('--session', type=Path)
    parser.add_argument('--payload', choices=PAYLOADS)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--prerequisites', type=Path)
    args = parser.parse_args()
    packet = read(verify_file(dict(path=str(args.packet.resolve()), sha256=args.packet_sha256)))
    root = Path(__file__).resolve().parents[1]
    validate_admission(root, packet)
    if args.mode == 'check': print('API33_ADMISSION_CHECKED_NO_DEVICE_OPERATIONS'); return 0
    require(args.session is not None and args.payload is not None and args.output is not None, 'Execution arguments missing')
    result = run_payload(root, packet, args.packet_sha256, read(args.session), args.payload, args.output,
                         read(args.prerequisites) if args.prerequisites else [])
    print(json.dumps(result)); return 0 if result['result'] == 'PASS' else 2


if __name__ == '__main__': raise SystemExit(main())
