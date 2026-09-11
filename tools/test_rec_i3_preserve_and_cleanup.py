"""Behavior regressions for REC-I3 V2 preservation and cleanup."""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
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
        (self.root / "tmp").mkdir()
        self.source = long_directory(self.root, "source")
        self.destination = long_directory(self.root, "destination")
        self.staging = self.root / "stage"
        self.observation = self.root / "observations" / "cleanup.json"
        self.adb_log = self.root / "adb.log"
        self.fake_adb = self.root / "fake-adb.cmd"
        self.fake_adb.write_text(
            "@echo off\r\n"
            "echo %*>>\"%FAKE_ADB_LOG%\"\r\n"
            "if \"%FAKE_ADB_MODE%\"==\"hang_first\" if \"%3 %4\"==\"shell am\" if not exist \"%FAKE_ADB_HANG_MARKER%\" (echo hung>\"%FAKE_ADB_HANG_MARKER%\"& ping 127.0.0.1 -n 6 >nul)\r\n"
            "if \"%FAKE_ADB_MODE%\"==\"transport_fail\" if \"%3\"==\"get-state\" (echo offline& exit /b 1)\r\n"
            "if \"%FAKE_ADB_MODE%\"==\"permission\" if \"%5\"==\"path\" (echo Security exception& exit /b 1)\r\n"
            "if \"%FAKE_ADB_MODE%\"==\"permission\" if \"%6\"==\"packages\" (echo Security exception& exit /b 1)\r\n"
            "if \"%FAKE_ADB_MODE%\"==\"path_stderr_permission\" if \"%5\"==\"path\" (echo Security exception: path denied 1>&2& exit /b 1)\r\n"
            "if \"%3\"==\"get-state\" echo device\r\n"
            "exit /b 0\r\n",
            encoding="utf-8",
        )
        self.robocopy_log = self.root / "robocopy.log"
        self.fake_robocopy = self.root / "fake-robocopy.cmd"
        self.fake_robocopy.write_text(
            "@echo off\r\n"
            "echo %*>>\"%FAKE_ROBOCOPY_LOG%\"\r\n"
            "echo copy stdout diagnostic\r\n"
            "echo copy stderr diagnostic 1>&2\r\n"
            "if \"%FAKE_ROBOCOPY_MODE%\"==\"fail\" exit /b 16\r\n"
            "if \"%FAKE_ROBOCOPY_MODE%\"==\"fail_second\" if exist \"%FAKE_COPY_MARKER%\" exit /b 16\r\n"
            "if \"%FAKE_ROBOCOPY_MODE%\"==\"hang_second\" if exist \"%FAKE_COPY_MARKER%\" (ping 127.0.0.1 -n 6 >nul& exit /b 16)\r\n"
            "echo copied>\"%FAKE_COPY_MARKER%\"\r\n"
            "if defined FAKE_COPY_BLOCKER \"%FAKE_PYTHON%\" \"%FAKE_COPY_BLOCKER%\"\r\n"
            "if \"%FAKE_ROBOCOPY_MODE%\"==\"hang\" (ping 127.0.0.1 -n 6 >nul& exit /b 16)\r\n"
            "robocopy.exe %*\r\n"
            "exit /b %errorlevel%\r\n",
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
        if os.environ.get("DORA_KEEP_HELPER_FIXTURE") == "1":
            print(f"RETAINED_FIXTURE {self.id()} {self.root}", flush=True)
        else:
            shutil.rmtree(extended(self.root), ignore_errors=True)

    def invoke(
        self,
        *,
        attempt: str = ATTEMPT,
        adb_mode: str = "absent",
        injection: str | None = None,
        helper_timeout: int = 30,
        robocopy_mode: str = "normal",
        missing_copy: bool = False,
        block_receipt: bool = False,
    ) -> subprocess.CompletedProcess[str]:
        command = [
            os.environ.get("DORA_REC_I3_TEST_POWERSHELL", "powershell.exe"), "-NoProfile", "-ExecutionPolicy", "Bypass",
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
        environment["TEMP"] = str(self.root / "tmp")
        environment["TMP"] = str(self.root / "tmp")
        environment["FAKE_ADB_LOG"] = str(self.adb_log)
        environment["FAKE_ADB_MODE"] = adb_mode
        environment["FAKE_ADB_HANG_MARKER"] = str(self.root / "adb-hang.marker")
        environment["FAKE_ROBOCOPY_LOG"] = str(self.robocopy_log)
        environment["FAKE_ROBOCOPY_MODE"] = robocopy_mode
        environment["DORA_REC_I3_ROBOCOPY_PATH"] = str(self.root / "missing-copy.exe" if missing_copy else self.fake_robocopy)
        environment["FAKE_COPY_MARKER"] = str(self.root / "copy.marker")
        if block_receipt:
            blocker = self.root / "block-copy-receipt.py"
            blocker.write_text(
                "from pathlib import Path\n"
                f"parent = Path({str(self.observation.parent)!r})\n"
                "for directory in parent.glob('rec-i3-copy-*'):\n"
                "    (directory / 'source-to-stage.json').mkdir(exist_ok=True)\n", encoding="utf-8",
            )
            environment["FAKE_COPY_BLOCKER"] = str(blocker)
            environment["FAKE_PYTHON"] = sys.executable
        environment["DORA_REC_I3_HELPER_COMMAND_TIMEOUT_SECONDS"] = str(helper_timeout)
        raw_completed = subprocess.run(command, capture_output=True, env=environment, timeout=90)
        completed = subprocess.CompletedProcess(command, raw_completed.returncode,
                                               raw_completed.stdout.decode("oem", errors="replace"),
                                               raw_completed.stderr.decode("oem", errors="replace"))
        if os.environ.get("DORA_KEEP_HELPER_FIXTURE") == "1":
            invocation = len(list(self.root.glob("helper-*.stdout"))) + 1
            (self.root / f"helper-{invocation}.stdout.raw").write_bytes(raw_completed.stdout)
            (self.root / f"helper-{invocation}.stderr.raw").write_bytes(raw_completed.stderr)
            (self.root / f"helper-{invocation}.stdout").write_text(completed.stdout, encoding="utf-8")
            (self.root / f"helper-{invocation}.stderr").write_text(completed.stderr, encoding="utf-8")
            (self.root / f"helper-{invocation}.json").write_text(json.dumps({"command": command, "exitCode": completed.returncode}), encoding="utf-8")
            if self.observation.parent.is_dir():
                shutil.copytree(self.observation.parent, self.root / f"invocation-{invocation}-observations")
        return completed

    def assert_copy_receipt(self, record: dict, operation: str) -> None:
        self.assertEqual(operation, record["name"])
        self.assertEqual(ATTEMPT, record["attemptId"])
        self.assertEqual(str(self.fake_robocopy), record["executable"])
        self.assertEqual(["/E", "/COPY:DAT", "/DCOPY:DAT", "/R:2", "/W:1", "/XJ", "/NJH", "/NJS", "/NFL", "/NDL"], record["arguments"][2:])
        self.assertIn("copy stdout diagnostic", Path(record["logPath"]).read_text(encoding="utf-8-sig"))
        self.assertIn("copy stderr diagnostic", Path(record["stderrPath"]).read_text(encoding="utf-8-sig"))
        self.assertFalse(Path(record["logPath"]).is_relative_to(self.source))
        self.assertEqual(record, json.loads(Path(record["receiptPath"]).read_text(encoding="utf-8-sig")))

    def test_copy_hop_receipts_survive_success_and_repeated_attempt(self) -> None:
        # Discarding either copy result or reusing receipt names loses command evidence despite equal file hashes.
        completed = self.invoke()
        self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)
        records = self.read_observation().get("copyCommands", [])
        self.assertEqual(2, len(records))
        self.assertEqual(str(self.source), records[0]["arguments"][0])
        self.assertEqual(self.read_observation()["stagingPath"], records[0]["arguments"][1])
        self.assertEqual(records[0]["arguments"][1], records[1]["arguments"][0])
        self.assertEqual(str(self.destination), records[1]["arguments"][1])
        for record, name in zip(records, ("source-to-stage", "stage-to-evidence")):
            self.assert_copy_receipt(record, name)
            self.assertEqual(1, record["exitCode"])
            self.assertFalse(record["timedOut"])
            self.assertIsNone(record["launchFailure"])
        saved = {path: path.read_bytes() for path in self.observation.parent.rglob("*") if path.is_file() and path != self.observation}
        self.assertNotEqual(0, self.invoke().returncode)
        for path, content in saved.items():
            self.assertEqual(content, path.read_bytes())

    def test_failed_copy_diagnostics_survive_each_hop(self) -> None:
        # A generic copy error must not erase the failing operation, exact exit, stdout or stderr.
        for mode, count in (("fail", 1), ("fail_second", 2)):
            with self.subTest(mode=mode):
                self.staging = self.root / mode
                (self.root / "copy.marker").unlink(missing_ok=True)
                completed = self.invoke(robocopy_mode=mode)
                self.assertNotEqual(0, completed.returncode)
                records = self.read_observation().get("copyCommands", [])
                self.assertEqual(count, len(records))
                self.assert_copy_receipt(records[-1], "source-to-stage" if count == 1 else "stage-to-evidence")
                self.assertEqual(16, records[-1]["exitCode"])
                self.assertFalse(records[-1]["timedOut"])
                self.assertTrue(self.read_observation()["cleanupAttempted"])

    def test_copy_timeout_receipt_retains_diagnostics_for_each_hop(self) -> None:
        for mode, count in (("hang", 1), ("hang_second", 2)):
            with self.subTest(mode=mode):
                self.staging = self.root / mode
                (self.root / "copy.marker").unlink(missing_ok=True)
                completed = self.invoke(robocopy_mode=mode, helper_timeout=1)
                self.assertNotEqual(0, completed.returncode)
                records = self.read_observation().get("copyCommands", [])
                self.assertEqual(count, len(records))
                self.assert_copy_receipt(records[-1], "source-to-stage" if count == 1 else "stage-to-evidence")
                self.assertIsNone(records[-1]["exitCode"])
                self.assertTrue(records[-1]["timedOut"])
                self.assertTrue(self.read_observation()["cleanupAttempted"])

    def test_missing_copy_executable_retains_launch_failure_and_cleanup(self) -> None:
        completed = self.invoke(missing_copy=True)
        self.assertNotEqual(0, completed.returncode)
        observation = self.read_observation()
        records = observation.get("copyCommands", [])
        self.assertEqual(1, len(records))
        self.assertIsNone(records[0]["exitCode"])
        self.assertFalse(records[0]["timedOut"])
        self.assertIn("missing-copy.exe", records[0]["launchFailure"])
        self.assertTrue(Path(records[0]["stderrPath"]).read_bytes())
        self.assertTrue(observation["cleanupAttempted"])

    def test_copy_receipt_write_failure_is_reported_after_cleanup(self) -> None:
        # Failure to write one receipt must fail preservation truthfully while finally still performs cleanup.
        completed = self.invoke(block_receipt=True)
        self.assertNotEqual(0, completed.returncode)
        observation = self.read_observation()
        self.assertTrue(observation["reportingFailure"])
        self.assertTrue(observation["cleanupAttempted"])
        self.assertTrue(all(record["packageAbsentObserved"] for record in observation["packageCleanup"]))
        self.assertEqual(0, observation["emulatorCleanup"]["exitCode"])
        self.assertTrue(observation["copyCommands"])
        self.assertTrue(Path(observation["copyCommands"][0]["logPath"]).is_file())

    def test_copy_receipt_allocation_failure_retains_cleanup_and_reporting_error(self) -> None:
        # A blocked observation parent must not prevent cleanup or lose the receipt-allocation error.
        self.observation.parent.write_text("blocked observation parent", encoding="utf-8")
        completed = self.invoke()
        self.assertNotEqual(0, completed.returncode)
        self.assertIn(f"-s {SERIAL} emu kill", self.adb_calls())
        fallback = list((self.root / "tmp").glob("rec-i3-copy-*/cleanup-observation-*.json"))
        self.assertEqual(1, len(fallback), completed.stdout + completed.stderr)
        observation = json.loads(fallback[0].read_text(encoding="utf-8-sig"))
        self.assertIn("COPY_RECEIPT_ALLOCATION_FAILED", observation["reportingFailure"])
        self.assertIn("OBSERVATION_WRITE_FAILED", observation["reportingFailure"])
        self.assertTrue(observation["cleanupAttempted"])
        self.assertEqual(2, len(observation["copyCommands"]))

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

    def test_path_query_stderr_permission_is_retained_and_never_proves_absence(self) -> None:
        completed = self.invoke(adb_mode="path_stderr_permission")
        # Discarding path-query stderr must not turn an exit-1 permission failure into accepted absence.
        observation = self.read_observation()
        self.assertNotEqual(0, completed.returncode, completed.stdout + completed.stderr)
        self.assertEqual("PACKAGE_CLEANUP_UNVERIFIED", observation["cleanupFailure"])
        for record in observation["packageCleanup"]:
            self.assertEqual(0, record["transportProbeExitCode"])
            self.assertEqual(0, record["packageListExitCode"])
            self.assertEqual(1, record["postUninstallQueryExitCode"])
            self.assertEqual("", record["postUninstallQueryOutput"])
            self.assertIn("Security exception: path denied", record["postUninstallQueryErrorOutput"])
            self.assertFalse(record["packageAbsentObserved"])
            for operation in ("forceStop", "uninstall", "transportProbe", "postUninstallQuery", "packageList"):
                self.assertIn(f"{operation}ErrorOutput", record)
        self.assertIn("errorOutput", observation["emulatorCleanup"])

    def test_robocopy_timeout_unwinds_into_complete_cleanup(self) -> None:
        completed = self.invoke(helper_timeout=1, robocopy_mode="hang")
        # An unbounded preservation copy must not prevent the helper finally path from reaching every cleanup action.
        self.assertNotEqual(0, completed.returncode)
        calls = self.adb_calls()
        self.assertTrue(any(" uninstall " in f" {call} " for call in calls), calls)
        self.assertTrue(any(call.endswith("emu kill") for call in calls), calls)
        observation = self.read_observation()
        self.assertIn("TIMEOUT", observation["copyFailure"])
        self.assertTrue(observation["cleanupAttempted"])

    def test_one_adb_timeout_does_not_block_later_cleanup_or_observation(self) -> None:
        completed = self.invoke(adb_mode="hang_first", helper_timeout=1)
        # One hung cleanup command must not block later package probes, emulator shutdown, or durable observation.
        self.assertNotEqual(0, completed.returncode)
        calls = self.adb_calls()
        self.assertTrue(any("pm list packages" in call for call in calls), calls)
        self.assertTrue(any(call.endswith("emu kill") for call in calls), calls)
        observation = self.read_observation()
        self.assertTrue(observation["packageCleanup"][0]["forceStopTimedOut"])
        self.assertEqual("PACKAGE_CLEANUP_UNVERIFIED", observation["cleanupFailure"])


if __name__ == "__main__":
    unittest.main()
