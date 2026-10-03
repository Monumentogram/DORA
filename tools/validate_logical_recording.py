"""Exact Stage 8.4C successor; never self-certifies physical/CI/publication acceptance."""
import argparse
import hashlib
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = 'e7370bae6ee4d862d2a04e9d52c53bbeb68c6f6f'
BASE_TREE = 'd9ca08b4b69681d610f5e0fb6385a03f52f2524f'
BRANCH = 'stage/8.4c-logical-recording-chunks'
CONTRACT = 'docs/contracts/DORA_LOGICAL_RECORDING_8_4C_V0_1.json'
SELF = 'tools/validate_logical_recording.py'
CONTRACT_SHA256 = '8c08dceb282f70566a53b8111d8257f99af19cb98b36d28f2403c9e13d193f0f'
STATUS_HEADER = '''## 2026-10-03 — Stage 8.4C logical recording and technical chunks

8.4C = PENDING_FINAL_PUBLICATION
8.3 / 8.4 = accepted predecessor PASS (external receipts).
8.5 = NOT_STARTED; Stage 8 = IN_PROGRESS.
One RecordingId, one exact OriginalAudioReference and one authorization unit across technical chunks.
See ADR-AUDIO-006; no Cloud implementation, PCM copies, schema change or VAD/profile changes.
PERF-REC-001 remains deferred/non-blocking; DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE remains OPEN.

'''


def validate_status_projection(current, historical):
    require(current == STATUS_HEADER + historical, 'Logical recording status or historical bytes changed')
    return historical


def require(value, message):
    if not value:
        raise ValueError(message)


def candidate(root=ROOT):
    return (root / CONTRACT).is_file()


def digest(data):
    return hashlib.sha256(data).hexdigest()


def normalized(path):
    return path.read_bytes().replace(b'\r\n', b'\n')


def validate_paths(actual, approved, complete=True):
    require(set(actual) <= set(approved) and (not complete or set(actual) == set(approved)), 'Unapproved or missing Stage8.4C paths')
    require(not any(Path(p).suffix.lower() in {'.aar', '.onnx', '.wav', '.pcm', '.apk', '.so', '.mp3'} for p in actual), 'No private/audio binaries')


def read_contract(root=ROOT):
    raw = normalized(root / CONTRACT)
    require(digest(raw) == CONTRACT_SHA256, 'Unsealed logical-recording contract')
    contract = json.loads(raw)
    require(contract['baseline'] == BASE and contract['status'] == 'PENDING_FINAL_PUBLICATION', 'Source cannot certify acceptance')
    require(contract['8.5'] == 'NOT_STARTED' and contract['securityRestoration'] == 'OPEN' and contract['cloudImplemented'] is False, 'Stage scope expanded')
    return contract


def validate_checkout(root=ROOT, *, allow_working=None):
    import validate_encrypted_persistence as persistence
    import validate_original_audio_lifecycle as lifecycle
    import validate_vad_runtime as parent
    import validate_development_device_security as security
    import logical_recording_ci_profile as ci
    contract = read_contract(root)
    git = lambda *args: persistence.git(root, *args)
    require(git('rev-parse', BASE + '^{tree}').decode().strip() == BASE_TREE, 'Accepted baseline changed')
    require(git('merge-base', BASE, 'HEAD').decode().strip() == BASE, 'Wrong stacked baseline')
    head = git('rev-parse', 'HEAD').decode().strip()
    branch = git('branch', '--show-current').decode().strip()
    working = persistence.working_verification(allow_working, os.environ)
    dirty = bool(git('status', '--porcelain', '--untracked-files=all'))
    require(branch == BRANCH or (not branch and os.environ.get('GITHUB_ACTIONS') == 'true'), 'Wrong Stage8.4C branch')
    require(working or not dirty, 'Publication requires clean source')
    if os.environ.get('GITHUB_ACTIONS') or os.environ.get('GITHUB_EVENT_NAME'):
        require(not working and not dirty and os.environ.get('GITHUB_SHA') == head and os.environ.get('GITHUB_REF') == 'refs/heads/' + BRANCH
                and os.environ.get('GITHUB_REPOSITORY') == 'Monumentogram/DORA' and os.environ.get('GITHUB_EVENT_NAME') == 'push', 'Exact-SHA CI identity mismatch')
    approved = set(contract['files']) | {CONTRACT, SELF}
    actual = set(git('diff', '--name-only', '--no-renames', BASE).decode().splitlines()) | set(git('ls-files', '--others', '--exclude-standard').decode().splitlines())
    validate_paths(actual, approved)
    for line in git('rev-list', '--reverse', '--parents', BASE + '..HEAD').decode().splitlines():
        parts = line.split()
        require(len(parts) == 2, 'No merge/rewrite history')
        validate_paths(git('diff', '--name-only', parts[1], parts[0]).decode().splitlines(), approved, False)
    require(head != BASE or working, 'Implementation commit absent')
    for path, sha in contract['files'].items():
        require(digest(normalized(root / path)) == sha, 'Stage8.4C file seal differs: ' + path)
    # Verify the immutable accepted predecessor, including every old source seal.
    old = parent.read_contract(root)
    for path, sha in old['files'].items():
        require(digest(git('show', BASE + ':' + path).replace(b'\r\n', b'\n')) == sha, 'Accepted Stage8.4 seal changed')
    historical = set(git('ls-tree', '-r', '--name-only', BASE, 'docs/evidence', 'docs/contracts', 'android/vendor', 'android/poc', 'android/ml').decode().splitlines())
    require(not historical.intersection(actual), 'Historical evidence, Recovery, VAD or dependency changed')
    require(ci.normalize((root / '.github/workflows/android-ci.yml').read_text(encoding='utf-8')) == git('show', BASE + ':.github/workflows/android-ci.yml').decode(), 'Inherited CI changed')
    for name in ('DORA_MVP1_STAGE_STATUS.md', 'DORA_MVP1_IMPLEMENTATION_BACKLOG.md'):
        validate_status_projection((root / 'docs' / name).read_text(encoding='utf-8'), git('show', BASE + ':docs/' + name).decode())
    require(json.loads((root / security.BLOCKER_PATH).read_text()) == security.RESTORATION_BLOCKER, 'Security restoration must remain OPEN')
    security.validate_development_sources(root)
    persistence.validate_product_sources(persistence.read_product_sources(root))
    test_root = root / 'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/persistence'
    tests = persistence.discover_device_tests({p.relative_to(test_root).as_posix(): p.read_text(encoding='utf-8') for p in test_root.rglob('*.kt')})
    persistence.validate_device_inventory(normalized(root / ci.INVENTORY), tests, contract['files'][ci.INVENTORY])
    require(set(json.loads((root / ci.OLD_INVENTORY).read_text())['tests']) <= tests, 'Historical test removed')
    legacy = lifecycle.historical_parent(root)
    inherited = set(legacy['implementation_paths']) | set(git('diff', '--name-only', lifecycle.PARENT, BASE).decode().splitlines())
    result = {**legacy, 'implementation_paths': sorted(inherited | approved), 'vad_runtime_graph': True, 'release_graph_sha256': old['releaseGraphSha256']}
    parent.approved_graph(root, result)
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--working', action='store_true', default=None)
    validate_checkout(allow_working=parser.parse_args().working)
    print('PASS Stage8.4C repository contract; external publication remains pending')
