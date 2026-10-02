"""Host-driven abrupt-process-death verification for synthetic encrypted persistence."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import queue
import re
import subprocess
import threading
import time
import sys

from run_encrypted_persistence_device import RUNNER, measure_page_sizes, parse_results, require

TEST = 'com.monumentogram.dora.audio.persistence.EncryptedAudioProcessDeathTest#verifyProcessDeathRecovery'
ORIGINAL_AUDIO_TEST = 'com.monumentogram.dora.audio.persistence.OriginalAudioProcessDeathTest#verifyOriginalAudioProcessDeath'
PHASES = ('INTENT', 'PUBLISHED', 'COMMITTED')
PACKAGE = RUNNER.split('/')[0]
MARKER = re.compile(r'^INSTRUMENTATION_STATUS: stream=DORA_PERSISTENCE_(READY|VERIFIED):'
                    r'(INTENT|PUBLISHED|COMMITTED):([1-9][0-9]*)\nINSTRUMENTATION_STATUS_CODE: 2$', re.MULTILINE)


def phase_marker(output, phase, kind):
    markers = list(MARKER.finditer(output))
    require(phase in PHASES and len(markers) == 1 and output.count('DORA_PERSISTENCE_') == 1,
            'Missing or ambiguous process phase marker')
    marker = markers[0]
    require(marker[1] == kind and marker[2] == phase, 'Unexpected process phase marker')
    return int(marker[3]), MARKER.sub('', output)


def ready_pid(output, phase):
    require('INSTRUMENTATION_CODE:' not in output, 'Prepare process completed before termination')
    return phase_marker(output, phase, 'READY')[0]


def verified_pid(output, phase, previous_pid, *, expected_test=TEST):
    pid, normal = phase_marker(output, phase, 'VERIFIED')
    require(pid != previous_pid, 'Verification did not execute in a fresh process')
    require(expected_test in (TEST, ORIGINAL_AUDIO_TEST), 'Unadmitted process test')
    require(parse_results(normal) == [{'name': expected_test, 'result': 'PASS'}],
            'Exact process-death verification test did not pass')
    return pid


def validate_death(before, after, pid):
    require(before.strip() == str(pid) and not after.strip(), 'Exact process death was not observed')


def wait_ready(process, phase, timeout=90):
    lines = queue.Queue()

    def read():
        for line in process.stdout:
            lines.put(line)
        lines.put(None)

    threading.Thread(target=read, daemon=True).start()
    deadline, captured = time.monotonic() + timeout, ''
    while True:
        remaining = deadline - time.monotonic()
        require(remaining > 0, 'Timed out before durable process phase')
        try:
            line = lines.get(timeout=remaining)
        except queue.Empty:
            raise ValueError('Timed out before durable process phase') from None
        require(line is not None, 'Prepare process ended before durable process phase')
        captured += line
        require(len(captured) < 131072, 'Unexpected instrumentation output volume')
        if MARKER.search(captured):
            return ready_pid(captured, phase)


def cleanup_process(process, force_stop, *, timeout=20, primary=None):
    try:
        try:
            force_stop()
        finally:
            if process.poll() is None:
                try:
                    process.wait(timeout=timeout)
                except subprocess.TimeoutExpired:
                    process.kill()
                    process.wait(timeout=5)
    except Exception:
        if primary is None:
            raise
        primary.add_note('Synthetic process cleanup also failed; no acceptance receipt was written.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--expected-api', type=int, required=True)
    parser.add_argument('--expected-page-size', type=int, choices=(4096, 16384), default=4096)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--receipt', type=Path, required=True)
    parser.add_argument('--original-audio', action='store_true')
    args = parser.parse_args()
    selected_test = ORIGINAL_AUDIO_TEST if args.original_audio else TEST
    require(re.fullmatch(r'emulator-\d+', args.serial), 'Only an explicitly selected emulator is admitted')
    require(not args.receipt.exists(), 'Receipt already exists; choose a new attempt path')
    driver_hash = hashlib.sha256(Path(__file__).read_bytes()).hexdigest()
    apk_hash = hashlib.sha256(args.apk.read_bytes()).hexdigest()
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    require(bool(sdk), 'Android SDK path is required')
    adb = [str(Path(sdk) / 'platform-tools' / ('adb.exe' if os.name == 'nt' else 'adb')), '-s', args.serial]

    def command(*arguments, timeout=120, absent=False):
        result = subprocess.run(adb + list(arguments), capture_output=True, text=True, timeout=timeout)
        if result.returncode and arguments == ('shell', 'am', 'force-stop', PACKAGE):
            # This command has no content payload; retain its OS diagnostic for failed campaigns.
            raise ValueError('Synthetic force-stop failed: ' + result.stderr.strip()[:256])
        require(result.returncode == 0 or (absent and result.returncode == 1 and not result.stdout.strip()),
                'Emulator command failed; no process-death acceptance')
        return result.stdout.replace('\r\n', '\n').strip()

    def instrument(phase, action):
        return ['shell', 'am', 'instrument', '-w', '-r', '-e', 'class', selected_test,
                '-e', 'persistenceCrashPhase', phase, '-e', 'persistenceCrashAction', action, RUNNER]

    require(command('get-state') == 'device' and command('shell', 'getprop', 'ro.kernel.qemu') == '1',
            'Selected target is not an online emulator')
    api = int(command('shell', 'getprop', 'ro.build.version.sdk'))
    abi = command('shell', 'getprop', 'ro.product.cpu.abi')
    page_measurement = measure_page_sizes(command, api, abi)
    page_size = page_measurement['page_size']
    require((api, page_size) == (args.expected_api, args.expected_page_size), 'Runtime identity mismatch')
    require(command('install', '-r', str(args.apk.resolve())).endswith('Success'), 'Test APK installation failed')
    require(command('shell', 'pm', 'clear', PACKAGE) == 'Success', 'Synthetic test package reset failed')
    rows = []
    for phase in PHASES:
        started = time.monotonic()
        process = subprocess.Popen(adb + instrument(phase, 'PREPARE'), stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, text=True, bufsize=1)
        try:
            pid = wait_ready(process, phase)
            before = command('shell', 'pidof', PACKAGE, absent=True)
            require(before == str(pid), 'Ready marker does not identify the live synthetic test process')
            require(process.poll() is None, 'Prepare instrumentation completed before termination')
            command('shell', 'am', 'force-stop', PACKAGE)
            deadline = time.monotonic() + 10
            after = command('shell', 'pidof', PACKAGE, absent=True)
            while after and time.monotonic() < deadline:
                time.sleep(0.05)
                after = command('shell', 'pidof', PACKAGE, absent=True)
            validate_death(before, after, pid)
            # The device holds the phase for 120 seconds. Refuse a death observed after expiry.
            require(time.monotonic() - started < 110, 'Termination exceeded the prepared phase hold window')
            process.wait(timeout=20)
            output = command(*instrument(phase, 'VERIFY'))
            fresh_pid = verified_pid(output, phase, pid, expected_test=selected_test)
            rows.append({'phase': phase, 'result': 'PASS', 'prepared_pid': pid, 'verifier_pid': fresh_pid,
                         'termination': 'AM_FORCE_STOP_WITH_PROCESS_ABSENCE_VERIFIED'})
            print('PASS abrupt process phase ' + phase, flush=True)
        finally:
            cleanup_process(process, lambda: command('shell', 'am', 'force-stop', PACKAGE),
                            primary=sys.exc_info()[1])
    receipt = {'schema_version': 1, 'status': 'PASS_COMPONENT_PROCESS_DEATH_ONLY',
               'test': selected_test,
               'api': api, 'abi': abi, **page_measurement, 'phases': rows,
               'apk_sha256': apk_hash,
               'driver_sha256': driver_hash,
               'physical_device': False, 'stage_acceptance': 'NOT_CLAIMED'}
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    args.receipt.write_text(json.dumps(receipt, sort_keys=True, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
