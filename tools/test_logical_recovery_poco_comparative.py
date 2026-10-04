"""Run the comparative parser's tests in the existing logical-recovery host CI selection."""
from poco_comparative_battery.test_comparative_parser import ParserTests
import copy
import json
from pathlib import Path
import unittest
from validate_poco_acceptance import validate_comparative_disposition

__all__ = ["ParserTests"]


class ComparativeAdmissionTests(unittest.TestCase):
    def setUp(self):
        root = Path(__file__).resolve().parents[1] / 'docs/evidence/poco-comparative-battery-8.6'
        self.receipt = json.loads((root / 'result.json').read_text())
        self.gate = json.loads((root / 'comparative-gate-disposition.json').read_text())

    def test_partial_disposition_is_accepted(self):
        validate_comparative_disposition(self.receipt, self.gate)

    def test_pass_or_invented_source_is_rejected(self):
        for target, key, value in [('receipt', 'verdict', 'PASS'), ('receipt', 'primarySource', 'battery_percent'), ('gate', 'acceptanceFormula', '4*c<=5*b')]:
            receipt, gate = copy.deepcopy(self.receipt), copy.deepcopy(self.gate)
            (receipt if target == 'receipt' else gate)[key] = value
            with self.assertRaises(ValueError):
                validate_comparative_disposition(receipt, gate)

    def test_fake_energy_and_unapproved_long_campaign_rejected(self):
        for key in ('batteryPercentConverted', 'currentSnapshotsIntegrated', 'hourCampaignExecuted', 'cycles200Executed', 'ratiosComputed'):
            with self.subTest(key=key), self.assertRaises(ValueError):
                validate_comparative_disposition({**self.receipt, key: True}, self.gate)

    def test_sheet_or_group_d_progress_rejected(self):
        for key, value in [('sheet', 'PASS'), ('groupD', 'IN_PROGRESS')]:
            with self.subTest(key=key), self.assertRaises(ValueError):
                validate_comparative_disposition({**self.receipt, key: value}, self.gate)
