"""Bounded Stage 8.2 successor validation; historical gates remain source-specific."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

BASELINE = "104ba3e6aa5b4d97633f80e275685d347a9ee128"
ROOT = Path(__file__).resolve().parents[1]
CONTRACT = "docs/contracts/DORA_ENCRYPTED_PERSISTENCE_8_2_V0_1.json"
# Exact candidate inventory; final runtime/review/publication acceptance remains separate.
CONTRACT_SHA256 = 'b30baa661330eb3286d7ae3983957a47ade543c016249f6c5ad2d63144761c76'
BASELINE_TREE = "f4574bc0d3bf2ea08d836ff091f48ff5616d55c8"
BASELINE_PARENT = "f3e58b12d0e9353cdb17e986336f23f48f33511c"
BRANCH = "stage/8.2-encrypted-persistence"
BACKUP_DOMAINS = frozenset({"root", "file", "database", "sharedpref", "external",
                          "device_root", "device_file", "device_database", "device_sharedpref"})
COORDINATE = "com.monumentogram.dora.thirdparty:sqlcipher-android:4.17.0-dora.1"
PRODUCT = 'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/'
RELEASE_GRAPH = 'docs/evidence/persistence-8.2-release-runtime-graph.json'
DEVICE_INVENTORY = 'docs/contracts/persistence-8.2-device-tests.json'
RELEASE_PROJECTS = frozenset({':app', ':core:audio', ':core:common', ':core:model'})
VERIFIED_ARTIFACTS = {
    (*COORDINATE.split(':'), 'sqlcipher-android-4.17.0-dora.1.aar'):
        {'4363d513532519f013ec620706dcb27ca40d2af5490c777165484c124f269120'},
    (*COORDINATE.split(':'), 'sqlcipher-android-4.17.0-dora.1.pom'):
        {'18838e526ebc38ea04241eedcb0dca2c32301477a06a699db62a7deca5037d84'},
}
STATUS_HEADER = """## 2026-10-01 — Stage 8.2 encrypted product persistence

8.2 = PENDING_FINAL_PUBLICATION
Target = ENCRYPTED_PRODUCT_PERSISTENCE_RUNTIME_READY
Stage 8.2C = NOT_STARTED
Stage 8.3 = NOT_STARTED
Group C = IN_PROGRESS
Exact-SHA CI, independent review and external publication receipt remain required.

"""


def read_product_sources(root=ROOT):
    files = {p.relative_to(root).as_posix(): p.read_text(encoding='utf-8')
             for p in (root / PRODUCT).rglob('*.kt')}
    for path in ('android/core/audio/build.gradle.kts', 'android/app/build.gradle.kts',
                 'android/gradle/libs.versions.toml', 'android/settings.gradle.kts',
                 'android/build-logic/src/main/kotlin/DoraAndroidSdk.kt'):
        files[path] = (root / path).read_text(encoding='utf-8')
    return files


def validate_product_sources(files):
    """Obvious bypass controls complement behavioral tests and independent source review."""
    compact = lambda value: re.sub(r'\s+', '', value)
    for path, source in files.items():
        if not path.startswith(PRODUCT):
            continue
        for forbidden in ('inMemoryDatabaseBuilder(', 'AndroidRecoveryJournalDatabase(',
                          'FrameworkSQLiteOpenHelperFactory(', 'fallbackToDestructiveMigration(',
                          'android.util.Log', 'println(', 'printStackTrace(', 'FileOutputStream(',
                          'execPerConnectionSQL(', 'AndroidKeystoreAead('):
            require(forbidden not in compact(source), 'Forbidden product persistence fallback/diagnostic: ' + path)
    required = {
        'android/core/audio/build.gradle.kts': [f'implementation("{COORDINATE}")',
            'implementation("com.google.crypto.tink:tink-android:1.23.0")',
            'implementation(libs.androidx.room.runtime)', 'ksp(libs.androidx.room.compiler)',
            'rootProject.file("poc/recovery/src/main/kotlin")', 'implementation(project(":core:model"))'],
        'android/app/build.gradle.kts': ['implementation(project(":core:audio"))'],
        'android/gradle/libs.versions.toml': ['room = "2.8.4"', 'ksp = "2.3.11"'],
        'android/settings.gradle.kts': ['RepositoriesMode.FAIL_ON_PROJECT_REPOS', 'exclusiveContent {',
            'url = uri("vendor/maven")', 'includeGroup("com.monumentogram.dora.thirdparty")'],
        'android/build-logic/src/main/kotlin/DoraAndroidSdk.kt': ['const val MIN = 28', 'const val COMPILE = 36'],
        PRODUCT + 'ProductAudioPort.kt': ['AudioFormat("PCM_S16LE", 16_000, 1)',
            'interface ProductAudioReaderPort', 'interface ProductAudioWriterPort'],
        PRODUCT + 'persistence/database/SqlCipherJournalHelperFactory.kt': [
            'Logger.setTarget(NoopTarget())', 'SQLiteGlobal.setWALConnectionPoolSize(1)',
            'opens.incrementAndGet() != 1', 'PRAGMA synchronous=FULL', 'PRAGMA wal_autocheckpoint=0',
            'PRAGMA temp_store=MEMORY', 'db.setForeignKeyConstraintsEnabled(true)',
            '"journal_mode" to "wal"', '"foreign_keys" to "1"', '"synchronous" to "2"',
            '"wal_autocheckpoint" to "0"', '"temp_store" to "2"', 'secret.fill(0)'],
        PRODUCT + 'persistence/journal/RoomAudioJournal.kt': ['Room.databaseBuilder(', '.openHelperFactory(helperFactory)',
            'database::endTransaction', 'database::inTransaction'],
        PRODUCT + 'persistence/journal/JournalSchemaVerifier.kt': ['PRAGMA foreign_key_check'],
        PRODUCT + 'persistence/keys/VaultSecretStore.kt': ['SecureRandom()::nextBytes', 'storage.reserve(selector)',
            'keys.openExisting(selector)', 'secret.fill(0)'],
        PRODUCT + 'persistence/EncryptedAudioVault.kt': [
            'NoLogRecoveryRunAeadBackend(context, secrets.vaultId, dependencies.runKeystore(AndroidVaultKeystoreIo),)',
            'val runKeystore: (VaultKeystoreIo) -> VaultKeystoreIo = { it }',
            'dependencies: Dependencies = Dependencies()',
            'keyFailures = backend.failureScopes', 'RecoveryKeyBootstrapController(',
            'RecoveryMicrofilePublicationController(', 'openedJournal::hasCommittedDeletionBootstrap'],
    }
    for path, tokens in required.items():
        require(path in files and all(compact(token) in compact(files[path]) for token in tokens),
                'Required encrypted persistence source policy missing: ' + path)
    ports = files[PRODUCT + 'ProductAudioPort.kt']
    require(all(token not in ports for token in ('poc.recovery', 'java.io.', 'android.', 'persistence.keys')),
            'Public product ports expose platform/storage/key internals')


def validate_context(branch, tree, parent, head, dirty, allow_working, environment):
    require((branch, tree, parent) == (BRANCH, BASELINE_TREE, BASELINE_PARENT),
            'Stage 8.2 branch/baseline identity mismatch')
    require(re.fullmatch(r'[0-9a-f]{40}', head), 'Invalid checkout HEAD')
    require(allow_working or not dirty, 'Stage 8.2 checkout must be clean')
    if environment.get('GITHUB_ACTIONS') or environment.get('GITHUB_EVENT_NAME'):
        require(not allow_working and not dirty, 'CI cannot admit working-tree verification')
        require(environment.get('GITHUB_EVENT_NAME') in {'push', 'workflow_dispatch'}
                and environment.get('GITHUB_REPOSITORY') == 'Monumentogram/DORA'
                and environment.get('GITHUB_REF') == 'refs/heads/' + BRANCH
                and environment.get('GITHUB_SHA') == head, 'Stage 8.2 GitHub event identity mismatch')


def validate_backup(manifest, legacy, modern):
    namespace = '{http://schemas.android.com/apk/res/android}'
    root = ET.fromstring(manifest)
    apps = root.findall('application')
    require(len(apps) == 1, 'Expected exactly one product application')
    app = apps[0]
    require(app.get(namespace + 'allowBackup') == 'false'
            and app.get(namespace + 'fullBackupContent') == '@xml/backup_rules'
            and app.get(namespace + 'dataExtractionRules') == '@xml/data_extraction_rules',
            'Product backup exclusions are not installed')
    require(not any(p.get(namespace + 'name') == 'android.permission.RECORD_AUDIO'
                    for p in root.findall('uses-permission')), 'Capture permission is outside Stage 8.2')
    def exclusions(element):
        require(not element.attrib and len(element) == len(BACKUP_DOMAINS), 'Unexpected backup inclusion or policy')
        require(all(child.tag == 'exclude' and child.attrib == {'domain': child.get('domain'), 'path': '.'}
                    and not len(child) for child in element), 'Backup must exclude entire domains')
        require({child.get('domain') for child in element} == BACKUP_DOMAINS, 'Incomplete backup domain exclusions')
    old = ET.fromstring(legacy)
    require(old.tag == 'full-backup-content', 'Wrong legacy backup policy')
    exclusions(old)
    new = ET.fromstring(modern)
    require(new.tag == 'data-extraction-rules' and not new.attrib and len(new) == 2
            and {child.tag for child in new} == {'cloud-backup', 'device-transfer'}, 'Incomplete modern backup policy')
    for child in new:
        exclusions(child)


def lock_entries(text):
    entries = {}
    for line in text.splitlines():
        if not line or line.startswith('#'):
            continue
        coordinate, configurations = line.split('=', 1)
        require(coordinate not in entries and configurations, 'Duplicate or empty lock entry')
        entries[coordinate] = set(configurations.split(','))
    return entries


def validate_lock_projection(historical, current, admitted, *, replacements=None, configuration_moves=None):
    old, new = lock_entries(historical), lock_entries(current)
    replacements = replacements or {}
    configuration_moves = configuration_moves or {}
    require(set(replacements) <= (set(old) - {'empty'})
            and len(set(replacements.values())) == len(replacements),
            'Replacement must identify a unique historical dependency')
    for previous, replacement in replacements.items():
        require(previous != replacement and len(previous.split(':')) == 3
                and len(replacement.split(':')) == 3
                and previous.split(':')[:2] == replacement.split(':')[:2]
                and replacement not in old and replacement not in admitted,
                'Replacement must be an explicit version change of the same component')
    expected = (set(old) - {'empty'} - set(replacements)) | set(replacements.values()) | set(admitted)
    require(set(new) - {'empty'} == expected,
            'Unrelated dependency/version changed or unadmitted lock coordinate')
    require(set(configuration_moves) <= (set(old) & set(new) - {'empty'} - set(replacements)),
            'Configuration upgrade must retain a historical source component')
    for previous, move in configuration_moves.items():
        replacement, configurations = move['replacement'], move['configurations']
        moved = set(configurations)
        require(replacement in new and replacement != previous
                and previous.split(':')[:2] == replacement.split(':')[:2]
                and bool(moved) and len(moved) == len(configurations)
                and moved <= old[previous] and not moved.intersection(new[previous])
                and moved <= new[replacement], 'Unbounded or missing configuration-specific dependency upgrade')
    for coordinate, configurations in old.items():
        if coordinate != 'empty':
            moved = set(configuration_moves.get(coordinate, {}).get('configurations', []))
            require(configurations - moved <= new[replacements.get(coordinate, coordinate)],
                    'Historical locked configuration removed')


def validate_verification_projection(historical, current, admitted):
    def local(element):
        return element.tag.rsplit('}', 1)[-1]

    def shape(element):
        return (element.tag, tuple(sorted(element.attrib.items())), (element.text or '').strip(),
                tuple(shape(child) for child in element))

    def parse(text):
        root = ET.fromstring(text)
        groups = [child for child in root if local(child) == 'components']
        require(len(groups) == 1, 'Exactly one verification component group required')
        artifacts, components = {}, {}
        for component in groups[0]:
            require(local(component) == 'component' and set(component.attrib) == {'group', 'name', 'version'},
                    'Unexpected verification component')
            coordinate = tuple(component.get(k) for k in ('group', 'name', 'version'))
            require(coordinate not in components and len(component) > 0, 'Duplicate or empty verification component')
            components[coordinate] = tuple(sorted(component.attrib.items()))
            for artifact in component:
                require(local(artifact) == 'artifact' and set(artifact.attrib) == {'name'}, 'Unexpected verification artifact')
                key = (*coordinate, artifact.get('name'))
                require(key not in artifacts, 'Duplicate verification artifact')
                artifacts[key] = artifact
        policy = (root.tag, tuple(sorted(root.attrib.items())),
                  tuple(shape(child) for child in root if local(child) != 'components'))
        return policy, components, artifacts

    old_policy, old_components, old = parse(historical)
    policy, components, actual = parse(current)
    require(policy == old_policy, 'Historical verification/trust policy changed')
    require(all(components.get(key) == value for key, value in old_components.items()),
            'Historical verification component removed')
    require(not set(old).intersection(admitted), 'New admission may not redefine historical verification')
    require(set(actual) == set(old) | set(admitted), 'Unadmitted or missing verification artifact')
    for key, artifact in old.items():
        require(shape(actual[key]) == shape(artifact), 'Historical verification artifact changed')
    for key, hashes in admitted.items():
        observed = set()
        for checksum in actual[key]:
            require(local(checksum) == 'sha256' and not len(checksum)
                    and set(checksum.attrib) <= {'value', 'origin'}, 'Unadmitted verification mechanism')
            value = checksum.get('value', '')
            require(re.fullmatch(r'[0-9a-f]{64}', value) and value not in observed, 'Invalid or duplicate artifact checksum')
            observed.add(value)
        require(observed == set(hashes) and bool(observed), 'Artifact checksum differs from admission')


def validate_history(history, head, implementation_paths, evidence_paths):
    require(1 <= len(history) <= 2, 'Expected one implementation and at most one evidence followup')
    previous = BASELINE
    for index, (parent, commit, paths) in enumerate(history):
        require(parent == previous and re.fullmatch(r'[0-9a-f]{40}', commit) and commit != previous,
                'Wrong or non-linear Stage 8.2 predecessor')
        require(set(paths) == set(implementation_paths) if index == 0 else bool(paths) and set(paths) <= set(evidence_paths),
                'Unbounded Stage 8.2 transition inventory')
        previous = commit
    require(previous == head, 'Stage 8.2 HEAD mismatch')


def validate_status_projection(current, historical):
    require(current == STATUS_HEADER + historical, 'Stage 8.2 header or historical status changed')
    return historical


def git(root, *args):
    return subprocess.check_output(['git', '-c', f'safe.directory={root.as_posix()}', '-C', str(root), *args])


def candidate(root=ROOT):
    return (root / CONTRACT).exists() or git(root, 'branch', '--show-current').decode().strip() == BRANCH


def working_verification(requested, environment):
    """Explicit local pre-publication checks; never a CI cleanliness bypass."""
    value = environment.get('DORA_PERSISTENCE_WORKING_CHECK')
    require(value in (None, '', '1'), 'Invalid local working-check opt-in')
    enabled = value == '1' if requested is None else requested
    require(not enabled or not (environment.get('GITHUB_ACTIONS') or environment.get('GITHUB_EVENT_NAME')),
            'CI cannot admit working-tree verification')
    return enabled


def discover_device_tests(sources):
    tests = []
    for path, source in sources.items():
        methods = re.findall(r'@Test\b(?:(?!@Test).)*?\bfun\s+([A-Za-z0-9_]+)', source, re.S)
        require(len(methods) == len(re.findall(r'@Test\b', source)),
                'Unrecognized instrumentation test declaration: ' + path)
        if not methods:
            continue
        package = re.search(r'^package ([\w.]+)', source, re.M)
        require(package is not None and 'class ' + Path(path).stem in source,
                'Unrecognized instrumentation test class: ' + path)
        tests.extend(package[1] + '.' + Path(path).stem + '#' + method for method in methods)
    require(bool(tests) and len(tests) == len(set(tests)), 'Missing or duplicate instrumentation source identity')
    return set(tests)


def validate_device_inventory(raw, expected_tests, expected_sha256):
    import run_encrypted_persistence_device as device
    require(hashlib.sha256(raw).hexdigest() == expected_sha256, 'Reviewed device test inventory changed')
    actual, _ = device.validate_inventory(json.loads(raw))
    require(actual == expected_tests, 'Device inventory omits or invents a source test')


def validate_checkout(root=ROOT, *, allow_working=None):
    import validate_original_audio_lifecycle as lifecycle
    if lifecycle.candidate(root):
        return lifecycle.validate_checkout(root, allow_working=allow_working)
    allow_working = working_verification(allow_working, os.environ)
    require((root / CONTRACT).is_file(), 'Stage 8.2 reviewed contract is missing')
    raw = (root / CONTRACT).read_bytes()
    require(CONTRACT_SHA256 is not None and hashlib.sha256(raw).hexdigest() == CONTRACT_SHA256,
            'Stage 8.2 reviewed contract inventory is not sealed')
    contract = json.loads(raw)
    head = git(root, 'rev-parse', 'HEAD').decode().strip()
    validate_context(git(root, 'branch', '--show-current').decode().strip(),
                     git(root, 'rev-parse', BASELINE + '^{tree}').decode().strip(),
                     git(root, 'show', '-s', '--format=%P', BASELINE).decode().strip(),
                     head, bool(git(root, 'status', '--porcelain', '--untracked-files=all')),
                     allow_working, os.environ)
    if os.environ.get('GITHUB_ACTIONS') or os.environ.get('GITHUB_EVENT_NAME'):
        require(Path(os.environ.get('GITHUB_WORKSPACE', '')).resolve() == root.resolve(),
                'Stage 8.2 CI workspace mismatch')
    paths, evidence_paths = set(contract['implementation_paths']), set(contract['evidence_paths'])
    require(len(paths) == len(contract['implementation_paths']) and CONTRACT in paths,
            'Malformed Stage 8.2 path inventory')
    history = []
    for line in git(root, 'rev-list', '--reverse', '--parents', BASELINE + '..HEAD').decode().splitlines():
        fields = line.split()
        require(len(fields) == 2, 'Stage 8.2 merge commit rejected')
        commit, parent = fields
        changed = git(root, 'diff', '--name-only', '--no-renames', parent, commit).decode().splitlines()
        history.append((parent, commit, changed))
    if history:
        validate_history(history, head, paths, evidence_paths)
    else:
        require(allow_working and head == BASELINE, 'Stage 8.2 implementation commit missing')
    changed = set(git(root, 'diff', '--name-only', '--no-renames', BASELINE).decode().splitlines())
    changed.update(git(root, 'ls-files', '--others', '--exclude-standard').decode().splitlines())
    expected_changes = paths | (set(history[1][2]) if len(history) == 2 else set())
    require(changed == expected_changes, 'Stage 8.2 exact changed-file inventory mismatch')
    # An allowlist must never admit an engine rewrite or a rewritten historical receipt.
    require(not any(p.startswith('android/poc/recovery/') for p in paths),
            'Accepted Recovery module cannot be part of this implementation')
    protected = ['docs/adr/ADR-AUDIO-001-product-recovery-boundary.md',
                 'docs/security/DORA_SECURITY_IDENTITY_ARCHITECTURE_V0_1.md',
                 'docs/contracts/DORA_SECURITY_IDENTITY_ARCHITECTURE_V0_1.json']
    protected += git(root, 'ls-tree', '-r', '--name-only', BASELINE, '--', 'docs/evidence/poc-recovery-001',
                     'android/poc/recovery/src').decode().splitlines()
    for path in protected:
        require((root / path).read_bytes().replace(b'\r\n', b'\n')
                == git(root, 'show', BASELINE + ':' + path).replace(b'\r\n', b'\n'),
                'Accepted security/Recovery source or evidence changed: ' + path)
    for path in ('docs/DORA_MVP1_STAGE_STATUS.md', 'docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md'):
        validate_status_projection((root / path).read_text(encoding='utf-8'),
                                   git(root, 'show', BASELINE + ':' + path).decode())
    validate_verification_projection(git(root, 'show', BASELINE + ':android/gradle/verification-metadata.xml'),
                                     (root / 'android/gradle/verification-metadata.xml').read_bytes(), VERIFIED_ARTIFACTS)
    for path, additions in contract['lock_additions'].items():
        validate_lock_projection(git(root, 'show', BASELINE + ':' + path).decode(),
                                 (root / path).read_text(encoding='utf-8'), additions,
                                 replacements=contract.get('lock_replacements', {}).get(path, {}),
                                 configuration_moves=contract.get('lock_configuration_moves', {}).get(path, {}))
    validate_backup(*[(root / path).read_text(encoding='utf-8') for path in (
        'android/app/src/main/AndroidManifest.xml',
        'android/app/src/main/res/xml/backup_rules.xml',
        'android/app/src/main/res/xml/data_extraction_rules.xml',
    )])
    validate_product_sources(read_product_sources(root))
    import persistence_ci_profile as ci
    ci.validate((root / '.github/workflows/android-ci.yml').read_text(encoding='utf-8'))
    import validate_encrypted_persistence_native as native
    native_inputs = native.read_inputs(root)
    native.validate_bundle(*native_inputs)
    native.validate_sbom(json.loads((root / 'android/vendor/sqlcipher/native-components.cdx.json').read_text()),
                         native_inputs[2])
    import persistence_release_inventory as inventory
    inventory.read_and_validate(root, contract, approved_release_graph(root, contract))
    test_root = root / 'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/persistence'
    test_sources = {p.relative_to(test_root).as_posix(): p.read_text(encoding='utf-8-sig')
                    for p in test_root.rglob('*.kt')}
    validate_device_inventory((root / DEVICE_INVENTORY).read_bytes(), discover_device_tests(test_sources),
                              contract['device_inventory_sha256'])
    return contract


def validate_historical_audio(root=ROOT):
    """Revalidate the immutable 8.1 transition with its original source-specific rules."""
    import validate_audio_recovery_boundary as audio
    require(git(root, 'rev-parse', audio.BASE + '^{tree}').decode().strip() == audio.BASE_TREE
            and git(root, 'show', '-s', '--format=%P', audio.BASE).decode().strip() == audio.BASE_PARENT,
            'Historical 8.1 predecessor identity changed')
    history = git(root, 'rev-list', '--reverse', '--parents', audio.BASE + '..' + BASELINE).decode().splitlines()
    transitions = [git(root, 'diff', '--name-only', '--no-renames', line.split()[1], line.split()[0]).decode().splitlines()
                   for line in history]
    audio.validate_history(history, transitions, BASELINE)
    files = {p: git(root, 'show', BASELINE + ':' + p).decode() for p in audio.PATHS if not p.endswith('.lockfile')}
    audio.validate_sources(files)
    for name in audio.STATUS:
        audio.validate_status_text(files[name], git(root, 'show', audio.BASE + ':' + name).decode())
    require(files['android/settings.gradle.kts'].replace('\ninclude(":core:audio")\n', '', 1)
            == git(root, 'show', audio.BASE + ':android/settings.gradle.kts').decode(),
            'Historical audio module admission changed')


def validate_compiled(root, contract, *, include_app=True):
    import io
    import zipfile
    import validate_audio_recovery_boundary as audio
    import validate_encrypted_persistence_native as native
    path = 'android/core/audio/gradle.lockfile'
    old = lock_entries(git(root, 'show', BASELINE + ':' + path).decode())
    replacements = contract.get('lock_replacements', {}).get(path, {})
    admitted = (set(old) - {'empty'} - set(replacements)) | set(replacements.values()) | set(contract['lock_additions'][path])
    audio.validate_compiled(root, approved_coordinates=admitted)
    with zipfile.ZipFile(root / 'android/core/audio/build/outputs/aar/audio-debug.aar') as aar:
        with zipfile.ZipFile(io.BytesIO(aar.read('classes.jar'))) as classes:
            names = set(classes.namelist())
            prefix = 'com/monumentogram/dora/audio/persistence/'
            for name in ('EncryptedAudioVault', 'database/SqlCipherJournalHelperFactory',
                         'journal/RoomAudioJournal', 'keys/VaultSecretStore', 'auth/AndroidAppLock'):
                require(prefix + name + '.class' in names, 'Required encrypted runtime class was not compiled')
    if include_app:
        import persistence_release_inventory as inventory
        record = inventory.read_and_validate(root, contract, approved_release_graph(root, contract))
        for variant, filename in (('debug', 'app-debug.apk'), ('release', 'app-release-unsigned.apk')):
            apk = root / 'android/app/build/outputs/apk' / variant / filename
            native.validate_apk(apk, root)
            with zipfile.ZipFile(apk) as archive:
                for path, digest in record['distributed_assets'].items():
                    entry = path.removeprefix('android/app/src/main/')
                    require(hashlib.sha256(archive.read(entry)).hexdigest() == digest,
                            'Product APK omitted or changed persistence license text')


def approved_release_graph(root, contract):
    import alpha_release_sbom as sbom
    approved = json.loads((root / RELEASE_GRAPH).read_text(encoding='utf-8'))
    require(hashlib.sha256(sbom.canonical_bytes(approved)).hexdigest() == contract['release_graph_sha256'],
            'Reviewed product release graph identity changed')
    require({node['path'] for node in approved['components'] if node['kind'] == 'project'} == RELEASE_PROJECTS,
            'Required product module edge or module isolation changed')
    composite = [node for node in approved['components'] if node['id'] == 'maven:' + COORDINATE]
    require(len(composite) == 1 and composite[0]['artifacts'] == [{
        'name': 'sqlcipher-android-4.17.0-dora.1.aar',
        'sha256': next(iter(VERIFIED_ARTIFACTS[(*COORDINATE.split(':'), 'sqlcipher-android-4.17.0-dora.1.aar')])),
    }], 'Release graph does not contain the exact admitted composite')
    sbom.check_graph(approved, sbom.lock_coordinates(root / 'android/app/gradle.lockfile'), approved,
                     allowed_projects=RELEASE_PROJECTS)
    return approved


def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--working', action='store_true', default=None)
    parser.add_argument('--compiled', action='store_true')
    args = parser.parse_args()
    contract = validate_checkout(allow_working=args.working)
    validate_historical_audio()
    if args.compiled:
        validate_compiled(ROOT, contract)
    print('PASS bounded Stage 8.2 repository checks; runtime evidence and external publication remain separate')


def require(condition, message):
    if not condition:
        raise ValueError(message)


if __name__ == '__main__':
    main()
