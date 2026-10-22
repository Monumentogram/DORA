"""Render/recreate actual APK on an explicitly fenced disposable API28/36 emulator only."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import xml.etree.ElementTree as ET

from alpha_security_preflight import require
from run_encrypted_persistence_device import parse_results
from run_isolated_alpha_device import emulator_fence
from run_owner_pilot_device import source_binding, git_command
from version_label import IDENTITIES, inspect_apk

TEST = 'com.monumentogram.dora.VersionLabelInstrumentedTest#installedVersionIsVisibleBeforeAndAfterRecreation'


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def installed_path(output):
    paths = output.splitlines()
    require(len(paths) == 1 and
            re.fullmatch(r'package:/data/app/(?:[A-Za-z0-9_+=~.-]+/){1,2}base\.apk', paths[0]) and
            not {'.', '..'} & set(paths[0].split('/')), 'INSTALLED_PATH_INVALID')
    return paths[0].removeprefix('package:')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--expected-api', type=int, choices=(28, 36), required=True)
    parser.add_argument('--variant', choices=('debug', 'alphaTest', 'ownerPilot'), required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--test-apk', type=Path, required=True)
    parser.add_argument('--receipt', type=Path, required=True)
    args = parser.parse_args()
    require(not args.receipt.exists(), 'RECEIPT_EXISTS')
    require(re.fullmatch(r'emulator-[0-9]+', args.serial), 'EMULATOR_SERIAL_REQUIRED')
    source = source_binding(git_command, os.environ.get('GITHUB_SHA'))
    sdk = Path(os.environ['ANDROID_HOME'])
    adb = sdk / ('platform-tools/adb' + ('.exe' if os.name == 'nt' else ''))
    def run(command, timeout=120):
        result = subprocess.run([str(x) for x in command], capture_output=True, text=True, timeout=timeout)
        require(result.returncode == 0, 'VERSION_LABEL_TOOL_FAILED')
        return result.stdout.strip()
    def command(*arguments):
        return run([adb, '-s', args.serial, *arguments], timeout=240)
    def fence():
        emulator_fence(args.serial, args.expected_api, command)
        require(command('shell', 'am', 'get-current-user') == '0', 'DISPOSABLE_OWNER_REQUIRED')
    label = inspect_apk(args.apk, sdk, args.variant)
    apk_sha, test_sha = digest(args.apk), digest(args.test_apk)
    package, code, name = IDENTITIES[args.variant]
    analyzer = sdk / ('cmdline-tools/latest/bin/apkanalyzer' + ('.bat' if os.name == 'nt' else ''))
    test_manifest = ET.fromstring(run([analyzer, 'manifest', 'print', args.test_apk]))
    namespace = '{http://schemas.android.com/apk/res/android}'
    instrumentation = test_manifest.findall('instrumentation')
    require(test_manifest.get('package') == package + '.test' and len(instrumentation) == 1 and
            instrumentation[0].get(namespace + 'targetPackage') == package and
            instrumentation[0].get(namespace + 'name') == 'androidx.test.runner.AndroidJUnitRunner',
            'VERSION_LABEL_TEST_TARGET_MISMATCH')
    fence()
    installed = command('shell', 'pm', 'list', 'packages', '--user', '0').splitlines()
    require('package:' + package not in installed and 'package:' + package + '.test' not in installed,
            'FRESH_EMULATOR_PACKAGES_REQUIRED')
    for path, flags in ((args.apk, []), (args.test_apk, ['-t'])):
        fence()
        require(command('install', *flags, '--user', '0', str(path)).splitlines()[-1] == 'Success', 'INSTALL_FAILED')
    path = installed_path(command('shell', 'pm', 'path', '--user', '0', package))
    with tempfile.TemporaryDirectory() as temp:
        installed_apk = Path(temp) / 'installed.apk'
        command('pull', path, str(installed_apk))
        require(digest(installed_apk) == apk_sha, 'INSTALLED_APK_BYTES_MISMATCH')
    fence()
    output = command('shell', 'am', 'instrument', '--user', '0', '-w', '-r', '-e', 'class', TEST,
                     package + '.test/androidx.test.runner.AndroidJUnitRunner')
    rows = parse_results(output)
    require(rows == [{'name': TEST, 'result': 'PASS'}], 'VERSION_LABEL_TEST_INVENTORY')
    require(digest(args.apk) == apk_sha and digest(args.test_apk) == test_sha, 'APK_CHANGED')
    require(source_binding(git_command) == source, 'SOURCE_CHANGED')
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    receipt = dict(source, variant=args.variant, api=args.expected_api, apk_sha256=apk_sha,
                   test_apk_sha256=test_sha, installed_bytes_verified=True, package=package,
                   version_code=int(code), version_name=name, compiled_label=label,
                   normal_activity_render_and_recreation=rows, physical_device=False)
    args.receipt.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print('PASS version label: installed bytes, visible UI and Activity recreation')


if __name__ == '__main__':
    main()
