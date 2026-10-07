"""Mutation tests for the narrow Stage 8.6A admission, not substitutes for device tests."""
from pathlib import Path
import subprocess
import unittest
import poco_final_deletion as change

ROOT = Path(__file__).resolve().parents[1]
BASE = '60a38e13739c04f18cffc21783329b633cf68a8f'


class FinalDeletionAdmissionTest(unittest.TestCase):
    def original(self, path):
        return subprocess.check_output(['git', 'show', BASE + ':' + path], cwd=ROOT)

    def test_only_explicit_additions_preserve_original_bytes(self):
        for path in change.OVERRIDES:
            self.assertEqual(change.normalize(path, (ROOT / path).read_bytes(), self.original(path)),
                             self.original(path).replace(b'\r\n', b'\n'))

    def test_removing_old_assertion_fails(self):
        raw = (ROOT / change.TEST).read_bytes().replace(b'                    assertTrue(fault.fired)', b'                    // removed')
        with self.assertRaises(ValueError):
            change.normalize(change.TEST, raw, self.original(change.TEST))

    def test_transaction_cleanup_change_fails(self):
        raw = (ROOT / change.PRODUCTION).read_bytes().replace(b'            database.close()', b'            database.endTransaction()\n            database.close()')
        with self.assertRaises(ValueError):
            change.normalize(change.PRODUCTION, raw, self.original(change.PRODUCTION))

    def test_main_thread_guard_removal_fails(self):
        raw = (ROOT / change.PRODUCTION).read_bytes().replace(b'check(Looper.myLooper() != Looper.getMainLooper())', b'check(true)')
        with self.assertRaises(ValueError):
            change.normalize(change.PRODUCTION, raw, self.original(change.PRODUCTION))

    def test_duplicate_addition_fails(self):
        raw = (ROOT / change.PRODUCTION).read_bytes() + change.EXECUTOR.encode()
        with self.assertRaises(ValueError):
            change.normalize(change.PRODUCTION, raw, self.original(change.PRODUCTION))

    def test_unapproved_path_fails(self):
        with self.assertRaises(ValueError):
            change.normalize('android/app/Other.kt', b'', b'')

    def test_mutated_before_after_state_fails(self):
        raw = (ROOT / change.TEST).read_bytes().replace(b'AudioSourceState.UserDeleted', b'AudioSourceState.Missing')
        with self.assertRaises(ValueError):
            change.normalize(change.TEST, raw, self.original(change.TEST))
