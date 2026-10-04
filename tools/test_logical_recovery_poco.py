"""The evidence-only child must preserve the parent's runtime and fail closed."""
import os
import unittest
from pathlib import Path

import validate_logical_recovery as parent


class PocoEvidenceSuccessorTest(unittest.TestCase):
    def test_exact_evidence_child_is_admitted_without_runtime_changes(self):
        # Integration through the real historical entry point, including CI identity.
        try:
            result = parent.validate_checkout(allow_working=not bool(os.environ.get('GITHUB_ACTIONS')))
        except ValueError as error:
            self.fail(str(error))
        self.assertTrue(result['vad_runtime_graph'])

    def test_release_entry_point_accepts_sealed_child(self):
        import validate_alpha_release as release
        from unittest.mock import patch
        # Local work opt-in only; never alter real CI identity or bypass clean CI.
        if os.environ.get('GITHUB_ACTIONS'):
            release.validate_recovery_closure_checkout()
        else:
            with patch.dict(os.environ, {'DORA_PERSISTENCE_WORKING_CHECK':'1'}):
                release.validate_recovery_closure_checkout()

    def test_runtime_path_is_rejected(self):
        import validate_poco_acceptance as child
        with self.assertRaisesRegex(ValueError, 'Unapproved'):
            child.validate_paths({'android/app/src/main/Injected.kt'}, {'docs/receipt.json'}, False)

    def test_missing_receipt_is_rejected(self):
        import validate_poco_acceptance as child
        with self.assertRaisesRegex(ValueError, 'missing'):
            child.validate_paths(set(), {'docs/receipt.json'})

    def test_changed_sealed_evidence_is_rejected(self):
        import validate_poco_acceptance as child
        with self.assertRaisesRegex(ValueError, 'seal'):
            child.validate_seal(b'unfavorable attempt removed', '0' * 64)

    def test_ci_wrong_ref_and_sha_are_rejected(self):
        import validate_poco_acceptance as child
        sha = 'a' * 40
        valid = {'GITHUB_ACTIONS':'true', 'GITHUB_EVENT_NAME':'push',
                 'GITHUB_REF':'refs/heads/' + child.BRANCH, 'GITHUB_SHA':sha,
                 'GITHUB_REPOSITORY':'Monumentogram/DORA'}
        child.validate_ci(valid, sha, False, False)
        for key, value in [('GITHUB_REF','refs/heads/stage/8.5-logical-recording-recovery'),
                           ('GITHUB_SHA','b' * 40),('GITHUB_EVENT_NAME','pull_request')]:
            with self.subTest(key=key), self.assertRaisesRegex(ValueError, 'CI identity'):
                child.validate_ci({**valid,key:value},sha,False,False)

    def test_dirty_or_working_ci_is_rejected(self):
        import validate_poco_acceptance as child
        for dirty, working in [(True,False),(False,True)]:
            with self.subTest(dirty=dirty), self.assertRaises(ValueError):
                child.validate_ci({'GITHUB_ACTIONS':'true'},'a'*40,dirty,working)

    def test_parent_route_cannot_hide_other_parent_edits(self):
        import validate_poco_acceptance as child
        original=b'def validate_checkout(root=ROOT, *, allow_working=None):\n    pass\n'
        current=original.replace(b'    pass',child.ROUTE.encode()+b'    pass')
        child.validate_parent_route(current,original)
        with self.assertRaisesRegex(ValueError,'parent'):
            child.validate_parent_route(current.replace(b'pass',b'return True'),original)


if __name__ == '__main__':
    unittest.main()
