# DORA 6.2D offline harness v0.1

Frozen executable listings for synthetic host evaluation only. No network/AWS SDK/CLI, credential discovery, uploads or product runtime. This proves terminal representation/adapter interchangeability and scoring semantics only. Live orchestration/client binding remains BLOCKED.

Extract the two Python blocks as named UTF-8/LF files outside the repository into its parent working directory; CPython3.12/Unicode15.0.0. Run python cloud62d_harness_test.py there with the unchanged DORA checkout beside it. The second block imports the first; comparison imports the pinned existing normalizer. Before terminal normalization the future operator must persist timestamp_audit for every raw item: missing/malformed/non-monotonic counts are per item; partial absence counts as both missing and malformed. No real corpus text is included. Synthetic results are not AWS observations.

## cloud62d_harness.py

SHA256: 6e99e08561534cde5b6bc69a40fd3e8986d763c00456e7fdb32bf3346cceabd1; bytes: 13912.

```python
"""DORA 6.2D v0.1 offline evaluation core. No network, credentials or AWS runner."""
import hashlib
import io
import json
import math
import sys
import unicodedata
import wave
from dataclasses import dataclass, asdict
from decimal import Decimal, InvalidOperation
from fractions import Fraction

ERRORS = ("unauthorized", "forbidden", "missing_input", "unsupported_format",
          "invalid_request", "size_duration_limit", "rate_limited",
          "provider_unavailable", "provider_internal", "timeout", "cancelled",
          "result_unavailable", "malformed_result", "unknown")
MODEL = "PROVIDER_MANAGED_MODEL_VERSION_NOT_DISCLOSED"


def require(ok, message):
    if not ok:
        raise ValueError(message)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def normalize(text):
    # Same language-neutral semantics as the pinned existing DORA normalizer.
    import re
    require(sys.version_info[:2] == (3, 12) and unicodedata.unidata_version == "15.0.0",
            "NORMALIZER_ENVIRONMENT")
    text = re.sub(r"[<\[][^>\]]*[>\]]", "", text.lower())
    text = re.sub(r"\(([^)]+?)\)", "", text)
    text = "".join(" " if unicodedata.category(c)[0] in "MSP" else c
                   for c in unicodedata.normalize("NFKC", text))
    return re.sub(r"\s+", " ", text.lower())


def align(reference, hypothesis):
    """Unit-cost Levenshtein; tie priority diagonal, deletion, insertion."""
    require(max(len(reference), len(hypothesis)) <= 4096, "TOKEN_BOUND")
    require((len(reference) + 1) * (len(hypothesis) + 1) <= 12500000, "CELL_BOUND")
    # Backtrace retained for reproducible timestamp matching.
    rows = [[j for j in range(len(hypothesis) + 1)]]
    for i, ref in enumerate(reference, 1):
        row = [i]
        for j, hyp in enumerate(hypothesis, 1):
            row.append(min(rows[-1][j-1] + (ref != hyp), rows[-1][j]+1, row[-1]+1))
        rows.append(row)
    i, j = len(reference), len(hypothesis)
    s = d = ins = 0
    matches = []
    while i or j:
        if i and j and rows[i][j] == rows[i-1][j-1] + (reference[i-1] != hypothesis[j-1]):
            if reference[i-1] == hypothesis[j-1]:
                matches.append((i-1, j-1))
            else:
                s += 1
            i, j = i-1, j-1
        elif i and rows[i][j] == rows[i-1][j]+1:
            d += 1
            i -= 1
        else:
            ins += 1
            j -= 1
    return {"substitutions": s, "deletions": d, "insertions": ins,
            "reference_tokens": len(reference), "matches": list(reversed(matches))}


def score(reference, hypothesis):
    require(isinstance(reference, str) and isinstance(hypothesis, str), "TEXT_TYPE")
    nr, nh = normalize(reference), normalize(hypothesis)
    require(bool(nr.split()), "EMPTY_REFERENCE")
    return {"raw": align(reference.split(), hypothesis.split()),
            "normalized": align(nr.split(), nh.split()),
            "raw_reference_sha256": digest(reference.encode()),
            "raw_hypothesis_sha256": digest(hypothesis.encode()),
            "normalized_reference_sha256": digest(nr.encode()),
            "normalized_hypothesis_sha256": digest(nh.encode())}


def wer_gate(counts, percent):
    n = sum(c["reference_tokens"] for c in counts)
    require(n > 0, "NO_COVERAGE")
    errors = sum(c["substitutions"] + c["deletions"] + c["insertions"] for c in counts)
    return {"errors": errors, "reference_tokens": n,
            "pass": errors * 100 <= percent * n}


def quantile(values, percentile):
    """Nearest rank including median: x[ceil(p*n)-1]; no interpolation."""
    require(bool(values) and 0 < percentile <= 100, "NO_COVERAGE")
    return sorted(values)[(percentile * len(values) + 99) // 100 - 1]


def latency(submit_us, complete_observed_us, duration_us):
    require(type(duration_us) is int and duration_us > 0, "INVALID_DURATION")
    require(type(submit_us) is int and type(complete_observed_us) is int
            and complete_observed_us >= submit_us, "INVALID_CLOCK")
    return Fraction(complete_observed_us - submit_us, duration_us)


def error_map(code, http=None, proven_context=None):
    # Ambiguous LimitExceeded/BadRequest never become a guessed throttle or size failure.
    fixed = {"UnrecognizedClientException": "unauthorized", "ExpiredTokenException": "unauthorized",
             "InvalidSignatureException": "unauthorized", "AccessDeniedException": "forbidden",
             "NoSuchKey": "missing_input", "ThrottlingException": "rate_limited",
             "ServiceUnavailableException": "provider_unavailable",
             "InternalFailureException": "provider_internal",
             "ConflictException": "invalid_request", "BadRequestException": "invalid_request",
             "LimitExceededException": "unknown", "ClientTimeout": "timeout"}
    category = fixed.get(code, "unknown")
    if proven_context in ERRORS and code in ("BadRequestException", "LimitExceededException"):
        category = proven_context
    if http == 429:
        category = "rate_limited"
    return {"category": category, "retryable": category in
            ("rate_limited", "provider_unavailable", "provider_internal", "timeout")}


@dataclass(frozen=True)
class Request:
    request_id: str
    language: str
    input_ref: str
    config_sha256: str
    duration_us: int


@dataclass(frozen=True)
class Result:
    request_id: str
    language: str
    input_ref: str
    state: str
    text: str | None
    words: tuple
    error: str | None
    retryable: bool
    provider: str
    model: str
    config_sha256: str
    deletion_ref: str


def validate(request, result):
    require((result.request_id, result.language, result.input_ref, result.config_sha256) ==
            (request.request_id, request.language, request.input_ref, request.config_sha256),
            "IDENTITY_MISMATCH")
    require(result.state in ("SUCCEEDED", "FAILED", "TIMEOUT", "CANCELLED"), "NON_TERMINAL")
    require(bool(result.provider and result.model and result.deletion_ref), "MISSING_PROVENANCE")
    require(result.error in ERRORS or result.error is None, "ERROR_CATEGORY")
    if result.state == "SUCCEEDED":
        require(isinstance(result.text, str) and result.error is None and not result.retryable,
                "UNREADABLE_SUCCESS")
    else:
        require(result.text is None and not result.words and result.error is not None,
                "FAILURE_WITH_SUCCESS_PAYLOAD")
    prior_start = prior_end = -1
    for word in result.words:
        require(set(word) == {"text", "start_us", "end_us"}, "WORD_SHAPE")
        start, end = word["start_us"], word["end_us"]
        require(isinstance(word["text"], str), "WORD_TEXT")
        if start is None or end is None:
            require(start is None and end is None, "PARTIAL_TIMESTAMP")
            continue
        require(type(start) is int and type(end) is int
                and 0 <= start <= end <= request.duration_us, "MALFORMED_TIMESTAMP")
        require(start >= prior_start and end >= prior_end, "NON_MONOTONIC_TIMESTAMP")
        prior_start, prior_end = start, end
    return result


class CloudAsrProvider:
    """Offline terminal-record port only; transport/live orchestration is not supplied."""
    def submit(self, request):
        require(request.language in ("ru-RU", "en-US") and request.duration_us > 0, "REQUEST")
        return request.request_id

    def delete(self, job_ref):
        require(bool(job_ref), "DELETE_REF")
        return {"deletion_ref": job_ref, "status": "MOCK_ONLY_NOT_AWS_DELETION"}


def timestamp_audit(items, duration_us):
    """Inspect ALL raw pronunciation items before terminal normalization/rejection.

    Missing and malformed counts may overlap for a partly absent pair. Each count
    counts items, not endpoints. Invalid pairs do not reset the last valid pair.
    The future operator must persist this audit with the immutable raw evidence.
    """
    counts = dict(pronunciation_items=0, missing=0, malformed=0, non_monotonic=0)
    last_start = last_end = -1
    for item in items:
        if item.get("type") != "pronunciation":
            continue
        counts["pronunciation_items"] += 1
        start, end = item.get("start_time"), item.get("end_time")
        if start is None or end is None:
            counts["missing"] += 1
            if (start is None) != (end is None):
                counts["malformed"] += 1
            continue
        try:
            a, b = Decimal(start) * 1000000, Decimal(end) * 1000000
            valid = (a.is_finite() and b.is_finite()
                     and a == a.to_integral_value() and b == b.to_integral_value()
                     and 0 <= a <= b <= duration_us)
        except (InvalidOperation, ValueError, TypeError):
            valid = False
        if not valid:
            counts["malformed"] += 1
            continue
        if a < last_start or b < last_end:
            counts["non_monotonic"] += 1
        last_start, last_end = a, b
    return counts


class FakeProvider(CloudAsrProvider):
    def terminal(self, request, payload):
        result = Result(**payload)
        return validate(request, result)


class AwsRecordAdapter(CloudAsrProvider):
    def terminal(self, request, payload):
        require(payload["region"] == "eu-central-1", "WRONG_REGION")
        require(payload["jobName"] == request.request_id, "IDENTITY_MISMATCH")
        require(payload["language"] == request.language and payload["input_ref"] == request.input_ref,
                "IDENTITY_MISMATCH")
        require(payload["config_sha256"] == request.config_sha256, "IDENTITY_MISMATCH")
        if payload["status"] == "FAILED":
            e = error_map(payload.get("code"), payload.get("http"), payload.get("proven_context"))
            return validate(request, Result(request.request_id, request.language, request.input_ref,
                            "FAILED", None, (), e["category"], e["retryable"], "AWS/Transcribe",
                            MODEL, request.config_sha256, payload["jobName"]))
        require(payload["status"] == "COMPLETED", "NON_TERMINAL")
        wire = payload["output"]
        require(wire["jobName"] == payload["jobName"] and wire["status"] == "COMPLETED",
                "IDENTITY_MISMATCH")
        audit = timestamp_audit(wire["results"]["items"], request.duration_us)
        require(not audit["malformed"] and not audit["non_monotonic"], "INVALID_TIMESTAMP_AUDIT")
        transcripts = wire["results"]["transcripts"]
        require(len(transcripts) == 1 and isinstance(transcripts[0]["transcript"], str),
                "UNREADABLE_SUCCESS")
        words = []
        for item in wire["results"]["items"]:
            require(item["type"] in ("pronunciation", "punctuation"), "ITEM_TYPE")
            if item["type"] == "punctuation":
                continue
            token = item["alternatives"][0]["content"]
            start = item.get("start_time")
            end = item.get("end_time")
            if start is not None and end is not None:
                a, b = Decimal(start) * 1000000, Decimal(end) * 1000000
                require(a.is_finite() and b.is_finite()
                        and a == a.to_integral_value() and b == b.to_integral_value(),
                        "MALFORMED_TIMESTAMP")
                start, end = int(a), int(b)
            words.append({"text": token, "start_us": start, "end_us": end})
        return validate(request, Result(request.request_id, request.language, request.input_ref,
                        "SUCCEEDED", transcripts[0]["transcript"], tuple(words), None, False,
                        "AWS/Transcribe", MODEL, request.config_sha256, payload["jobName"]))


def timestamp_metrics(reference_words, words):
    """Reference entries: independently annotated text/start_us/end_us; exact lexical matches only."""
    ref_tokens = [normalize(w["text"]).split() for w in reference_words]
    hyp_tokens = [normalize(w["text"]).split() for w in words]
    require(all(len(v) == 1 for v in ref_tokens + hyp_tokens), "TIMING_TOKEN_CARDINALITY")
    alignment = align([v[0] for v in ref_tokens], [v[0] for v in hyp_tokens])
    starts, ends = [], []
    missing = sum(w["start_us"] is None or w["end_us"] is None for w in words)
    for ri, hi in alignment["matches"]:
        if words[hi]["start_us"] is not None and words[hi]["end_us"] is not None:
            starts.append(abs(words[hi]["start_us"] - reference_words[ri]["start_us"]))
            ends.append(abs(words[hi]["end_us"] - reference_words[ri]["end_us"]))
    return {"matched": len(starts), "reference_words": len(reference_words), "missing": missing,
            "unmatched_reference": len(reference_words) - len(alignment["matches"]),
            "start_median_us": quantile(starts, 50) if starts else None,
            "start_p95_us": quantile(starts, 95) if starts else None,
            "end_median_us": quantile(ends, 50) if ends else None,
            "end_p95_us": quantile(ends, 95) if ends else None}


def wav_bytes(frames):
    with io.BytesIO() as out:
        with wave.open(out, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(16000)
            w.writeframes(b"\x00\x00" * frames)
        return out.getvalue()


def fixtures():
    values = [(name, wav_bytes(frames), frames) for name, frames in
              (("empty", 0), ("near_empty", 4000), ("short", 48000),
               ("ordinary_duration", 4800000), ("near_600", 9599984))]
    short = wav_bytes(48000)
    values += [("malformed", b"DORA_SYNTHETIC_NOT_WAV", None),
               ("truncated", short[:-101], None)]
    return [{"id": n, "sha256": digest(b), "bytes": len(b), "frames": f,
             "duration_us": f * 1000000 // 16000 if f is not None else None}
            for n, b, f in values]


if __name__ == "__main__":
    print(json.dumps({"mode": "OFFLINE_ONLY", "fixtures": fixtures()}, sort_keys=True))
```

## cloud62d_harness_test.py

SHA256: 74fc7b4f412087a658d880ca92ddbb8315cc7788ab6d18e102f67c98decc962a; bytes: 8120.

```python
import copy
import dataclasses
import importlib.util
import json
import pathlib
import sys
import unittest
from cloud62d_harness import *

REQ = Request("synthetic-request", "en-US", "synthetic:short", "0"*64, 3000000)


def aws_payload():
    return {"region": "eu-central-1", "jobName": REQ.request_id, "language": REQ.language,
            "input_ref": REQ.input_ref, "config_sha256": REQ.config_sha256,
            "status": "COMPLETED", "output": {"jobName": REQ.request_id, "status": "COMPLETED",
            "results": {"transcripts": [{"transcript": "test"}], "items": [{"type": "pronunciation",
            "start_time": "0.1", "end_time": "0.7", "alternatives": [{"content": "test"}]}]}}}


class MetricsTests(unittest.TestCase):
    def test_independent_edit_examples(self):
        for r, h, expected in [("a b", "a b", (0,0,0)), ("a b", "a x", (1,0,0)),
                               ("a b", "a", (0,1,0)), ("a", "a b", (0,0,1)),
                               ("a b", "b a", (2,0,0))]:
            v=align(r.split(),h.split())
            self.assertEqual(tuple(v[k] for k in ("substitutions","deletions","insertions")),expected)

    def test_threshold_exact_boundaries_and_weighted_counts(self):
        def c(e,n): return {"substitutions":e,"deletions":0,"insertions":0,"reference_tokens":n}
        self.assertTrue(wer_gate([c(20,100)],20)["pass"])
        self.assertFalse(wer_gate([c(21,100)],20)["pass"])
        self.assertTrue(wer_gate([c(18,100)],18)["pass"])
        self.assertFalse(wer_gate([c(19,100)],18)["pass"])
        self.assertFalse(wer_gate([c(0,1),c(30,100)],20)["pass"])
        with self.assertRaises(ValueError): wer_gate([],20)

    def test_quantiles_no_interpolation(self):
        self.assertEqual(quantile(list(range(1,21)),95),19)
        self.assertEqual(quantile([1,100],50),1)
        with self.assertRaises(ValueError):quantile([],95)

    def test_latency_parentheses_and_clock(self):
        self.assertEqual(latency(100,2100,1000),Fraction(2))
        with self.assertRaises(ValueError):latency(100,99,1000)
        with self.assertRaises(ValueError):latency(100,200,0)

    def test_normalizer_equivalence_to_existing_contract(self):
        repo=pathlib.Path(__file__).parent/"DORA"
        spec=importlib.util.spec_from_file_location("old",repo/"tools/alpha_asr_eval_text_contract.py")
        old=importlib.util.module_from_spec(spec);spec.loader.exec_module(old)
        for t in ["Привет, Мир!", "Café (quiet) [noise] １２３", "Ё ё é + 😀\nhello",
                  "a-b/c'd", "  empty   ", "<tag>Word</tag>", "a\u0301", ""]:
            self.assertEqual(normalize(t),old.normalize(t))

    def test_reference_never_empty_and_hashes(self):
        with self.assertRaises(ValueError):score("(noise)","test")
        v=score("TEST!","test")
        self.assertEqual(v["normalized"]["substitutions"],0)
        self.assertNotEqual(v["raw_reference_sha256"],v["normalized_reference_sha256"])

    def test_timestamp_alignment_and_missing(self):
        ref=[{"text":"a","start_us":0,"end_us":500000},{"text":"b","start_us":1000000,"end_us":1400000}]
        hyp=[{"text":"a","start_us":500000,"end_us":2000000},{"text":"b","start_us":None,"end_us":None}]
        v=timestamp_metrics(ref,hyp)
        self.assertEqual((v["start_p95_us"],v["end_p95_us"],v["missing"]),(500000,1500000,1))
        self.assertEqual(v["matched"],1)

    def test_all_raw_timestamp_defects_counted_before_rejection(self):
        def item(a,b):
            return {"type":"pronunciation","start_time":a,"end_time":b}
        items=[item("0.1","0.5"),item("NaN","0.7"),item("0.8","0.6"),
               item("0.05","0.4"),item(None,None),item("0.2",None),
               {"type":"punctuation"}]
        self.assertEqual(timestamp_audit(items,3000000),
                         {"pronunciation_items":6,"missing":2,"malformed":3,"non_monotonic":1})

    def test_ambiguous_error_never_guessed(self):
        self.assertEqual(error_map("LimitExceededException")["category"],"unknown")
        self.assertEqual(error_map("LimitExceededException",proven_context="size_duration_limit")["category"],"size_duration_limit")
        self.assertFalse(error_map("AccessDeniedException")["retryable"])
        self.assertTrue(error_map("ThrottlingException")["retryable"])

    def test_fixture_determinism(self):
        self.assertEqual(fixtures(),fixtures())
        self.assertEqual(next(x for x in fixtures() if x["id"]=="near_600")["duration_us"],599999000)


class SharedContract:
    # Each test runs against both adapters. No test invokes AWS.
    def result(self):
        return self.adapter.terminal(REQ,self.payload())
    def test_identity_submit_and_delete(self):
        self.assertEqual(self.adapter.submit(REQ),REQ.request_id)
        r=self.result()
        self.assertEqual((r.language,r.input_ref,r.config_sha256),(REQ.language,REQ.input_ref,REQ.config_sha256))
        self.assertEqual(self.adapter.delete(r.deletion_ref)["status"],"MOCK_ONLY_NOT_AWS_DELETION")
    def test_provider_independent_result_shape(self):
        r=self.result()
        self.assertIsInstance(r,Result)
        self.assertEqual((r.state,r.text,r.words[0]["start_us"]),("SUCCEEDED","test",100000))
        self.assertFalse(hasattr(r,"ResponseMetadata"))
    def test_request_mismatch_rejected(self):
        with self.assertRaises(ValueError):self.adapter.terminal(dataclasses.replace(REQ,request_id="wrong"),self.payload())
    def test_failed_error_retained(self):
        r=Result(REQ.request_id,REQ.language,REQ.input_ref,"FAILED",None,(),"forbidden",False,
                 "synthetic",MODEL,REQ.config_sha256,REQ.request_id)
        self.assertEqual(validate(REQ,r).error,"forbidden")
        with self.assertRaises(ValueError):validate(REQ,dataclasses.replace(r,text="bad success"))
    def test_unreadable_success_rejected(self):
        with self.assertRaises(ValueError):validate(REQ,dataclasses.replace(self.result(),text=None))
    def test_malformed_timing_rejected(self):
        for a,b in [(-1,3),(2,1),(0,3000001)]:
            with self.assertRaises(ValueError):
                validate(REQ,dataclasses.replace(self.result(),words=({"text":"test","start_us":a,"end_us":b},)))


class AwsTests(SharedContract,unittest.TestCase):
    adapter=AwsRecordAdapter()
    def payload(self):return aws_payload()
    def test_wrong_region(self):
        p=self.payload();p["region"]="us-east-1"
        with self.assertRaises(ValueError):self.adapter.terminal(REQ,p)
    def test_failed_wire(self):
        p=self.payload();p.update(status="FAILED",code="AccessDeniedException")
        self.assertEqual(self.adapter.terminal(REQ,p).error,"forbidden")
    def test_wrong_output_identity(self):
        p=self.payload();p["output"]["jobName"]="other"
        with self.assertRaises(ValueError):self.adapter.terminal(REQ,p)
    def test_nonfinite_timestamp(self):
        p=self.payload();p["output"]["results"]["items"][0]["start_time"]="NaN"
        with self.assertRaises(ValueError):self.adapter.terminal(REQ,p)


class FakeTests(SharedContract,unittest.TestCase):
    adapter=FakeProvider()
    def payload(self):
        # Independently authored generic record; no AWS payload adapter used.
        return dataclasses.asdict(Result(REQ.request_id,REQ.language,REQ.input_ref,"SUCCEEDED","test",
               ({"text":"test","start_us":100000,"end_us":700000},),None,False,
               "NonAWSFake","fake-v1",REQ.config_sha256,REQ.request_id))


if __name__=="__main__":
    suite=unittest.defaultTestLoader.loadTestsFromModule(sys.modules[__name__])
    result=unittest.TextTestRunner(verbosity=1).run(suite)
    report={"schema_version":"0.1","mode":"OFFLINE_SYNTHETIC_ONLY","tests_run":result.testsRun,
            "failures":len(result.failures),"errors":len(result.errors),"passed":result.wasSuccessful(),
            "aws_calls":0,"provider_benchmark_attempts":0,"aws_cleanup":"NOT_APPLICABLE_NO_OBJECTS_CREATED"}
    pathlib.Path("cloud62d-host-report.json").write_text(json.dumps(report,indent=2)+"\n",encoding="utf-8")
    sys.exit(not result.wasSuccessful())
```
