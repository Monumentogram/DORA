"""Offline private corpus acquisition. No network, ASR, or generated timing truth.

Every returned state/manifest is PRIVATE. Only public_summary() is publication safe.
The store is single-writer (the recorder serializes requests); finalization freezes it.
"""
from __future__ import annotations

from datetime import datetime, timezone
import hashlib
import io
import json
import os
from pathlib import Path
import re
import secrets
import struct
import wave

from tools.alpha_asr_eval_text_contract import normalize, check_environment

RELEASE = 'dora-owned-corpus-v1.0.0'
REDUCED_PROTOCOL = 'dora-owned-reduced8-v2'
REDUCED_IDS = tuple(f'{lang}-{kind}-{n:02}' for lang in ('ru', 'en')
                    for kind in ('read', 'spontaneous') for n in (1, 2))
EASY_EN_PROTOCOL = 'dora-owned-easy-en8-v3'
EASY_EN_IDS = ('ru-read-01', 'ru-read-02', 'ru-spontaneous-01', 'ru-spontaneous-02',
               'en-read-01', 'en-read-02', 'en-read-03', 'en-read-04')
ATTESTATION_VERSION = 'dora-owned-corpus-attestation-v1'
ATTESTATION_TEXT = ('I am the speaker of these recordings and authorize DORA to process this '
                    'corpus with Amazon Transcribe in eu-central-1 solely for the bounded '
                    '6.2D evaluation under the current DORA Alpha privacy/retention policy.')
EXCLUSION_REASONS = ('UNPROVEN_RIGHTS', 'OTHER_LANGUAGE_OR_MIXED', 'MISSING_HUMAN_REFERENCE',
                     'CORRUPTED_OR_UNDECODABLE_AUDIO', 'INVALID_FORMAT_OR_DURATION',
                     'DUPLICATE_SOURCE', 'EMPTY_NORMALIZED_REFERENCE', 'REFERENCE_HASH_MISMATCH')
CONDITIONS = ('CLEAN', 'MODERATE_ROOM_NOISE', 'DISTANT_MICROPHONE', 'MILD_REVERBERATION',
              'OWN_VOICE_SPEAKERPHONE_RECAPTURE')
CONVERSION_RECIPE = {'version': 'browser-offline-audio-context-v1',
                     'source': 'browser AudioContext mono PCM16 WAV at native sample rate',
                     'conversion': 'OfflineAudioContext one channel at 16000 Hz; PCM16 LE WAV',
                     'duration_tolerance_us': 1000, 'gain_normalization': False,
                     'manual_trim': False, 'denoising': False}
NATIVE_CONVERSION_RECIPE = {'version': 'android-audiorecord-pcm16-v1',
                            'source': 'Android AudioRecord mono PCM_SIGNED_16_LE 16000 Hz WAV',
                            'conversion': 'NONE_SOURCE_EQUALS_EVALUATION_WAV',
                            'gain_normalization': False, 'manual_trim': False, 'denoising': False}


def require(condition, code):
    if not condition:
        raise ValueError(code)


def digest(value):
    return hashlib.sha256(value).hexdigest()


def encoded(value):
    return (json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(',', ':'),
                       allow_nan=False) + '\n').encode('utf-8')


def utc_now():
    return datetime.now(timezone.utc).isoformat()


def validate_wav(data, *, evaluation=True):
    """Strict bounded RIFF PCM16 parser, including actual data length (wave alone accepts truncation)."""
    require(isinstance(data, bytes) and 44 <= len(data) <= 120_000_044, 'INVALID_WAV_SIZE')
    require(data[:4] == b'RIFF' and data[8:12] == b'WAVE', 'INVALID_WAV_CONTAINER')
    require(struct.unpack_from('<I', data, 4)[0] + 8 == len(data), 'TRUNCATED_OR_TRAILING_WAV')
    chunks = {}
    offset = 12
    while offset + 8 <= len(data):
        name, size = struct.unpack_from('<4sI', data, offset)
        offset += 8
        require(offset + size <= len(data), 'TRUNCATED_WAV_CHUNK')
        require(name not in chunks, 'DUPLICATE_WAV_CHUNK')
        chunks[name] = data[offset:offset + size]
        offset += size + (size % 2)
    require(offset == len(data) and b'fmt ' in chunks and b'data' in chunks, 'INVALID_WAV_STRUCTURE')
    require(len(chunks[b'fmt ']) == 16, 'UNSUPPORTED_WAV_FORMAT')
    codec, channels, rate, byte_rate, block_align, bits = struct.unpack('<HHIIHH', chunks[b'fmt '])
    require(codec == 1 and channels == 1 and bits == 16 and block_align == 2
            and byte_rate == rate * 2 and 8000 <= rate <= 192000, 'UNSUPPORTED_WAV_FORMAT')
    require(not evaluation or rate == 16000, 'EVALUATION_WAV_REQUIRES_16000_HZ')
    pcm = chunks[b'data']
    require(len(pcm) % 2 == 0 and len(pcm) > 0, 'INVALID_PCM_LENGTH')
    frames = len(pcm) // 2
    return {'sha256': digest(data), 'frames': frames, 'sample_rate_hz': rate,
            'duration_us': frames * 1_000_000 // rate, 'bytes': len(data)}


def selection_key(source_sha256):
    return digest((RELEASE + '\n' + source_sha256).encode('utf-8'))


def validate_timings(words, reference_words, duration_us):
    require(type(words) is list and len(words) == len(reference_words), 'TIMING_WORDS_INCOMPLETE')
    previous_start = previous_end = -1
    for item, reference in zip(words, reference_words):
        require(type(item) is dict and set(item) == {'text', 'start_us', 'end_us'}, 'TIMING_SHAPE')
        require(item['text'] == reference and len(normalize(item['text']).split()) == 1,
                'TIMING_REFERENCE_MISMATCH')
        start, end = item['start_us'], item['end_us']
        require(type(start) is int and type(end) is int and 0 <= start <= end <= duration_us,
                'TIMING_OUT_OF_RANGE')
        require(start >= previous_start and end >= previous_end, 'TIMING_NON_MONOTONIC')
        previous_start, previous_end = start, end


class CorpusStore:
    def __init__(self, root, repo_root):
        check_environment()
        self.repo = Path(repo_root).resolve()
        self.root = Path(root).resolve()
        require(self.root != self.repo and self.repo not in self.root.parents, 'PRIVATE_ROOT_INSIDE_GIT')
        require(not any((p / '.git').exists() for p in (self.root, *self.root.parents)),
                'PRIVATE_ROOT_INSIDE_GIT')

    def _path(self, relative):
        path = self.root / relative
        require(path.resolve().is_relative_to(self.root), 'PRIVATE_PATH_ESCAPE')
        require(not path.is_symlink(), 'PRIVATE_PATH_SYMLINK')
        return path

    def _write(self, relative, value, *, immutable=False):
        path = self._path(relative)
        path.parent.mkdir(parents=True, exist_ok=True)
        data = value if isinstance(value, bytes) else encoded(value)
        if immutable:
            if path.exists():
                require(path.read_bytes() == data, 'IMMUTABLE_ARTIFACT_MISMATCH')
            else:
                temporary = self._path(relative + '.part-' + secrets.token_hex(8))
                with temporary.open('xb') as stream:
                    stream.write(data)
                    stream.flush()
                    os.fsync(stream.fileno())
                try:
                    # Same-directory hard-link publication is atomic and never replaces
                    # an existing destination. Interrupted staging bytes remain private.
                    os.link(temporary, path)
                except FileExistsError:
                    require(path.read_bytes() == data, 'IMMUTABLE_ARTIFACT_MISMATCH')
                temporary.unlink()
        else:
            temporary = self._path(relative + '.tmp')
            with temporary.open('wb') as stream:
                stream.write(data)
                stream.flush()
                os.fsync(stream.fileno())
            os.replace(temporary, path)

    def initialize(self, inventory=None):
        if self._path('state.json').exists():
            if inventory is not None:
                require(digest(encoded(inventory)) == self._load()['inventory_sha256'],
                        'INVENTORY_ALREADY_FROZEN')
            return self.state()
        if inventory is None:
            require(self._path('inventory.json').exists(), 'PRIVATE_INVENTORY_MISSING')
            inventory = json.loads(self._path('inventory.json').read_text(encoding='utf-8'))
        self._validate_inventory(inventory)
        self.root.mkdir(parents=True, exist_ok=True)
        self._write('inventory.json', inventory, immutable=True)
        self._write('inventory.sha256', (digest(encoded(inventory)) + '\n').encode(), immutable=True)
        state = {'schema_version': '1.0', 'source_release': RELEASE, 'status': 'ACQUIRING',
                 'inventory_sha256': digest(encoded(inventory)), 'created_at': utc_now(),
                 'material_sha256': {i['id']: digest(i['material'].encode()) for i in inventory['items']},
                 'attestation': {'version': ATTESTATION_VERSION, 'sha256': digest(ATTESTATION_TEXT.encode()),
                                 'confirmed': False}, 'records': {}, 'exclusions': [],
                 'selection': None, 'events': [], 'manifest_sha256': None}
        self._write('state.json', state)
        return self.state()

    @staticmethod
    def _validate_inventory(inventory):
        require(type(inventory) is dict and set(inventory) == {'schema_version', 'source_release', 'items'}
                and inventory['schema_version'] == '1.0' and inventory['source_release'] == RELEASE,
                'INVENTORY_SCHEMA')
        items = inventory['items']
        require(type(items) is list and len(items) == 72, 'INVENTORY_COUNT')
        expected = {f'{lang}-{kind.lower()}-{n:02}' for lang in ('ru', 'en')
                    for kind, count in (('READ', 15), ('SPONTANEOUS', 15), ('NOISY', 6))
                    for n in range(1, count + 1)}
        require({i.get('id') for i in items} == expected, 'INVENTORY_IDS')
        for item in items:
            require(set(item) == {'id', 'language', 'speech_class', 'material', 'condition'},
                    'INVENTORY_FIELDS')
            require(item['id'].startswith(item['language'] + '-' + item['speech_class'].lower() + '-'),
                    'INVENTORY_IDENTITY')
            require(type(item['material']) is str and 1 <= len(item['material']) <= 10000,
                    'INVENTORY_MATERIAL')
            require(item['condition'] in CONDITIONS and
                    (item['condition'] == 'CLEAN') == (item['speech_class'] != 'NOISY'),
                    'INVENTORY_CONDITION')
            if item['speech_class'] == 'READ':
                require(45 <= len(normalize(item['material']).split()) <= 115, 'READ_WORD_COUNT')

    def _load(self, *, allow_pending_migration=False, allow_pending_mobile_import=False):
        require(allow_pending_mobile_import or not self._path('mobile-import-pending.json').exists(),
                'MOBILE_IMPORT_INCOMPLETE_RETRY_SAME_PACKAGE')
        require(self._path('state.json').exists(), 'PRIVATE_INVENTORY_MISSING')
        state = json.loads(self._path('state.json').read_text(encoding='utf-8'))
        data = self._path('inventory.json').read_bytes()
        require(digest(data) == state['inventory_sha256'] == self._path('inventory.sha256').read_text().strip(),
                'INVENTORY_HASH_MISMATCH')
        self._validate_inventory(json.loads(data))
        if not self._reduced(state) and not allow_pending_migration:
            require(not self._path('archive/v1-before-reduced8').exists() and
                    not self._path('protocol-overlay-v2.json').exists(), 'MIGRATION_INCOMPLETE_RETRY_MIGRATION')
        if self._reduced(state):
            raw = self._path('protocol-overlay-v2.json').read_bytes()
            if state['protocol_version'] == EASY_EN_PROTOCOL:
                active_raw = self._path('protocol-overlay-v3.json').read_bytes()
                active = json.loads(active_raw)
                require(digest(active_raw) == state['protocol_overlay_sha256'] and
                        digest(raw) == active['source_v2_overlay_sha256'], 'PROTOCOL_OVERLAY_HASH_MISMATCH')
                self._validate_easy_materials(active['material_overrides'])
                require(active['protocol_version'] == EASY_EN_PROTOCOL and
                        active['active_case_ids'] == list(EASY_EN_IDS) and
                        active['original_inventory_sha256'] == state['inventory_sha256'] and
                        active['material_sha256'] == {k: digest(v.encode()) for k, v in active['material_overrides'].items()},
                        'PROTOCOL_OVERLAY_MISMATCH')
                for filename, expected in active['legacy_snapshots'].items():
                    require(digest(self._path('archive/v2-before-easy-en/' + filename).read_bytes()) == expected,
                            'LEGACY_SNAPSHOT_HASH_MISMATCH')
            else:
                require(digest(raw) == state['protocol_overlay_sha256'], 'PROTOCOL_OVERLAY_HASH_MISMATCH')
                if not allow_pending_migration:
                    require(not self._path('archive/v2-before-easy-en').exists() and
                            not self._path('protocol-overlay-v3.json').exists(), 'MIGRATION_INCOMPLETE_RETRY_MIGRATION')
            overlay = json.loads(raw)
            require(overlay['active_case_ids'] == list(REDUCED_IDS) and
                    overlay['original_inventory_sha256'] == state['inventory_sha256'] and
                    overlay['protocol_version'] == REDUCED_PROTOCOL, 'PROTOCOL_OVERLAY_MISMATCH')
            for filename, expected in overlay['legacy_snapshots'].items():
                require(digest(self._path('archive/v1-before-reduced8/' + filename).read_bytes()) == expected,
                        'LEGACY_SNAPSHOT_HASH_MISMATCH')
        return state

    def _items(self):
        items = json.loads(self._path('inventory.json').read_text(encoding='utf-8'))['items']
        state = self._load()
        if self._reduced(state):
            by_id = {i['id']: i for i in items}
            if state['protocol_version'] == EASY_EN_PROTOCOL:
                overrides = json.loads(self._path('protocol-overlay-v3.json').read_bytes())['material_overrides']
                for case_id, material in overrides.items():
                    by_id[case_id] = {**by_id[case_id], 'material': material}
            return [by_id[case_id] for case_id in self._active_ids(state)]
        return items

    @staticmethod
    def _reduced(state):
        return state.get('protocol_version') in (REDUCED_PROTOCOL, EASY_EN_PROTOCOL)

    @staticmethod
    def _active_ids(state):
        return EASY_EN_IDS if state.get('protocol_version') == EASY_EN_PROTOCOL else REDUCED_IDS

    def _artifact(self, state, name):
        if state.get('protocol_version') == EASY_EN_PROTOCOL:
            return 'easy-en8-v3/' + name
        return 'reduced8-v2/' + name if self._reduced(state) else name

    def _active_records(self, state):
        if self._reduced(state):
            return {case_id: state['records'][case_id] for case_id in self._active_ids(state) if case_id in state['records']}
        return state['records']

    def migrate_reduced_eight(self):
        """Explicit owner-scoped additive migration. Never called by recorder startup."""
        from tools.cloud62d_owned.server import _ProcessLock
        lock = _ProcessLock(self.root)
        try:
            return self._migrate_reduced_eight_locked()
        finally:
            lock.close()

    def _migrate_reduced_eight_locked(self):
        state = self._load(allow_pending_migration=True)
        if self._reduced(state):
            return self.state()
        require(state['status'] != 'FINALIZED' and not state.get('finalization_started_at')
                and not self._path('manifest.json').exists(), 'LEGACY_FINALIZATION_MUST_REMAIN_IMMUTABLE')
        require(not self._path('provider-started.json').exists(), 'PROVIDER_STARTED_CORPUS_FROZEN')
        snapshots = {}
        for filename in ('inventory.json', 'inventory.sha256', 'state.json', 'selection.json'):
            if self._path(filename).exists():
                raw = self._path(filename).read_bytes()
                self._write('archive/v1-before-reduced8/' + filename, raw, immutable=True)
                snapshots[filename] = digest(raw)
        overlay = {'schema_version': '2.0', 'protocol_version': REDUCED_PROTOCOL,
                   'authority': 'EXPLICIT_OWNER_REDUCTION_TO_EIGHT_AND_RECORDING_DEFERRAL',
                   'source_release': RELEASE, 'original_inventory_sha256': state['inventory_sha256'],
                   'active_case_ids': list(REDUCED_IDS), 'legacy_snapshots': snapshots,
                   'selection': 'ALL_EIGHT_FIXED_CASES_NO_RESERVES_NO_REPLACEMENTS',
                   'timing_status': 'NOT_EVALUATED', 'noise_status': 'NOT_EVALUATED'}
        self._write('protocol-overlay-v2.json', overlay, immutable=True)
        state.update(protocol_version=REDUCED_PROTOCOL, protocol_overlay_sha256=digest(encoded(overlay)),
                     recording_enabled=False, selection=None, status='ACQUIRING', manifest_sha256=None,
                     archived_case_ids=[i['id'] for i in json.loads(self._path('inventory.json').read_bytes())['items']
                                        if i['id'] not in REDUCED_IDS])
        self._save(state, 'OWNER_REDUCED_EIGHT_RECORDING_DEFERRED')
        return self.state()

    @staticmethod
    def _validate_easy_materials(materials):
        require(type(materials) is dict and set(materials) == set(EASY_EN_IDS[4:]), 'EASY_ENGLISH_MATERIAL_IDS')
        require(all(type(v) is str and len(v) <= 10000 and 45 <= len(normalize(v).split()) <= 115
                    for v in materials.values()), 'READ_WORD_COUNT')

    def migrate_easy_english(self, materials, *, confirmed):
        """Explicit prospective amendment; original inventory and both predecessor archives survive."""
        from tools.cloud62d_owned.server import _ProcessLock
        require(confirmed is True, 'EXPLICIT_OWNER_SCOPE_CONFIRMATION_REQUIRED')
        self._validate_easy_materials(materials)
        lock = _ProcessLock(self.root)
        try:
            state = self._load(allow_pending_migration=True)
            if state.get('protocol_version') == EASY_EN_PROTOCOL:
                overlay = json.loads(self._path('protocol-overlay-v3.json').read_bytes())
                require(overlay['material_overrides'] == materials, 'EASY_ENGLISH_MATERIALS_ALREADY_FROZEN')
                return self.state()
            require(state.get('protocol_version') == REDUCED_PROTOCOL, 'EASY_ENGLISH_REQUIRES_V2_PREDECESSOR')
            self._mutable(state)
            require(not self._path('reduced8-v2/manifest.json').exists(), 'LEGACY_FINALIZATION_MUST_REMAIN_IMMUTABLE')
            original_items = json.loads(self._path('inventory.json').read_bytes())['items']
            original_material_hashes = {i['id']: digest(i['material'].encode()) for i in original_items}
            require('material_sha256' not in state or state['material_sha256'] == original_material_hashes,
                    'MATERIAL_HASH_MISMATCH')
            english = lambda value: type(value) is str and value.startswith('en-')
            require(not any(english(k) for k in state['records']) and
                    not any(english(e.get('case_id')) for e in state['exclusions'] + state['events']),
                    'ENGLISH_ACQUISITION_HISTORY_EXISTS')
            require(not any(p.name.startswith('en-') for area in ('source', 'audio', 'rejected')
                            for p in self._path(area).rglob('*') if p.is_file()), 'ENGLISH_ACQUISITION_HISTORY_EXISTS')
            # A retained phone export may contain attempts not yet admitted into canonical state.
            import zipfile
            for package in self._path('mobile').rglob('*.zip'):
                try:
                    with zipfile.ZipFile(package) as archive:
                        if 'export.json' not in archive.namelist():
                            continue
                        require(archive.getinfo('export.json').file_size <= 1_000_000, 'ENGLISH_ACQUISITION_HISTORY_UNRESOLVED')
                        export = json.loads(archive.read('export.json'))
                        require(not any(english(row.get('id')) for row in export.get('records', []) + export.get('rejected', [])),
                                'ENGLISH_ACQUISITION_HISTORY_EXISTS')
                except (zipfile.BadZipFile, KeyError, TypeError, json.JSONDecodeError):
                    raise ValueError('ENGLISH_ACQUISITION_HISTORY_UNRESOLVED') from None
            snapshots = {}
            for filename, source in (('state.json', 'state.json'), ('protocol-overlay-v2.json', 'protocol-overlay-v2.json'),
                                     ('selection.json', 'reduced8-v2/selection.json')):
                if self._path(source).exists():
                    raw = self._path(source).read_bytes()
                    self._write('archive/v2-before-easy-en/' + filename, raw, immutable=True)
                    snapshots[filename] = digest(raw)
            overlay = {'schema_version': '3.0', 'protocol_version': EASY_EN_PROTOCOL,
                       'authority': 'EXPLICIT_OWNER_FOUR_SIMPLE_ENGLISH_READ_CASES', 'source_release': RELEASE,
                       'original_inventory_sha256': state['inventory_sha256'],
                       'source_v2_overlay_sha256': state['protocol_overlay_sha256'],
                       'active_case_ids': list(EASY_EN_IDS), 'material_overrides': materials,
                       'material_sha256': {k: digest(v.encode()) for k, v in materials.items()},
                       'legacy_snapshots': snapshots, 'selection': 'ALL_EIGHT_FIXED_CASES_NO_RESERVES_NO_REPLACEMENTS',
                       'timing_status': 'NOT_EVALUATED', 'noise_status': 'NOT_EVALUATED',
                       'en_spontaneous_status': 'NOT_EVALUATED'}
            self._write('protocol-overlay-v3.json', overlay, immutable=True)
            state.update(protocol_version=EASY_EN_PROTOCOL, protocol_overlay_sha256=digest(encoded(overlay)),
                         selection=None, status='ACQUIRING', manifest_sha256=None,
                         archived_case_ids=[i['id'] for i in json.loads(self._path('inventory.json').read_bytes())['items']
                                            if i['id'] not in EASY_EN_IDS])
            state['material_sha256'] = {**original_material_hashes, **overlay['material_sha256']}
            self._save(state, 'OWNER_FOUR_SIMPLE_ENGLISH_READ_CASES_FROZEN')
            return self.state()
        finally:
            lock.close()

    def resume_recording(self, *, confirmed):
        """Local operator only, after the owner's explicit readiness instruction."""
        from tools.cloud62d_owned.server import _ProcessLock
        lock = _ProcessLock(self.root)
        try:
            return self._resume_recording_locked(confirmed=confirmed)
        finally:
            lock.close()

    def _resume_recording_locked(self, *, confirmed):
        state = self._load()
        self._mutable(state)
        require(self._reduced(state) and confirmed is True, 'EXPLICIT_OWNER_READINESS_REQUIRED')
        if not state['recording_enabled']:
            state['recording_enabled'] = True
            self._save(state, 'OWNER_EXPLICITLY_READY_TO_RECORD')
        return self.state()

    def _item(self, case_id):
        require(type(case_id) is str and re.fullmatch(r'(ru|en)-(read|spontaneous|noisy)-\d{2}', case_id),
                'INVALID_CASE_ID')
        item = next((i for i in self._items() if i['id'] == case_id), None)
        require(item is not None, 'UNKNOWN_CASE_ID')
        return item

    def _mutable(self, state, *, timings=False, finalizing=False):
        require(not self._path('provider-started.json').exists(), 'PROVIDER_STARTED_CORPUS_FROZEN')
        require(state['status'] != 'FINALIZED', 'MANIFEST_FROZEN')
        require(finalizing or not state.get('finalization_started_at'), 'FINALIZATION_IN_PROGRESS_RETRY_FINALIZE')
        require(timings or state['selection'] is None, 'SELECTION_FROZEN')

    def _save(self, state, event, case_id=None):
        state['events'].append({'event': event, 'case_id': case_id, 'at': utc_now()})
        self._write('state.json', state)

    def attest(self, *, confirmed):
        state = self._load()
        self._mutable(state)
        require(confirmed is True, 'ATTESTATION_REQUIRED')
        state['attestation']['confirmed'] = True
        self._save(state, 'LOCAL_SPEAKER_ATTESTATION_CONFIRMED')
        return self.state()

    def save_capture(self, case_id, source_wav, evaluation_wav):
        state = self._load()
        self._mutable(state)
        require(not self._reduced(state) or state['recording_enabled'] is True, 'RECORDING_DEFERRED')
        require(state['attestation']['confirmed'] is True, 'ATTESTATION_REQUIRED')
        item = self._item(case_id)
        require(case_id not in state['records'], 'CAPTURE_ALREADY_EXISTS')
        require(not any(e['case_id'] == case_id for e in state['exclusions']), 'CASE_EXCLUDED')
        require(isinstance(source_wav, bytes) and isinstance(evaluation_wav, bytes)
                and max(len(source_wav), len(evaluation_wav)) <= 120_000_044, 'INVALID_WAV_SIZE')
        try:
            source = validate_wav(source_wav, evaluation=False)
            final = validate_wav(evaluation_wav)
            require(abs(source['duration_us'] - final['duration_us']) <= 1000, 'CONVERSION_DURATION_MISMATCH')
            maximum = 45_000_000 if item['speech_class'] == 'READ' else 60_000_000
            require(20_000_000 <= final['duration_us'] <= maximum, 'RECORDING_DURATION_OUT_OF_RANGE')
        except ValueError as error:
            self._write(f'rejected/{case_id}-source.wav', source_wav, immutable=True)
            self._write(f'rejected/{case_id}-final.wav', evaluation_wav, immutable=True)
            state['exclusions'].append({'case_id': case_id, 'reason': 'INVALID_FORMAT_OR_DURATION',
                'validation_code': str(error), 'stage': 'PRE_PROVIDER', 'at': utc_now(),
                'source_sha256': digest(source_wav), 'uploaded_wav_sha256': digest(evaluation_wav)})
            self._save(state, 'INVALID_CAPTURE_PRESERVED_AND_EXCLUDED', case_id)
            raise
        self._write(f'source/{case_id}.wav', source_wav, immutable=True)
        self._write(f'audio/{case_id}.wav', evaluation_wav, immutable=True)
        state['records'][case_id] = {'recording': {'source_sha256': source['sha256'],
            'uploaded_wav_sha256': final['sha256'], 'source_rate_hz': source['sample_rate_hz'],
            'duration_us': final['duration_us'], 'frames': final['frames'],
            'source_path': f'source/{case_id}.wav', 'upload_path': f'audio/{case_id}.wav',
            'conversion_recipe_sha256': digest(encoded(CONVERSION_RECIPE)),
            'acquisition_condition': item['condition']},
            'reference': None, 'reference_revisions': [], 'timing': None, 'timing_revisions': [],
            'timing_draft': None}
        self._save(state, 'CAPTURE_FINALIZED', case_id)
        return self.state()

    def verify_reference(self, case_id, text, *, confirmed):
        state = self._load()
        self._mutable(state)
        self._item(case_id)
        require(confirmed is True, 'HUMAN_VERIFICATION_REQUIRED')
        require(case_id in state['records'], 'RECORDING_REQUIRED')
        require(type(text) is str and 0 < len(text) <= 20000, 'INVALID_REFERENCE_TEXT')
        normalized = normalize(text)
        words = normalized.split()
        require(words and len(words) <= 4096, 'EMPTY_OR_OVERSIZED_REFERENCE')
        record = state['records'][case_id]
        if record['reference']:
            record['reference_revisions'].append(record['reference'])
        record['reference'] = {'text': text, 'words': words, 'HUMAN_VERIFIED_REFERENCE': True,
                               'raw_reference_sha256': digest(text.encode()),
                               'normalized_reference_sha256': digest(normalized.encode()),
                               'verified_at': utc_now(), 'source': 'HUMAN_LISTENED_AND_VERIFIED'}
        self._save(state, 'REFERENCE_VERIFIED', case_id)
        return self.state()

    def exclude(self, case_id, reason):
        state = self._load()
        self._mutable(state)
        self._item(case_id)
        require(reason in EXCLUSION_REASONS, 'INVALID_EXCLUSION_REASON')
        require(not any(e['case_id'] == case_id for e in state['exclusions']), 'CASE_ALREADY_EXCLUDED')
        state['exclusions'].append({'case_id': case_id, 'reason': reason, 'at': utc_now(),
                                    'stage': 'PRE_PROVIDER'})
        self._save(state, 'PREFLIGHT_EXCLUSION', case_id)
        return self.state()

    def _audit_record(self, case_id, record):
        audio = record['recording']
        require(audio['conversion_recipe_sha256'] in
                {digest(encoded(CONVERSION_RECIPE)), digest(encoded(NATIVE_CONVERSION_RECIPE))},
                'UNKNOWN_CONVERSION_RECIPE')
        if audio['conversion_recipe_sha256'] == digest(encoded(NATIVE_CONVERSION_RECIPE)):
            require(audio['source_sha256'] == audio['uploaded_wav_sha256'] and audio['source_rate_hz'] == 16000,
                    'NATIVE_SOURCE_EVALUATION_MISMATCH')
        for path, field, evaluation in ((f'source/{case_id}.wav', 'source_sha256', False),
                                         (f'audio/{case_id}.wav', 'uploaded_wav_sha256', True)):
            data = self._path(path).read_bytes()
            require(digest(data) == audio[field], 'AUDIO_HASH_MISMATCH')
            checked = validate_wav(data, evaluation=evaluation)
            if evaluation:
                require(checked['duration_us'] == audio['duration_us'], 'DURATION_MISMATCH')
        ref = record['reference']
        require(ref and ref['HUMAN_VERIFIED_REFERENCE'] is True, 'MISSING_HUMAN_REFERENCE')
        require(digest(ref['text'].encode()) == ref['raw_reference_sha256'] and
                digest(normalize(ref['text']).encode()) == ref['normalized_reference_sha256'] and
                normalize(ref['text']).split() == ref['words'] and ref['words'], 'REFERENCE_HASH_MISMATCH')

    def select(self):
        state = self._load()
        if state['selection'] is not None:
            self._verify_frozen_selection(state)
            return state['selection']
        self._mutable(state)
        require(state['attestation']['confirmed'] is True, 'ATTESTATION_REQUIRED')
        excluded = {e['case_id'] for e in state['exclusions']}
        if self._reduced(state):
            require(all(case_id in state['records'] and case_id not in excluded for case_id in self._active_ids(state)),
                    'REDUCED_EIGHT_ALL_CLIPS_REQUIRED')
        eligible = []
        seen_source, seen_upload = set(), set()
        for item in self._items():
            case_id = item['id']
            if case_id in excluded:
                continue
            require(case_id in state['records'], 'INVENTORY_RECORDING_OR_EXCLUSION_REQUIRED')
            record = state['records'][case_id]
            self._audit_record(case_id, record)
            source = record['recording']['source_sha256']
            upload = record['recording']['uploaded_wav_sha256']
            require(source not in seen_source and upload not in seen_upload, 'DUPLICATE_SOURCE')
            seen_source.add(source)
            seen_upload.add(upload)
            eligible.append((selection_key(source), case_id, item))
        selection = {k: {'ru': [], 'en': []} for k in ('quality', 'noise', 'reserves', 'timing')}
        if self._reduced(state):
            for lang in ('ru', 'en'):
                selection['quality'][lang] = [case_id for case_id in self._active_ids(state) if case_id.startswith(lang + '-')]
        for lang in ('ru', 'en'):
            if self._reduced(state):
                continue
            for kind in ('READ', 'SPONTANEOUS', 'NOISY'):
                rows = sorted((key, case_id) for key, case_id, item in eligible
                              if item['language'] == lang and item['speech_class'] == kind)
                count = 6 if kind == 'NOISY' else 12
                require(len(rows) >= count, 'INSUFFICIENT_ELIGIBLE_COVERAGE')
                ids = [case_id for _, case_id in rows]
                selection['noise' if kind == 'NOISY' else 'quality'][lang].extend(ids[:count])
                selection['reserves'][lang].extend(ids[count:])
                if kind == 'READ':
                    selection['timing'][lang] = ids[:6]
                    require(sum(len(state['records'][case_id]['reference']['words'])
                                for case_id in ids[:6]) >= 100, 'TIMING_REFERENCE_WORD_COVERAGE')
        state['selection'] = selection
        state['status'] = 'SELECTED'
        self._write(self._artifact(state, 'selection.json'), {'selection': selection, 'exclusions': state['exclusions'],
                    'source_release': RELEASE, 'inventory_sha256': state['inventory_sha256'],
                    'single_speaker_limitation': 'ONE_AUTHORIZED_OWNER_SPEAKER_PER_LANGUAGE'}, immutable=True)
        self._save(state, 'PROSPECTIVE_SELECTION_FROZEN')
        return selection

    def _verify_frozen_selection(self, state):
        require(state['attestation'] == {'version': ATTESTATION_VERSION,
                'sha256': digest(ATTESTATION_TEXT.encode()), 'confirmed': True}, 'ATTESTATION_REQUIRED')
        frozen = json.loads(self._path(self._artifact(state, 'selection.json')).read_text(encoding='utf-8'))
        require(frozen['selection'] == state['selection'] and frozen['exclusions'] == state['exclusions']
                and frozen['inventory_sha256'] == state['inventory_sha256'], 'SELECTION_HASH_MISMATCH')
        excluded = {e['case_id'] for e in state['exclusions']}
        if self._reduced(state):
            for lang in ('ru', 'en'):
                require(state['selection']['quality'][lang] == [i for i in self._active_ids(state) if i.startswith(lang + '-')]
                        and all(state['selection'][area][lang] == [] for area in ('timing', 'noise', 'reserves')),
                        'REDUCED_SELECTION_MISMATCH')
            require(not excluded.intersection(self._active_ids(state)), 'REDUCED_EIGHT_ALL_CLIPS_REQUIRED')
        for item in self._items():
            if item['id'] not in excluded:
                require(item['id'] in state['records'], 'INVENTORY_RECORDING_OR_EXCLUSION_REQUIRED')
                self._audit_record(item['id'], state['records'][item['id']])

    def save_timings(self, case_id, words, *, confirmed):
        state = self._load()
        self._mutable(state, timings=True)
        require(confirmed is True, 'HUMAN_BLIND_TIMING_ATTESTATION_REQUIRED')
        require(state['selection'] and case_id in sum(state['selection']['timing'].values(), []),
                'NOT_IN_FROZEN_TIMING_SUBSET')
        record = state['records'][case_id]
        self._audit_record(case_id, record)
        validate_timings(words, record['reference']['words'], record['recording']['duration_us'])
        timing = {'words': words, 'HUMAN_ANNOTATED_BLIND_TO_AWS_OUTPUT': True,
                  'source': 'INDEPENDENT_MANUAL_WORD_MARKS_NO_GENERATED_ANCHORS',
                  'reference_sha256': record['reference']['raw_reference_sha256'],
                  'uploaded_wav_sha256': record['recording']['uploaded_wav_sha256'],
                  'annotated_at': utc_now()}
        if record['timing']:
            record['timing_revisions'].append(record['timing'])
        record['timing'] = timing
        record['timing_draft'] = None
        self._write(f'timings/{case_id}.json', timing)
        self._save(state, 'HUMAN_TIMING_VERIFIED', case_id)
        return self.state()

    def save_timing_draft(self, case_id, words):
        state = self._load()
        self._mutable(state, timings=True)
        require(state['selection'] and case_id in sum(state['selection']['timing'].values(), []),
                'NOT_IN_FROZEN_TIMING_SUBSET')
        record = state['records'][case_id]
        require(type(words) is list and len(words) == len(record['reference']['words']), 'TIMING_WORDS_INCOMPLETE')
        for item, reference in zip(words, record['reference']['words']):
            require(type(item) is dict and set(item) == {'text', 'start_us', 'end_us'}
                    and item['text'] == reference, 'TIMING_REFERENCE_MISMATCH')
            for field in ('start_us', 'end_us'):
                require(item[field] is None or (type(item[field]) is int and
                    0 <= item[field] <= record['recording']['duration_us']), 'TIMING_OUT_OF_RANGE')
        if record['timing'] and words != record['timing']['words']:
            record['timing_revisions'].append(record['timing'])
            record['timing'] = None
        record['timing_draft'] = {'words': words, 'authoritative': False, 'updated_at': utc_now()}
        self._save(state, 'HUMAN_TIMING_DRAFT_SAVED', case_id)
        return self.state()

    def get_audio(self, case_id):
        self._item(case_id)
        state = self._load()
        require(case_id in state['records'], 'RECORDING_REQUIRED')
        path = self._path(f'audio/{case_id}.wav')
        require(digest(path.read_bytes()) == state['records'][case_id]['recording']['uploaded_wav_sha256'],
                'AUDIO_HASH_MISMATCH')
        return path

    def state(self):
        state = self._load()
        selection = state['selection']
        items = []
        for item in self._items():
            case_id, lang = item['id'], item['language']
            record = state['records'].get(case_id, {})
            items.append({**item, **{k: record.get(k) for k in ('recording', 'reference', 'timing', 'timing_draft')},
                          'exclusion': next((e for e in state['exclusions'] if e['case_id'] == case_id), None),
                          'selected': bool(selection and case_id in selection['quality'][lang] + selection['noise'][lang]),
                          'timing_selected': bool(selection and case_id in selection['timing'][lang])})
        return {'status': state['status'], 'inventory_sha256': state['inventory_sha256'],
                'protocol_version': state.get('protocol_version', 'dora-owned-corpus-v1'),
                'recording_enabled': state.get('recording_enabled', True),
                'attestation': {**state['attestation'], 'text': ATTESTATION_TEXT},
                'items': items, 'selection': selection, 'public_summary': self.public_summary(state)}

    def public_summary(self, state=None):
        state = state or self._load()
        result = {'schema_version': '1.0', 'status': state['status'],
                  'inventory_sha256': state['inventory_sha256'], 'manifest_sha256': state['manifest_sha256'],
                  'attestation_version': state['attestation']['version'],
                  'attestation_sha256': state['attestation']['sha256'],
                  'candidates': {'ru': {'read': 15, 'spontaneous': 15, 'noisy': 6},
                                 'en': {'read': 15, 'spontaneous': 15, 'noisy': 6}}}
        for field in ('recorded', 'verified_references', 'selected_quality', 'selected_noise', 'timing_clips', 'timing_words'):
            result[field] = {'ru': 0, 'en': 0}
        if self._reduced(state):
            result.update(schema_version='3.0' if state['protocol_version'] == EASY_EN_PROTOCOL else '2.0',
                          protocol_version=state['protocol_version'],
                          protocol_overlay_sha256=state['protocol_overlay_sha256'],
                          recording_enabled=state['recording_enabled'],
                          timing_status='NOT_EVALUATED', noise_status='NOT_EVALUATED',
                          candidates={lang: {'read': 2, 'spontaneous': 2, 'noisy': 0} for lang in ('ru', 'en')})
        if state.get('protocol_version') == EASY_EN_PROTOCOL:
            result['candidates']['en'] = {'read': 4, 'spontaneous': 0, 'noisy': 0}
            result['en_spontaneous_status'] = 'NOT_EVALUATED'
        for case_id, record in self._active_records(state).items():
            lang = case_id[:2]
            result['recorded'][lang] += 1
            result['verified_references'][lang] += bool(record['reference'])
            if not self._reduced(state):
                result['timing_clips'][lang] += bool(record['timing'])
                result['timing_words'][lang] += len(record['timing']['words']) if record['timing'] else 0
        if state['selection']:
            for lang in ('ru', 'en'):
                result['selected_quality'][lang] = len(state['selection']['quality'][lang])
                result['selected_noise'][lang] = len(state['selection']['noise'][lang])
        result['excluded_count'] = sum(not self._reduced(state) or e['case_id'] in self._active_ids(state) for e in state['exclusions'])
        return result

    def _composite(self, state, language, target_frames):
        ids = state['selection']['quality'][language]
        data = bytearray()
        recipe = []
        cursor = 0
        while len(data) // 2 < target_frames:
            case_id = ids[cursor % len(ids)]
            audio = self._path(f'audio/{case_id}.wav').read_bytes()
            with wave.open(io.BytesIO(audio), 'rb') as source:
                pcm = source.readframes(source.getnframes())
            remaining = target_frames - len(data) // 2
            used = min(remaining, len(pcm) // 2)
            recipe.append({'case_id': case_id, 'uploaded_wav_sha256': digest(audio),
                           'source_start_frame': 0, 'frames': used, 'output_start_frame': len(data) // 2})
            data.extend(pcm[:used * 2])
            cursor += 1
        buffer = io.BytesIO()
        with wave.open(buffer, 'wb') as target:
            target.setparams((1, 2, 16000, 0, 'NONE', 'not compressed'))
            target.writeframes(data)
        value = buffer.getvalue()
        path = self._artifact(state, f'composites/{language}-{target_frames}.wav')
        self._write(path, value, immutable=True)
        return {'language': language, 'path': path, 'duration_us': target_frames * 1_000_000 // 16000,
                'sha256': digest(value), 'independent_wer_sample': False, 'silence_frames': 0,
                'recipe': recipe, 'recipe_sha256': digest(encoded(recipe))}

    def finalize(self):
        state = self._load()
        if state['status'] == 'FINALIZED':
            return self.validate_manifest()
        self._mutable(state, timings=True, finalizing=True)
        require(state['selection'], 'SELECTION_REQUIRED')
        self._verify_frozen_selection(state)
        for case_id, record in self._active_records(state).items():
            if case_id not in {e['case_id'] for e in state['exclusions']}:
                self._audit_record(case_id, record)
        for lang, ids in state['selection']['timing'].items():
            if self._reduced(state):
                require(not ids, 'REDUCED_TIMING_NOT_EVALUATED')
                continue
            count = 0
            for case_id in ids:
                record = state['records'][case_id]
                timing = record['timing']
                require(timing and timing['HUMAN_ANNOTATED_BLIND_TO_AWS_OUTPUT'] is True,
                        'TIMING_COVERAGE_INCOMPLETE')
                validate_timings(timing['words'], record['reference']['words'], record['recording']['duration_us'])
                require(self._path(f'timings/{case_id}.json').read_bytes() == encoded(timing), 'TIMING_HASH_MISMATCH')
                count += len(timing['words'])
            require(len(ids) == 6 and count >= 100, 'TIMING_COVERAGE_INCOMPLETE')
        if not state.get('finalization_started_at'):
            state['finalization_started_at'] = utc_now()
            self._save(state, 'MANIFEST_FINALIZATION_STARTED')
        composites = [self._composite(state, lang, frames) for lang in ('ru', 'en')
                      for frames in (4_800_000, 9_599_984)]
        manifest = {'schema_version': '1.0', 'source_release': RELEASE,
                    'inventory_sha256': state['inventory_sha256'], 'attestation': state['attestation'],
                    'material_sha256': {i['id']: digest(i['material'].encode()) for i in self._items()},
                    'destination': 'AWS / Amazon Transcribe / eu-central-1',
                    'authorization_reason': 'EXPLICIT_OWNER_REQUEST_AND_LOCAL_SPEAKER_ATTESTATION',
                    'license_terms': 'Project-authored material; owner-spoken solely for bounded 6.2D evaluation',
                    'participant_voice_implications': 'Single consenting owner voice; no identity published',
                    'source': 'DORA-owned private acquisition', 'cloud_processing_permitted': True,
                    'live_preflight_still_required': True, 'conversion_recipe': CONVERSION_RECIPE,
                    'normalization': 'dora-alpha-asr-normalization-v0.1 / CPython3.12 / Unicode15.0.0',
                    'selection': state['selection'], 'exclusions': state['exclusions'],
                    'selection_sha256': digest(self._path(self._artifact(state, 'selection.json')).read_bytes()),
                    'single_speaker_limitation': 'NO_MULTI_SPEAKER_POPULATION_GENERALIZATION',
                    'records': self._active_records(state), 'composites': composites,
                    'acquisition_events': state['events'], 'finalized_at': state['finalization_started_at']}
        for case_id, record in manifest['records'].items():
            record['language'] = case_id[:2]
            record['speech_class'] = self._item(case_id)['speech_class']
            record['timing_reference_sha256'] = digest(encoded(record['timing'])) if record['timing'] else None
        if self._reduced(state):
            manifest.update(schema_version='3.0' if state['protocol_version'] == EASY_EN_PROTOCOL else '2.0',
                            protocol_version=state['protocol_version'],
                            protocol_overlay_sha256=state['protocol_overlay_sha256'],
                            timing_status='NOT_EVALUATED', noise_status='NOT_EVALUATED')
            if state['protocol_version'] == EASY_EN_PROTOCOL:
                manifest['en_spontaneous_status'] = 'NOT_EVALUATED'
            recipes = {record['recording']['conversion_recipe_sha256'] for record in manifest['records'].values()}
            if digest(encoded(NATIVE_CONVERSION_RECIPE)) in recipes:
                manifest['conversion_recipe'] = NATIVE_CONVERSION_RECIPE if len(recipes) == 1 else {
                    'mode': 'PER_RECORD_CONVERSION_RECIPE_SHA256',
                    'recipes': {digest(encoded(CONVERSION_RECIPE)): CONVERSION_RECIPE,
                                digest(encoded(NATIVE_CONVERSION_RECIPE)): NATIVE_CONVERSION_RECIPE}}
        self._write(self._artifact(state, 'manifest.json'), manifest, immutable=True)
        state['manifest_sha256'] = digest(encoded(manifest))
        state['status'] = 'FINALIZED'
        self._save(state, 'MANIFEST_FINALIZED')
        self._write(self._artifact(state, 'public-summary.json'), self.public_summary(state), immutable=True)
        return self.public_summary(state)

    def validate_manifest(self):
        """Re-read every bound source/upload/timing/composite on autonomous resume."""
        state = self._load()
        require(state['status'] == 'FINALIZED', 'MANIFEST_NOT_FINALIZED')
        raw = self._path(self._artifact(state, 'manifest.json')).read_bytes()
        require(digest(raw) == state['manifest_sha256'], 'MANIFEST_HASH_MISMATCH')
        manifest = json.loads(raw)
        require(self._active_records(state) == manifest['records'] and state['attestation'] == manifest['attestation'],
                'MANIFEST_STATE_MISMATCH')
        require(manifest['inventory_sha256'] == state['inventory_sha256'], 'INVENTORY_HASH_MISMATCH')
        require(digest(self._path(self._artifact(state, 'selection.json')).read_bytes()) == manifest['selection_sha256'],
                'SELECTION_HASH_MISMATCH')
        require(state['selection'] == manifest['selection'] and state['exclusions'] == manifest['exclusions'],
                'SELECTION_HASH_MISMATCH')
        self._verify_frozen_selection(state)
        excluded = {e['case_id'] for e in manifest['exclusions']}
        for case_id, record in manifest['records'].items():
            if case_id not in excluded:
                self._audit_record(case_id, record)
            if record['timing'] and not self._reduced(state):
                require(digest(self._path(f'timings/{case_id}.json').read_bytes()) ==
                        record['timing_reference_sha256'], 'TIMING_HASH_MISMATCH')
                validate_timings(record['timing']['words'], record['reference']['words'],
                                 record['recording']['duration_us'])
        for composite in manifest['composites']:
            data = self._path(composite['path']).read_bytes()
            require(digest(data) == composite['sha256'] and
                    digest(encoded(composite['recipe'])) == composite['recipe_sha256'],
                    'COMPOSITE_HASH_MISMATCH')
            require(validate_wav(data)['duration_us'] == composite['duration_us'], 'COMPOSITE_DURATION_MISMATCH')
        return self.public_summary(state)


def main():
    import argparse
    parser = argparse.ArgumentParser(description='Private corpus operator control; never calls AWS.')
    parser.add_argument('action', choices=['migrate-reduced-eight', 'resume-recording'])
    parser.add_argument('--root', required=True)
    parser.add_argument('--repo', required=True)
    parser.add_argument('--owner-ready', action='store_true', help='Only after explicit owner readiness instruction.')
    args = parser.parse_args()
    try:
        store = CorpusStore(args.root, args.repo)
        if args.action == 'migrate-reduced-eight':
            store.migrate_reduced_eight()
        else:
            store.resume_recording(confirmed=args.owner_ready)
        print(json.dumps(store.public_summary(), sort_keys=True))
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
