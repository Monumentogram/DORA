"""Verify retained typed evidence against the immutable common error contract.

No new mapper, AWS calls, transcription or quality scoring. Generic BadRequest
does not imply missing input or access denial without the attached adjudication.
"""
import hashlib
import json
from pathlib import Path
import sys
import types
import unittest

ROOT = Path(__file__).resolve().parents[1]
SOURCE_SHA = '6e99e08561534cde5b6bc69a40fd3e8986d763c00456e7fdb32bf3346cceabd1'
EVIDENCE_SHA = 'c3d667c604e4ee12652cb314bacf4d780006e9f6ce5dd7e0c04b38556329b770'


class ErrorAdjudicationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        source = (ROOT / 'docs/stage0/DORA_CLOUD_EVALUATION_HARNESS_V0_1.md').read_text(
            encoding='utf-8').split('```python\n', 1)[1].split('```', 1)[0]
        if hashlib.sha256(source.encode()).hexdigest() != SOURCE_SHA:
            raise AssertionError('frozen provider contract changed')
        cls.contract = types.ModuleType('cloud62d_frozen_error_contract')
        sys.modules[cls.contract.__name__] = cls.contract
        exec(compile(source, 'frozen-cloud62d-harness', 'exec'), cls.contract.__dict__)
        raw = (ROOT / 'docs/evidence/cloud-6.2d-owner-technical-result-v0.1.json').read_text(
            encoding='utf-8').encode()
        if hashlib.sha256(raw).hexdigest() != EVIDENCE_SHA:
            raise AssertionError('retained technical evidence changed')
        cls.evidence = json.loads(raw)

    def test_retained_missing_input_adjudication_is_non_retryable_source_error(self):
        adjudication = self.evidence['independent_review_findings']['tech-missing_s3']
        self.assertEqual(adjudication['typed_outcome'], 'SUPPORTED_MISSING_INPUT_REJECTION')
        self.assertEqual(adjudication['raw_oracle_verdict_unchanged'], 'INCOMPLETE')
        self.assertEqual(self.contract.error_map('BadRequestException', 400,
                                                proven_context='missing_input'),
                         {'category': 'missing_input', 'retryable': False})

    def test_retained_denial_adjudication_is_non_retryable_access_error(self):
        adjudication = self.evidence['independent_review_findings']['tech-permission_denial']
        self.assertEqual(adjudication['typed_outcome'], 'SUPPORTED_ISOLATED_ACCESS_DENIAL')
        self.assertEqual(adjudication['raw_oracle_verdict_unchanged'], 'INCOMPLETE')
        self.assertEqual(self.contract.error_map('BadRequestException', 400,
                                                proven_context='forbidden'),
                         {'category': 'forbidden', 'retryable': False})

    def test_ambiguous_bad_request_is_not_guessed(self):
        self.assertEqual(self.contract.error_map('BadRequestException', 400),
                         {'category': 'invalid_request', 'retryable': False})

    def test_unambiguous_provider_errors_need_no_adjudication(self):
        for code, category in (('NoSuchKey', 'missing_input'),
                               ('AccessDeniedException', 'forbidden'),
                               ('ExpiredTokenException', 'unauthorized')):
            self.assertEqual(self.contract.error_map(code, 400),
                             {'category': category, 'retryable': False})


if __name__ == '__main__':
    unittest.main()
