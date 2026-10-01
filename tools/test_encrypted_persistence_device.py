"""Reject incomplete, skipped, or crashed Android instrumentation as runtime evidence."""
import unittest

import run_encrypted_persistence_device as device


def result(name, code):
    return ('INSTRUMENTATION_STATUS: class=example.PersistenceTest\n'
            f'INSTRUMENTATION_STATUS: test={name}\nINSTRUMENTATION_STATUS_CODE: {code}\n')


class DeviceReceiptTests(unittest.TestCase):
    def test_getconf_runtime_and_kernel_pages_are_separate_for_x86_64_emulation(self):
        measured = device.page_size_measurement(36, 'x86_64', '16384', 'KernelPageSize: 4 kB\n')
        self.assertEqual(16384, measured['page_size'])
        self.assertEqual(4096, measured['kernel_page_size'])
        self.assertEqual('GETCONF_PAGE_SIZE', measured['page_size_evidence'])
        self.assertEqual('X86_64_16K_USERSPACE_EMULATION', measured['page_size_mode'])

    def test_matching_runtime_and_kernel_measurements_remain_exact(self):
        for size in (4096, 16384):
            measured = device.page_size_measurement(36, 'arm64-v8a', str(size),
                                                    f'KernelPageSize: {size // 1024} kB\n')
            self.assertEqual(size, measured['page_size'])
            self.assertEqual(size, measured['kernel_page_size'])
            self.assertEqual('RUNTIME_MATCHES_KERNEL_MAPPING', measured['page_size_mode'])

    def test_only_api28_missing_getconf_can_use_measured_4k_fallback(self):
        measured = device.page_size_measurement(28, 'x86_64', 'GETCONF_UNAVAILABLE',
                                                'KernelPageSize: 4 kB\n')
        self.assertEqual(4096, measured['page_size'])
        self.assertEqual('API28_KERNEL_MAPPING_FALLBACK', measured['page_size_evidence'])
        self.assertEqual('API28_4K_KERNEL_MAPPING_ONLY', measured['page_size_mode'])
        for api, kernel in ((29, 4), (36, 4), (28, 16)):
            with self.assertRaises(ValueError):
                device.page_size_measurement(api, 'x86_64', 'GETCONF_UNAVAILABLE',
                                             f'KernelPageSize: {kernel} kB\n')

    def test_malformed_getconf_and_unexplained_page_disagreement_fail_closed(self):
        for output in ('', 'error', '65536', '4096\n16384', '16384\n16384', '016384'):
            with self.assertRaises(ValueError):
                device.page_size_measurement(28, 'x86_64', output, 'KernelPageSize: 4 kB\n')
        for api, abi, runtime, kernel in ((36, 'arm64-v8a', 16384, 4),
                                          (34, 'x86_64', 16384, 4),
                                          (36, 'x86_64', 4096, 16)):
            with self.assertRaises(ValueError):
                device.page_size_measurement(api, abi, str(runtime),
                                             f'KernelPageSize: {kernel} kB\n')

    def test_getconf_command_failure_does_not_fall_back_to_kernel_page_size(self):
        def command(*arguments):
            if arguments == ('shell', 'cat', '/proc/self/smaps'):
                return 'KernelPageSize: 4 kB\n'
            raise ValueError('getconf command failed')
        with self.assertRaisesRegex(ValueError, 'getconf command failed'):
            device.measure_page_sizes(command, 28, 'x86_64')

    def test_page_size_is_observed_on_api28_without_getconf(self):
        self.assertEqual(4096, device.page_size_from_smaps('KernelPageSize: 4 kB\nKernelPageSize: 4 kB\n'))
        self.assertEqual(16384, device.page_size_from_smaps('KernelPageSize: 16 kB\n'))
        for text in ('', 'KernelPageSize: 64 kB\n', 'KernelPageSize: 4 kB\nKernelPageSize: 16 kB\n'):
            with self.assertRaises(ValueError):
                device.page_size_from_smaps(text)

    def test_inventory_covers_multiple_no_credential_controls_without_arbitrary_skips(self):
        runtime = device.PACKAGE + '.runtime.AndroidProductAudioRuntimeTest#noCredentialCannotCreateStorage'
        valid = {'tests': [device.NO_CREDENTIAL, runtime, device.PACKAGE + '.Example#encryptedRoundTrip'],
                 'no_credential_tests': [device.NO_CREDENTIAL, runtime]}
        expected, controls = device.validate_inventory(valid)
        self.assertEqual(set(valid['tests']), expected)
        self.assertEqual({device.NO_CREDENTIAL, runtime}, controls)
        for changed in (dict(valid, no_credential_tests=[]),
                        dict(valid, no_credential_tests=[device.NO_CREDENTIAL, runtime, runtime]),
                        dict(valid, no_credential_tests=[device.NO_CREDENTIAL, device.PACKAGE + '.Missing#test']),
                        dict(valid, tests=valid['tests'] + ['unrelated.Test#test'])):
            with self.assertRaises(ValueError):
                device.validate_inventory(changed)

    def test_exact_success_and_one_explicit_assumption(self):
        output = result('encryptedRoundTrip', 0) + result('noCredential', -4) + 'OK (2 tests)\nINSTRUMENTATION_CODE: -1\n'
        rows = device.parse_results(output, expected_skips={'example.PersistenceTest#noCredential'})
        self.assertEqual([row['result'] for row in rows], ['PASS', 'SKIPPED'])

    def test_crash_or_runner_success_without_completed_tests_rejected(self):
        for output in ('INSTRUMENTATION_CODE: -1\n', result('started', 1) + 'INSTRUMENTATION_CODE: -1\n',
                       result('test', 0) + 'INSTRUMENTATION_FAILED: Process crashed\n'):
            with self.assertRaises(ValueError):
                device.parse_results(output)

    def test_failure_unexpected_skip_duplicate_and_count_mismatch_rejected(self):
        for output in (result('test', -2), result('test', -4), result('test', 0) * 2,
                       result('test', 0) + 'OK (2 tests)\n'):
            with self.assertRaises(ValueError):
                device.parse_results(output + 'INSTRUMENTATION_CODE: -1\n')

    def test_expected_skip_cannot_silently_disappear(self):
        with self.assertRaises(ValueError):
            device.parse_results(result('test', 0) + 'OK (1 test)\nINSTRUMENTATION_CODE: -1\n',
                                 expected_skips={'example.PersistenceTest#missing'})


if __name__ == '__main__':
    unittest.main()
