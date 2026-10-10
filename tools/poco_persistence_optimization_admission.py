"""Exact 9c6b756 successor admission. Historical manifests/evidence remain immutable.

Every override has reviewed before/after hashes; inherited seals see only the exact
parent bytes after current bytes were verified. Current product/graph checks still
run in the parent validator. CI never seals or admits dirty/working verification.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re

import poco_persistence_diagnostic_admission as c2

ROOT=Path(__file__).resolve().parents[1]
BASE='9c6b7563eca5b6c003ad2e192a887b5df2630e2d'
PARENT='tools/validate_poco_acceptance.py'
MANIFEST='docs/contracts/persistence-optimization-8.6c3.json'
INVENTORY='docs/contracts/persistence-8.6c3-device-tests.json'
DIAGNOSTIC_INVENTORY='docs/contracts/protected-diagnostic-8.6c3-tests.json'
OLD_INVENTORY='docs/contracts/logical-recovery-8.5-device-tests.json'
CI_ROUTE='    import persistence_optimization_ci_profile as c3\n    workflow = c3.normalize(workflow)\n'
ADDITIONS={
    'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/diagnostic/DiagnosticSuccessorLoaderTest.kt',
    'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/diagnostics/PersistenceOptimizationBenchmarkTest.kt',
    'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/diagnostics/PersistenceOptimizationProbe.kt',
    'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/persistence/ProtectedReadOnlyAcquisitionTest.kt',
    'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/persistence/ProtectedReadOnlyVaultTest.kt',
    'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/persistence/journal/RoomCatalogReservationReuseTest.kt',
    'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/DiagnosticProtectedOwnership.kt',
    'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/DiagnosticSuccessorPolicy.kt',
    'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/ProtectedReadOnlyAcquisition.kt',
    'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/ProtectedReadOnlyVault.kt',
    'android/core/audio/src/test/kotlin/com/monumentogram/dora/audio/CatalogOrderOptimizationTest.kt',
    'android/core/audio/src/test/kotlin/com/monumentogram/dora/audio/persistence/DiagnosticSuccessorPolicyTest.kt',
    'docs/adr/ADR-RECORDING-010-successor-protection-readonly-acquisition.md',
    'docs/adr/ADR-PERSISTENCE-004-linear-order-and-reservation-snapshot.md',
    'docs/superpowers/plans/2026-10-09-alpha-persistence-optimization.md',
    'docs/evidence/persistence-optimization-8.6c3/README.md',
    'docs/evidence/persistence-optimization-8.6c3/RESULT.md',
    'docs/evidence/persistence-optimization-8.6c3/benchmark-protocol.md',
    'docs/evidence/persistence-optimization-8.6c3/catalog-reuse-proof.md',
    'docs/evidence/persistence-optimization-8.6c3/benchmark-comparison.json',
    'docs/evidence/persistence-optimization-8.6c3/verification.json',
    'docs/evidence/persistence-optimization-8.6c3/privacy-audit.json',
    'tools/persistence_optimization_c3_results.py',
    'tools/run_persistence_optimization_c3.py',
    'tools/test_persistence_optimization_c3.py',
    'tools/test_run_persistence_optimization_c3.py',
    'tools/poco_non_battery/test_successor_policy.py',
    'tools/poco_persistence_optimization_admission.py',
    'tools/persistence_optimization_ci_profile.py',
    'tools/run_protected_diagnostic_c3.py',
    'tools/test_logical_recovery_persistence_optimization.py',
    INVENTORY,DIAGNOSTIC_INVENTORY,
}
OVERRIDES={
    PARENT,'.github/workflows/android-ci.yml','tools/logical_recovery_ci_profile.py',
    'tools/test_logical_recovery_final_deletion.py',
    'tools/poco_non_battery/protected_policy.py',
    'tools/poco_non_battery/diagnostic_isolation.py',
    'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/diagnostic/ProtectedHistoricalVaultTest.kt',
    'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/RecoveryAudioBridge.kt',
    'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/DiagnosticJournalFactory.kt',
    'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/DiagnosticPolicyLoader.kt',
    'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/DiagnosticSourcePolicy.kt',
    'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/journal/RoomAudioJournal.kt',
    'android/core/audio/src/test/kotlin/com/monumentogram/dora/audio/CatalogOrderWorkTest.kt',
}
PARENT_REPLACEMENTS={
    '    reduced_contract = reduced.load(root)\n':
        '    import poco_persistence_optimization_admission as c3\n    c3_contract = c3.validate_checkout(root)\n    reduced_contract = c3.load_reduced(root, c3_contract)\n',
    '    def before_protected(path, raw):\n':
        '    def before_protected(path, raw):\n        raw = c3.normalize(root, path, raw, c3_contract)\n',
    c2.ROUTE: '    approved |= c3.approved_paths()\n',
    "    persistence.validate_device_inventory((root / ci.INVENTORY).read_bytes().replace(b'\\r\\n',b'\\n'),\n                                         tests, contract['files'][ci.INVENTORY])\n":
        '    c3.validate_inventories(root, tests, c3_contract)\n',
}


def identity():
    return dict(schemaVersion=1,stage='8.6C.3',parent=BASE,
                admission='SCOPED_OPTIMIZATION_AND_PROTECTED_READONLY',rootCause='NOT_PROVEN',
                physicalAdmission='NOT_GRANTED',stage86='NOT_READY')


def approved_paths():
    return ADDITIONS|OVERRIDES|{MANIFEST}|c2.ADDITIONS|{c2.MANIFEST}


require=c2.require
digest=c2.digest
read_regular=c2.read_regular


def git(root,*args):
    import validate_encrypted_persistence as persistence
    return persistence.git(root,*args)


def validate_delta(paths,*,complete):
    approved=ADDITIONS|OVERRIDES|{MANIFEST}
    require(set(paths)<=approved and (not complete or set(paths)==approved),'Unapproved or missing C3 paths')


def validate_history(root):
    require(git(root,'merge-base',BASE,'HEAD').decode().strip()==BASE,'Wrong exact C3 parent')
    for line in git(root,'rev-list','--reverse','--parents',BASE+'..HEAD').decode().splitlines():
        parts=line.split()
        require(len(parts)==2,'C3 history must remain linear')
        validate_delta(git(root,'diff','--name-only','--no-renames',parts[1],parts[0]).decode().splitlines(),complete=False)


def upgrade_parent(original):
    current=original.replace(b'\r\n',b'\n')
    for before,after in PARENT_REPLACEMENTS.items():
        require(current.count(before.encode())==1,'C3 parent hook anchor changed')
        current=current.replace(before.encode(),after.encode(),1)
    return current


def validate_parent_route(current,original):
    require(current.replace(b'\r\n',b'\n')==upgrade_parent(original),'C3 parent changed beyond exact hooks')


def verify_parent(root,*,complete):
    validate_history(root)
    parent_paths=set(git(root,'ls-tree','-r','--name-only',BASE).decode().splitlines())
    require(not (ADDITIONS|{MANIFEST}) & parent_paths and OVERRIDES<=parent_paths,'C3 path roles differ from parent')
    actual=set(git(root,'diff','--name-only','--no-renames',BASE).decode().splitlines())
    actual|=set(git(root,'ls-files','--others','--exclude-standard').decode().splitlines())
    validate_delta(actual,complete=complete)
    validate_parent_route(read_regular(root,PARENT),git(root,'show',BASE+':'+PARENT))
    path='tools/logical_recovery_ci_profile.py'
    current=read_regular(root,path).replace(b'\r\n',b'\n')
    require(current.count(CI_ROUTE.encode())==1 and current.replace(CI_ROUTE.encode(),b'',1)==
            git(root,'show',BASE+':'+path).replace(b'\r\n',b'\n'),'C3 CI normalization hook changed')
    import persistence_optimization_ci_profile as ci
    workflow=read_regular(root,'.github/workflows/android-ci.yml').decode().replace('\r\n','\n')
    original_workflow=git(root,'show',BASE+':.github/workflows/android-ci.yml').decode().replace('\r\n','\n')
    require(ci.normalize(workflow)==original_workflow and ci.upgrade(original_workflow)==workflow,
            'C3 workflow changed beyond exact successor inventory and diagnostic gate')


def normalize_override(current,original,entry):
    require(type(entry) is dict and set(entry)=={'before','after'},'Invalid C3 override shape')
    require(digest(current)==entry['after'] and digest(original)==entry['before'],'C3 override seal mismatch')
    return original.replace(b'\r\n',b'\n')


def normalize(root,path,raw,contract):
    if path not in OVERRIDES:
        return raw
    return normalize_override(raw,git(root,'show',BASE+':'+path),contract['overrides'][path])


def load(root):
    value=json.loads(read_regular(root,MANIFEST),object_pairs_hook=c2.unique_object)
    fixed=identity()
    require(type(value) is dict and set(value)==set(fixed)|{'files','overrides'} and
            all(type(value.get(k)) is type(v) and value[k]==v for k,v in fixed.items()),'C3 admission identity changed')
    require(type(value['files']) is dict and set(value['files'])==ADDITIONS and
            type(value['overrides']) is dict and set(value['overrides'])==OVERRIDES,'C3 exact scope changed')
    for path,expected in value['files'].items():
        require(type(expected) is str and re.fullmatch('[0-9a-f]{64}',expected) and
                digest(read_regular(root,path))==expected,'C3 addition seal mismatch: '+path)
    for path in OVERRIDES:
        normalize(root,path,read_regular(root,path),value)
    return value


def load_reduced(root,contract):
    import poco_reduced_admission as reduced
    raw=read_regular(root,reduced.MANIFEST)
    require(digest(raw)==digest(git(root,'show',BASE+':'+reduced.MANIFEST)),'Historical reduced manifest changed')
    value=json.loads(raw,object_pairs_hook=c2.unique_object)
    require(set(value['overrides'])==reduced.OVERRIDES and value.get('protectedBase')==reduced.PROTECTED_BASE and
            set(value.get('protectedOverrides',{}))==reduced.PROTECTED_OVERRIDES,'Historical reduced scope changed')
    for path,expected in value['files'].items():
        reduced.validate_added_path(path)
        require(digest(normalize(root,path,read_regular(root,path),contract))==expected,
                'Historical reduced seal mismatch: '+path)
    return value


def validate_c2(root,contract):
    raw=read_regular(root,c2.MANIFEST)
    require(digest(raw)==digest(git(root,'show',BASE+':'+c2.MANIFEST)),'Historical C2 manifest changed')
    value=json.loads(raw,object_pairs_hook=c2.unique_object)
    require(set(value)==set(c2.IDENTITY)|{'files'} and all(value[k]==v for k,v in c2.IDENTITY.items()) and
            set(value['files'])==c2.ADDITIONS,'Historical C2 scope changed')
    for path,expected in value['files'].items():
        require(digest(normalize(root,path,read_regular(root,path),contract))==expected,'Historical C2 seal mismatch: '+path)
    # Retain the original additive route check against C2's own immutable parent.
    c2.validate_parent_route(normalize(root,PARENT,read_regular(root,PARENT),contract),
                             git(root,'show',c2.BASE+':'+PARENT))


def validate_inventory_projection(old,current,discovered):
    import run_encrypted_persistence_device as device
    require(set(current)==set(old)=={'schema_version','tests','no_credential_tests'} and
            current['schema_version']==old['schema_version']==1,'C3 inventory schema changed')
    previous,controls=device.validate_inventory(old)
    actual,current_controls=device.validate_inventory(current)
    require(previous<actual and current_controls==controls and current['no_credential_tests']==old['no_credential_tests'] and
            actual==set(discovered),'C3 inventory omits old tests/controls or differs from current sources')


def validate_inventories(root,discovered,contract):
    import validate_encrypted_persistence as persistence
    import run_protected_diagnostic_c3 as diagnostic
    raw=read_regular(root,OLD_INVENTORY)
    require(digest(raw)==digest(git(root,'show',BASE+':'+OLD_INVENTORY)),'Historical inventory changed')
    current=read_regular(root,INVENTORY)
    require(digest(current)==contract['files'][INVENTORY],'C3 inventory seal mismatch')
    validate_inventory_projection(json.loads(raw),json.loads(current),discovered)
    directory=root/'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/diagnostic'
    names=persistence.discover_device_tests({p.name:p.read_text(encoding='utf-8') for p in directory.glob('*.kt')})
    selected=diagnostic.validate_inventory(json.loads(read_regular(root,DIAGNOSTIC_INVENTORY)))
    require(selected==names,'C3 diagnostic inventory differs from source')


def validate_checkout(root=ROOT):
    verify_parent(root,complete=True)
    contract=load(root)
    validate_c2(root,contract)
    return contract


def seal(root=ROOT):
    require(not(os.environ.get('GITHUB_ACTIONS') or os.environ.get('GITHUB_EVENT_NAME')),'CI cannot seal C3 admission')
    verify_parent(root,complete=False)
    value={**identity(),'files':{p:digest(read_regular(root,p)) for p in sorted(ADDITIONS)},
           'overrides':{p:dict(before=digest(git(root,'show',BASE+':'+p)),after=digest(read_regular(root,p))) for p in sorted(OVERRIDES)}}
    destination=root/MANIFEST
    require(not destination.is_symlink() and destination.parent.resolve().is_relative_to(root.resolve()),'C3 manifest path unsafe')
    destination.write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')
    validate_checkout(root)


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--seal',action='store_true')
    parser.add_argument('--working',action='store_true',default=None)
    args=parser.parse_args()
    if args.seal:
        seal(); print('SEALED C3 exact source admission; inherited validation remains required')
    else:
        import validate_poco_acceptance as parent
        parent.validate_checkout(allow_working=args.working)
        print('PASS C3 repository admission; physical admission NOT_GRANTED; Stage8.6 NOT_READY')
