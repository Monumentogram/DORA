"""Mutation tests for the narrow Stage 8.6A admission, not substitutes for device tests."""
from pathlib import Path
import subprocess
import unittest
import poco_final_deletion as change
import poco_reduced_admission as successor

ROOT = Path(__file__).resolve().parents[1]
BASE = '60a38e13739c04f18cffc21783329b633cf68a8f'


class FinalDeletionAdmissionTest(unittest.TestCase):
    def accepted(self, path):
        # Exercise the original narrow normalizer against its accepted input;
        # current protected-source bytes are checked independently below.
        return subprocess.check_output(['git', 'show', successor.PROTECTED_BASE + ':' + path], cwd=ROOT)

    def original(self, path):
        return subprocess.check_output(['git', 'show', BASE + ':' + path], cwd=ROOT)

    def test_only_explicit_additions_preserve_original_bytes(self):
        for path in change.OVERRIDES:
            self.assertEqual(change.normalize(path, self.accepted(path), self.original(path)),
                             self.original(path).replace(b'\r\n', b'\n'))

    def test_removing_old_assertion_fails(self):
        raw = self.accepted(change.TEST).replace(b'                    assertTrue(fault.fired)', b'                    // removed')
        with self.assertRaises(ValueError):
            change.normalize(change.TEST, raw, self.original(change.TEST))

    def test_transaction_cleanup_change_fails(self):
        raw = self.accepted(change.PRODUCTION).replace(b'            database.close()', b'            database.endTransaction()\n            database.close()')
        with self.assertRaises(ValueError):
            change.normalize(change.PRODUCTION, raw, self.original(change.PRODUCTION))

    def test_main_thread_guard_removal_fails(self):
        raw = self.accepted(change.PRODUCTION).replace(b'check(Looper.myLooper() != Looper.getMainLooper())', b'check(true)')
        with self.assertRaises(ValueError):
            change.normalize(change.PRODUCTION, raw, self.original(change.PRODUCTION))

    def test_duplicate_addition_fails(self):
        raw = self.accepted(change.PRODUCTION) + change.EXECUTOR.encode()
        with self.assertRaises(ValueError):
            change.normalize(change.PRODUCTION, raw, self.original(change.PRODUCTION))

    def test_unapproved_path_fails(self):
        with self.assertRaises(ValueError):
            change.normalize('android/app/Other.kt', b'', b'')

    def test_mutated_before_after_state_fails(self):
        raw = self.accepted(change.TEST).replace(b'AudioSourceState.UserDeleted', b'AudioSourceState.Missing')
        with self.assertRaises(ValueError):
            change.normalize(change.TEST, raw, self.original(change.TEST))

    def test_current_successor_passes_both_exact_seal_layers(self):
        contract = successor.load(ROOT)
        raw = (ROOT / change.PRODUCTION).read_bytes()
        restored = successor.normalize_override(raw, self.accepted(change.PRODUCTION),
                                                contract['protectedOverrides'][change.PRODUCTION])
        self.assertEqual(change.normalize(change.PRODUCTION, restored, self.original(change.PRODUCTION)),
                         self.original(change.PRODUCTION).replace(b'\r\n', b'\n'))

    def test_current_successor_worker_guard_mutation_fails_before_normalization(self):
        contract = successor.load(ROOT)
        raw = (ROOT / change.PRODUCTION).read_bytes()
        mutated = raw.replace(b'check(Looper.myLooper() != Looper.getMainLooper())', b'check(true)')
        self.assertNotEqual(raw, mutated)
        with self.assertRaises(ValueError):
            successor.normalize_override(mutated, self.accepted(change.PRODUCTION),
                                         contract['protectedOverrides'][change.PRODUCTION])
