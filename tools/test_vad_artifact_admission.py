"""Bounded successor and artifact negative controls; no native-model proof."""
import copy
import struct
import unittest
import validate_vad_artifact_admission as gate
from vad_admission import build


class VadArtifactAdmissionTests(unittest.TestCase):
    def test_closure_cannot_invent_physical_execution_or_unbind_custody(self):
        import json
        from pathlib import Path
        root=Path(gate.__file__).resolve().parents[1]/'docs/evidence/vad-8.4-closure'
        values=[json.loads((root/n).read_text()) for n in ('supply-chain.json','retrieval.json','runtime-smoke.json')]
        gate.validate_closure_data(*values)
        for index,key,value in ((1,'aarSha256','0'*64),(1,'originalBuildOutputUsed',True),
                                (1,'physicalRuntimeProved',True),(2,'harnessInstalled',False),
                                (2,'repositoryDisposition','PASS'),(2,'installedApkSha256','0'*64),
                                (2,'unexpectedNonPlatformLibraries',['libpiper.so']),
                                (2,'nativeFilesOnDeviceMatchedPackagedHashes',False),
                                (2,'crashBufferErrors',1),(2,'processAbsentAfterCleanup',False),
                                (2,'campaignStartsFromStoppedProcess',False)):
            changed=copy.deepcopy(values);changed[index][key]=value
            with self.assertRaises(ValueError):gate.validate_closure_data(*changed)
        for key,value in (('samePid',False),('freshReceiptAfterDeletion',False),('totalPssKiB',0)):
            changed=copy.deepcopy(values);changed[2]['runs'][0][key]=value
            with self.assertRaises(ValueError):gate.validate_closure_data(*changed)
        for key,value in (('closed',False),('initialized',False),('resetToSilence',False),
                          ('speechPositiveWindows',0),('silencePositiveWindows',1),('modelSha256','0'*64)):
            changed=copy.deepcopy(values);changed[2]['runs'][0]['runtime'][key]=value
            with self.assertRaises(ValueError):gate.validate_closure_data(*changed)

    def test_custody_identity_and_mutability_are_explicit(self):
        from vad_admission import custody
        manifest=custody.base_manifest()
        custody.validate_manifest(manifest)
        for key,value in (('version','1.13.8-dora.2'),('sha256','0'*64),('bytes',1)):
            changed=copy.deepcopy(manifest);changed['artifact'][key]=value
            with self.assertRaises(ValueError):custody.validate_manifest(changed)
        for key,value in (('public',True),('ownerCanModifyOrDelete',False),('ciCredentialsGranted',True),('overwriteAllowed',True)):
            changed=copy.deepcopy(manifest);changed['custody'][key]=value
            with self.assertRaises(ValueError):custody.validate_manifest(changed)

    def test_custody_retrieval_rejects_size_hash_and_unknown_inventory(self):
        from vad_admission import custody
        data=b'synthetic metadata only'
        import hashlib
        identity={'bytes':len(data),'sha256':hashlib.sha256(data).hexdigest()}
        custody.verify_bytes(data,identity)
        for bad in (data+b'!',b'X'+data[1:]):
            with self.assertRaises(ValueError):custody.verify_bytes(bad,identity)
        with self.assertRaises(ValueError):build.check_aar_entries(build.AAR_ENTRIES|{'assets/unexpected.pcm'})

    def test_recovery_admission_requires_exact_branch_and_full_tree_gate(self):
        from types import SimpleNamespace
        from unittest.mock import patch
        import validate_poc_recovery_governance as recovery
        import validate_encrypted_persistence as persistence
        lifecycle=SimpleNamespace(branch=gate.BRANCH,head='a'*40)
        anchor=SimpleNamespace(commit='b'*40,is_ancestor_of_head=True)
        with patch.object(persistence,'validate_checkout') as check, \
             patch.object(recovery,'collect_pinned_commit_identity',return_value=anchor), \
             patch.object(recovery,'validate_rec_clean_integrated_anchor') as historical:
            self.assertTrue(recovery.rec_clean_integrated_candidate(lifecycle))
            check.assert_called_once_with(recovery.ROOT)
            historical.assert_called_once()
        with patch.object(persistence,'validate_checkout',side_effect=ValueError('Unapproved tree')):
            with self.assertRaisesRegex(ValueError,'Unapproved tree'):
                recovery.rec_clean_integrated_candidate(lifecycle)
        lifecycle.branch=gate.BRANCH+'-unapproved'
        self.assertFalse(recovery.rec_clean_integrated_candidate(lifecycle))

    def test_substituted_aar_rejected_before_harness(self):
        import tempfile
        from pathlib import Path
        with tempfile.TemporaryDirectory() as directory:
            fake=Path(directory)/'same-version.aar'
            fake.write_bytes(build.zip_bytes({'classes.jar':b'1.13.8'}))
            with self.assertRaises(ValueError):build.check_aar(fake)
        build.check_aar_entries(build.AAR_ENTRIES)
        for names in (build.AAR_ENTRIES|{'jni/arm64-v8a/libespeak.so'},build.AAR_ENTRIES-{'classes.jar'}):
            with self.assertRaises(ValueError):build.check_aar_entries(names)

    def test_stale_native_cache_is_rejected(self):
        import tempfile
        from pathlib import Path
        with tempfile.TemporaryDirectory() as directory:
            output=Path(directory)
            build.check_fresh_output(output)
            (output/'CMakeCache.txt').write_text('FETCHCONTENT_SOURCE_DIR_ONNXRUNTIME=unverified')
            with self.assertRaises(ValueError):build.check_fresh_output(output)

    def test_notice_and_smoke_cross_bind_to_binary(self):
        import json
        from pathlib import Path
        root=Path(gate.__file__).resolve().parents[1]/gate.PREFIX
        inputs=json.loads((root/'build-inputs.json').read_text())
        record=json.loads((root/'candidate.json').read_text())
        smoke=json.loads((root/'runtime-smoke.json').read_text())
        notice=(root/'NOTICE.txt').read_bytes()
        gate.validate_external_bindings(record,inputs,smoke,notice)
        with self.assertRaises(ValueError):gate.validate_external_bindings(record,inputs,smoke,notice+b'changed')
        for key in ('aarSha256','modelSha256'):
            changed={**smoke,key:'0'*64}
            with self.assertRaises(ValueError):gate.validate_external_bindings(record,inputs,changed,notice)

    def test_only_exact_notice_attribute_can_change(self):
        baseline=b'*.json text eol=lf\n'
        gate.validate_attributes(baseline+gate.NOTICE_ATTRIBUTE.encode(),baseline)
        for extra in (b'*.txt -text\n',b'android/** -text\n',b''):
            with self.assertRaises(ValueError):gate.validate_attributes(baseline+extra,baseline)

    def test_sbom_native_and_license_graph_cannot_drift(self):
        import json
        from pathlib import Path
        root=Path(gate.__file__).resolve().parents[1]/gate.PREFIX
        inputs=json.loads((root/'build-inputs.json').read_text())
        licenses=json.loads((root/'license-inventory.json').read_text())
        sbom=json.loads((root/'sbom.cdx.json').read_text())
        gate.validate_bundle(inputs,licenses,sbom)
        missing=copy.deepcopy(sbom)
        missing['components']=[c for c in missing['components'] if c['name']!='ONNX Runtime']
        with self.assertRaises(ValueError): gate.validate_bundle(inputs,licenses,missing)
        wrong_hash=copy.deepcopy(sbom)
        next(c for c in wrong_hash['components'] if c['name']=='ONNX Runtime')['hashes'][0]['content']='0'*64
        with self.assertRaises(ValueError): gate.validate_bundle(inputs,licenses,wrong_hash)
        unknown=copy.deepcopy(licenses);unknown['components'][0]['license']='UNKNOWN'
        with self.assertRaises(ValueError): gate.validate_bundle(inputs,unknown,sbom)
        extra=copy.deepcopy(inputs)
        extra['artifactInventory']['native']['libespeak.so']={}
        with self.assertRaises(ValueError): gate.validate_bundle(extra,licenses,sbom)

    def test_real_notice_file_is_packaged_without_mutation(self):
        import tempfile
        from pathlib import Path
        with tempfile.TemporaryDirectory() as directory:
            notice=Path(directory)/'NOTICE.txt'
            notice.write_bytes(b'Exact license text\n')
            self.assertEqual({'META-INF/LICENSES/NOTICE.txt':b'Exact license text\n'},build.notice_entries(notice))

    def test_exact_paths_only(self):
        gate.validate_paths(gate.PATHS)
        for forbidden in ('docs/evidence/unrelated.json', 'tools/unrelated.py',
                          'android/app/build.gradle.kts', '.github/workflows/android-ci.yml',
                          'docs/evidence/vad-8.4-admission/README.md',
                          'android/core/audio/src/main/RecordingController.kt'):
            with self.subTest(path=forbidden), self.assertRaises(ValueError):
                gate.validate_paths(gate.PATHS | {forbidden})

    def test_missing_inventory_rejected(self):
        with self.assertRaises(ValueError):
            gate.validate_paths(set())

    def test_record_cannot_self_certify(self):
        record = gate.base_record()
        gate.validate_record(record)
        for status in ('PASS', 'PASS / VAD_ONLY_RUNTIME_ARTIFACT_ADMITTED'):
            with self.assertRaises(ValueError):
                gate.validate_record({**record, 'status':status})

    def test_model_and_source_cannot_drift(self):
        for field in ('sherpaSourceSha', 'modelSha256', 'parentSha', 'initialHead'):
            record = gate.base_record()
            record[field] = '0' * len(record[field])
            with self.subTest(field=field), self.assertRaises(ValueError):
                gate.validate_record(record)

    def test_production_stages_cannot_start(self):
        for field in ('productionVadIntegration', '8.4C', '8.5'):
            record = gate.base_record()
            record[field] = 'STARTED'
            with self.subTest(field=field), self.assertRaises(ValueError):
                gate.validate_record(record)

    def test_security_cannot_close(self):
        record = gate.base_record()
        record['securityRestorationBlocker'] = 'CLOSED'
        with self.assertRaises(ValueError):
            gate.validate_record(record)

    def test_zip_deterministic_and_safe(self):
        self.assertEqual(build.zip_bytes({'b':b'2','a':b'1'}),build.zip_bytes({'a':b'1','b':b'2'}))
        for name in ('../escape','/absolute'):
            with self.assertRaises(ValueError):
                build.zip_bytes({name:b'x'})

    def test_harness_has_no_microphone_or_network_permission(self):
        from pathlib import Path
        import xml.etree.ElementTree as ET
        manifest=ET.fromstring((Path(gate.__file__).parent/'vad_admission/AndroidManifest.xml').read_bytes())
        self.assertEqual([],manifest.findall('uses-permission'))
        self.assertNotEqual('com.monumentogram.dora',manifest.get('package'))

    def test_truncated_native_input_rejected(self):
        for data in (b'',b'\x7fELF\x02\x01',bytes(64)):
            with self.assertRaises(ValueError):
                build.inspect_elf(data)

    def test_elf_alignment_and_architecture_negative_controls(self):
        data=bytearray(128)
        data[:6]=b'\x7fELF\x02\x01'
        struct.pack_into('<H',data,18,183)
        struct.pack_into('<Q',data,32,64)
        struct.pack_into('<HH',data,54,56,1)
        struct.pack_into('<IIQQQQQQ',data,64,1,5,0,0,0,128,128,16384)
        self.assertEqual('STATIC_16K_ALIGNMENT_COMPATIBLE',build.inspect_elf(data)['static16KiB'])
        for offset,fmt,value in ((18,'<H',62),(112,'<Q',4096),(80,'<Q',1),(96,'<Q',99999)):
            broken=copy.copy(data);struct.pack_into(fmt,broken,offset,value)
            with self.subTest(offset=offset),self.assertRaises(ValueError):
                build.inspect_elf(broken)


if __name__ == '__main__':
    unittest.main()
