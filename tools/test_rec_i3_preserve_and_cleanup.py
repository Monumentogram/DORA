"""Regression tests for REC-I3 long-path evidence preservation and independent cleanup."""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "tools" / "rec_i3_preserve_and_cleanup.ps1"


def extended(path: Path) -> str:
    absolute = str(path.resolve())
    return absolute if absolute.startswith("\\\\?\\") else "\\\\?\\" + absolute


def long_directory(root: Path, label: str) -> Path:
    current = root / label
    while len(str(current)) < 315:
        current /= "segment-" + ("x" * 42)
    os.makedirs(extended(current), exist_ok=True)
    return current


class PreserveAndCleanupTests(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp(prefix="dora-rec-i3-"))
        self.source = long_directory(self.root, "source")
        self.destination = long_directory(self.root, "destination")
        self.staging = self.root / "stage"
        self.observation = self.root / "cleanup.json"
        self.adb_log = self.root / "adb.log"
        self.fake_adb = self.root / "fake-adb.cmd"
        self.fake_adb.write_text("@echo off\r\necho %*>>\"%FAKE_ADB_LOG%\"\r\nexit /b 0\r\n")
        self.payload = b"DORA-REC-I3-long-path-preservation\x00\xff"
        self.relative = Path("raw") / "nested" / "evidence.bin"
        target = self.source / self.relative
        os.makedirs(extended(target.parent), exist_ok=True)
        with open(extended(target), "wb") as stream:
            stream.write(self.payload)

    def tearDown(self) -> None:
        shutil.rmtree(extended(self.root), ignore_errors=True)

    def invoke(self, inject_failure: bool = False) -> subprocess.CompletedProcess[str]:
        command = [
            "powershell.exe",
            "-NoProfile",
            "-ExecutionPolicy",
            "Bypass",
            "-File",
            str(SCRIPT),
            "-SourcePath",
            str(self.source),
            "-EvidenceRoot",
            str(self.destination),
            "-StagingRoot",
            str(self.staging),
            "-ObservationPath",
            str(self.observation),
            "-AdbPath",
            str(self.fake_adb),
            "-PackageNames",
            "com.monumentogram.dora.poc.recovery,com.monumentogram.dora.poc.recovery.test",
            "-StopEmulator",
        ]
        if inject_failure:
            command.append("-InjectCopyFailure")
        environment = os.environ.copy()
        environment["FAKE_ADB_LOG"] = str(self.adb_log)
        return subprocess.run(command, text=True, capture_output=True, env=environment, timeout=90)

    def test_preserves_bytes_and_hashes_across_310_character_paths(self) -> None:
        self.assertGreaterEqual(len(str(self.source)), 310)
        self.assertGreaterEqual(len(str(self.destination)), 310)
        completed = self.invoke()
        self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)
        manifest_path = self.destination / "PRESERVATION_MANIFEST.json"
        with open(extended(manifest_path), encoding="utf-8") as stream:
            manifest = json.load(stream)
        self.assertTrue(manifest["copySucceeded"])
        self.assertEqual(1, len(manifest["files"]))
        record = manifest["files"][0]
        self.assertEqual(self.relative.as_posix(), record["relativePath"])
        self.assertEqual(hashlib.sha256(self.payload).hexdigest(), record["sha256"])
        self.assertEqual(str((self.source / self.relative).resolve()), record["originalPath"])
        with open(extended(self.destination / self.relative), "rb") as stream:
            self.assertEqual(self.payload, stream.read())
        observation = json.loads(self.observation.read_text(encoding="utf-8-sig"))
        self.assertTrue(observation["copySucceeded"])
        self.assertTrue(observation["cleanupAttempted"])
        self.assertEqual(2, len(observation["packageCleanup"]))
        self.assertTrue(all(item["postUninstallQueryAttempted"] for item in observation["packageCleanup"]))
        self.assertTrue(all(item["packageAbsentObserved"] for item in observation["packageCleanup"]))
        self.assertTrue(observation["emulatorCleanup"]["attempted"])

    def test_cleanup_observation_survives_injected_copy_failure(self) -> None:
        completed = self.invoke(inject_failure=True)
        self.assertNotEqual(0, completed.returncode)
        observation = json.loads(self.observation.read_text(encoding="utf-8-sig"))
        self.assertFalse(observation["copySucceeded"])
        self.assertEqual("INJECTED_COPY_FAILURE", observation["copyFailure"])
        self.assertTrue(observation["cleanupAttempted"])
        self.assertTrue(all(item["forceStopAttempted"] for item in observation["packageCleanup"]))
        self.assertTrue(all(item["uninstallAttempted"] for item in observation["packageCleanup"]))
        self.assertTrue(all(item["postUninstallQueryAttempted"] for item in observation["packageCleanup"]))
        adb_calls = self.adb_log.read_text(encoding="utf-8")
        self.assertIn("shell am force-stop com.monumentogram.dora.poc.recovery", adb_calls)
        self.assertIn("uninstall com.monumentogram.dora.poc.recovery.test", adb_calls)
        self.assertIn("shell pm path com.monumentogram.dora.poc.recovery.test", adb_calls)
        self.assertIn("emu kill", adb_calls)


if __name__ == "__main__":
    unittest.main()
