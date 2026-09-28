"""Synthetic UI fixture server; temporary audio is silence, never microphone data."""
from pathlib import Path
import copy
import io
import sys
import tempfile
import wave
from server import make_server


class FixtureStore:
    def __init__(self, root):
        self.root = root
        self.data = {'status': 'ACQUIRING', 'attestation': {'text': 'SYNTHETIC TEST ONLY', 'confirmed': False},
                     'items': [{'id': 'synthetic', 'language': 'en', 'speech_class': 'READ',
                                'material': 'hello world', 'condition': 'CLEAN',
                                'recording': {'duration_us': 20000000}, 'reference': None,
                                'timing': None, 'exclusion': None, 'timing_selected': False}]}
        with wave.open(str(root / 'synthetic.wav'), 'wb') as out:
            out.setnchannels(1); out.setsampwidth(2); out.setframerate(16000)
            out.writeframes(b'\0\0' * 320000)
        if '--record' in sys.argv:
            self.data['items'][0]['recording'] = None
        if '--reduced8' in sys.argv:
            self.data['protocol_version'] = 'dora-owned-reduced8-v2'
            self.data['recording_enabled'] = False
            self.data['attestation']['confirmed'] = True
            for language, speech_class, number in [('en', 'READ', 2), ('en', 'SPONTANEOUS', 1), ('en', 'SPONTANEOUS', 2), ('ru', 'READ', 1), ('ru', 'READ', 2), ('ru', 'SPONTANEOUS', 1), ('ru', 'SPONTANEOUS', 2)]:
                item = copy.deepcopy(self.data['items'][0])
                item.update(id=f'{language}-{speech_class.lower()}-{number:02}', language=language, speech_class=speech_class, reference={'text': 'hello world', 'words': ['hello', 'world']})
                self.data['items'].append(item)

    def state(self):
        return self.data

    def get_audio(self, case_id):
        assert case_id == 'synthetic'
        return self.root / 'synthetic.wav'

    def attest(self, confirmed):
        assert confirmed is True
        self.data['attestation']['confirmed'] = True

    def save_capture(self, case_id, source, evaluation):
        assert self.data['attestation']['confirmed'] is True
        assert case_id == 'synthetic' and self.data['items'][0]['recording'] is None
        with wave.open(io.BytesIO(source), 'rb') as original, wave.open(io.BytesIO(evaluation), 'rb') as final:
            assert original.getnchannels() == final.getnchannels() == 1
            assert original.getsampwidth() == final.getsampwidth() == 2
            assert final.getframerate() == 16000
            duration = final.getnframes() / 16000
            assert 20 <= duration <= 45, f'SYNTHETIC_CAPTURE_DURATION_OUT_OF_RANGE: {duration}'
            assert abs(original.getnframes() / original.getframerate() - duration) < .001
        (self.root / 'synthetic.wav').write_bytes(evaluation)
        (self.root / 'original.wav').write_bytes(source)
        self.data['items'][0]['recording'] = {'duration_us': round(duration * 1000000)}

    def verify_reference(self, case_id, text, confirmed):
        assert confirmed is True and case_id == 'synthetic' and text == 'hello world'
        self.data['items'][0]['reference'] = {'text': text, 'words': ['hello', 'world']}

    def select(self):
        assert self.data['items'][0]['reference']
        self.data['status'] = 'SELECTED'
        if self.data.get('protocol_version') == 'dora-owned-reduced8-v2':
            assert len(self.data['items']) == 8
            assert all(item['recording'] and item['reference'] for item in self.data['items'])
        else:
            self.data['items'][0]['timing_selected'] = True

    def finalize(self):
        assert self.data.get('protocol_version') == 'dora-owned-reduced8-v2'
        assert self.data['status'] == 'SELECTED'
        assert len(self.data['items']) == 8 and all(item['reference'] and item['recording'] for item in self.data['items'])
        assert all(item['timing'] is None for item in self.data['items'])
        self.data['status'] = 'FINALIZED'

    def save_timings(self, case_id, words, confirmed):
        assert confirmed and case_id == 'synthetic'
        assert len(words) == 2 and all(w['start_us'] is not None and w['end_us'] is not None for w in words)
        self.data['items'][0]['timing'] = {'words': words}
        self.data['items'][0]['timing_draft'] = None

    def save_timing_draft(self, case_id, words):
        assert case_id == 'synthetic'
        self.data['items'][0]['timing_draft'] = {'words': words}


if __name__ == '__main__':
    with tempfile.TemporaryDirectory(prefix='dora-synthetic-ui-') as directory:
        server = make_server(FixtureStore(Path(directory)))
        print(f'http://127.0.0.1:{server.server_address[1]}/#{server.token}', flush=True)
        server.serve_forever()
