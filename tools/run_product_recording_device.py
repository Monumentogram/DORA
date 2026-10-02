"""Run product UI controls on an explicit emulator; never claim real microphone evidence."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

from run_encrypted_persistence_device import parse_results, require

PACKAGE = 'com.monumentogram.dora.debug'
TEST_CLASS = 'com.monumentogram.dora.DoraBootstrapAppTest'
TESTS = {
    TEST_CLASS + '#' + name for name in (
        'showsFourDestinationsAndChangesSelectedSection',
        'recordActionOpensPreflightWithoutStartingMicrophone',
        'leavingPreflightReturnsToNavigationWithoutStartingCapture',
        'staleNotificationCannotStartOrStopRecording',
        'activePresentationSeparatesCapturedAndDurableTime',
        'pausedPresentationOffersExplicitResumeAndStableFrameTime',
        'lockedPresentationHidesCapturedTimeButAllowsPauseAndStop',
        'stopConfirmationTruthfullyPreservesPausedState',
        'finalizingPresentationDoesNotClaimSaved',
        'permissionFailurePresentationDoesNotClaimCaptureOrSaved',
        'preflightSurvivesActivityRecreationWithoutStartingMicrophone',
    )
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--expected-api', type=int, choices=(28, 36), required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--test-apk', type=Path, required=True)
    parser.add_argument('--receipt', type=Path, required=True)
    args = parser.parse_args()
    require(re.fullmatch(r'emulator-\d+', args.serial), 'An explicit emulator is required')
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    require(bool(sdk), 'Android SDK path is required')
    adb = str(Path(sdk) / 'platform-tools' / ('adb.exe' if os.name == 'nt' else 'adb'))

    def command(*arguments, timeout=180):
        result = subprocess.run([adb, '-s', args.serial, *arguments], capture_output=True,
                                text=True, timeout=timeout, check=True)
        return result.stdout.replace('\r\n', '\n').strip()

    require(command('get-state') == 'device' and command('shell', 'getprop', 'ro.kernel.qemu') == '1',
            'Target must be an online test emulator')
    require(command('shell', 'getprop', 'ro.build.version.sdk') == str(args.expected_api), 'Wrong API')
    for apk in (args.apk, args.test_apk):
        require(command('install', '-r', str(apk.resolve())).endswith('Success'), 'APK installation failed')
    require(command('shell', 'pm', 'clear', PACKAGE) == 'Success', 'Synthetic product state reset failed')
    command('shell', 'input', 'keyevent', '224')
    command('shell', 'wm', 'dismiss-keyguard')
    rows = parse_results(command('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', TEST_CLASS,
                                 PACKAGE + '.test/androidx.test.runner.AndroidJUnitRunner', timeout=600))
    require({row['name'] for row in rows} == TESTS, 'Product UI inventory mismatch')
    receipt = {'schema_version': 1, 'status': 'PASS_PRODUCT_UI_COMPONENT_ONLY',
               'api': args.expected_api, 'tests': rows,
               'apk_sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(),
               'test_apk_sha256': hashlib.sha256(args.test_apk.read_bytes()).hexdigest(),
               'driver_sha256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
               'physical_microphone': False, 'stage_acceptance': 'NOT_CLAIMED'}
    require(not args.receipt.exists(), 'Existing receipt must not be overwritten')
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_text(json.dumps(receipt, sort_keys=True, indent=2) + '\n', encoding='utf-8')
    print(f'PASS {len(rows)} product UI controls, API {args.expected_api}; no physical microphone claim')


if __name__ == '__main__':
    main()
