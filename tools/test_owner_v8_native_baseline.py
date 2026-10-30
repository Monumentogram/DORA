"""Fail-closed artifact, visible-control and crash classification checks; no device calls."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import run_owner_v8_native_baseline as baseline


def tree(label, *, package='com.monumentogram.dora.alpha.preview', attributes='', bounds='[20,40][220,140]'):
    return ('<hierarchy><node package="' + package + '" enabled="true" clickable="true" '
            'bounds="' + bounds + '" ' + attributes + '><node package="' + package + '" enabled="true" '
            'clickable="false" bounds="' + bounds + '" text="' + label + '"/></node></hierarchy>')


class PublicV8BaselineTests(unittest.TestCase):
    def test_actual_runner_flow_records_only_diagnostics_and_always_stops_owned_install(self):
        for observation in ('crash', 'alive', 'exit', 'ui-failure', 'launch-error', 'launch-empty', 'launch-duplicate'):
            with self.subTest(observation=observation), tempfile.TemporaryDirectory() as temporary:
                apk, receipt = Path(temporary) / 'public.apk', Path(temporary) / 'result.json'
                apk.write_bytes(b'public-fixture')
                apk_hash = hashlib.sha256(apk.read_bytes()).hexdigest()
                calls, taps = [], []
                labels = ['New recording', 'I have informed the participants about recording',
                          'Allow notifications', 'Allow', 'Start recording']

                def command(arguments, **kwargs):
                    calls.append(arguments)
                    if 'badging' in arguments:
                        return "package: name='com.monumentogram.dora.alpha.preview' versionCode='8' versionName='0.1.0-owner-preview.8'"
                    if 'verify' in arguments:
                        return 'Signer #1 certificate SHA-256 digest: ' + baseline.CERT_SHA256
                    tail = arguments[3:]
                    outputs = {('shell', 'getprop', 'ro.kernel.qemu'): '1',
                               ('shell', 'getprop', 'ro.build.version.sdk'): '36',
                               ('shell', 'getprop', 'ro.product.cpu.abi'): 'x86_64',
                               ('shell', 'getprop', 'ro.product.cpu.abilist'): 'x86_64,arm64-v8a',
                               ('shell', 'getprop', 'ro.build.fingerprint'): 'google/emulator/test:16/BUILD/1:userdebug/dev-keys',
                               ('shell', 'getprop', 'ro.dalvik.vm.native.bridge'): 'libndk_translation.so',
                               ('shell', 'getconf', 'PAGE_SIZE'): '4096',
                               ('shell', 'cat', '/proc/cpuinfo'): 'processor : 0\nprocessor : 1\n',
                               ('shell', 'cat', '/proc/meminfo'): 'MemTotal: 2048000 kB\n',
                               ('shell', 'pm', 'list', 'packages', '-u', baseline.PACKAGE): '',
                               ('shell', 'pm', 'path', baseline.PACKAGE): 'package:/data/app/fixture/base.apk',
                               ('shell', 'sha256sum', '/data/app/fixture/base.apk'): apk_hash + '  /data/app/fixture/base.apk',
                               ('shell', 'dumpsys', 'package', baseline.PACKAGE): 'versionCode=8\nversionName=0.1.0-owner-preview.8',
                               ('shell', 'wm', 'size'): 'Physical size: 1080x2400'}
                    if tuple(tail) in outputs:
                        return outputs[tuple(tail)]
                    if tail[0] == 'install':
                        self.assertNotIn('-r', tail)
                        return 'Success'
                    if tail[:3] == ['shell', 'input', 'tap']:
                        taps.append(tuple(tail[3:]))
                        return ''
                    if tail[:3] == ['shell', 'am', 'start']:
                        return {'launch-error': 'Error type 3\nError: Activity class does not exist.',
                                'launch-empty': '', 'launch-duplicate': 'Status: ok\nStatus: ok'}.get(
                                    observation, 'Status: ok\nActivity: ' + baseline.PACKAGE + '/' + baseline.ACTIVITY)
                    if tail[:2] == ['exec-out', 'cat']:
                        if observation == 'ui-failure':
                            raise baseline.BaselineFailure('SYNTHETIC_UI_FAILURE')
                        package = baseline.PERMISSION_PACKAGE if len(taps) == 3 else baseline.PACKAGE
                        return tree(labels[len(taps)], package=package)
                    if tail[:4] == ['logcat', '-b', 'crash', '-d']:
                        return ('*** *** ***\nCmdline: ' + baseline.PACKAGE + '\n#00 libonnxruntime.so\n'
                                '#01 libsherpa-onnx-jni.so\nPRIVATE SECRET') if observation == 'crash' else ''
                    if len(tail) == 2 and tail[1].startswith('pidof '):
                        return '123' if observation == 'alive' else ''
                    if tail[:3] in (['shell', 'am', 'force-stop'], ['shell', 'input', 'keyevent'],
                                    ['shell', 'wm', 'dismiss-keyguard'],
                                    ['shell', 'uiautomator', 'dump'], ['shell', 'pm', 'grant'],
                                    ['shell', 'rm', '-f']) or tail == ['logcat', '-b', 'crash', '-c']:
                        return ''
                    raise AssertionError('Unexpected external command: ' + str(tail))

                with patch.object(baseline, 'APK_SHA256', apk_hash), \
                     patch.object(baseline, 'command', side_effect=command), patch.object(baseline.time, 'sleep'):
                    result = baseline.run_baseline(apk, Path(temporary), 'emulator-5554', receipt, observe_seconds=5)
                self.assertEqual('NOT_CLAIMED', result['recording_qualification'])
                self.assertEqual(4096, result['environment']['runtime_page_size'])
                self.assertEqual(2, result['environment']['guest_cpu_count'])
                self.assertEqual('NOT_CLAIMED', result['environment']['cross_host_equivalence'])
                self.assertTrue(result['owned_package_force_stopped'])
                wake = next(index for index, call in enumerate(calls) if call[-3:] == ['input', 'keyevent', '224'])
                dismiss = next(index for index, call in enumerate(calls) if call[-2:] == ['wm', 'dismiss-keyguard'])
                launch = next(index for index, call in enumerate(calls) if 'start' in call)
                self.assertLess(wake, dismiss)
                self.assertLess(dismiss, launch)
                self.assertTrue(result['wake_and_dismiss_sent'])
                self.assertTrue(any('packages' in call and '-u' in call for call in calls))
                self.assertEqual(1, sum(call[-3:] == ['am', 'force-stop', baseline.PACKAGE] for call in calls))
                self.assertNotIn('SECRET', receipt.read_text(encoding='utf-8'))
                if observation.startswith('launch-'):
                    self.assertEqual('DIAGNOSTIC_INCOMPLETE', result['status'])
                    self.assertEqual('ACTIVITY_LAUNCH_NOT_CONFIRMED', result['failure'])
                    self.assertEqual([], result['visible_actions'])
                elif observation == 'ui-failure':
                    self.assertEqual('DIAGNOSTIC_INCOMPLETE', result['status'])
                else:
                    self.assertEqual('start', result['last_ui_diagnostic']['expected_action'])
                    self.assertEqual(5, len(taps))
                    self.assertEqual('DIAGNOSTIC_COMPLETE', result['status'])
                    self.assertEqual({'crash': 'DORA_ORT_VAD_CRASH_OBSERVED',
                                      'alive': 'NO_MATCHING_CRASH_OBSERVED',
                                      'exit': 'PROCESS_EXITED_WITHOUT_MATCHING_CRASH'}[observation],
                                     result['observation'])

    def test_exact_public_package_version_and_single_owner_signer_are_required(self):
        good = "package: name='com.monumentogram.dora.alpha.preview' versionCode='8' versionName='0.1.0-owner-preview.8'"
        cert = 'Signer #1 certificate SHA-256 digest: ' + baseline.CERT_SHA256
        baseline.audit_identity(good, cert)
        for badging, signer in ((good.replace("versionCode='8'", "versionCode='9'"), cert),
                                (good.replace('.preview', '.pilot'), cert),
                                (good.replace('preview.8', 'preview.9'), cert),
                                (good, cert.replace(baseline.CERT_SHA256, '0' * 64)),
                                (good, cert + '\n' + cert.replace('#1', '#2'))):
            with self.assertRaises(baseline.BaselineFailure):
                baseline.audit_identity(badging, signer)

    def test_visible_click_requires_unique_enabled_bounded_same_package_target(self):
        self.assertEqual((120, 90), baseline.click_target(tree('New recording'), ('New recording',),
                                                       baseline.PACKAGE, 1080, 2400))
        for xml in (tree('New recording', package='unrelated.app'),
                    tree('New recording').replace('enabled="true"', 'enabled="false"'),
                    tree('New recording', attributes='visible-to-user="false"'),
                    tree('New recording', bounds='[20,40][220,3000]'),
                    tree('New recording', bounds='[20,40][20,140]'),
                    '<hierarchy>' + tree('New recording')[11:-12] * 2 + '</hierarchy>'):
            with self.subTest(xml=xml), self.assertRaises(baseline.BaselineFailure):
                baseline.click_target(xml, ('New recording',), baseline.PACKAGE, 1080, 2400)

    def test_crash_requires_same_dora_block_with_both_ort_and_vad_frames_and_never_emits_raw_content(self):
        native = ('*** *** ***\nCmdline: com.monumentogram.dora.alpha.preview\n'
                  'signal 6 (SIGABRT)\nbacktrace:\n'
                  '#00 libonnxruntime.so onnxruntime::InferenceSession\n'
                  '#01 libsherpa-onnx-jni.so\nprivate transcript SECRET\n')
        summary = baseline.crash_summary(native)
        self.assertEqual('DORA_ORT_VAD_CRASH_OBSERVED', summary['observation'])
        self.assertNotIn('SECRET', json.dumps(summary))
        self.assertEqual('NO_MATCHING_CRASH_OBSERVED', baseline.crash_summary(
            native.replace(baseline.PACKAGE, 'unrelated.app'))['observation'])
        self.assertEqual('DORA_OTHER_CRASH_OBSERVED', baseline.crash_summary(
            native.replace('libonnxruntime.so onnxruntime::InferenceSession', 'libother.so'))['observation'])
        separate = ('*** *** ***\nCmdline: com.monumentogram.dora.alpha.preview\n#00 libother.so\n'
                    '*** *** ***\nCmdline: unrelated.app\n#00 libonnxruntime.so\n#01 libsherpa-onnx-jni.so\n')
        self.assertEqual('DORA_OTHER_CRASH_OBSERVED', baseline.crash_summary(separate)['observation'])
        java = ('FATAL EXCEPTION: dora-recording\nProcess: ' + baseline.PACKAGE + ', PID: 123\n'
                'at ai.onnxruntime.OrtSession.run\nat com.monumentogram.dora.vad.SherpaVadEngine.open\n')
        self.assertEqual('DORA_ORT_VAD_CRASH_OBSERVED', baseline.crash_summary(java)['observation'])

    def test_exact_truncated_vad_thread_and_ort_constructor_offsets_are_preserved_without_raw_text(self):
        log = ('*** *** ***\nABI: \'x86_64\'\nGuest architecture: \'arm64\'\nCmdline: ' + baseline.PACKAGE + '\n'
               'pid: 12, tid: 34, name: Dora VAD infere  >>> ' + baseline.PACKAGE + ' <<<\n'
               'signal 11 (SIGSEGV), code 1, fault addr 0x0000000000000008\n'
               '#00 pc 00000000014bb1c8 /PRIVATE/base.apk!libonnxruntime.so\n'
               '#01 pc 0000000001230b40 /PRIVATE/PATH/libonnxruntime.so\n'
               '#02 pc 0000000000b2a2fc /PRIVATE/PATH/libonnxruntime.so\n'
               '#03 pc 0000000000000001 /system/lib64/libc.so (pthread_once)\n'
               '#04 pc 0000000000000002 /system/bin/linker64 (call_constructors)\n'
               '#05 pc 0000000000000003 /system/lib64/libnative_bridge.so\nSECRET\n')
        summary = baseline.crash_summary(log)
        self.assertEqual('DORA_ORT_VAD_CRASH_OBSERVED', summary['observation'])
        self.assertEqual(['0x14bb1c8', '0x1230b40', '0xb2a2fc'], [row['pc'] for row in summary['ort_frames']])
        self.assertEqual(['x86_64'], summary['guest_crash_abis'])
        self.assertEqual(['arm64'], summary['guest_architectures'])
        self.assertEqual(['0x8'], summary['fault_addresses'])
        self.assertEqual(['CALL_CONSTRUCTORS', 'NATIVE_BRIDGE', 'PTHREAD_ONCE'], summary['native_startup_markers'])
        self.assertIn('VAD_THREAD', summary['stack_categories'])
        self.assertNotIn('PRIVATE', json.dumps(summary))
        self.assertNotIn('SECRET', json.dumps(summary))
        self.assertEqual('DORA_OTHER_CRASH_OBSERVED', baseline.crash_summary(
            log.replace('Dora VAD infere', 'Other VAD thread'))['observation'])

    def test_wrong_hash_or_preexisting_package_never_installs_and_never_force_stops_it(self):
        with tempfile.TemporaryDirectory() as temporary:
            apk = Path(temporary) / 'fixture.apk'
            apk.write_bytes(b'public-fixture')
            with patch.object(baseline, 'command') as command:
                with self.assertRaises(baseline.BaselineFailure):
                    baseline.run_baseline(apk, Path(temporary), 'emulator-5554', Path(temporary) / 'result.json')
                command.assert_not_called()
            calls = []
            def command(arguments, **kwargs):
                calls.append(arguments)
                if arguments[0].endswith('aapt') or arguments[0].endswith('aapt.exe'):
                    return "package: name='com.monumentogram.dora.alpha.preview' versionCode='8' versionName='0.1.0-owner-preview.8'"
                if 'verify' in arguments:
                    return 'Signer #1 certificate SHA-256 digest: ' + baseline.CERT_SHA256
                if arguments[-1] == 'ro.kernel.qemu':
                    return '1'
                if arguments[-1] == 'ro.build.version.sdk':
                    return '36'
                if arguments[-1] == 'ro.product.cpu.abi':
                    return 'x86_64'
                if 'packages' in arguments:
                    return 'package:' + baseline.PACKAGE
                raise AssertionError('Unexpected external call')
            with patch.object(baseline, 'APK_SHA256', hashlib.sha256(apk.read_bytes()).hexdigest()), \
                 patch.object(baseline, 'command', side_effect=command):
                with self.assertRaises(baseline.BaselineFailure):
                    baseline.run_baseline(apk, Path(temporary), 'emulator-5554', Path(temporary) / 'fresh.json')
            self.assertFalse(any('install' in call or 'force-stop' in call for call in calls))


class SafeUiDiagnosticTests(unittest.TestCase):
    def test_known_dora_action_reports_only_counts_and_categories(self):
        result = baseline.ui_diagnostic(tree('New recording'), 'new_recording', 1080, 2400)
        self.assertEqual(['DORA'], result['package_categories'])
        self.assertEqual(1, result['known_control_matches']['new_recording'])
        self.assertEqual(1, result['expected_enabled_matches'])
        self.assertEqual(1, result['expected_clickable_ancestor_matches'])
        self.assertEqual(1, result['expected_bounded_target_matches'])
        self.assertFalse(result['keyguard_resource_marker'])

    def test_keyguard_screen_and_arbitrary_text_cannot_leak(self):
        xml = tree('PRIVATE SECRET', package='com.android.systemui',
                   attributes='resource-id="com.android.systemui:id/keyguard_status_view"')
        result = baseline.ui_diagnostic(xml, 'new_recording', 1080, 2400)
        self.assertEqual(['SYSTEM_UI'], result['package_categories'])
        self.assertTrue(result['keyguard_resource_marker'])
        self.assertEqual(0, result['known_control_matches']['new_recording'])
        self.assertNotIn('SECRET', json.dumps(result))
        self.assertNotIn('keyguard_status_view', json.dumps(result))

    def test_setup_and_unrelated_packages_report_fixed_categories_only(self):
        for package, expected in [('com.google.android.setupwizard', 'SETUP'), ('private.secret.app', 'OTHER')]:
            with self.subTest(package=package):
                result = baseline.ui_diagnostic(tree('SECRET', package=package), 'new_recording', 1080, 2400)
                self.assertEqual([expected], result['package_categories'])
                self.assertNotIn('SECRET', json.dumps(result))
                self.assertNotIn(package, json.dumps(result))

    def test_malformed_unknown_or_oversize_ui_fails_closed(self):
        for xml, action in [('<hierarchy>', 'new_recording'), ('<hierarchy/>', 'unknown'),
                            ('<hierarchy>' + '<node/>' * 4097 + '</hierarchy>', 'new_recording')]:
            with self.subTest(action=action), self.assertRaises(baseline.BaselineFailure):
                baseline.ui_diagnostic(xml, action, 1080, 2400)


if __name__ == '__main__':
    unittest.main()
