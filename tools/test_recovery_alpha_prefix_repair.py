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

if __name__=='__main__':unittest.main()
