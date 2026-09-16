"""Validate JUnit completion independently from the campaign's own result events."""
from __future__ import annotations

import json
import re


def _require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def validate_instrumentation_success(
    output: bytes, selector: str, allowed_stream_marker: str = "DORA_RECOVERY_CAMPAIGN_EVENT "
) -> None:
    """Accept one selected test's start/success and one successful runner terminal.

    Custom stream-only status bundles are observations inside that live test.
    They cannot replace JUnit completion. Intentional process-kill operations
    use the external death proof and must not call this success validator.
    """
    _require(isinstance(output, bytes), "Instrumentation output must be raw bytes")
    _require(re.fullmatch(r"[A-Za-z0-9_.$]+#[A-Za-z0-9_$]+", selector) is not None,
             "Expected instrumentation selector is malformed")
    _require(bool(allowed_stream_marker) and "\n" not in allowed_stream_marker and "\r" not in allowed_stream_marker,
             "Expected observation marker is malformed")
    text = output.decode("utf-8", errors="strict")
    _require(not any(marker in text for marker in ("FAILURES!!!", "INSTRUMENTATION_FAILED", "shortMsg=")),
             "Instrumentation contains failure diagnostics")
    expected_class, expected_test = selector.split("#")
    expected = {"class": expected_class, "test": expected_test, "numtests": "1", "current": "1"}
    fields: dict[str, str] = {}
    phase = 0  # No test, live test, successfully completed test.
    terminal = False
    for line in text.splitlines():
        field = re.fullmatch(r"INSTRUMENTATION_STATUS: ([A-Za-z][A-Za-z0-9_]*)=(.*)", line)
        status = re.fullmatch(r"INSTRUMENTATION_STATUS_CODE: (-?[0-9]+)\s*", line)
        end = re.fullmatch(r"INSTRUMENTATION_CODE: (-?[0-9]+)\s*", line)
        if field:
            _require(not terminal, "Status appeared after runner termination")
            key, value = field.groups()
            _require(key not in fields, "Duplicate instrumentation status field")
            fields[key] = value
        elif status:
            _require(not terminal, "Status appeared after runner termination")
            code = int(status.group(1))
            if set(fields) == {"stream"}:
                _require(phase == 1 and code == 0, "Observation appeared outside the selected live test")
                stream = fields["stream"]
                _require(stream.startswith(allowed_stream_marker), "Unknown instrumentation observation")
                observation = json.loads(stream[len(allowed_stream_marker):])
                _require(isinstance(observation, dict), "Instrumentation observation is not an object")
            else:
                _require(all(fields.get(key) == value for key, value in expected.items()),
                         "Instrumentation test identity or count differs")
                _require(set(fields) <= set(expected) | {"id", "stream"}, "Unexpected test status fields")
                _require((phase == 0 and code == 1) or (phase == 1 and code == 0),
                         "Expected exactly one test start and successful completion")
                phase += 1
            fields = {}
        elif end:
            _require(not terminal and not fields and phase == 2 and end.group(1) == "-1",
                     "Runner terminal is missing, duplicated, premature or unsuccessful")
            terminal = True
        elif line.startswith(("INSTRUMENTATION_STATUS", "INSTRUMENTATION_CODE")):
            raise ValueError("Malformed instrumentation status record")
    _require(terminal and phase == 2 and not fields, "Exact instrumentation completion is missing")
