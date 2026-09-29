"""Conservative local gate for synthetic Transcribe diagnostic attempts."""

import hashlib
import json
import math
import os
import tempfile
from datetime import datetime, timezone
from pathlib import Path


MODES = {"ENGINEERING_SANDBOX", "ADMISSION_REPRODUCTION"}
MAX_ATTEMPTS = 12


def _json(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), allow_nan=False)


def _sync_directory(path):
    if os.name == "nt":  # Windows does not permit opening directories this way.
        return
    fd = os.open(path, os.O_RDONLY | os.O_DIRECTORY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def _write_once(path, value):
    payload = (_json(value) + "\n").encode("utf-8")
    fd, temporary = tempfile.mkstemp(prefix=".pending-", dir=path.parent)
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(payload)
            stream.flush()
            os.fsync(stream.fileno())
        os.link(temporary, path)  # Atomic and refuses to replace an earlier record.
        _sync_directory(path.parent)
    finally:
        os.unlink(temporary)


def _fingerprint(request):
    semantic = dict(request)
    semantic.pop("TranscriptionJobName", None)
    semantic.pop("mode", None)
    if isinstance(semantic.get("Media"), dict):
        semantic["Media"] = dict(semantic["Media"])
        semantic["Media"].pop("MediaFileUri", None)
    return hashlib.sha256(_json(semantic).encode("utf-8")).hexdigest()


def _is_sha256(value):
    return isinstance(value, str) and len(value) == 64 and all(c in "0123456789abcdef" for c in value)


def reserve(root: Path, request: dict, hypothesis: str, delta: str,
            duration: float, reproduce_success: bool = False) -> Path:
    """Persist an intent before any API call; return its attempt directory."""
    if not isinstance(request, dict) or request.get("mode") not in MODES:
        raise ValueError("diagnostic mode is required")
    if request.get("audio_kind") != "synthetic":
        raise ValueError("only synthetic audio is allowed")
    if not _is_sha256(request.get("audio_sha256")):
        raise ValueError("synthetic audio SHA-256 is required")
    if not _is_sha256(request.get("config_sha256")):
        raise ValueError("observed IAM/S3/KMS config SHA-256 is required")
    if not isinstance(duration, (int, float)) or isinstance(duration, bool) or not math.isfinite(duration) or not 2 <= duration <= 5:
        raise ValueError("synthetic audio must be 2 to 5 seconds")
    if not isinstance(hypothesis, str) or not hypothesis.strip() or not isinstance(delta, str) or not delta.strip():
        raise ValueError("hypothesis and delta are required")
    if not isinstance(reproduce_success, bool):
        raise ValueError("reproduce_success must be boolean")
    serialized = _json(request)
    fingerprint = _fingerprint(request)
    root = Path(root)
    ledger = root / "diagnostic-ledger"
    ledger.mkdir(parents=True, exist_ok=True)
    lock = ledger / ".reserve-lock"
    os.mkdir(lock)  # A stranded lock fails closed after an interrupted reservation.
    try:
        attempts = sorted(p for p in ledger.glob("attempt-*") if p.is_dir())
        if len(attempts) >= MAX_ATTEMPTS:
            raise ValueError("12-attempt diagnostic cap reached")
        for attempt in attempts:
            try:
                intent = json.loads((attempt / "intent.json").read_text(encoding="utf-8"))
            except (OSError, ValueError) as exc:
                raise ValueError("unknown attempt intent blocks reservation") from exc
            if intent.get("fingerprint") != fingerprint:
                continue
            try:
                outcome = json.loads((attempt / "result.json").read_text(encoding="utf-8"))
            except (OSError, ValueError):
                raise ValueError("duplicate request has unknown outcome") from None
            if not (reproduce_success and outcome.get("completed") is True):
                raise ValueError("duplicate request is not eligible for replay")
        number = max((int(p.name[8:]) for p in attempts), default=0) + 1
        attempt = ledger / f"attempt-{number:03d}"
        attempt.mkdir()
        _sync_directory(ledger)
        _write_once(attempt / "intent.json", {
            "timestamp_utc": datetime.now(timezone.utc).isoformat(),
            "request": json.loads(serialized),
            "fingerprint": fingerprint,
            "hypothesis": hypothesis,
            "delta": delta,
            "duration_seconds": duration,
            "reserved_seconds": 15,
            "reserved_usd": "0.01",
            "reproduce_success": reproduce_success,
        })
        return attempt
    finally:
        os.rmdir(lock)


def finish(attempt: Path, result: dict, created: bool, completed: bool) -> None:
    """Persist one immutable outcome; unfinished and missing outcomes stay uncertain."""
    attempt = Path(attempt)
    if not (attempt / "intent.json").is_file():
        raise ValueError("attempt has no durable intent")
    if not isinstance(result, dict) or type(created) is not bool or type(completed) is not bool or completed and not created:
        raise ValueError("invalid diagnostic outcome")
    _write_once(attempt / "result.json", {
        "timestamp_utc": datetime.now(timezone.utc).isoformat(),
        "result": result,
        "created": created,
        "completed": completed,
    })
