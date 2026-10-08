import hashlib
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).parents[1]))
import poco_reduced_admission as admission


class SourceAdmissionTest(unittest.TestCase):
    def test_only_exact_reviewed_before_and_after_bytes_can_normalize(self):
        old, new = b'old baseline\n', b'reviewed storage budget\n'
        entry = {'before': hashlib.sha256(old).hexdigest(), 'after': hashlib.sha256(new).hexdigest()}
        self.assertEqual(admission.normalize_override(new, old, entry), old)
        for current, original in ((new + b'weaken assertion', old), (new, old + b'changed')):
            with self.assertRaises(ValueError):
                admission.normalize_override(current, original, entry)

    def test_added_paths_cannot_escape_explicit_stage_scope(self):
        for path in ('android/core/audio/src/main/Fake.kt', '.github/workflows/android-ci.yml',
                     'tools/../secret.txt', 'docs/evidence/other/result.json'):
            with self.assertRaises(ValueError):
                admission.validate_added_path(path)
        admission.validate_added_path('tools/poco_non_battery/receipts.py')
