"""Fail-closed inventory checks for the separate additive emulator suite."""
import sys
from pathlib import Path
import unittest

sys.path.insert(0, str(Path(__file__).parents[1]))
from poco_non_battery.diagnostic_isolation import EXPECTED, verify_output


def output(names, status=0):
    lines = []
    for name in names:
        cls, method = name.split('#')
        for code in (1, status):
            lines.extend((f'INSTRUMENTATION_STATUS: class={cls}',
                          f'INSTRUMENTATION_STATUS: test={method}',
                          f'INSTRUMENTATION_STATUS_CODE: {code}'))
    return '\n'.join(lines + [f'OK ({len(names)} tests)', 'INSTRUMENTATION_CODE: -1'])


class DiagnosticInventoryTest(unittest.TestCase):
    def test_exact_inventory(self):
        self.assertEqual(len(verify_output(output(sorted(EXPECTED)))), 15)

    def test_missing_test(self):
        with self.assertRaises(ValueError):
            verify_output(output(sorted(EXPECTED)[1:]))

    def test_duplicate_test(self):
        with self.assertRaises(ValueError):
            verify_output(output(sorted(EXPECTED) + [sorted(EXPECTED)[0]]))

    def test_skipped_test(self):
        with self.assertRaises(ValueError):
            verify_output(output(sorted(EXPECTED), -4))

    def test_failed_test(self):
        with self.assertRaises(ValueError):
            verify_output(output(sorted(EXPECTED), -2))

    def test_unexpected_test(self):
        with self.assertRaises(ValueError):
            verify_output(output(sorted(EXPECTED) + ['unexpected.Test#method']))


if __name__ == '__main__':
    unittest.main()
