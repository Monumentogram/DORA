"""Identity and fail-closed parsing for synthetic parallel-package evidence."""
import unittest


class ParallelTests(unittest.TestCase):
    def test_seed_requires_exact_package_directory_and_uid(self):
        try:
            from run_parallel_packages import seed_result
        except ImportError:
            self.fail('parallel package runner required')
        package = 'com.monumentogram.dora.alpha.preview'
        good = ('INSTRUMENTATION_RESULT: parallel_uid=10123\n'
                f'INSTRUMENTATION_RESULT: parallel_dir=/data/user/0/{package}/no_backup\n'
                'INSTRUMENTATION_RESULT: parallel_sealed=QUJDRA==\n')
        self.assertEqual('10123', seed_result(good, package)['uid'])
        from run_owner_pilot_device import exact_results
        selected = 'com.monumentogram.dora.ParallelPackageIsolationTest#seed'
        output = ('INSTRUMENTATION_STATUS: class=com.monumentogram.dora.ParallelPackageIsolationTest\n'
                  'INSTRUMENTATION_STATUS: test=seed\nINSTRUMENTATION_STATUS_CODE: 1\n'
                  'INSTRUMENTATION_STATUS: class=com.monumentogram.dora.ParallelPackageIsolationTest\n'
                  'INSTRUMENTATION_STATUS: test=seed\nINSTRUMENTATION_STATUS_CODE: 0\n'
                  + good + 'OK (1 test)\nINSTRUMENTATION_CODE: -1\n')
        self.assertEqual([{'name': selected, 'result': 'PASS'}], exact_results(output, {selected}))
        self.assertEqual('10123', seed_result(output, package)['uid'])
        for bad in ('', good + good, good.replace('.preview/', '.pilot/'),
                    good.replace('10123', '0'), good.replace('QUJDRA==', 'bad;command')):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                seed_result(bad, package)

    def test_current_source_peer_profile_is_explicit_before_any_device_command(self):
        from unittest.mock import patch
        import owner_alpha_pilot_artifact as pilot
        import owner_preview_artifact as preview
        import run_owner_pilot_device as device
        import run_parallel_packages as parallel

        def inspect(sdk, apk, test):
            module = pilot if apk.name == 'pilot.apk' else preview
            badging = ("package: name='" + module.PACKAGE + "' versionCode='" +
                       str(module.VERSION_CODE) + "' versionName='" + module.VERSION_NAME + "'" +
                       "\nuses-permission: name='android.permission.INTERNET'" +
                       "\nuses-permission: name='android.permission.ACCESS_NETWORK_STATE'")
            device.check_badging(badging)
            self.assertEqual(module.PACKAGE, device.PACKAGE)

        argv = ['parallel', '--serial', 'emulator-5554', '--expected-api', '28',
                '--pilot', 'pilot.apk', '--pilot-test', 'pilot-test.apk',
                '--preview', 'preview.apk', '--preview-test', 'preview-test.apk',
                '--receipt', 'never-written-parallel-peer-test-receipt.json']
        with patch('sys.argv', argv), patch.dict(parallel.os.environ, {'ANDROID_HOME': 'unread-sdk'}), \
                patch.object(parallel, 'source_binding', return_value={'source_sha': 'a' * 40, 'source_clean': True}), \
                patch.object(parallel, 'sha', return_value='b' * 64), \
                patch.object(pilot, 'audit_apk') as pilot_audit, patch.object(preview, 'audit_apk') as preview_audit, \
                patch.object(device, 'PACKAGE', device.PACKAGE), patch.object(device, 'check_badging', device.check_badging), \
                patch.object(device, 'inspect_apk_inputs', side_effect=inspect), \
                patch.object(parallel, 'emulator_fence', side_effect=RuntimeError('STOP_BEFORE_DEVICE')) as fence:
            with self.assertRaisesRegex(RuntimeError, 'STOP_BEFORE_DEVICE'):
                parallel.main()
            self.assertEqual({'synthetic_peer': True}, pilot_audit.call_args.kwargs)
            self.assertEqual({}, preview_audit.call_args.kwargs)
            self.assertEqual('ci-unsigned', pilot_audit.call_args.args[2])
            fence.assert_called_once()


if __name__ == '__main__':
    unittest.main()
