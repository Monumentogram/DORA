"""Host safety and receipt parsing; no adb, emulator or AWS calls."""
import unittest

from cloud18_integration import successful, validate_serial


class Cloud18RunnerTests(unittest.TestCase):
    def test_only_explicit_emulator_serials_are_admitted(self):
        validate_serial("emulator-5554")
        for serial in ("", "POCO", "192.168.0.2:5555", "emulator-", "emulator-5554 extra", "emulator-5554\n"):
            with self.subTest(serial=serial), self.assertRaises(ValueError):
                validate_serial(serial)

    def test_success_requires_exact_completed_test_count(self):
        self.assertTrue(successful("OK (1 test)\nINSTRUMENTATION_CODE: -1", 1))
        self.assertTrue(successful("OK (2 tests)", 2))
        for output in ("OK (0 tests)", "OK (2 tests)", "INSTRUMENTATION_CODE: 0", "INSTRUMENTATION_FAILED: offline", "OK (1 test)\nFAILURES!!!", "OK (1 test)\nINSTRUMENTATION_STATUS_CODE: -3"):
            with self.subTest(output=output):
                self.assertFalse(successful(output, 1))


if __name__ == "__main__":
    unittest.main()
