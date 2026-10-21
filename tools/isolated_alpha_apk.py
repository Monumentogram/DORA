"""Verify local isolated APK identity and compare complete uncompressed payloads."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

from alpha_release_identity import CERTIFICATE_SHA256
from isolated_alpha_preflight import PACKAGE, require

AAR_SHA = '64969d0fbf5d3936a127879684bc2f14014b99817ca3c9b2e16298c1372208db'
MODEL_SHA = '1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3'
ALLOWED_PERMISSIONS = {
    'android.permission.RECORD_AUDIO', 'android.permission.FOREGROUND_SERVICE',
    'android.permission.FOREGROUND_SERVICE_MICROPHONE', 'android.permission.POST_NOTIFICATIONS',
    'android.permission.WAKE_LOCK', 'android.permission.USE_BIOMETRIC',
    'android.permission.USE_FINGERPRINT', PACKAGE + '.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION',
}


def sha(path):
    digest = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            digest.update(block)
    return digest.hexdigest()


def check_badging(text, version_code, version_name):
    found = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", text, re.MULTILINE)
    require(found and found.groups() == (PACKAGE, str(version_code), version_name), 'APK_IDENTITY_MISMATCH')
    permissions = set(re.findall(r"^uses-permission[^:]*: name='([^']+)'", text, re.MULTILINE))
    require(permissions <= ALLOWED_PERMISSIONS, 'UNADMITTED_PERMISSION')


def check_signer(text, expected):
    require(bool(re.fullmatch('[a-f0-9]{64}', expected)) and expected != CERTIFICATE_SHA256,
            'ISOLATED_SIGNER_REQUIRED')
    signers = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]{64})', text)
    require([value.lower() for value in signers] == [expected], 'SIGNER_MISMATCH')


def payload(path):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), 'DUPLICATE_APK_ENTRIES')
        require(not any('dora-protected-policy' in name or
                        name.lower().endswith(('.p12', '.jks', '.keystore', '.pem')) for name in names),
                'PRIVATE_MATERIAL_IN_APK')
        return {name: hashlib.sha256(archive.read(name)).hexdigest() for name in sorted(names)
                if not name.endswith('/')}


def differences(first, second):
    return sorted(name for name in first.keys() | second.keys() if first.get(name) != second.get(name))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--compare', type=Path, required=True)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--source-sha', required=True)
    parser.add_argument('--version-code', type=int, required=True)
    parser.add_argument('--version-name', required=True)
    parser.add_argument('--certificate-sha256', required=True)
    parser.add_argument('--aar', type=Path, required=True)
    parser.add_argument('--receipt', type=Path, required=True)
    args = parser.parse_args()
    require(bool(re.fullmatch('[a-f0-9]{40}', args.source_sha)), 'INVALID_SOURCE_SHA')
    require(not args.receipt.exists(), 'RECEIPT_EXISTS')
    require(sha(args.aar) == AAR_SHA, 'PINNED_AAR_MISMATCH')
    root = Path(__file__).resolve().parents[1]
    def run(command):
        result = subprocess.run([str(item) for item in command], cwd=root, capture_output=True, text=True, timeout=120)
        require(result.returncode == 0, 'ARTIFACT_TOOL_FAILED')
        return result.stdout
    require(run(['git', 'rev-parse', 'HEAD']).strip() == args.source_sha, 'SOURCE_HEAD_MISMATCH')
    require(not run(['git', 'status', '--porcelain=v1', '--untracked-files=all']).strip(), 'SOURCE_NOT_CLEAN')
    bt = args.sdk / 'build-tools/36.0.0'
    signer = bt / ('apksigner.bat' if os.name == 'nt' else 'apksigner')
    aapt = bt / ('aapt.exe' if os.name == 'nt' else 'aapt')
    align = bt / ('zipalign.exe' if os.name == 'nt' else 'zipalign')
    records = []
    inventories = []
    for file in (args.apk, args.compare):
        check_signer(run([signer, 'verify', '--verbose', '--print-certs', file]), args.certificate_sha256)
        check_badging(run([aapt, 'dump', 'badging', file]), args.version_code, args.version_name)
        run([align, '-c', '-P', '16', '4', file])
        inventory = payload(file)
        require(inventory.get('assets/dora-vad/silero.onnx') == MODEL_SHA, 'PACKAGED_MODEL_MISMATCH')
        with zipfile.ZipFile(args.aar) as archive:
            native = {name.removeprefix('jni/'): hashlib.sha256(archive.read(name)).hexdigest()
                      for name in archive.namelist() if name.startswith('jni/')}
        for name, digest in native.items():
            require(inventory.get('lib/' + name) == digest, 'PACKAGED_VAD_NATIVE_MISMATCH')
        manifest = run([aapt, 'dump', 'xmltree', file, 'AndroidManifest.xml'])
        require('android:sharedUserId' not in manifest and 'android:singleUser' not in manifest,
                'SHARED_UID_OR_SINGLE_USER_COMPONENT')
        require('android:debuggable' not in manifest or
                bool(re.search(r'android:debuggable[^\n]*=\(type 0x12\)0x0\b', manifest)),
                'DEBUGGABLE_CANDIDATE')
        require(bool(re.search(r'android:allowBackup[^\n]*=\(type 0x12\)0x0\b', manifest)), 'BACKUP_NOT_DISABLED')
        inventories.append(inventory)
        records.append({'apk_sha256': sha(file), 'size_bytes': file.stat().st_size,
                        'payload': inventory, 'manifest_dump_sha256': hashlib.sha256(manifest.encode()).hexdigest()})
    diff = differences(*inventories)
    require(not diff, 'CLEAN_BUILD_PAYLOAD_MISMATCH')
    receipt = {'schema_version': 1, 'source_sha': args.source_sha, 'application_id': PACKAGE,
               'version_code': args.version_code, 'version_name': args.version_name,
               'certificate_sha256': args.certificate_sha256, 'aar_sha256': AAR_SHA,
               'model_sha256': MODEL_SHA, 'builds': records, 'payload_differences': diff,
               'byte_identical': records[0]['apk_sha256'] == records[1]['apk_sha256'],
               'physical_installed': False, 'production_binary_equivalence': 'NOT_CLAIMED'}
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    with args.receipt.open('x', encoding='utf-8') as output:
        json.dump(receipt, output, indent=2)
        output.write('\n')
    print('PASS isolated APK identity and clean-build payload comparison; no installation')


if __name__ == '__main__':
    main()
