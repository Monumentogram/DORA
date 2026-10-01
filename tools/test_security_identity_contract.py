"""Synthetic negative controls for the 7.4 contract and its bounded admission."""
import copy
import json
import unittest
from pathlib import Path

try:
    import validate_security_identity_contract as security
except ImportError:
    security = None


class SecurityContractTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(security, "7.4 validator required")
        self.contract = json.loads((security.ROOT / security.CONTRACT).read_text(encoding="utf-8"))

    def test_accepted_contract_and_exact_human_projection(self):
        security.validate_contract(self.contract)
        self.assertEqual(security.render(self.contract), (security.ROOT / security.DOCUMENT).read_text(encoding="utf-8"))

    def test_every_security_invariant_rejects_missing_or_opposite_value(self):
        for key, value in self.contract["invariants"].items():
            for replacement in (None, not value if type(value) is bool else "INVALID"):
                candidate = copy.deepcopy(self.contract)
                candidate["invariants"][key] = replacement
                with self.subTest(key=key, replacement=replacement), self.assertRaises(ValueError):
                    security.validate_contract(candidate)
            candidate = copy.deepcopy(self.contract)
            del candidate["invariants"][key]
            with self.subTest(missing=key), self.assertRaises(ValueError):
                security.validate_contract(candidate)

    def test_numeric_values_cannot_be_boolean_or_unbounded(self):
        for key in ("access_ttl_seconds", "refresh_idle_seconds", "session_absolute_seconds", "proof_nonce_ttl_seconds", "app_lock_grace_seconds", "version_code"):
            for value in (True, False, 0, -1, 999999999):
                if type(value) is int and value == self.contract["invariants"][key]:
                    continue
                candidate = copy.deepcopy(self.contract); candidate["invariants"][key] = value
                with self.subTest(key=key, value=value), self.assertRaises(ValueError):
                    security.validate_contract(candidate)

    def test_missing_decision_or_secret_location_rejected(self):
        for field in ("actors", "sections", "secret_locations"):
            candidate = copy.deepcopy(self.contract); candidate[field].pop()
            with self.subTest(field=field), self.assertRaises(ValueError):
                security.validate_contract(candidate)

    def test_secret_logging_and_client_provider_keys_rejected(self):
        for index in range(len(self.contract["secret_locations"])):
            candidate = copy.deepcopy(self.contract); candidate["secret_locations"][index]["logs"] = "ALLOWED"
            with self.subTest(index=index), self.assertRaises(ValueError):
                security.validate_contract(candidate)
        candidate = copy.deepcopy(self.contract); candidate["secret_locations"][9]["android"] = "APK"
        with self.assertRaises(ValueError):
            security.validate_contract(candidate)

    def test_no_wildcard_path_admission(self):
        security.validate_paths(security.PATHS)
        for path in ("android/app/build.gradle.kts", "android/poc/recovery/x.kt", "docs/evidence/alpha-7.3-ci-v0.1.json", "docs/contracts/DORA_PR86_CLEAN_RECOVERY_INTEGRATION_V0_1.json", "tools/recovery_campaign.py", "docs/security/unreviewed.md"):
            with self.subTest(path=path), self.assertRaises(ValueError):
                security.validate_paths(set(security.PATHS) | {path})

    def test_status_header_keeps_stage8_unstarted(self):
        security.validate_status(security.ROOT)


if __name__ == "__main__":
    unittest.main()
