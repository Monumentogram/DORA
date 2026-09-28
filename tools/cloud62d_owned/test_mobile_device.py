"""Private USB transfer regression tests; no device or actual corpus required."""
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from tools.cloud62d_owned.mobile_device import Device, PACKAGE, digest


class DeviceTests(unittest.TestCase):
    def test_usb_retry_preserves_interrupted_attempt_and_publishes_complete_bytes(self):
        device = Device('adb', 'synthetic-device')
        remote = {}
        interrupted = True
        def private(*args, data=None, allow_failure=False):
            nonlocal interrupted
            if args[0] == 'mkdir':
                return subprocess.CompletedProcess([], 0, b'')
            if args[0] == 'cat':
                return subprocess.CompletedProcess([], 0 if args[1] in remote else 1,
                                                   remote.get(args[1], b''))
            if args[0] == 'tee':
                if interrupted:
                    interrupted = False
                    remote[args[1]] = data[:3]
                    raise OSError('synthetic USB interruption')
                remote[args[1]] = data
            if args[0] == 'mv':
                remote.setdefault(args[3], remote[args[2]])
            return subprocess.CompletedProcess([], 0, b'')
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'seed.zip'
            source.write_bytes(b'synthetic seed')
            with patch.object(device, 'private', side_effect=private):
                with self.assertRaises(OSError):
                    device.put(source, 'seed.zip')
                device.put(source, 'seed.zip')
            self.assertEqual(remote['files/inbox/seed.zip'], source.read_bytes())
            self.assertIn(b'syn', remote.values())

    def test_collect_retry_after_disk_failure_never_publishes_partial(self):
        device = Device('adb', 'synthetic-device')
        data = b'synthetic export'
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with patch.object(device, 'private', return_value=subprocess.CompletedProcess([], 0, data)):
                with patch('tools.cloud62d_owned.mobile_device.os.fsync', side_effect=OSError('disk fault')):
                    with self.assertRaises(OSError):
                        device.collect(root)
                self.assertFalse((root / (digest(data) + '.zip')).exists())
                self.assertEqual(device.collect(root), digest(data))
                self.assertEqual((root / (digest(data) + '.zip')).read_bytes(), data)
                self.assertEqual(len(list(root.glob('*.partial'))), 1)

    def test_existing_private_input_is_never_overwritten(self):
        with tempfile.TemporaryDirectory() as directory:
            source = Path(directory) / 'seed.zip'
            source.write_bytes(b'new synthetic bytes')
            device = Device('adb', 'synthetic-device')
            with patch.object(device, 'private') as private:
                private.side_effect = [subprocess.CompletedProcess([], 0, b''),
                                       subprocess.CompletedProcess([], 0, b'old synthetic bytes')]
                with self.assertRaisesRegex(ValueError, 'EXISTING_INPUT_CONFLICT_PRESERVED'):
                    device.put(source, 'seed.zip')
                self.assertEqual(private.call_count, 2)

    def test_transfer_namespace_and_apk_binding(self):
        device = Device('adb', 'synthetic-device')
        with patch.object(device, 'call') as call:
            call.return_value = subprocess.CompletedProcess([], 0, b'PK\x00\n\xff')
            device.private('cat', 'files/export/mobile-export.zip')
            self.assertEqual(call.call_args_list[0].args[0][:6],
                             ['shell', '-T', 'run-as', PACKAGE, 'test', '-f'])
            self.assertEqual(call.call_args.args[0][:3], ['exec-out', 'run-as', PACKAGE])
        with patch.object(device, 'call', return_value=subprocess.CompletedProcess([], 1, b'')) as call:
            self.assertEqual(device.private('cat', 'files/missing', allow_failure=True).returncode, 1)
            self.assertEqual(call.call_count, 1)
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / 'test.apk'
            apk.write_bytes(b'synthetic apk')
            with patch.object(device, 'call') as call:
                with self.assertRaisesRegex(ValueError, 'APK_HASH_MISMATCH'):
                    device.install(apk, '0' * 64)
                call.assert_not_called()
                call.return_value = subprocess.CompletedProcess([], 0, b'Success')
                device.install(apk, digest(apk.read_bytes()))
                self.assertNotIn('-g', call.call_args.args[0])


if __name__ == '__main__':
    unittest.main()
