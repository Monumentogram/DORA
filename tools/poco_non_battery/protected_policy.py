"""Derive owner-private exact47 restrictions from an already authenticated snapshot.

This is not an authentication oracle. Callers must verify the retained inspector
receipt and exact snapshot digest first. Never print or publish the return value.
"""
import hashlib
import json
import re

from poco_protected_snapshot import records, require

UUID = re.compile(r'[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}')


def derive(raw, expected_sha256, profile, vault_file_names):
    require(hashlib.sha256(raw).hexdigest() == expected_sha256)
    snapshot = json.loads(raw)
    require(snapshot['format'] == 'DORA_PRIVATE_CATALOG_V1' and snapshot['schemaVersion'] == 3)
    require(snapshot['keyProofs'].keys() == snapshot['keys'].keys())
    require(snapshot['verifiedHistoricalKeyProofs'] == len(snapshot['keyProofs']) > 0)
    binding = records(snapshot, 'vault_binding')
    require(len(binding) == 1)
    removed = {row['assetId'] for row in records(snapshot, 'deletion_tombstone')}
    sources = []
    for row in records(snapshot, 'audio_asset'):
        require(all(row[field] == binding[0][field] for field in ('ownerId', 'vaultId')))
        if row['assetId'] not in removed:
            sources.append({field: row[field] for field in ('recordingId', 'assetId', 'sessionId')})
    require(len(sources) == 47 and len({row['assetId'] for row in sources}) == 47)
    require(all(UUID.fullmatch(value) for row in sources for value in row.values()))
    # Includes tombstones, unit/physical/epoch identities and file-only namespaces.
    identifiers = set(UUID.findall(raw.decode('utf-8')))
    for name in vault_file_names:
        identifiers.update(UUID.findall(name))
    require(all(UUID.fullmatch(binding[0][field]) for field in ('ownerId', 'vaultId')))
    require(set(profile) == {'package', 'model', 'api', 'firmware'})
    require(type(profile['api']) is int and profile['api'] == 34)
    require(all(type(profile[key]) is str and profile[key] for key in ('package', 'model', 'firmware')))
    policy = {'format': 'DORA_PROTECTED_HISTORICAL_V1', 'snapshotSha256': expected_sha256,
              **profile, 'ownerId': binding[0]['ownerId'], 'vaultId': binding[0]['vaultId'],
              'protectedSources': sorted(sources, key=lambda row: row['assetId']),
              'historicalIdentifiers': sorted(identifiers)}
    return (json.dumps(policy, ensure_ascii=True, sort_keys=True, separators=(',', ':')) + '\n').encode()
