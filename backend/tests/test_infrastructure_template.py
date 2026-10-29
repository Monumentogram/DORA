"""Guard the IaC boundaries that CloudFormation schema validation cannot enforce."""
import json
import unittest
from pathlib import Path

BASE = Path(__file__).resolve().parents[1] / "deployment"


class InfrastructureTests(unittest.TestCase):
    def setUp(self):
        self.stack = json.loads((BASE / "alpha/stack.json").read_text())
        self.resources = self.stack["Resources"]

    def test_start_permissions_match_adapter_without_uncontrolled_output_or_passrole(self):
        policy = json.loads((BASE / "runtime-policy.template.json").read_text())
        start = [s for s in policy["Statement"] if s["Action"] == "transcribe:StartTranscriptionJob"]
        self.assertEqual(1, len(start))
        self.assertEqual("*", start[0]["Resource"])
        self.assertEqual({"aws:RequestedRegion": "eu-central-1", "transcribe:OutputBucketName": "OUTPUT_BUCKET",
                          "transcribe:OutputEncryptionKMSKeyId": "OUTPUT_CMK_ARN"}, start[0]["Condition"]["StringEquals"])
        self.assertEqual({"transcribe:OutputKey": "output/dora-*.json"}, start[0]["Condition"]["StringLike"])
        role = self.resources["RuntimeRole"]["Properties"]
        bound_start = role["Policies"][0]["PolicyDocument"]["Statement"][0]
        self.assertEqual("*", bound_start["Resource"])
        self.assertEqual({"Fn::Sub": "${OutputKey.Arn}"}, bound_start["Condition"]["StringEquals"]["transcribe:OutputEncryptionKMSKeyId"])
        self.assertNotIn("iam:", json.dumps(role["Policies"]))
        self.assertNotIn("kms:*", json.dumps(role["Policies"]))
        self.assertEqual({"Service": "ec2.amazonaws.com"}, role["AssumeRolePolicyDocument"]["Statement"][0]["Principal"])

    def test_custody_retention_and_rollback_resources_remain_separate(self):
        for side in ("Input", "Output"):
            bucket = self.resources[side + "Bucket"]
            props = bucket["Properties"]
            self.assertEqual("Retain", bucket["DeletionPolicy"])
            self.assertTrue(all(props["PublicAccessBlockConfiguration"].values()))
            self.assertNotIn("VersioningConfiguration", props)
            self.assertNotIn("ReplicationConfiguration", props)
            self.assertNotIn("ObjectLockConfiguration", props)
            self.assertEqual({"Fn::GetAtt": [side + "Key", "Arn"]}, props["BucketEncryption"]["ServerSideEncryptionConfiguration"][0]["ServerSideEncryptionByDefault"]["KMSMasterKeyID"])
            rule = props["LifecycleConfiguration"]["Rules"][0]
            self.assertEqual("", rule["Prefix"])
            self.assertEqual(1, rule["ExpirationInDays"])
            self.assertEqual(1, rule["AbortIncompleteMultipartUpload"]["DaysAfterInitiation"])
            self.assertTrue(self.resources[side + "Key"]["Properties"]["EnableKeyRotation"])
            self.assertEqual("Retain", self.resources[side + "Key"]["DeletionPolicy"])
            self.assertEqual("Retain", self.resources[side + "BucketPolicy"]["DeletionPolicy"])

    def test_transcribe_write_probe_is_limited_to_one_non_audio_output_key(self):
        template = json.loads((BASE / "runtime-policy.template.json").read_text())
        bound = self.resources["RuntimeRole"]["Properties"]["Policies"][0]["PolicyDocument"]
        for policy, resource in ((template, "arn:aws:s3:::OUTPUT_BUCKET/output/.write_access_check_file.temp"),
                                 (bound, {"Fn::Sub": "arn:${AWS::Partition}:s3:::${OutputBucket}/output/.write_access_check_file.temp"})):
            probe = [s for s in policy["Statement"] if s.get("Sid") == "TranscribeOutputWriteProbe"]
            self.assertEqual(1, len(probe))
            self.assertEqual(resource, probe[0]["Resource"])
            self.assertEqual({"s3:PutObject", "s3:GetObject", "s3:DeleteObject"}, set(probe[0]["Action"]))
            self.assertIn("s3:ResourceAccount", probe[0]["Condition"]["StringEquals"])

    def test_public_owner_https_is_opt_in_and_cannot_broaden_ssh_or_egress(self):
        parameter = self.stack["Parameters"].get("EnablePublicOwnerHttps")
        self.assertIsNotNone(parameter, "Public owner HTTPS must have an explicit opt-in")
        self.assertEqual("false", parameter["Default"])
        self.assertEqual({"false", "true"}, set(parameter["AllowedValues"]))
        firewall = self.resources["HostFirewall"]["Properties"]
        for enabled in ("false", "true"):
            for device in ("", "192.0.2.2/32"):
                with self.subTest(enabled=enabled, device=device):
                    values = {"OwnerCidr": "192.0.2.1/32", "OwnerDeviceCidr": device,
                              "EnableAcmeHttpChallenge": "false", "EnablePublicOwnerHttps": enabled,
                              "AWS::NoValue": None}

                    def resolve(value):
                        if isinstance(value, list):
                            return [resolved for item in value if (resolved := resolve(item)) is not None]
                        if not isinstance(value, dict):
                            return value
                        if "Ref" in value:
                            return values[value["Ref"]]
                        if "Fn::Equals" in value:
                            left, right = value["Fn::Equals"]
                            return resolve(left) == resolve(right)
                        if "Fn::Not" in value:
                            return not resolve(value["Fn::Not"][0])
                        if "Fn::If" in value:
                            condition, yes, no = value["Fn::If"]
                            return resolve(yes if resolve(self.stack["Conditions"][condition]) else no)
                        return {key: resolve(item) for key, item in value.items()}

                    actual = {(r["IpProtocol"], r["FromPort"], r["ToPort"], r["CidrIp"])
                              for r in resolve(firewall["SecurityGroupIngress"])}
                    expected = {("tcp", 22, 22, "192.0.2.1/32"), ("tcp", 443, 443, "192.0.2.1/32")}
                    if device:
                        expected.add(("tcp", 443, 443, "192.0.2.2/32"))
                    if enabled == "true":
                        expected.add(("tcp", 443, 443, "0.0.0.0/0"))
                    self.assertEqual(expected, actual)
                    self.assertEqual([{"IpProtocol": "tcp", "FromPort": 443, "ToPort": 443,
                                       "CidrIp": "0.0.0.0/0"}], resolve(firewall["SecurityGroupEgress"]))

    def test_host_has_no_public_api_port_or_automatic_bootstrap_and_alarm_fails_closed(self):
        props = self.resources["Host"]["Properties"]
        self.assertNotIn("UserData", props)
        self.assertEqual("required", props["MetadataOptions"]["HttpTokens"])
        self.assertEqual("standard", props["CreditSpecification"]["CPUCredits"])
        self.assertTrue(props["DisableApiTermination"])
        rules = self.resources["HostFirewall"]["Properties"]["SecurityGroupIngress"]
        ingress = [r for r in rules if "Fn::If" not in r]
        self.assertEqual({22, 443}, {r["ToPort"] for r in ingress})
        self.assertTrue(all(r["CidrIp"] == {"Ref": "OwnerCidr"} for r in ingress))
        self.assertEqual("false", self.stack["Parameters"]["EnableAcmeHttpChallenge"]["Default"])
        optional = [r["Fn::If"] for r in rules if "Fn::If" in r]
        self.assertEqual(3, len(optional))
        condition, challenge, disabled = next(r for r in optional if r[0] == "AcmeHttpChallengeEnabled")
        self.assertEqual("AcmeHttpChallengeEnabled", condition)
        self.assertEqual({"Fn::Equals": [{"Ref": "EnableAcmeHttpChallenge"}, "true"]}, self.stack["Conditions"][condition])
        self.assertEqual((80, 80, "tcp", "0.0.0.0/0"),
                         (challenge["FromPort"], challenge["ToPort"], challenge["IpProtocol"], challenge["CidrIp"]))
        self.assertEqual({"Ref": "AWS::NoValue"}, disabled)
        device = next(r for r in optional if r[0] == "OwnerDeviceEnabled")
        self.assertEqual("", self.stack["Parameters"]["OwnerDeviceCidr"]["Default"])
        pattern = self.stack["Parameters"]["OwnerDeviceCidr"]["AllowedPattern"]
        import re
        self.assertIsNotNone(re.fullmatch(pattern, "192.0.2.3/32"))
        for value in ("0.0.0.0/0", "192.0.2.0/24", "::/0"):
            self.assertIsNone(re.fullmatch(pattern, value))
        self.assertEqual({"Fn::Not": [{"Fn::Equals": [{"Ref": "OwnerDeviceCidr"}, ""]}]},
                         self.stack["Conditions"]["OwnerDeviceEnabled"])
        self.assertEqual({"IpProtocol": "tcp", "FromPort": 443, "ToPort": 443,
                          "CidrIp": {"Ref": "OwnerDeviceCidr"}}, device[1])
        self.assertEqual({"Ref": "AWS::NoValue"}, device[2])
        self.assertTrue(self.resources["DataVolume"]["Properties"]["Encrypted"])
        self.assertEqual("Retain", self.resources["DataVolume"]["DeletionPolicy"])
        self.assertEqual("breaching", self.resources["CleanupHealthAlarm"]["Properties"]["TreatMissingData"])
        self.assertNotIn("CostFilters", self.resources["MonthlyBudget"]["Properties"]["Budget"])


if __name__ == "__main__":
    unittest.main()
