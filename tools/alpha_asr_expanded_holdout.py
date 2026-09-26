"""Prospective CV27 RU test / SPS5 EN train selector. All results are private.

Pure host helper: no I/O, decoding, inference, consent, or source admission.
The caller must bind the complete inventory, exact reference bytes, sourceValid,
full-decode probes and exact accepted historical sets to private pinned evidence.
Hash cardinalities alone do not prove that evidence. Real participant hashes are
provider keys, not proof of real-world identity equality between corpora.
"""
from collections import Counter
from copy import deepcopy
import re

import alpha_asr_prospective_reference_eligibility as prospective
from asr_small_arm82_v01 import selector as frozen

BLOCKED = 'BLOCKED / INSUFFICIENT_UNTOUCHED_EXPANDED_HOLDOUT'
PASS = 'PASS / UNTOUCHED_EXPANDED_HOLDOUT_AVAILABLE'


def expected_provider_authority():
    """Fresh exact authority; changes require a new prospective source decision."""
    return {
        'ru': dict(datasetId='cmu5x45pn00dao107j4o9w2yv',
                   release='cv-corpus-27.0-2026-09-11', providerLocale='ru',
                   sourceSplit='test', isolationMode='participant'),
        'en': dict(datasetId='cmu5nqn1h00vwmi07b4dbk085',
                   release='sps-corpus-5.0-2026-09-11', providerLocale='en',
                   sourceSplit='train', isolationMode='participant'),
    }


def _hash(value):
    return type(value) is str and re.fullmatch('[0-9a-f]{64}', value) is not None


def _counts(rows):
    return {locale: sum(r['locale'] == locale for r in rows) for locale in ('ru', 'en')}


def audit(rows, references, prior_sources, prior_audio, prior_participants, *,
          provider_authority):
    """Return (exact48 selected or empty, deterministic private audit).

    Existing train-inventory row fields remain required; providerLocale is added.
    RU requires CV27 test; EN requires retained SPS5 train. A valid participant
    SHA256 must come from the provider key, never a sentence id. Cohort authority
    is deliberately unsupported: FLEURS has not been admitted for this helper.
    Source/reference drift raises ValueError before ranking; eligibility failures
    receive their first applicable exclusion. Neither language ranks on shortage.
    The caller must consume every selected row even if its execution never starts.
    """
    expected = expected_provider_authority()
    if provider_authority != expected:
        raise ValueError('EXPANDED_PROVIDER_AUTHORITY_MISMATCH')
    sources, audio, participants = set(prior_sources), set(prior_audio), set(prior_participants)
    if len(sources) < 144 or len(audio) < 144 or len(participants) < 55:
        raise ValueError('HISTORICAL_AUTHORITY_COUNT_MISMATCH')
    if (any(type(s) is not tuple or len(s) != 4
            or any(type(v) is not str or not v for v in s) for s in sources)
            or not all(_hash(v) for v in audio | participants)):
        raise ValueError('HISTORICAL_AUTHORITY_INVALID')
    for row in rows:
        if type(row) is not dict or row.get('locale') not in expected:
            raise ValueError('EXPANDED_INVENTORY_AUTHORITY_MISMATCH')
        authority = expected[row['locale']]
        if any(row.get(k) != v for k, v in authority.items() if k != 'isolationMode'):
            raise ValueError('EXPANDED_INVENTORY_AUTHORITY_MISMATCH')
        path = row.get('upstreamRelativePath')
        if type(path) is not str or not path or any(0xD800 <= ord(c) <= 0xDFFF for c in path):
            raise ValueError('INVALID_UPSTREAM_PATH')
        if row.get('isolationCohort') is not None:
            raise ValueError('UNADMITTED_COHORT_AUTHORITY')
    _, admission = prospective.admit_references(rows, references)
    token_counts = {tuple(r['source']): r['normalizedReferenceTokenCount']
                    for r in admission['tokenCounts']}
    pool = [dict(deepcopy(r), normalizedReferenceTokenCount=token_counts[frozen.source_key(r)])
            for r in rows]
    steps = [{'step': 'complete_expanded_inventory', 'remaining': _counts(pool)}]
    exclusions = []

    def exclude(step, reject):
        nonlocal pool
        removed, remaining = [], []
        for row in pool:
            (removed if reject(row) else remaining).append(row)
        pool = remaining
        exclusions.extend({'source': frozen.source_key(r), 'reason': step} for r in removed)
        steps.append({'step': step, 'removed': _counts(removed), 'remaining': _counts(pool)})

    exclude('historical_source', lambda r: frozen.source_key(r) in sources)
    exclude('historical_audio_digest', lambda r: r.get('audioSha256') in audio)
    exclude('historical_participant', lambda r: r.get('participantSha256') in participants)
    exclude('normalized_reference_token_count_at_least_one',
            lambda r: r['normalizedReferenceTokenCount'] < 1)
    exclude('existing_raw_source_decode_validity',
            lambda r: r.get('sourceValid') is not True or r.get('referenceTextPresent') is not True
            or r.get('decodeResult') != 'VALIDATED'
            or not _hash(r.get('participantSha256')) or not _hash(r.get('audioSha256')))
    exclude('duration_1000_to_20000_ms_inclusive',
            lambda r: type(r.get('durationMs')) is not int or not 1000 <= r['durationMs'] <= 20000)
    frequency = Counter(r['audioSha256'] for r in pool)
    exclude('all_members_of_duplicate_audio_groups', lambda r: frequency[r['audioSha256']] > 1)
    pool.sort(key=frozen.canonical)
    exclusions.sort(key=frozen.canonical)
    eligible = _counts(pool)
    sufficient = all(n >= 24 for n in eligible.values())
    selected = []
    if sufficient:
        selected = [r for locale in ('ru', 'en') for r in
                    sorted([r for r in pool if r['locale'] == locale], key=frozen.rank)[:24]]
    return selected, {
        'verdict': PASS if sufficient else BLOCKED, 'rankingOccurred': sufficient,
        'steps': steps, 'eligibleCounts': eligible,
        'eligibleParticipants': {locale: len({r['participantSha256'] for r in pool
                                            if r['locale'] == locale}) for locale in ('ru', 'en')},
        'participantCountMeaning': 'DISTINCT_PROVIDER_KEYS_ONLY',
        'crossProviderRealWorldParticipantEquality': 'UNKNOWN',
        'providerAuthority': deepcopy(expected), 'providerAuthoritySha256': frozen.digest(expected),
        'eligiblePoolSha256': frozen.digest(pool), 'eligiblePool': pool,
        'exclusions': exclusions, 'exclusionsSha256': frozen.digest(exclusions),
        'referenceAdmission': admission,
        'historicalCounts': {'sources': len(sources), 'audio': len(audio),
                             'participants': len(participants)},
        'priorSourcesSha256': frozen.digest(sorted(sources)),
        'priorAudioSha256': frozen.digest(sorted(audio)),
        'priorParticipantsSha256': frozen.digest(sorted(participants)),
    }
