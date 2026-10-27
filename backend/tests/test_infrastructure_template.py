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

    def test_host_has_no_public_api_port_or_automatic_bootstrap_and_alarm_fails_closed(self):
        props = self.resources["Host"]["Properties"]
        self.assertNotIn("UserData", props)
        self.assertEqual("required", props["MetadataOptions"]["HttpTokens"])
        self.assertEqual("standard", props["CreditSpecification"]["CPUCredits"])
        self.assertTrue(props["DisableApiTermination"])
        ingress = self.resources["HostFirewall"]["Properties"]["SecurityGroupIngress"]
        self.assertEqual({22, 443}, {r["ToPort"] for r in ingress})
        self.assertTrue(all(r["CidrIp"] == {"Ref": "OwnerCidr"} for r in ingress))
        self.assertTrue(self.resources["DataVolume"]["Properties"]["Encrypted"])
        self.assertEqual("Retain", self.resources["DataVolume"]["DeletionPolicy"])
        self.assertEqual("breaching", self.resources["CleanupHealthAlarm"]["Properties"]["TreatMissingData"])
        self.assertNotIn("CostFilters", self.resources["MonthlyBudget"]["Properties"]["Budget"])


if __name__ == "__main__":
    unittest.main()
