"""Negative controls for the bounded API33 route; no devices or services."""
import copy
import unittest
from unittest.mock import patch
from pathlib import Path
import tempfile
import json
import recovery_api33 as api


class Api33AdmissionTests(unittest.TestCase):
    def setUp(self):
        self.packet = {
            "schema": api.SCHEMA, "executionId": "F34-01",
            "appSourceCommit": api.APP_COMMIT,
            "harnessSourceCommit": "1" * 40,
            "source": {"commit": "1" * 40, "tree": "2" * 40,
                       "appApkSha256": api.APP_SHA, "testApkSha256": "3" * 64},
            "environment": {"profile": "API33-GAPI", "api": 33, "abi": "x86_64",
                            "fingerprint": "google/test:13/test/userdebug", "product": "test",
                            "serial": "emulator-5560", "avdName": "api33_fixture", "adbPort": 5037},
            "payloads": list(api.PAYLOADS),
        }

    def test_exact_api33_contract(self):
        api.validate_contract(self.packet)

    def test_wrong_api_abi_or_missing_identity(self):
        for field, value in [("api", 36), ("api", True), ("abi", "arm64-v8a"),
                             ("fingerprint", ""), ("product", ""), ("profile", "E36-GAPI")]:
            with self.subTest(field=field, value=value):
                bad = copy.deepcopy(self.packet); bad["environment"][field] = value
                with self.assertRaises(ValueError): api.validate_contract(bad)

    def test_rejects_app_replacement_and_harness_mislabel(self):
        for key in ("appApkSha256", "commit"):
            bad = copy.deepcopy(self.packet); bad["source"][key] = "4" * len(bad["source"][key])
            with self.assertRaises(ValueError): api.validate_contract(bad)
        bad = copy.deepcopy(self.packet); bad["appSourceCommit"] = "4" * 40
        with self.assertRaises(ValueError): api.validate_contract(bad)

    def test_rejects_expanded_or_missing_payload(self):
        for values in [list(api.PAYLOADS) + ["K12"], list(api.PAYLOADS)[:-1], ["F33-01"] * 7]:
            bad = copy.deepcopy(self.packet); bad["payloads"] = values
            with self.assertRaises(ValueError): api.validate_contract(bad)

    def test_live_identity_must_equal_every_pinned_field(self):
        api.validate_live(self.packet, self.packet["environment"])
        for key in ("api", "abi", "fingerprint", "product", "serial", "avdName", "adbPort"):
            wrong = dict(self.packet["environment"]); wrong[key] = "wrong"
            with self.assertRaises(ValueError): api.validate_live(self.packet, wrong)

    def test_fresh_session_only(self):
        from datetime import datetime, timezone, timedelta
        now = datetime.now(timezone.utc)
        api.validate_expiry((now + timedelta(seconds=120)).isoformat(), now)
        for seconds in (-1, 301):
            with self.assertRaises(ValueError): api.validate_expiry((now + timedelta(seconds=seconds)).isoformat(), now)

    def test_flat_endpoint_cannot_override_admitted_device(self):
        self.packet['adb'] = {'sha256': 'a' * 64}
        env = self.packet['environment']
        session = dict(environment=env, serial=env['serial'], adbPort=env['adbPort'],
                       deviceFingerprint=env['fingerprint'], avdName=env['avdName'], adbSha256='a' * 64)
        api.validate_session_endpoint(self.packet, session)
        for key in ('serial', 'adbPort', 'deviceFingerprint', 'avdName', 'adbSha256'):
            with self.subTest(key=key), self.assertRaises(ValueError):
                api.validate_session_endpoint(self.packet, dict(session, **{key: 'wrong'}))

    def test_normal_plan_reuses_fixture_and_exact_k08_barriers(self):
        plan = api.build_plan(Path(__file__).resolve().parents[1], self.packet)
        self.assertEqual(4, len(plan['entries']))
        for normal, kill in zip(plan['entries'][:2], plan['entries'][2:]):
            self.assertEqual('NORMAL', normal['kind'])
            self.assertEqual('HARD_KILL', kill['kind'])
            self.assertNotEqual(normal['attemptId'], kill['attemptId'])
            self.assertNotEqual(normal['runId'], kill['runId'])
            for field in ('plaintextBytes', 'fixtureSha256', 'seed', 'publicBarrier', 'stratumId'):
                self.assertEqual(normal[field], kill[field])

    def test_preflight_requires_final_cleanup_and_original_status(self):
        with tempfile.TemporaryDirectory() as td:
            root = Path(td)
            items = []
            for payload in list(api.PAYLOADS)[:3]:
                attempt = root / payload; (attempt / 'p').mkdir(parents=True)
                rec = attempt / 'p/record.json'
                rec.write_text(json.dumps(dict(payload=payload, result='PASS', executed=True,
                    packetSha256='a' * 64, rawRetentionComplete=True, source=self.packet['source'], environment=self.packet['environment'])))
                term = attempt / 'terminal.json'
                terminal = dict(cleanupResult='VERIFIED', failure=None, bootstrap=False,
                                packetSha256='a' * 64, source=self.packet['source'], payload=payload)
                term.write_text(json.dumps(terminal))
                raw = attempt / 'p/instrumentation.stdout'; raw.write_bytes(InstrumentationTests().output(payload))
                items.append(dict(record=api.descriptor(rec), terminal=api.descriptor(term), instrumentationStdout=api.descriptor(raw)))
            with patch.object(api, 'verify_observations'):
                api.verify_preflights(self.packet, items)
                term.write_text(json.dumps(dict(terminal, cleanupResult='UNCERTAIN')))
                items[-1]['terminal'] = api.descriptor(term)
                with self.assertRaisesRegex(ValueError, 'cleanup'): api.verify_preflights(self.packet, items)
                term.write_text(json.dumps(terminal))
                items[-1]['terminal'] = api.descriptor(term)
                raw.write_bytes(InstrumentationTests().output('F33-03', -3))
                items[-1]['instrumentationStdout'] = api.descriptor(raw)
                with self.assertRaises(ValueError): api.verify_preflights(self.packet, items)

    def test_normal_rejects_short_prefix_and_requires_authenticated_eof(self):
        entry = dict(kind='NORMAL', plaintextBytes=480000, candidateId=api.campaign.CANDIDATES[0])
        observation = dict(acceptedEnd=480000, committedEnd=473256, recoveredEnd=480000, streamTerminal='AUTHENTICATED_EOF')
        def generic(*args): return dict(verdict='PASS', failures=[])
        with patch.object(api.campaign, 'evaluate_attempt', side_effect=generic):
            result = dict(status='VALID', candidateResult=observation)
            self.assertEqual('PASS', api.assess(entry, result)['verdict'])
            for changed in [dict(recoveredEnd=473256), dict(streamTerminal='TRUNCATED')]:
                bad = dict(result, candidateResult=dict(observation, **changed))
                self.assertEqual('FAIL', api.assess(entry, bad)['verdict'])

    def test_normal_cannot_dispatch_fault_or_external_barrier(self):
        for operation in ('FAULT', 'WRITE_UNTIL_BARRIER'):
            with self.assertRaises(ValueError): api.run_operation(None, {}, {'kind': 'NORMAL'}, operation, 'DEFAULT')

    def test_valid_kill_missing_candidate_remains_original_fail(self):
        entry = api.build_plan(Path(__file__).resolve().parents[1], self.packet)['entries'][2]
        result = dict(attemptId=entry['attemptId'], status='VALID', candidateResult=None,
                      kill={key: True for key in api.campaign.KILL_PROOFS}, cleanupResult='UNVERIFIED')
        self.assertEqual('FAIL', api.assess(entry, result)['verdict'])


class InstrumentationTests(unittest.TestCase):
    def output(self, payload, code=0):
        selector, _, markers = api.PAYLOADS[payload]
        cls, method = selector.split('#')
        fields = f'INSTRUMENTATION_STATUS: class={cls}\nINSTRUMENTATION_STATUS: test={method}\nINSTRUMENTATION_STATUS: numtests=1\nINSTRUMENTATION_STATUS: current=1\n'
        observations = ''.join('INSTRUMENTATION_STATUS: stream=' + m + '{}\nINSTRUMENTATION_STATUS_CODE: 0\n' for m in markers)
        return (fields + 'INSTRUMENTATION_STATUS_CODE: 1\n' + observations + fields + f'INSTRUMENTATION_STATUS_CODE: {code}\nINSTRUMENTATION_CODE: -1\n').encode()

    def test_all_three_exact_success_and_sqlite_two_markers(self):
        for key in list(api.PAYLOADS)[:3]: api.validate_fixed_status(self.output(key), key)

    def test_skip_failure_missing_terminal_wrong_selector_never_pass(self):
        payload = 'F33-02'; good = self.output(payload)
        for bad in [self.output(payload, -3), self.output(payload, -2), b'OK (0 tests)\nINSTRUMENTATION_CODE: -1\n',
                    good.replace(b'primaryAndConcurrentReaderKeepConfigurationAfterReopen', b'wrong'),
                    good.replace(b'INSTRUMENTATION_CODE: -1\n', b'')]:
            with self.assertRaises(ValueError): api.validate_fixed_status(bad, payload)

    def test_sqlite_missing_duplicate_observation_rejected(self):
        good = self.output('F33-01')
        marker = api.PAYLOADS['F33-01'][2][0]
        bundle = ('INSTRUMENTATION_STATUS: stream=' + marker + '{}\nINSTRUMENTATION_STATUS_CODE: 0\n').encode()
        for bad in [good.replace(bundle, b''), good.replace(bundle, bundle * 2)]:
            with self.assertRaises(ValueError): api.validate_fixed_status(bad, 'F33-01')


if __name__ == '__main__': unittest.main()
