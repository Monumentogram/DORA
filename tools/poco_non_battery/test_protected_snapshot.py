"""Synthetic negative controls: no device identities or audio."""
import copy
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).parents[1]))
from poco_protected_snapshot import validate_preservation


def table(columns, rows):
    return {"columns": columns, "rows": rows}


def fixture():
    return {
        "format": "DORA_PRIVATE_CATALOG_V1",
        "schemaVersion": 3,
        "schema": [["table", "synthetic", "synthetic", "CREATE TABLE synthetic"]],
        "tables": {
            "audio_asset": table(["assetId", "recordingId", "sessionId", "ownerId", "vaultId", "finalized"],
                                 [["old-asset", "old-recording", "old-session", "owner", "vault", 0]]),
            "unit_claim": table(["runId", "assetId"], [["old-run", "old-asset"]]),
            "manifest": table(["runId", "digest"], [["old-run", "old-digest"]]),
            "vault_binding": table(["singleton", "ownerId", "vaultId"], [[1, "owner", "vault"]]),
        },
        "keys": {"old-key": {"created": 1, "algorithm": "AES"}},
        "keyProofs": {"old-key": {"ciphertext": "synthetic-challenge"}},
        "verifiedHistoricalKeyProofs": 1,
    }


class ProtectedSnapshotTest(unittest.TestCase):
    def setUp(self):
        self.before = fixture()
        self.after = copy.deepcopy(self.before)
        self.allowed = [{"assetId": "new-asset", "recordingId": "new-recording", "sessionId": "new-session"}]

    def check(self, expected=True):
        if expected:
            return validate_preservation(self.before, self.after, self.allowed, {})
        with self.assertRaises(ValueError):
            validate_preservation(self.before, self.after, self.allowed, {})

    def add_new(self):
        self.after["tables"]["audio_asset"]["rows"].append(
            ["new-asset", "new-recording", "new-session", "owner", "vault", 1])
        self.after["tables"]["unit_claim"]["rows"].append(["new-run", "new-asset"])
        self.after["tables"]["manifest"]["rows"].append(["new-run", "new-digest"])

    def test_unchanged(self):
        self.check()

    def test_permitted_new_source_is_not_historical_mutation(self):
        self.add_new()
        self.check()

    def test_historical_row_changed(self):
        self.after["tables"]["manifest"]["rows"][0][1] = "changed"
        self.check(False)

    def test_historical_row_disappeared(self):
        self.after["tables"]["manifest"]["rows"] = []
        self.check(False)

    def test_extra_historical_row(self):
        self.after["tables"]["manifest"]["rows"].append(["old-run", "additional"])
        self.check(False)

    def test_orphan_row_addition(self):
        self.after["tables"]["manifest"]["rows"].append(["unknown-run", "additional"])
        self.check(False)

    def test_reassigned_historical_run(self):
        self.add_new()
        self.after["tables"]["unit_claim"]["rows"][0][1] = "new-asset"
        self.check(False)

    def test_reused_historical_recording(self):
        self.allowed[0]["recordingId"] = "old-recording"
        self.add_new()
        self.after["tables"]["audio_asset"]["rows"][-1][1] = "old-recording"
        self.check(False)

    def test_unapproved_new_source(self):
        self.add_new()
        self.allowed = []
        self.check(False)

    def test_vault_rebinding(self):
        self.after["tables"]["vault_binding"]["rows"][0][2] = "another"
        self.check(False)

    def test_key_missing(self):
        self.after["keys"] = {}
        self.check(False)

    def test_key_replaced(self):
        self.after["keys"]["old-key"]["created"] = 2
        self.check(False)

    def test_unattributed_new_key(self):
        self.after["keys"]["unapproved-key"] = {"created": 2}
        self.check(False)

    def test_schema_changed(self):
        self.after["schema"].append(["table", "extra", "extra", "CREATE TABLE extra"])
        self.check(False)

    def test_column_omitted(self):
        self.after["tables"]["manifest"]["columns"] = ["runId"]
        self.check(False)

    def test_unknown_table(self):
        self.after["tables"]["unexpected"] = table([], [])
        self.check(False)

    def test_duplicate_row_is_not_hidden_by_set(self):
        self.after["tables"]["manifest"]["rows"] *= 2
        self.check(False)

    def test_integer_is_not_string(self):
        self.after["tables"]["audio_asset"]["rows"][0][-1] = "0"
        self.check(False)

    def test_historical_physical_namespace_reuse(self):
        self.before['tables']['physical_source'] = table(['assetId', 'physicalId'], [['old-asset', 'old-physical']])
        self.after = copy.deepcopy(self.before)
        self.add_new()
        self.after['tables']['physical_source']['rows'].append(['new-asset', 'old-physical'])
        self.check(False)

    def test_cross_test_source_run_reference(self):
        self.before['tables']['deletion_target'] = table(['assetId', 'runId'], [])
        self.after = copy.deepcopy(self.before)
        self.add_new()
        self.allowed.append({'assetId': 'second-asset', 'recordingId': 'second-recording', 'sessionId': 'second-session'})
        self.after['tables']['audio_asset']['rows'].append(['second-asset', 'second-recording', 'second-session', 'owner', 'vault', 0])
        self.after['tables']['deletion_target']['rows'].append(['second-asset', 'new-run'])
        self.check(False)

    def test_missing_crypto_verification(self):
        self.after['verifiedHistoricalKeyProofs'] = 0
        self.check(False)

    def test_fresh_challenge_does_not_prove_old_key_identity(self):
        self.after['keyProofs']['old-key']['ciphertext'] = 'new-challenge'
        self.check(False)

    def test_missing_baseline_key_proof(self):
        self.before['keyProofs'] = {}
        self.after['keyProofs'] = {}
        self.before['verifiedHistoricalKeyProofs'] = 0
        self.after['verifiedHistoricalKeyProofs'] = 0
        self.check(False)


if __name__ == "__main__":
    unittest.main()
