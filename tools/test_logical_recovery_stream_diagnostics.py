"""Content-free streaming is observable before an Android command finishes."""
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest import mock

SPEC = importlib.util.find_spec('persistence_instrumentation_diagnostics')
if SPEC:
    import persistence_instrumentation_diagnostics as diag

NAMES = {'example.First#one', 'example.Second#two'}


class StreamDiagnosticsTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(SPEC, 'Streaming diagnostics module is not implemented')

    def test_start_and_completion_are_distinct(self):
        state = diag.Progress(NAMES)
        for line in ['INSTRUMENTATION_STATUS: class=example.First', 'INSTRUMENTATION_STATUS: test=one', 'INSTRUMENTATION_STATUS_CODE: 1']:
            state.feed(line)
        self.assertEqual('example.First#one', state.snapshot()['lastStartedTest'])
        self.assertIsNone(state.snapshot()['lastCompletedTest'])
        for line in ['INSTRUMENTATION_STATUS: class=example.First', 'INSTRUMENTATION_STATUS: test=one', 'INSTRUMENTATION_STATUS_CODE: 0']:
            state.feed(line)
        self.assertEqual(1, state.snapshot()['completedCount'])
        self.assertEqual('example.First#one', state.snapshot()['lastCompletedTest'])

    def test_unknown_identity_and_duplicate_completion_rejected(self):
        state = diag.Progress(NAMES)
        with self.assertRaises(ValueError):
            for line in ['INSTRUMENTATION_STATUS: class=private.Account', 'INSTRUMENTATION_STATUS: test=secret', 'INSTRUMENTATION_STATUS_CODE: 1']:
                state.feed(line)
        state = diag.Progress(NAMES)
        for repeat in range(2):
            if repeat:
                state.feed('INSTRUMENTATION_STATUS: class=example.First')
                state.feed('INSTRUMENTATION_STATUS: test=one')
                with self.assertRaisesRegex(ValueError, 'DUPLICATE_COMPLETION'):
                    state.feed('INSTRUMENTATION_STATUS_CODE: 0')
            else:
                state.feed('INSTRUMENTATION_STATUS: class=example.First')
                state.feed('INSTRUMENTATION_STATUS: test=one')
                state.feed('INSTRUMENTATION_STATUS_CODE: 0')

    def test_partition_is_exact_deterministic_and_not_discovered(self):
        self.assertEqual([['example.First#one'], ['example.Second#two']], diag.class_batches(NAMES))
        with self.assertRaises(ValueError):
            diag.require_complete(['example.First#one'], NAMES)
        with self.assertRaises(ValueError):
            diag.require_complete(['example.First#one', 'example.Second#two', 'example.First#one'], NAMES)
        diag.require_complete(sorted(NAMES), NAMES)

    def test_timeout_retains_start_and_does_not_expose_arguments(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp)/'partial.json'
            script = "import time;print('INSTRUMENTATION_STATUS: class=example.First',flush=True);print('INSTRUMENTATION_STATUS: test=one',flush=True);print('INSTRUMENTATION_STATUS_CODE: 1',flush=True);print('PIN=837291 private text',flush=True);time.sleep(5)"
            with self.assertRaises(diag.InstrumentationFailure) as caught:
                diag.stream([sys.executable, '-u', '-c', script], NAMES, path,
                            hard_timeout=.4, heartbeat=.1, inactivity=.15,
                            probe=lambda: {'emulatorOnline': True, 'instrumentationPidAlive': True},
                            capture=lambda: {'capture': 'SAFE'}, echo=False)
            receipt = json.loads(path.read_text())
            self.assertEqual('HARD_TIMEOUT', receipt['outcome'])
            self.assertEqual('example.First#one', receipt['lastStartedTest'])
            self.assertIsNone(receipt['lastCompletedTest'])
            self.assertTrue(receipt['stallCaptures'])
            for p in Path(tmp).iterdir():
                self.assertNotIn('837291', p.read_text())
                self.assertNotIn('private text', p.read_text())
            self.assertNotIn('837291', str(caught.exception))

    def test_credential_cleanup_executes_after_timeout(self):
        calls = []
        def command(*args):
            calls.append(args)
            return 'Pin set to redacted' if 'set-pin' in args else 'Lock credential cleared'
        with self.assertRaises(diag.InstrumentationFailure):
            with diag.synthetic_credential(command, '837291'):
                raise diag.InstrumentationFailure('HARD_TIMEOUT')
        self.assertIn(('shell','locksettings','clear','--old','837291'), calls)

    def test_cleanup_failure_is_sanitized_and_not_hidden(self):
        def command(*args):
            if 'set-pin' in args: return 'Pin set to redacted'
            raise subprocess.CalledProcessError(1, ['locksettings','837291'])
        with self.assertRaisesRegex(diag.InstrumentationFailure, 'CREDENTIAL_CLEANUP_FAILED') as caught:
            with diag.synthetic_credential(command, '837291'): pass
        self.assertNotIn('837291', str(caught.exception))

    def test_uncertain_setup_still_attempts_exact_new_credential_cleanup(self):
        calls=[]
        def command(*args):
            calls.append(args)
            if 'set-pin' in args:
                raise subprocess.TimeoutExpired(['set-pin','837291'], 1)
            return 'Lock credential cleared'
        with self.assertRaisesRegex(diag.InstrumentationFailure, 'CREDENTIAL_SETUP_FAILED'):
            with diag.synthetic_credential(command,'837291'): pass
        self.assertIn(('shell','locksettings','clear','--old','837291'),calls)

    def test_reap_timeout_is_redacted_and_terminal_receipt_survives(self):
        original=subprocess.Popen
        launched=[]
        def popen(*args,**kwargs):
            process=original(*args,**kwargs);launched.append(process)
            process.real_wait=process.wait
            def wait(timeout=None):
                if timeout==5:
                    raise subprocess.TimeoutExpired(['syntheticAuthPin','837291'],5)
                return process.real_wait(timeout)
            process.wait=wait
            return process
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(diag.subprocess,'Popen',side_effect=popen):
            path=Path(tmp)/'partial.json'
            try:
                with self.assertRaises(diag.InstrumentationFailure) as caught:
                    diag.stream([sys.executable,'-c','import time;time.sleep(3)'],NAMES,path,hard_timeout=.2,echo=False)
                self.assertNotIn('837291',str(caught.exception))
                self.assertEqual('PROCESS_REAP_FAILED',json.loads(path.read_text())['cleanupFailure'])
            finally:
                for process in launched:process.real_wait(timeout=5)

    def test_success_retains_only_allowlisted_output(self):
        with tempfile.TemporaryDirectory() as tmp:
            path=Path(tmp)/'done.json'
            lines=['arbitrary private log', 'INSTRUMENTATION_STATUS: class=example.First', 'INSTRUMENTATION_STATUS: test=one', 'INSTRUMENTATION_STATUS_CODE: 0', 'OK (1 test)', 'INSTRUMENTATION_CODE: -1']
            output=diag.stream([sys.executable,'-c','print('+repr('\n'.join(lines))+')'],NAMES,path,hard_timeout=3,echo=False)
            self.assertNotIn('arbitrary private log',output)
            self.assertIn('OK (1 test)',output)
            self.assertEqual('PROCESS_EXITED',json.loads(path.read_text())['outcome'])

    def test_failed_kill_cannot_block_pipe_cleanup(self):
        original = subprocess.Popen
        launched = []
        def popen(*args, **kwargs):
            process = original(*args, **kwargs)
            launched.append(process)
            process.real_kill = process.kill
            process.kill = mock.Mock(side_effect=OSError('synthetic kill failure'))
            return process
        with tempfile.TemporaryDirectory() as tmp, mock.patch.object(diag.subprocess, 'Popen', side_effect=popen):
            path = Path(tmp)/'partial.json'
            try:
                started = time.monotonic()
                with self.assertRaises(diag.InstrumentationFailure):
                    diag.stream([sys.executable, '-c', 'import time;time.sleep(8)'], NAMES,
                                path, hard_timeout=.1, echo=False)
                self.assertLess(time.monotonic()-started, 4)
                self.assertEqual('PROCESS_REAP_FAILED', json.loads(path.read_text())['cleanupFailure'])
            finally:
                for process in launched:
                    process.real_kill()
                    process.wait(timeout=5)
                    time.sleep(.1)
                    process.stdout.close()
                    process.stderr.close()

    def test_diagnostic_upload_is_exactly_additive(self):
        spec=importlib.util.find_spec('poco_remediation_ci_profile')
        self.assertIsNotNone(spec,'CI diagnostic preservation profile missing')
        import poco_remediation_ci_profile as profile
        original='prefix\n'+profile.ANCHOR+'suffix\n'
        changed=profile.upgrade(original)
        self.assertEqual(original,profile.normalize(changed))
        for broken in [changed.replace('if: always()', 'if: success()'),changed+profile.UPLOAD]:
            with self.assertRaises(ValueError):profile.normalize(broken)

    def test_successor_override_cannot_change_historical_workflow(self):
        import poco_remediation_ci_profile as profile
        from validate_poco_acceptance import validate_remediation_override, REMEDIATION_ROUTE
        original = 'timeout: 1800\n'+profile.ANCHOR+'full exact inventory\n'
        changed = profile.upgrade(original)
        validate_remediation_override('.github/workflows/android-ci.yml', changed.encode(), original.encode())
        with self.assertRaises(ValueError):
            validate_remediation_override('.github/workflows/android-ci.yml', changed.replace('1800','3600').encode(), original.encode())
        original = 'def normalize(workflow):\n    return workflow\n'
        changed = original.replace('    return', REMEDIATION_ROUTE+'    return')
        validate_remediation_override('tools/logical_recovery_ci_profile.py', changed.encode(), original.encode())
        with self.assertRaises(ValueError):
            validate_remediation_override('tools/logical_recovery_ci_profile.py', (changed+'# hidden edit').encode(), original.encode())


if __name__ == '__main__':
    unittest.main()
