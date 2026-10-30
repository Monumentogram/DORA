"""Isolated public v8 crash diagnostic, never a recording or release qualification."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = 'com.monumentogram.dora.alpha.preview'
ACTIVITY = 'com.monumentogram.dora.MainActivity'
APK_URL = ('https://github.com/Monumentogram/DORA/releases/download/'
           'personal-alpha-20261030-v8/DORA-owner-alpha-v8.apk')
APK_SHA256 = 'd4a8f29b1ae914f33f54fa777c5976098ad823d6f1ef615e51f180d9124e8110'
CERT_SHA256 = 'a5f865ce5d28056429b323ede48f53e9ae8ad06880ea20ed242c2a2ba274603d'
PERMISSION_PACKAGE = 'com.google.android.permissioncontroller'
UI_PATH = '/data/local/tmp/dora-public-v8-baseline-ui.xml'
LABELS = {
    'new_recording': ('New recording', 'Новая запись'),
    'participant_consent': ('I have informed the participants about recording',
                            'Я предупредил(а) участников о записи'),
    'notifications': ('Allow notifications', 'Разрешить уведомления'),
    'start': ('Start recording', 'Начать запись'),
    'notification_allow': ('Allow', 'Разрешить'),
}


class BaselineFailure(RuntimeError):
    """Only fixed, content-free failure reasons reach CI output."""


def require(condition, reason):
    if not condition:
        raise BaselineFailure(reason)


def command(arguments, *, timeout=30):
    try:
        result = subprocess.run(arguments, capture_output=True, text=True, timeout=timeout, check=True)
    except (OSError, subprocess.SubprocessError):
        raise BaselineFailure('EXTERNAL_COMMAND_FAILED_OR_TIMED_OUT') from None
    require(len(result.stdout) <= 2_097_152, 'COMMAND_OUTPUT_TOO_LARGE')
    return result.stdout.replace('\r\n', '\n').strip()


def digest(path):
    result = hashlib.sha256()
    with path.open('rb') as source:
        for block in iter(lambda: source.read(65_536), b''):
            result.update(block)
    return result.hexdigest()


def audit_identity(badging, signer):
    identities = re.findall(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.M)
    require(identities == [(PACKAGE, '8', '0.1.0-owner-preview.8')], 'WRONG_PUBLIC_V8_IDENTITY')
    pins = re.findall(r'^Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})$', signer, re.M)
    require([pin.lower() for pin in pins] == [CERT_SHA256], 'WRONG_PUBLIC_OWNER_SIGNER')


def bounds(node, width, height):
    match = re.fullmatch(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]', node.get('bounds', ''))
    if not match:
        return None
    left, top, right, bottom = map(int, match.groups())
    return (left, top, right, bottom) if 0 <= left < right <= width and 0 <= top < bottom <= height else None


def click_target(xml, labels, package, width, height):
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        raise BaselineFailure('MALFORMED_UI_XML') from None
    parents = {child: parent for parent in root.iter() for child in parent}
    candidates = {}
    for node in root.iter('node'):
        if node.get('text') not in labels and node.get('content-desc') not in labels:
            continue
        target = node
        while target is not None and target.get('clickable') != 'true':
            target = parents.get(target)
        if target is None:
            continue
        chain, current = [], node
        while current is not None and current.tag == 'node':
            chain.append(current)
            if current is target:
                break
            current = parents.get(current)
        if any(item.get('package') != package or item.get('enabled') != 'true'
               or item.get('visible-to-user', 'true') != 'true' for item in chain):
            continue
        rectangle = bounds(target, width, height)
        if rectangle:
            left, top, right, bottom = rectangle
            candidates[target] = ((left + right) // 2, (top + bottom) // 2)
    require(len(candidates) == 1, 'VISIBLE_CONTROL_MISSING_OR_AMBIGUOUS')
    return next(iter(candidates.values()))


def crash_summary(text):
    require(len(text) <= 2_097_152, 'CRASH_BUFFER_TOO_LARGE')
    dora, matching, categories, signals = False, False, set(), set()
    frames, abis, guest_architectures, faults, startup = [], set(), set(), set(), set()
    for block in re.split(r'(?=^.*(?:\*\*\* \*\*\* \*\*\*|FATAL EXCEPTION:).*$)', text, flags=re.M):
        process = re.search(r'(?:Cmdline:\s*' + re.escape(PACKAGE) + r'(?:\s|$)|Process:\s*'
                            + re.escape(PACKAGE) + r',\s*PID:\s*\d+|>>>\s*'
                            + re.escape(PACKAGE) + r'\s*<<<)', block)
        if not process:
            continue
        dora = True
        stack = '\n'.join(line for line in block.splitlines() if re.search(r'#\d+\s|\bat\s', line))
        ort = bool(re.search(r'libonnxruntime|onnxruntime::|ai\.onnxruntime\.Ort', stack))
        vad = bool(re.search(r'libsherpa[-_]onnx|SherpaVad|com\.monumentogram\.dora\.vad\.', stack))
        vad_thread = bool(re.search(r'name:\s*(?:Dora VAD infere|Dora VAD inference)\s+>>>\s*'
                                    + re.escape(PACKAGE) + r'\s*<<<', block))
        if ort:
            categories.add('ORT_STACK')
        if vad:
            categories.add('VAD_STACK')
        if vad_thread:
            categories.add('VAD_THREAD')
        matching |= ort and (vad or vad_thread)
        signals.update(re.findall(r'signal \d+ \((SIG(?:ABRT|SEGV|BUS|ILL|FPE|TRAP))\)', block))
        abis.update(re.findall(r"ABI:\s*'(arm64|arm|x86_64|x86)'", block))
        guest_architectures.update(re.findall(r"Guest architecture:\s*'(arm64|arm|x86_64|x86)'", block))
        faults.update(hex(int(value, 16)) for value in re.findall(r'fault addr (0x[a-fA-F0-9]{1,16})\b', block))
        for marker, label in (('pthread_once', 'PTHREAD_ONCE'), ('call_constructors', 'CALL_CONSTRUCTORS'),
                              ('native_bridge|NativeBridge|libndk_translation', 'NATIVE_BRIDGE')):
            if re.search(marker, stack):
                startup.add(label)
        for index, pc in re.findall(r'#(\d{1,3})\s+pc\s+([a-fA-F0-9]{1,16})\s+\S*[!/]libonnxruntime\.so\b', stack):
            require(len(frames) < 128, 'TOO_MANY_NATIVE_CRASH_FRAMES')
            frames.append(dict(index=int(index), pc=hex(int(pc, 16)), library='libonnxruntime.so'))
    observation = ('DORA_ORT_VAD_CRASH_OBSERVED' if matching else
                   'DORA_OTHER_CRASH_OBSERVED' if dora else 'NO_MATCHING_CRASH_OBSERVED')
    return dict(observation=observation, dora_process_crash=dora,
                stack_categories=sorted(categories), signals=sorted(signals), ort_frames=frames,
                guest_crash_abis=sorted(abis), fault_addresses=sorted(faults),
                guest_architectures=sorted(guest_architectures),
                native_startup_markers=sorted(startup),
                observed_crash_buffer_sha256=hashlib.sha256(text.encode('utf-8')).hexdigest())


def run_baseline(apk, sdk, serial, receipt, *, observe_seconds=30):
    require(re.fullmatch(r'emulator-\d+', serial), 'EMULATOR_SERIAL_REQUIRED')
    require(5 <= observe_seconds <= 60, 'INVALID_DIAGNOSTIC_OBSERVATION_BOUND')
    require(not receipt.exists(), 'RECEIPT_ALREADY_EXISTS')
    require(digest(apk) == APK_SHA256, 'PUBLIC_APK_HASH_MISMATCH')
    suffix = '.exe' if os.name == 'nt' else ''
    tools = sdk / 'build-tools/36.0.0'
    badging = command([str(tools / ('aapt' + suffix)), 'dump', 'badging', str(apk)])
    signer = command([str(tools / ('apksigner.bat' if os.name == 'nt' else 'apksigner')),
                      'verify', '--print-certs', str(apk)])
    audit_identity(badging, signer)
    adb = str(sdk / 'platform-tools' / ('adb' + suffix))

    def shell(*args, timeout=30):
        return command([adb, '-s', serial, *args], timeout=timeout)

    require(shell('shell', 'getprop', 'ro.kernel.qemu') == '1'
            and shell('shell', 'getprop', 'ro.build.version.sdk') == '36'
            and shell('shell', 'getprop', 'ro.product.cpu.abi') == 'x86_64', 'WRONG_EMULATOR_IDENTITY')
    # Include retained/uninstalled package records; never replace an earlier installation.
    require(shell('shell', 'pm', 'list', 'packages', '-u', PACKAGE) == '', 'PACKAGE_ALREADY_PRESENT')
    fingerprint = shell('shell', 'getprop', 'ro.build.fingerprint')
    abilist = shell('shell', 'getprop', 'ro.product.cpu.abilist')
    bridge = shell('shell', 'getprop', 'ro.dalvik.vm.native.bridge')
    page = shell('shell', 'getconf', 'PAGE_SIZE')
    cpus = re.findall(r'^processor\s*:\s*(\d+)\s*$', shell('shell', 'cat', '/proc/cpuinfo'), re.M)
    memory = re.findall(r'^MemTotal:\s*(\d+) kB$', shell('shell', 'cat', '/proc/meminfo'), re.M)
    require(re.fullmatch(r'[A-Za-z0-9_./:;,+ =-]{1,256}', fingerprint)
            and re.fullmatch(r'[A-Za-z0-9_-]+(?:,[A-Za-z0-9_-]+)*', abilist)
            and re.fullmatch(r'[A-Za-z0-9_.-]{0,128}', bridge) and page in {'4096', '16384'}
            and len(cpus) == len(set(cpus)) and 1 <= len(cpus) <= 256
            and len(memory) == 1 and 1 <= int(memory[0]) <= 1_073_741_824, 'MALFORMED_RUNTIME_ENVIRONMENT')
    environment = dict(host_os=platform.system(), host_architecture=platform.machine(),
                       guest_build_fingerprint=fingerprint, guest_abilist=abilist, native_bridge=bridge,
                       runtime_page_size=int(page), guest_cpu_count=len(cpus), guest_ram_kib=int(memory[0]),
                       cross_host_equivalence='NOT_CLAIMED')
    owned, failure = False, None
    result = dict(schema_version=1, status='DIAGNOSTIC_INCOMPLETE', package=PACKAGE,
                  version_code=8, version_name='0.1.0-owner-preview.8', apk_sha256=APK_SHA256,
                  certificate_sha256=CERT_SHA256, artifact_url=APK_URL, api=36, abi='x86_64',
                  environment=environment,
                  installed_fresh=False, visible_actions=[], observation_seconds=observe_seconds,
                  recording_qualification='NOT_CLAIMED', release_gate='NOT_CLAIMED', physical_device=False)
    try:
        require(shell('install', str(apk.resolve()), timeout=120).endswith('Success'), 'FRESH_INSTALL_FAILED')
        owned = True
        result['installed_fresh'] = True
        installed = shell('shell', 'dumpsys', 'package', PACKAGE)
        require(re.search(r'\bversionCode=8\b', installed)
                and re.search(r'\bversionName=0\.1\.0-owner-preview\.8(?:\s|$)', installed),
                'INSTALLED_VERSION_MISMATCH')
        paths = re.findall(r'^package:(/data/app/[A-Za-z0-9_./=+~-]+/base\.apk)$',
                           shell('shell', 'pm', 'path', PACKAGE), re.M)
        require(len(paths) == 1, 'INSTALLED_APK_PATH_AMBIGUOUS')
        installed_hash = shell('shell', 'sha256sum', paths[0]).split()
        require(installed_hash == [APK_SHA256, paths[0]], 'INSTALLED_APK_HASH_MISMATCH')
        size = re.fullmatch(r'Physical size: (\d+)x(\d+)', shell('shell', 'wm', 'size'))
        require(size is not None, 'DISPLAY_BOUNDS_UNAVAILABLE')
        width, height = map(int, size.groups())
        shell('shell', 'pm', 'grant', PACKAGE, 'android.permission.RECORD_AUDIO')
        # Crash-only buffer is cleared before this newly installed, exact package is launched.
        shell('logcat', '-b', 'crash', '-c')
        shell('shell', 'am', 'start', '-W', '-n', PACKAGE + '/' + ACTIVITY)

        def click(action, package=PACKAGE):
            deadline = time.monotonic() + 30
            scrolls = 0
            while time.monotonic() < deadline:
                shell('shell', 'uiautomator', 'dump', UI_PATH)
                xml = shell('exec-out', 'cat', UI_PATH)
                try:
                    x, y = click_target(xml, LABELS[action], package, width, height)
                except BaselineFailure as error:
                    if str(error) != 'VISIBLE_CONTROL_MISSING_OR_AMBIGUOUS':
                        raise
                    # Only bounded scrolling inside an actually visible app scroll container.
                    root = ET.fromstring(xml)
                    containers = [bounds(node, width, height) for node in root.iter('node')
                                  if node.get('package') == PACKAGE and node.get('scrollable') == 'true'
                                  and node.get('enabled') == 'true']
                    containers = [rectangle for rectangle in containers if rectangle]
                    if len(containers) == 1 and scrolls < 4 and package == PACKAGE:
                        left, top, right, bottom = containers[0]
                        x, span = (left + right) // 2, bottom - top
                        shell('shell', 'input', 'swipe', str(x), str(top + span * 3 // 4),
                              str(x), str(top + span // 4), '300')
                        scrolls += 1
                    time.sleep(0.5)
                    continue
                shell('shell', 'input', 'tap', str(x), str(y))
                result['visible_actions'].append(action)
                return
            raise BaselineFailure('VISIBLE_CONTROL_TIMEOUT_' + action.upper())

        click('new_recording')
        click('participant_consent')
        click('notifications')
        click('notification_allow', PERMISSION_PACKAGE)
        click('start')
        time.sleep(observe_seconds)
        result['status'] = 'DIAGNOSTIC_COMPLETE'
    except BaselineFailure as error:
        failure = str(error)
    finally:
        if owned:
            try:
                result.update(crash_summary(shell('logcat', '-b', 'crash', '-d', '-t', '400', '-v', 'brief')))
                pids = shell('shell', 'pidof ' + PACKAGE + ' || true')
                require(not pids or re.fullmatch(r'\d+(?: \d+)*', pids), 'MALFORMED_PROCESS_IDENTITY')
                result['process_alive_before_cleanup'] = bool(pids)
                if not pids and result['observation'] == 'NO_MATCHING_CRASH_OBSERVED':
                    result['observation'] = 'PROCESS_EXITED_WITHOUT_MATCHING_CRASH'
            except BaselineFailure:
                result['status'] = 'DIAGNOSTIC_INCOMPLETE'
                failure = failure or 'CRASH_OR_PROCESS_OBSERVATION_UNAVAILABLE'
            try:
                shell('shell', 'am', 'force-stop', PACKAGE)
                shell('shell', 'rm', '-f', UI_PATH)
                result['owned_package_force_stopped'] = True
            except BaselineFailure:
                result['status'] = 'DIAGNOSTIC_INCOMPLETE'
                failure = failure or 'OWNED_PACKAGE_CLEANUP_FAILED'
        if failure:
            result['failure'] = failure
        receipt.parent.mkdir(parents=True, exist_ok=True)
        with receipt.open('x', encoding='utf-8') as output:
            output.write(json.dumps(result, sort_keys=True, indent=2) + '\n')
        # Deliberately categorical: no raw logcat, XML, screenshots, audio or transcript artifacts.
        summary = {key: value for key, value in result.items() if key in
                   {'status', 'observation', 'stack_categories', 'signals', 'failure', 'visible_actions',
                    'environment', 'ort_frames', 'guest_crash_abis', 'guest_architectures', 'fault_addresses', 'native_startup_markers',
                    'observed_crash_buffer_sha256'}}
        with receipt.with_suffix('.summary.json').open('x', encoding='utf-8') as output:
            output.write(json.dumps(summary, sort_keys=True, indent=2) + '\n')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--receipt', type=Path, required=True)
    parser.add_argument('--sdk', type=Path, default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
    args = parser.parse_args()
    require(args.sdk is not None, 'ANDROID_SDK_REQUIRED')
    try:
        result = run_baseline(args.apk, Path(args.sdk), args.serial, args.receipt)
    except BaselineFailure as error:
        print(str(error))
        return 1
    print(result['status'] + ': ' + result.get('observation', 'OBSERVATION_UNAVAILABLE'))
    return 0 if result['status'] == 'DIAGNOSTIC_COMPLETE' else 1


if __name__ == '__main__':
    raise SystemExit(main())
