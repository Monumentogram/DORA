"""Behavior regressions for REC-I3 V2 preservation and cleanup."""

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
SERIAL = "emulator-5554"
ATTEMPT = "attempt-001"


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
        self.root = Path(tempfile.mkdtemp(prefix="dora-rec-i3-v2-"))
        self.source = long_directory(self.root, "source")
        self.destination = long_directory(self.root, "destination")
        self.staging = self.root / "stage"
        self.observation = self.root / "observations" / "cleanup.json"
        self.adb_log = self.root / "adb.log"
        self.fake_adb = self.root / "fake-adb.cmd"
        self.fake_adb.write_text(
            "@echo off\r\n"
            "echo %*>>\"%FAKE_ADB_LOG%\"\r\n"
            "if \"%FAKE_ADB_MODE%\"==\"transport_fail\" if \"%3\"==\"get-state\" (echo offline& exit /b 1)\r\n"
            "if \"%FAKE_ADB_MODE%\"==\"permission\" if \"%5\"==\"path\" (echo Security exception& exit /b 1)\r\n"
            "if \"%FAKE_ADB_MODE%\"==\"permission\" if \"%6\"==\"packages\" (echo Security exception& exit /b 1)\r\n"
            "if \"%3\"==\"get-state\" echo device\r\n"
            "exit /b 0\r\n",
            encoding="utf-8",
        )
        self.payloads = {
            Path("raw") / "nested" / "evidence.bin": b"DORA-REC-I3-long-path-preservation\x00\xff",
            Path("metadata.json"): b'{"synthetic":true}\n',
        }
        for relative, payload in self.payloads.items():
            target = self.source / relative
            os.makedirs(extended(target.parent), exist_ok=True)
            with open(extended(target), "wb") as stream:
                stream.write(payload)

    def tearDown(self) -> None:
        shutil.rmtree(extended(self.root), ignore_errors=True)

    def invoke(
        self,
        *,
        attempt: str = ATTEMPT,
        adb_mode: str = "absent",
        injection: str | None = None,
    ) -> subprocess.CompletedProcess[str]:
        command = [
            "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass",
            "-File", str(SCRIPT),
            "-SourcePath", str(self.source),
            "-EvidenceRoot", str(self.destination),
            "-StagingRoot", str(self.staging),
            "-ObservationPath", str(self.observation),
            "-AdbPath", str(self.fake_adb),
            "-PackageNames", "com.monumentogram.dora.poc.recovery,com.monumentogram.dora.poc.recovery.test",
            "-AttemptId", attempt,
            "-Serial", SERIAL,
            "-StopEmulator",
        ]
        if injection:
            command.append(f"-Inject{injection}Failure")
        environment = os.environ.copy()
        environment["FAKE_ADB_LOG"] = str(self.adb_log)
        environment["FAKE_ADB_MODE"] = adb_mode
        return subprocess.run(command, text=True, capture_output=True, env=environment, timeout=90)

    def read_observation(self) -> dict:
        return json.loads(self.observation.read_text(encoding="utf-8-sig"))

    def adb_calls(self) -> list[str]:
        return [line.strip() for line in self.adb_log.read_text(encoding="utf-8").splitlines()]

    def test_preserves_exact_three_hop_bytes_and_hashes_across_long_paths(self) -> None:
        completed = self.invoke()
        # Removing any source/stage/evidence size, digest, or path-set comparison must fail this contract.
        self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)
        with open(
            extended(self.destination / "PRESERVATION_MANIFEST.json"), encoding="utf-8-sig"
        ) as stream:
            manifest = json.load(stream)
        self.assertEqual("DORA_REC_I3_PRESERVATION_V2", manifest["schema"])
        self.assertTrue(manifest["copySucceeded"])
        expected_paths = sorted(path.as_posix() for path in self.payloads)
        self.assertEqual(expected_paths, manifest["sourceRelativePaths"])
        self.assertEqual(expected_paths, manifest["stagedRelativePaths"])
        self.assertEqual(expected_paths, manifest["evidenceRelativePaths"])
        self.assertEqual(expected_paths, sorted(item["relativePath"] for item in manifest["files"]))
        for record in manifest["files"]:
            payload = self.payloads[Path(record["relativePath"])]
            digest = hashlib.sha256(payload).hexdigest()
            self.assertEqual(len(payload), record["sourceBytes"])
            self.assertEqual(len(payload), record["stagedBytes"])
            self.assertEqual(len(payload), record["evidenceBytes"])
            self.assertEqual(digest, record["sourceSha256"])
            self.assertEqual(digest, record["stagedSha256"])
            self.assertEqual(digest, record["evidenceSha256"])

    def test_unique_attempt_staging_refuses_reuse_without_deleting_sentinel(self) -> None:
        staging_attempt = self.staging / f"rec-i3-preservation-{ATTEMPT}"
        staging_attempt.mkdir(parents=True)
        sentinel = staging_attempt / "sentinel.txt"
        sentinel.write_text("retain me", encoding="utf-8")
        completed = self.invoke()
        # Reintroducing deletion or overwrite of an existing attempt directory must destroy neither history nor its sentinel.
        self.assertNotEqual(0, completed.returncode)
        self.assertEqual("retain me", sentinel.read_text(encoding="utf-8"))
        self.assertIn("STAGING_ATTEMPT_ALREADY_EXISTS", completed.stdout + completed.stderr)
        self.assertTrue(self.read_observation()["cleanupAttempted"])

    def test_rejects_unsafe_attempt_identifier_and_still_cleans(self) -> None:
        completed = self.invoke(attempt="../escape")
        # Weakening the AttemptId allowlist must not permit staging path traversal.
        self.assertNotEqual(0, completed.returncode)
        self.assertIn("ATTEMPT_ID_UNSAFE", completed.stdout + completed.stderr)
        self.assertTrue(self.read_observation()["cleanupAttempted"])

    def test_every_device_action_is_serial_bound_and_absence_is_directly_proven(self) -> None:
        completed = self.invoke()
        # Omitting -s from any cleanup probe or mutation must fail the single-device ownership boundary.
        self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)
        calls = self.adb_calls()
        self.assertGreaterEqual(len(calls), 11)
        self.assertTrue(all(call.startswith(f"-s {SERIAL} ") for call in calls), calls)
        observation = self.read_observation()
        self.assertEqual("DORA_REC_I3_CLEANUP_OBSERVATION_V2", observation["schema"])
        self.assertTrue(all(item["transportProbeExitCode"] == 0 for item in observation["packageCleanup"]))
        self.assertTrue(all(item["packageListExitCode"] == 0 for item in observation["packageCleanup"]))
        self.assertTrue(all(item["packageAbsentObserved"] for item in observation["packageCleanup"]))

    def test_transport_or_permission_failures_never_claim_package_absence(self) -> None:
        for mode in ("transport_fail", "permission"):
            with self.subTest(mode=mode):
                self.adb_log.unlink(missing_ok=True)
                self.observation.unlink(missing_ok=True)
                shutil.rmtree(self.staging, ignore_errors=True)
                completed = self.invoke(adb_mode=mode)
                # Treating empty or failing pm output as absence must remain fail-closed without healthy transport and package listing.
                self.assertNotEqual(0, completed.returncode)
                observation = self.read_observation()
                self.assertEqual("PACKAGE_CLEANUP_UNVERIFIED", observation["cleanupFailure"])
                self.assertTrue(all(not item["packageAbsentObserved"] for item in observation["packageCleanup"]))

    def test_cleanup_survives_each_preservation_or_reporting_failure(self) -> None:
        for phase in ("Source", "Stage", "Evidence", "Report"):
            with self.subTest(phase=phase):
                self.adb_log.unlink(missing_ok=True)
                self.observation.unlink(missing_ok=True)
                shutil.rmtree(self.staging, ignore_errors=True)
                shutil.rmtree(extended(self.destination), ignore_errors=True)
                os.makedirs(extended(self.destination), exist_ok=True)
                completed = self.invoke(injection=phase)
                # Moving cleanup out of finally must be caught for failures at every preservation/reporting phase.
                self.assertNotEqual(0, completed.returncode)
                calls = self.adb_calls()
                self.assertTrue(any(" uninstall " in f" {call} " for call in calls), calls)
                self.assertTrue(any(call.endswith("emu kill") for call in calls), calls)
                if phase != "Report":
                    self.assertTrue(self.read_observation()["cleanupAttempted"])


if __name__ == "__main__":
    unittest.main()
