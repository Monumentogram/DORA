"""Read-only LONG-02 analysis; emit allowlisted, content-free statistics.

The input directory is private and never copied into the output. No Android/device
access. Percentiles use nearest rank, except p50 uses the ordinary median.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import statistics


STAGES = ('reserve', 'bootstrap', 'publication', 'recovery', 'catalog_commit',
          'sql_policy', 'sql_commit', 'key_open', 'key_generation')
METRICS = ('pssKiB', 'nativeHeapBytes', 'javaHeapBytes', 'threads', 'fds', 'rssKiB')
SPAN_PATTERN = re.compile(r'append_span start=(\d+) end=(\d+)')


def require_integer(value):
    if type(value) is not int or value < 0:
        raise ValueError('Expected nonnegative integer')


def require_number(value):
    if type(value) not in (int, float) or not math.isfinite(value) or value < 0:
        raise ValueError('Expected finite nonnegative number')


def validate_scalars(spans, samples, raw, terminal_durable_frames):
    """Validate every input scalar copied to, or used to calculate, public fields."""
    require_integer(terminal_durable_frames)
    for span in spans:
        for field in ('startNanos', 'endNanos', 'firstSeenSample'):
            require_integer(span[field])
        require_number(span['seconds'])
    for sample in samples:
        for field in ('index', 'frames', 'durableFrames', 'outstandingFrames', 'cpuTimeMs', 'vadDeadlineMisses'):
            require_integer(sample[field])
        require_number(sample['elapsedSeconds'])
        if type(sample['stages']) is not dict:
            raise ValueError('Expected stage mapping')
        for name in STAGES:
            if name in sample['stages']:
                stage = sample['stages'][name]
                if type(stage) is not dict:
                    raise ValueError('Expected stage measurement')
                require_number(stage['seconds'])
                require_integer(stage['count'])
    for original in raw['screenOffSamples']:
        if type(original['diagnostics']) is not str:
            raise ValueError('Expected diagnostic text')
        for field in METRICS:
            if field in original:
                require_integer(original[field])


def distribution(values):
    if not values or any(isinstance(x, bool) or not isinstance(x, (int, float))
                         or not math.isfinite(x) or x < 0 for x in values):
        raise ValueError('Expected nonempty finite nonnegative numeric values')
    ordered = sorted(values)
    return dict(n=len(values), p50=statistics.median(ordered),
                p95=ordered[math.ceil(.95 * len(ordered)) - 1],
                p99=ordered[math.ceil(.99 * len(ordered)) - 1], max=ordered[-1])


def ranks(values):
    ordered = sorted(range(len(values)), key=values.__getitem__)
    result = [0.] * len(values)
    start = 0
    while start < len(values):
        end = start + 1
        while end < len(values) and values[ordered[end]] == values[ordered[start]]:
            end += 1
        for index in ordered[start:end]:
            result[index] = (start + 1 + end) / 2
        start = end
    return result


def correlations(xs, ys):
    if len(xs) != len(ys):
        raise ValueError('Unpaired correlation')
    def pearson(a, b):
        if len(a) < 2 or len(set(a)) < 2 or len(set(b)) < 2:
            return None
        da = [v - statistics.mean(a) for v in a]
        db = [v - statistics.mean(b) for v in b]
        return sum(x*y for x, y in zip(da, db)) / math.sqrt(
            sum(x*x for x in da) * sum(y*y for y in db))
    return dict(n=len(xs), pearson=pearson(xs, ys), spearman=pearson(ranks(xs), ranks(ys)))


def analyze(spans, samples, raw, terminal_durable_frames, window_size=50, block_frames=80000):
    require_integer(window_size)
    require_integer(block_frames)
    if window_size <= 0 or block_frames <= 0 or not spans or not samples:
        raise ValueError('Positive window/block size and nonempty input required')
    validate_scalars(spans, samples, raw, terminal_durable_frames)
    spans = sorted(spans, key=lambda item: item['startNanos'])
    keys = [(s['startNanos'], s['endNanos']) for s in spans]
    if len(set(keys)) != len(keys):
        raise ValueError('Duplicate append span')
    for i, span in enumerate(spans):
        if span['endNanos'] <= span['startNanos'] or (
                i and span['startNanos'] < spans[i-1]['endNanos']):
            raise ValueError('Negative or overlapping append span')
        if not math.isclose(span['seconds'], (span['endNanos']-span['startNanos'])/1e9,
                            rel_tol=0, abs_tol=1e-9):
            raise ValueError('Span duration disagrees with endpoints')
    distribution([s['seconds'] for s in spans])
    if [s['index'] for s in samples] != list(range(len(samples))):
        raise ValueError('Noncontinuous sample indices')
    raw_samples = raw['screenOffSamples']
    if len(raw_samples) != len(samples):
        raise ValueError('Raw/derived sample counts differ')
    seen, first_seen, latest = set(), {}, {}
    sample_checks = []
    for sample, original in zip(samples, raw_samples):
        index = sample['index']
        current = {tuple(map(int, match)) for match in SPAN_PATTERN.findall(original['diagnostics'])}
        for key in current:
            first_seen.setdefault(key, index)
        seen.update(current)
        if current:
            latest[index] = max(current)
        durable = sample['durableFrames']
        sample_checks.append(dict(sample_index=index, recovered=len(seen),
                                  expected=durable/block_frames,
                                  agrees=durable == len(seen)*block_frames))
    if seen != set(keys) or any(first_seen[key] != span['firstSeenSample']
                               for key, span in zip(keys, spans)):
        raise ValueError('Derived spans/first observation disagree with raw diagnostics')
    complete = (samples[0]['durableFrames'] == 0 and all(c['agrees'] for c in sample_checks)
                and terminal_durable_frames == len(spans)*block_frames)
    lookup = {key: i + 1 for i, key in enumerate(keys)}
    rows = [dict(observed_rank=i+1, append_ordinal=i+1 if complete else None,
                 seconds=s['seconds'], first_seen_sample=s['firstSeenSample'])
            for i, s in enumerate(spans)]
    stage_rows, metric_rows = [], []
    for sample, original in zip(samples, raw_samples):
        index = sample['index']
        if index not in latest:
            continue
        rank = lookup[latest[index]]
        previous = samples[index-1] if index else sample
        seconds_delta = sample['elapsedSeconds'] - previous['elapsedSeconds']
        metrics = {k: original[k] for k in METRICS
                   if isinstance(original.get(k), (int, float)) and not isinstance(original[k], bool)}
        metrics.update(outstanding_frames=sample['outstandingFrames'],
                       durable_block_proxy=sample['durableFrames']/block_frames,
                       vad_deadline_miss_delta=sample['vadDeadlineMisses']-previous['vadDeadlineMisses'])
        if seconds_delta > 0:
            metrics['process_cpu_ms_per_second'] = (sample['cpuTimeMs']-previous['cpuTimeMs'])/seconds_delta
        metric_rows.append(dict(sample_index=index, observed_rank=rank,
                                append_seconds=spans[rank-1]['seconds'], metrics=metrics))
        stages = {name: sample['stages'][name]['seconds'] for name in STAGES if name in sample['stages']}
        if stages:
            distribution(list(stages.values()))
            stage_rows.append(dict(sample_index=index, observed_rank=rank,
                                   append_ordinal=rank if complete else None,
                                   append_seconds=spans[rank-1]['seconds'],
                                   stages_seconds=stages,
                                   counts={name: sample['stages'][name]['count'] for name in stages}))
    windows = []
    for start in range(0, len(spans), window_size):
        end = min(start+window_size, len(spans))
        selected = [r for r in stage_rows if start < r['observed_rank'] <= end]
        windows.append(dict(observed_rank_range=[start+1, end],
                            append_seconds=distribution([s['seconds'] for s in spans[start:end]]),
                            sampled_stages_seconds={name: distribution([r['stages_seconds'][name]
                                for r in selected if name in r['stages_seconds']])
                                for name in STAGES if any(name in r['stages_seconds'] for r in selected)}))
    slow = []
    for row in rows:
        if row['seconds'] <= 5:
            continue
        stage = next((r for r in stage_rows if r['observed_rank'] == row['observed_rank']), None)
        slow.append(dict(**row, stages_seconds=stage['stages_seconds'] if stage else None,
                         stage_sample_index=stage['sample_index'] if stage else None,
                         previous_gap_seconds=(spans[row['observed_rank']-1]['startNanos']-
                             spans[row['observed_rank']-2]['endNanos'])/1e9 if row['observed_rank'] > 1 else None))
    metric_correlations = {}
    for name in sorted({name for row in metric_rows for name in row['metrics']}):
        pairs = [row for row in metric_rows if name in row['metrics']]
        metric_correlations[name] = correlations([r['metrics'][name] for r in pairs],
                                                 [r['append_seconds'] for r in pairs])
    stage_correlations = {}
    for name in STAGES:
        selected = [r for r in stage_rows if name in r['stages_seconds']]
        stage_correlations[name] = correlations([r['observed_rank'] for r in selected],
                                                [r['stages_seconds'][name] for r in selected])
    normal = [r for r in rows if r['seconds'] <= 5]
    return dict(schema_version=1, root_cause='ROOT_CAUSE_NOT_PROVEN',
                percentile_method='p50 arithmetic median; p95/p99 nearest rank',
                coverage=dict(complete_completed_sequence=complete,
                              ordinal_basis='conditional on serialized fixed 80000-frame successful appends',
                              sample_checks=sample_checks, unique_spans=len(spans),
                              terminal_durable_frames=terminal_durable_frames,
                              sampled_stage_count=len(stage_rows),
                              latest_stage_pairing='same diagnostic snapshot; separate reads, not atomic'),
                overall_append_seconds=distribution([r['seconds'] for r in rows]),
                windows=windows, append_series=rows, sampled_stage_series=stage_rows,
                slow_appends=slow,
                ordinal_append_correlation=correlations([r['observed_rank'] for r in rows], [r['seconds'] for r in rows]),
                ordinal_append_correlation_excluding_over_5s=correlations(
                    [r['observed_rank'] for r in normal], [r['seconds'] for r in normal]),
                ordinal_sampled_stage_correlations=stage_correlations,
                sample_latest_append_metric_correlations=metric_correlations,
                sampled_metrics=metric_rows,
                sampled_stage_count_values={name: sorted({r['counts'][name] for r in stage_rows if name in r['counts']})
                                            for name in STAGES},
                missing_measurements=['per-append total catalog/journal entry count including prior recordings',
                    'per-append encrypted file count', 'SQLCipher database/WAL size over time',
                    'metadata lookup count', 'fsync count/duration', 'outstanding async operation count',
                    'per-stage timings for unsampled append spans', 'completed duration of final in-flight append'],
                interpretation=['Observed rank is always available; append ordinal requires complete coverage.',
                    'Ordinal and durable blocks are catalog-growth proxies, not measured global catalog size.',
                    'Correlations do not identify causes; time, growth and workload are confounded.',
                    'Stage timers are inclusive/nested and include scheduling; never sum them as independent costs.',
                    'Snapshot outstanding frames are not outstanding async operations or per-append peak backlog.',
                    'CPU is process-wide interval average, not persistence CPU; memory metrics are snapshots.',
                    'Small window p99 often equals maximum; no confidence or causal significance is claimed.'])


def destination_outside_source(input_dir, output):
    """Resolve directory aliases and refuse all existing names, including hardlinks."""
    source = input_dir.resolve(strict=True)
    destination = output.resolve()
    if destination == source or source in destination.parents:
        raise ValueError('Output must be outside the input directory tree')
    # is_symlink catches dangling aliases; open(mode=x) also closes the ordinary
    # exists/write race and refuses any link/file created after this validation.
    if output.exists() or output.is_symlink() or destination.exists():
        raise FileExistsError('Output must be a new file')
    return destination


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input-dir', required=True, type=Path)
    parser.add_argument('--output', required=True, type=Path)
    args = parser.parse_args()
    destination = destination_outside_source(args.input_dir, args.output)
    names = ('append-spans.json', 'sample-series.json', 'analysis.json', 'evidence/main02/dora-long-02.json')
    payloads = [(args.input_dir/name).read_bytes() for name in names]
    spans, samples, prior, raw = [json.loads(data.decode('utf-8-sig')) for data in payloads]
    source_commit = prior['sourceCommit']
    if type(source_commit) is not str or re.fullmatch(r'[0-9a-f]{40}', source_commit) is None:
        raise ValueError('Expected exact lowercase 40-hex source commit')
    result = analyze(spans, samples, raw, prior['terminalFrames']['durable'])
    result['source_commit'] = source_commit
    result['input_sha256'] = {name: hashlib.sha256(data).hexdigest() for name, data in zip(names, payloads)}
    serialized = json.dumps(result, indent=2, allow_nan=False)+'\n'
    destination.parent.mkdir(parents=True, exist_ok=True)
    # Recheck resolution after directory creation. Exclusive creation never
    # truncates an existing output or a hardlink alias to historical evidence.
    destination = destination_outside_source(args.input_dir, args.output)
    with destination.open('x', encoding='utf-8') as output:
        output.write(serialized)
    print(f"Analyzed {len(spans)} spans; complete coverage: {result['coverage']['complete_completed_sequence']}")


if __name__ == '__main__':
    main()
