"""Mutation controls for additive schema, frozen gates and exact process identity."""
import copy
import json
from pathlib import Path
import unittest

import validate_original_audio_lifecycle as gate
import original_audio_ci_profile as ci
import run_encrypted_persistence_crash as crash

ROOT = Path(__file__).resolve().parents[1]


class OriginalAudioGateTests(unittest.TestCase):
    def test_status_projection_preserves_parent_and_never_claims_future_pass(self):
        old = 'historical status\n'
        self.assertEqual(old, gate.validate_status_projection(gate.STATUS_HEADER + old, old))
        for bad in (gate.STATUS_HEADER + 'changed\n', (gate.STATUS_HEADER + old).replace('PENDING_FINAL_PUBLICATION', 'PASS'), old):
            with self.assertRaises(ValueError): gate.validate_status_projection(bad, old)

    def schemas(self):
        folder = ROOT / 'android/core/audio/schemas/com.monumentogram.dora.audio.persistence.journal.AudioJournalDatabase'
        return tuple(json.loads((folder / f'{v}.json').read_text()) for v in (1, 2))

    def test_additive_schema_preserves_every_existing_table_and_column(self):
        before, after = self.schemas()
        gate.validate_schema(before, after)
        for mutate in ('old-column', 'missing-old-table', 'cascade', 'extra-table', 'version'):
            bad = copy.deepcopy(after)
            if mutate == 'old-column': bad['database']['entities'][0]['fields'][0]['notNull'] = False
            if mutate == 'missing-old-table': bad['database']['entities'].pop(0)
            if mutate == 'cascade': bad['database']['entities'][-1]['foreignKeys'][0]['onDelete'] = 'CASCADE'
            if mutate == 'extra-table': bad['database']['entities'].append(copy.deepcopy(bad['database']['entities'][0]))
            if mutate == 'version': bad['database']['version'] = 3
            with self.subTest(mutate=mutate), self.assertRaises(ValueError): gate.validate_schema(before, bad)

    def test_workflow_roundtrip_preserves_all_parent_steps_and_rejects_gate_removal(self):
        parent = gate.parent_file(ROOT, '.github/workflows/android-ci.yml').decode()
        updated = ci.upgrade(parent)
        self.assertEqual(parent, ci.normalize(updated))
        for changed in (updated.replace('--original-audio', ''), updated.replace(ci.EXTRA_STEP, ''), updated + ci.EXTRA_STEP):
            with self.assertRaises(ValueError): ci.normalize(changed)

    def test_process_verifier_rejects_old_test_receipt_for_lifecycle_run(self):
        output = ('INSTRUMENTATION_STATUS: stream=DORA_PERSISTENCE_VERIFIED:PUBLISHED:124\nINSTRUMENTATION_STATUS_CODE: 2\n'
                  f'INSTRUMENTATION_STATUS: class={crash.TEST.split("#")[0]}\n'
                  f'INSTRUMENTATION_STATUS: test={crash.TEST.split("#")[1]}\n'
                  'INSTRUMENTATION_STATUS_CODE: 0\nOK (1 test)\nINSTRUMENTATION_CODE: -1\n')
        with self.assertRaises(ValueError): crash.verified_pid(output, 'PUBLISHED', 123, expected_test=crash.ORIGINAL_AUDIO_TEST)
        output = output.replace(crash.TEST.split('#')[0], crash.ORIGINAL_AUDIO_TEST.split('#')[0]).replace(crash.TEST.split('#')[1], crash.ORIGINAL_AUDIO_TEST.split('#')[1])
        self.assertEqual(124, crash.verified_pid(output, 'PUBLISHED', 123, expected_test=crash.ORIGINAL_AUDIO_TEST))


if __name__ == '__main__': unittest.main()
