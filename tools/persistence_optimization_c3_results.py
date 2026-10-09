"""Strict, content-free Stage8.6C3 receipt parser and preregistered comparison."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import statistics

PREFIX = 'INSTRUMENTATION_STATUS: stream=C3 '
TARGETS = (10, 400, 1000, 2000)
TRANSACTIONS = ['reservation', 'bootstrap', 'publication', 'catalogCommit']
STAGES = ['reserve', 'bootstrap', 'publication', 'recovery', 'catalog_commit']
ALL_STAGES = STAGES + ['sql_policy', 'sql_commit', 'key_open', 'key_generation']
NUMERIC = ('blocksBefore', 'elapsedNanos', 'threadCpuNanos', 'processCpuMs',
           'fullCatalogLoads', 'candidateFsyncCount', 'candidateParentFsyncCount')


def require(value):
    if not value:
        raise ValueError('C3 schema or sequence mismatch')


def integer(value):
    return type(value) is int and value >= 0


def exact(row, fields):
    require(type(row) is dict and set(row) == set(fields))


def unique(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result)
        result[key] = value
    return result


def reject_constant(_):
    raise ValueError('Nonfinite JSON')


def parse(text, expected_api, variant, source_sha256):
    require(expected_api in (28, 36) and variant in ('baseline', 'optimized'))
    require(re.fullmatch('[0-9a-f]{64}', source_sha256) is not None)
    result = dict(schema_version=1, status='INCOMPLETE', api=expected_api, variant=variant,
                  source_sha256=source_sha256, measurements=[], checkpoints=[])
    started = completed = junit = False
    maximum, checkpoint, buffered, progress = None, 0, 0, 0
    failed = bool(re.search(r'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|shortMsg=', text))
    try:
        for line in text.splitlines():
            line = line.lstrip('\ufeff').strip()
            if re.fullmatch(r'OK \(1 test\)', line):
                junit = completed
                continue
            if not line.startswith(PREFIX):
                continue
            require(not completed)
            row = json.loads(line[len(PREFIX):], object_pairs_hook=unique, parse_constant=reject_constant)
            require(type(row) is dict)
            event = row.get('event')
            if event == 'BEGIN':
                exact(row, ('event','api','maximum','variant','sourceSha256','pcmBytes','groupBlocks'))
                require(not started and type(row['api']) is int and row['api'] == expected_api)
                require(row['variant'] == variant and row['sourceSha256'] == source_sha256)
                require(type(row['maximum']) is int and row['maximum'] in TARGETS)
                require(row['pcmBytes'] == 160000 and row['groupBlocks'] == 120)
                maximum = row['maximum']; started = True
            else:
                require(started)
                if event == 'PROGRESS':
                    exact(row, ('event','blocks'))
                    require(integer(row['blocks']) and progress < row['blocks'] <= maximum)
                    require(row['blocks'] % 50 == 0 and buffered == 0)
                    progress = row['blocks']
                elif event == 'MEASURE':
                    exact(row, ('event','kind',*NUMERIC,'transactionOrder','stageOrder','stages'))
                    require(checkpoint < len(TARGETS) and TARGETS[checkpoint] <= maximum)
                    n = TARGETS[checkpoint]
                    sequence = [('catalog_load',n)]*3 + [('append',n+i) for i in range(7)]
                    require(buffered < len(sequence))
                    require((row['kind'],row['blocksBefore']) == sequence[buffered])
                    require(all(integer(row[key]) for key in NUMERIC))
                    append = row['kind'] == 'append'
                    require(row['fullCatalogLoads'] == ((5 if variant == 'baseline' else 4) if append else 1))
                    require(row['transactionOrder'] == (TRANSACTIONS if append else []))
                    require(row['stageOrder'] == (STAGES if append else []))
                    stages = row['stages']
                    require(type(stages) is dict and set(stages) <= set(ALL_STAGES + [s+'_count' for s in ALL_STAGES]))
                    require(all(integer(v) for v in stages.values()))
                    require(all((s in stages) == (s+'_count' in stages) for s in ALL_STAGES))
                    if append:
                        require(all(stages.get(s+'_count') == 1 for s in STAGES))
                        require(stages.get('sql_commit_count') == 2)
                        require(row['candidateFsyncCount'] > 0 and row['candidateParentFsyncCount'] > 0)
                    else:
                        require(row['candidateFsyncCount'] == row['candidateParentFsyncCount'] == 0)
                    # Only validated, fixed-schema observations enter a public receipt.
                    result['measurements'].append(row)
                    buffered += 1
                elif event == 'CHECKPOINT':
                    exact(row, ('event','target','actualBlocks'))
                    require(checkpoint < len(TARGETS) and buffered == 10)
                    n = TARGETS[checkpoint]
                    require(row['target'] == n and row['actualBlocks'] == n+7)
                    result['checkpoints'].append(n)
                    checkpoint += 1; buffered = 0
                elif event == 'COMPLETE':
                    exact(row, ('event','blocks','authenticatedPcmFrames','reopenedAuthenticatedPcmFrames','reopenedCatalogVerified'))
                    require(checkpoint == len([n for n in TARGETS if n <= maximum]) and buffered == 0)
                    require(row['blocks'] == maximum+7)
                    require(row['authenticatedPcmFrames'] == row['reopenedAuthenticatedPcmFrames'] == (maximum+7)*80000)
                    require(row['reopenedCatalogVerified'] is True)
                    result['completion'] = row
                    completed = True
                else:
                    require(False)
        if failed:
            result['status'] = 'FAILED'
        elif completed and junit and maximum == 2000:
            result['status'] = 'COMPLETE'
    except (ValueError, TypeError, KeyError):
        result['status'] = 'INVALID'
    return result


def distribution(rows, field):
    values = [r[field] for r in rows]
    return dict(values=values, median=statistics.median(values), maximum=max(values))


def compare(receipts):
    """Use validated receipts. Caller retains APK/input provenance beside this summary."""
    result = dict(schema_version=1, status='INCOMPLETE', comparisons=[])
    expected = {(api,v) for api in (28,36) for v in ('baseline','optimized')}
    indexed = {(r.get('api'),r.get('variant')): r for r in receipts}
    if len(receipts) != 4 or set(indexed) != expected or any(r.get('status') != 'COMPLETE' for r in receipts):
        return result
    useful = True
    for api in (28,36):
        for target in TARGETS:
            for kind in ('append','catalog_load'):
                groups = {}
                for variant in ('baseline','optimized'):
                    rows = indexed[(api,variant)]['measurements']
                    rows = [r for r in rows if r['kind'] == kind and target <= r['blocksBefore'] <= target+(6 if kind == 'append' else 0)]
                    require(len(rows) == (7 if kind == 'append' else 3))
                    groups[variant] = rows
                base, opt = groups['baseline'], groups['optimized']
                bw = statistics.median(r['elapsedNanos'] for r in base)
                ow = statistics.median(r['elapsedNanos'] for r in opt)
                bc = statistics.median(r['threadCpuNanos'] for r in base)
                oc = statistics.median(r['threadCpuNanos'] for r in opt)
                wall_ok = ow <= bw + max(bw * 0.10, 10_000_000)
                cpu_ok = kind != 'append' or target < 1000 or (bc > 0 and oc <= bc * 0.90)
                sync_ok = all([r[k] for r in base] == [r[k] for r in opt] for k in ('candidateFsyncCount','candidateParentFsyncCount'))
                useful &= wall_ok and cpu_ok and sync_ok
                result['comparisons'].append(dict(api=api,target=target,kind=kind,
                    baseline_wall=distribution(base,'elapsedNanos'), optimized_wall=distribution(opt,'elapsedNanos'),
                    baseline_cpu=distribution(base,'threadCpuNanos'), optimized_cpu=distribution(opt,'threadCpuNanos'),
                    cpu_reduction_fraction=(1-oc/bc) if bc else None, wall_gate=wall_ok,cpu_gate=cpu_ok,sync_gate=sync_ok))
    result['status'] = 'USEFUL' if useful else 'NOT_MET'
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--input', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--expected-api', type=int, required=True, choices=(28,36))
    parser.add_argument('--variant', required=True, choices=('baseline','optimized'))
    parser.add_argument('--source-sha256', required=True)
    args = parser.parse_args()
    require(args.output.resolve() != args.input.resolve())
    raw = args.input.read_bytes()
    result = parse(raw.decode('utf-8',errors='replace'),args.expected_api,args.variant,args.source_sha256)
    result['input_sha256'] = hashlib.sha256(raw).hexdigest()
    with args.output.open('x',encoding='utf-8') as stream:
        json.dump(result,stream,indent=2,allow_nan=False)
        stream.write('\n')
    print(result['status'])
    return 0 if result['status'] == 'COMPLETE' else 1


if __name__ == '__main__':
    raise SystemExit(main())
