"""Generated synthetic metadata only; no dataset, transcript, audio or network."""
from __future__ import annotations

import copy
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

from tools import alpha_asr_pilot_manifest as validator

IDS = {"ru": "cmu5mg3pr00simh07epeylc55", "en": "cmu5nqn1h00vwmi07b4dbk085"}
# Independently calculated using PowerShell/.NET SHA256, not the validator.
FIRST_24 = {
    "ru": [17, 16, 24, 8, 3, 23, 26, 19, 10, 29, 1, 15, 5, 28, 14, 20, 9, 6, 30, 2, 13, 18, 21, 22],
    "en": [26, 20, 23, 9, 28, 18, 25, 6, 14, 13, 3, 1, 17, 5, 11, 16, 7, 30, 21, 12, 8, 27, 22, 10],
}
SCRIPT = Path(__file__).with_name("alpha_asr_pilot_manifest.py")


def encode(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")


def sha(value):
    return hashlib.sha256(value).hexdigest()


def key(row):
    return sha(b"dora-alpha-asr-v0.1\0" + row["locale"].encode() + b"\0"
               + row["upstreamRelativePath"].encode("utf-8"))


def inventory_hash(inventory):
    value = copy.deepcopy(inventory)
    value["candidates"].sort(key=lambda row: (("ru", "en").index(row["locale"]),
                                             row["upstreamRelativePath"].encode("utf-8")))
    return sha(encode(value))


def seal(inventory, manifest):
    manifest["candidateInventorySha256"] = inventory_hash(inventory)
    manifest["manifestSha256"] = sha(encode({k: v for k, v in manifest.items() if k != "manifestSha256"}))


def sample(locale, ordinal):
    return {
        "sampleId": f"sample-{(ordinal + (1000 if locale == 'en' else 0)):016x}",
        "locale": locale, "datasetId": IDS[locale],
        "upstreamRelativePath": f"synthetic/{locale}/clip-{ordinal:03}.mp3",
        "sourceSplit": "test", "audioByteLength": 100 + ordinal,
        "audioSha256": sha(f"fake-audio-{locale}-{ordinal}".encode()),
        "referenceTextSha256": sha(f"fake-reference-digest-{locale}-{ordinal}".encode()),
        "durationMs": 1000 if ordinal % 2 else 20000,
        "referenceTextPresent": True, "decodeResult": "VALIDATED",
    }


def fixture():
    datasets = [{
        "locale": locale, "datasetId": IDS[locale], "version": "5.0",
        "release": "sps-corpus-5.0-2026-09-11", "licenseId": "CC0-1.0",
        "termsReference": "terms-" + ("1" if locale == "ru" else "2") * 32,
        "termsSha256": sha(f"fake-terms-{locale}".encode()),
        "archiveIdentity": f"synthetic-{locale}.tar.gz",
        "archiveSha256": sha(f"fake-archive-{locale}".encode()),
    } for locale in ("ru", "en")]
    common = {
        "schemaVersion": 1, "purpose": "BOUNDED_INTERNAL_ALPHA_ASR_EVALUATION_ONLY",
        "dataClass": "PUBLIC_LICENSED", "datasets": datasets,
        "campaignId": "campaign-" + "3" * 32,
        "storageClass": "LOCAL_PRIVATE_CONTROLLED_STORAGE",
        "trainingAllowed": False, "publicRedistributionAllowed": False,
        "selectionAlgorithm": "dora-alpha-asr-selection-v0.1",
    }
    inventory = {**copy.deepcopy(common), "profileId": "dora-alpha-asr-candidate-inventory-v0.1",
                 "candidateCount": 60,
                 "candidates": [sample(locale, n) for locale in ("ru", "en") for n in range(1, 31)]}
    selected = []
    for locale in ("ru", "en"):
        for rank, ordinal in enumerate(FIRST_24[locale], 1):
            row = sample(locale, ordinal)
            selected.append({**row, "selectionKey": key(row), "rank": rank, "eligibilityResult": "ELIGIBLE"})
    manifest = {**copy.deepcopy(common), "profileId": "dora-alpha-asr-pilot-manifest-v0.1",
                "sampleCount": 48, "samples": selected}
    seal(inventory, manifest)
    return inventory, manifest


class AlphaAsrPilotManifestTests(unittest.TestCase):
    def verify(self, inventory, manifest):
        return validator.verify(encode(inventory), encode(manifest))

    def reject(self, inventory, manifest, code):
        with self.assertRaises(validator.ValidationError) as caught:
            self.verify(inventory, manifest)
        self.assertEqual(code, str(caught.exception))

    def test_known_answer_30_per_locale_selects_exact_24_and_no_decode_claim(self):
        inventory, manifest = fixture()
        self.assertEqual("03cfc66071f6d21aa5c5b09db2db53394accf0e9161e2dffcada26c08b595ed9",
                         manifest["samples"][0]["selectionKey"])
        self.assertEqual("0a515cc8cbc8541ea68cfb5684f8cdcf5294cde47c9743120480a4bdadbba201",
                         manifest["samples"][24]["selectionKey"])
        result = self.verify(inventory, manifest)
        self.assertEqual("PASS", result["result"])
        self.assertEqual({"ru": 24, "en": 24}, result["selectedCounts"])
        self.assertEqual(48, result["sampleCount"])
        self.assertEqual("METADATA_CONTRACT_VALID", result["claim"])
        self.assertFalse(result["audioDecodedByValidator"])
        self.assertFalse(result["inventoryCompletenessVerified"])
        self.assertNotIn("synthetic/", json.dumps(result))

    def test_repeat_and_inventory_permutation_preserve_output_and_inputs(self):
        inventory, manifest = fixture()
        before = encode(inventory), encode(manifest)
        expected = self.verify(inventory, manifest)
        for rows in (inventory["candidates"][::-1],
                     inventory["candidates"][7:] + inventory["candidates"][:7]):
            permuted = {**inventory, "candidates": rows}
            self.assertEqual(expected, self.verify(permuted, manifest))
        self.assertEqual(expected, self.verify(inventory, manifest))
        self.assertEqual(before, (encode(inventory), encode(manifest)))

    def test_filter_excludes_metadata_ineligible_candidates(self):
        inventory, manifest = fixture()
        variations = (("durationMs", 999), ("durationMs", 20001),
                      ("referenceTextPresent", False), ("decodeResult", "FAILED"),
                      ("decodeResult", "NOT_RUN"))
        for n, (field, value) in enumerate(variations, 100):
            row = sample("ru", n)
            row[field] = value
            inventory["candidates"].append(row)
        inventory["candidateCount"] = len(inventory["candidates"])
        seal(inventory, manifest)
        self.assertEqual("PASS", self.verify(inventory, manifest)["result"])

    def test_insufficient_candidates_fails_for_either_language(self):
        for locale in ("ru", "en"):
            with self.subTest(locale=locale):
                inventory, manifest = fixture()
                inventory["candidates"] = [r for r in inventory["candidates"]
                                           if r["locale"] != locale][:24] + [sample(locale, n) for n in range(1, 24)]
                inventory["candidateCount"] = 47
                seal(inventory, manifest)
                self.reject(inventory, manifest, "E_INSUFFICIENT_CANDIDATES")

    def test_exact_root_policy_and_profile_fail_closed(self):
        changes = [
            ("schemaVersion", 2, "E_PROFILE"), ("schemaVersion", True, "E_PROFILE"),
            ("profileId", "other", "E_PROFILE"), ("purpose", "TRAINING", "E_POLICY"),
            ("dataClass", "GENERATED_TEXT", "E_POLICY"),
            ("trainingAllowed", True, "E_POLICY"),
            ("trainingAllowed", 0, "E_POLICY"),
            ("publicRedistributionAllowed", True, "E_POLICY"),
            ("storageClass", "PUBLIC", "E_POLICY"),
            ("selectionAlgorithm", "random-v1", "E_PROFILE"),
        ]
        for field, value, code in changes:
            with self.subTest(field=field, value=value):
                inventory, manifest = fixture()
                manifest[field] = value
                seal(inventory, manifest)
                self.reject(inventory, manifest, code)

    def test_exact_dataset_and_provenance_contract(self):
        for field, value, code in [
            ("datasetId", IDS["en"], "E_DATASET"), ("locale", "de", "E_DATASET"),
            ("version", "3.0", "E_DATASET"), ("licenseId", "OTHER", "E_DATASET"),
            ("release", "latest", "E_DATASET"),
            ("termsReference", "https://invalid.example/?token=fake", "E_PROVENANCE"),
            ("archiveIdentity", "../synthetic.tar.gz", "E_PROVENANCE"),
            ("archiveSha256", "PENDING_DOWNLOAD_VERIFICATION", "E_SHA256"),
            ("termsSha256", "", "E_SHA256"),
        ]:
            with self.subTest(field=field):
                inventory, manifest = fixture()
                manifest["datasets"][0][field] = value
                seal(inventory, manifest)
                self.reject(inventory, manifest, code)
        inventory, manifest = fixture()
        manifest["datasets"][0]["archiveSha256"] = "a" * 64
        seal(inventory, manifest)
        self.reject(inventory, manifest, "E_PROVENANCE_MISMATCH")

    def test_selected_counts_and_language_balance(self):
        for locale in ("ru", "en"):
            with self.subTest(locale=locale):
                inventory, manifest = fixture()
                manifest["samples"].pop(0 if locale == "ru" else 24)
                manifest["sampleCount"] = 47
                seal(inventory, manifest)
                self.reject(inventory, manifest, "E_SELECTED_COUNT")
        inventory, manifest = fixture()
        manifest["samples"][0].update(locale="en", datasetId=IDS["en"])
        seal(inventory, manifest)
        self.reject(inventory, manifest, "E_LANGUAGE_COUNTS")

    def test_selected_identity_split_eligibility_and_integer_bounds(self):
        changes = [
            ("datasetId", "other", "E_DATASET"), ("locale", "de", "E_DATASET"),
            ("sourceSplit", "train", "E_SPLIT"), ("durationMs", 999, "E_ELIGIBILITY"),
            ("durationMs", 20001, "E_ELIGIBILITY"), ("durationMs", True, "E_INTEGER"),
            ("audioByteLength", 0, "E_INTEGER"),
            ("referenceTextPresent", False, "E_ELIGIBILITY"),
            ("decodeResult", "NOT_RUN", "E_ELIGIBILITY"),
            ("eligibilityResult", "MAYBE", "E_ELIGIBILITY"),
        ]
        for field, value, code in changes:
            with self.subTest(field=field, value=value):
                inventory, manifest = fixture()
                manifest["samples"][0][field] = value
                seal(inventory, manifest)
                self.reject(inventory, manifest, code)

    def test_missing_invalid_and_placeholder_hashes(self):
        for field in ("audioSha256", "referenceTextSha256"):
            for value in (None, "", "z" * 64, "0" * 64, "A" * 64):
                with self.subTest(field=field, value=value):
                    inventory, manifest = fixture()
                    manifest["samples"][0][field] = value
                    seal(inventory, manifest)
                    self.reject(inventory, manifest, "E_SHA256")
        inventory, manifest = fixture()
        del manifest["samples"][0]["audioSha256"]
        seal(inventory, manifest)
        self.reject(inventory, manifest, "E_FIELDS")

    def test_duplicates_are_rejected_without_deduplicating_or_skipping(self):
        for field, code in [("sampleId", "E_DUPLICATE_SAMPLE"),
                            ("upstreamRelativePath", "E_DUPLICATE_PATH"),
                            ("audioSha256", "E_DUPLICATE_AUDIO")]:
            with self.subTest(field=field):
                inventory, manifest = fixture()
                manifest["samples"][1][field] = manifest["samples"][0][field]
                seal(inventory, manifest)
                self.reject(inventory, manifest, code)
        for field, code in [("sampleId", "E_DUPLICATE_SAMPLE"),
                            ("upstreamRelativePath", "E_DUPLICATE_PATH")]:
            inventory, manifest = fixture()
            inventory["candidates"][1][field] = inventory["candidates"][0][field]
            seal(inventory, manifest)
            self.reject(inventory, manifest, code)

    def test_unsafe_relative_paths_are_rejected(self):
        paths = ["/audio.mp3", "C:/audio.mp3", "../audio.mp3", "clips/../audio.mp3",
                 "./audio.mp3", "clips/./audio.mp3", "clips\\audio.mp3", "clips//audio.mp3",
                 "clips/\x00audio.mp3", "https://invalid.example/a.mp3", "clips/a.mp3?token=fake",
                 "clips/%2e%2e/audio.mp3", "clips/a.mp3 ", "clips/NUL.mp3"]
        for path in paths:
            with self.subTest(path=repr(path)):
                inventory, manifest = fixture()
                manifest["samples"][0]["upstreamRelativePath"] = path
                seal(inventory, manifest)
                self.reject(inventory, manifest, "E_PATH")

    def test_utf8_paths_preserve_case_and_unicode_without_normalization(self):
        inventory, manifest = fixture()
        # Replace the entire set with 24 per locale: every row must be selected.
        inventory["candidates"] = [sample(locale, n) for locale in ("ru", "en") for n in range(1, 25)]
        inventory["candidates"][0]["upstreamRelativePath"] = "synthetic/ru/\u0416-e\u0301.mp3"
        inventory["candidates"][1]["upstreamRelativePath"] = "synthetic/ru/\u0416-\u00e9.mp3"
        inventory["candidates"][2]["upstreamRelativePath"] = "synthetic/ru/Case.mp3"
        inventory["candidates"][3]["upstreamRelativePath"] = "synthetic/ru/case.mp3"
        inventory["candidateCount"] = 48
        manifest["samples"] = []
        for locale in ("ru", "en"):
            rows = sorted((r for r in inventory["candidates"] if r["locale"] == locale),
                          key=lambda r: (bytes.fromhex(key(r)), r["upstreamRelativePath"].encode("utf-8")))
            manifest["samples"].extend({**r, "selectionKey": key(r), "rank": rank,
                                        "eligibilityResult": "ELIGIBLE"} for rank, r in enumerate(rows, 1))
        seal(inventory, manifest)
        self.assertEqual("PASS", self.verify(inventory, manifest)["result"])

    def test_wrong_selection_key_rank_or_order(self):
        for field, value, code in [("selectionKey", "a" * 64, "E_SELECTION_KEY"),
                                    ("rank", 2, "E_RANK")]:
            inventory, manifest = fixture()
            manifest["samples"][0][field] = value
            seal(inventory, manifest)
            self.reject(inventory, manifest, code)
        inventory, manifest = fixture()
        manifest["samples"][0], manifest["samples"][1] = manifest["samples"][1], manifest["samples"][0]
        seal(inventory, manifest)
        self.reject(inventory, manifest, "E_RANK")

    def test_cherry_picking_and_metadata_tamper_fail_even_with_correct_hashes(self):
        inventory, manifest = fixture()
        replacement = sample("ru", 4)  # Excluded by the independent first-24 answer.
        manifest["samples"][23] = {**replacement, "rank": 24, "selectionKey": key(replacement),
                                   "eligibilityResult": "ELIGIBLE"}
        seal(inventory, manifest)
        self.reject(inventory, manifest, "E_SELECTION")
        inventory, manifest = fixture()
        manifest["samples"][0]["audioByteLength"] += 1
        seal(inventory, manifest)
        self.reject(inventory, manifest, "E_SELECTION")

    def test_inventory_and_manifest_digest_mismatches(self):
        inventory, manifest = fixture()
        manifest["candidateInventorySha256"] = "a" * 64
        manifest["manifestSha256"] = sha(encode({k: v for k, v in manifest.items() if k != "manifestSha256"}))
        self.reject(inventory, manifest, "E_INVENTORY_DIGEST")
        inventory, manifest = fixture()
        manifest["manifestSha256"] = "a" * 64
        self.reject(inventory, manifest, "E_MANIFEST_DIGEST")

    def test_unknown_fields_rejected_at_every_object_boundary(self):
        for where in ("inventory", "manifest", "dataset", "candidate", "sample"):
            with self.subTest(where=where):
                inventory, manifest = fixture()
                target = {"inventory": inventory, "manifest": manifest, "dataset": manifest["datasets"][0],
                          "candidate": inventory["candidates"][0], "sample": manifest["samples"][0]}[where]
                target["client_id"] = "synthetic-forbidden-field"
                seal(inventory, manifest)
                self.reject(inventory, manifest, "E_FIELDS")

    def test_strict_json_rejects_ambiguity_without_echoing_input(self):
        _, manifest = fixture()
        invalid = [b'{"x":1,"x":2}', b'{"x":1,"\\u0078":2}', b'{"x":NaN}',
                   b'{"x":Infinity}', b'{"x":1.5}', b'{"x":1e3}', b'{"x":-0}',
                   b'{"x":9223372036854775808}', b'{"x":"\\ud800"}', b'\xef\xbb\xbf{}',
                   b'{"x":"\xff"}', b'{} trailing', b'[' * 100 + b']' * 100]
        for raw in invalid:
            with self.subTest(raw=raw):
                with self.assertRaises(validator.ValidationError) as caught:
                    validator.verify(raw, encode(manifest))
                self.assertRegex(str(caught.exception), r"^E_[A-Z_]+$")
        inventory, manifest = fixture()
        raw = encode(manifest).replace(b'"durationMs":1000', b'"durationMs":1000.0', 1)
        with self.assertRaises(validator.ValidationError):
            validator.verify(encode(inventory), raw)

    def test_cli_is_read_only_content_free_and_uses_failure_exit_codes(self):
        inventory, manifest = fixture()
        with tempfile.TemporaryDirectory(prefix="dora-alpha-asr-synthetic-") as directory:
            root = Path(directory)
            a, b = root / "synthetic-inventory.json", root / "synthetic-manifest.json"
            a.write_bytes(encode(inventory))
            b.write_bytes(encode(manifest))
            before = a.read_bytes(), b.read_bytes()
            command = [sys.executable, "-B", str(SCRIPT)]
            run = lambda args: subprocess.run(command + args, capture_output=True, timeout=15)
            success = run([str(a), str(b)])
            self.assertEqual(0, success.returncode, success.stderr)
            self.assertEqual("PASS", json.loads(success.stdout)["result"])
            self.assertEqual(b"", success.stderr)
            self.assertEqual(before, (a.read_bytes(), b.read_bytes()))
            self.assertEqual(2, len(list(root.iterdir())))
            bad = copy.deepcopy(manifest)
            bad["client_id"] = "PRIVATE_CANARY"
            b.write_bytes(encode(bad))
            failure = run([str(a), str(b)])
            self.assertEqual(1, failure.returncode)
            self.assertEqual(b"", failure.stdout)
            self.assertEqual({"result": "FAIL", "code": "E_FIELDS"}, json.loads(failure.stderr))
            self.assertNotIn(b"PRIVATE_CANARY", failure.stderr)
            self.assertNotIn(str(root).encode(), failure.stderr)
            self.assertEqual(3, run([str(root / "missing"), str(b)]).returncode)
            self.assertEqual(2, run([]).returncode)
            self.assertEqual(encode(bad), b.read_bytes())


    def test_empty_oversized_and_deep_inputs_fail_closed(self):
        _, manifest = fixture()
        for raw in (b"", b" " * (64 * 1024 * 1024 + 1)):
            with self.assertRaises(validator.ValidationError) as caught:
                validator.verify(raw, encode(manifest))
            self.assertEqual("E_INPUT_SIZE", str(caught.exception))
        with self.assertRaises(validator.ValidationError) as caught:
            validator.verify(b"[" * 20 + b"0" + b"]" * 20, encode(manifest))
        self.assertEqual("E_JSON_DEPTH", str(caught.exception))

    def test_candidate_count_and_top_level_hash_binding(self):
        inventory, manifest = fixture()
        inventory["candidateCount"] -= 1
        seal(inventory, manifest)
        self.reject(inventory, manifest, "E_CANDIDATE_COUNT")
        inventory, manifest = fixture()
        # A non-selected row still participates in the inventory digest.
        inventory["candidates"][3]["audioByteLength"] += 1
        self.reject(inventory, manifest, "E_INVENTORY_DIGEST")
        inventory, manifest = fixture()
        manifest["campaignId"] = "campaign-" + "4" * 32
        seal(inventory, manifest)
        self.reject(inventory, manifest, "E_PROVENANCE_MISMATCH")

    def test_cli_rejects_network_looking_inputs_without_echo(self):
        command = [sys.executable, "-B", str(SCRIPT)]
        for locator in ("https://invalid.example/private", "//invalid.example/private",
                        "\\\\invalid.example\\private"):
            completed = subprocess.run(command + [locator, "unused"], capture_output=True, timeout=15)
            self.assertEqual(1, completed.returncode)
            self.assertEqual({"result": "FAIL", "code": "E_LOCAL_INPUT"}, json.loads(completed.stderr))
            self.assertEqual(b"", completed.stdout)
            self.assertNotIn(b"invalid.example", completed.stderr)


if __name__ == "__main__":
    unittest.main()
