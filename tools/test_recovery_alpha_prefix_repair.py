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
        self.assertEqual(23,len(result['sourceDelta']))
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
        patch.object(subject,'candidate_api',return_value=self.api).start()
        self.legacy=subject.legacy_api()
        self.frozen=dict(executionId='E36RED01',variantCount=165,entries=[dict(slot=1,mutationVariants=['DEFAULT'],attemptId='old',runId='old',executionEntrySha256='old')])
        self.frozen_descriptor=self.write('frozen.json',self.frozen)
        self.legacy['FROZEN_SELECTION_SHA256']=self.frozen_descriptor['sha256']
        patch.object(subject,'legacy_api',return_value=self.legacy).start()
        self.review=self.write('review.md','Exact combined source review')
        self.proof=subject.applicability_facts(self.api,self.profile,self.binding)
        self.proof.update(frozenSelection=self.frozen_descriptor,independentReview=self.review)
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
        self.temp=tempfile.TemporaryDirectory();self.addCleanup(self.temp.cleanup)
        self.root=Path(self.temp.name);self.repo=self.root/'repo';(self.repo/'tools').mkdir(parents=True)
        self.addCleanup(patch.stopall)
        patch.object(subject,'ROOT',self.repo).start()
        self.files={}
        for name in ('powershell.exe','adb.exe','python.exe','launcher.ps1','app.apk','test.apk'):
            p=self.root/name;p.write_bytes(('exact fixture '+name).encode());self.files[name]=p
        (self.repo/'tools/recovery_campaign.py').write_text('fixed driver fixture')
        for constant,name in (('POWERSHELL_SHA256','powershell.exe'),('ADB_SHA256','adb.exe'),
                              ('PYTHON_SHA256','python.exe'),('LAUNCHER_SHA256','launcher.ps1')):
            patch.object(subject,constant,subject.file_sha(self.files[name])).start()
        self.source=dict(commit='a'*40,tree='b'*40,appApkSha256=subject.file_sha(self.files['app.apk']),testApkSha256=subject.file_sha(self.files['test.apk']))
        self.plan=dict(source=self.source)
        self.gate=dict(source=self.source,alphaPreflight=dict(source=self.source),scopeProof='exact')
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
        plan=dict(source=source);gate=gate_fixture.gate
        gate.update(source=source,manifestSha256=fixtures.campaign.digest_json(plan))
        gate['alphaPreflight']['source']=source
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
             patch.object(fixtures.campaign,'accepted_alpha_source',return_value=source), \
             patch.object(subject,'POWERSHELL_SHA256',subject.file_sha(files['powershell.exe'])), \
             patch.object(subject,'ADB_SHA256',subject.file_sha(files['adb.exe'])), \
             patch.object(subject,'PYTHON_SHA256',subject.file_sha(files['python.exe'])), \
             patch.object(subject,'LAUNCHER_SHA256',subject.file_sha(files['launcher.ps1'])):
            transcript.check();self.assertEqual(3,origin.call_count)
            gate['exactHeadCiPassed']=False
            gate_d=transcript.write('complete-gate.json',gate)
            attempt=transcript.proof['attempts'][0]
            pin=json.loads(Path(attempt['pin']['path']).read_bytes());pin['gateFileSha256']=gate_d['sha256']
            attempt['pin']=transcript.write(Path(attempt['pin']['path']).name,pin)
            launcher=json.loads(Path(attempt['launcherReceipt']['path']).read_bytes());launcher['argv'][11]=attempt['pin']['sha256']
            attempt['launcherReceipt']=transcript.write(Path(attempt['launcherReceipt']['path']).name,launcher)
            with self.assertRaisesRegex(ValueError,'exactHeadCiPassed'):transcript.check()


if __name__=='__main__':unittest.main()
