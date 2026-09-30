"""Neutral offline logical snapshots; no persistence, text replay or provider mocks."""
import copy
import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[1]
CONTRACT = ROOT / 'docs/contracts/DORA_ALPHA_TRANSCRIPT_VERSIONING_EDIT_CONTRACT_V0_1.json'
NOW = '2026-09-30T00:00:00Z'


def uid(n):
    return f'00000000-0000-4000-8000-{n:012d}'


def unknown():
    return {'availability': 'UNKNOWN', 'value': None}


def stamp():
    return {'availability': 'UNAVAILABLE', 'start_us': None, 'end_us': None, 'quality': 'UNKNOWN'}


def source():
    return {'recording_id': uid(1), 'audio_asset_id': uid(2), 'sha256': '0' * 64}


def revision(n):
    return {'recording_id': uid(1), 'ordinal': n}


def version(n, engine):
    processing = {k: unknown() for k in ('implementation_version', 'route_id', 'provider_id',
        'provider_version', 'model_id', 'model_version', 'adapter_profile_id', 'adapter_profile_version')}
    processing['implementation_id'] = 'synthetic-engine'
    if engine == 'LOCAL':
        for k in ('route_id', 'provider_id', 'provider_version', 'adapter_profile_id', 'adapter_profile_version'):
            processing[k] = {'availability': 'NOT_APPLICABLE', 'value': None}
    return {'transcript_id': uid(10 + n), 'recording_id': uid(1), 'source': source(), 'version_order': n,
            'engine': engine, 'processing_status': 'VALID', 'processing': processing,
            'raw_provenance': {'processing_action_id': uid(40+n), 'processing_job_id': uid(50+n),
                'idempotency_key': uid(60+n), 'attempt_id': uid(70+n), 'configuration_ref': uid(80+n),
                'generation': n, 'provider_operation_ref': unknown()}, 'merge_provenance': None,
            'created_at': NOW, 'processed_at': unknown(), 'text': 'alpha',
            'segments': [{'segment_id': uid(30), 'transcript_id': uid(10+n), 'order': 0,
                          'source': source(), 'text': 'alpha', 'timestamp': stamp()}]}


def anchor(transcript=11):
    return {'source': source(), 'base_transcript_id': uid(transcript),
            'segment_ref': {'transcript_id': uid(transcript), 'segment_id': uid(30)},
            'timestamp': stamp(), 'context': {'before': '', 'target': 'alpha', 'after': '', 'boundary': 'NONE'},
            'char_range': None}


def edit(n=1, base=11):
    return {'edit_id': uid(100+n), 'recording_id': uid(1), 'base_transcript_id': uid(base),
            'base_edit_revision': revision(n-1), 'revision': revision(n), 'actor': 'USER',
            'created_at': NOW, 'kind': 'REPLACE', 'anchor': anchor(base), 'payload': 'beta',
            'mapping_state': 'EXACT', 'conflict': 'NONE'}


def initial():
    d = {'decision_id': uid(200), 'recording_id': uid(1), 'from_transcript_id': None,
         'target_transcript_id': uid(11), 'proposal_id': None, 'base_edit_revision': revision(0),
         'base_selection_revision': 0, 'actor': 'SYSTEM', 'created_at': NOW,
         'kind': 'AUTO_INITIAL', 'outcome': 'ACCEPTED', 'reason': 'EXPECTED_RESULT', 'resolution_ids': []}
    return {'recording_id': uid(1), 'lifecycle': 'LIVE',
            'sources': [{'source': source(), 'duration_us': 1000000, 'availability': 'AVAILABLE'}],
            'current_source': source(), 'versions': [version(1, 'LOCAL'), version(2, 'CLOUD')],
            'edits': [], 'edit_revision': revision(0), 'alignments': [], 'proposals': [],
            'resolutions': [], 'decisions': [d],
            'selection': {'recording_id': uid(1), 'transcript_id': uid(11), 'selection_revision': 1,
                          'decision_id': uid(200), 'mode': 'AUTOMATIC'},
            'processing_history': [{'source': source(), 'processing_action_id': uid(41), 'generation': 1},
                                   {'source': source(), 'processing_action_id': uid(42), 'generation': 2}],
            'expected_processing': {'source': source(), 'processing_action_id': uid(42), 'generation': 2}}


def proposed(state='EXACT'):
    s = initial()
    s['edits'] = [edit()]
    s['edit_revision'] = revision(1)
    conflict = {'EXACT': 'NONE', 'MAPPED': 'NONE', 'AMBIGUOUS': 'AMBIGUOUS_MAPPING',
                'UNMAPPED': 'UNMAPPED_EDIT', 'CONFLICTED': 'MANUAL_REVIEW_REQUIRED'}[state]
    safe = state in ('EXACT', 'MAPPED')
    s['alignments'] = [{'alignment_id': uid(300), 'recording_id': uid(1), 'source': source(),
        'source_transcript_ids': [uid(11)], 'target_transcript_id': uid(12),
        'base_edit_revision': revision(1), 'method_id': 'synthetic-declared-evidence',
        'method_version': unknown(), 'mappings': [{'edit_id': uid(101),
            'target_anchor': anchor(12) if state != 'UNMAPPED' else None, 'state': state,
            'conflict': conflict, 'basis': 'UNIQUE_CONTEXT' if safe else 'INSUFFICIENT'}]}]
    s['proposals'] = [{'proposal_id': uid(400), 'recording_id': uid(1), 'source': source(),
        'base_transcript_id': uid(11), 'candidate_transcript_id': uid(12),
        'base_edit_revision': revision(1), 'base_selection_revision': 1,
        'alignment_id': uid(300), 'mapped_edit_ids': [uid(101)] if safe else [],
        'unmapped_edit_ids': [uid(101)] if state == 'UNMAPPED' else [],
        'conflicted_edit_ids': [uid(101)] if state in ('AMBIGUOUS', 'CONFLICTED') else [],
        'created_at': NOW, 'creation_state': 'READY' if safe else 'NEEDS_REVIEW'}]
    return s


def merged():
    s = proposed()
    v = version(3, 'MERGED')
    v['raw_provenance'] = None
    v['merge_provenance'] = {'proposal_id': uid(400), 'base_edit_revision': revision(1),
                           'applied_edit_ids': [uid(101)], 'resolution_ids': [],
                           'parent_transcript_ids': [uid(11), uid(12)]}
    v['text'] = 'beta'
    v['segments'][0]['text'] = 'beta'
    s['versions'].append(v)
    return s


def decision(s, kind='AUTO_CLOUD_REPLACE', target=12):
    return {'decision_id': uid(201), 'recording_id': uid(1),
            'from_transcript_id': s['selection']['transcript_id'],
            'target_transcript_id': uid(target) if target else None,
            'proposal_id': uid(400) if kind in ('ACCEPT_PROPOSAL', 'REJECT_PROPOSAL') else None,
            'base_edit_revision': copy.deepcopy(s['edit_revision']),
            'base_selection_revision': s['selection']['selection_revision'],
            'actor': 'SYSTEM' if kind.startswith('AUTO') else 'USER', 'created_at': NOW,
            'kind': kind, 'outcome': 'REJECTED' if kind == 'REJECT_PROPOSAL' else 'ACCEPTED',
            'reason': {'AUTO_CLOUD_REPLACE': 'EXPECTED_RESULT', 'AUTO_INITIAL': 'EXPECTED_RESULT',
                       'ACCEPT_PROPOSAL': 'USER_ACCEPTED', 'REJECT_PROPOSAL': 'USER_REJECTED',
                       'MANUAL_SELECT': 'USER_SELECTED_VERSION', 'CLEAR_SELECTION': 'USER_CLEARED'}[kind],
            'resolution_ids': []}


def applied(s, d):
    after = copy.deepcopy(s)
    after['decisions'].append(d)
    if d['outcome'] == 'ACCEPTED':
        after['selection'] = {'recording_id': uid(1), 'transcript_id': d['target_transcript_id'],
            'selection_revision': s['selection']['selection_revision'] + 1,
            'decision_id': d['decision_id'], 'mode': 'AUTOMATIC' if d['actor'] == 'SYSTEM' else 'MANUAL'}
    return after


class TranscriptContractTests(unittest.TestCase):
    def test_merged_cannot_depend_on_edit_based_on_itself_or_later_merge(self):
        for base in (13, 14):
            s = merged()
            if base == 14:
                later = copy.deepcopy(s['versions'][2])
                later.update(transcript_id=uid(14), version_order=4)
                later['segments'][0]['transcript_id'] = uid(14)
                s['versions'].append(later)
            s['edits'][0]['base_transcript_id'] = uid(base)
            s['edits'][0]['anchor'] = anchor(base)
            s['alignments'][0]['source_transcript_ids'] = [uid(base)]
            self.invalid(s)

    def test_cancel_cannot_reset_processing_generation(self):
        before = initial()
        newer = copy.deepcopy(before)
        expected = {'source': source(), 'processing_action_id': uid(43), 'generation': 3}
        newer['expected_processing'] = expected
        newer.setdefault('processing_history', []).append(expected)
        self.v.validate_transition(before, newer, self.c)
        cancelled = copy.deepcopy(newer)
        cancelled['expected_processing'] = None
        self.v.validate_transition(newer, cancelled, self.c)
        replay = copy.deepcopy(cancelled)
        replay['expected_processing'] = copy.deepcopy(before['expected_processing'])
        with self.assertRaises(ValueError): self.v.validate_transition(cancelled, replay, self.c)

    def test_historical_auto_overwrite_and_forged_actor_rejected(self):
        s = proposed()
        self.invalid(applied(s, decision(s)))
        s = initial()
        s['decisions'][0]['actor'] = 'USER'
        s['selection']['mode'] = 'MANUAL'
        self.invalid(s)

    def test_processing_history_is_immutable_and_new_generation_can_resume(self):
        s = initial()
        cancelled = copy.deepcopy(s)
        cancelled['expected_processing'] = None
        self.v.validate_transition(s, cancelled, self.c)
        resumed = copy.deepcopy(cancelled)
        h = {'source': source(), 'processing_action_id': uid(43), 'generation': 3}
        resumed['processing_history'].append(h)
        resumed['expected_processing'] = h
        self.v.validate_transition(cancelled, resumed, self.c)
        bad = copy.deepcopy(resumed)
        bad['processing_history'][1]['generation'] = 1
        with self.assertRaises(ValueError): self.v.validate_transition(resumed, bad, self.c)

    def test_explicit_rejection_cannot_be_reopened_in_imported_history(self):
        s = merged()
        rejected = applied(s, decision(s, 'REJECT_PROPOSAL'))
        d = decision(rejected, 'ACCEPT_PROPOSAL', 13)
        d['decision_id'] = uid(202)
        self.invalid(applied(rejected, d))

    def test_system_rejected_attempt_does_not_reject_user_proposal(self):
        s = merged()
        d = decision(s, 'REJECT_PROPOSAL')
        d.update(actor='SYSTEM', reason='STALE_OR_CONFLICT', base_selection_revision=0)
        with self.assertRaises(ValueError): self.v.validate_decision(d, s, self.c)
        d.update(kind='ACCEPT_PROPOSAL')
        after = applied(s, d)
        self.v.validate_transition(s, after, self.c)
        self.assertEqual('READY', self.v.proposal_state(after['proposals'][0], after))

    def test_new_proposal_cannot_claim_inactive_base(self):
        before = proposed()
        before['proposals'] = []
        after = copy.deepcopy(before)
        p = proposed()['proposals'][0]
        p.update(base_transcript_id=uid(12), candidate_transcript_id=uid(11))
        after['alignments'][0]['target_transcript_id'] = uid(11)
        after['alignments'][0]['mappings'][0]['target_anchor'] = anchor(11)
        before['alignments'] = copy.deepcopy(after['alignments'])
        after['proposals'] = [p]
        with self.assertRaises(ValueError): self.v.validate_transition(before, after, self.c)

    def test_explicit_resolution_allows_reviewed_merge(self):
        for kind in ('APPLY_AT_REVIEWED_ANCHOR', 'USE_CANDIDATE_TEXT'):
            s = proposed('UNMAPPED')
            r = {'resolution_id': uid(500), 'recording_id': uid(1), 'edit_id': uid(101),
                 'proposal_id': uid(400), 'target_transcript_id': uid(12),
                 'base_edit_revision': revision(1), 'base_selection_revision': 1,
                 'actor': 'USER', 'created_at': NOW, 'kind': kind,
                 'target_anchor': anchor(12) if kind == 'APPLY_AT_REVIEWED_ANCHOR' else None,
                 'reason': 'USER_REVIEWED_CORRECTION' if kind == 'APPLY_AT_REVIEWED_ANCHOR' else 'USER_CHOSE_CANDIDATE_TEXT'}
            s['resolutions'] = [r]
            v = merged()['versions'][2]
            v['merge_provenance']['resolution_ids'] = [uid(500)]
            if kind == 'USE_CANDIDATE_TEXT': v['merge_provenance']['applied_edit_ids'] = []
            s['versions'].append(v)
            d = decision(s, 'ACCEPT_PROPOSAL', 13)
            d['resolution_ids'] = [uid(500)]
            self.v.validate_transition(s, applied(s, d), self.c)

    def test_manual_older_raw_requires_explicit_per_edit_resolution(self):
        s = merged()
        d = decision(s, 'MANUAL_SELECT', 11)
        with self.assertRaises(ValueError): self.v.validate_decision(d, s, self.c)
        s['resolutions'] = [{'resolution_id': uid(501), 'recording_id': uid(1), 'edit_id': uid(101),
            'proposal_id': None, 'target_transcript_id': uid(11), 'base_edit_revision': revision(1),
            'base_selection_revision': 1, 'actor': 'USER', 'created_at': NOW,
            'kind': 'USE_CANDIDATE_TEXT', 'target_anchor': None, 'reason': 'USER_CHOSE_CANDIDATE_TEXT'}]
        d['resolution_ids'] = [uid(501)]
        self.v.validate_transition(s, applied(s, d), self.c)

    def setUp(self):
        p = ROOT / 'tools/validate_transcript_versioning_contract.py'
        self.assertTrue(p.exists(), 'Transcript contract validator is not implemented')
        spec = importlib.util.spec_from_file_location('transcript_validator', p)
        self.v = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.v)
        self.c = json.loads(CONTRACT.read_text(encoding='utf-8'))

    def valid(self, s):
        self.v.validate_snapshot(s, self.c)

    def invalid(self, s):
        with self.assertRaises(ValueError):
            self.valid(s)

    def test_catalog_and_neutral_snapshot(self):
        self.v.validate_contract(self.c)
        self.valid(initial())

    def test_schema_rejects_active_engine_physical_and_provider_fields(self):
        for field in ('isActive', 'edited_text', 'table_name', 'TranscriptionJobName', 'credentials'):
            c = copy.deepcopy(self.c)
            c['types']['TranscriptVersion'][field] = 'text'
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.v.validate_contract(c)
        c = copy.deepcopy(self.c)
        c['enums']['Engine'].append('ACTIVE')
        with self.assertRaises(ValueError):
            self.v.validate_contract(c)

    def test_schema_cannot_relax_non_execution(self):
        c = copy.deepcopy(self.c)
        c['non_execution']['7.2E'] = 'STARTED'
        with self.assertRaises(ValueError):
            self.v.validate_contract(c)

    def test_raw_local_and_cloud_are_immutable(self):
        for index in (0, 1):
            before = initial()
            after = copy.deepcopy(before)
            after['versions'][index]['text'] = 'changed'
            with self.subTest(engine=index), self.assertRaises(ValueError):
                self.v.validate_transition(before, after, self.c)

    def test_edit_append_preserves_raw_versions(self):
        before = initial()
        after = copy.deepcopy(before)
        after['edits'] = [edit()]
        after['edit_revision'] = revision(1)
        self.v.validate_transition(before, after, self.c)
        self.assertEqual(before['versions'], after['versions'])

    def test_merged_is_new_version_with_parents_and_edits_retained(self):
        s = merged()
        self.valid(s)
        self.v.validate_transition(proposed(), s, self.c)
        self.assertEqual(3, len(s['versions']))

    def test_duplicate_identity_and_order_rejected(self):
        for key in ('transcript_id', 'version_order'):
            s = initial()
            s['versions'][1][key] = s['versions'][0][key]
            self.invalid(s)

    def test_wrong_recording_source_and_segment_owner_rejected(self):
        for mode in ('recording', 'source', 'segment'):
            s = initial()
            if mode == 'recording': s['versions'][0]['recording_id'] = uid(99)
            if mode == 'source': s['versions'][0]['source']['sha256'] = '1' * 64
            if mode == 'segment': s['versions'][0]['segments'][0]['transcript_id'] = uid(12)
            self.invalid(s)

    def test_unavailable_partial_and_known_timestamps_supported(self):
        for availability, start, end in [('UNAVAILABLE', None, None), ('PARTIAL', 5, None), ('KNOWN', 5, 9)]:
            s = proposed()
            s['edits'][0]['anchor']['timestamp'].update(availability=availability, start_us=start, end_us=end)
            self.valid(s)

    def test_fabricated_unavailable_zero_and_invalid_bounds_rejected(self):
        for availability, start, end in [('UNAVAILABLE', 0, 0), ('KNOWN', 10, 2), ('KNOWN', 0, 1000001)]:
            s = proposed()
            s['edits'][0]['anchor']['timestamp'].update(availability=availability, start_us=start, end_us=end)
            self.invalid(s)

    def test_char_offset_only_anchor_rejected(self):
        s = proposed()
        a = s['edits'][0]['anchor']
        a['segment_ref'] = None
        a['context'] = {'before': '', 'target': '', 'after': '', 'boundary': 'NONE'}
        a['char_range'] = {'start': 0, 'end': 1}
        self.invalid(s)

    def test_anchor_segment_from_other_transcript_rejected(self):
        s = proposed()
        s['edits'][0]['anchor']['segment_ref']['transcript_id'] = uid(12)
        self.invalid(s)

    def test_cross_engine_same_segment_id_is_not_mapping_evidence(self):
        s = proposed()
        self.assertEqual(s['versions'][0]['segments'][0]['segment_id'], s['versions'][1]['segments'][0]['segment_id'])
        s['alignments'][0]['mappings'][0]['basis'] = 'INSUFFICIENT'
        self.invalid(s)

    def test_insert_replace_delete_and_empty_document_boundary(self):
        for kind in ('INSERT', 'REPLACE', 'DELETE'):
            s = initial()
            e = edit()
            e['kind'] = kind
            if kind == 'INSERT':
                e['anchor']['context']['target'] = ''
                e['anchor']['context']['boundary'] = 'START'
                e['anchor']['segment_ref'] = None
            if kind == 'DELETE': e['payload'] = ''
            s['edits'] = [e]
            s['edit_revision'] = revision(1)
            self.valid(s)

    def test_edit_requires_base_and_revision(self):
        for field in ('base_transcript_id', 'base_edit_revision'):
            s = proposed()
            del s['edits'][0][field]
            self.invalid(s)

    def test_edit_history_mutation_and_revision_gap_rejected(self):
        before = proposed()
        after = copy.deepcopy(before)
        after['edits'][0]['payload'] = 'changed'
        with self.assertRaises(ValueError): self.v.validate_transition(before, after, self.c)
        after = copy.deepcopy(before)
        after['edit_revision'] = revision(5)
        self.invalid(after)

    def test_safe_mapping_is_proposal_eligible_not_activation_permission(self):
        for state in ('EXACT', 'MAPPED'):
            s = proposed(state)
            self.valid(s)
            self.assertEqual('READY', self.v.proposal_state(s['proposals'][0], s))
            with self.assertRaises(ValueError): self.v.validate_decision(decision(s), s, self.c)

    def test_ambiguous_unmapped_and_conflicted_edits_are_retained(self):
        for state in ('AMBIGUOUS', 'UNMAPPED', 'CONFLICTED'):
            s = proposed(state)
            self.valid(s)
            self.assertEqual('NEEDS_REVIEW', self.v.proposal_state(s['proposals'][0], s))
            s['proposals'][0]['unmapped_edit_ids'] = []
            s['proposals'][0]['conflicted_edit_ids'] = []
            self.invalid(s)

    def test_mapping_wrong_source_rejected(self):
        s = proposed()
        s['alignments'][0]['mappings'][0]['target_anchor']['source']['audio_asset_id'] = uid(99)
        self.invalid(s)

    def test_proposal_binds_candidate_active_base_and_alignment(self):
        for field in ('base_transcript_id', 'candidate_transcript_id', 'alignment_id'):
            s = proposed()
            s['proposals'][0][field] = uid(99)
            self.invalid(s)

    def test_concurrent_edit_makes_proposal_stale(self):
        s = merged()
        d = decision(s, 'ACCEPT_PROPOSAL', 13)
        s['edits'].append(edit(2))
        s['edit_revision'] = revision(2)
        self.valid(s)
        self.assertEqual('STALE', self.v.proposal_state(s['proposals'][0], s))
        with self.assertRaises(ValueError): self.v.validate_decision(d, s, self.c)

    def test_edit_on_another_base_also_invalidates_proposal(self):
        s = proposed()
        s['edits'].append(edit(2, 12))
        s['edit_revision'] = revision(2)
        self.valid(s)
        self.assertEqual('STALE', self.v.proposal_state(s['proposals'][0], s))

    def test_accept_merge_preserves_history_and_moves_only_pointer(self):
        s = merged()
        d = decision(s, 'ACCEPT_PROPOSAL', 13)
        self.v.validate_decision(d, s, self.c)
        after = applied(s, d)
        self.v.validate_transition(s, after, self.c)
        self.assertEqual(s['versions'], after['versions'])
        self.assertEqual(s['edits'], after['edits'])

    def test_rejected_proposal_keeps_pointer_and_cannot_be_accepted_later(self):
        s = merged()
        d = decision(s, 'REJECT_PROPOSAL', 12)
        after = applied(s, d)
        self.v.validate_transition(s, after, self.c)
        self.assertEqual(s['selection'], after['selection'])
        self.assertEqual('REJECTED', self.v.proposal_state(after['proposals'][0], after))
        with self.assertRaises(ValueError): self.v.validate_decision(decision(after, 'ACCEPT_PROPOSAL', 13), after, self.c)

    def test_no_edit_cloud_replacement_requires_exact_action_generation(self):
        s = initial()
        self.v.validate_decision(decision(s), s, self.c)
        for field in ('generation', 'processing_action_id'):
            bad = copy.deepcopy(s)
            bad['expected_processing'][field] = 9 if field == 'generation' else uid(99)
            with self.assertRaises(ValueError): self.v.validate_decision(decision(bad), bad, self.c)

    def test_manual_older_choice_and_late_result_guard(self):
        s = initial()
        d = decision(s)
        current = applied(s, d)
        self.v.validate_transition(s, current, self.c)
        manual = decision(current, 'MANUAL_SELECT', 11)
        manual['decision_id'] = uid(202)
        after = applied(current, manual)
        self.v.validate_transition(current, after, self.c)
        late = decision(after)
        late['decision_id'] = uid(203)
        with self.assertRaises(ValueError): self.v.validate_decision(late, after, self.c)

    def test_stale_selection_revision_cannot_win_aba(self):
        s = initial()
        d = decision(s)
        d['base_selection_revision'] = 0
        with self.assertRaises(ValueError): self.v.validate_decision(d, s, self.c)

    def test_direct_pointer_mutation_without_decision_rejected(self):
        before = initial()
        after = copy.deepcopy(before)
        after['selection']['transcript_id'] = uid(12)
        with self.assertRaises(ValueError): self.v.validate_transition(before, after, self.c)

    def test_partial_failed_output_not_active_or_edit_base(self):
        for status in ('PARTIAL', 'FAILED'):
            s = initial()
            s['versions'][1]['processing_status'] = status
            self.valid(s)
            with self.assertRaises(ValueError): self.v.validate_decision(decision(s), s, self.c)
            s['edits'] = [edit(base=12)]
            s['edit_revision'] = revision(1)
            self.invalid(s)

    def test_missing_active_edit_base_or_merge_parent_rejected(self):
        for mode in ('active', 'edit', 'parent'):
            s = merged()
            if mode == 'active': s['selection']['transcript_id'] = uid(99)
            if mode == 'edit': s['edits'][0]['base_transcript_id'] = uid(99)
            if mode == 'parent': s['versions'][2]['merge_provenance']['parent_transcript_ids'][0] = uid(99)
            self.invalid(s)

    def test_hypothetical_version_removal_cannot_orphan_history(self):
        s = merged()
        s['versions'].pop(0)
        self.invalid(s)

    def test_source_unavailable_preserves_text_but_blocks_automatic_result(self):
        before = proposed()
        after = copy.deepcopy(before)
        after['sources'][0]['availability'] = 'USER_DELETED'
        self.v.validate_transition(before, after, self.c)
        self.assertEqual(before['versions'], after['versions'])
        s = initial()
        s['sources'][0]['availability'] = 'MISSING'
        self.valid(s)
        with self.assertRaises(ValueError): self.v.validate_decision(decision(s), s, self.c)
        self.v.validate_decision(decision(s, 'MANUAL_SELECT', 12), s, self.c)

    def test_tombstoned_recording_cannot_activate(self):
        s = initial()
        s['lifecycle'] = 'TOMBSTONED'
        with self.assertRaises(ValueError): self.v.validate_decision(decision(s), s, self.c)

    def test_explicit_clear_preserves_all_versions(self):
        s = initial()
        d = decision(s, 'CLEAR_SELECTION', None)
        self.v.validate_transition(s, applied(s, d), self.c)

    def test_stage1_utf8_hygiene_and_negative_markers(self):
        self.v.validate_stage1_hygiene(ROOT)
        for marker in ('\u0432\u0402', '\u0432\u2020', '\u0412\u00a7', '\ufffd'):
            with self.assertRaises(ValueError): self.v.validate_text_hygiene('heading ' + marker)


if __name__ == '__main__':
    unittest.main()
