"""Synthetic negative controls for public offline latency summaries."""
import math
import json
import os
from pathlib import Path
import tempfile
import subprocess
import unittest
from unittest.mock import patch

import persistence_latency_analysis as analysis


def fixture():
    spans = [dict(startNanos=1, endNanos=1000000001, seconds=1., firstSeenSample=1),
             dict(startNanos=2000000001, endNanos=8000000001, seconds=6., firstSeenSample=1)]
    series = [dict(index=0, elapsedSeconds=0, frames=0, durableFrames=0,
                   outstandingFrames=0, stages={}, cpuTimeMs=0, vadDeadlineMisses=0),
              dict(index=1, elapsedSeconds=10, frames=180000, durableFrames=160000,
                   outstandingFrames=20000, cpuTimeMs=1000, vadDeadlineMisses=2,
                   stages={'reserve': {'seconds': 3., 'count': 1},
                           'sql_policy': {'seconds': 2., 'count': 20}})]
    raw = {'screenOffSamples': [dict(diagnostics=''), dict(diagnostics=
        'append_span start=1 end=1000000001\nappend_span start=2000000001 end=8000000001')]}
    return spans, series, raw


class StatisticsTests(unittest.TestCase):
    def test_stage_counts_reject_private_strings_bool_fraction_and_negative(self):
        for invalid in ('PRIVATE_CANARY', True, 1.5, -1, None):
            spans, samples, raw = fixture()
            samples[1]['stages']['reserve']['count'] = invalid
            with self.subTest(value=invalid), self.assertRaises(ValueError):
                analysis.analyze(spans, samples, raw, 160000)

    def test_all_exported_input_scalars_reject_wrong_types_or_nonfinite_values(self):
        mutations = [
            lambda spans, samples, raw: samples[1].update(outstandingFrames='PRIVATE_CANARY'),
            lambda spans, samples, raw: samples[1].update(index=True),
            lambda spans, samples, raw: spans[0].update(firstSeenSample=True),
            lambda spans, samples, raw: raw['screenOffSamples'][1].update(nativeHeapBytes=float('nan')),
            lambda spans, samples, raw: raw['screenOffSamples'][1].update(pssKiB='PRIVATE_CANARY'),
            lambda spans, samples, raw: raw['screenOffSamples'][1].update(fds=True),
            lambda spans, samples, raw: samples[0]['stages'].update(reserve=dict(seconds=1., count='PRIVATE_CANARY')),
            lambda spans, samples, raw: samples[0].update(cpuTimeMs='PRIVATE_CANARY'),
            lambda spans, samples, raw: samples[1]['stages']['reserve'].update(seconds=True),
        ]
        for mutate in mutations:
            data = fixture()
            mutate(*data)
            with self.subTest(mutation=mutate), self.assertRaises(ValueError):
                analysis.analyze(*data, terminal_durable_frames=160000)
        for terminal in ('PRIVATE_CANARY', True, 1.5, -1):
            with self.subTest(terminal=terminal), self.assertRaises(ValueError):
                analysis.analyze(*fixture(), terminal_durable_frames=terminal)

    def test_nearest_rank_preserves_extreme_tail_and_even_median(self):
        result = analysis.distribution(list(range(1, 20)) + [200])
        self.assertEqual(result, dict(n=20, p50=10.5, p95=19, p99=200, max=200))

    def test_empty_nonfinite_negative_values_are_rejected(self):
        for values in ([], [math.nan], [math.inf], [-1], [True]):
            with self.subTest(values=values), self.assertRaises(ValueError):
                analysis.distribution(values)

    def test_tied_rank_correlation_and_constant_undefined(self):
        result = analysis.correlations([1, 1, 2, 2], [4, 4, 3, 3])
        self.assertAlmostEqual(result['spearman'], -1.)
        self.assertIsNone(analysis.correlations([1, 1], [1, 2])['pearson'])

    def test_coverage_and_stage_join_use_latest_only(self):
        result = analysis.analyze(*fixture(), terminal_durable_frames=160000, window_size=1)
        self.assertTrue(result['coverage']['complete_completed_sequence'])
        self.assertEqual([r['append_ordinal'] for r in result['append_series']], [1, 2])
        self.assertEqual(result['sampled_stage_series'][0]['append_ordinal'], 2)
        self.assertEqual(result['slow_appends'][0]['stages_seconds']['reserve'], 3.)
        self.assertEqual(result['slow_appends'][0]['seconds'], 6.)
        self.assertEqual(len(result['windows']), 2)

    def test_missing_completed_span_does_not_invent_actual_ordinal(self):
        spans, series, raw = fixture()
        spans.pop(0)
        raw['screenOffSamples'][1]['diagnostics'] = 'append_span start=2000000001 end=8000000001'
        result = analysis.analyze(spans, series, raw, terminal_durable_frames=160000)
        self.assertFalse(result['coverage']['complete_completed_sequence'])
        self.assertIsNone(result['append_series'][0]['append_ordinal'])
        self.assertEqual(result['append_series'][0]['observed_rank'], 1)

    def test_duplicate_overlap_and_duration_mismatch_fail_closed(self):
        for mode in ('duplicate', 'overlap', 'duration'):
            spans, series, raw = fixture()
            if mode == 'duplicate':
                spans.append(spans[0].copy())
            elif mode == 'overlap':
                spans[1]['startNanos'] = 2
            else:
                spans[0]['seconds'] = 2
            with self.subTest(mode=mode), self.assertRaises(ValueError):
                analysis.analyze(spans, series, raw, terminal_durable_frames=160000)

    def test_raw_reconstruction_mismatch_is_rejected(self):
        spans, series, raw = fixture()
        raw['screenOffSamples'][1]['diagnostics'] = ''
        with self.assertRaises(ValueError):
            analysis.analyze(spans, series, raw, terminal_durable_frames=160000)

    def test_unknown_stages_and_private_fields_never_appear_in_output(self):
        spans, series, raw = fixture()
        raw['privateOwner'] = 'private-canary'
        series[1]['stages']['private-canary'] = {'seconds': 100, 'count': 1}
        result = analysis.analyze(spans, series, raw, terminal_durable_frames=160000)
        self.assertNotIn('private-canary', str(result))
        self.assertNotIn('startNanos', str(result))


class CliPreservationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.source = self.root/'input'
        self.source.mkdir()
        spans, samples, raw = fixture()
        self.inputs = {'append-spans.json': spans, 'sample-series.json': samples,
                       'analysis.json': {'terminalFrames': {'durable': 160000}, 'sourceCommit': 'a'*40},
                       'evidence/main02/dora-long-02.json': raw}
        for name, value in self.inputs.items():
            path = self.source/name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(json.dumps(value), encoding='utf-8')

    def run_cli(self, output):
        with patch('sys.argv', ['analysis', '--input-dir', str(self.source), '--output', str(output)]):
            analysis.main()

    def snapshot(self):
        return {name: (self.source/name).read_bytes() for name in self.inputs}

    def test_source_commit_must_be_exact_lowercase_sha_without_public_leak(self):
        for value in ('PRIVATE_CANARY', 'A'*40, 'a'*39, 'a'*41, True, 42):
            prior = self.inputs['analysis.json'].copy()
            prior['sourceCommit'] = value
            (self.source/'analysis.json').write_text(json.dumps(prior), encoding='utf-8')
            output = self.root/'result.json'
            with self.subTest(value=value), self.assertRaises(ValueError):
                self.run_cli(output)
            self.assertFalse(output.exists())

    def test_source_targets_nested_output_existing_outputs_and_hardlinks_are_refused(self):
        existing = self.root/'existing.json'
        existing.write_bytes(b'PRESERVE')
        hardlink = self.root/'hardlink.json'
        os.link(self.source/'append-spans.json', hardlink)
        paths = [self.source/'append-spans.json', self.source/'new.json',
                 self.source/'nested'/'new.json', existing, hardlink]
        for output in paths:
            before = self.snapshot()
            with self.subTest(output=output), self.assertRaises((ValueError, FileExistsError)):
                self.run_cli(output)
            self.assertEqual(self.snapshot(), before)
        self.assertEqual(existing.read_bytes(), b'PRESERVE')
        self.assertFalse((self.source/'new.json').exists())
        self.assertFalse((self.source/'nested').exists())

    def test_directory_alias_into_input_tree_is_refused(self):
        alias = self.root/'alias'
        try:
            alias.symlink_to(self.source, target_is_directory=True)
        except OSError:
            if os.name != 'nt':
                raise
            # A Windows directory junction exercises the same resolved-path
            # protection without requiring the symbolic-link privilege.
            subprocess.run(['cmd', '/c', 'mklink', '/J', str(alias), str(self.source)],
                           check=True, capture_output=True)
        before = self.snapshot()
        with self.assertRaises(ValueError):
            self.run_cli(alias/'new.json')
        self.assertEqual(self.snapshot(), before)
        self.assertFalse((self.source/'new.json').exists())

    def test_fresh_external_output_is_created_once_without_source_mutation(self):
        before = self.snapshot()
        output = self.root/'results'/'new.json'
        self.run_cli(output)
        self.assertEqual(json.loads(output.read_text())['source_commit'], 'a'*40)
        emitted = output.read_bytes()
        with self.assertRaises((ValueError, FileExistsError)):
            self.run_cli(output)
        self.assertEqual(output.read_bytes(), emitted)
        self.assertEqual(self.snapshot(), before)


if __name__ == '__main__':
    unittest.main()
