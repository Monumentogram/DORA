"""Exercise the compiled boundary, including fully qualified provider types (no source grep)."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

from verify_alpha_boundaries import inspect_classes, validate_model_lock


class AlphaBoundaryTests(unittest.TestCase):
    def compile_fixture(self, body, extra=None):
        temporary = tempfile.TemporaryDirectory(prefix="alpha-boundary-")
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        source = root / "Probe.java"
        source.write_text("package com.monumentogram.dora.model.alpha; public class Probe {" + body + "}", encoding="utf-8")
        sources = [str(source)]
        if extra:
            external = root / "TranscribeClient.java"
            external.write_text(extra, encoding="utf-8")
            sources.append(str(external))
        javac = str(Path(os.environ["JAVA_HOME"]) / "bin" / "javac") if os.environ.get("JAVA_HOME") else shutil.which("javac")
        subprocess.run([javac, "-d", str(root), *sources], check=True, capture_output=True)
        return root

    def test_safe_value_contract_is_admitted(self):
        root = self.compile_fixture("public String value;")
        self.assertGreater(inspect_classes(root, "com.monumentogram.dora.model.alpha."), 0)

    def test_fully_qualified_provider_type_is_rejected(self):
        root = self.compile_fixture(
            "public software.amazon.awssdk.services.transcribe.TranscribeClient client;",
            "package software.amazon.awssdk.services.transcribe; public class TranscribeClient {}",
        )
        with self.assertRaisesRegex(ValueError, "forbidden dependency"):
            inspect_classes(root, "com.monumentogram.dora.model.alpha.")

    def test_filesystem_network_and_logging_dependencies_are_rejected(self):
        for body in ("public java.io.File file;", "public java.net.URL url;", "public void log() { System.out.println(1); }"):
            with self.subTest(body=body):
                root = self.compile_fixture(body)
                with self.assertRaisesRegex(ValueError, "forbidden dependency"):
                    inspect_classes(root, "com.monumentogram.dora.model.alpha.")

    def test_missing_compilation_fails_closed(self):
        root = self.compile_fixture("public String value;")
        with self.assertRaises(ValueError):
            inspect_classes(root, "com.monumentogram.dora.missing.")

    def test_unused_provider_dependency_in_model_lock_is_rejected(self):
        with self.assertRaisesRegex(ValueError, "dependency lock"):
            validate_model_lock("software.amazon.awssdk:transcribe:1=debugRuntimeClasspath\n")


if __name__ == "__main__":
    unittest.main()
