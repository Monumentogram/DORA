"""Fail-closed controls for the independent owner pilot artifact boundary."""
import copy
import importlib
from pathlib import Path
import tempfile
import unittest
import zipfile
import struct


class PilotArtifactTests(unittest.TestCase):
    def setUp(self):
        try:
            self.pilot = importlib.import_module('owner_alpha_pilot_artifact')
        except ImportError:
            self.fail('owner pilot artifact verifier must exist')

    def manifest(self):
        return '''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.monumentogram.dora.alpha.pilot" android:versionCode="4" android:versionName="0.1.0-owner-alpha.4"><application android:allowBackup="false"><activity android:name="com.monumentogram.dora.MainActivity" android:exported="true"><intent-filter><action android:name="android.intent.action.MAIN"/><category android:name="android.intent.category.LAUNCHER"/></intent-filter></activity><service android:name="com.monumentogram.dora.recording.ProductRecordingService" android:exported="false" android:foregroundServiceType="microphone"/></application></manifest>'''

    def test_namespace_is_not_runtime_package_and_launcher_is_only_export(self):
        self.assertEqual(2, len(self.pilot.check_manifest(self.manifest())))
        for broken in (
            self.manifest().replace('com.monumentogram.dora.MainActivity', 'com.monumentogram.dora.alpha.pilot.MainActivity'),
            self.manifest().replace('android.intent.action.MAIN', 'android.intent.action.SEND'),
            self.manifest().replace('</intent-filter>', '<data android:scheme="content"/></intent-filter>'),
            self.manifest().replace('ProductRecordingService" android:exported="false"', 'ProductRecordingService" android:exported="true"'),
            self.manifest().replace('</application>', '<activity android:name="com.monumentogram.dora.operator.IsolatedAlphaOperatorActivity" android:exported="false"/></application>'),
            self.manifest().replace('</application>', '<provider android:name="FileProvider" android:exported="false" android:grantUriPermissions="true"/></application>'),
        ):
            with self.subTest(broken=broken), self.assertRaises(ValueError):
                self.pilot.check_manifest(broken)

    def test_manifest_rejects_shared_uid_backup_debug_test_cross_user(self):
        for old, new in (
            ('package=', 'android:sharedUserId="shared" package='),
            ('allowBackup="false"', 'allowBackup="true"'),
            ('<application ', '<application android:debuggable="true" '),
            ('<application ', '<application android:testOnly="true" '),
            ('<application ', '<application android:usesCleartextTraffic="true" '),
            ('<application ', '<uses-permission android:name="android.permission.INTERACT_ACROSS_USERS"/><application '),
            ('<application ', '<instrumentation android:name="Runner"/><application '),
            ('android:exported="false"', 'android:exported="false" android:singleUser="true"'),
            ('versionCode="4"', 'versionCode="1"'),
        ):
            with self.subTest(new=new), self.assertRaises(ValueError):
                self.pilot.check_manifest(self.manifest().replace(old, new))

    def test_compiled_manifest_rejects_auth_activity_and_accepts_private_room(self):
        xml = self.manifest().replace('foregroundServiceType="microphone"', 'foregroundServiceType="0x80"')
        xml = xml.replace('</application>', '<service android:name="androidx.room.MultiInstanceInvalidationService" android:exported="false"/></application>')
        self.assertEqual(3, len(self.pilot.check_manifest(xml)))
        with self.assertRaises(ValueError):
            self.pilot.check_manifest(xml.replace('</application>', '<activity android:name="com.monumentogram.dora.audio.persistence.auth.AppLockAuthenticationActivity" android:exported="false"/></application>'))

    def test_badging_rejects_legacy_packages_versions_and_network(self):
        good = "package: name='com.monumentogram.dora.alpha.pilot' versionCode='4' versionName='0.1.0-owner-alpha.4'"
        self.pilot.check_badging(good)
        for broken in (good.replace('.alpha.pilot', ''), good.replace('.alpha.pilot', '.debug'),
                       good.replace('.alpha.pilot', '.alpha.test'), good.replace("versionCode='4'", "versionCode='5'"),
                       good + "\nuses-permission: name='android.permission.INTERNET'"):
            with self.assertRaises(ValueError):
                self.pilot.check_badging(broken)

    def test_actual_backup_resources_disable_cloud_and_device_transfer(self):
        domains = ('root', 'file', 'database', 'sharedpref', 'external', 'device_root', 'device_file', 'device_database', 'device_sharedpref')
        excludes = ''.join('<exclude domain="' + domain + '" path="."/>' for domain in domains)
        legacy = '<full-backup-content>' + excludes + '</full-backup-content>'
        modern = '<data-extraction-rules><cloud-backup>' + excludes + '</cloud-backup><device-transfer>' + excludes + '</device-transfer></data-extraction-rules>'
        self.assertTrue(hasattr(self.pilot, 'check_backup_rules'), 'actual backup resource audit is required')
        self.pilot.check_backup_rules(legacy, modern)
        for old, new in ((legacy, modern.replace('<exclude domain="root" path="."/>', '')),
                         (legacy.replace('exclude', 'include'), modern),
                         (legacy, modern.replace('path="."', 'path="subset"'))):
            with self.assertRaises(ValueError):
                self.pilot.check_backup_rules(old, new)

    def test_resource_resolution_binds_manifest_id_and_rejects_qualified_alternatives(self):
        table = 'resource 0x7f0b0000 com.monumentogram.dora.alpha.pilot:xml/backup_rules: t=0x03 d=0x07\n (string8) "res/Qq.xml"\n'
        self.assertTrue(hasattr(self.pilot, 'backup_resource_path'), 'resource reference binding is required')
        self.assertEqual('res/Qq.xml', self.pilot.backup_resource_path(table, '@ref/0x7f0b0000', 'backup_rules'))
        for contents, reference in ((table, '@ref/0x7f0b0001'), (table + table, '@ref/0x7f0b0000')):
            with self.assertRaises(ValueError):
                self.pilot.backup_resource_path(contents, reference, 'backup_rules')

    def test_owner_signer_cannot_reuse_any_known_identity_or_debug_subject(self):
        from alpha_release_identity import CERTIFICATE_SHA256
        excluded = {'diagnostic': '2f02873f869862680f63e58e2cac37561f45078019e666eae67440365b801ee8',
                    'debug': 'a526f525ec8594bc40fc7f0c9ddc6b8ad034d4c3ecd47f650ef81c7b56f74e72'}
        owner = 'a5f865ce5d28056429b323ede48f53e9ae8ad06880ea20ed242c2a2ba274603d'
        good = 'Signer #1 certificate DN: CN=DORA Owner Pilot\nSigner #1 certificate SHA-256 digest: ' + owner
        self.pilot.check_signer(good, owner, excluded, 'owner-local')
        for expected in (CERTIFICATE_SHA256, 'a' * 64, 'b' * 64):
            with self.assertRaises(ValueError):
                self.pilot.check_signer(good, expected, excluded, 'owner-local')
        for output, pins in ((good.replace('DORA Owner Pilot', 'Android Debug'), excluded), (good, {}),
                             (good, {'diagnostic': 'a' * 64, 'debug': 'b' * 64}),
                             (good + '\nSigner #2 certificate SHA-256 digest: ' + owner, excluded)):
            with self.assertRaises(ValueError):
                self.pilot.check_signer(output, owner, pins, 'owner-local')
        with self.assertRaises(ValueError):
            self.pilot.check_signer(good.replace(owner, 'c' * 64), 'c' * 64, excluded, 'owner-local')
        self.pilot.check_signer(good.replace('DORA Owner Pilot', 'Android Debug'), owner, {}, 'ci-test-signed')

    def test_diagnostic_constant_must_be_false_and_never_reassigned(self):
        good = '.class public final Lcom/monumentogram/dora/audio/persistence/DiagnosticBuild;\n.field public static final ENABLED:Z = false\n'
        self.pilot.check_diagnostic_code(good)
        for broken in (good.replace('false', 'true'), good.replace('false', '0x1'), '',
                       good + 'sput-boolean v0, Lcom/monumentogram/dora/audio/persistence/DiagnosticBuild;->ENABLED:Z'):
            with self.assertRaises(ValueError):
                self.pilot.check_diagnostic_code(broken)

    def test_dex_rejects_operator_instrumentation_and_override_marker_tokens(self):
        good = b'dex\n035\x00Lcom/monumentogram/dora/MainActivity;'
        self.pilot.check_dex_bytes(good)
        for token in (b'Lcom/monumentogram/dora/operator/', b'Landroidx/test/', b'Lorg/junit/',
                      b'IsolatedAlphaOperator', b'dora-debug-unlock', b'dora-insecure-device', b'BiometricPrompt',
                      b'createConfirmDeviceCredentialIntent', b'AppLockAuthenticationActivity',
                      b'AndroidAuthProof', b'DevelopmentAppLockOverride'):
            with self.assertRaises(ValueError):
                self.pilot.check_dex_bytes(good + token)

    def test_two_clean_builds_require_complete_byte_equality_not_just_payload(self):
        with tempfile.TemporaryDirectory() as temp:
            a, b = Path(temp) / 'a.apk', Path(temp) / 'b.apk'
            for path, comment in ((a, b'one'), (b, b'two')):
                with zipfile.ZipFile(path, 'w') as archive:
                    archive.writestr('classes.dex', b'identical payload')
                    archive.comment = comment
            with self.assertRaises(ValueError):
                self.pilot.check_reproducible(a, b)
            b.write_bytes(a.read_bytes())
            self.pilot.check_reproducible(a, b)

    def test_build_receipt_binds_variant_clean_source_and_independent_build(self):
        good = {'source_sha': 'a' * 40, 'sha256': 'b' * 64, 'source_clean': True,
                'variant': 'ownerPilot', 'independent_clean_build': 1,
                'no_build_cache': True, 'dependency_verification': 'strict'}
        self.pilot.check_build_receipt(good, 'a' * 40, 'b' * 64, 1)
        for field, value in (('source_sha', 'c' * 40), ('sha256', 'd' * 64), ('source_clean', False),
                             ('variant', 'release'), ('independent_clean_build', 2),
                             ('no_build_cache', False), ('dependency_verification', 'off')):
            with self.assertRaises(ValueError):
                self.pilot.check_build_receipt({**good, field: value}, 'a' * 40, 'b' * 64, 1)

    def test_graph_requires_actual_pilot_root_and_release_local_variants(self):
        sbom = importlib.import_module('owner_alpha_pilot_sbom')
        from alpha_release_sbom import ROOT
        import json
        release = json.loads((ROOT / 'docs/evidence/vad-8.4-runtime/ci-release-graph.json').read_text())
        graph = copy.deepcopy(release)
        graph['configuration'] = 'ownerPilotRuntimeClasspath'
        graph['project_configurations'] = {c['path']: ('ownerPilotRuntimeClasspath' if c['path'] == ':app' else 'releaseRuntimeClasspath') for c in graph['components'] if c['kind'] == 'project'}
        self.assertEqual('ownerPilotRuntimeClasspath', sbom.check_graph(graph)['configuration'])
        for change in ('wrong-root', 'debug-dependency', 'unexpected-component'):
            broken = copy.deepcopy(graph)
            if change == 'wrong-root':
                broken['configuration'] = 'releaseRuntimeClasspath'
            elif change == 'debug-dependency':
                broken['project_configurations'][':core:audio'] = 'debugRuntimeClasspath'
            else:
                broken['components'][0]['version'] = '0.0.0'
            with self.assertRaises(ValueError):
                sbom.check_graph(broken)
        locks = (sbom.ROOT / 'android/app/gradle.lockfile').read_text()
        self.assertTrue(hasattr(sbom, 'pilot_lock_coordinates'), 'pilot-specific lock validation required')
        with self.assertRaises(ValueError):
            sbom.pilot_lock_coordinates(locks.replace('ownerPilotRuntimeClasspath', 'unusedRuntimeClasspath'))

    def test_native_elf_rejects_non_16k_64bit_load_and_truncation(self):
        data = bytearray(128)
        data[:6] = b'\x7fELF\x02\x01'
        struct.pack_into('<Q', data, 32, 64)
        struct.pack_into('<HH', data, 54, 56, 1)
        struct.pack_into('<IIQQQQQQ', data, 64, 1, 5, 0, 0, 0, 128, 128, 16384)
        self.assertEqual([16384], self.pilot.native_alignment(data)['load_alignment'])
        for alignment in (0, 4096):
            broken = data.copy()
            struct.pack_into('<Q', broken, 112, alignment)
            with self.assertRaises(ValueError):
                self.pilot.native_alignment(broken)
        with self.assertRaises(ValueError):
            self.pilot.native_alignment(data[:100])

    def test_sbom_binds_actual_pilot_graph_and_local_model_only_for_owner(self):
        sbom = importlib.import_module('owner_alpha_pilot_sbom')
        import json
        graph = json.loads(sbom.APPROVED.read_text())
        graph['configuration'] = 'ownerPilotRuntimeClasspath'
        graph['project_configurations'] = {c['path']: ('ownerPilotRuntimeClasspath' if c['path'] == ':app' else 'releaseRuntimeClasspath') for c in graph['components'] if c['kind'] == 'project'}
        ci = sbom.generate(graph, 'a' * 40)
        owner = sbom.generate(graph, 'a' * 40, private_vad=True)
        self.assertEqual('0.1.0-owner-alpha.4', ci['metadata']['component']['version'])
        self.assertNotIn('private-vad-model', {c['bom-ref'] for c in ci['components']})
        model = next(c for c in owner['components'] if c['bom-ref'] == 'private-vad-model')
        self.assertEqual('1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3', model['hashes'][0]['content'])
        self.assertIn({'name': 'dora:configuration', 'value': 'ownerPilotRuntimeClasspath'}, owner['metadata']['properties'])


class PilotBuildTests(unittest.TestCase):
    def setUp(self):
        try:
            self.build = importlib.import_module('owner_alpha_pilot_build')
        except ImportError:
            self.fail('owner pilot independent clean build tool must exist')

    def test_build_command_cleans_actual_owner_variant_and_disables_both_caches(self):
        command = self.build.build_command(Path('/external/private-vad'), Path('/external/private-asr'))
        self.assertIn('clean', command)
        self.assertIn('-PdoraPrivateAsrDir=/external/private-asr', [x.replace('\\', '/') for x in command])
        self.assertIn(':app:assembleOwnerPilot', command)
        self.assertIn('--no-build-cache', command)
        self.assertIn('--no-configuration-cache', command)
        self.assertEqual('strict', command[command.index('--dependency-verification') + 1])
        self.assertNotIn(':app:assembleRelease', command)

    def test_build_inputs_cannot_live_in_repository_or_overwrite_existing_output(self):
        with tempfile.TemporaryDirectory() as temp:
            external = Path(temp)
            for output in (self.build.ROOT / 'candidate', external):
                with self.assertRaises(ValueError):
                    self.build.check_output(output)
            self.build.check_output(external / 'fresh-output')

    def test_private_inputs_must_match_pins_before_any_build(self):
        with tempfile.TemporaryDirectory() as temp:
            directory = Path(temp)
            (directory / 'sherpa-onnx-vad-1.13.8-dora.1-arm64.aar').write_bytes(b'unapproved')
            (directory / 'silero.onnx').write_bytes(b'unapproved')
            with self.assertRaises(ValueError):
                self.build.private_inputs(directory)


if __name__ == '__main__':
    unittest.main()
