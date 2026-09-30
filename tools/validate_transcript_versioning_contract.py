"""Offline logical contract checks, never storage, authorization or a merge algorithm."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

from validate_cloud_asr_provider_contract import require, validate_value

ROOT = Path(__file__).resolve().parents[1]
CONTRACT_PATH = 'docs/contracts/DORA_ALPHA_TRANSCRIPT_VERSIONING_EDIT_CONTRACT_V0_1.json'
# Canonical frozen structure excluding closure status; changes require version review.
CATALOG_SHA256 = '687f8767f56c4867faabff5a0138a65eb579e19843301d848041a63952d340b7'
SAFE = {'EXACT', 'MAPPED'}
COLLECTION_IDS = {'versions': 'transcript_id', 'edits': 'edit_id', 'alignments': 'alignment_id',
                  'proposals': 'proposal_id', 'resolutions': 'resolution_id', 'decisions': 'decision_id'}


def load_json(path):
    def unique_pairs(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, 'DUPLICATE_JSON_KEY')
            result[key] = value
        return result
    return json.loads(Path(path).read_text(encoding='utf-8'), object_pairs_hook=unique_pairs)


def validate_contract(contract):
    require(type(contract) is dict, 'CATALOG_TYPE')
    body = {k: v for k, v in contract.items() if k != 'status'}
    digest = hashlib.sha256(json.dumps(body, sort_keys=True, separators=(',', ':'), ensure_ascii=True).encode()).hexdigest()
    require(digest == CATALOG_SHA256, 'FROZEN_CATALOG_STRUCTURE')
    require(contract.get('status') in ('CONTRACT_DEFINED_AWAITING_EXACT_SHA_CI',
            'PASS / VERSIONED_TRANSCRIPT_AND_EDIT_CONTRACT_READY'), 'CATALOG_STATUS')


def index(items, field):
    result = {item[field]: item for item in items}
    require(len(result) == len(items), 'DUPLICATE_IDENTITY')
    return result


def unique(values):
    require(len(values) == len(set(values)), 'DUPLICATE_REFERENCE')
    return set(values)


def tables(snapshot):
    return {key: index(snapshot[key], field) for key, field in COLLECTION_IDS.items()}


def source_state(reference, snapshot):
    matches = [s for s in snapshot['sources'] if s['source'] == reference]
    require(len(matches) == 1 and reference['recording_id'] == snapshot['recording_id'], 'EXACT_SOURCE')
    return matches[0]


def check_revision(value, snapshot):
    require(value['recording_id'] == snapshot['recording_id']
            and value['ordinal'] <= snapshot['edit_revision']['ordinal'], 'REVISION_SCOPE')


def check_timestamp(stamp, reference, snapshot):
    duration = source_state(reference, snapshot)['duration_us']
    start, end = stamp['start_us'], stamp['end_us']
    for value in (start, end):
        require(value is None or 0 <= value <= duration, 'TIMESTAMP_RANGE')
    require(start is None or end is None or start <= end, 'TIMESTAMP_ORDER')


def valid_version(version_id, snapshot, t):
    require(version_id in t['versions'], 'MISSING_VERSION')
    version = t['versions'][version_id]
    require(version['processing_status'] == 'VALID', 'INCOMPLETE_VERSION')
    return version


def check_anchor(anchor, version_id, snapshot, t):
    version = valid_version(version_id, snapshot, t)
    require(anchor['base_transcript_id'] == version_id and anchor['source'] == version['source'], 'ANCHOR_SCOPE')
    segment = anchor['segment_ref']
    if segment is not None:
        require(segment['transcript_id'] == version_id
                and any(s['segment_id'] == segment['segment_id'] for s in version['segments']), 'ANCHOR_SEGMENT_OWNER')
    context = anchor['context']
    require(segment is not None or any(context[k] for k in ('before', 'target', 'after'))
            or context['boundary'] != 'NONE', 'OFFSET_ONLY_ANCHOR')
    if anchor['char_range'] is not None:
        require(anchor['char_range']['start'] <= anchor['char_range']['end'], 'CHAR_RANGE')
    check_timestamp(anchor['timestamp'], anchor['source'], snapshot)


def source_edits(snapshot, source, revision):
    return {e['edit_id']: e for e in snapshot['edits']
            if e['anchor']['source'] == source and e['revision']['ordinal'] <= revision['ordinal']}


def proposal_resolutions(proposal, snapshot):
    return {r['edit_id']: r for r in snapshot['resolutions']
            if r['proposal_id'] == proposal['proposal_id']
            and r['target_transcript_id'] == proposal['candidate_transcript_id']
            and r['base_edit_revision'] == proposal['base_edit_revision']
            and r['base_selection_revision'] == proposal['base_selection_revision']}


def proposal_state(proposal, snapshot):
    for d in snapshot['decisions']:
        if d['proposal_id'] == proposal['proposal_id']:
            if d['kind'] == 'ACCEPT_PROPOSAL' and d['outcome'] == 'ACCEPTED':
                return 'ACCEPTED'
            if (d['kind'] == 'REJECT_PROPOSAL' and d['outcome'] == 'REJECTED'
                    and d['actor'] == 'USER' and d['reason'] == 'USER_REJECTED'):
                return 'REJECTED'
    if (proposal['base_edit_revision'] != snapshot['edit_revision']
            or proposal['base_selection_revision'] != snapshot['selection']['selection_revision']
            or proposal['base_transcript_id'] != snapshot['selection']['transcript_id']
            or proposal['source'] != snapshot['current_source']):
        return 'STALE'
    unresolved = set(proposal['unmapped_edit_ids'] + proposal['conflicted_edit_ids']) - set(proposal_resolutions(proposal, snapshot))
    return 'NEEDS_REVIEW' if unresolved else 'READY'


def check_recorded_decision(d, snapshot, t, prior_mode):
    """Retained-history invariants, not proof of past source availability/atomicity."""
    kind = d['kind']
    if d['outcome'] == 'REJECTED':
        if kind == 'REJECT_PROPOSAL':
            require(d['actor'] == 'USER' and d['reason'] == 'USER_REJECTED'
                    and d['proposal_id'] in t['proposals'] and not d['resolution_ids'], 'USER_REJECTION')
            p = t['proposals'][d['proposal_id']]
            require(d['from_transcript_id'] == p['base_transcript_id']
                    and d['target_transcript_id'] == p['candidate_transcript_id']
                    and d['base_edit_revision'] == p['base_edit_revision']
                    and d['base_selection_revision'] == p['base_selection_revision'], 'REJECTION_SCOPE')
        else:
            require(d['reason'] == 'STALE_OR_CONFLICT', 'REJECTION_REASON')
        return
    if kind.startswith('AUTO'):
        require(d['actor'] == 'SYSTEM' and d['reason'] == 'EXPECTED_RESULT'
                and d['proposal_id'] is None and not d['resolution_ids'], 'AUTO_DECISION')
        require(d['base_edit_revision']['ordinal'] == 0 and prior_mode != 'MANUAL', 'USER_AUTHORITY')
        target = valid_version(d['target_transcript_id'], snapshot, t)
        require(target['engine'] in ('LOCAL', 'CLOUD'), 'AUTO_RAW_EXPECTED')
        raw = target['raw_provenance']
        require(any(h == {'source': target['source'], 'processing_action_id': raw['processing_action_id'],
                         'generation': raw['generation']} for h in snapshot['processing_history']), 'HISTORICAL_PROCESSING')
        if kind == 'AUTO_INITIAL':
            require(d['from_transcript_id'] is None, 'INITIAL_SELECTION')
        else:
            current = valid_version(d['from_transcript_id'], snapshot, t)
            require(current['engine'] == 'LOCAL' and target['engine'] == 'CLOUD'
                    and current['source'] == target['source']
                    and current['version_order'] < target['version_order'], 'AUTO_REPLACEMENT')
    elif kind == 'ACCEPT_PROPOSAL':
        require(d['actor'] == 'USER' and d['reason'] == 'USER_ACCEPTED'
                and d['proposal_id'] in t['proposals'], 'USER_ACCEPTANCE')
        p = t['proposals'][d['proposal_id']]
        require(d['from_transcript_id'] == p['base_transcript_id']
                and d['base_edit_revision'] == p['base_edit_revision']
                and d['base_selection_revision'] == p['base_selection_revision'], 'ACCEPTANCE_BASE')
        target = valid_version(d['target_transcript_id'], snapshot, t)
        require(target['engine'] == 'MERGED' and target['merge_provenance']['proposal_id'] == p['proposal_id'], 'PROPOSAL_TARGET')
        require(set(d['resolution_ids']) == set(target['merge_provenance']['resolution_ids']), 'ACCEPTANCE_RESOLUTIONS')
    elif kind == 'MANUAL_SELECT':
        require(d['actor'] == 'USER' and d['reason'] == 'USER_SELECTED_VERSION' and d['proposal_id'] is None, 'MANUAL_DECISION')
        target = valid_version(d['target_transcript_id'], snapshot, t)
        incorporated = set(target['merge_provenance']['applied_edit_ids']) if target['engine'] == 'MERGED' else set()
        captured = {e['edit_id'] for e in snapshot['edits'] if e['revision']['ordinal'] <= d['base_edit_revision']['ordinal']}
        resolved = set()
        for rid in d['resolution_ids']:
            r = t['resolutions'][rid]
            require(r['proposal_id'] is None and r['target_transcript_id'] == target['transcript_id']
                    and r['base_edit_revision'] == d['base_edit_revision']
                    and r['base_selection_revision'] == d['base_selection_revision']
                    and r['kind'] == 'USE_CANDIDATE_TEXT', 'MANUAL_EDIT_RESOLUTION')
            resolved.add(r['edit_id'])
        require(captured - incorporated == resolved, 'MANUAL_CORRECTION_LOSS')
    elif kind == 'CLEAR_SELECTION':
        require(d['actor'] == 'USER' and d['reason'] == 'USER_CLEARED'
                and d['target_transcript_id'] is None and d['proposal_id'] is None
                and not d['resolution_ids'], 'CLEAR_DECISION')
    else:
        raise ValueError('INVALID_ACCEPTED_DECISION')


def validate_snapshot(snapshot, contract):
    validate_contract(contract)
    validate_value('Snapshot', snapshot, contract)
    t = tables(snapshot)
    owner = snapshot['recording_id']
    require(snapshot['sources'], 'SOURCE_REQUIRED')
    unique([s['source']['audio_asset_id'] for s in snapshot['sources']])
    for s in snapshot['sources']:
        require(s['source']['recording_id'] == owner, 'SOURCE_OWNER')
    source_state(snapshot['current_source'], snapshot)
    for collection in COLLECTION_IDS:
        for item in snapshot[collection]:
            require(item['recording_id'] == owner, 'RECORDING_OWNER')
    unique([v['version_order'] for v in snapshot['versions']])
    valid_actions = []
    for v in snapshot['versions']:
        source_state(v['source'], snapshot)
        require([s['order'] for s in v['segments']] == list(range(len(v['segments']))), 'SEGMENT_ORDER')
        unique([s['segment_id'] for s in v['segments']])
        prior = {'start_us': -1, 'end_us': -1}
        for s in v['segments']:
            require(s['transcript_id'] == v['transcript_id'] and s['source'] == v['source'], 'SEGMENT_OWNER')
            check_timestamp(s['timestamp'], s['source'], snapshot)
            for bound in prior:
                value = s['timestamp'][bound]
                if value is not None:
                    require(value >= prior[bound], 'SEGMENT_TIME_ORDER')
                    prior[bound] = value
        if v['engine'] == 'MERGED':
            require(v['raw_provenance'] is None and v['merge_provenance'] is not None
                    and v['processing_status'] == 'VALID', 'MERGED_PROVENANCE')
        else:
            require(v['raw_provenance'] is not None and v['merge_provenance'] is None, 'RAW_PROVENANCE')
            if v['processing_status'] == 'VALID':
                valid_actions.append(v['raw_provenance']['processing_action_id'])
            if v['engine'] == 'LOCAL':
                for field in ('route_id', 'provider_id', 'provider_version', 'adapter_profile_id', 'adapter_profile_version'):
                    require(v['processing'][field]['availability'] == 'NOT_APPLICABLE', 'LOCAL_PROVIDER_COUPLING')
    unique(valid_actions)
    require(snapshot['edit_revision'] == {'recording_id': owner, 'ordinal': len(snapshot['edits'])}, 'EDIT_REVISION_HEAD')
    for number, e in enumerate(snapshot['edits'], 1):
        require(e['base_edit_revision'] == {'recording_id': owner, 'ordinal': number-1}
                and e['revision'] == {'recording_id': owner, 'ordinal': number}, 'EDIT_REVISION_CHAIN')
        check_anchor(e['anchor'], e['base_transcript_id'], snapshot, t)
        target = e['anchor']['context']['target']
        require((e['kind'] == 'INSERT' and not target and bool(e['payload']))
                or (e['kind'] == 'REPLACE' and bool(target) and bool(e['payload']))
                or (e['kind'] == 'DELETE' and bool(target) and not e['payload']), 'EDIT_PAYLOAD')
    for a in snapshot['alignments']:
        target = valid_version(a['target_transcript_id'], snapshot, t)
        require(a['source'] == target['source'], 'ALIGNMENT_SOURCE')
        check_revision(a['base_edit_revision'], snapshot)
        edits = source_edits(snapshot, a['source'], a['base_edit_revision'])
        require(unique(a['source_transcript_ids']) == {e['base_transcript_id'] for e in edits.values()}, 'ALIGNMENT_BASES')
        for version_id in a['source_transcript_ids']:
            require(valid_version(version_id, snapshot, t)['source'] == a['source'], 'ALIGNMENT_BASE_SOURCE')
        require(unique([m['edit_id'] for m in a['mappings']]) == set(edits), 'ALIGNMENT_EDIT_COVERAGE')
        for m in a['mappings']:
            if m['target_anchor'] is not None:
                check_anchor(m['target_anchor'], a['target_transcript_id'], snapshot, t)
            if m['state'] in SAFE:
                require(m['target_anchor'] is not None and m['conflict'] == 'NONE'
                        and m['basis'] in ('UNIQUE_CONTEXT', 'USER_CONFIRMED'), 'UNPROVEN_MAPPING')
            elif m['state'] == 'UNMAPPED':
                require(m['target_anchor'] is None and m['conflict'] == 'UNMAPPED_EDIT', 'UNMAPPED_STATE')
            elif m['state'] == 'AMBIGUOUS':
                require(m['conflict'] == 'AMBIGUOUS_MAPPING', 'AMBIGUOUS_STATE')
            else:
                require(m['conflict'] != 'NONE', 'CONFLICT_STATE')
    for p in snapshot['proposals']:
        base = valid_version(p['base_transcript_id'], snapshot, t)
        candidate = valid_version(p['candidate_transcript_id'], snapshot, t)
        require(p['source'] == base['source'] == candidate['source']
                and p['base_transcript_id'] != p['candidate_transcript_id'], 'PROPOSAL_SOURCES')
        check_revision(p['base_edit_revision'], snapshot)
        require(p['base_edit_revision']['ordinal'] > 0, 'PROPOSAL_EDITS_REQUIRED')
        require(p['alignment_id'] in t['alignments'], 'PROPOSAL_ALIGNMENT')
        a = t['alignments'][p['alignment_id']]
        require(a['target_transcript_id'] == p['candidate_transcript_id']
                and a['source'] == p['source'] and a['base_edit_revision'] == p['base_edit_revision'], 'PROPOSAL_ALIGNMENT_SCOPE')
        expected = {'mapped_edit_ids': [], 'unmapped_edit_ids': [], 'conflicted_edit_ids': []}
        for m in a['mappings']:
            bucket = 'mapped_edit_ids' if m['state'] in SAFE else 'unmapped_edit_ids' if m['state'] == 'UNMAPPED' else 'conflicted_edit_ids'
            expected[bucket].append(m['edit_id'])
        for key, values in expected.items():
            require(unique(p[key]) == set(values), 'PROPOSAL_EDIT_PARTITIONS')
        unsafe = bool(p['unmapped_edit_ids'] or p['conflicted_edit_ids'])
        require(p['creation_state'] == ('NEEDS_REVIEW' if unsafe else 'READY'), 'PROPOSAL_CREATION_STATE')
        require(p['base_selection_revision'] <= snapshot['selection']['selection_revision'], 'FUTURE_SELECTION_BASE')
    resolution_keys = []
    for r in snapshot['resolutions']:
        require(r['edit_id'] in t['edits'], 'RESOLUTION_EDIT')
        e = t['edits'][r['edit_id']]
        target = valid_version(r['target_transcript_id'], snapshot, t)
        require(target['source'] == e['anchor']['source'], 'RESOLUTION_SOURCE')
        check_revision(r['base_edit_revision'], snapshot)
        require(e['revision']['ordinal'] <= r['base_edit_revision']['ordinal'], 'RESOLUTION_REVISION')
        if r['proposal_id'] is not None:
            require(r['proposal_id'] in t['proposals'], 'RESOLUTION_PROPOSAL')
            p = t['proposals'][r['proposal_id']]
            require(r['target_transcript_id'] == p['candidate_transcript_id']
                    and r['base_edit_revision'] == p['base_edit_revision']
                    and r['base_selection_revision'] == p['base_selection_revision'], 'RESOLUTION_PROPOSAL_SCOPE')
        if r['kind'] == 'APPLY_AT_REVIEWED_ANCHOR':
            require(r['target_anchor'] is not None and r['reason'] == 'USER_REVIEWED_CORRECTION', 'RESOLUTION_ANCHOR')
            check_anchor(r['target_anchor'], r['target_transcript_id'], snapshot, t)
        else:
            require(r['target_anchor'] is None and r['reason'] == 'USER_CHOSE_CANDIDATE_TEXT', 'RESOLUTION_EXCLUSION')
        resolution_keys.append((r['edit_id'], r['proposal_id'], r['target_transcript_id'],
                                r['base_edit_revision']['ordinal'], r['base_selection_revision']))
    unique(resolution_keys)
    for v in snapshot['versions']:
        if v['engine'] != 'MERGED':
            continue
        m = v['merge_provenance']
        require(m['proposal_id'] in t['proposals'], 'MERGE_PROPOSAL')
        p = t['proposals'][m['proposal_id']]
        require(m['base_edit_revision'] == p['base_edit_revision'] and v['source'] == p['source'], 'MERGE_SCOPE')
        require(unique(m['parent_transcript_ids']) == {p['base_transcript_id'], p['candidate_transcript_id']}, 'MERGE_PARENTS')
        for parent in m['parent_transcript_ids']:
            require(valid_version(parent, snapshot, t)['version_order'] < v['version_order'], 'MERGE_PARENT_ORDER')
        # Include every edit input, even explicitly excluded corrections. Alignment
        # and resolution targets are the candidate already checked as a parent.
        for e in source_edits(snapshot, p['source'], p['base_edit_revision']).values():
            require(valid_version(e['base_transcript_id'], snapshot, t)['version_order'] < v['version_order'], 'MERGE_EDIT_BASE_ORDER')
        resolutions = proposal_resolutions(p, snapshot)
        require(unique(m['resolution_ids']) == {r['resolution_id'] for r in resolutions.values()}, 'MERGE_RESOLUTIONS')
        unsafe = set(p['unmapped_edit_ids'] + p['conflicted_edit_ids'])
        require(unsafe <= set(resolutions), 'UNRESOLVED_MERGE')
        applied = set(p['mapped_edit_ids'])
        for eid, r in resolutions.items():
            if r['kind'] == 'APPLY_AT_REVIEWED_ANCHOR': applied.add(eid)
            else: applied.discard(eid)
        require(unique(m['applied_edit_ids']) == applied, 'MERGE_EDIT_COVERAGE')
    selection = snapshot['selection']
    require(selection['recording_id'] == owner, 'SELECTION_OWNER')
    pointer, revision_number, decision_id, mode = None, 0, None, 'NONE'
    closed_proposals = set()
    for d in snapshot['decisions']:
        check_revision(d['base_edit_revision'], snapshot)
        for field in ('from_transcript_id', 'target_transcript_id'):
            if d[field] is not None: valid_version(d[field], snapshot, t)
        if d['proposal_id'] is not None: require(d['proposal_id'] in t['proposals'], 'DECISION_PROPOSAL')
        require(unique(d['resolution_ids']) <= set(t['resolutions']), 'DECISION_RESOLUTION')
        check_recorded_decision(d, snapshot, t, mode)
        terminal = ((d['kind'] == 'ACCEPT_PROPOSAL' and d['outcome'] == 'ACCEPTED')
                    or (d['kind'] == 'REJECT_PROPOSAL' and d['reason'] == 'USER_REJECTED'))
        if terminal:
            require(d['proposal_id'] not in closed_proposals, 'PROPOSAL_ALREADY_CLOSED')
            closed_proposals.add(d['proposal_id'])
        if d['outcome'] == 'ACCEPTED':
            require(d['base_selection_revision'] == revision_number and d['from_transcript_id'] == pointer, 'DECISION_CHAIN')
            pointer, revision_number, decision_id = d['target_transcript_id'], revision_number + 1, d['decision_id']
            mode = 'AUTOMATIC' if d['actor'] == 'SYSTEM' else 'MANUAL'
    require(selection == {'recording_id': owner, 'transcript_id': pointer,
            'selection_revision': revision_number, 'decision_id': decision_id, 'mode': mode}, 'SELECTION_PROJECTION')
    history = snapshot['processing_history']
    unique([h['processing_action_id'] for h in history])
    require(all(a['generation'] < b['generation'] for a, b in zip(history, history[1:])), 'PROCESSING_GENERATION_ORDER')
    for h in history: source_state(h['source'], snapshot)
    if snapshot['expected_processing'] is not None:
        require(history and snapshot['expected_processing'] == history[-1], 'EXPECTED_HISTORY_HEAD')


def validate_decision(decision, snapshot, contract):
    validate_snapshot(snapshot, contract)
    validate_value('ActivationDecision', decision, contract)
    t = tables(snapshot)
    d, selection = decision, snapshot['selection']
    require(d['decision_id'] not in t['decisions'] and d['recording_id'] == snapshot['recording_id'], 'DECISION_IDENTITY')
    require(unique(d['resolution_ids']) <= set(t['resolutions']), 'DECISION_RESOLUTION')
    if d['proposal_id'] is not None: require(d['proposal_id'] in t['proposals'], 'DECISION_PROPOSAL')
    if d['target_transcript_id'] is not None: valid_version(d['target_transcript_id'], snapshot, t)
    check_revision(d['base_edit_revision'], snapshot)
    check_recorded_decision(d, snapshot, t, selection['mode'])
    if d['outcome'] == 'REJECTED':
        return
    require(snapshot['lifecycle'] == 'LIVE', 'TOMBSTONED_RECORDING')
    require(d['base_edit_revision'] == snapshot['edit_revision']
            and d['base_selection_revision'] == selection['selection_revision']
            and d['from_transcript_id'] == selection['transcript_id'], 'STALE_ACTIVATION')
    if d['kind'].startswith('AUTO'):
        target = valid_version(d['target_transcript_id'], snapshot, t)
        expected = snapshot['expected_processing']
        require(expected is not None, 'AUTO_RAW_EXPECTED')
        require(target['source'] == expected['source'] == snapshot['current_source']
                and source_state(target['source'], snapshot)['availability'] == 'AVAILABLE', 'AUTO_SOURCE')
        raw = target['raw_provenance']
        require(raw['processing_action_id'] == expected['processing_action_id']
                and raw['generation'] == expected['generation'], 'STALE_PROCESSING_GENERATION')
    elif d['kind'] == 'ACCEPT_PROPOSAL':
        require(proposal_state(t['proposals'][d['proposal_id']], snapshot) == 'READY', 'PROPOSAL_NOT_READY')


def validate_transition(before, after, contract):
    validate_snapshot(before, contract)
    validate_snapshot(after, contract)
    require(before['recording_id'] == after['recording_id']
            and before['current_source'] == after['current_source'], 'CONTEXT_CHANGE')
    for collection in COLLECTION_IDS:
        require(after[collection][:len(before[collection])] == before[collection], 'IMMUTABLE_HISTORY')
    for old_source in before['sources']:
        current = source_state(old_source['source'], after)
        require(current['duration_us'] == old_source['duration_us'], 'SOURCE_IDENTITY_MUTATION')
    if before['lifecycle'] == 'TOMBSTONED':
        require(after['lifecycle'] == 'TOMBSTONED', 'RECORDING_RESURRECTION')
        for key in ('versions', 'edits', 'alignments', 'proposals', 'resolutions'):
            require(before[key] == after[key], 'TOMBSTONE_NEW_CONTENT')
    old_history, new_history = before['processing_history'], after['processing_history']
    require(new_history[:len(old_history)] == old_history, 'IMMUTABLE_PROCESSING_HISTORY')
    old_expected, new_expected = before['expected_processing'], after['expected_processing']
    if new_expected is not None and old_expected != new_expected:
        require(len(new_history) > len(old_history), 'PROCESSING_RESCHEDULE_REQUIRED')
    if before['lifecycle'] == 'TOMBSTONED':
        require(new_history == old_history and new_expected is None, 'TOMBSTONE_PROCESSING')
    for p in after['proposals'][len(before['proposals']):]:
        require(p['base_transcript_id'] == before['selection']['transcript_id']
                and p['base_selection_revision'] == before['selection']['selection_revision']
                and p['base_edit_revision'] == after['edit_revision']
                and p['source'] == before['current_source'], 'PROPOSAL_CREATION_BASE')
    decisions = after['decisions'][len(before['decisions']):]
    require(len(decisions) <= 1, 'ONE_SELECTION_DECISION_PER_CHECK')
    if decisions:
        require(before['edits'] == after['edits'] and before['expected_processing'] == after['expected_processing']
                and before['lifecycle'] == after['lifecycle'] and before['sources'] == after['sources'], 'DECISION_CONTEXT_RACE')
        validate_decision(decisions[0], before, contract)
    if not decisions or decisions[0]['outcome'] == 'REJECTED':
        require(before['selection'] == after['selection'], 'UNAUTHORIZED_POINTER_CHANGE')


def validate_text_hygiene(text):
    require(not any(marker in text for marker in ('\u0432\u0402', '\u0432\u2020', '\u0412\u00a7', '\ufffd')), 'STAGE1_MOJIBAKE')


def validate_stage1_hygiene(root=ROOT):
    paths = list((root / 'docs/stage1').glob('*CONTRACT*.md'))
    require(paths, 'STAGE1_CONTRACTS_MISSING')
    for path in paths:
        validate_text_hygiene(path.read_text(encoding='utf-8'))


def main():
    validate_contract(load_json(ROOT / CONTRACT_PATH))
    validate_stage1_hygiene()
    print('PASS transcript logical catalogue and Stage1 UTF-8 hygiene')
    print('PERSISTENCE / MERGE RUNTIME / AWS / AUDIO: NOT_IMPLEMENTED_OR_NOT_RUN')


if __name__ == '__main__':
    main()
