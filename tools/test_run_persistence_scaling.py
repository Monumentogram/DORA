import contextlib
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import run_persistence_scaling as runner
from run_persistence_scaling import validate_serial, verify_emulator


class ScalingTargetTests(unittest.TestCase):
    def test_physical_and_missing_serials_rejected_before_any_command(self):
        for serial in ('', '192.0.2.4:5555', 'POCO123', 'emulator-5580 shell id'):
            with self.subTest(serial=serial), self.assertRaises(ValueError):
                validate_serial(serial)

    def test_qemu_property_required_even_with_emulator_shaped_serial(self):
        with self.assertRaises(ValueError):
            verify_emulator(lambda *args: '0' if args[-1] == 'ro.kernel.qemu' else '36', 36)

    def test_api_mismatch_rejected(self):
        with self.assertRaises(ValueError):
            verify_emulator(lambda *args: '1' if args[-1] == 'ro.kernel.qemu' else '28', 36)

    def test_valid_target_has_explicit_api_binding(self):
        validate_serial('emulator-5580')
        self.assertEqual(36, verify_emulator(lambda *args: '1' if args[-1] == 'ro.kernel.qemu' else '36', 36))


class ScalingFailureCleanupTests(unittest.TestCase):
    def exercise_failure(self, instrumentation_result, stop_error=None):
        # Only subprocess I/O is replaced: real CLI, parser, receipt and fresh files run.
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            apk = root / 'synthetic.apk'
            apk.write_bytes(b'synthetic APK fixture; never installed')
            destination = root / 'new-run'
            adb = root / 'nonexistent-adb'
            prefix = [str(adb), '-s', 'emulator-5580']
            calls = []

            def fake_run(command, **kwargs):
                self.assertEqual(prefix, command[:3])
                parts = command[3:]
                calls.append(parts)
                if parts == ['shell', 'getprop', 'ro.kernel.qemu']:
                    return subprocess.CompletedProcess(command, 0, stdout='1')
                if parts == ['shell', 'getprop', 'ro.build.version.sdk']:
                    return subprocess.CompletedProcess(command, 0, stdout='36')
                if parts == ['install', '-r', str(apk.resolve())]:
                    return subprocess.CompletedProcess(command, 0, stdout='Success')
                if parts[:3] == ['shell', 'am', 'instrument']:
                    self.assertEqual('com.monumentogram.dora.audio.test/androidx.test.runner.AndroidJUnitRunner', parts[-1])
                    self.assertEqual('com.monumentogram.dora.audio.diagnostics.PersistenceScalingBenchmarkTest',
                                     parts[parts.index('class') + 1])
                    kwargs['stdout'].write(b'private instrumentation diagnostic\n')
                    if isinstance(instrumentation_result, BaseException):
                        raise instrumentation_result
                    return subprocess.CompletedProcess(command, instrumentation_result)
                if parts == ['shell', 'am', 'force-stop', 'com.monumentogram.dora.audio.test']:
                    self.assertEqual(120, kwargs['timeout'])
                    if stop_error is not None:
                        raise stop_error
                    return subprocess.CompletedProcess(command, 0, stdout='')
                self.fail('Unexpected subprocess command')

            arguments = ['run_persistence_scaling.py', '--adb', str(adb),
                         '--serial', 'emulator-5580', '--expected-api', '36',
                         '--apk', str(apk), '--output-dir', str(destination)]
            with patch('sys.argv', arguments), patch.object(runner.subprocess, 'run', side_effect=fake_run), contextlib.redirect_stdout(io.StringIO()):
                try:
                    status = runner.main()
                except (subprocess.SubprocessError, OSError, KeyboardInterrupt):
                    self.fail('Cleanup failure must not prevent a fail-closed receipt')
            receipt = json.loads((destination / 'receipt.json').read_text(encoding='utf-8'))
            self.assertEqual(1, status)
            self.assertEqual('FAILED', receipt['status'])
            self.assertNotIn('PRIVATE_CANARY', json.dumps(receipt))
            self.assertNotIn('private instrumentation diagnostic', json.dumps(receipt))
            self.assertEqual(b'private instrumentation diagnostic\n',
                             (destination / 'instrumentation.private.log').read_bytes())
            return calls, receipt

    def test_nonzero_adb_exit_attempts_scoped_stop_before_failure_receipt(self):
        calls, receipt = self.exercise_failure(1)
        self.assertEqual(['shell', 'am', 'force-stop', 'com.monumentogram.dora.audio.test'], calls[-1])
        self.assertEqual('ADB_FAILED', receipt['host_process_outcome'])
        self.assertEqual('COMMAND_SUCCEEDED', receipt['test_package_stop'])

    def test_timeout_with_failed_stop_retains_content_free_failure_receipt(self):
        _, receipt = self.exercise_failure(
            subprocess.TimeoutExpired('PRIVATE_CANARY', 7200),
            subprocess.CalledProcessError(1, 'PRIVATE_CANARY', output='PRIVATE_CANARY'))
        self.assertEqual('TIMED_OUT_OR_INTERRUPTED', receipt['host_process_outcome'])
        self.assertEqual('UNCONFIRMED', receipt.get('test_package_stop'))

    def test_interrupt_with_unavailable_stop_retains_failure_receipt(self):
        _, receipt = self.exercise_failure(KeyboardInterrupt(), OSError('PRIVATE_CANARY'))
        self.assertEqual('UNCONFIRMED', receipt.get('test_package_stop'))

    def test_nonzero_adb_with_stop_timeout_is_reported_as_unconfirmed(self):
        _, receipt = self.exercise_failure(1, subprocess.TimeoutExpired('PRIVATE_CANARY', 120))
        self.assertEqual('ADB_FAILED', receipt['host_process_outcome'])
        self.assertEqual('UNCONFIRMED', receipt.get('test_package_stop'))


if __name__ == '__main__':
    unittest.main()
