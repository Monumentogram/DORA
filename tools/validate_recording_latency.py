"""Bounded latency successor. Source admission cannot certify physical timing or publication."""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PARENT = 'c596f5256e34bebf308d4c90d89c9f97affc3abb'
PARENT_TREE = '6fb7b7cb1b96de2062ac5918eca0028d7a26fab4'
BRANCH = 'stage/8.3-pause-resume-latency'
CONTRACT = 'docs/contracts/DORA_RECORDING_LATENCY_8_3_V0_1.json'
CONTRACT_SHA256 = '65080506cc58adeb188073f7f8d86d650825ae93534aeb5555198db33faa052f'
STATUS_HEADER = '''## 2026-10-02 — Stage 8.3 pause/resume latency remediation

Remediation = PENDING_FINAL_PUBLICATION
Historical 8.3 functional gate = PASS (external publication for c596f5256e34bebf308d4c90d89c9f97affc3abb)
Pause latency = P1 OPEN until all physical thresholds pass
8.4 = NOT_STARTED
Stage 8 = IN_PROGRESS
Source checks do not certify the 20-cycle physical timing, exact-SHA CI or publication gates.

'''
LIMITS_MS = {'pause_ack': 100, 'pause_admission': 250, 'pause_confirmed': 500,
             'resume_ack': 100, 'resume_native': 500, 'resume_first_pcm': 750, 'resume_confirmed': 750}
POLICY_PATH = 'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/database/SqlCipherJournalHelperFactory.kt'
POLICY_SOURCE_SHA256 = 'f04bc193d2bd2000d9313c946ff45d2f42a72a0f4540220c4dabe1e6171eb592'


def project_policy_sources(files):
    """Exact reviewed combined-query source projects to immutable predecessor checks only."""
    source = files.get(POLICY_PATH, '')
    if 'POLICY_QUERY' not in source:
        return files
    require(hashlib.sha256(source.replace('\r\n', '\n').encode()).hexdigest() == POLICY_SOURCE_SHA256,
            'Unreviewed combined SQL policy source')
    import validate_encrypted_persistence as persistence
    projected = dict(files)
    projected[POLICY_PATH] = persistence.git(ROOT, 'show', PARENT + ':' + POLICY_PATH).decode()
    return projected


def require(condition, message):
    if not condition:
        raise ValueError(message)


def candidate(root=ROOT):
    return (root / CONTRACT).exists()


def validate_status_projection(current, historical):
    require(current == STATUS_HEADER + historical.replace('\r\n', '\n'), 'Latency status projection changed')
    return historical


def distribution(values):
    require(len(values) == 20 and all(type(v) in (int, float) and math.isfinite(v) and v >= 0 for v in values),
            'Exactly 20 finite nonnegative measurements required; missing/outliers cannot be excluded')
    ordered = sorted(values)
    return dict(min=ordered[0], median=(ordered[9] + ordered[10]) / 2, p95=ordered[18], max=ordered[19])


def p95_thresholds(measurements):
    """Only the named p95 comparison. Never an overall physical/outlier acceptance verdict."""
    require(set(measurements) == set(LIMITS_MS), 'Physical timing metric omitted or substituted')
    stats = {key: distribution(values) for key, values in measurements.items()}
    return stats, all(stats[key]['p95'] <= limit for key, limit in LIMITS_MS.items())


def validate_checkout(root=ROOT, *, allow_working=None):
    import validate_encrypted_persistence as persistence
    import validate_original_audio_lifecycle as lifecycle
    import validate_product_recording as recording
    import product_recording_ci_profile as parent_ci
    import recording_latency_ci_profile as ci
    allow_working = persistence.working_verification(allow_working, os.environ)
    git = lambda *args: persistence.git(root, *args)
    historical = lambda commit, path: git('show', commit + ':' + path)
    require(git('rev-parse', PARENT + '^{tree}').decode().strip() == PARENT_TREE, 'Accepted recording tree changed')
    legacy = lifecycle.historical_parent(root)
    inherited = set(legacy['implementation_paths'])
    # Replay sealed predecessor inventories and source checks from immutable accepted Git objects.
    for module, commit in ((lifecycle, recording.PARENT), (recording, PARENT)):
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
    require(parent_ci.normalize(historical(PARENT, workflow_path).decode()) == historical(recording.PARENT, workflow_path).decode(),
            'Accepted recording CI changed')
    raw = (root / CONTRACT).read_bytes()
    require(hashlib.sha256(raw).hexdigest() == CONTRACT_SHA256, 'Latency contract not sealed')
    contract = json.loads(raw)
    require(contract['sql_policy_source_sha256'] == POLICY_SOURCE_SHA256, 'SQL policy source seal changed')
    require(contract['parent_sha'] == PARENT and contract['status'] == 'PENDING_FINAL_PUBLICATION', 'Source cannot certify publication')
    require(contract['physical_p95_limits_ms'] == LIMITS_MS and contract['physical_trials_per_control'] == 20,
            'Physical thresholds or denominator changed')
    head = git('rev-parse', 'HEAD').decode().strip()
    branch = git('branch', '--show-current').decode().strip()
    dirty = bool(git('status', '--porcelain', '--untracked-files=all'))
    require(branch == BRANCH or (branch == '' and os.environ.get('GITHUB_ACTIONS') == 'true'), 'Wrong latency branch')
    require(allow_working or not dirty, 'Latency checkout must be clean')
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
    require(actual == expected, 'Latency changed-file inventory mismatch')
    for line in git('rev-list', '--reverse', '--parents', PARENT + '..' + head).decode().splitlines():
        parts = line.split()
        require(len(parts) == 2, 'Latency merge commit rejected')
        paths = set(git('diff', '--name-only', parts[1], parts[0]).decode().splitlines())
        require(bool(paths) and paths <= expected, 'Unbounded latency history')
    require(head != PARENT or allow_working, 'Latency implementation commit absent')
    require(not any(p.startswith(('android/poc/', 'android/vendor/', 'docs/evidence/poc-recovery-001/', 'android/core/audio/schemas/'))
                    or p.endswith('gradle.lockfile') or p == 'android/gradle/verification-metadata.xml' for p in expected),
            'Accepted engines, schema, native dependencies or evidence changed')
    frozen = [lifecycle.CONTRACT, persistence.CONTRACT, recording.CONTRACT, ci.OLD_INVENTORY,
              'docs/adr/ADR-DEV-001-temporary-app-lock-exception.md',
              'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/auth/AndroidAppLock.kt']
    for variant in ('debug', 'release'):
        frozen.append(f'android/core/audio/src/{variant}/kotlin/com/monumentogram/dora/audio/persistence/auth/DevelopmentAppLockOverride.kt')
    for path in frozen:
        require((root / path).read_bytes().replace(b'\r\n', b'\n') == historical(PARENT, path).replace(b'\r\n', b'\n'), 'Frozen authority changed')
    for path in ('docs/DORA_MVP1_STAGE_STATUS.md', 'docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md'):
        require((root / path).read_bytes() == STATUS_HEADER.encode() + historical(PARENT, path), 'Historical status bytes changed')
        recording.validate_status_projection(historical(PARENT, path).decode(), historical(recording.PARENT, path).decode())
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
    inventory_raw = (root / ci.INVENTORY).read_bytes()
    persistence.validate_device_inventory(inventory_raw, tests, contract['device_inventory_sha256'])
    require(set(json.loads(historical(PARENT, ci.OLD_INVENTORY))['tests']) <= set(json.loads(inventory_raw)['tests']),
            'Parent device test removed')
    return {**legacy, 'implementation_paths': sorted(inherited | expected)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--working', action='store_true', default=None)
    args = parser.parse_args()
    validate_checkout(allow_working=args.working)
    print('PASS bounded latency source admission; physical timing and publication remain separate')


if __name__ == '__main__':
    main()
