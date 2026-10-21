"""Bounded production artifact checks; never installs or closes physical gates."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
import zipfile

from alpha_release_identity import APPLICATION_ID, CERTIFICATE_SHA256, identity
from isolated_alpha_apk import AAR_SHA, MODEL_SHA, payload, differences, sha
from build_internal_alpha import verify_certificate

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'
PERMISSIONS = {'android.permission.' + name for name in (
    'RECORD_AUDIO', 'FOREGROUND_SERVICE', 'FOREGROUND_SERVICE_MICROPHONE',
    'POST_NOTIFICATIONS', 'WAKE_LOCK', 'USE_BIOMETRIC', 'USE_FINGERPRINT')}
PERMISSIONS.add(APPLICATION_ID + '.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION')


def require(value, reason):
    if not value:
        raise ValueError(reason)


def check_version(code, installed):
    require(type(code) is int and type(installed) is int and installed >= 0 and code > installed,
            'VERSION_CODE_NOT_MONOTONIC')


def check_build_receipt(receipt, source, artifact_sha):
    require(receipt.get('source_sha') == source and receipt.get('source_clean') is True and
            receipt.get('sha256') == artifact_sha, 'ARTIFACT_BUILD_PROVENANCE_MISMATCH')


def check_badging(text, code, name):
    match = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", text, re.M)
    require(match and match.groups() == (APPLICATION_ID, str(code), name), 'PRODUCTION_IDENTITY_MISMATCH')
    permissions = set(re.findall(r"^uses-permission[^:]*: name='([^']+)'", text, re.M))
    require(permissions <= PERMISSIONS, 'UNADMITTED_PERMISSION')


def check_manifest(xml):
    root = ET.fromstring(xml)
    require(root.get('package') == APPLICATION_ID, 'MANIFEST_PACKAGE_MISMATCH')
    require(root.get(ANDROID + 'sharedUserId') is None, 'SHARED_UID')
    app = root.find('application')
    require(app is not None and app.get(ANDROID + 'allowBackup') == 'false', 'BACKUP_NOT_DISABLED')
    for flag in ('debuggable', 'testOnly', 'usesCleartextTraffic'):
        require(app.get(ANDROID + flag, 'false') == 'false', 'UNSAFE_APPLICATION_FLAG:' + flag)
    require(root.find('instrumentation') is None, 'INSTRUMENTATION_IN_PRODUCT')
    components = []
    for element in app:
        require(element.get(ANDROID + 'singleUser', 'false') == 'false', 'SINGLE_USER_COMPONENT')
        if element.tag not in ('activity', 'service', 'provider', 'receiver', 'activity-alias'):
            continue
        name = element.get(ANDROID + 'name', '')
        profile_receiver = (element.tag == 'receiver' and
                            name == 'androidx.profileinstaller.ProfileInstallReceiver' and
                            element.get(ANDROID + 'permission') == 'android.permission.DUMP')
        require(element.tag not in ('receiver', 'activity-alias') or profile_receiver, 'UNADMITTED_COMPONENT')
        require('operator' not in name.lower() and 'test' not in name.lower(), 'TEST_COMPONENT')
        exported = element.get(ANDROID + 'exported')
        require(exported in ('true', 'false'), 'IMPLICIT_EXPORT')
        require(exported == 'false' or profile_receiver or (element.tag == 'activity' and name in
                ('.MainActivity', APPLICATION_ID + '.MainActivity')), 'EXPORTED_COMPONENT')
        components.append({'type': element.tag, 'name': name, 'exported': exported == 'true'})
    return components


def check_denial_code(smali, signature):
    methods = re.findall(r'^\.method [^\n]* ' + re.escape(signature) + r'\n(.*?)^\.end method', smali, re.M | re.S)
    require(len(methods) == 1, 'RELEASE_OVERRIDE_METHOD_MISSING_OR_DUPLICATE')
    body = re.sub(r'^\s*\.annotation[^\n]*\n.*?^\s*\.end annotation\s*$', '', methods[0], flags=re.M | re.S)
    instructions = [line.strip() for line in body.splitlines()
                    if line.strip() and not line.strip().startswith(('.', '#'))]
    require(len(instructions) >= 2, 'EMPTY_OVERRIDE')
    constant = re.fullmatch(r'const/4 ([vp]\d+), 0x0', instructions[-2])
    require(constant and instructions[-1] == 'return ' + constant.group(1), 'OVERRIDE_NOT_FALSE')
    for line in instructions[:-2]:
        require(re.fullmatch(r'const-string [vp]\d+, "[A-Za-z]+"', line) or
                re.fullmatch(r'invoke-static \{[vp]\d+, [vp]\d+\}, Lkotlin/jvm/internal/Intrinsics;->checkNotNullParameter\(Ljava/lang/Object;Ljava/lang/String;\)V', line),
                'OVERRIDE_HAS_NON_DENIAL_OPERATION')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--compare', type=Path)
    parser.add_argument('--build-receipt', type=Path, required=True)
    parser.add_argument('--compare-build-receipt', type=Path)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--source-sha', required=True)
    parser.add_argument('--installed-version-code', type=int, required=True)
    parser.add_argument('--aar', type=Path)
    parser.add_argument('--signed', action='store_true')
    parser.add_argument('--receipt', type=Path, required=True)
    args = parser.parse_args()
    require(bool(args.compare) == bool(args.compare_build_receipt), 'COMPARE_BUILD_RECEIPT_REQUIRED')
    require(not args.receipt.exists(), 'RECEIPT_EXISTS')
    def run(command):
        result = subprocess.run([str(item) for item in command], cwd=ROOT,
                                capture_output=True, text=True, timeout=180)
        require(result.returncode == 0, 'ARTIFACT_TOOL_FAILED')
        return result.stdout
    require(re.fullmatch('[a-f0-9]{40}', args.source_sha), 'INVALID_SOURCE_SHA')
    require(run(['git', 'rev-parse', 'HEAD']).strip() == args.source_sha, 'SOURCE_SHA_MISMATCH')
    require(not run(['git', 'status', '--porcelain=v1', '--untracked-files=all']).strip(), 'SOURCE_NOT_CLEAN')
    code, version = identity()
    check_version(code, args.installed_version_code)
    bt = args.sdk / 'build-tools/36.0.0'
    exe = '.exe' if os.name == 'nt' else ''
    bat = '.bat' if os.name == 'nt' else ''
    analyzer = args.sdk / ('cmdline-tools/latest/bin/apkanalyzer' + bat)
    inventories, builds = [], []
    artifacts = [(args.apk, args.build_receipt)]
    if args.compare:
        artifacts.append((args.compare, args.compare_build_receipt))
    for path, build_receipt in artifacts:
        artifact_sha = sha(path)
        check_build_receipt(json.loads(build_receipt.read_text(encoding='utf-8')), args.source_sha, artifact_sha)
        check_badging(run([bt / ('aapt' + exe), 'dump', 'badging', path]), code, version)
        xml = run([analyzer, 'manifest', 'print', path])
        components = check_manifest(xml)
        override_code = {}
        for owner, signature in (
            ('DevelopmentDeviceSecurityOverride', 'allowed(Ljava/lang/String;ZLjava/io/File;)Z'),
            ('DevelopmentAppLockOverride', 'unlock(Lcom/monumentogram/dora/audio/persistence/auth/AppLockSession;Ljava/lang/String;ZLkotlin/jvm/functions/Function0;)Z'),
        ):
            smali = run([analyzer, 'dex', 'code', '--class',
                         'com.monumentogram.dora.audio.persistence.auth.' + owner, path])
            check_denial_code(smali, signature)
            override_code[owner] = hashlib.sha256(smali.encode()).hexdigest()
        run([bt / ('zipalign' + exe), '-c', '-P', '16', '4', path])
        if args.signed:
            verify_certificate(run([bt / ('apksigner' + bat), 'verify', '--verbose', '--print-certs', path]), CERTIFICATE_SHA256)
        inventory = payload(path)
        with zipfile.ZipFile(path) as archive:
            dex_names = sorted(name for name in archive.namelist() if re.fullmatch(r'classes\d*\.dex', name))
            require(dex_names, 'NO_DEX')
            for name in dex_names:
                dex = archive.read(name)
                require(b'Lcom/monumentogram/dora/operator/' not in dex and
                        b'Landroidx/test/' not in dex, 'TEST_CODE_IN_PRODUCTION_DEX')
            if args.aar:
                require(sha(args.aar) == AAR_SHA, 'PINNED_AAR_MISMATCH')
                require(inventory.get('assets/dora-vad/silero.onnx') == MODEL_SHA, 'MODEL_MISMATCH')
                with zipfile.ZipFile(args.aar) as native:
                    for name in native.namelist():
                        if name.startswith('jni/') and not name.endswith('/'):
                            require(inventory.get('lib/' + name[4:]) == hashlib.sha256(native.read(name)).hexdigest(), 'NATIVE_PAYLOAD_MISMATCH')
        require(sha(path) == artifact_sha, 'ARTIFACT_CHANGED_DURING_VERIFICATION')
        builds.append({'apk_sha256': artifact_sha, 'size_bytes': path.stat().st_size,
                       'build_receipt_sha256': sha(build_receipt),
                       'payload': inventory, 'dex_inventory': dex_names, 'components': components,
                       'release_override_dex': override_code,
                       'manifest_sha256': hashlib.sha256(xml.encode()).hexdigest()})
        inventories.append(inventory)
    diff = differences(*inventories) if len(inventories) == 2 else None
    require(diff is None or not diff, 'CLEAN_BUILD_PAYLOAD_MISMATCH')
    receipt = {'source_sha': args.source_sha, 'provenance': 'LOCAL_BUILD_RECEIPT_BOUND_NOT_REMOTE_ATTESTATION',
               'application_id': APPLICATION_ID,
               'version_code': code, 'version_name': version, 'builds': builds,
               'certificate_sha256': CERTIFICATE_SHA256 if args.signed else None,
               'payload_differences': diff, 'private_vad_verified': args.aar is not None,
               'production_installation': 'NOT_RUN', 'security_restoration': 'OPEN',
               'distribution': 'BLOCKED', 'signed': args.signed}
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    with args.receipt.open('x', encoding='utf-8') as output:
        json.dump(receipt, output, indent=2)
        output.write('\n')
    print('PASS production artifact preflight; installation NOT_RUN; distribution BLOCKED')


if __name__ == '__main__':
    main()
