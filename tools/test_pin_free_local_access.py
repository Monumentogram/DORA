"""Permanent ADR-0021 gate. Historical evidence and test fixtures are not product code."""
import pathlib
import unittest
from pin_free_policy import FORBIDDEN, violations

ROOT = pathlib.Path(__file__).resolve().parents[1]


class PinFreeLocalAccessTest(unittest.TestCase):
    def test_no_application_pin_or_biometric_prompt(self):
        self.assertEqual([], violations(ROOT), "NO_APPLICATION_PIN_OR_BIOMETRIC_PROMPT")

    def test_multiline_auth_bound_key_and_platform_prompts_are_rejected(self):
        for source in ('setUserAuthenticationRequired(\n true\n)', 'BiometricPrompt.Builder',
                       'createConfirmDeviceCredentialIntent(', 'ACTION_SET_NEW_PASSWORD'):
            self.assertIsNotNone(FORBIDDEN.search(source))

    def test_future_module_is_checked_but_historical_test_fixture_is_excluded(self):
        import tempfile
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            for variant in ('main', 'androidTest'):
                path = root / 'android/new/module/src' / variant / 'Gate.java'
                path.parent.mkdir(parents=True)
                path.write_text('BiometricPrompt', encoding='utf-8')
            self.assertEqual(['android/new/module/src/main/Gate.java'], violations(root))


if __name__ == "__main__":
    unittest.main()
