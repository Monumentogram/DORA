"""Synthetic q8 binding checks; no private corpus, model or device access."""
import copy
import hashlib
import os
import tempfile
from pathlib import Path
import unittest
from unittest.mock import patch

import alpha_asr_small_campaign as historical
from alpha_asr_campaign import Journal
from asr_small_q8_v01 import alpha_asr_small_q8_binding as binding
from test_alpha_asr_small_campaign import selected, attempt

REPO = Path(historical.__file__).resolve().parents[1]
PINS = {k: str(i) * 64 for i, k in enumerate(('manifest', 'transfer', 'freeze', 'selection'), 1)}


def data_fixture():
    from test_alpha_asr_expanded_holdout import inputs
    import alpha_asr_expanded_holdout as expanded
    from asr_small_arm82_v01 import selector
    rows, refs, sources, audio, people, provider = inputs()
    chosen, audit = expanded.audit(rows, refs, sources, audio, people, provider_authority=provider)
    selected_rows = [dict(r, rank=i % 24 + 1, selectionKey=selector.rank(r)[0].hex(),
                         eligibilityResult='ELIGIBLE', sampleId='sample-' + hashlib.sha256(
                             (r['locale'] + '\0' + r['upstreamRelativePath']).encode()).hexdigest()[:16])
                     for i, r in enumerate(chosen)]
    prior = {'sources': sorted(sources), 'audio': sorted(audio), 'participants': sorted(people)}
    docs = {'candidate-inventory.json': {'rows': rows, 'sourceIndexes': []},
            'reference-bindings.json': [{'source': key, 'bytesHex': value.hex()} for key, value in refs.items()],
            'prior-use-authority.json': prior, 'decode-audit.json': {'synthetic': True},
            'historical-private-baseline.json': {},
            'effective-prior-use-authority.json': {**copy.deepcopy(prior),
                'historicalFilesVerified': 1, 'additionalEvidence': [],
                'counts': {'sources': 144, 'audio': 144, 'participants': 55}},
            'data-authority.json': {'experimentId': binding.EXPERIMENT_ID, 'providerAuthority': provider,
                'sourceIndexes': [], 'purpose': 'BOUNDED_INTERNAL_ALPHA_ASR_EVALUATION_ONLY',
                'retentionDeadline': '2026-10-25', 'training': False, 'tuning': False,
                'normalizerChanged': False, 'scoringChanged': False, 'thresholdsChanged': False,
                'retentionExtension': False, 'redistribution': False,
                'selection': {'ru': 24, 'en': 24, 'order': 'RU24_THEN_EN24',
                    'ranking': 'Unchanged frozen selector.rank', 'shortage': 'NO_RANKING_NO_SELECTION',
                    'manualReplacement': False, 'durationBalancing': False}},
            'pool-audit.json': audit,
            'selected-manifest.json': {'experimentId': binding.EXPERIMENT_ID, 'samples': selected_rows,
                'selectedCounts': {'ru': 24, 'en': 24}, 'bindings': [],
                'eligiblePoolSha256': audit['eligiblePoolSha256'], 'exclusionSetSha256': audit['exclusionsSha256'],
                'priorParticipantsSha256': audit['priorParticipantsSha256']},
            'prior-use-ledger.json': {'experimentId': binding.EXPERIMENT_ID,
                'developmentState': 'NO_SEPARATE_DEVELOPMENT_OR_TUNING_SET_EXISTS',
                'selectedBeforeAnyInference': True, 'allSelectionsConsumedRegardlessOfExecution': True,
                'records': [dict(r, useClass='EVALUATION', consumed=True) for r in selected_rows]},
            'consumed-authority.json': {'sources': sorted(sources | {selector.source_key(r) for r in selected_rows}),
                'audio': sorted(audio | {r['audioSha256'] for r in selected_rows}),
                'participants': sorted(people | {r['participantSha256'] for r in selected_rows})},
            'transfer-index.json': {'files': []},
            'data-freeze.json': {'experimentId': binding.EXPERIMENT_ID,
                'scope': 'NEW_HOLDOUT_DATA_ONLY_MODEL_NOT_EXECUTED', 'asrInferenceBeforeFreeze': 0,
                'selectedCounts': {'ru': 24, 'en': 24}, 'eligiblePoolSha256': audit['eligiblePoolSha256'],
                'retentionDeadline': '2026-10-25'}}
    return docs


def seal(docs):
    """Bind synthetic documents after a deliberate mutation, like an adversarial freeze."""
    def raw(name):
        value = historical.canonical(docs[name]) + b'\n'
        return {'bytes': len(value), 'sha256': hashlib.sha256(value).hexdigest()}
    docs['historical-private-baseline.json']['prior-use-authority.json'] = raw('prior-use-authority.json')
    effective = docs['effective-prior-use-authority.json']
    effective['baselinePin'] = raw('prior-use-authority.json')
    effective['historicalSnapshotPin'] = raw('historical-private-baseline.json')
    authority = docs['data-authority.json']
    authority['historicalPriorUsePin'] = raw('effective-prior-use-authority.json')
    authority['inventoryPriorUsePin'] = raw('prior-use-authority.json')
    authority['sourceAuditPin'] = raw('decode-audit.json')
    ledger = docs['prior-use-ledger.json']
    for field, name in (('inheritedPriorUsePin', 'effective-prior-use-authority.json'),
                         ('dataAuthorityPin', 'data-authority.json'),
                         ('inventoryPin', 'candidate-inventory.json'), ('auditPin', 'pool-audit.json')):
        ledger[field] = raw(name)
    consumed = docs['consumed-authority.json']
    consumed['inheritedPin'] = raw('effective-prior-use-authority.json')
    consumed['newLedgerPin'] = raw('prior-use-ledger.json')
    docs['selected-manifest.json']['dataAuthoritySha256'] = historical.digest(authority)
    data = docs['data-freeze.json']
    for field, name in (('dataAuthoritySha256', 'data-authority.json'),
                        ('manifestSha256', 'selected-manifest.json'), ('transferSha256', 'transfer-index.json'),
                        ('exclusionAuditSha256', 'pool-audit.json'), ('inventorySha256', 'candidate-inventory.json'),
                        ('priorUseLedgerSha256', 'prior-use-ledger.json'), ('consumedAuthoritySha256', 'consumed-authority.json')):
        data[field] = historical.digest(docs[name])
    data['effectivePriorUseAuthorityPin'] = raw('effective-prior-use-authority.json')
    return {name: raw(name) for name in docs}


class Q8BindingTests(unittest.TestCase):
    def test_failed_or_drifted_admission_blocks_even_with_matching_file_pin(self):
        data_pin = {'bytes': 42, 'sha256': '4' * 64}
        admission = {'result': 'PASS / SMALL_Q8_ARTIFACT_INIT_OPERATOR_READY',
                     'dataCommit': 'a' * 40, 'model': copy.deepcopy(binding.MODEL),
                     'initPreflight': {'result': 'PASS_Q8_INIT_AND_LIFECYCLE', 'cleanup': 'VERIFIED',
                                       'modelInitAttempts': 1, 'asrInference': 0, 'dataCommit': 'a' * 40,
                                       'dataFreezePin': data_pin,
                                       'model': {k: binding.MODEL[k] for k in ('bytes', 'sha256')}},
                     'staticVerification': {'result': 'PASS', 'modelSha256': binding.MODEL['sha256'],
                                            'tensorCount': 479, 'exactEndOfFile': True}}
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            path = root / 'docs/evidence/poc-asr-001/asr-small-q8-admission-stage0-v0.1.json'
            path.parent.mkdir(parents=True)
            freeze = {'dataCommit': 'a' * 40, 'sourceFiles': {},
                      'privateDocuments': {'data-freeze.json': data_pin}}
            def check(value):
                raw = historical.canonical(value) + b'\n'; path.write_bytes(raw)
                freeze['sourceFiles'][path.relative_to(root).as_posix()] = {
                    'bytes': len(raw), 'sha256': hashlib.sha256(raw).hexdigest()}
                binding.verify_admission(root, freeze)
            check(admission)
            changes = (((), 'result', 'BLOCKED'), ((), 'dataCommit', 'b' * 40),
                       (('model',), 'sha256', '0' * 64),
                       (('initPreflight',), 'cleanup', 'UNVERIFIED'),
                       (('initPreflight',), 'modelInitAttempts', True),
                       (('initPreflight',), 'asrInference', 1),
                       (('initPreflight',), 'model', {'bytes': 1, 'sha256': '0' * 64}),
                       (('initPreflight',), 'dataCommit', 'b' * 40),
                       (('initPreflight',), 'dataFreezePin', {'bytes': 1, 'sha256': '0' * 64}),
                       (('staticVerification',), 'result', 'FAIL'),
                       (('staticVerification',), 'modelSha256', '0' * 64),
                       (('staticVerification',), 'tensorCount', 478),
                       (('staticVerification',), 'exactEndOfFile', False))
            for parents, key, value in changes:
                with self.subTest(key=key, value=value):
                    bad = copy.deepcopy(admission); target = bad
                    for parent in parents: target = target[parent]
                    target[key] = value
                    with self.assertRaisesRegex(ValueError, 'Q8_ADMISSION_REQUIRED'):
                        check(bad)

    def test_config_cannot_choose_a_new_work_directory_to_bypass_one_shot_claim(self):
        with tempfile.TemporaryDirectory() as td, patch.dict(os.environ, {'LOCALAPPDATA': td}):
            root = Path(td) / 'DORA/private'
            config = {'acceptance': str(root / 'acceptance'), 'work': str(root / 'only-work')}
            frozen = {'acceptanceRelativePath': 'acceptance', 'workRelativePath': 'only-work'}
            binding.check_storage(config, frozen)
            config['work'] = str(root / 'another-work')
            with self.assertRaisesRegex(ValueError, 'FROZEN_STORAGE_BINDING'):
                binding.check_storage(config, frozen)

    def test_data_validation_recomputes_without_mutating_frozen_selection(self):
        docs = data_fixture(); pins = seal(docs); before = copy.deepcopy(docs)
        rows = binding.validate_data(docs, pins)
        self.assertEqual(rows, docs['selected-manifest.json']['samples'])
        self.assertEqual(docs, before)
        rows[0]['locale'] = 'en'
        self.assertEqual(docs, before)

    def test_ledger_cannot_drop_consumption_even_when_document_hashes_are_rebound(self):
        docs = data_fixture(); docs['prior-use-ledger.json']['records'][0]['consumed'] = False
        with self.assertRaisesRegex(ValueError, 'CONSUMPTION_LEDGER'):
            binding.validate_data(docs, seal(docs))

    def test_consumed_authority_must_include_every_selected_row(self):
        docs = data_fixture(); docs['consumed-authority.json']['audio'].pop()
        with self.assertRaisesRegex(ValueError, 'CONSUMED_AUTHORITY_UNION'):
            binding.validate_data(docs, seal(docs))

    def test_rebound_selected_swap_cannot_replace_same_frozen_order(self):
        docs = data_fixture(); rows = docs['selected-manifest.json']['samples']
        rows[0], rows[1] = rows[1], rows[0]
        with self.assertRaisesRegex(ValueError, 'SELECTION_IDENTITY'):
            binding.validate_data(docs, seal(docs))

    def test_reference_bindings_must_not_hide_duplicate_entries(self):
        docs = data_fixture(); docs['reference-bindings.json'].append(copy.deepcopy(docs['reference-bindings.json'][0]))
        with self.assertRaisesRegex(ValueError, 'REFERENCE_BINDING_SET'):
            binding.validate_data(docs, seal(docs))

    def test_q8_composition_preserves_scoring_and_historical_profile(self):
        before = copy.deepcopy(historical.profile())
        c, op = binding.composition(REPO, PINS)
        self.assertEqual(historical.profile(), before)
        self.assertIs(op.c, c)
        self.assertEqual(c.profile()['modelArtifact'], 'ggml-small-q8_0.bin')
        self.assertEqual(c.profile()['modelBytes'], 264464607)
        rows = selected()
        cases = [attempt(r) for r in rows]
        for count in (48, 19):
            if count == 19:
                cases[18]['inferenceMicros'] = 6000001
            old = historical.evaluate(before, rows, cases[:count], 'VERIFIED')
            new = c.evaluate(c.profile(), rows, cases[:count], 'VERIFIED')
            self.assertEqual({k: v for k, v in old.items() if k not in ('profileId', 'profileSha256')},
                             {k: v for k, v in new.items() if k not in ('profileId', 'profileSha256')})

    def test_semantic_or_wrong_model_delta_is_rejected(self):
        c, _ = binding.composition(REPO, PINS)
        for key, value in (('modelBytes', 1), ('modelArtifact', 'other.bin'),
                           ('attemptPolicy', 'RETRY'), ('warmup', 'NONE')):
            p = c.profile(); p[key] = value
            with self.assertRaises(ValueError):
                binding.check_semantics(p, historical.profile())
        p = c.profile(); p['resourceGates']['maximumRtf'] = 7
        with self.assertRaises(ValueError): binding.check_semantics(p, historical.profile())

    def test_claim_survives_restart_and_rejects_nonempty_work(self):
        with tempfile.TemporaryDirectory() as td:
            work = Path(td)
            binding.claim(work, {'synthetic': True})
            first = (work / 'campaign-started.json').read_bytes()
            with self.assertRaises(ValueError): binding.claim(work, {'synthetic': False})
            self.assertEqual((work / 'campaign-started.json').read_bytes(), first)
        with tempfile.TemporaryDirectory() as td:
            work = Path(td); (work / 'warmup-started.json').write_text('{}')
            with self.assertRaises(ValueError): binding.claim(work, {})

    def test_independent_expected_commit_cannot_be_replaced_by_envelope(self):
        envelope = {'operatorId': binding.OPERATOR_ID, 'experimentId': binding.EXPERIMENT_ID,
                    'baseCommit': 'a' * 40, 'dataCommit': 'b' * 40,
                    'branch': 'chat/alpha-asr-runner-scope', 'campaignInvocationBudget': 1,
                    'retentionDeadline': '2026-10-25'}
        binding.check_execution_authority(envelope, 'a' * 40)
        for bad in ('b' * 40, '', None):
            with self.assertRaises(ValueError): binding.check_execution_authority(envelope, bad)
        envelope['campaignInvocationBudget'] = True
        with self.assertRaises(ValueError): binding.check_execution_authority(envelope, 'a' * 40)

    def test_path_traversal_and_pin_mismatch_rejected(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td); p = root / 'file'; p.write_bytes(b'synthetic')
            for name in ('../outside', '/absolute', 'C:/absolute', 'nested/../../outside', 'a\\b'):
                with self.assertRaises(ValueError): binding.beneath(root, name)
            with self.assertRaises(ValueError):
                binding.pin_file(p, {'bytes': 9, 'sha256': '0' * 64})

    def test_missing_source_pin_cannot_disable_verification(self):
        with self.assertRaisesRegex(ValueError, 'SOURCE_PIN_SET'):
            binding.verify_source_files(REPO, {})

    def test_unauthorized_api_does_not_reach_filesystem(self):
        with self.assertRaisesRegex(ValueError, 'MEASURED_EXECUTION_NOT_AUTHORIZED'):
            binding.campaign({}, '0' * 64, 'a' * 40)

    def test_inherited_sequence_verifies_each_boundary_and_never_retries(self):
        c, op = binding.composition(REPO, PINS)
        rows = selected(); events = []
        class GeneratedDriver:
            def warmup(self): events.append('warmup'); return 'VERIFIED'
            def case(self, row, position):
                events.append(position)
                result = attempt(row); result['inferenceMicros'] = 6000001
                return result
            def cleanup(self): events.append('cleanup'); return 'VERIFIED'
        with tempfile.TemporaryDirectory() as td:
            journal = Journal(Path(td) / 'attempts.sqlite')
            try:
                result = op.run_sequence(rows, GeneratedDriver(), journal,
                                         lambda: events.append('verify'))
                self.assertEqual(events, ['verify', 'warmup', 'verify', 1, 'verify', 'cleanup'])
                self.assertEqual(result['resourceFailures'], ['RTF_MAXIMUM'])
                with self.assertRaises(ValueError):
                    op.run_sequence(rows, GeneratedDriver(), journal, lambda: None)
                self.assertEqual(len(journal.records()), 1)
            finally: journal.close()


if __name__ == '__main__': unittest.main()
