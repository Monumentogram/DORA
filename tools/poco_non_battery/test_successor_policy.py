"""Synthetic successor-policy checks; fixtures contain no owner data."""
import hashlib
import json
from pathlib import Path
import sys
import unittest
import uuid
sys.path.insert(0, str(Path(__file__).parents[1]))
from poco_non_battery import protected_policy


def uid(n):
    return str(uuid.UUID(int=n))


def packed(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':')).encode()


ARTIFACTS = {
    'key_confirmation': 'key-confirmation/run.kc',
    'manifest_ciphertext': 'manifests/g-00000000000000000001.ct',
    'manifest_wrapped_keyset': 'key-envelopes/manifest-g-00000000000000000001.ks',
    'microfile_ciphertext': 'units/u-0000000000.ct',
    'microfile_wrapped_keyset': 'key-envelopes/u-0000000000.ks',
}


class SuccessorPolicyTest(unittest.TestCase):
    def setUp(self):
        self.old = {'format': 'DORA_PROTECTED_HISTORICAL_V1', 'snapshotSha256': 'a' * 64,
                    'package': 'synthetic.package', 'model': 'synthetic', 'api': 34,
                    'firmware': 'synthetic', 'ownerId': uid(1), 'vaultId': uid(2),
                    'protectedSources': [dict(recordingId=uid(100+3*n), assetId=uid(101+3*n),
                                              sessionId=uid(102+3*n)) for n in range(47)],
                    'historicalIdentifiers': [uid(n) for n in range(100, 241)]}
        source = dict(recordingId=uid(1000), assetId=uid(1001), sessionId=uid(1002),
                      ownerId=uid(1), vaultId=uid(2))
        blocks = []
        for n in range(394):
            run = uid(2000+n)
            uri = 'android-keystore://dora.poc.recovery.v1.' + run
            alias = 'dora.vault.run.v1.' + hashlib.sha256(
                f'DORA/recovery-physical-alias/v1/{uid(2)}/{run}'.encode()).hexdigest()
            blocks.append(dict(ordinal=n, firstFrame=n*80000, claimedFrames=80000, runId=run,
                               physicalId=uid(4000+n//100), physicalFirstFrame=(n//100)*8000000,
                               sourceFrameOffset=(n%100)*80000, canonicalKeyUri=uri,
                               canonicalKeyUriSha256=hashlib.sha256(uri.encode()).hexdigest(),
                               physicalKeystoreAlias=alias, savedChallengePresent=False,
                               savedKeyProperties=dict(created=1,algorithm='AES',keySize=256,purposes=3,
                                   origin=1,userAuthenticationRequired=False,hardware=True,
                                   blockModes=['GCM'],encryptionPaddings=['NoPadding']), files=[dict(role=role,
                               path=f'no_backup/dora-vault-v1/poc-recovery/v1/runs/{run}/{relative}', metadataBytes=10, savedSha256='b'*64)
                               for role, relative in ARTIFACTS.items()]))
        self.mapping = dict(format='LONG02_OFFLINE_PROTECTION_MAPPING_V1_NOT_POLICY_NOT_BACKUP',
                            source=source, blocks=blocks, blockCount=394, fileCount=1970,
                            claimedFrames=31520000, snapshotSha256='c'*64, codeReference='d'*40,
                            custodySha256='e'*64, verifiedEvidenceFiles=19, metadataTotalFileBytes=19700,
                            savedAliasCount=394,savedKeyChallengeCount=0,deviceAccessPerformed=False,
                            audioBytesBackedUp=False,authenticatedPcmReadback='NOT_RUN',deviceProtectionExtended=False)

    def derive(self):
        self.assertTrue(hasattr(protected_policy, 'derive_successor'), 'Successor policy derivation missing')
        old, mapping = packed(self.old), packed(self.mapping)
        return json.loads(protected_policy.derive_successor(
            old, hashlib.sha256(old).hexdigest(), mapping, hashlib.sha256(mapping).hexdigest()))

    def test_retains_every_old_source_and_adds_exact_one(self):
        result = self.derive()
        self.assertEqual(len(result['protectedSources']), 48)
        self.assertEqual(result['predecessorSources'], self.old['protectedSources'])
        self.assertTrue(all(row in result['protectedSources'] for row in self.old['protectedSources']))
        self.assertEqual(len(result['additionalRunBindings']), 394)
        self.assertEqual(len(result['additionalPhysicalSources']), 4)
        self.assertTrue(set(self.old['historicalIdentifiers']) <= set(result['historicalIdentifiers']))
        self.assertEqual(result['predecessorIdentifiers'], self.old['historicalIdentifiers'])
        self.assertEqual(result['format'], 'DORA_PROTECTED_SUCCESSOR_V2')
        self.assertEqual(result['predecessorPolicySha256'], hashlib.sha256(packed(self.old)).hexdigest())
        self.assertEqual(result['custodyMappingSha256'], hashlib.sha256(packed(self.mapping)).hexdigest())
        self.assertEqual(result['snapshotSha256'], self.mapping['snapshotSha256'])
        self.assertEqual(sum(r['frames'] for r in result['additionalRunBindings']),31520000)
        self.assertEqual(result, self.derive())

    def test_mapping_wrong_owner_duplicate_run_and_old_namespace_rejected(self):
        for mutation in ('owner', 'duplicate', 'collision'):
            self.setUp()
            if mutation == 'owner': self.mapping['source']['ownerId'] = uid(9)
            if mutation == 'duplicate': self.mapping['blocks'][1]['runId'] = self.mapping['blocks'][0]['runId']
            if mutation == 'collision': self.mapping['source']['recordingId'] = self.old['protectedSources'][0]['recordingId']
            with self.assertRaises(ValueError): self.derive()

    def test_gap_wrong_alias_and_missing_artifact_rejected(self):
        for mutation in ('gap', 'alias', 'artifact'):
            self.setUp()
            if mutation == 'gap': self.mapping['blocks'][20]['firstFrame'] += 1
            if mutation == 'alias': self.mapping['blocks'][20]['physicalKeystoreAlias'] = 'wrong'
            if mutation == 'artifact': self.mapping['blocks'][20]['files'].pop()
            with self.assertRaises(ValueError): self.derive()

    def test_removed_historical_source_and_changed_pinned_bytes_rejected(self):
        self.old['protectedSources'].pop()
        with self.assertRaises(ValueError): self.derive()
        with self.assertRaises(ValueError):
            protected_policy.derive_successor(packed(self.old), '0'*64, packed(self.mapping), '0'*64)

    def test_predecessor_namespace_is_complete_unique_and_canonical(self):
        for mutation in ('missing_id', 'duplicate_id', 'bad_id', 'duplicate_source', 'bad_format'):
            self.setUp()
            if mutation == 'missing_id': self.old['historicalIdentifiers'].pop()
            if mutation == 'duplicate_id': self.old['historicalIdentifiers'].append(self.old['historicalIdentifiers'][0])
            if mutation == 'bad_id': self.old['historicalIdentifiers'].append('*')
            if mutation == 'duplicate_source': self.old['protectedSources'][1] = self.old['protectedSources'][0]
            if mutation == 'bad_format': self.old['format'] = 'DORA_PROTECTED_SUCCESSOR_V2'
            with self.subTest(mutation=mutation), self.assertRaises(ValueError): self.derive()

    def test_added_source_run_and_physical_namespaces_must_be_disjoint(self):
        for mutation in ('old_run', 'old_physical', 'source_run', 'source_physical', 'root_source', 'run_physical'):
            self.setUp()
            if mutation == 'old_run': self.mapping['blocks'][0]['runId'] = self.old['historicalIdentifiers'][0]
            if mutation == 'old_physical': self.mapping['blocks'][0]['physicalId'] = self.old['historicalIdentifiers'][0]
            if mutation == 'source_run': self.mapping['source']['assetId'] = self.mapping['blocks'][0]['runId']
            if mutation == 'source_physical': self.mapping['source']['assetId'] = self.mapping['blocks'][0]['physicalId']
            if mutation == 'root_source': self.mapping['source']['recordingId'] = self.old['ownerId']
            if mutation == 'run_physical': self.mapping['blocks'][0]['physicalId'] = self.mapping['blocks'][0]['runId']
            with self.subTest(mutation=mutation), self.assertRaises(ValueError): self.derive()

    def test_exact_roles_unique_paths_owned_canonical_names_and_digests(self):
        for mutation in ('duplicate_role', 'duplicate_path', 'wrong_run_path', 'traversal', 'absolute',
                         'backslash', 'wrong_generation', 'wrong_unit', 'bad_hash', 'bad_bytes'):
            self.setUp()
            files = self.mapping['blocks'][0]['files']
            if mutation == 'duplicate_role': files[1]['role'] = files[0]['role']
            if mutation == 'duplicate_path': files[1]['path'] = files[0]['path']
            if mutation == 'wrong_run_path': files[0]['path'] = files[0]['path'].replace(uid(2000),uid(9000))
            if mutation == 'traversal': files[0]['path'] = '../'+files[0]['path']
            if mutation == 'absolute': files[0]['path'] = '/'+files[0]['path']
            if mutation == 'backslash': files[0]['path'] = files[0]['path'].replace('/','\\')
            if mutation == 'wrong_generation': files[1]['path'] = files[1]['path'].replace('00000000000000000001','00000000000000000002')
            if mutation == 'wrong_unit': files[3]['path'] = files[3]['path'].replace('0000000000','0000000001')
            if mutation == 'bad_hash': files[0]['savedSha256'] = 'X'*64
            if mutation == 'bad_bytes': files[0]['metadataBytes'] = True
            with self.subTest(mutation=mutation), self.assertRaises(ValueError): self.derive()

    def test_physical_group_ranges_cannot_overlap_gap_or_reenter(self):
        for mutation in ('offset', 'physical_start', 'reentry', 'fifth_group', 'ordinal', 'frames', 'float', 'bool'):
            self.setUp()
            block = self.mapping['blocks'][200]
            if mutation == 'offset': block['sourceFrameOffset'] = 80000
            if mutation == 'physical_start': block['physicalFirstFrame'] -= 80000
            if mutation == 'reentry': block['physicalId'] = self.mapping['blocks'][0]['physicalId']
            if mutation == 'fifth_group': block['physicalId'] = uid(6000)
            if mutation == 'ordinal': block['ordinal'] -= 1
            if mutation == 'frames': block['claimedFrames'] -= 1
            if mutation == 'float': block['firstFrame'] = float(block['firstFrame'])
            if mutation == 'bool': self.mapping['blocks'][0]['ordinal'] = False
            with self.subTest(mutation=mutation), self.assertRaises(ValueError): self.derive()

    def test_count_ownership_canonical_key_uri_and_hash_mismatch(self):
        for mutation in ('blockCount','fileCount','claimedFrames','metadataTotalFileBytes','savedAliasCount',
                         'vault','uri','uri_hash'):
            self.setUp()
            if mutation in ('blockCount','fileCount','claimedFrames','metadataTotalFileBytes','savedAliasCount'): self.mapping[mutation] -= 1
            if mutation == 'vault': self.mapping['source']['vaultId'] = uid(9000)
            if mutation == 'uri': self.mapping['blocks'][0]['canonicalKeyUri'] += 'extra'
            if mutation == 'uri_hash': self.mapping['blocks'][0]['canonicalKeyUriSha256'] = '0'*64
            with self.subTest(mutation=mutation), self.assertRaises(ValueError): self.derive()

    def test_saved_properties_are_not_key_challenges_or_pcm_authentication(self):
        for mutation in ('algorithm','size','purpose','auth','challenge','pcm','backup','protection'):
            self.setUp()
            properties=self.mapping['blocks'][0]['savedKeyProperties']
            if mutation == 'algorithm': properties['algorithm']='DES'
            if mutation == 'size': properties['keySize']=128
            if mutation == 'purpose': properties['purposes']=True
            if mutation == 'auth': properties['userAuthenticationRequired']=True
            if mutation == 'challenge': self.mapping['blocks'][0]['savedChallengePresent']=True
            if mutation == 'pcm': self.mapping['authenticatedPcmReadback']='COMPLETE'
            if mutation == 'backup': self.mapping['audioBytesBackedUp']=True
            if mutation == 'protection': self.mapping['deviceProtectionExtended']=True
            with self.subTest(mutation=mutation), self.assertRaises(ValueError): self.derive()

    def test_malformed_duplicate_keys_and_unknown_fields_are_content_free_errors(self):
        self.assertTrue(hasattr(protected_policy,'derive_successor'), 'Successor policy derivation missing')
        for old in (b'{"PRIVATE_CANARY":', b'{"format":"first","format":"second"}',
                    packed(dict(self.old, PRIVATE_CANARY='PRIVATE_CANARY'))):
            with self.assertRaises(ValueError) as error:
                protected_policy.derive_successor(old,hashlib.sha256(old).hexdigest(),
                    packed(self.mapping),hashlib.sha256(packed(self.mapping)).hexdigest())
            self.assertNotIn('PRIVATE_CANARY',str(error.exception))


if __name__ == '__main__': unittest.main()
