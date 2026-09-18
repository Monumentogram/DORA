"""Physical admission negative controls; synthetic identity, no ADB."""
import copy
import hashlib
import json
from pathlib import Path
import subprocess
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch
import recovery_physical as physical


def packet():
    return dict(schema='DORA_PHYSICAL_NINE_V1', executionId='P35-TEST',
        appSourceCommit=physical.APP_COMMIT, harnessSourceCommit='1' * 40,
        source=dict(commit='1' * 40, tree='2' * 40, appApkSha256=physical.APP_SHA, testApkSha256='3' * 64),
        environment=dict(profile='POCO-M5-PHYSICAL', api=34, abi='arm64-v8a',
            fingerprint='synthetic/stone:14/test/1:user/release-keys', product='stone_p_ru',
            serial='SYNTHETIC000001', adbPort=5037, model='22071219CG', manufacturer='Xiaomi',
            buildType='user', release='14', device='stone', selinux='Enforcing'),
        payloads=['P35-01','P35-02','P35-03','P35-04','P35-05','P35-06','P35-07','P35-08','P35-09'])


class PhysicalTests(unittest.TestCase):
    def test_exact_identity_and_nine_rows(self):
        physical.validate_contract(packet())
        plan = physical.build_plan(Path(__file__).resolve().parents[1], packet())
        self.assertEqual(6, len(plan['entries']))
        self.assertEqual(['NORMAL','NORMAL','HARD_KILL','HARD_KILL','HARD_KILL','HARD_KILL'],
                         [e['kind'] for e in plan['entries']])
        stream, micro = plan['entries'][-2:]
        self.assertEqual(('K12', 8137, 'K12-STREAM-V0.7'),
                         (stream['stratumId'], stream['plaintextBytes'], stream['seedId']))
        self.assertEqual(('K12', 360000, 'K12-MICROFILE-V0.3'),
                         (micro['stratumId'], micro['plaintextBytes'], micro['seedId']))
        self.assertEqual(6, len(set(e['runId'] for e in plan['entries'])))

    def test_unknown_device_or_scope_rejected(self):
        for key, value in [('profile','D2'),('api',33),('api',True),('abi','x86_64'),
            ('model','another'),('product','another'),('device','another'),
            ('manufacturer','another'),('buildType','userdebug'),('release','13'),
            ('fingerprint',''),('serial','emulator-5556'),('selinux','Permissive')]:
            with self.subTest(key=key), self.assertRaises(ValueError):
                bad = packet(); bad['environment'][key] = value
                physical.validate_contract(bad)
        for rows in [packet()['payloads'][:-1], packet()['payloads'] + ['other']]:
            with self.assertRaises(ValueError):
                bad = packet(); bad['payloads'] = rows; physical.validate_contract(bad)

    def test_every_live_field_is_bound(self):
        p = packet(); physical.validate_live(p, p['environment'])
        for field in p['environment']:
            with self.subTest(field=field), self.assertRaises(ValueError):
                live = dict(p['environment']); live[field] = 'wrong'
                physical.validate_live(p, live)

    def test_no_controller_needed_for_independent_rows(self):
        for payload in packet()['payloads'][:5] + ['PROBE']:
            physical.require_controller_for_payload(packet(), payload)
        for payload in packet()['payloads'][5:]:
            with self.assertRaises(ValueError): physical.require_controller_for_payload(packet(), payload)

    def test_physical_preflight_passes_exact_expected_arguments(self):
        argv = physical.fixed_command(packet(), 'P35-01')
        args = {argv[i+1]: argv[i+2] for i, v in enumerate(argv) if v == '-e'}
        self.assertEqual('POCO-M5-PHYSICAL', args['recoveryDeviceProfile'])
        self.assertEqual('34', args['recoveryExpectedApi'])
        self.assertEqual('arm64-v8a', args['recoveryExpectedAbi'])
        self.assertEqual('22071219CG', args['recoveryExpectedModel'])
        self.assertEqual('true', args['pocRecoveryE36GapiSupplementalSqlitePreflight'])

    def test_k12_exact_values_cannot_pass_generic_loss_bound(self):
        for candidate, values in [('REC-STREAM-TINK',(8137,4056,8136)),
                                   ('REC-MICROFILE-TINK',(360000,320000,320000))]:
            entry = dict(candidateId=candidate, stratumId='K12')
            observed = dict(zip(('acceptedEnd','committedEnd','recoveredEnd'), values))
            for field in observed:
                bad = dict(observed); bad[field] += 1
                self.assertTrue(physical.k12_failures(entry, bad))

    def test_unknown_payload_rejected(self):
        with self.assertRaises(ValueError): physical.require_controller_for_payload(packet(), 'UNKNOWN')

    def test_probe_target_requires_exact_process_and_normalized_context(self):
        ready = dict(nonce='a' * 32, pid=123, uid=10123, packageName=physical.PACKAGE,
                     processName=physical.PACKAGE, cmdline=physical.PACKAGE,
                     selinuxContext='u:r:untrusted_app:s0:c1,c2')
        physical.validate_probe_target(ready, 'a' * 32, '123\n',
                                       (physical.PACKAGE + '\x00').encode())
        for field, value in [('nonce', 'b' * 32), ('processName', 'test'),
                             ('packageName', 'test'), ('cmdline', 'test'),
                             ('uid', 0), ('selinuxContext', 'u:r:shell:s0')]:
            with self.subTest(field=field), self.assertRaises(ValueError):
                physical.validate_probe_target(dict(ready, **{field: value}), 'a' * 32,
                                               '123', (physical.PACKAGE + '\x00').encode())

    def test_k12_canonical_state_and_invalid_classification(self):
        entry = dict(attemptId='a', kind='HARD_KILL', candidateId=physical.campaign.CANDIDATES[1],
                     stratumId='K12', mutationVariants=['DEFAULT'])
        invalid = dict(attemptId='a', status='INVALID',
                       invalidReason='EXTERNAL_CONTROLLER_OR_PREFLIGHT_EVIDENCE_ENVELOPE_INCOMPLETE_OR_SCHEMA_INVALID')
        self.assertEqual('INCONCLUSIVE', physical.assess(entry, invalid)['verdict'])
        micro = dict(acceptedEnd=360000, committedEnd=320000, recoveredEnd=320000,
                     processingIntentCount=2, duplicateProcessingIntents=0,
                     missingProcessingIntents=0, implicitCommitCount=0,
                     receiptIdentity='a' * 64, repeatStable=True,
                     quarantineObservation=dict(rowCount=1, completedCount=1,
                         uniqueIntentCount=1, sourceItemCount=0, destinationItemCount=1,
                         terminalStates=['COMPLETED']))
        self.assertEqual([], physical.k12_failures(entry, micro, micro))
        for field, value in [('acceptedEnd', 360001), ('committedEnd', 319999),
                             ('recoveredEnd', 319999), ('processingIntentCount', 1),
                             ('duplicateProcessingIntents', 1), ('missingProcessingIntents', 1),
                             ('implicitCommitCount', 1)]:
            bad_replay = copy.deepcopy(micro)
            bad_replay[field] = value
            self.assertTrue(physical.k12_failures(entry, micro, bad_replay), field)
        for mutation in [dict(rowCount=2), dict(completedCount=0),
                         dict(destinationItemCount=0), dict(sourceItemCount=1)]:
            bad = copy.deepcopy(micro)
            bad['quarantineObservation'].update(mutation)
            self.assertTrue(physical.k12_failures(entry, bad, micro))

    def test_stream_k12_requires_persisted_state_readback_and_stable_replay(self):
        entry = dict(candidateId=physical.campaign.CANDIDATES[0], stratumId='K12')
        observed = dict(acceptedEnd=8137, committedEnd=4056, recoveredEnd=8136,
            processingIntentCount=0, duplicateProcessingIntents=0,
            missingProcessingIntents=0, implicitCommitCount=0,
            rangeStart=8192, rangeEnd=8193,
            rangeCertainty='EXACT_FORMAT_BOUNDARY', boundaryResult='EXACT_FORMAT_BOUNDARY',
            sourceUnchanged=True,
            sourceBytes=8192, preFaultSourceBytes=8192,
            currentSourceBytes=8193, observedSourceBytes=8193, receiptIdentity='a' * 64,
            repeatStable=True, streamPersistenceObservation=dict(sealedValidOutcomes=1,
                activeRanges=1, activeRangeStart=8192, activeRangeEnd=8193,
                activeRangeCertainty='EXACT_FORMAT_BOUNDARY', persistedStateDigest='b' * 64))
        self.assertEqual([], physical.k12_failures(entry, observed, copy.deepcopy(observed)))
        for field, value in [('acceptedEnd', 8138), ('committedEnd', 4057),
                             ('recoveredEnd', 8137), ('processingIntentCount', 1),
                             ('duplicateProcessingIntents', 1), ('missingProcessingIntents', 1),
                             ('implicitCommitCount', 1)]:
            bad_replay = copy.deepcopy(observed)
            bad_replay[field] = value
            self.assertTrue(physical.k12_failures(entry, observed, bad_replay), field)
        for field, value in [('sourceBytes', 8193), ('preFaultSourceBytes', 8193),
                             ('currentSourceBytes', 8192), ('observedSourceBytes', 8192),
                             ('sourceUnchanged', False)]:
            bad = copy.deepcopy(observed)
            bad[field] = value
            self.assertTrue(physical.k12_failures(entry, bad, copy.deepcopy(observed)), field)
        bad_replay = copy.deepcopy(observed)
        bad_replay['currentSourceBytes'] = 8194
        self.assertTrue(physical.k12_failures(entry, observed, bad_replay))
        for field, value in [('sealedValidOutcomes', 0), ('activeRanges', 2),
                             ('persistedStateDigest', 'c' * 64)]:
            bad_replay = copy.deepcopy(observed)
            bad_replay['streamPersistenceObservation'][field] = value
            self.assertTrue(physical.k12_failures(entry, observed, bad_replay))

    def test_payload_admission_is_selected_before_device(self):
        with TemporaryDirectory() as directory:
            p = packet()
            p['files'] = []
            p['proofs'] = {'review': {'path': str(Path(directory) / 'review.json'), 'sha256': '0' * 64}}
            for payload in list(physical.PAYLOADS)[:3] + ['PROBE', 'CLEANUP']:
                physical.precheck_payload(p, payload, [])
            with self.assertRaises(ValueError): physical.precheck_payload(p, 'P35-06', [])
            with self.assertRaises(ValueError): physical.precheck_payload(p, 'P35-04', None)

    def test_live_signal_identity_rejects_pid_uid_and_context_drift(self):
        package = physical.PACKAGE
        target = 'u:r:untrusted_app:s0:c1,c2'
        sender = 'u:r:runas_app:s0:c1,c2'
        digest = 'a' * 64
        responses = {
            ('shell', 'getenforce'): b'Enforcing\n',
            ('shell', 'sha256sum', '/system/bin/kill'): (digest + '  /system/bin/kill\n').encode(),
            ('shell', 'pidof', package): b'123\n',
            ('exec-out', 'run-as', package, 'cat', '/proc/123/cmdline'): (package + '\x00').encode(),
            ('exec-out', 'run-as', package, 'cat', '/proc/123/status'): b'Name:\tprobe\nUid:\t10123\t10123\t10123\t10123\n',
            ('exec-out', 'run-as', package, 'cat', '/proc/123/attr/current'): (target + '\n').encode(),
            ('exec-out', 'run-as', package, 'cat', '/proc/123/stat'):
                ('123 (probe) S ' + '0 ' * 18 + '12345 0\n').encode(),
            ('shell', '/system/bin/ps', '-ww', '-p', '123', '-o', 'PID,UID,LABEL,NAME'):
                ('PID UID LABEL NAME\n123 10123 ' + target + ' ' + package + '\n').encode(),
            ('shell', 'run-as', package, 'id', '-u'): b'10123\n',
            ('shell', 'run-as', package, 'cat', '/proc/self/attr/current'): (sender + '\n').encode(),
        }
        class Transport:
            def run(self, arguments, label, **kwargs):
                return subprocess.CompletedProcess(arguments, 0, responses[tuple(arguments)], b'')
        self.assertEqual(sender, physical.live_signal_identity(Transport(), 123, 10123,
                             target, digest, 'test', sender)['senderContext'])
        for command, changed in [
            (('shell', 'pidof', package), b'124\n'),
            (('exec-out', 'run-as', package, 'cat', '/proc/123/status'),
             b'Uid:\t10124\t10124\t10124\t10124\n'),
            (('shell', 'run-as', package, 'cat', '/proc/self/attr/current'), b'u:r:shell:s0\n'),
            (('shell', 'sha256sum', '/system/bin/kill'), b'wrong  /system/bin/kill\n')]:
            with self.subTest(command=command), self.assertRaises(ValueError):
                original = responses[command]
                responses[command] = changed
                try: physical.live_signal_identity(Transport(), 123, 10123, target, digest, 'test', sender)
                finally: responses[command] = original

    def test_completed_failed_preflight_is_distinct_from_success(self):
        selector = physical.PAYLOADS['P35-02'][0]
        cls, method = selector.split('#')
        output = (f'INSTRUMENTATION_STATUS: numtests=1\n'
            f'INSTRUMENTATION_STATUS: current=1\n'
            f'INSTRUMENTATION_STATUS: class={cls}\n'
            f'INSTRUMENTATION_STATUS: test={method}\n'
            'INSTRUMENTATION_STATUS_CODE: 1\n'
            f'INSTRUMENTATION_STATUS: numtests=1\n'
            f'INSTRUMENTATION_STATUS: current=1\n'
            f'INSTRUMENTATION_STATUS: class={cls}\n'
            f'INSTRUMENTATION_STATUS: test={method}\n'
            'INSTRUMENTATION_STATUS: stack=synthetic failure\n'
            'INSTRUMENTATION_STATUS_CODE: -2\n'
            'FAILURES!!!\nINSTRUMENTATION_CODE: -1\n').encode()
        self.assertEqual('FAIL', physical.fixed_completion(output, 'P35-02'))
        self.assertEqual('PASS', physical.fixed_completion(output.replace(b'STATUS_CODE: -2', b'STATUS_CODE: 0'),
                                                              'P35-02'))
        with self.assertRaises(ValueError):
            physical.fixed_completion(output.replace(b'INSTRUMENTATION_CODE: -1', b''), 'P35-02')
        with self.assertRaises(ValueError):
            physical.fixed_completion(output.replace(b'STATUS_CODE: -2', b'STATUS_CODE: -3'), 'P35-02')

    def test_failed_preflight_cleanup_requires_addressed_evidence(self):
        class CacheTransport:
            listing = b'other-task-file\n'
            def run(self, arguments, label):
                self.asserted = arguments == ['shell','run-as',physical.PACKAGE,'ls','cache']
                return subprocess.CompletedProcess(arguments, 0, self.listing, b'')
        transport=CacheTransport()
        self.assertTrue(physical.preflight_scoped_cleanup(transport,'P35-02',b'')['namespaceAbsent'])
        self.assertTrue(transport.asserted)
        transport.listing=b'rec-i3-sqlite-leftover\n'
        with self.assertRaises(ValueError):
            physical.preflight_scoped_cleanup(transport,'P35-02',b'')
        marker=b'INSTRUMENTATION_STATUS: stream=INSTRUMENTATION_PLATFORM_PREREQUISITES_OBSERVATION '
        observation=dict(keystore=dict(cleanupAliasAbsent=True),
                         filesystem=dict(cleanupOwnedNamespaceAbsent=True))
        self.assertTrue(physical.preflight_scoped_cleanup(transport,'P35-03',
                        marker+json.dumps(observation).encode()+b'\n')['verified'])
        observation['filesystem']['cleanupOwnedNamespaceAbsent']=False
        with self.assertRaises(ValueError):
            physical.preflight_scoped_cleanup(transport,'P35-03',
                                              marker+json.dumps(observation).encode()+b'\n')

    def test_supported_controller_requires_native_signal_and_death_receipts(self):
        with TemporaryDirectory() as directory:
            folder=Path(directory)
            def put(name, value):
                path=folder/name
                path.write_bytes(value if isinstance(value, bytes) else json.dumps(value).encode())
                return physical.descriptor(path)
            ready=put('probe-ready.json', dict(nonce='a'*32,pid=123,uid=10123,
                packageName=physical.PACKAGE,processName=physical.PACKAGE,
                cmdline=physical.PACKAGE,selinuxContext='u:r:untrusted_app:s0:c1'))
            command=put('probe-command.json',dict(nonce='a'*32,argv=['adb'],timeoutSeconds=45))
            proof=dict(schema='DORA_PHYSICAL_SIGKILL_PROBE_V1',status='SUPPORTED',
                environment=packet()['environment'],source=packet()['source'],
                kind='RUN_AS_EXACT_PID_SIGKILL',noRoot=True,signalExit=0,deathConfirmed=True,
                boundedMeasurementComplete=True,rawRetentionComplete=True,executed=True,
                result='PASS',countedAsCoverage=False,targetPid=123,targetUid=10123,
                senderUid=10123,targetCmdline=physical.PACKAGE,
                targetContext='u:r:untrusted_app:s0:c1',senderContext='u:r:runas_app:s0:c1',
                observationMethod='SHELL_PS_WIDE_EXACT_PID_V1',targetStartTimeTicks=12345,
                observationElapsedSeconds=1.0,naturalTimeoutObserved=False,
                selinux='Enforcing',killSha256='b'*64,
                signalCommand=['shell','run-as',physical.PACKAGE,'/system/bin/kill','-9','123'],
                signalStdoutHex='',signalStderrHex='',evidence=[ready,command])
            item=put('probe.json',proof)
            review=put('review.json',dict(files=[item]))
            p=packet();p['files']=[item];p['proofs']={'review':review};p['controllerProof']=item
            with self.assertRaises(ValueError): physical.require_controller_for_payload(p,'P35-06')
            signal='001-probe-sigkill'
            death='002-probe-death'
            proof['evidence'] += [
                put(signal+'.command.json',dict(argv=['adb',*proof['signalCommand']])),
                put(signal+'.result.json',dict(nativeExit=0,timedOut=False)),
                put(signal+'.stdout',b''), put(signal+'.stderr',b''),
                put(death+'.command.json',dict(argv=['adb','shell','pidof',physical.PACKAGE])),
                put(death+'.result.json',dict(nativeExit=1,timedOut=False)),
                put(death+'.stdout',b''), put(death+'.stderr',b'')]
            item=put('probe.json',proof);review=put('review.json',dict(files=[item]))
            p['files']=[item];p['proofs']['review']=review;p['controllerProof']=item
            physical.require_controller_for_payload(p,'P35-06')
            proof['schema']='UNBOUND'
            item=put('probe.json',proof);review=put('review.json',dict(files=[item]))
            p['files']=[item];p['proofs']['review']=review;p['controllerProof']=item
            with self.assertRaises(ValueError): physical.require_controller_for_payload(p,'P35-06')



class PhysicalObservationRepairTests(unittest.TestCase):
    target='u:r:untrusted_app:s0:c162,c256,c512,c768'
    sender='u:r:runas_app:s0:c162,c256,c512,c768'
    def transport(self):
        package=physical.PACKAGE
        stat=('123 (poco probe) S '+'0 '*18+'98765 0\n').encode()
        responses={
            ('shell','getenforce'): b'Enforcing\n',
            ('shell','sha256sum','/system/bin/kill'): ('a'*64+'  /system/bin/kill\n').encode(),
            ('shell','pidof',package):b'123\n',
            ('exec-out','run-as',package,'cat','/proc/123/cmdline'):(package+'\x00').encode(),
            ('exec-out','run-as',package,'cat','/proc/123/status'):b'Name:\tprobe\nUid:\t10162\t10162\t10162\t10162\n',
            ('exec-out','run-as',package,'cat','/proc/123/attr/current'):b'cat: /proc/123/attr/current: Permission denied\n',
            ('exec-out','run-as',package,'cat','/proc/123/stat'):stat,
            ('shell','/system/bin/ps','-ww','-p','123','-o','PID,UID,LABEL,NAME'):
                ('  PID UID LABEL NAME\n123 10162 '+self.target+' '+package+'\n').encode(),
            ('shell','run-as',package,'id','-u'):b'10162\n',
            ('shell','run-as',package,'cat','/proc/self/attr/current'):(self.sender+'\x00').encode(),
        }
        class Transport:
            calls=[]
            def run(inner,arguments,label,**kwargs):
                inner.calls.append((tuple(arguments),label))
                value=responses[tuple(arguments)]
                if isinstance(value,list):value=value.pop(0)
                if isinstance(value,subprocess.CompletedProcess):return value
                return subprocess.CompletedProcess(arguments,0,value,b'')
        return Transport(),responses
    def test_wide_shell_observation_replaces_denied_run_as_context(self):
        transport,_=self.transport()
        identity=physical.live_signal_identity(transport,123,10162,self.target,'a'*64,'test',self.sender)
        self.assertEqual(self.target,identity['targetContext'])
        self.assertEqual(98765,identity['targetStartTimeTicks'])
        self.assertEqual('SHELL_PS_WIDE_EXACT_PID_V1',identity['observationMethod'])
        self.assertFalse(any('attr/current' in arg and '/proc/123/' in arg for args,_ in transport.calls for arg in args))
    def test_observer_denial_in_stdout_with_native_zero_is_unavailable(self):
        transport,responses=self.transport()
        key=('shell','/system/bin/ps','-ww','-p','123','-o','PID,UID,LABEL,NAME')
        responses[key]=b'cat: /proc/123/attr/current: Permission denied\n'
        with self.assertRaisesRegex(ValueError,'OBSERVATION_UNAVAILABLE'):
            physical.live_signal_identity(transport,123,10162,self.target,'a'*64,'test',self.sender)
    def test_ps_exact_pid_uid_context_and_unambiguous_full_record(self):
        key=('shell','/system/bin/ps','-ww','-p','123','-o','PID,UID,LABEL,NAME')
        for mutation in ['pid','uid','context','truncated','duplicate','header','empty','native','stderr']:
            with self.subTest(mutation=mutation):
                transport,responses=self.transport();valid=responses[key]
                changes={'pid':valid.replace(b'123 10162',b'124 10162'),
                    'uid':valid.replace(b'123 10162',b'123 10163'),
                    'context':valid.replace(b'c768',b'c769'),
                    'truncated':valid.replace(b'c512,c768',b'c512,...'),
                    'duplicate':valid+valid.splitlines()[1]+b'\n',
                    'header':valid.replace(b'LABEL',b'CONTEXT'),'empty':b'',
                    'native':subprocess.CompletedProcess([],1,valid,b''),
                    'stderr':subprocess.CompletedProcess([],0,valid,b'Permission denied')}
                responses[key]=changes[mutation]
                with self.assertRaises(ValueError):
                    physical.live_signal_identity(transport,123,10162,self.target,'a'*64,'test',self.sender)
    def test_process_replacement_between_identity_reads_is_rejected(self):
        transport,responses=self.transport()
        key=('exec-out','run-as',physical.PACKAGE,'cat','/proc/123/stat')
        responses[key]=[responses[key],responses[key].replace(b'98765',b'98766')]
        with self.assertRaisesRegex(ValueError,'PROCESS_REPLACED'):
            physical.live_signal_identity(transport,123,10162,self.target,'a'*64,'test',self.sender)

class PhysicalSuccessorGateTests(unittest.TestCase):
    def test_signal_requires_clean_native_success(self):
        self.assertTrue(callable(getattr(physical,'signal_succeeded',None)))
        self.assertTrue(physical.signal_succeeded(subprocess.CompletedProcess([],0,b'',b'')))
        for code,out,err in [(0,b'Permission denied',b''),(0,b'',b'Operation not permitted'),(1,b'',b''),(0,b'unknown output',b'')]:
            self.assertFalse(physical.signal_succeeded(subprocess.CompletedProcess([],code,out,err)))
    def test_preflight_reuse_preserves_original_source_and_requires_review(self):
        self.assertTrue(callable(getattr(physical,'preflight_packet',None)))
        p=packet()
        self.assertEqual(p,physical.preflight_packet(p))
        with TemporaryDirectory() as directory:
            folder=Path(directory)
            def put(name,value):
                path=folder/name;path.write_text(json.dumps(value));return physical.descriptor(path)
            prior=packet(); prior['harnessSourceCommit']='4'*40;prior['source']['commit']='4'*40
            prior_item=put('prior.json',prior)
            proof=dict(schema='DORA_PHYSICAL_PREFLIGHT_REUSE_V1',priorPacket=prior_item,source=p['source'],
                executionId=p['executionId'],environment=p['environment'],priorPrerequisites=None,reviewedDiff=None)
            proof_item=put('reuse.json',proof)
            p['preflightReuse']=proof_item;p['files']=[proof_item,prior_item]
            review=put('review.json',dict(verdict='APPROVED',files=[]))
            p['proofs']={'review':review}
            with self.assertRaises(ValueError):physical.preflight_packet(p)
            p['files']=[]
            with self.assertRaises(ValueError):physical.preflight_packet(p)

    def test_reviewed_host_only_preflight_transfer_and_negative_controls(self):
        with TemporaryDirectory() as directory:
            folder=Path(directory)
            def put(name,value):
                path=folder/name;path.write_text(value if isinstance(value,str) else json.dumps(value));return physical.descriptor(path)
            p=packet();prior=packet();prior['harnessSourceCommit']='4'*40;prior['source']['commit']='4'*40
            prior_item=put('prior.json',prior);previous=put('prerequisites.json',[]);delta=put('diff.txt','reviewed host delta')
            proof=dict(schema='DORA_PHYSICAL_PREFLIGHT_REUSE_V1',priorPacket=prior_item,source=p['source'],
                executionId=p['executionId'],environment=p['environment'],priorPrerequisites=previous,reviewedDiff=delta)
            module=Path(physical.__file__).read_text(encoding='utf-8')
            changed=['tools/recovery_physical.py','tools/test_recovery_physical.py']
            def git(root,*args):
                if args[0]=='merge-base':return ''
                if args[0]=='show':return module
                if '--name-only' in args:return '\n'.join(changed)
                return 'reviewed host delta'
            def bind():
                item=put('reuse.json',proof)
                p['preflightReuse']=item;p['files']=[item,prior_item,previous,delta]
                p['proofs']={'review':put('review.json',dict(verdict='APPROVED',files=p['files']))}
            with patch.object(physical,'PREFLIGHT_COMMIT','4'*40),patch.object(physical,'PREFLIGHT_PACKET_SHA',prior_item['sha256']),patch.object(physical,'git',side_effect=git):
                bind();self.assertEqual(prior,physical.preflight_packet(p))
                changed.append('android/poc/recovery/src/main/Changed.kt')
                with self.assertRaisesRegex(ValueError,'Android or contract'):physical.preflight_packet(p)
                changed.pop()
                module=module.replace("'SQLite revision drift'","'altered SQLite validation'")
                with self.assertRaisesRegex(ValueError,'Preflight implementation changed'):physical.preflight_packet(p)
                module=Path(physical.__file__).read_text(encoding='utf-8')
                module=module.replace("PREFIX = 'com.monumentogram.dora.poc.recovery.'", "PREFIX = 'changed.'")
                with self.assertRaisesRegex(ValueError,'Preflight implementation changed'):physical.preflight_packet(p)
                module=Path(physical.__file__).read_text(encoding='utf-8')
                p['source']['testApkSha256']='9'*64;proof['source']=p['source'];bind()
                with self.assertRaisesRegex(ValueError,'APK drift'):physical.preflight_packet(p)
                p['source']['testApkSha256']=prior['source']['testApkSha256'];bind()
                p['environment']['fingerprint']='changed';proof['environment']=p['environment'];bind()
                with self.assertRaisesRegex(ValueError,'environment drift'):physical.preflight_packet(p)
                p['environment']=prior['environment'];proof['environment']=p['environment'];bind()
                (folder/'diff.txt').write_text('tampered')
                with self.assertRaisesRegex(ValueError,'digest mismatch'):physical.preflight_packet(p)

class PhysicalKillEnvelopeTests(unittest.TestCase):
    def run_envelope(self, death=None, elapsed=1, output=b''):
        with TemporaryDirectory() as directory:
            class Transport:
                def __init__(self):self.directory=Path(directory);self.signals=0
                def run(self,arguments,label,*args,**kwargs):
                    if label=='sigkill':
                        self.signals+=1
                        return subprocess.CompletedProcess([],0,b'',b'')
                    if label=='death-confirmation':return death or subprocess.CompletedProcess([],1,b'',b'')
                    return subprocess.CompletedProcess([],0,b'Enforcing',b'')
            transport=Transport()
            def kill(wrapper,*args):
                wrapper.run(['shell','run-as',physical.PACKAGE,'kill','-9','123'],'sigkill')
                wrapper.run(['shell','pidof',physical.PACKAGE],'death-confirmation',require_success=False)
                (transport.directory/'kill-instrument.stdout').write_bytes(output)
                return {'complete':True}
            controller=dict(kind='RUN_AS_EXACT_PID_SIGKILL',targetUid=10162,targetContext='target',senderContext='sender',killSha256='a'*64)
            with patch.object(physical.campaign,'run_kill',side_effect=kill),patch.object(physical,'live_signal_identity',return_value={}),patch.object(physical.campaign,'parse_events',return_value=[{'eventType':'ERROR'}] if b'ERROR' in output else []),patch.object(physical.time,'monotonic',side_effect=[0,elapsed,elapsed,elapsed]):
                try:return physical.run_physical_kill(transport,{}, {},'DEFAULT',controller)
                finally:self.signals=transport.signals

    def test_death_requires_clean_absence(self):
        self.assertEqual({'complete':True},self.run_envelope())
        for death in [subprocess.CompletedProcess([],1,b'',b'Permission denied'),subprocess.CompletedProcess([],0,b'',b''),subprocess.CompletedProcess([],1,b'123',b'')]:
            with self.assertRaisesRegex(ValueError,'DEATH_OBSERVATION'):self.run_envelope(death=death)

    def test_expired_attempt_cannot_dispatch(self):
        with self.assertRaisesRegex(ValueError,'BARRIER_EXPIRED'):self.run_envelope(elapsed=91)
        self.assertEqual(0,self.signals)

    def test_timeout_error_and_graceful_completion_cannot_be_credited(self):
        for output in [b'ERROR',b'External controller did not kill the paused process',b'INSTRUMENTATION_STATUS_CODE: 0\n']:
            with self.assertRaisesRegex(ValueError,'BARRIER_COMPLETED'):self.run_envelope(output=output)

if __name__ == '__main__': unittest.main()
