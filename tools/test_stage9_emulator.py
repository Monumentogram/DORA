import unittest
import ast
import json
from pathlib import Path
import re
import sqlite3
from run_stage9_emulator import successful, validate_serial

ROOT = Path(__file__).resolve().parents[1]
JOURNAL = ROOT / 'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/persistence/journal'
SCHEMAS = ROOT / 'android/core/audio/schemas/com.monumentogram.dora.audio.persistence.journal.AudioJournalDatabase'


class Stage9EmulatorTest(unittest.TestCase):
    def test_every_core_sqlite_callback_has_an_explicit_current_or_historical_scope(self):
        directory = ROOT / 'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio'
        observed = {}
        for path in directory.rglob('*.kt'):
            versions = re.findall(r'SupportSQLiteOpenHelper\s*\.\s*Callback\s*\(\s*([^\)]+)\)',
                                  path.read_text(encoding='utf-8'))
            if versions:
                observed[path.relative_to(directory).as_posix()] = [v.strip() for v in versions]
        # Current-source checkpoint readers must use the latest admitted journal. The other
        # callbacks deliberately create tiny policy/revision databases or explicit old fixtures.
        self.assertEqual({
            'diagnostic/ProtectedHistoricalVaultTest.kt': ['3', '3'],
            'persistence/LogicalSourceCheckpoint.kt': ['AUDIO_INDEX_SCHEMA_VERSION'],
            'persistence/OriginalAudioProcessDeathTest.kt': ['AUDIO_INDEX_SCHEMA_VERSION'],
            'persistence/database/SqlCipherConnectionPolicyTest.kt': ['1', '1'],
            'persistence/journal/AudioCatalogRevisionTest.kt': ['1'],
            'persistence/journal/OriginalAudioFullMigrationTest.kt': ['version'],
            'persistence/journal/OriginalAudioMigrationTest.kt': ['version'],
            'persistence/journal/TranscriptMigrationTest.kt': ['version'],
        }, observed)
        latest = max(int(path.stem) for path in SCHEMAS.glob('*.json'))
        migration = ROOT / 'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/journal/AudioDeletionIndexMigration.kt'
        self.assertRegex(migration.read_text(encoding='utf-8'),
                         rf'const val AUDIO_INDEX_SCHEMA_VERSION = {latest}\b')

    def test_stage9_preserves_all_ordinary_original_audio_migration_cases(self):
        tree = ast.parse((ROOT / 'tools/run_stage9_emulator.py').read_text(encoding='utf-8'))
        batch = next(node for node in ast.walk(tree) if isinstance(node, ast.Call)
                     and isinstance(node.func, ast.Name) and node.func.id == 'instrument'
                     and node.args and isinstance(node.args[0], ast.Constant)
                     and node.args[0].value == 'encrypted-persistence-and-migration')

        def selection(node):
            if isinstance(node, ast.Constant) and isinstance(node.value, str):
                return node.value
            if isinstance(node, ast.Name) and node.id == 'prefix':
                return 'com.monumentogram.dora.audio.persistence.'
            if isinstance(node, ast.BinOp) and isinstance(node.op, ast.Add):
                return selection(node.left) + selection(node.right)
            self.fail('Unexpected migration selection expression')

        prefix = 'com.monumentogram.dora.audio.persistence.'
        self.assertEqual({prefix + name for name in ('TranscriptPersistenceTest',
            'journal.TranscriptMigrationTest', 'journal.OriginalAudioMigrationTest',
            'journal.OriginalAudioFullMigrationTest')}, set(selection(batch.args[2]).split(',')))
        self.assertEqual(9, batch.args[3].value)
        ordinary = set(json.loads((ROOT / 'docs/contracts/alpha-v9-persistence-tests-v1.json')
                                 .read_text(encoding='utf-8'))['tests'])
        for name in ('OriginalAudioMigrationTest', 'OriginalAudioFullMigrationTest'):
            source = (JOURNAL / (name + '.kt')).read_text(encoding='utf-8')
            for method in re.findall(r'@Test\s+fun (\w+)', source):
                self.assertIn(prefix + 'journal.' + name + '#' + method, ordinary)

    def test_historical_fixture_readbacks_target_current_schema_without_downgrade(self):
        latest = max(int(path.stem) for path in SCHEMAS.glob('*.json'))
        versions = {}
        production = ROOT / 'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/journal'
        for path in production.glob('*.kt'):
            versions.update((name, int(value)) for name, value in re.findall(
                r'const val (\w+SCHEMA_VERSION) = (\d+)', path.read_text(encoding='utf-8')))
        for name in ('OriginalAudioMigrationTest.kt', 'OriginalAudioFullMigrationTest.kt'):
            source = (JOURNAL / name).read_text(encoding='utf-8')
            callbacks = re.findall(r'(?:fixture\.raw\(|raw\(f,\s*)(\w+SCHEMA_VERSION)', source)
            self.assertTrue(callbacks, name)
            for version in callbacks:
                self.assertEqual(latest, versions[version], (name, version))

    def test_full_fixture_removes_all_successor_tables_and_indexes_from_exported_schema(self):
        source = (JOURNAL / 'OriginalAudioFullMigrationTest.kt').read_text(encoding='utf-8')
        latest = max(int(path.stem) for path in SCHEMAS.glob('*.json'))

        def statements(version):
            document = json.loads((SCHEMAS / f'{version}.json').read_text(encoding='utf-8'))
            for entity in document['database']['entities']:
                yield entity['createSql'].replace('${TABLE_NAME}', entity['tableName'])
                for index in entity.get('indices', []):
                    yield index['createSql'].replace('${TABLE_NAME}', entity['tableName'])

        def inventory(database):
            return {name: re.sub(r'\s+', ' ', sql.replace('IF NOT EXISTS ', '').strip())
                    for name, sql in database.execute(
                        "SELECT name,sql FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'")}

        for version in (1, 2):
            with self.subTest(version=version), sqlite3.connect(':memory:') as actual, sqlite3.connect(':memory:') as expected:
                actual.execute('PRAGMA foreign_keys=ON')
                for statement in statements(latest):
                    actual.execute(statement)
                for line in source.splitlines():
                    match = re.search(r'db\.execSQL\("(DROP (?:TABLE|INDEX) [^"$]+)"\)', line)
                    if match and not (version == 2 and 'if (oldVersion == 1)' in line):
                        actual.execute(match[1])
                for statement in statements(version):
                    expected.execute(statement)
                self.assertEqual(inventory(expected), inventory(actual))

    def test_physical_and_ambiguous_targets_are_rejected(self):
        for value in ("", "device", "emulator-5554 extra", "emulator-5554;echo"):
            with self.assertRaises(ValueError):
                validate_serial(value)
        validate_serial("emulator-5554")

    def test_instrumentation_exit_alone_cannot_claim_pass(self):
        self.assertFalse(successful("INSTRUMENTATION_CODE: -1", 3))
        self.assertFalse(successful("FAILURES!!!\nOK (3 tests)", 3))
        self.assertFalse(successful("OK (2 tests)", 3))
        self.assertFalse(successful("INSTRUMENTATION_STATUS_CODE: -3\nOK (3 tests)", 3))
        self.assertTrue(successful("OK (3 tests)\nINSTRUMENTATION_CODE: -1", 3))


if __name__ == "__main__":
    unittest.main()
