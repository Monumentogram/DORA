"""Bounded allowlisted instrumentation progress; never persists command arguments or raw logs."""
from contextlib import contextmanager
import json
from pathlib import Path
import queue
import re
import subprocess
import threading
import time


class InstrumentationFailure(RuntimeError):
    """Only a fixed content-free reason, never subprocess arguments/output."""


def require_complete(executed, expected):
    if len(executed) != len(set(executed)) or set(executed) != set(expected):
        raise ValueError('INVENTORY_INCOMPLETE_OR_DUPLICATED')


def class_batches(expected):
    groups = {}
    for name in sorted(expected):
        if not re.fullmatch(r'[A-Za-z0-9_.$]+#[A-Za-z0-9_$]+', name):
            raise ValueError('INVALID_INVENTORY_IDENTITY')
        groups.setdefault(name.split('#')[0], []).append(name)
    return list(groups.values())


class Progress:
    def __init__(self, expected):
        self.expected = set(expected)
        self.classes = {name.split('#')[0] for name in expected}
        self.methods = {name.split('#')[1] for name in expected}
        self.current = {}
        self.last_started = self.last_completed = None
        self.completed = []
        self.events = 0
        self.suppressed = 0

    def feed(self, line):
        match = re.fullmatch(r'INSTRUMENTATION_STATUS: (class|test)=(.+)', line)
        if match:
            key, value = match.groups()
            if value not in (self.classes if key == 'class' else self.methods):
                raise ValueError('UNEXPECTED_TEST_IDENTITY')
            self.current[key] = value
            return line
        match = re.fullmatch(r'INSTRUMENTATION_STATUS_CODE: (-?\d+)', line)
        if match:
            if set(self.current) != {'class', 'test'}:
                raise ValueError('INCOMPLETE_TEST_IDENTITY')
            name = self.current['class'] + '#' + self.current['test']
            if name not in self.expected:
                raise ValueError('UNEXPECTED_TEST_IDENTITY')
            code = int(match[1])
            if code == 1:
                self.last_started = name
            elif code in (0, -1, -2, -3, -4):
                if name in self.completed:
                    raise ValueError('DUPLICATE_COMPLETION')
                self.last_completed = name
                self.completed.append(name)
            else:
                raise ValueError('UNEXPECTED_STATUS_CODE')
            self.current = {}
            self.events += 1
            return line
        if re.fullmatch(r'OK \(\d+ tests?\)|INSTRUMENTATION_CODE: -?\d+|FAILURES!!!', line):
            return line
        if line.startswith('INSTRUMENTATION_FAILED'):
            return 'INSTRUMENTATION_FAILED: REDACTED'
        self.suppressed += 1
        return None

    def snapshot(self):
        return {'lastStartedTest': self.last_started, 'lastCompletedTest': self.last_completed,
                'completedCount': len(self.completed), 'completedTests': list(self.completed),
                'statusEvents': self.events, 'suppressedLineCount': self.suppressed}


@contextmanager
def synthetic_credential(command, pin):
    setup_failure = None
    try:
        setup = command('shell', 'locksettings', 'set-pin', pin)
    except Exception:
        setup_failure = 'CREDENTIAL_SETUP_FAILED'
    else:
        if 'Pin set to' not in setup:
            setup_failure = 'CREDENTIAL_SETUP_NOT_CONFIRMED'
    try:
        if setup_failure:
            raise InstrumentationFailure(setup_failure)
        yield
    finally:
        try:
            cleared = command('shell', 'locksettings', 'clear', '--old', pin)
            if 'Lock credential cleared' not in cleared:
                raise ValueError('not confirmed')
        except Exception:
            raise InstrumentationFailure('CREDENTIAL_CLEANUP_FAILED') from None


def stream(arguments, expected, receipt, *, hard_timeout=1800, heartbeat=30,
           inactivity=120, probe=None, capture=None, echo=True):
    """Full-suite deadline unchanged. Inactivity captures evidence; it does not skip/abort a test."""
    receipt = Path(receipt)
    receipt.parent.mkdir(parents=True, exist_ok=True)
    if receipt.exists() or receipt.with_suffix('.jsonl').exists():
        raise InstrumentationFailure('REFUSE_OVERWRITE_DIAGNOSTICS')
    state = Progress(expected)
    lines = queue.Queue(maxsize=256)
    results = queue.Queue()
    started = time.monotonic()
    last_progress = started
    next_heartbeat = started
    last_captured = None
    inflight = False
    health = {'emulatorOnline': None, 'instrumentationPidAlive': None}
    captures, safe_output = [], []
    log_bytes = 0
    outcome = 'RUNNING'
    cleanup_failure = None
    process = None
    stopped = threading.Event()
    readers = []

    def enqueue(item):
        while not stopped.is_set():
            try:
                lines.put(item, timeout=.1)
                return
            except queue.Full:
                continue

    def reader(pipe, origin):
        try:
            while True:
                data = pipe.readline(4096)
                if not data: break
                enqueue((origin, data.decode('utf-8', errors='replace').rstrip('\r\n')))
        finally:
            enqueue((origin, None))
            pipe.close()

    def background(stalled, test_at_request):
        try:
            result = {'health': probe() if probe else health,
                      'capture': capture() if stalled and capture else None}
        except Exception:
            result = {'health': {'emulatorOnline': None, 'instrumentationPidAlive': None},
                      'capture': {'status': 'DIAGNOSTIC_PROBE_FAILED'} if stalled else None}
        result['testAtRequest'] = test_at_request
        results.put(result)

    with receipt.with_suffix('.jsonl').open('x', encoding='utf-8') as log:
        def publish(kind):
            nonlocal log_bytes
            row = {**state.snapshot(), 'event': kind, 'elapsedSeconds': round(time.monotonic()-started, 3),
                   'health': health, 'outcome': outcome}
            # Full completed inventory lives in atomic receipt; heartbeat stays small.
            row.pop('completedTests')
            payload = json.dumps(row, sort_keys=True)+'\n'
            if log_bytes + len(payload.encode()) > 1024*1024:
                raise InstrumentationFailure('DIAGNOSTIC_LOG_LIMIT')
            log.write(payload); log.flush(); log_bytes += len(payload.encode())
            if echo: print(payload, end='', flush=True)
            full = {**state.snapshot(), 'elapsedSeconds': row['elapsedSeconds'], 'health': health,
                    'outcome': outcome, 'stallCaptures': captures,
                    'cleanupFailure': cleanup_failure,
                    'hardTimeoutSeconds': hard_timeout, 'inactivityCaptureSeconds': inactivity,
                    'rawOutputPublished': False}
            temp = receipt.with_suffix('.tmp')
            temp.write_text(json.dumps(full, indent=2)+'\n', encoding='utf-8')
            temp.replace(receipt)

        try:
            process = subprocess.Popen(arguments, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
            for origin, pipe in [('stdout', process.stdout), ('stderr', process.stderr)]:
                thread = threading.Thread(target=reader, args=(pipe, origin), daemon=True)
                readers.append(thread); thread.start()
            ended = set()
            while True:
                now = time.monotonic()
                while not results.empty():
                    result = results.get_nowait(); inflight = False
                    health = result['health']
                    if result['capture'] is not None:
                        captures.append({'elapsedSeconds': round(now-started, 3),
                                         'lastStartedTestAtRequest': result['testAtRequest'],
                                         'lastStartedTestAtReturn': state.last_started, **result['capture']})
                if now-started >= hard_timeout:
                    outcome = 'HARD_TIMEOUT'
                    raise InstrumentationFailure(outcome)
                if now >= next_heartbeat:
                    stalled = now-last_progress >= inactivity and last_captured != state.events
                    if not inflight:
                        inflight = True
                        if stalled: last_captured = state.events
                        threading.Thread(target=background, args=(stalled, state.last_started), daemon=True).start()
                    publish('HEARTBEAT'); next_heartbeat = now+heartbeat
                try:
                    origin, line = lines.get(timeout=min(.1, max(.001, hard_timeout-(now-started))))
                except queue.Empty:
                    continue
                if line is None:
                    ended.add(origin)
                elif origin == 'stdout':
                    events = state.events
                    safe = state.feed(line)
                    if safe is not None:
                        safe_output.append(safe)
                        if sum(map(len, safe_output)) > 512*1024:
                            raise InstrumentationFailure('SAFE_OUTPUT_LIMIT')
                    if state.events != events:
                        last_progress = time.monotonic(); publish('TEST_PROGRESS')
                else:
                    state.suppressed += 1
                if len(ended) == 2 and lines.empty():
                    code = process.wait(timeout=2)
                    if code != 0:
                        outcome = 'PROCESS_FAILED'
                        raise InstrumentationFailure(outcome)
                    outcome = 'PROCESS_EXITED'
                    break
        except InstrumentationFailure:
            if outcome == 'RUNNING': outcome = 'DIAGNOSTIC_FAILURE'
            raise
        except Exception:
            outcome = 'DIAGNOSTIC_FAILURE'
            raise InstrumentationFailure(outcome) from None
        finally:
            try:
                if process is not None and process.poll() is None:
                    process.kill(); process.wait(timeout=5)
            except Exception:
                cleanup_failure = 'PROCESS_REAP_FAILED'
            stopped.set()
            try:
                for thread in readers: thread.join(timeout=1)
                if process is not None:
                    for pipe, thread in zip((process.stdout, process.stderr), readers):
                        if thread.is_alive():
                            cleanup_failure = cleanup_failure or 'PIPE_READER_STILL_ALIVE'
                        else:
                            pipe.close()
            except Exception:
                cleanup_failure = cleanup_failure or 'PIPE_CLEANUP_FAILED'
            try:
                publish('TERMINAL')
            except Exception:
                raise InstrumentationFailure('TERMINAL_RECEIPT_WRITE_FAILED') from None
            if cleanup_failure:
                raise InstrumentationFailure(cleanup_failure) from None
    return '\n'.join(safe_output)+'\n'


def emulator_diagnostics(adb, serial, *, stack=False):
    """Verified synthetic emulator only. Broad device output never leaves this reduction."""
    if not re.fullmatch(r'emulator-\d+', serial):
        raise ValueError('ONLY_TEST_EMULATOR')
    package = 'com.monumentogram.dora.audio.test'

    def query(*args):
        try:
            result = subprocess.run([adb, '-s', serial, *args], capture_output=True,
                                    timeout=3, text=True)
            return result.stdout[:128*1024] if result.returncode == 0 else None
        except Exception:
            return None

    online = query('get-state')
    pids = query('shell', 'pidof '+package+' || true')
    identifiers = [int(p) for p in (pids or '').split() if p.isdecimal()]
    result = {'emulatorOnline': online.strip() == 'device' if online is not None else None,
              'instrumentationPidAlive': bool(identifiers) if pids is not None else None,
              'instrumentationPids': identifiers}
    if not stack: return result
    frames, threads = [], []
    for pid in identifiers[:2]:
        ps = query('shell', 'ps', '-T', '-p', str(pid), '-o', 'PID,TID,STAT,NAME')
        for line in (ps or '').splitlines()[1:]:
            parts = line.split()
            if len(parts) == 4 and parts[0].isdigit() and parts[1].isdigit() and re.fullmatch(r'[A-Za-z+<N]+', parts[2]):
                threads.append({'pid': int(parts[0]), 'tid': int(parts[1]), 'state': parts[2]})
        signal = query('shell', 'run-as', package, 'kill', '-3', str(pid))
        result['javaStackSignalCommandSucceeded'] = signal is not None
        logs = query('logcat', '-d', '-t', '400', '--pid='+str(pid))
        for line in (logs or '').splitlines():
            match = re.search(r'\bat ([A-Za-z0-9_.$]+)\(', line)
            if match: frames.append(match[1])
    activity = query('shell', 'dumpsys', 'activity', 'processes', package)
    # logcat is a bounded historical buffer, not a proven fresh SIGQUIT block.
    # Never attribute these frames to the current stalled method.
    result.update(threadStates=threads[:128], unattributedHistoricalJavaFrames=frames[:128],
                  javaStackAttribution='UNPROVEN_NOT_HANG_CAUSE_EVIDENCE',
                  activityProcessMentioned=package in activity if activity is not None else None,
                  rawPsActivityAndLogcatPublished=False)
    return result
