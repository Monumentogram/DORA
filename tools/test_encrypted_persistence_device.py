"""Reject incomplete, skipped, or crashed Android instrumentation as runtime evidence."""
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import run_encrypted_persistence_device as device


def result(name, code):
    return ('INSTRUMENTATION_STATUS: class=example.PersistenceTest\n'
            f'INSTRUMENTATION_STATUS: test={name}\nINSTRUMENTATION_STATUS_CODE: {code}\n')


class DeviceReceiptTests(unittest.TestCase):
    def test_api28_library_budget_requires_exact_approved_inventory_and_stride(self):
        path = Path(__file__).resolve().parents[1] / 'docs/contracts/alpha-v9-persistence-tests-v1.json'
        inventory = json.loads(path.read_text(encoding='utf-8'))
        expected, _ = device.validate_inventory(inventory)
        heavy = device.PACKAGE + '.RecordingSearchPersistenceTest#syntheticEncryptedLibraries10_100_1000'
        def budget(contract=inventory, names=None, api=28, index=0, count=4, diagnostic=False,
                   exact=True, phase='pin-free'):
            names = device.select_inventory(set(contract['tests']), shard_index=index, shard_count=count) if names is None else names
            return device.phase_budget(contract, names, api=api, shard_index=index, shard_count=count,
                                       diagnostic=diagnostic, exact_inventory=exact, phase=phase)
        for index in range(4):
            selected = device.select_inventory(expected, shard_index=index, shard_count=4)
            for phase in ('pin-free', 'credential'):
                result = budget(names=selected, index=index, phase=phase)
                self.assertEqual(2700 if heavy in selected else 1800, result['timeout_seconds'])
                self.assertEqual('API28_V9_LIBRARY_1000_2700_V1' if heavy in selected else
                                 'DEFAULT_PERSISTENCE_1800_V1', result['policy'])
        selected = device.select_inventory(expected, shard_index=0, shard_count=4)
        self.assertIn(heavy, selected)
        self.assertEqual(1800, budget(api=36)['timeout_seconds'])
        self.assertEqual(1800, budget(exact=False)['timeout_seconds'])
        self.assertEqual(1800, budget(count=1)['timeout_seconds'])
        self.assertEqual(1800, budget(count=2)['timeout_seconds'])
        self.assertEqual(600, budget(diagnostic=True, phase='credential')['timeout_seconds'])
        self.assertEqual(1800, budget(diagnostic=True)['timeout_seconds'])
        for phase in ('initial-no-credential', 'final-no-credential'):
            self.assertEqual(120, budget(phase=phase)['timeout_seconds'])
        wrong = set(selected)
        wrong.remove(next(name for name in wrong if name != heavy))
        wrong.add(next(name for name in expected if name not in selected))
        self.assertEqual(1800, budget(names=wrong)['timeout_seconds'])
        self.assertEqual(1800, budget(names=selected, index=1)['timeout_seconds'])
        for key, value in [('authority', ['UNREVIEWED']), ('no_credential_tests', []),
                           ('successor', 'OTHER'), ('profile', 'OTHER')]:
            changed = dict(inventory, **{key: value})
            self.assertEqual(1800, budget(contract=changed, names=selected)['timeout_seconds'])
        changed = json.loads(json.dumps(inventory))
        other = next(i for i, name in enumerate(changed['tests']) if name != heavy and name not in selected)
        changed['tests'][other] += 'Unreviewed'
        self.assertEqual(188, len(set(changed['tests'])))
        self.assertEqual(1800, budget(contract=changed, names=selected)['timeout_seconds'])

    def test_approved_library_batch_applies_same_budget_to_both_pin_states_and_failure_metadata(self):
        inventory = json.loads((Path(__file__).resolve().parents[1] /
            'docs/contracts/alpha-v9-persistence-tests-v1.json').read_text(encoding='utf-8'))
        expected, _ = device.validate_inventory(inventory)
        selected = device.select_inventory(expected, shard_index=0, shard_count=4)
        for failed in (None, 'timeout', 'skip', 'failure', 'inventory-change'):
            with self.subTest(failed=failed), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                inventory_path, apk, receipt = root / 'inventory.json', root / 'test.apk', root / 'receipt.json'
                inventory_path.write_text(json.dumps(inventory), encoding='utf-8')
                apk.write_bytes(b'synthetic-apk')
                seen = []
                def command(arguments, **kwargs):
                    args = tuple(arguments[3:])
                    values = {('get-state',): 'device', ('shell', 'getprop', 'ro.kernel.qemu'): '1',
                              ('shell', 'getprop', 'ro.build.version.sdk'): '28',
                              ('shell', 'getprop', 'ro.product.cpu.abi'): 'x86_64',
                              ('shell', 'cat', '/proc/self/smaps'): 'KernelPageSize: 4 kB\n'}
                    value = values.get(args, 'Success')
                    if len(args) > 1 and str(args[1]).startswith('if command -v getconf'): value = '4096'
                    if args[:3] == ('shell', 'locksettings', 'set-pin'): value = 'Pin set to synthetic'
                    if args[:3] == ('shell', 'locksettings', 'clear'): value = 'Lock credential cleared'
                    return type('Completed', (), {'stdout': value})()
                def stream(argv, names, path, **kwargs):
                    phase = path.stem
                    seen.append((phase, set(names), kwargs['hard_timeout']))
                    policy = json.loads(path.with_suffix('.budget.json').read_text())
                    self.assertEqual(kwargs['hard_timeout'], policy['timeout_seconds'])
                    self.assertEqual(phase, policy['phase'])
                    if failed == 'timeout' and phase == 'pin-free':
                        raise device.diagnostics.InstrumentationFailure('HARD_TIMEOUT')
                    if failed == 'inventory-change' and phase == 'credential':
                        inventory_path.write_bytes(inventory_path.read_bytes() + b'\n')
                    rows = []
                    for name in sorted(names):
                        cls, method = name.split('#')
                        for code in (1, 0):
                            if phase == 'pin-free' and code == 0:
                                code = -4 if failed == 'skip' else -2 if failed == 'failure' else code
                            rows.append(f'INSTRUMENTATION_STATUS: class={cls}\n'
                                        f'INSTRUMENTATION_STATUS: test={method}\nINSTRUMENTATION_STATUS_CODE: {code}\n')
                    return ''.join(rows) + f'OK ({len(names)} tests)\nINSTRUMENTATION_CODE: -1\n'
                args = ['runner', '--serial', 'emulator-5554', '--expected-api', '28', '--apk', str(apk),
                        '--inventory', str(inventory_path), '--receipt', str(receipt), '--exact-inventory',
                        '--shard-index', '0', '--shard-count', '4']
                with patch('sys.argv', args), patch.dict('os.environ', {'ANDROID_HOME': temporary}), \
                        patch.object(device.subprocess, 'run', side_effect=command), \
                        patch.object(device.diagnostics, 'stream', side_effect=stream):
                    if failed:
                        failure = device.diagnostics.InstrumentationFailure if failed == 'timeout' else ValueError
                        with self.assertRaises(failure): device.main()
                        self.assertFalse(receipt.exists())
                    else:
                        device.main()
                budget = json.loads((root / 'receipt-diagnostics/pin-free.budget.json').read_text())
                self.assertEqual('API28_V9_LIBRARY_1000_2700_V1', budget['policy'])
                self.assertEqual(2700, budget['timeout_seconds'])
                self.assertEqual(47, budget['selected_test_count'])
                self.assertNotIn('syntheticAuthPin', json.dumps(budget))
                if not failed:
                    self.assertEqual([120, 2700, 2700, 120], [row[2] for row in seen])
                    saved = json.loads(receipt.read_text())
                    self.assertEqual(saved['phase_budgets']['pin-free']['policy'], saved['phase_budgets']['credential']['policy'])
                    for key in ('pin_free_tests', 'credential_tests'):
                        self.assertEqual(selected, {row['name'] for row in saved[key]})
                        self.assertEqual({'PASS'}, {row['result'] for row in saved[key]})

    def test_recording_progress_is_validated_without_becoming_a_test_completion(self):
        name = 'com.monumentogram.dora.audio.persistence.AlphaRecordingRuntimeTest'
        start = result('recording', 1).replace('example.PersistenceTest', name)
        end = result('recording', 0).replace('example.PersistenceTest', name)
        checkpoint = ('INSTRUMENTATION_STATUS: stage9Progress=phase=APPEND acceptedUnits=30 '
                      'durableFrames=2400000 elapsedMs=1000\nINSTRUMENTATION_STATUS_CODE: 3\n')
        terminal = 'OK (1 test)\nINSTRUMENTATION_CODE: -1\n'
        self.assertEqual([{'name': name + '#recording', 'result': 'PASS'}],
                         device.parse_results(start + checkpoint + end + terminal))
        for output in (start + checkpoint + terminal,
                       checkpoint + start + end + terminal,
                       start + end + checkpoint + terminal,
                       (start + checkpoint + end + terminal).replace(name, 'example.PersistenceTest'),
                       start + checkpoint.replace('APPEND', 'UNKNOWN') + end + terminal,
                       start + checkpoint.replace('acceptedUnits=30', 'acceptedUnits=999') + end + terminal,
                       start + 'INSTRUMENTATION_STATUS_CODE: 3\n' + end + terminal,
                       start + checkpoint.replace('INSTRUMENTATION_STATUS_CODE: 3\n', '') + end + terminal,
                       start + checkpoint + end + terminal + checkpoint):
            with self.subTest(output=output), self.assertRaises(ValueError):
                device.parse_results(output)

    def test_four_shards_partition_all_188_ordinary_methods_without_overlap(self):
        inventory = json.loads((Path(__file__).resolve().parents[1] /
                                'docs/contracts/alpha-v9-persistence-tests-v1.json').read_text(encoding='utf-8'))
        expected, _ = device.validate_inventory(inventory)
        self.assertEqual(188, len(expected))
        shards = [device.select_inventory(expected, shard_index=index, shard_count=4) for index in range(4)]
        self.assertEqual([47, 47, 47, 47], [len(shard) for shard in shards])
        self.assertEqual(expected, set().union(*shards))
        for index, shard in enumerate(shards):
            self.assertEqual(set(sorted(expected)[index::4]), shard)
            for other in shards[index + 1:]:
                self.assertFalse(shard & other)
        self.assertEqual(expected, device.select_inventory(expected))

    def test_shard_bounds_empty_selection_and_diagnostic_combination_fail_closed(self):
        expected = {'example.Test#alpha', 'example.Test#beta'}
        for index, count in ((-1, 1), (0, 0), (0, -1), (1, 1), (2, 2), (0, 3), (2, 3)):
            with self.subTest(index=index, count=count), self.assertRaises(ValueError):
                device.select_inventory(expected, shard_index=index, shard_count=count)
        with self.assertRaises(ValueError):
            device.select_inventory(set())
        with self.assertRaises(ValueError):
            device.select_inventory(expected, shard_count=2, diagnostic_class='example.Test')
        with self.assertRaises(ValueError):
            device.select_inventory(expected, diagnostic_method='alpha')
        with self.assertRaises(ValueError):
            device.select_inventory(expected, diagnostic_method='')
        with self.assertRaises(ValueError):
            device.select_inventory(expected, diagnostic_class='example.Test', diagnostic_method='')
        with self.assertRaises(ValueError):
            device.select_inventory(expected, diagnostic_class='example.Missing')
        self.assertEqual({'example.Test#alpha'}, device.select_inventory(
            expected, diagnostic_class='example.Test', diagnostic_method='alpha'))

    def test_sharded_runner_preserves_controls_both_pin_states_timeout_and_exact_receipt(self):
        for corruption in (None, 'failure', 'skip', 'missing', 'other-shard'):
            with self.subTest(corruption=corruption), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                names = [device.PACKAGE + '.Example#' + suffix for suffix in ('alpha', 'beta', 'gamma')]
                inventory = {'profile': 'PIN_FREE_LOCAL_ACCESS', 'tests': [*names, device.LOCAL_ACCESS],
                             'no_credential_tests': [device.LOCAL_ACCESS]}
                inventory_path, apk, receipt = root / 'inventory.json', root / 'test.apk', root / 'receipt.json'
                inventory_path.write_text(json.dumps(inventory), encoding='utf-8')
                apk.write_bytes(b'synthetic-apk')
                # Global sort is alpha, beta, gamma, then the auth control; shard 0 selects alpha/gamma.
                selected = {names[0], names[2]}
                phases = []

                def command(arguments, **kwargs):
                    args = arguments[3:]
                    outputs = {('get-state',): 'device',
                               ('shell', 'getprop', 'ro.kernel.qemu'): '1',
                               ('shell', 'getprop', 'ro.build.version.sdk'): '28',
                               ('shell', 'getprop', 'ro.product.cpu.abi'): 'x86_64',
                               ('shell', 'cat', '/proc/self/smaps'): 'KernelPageSize: 4 kB\n'}
                    output = outputs.get(tuple(args))
                    if output is None:
                        if args[:2] == ['shell', 'if command -v getconf >/dev/null 2>&1; then getconf PAGE_SIZE; else echo GETCONF_UNAVAILABLE; fi']:
                            output = '4096'
                        elif args[:3] == ['shell', 'locksettings', 'set-pin']:
                            output = 'Pin set to synthetic'
                        elif args[:3] == ['shell', 'locksettings', 'clear']:
                            output = 'Lock credential cleared'
                        else:
                            output = 'Success'
                    return type('Completed', (), {'stdout': output})()

                def instrument(arguments, expected, path, **kwargs):
                    phase = path.stem
                    phases.append((phase, set(expected), kwargs['hard_timeout']))
                    emitted = sorted(expected)
                    self.assertEqual(emitted, arguments[arguments.index('class') + 1].split(','))
                    if phase == 'credential':
                        self.assertIn('syntheticAuthPin', arguments)
                    if phase == 'pin-free' and corruption == 'missing':
                        emitted.pop()
                    if phase == 'pin-free' and corruption == 'other-shard':
                        emitted[-1] = names[1]
                    code = -2 if corruption == 'failure' else -4 if corruption == 'skip' else 0
                    rows = []
                    for name in emitted:
                        class_name, method = name.split('#')
                        rows.append(f'INSTRUMENTATION_STATUS: class={class_name}\n'
                                    f'INSTRUMENTATION_STATUS: test={method}\n'
                                    f'INSTRUMENTATION_STATUS_CODE: {code if phase == "pin-free" else 0}\n')
                    return ''.join(rows) + f'OK ({len(emitted)} tests)\nINSTRUMENTATION_CODE: -1\n'

                arguments = ['runner', '--serial', 'emulator-5554', '--expected-api', '28', '--apk', str(apk),
                             '--inventory', str(inventory_path), '--receipt', str(receipt),
                             '--shard-index', '0', '--shard-count', '2']
                with patch('sys.argv', arguments), patch.dict('os.environ', {'ANDROID_HOME': temporary}), \
                     patch.object(device.subprocess, 'run', side_effect=command), \
                     patch.object(device.diagnostics, 'stream', side_effect=instrument):
                    if corruption:
                        with self.assertRaises(ValueError):
                            device.main()
                        self.assertFalse(receipt.exists())
                        continue
                    device.main()
                self.assertEqual([('initial-no-credential', {device.LOCAL_ACCESS}, 120),
                                  ('pin-free', selected, 1800), ('credential', selected, 1800),
                                  ('final-no-credential', {device.LOCAL_ACCESS}, 120)], phases)
                saved = json.loads(receipt.read_text(encoding='utf-8'))
                self.assertEqual({'index': 0, 'count': 2, 'global_test_count': 4, 'selected_test_count': 2,
                                  'selected_tests_sha256': hashlib.sha256(
                                      ('\n'.join([names[0], names[2]]) + '\n').encode()).hexdigest(),
                                  'selection_policy': 'SORTED_GLOBAL_NAMES_STRIDE_V1'}, saved['shard'])
                for key in ('pin_free_tests', 'credential_tests'):
                    self.assertEqual(selected, {row['name'] for row in saved[key]})
                    self.assertEqual({'PASS'}, {row['result'] for row in saved[key]})

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
