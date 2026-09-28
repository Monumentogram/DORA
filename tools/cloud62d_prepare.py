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


def require(condition, code):
    if not condition:
        raise ValueError(code)


def digest(data):
    return hashlib.sha256(data).hexdigest()


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
        record = json.loads(path.read_text(encoding='utf-8'))
        require(record['source_baseline'] == BASELINE and record['phase_a'] == phase['phase_a']
                and record['phase_b'] == 'NOT_RUN', 'PREMATURE_PHASE_PROMOTION')
        require(record['effective_gate_statuses'] == phase['effective_gate_statuses'],
                'GATE_PROMOTION')
        for source in record['tool_bindings']:
            data = (REPO / source['path']).read_bytes().replace(b'\r\n', b'\n')
            require(digest(data) == source['sha256_lf_utf8'], 'PREPARATION_SOURCE_BINDING')
        markdown = REPO / 'docs/stage0/DORA_CLOUD_62D_PREPARATION_V0_1.md'
        require(markdown.read_text(encoding='utf-8') == render_record(record), 'JSON_MD_PARITY')
    return {'frozen_phase_a': 'UNCHANGED', 'source_bindings': len(phase['sources']),
            'dag': 'PASS', 'gate_count': 39, 'counts': dict(counts),
            'preparation_record': 'VERIFIED' if path.exists() else 'NOT_YET_CREATED'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--git', default='git')
    args = parser.parse_args()
    print(json.dumps(verify(args.git), indent=2))
