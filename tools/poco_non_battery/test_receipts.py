"""Synthetic receipts: no microphone, identities or audio."""
import unittest

from receipts import validate_cycles, validate_long


def cycle(number):
    return dict(attempt=number, preconditionValid=True, started=True, finalized=True,
                readbackFrames=48000, durableFrames=48000, admittedFrames=48000,
                wholeRecordingLost=False, microphoneReleased=True, fgsStopped=True,
                campaignIdentity=f'{number:064x}', ownerIdentity=False, assetDisposition='OWNED',
                unexplainedGaps=0, unexplainedDuplicates=0, corruptSegments=0, readErrors=0,
                nativeSessionAbsent=True, deletion=dict(productDeletionCompleted=True,
                    verifiedAbsentTargets=7, ownerRecordingsPreserved=46))


def long_run():
    return dict(run='DORA-LONG-01', pid=123, screenOffStartMs=1000,
                screenOffEndMs=3601000, callbackCoverage=True,
                events=[], samples=[dict(elapsedMs=x, pid=123, interactive=False,
                                        thermal=0, fgs=True, microphone=True,
                                        nativeRecording=True, nativeSession=7,
                                        batterySaver=False, playbackActive=False, audioMode=0,
                                        fgsPid=123, microphoneFgsType=True,
                                        audioConfigurations=[dict(session=7, deviceType=15,
                                                                 silenced=False, sampleRate=16000,
                                                                 channels=1, encoding=2)])
                                    for x in range(1000, 3601001, 30000)],
                admittedFrames=57600000, durableFrames=57600000,
                readbackFrames=57600000, unexplainedGaps=0, unexplainedDuplicates=0,
                corruptSegments=0, attributedBytes=125000000,
                logicalPositiveGrowthBytes=124000000, allocatedPositiveGrowthBytes=125000000,
                storageScope='VAULT_POSITIVE_FILE_GROWTH_MAX_LOGICAL_ALLOCATED',
                readErrors=0, vadInference=1, vadRuntime='PINNED_SHERPA_SILERO',
                vadProof=dict(factoryClass='com.monumentogram.dora.vad.sherpa.SherpaEngineFactory',
                              engineClass='com.monumentogram.dora.vad.sherpa.SherpaVadEngine',
                              bindingClass='com.monumentogram.dora.vad.sherpa.ReflectiveSherpaBinding',
                              nativeInstanceClass='com.k2fsa.sherpa.onnx.Vad', nativeVersion='1.13.8',
                              profileModelSha256='1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3',
                              namedLibraryMapping=False, executableApkMapping=True),
                microphoneReleased=True, fgsStopped=True, nativeSessionAbsent=True,
                callbackRegisteredMs=0, callbackBarrierMs=3602000,
                callbackUnregisteredMs=3601999, finalEventsDrained=True,
                started=True, finalized=True, wholeRecordingLost=False, ownerIdentity=False,
                assetDisposition='OWNED', campaignIdentity='a' * 64,
                sourceFingerprint='b' * 64, sourceVersion=1, authorizationUnits=1,
                capCount=5, chunks=[dict(first=n * 9600000, end=(n + 1) * 9600000,
                    processingFirst=0 if n == 0 else n * 9600000 - 32000,
                    open='START' if n == 0 else 'CAP', close='CAP' if n < 5 else 'STOP',
                    epochFingerprint='c' * 64, chunkFingerprint=f'{n:064x}', profileSha256='1c5ceb70f08593dfa96e522f43c51b7b28e36ab8b65a423fae417cc4e1cd2292')
                    for n in range(6)],
                deletion=dict(productDeletionCompleted=True, verifiedAbsentTargets=7,
                              ownerRecordingsPreserved=46))


class ReceiptTest(unittest.TestCase):
    def test_exact_denominator(self):
        self.assertTrue(validate_cycles([cycle(n) for n in range(1, 201)])['pass'])
        with self.assertRaises(ValueError):
            validate_cycles([cycle(n) for n in range(1, 200)])

    def test_duplicate_attempt_rejected(self):
        rows = [cycle(n) for n in range(1, 201)]
        rows[-1] = rows[0]
        with self.assertRaises(ValueError):
            validate_cycles(rows)

    def test_failure_is_retained_not_removed(self):
        rows = [cycle(n) for n in range(1, 201)]
        rows[0].update(started=False, finalized=False, readbackFrames=0)
        self.assertTrue(validate_cycles(rows)['pass'])
        rows[1].update(finalized=False)
        self.assertFalse(validate_cycles(rows)['pass'])  # 198/199 <99.5%

    def test_invalid_preconditions_cannot_enter_denominator(self):
        rows = [cycle(n) for n in range(1, 201)]
        rows[0]['preconditionValid'] = False
        with self.assertRaises(ValueError):
            validate_cycles(rows)

    def test_identity_collision_or_owner_rejected(self):
        for key, value in [('ownerIdentity', True), ('campaignIdentity', f'{2:064x}')]:
            rows = [cycle(n) for n in range(1, 201)]
            rows[0][key] = value
            with self.assertRaises(ValueError):
                validate_cycles(rows)

    def test_real_start_failure_may_have_no_asset(self):
        rows = [cycle(n) for n in range(1, 201)]
        rows[0].update(started=False, finalized=False, campaignIdentity=None,
                       assetDisposition='NO_ASSET_VERIFIED')
        self.assertTrue(validate_cycles(rows)['pass'])
        rows[0]['assetDisposition'] = 'UNKNOWN'
        self.assertFalse(validate_cycles(rows)['pass'])

    def test_complete_long_window(self):
        self.assertTrue(validate_long(long_run())['pass'])

    def test_screen_wake_callback_rejects_even_between_samples(self):
        row = long_run()
        row['events'] = [dict(kind='SCREEN_ON', elapsedMs=2000)]
        self.assertFalse(validate_long(row)['pass'])

    def test_coverage_missing_or_sampling_gap_rejects(self):
        for alteration in ('coverage', 'sample'):
            row = long_run()
            if alteration == 'coverage':
                row['callbackCoverage'] = False
            else:
                del row['samples'][1]
            self.assertFalse(validate_long(row)['pass'])

    def test_thermal_transition_cannot_hide_between_samples(self):
        row = long_run()
        row['events'] = [dict(kind='THERMAL', elapsedMs=2000, thermal=3)]
        self.assertFalse(validate_long(row)['pass'])

    def test_storage_one_byte_over_fails(self):
        row = long_run()
        row['attributedBytes'] += 1
        row['allocatedPositiveGrowthBytes'] += 1
        self.assertFalse(validate_long(row)['pass'])

    def test_storage_cannot_disagree_with_measured_growth(self):
        row = long_run(); row['attributedBytes'] = 0
        with self.assertRaises(ValueError):
            validate_long(row)

    def test_consistent_but_unadmitted_profile_cannot_pass(self):
        row = long_run()
        for chunk in row['chunks']:
            chunk['profileSha256'] = 'e' * 64
        self.assertFalse(validate_long(row)['pass'])

    def test_missing_storage_scope_rejects(self):
        row = long_run()
        del row['storageScope']
        with self.assertRaises(ValueError):
            validate_long(row)

    def test_canonical_mismatch_or_read_error_fails(self):
        for key in ('admittedFrames', 'durableFrames', 'readErrors'):
            row = long_run()
            row[key] += 1
            self.assertFalse(validate_long(row)['pass'])

    def test_no_missing_telemetry_coerced_to_zero(self):
        row = long_run()
        row['samples'][1]['thermal'] = None
        with self.assertRaises(ValueError):
            validate_long(row)

    def test_native_or_route_evidence_cannot_be_replaced_by_thread_health(self):
        for replacement in ([], [dict(session=7, deviceType=7, silenced=True,
                                     sampleRate=16000, channels=1, encoding=2)]):
            row = long_run()
            row['samples'][1]['audioConfigurations'] = replacement
            self.assertFalse(validate_long(row)['pass'])

    def test_callback_barrier_or_late_wake_cannot_be_omitted(self):
        row = long_run()
        row['callbackBarrierMs'] = row['screenOffEndMs'] - 1
        self.assertFalse(validate_long(row)['pass'])
        row = long_run()
        row['events'] = [dict(kind='SCREEN_ON', elapsedMs=3601500)]
        self.assertFalse(validate_long(row)['pass'])

    def test_vad_requires_real_binding_and_exact_model_not_named_maps(self):
        self.assertTrue(validate_long(long_run())['pass'])  # Direct-from-APK native mapping.
        for field, value in [('bindingClass', 'FakeBinding'), ('nativeVersion', 'unknown'),
                             ('profileModelSha256', '0' * 64)]:
            row = long_run()
            row['vadProof'][field] = value
            self.assertFalse(validate_long(row)['pass'])

    def test_native_session_must_not_restart_between_samples(self):
        row = long_run()
        row['samples'][1]['nativeSession'] = 8
        row['samples'][1]['audioConfigurations'][0]['session'] = 8
        self.assertFalse(validate_long(row)['pass'])

    def test_missing_chunks_or_false_cap_count_rejected(self):
        for key, value in [('chunks', []), ('capCount', 0), ('authorizationUnits', 2)]:
            row = long_run()
            row[key] = value
            self.assertFalse(validate_long(row)['pass'])

    def test_chunk_gap_overlap_or_epoch_restart_fails(self):
        for key, value in [('first', 9600001), ('processingFirst', 9600000),
                           ('epochFingerprint', 'e' * 64), ('end', 19200001)]:
            row = long_run()
            row['chunks'][1][key] = value
            self.assertFalse(validate_long(row)['pass'])

    def test_finalization_and_cleanup_cannot_be_assumed(self):
        for key in ('finalized', 'started'):
            row = long_run()
            row[key] = False
            self.assertFalse(validate_long(row)['pass'])
        row = long_run()
        row['deletion']['productDeletionCompleted'] = False
        self.assertFalse(validate_long(row)['pass'])

    def test_cycle_corruption_read_error_or_unverified_cleanup_fails(self):
        for key in ('corruptSegments', 'unexplainedGaps', 'unexplainedDuplicates', 'readErrors'):
            rows = [cycle(n) for n in range(1, 201)]
            rows[0][key] = 1
            self.assertFalse(validate_cycles(rows)['pass'])
        rows = [cycle(n) for n in range(1, 201)]
        rows[0]['deletion']['ownerRecordingsPreserved'] = 45
        self.assertFalse(validate_cycles(rows)['pass'])

    def test_transient_route_silencing_or_playback_event_fails(self):
        for event in [dict(kind='AUDIO_CONFIG', elapsedMs=2000, configurations=[]),
                      dict(kind='PLAYBACK', elapsedMs=2000, active=True),
                      dict(kind='POWER_SAVE', elapsedMs=2000, enabled=True)]:
            row = long_run()
            row['events'] = [event]
            self.assertFalse(validate_long(row)['pass'])

    def test_call_mode_and_saver_in_samples_fail(self):
        for key, value in [('audioMode', 3), ('batterySaver', True), ('playbackActive', True)]:
            row = long_run()
            row['samples'][1][key] = value
            self.assertFalse(validate_long(row)['pass'])


if __name__ == '__main__':
    unittest.main()
