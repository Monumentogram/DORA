"""Pure host evaluator tests. Synthetic facts are not AEAD/device evidence."""
import copy
import hashlib
import runpy
import struct
import unittest
from pathlib import Path

campaign = runpy.run_path(str(Path(__file__).with_name('recovery_campaign.py')))
RUN = '00112233445566778899aabbccddeeff'
NAME = 'units/u-0000000001.ct'
ORIGINAL = bytes(range(256)) * 625 + b'x' * 33
FULL = ORIGINAL + b'Z'


def fixture():
    entry = dict(attemptId='SCHEMA6-MICRO-TRU03', runId=RUN, kind='FAULT',
                 caseId='TRU-03', candidateId='REC-MICROFILE-TINK',
                 mutationVariants=['DEFAULT'], plaintextBytes=480000,
                 expectedClassifications=[])
    before, after = (hashlib.sha256(b).hexdigest() for b in (ORIGINAL, FULL))
    baseline = dict(schema='DORA_MICROFILE_TRU03_BASELINE_V1', runId=RUN,
                    candidateId='REC-MICROFILE-TINK', acceptedEnd=480000,
                    committedEnd=480000, sourceRelativeName=NAME,
                    sourceBytes=len(ORIGINAL), sourceSha256=before,
                    processingIntentCount=3, processingIntentSha256='2' * 64)
    # Independently encode the frozen v0.6 identity contract; no production helper.
    lp = lambda text: struct.pack('>H', len(text)) + text.encode('ascii')
    encoded = (lp('poc-recovery-protocol-stage0-v0.6') + lp('REC-MICROFILE-TINK') +
               bytes.fromhex(RUN) + lp(NAME) + lp('MICROFILE_CIPHERTEXT') +
               struct.pack('>Q', len(FULL)) + bytes.fromhex(after))
    intent = hashlib.sha256(encoded).hexdigest()
    proof = dict(schema='DORA_MICROFILE_TRU03_RETAINED_V1', verified=True, journalSchemaVersion=6,
                 baseline=copy.deepcopy(baseline), intentId=intent,
                 destinationRelativeName='objects/q-' + intent + '.bin',
                 candidateId='REC-MICROFILE-TINK', runId=RUN,
                 sourceRelativeName=NAME, artifactRole='MICROFILE_CIPHERTEXT',
                 bootstrapBinding='PRESENT', state='COMPLETED',
                 observedState='REFERENCED_REJECTED', rowCount=1,
                 containerBytes=len(FULL), containerSha256=after,
                 originalExtentBytes=len(ORIGINAL), originalExtentSha256=before,
                 appendByte=90, sourceAbsent=True,
                 processingIntentCount=3, processingIntentSha256='2' * 64)
    observed = dict(acceptedEnd=480000, committedEnd=480000, recoveredEnd=480000,
                    authenticated=True, contiguous=True, repeatStable=True,
                    caseOracleSatisfied=True, classification='VALID',
                    duplicateProcessingIntents=0, missingProcessingIntents=0,
                    microphoneOpens=0, unsafePathOpens=0, processingIntentCount=3,
                    receiptIdentity='3' * 64,
                    microfileControllerObservation=dict(resultType='AuthenticatedPrefix',
                        classification='VALID', failure=None),
                    committedRowObservation=dict(beforeRowCount=3, afterRowCount=3,
                        beforeSha256='2'*64, afterSha256='2'*64, removedRowCount=0),
                    implicitCommitCount=0, microfileTru03Observation=proof,
                    artifactMutationFacts=dict(recipe='TRU-03', relativeName=NAME,
                        beforeBytes=len(ORIGINAL), beforeSha256=before,
                        afterBytes=len(FULL), afterSha256=after,
                        affectedPlaintextStart=160000, recoveryVerdictClaimed=False))
    result = dict(attemptId=entry['attemptId'], status='VALID', candidateResult=observed,
                  hostOracleEqual=True, hostReplayOracleEqual=True,
                  hostMicrofileTru03Baseline=copy.deepcopy(baseline),
                  microfileTru03Replay=copy.deepcopy(observed), cleanupResult='VERIFIED')
    return entry, observed, result


class MicrofileAppend(unittest.TestCase):
    def test_full_original_authenticated_prefix_with_exact_retained_append_passes(self):
        entry, _, result = fixture()
        assessment = campaign['evaluate_attempt'](entry, result)
        self.assertEqual('PASS', assessment['verdict'], assessment)

    def test_missing_untyped_or_legacy_observation_cannot_borrow_schema6_acceptance(self):
        for field, value in [('verified', False), ('journalSchemaVersion', 5), ('journalSchemaVersion', True),
                             ('state', 'PENDING'), ('observedState', 'REFERENCED_DEPENDENT'),
                             ('sourceAbsent', False), ('rowCount', 2), ('rowCount', True),
                             ('artifactRole', 'MANIFEST_CIPHERTEXT'), ('bootstrapBinding', 'ABSENT'),
                             ('candidateId', 'REC-STREAM-TINK'), ('runId', 'f'*32),
                             ('sourceRelativeName', '../unit.ct'), ('intentId', '0'*64),
                             ('destinationRelativeName', 'objects/q-foreign.bin'),
                             ('appendByte', 91), ('appendByte', True),
                             ('containerBytes', len(FULL)+1), ('containerSha256', '0'*64),
                             ('originalExtentBytes', len(ORIGINAL)-1),
                             ('originalExtentSha256', '0'*64), ('processingIntentSha256', '0'*64)]:
            with self.subTest(field=field, value=value):
                entry, observed, result = fixture()
                observed['microfileTru03Observation'][field] = value
                self.assertEqual('FAIL', campaign['evaluate_attempt'](entry, result)['verdict'])
        entry, observed, result = fixture()
        del observed['microfileTru03Observation']
        self.assertEqual('FAIL', campaign['evaluate_attempt'](entry, result)['verdict'])

    def test_identical_malicious_retained_proofs_are_rejected(self):
        # Equal first/replay proofs must not bypass the actual retained guards.
        for field, value in [('schema', 'LEGACY'), ('verified', False),
                             ('journalSchemaVersion', 5), ('journalSchemaVersion', True),
                             ('state', 'PENDING'), ('observedState', 'REFERENCED_DEPENDENT'),
                             ('sourceAbsent', False), ('rowCount', 2), ('rowCount', True),
                             ('artifactRole', 'MANIFEST_CIPHERTEXT'), ('bootstrapBinding', 'ABSENT'),
                             ('candidateId', 'REC-STREAM-TINK'), ('runId', 'f'*32),
                             ('sourceRelativeName', '../unit.ct'), ('intentId', '0'*64),
                             ('destinationRelativeName', 'objects/q-foreign.bin'),
                             ('appendByte', 91), ('appendByte', True),
                             ('containerBytes', len(FULL)+1), ('containerSha256', '0'*64),
                             ('originalExtentBytes', len(ORIGINAL)-1),
                             ('originalExtentSha256', '0'*64),
                             ('processingIntentCount', 4), ('processingIntentSha256', '0'*64)]:
            with self.subTest(field=field, value=value):
                entry, observed, result = fixture()
                observed['microfileTru03Observation'][field] = value
                result['microfileTru03Replay']['microfileTru03Observation'][field] = value
                self.assertEqual(observed['microfileTru03Observation'],
                                 result['microfileTru03Replay']['microfileTru03Observation'])
                self.assertFalse(campaign['microfile_tru03_retained_append'](entry, observed, result))
                self.assertEqual(['TRU-03:SCHEMA6_EXACT_RETAINED_APPEND_AND_REPLAY_REQUIRED'],
                                 campaign['fault_predicate_failures'](entry, observed, result))

    def test_committed_corruption_recipe_guards_are_independent_of_product_fail(self):
        bounds = 'REJECTED_COMMITTED_REGION_AND_ORIGINAL_C_REQUIRED'
        explicit = 'EXPLICIT_CORRUPTION_OR_SPLIT_BRAIN_CLASSIFICATION_REQUIRED'
        region = 'NO_PREFIX_ACROSS_AFFECTED_REGION'
        for case in ('COR-01', 'COR-04', 'TRU-02'):
            with self.subTest(case=case, mutation='valid_rejected_prefix'):
                entry, observed, result = fixture()
                entry['caseId'] = case
                observed.update(recoveredEnd=160000, classification='CORRUPT_MANIFEST')
                observed['artifactMutationFacts']['recipe'] = case
                self.assertEqual([], campaign['fault_predicate_failures'](entry, observed, result))
                assessment = campaign['evaluate_attempt'](entry, result)
                self.assertEqual('FAIL', assessment['verdict'])
                self.assertIn('COMMITTED_LOSS_OR_NONCONTIGUOUS_WATERMARKS', assessment['failures'])
                if case == 'TRU-02':
                    self.assertIn('NORMATIVE_COMMITTED_LOSS_CASE_REQUIRES_PRODUCT_FAIL',
                                  assessment['failures'])
            for target, field, value, reasons in [
                    ('observed', 'recoveredEnd', 480000, [bounds, region]),
                    ('observed', 'classification', 'VALID', [explicit]),
                    ('observed', 'recoveredEnd', 160001, [region]),
                    ('facts', 'affectedPlaintextStart', 'MISSING', [region]),
                    ('facts', 'affectedPlaintextStart', True, [region]),
                    ('observed', 'committedEnd', None, [bounds, region]),
                    ('observed', 'committedEnd', True, [bounds, region]),
                    ('observed', 'committedEnd', 160000, [bounds]),
                    ('observed', 'committedEnd', 480001, [bounds]),
                    ('observed', 'recoveredEnd', -1, [bounds])]:
                with self.subTest(case=case, target=target, field=field, value=value):
                    entry, observed, result = fixture()
                    entry['caseId'] = case
                    observed.update(recoveredEnd=160000, classification='CORRUPT_MANIFEST')
                    observed['artifactMutationFacts']['recipe'] = case
                    destination = observed if target == 'observed' else observed['artifactMutationFacts']
                    if value == 'MISSING':
                        del destination[field]
                    else:
                        destination[field] = value
                    self.assertEqual([case + ':' + reason for reason in reasons],
                                     campaign['fault_predicate_failures'](entry, observed, result))

    def test_first_or_replay_cannot_return_tail_or_unauthenticated_partial_prefix(self):
        for target in ('candidateResult', 'microfileTru03Replay'):
            for key, value in [('recoveredEnd', 480001), ('recoveredEnd', 160000),
                               ('committedEnd', 160000), ('authenticated', False),
                               ('classification', 'CORRUPT_MANIFEST'), ('caseOracleSatisfied', False),
                               ('processingIntentCount', 4), ('receiptIdentity', ''),
                               ('implicitCommitCount', 1)]:
                with self.subTest(target=target, key=key):
                    entry, _, result = fixture()
                    result[target][key] = value
                    self.assertEqual('FAIL', campaign['evaluate_attempt'](entry, result)['verdict'])

    def test_replay_identity_and_both_independent_oracles_are_required(self):
        for mutate in [lambda r: r.update(hostOracleEqual=False),
                       lambda r: r.update(hostReplayOracleEqual=False),
                       lambda r: r.pop('microfileTru03Replay'),
                       lambda r: r['microfileTru03Replay']['microfileTru03Observation'].update(intentId='f'*64),
                       lambda r: r['hostMicrofileTru03Baseline'].update(committedEnd=160000),
                       lambda r: r['hostMicrofileTru03Baseline'].update(processingIntentSha256='f'*64)]:
            entry, _, result = fixture()
            mutate(result)
            self.assertEqual('FAIL', campaign['evaluate_attempt'](entry, result)['verdict'])

    def test_original_journal_and_injection_must_match_exact_baseline(self):
        for field, value in [('beforeBytes', len(ORIGINAL)+1), ('beforeSha256', '0'*64),
                             ('afterBytes', len(FULL)+1), ('afterSha256', '0'*64),
                             ('recipe', 'COR-01'), ('relativeName', 'units/u-0000000002.ct'),
                             ('affectedPlaintextStart', 0), ('recoveryVerdictClaimed', True)]:
            entry, observed, result = fixture()
            observed['artifactMutationFacts'][field] = value
            self.assertEqual('FAIL', campaign['evaluate_attempt'](entry, result)['verdict'])
        entry, observed, result = fixture()
        observed['committedRowObservation']['afterSha256'] = '0'*64
        self.assertEqual('FAIL', campaign['evaluate_attempt'](entry, result)['verdict'])

    def test_genuine_committed_corruption_still_fails_product(self):
        for case in ('COR-01', 'COR-04', 'TRU-02'):
            entry, observed, result = fixture()
            entry['caseId'] = case
            observed.update(recoveredEnd=160000, classification='CORRUPT_MANIFEST')
            self.assertEqual('FAIL', campaign['evaluate_attempt'](entry, result)['verdict'])

    def test_actual_controller_failure_cannot_be_replaced_by_authenticated_flag(self):
        for target in ('candidateResult', 'microfileTru03Replay'):
            entry, _, result = fixture()
            result[target]['microfileControllerObservation']['resultType'] = 'PartialPrefix'
            self.assertEqual('FAIL', campaign['evaluate_attempt'](entry, result)['verdict'])

    def test_stream_exact_remainder_path_is_unchanged(self):
        entry, _, _ = fixture()
        entry.update(candidateId='REC-STREAM-TINK', mutationVariants=['APPEND_1'])
        observed = dict(committedEnd=4056, recoveredEnd=8136, acceptedEnd=8137,
            classification='VALID', preFaultSourceBytes=8192, observedSourceBytes=8193,
            sourceUnchanged=True, processingIntentCount=0, rangeStart=8192, rangeEnd=8193,
            rangeCertainty='EXACT_FORMAT_BOUNDARY')
        self.assertEqual([], campaign['fault_predicate_failures'](entry, observed, {}))
        observed['rangeStart'] = 0
        self.assertTrue(campaign['fault_predicate_failures'](entry, observed, {}))


if __name__ == '__main__': unittest.main()
