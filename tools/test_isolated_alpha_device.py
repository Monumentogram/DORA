"""The mutating runner is restricted to disposable emulators."""
import unittest

from run_isolated_alpha_device import emulator_fence, created_user, switch_complete


class DeviceRunnerTests(unittest.TestCase):
    def test_physical_serial_never_reaches_adb(self):
        calls = []
        with self.assertRaises(ValueError):
            emulator_fence('physical-fixture', 36, lambda *args: calls.append(args))
        self.assertEqual([], calls)

    def test_emulator_name_cannot_override_real_device_property(self):
        def command(*args):
            return 'device' if args == ('get-state',) else '0'
        with self.assertRaises(ValueError):
            emulator_fence('emulator-5566', 36, command)

    def test_expected_api_is_mandatory(self):
        def command(*args):
            if args == ('get-state',):
                return 'device'
            return '1' if args[-1] == 'ro.kernel.qemu' else '28'
        with self.assertRaises(ValueError):
            emulator_fence('emulator-5566', 36, command)

    def test_created_user_is_exact_and_never_owner(self):
        self.assertEqual(12, created_user('Success: created user id 12'))
        for text in ('Success: created user id 0', 'Error: no user',
                     'Success: created user id 12\nSuccess: created user id 13'):
            with self.subTest(text=text), self.assertRaises(ValueError):
                created_user(text)

    def test_switch_requires_actual_user_and_no_pending_target(self):
        self.assertTrue(switch_complete('  mCurrentUserId:0\n  mTargetUserId:-10000\n', 0))
        self.assertFalse(switch_complete('  mCurrentUserId:13\n  mTargetUserId:0\n', 0))
        self.assertFalse(switch_complete('  mCurrentUserId:0\n  mTargetUserId:13\n', 0))
        self.assertFalse(switch_complete('0', 0))


if __name__ == '__main__':
    unittest.main()
