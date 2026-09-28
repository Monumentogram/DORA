"""USB-only transfer for the separate test APK. Never activates or records implicitly.

Archives contain PRIVATE voice/text. Run from the prepared host, never in CI.
This CLI prints only stable outcomes and whole-file hashes, not device identifiers.
"""
import argparse
import hashlib
import os
from pathlib import Path
import re
import subprocess
import uuid

PACKAGE = 'com.monumentogram.dora.stage0.ownedcorpus'
LIMIT = 128 * 1024 * 1024


def require(condition, code):
    if not condition:
        raise ValueError(code)


def digest(data):
    return hashlib.sha256(data).hexdigest()


class Device:
    def __init__(self, adb, serial):
        require(re.fullmatch(r'[A-Za-z0-9._:-]+', serial) is not None, 'INVALID_DEVICE_SELECTOR')
        self.prefix = [str(adb), '-s', serial]

    def call(self, args, data=None, *, allow_failure=False):
        environment = dict(os.environ)
        if 'ANDROID_USER_HOME' not in environment and 'USERPROFILE' in environment:
            environment['ANDROID_USER_HOME'] = str(Path(environment['USERPROFILE']) / '.android')
        result = subprocess.run(self.prefix + args, input=data, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, timeout=120, env=environment)
        require(allow_failure or result.returncode == 0, 'DEVICE_OPERATION_FAILED')
        return result

    def private(self, *args, data=None, allow_failure=False):
        # Windows adb shell translates output LF to CRLF even without a PTY.
        # Use raw exec for payloads, shell v2 separately for reliable existence/status.
        if args[0] == 'cat' and data is None:
            exists = self.call(['shell', '-T', 'run-as', PACKAGE, 'test', '-f', args[1]],
                               allow_failure=True)
            if exists.returncode != 0:
                require(allow_failure, 'PRIVATE_DEVICE_FILE_UNAVAILABLE')
                return exists
            return self.call(['exec-out', 'run-as', PACKAGE, *args])
        if data is not None:
            return self.call(['exec-in', 'run-as', PACKAGE, *args], data,
                             allow_failure=allow_failure)
        return self.call(['shell', '-T', 'run-as', PACKAGE, *args],
                         allow_failure=allow_failure)

    def install(self, apk, expected_sha256):
        data = Path(apk).read_bytes()
        require(digest(data) == expected_sha256, 'APK_HASH_MISMATCH')
        result = self.call(['install', '-r', str(Path(apk).resolve())])
        require(b'Success' in result.stdout, 'APK_INSTALL_NOT_CONFIRMED')

    def put(self, source, name):
        require(name in ('seed.zip', 'activation.json'), 'UNSUPPORTED_PRIVATE_INPUT')
        data = Path(source).read_bytes()
        require(0 < len(data) <= LIMIT, 'PRIVATE_PACKAGE_SIZE')
        value = digest(data)
        target = 'files/inbox/' + name
        self.private('mkdir', '-p', 'files/inbox')
        existing = self.private('cat', target, allow_failure=True)
        if existing.returncode == 0:
            require(existing.stdout == data, 'EXISTING_INPUT_CONFLICT_PRESERVED')
            return value
        # A fresh attempt preserves interrupted USB bytes and permits a safe retry.
        # Only a fully checked file is published at the immutable input name.
        temporary = f'files/inbox/incoming-{value}-{uuid.uuid4().hex}'
        self.private('tee', temporary, data=data)
        require(self.private('cat', temporary).stdout == data, 'DEVICE_TRANSFER_HASH_MISMATCH')
        self.private('mv', '-n', temporary, target)
        require(self.private('cat', target).stdout == data, 'DEVICE_INPUT_NOT_BOUND')
        return value

    def collect(self, destination_root):
        root = Path(destination_root).resolve()
        require(not any((p / '.git').exists() for p in (root, *root.parents)), 'PRIVATE_ROOT_INSIDE_GIT')
        data = self.private('cat', 'files/export/mobile-export.zip').stdout
        require(0 < len(data) <= LIMIT, 'PRIVATE_PACKAGE_SIZE')
        value = digest(data)
        root.mkdir(parents=True, exist_ok=True)
        path = root / (value + '.zip')
        if path.exists():
            require(path.read_bytes() == data, 'IMMUTABLE_EXPORT_CONFLICT')
        else:
            temporary = root / ('incoming-' + uuid.uuid4().hex + '.partial')
            with temporary.open('xb') as output:
                output.write(data)
                output.flush()
                os.fsync(output.fileno())
            require(temporary.read_bytes() == data, 'LOCAL_TRANSFER_HASH_MISMATCH')
            try:
                os.link(temporary, path)  # Atomic publication; never replaces an existing file.
            except FileExistsError:
                require(path.read_bytes() == data, 'IMMUTABLE_EXPORT_CONFLICT')
            temporary.unlink()
        return value


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True, type=Path)
    parser.add_argument('--serial', required=True)
    commands = parser.add_subparsers(dest='command', required=True)
    install = commands.add_parser('install')
    install.add_argument('--apk', type=Path, required=True)
    install.add_argument('--sha256', required=True)
    seed = commands.add_parser('seed')
    seed.add_argument('--archive', type=Path, required=True)
    activation = commands.add_parser('activate')
    activation.add_argument('--file', type=Path, required=True)
    activation.add_argument('--owner-ready', action='store_true')
    collect = commands.add_parser('collect')
    collect.add_argument('--private-output', type=Path, required=True)
    args = parser.parse_args()
    device = Device(args.adb, args.serial)
    if args.command == 'install':
        device.install(args.apk, args.sha256)
        print('SEPARATE_TEST_APK_INSTALLED; installation did not request microphone permission')
    elif args.command == 'seed':
        print('PRIVATE_SEED_TRANSFERRED_SHA256=' + device.put(args.archive, 'seed.zip'))
    elif args.command == 'activate':
        require(args.owner_ready, 'EXPLICIT_OWNER_READINESS_REQUIRED')
        print('PRIVATE_ACTIVATION_TRANSFERRED_SHA256=' + device.put(args.file, 'activation.json'))
    else:
        print('PRIVATE_EXPORT_COLLECTED_SHA256=' + device.collect(args.private_output))


if __name__ == '__main__':
    main()
