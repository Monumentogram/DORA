"""Host state-machine regressions. Synthetic transport is never physical evidence."""
import importlib.util
from pathlib import Path
import unittest
import os
import tempfile
import threading
import json
import ctypes

spec = importlib.util.spec_from_file_location('alpha_operator', Path(__file__).parents[1] / 'poco_alpha_acceptance.py')
operator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(operator)


class HostSafetyTest(unittest.TestCase):
    def test_terminal_publication_between_receipt_and_process_exit_is_retained(self):
        from types import SimpleNamespace
        state = {'phase': 'ATTEMPT_COMPLETE'}
        def poll():
            state['phase'] = 'COMPLETE'
            return 0
        device = SimpleNamespace(receipt=lambda run: dict(state))
        process = SimpleNamespace(poll=poll)
        self.assertEqual(operator.instrumentation_progress(device, process, 'test-run')['phase'], 'COMPLETE')

    def test_exited_process_without_terminal_still_fails_closed(self):
        from types import SimpleNamespace
        device = SimpleNamespace(receipt=lambda run: {'phase': 'RECORDING'})
        with self.assertRaisesRegex(RuntimeError, 'INSTRUMENTATION_EXITED_WITHOUT_TERMINAL'):
            operator.instrumentation_progress(device, SimpleNamespace(poll=lambda: 1), 'test-run')

    def test_planned_recovery_kill_never_targets_an_unmatched_process(self):
        device = operator.Device({}); commands = []
        device.text = lambda *args, **kwargs: '222'
        device.call = lambda *args, **kwargs: commands.append(args)
        row = dict(mode='recovery-seed', run='lite-functional-recovery-seed-01',
                   phase='RECOVERY_KILL_READY', pid=111, durableFramesBeforeKill=64000,
                   activeCampaignIdentity='a' * 64, invocationTokenFingerprint='b' * 64)
        with self.assertRaises(ValueError):
            device.kill_owned_for_recovery(row)
        self.assertEqual(commands, [])

    @unittest.skipUnless(os.name == 'nt', 'Windows sharing semantics')
    def test_atomic_receipt_replacement_survives_short_windows_reader_lock(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / 'receipt.json'
            operator.write_json(path, {'old': True})
            kernel = ctypes.WinDLL('kernel32', use_last_error=True)
            kernel.CreateFileW.restype = ctypes.c_void_p
            kernel.CreateFileW.argtypes = [ctypes.c_wchar_p, ctypes.c_ulong, ctypes.c_ulong,
                                           ctypes.c_void_p, ctypes.c_ulong, ctypes.c_ulong, ctypes.c_void_p]
            kernel.CloseHandle.argtypes = [ctypes.c_void_p]
            handle = kernel.CreateFileW(str(path), 0x80000000, 1, None, 3, 0, None)
            self.assertNotEqual(handle, ctypes.c_void_p(-1).value)
            timer = threading.Timer(.15, lambda: kernel.CloseHandle(handle)); timer.start()
            try:
                operator.write_json(path, {'new': True})
            finally:
                timer.join()
            self.assertEqual(json.loads(path.read_text()), {'new': True})

    def test_main_runs_require_matching_sealed_apk_and_fixed_run_id(self):
        from alpha_protocol import seal
        protocol = {'productApkSha256': 'a' * 64, 'helperApkSha256': 'b' * 64,
                    'ownerMapSha256': 'c' * 64,
                    'runs': {'lite-cycles-01': {'mode': 'lite-cycles', 'attempts': 60,
                                             'hardDeadlineSeconds': 14400}}}
        config = {k: protocol[k] for k in ('productApkSha256', 'helperApkSha256', 'ownerMapSha256')}
        config.update(protocol=protocol, protocolSha256=seal(protocol))
        self.assertEqual(operator.admit_run(config, 'lite-cycles-01', 'lite-cycles')['attempts'], 60)
        for run, mode in [('lite-cycles-02', 'lite-cycles'), ('lite-cycles-01', 'lite-long')]:
            with self.assertRaises(ValueError):
                operator.admit_run(config, run, mode)
        config['productApkSha256'] = 'd' * 64
        with self.assertRaises(ValueError):
            operator.admit_run(config, 'lite-cycles-01', 'lite-cycles')

    def device(self, watchdog=None, receipt=None):
        device = operator.Device({})
        device.safe_state = lambda: {'fgsAbsent': True, 'micNotRunning': True}
        device.receipt = lambda run: watchdog if run.endswith('-watchdog') else receipt
        return device

    def test_mic_absence_during_pending_start_is_not_safe_shutdown(self):
        device = self.device(receipt={'phase': 'START_ATTEMPT_COMMITTED', 'activeAttempt': {}})
        self.assertFalse(all(device.confirmed_shutdown('test-run').values()))

    def test_abort_request_is_not_proof_of_stop(self):
        device = self.device(watchdog={'state': 'ABORT_REQUESTED', 'pendingStartCancelled': True})
        self.assertFalse(all(device.confirmed_shutdown('test-run').values()))

    def test_only_confirmed_cancelled_start_can_finish_shutdown(self):
        for state in ('STOP_CONFIRMED', 'CLOSED_SAFE'):
            device = self.device(watchdog={'state': state, 'pendingStartCancelled': True})
            self.assertTrue(all(device.confirmed_shutdown('test-run').values()))
            device = self.device(watchdog={'state': state, 'pendingStartCancelled': False})
            self.assertFalse(all(device.confirmed_shutdown('test-run').values()))

    def test_failure_before_any_start_is_distinct_from_pending_attempt(self):
        device = self.device(receipt={'phase': 'FAILED'})
        self.assertTrue(all(device.confirmed_shutdown('test-run').values()))
        device = self.device(receipt={'phase': 'FAILED', 'invocationTokenFingerprint': 'a' * 64})
        self.assertFalse(all(device.confirmed_shutdown('test-run').values()))

    def test_secure_screen_never_receives_dismiss_or_wake_commands(self):
        device = operator.Device({})
        device.text = lambda *args, **kwargs: '  secure=true\n  showing=true\n'
        commands = []
        device.call = lambda *args, **kwargs: commands.append(args)
        with self.assertRaises(ValueError):
            device.ready_screen()
        self.assertEqual(commands, [])

    def test_nonsecure_screen_uses_standard_dismiss_and_verifies_result(self):
        device = operator.Device({})
        states = iter(['  secure=false\n  showing=true\n', '  showing=false\n'])
        device.text = lambda *args, **kwargs: next(states)
        commands = []
        device.call = lambda *args, **kwargs: commands.append(args)
        self.assertFalse(device.ready_screen()['securitySettingsChanged'])
        self.assertEqual(commands, [('shell', 'input', 'keyevent', '224'), ('shell', 'wm', 'dismiss-keyguard')])

    def test_heartbeat_uses_bounded_shell_without_stdin_stream(self):
        device = operator.Device({}); calls = []
        device.call = lambda *args, **kwargs: calls.append((args, kwargs))
        device.signal('lite-screen-smoke-01', 'heartbeat', '123')
        args, kwargs = calls[0]
        self.assertEqual(args[0], 'shell')
        self.assertNotIn('input', kwargs)
        self.assertLessEqual(kwargs['timeout'], 5)
        for unsafe in ('1;echo secret', '', '0\n1'):
            with self.assertRaises(ValueError):
                device.signal('lite-screen-smoke-01', 'heartbeat', unsafe)


if __name__ == '__main__':
    unittest.main()
