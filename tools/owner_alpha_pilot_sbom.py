"""CycloneDX from actual ownerPilot graph, checked against admitted release inventory."""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
from pathlib import Path
import re

import alpha_release_sbom as release
from alpha_security_preflight import require
from isolated_alpha_apk import AAR_SHA, MODEL_SHA
from owner_alpha_pilot_artifact import PACKAGE, VERSION_NAME
from validate_vad_runtime import PROJECTS

ROOT = Path(__file__).resolve().parents[1]
APPROVED = ROOT / 'docs/evidence/vad-8.4-runtime/ci-release-graph.json'
APPROVED_SHA = 'f2de7e34fbb767a1453a12317e0b3625ce3ee1ffa29636005ecd0bbeab6236b0'


def pilot_lock_coordinates(text):
    coordinates = set()
    for line in text.splitlines():
        if not line or line.startswith('#'):
            continue
        coordinate, configurations = line.split('=', 1)
        if 'ownerPilotRuntimeClasspath' in configurations.split(',') and coordinate != 'empty':
            require(len(coordinate.split(':')) == 3 and release.exact_version(coordinate.rsplit(':', 1)[1]),
                    'INVALID_PILOT_LOCK_COORDINATE')
            coordinates.add(coordinate)
    require(coordinates, 'PILOT_RUNTIME_LOCKS_MISSING')
    return coordinates


def check_graph(graph):
    require(graph.get('configuration') == 'ownerPilotRuntimeClasspath', 'ACTUAL_PILOT_GRAPH_REQUIRED')
    expected = {path: ('ownerPilotRuntimeClasspath' if path == ':app' else 'releaseRuntimeClasspath') for path in PROJECTS}
    require(graph.get('project_configurations') == expected, 'PILOT_RELEASE_DEPENDENCY_VARIANTS_REQUIRED')
    approved = json.loads(APPROVED.read_text())
    require(hashlib.sha256(release.canonical_bytes(approved)).hexdigest() == APPROVED_SHA, 'APPROVED_RELEASE_GRAPH_CHANGED')
    # The predecessor validator is reused only after checking the actual variant map.
    projected = copy.deepcopy(graph)
    projected.pop('project_configurations')
    projected['configuration'] = 'releaseRuntimeClasspath'
    locked = pilot_lock_coordinates((ROOT / 'android/app/gradle.lockfile').read_text())
    require(locked == release.lock_coordinates(), 'PILOT_LOCKS_DIFFER_FROM_RELEASE')
    release.check_graph(projected, locked, approved, allowed_projects=PROJECTS)
    return release.normalize(graph)


def generate(graph, source, *, private_vad=False, private_asr=False):
    require(re.fullmatch('[a-f0-9]{40}', source), 'INVALID_SOURCE_SHA')
    actual = check_graph(graph)
    approved = json.loads(APPROVED.read_text())
    bom = release.generate(approved, source, release.lock_coordinates(), approved, allowed_projects=PROJECTS)
    bom['metadata']['component'].update(name='DORA Owner Alpha Pilot', version=VERSION_NAME)
    bom['metadata']['tools']['components'] = [{'type': 'application', 'name': 'dora-owner-alpha-pilot-sbom', 'version': '1'}]
    bom['metadata']['properties'] = [
        {'name': 'dora:source-sha', 'value': source},
        {'name': 'dora:application-id', 'value': PACKAGE},
        {'name': 'dora:configuration', 'value': 'ownerPilotRuntimeClasspath'},
        {'name': 'dora:graph-sha256', 'value': hashlib.sha256(release.canonical_bytes(actual)).hexdigest()},
        {'name': 'dora:private-vad-packaged', 'value': str(private_vad).lower()},
    ]
    for component in bom['components']:
        if component['bom-ref'].startswith('project:'):
            component['version'] = VERSION_NAME
    import persistence_release_inventory as inventory
    contract = json.loads((ROOT / 'docs/contracts/DORA_ENCRYPTED_PERSISTENCE_8_2_V0_1.json').read_text())
    record = inventory.read_and_validate(ROOT, contract, approved)
    bom = inventory.enrich_sbom(bom, record, contract['native_sbom_sha256'])
    if private_vad:
        constituents = ROOT / 'docs/evidence/vad-8.4-remediation/sbom.cdx.json'
        digest = hashlib.sha256(constituents.read_bytes().replace(b'\r\n', b'\n')).hexdigest()
        references = [('private-vad-aar', 'sherpa-onnx-vad', '1.13.8-dora.1-arm64', AAR_SHA, 'library'),
                      ('private-vad-model', 'Silero VAD', 'pinned-sha256', MODEL_SHA, 'file')]
        for ref, name, version, pin, kind in references:
            component = {'type': kind, 'bom-ref': ref, 'name': name, 'version': version,
                         'hashes': [{'alg': 'SHA-256', 'content': pin}]}
            if kind == 'library':
                component['externalReferences'] = [{'type': 'bom', 'url': 'urn:sha256:' + digest,
                    'comment': 'docs/evidence/vad-8.4-remediation/sbom.cdx.json; owner-local pinned file dependency'}]
            else:
                component['licenses'] = [{'expression': 'MIT'}]
            bom['components'].append(component)
            bom['dependencies'].append({'ref': ref, 'dependsOn': []})
            next(edge for edge in bom['dependencies'] if edge['ref'] == 'project::app')['dependsOn'].append(ref)
    if private_asr:
        require(private_vad, 'ASR_PILOT_REQUIRES_VAD')
        from owner_arm82 import enrich_sbom
        bom = enrich_sbom(bom)
    return bom


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--graph', type=Path, required=True)
    parser.add_argument('--source-sha', required=True)
    parser.add_argument('--private-vad', action='store_true')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    require(not args.output.exists(), 'SBOM_OUTPUT_EXISTS')
    bom = generate(json.loads(args.graph.read_text()), args.source_sha, private_vad=args.private_vad)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with args.output.open('xb') as stream:
        stream.write(release.canonical_bytes(bom))
    print('PASS ownerPilot graph and SBOM; private VAD packaging: ' + str(args.private_vad))


if __name__ == '__main__':
    main()
