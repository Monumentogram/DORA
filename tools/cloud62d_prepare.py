"""Offline 6.2D preparation guards. These do not authorize or call AWS.

Private facts must be gathered and checked by the caller, never inferred from
these shape checks. A passing prospective guard is necessary, not sufficient,
for the separately bound live operator.
"""
import argparse
from collections import Counter
from decimal import Decimal
import hashlib
import json
from pathlib import Path
import re
import subprocess

BASELINE = 'd8d5c90516b6a9afa96c21e703dfe7481e64e589'
PHASE = 'docs/contracts/DORA_CLOUD_EVALUATION_PHASE_A_V0_1.json'
PREPARATION = 'docs/contracts/DORA_CLOUD_62D_PREPARATION_V0_1.json'
REPO = Path(__file__).resolve().parents[1]
PREPARATION_COMMIT = 'c98856e25f07ef27aa0121c6458a5d3436fc574a'
AMENDMENT = 'docs/contracts/DORA_CLOUD_62D_EIGHT_CLIP_PROTOCOL_V0_1.json'
AMENDMENT_COMMIT = 'e0d1479333122166192b5f6a5f27813d9eb86f09'
MOBILE = 'docs/contracts/DORA_CLOUD_62D_MOBILE_ACQUISITION_V0_1.json'
EIGHT_IDS = tuple(f'{lang}-{kind}-{n:02}' for lang in ('ru', 'en')
                  for kind in ('read', 'spontaneous') for n in (1, 2))


def require(condition, code):
    if not condition:
        raise ValueError(code)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def bounded_report(rows):
    """Summarize bound primary oracle counts, never score text or authorize AWS.

    Caller must establish immutable manifest membership, verified references,
    raw-result identity and oracle provenance. Returned per-case rows are PRIVATE.
    Missing attempts stay visible; successful retries cannot replace primaries.
    """
    require(type(rows) is list, 'REPORT_ROWS')
    indexed = {}
    count_keys = {'substitutions', 'deletions', 'insertions', 'reference_tokens'}
    for row in rows:
        require(type(row) is dict and set(row) == {'case_id', 'state', 'raw', 'normalized'},
                'REPORT_ROW_SHAPE')
        case_id = row['case_id']
        require(case_id in EIGHT_IDS and case_id not in indexed, 'REPORT_MEMBERSHIP')
        require(row['state'] in ('NOT_RUN', 'SUCCEEDED', 'FAILED'), 'REPORT_STATE')
        for mode in ('raw', 'normalized'):
            counts = row[mode]
            if row['state'] != 'SUCCEEDED':
                require(counts is None, 'UNMEASURED_COUNTS')
                continue
            require(type(counts) is dict and set(counts) == count_keys and
                    all(type(v) is int and 0 <= v <= 4096 for v in counts.values()) and
                    counts['reference_tokens'] > 0 and
                    counts['substitutions'] + counts['deletions'] <= counts['reference_tokens'],
                    'INVALID_ORACLE_COUNTS')
        indexed[case_id] = row

    def aggregate(items, mode):
        observed = [r[mode] for r in items if r['state'] == 'SUCCEEDED']
        counts = {key: sum(c[key] for c in observed) for key in count_keys}
        errors = sum(counts[k] for k in ('substitutions', 'deletions', 'insertions'))
        return {**counts, 'errors': errors, 'scored_records': len(observed),
                'wer_percent': errors * 100 / counts['reference_tokens']
                if counts['reference_tokens'] else None}

    records = []
    for case_id in EIGHT_IDS:
        row = indexed.get(case_id, {'case_id': case_id, 'state': 'NOT_RUN',
                                    'raw': None, 'normalized': None})
        records.append({**row, **{mode: aggregate([row], mode)
                                 for mode in ('raw', 'normalized')}})

    def summarize(ids, threshold):
        items = [indexed.get(i, {'state': 'NOT_RUN'}) for i in ids]
        modes = {mode: aggregate(items, mode) for mode in ('raw', 'normalized')}
        successes = sum(r['state'] == 'SUCCEEDED' for r in items)
        failed = sum(r['state'] == 'FAILED' for r in items)
        n, errors = modes['normalized']['reference_tokens'], modes['normalized']['errors']
        quality = ('PASS' if errors * 100 <= threshold * n else 'FAIL') if successes == len(ids) else (
            'NOT_RUN' if successes == failed == 0 else 'INCOMPLETE')
        return {**modes, 'expected_records': len(ids), 'succeeded_records': successes,
                'failed_records': failed, 'not_run_records': len(ids)-successes-failed,
                'threshold_percent': threshold, 'bounded_quality': quality}

    languages = {}
    for lang, threshold in (('ru', 20), ('en', 18)):
        ids = [i for i in EIGHT_IDS if i.startswith(lang + '-')]
        result = summarize(ids, threshold)
        result['speech_classes'] = {kind: summarize([i for i in ids if f'-{kind}-' in i], threshold)
                                    for kind in ('read', 'spontaneous')}
        if result['bounded_quality'] == 'PASS' and any(
                r['bounded_quality'] != 'PASS' for r in result['speech_classes'].values()):
            result['bounded_quality'] = 'FAIL'
        languages[lang] = result
    return {'scope': 'EIGHT_CLIPS_ONE_OWNER_ONLY', 'records': records, 'languages': languages,
            'timestamp_accuracy': 'NOT_EVALUATED', 'noise_robustness': 'NOT_EVALUATED',
            'broad_admission': 'NOT_ESTABLISHED', 'provider_admission': 'NOT_ESTABLISHED'}


def quote(durations_us, ancillary_tax_upper):
    """Frozen-rate reservation with one-second increments and no minimum.

    Include primary, diagnostic, uncertain and possible retry dispatches. This
    is a planning bound, not a current price quote or invoice verification.
    """
    require(isinstance(ancillary_tax_upper, Decimal) and
            ancillary_tax_upper.is_finite() and
            Decimal(0) <= ancillary_tax_upper <= Decimal(8), 'ANCILLARY_BOUND')
    require(0 < len(durations_us) <= 96, 'ATTEMPT_BOUND')
    require(all(type(d) is int and 0 <= d <= 600_000_000 for d in durations_us),
            'DURATION_BOUND')
    seconds = sum((d + 999_999) // 1_000_000 for d in durations_us)
    asr = Decimal(seconds) * Decimal('0.0001')
    require(seconds <= 20_000 and asr <= Decimal(2), 'ASR_RESERVATION_BOUND')
    total = asr + ancillary_tax_upper
    require(total <= Decimal(10), 'TOTAL_BUDGET_BOUND')
    return {'billable_seconds': seconds, 'asr_usd': str(asr),
            'ancillary_tax_upper_usd': str(ancillary_tax_upper),
            'upper_total_usd': str(total)}


def validate_dag(gates):
    graph = {g['gate_id']: g['dependencies'] for g in gates}
    require(len(graph) == len(gates), 'DUPLICATE_GATE')
    done, active = set(), set()

    def visit(node):
        require(node in graph, 'MISSING_DEPENDENCY')
        require(node not in active, 'GATE_CYCLE')
        if node in done:
            return
        active.add(node)
        for dependency in graph[node]:
            visit(dependency)
        active.remove(node)
        done.add(node)

    for node in graph:
        visit(node)


def public_corpus_summary(private):
    """Export exactly aggregate counts and whole inventory/manifest hashes."""
    result = {}
    for key in ('inventory_sha256', 'manifest_sha256'):
        value = private.get(key)
        require(value is None or isinstance(value, str) and
                re.fullmatch('[0-9a-f]{64}', value), 'WHOLE_HASH')
        result[key] = value
    for key in ('candidate_counts', 'recorded_counts', 'selected_counts', 'timing_counts'):
        counts = private[key]
        require(set(counts) == {'ru', 'en'} and
                all(type(v) is int and 0 <= v <= 36 for v in counts.values()),
                'AGGREGATE_COUNTS')
        result[key] = dict(counts)
    return result


def assert_prospective_publication(state):
    require(state.get('phase_a') == 'PASS', 'PHASE_A_NOT_PASS')
    require(state.get('tree_clean') is True, 'DIRTY_TREE')
    head = state.get('local_head', '')
    require(isinstance(head, str) and re.fullmatch('[0-9a-f]{40}', head) and
            state.get('fetched_remote_head') == head == state.get('published_commit'),
            'NOT_PUBLISHED_EXACT_COMMIT')
    require(type(state.get('aws_result_count')) is int and state['aws_result_count'] == 0,
            'PRIOR_AWS_RESULTS_OR_UNPROVEN_ZERO')
    checks = state.get('preflight', {})
    require(checks == {f'PREFLIGHT-{i:02d}': 'PASS' for i in range(1, 13)},
            'LIVE_PREFLIGHT_NOT_12_OF_12')
    for kind in ('manifest', 'config', 'client'):
        value = state.get(f'{kind}_sha256')
        require(isinstance(value, str) and re.fullmatch('[0-9a-f]{64}', value) and
                value == state.get(f'published_{kind}_sha256'), 'UNBOUND_' + kind.upper())


def git_blob(git, revision, path):
    return subprocess.run([git, '-C', str(REPO), 'show', f'{revision}:{path}'],
                          check=True, stdout=subprocess.PIPE).stdout


def render_record(record):
    """Lossless human view; JSON is deliberately visible for exact parity."""
    return ('# DORA 6.2D preparation v0.1\n\n'
            'Preparation only; Phase A remains PARTIAL and Phase B NOT_RUN.\n'
            'The following is the exact public machine record. No private paths,\n'
            'per-clip hashes, speech, account identifiers or credentials belong here.\n\n'
            '```json\n' + json.dumps(record, ensure_ascii=False, indent=2) + '\n```\n')


def validate_amendment(record):
    expected = {'protocol_id': 'dora-owned-reduced8-v2', 'active_ids': list(EIGHT_IDS),
                'wer_threshold_percent': {'ru': 20, 'en': 18},
                'timestamp_accuracy': 'NOT_EVALUATED', 'noise_robustness': 'NOT_EVALUATED',
                'phase_a_successor': 'NOT_CREATED', 'phase_b': 'NOT_RUN',
                'recording': 'DEFERRED_UNTIL_EXPLICIT_OWNER_READY',
                'budget_usd': {'total': 10, 'asr': 2, 'ancillary_tax': 8}}
    for key, value in expected.items():
        require(record.get(key) == value, 'AMENDMENT_SCOPE_' + key.upper())


def render_amendment(record):
    return ('# DORA 6.2D: prospective eight-recording protocol amendment v0.1\n\n'
            'Owner decision: 2026-09-28. Exactly **8 recordings**: RU 2 READ + 2 SPONTANEOUS; '
            'EN 2 READ + 2 SPONTANEOUS. All eight enter the bounded evaluation.\n\n'
            '**Recording is deferred.** Wait for the Owner\'s explicit “Готов записывать”. '
            'No microphone, login request, readiness polling or AWS evaluation now. '
            'That phrase permits guided acquisition only; actual words still require personal '
            'verification and AWS prerequisites remain independent.\n\n'
            'This is a prospective protocol change, **not a complete Phase A successor**. '
            'Published Phase A v0.1 and preparation evidence remain immutable historical records. '
            'The replacement table below names every relaxed requirement; none is claimed satisfied.\n\n'
            'WER limits remain RU ≤20% and EN ≤18%, computed from actual error/word counts '
            'separately by language, speech class and recording. No cross-language average. '
            'Noise robustness and timestamp accuracy are **NOT_EVALUATED / НЕ ОЦЕНЕНЫ**. '
            'One owner and eight recordings cannot establish quality for other voices or conditions; '
            'provider/broad admission remains unestablished where it depends on those properties.\n\n'
            'The following machine record is reproduced exactly. Private speech, references, '
            'per-record hashes, account identity and credentials must remain outside public Git.\n\n'
            '```json\n' + json.dumps(record, ensure_ascii=False, indent=2) + '\n```\n')


def validate_mobile(record):
    validate_amendment(record)
    require(record.get('application_id') == 'com.monumentogram.dora.stage0.ownedcorpus',
            'MOBILE_APPLICATION_ID')
    require(record.get('initial_seed_enabled') is False, 'MOBILE_INITIAL_DEFERRAL')
    require(record.get('accepted_capture_replacement') == 'FORBIDDEN', 'MOBILE_RETAKE_POLICY')
    require(record.get('technical_retry') == 'EXPLICIT_BEFORE_ACCEPTANCE_WITH_ALL_ATTEMPTS_RETAINED',
            'MOBILE_RETRY_POLICY')
    require(record.get('real_microphone_test') == 'NOT_RUN', 'MOBILE_REAL_MIC_NOT_AUTHORIZED')
    require(record.get('broad_admission') == 'NOT_ESTABLISHED', 'MOBILE_ADMISSION')


def render_mobile(record):
    return ('# DORA 6.2D mobile acquisition evidence v0.1\n\n'
            'Prospective acquisition tooling only; no complete Phase A successor or AWS run.\n'
            'The [design and requirement changes](DORA_CLOUD_62D_MOBILE_ACQUISITION_V0_1.md) '
            'define the bounded phone workflow. Only synthetic capture data was tested; '
            'real microphone testing is deferred until explicit Owner readiness.\n\n'
            '```json\n' + json.dumps(record, ensure_ascii=False, indent=2) + '\n```\n')


def verify(git):
    phase = json.loads((REPO / PHASE).read_text(encoding='utf-8'))
    frozen = [PHASE, 'docs/stage0/DORA_CLOUD_EVALUATION_PHASE_A_V0_1.md',
              'docs/stage0/DORA_CLOUD_EVALUATION_HARNESS_V0_1.md',
              'docs/evidence/cloud-6.2d-phase-a-host-v0.1.json']
    # Git canonical blobs and normalized checkout text are compared separately.
    for path in frozen:
        old = git_blob(git, BASELINE, path)
        require(git_blob(git, 'HEAD', path) == old, 'FROZEN_HISTORY_CHANGED')
        require((REPO / path).read_text(encoding='utf-8') ==
                old.decode('utf-8').replace('\r\n', '\n'), 'FROZEN_WORKTREE_CHANGED')
    for source in phase['sources']:
        data = git_blob(git, source['baseline_commit'], source['path'])
        require(len(data) == source['bytes'] and digest(data) == source['sha256'],
                'FROZEN_SOURCE_BINDING')
    gates = json.loads((REPO / 'docs/contracts/DORA_CLOUD_ALPHA_ADMISSION_GATES_V0_1.json')
                       .read_text(encoding='utf-8'))['gates']
    validate_dag(gates)
    require(len(gates) == len(phase['effective_gate_statuses']) == 39, 'GATE_COUNT')
    counts = Counter(phase['effective_gate_statuses'].values())
    require(all(counts[k] == v for k, v in phase['effective_status_counts'].items()),
            'GATE_ACCOUNTING')
    path = REPO / PREPARATION
    if path.exists():
        require(path.read_bytes().replace(b'\r\n', b'\n') ==
                git_blob(git, PREPARATION_COMMIT, PREPARATION).replace(b'\r\n', b'\n'),
                'HISTORICAL_PREPARATION_CHANGED')
        record = json.loads(path.read_text(encoding='utf-8'))
        require(record['source_baseline'] == BASELINE and record['phase_a'] == phase['phase_a']
                and record['phase_b'] == 'NOT_RUN', 'PREMATURE_PHASE_PROMOTION')
        require(record['effective_gate_statuses'] == phase['effective_gate_statuses'],
                'GATE_PROMOTION')
        for source in record['tool_bindings']:
            # The published preparation is historical evidence. Later protocol
            # revisions bind their own sources, never rewrite its source hashes.
            data = git_blob(git, PREPARATION_COMMIT, source['path']).replace(b'\r\n', b'\n')
            require(digest(data) == source['sha256_lf_utf8'], 'PREPARATION_SOURCE_BINDING')
        markdown = REPO / 'docs/stage0/DORA_CLOUD_62D_PREPARATION_V0_1.md'
        require(markdown.read_text(encoding='utf-8') == render_record(record), 'JSON_MD_PARITY')
    amendment_path = REPO / AMENDMENT
    if amendment_path.exists():
        require(amendment_path.read_bytes().replace(b'\r\n', b'\n') ==
                git_blob(git, AMENDMENT_COMMIT, AMENDMENT).replace(b'\r\n', b'\n'),
                'HISTORICAL_AMENDMENT_CHANGED')
        amendment = json.loads(amendment_path.read_text(encoding='utf-8'))
        validate_amendment(amendment)
        require(amendment['effective_gate_statuses'] == phase['effective_gate_statuses'],
                'GATE_PROMOTION')
        for source in amendment['tool_bindings']:
            data = git_blob(git, AMENDMENT_COMMIT, source['path']).replace(b'\r\n', b'\n')
            require(digest(data) == source['sha256_lf_utf8'], 'AMENDMENT_SOURCE_BINDING')
        markdown = REPO / 'docs/stage0/DORA_CLOUD_62D_EIGHT_CLIP_PROTOCOL_V0_1.md'
        require(markdown.read_text(encoding='utf-8') == render_amendment(amendment),
                'AMENDMENT_JSON_MD_PARITY')
    mobile_path = REPO / MOBILE
    if mobile_path.exists():
        mobile = json.loads(mobile_path.read_text(encoding='utf-8'))
        validate_mobile(mobile)
        require(mobile['effective_gate_statuses'] == phase['effective_gate_statuses'],
                'GATE_PROMOTION')
        for source in mobile['tool_bindings']:
            data = (REPO / source['path']).read_bytes().replace(b'\r\n', b'\n')
            require(digest(data) == source['sha256_lf_utf8'], 'MOBILE_SOURCE_BINDING')
        markdown = REPO / 'docs/stage0/DORA_CLOUD_62D_MOBILE_EVIDENCE_V0_1.md'
        require(markdown.read_text(encoding='utf-8') == render_mobile(mobile),
                'MOBILE_JSON_MD_PARITY')
    return {'frozen_phase_a': 'UNCHANGED', 'source_bindings': len(phase['sources']),
            'dag': 'PASS', 'gate_count': 39, 'counts': dict(counts),
            'preparation_record': 'VERIFIED' if path.exists() else 'NOT_YET_CREATED',
            'eight_clip_amendment': 'VERIFIED' if amendment_path.exists() else 'NOT_YET_CREATED',
            'mobile_acquisition': 'VERIFIED' if mobile_path.exists() else 'NOT_YET_CREATED'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--git', default='git')
    args = parser.parse_args()
    print(json.dumps(verify(args.git), indent=2))
