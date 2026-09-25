"""Isolated Stage-0 model-init runner. No corpus, inference, normalization or scoring.

Requests and returned attempt records are private. Only public_projection is public.
Engine.start(request, control) checks control immediately before native invocation and
returns a bounded session with nonblocking poll() and terminate(reason).
An adapter must verify actual artifacts before invoking native code and confirm termination.
"""
from __future__ import annotations

import copy
import hashlib
import json
import re
import time

import alpha_asr_eval_text_contract as frozen

VERSION = "dora-alpha-asr-runner-request-v0.1"
ATTEMPT_VERSION = "dora-alpha-asr-runner-attempt-v0.1"
MODEL_BYTES = 59707625
PROBE = b"dora-stage0-generated-model-init-probe-v0.1\n"
OUTCOMES = ("SUCCESS", "MODEL_LOAD_FAILURE", "INPUT_FAILURE", "ENGINE_FAILURE", "TIMEOUT", "CANCELLED")
MAX_ATTEMPTS = 32


class RunnerError(ValueError):
    """Content-free stable code; never wrap external exception text."""


class RunnerStopped(Exception):
    def __init__(self, outcome):
        self.outcome = outcome


class Control:
    def __init__(self, deadline, monotonic, cancelled):
        self.deadline, self.monotonic, self.cancelled = deadline, monotonic, cancelled

    def check(self):
        if self.cancelled():
            raise RunnerStopped("CANCELLED")
        if self.monotonic() >= self.deadline:
            raise RunnerStopped("TIMEOUT")

    def remaining(self):
        self.check()
        return self.deadline - self.monotonic()


def require(condition, code="INVALID_REQUEST"):
    if not condition:
        raise RunnerError(code)


def canonical(value):
    try:
        return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True,
                          allow_nan=False)
    except (ValueError, TypeError):
        raise RunnerError("INVALID_JSON") from None


def parse_request(text):
    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, "DUPLICATE_JSON_KEY")
            result[key] = value
        return result
    try:
        require(type(text) is str and len(text) <= 16384, "INVALID_JSON")
        return json.loads(text, object_pairs_hook=pairs,
                          parse_constant=lambda _: require(False, "INVALID_JSON"))
    except (ValueError, TypeError):
        raise RunnerError("INVALID_JSON") from None


def request_shell(native_candidate_sha256, case_id, locale):
    return {
        "runnerContractVersion": VERSION,
        "profile": frozen.profile(),
        "model": {"sha256": frozen.MODEL_SHA256, "bytes": MODEL_BYTES},
        "runtimeCommit": frozen.RUNTIME_COMMIT,
        "nativeCandidateSha256": native_candidate_sha256,
        "operation": "MODEL_INIT_ONLY",
        "caseId": case_id, "locale": locale, "attemptOrdinal": 1,
        "input": {"kind": "GENERATED_MODEL_INIT_PROBE", "bytes": len(PROBE),
                  "sha256": hashlib.sha256(PROBE).hexdigest()},
        "timeout": {"policy": "MONOTONIC_TERMINATE_AND_VERIFY", "milliseconds": 120000},
        "cancellation": {"policy": "TERMINATE_AND_VERIFY_NO_RETRY", "requested": False},
    }


def validate_request(value, native_candidate_sha256):
    try:
        require(type(native_candidate_sha256) is str and
                re.fullmatch(r"[0-9a-f]{64}", native_candidate_sha256), "INVALID_NATIVE_IDENTITY")
        require(type(value) is dict)
        frozen._identity(value.get("caseId"), value.get("locale"))
        frozen.validate_profile(value.get("profile"))
        expected = request_shell(native_candidate_sha256, value["caseId"], value["locale"])
        frozen._shape(value, expected)
        require(type(value["operation"]) is str and value["operation"] in
                ("MODEL_INIT_ONLY", "LIFECYCLE_HOLD_ONLY"), "INVALID_OPERATION")
        expected["operation"] = value["operation"]
        ordinal = value["attemptOrdinal"]
        require(type(ordinal) is int and 1 <= ordinal <= MAX_ATTEMPTS, "INVALID_ATTEMPT_ORDINAL")
        frozen._shape(value["timeout"], expected["timeout"])
        milliseconds = value["timeout"]["milliseconds"]
        require(type(milliseconds) is int and 1 <= milliseconds <= 120000, "INVALID_TIMEOUT")
        frozen._shape(value["cancellation"], expected["cancellation"])
        requested = value["cancellation"]["requested"]
        require(type(requested) is bool, "INVALID_CANCELLATION")
        expected["attemptOrdinal"] = ordinal
        expected["timeout"]["milliseconds"] = milliseconds
        expected["cancellation"]["requested"] = requested
        frozen._exact(value, expected)
    except frozen.ContractError:
        raise RunnerError("REQUEST_BINDING_MISMATCH") from None


def attempt_record(request, outcome):
    require(outcome in OUTCOMES, "INVALID_OUTCOME")
    evaluation = frozen.result_shell(request["caseId"], request["locale"])
    # Frozen SUCCEEDED means execution AND scoring; successful init must not invent WER.
    if outcome != "SUCCESS":
        evaluation["attemptOrdinal"] = request["attemptOrdinal"]
        evaluation["executionAttemptState"] = "INVALID_INPUT" if outcome == "INPUT_FAILURE" else "FAILED"
        evaluation["errorCategory"] = {
            "INPUT_FAILURE": "INPUT_BINDING_MISMATCH", "TIMEOUT": "RUNNER_TIMEOUT",
        }.get(outcome, "RUNNER_FAILURE")
    frozen.validate_result(evaluation)
    return {"attemptVersion": ATTEMPT_VERSION, "state": "FINISHED", "outcome": outcome,
            "request": copy.deepcopy(request), "evaluationResult": evaluation}


def validate_record(record):
    require(type(record) is dict and type(record.get("request")) is dict, "INVALID_RECORD")
    request = record["request"]
    validate_request(request, request.get("nativeCandidateSha256"))
    require(record.get("outcome") in OUTCOMES, "INVALID_RECORD")
    require(canonical(record) == canonical(attempt_record(request, record["outcome"])), "INVALID_RECORD")


def run(request, native_candidate_sha256, store, engine, *, cancelled=lambda: False,
        monotonic=time.monotonic, sleep=time.sleep):
    # Snapshot prevents caller mutation between identity validation and native invocation.
    request = copy.deepcopy(request)
    validate_request(request, native_candidate_sha256)
    replay = store.begin(request)
    if replay is not None:
        validate_record(replay)
        return replay
    session = None
    outcome = "ENGINE_FAILURE"
    try:
        if request["cancellation"]["requested"] or cancelled():
            outcome = "CANCELLED"
        else:
            deadline = monotonic() + request["timeout"]["milliseconds"] / 1000
            control = Control(deadline, monotonic, cancelled)
            session = engine.start(copy.deepcopy(request), control)
            while True:
                if cancelled():
                    outcome = "CANCELLED"
                    session.terminate(outcome)
                    break
                if monotonic() >= deadline:
                    outcome = "TIMEOUT"
                    session.terminate(outcome)
                    break
                polled = session.poll()
                if polled is not None:
                    outcome = polled if type(polled) is str and polled in OUTCOMES else "ENGINE_FAILURE"
                    break
                sleep(0.01)
    except RunnerStopped as stopped:
        outcome = stopped.outcome
    except Exception:
        outcome = "ENGINE_FAILURE"
        if session is not None:
            try:
                session.terminate(outcome)
            except Exception:
                # Adapter owns detailed private diagnostics and cleanup; no success claim.
                pass
    return store.finish(request, outcome)


def public_projection(records):
    counts = {}
    for record in records:
        validate_record(record)
        outcome = record["outcome"]
        counts[outcome] = counts.get(outcome, 0) + 1
    return {"runnerContractVersion": VERSION, "profileId": frozen.PROFILE_ID,
            "outcomeCounts": counts, "wer": "NOT_RUN", "campaign": "NOT_RUN",
            "productionAdmission": False}


# Imported after definitions because the persistence module uses closed validation above.
from alpha_asr_runner_store import AttemptStore  # noqa: E402
