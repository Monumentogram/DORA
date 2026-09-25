"""Narrow physical Android model-init adapter. No inference or production app.

Prepare verifies host and device bytes; start rechecks both before native execution.
Paths/serial/subprocess diagnostics are private and never returned in public evidence.
Caller must cleanup in finally. Each adapter owns a fresh unpredictable device directory.
"""
from __future__ import annotations

import hashlib
import copy
from pathlib import Path
import re
import subprocess
import time
import uuid

import alpha_asr_eval_text_contract as frozen
from alpha_asr_runner import MODEL_BYTES, RunnerError, canonical, require, validate_request

NATIVE_NAMES = ("libc++_shared.so", "libggml-base.so", "libggml-cpu.so", "libggml.so",
                "libwhisper.so", "dora-asr-preflight")


def manifest_shell(files):
    return {"version": "dora-alpha-asr-native-candidate-v0.1", "runtimeCommit": frozen.RUNTIME_COMMIT,
            "ndk": "28.2.13676358", "cmake": "3.22.1", "api": 28, "abi": "arm64-v8a",
            "cpuArchitecture": "armv8-a", "buildType": "Release", "stl": "c++_shared",
            "cpuOnly": True, "files": files}


def validate_manifest(value):
    try:
        require(type(value) is dict and type(value.get("files")) is dict, "INVALID_NATIVE_MANIFEST")
        require(set(value["files"]) == set(NATIVE_NAMES), "INVALID_NATIVE_MANIFEST")
        for row in value["files"].values():
            frozen._shape(row, ("bytes", "sha256"))
            frozen._integer(row["bytes"], 1)
            frozen._hash(row["sha256"])
        frozen._exact(value, manifest_shell(value["files"]))
    except frozen.ContractError:
        raise RunnerError("INVALID_NATIVE_MANIFEST") from None


def file_identity(path):
    path = Path(path)
    require(path.is_file() and not path.is_symlink(), "ARTIFACT_UNAVAILABLE")
    with path.open("rb") as source:
        return {"bytes": path.stat().st_size, "sha256": hashlib.file_digest(source, "sha256").hexdigest()}


def verify_model(path):
    try:
        require(file_identity(path) == {"bytes": MODEL_BYTES, "sha256": frozen.MODEL_SHA256},
                "MODEL_IDENTITY_MISMATCH")
    except (OSError, RunnerError):
        raise RunnerError("MODEL_IDENTITY_MISMATCH") from None


def verify_native_files(paths, manifest):
    validate_manifest(manifest)
    require(set(paths) == set(NATIVE_NAMES), "NATIVE_IDENTITY_MISMATCH")
    try:
        for name in NATIVE_NAMES:
            require(file_identity(paths[name]) == manifest["files"][name], "NATIVE_IDENTITY_MISMATCH")
    except (OSError, RunnerError):
        raise RunnerError("NATIVE_IDENTITY_MISMATCH") from None


def parse_native_receipt(returncode, stdout):
    # Windows ADB can emit CRLF even with PTY disabled. Accept only complete,
    # uniformly delimited receipts; never strip or accept trailing content.
    if returncode == 0 and stdout in (b"DORA_ASR_BOUNDARY_V1\nMODEL_INIT_SUCCESS\n",
                                     b"DORA_ASR_BOUNDARY_V1\r\nMODEL_INIT_SUCCESS\r\n"):
        return "SUCCESS"
    if returncode == 2 and stdout in (b"DORA_ASR_BOUNDARY_V1\nMODEL_LOAD_FAILURE\n",
                                     b"DORA_ASR_BOUNDARY_V1\r\nMODEL_LOAD_FAILURE\r\n"):
        return "MODEL_LOAD_FAILURE"
    return "ENGINE_FAILURE"


def parse_process_state(data):
    require(data in (b"LIVE\n", b"GONE\n", b"PENDING\n", b"LIVE\r\n", b"GONE\r\n", b"PENDING\r\n"),
            "DEVICE_PROCESS_IDENTITY_UNVERIFIED")
    return data.decode("ascii").strip()


class AdbEngine:
    def __init__(self, adb, model, paths, manifest):
        validate_manifest(manifest)
        self.adb, self.model = str(adb), Path(model)
        self.paths, self.manifest = dict(paths), copy.deepcopy(manifest)
        self.identity = hashlib.sha256(canonical(manifest).encode("ascii")).hexdigest()
        self.directory = "/data/local/tmp/dora-asr-" + uuid.uuid4().hex
        self.serial = None
        self.owned = False
        self.session = None
        self.control = None

    def command(self, args, timeout=30):
        prefix = [self.adb] + (["-s", self.serial] if self.serial else [])
        if self.control is not None:
            timeout = min(timeout, self.control.remaining())
        try:
            result = subprocess.run(prefix + args, capture_output=True, timeout=timeout)
            if self.control is not None:
                self.control.check()
            require(result.returncode == 0, "ADB_OPERATION_FAILED")
            return result.stdout
        except (OSError, subprocess.TimeoutExpired):
            if self.control is not None:
                self.control.check()
            raise RunnerError("ADB_OPERATION_FAILED") from None

    def shell(self, script):
        return self.command(["shell", "-T", script])

    def prepare(self, request):
        validate_request(request, self.identity)
        verify_model(self.model)
        verify_native_files(self.paths, self.manifest)
        devices = self.command(["devices"]).decode("ascii", errors="strict").splitlines()[1:]
        rows = [line.split() for line in devices if line.strip()]
        require(len(rows) == 1 and len(rows[0]) == 2, "BLOCKED_ANDROID_ARM64_RUNTIME_UNAVAILABLE")
        require(rows[0][1] != "unauthorized", "BLOCKED_ADB_DEVICE_UNAUTHORIZED")
        require(rows[0][1] == "device", "BLOCKED_ANDROID_ARM64_RUNTIME_UNAVAILABLE")
        self.serial = rows[0][0]
        props = {}
        for key in ("ro.build.version.sdk", "ro.product.cpu.abi", "ro.product.cpu.abilist",
                    "ro.build.type", "ro.build.version.release"):
            props[key] = self.shell("getprop " + key).decode("ascii").strip()
        require(props["ro.build.version.sdk"].isdigit() and int(props["ro.build.version.sdk"]) >= 28,
                "BLOCKED_ANDROID_API_BELOW_28")
        require("arm64-v8a" in props["ro.product.cpu.abilist"].split(","), "BLOCKED_ANDROID_ARM64_UNAVAILABLE")
        require(not self.owned, "ALREADY_PREPARED")
        self.shell("umask 077; mkdir " + self.directory)
        self.owned = True
        self.shell("printf '%s' " + self.identity + " > " + self.directory + "/owner")
        for name in NATIVE_NAMES:
            self.command(["push", str(self.paths[name]), self.directory + "/" + name])
        self.command(["push", str(self.model), self.directory + "/model.bin"])
        self.shell("chmod 700 " + self.directory + "/dora-asr-preflight")
        self.verify_remote()
        return props

    def verify_remote(self):
        require(self.owned, "DEVICE_NOT_PREPARED")
        expected = dict(self.manifest["files"])
        expected["model.bin"] = {"bytes": MODEL_BYTES, "sha256": frozen.MODEL_SHA256}
        names = list(NATIVE_NAMES) + ["model.bin"]
        output = self.shell("cd " + self.directory + " && sha256sum " + " ".join(names))
        rows = output.decode("ascii").splitlines()
        require(len(rows) == len(names), "DEVICE_ARTIFACT_MISMATCH")
        for line, name in zip(rows, names):
            pieces = line.split()
            require(len(pieces) == 2 and pieces[0] == expected[name]["sha256"] and pieces[1] == name,
                    "DEVICE_ARTIFACT_MISMATCH")

    def start(self, request, control):
        validate_request(request, self.identity)
        require(hashlib.sha256(canonical(self.manifest).encode("ascii")).hexdigest() == self.identity,
                "NATIVE_IDENTITY_MISMATCH")
        require(self.session is None, "NATIVE_REPLAY_FORBIDDEN")
        control.check()
        verify_model(self.model)
        control.check()
        verify_native_files(self.paths, self.manifest)
        control.check()
        self.control = control
        try:
            self.verify_remote()
        finally:
            self.control = None
        mode = "hold" if request["operation"] == "LIFECYCLE_HOLD_ONLY" else "init"
        script = ("cd " + self.directory + " && "
                  "exec env LD_LIBRARY_PATH=" + self.directory + " " + self.directory +
                  "/dora-asr-preflight " + mode)
        control.check()
        try:
            process = subprocess.Popen([self.adb, "-s", self.serial, "shell", "-T", script],
                                       stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        except OSError:
            raise RunnerError("ENGINE_FAILURE") from None
        self.session = AdbSession(self, process)
        return self.session

    def cleanup(self):
        if not self.owned:
            return "NOT_NEEDED"
        if self.session:
            self.session.terminate("CANCELLED")
        require(re.fullmatch(r"/data/local/tmp/dora-asr-[0-9a-f]{32}", self.directory), "CLEANUP_SCOPE_INVALID")
        owner = self.shell("cat " + self.directory + "/owner").decode("ascii")
        require(owner == self.identity, "CLEANUP_OWNERSHIP_MISMATCH")
        self.shell("rm -rf " + self.directory)
        self.shell("test ! -e " + self.directory)
        self.owned = False
        return "VERIFIED"


class AdbSession:
    def __init__(self, engine, process):
        self.engine, self.process = engine, process
        self.receipt = None

    def remote_state(self):
        directory = self.engine.directory
        script = ("if test ! -f " + directory + "/pid; then echo PENDING; else "
                  "p=$(cat " + directory + "/pid) || exit 31; "
                  "case $p in '') echo PENDING; exit 0;; 0|*[!0-9]*) exit 31;; esac; "
                  "if test ! -d /proc/$p; then echo GONE; exit 0; fi; "
                  "c=$(tr '\\000' ' ' < /proc/$p/cmdline 2>/dev/null) || { "
                  "if test ! -d /proc/$p; then echo GONE; exit 0; fi; exit 33; }; "
                  "case $c in '" + directory + "/dora-asr-preflight '*) echo LIVE;; "
                  "'') echo GONE;; *) exit 32;; esac; fi")
        return parse_process_state(self.engine.shell(script))

    def poll(self):
        if self.process.poll() is None:
            return None
        if self.receipt is None:
            output, _ = self.process.communicate(timeout=5)
            self.receipt = parse_native_receipt(self.process.returncode, output)
        return self.receipt

    def terminate(self, reason):
        # Kill only the exact executable belonging to our directory, never a global process name.
        directory = self.engine.directory
        for _ in range(50):
            state = self.remote_state()
            if state != "PENDING":
                break
            time.sleep(0.1)
        else:
            if self.process.poll() is None:
                self.process.terminate()
                self.process.communicate(timeout=5)
            raise RunnerError("DEVICE_PROCESS_START_UNVERIFIED")
        script = ("if test -f " + directory + "/pid; then p=$(cat " + directory + "/pid); "
                  "case $p in ''|0|*[!0-9]*) exit 31;; esac; "
                  "if test -d /proc/$p; then "
                  "c=$(tr '\\000' ' ' < /proc/$p/cmdline) || { "
                  "if test -d /proc/$p; then exit 33; fi; c=''; }; "
                  "case $c in '" + directory + "/dora-asr-preflight '*) "
                  "kill -TERM $p || { test ! -d /proc/$p || exit 33; };; "
                  "'') :;; *) exit 32;; esac; fi; fi")
        self.engine.shell(script)
        for _ in range(50):
            if self.remote_state() == "GONE":
                break
            time.sleep(0.1)
        else:
            raise RunnerError("DEVICE_PROCESS_TERMINATION_UNVERIFIED")
        if self.process.poll() is None:
            self.process.terminate()
        try:
            self.process.communicate(timeout=5)
        except subprocess.TimeoutExpired:
            self.process.kill()
            self.process.communicate(timeout=5)
