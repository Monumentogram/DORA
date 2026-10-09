"""Synthetic negative controls for the exact additive Stage 8.6C.2 admission."""
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


class PersistenceDiagnosticAdmissionTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(importlib.util.find_spec('poco_persistence_diagnostic_admission'),
                             'Exact Stage 8.6C.2 admission is not implemented')
        import poco_persistence_diagnostic_admission as admission
        self.admission = admission
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.value = {
            'schemaVersion': 1, 'stage': '8.6C.2',
            'parent': '944b41532e3007ca14d1851d83c2232de1132874',
            'admission': 'DIAGNOSTIC_ONLY', 'runtimePolicy': 'UNCHANGED',
            'rootCause': 'NOT_PROVEN', 'files': {},
        }
        for path in admission.ADDITIONS:
            destination = self.root / path
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(b'synthetic\n')
            self.value['files'][path] = hashlib.sha256(b'synthetic\n').hexdigest()
        self.write_manifest()

    def write_manifest(self):
        path = self.root / self.admission.MANIFEST
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(self.value), encoding='utf-8')

    def test_exact_manifest_accepts_lf_and_crlf_content(self):
        path = 'tools/persistence_latency_analysis.py'
        (self.root / path).write_bytes(b'synthetic\r\n')
        result = self.admission.load(self.root)
        self.assertEqual('NOT_PROVEN', result['rootCause'])
        self.assertEqual(hashlib.sha256(b'synthetic\n').hexdigest(), result['files'][path])

    def test_unapproved_file_in_same_evidence_directory_is_rejected(self):
        self.value['files']['docs/evidence/persistence-latency-8.6c2/private-audio.wav'] = 'a' * 64
        self.write_manifest()
        with self.assertRaisesRegex(ValueError, 'scope'):
            self.admission.load(self.root)

    def test_missing_approved_path_is_rejected(self):
        del self.value['files']['tools/persistence_latency_analysis.py']
        self.write_manifest()
        with self.assertRaisesRegex(ValueError, 'scope'):
            self.admission.load(self.root)

    def test_stale_hash_is_rejected(self):
        (self.root / 'tools/persistence_latency_analysis.py').write_bytes(b'changed\n')
        with self.assertRaisesRegex(ValueError, 'seal'):
            self.admission.load(self.root)

    def test_changed_parent_or_runtime_claim_is_rejected(self):
        for field, value in [('parent', 'a' * 40), ('runtimePolicy', 'MODIFIED'),
                             ('rootCause', 'PROVEN'), ('stage', '9')]:
            with self.subTest(field=field):
                original = self.value[field]
                self.value[field] = value
                self.write_manifest()
                with self.assertRaisesRegex(ValueError, 'identity'):
                    self.admission.load(self.root)
                self.value[field] = original

    def test_duplicate_json_keys_are_rejected(self):
        raw = json.dumps(self.value).replace('"schemaVersion": 1',
                                             '"schemaVersion": 1, "schemaVersion": 1')
        (self.root / self.admission.MANIFEST).write_text(raw, encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'Duplicate'):
            self.admission.load(self.root)

    def test_parent_mutations_and_unapproved_history_paths_are_rejected(self):
        for path in ['android/core/audio/src/main/Injected.kt',
                     'docs/evidence/poco-recording-8.6/result.json',
                     'docs/contracts/poco-reduced-alpha-8.6b.json',
                     'docs/evidence/persistence-latency-8.6c2/unreviewed.json',
                     'docs/evidence/persistence-latency-8.6c2/../escape.json']:
            with self.subTest(path=path), self.assertRaisesRegex(ValueError, 'paths'):
                self.admission.validate_delta({path}, complete=False)

    def test_complete_delta_requires_every_sealed_addition(self):
        with self.assertRaisesRegex(ValueError, 'paths'):
            self.admission.validate_delta({'tools/persistence_latency_analysis.py'}, complete=True)
        self.admission.validate_delta({'tools/persistence_latency_analysis.py'}, complete=False)

    def git(self, *args):
        return subprocess.check_output(['git', '-C', str(self.root), *args],
                                       stderr=subprocess.STDOUT).decode().strip()

    def initialize_parent(self):
        self.git('init', '-q')
        self.git('config', 'user.name', 'Synthetic admission fixture')
        self.git('config', 'user.email', 'fixture@example.invalid')
        self.git('config', 'core.autocrlf', 'false')
        original = b'def validate_checkout():\n    approved = set()\n    return approved\n'
        parent = self.root / self.admission.PARENT
        parent.write_bytes(original)
        historical = self.root / 'historical.txt'
        historical.write_bytes(b'preserved\n')
        self.git('add', self.admission.PARENT, 'historical.txt')
        self.git('-c', 'commit.gpgsign=false', 'commit', '-q', '-m', 'Synthetic parent')
        baseline = self.git('rev-parse', 'HEAD')
        parent.write_bytes(original.replace(b'    return approved',
                                            self.admission.ROUTE.encode() + b'    return approved'))
        return baseline

    def test_real_git_additive_worktree_preserves_parent(self):
        baseline = self.initialize_parent()
        with patch.object(self.admission, 'BASE', baseline):
            self.admission.verify_parent(self.root, complete=True)

    def test_parent_edit_reverted_in_later_commit_is_still_rejected(self):
        baseline = self.initialize_parent()
        historical = self.root / 'historical.txt'
        historical.write_bytes(b'changed\n')
        self.git('add', 'historical.txt')
        self.git('-c', 'commit.gpgsign=false', 'commit', '-q', '-m', 'Synthetic forbidden change')
        historical.write_bytes(b'preserved\n')
        self.git('add', 'historical.txt')
        self.git('-c', 'commit.gpgsign=false', 'commit', '-q', '-m', 'Synthetic restoration')
        self.assertNotIn('historical.txt', self.git('diff', '--name-only', baseline))
        with patch.object(self.admission, 'BASE', baseline), self.assertRaisesRegex(ValueError, 'paths'):
            self.admission.verify_parent(self.root, complete=True)

    def test_ci_cannot_reseal_evidence(self):
        for key in ('GITHUB_ACTIONS', 'GITHUB_EVENT_NAME'):
            with self.subTest(key=key), patch.dict(os.environ, {key: 'true'}):
                with self.assertRaisesRegex(ValueError, 'CI cannot seal'):
                    self.admission.seal(self.root)

    def test_explicit_local_seal_records_final_bytes_without_changing_parent(self):
        baseline = self.initialize_parent()
        changed = 'tools/persistence_latency_analysis.py'
        (self.root / changed).write_bytes(b'final synthetic content\n')
        parent_before = (self.root / self.admission.PARENT).read_bytes()
        with patch.object(self.admission, 'BASE', baseline), patch.dict(
                os.environ, {'GITHUB_ACTIONS': '', 'GITHUB_EVENT_NAME': ''}):
            self.admission.seal(self.root)
            result = self.admission.load(self.root)
        self.assertEqual(hashlib.sha256(b'final synthetic content\n').hexdigest(),
                         result['files'][changed])
        self.assertEqual(parent_before, (self.root / self.admission.PARENT).read_bytes())

    def test_only_exact_reversible_parent_hook_is_admitted(self):
        original = b'def validate_checkout():\n    approved = set()\n    return approved\n'
        current = original.replace(b'    return approved',
                                   self.admission.ROUTE.encode() + b'    return approved')
        self.admission.validate_parent_route(current, original)
        for changed in [current + b'# unrelated edit\n',
                        current.replace(b'return approved', b'return True'),
                        current.replace(self.admission.ROUTE.encode(), b''),
                        current.replace(self.admission.ROUTE.encode(),
                                        self.admission.ROUTE.encode() * 2)]:
            with self.subTest(changed=changed), self.assertRaisesRegex(ValueError, 'parent'):
                self.admission.validate_parent_route(changed, original)

    def test_inherited_exact_ci_identity_remains_fail_closed(self):
        import validate_poco_acceptance as parent
        sha = 'a' * 40
        valid = {'GITHUB_ACTIONS': 'true', 'GITHUB_EVENT_NAME': 'push',
                 'GITHUB_REF': 'refs/heads/stage/8.6-poco-recording-acceptance',
                 'GITHUB_SHA': sha, 'GITHUB_REPOSITORY': 'Monumentogram/DORA'}
        parent.validate_ci(valid, sha, False, False)
        for field, value in [('GITHUB_SHA', 'b' * 40), ('GITHUB_REF', 'refs/heads/main'),
                             ('GITHUB_REPOSITORY', 'other/repository'),
                             ('GITHUB_EVENT_NAME', 'pull_request')]:
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, 'CI identity'):
                parent.validate_ci({**valid, field: value}, sha, False, False)
        for dirty, working in [(True, False), (False, True)]:
            with self.subTest(dirty=dirty), self.assertRaisesRegex(ValueError, 'CI identity'):
                parent.validate_ci(valid, sha, dirty, working)


if __name__ == '__main__':
    unittest.main()
