#!/usr/bin/env python3
"""Exact nine-check physical POCO successor; existing E36 execution remains unchanged.

The private retained-handle launcher owns all processes. This module only consumes
an expiring owned session and a reviewed, hash-bound packet. It never starts an
ADB server/emulator and never installs or uninstalls packages.
"""
from __future__ import annotations

import argparse
import ast
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import uuid
import queue
import threading
import time

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

SCHEMA = 'DORA_PHYSICAL_NINE_V1'
APP_COMMIT = '79d930d73ce7836c3cf5bec10be85f800936e829'
APP_SHA = '9dd8f1dfd05ad4404e9c52ad5e7b11b575e6ba03bcba87a176bd953ddc649a7c'
PREFLIGHT_COMMIT = '00d9fdbe1c5578f0703c7275a6fa59646516a4ef'
PREFLIGHT_PACKET_SHA = '3692a3468229ad76f575a09e97bf7ab80c3f685da65ec2680b25bad6402e1871'
CONTROLLER_COMMIT = 'e86af1f53671c6655afbd7a91a03d78462de5dbd'
CONTROLLER_PACKET_SHA = 'cd2a12a79b2548f77ca6f23a8a162f84a60ee3aadd57ea3420646a6c78ad3c5a'
PACKAGE = campaign.PACKAGE
PREFIX = 'com.monumentogram.dora.poc.recovery.'
PAYLOADS = {
    'P35-01': (PREFIX + 'candidate.RecoveryE36GapiPreflightInstrumentedTest#supplementalCanonicalSqliteCompileOptionsPreflight',
               'pocRecoveryE36GapiSupplementalSqlitePreflight',
               ('INSTRUMENTATION_SUPPLEMENTAL_SQLITE_PREFLIGHT_OBSERVATION ', 'INSTRUMENTATION_SUPPLEMENTAL_SQLITE_PREFLIGHT_STATUS ')),
    'P35-02': (PREFIX + 'journal.RecoveryJournalConnectionConfigurationTest#primaryAndConcurrentReaderKeepConfigurationAfterReopen', None, ()),
    'P35-03': (PREFIX + 'candidate.RecoveryPlatformPrerequisitesInstrumentedTest#syntheticKeystoreLifecycleAndFilesystemPrerequisites',
               'pocRecoveryPlatformPrerequisites', ('INSTRUMENTATION_PLATFORM_PREREQUISITES_OBSERVATION ',)),
    'P35-04': ('NORMAL', campaign.CANDIDATES[0], ()),
    'P35-05': ('NORMAL', campaign.CANDIDATES[1], ()),
    'P35-06': ('HARD_KILL', campaign.CANDIDATES[0], ()),
    'P35-07': ('HARD_KILL', campaign.CANDIDATES[1], ()),
    'P35-08': ('HARD_KILL', campaign.CANDIDATES[0], ()),
    'P35-09': ('HARD_KILL', campaign.CANDIDATES[1], ()),
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
    require(packet.get('schema') == SCHEMA, 'Wrong physical packet schema')
    require(re.fullmatch(r'[A-Za-z0-9_-]{1,24}', packet.get('executionId', '')) is not None, 'Unsafe execution ID')
    require(packet.get('appSourceCommit') == APP_COMMIT, 'Accepted app source changed')
    source = packet.get('source', {})
    require(set(source) == {'commit', 'tree', 'appApkSha256', 'testApkSha256'}, 'Exact source fields required')
    for key, value in source.items(): campaign.hex_value(value, 40 if key in ('commit', 'tree') else 64)
    require(source['appApkSha256'] == APP_SHA, 'Accepted app APK changed')
    require(source['commit'] == packet.get('harnessSourceCommit'), 'Harness/source commit alias mismatch')
    env = packet.get('environment', {})
    fixed = dict(profile='POCO-M5-PHYSICAL', api=34, abi='arm64-v8a', model='22071219CG',
                 manufacturer='Xiaomi', buildType='user', release='14', device='stone',
                 product='stone_p_ru', selinux='Enforcing')
    require(set(env) == set(fixed) | {'fingerprint','serial','adbPort'}, 'Exact physical identity fields required')
    require(type(env['api']) is int and all(env.get(k) == v for k,v in fixed.items()), 'Unadmitted physical profile')
    require(isinstance(env['fingerprint'], str) and re.fullmatch(r'[A-Za-z0-9_./:+-]+', env['fingerprint']) is not None, 'Invalid fingerprint')
    require(isinstance(env['serial'], str) and re.fullmatch(r'[A-Za-z0-9]{6,64}', env['serial']) is not None, 'Exact physical serial required')
    require(type(env['adbPort']) is int and 1024 <= env['adbPort'] <= 65535, 'Invalid ADB port')
    require(packet.get('payloads') == list(PAYLOADS), 'Nine ordered payloads required')


def validate_live(packet: dict, live: dict) -> None:
    require(live == packet['environment'], 'Live physical identity mismatch')


def validate_expiry(value: str, now: datetime | None = None) -> None:
    expiry = datetime.fromisoformat(value.replace('Z', '+00:00'))
    remaining = (expiry - (now or datetime.now(timezone.utc))).total_seconds()
    require(0 < remaining <= 300, 'Session expired or exceeds five minutes')


def validate_session_endpoint(packet: dict, session: dict) -> None:
    validate_live(packet, session['environment'])
    for flat, key in [('serial', 'serial'), ('adbPort', 'adbPort'), ('deviceFingerprint', 'fingerprint')]:
        require(session.get(flat) == packet['environment'][key], 'Session endpoint mismatch: ' + flat)
    require(session.get('adbSha256') == packet['adb']['sha256'], 'Session ADB digest mismatch')


def build_plan(root: Path, packet: dict) -> dict:
    validate_contract(packet)
    inherited = campaign.build_plan(root, 'PHASE_A', packet['source'], 20260918)
    entries = []
    for identity, (kind, candidate, _) in list(PAYLOADS.items())[3:]:
        base = next(e for e in inherited['entries'] if e['kind'] == 'HARD_KILL' and e['candidateId'] == candidate
                    and e['stratumId'] == ('K12' if identity in ('P35-08','P35-09') else 'K08') and e['environment'] == 'E36-GAPI' and e['slot'] == 1)
        entry = dict(base, baseAttemptId=base['attemptId'], attemptId=packet['executionId'] + '-' + identity,
                     environment='POCO-M5-PHYSICAL', kind=kind)
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


def require_controller_for_payload(packet: dict, payload: str) -> None:
    require(payload in PAYLOADS or payload in ('PROBE', 'CLEANUP'), 'Unknown physical payload')
    if payload not in list(PAYLOADS)[5:]: return
    item = packet.get('controllerProof')
    require(isinstance(item, dict) and item in packet['files'], 'Physical SIGKILL proof missing')
    proof = read(verify_file(item))
    expected_source = controller_source(packet)
    require(proof['schema'] == 'DORA_PHYSICAL_SIGKILL_PROBE_V1'
            and proof['status'] == 'SUPPORTED' and proof['environment'] == packet['environment']
            and proof['source'] == expected_source, 'Controller proof does not apply')
    require(proof['kind'] == 'RUN_AS_EXACT_PID_SIGKILL' and proof['noRoot'] is True
            and proof['signalExit'] == 0 and proof['deathConfirmed'] is True
            and proof['boundedMeasurementComplete'] is True and proof['rawRetentionComplete'] is True
            and proof['executed'] is True and proof['result'] == 'PASS'
            and proof['countedAsCoverage'] is False
            and proof['targetUid'] > 10000 and proof['senderUid'] == proof['targetUid']
            and proof['targetCmdline'] == PACKAGE and proof['targetContext'].startswith('u:r:untrusted_app')
            and proof.get('observationMethod') == 'SHELL_PS_WIDE_EXACT_PID_V1'
            and type(proof.get('targetStartTimeTicks')) is int and proof['targetStartTimeTicks'] > 0
            and type(proof.get('observationElapsedSeconds')) in (int,float)
            and 0 <= proof['observationElapsedSeconds'] < 10
            and proof.get('naturalTimeoutObserved') is False
            and isinstance(proof.get('senderContext'), str) and proof['senderContext'].startswith('u:r:')
            and proof['selinux'] == 'Enforcing', 'Invalid non-root controller evidence')
    require(type(proof['targetPid']) is int and proof['targetPid'] > 1
            and proof['signalCommand'] == ['shell','run-as',PACKAGE,'/system/bin/kill','-9',str(proof['targetPid'])],
            'Controller command/PID mismatch')
    for evidence in proof['evidence']: verify_file(evidence)
    ready_items=[e for e in proof['evidence'] if Path(e['path']).name == 'probe-ready.json']
    command_items=[e for e in proof['evidence'] if Path(e['path']).name == 'probe-command.json']
    require(len(ready_items) == len(command_items) == 1, 'Probe rendezvous receipts missing')
    ready=read(verify_file(ready_items[0])); command=read(verify_file(command_items[0]))
    require(re.fullmatch(r'[0-9a-f]{32}', ready.get('nonce','')) is not None
            and command['nonce'] == ready['nonce'] and ready['pid'] == proof['targetPid']
            and ready['uid'] == proof['targetUid'] and ready['selinuxContext'] == proof['targetContext']
            and ready['packageName'] == ready['processName'] == ready['cmdline'] == PACKAGE,
            'Probe target proof mismatch')
    evidence_by_name={Path(e['path']).name: e for e in proof['evidence']}
    require(len(evidence_by_name) == len(proof['evidence'])
            and all(Path(e['path']).parent == Path(item['path']).parent for e in proof['evidence']),
            'Probe evidence directory or name collision')
    signal_matches=[re.fullmatch(r'(\d{3})-probe-sigkill\.result\.json', name)
                    for name in evidence_by_name]
    signal_matches=[m for m in signal_matches if m]
    require(len(signal_matches) == 1, 'Exact native signal receipt missing')
    signal_prefix=signal_matches[0][1] + '-probe-sigkill'
    require(all(signal_prefix + suffix in evidence_by_name
                for suffix in ('.command.json','.result.json','.stdout','.stderr')),
            'Native signal receipt quartet incomplete')
    signal_result=read(verify_file(evidence_by_name[signal_prefix+'.result.json']))
    signal_command=read(verify_file(evidence_by_name[signal_prefix+'.command.json']))
    require(signal_result == {'nativeExit': 0, 'timedOut': False}
            and signal_command['argv'][-6:] == proof['signalCommand']
            and verify_file(evidence_by_name[signal_prefix+'.stdout']).read_bytes().hex() == proof['signalStdoutHex']
            and verify_file(evidence_by_name[signal_prefix+'.stderr']).read_bytes().hex() == proof['signalStderrHex'],
            'Native signal receipt differs from proof')
    death_results=[(int(m[1]), name) for name in evidence_by_name
                   if (m := re.fullmatch(r'(\d{3})-probe-death\.result\.json', name))]
    confirmed=False
    for sequence, name in death_results:
        prefix=name[:-len('.result.json')]
        if sequence <= int(signal_matches[0][1]) or any(prefix+suffix not in evidence_by_name
                for suffix in ('.command.json','.stdout','.stderr')):
            continue
        native=read(verify_file(evidence_by_name[name]))
        argv=read(verify_file(evidence_by_name[prefix+'.command.json']))['argv']
        if native == {'nativeExit': 1, 'timedOut': False} and argv[-3:] == ['shell','pidof',PACKAGE] \
                and not verify_file(evidence_by_name[prefix+'.stdout']).read_bytes():
            confirmed=True
    require(confirmed, 'Independent post-signal death receipt missing')
    require(re.fullmatch(r'[0-9a-f]{64}', proof['killSha256']) is not None, 'Missing signal executable pin')
    review = read(verify_file(packet['proofs']['review']))
    require(item in review['files'], 'Controller proof not technically reviewed')


def controller_source(packet: dict) -> dict:
    """Preserve one successful probe and its live installation across framing repair."""
    if 'controllerReuse' not in packet: return packet['source']
    item = packet['controllerReuse']; reuse = read(verify_file(item))
    require(reuse.get('schema') == 'DORA_PHYSICAL_CONTROLLER_REUSE_V1'
            and reuse.get('source') == packet['source']
            and reuse.get('environment') == packet['environment']
            and reuse.get('executionId') == packet['executionId'], 'Controller reuse successor drift')
    prior_item = reuse['priorPacket']; prior = read(verify_file(prior_item))
    require(prior_item['sha256'] == CONTROLLER_PACKET_SHA
            and prior['harnessSourceCommit'] == CONTROLLER_COMMIT,
            'Controller reuse predecessor drift')
    require(reuse['measurementSource'] == prior['source'] and prior['environment'] == packet['environment']
            and all(prior['source'][k] == packet['source'][k] for k in ('appApkSha256','testApkSha256')),
            'Controller reuse identity or APK drift')
    required = [item,prior_item,reuse['reviewedDiff'],reuse['ownership'],reuse['priorTerminal'],reuse['record']]
    review = read(verify_file(packet['proofs']['review']))
    require(review['verdict'] == 'APPROVED' and packet['controllerProof'] == reuse['record']
            and all(value in packet['files'] and value in review['files'] for value in required),
            'Controller reuse inputs not reviewed and pinned')
    for value in required: verify_file(value)
    record=read(verify_file(reuse['record'])); terminal=read(verify_file(reuse['priorTerminal']))
    ownership=read(verify_file(reuse['ownership']))
    require(all(v['source'] == prior['source'] for v in (record,terminal,ownership))
            and record['packetSha256'] == prior_item['sha256'] and record['status'] == 'SUPPORTED'
            and terminal['record'] == reuse['record'] and terminal['ownershipProof'] == reuse['ownership']
            and terminal['cleanupResult'] == 'VERIFIED' and terminal['packagesPreservedOwned'] is True
            and terminal['failure'] is None, 'Controller reuse original ownership or probe drift')
    root=Path(__file__).resolve().parents[1]; before_commit=prior['harnessSourceCommit']
    git(root,'merge-base','--is-ancestor',before_commit,packet['harnessSourceCommit'])
    changed=set(git(root,'diff','--name-only',before_commit,packet['harnessSourceCommit']).splitlines())
    require(bool(changed) and changed <= {'tools/recovery_physical.py','tools/test_recovery_physical.py',
            'tools/validate_recovery_0d6_candidate.py','tools/test_validate_recovery_0d6_candidate.py'},
            'Controller reuse changed Android or contract inputs')
    require(verify_file(reuse['reviewedDiff']).read_text(encoding='utf-8') ==
            git(root,'diff','--binary','--no-ext-diff',before_commit,packet['harnessSourceCommit']),
            'Controller reuse reviewed diff mismatch')
    before=ast.parse(git(root,'show',before_commit+':tools/recovery_physical.py'))
    after=ast.parse(Path(__file__).read_text(encoding='utf-8'))
    names={'run_probe','live_signal_identity','observation_output','process_start_time',
           'process_context','signal_succeeded','validate_probe_target','validate_session_endpoint'}
    def semantics(module):
        return {n.name:ast.dump(n,include_attributes=False) for n in module.body
                if isinstance(n,ast.FunctionDef) and n.name in names}
    require(semantics(before) == semantics(after) and len(semantics(before)) == len(names),
            'Controller reuse probe semantics changed')
    return prior['source']


def precheck_payload(packet: dict, payload: str, prerequisites: list[dict] | None) -> None:
    """All row-specific gates run before launcher starts ADB or installs anything."""
    require_controller_for_payload(packet, payload)
    if payload in list(PAYLOADS)[3:]:
        require(prerequisites is not None, 'Preflight prerequisites descriptor required')
        verify_preflights(packet, prerequisites)


def validate_probe_target(ready: dict, nonce: str, pid_text: str, cmdline: bytes) -> None:
    require(ready.get('nonce') == nonce, 'Probe nonce mismatch')
    require(type(ready.get('pid')) is int and ready['pid'] > 1, 'Probe PID invalid')
    require(type(ready.get('uid')) is int and ready['uid'] > 10000, 'Probe UID invalid')
    require(ready.get('packageName') == PACKAGE and ready.get('processName') == PACKAGE
            and ready.get('cmdline') == PACKAGE,
            'Probe is not target package process')
    require(isinstance(ready.get('selinuxContext'), str)
            and ready['selinuxContext'].startswith('u:r:untrusted_app'), 'Unexpected target SELinux domain')
    campaign.kill_arguments(ready['pid'], pid_text, cmdline.decode('utf-8'))


def observation_output(transport, arguments: list[str], label: str) -> bytes:
    """exec-out may return native zero with a remote error in stdout."""
    try:
        result = transport.run(arguments, label, require_success=False)
    except (OSError, subprocess.SubprocessError, ValueError) as error:
        raise ValueError('OBSERVATION_UNAVAILABLE:' + label + ':' + str(error)) from error
    require(result.returncode == 0 and not result.stderr.strip()
            and re.search(rb'permission denied|operation not permitted|no such (?:file|process)|'
                          rb'unknown (?:option|command)|bad (?:option|pid)|not found|usage:',
                          result.stdout, re.I) is None,
            'OBSERVATION_UNAVAILABLE:' + label)
    return result.stdout


def process_start_time(value: bytes, pid: int) -> int:
    match = re.fullmatch(rb'([0-9]+) \(.*\) ([A-Za-z] .+)\r?\n?', value)
    require(match is not None and int(match[1]) == pid, 'IDENTITY_MISMATCH:stat PID')
    fields = match[2].split()
    require(len(fields) >= 20 and fields[19].isdigit() and int(fields[19]) > 0,
            'OBSERVATION_UNAVAILABLE:stat starttime')
    return int(fields[19])


def process_context(value: bytes, pid: int, uid: int, context: str) -> str:
    try:
        lines = value.decode('utf-8', errors='strict').splitlines()
    except UnicodeError as error:
        raise ValueError('OBSERVATION_UNAVAILABLE:ps encoding') from error
    require(len(lines) == 2 and lines[0].split() == ['PID', 'UID', 'LABEL', 'NAME'],
            'OBSERVATION_UNAVAILABLE:ps incomplete or ambiguous')
    fields = lines[1].split()
    require(len(fields) == 4 and fields[0].isdigit() and fields[1].isdigit(),
            'OBSERVATION_UNAVAILABLE:ps fields')
    require(re.fullmatch(r'u:r:untrusted_app(?:_[0-9]+)?:s0(?::c[0-9]+(?:,c[0-9]+)*)?', fields[2])
            is not None, 'OBSERVATION_UNAVAILABLE:ps context malformed or truncated')
    require(int(fields[0]) == pid and int(fields[1]) == uid and fields[3] == PACKAGE,
            'IDENTITY_MISMATCH:ps PID/UID/package')
    require(fields[2] == context, 'IDENTITY_MISMATCH:target SELinux context')
    return fields[2]


def signal_succeeded(result) -> bool:
    return result.returncode == 0 and not result.stdout.strip() and not result.stderr.strip()


def live_signal_identity(transport, pid: int, expected_uid: int, expected_context: str,
                         expected_kill_sha: str, label: str,
                         expected_sender_context: str | None = None) -> dict:
    """Read every mutable signal premise immediately before one exact PID signal."""
    began = time.monotonic()
    require(type(pid) is int and pid > 1, 'Invalid target PID')
    require(type(expected_uid) is int and expected_uid > 10000, 'Unprivileged target UID required')
    def observe(arguments, suffix):
        return observation_output(transport, arguments, label + '-' + suffix)
    require(observe(['shell', 'getenforce'], 'selinux').strip() == b'Enforcing',
            'SELinux no longer enforcing')
    kill_hash = observe(['shell', 'sha256sum', '/system/bin/kill'], 'kill-hash').decode().split()
    require(kill_hash == [expected_kill_sha, '/system/bin/kill'], 'Stock signal binary drift')
    start_command = ['exec-out', 'run-as', PACKAGE, 'cat', f'/proc/{pid}/stat']
    started = process_start_time(observe(start_command, 'starttime'), pid)
    def target_observation(suffix):
        pid_text = observe(['shell', 'pidof', PACKAGE], suffix + '-pid').decode('utf-8')
        cmdline = observe(['exec-out', 'run-as', PACKAGE, 'cat', f'/proc/{pid}/cmdline'], suffix + '-cmdline')
        campaign.kill_arguments(pid, pid_text, cmdline.decode('utf-8'))
        status = observe(['exec-out', 'run-as', PACKAGE, 'cat', f'/proc/{pid}/status'], suffix + '-status').decode('utf-8')
        uid = re.findall(r'^Uid:\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s*$', status, re.M)
        require(len(uid) == 1 and all(int(x) == expected_uid for x in uid[0]), 'IDENTITY_MISMATCH:target UID')
        return process_context(observe(['shell', '/system/bin/ps', '-ww', '-p', str(pid),
            '-o', 'PID,UID,LABEL,NAME'], suffix + '-context'), pid, expected_uid, expected_context)
    context = target_observation('initial')
    sender = observe(['shell', 'run-as', PACKAGE, 'id', '-u'], 'sender-uid').decode().strip()
    require(sender.isdecimal() and int(sender) == expected_uid, 'Sender/target UID mismatch')
    sender_context = observe(['shell', 'run-as', PACKAGE, 'cat', '/proc/self/attr/current'],
                            'sender-context').decode().strip('\x00\r\n ')
    require(re.fullmatch(r'u:r:runas_app:s0(?::c[0-9]+(?:,c[0-9]+)*)?', sender_context) is not None
            and (expected_sender_context is None
            or sender_context == expected_sender_context), 'Sender SELinux context drift')
    target_observation('final')
    require(process_start_time(observe(start_command, 'starttime-final'), pid) == started,
            'PROCESS_REPLACED:target starttime changed')
    elapsed = time.monotonic() - began
    require(elapsed < 10, 'OBSERVATION_UNAVAILABLE:identity observation exceeded ten seconds')
    return dict(targetPid=pid, targetUid=expected_uid, targetCmdline=PACKAGE,
                targetContext=context, senderUid=int(sender), senderContext=sender_context,
                selinux='Enforcing', killSha256=expected_kill_sha,
                targetStartTimeTicks=started, observationMethod='SHELL_PS_WIDE_EXACT_PID_V1',
                observationElapsedSeconds=elapsed)


def run_probe(root: Path, packet: dict, packet_sha: str, session: dict, output: Path) -> dict:
    """One bounded external-signal feasibility measurement; never campaign credit."""
    validate_admission(root, packet)
    precheck_payload(packet, 'PROBE', None)
    validate_expiry(session['expiresAtUtc']); validate_session_endpoint(packet, session)
    require(session.get('schema') == 'DORA_PHYSICAL_OWNED_SESSION_V1'
            and session.get('ownershipVerified') is True and session.get('payload') == 'PROBE'
            and session.get('packetSha256') == packet_sha and session.get('source') == packet['source'],
            'Probe session mismatch')
    receipt=read(verify_file(session['launcherReceipt']))
    require(receipt['packetSha256'] == packet_sha and receipt['payload'] == 'PROBE'
            and receipt['launcherSha256'] == packet['launcher']['sha256'], 'Probe receipt mismatch')
    require(not output.exists(), 'Probe identity consumed')
    output.mkdir(parents=True)
    transport=campaign.OwnedAdbTransport(verify_file(packet['adb']),session,output)
    nonce=uuid.uuid4().hex
    selector=PREFIX+'candidate.RecoveryExternalSigkillProbeInstrumentedTest#awaitExternalSignal'
    args=fixed_command(packet,'P35-01')
    args[args.index('class')+1]=selector
    flag=args.index('pocRecoveryE36GapiSupplementalSqlitePreflight')
    args[flag]='recoveryPhysicalSigkillProbe'
    args[-1:-1]=['-e','recoveryProbeNonce',nonce]
    result=dict(schema='DORA_PHYSICAL_SIGKILL_PROBE_V1', payload='PROBE',
        packetSha256=packet_sha, source=packet['source'], environment=packet['environment'],
        status='INCONCLUSIVE', kind='RUN_AS_EXACT_PID_SIGKILL', noRoot=True,
        signalExit=None, deathConfirmed=False, executed=False, result='INCONCLUSIVE',
        rawRetentionComplete=False, countedAsCoverage=False, boundedMeasurementComplete=False)
    process=None; reader=None
    try:
        transport.verify_device(packet['source'])
        require(transport.run(['shell','getenforce'],'selinux').stdout.strip()==b'Enforcing','SELinux not enforcing')
        result['selinux']='Enforcing'
        kill_sha=transport.run(['shell','sha256sum','/system/bin/kill'],'kill-sha').stdout.decode().split()
        require(len(kill_sha)==2 and kill_sha[1]=='/system/bin/kill','No stock kill binary')
        result['killSha256']=kill_sha[0]
        argv=transport.argv(args)
        campaign.save_new(output/'probe-command.json',dict(argv=argv,nonce=nonce,timeoutSeconds=45))
        lines=queue.Queue()
        with (output/'probe.stdout').open('xb') as stdout, (output/'probe.stderr').open('xb') as stderr:
            process=subprocess.Popen(argv,stdout=subprocess.PIPE,stderr=stderr,
                creationflags=getattr(subprocess,'CREATE_NO_WINDOW',0))
            result['executed']=True
            def capture():
                for line in iter(process.stdout.readline,b''):
                    stdout.write(line); stdout.flush(); lines.put(line)
                lines.put(None)
            reader=threading.Thread(target=capture,daemon=True); reader.start()
            deadline=time.monotonic()+20; ready=None; ready_at=None; start=[]
            while time.monotonic()<deadline:
                try: line=lines.get(timeout=max(.01,deadline-time.monotonic()))
                except queue.Empty: break
                if line is None: break
                start.append(line)
                marker=b'INSTRUMENTATION_STATUS: stream=PHYSICAL_SIGKILL_PROBE_READY '
                if line.startswith(marker):
                    ready_at=time.monotonic()
                    try: ready=json.loads(line[len(marker):].strip())
                    except (ValueError, UnicodeError) as error: result['controllerError']='Malformed READY: '+str(error)
                    break
            cls, method = selector.split('#')
            start_valid=(sum(line.startswith(f'INSTRUMENTATION_STATUS: class={cls}'.encode()) for line in start) == 1
                    and sum(line.startswith(f'INSTRUMENTATION_STATUS: test={method}'.encode()) for line in start) == 1
                    and sum(line.startswith(b'INSTRUMENTATION_STATUS_CODE: 1') for line in start) == 1)
            signal=None; signal_accepted=False
            try:
                require(ready is not None and start_valid, 'Probe READY or selector start missing')
                campaign.save_new(output/'probe-ready.json',ready)
                pid_text=transport.run(['shell','pidof',PACKAGE],'target-pid').stdout.decode()
                cmdline=transport.run(['exec-out','run-as',PACKAGE,'cat',f"/proc/{ready['pid']}/cmdline"],'target-cmdline').stdout
                validate_probe_target(ready,nonce,pid_text,cmdline)
                identity=live_signal_identity(transport, ready['pid'], ready['uid'], ready['selinuxContext'],
                                              result['killSha256'], 'probe-presignal')
                campaign.save_new(output/'probe-identity.json',identity)
                result.update(identity)
                require(ready_at is not None and time.monotonic()-ready_at < 15,
                        'OBSERVATION_UNAVAILABLE:READY too old for bounded probe')
                signal_command=['shell','run-as',PACKAGE,'/system/bin/kill','-9',str(ready['pid'])]
                result['signalCommand']=signal_command
                signal=transport.run(signal_command, 'probe-sigkill', require_success=False)
                result['signalExit']=signal.returncode
                result['signalStdoutHex']=signal.stdout.hex()
                result['signalStderrHex']=signal.stderr.hex()
                require(signal_succeeded(signal), 'SIGNAL_REJECTED:nonzero exit or diagnostic output')
                signal_accepted=True
            except (ValueError, OSError, subprocess.SubprocessError, KeyError, TypeError) as error:
                # A capability check can fail without killing the probe. Retain the
                # exact transport receipts and wait for the Android-side timeout.
                result['controllerError']=str(error)
                category=str(error).split(':',1)[0]
                result['failureStage']=category if category in ('OBSERVATION_UNAVAILABLE',
                    'IDENTITY_MISMATCH','PROCESS_REPLACED','SIGNAL_REJECTED') else 'IDENTITY_MISMATCH'
                receipts=sorted(output.glob('*.result.json'))
                if receipts:
                    native=read(receipts[-1])
                    stderr=receipts[-1].with_name(receipts[-1].name.replace('.result.json','.stderr')).read_bytes()
                    failed_stdout=receipts[-1].with_name(receipts[-1].name.replace('.result.json','.stdout')).read_bytes()
                    result['controllerFailureNativeExit']=native.get('nativeExit')
                    result['controllerFailureTimedOut']=native.get('timedOut')
                    result['controllerFailureStderrHex']=stderr.hex()
                    result['controllerFailureStdoutHex']=failed_stdout.hex()
                    result['observationDenied']=signal is None and re.search(
                        b'permission denied|operation not permitted', stderr+failed_stdout, re.I) is not None
            if signal_accepted:
                deadline=time.monotonic()+5
                while time.monotonic()<deadline:
                    death=transport.run(['shell','pidof',PACKAGE],'probe-death',require_success=False)
                    if death.returncode==1 and not death.stdout.strip() and not death.stderr.strip():
                        result['deathConfirmed']=True; break
                    time.sleep(.1)
            # Any unconfirmed signal or missing READY leaves the Android test to
            # reach its own 30-second timeout before the host client is reaped.
            process.wait(timeout=15 if result['deathConfirmed'] else 40)
            reader.join(timeout=3)
            require(not reader.is_alive(),'Probe output incomplete')
            result['instrumentationExit']=process.returncode
            transcript=(output/'probe.stdout').read_bytes()
            result['naturalTimeoutObserved']=b'PHYSICAL_SIGKILL_PROBE_TIMEOUT ' in transcript
            require(not result['deathConfirmed'] or not result['naturalTimeoutObserved'],
                    'DEATH_UNCONFIRMED:natural timeout cannot prove SIGKILL')
            marker=b'INSTRUMENTATION_STATUS: stream=PHYSICAL_SIGKILL_PROBE_READY '
            ready_count=sum(line.startswith(marker) for line in transcript.splitlines())
            require(ready_count <= 1 and (ready_count == 1 or ready is None),
                    'Probe READY duplicated or lost')
            require(result['deathConfirmed'] or b'PHYSICAL_SIGKILL_PROBE_TIMEOUT ' in transcript
                    or selected_completion(transcript, selector) == 'FAIL',
                    'Probe did not reach death or bounded natural timeout')
            require(transport.run(['shell','getenforce'],'selinux-after').stdout.strip()==b'Enforcing','SELinux changed')
            transport.verify_device(packet['source'])
            result['boundedMeasurementComplete']=True
            if signal_accepted and result['deathConfirmed']:
                result['status']='SUPPORTED'
            elif signal is not None and not signal_accepted and re.search(b'permission denied|operation not permitted',
                                                     signal.stderr + signal.stdout, re.I):
                require(b'PHYSICAL_SIGKILL_PROBE_TIMEOUT ' in transcript, 'Denied signal lacks natural probe timeout')
                result['status']='DENIED'
            else:
                result['status']='INCONCLUSIVE'
                if signal_accepted and not result['deathConfirmed']:
                    result['failureStage']='DEATH_UNCONFIRMED'
            result['result']='PASS' if result['status'] in ('SUPPORTED','DENIED','UNSUPPORTED') else 'INCONCLUSIVE'
    except (ValueError,OSError,subprocess.SubprocessError,KeyError) as error:
        result['error']=str(error)
        if str(error).startswith('DEATH_UNCONFIRMED:'):result['failureStage']='DEATH_UNCONFIRMED'
    finally:
        if process is not None and process.poll() is None:
            # Only this retained host ADB client, never the Android process or another service.
            process.kill(); process.wait(timeout=5)
        if reader is not None: reader.join(timeout=3)
        result['rawRetentionComplete']=reader is None or not reader.is_alive()
        result['evidence']=[descriptor(p) for p in sorted(output.glob('*')) if p.is_file()]
        campaign.save_new(output/'probe.json',result)
        campaign.save_new(output/'record.json',result)
    return result


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
    private = root.parent / 'launcher'
    required_private = [private / name for name in ('Invoke-Physical.ps1',
        'Attempt05-Lifecycle-Functions.ps1', 'Shutdown-Member-Resolution.ps1',
        'rec_i3_owned_process.psm1')]
    require(packet['launcher'] == descriptor(required_private[0]), 'Launcher descriptor mismatch')
    required = [Path(__file__), root / 'tools/recovery_campaign.py',
                root / 'tools/recovery_instrumentation_status.py', *required_private]
    for path in required:
        require(descriptor(path) in packet['files'], 'Runner/parser not bound')
    for value in packet['proofs'].values():
        require(value in packet['files'], 'Proof descriptor absent from packet files')
    require(packet['identityProof'] in packet['files'], 'Identity proof descriptor absent')
    proofs = {key: read(verify_file(value)) for key, value in packet['proofs'].items()}
    require(set(proofs) == {'ci', 'review', 'checks', 'signatures'}, 'Required proof set changed')
    require(all(proofs[key].get('source') == packet['source'] for key in proofs),
            'Proof source applicability mismatch')
    require(proofs['ci']['head_sha'] == packet['harnessSourceCommit'] and proofs['ci']['conclusion'] == 'success'
            and proofs['ci']['status'] == 'completed', 'Exact harness CI missing')
    require(proofs['review']['verdict'] == 'APPROVED' and proofs['review']['humanApprovalClaimed'] is False,
            'Independent technical review missing')
    for item in proofs['review']['files']: verify_file(item)
    require(all(descriptor(path) in proofs['review']['files'] for path in required),
            'Runner or launcher not technically reviewed')
    require(proofs['checks']['passed'] is True, 'Required local checks incomplete')
    for item in proofs['checks']['evidence']: verify_file(item)
    signatures = proofs['signatures']
    require(signatures['appSha256'] == APP_SHA and signatures['testSha256'] == packet['source']['testApkSha256']
            and signatures['appCertificateSha256'] == signatures['testCertificateSha256']
            and re.fullmatch(r'[0-9a-f]{64}', signatures['appCertificateSha256']) is not None
            and signatures['targetPackage'] == PACKAGE, 'APK target/signature mismatch')
    for item in signatures['evidence']: verify_file(item)
    require(packet['plan'] == build_plan(root, packet), 'API33 schedule/fixture/recipe drift')
    identity = read(verify_file(packet['identityProof']))
    require(identity['environment'] == packet['environment'] and identity['ownerAuthorized'] is True
            and identity.get('source') == packet['source'], 'Physical owner/measurement mismatch')
    require(packet['identityProof'] in packet['files'] and packet['identityProof'] in proofs['review']['files'], 'Identity proof not reviewed')
    if 'controllerReuse' in packet: controller_source(packet)


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


def selected_completion(output: bytes, selector: str) -> str:
    """One selected JUnit test reached a terminal PASS/FAIL; skip is never complete."""
    cls, method = selector.split('#')
    fields = {}
    phase = 0
    outcome = None
    terminal = False
    for line in output.decode('utf-8', errors='strict').splitlines():
        field = re.fullmatch(r'INSTRUMENTATION_STATUS: ([A-Za-z][A-Za-z0-9_]*)=(.*)', line)
        code = re.fullmatch(r'INSTRUMENTATION_STATUS_CODE: (-?[0-9]+)\s*', line)
        end = re.fullmatch(r'INSTRUMENTATION_CODE: (-?[0-9]+)\s*', line)
        if field:
            require(not terminal and field[1] not in fields, 'Duplicate or late JUnit status')
            fields[field[1]] = field[2]
        elif code:
            require(not terminal, 'Late JUnit code')
            if set(fields) == {'stream'}:
                require(phase == 1 and code[1] == '0', 'Observation outside live test')
            else:
                require(fields.get('class') == cls and fields.get('test') == method
                        and fields.get('numtests') == fields.get('current') == '1',
                        'Selected JUnit identity differs')
                if phase == 0:
                    require(code[1] == '1', 'Selected JUnit test did not start')
                    phase = 1
                else:
                    require(phase == 1 and code[1] in ('0', '-1', '-2'),
                            'Selected JUnit test skipped or duplicated')
                    outcome = 'PASS' if code[1] == '0' else 'FAIL'
                    phase = 2
            fields = {}
        elif end:
            require(not terminal and not fields and phase == 2 and end[1] == '-1',
                    'JUnit runner terminal incomplete')
            terminal = True
        elif line.startswith(('INSTRUMENTATION_STATUS', 'INSTRUMENTATION_CODE')):
            raise ValueError('Malformed JUnit status')
    require(terminal and outcome is not None, 'Selected JUnit terminal absent')
    return outcome


def fixed_completion(output: bytes, payload: str) -> str:
    require(payload in list(PAYLOADS)[:3], 'Unknown fixed payload')
    return selected_completion(output, PAYLOADS[payload][0])


def preflight_scoped_cleanup(transport, payload: str, output: bytes) -> dict:
    """Evidence about test-owned state, independent from the preflight result."""
    if payload == 'P35-01':
        # This test has no per-run namespace; its shared journal belongs to the
        # exactly task-installed pair and is removed by final package CLEANUP.
        return dict(verified=True, scope='OWNED_PAIR_SHARED_JOURNAL_NO_RUN_NAMESPACE')
    if payload == 'P35-02':
        listing = transport.run(['shell','run-as',PACKAGE,'ls','cache'], 'journal-cache-after').stdout.decode('utf-8')
        names = listing.splitlines()
        require(all(not name.startswith('rec-i3-sqlite-') for name in names),
                'Isolated journal cache namespace remains')
        return dict(verified=True, scope='ISOLATED_JOURNAL_CACHE', namespaceAbsent=True)
    require(payload == 'P35-03', 'Unknown preflight cleanup scope')
    marker='INSTRUMENTATION_STATUS: stream=INSTRUMENTATION_PLATFORM_PREREQUISITES_OBSERVATION '
    facts=[json.loads(line[len(marker):]) for line in output.decode('utf-8').splitlines()
           if line.startswith(marker)]
    require(len(facts) == 1 and facts[0]['keystore']['cleanupAliasAbsent'] is True
            and facts[0]['filesystem']['cleanupOwnedNamespaceAbsent'] is True,
            'Platform synthetic cleanup not independently observed')
    return dict(verified=True, scope='PLATFORM_SYNTHETIC_ALIAS_AND_NAMESPACE',
                cleanupAliasAbsent=True, cleanupOwnedNamespaceAbsent=True)


def fixed_command(packet: dict, payload: str) -> list[str]:
    selector, flag, _ = PAYLOADS[payload]
    env = packet['environment']
    args = ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', selector]
    values = dict(recoveryHarnessRevision=packet['harnessSourceCommit'], recoveryDeviceProfile='POCO-M5-PHYSICAL',
                  recoveryExpectedFingerprint=env['fingerprint'], recoveryExpectedProduct=env['product'],
                  recoveryExpectedApi=str(env['api']), recoveryExpectedAbi=env['abi'],
                  recoveryExpectedModel=env['model'], recoveryExpectedManufacturer=env['manufacturer'],
                  recoveryExpectedBuildType=env['buildType'], recoveryExpectedRelease=env['release'],
                  recoveryExpectedDevice=env['device'],
                  recoveryExpectedAppSha256=APP_SHA, recoveryExpectedTestSha256=packet['source']['testApkSha256'])
    if flag: values[flag] = 'true'
    for key, value in values.items(): args.extend(['-e', key, value])
    return args + [campaign.RUNNER]


def verify_observations(packet: dict, payload: str, observations: list[dict]) -> None:
    if payload == 'P35-01':
        require(observations[0] == observations[1], 'SQLite observation/status differ')
        value = observations[0]
        require(value['device']['sdk'] == packet['environment']['api'] and value['device']['fingerprint'] == packet['environment']['fingerprint'], 'SQLite environment drift')
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
    elif payload == 'P35-03':
        value = observations[0]
        require(value['status'] == 'PASS' and value['api'] == packet['environment']['api'] and value['deviceFingerprint'] == packet['environment']['fingerprint'], 'Platform prerequisites failed')
        require(value['targetApkSha256'] == APP_SHA and value['testApkSha256'] == packet['source']['testApkSha256']
                and value['harnessRevision'] == packet['harnessSourceCommit'], 'Platform source drift')
        require(value['keystore']['cleanupAliasAbsent'] is True and value['filesystem']['cleanupOwnedNamespaceAbsent'] is True, 'Platform synthetic cleanup incomplete')


def preflight_packet(packet: dict) -> dict:
    """Reuse exact predecessor evidence under a reviewed host-only source delta."""
    if 'preflightReuse' not in packet:
        return packet
    item = packet['preflightReuse']
    proof = read(verify_file(item))
    require(proof.get('schema') == 'DORA_PHYSICAL_PREFLIGHT_REUSE_V1'
            and proof.get('source') == packet['source']
            and proof.get('executionId') == packet['executionId']
            and proof.get('environment') == packet['environment'], 'Preflight reuse successor mismatch')
    prior_item = proof['priorPacket']
    require(prior_item['sha256'] == PREFLIGHT_PACKET_SHA, 'Unreviewed physical predecessor packet')
    prior = read(verify_file(prior_item))
    validate_contract(prior)
    require(prior['harnessSourceCommit'] == PREFLIGHT_COMMIT, 'Wrong physical predecessor source')
    require(prior['environment'] == packet['environment'], 'Preflight reuse environment drift')
    require(all(prior['source'][field] == packet['source'][field]
                for field in ('appApkSha256','testApkSha256')), 'Preflight reuse APK drift')
    review = read(verify_file(packet['proofs']['review']))
    required = [item,prior_item,proof['priorPrerequisites'],proof['reviewedDiff']]
    require(review['verdict'] == 'APPROVED' and all(value in packet['files'] and value in review['files']
                for value in required), 'Preflight reuse proof or reviewed inputs not pinned')
    for value in required: verify_file(value)
    root = Path(__file__).resolve().parents[1]
    git(root,'merge-base','--is-ancestor',PREFLIGHT_COMMIT,packet['harnessSourceCommit'])
    changed = set(git(root,'diff','--name-only',PREFLIGHT_COMMIT,packet['harnessSourceCommit']).splitlines())
    allowed = {'tools/recovery_physical.py','tools/test_recovery_physical.py',
               'tools/validate_recovery_0d6_candidate.py','tools/test_validate_recovery_0d6_candidate.py'}
    require(bool(changed) and changed <= allowed, 'Preflight reuse changed Android or contract inputs')
    diff = git(root,'diff','--binary','--no-ext-diff',PREFLIGHT_COMMIT,packet['harnessSourceCommit'])
    require(verify_file(proof['reviewedDiff']).read_text(encoding='utf-8') == diff,
            'Preflight reuse reviewed diff mismatch')
    before = ast.parse(git(root,'show',PREFLIGHT_COMMIT+':tools/recovery_physical.py'))
    after = ast.parse(Path(__file__).read_text(encoding='utf-8'))
    functions = {'validate_contract','validate_fixed_status','selected_completion','fixed_completion',
                 'preflight_scoped_cleanup','fixed_command','verify_observations'}
    constants = {'PAYLOADS','PREFIX','PACKAGE','APP_SHA','APP_COMMIT'}
    def semantics(module):
        result = {node.name:ast.dump(node,include_attributes=False) for node in module.body
                if isinstance(node,ast.FunctionDef) and node.name in functions}
        result.update({target.id:ast.dump(node.value,include_attributes=False) for node in module.body
                       if isinstance(node,ast.Assign) for target in node.targets
                       if isinstance(target,ast.Name) and target.id in constants})
        return result
    require(semantics(before) == semantics(after) and len(semantics(before)) == len(functions | constants),
            'Preflight implementation changed')
    return prior


def verify_preflights(packet: dict, prerequisites: list[dict]) -> None:
    require(len(prerequisites) == 3, 'Three physical preflights required')
    expected = preflight_packet(packet)
    if 'preflightReuse' in packet:
        proof = read(verify_file(packet['preflightReuse']))
        require(prerequisites == read(verify_file(proof['priorPrerequisites'])),
                'Preflight reuse evidence set changed')
    for payload, item in zip(list(PAYLOADS)[:3], prerequisites):
        record_path = verify_file(item['record']); terminal_path = verify_file(item['terminal'])
        raw_path = verify_file(item['instrumentationStdout'])
        require(record_path.parent.name == 'p' and terminal_path == record_path.parent.parent/'terminal.json'
                and raw_path.parent == record_path.parent, 'Mixed preflight evidence')
        record=read(record_path); terminal=read(terminal_path)
        require(terminal['record'] == item['record'], 'Preflight terminal record pin mismatch')
        if 'preflightReuse' in packet:
            require(record['packetSha256'] == PREFLIGHT_PACKET_SHA, 'Preflight predecessor packet drift')
        verify_observations(expected, payload, validate_fixed_status(raw_path.read_bytes(), payload))
        require(terminal['cleanupResult'] == 'VERIFIED' and terminal['failure'] is None
                and terminal['payload'] == payload and terminal['source'] == expected['source']
                and terminal['packetSha256'] == record['packetSha256'], 'Preflight cleanup incomplete')
        require(record['payload'] == payload and record['result'] == 'PASS' and record['executed'] is True
                and record['source'] == expected['source'] and record['environment'] == expected['environment']
                and record['rawRetentionComplete'] is True, 'Missing applicable physical preflight')


def k12_failures(entry: dict, observed: dict, replay: dict | None = None) -> list[str]:
    if entry.get('stratumId') != 'K12': return []
    replay = observed if replay is None else replay
    stream = entry['candidateId'] == campaign.CANDIDATES[0]
    expected = (8137,4056,8136) if stream else (360000,320000,320000)
    failures = []
    for value in (observed, replay):
        if any(type(value.get(key)) is not int or value[key] != exact for key, exact in zip(
                ('acceptedEnd', 'committedEnd', 'recoveredEnd'), expected)):
            failures.append('K12_EXACT_EXTENTS_MISMATCH')
        if type(value.get('processingIntentCount')) is not int \
                or value['processingIntentCount'] != (0 if stream else 2):
            failures.append('K12_INTENT_COUNT')
        if any(type(value.get(key)) is not int or value[key] != 0 for key in
               ('duplicateProcessingIntents', 'missingProcessingIntents', 'implicitCommitCount')):
            failures.append('K12_ZERO_COUNTERS')
    if stream:
        for value in (observed, replay):
            if (value.get('rangeStart'), value.get('rangeEnd'), value.get('rangeCertainty'),
                value.get('boundaryResult')) != (8192, 8193, 'EXACT_FORMAT_BOUNDARY', 'EXACT_FORMAT_BOUNDARY'):
                failures.append('K12_RANGE')
            if value.get('sourceUnchanged') is not True or any(
                    type(value.get(key)) is not int or value[key] != exact
                    for key, exact in [('sourceBytes', 8192), ('preFaultSourceBytes', 8192),
                                       ('currentSourceBytes', 8193), ('observedSourceBytes', 8193)]):
                failures.append('K12_SOURCE_CHANGED')
            proof = value.get('streamPersistenceObservation') or {}
            if not isinstance(proof, dict) or any(type(proof.get(key)) is not int or proof[key] != expected
                    for key, expected in [('sealedValidOutcomes', 1), ('activeRanges', 1),
                                          ('activeRangeStart', 8192), ('activeRangeEnd', 8193)]) \
                    or proof.get('activeRangeCertainty') != 'EXACT_FORMAT_BOUNDARY' \
                    or not isinstance(proof.get('persistedStateDigest'), str) \
                    or re.fullmatch(r'[0-9a-f]{64}', proof['persistedStateDigest']) is None:
                failures.append('K12_STREAM_PERSISTENCE_READBACK')
        if observed.get('streamPersistenceObservation') != replay.get('streamPersistenceObservation'):
            failures.append('K12_STREAM_PERSISTENCE_REPLAY_DRIFT')
    else:
        for value in (observed, replay):
            quarantine = value.get('quarantineObservation') or {}
            if quarantine != dict(rowCount=1, completedCount=1, uniqueIntentCount=1,
                                  sourceItemCount=0, destinationItemCount=1,
                                  terminalStates=['COMPLETED']):
                failures.append('K12_MICROFILE_QUARANTINE_STATE')
        if observed.get('quarantineObservation') != replay.get('quarantineObservation'):
            failures.append('K12_MICROFILE_QUARANTINE_REPLAY_DRIFT')
    if not observed.get('receiptIdentity') or observed.get('repeatStable') is not True: failures.append('K12_REPLAY')
    return failures


def assess(entry: dict, result: dict) -> dict:
    assessment = campaign.evaluate_attempt(entry, result)
    observed = result.get('candidateResult') or {}
    if entry['kind'] == 'NORMAL' and result['status'] == 'VALID':
        full = all(observed.get(k) == entry['plaintextBytes'] for k in ('acceptedEnd', 'recoveredEnd'))
        if entry['candidateId'] == campaign.CANDIDATES[0]: full = full and observed.get('streamTerminal') == 'AUTHENTICATED_EOF'
        if not full:
            assessment['failures'].append('NORMAL_FULL_EXTENT_NOT_RECOVERED'); assessment['verdict'] = 'FAIL'
    if result['status'] == 'VALID':
        failures = k12_failures(entry, observed, result.get('candidateReplay') or {})
        assessment['failures'].extend(failures)
        if failures: assessment['verdict'] = 'FAIL'
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


def run_physical_kill(transport, plan: dict, entry: dict, variant: str, controller: dict) -> dict:
    require(controller['kind'] == 'RUN_AS_EXACT_PID_SIGKILL', 'Unsupported physical controller')
    signal_receipts = []
    # Starting before instrumentation makes this a conservative upper bound on
    # barrier age, including queued output and the inherited initial PID reads.
    began = time.monotonic()
    class ExactSignalTransport:
        def __getattr__(self,name): return getattr(transport,name)
        def run(self,arguments,label,*args,**kwargs):
            if label == 'sigkill':
                require(time.monotonic() - began < 90, 'BARRIER_EXPIRED:attempt exceeded dispatch budget')
                require(arguments[:5] == ['shell','run-as',PACKAGE,'kill','-9'] and len(arguments) == 6, 'Unexpected kill command')
                pid = int(arguments[5])
                identity = live_signal_identity(transport, pid, controller['targetUid'],
                    controller['targetContext'], controller['killSha256'], 'presignal',
                    controller['senderContext'])
                campaign.save_new(transport.directory / 'physical-presignal.json', identity)
                require(time.monotonic() - began < 90, 'BARRIER_EXPIRED:identity exceeded dispatch budget')
                arguments = ['shell','run-as',PACKAGE,'/system/bin/kill','-9',arguments[5]]
                signal = transport.run(arguments,label,*args,timeout=5,require_success=False)
                signal_receipts.append(signal)
                require(signal_succeeded(signal), 'SIGNAL_REJECTED:nonzero exit or diagnostic output')
                elapsed = time.monotonic() - began
                require(elapsed < 100, 'BARRIER_EXPIRED:signal completion exceeded budget')
                campaign.save_new(transport.directory / 'physical-dispatch.json',
                    dict(attemptElapsedSeconds=elapsed,dispatchBudgetSeconds=90,barrierLifetimeSeconds=120))
                return signal
            if label == 'death-confirmation':
                result = transport.run(arguments,label,*args,**kwargs)
                live = result.returncode == 0 and re.fullmatch(rb'[1-9][0-9]*',result.stdout.strip()) is not None
                absent = result.returncode == 1 and not result.stdout.strip()
                require(not result.stderr.strip() and (live or absent), 'DEATH_OBSERVATION_UNAVAILABLE')
                return result
            return transport.run(arguments,label,*args,**kwargs)
    proofs = campaign.run_kill(ExactSignalTransport(),plan,entry,variant)
    require(len(signal_receipts) == 1, 'Exact one signal required')
    output = (transport.directory / 'kill-instrument.stdout').read_bytes()
    events = campaign.parse_events(output,entry,'WRITE_UNTIL_BARRIER')
    require(not any(event['eventType'] in ('ERROR','RESULT') for event in events)
            and b'External controller did not kill the paused process' not in output
            and not re.search(rb'^INSTRUMENTATION_CODE: -1\s*$',output,re.M),
            'BARRIER_COMPLETED:timeout, error or graceful test completion')
    require(transport.run(['shell','getenforce'], 'selinux-after').stdout.strip() == b'Enforcing', 'SELinux changed')
    return proofs


def run_payload(root: Path, packet: dict, packet_sha: str, session: dict, payload: str, output: Path,
                prerequisites: list[dict]) -> dict:
    validate_admission(root, packet)
    require(payload in PAYLOADS, 'Payload outside nine-check scope')
    precheck_payload(packet, payload, prerequisites)
    require(session.get('schema') == 'DORA_PHYSICAL_OWNED_SESSION_V1' and session.get('ownershipVerified') is True, 'Owned session required')
    require(session.get('packetSha256') == packet_sha and session.get('payload') == payload
            and session.get('source') == packet['source'], 'Session authority mismatch')
    validate_expiry(session['expiresAtUtc']); validate_session_endpoint(packet, session)
    receipt = read(verify_file(session['launcherReceipt']))
    require(receipt['packetSha256'] == packet_sha and receipt['payload'] == payload
            and receipt['launcherSha256'] == packet['launcher']['sha256'], 'Launcher receipt mismatch')
    verify_file(packet['launcher'])
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
        for prop, expected in [('ro.build.version.sdk', str(packet['environment']['api'])), ('ro.product.name', packet['environment']['product']),
                               ('ro.product.cpu.abi', packet['environment']['abi']),
                               ('ro.product.model', packet['environment']['model']),
                               ('ro.product.manufacturer', packet['environment']['manufacturer']),
                               ('ro.build.type', packet['environment']['buildType']),
                               ('ro.build.version.release', packet['environment']['release']),
                               ('ro.product.device', packet['environment']['device'])]:
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
            record['preflightCompletion'] = fixed_completion(completed.stdout, payload)
            cleanup_scope = preflight_scoped_cleanup(transport, payload, completed.stdout)
            campaign.save_new(output / 'preflight-scoped-cleanup.json', cleanup_scope)
            record['preflightScopedCleanupVerified'] = cleanup_scope['verified'] is True
            require(record['preflightCompletion'] == 'PASS', 'Selected preflight completed with failure')
            observations = validate_fixed_status(completed.stdout, payload)
            campaign.save_new(output / 'observations.json', dict(observations=observations))
            verify_observations(packet, payload, observations)
            record.update(result='PASS', cleanupResult='RUN_SCOPED_VERIFIED_PACKAGES_RETAINED')
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
                result['kill'] = run_physical_kill(transport, plan, entry, variant, read(verify_file(packet['controllerProof'])))
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
            result.update(candidateResult=observed, candidateReplay=replay, hostOracleEqual=equal,
                          hostReplayOracleEqual=replay_equal)
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
        if record['executed'] and record['rawRetentionComplete'] and payload in list(PAYLOADS)[:3] \
                and record.get('preflightCompletion') in ('PASS','FAIL'):
            record['result'] = 'FAIL'
            if record.get('preflightScopedCleanupVerified') is True:
                record['cleanupResult'] = 'RUN_SCOPED_VERIFIED_PACKAGES_RETAINED'
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
    parser.add_argument('--payload', choices=[*PAYLOADS,'PROBE','CLEANUP'], required=True)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--prerequisites', type=Path)
    args = parser.parse_args()
    packet = read(verify_file(dict(path=str(args.packet.resolve()), sha256=args.packet_sha256)))
    root = Path(__file__).resolve().parents[1]
    validate_admission(root, packet)
    prerequisites = read(args.prerequisites) if args.prerequisites else None
    precheck_payload(packet, args.payload, prerequisites)
    if args.mode == 'check': print('PHYSICAL_ADMISSION_CHECKED_NO_DEVICE_OPERATIONS'); return 0
    require(args.session is not None and args.payload is not None and args.output is not None, 'Execution arguments missing')
    require(args.payload != 'CLEANUP', 'Cleanup is host only')
    if args.payload == 'PROBE':
        result=run_probe(root,packet,args.packet_sha256,read(args.session),args.output)
    else:
        result = run_payload(root, packet, args.packet_sha256, read(args.session), args.payload, args.output,
                             prerequisites)
    print(json.dumps(result)); return 0 if result['result'] == 'PASS' else 2


if __name__ == '__main__': raise SystemExit(main())
