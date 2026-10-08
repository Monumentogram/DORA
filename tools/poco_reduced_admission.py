"""Narrow additive Stage 8.6B scope. Historical source/evidence must remain exact."""
import hashlib
import json
from pathlib import PurePosixPath

MANIFEST = 'docs/contracts/poco-reduced-alpha-8.6b.json'
PROTECTED_BASE = '20057696c556a354d08e007dfcba736ff217fe6d'
PROTECTED_OVERRIDES = {
    'tools/test_logical_recovery_final_deletion.py',
    'android/core/audio/build.gradle.kts',
    'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/AndroidAudioDeletionStorage.kt',
    'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/EncryptedAudioVault.kt',
    'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/journal/RoomAudioJournal.kt',
    'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/journal/VaultJournalLease.kt',
    'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/keys/AndroidVaultKeyBackend.kt',
    'tools/run_product_recording_device.py',
}
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
REMEDIATION_PREFIX = '''## 2026-10-08 — LONG01 forensic / UI / build remediation

**PARTIAL / LONG_CAPTURE_ROOT_CAUSE_UNPROVEN**. The source-preserving inspector
authenticated 31,040,000 contiguous committed frames (388 units); all 46 originals
and all 8,550 original vault files were unchanged. Historical interruption remains
NOT_READY; the missing terminal enum cannot be reconstructed from counter proximity.
Successor code adds bounded terminal diagnostics and accessible preflight actions.
Clean nonincremental builds are reproducible; the historical incremental sequence
reproduces the physical DEX payload. Old physical proof does not transfer to new code.
Normal UI smoke is NOT_RUN because automatic Recovery may reconcile retained LONG01;
reviewed isolation or separate owner disposition is required. No long retry admitted.

See [remediation receipts](evidence/poco-reduced-alpha-8.6b/remediation-01/README.md).
Exact publication-SHA CI remains a separate required check, reported with its run ID.
Stage 8.6 not accepted; Sheet unchanged; battery deferral and security blocker unchanged.
No Stage 9 / Group D / Cloud / ASR admission. PR99 remains DRAFT / OPEN / UNMERGED.
The earlier dated status and evidence below are retained verbatim.

---

'''
PROTECTED_PREFIX = '## 2026-10-08 — Protected historical Recovery owner admission\n\n[OD-86B-LITE-PROTECTED-HISTORICAL-RECOVERY](adr/ADR-RECORDING-009-protected-historical-recovery.md)\nauthorizes temporary debug-only read-only protection of all 47 historical sources.\nImplementation/synthetic validation: IN_PROGRESS. Physical isolation and successor\nacceptance: NOT_RUN. No Stage 8.6 PASS. Original LONG01 failure remains unchanged.\n\nThe exact private snapshot restricts historical catalog, metadata, keys and files\nbefore mutation. New test sources retain full ordinary Recovery and deletion.\nOnly after isolation tests, independent review, exact-APK bounded physical smoke\nand complete preservation PASS may the fresh60 cycles and one powered hour run.\nNo battery work; deferrals, Sheet, GroupD/Stage9/Cloud/ASR and security blocker unchanged.\nSee [successor evidence](evidence/poco-reduced-alpha-8.6b/protected-isolation-02/README.md).\nEarlier dated statements below are retained as historical evidence.\n\n---\n\n'
PREFIX = PROTECTED_PREFIX + REMEDIATION_PREFIX + PREFIX

OVERRIDES = {
    # Owner-scoped LONG01/UI/build remediation; exact before/after bytes remain sealed.
    'android/app/src/androidTest/kotlin/com/monumentogram/dora/DoraBootstrapAppTest.kt',
    'android/app/src/main/kotlin/com/monumentogram/dora/recording/AudioRecordCapture.kt',
    'android/app/src/main/kotlin/com/monumentogram/dora/recording/CaptureAdmission.kt',
    'android/app/src/test/kotlin/com/monumentogram/dora/recording/CaptureAdmissionTest.kt',
    'android/app/src/test/kotlin/com/monumentogram/dora/recording/NativeCaptureLifetimeTest.kt',
    'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/recording/RecordingSession.kt',
    'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/recording/RecordingSegmentation.kt',
    'tools/run_product_recording_device.py',
    'android/app/src/main/kotlin/com/monumentogram/dora/recording/RecordingController.kt',
    'android/app/src/main/kotlin/com/monumentogram/dora/recording/RecordingScreen.kt',
}
EXACT_ADDITIONS = {
    # OD-86B-LITE-PROTECTED-HISTORICAL-RECOVERY: exact additive diagnostic sources.
    'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/diagnostic/DiagnosticKeyFenceTest.kt',
    'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/diagnostic/DiagnosticPolicyLoaderTest.kt',
    'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/diagnostic/ProtectedHistoricalVaultTest.kt',
    'android/core/audio/src/debug/kotlin/com/monumentogram/dora/audio/persistence/DiagnosticBuild.kt',
    'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/DiagnosticJournalFactory.kt',
    'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/DiagnosticPolicyBinding.kt',
    'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/DiagnosticPolicyLoader.kt',
    'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/DiagnosticSourcePolicy.kt',
    'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/DiagnosticStorageFence.kt',
    'android/core/audio/src/release/kotlin/com/monumentogram/dora/audio/persistence/DiagnosticBuild.kt',
    'android/core/audio/src/test/kotlin/com/monumentogram/dora/audio/persistence/DiagnosticPolicyBindingTest.kt',
    'android/core/audio/src/test/kotlin/com/monumentogram/dora/audio/persistence/DiagnosticSourcePolicyTest.kt',
    'android/core/audio/src/test/kotlin/com/monumentogram/dora/audio/persistence/DiagnosticStorageFenceTest.kt',
    'android/core/audio/src/testRelease/kotlin/com/monumentogram/dora/audio/persistence/DiagnosticReleaseFenceTest.kt',
    'docs/adr/ADR-RECORDING-009-protected-historical-recovery.md',
    'android/core/audio/src/test/kotlin/com/monumentogram/dora/audio/recording/ProtectedOriginalRecoveryRiskTest.kt',
    'docs/adr/ADR-RECORDING-008-protected-shared-catalog-exception.md',
    'tools/poco_protected_snapshot.py',
    'android/app/src/main/kotlin/com/monumentogram/dora/recording/RecordingTerminalDiagnostics.kt',
    'android/app/src/test/kotlin/com/monumentogram/dora/recording/RecordingTerminalDiagnosticsTest.kt',
    'android/app/src/test/kotlin/com/monumentogram/dora/recording/CapturePersistenceBackpressureTest.kt',
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
    if value.get('protectedBase') != PROTECTED_BASE or set(value.get('protectedOverrides', {})) != PROTECTED_OVERRIDES:
        raise ValueError('Protected historical Recovery scope changed')
    for path in value['files']:
        validate_added_path(path)
    for path, expected in value['files'].items():
        if digest((root / path).read_bytes()) != expected:
            raise ValueError('Reduced acceptance source/evidence seal mismatch: ' + path)
    return value
