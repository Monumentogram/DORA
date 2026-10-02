"""Negative source-admission controls, not microphone runtime evidence."""
import unittest
from unittest.mock import patch
from types import SimpleNamespace
from pathlib import Path
import product_recording_ci_profile as ci
import validate_product_recording as gate

ROOT = Path(__file__).resolve().parents[1]


class RecordingAdmissionTests(unittest.TestCase):
    def test_recovery_successor_requires_complete_recording_gate(self):
        import validate_poc_recovery_governance as recovery
        import validate_encrypted_persistence as persistence
        with patch.object(persistence, 'validate_checkout', side_effect=ValueError('invalid successor')):
            with self.assertRaisesRegex(ValueError, 'invalid successor'):
                recovery.rec_clean_integrated_candidate(SimpleNamespace(branch=gate.BRANCH))

    def test_bootstrap_microphone_cannot_skip_successor_admission(self):
        import validate_stage00 as bootstrap
        with patch.object(gate, 'candidate', return_value=False):
            with self.assertRaisesRegex(ValueError, 'must not request microphone'):
                bootstrap.validate_android_bootstrap()
        with patch.object(gate, 'validate_checkout', side_effect=ValueError('invalid successor')):
            with self.assertRaisesRegex(ValueError, 'invalid successor'):
                bootstrap.validate_android_bootstrap()

    def test_capture_poc_does_not_admit_unreviewed_product_microphone(self):
        import validate_poc_capture as poc
        with patch.object(gate, 'candidate', return_value=False):
            with self.assertRaisesRegex(ValueError, 'microphone-free'):
                poc.validate_module_and_manifests()
        with patch.object(gate, 'validate_checkout', side_effect=ValueError('invalid successor')):
            with self.assertRaisesRegex(ValueError, 'invalid successor'):
                poc.validate_module_and_manifests()

    def test_manifest_requires_unexported_microphone_service(self):
        source = (ROOT / 'android/app/src/main/AndroidManifest.xml').read_text()
        gate.validate_manifest(source)
        for old, new in [('android:foregroundServiceType="microphone"', 'android:foregroundServiceType="dataSync"'),
                         ('android:stopWithTask="false"', 'android:stopWithTask="true"'),
                         ('android.permission.RECORD_AUDIO', 'android.permission.INTERNET')]:
            self.assertIn(old, source)
            with self.assertRaises(ValueError):
                gate.validate_manifest(source.replace(old, new))

    def test_plaintext_store_and_content_logs_rejected(self):
        for forbidden in ('FileOutputStream', 'RandomAccessFile', 'MediaRecorder(', '.writeBytes(', 'android.util.Log'):
            with self.assertRaises(ValueError):
                gate.validate_capture_sources({'capture.kt': forbidden})

    def test_ci_addition_preserves_parent_and_rejects_changed_gate(self):
        parent = 'before\n' + ci.ANCHOR + 'middle\n' + ci.DEVICE_ANCHOR + 'after\n'
        updated = ci.upgrade(parent)
        self.assertEqual(parent, ci.normalize(updated))
        with self.assertRaises(ValueError):
            ci.normalize(updated.replace('validate_product_recording.py', 'echo.py'))
        with self.assertRaises(ValueError):
            ci.normalize(updated + ci.GATE)
        with self.assertRaises(ValueError):
            ci.normalize(updated.replace(ci.DEVICE_GATE, ''))

    def test_source_status_never_claims_pass(self):
        self.assertIn('8.3 = PENDING_FINAL_PUBLICATION', gate.STATUS_HEADER)
        self.assertIn('8.4 = NOT_STARTED', gate.STATUS_HEADER)
        self.assertNotIn('8.3 = PASS', gate.STATUS_HEADER)


if __name__ == '__main__':
    unittest.main()
