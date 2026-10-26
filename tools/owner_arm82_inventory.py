"""Disjoint successor accounting; historical diagnostic-v3 remains NOT_QUALIFIED."""
from alpha_security_preflight import require
import hashlib

PREFIX = 'com.monumentogram.dora.audio.persistence.'
HISTORICAL = {PREFIX + 'ProtectedReadOnlyVaultTest#' + name for name in (
    'fullSuccessor394UsesPinnedEntryAndRejectsCatalogOwnershipDrift',
    'completeAuthenticationOnEncryptedCopyLeavesSourceIdenticalAcrossRestart',
    'wrongOwnershipAndRevocationFailWithoutChangingOriginal',
)}
PHASED = {PREFIX + 'TranscriptProcessRestartTest#' + name for name in ('seed', 'readAfterProcessRestart')}


def project_stage9_policy(files, root):
    """Replay inherited SQL checks only after exact accepted Stage9 source validation."""
    import validate_encrypted_persistence as persistence
    from validate_recording_latency import POLICY_PATH
    source = files[POLICY_PATH].replace('\r\n', '\n')
    require(hashlib.sha256(source.encode()).hexdigest() ==
            '8ca6e22abba4bdad5b1a03c0a0ba67f0af7df67989f0162abe463396efec82a2',
            'UNREVIEWED_STAGE9_SQL_POLICY')
    projected = dict(files)
    projected[POLICY_PATH] = persistence.git(root, 'show',
        '4ee27ef2473b0b047ee73be0595e981d08848e94:' + POLICY_PATH).decode()
    return projected


def validate_accounting(contract, discovered, legacy, deferred):
    bulk = set(contract['tests'])
    historical = set(contract['historical_not_qualified'])
    phases = set(contract['separate_process_tests'])
    require(historical == HISTORICAL and phases == PHASED, 'ARM82_DISPOSITION_CHANGED')
    require(contract['deferred_tests'] == sorted(deferred), 'ARM82_LONG_DEFERRALS_CHANGED')
    groups = (bulk, historical, phases, set(deferred))
    require(sum(map(len, groups)) == len(set().union(*groups)) and
            set().union(*groups) == discovered, 'ARM82_INVENTORY_NOT_EXHAUSTIVE_OR_DISJOINT')
    require(legacy - historical - set(deferred) <= bulk, 'ARM82_INHERITED_REGRESSION_REMOVED')
    return bulk | historical | phases
