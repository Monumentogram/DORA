"""Prospective reference admission before the frozen dev selector.

Private inputs/results only. No I/O, inference, scoring or historical mutation.
Future operators must use this wrapper, after binding the complete source and
prior-use authority. This does not authorize a new source or measured campaign.
"""
import hashlib

import alpha_asr_eval_text_contract as contract
from asr_small_arm82_v01 import selector as historical


def admit_references(rows, references):
    """Check exact UTF-8 bytes with the production evaluation normalizer.

    `references` maps each frozen source_key to its exact source reference bytes.
    Missing/mismatched content is an authority error, not a replacement case.
    An empty normalized reference is excluded before any deterministic ranking.
    """
    contract.check_environment()
    if len({historical.source_key(row) for row in rows}) != len(rows):
        raise ValueError('DUPLICATE_SOURCE')
    eligible, token_counts = [], []
    empty = {'ru': 0, 'en': 0}
    for row in rows:
        key = historical.source_key(row)
        raw = references.get(key)
        if (type(raw) is not bytes or row['locale'] not in empty
                or hashlib.sha256(raw).hexdigest() != row['referenceTextSha256']):
            raise ValueError('REFERENCE_BINDING_MISMATCH')
        try:
            text = raw.decode('utf-8', errors='strict')
        except UnicodeDecodeError:
            raise ValueError('INVALID_REFERENCE_UTF8') from None
        count = len(contract.normalized_tokens(text))
        token_counts.append({'source': key, 'normalizedReferenceTokenCount': count})
        if count >= 1:
            eligible.append(dict(row))
        else:
            empty[row['locale']] += 1
    token_counts.sort(key=historical.canonical)
    return eligible, {'normalizationVersion': contract.NORMALIZATION_VERSION,
                      'invariant': 'NORMALIZED_REFERENCE_TOKEN_COUNT >= 1',
                      'normalizedEmptyCounts': empty, 'tokenCounts': token_counts}


def select(rows, references, prior_sources, prior_audio, prior_participants):
    """Generated-tested prospective entry point; shortage still stops selection."""
    eligible, admission = admit_references(rows, references)
    selected, audit = historical.select(eligible, prior_sources, prior_audio, prior_participants)
    return selected, {**audit, 'referenceAdmission': admission}
