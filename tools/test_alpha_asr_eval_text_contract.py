"""Synthetic-only tests; no model, corpus or manifest access."""
import copy
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import unittest

try:
    import alpha_asr_eval_text_contract as c
except ModuleNotFoundError:
    c = None

FIXTURES = (
    ("HELLO, WORLD!", "hello world "),
    ("ПРИВЕТ, МИР!", "привет мир "),
    ("  One\t\nTWO\u00a0three  ", " one two three "),
    ("one<noise>two", "onetwo"), ("one[noise]two", "onetwo"),
    ("one(noise)two", "onetwo"), ("ＡＢＣ １２ ﬃ", "abc 12 ffi"),
    ("$42 + €7—ok", " 42 7 ok"), ("CAFÉ naïve Й Ё", "café naïve й ё"),
    ("CAFE\u0301 И\u0306 Е\u0308", "café й ё"),
    ("x\u0301y", "x y"), ("İ", "i "), ("ᴬ", "a"),
    ("a<hidden]b", "ab"), ("a[hidden>b", "ab"),
    ("a(outer(inner)tail)b", "atail b"), ("a()b", "a b"),
    ("a<unterminated", "a unterminated"), ("a<line\nbreak>b", "ab"),
    ("a(line\nbreak)b", "ab"), ("a［noise］b", "a noise b"),
    ("a\u200bb", "a\u200bb"), ("", ""), ("[noise]", ""), ("!!!", " "),
    ("a\x1cb", "a b"),
)


class ContractTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(c, "host contract implementation is missing")

    def request(self, reference="One TWO!", hypothesis="one two"):
        return dict(profile=c.profile(), caseId="sample-0000000000000001", locale="en",
                    referenceText=reference, hypothesisText=hypothesis)

    def result(self):
        v = c.result_shell("sample-0000000000000001", "en")
        v.update(executionAttemptState="SUCCEEDED", attemptOrdinal=1,
                 rawReferenceTextSha256=hashlib.sha256(b"a b").hexdigest(),
                 rawHypothesisTextSha256=hashlib.sha256(b"a").hexdigest(),
                 rawTokenCounts=dict(reference=2, hypothesis=1),
                 normalizedTokenCounts=dict(reference=2, hypothesis=1),
                 oracleCounts={s: dict(substitutions=0, deletions=1, insertions=0)
                               for s in ("raw", "normalized")},
                 normalizedWerContribution=dict(errors=1, referenceTokens=2))
        return v

    def reject(self, func, value):
        with self.assertRaises(c.ContractError):
            func(value)

    def test_literal_normalization(self):
        for i, (source, expected) in enumerate(FIXTURES):
            with self.subTest(index=i):
                self.assertEqual(expected, c.normalize(source))
                self.assertEqual(expected.split(), c.normalized_tokens(source))

    def test_raw_tokens_hashes_and_private_streams(self):
        self.assertEqual(["One", "TWO!"], c.raw_tokens("One\t TWO!"))
        v = c.prepare_case(self.request())
        self.assertEqual(["One", "TWO!"], v["rawReferenceTokens"])
        self.assertEqual(["one", "two"], v["normalizedReferenceTokens"])
        self.assertEqual(hashlib.sha256(b"One TWO!").hexdigest(), v["rawReferenceTextSha256"])
        self.assertEqual([], v["timestampAnchors"])

    def test_empty_hypothesis_accepted(self):
        for text in ("", "!!!", "[noise]"):
            self.assertEqual([], c.prepare_case(self.request(hypothesis=text))["normalizedHypothesisTokens"])

    def test_empty_reference_fails_case(self):
        for text in ("", " ", "!!!", "[noise]", "(noise)"):
            with self.assertRaisesRegex(c.ContractError, "^INVALID_EMPTY_NORMALIZED_REFERENCE$"):
                c.prepare_case(self.request(reference=text))

    def test_wrong_text_types_and_surrogates(self):
        for bad in (None, True, 4, [], {}, "\ud800"):
            for key in ("referenceText", "hypothesisText"):
                v = self.request()
                v[key] = bad
                self.reject(c.prepare_case, v)

    def test_input_closed_shape_and_identity(self):
        for key, bad in (("locale", "mixed"), ("locale", []), ("caseId", "private/path"),
                         ("caseId", None), ("extra", "PRIVATE_SENTINEL")):
            v = self.request()
            v[key] = bad
            self.reject(c.prepare_case, v)
        for bad in (None, [], {}, 1):
            self.reject(c.prepare_case, bad)

    def test_profile_pins_and_types_fail_closed(self):
        for key in c.profile():
            v = c.profile()
            v[key] = None
            self.reject(c.validate_profile, v)
        for key, bad in (("contractVersion", "v99"), ("modelSha256", "0" * 64),
                         ("modelSha256", "g" * 64), ("oracleSchemaVersion", True),
                         ("extra", "private")):
            v = c.profile()
            v[key] = bad
            self.reject(c.validate_profile, v)

    def test_exact_language_gate_boundaries(self):
        for locale, errors, total, expected in (
                ("ru", 20, 100, "PASS"), ("ru", 21, 100, "FAIL"),
                ("en", 18, 100, "PASS"), ("en", 19, 100, "FAIL"),
                ("ru", 1, 5, "PASS"), ("en", 9, 50, "PASS"), ("ru", 200, 100, "FAIL")):
            self.assertEqual(expected, c.language_gate(locale, errors, total))
        for locale in ("mixed", "MIXED_RU_EN", "noisy", "speakerphone", None):
            with self.assertRaises(c.ContractError):
                c.language_gate(locale, 0, 100)
        for errors, total in ((0, 0), (-1, 100), (True, 100), (1.0, 100)):
            with self.assertRaises(c.ContractError):
                c.language_gate("ru", errors, total)
        # Contributions 0/1 and 20/99 sum to 20/100, never mean of percentages.
        self.assertEqual("PASS", c.language_gate("ru", 20, 100))

    def test_shell_claim_ceiling(self):
        v = c.result_shell("sample-0000000000000001", "ru")
        c.validate_result(v)
        self.assertEqual("NOT_RUN", v["executionAttemptState"])
        self.assertEqual("NOT_EVALUABLE", v["timestampQuality"])
        self.assertEqual("NOT_EVALUATED", v["timestampMedianGate"])
        self.assertEqual("NOT_EVALUATED", v["timestampP95Gate"])
        for area in ("rtf", "memory"):
            self.assertEqual("PROPOSED_NOT_APPROVED", v[area]["thresholdState"])
        for key in ("rawTokenCounts", "normalizedTokenCounts", "oracleCounts", "normalizedWerContribution"):
            self.assertIsNone(v[key])

    def test_future_success_and_empty_hypothesis_result(self):
        v = self.result()
        c.validate_result(v)
        v["rawHypothesisTextSha256"] = hashlib.sha256(b"").hexdigest()
        for stream in ("raw", "normalized"):
            v[stream + "TokenCounts"]["hypothesis"] = 0
            v["oracleCounts"][stream]["deletions"] = 2
        v["normalizedWerContribution"]["errors"] = 2
        c.validate_result(v)

    def test_result_inconsistency_rejected(self):
        for key, bad in (
                ("timestampQuality", "PASS"), ("timestampMedianGate", "PASS"),
                ("timestampP95Gate", "PASS"), ("errorCategory", "RUNNER_FAILURE"),
                ("rawReferenceTextSha256", "BAD"), ("rawHypothesisTextSha256", None),
                ("attemptOrdinal", True), ("attemptOrdinal", 0), ("oracleCounts", None),
                ("normalizedTokenCounts", dict(reference=0, hypothesis=0)),
                ("normalizedWerContribution", dict(errors=0, referenceTokens=2)),
                ("rawTokenCounts", dict(reference=2, hypothesis=8)), ("extra", "private")):
            v = self.result()
            v[key] = bad
            self.reject(c.validate_result, v)

    def test_nested_unknown_fields_rejected(self):
        for path in (("rtf",), ("memory",), ("thermal",), ("timing",),
                     ("oracleCounts", "raw"), ("normalizedTokenCounts",)):
            v = self.result()
            node = v
            for name in path:
                node = node[name]
            node["privateSentinel"] = "private"
            self.reject(c.validate_result, v)

    def test_measurement_state_and_no_pass(self):
        for area in ("rtf", "memory", "thermal", "timing"):
            v = self.result()
            v[area]["state"] = "PASS"
            self.reject(c.validate_result, v)
        v = self.result()
        v["memory"]["peakPssBytes"] = 1
        self.reject(c.validate_result, v)

    def test_failure_retained_without_score(self):
        for state, error in (("FAILED", "RUNNER_FAILURE"),
                             ("INVALID_INPUT", "INVALID_EMPTY_NORMALIZED_REFERENCE")):
            v = c.result_shell("sample-0000000000000001", "en")
            v.update(executionAttemptState=state, errorCategory=error, attemptOrdinal=1)
            c.validate_result(v)
            v["normalizedWerContribution"] = dict(errors=0, referenceTokens=1)
            self.reject(c.validate_result, v)

    def test_determinism_and_no_shared_mutation(self):
        req = self.request()
        expected = c.prepare_case(req)
        for _ in range(100):
            self.assertEqual(expected, c.prepare_case(req))
        v = c.profile()
        v["languageGates"]["ru"] = 99
        self.assertEqual("PASS", c.language_gate("ru", 20, 100))

    def test_content_free_cli(self):
        script = Path(__file__).with_name("alpha_asr_eval_text_contract.py")
        run = subprocess.run([sys.executable, "-B", str(script)], capture_output=True, timeout=10)
        self.assertEqual(0, run.returncode)
        c.validate_profile(json.loads(run.stdout))
        run = subprocess.run([sys.executable, "-B", str(script), "PRIVATE_SENTINEL"],
                             capture_output=True, timeout=10)
        self.assertNotEqual(0, run.returncode)
        self.assertNotIn(b"PRIVATE_SENTINEL", run.stdout + run.stderr)


class ExtraTests(unittest.TestCase):
    def test_parity_verifier_missing_or_bad_source_fails_closed(self):
        import importlib.util
        self.assertIsNotNone(importlib.util.find_spec("verify_alpha_asr_normalizer_parity"),
                             "pinned upstream parity verifier is missing")
        from verify_alpha_asr_normalizer_parity import verify
        with self.assertRaises(c.ContractError):
            verify(b"raise RuntimeError('PRIVATE_SENTINEL')")

    def test_resource_limits_are_rejections_not_truncation(self):
        helper = ContractTests()
        for text in ("a" * 4097, " ".join(["a"] * 4097)):
            v = helper.request(reference=text)
            with self.assertRaisesRegex(c.ContractError, "ORACLE_INPUT_LIMIT_EXCEEDED"):
                c.prepare_case(v)
        with self.assertRaisesRegex(c.ContractError, "TEXT_LIMIT_EXCEEDED"):
            c.normalize("a" * 131073)

    def test_observed_fields_are_typed_consistent_observations(self):
        v = ContractTests().result()
        v["timing"].update(state="OBSERVED", audioDurationMicros=100, inferenceElapsedMicros=20)
        v["rtf"].update(state="OBSERVED", elapsedMicros=20, audioMicros=100)
        v["memory"].update(state="OBSERVED", peakPssBytes=1000)
        v["thermal"].update(state="OBSERVED", initialStatus="LIGHT", maximumStatus="SEVERE")
        c.validate_result(v)
        for area, key, bad in (("rtf", "audioMicros", 0), ("rtf", "elapsedMicros", 21),
                               ("memory", "peakPssBytes", True), ("memory", "peakPssBytes", -1),
                               ("memory", "peakPssBytes", float("nan")),
                               ("thermal", "maximumStatus", "NONE"),
                               ("thermal", "initialStatus", None)):
            changed = copy.deepcopy(v)
            changed[area][key] = bad
            with self.assertRaises(c.ContractError):
                c.validate_result(changed)

    def test_not_run_cannot_contain_measurements(self):
        v = c.result_shell("sample-0000000000000001", "en")
        v["timing"].update(state="OBSERVED", inferenceElapsedMicros=20)
        with self.assertRaises(c.ContractError):
            c.validate_result(v)

    def test_environment_profile_rejects_unicode_drift(self):
        from unittest.mock import patch
        with patch.object(c.unicodedata, "unidata_version", "99.0.0"):
            with self.assertRaisesRegex(c.ContractError, "UNSUPPORTED_UNICODE_PROFILE"):
                c.normalize("synthetic")


class OracleIntegrationTests(unittest.TestCase):
    def test_four_token_streams_reach_unchanged_java_oracle(self):
        import shutil
        import tempfile
        self.assertIsNotNone(shutil.which("javac"), "JDK 17 is required for this host-only test")
        root = Path(__file__).resolve().parents[1]
        main = root / "tools/asr_i1_synthetic_scoring_oracle/src/main/java/com/monumentogram/dora/stage0/asr/i1/AsrSyntheticScoringOracle.java"
        tests = root / "tools/asr_i1_synthetic_scoring_oracle/src/test/java/com/monumentogram/dora/stage0/asr/i1/AsrSyntheticScoringOracleTest.java"
        # Git stores LF; the Windows checkout may use CRLF. Pin semantic source bytes.
        self.assertEqual(c.ORACLE_SHA256, hashlib.sha256(main.read_bytes().replace(b"\r\n", b"\n")).hexdigest())
        fixtures = (
            ("One TWO!", "one two", "en", (2, 2, 0, 0), (2, 0, 0, 0)),
            ("ПРИВЕТ МИР", "", "ru", (2, 0, 2, 0), (2, 0, 2, 0)),
            ("one two", "one EXTRA two", "en", (2, 0, 0, 1), (2, 0, 0, 1)),
            ("one two", "one three", "en", (2, 1, 0, 0), (2, 1, 0, 0)),
        )
        calls = []
        for ordinal, (ref, hyp, locale, _, _) in enumerate(fixtures):
            request = ContractTests().request(ref, hyp)
            request["locale"] = locale
            prepared = c.prepare_case(request)
            arrays = []
            for key in ("rawReferenceTokens", "rawHypothesisTokens",
                        "normalizedReferenceTokens", "normalizedHypothesisTokens"):
                arrays.append("List.of(" + ",".join(json.dumps(t, ensure_ascii=True)
                                                   for t in prepared[key]) + ")")
            # QUIET is a required technical enum slot in a synthetic test, no corpus label.
            calls.append('System.out.println(((EvaluationAccepted)AsrSyntheticScoringOracle.evaluate('
                         'new ScoringRequest(List.of(new CaseInput("synthetic-' + str(ordinal)
                         + '",LanguageSlice.' + locale.upper() + ',AcousticSlice.QUIET,'
                         + ",".join(arrays) + ',List.of()))))).score().canonicalJson());')
        source = (
            "import java.util.List;\n"
            "import com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle;\n"
            "import com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle.*;\n"
            "public final class ContractBridge { public static void main(String[] args) {\n"
            + "\n".join(calls) + "\n}}\n")
        with tempfile.TemporaryDirectory(prefix="dora-asr-contract-synthetic-") as temp:
            bridge = Path(temp) / "ContractBridge.java"
            bridge.write_text(source, encoding="ascii")
            run = subprocess.run(["javac", "--release", "17", "-encoding", "UTF-8",
                                  "-Xlint:all", "-Werror", "-d", temp,
                                  str(main), str(tests), str(bridge)], capture_output=True, timeout=60)
            self.assertEqual(0, run.returncode, "synthetic host Java compilation failed")
            run = subprocess.run(["java", "-ea", "-Xverify:all", "-cp", temp,
                                  "com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracleTest"],
                                 capture_output=True, timeout=60)
            self.assertEqual(0, run.returncode, "existing Java oracle suite failed")
            self.assertEqual(b"POC_ASR_I1_SYNTHETIC_SCORING_ORACLE_TESTS_OK", run.stdout.strip())
            run = subprocess.run(["java", "-ea", "-Xverify:all", "-cp", temp, "ContractBridge"],
                                 capture_output=True, timeout=60)
            self.assertEqual(0, run.returncode, "synthetic token contract bridge failed")
            rows = [json.loads(row) for row in run.stdout.splitlines()]
            self.assertEqual(4, len(rows))
            for fixture, row in zip(fixtures, rows):
                for stream, expected in (("raw", fixture[3]), ("normalized", fixture[4])):
                    score = row["overall"][stream]
                    self.assertEqual(expected, tuple(score[k] for k in
                        ("referenceTokens", "substitutions", "deletions", "insertions")))
                self.assertIsNone(row["overall"]["timestamps"]["medianAbsoluteErrorMicros"])
                self.assertIsNone(row["overall"]["timestamps"]["p95NearestRankAbsoluteErrorMicros"])


if __name__ == "__main__":
    unittest.main()
