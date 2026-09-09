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
        self.root = Path(tempfile.mkdtemp(prefix=".tmp-rec-i3-v8-runner-"))
        (self.root / "tmp").mkdir()
        self.repo = self.root / "repo"
        self.repo.mkdir()
        (self.repo / "tools").mkdir()
        (self.repo / "android").mkdir()
        shutil.copy2(PRESERVER, self.repo / "tools" / PRESERVER.name)
        (self.repo / "tools" / "validate_poc_recovery_governance.py").write_text(
            "import os\nprint('synthetic governance pass')\nraise SystemExit(int(os.environ.get('FAKE_GOVERNANCE_EXIT', '0')))\n",
            encoding="utf-8",
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
        self.apk_lifecycle_log = self.root / "apk-lifecycle.jsonl"
        self.apk_lifecycle = self.root / "apk-lifecycle.py"
        # Model the installed AGP/UTP side effect only for cleanup-ownership regressions.
        self.apk_lifecycle.write_text(
            "import json, os, sys\n"
            "from pathlib import Path\n"
            "state_path = Path(os.environ['FAKE_APK_STATE'])\n"
            "installed = set(json.loads(state_path.read_text()) if state_path.exists() else [])\n"
            "packages = ['com.monumentogram.dora.poc.recovery', 'com.monumentogram.dora.poc.recovery.test']\n"
            "args = sys.argv[1:]\n"
            "def record(event, **fields):\n"
            "    with Path(os.environ['FAKE_APK_LIFECYCLE_LOG']).open('a', encoding='utf-8') as stream:\n"
            "        stream.write(json.dumps(dict(event=event, installed=sorted(installed), **fields)) + '\\n')\n"
            "def save():\n"
            "    state_path.write_text(json.dumps(sorted(installed)), encoding='utf-8')\n"
            "if args[0] == 'connected':\n"
            "    installed.update(packages)\n"
            "    record('connected-install')\n"
            "    if '-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true' not in args[1:]:\n"
            "        installed.difference_update(packages)\n"
            "        record('utp-default-removal')\n"
            "    else:\n"
            "        record('utp-retained-for-helper')\n"
            "    save()\n"
            "elif args[2] == 'uninstall':\n"
            "    package = args[3]\n"
            "    if package not in installed:\n"
            "        record('uninstall-not-installed', package=package)\n"
            "        print('Failure [DELETE_FAILED_INTERNAL_ERROR: package not installed]', file=sys.stderr)\n"
            "        sys.exit(1)\n"
            "    installed.remove(package)\n"
            "    save()\n"
            "    preserved = bool(list(Path(os.environ['FAKE_EVIDENCE']).glob('REC-I3-V8-PRESERVED-*/PRESERVATION_MANIFEST.json')))\n"
            "    if os.environ['FAKE_APK_UNINSTALL_ERROR'] == '1':\n"
            "        record('helper-uninstall-error', package=package, preserved=preserved)\n"
            "        print('Failure [forced helper uninstall error after removal]', file=sys.stderr)\n"
            "        sys.exit(17)\n"
            "    record('helper-uninstall-success', package=package, preserved=preserved)\n"
            "    print('Success')\n"
            "elif args[3:5] == ['pm', 'path']:\n"
            "    package = args[5]\n"
            "    record('pm-path', package=package)\n"
            "    if package in installed:\n"
            "        print('package:/data/app/' + package + '/base.apk')\n"
            "    else:\n"
            "        sys.exit(1)\n"
            "elif args[3:6] == ['pm', 'list', 'packages']:\n"
            "    record('pm-list')\n"
            "    for package in sorted(installed):\n"
            "        print('package:' + package)\n"
            "else:\n"
            "    raise SystemExit('unexpected stateful fake arguments: ' + repr(args))\n",
            encoding="utf-8",
        )
        self.adb = self.toolchain / "platform-tools" / "adb.cmd"
        self.adb.write_text(
            "@echo off\r\n"
            "echo %*>>\"%FAKE_ADB_LOG%\"\r\n"
            "if \"%FAKE_APK_LIFECYCLE%\"==\"1\" if \"%3\"==\"uninstall\" goto stateful\r\n"
            "if \"%FAKE_APK_LIFECYCLE%\"==\"1\" if \"%4 %5\"==\"pm path\" goto stateful\r\n"
            "if \"%FAKE_APK_LIFECYCLE%\"==\"1\" if \"%4 %5 %6\"==\"pm list packages\" goto stateful\r\n"
            "if \"%FAKE_ADB_FAILURE%\"==\"final_logcat\" if \"%3 %4\"==\"logcat -d\" exit /b 9\r\n"
            "if \"%FAKE_ADB_FAILURE%\"==\"final_logcat_timeout\" if \"%3 %4\"==\"logcat -d\" ping 127.0.0.1 -n 70 >nul\r\n"
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
            "if \"%3\"==\"uninstall\" echo uninstalled>\"%FAKE_UNINSTALLED_PREFIX%-%4\"\r\n"
            "if \"%4 %5\"==\"pm path\" if \"%FAKE_ADB_IDENTITY%\"==\"preflight_path_permission\" if not exist \"%FAKE_UNINSTALLED_PREFIX%-%6\" (echo SecurityException: permission denied 1>&2& exit /b 1)\r\n"
            "if \"%5\"==\"path\" (if \"%FAKE_ADB_IDENTITY%\"==\"package_present\" (echo package:/data/app/present.apk& exit /b 0) else (exit /b 1))\r\n"
            "if \"%5 %6\"==\"list packages\" if \"%FAKE_ADB_IDENTITY%\"==\"package_present\" echo package:com.monumentogram.dora.poc.recovery\r\n"
            "exit /b 0\r\n"
            ":stateful\r\n"
            "\"%DORA_REC_I3_PYTHON_PATH%\" \"%FAKE_APK_LIFECYCLE_SCRIPT%\" %*\r\n"
            "exit /b %errorlevel%\r\n",
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
            "if \"%FAKE_APK_LIFECYCLE%\"==\"1\" \"%DORA_REC_I3_PYTHON_PATH%\" \"%FAKE_APK_LIFECYCLE_SCRIPT%\" connected %*\r\n"
            "if defined FAKE_METADATA_REPORT_BLOCKER \"%DORA_REC_I3_PYTHON_PATH%\" \"%FAKE_METADATA_REPORT_BLOCKER%\"\r\n"
            "if defined FAKE_DELETE_PRESERVER del /q \"%FAKE_DELETE_PRESERVER%\"\r\n"
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
        else:
            print(f"RETAINED_FIXTURE {self.id()} {self.root}", flush=True)

    @property
    def ledger(self) -> Path:
        return self.evidence / f"REC-I3-V8-ATTEMPT-{self.commit}.json"

    @property
    def completion(self) -> Path:
        return self.evidence / f"REC-I3-V8-COMPLETION-{self.commit}.json"

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
        governance_exit: int = 0,
        final_logcat_failure: bool = False,
        final_logcat_timeout: bool = False,
        helper_watchdog: int = 1800,
        metadata_destination: Path | None = None,
        completion_failure: bool = False,
        delete_preserver: bool = False,
        block_metadata_report: bool = False,
        block_fallback_artifacts: bool = False,
        apk_lifecycle: bool = False,
        apk_uninstall_error: bool = False,
    ) -> subprocess.CompletedProcess[str]:
        environment = os.environ.copy()
        environment.update(
            {
                "ANDROID_SDK_ROOT": str(self.toolchain),
                "ANDROID_HOME": str(self.root / "wrong-sdk-root"),
                "TEMP": str(self.root / "tmp"),
                "TMP": str(self.root / "tmp"),
                "FAKE_ADB_LOG": str(self.adb_log),
                "FAKE_APK_LIFECYCLE": "1" if apk_lifecycle else "0",
                "FAKE_APK_LIFECYCLE_SCRIPT": str(self.apk_lifecycle),
                "FAKE_APK_LIFECYCLE_LOG": str(self.apk_lifecycle_log),
                "FAKE_APK_STATE": str(self.root / "installed-packages.json"),
                "FAKE_APK_UNINSTALL_ERROR": "1" if apk_uninstall_error else "0",
                "FAKE_EVIDENCE": str(self.evidence),
                "FAKE_UNINSTALLED_PREFIX": str(self.root / "uninstalled"),
                "FAKE_EMULATOR_LOG": str(self.root / "emulator.log"),
                "FAKE_EMULATOR_STARTED": str(self.emulator_started),
                "FAKE_GRADLE_LOG": str(self.gradle_log),
                "FAKE_GRADLE_MARKER": str(self.gradle_marker),
                "FAKE_GRADLE_EXIT": str(gradle_exit),
                "FAKE_GRADLE_DELAY": "1" if gradle_delay else "0",
                "FAKE_ADB_IDENTITY": identity_mode,
                "FAKE_ADB_FAILURE": "final_logcat_timeout" if final_logcat_timeout else "final_logcat" if final_logcat_failure else "none",
                "FAKE_GOVERNANCE_EXIT": str(governance_exit),
                "FAKE_GIT_LOG": str(self.git_log),
                "DORA_REC_I3_GIT_PATH": str(self.git_wrapper),
                "DORA_REC_I3_PYTHON_PATH": sys.executable,
                "DORA_REC_I3_EXPECTED_LEDGER": str(self.ledger),
                "DORA_REC_I3_COMMAND_TIMEOUT_SECONDS": str(command_timeout),
                "DORA_REC_I3_HELPER_WATCHDOG_SECONDS": str(helper_watchdog),
            }
        )
        if report_failure:
            environment["DORA_REC_I3_INJECT_REPORT_FAILURE"] = "1"
        if metadata_destination:
            environment["DORA_REC_I3_METADATA_DESTINATION"] = str(metadata_destination)
        if completion_failure:
            environment["DORA_REC_I3_INJECT_COMPLETION_FAILURE"] = "1"
        if delete_preserver:
            environment["FAKE_DELETE_PRESERVER"] = str(
                self.repo / "tools" / "rec_i3_preserve_and_cleanup.ps1"
            )
        if block_metadata_report or block_fallback_artifacts:
            blocker_script = self.root / "block-metadata-report.py"
            block_operation = (
                "target = evidence / f'REC-I3-V8-CLEANUP-FALLBACK-v8-{stamp}'\n"
                "target.mkdir()\n"
                "(target / 'sentinel.txt').write_text('retain prior evidence', encoding='utf-8')\n"
                if block_fallback_artifacts else
                "(evidence / f'REC-I3-V8-METADATA-FAILURE-{stamp}.json').mkdir()\n"
            )
            blocker_script.write_text(
                "from pathlib import Path\n"
                f"evidence = Path({str(self.evidence)!r})\n"
                "raw = next(evidence.glob('REC-I3-V8-RAW-*'))\n"
                "stamp = raw.name.removeprefix('REC-I3-V8-RAW-')\n"
                + block_operation,
                encoding="utf-8",
            )
            environment["FAKE_METADATA_REPORT_BLOCKER"] = str(blocker_script)
        command = [
            "powershell.exe", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(RUNNER),
            "-Repository", str(self.repo), "-EvidenceBase", str(self.evidence),
            "-StagingRoot", str(self.staging), "-Serial", SERIAL,
            "-AcceptedCommit", accepted_commit or self.commit,
            "-AcceptedTree", accepted_tree or self.tree,
        ]
        completed = subprocess.run(command, text=True, capture_output=True, env=environment, timeout=90)
        if os.environ.get("DORA_KEEP_RUNNER_FIXTURE") == "1":
            invocation = len(list(self.root.glob("runner-*.stdout"))) + 1
            (self.root / f"runner-{invocation}.stdout").write_text(completed.stdout, encoding="utf-8")
            (self.root / f"runner-{invocation}.stderr").write_text(completed.stderr, encoding="utf-8")
            (self.root / f"runner-{invocation}.json").write_text(
                json.dumps({"command": command, "exitCode": completed.returncode}, indent=2),
                encoding="utf-8",
            )
        return completed

    def test_connected_apks_are_removed_by_helper_after_preservation(self) -> None:
        # Omitting the invocation-only UTP option removes APKs before the helper and must fail cleanup.
        completed = self.invoke(apk_lifecycle=True)
        observation = json.loads(
            next(self.evidence.glob("REC-I3-V8-CLEANUP-*.json")).read_text(encoding="utf-8-sig")
        )
        self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr + json.dumps(observation))
        self.assertTrue(observation["copySucceeded"])
        self.assertIsNone(observation["cleanupFailure"])
        self.assertTrue(observation["cleanupAttempted"])
        self.assertEqual(2, len(observation["packageCleanup"]))
        for record in observation["packageCleanup"]:
            self.assertTrue(record["packageAbsentObserved"])
            # Every successful cleanup command retains its own exit, timeout and both output streams.
            expected = {
                "forceStop": (0, ""), "uninstall": (0, "Success"),
                "transportProbe": (0, "device"), "postUninstallQuery": (1, ""),
                "packageList": (0, "" if record["package"].endswith(".test") else
                                "package:com.monumentogram.dora.poc.recovery.test"),
            }
            for prefix, (exit_code, output) in expected.items():
                self.assertTrue(record[prefix + "Attempted"])
                self.assertEqual(exit_code, record[prefix + "ExitCode"])
                self.assertFalse(record[prefix + "TimedOut"])
                self.assertEqual(output, record[prefix + "Output"])
                self.assertEqual("", record[prefix + "ErrorOutput"])
        self.assertEqual(0, observation["emulatorCleanup"]["exitCode"])
        self.assertFalse(observation["emulatorCleanup"]["timedOut"])
        self.assertEqual("", observation["emulatorCleanup"]["output"])
        self.assertEqual("", observation["emulatorCleanup"]["errorOutput"])
        option = "-Pandroid.injected.androidTest.leaveApksInstalledAfterRun=true"
        commands = self.gradle_log.read_text(encoding="utf-8").splitlines()
        connected = [line for line in commands if "connectedDebugAndroidTest" in line]
        self.assertEqual(1, len(connected))
        self.assertEqual(1, connected[0].split().count(option))
        assembly = [line for line in commands if "assembleDebug" in line]
        self.assertEqual(2, len(assembly))
        self.assertTrue(all(option not in line for line in assembly))
        events = [json.loads(line) for line in self.apk_lifecycle_log.read_text().splitlines()]
        installed = next(index for index, event in enumerate(events) if event["event"] == "connected-install")
        self.assertTrue(all(event["installed"] == [] for event in events[:installed]))
        self.assertEqual(4, len(events[:installed]))
        removals = [event for event in events if event["event"] == "helper-uninstall-success"]
        self.assertEqual(2, len(removals))
        self.assertTrue(all(event["preserved"] for event in removals))
        self.assertNotIn("utp-default-removal", [event["event"] for event in events])
        self.assertEqual([], events[-1]["installed"])
        self.assertEqual([], list(self.evidence.glob("REC-I3-V8-CLEANUP-FALLBACK-*")))

    def test_helper_uninstall_error_remains_unverified_despite_empty_queries(self) -> None:
        # Treating a nonzero uninstall as success merely because later queries are empty must fail.
        completed = self.invoke(apk_lifecycle=True, apk_uninstall_error=True)
        self.assertNotEqual(0, completed.returncode)
        observation = json.loads(
            next(self.evidence.glob("REC-I3-V8-CLEANUP-*.json")).read_text(encoding="utf-8-sig")
        )
        self.assertTrue(observation["copySucceeded"])
        self.assertEqual("PACKAGE_CLEANUP_UNVERIFIED", observation["cleanupFailure"])
        self.assertEqual(2, len(observation["packageCleanup"]))
        for record in observation["packageCleanup"]:
            self.assertEqual(17, record["uninstallExitCode"])
            self.assertIn("forced helper uninstall error after removal", record["uninstallErrorOutput"])
            self.assertEqual(1, record["postUninstallQueryExitCode"])
            self.assertEqual("", record["postUninstallQueryOutput"])
            self.assertEqual("", record["postUninstallQueryErrorOutput"])
            self.assertFalse(record["packageAbsentObserved"])
        self.assertEqual("", observation["packageCleanup"][-1]["packageListOutput"])
        report = json.loads(next(self.evidence.glob("REC-I3-V8-REPORT-*.json")).read_text(encoding="utf-8-sig"))
        self.assertNotEqual(0, report["cleanupExitCode"])
        self.assertEqual("PRESERVATION_OR_CLEANUP_FAILED", report["primaryFailure"])

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

    def test_preflight_path_permission_stderr_blocks_launch_and_still_cleans(self) -> None:
        # Ignoring stderr on empty exit-1 pm path would consume the attempt despite unverified absence.
        completed = self.invoke(identity_mode="preflight_path_permission")
        self.assertNotEqual(0, completed.returncode, completed.stdout + completed.stderr)
        report = json.loads(
            next(self.evidence.glob("REC-I3-V8-REPORT-*.json")).read_text(encoding="utf-8-sig")
        )
        self.assertIn("PACKAGE_ABSENCE_UNVERIFIED", report["primaryFailure"])
        self.assertFalse(self.ledger.exists())
        self.assertFalse(self.gradle_marker.exists())
        self.assertNotIn("connectedDebugAndroidTest", self.gradle_log.read_text(encoding="utf-8"))
        raw = next(self.evidence.glob("REC-I3-V8-RAW-*"))
        preflight = json.loads((raw / "preflight.json").read_text(encoding="utf-8-sig"))
        path_record = next(item for item in preflight["commands"] if item["name"].startswith("package-path-"))
        self.assertEqual(1, path_record["exitCode"])
        self.assertEqual("", path_record["output"])
        self.assertIn("SecurityException: permission denied", path_record["errorOutput"])
        self.assertIn(
            "SecurityException: permission denied",
            Path(path_record["logPath"] + ".stderr").read_text(encoding="utf-8-sig"),
        )
        list_record = next(item for item in preflight["commands"] if item["name"].startswith("package-list-"))
        self.assertEqual(0, list_record["exitCode"])
        self.assertEqual("", list_record["output"])
        self.assertEqual("", list_record["errorOutput"])
        observation = json.loads(
            next(self.evidence.glob("REC-I3-V8-CLEANUP-*.json")).read_text(encoding="utf-8-sig")
        )
        self.assertTrue(observation["cleanupAttempted"])
        self.assertEqual(2, len(observation["packageCleanup"]))
        for record in observation["packageCleanup"]:
            self.assertEqual(1, record["postUninstallQueryExitCode"])
            self.assertEqual("", record["postUninstallQueryOutput"])
            self.assertEqual("", record["postUninstallQueryErrorOutput"])
            self.assertTrue(record["packageAbsentObserved"])
        calls = self.adb_log.read_text(encoding="utf-8").splitlines()
        for package in ("com.monumentogram.dora.poc.recovery", "com.monumentogram.dora.poc.recovery.test"):
            self.assertIn(f"-s {SERIAL} shell am force-stop {package}", calls)
            uninstall_index = calls.index(f"-s {SERIAL} uninstall {package}")
            self.assertIn(f"-s {SERIAL} shell pm path {package}", calls[uninstall_index + 1:])
        self.assertIn(f"-s {SERIAL} emu kill", calls)

    def test_success_binds_serial_streams_output_and_records_preflights(self) -> None:
        completed = self.invoke()
        # Dropping serial ownership, streamed logs, or any preflight result must fail the host-run contract.
        self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)
        self.assertTrue(self.ledger.is_file())
        ledger = json.loads(self.ledger.read_text(encoding="utf-8-sig"))
        self.assertEqual("ATTEMPT_CONSUMED_EXECUTION_UNKNOWN", ledger["state"])
        self.assertNotIn("completion", ledger)
        completion = json.loads(self.completion.read_text(encoding="utf-8-sig"))
        self.assertEqual("ATTEMPT_CONSUMED_COMPLETED", completion["state"])
        self.assertEqual(0, completion["gradleExitCode"])
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
            completion["firstCheckpointDiagnostic"],
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
        completion = json.loads(self.completion.read_text(encoding="utf-8-sig"))
        self.assertTrue(completion["timedOut"])
        self.assertTrue(any(self.evidence.glob("REC-I3-V8-CLEANUP-*.json")))

    def test_metadata_filesystem_failure_cannot_skip_cleanup(self) -> None:
        blocker = self.root / "metadata-is-a-file"
        blocker.write_text("not a directory", encoding="utf-8")
        completed = self.invoke(metadata_destination=blocker)
        # An actual metadata directory failure after execution must still uninstall and stop the emulator.
        self.assertNotEqual(0, completed.returncode)
        observation = json.loads(
            next(self.evidence.glob("REC-I3-V8-CLEANUP-*.json")).read_text(encoding="utf-8-sig")
        )
        self.assertTrue(observation["cleanupAttempted"])
        self.assertTrue(observation["emulatorCleanup"]["attempted"])
        self.assertTrue(any(" uninstall " in f" {line} " for line in self.adb_log.read_text().splitlines()))

    def test_helper_launch_failure_uses_independent_cleanup_fallback(self) -> None:
        completed = self.invoke(delete_preserver=True)
        # Losing the helper process after execution must activate coordinator-owned bounded cleanup.
        self.assertNotEqual(0, completed.returncode)
        fallback = next(self.evidence.glob("REC-I3-V8-CLEANUP-FALLBACK-*/observation.json"))
        self.assertTrue(fallback.is_file())
        observation = json.loads(fallback.read_text(encoding="utf-8-sig"))
        self.assertTrue(observation["cleanupAttempted"])
        self.assertTrue(any("emu kill" in line for line in self.adb_log.read_text().splitlines()))

    def test_metadata_failure_report_write_cannot_bypass_cleanup(self) -> None:
        blocker = self.root / "metadata-is-a-file"
        blocker.write_text("not a directory", encoding="utf-8")
        completed = self.invoke(metadata_destination=blocker, block_metadata_report=True)
        # A second filesystem exception while reporting metadata failure must still reach cleanup.
        self.assertNotEqual(0, completed.returncode)
        calls = self.adb_log.read_text().splitlines()
        for package in ("com.monumentogram.dora.poc.recovery", "com.monumentogram.dora.poc.recovery.test"):
            self.assertIn(f"-s {SERIAL} uninstall {package}", calls, completed.stdout + completed.stderr)
        self.assertIn(f"-s {SERIAL} emu kill", calls)
        observations = list(self.evidence.glob("REC-I3-V8-CLEANUP-*.json"))
        self.assertEqual(1, len(observations))
        self.assertTrue(json.loads(observations[0].read_text(encoding="utf-8-sig"))["cleanupAttempted"])

    def test_helper_watchdog_timeout_uses_independent_cleanup_fallback(self) -> None:
        completed = self.invoke(helper_watchdog=1)
        # Killing the helper before its observation must still run independently bounded package and emulator cleanup.
        self.assertNotEqual(0, completed.returncode)
        observation = json.loads(
            next(self.evidence.glob("REC-I3-V8-CLEANUP-FALLBACK-*/observation.json")).read_text(encoding="utf-8-sig")
        )
        self.assertTrue(observation["cleanupAttempted"])
        self.assertEqual(2, len(observation["packageCleanup"]))
        for package in observation["packageCleanup"]:
            self.assertEqual(5, len(package["commands"]))
        self.assertEqual(0, observation["emulatorCleanup"]["exitCode"])
        calls = self.adb_log.read_text().splitlines()
        for package in ("com.monumentogram.dora.poc.recovery", "com.monumentogram.dora.poc.recovery.test"):
            self.assertIn(f"-s {SERIAL} uninstall {package}", calls)
        self.assertIn(f"-s {SERIAL} emu kill", calls)

    def test_repeated_preparation_fallback_retains_each_attempt_identity_and_log_set(self) -> None:
        first = self.invoke(governance_exit=7, helper_watchdog=1)
        # Reusing shared fallback filenames must not erase a prior non-consuming preparation attempt.
        self.assertNotEqual(0, first.returncode)
        self.assertFalse(self.ledger.exists())
        first_artifacts = {
            path: path.read_bytes()
            for path in self.evidence.rglob("*")
            if path.is_file() and "fallback" in str(path.relative_to(self.evidence)).lower()
        }
        second = self.invoke(governance_exit=7, helper_watchdog=1)
        self.assertNotEqual(0, second.returncode)
        self.assertFalse(self.ledger.exists())
        observations = list(self.evidence.glob("REC-I3-V8-CLEANUP-FALLBACK-*/observation.json"))
        self.assertEqual(2, len(observations), first.stdout + first.stderr + second.stdout + second.stderr)
        attempts = set()
        for path in observations:
            observation = json.loads(path.read_text(encoding="utf-8-sig"))
            attempts.add(observation["attemptId"])
            self.assertEqual(self.commit, observation["acceptedCommit"])
            self.assertEqual(self.tree, observation["acceptedTree"])
            self.assertEqual(SERIAL, observation["serial"])
            self.assertEqual(11, len(list(path.parent.glob("*.log"))))
            self.assertEqual(11, len(list(path.parent.glob("*.log.stderr"))))
            self.assertTrue(all(log.is_file() for log in path.parent.glob("*.log")))
        self.assertEqual(2, len(attempts))
        self.assertTrue(first_artifacts)
        for path, contents in first_artifacts.items():
            self.assertEqual(contents, path.read_bytes(), str(path))

    def test_fallback_artifact_collision_retains_prior_files_and_still_cleans(self) -> None:
        completed = self.invoke(helper_watchdog=1, block_fallback_artifacts=True)
        # Refusing to replace an existing attempt directory must neither touch its files nor skip cleanup.
        self.assertNotEqual(0, completed.returncode)
        prior = next(self.evidence.glob("REC-I3-V8-CLEANUP-FALLBACK-*"))
        self.assertEqual(["sentinel.txt"], sorted(path.name for path in prior.iterdir()))
        self.assertEqual("retain prior evidence", (prior / "sentinel.txt").read_text())
        relocated = list((self.root / "tmp").glob("DORA-REC-I3-CLEANUP-*/observation.json"))
        self.assertEqual(1, len(relocated), completed.stdout + completed.stderr)
        observation = json.loads(relocated[0].read_text(encoding="utf-8-sig"))
        self.assertIn("FALLBACK_ATTEMPT_ALREADY_EXISTS", observation["evidenceFailure"])
        self.assertEqual(self.commit, observation["acceptedCommit"])
        self.assertEqual(0, observation["emulatorCleanup"]["exitCode"])
        calls = self.adb_log.read_text().splitlines()
        for package in ("com.monumentogram.dora.poc.recovery", "com.monumentogram.dora.poc.recovery.test"):
            self.assertIn(f"-s {SERIAL} uninstall {package}", calls)
        self.assertIn(f"-s {SERIAL} emu kill", calls)

    def test_preparation_failure_without_build_tree_records_cleanup_for_started_or_online_target(self) -> None:
        for already_online in (False, True):
            with self.subTest(already_online=already_online):
                shutil.rmtree(self.evidence, ignore_errors=True)
                self.emulator_started.unlink(missing_ok=True)
                self.adb_log.unlink(missing_ok=True)
                if already_online:
                    self.emulator_started.write_text("present", encoding="utf-8")
                completed = self.invoke(governance_exit=7)
                # Early governance failure must not depend on a generated build tree to record cleanup truth.
                self.assertNotEqual(0, completed.returncode)
                observation_path = next(self.evidence.glob("REC-I3-V8-CLEANUP-*.json"))
                observation = json.loads(observation_path.read_text(encoding="utf-8-sig"))
                self.assertTrue(observation["cleanupAttempted"])
                self.assertTrue(observation["emulatorCleanup"]["attempted"])

    def test_successful_already_online_authorized_emulator_is_stopped(self) -> None:
        self.emulator_started.write_text("present", encoding="utf-8")
        completed = self.invoke()
        # Reusing a verified authorized emulator must not weaken the inherited stop-and-verify contract.
        self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)
        observation = json.loads(
            next(self.evidence.glob("REC-I3-V8-CLEANUP-*.json")).read_text(encoding="utf-8-sig")
        )
        self.assertTrue(observation["emulatorCleanup"]["attempted"])
        self.assertTrue(any(line.endswith("emu kill") for line in self.adb_log.read_text().splitlines()))

    def test_completion_persistence_failure_preserves_create_once_launch_ledger(self) -> None:
        completed = self.invoke(completion_failure=True)
        # Completion write failure must never truncate the durable create-once launch record or permit a retry.
        self.assertNotEqual(0, completed.returncode)
        launch = json.loads(self.ledger.read_text(encoding="utf-8-sig"))
        self.assertEqual("ATTEMPT_CONSUMED_EXECUTION_UNKNOWN", launch["state"])
        self.assertEqual(1, launch["launchRecord"]["instrumentationAttemptCount"])
        second = self.invoke()
        self.assertNotEqual(0, second.returncode)

    def test_required_final_logcat_failure_is_durable_and_fails_run_after_cleanup(self) -> None:
        completed = self.invoke(final_logcat_failure=True)
        # Discarding final-logcat failure must not allow a false-success host report.
        self.assertNotEqual(0, completed.returncode)
        raw = next(self.evidence.glob("REC-I3-V8-RAW-*"))
        result = json.loads((raw / "final-logcat-result.json").read_text(encoding="utf-8-sig"))
        self.assertEqual(9, result["exitCode"])
        self.assertFalse(result["timedOut"])
        completion = json.loads(self.completion.read_text(encoding="utf-8-sig"))
        self.assertEqual(0, completion["gradleExitCode"])
        self.assertTrue(any(self.evidence.glob("REC-I3-V8-CLEANUP-*.json")))

    def test_required_final_logcat_timeout_preserves_instrumentation_result_and_cleanup(self) -> None:
        completed = self.invoke(final_logcat_timeout=True)
        # A timed-out required logcat must fail the host run without changing a completed instrumentation outcome.
        self.assertNotEqual(0, completed.returncode)
        raw = next(self.evidence.glob("REC-I3-V8-RAW-*"))
        result = json.loads((raw / "final-logcat-result.json").read_text(encoding="utf-8-sig"))
        self.assertIsNone(result["exitCode"])
        self.assertTrue(result["timedOut"])
        completion = json.loads(self.completion.read_text(encoding="utf-8-sig"))
        self.assertEqual(0, completion["gradleExitCode"])
        self.assertEqual("ATTEMPT_CONSUMED_COMPLETED", completion["state"])
        observation = json.loads(
            next(self.evidence.glob("REC-I3-V8-CLEANUP-*.json")).read_text(encoding="utf-8-sig")
        )
        self.assertTrue(observation["cleanupAttempted"])
        self.assertEqual(0, observation["emulatorCleanup"]["exitCode"])


if __name__ == "__main__":
    unittest.main()
