"""Prospective document generator tests; all proof files synthetic, no AWS."""
import copy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

try:
    from tools.cloud62d_aws import bounded_phase_a as phase
except ImportError:
    phase = None


class PhaseTests(unittest.TestCase):
    def setUp(self):
        self.assertIsNotNone(phase, 'bounded prospective generator missing')
        self.repo = Path(__file__).resolve().parents[2]

    def test_draft_has_no_actual_pass_or_publication_authority_and_preserves_39_gates(self):
        record = phase.build_draft(self.repo)
        self.assertEqual(record['phase_a'], 'DRAFT_AWAITING_VERIFIED_PREFLIGHT')
        self.assertIsNone(record['live_binding'])
        self.assertEqual(record['phase_b'], 'NOT_RUN')
        self.assertEqual(len(record['effective_gate_statuses']), 39)
        self.assertEqual(record['effective_gate_statuses']['CLD-ADM-PROVIDER-001'], 'OPEN')
        self.assertEqual(record['scope'], 'BOUNDED_EIGHT_CLIP_LIVE_EVALUATION')
        self.assertEqual(record['dataset']['composition']['en'], {'READ': 4, 'SPONTANEOUS': 0})

    def test_md_is_exact_machine_record_rendering_and_retains_superseded_broad_requirements(self):
        record = phase.build_draft(self.repo)
        markdown = phase.render(record)
        self.assertEqual(phase.parse_rendered(markdown), record)
        changes = {r['original_pointer']: r for r in record['prospective_supersessions']}
        self.assertIn('/protocol/quality/required_slices', changes)
        self.assertIn('/protocol/timestamps/coverage', changes)
        self.assertIn('/execution_envelope/preflight/8', changes)
        self.assertTrue(all(r['broad_requirement_disposition'] == 'UNCHANGED_AND_UNSATISFIED_BY_BOUNDED8'
                            for r in changes.values()))
        self.assertEqual(record['metrics']['timestamp_accuracy'], 'NOT_EVALUATED')
        self.assertEqual(record['metrics']['threshold_percent'], {'ru': 20, 'en': 18})

    def test_preflight_rejects_unverified_controls_stale_bindings_missing_evidence_and_prior_speech(self):
        binding = {key: 'a'*64 for key in phase.BINDING_HASH_KEYS}
        proof = {'status': 'BOUNDED8_PREFLIGHT_PASS', 'checks': {f'PREFLIGHT-{i:02}': 'PASS' for i in range(1,13)},
            'internal_alpha_checks': {f'OD-11C-26-{i:02}': 'PASS' for i in range(1,13)},
            'bounded8_data_authority': 'PASS', 'broad_dataset_coverage': 'NOT_SATISFIED',
            'aws_speech_calls': 0, 'expires_at': '2999-01-01T00:00:00+00:00',
            'bindings': binding, 'evidence_sha256': {k:'b'*64 for k in phase.EVIDENCE_LABELS}}
        phase.validate_preflight(proof, binding, proof['evidence_sha256'])
        for mutation in ('blocked', 'speech', 'binding', 'evidence', 'expired', 'alpha_missing'):
            bad = copy.deepcopy(proof)
            if mutation == 'blocked': bad['checks']['PREFLIGHT-05'] = 'BLOCKED'
            if mutation == 'speech': bad['aws_speech_calls'] = 1
            if mutation == 'binding': bad['bindings']['config_sha256'] = 'c'*64
            if mutation == 'evidence': del bad['evidence_sha256']['cleanup_readback']
            if mutation == 'expired': bad['expires_at'] = '2000-01-01T00:00:00+00:00'
            if mutation == 'alpha_missing': del bad['internal_alpha_checks']
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                phase.validate_preflight(bad, binding, proof['evidence_sha256'])

    def test_owner_notice_is_delivered_and_current_contract_snapshots_bound(self):
        receipt = {'notice_sha256':'a'*64, 'delivered_at':'2026-01-01T00:00:00+00:00',
            'explicit_authorization':'Existing explicit owner instruction, privately recorded',
            'rights_and_credential_loss_route':'Verified private route',
            'service_terms_sha256':'b'*64, 'customer_agreement_sha256':'c'*64}
        phase.validate_owner_authority(receipt)
        for field in receipt:
            bad = dict(receipt); del bad[field]
            with self.subTest(field=field), self.assertRaises(ValueError):
                phase.validate_owner_authority(bad)

    def test_public_view_does_not_copy_private_account_paths_or_raw_proof_into_document(self):
        draft = phase.build_draft(self.repo)
        private = {'account_id':'123456789012', 'owner_principal_arn':'arn:aws:iam::123456789012:role/Secret',
                   'outputs':{'InputBucket':'private-bucket'}, 'client_version':'aws-cli/2.99.0'}
        public = phase.public_environment(private)
        text = json.dumps(public)
        for sensitive in ('123456789012', 'Secret', 'private-bucket'):
            self.assertNotIn(sensitive, text)
        self.assertEqual(public['region'], 'eu-central-1')
        self.assertNotIn('raw_results', draft)

    def test_finalize_binds_exact_private_files_and_never_leaks_private_plan_fields(self):
        from tools.cloud62d_aws.aws_prepare import new_config
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder)
            config = new_config('123456789012', 'arn:aws:iam::123456789012:role/Owner', '1234abcd')
            config.update(template_sha256='a'*64, write_deadline='2999-01-01T00:00:00+00:00',
                          client_version='aws-cli/2.99.0 Python/3.13 Windows/11', outputs={})
            for kind in ('Input', 'Output'):
                config['outputs'][kind+'Bucket'] = f'dora-62d-123456789012-1234abcd-{kind.lower()}'
                config['outputs'][kind+'Key'] = 'arn:aws:kms:eu-central-1:123456789012:key/' + 'a'*36
            for kind, suffix in (('OperatorRole', 'operator'), ('DataRole', 'data')):
                config['outputs'][kind] = 'arn:aws:iam::123456789012:role/dora-62d-1234abcd-' + suffix
            plan = {'pricing': {'usd_per_second':'0.0001', 'minimum_billable_seconds':0,
                'source_url':'https://pricing.us-east-1.amazonaws.com/test', 'verified_at':'2026-09-28'},
                'cases':[{'primary':True, 'duration_us':20_000_000, 'secret_reference':'PRIVATE_WORDS',
                          'source_sha256':'e'*64} for _ in range(8)],
                'budget':{'reserved_seconds':160, 'asr_upper_usd':'0.016', 'ancillary_tax_upper_usd':'8', 'total_upper_usd':'8.016'},
                'serial':True, 'automatic_retries':0, 'poll_seconds':2, 'job_timeout_seconds':1800,
                'maximum_api_calls':20000, 'maximum_result_bytes':5_000_000, 'maximum_start_dispatches':20,
                'maximum_upload_bytes':65*1024**2, 'maximum_download_bytes':100*1024**2}
            paths = {}
            for name, value in (('config',config), ('plan',plan), ('aws','synthetic binary'), ('aws_config','private config')):
                paths[name] = root / (name + '.json')
                paths[name].write_bytes(phase.json_bytes(value))
            binding = {'manifest_sha256':phase.MANIFEST,
                'operator_sha256':phase.normalized_sha(self.repo / 'tools/cloud62d_aws/live_run.py')}
            for name, field in (('config','config_sha256'), ('plan','plan_sha256'), ('aws','aws_client_sha256'), ('aws_config','aws_config_sha256')):
                binding[field] = phase.sha(paths[name].read_bytes())
            evidence_paths = {}
            for label in phase.EVIDENCE_LABELS:
                evidence_paths[label] = root / (label + '.json')
                evidence_paths[label].write_text('SYNTHETIC_PROOF_ONLY', encoding='utf-8')
            evidence_paths['owner_authority'].write_bytes(phase.json_bytes({
                'notice_sha256':'a'*64, 'delivered_at':'2026-01-01T00:00:00+00:00',
                'explicit_authorization':'Existing explicit owner instruction, privately recorded',
                'rights_and_credential_loss_route':'Verified private route',
                'service_terms_sha256':'b'*64, 'customer_agreement_sha256':'c'*64}))
            proof = {'status':'BOUNDED8_PREFLIGHT_PASS', 'checks':{f'PREFLIGHT-{i:02}':'PASS' for i in range(1,13)},
                'internal_alpha_checks':{f'OD-11C-26-{i:02}':'PASS' for i in range(1,13)},
                'bounded8_data_authority':'PASS', 'broad_dataset_coverage':'NOT_SATISFIED', 'aws_speech_calls':0,
                'expires_at':'2999-01-01T00:00:00+00:00', 'bindings':binding,
                'evidence_sha256':{k:phase.sha(v.read_bytes()) for k,v in evidence_paths.items()}}
            preflight_path = root/'proof.json'; preflight_path.write_bytes(phase.json_bytes(proof))
            with patch.object(phase, 'CorpusStore') as fake_store, patch.object(phase.live_run, 'build_plan', return_value=plan):
                fake_store.return_value.validate_manifest.return_value = {'manifest_sha256':phase.MANIFEST}
                fake_store.return_value._path.return_value = root/'absent-provider-marker'
                kwargs = dict(repo=self.repo, corpus_root=root/'corpus', config_path=paths['config'],
                    plan_path=paths['plan'], preflight_path=preflight_path, aws_path=paths['aws'],
                    aws_config_path=paths['aws_config'], evidence_paths=evidence_paths)
                bundle = phase.finalize_bundle(**kwargs)
                record = bundle['record']
                self.assertEqual(record['phase_a'], 'BOUNDED8_PREFLIGHT_VERIFIED_AWAITING_PUBLICATION')
                self.assertEqual(record['publication_barrier']['status'], 'NOT_PUBLISHED')
                self.assertEqual(record['phase_b'], 'NOT_RUN')
                self.assertEqual(record['live_binding']['config_sha256'], binding['config_sha256'])
                self.assertEqual(phase.parse_rendered(bundle['markdown']), record)
                for private in ('123456789012', 'PRIVATE_WORDS', 'e'*64, str(root)):
                    self.assertNotIn(private, bundle['markdown'])
                evidence_paths['cleanup_readback'].write_text('CHANGED', encoding='utf-8')
                with self.assertRaisesRegex(ValueError, 'PREFLIGHT_PROOF_BINDING'):
                    phase.finalize_bundle(**kwargs)
                evidence_paths['cleanup_readback'].write_text('SYNTHETIC_PROOF_ONLY', encoding='utf-8')
                changed = copy.deepcopy(plan); changed['cases'].pop()
                paths['plan'].write_bytes(phase.json_bytes(changed))
                with self.assertRaisesRegex(ValueError, 'FROZEN_PLAN_CHANGED'):
                    phase.finalize_bundle(**kwargs)


if __name__ == '__main__':
    unittest.main()
