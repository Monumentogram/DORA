"""Admit sealed Stage 8.6 evidence and the explicit Stage 8.6A close-cycle correction."""
import argparse
import hashlib
import json
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BASE = 'b8cac85f30f446d98c1a6b1d21490d8c10bc5b41'
BRANCH = 'stage/8.6-poco-recording-acceptance'
EVIDENCE = 'docs/evidence/poco-recording-8.6/'
SELF = 'tools/validate_poco_acceptance.py'
PARENT = 'tools/validate_logical_recovery.py'
GOVERNANCE = 'tools/validate_poc_recovery_governance.py'
ROUTE = '''    import validate_poco_acceptance as successor
    if successor.candidate(root):
        return successor.validate_checkout(root, allow_working=allow_working)
'''
STATUS_ROUTE = '''    import poco_alpha_battery_defer as alpha
    if current.startswith(alpha.PREFIX):
        current = alpha.normalize_document('docs/DORA_MVP1_STAGE_STATUS.md', current.encode()).decode()
'''
REDUCED_STATUS_ROUTE = """    import poco_reduced_admission as reduced
    if current.startswith(reduced.PREFIX):
        current = reduced.normalize_document('docs/DORA_MVP1_STAGE_STATUS.md', current.encode()).decode()
"""
# Exact content seals; additions/edits require a new reviewed evidence revision.
SEALED = {'docs/evidence/poco-recording-8.6/attempts.json': '41993ad345629651577ad94adfd80197d0fdd790cf391759beb3df2825c9764e', 'docs/evidence/poco-recording-8.6/batterystats-source-summary.txt': 'da3a563e0d942f48c2809ced7fe98a370018ffab5e05e7e11153915755e5ad07', 'docs/evidence/poco-recording-8.6/cleanup-diagnostic-files.txt': 'dd8375c9e37ee04796a5237f78923faca46245a35668894177cb2b8a85f5ad3e', 'docs/evidence/poco-recording-8.6/device-preflight.json': '2411808feedbcabef0754b5a59302bb2255efd534080d4411f9fb71a3982ea66', 'docs/evidence/poco-recording-8.6/EnergyProbe.java': '8e45cc34d14d9bd53b7f2a26edc4a1b7c822c1d05b104b29075abbeeb4ec856e', 'docs/evidence/poco-recording-8.6/fixture-manifest.json': 'c8f567882b06e4e03b0a00f03b5aa59921ac0f5f69cde167b40df3d5340a8408', 'docs/evidence/poco-recording-8.6/framework-energy-probe-screen-off.txt': 'bee070cd9160fa644e1fd366c9dbe28ea5ac9fab430e4c344111dec81a9bbd33', 'docs/evidence/poco-recording-8.6/framework-energy-probe.txt': '86488169581ac12fefb6ebf655608841c5be326633b724863254bf123b1dd9d4', 'docs/evidence/poco-recording-8.6/oracle-result.txt': '7934c2f52f3d66c9193320dd44ce801ef62716c4a3323a5b471d25e6586b2a56', 'docs/evidence/poco-recording-8.6/perfetto-consumer-summary.json': '43445acea8eccb372d834ee31dc3f614f2e1e5c4718c0dcc5fc5581b44d579c6', 'docs/evidence/poco-recording-8.6/perfetto-summary.json': 'ee9319553dc3f5846ccca191d3be01cf217a2557353885a811d6952a7bfa349d', 'docs/evidence/poco-recording-8.6/power-preflight-consumers.pbtxt': '58c22bb79ffebba89dc7f690df7fb7dd9dba8f6d8484901aa176b8863f85d613', 'docs/evidence/poco-recording-8.6/power-preflight-rails.pbtxt': 'ecce9d7b3fd2233ed862f04bb0aeb8947eedd6e87a9efa4a7ebe1139946da8e3', 'docs/evidence/poco-recording-8.6/protocol.json': '873df5993a2d14d0c054e0b9aa3afe68a1dc8c53e69d686d476cbb080c4e2364', 'docs/evidence/poco-recording-8.6/README.md': 'b3b8a9780d0e694982465d03c990e087a18598ebd431f3da9466d88fe53d763e', 'docs/evidence/poco-recording-8.6/result.json': '4cba9826f1f3afe8b6a40bff36f5cb59ad73687531477368a49a679b63a1591e', 'docs/evidence/poco-recording-8.6/screen-before.txt': '47af5ef8b5c76675798d0d3fd9e41b050f21c4c4472fd057d0bf02809223a8be', 'docs/evidence/poco-recording-8.6/screen-off-end.txt': 'b11c0847e108cea10d11e68469ab13c37eca4e6b43babd86b190dd36708c9c4a', 'docs/evidence/poco-recording-8.6/screen-off.txt': '8c2e9f24c621a6ff13aa55f27fde0aca2c8a737015b6a6093f28dd2778459c17', 'tools/test_logical_recovery_poco.py': '95b404a752b24850f6ffcfff15e18b40f15d200664279bc16f4eb47b034134b6'}


# Separately reviewed comparative preflight; original SEALED bytes stay unchanged.
COMPARATIVE_SEALED = {'docs/evidence/poco-comparative-battery-8.6/A-01-disposition.json': '3a84c57db534b00172708aea697e8d82bc199cdd11db0b664334c595afc1112e', 'docs/evidence/poco-comparative-battery-8.6/A-02-disposition.json': 'de9de13908252b8f0d230fcd71664632ec8a93311921b2b43cd357c99eb728cc', 'docs/evidence/poco-comparative-battery-8.6/A-03-disposition.json': '270dcfd89c40aacb1a439ae67a1bfde3b7ed4ee3c3ae54f7c4b21e4910b1b328', 'docs/evidence/poco-comparative-battery-8.6/A-04-disposition.json': '3bc5ed71dd6613a196fcc155176319640907abb1e4b775703fcbaf2b195dcfab', 'docs/evidence/poco-comparative-battery-8.6/A-04-samples.jsonl': '15d2ad47025f7a8f526d77f62743423cb6c92a4f2dee1b30adb6ba635293e1d4', 'docs/evidence/poco-comparative-battery-8.6/accubattery-final-package.json': '47df2ce7d7f46fdb6c6f9b394ae1e7c7f21197f12d0c5086e06cef6fc980414e', 'docs/evidence/poco-comparative-battery-8.6/B-01-disposition.json': '6c8d1d1f9b679cf0c64dd50b7b7510229feb418009b7c183d9908e14b17a6f5e', 'docs/evidence/poco-comparative-battery-8.6/B-01-samples.jsonl': '1daa888fab484dee313c088ba155028cf5f2d3fc9a43cb01244280cb8a0b4214', 'docs/evidence/poco-comparative-battery-8.6/baseline-build-identity.json': 'f3a6c854a5d2897e2bd9bbfe66bd3b903c31ba1d2071c5ab7357002b6eb76bdc', 'docs/evidence/poco-comparative-battery-8.6/C-01-disposition.json': '519f19845d9c915a20aa69a67419f81b1922cbcaa72ceed5f4df5256a5438071', 'docs/evidence/poco-comparative-battery-8.6/C-01-readback-deletion.json': 'b590a9e521ddb05773b5373e26c3a19a005164c346e8cd7b16c6bb284c6da3e3', 'docs/evidence/poco-comparative-battery-8.6/C-01-samples.jsonl': 'fb539dcf0b2861e521bc18c30f2fc81b89aebf30531edd84ed1ec791f833775d', 'docs/evidence/poco-comparative-battery-8.6/comparative-gate-disposition.json': '1e9f9c4e3a0dc9b6c21e44ff1a136dc8cef764a5eaca8108650994520ea47d36', 'docs/evidence/poco-comparative-battery-8.6/observer-build-identity.json': '030f55daa1ae52e9ad1ef8e9053ebbc27ffb83c4afef91561c706848b7f378d0', 'docs/evidence/poco-comparative-battery-8.6/publication-audit.json': 'a73a8026f8e4114610b5219628b5ba3d857909f27bcc39a3edcfdae41a78d5fe', 'docs/evidence/poco-comparative-battery-8.6/README.md': '8febf063e04960d3bbdb52852b19b63326c837032a8e6db7fad5e84ec048e704', 'docs/evidence/poco-comparative-battery-8.6/result.json': 'c9f3648330a86e1701aadcc783f097f5b4a24ec137a7e33b8eb78ba23faf3190', 'docs/evidence/poco-comparative-battery-8.6/short-probe-protocol-v2.json': 'bcd3d6e1e12948326a8df59cad1849cb7f2757edad3108d4370b339012fc0f79', 'docs/evidence/poco-comparative-battery-8.6/short-probe-protocol.json': '4515858471d178835db83b68595e93c993f5b82fd742f9fb39a137ae52988e82', 'docs/evidence/poco-comparative-battery-8.6/sysfs-readonly-inventory.json': 'd12e6921492cfa28b58fcdae05b57606a3ec7fa499d06c5afa4edf1f7e14ca28', 'docs/evidence/poco-comparative-battery-8.6/test-apk-payload-verification.json': 'bdc433e26af0e453bf19e05eba189191b73356a8200881afbb3797e1e15e2351', 'docs/evidence/poco-comparative-battery-8.6/test-helper-cleanup.json': 'ead5136cfd4299a4c7088ba0cfe2fc4c433d49518d8a76ca556df31bad6a39bb', 'docs/evidence/poco-comparative-battery-8.6/uid-additional-diagnostics.json': '8e6907339df0932a0e349a383f00c854792ab4f7ed69e793453cad9e778e93a6', 'tools/poco_comparative_battery/baseline/AndroidManifest.xml': '98ce55693bfcadfdd15679358c6798e60d4e076230ff953f775a2f442eed85d6', 'tools/poco_comparative_battery/baseline/CaptureService.java': '769a4b7cdff2ccf76e715bfdaa4f69632d8de44285129ff154b6b8d4c77dc677', 'tools/poco_comparative_battery/baseline/MainActivity.java': '903872ce661d2a5907d5b446838646def9fbed6499abed68cd4c882943b927c7', 'tools/poco_comparative_battery/build.py': 'c17401e5f1f5890cf3d46767bcb4196ab4686dd679173d40422192506273e9e0', 'tools/poco_comparative_battery/comparative_parser.py': 'e328eac2e57d772df7296908d08fd7f27ac7e2863b6ae37626b003ed855c098c', 'tools/poco_comparative_battery/observer/AndroidManifest.xml': 'cf7532e967dae6d30ccaf7541238e5b592199c801964d744c50f86e343e9c48e', 'tools/poco_comparative_battery/observer/ProbeInstrumentation.java': '1f7e38b0677a08db58bb29e8bc79dcc68e64b04ced33c656ce9dc4b87c932dfd', 'tools/poco_comparative_battery/test_comparative_parser.py': '541d2250637c114a382be5d272428c47109a527230fc38b668067c7c5b9aca39', 'tools/test_logical_recovery_poco_comparative.py': '103be4b43eeb93648cf9886d05c0b36216fbeac1c95320d3b62c88c7317154ef'}
COMPARATIVE = 'docs/evidence/poco-comparative-battery-8.6/'

# Explicit harness-only successor. Historical seals are never replaced.
REMEDIATION_SEALED = {'.github/workflows/android-ci.yml': '62a50fc609d7c6c15b50cf7f5b56d8a32611b174a443470af7cb9b9907169cd2', 'tools/logical_recovery_ci_profile.py': 'fd0ce7803067710efd00b45b88ac26657076129f3c0cbd6f28d13bbfe69de39c', 'tools/run_encrypted_persistence_device.py': 'b94b634f93773fe3c30614d1151d1ce1ca6b3b0a502716ca66df2c727ee538f4', 'tools/persistence_instrumentation_diagnostics.py': 'd18fdaa4268e128055986d4642875fd1e599673acfdae3fd16a6c7446206e4ba', 'tools/poco_remediation_ci_profile.py': 'f171c57ea438f17366623d7e6699ef0560deccb955ccb2afe429501daa07b821', 'tools/test_logical_recovery_stream_diagnostics.py': '83c68f7a5012be0b04fa51e05bf7f88352f4e21c9c4d74494024e70dc21f3c01', 'docs/superpowers/plans/2026-10-04-stage86-remediation.md': 'edae98811f248e8efd669f4eab9658135598062947de298c1c78d3b120f338a8'}
REMEDIATION_OVERRIDES = {'.github/workflows/android-ci.yml', 'tools/logical_recovery_ci_profile.py'}
REMEDIATION_ROUTE = '    import poco_remediation_ci_profile as remediation\n    workflow = remediation.normalize(workflow)\n'

# Owner-approved additive governance; historical sealed evidence is not replaced.
ALPHA_DEFER_SEALED = {'docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md': '754fa358de7de4e814fcc27c18104639c3597b1d0336dbf58a85139356c9582a', 'docs/DORA_MVP1_PRODUCT_DECISIONS.md': '3649ad24d9e126f2ea2e8ce0f47b9656898a93210441082024f99bf6148219dc', 'docs/DORA_MVP1_STAGE_STATUS.md': '2748bb3334a308ef973967ae313a709c210edb51a035c0d819bf2ae6f60d21a9', 'docs/adr/ADR-PERF-002-alpha-battery-efficiency-deferral.md': 'bf87b64b9039df7812d992447187c3af22680fc597f302e19069e8f7ff3e946b', 'docs/evidence/poco-battery-alpha-deferral/ci-readiness.json': 'd636d061e4fc510311c2f5067e28320bb90e240ba83b07a19c1255e97ba32cc1', 'docs/evidence/poco-battery-alpha-deferral/freshness-attempt-ledger-v2.json': '619da9d3bbf71b7cf0fa7ea2543bf72133a2d5f1e21910547e9e97f30c8cbb4d', 'docs/evidence/poco-battery-alpha-deferral/freshness-isolated-01-disposition.json': '5e7f39c081291e7154093c91f8e4cbe9672b69949a47c2a3e1893d863e39d8aa', 'docs/evidence/poco-battery-alpha-deferral/freshness-isolated-02-evaluation.json': '4c04440feb700c9387d1b4c97a49dbb1af7acf322f3c634a00457543906d90bd', 'docs/evidence/poco-battery-alpha-deferral/freshness-isolated-02.jsonl': 'd3462d5284d70ee33bdff4a6233c315c61c7ad447bdf71fdbb2d1fcb31c6d43b', 'docs/evidence/poco-battery-alpha-deferral/isolated-freshness-protocol-v1.json': '9195c17cb6955a89acc30b304707e52874a9da4582e2150c47305698696ab2c4', 'docs/evidence/poco-battery-alpha-deferral/owner-defer-device-cleanup.json': '5f3c846920ff885ea50de84e16a478d0b1d31b6f876bbea2c370e72cf477fda9', 'docs/evidence/poco-battery-alpha-deferral/repeat-owner-recordings-preflight.json': '24be2291a296628de5025e732315533675a10de8e739f3e2f681632762ec3d61', 'docs/evidence/poco-battery-alpha-deferral/summary.json': '3346a94b78d85a935b3151170d877d8b2e712820e4de4c978f2a947d182ad920', 'docs/governance/poco-battery-alpha-deferral.json': '784d8d0e392338e28a5db965b656bd8dcaef93aaa0c29ac2524a2ac017ecd4c3', 'tools/poco_alpha_battery_defer.py': '9e1bc43cc8479db8d097033a8cbff277a2cbd6de3523d396160808a6391e9fca', 'tools/test_logical_recovery_alpha_battery_defer.py': '4ecfc407192bdf1df4bd9ca220dffec825088c7511736499af64f5da6a56f559'}


# Stage 8.6A has exactly two reversible historical source overrides. Old seals remain intact.
FINAL_DELETION_SEALED = {'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/persistence/EncryptedAudioVaultDeletionFailureTest.kt': 'c2fabd69a11e0c129e011ade86878b2a3f99ba7e2cb41a72f06abe3c115bbddb', 'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/persistence/RoomInvalidationProbe.kt': 'c628f33ef03d38595de0c41f9875729c7974f3e45bd2ae080b4990000d84db64', 'android/core/audio/src/main/kotlin/com/monumentogram/dora/audio/persistence/journal/RoomAudioJournal.kt': '3a27cb7af7afd17d3b7245aacd61327d4311816732a999b370ad1a0e4d913d79', 'docs/adr/ADR-PERSISTENCE-003-room-invalidation-worker-ownership.md': '3be19d9b67654d3dbd1b37abef475b88597d45fe25898b7f7b1d648f17f43f25', 'docs/evidence/api28-final-deletion-8.6a/README.md': '134fc824a7317f0d4b5befc608de2f0d3701a67cb33d94c94ffc9cd03c213818', 'docs/evidence/api28-final-deletion-8.6a/api28-full.json': '16beb831e4ab7009358871e178fa288859599cc646ab32bdc147af22df21c2d5', 'docs/evidence/api28-final-deletion-8.6a/api28-green-class-01.json': '6de896663096ba3c1362d1e09aafe743aaa917d214f2dde86bae82e191e88e9f', 'docs/evidence/api28-final-deletion-8.6a/api28-green-target-02.json': 'f4e22ad8059fe34372d3f8e25e1da212e241f2b6f2e732a7d31421f9e00d646e', 'docs/evidence/api28-final-deletion-8.6a/api28-green-target-03.json': 'f4e22ad8059fe34372d3f8e25e1da212e241f2b6f2e732a7d31421f9e00d646e', 'docs/evidence/api28-final-deletion-8.6a/api28-green-target-04.json': 'f4e22ad8059fe34372d3f8e25e1da212e241f2b6f2e732a7d31421f9e00d646e', 'docs/evidence/api28-final-deletion-8.6a/api28-recovery.json': '8975496e5828a890731ed57d00eb08b18c8f0cdaecdaac8ebdfe9f5cfec415aa', 'docs/evidence/api28-final-deletion-8.6a/api36-full.json': '52f552c2ad7be1bf36b1b65c4542d71ad129e3e3e919ad344eef82f7de02045b', 'docs/evidence/api28-final-deletion-8.6a/api36-recovery.json': 'a8333ad18031bfaba3f3042ce90244036082ec2e88927e36042301120d18d178', 'docs/evidence/api28-final-deletion-8.6a/forced-before-probe.kt.txt': 'fc4daa7be5066f0f8ddb37e195b702bf5c80f01ed466629dbcfd48263a7d954d', 'docs/evidence/api28-final-deletion-8.6a/forced-before-trace.txt': 'e44ed2cbd2f4b032da148f8ae9f4a9bd69bae806d49bc88774291fe5b986e931', 'docs/evidence/api28-final-deletion-8.6a/local-validation.json': 'da15033021f5783e3e48f3bb61022e27448c4adcd4d22e35b3e1a847f7bbc1eb', 'tools/poco_final_deletion.py': '270b7222d76950eb7c14d7ffec4ebf70d1fe7a90ae3a3d10924f056ce1536cfc', 'tools/test_logical_recovery_final_deletion.py': '0f6c58a65f28b818cba031474ce92362415fa1934fe230831a3dea791c1d2070'}


def validate_remediation_override(path, current, original):
    current, original = (raw.replace(b'\r\n', b'\n') for raw in (current, original))
    if path == '.github/workflows/android-ci.yml':
        import poco_remediation_ci_profile as profile
        require(profile.normalize(current.decode()).encode() == original,
                'Remediation workflow changed beyond diagnostic upload')
    elif path == 'tools/logical_recovery_ci_profile.py':
        route = REMEDIATION_ROUTE.encode()
        require(current.count(route) == 1 and current.replace(route, b'', 1) == original,
                'Recovery CI profile changed beyond reversible diagnostic route')
    else:
        raise ValueError('Unapproved remediation override')


def require(value, message):
    if not value:
        raise ValueError(message)


def candidate(root=ROOT):
    return (root / EVIDENCE / 'result.json').is_file()


def validate_paths(actual, approved, complete=True):
    require(set(actual) <= set(approved) and (not complete or set(actual) == set(approved)),
            'Unapproved or missing Stage8.6 paths')


def validate_seal(raw, expected):
    require(hashlib.sha256(raw.replace(b'\r\n', b'\n')).hexdigest() == expected,
            'Stage8.6 evidence seal mismatch')


def validate_parent_route(current, original):
    current = current.replace(b'\r\n', b'\n')
    expected = int(b'def validate_status_projection(' in original)
    require(current.count(REDUCED_STATUS_ROUTE.encode()) == expected, 'Reduced status route changed')
    current = current.replace(REDUCED_STATUS_ROUTE.encode(), b'', expected)
    require(current.count(STATUS_ROUTE.encode()) == expected, 'Parent status route changed')
    current = current.replace(STATUS_ROUTE.encode(), b'', expected)
    require(current.count(ROUTE.encode()) == 1 and
            current.replace(ROUTE.encode(), b'', 1) == original.replace(b'\r\n', b'\n'),
            'Accepted parent changed beyond successor route')


def validate_ci(environment, head, dirty, working):
    if environment.get('GITHUB_ACTIONS') or environment.get('GITHUB_EVENT_NAME'):
        require(not working and not dirty and environment.get('GITHUB_SHA') == head and
                environment.get('GITHUB_REF') == 'refs/heads/' + BRANCH and
                environment.get('GITHUB_REPOSITORY') == 'Monumentogram/DORA' and
                environment.get('GITHUB_EVENT_NAME') == 'push', 'Exact-SHA CI identity mismatch')


def validate_checkout(root=ROOT, *, allow_working=None):
    import validate_encrypted_persistence as persistence
    import validate_original_audio_lifecycle as lifecycle
    import validate_vad_runtime as vad
    import validate_development_device_security as security
    import logical_recovery_ci_profile as ci
    import validate_logical_recovery as parent
    git = lambda *args: persistence.git(root, *args)
    require(git('merge-base', BASE, 'HEAD').decode().strip() == BASE, 'Wrong exact Stage8.5 baseline')
    head = git('rev-parse', 'HEAD').decode().strip()
    branch = git('branch', '--show-current').decode().strip()
    require(branch == BRANCH or (not branch and os.environ.get('GITHUB_ACTIONS') == 'true'),
            'Wrong Stage8.6 branch')
    working = persistence.working_verification(allow_working, os.environ)
    dirty = bool(git('status', '--porcelain', '--untracked-files=all'))
    require(working or not dirty, 'Publication requires clean source')
    validate_ci(os.environ, head, dirty, working)
    import poco_alpha_battery_defer as alpha
    import poco_final_deletion as deletion
    import poco_reduced_admission as reduced
    reduced_contract = reduced.load(root)
    def before_protected(path, raw):
        if path not in reduced.PROTECTED_OVERRIDES:
            return raw
        return reduced.normalize_override(raw, git('show', reduced.PROTECTED_BASE + ':' + path),
                                          reduced_contract['protectedOverrides'][path])
    approved = set(SEALED) | set(COMPARATIVE_SEALED) | set(REMEDIATION_SEALED) | set(ALPHA_DEFER_SEALED) | set(FINAL_DELETION_SEALED) | {SELF, PARENT, GOVERNANCE}
    approved |= set(reduced_contract['files']) | reduced.OVERRIDES | {reduced.MANIFEST}
    approved |= reduced.PROTECTED_OVERRIDES
    import poco_persistence_diagnostic_admission as diagnostic
    approved |= diagnostic.validate_checkout(root)
    actual = set(git('diff', '--name-only', '--no-renames', BASE).decode().splitlines())
    actual |= set(git('ls-files', '--others', '--exclude-standard').decode().splitlines())
    validate_paths(actual, approved)
    for line in git('rev-list', '--reverse', '--parents', BASE + '..HEAD').decode().splitlines():
        parts = line.split()
        require(len(parts) == 2, 'No merge or rewritten baseline')
        validate_paths(git('diff', '--name-only', parts[1], parts[0]).decode().splitlines(), approved, False)
    for path, digest in {**SEALED, **COMPARATIVE_SEALED, **REMEDIATION_SEALED, **ALPHA_DEFER_SEALED, **FINAL_DELETION_SEALED}.items():
        raw = before_protected(path, (root / path).read_bytes())
        if path in reduced.DOCUMENTS:
            raw = reduced.normalize_document(path, raw)
        validate_seal(raw, digest)
    for path in deletion.OVERRIDES:
        deletion.normalize(path, before_protected(path, (root / path).read_bytes()), git('show', BASE + ':' + path))
    for path in reduced.OVERRIDES:
        reduced.normalize_override(before_protected(path, (root / path).read_bytes()), git('show', BASE + ':' + path), reduced_contract['overrides'][path])
    for path in reduced.PROTECTED_OVERRIDES:
        before_protected(path, (root / path).read_bytes())
    for path in alpha.DOCUMENTS:
        require(alpha.normalize_document(path, reduced.normalize_document(path, (root / path).read_bytes())) ==
                git('show', BASE + ':' + path).replace(b'\r\n', b'\n'),
                'Alpha owner decision must preserve historical document body')
    alpha.validate_file(root)
    validate_parent_route((root / PARENT).read_bytes(), git('show', BASE + ':' + PARENT))
    governance = (root / GOVERNANCE).read_bytes().replace(b'\r\n', b'\n')
    addition = b', "stage/8.6-poco-recording-acceptance"'
    require(governance.count(addition) == 2 and governance.replace(addition, b'') ==
            git('show', BASE + ':' + GOVERNANCE).replace(b'\r\n', b'\n'),
            'Recovery governance changed beyond two verified branch guards')
    # All other Android/build/workflow/oracle/historical bytes stay frozen.
    # Re-run inherited source, dependency-graph and test-inventory validations as well.
    contract = parent.read_contract(root)
    for path, digest in contract['files'].items():
        raw = governance.replace(addition, b'') if path == GOVERNANCE else (root / path).read_bytes()
        raw = before_protected(path, raw)
        if path in reduced.DOCUMENTS:
            raw = reduced.normalize_document(path, raw)
        if path in alpha.DOCUMENTS:
            raw = alpha.normalize_document(path, raw)
        if path in REMEDIATION_OVERRIDES:
            original = git('show', BASE + ':' + path)
            validate_remediation_override(path, raw, original)
            raw = original
        if path in deletion.OVERRIDES:
            raw = deletion.normalize(path, raw, git('show', BASE + ':' + path))
        if path in reduced.OVERRIDES:
            raw = reduced.normalize_override(raw, git('show', BASE + ':' + path), reduced_contract['overrides'][path])
        validate_seal(raw, digest)
    security.validate_development_sources(root)
    require(json.loads((root / security.BLOCKER_PATH).read_text()) == security.RESTORATION_BLOCKER,
            'Security restoration remains OPEN')
    persistence.validate_product_sources(persistence.read_product_sources(root))
    test_root = root / 'android/core/audio/src/androidTest/kotlin/com/monumentogram/dora/audio/persistence'
    tests = persistence.discover_device_tests({p.relative_to(test_root).as_posix(): p.read_text(encoding='utf-8')
                                              for p in test_root.rglob('*.kt')})
    persistence.validate_device_inventory((root / ci.INVENTORY).read_bytes().replace(b'\r\n',b'\n'),
                                         tests, contract['files'][ci.INVENTORY])
    receipt = json.loads((root / EVIDENCE / 'result.json').read_text(encoding='utf-8'))
    require(receipt['verdict'] == 'BLOCKED / POCO_ENERGY_MEASUREMENT_UNAVAILABLE' and
            receipt['campaignSourceSha'] is None and receipt['campaignApkSha256'] is None,
            'Preflight cannot certify physical acceptance')
    validate_comparative_disposition(
        json.loads((root / COMPARATIVE / 'result.json').read_text(encoding='utf-8')),
        json.loads((root / COMPARATIVE / 'comparative-gate-disposition.json').read_text(encoding='utf-8')),
    )
    legacy = lifecycle.historical_parent(root)
    inherited = set(legacy['implementation_paths']) | set(
        git('diff', '--name-only', lifecycle.PARENT, BASE).decode().splitlines())
    result = {**legacy, 'implementation_paths': sorted(inherited | approved),
              'vad_runtime_graph': True, 'release_graph_sha256': vad.read_contract(root)['releaseGraphSha256']}
    vad.approved_graph(root, result)
    return result


def validate_comparative_disposition(receipt, gate):
    require(receipt['verdict'] == 'PARTIAL / BATTERYSTATS_COMPARATIVE_ONLY',
            'Comparative preflight is not acceptance')
    for value in (receipt, gate):
        require(value['hardwareMicroWhGate'] ==
                'NOT_EVALUATED / HARDWARE_ENERGY_COUNTER_UNAVAILABLE' and
                value['primarySource'] is None and value['acceptanceFormula'] is None,
                'Unsupported measurement/source admission')
    require(not receipt['ratiosComputed'] and not receipt['batteryPercentConverted'] and
            not receipt['currentSnapshotsIntegrated'] and not receipt['hourCampaignExecuted'] and
            not receipt['cycles200Executed'], 'Probe cannot replace acceptance or invent energy')
    require(receipt['sheet'] == 'UNCHANGED' and receipt['groupD'] == 'NOT_STARTED' and
            receipt['cloud'] == 'NOT_STARTED' and receipt['asr'] == 'NOT_STARTED',
            'Comparative preflight scope changed')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--working', action='store_true', default=None)
    validate_checkout(allow_working=parser.parse_args().working)
    print('PASS sealed repository contract; battery deferred; Stage8.6 non-battery acceptance gaps OPEN')
