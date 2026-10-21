"""Negative controls for the emulator-only pilot acceptance runner."""
import importlib.util
import json
from pathlib import Path
import unittest
import tempfile
from unittest.mock import patch


class PilotRunnerTests(unittest.TestCase):
    def setUp(self):
        source = Path(__file__).with_name('run_owner_pilot_device.py')
        self.assertTrue(source.is_file(), 'Pilot runner is required')
        spec = importlib.util.spec_from_file_location('pilot_runner', source)
        self.runner = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.runner)

    def test_physical_serial_never_reaches_adb(self):
        calls = []
        with self.assertRaises(ValueError):
            self.runner.emulator_fence('physical-fixture', 36, lambda *args: calls.append(args))
        self.assertEqual([], calls)

    def test_named_emulator_still_requires_qemu_and_exact_api(self):
        for qemu, api in [('0', '36'), ('1', '28')]:
            def command(*args):
                return 'device' if args == ('get-state',) else qemu if args[-1] == 'ro.kernel.qemu' else api
            with self.subTest(qemu=qemu, api=api), self.assertRaises(ValueError):
                self.runner.emulator_fence('emulator-5554', 36, command)

    def test_inventory_rejects_omission_duplicate_unexpected_and_operator_tests(self):
        contract = json.loads((Path(__file__).parents[1] / 'docs/contracts/owner-pilot-device-tests-v1.json').read_text())
        self.runner.validate_inventory(contract)
        for mutation in ('missing', 'duplicate', 'operator', 'unknown'):
            invalid = json.loads(json.dumps(contract))
            names = invalid['phases']['secondary']
            if mutation == 'missing':
                names.pop()
            elif mutation == 'duplicate':
                names.append(names[0])
            elif mutation == 'operator':
                names[0] = 'com.monumentogram.dora.operator.IsolatedAlphaOperatorInstrumentedTest#read'
            else:
                invalid['phases']['unexpected'] = [names[0]]
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                self.runner.validate_inventory(invalid)

    def test_instrumentation_is_exact_and_diagnostics_never_include_raw_failure(self):
        name = self.runner.CLASS + '#packageManifestAndReleaseOverridesAreClosed'
        def output(test=name, code=0, count=1):
            owner, method = test.split('#')
            return (f'INSTRUMENTATION_STATUS: class={owner}\nINSTRUMENTATION_STATUS: test={method}\n'
                    f'INSTRUMENTATION_STATUS_CODE: {code}\nOK ({count} test)\nINSTRUMENTATION_CODE: -1\n')
        self.assertEqual([{'name': name, 'result': 'PASS'}], self.runner.exact_results(output(), {name}))
        cases = [output(code=-4), output(code=-2), output(count=2), output(test=name+'Unexpected'),
                 output()+output(), 'PIN=123456 private audio /data/user/10/vault']
        for raw in cases:
            with self.subTest(raw=raw[:20]), self.assertRaisesRegex(ValueError, '^PILOT_INSTRUMENTATION_REJECTED$'):
                self.runner.exact_results(raw, {name})

    def test_created_user_never_accepts_owner_or_ambiguous_identity(self):
        self.assertEqual(12, self.runner.created_user('Success: created user id 12'))
        for value in ['Success: created user id 0', 'Success: created user id 12\nSuccess: created user id 13', '12']:
            with self.assertRaises(ValueError):
                self.runner.created_user(value)

    def test_apk_input_admission_denies_other_packages_and_wrong_test_targets(self):
        package = self.runner.PACKAGE
        app = f"package: name='{package}' versionCode='1' versionName='0.1.0-owner-alpha.1'"
        test = f"package: name='{package}.test' versionCode='1' versionName=''"
        xml = ('<manifest xmlns:android="http://schemas.android.com/apk/res/android" '
               f'package="{package}.test"><instrumentation '
               'android:name="androidx.test.runner.AndroidJUnitRunner" '
               f'android:targetPackage="{package}" /></manifest>')
        self.runner.admit_apk_inputs(app, test, xml)
        for a, t, x in [(app.replace(package, 'com.monumentogram.dora'), test, xml),
                        (app, test.replace(package, 'other.package'), xml),
                        (app, test, xml.replace(f'targetPackage="{package}"', 'targetPackage="com.monumentogram.dora"')),
                        (app, test, xml.replace('</manifest>', '<instrumentation /></manifest>'))]:
            with self.assertRaises(ValueError):
                self.runner.admit_apk_inputs(a, t, x)

    def test_timeout_force_stops_only_exact_synthetic_packages_in_selected_user(self):
        calls = []
        self.runner.stop_synthetic_instrumentation(lambda *args: calls.append(args), 12)
        self.assertEqual([
            ('shell', 'am', 'force-stop', '--user', '12', self.runner.PACKAGE + '.test'),
            ('shell', 'am', 'force-stop', '--user', '12', self.runner.PACKAGE),
        ], calls)
        with self.assertRaises(ValueError):
            self.runner.stop_synthetic_instrumentation(lambda *args: calls.append(args), -1)

    def test_failure_locations_publish_only_fixed_source_and_bounded_line(self):
        raw = ('PIN=123456 private vault /data/user/10/secret\n'
               '    at com.monumentogram.dora.ownerpilot.OwnerPilotInstrumentedTest.secret(OwnerPilotInstrumentedTest.kt:123)\n'
               '    at com.monumentogram.dora.ownerpilot.OwnerPilotInstrumentedTest.secret(SyntheticSystemKeyguard.kt:123)\n'
               '    at com.monumentogram.dora.ownerpilot.OwnerPilotInstrumentedTest.secret(OwnerPilotInstrumentedTest.kt:123456)\n')
        result = self.runner.failure_locations(raw)
        self.assertEqual([{'source': 'OwnerPilotInstrumentedTest.kt', 'line': 123}], result)
        for private in ['123456', 'PIN', 'secret', '/data/', 'vault']:
            self.assertNotIn(private, json.dumps(result))

    def test_source_binding_rejects_dirty_changed_and_mismatched_ci_head(self):
        commit = 'a' * 40
        def command(*args):
            return commit if args == ('rev-parse', 'HEAD') else ''
        self.assertEqual({'source_sha': commit, 'source_clean': True},
                         self.runner.source_binding(command, commit))
        with self.assertRaises(ValueError):
            self.runner.source_binding(command, 'b' * 40)
        for state in [' M tracked.py', '?? new.py', 'M  staged.py']:
            def dirty(*args):
                return commit if args == ('rev-parse', 'HEAD') else state
            with self.subTest(state=state), self.assertRaises(ValueError):
                self.runner.source_binding(dirty)
        for malformed in ['main', '', 'a' * 39, 'A' * 40]:
            with self.subTest(head=malformed), self.assertRaises(ValueError):
                self.runner.source_binding(lambda *args: malformed if args == ('rev-parse', 'HEAD') else '')

    def test_force_stop_failure_preserves_failure_receipt_and_original_locations(self):
        raw = ('PIN=123456 /private/location\n'
               '    at com.monumentogram.dora.ownerpilot.OwnerPilotInstrumentedTest.unlock(OwnerPilotInstrumentedTest.kt:123)\n')
        def rejected(*args):
            raise ValueError('private command failure')
        with tempfile.TemporaryDirectory() as directory:
            receipt = Path(directory) / 'pilot.json'
            self.runner.record_instrumentation_failure(receipt, raw, 36, 'secondary', rejected, 12)
            self.assertFalse(receipt.exists())
            failure = json.loads(receipt.with_suffix('.failure.json').read_text())
            self.assertEqual('FAILED_NOT_ACCEPTANCE', failure['status'])
            self.assertEqual('STOP_UNCONFIRMED', failure['instrumentation_cleanup'])
            self.assertEqual([{'source': 'OwnerPilotInstrumentedTest.kt', 'line': 123}], failure['locations'])
            for private in ['123456', '/private/', 'private command failure']:
                self.assertNotIn(private, json.dumps(failure))

    def test_dirty_or_ci_mismatched_source_stops_main_before_any_subprocess_or_apk_read(self):
        arguments = ['run_owner_pilot_device.py', '--serial', 'emulator-5554', '--expected-api', '36',
                     '--apk', 'absent.apk', '--test-apk', 'absent-test.apk', '--receipt', 'absent.json']
        for dirty, ci in [(True, 'a' * 40), (False, 'b' * 40)]:
            def command(*args):
                return 'a' * 40 if args == ('rev-parse', 'HEAD') else ' M tracked.py' if dirty else ''
            with self.subTest(dirty=dirty, ci=ci), patch('sys.argv', arguments), \
                    patch.dict(self.runner.os.environ, {'GITHUB_SHA': ci}), \
                    patch.object(self.runner, 'git_command', command), \
                    patch.object(self.runner.subprocess, 'run') as launched:
                with self.assertRaisesRegex(ValueError, 'PILOT_SOURCE_(NOT_CLEAN|SHA_MISMATCH)'):
                    self.runner.main()
                launched.assert_not_called()


if __name__ == '__main__':
    unittest.main()
