#!/usr/bin/env python3
"""Stage 0 5.3A host text/result contract. Standard library only; no inference or WER.

Normalization follows OpenAI Whisper BasicTextNormalizer(False, False), pinned in
profile(). See the contract document for upstream MIT attribution. All returned
token arrays, per-case hashes and case results are CONTROLLED PRIVATE data.
The no-argument CLI prints only the content-free profile; it never reads stdin.
"""
from __future__ import annotations

import hashlib
import json
import platform
import re
import sys
import unicodedata

CONTRACT_VERSION = "dora-alpha-asr-eval-result-v0.1"
NORMALIZATION_VERSION = "dora-alpha-asr-normalization-v0.1"
PROFILE_ID = "dora-alpha-asr-eval-profile-v0.1"
UPSTREAM_REVISION = "86098128c0b4f24f0e2aa2994de830614b474227"
UPSTREAM_SHA256 = "4742eaa040e0657fa1247a1361e0d856c62317a43326ea59a40c2e9edd8d2c38"
ORACLE_SHA256 = "a2b3e37305f24f91a8ace00f5f824d1d13effda94248c15b31aa31db78c76125"
CORPUS_SHA256 = "5d9e12fa76e9db54bc69bbd22396dbec733348db97bba353361ac701af64061c"
MODEL_SHA256 = "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898"
RUNTIME_COMMIT = "927cfce34f31707e17f2bff35c349632fb9e2c3a"
MAX_INTEGER = 2**63 - 1
MAX_TEXT_CODEPOINTS = 131072  # Host input safety bound, not a quality threshold.
STATES = ("NOT_RUN", "SUCCEEDED", "FAILED", "INVALID_INPUT")
ERRORS = ("RUNNER_FAILURE", "RUNNER_TIMEOUT", "INPUT_BINDING_MISMATCH",
          "INVALID_EMPTY_NORMALIZED_REFERENCE", "ORACLE_REJECTED", "INVALID_TEXT")
THERMAL_STATES = ("NONE", "LIGHT", "MODERATE", "SEVERE", "CRITICAL", "EMERGENCY", "SHUTDOWN")


class ContractError(ValueError):
    """Stable code only; no input content, keys, identifiers or paths."""


def require(condition, code="INVALID_CONTRACT"):
    if not condition:
        raise ContractError(code)


def _shape(value, keys):
    require(type(value) is dict and set(value) == set(keys), "INVALID_FIELDS")


def _integer(value, minimum=0):
    require(type(value) is int and minimum <= value <= MAX_INTEGER, "INVALID_INTEGER")


def _text(value):
    require(type(value) is str, "INVALID_TEXT")
    require(len(value) <= MAX_TEXT_CODEPOINTS, "TEXT_LIMIT_EXCEEDED")
    require(not any(0xD800 <= ord(ch) <= 0xDFFF for ch in value), "INVALID_TEXT")


def _hash(value):
    require(type(value) is str and re.fullmatch(r"[0-9a-f]{64}", value) is not None,
            "INVALID_HASH")


def _identity(case_id, locale):
    require(type(case_id) is str and re.fullmatch(r"sample-[0-9a-f]{16}", case_id) is not None,
            "INVALID_CASE_ID")
    require(type(locale) is str and locale in ("ru", "en"), "UNSUPPORTED_LOCALE")


def check_environment():
    require(platform.python_implementation() == "CPython" and sys.version_info[:2] == (3, 12)
            and unicodedata.unidata_version == "15.0.0", "UNSUPPORTED_UNICODE_PROFILE")


def profile():
    """Fresh closed profile. Equality includes JSON types and every nested field."""
    return {
        "profileId": PROFILE_ID,
        "contractVersion": CONTRACT_VERSION,
        "normalizationVersion": NORMALIZATION_VERSION,
        "pythonSemanticProfile": "cpython-3.12-unicode-15.0.0",
        "upstreamRepository": "openai/whisper",
        "upstreamRevision": UPSTREAM_REVISION,
        "upstreamPath": "whisper/normalizers/basic.py",
        "upstreamSourceSha256": UPSTREAM_SHA256,
        "upstreamOptions": {"removeDiacritics": False, "splitLetters": False},
        "oracleIdentity": "com.monumentogram.dora.stage0.asr.i1.AsrSyntheticScoringOracle",
        "oracleSchemaVersion": 1,
        "oracleSourceSha256": ORACLE_SHA256,
        "selectedManifestSha256": CORPUS_SHA256,
        "modelArtifact": "ggml-base-q5_1.bin",
        "modelSha256": MODEL_SHA256,
        "runtimeSourceVersion": "ggml-org/whisper.cpp v1.9.4",
        "runtimeSourceCommit": RUNTIME_COMMIT,
        "gateSetVersion": "stage0-v0.1",
        "languageGates": {"ru": {"errors": 20, "referenceTokens": 100},
                          "en": {"errors": 18, "referenceTokens": 100}},
        "rawWerRole": "DIAGNOSTIC_ONLY",
        "normalizedWerRole": "LANGUAGE_QUALITY_METRIC",
        "unclaimedGates": ["MIXED_RU_EN", "NOISY", "SPEAKERPHONE"],
        "timestampReferenceState": "NOT_AVAILABLE_IN_5_1_CORPUS",
        "timestampQuality": "NOT_EVALUABLE",
        "timestampMedianGate": "NOT_EVALUATED",
        "timestampP95Gate": "NOT_EVALUATED",
        "rtfThresholdState": "PROPOSED_NOT_APPROVED",
        "memoryThresholdState": "PROPOSED_NOT_APPROVED",
    }


def _exact(value, expected):
    require(type(value) is type(expected), "PROFILE_MISMATCH")
    if type(expected) is dict:
        _shape(value, expected)
        for key in expected:
            _exact(value[key], expected[key])
    elif type(expected) is list:
        require(len(value) == len(expected), "PROFILE_MISMATCH")
        for item, target in zip(value, expected):
            _exact(item, target)
    else:
        require(value == expected, "PROFILE_MISMATCH")


def validate_profile(value):
    check_environment()
    _exact(value, profile())


def normalize(text):
    check_environment()
    _text(text)
    value = text.lower()
    # Exact upstream patterns: mixed bracket closers and first ')' are intentional.
    value = re.sub(r"[<\[][^>\]]*[>\]]", "", value)
    value = re.sub(r"\(([^)]+?)\)", "", value)
    value = "".join(" " if unicodedata.category(ch)[0] in "MSP" else ch
                    for ch in unicodedata.normalize("NFKC", value))
    return re.sub(r"\s+", " ", value.lower())  # No strip: upstream preserves edge spaces.


def raw_tokens(text):
    check_environment()
    _text(text)
    return text.split()


def normalized_tokens(text):
    return normalize(text).split()


def prepare_case(value):
    """Validate private text input; return four private streams for the existing oracle.

    The future caller must establish exact manifest membership and reference authority
    before calling. This function never reads a manifest, audio or model.
    """
    _shape(value, ("profile", "caseId", "locale", "referenceText", "hypothesisText"))
    validate_profile(value["profile"])
    _identity(value["caseId"], value["locale"])
    reference, hypothesis = value["referenceText"], value["hypothesisText"]
    raw_ref, raw_hyp = raw_tokens(reference), raw_tokens(hypothesis)
    norm_ref, norm_hyp = normalized_tokens(reference), normalized_tokens(hypothesis)
    require(bool(norm_ref), "INVALID_EMPTY_NORMALIZED_REFERENCE")
    streams = (raw_ref, raw_hyp, norm_ref, norm_hyp)
    for stream in streams:
        require(len(stream) <= 4096, "ORACLE_INPUT_LIMIT_EXCEEDED")
        require(all(len(token.encode("utf-16-le")) // 2 <= 4096 for token in stream),
                "ORACLE_INPUT_LIMIT_EXCEEDED")
    require((len(raw_ref) + 1) * (len(raw_hyp) + 1)
            + (len(norm_ref) + 1) * (len(norm_hyp) + 1) <= 25_000_000,
            "ORACLE_INPUT_LIMIT_EXCEEDED")
    return {
        "caseId": value["caseId"], "locale": value["locale"],
        "rawReferenceTextSha256": hashlib.sha256(reference.encode("utf-8")).hexdigest(),
        "rawHypothesisTextSha256": hashlib.sha256(hypothesis.encode("utf-8")).hexdigest(),
        "rawReferenceTokens": raw_ref, "rawHypothesisTokens": raw_hyp,
        "normalizedReferenceTokens": norm_ref, "normalizedHypothesisTokens": norm_hyp,
        "timestampAnchors": [],
    }


def language_gate(locale, errors, reference_tokens):
    """Compare already aggregated oracle counts, never align text or average case WERs.

    PASS describes this numerical language predicate only. The caller must separately
    prove complete 24-case coverage, no failed/invalid attempts and campaign authority.
    """
    require(type(locale) is str and locale in ("ru", "en"), "UNSUPPORTED_LOCALE")
    _integer(errors)
    _integer(reference_tokens, 1)
    limit = 20 if locale == "ru" else 18
    return "PASS" if errors * 100 <= limit * reference_tokens else "FAIL"


def result_shell(case_id, locale):
    """Private record shape for a future attempt, with explicit absent measurements."""
    check_environment()
    _identity(case_id, locale)
    return {
        "profile": profile(), "caseId": case_id, "locale": locale,
        "attemptOrdinal": 0, "executionAttemptState": "NOT_RUN", "errorCategory": None,
        "rawReferenceTextSha256": None, "rawHypothesisTextSha256": None,
        "rawTokenCounts": None, "normalizedTokenCounts": None,
        "oracleCounts": None, "normalizedWerContribution": None,
        "timing": {"state": "NOT_MEASURED", "audioDurationMicros": None,
                   "inferenceElapsedMicros": None, "modelLoadElapsedMicros": None},
        "timestampReferenceState": "NOT_AVAILABLE_IN_5_1_CORPUS",
        "timestampQuality": "NOT_EVALUABLE",
        "timestampMedianGate": "NOT_EVALUATED", "timestampP95Gate": "NOT_EVALUATED",
        "rtf": {"state": "NOT_MEASURED", "elapsedMicros": None, "audioMicros": None,
                "thresholdState": "PROPOSED_NOT_APPROVED"},
        "memory": {"state": "NOT_MEASURED", "peakPssBytes": None, "peakNativeHeapBytes": None,
                   "thresholdState": "PROPOSED_NOT_APPROVED"},
        "thermal": {"state": "NOT_MEASURED", "initialStatus": None, "maximumStatus": None},
    }


def _observation(value, shape):
    _shape(value, shape)
    require(type(value["state"]) is str
            and value["state"] in ("NOT_MEASURED", "UNAVAILABLE", "OBSERVED"),
            "INVALID_MEASUREMENT_STATE")
    fields = [key for key in shape if key not in ("state", "thresholdState")]
    if "thresholdState" in shape:
        require(value["thresholdState"] == "PROPOSED_NOT_APPROVED", "UNAPPROVED_THRESHOLD")
    if value["state"] != "OBSERVED":
        require(all(value[key] is None for key in fields), "UNMEASURED_VALUE")
    else:
        require(any(value[key] is not None for key in fields), "MISSING_OBSERVATION")
        for key in fields:
            if value[key] is not None:
                if key.endswith("Status"):
                    require(type(value[key]) is str and value[key] in THERMAL_STATES,
                            "INVALID_THERMAL_STATUS")
                else:
                    _integer(value[key])


def validate_result(value):
    """Closed private result validation; supplied S/D/I are NOT rescored here.

    SUCCEEDED means execution plus oracle scoring completed, never quality PASS.
    Failed/invalid rows have no WER contribution and must not be silently excluded.
    """
    require(type(value) is dict, "INVALID_FIELDS")
    _identity(value.get("caseId"), value.get("locale"))
    shell = result_shell(value["caseId"], value["locale"])
    _shape(value, shell)
    validate_profile(value["profile"])
    state = value["executionAttemptState"]
    require(type(state) is str and state in STATES, "INVALID_ATTEMPT_STATE")
    _integer(value["attemptOrdinal"])
    require((state == "NOT_RUN") == (value["attemptOrdinal"] == 0), "INVALID_ATTEMPT_ORDINAL")
    if state in ("NOT_RUN", "SUCCEEDED"):
        require(value["errorCategory"] is None, "UNEXPECTED_ERROR")
    else:
        require(type(value["errorCategory"]) is str and value["errorCategory"] in ERRORS,
                "INVALID_ERROR_CATEGORY")
        require((state == "INVALID_INPUT") == (value["errorCategory"] in
                ("INVALID_EMPTY_NORMALIZED_REFERENCE", "INPUT_BINDING_MISMATCH", "INVALID_TEXT")),
                "ERROR_STATE_MISMATCH")
    for key in ("rawReferenceTextSha256", "rawHypothesisTextSha256"):
        if value[key] is not None:
            _hash(value[key])
    for key in ("timestampReferenceState", "timestampQuality", "timestampMedianGate", "timestampP95Gate"):
        require(value[key] == shell[key], "TIMESTAMP_NOT_EVALUABLE")
    for area in ("timing", "rtf", "memory", "thermal"):
        _observation(value[area], shell[area])
        if state == "NOT_RUN":
            require(value[area] == shell[area], "UNEXECUTED_OBSERVATION")
    if value["rtf"]["state"] == "OBSERVED":
        _integer(value["rtf"]["elapsedMicros"])
        _integer(value["rtf"]["audioMicros"], 1)
        require(value["timing"]["state"] == "OBSERVED"
                and value["rtf"]["elapsedMicros"] == value["timing"]["inferenceElapsedMicros"]
                and value["rtf"]["audioMicros"] == value["timing"]["audioDurationMicros"],
                "RTF_TIMING_MISMATCH")
    if value["thermal"]["state"] == "OBSERVED":
        initial, maximum = value["thermal"]["initialStatus"], value["thermal"]["maximumStatus"]
        require(initial in THERMAL_STATES and maximum in THERMAL_STATES,
                "MISSING_THERMAL_OBSERVATION")
        require(THERMAL_STATES.index(maximum) >= THERMAL_STATES.index(initial),
                "THERMAL_ORDER_MISMATCH")
    score_fields = ("rawTokenCounts", "normalizedTokenCounts", "oracleCounts", "normalizedWerContribution")
    if state != "SUCCEEDED":
        require(all(value[key] is None for key in score_fields), "UNSCORED_CONTRIBUTION")
        return
    _hash(value["rawReferenceTextSha256"])
    _hash(value["rawHypothesisTextSha256"])
    _shape(value["oracleCounts"], ("raw", "normalized"))
    for stream in ("raw", "normalized"):
        tokens = value[stream + "TokenCounts"]
        edits = value["oracleCounts"][stream]
        _shape(tokens, ("reference", "hypothesis"))
        _integer(tokens["reference"], 1)
        _integer(tokens["hypothesis"])
        require(max(tokens.values()) <= 4096, "ORACLE_INPUT_LIMIT_EXCEEDED")
        _shape(edits, ("substitutions", "deletions", "insertions"))
        for count in edits.values():
            _integer(count)
        require(edits["substitutions"] + edits["deletions"] <= tokens["reference"]
                and edits["substitutions"] + edits["insertions"] <= tokens["hypothesis"]
                and tokens["reference"] - edits["deletions"] + edits["insertions"] == tokens["hypothesis"],
                "INCONSISTENT_ORACLE_COUNTS")
    contribution = value["normalizedWerContribution"]
    _shape(contribution, ("errors", "referenceTokens"))
    _integer(contribution["errors"])
    _integer(contribution["referenceTokens"], 1)
    require(contribution["errors"] == sum(value["oracleCounts"]["normalized"].values())
            and contribution["referenceTokens"] == value["normalizedTokenCounts"]["reference"],
            "INCONSISTENT_WER_CONTRIBUTION")


def main():
    try:
        require(len(sys.argv) == 1, "UNSUPPORTED_ARGUMENTS")
        check_environment()
        print(json.dumps(profile(), sort_keys=True, separators=(",", ":")))
        return 0
    except ContractError as error:
        print(json.dumps({"error": str(error)}))
        return 2


if __name__ == "__main__":
    sys.exit(main())
