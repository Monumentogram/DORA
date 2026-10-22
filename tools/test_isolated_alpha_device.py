"""The mutating runner is restricted to disposable emulators."""
import unittest
import json
from pathlib import Path
import tempfile

from run_isolated_alpha_device import emulator_fence, created_user, switch_complete
from run_isolated_alpha_device import failure_summary, write_failure_diagnostics


class DeviceRunnerTests(unittest.TestCase):
    def test_failure_summary_keeps_only_exact_known_locations_and_fixed_stage(self):
        output = '''INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: private message
    at com.monumentogram.dora.operator.SyntheticSystemKeyguard.unlockIfNeeded(SyntheticSystemKeyguard.kt:45)
    at com.monumentogram.dora.operator.IsolatedAlphaOperatorInstrumentedTest.realSecondaryRuntimeAuthenticatedReadbackDeletionAndReopen(IsolatedAlphaOperatorInstrumentedTest.kt:86)
    at com.monumentogram.dora.operator.SyntheticSystemKeyguard.unlockIfNeeded(SyntheticSystemKeyguard.kt:45)
'''
        self.assertEqual({
            'schema_version': 1, 'status': 'FAILED_NOT_ACCEPTANCE', 'api': 36,
            'caller_state': None,
            'stage': 'SYSTEM_KEYGUARD_SETUP', 'locations': [
                {'class': 'SyntheticSystemKeyguard', 'method': 'unlockIfNeeded',
                 'source': 'SyntheticSystemKeyguard.kt', 'line': 45},
                {'class': 'IsolatedAlphaOperatorInstrumentedTest',
                 'method': 'realSecondaryRuntimeAuthenticatedReadbackDeletionAndReopen',
                 'source': 'IsolatedAlphaOperatorInstrumentedTest.kt', 'line': 86},
            ],
        }, failure_summary(output, 36))

    def test_failure_summary_never_publishes_messages_pin_paths_or_unknown_frames(self):
        output = '''INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: PIN=123456 /data/user/10/private-vault identity-secret
    at evil.private.Secret.identity(Secret.kt:123)
    at com.monumentogram.dora.operator.SyntheticSystemKeyguard.secret(SyntheticSystemKeyguard.kt:45)
    at com.monumentogram.dora.operator.SyntheticSystemKeyguard.unlockIfNeeded(/private/SyntheticSystemKeyguard.kt:45)
    at com.monumentogram.dora.operator.SyntheticSystemKeyguard.unlockIfNeeded(SyntheticSystemKeyguard.kt:123456)
    at com.monumentogram.dora.operator.SyntheticSystemKeyguard.unlockIfNeeded(SyntheticSystemKeyguard.kt:45) PIN=123456
    at com.monumentogram.dora.operator.SyntheticSystemKeyguard.unlockIfNeeded(IsolatedAlphaOperatorInstrumentedTest.kt:45)
    at com.monumentogram.dora.operator.IsolatedAlphaOperatorInstrumentedTest.unlock$secret(IsolatedAlphaOperatorInstrumentedTest.kt:45)
'''
        result = failure_summary(output, 28)
        self.assertEqual([], result['locations'])
        self.assertEqual('UNCLASSIFIED', result['stage'])
        public = json.dumps(result)
        for private in ('123456', '/data/', '/private/', 'identity-secret', 'AssertionError',
                        'PIN=', 'evil', 'secret', 'stack='):
            self.assertNotIn(private, public)

    def test_failure_writer_preserves_private_redaction_and_public_nonacceptance(self):
        output = '''INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: PIN=123456 /private/location
    at com.monumentogram.dora.operator.IsolatedAlphaOperatorInstrumentedTest.unlock(IsolatedAlphaOperatorInstrumentedTest.kt:313)
'''
        with tempfile.TemporaryDirectory() as directory:
            receipt = Path(directory) / 'operator.json'
            write_failure_diagnostics(receipt, output, 36)
            self.assertFalse(receipt.exists())
            self.assertTrue(receipt.with_suffix('.failure.json').exists())
            public = json.loads(receipt.with_suffix('.failure.json').read_text(encoding='utf-8'))
            self.assertEqual('FAILED_NOT_ACCEPTANCE', public['status'])
            self.assertEqual('AUTHENTICATED_OPEN', public['stage'])
            self.assertNotIn('123456', json.dumps(public))
            self.assertNotIn('/private/', json.dumps(public))
            private = receipt.with_suffix('.failure.local.txt').read_text(encoding='utf-8')
            self.assertNotIn('123456', private)
            self.assertIn('[REDACTED]', private)
            self.assertIn('/private/location', private)

    def test_failure_metadata_is_bounded_and_stages_are_fixed(self):
        prefix = '    at com.monumentogram.dora.operator.IsolatedAlphaOperatorInstrumentedTest.'
        for method, expected in (
                ('awaitCallerResumed', 'CALLER_READINESS'),
                ('enterCredential', 'APP_CREDENTIAL_ENTRY'),
                ('waitUi', 'OPERATOR_UI'), ('delete', 'CONFIRMED_DELETION')):
            with self.subTest(method=method):
                frame = prefix + method + '(IsolatedAlphaOperatorInstrumentedTest.kt:50)'
                self.assertEqual(expected, failure_summary(frame, 36)['stage'])
        output = '\n'.join(prefix + 'unlock(IsolatedAlphaOperatorInstrumentedTest.kt:' +
                           str(line) + ')' for line in range(1, 30))
        self.assertEqual(16, len(failure_summary(output, 28)['locations']))
        for line in ('0', '-1', '2001', '123456', '7secret'):
            with self.subTest(line=line):
                frame = prefix + 'unlock(IsolatedAlphaOperatorInstrumentedTest.kt:' + line + ')'
                self.assertEqual([], failure_summary(frame, 36)['locations'])
        with self.assertRaises(ValueError):
            failure_summary(output, 0)

    def test_failure_receipt_shares_bounded_caller_diagnostics(self):
        line = ('INSTRUMENTATION_STATUS: stack=java.lang.AssertionError: '
                'Credential prompt timeout: NO_RESULT callerResumedAtRequest=true '
                'focusAtRequest=false secure=true locked=true '
                'callerFinishing=false callerDestroyed=false')
        frame = ('\n    at com.monumentogram.dora.operator.IsolatedAlphaOperatorInstrumentedTest.'
                 'unlock(IsolatedAlphaOperatorInstrumentedTest.kt:339)\n')
        for assertion, valid in [(line, True), (line + ' PIN=123456 /private/location', False),
                                 (line.replace('NO_RESULT', 'FAILED_SECRET'), False),
                                 (line + '\n' + line.replace('locked=true', 'locked=false'), False)]:
            with self.subTest(valid=valid), tempfile.TemporaryDirectory() as directory:
                receipt = Path(directory) / 'operator.json'
                write_failure_diagnostics(receipt, assertion + frame, 36)
                public = json.loads(receipt.with_suffix('.failure.json').read_text(encoding='utf-8'))
                self.assertEqual('FAILED_NOT_ACCEPTANCE', public['status'])
                self.assertEqual('AUTHENTICATED_OPEN', public['stage'])
                self.assertEqual(339, public['locations'][0]['line'])
                if valid:
                    self.assertEqual('PROMPT_TIMEOUT', public['caller_state']['failure'])
                    self.assertEqual('NO_RESULT', public['caller_state']['availability'])
                    self.assertTrue(public['caller_state']['locked'])
                    self.assertFalse(public['caller_state']['caller_destroyed'])
                else:
                    self.assertIsNone(public['caller_state'])
                for private in ('123456', '/private/', 'SECRET', 'PIN', 'AssertionError'):
                    self.assertNotIn(private, json.dumps(public))

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
