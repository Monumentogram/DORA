"""Bounded ADR-DEV-002 successor. Debug evidence never certifies production authentication."""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PARENT = '1fce758237942f03e35b6560b48a2f4983389eab'
PARENT_TREE = '8168148095c6f413bbf9a0576a4acd3f9678e139'
BRANCH = 'stage/8.3-instant-recording-controls'
CONTRACT = 'docs/contracts/DORA_DEVELOPMENT_DEVICE_SECURITY_V0_1.json'
CONTRACT_SHA256 = '7d167f4fc37c46b440097505d4199ed8325e00369267bb61dbe47fd841ad9ca1'
STATUS_HEADER = '## 2026-10-02 — Owner-authorized no-PIN development / Stage 8.3 remediation\n\n8.3 functional = PASS\n8.3 instant-control remediation = NOT_READY / PHYSICAL_ACCEPTANCE_PENDING\nADR-DEV-002 is a debug-only development exception; production authentication is not certified.\nDEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE = OPEN; blocks final/signed Alpha security acceptance.\nTracked checks: docs/security/DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE.json\nNew exact-SHA 30+30, vault continuity, CI, review and leak audit remain required.\n8.4 = NOT_STARTED\nStage 8 = IN_PROGRESS\n\n'
LIMITS_MS = {'pause_ack': 100, 'pause_admission': 50, 'pause_native': 200, 'pause_confirmed': 250,
             'resume_ack': 100, 'resume_native': 150, 'resume_first_pcm': 300, 'resume_confirmed': 300}
POLICY_PATH = 'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/database/SqlCipherJournalHelperFactory.kt'
POLICY_SOURCE_SHA256 = 'f04bc193d2bd2000d9313c946ff45d2f42a72a0f4540220c4dabe1e6171eb592'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def candidate(root=ROOT):
    return (root / CONTRACT).exists()


def validate_status_projection(current, historical):
    require(current == STATUS_HEADER + historical.replace('\r\n', '\n'), 'Instant-control status projection changed')
    return historical


def distribution(values):
    require(len(values) == 30 and all(type(v) in (int, float) and math.isfinite(v) and v >= 0 for v in values),
            'Exactly 30 finite nonnegative measurements required; missing/outliers cannot be excluded')
    ordered = sorted(values)
    return dict(min=ordered[0], median=(ordered[14] + ordered[15]) / 2, p95=ordered[28], max=ordered[29])


def p95_thresholds(measurements):
    """Only the named p95 comparison. Never an overall physical/outlier acceptance verdict."""
    require(set(measurements) == set(LIMITS_MS), 'Physical timing metric omitted or substituted')
    stats = {key: distribution(values) for key, values in measurements.items()}
    return stats, all(stats[key]['p95'] <= limit for key, limit in LIMITS_MS.items())


def validate_checkout(root=ROOT, *, allow_working=None):
    import validate_encrypted_persistence as persistence
    import validate_original_audio_lifecycle as lifecycle
    import validate_product_recording as recording
    import development_device_security_ci_profile as ci
    import instant_recording_ci_profile as instant_ci
    import validate_instant_recording as instant
    import validate_recording_latency as latency
    import recording_latency_ci_profile as latency_ci
    allow_working = persistence.working_verification(allow_working, os.environ)
    git = lambda *args: persistence.git(root, *args)
    historical = lambda commit, path: git('show', commit + ':' + path)
    require(git('rev-parse', PARENT + '^{tree}').decode().strip() == PARENT_TREE, 'Accepted recording tree changed')
    legacy = lifecycle.historical_parent(root)
    inherited = set(legacy['implementation_paths'])
    # Replay sealed predecessor inventories and source checks from immutable accepted Git objects.
    for module, commit in ((lifecycle, recording.PARENT), (recording, latency.PARENT), (latency, instant.PARENT), (instant, PARENT)):
        raw = historical(commit, module.CONTRACT)
        require(hashlib.sha256(raw).hexdigest() == module.CONTRACT_SHA256, 'Accepted contract changed')
        accepted = json.loads(raw)
        paths = set(git('diff', '--name-only', module.PARENT, commit).decode().splitlines())
        require(paths == set(accepted['implementation_paths']), 'Accepted implementation inventory changed')
        inherited.update(paths)
        for line in git('rev-list', '--reverse', '--parents', module.PARENT + '..' + commit).decode().splitlines():
            parts = line.split()
            require(len(parts) == 2, 'Accepted history is nonlinear')
            changed = set(git('diff', '--name-only', parts[1], parts[0]).decode().splitlines())
            require(bool(changed) and changed <= paths, 'Accepted history exceeded its inventory')
    recording.validate_manifest(historical(PARENT, 'android/app/src/main/AndroidManifest.xml').decode())
    recording.validate_capture_sources({p: historical(PARENT, p).decode() for p in inherited
        if p.startswith('android/app/src/main/kotlin/com/monumentogram/dora/recording/') and p.endswith('.kt')})
    workflow_path = '.github/workflows/android-ci.yml'
    require(instant_ci.normalize(historical(PARENT, workflow_path).decode()) == historical(instant.PARENT, workflow_path).decode(),
            'Accepted recording CI changed')
    raw = (root / CONTRACT).read_bytes()
    require(hashlib.sha256(raw).hexdigest() == CONTRACT_SHA256, 'Instant-control contract not sealed')
    contract = json.loads(raw)
    require(contract['sql_policy_source_sha256'] == POLICY_SOURCE_SHA256, 'SQL policy source seal changed')
    require(contract['parent_sha'] == PARENT and contract['status'] == 'PENDING_FINAL_PUBLICATION', 'Source cannot certify publication')
    require(contract['physical_p95_limits_ms'] == LIMITS_MS and contract['physical_trials_per_control'] == 30,
            'Physical thresholds or denominator changed')
    head = git('rev-parse', 'HEAD').decode().strip()
    branch = git('branch', '--show-current').decode().strip()
    dirty = bool(git('status', '--porcelain', '--untracked-files=all'))
    require(branch == BRANCH or (branch == '' and os.environ.get('GITHUB_ACTIONS') == 'true'), 'Wrong latency branch')
    require(allow_working or not dirty, 'Instant-control checkout must be clean')
    require(git('merge-base', PARENT, head).decode().strip() == PARENT, 'Wrong latency parent')
    if os.environ.get('GITHUB_ACTIONS'):
        require(not allow_working and not dirty and os.environ.get('GITHUB_SHA') == head
                and os.environ.get('GITHUB_REF') == 'refs/heads/' + BRANCH
                and os.environ.get('GITHUB_EVENT_NAME') == 'push'
                and Path(os.environ.get('GITHUB_WORKSPACE', '')).resolve() == root.resolve(), 'Wrong exact-SHA CI context')
    expected = set(contract['implementation_paths'])
    require(CONTRACT in expected and len(expected) == len(contract['implementation_paths']), 'Malformed latency inventory')
    actual = set(git('diff', '--name-only', '--no-renames', PARENT).decode().splitlines())
    actual.update(git('ls-files', '--others', '--exclude-standard').decode().splitlines())
    require(actual == expected, 'Instant-control changed-file inventory mismatch')
    for line in git('rev-list', '--reverse', '--parents', PARENT + '..' + head).decode().splitlines():
        parts = line.split()
        require(len(parts) == 2, 'Instant-control merge commit rejected')
        paths = set(git('diff', '--name-only', parts[1], parts[0]).decode().splitlines())
        require(bool(paths) and paths <= expected, 'Unbounded latency history')
    require(head != PARENT or allow_working, 'Instant-control implementation commit absent')
    require(not any(p.startswith(('android/poc/', 'android/vendor/', 'docs/evidence/poc-recovery-001/', 'android/core/audio/schemas/'))
                    or p.endswith('gradle.lockfile') or p == 'android/gradle/verification-metadata.xml' for p in expected),
            'Accepted engines, schema, native dependencies or evidence changed')
    frozen = [lifecycle.CONTRACT, persistence.CONTRACT, recording.CONTRACT, latency.CONTRACT, instant.CONTRACT, instant_ci.INVENTORY,
              'docs/adr/ADR-DEV-001-temporary-app-lock-exception.md',
              'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/auth/AndroidAuthProof.kt',
              'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/auth/AppLockAuthenticationActivity.kt',
              'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/keys/AndroidVaultKeyBackend.kt']
    for variant in ('debug', 'release'):
        frozen.append(f'android/core/audio/src/{variant}/kotlin/com/monumentogram/dora/audio/persistence/auth/DevelopmentAppLockOverride.kt')
    historical_artifacts = set(git('ls-tree', '-r', '--name-only', PARENT, 'docs/evidence', 'docs/contracts').decode().splitlines())
    require(not historical_artifacts.intersection(actual), 'Historical evidence or contract modified')
    for path in frozen:
        require((root / path).read_bytes().replace(b'\r\n', b'\n') == historical(PARENT, path).replace(b'\r\n', b'\n'), 'Frozen authority changed')
    for path in ('docs/DORA_MVP1_STAGE_STATUS.md', 'docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md'):
        require((root / path).read_bytes() == STATUS_HEADER.encode() + historical(PARENT, path), 'Historical status bytes changed')
        instant.validate_status_projection(historical(PARENT, path).decode(), historical(instant.PARENT, path).decode())
        latency.validate_status_projection(historical(instant.PARENT, path).decode(), historical(latency.PARENT, path).decode())
        recording.validate_status_projection(historical(latency.PARENT, path).decode(), historical(recording.PARENT, path).decode())
        require(historical(recording.PARENT, path) == lifecycle.STATUS_HEADER.encode() + lifecycle.parent_file(root, path),
                'Accepted lifecycle status changed')
    workflow = (root / workflow_path).read_text(encoding='utf-8')
    require(ci.GATE in workflow and ci.normalize(workflow) == historical(PARENT, workflow_path).decode(), 'Parent CI gates changed')
    persistence.validate_product_sources(persistence.read_product_sources(root))
    manifest_source = (root / 'android/app/src/main/AndroidManifest.xml').read_text()
    recording.validate_manifest(manifest_source)
    recording.validate_capture_sources({p.relative_to(root).as_posix(): p.read_text(encoding='utf-8')
        for p in (root / 'android/app/src/main/kotlin/com/monumentogram/dora/recording').glob('*.kt')})
    manifest = ET.fromstring(manifest_source)
    for permission in list(manifest.findall('uses-permission')):
        if permission.get(recording.ANDROID + 'name') == 'android.permission.RECORD_AUDIO':
            manifest.remove(permission)
    persistence.validate_backup(ET.tostring(manifest, encoding='unicode'), *[(root / p).read_text() for p in (
        'android/app/src/main/res/xml/backup_rules.xml', 'android/app/src/main/res/xml/data_extraction_rules.xml')])
    import validate_encrypted_persistence_native as native
    inputs = native.read_inputs(root)
    native.validate_bundle(*inputs)
    native.validate_sbom(json.loads((root / 'android/vendor/sqlcipher/native-components.cdx.json').read_text()), inputs[2])
    import persistence_release_inventory as inventory
    inventory.read_and_validate(root, legacy, persistence.approved_release_graph(root, legacy))
    test_root = root / 'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/persistence'
    tests = persistence.discover_device_tests({f.relative_to(test_root).as_posix(): f.read_text(encoding='utf-8-sig') for f in test_root.rglob('*.kt')})
    inventory_raw = (root / instant_ci.INVENTORY).read_bytes()
    persistence.validate_device_inventory(inventory_raw, tests, contract['device_inventory_sha256'])
    require(set(json.loads(historical(PARENT, instant_ci.INVENTORY))['tests']) <= set(json.loads(inventory_raw)['tests']),
            'Parent device test removed')
    validate_development_sources(root)
    require(json.loads((root / BLOCKER_PATH).read_text(encoding='utf-8')) == RESTORATION_BLOCKER, 'Restoration blocker must remain OPEN')
    return {**legacy, 'implementation_paths': sorted(inherited | expected)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--working', action='store_true', default=None)
    parser.add_argument('--alpha-close', action='store_true')
    args = parser.parse_args()
    if args.alpha_close:
        require_alpha_security_restored(json.loads((ROOT / BLOCKER_PATH).read_text(encoding='utf-8')))
    validate_checkout(allow_working=args.working)
    print('PASS bounded debug-only device-security source admission; physical and production-authentication acceptance remain separate')




BLOCKER_PATH = "docs/security/DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE.json"
RESTORATION_BLOCKER = {'id': 'DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE', 'state': 'OPEN', 'blocks': ['FINAL_ALPHA_CLOSURE', 'SIGNED_ALPHA_SECURITY_ACCEPTANCE'], 'alpha_security_acceptance': False, 'evidence_sha256': None, 'checks': {'android_credential_restored': False, 'no_device_lock_marker_removed': False, 'no_prompt_marker_removed': False, 'dora_restarted': False, 'is_device_secure_true': False, 'normal_app_lock_prompt_verified': False, 'background_and_device_lock_revoke': False, 'resume_requires_fresh_proof': False, 'release_overrides_impossible': False, 'same_encrypted_vault_readable': False}}


def require_alpha_security_restored(blocker):
    import re
    require(blocker.get('id') == RESTORATION_BLOCKER['id'] and blocker.get('state') == 'CLOSED'
            and blocker.get('alpha_security_acceptance') is True
            and set(blocker.get('checks', {})) == set(RESTORATION_BLOCKER['checks'])
            and all(value is True for value in blocker['checks'].values())
            and re.fullmatch(r'[0-9a-f]{64}', blocker.get('evidence_sha256') or '') is not None,
            'DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE: secure-device evidence required')


def validate_development_sources(root):
    base = 'android/core/audio/src/'
    auth = '/kotlin/com/monumentogram/dora/audio/persistence/auth/'
    release = (root / (base + 'release' + auth + 'DevelopmentDeviceSecurityOverride.kt')).read_text(encoding='utf-8')
    import re
    compact = re.sub(r'\s+', '', release)
    require('):Boolean=false' in compact and 'valnotice:String?=null' in compact,
            'Release must compile the unconditional denial implementation')
    require(all(token not in release for token in ('development-device-no-lock', 'BuildConfig', '.exists(', '.isFile', 'isDeviceSecure')),
            'Release exception reads development state')
    debug = (root / (base + 'debug' + auth + 'DevelopmentDeviceSecurityOverride.kt')).read_text(encoding='utf-8')
    require(all(token in debug for token in ('com.monumentogram.dora.debug', 'development-device-no-lock', 'marker.canonicalFile == marker', 'marker.isFile', 'marker.canRead()', 'marker.length() == 0L')),
            'Exact private debug opt-in conditions missing')
    policy = (root / (base + 'main' + auth + 'AndroidDeviceSecurityPolicy.kt')).read_text(encoding='utf-8')
    require('application.noBackupFilesDir' in policy and 'ApplicationInfo.FLAG_DEBUGGABLE' in policy,
            'Device security marker must use private no-backup application storage')
    lock = (root / (base + 'main' + auth + 'AndroidAppLock.kt')).read_text(encoding='utf-8')
    manager = (root / (base + 'main/kotlin/com/monumentogram/dora/audio/persistence/runtime/AndroidRecordingAccessManager.kt')).read_text(encoding='utf-8')
    require('deviceSecurity.deviceReady()' in lock and 'deviceSecurity.credentialAvailable()' in lock
            and 'appLock.deviceSecurity.credentialAvailable() && epoch == requestEpoch' in manager,
            'App Lock and recording must share the effective policy with epoch fencing')
    require('isDeviceSecure' not in lock + manager, 'Ad-hoc security policy reintroduced')


if __name__ == '__main__':
    main()
