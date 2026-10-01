"""Negative controls for the bounded Stage 8.2 successor; no runtime substitutes."""
import unittest
from pathlib import Path
import hashlib
import json

import validate_encrypted_persistence as gate
import persistence_ci_profile as ci


OLD = '''<verification-metadata><configuration><verify-metadata>true</verify-metadata><verify-signatures>false</verify-signatures></configuration><components><component group="old" name="library" version="1.0"><artifact name="library.jar"><sha256 value="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa" origin="baseline"/></artifact></component></components></verification-metadata>'''
NEW_COMPONENT = '<component group="new" name="library" version="2.0"><artifact name="library.aar"><sha256 value="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" origin="verified"/></artifact></component>'
ADMITTED = {("new", "library", "2.0", "library.aar"): {"b" * 64}}


class PersistenceSuccessorTests(unittest.TestCase):
    def test_device_inventory_is_sealed_and_covers_annotated_source_methods(self):
        source = {'AndroidAppLockTest.kt': '''package com.monumentogram.dora.audio.persistence.auth
class AndroidAppLockTest {
 @Test
 @Suppress("LongMethod") // annotation between Test and method
 fun coldLaunchWithoutCredentialRequiresSystemSetup() {}
 @Test(timeout = 20_000) fun actualSystemCredential() {}
}'''}
        tests = gate.discover_device_tests(source)
        self.assertEqual(2, len(tests))
        control = 'com.monumentogram.dora.audio.persistence.auth.AndroidAppLockTest#coldLaunchWithoutCredentialRequiresSystemSetup'
        raw = json.dumps({'tests': sorted(tests), 'no_credential_tests': [control]}).encode()
        digest = hashlib.sha256(raw).hexdigest()
        gate.validate_device_inventory(raw, tests, digest)
        for payload, expected, checksum in ((raw, tests | {'missing.Test#test'}, digest),
                                             (raw + b' ', tests, digest), (raw, tests, '0' * 64)):
            with self.assertRaises(ValueError):
                gate.validate_device_inventory(payload, expected, checksum)

    def test_working_verification_requires_explicit_local_opt_in(self):
        self.assertFalse(gate.working_verification(None, {}))
        self.assertTrue(gate.working_verification(None, {'DORA_PERSISTENCE_WORKING_CHECK': '1'}))
        self.assertFalse(gate.working_verification(False, {'DORA_PERSISTENCE_WORKING_CHECK': '1'}))
        for environment in ({'DORA_PERSISTENCE_WORKING_CHECK': 'true'},
                            {'DORA_PERSISTENCE_WORKING_CHECK': '1', 'GITHUB_ACTIONS': 'true'},
                            {'GITHUB_EVENT_NAME': 'push'}):
            with self.subTest(environment=environment), self.assertRaises(ValueError):
                gate.working_verification(True if 'DORA_PERSISTENCE_WORKING_CHECK' not in environment else None,
                                          environment)

    def test_product_source_has_no_plaintext_fallback_or_diagnostic_escape(self):
        files = gate.read_product_sources()
        gate.validate_product_sources(files)
        target = gate.PRODUCT + 'persistence/EncryptedAudioVault.kt'
        for forbidden in ('Room.inMemoryDatabaseBuilder(', 'AndroidRecoveryJournalDatabase(',
                          'FrameworkSQLiteOpenHelperFactory(', 'fallbackToDestructiveMigration(',
                          'android.util.Log.e(', 'println(', 'printStackTrace(', 'FileOutputStream('):
            bad = dict(files)
            bad[target] += '\n' + forbidden
            with self.subTest(forbidden=forbidden), self.assertRaises(ValueError):
                gate.validate_product_sources(bad)

    def test_required_encryption_policy_and_dependency_pins_cannot_be_removed(self):
        files = gate.read_product_sources()
        for path, before, after in (
            ('android/core/audio/build.gradle.kts', gate.COORDINATE, 'net.zetetic:sqlcipher-android:4.17.0'),
            ('android/app/build.gradle.kts', 'implementation(project(":core:audio"))', ''),
            ('android/gradle/libs.versions.toml', 'room = "2.8.4"', 'room = "2.8.5"'),
            (gate.PRODUCT + 'persistence/database/SqlCipherJournalHelperFactory.kt', 'PRAGMA synchronous=FULL', 'PRAGMA synchronous=NORMAL'),
            (gate.PRODUCT + 'persistence/keys/VaultSecretStore.kt', 'SecureRandom()::nextBytes', 'insecure::nextBytes'),
            (gate.PRODUCT + 'persistence/journal/RoomAudioJournal.kt', '.openHelperFactory(helperFactory)', ''),
            (gate.PRODUCT + 'persistence/EncryptedAudioVault.kt',
             'dependencies.runKeystore(AndroidVaultKeystoreIo)', 'dependencies.runKeystore(FakeKeyStore)'),
            (gate.PRODUCT + 'persistence/EncryptedAudioVault.kt',
             'val runKeystore: (VaultKeystoreIo) -> VaultKeystoreIo = { it }',
             'val runKeystore: (VaultKeystoreIo) -> VaultKeystoreIo = { FakeKeyStore }'),
        ):
            bad = dict(files)
            self.assertIn(before, bad[path])
            bad[path] = bad[path].replace(before, after)
            with self.subTest(path=path), self.assertRaises(ValueError):
                gate.validate_product_sources(bad)

    def test_required_persistence_ci_cannot_be_removed_or_weakened(self):
        historical = ci.normalize((Path(__file__).resolve().parents[1] / '.github/workflows/android-ci.yml').read_text())
        current = historical.replace('      - name: Verify locked search dependency artifact inventory\n',
                                     ci.STEP + '      - name: Verify locked search dependency artifact inventory\n') + ci.JOBS
        for before, after in ci.REPLACEMENTS.items():
            current = current.replace(before, after)
        ci.validate(current)
        self.assertEqual(historical, ci.normalize(current))
        for before, after in ((ci.STEP, ''), (ci.JOBS, ''), ('api: [28, 36]', 'api: [36]'),
                              ('--expected-page-size 4096', ''), ('--dependency-verification strict', ''),
                              ('--inventory docs/contracts/persistence-8.2-device-tests.json', ''),
                              ('--compiled', ''), ('if-no-files-found: error', 'if-no-files-found: warn')):
            with self.subTest(before=before), self.assertRaises(ValueError):
                ci.validate(current.replace(before, after))

    def test_exact_branch_and_baseline_identity_are_required(self):
        gate.validate_context(gate.BRANCH, gate.BASELINE_TREE, gate.BASELINE_PARENT, "1" * 40, False, False, {})
        for branch, tree, parent in (("main", gate.BASELINE_TREE, gate.BASELINE_PARENT),
                                     (gate.BRANCH, "0" * 40, gate.BASELINE_PARENT),
                                     (gate.BRANCH, gate.BASELINE_TREE, "0" * 40)):
            with self.assertRaises(ValueError):
                gate.validate_context(branch, tree, parent, "1" * 40, False, False, {})

    def test_dirty_checkout_is_only_permitted_for_explicit_local_working_check(self):
        with self.assertRaises(ValueError):
            gate.validate_context(gate.BRANCH, gate.BASELINE_TREE, gate.BASELINE_PARENT, "1" * 40, True, False, {})
        gate.validate_context(gate.BRANCH, gate.BASELINE_TREE, gate.BASELINE_PARENT, "1" * 40, True, True, {})

    def test_ci_cannot_opt_into_working_mode_or_use_pr_merge_context(self):
        event = dict(GITHUB_ACTIONS="true", GITHUB_EVENT_NAME="push", GITHUB_REPOSITORY="Monumentogram/DORA",
                     GITHUB_REF="refs/heads/" + gate.BRANCH, GITHUB_SHA="1" * 40)
        gate.validate_context(gate.BRANCH, gate.BASELINE_TREE, gate.BASELINE_PARENT, "1" * 40, False, False, event)
        for changed in (dict(event, GITHUB_EVENT_NAME="pull_request"), dict(event, GITHUB_SHA="2" * 40),
                        dict(event, GITHUB_REF="refs/pull/88/merge"), dict(event, GITHUB_REPOSITORY="other/DORA")):
            with self.assertRaises(ValueError):
                gate.validate_context(gate.BRANCH, gate.BASELINE_TREE, gate.BASELINE_PARENT, "1" * 40, False, False, changed)
        with self.assertRaises(ValueError):
            gate.validate_context(gate.BRANCH, gate.BASELINE_TREE, gate.BASELINE_PARENT, "1" * 40, False, True, event)

    def test_backup_requires_all_domains_in_both_transfer_modes(self):
        manifest = '<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:allowBackup="false" android:fullBackupContent="@xml/backup_rules" android:dataExtractionRules="@xml/data_extraction_rules"/></manifest>'
        rules = ''.join('<exclude domain="' + d + '" path="."/>' for d in gate.BACKUP_DOMAINS)
        legacy = '<full-backup-content>' + rules + '</full-backup-content>'
        modern = '<data-extraction-rules><cloud-backup>' + rules + '</cloud-backup><device-transfer>' + rules + '</device-transfer></data-extraction-rules>'
        gate.validate_backup(manifest, legacy, modern)
        for m, old, new in ((manifest.replace('allowBackup="false"', 'allowBackup="true"'), legacy, modern),
                             (manifest, legacy.replace('domain="database"', 'domain="file"'), modern),
                             (manifest, legacy, modern.replace('<device-transfer>', '<device-transfer><include domain="file" path="."/>'))):
            with self.assertRaises(ValueError):
                gate.validate_backup(m, old, new)

    def test_unrelated_lock_versions_cannot_change(self):
        old = '# lock\nold:library:1.0=releaseRuntimeClasspath\nempty=test\n'
        new = '# lock\nold:library:1.0=debugRuntimeClasspath,releaseRuntimeClasspath\nnew:library:2.0=releaseRuntimeClasspath\nempty=test\n'
        gate.validate_lock_projection(old, new, {"new:library:2.0"})
        for changed in (new.replace('old:library:1.0', 'old:library:1.1'), new + 'unexpected:library:1.0=test\n'):
            with self.assertRaises(ValueError):
                gate.validate_lock_projection(old, changed, {"new:library:2.0"})

    def test_reviewed_transitive_replacement_preserves_configuration_coverage(self):
        old = 'androidx.annotation:annotation:1.8.2=debug,release\nother:library:1=release\n'
        new = 'androidx.annotation:annotation:1.9.1=debug,release,test\nother:library:1=release\n'
        replacements = {'androidx.annotation:annotation:1.8.2': 'androidx.annotation:annotation:1.9.1'}
        gate.validate_lock_projection(old, new, set(), replacements=replacements)
        for changed in (new.replace('debug,release,test', 'debug,test'),
                        new.replace('other:library:1', 'other:library:2'),
                        new + 'androidx.annotation:annotation:1.8.2=debug,release\n'):
            with self.assertRaises(ValueError):
                gate.validate_lock_projection(old, changed, set(), replacements=replacements)

    def test_replacement_cannot_rename_component_or_admit_nonexistent_predecessor(self):
        old = 'old:library:1=release\n'
        for replacements, new in (({'old:library:1': 'other:library:2'}, 'other:library:2=release\n'),
                                  ({'old:library:0': 'old:library:2'}, 'old:library:1=release\nold:library:2=release\n')):
            with self.assertRaises(ValueError):
                gate.validate_lock_projection(old, new, set(), replacements=replacements)

    def test_configuration_specific_transitive_upgrade_is_exactly_bounded(self):
        old = 'sample:annotations:1=compile,runtime\nsample:annotations:2=lint\n'
        new = 'sample:annotations:1=compile\nsample:annotations:2=lint,runtime\n'
        moves = {'sample:annotations:1': {'replacement': 'sample:annotations:2', 'configurations': ['runtime']}}
        gate.validate_lock_projection(old, new, set(), configuration_moves=moves)
        for changed, policy in ((new, {}),
                                (new.replace('=compile', '=other'), moves),
                                (new.replace('lint,runtime', 'lint'), moves),
                                (new, {'sample:annotations:1': {'replacement': 'unrelated:other:2', 'configurations': ['runtime']}}),
                                (new, {'sample:annotations:1': {'replacement': 'sample:annotations:2', 'configurations': ['compile', 'runtime']}})):
            with self.assertRaises(ValueError):
                gate.validate_lock_projection(old, changed, set(), configuration_moves=policy)

    def test_additive_verified_artifact_preserves_historical_policy(self):
        current = OLD.replace('</components>', NEW_COMPONENT + '</components>')
        gate.validate_verification_projection(OLD, current, ADMITTED)

    def test_removing_historical_hash_is_rejected(self):
        with self.assertRaises(ValueError):
            gate.validate_verification_projection(OLD, OLD.replace('a' * 64, 'c' * 64), {})

    def test_changed_trust_policy_is_rejected(self):
        with self.assertRaises(ValueError):
            gate.validate_verification_projection(OLD, OLD.replace('<verify-metadata>true', '<verify-metadata>false'), {})

    def test_unadmitted_component_is_rejected(self):
        with self.assertRaises(ValueError):
            gate.validate_verification_projection(OLD, OLD.replace('</components>', NEW_COMPONENT + '</components>'), {})

    def test_extra_checksum_for_admitted_artifact_is_rejected(self):
        component = NEW_COMPONENT.replace('</artifact>', '<sha256 value="' + 'c' * 64 + '"/></artifact>')
        with self.assertRaises(ValueError):
            gate.validate_verification_projection(OLD, OLD.replace('</components>', component + '</components>'), ADMITTED)

    def test_duplicate_component_is_rejected(self):
        with self.assertRaises(ValueError):
            gate.validate_verification_projection(OLD, OLD.replace('</components>', NEW_COMPONENT * 2 + '</components>'), ADMITTED)

    def test_one_exact_implementation_transition_is_accepted(self):
        gate.validate_history([(gate.BASELINE, "1" * 40, {"allowed.kt"})], "1" * 40, {"allowed.kt"}, {"receipt.json"})

    def test_forbidden_intermediate_edit_cannot_be_hidden_by_revert(self):
        history = [(gate.BASELINE, "1" * 40, {"allowed.kt", "forbidden.kt"}), ("1" * 40, "2" * 40, {"forbidden.kt"})]
        with self.assertRaises(ValueError):
            gate.validate_history(history, "2" * 40, {"allowed.kt"}, {"receipt.json"})

    def test_evidence_followup_cannot_change_implementation(self):
        with self.assertRaises(ValueError):
            gate.validate_history([(gate.BASELINE, "1" * 40, {"allowed.kt"}), ("1" * 40, "2" * 40, {"allowed.kt"})], "2" * 40, {"allowed.kt"}, {"receipt.json"})

    def test_wrong_predecessor_is_rejected(self):
        with self.assertRaises(ValueError):
            gate.validate_history([("f" * 40, "1" * 40, {"allowed.kt"})], "1" * 40, {"allowed.kt"}, {"receipt.json"})

    def test_status_projection_preserves_complete_historical_bytes(self):
        self.assertEqual("historical\n", gate.validate_status_projection(gate.STATUS_HEADER + "historical\n", "historical\n"))

    def test_premature_pass_is_rejected(self):
        with self.assertRaises(ValueError):
            gate.validate_status_projection(gate.STATUS_HEADER.replace('PENDING_FINAL_PUBLICATION', 'PASS') + "historical\n", "historical\n")

    def test_modified_historical_status_is_rejected(self):
        with self.assertRaises(ValueError):
            gate.validate_status_projection(gate.STATUS_HEADER + "revised history\n", "historical\n")


if __name__ == '__main__':
    unittest.main()
