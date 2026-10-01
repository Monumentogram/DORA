"""Signing boundary tests use disposable files, never the owner's credentials."""

import tempfile
import unittest
import subprocess
from pathlib import Path

import build_internal_alpha as alpha


class SigningBoundaryTest(unittest.TestCase):
    def test_missing_material_fails_before_any_build(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "signing material"):
                alpha.signing_material(Path(directory), Path(directory) / "repo")

    def test_repository_cannot_hold_signing_material_even_if_ignored(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            with self.assertRaisesRegex(ValueError, "outside"):
                alpha.signing_material(root / "ignored", root)

    def test_invalid_certificate_pin_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            for name in ("alpha.p12", "password.txt", "certificate.sha256"):
                (root / name).write_text("synthetic-invalid", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "SHA-256"):
                alpha.signing_material(root, root / "repo")

    def test_material_in_another_git_checkout_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / ".git").mkdir()
            with self.assertRaisesRegex(ValueError, "outside"):
                alpha.signing_material(root / "secrets", root / "other-repo")

    def test_certificate_mismatch_cannot_publish_an_artifact(self):
        with self.assertRaisesRegex(ValueError, "certificate"):
            alpha.verify_certificate(
                "Signer #1 certificate SHA-256 digest: " + "b" * 64, "a" * 64
            )

    def test_one_matching_signer_is_required(self):
        output = "Signer #1 certificate SHA-256 digest: " + "a" * 64
        alpha.verify_certificate(output, "a" * 64)
        with self.assertRaisesRegex(ValueError, "certificate"):
            alpha.verify_certificate(output + "\n" + output, "a" * 64)


class ProductReleaseBoundaryTest(unittest.TestCase):
    def setUp(self):
        self.assertTrue(hasattr(alpha, "source_identity"), "clean exact-source gate is required")
        self.assertTrue(hasattr(alpha, "verify_apk_identity"), "signed product identity gate is required")

    def test_wrong_package_version_and_permissions_rejected(self):
        valid = "package: name='com.monumentogram.dora' versionCode='4' versionName='0.1.0-alpha.2' platformBuildVersionName='16'"
        alpha.verify_apk_identity(valid, 4, "0.1.0-alpha.2")
        for changed in [valid.replace("versionCode='4'", "versionCode='2'"), valid.replace("alpha.2", "alpha.1"), valid.replace("com.monumentogram.dora'", "com.monumentogram.dora.debug'"), valid + "\nuses-permission: name='android.permission.INTERNET'", valid + "\nuses-permission: name='android.permission.RECORD_AUDIO'"]:
            with self.subTest(changed=changed), self.assertRaises(ValueError):
                alpha.verify_apk_identity(changed, 4, "0.1.0-alpha.2")

    def test_dirty_and_wrong_exact_source_fail_closed(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            def git(*args):
                return subprocess.check_output(["git", "-C", str(root), *args], text=True, stderr=subprocess.PIPE).strip()
            git("init", "-b", "stage/7-alpha-foundation")
            (root / "source.txt").write_text("synthetic\n")
            git("add", "source.txt")
            git("-c", "user.name=Synthetic Test", "-c", "user.email=synthetic@example.invalid", "commit", "-qm", "fixture")
            sha = git("rev-parse", "HEAD")
            self.assertEqual(alpha.source_identity(root, sha)["source_sha"], sha)
            with self.assertRaises(ValueError):
                alpha.source_identity(root, "f" * 40)
            (root / "untracked.txt").write_text("not committed")
            with self.assertRaises(ValueError):
                alpha.source_identity(root, sha)
            (root / "untracked.txt").unlink()
            (root / "source.txt").write_text("modified")
            with self.assertRaises(ValueError):
                alpha.source_identity(root, sha)


if __name__ == "__main__":
    unittest.main()
