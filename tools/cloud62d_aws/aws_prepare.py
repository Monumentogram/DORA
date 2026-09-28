"""Private, bounded AWS preparation. No upload or StartTranscriptionJob implementation.

AWS replies and actual account identifiers are written only outside Git. Network reads
are explicit commands; template/tests are offline. Account governance is never changed.
"""
import argparse
from datetime import datetime, timedelta, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys

REGION = "eu-central-1"
PROFILE = "dora-62d-alpha"
HERE = Path(__file__).resolve().parent
REQS = ["Test account and short-lived credentials", "No permanent credentials in Android",
        "Exact eu-central-1", "No automatic fallback", "Effective Transcribe opt-out",
        "Private bounded S3", "OD-11C-13..22 retention", "ADR-0011 KMS/encryption",
        "Individual dataset admission", "Budget envelope", "Cleanup plan",
        "Only owner-authorized test audio; no customer/third-party audio"]


def require(ok, message):
    if not ok:
        raise ValueError(message)


def private_path(path):
    p = Path(path).resolve()
    for parent in (p, *p.parents):
        require(not (parent / ".git").exists(), "PRIVATE_PATH_IS_IN_GIT")
    return p


def save_private(path, value):
    p = private_path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    tmp = p.with_suffix(p.suffix + ".tmp")
    tmp.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    tmp.replace(p)


def new_config(account, principal, run_id):
    return {"schema_version": "dora-62d-aws-private-v1", "profile": PROFILE,
            "region": REGION, "account_id": account, "owner_principal_arn": principal,
            "run_id": run_id, "stack_name": "dora-62d-" + run_id,
            "job_prefix": "d62d-" + run_id + "-", "input_prefix": "input/",
            "output_prefix": "output/", "fallback": False}


def validate_config(c):
    require(c.get("region") == REGION and c.get("fallback") is False, "REGION_OR_FALLBACK")
    require(re.fullmatch(r"\d{12}", c.get("account_id", "")), "ACCOUNT_ID")
    require(re.fullmatch(r"[a-f0-9]{8,16}", c.get("run_id", "")), "OPAQUE_RUN_ID")
    require(re.fullmatch(r"arn:aws:iam::" + c["account_id"] + r":role/[A-Za-z0-9_+=,.@/-]+", c.get("owner_principal_arn", "")), "SAME_ACCOUNT_ROLE_REQUIRED")
    require(c.get("profile") == PROFILE, "NAMED_PROFILE_REQUIRED")
    require(c.get("stack_name") == "dora-62d-" + c["run_id"], "STACK_SCOPE")
    require(c.get("job_prefix") == "d62d-" + c["run_id"] + "-", "JOB_SCOPE")
    require(c.get("input_prefix") == "input/" and c.get("output_prefix") == "output/", "PREFIX_SCOPE")
    if "outputs" in c:
        outputs = c["outputs"]
        for kind in ("Input", "Output"):
            require(outputs.get(kind + "Bucket") == f"dora-62d-{c['account_id']}-{c['run_id']}-{kind.lower()}", "FOREIGN_BUCKET")
            require(re.fullmatch(r"arn:aws:kms:eu-central-1:" + c["account_id"] + r":key/[a-f0-9-]{36}", outputs.get(kind + "Key", "")), "FOREIGN_KEY")
        for kind, suffix in (("DataRole", "data"), ("OperatorRole", "operator")):
            require(outputs.get(kind) == f"arn:aws:iam::{c['account_id']}:role/dora-62d-{c['run_id']}-{suffix}", "FOREIGN_ROLE")


def effective_optout(content):
    """Accept ONLY AWS's flattened effective policy, never a draft @@assign policy."""
    services = content.get("services", {})
    if not isinstance(services, dict):
        return False
    policy = services.get("transcribe", services.get("default", {}))
    return isinstance(policy, dict) and policy.get("opt_out_policy") == "optOut"


def owned_job(c, name):
    return name.startswith(c["job_prefix"]) and bool(re.fullmatch(r"[A-Za-z0-9._-]+", name))


def initial_preflight():
    return [{"id": f"PREFLIGHT-{i:02}", "requirement": r, "status": "BLOCKED",
             "evidence": "NOT_VERIFIED"} for i, r in enumerate(REQS, 1)]


def sub(text): return {"Fn::Sub": text}
def ref(name): return {"Ref": name}
def arn(name): return {"Fn::GetAtt": [name, "Arn"]}
def statement(actions, resources, **extra):
    return {"Effect": "Allow", "Action": actions, "Resource": resources, **extra}
def policy(statements): return {"Version": "2012-10-17", "Statement": statements}


def template():
    """Deterministic CloudFormation: two private buckets/keys, three task-owned roles.

    Key-policy Resource '*' denotes ONLY the containing KMS key. Every identity
    permission uses its exact key ARN. No account-wide key administration grant.
    """
    r = {}
    tags = [{"Key": "Project", "Value": "DORA"}, {"Key": "Purpose", "Value": "6.2D-test"},
            {"Key": "Run", "Value": ref("RunId")}]
    for kind, prefix in (("Input", "input/"), ("Output", "output/")):
        r[kind + "Key"] = {"Type": "AWS::KMS::Key", "Properties": {
            "Description": "DORA 6.2D temporary " + kind.lower(), "KeySpec": "SYMMETRIC_DEFAULT",
            "KeyUsage": "ENCRYPT_DECRYPT", "MultiRegion": False, "EnableKeyRotation": True,
            "PendingWindowInDays": 7, "Tags": tags,
            "KeyPolicy": policy([
                statement(["kms:DescribeKey", "kms:GetKeyPolicy", "kms:PutKeyPolicy", "kms:EnableKeyRotation", "kms:GetKeyRotationStatus", "kms:ScheduleKeyDeletion", "kms:CancelKeyDeletion", "kms:EnableKey", "kms:DisableKey", "kms:TagResource", "kms:UntagResource", "kms:ListResourceTags"], "*", Principal={"AWS": ref("OwnerPrincipalArn")}),
                statement(["kms:Decrypt", "kms:Encrypt", "kms:GenerateDataKey", "kms:DescribeKey"], "*", Principal={"AWS": sub("arn:aws:iam::${AWS::AccountId}:root")}, Condition={"ArnEquals": {"aws:PrincipalArn": [sub("arn:aws:iam::${AWS::AccountId}:role/dora-62d-${RunId}-operator"), sub("arn:aws:iam::${AWS::AccountId}:role/dora-62d-${RunId}-data")]}})
            ])}}
        r[kind + "Bucket"] = {"Type": "AWS::S3::Bucket", "Properties": {
            "BucketName": sub("dora-62d-${AWS::AccountId}-${RunId}-" + kind.lower()),
            "PublicAccessBlockConfiguration": {k: True for k in ("BlockPublicAcls", "IgnorePublicAcls", "BlockPublicPolicy", "RestrictPublicBuckets")},
            "OwnershipControls": {"Rules": [{"ObjectOwnership": "BucketOwnerEnforced"}]},
            "BucketEncryption": {"ServerSideEncryptionConfiguration": [{"ServerSideEncryptionByDefault": {"SSEAlgorithm": "aws:kms", "KMSMasterKeyID": arn(kind + "Key")}, "BucketKeyEnabled": False}]},
            "LifecycleConfiguration": {"Rules": [{"Id": "FallbackOnly", "Status": "Enabled", "Prefix": prefix, "ExpirationInDays": 1, "AbortIncompleteMultipartUpload": {"DaysAfterInitiation": 1}}]}, "Tags": tags}}
        all_resources = [arn(kind + "Bucket"), sub("${" + kind + "Bucket.Arn}/*")]
        object_resource = sub("${" + kind + "Bucket.Arn}/*")
        r[kind + "BucketPolicy"] = {"Type": "AWS::S3::BucketPolicy", "Properties": {
            "Bucket": ref(kind + "Bucket"), "PolicyDocument": policy([
                {"Effect": "Deny", "Principal": "*", "Action": ["s3:GetObject", "s3:PutObject", "s3:DeleteObject", "s3:ListBucket"], "Resource": all_resources, "Condition": {"Bool": {"aws:SecureTransport": "false"}}},
                {"Effect": "Deny", "Principal": "*", "Action": "s3:PutObject", "Resource": object_resource, "Condition": {"DateGreaterThanEquals": {"aws:CurrentTime": ref("WriteDeadline")}}},
                {"Effect": "Deny", "Principal": "*", "Action": "s3:PutObject", "Resource": object_resource, "Condition": {"StringNotEqualsIfExists": {"s3:x-amz-server-side-encryption": "aws:kms"}}},
                {"Effect": "Deny", "Principal": "*", "Action": "s3:PutObject", "Resource": object_resource, "Condition": {"StringNotEqualsIfExists": {"s3:x-amz-server-side-encryption-aws-kms-key-id": arn(kind + "Key")}}}
            ])}}
    job_arn = sub("arn:aws:transcribe:eu-central-1:${AWS::AccountId}:transcription-job/d62d-${RunId}-*")
    data_stmts = [statement("s3:GetObject", sub("${InputBucket.Arn}/input/*")),
                  statement("s3:PutObject", sub("${OutputBucket.Arn}/output/*")),
                  statement(["s3:GetBucketLocation"], [arn("InputBucket"), arn("OutputBucket")]),
                  statement(["kms:Decrypt"], arn("InputKey")),
                  statement(["kms:GenerateDataKey", "kms:Encrypt", "kms:Decrypt"], arn("OutputKey"))]
    r["DataRole"] = {"Type": "AWS::IAM::Role", "Properties": {
        "RoleName": sub("dora-62d-${RunId}-data"), "MaxSessionDuration": 3600, "Tags": tags,
        "AssumeRolePolicyDocument": policy([{"Effect": "Allow", "Principal": {"Service": "transcribe.amazonaws.com"}, "Action": "sts:AssumeRole", "Condition": {"StringEquals": {"aws:SourceAccount": ref("AWS::AccountId")}, "ArnLike": {"aws:SourceArn": job_arn}}}]),
        "Policies": [{"PolicyName": "ExactData", "PolicyDocument": policy(data_stmts)}]}}
    cleanup = []
    for kind, prefix in (("Input", "input/"), ("Output", "output/")):
        cleanup.extend([statement(["s3:ListBucket", "s3:ListBucketVersions"], arn(kind + "Bucket"), Condition={"StringLike": {"s3:prefix": [prefix, prefix + "*"]}}),
                        statement("s3:ListBucketMultipartUploads", arn(kind + "Bucket")),
                        statement(["s3:DeleteObject", "s3:DeleteObjectVersion", "s3:AbortMultipartUpload"], sub("${" + kind + "Bucket.Arn}/" + prefix + "*"))])
    cleanup.extend([statement(["transcribe:GetTranscriptionJob", "transcribe:DeleteTranscriptionJob"], job_arn), statement("transcribe:ListTranscriptionJobs", "*")])
    operator = cleanup + data_stmts + [statement(["s3:PutObject"], sub("${InputBucket.Arn}/input/*")),
        statement(["s3:GetObject"], sub("${OutputBucket.Arn}/output/*")),
        statement(["kms:GenerateDataKey", "kms:Encrypt"], arn("InputKey")),
        statement("transcribe:StartTranscriptionJob", job_arn, Condition={"StringEquals": {"aws:RequestedRegion": REGION}, "DateLessThan": {"aws:CurrentTime": ref("WriteDeadline")}}),
        statement("iam:PassRole", arn("DataRole"), Condition={"StringEquals": {"iam:PassedToService": "transcribe.amazonaws.com"}})]
    r["OperatorRole"] = {"Type": "AWS::IAM::Role", "Properties": {
        "RoleName": sub("dora-62d-${RunId}-operator"), "MaxSessionDuration": 3600, "Tags": tags,
        "AssumeRolePolicyDocument": policy([{"Effect": "Allow", "Principal": {"AWS": ref("OwnerPrincipalArn")}, "Action": "sts:AssumeRole"}]),
        "Policies": [{"PolicyName": "BoundedExecution", "PolicyDocument": policy(operator)}]}}
    r["WatchdogRole"] = {"Type": "AWS::IAM::Role", "Properties": {
        "RoleName": sub("dora-62d-${RunId}-watchdog"), "Tags": tags,
        "AssumeRolePolicyDocument": policy([{"Effect": "Allow", "Principal": {"Service": "lambda.amazonaws.com"}, "Action": "sts:AssumeRole"}]),
        "Policies": [{"PolicyName": "BoundedDeleteOnly", "PolicyDocument": policy(cleanup)}]}}
    r["Watchdog"] = {"Type": "AWS::Lambda::Function", "Properties": {
        "FunctionName": sub("dora-62d-${RunId}-watchdog"), "Runtime": "python3.12", "Handler": "index.handler", "MemorySize": 128,
        "Timeout": 60, "ReservedConcurrentExecutions": 1, "Role": arn("WatchdogRole"), "Tags": tags,
        "Environment": {"Variables": {"INPUT_BUCKET": ref("InputBucket"), "OUTPUT_BUCKET": ref("OutputBucket"), "JOB_PREFIX": sub("d62d-${RunId}-"), "DEADLINE": ref("WriteDeadline")}},
        "Code": {"ZipFile": (HERE / "watchdog.py").read_text(encoding="utf-8")}}}
    r["Schedule"] = {"Type": "AWS::Events::Rule", "Properties": {"Name": sub("dora-62d-${RunId}-expiry"), "ScheduleExpression": "rate(5 minutes)", "State": "ENABLED", "Targets": [{"Id": "Expiry", "Arn": arn("Watchdog")}]}}
    r["InvokePermission"] = {"Type": "AWS::Lambda::Permission", "Properties": {"Action": "lambda:InvokeFunction", "FunctionName": ref("Watchdog"), "Principal": "events.amazonaws.com", "SourceArn": arn("Schedule"), "SourceAccount": ref("AWS::AccountId")}}
    return {"AWSTemplateFormatVersion": "2010-09-09", "Description": "DORA 6.2D bounded test only; deploy in eu-central-1 after authenticated account approval",
        "Parameters": {"RunId": {"Type": "String", "AllowedPattern": "[a-f0-9]{8,16}"}, "OwnerPrincipalArn": {"Type": "String", "AllowedPattern": "arn:aws:iam::[0-9]{12}:role/.+"}, "WriteDeadline": {"Type": "String", "AllowedPattern": "[0-9TZ:+.-]+"}},
        "Rules": {"FrankfurtOnly": {"Assertions": [{"Assert": {"Fn::Equals": [ref("AWS::Region"), REGION]}, "AssertDescription": "eu-central-1 only"}]}},
        "Resources": r, "Outputs": {name: {"Value": arn(name) if name.endswith(("Key", "Role")) else ref(name)} for name in ("InputBucket", "OutputBucket", "InputKey", "OutputKey", "DataRole", "OperatorRole", "Watchdog", "Schedule")}}


class AwsError(RuntimeError):
    def __init__(self, code): super().__init__(code); self.code = code


class Aws:
    def __init__(self, binary, profile=PROFILE):
        self.binary, self.profile = str(binary), profile
        self.evidence = []

    def call(self, *args):
        env = os.environ.copy()
        for key in list(env):
            if key.startswith("AWS_"):
                env.pop(key)
        env.update(AWS_MAX_ATTEMPTS="1", AWS_EC2_METADATA_DISABLED="true", AWS_PAGER="", AWS_IGNORE_CONFIGURED_ENDPOINT_URLS="true")
        cmd = [self.binary, *args, "--profile", self.profile, "--region", REGION, "--output", "json", "--no-cli-pager"]
        result = subprocess.run(cmd, capture_output=True, text=True, env=env, timeout=120)
        if result.returncode:
            match = re.search(r"\(([A-Za-z0-9_.-]+)\)", result.stderr)
            code = match.group(1) if match else "CLI_FAILED_REDACTED"
            self.evidence.append({"operation": list(args[:2]), "error": code})
            raise AwsError(code)
        value = json.loads(result.stdout) if result.stdout.strip() else {}
        self.evidence.append({"operation": list(args[:2]), "response": value})
        return value


def identity_and_optout(api, c):
    identity = api.call("sts", "get-caller-identity")
    require(identity["Account"] == c["account_id"], "ACCOUNT_MISMATCH")
    expected_role = c["owner_principal_arn"].split("/")[-1]
    require(identity["Arn"].startswith("arn:aws:sts::" + c["account_id"] + ":assumed-role/" + expected_role + "/"), "OWNER_ROLE_MISMATCH")
    api.call("organizations", "describe-organization")
    effective = api.call("organizations", "describe-effective-policy", "--policy-type", "AISERVICES_OPT_OUT_POLICY")["EffectivePolicy"]
    require(effective.get("TargetId") == c["account_id"], "OPT_OUT_TARGET_MISMATCH")
    require(effective_optout(json.loads(effective["PolicyContent"])), "EFFECTIVE_TRANSCRIBE_OPT_OUT_REQUIRED")
    return identity


def preflight(api, c):
    rows = initial_preflight()
    def mark(index, status, evidence): rows[index - 1].update(status=status, evidence=evidence)
    mark(4, "PASS", "Explicit eu-central-1, no retries, no endpoint override, no fallback code")
    mark(11, "PASS", "Bounded delete/list/verify procedure prepared; execution unverified")
    try:
        identity_and_optout(api, c)
        mark(1, "PASS", "STS account and expected assumed role matched; private evidence")
        mark(5, "PASS", "Effective account-target policy from Organizations is optOut")
    except (AwsError, ValueError) as e:
        mark(1, "BLOCKED", str(e)); mark(5, "BLOCKED", str(e))
        return rows
    try:
        outputs = c["outputs"]
        for kind in ("Input", "Output"):
            bucket = outputs[kind + "Bucket"]
            base = ["--bucket", bucket, "--expected-bucket-owner", c["account_id"]]
            require(api.call("s3api", "get-bucket-location", *base)["LocationConstraint"] == REGION, "S3_REGION")
            pab = api.call("s3api", "get-public-access-block", *base)["PublicAccessBlockConfiguration"]
            require(all(pab.get(k) is True for k in ("BlockPublicAcls", "IgnorePublicAcls", "BlockPublicPolicy", "RestrictPublicBuckets")), "S3_PUBLIC_BLOCK")
            require(api.call("s3api", "get-bucket-versioning", *base).get("Status") is None, "S3_VERSIONING_WAS_ENABLED")
            require(api.call("s3api", "get-bucket-ownership-controls", *base)["OwnershipControls"]["Rules"] == [{"ObjectOwnership": "BucketOwnerEnforced"}], "S3_ACLS")
            require(api.call("s3api", "get-bucket-policy-status", *base)["PolicyStatus"]["IsPublic"] is False, "S3_PUBLIC_POLICY")
            api.call("s3api", "get-bucket-policy", *base)
            api.call("s3api", "get-bucket-lifecycle-configuration", *base)
            for op, absent in (("get-bucket-replication", "ReplicationConfigurationNotFoundError"), ("get-object-lock-configuration", "ObjectLockConfigurationNotFoundError")):
                try:
                    api.call("s3api", op, *base)
                    raise ValueError("PROHIBITED_S3_CONFIGURATION")
                except AwsError as e:
                    require(e.code == absent, "UNVERIFIED_S3_CONFIGURATION")
            enc = api.call("s3api", "get-bucket-encryption", *base)["ServerSideEncryptionConfiguration"]["Rules"]
            require(len(enc) == 1 and enc[0]["ApplyServerSideEncryptionByDefault"] == {"SSEAlgorithm": "aws:kms", "KMSMasterKeyID": outputs[kind + "Key"]}, "S3_KMS_MISMATCH")
            key = api.call("kms", "describe-key", "--key-id", outputs[kind + "Key"])["KeyMetadata"]
            require(key["Arn"].startswith("arn:aws:kms:" + REGION + ":" + c["account_id"] + ":key/") and key["KeyState"] == "Enabled" and key["KeyManager"] == "CUSTOMER" and key["KeySpec"] == "SYMMETRIC_DEFAULT" and key["KeyUsage"] == "ENCRYPT_DECRYPT" and key.get("MultiRegion") is False, "KMS_PROPERTIES")
            api.call("kms", "get-key-policy", "--key-id", key["Arn"], "--policy-name", "default")
        mark(3, "PASS", "Both S3 locations and customer KMS ARNs exactly eu-central-1")
        mark(6, "BLOCKED", "S3 properties verified; IAM/bucket-policy exact drift review still required")
        mark(8, "BLOCKED", "KMS properties verified; key/role policy effective permission review still required")
        mark(7, "BLOCKED", "Watchdog/deadline and immediate cleanup require synthetic deletion proof")
    except (KeyError, AwsError, ValueError) as e:
        mark(6, "BLOCKED", type(e).__name__ if isinstance(e, KeyError) else str(e))
    return rows


def cleanup(api, c):
    """Exact stack-owned prefixes only; late/in-flight jobs remain PENDING."""
    require("outputs" in c, "NO_BOUND_RESOURCES")
    outputs = c["outputs"]
    pending = []
    failures = []
    def attempt(*args):
        try:
            return api.call(*args)
        except (AwsError, subprocess.TimeoutExpired) as error:
            failures.append(error.code if isinstance(error, AwsError) else "CLI_TIMEOUT")
            return {}
    for job in attempt("transcribe", "list-transcription-jobs", "--job-name-contains", c["job_prefix"]).get("TranscriptionJobSummaries", []):
        name = job["TranscriptionJobName"]
        require(owned_job(c, name), "UNOWNED_JOB_RETURNED")
        state = attempt("transcribe", "get-transcription-job", "--transcription-job-name", name).get("TranscriptionJob", {}).get("TranscriptionJobStatus")
        if state not in ("COMPLETED", "FAILED"):
            pending.append(name); continue
        attempt("transcribe", "delete-transcription-job", "--transcription-job-name", name)
    for kind in ("Input", "Output"):
        bucket, prefix = outputs[kind + "Bucket"], c[kind.lower() + "_prefix"]
        base = ["--bucket", bucket, "--expected-bucket-owner", c["account_id"]]
        listing = attempt("s3api", "list-object-versions", *base, "--prefix", prefix)
        for obj in listing.get("Versions", []) + listing.get("DeleteMarkers", []):
            require(obj["Key"].startswith(prefix), "UNOWNED_OBJECT")
            attempt("s3api", "delete-object", *base, "--key", obj["Key"], "--version-id", obj["VersionId"])
        for upload in attempt("s3api", "list-multipart-uploads", *base, "--prefix", prefix).get("Uploads", []):
            require(upload["Key"].startswith(prefix), "UNOWNED_MULTIPART")
            attempt("s3api", "abort-multipart-upload", *base, "--key", upload["Key"], "--upload-id", upload["UploadId"])
        check = attempt("s3api", "list-object-versions", *base, "--prefix", prefix)
        if check.get("Versions") or check.get("DeleteMarkers"): failures.append("CLEANUP_OBJECTS_REMAIN")
        if attempt("s3api", "list-multipart-uploads", *base, "--prefix", prefix).get("Uploads"): failures.append("CLEANUP_PARTS_REMAIN")
    remaining = attempt("transcribe", "list-transcription-jobs", "--job-name-contains", c["job_prefix"]).get("TranscriptionJobSummaries", [])
    return {"status": "PENDING" if pending or remaining or failures else "VERIFIED_VISIBLE_EMPTY", "pending_jobs": pending, "failures": failures,
            "limitation": "Recheck after late completions; no assertion about provider-internal copies. Stack/key retirement separate."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["template", "bind", "readback", "preflight", "deploy", "cleanup", "retire"])
    parser.add_argument("--config", type=Path)
    parser.add_argument("--aws")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--approved-account", help="Exact owner-approved account; deploy only")
    args = parser.parse_args()
    if args.command == "template":
        args.output.write_text(json.dumps(template(), indent=2) + "\n", encoding="utf-8")
        return
    if args.command == "bind":
        require(args.aws and args.config, "PRIVATE_CONFIG_AND_CLI_REQUIRED")
        private_path(args.config); private_path(args.output)
        require(not args.config.exists(), "CONFIG_ALREADY_EXISTS")
        api = Aws(args.aws)
        identity = api.call("sts", "get-caller-identity")
        match = re.fullmatch(r"arn:aws:sts::(\d{12}):assumed-role/([^/]+)/[^/]+", identity["Arn"])
        require(match is not None, "SHORT_LIVED_ASSUMED_ROLE_REQUIRED")
        owner = api.call("iam", "get-role", "--role-name", match.group(2))["Role"]["Arn"]
        c = new_config(identity["Account"], owner, secrets.token_hex(6))
        validate_config(c)
        cli_path = Path(args.aws).resolve()
        c["client_sha256"] = hashlib.sha256(cli_path.read_bytes()).hexdigest()
        c["client_version"] = subprocess.run([str(cli_path), "--version"], capture_output=True, text=True, check=True).stdout.strip()
        save_private(args.config, c)
        save_private(args.output, {"status": "ACCOUNT_BOUND_NOT_DEPLOYED", "private_api_evidence": api.evidence})
        print("Private account binding saved; nothing deployed.")
        return
    require(args.config and args.aws, "PRIVATE_CONFIG_AND_CLI_REQUIRED")
    private_path(args.config); private_path(args.output)
    c = json.loads(args.config.read_text(encoding="utf-8")); validate_config(c)
    api = Aws(args.aws)
    report = {"status": "BLOCKED", "recorded_at": datetime.now(timezone.utc).isoformat()}
    try:
        if args.command == "preflight":
            report["checks"] = preflight(api, c)
        elif args.command == "readback":
            require(api.call("sts", "get-caller-identity")["Account"] == c["account_id"], "ACCOUNT_MISMATCH")
            stack = api.call("cloudformation", "describe-stacks", "--stack-name", c["stack_name"])["Stacks"][0]
            require(stack["StackId"].startswith("arn:aws:cloudformation:" + REGION + ":" + c["account_id"] + ":stack/" + c["stack_name"] + "/"), "FOREIGN_STACK")
            require(stack["StackStatus"] == "CREATE_COMPLETE", "STACK_NOT_READY")
            parameters = {p["ParameterKey"]: p["ParameterValue"] for p in stack["Parameters"]}
            require(parameters == {"RunId": c["run_id"], "OwnerPrincipalArn": c["owner_principal_arn"], "WriteDeadline": c["write_deadline"]}, "STACK_PARAMETERS_CHANGED")
            c["outputs"] = {p["OutputKey"]: p["OutputValue"] for p in stack["Outputs"]}
            validate_config(c)
            save_private(args.config, c)
            report["status"] = "RESOURCES_BOUND_PREFLIGHT_REQUIRED"
        elif args.command == "deploy":
            require(args.approved_account == c["account_id"], "EXPLICIT_ACCOUNT_APPROVAL_REQUIRED")
            identity_and_optout(api, c)
            require("outputs" not in c, "ALREADY_DEPLOYED_NO_SILENT_UPDATE")
            c["write_deadline"] = (datetime.now(timezone.utc) + timedelta(hours=23)).isoformat()
            body = json.dumps(template(), separators=(",", ":"))
            require(len(body.encode()) < 51200, "TEMPLATE_SIZE")
            c["template_sha256"] = hashlib.sha256(body.encode()).hexdigest()
            save_private(args.config, c)
            api.call("cloudformation", "create-stack", "--stack-name", c["stack_name"], "--template-body", body,
                     "--capabilities", "CAPABILITY_NAMED_IAM", "--parameters", json.dumps([
                         {"ParameterKey": "RunId", "ParameterValue": c["run_id"]},
                         {"ParameterKey": "OwnerPrincipalArn", "ParameterValue": c["owner_principal_arn"]},
                         {"ParameterKey": "WriteDeadline", "ParameterValue": c["write_deadline"]}]))
            report["status"] = "CREATE_REQUESTED_REQUIRES_STACK_READBACK"
        else:
            require(api.call("sts", "get-caller-identity")["Account"] == c["account_id"], "ACCOUNT_MISMATCH")
            report.update(cleanup(api, c))
            if args.command == "retire":
                require(report["status"] == "VERIFIED_VISIBLE_EMPTY", "CLEANUP_PENDING_NO_RETIRE")
                require(datetime.now(timezone.utc) >= datetime.fromisoformat(c["write_deadline"]), "WAIT_FOR_WRITE_FENCE_BEFORE_RETIRE")
                api.call("cloudformation", "delete-stack", "--stack-name", c["stack_name"])
                report["status"] = "DELETE_REQUESTED_VERIFY_STACK_ABSENT_AND_KEYS_PENDING_DELETION"
    finally:
        report["private_api_evidence"] = api.evidence
        save_private(args.output, report)
    print("Private AWS evidence saved; no audio upload or transcription call was made.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, AwsError, subprocess.TimeoutExpired) as error:
        print("BLOCKED: " + (str(error) if not isinstance(error, subprocess.TimeoutExpired) else "CLI_TIMEOUT"), file=sys.stderr)
        sys.exit(2)
