"""Negative controls for public release evidence, not simulated device acceptance."""
import copy
import hashlib
import json
import unittest
from unittest.mock import patch

try:
    import validate_alpha_release as release
except ImportError:
    release = None

SOURCE = "a" * 40
PIN = "e77c9af533e603c0fa7c5c2000eb74b9514d001d98c3e5845cc2bae905be76bf"


def fixture():
    bom = {"bomFormat": "CycloneDX", "specVersion": "1.6", "metadata": {"component": {"version": "0.1.0-alpha.2"}, "properties": [{"name": "dora:source-sha", "value": SOURCE}]}}
    bom_bytes = json.dumps(bom).encode()
    contract = {
        "task": "7.3", "status": "PASS / INTERNAL_ALPHA_APK_BUILD_CI_DEVICE_READY", "application_id": "com.monumentogram.dora",
        "version_code": 4, "version_name": "0.1.0-alpha.2", "certificate_sha256": PIN,
        "source_sha": SOURCE, "branch": "stage/7-alpha-foundation", "distribution": "OWNER_ONLY_CLOSED_INTERNAL_ALPHA",
        "artifact_sha256": "b" * 64, "sbom_sha256": hashlib.sha256(bom_bytes).hexdigest(),
        "migration": "N/A / NO_PRODUCT_PERSISTENCE_SCHEMA_YET",
        "non_execution": {"7.3C": "NOT_STARTED", "stage8": "NOT_STARTED", "aws": "NOT_CALLED", "audio": "NOT_USED", "runtime": "NOT_IMPLEMENTED"},
    }
    build = {
        "source_sha": SOURCE, "source_tree_clean": True, "application_id": "com.monumentogram.dora", "version_code": 4, "version_name": "0.1.0-alpha.2",
        "certificate_sha256": PIN, "signer_count": 1, "artifact": "dora-0.1.0-alpha.2-vc4.apk", "sha256": "b" * 64, "size_bytes": 1234,
        "repeat_sha256": "b" * 64, "reproducibility": "BYTE_IDENTICAL", "signature_verification": "PASS", "zip_alignment_16k": "PASS", "native_elf": "PASS",
        "permissions": [], "debuggable": False, "test_only": False, "unexpected_exported_components": [], "secret_scan": "PASS_ZERO_FINDINGS",
        "signing_backup": {"backup_state": "NEW_ENCRYPTED_BACKUP_CREATED_AND_RESTORE_VERIFIED", "encryption_verified": True, "restore_verified": True, "certificate_sha256": PIN, "private_key_proof": "PASS_RSA3072_SHA256_SIGN_VERIFY_AND_NEGATIVE_CONTROL", "temporary_restore_cleanup": "PASS_ABSENCE_VERIFIED", "active_material_unchanged": True},
        "device": {"model": "POCO M5", "hardware_model": "22071219CG", "android": "14", "api": 34, "abi": "arm64-v8a", "baseline_version_code": 2, "baseline_version_name": "0.1.0-alpha.1", "baseline_certificate_sha256": PIN, "baseline_apk_sha256": "3cb591abc8e33a832bdf018b7cff545a8b3c39641ab74b44e8d4168cfa045d3c", "uid_before": "10000", "uid_after": "10000", "first_install_time_before": "2026-09-30 09:00:00", "first_install_time_after": "2026-09-30 09:00:00", "upgrade": "PASS", "final_version_code": 4, "final_version_name": "0.1.0-alpha.2", "cold_launch": "PASS", "foreground_activity": "com.monumentogram.dora/.MainActivity", "visible_version_label": "DORA Alpha 0.1.0-alpha.2 (4)", "installed_apk_sha256": "b" * 64},
    }
    ci = {"head_sha": SOURCE, "conclusion": "success", "run_id": 1, "jobs": {"android-bootstrap": {"conclusion": "success", "steps": [{"name": "mandatory", "conclusion": "success"}]}, "search-smoke": {"conclusion": "success", "steps": [{"name": "mandatory", "conclusion": "success"}]}}}
    return contract, build, ci, bom_bytes


class ReleaseEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(release, "release evidence validator is required")

    def test_complete_consistent_release_evidence_accepted(self):
        release.validate_closed(*fixture(), SOURCE)

    def reject(self, mutate):
        data = list(fixture()); mutate(data)
        with self.assertRaises((ValueError, KeyError)):
            release.validate_closed(*data, SOURCE)

    def test_version_reuse_rejected(self):
        self.reject(lambda d: d[0].update(version_code=2))

    def test_wrong_signer_rejected(self):
        self.reject(lambda d: d[1].update(certificate_sha256="c" * 64))

    def test_wrong_source_rejected(self):
        self.reject(lambda d: d[1].update(source_sha="c" * 40))

    def test_wrong_sbom_digest_rejected(self):
        self.reject(lambda d: d[0].update(sbom_sha256="c" * 64))

    def test_wrong_installed_version_rejected(self):
        self.reject(lambda d: d[1]["device"].update(final_version_code=3))

    def test_unexpected_permission_rejected(self):
        for permission in ["android.permission.INTERNET", "android.permission.RECORD_AUDIO"]:
            self.reject(lambda d: d[1].update(permissions=[permission]))

    def test_stale_ci_sha_rejected(self):
        self.reject(lambda d: d[2].update(head_sha="c" * 40))

    def test_missing_or_skipped_ci_job_rejected(self):
        self.reject(lambda d: d[2]["jobs"].pop("search-smoke"))
        self.reject(lambda d: d[2]["jobs"]["android-bootstrap"]["steps"][0].update(conclusion="skipped"))

    def test_unverified_restore_and_unclean_source_rejected(self):
        self.reject(lambda d: d[1]["signing_backup"].update(restore_verified=False))
        self.reject(lambda d: d[1].update(source_tree_clean=False))

    def test_uid_install_time_apk_repeatability_and_stage8_mismatch_rejected(self):
        for mutate in [lambda d: d[1]["device"].update(uid_after="10001"), lambda d: d[1]["device"].update(first_install_time_after="different"), lambda d: d[1]["device"].update(installed_apk_sha256="c" * 64), lambda d: d[1].update(repeat_sha256="c" * 64), lambda d: d[0]["non_execution"].update(stage8="PASS")]:
            self.reject(mutate)

    def test_unknown_fields_cannot_replace_required_evidence(self):
        self.reject(lambda d: d[1].pop("signature_verification"))

    def test_tooling_successor_keeps_release_inputs_and_evidence_frozen(self):
        release.validate_successor_paths(['tools/cloud_contract_harness.py',
            'docs/evidence/alpha-7.3c-local-v0.1.json'])
        for path in ('android/app/src/main/New.kt', 'android/alpha-release.properties',
                     'android/app/gradle.lockfile', 'tools/build_internal_alpha.py',
                     'docs/evidence/alpha-7.3-ci-v0.1.json', 'unexpected.py'):
            with self.subTest(path=path), self.assertRaises(ValueError):
                release.validate_successor_paths([path])

    def test_successor_workflow_preserves_every_existing_command(self):
        old = 'before\n      - name: Validate isolated capture PoC contracts\nafter\n'
        current = old.replace('      - name: Validate isolated capture PoC contracts',
                              release.HARNESS_STEP + '      - name: Validate isolated capture PoC contracts')
        release.validate_successor_workflow(old, current)
        with self.assertRaises(ValueError):
            release.validate_successor_workflow(old, current.replace('before','omitted'))

    def test_recovery_preparation_requires_pinned_contract(self):
        import validate_poc_recovery_governance as governance
        contract_bytes = (release.ROOT / governance.REC_CLEAN_CONTRACT).read_bytes()
        paths = json.loads(contract_bytes)["implementation_paths"]
        release.validate_recovery_successor_paths(paths, contract_bytes)
        for changed in (contract_bytes + b"\n", contract_bytes.replace(b'"executionAllowed": false', b'"executionAllowed": true')):
            with self.assertRaisesRegex(ValueError, "contract digest"):
                release.validate_recovery_successor_paths(paths, changed)

    def test_recovery_preparation_cannot_admit_product_or_historical_release_paths(self):
        import validate_poc_recovery_governance as governance
        contract_bytes = (release.ROOT / governance.REC_CLEAN_CONTRACT).read_bytes()
        for path in ('android/app/src/main/New.kt', 'android/alpha-release.properties',
                     'android/poc/recovery/gradle.lockfile', 'android/gradle/verification-metadata.xml',
                     'tools/build_internal_alpha.py', 'docs/evidence/alpha-7.3-ci-v0.1.json',
                     'android/poc/recovery/src/main/New.kt', 'docs/evidence/poc-recovery-001/new.json'):
            with self.subTest(path=path), self.assertRaisesRegex(ValueError, "Product/release"):
                release.validate_recovery_successor_paths([path], contract_bytes)



class RecoveryFinalReceiptTest(unittest.TestCase):
    def context(self):
        import validate_poc_recovery_governance as recovery
        return [recovery.PinnedCommitIdentity(
            "281fe6f13ca353088c731505cc8a7530c3864c7f", "15fd80667512b0700ae45e921cdafdcf00a3c574",
            ("358619c68368a21e0ccf4b00e7eb4662617bd00d",), True),
            "281fe6f13ca353088c731505cc8a7530c3864c7f", ("358619c68368a21e0ccf4b00e7eb4662617bd00d",),
            b"frozen receipt", b"frozen receipt", b"frozen receipt"]

    def test_exact_f_and_direct_child_with_identical_bytes(self):
        data = self.context()
        release.validate_recovery_closure_context(*data)
        data[1:3] = ["a"*40, (data[0].commit,)]
        release.validate_recovery_closure_context(*data)

    def test_wrong_f_identity_tree_parent_or_ancestry(self):
        from dataclasses import replace
        for mutation in (dict(commit="c"*40), dict(tree="c"*40), dict(parents=("c"*40,)),
                         dict(is_ancestor_of_head=False), dict(parents=("c"*40, "d"*40))):
            data = self.context()
            data[0] = replace(data[0], **mutation)
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                release.validate_recovery_closure_context(*data)

    def test_grandchild_merge_and_receipt_mutations_rejected(self):
        for head, parents in (("a"*40, ("b"*40,)), ("a"*40, (self.context()[0].commit, "b"*40)),
                              ("not-a-sha", (self.context()[0].commit,))):
            data = self.context(); data[1:3] = [head, parents]
            with self.subTest(parents=parents), self.assertRaises(ValueError):
                release.validate_recovery_closure_context(*data)
        for index in (3, 4):
            data = self.context(); data[index] = b"modified"
            with self.subTest(layer=index), self.assertRaises(ValueError):
                release.validate_recovery_closure_context(*data)

    def test_exact_path_requires_successful_context_and_no_wildcard(self):
        import validate_poc_recovery_governance as recovery
        contract = (release.ROOT / recovery.REC_CLEAN_CONTRACT).read_bytes()
        path = "docs/evidence/recovery-clean-replacement-integration-v0.1.json"
        with patch.object(release, "validate_recovery_closure_checkout") as check:
            release.validate_recovery_successor_paths([path], contract)
            check.assert_called_once_with()
        with patch.object(release, "validate_recovery_closure_checkout", side_effect=ValueError("No F")):
            with self.assertRaises(ValueError):
                release.validate_recovery_successor_paths([path], contract)
        for forbidden in ("docs/evidence/recovery-clean-replacement-integration-v0.2.json",
                          "docs/evidence/recovery-clean-replacement-random.json",
                          "docs/evidence/poc-recovery-001/new.json", "docs/evidence/alpha-7.3-ci-v0.1.json",
                          "android/app/src/main/New.kt", "android/alpha-release.properties",
                          "android/poc/recovery/src/main/New.kt"):
            with self.subTest(path=forbidden), self.assertRaises(ValueError):
                release.validate_recovery_successor_paths([forbidden], contract)

class SecurityArchitectureReleaseTests(unittest.TestCase):
    def test_security_contract_paths_are_bounded(self):
        import validate_security_identity_contract as security
        release.validate_successor_paths(security.PATHS)
        with self.assertRaises(ValueError):
            release.validate_successor_paths(set(security.PATHS) | {"android/app/build.gradle.kts"})

    def test_security_step_preserves_every_existing_ci_step(self):
        import validate_security_identity_contract as security
        original = "before\nafter\n"
        release.validate_successor_workflow(original, release.HARNESS_STEP + security.STEP + original)
        for current in (release.HARNESS_STEP + security.STEP * 2 + original,
                        release.HARNESS_STEP + security.STEP + original.replace("before", "skipped")):
            with self.assertRaises(ValueError):
                release.validate_successor_workflow(original, current)


if __name__ == "__main__":
    unittest.main()
