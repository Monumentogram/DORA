"""Exact additive Stage 8.6C.2 diagnostics; no runtime or historical evidence override.

This is called inside the existing Stage 8.6 validator, never instead of it. Its
accepted parent is immutable; removing the one hook must recover the entire
parent validator. All other parent files and every successor commit stay scoped.
Run --seal explicitly after every new artifact is final, then run the inherited
validator with --working locally. CI never seals or admits working-tree checks.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
BASE = '944b41532e3007ca14d1851d83c2232de1132874'
PARENT = 'tools/validate_poco_acceptance.py'
MANIFEST = 'docs/contracts/persistence-latency-8.6c2.json'
ROUTE = '''    import poco_persistence_diagnostic_admission as diagnostic
    approved |= diagnostic.validate_checkout(root)
'''
ADDITIONS = {
    'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/diagnostics/PersistenceScalingBenchmarkTest.kt',
    'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/diagnostics/PersistenceScalingProbe.kt',
    'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/diagnostics/PersistenceAsyncDurabilityTest.kt',
    'android/core/audio/src/test/kotlin/com/monumentogram/dora/audio/CatalogOrderWorkTest.kt',
    'docs/evidence/persistence-latency-8.6c2/README.md',
    'docs/evidence/persistence-latency-8.6c2/RESULT.md',
    'docs/evidence/persistence-latency-8.6c2/architecture.md',
    'docs/evidence/persistence-latency-8.6c2/benchmark-api36.json',
    'docs/evidence/persistence-latency-8.6c2/catalog-order-work-gradle.log',
    'docs/evidence/persistence-latency-8.6c2/catalog-order-work-initial-failure.txt',
    'docs/evidence/persistence-latency-8.6c2/catalog-order-work-stdout.log',
    'docs/evidence/persistence-latency-8.6c2/catalog-order-work.json',
    'docs/evidence/persistence-latency-8.6c2/cpu-profile.json',
    'docs/evidence/persistence-latency-8.6c2/long02-analysis.json',
    'docs/evidence/persistence-latency-8.6c2/long02-protection-plan.md',
    'docs/evidence/persistence-latency-8.6c2/long02-statistics.md',
    'docs/evidence/persistence-latency-8.6c2/verification.json',
    'docs/superpowers/plans/2026-10-09-persistence-latency-causal.md',
    'tools/persistence_latency_analysis.py',
    'tools/persistence_scaling_results.py',
    'tools/run_persistence_scaling.py',
    'tools/test_persistence_latency_analysis.py',
    'tools/test_persistence_scaling_results.py',
    'tools/test_run_persistence_scaling.py',
    'tools/poco_persistence_diagnostic_admission.py',
    'tools/test_logical_recovery_persistence_diagnostic.py',
}
IDENTITY = {
    'schemaVersion': 1, 'stage': '8.6C.2', 'parent': BASE,
    'admission': 'DIAGNOSTIC_ONLY', 'runtimePolicy': 'UNCHANGED',
    'rootCause': 'NOT_PROVEN',
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(raw):
    return hashlib.sha256(raw.replace(b'\r\n', b'\n')).hexdigest()


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'Duplicate diagnostic manifest key')
        result[key] = value
    return result


def read_regular(root, path):
    candidate = root / path
    require(candidate.is_file() and not any(p.is_symlink() for p in
            [candidate, *candidate.parents]),
            'Diagnostic file missing or linked: ' + path)
    require(candidate.resolve().is_relative_to(root.resolve()), 'Diagnostic path escapes root')
    return candidate.read_bytes()


def load(root):
    value = json.loads(read_regular(root, MANIFEST), object_pairs_hook=unique_object)
    require(type(value) is dict and set(value) == set(IDENTITY) | {'files'} and
            all(type(value.get(k)) is type(v) and value[k] == v for k, v in IDENTITY.items()),
            'Diagnostic admission identity changed')
    require(type(value['files']) is dict and set(value['files']) == ADDITIONS,
            'Diagnostic addition scope changed')
    for path, expected in value['files'].items():
        require(type(expected) is str and re.fullmatch('[0-9a-f]{64}', expected) and
                digest(read_regular(root, path)) == expected,
                'Diagnostic source/evidence seal mismatch: ' + path)
    return value


def validate_delta(paths, *, complete):
    approved = ADDITIONS | {MANIFEST, PARENT}
    require(set(paths) <= approved and (not complete or set(paths) == approved),
            'Unapproved or missing diagnostic successor paths')


def validate_parent_route(current, original):
    current, original = (raw.replace(b'\r\n', b'\n') for raw in (current, original))
    route = ROUTE.encode()
    require(current.count(route) == 1 and current.replace(route, b'', 1) == original,
            'Diagnostic parent changed beyond exact additive hook')


def verify_parent(root, *, complete):
    import validate_encrypted_persistence as persistence
    git = lambda *args: persistence.git(root, *args)
    require(git('merge-base', BASE, 'HEAD').decode().strip() == BASE,
            'Wrong exact diagnostic parent')
    parent_paths = set(git('ls-tree', '-r', '--name-only', BASE).decode().splitlines())
    require(not (ADDITIONS | {MANIFEST}) & parent_paths,
            'Diagnostic additions overlap immutable parent')
    actual = set(git('diff', '--name-only', '--no-renames', BASE).decode().splitlines())
    actual |= set(git('ls-files', '--others', '--exclude-standard').decode().splitlines())
    validate_delta(actual, complete=complete)
    for line in git('rev-list', '--reverse', '--parents', BASE + '..HEAD').decode().splitlines():
        parts = line.split()
        require(len(parts) == 2, 'Diagnostic history must remain linear')
        validate_delta(git('diff', '--name-only', '--no-renames', parts[1], parts[0])
                       .decode().splitlines(), complete=False)
    validate_parent_route(read_regular(root, PARENT), git('show', BASE + ':' + PARENT))


def validate_checkout(root=ROOT):
    verify_parent(root, complete=True)
    load(root)
    return ADDITIONS | {MANIFEST}


def seal(root=ROOT):
    require(not (os.environ.get('GITHUB_ACTIONS') or os.environ.get('GITHUB_EVENT_NAME')),
            'CI cannot seal diagnostic evidence')
    verify_parent(root, complete=False)
    value = {**IDENTITY, 'files': {path: digest(read_regular(root, path))
                                 for path in sorted(ADDITIONS)}}
    destination = root / MANIFEST
    require(not destination.is_symlink(), 'Diagnostic manifest must not be linked')
    require(destination.parent.resolve().is_relative_to(root.resolve()),
            'Diagnostic manifest escapes root')
    destination.write_text(json.dumps(value, indent=2) + '\n', encoding='utf-8')
    validate_checkout(root)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--seal', action='store_true')
    parser.add_argument('--working', action='store_true', default=None)
    args = parser.parse_args()
    if args.seal:
        seal()
        print('SEALED exact Stage 8.6C.2 diagnostic additions; inherited validation still required')
    else:
        import validate_poco_acceptance as parent
        parent.validate_checkout(allow_working=args.working)
        print('PASS additive diagnostic admission; Stage 8.6 remains NOT_READY')
