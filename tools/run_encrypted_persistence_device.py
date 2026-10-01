"""Run the exact synthetic persistence inventory on an explicitly selected test emulator."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

PACKAGE = 'com.monumentogram.dora.audio.persistence'
NO_CREDENTIAL = PACKAGE + '.auth.AndroidAppLockTest#coldLaunchWithoutCredentialRequiresSystemSetup'
RUNNER = 'com.monumentogram.dora.audio.test/androidx.test.runner.AndroidJUnitRunner'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def parse_results(output, *, expected_skips=frozenset()):
    require('INSTRUMENTATION_CODE: -1' in output and 'INSTRUMENTATION_FAILED' not in output,
            'Instrumentation did not complete successfully')
    rows, current, seen = [], {}, set()
    for line in output.splitlines():
        status = re.fullmatch(r'INSTRUMENTATION_STATUS: (class|test)=(.+)', line)
        if status:
            current[status[1]] = status[2]
        code = re.fullmatch(r'INSTRUMENTATION_STATUS_CODE: (-?\d+)', line)
        if not code:
            continue
        code = int(code[1])
        if code == 1:
            current = {}
            continue
        require(set(current) == {'class', 'test'}, 'Incomplete instrumentation test identity')
        name = current['class'] + '#' + current['test']
        require(re.fullmatch(r'[A-Za-z0-9_.$]+#[A-Za-z0-9_$]+', name) and name not in seen,
                'Invalid or duplicate instrumentation test identity')
        require(code == 0 or (code == -4 and name in expected_skips),
                'Failed or unexpectedly skipped persistence test: ' + name)
        rows.append({'name': name, 'result': 'PASS' if code == 0 else 'SKIPPED'})
        seen.add(name)
        current = {}
    count = re.findall(r'^OK \((\d+) tests?\)$', output, re.MULTILINE)
    require('FAILURES!!!' not in output and len(count) == 1 and int(count[0]) == len(rows) and bool(rows),
            'Instrumentation completion/count mismatch')
    require({row['name'] for row in rows if row['result'] == 'SKIPPED'} == set(expected_skips),
            'Expected credential-state control missing')
    return rows


def validate_inventory(inventory):
    expected, controls = inventory['tests'], inventory['no_credential_tests']
    require(len(expected) == len(set(expected)) and NO_CREDENTIAL in controls
            and all(name.startswith(PACKAGE + '.') and re.fullmatch(r'[A-Za-z0-9_.$]+#[A-Za-z0-9_$]+', name)
                    for name in expected), 'Invalid persistence test inventory')
    require(len(controls) == len(set(controls)) and set(controls) <= set(expected),
            'Invalid no-credential control inventory')
    return set(expected), set(controls)


def page_size_from_smaps(text):
    # This reports kernel mapping pages, not simulated x86_64 userspace page size.
    observed = {int(value) * 1024 for value in re.findall(r'^KernelPageSize:\s+(\d+) kB$', text, re.MULTILINE)}
    require(len(observed) == 1 and observed <= {4096, 16384}, 'Runtime page size is absent or ambiguous')
    return observed.pop()


def page_size_measurement(api, abi, getconf, smaps):
    kernel = page_size_from_smaps(smaps)
    if getconf == 'GETCONF_UNAVAILABLE':
        require(api == 28 and kernel == 4096, 'Missing getconf outside the measured API28 4K fallback')
        runtime, evidence, mode = kernel, 'API28_KERNEL_MAPPING_FALLBACK', 'API28_4K_KERNEL_MAPPING_ONLY'
    else:
        require(getconf in {'4096', '16384'}, 'Runtime getconf page size is absent or malformed')
        runtime, evidence = int(getconf), 'GETCONF_PAGE_SIZE'
        if runtime == kernel:
            mode = 'RUNTIME_MATCHES_KERNEL_MAPPING'
        else:
            require(api >= 35 and abi == 'x86_64' and runtime == 16384 and kernel == 4096,
                    'Unexplained runtime/kernel page-size disagreement')
            mode = 'X86_64_16K_USERSPACE_EMULATION'
    return {'page_size': runtime, 'page_size_evidence': evidence, 'page_size_mode': mode,
            'kernel_page_size': kernel, 'kernel_page_size_evidence': 'PROC_SMAPS_KERNEL_PAGE_SIZE'}


def measure_page_sizes(command, api, abi):
    # Android's 16K emulator verification uses getconf PAGE_SIZE. Keep the kernel
    # observation separate; x86_64 can simulate 16K userspace over 4K kernel mappings.
    getconf = command('shell', 'if command -v getconf >/dev/null 2>&1; then getconf PAGE_SIZE; '
                      'else echo GETCONF_UNAVAILABLE; fi')
    return page_size_measurement(api, abi, getconf, command('shell', 'cat', '/proc/self/smaps'))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--expected-api', type=int, required=True)
    parser.add_argument('--expected-page-size', type=int, choices=(4096, 16384), default=4096)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--inventory', type=Path, required=True)
    parser.add_argument('--receipt', type=Path, required=True)
    args = parser.parse_args()
    require(re.fullmatch(r'emulator-\d+', args.serial), 'Only an explicitly selected test emulator is admitted')
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    require(bool(sdk), 'Android SDK path is required')
    adb = str(Path(sdk) / 'platform-tools' / ('adb.exe' if os.name == 'nt' else 'adb'))

    def command(*arguments, timeout=120):
        result = subprocess.run([adb, '-s', args.serial, *arguments], capture_output=True,
                                text=True, timeout=timeout, check=True)
        return result.stdout.replace('\r\n', '\n').strip()

    require(command('get-state') == 'device' and command('shell', 'getprop', 'ro.kernel.qemu') == '1',
            'Selected target is not an online emulator')
    api = int(command('shell', 'getprop', 'ro.build.version.sdk'))
    abi = command('shell', 'getprop', 'ro.product.cpu.abi')
    page_measurement = measure_page_sizes(command, api, abi)
    page_size = page_measurement['page_size']
    require((api, page_size) == (args.expected_api, args.expected_page_size), 'Runtime API/page-size identity mismatch')
    expected, no_credential = validate_inventory(json.loads(args.inventory.read_text(encoding='utf-8')))
    require(command('install', '-r', str(args.apk.resolve())).endswith('Success'), 'Test APK was not installed')
    require(command('shell', 'pm', 'clear', RUNNER.split('/')[0]) == 'Success',
            'Synthetic instrumentation package state could not be reset')

    def run_no_credential_controls():
        output = command('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', ','.join(sorted(no_credential)), RUNNER)
        rows = parse_results(output)
        require({row['name'] for row in rows} == no_credential, 'No-credential controls did not execute')
        return rows

    # Prove fresh-package denial before a successful run can create the canonical vault.
    initial_no_credential_rows = run_no_credential_controls()
    # This fresh test-emulator-only setup cannot replace an existing credential without its old value.
    pin = '246810'
    setup = command('shell', 'locksettings', 'set-pin', pin)
    require('Pin set to' in setup, 'Synthetic credential setup failed; existing device state retained')
    credential_rows = []
    try:
        command('shell', 'input', 'keyevent', '82')
        output = command('shell', 'am', 'instrument', '-w', '-r', '-e', 'package', PACKAGE,
                         '-e', 'syntheticAuthPin', pin, RUNNER, timeout=1800)
        credential_rows = parse_results(output, expected_skips=no_credential)
        require({row['name'] for row in credential_rows} == expected, 'Executed persistence inventory differs')
    finally:
        cleared = command('shell', 'locksettings', 'clear', '--old', pin)
        require('Lock credential cleared' in cleared, 'Synthetic credential cleanup was not confirmed')
    no_credential_rows = run_no_credential_controls()
    receipt = {'schema_version': 1, 'status': 'PASS_COMPONENT_RUNTIME_ONLY',
               'api': api, 'abi': abi, **page_measurement,
               'apk_sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(),
               'inventory_sha256': hashlib.sha256(args.inventory.read_bytes()).hexdigest(),
               'credential_tests': credential_rows, 'initial_no_credential_tests': initial_no_credential_rows,
               'no_credential_tests': no_credential_rows,
               'synthetic_credential_cleanup': 'VERIFIED', 'physical_device': False,
               'stage_acceptance': 'NOT_CLAIMED'}
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_text(json.dumps(receipt, sort_keys=True, indent=2) + '\n', encoding='utf-8')
    print(f'PASS {len(expected)} distinct persistence tests, API {api}, page size {page_size}; component evidence only')


if __name__ == '__main__':
    main()
