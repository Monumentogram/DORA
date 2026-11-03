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
KEY_PIPELINE = ('com.monumentogram.dora.audio.persistence.keys.RunKeyReusePipelineTest#'
                'finalizationAndFreshReadsUseOneValidatedOpenAndFiveProviderCallsPerUnit')


def key_status(code, name=KEY_PIPELINE):
    cls, method = name.split('#')
    return [f'INSTRUMENTATION_STATUS: class={cls}', f'INSTRUMENTATION_STATUS: test={method}',
            f'INSTRUMENTATION_STATUS_CODE: {code}']


def key_measurement(operation='FINALIZE', values='units=4 opens=4 infos=4 ciphers=20 elapsedNs=123456789'):
    return [f'INSTRUMENTATION_STATUS: runKeyPipeline=operation={operation} {values}',
            'INSTRUMENTATION_STATUS_CODE: 3']


class RunKeyPipelineTelemetryTests(unittest.TestCase):
    def started(self, name=KEY_PIPELINE):
        state = diag.Progress({name})
        for line in key_status(1, name):
            state.feed(line)
        return state

    def test_three_measurements_survive_real_stream_and_exact_completion_parser(self):
        import run_encrypted_persistence_device as device
        lines = key_status(1)
        for operation in ('FINALIZE', 'AUTHENTICATED_OPEN', 'AUTHENTICATED_OPEN'):
            lines += key_measurement(operation)
        lines += key_status(0) + ['OK (1 test)', 'INSTRUMENTATION_CODE: -1']
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / 'measurement.json'
            try:
                output = diag.stream([sys.executable, '-c', 'print(' + repr('\n'.join(lines)) + ')'],
                                     {KEY_PIPELINE}, path, hard_timeout=3, echo=False)
            except diag.InstrumentationFailure as error:
                self.fail('Bounded progress must not become anonymous completion: ' + str(error))
            self.assertEqual([{'name': KEY_PIPELINE, 'result': 'PASS'}], device.parse_results(output))
            receipt = json.loads(path.read_text())
            self.assertEqual(1, receipt['completedCount'])
            self.assertEqual([KEY_PIPELINE], receipt['completedTests'])
            self.assertEqual([
                dict(operation=operation, units=4, opens=4, infos=4, ciphers=20, elapsedNs=123456789)
                for operation in ('FINALIZE', 'AUTHENTICATED_OPEN', 'AUTHENTICATED_OPEN')
            ], receipt['runKeyPipelineMeasurements'])

    def test_measurement_commits_only_with_progress_status_and_never_completes_test(self):
        state = self.started()
        state.feed(key_measurement()[0])
        self.assertEqual([], state.snapshot()['runKeyPipelineMeasurements'])
        self.assertEqual([], state.completed)
        with self.assertRaises(ValueError):
            state.require_passed()
        state.feed('INSTRUMENTATION_STATUS_CODE: 3')
        self.assertEqual(1, len(state.snapshot()['runKeyPipelineMeasurements']))
        self.assertEqual([], state.completed)
        with self.assertRaises(ValueError):
            state.require_passed()

    def test_measurements_require_exact_active_method_and_phase_order(self):
        other = KEY_PIPELINE.split('#')[0] + '#providerInvalidationAfterConfirmationStillRejectsBothReusedEnvelopeDecrypts'
        finished = self.started()
        for line in key_status(0):
            finished.feed(line)
        for state in (diag.Progress({KEY_PIPELINE}), self.started(other), finished):
            with self.assertRaises(ValueError):
                state.feed(key_measurement()[0])
        state = self.started()
        with self.assertRaises(ValueError):
            state.feed(key_measurement('AUTHENTICATED_OPEN')[0])
        for operation in ('FINALIZE', 'AUTHENTICATED_OPEN', 'AUTHENTICATED_OPEN'):
            for line in key_measurement(operation):
                state.feed(line)
        with self.assertRaises(ValueError):
            state.feed(key_measurement('AUTHENTICATED_OPEN')[0])

    def test_malformed_unbounded_or_content_bearing_measurements_are_rejected(self):
        values = 'units=4 opens=4 infos=4 ciphers=20 elapsedNs=123456789'
        for bad in (values.replace('units=4', 'units=5'), values.replace('opens=4', 'opens=-1'),
                    values.replace('infos=4', 'infos=1000'), values.replace('ciphers=20', 'ciphers=1000'),
                    values.replace('elapsedNs=123456789', 'elapsedNs=1800000000001'),
                    values + ' alias=private', values.replace('opens=4', 'opens=private')):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                self.started().feed(key_measurement(values=bad)[0])
        with self.assertRaises(ValueError):
            self.started().feed(key_measurement('PRIVATE')[0])

    def test_old_anonymous_success_and_unknown_status_remain_rejected(self):
        state = self.started()
        state.feed('INSTRUMENTATION_STATUS: stream=RUN_KEY_PIPELINE FINALIZE units=4 opens=4 infos=4 ciphers=20 elapsedNs=1')
        with self.assertRaisesRegex(ValueError, 'INCOMPLETE_TEST_IDENTITY'):
            state.feed('INSTRUMENTATION_STATUS_CODE: 0')
        for code in (0, 1, 2, 4, -2, -3, -4):
            state = self.started()
            state.feed(key_measurement()[0])
            with self.subTest(code=code), self.assertRaises(ValueError):
                state.feed(f'INSTRUMENTATION_STATUS_CODE: {code}')
        with self.assertRaises(ValueError):
            self.started().feed('INSTRUMENTATION_STATUS_CODE: 3')

    def test_progress_cannot_hide_failure_skip_missing_or_duplicate_completion(self):
        import run_encrypted_persistence_device as device
        start = key_status(1) + key_measurement()
        terminal = ['OK (1 test)', 'INSTRUMENTATION_CODE: -1']
        cases = [start + key_status(code) + terminal for code in (-1, -2, -3, -4)]
        cases += [start + terminal, start + key_status(0) * 2 + terminal,
                  start + key_status(0) + terminal + key_measurement()[:1]]
        for lines in cases:
            with self.subTest(lines=lines), self.assertRaises(ValueError):
                device.parse_results('\n'.join(lines))


class StreamDiagnosticsTests(unittest.TestCase):
    def test_stage9_checkpoint_never_counts_as_test_completion(self):
        name = 'com.monumentogram.dora.audio.persistence.AlphaRecordingRuntimeTest#interruptedAtExactLimitReopensPrefixWithoutResumeOrFalseFinalization'
        state = diag.Progress({name})
        state.feed('INSTRUMENTATION_STATUS: class=' + name.split('#')[0])
        state.feed('INSTRUMENTATION_STATUS: test=' + name.split('#')[1])
        state.feed('INSTRUMENTATION_STATUS_CODE: 1')
        state.feed('INSTRUMENTATION_STATUS: stage9Progress=phase=APPEND acceptedUnits=30 durableFrames=2400000 elapsedMs=9000')
        self.assertIsNone(state.snapshot().get('stage9Checkpoint'))
        state.feed('INSTRUMENTATION_STATUS_CODE: 3')
        self.assertEqual({'phase': 'APPEND', 'acceptedUnits': 30, 'durableFrames': 2400000,
                          'elapsedMs': 9000}, state.snapshot()['stage9Checkpoint'])
        self.assertEqual(0, state.snapshot()['completedCount'])
        self.assertEqual(name, state.snapshot()['lastStartedTest'])
        for bad in ('phase=PRIVATE acceptedUnits=30 durableFrames=2400000 elapsedMs=9',
                    'phase=APPEND acceptedUnits=363 durableFrames=2400000 elapsedMs=9',
                    'phase=APPEND acceptedUnits=30 durableFrames=28800801 elapsedMs=9',
                    'phase=APPEND acceptedUnits=30 durableFrames=2400000 elapsedMs=-1'):
            with self.assertRaises(ValueError):
                state.feed('INSTRUMENTATION_STATUS: stage9Progress=' + bad)
        with self.assertRaises(ValueError):
            diag.Progress(NAMES).feed('INSTRUMENTATION_STATUS: stage9Progress=phase=APPEND acceptedUnits=30 durableFrames=2400000 elapsedMs=9')

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


    def test_api28_budget_does_not_extend_api36_or_instrumentation(self):
        import poco_remediation_ci_profile as profile
        original = profile.BUDGET_BASE+'\n'+profile.ANCHOR
        changed = profile.upgrade(original).replace(profile.BUDGET_BASE, profile.BUDGET_APPROVED)
        self.assertEqual(original, profile.normalize(changed))
        for broken in [changed.replace('28 && 60 || 45', '28 && 61 || 45'),
                       changed.replace('28 && 60 || 45', '28 && 60 || 60'),
                       changed.replace('${{ matrix.api == 28 && 60 || 45 }}', '60')]:
            with self.assertRaises(ValueError): profile.normalize(broken)


if __name__ == '__main__':
    unittest.main()
