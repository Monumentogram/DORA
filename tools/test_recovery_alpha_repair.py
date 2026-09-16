"""Reduced repair admission; exact Git-query seam, no Git or device mutations."""
import copy
import hashlib
import json
from pathlib import Path
import unittest
import tempfile
from unittest.mock import patch

import recovery_campaign as campaign
import test_recovery_campaign as campaign_tests
import recovery_alpha_repair as repair
import validate_recovery_0d6_candidate as candidate


class HistoricalRouteTests(unittest.TestCase):
    def test_legacy_fixture_stays_on_legacy_route_with_successor_binding_installed(self):
        with patch.object(candidate, 'ALPHA_PREFIX_REPAIR_BINDING', {}, create=True):
            fixture = ExactRepairProfileTests()
            fixture.setUp()
            try:
                self.assertNotIn('ALPHA_PREFIX_REPAIR_BINDING', fixture.api)
                fixture.test_exact_profile_and_recomputed_applicability_accept()
            finally:
                fixture.doCleanups()

    def test_repaired_profile_cannot_enter_unchanged_apk_scope(self):
        with patch.object(campaign.runpy, 'run_path', return_value={
                'ALPHA_REDUCED_REPAIR_BINDING': {}, 'alpha_preflight_source': lambda: {'would': 'accept'}}):
            with self.assertRaisesRegex(ValueError, 'restricted to reduced'):
                campaign.accepted_alpha_source()


class ExactRepairProfileTests(unittest.TestCase):
    """Use the real candidate validator; substitute only exact read-only Git replies."""
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.directory = Path(self.temp.name)
        self.profile = candidate.Profile('a' * 40, 'b' * 40, candidate.MAINTENANCE_PATHS)
        self.head, self.head_tree = 'c' * 40, 'd' * 40
        self.binding = dict(appApkSha256='e' * 64, testApkSha256='f' * 64, applicabilitySha256='0' * 64)
        blob = lambda value: dict(mode='100644', type='blob', object=value * 40)
        self.before = {path: blob('1') for path in repair.ANDROID_REPAIR_PATHS | repair.PREFLIGHT_METHODS.keys()}
        self.after = copy.deepcopy(self.before)
        for path in repair.ANDROID_REPAIR_PATHS:
            self.after[path] = blob('2')
        self.after['tools/recovery_alpha_repair.py'] = blob('3')
        self.git_overrides = {}
        self.addCleanup(patch.stopall)
        patch.object(candidate, 'git', side_effect=self.git).start()
        self.api = dict(candidate.__dict__, ALPHA_REDUCED_REPAIR_BINDING=self.binding,
                        active_profile=lambda: self.profile)
        # This fixture proves the historical SPL route even when the active
        # metadata installs the separately tested prefix-repair successor.
        self.api.pop('ALPHA_PREFIX_REPAIR_BINDING', None)
        patch.object(repair, 'candidate_api', return_value=self.api).start()
        self.source = dict(commit=self.head, tree=self.head_tree,
                           appApkSha256=self.binding['appApkSha256'], testApkSha256=self.binding['testApkSha256'])
        # The full protocol-generated selection is exercised by RepairRouteTests;
        # here a small exact fixture isolates the frozen-selection comparison.
        self.frozen = dict(executionId='E36RED01', completedBaseAttemptIds=['base'],
                           selectedCompletedBaseAttemptIds=['base'], originalBaseAttemptIds=['base'],
                           faultBaseCount=90, hardKillBaseCount=24, variantCount=165,
                           entries=[dict(originalBaseAttemptId='base', slot=1, mutationVariants=['DEFAULT'],
                                         attemptId='old-base', runId='old', executionEntrySha256='old')])
        frozen = self.write('selection.json', self.frozen)
        patch.object(repair, 'FROZEN_SELECTION_SHA256', frozen['sha256']).start()
        self.selection = copy.deepcopy(self.frozen)
        self.selection.update(executionId='E36REPAIR01')
        self.selection['entries'][0].update(attemptId='new-base', runId='new', executionEntrySha256='new')
        self.review = self.write('review.md', 'Exact eleven-file path/method applicability review')
        self.plan = dict(source=self.source)
        self.gate = dict(source=self.source, supportedPayloads=['CAMPAIGN'], environment='E36-GAPI',
                         physicalAuthorization=dict(authorized=False), reducedSelection=self.selection,
                         proofs=dict(independentReview=self.review),
                         alphaReduced=dict(scope=repair.SCOPE, source=self.source))
        self.proof = repair.applicability_facts(self.api, self.profile, self.binding)
        self.proof.update(frozenSelection=frozen, independentReview=self.review)
        self.bind_proof()

    def write(self, name, value):
        path = self.directory / name
        data = value.encode() if isinstance(value, str) else campaign.canonical(value)
        path.write_bytes(data)
        return dict(path=str(path), sha256=hashlib.sha256(data).hexdigest())

    def bind_proof(self):
        descriptor = self.write('applicability.json', self.proof)
        self.binding['applicabilitySha256'] = descriptor['sha256']
        self.gate['alphaReduced']['sourceRepair'] = descriptor

    def git(self, *args, root):
        self.assertEqual(root, repair.ROOT)
        if args in self.git_overrides:
            return self.git_overrides[args]
        fixed = {
            ('rev-parse', self.profile.implementation_commit + '^{tree}'): self.profile.implementation_tree,
            ('rev-parse', 'HEAD'): self.head, ('rev-parse', 'HEAD^{tree}'): self.head_tree,
            ('branch', '--show-current'): candidate.BRANCH,
            ('show', '-s', '--format=%P', self.head): self.profile.implementation_commit,
            ('rev-list', '--min-parents=2', self.profile.implementation_commit + '..' + self.head): '',
            ('diff-tree', '--no-commit-id', '--name-only', '--no-renames', '-z', '-r', self.head):
                '\0'.join(sorted(candidate.MAINTENANCE_PATHS)) + '\0',
            ('status', '--porcelain'): '',
            ('rev-parse', repair.BASELINE_COMMIT + '^{tree}'): repair.BASELINE_TREE,
            ('merge-base', repair.BASELINE_COMMIT, self.profile.implementation_commit): repair.BASELINE_COMMIT,
            ('rev-parse', repair.HISTORICAL_COMMIT + ':android'): '4' * 40,
            ('rev-parse', repair.BASELINE_COMMIT + ':android'): '4' * 40,
        }
        if args in fixed:
            return fixed[args]
        if len(args) == 4 and args[:2] == ('ls-tree', self.head) and args[2] == '--' and args[3] in candidate.MAINTENANCE_PATHS:
            return '100644 blob ' + '5' * 40 + '\t' + args[3]
        for commit, entries in ((repair.BASELINE_COMMIT, self.before), (self.profile.implementation_commit, self.after)):
            if args == ('ls-tree', '-r', '-z', commit):
                return ''.join(f"{obj['mode']} {obj['type']} {obj['object']}\t{path}\0" for path, obj in sorted(entries.items()))
        self.fail('Unexpected Git query: ' + repr(args))

    def check(self):
        repair.validate(self.plan, self.gate)

    def test_exact_profile_and_recomputed_applicability_accept(self):
        self.check()

    def test_uninstalled_final_pins_fail_closed(self):
        self.api.pop('ALPHA_REDUCED_REPAIR_BINDING')
        with self.assertRaisesRegex(ValueError, 'not installed'):
            self.check()

    def test_source_agreement_cannot_replace_actual_git_or_apk_profile(self):
        for field in ('commit', 'tree', 'appApkSha256', 'testApkSha256'):
            with self.subTest(field=field):
                saved = self.source[field]
                self.source[field] = '9' * len(saved)
                with self.assertRaisesRegex(ValueError, 'actual accepted profile'):
                    self.check()
                self.source[field] = saved

    def test_real_validator_rejects_wrong_parent_tree_dirty_and_metadata_delta(self):
        cases = {
            ('show', '-s', '--format=%P', self.head): ('9' * 40, 'not direct'),
            ('rev-parse', self.profile.implementation_commit + '^{tree}'): ('9' * 40, 'tree drift'),
            ('status', '--porcelain'): (' M tools/recovery_campaign.py', 'dirty'),
            ('diff-tree', '--no-commit-id', '--name-only', '--no-renames', '-z', '-r', self.head):
                ('tools/recovery_alpha_repair.py\0', 'undeclared paths'),
            ('rev-parse', repair.BASELINE_COMMIT + '^{tree}'): ('9' * 40, 'baseline tree'),
            ('merge-base', repair.BASELINE_COMMIT, self.profile.implementation_commit): ('9' * 40, 'descend'),
            ('rev-parse', repair.HISTORICAL_COMMIT + ':android'): ('9' * 40, 'Historical preflight'),
        }
        for query, (response, message) in cases.items():
            with self.subTest(query=query):
                self.git_overrides[query] = response
                with self.assertRaisesRegex(ValueError, message):
                    self.check()
                self.git_overrides.clear()

    def test_unrelated_android_build_and_dependency_changes_rejected(self):
        for path in ('android/build.gradle.kts', 'android/gradle/libs.versions.toml', 'build.gradle.kts',
                     'gradle/wrapper/gradle-wrapper.properties', '.github/workflows/unrelated.yml',
                     'tools/unrelated.py', 'docs/unreviewed-decision.md',
                     next(iter(repair.PREFLIGHT_METHODS))):
            with self.subTest(path=path):
                previous = self.after.get(path)
                self.after[path] = dict(mode='100644', type='blob', object='9' * 40)
                with self.assertRaisesRegex(ValueError, 'Android delta|unrelated repository'):
                    self.check()
                if previous is None:
                    del self.after[path]
                else:
                    self.after[path] = previous

    def test_missing_deleted_and_nonregular_repair_files_rejected(self):
        path = next(iter(repair.ANDROID_REPAIR_PATHS))
        original = self.after[path]
        for replacement, message in ((None, 'non-regular or deleted'),
                                     (self.before[path], 'Android delta'),
                                     (dict(original, mode='120000'), 'non-regular or deleted')):
            with self.subTest(replacement=replacement):
                if replacement is None:
                    del self.after[path]
                else:
                    self.after[path] = replacement
                with self.assertRaisesRegex(ValueError, message):
                    self.check()
                self.after[path] = original

    def test_wrong_delta_and_self_declared_equivalence_cannot_replace_facts(self):
        self.proof['sourceDelta'][0]['after']['object'] = '9' * 40
        self.bind_proof()
        with self.assertRaisesRegex(ValueError, 'recomputed'):
            self.check()
        self.proof['buildInputsUnchanged'] = True
        self.bind_proof()
        with self.assertRaisesRegex(ValueError, 'recomputed'):
            self.check()

    def test_evidence_pin_and_review_bytes_are_bound(self):
        self.binding['applicabilitySha256'] = '9' * 64
        with self.assertRaisesRegex(ValueError, 'pin mismatch'):
            self.check()
        self.bind_proof()
        Path(self.review['path']).write_text('replaced review', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'bytes mismatch'):
            self.check()

    def test_original_selection_slots_variants_and_initial_completed_preference_fixed(self):
        for key, value in (('completedBaseAttemptIds', ['newly-completed']), ('variantCount', 164),
                           ('entries', [dict(self.selection['entries'][0], slot=2)]),
                           ('entries', [dict(self.selection['entries'][0], mutationVariants=['WRONG'])])):
            with self.subTest(key=key, value=value):
                saved = self.selection[key]
                self.selection[key] = value
                with self.assertRaisesRegex(ValueError, 'original 114'):
                    self.check()
                self.selection[key] = saved
        self.selection['executionId'] = self.frozen['executionId']
        with self.assertRaisesRegex(ValueError, 'fresh execution'):
            self.check()

    def test_physical_full_and_preflight_scopes_forbidden(self):
        for field, value in (('alphaCampaign', {}), ('alphaPreflight', {}),
                             ('supportedPayloads', ['CAMPAIGN', 'PREFLIGHT']),
                             ('physicalAuthorization', {'authorized': True}), ('environment', 'PHYSICAL')):
            with self.subTest(field=field):
                gate = copy.deepcopy(self.gate)
                gate[field] = value
                with self.assertRaisesRegex(ValueError, 'restricted to reduced'):
                    repair.validate(self.plan, gate)


class RepairRouteTests(unittest.TestCase):
    def test_changed_apk_repair_uses_separate_applicability_path(self):
        fixture = campaign_tests.AlphaCampaignAdmission()
        fixture.setUp()
        self.addCleanup(fixture.doCleanups)
        root = Path(campaign.__file__).resolve().parents[1]
        source = dict(fixture.source, appApkSha256='c' * 64, testApkSha256='d' * 64)
        plan = campaign.build_plan(root, 'PHASE_A', source, 20260914, 'E36REPAIR01')
        original = campaign.build_plan(root, 'PHASE_A', campaign.ALPHA_PREFLIGHT_SOURCE, 20260914)
        selection = campaign.build_reduced_e36_selection(original, plan, ())
        original_path = fixture.preflight_root / campaign.ALPHA_PREFLIGHT_FULL_PLAN_RELATIVE_PATH
        original_path.parent.mkdir(parents=True, exist_ok=True)
        original_path.write_bytes(campaign.canonical(original))
        original_hash = hashlib.sha256(original_path.read_bytes()).hexdigest()
        owner_path = root / campaign.ALPHA_REDUCED_DECISION_PATH
        gate = copy.deepcopy(fixture.gate)
        gate.pop('alphaCampaign')
        gate.update(source=source, manifestSha256=campaign.digest_json(plan),
                    supportedAttemptIds=selection['supportedAttemptIds'],
                    supportedBaseAttemptIds=selection['originalBaseAttemptIds'], reducedSelection=selection,
                    alphaReduced=dict(decisionId=campaign.ALPHA_REDUCED_DECISION_ID, scope=campaign.ALPHA_REDUCED_SCOPE,
                        source=source, historicalSource=campaign.ALPHA_PREFLIGHT_SOURCE, sourceRepair={'path':'reviewed-proof'},
                        originalPlanSource=original['source'], originalPlanManifestSha256=campaign.digest_json(original),
                        selectionManifestSha256=campaign.digest_json(selection)))
        gate['proofs'].update(ownerDecision=dict(path=str(owner_path),sha256=hashlib.sha256(owner_path.read_bytes()).hexdigest()),
            originalPlan=dict(path=str(original_path),sha256=original_hash,manifestSha256=campaign.digest_json(original)))
        calls = []
        # Only the separately tested repair-profile boundary is substituted.
        def applicability(actual_plan, actual_gate):
            self.assertEqual(actual_plan['source'], source)
            self.assertEqual(actual_gate['reducedSelection'], selection)
            calls.append('verified')
        with patch.object(campaign,'ALPHA_PREFLIGHT_FULL_PLAN_SHA256',original_hash), \
                patch.object(campaign,'validate_alpha_repair',applicability,create=True):
            error = None
            try:
                campaign.validate_execution_gate(plan,gate)
            except ValueError as caught:
                error = str(caught)
            self.assertIsNone(error, error)
            self.assertEqual(calls,['verified'])


if __name__ == '__main__':
    unittest.main()
