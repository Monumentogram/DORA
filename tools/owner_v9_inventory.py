"""Reviewed v9 source/inventory successor; runtime and historical qualification remain separate."""
import hashlib
import json
import re

from alpha_security_preflight import require
from owner_arm82_inventory import HISTORICAL, PHASED
from validate_recording_latency import POLICY_PATH

ADMISSION = 'docs/contracts/alpha-v9-storage-admission-v1.json'
INVENTORY = 'docs/contracts/alpha-v9-persistence-tests-v1.json'
PREDECESSOR = 'docs/contracts/owner-arm82-persistence-tests-v1.json'
ADMISSION_SHA256 = '1d1e1bc07e04a06df5bdfa5005a339547696a63af9dd7ca39da6ffad2bf6fbc7'
INVENTORY_SHA256 = '3db0fdade6f90323984cd88650492a60dc0fe143d7247a279e309a2fbd50a9cd'
PREDECESSOR_SHA256 = 'f72905a663e7bfcd7b41ce7497f4141ddb2014c039cc5afedf665d1c314edd85'
CONTROLLER = 'android/app/src/main/kotlin/com/monumentogram/dora/recording/RecordingController.kt'
PREFIX = 'com.monumentogram.dora.audio.persistence.'
PAGED_PHASES = {PREFIX + 'PagedTranscriptProcessRestartTest#' + name for name in ('seed', 'resumeAfterProcessRestart')}
QUALIFICATION = {
    PREFIX + 'Audio24HourSyntheticTest#encryptedTwentyFourHoursReopensSeeksStreams288BoundedWindowsAndDeletes',
    PREFIX + 'ArchivedStorageRecoveryTest#preservedFullDiskFixtureCheckpointsAndAuthenticatesItsExactCommittedPrefix',
}
SCHEMA_IDENTITIES = {4: '3af41c4ca80a221a72c975d655bce119',
                     5: 'be7c41fa8dbf7312dba91ab72d7175d6', 6: '0242040b2128fe0e895a4c73ed872b2f'}


def schema_path(version):
    return 'android/core/audio/schemas/com.monumentogram.dora.audio.persistence.journal.AudioJournalDatabase/' + str(version) + '.json'


def parse_json(text):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, 'V9_DUPLICATE_JSON_KEY')
            result[key] = value
        return result
    return json.loads(text, object_pairs_hook=unique)


def digest(path, text):
    text = text.replace('\r\n', '\n')
    if path.endswith('.json'):
        text = json.dumps(parse_json(text), sort_keys=True, separators=(',', ':'), ensure_ascii=False)
    return hashlib.sha256(text.encode('utf-8')).hexdigest()


def read_pinned(root, path, expected):
    text = (root / path).read_text(encoding='utf-8')
    require(digest(path, text) == expected, 'UNREVIEWED_V9_CONTRACT:' + path)
    return parse_json(text)


def candidate(root):
    return (root / ADMISSION).is_file() and (root / INVENTORY).is_file()


def read_admission(root):
    admission = read_pinned(root, ADMISSION, ADMISSION_SHA256)
    require(admission['migration_versions'] == [4, 5, 6] and
            admission['qualification'] == 'NOT_DETERMINED_BY_SOURCE_CHECK', 'V9_ADMISSION_SCOPE_CHANGED')
    return admission


def read_inventory(root):
    require(hashlib.sha256((root / PREDECESSOR).read_bytes().replace(b'\r\n', b'\n')).hexdigest() ==
            PREDECESSOR_SHA256, 'V9_HISTORICAL_INVENTORY_CHANGED')
    return read_pinned(root, INVENTORY, INVENTORY_SHA256)


def read_sources(root):
    return {path: (root / path).read_text(encoding='utf-8') for path in read_admission(root)['sources']}


def validate_schema_chain(schemas):
    require(set(schemas) == {4, 5, 6}, 'V9_SCHEMA_CHAIN_INCOMPLETE')
    databases = {version: document['database'] for version, document in schemas.items()}
    entities = {}
    for version, db in databases.items():
        require(db['version'] == version and db['identityHash'] == SCHEMA_IDENTITIES[version],
                'V9_SCHEMA_IDENTITY_CHANGED')
        entities[version] = {entity['tableName']: entity for entity in db['entities']}
        require(len(entities[version]) == len(db['entities']), 'V9_DUPLICATE_SCHEMA_ENTITY')
    old, paged, indexed = (entities[version] for version in (4, 5, 6))
    additions = {'transcript_progress', 'transcript_part', 'transcript_page', 'transcript_page_edit', 'audio_catalog_revision'}
    require(set(paged) == set(old) | additions and all(paged[name] == entity for name, entity in old.items()),
            'V9_V4_TO_V5_NOT_ADDITIVE')
    require(set(indexed) == set(paged), 'V9_V5_TO_V6_TABLE_SCOPE_CHANGED')
    expected = {**paged['deletion_target'], 'indices': [*paged['deletion_target']['indices'], {
        'name': 'index_deletion_target_assetId_kind_completed_runId_relativeName', 'unique': False,
        'columnNames': ['assetId', 'kind', 'completed', 'runId', 'relativeName'], 'orders': [],
        'createSql': 'CREATE INDEX IF NOT EXISTS `index_deletion_target_assetId_kind_completed_runId_relativeName` '
                     'ON `${TABLE_NAME}` (`assetId`, `kind`, `completed`, `runId`, `relativeName`)'}]}
    require(all(indexed[name] == (expected if name == 'deletion_target' else entity)
                for name, entity in paged.items()), 'V9_V5_TO_V6_NOT_REVIEWED_INDEX_ONLY')
    require(databases[4].get('views') == databases[5].get('views') == databases[6].get('views'),
            'V9_SCHEMA_VIEWS_CHANGED')


def validate_sources(root, sources=None):
    pins = read_admission(root)['sources']
    sources = read_sources(root) if sources is None else sources
    require(set(sources) == set(pins), 'V9_SOURCE_INVENTORY_CHANGED')
    for path, expected in pins.items():
        require(digest(path, sources[path]) == expected, 'UNREVIEWED_V9_STORAGE_SOURCE:' + path)
    validate_schema_chain({version: parse_json(sources[schema_path(version)]) for version in (4, 5, 6)})
    validate_library_projection(sources)


def validate_library_projection(sources):
    """Static boundary checks complement actual encrypted no-run-key-open device tests."""
    base = 'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/'
    schema = sources[base + 'persistence/journal/AudioJournalSchema.kt']
    names = {'lastLibraryClaim', 'libraryClaimStats', 'invalidLibraryClaim', 'libraryFinalizationStats'}
    queries = dict((name, expression) for expression, name in re.findall(
        r'@Query\(((?:(?!@Query).)*?)\)\s*fun\s+(lastLibraryClaim|libraryClaimStats|invalidLibraryClaim|libraryFinalizationStats)\b', schema, re.S))
    require(set(queries) == names, 'V9_LIBRARY_QUERY_INVENTORY_CHANGED')
    literal = r'"""[\s\S]*?"""|"(?:\\.|[^"\\])*"'
    allowed_tables = {'unit_claim', 'physical_source', 'bootstrap', 'microfile', 'manifest', 'finalization_source'}
    for name, expression in queries.items():
        require(re.fullmatch(r'[\s+]*', re.sub(literal, '', expression)) is not None,
                'V9_LIBRARY_QUERY_NOT_CONSTANT')
        parts = re.findall(literal, expression)
        query = ''.join(part[3:-3] if part.startswith('"""') else json.loads(part) for part in parts)
        require(query.lstrip().upper().startswith('SELECT ') and ';' not in query and
                not re.search(r'\b(?:INSERT|UPDATE|DELETE|DROP|ALTER|CREATE|PRAGMA|ATTACH|DETACH)\b', query, re.I),
                'V9_LIBRARY_QUERY_NOT_READ_ONLY')
        require(set(re.findall(r'\b(?:FROM|JOIN)\s+(\w+)', query, re.I)) <= allowed_tables,
                'V9_LIBRARY_QUERY_TABLE_SCOPE_CHANGED')
        if name in {'lastLibraryClaim', 'invalidLibraryClaim'}:
            require(re.search(r'\bLIMIT\s+1\s*$', query, re.I) is not None, 'V9_LIBRARY_ROW_READ_NOT_BOUNDED')

    def method(source, name):
        found = re.search(r'(?m)^    (?:(?:private|override)\s+)?fun\s+' + name + r'\b', source)
        require(found is not None, 'V9_LIBRARY_METHOD_MISSING')
        closing = re.search(r'(?m)^    }\s*$', source[found.end():])
        require(closing is not None, 'V9_LIBRARY_METHOD_BOUNDARY_MISSING')
        return source[found.start():found.end() + closing.end()]

    forbidden = (r'\b(?:RecordingRecoveryReader|ProtectedPcm|RecoveryAudioBridge|VaultKeystoreIo|AndroidVaultKeystoreIo)\b|'
                 r'\b(?:bridge|originals|keys|reader)\s*\.|'
                 r'\b(?:sourceForRead|recordingRecovery|openAuthenticated|acquire|extract|reconcile|openExisting)\s*\(')
    vault = sources[base + 'persistence/EncryptedAudioVault.kt']
    journal = sources[base + 'persistence/journal/RoomAudioJournal.kt']
    for body in (method(vault, 'recordingLibraryMetadata'), method(journal, 'recordingLibraryMetadata')):
        require(re.search(forbidden, body) is None, 'V9_LIBRARY_AUDIO_OR_RECOVERY_ACCESS')
    browse = method(vault, 'recordingSearchPage')
    require('::recordingLibraryMetadata' in browse and '::recordingRecovery' not in browse,
            'V9_LIBRARY_BROWSE_USES_RECOVERY')


def project_policy_sources(files, root):
    """Validate the exact successor, then replay only the inherited helper's static checks."""
    import validate_encrypted_persistence as persistence
    reviewed = read_sources(root)
    reviewed.update({path: text for path, text in files.items() if path in reviewed})
    validate_sources(root, reviewed)
    projected = dict(files)
    projected[POLICY_PATH] = persistence.git(root, 'show',
        '4ee27ef2473b0b047ee73be0595e981d08848e94:' + POLICY_PATH).decode()
    return projected


def validate_accounting(contract, discovered, predecessor):
    require(contract['successor'] == 'ALPHA_V9_24H' and contract['profile'] == 'PIN_FREE_LOCAL_ACCESS' and
            contract['predecessor_sha256'] == PREDECESSOR_SHA256 and
            contract['qualification'] == 'NOT_DETERMINED_BY_SOURCE_INVENTORY', 'V9_INVENTORY_SCOPE_CHANGED')
    names = ('tests', 'historical_not_qualified', 'separate_process_tests', 'qualification_only_tests', 'deferred_tests')
    groups = [set(contract[name]) for name in names]
    require(all(len(group) == len(contract[name]) for name, group in zip(names, groups)), 'V9_DUPLICATE_TEST')
    bulk, historical, phases, qualification, deferred = groups
    require(historical == HISTORICAL == set(predecessor['historical_not_qualified']) and
            phases == PHASED | PAGED_PHASES and qualification == QUALIFICATION and not deferred,
            'V9_TEST_DISPOSITION_CHANGED')
    require(sum(map(len, groups)) == len(set().union(*groups)) and set().union(*groups) == discovered,
            'V9_INVENTORY_NOT_EXHAUSTIVE_OR_DISJOINT')
    require(set(predecessor['tests']) | set(predecessor['deferred_tests']) <= bulk,
            'V9_INHERITED_REGRESSION_REMOVED')
    require(contract['no_credential_tests'] == predecessor['no_credential_tests'] and
            set(contract['no_credential_tests']) <= bulk and
            contract['qualification_arguments'] == {'dora24hSynthetic': 'true'} and
            contract.get('archived_recovery_arguments') == {'doraRecoverFailed24h': 'true'}, 'V9_CONTROLS_CHANGED')
    return bulk
