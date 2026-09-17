"""Fresh-source preflight evidence regressions using real strict instrumentation parsing."""
import copy
import hashlib
import json
from pathlib import Path
import tempfile
import unittest
from types import SimpleNamespace
from unittest.mock import patch

import recovery_alpha_prefix_repair as subject
import validate_recovery_0d6_candidate as candidate


class FreshPreflightTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        # Origin validation has separate real-file tests below. This seam isolates
        # the strict transcript/native/identity/cleanup matrix from Git discovery.
        origin=patch.object(subject,'validate_preflight_origin')
        origin.start();self.addCleanup(origin.stop)
        self.source = dict(commit='a'*40, tree='b'*40, appApkSha256='c'*64, testApkSha256='d'*64)
        self.proof = dict(schema='DORA_RECOVERY_FRESH_PREFLIGHT_V1', source=self.source,
                          environment='E36-GAPI', campaignAttempts=0, humanReview=False, attempts=[])
        for number, (payload, selector) in enumerate(subject.PREFLIGHT_SELECTORS.items()):
            owner = f'FRESH-PREFLIGHT-{number}'
            (self.root/owner).mkdir()
            terminal = dict(schema='DORA_0D6_PRIVATE_LAUNCH_RESULT_V1', completed=[payload], failure=None,
                            cleanupResult='VERIFIED', deviceExecutionRequested=True, campaignApprovalGranted=False)
            native = dict(nativeExitCode=0, timedOut=False, rawStderr='',
                          argv=['adb.exe']+subject.instrument_arguments(payload,self.source))
            case, method = selector.split('#')
            bundles = [
                f'INSTRUMENTATION_STATUS: class={case}\nINSTRUMENTATION_STATUS: test={method}\n'
                f'INSTRUMENTATION_STATUS: numtests=1\nINSTRUMENTATION_STATUS: current=1\n'
                f'INSTRUMENTATION_STATUS_CODE: {code}\n' for code in (1,0)
            ]
            observation = dict(harnessRevision=self.source['commit'],protocolId='poc-recovery-protocol-stage0-v0.8',
                apks=dict(targetSha256=self.source['appApkSha256'],testSha256=self.source['testApkSha256']),
                device=dict(sdk=36,fingerprint=subject.FINGERPRINT),
                sqlitePragmaObservations=dict(journal_mode='wal',synchronous='2',wal_autocheckpoint='0',foreign_keys='1'),
                targetApkSha256=self.source['appApkSha256'],testApkSha256=self.source['testApkSha256'],
                api=36,deviceFingerprint=subject.FINGERPRINT,status='PASS',powerLossDurabilityProven=False)
            stream=''.join('INSTRUMENTATION_STATUS: stream='+m+json.dumps(observation)+'\nINSTRUMENTATION_STATUS_CODE: 0\n'
                           for m in subject.PREFLIGHT_MARKERS[payload])
            native['rawStdout']=bundles[0]+stream+bundles[1]+'INSTRUMENTATION_CODE: -1\n'
            pin=self.write(f'{number}.pin.json',dict(source=self.source,payload=payload,attemptIds=[],outputRoot=str(self.root/owner)))
            self.proof['attempts'].append(dict(payload=payload, ownerSessionId=owner,
                pin=pin, instrumentationReceipt=self.write(f'{owner}/native.json',native),
                launcherReceipt=self.write(f'{number}.launcher.json',dict(nativeExitCode=0,
                    argv=['powershell','-PinPath',pin['path'],'-ApprovedPinSha256',pin['sha256'],'-Execute'])),
                terminal=self.write(f'{owner}/terminal-result.json',terminal)))
        self.original=copy.deepcopy(self.proof)
        self.original_files={p:p.read_bytes() for p in self.root.rglob('*') if p.is_file()}

    def restore(self):
        self.proof=copy.deepcopy(self.original)
        for p,data in self.original_files.items():p.write_bytes(data)
        self.check()  # Ensure every mutation starts from genuinely accepted evidence.

    def write(self, name, value):
        p=self.root/name
        data=value.encode() if isinstance(value,str) else json.dumps(value,sort_keys=True).encode()
        p.write_bytes(data)
        return dict(path=str(p),sha256=hashlib.sha256(data).hexdigest())

    def check(self):
        subject.validate_fresh_preflight(self.write('proof.json',self.proof),self.source)

    def mutate_json(self, key, field, value):
        d=self.proof['attempts'][0][key]
        data=json.loads(Path(d['path']).read_bytes());data[field]=value
        self.proof['attempts'][0][key]=self.write(Path(d['path']).relative_to(self.root),data)

    def test_accepts_three_fresh_source_exact_completed_tests_and_cleanup(self):
        self.check()

    def test_historical_source_cannot_supply_current_preflight(self):
        self.proof['source']=dict(self.source,commit='f'*40)
        with self.assertRaises(ValueError):self.check()

    def test_missing_duplicate_or_extra_payload_rejected(self):
        original=copy.deepcopy(self.proof['attempts'])
        for attempts in (original[:2],[original[0],original[0],original[2]],original+[original[0]]):
            self.restore()
            self.proof['attempts']=attempts
            with self.assertRaises(ValueError):self.check()

    def test_claimed_pass_without_actual_junit_success_is_rejected(self):
        d=self.proof['attempts'][0]['instrumentationReceipt']
        output=json.loads(Path(d['path']).read_bytes())['rawStdout']
        for bad in ('PASS',output.replace('STATUS_CODE: 0','STATUS_CODE: -2'),
                    output.replace('INSTRUMENTATION_CODE: -1',''),
                    output+'\nFAILURES!!!'):
            self.restore()
            self.mutate_json('instrumentationReceipt','rawStdout',bad)
            with self.assertRaises(ValueError):self.check()

    def test_foreign_requested_test_rejected(self):
        d=self.proof['attempts'][0]['instrumentationReceipt']
        output=json.loads(Path(d['path']).read_bytes())['rawStdout'].replace('supplementalCanonicalSqliteCompileOptionsPreflight','unrelatedTest')
        self.mutate_json('instrumentationReceipt','rawStdout',output)
        with self.assertRaises(ValueError):self.check()

    def test_native_failure_timeout_source_and_owner_drift_rejected(self):
        for field,value in (('nativeExitCode',2),('timedOut',True),('argv',['adb.exe','shell','true']),('rawStderr','error')):
            self.restore()
            self.mutate_json('instrumentationReceipt',field,value)
            with self.assertRaises(ValueError):self.check()
        self.restore()
        self.mutate_json('launcherReceipt','nativeExitCode',2)
        with self.assertRaises(ValueError):self.check()
        self.restore()
        self.mutate_json('pin','source',dict(self.source,commit='f'*40))
        with self.assertRaises(ValueError):self.check()
        self.restore()
        self.proof['attempts'][0]['ownerSessionId']='foreign'
        with self.assertRaises(ValueError):self.check()

    def test_uncertain_incomplete_foreign_cleanup_or_new_attempts_rejected(self):
        for field,value in (('cleanupResult','UNCERTAIN'),('failure','FAILED'),
                            ('deviceExecutionRequested',False),('completed',['campaign-case'])):
            self.restore()
            self.mutate_json('terminal',field,value)
            with self.assertRaises(ValueError):self.check()

    def test_missing_and_changed_raw_bytes_rejected(self):
        p=Path(self.proof['attempts'][0]['instrumentationReceipt']['path'])
        p.write_text('replaced')
        with self.assertRaises(ValueError):self.check()
        p.unlink()
        with self.assertRaises(ValueError):self.check()

    def test_scope_and_human_attestation_cannot_be_expanded(self):
        for key,value in (('environment','PHYSICAL'),('campaignAttempts',1),('humanReview',True)):
            self.restore()
            old=self.proof[key];self.proof[key]=value
            with self.assertRaises(ValueError):self.check()
            self.proof[key]=old

    def test_missing_duplicate_foreign_apk_and_failed_observations_rejected(self):
        d=self.proof['attempts'][0]['instrumentationReceipt']
        output=json.loads(Path(d['path']).read_bytes())['rawStdout']
        marker=subject.PREFLIGHT_MARKERS['SUPPLEMENTAL_SQLITE'][0]
        start=output.index('INSTRUMENTATION_STATUS: stream='+marker)
        end=output.index('INSTRUMENTATION_STATUS_CODE: 0\n',start)+len('INSTRUMENTATION_STATUS_CODE: 0\n')
        for bad in (output[:start]+output[end:],output[:end]+output[start:end]+output[end:],
                    output.replace(self.source['appApkSha256'],'e'*64),output.replace('"synchronous": "2"','"synchronous": "1"'),
                    output.replace(self.source['commit'],'f'*40)):
            self.restore();self.mutate_json('instrumentationReceipt','rawStdout',bad)
            with self.assertRaises(ValueError):self.check()


class PrefixApplicabilityFactsTests(unittest.TestCase):
    def setUp(self):
        self.profile=SimpleNamespace(implementation_commit='a'*40,implementation_tree='b'*40)
        self.binding=dict(appApkSha256='c'*64,testApkSha256='d'*64)
        self.legacy=subject.legacy_api()
        blob=lambda n:dict(mode='100644',type='blob',object=n*40)
        self.before={p:blob('1') for p in subject.ANDROID_REPAIR_PATHS|self.legacy['PREFLIGHT_METHODS'].keys()}
        self.after=copy.deepcopy(self.before)
        self.after.update({p:blob('2') for p in subject.ANDROID_REPAIR_PATHS})
        self.after[subject.DECISION_PATH]=blob('3')
        self.after['tools/recovery_alpha_prefix_repair.py']=blob('4')
        self.overrides={}

    def git(self,*args,root):
        self.assertEqual(root,subject.ROOT)
        if args in self.overrides:return self.overrides[args]
        if args==('rev-parse',subject.BASELINE_COMMIT+'^{tree}'):return subject.BASELINE_TREE
        if args==('merge-base',subject.BASELINE_COMMIT,self.profile.implementation_commit):return subject.BASELINE_COMMIT
        for commit,entries in ((subject.BASELINE_COMMIT,self.before),(self.profile.implementation_commit,self.after)):
            if args==('ls-tree','-r','-z',commit):
                return ''.join(f"{v['mode']} {v['type']} {v['object']}\t{p}\0" for p,v in sorted(entries.items()))
        self.fail('Unexpected Git query '+repr(args))

    def check(self):return subject.applicability_facts({'git':self.git},self.profile,self.binding)

    def test_exact_source_requires_fresh_preflight_including_changed_journal_method(self):
        result=self.check()
        self.assertFalse(result['historicalPreflightReusable'])
        self.assertEqual(3,len(result['requiredFreshPreflight']))
        self.assertEqual(26,len(result['sourceDelta']))
        self.assertEqual(subject.BASELINE_COMMIT,result['baseline']['commit'])

    def test_unrelated_build_dependency_decision_or_missing_repair_rejected(self):
        for path in ('android/build.gradle.kts','android/gradle/libs.versions.toml','docs/adr/unapproved.md','tools/unsafe.py'):
            self.check();self.after[path]=dict(mode='100644',type='blob',object='e'*40)
            with self.assertRaises(ValueError):self.check()
            del self.after[path]
        path=next(iter(subject.ANDROID_REPAIR_PATHS));self.after[path]=self.before[path]
        with self.assertRaises(ValueError):self.check()

    def test_deleted_executable_and_symlink_paths_rejected(self):
        path=next(iter(subject.ANDROID_REPAIR_PATHS));original=self.after[path]
        for replacement in (None,dict(original,mode='100755'),dict(original,mode='120000')):
            self.after[path]=original;self.check()
            if replacement is None:del self.after[path]
            else:self.after[path]=replacement
            with self.assertRaises(ValueError):self.check()

    def test_wrong_baseline_or_non_descendant_rejected(self):
        for query in (('rev-parse',subject.BASELINE_COMMIT+'^{tree}'),('merge-base',subject.BASELINE_COMMIT,self.profile.implementation_commit)):
            self.check();self.overrides[query]='f'*40
            with self.assertRaises(ValueError):self.check()
            self.overrides.clear()


class CombinedAdmissionTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        self.facts=PrefixApplicabilityFactsTests();self.facts.setUp()
        self.profile=candidate.Profile('a'*40,'b'*40,candidate.MAINTENANCE_PATHS)
        self.facts.profile=self.profile
        self.source=dict(commit='e'*40,tree='f'*40,appApkSha256='c'*64,testApkSha256='d'*64)
        self.binding=dict(appApkSha256='c'*64,testApkSha256='d'*64,applicabilitySha256='0'*64)
        self.overrides={}
        self.addCleanup(patch.stopall)
        patch.object(candidate,'git',side_effect=self.git).start()
        self.api=dict(candidate.__dict__,active_profile=lambda:self.profile,ALPHA_PREFIX_REPAIR_BINDING=self.binding)
        # This fixture describes the historical prefix/capture context only.
        self.api.pop('ALPHA_STREAM_PATH_REPAIR_BINDING', None)
        self.api.pop('ALPHA_COLLECTOR_QUERY_BINDING', None)
        self.api.pop('ALPHA_GIT_QUERY_BINDING', None)
        self.api.pop('ALPHA_MICROFILE_DISPOSITION_BINDING', None)
        self.api.pop('ALPHA_MICROFILE_TRU03_BINDING', None)
        patch.object(subject,'candidate_api',return_value=self.api).start()
        self.legacy=subject.legacy_api()
        self.frozen=dict(executionId='E36RED01',variantCount=165,entries=[dict(slot=1,mutationVariants=['DEFAULT'],attemptId='old',runId='old',executionEntrySha256='old')])
        self.frozen_descriptor=self.write('frozen.json',self.frozen)
        self.legacy['FROZEN_SELECTION_SHA256']=self.frozen_descriptor['sha256']
        patch.object(subject,'legacy_api',return_value=self.legacy).start()
        self.review=self.write('review.md','Exact combined source review')
        self.facts.after['tools/test_recovery_alpha_prefix_repair.py']=dict(mode='100644',type='blob',object='4'*40)
        self.capture=CaptureControlFixture(self,self.root,self.api,self.profile,self.binding,self.facts.after)
        self.proof=subject.applicability_facts(self.api,self.profile,self.binding)
        self.proof.update(frozenSelection=self.frozen_descriptor,independentReview=self.review,captureRepair=self.capture.descriptor)
        self.plan=dict(source=self.source)
        self.gate=dict(source=self.source,environment='E36-GAPI',supportedAttemptIds=[],
                       physicalAuthorization=dict(authorized=False),proofs=dict(independentReview=self.review),
                       alphaPreflight=dict(source=self.source))
        self.bind()

    def write(self,name,value):
        p=self.root/name;data=value.encode() if isinstance(value,str) else json.dumps(value,sort_keys=True).encode()
        p.write_bytes(data);return dict(path=str(p),sha256=hashlib.sha256(data).hexdigest())

    def bind(self):
        proof=self.write('applicability.json',self.proof)
        self.binding['applicabilitySha256']=proof['sha256'];self.gate['alphaPreflight']['sourceRepair']=proof

    def git(self,*args,root):
        if args in self.overrides:return self.overrides[args]
        fixed={('rev-parse',self.profile.implementation_commit+'^{tree}'):self.profile.implementation_tree,
               ('rev-parse','HEAD'):self.source['commit'],('rev-parse',self.source['commit']):self.source['commit'],
               ('rev-parse','HEAD^{tree}'):self.source['tree'],('branch','--show-current'):candidate.BRANCH,
               ('show','-s','--format=%P',self.source['commit']):self.profile.implementation_commit,
               ('rev-list','--min-parents=2',self.profile.implementation_commit+'..'+self.source['commit']):'',
               ('diff-tree','--no-commit-id','--name-only','--no-renames','-z','-r',self.source['commit']):'\0'.join(sorted(candidate.MAINTENANCE_PATHS))+'\0',
               ('status','--porcelain'):''}
        if args in fixed:return fixed[args]
        if len(args)==4 and args[:3]==('ls-tree',self.source['commit'],'--') and args[3] in candidate.MAINTENANCE_PATHS:
            return '100644 blob '+'9'*40+'\t'+args[3]
        return self.facts.git(*args,root=root)

    def check(self):subject.validate_preflight(self.plan,self.gate)

    def test_actual_exact_candidate_and_recomputed_prefix_proof_accept(self):self.check()

    def test_absent_binding_wrong_parent_dirty_and_wrong_metadata_are_rejected(self):
        for query,value in ((('show','-s','--format=%P',self.source['commit']),'9'*40),
                            (('status','--porcelain'),' M runtime.kt'),
                            (('diff-tree','--no-commit-id','--name-only','--no-renames','-z','-r',self.source['commit']),'tools/unrelated.py\0')):
            self.check();self.overrides[query]=value
            with self.assertRaises(ValueError):self.check()
            self.overrides.clear()
        self.api.pop('ALPHA_PREFIX_REPAIR_BINDING')
        with self.assertRaises(ValueError):self.check()

    def test_source_agreement_wrong_apks_and_unbound_review_cannot_authorize(self):
        for field in self.source:
            self.check();old=self.gate['alphaPreflight']['source'];self.gate['alphaPreflight']['source']=dict(old,**{field:'0'*len(old[field])})
            with self.assertRaises(ValueError):self.check()
            self.gate['alphaPreflight']['source']=old
        Path(self.review['path']).write_text('changed')
        with self.assertRaises(ValueError):self.check()

    def test_self_declared_equivalence_or_changed_source_facts_rejected(self):
        original=copy.deepcopy(self.proof)
        self.proof['historicalPreflightReusable']=True;self.bind()
        with self.assertRaises(ValueError):self.check()
        self.proof=original;self.proof['sourceDelta'][0]['after']['object']='0'*40;self.bind()
        with self.assertRaises(ValueError):self.check()

    def test_physical_campaign_and_consumed_scope_cannot_enter_preflight(self):
        for key,value in (('alphaReduced',{}),('alphaCampaign',{}),('supportedAttemptIds',['campaign']),('environment','D2')):
            old=copy.deepcopy(self.gate);self.gate[key]=value
            with self.assertRaises(ValueError):self.check()
            self.gate=old

    def test_reduced_campaign_requires_fresh_preflight_and_original_selection(self):
        decision=self.gate.pop('alphaPreflight');decision['scope']=subject.SCOPE
        self.gate.update(alphaReduced=decision,supportedPayloads=['CAMPAIGN'])
        selection=copy.deepcopy(self.frozen);selection['executionId']='FRESH'
        selection['entries'][0].update(attemptId='new',runId='new',executionEntrySha256='new')
        self.gate['reducedSelection']=selection
        with self.assertRaisesRegex(ValueError,'Malformed descriptor'):subject.validate(self.plan,self.gate)
        selection['entries'][0]['slot']=2
        with self.assertRaisesRegex(ValueError,'original 114'):subject.validate(self.plan,self.gate)


class PrefixRouteTests(unittest.TestCase):
    def test_repaired_preflight_dispatches_separate_source_proof(self):
        import test_recovery_campaign as fixtures
        campaign=fixtures.campaign
        fixture=fixtures.AlphaPreflightAdmission();fixture.setUp();self.addCleanup(fixture.doCleanups)
        fixture.gate['alphaPreflight']['sourceRepair']={'path':'exact-proof'}
        calls=[]
        with patch.object(campaign,'validate_alpha_prefix_preflight',side_effect=lambda p,g:calls.append((p,g)),create=True):
            campaign.validate_execution_gate(fixture.plan,fixture.gate,payload='JOURNAL_CONNECTIONS')
        self.assertEqual([(fixture.plan,fixture.gate)],calls)

    def test_prefix_profile_cannot_enter_historical_unchanged_source_route(self):
        import recovery_campaign as campaign
        with patch.object(campaign.runpy,'run_path',return_value={'ALPHA_PREFIX_REPAIR_BINDING':{},'alpha_preflight_source':lambda:{}}):
            with self.assertRaises(ValueError):campaign.accepted_alpha_source()


class PreflightOriginTests(unittest.TestCase):
    def setUp(self):
        legacy=subject.legacy_api()
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);self.repo=self.root/'repo';(self.repo/'tools').mkdir(parents=True)
        self.addCleanup(patch.stopall)
        patch.object(subject,'ROOT',self.repo).start()
        patch.object(subject,'legacy_api',return_value=legacy).start()
        self.files={}
        for name in ('powershell.exe','adb.exe','python.exe','launcher.ps1','app.apk','test.apk'):
            p=self.root/name;p.write_bytes(('exact fixture '+name).encode());self.files[name]=p
        (self.repo/'tools/recovery_campaign.py').write_text('fixed driver fixture')
        for constant,name in (('POWERSHELL_SHA256','powershell.exe'),('ADB_SHA256','adb.exe'),
                              ('PYTHON_SHA256','python.exe'),('LAUNCHER_SHA256','launcher.ps1')):
            patch.object(subject,constant,subject.file_sha(self.files[name])).start()
        self.source=dict(commit='a'*40,tree='b'*40,appApkSha256=subject.file_sha(self.files['app.apk']),testApkSha256=subject.file_sha(self.files['test.apk']))
        prefix={k:self.source[k] for k in ('appApkSha256','testApkSha256')}
        profile=SimpleNamespace(implementation_commit='c'*40,implementation_tree='d'*40)
        self.api=dict(git=lambda *a,**k:None,active_profile=lambda:profile,ALPHA_PREFIX_REPAIR_BINDING=prefix)
        self.capture=CaptureControlFixture(self,self.root,self.api,profile,prefix,powershell=self.files['powershell.exe'])
        patch.object(subject,'candidate_api',return_value=self.api).start()
        self.files['launcher.ps1']=Path(self.capture.new['Invoke-0D6Campaign.ps1']['path'])
        patch.object(subject,'LAUNCHER_SHA256',subject.file_sha(self.files['launcher.ps1'])).start()
        source_proof=self.capture.write('applicability.json',dict(captureRepair=self.capture.descriptor))
        prefix['applicabilitySha256']=source_proof['sha256']
        self.plan=dict(source=self.source)
        self.gate=dict(source=self.source,alphaPreflight=dict(source=self.source,sourceRepair=source_proof),scopeProof='exact')
        self.plan_path=self.root/'plan.json';self.gate_path=self.root/'gate.json'
        self.plan_path.write_text(json.dumps(self.plan));self.gate_path.write_text(json.dumps(self.gate))
        self.pin=dict(schema='DORA_0D6_PRIVATE_LAUNCH_PIN_V1',source=self.source,sourceRoot=str(self.repo),
            ownerSessionId='FRESH',payload='JOURNAL_CONNECTIONS',attemptIds=[],outputRoot=str(self.root/'FRESH'),
            launcherSha256=subject.LAUNCHER_SHA256,pythonPath=str(self.files['python.exe']),pythonSha256=subject.PYTHON_SHA256,
            appApkPath=str(self.files['app.apk']),testApkPath=str(self.files['test.apk']),
            driverSha256=subject.file_sha(self.repo/'tools/recovery_campaign.py'),
            planPath=str(self.plan_path),planFileSha256=subject.file_sha(self.plan_path),
            gatePath=str(self.gate_path),gateFileSha256=subject.file_sha(self.gate_path))
        self.native=dict(argv=[str(self.files['adb.exe'])]+subject.instrument_arguments('JOURNAL_CONNECTIONS',self.source))
        self.attempt=dict(payload='JOURNAL_CONNECTIONS',ownerSessionId='FRESH')
        self.launcher={};self.calls=[]
        def gate_check(plan,gate,payload):
            self.assertEqual(self.plan,plan);self.assertEqual(self.gate,gate);self.assertEqual('JOURNAL_CONNECTIONS',payload)
            self.calls.append(payload)
        self.gate_validator=gate_check
        patch.object(subject.runpy,'run_path',side_effect=lambda path:{'validate_execution_gate':self.gate_validator}).start()
        self.bind()
        self.original_files={p:p.read_bytes() for p in self.root.rglob('*') if p.is_file()}
        self.original=(copy.deepcopy(self.pin),copy.deepcopy(self.native),copy.deepcopy(self.launcher),copy.deepcopy(self.attempt))

    def bind(self):
        p=self.root/'pin.json';p.write_text(json.dumps(self.pin))
        self.attempt['pin']=dict(path=str(p),sha256=subject.file_sha(p))
        self.launcher['argv']=[str(self.files['powershell.exe']),'-NoLogo','-NoProfile','-NonInteractive',
            '-ExecutionPolicy','Bypass','-File',str(self.files['launcher.ps1']),'-PinPath',str(p),
            '-ApprovedPinSha256',self.attempt['pin']['sha256'],'-Execute']

    def check(self):subject.validate_preflight_origin(self.attempt,self.pin,self.native,self.launcher,self.source)

    def restore(self):
        for p,data in self.original_files.items():p.write_bytes(data)
        self.pin,self.native,self.launcher,self.attempt=copy.deepcopy(self.original)
        self.plan=json.loads(self.plan_path.read_bytes())
        self.gate=json.loads(self.gate_path.read_bytes())
        self.check()

    def test_exact_files_pins_and_existing_preflight_gate_are_required(self):
        self.check();self.assertEqual(['JOURNAL_CONNECTIONS'],self.calls)

    def test_nonexistent_or_wrong_native_and_launcher_files_rejected(self):
        for key,index in (('native',0),('launcher',0),('launcher',7)):
            self.restore();getattr(self,key)['argv'][index]=str(self.root/'foreign.exe')
            with self.assertRaises(ValueError):self.check()
        for name in ('adb.exe','powershell.exe','launcher.ps1','python.exe'):
            self.restore();self.files[name].write_text('replaced')
            with self.assertRaises(ValueError):self.check()

    def test_self_consistent_foreign_pin_origin_cannot_authorize(self):
        for key,value in (('schema','FOREIGN'),('sourceRoot',str(self.root/'foreign')),('ownerSessionId','OTHER'),
                          ('launcherSha256','0'*64),('pythonSha256','0'*64),('driverSha256','0'*64),
                          ('planFileSha256','0'*64),('gateFileSha256','0'*64)):
            self.restore();self.pin[key]=value;self.bind()
            with self.assertRaises(ValueError):self.check()

    def test_changed_apks_plan_and_gate_bytes_rejected(self):
        for p in (self.files['app.apk'],self.files['test.apk'],self.plan_path,self.gate_path):
            self.restore();p.write_text('drifted')
            with self.assertRaises(ValueError):self.check()

    def test_extra_launcher_switch_or_changed_pin_arguments_rejected(self):
        for mutation in (lambda a:a.append('-Other'),lambda a:a.__setitem__(6,'-Command'),lambda a:a.__setitem__(11,'0'*64)):
            self.restore();mutation(self.launcher['argv'])
            with self.assertRaises(ValueError):self.check()

    def test_self_consistent_campaign_gate_and_failed_preflight_gate_rejected(self):
        self.gate['alphaCampaign']={};self.gate.pop('alphaPreflight')
        self.gate_path.write_text(json.dumps(self.gate));self.pin['gateFileSha256']=subject.file_sha(self.gate_path);self.bind()
        with self.assertRaises(ValueError):self.check()
        self.restore()
        def reject(*args,**kwargs):raise ValueError('Real gate refused prerequisite')
        self.gate_validator=reject
        with self.assertRaisesRegex(ValueError,'Real gate refused'):self.check()


class CompleteFreshPreflightTests(unittest.TestCase):
    def test_three_payloads_pass_real_origin_gate_and_strict_parser_together(self):
        import test_recovery_campaign as fixtures
        real_origin=subject.validate_preflight_origin
        original_run_path=subject.runpy.run_path
        transcript=FreshPreflightTests();transcript.setUp();self.addCleanup(transcript.doCleanups)
        gate_fixture=fixtures.AlphaPreflightAdmission();gate_fixture.setUp();self.addCleanup(gate_fixture.doCleanups)
        root=transcript.root
        files={}
        for name in ('powershell.exe','adb.exe','python.exe','launcher.ps1','app.apk','test.apk'):
            p=root/name;p.write_bytes(('complete origin '+name).encode());files[name]=p
        source=dict(transcript.source,appApkSha256=subject.file_sha(files['app.apk']),testApkSha256=subject.file_sha(files['test.apk']))
        prefix={k:source[k] for k in ('appApkSha256','testApkSha256')}
        profile=SimpleNamespace(implementation_commit='c'*40,implementation_tree='d'*40)
        api=dict(git=lambda *a,**k:None,active_profile=lambda:profile,ALPHA_PREFIX_REPAIR_BINDING=prefix)
        capture=CaptureControlFixture(self,root,api,profile,prefix,powershell=files['powershell.exe'])
        files['launcher.ps1']=Path(capture.new['Invoke-0D6Campaign.ps1']['path'])
        source_proof=capture.write('applicability.json',dict(captureRepair=capture.descriptor))
        prefix['applicabilitySha256']=source_proof['sha256']
        plan=dict(source=source);gate=gate_fixture.gate
        gate.update(source=source,manifestSha256=fixtures.campaign.digest_json(plan))
        gate['alphaPreflight']['source']=source
        gate['alphaPreflight']['sourceRepair']=source_proof
        plan_d=transcript.write('complete-plan.json',plan);gate_d=transcript.write('complete-gate.json',gate)
        for attempt in transcript.proof['attempts']:
            payload=attempt['payload'];owner=attempt['ownerSessionId']
            pin=json.loads(Path(attempt['pin']['path']).read_bytes())
            pin.update(schema='DORA_0D6_PRIVATE_LAUNCH_PIN_V1',source=source,sourceRoot=str(subject.ROOT),ownerSessionId=owner,
                launcherSha256=subject.file_sha(files['launcher.ps1']),pythonPath=str(files['python.exe']),pythonSha256=subject.file_sha(files['python.exe']),
                appApkPath=str(files['app.apk']),testApkPath=str(files['test.apk']),driverSha256=subject.file_sha(subject.ROOT/'tools/recovery_campaign.py'),
                planPath=plan_d['path'],planFileSha256=plan_d['sha256'],gatePath=gate_d['path'],gateFileSha256=gate_d['sha256'])
            attempt['pin']=transcript.write(Path(attempt['pin']['path']).name,pin)
            native=json.loads(Path(attempt['instrumentationReceipt']['path']).read_bytes())
            native['argv']=[str(files['adb.exe'])]+subject.instrument_arguments(payload,source)
            for key in ('appApkSha256','testApkSha256'):
                native['rawStdout']=native['rawStdout'].replace(transcript.source[key],source[key])
            attempt['instrumentationReceipt']=transcript.write(owner+'/native.json',native)
            argv=[str(files['powershell.exe']),'-NoLogo','-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File',str(files['launcher.ps1']),
                '-PinPath',attempt['pin']['path'],'-ApprovedPinSha256',attempt['pin']['sha256'],'-Execute']
            attempt['launcherReceipt']=transcript.write(owner+'-launcher.json',dict(nativeExitCode=0,argv=argv))
        transcript.source=source;transcript.proof['source']=source
        def route(path):
            if Path(path)==subject.ROOT/'tools/recovery_campaign.py':return fixtures.campaign.__dict__
            return original_run_path(path)
        with patch.object(subject,'validate_preflight_origin',side_effect=real_origin) as origin, \
             patch.object(subject.runpy,'run_path',side_effect=route), \
             patch.object(subject,'candidate_api',return_value=api), \
             patch.object(fixtures.campaign,'validate_alpha_prefix_preflight') as static_source, \
             patch.object(subject,'POWERSHELL_SHA256',subject.file_sha(files['powershell.exe'])), \
             patch.object(subject,'ADB_SHA256',subject.file_sha(files['adb.exe'])), \
             patch.object(subject,'PYTHON_SHA256',subject.file_sha(files['python.exe'])), \
             patch.object(subject,'LAUNCHER_SHA256',subject.file_sha(files['launcher.ps1'])):
            transcript.check();self.assertEqual(3,origin.call_count)
            self.assertEqual(3,static_source.call_count)
            gate['exactHeadCiPassed']=False
            gate_d=transcript.write('complete-gate.json',gate)
            attempt=transcript.proof['attempts'][0]
            pin=json.loads(Path(attempt['pin']['path']).read_bytes());pin['gateFileSha256']=gate_d['sha256']
            attempt['pin']=transcript.write(Path(attempt['pin']['path']).name,pin)
            launcher=json.loads(Path(attempt['launcherReceipt']['path']).read_bytes());launcher['argv'][11]=attempt['pin']['sha256']
            attempt['launcherReceipt']=transcript.write(Path(attempt['launcherReceipt']['path']).name,launcher)
            with self.assertRaisesRegex(ValueError,'exactHeadCiPassed'):transcript.check()


class ProductionCaptureControlPinsTests(unittest.TestCase):
    def test_every_production_control_pin_is_canonical_sha256(self):
        # Check the real constants, without the synthetic fixture's pin patches.
        for name,digest in subject.CAPTURE_OLD_CONTROLS.items():
            with self.subTest(control=name):
                self.assertIsInstance(digest,str)
                self.assertRegex(digest,r'\A[0-9a-f]{64}\Z')


class CaptureControlFixture:
    """Real inert control files; Git discovery alone is synthetic."""
    baseline='eda7a904fde8e1de211fa666b8e7d09e8d252b0d'
    tree='92c1db823a7d9f966a26e8a934f19c5601048dd0'
    paths=('tools/recovery_alpha_prefix_repair.py','tools/test_recovery_alpha_prefix_repair.py')
    names=('Invoke-0D6Campaign.ps1','Attempt05-Lifecycle-Functions.ps1','Campaign-Checkpoint.ps1',
           'Logcat-Capture.ps1','rec_i3_owned_process.psm1','Shutdown-Member-Resolution.ps1')

    def __init__(self, test, root, api, profile, prefix, entries=None, powershell=None):
        self.root=root/'capture';self.root.mkdir()
        self.api=api;self.profile=profile;self.prefix=prefix
        self.old={};self.new={}
        for dirname, mapping in (('old',self.old),('new',self.new)):
            folder=self.root/dirname;folder.mkdir()
            for name in self.names:
                p=folder/name
                p.write_text(('new ' if dirname=='new' and name in (self.names[0],self.names[4]) else 'old ')+name)
                mapping[name]=dict(path=str(p),sha256=subject.file_sha(p))
        # Production old hashes are fixed. Synthetic byte identities are isolated here.
        patcher=patch.object(subject,'CAPTURE_OLD_CONTROLS',{n:d['sha256'] for n,d in self.old.items()},create=True)
        patcher.start();test.addCleanup(patcher.stop)
        if powershell is None:
            powershell=self.root/'powershell.exe';powershell.write_text('inert native fixture')
        patcher=patch.object(subject,'POWERSHELL_SHA256',subject.file_sha(powershell))
        patcher.start();test.addCleanup(patcher.stop)
        patcher=patch.object(subject,'CAPTURE_APK_PAIR',{k:prefix[k] for k in ('appApkSha256','testApkSha256')})
        patcher.start();test.addCleanup(patcher.stop)
        blob=lambda x:dict(mode='100644',type='blob',object=x*40)
        self.after=copy.deepcopy(entries or {})
        for p in self.paths:self.after.setdefault(p,blob('2'))
        self.before=copy.deepcopy(self.after)
        for p in self.paths:self.before[p]=blob('1')
        self.metadata_before='IMPLEMENTATION_COMMIT="old"\nIMPLEMENTATION_TREE="old"\nALPHA_PREFIX_REPAIR_BINDING={}\ndef validate(): return True\n'
        self.metadata_after=self.metadata_before+'ALPHA_CAPTURE_REPAIR_BINDING={}\n'
        original_git=api['git']
        def git(*args,root):
            if args==('rev-parse',self.baseline+'^{tree}'):return self.tree
            if args==('merge-base',self.baseline,profile.implementation_commit):return self.baseline
            for commit,tree in ((self.baseline,self.before),(profile.implementation_commit,self.after)):
                if args==('ls-tree','-r','-z',commit):
                    return ''.join(f"{v['mode']} {v['type']} {v['object']}\t{p}\0" for p,v in sorted(tree.items()))
            if args==('show',profile.implementation_commit+':tools/validate_recovery_0d6_candidate.py'):return self.metadata_before
            if args==('show','HEAD:tools/validate_recovery_0d6_candidate.py'):return self.metadata_after
            return original_git(*args,root=root)
        api['git']=git
        test_script=self.root/'test-capture.ps1';test_script.write_text('# inert fixture, never executed')
        test_descriptor=dict(path=str(test_script),sha256=subject.file_sha(test_script))
        receipt=self.write('native.json',dict(nativeExitCode=0,
            argv=[str(powershell),'-NoLogo','-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File',
                  str(test_script),'-ModulePath',self.new['rec_i3_owned_process.psm1']['path']],
            startedAtUtc='2026-09-15T00:00:00Z',endedAtUtc='2026-09-15T00:00:01Z'))
        red=self.write('original-red.json',dict(nativeExitCode=1,reason='demonstrated original capture loss'))
        self.test_manifest=dict(schema='DORA_CAPTURE_NATIVE_TEST_V1',controlsBefore=self.new,controlsAfter=self.new,
            testFilesBefore=[test_descriptor],testFilesAfter=[test_descriptor],receipt=receipt,redEvidence=[red])
        test_manifest=self.write('native-manifest.json',self.test_manifest)
        review=self.write('review.json',dict(disposition='CLEAN',scope='exact six inert fixture controls'))
        self.proof=dict(schema='DORA_RECOVERY_CAPTURE_REPAIR_V1',sourceRoot=str(subject.ROOT),
            baseline=dict(commit=self.baseline,tree=self.tree),
            implementation=dict(commit=profile.implementation_commit,tree=profile.implementation_tree),
            apkPair={k:prefix[k] for k in ('appApkSha256','testApkSha256')},
            sourceDelta=[dict(path=p,before=self.before[p],after=self.after[p]) for p in sorted(self.paths)],
            oldControls=self.old,newControls=self.new,nativeTests=[test_manifest],independentReview=review)
        self.bind()

    def write(self,name,value):
        p=self.root/name;p.write_text(json.dumps(value,sort_keys=True));return dict(path=str(p),sha256=subject.file_sha(p))

    def bind(self):
        self.descriptor=self.write('proof.json',self.proof)
        previous=self.api.get('ALPHA_CAPTURE_REPAIR_BINDING',{})
        self.api['ALPHA_CAPTURE_REPAIR_BINDING']=dict(
            launcherSha256=self.new.get(self.names[0],{}).get('sha256',previous.get('launcherSha256')),
            ownedProcessModuleSha256=self.new.get(self.names[4],{}).get('sha256',previous.get('ownedProcessModuleSha256')),
            proofSha256=self.descriptor['sha256'])

    def check(self):return subject.validate_capture_repair(self.api,self.profile,self.prefix,self.descriptor)


class CaptureRepairTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)
        self.profile=SimpleNamespace(implementation_commit='a'*40,implementation_tree='b'*40)
        self.prefix=dict(appApkSha256='c'*64,testApkSha256='d'*64)
        def unexpected(*args,**kwargs):self.fail('Unexpected Git query '+repr(args))
        self.api=dict(git=unexpected)
        self.fixture=CaptureControlFixture(self,self.root,self.api,self.profile,self.prefix)

    def test_pinned_six_controls_host_only_delta_and_review_are_accepted(self):
        result=self.fixture.check()
        self.assertEqual(self.fixture.new,result)

    def test_missing_binding_never_falls_back_to_old_launcher(self):
        del self.api['ALPHA_CAPTURE_REPAIR_BINDING']
        with self.assertRaisesRegex(ValueError,'capture repair pins'):self.fixture.check()

    def test_old_source_gate_without_capture_binding_is_closed(self):
        old=CombinedAdmissionTests();old.setUp();self.addCleanup(old.doCleanups)
        old.api.pop('ALPHA_CAPTURE_REPAIR_BINDING',None)
        with self.assertRaisesRegex(ValueError,'capture repair pins'):old.check()

    def test_control_tamper_missing_extra_foreign_sibling_and_basename_rejected(self):
        for mode in ('tamper','missing','extra','parent','basename'):
            with self.subTest(mode=mode):
                original=copy.deepcopy(self.fixture.proof)
                desc=self.fixture.new[self.fixture.names[4]];p=Path(desc['path']);data=p.read_bytes()
                if mode=='tamper':p.write_bytes(data+b'drift')
                elif mode=='missing':del self.fixture.proof['newControls'][self.fixture.names[4]]
                elif mode=='extra':self.fixture.proof['newControls']['extra.ps1']=desc
                else:
                    q=self.root/(p.name if mode=='parent' else 'wrong.psm1');q.write_bytes(data)
                    desc['path']=str(q)
                self.fixture.bind()
                with self.assertRaises(ValueError):self.fixture.check()
                p.write_bytes(data);self.fixture.proof=original;self.fixture.new=original['newControls'];self.fixture.bind()

    def test_unrelated_control_changes_and_old_launcher_fallback_rejected(self):
        for name in (self.fixture.names[1],self.fixture.names[0]):
            original=copy.deepcopy(self.fixture.proof)
            p=Path(self.fixture.new[name]['path'])
            p.write_text('changed unrelated' if name==self.fixture.names[1] else 'old '+name)
            self.fixture.new[name]['sha256']=subject.file_sha(p);self.fixture.bind()
            with self.assertRaises(ValueError):self.fixture.check()
            self.fixture.proof=original;self.fixture.new=original['newControls']

    def test_foreign_source_apk_implementation_and_self_consistent_delta_rejected(self):
        for field,value in (('sourceRoot',str(self.root)),('apkPair',dict(self.prefix,appApkSha256='f'*64)),
                            ('implementation',dict(commit='f'*40,tree='e'*40)),('sourceDelta',[])):
            original=copy.deepcopy(self.fixture.proof);self.fixture.proof[field]=value;self.fixture.bind()
            with self.assertRaises(ValueError):self.fixture.check()
            self.fixture.proof=original;self.fixture.bind()
        self.fixture.after['android/build.gradle.kts']=dict(mode='100644',type='blob',object='9'*40)
        with self.assertRaisesRegex(ValueError,'host-only'):self.fixture.check()

    def test_native_failure_review_tamper_and_metadata_behavior_rejected(self):
        p=Path(self.fixture.test_manifest['receipt']['path'])
        receipt=json.loads(p.read_bytes());receipt['nativeExitCode']=2
        self.fixture.test_manifest['receipt']=self.fixture.write(p.name,receipt)
        self.fixture.proof['nativeTests'][0]=self.fixture.write('native-manifest.json',self.fixture.test_manifest);self.fixture.bind()
        with self.assertRaises(ValueError):self.fixture.check()
        receipt['nativeExitCode']=0
        self.fixture.test_manifest['receipt']=self.fixture.write(p.name,receipt)
        self.fixture.proof['nativeTests'][0]=self.fixture.write('native-manifest.json',self.fixture.test_manifest);self.fixture.bind()
        self.fixture.metadata_after=self.fixture.metadata_before.replace('return True','return False')+'ALPHA_CAPTURE_REPAIR_BINDING={}\n'
        with self.assertRaisesRegex(ValueError,'metadata behavior'):self.fixture.check()
        self.fixture.metadata_after=self.fixture.metadata_before+'ALPHA_CAPTURE_REPAIR_BINDING={}\n'
        Path(self.fixture.proof['independentReview']['path']).write_text('changed review')
        with self.assertRaises(ValueError):self.fixture.check()

    def test_final_embedded_controls_scripts_and_red_provenance_must_match_test_manifests(self):
        original=copy.deepcopy(self.fixture.test_manifest)
        for field,value in (('controlsBefore',self.fixture.old),('controlsAfter',self.fixture.old),
                            ('testFilesAfter',[]),('redEvidence',[])):
            self.fixture.check()
            manifest=copy.deepcopy(original);manifest[field]=value
            self.fixture.proof['nativeTests'][0]=self.fixture.write('native-manifest.json',manifest);self.fixture.bind()
            with self.assertRaises(ValueError):self.fixture.check()
            self.fixture.proof['nativeTests'][0]=self.fixture.write('native-manifest.json',original);self.fixture.bind()
        script=Path(original['testFilesBefore'][0]['path']);script.write_text('changed test')
        with self.assertRaisesRegex(ValueError,'hash'):self.fixture.check()

    def test_success_receipt_without_invoked_pinned_test_script_rejected(self):
        receipt=self.fixture.write('native.json',dict(nativeExitCode=0,argv=['unrelated-tests'],
            startedAtUtc='2026-09-15T00:00:00Z',endedAtUtc='2026-09-15T00:00:01Z'))
        self.fixture.test_manifest['receipt']=receipt
        self.fixture.proof['nativeTests'][0]=self.fixture.write('native-manifest.json',self.fixture.test_manifest);self.fixture.bind()
        with self.assertRaisesRegex(ValueError,'command lacks pinned script'):self.fixture.check()

    def test_self_consistent_success_for_another_module_is_rejected(self):
        receipt=json.loads(Path(self.fixture.test_manifest['receipt']['path']).read_bytes())
        receipt['argv'][-1]=self.fixture.old['rec_i3_owned_process.psm1']['path']
        self.fixture.test_manifest['receipt']=self.fixture.write('native.json',receipt)
        self.fixture.proof['nativeTests'][0]=self.fixture.write('native-manifest.json',self.fixture.test_manifest);self.fixture.bind()
        with self.assertRaisesRegex(ValueError,'tested module'):self.fixture.check()

    def test_native_boolean_exit_duplicate_module_and_invalid_utc_interval_rejected(self):
        original=json.loads(Path(self.fixture.test_manifest['receipt']['path']).read_bytes())
        for field,value in (('nativeExitCode',False),('timedOut',0),
                            ('argv',original['argv']+original['argv'][-2:]),
                            ('startedAtUtc','not-a-time'),('startedAtUtc','2026-09-15T00:00:02Z'),
                            ('startedAtUtc','2026-09-15T00:00:00'),('startedAtUtc','2026-09-15T00:00:00+03:00')):
            receipt=dict(original,**{field:value})
            self.fixture.test_manifest['receipt']=self.fixture.write('native.json',receipt)
            self.fixture.proof['nativeTests'][0]=self.fixture.write('native-manifest.json',self.fixture.test_manifest);self.fixture.bind()
            with self.subTest(field=field,value=value),self.assertRaises(ValueError):self.fixture.check()

    def test_native_origin_cannot_carry_unused_script_or_unpinned_executable(self):
        original=json.loads(Path(self.fixture.test_manifest['receipt']['path']).read_bytes())
        for mode in ('executable','unused-script','prefix','extra-file'):
            receipt=copy.deepcopy(original)
            if mode=='executable':receipt['argv'][0]=self.fixture.new[self.fixture.names[0]]['path']
            elif mode=='unused-script':receipt['argv'][7]='unrelated.ps1';receipt['argv'].append(original['argv'][7])
            elif mode=='prefix':receipt['argv'][6]='-Command'
            else:receipt['argv']+=['-File','another.ps1']
            self.fixture.test_manifest['receipt']=self.fixture.write('native.json',receipt)
            self.fixture.proof['nativeTests'][0]=self.fixture.write('native-manifest.json',self.fixture.test_manifest);self.fixture.bind()
            with self.subTest(mode=mode),self.assertRaises(ValueError):self.fixture.check()

    def test_duplicate_native_manifest_or_receipt_cannot_inflate_tests(self):
        original=copy.deepcopy(self.fixture.proof['nativeTests'])
        self.fixture.proof['nativeTests']*=2;self.fixture.bind()
        with self.assertRaisesRegex(ValueError,'Duplicate'):self.fixture.check()
        second=self.fixture.write('same-receipt-manifest.json',self.fixture.test_manifest)
        self.fixture.proof['nativeTests']=original+[second];self.fixture.bind()
        with self.assertRaisesRegex(ValueError,'Duplicate'):self.fixture.check()

    def test_sixteen_distinct_native_runs_are_bounded_and_seventeen_rejected(self):
        receipt=json.loads(Path(self.fixture.test_manifest['receipt']['path']).read_bytes())
        self.fixture.proof['nativeTests']=[]
        for index in range(16):
            manifest=copy.deepcopy(self.fixture.test_manifest)
            manifest['receipt']=self.fixture.write(f'native-{index}.json',receipt)
            self.fixture.proof['nativeTests'].append(self.fixture.write(f'run-{index}.json',manifest))
        self.fixture.bind();self.fixture.check()
        self.fixture.proof['nativeTests'].append(self.fixture.proof['nativeTests'][0]);self.fixture.bind()
        with self.assertRaisesRegex(ValueError,'bounded capture native tests'):self.fixture.check()

    def test_reparse_control_parent_and_nonregular_files_are_rejected(self):
        original=Path.lstat;parent=Path(self.fixture.new[self.fixture.names[0]]['path']).parent
        def reparse(p,*args,**kwargs):
            result=original(p,*args,**kwargs)
            if p==parent:return SimpleNamespace(st_mode=result.st_mode,st_file_attributes=1024)
            return result
        with patch.object(Path,'lstat',reparse):
            with self.assertRaisesRegex(ValueError,'Reparse'):self.fixture.check()
        p=Path(self.fixture.new[self.fixture.names[0]]['path']);p.unlink();p.mkdir()
        with self.assertRaises(ValueError):self.fixture.check()

    def test_binding_cannot_add_whitelist_or_change_old_reduced_metadata(self):
        binding=self.api['ALPHA_CAPTURE_REPAIR_BINDING']
        for key,value in (('allowedLaunchers',[]),('proofSha256','0'*64),('ownedProcessModuleSha256','1'*64)):
            original=copy.deepcopy(binding);binding[key]=value
            with self.assertRaises(ValueError):self.fixture.check()
            binding.clear();binding.update(original)
        self.fixture.metadata_after+='ALPHA_REDUCED_REPAIR_BINDING={}\n'
        with self.assertRaisesRegex(ValueError,'metadata behavior'):self.fixture.check()
        self.fixture.metadata_after=self.fixture.metadata_before+'ALPHA_CAPTURE_REPAIR_BINDING=unsafe_call()\n'
        with self.assertRaises(ValueError):self.fixture.check()




class StreamPathSuccessorTests(unittest.TestCase):
    def setUp(self):
        self.old=CombinedAdmissionTests();self.old.setUp();self.addCleanup(self.old.doCleanups)
        f=self.old
        self.old_profile=f.profile
        self.old_binding=copy.deepcopy(f.binding)
        self.old_capture=copy.deepcopy(f.api['ALPHA_CAPTURE_REPAIR_BINDING'])
        self.historical=copy.deepcopy(f.gate['alphaPreflight']['sourceRepair'])
        self.base='4'*40;self.base_tree='5'*40
        self.profile=candidate.Profile('6'*40,'7'*40,candidate.MAINTENANCE_PATHS)
        self.source=dict(commit='8'*40,tree='9'*40,appApkSha256='2'*64,testApkSha256='3'*64)
        self.paths=frozenset(subject.PREFIX+part+'/'+subject.PACKAGE+name for part,name in (
            ('main','contract/RecoveryBinary.kt'),('main','contract/RecoveryRecords.kt'),
            ('main','candidate/RecoveryStreamingTinkPrerequisiteCrypto.kt'),
            ('test','candidate/RecoveryStreamingTinkPrerequisiteCryptoTest.kt')))
        for entries in (f.facts.before,f.facts.after,f.capture.before,f.capture.after):
            for p in self.paths:entries[p]=dict(mode='100644',type='blob',object='1'*40)
        self.before=copy.deepcopy(f.capture.after);self.after=copy.deepcopy(self.before)
        self.changed=self.paths|subject.CAPTURE_HOST_PATHS
        for p in self.changed:self.after[p]=dict(mode='100644',type='blob',object='7'*40)
        self.api=f.api
        self.api.update(active_profile=lambda:self.profile,
            ALPHA_PREFIX_REPAIR_BINDING={k:self.source[k] for k in ('appApkSha256','testApkSha256')})
        self.original_git=self.api['git']
        self.api['git']=self.git
        patch.object(candidate,'git',side_effect=self.git).start()
        constants=dict(STREAM_PATH_BASELINE_COMMIT=self.base,STREAM_PATH_BASELINE_TREE=self.base_tree,
            STREAM_PATH_OLD_IMPLEMENTATION_COMMIT=self.old_profile.implementation_commit,
            STREAM_PATH_OLD_IMPLEMENTATION_TREE=self.old_profile.implementation_tree,
            STREAM_PATH_OLD_PREFIX_BINDING=self.old_binding,STREAM_PATH_OLD_CAPTURE_BINDING=self.old_capture)
        for key,value in constants.items():patch.object(subject,key,value,create=True).start()
        self.overrides={}
        self.metadata=f.capture.metadata_after+'ALPHA_STREAM_PATH_REPAIR_BINDING={}\n'
        self.proof=dict(schema='DORA_RECOVERY_STREAM_PATH_REPAIR_APPLICABILITY_V1',scope=subject.SCOPE,
            baseline=dict(commit=self.base,tree=self.base_tree),
            implementation=dict(commit=self.profile.implementation_commit,tree=self.profile.implementation_tree),
            apkPair={k:self.source[k] for k in ('appApkSha256','testApkSha256')},
            sourceDelta=[dict(path=p,before=self.before[p],after=self.after[p]) for p in sorted(self.changed)],
            historicalApplicability=self.historical,captureRepair=copy.deepcopy(f.proof['captureRepair']),
            frozenSelection=f.frozen_descriptor,independentReview=f.review,
            historicalPreflightReusable=False,requiredFreshPreflight=f.proof['requiredFreshPreflight'],
            contract='AUTHENTICATED_CHECKPOINT_EMBEDDED_PATH_TO_EXISTING_UNSAFE_PATH')
        self.plan=dict(source=self.source)
        self.gate=copy.deepcopy(f.gate);self.gate['source']=self.source;self.gate['alphaPreflight']['source']=self.source
        self.bind()

    def git(self,*args,root):
        if args in self.overrides:return self.overrides[args]
        fixed={('merge-base',subject.BASELINE_COMMIT,self.profile.implementation_commit):subject.BASELINE_COMMIT,
               ('rev-parse',self.base+'^{tree}'):self.base_tree,
               ('merge-base',self.base,self.profile.implementation_commit):self.base,
               ('rev-parse',self.profile.implementation_commit+'^{tree}'):self.profile.implementation_tree,
               ('rev-parse','HEAD'):self.source['commit'],('rev-parse',self.source['commit']):self.source['commit'],
               ('rev-parse','HEAD^{tree}'):self.source['tree'],('branch','--show-current'):candidate.BRANCH,
               ('show','-s','--format=%P',self.source['commit']):self.profile.implementation_commit,
               ('rev-list','--min-parents=2',self.profile.implementation_commit+'..'+self.source['commit']):'',
               ('diff-tree','--no-commit-id','--name-only','--no-renames','-z','-r',self.source['commit']):'\0'.join(sorted(candidate.MAINTENANCE_PATHS))+'\0',
               ('status','--porcelain'):'',
               ('show',self.base+':tools/validate_recovery_0d6_candidate.py'):self.old.capture.metadata_after,
               ('show',self.profile.implementation_commit+':tools/validate_recovery_0d6_candidate.py'):self.old.capture.metadata_after,
               ('show','HEAD:tools/validate_recovery_0d6_candidate.py'):self.metadata}
        if args in fixed:return fixed[args]
        if len(args)==4 and args[:3]==('ls-tree',self.source['commit'],'--'):
            return '100644 blob '+'9'*40+'\t'+args[3]
        for revision,entries in ((self.base,self.before),(self.profile.implementation_commit,self.after)):
            if args==('ls-tree','-r','-z',revision):
                return ''.join(f"{v['mode']} {v['type']} {v['object']}\t{p}\0" for p,v in sorted(entries.items()))
        return self.original_git(*args,root=root)

    def bind(self):
        descriptor=self.old.write('stream-path-proof.json',self.proof)
        self.api['ALPHA_PREFIX_REPAIR_BINDING']['applicabilitySha256']=descriptor['sha256']
        self.api['ALPHA_STREAM_PATH_REPAIR_BINDING']=dict(proofSha256=descriptor['sha256'])
        self.gate['alphaPreflight']['sourceRepair']=descriptor

    def check(self):subject.validate_preflight(self.plan,self.gate)

    def test_exact_successor_with_unchanged_historical_proofs_accepts_static_only(self):
        self.check()
        gate=copy.deepcopy(self.gate);decision=gate.pop('alphaPreflight');decision['scope']=subject.SCOPE
        selection=copy.deepcopy(self.old.frozen);selection['executionId']='FRESH-PATH'
        gate.update(alphaReduced=decision,supportedPayloads=['CAMPAIGN'],reducedSelection=selection)
        with self.assertRaisesRegex(ValueError,'Malformed descriptor'):subject.validate(self.plan,gate)

    def test_inert_facts_builder_recomputes_document_and_historical_controls(self):
        proof,frozen,controls=subject.stream_path_facts(self.api,self.profile,
            self.api['ALPHA_PREFIX_REPAIR_BINDING'],self.historical,self.proof['captureRepair'],self.old.review)
        self.assertEqual(self.proof,proof)
        self.assertEqual(self.old.frozen,frozen)
        self.assertEqual(self.old.capture.new,controls)

    def test_successor_preflight_origin_uses_historical_controls_and_current_pair(self):
        files={}
        for name in ('adb.exe','python.exe','app.apk','test.apk'):
            p=self.old.root/name;p.write_text('new exact '+name);files[name]=p
        for constant,name in (('ADB_SHA256','adb.exe'),('PYTHON_SHA256','python.exe')):
            patch.object(subject,constant,subject.file_sha(files[name])).start()
        for key,name in (('appApkSha256','app.apk'),('testApkSha256','test.apk')):
            self.source[key]=subject.file_sha(files[name]);self.api['ALPHA_PREFIX_REPAIR_BINDING'][key]=self.source[key]
            self.proof['apkPair'][key]=self.source[key]
        self.bind();self.check()
        plan=self.old.write('origin-plan.json',self.plan);gate=self.old.write('origin-gate.json',self.gate)
        launcher=self.old.capture.new['Invoke-0D6Campaign.ps1']
        pin=dict(schema='DORA_0D6_PRIVATE_LAUNCH_PIN_V1',sourceRoot=str(subject.ROOT),
            ownerSessionId='PATH-FRESH',launcherSha256=launcher['sha256'],
            pythonPath=str(files['python.exe']),pythonSha256=subject.PYTHON_SHA256,
            appApkPath=str(files['app.apk']),testApkPath=str(files['test.apk']),
            driverSha256=subject.file_sha(subject.ROOT/'tools/recovery_campaign.py'),
            planPath=plan['path'],planFileSha256=plan['sha256'],gatePath=gate['path'],gateFileSha256=gate['sha256'])
        attempt=dict(ownerSessionId='PATH-FRESH',payload='JOURNAL_CONNECTIONS',pin=self.old.write('origin-pin.json',pin))
        receipt=json.loads(Path(self.old.capture.test_manifest['receipt']['path']).read_bytes())
        launch=dict(argv=[receipt['argv'][0],'-NoLogo','-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass',
            '-File',launcher['path'],'-PinPath',attempt['pin']['path'],'-ApprovedPinSha256',attempt['pin']['sha256'],'-Execute'])
        native=dict(argv=[str(files['adb.exe'])]+subject.instrument_arguments('JOURNAL_CONNECTIONS',self.source))
        original=subject.runpy.run_path
        def execute_gate(p,g,payload):
            self.assertEqual('JOURNAL_CONNECTIONS',payload)
            subject.validate_preflight(p,g)
        def run_path(path):
            if Path(path)==subject.ROOT/'tools/recovery_campaign.py':return dict(validate_execution_gate=execute_gate)
            return original(path)
        with patch.object(subject.runpy,'run_path',side_effect=run_path):
            subject.validate_preflight_origin(attempt,pin,native,launch,self.source)
            launch['argv'][7]=self.old.capture.old['Invoke-0D6Campaign.ps1']['path']
            with self.assertRaises(ValueError):subject.validate_preflight_origin(attempt,pin,native,launch,self.source)

    def test_historical_controls_receipts_and_review_are_rehashed(self):
        self.check()
        paths=[Path(self.old.capture.new['rec_i3_owned_process.psm1']['path']),
               Path(self.old.capture.test_manifest['receipt']['path']),Path(self.old.review['path'])]
        for path in paths:
            data=path.read_bytes();path.write_bytes(data+b'drift')
            try:
                with self.assertRaises(ValueError):self.check()
            finally:path.write_bytes(data)
            self.check()

    def test_both_current_routes_reject_legacy_preflight_source(self):
        self.check()
        transcript=FreshPreflightTests();transcript.setUp();self.addCleanup(transcript.doCleanups)
        gate=copy.deepcopy(self.gate);decision=gate.pop('alphaPreflight');decision['scope']=subject.SCOPE
        selection=copy.deepcopy(self.old.frozen);selection['executionId']='FRESH-PATH'
        gate.update(alphaReduced=decision,supportedPayloads=['CAMPAIGN'],reducedSelection=selection)
        decision['freshPreflight']=transcript.write('old-source-proof.json',transcript.proof)
        with self.assertRaisesRegex(ValueError,'Fresh preflight source'):subject.validate(self.plan,gate)

    def test_malformed_successor_binding_never_falls_back(self):
        self.check()
        for binding in (None,{},dict(proofSha256='0'*64),dict(proofSha256=self.api['ALPHA_PREFIX_REPAIR_BINDING']['applicabilitySha256'],extra=True)):
            self.api['ALPHA_STREAM_PATH_REPAIR_BINDING']=binding
            with self.assertRaises(ValueError):self.check()
        self.api.pop('ALPHA_STREAM_PATH_REPAIR_BINDING')
        with self.assertRaises(ValueError):self.check()

    def test_each_missing_extra_deleted_or_nonregular_source_change_rejects(self):
        self.check();original=copy.deepcopy(self.after)
        for p in self.changed:
            self.after=copy.deepcopy(original);self.after[p]=self.before[p]
            with self.assertRaises(ValueError):self.check()
        for p in ('android/build.gradle.kts','tools/recovery_campaign.py','docs/adr/other.md'):
            self.after=copy.deepcopy(original);self.after[p]=dict(mode='100644',type='blob',object='8'*40)
            with self.assertRaises(ValueError):self.check()
        p=sorted(self.paths)[0]
        for mode in (None,'100755','120000'):
            self.after=copy.deepcopy(original)
            if mode is None:del self.after[p]
            else:self.after[p]['mode']=mode
            with self.assertRaises(ValueError):self.check()

    def test_rehashed_wrong_historical_proofs_and_capture_binding_reject(self):
        self.check();original=copy.deepcopy(self.proof)
        old=json.loads(Path(self.historical['path']).read_bytes());old['historicalPreflightReusable']=True
        self.proof['historicalApplicability']=self.old.write('false-history.json',old);self.bind()
        with self.assertRaises(ValueError):self.check()
        self.proof=original;self.bind();self.check()
        self.api['ALPHA_CAPTURE_REPAIR_BINDING']=dict(self.old_capture,launcherSha256='f'*64)
        with self.assertRaises(ValueError):self.check()

    def test_current_source_proof_apks_preflight_and_authority_fields_are_exact(self):
        self.check();original=copy.deepcopy(self.proof)
        for field,value in (('apkPair',self.old_capture),('historicalPreflightReusable',True),('contract','ALLOW_NEW_FAILURES'),('sourceDelta',[]),('coverage',True)):
            self.proof=dict(original,**{field:value});self.bind()
            with self.assertRaises(ValueError):self.check()
        self.proof=original;self.bind();self.check()
        self.gate['source']=dict(self.source,appApkSha256=self.old_binding['appApkSha256'])
        with self.assertRaises(ValueError):self.check()

    def test_new_metadata_binding_cannot_mask_behavior_or_other_binding_changes(self):
        self.check();original=self.metadata
        for content in (original.replace('return True','return False'),
                        original+'ALPHA_REDUCED_REPAIR_BINDING={}\n',
                        original.replace('ALPHA_STREAM_PATH_REPAIR_BINDING={}','ALPHA_STREAM_PATH_REPAIR_BINDING=unsafe_call()'),
                        original+'ALPHA_STREAM_PATH_REPAIR_BINDING={}\n'):
            self.metadata=content
            with self.assertRaises(ValueError):self.check()
        self.metadata=original

    def test_wrong_baseline_ancestry_and_dirty_current_candidate_reject(self):
        self.check()
        for query,value in ((('rev-parse',self.base+'^{tree}'),'f'*40),
                            (('merge-base',self.base,self.profile.implementation_commit),'f'*40),
                            (('status','--porcelain'),' M runtime.kt'),
                            (('show','-s','--format=%P',self.source['commit']),'f'*40)):
            self.overrides[query]=value
            with self.assertRaises(ValueError):self.check()
            self.overrides.clear()


class StreamPathFixtureIsolationTests(unittest.TestCase):
    def test_historical_fixture_is_independent_of_installed_successor_metadata(self):
        binding=dict(proofSha256='f'*64)
        with patch.object(candidate,'ALPHA_STREAM_PATH_REPAIR_BINDING',binding,create=True):
            fixture=CombinedAdmissionTests();fixture.setUp();self.addCleanup(fixture.doCleanups)
            self.assertNotIn('ALPHA_STREAM_PATH_REPAIR_BINDING',fixture.api)
            self.assertEqual(binding,candidate.ALPHA_STREAM_PATH_REPAIR_BINDING)
            fixture.check()


class StreamPathNoFallbackTests(unittest.TestCase):
    def test_new_binding_duplicate_dictionary_key_is_not_an_exact_literal(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);(root/'tools').mkdir()
            (root/'tools/validate_recovery_0d6_candidate.py').write_text(
                'ALPHA_STREAM_PATH_REPAIR_BINDING={"proofSha256":"'+'a'*64+'","proofSha256":"'+'b'*64+'"}\n')
            with patch.object(subject,'ROOT',root),self.assertRaises(ValueError):subject.candidate_api()

    def test_new_binding_call_rejected_before_metadata_side_effect(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);(root/'tools').mkdir();marker=root/'executed'
            metadata=root/'tools/validate_recovery_0d6_candidate.py'
            metadata.write_text('from pathlib import Path\n'
                'def payload():\n'
                f'    Path({str(marker)!r}).write_text("executed")\n'
                '    return {"proofSha256":"'+'a'*64+'"}\n'
                'ALPHA_STREAM_PATH_REPAIR_BINDING=payload()\n')
            rejected=False
            with patch.object(subject,'ROOT',root):
                try:subject.candidate_api()
                except ValueError:rejected=True
            self.assertFalse(marker.exists(), 'Metadata binding executed before rejection')
            self.assertTrue(rejected)

    def test_new_empty_binding_cannot_authorize_old_route(self):
        fixture=CombinedAdmissionTests();fixture.setUp();self.addCleanup(fixture.doCleanups)
        fixture.api['ALPHA_STREAM_PATH_REPAIR_BINDING']={}
        with self.assertRaises(ValueError):fixture.check()

class CollectorQuerySuccessorTests(unittest.TestCase):
    def setUp(self):
        self.old=StreamPathSuccessorTests();self.old.setUp();self.addCleanup(self.old.doCleanups)
        old=self.old
        self.historical=copy.deepcopy(old.gate['alphaPreflight']['sourceRepair'])
        self.old_binding=copy.deepcopy(old.api['ALPHA_PREFIX_REPAIR_BINDING'])
        self.base=old.source['commit'];self.base_tree=old.source['tree']
        self.profile=candidate.Profile('b'*40,'c'*40,candidate.MAINTENANCE_PATHS)
        self.source=dict(old.source,commit='d'*40,tree='e'*40)
        self.before=copy.deepcopy(old.after);self.after=copy.deepcopy(self.before)
        for p in subject.CAPTURE_HOST_PATHS:self.after[p]=dict(mode='100644',type='blob',object='a'*40)
        self.api=dict(old.api,git=self.git,active_profile=lambda:self.profile,
                      ALPHA_PREFIX_REPAIR_BINDING=copy.deepcopy(self.old_binding))
        self.overrides={};self.metadata=old.metadata+'ALPHA_COLLECTOR_QUERY_BINDING={}\n'
        patch.object(candidate,'git',side_effect=self.git).start()
        patch.object(subject,'candidate_api',return_value=self.api).start()
        for key,value in dict(COLLECTOR_BASELINE_COMMIT=self.base,COLLECTOR_BASELINE_TREE=self.base_tree,
            COLLECTOR_OLD_IMPLEMENTATION_COMMIT=old.profile.implementation_commit,
            COLLECTOR_OLD_IMPLEMENTATION_TREE=old.profile.implementation_tree,
            COLLECTOR_OLD_PREFIX_BINDING=self.old_binding).items():
            patch.object(subject,key,value,create=True).start()
        folder=old.old.root/'query-controls';folder.mkdir();self.controls={}
        self.launcher_bytes=b'exact synthetic launcher pin replacement'
        patch.object(subject,'collector_launcher_bytes',return_value=self.launcher_bytes).start()
        for name,descriptor in old.old.capture.new.items():
            data=Path(descriptor['path']).read_bytes()
            if name=='rec_i3_owned_process.psm1':data+=b' query diagnostic successor'
            if name=='Invoke-0D6Campaign.ps1':data=self.launcher_bytes
            p=folder/name;p.write_bytes(data)
            self.controls[name]=dict(path=str(p),sha256=subject.file_sha(p))
        self.tests={}
        for scenario in ('delayed','missed','capacity','lifetime','owner','image','startup','deadline','identity','streams','historical','query'):
            manifest=copy.deepcopy(old.old.capture.test_manifest)
            manifest.update(controlsBefore=self.controls,controlsAfter=self.controls)
            receipt=json.loads(Path(manifest['receipt']['path']).read_bytes())
            receipt['argv'][-1]=self.controls['rec_i3_owned_process.psm1']['path']
            receipt['argv']+=['-Scenario',scenario]
            manifest['receipt']=old.old.write('query-'+scenario+'-receipt.json',receipt)
            self.tests[scenario]=old.old.write('query-'+scenario+'-manifest.json',manifest)
        self.plan=dict(source=self.source);self.gate=copy.deepcopy(old.gate)
        self.gate['source']=self.source;self.gate['alphaPreflight']['source']=self.source
        self.refresh()

    def git(self,*args,root):
        if args in self.overrides:return self.overrides[args]
        fixed={('rev-parse',self.base+'^{tree}'):self.base_tree,
            ('merge-base',self.base,self.profile.implementation_commit):self.base,
            ('rev-parse',self.profile.implementation_commit+'^{tree}'):self.profile.implementation_tree,
            ('rev-parse','HEAD'):self.source['commit'],('rev-parse',self.source['commit']):self.source['commit'],
            ('rev-parse','HEAD^{tree}'):self.source['tree'],('branch','--show-current'):candidate.BRANCH,
            ('show','-s','--format=%P',self.source['commit']):self.profile.implementation_commit,
            ('rev-list','--min-parents=2',self.profile.implementation_commit+'..'+self.source['commit']):'',
            ('diff-tree','--no-commit-id','--name-only','--no-renames','-z','-r',self.source['commit']):'\0'.join(sorted(candidate.MAINTENANCE_PATHS))+'\0',
            ('status','--porcelain'):'',
            ('show',self.base+':tools/validate_recovery_0d6_candidate.py'):self.old.metadata,
            ('show',self.profile.implementation_commit+':tools/validate_recovery_0d6_candidate.py'):self.old.metadata,
            ('show','HEAD:tools/validate_recovery_0d6_candidate.py'):self.metadata}
        if args in fixed:return fixed[args]
        if len(args)==4 and args[:3]==('ls-tree',self.source['commit'],'--'):
            return '100644 blob '+'e'*40+'\t'+args[3]
        for revision,entries in ((self.base,self.before),(self.profile.implementation_commit,self.after)):
            if args==('ls-tree','-r','-z',revision):
                return ''.join(f"{v['mode']} {v['type']} {v['object']}\t{p}\0" for p,v in sorted(entries.items()))
        return self.old.git(*args,root=root)

    def refresh(self):
        self.api['ALPHA_COLLECTOR_QUERY_BINDING']=dict(proofSha256='0'*64,
            ownedProcessModuleSha256=self.controls['rec_i3_owned_process.psm1']['sha256'])
        self.proof,_,_=subject.collector_query_facts(self.api,self.profile,self.api['ALPHA_PREFIX_REPAIR_BINDING'],
            self.historical,self.controls,self.tests,self.old.old.review)
        self.bind()

    def bind(self):
        d=self.old.old.write('query-proof.json',self.proof)
        self.api['ALPHA_PREFIX_REPAIR_BINDING']['applicabilitySha256']=d['sha256']
        self.api['ALPHA_COLLECTOR_QUERY_BINDING']['proofSha256']=d['sha256']
        self.gate['alphaPreflight']['sourceRepair']=d

    def check(self):subject.validate_preflight(self.plan,self.gate)

    def test_host_only_successor_revalidates_history_and_returns_new_controls(self):
        self.check()
        _,controls=subject.validate_collector_query_proof(self.api,self.profile,
            self.api['ALPHA_PREFIX_REPAIR_BINDING'],self.proof,self.gate['alphaPreflight']['sourceRepair'],self.old.old.review)
        self.assertEqual(self.controls,controls)
        self.assertFalse(self.proof['historicalPreflightReusable'])
        self.assertEqual(self.old_binding['applicabilitySha256'],self.historical['sha256'])

    def test_malformed_missing_or_foreign_binding_never_falls_back(self):
        self.check();original=copy.deepcopy(self.api['ALPHA_COLLECTOR_QUERY_BINDING'])
        for value in (None,{},dict(original,extra=True),dict(original,proofSha256='f'*64)):
            self.api['ALPHA_COLLECTOR_QUERY_BINDING']=value
            with self.assertRaises(ValueError):self.check()
        self.api.pop('ALPHA_COLLECTOR_QUERY_BINDING')
        with self.assertRaises(ValueError):self.check()

    def test_android_build_fixture_and_driver_changes_are_not_host_repair(self):
        original=copy.deepcopy(self.after)
        for path in ('android/build.gradle.kts','tools/recovery_campaign.py',next(iter(subject.STREAM_PATH_ANDROID_PATHS))):
            self.after=copy.deepcopy(original);self.after[path]=dict(mode='100644',type='blob',object='f'*40)
            with self.assertRaisesRegex(ValueError,'host-only'):self.refresh()
        self.after=original
        p=next(iter(subject.CAPTURE_HOST_PATHS));self.after[p]=self.before[p]
        with self.assertRaisesRegex(ValueError,'host-only'):self.refresh()

    def test_same_module_changed_other_control_and_foreign_sibling_rejected(self):
        original=copy.deepcopy(self.controls)
        for mode in ('same-module','other-control','foreign-parent'):
            self.controls=copy.deepcopy(original)
            name='rec_i3_owned_process.psm1' if mode=='same-module' else 'Logcat-Capture.ps1'
            p=Path(self.controls[name]['path']);data=p.read_bytes()
            if mode=='same-module':p.write_bytes(Path(self.old.old.capture.new[name]['path']).read_bytes())
            elif mode=='other-control':p.write_bytes(data+b'changed')
            else:
                p=self.old.old.root/name;p.write_bytes(data);self.controls[name]['path']=str(p)
            self.controls[name]['sha256']=subject.file_sha(p)
            with self.assertRaises(ValueError):self.refresh()
            Path(original[name]['path']).write_bytes(data)

    def test_missing_or_duplicate_native_scenarios_and_failed_receipt_rejected(self):
        original=copy.deepcopy(self.tests)
        self.tests.pop('query')
        with self.assertRaises(ValueError):self.refresh()
        self.tests=copy.deepcopy(original);self.tests['query']=self.tests['delayed']
        with self.assertRaises(ValueError):self.refresh()
        self.tests=original
        manifest=json.loads(Path(self.tests['query']['path']).read_bytes())
        receipt=json.loads(Path(manifest['receipt']['path']).read_bytes());receipt['nativeExitCode']=2
        manifest['receipt']=self.old.old.write('query-failed.json',receipt)
        self.tests['query']=self.old.old.write('query-failed-manifest.json',manifest)
        with self.assertRaises(ValueError):self.refresh()

    def test_rehashed_claims_cannot_change_apks_history_or_authority(self):
        self.check();original=copy.deepcopy(self.proof)
        for key,value in (('apkPair',dict(self.proof['apkPair'],appApkSha256='f'*64)),
                          ('historicalPreflightReusable',True),('sourceDelta',[]),('coverageGranted',True)):
            self.proof=dict(original,**{key:value});self.bind()
            with self.assertRaises(ValueError):self.check()
        self.proof=original;self.bind()
        self.api['ALPHA_STREAM_PATH_REPAIR_BINDING']=dict(proofSha256='f'*64)
        with self.assertRaises(ValueError):self.check()

    def test_metadata_cannot_change_behavior_or_execute_binding(self):
        self.check();original=self.metadata
        for value in (original.replace('return True','return False'),original+'ALPHA_COLLECTOR_QUERY_BINDING={}\n',
                      original.replace('ALPHA_COLLECTOR_QUERY_BINDING={}','ALPHA_COLLECTOR_QUERY_BINDING=unsafe_call()')):
            self.metadata=value
            with self.assertRaises(ValueError):self.check()


class CollectorQueryLiteralTests(unittest.TestCase):
    def test_launcher_derivative_is_only_exact_embedded_module_digest(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'Invoke-0D6Campaign.ps1'
            original=b"if(hash-ne'"+b'A'*64+b"'){throw 'mismatch'}\r\n"
            path.write_bytes(original)
            controls={'Invoke-0D6Campaign.ps1':dict(path=str(path),sha256=subject.file_sha(path)),
                      'rec_i3_owned_process.psm1':dict(sha256='a'*64)}
            self.assertEqual(original.replace(b'A'*64,b'B'*64),subject.collector_launcher_bytes(controls,'b'*64))
            for data in (b'no embedded digest',original+original):
                path.write_bytes(data);controls['Invoke-0D6Campaign.ps1']['sha256']=subject.file_sha(path)
                with self.assertRaises(ValueError):subject.collector_launcher_bytes(controls,'b'*64)

    def test_binding_rejected_before_metadata_execution(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);(root/'tools').mkdir();marker=root/'executed'
            metadata=root/'tools/validate_recovery_0d6_candidate.py'
            metadata.write_text(f'from pathlib import Path\nALPHA_COLLECTOR_QUERY_BINDING=Path({str(marker)!r}).touch()\n')
            with patch.object(subject,'ROOT',root),self.assertRaises(ValueError):subject.candidate_api()
            self.assertFalse(marker.exists())


class GitQuerySuccessorTests(unittest.TestCase):
    def setUp(self):
        self.old=CollectorQuerySuccessorTests();self.old.setUp();self.addCleanup(self.old.doCleanups)
        old=self.old
        self.historical=copy.deepcopy(old.gate['alphaPreflight']['sourceRepair'])
        self.old_binding=copy.deepcopy(old.api['ALPHA_PREFIX_REPAIR_BINDING'])
        self.base=old.source['commit'];self.base_tree=old.source['tree']
        self.profile=candidate.Profile('2'*40,'3'*40,candidate.MAINTENANCE_PATHS)
        self.source=dict(old.source,commit='4'*40,tree='5'*40)
        self.before=copy.deepcopy(old.after);self.after=copy.deepcopy(self.before)
        for p in subject.CAPTURE_HOST_PATHS:self.after[p]=dict(mode='100644',type='blob',object='6'*40)
        self.api=dict(old.api,git=self.git,active_profile=lambda:self.profile,
                      ALPHA_PREFIX_REPAIR_BINDING=copy.deepcopy(self.old_binding))
        self.metadata=old.metadata+'ALPHA_GIT_QUERY_BINDING={}\n'
        patch.object(candidate,'git',side_effect=self.git).start()
        patch.object(subject,'candidate_api',return_value=self.api).start()
        for key,value in dict(GIT_QUERY_BASELINE_COMMIT=self.base,GIT_QUERY_BASELINE_TREE=self.base_tree,
            GIT_QUERY_OLD_IMPLEMENTATION_COMMIT=old.profile.implementation_commit,
            GIT_QUERY_OLD_IMPLEMENTATION_TREE=old.profile.implementation_tree,
            GIT_QUERY_OLD_PREFIX_BINDING=self.old_binding,
            GIT_QUERY_OLD_COLLECTOR_BINDING=copy.deepcopy(old.api['ALPHA_COLLECTOR_QUERY_BINDING'])).items():
            patch.object(subject,key,value,create=True).start()
        folder=old.old.old.root/'git-controls';folder.mkdir();self.controls={}
        self.launcher_bytes=b'exact synthetic lifecycle digest replacement'
        patch.object(subject,'git_query_launcher_bytes',return_value=self.launcher_bytes,create=True).start()
        for name,descriptor in old.controls.items():
            data=Path(descriptor['path']).read_bytes()
            if name=='Attempt05-Lifecycle-Functions.ps1':data+=b' suspended Git query'
            if name=='Invoke-0D6Campaign.ps1':data=self.launcher_bytes
            p=folder/name;p.write_bytes(data)
            self.controls[name]=dict(path=str(p),sha256=subject.file_sha(p))
        manifest=json.loads(Path(old.tests['query']['path']).read_bytes())
        manifest.update(controlsBefore=self.controls,controlsAfter=self.controls)
        receipt=json.loads(Path(manifest['receipt']['path']).read_bytes())
        at=receipt['argv'].index('-ModulePath')+1;receipt['argv'][at]=self.controls['rec_i3_owned_process.psm1']['path']
        manifest['receipt']=old.old.old.write('git-native-receipt.json',receipt)
        self.native=old.old.old.write('git-native-manifest.json',manifest)
        self.plan=dict(source=self.source);self.gate=copy.deepcopy(old.gate)
        self.gate['source']=self.source;self.gate['alphaPreflight']['source']=self.source
        self.api['ALPHA_GIT_QUERY_BINDING']=dict(proofSha256='0'*64,
            lifecycleLibrarySha256=self.controls['Attempt05-Lifecycle-Functions.ps1']['sha256'])
        self.refresh()

    def git(self,*args,root):
        fixed={('rev-parse',self.base+'^{tree}'):self.base_tree,
            ('merge-base',self.base,self.profile.implementation_commit):self.base,
            ('rev-parse',self.profile.implementation_commit+'^{tree}'):self.profile.implementation_tree,
            ('rev-parse','HEAD'):self.source['commit'],('rev-parse',self.source['commit']):self.source['commit'],
            ('rev-parse','HEAD^{tree}'):self.source['tree'],('branch','--show-current'):candidate.BRANCH,
            ('show','-s','--format=%P',self.source['commit']):self.profile.implementation_commit,
            ('rev-list','--min-parents=2',self.profile.implementation_commit+'..'+self.source['commit']):'',
            ('diff-tree','--no-commit-id','--name-only','--no-renames','-z','-r',self.source['commit']):'\0'.join(sorted(candidate.MAINTENANCE_PATHS))+'\0',
            ('status','--porcelain'):'',
            ('show',self.base+':tools/validate_recovery_0d6_candidate.py'):self.old.metadata,
            ('show',self.profile.implementation_commit+':tools/validate_recovery_0d6_candidate.py'):self.old.metadata,
            ('show','HEAD:tools/validate_recovery_0d6_candidate.py'):self.metadata}
        if args in fixed:return fixed[args]
        if len(args)==4 and args[:3]==('ls-tree',self.source['commit'],'--'):
            return '100644 blob '+'e'*40+'\t'+args[3]
        for revision,entries in ((self.base,self.before),(self.profile.implementation_commit,self.after)):
            if args==('ls-tree','-r','-z',revision):
                return ''.join(f"{v['mode']} {v['type']} {v['object']}\t{p}\0" for p,v in sorted(entries.items()))
        return self.old.git(*args,root=root)

    def refresh(self):
        self.proof,_,_=subject.git_query_facts(self.api,self.profile,self.api['ALPHA_PREFIX_REPAIR_BINDING'],
            self.historical,self.controls,self.native,self.old.old.old.review)
        self.bind()

    def bind(self):
        descriptor=self.old.old.old.write('git-proof.json',self.proof)
        self.api['ALPHA_PREFIX_REPAIR_BINDING']['applicabilitySha256']=descriptor['sha256']
        self.api['ALPHA_GIT_QUERY_BINDING']['proofSha256']=descriptor['sha256']
        self.gate['alphaPreflight']['sourceRepair']=descriptor

    def check(self):subject.validate_preflight(self.plan,self.gate)

    def test_exact_git_successor_rehashes_collector_history(self):
        self.check()
        _,controls=subject.validate_git_query_proof(self.api,self.profile,self.api['ALPHA_PREFIX_REPAIR_BINDING'],
            self.proof,self.gate['alphaPreflight']['sourceRepair'],self.old.old.old.review)
        self.assertEqual(self.controls,controls)
        self.assertEqual(self.old.controls['rec_i3_owned_process.psm1']['sha256'],controls['rec_i3_owned_process.psm1']['sha256'])
        self.assertFalse(self.proof['historicalPreflightReusable'])

    def test_malformed_binding_and_changed_history_cannot_fallback(self):
        self.check();original=copy.deepcopy(self.api['ALPHA_GIT_QUERY_BINDING'])
        for value in (None,{},dict(original,extra=True),dict(original,proofSha256='f'*64)):
            self.api['ALPHA_GIT_QUERY_BINDING']=value
            with self.assertRaises(ValueError):self.check()
        self.api['ALPHA_GIT_QUERY_BINDING']=original
        self.api['ALPHA_COLLECTOR_QUERY_BINDING']=dict(proofSha256='f'*64,ownedProcessModuleSha256='e'*64)
        with self.assertRaises(ValueError):self.check()

    def test_unrelated_source_or_native_module_change_is_rejected(self):
        self.check()
        self.after['android/build.gradle.kts']=dict(mode='100644',type='blob',object='7'*40)
        with self.assertRaisesRegex(ValueError,'host-only'):self.refresh()
        self.after.pop('android/build.gradle.kts')
        path=Path(self.controls['rec_i3_owned_process.psm1']['path']);path.write_bytes(path.read_bytes()+b'changed')
        self.controls[path.name]['sha256']=subject.file_sha(path)
        with self.assertRaisesRegex(ValueError,'control'):self.refresh()

    def test_original_native_success_and_exact_proof_are_required(self):
        self.check();original=copy.deepcopy(self.proof)
        for key,value in (('historicalPreflightReusable',True),('sourceDelta',[]),('coverageGranted',True)):
            self.proof=dict(original,**{key:value});self.bind()
            with self.assertRaises(ValueError):self.check()
        self.proof=original;self.bind()
        manifest=json.loads(Path(self.native['path']).read_bytes())
        receipt=json.loads(Path(manifest['receipt']['path']).read_bytes());receipt['nativeExitCode']=1
        manifest['receipt']=self.old.old.old.write('git-failed-native.json',receipt)
        self.native=self.old.old.old.write('git-failed-manifest.json',manifest)
        with self.assertRaises(ValueError):self.refresh()

    def test_metadata_behavior_is_not_admitted(self):
        self.check()
        self.metadata+='dangerous_call()\n'
        with self.assertRaisesRegex(ValueError,'behavior'):self.check()


class GitQueryLiteralTests(unittest.TestCase):
    def test_only_single_exact_library_digest_can_change_in_launcher(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'Invoke-0D6Campaign.ps1';data=b"pin='"+b'A'*64+b"'\n"
            path.write_bytes(data)
            controls={'Invoke-0D6Campaign.ps1':dict(path=str(path),sha256=subject.file_sha(path)),
                      'Attempt05-Lifecycle-Functions.ps1':dict(sha256='a'*64)}
            self.assertEqual(data.replace(b'A'*64,b'B'*64),subject.git_query_launcher_bytes(controls,'b'*64))
            path.write_bytes(data+data);controls[path.name]['sha256']=subject.file_sha(path)
            with self.assertRaises(ValueError):subject.git_query_launcher_bytes(controls,'b'*64)

    def test_nonliteral_git_binding_is_rejected_before_execution(self):
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);(root/'tools').mkdir();marker=root/'executed'
            (root/'tools/validate_recovery_0d6_candidate.py').write_text(
                f'from pathlib import Path\nALPHA_GIT_QUERY_BINDING=Path({str(marker)!r}).touch()\n')
            with patch.object(subject,'ROOT',root),self.assertRaises(ValueError):subject.candidate_api()
            self.assertFalse(marker.exists())


class MicrofileDispositionSuccessorTests(unittest.TestCase):
    """Synthetic source/evidence graph; no Git, Android or native subprocesses."""
    def setUp(self):
        self.old=GitQuerySuccessorTests();self.old.setUp();self.addCleanup(self.old.doCleanups)
        old=self.old
        self.write=old.old.old.old.write
        self.root=old.old.old.old.root
        # Distinct from every older synthetic revision, including STREAM's 4/5.
        self.base='ba'*20;self.base_tree='bb'*20
        self.profile=candidate.Profile('ab'*20,'ac'*20,candidate.MAINTENANCE_PATHS)
        self.source=dict(old.source,commit='ad'*20,tree='ae'*20)
        self.before=copy.deepcopy(old.after)
        # Added schema files are genuinely absent in the old tree.
        for p in subject.MICROFILE_DISPOSITION_PATHS-subject.MICROFILE_DISPOSITION_ADDED_PATHS:
            mode='100755' if p=='tools/validate_stage00.py' else '100644'
            self.before.setdefault(p,dict(mode=mode,type='blob',object='af'*20))
        self.after=copy.deepcopy(self.before)
        for p in subject.MICROFILE_DISPOSITION_PATHS:
            mode='100755' if p=='tools/validate_stage00.py' else '100644'
            self.after[p]=dict(mode=mode,type='blob',object='bc'*20)
        self.overrides={}
        self.api=dict(old.api,git=self.git,active_profile=lambda:self.profile,
                      ALPHA_PREFIX_REPAIR_BINDING=copy.deepcopy(old.api['ALPHA_PREFIX_REPAIR_BINDING']))
        self.metadata=old.metadata+'ALPHA_MICROFILE_DISPOSITION_BINDING={}\n'
        for key,value in dict(MICROFILE_DISPOSITION_BASELINE_COMMIT=self.base,
            MICROFILE_DISPOSITION_BASELINE_TREE=self.base_tree,
            MICROFILE_DISPOSITION_OLD_IMPLEMENTATION_COMMIT=old.profile.implementation_commit,
            MICROFILE_DISPOSITION_OLD_IMPLEMENTATION_TREE=old.profile.implementation_tree,
            MICROFILE_DISPOSITION_OLD_PREFIX_BINDING=copy.deepcopy(old.api['ALPHA_PREFIX_REPAIR_BINDING']),
            MICROFILE_DISPOSITION_OLD_GIT_BINDING=copy.deepcopy(old.api['ALPHA_GIT_QUERY_BINDING'])).items():
            p=patch.object(subject,key,value);p.start();self.addCleanup(p.stop)
        for p in (patch.object(candidate,'git',side_effect=self.git),
                  patch.object(subject,'candidate_api',return_value=self.api)):
            p.start();self.addCleanup(p.stop)
        # Build parsing is exercised with real files below. Here isolate recursive
        # historical source/control admission, without constructing a second tree.
        p=patch.object(subject,'validate_microfile_disposition_build')
        p.start();self.addCleanup(p.stop)
        self.historical=copy.deepcopy(old.gate['alphaPreflight']['sourceRepair'])
        self.controls=copy.deepcopy(old.controls)
        self.proposal=self.write('micro-proposal.txt','Exact synthetic approved proposal')
        self.decision=self.write('micro-owner.json',dict(approvedProposal=self.proposal,
            schema='DORA_EXPLICIT_OWNER_DECISION_V1',exactUserReply='Одобряю предложение schema 6',
            schemaVersion=6,sharedSchema5Preserved=True,runtimeAcceptanceGranted=False,
            productFailuresReclassified=False,
            scope=['MICROFILE TRU-03','MICROFILE COR-01','MICROFILE COR-04','MICROFILE TRU-02']))
        for key,value in dict(MICROFILE_DISPOSITION_PROPOSAL_SHA256=self.proposal['sha256'],
                              MICROFILE_DISPOSITION_DECISION_SHA256=self.decision['sha256']).items():
            p=patch.object(subject,key,value);p.start();self.addCleanup(p.stop)
        self.apks={}
        for key in ('appApkSha256','testApkSha256'):
            self.apks[key]=self.write('micro-'+key+'.apk','fresh binary '+key)
            self.source[key]=self.apks[key]['sha256']
            self.api['ALPHA_PREFIX_REPAIR_BINDING'][key]=self.source[key]
        self.build=self.write('micro-build.json',dict(schema='SYNTHETIC_BUILD',nativeExitCode=0))
        self.review_value=dict(schema='DORA_MICROFILE_DISPOSITION_TECHNICAL_REVIEW_V1',
            implementation=dict(commit=self.profile.implementation_commit,tree=self.profile.implementation_tree),
            ownerProposal=self.proposal,ownerDecision=self.decision,build=self.build,apks=self.apks,
            independentTechnicalReview=True,buildAndApkSourceVerified=True,
            historicalCoverageAutomaticallyGranted=False,runtimeAdmissionGranted=False)
        self.review=self.write('micro-review.json',self.review_value)
        self.api['ALPHA_MICROFILE_DISPOSITION_BINDING']=dict(proofSha256='0'*64)
        self.plan=dict(source=self.source);self.gate=copy.deepcopy(old.gate)
        self.gate['source']=self.source;self.gate['alphaPreflight']['source']=self.source
        self.gate['proofs']['independentReview']=self.review
        self.refresh()

    def git(self,*args,root):
        if args in self.overrides:return self.overrides[args]
        fixed={('rev-parse',self.base+'^{tree}'):self.base_tree,
            ('merge-base',self.base,self.profile.implementation_commit):self.base,
            ('rev-parse',self.profile.implementation_commit+'^{tree}'):self.profile.implementation_tree,
            ('rev-parse','HEAD'):self.source['commit'],('rev-parse',self.source['commit']):self.source['commit'],
            ('rev-parse','HEAD^{tree}'):self.source['tree'],('branch','--show-current'):candidate.BRANCH,
            ('show','-s','--format=%P',self.source['commit']):self.profile.implementation_commit,
            ('rev-list','--min-parents=2',self.profile.implementation_commit+'..'+self.source['commit']):'',
            ('diff-tree','--no-commit-id','--name-only','--no-renames','-z','-r',self.source['commit']):'\0'.join(sorted(candidate.MAINTENANCE_PATHS))+'\0',
            ('status','--porcelain'):'',
            ('show',self.base+':tools/validate_recovery_0d6_candidate.py'):self.old.metadata,
            ('show',self.profile.implementation_commit+':tools/validate_recovery_0d6_candidate.py'):self.old.metadata,
            ('show','HEAD:tools/validate_recovery_0d6_candidate.py'):self.metadata}
        if args in fixed:return fixed[args]
        if len(args)==4 and args[:3]==('ls-tree',self.source['commit'],'--'):
            return '100644 blob '+'bc'*20+'\t'+args[3]
        for revision,entries in ((self.base,self.before),(self.profile.implementation_commit,self.after)):
            if args==('ls-tree','-r','-z',revision):
                return ''.join(f"{v['mode']} {v['type']} {v['object']}\t{p}\0" for p,v in sorted(entries.items()))
        return self.old.git(*args,root=root)

    def refresh(self):
        self.proof,_,_=subject.microfile_disposition_facts(self.api,self.profile,
            self.api['ALPHA_PREFIX_REPAIR_BINDING'],self.historical,self.proposal,self.decision,
            self.build,self.apks,self.review)
        self.bind()

    def bind(self):
        d=self.write('micro-proof.json',self.proof)
        self.api['ALPHA_PREFIX_REPAIR_BINDING']['applicabilitySha256']=d['sha256']
        self.api['ALPHA_MICROFILE_DISPOSITION_BINDING']['proofSha256']=d['sha256']
        self.gate['alphaPreflight']['sourceRepair']=d

    def check(self):subject.validate_preflight(self.plan,self.gate)

    def test_exact_scope_preserves_all_recursive_native_controls_and_selection(self):
        self.check()
        self.assertEqual(self.old.proof['frozenSelection'],self.proof['frozenSelection'])
        self.assertEqual(self.controls,self.proof['controls'])
        self.assertFalse(self.proof['historicalPreflightReusable'])
        self.assertEqual(3,len(self.proof['requiredFreshPreflight']))
        self.assertEqual(subject.MICROFILE_DISPOSITION_PATHS,
                         {row['path'] for row in self.proof['sourceDelta']})
        self.assertEqual(self.before,self.old.after | {
            p:v for p,v in self.before.items() if p not in self.old.after})

    def test_wrong_ancestry_tree_extra_deleted_and_mode_are_rejected(self):
        self.check()
        for command in (('merge-base',self.base,self.profile.implementation_commit),
                        ('rev-parse',self.base+'^{tree}'),
                        ('rev-parse',self.profile.implementation_commit+'^{tree}')):
            self.overrides[command]='f'*40
            with self.assertRaises(ValueError):self.refresh()
            self.overrides.clear()
        original=copy.deepcopy(self.after)
        changes=[('android/build.gradle.kts',dict(mode='100644',type='blob',object='f'*40))]
        changes += [(next(iter(subject.MICROFILE_DISPOSITION_PATHS)),None)]
        nonexecutable=sorted(subject.MICROFILE_DISPOSITION_PATHS-{'tools/validate_stage00.py'})[0]
        changes += [(nonexecutable,dict(mode=mode,type=kind,object='f'*40))
                    for mode,kind in (('120000','blob'),('100755','blob'),('160000','commit'))]
        for p,value in changes:
            self.after=copy.deepcopy(original)
            if value is None:self.after.pop(p)
            else:self.after[p]=value
            with self.assertRaises(ValueError):self.refresh()
        self.after=original;self.check()

    def test_stage00_executable_mode_is_preserved_on_both_sides(self):
        self.check()
        for entries in (self.before,self.after):
            row=entries['tools/validate_stage00.py']
            for mode in ('100644','120000','160000'):
                row['mode']=mode
                with self.assertRaises(ValueError):self.refresh()
            row['mode']='100755'
        self.check()

    def test_apk_pair_review_build_and_owner_are_bound(self):
        self.check()
        for field in ('appApkSha256','testApkSha256'):
            original=self.api['ALPHA_PREFIX_REPAIR_BINDING'][field]
            self.api['ALPHA_PREFIX_REPAIR_BINDING'][field]='e'*64
            with self.assertRaises(ValueError):self.refresh()
            self.api['ALPHA_PREFIX_REPAIR_BINDING'][field]=original
        for field in ('proposal','decision','build','review'):
            original=getattr(self,field)
            setattr(self,field,dict(original,sha256='e'*64))
            with self.assertRaises(ValueError):self.refresh()
            setattr(self,field,original)
        for key,value in (('implementation',dict(commit='f'*40,tree='e'*40)),
                          ('buildAndApkSourceVerified',False),('runtimeAdmissionGranted',True)):
            self.review=self.write('micro-bad-review.json',dict(self.review_value,**{key:value}))
            with self.assertRaises(ValueError):self.refresh()
        self.review=self.write('micro-review.json',self.review_value);self.check()

    def test_historical_binding_and_proof_cannot_fallback(self):
        self.check()
        original=copy.deepcopy(self.api['ALPHA_MICROFILE_DISPOSITION_BINDING'])
        for value in (None,{},dict(proofSha256='e'*64),dict(original,extra=True)):
            self.api['ALPHA_MICROFILE_DISPOSITION_BINDING']=value
            with self.assertRaises(ValueError):self.check()
        self.api['ALPHA_MICROFILE_DISPOSITION_BINDING']=original
        old=copy.deepcopy(self.api['ALPHA_GIT_QUERY_BINDING'])
        self.api['ALPHA_GIT_QUERY_BINDING']=dict(old,lifecycleLibrarySha256='f'*64)
        with self.assertRaises(ValueError):self.refresh()
        self.api['ALPHA_GIT_QUERY_BINDING']=old
        original=copy.deepcopy(self.proof)
        for key,value in (('controls',{}),('historicalPreflightReusable',True),('sourceDelta',[]),
                          ('frozenSelection',{}),('coverageGranted',True)):
            self.proof=dict(original,**{key:value});self.bind()
            with self.assertRaises(ValueError):self.check()
        self.proof=original;self.bind();self.check()
        p=Path(self.controls['Attempt05-Lifecycle-Functions.ps1']['path'])
        p.write_bytes(p.read_bytes()+b' changed')
        with self.assertRaises(ValueError):self.check()

    def test_metadata_executable_or_duplicate_changes_are_not_admitted(self):
        self.check();original=self.metadata
        for value in (original+'side_effect()\n',original+'ALPHA_MICROFILE_DISPOSITION_BINDING={}\n',
                      original.replace('ALPHA_MICROFILE_DISPOSITION_BINDING={}',
                                       'ALPHA_MICROFILE_DISPOSITION_BINDING=side_effect()')):
            self.metadata=value
            with self.assertRaises((ValueError,TypeError)):self.check()
        self.metadata=original;self.check()

    def test_campaign_still_needs_three_fresh_preflights_and_original_selection(self):
        self.check();gate=copy.deepcopy(self.gate)
        decision=gate.pop('alphaPreflight');decision['scope']=subject.SCOPE
        frozen=self.old.old.old.old.frozen
        selection=copy.deepcopy(frozen);selection['executionId']='MICROFILE-FRESH'
        gate.update(alphaReduced=decision,supportedPayloads=['CAMPAIGN'],reducedSelection=selection)
        with self.assertRaisesRegex(ValueError,'Malformed descriptor'):subject.validate(self.plan,gate)
        gate['reducedSelection']['slots']=[]
        with self.assertRaises(ValueError):subject.validate(self.plan,gate)


class MicrofileDispositionLiteralTests(unittest.TestCase):
    def test_inert_single_literal_only(self):
        key='ALPHA_MICROFILE_DISPOSITION_BINDING'
        subject.microfile_disposition_metadata_literal(key+'={"proofSha256":"'+'a'*64+'"}\n')
        for value in ('{}','None','payload()',
                      '{"proofSha256":"'+'a'*64+'","proofSha256":"'+'b'*64+'"}'):
            with self.assertRaises(ValueError):subject.microfile_disposition_metadata_literal(key+'='+value)
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);(root/'tools').mkdir();marker=root/'executed'
            (root/'tools/validate_recovery_0d6_candidate.py').write_text(
                f'from pathlib import Path\n{key}=Path({str(marker)!r}).touch()\n')
            with patch.object(subject,'ROOT',root),self.assertRaises(ValueError):subject.candidate_api()
            self.assertFalse(marker.exists())


class MicrofileDispositionBuildTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)/'source';self.root.mkdir()
        self.snapshot=Path(self.temp.name)/'snapshot';self.snapshot.mkdir()
        p=patch.object(subject,'ROOT',self.root);p.start();self.addCleanup(p.stop)
        self.files=[];self.after={};self.copy_index=0
        self.objects={};self.git_overrides={}
        for path in sorted(subject.MICROFILE_DISPOSITION_ANDROID_PATHS|{'tools/test_rec_microfile_disposition_schema.py'}):
            row=self.add(self.root/path,'exact source '+path)
            self.after[path]=dict(mode='100644',type='blob',object=hashlib.sha1(path.encode()).hexdigest())
            self.objects[('--path='+path,row['copy']['path'])]=self.after[path]['object']
        self.pair={}
        for key,path in (
            ('appApkSha256','android/poc/recovery/build/outputs/apk/debug/recovery-debug.apk'),
            ('testApkSha256','android/poc/recovery/build/outputs/apk/androidTest/debug/recovery-debug-androidTest.apk')):
            self.pair[key]=self.add(self.root/path,'apk '+key)['source']['sha256']
        self.owner=self.descriptor(self.snapshot/'owner.json','approved owner')
        tasks=['spotlessCheck','detekt',':poc:recovery:testDebugUnitTest',
            ':poc:recovery:compileDebugAndroidTestKotlin',':poc:recovery:lintDebug',
            ':poc:recovery:assembleDebug',':poc:recovery:assembleDebugAndroidTest']
        init=self.root/'init.gradle';self.add(init,'exact init')
        self.native=dict(argv=['cmd.exe','/d','/c','gradlew.bat','--no-daemon','--offline',
            '--no-configuration-cache','--max-workers=2','--init-script',str(init),*tasks],
            cwd=str(self.root/'android'),startedAtUtc='2026-09-16T01:00:00+00:00',
            endedAtUtc='2026-09-16T01:01:00+00:00',nativeExitCode=0)
        self.add_receipt('schema6-verify-02',self.native,
            '\n'.join('> Task '+(t if t.startswith(':') else ':'+t) for t in tasks)+'\nBUILD SUCCESSFUL\n','')
        host=dict(self.native,argv=['python.exe','-X','utf8','-m','unittest','discover','-s','tools',
                                   '-p','test_rec_*schema.py','-v'],cwd=str(self.root))
        self.add_receipt('schema6-host-green-03',host,'','Ran 10 tests in 1.0s\n\nOK\n')
        self.add(self.root/'android/poc/recovery/build/test-results/testDebugUnitTest/TEST-real.xml',
                 '<testsuite tests="449" failures="0" errors="0" skipped="0"/>')
        self.value=dict(schema='DORA_MICROFILE_SCHEMA6_LOCAL_PASS_SNAPSHOT_V1',phase='verify-02',
            baselineCommit=subject.MICROFILE_DISPOSITION_BASELINE_COMMIT,sourceRoot=str(self.root),
            schemaVersion=6,hostSchemaTests=10,deviceCoverageGranted=False,ciPassed=False,
            apkPair=self.pair,sourcePaths=sorted(subject.MICROFILE_DISPOSITION_ANDROID_PATHS),
            ownerDecision=dict(self.owner,bytes=(self.snapshot/'owner.json').stat().st_size),
            counts=dict(tests=449,failures=0,errors=0,skipped=0),files=self.files)
        self.api={'git':self.git}

    def descriptor(self,path,data):
        path.parent.mkdir(parents=True,exist_ok=True)
        path.write_text(data,encoding='utf-8')
        return dict(path=str(path),sha256=subject.file_sha(path))

    def add(self,path,text):
        self.copy_index+=1
        original=self.descriptor(path,text);original['bytes']=path.stat().st_size
        copy_path=self.snapshot/(str(self.copy_index)+'-'+path.name)
        copy_path.write_bytes(path.read_bytes())
        copied=dict(path=str(copy_path),sha256=subject.file_sha(copy_path),bytes=copy_path.stat().st_size)
        row=dict(source=original,copy=copied);self.files.append(row);return row

    def add_receipt(self,stem,native,stdout,stderr):
        raw=self.root/'native'
        self.add(raw/(stem+'.receipt.json'),json.dumps(native))
        self.add(raw/(stem+'.started.json'),json.dumps({k:native[k] for k in ('argv','cwd','startedAtUtc')}))
        self.add(raw/(stem+'.stdout.log'),stdout);self.add(raw/(stem+'.stderr.log'),stderr)

    def git(self,*args,root):
        self.assertEqual(self.root,root);self.assertEqual('hash-object',args[0])
        key=args[1:]
        return self.git_overrides.get(key,self.objects[key])

    def check(self):
        d=self.descriptor(self.snapshot/'MANIFEST.json',json.dumps(self.value))
        subject.validate_microfile_disposition_build(self.api,self.after,d,self.pair,self.owner)

    def replace_copy(self,name,value):
        row=next(r for r in self.files if Path(r['source']['path']).name==name)
        data=json.dumps(value).encode()
        Path(row['copy']['path']).write_bytes(data)
        for key in ('source','copy'):
            row[key].update(bytes=len(data),sha256=hashlib.sha256(data).hexdigest())

    def test_closed_real_file_fixture_accepts_without_subprocesses(self):self.check()

    def test_missing_source_blob_drift_and_changed_copy_are_rejected(self):
        self.check();row=self.files.pop(0)
        with self.assertRaises(ValueError):self.check()
        self.files.insert(0,row);self.git_overrides[next(iter(self.objects))]='f'*40
        with self.assertRaises(ValueError):self.check()
        self.git_overrides.clear()
        Path(row['copy']['path']).write_bytes(b'changed')
        with self.assertRaises(ValueError):self.check()

    def test_native_failure_or_removed_task_does_not_become_build_pass(self):
        self.check()
        for native in (dict(self.native,nativeExitCode=1),dict(self.native,argv=self.native['argv'][:-1])):
            self.replace_copy('schema6-verify-02.receipt.json',native)
            self.replace_copy('schema6-verify-02.started.json',
                              {k:native[k] for k in ('argv','cwd','startedAtUtc')})
            with self.assertRaises(ValueError):self.check()
        self.replace_copy('schema6-verify-02.receipt.json',self.native)
        self.replace_copy('schema6-verify-02.started.json',
                          {k:self.native[k] for k in ('argv','cwd','startedAtUtc')})
        self.check()

    def test_apk_counts_and_device_credit_cannot_drift(self):
        self.check()
        for key,value in (('apkPair',dict(self.pair,appApkSha256='e'*64)),
                          ('counts',dict(tests=449,failures=1,errors=0,skipped=0)),
                          ('deviceCoverageGranted',True),('sourcePaths',[])):
            original=self.value[key];self.value[key]=value
            with self.assertRaises(ValueError):self.check()
            self.value[key]=original
        self.check()



def historical_tru03_metadata_fixture(text):
    """Project only fixture metadata fields; production validators stay untouched."""
    import ast
    historical = {
        'IMPLEMENTATION_COMMIT': '4279bcd7ad50d5d3f284640602f3e1fc6f351a31',
        'IMPLEMENTATION_TREE': 'f2d8ee2ec9a721f963855421a2d5f61f6ba9f9e3',
        'ALPHA_PREFIX_REPAIR_BINDING': {
            'appApkSha256': '7cce368663e0de0ae287a38c234bab2c6140588a8fa2af3dbf1e883a18f8f064',
            'testApkSha256': '5ed8ca5ede2e0ca12dc23824833ee1c00cbaaf665f78847114f3cb8fc9a2a4b5',
            'applicabilitySha256': 'b71860d73c63bd0215590e9a916dbad3c131a9045da1de1bcb56f8a01e9ba315'}}
    projected = set(historical) | {'ALPHA_MICROFILE_TRU03_BINDING'}
    tree = ast.parse(text); lines = text.splitlines(keepends=True); found = set()
    for node in reversed(tree.body):
        if isinstance(node, ast.Assign) and len(node.targets)==1 and isinstance(node.targets[0], ast.Name):
            name = node.targets[0].id
            if name in projected:
                if name in found: raise AssertionError('Duplicate fixture metadata field')
                found.add(name)
                # Require inert assignments even though only fixed fixture literals survive.
                ast.literal_eval(node.value)
                lines[node.lineno-1:node.end_lineno] = ([name+' = '+json.dumps(historical[name],indent=4)+'\n'] if name in historical else [])
    if not set(historical)<=found: raise AssertionError('Missing fixture metadata field')
    def retained(source):
        parsed=ast.parse(source)
        parsed.body=[node for node in parsed.body if not (isinstance(node,ast.Assign) and len(node.targets)==1 and isinstance(node.targets[0],ast.Name) and node.targets[0].id in projected)]
        return ast.dump(parsed,include_attributes=False)
    result=''.join(lines)
    if retained(text)!=retained(result): raise AssertionError('Fixture projection changed behavior')
    return result


class Tru03PreimportTests(unittest.TestCase):
    """Synthetic native Git transport; real bootstrap/metadata loader, no Git mutation."""
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);(self.root/'tools').mkdir()
        self.addCleanup(patch.stopall)
        patch.object(subject,'ROOT',self.root).start()
        self.base='3abf0f45ae637c5dedde5550b4bd19eb99f1ac5e'
        self.impl='1'*40;self.head='2'*40;self.itree='3'*40;self.htree='4'*40
        self.base_text=historical_tru03_metadata_fixture(Path(candidate.__file__).read_text(encoding='utf-8'))
        self.text=self.base_text.replace('4279bcd7ad50d5d3f284640602f3e1fc6f351a31',self.impl).replace('f2d8ee2ec9a721f963855421a2d5f61f6ba9f9e3',self.itree)
        self.text=self.text.replace('b71860d73c63bd0215590e9a916dbad3c131a9045da1de1bcb56f8a01e9ba315','a'*64)
        self.text=self.text.replace('ALPHA_MICROFILE_DISPOSITION_BINDING = {\n    "proofSha256": "'+'a'*64+'"','ALPHA_MICROFILE_DISPOSITION_BINDING = {\n    "proofSha256": "b71860d73c63bd0215590e9a916dbad3c131a9045da1de1bcb56f8a01e9ba315"')
        self.text+='\nALPHA_MICROFILE_TRU03_BINDING = {"proofSha256": "'+'a'*64+'"}\n'
        self.sentinel=self.root/'EXECUTION_MUST_NOT_HAPPEN'
        self.paths={'android/poc/recovery/src/androidTest/kotlin/com/monumentogram/dora/poc/recovery/candidate/RecoveryCampaignInstrumentedTest.kt','tools/recovery_campaign.py','tools/test_recovery_microfile_tru03.py','tools/recovery_alpha_prefix_repair.py','tools/test_recovery_alpha_prefix_repair.py'}
        self.metadata='tools/validate_recovery_0d6_candidate.py';self.metadata_test='tools/test_validate_recovery_0d6_candidate.py'
        self.before={p:self.entry(b'old') for p in self.paths if p!='tools/test_recovery_microfile_tru03.py'}
        self.before.update({self.metadata:self.entry(self.base_text.encode()),self.metadata_test:self.entry(b'old tests')})
        self.after=dict(self.before,**{p:self.entry(b'new') for p in self.paths})
        self.queries=[];self.overrides={};self.seal()
        patch.object(subject,'_tru03_git',side_effect=self.git,create=True).start()
    @staticmethod
    def entry(data):return dict(mode='100644',type='blob',object=hashlib.sha1(b'blob '+str(len(data)).encode()+b'\0'+data).hexdigest())
    def seal(self):
        (self.root/self.metadata).write_text(self.text,encoding='utf-8',newline='\n')
        self.current=dict(self.after,**{self.metadata:self.entry(self.text.encode()),self.metadata_test:self.entry(b'new tests')})
    def git(self,*args,root):
        self.assertEqual(Path(root),self.root);self.queries.append(args)
        if args in self.overrides:return self.overrides[args]
        fixed={('rev-parse','--show-toplevel'):str(self.root),('rev-parse','HEAD'):self.head,
            ('rev-parse','HEAD^{tree}'):self.htree,('status','--porcelain'):'',('branch','--show-current'):'',
            ('rev-parse',self.base+'^{tree}'):'e85965dd6058b242a70c87a8c48c34ecbf20312b',
            ('merge-base',self.base,self.head):self.base,
            ('show','-s','--format=%P',self.head):self.impl,
            ('show','-s','--format=%P',self.impl):self.base,
            ('rev-parse',self.impl+'^{tree}'):self.itree,
            ('show',self.impl+':'+self.metadata):self.base_text,
            ('show',self.base+':'+self.metadata):self.base_text}
        if args in fixed:return fixed[args]
        if len(args)==3 and args[:2]==('ls-tree','-rz'):
            entries={self.base:self.before,self.impl:self.after,self.head:self.current}[args[2]]
            return ''.join(e['mode']+' '+e['type']+' '+e['object']+'\t'+p+'\0' for p,e in sorted(entries.items()))
        if len(args)==4 and args[0]=='ls-tree' and args[2]=='--':
            entry=self.current[args[3]]
            return entry['mode']+' '+entry['type']+' '+entry['object']+'\t'+args[3]
        if len(args)==3 and args[0]=='hash-object' and args[1].startswith('--path='):
            return self.entry(Path(args[2]).read_bytes())['object']
        raise AssertionError('Unexpected native query '+repr(args))
    def test_metadata_execution_is_rejected_before_runpy(self):
        self.text+='\nPath('+repr(str(self.sentinel))+').write_text("executed")\n';self.seal()
        with self.assertRaisesRegex(ValueError,'TRU03_METADATA_BEHAVIOR'):
            subject.candidate_api()
        self.assertFalse(self.sentinel.exists())
    def test_missing_binding_native_descendant_rejected(self):
        self.text=self.text[:self.text.index('\nALPHA_MICROFILE_TRU03_BINDING')];self.seal()
        with self.assertRaisesRegex(ValueError,'TRU03_METADATA_BINDING_REQUIRED'):subject.bootstrap_current_metadata()
    def test_exact_native_child_bootstrap_without_loading_metadata(self):
        with patch.object(subject.runpy,'run_path',side_effect=AssertionError('bootstrap must not load metadata')):
            facts=subject.bootstrap_current_metadata()
        self.assertEqual(facts['route'],'TRU03');self.assertEqual(facts['implementation'],dict(commit=self.impl,tree=self.itree))
    def test_exact_path_and_mode_negatives(self):
        good=copy.deepcopy(self.after)
        for mutation in ('extra','missing','mode','deleted','existing_new'):
            with self.subTest(mutation=mutation):
                self.after=copy.deepcopy(good);before=copy.deepcopy(self.before)
                if mutation=='extra':self.after['unexpected.py']=self.entry(b'new')
                elif mutation=='missing':self.after['tools/recovery_campaign.py']=self.before['tools/recovery_campaign.py']
                elif mutation=='mode':self.after['tools/recovery_campaign.py']['mode']='100755'
                elif mutation=='deleted':self.after.pop('tools/recovery_campaign.py')
                else:self.before['tools/test_recovery_microfile_tru03.py']=self.entry(b'old')
                self.seal()
                with self.assertRaisesRegex(ValueError,'TRU03_IMPLEMENTATION_(DELTA|MODE|ADDED_PATH)'):subject.bootstrap_current_metadata()
                self.before=before
        self.after=good
    def test_literal_mutation_rejected_without_execution(self):
        good=self.text
        for statement in ('ALPHA_MICROFILE_TRU03_BINDING["proofSha256"] = "b"*64',
            'alias = ALPHA_MICROFILE_TRU03_BINDING', 'del ALPHA_MICROFILE_TRU03_BINDING',
            'ALPHA_MICROFILE_TRU03_BINDING = {"proofSha256": "a"*64}'):
            with self.subTest(statement=statement):
                self.text=good+'\n'+statement+'\n';self.seal()
                with self.assertRaisesRegex(ValueError,'TRU03_METADATA_LITERAL'):subject.bootstrap_current_metadata()

    def campaign_fixture(self):
        """Real campaign/prefix modules; only native Git transport is synthetic."""
        import runpy
        import subprocess
        original_run=runpy.run_path
        for name in ('recovery_campaign.py','recovery_alpha_prefix_repair.py','recovery_instrumentation_status.py'):
            target=self.root/'tools'/name
            target.write_bytes(Path(subject.__file__).with_name(name).read_bytes())
            if 'tools/'+name in self.after:self.after['tools/'+name]=self.entry(target.read_bytes())
        self.seal()
        campaign=SimpleNamespace(**original_run(str(self.root/'tools/recovery_campaign.py')))
        self.loads=[]
        def native(argv,**kwargs):
            self.assertEqual(argv[:3],['git','-c','safe.directory='+self.root.as_posix()])
            environment=kwargs.pop('env');self.assertEqual(environment.get('GIT_OPTIONAL_LOCKS'),'0')
            self.assertEqual(kwargs,dict(cwd=self.root,check=True,text=True,capture_output=True))
            return SimpleNamespace(stdout=self.git(*argv[3:],root=self.root))
        def tracked(path,*args,**kwargs):
            self.loads.append(Path(path).name)
            return original_run(path,*args,**kwargs)
        patch.object(subprocess,'run',side_effect=native).start()
        patch.object(runpy,'run_path',side_effect=tracked).start()
        return campaign

    def assert_no_metadata_loaded(self):
        self.assertIn('recovery_alpha_prefix_repair.py',self.loads)
        self.assertNotIn('validate_recovery_0d6_candidate.py',self.loads)
        self.assertNotIn('recovery_alpha_repair.py',self.loads)
        self.assertFalse(self.sentinel.exists())

    def test_real_reduced_entry_rejects_executable_metadata_before_legacy_import(self):
        campaign=self.campaign_fixture()
        self.text+='\nPath('+repr(str(self.sentinel))+').write_text("executed")\n';self.seal()
        with self.assertRaisesRegex(ValueError,'TRU03_METADATA_BEHAVIOR'):
            campaign.validate_alpha_repair({},dict(alphaReduced={}))
        self.assert_no_metadata_loaded()

    def test_real_preflight_entry_rejects_deleted_binding_before_import(self):
        campaign=self.campaign_fixture()
        self.text=self.text[:self.text.index('\nALPHA_MICROFILE_TRU03_BINDING')];self.seal()
        with self.assertRaisesRegex(ValueError,'TRU03_METADATA_BINDING_REQUIRED'):
            campaign.validate_alpha_prefix_preflight({},dict(alphaPreflight={}))
        self.assert_no_metadata_loaded()

    def test_real_generic_entry_requires_proof_without_loading_valid_child(self):
        campaign=self.campaign_fixture()
        with self.assertRaisesRegex(ValueError,'TRU03_SOURCE_REPAIR_REQUIRED'):
            campaign.accepted_alpha_source()
        self.assertEqual(self.loads,[])
        self.assertFalse(self.sentinel.exists())

    def test_unchanged_scope_rejects_repair_markers_without_import_or_git(self):
        campaign=self.campaign_fixture()
        metadata=self.root/self.metadata
        for name in ('ALPHA_REDUCED_REPAIR_BINDING','ALPHA_PREFIX_REPAIR_BINDING','ALPHA_MICROFILE_TRU03_BINDING'):
            metadata.write_text(name+' = unexpected_call()\n',encoding='utf-8')
            with self.subTest(name=name), patch.object(campaign.accepted_alpha_source.__globals__['subprocess'],'run',side_effect=AssertionError('no native call for denied scope')):
                with self.assertRaisesRegex(ValueError,'restricted to reduced'):
                    campaign.accepted_alpha_source()
            self.assertEqual(self.loads,[])

    def test_execution_gate_omitted_source_repair_rejects_marker_before_code_loading(self):
        campaign=self.campaign_fixture()
        source=dict(commit=self.head,tree=self.htree,appApkSha256='a'*64,testApkSha256='b'*64)
        plan=dict(source=source)
        gate=dict(schema='DORA_RECOVERY_CAMPAIGN_EXECUTION_GATE_V1',manifestSha256=campaign.digest_json(plan),
            source=source,driverSha256=subject.file_sha(self.root/'tools/recovery_campaign.py'),
            instrumentationParserSha256=subject.file_sha(self.root/'tools/recovery_instrumentation_status.py'),
            alphaReduced=dict(decisionId=campaign.ALPHA_REDUCED_DECISION_ID,scope=campaign.ALPHA_REDUCED_SCOPE,source=source))
        self.text+='\nPath('+repr(str(self.sentinel))+').write_text("executed")\n';self.seal()
        with self.assertRaisesRegex(ValueError,'TRU03_SOURCE_REPAIR_REQUIRED'):
            campaign.validate_execution_gate(plan,gate)
        self.assertEqual(self.loads,[])
        self.assertFalse(self.sentinel.exists())

    def test_preflight_omitted_source_repair_and_deleted_binding_still_rejects_before_import(self):
        campaign=self.campaign_fixture()
        source=dict(commit=self.head,tree=self.htree)
        import ast
        tree=ast.parse(self.text)
        markers={'ALPHA_REDUCED_REPAIR_BINDING','ALPHA_PREFIX_REPAIR_BINDING','ALPHA_MICROFILE_TRU03_BINDING'}
        tree.body=[node for node in tree.body if not (isinstance(node,ast.Assign)
            and any(isinstance(target,ast.Name) and target.id in markers for target in node.targets))]
        self.text=ast.unparse(tree);self.seal()
        gate=dict(alphaPreflight=dict(decisionId='DORA_0D6_ALPHA_PREFLIGHT_20260914',
            scope='INTERNAL_ALPHA_E36_PREFLIGHT_ONLY',source=source))
        with self.assertRaisesRegex(ValueError,'TRU03_METADATA_BINDING_REQUIRED'):
            campaign.validate_alpha_preflight(dict(source=source),gate,'SUPPLEMENTAL_SQLITE')
        self.assert_no_metadata_loaded()

    def test_reduced_wrapper_valid_bootstrap_delegates_to_real_prefix_without_legacy(self):
        campaign=self.campaign_fixture()
        # An intentionally invalid reduced scope isolates the real next callee;
        # it is not a complete gate fixture or a successful source proof.
        with self.assertRaisesRegex(ValueError,'Prefix applicability is restricted to reduced E36 campaign scope'):
            campaign.validate_alpha_repair({},dict(alphaReduced={}))
        self.assert_no_metadata_loaded()

    def test_independent_build_policy_is_mandatory_and_cannot_be_supplied_by_document(self):
        with self.assertRaisesRegex(ValueError,'TRU03_FIXED_SOURCE_ROOT'):
            subject._tru03_build_context({'trusted':True},{},{},{},{})

    def test_administrative_facts_at_implementation_needs_no_metadata_child(self):
        # Component validators are isolated here; this proves order/API only,
        # never admission or a successful historical/build proof validation.
        self.overrides[('rev-parse','HEAD')]=self.impl
        self.overrides[('rev-parse','HEAD^{tree}')]=self.itree
        names={node.targets[0].id for node in subject.ast.parse(self.base_text).body
            if isinstance(node,subject.ast.Assign) and len(node.targets)==1
            and isinstance(node.targets[0],subject.ast.Name)
            and node.targets[0].id.startswith('ALPHA_') and node.targets[0].id.endswith('_BINDING')}
        _,bindings=subject._tru03_metadata_body(self.base_text,names)
        apks={}
        for key in ('appApkSha256','testApkSha256'):
            path=self.root/key;path.write_bytes(key.encode())
            apks[key]=dict(path=str(path),sha256=subject.file_sha(path))
        path=self.root/'build.json';path.write_text('{}')
        build=dict(path=str(path),sha256=subject.file_sha(path))
        with patch.object(subject,'validate_tru03_predecessor',return_value=dict(inheritedBindings=bindings)), \
             patch.object(subject,'validate_tru03_authorization'), \
             patch.object(subject,'_tru03_build_context',side_effect=ValueError('COMPONENT_CONTEXT_REACHED')), \
             patch.object(subject,'bootstrap_current_metadata',side_effect=AssertionError('M does not exist')):
            with self.assertRaisesRegex(ValueError,'COMPONENT_CONTEXT_REACHED'):
                subject.microfile_tru03_facts({},SimpleNamespace(implementation_commit=self.impl,implementation_tree=self.itree),
                    {k:v['sha256'] for k,v in apks.items()},{},build,apks,{}, {})


class Tru03DispatchTests(unittest.TestCase):
    """Actual dispatch functions, isolated component-validation stop sentinel."""
    class StopAtNewProof(Exception):pass
    def setUp(self):
        self.fixture=MicrofileDispositionSuccessorTests();self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups);f=self.fixture
        proof=dict(schema=subject.TRU03_SCHEMA)
        descriptor=f.write('tru03-route.json',proof)
        f.api[subject.TRU03_BINDING_KEY]=dict(proofSha256=descriptor['sha256'])
        f.api['ALPHA_PREFIX_REPAIR_BINDING']['applicabilitySha256']=descriptor['sha256']
        f.gate['alphaPreflight']['sourceRepair']=descriptor
        for name in ('validate_microfile_disposition_proof','validate_git_query_proof','validate_collector_query_proof','validate_stream_path_proof'):
            p=patch.object(subject,name,side_effect=AssertionError('Historical fallback called'))
            p.start();self.addCleanup(p.stop)
        p=patch.object(subject,'validate_microfile_tru03_proof',side_effect=self.StopAtNewProof('exact new route'))
        self.target=p.start();self.addCleanup(p.stop)

    def test_source_dispatch_selects_new_route_before_inherited_old_binding(self):
        f=self.fixture
        with self.assertRaisesRegex(self.StopAtNewProof,'exact new route'):
            subject.validate_source(f.plan,f.gate,'alphaPreflight')
        self.target.assert_called_once()

    def test_origin_dispatch_selects_new_route_before_inherited_old_binding(self):
        f=self.fixture;gate=f.write('tru03-origin-gate.json',f.gate)
        attempt=dict(ownerSessionId='synthetic-owner',pin=dict(path='synthetic-pin',sha256='1'*64))
        pin=dict(schema='DORA_0D6_PRIVATE_LAUNCH_PIN_V1',ownerSessionId=attempt['ownerSessionId'],
            sourceRoot=str(subject.ROOT.resolve()),gatePath=gate['path'],gateFileSha256=gate['sha256'])
        launcher=dict(argv=['powershell','-NoLogo','-NoProfile','-NonInteractive','-ExecutionPolicy','Bypass','-File',
            'launcher','-PinPath',attempt['pin']['path'],'-ApprovedPinSha256',attempt['pin']['sha256'],'-Execute'])
        with self.assertRaisesRegex(self.StopAtNewProof,'exact new route'):
            subject.validate_preflight_origin(attempt,pin,{},launcher,f.source)
        self.target.assert_called_once()

    def test_new_binding_cannot_downgrade_to_old_proof_schema(self):
        f=self.fixture
        descriptor=f.write('tru03-wrong-route.json',dict(schema='DORA_RECOVERY_MICROFILE_DISPOSITION_APPLICABILITY_V1'))
        f.gate['alphaPreflight']['sourceRepair']=descriptor
        f.api[subject.TRU03_BINDING_KEY]['proofSha256']=descriptor['sha256']
        f.api['ALPHA_PREFIX_REPAIR_BINDING']['applicabilitySha256']=descriptor['sha256']
        with self.assertRaisesRegex(ValueError,'TRU03_PROOF_SCHEMA'):
            subject.validate_source(f.plan,f.gate,'alphaPreflight')
        self.target.assert_not_called()


class Tru03ExactProofTests(unittest.TestCase):
    """Isolate final proof equality after component checks; never an admission."""
    def test_integer_zero_cannot_substitute_for_false_runtime_credit_flag(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'proof.json'
            expected=dict(schema=subject.TRU03_SCHEMA,independentReview={},runtimeAdmissionGranted=False)
            actual=dict(expected,runtimeAdmissionGranted=0)
            path.write_text(json.dumps(actual),encoding='utf-8')
            descriptor=dict(path=str(path),sha256=subject.file_sha(path))
            api={subject.TRU03_BINDING_KEY:dict(proofSha256=descriptor['sha256'])}
            binding=dict(applicabilitySha256=descriptor['sha256'])
            with patch.object(subject,'microfile_tru03_facts',return_value=(expected,{},{})), \
                 patch.object(subject,'validate_tru03_metadata_shape'):
                with self.assertRaisesRegex(ValueError,'TRU03_EXACT_PROOF'):
                    subject.validate_microfile_tru03_proof(api,None,binding,actual,descriptor,{})

    def test_exact_typed_proof_returns_only_verified_component_results(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'proof.json'
            expected=dict(schema=subject.TRU03_SCHEMA,independentReview={},runtimeAdmissionGranted=False)
            path.write_text(json.dumps(expected),encoding='utf-8')
            descriptor=dict(path=str(path),sha256=subject.file_sha(path))
            api={subject.TRU03_BINDING_KEY:dict(proofSha256=descriptor['sha256'])}
            binding=dict(applicabilitySha256=descriptor['sha256'])
            with patch.object(subject,'microfile_tru03_facts',return_value=(expected,{'frozen':'verified'},{'control':'verified'})) as facts, \
                 patch.object(subject,'validate_tru03_metadata_shape') as metadata:
                self.assertEqual(subject.validate_microfile_tru03_proof(api,None,binding,expected,descriptor,{}),
                    ({'frozen':'verified'},{'control':'verified'}))
                facts.assert_called_once();metadata.assert_called_once()

    def test_duplicate_or_nonfinite_new_json_rejected_after_correct_byte_binding(self):
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'proof.json'
            for raw,label in (('{"key":1,"key":2}','TRU03_DUPLICATE_JSON_KEY'),
                              ('{"key":NaN}','TRU03_NONFINITE_JSON'),
                              ('{"key":Infinity}','TRU03_NONFINITE_JSON')):
                with self.subTest(raw=raw):
                    path.write_text(raw,encoding='utf-8')
                    with self.assertRaisesRegex(ValueError,label):
                        subject._tru03_json(dict(path=str(path),sha256=subject.file_sha(path)))


class Tru03HistoricalLoaderTests(unittest.TestCase):
    """Bound files and native transport; loader failures before any runpy call.

    Synthetic proof/build and three inert Python bytes have fixture-only policy
    hash substitutions. No old proof/loader is executed or claimed verified.
    """
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name)/'old-source';(self.root/'tools').mkdir(parents=True)
        self.entries={}
        pins={}
        for relative in subject.TRU03_VALIDATION_FILES:
            path=self.root/relative;path.write_bytes(('# synthetic inert loader '+relative+'\n').encode())
            pins[relative]=subject.file_sha(path)
            self.entries[relative]=Tru03PreimportTests.entry(path.read_bytes())
        p=patch.object(subject,'TRU03_VALIDATION_FILES',pins);p.start();self.addCleanup(p.stop)
        self.build=self.write('build.json',dict(sourceRoot=str(self.root)))
        self.proof=self.write('proof.json',dict(build=self.build,independentReview=dict(path='not-loaded',sha256=subject.TRU03_OLD_REVIEW_SHA)))
        self.predecessor=dict(sourceRoot=str(self.root),finalSource=subject.TRU03_OLD_SOURCE,
            implementation=subject.TRU03_OLD_IMPLEMENTATION,applicability=self.proof,
            validationFiles={p:dict(path=str(self.root/p),sha256=s) for p,s in subject.TRU03_VALIDATION_FILES.items()})
        self.overrides={}
        for name,value in (('TRU03_OLD_BUILD_SHA',self.build['sha256']),('TRU03_OLD_PROOF_SHA',self.proof['sha256'])):
            p=patch.object(subject,name,value);p.start();self.addCleanup(p.stop)
        p=patch.object(subject,'_tru03_git',side_effect=self.git);p.start();self.addCleanup(p.stop)
        p=patch.object(subject.runpy,'run_path',side_effect=AssertionError('LOAD_REACHED_AFTER_CHECKS'))
        self.loader=p.start();self.addCleanup(p.stop)
    def write(self,name,value):
        path=Path(self.temp.name)/name;path.write_text(json.dumps(value),encoding='utf-8')
        return dict(path=str(path),sha256=subject.file_sha(path))
    def git(self,*args,root):
        self.assertEqual(root,self.root)
        if args in self.overrides:return self.overrides[args]
        fixed={('rev-parse','--show-toplevel'):str(self.root),('status','--porcelain'):'',
            ('rev-parse','HEAD'):subject.TRU03_BASELINE_COMMIT,('rev-parse','HEAD^{tree}'):subject.TRU03_BASELINE_TREE}
        if args in fixed:return fixed[args]
        if args==('ls-tree','-rz',subject.TRU03_BASELINE_COMMIT):
            return ''.join(e['mode']+' '+e['type']+' '+e['object']+'\t'+p+'\0' for p,e in sorted(self.entries.items()))
        if args[0]=='hash-object':return Tru03PreimportTests.entry(Path(args[2]).read_bytes())['object']
        raise AssertionError(args)
    def test_all_three_bound_fixture_files_verified_before_natural_loader(self):
        with self.assertRaisesRegex(AssertionError,'LOAD_REACHED_AFTER_CHECKS'):
            subject.load_tru03_predecessor_context(self.predecessor)
        self.loader.assert_called_once_with(str(self.root/'tools/recovery_alpha_prefix_repair.py'))
    def test_wrong_native_identity_rejected_before_loader(self):
        self.overrides[('rev-parse','HEAD')]='f'*40
        with self.assertRaisesRegex(ValueError,'TRU03_PREDECESSOR_SOURCE'):
            subject.load_tru03_predecessor_context(self.predecessor)
        self.loader.assert_not_called()
    def test_same_current_root_and_extra_loader_rejected_before_loader(self):
        with patch.object(subject,'ROOT',self.root),self.assertRaisesRegex(ValueError,'TRU03_PREDECESSOR_ROOT'):
            subject.load_tru03_predecessor_context(self.predecessor)
        self.predecessor['validationFiles']['unexpected.py']={}
        with self.assertRaisesRegex(ValueError,'TRU03_PREDECESSOR_LOADER_SET'):
            subject.load_tru03_predecessor_context(self.predecessor)
        self.loader.assert_not_called()
    def test_any_of_three_loader_bytes_or_native_blobs_reject_before_execution(self):
        for relative in subject.TRU03_VALIDATION_FILES:
            with self.subTest(path=relative,mutation='bytes'):
                path=self.root/relative;raw=path.read_bytes();path.write_bytes(raw+b'\n# tamper')
                with self.assertRaises(ValueError):subject.load_tru03_predecessor_context(self.predecessor)
                self.loader.assert_not_called();path.write_bytes(raw)
            with self.subTest(path=relative,mutation='blob'):
                original=self.entries[relative]['object'];self.entries[relative]['object']='f'*40
                with self.assertRaisesRegex(ValueError,'TRU03_SOURCE_BLOB'):
                    subject.load_tru03_predecessor_context(self.predecessor)
                self.loader.assert_not_called();self.entries[relative]['object']=original


if __name__=='__main__':unittest.main()


class Tru03PinnedContextTests(unittest.TestCase):
    """Synthetic files, only native Windows/Java leaf transports substituted."""
    def setUp(self):
        import os
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        base=Path(self.temp.name);self.root=base/'source';self.root.mkdir()
        self.policy=copy.deepcopy(subject.TRU03_FIXED_MACHINE_POLICY)
        p=self.policy;self.wrapper=p['wrapper'];home=base/'home';home.mkdir()
        self.cache=home/'.gradle/wrapper/dists/gradle-fixed/cache';self.cache.mkdir(parents=True)
        self.distribution=self.cache/'gradle-fixed';(self.distribution/'lib').mkdir(parents=True)
        (self.distribution/'init.d').mkdir()
        def write(path,data=b'fixed synthetic policy bytes'):
            path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data);return subject.tru03_desc(path)
        self.write=write
        for key in ('python','cmd','java','recorder','runner'):p['tools'][key]=write(base/'tools'/key)
        p['tools']['javaHome']=str(base/'java');p['tools']['androidSdkRoot']=str(base/'sdk')
        p['generator']=write(base/'generator.py');p['jdkRelease']=write(base/'release')
        # Actual reviewed template bytes and actual renderer; no renderer mock.
        template=Path(subject.TRU03_FIXED_MACHINE_POLICY['template']['path']).read_bytes()
        p['template']=write(base/'template.gradle',template)
        p['planned']=dict(sourceRoot=str(self.root),snapshotRoot=str(base/'snapshot'),rawRoot=str(base/'raw'),phase='verify-01')
        p['renderedInitSha256']=hashlib.sha256(subject._tru03_render_init(template,self.root,base/'raw','verify-01')).hexdigest()
        p['identity']=dict(Identity='FIXTURE\\owner',UserProfile=str(home),Home=None)
        p['java']=dict(userHome=str(home),javaHome=p['tools']['javaHome'],version='fixture')
        p['exactThreeOverrides']=dict(ANDROID_HOME=p['tools']['androidSdkRoot'],JAVA_HOME=p['tools']['javaHome'],PYTHONUTF8='1')
        p['environment'].update(sourceRoot=str(self.root),javaHome=p['tools']['javaHome'],androidHome=p['tools']['androidSdkRoot'],pythonPath=p['tools']['python']['path'],gradleUserHome=str(home/'.gradle'))
        for relative in self.wrapper['files']:
            self.wrapper['files'][relative]=write(self.root/'android'/relative)
        self.wrapper['projectProperties']=write(self.root/'android/gradle.properties')
        self.wrapper['launcher']=write(self.distribution/'lib/gradle-launcher-fixture.jar')
        self.wrapper['okMarker']=write(self.cache/'gradle-fixed.zip.ok',b'')
        self.wrapper.update(selectedCacheRoot=str(self.cache),selectedDistribution=str(self.distribution),selectedUserHome=str(home/'.gradle'))
        self.wrapper['cacheInventory']=subject._tru03_policy_inventory(self.cache)
        write(self.distribution/'init.d/readme.txt')
        p['userConfiguration']['initializerDirectories']=[subject._tru03_policy_inventory(home/'.gradle/init.d'),subject._tru03_policy_inventory(self.distribution/'init.d')]
        self.java={'user.home':str(home),'java.home':p['tools']['javaHome'],'java.version':'fixture'}
        for name,value in [('ROOT',self.root),('TRU03_FIXED_MACHINE_POLICY',p)]:
            patcher=patch.object(subject,name,value);patcher.start();self.addCleanup(patcher.stop)
        env=patch.dict(os.environ,{'USERPROFILE':str(home)},clear=True);env.start();self.addCleanup(env.stop)
        identity=patch.object(subject,'_tru03_normal_identity',return_value=p['identity']['Identity']);self.identity=identity.start();self.addCleanup(identity.stop)
        java=patch.object(subject,'_tru03_java_properties',return_value=self.java);self.probe=java.start();self.addCleanup(java.stop)

    def test_policy_is_computed_without_build_document(self):
        value=subject._tru03_fixed_build_policy()
        self.assertEqual(value['tools'],self.policy['tools'])
        self.assertEqual(value['generator'],self.policy['generator'])
        self.assertEqual(value['wrapperDistribution'],str(self.distribution))
        env=self.probe.call_args.args[2]
        self.assertEqual(set(env),{'USERPROFILE','ANDROID_HOME','JAVA_HOME','PYTHONUTF8'})
        self.assertEqual(hashlib.sha256(value['initBytes']).hexdigest(),self.policy['renderedInitSha256'])

    def test_java_and_native_identity_are_independent_of_environment_claims(self):
        self.identity.return_value='FIXTURE\\other'
        with self.assertRaisesRegex(ValueError,'TRU03_NORMAL_WINDOWS_IDENTITY'):subject._tru03_fixed_build_policy()
        self.identity.return_value=self.policy['identity']['Identity'];self.java['user.home']='C:\\'
        with self.assertRaisesRegex(ValueError,'TRU03_JAVA_POLICY_PROPERTIES'):subject._tru03_fixed_build_policy()

    def test_environment_injection_cannot_be_resealed_as_policy(self):
        import os
        for key in ('HOME','GRADLE_USER_HOME','GRADLE_HOME','PYTHONPATH','PYTHONHOME','JAVA_OPTS','GRADLE_OPTS','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS'):
            with self.subTest(key=key),patch.dict(os.environ,{key:'unreviewed'}),self.assertRaisesRegex(ValueError,'TRU03_UNREVIEWED_ENVIRONMENT:'+key):
                subject._tru03_fixed_build_policy()

    def test_tool_bytes_wrapper_bytes_and_local_config_are_not_document_authority(self):
        for path in (Path(self.policy['tools']['runner']['path']),self.root/'android/gradle/wrapper/gradle-wrapper.properties'):
            with self.subTest(path=path):
                old=path.read_bytes();path.write_bytes(b'changed')
                with self.assertRaises(ValueError):subject._tru03_fixed_build_policy()
                path.write_bytes(old)
        self.write(self.root/'android/local.properties')
        with self.assertRaisesRegex(ValueError,'TRU03_UNREVIEWED_LOCAL_PROPERTIES'):subject._tru03_fixed_build_policy()

    def test_wrapper_ambiguity_and_initializer_additions_fail_exact_guards(self):
        extra=self.cache/'other';extra.mkdir()
        with self.assertRaisesRegex(ValueError,'TRU03_WRAPPER_CACHE_SELECTION'):subject._tru03_fixed_build_policy()
        extra.rmdir();extra=self.distribution/'lib/gradle-launcher-extra.jar';extra.write_bytes(b'extra')
        with self.assertRaisesRegex(ValueError,'TRU03_WRAPPER_SINGLE_LAUNCHER'):subject._tru03_fixed_build_policy()
        extra.unlink();extra=self.distribution/'init.d/inject.gradle';extra.write_bytes(b'println 1')
        with self.assertRaisesRegex(ValueError,'TRU03_INITIALIZER_INVENTORY'):subject._tru03_fixed_build_policy()


class Tru03ContextBindingTests(unittest.TestCase):
    """Constructor integration with synthetic native tree transport and files."""
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        base=Path(self.temp.name);self.root=base/'source';self.root.mkdir();self.snapshot=base/'snapshot';self.snapshot.mkdir()
        self.manifest=self.snapshot/'MANIFEST.json';self.manifest.write_text('{"phase":"verify-01"}')
        self.descriptor=dict(path=str(self.manifest),sha256=subject.file_sha(self.manifest))
        self.identity=dict(commit='a'*40,tree='b'*40);self.before={};self.after={}
        self.policy=dict(sourceRoot=str(self.root),snapshotRoot=str(self.snapshot),rawRoot=str(base/'raw'),phase='verify-01',tools={},environment={},generator={},initBytes=b'fixed',wrapperDistribution=str(base/'wrapper'))
        for name,value in [('ROOT',self.root),('_tru03_fixed_build_policy',lambda:self.policy),('_tru03_source_identity',lambda root:dict(self.identity)),('_tru03_implementation',lambda impl,root:(self.before,self.after,[]))]:
            p=patch.object(subject,name,value);p.start();self.addCleanup(p.stop)
    def context(self):return subject._tru03_build_context(self.descriptor,dict(commit='a'*40,tree='b'*40),self.before,self.after,{})

    def test_clean_implementation_context_never_imports_metadata(self):
        with patch.object(subject,'candidate_api',side_effect=AssertionError('METADATA_EXECUTED')),patch.object(subject,'bootstrap_current_metadata',side_effect=AssertionError('M_REQUIRED')):
            context=self.context();context.native_check();self.assertEqual(context.metadata_current,{})

    def test_manifest_location_phase_and_hash_are_fixed(self):
        self.descriptor['path']=str(self.snapshot/'elsewhere.json')
        with self.assertRaisesRegex(ValueError,'TRU03_FIXED_BUILD_DESCRIPTOR'):self.context()
        self.descriptor['path']=str(self.manifest);self.manifest.write_text('{"phase":"verify-02"}')
        self.descriptor['sha256']=subject.file_sha(self.manifest)
        with self.assertRaisesRegex(ValueError,'TRU03_FIXED_BUILD_PHASE'):self.context()

    def test_post_context_identity_and_document_drift_rejected(self):
        context=self.context();self.identity['commit']='c'*40
        with self.assertRaisesRegex(ValueError,'TRU03_BUILD_SOURCE_CHANGED'):context.native_check()
        self.identity['commit']='a'*40;self.manifest.write_text('{"phase":"verify-01","tamper":true}')
        with self.assertRaises(ValueError):context.native_check()

    def test_native_copy_path_and_blob_transport_are_exact(self):
        relative='tools/recovery_campaign.py';self.after[relative]=dict(mode='100644',type='blob',object='c'*40)
        context=self.context();path=self.snapshot/'source'/relative;path.parent.mkdir(parents=True);path.write_bytes(b'copied')
        with patch.object(subject,'_tru03_git',return_value='c'*40) as git:
            self.assertEqual(context.blob_for_copy(relative,path),'c'*40)
            git.assert_called_once_with('hash-object','--path='+relative,str(path),root=self.root)
        with self.assertRaisesRegex(ValueError,'TRU03_COPY_FIXED_PATH'):context.blob_for_copy(relative,self.snapshot/'elsewhere.py')
        with self.assertRaisesRegex(ValueError,'TRU03_COPY_NATIVE_PATH'):context.blob_for_copy('../elsewhere',path)

    def test_metadata_child_requires_bootstrap_and_binds_both_current_files(self):
        self.identity=dict(commit='d'*40,tree='e'*40)
        for relative in subject.TRU03_METADATA_PATHS:
            path=self.root/relative;path.parent.mkdir(parents=True,exist_ok=True);path.write_text('# current metadata')
        result=dict(route='TRU03',source=self.identity,implementation=dict(commit='a'*40,tree='b'*40))
        with patch.object(subject,'bootstrap_current_metadata',return_value=result) as bootstrap:
            context=self.context();self.assertEqual(set(context.metadata_current),subject.TRU03_METADATA_PATHS)
            self.assertGreaterEqual(bootstrap.call_count,2)
            next(iter(context.metadata_current.values()))['sha256']='0'*64
            with self.assertRaisesRegex(ValueError,'TRU03_CONTEXT_METADATA_CHANGED'):context.native_check()


class Tru03InstalledMetadataFixtureIsolationTests(unittest.TestCase):
    def test_historical_context_does_not_inherit_installed_tru03_binding(self):
        installed = dict(proofSha256='f'*64)
        with patch.object(candidate, 'ALPHA_MICROFILE_TRU03_BINDING', installed, create=True):
            fixture = CombinedAdmissionTests(); fixture.setUp()
            self.addCleanup(fixture.doCleanups)
            self.assertNotIn('ALPHA_MICROFILE_TRU03_BINDING', fixture.api)
            self.assertEqual(candidate.ALPHA_MICROFILE_TRU03_BINDING, installed)
            fixture.check()

    def test_native_fixture_stays_unbound_at_i_with_installed_tru03_metadata(self):
        import ast
        text = Path(candidate.__file__).read_text(encoding='utf-8')
        lines = text.splitlines(keepends=True)
        for node in reversed(ast.parse(text).body):
            if isinstance(node, ast.Assign) and len(node.targets)==1 and isinstance(node.targets[0], ast.Name):
                name=node.targets[0].id
                if name == 'ALPHA_MICROFILE_TRU03_BINDING':
                    lines[node.lineno-1:node.end_lineno]=[]
                elif name in ('IMPLEMENTATION_COMMIT', 'IMPLEMENTATION_TREE'):
                    lines[node.lineno-1:node.end_lineno]=[name+' = '+repr('e'*40)+'\n']
        installed=''.join(lines)+'\nALPHA_MICROFILE_TRU03_BINDING = {"proofSha256": "'+'f'*64+'"}\n'
        with tempfile.TemporaryDirectory() as directory:
            path=Path(directory)/'installed.py';path.write_text(installed,encoding='utf-8')
            with patch.object(candidate,'__file__',str(path)):
                fixture=Tru03PreimportTests();fixture.setUp()
                self.addCleanup(fixture.doCleanups)
                self.assertIsNone(subject.microfile_tru03_metadata_literal(fixture.base_text))
                fixture.test_exact_native_child_bootstrap_without_loading_metadata()
                fixture.test_metadata_execution_is_rejected_before_runpy()
            self.assertEqual(path.read_text(encoding='utf-8'),installed)
