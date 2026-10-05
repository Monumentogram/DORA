"""Owner-scoped battery waiver; never admits physical recording acceptance."""
import json

DOCUMENTS = {
    'docs/DORA_MVP1_STAGE_STATUS.md',
    'docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md',
    'docs/DORA_MVP1_PRODUCT_DECISIONS.md',
}
PREFIX = '''## 2026-10-05 — Owner defers battery efficiency beyond Alpha

Current Stage 8.6 Alpha status: **NOT_READY / NON_BATTERY_ACCEPTANCE_GAPS_OPEN**.
Battery efficiency: **DEFERRED / NON_BLOCKING_FOR_ALPHA** by explicit Owner decision.
Freshness: PASS / OPERATIONALLY_VALIDATED; repeatability: DEFERRED / NOT_RUN;
comparative source admission: DEFERRED; DORA/baseline ratio: NOT_EVALUATED.
Hardware microWh: NOT_EVALUATED / HARDWARE_ENERGY_COUNTER_UNAVAILABLE.

[ADR-PERF-002](adr/ADR-PERF-002-alpha-battery-efficiency-deferral.md) and
[current gate audit](governance/poco-battery-alpha-deferral.json) supersede only
the battery-efficiency requirement as an Alpha blocker. No energy PASS is asserted.
PERF-REC-002 tracks deferred Beta/separate performance validation, with new Owner scope required.
Further battery repeats, cable experiments and Wi-Fi remediation for measurement are stopped.

Stage 8 remains IN_PROGRESS. Non-battery 8.6 gates remain open: 200 physical
Start/Finalize cycles; clean hour-long screen-off capture, integrity, storage,
thermal/resources; storage-budget/low-storage UX; bounded Doze/Battery Saver,
notification and Recovery checks. No replacement physical campaign is launched here.
8.3/8.4/8.4C/8.5 retain their accepted predecessor status; earlier dated entries
below are immutable historical snapshots. Group D / Cloud / ASR implementation
is NOT_STARTED by this decision. Next product stage after recording closure:
Stage 9 ASR/versioning under the admitted Alpha roadmap, separately scoped.
DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE remains OPEN; PERF-REC-001 stays deferred/non-blocking.
No Sheet write, merge, runtime/model/profile change or device-matrix acceptance.

---

'''


def normalize_document(path, raw):
    raw = raw.replace(b'\r\n', b'\n')
    if path not in DOCUMENTS:
        raise ValueError('Unapproved Alpha document override')
    prefix = PREFIX.encode()
    if not raw.startswith(prefix) or raw.count(prefix) != 1:
        raise ValueError('Alpha deferral prefix missing or changed')
    return raw[len(prefix):]


def validate(record):
    exact = {
        'ownerDecision': 'OD-86-ALPHA-BATTERY-DEFER',
        'stage86AlphaStatus': 'NOT_READY / NON_BATTERY_ACCEPTANCE_GAPS_OPEN',
        'batteryFreshness': 'PASS / OPERATIONALLY_VALIDATED',
        'batteryRepeatability': 'DEFERRED / NOT_RUN',
        'comparativeSourceAdmission': 'DEFERRED',
        'doraBaselineRatio': 'NOT_EVALUATED',
        'hardwareMicroWhGate': 'NOT_EVALUATED / HARDWARE_ENERGY_COUNTER_UNAVAILABLE',
        'batteryEfficiencyGate': 'DEFERRED / NON_BLOCKING_FOR_ALPHA',
        'securityRestorationBlocker': 'OPEN',
        'PERF-REC-001': 'DEFERRED_NON_BLOCKING',
        'sheet': 'UNCHANGED',
        'groupD': 'NOT_STARTED',
        'cloudAsrImplementation': 'NOT_STARTED',
    }
    if any(record.get(k) != v for k, v in exact.items()):
        raise ValueError('Owner waiver cannot certify unexecuted gates')
    for key in ('stage86Closed', 'fullAlphaClosed', 'energyPassed', 'runtimeChanged',
                'sixBatteryRepeatsExecuted', 'oneHourBatteryCampaignExecuted',
                'cycles200Executed', 'hardwareEnergyInvented', 'historicalEvidenceRewritten'):
        if record.get(key) is not False:
            raise ValueError('Unapproved Alpha closure or measurement claim')
    if record.get('deferredBacklogId') != 'PERF-REC-002' or record.get('newOwnerDecisionRequiredToResume') is not True:
        raise ValueError('Battery scope must remain deferred')
    if len(record.get('remainingGateGroups', [])) < 6:
        raise ValueError('Remaining acceptance gaps omitted')


def validate_file(root):
    validate(json.loads((root / 'docs/governance/poco-battery-alpha-deferral.json').read_text(encoding='utf-8')))
