"""Exercise the actual Gradle exporter against synthetic local file dependencies."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[1]


class ReleaseGraphIntegrationTest(unittest.TestCase):
    def test_local_file_dependency_cannot_disappear_from_inventory(self):
        with tempfile.TemporaryDirectory(prefix="dora-release-graph-") as directory:
            root = Path(directory)
            (root / "settings.gradle").write_text("rootProject.name='fixture'\ninclude ':app', ':core:model'\n")
            (root / "build.gradle").write_text("allprojects { configurations { releaseRuntimeClasspath { canBeResolved=true; canBeConsumed=true } } }\nproject(':app') { dependencies { releaseRuntimeClasspath project(path: ':core:model', configuration: 'releaseRuntimeClasspath') } }\n")
            for module in ("app", "core/model"):
                (root / module).mkdir(parents=True)
            with zipfile.ZipFile(root / "extra.jar", "w") as archive:
                archive.writestr("fixture.txt", "synthetic unadmitted runtime input")
            wrapper = ROOT / "android" / ("gradlew.bat" if os.name == "nt" else "gradlew")
            command = [str(wrapper), "--no-daemon", "--no-configuration-cache", "--offline", "--console=plain", "-p", str(root), "--init-script", str(ROOT / "tools/alpha_release_graph.init.gradle"), ":app:exportAlphaReleaseGraph"]
            clean = subprocess.run(command, capture_output=True, text=True)
            self.assertEqual(clean.returncode, 0, clean.stdout + clean.stderr)
            for module in ("app", "core/model"):
                with self.subTest(module=module):
                    build = root / module / "build.gradle"
                    build.write_text("dependencies { releaseRuntimeClasspath files(rootProject.file('extra.jar')) }\n")
                    result = subprocess.run(command, capture_output=True, text=True)
                    self.assertNotEqual(result.returncode, 0, "Exporter silently omitted a runtime file dependency")
                    self.assertIn("Unadmitted release file dependency", result.stdout + result.stderr)
                    build.unlink()


if __name__ == "__main__":
    unittest.main()
