"""Prospective train availability audit. Pure host helper; all outputs are private.

Caller must bind the complete inventory to the exact authorized archives and
the complete prior-use authority. This does not admit a source or an experiment.
The historical dev selector, production normalizer and rank remain unchanged.
"""
from collections import Counter
import re

import alpha_asr_prospective_reference_eligibility as prospective
from asr_small_arm82_v01 import selector as frozen

BLOCKED = 'BLOCKED / INSUFFICIENT_UNTOUCHED_HOLDOUT_AFTER_TRAIN_AUDIT'
PASS = 'PASS / UNTOUCHED_TRAIN_HOLDOUT_AVAILABLE'


def counts(rows):
    return {locale: sum(r['locale'] == locale for r in rows) for locale in ('ru', 'en')}


def audit(rows, references, prior_sources, prior_audio, prior_participants):
    """First-applicable attribution; no rank call until both final pools reach24.

    sourceValid binds exact member/path/index provenance in the caller;
    decodeResult and durationMs bind its pinned full-decode/probe checks.
    Every source reference is hash-bound by the accepted wrapper before ranking.
    """
    if any(r['locale'] not in ('ru', 'en') or r['sourceSplit'] != 'train' for r in rows):
        raise ValueError('TRAIN_INVENTORY_REQUIRED')
    _, admission = prospective.admit_references(rows, references)
    token_counts = {tuple(r['source']): r['normalizedReferenceTokenCount']
                    for r in admission['tokenCounts']}
    pool = [dict(r, normalizedReferenceTokenCount=token_counts[frozen.source_key(r)]) for r in rows]
    steps = [{'step': 'complete_train_inventory', 'remaining': counts(pool)}]
    exclusions = []

    def exclude(step, reject):
        nonlocal pool
        removed = [r for r in pool if reject(r)]
        pool = [r for r in pool if not reject(r)]
        exclusions.extend({'source': frozen.source_key(r), 'reason': step} for r in removed)
        steps.append({'step': step, 'removed': counts(removed), 'remaining': counts(pool)})

    exclude('historical_source', lambda r: frozen.source_key(r) in prior_sources)
    exclude('historical_audio_digest', lambda r: r['audioSha256'] in prior_audio)
    exclude('historical_participant', lambda r: r['participantSha256'] in prior_participants)
    exclude('normalized_reference_token_count_at_least_one',
            lambda r: r['normalizedReferenceTokenCount'] < 1)
    exclude('existing_raw_source_decode_validity',
            lambda r: r.get('sourceValid') is not True or r['referenceTextPresent'] is not True
            or r['decodeResult'] != 'VALIDATED'
            or re.fullmatch('[0-9a-f]{64}', r['participantSha256']) is None
            or re.fullmatch('[0-9a-f]{64}', r['audioSha256']) is None)
    exclude('duration_1000_to_20000_ms_inclusive',
            lambda r: type(r['durationMs']) is not int or not 1000 <= r['durationMs'] <= 20000)
    frequency = Counter(r['audioSha256'] for r in pool)
    exclude('all_members_of_duplicate_audio_groups', lambda r: frequency[r['audioSha256']] > 1)
    pool.sort(key=frozen.canonical)
    exclusions.sort(key=frozen.canonical)
    eligible = counts(pool)
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
        'eligiblePoolSha256': frozen.digest(pool), 'eligiblePool': pool,
        'exclusions': exclusions, 'exclusionsSha256': frozen.digest(exclusions),
        'referenceAdmission': admission,
        'priorSourcesSha256': frozen.digest(sorted(prior_sources)),
        'priorAudioSha256': frozen.digest(sorted(prior_audio)),
        'priorParticipantsSha256': frozen.digest(sorted(prior_participants)),
    }
