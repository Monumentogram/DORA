"""Exact Stage8.4 implementation successor. Source validation never certifies physical PASS."""
import argparse
import hashlib
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = '4073107108604136425071a51e63703374a660b8'
BASE_TREE = '236123b04b7efe099d3bec82352a18b7f7d7effa'
BRANCH = 'stage/8.4-vad-runtime-integration'
CONTRACT = 'docs/contracts/DORA_VAD_RUNTIME_8_4_V0_1.json'
CONTRACT_SHA256 = '9f5cf26ae7c211c6c312834c503172172909d5b19aa6fd6382767f5feabd5fe8'
SELF = 'tools/validate_vad_runtime.py'
GRAPH = 'docs/evidence/vad-8.4-runtime/ci-release-graph.json'
PROJECTS = frozenset({':app', ':core:audio', ':core:common', ':core:model', ':ml:vad-api', ':ml:vad-sherpa'})
PROFILE = 'docs/evidence/vad-8.4-runtime/profile.json'
PROFILE_SHA = '1c5ceb70f08593dfa96e522f43c51b7b28e36ab8b65a423fae417cc4e1cd2292'


def require(value, message):
    if not value:
        raise ValueError(message)


def candidate(root=ROOT):
    return (root / CONTRACT).is_file()


def digest(data):
    return hashlib.sha256(data).hexdigest()


def normalized(path):
    return path.read_bytes().replace(b'\r\n', b'\n')


def read_contract(root=ROOT):
    raw = normalized(root / CONTRACT)
    require(digest(raw) == CONTRACT_SHA256, 'Stage8.4 contract is not sealed')
    contract = json.loads(raw)
    require(contract['status'] == 'PENDING_FINAL_PUBLICATION' and contract['baseline'] == BASE,
            'Source cannot self-certify publication or change admission baseline')
    require(contract['8.4C'] == contract['8.5'] == 'NOT_STARTED'
            and contract['securityRestoration'] == 'OPEN'
            and contract['ciPrivateRuntime'] == 'NOT_PROVISIONED', 'Stage scope inflated')
    return contract


def validate_paths(actual, approved, *, complete=True):
    require(set(actual) <= set(approved) and (not complete or set(actual) == set(approved)),
            'Unapproved or missing Stage8.4 path')
    require(not any(Path(p).suffix.lower() in {'.aar', '.onnx', '.wav', '.pcm', '.mp3', '.apk', '.so'}
                    for p in actual), 'Private binary/audio cannot enter implementation history')


def project_policy_source(source, root=ROOT):
    """Only the sealed schema-upgrade change may project to predecessor SQL policy checks."""
    import validate_recording_latency as latency
    import validate_encrypted_persistence as persistence
    contract = read_contract(root)
    require(digest(source.replace('\r\n', '\n').encode()) == contract['files'][latency.POLICY_PATH],
            'Unreviewed Stage8.4 SQL policy change')
    return persistence.git(root, 'show', BASE + ':' + latency.POLICY_PATH).decode()


def approved_graph(root, contract):
    import alpha_release_sbom as sbom
    graph = json.loads((root / GRAPH).read_text(encoding='utf-8'))
    require(digest(sbom.canonical_bytes(graph)) == contract['release_graph_sha256'], 'Stage8.4 graph changed')
    sbom.check_graph(graph, sbom.lock_coordinates(root / 'android/app/gradle.lockfile'), graph,
                     allowed_projects=PROJECTS)
    require({n['path'] for n in graph['components'] if n['kind'] == 'project'} == PROJECTS,
            'Stage8.4 module inventory changed')
    historical = json.loads((root / 'docs/evidence/persistence-8.2-release-runtime-graph.json').read_text())
    modules = lambda g: sorted((n for n in g['components'] if n['kind'] == 'maven'), key=lambda n: n['id'])
    require(modules(graph) == modules(historical), 'Credential-free CI cannot introduce another external runtime')
    return graph


def validate_checkout(root=ROOT, *, allow_working=None):
    import validate_encrypted_persistence as persistence
    import validate_original_audio_lifecycle as lifecycle
    import validate_product_recording as recording
    import validate_development_device_security as security
    import validate_vad_artifact_admission as admission
    import vad_runtime_ci_profile as ci
    contract = read_contract(root)
    git = lambda *args: persistence.git(root, *args)
    require(git('rev-parse', BASE + '^{tree}').decode().strip() == BASE_TREE, 'Exact admission tree changed')
    require(git('merge-base', BASE, 'HEAD').decode().strip() == BASE, 'Wrong Stage8.4 parent')
    working = persistence.working_verification(allow_working, os.environ)
    head = git('rev-parse', 'HEAD').decode().strip()
    branch = git('branch', '--show-current').decode().strip()
    dirty = bool(git('status', '--porcelain', '--untracked-files=all'))
    require(branch == BRANCH or (branch == '' and os.environ.get('GITHUB_ACTIONS') == 'true'), 'Wrong Stage8.4 branch')
    require(working or not dirty, 'Stage8.4 publication requires a clean checkout')
    if os.environ.get('GITHUB_ACTIONS') or os.environ.get('GITHUB_EVENT_NAME'):
        require(not working and not dirty and os.environ.get('GITHUB_SHA') == head
                and os.environ.get('GITHUB_REF') == 'refs/heads/' + BRANCH
                and os.environ.get('GITHUB_REPOSITORY') == 'Monumentogram/DORA'
                and os.environ.get('GITHUB_EVENT_NAME') == 'push', 'Exact-SHA CI context mismatch')
    approved = set(contract['files']) | {CONTRACT, SELF}
    actual = set(git('diff', '--name-only', '--no-renames', BASE).decode().splitlines())
    actual.update(git('ls-files', '--others', '--exclude-standard').decode().splitlines())
    validate_paths(actual, approved)
    for line in git('rev-list', '--reverse', '--parents', BASE + '..HEAD').decode().splitlines():
        parts = line.split()
        require(len(parts) == 2, 'Stage8.4 merge/rewrite history is not allowed')
        paths = git('diff', '--name-only', '--no-renames', parts[1], parts[0]).decode().splitlines()
        require(bool(paths), 'Empty implementation commit rejected')
        validate_paths(paths, approved, complete=False)
    require(head != BASE or working, 'Stage8.4 implementation commit absent')
    for path, expected in contract['files'].items():
        require(digest(normalized(root / path)) == expected, 'Stage8.4 file seal differs: ' + path)
    historical = set(git('ls-tree', '-r', '--name-only', BASE, 'docs/evidence', 'docs/contracts',
                         'android/vendor', 'android/poc').decode().splitlines())
    require(not historical.intersection(actual), 'Historical evidence/contracts/vendor/Recovery changed')
    require(digest(normalized(root / PROFILE)) == PROFILE_SHA, 'Calibration profile changed after freeze')
    workflow = (root / '.github/workflows/android-ci.yml').read_text(encoding='utf-8')
    require(ci.GATE in workflow and ci.normalize(workflow) == git('show', BASE + ':.github/workflows/android-ci.yml').decode(),
            'Historical CI changed or Stage8.4 gates omitted')
    legacy = lifecycle.historical_parent(root)
    sources = persistence.read_product_sources(root)
    persistence.validate_product_sources(sources)
    recording.validate_manifest((root / 'android/app/src/main/AndroidManifest.xml').read_text())
    recording.validate_capture_sources({p.relative_to(root).as_posix(): p.read_text(encoding='utf-8')
        for p in (root / 'android/app/src/main/kotlin/com/monumentogram/dora/recording').glob('*.kt')})
    security.validate_development_sources(root)
    require(json.loads((root / security.BLOCKER_PATH).read_text()) == security.RESTORATION_BLOCKER,
            'Development security restoration remains OPEN')
    prefix = root / admission.PREFIX
    admission.validate_bundle(*[json.loads((prefix / name).read_text()) for name in
        ('build-inputs.json', 'license-inventory.json', 'sbom.cdx.json')])
    admission.validate_closure_data(*[json.loads((root / admission.CLOSURE_PREFIX / name).read_text()) for name in
        ('supply-chain.json', 'retrieval.json', 'runtime-smoke.json')])
    test_root = root / 'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/persistence'
    tests = persistence.discover_device_tests({p.relative_to(test_root).as_posix(): p.read_text(encoding='utf-8')
                                              for p in test_root.rglob('*.kt')})
    inventory = normalized(root / ci.INVENTORY)
    persistence.validate_device_inventory(inventory, tests, contract['files'][ci.INVENTORY])
    require(set(json.loads(git('show', BASE + ':' + ci.OLD_INVENTORY))['tests']) <= tests,
            'Historical instrumentation test removed')
    inherited = set(legacy['implementation_paths']) | set(git('diff', '--name-only', lifecycle.PARENT, BASE).decode().splitlines())
    result = {**legacy, 'implementation_paths': sorted(inherited | approved),
              'vad_runtime_graph': True, 'release_graph_sha256': contract['releaseGraphSha256']}
    approved_graph(root, result)
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--working', action='store_true', default=None)
    validate_checkout(allow_working=parser.parse_args().working)
    print('PASS repository Stage8.4 logic admission; private runtime, physical acceptance and publication are separate')
