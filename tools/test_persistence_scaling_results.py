"""Fail-closed benchmark receipt tests; all fixtures are synthetic."""
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

import persistence_scaling_results as parser


def records():
    result = [dict(event='BEGIN', api=36, maximum=2000, pcmBytes=160000,
                   timingScope='synthetic test', countersScope='synthetic test')]
    for size in (10, 100, 400, 1000, 2000):
        kinds = [('catalog_load', size)]*3 + [pair for _ in range(3) for pair in
                 [('metadata_insert', size), ('metadata_same_row', size)]] + [
                     ('append', size+i) for i in range(3)]
        for kind, count in kinds:
            result.append(dict(event='MEASURE', kind=kind, blocksBefore=count,
                elapsedNanos=1000000+count, threadCpuNanos=500000, processCpuMs=1,
                javaHeapBefore=100, javaHeapAfter=90, nativeHeapBefore=80, nativeHeapAfter=90,
                gcCountBefore=None, gcCountAfter=None, filesAfter=10, bytesAfter=100,
                databaseBytesAfter=20, walBytesAfter=30,
                submittedUnreturnedFramesPeak=80000 if kind == 'append' else 0,
                submittedUnreturnedFramesAfter=0, captureOutstandingFrames=None,
                stages={'sql_policy': 100, 'sql_policy_count': 2},
                boundaries={'counts': {'db.query': 2}, 'nanos': {'db.query': 100}}))
        result.append(dict(event='CHECKPOINT', target=size, actualBlocks=size+3))
    result.append(dict(event='COMPLETE', blocks=2003, authenticatedPcmFrames=160240000,
                       reopenedCatalogVerified=True))
    return result


def log(rows, ending='OK (1 test)\n'):
    return ''.join('INSTRUMENTATION_STATUS: stream=SCALING '+json.dumps(row)+'\n'
                   for row in rows) + ending


class BenchmarkParserTests(unittest.TestCase):
    def test_full_receipt_preserves_small_n_and_real_append_sizes(self):
        result = parser.parse(log(records()), expected_api=36)
        self.assertEqual(result['status'], 'COMPLETE')
        self.assertEqual(result['checkpoints'][0]['operations']['append']['blocks_before'], [10, 11, 12])
        self.assertEqual(result['checkpoints'][0]['operations']['append']['elapsed_ms'],
                         {'n': 3, 'p50': 1.000011, 'max': 1.000012})
        self.assertIsNone(result['measurements'][0]['captureOutstandingFrames'])
        self.assertNotIn('p95', str(result))

    def test_missing_begin_checkpoint_complete_or_junit_never_completes(self):
        rows = records()
        for changed, ending in ((rows[1:], 'OK (1 test)'),
                                ([r for r in rows if r.get('target') != 100], 'OK (1 test)'),
                                (rows[:-1], 'OK (1 test)'), (rows, '')):
            with self.subTest(length=len(changed), ending=ending):
                self.assertNotEqual(parser.parse(log(changed, ending), 36)['status'], 'COMPLETE')

    def test_partial_valid_prefix_is_incomplete_with_completed_checkpoints(self):
        result = parser.parse(log(records()[:14], ''), 36)
        self.assertEqual(result['status'], 'INCOMPLETE')
        self.assertEqual([r['target'] for r in result['checkpoints']], [10])

    def test_duplicate_reordered_or_wrong_size_records_are_invalid(self):
        cases = []
        rows = records()
        cases.append(rows[:2]+[rows[1]]+rows[2:])
        rows = records(); rows[1], rows[4] = rows[4], rows[1]; cases.append(rows)
        rows = records(); rows[13]['actualBlocks'] = 10; cases.append(rows)
        rows = records(); rows[10]['blocksBefore'] = 11; cases.append(rows)
        for rows in cases:
            with self.subTest():
                self.assertEqual(parser.parse(log(rows), 36)['status'], 'INVALID')

    def test_wrong_api_or_authenticated_frame_count_cannot_pass(self):
        self.assertEqual(parser.parse(log(records()), 28)['status'], 'INVALID')
        rows = records(); rows[-1]['authenticatedPcmFrames'] = 160000000
        self.assertEqual(parser.parse(log(rows), 36)['status'], 'INVALID')

    def test_failure_or_success_before_complete_cannot_pass(self):
        self.assertEqual(parser.parse(log(records(), 'FAILURES!!!\nOK (1 test)'), 36)['status'], 'FAILED')
        result = parser.parse('OK (1 test)\n'+log(records(), ''), 36)
        self.assertNotEqual(result['status'], 'COMPLETE')

    def test_unknown_fields_keys_strings_and_nonfinite_numbers_fail_without_echo(self):
        cases = []
        for field in ('privateOwner', 'elapsedNanos'):
            rows = records(); rows[1][field] = 'secret-canary'; cases.append(rows)
        rows = records(); rows[1]['stages']['secret-canary'] = 1; cases.append(rows)
        rows = records(); rows[1]['boundaries']['counts']['db.secret-canary'] = 1; cases.append(rows)
        rows = records(); rows[1]['elapsedNanos'] = float('nan'); cases.append(rows)
        for rows in cases:
            result = parser.parse(log(rows), 36)
            self.assertEqual(result['status'], 'INVALID')
            self.assertNotIn('secret-canary', json.dumps(result))

    def test_duplicate_json_fields_and_truncated_json_are_invalid(self):
        for line in ('{"event":"BEGIN","event":"COMPLETE"}', '{"event":'):
            result = parser.parse('INSTRUMENTATION_STATUS: stream=SCALING '+line, 36)
            self.assertEqual(result['status'], 'INVALID')

    def test_capture_queue_cannot_be_fabricated_from_sync_submissions(self):
        rows = records(); rows[1]['captureOutstandingFrames'] = 0
        self.assertEqual(parser.parse(log(rows), 36)['status'], 'INVALID')


class BenchmarkCliPreservationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.input_dir = self.root/'evidence'
        self.input_dir.mkdir()
        self.source = self.input_dir/'raw.log'
        self.source.write_text(log(records()), encoding='utf-8')
        self.original = self.source.read_bytes()

    def run_cli(self, output):
        with patch('sys.argv', ['parser', '--input', str(self.source), '--output', str(output),
                                '--expected-api', '36']):
            return parser.main()

    def test_existing_output_and_hardlink_preserve_all_original_bytes(self):
        existing = self.root/'existing.json'
        existing.write_bytes(b'PRESERVE_EXISTING')
        hardlink = self.root/'hardlink.json'
        os.link(self.source, hardlink)
        for output in (existing, hardlink):
            with self.subTest(output=output):
                before = output.read_bytes()
                with self.assertRaises((ValueError, FileExistsError)):
                    self.run_cli(output)
                self.assertEqual(output.read_bytes(), before)
                self.assertEqual(self.source.read_bytes(), self.original)

    def test_input_directory_and_descendants_cannot_receive_output(self):
        for output in (self.input_dir/'new.json', self.input_dir/'nested'/'new.json'):
            with self.subTest(output=output), self.assertRaises(ValueError):
                self.run_cli(output)
            self.assertFalse(output.exists())
        self.assertEqual(self.source.read_bytes(), self.original)
        self.assertFalse((self.input_dir/'nested').exists())

    def test_directory_alias_into_raw_evidence_is_refused(self):
        alias = self.root/'alias'
        try:
            alias.symlink_to(self.input_dir, target_is_directory=True)
        except OSError:
            if os.name != 'nt':
                raise
            subprocess.run(['cmd', '/c', 'mklink', '/J', str(alias), str(self.input_dir)],
                           check=True, capture_output=True)
        with self.assertRaises(ValueError):
            self.run_cli(alias/'new.json')
        self.assertFalse((self.input_dir/'new.json').exists())
        self.assertEqual(self.source.read_bytes(), self.original)

    def test_new_external_output_is_created_once_with_same_schema(self):
        output = self.root/'public'/'new.json'
        self.assertEqual(self.run_cli(output), 0)
        emitted = output.read_bytes()
        self.assertEqual(json.loads(emitted)['status'], 'COMPLETE')
        with self.assertRaises((ValueError, FileExistsError)):
            self.run_cli(output)
        self.assertEqual(output.read_bytes(), emitted)
        self.assertEqual(self.source.read_bytes(), self.original)


if __name__ == '__main__':
    unittest.main()
