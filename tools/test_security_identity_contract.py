"""Synthetic negative controls for the 7.4 contract and its bounded admission."""
import copy
import json
import unittest
from pathlib import Path
import os
import subprocess
import tempfile
from unittest.mock import patch

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

    def test_owner_selected_account_device_and_mandatory_lock_policy(self):
        expected = {
            "google_sign_in": "SELECTED_CREDENTIAL_MANAGER",
            "cloud_identity": "GOOGLE_AUTHENTICATED_DORA_USER",
            "installation_proof_role": "DEVICE_BINDING_NOT_USER_IDENTITY",
            "google_token_resource_authorization": False,
            "app_lock": "MANDATORY_SENSITIVE_CONTENT_BIOMETRIC_STRONG_DEVICE_CREDENTIAL",
            "app_lock_optional": False,
            "persisted_unlock": False,
            "automatic_upload_after_sign_in": False,
        }
        for key, value in expected.items():
            with self.subTest(key=key):
                self.assertEqual(self.contract["invariants"].get(key), value)

    def test_rejected_superseded_security_policies(self):
        for key, value in (("google_sign_in", "NOT_SELECTED"),
                           ("cloud_identity", "INVITED_INSTALLATION_PROOF_OF_KEY"),
                           ("app_lock", "OPTIONAL_BIOMETRIC_PROMPT_DEVICE_CREDENTIAL"),
                           ("google_token_resource_authorization", True),
                           ("installation_proof_role", "USER_IDENTITY"),
                           ("app_lock_optional", True), ("persisted_unlock", True)):
            candidate = copy.deepcopy(self.contract)
            candidate["invariants"][key] = value
            with self.subTest(key=key), self.assertRaises(ValueError):
                security.validate_contract(candidate)

    def test_pinned_fetch_precedes_first_dependent_validator(self):
        workflow = (security.ROOT / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")
        fetch = workflow.index("      - name: Fetch pinned Recovery reviewed-source provenance")
        release = workflow.index("      - name: Validate internal Alpha release evidence and negative controls")
        self.assertLess(fetch, release)
        security.validate_ci_provenance_order(workflow)
        for bad in (workflow.replace(security.PROVENANCE_STEP, ""),
                    workflow.replace(security.PROVENANCE_STEP, "") + security.PROVENANCE_STEP,
                    workflow.replace(security.PROVENANCE_SHA, "main"),
                    workflow.replace("        run: >-\n          git fetch", "        if: false\n        run: >-\n          git fetch")):
            with self.assertRaises(ValueError):
                security.validate_ci_provenance_order(bad)

    def test_empty_object_database_fails_closed_without_cached_provenance(self):
        import validate_poc_recovery_governance as recovery
        clean_env = {k: v for k, v in os.environ.items() if not k.startswith("GIT_")}
        with tempfile.TemporaryDirectory(prefix="dora-provenance-empty-") as directory:
            root = Path(directory)
            subprocess.run(["git", "init", "--quiet", str(root)], env=clean_env, check=True)
            self.assertFalse((root / ".git/objects/info/alternates").exists())
            with patch.dict(os.environ, clean_env, clear=True), patch.object(recovery, "ROOT", root):
                self.assertIsNone(recovery.collect_pinned_commit_identity(
                    "89551b17a84bc090ccf1cd36d48aeb59afc403fa", "HEAD").commit)
                with self.assertRaisesRegex(ValueError, "historical reviewed source commit is missing"):
                    recovery.validate_rec_i3_reviewed_source_provenance()


if __name__ == "__main__":
    unittest.main()
