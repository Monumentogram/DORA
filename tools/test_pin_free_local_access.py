"""Permanent ADR-0021 gate. Historical evidence and test fixtures are not product code."""
import pathlib
import unittest
from pin_free_policy import FORBIDDEN, violations
import validate_encrypted_persistence as persistence
import validate_pin_free_local_access as local_access

ROOT = pathlib.Path(__file__).resolve().parents[1]


class PinFreeLocalAccessTest(unittest.TestCase):
    def test_no_application_pin_or_biometric_prompt(self):
        self.assertEqual([], violations(ROOT), "NO_APPLICATION_PIN_OR_BIOMETRIC_PROMPT")

    def test_multiline_auth_bound_key_and_platform_prompts_are_rejected(self):
        for source in ('setUserAuthenticationRequired(\n true\n)', 'BiometricPrompt.Builder',
                       'createConfirmDeviceCredentialIntent(', 'ACTION_SET_NEW_PASSWORD'):
            self.assertIsNotNone(FORBIDDEN.search(source))

    def test_future_module_is_checked_but_historical_test_fixture_is_excluded(self):
        import tempfile
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            for variant in ('main', 'androidTest'):
                path = root / 'android/new/module/src' / variant / 'Gate.java'
                path.parent.mkdir(parents=True)
                path.write_text('BiometricPrompt', encoding='utf-8')
            self.assertEqual(['android/new/module/src/main/Gate.java'], violations(root))


class ReviewedKeyPolicyTest(unittest.TestCase):
    SCOPES = 'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/keys/RunKeyFailureScopes.kt'

    @classmethod
    def setUpClass(cls):
        paths = (local_access.HISTORICAL_BLOCKER, local_access.KEY_BACKEND)
        cls.historical = {path: persistence.git(ROOT, 'show', local_access.BASE + ':' + path)
                          for path in paths}
        cls.current = {path: (ROOT / path).read_bytes() for path in (*paths, cls.SCOPES)}

    def test_reviewed_scoped_key_reuse_is_admitted(self):
        try:
            local_access.validate_preserved_policy(self.current, self.historical, successor=True)
        except ValueError as error:
            self.fail('Reviewed ADR-0032 key reuse must be admitted: ' + str(error))

    def test_legacy_noninteractive_policy_stays_admitted(self):
        local_access.validate_preserved_policy(self.historical, self.historical, successor=False)

    def test_successor_cannot_rewrite_the_historical_blocker(self):
        changed = {**self.current, local_access.HISTORICAL_BLOCKER: b'{"status":"SATISFIED"}'}
        with self.assertRaisesRegex(ValueError, 'Historical blocker'):
            local_access.validate_preserved_policy(changed, self.historical, successor=True)

    def test_scoped_reuse_requires_successor_admission(self):
        with self.assertRaises(ValueError):
            local_access.validate_preserved_policy(self.current, self.historical, successor=False)

    def test_altered_keystore_security_policy_is_rejected(self):
        mutations = (
            (b'setUserAuthenticationRequired(false)', b'setUserAuthenticationRequired(true)'),
            (b'setIsStrongBoxBacked(strongBox)', b'setIsStrongBoxBacked(false)'),
            (b'cipher.updateAAD(associatedData)', b'// AAD skipped'),
            (b'if (operations.exists(alias)) throw KeyBoundaryException(KeyFailure.NAMESPACE_OCCUPIED)',
             b'if (operations.exists(alias)) operations.remove(alias)'),
        )
        for before, after in mutations:
            with self.subTest(policy=before):
                self.assertIn(before, self.current[local_access.KEY_BACKEND])
                changed = {**self.current, local_access.KEY_BACKEND:
                           self.current[local_access.KEY_BACKEND].replace(before, after)}
                with self.assertRaisesRegex(ValueError, 'Unreviewed ADR-0032'):
                    local_access.validate_preserved_policy(changed, self.historical, successor=True)

    def test_altered_scoped_lifetime_is_rejected(self):
        mutations = (
            (b'requireAvailable()', b'Unit'),
            (b'active.get() !== attempt || attempt.closed', b'false'),
            (b'attempt.primitive?.close()', b'// keep borrowed primitive'),
            (b'attempt.reuseAllowed = false', b'attempt.reuseAllowed = true'),
        )
        for before, after in mutations:
            with self.subTest(policy=before):
                self.assertIn(before, self.current[self.SCOPES])
                changed = {**self.current, self.SCOPES: self.current[self.SCOPES].replace(before, after)}
                with self.assertRaisesRegex(ValueError, 'Unreviewed ADR-0032'):
                    local_access.validate_preserved_policy(changed, self.historical, successor=True)

    def test_missing_scope_implementation_is_rejected(self):
        changed = {path: source for path, source in self.current.items() if path != self.SCOPES}
        with self.assertRaisesRegex(ValueError, 'Unreviewed ADR-0032'):
            local_access.validate_preserved_policy(changed, self.historical, successor=True)


if __name__ == "__main__":
    unittest.main()
