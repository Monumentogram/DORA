"""Synthetic secondary-user operator tests. All mutations require an explicit verified emulator."""
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

from isolated_alpha_preflight import PACKAGE, require
from run_encrypted_persistence_device import parse_results

CLASS = 'com.monumentogram.dora.operator.IsolatedAlphaOperatorInstrumentedTest'
RUNNER = PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner'

FAILURE_METHODS = {
    'SyntheticSystemKeyguard': frozenset({
        'unlockIfNeeded', 'windowMetadata', 'systemKeyguardPinSurface',
    }),
    'IsolatedAlphaOperatorInstrumentedTest': frozenset({
        'realSecondaryRuntimeAuthenticatedReadbackDeletionAndReopen',
        'actualPackageAndOwnerIdentityAreNeverImplicitlySelected', 'seedOwnerIsolationSentinel',
        'secondaryCannotReadOwnerSentinel', 'verifyOwnerIsolationSentinelUnchanged',
        'assertOwnerSentinel', 'unlock', 'callerDiagnostics', 'awaitCallerResumed',
        'availabilityCode', 'enterCredential', 'exerciseOperatorUi', 'uiReadback', 'waitUi',
        'button', 'field', 'views', 'delete', 'node', 'success', 'id', 'identity',
    }),
}
FAILURE_STAGES = {
    'awaitCallerResumed': 'CALLER_READINESS',
    'enterCredential': 'APP_CREDENTIAL_ENTRY',
    'unlock': 'AUTHENTICATED_OPEN',
    'exerciseOperatorUi': 'OPERATOR_UI', 'uiReadback': 'OPERATOR_UI', 'waitUi': 'OPERATOR_UI',
    'button': 'OPERATOR_UI', 'field': 'OPERATOR_UI', 'views': 'OPERATOR_UI',
    'delete': 'CONFIRMED_DELETION',
}


def failure_caller_state(output):
    messages = {
        'Fixture caller never resumed': 'CALLER_NOT_RESUMED',
        'Credential prompt timeout': 'PROMPT_TIMEOUT',
        'Real authenticated open timed out': 'OPEN_TIMEOUT',
        'Real authenticated open failed': 'OPEN_FAILED',
    }
    availability = {'NO_RESULT', 'LOCKED', 'OPENING', 'AVAILABLE'} | {
        'FAILED_' + reason for reason in (
            'LOCKED', 'CANCELLED', 'CREDENTIAL_SETUP_REQUIRED', 'UNAVAILABLE',
            'INVALID_INPUT', 'COLLISION', 'KEY_UNAVAILABLE', 'KEY_INVALIDATED',
            'AUTHENTICATION_FAILED', 'INCOMPLETE', 'CORRUPT', 'BUSY', 'UNCERTAIN')
    }
    configuration_flags = ('DENSITY', 'FONT_SCALE', 'ORIENTATION', 'SCREEN_SIZE', 'UI_MODE', 'OTHER')
    observations = set()
    for line in output.splitlines():
        match = re.fullmatch(
            r'INSTRUMENTATION_STATUS: stack=java\.lang\.AssertionError: ('
            + '|'.join(re.escape(message) for message in messages) +
            r'): (?:([A-Z_]{1,40}) )?callerResumedAtRequest=(true|false) '
            r'focusAtRequest=(true|false) secure=(true|false) locked=(true|false) '
            r'callerFinishing=(true|false) callerDestroyed=(true|false)'
            r'(?: callerChangingConfigurations=(true|false) configurationChanges=([A-Z_,]{1,80}) '
            r'otherMainResumed=(true|false))?', line)
        if match:
            kind, state = messages[match[1]], match[2]
            if (kind == 'CALLER_NOT_RESUMED' and state is not None) or (
                    kind != 'CALLER_NOT_RESUMED' and state not in availability):
                return None
            changing, changes, replacement = match.groups()[8:]
            flags = () if changes in (None, 'NONE') else tuple(changes.split(','))
            if flags != tuple(flag for flag in configuration_flags if flag in flags):
                return None
            observations.add((kind, state, *(value == 'true' for value in match.groups()[2:8]),
                              changing, flags, replacement))
    if len(observations) != 1:
        return None
    keys = ('failure', 'availability', 'caller_resumed_at_request', 'focus_at_request',
            'secure', 'locked', 'caller_finishing', 'caller_destroyed')
    observation = observations.pop()
    result = dict(zip(keys, observation[:8]))
    if observation[8] is not None:
        result.update(caller_changing_configurations=observation[8] == 'true',
                      configuration_changes=list(observation[9]),
                      other_main_resumed=observation[10] == 'true')
    return result


def failure_summary(output, api):
    require(api in (28, 36), 'EMULATOR_API_MISMATCH')
    locations, seen = [], set()
    for line in output.splitlines():
        frame = re.fullmatch(
            r'\s+at com\.monumentogram\.dora\.operator\.'
            r'(IsolatedAlphaOperatorInstrumentedTest|SyntheticSystemKeyguard)\.'
            r'([A-Za-z0-9_$]+)\(([A-Za-z0-9]+\.kt):([1-9][0-9]{0,3})\)\s*', line)
        if not frame:
            continue
        owner, method, source, number = frame.groups()
        if method not in FAILURE_METHODS[owner] or source != owner + '.kt' or int(number) > 2000:
            continue
        location = (owner, method, source, int(number))
        if location not in seen:
            locations.append({'class': owner, 'method': method, 'source': source, 'line': int(number)})
            seen.add(location)
        if len(locations) == 16:
            break
    stage = 'UNCLASSIFIED'
    if locations:
        first = locations[0]
        stage = ('SYSTEM_KEYGUARD_SETUP' if first['class'] == 'SyntheticSystemKeyguard'
                 else FAILURE_STAGES.get(first['method'], 'TEST_SCENARIO'))
    return {'schema_version': 1, 'status': 'FAILED_NOT_ACCEPTANCE', 'api': api,
            'stage': stage, 'locations': locations, 'caller_state': failure_caller_state(output)}


def write_failure_diagnostics(receipt, output, api):
    receipt.parent.mkdir(parents=True, exist_ok=True)
    diagnostic = receipt.with_suffix('.failure.local.txt')
    with diagnostic.open('a', encoding='utf-8') as stream:
        stream.write(re.sub(r'\d{6}', '[REDACTED]', output) + '\n')
    receipt.with_suffix('.failure.json').write_text(
        json.dumps(failure_summary(output, api), indent=2) + '\n', encoding='utf-8')


def emulator_fence(serial, api, command):
    require(bool(re.fullmatch(r'emulator-\d+', serial)), 'EXPLICIT_EMULATOR_REQUIRED')
    require(command('get-state').strip() == 'device' and
            command('shell', 'getprop', 'ro.kernel.qemu').strip() == '1', 'VERIFIED_EMULATOR_REQUIRED')
    require(api in (28, 36) and command('shell', 'getprop', 'ro.build.version.sdk').strip() == str(api),
            'EMULATOR_API_MISMATCH')


def created_user(output):
    found = re.fullmatch(r'Success: created user id ([1-9]\d*)', output.strip())
    require(found is not None, 'NEW_SECONDARY_USER_NOT_CONFIRMED')
    return int(found[1])


def switch_complete(dump, user):
    current = re.findall(r'^\s*mCurrentUserId:\s*(-?\d+)\s*$', dump, re.MULTILINE)
    target = re.findall(r'^\s*mTargetUserId:\s*(-?\d+)\s*$', dump, re.MULTILINE)
    return current == [str(user)] and target == ['-10000']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--expected-api', type=int, choices=(28, 36), required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--test-apk', type=Path, required=True)
    parser.add_argument('--receipt', type=Path, required=True)
    args = parser.parse_args()
    require(bool(re.fullmatch(r'emulator-\d+', args.serial)), 'EXPLICIT_EMULATOR_REQUIRED')
    require(not args.receipt.exists(), 'RECEIPT_ALREADY_EXISTS')
    input_hashes = {str(file): hashlib.sha256(file.read_bytes()).hexdigest()
                    for file in (args.apk, args.test_apk)}
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    require(bool(sdk), 'SDK_REQUIRED')
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
        require(command('install', '--user', '0', str(file.resolve())).endswith('Success'), 'SYNTHETIC_INSTALL_FAILED')
    rows = []
    def instrument(user, methods, extra=()):
        selected = {CLASS + '#' + method for method in methods}
        output = command('shell', 'am', 'instrument', '--user', str(user), '-w', '-r',
                         '-e', 'class', ','.join(sorted(selected)), *extra, RUNNER, timeout=900)
        # Never retain raw instrumentation output: PIN-bearing command arguments and audio stay private.
        try:
            result = parse_results(output)
        except ValueError:
            # Raw failures stay local. Only allowlisted locations enter public failure JSON;
            # it is explicitly not an acceptance receipt and the original failure is rethrown.
            write_failure_diagnostics(args.receipt, output, args.expected_api)
            raise
        require({row['name'] for row in result} == selected, 'OPERATOR_TEST_INVENTORY_MISMATCH')
        rows.append({'user_role': 'owner_negative' if user == 0 else 'secondary', 'tests': result})
        args.receipt.parent.mkdir(parents=True, exist_ok=True)
        args.receipt.with_suffix('.progress.json').write_text(json.dumps(
            {'status': 'INCOMPLETE_NOT_ACCEPTANCE', 'phases': rows, 'physical_device': False},
            indent=2) + '\n', encoding='utf-8')

    instrument(0, ['actualPackageAndOwnerIdentityAreNeverImplicitlySelected', 'seedOwnerIsolationSentinel'])
    user = created_user(command('shell', 'pm', 'create-user', 'DoraIsolatedSynthetic'))
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
        command('shell', 'am', 'switch-user', str(user))
        wait_for_switch(user)
        for package in (PACKAGE, PACKAGE + '.test'):
            output = command('shell', 'cmd', 'package', 'install-existing', '--user', str(user), package)
            require('installed for user' in output, 'SECONDARY_INSTALL_FAILED')
        require('Pin set to' in command('shell', 'locksettings', 'set-pin', '--user', str(user), pin),
                'SYNTHETIC_CREDENTIAL_SETUP_FAILED')
        pin_set = True
        command('shell', 'input', 'keyevent', '224')
        command('shell', 'wm', 'dismiss-keyguard')
        instrument(user, ['realSecondaryRuntimeAuthenticatedReadbackDeletionAndReopen',
                          'actualPackageAndOwnerIdentityAreNeverImplicitlySelected',
                          'secondaryCannotReadOwnerSentinel'],
                   ('-e', 'syntheticAuthPin', pin, '-e', 'operatorUserId', str(user),
                    '-e', 'operatorUserSerial', serial_number))
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
    instrument(0, ['verifyOwnerIsolationSentinelUnchanged'])
    users = command('shell', 'pm', 'list', 'users')
    require(not re.search(r'UserInfo\{' + str(user) + ':', users), 'SYNTHETIC_USER_REMAINS')
    require(all(hashlib.sha256(Path(file).read_bytes()).hexdigest() == digest
                for file, digest in input_hashes.items()), 'APK_CHANGED_DURING_TEST')
    receipt = {'schema_version': 1, 'status': 'PASS_ISOLATED_OPERATOR_SYNTHETIC_ONLY',
               'api': args.expected_api, 'phases': rows, 'synthetic_user_removed': True,
               'synthetic_credential_cleanup': 'VERIFIED', 'physical_device': False,
               'apk_sha256': input_hashes[str(args.apk)],
               'test_apk_sha256': input_hashes[str(args.test_apk)],
               'physical_smoke': 'NOT_RUN', 'historical_vault_access': False}
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    with args.receipt.open('x', encoding='utf-8') as output:
        json.dump(receipt, output, indent=2)
        output.write('\n')
    print('PASS isolated operator, real encrypted adapters and secondary-user negatives; synthetic emulator only')


if __name__ == '__main__':
    main()
