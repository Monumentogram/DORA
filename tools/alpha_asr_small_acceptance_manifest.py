#!/usr/bin/env python3
"""Frozen SMALL acceptance metadata validator; no audio/reference/device access.

CLI reads four private JSON inputs and emits only aggregate counts/digests or a
stable error code. analyze/prepare return PRIVATE metadata for an authorized
materializer; they never read sample paths or write files. No authority overrides
are accepted by the CLI. Tests patch pins only for generated synthetic fixtures.
"""
from __future__ import annotations

from collections import Counter
import sys

if __package__:
    from . import alpha_asr_pilot_manifest as old
else:
    import alpha_asr_pilot_manifest as old

INVENTORY_SHA256 = 'ad454b162fab7dea96c28612a374fcae79040346dae5ee2916b3c4482b3ec041'
ORIGINAL_SHA256 = '5d9e12fa76e9db54bc69bbd22396dbec733348db97bba353361ac701af64061c'
CONTRACT_ID = 'dora-alpha-asr-small-acceptance-selection-v0.1'
CONTRACT_SHA256 = '4db7c7d67aac03504a1233f0f59cfe1bedee414917f28e2c915d337d404638ff'
PROFILE = 'dora-alpha-asr-small-acceptance-manifest-v0.1'
LEDGER_PROFILE = 'dora-alpha-asr-prior-use-v0.1'
NO_DEVELOPMENT = 'NO_SEPARATE_DEVELOPMENT_OR_TUNING_SET_EXISTS'
ValidationError = old.ValidationError
selection_key = old.selection_key


def order_key(row):
    return selection_key(row), row['upstreamRelativePath'].encode('utf-8')


def source_identity(row):
    return row['locale'], row['datasetId'], row['upstreamRelativePath']


def _stable(rows):
    return sorted(rows, key=old.canonical_json)


def _ledger(value):
    old._fields(value, {'schemaVersion', 'profileId', 'inventorySha256',
                       'originalManifestSha256', 'developmentState',
                       'participantDisjointness', 'records'})
    old.require(type(value['schemaVersion']) is int and value['schemaVersion'] == 1
                and value['profileId'] == LEDGER_PROFILE, 'E_LEDGER_PROFILE')
    old.require(value['inventorySha256'] == INVENTORY_SHA256
                and value['originalManifestSha256'] == ORIGINAL_SHA256, 'E_LEDGER_AUTHORITY')
    old.require(value['developmentState'] == NO_DEVELOPMENT
                and value['participantDisjointness'] == 'NOT_APPLICABLE_NO_DEVELOPMENT_SET',
                'E_DEVELOPMENT_SCOPE')
    records = value['records']
    old.require(type(records) is list and len(records) <= old.MAX_CANDIDATES, 'E_LEDGER_ROWS')
    for row in records:
        old._fields(row, {'locale', 'datasetId', 'upstreamRelativePath', 'audioSha256', 'useClass'})
        old.require(type(row['locale']) is str and row['locale'] in old.LOCALES, 'E_DATASET')
        old.require(row['datasetId'] == old.DATASET_IDS[row['locale']], 'E_DATASET')
        old._path(row['upstreamRelativePath'])
        old._hash(row['audioSha256'])
        old.require(row['useClass'] in ('EVALUATION', 'TUNING'), 'E_PRIOR_USE_CLASS')
    old.require(len({old.canonical_json(r) for r in records}) == len(records), 'E_LEDGER_DUPLICATE')
    return {**value, 'records': _stable(records)}


def analyze(inventory_bytes, original_bytes, ledger_bytes):
    """Private pool/audit; validates pinned historical authority before exclusions.

    No availability, lawful provenance, or ledger completeness is inferred from
    metadata consistency. The authorized materializer establishes those facts.
    """
    inv = old.parse_json(inventory_bytes)
    original = old.parse_json(original_bytes)
    ledger = old.parse_json(ledger_bytes)
    old._root(inv, old.INVENTORY_PROFILE, {'candidateCount', 'candidates'})
    old._int(inv['candidateCount'], 0, old.MAX_CANDIDATES)
    old._rows(inv['candidates'], False)
    old.require(inv['candidateCount'] == len(inv['candidates']), 'E_CANDIDATE_COUNT')
    old.require(old.inventory_digest(inv) == INVENTORY_SHA256, 'E_INVENTORY_AUTHORITY')
    old.require(type(original) is dict and original.get('manifestSha256') == ORIGINAL_SHA256
                and old.digest({k: v for k, v in original.items() if k != 'manifestSha256'})
                == ORIGINAL_SHA256, 'E_ORIGINAL_AUTHORITY')
    old.verify(inventory_bytes, original_bytes)
    ledger = _ledger(ledger)
    original_sources = {source_identity(r) for r in original['samples']}
    original_audio = {r['audioSha256'] for r in original['samples']}
    prior_sources = {source_identity(r) for r in ledger['records']}
    prior_audio = {r['audioSha256'] for r in ledger['records']}
    exclusions, remaining = [], []
    counts = dict.fromkeys(('baseIneligible', 'originalSource', 'originalAudio', 'priorUse', 'duplicateAudio'), 0)
    for row in inv['candidates']:
        reasons = []
        if not old._eligible(row): reasons.append('baseIneligible')
        if source_identity(row) in original_sources: reasons.append('originalSource')
        if row['audioSha256'] in original_audio: reasons.append('originalAudio')
        if source_identity(row) in prior_sources or row['audioSha256'] in prior_audio:
            reasons.append('priorUse')
        if reasons:
            exclusions.append({'sourceIdentity': source_identity(row), 'audioSha256': row['audioSha256'], 'reasons': reasons})
            for reason in reasons: counts[reason] += 1
        else:
            remaining.append(row)
    frequency = Counter(r['audioSha256'] for r in remaining)
    duplicates = sorted(h for h, count in frequency.items() if count > 1)
    pool = []
    for row in remaining:
        if frequency[row['audioSha256']] > 1:
            counts['duplicateAudio'] += 1
            exclusions.append({'sourceIdentity': source_identity(row), 'audioSha256': row['audioSha256'], 'reasons': ['duplicateAudio']})
        else:
            pool.append(row)
    pool.sort(key=lambda r: (old.LOCALES.index(r['locale']), r['upstreamRelativePath'].encode('utf-8')))
    exclusions = _stable(exclusions)
    digests = {
        'originalSource': old.digest(_stable(list(original_sources))),
        'originalAudio': old.digest(sorted(original_audio)),
        'priorUse': old.digest(ledger),
        'duplicateGroups': old.digest([{'audioSha256': h, 'count': frequency[h]} for h in duplicates]),
        'combinedExclusionSet': old.digest(exclusions),
    }
    summary = {'candidateInventorySha256': INVENTORY_SHA256, 'originalManifestSha256': ORIGINAL_SHA256,
               'inventoryAuthorityMatch': True, 'originalManifestAuthorityMatch': True,
               'exclusionCounts': counts, 'exclusionCountMeaning': 'Per reason; overlapping reasons are not summed.',
               'uniqueExcludedCount': len(exclusions), 'duplicateGroupCount': len(duplicates),
               'eligibleCounts': {loc: sum(r['locale'] == loc for r in pool) for loc in old.LOCALES},
               'exclusionDigests': digests, 'eligiblePoolSha256': old.digest(pool),
               'priorUseLedgerSha256': old.digest(ledger), 'developmentState': NO_DEVELOPMENT,
               'participantDisjointness': 'NOT_APPLICABLE_NO_DEVELOPMENT_SET',
               'audioDecodedByValidator': False, 'inventoryCompletenessVerified': False,
               'priorUseLedgerCompletenessVerified': False}
    return {'summary': summary, 'eligiblePool': pool, 'exclusions': exclusions, 'priorUseLedger': ledger}


def prepare(inventory_bytes, original_bytes, ledger_bytes):
    """Build a PRIVATE expected manifest in memory; no files are materialized."""
    audit = analyze(inventory_bytes, original_bytes, ledger_bytes)
    summary = audit['summary']
    old.require(all(summary['eligibleCounts'][loc] >= 24 for loc in old.LOCALES),
                'E_INSUFFICIENT_FRESH_HOLDOUT')
    selected = []
    for loc in old.LOCALES:
        rows = sorted((r for r in audit['eligiblePool'] if r['locale'] == loc), key=order_key)[:24]
        selected.extend({**r, 'rank': rank, 'selectionKey': selection_key(r).hex(), 'eligibilityResult': 'ELIGIBLE'}
                        for rank, r in enumerate(rows, 1))
    manifest = {'schemaVersion': 1, 'profileId': PROFILE,
                'selectionContractId': CONTRACT_ID, 'selectionContractSha256': CONTRACT_SHA256,
                'candidateInventorySha256': INVENTORY_SHA256, 'originalManifestSha256': ORIGINAL_SHA256,
                'priorUseLedgerSha256': summary['priorUseLedgerSha256'],
                'exclusionDigests': summary['exclusionDigests'], 'eligiblePoolSha256': summary['eligiblePoolSha256'],
                'sampleCount': 48, 'selectedCounts': {'ru': 24, 'en': 24}, 'samples': selected}
    manifest['manifestSha256'] = old.digest(manifest)
    return manifest, audit


def verify(inventory_bytes, original_bytes, ledger_bytes, manifest_bytes):
    expected, audit = prepare(inventory_bytes, original_bytes, ledger_bytes)
    manifest = old.parse_json(manifest_bytes)
    # Canonical bytes distinguish bool from int and reject every extra nested field.
    old.require(old.canonical_json(manifest) == old.canonical_json(expected), 'E_SUCCESSOR_MANIFEST')
    return {'result': 'PASS', 'claim': 'PINNED_EXCLUSION_AWARE_METADATA_CONTRACT_VALID',
            **audit['summary'], 'selectionContractId': CONTRACT_ID,
            'selectionContractSha256': CONTRACT_SHA256, 'sampleCount': 48,
            'selectedCounts': {'ru': 24, 'en': 24}, 'manifestSha256': expected['manifestSha256']}


def main(argv=None):
    args = sys.argv[1:] if argv is None else argv
    if len(args) != 4:
        print('{"result":"FAIL","code":"E_USAGE"}', file=sys.stderr)
        return 2
    try:
        result = verify(*(old._read_local(p) for p in args))
    except ValidationError as error:
        print(old.canonical_json({'result': 'FAIL', 'code': str(error)}).decode(), file=sys.stderr)
        return 1
    except OSError:
        print('{"result":"FAIL","code":"E_READ"}', file=sys.stderr)
        return 3
    except Exception:
        print('{"result":"FAIL","code":"E_INTERNAL"}', file=sys.stderr)
        return 3
    print(old.canonical_json(result).decode())
    return 0


if __name__ == '__main__':
    sys.exit(main())
