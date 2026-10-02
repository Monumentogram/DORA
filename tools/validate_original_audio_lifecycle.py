"""Bounded Stage 8.2C successor; no self-certification of runtime or publication."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
PARENT = '5af0f28046237a42d2d5b8c51a3b419013d7ee8b'
PARENT_TREE = 'a4ccdb40da1a5b5819077ed3555c19e4425321a4'
BRANCH = 'stage/8.2c-original-audio-lifecycle'
CONTRACT = 'docs/contracts/DORA_ORIGINAL_AUDIO_LIFECYCLE_8_2C_V0_1.json'
CONTRACT_SHA256 = '12667f3a2583c43e9e5a6d86ab9aec1a03780640888b57094759346d0207ce9e'
SCHEMA = 'android/core/audio/schemas/com.monumentogram.dora.audio.persistence.journal.AudioJournalDatabase/'
STATUS_HEADER = '''## 2026-10-02 \u2014 Stage 8.2C original audio lifecycle

8.2C = PENDING_FINAL_PUBLICATION
Target = ORIGINAL_AUDIO_LIFECYCLE_RUNTIME_READY
8.2 = PASS (external publication for 5af0f28046237a42d2d5b8c51a3b419013d7ee8b)
8.3 = NOT_STARTED
Stage 8 = IN_PROGRESS
Exact-SHA CI, independent review, artifacts and Sheet readback remain publication gates.

'''


def require(condition, message):
    if not condition: raise ValueError(message)


def parent_file(root, path):
    import validate_encrypted_persistence as parent
    return parent.git(root, 'show', PARENT + ':' + path)


def candidate(root=ROOT):
    import validate_encrypted_persistence as parent
    return (root / CONTRACT).exists() or parent.git(root, 'branch', '--show-current').decode().strip() == BRANCH


def validate_status_projection(current, historical):
    require(current == STATUS_HEADER + historical, 'Lifecycle status must preserve exact parent text')
    return historical


def validate_schema(before, after):
    old, new = before['database'], after['database']
    require((old['version'], new['version']) == (1, 2), 'Only non-destructive 1 to 2 migration admitted')
    old_tables = {e['tableName']: e for e in old['entities']}
    new_tables = {e['tableName']: e for e in new['entities']}
    require(len(new_tables) == len(new['entities']) and set(new_tables) == set(old_tables) | {'original_audio_reference'},
            'Migration table scope differs')
    require(all(new_tables[name] == entity for name, entity in old_tables.items()), 'Existing v1 schema changed')
    source = new_tables['original_audio_reference']
    require(source['primaryKey']['columnNames'] == ['assetId'] and source['foreignKeys'] == [{
        'table': 'audio_asset', 'onDelete': 'RESTRICT', 'onUpdate': 'NO ACTION',
        'columns': ['assetId'], 'referencedColumns': ['assetId']}], 'Source provenance reference may not cascade')
    require([(i['columnNames'], i['unique']) for i in source['indices']] == [(['digest'], True)], 'Digest collision constraint absent')
    require([f['columnName'] for f in source['fields']] == ['assetId', 'version', 'digest', 'frames', 'unavailableReason'],
            'Unadmitted lifecycle metadata')


def historical_parent(root):
    """Run the parent's source-specific checks against the immutable accepted Git objects."""
    import validate_encrypted_persistence as p
    require(p.git(root, 'rev-parse', PARENT + '^{tree}').decode().strip() == PARENT_TREE, 'Accepted parent tree changed')
    raw = parent_file(root, p.CONTRACT)
    require(hashlib.sha256(raw).hexdigest() == p.CONTRACT_SHA256, 'Accepted Stage 8.2 contract changed')
    contract = json.loads(raw)
    history = []
    for line in p.git(root, 'rev-list', '--reverse', '--parents', p.BASELINE + '..' + PARENT).decode().splitlines():
        parts = line.split(); require(len(parts) == 2, 'Accepted parent history is nonlinear')
        history.append((parts[1], parts[0], p.git(root, 'diff', '--name-only', parts[1], parts[0]).decode().splitlines()))
    p.validate_history(history, PARENT, contract['implementation_paths'], contract['evidence_paths'])
    sources = {path: parent_file(root, path).decode() for path in p.read_product_sources(root) if path in contract['implementation_paths']}
    # Include inherited product files and build inputs even where the parent's delta omitted them.
    for path in p.read_product_sources(root):
        exists = p.git(root, 'ls-tree', '--name-only', PARENT, '--', path).decode().strip()
        if exists: sources[path] = parent_file(root, path).decode()
    p.validate_product_sources(sources)
    for path in ('docs/DORA_MVP1_STAGE_STATUS.md', 'docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md'):
        p.validate_status_projection(parent_file(root, path).decode(), p.git(root, 'show', p.BASELINE + ':' + path).decode())
    import persistence_ci_profile
    persistence_ci_profile.validate(parent_file(root, '.github/workflows/android-ci.yml').decode())
    p.validate_historical_audio(root)
    return contract


def validate_checkout(root=ROOT, *, allow_working=None):
    import validate_encrypted_persistence as p
    import original_audio_ci_profile as ci
    allow_working = p.working_verification(allow_working, os.environ)
    raw = (root / CONTRACT).read_bytes()
    require(CONTRACT_SHA256 is not None and hashlib.sha256(raw).hexdigest() == CONTRACT_SHA256, 'Lifecycle inventory is not sealed')
    contract = json.loads(raw)
    require(contract['parent_sha'] == PARENT and contract['status'] == 'PENDING_FINAL_PUBLICATION', 'Source cannot certify future publication')
    legacy = historical_parent(root)
    head = p.git(root, 'rev-parse', 'HEAD').decode().strip()
    branch = p.git(root, 'branch', '--show-current').decode().strip()
    dirty = bool(p.git(root, 'status', '--porcelain', '--untracked-files=all'))
    require(branch == BRANCH or (branch == '' and os.environ.get('GITHUB_ACTIONS') == 'true'), 'Wrong lifecycle branch')
    require(allow_working or not dirty, 'Lifecycle checkout must be clean')
    require(p.git(root, 'merge-base', PARENT, head).decode().strip() == PARENT, 'Wrong lifecycle parent')
    if os.environ.get('GITHUB_ACTIONS'):
        require(not allow_working and not dirty and os.environ.get('GITHUB_SHA') == head
                and os.environ.get('GITHUB_REF') == 'refs/heads/' + BRANCH
                and os.environ.get('GITHUB_EVENT_NAME') == 'push'
                and Path(os.environ.get('GITHUB_WORKSPACE', '')).resolve() == root.resolve(), 'Wrong exact-SHA CI context')
    expected = set(contract['implementation_paths'])
    require(len(expected) == len(contract['implementation_paths']) and CONTRACT in expected, 'Malformed lifecycle inventory')
    changed = set(p.git(root, 'diff', '--name-only', '--no-renames', PARENT).decode().splitlines())
    changed.update(p.git(root, 'ls-files', '--others', '--exclude-standard').decode().splitlines())
    require(changed == expected, 'Lifecycle changed-file inventory mismatch')
    for line in p.git(root, 'rev-list', '--reverse', '--parents', PARENT + '..' + head).decode().splitlines():
        parts = line.split(); require(len(parts) == 2, 'Lifecycle merge commit rejected')
        paths = set(p.git(root, 'diff', '--name-only', parts[1], parts[0]).decode().splitlines())
        require(bool(paths) and paths <= expected, 'Unbounded lifecycle history')
    require(head != PARENT or allow_working, 'Lifecycle implementation commit absent')
    require(not any(path.startswith(('android/poc/recovery/', 'docs/evidence/poc-recovery-001/', 'android/vendor/'))
                    or path.endswith('gradle.lockfile') or path == 'android/gradle/verification-metadata.xml'
                    for path in expected), 'Historical Recovery or admitted dependencies changed')
    for path in (p.CONTRACT, p.DEVICE_INVENTORY, SCHEMA + '1.json', 'docs/contracts/DORA_ASR_DATA_AND_VERSIONING_CONTRACT_V0_1.md',
                 'docs/contracts/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_4.json'):
        require((root / path).read_bytes().replace(b'\r\n', b'\n') == parent_file(root, path).replace(b'\r\n', b'\n'), 'Frozen authority changed')
    validate_schema(json.loads((root / (SCHEMA + '1.json')).read_text()), json.loads((root / (SCHEMA + '2.json')).read_text()))
    for path in ('docs/DORA_MVP1_STAGE_STATUS.md', 'docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md'):
        require((root / path).read_bytes() == STATUS_HEADER.encode() + parent_file(root, path), 'Historical status projection changed')
    require(ci.normalize((root / '.github/workflows/android-ci.yml').read_text()) == parent_file(root, '.github/workflows/android-ci.yml').decode(), 'Parent CI changed')
    p.validate_product_sources(p.read_product_sources(root))
    p.validate_backup(*[(root / path).read_text() for path in ('android/app/src/main/AndroidManifest.xml', 'android/app/src/main/res/xml/backup_rules.xml', 'android/app/src/main/res/xml/data_extraction_rules.xml')])
    import validate_encrypted_persistence_native as native
    inputs = native.read_inputs(root); native.validate_bundle(*inputs)
    native.validate_sbom(json.loads((root / 'android/vendor/sqlcipher/native-components.cdx.json').read_text()), inputs[2])
    import persistence_release_inventory as inventory
    inventory.read_and_validate(root, legacy, p.approved_release_graph(root, legacy))
    test_root = root / 'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/persistence'
    tests = p.discover_device_tests({f.relative_to(test_root).as_posix(): f.read_text(encoding='utf-8-sig') for f in test_root.rglob('*.kt')})
    p.validate_device_inventory((root / ci.INVENTORY).read_bytes(), tests, contract['device_inventory_sha256'])
    # Existing higher-level release validators need the complete bounded successor path projection.
    return {**legacy, 'implementation_paths': sorted(set(legacy['implementation_paths']) | expected)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--working', action='store_true', default=None)
    args = parser.parse_args()
    validate_checkout(allow_working=args.working)
    print('PASS bounded original audio source/migration/CI checks; runtime and publication remain separate')


if __name__ == '__main__': main()
