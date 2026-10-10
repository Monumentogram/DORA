"""Validate host-saved emulator benchmark output without contacting any device.

Only fixed schema numeric fields enter public output. COMPLETE requires all five
checkpoints, authenticated/reopened completion and a subsequent one-test JUnit OK.
Exit 0 means complete, 2 incomplete, 1 invalid or failed. Raw input is never written.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import statistics


TARGETS = (10, 100, 400, 1000, 2000)
PREFIX = 'INSTRUMENTATION_STATUS: stream=SCALING '
KINDS = ('catalog_load', 'metadata_insert', 'metadata_same_row', 'append')
STAGES = ('reserve', 'bootstrap', 'publication', 'recovery', 'catalog_commit',
          'sql_policy', 'sql_commit', 'key_open', 'key_generation')
BOUNDARIES = {
    'candidate.close', 'candidate.finalExists', 'candidate.fsync', 'candidate.fsyncParent',
    'candidate.openExclusiveTemp', 'candidate.renameTempToFinal', 'candidate.write',
    'db.beginTransactionNonExclusive', 'db.compileStatement', 'db.endTransaction',
    'db.inTransaction', 'db.isOpen', 'db.isWriteAheadLoggingEnabled', 'db.query',
    'db.setTransactionSuccessful', 'statement.bindLong', 'statement.bindNull',
    'statement.bindString', 'statement.bindDouble', 'statement.bindBlob',
    'statement.close', 'statement.execute', 'statement.executeInsert',
    'statement.executeUpdateDelete', 'statement.clearBindings',
}
NUMERIC = ('blocksBefore', 'elapsedNanos', 'threadCpuNanos', 'processCpuMs',
           'javaHeapBefore', 'javaHeapAfter', 'nativeHeapBefore', 'nativeHeapAfter',
           'filesAfter', 'bytesAfter', 'databaseBytesAfter', 'walBytesAfter',
           'submittedUnreturnedFramesPeak', 'submittedUnreturnedFramesAfter')
NULLABLE = ('gcCountBefore', 'gcCountAfter')


def require(condition):
    if not condition:
        raise ValueError('Benchmark schema or event sequence mismatch')


def integer(value):
    return type(value) is int and value >= 0


def exact(row, fields):
    require(type(row) is dict and set(row) == set(fields))


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result)
        result[key] = value
    return result


def reject_constant(_):
    raise ValueError('Nonfinite JSON number')


def validate_measure(row):
    exact(row, ('event', 'kind', *NUMERIC, *NULLABLE, 'captureOutstandingFrames', 'stages', 'boundaries'))
    require(row['kind'] in KINDS)
    require(all(integer(row[k]) for k in NUMERIC))
    require(all(row[k] is None or integer(row[k]) for k in NULLABLE))
    require(row['captureOutstandingFrames'] is None)
    require(row['submittedUnreturnedFramesAfter'] == 0)
    require(row['submittedUnreturnedFramesPeak'] == (80000 if row['kind'] == 'append' else 0))
    require(type(row['stages']) is dict)
    allowed = set(STAGES) | {name+'_count' for name in STAGES}
    require(set(row['stages']) <= allowed)
    require(all(integer(v) for v in row['stages'].values()))
    require(all((name in row['stages']) == (name+'_count' in row['stages']) for name in STAGES))
    exact(row['boundaries'], ('counts', 'nanos'))
    counts, nanos = row['boundaries']['counts'], row['boundaries']['nanos']
    require(type(counts) is dict and type(nanos) is dict)
    require(set(counts) == set(nanos) and set(counts) <= BOUNDARIES)
    require(all(integer(v) for v in list(counts.values())+list(nanos.values())))


def distribution(values, divisor=1):
    values = [v/divisor for v in values]
    return dict(n=len(values), p50=statistics.median(values), max=max(values))


def summarize(target, rows):
    operations = {}
    for kind in KINDS:
        group = [r for r in rows if r['kind'] == kind]
        operations[kind] = dict(
            blocks_before=[r['blocksBefore'] for r in group],
            elapsed_ms=distribution([r['elapsedNanos'] for r in group], 1e6),
            thread_cpu_ms=distribution([r['threadCpuNanos'] for r in group], 1e6),
            process_cpu_ms=distribution([r['processCpuMs'] for r in group]),
            snapshots={name: distribution([r[name] for r in group if r[name] is not None])
                       if any(r[name] is not None for r in group) else None
                       for name in (*NUMERIC[4:], *NULLABLE)},
            stage_ms={name: distribution([r['stages'][name] for r in group if name in r['stages']], 1e6)
                      for name in STAGES if any(name in r['stages'] for r in group)},
            stage_counts={name: [r['stages'].get(name+'_count') for r in group]
                          for name in STAGES if any(name in r['stages'] for r in group)},
            boundary_counts={name: [r['boundaries']['counts'].get(name, 0) for r in group]
                             for name in sorted({k for r in group for k in r['boundaries']['counts']})},
            boundary_ms={name: distribution([r['boundaries']['nanos'].get(name, 0) for r in group], 1e6)
                         for name in sorted({k for r in group for k in r['boundaries']['nanos']})},
        )
    return dict(target=target, actual_blocks_after=target+3, operations=operations)


def parse(text, expected_api):
    require(expected_api in (28, 36))
    result = dict(schema_version=1, status='INCOMPLETE', expected_api=expected_api,
                  checkpoints=[], measurements=[], completed_blocks=None,
                  authenticated_pcm_frames=None, reopened_catalog_verified=False,
                  interpretation=[
                      'Host-saved emulator output; not POCO performance or capture admission evidence.',
                      'Three measurements per operation/target; p50 and maximum only, no tail-percentile claim.',
                      'Append measurements start with n,n+1,n+2 blocks; same-state operations use exactly n.',
                      'Stage and boundary durations are nested/inclusive and must not be summed.',
                      'db.endTransaction includes Room invalidation operations; not four application commit phases.',
                      'candidate.fsync/fsyncParent cover selected publication boundaries; not all sync calls.',
                      'Capture outstanding frames are unavailable (null); synchronous submitted frames peak at 80000 then 0.',
                      'Snapshot file/database/WAL scans occur after measured elapsed/CPU interval.',
                  ])
    started, completed, junit_after = False, False, False
    buffer, checkpoint_index, previous_progress = [], 0, 0
    failed = bool(re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|shortMsg=', text))
    try:
        for line in text.splitlines():
            line = line.lstrip('\ufeff').strip()
            if re.fullmatch(r'OK \(1 test\)', line):
                if completed:
                    junit_after = True
                continue
            if not line.startswith(PREFIX):
                continue
            require(not completed)
            row = json.loads(line[len(PREFIX):], object_pairs_hook=unique_object,
                             parse_constant=reject_constant)
            require(type(row) is dict)
            event = row.get('event')
            if event == 'BEGIN':
                exact(row, ('event', 'api', 'maximum', 'pcmBytes', 'timingScope', 'countersScope'))
                require(not started and row['api'] == expected_api and type(row['api']) is int)
                require(row['maximum'] == 2000 and row['pcmBytes'] == 160000)
                require(type(row['timingScope']) is str and type(row['countersScope']) is str)
                started = True
                result['api'] = expected_api
            else:
                require(started)
                if event == 'PROGRESS':
                    exact(row, ('event', 'blocks'))
                    require(integer(row['blocks']) and previous_progress < row['blocks'] <= 2000)
                    require(row['blocks'] % 50 == 0 and not buffer)
                    previous_progress = row['blocks']
                elif event == 'MEASURE':
                    require(checkpoint_index < len(TARGETS))
                    validate_measure(row)
                    target = TARGETS[checkpoint_index]
                    sequence = [('catalog_load', target)]*3 + [pair for _ in range(3) for pair in
                        [('metadata_insert', target), ('metadata_same_row', target)]] + [
                            ('append', target+i) for i in range(3)]
                    require(len(buffer) < len(sequence))
                    require((row['kind'], row['blocksBefore']) == sequence[len(buffer)])
                    buffer.append(row)
                    result['measurements'].append(row)
                elif event == 'CHECKPOINT':
                    exact(row, ('event', 'target', 'actualBlocks'))
                    require(checkpoint_index < len(TARGETS))
                    target = TARGETS[checkpoint_index]
                    require(row['target'] == target and row['actualBlocks'] == target+3 and len(buffer) == 12)
                    result['checkpoints'].append(summarize(target, buffer))
                    checkpoint_index += 1
                    buffer = []
                elif event == 'COMPLETE':
                    exact(row, ('event', 'blocks', 'authenticatedPcmFrames', 'reopenedCatalogVerified'))
                    require(checkpoint_index == len(TARGETS) and not buffer)
                    require(row['blocks'] == 2003 and row['authenticatedPcmFrames'] == 160240000
                            and row['reopenedCatalogVerified'] is True)
                    completed = True
                    result.update(completed_blocks=2003, authenticated_pcm_frames=160240000,
                                  reopened_catalog_verified=True)
                else:
                    raise ValueError('Unknown event')
    except (ValueError, TypeError, KeyError):
        result['status'] = 'INVALID'
        result['reason'] = 'Invalid schema, JSON, API, or event order; unsafe content omitted.'
        return result
    if failed:
        result['status'] = 'FAILED'
        result['reason'] = 'Instrumentation failure marker present.'
    elif completed and junit_after:
        result['status'] = 'COMPLETE'
    else:
        result['reason'] = 'Missing full completion, all checkpoints, or subsequent one-test JUnit success.'
    if result['checkpoints']:
        baseline = result['checkpoints'][0]['operations']
        result['elapsed_median_ratio_to_10'] = {
            str(check['target']): {kind: check['operations'][kind]['elapsed_ms']['p50'] /
                                 baseline[kind]['elapsed_ms']['p50']
                                 if baseline[kind]['elapsed_ms']['p50'] else None for kind in KINDS}
            for check in result['checkpoints']}
    return result


def new_destination(input_path, output_path):
    """Keep output outside the raw evidence tree and never replace any existing name."""
    source_dirs = {input_path.parent.resolve(strict=True), input_path.resolve(strict=True).parent}
    destination = output_path.resolve()
    if any(destination == source or source in destination.parents for source in source_dirs):
        raise ValueError('Output must be outside the input evidence directory tree')
    if output_path.exists() or output_path.is_symlink() or destination.exists():
        raise FileExistsError('Output must be a new file')
    return destination


def main():
    command = argparse.ArgumentParser(description=__doc__)
    command.add_argument('--input', required=True, type=Path)
    command.add_argument('--output', required=True, type=Path)
    command.add_argument('--expected-api', required=True, type=int, choices=(28, 36))
    args = command.parse_args()
    destination = new_destination(args.input, args.output)
    raw = args.input.read_bytes()
    result = parse(raw.decode('utf-8-sig'), args.expected_api)
    result['input_sha256'] = hashlib.sha256(raw).hexdigest()
    serialized = json.dumps(result, indent=2, allow_nan=False)+'\n'
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination = new_destination(args.input, args.output)
    # Exclusive creation also refuses a hardlink/file introduced after validation.
    with destination.open('x', encoding='utf-8') as output:
        output.write(serialized)
    print(f"{result['status']}: {len(result['checkpoints'])}/5 validated checkpoints")
    return 0 if result['status'] == 'COMPLETE' else 2 if result['status'] == 'INCOMPLETE' else 1


if __name__ == '__main__':
    raise SystemExit(main())
