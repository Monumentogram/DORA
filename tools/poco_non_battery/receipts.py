"""Fail-closed non-battery physical receipts. Never evaluates energy efficiency."""
import re


def require(condition, label):
    if not condition:
        raise ValueError(label)


def integer(row, key, minimum=0):
    value = row.get(key)
    require(type(value) is int and value >= minimum, 'Invalid integer: ' + key)
    return value


def boolean(row, key):
    value = row.get(key)
    require(type(value) is bool, 'Invalid boolean: ' + key)
    return value


def cleanup_verified(row):
    deletion = row.get('deletion')
    require(isinstance(deletion, dict), 'Missing product deletion receipt')
    return (boolean(deletion, 'productDeletionCompleted')
            and integer(deletion, 'verifiedAbsentTargets') > 0
            and integer(deletion, 'ownerRecordingsPreserved') == 46)


def audio_configuration_valid(configurations, native_session):
    require(isinstance(configurations, list), 'Audio configurations unavailable')
    matching = [c for c in configurations if integer(c, 'session') == native_session]
    if len(matching) != 1:
        return False
    config = matching[0]
    return (integer(config, 'deviceType') == 15 and not boolean(config, 'silenced')
            and integer(config, 'sampleRate') == 16000 and integer(config, 'channels') == 1
            and integer(config, 'encoding') == 2)


def chunks_verified(row, frames, minimum_caps=5):
    chunks = row.get('chunks')
    require(isinstance(chunks, list), 'Missing technical chunks')
    if not chunks:
        return False
    end = caps = 0
    identities = set()
    epoch = chunks[0].get('epochFingerprint')
    profile = chunks[0].get('profileSha256')
    ok = profile == '1c5ceb70f08593dfa96e522f43c51b7b28e36ab8b65a423fae417cc4e1cd2292' and all(isinstance(v, str) and re.fullmatch('[0-9a-f]{64}', v)
             for v in (epoch, profile, row.get('sourceFingerprint')))
    for number, chunk in enumerate(chunks):
        first, last = integer(chunk, 'first'), integer(chunk, 'end', 1)
        digest = chunk.get('chunkFingerprint')
        ok &= (isinstance(digest, str) and re.fullmatch('[0-9a-f]{64}', digest) is not None
               and digest not in identities)
        identities.add(digest)
        ok &= (first == end and 0 < last - first <= 9_600_000
               and chunk.get('epochFingerprint') == epoch and chunk.get('profileSha256') == profile
               and chunk.get('open') == ('START' if number == 0 else 'CAP')
               and integer(chunk, 'processingFirst') == (0 if number == 0 else first - 32_000))
        if number < len(chunks) - 1:
            ok &= chunk.get('close') == 'CAP' and last - first == 9_600_000
            caps += 1
        else:
            ok &= chunk.get('close') == 'STOP'
        end = last
    return bool(ok and end == frames and caps >= minimum_caps and caps == integer(row, 'capCount')
                and integer(row, 'authorizationUnits') == 1 and integer(row, 'sourceVersion', 1) == 1)


def validate_cycles(rows):
    return cycle_evidence(rows, 200)


def cycle_evidence(rows, expected):
    require(expected in (60, 200), 'Unknown governed campaign size')
    require(len(rows) == expected, 'Exact governed attempts required')
    require([integer(r, 'attempt', 1) for r in rows] == list(range(1, expected + 1)),
            'Attempt order, duplicate or missing attempt')
    started = finalized = lost = 0
    identities = set()
    integrity = True
    for row in rows:
        require(boolean(row, 'preconditionValid'), 'Invalid precondition in denominator')
        did_start, did_finalize = boolean(row, 'started'), boolean(row, 'finalized')
        require(not did_finalize or did_start, 'Finalize without Start')
        started += did_start
        finalized += did_finalize
        lost += boolean(row, 'wholeRecordingLost')
        require(not boolean(row, 'ownerIdentity'), 'Owner recording targeted')
        identity = row.get('campaignIdentity')
        disposition = row.get('assetDisposition')
        require(disposition in ('OWNED', 'NO_ASSET_VERIFIED', 'UNKNOWN'), 'Unknown asset disposition')
        if disposition == 'OWNED':
            require(isinstance(identity, str) and re.fullmatch('[0-9a-f]{64}', identity),
                    'Missing campaign identity')
            require(identity not in identities, 'Duplicate recording identity')
            identities.add(identity)
        else:
            require(not did_start and not did_finalize and identity is None,
                    'No-asset disposition contradicts recording')
            integrity &= disposition == 'NO_ASSET_VERIFIED'
        integrity &= boolean(row, 'microphoneReleased') and boolean(row, 'fgsStopped')
        if did_finalize:
            frames = integer(row, 'readbackFrames', 1)
            integrity &= frames == integer(row, 'durableFrames') == integer(row, 'admittedFrames')
            integrity &= all(integer(row, key) == 0 for key in (
                'unexplainedGaps', 'unexplainedDuplicates', 'corruptSegments', 'readErrors'))
            integrity &= boolean(row, 'nativeSessionAbsent') and cleanup_verified(row)
    success = (started >= 199 and 200 * finalized >= 199 * started) if expected == 200 else started == finalized == 60
    return dict(attempts=expected, started=started, finalized=finalized, wholeRecordingLoss=lost,
                **{'pass': success
                   and lost == 0 and integrity})


def validate_long(row):
    require(row.get('run') in ('DORA-LONG-01', 'DORA-LONG-02', 'DORA-LONG-03'), 'Unknown run')
    start, end = integer(row, 'screenOffStartMs'), integer(row, 'screenOffEndMs')
    require(end > start, 'Invalid duration')
    pid = integer(row, 'pid', 1)
    samples, events = row.get('samples'), row.get('events')
    require(isinstance(samples, list) and len(samples) >= 2, 'Missing samples')
    require(isinstance(events, list), 'Missing callbacks')
    times = [integer(s, 'elapsedMs') for s in samples]
    require(all(a < b for a, b in zip(times, times[1:])), 'Nonmonotonic samples')
    coverage = (boolean(row, 'callbackCoverage') and times[0] <= start and times[-1] >= end
                and max(b - a for a, b in zip(times, times[1:])) <= 35000)
    barrier = integer(row, 'callbackBarrierMs')
    coverage &= (integer(row, 'callbackRegisteredMs') <= start and barrier >= end
                 and end <= integer(row, 'callbackUnregisteredMs') <= barrier
                 and boolean(row, 'finalEventsDrained'))
    screen = thermal = live = controls = True
    initial_session = integer(samples[0], 'nativeSession', 1)
    for sample in samples:
        screen &= not boolean(sample, 'interactive')
        thermal &= integer(sample, 'thermal') < 3
        controls &= (not boolean(sample, 'batterySaver') and not boolean(sample, 'playbackActive')
                     and integer(sample, 'audioMode') == 0)
        live &= (integer(sample, 'pid', 1) == pid and boolean(sample, 'fgs')
                 and boolean(sample, 'microphone') and boolean(sample, 'nativeRecording')
                 and integer(sample, 'fgsPid', 1) == pid and boolean(sample, 'microphoneFgsType'))
        native_session = integer(sample, 'nativeSession', 1)
        live &= native_session == initial_session
        live &= audio_configuration_valid(sample.get('audioConfigurations'), native_session)
    for event in events:
        when = integer(event, 'elapsedMs')
        require(event.get('kind') in ('SCREEN_ON', 'SCREEN_OFF', 'THERMAL', 'AUDIO_CONFIG',
                                     'PLAYBACK', 'POWER_SAVE', 'AUDIO_MODE'), 'Unknown callback')
        if start <= when <= barrier:
            screen &= event['kind'] != 'SCREEN_ON'
        if event['kind'] == 'THERMAL':
            thermal &= integer(event, 'thermal') < 3
        if start <= when <= end:
            if event['kind'] == 'AUDIO_CONFIG':
                live &= audio_configuration_valid(event.get('configurations'), initial_session)
            if event['kind'] == 'PLAYBACK':
                controls &= not boolean(event, 'active')
            if event['kind'] == 'POWER_SAVE':
                controls &= not boolean(event, 'enabled')
            if event['kind'] == 'AUDIO_MODE':
                controls &= integer(event, 'mode') == 0
    frames = integer(row, 'readbackFrames', 1)
    integrity = (frames == integer(row, 'durableFrames') == integer(row, 'admittedFrames')
                 and integer(row, 'unexplainedGaps') == 0
                 and integer(row, 'unexplainedDuplicates') == 0
                 and integer(row, 'corruptSegments') == 0 and integer(row, 'readErrors') == 0)
    integrity &= (boolean(row, 'started') and boolean(row, 'finalized')
                  and not boolean(row, 'wholeRecordingLost') and not boolean(row, 'ownerIdentity')
                  and row.get('assetDisposition') == 'OWNED' and chunks_verified(row, frames))
    require(row.get('storageScope') == 'VAULT_POSITIVE_FILE_GROWTH_MAX_LOGICAL_ALLOCATED',
            'Storage accounting incomplete')
    require(integer(row, 'attributedBytes') == max(integer(row, 'logicalPositiveGrowthBytes'),
            integer(row, 'allocatedPositiveGrowthBytes')), 'Storage growth totals disagree')
    storage = integer(row, 'attributedBytes') * 57_600_000 <= 125_000_000 * frames
    vad = vad_verified(row)
    released = (boolean(row, 'microphoneReleased') and boolean(row, 'fgsStopped')
                and boolean(row, 'nativeSessionAbsent') and cleanup_verified(row))
    return dict(coverage=coverage, screen=screen, thermal=thermal, live=live, controls=controls,
                integrity=integrity, storage=storage, vad=vad, released=released,
                **{'pass': end - start >= 3_600_000 and all(
                    (coverage, screen, thermal, live, controls, integrity, storage, vad, released))})


def vad_verified(row):
    vad = (integer(row, 'vadInference') > 0 and row.get('vadRuntime') == 'PINNED_SHERPA_SILERO')
    proof = row.get('vadProof')
    require(isinstance(proof, dict), 'Missing VAD implementation proof')
    expected_vad = dict(factoryClass='com.monumentogram.dora.vad.sherpa.SherpaEngineFactory',
                        engineClass='com.monumentogram.dora.vad.sherpa.SherpaVadEngine',
                        bindingClass='com.monumentogram.dora.vad.sherpa.ReflectiveSherpaBinding',
                        nativeInstanceClass='com.k2fsa.sherpa.onnx.Vad', nativeVersion='1.13.8',
                        profileModelSha256='1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3')
    return vad and all(proof.get(key) == value for key, value in expected_vad.items())
