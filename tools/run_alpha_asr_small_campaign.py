"""SMALL operator, frozen in 5.6C.2A; CLI exposes content-free profile only.

Future owner-authorized 5.6C.2B calls campaign(config, authorization) explicitly.
No CLI config/path is opened in 5.6C.2A. All real I/O is inside that gated API.
The injectable sequence is exercised with generated observations in host tests.
"""
import datetime
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import wave

import alpha_asr_small_campaign as c
from alpha_asr_campaign import Journal, Oracle, file_sha, save_new


def strict_json(raw):
    def pairs(items):
        out = {}
        for key, value in items:
            c.require(key not in out, 'DUPLICATE_JSON_KEY'); out[key] = value
        return out
    def invalid(_): raise ValueError('NONFINITE_JSON')
    return json.loads(raw, object_pairs_hook=pairs, parse_constant=invalid)


def bound_json(raw, expected, self_field=None):
    value = strict_json(raw)
    # The materialized manifest's conventional self-digest is excluded. Other
    # documents use their whole canonical value. Both require the pinned digest.
    actual = c.digest(value)
    if actual != expected and self_field is not None and type(value) is dict and self_field in value:
        c.require(value[self_field] == expected, 'DOCUMENT_IDENTITY_MISMATCH')
        actual = c.digest({k: v for k, v in value.items() if k != self_field})
    c.require(actual == expected, 'DOCUMENT_IDENTITY_MISMATCH')
    return value


def verify_file(path, size, sha):
    c.require(Path(path).stat().st_size == size and file_sha(path) == sha, 'FILE_IDENTITY_MISMATCH')


def verify_repository(repo, expected_commit):
    def git(*args):
        p = subprocess.run(['git', '-C', str(repo), *args], capture_output=True, timeout=30)
        c.require(p.returncode == 0, 'REPOSITORY_IDENTITY_UNAVAILABLE')
        return p.stdout.decode().strip()
    c.require(git('rev-parse', 'HEAD') == expected_commit
              and git('rev-parse', 'HEAD^') == c.RECOVERY_COMMIT
              and git('branch', '--show-current') == 'chat/alpha-asr-runner-scope'
              and not git('status', '--porcelain'), 'REPOSITORY_IDENTITY_MISMATCH')
    record = strict_json((repo / 'docs/evidence/poc-asr-001/alpha-asr-small-campaign-operator-stage0-v0.1.json').read_bytes())
    c.validate_profile(record['evaluationProfile'])
    c.require(record['evaluationProfileSha256'] == c.digest(c.profile()), 'PROFILE_IDENTITY_MISMATCH')
    for name, expected in record['operatorFiles'].items():
        c.require(file_sha(repo / name) == expected['sha256'], 'OPERATOR_IDENTITY_MISMATCH')
    for name, expected in record['protectedHistoricalFiles'].items():
        c.require(file_sha(repo / name) == expected['sha256'], 'HISTORICAL_IDENTITY_MISMATCH')


def verify_inputs(config):
    """Actual future verification, before the first inference and each case.

    Config is private path binding, never authority for model/profile identities.
    The independently owner-admitted operatorCommit is a mandatory execution pin.
    """
    repo = Path(__file__).resolve().parents[1]
    verify_repository(repo, config['operatorCommit'])
    c.require(datetime.date.today() <= datetime.date(2026, 10, 25), 'RETENTION_DEADLINE_EXCEEDED')
    # Reuse the historical storage checker only; do not call BASE setup/profile.
    from run_alpha_asr_campaign import require_private
    for key in ('acceptance', 'work'):
        require_private(Path(config[key]), repo)
    root = Path(config['acceptance'])
    manifest = bound_json((root / 'materialized-manifest.json').read_bytes(), c.MANIFEST_SHA, 'manifestSha256')
    bound_json((root / 'transfer-index.json').read_bytes(), c.TRANSFER_SHA, 'aggregateIndexSha256')
    bound_json((root / 'pre-inference-freeze.json').read_bytes(), c.FREEZE_SHA)
    rows = manifest['samples']; c.validate_selection(rows)
    verify_file(config['model'], c.MODEL_BYTES, c.MODEL_SHA)
    c.require(Path(config['model']).name == c.profile()['modelArtifact'], 'MODEL_NAME_MISMATCH')
    # The same six model-independent native artifacts as historical 5.4; the
    # historical BASE model/manifest/profile is never imported as SMALL authority.
    historical = strict_json((repo / 'docs/evidence/poc-asr-001/alpha-asr-campaign-stage0-v0.1.json').read_bytes())
    native = historical['runtime']['nativeArtifacts']
    c.require(set(config['native']) == set(native), 'NATIVE_SET_MISMATCH')
    for name, pin in native.items(): verify_file(config['native'][name], pin['bytes'], pin['sha256'])
    runtime = Path(config['runtimeSource'])
    p = subprocess.run(['git', '-C', str(runtime), 'rev-parse', 'HEAD'], capture_output=True, timeout=30)
    c.require(p.returncode == 0 and p.stdout.decode().strip() == c.text.RUNTIME_COMMIT, 'RUNTIME_IDENTITY_MISMATCH')
    p = subprocess.run(['git', '-C', str(runtime), 'status', '--porcelain'], capture_output=True, timeout=30)
    c.require(p.returncode == 0 and not p.stdout.strip(), 'RUNTIME_SOURCE_DIRTY')
    decoding = bound_json((repo / 'docs/evidence/poc-asr-001/alpha-asr-decoding-profile-stage0-v0.1.json').read_bytes(), c.DECODING_SHA)
    protocol = bound_json((repo / 'docs/evidence/poc-asr-001/alpha-asr-measurement-protocol-stage0-v0.1.json').read_bytes(), c.PROTOCOL_SHA)
    governance = strict_json((repo / 'docs/evidence/poc-asr-001/alpha-asr-small-evaluation-governance-stage0-v0.1.json').read_bytes())
    c.require(c.digest(governance['resourceGates']) == c.GATES_SHA, 'RESOURCE_GATES_MISMATCH')
    c.require(file_sha(runtime / 'examples/miniaudio.h') == protocol['decoderSha256'], 'DECODER_IDENTITY_MISMATCH')
    c.require(set(config['bindings']) == {r['sampleId'] for r in rows}, 'BINDING_SET_MISMATCH')
    for row in rows:
        for kind, sha in (('audio', row['audioSha256']), ('reference', row['referenceTextSha256'])):
            path = Path(config['bindings'][row['sampleId']][kind])
            c.require(path.resolve().is_relative_to(root.resolve()) and not path.is_symlink(), 'INPUT_PATH_INVALID')
            require_private(path, repo)
            c.require(file_sha(path) == sha, 'INPUT_BINDING_MISMATCH')
        c.require(Path(config['bindings'][row['sampleId']]['audio']).stat().st_size == row['audioByteLength'], 'AUDIO_SIZE_MISMATCH')
    return rows, decoding, protocol


def failed_attempt(row):
    return {'caseId': row['sampleId'], 'locale': row['locale'], 'ordinal': 1,
            'state': 'FAILED', 'cleanup': 'UNVERIFIED', 'quality': None,
            'oomEvidence': [], 'memory': [], 'thermal': []}


def run_sequence(rows, driver, journal, verify):
    """One-shot sequencer. Journal begin is durable BEFORE each backend call.

    Any interrupted or prior attempt blocks a new campaign; the journal's older
    retry capability is deliberately not exposed. Exceptions never trigger retry.
    """
    c.validate_selection(rows)
    c.require(not journal.records(), 'EXISTING_ATTEMPTS_REQUIRE_OWNER_REVIEW')
    attempts = []; cleanup = 'UNVERIFIED'; evidence_complete = True
    try:
        verify()
        c.require(driver.warmup() == 'VERIFIED', 'WARMUP_INCOMPLETE')
        for position, row in enumerate(rows, 1):
            verify()
            journal.begin(position, row['sampleId'], row['locale'], 1,
                          {'profileSha256': c.digest(c.profile()), 'privateFreezeSha256': c.FREEZE_SHA})
            result = failed_attempt(row)
            try:
                result = driver.case(row, position)
            except Exception:
                # A driver can retain partial positive evidence without inventing
                # OOM from a generic exit. Stable public output omits exceptions.
                if hasattr(driver, 'partial'):
                    try: result = driver.partial(row, position)
                    except Exception: result = failed_attempt(row)
            attempts.append(result)
            journal.finish(position, {'state': result['state'], 'attempt': result})
            valid, failures = c.case_resources(result)
            if not valid or failures: break
            if not c.quality_valid(result): break
        verify()
    except Exception:
        # A pre-start identity/warmup failure has no measured case attempt.
        # An interrupted journal record remains retained and prohibits replay.
        evidence_complete = False
    finally:
        try: cleanup = driver.cleanup()
        except Exception: cleanup = 'UNVERIFIED'
    safety = []
    if hasattr(driver, 'safety_evidence'):
        try:
            observations = driver.safety_evidence()
            if type(observations) is list: safety = observations
        except Exception: evidence_complete = False
    return c.evaluate(c.profile(), rows, attempts, cleanup, evidence_complete, safety)


def read_attempt(destination, row, state, oracle=None, reference=None):
    """Read retained native output. Raw telemetry is evaluated, not trusted summaries."""
    result = failed_attempt(row); result['state'] = state
    p = Path(destination)
    def lines(name):
        try: return (p / name).read_bytes().decode('utf-8').splitlines()
        except (OSError, UnicodeError): return []
    for line in lines('memory.csv'):
        try: result['memory'].append(list(map(int, line.split(','))))
        except ValueError: result['memory'].append([])
    if (p / 'thermal.jsonl').is_file():
        from decimal import Decimal
        for line in lines('thermal.jsonl'):
            try:
                r = json.loads(line, parse_float=Decimal)
                result['thermal'].append([int(r['monotonicSeconds'] * 1000000), r['status']])
            except (ValueError, KeyError, TypeError, ArithmeticError): result['thermal'].append([])
        good = [r for r in result['thermal'] if len(r) == 2]
        if good:
            result['thermalWindowStartMicros'] = good[0][0]
            result['thermalWindowEndMicros'] = good[-1][0]
    if (p / 'metrics.json').is_file():
        try:
            m = strict_json((p / 'metrics.json').read_bytes())
            c.require(type(m) is dict, 'INVALID_NATIVE_METRICS')
            for target, source in (('inferenceMicros', 'inferenceElapsedMicros'),
                    ('coldLoadMicros', 'modelLoadElapsedMicros'),
                    ('windowStartMicros', 'measurementStartMicros'), ('windowEndMicros', 'measurementEndMicros')):
                if c.integer(m.get(source)): result[target] = m[source]
            if c.integer(m.get('decodedFrames')) and type(m.get('sampleRate')) is int and m['sampleRate'] == 16000:
                result['durationMicros'] = (m['decodedFrames'] * 1000000 + 8000) // 16000
            c.require(all(c.integer(m[k]) for k in ('decodedFrames', 'nativeCode', 'sampleRate',
                'inferenceElapsedMicros', 'modelLoadElapsedMicros', 'measurementStartMicros', 'measurementEndMicros'))
                and m['sampleRate'] == 16000, 'INVALID_NATIVE_METRICS')
            result.update(durationMicros=(m['decodedFrames'] * 1000000 + 8000) // 16000,
                inferenceMicros=m['inferenceElapsedMicros'], coldLoadMicros=m['modelLoadElapsedMicros'],
                windowStartMicros=m['measurementStartMicros'], windowEndMicros=m['measurementEndMicros'],
                freshProcessContext=True)
            if m['nativeCode'] != 0: result['state'] = 'FAILED'
        except (OSError, ValueError, KeyError, TypeError): result['state'] = 'FAILED'
    # Only an explicit allocation exception from this owned process is decisive.
    # Null model context, nonzero inference rc and generic signal 9 are ambiguous.
    if 'libc++abi: terminating due to uncaught exception of type std::bad_alloc: std::bad_alloc' in lines('native.stderr'):
        result['oomEvidence'] = [{'kind': 'NATIVE_ALLOCATION_FAILURE', 'sha256': file_sha(p / 'native.stderr')}]
    if (p / 'cleanup.json').is_file():
        try: result['cleanup'] = strict_json((p / 'cleanup.json').read_bytes())['status']
        except (OSError, ValueError, KeyError, TypeError): pass
    if result['state'] == 'SUCCEEDED':
        prepared = c.prepare_case(c.profile(), row, reference, (p / 'hypothesis.txt').read_bytes().decode('utf-8'))
        score = oracle.score(prepared)
        result['quality'] = {s: {'errors': sum(score[s][k] for k in ('substitutions', 'deletions', 'insertions')),
                                'referenceTokens': score[s]['referenceTokens']} for s in ('raw', 'normalized')}
        save_new(p / 'prepared-small-private.json', prepared)
    return result


class SmallDriver:
    """Future physical adapter. Construction alone performs no device operation."""
    def __init__(self, config, device, decoding, oracle):
        self.config, self.device, self.decoding, self.oracle = config, device, decoding, oracle
        self.work = Path(config['work'])
        self.safety = []

    def warmup(self):
        facts = self.device.connect()  # Historical helper verifies physical POCO M5.
        save_new(self.work / 'environment-before.json', facts)
        self.safety.append({'thermal': [[0, facts['thermalStatus']]], 'oomEvidence': []})
        c.require(facts['thermalStatus'] < 3, 'SEVERE_THERMAL_BEFORE_START')
        self.device.prepare()
        for locale in ('ru', 'en'):
            c.require(c.canonical(self.device.profile(locale)) == c.canonical(self.decoding['resolvedWhisperFullParams'][locale]), 'DEVICE_DECODING_PROFILE_MISMATCH')
        codec = self.device.execute(Path(self.config['runtimeSource']) / 'samples/jfk.mp3', 'en', self.work / 'codec', mode='decode')
        c.require(codec['metrics']['decodedFrames'] > 0 and codec['thermal']['maximum'] < 3, 'CODEC_PREFLIGHT_FAILED')
        silent = self.work / 'generated-silence.wav'
        with silent.open('xb') as raw:
            with wave.open(raw, 'wb') as audio:
                audio.setnchannels(1); audio.setsampwidth(2); audio.setframerate(16000)
                audio.writeframes(b'\0' * 96000)
        # A durable marker before any warmup invocation prevents implicit replay.
        save_new(self.work / 'warmup-started.json', {'profileSha256': c.digest(c.profile()), 'attemptOrdinal': 1})
        observation = self.device.execute(silent, 'en', self.work / 'warmup')
        c.require(observation['metrics']['decodedFrames'] == 48000 and observation['thermal']['maximum'] < 3, 'WARMUP_FAILED')
        save_new(self.work / 'warmup-terminal.json', {'state': 'SUCCEEDED', 'qualityScored': False})
        return 'VERIFIED'

    def case(self, row, position):
        binding = self.config['bindings'][row['sampleId']]
        reference = Path(binding['reference']).read_bytes()
        c.require(hashlib.sha256(reference).hexdigest() == row['referenceTextSha256'], 'REFERENCE_CHANGED')
        c.require(file_sha(binding['audio']) == row['audioSha256'], 'AUDIO_CHANGED')
        destination = self.work / 'cases' / f'{position:02d}'
        self.device.execute(Path(binding['audio']), row['locale'], destination)
        return read_attempt(destination, row, 'SUCCEEDED', self.oracle, reference.decode('utf-8'))

    def partial(self, row, position):
        return read_attempt(self.work / 'cases' / f'{position:02d}', row, 'FAILED')

    def cleanup(self):
        return self.device.cleanup()

    def safety_evidence(self):
        # Read only retained pre-corpus output, including a failed warmup/codec.
        row = {'sampleId': 'sample-0000000000000000', 'locale': 'en'}
        return self.safety + [read_attempt(self.work / name, row, 'FAILED') for name in ('codec', 'warmup')]


def campaign(config, authorization):
    """Not called in 5.6C.2A. Requires a later explicit owner execution decision."""
    c.require(authorization == 'OWNER_AUTHORIZED_5.6C.2B', 'MEASURED_EXECUTION_NOT_AUTHORIZED')
    rows, decoding, _ = verify_inputs(config)
    work = Path(config['work']); work.mkdir(parents=True, exist_ok=True)
    # Permanent one-shot marker survives interruption, including before case #1.
    save_new(work / 'campaign-started.json', {'profileSha256': c.digest(c.profile()),
             'operatorCommit': config['operatorCommit'], 'privateFreezeSha256': c.FREEZE_SHA})
    from alpha_asr_campaign_device import Device
    artifacts = {**config['native'], 'model.bin': config['model']}
    identity = c.digest({n: {'sha256': file_sha(p), 'bytes': Path(p).stat().st_size} for n, p in artifacts.items()})
    device = Device(config['adb'], artifacts, identity)
    oracle = Oracle(work / 'oracle'); oracle.compile()
    journal = Journal(work / 'attempts.sqlite')
    try:
        result = run_sequence(rows, SmallDriver(config, device, decoding, oracle), journal,
                              lambda: verify_inputs(config))
        save_new(work / 'terminal-aggregate.json', result)
        return result
    finally:
        journal.close()


def main(argv=None):
    args = sys.argv[1:] if argv is None else argv
    if args not in ([], ['profile']):
        print('{"result":"BLOCKED","code":"MEASURED_EXECUTION_REQUIRES_SEPARATE_5_6C_2B_AUTHORITY"}')
        return 2
    print(c.canonical({'evaluationProfile': c.profile(), 'canonicalSha256': c.digest(c.profile())}).decode())
    return 0


if __name__ == '__main__': sys.exit(main())
