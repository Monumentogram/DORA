"""Private, offline Android acquisition packages. Never contacts a device or network.

All package files/identifiers/paths are private. Only aggregate receipts may be public.
Run this while the recorder is stopped; every operation holds its same OS lock.
"""
from __future__ import annotations

import argparse
import copy
import io
import json
from pathlib import Path, PurePosixPath
import re
import secrets
import stat
import zipfile

from tools.cloud62d_owned.corpus import (
    ATTESTATION_TEXT, CONVERSION_RECIPE as BROWSER_CONVERSION_RECIPE,
    NATIVE_CONVERSION_RECIPE, REDUCED_IDS, REDUCED_PROTOCOL, CorpusStore,
    digest, encoded, normalize, require, utc_now, validate_wav,
)
from tools.cloud62d_owned.server import _ProcessLock

APP = 'DORA_OWNED8_ANDROID_V1'
MAX_PACKAGE_BYTES = 64 * 1024 * 1024
MAX_UNCOMPRESSED_BYTES = 48 * 1024 * 1024
MAX_AUDIO_BYTES = 1_920_044
MAX_JSON_BYTES = 1024 * 1024


def _json(raw):
    def pairs(items):
        result = {}
        for key, value in items:
            require(key not in result, 'DUPLICATE_JSON_FIELD')
            result[key] = value
        return result

    def invalid_constant(value):
        raise ValueError('INVALID_JSON_NUMBER')

    try:
        return json.loads(raw, object_pairs_hook=pairs, parse_constant=invalid_constant)
    except (UnicodeError, json.JSONDecodeError):
        raise ValueError('INVALID_PACKAGE_JSON') from None


def _fingerprints(state):
    return {case: digest(encoded(state['records'].get(case))) for case in REDUCED_IDS}


def _ready_state(store, *, pending=False):
    state = store._load(allow_pending_mobile_import=pending)
    require(store._reduced(state), 'REDUCED_EIGHT_PROTOCOL_REQUIRED')
    store._mutable(state)
    require(state['attestation']['confirmed'] is True, 'ATTESTATION_REQUIRED')
    return state


def _seed_path(seed_id):
    require(type(seed_id) is str and re.fullmatch(r'[0-9a-f]{32}', seed_id), 'INVALID_SEED_ID')
    return 'mobile/seeds/' + seed_id


def _load_seed(store, seed_id):
    base = _seed_path(seed_id)
    require(store._path(base + '/seed.json').exists(), 'UNKNOWN_SEED_ID')
    raw = store._path(base + '/seed.json').read_bytes()
    binding = _json(store._path(base + '/binding.json').read_bytes())
    require(digest(raw) == binding['seed_sha256'], 'PRIVATE_SEED_HASH_MISMATCH')
    seed = _json(raw)
    require(seed['seed_id'] == seed_id and seed['protocol_version'] == REDUCED_PROTOCOL,
            'PRIVATE_SEED_BINDING_MISMATCH')
    return base, seed, binding


def create_seed(store):
    """Create a private immutable seed ZIP; recording readiness is never changed."""
    lock = _ProcessLock(store.root)
    try:
        state = _ready_state(store)
        seed_id = secrets.token_hex(16)
        base = _seed_path(seed_id)
        items, audio_files = [], {}
        for item in store._items():
            row = copy.deepcopy(item)
            row.update(recording=None, reference=None)
            record = state['records'].get(item['id'])
            if record:
                audio_path = f'audio/{item["id"]}.wav'
                data = store.get_audio(item['id']).read_bytes()
                validate_wav(data)
                audio_files[audio_path] = data
                row['recording'] = {'audio_path': audio_path,
                    'audio_sha256': record['recording']['uploaded_wav_sha256'],
                    'duration_us': record['recording']['duration_us']}
                if record['reference']:
                    store._audit_record(item['id'], record)
                    row['reference'] = {'text': record['reference']['text'], 'HUMAN_VERIFIED_REFERENCE': True}
            items.append(row)
        seed = {'schema_version': '1.0', 'app': APP, 'seed_id': seed_id,
                'protocol_version': REDUCED_PROTOCOL, 'inventory_sha256': state['inventory_sha256'],
                'protocol_overlay_sha256': state['protocol_overlay_sha256'],
                'recording_enabled': False,
                'attestation': {**state['attestation'], 'text': ATTESTATION_TEXT}, 'items': items}
        raw = encoded(seed)
        store._write(base + '/seed.json', raw, immutable=True)
        binding = {'seed_sha256': digest(raw), 'inventory_sha256': state['inventory_sha256'],
                   'protocol_overlay_sha256': state['protocol_overlay_sha256']}
        store._write(base + '/binding.json', binding, immutable=True)
        store._write(base + '/sync.json', {'fingerprints': _fingerprints(state), 'packages': [],
                                         'last_export_revision': 0})
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w', compression=zipfile.ZIP_STORED) as archive:
            archive.writestr('seed.json', raw)
            for name, data in audio_files.items():
                archive.writestr(name, data)
        store._write(base + '/seed.zip', buffer.getvalue(), immutable=True)
        return {'seed_id': seed_id, 'seed_sha256': digest(raw), 'zip_path': str(store._path(base + '/seed.zip')),
                'recording_enabled': False, 'items': len(items)}
    finally:
        lock.close()


def activate_seed(store, seed_id):
    """Export an activation only after host owner readiness was explicitly recorded."""
    lock = _ProcessLock(store.root)
    try:
        state = _ready_state(store)
        require(state['recording_enabled'] is True, 'RECORDING_DEFERRED')
        base, seed, binding = _load_seed(store, seed_id)
        require(seed['inventory_sha256'] == state['inventory_sha256'] and
                seed['protocol_overlay_sha256'] == state['protocol_overlay_sha256'], 'SEED_BINDING_MISMATCH')
        activation = {'schema_version': '1.0', 'app': APP, 'seed_id': seed_id,
                      'seed_sha256': binding['seed_sha256'], 'inventory_sha256': state['inventory_sha256'],
                      'protocol_overlay_sha256': state['protocol_overlay_sha256'], 'recording_enabled': True}
        store._write(base + '/activation.json', activation, immutable=True)
        return {'activation_path': str(store._path(base + '/activation.json')), 'seed_id': seed_id}
    finally:
        lock.close()


def _read_archive(raw):
    require(len(raw) <= MAX_PACKAGE_BYTES, 'PACKAGE_TOO_LARGE')
    try:
        with zipfile.ZipFile(io.BytesIO(raw)) as archive:
            infos = archive.infolist()
            require(1 <= len(infos) <= 48, 'ZIP_MEMBER_COUNT')
            require(sum(i.file_size for i in infos) <= MAX_UNCOMPRESSED_BYTES, 'ZIP_EXPANDED_SIZE_LIMIT')
            names = set()
            files = {}
            for info in infos:
                name = info.filename
                parts = PurePosixPath(name).parts
                require(type(name) is str and name and '\\' not in name and ':' not in name
                        and not name.startswith('/') and all(p not in ('.', '..') for p in parts)
                        and str(PurePosixPath(name)) == name and not info.is_dir(), 'UNSAFE_ZIP_PATH')
                require(name.casefold() not in names, 'DUPLICATE_ZIP_MEMBER')
                names.add(name.casefold())
                require(not info.flag_bits & 1 and
                        stat.S_IFMT(info.external_attr >> 16) != stat.S_IFLNK, 'UNSAFE_ZIP_MEMBER')
                require(info.compress_type in (zipfile.ZIP_STORED, zipfile.ZIP_DEFLATED), 'UNSUPPORTED_ZIP_COMPRESSION')
                maximum = MAX_JSON_BYTES if name in ('export.json', 'audit.json') else MAX_AUDIO_BYTES
                require(0 <= info.file_size <= maximum, 'ZIP_MEMBER_SIZE_LIMIT')
                require(info.file_size <= 1024 or info.file_size <= max(1, info.compress_size) * 300,
                        'ZIP_COMPRESSION_RATIO_LIMIT')
                with archive.open(info) as stream:
                    value = stream.read(maximum + 1)
                    require(len(value) == info.file_size and len(value) <= maximum and not stream.read(1),
                            'ZIP_MEMBER_SIZE_MISMATCH')
                files[name] = value
        require('export.json' in files, 'EXPORT_MANIFEST_MISSING')
        return files
    except (zipfile.BadZipFile, RuntimeError, EOFError, NotImplementedError):
        raise ValueError('INVALID_EXPORT_ZIP') from None


def _validate_export(files):
    manifest = _json(files['export.json'])
    keys = {'schema_version', 'app', 'seed_id', 'seed_sha256', 'inventory_sha256',
            'protocol_overlay_sha256', 'export_revision', 'records'}
    require(type(manifest) is dict and keys <= set(manifest) <= keys | {'rejected', 'audit_path', 'audit_sha256'},
            'EXPORT_MANIFEST_SHAPE')
    require(manifest['schema_version'] == '1.0' and manifest['app'] == APP, 'EXPORT_VERSION_MISMATCH')
    require(type(manifest['export_revision']) is int and 1 <= manifest['export_revision'] <= 2**63 - 1,
            'INVALID_EXPORT_REVISION')
    require(type(manifest['records']) is list and len(manifest['records']) <= 8, 'EXPORT_RECORD_COUNT')
    seen = set()
    expected = {'export.json'}
    if 'audit_path' in manifest or 'audit_sha256' in manifest:
        require(manifest.get('audit_path') == 'audit.json' and 'audit.json' in files and
                digest(files['audit.json']) == manifest.get('audit_sha256'), 'AUDIT_BINDING_MISMATCH')
        require(type(_json(files['audit.json'])) is dict, 'AUDIT_JSON_SHAPE')
        expected.add('audit.json')
    for row in manifest['records']:
        required = {'id', 'audio_path', 'audio_sha256', 'reference_text', 'human_verified_reference'}
        require(type(row) is dict and required <= set(row) <= required | {'reference_revisions'}, 'EXPORT_RECORD_SHAPE')
        case = row['id']
        require(case in REDUCED_IDS and case not in seen, 'UNKNOWN_OR_DUPLICATE_CASE')
        seen.add(case)
        require(row['audio_path'] == f'audio/{case}.wav' and row['audio_path'] in files, 'AUDIO_BINDING_MISMATCH')
        expected.add(row['audio_path'])
        require(digest(files[row['audio_path']]) == row['audio_sha256'], 'AUDIO_HASH_MISMATCH')
        require(type(row['human_verified_reference']) is bool, 'REFERENCE_CONFIRMATION_TYPE')
        require(row['reference_text'] is None or
                (type(row['reference_text']) is str and len(row['reference_text']) <= 20000), 'INVALID_REFERENCE_TEXT')
        if row['human_verified_reference']:
            require(type(row['reference_text']) is str and bool(normalize(row['reference_text']).split()),
                    'EMPTY_OR_OVERSIZED_REFERENCE')
        revisions = row.get('reference_revisions', [])
        require(type(revisions) is list and len(revisions) <= 100, 'REFERENCE_REVISION_LIMIT')
        for revision in revisions:
            require(type(revision) is dict and set(revision) == {'text', 'human_verified_reference'}
                    and type(revision['text']) is str and len(revision['text']) <= 20000
                    and type(revision['human_verified_reference']) is bool, 'REFERENCE_REVISION_SHAPE')
    rejected = manifest.get('rejected', [])
    require(type(rejected) is list and len(rejected) <= 32, 'REJECTED_ATTEMPT_LIMIT')
    attempt_ids = set()
    for row in rejected:
        require(type(row) is dict and set(row) == {'attempt_id', 'id', 'audio_path', 'audio_sha256', 'reason'},
                'REJECTED_ATTEMPT_SHAPE')
        require(type(row['attempt_id']) is str and re.fullmatch(r'[0-9a-f]{32}', row['attempt_id'])
                and row['attempt_id'] not in attempt_ids and row['id'] in REDUCED_IDS, 'REJECTED_ATTEMPT_ID')
        attempt_ids.add(row['attempt_id'])
        require(row['audio_path'] == f'rejected/{row["attempt_id"]}.wav' and row['audio_path'] in files,
                'REJECTED_ATTEMPT_PATH')
        require(type(row['reason']) is str and re.fullmatch(r'[A-Z][A-Z0-9_]{0,80}', row['reason']),
                'REJECTED_ATTEMPT_REASON')
        require(digest(files[row['audio_path']]) == row['audio_sha256'], 'REJECTED_ATTEMPT_HASH')
        expected.add(row['audio_path'])
    require(set(files) == expected, 'UNDECLARED_ZIP_MEMBER')
    return manifest


def _plan_import(store, state, manifest, files, package_sha):
    after = copy.deepcopy(state)
    writes = {}
    captured = references = 0
    excluded = {e['case_id'] for e in state['exclusions']}
    for row in manifest['records']:
        case = row['id']
        require(case not in excluded, 'CASE_EXCLUDED')
        data = files[row['audio_path']]
        audio = validate_wav(data)
        require(all(other == case or record['recording']['uploaded_wav_sha256'] != audio['sha256']
                    for other, record in after['records'].items() if other in REDUCED_IDS), 'DUPLICATE_SOURCE')
        maximum = 45_000_000 if '-read-' in case else 60_000_000
        require(20_000_000 <= audio['duration_us'] <= maximum, 'RECORDING_DURATION_OUT_OF_RANGE')
        old = after['records'].get(case)
        if old:
            require(old['recording']['uploaded_wav_sha256'] == audio['sha256'], 'IMMUTABLE_AUDIO_CONFLICT')
            for filename, field in ((f'source/{case}.wav', 'source_sha256'), (f'audio/{case}.wav', 'uploaded_wav_sha256')):
                require(digest(store._path(filename).read_bytes()) == old['recording'][field], 'AUDIO_HASH_MISMATCH')
        else:
            require(state['recording_enabled'] is True, 'RECORDING_DEFERRED')
            captured += 1
            for filename in (f'source/{case}.wav', f'audio/{case}.wav'):
                if store._path(filename).exists():
                    require(store._path(filename).read_bytes() == data, 'IMMUTABLE_AUDIO_CONFLICT')
                writes[filename] = row['audio_path']
            after['records'][case] = {'recording': {'source_sha256': audio['sha256'],
                'uploaded_wav_sha256': audio['sha256'], 'source_rate_hz': 16000,
                'duration_us': audio['duration_us'], 'frames': audio['frames'],
                'source_path': f'source/{case}.wav', 'upload_path': f'audio/{case}.wav',
                'conversion_recipe_sha256': digest(encoded(NATIVE_CONVERSION_RECIPE)),
                'acquisition_condition': 'CLEAN'}, 'reference': None, 'reference_revisions': [],
                'timing': None, 'timing_revisions': [], 'timing_draft': None}
        record = after['records'][case]
        if not row['human_verified_reference']:
            if record['reference']:
                record['reference_revisions'].append(record['reference'])
                record['reference'] = None
            record['reference_draft'] = {'text': row['reference_text'], 'authoritative': False,
                                         'source': 'ANDROID_UNVERIFIED_DRAFT', 'updated_at': utc_now()}
            if record['timing']:
                record['timing_revisions'].append(record['timing'])
                record['timing'] = None
            record['timing_draft'] = None
        if row['human_verified_reference'] and (record['reference'] is None or
                                               record['reference']['text'] != row['reference_text']):
            references += 1
            if record['reference']:
                record['reference_revisions'].append(record['reference'])
            text = row['reference_text']
            normalized = normalize(text)
            words = normalized.split()
            require(len(words) <= 4096, 'EMPTY_OR_OVERSIZED_REFERENCE')
            record['reference'] = {'text': text, 'words': words, 'HUMAN_VERIFIED_REFERENCE': True,
                'raw_reference_sha256': digest(text.encode()), 'normalized_reference_sha256': digest(normalized.encode()),
                'verified_at': utc_now(), 'source': 'HUMAN_LISTENED_AND_VERIFIED_ON_ANDROID'}
            if record['timing']:
                record['timing_revisions'].append(record['timing'])
                record['timing'] = None
            record['timing_draft'] = None
        if row['human_verified_reference']:
            record.pop('reference_draft', None)
    after['events'].append({'event': 'MOBILE_PACKAGE_IMPORTED', 'case_id': None,
                            'package_sha256': package_sha, 'at': utc_now()})
    return after, writes, {'status': 'IMPORTED', 'package_sha256': package_sha,
                          'imported_recordings': captured, 'verified_references': references,
                          'rejected_attempts_preserved': len(manifest.get('rejected', []))}


def _complete_transaction(store, base, transaction, files):
    current_raw = store._path('state.json').read_bytes()
    plan_raw = encoded(transaction['after_state'])
    require(digest(plan_raw) == transaction['after_state_sha256'], 'IMPORT_PLAN_HASH_MISMATCH')
    current_sha = digest(current_raw)
    require(current_sha in (transaction['before_state_sha256'], transaction['after_state_sha256']),
            'IMPORT_INTERRUPTED_STATE_CONFLICT')
    for target, member in transaction['writes'].items():
        require(re.fullmatch(r'(source|audio)/(ru|en)-(read|spontaneous)-0[12]\.wav', target), 'IMPORT_PLAN_PATH')
        require(member in files and target.split('/')[-1] == member.split('/')[-1], 'IMPORT_PLAN_PATH')
        store._write(target, files[member], immutable=True)
    if current_sha == transaction['before_state_sha256']:
        store._write('state.json', plan_raw)
    store._write(transaction['sync_path'], transaction['sync'])
    store._write(base + '/receipt.json', transaction['receipt'], immutable=True)
    store._path('mobile-import-pending.json').unlink()
    return transaction['receipt']


def import_export(store, package):
    """Preserve then validate an export, atomically import accepted records/revisions."""
    lock = _ProcessLock(store.root)
    base = None
    try:
        path = Path(package)
        require(path.is_file() and path.stat().st_size <= MAX_PACKAGE_BYTES, 'PACKAGE_TOO_LARGE_OR_MISSING')
        raw = path.read_bytes()
        package_sha = digest(raw)
        base = 'mobile/incoming/' + package_sha
        store._write(base + '/package.zip', raw, immutable=True)
        files = _read_archive(raw)
        manifest = _validate_export(files)
        seed_base, seed, binding = _load_seed(store, manifest['seed_id'])
        require(all(manifest[k] == binding[k] for k in ('seed_sha256', 'inventory_sha256', 'protocol_overlay_sha256')),
                'SEED_BINDING_MISMATCH')
        pending_exists = store._path('mobile-import-pending.json').exists()
        if pending_exists:
            pending = _json(store._path('mobile-import-pending.json').read_bytes())
            require(pending['package_sha256'] == package_sha, 'MOBILE_IMPORT_INCOMPLETE_RETRY_SAME_PACKAGE')
            store._load(allow_pending_mobile_import=True)
            if store._path(base + '/transaction.json').exists():
                transaction = _json(store._path(base + '/transaction.json').read_bytes())
                require(transaction['sync_path'] == seed_base + '/sync.json', 'IMPORT_PLAN_PATH')
                return _complete_transaction(store, base, transaction, files)
        if store._path(base + '/receipt.json').exists():
            result = _json(store._path(base + '/receipt.json').read_bytes())
            return {**result, 'status': 'ALREADY_IMPORTED'}
        state = _ready_state(store, pending=pending_exists)
        require(seed['inventory_sha256'] == state['inventory_sha256'] and
                seed['protocol_overlay_sha256'] == state['protocol_overlay_sha256'], 'SEED_BINDING_MISMATCH')
        sync = _json(store._path(seed_base + '/sync.json').read_bytes())
        require(sync['fingerprints'] == _fingerprints(state), 'STALE_HOST_STATE')
        require(manifest['export_revision'] > sync.get('last_export_revision', 0), 'STALE_MOBILE_EXPORT_REVISION')
        if any(row['id'] not in state['records'] for row in manifest['records']):
            require(state['recording_enabled'] is True, 'RECORDING_DEFERRED')
            require(store._path(seed_base + '/activation.json').exists(), 'MOBILE_ACTIVATION_REQUIRED')
            activation = _json(store._path(seed_base + '/activation.json').read_bytes())
            require(activation == {'schema_version': '1.0', 'app': APP, 'seed_id': manifest['seed_id'],
                    **binding, 'recording_enabled': True}, 'MOBILE_ACTIVATION_BINDING_MISMATCH')
        after, writes, receipt = _plan_import(store, state, manifest, files, package_sha)
        after_raw = encoded(after)
        transaction = {'before_state_sha256': digest(store._path('state.json').read_bytes()),
                       'after_state_sha256': digest(after_raw), 'after_state': after, 'writes': writes,
                       'sync_path': seed_base + '/sync.json',
                       'sync': {'fingerprints': _fingerprints(after), 'packages': sync['packages'] + [package_sha],
                                'last_export_revision': manifest['export_revision']},
                       'receipt': receipt}
        if not pending_exists:
            store._write('mobile-import-pending.json', {'package_sha256': package_sha})
        # Atomic replace publishes a complete plan; the pending marker fences other writers.
        require(not store._path(base + '/transaction.json').exists(), 'IMPORT_TRANSACTION_ALREADY_EXISTS')
        store._write(base + '/transaction.json', transaction)
        return _complete_transaction(store, base, transaction, files)
    except ValueError as error:
        if base and not store._path('mobile-import-pending.json').exists():
            code = str(error)
            store._write(base + '/rejection.json', {'status': 'REJECTED',
                'error': code if re.fullmatch(r'[A-Z][A-Z0-9_]{0,100}', code) else 'VALIDATION_FAILED'})
        raise
    finally:
        lock.close()


def main():
    parser = argparse.ArgumentParser(description='Offline private Android corpus package bridge; no device/network access.')
    parser.add_argument('action', choices=['seed', 'activate', 'import'])
    parser.add_argument('--root', required=True)
    parser.add_argument('--repo', required=True)
    parser.add_argument('--seed-id')
    parser.add_argument('--package')
    args = parser.parse_args()
    try:
        store = CorpusStore(args.root, args.repo)
        if args.action == 'seed':
            result = create_seed(store)
        elif args.action == 'activate':
            result = activate_seed(store, args.seed_id)
        else:
            require(args.package is not None, 'PACKAGE_REQUIRED')
            result = import_export(store, args.package)
        print(json.dumps(result, sort_keys=True))
        return 0
    except (ValueError, RuntimeError) as error:
        code = str(error)
        print(json.dumps({'error': code if re.fullmatch(r'[A-Z][A-Z0-9_]{0,100}', code) else 'VALIDATION_FAILED'}))
        return 2
    except OSError:
        print(json.dumps({'error': 'PRIVATE_STORAGE_UNAVAILABLE'}))
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
