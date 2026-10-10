"""C3 runner guards and interrupted-run receipts with synthetic subprocess edges."""
import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


class C3RunnerTests(unittest.TestCase):
    def runner(self):
        self.assertIsNotNone(importlib.util.find_spec('run_persistence_optimization_c3'),
                             'C3 guarded runner is not implemented')
        import run_persistence_optimization_c3
        return run_persistence_optimization_c3

    def test_only_explicit_emulator_serial_and_manifest_package_are_admitted(self):
        runner = self.runner()
        for serial in ('', 'POCO123', '192.0.2.1:5555', 'emulator-5580 shell id'):
            with self.assertRaises(ValueError): runner.validate_serial(serial)
        runner.validate_serial('emulator-5580')
        runner.verify_package("package: name='com.monumentogram.dora.audio.test' versionCode='1'")
        for manifest in ("package: name='com.monumentogram.dora'", '',
                         "package: name='com.monumentogram.dora.audio.test.evil'"):
            with self.assertRaises(ValueError): runner.verify_package(manifest)

    def test_qemu_hardware_and_expected_api_all_required(self):
        runner = self.runner()
        properties = {'ro.kernel.qemu':'1','ro.hardware':'ranchu','ro.build.version.sdk':'36'}
        runner.verify_emulator(lambda *p: properties[p[-1]],36)
        for key, value in [('ro.kernel.qemu','0'),('ro.hardware','physical'),('ro.build.version.sdk','28')]:
            changed = dict(properties); changed[key] = value
            with self.assertRaises(ValueError): runner.verify_emulator(lambda *p: changed[p[-1]],36)

    def test_timeout_keeps_raw_and_stops_only_verified_test_package(self):
        self.failure_case(subprocess.TimeoutExpired('PRIVATE_CANARY',1), OSError('PRIVATE_CANARY'))

    def test_adb_failure_keeps_raw_and_records_successful_scoped_stop(self):
        self.failure_case(1)

    def failure_case(self, outcome, stop_error=None):
        runner = self.runner()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); apk = root/'synthetic.apk'; apk.write_bytes(b'synthetic only')
            out = root/'new-output'; calls = []
            digest = hashlib.sha256(apk.read_bytes()).hexdigest()
            def execute(command, **kw):
                calls.append(command)
                if command[0] == 'synthetic-aapt':
                    return subprocess.CompletedProcess(command,0,stdout="package: name='com.monumentogram.dora.audio.test'")
                self.assertEqual(['synthetic-adb','-s','emulator-5580'],command[:3])
                parts = command[3:]
                if parts[:2] == ['shell','getprop']:
                    return subprocess.CompletedProcess(command,0,stdout={
                        'ro.kernel.qemu':'1','ro.hardware':'ranchu','ro.build.version.sdk':'36'}[parts[-1]])
                if parts[:2] == ['install','-r']:
                    return subprocess.CompletedProcess(command,0,stdout='Success')
                if parts[:3] == ['shell','am','instrument']:
                    self.assertIn('persistenceOptimizationC3',parts)
                    self.assertEqual(runner.PACKAGE+'/androidx.test.runner.AndroidJUnitRunner',parts[-1])
                    kw['stdout'].write(b'PRIVATE_CANARY\n')
                    if isinstance(outcome,BaseException): raise outcome
                    return subprocess.CompletedProcess(command,outcome)
                if parts == ['shell','am','force-stop',runner.PACKAGE]:
                    if stop_error: raise stop_error
                    return subprocess.CompletedProcess(command,0,stdout='')
                self.fail('Unexpected operation')
            argv = ['runner','--adb','synthetic-adb','--aapt','synthetic-aapt','--serial','emulator-5580',
                    '--expected-api','36','--apk',str(apk),'--apk-sha256',digest,'--variant','baseline',
                    '--source-sha256','a'*64,'--output-dir',str(out)]
            with patch('sys.argv',argv), patch.object(runner.subprocess,'run',side_effect=execute), contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(1,runner.main())
            receipt = json.loads((out/'receipt.json').read_text())
            self.assertEqual('FAILED',receipt['status'])
            self.assertEqual('UNCONFIRMED' if stop_error else 'COMMAND_SUCCEEDED',receipt['test_package_stop'])
            self.assertNotIn('PRIVATE_CANARY',json.dumps(receipt))
            self.assertEqual(b'PRIVATE_CANARY\n',(out/'instrumentation.private.log').read_bytes())
            self.assertEqual(['shell','am','force-stop',runner.PACKAGE],calls[-1][3:])


if __name__ == '__main__':
    unittest.main()
