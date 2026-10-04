"""Admit only sealed Stage 8.6 BLOCKED evidence; all accepted runtime bytes stay frozen."""
import argparse
import hashlib
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = 'b8cac85f30f446d98c1a6b1d21490d8c10bc5b41'
BRANCH = 'stage/8.6-poco-recording-acceptance'
EVIDENCE = 'docs/evidence/poco-recording-8.6/'
SELF = 'tools/validate_poco_acceptance.py'
PARENT = 'tools/validate_logical_recovery.py'
ROUTE = '''    import validate_poco_acceptance as successor
    if successor.candidate(root):
        return successor.validate_checkout(root, allow_working=allow_working)
'''
# Exact content seals; additions/edits require a new reviewed evidence revision.
SEALED = {'docs/evidence/poco-recording-8.6/attempts.json': '41993ad345629651577ad94adfd80197d0fdd790cf391759beb3df2825c9764e', 'docs/evidence/poco-recording-8.6/batterystats-source-summary.txt': 'da3a563e0d942f48c2809ced7fe98a370018ffab5e05e7e11153915755e5ad07', 'docs/evidence/poco-recording-8.6/cleanup-diagnostic-files.txt': 'dd8375c9e37ee04796a5237f78923faca46245a35668894177cb2b8a85f5ad3e', 'docs/evidence/poco-recording-8.6/device-preflight.json': '2411808feedbcabef0754b5a59302bb2255efd534080d4411f9fb71a3982ea66', 'docs/evidence/poco-recording-8.6/EnergyProbe.java': '8e45cc34d14d9bd53b7f2a26edc4a1b7c822c1d05b104b29075abbeeb4ec856e', 'docs/evidence/poco-recording-8.6/fixture-manifest.json': 'c8f567882b06e4e03b0a00f03b5aa59921ac0f5f69cde167b40df3d5340a8408', 'docs/evidence/poco-recording-8.6/framework-energy-probe-screen-off.txt': 'bee070cd9160fa644e1fd366c9dbe28ea5ac9fab430e4c344111dec81a9bbd33', 'docs/evidence/poco-recording-8.6/framework-energy-probe.txt': '86488169581ac12fefb6ebf655608841c5be326633b724863254bf123b1dd9d4', 'docs/evidence/poco-recording-8.6/oracle-result.txt': '7934c2f52f3d66c9193320dd44ce801ef62716c4a3323a5b471d25e6586b2a56', 'docs/evidence/poco-recording-8.6/perfetto-consumer-summary.json': '43445acea8eccb372d834ee31dc3f614f2e1e5c4718c0dcc5fc5581b44d579c6', 'docs/evidence/poco-recording-8.6/perfetto-summary.json': 'ee9319553dc3f5846ccca191d3be01cf217a2557353885a811d6952a7bfa349d', 'docs/evidence/poco-recording-8.6/power-preflight-consumers.pbtxt': '58c22bb79ffebba89dc7f690df7fb7dd9dba8f6d8484901aa176b8863f85d613', 'docs/evidence/poco-recording-8.6/power-preflight-rails.pbtxt': 'ecce9d7b3fd2233ed862f04bb0aeb8947eedd6e87a9efa4a7ebe1139946da8e3', 'docs/evidence/poco-recording-8.6/protocol.json': '873df5993a2d14d0c054e0b9aa3afe68a1dc8c53e69d686d476cbb080c4e2364', 'docs/evidence/poco-recording-8.6/README.md': 'c05ec739b552148cda9df8d7061fbf84daa51161486a76849071dfeb97d1df41', 'docs/evidence/poco-recording-8.6/result.json': '4cba9826f1f3afe8b6a40bff36f5cb59ad73687531477368a49a679b63a1591e', 'docs/evidence/poco-recording-8.6/screen-before.txt': '47af5ef8b5c76675798d0d3fd9e41b050f21c4c4472fd057d0bf02809223a8be', 'docs/evidence/poco-recording-8.6/screen-off-end.txt': 'b11c0847e108cea10d11e68469ab13c37eca4e6b43babd86b190dd36708c9c4a', 'docs/evidence/poco-recording-8.6/screen-off.txt': '8c2e9f24c621a6ff13aa55f27fde0aca2c8a737015b6a6093f28dd2778459c17', 'tools/test_logical_recovery_poco.py': '87afc12dcf8286fa4b2cc1fd1eb3d1e6d948e914c77797003126acb6aab182cc'}


def require(value, message):
    if not value:
        raise ValueError(message)


def candidate(root=ROOT):
    return (root / EVIDENCE / 'result.json').is_file()


def validate_paths(actual, approved, complete=True):
    require(set(actual) <= set(approved) and (not complete or set(actual) == set(approved)),
            'Unapproved or missing Stage8.6 paths')


def validate_seal(raw, expected):
    require(hashlib.sha256(raw.replace(b'\r\n', b'\n')).hexdigest() == expected,
            'Stage8.6 evidence seal mismatch')


def validate_parent_route(current, original):
    current = current.replace(b'\r\n', b'\n')
    require(current.count(ROUTE.encode()) == 1 and
            current.replace(ROUTE.encode(), b'', 1) == original.replace(b'\r\n', b'\n'),
            'Accepted parent changed beyond successor route')


def validate_ci(environment, head, dirty, working):
    if environment.get('GITHUB_ACTIONS') or environment.get('GITHUB_EVENT_NAME'):
        require(not working and not dirty and environment.get('GITHUB_SHA') == head and
                environment.get('GITHUB_REF') == 'refs/heads/' + BRANCH and
                environment.get('GITHUB_REPOSITORY') == 'Monumentogram/DORA' and
                environment.get('GITHUB_EVENT_NAME') == 'push', 'Exact-SHA CI identity mismatch')


def validate_checkout(root=ROOT, *, allow_working=None):
    import validate_encrypted_persistence as persistence
    import validate_original_audio_lifecycle as lifecycle
    import validate_vad_runtime as vad
    import validate_development_device_security as security
    import logical_recovery_ci_profile as ci
    import validate_logical_recovery as parent
    git = lambda *args: persistence.git(root, *args)
    require(git('merge-base', BASE, 'HEAD').decode().strip() == BASE, 'Wrong exact Stage8.5 baseline')
    head = git('rev-parse', 'HEAD').decode().strip()
    branch = git('branch', '--show-current').decode().strip()
    require(branch == BRANCH or (not branch and os.environ.get('GITHUB_ACTIONS') == 'true'),
            'Wrong Stage8.6 branch')
    working = persistence.working_verification(allow_working, os.environ)
    dirty = bool(git('status', '--porcelain', '--untracked-files=all'))
    require(working or not dirty, 'Publication requires clean source')
    validate_ci(os.environ, head, dirty, working)
    approved = set(SEALED) | {SELF, PARENT}
    actual = set(git('diff', '--name-only', '--no-renames', BASE).decode().splitlines())
    actual |= set(git('ls-files', '--others', '--exclude-standard').decode().splitlines())
    validate_paths(actual, approved)
    for line in git('rev-list', '--reverse', '--parents', BASE + '..HEAD').decode().splitlines():
        parts = line.split()
        require(len(parts) == 2, 'No merge or rewritten baseline')
        validate_paths(git('diff', '--name-only', parts[1], parts[0]).decode().splitlines(), approved, False)
    for path, digest in SEALED.items():
        validate_seal((root / path).read_bytes(), digest)
    validate_parent_route((root / PARENT).read_bytes(), git('show', BASE + ':' + PARENT))
    # The path allowlist freezes every Android/build/workflow/oracle/historical byte.
    # Re-run inherited source, dependency-graph and test-inventory validations as well.
    contract = parent.read_contract(root)
    for path, digest in contract['files'].items():
        validate_seal((root / path).read_bytes(), digest)
    security.validate_development_sources(root)
    require(json.loads((root / security.BLOCKER_PATH).read_text()) == security.RESTORATION_BLOCKER,
            'Security restoration remains OPEN')
    persistence.validate_product_sources(persistence.read_product_sources(root))
    test_root = root / 'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/persistence'
    tests = persistence.discover_device_tests({p.relative_to(test_root).as_posix(): p.read_text(encoding='utf-8')
                                              for p in test_root.rglob('*.kt')})
    persistence.validate_device_inventory((root / ci.INVENTORY).read_bytes().replace(b'\r\n',b'\n'),
                                         tests, contract['files'][ci.INVENTORY])
    receipt = json.loads((root / EVIDENCE / 'result.json').read_text(encoding='utf-8'))
    require(receipt['verdict'] == 'BLOCKED / POCO_ENERGY_MEASUREMENT_UNAVAILABLE' and
            receipt['campaignSourceSha'] is None and receipt['campaignApkSha256'] is None,
            'Preflight cannot certify physical acceptance')
    legacy = lifecycle.historical_parent(root)
    inherited = set(legacy['implementation_paths']) | set(
        git('diff', '--name-only', lifecycle.PARENT, BASE).decode().splitlines())
    result = {**legacy, 'implementation_paths': sorted(inherited | approved),
              'vad_runtime_graph': True, 'release_graph_sha256': vad.read_contract(root)['releaseGraphSha256']}
    vad.approved_graph(root, result)
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--working', action='store_true', default=None)
    validate_checkout(allow_working=parser.parse_args().working)
    print('PASS sealed preflight repository contract; Stage8.6 physical acceptance BLOCKED')
