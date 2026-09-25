"""Frozen 5.6C.2A SMALL profile and pure evaluator. No I/O on import/evaluation.

Inputs are PRIVATE. Only evaluate()'s allowlisted aggregates may be published.
Fractions and integer cross-products decide every numerical acceptance gate.
"""
from fractions import Fraction
import hashlib
import json
import re

import alpha_asr_eval_text_contract as text

VERSION = 'dora-alpha-asr-small-campaign-v0.1'
PROFILE_ID = 'dora-alpha-asr-small-eval-profile-v0.1'
MANIFEST_SHA = '2cb05132de945fe8a1b281e79efd8efa15d9b9b94c0326f6572a594e0c74e9bb'
TRANSFER_SHA = '95c26be0501172196587181b3f46705b65237bde879a0e3ee8eb748cee2e1a87'
FREEZE_SHA = 'eb088082b2cde6d2bc2649e6cb6d67a92d3a68ad447a8a7331eec58c2794bcaa'
MODEL_SHA = 'ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb'
MODEL_BYTES = 190085487
DECODING_SHA = 'cd53fda162402675f54875de470b01c303f0ede9a2e0cefd6f1056e8fa381d39'
PROTOCOL_SHA = '0f1e456fb4b4b0da28a209abe94e42fe7a0ca599e5c5c794db16a6abe478b116'
GATES_SHA = 'fdfb56b56429c07a99538c92d87f9ea8570c723cc67f5e5eb02915d97557ccd1'
GATES_ID = 'dora-alpha-asr-small-poco-m5-resource-gates-v0.1'
GOVERNANCE_COMMIT = 'dd1468dcaace9699e64b1945f83a9b532205d435'
RECOVERY_COMMIT = 'aa4e65cf4510f055260dca6a7e6e8f26916f732e'


def require(ok, code):
    if not ok: raise ValueError(code)


def canonical(value):
    return json.dumps(value, sort_keys=True, ensure_ascii=False,
                      separators=(',', ':'), allow_nan=False).encode('utf-8')


def digest(value):
    return hashlib.sha256(canonical(value)).hexdigest()


def profile():
    # Only model-independent semantic pins are imported, never the BASE profile.
    return {'profileId': PROFILE_ID, 'campaignVersion': VERSION,
        'normalizationVersion': text.NORMALIZATION_VERSION,
        'pythonSemanticProfile': 'cpython-3.12-unicode-15.0.0',
        'upstreamRepository': 'openai/whisper', 'upstreamRevision': text.UPSTREAM_REVISION,
        'upstreamSourceSha256': text.UPSTREAM_SHA256,
        'upstreamOptions': {'removeDiacritics': False, 'splitLetters': False},
        'oracleIdentity': 'com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle',
        'oracleSchemaVersion': 1, 'oracleSourceSha256': text.ORACLE_SHA256,
        'selectedManifestSha256': MANIFEST_SHA, 'transferIndexSha256': TRANSFER_SHA,
        'privateFreezeSha256': FREEZE_SHA,
        'selectionContractSha256': '4db7c7d67aac03504a1233f0f59cfe1bedee414917f28e2c915d337d404638ff',
        'governanceCommit': GOVERNANCE_COMMIT, 'sourceRecoveryCommit': RECOVERY_COMMIT,
        'modelArtifact': 'ggml-small-q5_1.bin', 'modelBytes': MODEL_BYTES, 'modelSha256': MODEL_SHA,
        'runtimeSourceVersion': 'ggml-org/whisper.cpp v1.9.4',
        'runtimeSourceCommit': text.RUNTIME_COMMIT,
        'decodingProfileSha256': DECODING_SHA, 'measurementProtocolSha256': PROTOCOL_SHA,
        'resourceGateVersion': GATES_ID, 'resourceGateSha256': GATES_SHA,
        'languageGates': {'ru': {'errors': 20, 'referenceTokens': 100},
                          'en': {'errors': 18, 'referenceTokens': 100}},
        'resourceGates': {'weightedRtf': 2, 'p95Rtf': 4, 'p95Rank': 46, 'maximumRtf': 6,
            'coldLoadMicros': 15000000, 'pssBytes': 1610612736, 'nativeHeapBytes': 1342177280,
            'evidencedOomEvents': 0, 'forbiddenThermalMinimum': 3,
            'memoryTargetMs': 100, 'memoryMaximumGapMs': 1000,
            'thermalDelayMs': 1000, 'thermalMaximumGapMs': 3000},
        'attemptPolicy': 'ONE_PRIMARY_PER_CASE_NO_AUTOMATIC_RETRY_STOP_ON_INVALID_ATTEMPT',
        'order': ['ru'] * 24 + ['en'] * 24,
        'warmup': 'ONE_GENERATED_3_SECOND_SILENCE_FULL_INFERENCE_BEFORE_CORPUS',
        'rawWerRole': 'DIAGNOSTIC_ONLY', 'medianRtfRole': 'DIAGNOSTIC_ONLY',
        'endToEndRole': 'DIAGNOSTIC_ONLY', 'timestampQuality': 'NOT_EVALUABLE',
        'scope': 'PHYSICAL_POCO_M5_BOUNDED_STAGE_0_ONLY', 'productionAdmission': False,
        'allDeviceSupport': False, 'retentionDeadline': '2026-10-25'}


def validate_profile(value):
    text.check_environment()
    require(canonical(value) == canonical(profile()), 'SMALL_PROFILE_MISMATCH')


def prepare_case(value, row, reference, hypothesis):
    """Reuse the exact 5.3A functions; never substitute a BASE profile identity."""
    validate_profile(value)
    text._identity(row['sampleId'], row['locale'])
    streams = [text.raw_tokens(reference), text.raw_tokens(hypothesis),
               text.normalized_tokens(reference), text.normalized_tokens(hypothesis)]
    require(bool(streams[2]), 'INVALID_EMPTY_NORMALIZED_REFERENCE')
    require(all(len(s) <= 4096 for s in streams)
            and all(len(t.encode('utf-16-le')) // 2 <= 4096 for s in streams for t in s)
            and (len(streams[0]) + 1) * (len(streams[1]) + 1)
            + (len(streams[2]) + 1) * (len(streams[3]) + 1) <= 25000000, 'ORACLE_INPUT_LIMIT_EXCEEDED')
    return {'caseId': row['sampleId'], 'locale': row['locale'],
            'rawReferenceTextSha256': hashlib.sha256(reference.encode('utf-8')).hexdigest(),
            'rawHypothesisTextSha256': hashlib.sha256(hypothesis.encode('utf-8')).hexdigest(),
            **dict(zip(('rawReferenceTokens', 'rawHypothesisTokens',
                        'normalizedReferenceTokens', 'normalizedHypothesisTokens'), streams)),
            'timestampAnchors': []}


def validate_selection(rows):
    require(type(rows) is list and len(rows) == 48, 'SELECTED_SET_INVALID')
    for row in rows: text._identity(row['sampleId'], row['locale'])
    require(len({r['sampleId'] for r in rows}) == 48
            and [r['locale'] for r in rows] == ['ru'] * 24 + ['en'] * 24, 'SELECTED_SET_INVALID')


def integer(value, minimum=0):
    return type(value) is int and minimum <= value <= 2**63 - 1


def telemetry_valid(rows, start, end, gap, width):
    if not (integer(start) and integer(end) and start < end
            and type(rows) is list and len(rows) >= 2): return False
    if not all(type(r) is list and len(r) == width and all(integer(v) for v in r) for r in rows):
        return False
    return (rows[0][0] <= start + gap and rows[-1][0] >= end - gap
            and rows[0][0] <= end and rows[-1][0] >= start
            and all(0 < b[0] - a[0] <= gap for a, b in zip(rows, rows[1:])))


def case_resources(row):
    """Decisive positive observations survive other missing/invalid telemetry."""
    failures = set()
    evidence = row.get('oomEvidence')
    def valid_event(e): return (type(e) is dict
        and set(e) == {'kind', 'sha256'} and e['kind'] in ('OWNED_PROCESS_LMK', 'NATIVE_ALLOCATION_FAILURE')
        and type(e['sha256']) is str and re.fullmatch('[0-9a-f]{64}', e['sha256']) is not None)
    valid_oom = type(evidence) is list and all(valid_event(e) for e in evidence)
    if type(evidence) is list and any(valid_event(e) for e in evidence): failures.add('OOM')
    memory, thermal = row.get('memory'), row.get('thermal')
    if type(memory) is list:
        for r in memory:
            if type(r) is list and len(r) == 3:
                if integer(r[1]) and r[1] > 1610612736: failures.add('PSS')
                if integer(r[2]) and r[2] > 1342177280: failures.add('NATIVE_HEAP')
    if type(thermal) is list:
        for r in thermal:
            if type(r) is list and len(r) == 2 and integer(r[1]) and 3 <= r[1] <= 6:
                failures.add('THERMAL_SEVERE')
    timing = all(integer(row.get(k), 1) for k in ('durationMicros', 'inferenceMicros', 'coldLoadMicros'))
    if integer(row.get('durationMicros'), 1) and integer(row.get('inferenceMicros'), 1):
        if row['inferenceMicros'] > 6 * row['durationMicros']: failures.add('RTF_MAXIMUM')
    if integer(row.get('coldLoadMicros'), 1):
        if row['coldLoadMicros'] > 15000000: failures.add('COLD_LOAD')
    valid = (timing and valid_oom and row.get('state') == 'SUCCEEDED'
        and row.get('cleanup') == 'VERIFIED' and row.get('freshProcessContext') is True
        and telemetry_valid(memory, row.get('windowStartMicros'), row.get('windowEndMicros'), 1000000, 3)
        and all(r[1] > 0 and r[2] > 0 for r in memory)
        and telemetry_valid(thermal, row.get('thermalWindowStartMicros'), row.get('thermalWindowEndMicros'), 3000000, 2)
        and all(0 <= r[1] <= 6 for r in thermal))
    return bool(valid), failures


def disposition(complete, quality, resource):
    require(type(complete) is bool and quality in ('PASS', 'FAIL', 'NOT_EVALUABLE')
            and resource in ('PASS', 'FAIL', 'NOT_EVALUABLE'), 'INVALID_DISPOSITION_INPUT')
    if quality == 'FAIL' and resource == 'FAIL':
        return 'VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_AND_RESOURCE_GATES'
    if resource == 'FAIL': return 'VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_RESOURCE_GATE'
    # A complete language's decisive quality rejection is retained even if another
    # stream is incomplete. Never infer the missing resource result to be PASS.
    if quality == 'FAIL': return 'VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_QUALITY_GATE'
    if complete and quality == resource == 'PASS': return 'PASS / ACCEPTED_FOR_BOUNDED_ALPHA_ON_POCO_M5'
    return 'INCONCLUSIVE / CAMPAIGN_EVIDENCE_INCOMPLETE'


def quality_valid(row):
    q = row.get('quality')
    return (row.get('state') == 'SUCCEEDED' and type(q) is dict
        and all(type(q.get(s)) is dict and integer(q[s].get('errors'))
                and integer(q[s].get('referenceTokens'), 1) for s in ('raw', 'normalized')))


def evaluate(value, selected, attempts, cleanup, evidence_complete=True, safety_evidence=()):
    validate_profile(value); validate_selection(selected)
    require(type(attempts) is list and len(attempts) <= 48, 'ATTEMPT_ACCOUNTING_INVALID')
    for expected, actual in zip(selected, attempts):
        require(actual.get('caseId') == expected['sampleId'] and actual.get('locale') == expected['locale']
                and type(actual.get('ordinal')) is int and actual['ordinal'] == 1, 'ATTEMPT_ACCOUNTING_INVALID')
    languages = {}
    quality_checks = [quality_valid(r) for r in attempts]
    for locale in ('ru', 'en'):
        rows = [r for r, valid in zip(attempts, quality_checks) if valid and r['locale'] == locale]
        languages[locale] = {'completedValidCases': len(rows), 'gate': 'NOT_EVALUABLE'}
        for stream in ('raw', 'normalized'):
            languages[locale][stream] = {k: sum(r['quality'][stream][k] for r in rows)
                                        for k in ('errors', 'referenceTokens')}
        if len(rows) == 24:
            q = languages[locale]['normalized']
            languages[locale]['gate'] = text.language_gate(locale, q['errors'], q['referenceTokens'])
    quality = ('FAIL' if any(v['gate'] == 'FAIL' for v in languages.values()) else
               'PASS' if all(v['gate'] == 'PASS' for v in languages.values()) else 'NOT_EVALUABLE')
    checks = [case_resources(r) for r in attempts]
    failures = set().union(*(f for _, f in checks))
    for observation in safety_evidence:
        # Pre-corpus warmup is not one of the 48 performance/quality cases.
        failures.update(c for c in case_resources(observation)[1] if c in ('OOM', 'THERMAL_SEVERE'))
    all_resource_valid = (evidence_complete is True and len(attempts) == 48
                          and all(v for v, _ in checks) and cleanup == 'VERIFIED')
    rtf = {'p95Rank': 46, 'state': 'NOT_EVALUABLE'}
    if len(attempts) == 48 and all(integer(r.get('durationMicros'), 1)
                                  and integer(r.get('inferenceMicros'), 1) for r in attempts):
        ratios = sorted(Fraction(r['inferenceMicros'], r['durationMicros']) for r in attempts)
        weighted = Fraction(sum(r['inferenceMicros'] for r in attempts), sum(r['durationMicros'] for r in attempts))
        if weighted > 2: failures.add('RTF_WEIGHTED')
        if ratios[45] > 4: failures.add('RTF_P95')
        def ratio(r): return {'numerator': r.numerator, 'denominator': r.denominator}
        rtf.update(state='OBSERVED', weighted=ratio(weighted), p95=ratio(ratios[45]),
                   maximum=ratio(ratios[-1]), medianDiagnostic=ratio((ratios[23] + ratios[24]) / 2))
    resource = 'FAIL' if failures else 'PASS' if all_resource_valid else 'NOT_EVALUABLE'
    complete = all_resource_valid and all(quality_checks)
    return {'profileId': PROFILE_ID, 'profileSha256': digest(profile()),
        'execution': 'PASS / BOUNDED_SMALL_POCO_48_CAMPAIGN_COMPLETE' if complete
                     else 'INCOMPLETE / BOUNDED_SMALL_POCO_CAMPAIGN_STOPPED',
        'candidate': disposition(complete, quality, resource), 'quality': quality,
        'qualityComplete': all(v['completedValidCases'] == 24 for v in languages.values()),
        'resource': resource, 'resourceFailures': sorted(failures), 'languages': languages,
        'attemptCount': len(attempts), 'rtf': rtf, 'cleanup': 'VERIFIED' if cleanup == 'VERIFIED' else 'UNVERIFIED',
        'timestampQuality': 'NOT_EVALUABLE', 'endToEndRole': 'DIAGNOSTIC_ONLY',
        'scope': 'PHYSICAL_POCO_M5_BOUNDED_STAGE_0_ONLY'}
