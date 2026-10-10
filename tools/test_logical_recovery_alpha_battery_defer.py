import copy
import json
from pathlib import Path
import unittest
import poco_alpha_battery_defer as waiver


class AlphaBatteryDeferralTest(unittest.TestCase):
    def setUp(self):
        self.record = json.loads((Path(__file__).resolve().parents[1] / 'docs/governance/poco-battery-alpha-deferral.json').read_text(encoding='utf-8'))

    def test_current_owner_scope(self):
        waiver.validate(self.record)

    def test_no_false_pass(self):
        for key in ('stage86AlphaStatus', 'batteryRepeatability', 'comparativeSourceAdmission',
                    'hardwareMicroWhGate', 'doraBaselineRatio', 'batteryEfficiencyGate'):
            with self.subTest(key=key), self.assertRaises(ValueError):
                waiver.validate({**self.record, key: 'PASS'})

    def test_no_scope_expansion(self):
        for key in ('stage86Closed', 'fullAlphaClosed', 'energyPassed', 'runtimeChanged',
                    'sixBatteryRepeatsExecuted', 'oneHourBatteryCampaignExecuted',
                    'cycles200Executed', 'hardwareEnergyInvented', 'historicalEvidenceRewritten'):
            with self.subTest(key=key), self.assertRaises(ValueError):
                waiver.validate({**self.record, key: True})

    def test_other_blockers_preserved(self):
        for key in ('securityRestorationBlocker', 'sheet', 'groupD', 'cloudAsrImplementation'):
            with self.subTest(key=key), self.assertRaises(ValueError):
                waiver.validate({**self.record, key: 'PASS'})

    def test_missing_gaps_rejected(self):
        with self.assertRaises(ValueError):
            waiver.validate({**self.record, 'remainingGateGroups': []})

    def test_exact_additive_prefix(self):
        for path in waiver.DOCUMENTS:
            self.assertEqual(waiver.normalize_document(path, waiver.PREFIX.encode() + b'old bytes\n'), b'old bytes\n')
            with self.assertRaises(ValueError):
                waiver.normalize_document(path, waiver.PREFIX.replace('NOT_READY', 'PASS').encode() + b'old bytes\n')

    def test_no_unlisted_override(self):
        with self.assertRaises(ValueError):
            waiver.normalize_document('android/app/src/main/Anything.kt', waiver.PREFIX.encode())

    def test_no_automatic_battery_resumption(self):
        with self.assertRaises(ValueError):
            waiver.validate({**self.record, 'newOwnerDecisionRequiredToResume': False})
