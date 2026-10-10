"""Synthetic fail-closed controls for exact C3 admission; no device or private data."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch


class OptimizationAdmissionTest(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(importlib.util.find_spec('poco_persistence_optimization_admission'),
                             'C3 exact successor admission missing')
        import poco_persistence_optimization_admission as admission
        self.a = admission

    def test_delta_rejects_unrelated_runtime_and_historical_evidence(self):
        for path in ('android/core/audio/src/main/Injected.kt','docs/evidence/persistence-latency-8.6c2/RESULT.md',
                     'docs/contracts/poco-reduced-alpha-8.6b.json','docs/evidence/persistence-optimization-8.6c3/private.json'):
            with self.assertRaises(ValueError): self.a.validate_delta({path},complete=False)
        self.a.validate_delta(self.a.ADDITIONS | self.a.OVERRIDES | {self.a.MANIFEST},complete=True)
        with self.assertRaises(ValueError): self.a.validate_delta(set(),complete=True)

    def test_parent_whitelist_retains_every_historical_c2_addition(self):
        import poco_persistence_diagnostic_admission as c2
        self.assertTrue(hasattr(self.a,'approved_paths'),'C3 cumulative parent whitelist missing')
        expected=self.a.ADDITIONS|self.a.OVERRIDES|{self.a.MANIFEST}|c2.ADDITIONS|{c2.MANIFEST}
        self.assertEqual(expected,self.a.approved_paths())

    def test_exact_hooks_recover_parent_and_reject_bypass(self):
        root = Path(__file__).resolve().parents[1]
        import validate_encrypted_persistence as p
        original = p.git(root,'show',self.a.BASE+':'+self.a.PARENT)
        current = self.a.upgrade_parent(original)
        self.a.validate_parent_route(current,original)
        for changed in (current+b'\n# unrelated\n', current.replace(b'c3.load_reduced',b'c3.skip_reduced')):
            with self.assertRaises(ValueError): self.a.validate_parent_route(changed,original)

    def test_override_requires_both_reviewed_hashes(self):
        before,after=b'before\n',b'after\n'
        entry=dict(before=self.a.digest(before),after=self.a.digest(after))
        self.assertEqual(before,self.a.normalize_override(after,before,entry))
        for current,original in ((b'changed',before),(after,b'changed')):
            with self.assertRaises(ValueError): self.a.normalize_override(current,original,entry)

    def test_manifest_rejects_current_hash_scope_identity_and_duplicate_key_drift(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            def git(*args):
                return subprocess.check_output(['git','-C',str(root),*args],stderr=subprocess.STDOUT).decode().strip()
            git('init','-q'); git('config','user.name','Synthetic'); git('config','user.email','synthetic@example.invalid')
            for path in self.a.OVERRIDES:
                target=root/path; target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(b'before\n')
            git('add','.'); git('-c','commit.gpgsign=false','commit','-qm','Synthetic original overrides')
            base=git('rev-parse','HEAD')
            for path in self.a.ADDITIONS|self.a.OVERRIDES:
                target=root/path; target.parent.mkdir(parents=True,exist_ok=True); target.write_bytes(b'after\n')
            with patch.object(self.a,'BASE',base):
                value={**self.a.identity(),'files':{p:self.a.digest(b'after\n') for p in self.a.ADDITIONS},
                       'overrides':{p:dict(before=self.a.digest(b'before\n'),after=self.a.digest(b'after\n')) for p in self.a.OVERRIDES}}
                manifest=root/self.a.MANIFEST; manifest.parent.mkdir(parents=True,exist_ok=True)
                def save(v): manifest.write_text(json.dumps(v),encoding='utf-8')
                save(value); self.a.load(root)
                for changed in (dict(value,stage='9'),dict(value,files={}),dict(value,overrides={})):
                    save(changed)
                    with self.assertRaises(ValueError): self.a.load(root)
                save(value)
                target=root/next(iter(self.a.OVERRIDES)); target.write_bytes(b'not reviewed\n')
                with self.assertRaises(ValueError): self.a.load(root)
                target.write_bytes(b'after\n')
                manifest.write_text(json.dumps(value).replace('"schemaVersion": 1','"schemaVersion": 1, "schemaVersion": 1'),encoding='utf-8')
                with self.assertRaises(ValueError): self.a.load(root)

    def test_reduced_historical_seal_requires_exact_current_override_before_normalizing(self):
        import poco_reduced_admission as reduced
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            def git(*args):
                return subprocess.check_output(['git','-C',str(root),*args],stderr=subprocess.STDOUT).decode().strip()
            git('init','-q'); git('config','user.name','Synthetic'); git('config','user.email','synthetic@example.invalid')
            path='tools/poco_non_battery/protected_policy.py'
            target=root/path; target.parent.mkdir(parents=True); target.write_bytes(b'before\n')
            old=dict(overrides={p:{} for p in reduced.OVERRIDES},protectedBase=reduced.PROTECTED_BASE,
                     protectedOverrides={p:{} for p in reduced.PROTECTED_OVERRIDES},files={path:self.a.digest(b'before\n')})
            manifest=root/reduced.MANIFEST; manifest.parent.mkdir(parents=True); manifest.write_text(json.dumps(old))
            git('add','.'); git('-c','commit.gpgsign=false','commit','-qm','Synthetic historical reduced policy')
            base=git('rev-parse','HEAD'); target.write_bytes(b'after\n')
            contract={'overrides':{path:dict(before=self.a.digest(b'before\n'),after=self.a.digest(b'after\n'))}}
            with patch.object(self.a,'BASE',base):
                self.assertEqual(old,self.a.load_reduced(root,contract))
                with self.assertRaises(ValueError): reduced.load(root)
                target.write_bytes(b'unreviewed\n')
                with self.assertRaises(ValueError): self.a.load_reduced(root,contract)
                target.write_bytes(b'after\n'); old['files'][path]=self.a.digest(b'after\n'); manifest.write_text(json.dumps(old))
                with self.assertRaises(ValueError): self.a.load_reduced(root,contract)

    def test_ci_cannot_reseal_or_admit_working_tree(self):
        import validate_encrypted_persistence as p
        for key in ('GITHUB_ACTIONS','GITHUB_EVENT_NAME'):
            with patch.dict(os.environ,{key:'true'}):
                with self.assertRaises(ValueError): self.a.seal(Path('nonexistent'))
                with self.assertRaises(ValueError): p.working_verification(True,os.environ)

    def test_inventory_preserves_old_tests_controls_and_exact_current_methods(self):
        old=dict(schema_version=1,tests=['com.monumentogram.dora.audio.persistence.auth.AndroidAppLockTest#coldLaunchWithoutCredentialRequiresSystemSetup'],
                 no_credential_tests=['com.monumentogram.dora.audio.persistence.auth.AndroidAppLockTest#coldLaunchWithoutCredentialRequiresSystemSetup'])
        extra='com.monumentogram.dora.audio.persistence.NewTest#check'
        new=dict(old,tests=old['tests']+[extra])
        self.a.validate_inventory_projection(old,new,set(new['tests']))
        for changed in (dict(new,tests=[extra]),dict(new,no_credential_tests=[]),dict(new,tests=new['tests']+[extra])):
            with self.assertRaises(ValueError): self.a.validate_inventory_projection(old,changed,set(new['tests']))

    def test_ci_upgrade_exact_reversible_and_mandatory(self):
        self.assertIsNotNone(importlib.util.find_spec('persistence_optimization_ci_profile'))
        import persistence_optimization_ci_profile as profile
        import validate_encrypted_persistence as p
        root=Path(__file__).resolve().parents[1]
        old=p.git(root,'show',self.a.BASE+':.github/workflows/android-ci.yml').decode()
        new=profile.upgrade(old)
        self.assertEqual(old,profile.normalize(new))
        for changed in (new.replace(profile.GATE,''),new.replace(profile.HOST_GATE,''),new.replace(profile.NEW_INVENTORY,profile.OLD_INVENTORY),
                        new.replace(profile.GATE,profile.GATE.replace('--expected-api','--ignored-api',1))):
            with self.assertRaises(ValueError): profile.normalize(changed)

    def test_diagnostic_inventory_rejects_skips_duplicates_and_unapproved_classes(self):
        self.assertIsNotNone(importlib.util.find_spec('run_protected_diagnostic_c3'))
        import run_protected_diagnostic_c3 as runner
        names=[name+'#syntheticTest' for name in sorted(runner.CLASSES)]
        value=dict(schema_version=1,tests=names)
        self.assertEqual(set(names),runner.validate_inventory(value))
        for changed in (dict(value,tests=names+names[:1]),dict(value,tests=names[:-1]),
                        dict(value,tests=names+['com.monumentogram.dora.audio.diagnostics.PersistenceScalingBenchmarkTest#realEncryptedScaling']),
                        dict(value,no_credential_tests=[])):
            with self.assertRaises(ValueError): runner.validate_inventory(changed)

    def test_diagnostic_credential_cleanup_even_when_setup_or_tests_interrupt(self):
        import run_protected_diagnostic_c3 as runner
        for stage in ('setup','body','success'):
            calls=[]
            def command(*args):
                calls.append(args)
                if 'set-pin' in args and stage=='setup': raise KeyboardInterrupt()
                return 'Pin set to synthetic' if 'set-pin' in args else 'Lock credential cleared'
            def action():
                if stage=='body': raise KeyboardInterrupt()
                return 'done'
            if stage=='success': self.assertEqual('done',runner.credential_phase(command,action,'123456'))
            else:
                with self.assertRaises(KeyboardInterrupt): runner.credential_phase(command,action,'123456')
            self.assertEqual(('shell','locksettings','clear','--old','123456'),calls[-1])

    def test_legacy_isolation_selects_exact_methods_when_diagnostic_package_expands(self):
        from poco_non_battery import diagnostic_isolation as isolation
        extra = isolation.PACKAGE + '.DiagnosticSuccessorLoaderTest#newSuccessorControl'
        def output(names, code=0):
            rows = []
            for name in names:
                cls, method = name.split('#')
                rows.extend(('INSTRUMENTATION_STATUS: class=' + cls,
                             'INSTRUMENTATION_STATUS: test=' + method,
                             'INSTRUMENTATION_STATUS_CODE: ' + str(code)))
            return '\n'.join(rows + [f'OK ({len(names)} tests)', 'INSTRUMENTATION_CODE: -1'])
        calls = []
        def command(*args, **kwargs):
            calls.append(args)
            if args[:2] == ('shell', 'getprop'): return '1'
            if args[0] == 'install': return 'Success'
            selected = args[args.index('-e') + 2]
            names = sorted(isolation.EXPECTED | {extra}) if 'package' in args else selected.split(',')
            return output(names)
        with patch.object(isolation.zipfile, 'ZipFile') as archive, patch.object(Path, 'read_bytes', return_value=b'synthetic'):
            archive.return_value.__enter__.return_value.namelist.return_value = []
            receipt = isolation.run(command)
        self.assertEqual(isolation.EXPECTED, {row['name'] for row in receipt['tests']})
        instrument = calls[-1]
        self.assertEqual(('class', ','.join(sorted(isolation.EXPECTED))),
                         instrument[instrument.index('-e') + 1:instrument.index('-e') + 3])
        expected = sorted(isolation.EXPECTED)
        for names, code in ((expected + [extra], 0), (expected[:-1], 0),
                            (expected + expected[:1], 0), (expected, -4), (expected, -2)):
            with self.assertRaises(ValueError): isolation.verify_output(output(names, code))

    def test_real_git_history_rejects_reverted_unapproved_edit(self):
        with tempfile.TemporaryDirectory() as temp:
            root=Path(temp)
            def git(*args):
                return subprocess.check_output(['git','-C',str(root),*args],stderr=subprocess.STDOUT).decode().strip()
            git('init','-q'); git('config','user.name','Synthetic'); git('config','user.email','synthetic@example.invalid')
            (root/'historical.txt').write_text('old')
            git('add','.'); git('-c','commit.gpgsign=false','commit','-qm','Synthetic baseline')
            base=git('rev-parse','HEAD')
            for value in ('changed','old'):
                (root/'historical.txt').write_text(value)
                git('add','.'); git('-c','commit.gpgsign=false','commit','-qm','Synthetic change')
            self.assertEqual('',git('diff','--name-only',base))
            with patch.object(self.a,'BASE',base),self.assertRaisesRegex(ValueError,'paths'):
                self.a.validate_history(root)


if __name__ == '__main__': unittest.main()
