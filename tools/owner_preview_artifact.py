"""Actual ownerPreview APK audit. Never installs, distributes, or grants software readiness."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import xml.etree.ElementTree as ET
import zipfile

from alpha_release_identity import CERTIFICATE_SHA256 as PRODUCTION_CERTIFICATE
from alpha_security_preflight import check_denial_code, require
from build_internal_alpha import verify_certificate
from isolated_alpha_apk import AAR_SHA, MODEL_SHA, payload, sha
from version_label import check_label

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'com.monumentogram.dora.alpha.preview'
NAMESPACE = 'com.monumentogram.dora'
VERSION_CODE = 7
VERSION_NAME = '0.1.0-owner-preview.7'
OWNER_CERTIFICATE = 'a5f865ce5d28056429b323ede48f53e9ae8ad06880ea20ed242c2a2ba274603d'
EXCLUDED_CERTIFICATES = {
    'diagnostic': '2f02873f869862680f63e58e2cac37561f45078019e666eae67440365b801ee8',
    'debug': 'a526f525ec8594bc40fc7f0c9ddc6b8ad034d4c3ecd47f650ef81c7b56f74e72',
}
BASE = '5957acb6e54f733ae5224c27b73a4b55eea4360e'
ANDROID = '{http://schemas.android.com/apk/res/android}'
MODES = ('owner-local', 'ci-unsigned', 'ci-test-signed')
PERMISSIONS = {'android.permission.' + name for name in (
    'RECORD_AUDIO', 'FOREGROUND_SERVICE', 'FOREGROUND_SERVICE_MICROPHONE',
    'POST_NOTIFICATIONS', 'WAKE_LOCK', 'INTERNET', 'ACCESS_NETWORK_STATE')}
PERMISSIONS.add(PACKAGE + '.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION')


def run(command, *, timeout=180):
    result = subprocess.run([str(item) for item in command], cwd=ROOT,
                            capture_output=True, text=True, timeout=timeout)
    require(result.returncode == 0, 'PILOT_ARTIFACT_TOOL_FAILED')
    return result.stdout


def exact_source(source, mode):
    require(re.fullmatch('[a-f0-9]{40}', source), 'INVALID_SOURCE_SHA')
    require(run(['git', 'rev-parse', 'HEAD']).strip() == source, 'SOURCE_SHA_MISMATCH')
    require(not run(['git', 'status', '--porcelain=v1', '--untracked-files=all']).strip(), 'SOURCE_NOT_CLEAN')
    run(['git', 'merge-base', '--is-ancestor', BASE, source])
    if mode == 'owner-local':
        require(os.environ.get('GITHUB_ACTIONS') != 'true', 'OWNER_LOCAL_ONLY')
        require(run(['git', 'branch', '--show-current']).strip() == 'codex/stage12-alpha-ready', 'UNADMITTED_BRANCH')


def check_badging(text):
    found = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", text, re.M)
    require(found and found.groups() == (PACKAGE, str(VERSION_CODE), VERSION_NAME), 'PILOT_IDENTITY_MISMATCH')
    require(set(re.findall(r"^uses-permission[^:]*: name='([^']+)'", text, re.M)) <= PERMISSIONS, 'UNADMITTED_PERMISSION')


def check_manifest(xml):
    root = ET.fromstring(xml)
    require(root.get('package') == PACKAGE, 'PILOT_MANIFEST_PACKAGE_MISMATCH')
    require(root.get(ANDROID + 'versionCode') == str(VERSION_CODE) and
            root.get(ANDROID + 'versionName') == VERSION_NAME, 'PILOT_MANIFEST_VERSION_MISMATCH')
    require(root.get(ANDROID + 'sharedUserId') is None and
            root.get(ANDROID + 'sharedUserMaxSdkVersion') is None, 'SHARED_UID')
    require(root.find('instrumentation') is None, 'INSTRUMENTATION_IN_PILOT')
    for permission in root:
        if permission.tag.startswith('uses-permission'):
            require(permission.get(ANDROID + 'name') in PERMISSIONS, 'UNADMITTED_PERMISSION')
        if permission.tag == 'permission':
            require(permission.get(ANDROID + 'name') == PACKAGE + '.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION' and
                    permission.get(ANDROID + 'protectionLevel') in ('signature', '0x2'), 'UNADMITTED_DEFINED_PERMISSION')
    apps = root.findall('application')
    require(len(apps) == 1, 'MISSING_OR_DUPLICATE_APPLICATION')
    app = apps[0]
    require(app.get(ANDROID + 'allowBackup') == 'false', 'BACKUP_NOT_DISABLED')
    require(app.get(ANDROID + 'backupAgent') is None, 'BACKUP_AGENT')
    for flag in ('debuggable', 'testOnly', 'usesCleartextTraffic'):
        require(app.get(ANDROID + flag, 'false') == 'false', 'UNSAFE_APPLICATION_FLAG:' + flag)
    allowed = {
        ('activity', NAMESPACE + '.MainActivity'),
        ('service', NAMESPACE + '.recording.ProductRecordingService'),
        ('service', 'androidx.room.MultiInstanceInvalidationService'),
        ('provider', 'androidx.startup.InitializationProvider'),
        ('receiver', 'androidx.profileinstaller.ProfileInstallReceiver'),
    }
    components, seen = [], set()
    for element in app:
        require(element.get(ANDROID + 'singleUser', 'false') == 'false', 'SINGLE_USER_COMPONENT')
        require(element.get(ANDROID + 'grantUriPermissions', 'false') == 'false' and
                element.find('grant-uri-permission') is None, 'URI_EXPORT_ROUTE')
        if element.tag not in ('activity', 'activity-alias', 'service', 'provider', 'receiver'):
            continue
        name = element.get(ANDROID + 'name', '')
        key = (element.tag, name)
        require(key in allowed and key not in seen, 'UNADMITTED_OR_DUPLICATE_COMPONENT')
        seen.add(key)
        exported = element.get(ANDROID + 'exported')
        require(exported in ('true', 'false'), 'IMPLICIT_EXPORT')
        if name == NAMESPACE + '.MainActivity':
            require(exported == 'true', 'MISSING_PRODUCT_LAUNCHER')
            filters = element.findall('intent-filter')
            require(len(filters) == 1 and len(filters[0]) == 2 and
                    [(node.tag, node.get(ANDROID + 'name')) for node in filters[0]] == [
                        ('action', 'android.intent.action.MAIN'), ('category', 'android.intent.category.LAUNCHER')],
                    'UNADMITTED_LAUNCHER_ROUTE')
        elif element.tag == 'receiver':
            require(element.get(ANDROID + 'permission') == 'android.permission.DUMP', 'UNPROTECTED_PROFILE_RECEIVER')
            require(all(node.tag == 'action' and node.get(ANDROID + 'name', '').startswith('androidx.profileinstaller.action.')
                        for intent in element.findall('intent-filter') for node in intent), 'UNADMITTED_PROFILE_ROUTE')
        else:
            require(exported == 'false' and not element.findall('intent-filter'), 'EXPORTED_COMPONENT')
        if name == NAMESPACE + '.recording.ProductRecordingService':
            require(element.get(ANDROID + 'foregroundServiceType') in ('microphone', '0x80'), 'MICROPHONE_FGS_MISSING')
        if element.tag == 'provider':
            require(element.get(ANDROID + 'authorities') == PACKAGE + '.androidx-startup', 'PROVIDER_IDENTITY_MISMATCH')
        components.append({'type': element.tag, 'name': name, 'exported': exported == 'true'})
    require({('activity', NAMESPACE + '.MainActivity'), ('service', NAMESPACE + '.recording.ProductRecordingService')} <= seen,
            'PRODUCT_COMPONENT_MISSING')
    return components


def check_signer(text, expected, excluded, mode):
    require(mode in ('owner-local', 'ci-test-signed'), 'SIGNED_MODE_REQUIRED')
    require(isinstance(expected, str) and re.fullmatch('[a-f0-9]{64}', expected), 'INVALID_SIGNER_PIN')
    if mode == 'owner-local':
        require(excluded == EXCLUDED_CERTIFICATES, 'KNOWN_SIGNER_EXCLUSIONS_REQUIRED')
        require(expected == OWNER_CERTIFICATE, 'OWNER_SIGNER_PIN_MISMATCH')
        require(expected not in {PRODUCTION_CERTIFICATE, *excluded.values()}, 'SEPARATE_PILOT_SIGNER_REQUIRED')
        subjects = re.findall(r'^Signer #\d+ certificate DN: (.+)$', text, re.M)
        require(len(subjects) == 1 and 'android debug' not in subjects[0].lower(), 'DEBUG_OR_MISSING_SIGNER_SUBJECT')
    verify_certificate(text, expected)


def check_diagnostic_code(smali):
    fields = re.findall(r'^\.field [^\n]*\bENABLED:Z\s*=\s*(\S+)\s*$', smali, re.M)
    require(fields in (['false'], ['0x0']), 'DIAGNOSTIC_BUILD_NOT_FALSE')
    require(not re.search(r'\bsput-boolean\b[^\n]*->ENABLED:Z', smali), 'DIAGNOSTIC_BUILD_REASSIGNED')


def check_dex_bytes(data):
    require(data.startswith(b'dex\n'), 'INVALID_DEX')
    for token in (b'Lcom/monumentogram/dora/operator/', b'Landroidx/test/', b'Lorg/junit/',
                  b'IsolatedAlphaOperator', b'dora-debug-unlock', b'dora-insecure-device',
                  b'BiometricPrompt', b'createConfirmDeviceCredentialIntent',
                  b'AppLockAuthenticationActivity', b'AndroidAuthProof', b'AndroidAppLock',
                  b'DevelopmentAppLockOverride', b'DevelopmentDeviceSecurityOverride'):
        require(token not in data, 'OPERATOR_TEST_OR_OVERRIDE_IN_DEX')


def backup_resource_path(table, reference, name):
    require(isinstance(reference, str) and re.fullmatch(r'@ref/0x[0-9a-f]+', reference), 'BACKUP_RESOURCE_REFERENCE_REQUIRED')
    pattern = (r'^\s*resource ' + re.escape(reference[5:]) + r' [^\s]+:xml/' + re.escape(name) +
               r':[^\n]*\n\s*\(string8\) "([^"\n]+)"')
    matches = re.findall(pattern, table, re.M)
    require(len(matches) == 1 and re.fullmatch(r'res/[A-Za-z0-9_/.-]+\.xml', matches[0]) and
            '..' not in matches[0].split('/'), 'BACKUP_RESOURCE_AMBIGUOUS_OR_MISSING')
    return matches[0]


def check_backup_rules(legacy_xml, modern_xml):
    legacy, modern = ET.fromstring(legacy_xml), ET.fromstring(modern_xml)
    domains = {'root', 'file', 'database', 'sharedpref', 'external', 'device_root',
               'device_file', 'device_database', 'device_sharedpref'}
    require(legacy.tag == 'full-backup-content' and modern.tag == 'data-extraction-rules' and
            sorted(child.tag for child in modern) == ['cloud-backup', 'device-transfer'], 'BACKUP_POLICY_MISSING')
    for policy in (legacy, *modern):
        require(len(policy) == len(domains) and {child.get('domain') for child in policy} == domains and
                all(child.tag == 'exclude' and child.get('path') == '.' for child in policy), 'BACKUP_OR_TRANSFER_ROUTE')


def check_build_receipt(receipt, source, digest, index):
    require(receipt.get('source_sha') == source and receipt.get('sha256') == digest and
            receipt.get('source_clean') is True and receipt.get('variant') == 'ownerPreview' and
            type(receipt.get('independent_clean_build')) is int and receipt['independent_clean_build'] == index and
            receipt.get('no_build_cache') is True and receipt.get('dependency_verification') == 'strict',
            'PILOT_BUILD_PROVENANCE_MISMATCH')


def check_reproducible(first, second):
    require(Path(first).resolve() != Path(second).resolve(), 'INDEPENDENT_BUILD_PATHS_REQUIRED')
    require(sha(first) == sha(second), 'CLEAN_BUILD_FULL_BYTES_MISMATCH')


def native_alignment(data):
    require(len(data) >= 64 and data[:4] == b'\x7fELF' and data[5] == 1, 'INVALID_NATIVE_ELF')
    bits = data[4]
    require(bits in (1, 2), 'UNKNOWN_ELF_CLASS')
    offset = struct.unpack_from('<Q' if bits == 2 else '<I', data, 32 if bits == 2 else 28)[0]
    size, count = struct.unpack_from('<HH', data, 54 if bits == 2 else 42)
    require(count > 0 and size >= (56 if bits == 2 else 32) and offset + size * count <= len(data), 'INVALID_ELF_TABLE')
    loads = []
    for index in range(count):
        fields = struct.unpack_from('<IIQQQQQQ' if bits == 2 else '<IIIIIIII', data, offset + index * size)
        if bits == 2:
            kind, _, position, address, _, file_size, _, alignment = fields
        else:
            kind, position, address, _, file_size, _, _, alignment = fields
        require(position + file_size <= len(data), 'INVALID_ELF_SEGMENT')
        if kind == 1:
            minimum = 16384 if bits == 2 else 4096
            require(alignment >= minimum and position % minimum == address % minimum, 'INCOMPATIBLE_NATIVE_ALIGNMENT')
            loads.append(alignment)
    require(loads, 'MISSING_ELF_LOAD')
    return {'bits': 64 if bits == 2 else 32, 'load_alignment': loads}


def check_pin_resources(resources):
    for token in ('Разблокировать', 'Разблокируйте DORA', 'Требуется разблокировка', 'Настройте PIN'):
        require(token not in resources, 'NO_APPLICATION_PIN_OR_BIOMETRIC_PROMPT')


def audit_apk(path, sdk, mode, certificate=None, excluded=None, aar=None):
    before = sha(path)
    bt = sdk / 'build-tools/36.0.0'
    exe, bat = ('.exe', '.bat') if os.name == 'nt' else ('', '')
    analyzer = sdk / ('cmdline-tools/latest/bin/apkanalyzer' + bat)
    badging = run([bt / ('aapt' + exe), 'dump', 'badging', path])
    check_badging(badging)
    xml = run([analyzer, 'manifest', 'print', path])
    components = check_manifest(xml)
    app = ET.fromstring(xml).find('application')
    resources = run([bt / ('aapt' + exe), 'dump', '--values', 'resources', path])
    version_label = check_label(badging, resources, 'ownerPreview')
    backup_xml = []
    for attribute, name in (('fullBackupContent', 'backup_rules'), ('dataExtractionRules', 'data_extraction_rules')):
        file = backup_resource_path(resources, app.get(ANDROID + attribute), name)
        backup_xml.append(run([analyzer, 'resources', 'xml', '--file', file, path]))
    check_backup_rules(*backup_xml)
    check_pin_resources(resources)
    diagnostic = run([analyzer, 'dex', 'code', '--class', NAMESPACE + '.audio.persistence.DiagnosticBuild', path])
    check_diagnostic_code(diagnostic)
    run([bt / ('zipalign' + exe), '-c', '-P', '16', '4', path])
    if mode != 'ci-unsigned':
        check_signer(run([bt / ('apksigner' + bat), 'verify', '--verbose', '--print-certs', path]), certificate, excluded or {}, mode)
    inventory = payload(path)
    native = {}
    with zipfile.ZipFile(path) as archive:
        dex_names = sorted(name for name in archive.namelist() if re.fullmatch(r'classes\d*\.dex', name))
        require(dex_names, 'NO_DEX')
        for name in dex_names:
            check_dex_bytes(archive.read(name))
        for name in archive.namelist():
            if name.startswith('lib/') and name.endswith('.so'):
                native[name] = native_alignment(archive.read(name))
        require(native, 'NATIVE_RUNTIME_MISSING')
        if mode == 'owner-local':
            require(aar is not None and sha(aar) == AAR_SHA, 'PINNED_AAR_REQUIRED')
            require(inventory.get('assets/dora-vad/silero.onnx') == MODEL_SHA, 'PACKAGED_MODEL_MISMATCH')
            require(all(name.startswith('lib/arm64-v8a/') for name in native), 'OWNER_PILOT_ABI_MISMATCH')
            with zipfile.ZipFile(aar) as source:
                names = [name for name in source.namelist() if name.startswith('jni/') and name.endswith('.so')]
                require(names, 'AAR_NATIVE_MISSING')
                for name in names:
                    require(inventory.get('lib/' + name[4:]) == hashlib.sha256(source.read(name)).hexdigest(), 'PACKAGED_VAD_NATIVE_MISMATCH')
    asr_dependencies = {}
    if mode == 'owner-local':
        import owner_arm82
        owner_arm82.check_sources()
        owner_arm82.check_payload(inventory)
        asr_dependencies = owner_arm82.audit_dependencies(path, sdk)
    require(sha(path) == before, 'ARTIFACT_CHANGED_DURING_AUDIT')
    return {'apk_sha256': before, 'size_bytes': path.stat().st_size, 'payload': inventory,
            'compiled_version_label': version_label, 'version_label_parity': 'PASS',
            'components': components, 'dex_inventory': dex_names, 'no_application_pin_or_biometric_prompt': 'PASS',
            'diagnostic_false_dex_sha256': hashlib.sha256(diagnostic.encode()).hexdigest(),
            'backup_policy_sha256': [hashlib.sha256(value.encode()).hexdigest() for value in backup_xml],
            'manifest_sha256': hashlib.sha256(xml.encode()).hexdigest(), 'native_elf': native,
            'zip_alignment_16k': 'PASS', 'arm82_dependencies': asr_dependencies}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--mode', choices=MODES, required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--compare', type=Path)
    parser.add_argument('--build-receipt', type=Path)
    parser.add_argument('--compare-build-receipt', type=Path)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--source-sha', required=True)
    parser.add_argument('--certificate-sha256')
    parser.add_argument('--diagnostic-certificate-sha256')
    parser.add_argument('--debug-certificate-sha256')
    parser.add_argument('--aar', type=Path)
    parser.add_argument('--graph', type=Path)
    parser.add_argument('--sbom', type=Path)
    parser.add_argument('--receipt', type=Path, required=True)
    args = parser.parse_args()
    require(not args.receipt.exists(), 'RECEIPT_EXISTS')
    exact_source(args.source_sha, args.mode)
    owner = args.mode == 'owner-local'
    if owner:
        require(all((args.compare, args.build_receipt, args.compare_build_receipt, args.aar, args.graph, args.sbom)),
                'OWNER_LOCAL_COMPLETE_EVIDENCE_REQUIRED')
    else:
        require(not args.aar, 'CI_PRIVATE_INPUT_NOT_ADMITTED')
    if args.mode == 'ci-unsigned':
        require(not args.certificate_sha256, 'UNSIGNED_MODE_CANNOT_CLAIM_SIGNER')
    artifacts = [(args.apk, args.build_receipt)]
    if args.compare:
        require(args.compare_build_receipt, 'COMPARE_RECEIPT_REQUIRED')
        artifacts.append((args.compare, args.compare_build_receipt))
        check_reproducible(args.apk, args.compare)
    records = []
    for index, (path, build_receipt) in enumerate(artifacts, 1):
        record = audit_apk(path, args.sdk, args.mode, args.certificate_sha256,
            {'diagnostic': args.diagnostic_certificate_sha256, 'debug': args.debug_certificate_sha256}, args.aar)
        if build_receipt:
            check_build_receipt(json.loads(build_receipt.read_text()), args.source_sha, record['apk_sha256'], index)
            record['build_receipt_sha256'] = sha(build_receipt)
        records.append(record)
    sbom_hash = graph_hash = None
    if args.graph or args.sbom:
        require(args.graph and args.sbom, 'GRAPH_AND_SBOM_REQUIRED_TOGETHER')
        from owner_preview_sbom import generate
        require(json.loads(args.sbom.read_text()) == generate(json.loads(args.graph.read_text()), args.source_sha, private_vad=owner, private_asr=owner), 'SBOM_MISMATCH')
        sbom_hash, graph_hash = sha(args.sbom), sha(args.graph)
    exact_source(args.source_sha, args.mode)
    receipt = {'schema_version': 1, 'mode': args.mode, 'source_sha': args.source_sha, 'source_clean': True,
        'application_id': PACKAGE, 'version_code': VERSION_CODE, 'version_name': VERSION_NAME,
        'variant': 'ownerPreview', 'builds': records, 'certificate_sha256': args.certificate_sha256,
        'signing_claim': 'OWNER_LOCAL_SEPARATE_SIGNER' if owner else 'CI_ONLY_NO_OWNER_SIGNING_CLAIM',
        'known_excluded_signers': {'production': PRODUCTION_CERTIFICATE,
            'diagnostic': args.diagnostic_certificate_sha256, 'debug': args.debug_certificate_sha256} if owner else {},
        'aar_sha256': AAR_SHA if owner else None, 'model_sha256': MODEL_SHA if owner else None,
        'arm82_device_runtime': 'NOT_RUN', 'asr_runtime_packaged': owner,
        'byte_identical': len(records) == 2, 'sbom_sha256': sbom_hash, 'graph_sha256': graph_hash,
        'software_readiness': 'NOT_DETERMINED_BY_ARTIFACT_AUDIT', 'physical_installed': False,
        'distribution': 'BLOCKED', 'provenance': 'LOCAL_RECEIPT_NOT_REMOTE_ATTESTATION'}
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    with args.receipt.open('x', encoding='utf-8') as stream:
        json.dump(receipt, stream, indent=2)
        stream.write('\n')
    print('PASS ownerPreview artifact audit (' + args.mode + '); software readiness is a separate gate')


if __name__ == '__main__':
    main()
