import copy
import json
import unittest

from botocore.exceptions import ClientError

from dora_cloud.preflight import verify_live
from dora_cloud.aws_adapter import ProviderError
from test_aws_adapter import config


class ReadOnlyAWS:
    def __init__(self):
        self.cfg = config()
        self.fault = None
        self.calls = []

    def __getattr__(self, name):
        if not name.startswith(("get_", "describe_")):
            raise AssertionError("preflight must only read AWS")
        def call(**kwargs):
            self.calls.append((name, kwargs))
            output = kwargs.get("Bucket") == self.cfg.output_bucket
            key = self.cfg.output_kms_arn if output else self.cfg.input_kms_arn
            if name == "get_caller_identity":
                return {"Account": "000000000000" if self.fault == "account" else self.cfg.account_id}
            if name == "describe_effective_policy":
                return {"EffectivePolicy": {"TargetId": self.cfg.account_id, "PolicyContent": json.dumps(
                    {"services": {"default": {"opt_out_policy": "optOut"}, "transcribe": {
                        "opt_out_policy": "optIn" if self.fault == "optout" else "optOut"}}})}}
            if name == "get_bucket_location":
                return {"LocationConstraint": "us-east-1" if self.fault == "region" else "eu-central-1"}
            if name == "get_public_access_block":
                return {"PublicAccessBlockConfiguration": {k: self.fault != "public" for k in
                    ("BlockPublicAcls", "IgnorePublicAcls", "BlockPublicPolicy", "RestrictPublicBuckets")}}
            if name == "get_bucket_policy_status":
                return {"PolicyStatus": {"IsPublic": self.fault == "public_policy"}}
            if name == "get_bucket_policy":
                return {"Policy": json.dumps({"Statement": [{"Effect": "Deny", "Principal": "*", "Action": "s3:*",
                    "Resource": [f"arn:aws:s3:::{kwargs['Bucket']}", f"arn:aws:s3:::{kwargs['Bucket']}/*"],
                    "Condition": {"Bool": {"aws:SecureTransport": "false" if self.fault != "tls" else "true"}}}]})}
            if name == "get_bucket_ownership_controls":
                return {"OwnershipControls": {"Rules": [{"ObjectOwnership": "BucketOwnerEnforced"}]}}
            if name == "get_bucket_versioning":
                return {"Status": "Enabled"} if self.fault == "versioning" else {}
            if name == "get_bucket_replication":
                if self.fault == "replication":
                    return {"ReplicationConfiguration": {"Rules": [{}]}}
                raise ClientError({"Error": {"Code": "ReplicationConfigurationNotFoundError"}}, name)
            if name == "get_object_lock_configuration":
                if self.fault == "object_lock":
                    return {"ObjectLockConfiguration": {"ObjectLockEnabled": "Enabled"}}
                raise ClientError({"Error": {"Code": "ObjectLockConfigurationNotFoundError"}}, name)
            if name == "get_bucket_encryption":
                return {"ServerSideEncryptionConfiguration": {"Rules": [{"ApplyServerSideEncryptionByDefault": {
                    "SSEAlgorithm": "aws:kms", "KMSMasterKeyID": "wrong" if self.fault == "encryption" else key}}]}}
            if name == "get_bucket_lifecycle_configuration":
                return {"Rules": [{"Status": "Enabled", "Filter": {"Prefix": ""}, "Expiration": {
                    "Days": 7 if self.fault == "retention" else 1}, "AbortIncompleteMultipartUpload": {"DaysAfterInitiation": 1}}]}
            if name == "describe_key":
                return {"KeyMetadata": {"Arn": kwargs["KeyId"], "AWSAccountId": self.cfg.account_id,
                    "KeyState": "Enabled", "KeyManager": "CUSTOMER", "KeySpec": "SYMMETRIC_DEFAULT",
                    "KeyUsage": "ENCRYPT_DECRYPT", "MultiRegion": self.fault == "multiregion"}}
            if name == "get_key_rotation_status":
                return {"KeyRotationEnabled": self.fault != "rotation"}
            raise AssertionError(name)
        return call


class PreflightTests(unittest.TestCase):
    def test_current_account_policy_private_buckets_and_cmk_are_required(self):
        for fault in (None, "account", "optout", "region", "public", "public_policy", "tls",
                      "versioning", "replication", "object_lock", "encryption", "retention", "multiregion", "rotation"):
            aws = ReadOnlyAWS()
            aws.fault = fault
            with self.subTest(fault=fault):
                if fault:
                    with self.assertRaises(ProviderError):
                        verify_live(config(), aws, aws, aws, aws)
                else:
                    evidence = verify_live(config(), aws, aws, aws, aws)
                    self.assertEqual("VERIFIED", evidence["status"])
                    self.assertGreater(len(aws.calls), 20)
                for name, kwargs in aws.calls:
                    if "Bucket" in kwargs:
                        self.assertEqual("123456789012", kwargs["ExpectedBucketOwner"])

    def test_disabled_config_never_contacts_aws(self):
        aws = ReadOnlyAWS()
        with self.assertRaises(ProviderError):
            verify_live(config(live_enabled=False), aws, aws, aws, aws)
        self.assertEqual([], aws.calls)

    def test_missing_config_and_wrong_region_fail_locally(self):
        from dora_cloud.config import LiveConfig
        for values in ({}, {"region": "us-east-1"}):
            with self.assertRaises(ValueError):
                LiveConfig(**values)
        with self.assertRaises(ValueError):
            config(output_kms_arn=config().input_kms_arn)
        with self.assertRaises(ValueError):
            config(input_bucket=config().output_bucket)


if __name__ == "__main__":
    unittest.main()
