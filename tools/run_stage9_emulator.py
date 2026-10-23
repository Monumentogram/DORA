"""Synthetic Stage 9 checks. Refuses physical devices and emits content-free receipts."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import uuid


def validate_serial(serial):
    if not re.fullmatch(r"emulator-[0-9]+", serial):
        raise ValueError("An explicit emulator serial is required")


def successful(output, count):
    return (re.search(rf"OK \({count} tests?\)", output) is not None
            and not re.search(r"FAILURES|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|INSTRUMENTATION_STATUS_CODE: -[234]", output))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--api", type=int, choices=(28, 36), required=True)
    parser.add_argument("--apk-root", type=Path, required=True)
    parser.add_argument("--receipt", type=Path, required=True)
    args = parser.parse_args()
    validate_serial(args.serial)
    adb = shutil.which("adb")
    if not adb:
        adb = str(Path(os.environ["ANDROID_HOME"]) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb"))

    def command(*parts, timeout=180):
        return subprocess.run([adb, "-s", args.serial, *parts], check=True,
                              capture_output=True, text=True, timeout=timeout).stdout

    if command("shell", "getprop", "ro.kernel.qemu").strip() != "1":
        raise RuntimeError("Target is not an emulator")
    if command("shell", "getprop", "ro.build.version.sdk").strip() != str(args.api):
        raise RuntimeError("Unexpected emulator API")
    receipt = {"scope": "synthetic-only-no-native-ASR-claim", "api": args.api,
               "sha": subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip(),
               "apks": {}, "checks": []}
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    try:
        for name in ("app-debug.apk", "app-debug-androidTest.apk", "audio-debug-androidTest.apk"):
            matches = list(args.apk_root.rglob(name))
            if len(matches) != 1:
                raise RuntimeError("Expected exactly one " + name)
            apk = matches[0]
            receipt["apks"][name] = hashlib.sha256(apk.read_bytes()).hexdigest()
            if "Success" not in command("install", "-r", "-t", str(apk)):
                raise RuntimeError("Install failed")

        def instrument(label, package, classes, count, extra=()):
            output = command("shell", "am", "instrument", "-w", "-r", "-e", "class", classes,
                             *extra, package + "/androidx.test.runner.AndroidJUnitRunner", timeout=900)
            passed = successful(output, count)
            receipt["checks"].append({"name": label, "expectedTests": count, "passed": passed})
            # Tests contain only synthetic fixtures; failure output is useful for diagnosing CI.
            if not passed:
                print(output)
                raise RuntimeError(label + " failed or skipped")

        prefix = "com.monumentogram.dora.audio.persistence."
        instrument("encrypted-persistence-and-migration", "com.monumentogram.dora.audio.test",
                   prefix + "TranscriptPersistenceTest," + prefix + "journal.TranscriptMigrationTest", 5)
        instrument("bounded-recording-and-pin-free-authority", "com.monumentogram.dora.audio.test",
                   prefix + "AlphaRecordingRuntimeTest," + prefix + "auth.AndroidLocalAccessTest", 3)
        run = "ci-" + uuid.uuid4().hex
        for phase, method in (("SEED", "seed"), ("READ", "readAfterProcessRestart")):
            command("shell", "am", "force-stop", "com.monumentogram.dora.audio.test")
            instrument("process-" + phase.lower(), "com.monumentogram.dora.audio.test",
                       prefix + "TranscriptProcessRestartTest#" + method, 1,
                       ("-e", "transcriptProcessPhase", phase, "-e", "transcriptProcessRun", run))
        instrument("transcript-editor", "com.monumentogram.dora.debug.test",
                   "com.monumentogram.dora.transcript.TranscriptUiTest,"
                   "com.monumentogram.dora.transcript.ModelPickerLifecycleTest,"
                   "com.monumentogram.dora.AlphaRecordingUiTest", 6)
        receipt["result"] = "PASS"
    finally:
        receipt.setdefault("result", "FAIL")
        args.receipt.write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
