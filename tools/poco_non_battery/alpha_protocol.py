"""Owner-approved reduced sample and conservative autonomous safety policy."""
import hashlib
import json

from receipts import boolean, integer, require, cycle_evidence, chunks_verified, vad_verified

OWNER_DECISION = 'OD-86B-REDUCED-AUTONOMOUS-ALPHA-ACCEPTANCE'


def seal(protocol):
    return hashlib.sha256(json.dumps(protocol, sort_keys=True, separators=(',', ':'),
                                     ensure_ascii=True).encode()).hexdigest()


def verify_seal(protocol, expected):
    require(isinstance(expected, str) and len(expected) == 64 and seal(protocol) == expected,
            'Protocol changed after seal')


def keyguard_action(secure, showing):
    require(type(secure) is bool and type(showing) is bool, 'Unknown keyguard state')
    require(not secure, 'SECURE_KEYGUARD_REQUIRES_OWNER')
    return 'DISMISS_NONSECURE' if showing else 'NONE'


def validate_reduced_cycles(rows, protected_count=46):
    result = cycle_evidence(rows, 60, protected_count)
    for row in rows:
        if row['started']:
            frames = integer(row, 'readbackFrames')
            result['pass'] &= frames >= 80000 and chunks_verified(row, frames, minimum_caps=0) and vad_verified(row)
            result['pass'] &= (integer(row, 'stopRequestedElapsedMs')
                               - integer(row, 'startConfirmedElapsedMs') >= 5000)
    result['scope'] = 'REDUCED_OWNER_ONLY_ALPHA_SAMPLE_NOT_99_5_PERCENT_RELIABILITY_PROOF'
    return result


def watchdog_reason(row):
    # Validate the entire snapshot before interpreting it; absent evidence is never healthy.
    online = boolean(row, 'adbOnline')
    alive = boolean(row, 'supervisorAlive')
    age = integer(row, 'hostHeartbeatAgeMs')
    elapsed = integer(row, 'elapsedMs')
    deadline = integer(row, 'hardDeadlineMs', 1)
    thermal = integer(row, 'thermal')
    require(thermal <= 6, 'Unknown Android thermal classification')
    free = integer(row, 'freeBytes')
    expected = boolean(row, 'recordingExpected')
    present = boolean(row, 'fgsPresent')
    for failed, reason in (
        (not online, 'ADB_UNAVAILABLE'), (not alive, 'ORCHESTRATOR_EXITED'),
        (age > 30000, 'HOST_HEARTBEAT_EXPIRED'), (elapsed > deadline, 'HARD_DEADLINE'),
        (thermal >= 3, 'THERMAL_SEVERE'), (free < 16777216, 'STORAGE_RESERVE_EXHAUSTED'),
        (expected and not present, 'RECORDING_FGS_MISSING'),
    ):
        if failed:
            return reason
    return None
