"""Signing boundary tests use disposable files, never the owner's credentials."""

import tempfile
import unittest
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


if __name__ == "__main__":
    unittest.main()
