"""Opt-in synthetic encrypted benchmark on an explicitly selected Android emulator.

Never discovers or connects physical devices. Retains private raw output separately
from the parser's allowlisted receipt. Requires an already credential-configured
test emulator; does not alter its credential or delete retained synthetic vaults.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import time

from persistence_scaling_results import parse

PACKAGE = 'com.monumentogram.dora.audio.test'
TEST = 'com.monumentogram.dora.audio.diagnostics.PersistenceScalingBenchmarkTest'


def validate_serial(serial):
    if not isinstance(serial, str) or not re.fullmatch(r'emulator-[0-9]+', serial):
        raise ValueError('Explicit emulator serial required; physical devices forbidden')


def verify_emulator(command, expected_api):
    if expected_api not in (28, 36):
        raise ValueError('Expected API28 or API36')
    if command('shell', 'getprop', 'ro.kernel.qemu').strip() != '1':
        raise ValueError('Selected target is not an emulator')
    if command('shell', 'getprop', 'ro.build.version.sdk').strip() != str(expected_api):
        raise ValueError('Emulator API mismatch')
    return expected_api


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True, type=Path)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--expected-api', required=True, type=int, choices=(28, 36))
    parser.add_argument('--apk', required=True, type=Path)
    parser.add_argument('--output-dir', required=True, type=Path)
    parser.add_argument('--timeout-seconds', type=int, default=7200)
    args = parser.parse_args()
    validate_serial(args.serial)
    if not 60 <= args.timeout_seconds <= 14400:
        raise ValueError('Bounded timeout required')
    args.output_dir.mkdir(parents=True, exist_ok=False)
    prefix = [str(args.adb), '-s', args.serial]

    def command(*parts):
        return subprocess.run([*prefix, *parts], check=True, capture_output=True,
                              text=True, timeout=120).stdout.strip()

    verify_emulator(command, args.expected_api)
    apk_hash = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    installed = command('install', '-r', str(args.apk.resolve()))
    if 'Success' not in installed:
        raise RuntimeError('Test APK install not confirmed')
    command_line = [*prefix, 'shell', 'am', 'instrument', '-w', '-r', '-e', 'class', TEST,
                    '-e', 'persistenceScaling', 'true', '-e', 'maxBlocks', '2000',
                    PACKAGE + '/androidx.test.runner.AndroidJUnitRunner']
    raw_path = args.output_dir / 'instrumentation.private.log'
    begin = time.monotonic()
    outcome = 'EXITED'
    try:
        with raw_path.open('xb') as stream:
            result = subprocess.run(command_line, stdout=stream, stderr=subprocess.STDOUT,
                                    timeout=args.timeout_seconds, check=False)
        if result.returncode != 0:
            outcome = 'ADB_FAILED'
    except (subprocess.TimeoutExpired, KeyboardInterrupt):
        outcome = 'TIMED_OUT_OR_INTERRUPTED'
    stop_status = 'NOT_REQUESTED'
    if outcome != 'EXITED':
        # Transport failure does not prove instrumentation has stopped. This is
        # bounded to the same already verified emulator and synthetic package.
        try:
            command('shell', 'am', 'force-stop', PACKAGE)
            stop_status = 'COMMAND_SUCCEEDED'
        except (subprocess.SubprocessError, OSError, KeyboardInterrupt):
            # Keep the raw log and failed receipt even when cleanup is unavailable;
            # never publish command output, device identifiers or exception text.
            stop_status = 'UNCONFIRMED'
    raw = raw_path.read_bytes()
    receipt = parse(raw.decode('utf-8', errors='replace'), args.expected_api)
    receipt.update(apk_sha256=apk_hash, input_sha256=hashlib.sha256(raw).hexdigest(),
                   elapsed_seconds=time.monotonic()-begin, host_process_outcome=outcome,
                   test_package_stop=stop_status)
    if outcome != 'EXITED':
        receipt['status'] = 'FAILED'
    (args.output_dir / 'receipt.json').write_text(
        json.dumps(receipt, indent=2, allow_nan=False)+'\n', encoding='utf-8')
    print(receipt['status'])
    return 0 if receipt['status'] == 'COMPLETE' else 1


if __name__ == '__main__':
    raise SystemExit(main())
