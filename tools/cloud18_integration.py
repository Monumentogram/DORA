"""Synthetic Stage18 HTTPS integration; emulator targets only, never AWS.

Private fixture keys, ledger and diagnostic output stay in git-ignored tmp/.
Only the content-free receipt is suitable for CI publication.
"""
import argparse
import base64
import hashlib
import http.client
import json
import os
from pathlib import Path
import re
import shutil
import socket
import ssl
import subprocess
import sys
import tempfile
import time


def validate_serial(serial):
    if not re.fullmatch(r"emulator-[0-9]+", serial):
        raise ValueError("An explicit emulator serial is required")


def successful(output, count):
    return (re.search(rf"OK \({count} tests?\)", output) is not None
            and not re.search(r"FAILURES|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|INSTRUMENTATION_STATUS_CODE: -[234]", output))


def wait_for_fixture(process, certificate, timeout=60):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if process.poll() is not None:
            raise RuntimeError("Synthetic fixture exited before readiness")
        if certificate.is_file():
            connection = None
            try:
                context = ssl.create_default_context(cafile=str(certificate))
                connection = http.client.HTTPSConnection("127.0.0.1", 8443, context=context, timeout=1)
                connection.request("POST", "/v1/session", "{}", {"Content-Type": "application/json"})
                response = connection.getresponse()
                if response.status == 401 and json.loads(response.read(4096)).get("error_code") == "AUTH_INVALID":
                    return
            except (OSError, ValueError, http.client.HTTPException):
                pass
            finally:
                if connection is not None:
                    connection.close()
        time.sleep(0.2)
    raise RuntimeError("Synthetic HTTPS fixture readiness timed out")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--api", type=int, choices=(28, 36), required=True)
    parser.add_argument("--apk-root", type=Path, required=True)
    parser.add_argument("--receipt", type=Path, required=True)
    parser.add_argument("--python", default=sys.executable, help="Python with backend requirements installed")
    parser.add_argument("--adb", help="Optional explicit adb executable")
    args = parser.parse_args(argv)
    validate_serial(args.serial)
    repo = Path(__file__).resolve().parents[1]
    adb = args.adb or shutil.which("adb")
    if not adb:
        sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
        if not sdk:
            raise RuntimeError("adb or ANDROID_HOME is required")
        adb = str(Path(sdk) / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb"))

    def command(*parts, timeout=180):
        return subprocess.run([adb, "-s", args.serial, *parts], check=True,
                              capture_output=True, text=True, timeout=timeout).stdout

    # Validate target before installing packages, reading app files or running tests.
    if command("shell", "getprop", "ro.kernel.qemu").strip() != "1":
        raise RuntimeError("Target is not an emulator")
    if command("shell", "getprop", "ro.build.version.sdk").strip() != str(args.api):
        raise RuntimeError("Unexpected emulator API")
    receipt = {
        "scope": "synthetic-https-no-aws-no-physical-device", "api": args.api,
        "AWS_LIVE_RUNTIME": "NOT_RUN",
        "sha": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=repo, text=True).strip(),
        "workingTreeDirty": bool(subprocess.check_output(["git", "status", "--porcelain"], cwd=repo, text=True).strip()),
        "apks": {}, "checks": [],
    }
    args.receipt.parent.mkdir(parents=True, exist_ok=True)
    private_root = repo / "tmp"
    private_root.mkdir(exist_ok=True)
    private = Path(tempfile.mkdtemp(prefix="cloud18-", dir=private_root))
    process = None
    log = None
    try:
        for name in ("app-debug.apk", "app-debug-androidTest.apk", "audio-debug-androidTest.apk"):
            matches = list(args.apk_root.rglob(name))
            if len(matches) != 1:
                raise RuntimeError("Expected exactly one " + name)
            apk = matches[0].resolve()
            receipt["apks"][name] = hashlib.sha256(apk.read_bytes()).hexdigest()
            if "Success" not in command("install", "-r", "-t", str(apk)):
                raise RuntimeError("APK installation failed")

        def instrument(label, package, selection, count, extra=()):
            output = command("shell", "am", "instrument", "-w", "-r", "-e", "class", selection,
                             *extra, package + "/androidx.test.runner.AndroidJUnitRunner", timeout=900)
            (private / (label + ".log")).write_text(output, encoding="utf-8")
            passed = successful(output, count)
            receipt["checks"].append({"name": label, "expectedTests": count, "passed": passed})
            if not passed:
                raise RuntimeError(label + " failed or skipped; inspect private diagnostic log")

        app = "com.monumentogram.dora.debug"
        test_package = app + ".test"
        instrument("cloud-failure-recovery", test_package,
                   "com.monumentogram.dora.transcript.CloudFailureRecoveryTest", 3)
        instrument("export-provider-faults", test_package,
                   "com.monumentogram.dora.transcript.TextExportProviderTest", 1)
        cloud_class = "com.monumentogram.dora.transcript.CloudHttpIntegrationTest"
        command("shell", "am", "force-stop", app)
        fixture_args = ("-e", "cloudFixtureEnabled", "true")
        instrument("prepare-installation", test_package, cloud_class + "#prepareInvitation", 1, fixture_args)
        invitation = command("exec-out", "run-as", app, "cat", "files/cloud-test-invitation.json")
        if set(json.loads(invitation)) != {"installation_id", "public_key_spki_base64"}:
            raise RuntimeError("Unexpected public invitation shape")
        invitation_path = private / "invitation.json"
        invitation_path.write_text(invitation, encoding="utf-8")
        # Avoid accidentally connecting to another process already listening on the fixed test port.
        with socket.socket() as probe:
            probe.bind(("127.0.0.1", 8443))
        log = (private / "fixture.log").open("w", encoding="utf-8")
        environment = dict(os.environ, AWS_EC2_METADATA_DISABLED="true")
        process = subprocess.Popen(
            [args.python, str(repo / "backend/tests/fixture_server.py"), "--invitation", str(invitation_path),
             "--state-dir", str(private / "fixture"), "--host", "127.0.0.1", "--port", "8443"],
            cwd=repo, env=environment, stdout=log, stderr=subprocess.STDOUT,
            creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0,
        )
        certificate = private / "fixture/tls.crt"
        wait_for_fixture(process, certificate)
        encoded = base64.b64encode(ssl.PEM_cert_to_DER_cert(certificate.read_text(encoding="ascii"))).decode("ascii")
        instrument("signed-cloud-only-and-edits", test_package,
                   cloud_class + "#signedCloudOnlyAndEditedRecordingRoundTrip", 1,
                   fixture_args + ("-e", "cloudCertificate", encoded))
        command("shell", "am", "force-stop", app)
        instrument("cloud-process-restart", test_package,
                   cloud_class + "#cloudAndEditsRemainAfterProcessRestart", 1, fixture_args)
        instrument("cloud-encrypted-persistence", "com.monumentogram.dora.audio.test",
                   "com.monumentogram.dora.audio.persistence.CloudTranscriptPersistenceTest", 2)
        instrument("consent-and-transcript-editor", test_package,
                   "com.monumentogram.dora.transcript.TranscriptUiTest", 1)
        instrument("encrypted-cancellation-outbox", test_package,
                   "com.monumentogram.dora.transcript.CloudCancellationPersistenceTest", 2)
        receipt["result"] = "PASS"
        return 0
    finally:
        if process is not None:
            process.terminate()
            try:
                process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait(timeout=10)
        if log is not None:
            log.close()
        receipt.setdefault("result", "FAIL")
        args.receipt.write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
        print("Private synthetic diagnostics retained at " + str(private))


if __name__ == "__main__":
    raise SystemExit(main())
