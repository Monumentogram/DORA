"""Read-only future-install preflight. This tool cannot install, switch or remove users."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

PACKAGE = 'com.monumentogram.dora.alpha.test'
HISTORICAL_PACKAGES = ('com.monumentogram.dora', 'com.monumentogram.dora.debug')


def require(condition, reason):
    if not condition:
        raise ValueError('STOP / ' + reason)


def secondary_user(dump: str, user_id: int, serial_number: int) -> dict:
    require(user_id != 0, 'OWNER_USER_SELECTED')
    require(user_id > 0 and serial_number > 0, 'INVALID_USER_IDENTITY')
    rows = list(re.finditer(r'^\s*UserInfo\{(\d+):[^\r\n]*:([0-9a-fA-F]+)\}'
                            r'\s+serialNo=(\d+)[^\r\n]*', dump, re.MULTILINE))
    matches = [(index, match) for index, match in enumerate(rows) if int(match[1]) == user_id]
    require(len(matches) == 1, 'USER_NOT_UNIQUELY_PRESENT')
    index, match = matches[0]
    block = dump[match.end():rows[index + 1].start() if index + 1 < len(rows) else len(dump)]
    require(int(match[3]) == serial_number, 'USER_SERIAL_MISMATCH')
    # dumpsys emits these lifecycle fields independently from the numeric flags.
    # Contrary selected-user evidence must reject even a nominal 0x410 secondary.
    require(not re.search(r'<\s*(?:partial|removing|pre-created)\s*>',
                          match[0] + block, re.IGNORECASE), 'UNSAFE_USER_LIFECYCLE')
    # Conservative physical API34 policy: only initialized, non-admin full secondary.
    # Do not infer a full user from a profile's screen, label or current-user value.
    require(int(match[2], 16) == 0x410, 'UNSAFE_USER_FLAGS')
    kinds = re.findall(r'^\s*Type:\s*(\S+)\s*$', block, re.MULTILINE)
    require(kinds == ['android.os.usertype.full.SECONDARY'], 'FULL_SECONDARY_NOT_PROVEN')
    return {'user_id': user_id, 'serial_number': serial_number}


def preflight(command, user_id: int, serial_number: int) -> dict:
    require(user_id != 0, 'OWNER_USER_SELECTED')  # Before even get-state.
    require(user_id > 0 and serial_number > 0, 'INVALID_USER_IDENTITY')
    require(command('get-state').strip() == 'device', 'ADB_AUTHORIZATION_REQUIRED')
    identity = secondary_user(command('shell', 'dumpsys', 'user'), user_id, serial_number)
    require(command('shell', 'am', 'get-current-user').strip() == str(user_id),
            'SELECTED_USER_NOT_FOREGROUND')
    # -u includes retained package state; no reinstall/update is allowed by this procedure.
    for args in (('shell', 'pm', 'list', 'packages', '-u', '--user', 'all', PACKAGE),
                 ('shell', 'pm', 'list', 'packages', '-u', '--user', '0', PACKAGE)):
        output = command(*args).strip()
        require(not output, 'CANDIDATE_ALREADY_KNOWN_OR_QUERY_UNEXPECTED')
    # Content-free fingerprints detect package metadata changes. They do not prove vault bytes.
    fingerprints = {}
    for package in HISTORICAL_PACKAGES:
        output = command('shell', 'dumpsys', 'package', package)
        fingerprints[package] = hashlib.sha256(output.encode()).hexdigest()
    return {'schema_version': 1, 'status': 'READY_FOR_SEPARATELY_APPROVED_INSTALL',
            **identity, 'application_id': PACKAGE, 'device_mutated': False,
            'historical_package_metadata_sha256': fingerprints,
            'historical_vault_read': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--user-id', type=int, required=True)
    parser.add_argument('--user-serial', type=int, required=True)
    parser.add_argument('--receipt', type=Path, required=True)
    args = parser.parse_args()
    require(args.user_id != 0, 'OWNER_USER_SELECTED')
    require(bool(re.fullmatch(r'[A-Za-z0-9._:-]+', args.serial)), 'INVALID_DEVICE_SELECTOR')
    require(not args.receipt.exists(), 'RECEIPT_ALREADY_EXISTS')
    sdk = os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    require(bool(sdk), 'SDK_REQUIRED')
    adb = Path(sdk) / 'platform-tools' / ('adb.exe' if os.name == 'nt' else 'adb')
    def command(*arguments):
        result = subprocess.run([str(adb), '-s', args.serial, *arguments], capture_output=True,
                                text=True, timeout=30)
        require(result.returncode == 0, 'READ_ONLY_QUERY_FAILED')
        return result.stdout.replace('\r\n', '\n')
    result = preflight(command, args.user_id, args.user_serial)
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    with args.receipt.open('x', encoding='utf-8') as output:
        json.dump(result, output, indent=2)
        output.write('\n')
    print(result['status'] + '; no installation performed')


if __name__ == '__main__':
    main()
