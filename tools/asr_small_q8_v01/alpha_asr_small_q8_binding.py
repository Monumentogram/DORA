"""Additive q8 operator. Import is host-only; execution needs external freeze pins.

The authorization token is an API guard, never an owner decision. Config contains
locators only. The independently supplied accepted commit and envelope digest
must both match before private input verification or a durable one-shot claim.
Historical evaluator, sequencing, decoding, gates and device code stay unchanged.
"""
import copy
import datetime
import hashlib
import importlib.util
import os
from pathlib import Path
import re
import sys

import alpha_asr_small_campaign as historical
import run_alpha_asr_small_campaign as historical_operator
import run_alpha_asr_small_campaign_v3 as storage
import alpha_asr_expanded_holdout as expanded
from asr_small_arm82_v01 import selector
from asr_small_arm82_v01 import alpha_asr_small_arm82_binding as arm82
from alpha_asr_campaign import Journal, Oracle, file_sha, save_new
from alpha_asr_campaign_device import Device

OPERATOR_ID = 'dora-asr-small-q8-01-operator-v0.1'
EXPERIMENT_ID = 'ASR-SMALL-Q8-01'
AUTHORIZATION = 'OWNER_AUTHORIZED_ASR_SMALL_Q8_01'
MODEL = {'artifact': 'ggml-small-q8_0.bin', 'bytes': 264464607,
         'sha256': '49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f'}
CONFIG_KEYS = frozenset(('repo', 'bundle', 'acceptance', 'work', 'model', 'native', 'runtimeSource', 'adb'))
IDENTITY_FIELDS = arm82.IDENTITY_FIELDS | {'modelArtifact', 'modelBytes', 'modelSha256'}
ADMISSION_PATH = 'docs/evidence/poc-asr-001/asr-small-q8-admission-stage0-v0.1.json'
SOURCE_FILES = frozenset(storage.OPERATOR_FILES) | frozenset((
    'tools/alpha_asr_expanded_holdout.py', 'tools/alpha_asr_prospective_reference_eligibility.py',
    'tools/asr_small_arm82_v01/selector.py',
    'tools/asr_small_arm82_v01/alpha_asr_small_arm82_binding.py',
    'tools/asr_small_q8_v01/alpha_asr_small_q8_binding.py',
    'tools/test_alpha_asr_small_q8_binding.py',
    'docs/evidence/poc-asr-001/asr-small-arm82-01-measured-stage0-v0.1.json', ADMISSION_PATH))
PRIVATE_DOCUMENTS = frozenset((
    'data-authority.json', 'data-freeze.json', 'candidate-inventory.json',
    'reference-bindings.json', 'pool-audit.json', 'selected-manifest.json',
    'transfer-index.json', 'prior-use-authority.json', 'effective-prior-use-authority.json',
    'prior-use-ledger.json', 'consumed-authority.json', 'decode-audit.json',
    'historical-private-baseline.json'))
require, digest, canonical = historical.require, historical.digest, historical.canonical
# Pin imported repository module bytes as loaded, as well as their current files.
_LOADED = {Path(m.__file__).resolve(): file_sha(m.__file__) for m in tuple(sys.modules.values())
           if getattr(m, '__file__', None) and str(m.__file__).endswith('.py')
           and any(Path(m.__file__).as_posix().endswith('/' + n) for n in SOURCE_FILES)}
_LOADED[Path(__file__).resolve()] = file_sha(__file__)


def hash_string(value, length=64):
    return type(value) is str and re.fullmatch('[0-9a-f]{%d}' % length, value) is not None


def beneath(root, relative):
    require(type(relative) is str and relative and '\\' not in relative
            and ':' not in relative and not relative.startswith('/')
            and all(p not in ('', '.', '..') for p in relative.split('/')), 'RELATIVE_PATH_INVALID')
    path = Path(root) / relative
    require(path.resolve().is_relative_to(Path(root).resolve()), 'PATH_ESCAPE')
    storage.no_reparse(path)
    return path


def pin_file(path, pin):
    require(type(pin) is dict and set(pin) == {'bytes', 'sha256'}
            and type(pin['bytes']) is int and pin['bytes'] >= 0
            and hash_string(pin['sha256']), 'FILE_PIN_INVALID')
    arm82.pin_file(Path(path), pin)


def verify_source_files(repo, pins):
    require(type(pins) is dict and set(pins) == SOURCE_FILES, 'SOURCE_PIN_SET')
    for name, pin in pins.items():
        path = beneath(repo, name)
        pin_file(path, pin)
        if path.resolve() in _LOADED:
            require(_LOADED[path.resolve()] == pin['sha256'], 'LOADED_SOURCE_IDENTITY')
    for loaded in _LOADED:
        require(loaded.is_relative_to(Path(repo).resolve()), 'LOADED_SOURCE_LOCATION')


def verify_admission(repo, freeze):
    """Require the published exact q8 admission before any private/model execution."""
    path = beneath(repo, ADMISSION_PATH)
    pin_file(path, freeze['sourceFiles'][ADMISSION_PATH])
    admission = historical_operator.strict_json(path.read_bytes())
    init = admission.get('initPreflight', {})
    static = admission.get('staticVerification', {})
    data_pin = {k: freeze['privateDocuments']['data-freeze.json'][k] for k in ('bytes', 'sha256')}
    require(admission.get('result') == 'PASS / SMALL_Q8_ARTIFACT_INIT_OPERATOR_READY'
            and admission.get('dataCommit') == freeze['dataCommit']
            and admission.get('model') == MODEL
            and init.get('result') == 'PASS_Q8_INIT_AND_LIFECYCLE'
            and init.get('dataCommit') == freeze['dataCommit'] and init.get('dataFreezePin') == data_pin
            and init.get('model') == {k: MODEL[k] for k in ('bytes', 'sha256')}
            and init.get('cleanup') == 'VERIFIED'
            and type(init.get('modelInitAttempts')) is int and init['modelInitAttempts'] == 1
            and type(init.get('asrInference')) is int and init['asrInference'] == 0
            and static.get('result') == 'PASS' and static.get('modelSha256') == MODEL['sha256']
            and type(static.get('tensorCount')) is int and static['tensorCount'] == 479
            and static.get('exactEndOfFile') is True, 'Q8_ADMISSION_REQUIRED')


def load_module(path, name):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def check_semantics(profile, original):
    require(set(profile) == set(original)
            and all(profile[k] == original[k] for k in original if k not in IDENTITY_FIELDS),
            'SEMANTIC_PROFILE_DELTA')
    require({k: profile['model' + k.capitalize()] for k in ('artifact', 'bytes')}
            == {k: MODEL[k] for k in ('artifact', 'bytes')}
            and profile['modelSha256'] == MODEL['sha256'], 'Q8_MODEL_IDENTITY')


def composition(repo, pins):
    require(type(pins) is dict and set(pins) == {'manifest', 'transfer', 'freeze', 'selection'}
            and all(hash_string(v) for v in pins.values()), 'BINDING_PINS')
    c = load_module(Path(repo) / 'tools/alpha_asr_small_campaign.py', '_q8_evaluator')
    op = load_module(Path(repo) / 'tools/run_alpha_asr_small_campaign.py', '_q8_sequencer')
    c.VERSION = 'dora-asr-small-q8-01-campaign-v0.1'
    c.PROFILE_ID = 'dora-asr-small-q8-01-eval-profile-v0.1'
    c.MODEL_SHA, c.MODEL_BYTES = MODEL['sha256'], MODEL['bytes']
    c.MANIFEST_SHA, c.TRANSFER_SHA, c.FREEZE_SHA = (pins[k] for k in ('manifest', 'transfer', 'freeze'))
    original_profile = c.profile
    def profile():
        p = original_profile()
        p.update(modelArtifact=MODEL['artifact'], selectionContractSha256=pins['selection'])
        return p
    c.profile = profile
    op.c = c
    check_semantics(c.profile(), historical.profile())
    c.validate_profile(c.profile())
    return c, op


def check_execution_authority(freeze, expected_commit):
    require(hash_string(expected_commit, 40) and freeze['baseCommit'] == expected_commit
            and hash_string(freeze['dataCommit'], 40) and freeze['dataCommit'] != expected_commit
            and freeze['operatorId'] == OPERATOR_ID and freeze['experimentId'] == EXPERIMENT_ID
            and freeze['branch'] == storage.BRANCH
            and type(freeze['campaignInvocationBudget']) is int and freeze['campaignInvocationBudget'] == 1
            and freeze['retentionDeadline'] == '2026-10-25', 'EXECUTION_AUTHORITY')


def check_storage(config, frozen_storage):
    require(type(frozen_storage) is dict
            and set(frozen_storage) == {'acceptanceRelativePath', 'workRelativePath'}, 'FROZEN_STORAGE_SCHEMA')
    root = Path(os.environ['LOCALAPPDATA']) / 'DORA/private'
    for name in ('acceptance', 'work'):
        expected = beneath(root, frozen_storage[name + 'RelativePath'])
        require(Path(config[name]).resolve() == expected.resolve(), 'FROZEN_STORAGE_BINDING')


def read_documents(root, pins):
    require(type(pins) is dict and set(pins) == PRIVATE_DOCUMENTS, 'PRIVATE_DOCUMENT_SET')
    documents, raw_pins = {}, {}
    for name, pin in pins.items():
        require(type(pin) is dict and set(pin) == {'bytes', 'sha256', 'canonicalSha256'}
                and hash_string(pin['canonicalSha256']), 'DOCUMENT_PIN_INVALID')
        path = beneath(root, name)
        raw_pin = {k: pin[k] for k in ('bytes', 'sha256')}
        pin_file(path, raw_pin)
        documents[name] = historical_operator.bound_json(path.read_bytes(), pin['canonicalSha256'])
        raw_pins[name] = raw_pin
    return documents, raw_pins


def prior_sets(value):
    require(type(value) is dict and all(type(value.get(k)) is list
            for k in ('sources', 'audio', 'participants')), 'PRIOR_AUTHORITY_SCHEMA')
    require(all(type(s) in (tuple, list) and len(s) == 4
                and all(type(v) is str and v for v in s) for s in value['sources'])
            and all(hash_string(h) for h in value['audio'] + value['participants']), 'PRIOR_AUTHORITY_SCHEMA')
    sets = (set(map(tuple, value['sources'])), set(value['audio']), set(value['participants']))
    require(all(len(values) == len(value[k]) for values, k in zip(sets, ('sources', 'audio', 'participants'))),
            'PRIOR_AUTHORITY_DUPLICATES')
    return sets


def validate_data(docs, raw_pins):
    """Recompute only to validate the frozen selection; never write or replace it."""
    require(set(docs) == PRIVATE_DOCUMENTS and set(raw_pins) == PRIVATE_DOCUMENTS, 'PRIVATE_DOCUMENT_SET')
    authority, data = docs['data-authority.json'], docs['data-freeze.json']
    manifest, audit = docs['selected-manifest.json'], docs['pool-audit.json']
    ledger, consumed = docs['prior-use-ledger.json'], docs['consumed-authority.json']
    effective, inventory = docs['effective-prior-use-authority.json'], docs['candidate-inventory.json']
    require(all(d['experimentId'] == EXPERIMENT_ID for d in (authority, data, manifest, ledger)), 'DATA_EXPERIMENT')
    require(authority['providerAuthority'] == expanded.expected_provider_authority()
            and authority['sourceIndexes'] == inventory['sourceIndexes']
            and authority['selection'] == {'ru': 24, 'en': 24, 'order': 'RU24_THEN_EN24',
                'ranking': 'Unchanged frozen selector.rank', 'shortage': 'NO_RANKING_NO_SELECTION',
                'manualReplacement': False, 'durationBalancing': False}, 'DATA_SELECTION_CONTRACT')
    require(authority['retentionDeadline'] == data['retentionDeadline'] == '2026-10-25'
            and authority['purpose'] == 'BOUNDED_INTERNAL_ALPHA_ASR_EVALUATION_ONLY'
            and all(authority[k] is False for k in ('training', 'tuning', 'normalizerChanged',
                    'scoringChanged', 'thresholdsChanged', 'retentionExtension', 'redistribution')),
            'DATA_USE_CONTRACT')
    links = ((data, 'dataAuthoritySha256', 'data-authority.json'),
             (data, 'manifestSha256', 'selected-manifest.json'),
             (data, 'transferSha256', 'transfer-index.json'),
             (data, 'exclusionAuditSha256', 'pool-audit.json'),
             (data, 'inventorySha256', 'candidate-inventory.json'),
             (data, 'priorUseLedgerSha256', 'prior-use-ledger.json'),
             (data, 'consumedAuthoritySha256', 'consumed-authority.json'),
             (manifest, 'dataAuthoritySha256', 'data-authority.json'))
    require(all(obj[field] == digest(docs[name]) for obj, field, name in links), 'DATA_DOCUMENT_CHAIN')
    raw_links = ((authority, 'historicalPriorUsePin', 'effective-prior-use-authority.json'),
                 (authority, 'inventoryPriorUsePin', 'prior-use-authority.json'),
                 (authority, 'sourceAuditPin', 'decode-audit.json'),
                 (data, 'effectivePriorUseAuthorityPin', 'effective-prior-use-authority.json'),
                 (ledger, 'inheritedPriorUsePin', 'effective-prior-use-authority.json'),
                 (ledger, 'dataAuthorityPin', 'data-authority.json'),
                 (ledger, 'inventoryPin', 'candidate-inventory.json'),
                 (ledger, 'auditPin', 'pool-audit.json'),
                 (consumed, 'inheritedPin', 'effective-prior-use-authority.json'),
                 (consumed, 'newLedgerPin', 'prior-use-ledger.json'),
                 (effective, 'historicalSnapshotPin', 'historical-private-baseline.json'))
    require(all(obj[field] == raw_pins[name] for obj, field, name in raw_links), 'DATA_RAW_PIN_CHAIN')
    snapshot = docs['historical-private-baseline.json']
    require(type(snapshot) is dict and effective['baselinePin'] in snapshot.values()
            and type(effective['historicalFilesVerified']) is int
            and effective['historicalFilesVerified'] == len(snapshot), 'HISTORICAL_SNAPSHOT_CHAIN')
    previous = prior_sets(docs['prior-use-authority.json'])
    sources, audio, people = sets = prior_sets(effective)
    require(all(old <= new for old, new in zip(previous, sets))
            and effective['counts'] == dict(zip(('sources', 'audio', 'participants'), map(len, sets))),
            'EFFECTIVE_PRIOR_AUTHORITY')
    references = docs['reference-bindings.json']
    require(type(references) is list and all(type(r) is dict and set(r) == {'source', 'bytesHex'}
            for r in references), 'REFERENCE_BINDING_SCHEMA')
    refs = {tuple(r['source']): bytes.fromhex(r['bytesHex']) for r in references}
    require(len(refs) == len(references) and set(refs) == {selector.source_key(r) for r in inventory['rows']},
            'REFERENCE_BINDING_SET')
    chosen, computed = expanded.audit(inventory['rows'], refs, sources, audio, people,
                                     provider_authority=authority['providerAuthority'])
    require(computed['verdict'] == expanded.PASS and canonical(computed) == canonical(audit), 'AUDIT_IDENTITY')
    expected = [dict(r, rank=i % 24 + 1, selectionKey=selector.rank(r)[0].hex(), eligibilityResult='ELIGIBLE',
                    sampleId='sample-' + hashlib.sha256((r['locale'] + '\0' + r['upstreamRelativePath']).encode()).hexdigest()[:16])
                for i, r in enumerate(chosen)]
    require(canonical(expected) == canonical(manifest['samples']), 'SELECTION_IDENTITY')
    rows = manifest['samples']; historical.validate_selection(rows)
    require(len({selector.source_key(r) for r in rows}) == len({r['audioSha256'] for r in rows}) == 48,
            'SELECTED_IDENTITIES')
    require(manifest['eligiblePoolSha256'] == data['eligiblePoolSha256'] == computed['eligiblePoolSha256']
            and manifest['exclusionSetSha256'] == computed['exclusionsSha256']
            and manifest['priorParticipantsSha256'] == computed['priorParticipantsSha256'], 'SELECTION_AUDIT_CHAIN')
    require(data['selectedCounts'] == manifest['selectedCounts'] == {'ru': 24, 'en': 24}
            and data['scope'] == 'NEW_HOLDOUT_DATA_ONLY_MODEL_NOT_EXECUTED'
            and type(data['asrInferenceBeforeFreeze']) is int and data['asrInferenceBeforeFreeze'] == 0,
            'DATA_FREEZE_STATE')
    require(ledger['developmentState'] == 'NO_SEPARATE_DEVELOPMENT_OR_TUNING_SET_EXISTS'
            and ledger['selectedBeforeAnyInference'] is True
            and ledger['allSelectionsConsumedRegardlessOfExecution'] is True
            and canonical(ledger['records']) == canonical([dict(r, useClass='EVALUATION', consumed=True) for r in rows]),
            'CONSUMPTION_LEDGER')
    expected_sets = (sources | {selector.source_key(r) for r in rows},
                     audio | {r['audioSha256'] for r in rows}, people | {r['participantSha256'] for r in rows})
    require(prior_sets(consumed) == expected_sets, 'CONSUMED_AUTHORITY_UNION')
    return copy.deepcopy(rows)


def claim(work, receipt):
    work = Path(work)
    require(work.is_dir() and not any(work.iterdir()), 'CAMPAIGN_ALREADY_CLAIMED')
    # Exclusive create plus fsync closes the race between separate invocations.
    save_new(work / 'campaign-started.json', receipt)


def check_case(row, position, case):
    arm82.check_case(row, position, case)


def verify_inputs(config, freeze_sha, expected_commit):
    require(type(config) is dict and set(config) == CONFIG_KEYS, 'CONFIG_FIELDS')
    require(all(type(config[k]) is str and config[k] for k in CONFIG_KEYS - {'native'}), 'CONFIG_LOCATORS')
    require(type(config['native']) is dict and set(config['native']) == storage.NATIVE_NAMES
            and all(type(v) is str and v for v in config['native'].values()), 'NATIVE_SET')
    require(hash_string(freeze_sha) and hash_string(expected_commit, 40), 'INDEPENDENT_EXECUTION_PINS')
    repo, bundle, root, work = (Path(config[k]) for k in ('repo', 'bundle', 'acceptance', 'work'))
    for path in (repo, bundle, root, work): storage.no_reparse(path)
    freeze = historical_operator.bound_json(beneath(bundle, 'execution-freeze.json').read_bytes(), freeze_sha)
    check_execution_authority(freeze, expected_commit)
    check_storage(config, freeze['storage'])
    require(storage.git(repo, 'rev-parse', 'HEAD') == expected_commit
            and storage.git(repo, 'rev-parse', 'HEAD^') == freeze['dataCommit']
            and storage.git(repo, 'branch', '--show-current') == freeze['branch']
            and not storage.git(repo, 'status', '--porcelain=v1', '--untracked-files=all'), 'REPOSITORY_IDENTITY')
    require(datetime.date.today() <= datetime.date(2026, 10, 25), 'RETENTION_DEADLINE_EXCEEDED')
    verify_source_files(repo, freeze['sourceFiles'])
    verify_admission(repo, freeze)
    for path in (root, bundle, Path(config['model']), Path(config['runtimeSource']),
                 *map(Path, config['native'].values())):
        require(not work.resolve().is_relative_to(path.resolve())
                and not path.resolve().is_relative_to(work.resolve()), 'WORK_STORAGE_OVERLAP')
    arm82.private_batch([root, work, *(beneath(root, n) for n in PRIVATE_DOCUMENTS)], repo)
    docs, raw_pins = read_documents(root, freeze['privateDocuments'])
    for field, name in (('helperPin', 'tools/alpha_asr_expanded_holdout.py'),
                        ('referenceWrapperPin', 'tools/alpha_asr_prospective_reference_eligibility.py'),
                        ('normalizerPin', 'tools/alpha_asr_eval_text_contract.py'),
                        ('rankPin', 'tools/asr_small_arm82_v01/selector.py')):
        require(docs['data-authority.json'][field] == freeze['sourceFiles'][name], 'DATA_SOURCE_PIN_CHAIN')
    rows = validate_data(docs, raw_pins)
    pins = {k: digest(docs[n]) for k, n in (('manifest', 'selected-manifest.json'),
            ('transfer', 'transfer-index.json'), ('freeze', 'data-freeze.json'), ('selection', 'data-authority.json'))}
    c, op = composition(repo, pins)
    require(digest(c.profile()) == freeze['profileSha256'], 'PROFILE_IDENTITY')
    cases = verify_materialized(root, repo, docs, rows)
    require(freeze['model'] == MODEL, 'MODEL_AUTHORITY')
    model = Path(config['model']); storage.require_model_storage(model, repo)
    require(model.name == MODEL['artifact'], 'MODEL_NAME')
    pin_file(model, {k: MODEL[k] for k in ('bytes', 'sha256')})
    build = historical_operator.bound_json(beneath(bundle, 'build-summary.json').read_bytes(), freeze['buildSha256'])
    require(build['result'] == 'PASS_FROZEN_ARM82_BINARY_ADMISSION' and build['runtimeBuildCount'] == 1
            and set(build['artifacts']) == storage.NATIVE_NAMES, 'BUILD_ADMISSION')
    accepted = historical_operator.strict_json((repo / 'docs/evidence/poc-asr-001/asr-small-arm82-01-measured-stage0-v0.1.json').read_bytes())
    require(build == accepted['runtimeBuild'], 'ACCEPTED_ARM82_BUILD_IDENTITY')
    for name, pin in build['artifacts'].items(): pin_file(Path(config['native'][name]), pin)
    runtime = Path(config['runtimeSource']); storage.no_reparse(runtime)
    require(storage.git(runtime, 'rev-parse', 'HEAD') == c.text.RUNTIME_COMMIT
            and not storage.git(runtime, 'status', '--porcelain=v1', '--untracked-files=all'), 'RUNTIME_IDENTITY')
    public = repo / 'docs/evidence/poc-asr-001'
    decoding = historical_operator.bound_json((public / 'alpha-asr-decoding-profile-stage0-v0.1.json').read_bytes(), c.DECODING_SHA)
    protocol = historical_operator.bound_json((public / 'alpha-asr-measurement-protocol-stage0-v0.1.json').read_bytes(), c.PROTOCOL_SHA)
    governance = historical_operator.strict_json((public / 'alpha-asr-small-evaluation-governance-stage0-v0.1.json').read_bytes())
    require(digest(governance['resourceGates']) == c.GATES_SHA, 'RESOURCE_GATES_IDENTITY')
    require(file_sha(runtime / 'examples/miniaudio.h') == protocol['decoderSha256'], 'DECODER_IDENTITY')
    pin_file(runtime / 'samples/jfk.mp3', freeze['codecSample'])
    isa = freeze['isaAdmission']; cap = isa['capabilities']
    require(isa == accepted['executionFreeze']['isaAdmission'], 'ACCEPTED_ISA_IDENTITY')
    require(cap['readErrno'] == 0 and cap['admitted'] is True and cap['requiredMask'] == 1050114
            and cap['atHwcap'] & 1050114 == 1050114 and isa['cleanupVerified'] is True, 'ISA_NOT_ADMITTED')
    return {'c': c, 'op': op, 'rows': rows, 'cases': cases, 'decoding': decoding, 'freeze': freeze}


def verify_materialized(root, repo, docs, rows):
    manifest, transfer = docs['selected-manifest.json'], docs['transfer-index.json']
    require(len(manifest['bindings']) == 48, 'CASE_BINDINGS')
    cases, files, paths = {}, {}, []
    for position, (row, binding) in enumerate(zip(rows, manifest['bindings']), 1):
        require(binding['sampleId'] == row['sampleId'], 'CASE_BINDING_IDENTITY')
        case = {'row': copy.deepcopy(row), 'position': position}
        for kind in ('audio', 'reference'):
            relative = binding[kind + 'RelativePath']; path = beneath(root / 'materialized', relative)
            require(relative not in files, 'DUPLICATE_MATERIALIZED_PATH')
            pin = binding[kind]; pin_file(path, pin)
            require(pin['sha256'] == row['audioSha256' if kind == 'audio' else 'referenceTextSha256'], 'CONTENT_BINDING')
            if kind == 'audio': require(pin['bytes'] == row['audioByteLength'], 'AUDIO_SIZE')
            files[relative] = pin; paths.append(path); case[kind] = path; case[kind + 'Pin'] = pin
        cases[row['sampleId']] = case
    expected = [{'path': p, **pin} for p, pin in sorted(files.items())]
    require(canonical(transfer['files']) == canonical(expected), 'TRANSFER_BINDING_SET')
    arm82.private_batch(paths, repo)
    return cases


def driver(inputs, config, device, oracle):
    op = inputs['op']; repo = Path(config['repo'])
    class BoundDriver(op.SmallDriver):
        def case(self, row, position):
            item = inputs['cases'].get(row['sampleId']); check_case(row, position, item)
            for kind in ('audio', 'reference'): pin_file(item[kind], item[kind + 'Pin'])
            arm82.private_batch([item['audio'], item['reference']], repo)
            reference = item['reference'].read_bytes()
            require(hashlib.sha256(reference).hexdigest() == row['referenceTextSha256'], 'REFERENCE_CHANGED')
            destination = self.work / 'cases' / f'{position:02d}'
            self.device.execute(item['audio'], row['locale'], destination)
            return op.read_attempt(destination, row, 'SUCCEEDED', self.oracle, reference.decode('utf8'))
    return BoundDriver({k: config[k] for k in ('work', 'runtimeSource')}, device, inputs['decoding'], oracle)


def campaign(config, freeze_sha, expected_commit, authorization=None):
    require(authorization == AUTHORIZATION, 'MEASURED_EXECUTION_NOT_AUTHORIZED')
    config = copy.deepcopy(config)
    inputs = verify_inputs(config, freeze_sha, expected_commit); c, op = inputs['c'], inputs['op']
    work = Path(config['work'])
    claim(work, {'operatorId': OPERATOR_ID, 'executionFreezeSha256': freeze_sha,
                 'profileSha256': digest(c.profile()), 'baseCommit': expected_commit, 'apiInvocationCount': 1})
    journal = device = None; entered = False
    try:
        oracle = Oracle(work / 'oracle'); oracle.compile()
        journal = Journal(work / 'attempts.sqlite')
        require(not journal.records(), 'EXISTING_ATTEMPTS_REQUIRE_OWNER_REVIEW')
        artifacts = {**config['native'], 'model.bin': config['model']}
        identity = digest({n: {'sha256': file_sha(p), 'bytes': Path(p).stat().st_size} for n, p in artifacts.items()})
        class BoundDevice(Device):
            def connect(self):
                facts = super().connect()
                expected = inputs['freeze']['isaAdmission']['device']
                require(all(facts['properties'].get(k) == v for k, v in expected.items()
                            if k != 'ro.product.cpu.abi'), 'PHYSICAL_ISA_DEVICE_BINDING')
                require(int(facts['properties']['ro.build.version.sdk']) >= 28, 'ANDROID_API_BELOW_28')
                return facts
        device = BoundDevice(config['adb'], artifacts, identity)
        runner = driver(inputs, config, device, oracle); entered = True
        result = op.run_sequence(inputs['rows'], runner, journal,
                                 lambda: verify_inputs(config, freeze_sha, expected_commit))
    except Exception:
        cleanup = 'UNVERIFIED'
        if device is not None and not entered:
            try: cleanup = device.cleanup()
            except Exception: pass
        result = c.evaluate(c.profile(), inputs['rows'], [], cleanup, False)
    finally:
        if journal is not None: journal.close()
    save_new(work / 'terminal-aggregate.json', result)
    return result
