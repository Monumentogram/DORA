"""Private whole-catalog comparison. Errors deliberately contain no identifiers.

This compares already authenticated, complete snapshots; it is not authentication
and does not grant any device operation. Public receipts contain counts only.
"""
from collections import Counter
import hashlib
import json


def require(condition):
    if not condition:
        raise ValueError('PROTECTED_SNAPSHOT_REJECTED')


def encoded(row):
    return json.dumps(row, ensure_ascii=True, separators=(',', ':'), allow_nan=False)


def records(snapshot, name):
    item = snapshot['tables'][name]
    columns = item['columns']
    require(len(columns) == len(set(columns)))
    result = []
    for row in item['rows']:
        require(len(row) == len(columns))
        require(all(value is None or type(value) in (int, str) for value in row))
        result.append(dict(zip(columns, row)))
    return result


def unique(rows, key):
    result = {}
    for row in rows:
        value = row[key]
        require(type(value) is str and value and value not in result)
        result[value] = row
    return result


def validate_preservation(before, after, permitted_sources, permitted_key_runs, protected_namespaces=()):
    """Reject all deltas except exactly owned additions for predeclared sources.

    permitted_key_runs is a separately verified alias -> new runId map. No
    wildcard or table-wide exception. Existing rows remain an exact multiset.
    Whole snapshots must have the same authenticated schema/column inventory.
    """
    require(before['format'] == after['format'] == 'DORA_PRIVATE_CATALOG_V1')
    require(before['schemaVersion'] == after['schemaVersion'] == 3)
    require(before['schema'] == after['schema'])
    require(before['tables'].keys() == after['tables'].keys())
    proofs = before.get('keyProofs')
    require(isinstance(proofs, dict) and bool(proofs) and proofs.keys() == before['keys'].keys())
    require(proofs == after.get('keyProofs'))
    require(before.get('verifiedHistoricalKeyProofs') == after.get('verifiedHistoricalKeyProofs') == len(proofs))
    old_assets = unique(records(before, 'audio_asset'), 'assetId')
    new_assets = unique(records(after, 'audio_asset'), 'assetId')
    allowed = unique(permitted_sources, 'assetId')
    components = ('assetId', 'recordingId', 'sessionId')
    old_ids = {row[c] for row in old_assets.values() for c in components}
    old_runs = unique(records(before, 'unit_claim'), 'runId')
    old_ids.update(old_runs)
    identity_fields = ('runId', 'appendRunId', 'bootstrapRunId', 'physicalId',
                       'segmentId', 'captureEpochId', 'recordingId', 'sessionId')
    for name in before['tables']:
        for row in records(before, name):
            old_ids.update(row[field] for field in identity_fields if row.get(field) is not None)
    old_ids.update(protected_namespaces)
    new_ids = [row[c] for row in allowed.values() for c in components]
    require(len(new_ids) == len(set(new_ids)) and not old_ids.intersection(new_ids))
    binding = records(before, 'vault_binding')
    require(len(binding) == 1)
    accepted_assets = set()
    for asset, row in new_assets.items():
        if asset in old_assets:
            require(encoded(row) == encoded(old_assets[asset]))
            continue
        require(asset in allowed)
        require(all(row[c] == allowed[asset][c] for c in components))
        require(all(row[c] == binding[0][c] for c in ('ownerId', 'vaultId')))
        accepted_assets.add(asset)
    claims = unique(records(after, 'unit_claim'), 'runId')
    accepted_runs = {run: row['assetId'] for run, row in claims.items()
                     if run not in old_ids and row['assetId'] in accepted_assets}
    # A new row cannot smuggle a historical namespace via a second identity field.
    namespace_owners = {}
    for name, original in before['tables'].items():
        current = after['tables'][name]
        require(original['columns'] == current['columns'])
        old = Counter(encoded(row) for row in original['rows'])
        new = Counter(encoded(row) for row in current['rows'])
        require(not (old - new))
        additions = new - old
        # Decode only our own canonical JSON, preserving duplicate multiplicity.
        for text, count in additions.items():
            require(count == 1)
            row = dict(zip(original['columns'], json.loads(text)))
            if 'assetId' in row:
                require(row['assetId'] in accepted_assets)
            elif 'runId' in row:
                require(row['runId'] in accepted_runs)
            else:
                require(False)  # Global/schema/orphan rows have no exception.
            require(all(row.get(field) not in old_ids for field in identity_fields))
            if row.get('runId') is not None:
                require(row['runId'] in accepted_runs)
                require(row.get('assetId', accepted_runs[row['runId']]) == accepted_runs[row['runId']])
            owner = row.get('assetId', accepted_runs.get(row.get('runId')))
            require(owner in accepted_assets)
            for field in ('appendRunId', 'bootstrapRunId'):
                require(row.get(field) is None or row[field] in accepted_runs)
                require(row.get(field) is None or accepted_runs[row[field]] == owner)
            for field in ('physicalId', 'segmentId', 'captureEpochId'):
                if row.get(field) is not None:
                    require(namespace_owners.setdefault(row[field], owner) == owner)
    for alias, state in before['keys'].items():
        require(alias in after['keys'] and encoded(after['keys'][alias]) == encoded(state))
    for alias in after['keys'].keys() - before['keys'].keys():
        require(alias in permitted_key_runs and permitted_key_runs[alias] in accepted_runs)
        value = f"DORA/recovery-physical-alias/v1/{binding[0]['vaultId']}/{permitted_key_runs[alias]}"
        require(alias == 'dora.vault.run.v1.' + hashlib.sha256(value.encode('ascii')).hexdigest())
    return {'historicalRows': sum(len(t['rows']) for t in before['tables'].values()),
            'historicalKeys': len(before['keys']), 'protectedRowsChanged': 0,
            'protectedKeysChanged': 0, 'addedTestAssets': len(accepted_assets)}
