"""Host-only behavior regressions for the bounded REC-I3 V8 runner."""

from __future__ import annotations

import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "tools" / "run_rec_i3_v8.ps1"
PRESERVER = ROOT / "tools" / "rec_i3_preserve_and_cleanup.ps1"
SERIAL = "emulator-5554"
FINGERPRINT = "google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys"


def git(root: Path, *args: str) -> str:
    return subprocess.run(
        ["cmd.exe", "/d", "/c", "git.exe", *args],
        cwd=root, input="", text=True, capture_output=True, check=True
    ).stdout.strip()


class RecI3V8RunnerTests(unittest.TestCase):
    def setUp(self) -> None:
        self.root = Path(tempfile.mkdtemp(prefix=".tmp-rec-i3-v8-runner-", dir=ROOT))
        self.repo = self.root / "repo"
        self.repo.mkdir()
        (self.repo / "tools").mkdir()
        (self.repo / "android").mkdir()
        shutil.copy2(PRESERVER, self.repo / "tools" / PRESERVER.name)
        (self.repo / "tools" / "validate_poc_recovery_governance.py").write_text(
            "print('synthetic governance pass')\n", encoding="utf-8"
        )
        (self.repo / "tools" / "verify_poc_recovery_dependency_inventory.py").write_text(
            "print('synthetic dependency pass')\n", encoding="utf-8"
        )
        self.toolchain = self.root / "sdk"
        (self.toolchain / "platform-tools").mkdir(parents=True)
        (self.toolchain / "emulator").mkdir()
        self.adb_log = self.root / "adb.log"
        self.gradle_log = self.root / "gradle.log"
        self.git_log = self.root / "git.log"
        self.emulator_started = self.root / "emulator-started.txt"
        self.gradle_marker = self.root / "gradle-started.txt"
        self.adb = self.toolchain / "platform-tools" / "adb.cmd"
        self.adb.write_text(
            "@echo off\r\n"
            "echo %*>>\"%FAKE_ADB_LOG%\"\r\n"
            "if \"%3\"==\"devices\" (\r\n"
            "  echo List of devices attached\r\n"
            "  if exist \"%FAKE_EMULATOR_STARTED%\" echo %2 device\r\n"
            "  exit /b 0\r\n"
            ")\r\n"
            "if \"%3\"==\"get-state\" echo device\r\n"
            "if \"%5\"==\"sys.boot_completed\" echo 1\r\n"
            "if \"%5\"==\"ro.build.version.sdk\" (if \"%FAKE_ADB_IDENTITY%\"==\"bad_api\" (echo 35) else (echo 36))\r\n"
            "if \"%5\"==\"ro.product.cpu.abi\" (if \"%FAKE_ADB_IDENTITY%\"==\"bad_abi\" (echo arm64-v8a) else (echo x86_64))\r\n"
            "if \"%5\"==\"ro.build.fingerprint\" (if \"%FAKE_ADB_IDENTITY%\"==\"bad_fingerprint\" (echo wrong/fingerprint) else (echo " + FINGERPRINT + "))\r\n"
            "if \"%3 %4 %5\"==\"emu avd name\" (if \"%FAKE_ADB_IDENTITY%\"==\"bad_avd\" (echo wrong_avd) else (echo dora_api36_recovery))\r\n"
            "if \"%5\"==\"path\" (if \"%FAKE_ADB_IDENTITY%\"==\"package_present\" (echo package:/data/app/present.apk& exit /b 0) else (exit /b 1))\r\n"
            "if \"%5 %6\"==\"list packages\" if \"%FAKE_ADB_IDENTITY%\"==\"package_present\" echo package:com.monumentogram.dora.poc.recovery\r\n"
            "exit /b 0\r\n",
            encoding="utf-8",
        )
        self.emulator = self.toolchain / "emulator" / "emulator.cmd"
        self.emulator.write_text(
            "@echo off\r\n"
            "echo %*>>\"%FAKE_EMULATOR_LOG%\"\r\n"
            "echo started>\"%FAKE_EMULATOR_STARTED%\"\r\n"
            "exit /b 0\r\n",
            encoding="utf-8",
        )
        self.gradle = self.repo / "android" / "gradlew.bat"
        self.gradle.write_text(
            "@echo off\r\n"
            "echo %*>>\"%FAKE_GRADLE_LOG%\"\r\n"
            "echo ANDROID_SERIAL=%ANDROID_SERIAL%>>\"%FAKE_GRADLE_LOG%\"\r\n"
            "if \"%1\"==\"--version\" (echo Gradle synthetic& exit /b 0)\r\n"
            "echo %*| findstr /c:\"assembleDebug\" >nul && (mkdir poc\\recovery\\build\\outputs\\apk\\debug 2>nul& mkdir poc\\recovery\\build\\outputs\\apk\\androidTest\\debug 2>nul& echo target>poc\\recovery\\build\\outputs\\apk\\debug\\recovery-debug.apk& echo test>poc\\recovery\\build\\outputs\\apk\\androidTest\\debug\\recovery-debug-androidTest.apk& exit /b 0)\r\n"
            "echo %*| findstr /c:\"connectedDebugAndroidTest\" >nul || exit /b 0\r\n"
            "if not exist \"%DORA_REC_I3_EXPECTED_LEDGER%\" exit /b 91\r\n"
            "echo started>\"%FAKE_GRADLE_MARKER%\"\r\n"
            "if not \"%FAKE_GRADLE_DELAY%\"==\"0\" ping 127.0.0.1 -n 6 >nul\r\n"
            "echo STREAM_LINE_ONE\r\n"
            "echo STREAM_LINE_TWO\r\n"
            "echo INSTRUMENTATION_CHECKPOINT_INSERT synthetic\r\n"
            "exit /b %FAKE_GRADLE_EXIT%\r\n",
            encoding="utf-8",
        )
        self.git_wrapper = self.root / "git-wrapper.cmd"
        self.git_wrapper.write_text(
            '@echo off\r\necho %*>>"%FAKE_GIT_LOG%"\r\ngit.exe %*\r\n',
            encoding="utf-8",
        )
        git(self.repo, "init", "-q")
        git(self.repo, "add", ".")
        git(
            self.repo, "-c", "user.name=Dora Test", "-c",
            "user.email=dora@example.invalid", "commit", "-q", "-m", "fixture",
        )
        self.commit = git(self.repo, "rev-parse", "HEAD")
        self.tree = git(self.repo, "show", "-s", "--format=%T", "HEAD")
        self.evidence = self.root / "evidence"
        self.staging = self.root / "staging"

    def tearDown(self) -> None:
        if os.environ.get("DORA_KEEP_RUNNER_FIXTURE") != "1":
            shutil.rmtree(self.root, ignore_errors=True)

    @property
    def ledger(self) -> Path:
        return self.evidence / f"REC-I3-V8-ATTEMPT-{self.commit}.json"

    def invoke(
        self,
        *,
        accepted_commit: str | None = None,
        accepted_tree: str | None = None,
        gradle_exit: int = 0,
        report_failure: bool = False,
        identity_mode: str = "valid",
        command_timeout: int = 30,
        gradle_delay: bool = False,
    ) -> subprocess.CompletedProcess[str]:
        environment = os.environ.copy()
        environment.update(
            {
                "ANDROID_SDK_ROOT": str(self.toolchain),
                "ANDROID_HOME": str(self.root / "wrong-sdk-root"),
                "FAKE_ADB_LOG": str(self.adb_log),
                "FAKE_EMULATOR_LOG": str(self.root / "emulator.log"),
                "FAKE_EMULATOR_STARTED": str(self.emulator_started),
                "FAKE_GRADLE_LOG": str(self.gradle_log),
                "FAKE_GRADLE_MARKER": str(self.gradle_marker),
                "FAKE_GRADLE_EXIT": str(gradle_exit),
                "FAKE_GRADLE_DELAY": "1" if gradle_delay else "0",
                "FAKE_ADB_IDENTITY": identity_mode,
                "FAKE_GIT_LOG": str(self.git_log),
                "DORA_REC_I3_GIT_PATH": str(self.git_wrapper),
                "DORA_REC_I3_PYTHON_PATH": sys.executable,
                "DORA_REC_I3_EXPECTED_LEDGER": str(self.ledger),
                "DORA_REC_I3_COMMAND_TIMEOUT_SECONDS": str(command_timeout),
            }
        )
        if report_failure:
            environment["DORA_REC_I3_INJECT_REPORT_FAILURE"] = "1"
        command = [
            "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(RUNNER),
            "-Repository", str(self.repo), "-EvidenceBase", str(self.evidence),
            "-StagingRoot", str(self.staging), "-Serial", SERIAL,
            "-AcceptedCommit", accepted_commit or self.commit,
            "-AcceptedTree", accepted_tree or self.tree,
        ]
        return subprocess.run(command, text=True, capture_output=True, env=environment, timeout=90)

    def test_rejects_identity_and_dirty_failures_before_instrumentation(self) -> None:
        for mutation in ("commit", "tree", "dirty"):
            with self.subTest(mutation=mutation):
                shutil.rmtree(self.evidence, ignore_errors=True)
                self.gradle_marker.unlink(missing_ok=True)
                if mutation == "dirty":
                    (self.repo / "dirty.txt").write_text("dirty", encoding="utf-8")
                completed = self.invoke(
                    accepted_commit="0" * 40 if mutation == "commit" else None,
                    accepted_tree="0" * 40 if mutation == "tree" else None,
                )
                # Relaxing immutable identity or clean-tree preflight must never reach connected Gradle.
                self.assertNotEqual(0, completed.returncode)
                self.assertFalse(self.gradle_marker.exists())
                (self.repo / "dirty.txt").unlink(missing_ok=True)

    def test_rejects_fingerprint_or_avd_identity_mismatch_before_instrumentation(self) -> None:
        for mode in ("bad_api", "bad_abi", "bad_fingerprint", "bad_avd", "package_present"):
            with self.subTest(mode=mode):
                shutil.rmtree(self.evidence, ignore_errors=True)
                self.gradle_marker.unlink(missing_ok=True)
                self.emulator_started.write_text("present", encoding="utf-8")
                completed = self.invoke(identity_mode=mode)
                # Weakening the exact E36 fingerprint or named-AVD predicate must never consume the attempt.
                self.assertNotEqual(0, completed.returncode)
                self.assertFalse(self.gradle_marker.exists())
                self.assertFalse(self.ledger.exists())

    def test_success_binds_serial_streams_output_and_records_preflights(self) -> None:
        completed = self.invoke()
        # Dropping serial ownership, streamed logs, or any preflight result must fail the host-run contract.
        self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)
        self.assertTrue(self.ledger.is_file())
        ledger = json.loads(self.ledger.read_text(encoding="utf-8-sig"))
        self.assertEqual("ATTEMPT_CONSUMED_COMPLETED", ledger["state"])
        self.assertEqual(0, ledger["completion"]["gradleExitCode"])
        gradle_log = self.gradle_log.read_text(encoding="utf-8")
        self.assertIn(f"ANDROID_SERIAL={SERIAL}", gradle_log)
        self.assertIn(f"--serial {SERIAL}", gradle_log)
        self.assertIn(":poc:recovery:connectedDebugAndroidTest", gradle_log)
        self.assertIn("--offline --no-daemon --no-configuration-cache", gradle_log)
        self.assertIn(
            "RecoveryE36GapiPreflightInstrumentedTest#syntheticFreshReadbackCleanupReplayAndIdentityDenial",
            gradle_log,
        )
        self.assertIn("pocRecoveryE36GapiPreflight=true", gradle_log)
        self.assertIn(f"recoveryHarnessRevision={self.commit}", gradle_log)
        adb_calls = self.adb_log.read_text(encoding="utf-8").splitlines()
        self.assertTrue(all(line.startswith(f"-s {SERIAL} ") for line in adb_calls), adb_calls)
        raw = next(self.evidence.glob("REC-I3-V8-RAW-*"))
        preflight = json.loads((raw / "preflight.json").read_text(encoding="utf-8-sig"))
        self.assertGreaterEqual(len(preflight["commands"]), 7)
        self.assertTrue(all("exitCode" in item for item in preflight["commands"]))
        names = {item["name"] for item in preflight["commands"]}
        self.assertTrue(
            {"governance", "dependencies-online", "assemble-online", "assemble-offline", "adb-api", "adb-abi"}
            <= names
        )
        apk_records = json.loads((raw / "apk-digests.json").read_text(encoding="utf-8-sig"))
        self.assertEqual(2, len(apk_records["apks"]))
        gradle_output = (raw / "connected-gradle.log").read_text(encoding="utf-8-sig")
        self.assertLess(gradle_output.index("STREAM_LINE_ONE"), gradle_output.index("STREAM_LINE_TWO"))
        invoked_git = self.git_log.read_text(encoding="utf-8").lower()
        self.assertFalse(any(word in invoked_git for word in (" fetch", " checkout", " reset", " clean")))
        preserved = next(self.evidence.glob("REC-I3-V8-PRESERVED-*"))
        self.assertEqual(2, len(list(preserved.rglob("*.apk"))))
        self.assertTrue(any(preserved.rglob("preflight.json")))
        emulator_args = (self.root / "emulator.log").read_text(encoding="utf-8")
        self.assertIn(f"-avd dora_api36_recovery -port {SERIAL.removeprefix('emulator-')}", emulator_args)
        self.assertEqual(
            ["INSTRUMENTATION_CHECKPOINT_INSERT synthetic"],
            ledger["completion"]["firstCheckpointDiagnostic"],
        )

    def test_atomic_attempt_ledger_blocks_second_launch_and_interruption_stays_unknown(self) -> None:
        first = self.invoke(gradle_exit=130)
        # Moving the lock after Gradle launch or rewriting interrupted state as unconsumed must fail this regression.
        self.assertNotEqual(0, first.returncode)
        self.assertTrue(self.gradle_marker.is_file())
        ledger = json.loads(self.ledger.read_text(encoding="utf-8-sig"))
        self.assertEqual("ATTEMPT_CONSUMED_EXECUTION_UNKNOWN", ledger["state"])
        first_gradle_lines = len(self.gradle_log.read_text(encoding="utf-8").splitlines())
        second = self.invoke()
        self.assertNotEqual(0, second.returncode)
        self.assertEqual(first_gradle_lines, len(self.gradle_log.read_text(encoding="utf-8").splitlines()))

    def test_cleanup_runs_first_when_optional_reporting_fails(self) -> None:
        completed = self.invoke(report_failure=True)
        # Optional reporting failure must never bypass preservation, uninstall, package proof, or owned-emulator kill.
        self.assertNotEqual(0, completed.returncode)
        self.assertTrue(any(self.evidence.glob("REC-I3-V8-CLEANUP-*.json")))
        calls = self.adb_log.read_text(encoding="utf-8").splitlines()
        self.assertTrue(any(" uninstall " in f" {line} " for line in calls))
        self.assertTrue(any(line.endswith("emu kill") for line in calls))
        self.assertTrue((self.evidence / "REPORTING_FAILURE.txt").is_file())

    def test_connected_gradle_timeout_consumes_attempt_and_runs_cleanup(self) -> None:
        completed = self.invoke(command_timeout=1, gradle_delay=True)
        # Removing the finite connected-command watchdog must not leave an unbounded attempt or reusable ledger.
        self.assertNotEqual(0, completed.returncode)
        ledger = json.loads(self.ledger.read_text(encoding="utf-8-sig"))
        self.assertEqual("ATTEMPT_CONSUMED_EXECUTION_UNKNOWN", ledger["state"])
        self.assertTrue(ledger["completion"]["timedOut"])
        self.assertTrue(any(self.evidence.glob("REC-I3-V8-CLEANUP-*.json")))


if __name__ == "__main__":
    unittest.main()
