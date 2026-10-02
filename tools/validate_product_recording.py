"""Bounded recording successor; source admission never certifies microphone/publication gates."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
PARENT = 'a1b9a8a56fe62ceb2332a0e7147ab537f8ce3431'
PARENT_TREE = 'f16bd2a019ae94ed5f058c93b8e86e0d37c2b1c1'
BRANCH = 'stage/8.3-product-recording'
CONTRACT = 'docs/contracts/DORA_PRODUCT_RECORDING_8_3_V0_1.json'
CONTRACT_SHA256 = '7e42e7e70907b996b99f4cc3d733702fd8a554623c41c78c17cae855825c3208'
STATUS_HEADER = '''## 2026-10-02 \u2014 Stage 8.3 product recording

8.3 = PENDING_FINAL_PUBLICATION
Target = PRODUCT_RECORDING_RUNTIME_READY
8.2C = PASS (external publication for a1b9a8a56fe62ceb2332a0e7147ab537f8ce3431)
8.4 = NOT_STARTED
Stage 8 = IN_PROGRESS
Physical POCO microphone, exact-SHA CI, independent review, artifact audit and Sheet readback remain publication gates.

'''
ANDROID = '{http://schemas.android.com/apk/res/android}'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def candidate(root=ROOT):
    return (root / CONTRACT).exists()


def validate_status_projection(current, historical):
    require(current == STATUS_HEADER + historical.replace('\r\n', '\n'), 'Recording status projection changed')
    return historical


def validate_manifest(source):
    tree = ET.fromstring(source)
    permissions = {node.get(ANDROID + 'name') for node in tree.findall('uses-permission')}
    required = {'android.permission.' + name for name in (
        'RECORD_AUDIO', 'FOREGROUND_SERVICE', 'FOREGROUND_SERVICE_MICROPHONE', 'POST_NOTIFICATIONS', 'WAKE_LOCK')}
    require(required <= permissions, 'Required microphone/foreground permissions missing')
    service = [n for n in tree.findall('application/service') if n.get(ANDROID + 'name', '').endswith('ProductRecordingService')]
    require(len(service) == 1, 'Recording service missing or duplicated')
    require(service[0].get(ANDROID + 'exported') == 'false'
            and service[0].get(ANDROID + 'foregroundServiceType') == 'microphone'
            and service[0].get(ANDROID + 'stopWithTask') == 'false', 'Recording service boundary changed')


def validate_capture_sources(sources):
    for path, source in sources.items():
        for forbidden in ('FileOutputStream', 'RandomAccessFile', 'MediaRecorder(', '.writeBytes(',
                          '.writeText(', 'android.util.Log', 'printStackTrace', 'android/poc/capture'):
            require(forbidden not in source, 'Unadmitted recording store/diagnostic: ' + path)


def validate_checkout(root=ROOT, *, allow_working=None):
    import validate_recording_latency as latency
    if latency.candidate(root):
        return latency.validate_checkout(root, allow_working=allow_working)
    import validate_encrypted_persistence as persistence
    import validate_original_audio_lifecycle as lifecycle
    import product_recording_ci_profile as ci
    allow_working = persistence.working_verification(allow_working, os.environ)
    git = lambda *args: persistence.git(root, *args)
    historical = lambda path: git('show', PARENT + ':' + path)
    require(git('rev-parse', PARENT + '^{tree}').decode().strip() == PARENT_TREE, 'Accepted parent tree changed')
    legacy = lifecycle.historical_parent(root)
    parent_raw = historical(lifecycle.CONTRACT)
    require(hashlib.sha256(parent_raw).hexdigest() == lifecycle.CONTRACT_SHA256, 'Accepted lifecycle contract changed')
    parent_contract = json.loads(parent_raw)
    parent_paths = set(git('diff', '--name-only', lifecycle.PARENT, PARENT).decode().splitlines())
    require(parent_paths == set(parent_contract['implementation_paths']), 'Accepted lifecycle inventory changed')
    raw = (root / CONTRACT).read_bytes()
    require(hashlib.sha256(raw).hexdigest() == CONTRACT_SHA256, 'Recording inventory not sealed')
    contract = json.loads(raw)
    require(contract['parent_sha'] == PARENT and contract['status'] == 'PENDING_FINAL_PUBLICATION', 'Source cannot certify publication')
    head = git('rev-parse', 'HEAD').decode().strip()
    branch = git('branch', '--show-current').decode().strip()
    dirty = bool(git('status', '--porcelain', '--untracked-files=all'))
    require(branch == BRANCH or (branch == '' and os.environ.get('GITHUB_ACTIONS') == 'true'), 'Wrong recording branch')
    require(allow_working or not dirty, 'Recording checkout must be clean')
    require(git('merge-base', PARENT, head).decode().strip() == PARENT, 'Wrong recording parent')
    if os.environ.get('GITHUB_ACTIONS'):
        require(not allow_working and not dirty and os.environ.get('GITHUB_SHA') == head
                and os.environ.get('GITHUB_REF') == 'refs/heads/' + BRANCH
                and os.environ.get('GITHUB_EVENT_NAME') == 'push'
                and Path(os.environ.get('GITHUB_WORKSPACE', '')).resolve() == root.resolve(), 'Wrong exact-SHA CI context')
    expected = set(contract['implementation_paths'])
    require(CONTRACT in expected and len(expected) == len(contract['implementation_paths']), 'Malformed recording inventory')
    actual = set(git('diff', '--name-only', '--no-renames', PARENT).decode().splitlines())
    actual.update(git('ls-files', '--others', '--exclude-standard').decode().splitlines())
    require(actual == expected, 'Recording changed-file inventory mismatch')
    for line in git('rev-list', '--reverse', '--parents', PARENT + '..' + head).decode().splitlines():
        parts = line.split()
        require(len(parts) == 2, 'Recording merge commit rejected')
        paths = set(git('diff', '--name-only', parts[1], parts[0]).decode().splitlines())
        require(bool(paths) and paths <= expected, 'Unbounded recording history')
    require(head != PARENT or allow_working, 'Recording implementation commit absent')
    require(not any(p.startswith(('android/poc/', 'android/vendor/', 'docs/evidence/poc-recovery-001/', 'android/core/audio/schemas/'))
                    or p.endswith('gradle.lockfile') or p == 'android/gradle/verification-metadata.xml' for p in expected),
            'Accepted engines, schema, native dependencies or evidence changed')
    for path in (lifecycle.CONTRACT, persistence.CONTRACT, 'docs/contracts/original-audio-8.2c-device-tests.json'):
        require((root / path).read_bytes().replace(b'\r\n', b'\n') == historical(path).replace(b'\r\n', b'\n'), 'Frozen authority changed')
    for path in ('docs/DORA_MVP1_STAGE_STATUS.md', 'docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md'):
        current = (root / path).read_text(encoding='utf-8')
        require(current == STATUS_HEADER + historical(path).decode().replace('\r\n', '\n'), 'Historical status projection changed')
        require(historical(path) == lifecycle.STATUS_HEADER.encode() + lifecycle.parent_file(root, path), 'Accepted parent status changed')
    workflow = (root / '.github/workflows/android-ci.yml').read_text(encoding='utf-8')
    require(ci.normalize(workflow) == historical('.github/workflows/android-ci.yml').decode(), 'Parent CI gates changed')
    require(ci.GATE in workflow, 'Recording CI gate missing')
    persistence.validate_product_sources(persistence.read_product_sources(root))
    validate_manifest((root / 'android/app/src/main/AndroidManifest.xml').read_text())
    validate_capture_sources({p.relative_to(root).as_posix(): p.read_text(encoding='utf-8')
                             for p in (root / 'android/app/src/main/kotlin/com/monumentogram/dora/recording').glob('*.kt')})
    # The historical gate forbids the new microphone permission. Validate the
    # exact new manifest above, then project only that admitted permission away
    # for the unchanged historical backup checks.
    manifest = ET.fromstring((root / 'android/app/src/main/AndroidManifest.xml').read_text())
    for permission in list(manifest.findall('uses-permission')):
        if permission.get(ANDROID + 'name') == 'android.permission.RECORD_AUDIO':
            manifest.remove(permission)
    persistence.validate_backup(ET.tostring(manifest, encoding='unicode'), *[(root / p).read_text() for p in (
        'android/app/src/main/res/xml/backup_rules.xml', 'android/app/src/main/res/xml/data_extraction_rules.xml')])
    import validate_encrypted_persistence_native as native
    inputs = native.read_inputs(root)
    native.validate_bundle(*inputs)
    native.validate_sbom(json.loads((root / 'android/vendor/sqlcipher/native-components.cdx.json').read_text()), inputs[2])
    import persistence_release_inventory as inventory
    inventory.read_and_validate(root, legacy, persistence.approved_release_graph(root, legacy))
    return {**legacy, 'implementation_paths': sorted(set(legacy['implementation_paths']) | parent_paths | expected)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--working', action='store_true', default=None)
    args = parser.parse_args()
    validate_checkout(allow_working=args.working)
    print('PASS bounded recording source admission; physical microphone and publication remain separate')


if __name__ == '__main__':
    main()
