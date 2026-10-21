"""Safety gates for the synthetic-only preservation experiment."""
import unittest
from user0_preservation_runner import validate_target, validate_apk, validate_inventory
import user0_preservation_runner as runner


class PreservationRunnerTest(unittest.TestCase):
    def test_interrupted_instrumentation_stops_only_selected_synthetic_package(self):
        stops = []
        def interrupted():
            raise runner.diagnostics.InstrumentationFailure('SYNTHETIC_TIMEOUT')
        with self.assertRaises(runner.diagnostics.InstrumentationFailure):
            runner.execute_bounded(interrupted, lambda: stops.append(runner.PACKAGE))
        self.assertEqual(stops, ['com.monumentogram.dora.audio.test'])

    def test_stop_failure_is_not_reported_as_confirmed_termination(self):
        def fail():
            raise RuntimeError('not logged')
        with self.assertRaisesRegex(runner.diagnostics.InstrumentationFailure, 'STOP_UNCONFIRMED'):
            runner.execute_bounded(fail, fail)

    def test_physical_and_ambiguous_serials_are_rejected_before_query(self):
        for serial in ('', 'physical-device', 'emulator-5554 extra', '-e'):
            with self.subTest(serial=serial), self.assertRaises(ValueError):
                validate_target(serial, 28, lambda *args: self.fail('queried forbidden target'))

    def test_false_emulator_or_wrong_api_is_rejected(self):
        for overrides in ({'ro.kernel.qemu': '0'}, {'ro.hardware': 'physical'},
                          {'ro.build.version.sdk': '34'}):
            values = {'ro.kernel.qemu': '1', 'ro.hardware': 'ranchu', 'ro.build.version.sdk': '28'}
            values.update(overrides)
            with self.subTest(overrides=overrides), self.assertRaises(ValueError):
                validate_target('emulator-5554', 28, lambda *args: values[args[-1]])

    def test_admitted_emulator_only_uses_readonly_identity_queries(self):
        calls = []
        def query(*args):
            calls.append(args)
            return {'ro.kernel.qemu': '1', 'ro.hardware': 'ranchu', 'ro.build.version.sdk': '36'}[args[-1]]
        validate_target('emulator-5554', 36, query)
        self.assertTrue(all(args[:2] == ('shell', 'getprop') for args in calls))

    def test_only_synthetic_test_package_is_admitted(self):
        validate_apk("package: name='com.monumentogram.dora.audio.test' versionCode='1'")
        for name in ('com.monumentogram.dora', 'com.monumentogram.dora.debug', 'com.monumentogram.dora.alpha.test'):
            with self.subTest(name=name), self.assertRaises(ValueError):
                validate_apk(f"package: name='{name}'")

    def test_inventory_rejects_duplicate_empty_and_unrelated_tests(self):
        test = 'com.monumentogram.dora.audio.persistence.HistoricalPreservationTest#example'
        self.assertEqual(validate_inventory({'tests': [test]}), [test])
        for names in ([], [test, test], ['com.monumentogram.dora.Other#example']):
            with self.subTest(names=names), self.assertRaises(ValueError):
                validate_inventory({'tests': names})


if __name__ == '__main__':
    unittest.main()
