"""Synthetic preservation tests only. Never select or discover a physical device."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

import persistence_instrumentation_diagnostics as diagnostics
from run_encrypted_persistence_device import parse_results

PACKAGE = 'com.monumentogram.dora.audio.test'
RUNNER = PACKAGE + '/androidx.test.runner.AndroidJUnitRunner'


def require(condition):
    if not condition:
        raise ValueError('PRESERVATION_EXPERIMENT_ADMISSION_DENIED')


def validate_target(serial, api, query):
    require(re.fullmatch(r'emulator-[0-9]+', serial) is not None and api in (28, 36))
    require(query('shell', 'getprop', 'ro.kernel.qemu').strip() == '1')
    require(query('shell', 'getprop', 'ro.hardware').strip() in ('ranchu', 'goldfish'))
    require(query('shell', 'getprop', 'ro.build.version.sdk').strip() == str(api))


def validate_apk(badging):
    require(re.findall(r"^package: name='([^']+)'", badging, re.MULTILINE) == [PACKAGE])


def validate_inventory(inventory):
    tests = inventory['tests']
    require(bool(tests) and len(tests) == len(set(tests)))
    require(all(re.fullmatch(r'com\.monumentogram\.dora\.audio\.persistence\.[A-Za-z]+(?:Test)#[A-Za-z0-9_]+', test) for test in tests))
    return sorted(tests)


def execute_bounded(instrument, stop):
    try:
        return instrument()
    except (Exception, KeyboardInterrupt):
        try:
            stop()
        except (Exception, KeyboardInterrupt):
            raise diagnostics.InstrumentationFailure('STOP_UNCONFIRMED') from None
        raise diagnostics.InstrumentationFailure('SYNTHETIC_RUN_ABORTED_STOP_COMMAND_SUCCEEDED') from None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--expected-api', type=int, choices=(28, 36), required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--inventory', type=Path, required=True)
    parser.add_argument('--receipt', type=Path, required=True)
    parser.add_argument('--source-sha', required=True)
    args = parser.parse_args()
    require(re.fullmatch(r'emulator-[0-9]+', args.serial) is not None)
    require(re.fullmatch(r'[0-9a-f]{40}', args.source_sha) is not None)
    require(not args.receipt.exists())
    selected = validate_inventory(json.loads(args.inventory.read_text(encoding='utf-8')))
    sdk = Path(os.environ['ANDROID_HOME'])
    suffix = '.exe' if os.name == 'nt' else ''
    adb = sdk / 'platform-tools' / ('adb' + suffix)
    aapt = sdk / 'build-tools/36.0.0' / ('aapt' + suffix)
    validate_apk(subprocess.check_output([str(aapt), 'dump', 'badging', str(args.apk)], text=True))

    def command(*parts):
        return subprocess.check_output([str(adb), '-s', args.serial, *parts], text=True, timeout=120).strip()

    validate_target(args.serial, args.expected_api, command)
    require(command('install', '-r', str(args.apk.resolve())).endswith('Success'))
    # Fixtures use unique private synthetic directories. No clear/uninstall/credential operations.
    output = execute_bounded(
        lambda: diagnostics.stream(
            [str(adb), '-s', args.serial, 'shell', 'am', 'instrument', '-w', '-r',
             '-e', 'class', ','.join(selected), RUNNER], selected,
            args.receipt.with_suffix('.progress.json'), hard_timeout=900,
            probe=lambda: diagnostics.emulator_diagnostics(str(adb), args.serial),
            capture=lambda: diagnostics.emulator_diagnostics(str(adb), args.serial, stack=True)),
        lambda: command('shell', 'am', 'force-stop', PACKAGE))
    rows = parse_results(output)
    diagnostics.require_complete([row['name'] for row in rows], selected)
    receipt = {'status': 'PASS_SYNTHETIC_PRESERVATION_ONLY', 'api': args.expected_api,
               'source_sha': args.source_sha, 'physical_device': False,
               'historical_vault_access': False, 'real_app_lock_proof': 'NOT_TESTED',
               'apk_sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(),
               'inventory_sha256': hashlib.sha256(args.inventory.read_bytes()).hexdigest(),
               'tests': rows}
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print(f"{receipt['status']}: {len(rows)} tests, API{args.expected_api}")


if __name__ == '__main__':
    main()
