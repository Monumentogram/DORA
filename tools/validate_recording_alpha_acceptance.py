"""Exact additive Owner governance; never a physical threshold PASS or runtime exception."""
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = '1272ea212308eeb8cb9b27e81aa35bf939967d8f'
RECORD = 'docs/governance/recording-control-alpha-acceptance.json'
STATUS_HEADER = '## 2026-10-03 — Owner acceptance of Stage 8.3 responsiveness for Alpha\n\nCurrent governance disposition: ACCEPTED_FOR_ALPHA / INSTANT_CONTROL_FUNCTIONALLY_RESOLVED.\n8.3 = PASS / functional recording controls.\n8.3 latency remediation = OWNER_ACCEPTED_FOR_ALPHA.\nLatency remediation accepted for Alpha by owner; strict historical micro-latency gate remains not technically passed.\nHistorical strict campaign = NOT_READY / PHYSICAL_INSTANT_CONTROL_GATE_FAILED.\nPERF-REC-001 = DEFERRED_AFTER_ALPHA_ACCEPTANCE / NOT BLOCKING ALPHA.\n8.4 = NOT_STARTED; readiness = READY_TO_START in a separate owner-scoped task after publication.\nStage 8 = IN_PROGRESS.\n\n[ADR-PERF-001](adr/ADR-PERF-001-alpha-recording-control-latency-acceptance.md)\nrecords the exact Owner decision and [content-free evidence](governance/recording-control-alpha-acceptance.json).\nThis later disposition removes only the micro-latency blocker to Alpha progression.\nEarlier PENDING/P1/NOT_READY entries below are preserved historical snapshots;\ntheir strict timing requirement is deferred, not retroactively satisfied.\nDEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE remains OPEN and still blocks final Alpha\nclosure and signed Alpha security acceptance. This is no production-authentication certification.\n\n### PERF-REC-001 — Pause/Resume presentation micro-latency hardening\n\nPriority: non-blocking Alpha. Status: DEFERRED_AFTER_ALPHA_ACCEPTANCE.\nScope: investigate remaining input-to-presentation variance; add exact StateFlow\npublication / Compose observation timing only if needed; profile main-thread,\nrecomposition and layout work; optimize only for a measurable user or release need.\nScheduling: separate performance-hardening activity, not part of Stage 8.4.\nRe-evaluate before public release; the Owner must explicitly scope any later work.\nNo runtime optimization, changed thresholds, new device campaign or Stage 8.4 implementation is admitted here.\n\n---\n\n'
DECISION_HEADER = '## 2026-10-03 — Owner Alpha acceptance of recording-control responsiveness\n\n[ADR-PERF-001](adr/ADR-PERF-001-alpha-recording-control-latency-acceptance.md)\nrecords ACCEPTED_FOR_ALPHA / INSTANT_CONTROL_FUNCTIONALLY_RESOLVED.\nHistorical strict micro-latency acceptance remains NOT_READY / PHYSICAL_INSTANT_CONTROL_GATE_FAILED;\nthe quantitative thresholds are unchanged and are not declared passed.\nPERF-REC-001 is deferred and non-blocking Alpha, outside Stage 8.4.\n8.4 remains NOT_STARTED, READY_TO_START only as a separate task after publication.\nThe OPEN development-security restoration blocker still gates final/signed Alpha closure.\nEarlier decisions below remain historical and unchanged.\n\n---\n\n'
SEALED_DOCUMENTS = {'docs/adr/ADR-PERF-001-alpha-recording-control-latency-acceptance.md': 'fcfdb88cc35e7b01ed70845e9661596617375078382679caf1d0ca00f2dac58e', 'docs/governance/recording-control-alpha-acceptance.json': '6d97f124080209273bdb20b9f779150efb847eae19c5992b13d02029d88816de'}
STATUS_PATHS = ('docs/DORA_MVP1_STAGE_STATUS.md', 'docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md')
DECISIONS = 'docs/DORA_MVP1_PRODUCT_DECISIONS.md'
PATHS = set(SEALED_DOCUMENTS) | set(STATUS_PATHS) | {DECISIONS,
    'tools/validate_recording_alpha_acceptance.py',
    'tools/validate_development_device_security.py',
    'tools/validate_security_identity_contract.py',
    'tools/test_development_device_security.py'}
LIMITS = {'pause_ack':100, 'pause_admission':50, 'pause_native':200, 'pause_confirmed':250,
          'resume_ack':100, 'resume_native':150, 'resume_first_pcm':300, 'resume_confirmed':300}


def require(value, message):
    if not value:
        raise ValueError(message)


def candidate(root=ROOT):
    return (root / RECORD).exists()


def read_record(root=ROOT):
    return json.loads((root / RECORD).read_text(encoding='utf-8'))


def validate_record(record):
    require(record['product_source_sha'] == BASE, 'Owner decision source changed')
    require(record['disposition'] == 'ACCEPTED_FOR_ALPHA / INSTANT_CONTROL_FUNCTIONALLY_RESOLVED'
            and record['remediation'] == 'OWNER_ACCEPTED_FOR_ALPHA', 'Owner disposition is not a PASS')
    require(record['historical_strict_gate'] == 'NOT_READY / PHYSICAL_INSTANT_CONTROL_GATE_FAILED'
            and record['strict_gate_technically_passed'] is False, 'Historical strict gate cannot pass')
    require(record['historical_limits_ms'] == LIMITS, 'Historical thresholds changed')
    require(record['performance_item'] == {'id':'PERF-REC-001',
            'title':'Pause/Resume presentation micro-latency hardening',
            'status':'DEFERRED_AFTER_ALPHA_ACCEPTANCE', 'blocks_alpha':False,
            'scheduled_in_stage_8_4':False}, 'Performance item must remain deferred outside 8.4')
    require(record['stages'] == {'8.3':'PASS / functional recording controls', '8.4':'NOT_STARTED',
            '8.4_readiness':'READY_TO_START_AFTER_PUBLICATION', '8':'IN_PROGRESS'}, 'Stage disposition changed')
    require(record['security_restoration_blocker'] == 'OPEN', 'Alpha security closure is not authorized')


def validate_paths(paths):
    require(set(paths) <= PATHS, 'Only the exact governance docs/tools delta is allowed')


def validate_status_projection(current, historical):
    require(current == STATUS_HEADER + historical, 'Owner status prefix or historical bytes changed')
    return historical


def validate_overlay(root=ROOT):
    # This supplements (never replaces) the complete inherited source-admission checks.
    from validate_encrypted_persistence import git
    require(git(root, 'merge-base', BASE, 'HEAD').decode().strip() == BASE, 'Wrong governance parent')
    actual = set(git(root, 'diff', '--name-only', '--no-renames', BASE).decode().splitlines())
    actual.update(git(root, 'ls-files', '--others', '--exclude-standard').decode().splitlines())
    validate_paths(actual)
    require(actual == PATHS, 'Incomplete exact governance inventory')
    for line in git(root, 'rev-list', '--reverse', '--parents', BASE + '..HEAD').decode().splitlines():
        parts = line.split()
        require(len(parts) == 2, 'Governance merge history rejected')
        paths = set(git(root, 'diff', '--name-only', '--no-renames', parts[1], parts[0]).decode().splitlines())
        require(bool(paths), 'Empty governance commit rejected')
        validate_paths(paths)
    for path, expected in SEALED_DOCUMENTS.items():
        require(hashlib.sha256((root/path).read_bytes()).hexdigest() == expected, 'Sealed Owner document changed: ' + path)
    for path in STATUS_PATHS:
        require((root/path).read_bytes() == STATUS_HEADER.encode() + git(root,'show',BASE+':'+path),
                'Historical status bytes changed')
    require((root/DECISIONS).read_bytes() == DECISION_HEADER.encode() + git(root,'show',BASE+':'+DECISIONS),
            'Historical product decisions changed')
    validate_record(read_record(root))
    # All non-allowlisted tracked bytes (including Android, workflows, contracts, evidence,
    # and restoration blocker) are unchanged from the exact reviewed product baseline.
    return PATHS
