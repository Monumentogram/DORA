"""POCO LITE operator. Private configuration stays outside Git; no battery measurement.

Screen preflight is diagnostic only. Campaign admission requires sealed protocol and clean
source; unimplemented/missing acceptance phases cannot produce a terminal PASS.
"""
import argparse
import ctypes
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parent / 'poco_non_battery'))
from alpha_protocol import keyguard_action, verify_seal

PACKAGE = 'com.monumentogram.dora.debug'
HELPER = 'com.monumentogram.dora.stage86b.driver'
SERVICE = 'ProductRecordingService'


def write_json(path, value):
    temporary = path.with_suffix(path.suffix + '.tmp')
    with temporary.open('w', encoding='utf-8') as out:
        json.dump(value, out, indent=2)
        out.write('\n'); out.flush(); os.fsync(out.fileno())
    deadline = time.monotonic() + 2
    while True:
        try:
            os.replace(temporary, path)
            break
        except PermissionError as error:
            # Windows readers may briefly deny FILE_SHARE_DELETE. Preserve the old
            # complete receipt until atomic replacement succeeds; never truncate it.
            if os.name != 'nt' or getattr(error, 'winerror', None) not in (5, 32) or time.monotonic() >= deadline:
                raise
            time.sleep(.02)


def instrumentation_progress(device, process, run):
    # Observe process exit before reading its final atomically published receipt.
    # The reverse order can mistake a stale progress snapshot for a missing terminal.
    exited = process.poll() is not None
    row = device.receipt(run)
    if exited and (not row or row.get('phase') not in ('COMPLETE', 'FAILED')):
        raise RuntimeError('INSTRUMENTATION_EXITED_WITHOUT_TERMINAL')
    return row


class Device:
    def __init__(self, config):
        self.config = config

    def call(self, *args, timeout=15, check=True, input=None):
        result = subprocess.run([self.config['adb'], '-s', self.config['serial'], *args],
                                input=input, capture_output=True, timeout=timeout)
        if check and result.returncode:
            raise RuntimeError('ADB_COMMAND_FAILED_OUTPUT_WITHHELD')
        return result

    def text(self, *args, **kwargs):
        return self.call(*args, **kwargs).stdout.decode('utf-8', errors='replace').strip()

    def ready_screen(self):
        raw = self.text('shell', 'dumpsys', 'window', 'policy')
        def flag(name):
            values = re.findall(r'^\s*' + name + r'=(true|false)\s*$', raw, re.M)
            if len(values) != 1:
                raise RuntimeError('KEYGUARD_STATE_AMBIGUOUS')
            return values[0] == 'true'
        action = keyguard_action(flag('secure'), flag('showing'))
        self.call('shell', 'input', 'keyevent', '224')
        if action == 'DISMISS_NONSECURE':
            self.call('shell', 'wm', 'dismiss-keyguard')
        # Recheck after standard wake/dismiss; never inject a PIN, swipe or change settings.
        end = time.monotonic() + 5
        while time.monotonic() < end:
            current = self.text('shell', 'dumpsys', 'window', 'policy')
            if re.search(r'^\s*showing=false\s*$', current, re.M):
                return {'nonsecure': True, 'showing': False, 'securitySettingsChanged': False}
            time.sleep(.2)
        raise RuntimeError('AUTONOMOUS_SCREEN_OFF_CONTROL_UNAVAILABLE')

    def installed_sha(self, package):
        path = self.text('shell', 'pm', 'path', package)
        if not path.startswith('package:/') or '\n' in path:
            raise RuntimeError('PACKAGE_LAYOUT_UNEXPECTED')
        return self.text('shell', 'sha256sum', path[8:]).split()[0]

    def receipt(self, run):
        result = self.call('exec-out', 'run-as', PACKAGE, 'cat',
                           f'no_backup/stage86b-receipts/{run}.json', check=False)
        if result.returncode:
            return None
        try:
            return json.loads(result.stdout)
        except (ValueError, UnicodeError):
            return None

    def signal(self, run, suffix, content):
        if not re.fullmatch('[a-z0-9-]{3,48}', run) or suffix not in ('heartbeat', 'abort'):
            raise ValueError('SIGNAL_PATH')
        if not re.fullmatch('[0-9]{1,18}', content):
            raise ValueError('SIGNAL_CONTENT')
        path = f'no_backup/stage86b-receipts/{run}.{suffix}'
        # adb exec-out does not provide the required stdin EOF semantics on this host.
        # Only validated decimal content and fixed relative paths enter the quoted shell.
        command = f"printf '%s' {content} > {path}.tmp && mv {path}.tmp {path}"
        return self.call('shell', 'run-as', PACKAGE, 'sh', '-c', shlex.quote(command),
                         timeout=5, check=False)

    def safe_state(self):
        service = self.text('shell', 'dumpsys', 'activity', 'services', PACKAGE)
        appop = self.text('shell', 'cmd', 'appops', 'get', PACKAGE, 'RECORD_AUDIO')
        return {'fgsAbsent': SERVICE not in service,
                'micNotRunning': '(running)' not in appop and 'running=true' not in appop}

    def power_context(self):
        raw = self.text('shell', 'dumpsys', 'battery')
        values = {}
        for field in ('AC powered', 'USB powered', 'Wireless powered', 'status', 'temperature'):
            matches = re.findall(r'^\s*' + re.escape(field) + r':\s*(true|false|[0-9]+)\s*$', raw, re.M)
            if len(matches) != 1:
                raise RuntimeError('POWER_CONTEXT_UNAVAILABLE')
            values[field] = matches[0] == 'true' if matches[0] in ('true', 'false') else int(matches[0])
        values['deviceUptimeSeconds'] = self.text('shell', 'cat', '/proc/uptime').split()[0]
        values['purpose'] = 'USB_POWER_CONTEXT_ONLY_NO_ENERGY_MEASUREMENT'
        return values

    def confirmed_shutdown(self, run):
        safe = self.safe_state()
        receipt = self.receipt(run)
        watchdog = self.receipt(run + '-watchdog')
        settled = (watchdog is not None and watchdog.get('state') in ('CLOSED_SAFE', 'STOP_CONFIRMED')
                   and watchdog.get('pendingStartCancelled') is True)
        never_started = (receipt is not None and receipt.get('phase') == 'FAILED'
                         and 'activeAttempt' not in receipt and 'invocationTokenFingerprint' not in receipt)
        return {**safe, 'pendingStartExcluded': settled or never_started}

    def kill_owned_for_recovery(self, row):
        if (row.get('mode') != 'recovery-seed' or row.get('run') != 'lite-functional-recovery-seed-01'
                or row.get('phase') != 'RECOVERY_KILL_READY'
                or type(row.get('pid')) is not int or row['pid'] <= 0
                or type(row.get('durableFramesBeforeKill')) is not int or row['durableFramesBeforeKill'] <= 0
                or any(not re.fullmatch('[a-f0-9]{64}', row.get(k, ''))
                       for k in ('activeCampaignIdentity', 'invocationTokenFingerprint'))):
            raise ValueError('RECOVERY_KILL_AUTHORITY_MISSING')
        pid = str(row['pid'])
        if self.text('shell', 'pidof', PACKAGE, check=False) != pid:
            raise ValueError('RECOVERY_KILL_PID_MISMATCH')
        self.call('shell', 'run-as', PACKAGE, 'kill', '-9', pid, check=False)
        deadline = time.monotonic() + 10
        while time.monotonic() < deadline:
            if not self.text('shell', 'pidof', PACKAGE, check=False):
                safe = self.safe_state()
                if all(safe.values()):
                    return {**safe, 'pendingStartExcluded': True, 'processAbsenceConfirmed': True}
            time.sleep(.2)
        raise RuntimeError('RECOVERY_KILL_ABSENCE_UNPROVEN')


def watch(config, output, run):
    """Separate host process; device thread is the second stop path if ADB disappears."""
    device = Device(config); previous_failure = None; shutdown_deadline = None
    if os.name == 'nt':
        if not ctypes.windll.kernel32.SetThreadExecutionState(0x80000001):
            raise RuntimeError('HOST_AWAKE_LEASE_FAILED')
    try:
        write_json(output / f'{run}-host-watchdog.json',
                   {'state': 'ARMED', 'awakeLeaseHeld': os.name == 'nt'})
        while True:
            state = json.loads((output / f'{run}-supervisor.json').read_text())
            if state['terminal']:
                write_json(output / f'{run}-host-watchdog.json',
                           {'state': 'CLOSED_SAFE' if state.get('safeShutdownConfirmed') else 'DEVICE_SAFE_STATE_UNCONFIRMED',
                            'reason': previous_failure, 'awakeLeaseHeld': os.name == 'nt'})
                return
            failure = None
            if time.monotonic() - state['heartbeat'] > 30:
                failure = 'HOST_HEARTBEAT_EXPIRED'
            elif time.monotonic() > state['deadline']:
                failure = 'HARD_DEADLINE'
            try:
                if device.text('get-state', timeout=5) != 'device':
                    failure = 'ADB_UNAVAILABLE'
            except Exception:
                failure = 'ADB_UNAVAILABLE'
            if failure or previous_failure:
                if previous_failure is None:
                    previous_failure = failure; shutdown_deadline = time.monotonic() + 60
                write_json(output / f'{run}-host-watchdog.json', {'state': 'ABORT_REQUESTED', 'reason': failure})
                try:
                    device.signal(run, 'abort', '1')
                    safe = device.confirmed_shutdown(run)
                    if all(safe.values()):
                        write_json(output / f'{run}-host-watchdog.json',
                                   {'state': 'STOP_CONFIRMED', 'reason': previous_failure, 'safeState': safe})
                        return
                except Exception:
                    pass
                if time.monotonic() >= shutdown_deadline:
                    write_json(output / f'{run}-host-watchdog.json',
                               {'state': 'DEVICE_SAFE_STATE_UNCONFIRMED', 'reason': previous_failure})
                    return
            time.sleep(2)
    finally:
        if os.name == 'nt':
            ctypes.windll.kernel32.SetThreadExecutionState(0x80000000)


def preflight(config, output, run):
    device = Device(config)
    if device.text('get-state') != 'device':
        raise RuntimeError('DEVICE_UNAVAILABLE')
    expected = {'ro.product.model': '22071219CG', 'ro.build.version.sdk': '34',
                'ro.product.cpu.abi': 'arm64-v8a'}
    for prop, value in expected.items():
        if device.text('shell', 'getprop', prop) != value:
            raise RuntimeError('DEVICE_PROFILE_CHANGED')
    for package, sha in [(PACKAGE, config['productApkSha256']), (HELPER, config['helperApkSha256'])]:
        if device.installed_sha(package) != sha:
            raise RuntimeError('APK_IDENTITY_MISMATCH')
    safe = device.safe_state()
    if not all(safe.values()):
        raise RuntimeError('PREEXISTING_RECORDING_OR_MICROPHONE')
    screen = device.ready_screen()
    write_json(output / f'{run}-host-preflight.json',
               {'adb': 'AUTHORIZED', 'device': expected, 'apkVerified': True, 'safeState': safe,
                'screen': screen, 'batteryExperiments': False, 'fullCampaignAdmitted': False})
    return device


def admit_run(config, run, mode):
    if mode == 'lite-screen-smoke' and re.fullmatch('lite-screen-smoke-[0-9]{2}', run):
        return {'attempts': 1, 'hardDeadlineSeconds': 240}
    protocol = config.get('protocol', {})
    verify_seal(protocol, config.get('protocolSha256'))
    for key in ('productApkSha256', 'helperApkSha256', 'ownerMapSha256'):
        if protocol.get(key) != config.get(key):
            raise ValueError('CAMPAIGN_IDENTITY_MISMATCH')
    entry = protocol.get('runs', {}).get(run)
    if not entry or entry.get('mode') != mode or mode not in ('lite-cycles', 'lite-long', 'notification', 'permission', 'storage-ui', 'failure-inspect', 'recovery-seed', 'recovery-resume'):
        raise ValueError('CAMPAIGN_RUN_NOT_PREDECLARED')
    if entry.get('attempts') != (60 if mode == 'lite-cycles' else 1):
        raise ValueError('CAMPAIGN_DENOMINATOR_CHANGED')
    return entry


def execute(config, output, run, mode, config_path):
    admitted = admit_run(config, run, mode)
    device = preflight(config, output, run)
    if device.receipt(run) is not None or (output / f'{run}-supervisor.json').exists():
        raise RuntimeError('RUN_EXISTS_NO_OVERWRITE')
    state_path = output / f'{run}-supervisor.json'
    state = {'run': run, 'heartbeat': time.monotonic(),
             'deadline': time.monotonic() + admitted['hardDeadlineSeconds'], 'terminal': False}
    write_json(state_path, state)
    guardian = subprocess.Popen([sys.executable, __file__, '--config', str(config_path),
                                 '--output', str(output), '--run', run, '--watchdog'],
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    terminal = None; last = None; launches = set(); sequence = 0; safe = None
    collected = admitted.get('firstAttempt', 1) - 1
    last_power = 0; power_samples = []
    try:
        ready_deadline = time.monotonic() + 5
        while time.monotonic() < ready_deadline:
            status_file = output / f'{run}-host-watchdog.json'
            if status_file.exists():
                ready = json.loads(status_file.read_text())
                if ready.get('state') == 'ARMED' and ready.get('awakeLeaseHeld'):
                    break
            if guardian.poll() is not None:
                raise RuntimeError('HOST_WATCHDOG_START_FAILED')
            time.sleep(.1)
        else:
            raise RuntimeError('HOST_WATCHDOG_AWAKE_LEASE_UNPROVEN')
        with (output / f'{run}.local.log').open('xb') as log:
            continuation = []
            if config.get('protectedPolicySha256'):
                pin = config['protectedPolicySha256']
                if not re.fullmatch('[a-f0-9]{64}', pin):
                    raise ValueError('PROTECTED_POLICY_PIN_INVALID')
                continuation.extend(['-e', 'protectedPolicySha256', pin])
            if admitted.get('firstAttempt', 1) > 1:
                for key in ('firstAttempt', 'continuationOf', 'continuationReceiptSha256'):
                    continuation.extend(['-e', key, str(admitted[key])])
            if mode in ('recovery-resume', 'failure-inspect'):
                for key in ('referenceRun', 'referenceReceiptSha256'):
                    continuation.extend(['-e', key, str(admitted[key])])
            instrumentation = ('.FailureInspectionInstrumentation' if mode == 'failure-inspect' else
                               '.CampaignInstrumentation' if mode.startswith('lite-') else '.FunctionalInstrumentation')
            process = subprocess.Popen([config['adb'], '-s', config['serial'], 'shell', 'am', 'instrument', '-w',
                '-e', 'run', run, '-e', 'mode', mode, '-e', 'expectedApkSha256', config['productApkSha256'],
                '-e', 'ownerMapSha256', config['ownerMapSha256'], *continuation, HELPER + '/' + instrumentation],
                stdout=log, stderr=log)
        while time.monotonic() < state['deadline']:
            if guardian.poll() is not None:
                raise RuntimeError('HOST_WATCHDOG_EXITED')
            state['heartbeat'] = time.monotonic(); write_json(state_path, state)
            row = instrumentation_progress(device, process, run)
            if row:
                sequence += 1
                device.signal(run, 'heartbeat', str(sequence))
                write_json(output / f'{run}-progress.json', row)
                if mode == 'lite-long' and time.monotonic() - last_power >= 30:
                    context = device.power_context()
                    power_samples.append(context)
                    write_json(output / f'{run}-power-context.json', power_samples)
                    last_power = time.monotonic()
                    if context['USB powered'] is not True:
                        raise RuntimeError('USB_POWER_CONFIGURATION_CHANGED')
                completed = row.get('completedAttempts', 0)
                while collected < completed:
                    number = collected + 1
                    attempt = device.receipt(f'{run}-{number:03}')
                    if not attempt:
                        raise RuntimeError('COMPLETED_ATTEMPT_RECEIPT_MISSING')
                    write_json(output / f'{run}-{number:03}.json', attempt)
                    collected = number
                    print(json.dumps({'run': run, 'completedAttempts': collected}), flush=True)
                phase = row['phase']
                if phase != last:
                    print(json.dumps({'run': run, 'phase': phase}), flush=True); last = phase
                if phase in ('HOST_LAUNCH_READY', 'POST_WINDOW_HOST_LAUNCH_READY') and phase not in launches:
                    device.ready_screen()
                    device.call('shell', 'am', 'start', '-n', PACKAGE + '/com.monumentogram.dora.MainActivity')
                    launches.add(phase)
                if phase == 'RECOVERY_KILL_READY' and mode == 'recovery-seed':
                    safe = device.kill_owned_for_recovery(row)
                    terminal = {**row, 'phase': 'EXPECTED_PROCESS_DEATH_CONFIRMED', 'hostKill': safe}
                    write_json(output / f'{run}.json', terminal)
                    break
                if phase in ('COMPLETE', 'FAILED'):
                    terminal = row
                    write_json(output / f'{run}.json', row)
                    for number in range(collected + 1, admitted['attempts'] + 1):
                        state['heartbeat'] = time.monotonic(); write_json(state_path, state)
                        sequence += 1; device.signal(run, 'heartbeat', str(sequence))
                        attempt = device.receipt(f'{run}-{number:03}')
                        if attempt:
                            write_json(output / f'{run}-{number:03}.json', attempt)
                    break
            time.sleep(1)
        if terminal is None:
            raise RuntimeError('INSTRUMENTATION_HARD_DEADLINE')
        if terminal['phase'] != 'COMPLETE' and not (mode == 'recovery-seed' and terminal['phase'] == 'EXPECTED_PROCESS_DEATH_CONFIRMED'):
            raise RuntimeError('DIAGNOSTIC_FAILED_' + terminal.get('errorCode', 'UNKNOWN'))
        if mode != 'recovery-seed':
            safe = device.confirmed_shutdown(run)
        if not all(safe.values()):
            raise RuntimeError('DEVICE_SAFE_STATE_UNCONFIRMED')
        print(json.dumps({'run': run, 'diagnostic': 'COMPLETE', 'safeState': safe,
                          'ownerRecordingsPreserved': terminal.get('ownerInventoryUnchanged')}), flush=True)
    except Exception as error:
        try:
            device.signal(run, 'abort', '1')
        except Exception:
            pass
        try:
            safe = device.confirmed_shutdown(run)
        except Exception:
            safe = None
        write_json(output / f'{run}-failure.json',
                   {'reason': str(error) if re.fullmatch('[A-Z0-9_]+', str(error)) else type(error).__name__,
                    'winerror': getattr(error, 'winerror', None), 'errno': getattr(error, 'errno', None),
                    'safeState': safe, 'unverifiedAudioDeleted': False})
        raise
    finally:
        # Keep independent supervision active while a failed/pending Start is settling.
        shutdown_deadline = time.monotonic() + 60
        while not (safe and all(safe.values())) and time.monotonic() < shutdown_deadline:
            try:
                device.signal(run, 'abort', '1')
                safe = device.confirmed_shutdown(run)
            except Exception:
                safe = None
            if safe and all(safe.values()):
                break
            time.sleep(1)
        state['safeShutdownConfirmed'] = bool(safe and all(safe.values()))
        state['terminal'] = True; state['heartbeat'] = time.monotonic(); write_json(state_path, state)
        write_json(output / f'{run}-shutdown.json',
                   {'state': 'SAFE_CONFIRMED' if state['safeShutdownConfirmed'] else 'DEVICE_SAFE_STATE_UNCONFIRMED',
                    'safeState': safe, 'unverifiedAudioDeleted': False})
        try:
            guardian.wait(timeout=10)
        except subprocess.TimeoutExpired:
            guardian.terminate(); guardian.wait(timeout=5)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--config', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--run', required=True)
    parser.add_argument('--mode', default='lite-screen-smoke', choices=('lite-screen-smoke', 'lite-cycles', 'lite-long', 'notification', 'permission', 'storage-ui', 'failure-inspect', 'recovery-seed', 'recovery-resume'))
    parser.add_argument('--watchdog', action='store_true')
    args = parser.parse_args()
    config = json.loads(args.config.read_text(encoding='utf-8'))
    args.output.mkdir(parents=True, exist_ok=True)
    if args.watchdog:
        watch(config, args.output, args.run)
    else:
        execute(config, args.output, args.run, args.mode, args.config)


if __name__ == '__main__':
    try:
        main()
    except Exception as failure:
        # subprocess exceptions embed private device selectors/paths; never echo them.
        reason = str(failure) if re.fullmatch('[A-Z0-9_]+', str(failure)) else type(failure).__name__
        print(json.dumps({'terminal': 'BLOCKED', 'reason': reason}), flush=True)
        raise SystemExit(1)
