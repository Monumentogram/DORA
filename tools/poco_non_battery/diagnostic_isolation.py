"""Additive synthetic isolation inventory; never operates a physical device."""
import hashlib
from pathlib import Path
import zipfile

from run_encrypted_persistence_device import parse_results, require

PACKAGE = 'com.monumentogram.dora.audio.diagnostic'
INVENTORY = {
    'ProtectedHistoricalVaultTest': (
        'missingRoomMetadataCannotReachRepairCallback',
        'missingBindingCannotReachBootstrapCallback',
        'missingRoomIdentityCannotReachRepairCallback',
        'changedRoomIdentityCannotReachRepairCallback',
        'olderSchemaCannotReachMigrationCallback',
        'protectedSourcesRejectMutationWhileNewSourceRetainsRecoveryAndDeletion',
    ),
    'DiagnosticKeyFenceTest': ('protectedOrUnownedRunCannotCreateOrRemoveKey',),
    'DiagnosticPolicyLoaderTest': (
        'exactManifestBindsAll47SourcesAndVault',
        'deviceAndPackageMismatchesAreRejected',
        'sourceCardinalityAndDuplicatesAreRejected',
        'malformedManifestAndIdentifiersAreRejected',
        'ordinaryBuildDoesNotActivateUnpinnedPrivatePolicy',
        'privateFileIsReadFreshAtEveryOpen',
        'symlinksIncludingDanglingLinksAreRejected',
        'emptyOversizedAndDirectoryInputsAreRejected',
    ),
}
EXPECTED = {f'{PACKAGE}.{cls}#{method}' for cls, methods in INVENTORY.items() for method in methods}


def verify_output(output):
    rows = parse_results(output)
    require({row['name'] for row in rows} == EXPECTED, 'Diagnostic isolation inventory mismatch')
    return rows


def run(command):
    require(command('shell', 'getprop', 'ro.kernel.qemu') == '1', 'Isolation tests require emulator')
    apk = Path(__file__).resolve().parents[2] / 'android/core/audio/build/outputs/apk/androidTest/debug/audio-debug-androidTest.apk'
    with zipfile.ZipFile(apk) as archive:
        require('assets/dora-protected-policy.sha256' not in archive.namelist(),
                'Synthetic isolation APK must not contain private policy pin')
    require(command('install', '-r', str(apk)).endswith('Success'), 'Isolation APK install failed')
    rows = verify_output(command('shell', 'am', 'instrument', '-w', '-r', '-e', 'package', PACKAGE,
                                'com.monumentogram.dora.audio.test/androidx.test.runner.AndroidJUnitRunner',
                                timeout=300))
    return {'status': 'PASS_SYNTHETIC_PROTECTED_SOURCE_ISOLATION', 'tests': rows,
            'testApkSha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
            'physicalMicrophone': False, 'historicalPersistenceInventoryChanged': False}
