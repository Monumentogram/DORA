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


# Canonical final names for generation-one, unit-zero MICROFILE publication.
# These are public format constants, never discovered from private filenames.
_SUCCESSOR_ARTIFACTS = {
    'key_confirmation': 'key-confirmation/run.kc',
    'manifest_ciphertext': 'manifests/g-00000000000000000001.ct',
    'manifest_wrapped_keyset': 'key-envelopes/manifest-g-00000000000000000001.ks',
    'microfile_ciphertext': 'units/u-0000000000.ct',
    'microfile_wrapped_keyset': 'key-envelopes/u-0000000000.ks',
}
_SOURCE_FIELDS = {'recordingId', 'assetId', 'sessionId'}
_OLD_FIELDS = {'format', 'snapshotSha256', 'package', 'model', 'api', 'firmware',
               'ownerId', 'vaultId', 'protectedSources', 'historicalIdentifiers'}
_MAPPING_FIELDS = {'format', 'codeReference', 'snapshotSha256', 'custodySha256',
                   'verifiedEvidenceFiles', 'source', 'blocks', 'blockCount', 'fileCount',
                   'claimedFrames', 'metadataTotalFileBytes', 'savedAliasCount',
                   'savedKeyChallengeCount', 'deviceAccessPerformed', 'audioBytesBackedUp',
                   'authenticatedPcmReadback', 'deviceProtectionExtended'}
_BLOCK_FIELDS = {'ordinal', 'firstFrame', 'claimedFrames', 'runId', 'physicalId',
                 'physicalFirstFrame', 'sourceFrameOffset', 'canonicalKeyUri',
                 'canonicalKeyUriSha256', 'physicalKeystoreAlias', 'files',
                 'savedKeyProperties', 'savedChallengePresent'}


def _exact(value, keys):
    require(type(value) is dict and set(value) == set(keys))


def _hash(value, width=64):
    require(type(value) is str and re.fullmatch('[a-f0-9]{'+str(width)+'}', value) is not None)


def _uuid(value):
    require(type(value) is str and UUID.fullmatch(value) is not None)


def _integer(value):
    require(type(value) is int and 0 <= value <= 2**63-1)


def _source(value):
    _exact(value, _SOURCE_FIELDS)
    for part in value.values():
        _uuid(part)
    require(len(set(value.values())) == 3)


def _unique_json(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result)
        result[key] = value
    return result


def _reject_constant(_):
    raise ValueError('SUCCESSOR_POLICY_REJECTED')


def _pinned_json(raw, digest):
    require(type(raw) is bytes)
    _hash(digest)
    require(hashlib.sha256(raw).hexdigest() == digest)
    return json.loads(raw.decode('utf-8'), object_pairs_hook=_unique_json,
                      parse_constant=_reject_constant)


def _old_successor_input(old):
    _exact(old, _OLD_FIELDS)
    require(old['format'] == 'DORA_PROTECTED_HISTORICAL_V1')
    _hash(old['snapshotSha256'])
    require(type(old['api']) is int and old['api'] == 34)
    require(all(type(old[k]) is str and bool(old[k]) for k in ('package','model','firmware')))
    _uuid(old['ownerId']); _uuid(old['vaultId'])
    require(old['ownerId'] != old['vaultId'])
    sources = old['protectedSources']
    require(type(sources) is list and len(sources) == 47)
    for source in sources:
        _source(source)
    require(len({s['assetId'] for s in sources}) == 47)
    identifiers = old['historicalIdentifiers']
    require(type(identifiers) is list and 1 <= len(identifiers) <= 100000)
    for identifier in identifiers:
        _uuid(identifier)
    require(len(set(identifiers)) == len(identifiers))
    require({value for row in sources for value in row.values()} <= set(identifiers))


def _mapping_controls(mapping):
    _exact(mapping, _MAPPING_FIELDS)
    require(mapping['format'] == 'LONG02_OFFLINE_PROTECTION_MAPPING_V1_NOT_POLICY_NOT_BACKUP')
    _hash(mapping['codeReference'],40)
    _hash(mapping['snapshotSha256']); _hash(mapping['custodySha256'])
    for field, expected in (('blockCount',394),('fileCount',1970),('claimedFrames',31520000),
                            ('verifiedEvidenceFiles',19),('savedAliasCount',394),('savedKeyChallengeCount',0)):
        _integer(mapping[field]); require(mapping[field] == expected)
    _integer(mapping['metadataTotalFileBytes'])
    for field in ('deviceAccessPerformed','audioBytesBackedUp','deviceProtectionExtended'):
        require(mapping[field] is False)
    require(mapping['authenticatedPcmReadback'] == 'NOT_RUN')


def _saved_key_properties(block):
    properties = block['savedKeyProperties']
    _exact(properties, ('created','algorithm','keySize','purposes','origin',
                       'userAuthenticationRequired','hardware','blockModes','encryptionPaddings'))
    for field in ('created','keySize','purposes','origin'):
        _integer(properties[field])
    require(properties['algorithm'] == 'AES' and properties['keySize'] == 256)
    require(properties['purposes'] == 3 and properties['origin'] == 1)
    require(properties['userAuthenticationRequired'] is False and type(properties['hardware']) is bool)
    require(properties['blockModes'] == ['GCM'] and properties['encryptionPaddings'] == ['NoPadding'])
    require(block['savedChallengePresent'] is False)


def _owned_files(block, paths):
    files = block['files']
    require(type(files) is list and len(files) == 5)
    roles, metadata_bytes = set(), 0
    for artifact in files:
        _exact(artifact, ('role','path','metadataBytes','savedSha256'))
        role = artifact['role']
        require(type(role) is str and role in _SUCCESSOR_ARTIFACTS and role not in roles)
        roles.add(role)
        expected = ('no_backup/dora-vault-v1/poc-recovery/v1/runs/' + block['runId'] +
                    '/' + _SUCCESSOR_ARTIFACTS[role])
        require(artifact['path'] == expected and expected not in paths)
        paths.add(expected)
        _hash(artifact['savedSha256']); _integer(artifact['metadataBytes'])
        require(artifact['metadataBytes'] > 0)
        metadata_bytes += artifact['metadataBytes']
    require(roles == set(_SUCCESSOR_ARTIFACTS))
    return metadata_bytes


def _successor(old, old_sha256, mapping, mapping_sha256):
    _old_successor_input(old); _mapping_controls(mapping)
    selected = mapping['source']
    _exact(selected, _SOURCE_FIELDS | {'ownerId','vaultId'})
    require(all(selected[k] == old[k] for k in ('ownerId','vaultId')))
    added = {k:selected[k] for k in sorted(_SOURCE_FIELDS)}
    _source(added)
    predecessor_ids = set(old['historicalIdentifiers'])
    reserved = predecessor_ids | {old['ownerId'],old['vaultId']}
    added_ids = set(added.values())
    require(not added_ids & reserved)
    blocks = mapping['blocks']
    require(type(blocks) is list and len(blocks) == 394)
    runs, physical, paths, bindings = set(), {}, set(), []
    metadata_bytes = 0
    previous_physical = None
    for ordinal, block in enumerate(blocks):
        _exact(block, _BLOCK_FIELDS)
        for field in ('ordinal','firstFrame','claimedFrames','physicalFirstFrame','sourceFrameOffset'):
            _integer(block[field])
        require(block['ordinal'] == ordinal and block['firstFrame'] == ordinal*80000)
        require(block['claimedFrames'] == 80000)
        require(block['physicalFirstFrame']+block['sourceFrameOffset'] == block['firstFrame'])
        run, physical_id = block['runId'], block['physicalId']
        _uuid(run); _uuid(physical_id)
        require(run not in runs and run not in reserved | added_ids)
        require(physical_id not in reserved | added_ids)
        if physical_id not in physical:
            require(block['sourceFrameOffset'] == 0 and block['physicalFirstFrame'] == block['firstFrame'])
            physical[physical_id] = dict(assetId=added['assetId'], physicalId=physical_id,
                                        physicalFirstFrame=block['physicalFirstFrame'])
        else:
            require(previous_physical == physical_id)
            require(physical[physical_id]['physicalFirstFrame'] == block['physicalFirstFrame'])
        previous_physical = physical_id
        runs.add(run)
        uri = 'android-keystore://dora.poc.recovery.v1.'+run
        require(block['canonicalKeyUri'] == uri)
        require(block['canonicalKeyUriSha256'] == hashlib.sha256(uri.encode()).hexdigest())
        alias = 'dora.vault.run.v1.'+hashlib.sha256(
            ('DORA/recovery-physical-alias/v1/'+old['vaultId']+'/'+run).encode()).hexdigest()
        require(block['physicalKeystoreAlias'] == alias)
        _saved_key_properties(block)
        metadata_bytes += _owned_files(block,paths)
        binding = {k:block[k] for k in ('runId','physicalId','ordinal','firstFrame',
                    'physicalFirstFrame','sourceFrameOffset','canonicalKeyUri',
                    'canonicalKeyUriSha256','physicalKeystoreAlias')}
        bindings.append(dict(binding,assetId=added['assetId'],frames=block['claimedFrames']))
    require(len(runs) == 394 and len(physical) == 4 and not runs & set(physical))
    require(len(paths) == 1970 and metadata_bytes == mapping['metadataTotalFileBytes'])
    identifiers = predecessor_ids | added_ids | runs | set(physical)
    require(len(identifiers) <= 100000)
    return dict(format='DORA_PROTECTED_SUCCESSOR_V2', snapshotSha256=mapping['snapshotSha256'],
                predecessorPolicySha256=old_sha256, custodyMappingSha256=mapping_sha256,
                **{k:old[k] for k in ('package','model','api','firmware','ownerId','vaultId')},
                protectedSources=old['protectedSources']+[added],
                predecessorSources=old['protectedSources'], predecessorIdentifiers=old['historicalIdentifiers'],
                additionalSource=added, historicalIdentifiers=sorted(identifiers),
                additionalRunBindings=bindings, additionalPhysicalSources=list(physical.values()))


def derive_successor(old_raw, expected_old_sha256, mapping_raw, expected_mapping_sha256):
    """Return a private V2 policy from pinned old policy and saved offline mapping.

    This performs no I/O or device/authentication operation. The caller must retain
    the prior authenticated custody evidence and pin both exact inputs separately.
    Saved key properties and file hashes never prove fresh keys, backup or PCM.
    Errors deliberately contain no private input. The V1 derive path is unchanged.
    """
    try:
        old = _pinned_json(old_raw,expected_old_sha256)
        mapping = _pinned_json(mapping_raw,expected_mapping_sha256)
        value = _successor(old,expected_old_sha256,mapping,expected_mapping_sha256)
        raw = (json.dumps(value,ensure_ascii=True,sort_keys=True,separators=(',',':'),allow_nan=False)+'\n').encode()
        require(len(raw) <= 1024*1024)
        return raw
    except (ValueError,TypeError,KeyError,UnicodeError,OverflowError):
        raise ValueError('SUCCESSOR_POLICY_REJECTED') from None
