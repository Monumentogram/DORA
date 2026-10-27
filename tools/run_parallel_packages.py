"""Dual-package file/Keystore isolation on an explicitly fenced disposable emulator only.

Pilot here is the synthetic current-source v4 peer, NOT exact v2 or migration evidence.
"""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess

from alpha_security_preflight import require
from isolated_alpha_apk import sha
from run_isolated_alpha_device import emulator_fence
from run_owner_pilot_device import exact_results, source_binding, git_command

PILOT = 'com.monumentogram.dora.alpha.pilot'
PREVIEW = 'com.monumentogram.dora.alpha.preview'
CLASS = 'com.monumentogram.dora.ParallelPackageIsolationTest'


def seed_result(output, package):
    result = {}
    patterns = {'uid': r'[1-9][0-9]{4,5}',
                'dir': r'/data/user/0/' + re.escape(package) + '/no_backup',
                'sealed': r'[A-Za-z0-9+/]+={0,2}'}
    for key, pattern in patterns.items():
        values = re.findall(r'^INSTRUMENTATION_RESULT: parallel_' + key + r'=(.*)$', output, re.M)
        require(len(values) == 1 and re.fullmatch(pattern, values[0]), 'INVALID_PARALLEL_SEED')
        result[key] = values[0]
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--expected-api', type=int, choices=(28, 36), required=True)
    for name in ('pilot', 'pilot-test', 'preview', 'preview-test', 'receipt'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    require(re.fullmatch(r'emulator-[0-9]+', args.serial), 'EXPLICIT_EMULATOR_REQUIRED')
    require(not args.receipt.exists(), 'RECEIPT_EXISTS')
    source = source_binding(git_command, os.environ.get('GITHUB_SHA'))
    sdk = Path(os.environ['ANDROID_HOME'])
    adb = sdk / ('platform-tools/adb.exe' if os.name == 'nt' else 'platform-tools/adb')
    paths = {PILOT: (args.pilot, args.pilot_test), PREVIEW: (args.preview, args.preview_test)}
    # Inspect every input before contacting even the explicitly selected emulator.
    from owner_alpha_pilot_artifact import audit_apk as audit_pilot
    from owner_preview_artifact import audit_apk as audit_preview
    from run_owner_pilot_device import inspect_apk_inputs
    import run_owner_pilot_device as device
    for package, (apk, test) in paths.items():
        audit = audit_pilot if package == PILOT else audit_preview
        audit(apk, sdk, 'ci-unsigned')
        device.PACKAGE = package
        device.check_badging = __import__('owner_alpha_pilot_artifact' if package == PILOT else 'owner_preview_artifact').check_badging
        inspect_apk_inputs(str(sdk), apk, test)
    hashes = {package: [sha(p) for p in files] for package, files in paths.items()}

    def command(*arguments, timeout=180):
        result = subprocess.run([str(adb), '-s', args.serial, *arguments],
                                capture_output=True, text=True, timeout=timeout)
        require(result.returncode == 0, 'PARALLEL_EMULATOR_COMMAND_FAILED')
        return result.stdout.strip()

    emulator_fence(args.serial, args.expected_api, command)
    require(command('shell', 'am', 'get-current-user') == '0', 'DISPOSABLE_OWNER_REQUIRED')
    for package in paths:
        require(not command('shell', 'pm', 'list', 'packages', '-u', package), 'FRESH_PACKAGES_REQUIRED')
        for apk in paths[package]:
            require(command('install', str(apk.resolve())).endswith('Success'), 'PARALLEL_INSTALL_FAILED')

    def instrument(package, method, peer=None):
        extra = []
        if peer:
            for key, value in [('peerUid', peer['uid']), ('peerDir', peer['dir']), ('peerSealed', peer['sealed'])]:
                extra += ['-e', key, value]
        selected = CLASS + '#' + method
        output = command('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', selected,
                         *extra, package + '.test/androidx.test.runner.AndroidJUnitRunner')
        exact_results(output, {selected})
        return output

    seeded = {package: seed_result(instrument(package, 'seed'), package) for package in paths}
    require(seeded[PILOT]['uid'] != seeded[PREVIEW]['uid'], 'SHARED_UID')
    for package, peer in ((PILOT, PREVIEW), (PREVIEW, PILOT)):
        instrument(package, 'cannotReadPeerFileOrDecryptPeerKey', seeded[peer])
    pilot_before = command('shell', 'pm', 'path', PILOT)
    for package in (PREVIEW + '.test', PREVIEW):
        require(command('uninstall', package).endswith('Success'), 'PREVIEW_REMOVAL_FAILED')
    require(not command('shell', 'pm', 'list', 'packages', '-u', PREVIEW), 'PREVIEW_REMAINS')
    require(command('shell', 'pm', 'path', PILOT) == pilot_before, 'PILOT_PATH_CHANGED')
    instrument(PILOT, 'verifyOwn')
    for package in (PILOT + '.test', PILOT):
        require(command('uninstall', package).endswith('Success'), 'SYNTHETIC_PEER_CLEANUP_FAILED')
    require(source_binding(git_command, os.environ.get('GITHUB_SHA')) == source, 'SOURCE_CHANGED')
    require(hashes == {p: [sha(f) for f in files] for p, files in paths.items()}, 'INPUT_CHANGED')
    receipt = {**source, 'status': 'PASS', 'api': args.expected_api, 'physical_device': False,
               'peer_scope': 'CURRENT_SOURCE_SYNTHETIC_PILOT_V4_NOT_EXACT_V2',
               'apk_sha256': hashes, 'uids': {p: v['uid'] for p, v in seeded.items()},
               'distinct_data_directories': True, 'bidirectional_file_access_denied': True,
               'same_alias_cross_uid_decryption_denied': True, 'own_keys_survive_restart': True,
               'pilot_survives_preview_removal': True, 'synthetic_packages_removed': True}
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_text(json.dumps(receipt, indent=2) + '\n', encoding='utf-8')
    print('PASS synthetic package/file/Keystore isolation; not v2 runtime or migration evidence')


if __name__ == '__main__':
    main()
