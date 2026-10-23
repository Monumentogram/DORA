import unittest
from run_stage9_emulator import successful, validate_serial


class Stage9EmulatorTest(unittest.TestCase):
    def test_physical_and_ambiguous_targets_are_rejected(self):
        for value in ("", "device", "emulator-5554 extra", "emulator-5554;echo"):
            with self.assertRaises(ValueError):
                validate_serial(value)
        validate_serial("emulator-5554")

    def test_instrumentation_exit_alone_cannot_claim_pass(self):
        self.assertFalse(successful("INSTRUMENTATION_CODE: -1", 3))
        self.assertFalse(successful("FAILURES!!!\nOK (3 tests)", 3))
        self.assertFalse(successful("OK (2 tests)", 3))
        self.assertFalse(successful("INSTRUMENTATION_STATUS_CODE: -3\nOK (3 tests)", 3))
        self.assertTrue(successful("OK (3 tests)\nINSTRUMENTATION_CODE: -1", 3))


if __name__ == "__main__":
    unittest.main()
