"""Complement no-key-open device tests with SELECT-only scalar metadata boundaries."""
from pathlib import Path
import unittest

import owner_v9_inventory as v9

ROOT = Path(__file__).resolve().parents[1]
BASE = 'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/'


class RecordingLibraryProjectionContractTest(unittest.TestCase):
    def sources(self):
        return {BASE + path: (ROOT / (BASE + path)).read_text(encoding='utf-8') for path in (
            'persistence/journal/AudioJournalSchema.kt', 'persistence/journal/RoomAudioJournal.kt',
            'persistence/EncryptedAudioVault.kt', 'recording/RecordingSearch.kt')}

    def test_metadata_projection_is_read_only_and_routes_browsing_without_audio_acquisition(self):
        v9.validate_library_projection(self.sources())

    def test_pcm_recovery_key_reads_and_writing_queries_are_rejected(self):
        sources = self.sources()
        journal = BASE + 'persistence/journal/RoomAudioJournal.kt'
        for invocation in ('bridge.read(identity)', 'originals.acquire(identity)',
                           'ProtectedPcm.readWindow(identity)', 'keys.openExisting(identity)',
                           'RecordingRecoveryReader(journal, bridge).read(identity)'):
            broken = dict(sources)
            broken[journal] = broken[journal].replace('val revision = catalogRevision()',
                'val revision = catalogRevision(); ' + invocation, 1)
            self.assertNotEqual(sources[journal], broken[journal])
            with self.subTest(invocation=invocation), self.assertRaises(ValueError):
                v9.validate_library_projection(broken)
        schema = BASE + 'persistence/journal/AudioJournalSchema.kt'
        broken = dict(sources)
        broken[schema] = broken[schema].replace('SELECT COUNT(*) AS unitCount', 'DELETE FROM unit_claim; SELECT COUNT(*) AS unitCount', 1)
        with self.assertRaises(ValueError):
            v9.validate_library_projection(broken)
        vault = BASE + 'persistence/EncryptedAudioVault.kt'
        broken = dict(sources)
        broken[vault] = broken[vault].replace('::recordingLibraryMetadata', '::recordingRecovery', 1)
        with self.assertRaises(ValueError):
            v9.validate_library_projection(broken)


if __name__ == '__main__':
    unittest.main()
