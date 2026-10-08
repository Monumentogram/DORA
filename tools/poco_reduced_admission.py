"""Narrow additive Stage 8.6B scope. Historical source/evidence must remain exact."""
import hashlib
import json
from pathlib import PurePosixPath

MANIFEST = 'docs/contracts/poco-reduced-alpha-8.6b.json'
DOCUMENTS = {
    'docs/DORA_MVP1_STAGE_STATUS.md', 'docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md',
    'docs/DORA_MVP1_PRODUCT_DECISIONS.md',
}
PREFIX = '''## 2026-10-08 — Owner-scoped autonomous POCO Alpha acceptance

Stage 8.6B-LITE: **NOT_READY / POCO_SCREEN_OFF_CAPTURE_FAILED**.
Owner scope is exactly 60 real five-second cycles and one USB-powered 3,600-second
screen-off run. All 60 must pass; this is not the historical 200-cycle reliability
claim or the former three-hour acceptance. No whole Stage 8.6 closure is asserted.

The fixed short series passed 60/60 with full authenticated readback and exact
owned-source deletion. DORA-LONG-01 interrupted after about 32.5 minutes with
LONG_CAPTURE_INTERRUPTED; it was not repeated. The failed source is retained,
its full authenticated readback is NOT_RUN, and catalog-only inspection proves
46/46 original recordings preserved. Remaining functional smokes are NOT_RUN
following this main-campaign failure. The next scoped work is interruption
root-cause isolation; no later product stage is admitted by these results.

[ADR-RECORDING-005](adr/ADR-RECORDING-005-hour-storage-admission.md) admits a minimal
storage budget: 125,000,000 bytes/hour plus 16,777,216-byte finalization headroom.
Fresh Start and the native capture admission boundary check exact free bytes;
unknown capacity fails closed. UI shows free/required space and a refresh action.
This is headroom admission, not preallocation or a new retention/deletion policy.

[ADR-RECORDING-006](adr/ADR-RECORDING-006-reduced-autonomous-alpha-acceptance.md) defines
reduced coverage; [OD-86B-LITE-BATTERY-SAVER-DEFERRAL](adr/ADR-RECORDING-007-usb-battery-saver-deferral.md)
supersedes its Saver requirement: **DEFERRED / USB_POWER_CONSTRAINT**. Autonomous
Doze may be deferred if unavailable. Canonical integrity, storage, thermal, real
VAD, Recovery, owner-data preservation and safe shutdown are not weakened.

Battery efficiency remains DEFERRED / NON_BLOCKING_FOR_ALPHA (ADR-PERF-002).
Hardware microWh and comparative ratio remain NOT_EVALUATED. No battery experiments.
Current evidence is under [poco-reduced-alpha-8.6b](evidence/poco-reduced-alpha-8.6b/).
Historical failures, earlier requirements and dated statuses below remain intact.
Exact-SHA CI is reported for the publication commit and cannot override the failed
physical gate. Independent review checks evidence truth, not a physical PASS. Sheet C78 unchanged.
Group D / Stage 9 / Cloud / ASR NOT_STARTED. Security restoration remains OPEN;
PERF-REC-001 deferred/non-blocking. PR99 stays DRAFT / OPEN / UNMERGED.

---

'''
OVERRIDES = {
    'android/app/src/main/kotlin/com/monumentogram/dora/recording/RecordingController.kt',
    'android/app/src/main/kotlin/com/monumentogram/dora/recording/RecordingScreen.kt',
}
EXACT_ADDITIONS = {
    'android/app/src/main/kotlin/com/monumentogram/dora/recording/RecordingStorageBudget.kt',
    'android/app/src/test/kotlin/com/monumentogram/dora/recording/RecordingStorageBudgetTest.kt',
    'tools/poco_alpha_acceptance.py', 'tools/poco_reduced_admission.py',
    'tools/test_logical_recovery_poco_reduced.py',
    'docs/adr/ADR-RECORDING-005-hour-storage-admission.md',
    'docs/adr/ADR-RECORDING-006-reduced-autonomous-alpha-acceptance.md',
    'docs/adr/ADR-RECORDING-007-usb-battery-saver-deferral.md',
    'docs/superpowers/plans/2026-10-08-poco-non-battery-acceptance.md',
    'docs/superpowers/plans/2026-10-08-poco-reduced-autonomous-alpha.md',
}


def digest(raw):
    return hashlib.sha256(raw.replace(b'\r\n', b'\n')).hexdigest()


def normalize_document(path, raw):
    raw = raw.replace(b'\r\n', b'\n')
    prefix = PREFIX.encode()
    if path not in DOCUMENTS or not raw.startswith(prefix) or raw.count(prefix) != 1:
        raise ValueError('Reduced document prefix missing or changed')
    return raw[len(prefix):]


def normalize_override(current, original, entry):
    if digest(current) != entry.get('after') or digest(original) != entry.get('before'):
        raise ValueError('Reduced acceptance override differs from reviewed exact source')
    return original.replace(b'\r\n', b'\n')


def validate_added_path(path):
    if '..' in PurePosixPath(path).parts or PurePosixPath(path).is_absolute():
        raise ValueError('Invalid reduced acceptance path')
    if path not in EXACT_ADDITIONS and not path.startswith((
            'tools/poco_non_battery/', 'docs/evidence/poco-reduced-alpha-8.6b/')):
        raise ValueError('Unapproved reduced acceptance addition')
    if PurePosixPath(path).suffix not in ('.py', '.java', '.xml', '.json', '.md', '.kt'):
        raise ValueError('Binary/raw logs not admitted to reduced evidence')


def load(root):
    value = json.loads((root / MANIFEST).read_text(encoding='utf-8'))
    if set(value['overrides']) != OVERRIDES:
        raise ValueError('Reduced runtime override scope changed')
    for path in value['files']:
        validate_added_path(path)
    for path, expected in value['files'].items():
        if digest((root / path).read_bytes()) != expected:
            raise ValueError('Reduced acceptance source/evidence seal mismatch: ' + path)
    return value
