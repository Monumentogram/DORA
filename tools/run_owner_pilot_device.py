"""Synthetic secondary-user owner pilot tests. All mutations require an explicit verified emulator."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import time
import xml.etree.ElementTree as ET

from isolated_alpha_preflight import require
from run_isolated_alpha_device import emulator_fence, created_user, switch_complete, failure_caller_state
from run_encrypted_persistence_device import parse_results
from owner_alpha_pilot_artifact import check_badging

PACKAGE = "com.monumentogram.dora.alpha.pilot"

CLASS = 'com.monumentogram.dora.ownerpilot.OwnerPilotInstrumentedTest'
RUNNER = PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner'
ROOT = Path(__file__).resolve().parents[1]

PHASES = {
    'owner_initial': ('noSystemCredentialCannotOpenVault', 'packageManifestAndReleaseOverridesAreClosed', 'seedOwnerIsolationSentinel'),
    'secondary': ('realSecondaryRuntimeAuthenticatedReadbackDeletionAndReopen', 'packageManifestAndReleaseOverridesAreClosed', 'secondaryCannotReadOwnerSentinel'),
    'owner_final': ('verifyOwnerIsolationSentinelUnchanged',),
}


def validate_inventory(contract):
    expected = {phase: [CLASS + '#' + name for name in methods] for phase, methods in PHASES.items()}
    require(contract.get('schema_version') == 1 and contract.get('package') == PACKAGE and
            contract.get('phases') == expected, 'PILOT_INVENTORY_MISMATCH')
    return expected


def source_binding(command, expected=None):
    source = command('rev-parse', 'HEAD').strip()
    require(re.fullmatch(r'[a-f0-9]{40}', source) is not None, 'PILOT_SOURCE_SHA_INVALID')
    require(expected is None or source == expected, 'PILOT_SOURCE_SHA_MISMATCH')
    require(not command('status', '--porcelain=v1', '--untracked-files=all').strip(),
            'PILOT_SOURCE_NOT_CLEAN')
    return {'source_sha': source, 'source_clean': True}


def git_command(*arguments):
    try:
        result = subprocess.run(['git', *arguments], cwd=ROOT, capture_output=True,
                                text=True, timeout=30)
    except Exception:
        raise ValueError('PILOT_SOURCE_CHECK_FAILED') from None
    require(result.returncode == 0, 'PILOT_SOURCE_CHECK_FAILED')
    return result.stdout


def exact_results(output, selected):
    try:
        result = parse_results(output)
        require({row['name'] for row in result} == selected, 'INVENTORY_MISMATCH')
        return result
    except ValueError:
        # Never interpolate raw output, arbitrary class names, PIN, paths or provider messages.
        raise ValueError('PILOT_INSTRUMENTATION_REJECTED') from None


def failure_locations(output):
    locations = []
    for line in output.splitlines():
        match = re.fullmatch(
            r'\s+at com\.monumentogram\.dora\.ownerpilot\.'
            r'(OwnerPilotInstrumentedTest|SyntheticSystemKeyguard|ReleaseOverrideAssertionsKt)'
            r'\.[A-Za-z0-9_$]+\((OwnerPilotInstrumentedTest|SyntheticSystemKeyguard|ReleaseOverrideAssertions)'
            r'\.kt:([1-9][0-9]{0,2})\)\s*', line)
        if match and match[1].removesuffix('Kt') == match[2]:
            location = {'source': match[2] + '.kt', 'line': int(match[3])}
            if location not in locations:
                locations.append(location)
            if len(locations) == 12:
                break
    return locations


def failure_configuration(output):
    observations = set()
    for line in output.splitlines():
        match = re.fullmatch(
            r'INSTRUMENTATION_STATUS: stack=java\.lang\.AssertionError: '
            r'PILOT_UI_CONFIGURATION width_dp=([1-9][0-9]{1,3}) '
            r'font_scale_permille=([1-9][0-9]{2,3})'
            r'(?: expected:<360> but was:<\1>)?', line)
        if match:
            width, font = map(int, match.groups())
            if 100 <= width <= 2000 and 500 <= font <= 3000:
                observations.add((width, font))
    if len(observations) != 1:
        return None
    width, font = observations.pop()
    return {'width_dp': width, 'font_scale_permille': font}


def switch_to_secondary_viewport(command, wait_for_switch, user):
    require(type(user) is int and user > 0, 'SYNTHETIC_SECONDARY_REQUIRED')
    command('shell', 'am', 'switch-user', str(user))
    wait_for_switch(user)
    require(command('shell', 'am', 'get-current-user') == str(user),
            'SECONDARY_VIEWPORT_USER_MISMATCH')
    # AOSP wm density uses USER_CURRENT; setCurrentUser reloads the new user's density.
    # Apply the compact fixture only after the secondary switch has completed.
    command('shell', 'settings', '--user', str(user), 'put', 'system', 'font_scale', '2.0')
    command('shell', 'wm', 'size', '1080x1920')
    command('shell', 'wm', 'density', '480')


def admit_apk_inputs(app_badging, test_badging, test_manifest):
    check_badging(app_badging)
    packages = re.findall(r"^package: name='([^']+)'", test_badging, re.MULTILINE)
    require(packages == [PACKAGE + '.test'], 'PILOT_TEST_PACKAGE_MISMATCH')
    root = ET.fromstring(test_manifest)
    namespace = '{http://schemas.android.com/apk/res/android}'
    tests = root.findall('instrumentation')
    require(root.get('package') == PACKAGE + '.test' and
            root.get(namespace + 'sharedUserId') is None and len(tests) == 1 and
            tests[0].get(namespace + 'name') == 'androidx.test.runner.AndroidJUnitRunner' and
            tests[0].get(namespace + 'targetPackage') == PACKAGE,
            'PILOT_TEST_TARGET_MISMATCH')


def inspect_apk_inputs(sdk, apk, test_apk):
    suffix, batch = ('.exe', '.bat') if os.name == 'nt' else ('', '')
    aapt = Path(sdk) / ('build-tools/36.0.0/aapt' + suffix)
    analyzer = Path(sdk) / ('cmdline-tools/latest/bin/apkanalyzer' + batch)
    def inspect(*args):
        try:
            result = subprocess.run([str(arg) for arg in args], capture_output=True,
                                    text=True, timeout=120)
        except Exception:
            raise ValueError('PILOT_APK_INSPECTION_FAILED') from None
        require(result.returncode == 0, 'PILOT_APK_INSPECTION_FAILED')
        return result.stdout
    admit_apk_inputs(inspect(aapt, 'dump', 'badging', apk),
                     inspect(aapt, 'dump', 'badging', test_apk),
                     inspect(analyzer, 'manifest', 'print', test_apk))


def stop_synthetic_instrumentation(command, user):
    require(isinstance(user, int) and user >= 0, 'SYNTHETIC_USER_REQUIRED')
    for package in (PACKAGE + '.test', PACKAGE):
        command('shell', 'am', 'force-stop', '--user', str(user), package)


def record_instrumentation_failure(receipt, output, api, phase, command, user):
    receipt.parent.mkdir(parents=True, exist_ok=True)
    diagnostic = receipt.with_suffix('.failure.json')
    failure = {
        'schema_version': 1, 'status': 'FAILED_NOT_ACCEPTANCE',
        'api': api, 'phase': phase, 'physical_device': False,
        'locations': failure_locations(output),
        'ui_configuration': failure_configuration(output),
        'caller_state': failure_caller_state(output),
        'instrumentation_cleanup': 'STOP_UNCONFIRMED',
    }
    # Preserve the original sanitized evidence before cleanup can itself fail or stall.
    diagnostic.write_text(json.dumps(failure, indent=2) + '\n', encoding='utf-8')
    try:
        stop_synthetic_instrumentation(command, user)
    except Exception:
        return
    failure['instrumentation_cleanup'] = 'STOP_CONFIRMED'
    diagnostic.write_text(json.dumps(failure, indent=2) + '\n', encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--expected-api', type=int, choices=(28, 36), required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--test-apk', type=Path, required=True)
    parser.add_argument('--receipt', type=Path, required=True)
    parser.add_argument('--inventory', type=Path, default=Path('docs/contracts/owner-pilot-device-tests-v1.json'))
    args = parser.parse_args()
    require(bool(re.fullmatch(r'emulator-\d+', args.serial)), 'EXPLICIT_EMULATOR_REQUIRED')
    source = source_binding(git_command, os.environ.get('GITHUB_SHA'))
    require(not args.receipt.exists(), 'RECEIPT_ALREADY_EXISTS')
    inventory = validate_inventory(json.loads(args.inventory.read_text(encoding='utf-8')))
    inventory_hash = hashlib.sha256(args.inventory.read_bytes()).hexdigest()
    input_hashes = {str(file): hashlib.sha256(file.read_bytes()).hexdigest()
                    for file in (args.apk, args.test_apk)}
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    require(bool(sdk), 'SDK_REQUIRED')
    # Admit both supplied APK identities before any ADB, including read-only device selection.
    inspect_apk_inputs(sdk, args.apk, args.test_apk)
    adb = str(Path(sdk) / 'platform-tools' / ('adb.exe' if os.name == 'nt' else 'adb'))
    def command(*arguments, timeout=120):
        try:
            result = subprocess.run([adb, '-s', args.serial, *arguments], capture_output=True,
                                    text=True, timeout=timeout)
        except Exception:
            raise ValueError('STOP / EMULATOR_COMMAND_TIMED_OUT') from None
        require(result.returncode == 0, 'EMULATOR_COMMAND_FAILED')
        return result.stdout.replace('\r\n', '\n').strip()
    emulator_fence(args.serial, args.expected_api, command)
    def wait_for_switch(user):
        deadline = time.monotonic() + 90
        while True:
            if args.expected_api == 36:
                ready = switch_complete(command('shell', 'dumpsys', 'activity', 'users'), user)
            else:
                # API28 has no `dumpsys activity users` current/target fields.
                # This is foreground readiness only; cleanup independently retries
                # until the system admits removal of the exact created serial.
                activity = command('shell', 'dumpsys', 'activity')
                ready = (command('shell', 'am', 'get-current-user') == str(user) and
                         re.findall(r'^\s*mCurrentUser=(\d+)\s*$', activity, re.MULTILINE) == [str(user)])
            if ready:
                return
            require(time.monotonic() < deadline, 'USER_SWITCH_COMPLETION_TIMEOUT')
            time.sleep(1)
    require(command('shell', 'am', 'get-current-user') == '0', 'FRESH_OWNER_CONTEXT_REQUIRED')
    require(not command('shell', 'pm', 'list', 'packages', '-u', PACKAGE), 'FRESH_PACKAGE_REQUIRED')
    for file in (args.apk, args.test_apk):
        require(hashlib.sha256(file.read_bytes()).hexdigest() == input_hashes[str(file)],
                'APK_CHANGED_BEFORE_INSTALL')
        require(command('install', '--user', '0', str(file.resolve())).endswith('Success'), 'SYNTHETIC_INSTALL_FAILED')
    rows = []
    def instrument(user, phase, extra=()):
        selected = set(inventory[phase])
        output = ''
        try:
            output = command('shell', 'am', 'instrument', '--user', str(user), '-w', '-r',
                             '-e', 'class', ','.join(sorted(selected)), *extra, RUNNER, timeout=900)
            result = exact_results(output, selected)
        except ValueError:
            record_instrumentation_failure(args.receipt, output, args.expected_api, phase,
                                           command, user)
            raise ValueError('PILOT_INSTRUMENTATION_REJECTED') from None
        rows.append({'phase': phase, 'tests': result})
        args.receipt.parent.mkdir(parents=True, exist_ok=True)
        args.receipt.with_suffix('.progress.json').write_text(json.dumps(
            {'status': 'INCOMPLETE_NOT_ACCEPTANCE', 'phases': rows, 'physical_device': False},
            indent=2) + '\n', encoding='utf-8')

    instrument(0, 'owner_initial')
    user = created_user(command('shell', 'pm', 'create-user', 'DoraOwnerPilotSynthetic'))
    pin = str(secrets.randbelow(900000) + 100000)
    pin_set = False
    serial_number = None
    try:
        dump = command('shell', 'dumpsys', 'user')
        match = re.search(r'UserInfo\{' + str(user) + r':[^\r\n]*:[0-9a-fA-F]+\}\s+serialNo=(\d+)', dump)
        require(match is not None and int(match[1]) > 0, 'USER_SERIAL_NOT_CONFIRMED')
        serial_number = match[1]
        # API28 start-user has no -w option; the explicit switch readiness gate follows.
        command('shell', 'am', 'start-user', str(user))
        # Skip account/setup screens only in the disposable emulator, not lock/authentication.
        command('shell', 'settings', '--user', str(user), 'put', 'secure', 'user_setup_complete', '1')
        switch_to_secondary_viewport(command, wait_for_switch, user)
        for package in (PACKAGE, PACKAGE + '.test'):
            output = command('shell', 'cmd', 'package', 'install-existing', '--user', str(user), package)
            require('installed for user' in output, 'SECONDARY_INSTALL_FAILED')
        require('Pin set to' in command('shell', 'locksettings', 'set-pin', '--user', str(user), pin),
                'SYNTHETIC_CREDENTIAL_SETUP_FAILED')
        pin_set = True
        command('shell', 'input', 'keyevent', '224')
        command('shell', 'wm', 'dismiss-keyguard')
        instrument(user, 'secondary',
                   ('-e', 'syntheticAuthPin', pin, '-e', 'pilotUserId', str(user),
                    '-e', 'pilotUserSerial', serial_number))
    finally:
        if pin_set:
            require('Lock credential cleared' in command('shell', 'locksettings', 'clear', '--user', str(user), '--old', pin),
                    'SYNTHETIC_CREDENTIAL_CLEANUP_FAILED')
        command('shell', 'am', 'switch-user', '0')
        wait_for_switch(0)
        # get-current-user can change before the old user's switch callbacks finish.
        command('shell', 'am', 'stop-user', '-w', '-f', str(user))
        require(serial_number is not None, 'SYNTHETIC_USER_SERIAL_UNPROVEN')
        deadline = time.monotonic() + 60
        while True:
            dump = command('shell', 'dumpsys', 'user')
            remaining = re.findall(r'UserInfo\{' + str(user) + r':[^\r\n]*:[0-9a-fA-F]+\}\s+serialNo=(\d+)', dump)
            if not remaining:
                break
            require(remaining == [serial_number], 'SYNTHETIC_USER_ID_REUSED')
            try:
                command('shell', 'pm', 'remove-user', str(user))
            except ValueError:
                # Android can still protect the previous current user while its
                # asynchronous switch finishes. Never select another user as fallback.
                pass
            require(time.monotonic() < deadline, 'SYNTHETIC_USER_CLEANUP_FAILED')
            time.sleep(1)
    instrument(0, 'owner_final')
    users = command('shell', 'pm', 'list', 'users')
    require(not re.search(r'UserInfo\{' + str(user) + ':', users), 'SYNTHETIC_USER_REMAINS')
    require(all(hashlib.sha256(Path(file).read_bytes()).hexdigest() == digest
                for file, digest in input_hashes.items()), 'APK_CHANGED_DURING_TEST')
    require(hashlib.sha256(args.inventory.read_bytes()).hexdigest() == inventory_hash,
            'PILOT_INVENTORY_CHANGED_DURING_TEST')
    require(source_binding(git_command, os.environ.get('GITHUB_SHA')) == source,
            'PILOT_SOURCE_CHANGED_DURING_TEST')
    receipt = {'schema_version': 1, 'status': 'PASS_OWNER_PILOT_SYNTHETIC_ONLY',
               **source,
               'api': args.expected_api, 'phases': rows, 'synthetic_user_removed': True,
               'inventory_sha256': inventory_hash, 'signing_scope': 'DISPOSABLE_TEST_ONLY',
               'private_vad_acceptance': 'NOT_CLAIMED',
               'ui_fixture': {'width_dp': 360, 'font_scale': 2.0},
               'synthetic_credential_cleanup': 'VERIFIED', 'physical_device': False,
               'apk_sha256': input_hashes[str(args.apk)],
               'test_apk_sha256': input_hashes[str(args.test_apk)],
               'physical_smoke': 'NOT_RUN', 'historical_vault_access': False}
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    with args.receipt.open('x', encoding='utf-8') as output:
        json.dump(receipt, output, indent=2)
        output.write('\n')
    print('PASS owner pilot runtime and UI; synthetic emulator and disposable signer only')


if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, KeyError, TypeError):
        raise SystemExit('STOP / OWNER_PILOT_SYNTHETIC_RUN_FAILED') from None
