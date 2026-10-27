"""Read-only admission checks. Never provisions or mutates cloud resources."""
import json
import time

from botocore.exceptions import BotoCoreError, ClientError

from .aws_adapter import ProviderError


def require(condition):
    if not condition:
        raise ProviderError("LIVE_PREFLIGHT_FAILED")


def whole_bucket(rule):
    # S3 accepts modern empty Filter and legacy empty Prefix representations.
    # Never infer whole-bucket coverage from missing or conflicting fields.
    if "Filter" in rule:
        return "Prefix" not in rule and rule["Filter"] in ({}, {"Prefix": ""})
    return rule.get("Prefix") == ""


def verify_live(config, sts, organizations, s3, kms):
    if not config.live_enabled:
        raise ProviderError("LIVE_DISABLED")
    try:
        require(sts.get_caller_identity()["Account"] == config.account_id)
        effective = organizations.describe_effective_policy(PolicyType="AISERVICES_OPT_OUT_POLICY", TargetId=config.account_id)["EffectivePolicy"]
        require(effective["TargetId"] == config.account_id)
        services = json.loads(effective["PolicyContent"])["services"]
        require(services.get("transcribe", services.get("default", {})).get("opt_out_policy") == "optOut")
        for bucket, key in ((config.input_bucket, config.input_kms_arn), (config.output_bucket, config.output_kms_arn)):
            args = {"Bucket": bucket, "ExpectedBucketOwner": config.account_id}
            require(s3.get_bucket_location(**args)["LocationConstraint"] == "eu-central-1")
            public = s3.get_public_access_block(**args)["PublicAccessBlockConfiguration"]
            require(all(public.get(k) is True for k in ("BlockPublicAcls", "IgnorePublicAcls", "BlockPublicPolicy", "RestrictPublicBuckets")))
            require(s3.get_bucket_policy_status(**args)["PolicyStatus"]["IsPublic"] is False)
            policy = json.loads(s3.get_bucket_policy(**args)["Policy"])
            require(any(s.get("Effect") == "Deny" and s.get("Principal") == "*" and s.get("Action") == "s3:*" and
                        set(s.get("Resource", [])) >= {f"arn:aws:s3:::{bucket}", f"arn:aws:s3:::{bucket}/*"} and
                        s.get("Condition") == {"Bool": {"aws:SecureTransport": "false"}} for s in policy["Statement"]))
            ownership = s3.get_bucket_ownership_controls(**args)["OwnershipControls"]["Rules"]
            require(ownership == [{"ObjectOwnership": "BucketOwnerEnforced"}])
            require("Status" not in s3.get_bucket_versioning(**args))
            for operation, absent in ((s3.get_bucket_replication, "ReplicationConfigurationNotFoundError"),
                                      (s3.get_object_lock_configuration, "ObjectLockConfigurationNotFoundError")):
                try:
                    operation(**args)
                except ClientError as exc:
                    require(exc.response.get("Error", {}).get("Code") == absent)
                else:
                    raise ProviderError("LIVE_PREFLIGHT_FAILED")
            rules = s3.get_bucket_encryption(**args)["ServerSideEncryptionConfiguration"]["Rules"]
            require(len(rules) == 1 and rules[0]["ApplyServerSideEncryptionByDefault"] == {"SSEAlgorithm": "aws:kms", "KMSMasterKeyID": key})
            rules = s3.get_bucket_lifecycle_configuration(**args)["Rules"]
            require(any(r.get("Status") == "Enabled" and whole_bucket(r) and
                        r.get("Expiration") == {"Days": 1} and r.get("AbortIncompleteMultipartUpload") == {"DaysAfterInitiation": 1}
                        and not any(k in r for k in ("Transitions", "NoncurrentVersionTransitions")) for r in rules))
            require(not any(r.get("Status") == "Enabled" and (r.get("Transitions") or r.get("NoncurrentVersionTransitions")) for r in rules))
            metadata = kms.describe_key(KeyId=key)["KeyMetadata"]
            require(all(metadata.get(k) == v for k, v in {"Arn": key, "AWSAccountId": config.account_id,
                "KeyState": "Enabled", "KeyManager": "CUSTOMER", "KeySpec": "SYMMETRIC_DEFAULT", "KeyUsage": "ENCRYPT_DECRYPT", "MultiRegion": False}.items()))
            require(kms.get_key_rotation_status(KeyId=key)["KeyRotationEnabled"] is True)
        return {"status": "VERIFIED", "checked_at": int(time.time()), "account_id": config.account_id, "region": config.region}
    except ProviderError:
        raise
    except (BotoCoreError, ClientError, KeyError, TypeError, ValueError):
        raise ProviderError("LIVE_PREFLIGHT_FAILED") from None
