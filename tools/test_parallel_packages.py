"""Identity and fail-closed parsing for synthetic parallel-package evidence."""
import unittest


class ParallelTests(unittest.TestCase):
    def test_seed_requires_exact_package_directory_and_uid(self):
        try:
            from run_parallel_packages import seed_result
        except ImportError:
            self.fail('parallel package runner required')
        package = 'com.monumentogram.dora.alpha.preview'
        good = ('INSTRUMENTATION_STATUS: parallel_uid=10123\n'
                f'INSTRUMENTATION_STATUS: parallel_dir=/data/user/0/{package}/no_backup\n'
                'INSTRUMENTATION_STATUS: parallel_sealed=QUJDRA==\n')
        self.assertEqual('10123', seed_result(good, package)['uid'])
        for bad in ('', good + good, good.replace('.preview/', '.pilot/'),
                    good.replace('10123', '0'), good.replace('QUJDRA==', 'bad;command')):
            with self.subTest(bad=bad), self.assertRaises(ValueError):
                seed_result(bad, package)


if __name__ == '__main__':
    unittest.main()
