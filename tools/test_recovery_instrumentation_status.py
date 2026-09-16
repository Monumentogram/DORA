"""Exact Android instrumentation completion, including failure after a RESULT event."""
import importlib.util
from pathlib import Path
import unittest

_path = Path(__file__).with_name("recovery_instrumentation_status.py")
_spec = importlib.util.spec_from_file_location("recovery_instrumentation_status", _path)
_parser = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_parser)
validate = _parser.validate_instrumentation_success

SELECTOR = "example.Campaign#execute"
START = """INSTRUMENTATION_STATUS: class=example.Campaign
INSTRUMENTATION_STATUS: current=1
INSTRUMENTATION_STATUS: id=AndroidJUnitRunner
INSTRUMENTATION_STATUS: numtests=1
INSTRUMENTATION_STATUS: stream=
INSTRUMENTATION_STATUS: test=execute
INSTRUMENTATION_STATUS_CODE: 1
"""
EVENT = 'INSTRUMENTATION_STATUS: stream=DORA_RECOVERY_CAMPAIGN_EVENT {"eventType":"RESULT"}\nINSTRUMENTATION_STATUS_CODE: 0\n'
SUCCESS = START.replace("stream=\n", "stream=.\n").replace("STATUS_CODE: 1", "STATUS_CODE: 0")
TERMINAL = "INSTRUMENTATION_RESULT: stream=\nTime: 0.01\n\nOK (1 test)\nINSTRUMENTATION_CODE: -1\n"
VALID = START + EVENT + SUCCESS + TERMINAL


class InstrumentationCompletionTests(unittest.TestCase):
    def assert_rejected(self, text):
        with self.assertRaises(ValueError):
            validate(text.encode(), SELECTOR)

    def test_exact_test_and_custom_event_complete(self):
        validate(VALID.encode(), SELECTOR)
        validate(VALID.replace("\n", "\r\n").encode(), SELECTOR)

    def test_multiple_live_stream_events_allowed(self):
        validate((START + EVENT.replace("RESULT", "READY") + EVENT + SUCCESS + TERMINAL).encode(), SELECTOR)

    def test_result_then_process_crash_is_not_success(self):
        self.assert_rejected(START + EVENT + "INSTRUMENTATION_RESULT: shortMsg=Process crashed.\nINSTRUMENTATION_CODE: 0\n")

    def test_result_then_junit_failure_or_skip_is_rejected(self):
        for code in (-1, -2, -3, -4):
            with self.subTest(code=code):
                self.assert_rejected(VALID.replace(SUCCESS, SUCCESS.replace("STATUS_CODE: 0", f"STATUS_CODE: {code}")))

    def test_wrong_selector_or_multiple_tests_is_rejected(self):
        for original, replacement in (("class=example.Campaign", "class=example.Other"),
                                      ("test=execute", "test=other"),
                                      ("numtests=1", "numtests=2"), ("current=1", "current=2")):
            with self.subTest(field=original):
                self.assert_rejected(VALID.replace(original, replacement))

    def test_missing_duplicate_or_wrong_terminal_is_rejected(self):
        for terminal in ("", "INSTRUMENTATION_CODE: 0\n", TERMINAL + "INSTRUMENTATION_CODE: 0\n", TERMINAL + TERMINAL):
            with self.subTest(terminal=terminal):
                self.assert_rejected(START + EVENT + SUCCESS + terminal)

    def test_missing_duplicate_and_reordered_test_status_is_rejected(self):
        for text in (EVENT + SUCCESS + TERMINAL, START + EVENT + TERMINAL,
                     START + START + EVENT + SUCCESS + TERMINAL,
                     SUCCESS + START + EVENT + TERMINAL,
                     START + EVENT + SUCCESS + SUCCESS + TERMINAL):
            with self.subTest(text=text):
                self.assert_rejected(text)

    def test_unknown_or_malformed_stream_observation_is_rejected(self):
        for event in (EVENT.replace("DORA_RECOVERY_CAMPAIGN_EVENT", "OTHER_EVENT"),
                      EVENT.replace('{"eventType":"RESULT"}', "not-json"),
                      EVENT.replace('{"eventType":"RESULT"}', "[]"),
                      EVENT.replace("STATUS_CODE: 0", "STATUS_CODE: 2")):
            with self.subTest(event=event):
                self.assert_rejected(START + event + SUCCESS + TERMINAL)

    def test_duplicate_or_unfinished_status_fields_are_rejected(self):
        self.assert_rejected(VALID.replace("INSTRUMENTATION_STATUS: test=execute", "INSTRUMENTATION_STATUS: test=execute\nINSTRUMENTATION_STATUS: test=execute", 1))
        self.assert_rejected(VALID + "INSTRUMENTATION_STATUS: class=example.Campaign\n")

    def test_custom_event_outside_live_test_is_rejected(self):
        self.assert_rejected(EVENT + START + SUCCESS + TERMINAL)
        self.assert_rejected(START + SUCCESS + EVENT + TERMINAL)

    def test_failure_summary_cannot_coexist_with_success(self):
        for failure in ("FAILURES!!!", "INSTRUMENTATION_FAILED: test", "INSTRUMENTATION_RESULT: shortMsg=crashed"):
            with self.subTest(failure=failure):
                self.assert_rejected(VALID + failure + "\n")

    def test_invalid_utf8_is_rejected(self):
        with self.assertRaises(ValueError):
            validate(VALID.encode() + b"\xff", SELECTOR)


if __name__ == "__main__":
    unittest.main()
