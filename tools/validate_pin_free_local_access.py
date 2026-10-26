"""ADR-0021 successor: preserve historical evidence and current encrypted storage checks."""
import argparse
import json
import os
from pathlib import Path
import xml.etree.ElementTree as ET

import pin_free_policy

ROOT = Path(__file__).resolve().parents[1]
BASE = 'a14d30a8172f13361fbe29b08cdb463651fc954a'
INVENTORY = 'docs/contracts/owner-arm82-persistence-tests-v1.json'


def candidate(root=ROOT):
    return (root / INVENTORY).is_file()


def validate_checkout(root=ROOT, *, allow_working=None):
    import validate_encrypted_persistence as persistence
    import validate_original_audio_lifecycle as lifecycle
    import validate_product_recording as recording
    import validate_vad_runtime as vad
    import validate_encrypted_persistence_native as native
    require = persistence.require
    git = lambda *args: persistence.git(root, *args).decode().strip()
    require(git('merge-base', BASE, 'HEAD') == BASE, 'ADR-0021 baseline is not an ancestor')
    dirty = bool(git('status', '--porcelain=v1', '--untracked-files=all'))
    working = persistence.working_verification(allow_working, os.environ)
    require(working or not dirty, 'ADR-0021 publication requires clean source')
    if os.environ.get('GITHUB_ACTIONS'):
        require(not working and not dirty and git('rev-parse', 'HEAD') == os.environ.get('GITHUB_SHA'),
                'ADR-0021 exact-SHA CI mismatch')
    historical = set(git('ls-tree', '-r', '--name-only', BASE,
                         'docs/evidence', 'docs/contracts', 'android/vendor', 'android/poc').splitlines())
    changed = set(git('diff', '--name-only', BASE).splitlines())
    require(not historical.intersection(changed), 'Historical evidence, contracts, vendor or Recovery changed')
    require(not pin_free_policy.violations(root), 'NO_APPLICATION_PIN_OR_BIOMETRIC_PROMPT')
    from owner_arm82_inventory import project_stage9_policy
    persistence.validate_product_sources(project_stage9_policy(persistence.read_product_sources(root), root))
    manifest = (root / 'android/app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
    recording.validate_manifest(manifest)
    capture = {p.relative_to(root).as_posix(): p.read_text(encoding='utf-8')
               for p in (root / 'android/app/src/main/kotlin/com/monumentogram/dora/recording').glob('*.kt')}
    # The accepted content-free terminal receipt predates ADR-0021. Keep its exact
    # implementation, then apply the older no-alternate-audio-store check elsewhere.
    controller = 'android/app/src/main/kotlin/com/monumentogram/dora/recording/RecordingController.kt'
    require(capture.pop(controller).replace('\r\n', '\n') ==
            persistence.git(root, 'show', BASE + ':' + controller).decode().replace('\r\n', '\n'),
            'Accepted capture/terminal-receipt implementation changed')
    recording.validate_capture_sources(capture)
    xml = ET.fromstring(manifest)
    for permission in list(xml.findall('uses-permission')):
        if permission.get(recording.ANDROID + 'name') == 'android.permission.RECORD_AUDIO':
            xml.remove(permission)
    persistence.validate_backup(ET.tostring(xml, encoding='unicode'), *[
        (root / path).read_text(encoding='utf-8') for path in (
            'android/app/src/main/res/xml/backup_rules.xml',
            'android/app/src/main/res/xml/data_extraction_rules.xml')])
    inputs = native.read_inputs(root)
    native.validate_bundle(*inputs)
    native.validate_sbom(json.loads((root / 'android/vendor/sqlcipher/native-components.cdx.json').read_text()), inputs[2])
    test_root = root / 'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/persistence'
    tests = persistence.discover_device_tests({p.relative_to(test_root).as_posix(): p.read_text(encoding='utf-8-sig')
                                              for p in test_root.rglob('*.kt')})
    import run_encrypted_persistence_device as device
    import validate_stage8_alpha as alpha
    contract = json.loads((root / INVENTORY).read_text())
    inventory, _ = device.validate_inventory(contract)
    require(contract.get('deferred_tests') == sorted(alpha.DEFERRED_DEVICE),
            'Only the accepted over-five-minute test deferrals are inherited')
    from owner_arm82_inventory import validate_accounting
    legacy = json.loads((root / 'docs/contracts/pin-free-local-access-device-tests-v1.json').read_text())
    accounted = validate_accounting(contract, tests, set(legacy['tests']), alpha.DEFERRED_DEVICE)
    old = json.loads((root / 'docs/contracts/persistence-8.6c3-device-tests.json').read_text())
    superseded = {name for name in old['tests'] if '.auth.AndroidAppLockTest#' in name or
                  '.auth.AndroidAuthProofTest#' in name or
                  name.endswith(('#credentialVaultRoundTripRelockReopenConfirmedDeletion', '#noCredentialCannotCreateStorage'))}
    require(set(old['tests']) - superseded - alpha.DEFERRED_DEVICE <= accounted,
            'Non-PIN security regression test removed')
    for path in ('docs/security/DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE.json',
                 'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/keys/AndroidVaultKeyBackend.kt'):
        require((root / path).read_bytes().replace(b'\r\n', b'\n') == persistence.git(root, 'show', BASE + ':' + path).replace(b'\r\n', b'\n'),
                'Historical blocker or noninteractive Keystore policy changed')
    legacy = lifecycle.historical_parent(root)
    result = {**legacy, 'local_access_profile': 'ADR-0021', 'vad_runtime_graph': True,
              'release_graph_sha256': vad.read_contract(root)['releaseGraphSha256']}
    vad.approved_graph(root, result)
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--working', action='store_true', default=None)
    args = parser.parse_args()
    validate_checkout(allow_working=args.working)
    print('PASS ADR-0021 source, encryption, historical preservation and exact test inventory; device acceptance separate')
