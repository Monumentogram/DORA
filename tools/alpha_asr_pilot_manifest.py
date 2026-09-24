#!/usr/bin/env python3
"""Stage-0 Alpha ASR metadata verification only; Python standard library.

Reads two controlled-private JSON inputs. Never reads audio or transcripts,
decodes media, authenticates, downloads, selects a model, or writes a manifest.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import sys
import unicodedata

SCHEMA_VERSION = 1
INVENTORY_PROFILE = "dora-alpha-asr-candidate-inventory-v0.1"
MANIFEST_PROFILE = "dora-alpha-asr-pilot-manifest-v0.1"
SELECTION_ALGORITHM = "dora-alpha-asr-selection-v0.1"
LOCALES = ("ru", "en")
DATASET_IDS = {"ru": "cmu5mg3pr00simh07epeylc55", "en": "cmu5nqn1h00vwmi07b4dbk085"}
RELEASE = "sps-corpus-5.0-2026-09-11"
MAX_INPUT_BYTES = 64 * 1024 * 1024
MAX_CANDIDATES = 100_000
MAX_DEPTH = 16
MAX_INTEGER = 2**63 - 1

COMMON_FIELDS = frozenset({
    "schemaVersion", "profileId", "purpose", "dataClass", "datasets", "campaignId",
    "storageClass", "trainingAllowed", "publicRedistributionAllowed", "selectionAlgorithm",
})
DATASET_FIELDS = frozenset({
    "locale", "datasetId", "version", "release", "licenseId",
    "termsReference", "termsSha256", "archiveIdentity", "archiveSha256",
})
CANDIDATE_FIELDS = frozenset({
    "sampleId", "locale", "datasetId", "upstreamRelativePath", "sourceSplit",
    "audioByteLength", "audioSha256", "referenceTextSha256", "durationMs",
    "referenceTextPresent", "decodeResult",
})
SELECTED_FIELDS = CANDIDATE_FIELDS | {"selectionKey", "rank", "eligibilityResult"}
POLICY = {
    "purpose": "BOUNDED_INTERNAL_ALPHA_ASR_EVALUATION_ONLY",
    "dataClass": "PUBLIC_LICENSED",
    "storageClass": "LOCAL_PRIVATE_CONTROLLED_STORAGE",
    "trainingAllowed": False,
    "publicRedistributionAllowed": False,
}
SHA256 = re.compile(r"[0-9a-f]{64}")
OPAQUE_SAMPLE = re.compile(r"sample-[0-9a-f]{16}")
RESERVED_DEVICE = re.compile(r"(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])", re.IGNORECASE)


class ValidationError(ValueError):
    """Stable content-free error code: never an input value, field name or path."""


def require(condition, code):
    if not condition:
        raise ValidationError(code)


def canonical_json(value):
    """UTF-8, scalar Unicode key order, compact JSON, no BOM or terminal newline."""
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"),
                      allow_nan=False).encode("utf-8")


def digest(value):
    return hashlib.sha256(canonical_json(value)).hexdigest()


def _unique_object(pairs):
    result = {}
    for name, value in pairs:
        require(name not in result, "E_JSON_DUPLICATE_KEY")
        result[name] = value
    return result


def _integer_token(token):
    require(token != "-0" and len(token) <= 20, "E_JSON_NUMBER")
    value = int(token)
    require(-MAX_INTEGER <= value <= MAX_INTEGER, "E_JSON_NUMBER")
    return value


def _non_integer(_token):
    raise ValidationError("E_JSON_NUMBER")


def _json_tree(value, depth=0):
    require(depth <= MAX_DEPTH, "E_JSON_DEPTH")
    if isinstance(value, dict):
        for name, item in value.items():
            _json_tree(name, depth + 1)
            _json_tree(item, depth + 1)
    elif isinstance(value, list):
        for item in value:
            _json_tree(item, depth + 1)
    elif isinstance(value, str):
        require(len(value) <= 4096, "E_JSON_STRING")
        try:
            value.encode("utf-8", errors="strict")
        except UnicodeError:
            raise ValidationError("E_JSON_UNICODE") from None


def parse_json(raw):
    require(type(raw) is bytes and 0 < len(raw) <= MAX_INPUT_BYTES, "E_INPUT_SIZE")
    try:
        value = json.loads(raw.decode("utf-8", errors="strict"),
                           object_pairs_hook=_unique_object, parse_int=_integer_token,
                           parse_float=_non_integer, parse_constant=_non_integer)
        _json_tree(value)
        return value
    except ValidationError:
        raise
    except (ValueError, UnicodeError, RecursionError):
        raise ValidationError("E_JSON") from None


def _fields(value, expected):
    require(type(value) is dict and value.keys() == expected, "E_FIELDS")


def _int(value, minimum, maximum):
    require(type(value) is int and minimum <= value <= maximum, "E_INTEGER")


def _hash(value):
    require(type(value) is str and SHA256.fullmatch(value) is not None
            and value != "0" * 64, "E_SHA256")


def _path(value):
    require(type(value) is str and 0 < len(value.encode("utf-8")) <= 1024, "E_PATH")
    require(not any(c in value for c in '\\:<>|?*%@"')
            and not any(unicodedata.category(c).startswith("C") for c in value), "E_PATH")
    segments = value.split("/")
    require(all(part and part not in (".", "..") and part == part.strip()
                and not part.endswith(".") and not RESERVED_DEVICE.fullmatch(part.split(".")[0])
                for part in segments), "E_PATH")


def _dataset(value, locale):
    _fields(value, DATASET_FIELDS)
    require(value["locale"] == locale and value["datasetId"] == DATASET_IDS[locale]
            and value["version"] == "5.0" and value["release"] == RELEASE
            and value["licenseId"] == "CC0-1.0", "E_DATASET")
    require(type(value["termsReference"]) is str
            and re.fullmatch(r"terms-[0-9a-f]{32}", value["termsReference"]), "E_PROVENANCE")
    require(type(value["archiveIdentity"]) is str
            and re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,240}\.tar\.gz",
                             value["archiveIdentity"]), "E_PROVENANCE")
    _hash(value["termsSha256"])
    _hash(value["archiveSha256"])


def _root(value, profile, extra_fields):
    _fields(value, COMMON_FIELDS | extra_fields)
    require(type(value["schemaVersion"]) is int and value["schemaVersion"] == SCHEMA_VERSION
            and value["profileId"] == profile
            and value["selectionAlgorithm"] == SELECTION_ALGORITHM, "E_PROFILE")
    for name, expected in POLICY.items():
        require(type(value[name]) is type(expected) and value[name] == expected, "E_POLICY")
    require(type(value["campaignId"]) is str
            and re.fullmatch(r"campaign-[0-9a-f]{32}", value["campaignId"]), "E_PROVENANCE")
    require(type(value["datasets"]) is list and len(value["datasets"]) == 2, "E_DATASET")
    for dataset, locale in zip(value["datasets"], LOCALES):
        _dataset(dataset, locale)


def _candidate(row, selected):
    _fields(row, SELECTED_FIELDS if selected else CANDIDATE_FIELDS)
    require(type(row["locale"]) is str and row["locale"] in LOCALES, "E_DATASET")
    require(row["datasetId"] == DATASET_IDS[row["locale"]], "E_DATASET")
    require(row["sourceSplit"] == "test", "E_SPLIT")
    require(type(row["sampleId"]) is str and OPAQUE_SAMPLE.fullmatch(row["sampleId"]),
            "E_SAMPLE_ID")
    _path(row["upstreamRelativePath"])
    _int(row["audioByteLength"], 1, MAX_INTEGER)
    _int(row["durationMs"], 0, MAX_INTEGER)
    _hash(row["audioSha256"])
    _hash(row["referenceTextSha256"])
    require(type(row["referenceTextPresent"]) is bool
            and row["decodeResult"] in ("VALIDATED", "FAILED", "NOT_RUN"), "E_ELIGIBILITY")
    if selected:
        require(row["eligibilityResult"] == "ELIGIBLE" and _eligible(row), "E_ELIGIBILITY")
        _hash(row["selectionKey"])
        _int(row["rank"], 1, 24)


def _eligible(row):
    # These are supplied metadata assertions, never proof that this program decoded audio.
    return (row["referenceTextPresent"] and row["decodeResult"] == "VALIDATED"
            and 1000 <= row["durationMs"] <= 20000)


def selection_key(row):
    payload = (b"dora-alpha-asr-v0.1\x00" + row["locale"].encode("utf-8") + b"\x00"
               + row["upstreamRelativePath"].encode("utf-8"))
    return hashlib.sha256(payload).digest()


def _rows(rows, selected):
    require(type(rows) is list and len(rows) <= MAX_CANDIDATES, "E_ROWS")
    for row in rows:
        _candidate(row, selected)
    for field, code in (("sampleId", "E_DUPLICATE_SAMPLE"),
                        ("upstreamRelativePath", "E_DUPLICATE_PATH")):
        require(len({r[field] for r in rows}) == len(rows), code)
    if selected:
        require(len({r["audioSha256"] for r in rows}) == len(rows), "E_DUPLICATE_AUDIO")


def inventory_digest(inventory):
    # Candidate input order has no semantics. Other arrays retain their defined order.
    normalized = {**inventory, "candidates": sorted(
        inventory["candidates"],
        key=lambda r: (LOCALES.index(r["locale"]), r["upstreamRelativePath"].encode("utf-8")),
    )}
    return digest(normalized)


def verify(inventory_bytes, manifest_bytes):
    """Validate owned parsed snapshots and return only safe aggregate metadata.

    PASS means consistency relative to the supplied inventory, not that the
    inventory is complete, provider facts are authentic, or materialization ran.
    """
    inventory = parse_json(inventory_bytes)
    manifest = parse_json(manifest_bytes)
    _root(inventory, INVENTORY_PROFILE, {"candidateCount", "candidates"})
    _root(manifest, MANIFEST_PROFILE,
          {"sampleCount", "samples", "candidateInventorySha256", "manifestSha256"})
    for name in COMMON_FIELDS - {"profileId"}:
        require(inventory[name] == manifest[name], "E_PROVENANCE_MISMATCH")
    _int(inventory["candidateCount"], 0, MAX_CANDIDATES)
    _rows(inventory["candidates"], False)
    require(inventory["candidateCount"] == len(inventory["candidates"]), "E_CANDIDATE_COUNT")
    require(type(manifest["sampleCount"]) is int and manifest["sampleCount"] == 48
            and type(manifest["samples"]) is list and len(manifest["samples"]) == 48,
            "E_SELECTED_COUNT")
    _rows(manifest["samples"], True)
    counts = {locale: sum(r["locale"] == locale for r in manifest["samples"])
              for locale in LOCALES}
    require(counts == {"ru": 24, "en": 24}, "E_LANGUAGE_COUNTS")
    expected = []
    for locale in LOCALES:
        eligible = [r for r in inventory["candidates"] if r["locale"] == locale and _eligible(r)]
        require(len(eligible) >= 24, "E_INSUFFICIENT_CANDIDATES")
        eligible.sort(key=lambda r: (selection_key(r), r["upstreamRelativePath"].encode("utf-8")))
        expected.extend(eligible[:24])
    for position, row in enumerate(manifest["samples"]):
        require(row["rank"] == position % 24 + 1, "E_RANK")
        require(row["selectionKey"] == selection_key(row).hex(), "E_SELECTION_KEY")
        require({name: row[name] for name in CANDIDATE_FIELDS} == expected[position], "E_SELECTION")
    _hash(manifest["candidateInventorySha256"])
    _hash(manifest["manifestSha256"])
    inventory_sha = inventory_digest(inventory)
    require(manifest["candidateInventorySha256"] == inventory_sha, "E_INVENTORY_DIGEST")
    manifest_sha = digest({k: v for k, v in manifest.items() if k != "manifestSha256"})
    require(manifest["manifestSha256"] == manifest_sha, "E_MANIFEST_DIGEST")
    return {
        "result": "PASS", "claim": "METADATA_CONTRACT_VALID",
        "sampleCount": 48, "selectedCounts": counts,
        "candidateInventorySha256": inventory_sha, "manifestSha256": manifest_sha,
        "audioDecodedByValidator": False, "inventoryCompletenessVerified": False,
    }


def _read_local(path_text):
    require(not path_text.startswith(("\\\\", "//")) and "://" not in path_text, "E_LOCAL_INPUT")
    path = Path(path_text)
    if path.is_symlink() or not path.is_file():
        raise OSError
    with path.open("rb") as stream:
        return stream.read(MAX_INPUT_BYTES + 1)


def main(argv=None):
    args = sys.argv[1:] if argv is None else argv
    if len(args) != 2:
        print('{"result":"FAIL","code":"E_USAGE"}', file=sys.stderr)
        return 2
    try:
        result = verify(_read_local(args[0]), _read_local(args[1]))
    except ValidationError as error:
        print(canonical_json({"result": "FAIL", "code": str(error)}).decode(), file=sys.stderr)
        return 1
    except OSError:
        print('{"result":"FAIL","code":"E_READ"}', file=sys.stderr)
        return 3
    except Exception:
        # CLI must not disclose a private input or locator through a traceback.
        print('{"result":"FAIL","code":"E_INTERNAL"}', file=sys.stderr)
        return 3
    print(canonical_json(result).decode())
    return 0


if __name__ == "__main__":
    sys.exit(main())
