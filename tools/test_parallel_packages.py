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


if __name__ == '__main__':
    unittest.main()
