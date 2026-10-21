"""Identity and payload failures use synthetic ZIPs, never private model input."""
import tempfile
from pathlib import Path
import unittest
import warnings
import zipfile

import isolated_alpha_apk as apk


class ApkTests(unittest.TestCase):
    def test_identity_rejects_production_debug_and_version_drift(self):
        for package in ('com.monumentogram.dora', 'com.monumentogram.dora.debug'):
            with self.assertRaises(ValueError):
                apk.check_badging(f"package: name='{package}' versionCode='4' versionName='x'", 4, 'x')
        with self.assertRaises(ValueError):
            apk.check_badging("package: name='com.monumentogram.dora.alpha.test' versionCode='5' versionName='x'", 4, 'x')

    def test_identity_accepts_only_expected_version_and_permissions(self):
        good = "package: name='com.monumentogram.dora.alpha.test' versionCode='4' versionName='x'"
        apk.check_badging(good, 4, 'x')
        for permission in ('INTERNET', 'INTERACT_ACROSS_USERS', 'MANAGE_USERS', 'READ_EXTERNAL_STORAGE'):
            with self.subTest(permission=permission), self.assertRaises(ValueError):
                apk.check_badging(good + f"\nuses-permission: name='android.permission.{permission}'", 4, 'x')

    def test_archive_rejects_duplicates_and_private_material(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / 'synthetic.apk'
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr('classes.dex', b'synthetic')
                archive.writestr('assets/credential.p12', b'fixture')
            with self.assertRaises(ValueError):
                apk.payload(path)
            with zipfile.ZipFile(path, 'w') as archive:
                archive.writestr('classes.dex', b'synthetic')
                with warnings.catch_warnings():
                    warnings.simplefilter('ignore', UserWarning)
                    archive.writestr('classes.dex', b'different')
            with self.assertRaises(ValueError):
                apk.payload(path)

    def test_payload_comparison_detects_changed_dex_and_model(self):
        self.assertEqual(['classes.dex'], apk.differences({'classes.dex': 'a'}, {'classes.dex': 'b'}))
        self.assertEqual(['assets/dora-vad/silero.onnx'], apk.differences({}, {'assets/dora-vad/silero.onnx': 'b'}))
        self.assertEqual([], apk.differences({'classes.dex': 'a'}, {'classes.dex': 'a'}))

    def test_signer_rejects_production_pin_and_multiple_signers(self):
        from alpha_release_identity import CERTIFICATE_SHA256
        with self.assertRaises(ValueError):
            apk.check_signer('Signer #1 certificate SHA-256 digest: ' + CERTIFICATE_SHA256, CERTIFICATE_SHA256)
        with self.assertRaises(ValueError):
            apk.check_signer('Signer #1 certificate SHA-256 digest: ' + 'a'*64 + '\nSigner #2 certificate SHA-256 digest: ' + 'b'*64, 'a'*64)
        apk.check_signer('Signer #1 certificate SHA-256 digest: ' + 'a'*64, 'a'*64)


if __name__ == '__main__':
    unittest.main()
