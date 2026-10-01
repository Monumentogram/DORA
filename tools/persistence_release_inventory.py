"""Stage 8.2 release license surface and embedded constituents, separate from Maven resolution."""
import copy
import hashlib
import json
from pathlib import PurePosixPath
import re

from validate_encrypted_persistence import require

NATIVE = 'com.monumentogram.dora.thirdparty:sqlcipher-android:4.17.0-dora.1'
TINK = 'com.google.crypto.tink:tink-android:1.23.0'
PROTOBUF = 'embedded:protobuf-java:4.33.6'
ASSETS = 'android/app/src/main/assets/third_party/persistence'
RECORD = 'docs/evidence/persistence-8.2-dependency-license-inventory.json'
NATIVE_SBOM = 'android/vendor/sqlcipher/native-components.cdx.json'


def validate(record, added_coordinates, asset_payloads):
    coordinates = [row['coordinate'] for row in record['components']]
    require(len(coordinates) == len(set(coordinates))
            and set(coordinates) | {NATIVE} == set(added_coordinates)
            and record['native_composite']['coordinate'] == NATIVE,
            'Product release license component inventory differs')
    require(all(row['license_expression'] == 'Apache-2.0' for row in record['components'])
            and record['jsr305'] == 'EXCLUDED', 'Unadmitted product dependency license')
    embedded = record['embedded_components']
    require(len(embedded) == 1 and embedded[0]['coordinate'] == PROTOBUF
            and embedded[0]['parent'] == TINK and embedded[0]['license_expression'] == 'BSD-3-Clause',
            'Shaded protobuf license inventory missing')
    expected = record['distributed_assets']
    require(bool(expected) and set(expected) == set(asset_payloads), 'Distributed license text missing')
    for path, digest in expected.items():
        require(PurePosixPath(path).parent.as_posix() == ASSETS
                and re.fullmatch(r'[0-9a-f]{64}', digest)
                and hashlib.sha256(asset_payloads[path]).hexdigest() == digest,
                'Distributed license text identity changed')


def read_and_validate(root, contract, graph):
    raw = (root / RECORD).read_bytes()
    require(hashlib.sha256(raw).hexdigest() == contract['dependency_inventory_sha256'],
            'Reviewed dependency/license inventory changed')
    record = json.loads(raw)
    historical = json.loads((root / 'docs/contracts/DORA_ALPHA_RELEASE_RUNTIME_GRAPH_V0_1.json').read_text())
    coordinates = lambda value: {node['id'][6:] for node in value['components'] if node['kind'] == 'maven'}
    payloads = {p.relative_to(root).as_posix(): p.read_bytes() for p in (root / ASSETS).glob('*.txt')}
    validate(record, coordinates(graph) - coordinates(historical), payloads)
    require(not any(':jsr305:' in node['id'] for node in graph['components']), 'Excluded jsr305 entered product graph')
    require(hashlib.sha256((root / NATIVE_SBOM).read_bytes()).hexdigest() == contract['native_sbom_sha256'],
            'Native constituent SBOM identity changed')
    # Resolution selects some metadata-only nodes; every selected binary must still match its admission.
    by_id = {'maven:' + row['coordinate']: row for row in record['components']}
    for node in graph['components']:
        if node['id'] in by_id:
            require(all(artifact in by_id[node['id']]['cached_published_artifacts'] for artifact in node['artifacts']),
                    'Resolved product artifact differs from license inventory')
    return record


def enrich_sbom(bom, record, native_sha256):
    require(re.fullmatch(r'[0-9a-f]{64}', native_sha256), 'Invalid native constituent SBOM digest')
    result = copy.deepcopy(bom)
    by_ref = {component['bom-ref']: component for component in result['components']}
    for row in record['components']:
        by_ref['maven:' + row['coordinate']]['licenses'] = [{'expression': row['license_expression']}]
    by_ref['maven:' + NATIVE]['externalReferences'] = [
        {'type': 'bom', 'url': 'urn:sha256:' + native_sha256,
         'comment': 'Exact native constituent inventory: ' + NATIVE_SBOM}]
    result['components'].append({'type': 'library', 'bom-ref': PROTOBUF, 'name': 'protobuf-java',
                                 'version': '4.33.6', 'licenses': [{'expression': 'BSD-3-Clause'}],
                                 'properties': [{'name': 'dora:embedding', 'value': 'Shaded inside ' + TINK}]})
    next(edge for edge in result['dependencies'] if edge['ref'] == 'maven:' + TINK)['dependsOn'].append(PROTOBUF)
    result['dependencies'].append({'ref': PROTOBUF, 'dependsOn': []})
    return result
