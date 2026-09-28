"""Private, bounded AWS preparation. No upload or StartTranscriptionJob implementation.

AWS replies and actual account identifiers are written only outside Git. Network reads
are explicit commands; template/tests are offline. Account governance is never changed.
"""
import argparse
import configparser
import base64
import io
from datetime import datetime, timedelta, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import sys
from urllib.parse import unquote
from urllib.parse import urlparse
from urllib.request import HTTPRedirectHandler, build_opener
import zipfile

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
    if "write_deadline" in c:
        require(datetime.fromisoformat(c["write_deadline"].replace("Z", "+00:00")).utcoffset() == timedelta(0), "UTC_DEADLINE_REQUIRED")
    if "expected_execution_principal_arn" in c:
        require(c["expected_execution_principal_arn"] == f"arn:aws:iam::{c['account_id']}:role/dora-62d-{c['run_id']}-operator", "EXECUTION_ROLE_SCOPE")
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


def digest_json(value):
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def exact_policy(actual, expected, reason):
    if isinstance(actual, str):
        actual = json.loads(unquote(actual))
    def normalized(value):
        if isinstance(value, dict):
            return {k: normalized(v) for k, v in value.items()}
        if isinstance(value, list):
            values = sorted((normalized(v) for v in value), key=lambda v: json.dumps(v, sort_keys=True))
            return values[0] if len(values) == 1 else values
        return value
    require(normalized(actual) == normalized(expected), reason)


def resolved_template(c):
    validate_config(c)
    values = {"AWS::AccountId": c["account_id"], "AWS::Region": REGION, "RunId": c["run_id"], "OwnerPrincipalArn": c["owner_principal_arn"], "WriteDeadline": c["write_deadline"], **c["outputs"]}
    for kind in ("Input", "Output"):
        values[kind + "Bucket.Arn"] = "arn:aws:s3:::" + values[kind + "Bucket"]
        values[kind + "Key.Arn"] = values[kind + "Key"]
    for name, suffix in (("DataRole", "data"), ("OperatorRole", "operator"), ("WatchdogRole", "watchdog")):
        values[name + ".Arn"] = f"arn:aws:iam::{c['account_id']}:role/dora-62d-{c['run_id']}-{suffix}"
    values["Watchdog.Arn"] = f"arn:aws:lambda:{REGION}:{c['account_id']}:function:dora-62d-{c['run_id']}-watchdog"
    values["Schedule.Arn"] = f"arn:aws:events:{REGION}:{c['account_id']}:rule/dora-62d-{c['run_id']}-expiry"
    def resolve(value):
        if isinstance(value, list): return [resolve(v) for v in value]
        if not isinstance(value, dict): return value
        if set(value) == {"Ref"}: return values[value["Ref"]]
        if set(value) == {"Fn::GetAtt"}: return values[".".join(value["Fn::GetAtt"]) ]
        if set(value) == {"Fn::Sub"}: return re.sub(r"\$\{([^}]+)\}", lambda m: values[m.group(1)], value["Fn::Sub"])
        return {k: resolve(v) for k, v in value.items()}
    return resolve(template())


def validate_sso_config(path):
    path = private_path(path)
    require(path.is_file(), "SSO_CONFIG_MISSING")
    cfg = configparser.ConfigParser(interpolation=None)
    cfg.read_string(path.read_text(encoding="utf-8-sig"))
    section = "profile " + PROFILE
    require(section in cfg and not cfg.defaults(), "SSO_CONFIG_PROFILE")
    p = cfg[section]
    require(set(p) <= {"sso_session", "sso_account_id", "sso_role_name", "region", "output"}, "SSO_CONFIG_UNSAFE_FIELD")
    require(p.get("region") == REGION and re.fullmatch(r"\d{12}", p.get("sso_account_id", "")), "SSO_CONFIG_BINDING")
    require(re.fullmatch(r"[A-Za-z0-9_+=,.@-]+", p.get("sso_role_name", "")), "SSO_CONFIG_ROLE")
    session = "sso-session " + p.get("sso_session", "")
    require(set(cfg.sections()) == {section, session}, "SSO_CONFIG_EXTRA_SECTION")
    s = cfg[session]
    require(set(s) <= {"sso_start_url", "sso_region", "sso_registration_scopes"}, "SSO_CONFIG_UNSAFE_FIELD")
    require(s.get("sso_region") == REGION and re.fullmatch(r"https://[A-Za-z0-9-]+\.awsapps\.com/start/?", s.get("sso_start_url", "")), "SSO_CONFIG_SESSION")
    require(s.get("sso_registration_scopes", "sso:account:access") == "sso:account:access", "SSO_CONFIG_SCOPE")
    return path, p["sso_account_id"], p["sso_role_name"]


def proof_policy(c):
    """Read/assume-only extension; owner performs reviewed deployment and retirement."""
    validate_config(c)
    account, run = c["account_id"], c["run_id"]
    role = f"arn:aws:iam::{account}:role/dora-62d-{run}-"
    buckets = [f"arn:aws:s3:::dora-62d-{account}-{run}-{kind}" for kind in ("input", "output")]
    statements = [statement(["sts:GetCallerIdentity", "organizations:DescribeOrganization", "organizations:DescribeEffectivePolicy"], "*"),
        statement("sts:AssumeRole", role + "operator"),
        statement(["iam:GetRole", "iam:GetRolePolicy", "iam:ListRolePolicies", "iam:ListAttachedRolePolicies"], [c["owner_principal_arn"], *[role + k for k in ("data", "operator", "watchdog")]]),
        statement(["cloudformation:DescribeStacks", "cloudformation:GetTemplate", "cloudformation:DescribeStackResources"], f"arn:aws:cloudformation:{REGION}:{account}:stack/{c['stack_name']}/*"),
        statement(["s3:GetBucketLocation", "s3:GetBucketPublicAccessBlock", "s3:GetBucketVersioning", "s3:GetBucketOwnershipControls", "s3:GetBucketPolicyStatus", "s3:GetBucketPolicy", "s3:GetLifecycleConfiguration", "s3:GetReplicationConfiguration", "s3:GetBucketObjectLockConfiguration", "s3:GetEncryptionConfiguration", "s3:ListBucket", "s3:ListBucketVersions", "s3:ListBucketMultipartUploads"], buckets),
        statement("transcribe:ListTranscriptionJobs", "*"),
        statement("transcribe:GetTranscriptionJob", f"arn:aws:transcribe:{REGION}:{account}:transcription-job/d62d-{run}-*"),
        statement(["kms:DescribeKey", "kms:GetKeyPolicy", "kms:GetKeyRotationStatus", "kms:ListGrants"], f"arn:aws:kms:{REGION}:{account}:key/*", Condition={"StringEquals": {"aws:ResourceTag/Run": run}}),
        statement(["lambda:GetFunction", "lambda:GetFunctionConfiguration", "lambda:GetFunctionConcurrency", "lambda:GetPolicy", "lambda:InvokeFunction"], f"arn:aws:lambda:{REGION}:{account}:function:dora-62d-{run}-watchdog"),
        statement(["events:DescribeRule", "events:ListTargetsByRule"], f"arn:aws:events:{REGION}:{account}:rule/dora-62d-{run}-expiry")]
    return policy(statements)


def console_bundle(c):
    """Freeze a private bundle before owner console deployment; never calls AWS."""
    validate_config(c)
    require("outputs" not in c, "ALREADY_DEPLOYED_NO_SILENT_UPDATE")
    if "write_deadline" not in c:
        c["write_deadline"] = (datetime.now(timezone.utc) + timedelta(hours=23)).isoformat()
    body = template()
    c["template_sha256"] = digest_json(body)
    return {"template": body, "parameters": {"RunId": c["run_id"], "OwnerPrincipalArn": c["owner_principal_arn"], "WriteDeadline": c["write_deadline"]},
            "stack_name": c["stack_name"], "region": REGION, "proof_extension": proof_policy(c),
            "authority": "OWNER_CONTROLLED_CONSOLE_DEPLOYMENT_NO_PERSISTENT_PROVISIONER"}


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
                statement(["kms:DescribeKey", "kms:GetKeyPolicy", "kms:PutKeyPolicy", "kms:EnableKeyRotation", "kms:GetKeyRotationStatus", "kms:ScheduleKeyDeletion", "kms:CancelKeyDeletion", "kms:EnableKey", "kms:DisableKey", "kms:TagResource", "kms:UntagResource", "kms:ListResourceTags"], "*", Principal={"AWS": sub("arn:aws:iam::${AWS::AccountId}:root")}),
                statement(["kms:DescribeKey", "kms:GetKeyPolicy", "kms:GetKeyRotationStatus", "kms:ListGrants"], "*", Principal={"AWS": ref("OwnerPrincipalArn")}),
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
        "Timeout": 60, "Role": arn("WatchdogRole"), "Tags": tags,
        "Environment": {"Variables": {"INPUT_BUCKET": ref("InputBucket"), "OUTPUT_BUCKET": ref("OutputBucket"), "JOB_PREFIX": sub("d62d-${RunId}-"), "DEADLINE": ref("WriteDeadline")}},
        "Code": {"ZipFile": (HERE / "watchdog.py").read_text(encoding="utf-8")}}}
    r["Schedule"] = {"Type": "AWS::Events::Rule", "Properties": {"Name": sub("dora-62d-${RunId}-expiry"), "ScheduleExpression": "rate(5 minutes)", "State": "ENABLED", "Targets": [{"Id": "Expiry", "Arn": arn("Watchdog")}]}}
    r["InvokePermission"] = {"Type": "AWS::Lambda::Permission", "Properties": {"Action": "lambda:InvokeFunction", "FunctionName": ref("Watchdog"), "Principal": "events.amazonaws.com", "SourceArn": arn("Schedule"), "SourceAccount": ref("AWS::AccountId")}}
    return {"AWSTemplateFormatVersion": "2010-09-09", "Description": "DORA 6.2D bounded test only; deploy in eu-central-1 after authenticated account approval",
        "Parameters": {"RunId": {"Type": "String", "AllowedPattern": "[a-f0-9]{8,16}"}, "OwnerPrincipalArn": {"Type": "String", "AllowedPattern": "arn:aws:iam::[0-9]{12}:role/.+"}, "WriteDeadline": {"Type": "String", "AllowedPattern": "[0-9TZ:+.-]+"}},
        "Rules": {"FrankfurtOnly": {"Assertions": [{"Assert": {"Fn::Equals": [ref("AWS::Region"), REGION]}, "AssertDescription": "eu-central-1 only"}]}},
        "Resources": r, "Outputs": {name: {"Value": arn(name) if name.endswith(("Key", "Role")) else ref(name)} for name in ("InputBucket", "OutputBucket", "InputKey", "OutputKey", "DataRole", "OperatorRole", "Watchdog", "Schedule")}}


class AwsError(RuntimeError):
    def __init__(self, code, detail=None):
        super().__init__(code)
        self.code = code
        # Private adjudication only; never part of str(error), logs or API evidence.
        self.detail = detail


class Aws:
    def __init__(self, binary, profile=PROFILE, config_file=None):
        require(profile == PROFILE, "NAMED_PROFILE_REQUIRED")
        self.binary, self.profile = str(binary), profile
        self.config_file = validate_sso_config(config_file)[0] if config_file else None
        self.config_sha256 = hashlib.sha256(self.config_file.read_bytes()).hexdigest() if self.config_file else None
        self._credentials = None
        self.evidence = []

    def function_source(self, reply):
        location = reply["Code"]["Location"]
        parsed = urlparse(location)
        require(parsed.scheme == "https" and parsed.hostname and parsed.hostname.endswith(".amazonaws.com"), "LAMBDA_CODE_HOST")
        class NoRedirect(HTTPRedirectHandler):
            def redirect_request(self, *args, **kwargs): return None
        with build_opener(NoRedirect).open(location, timeout=30) as response:
            require(response.geturl() == location, "LAMBDA_CODE_REDIRECT")
            data = response.read(262145)
        require(len(data) <= 262144, "LAMBDA_CODE_SIZE")
        require(base64.b64encode(hashlib.sha256(data).digest()).decode() == reply["Configuration"]["CodeSha256"], "LAMBDA_CODE_HASH")
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            require(archive.namelist() == ["index.py"] and archive.getinfo("index.py").file_size <= 65536, "LAMBDA_CODE_MEMBERS")
            return archive.read("index.py").decode("utf-8")

    def call(self, *args):
        env = os.environ.copy()
        for key in list(env):
            if key.startswith("AWS_"):
                env.pop(key)
        env.update(AWS_MAX_ATTEMPTS="1", AWS_EC2_METADATA_DISABLED="true", AWS_PAGER="", AWS_IGNORE_CONFIGURED_ENDPOINT_URLS="true")
        profile_args = ["--profile", self.profile]
        if self.config_file:
            require(hashlib.sha256(self.config_file.read_bytes()).hexdigest() == self.config_sha256, "SSO_CONFIG_CHANGED")
            env["AWS_CONFIG_FILE"] = str(self.config_file)
        if self._credentials:
            require(datetime.now(timezone.utc) < datetime.fromisoformat(self._credentials["Expiration"].replace("Z", "+00:00")), "SESSION_EXPIRED")
            env.update(AWS_ACCESS_KEY_ID=self._credentials["AccessKeyId"], AWS_SECRET_ACCESS_KEY=self._credentials["SecretAccessKey"], AWS_SESSION_TOKEN=self._credentials["SessionToken"])
            profile_args = []
        cmd = [self.binary, *args, *profile_args, "--region", REGION, "--output", "json", "--no-cli-pager"]
        result = subprocess.run(cmd, capture_output=True, text=True, env=env, timeout=120)
        if result.returncode:
            match = re.search(r"\(([A-Za-z0-9_.-]+)\)", result.stderr)
            code = match.group(1) if match else "CLI_FAILED_REDACTED"
            self.evidence.append({"operation": list(args[:2]), "error": code})
            raise AwsError(code, result.stderr)
        value = json.loads(result.stdout) if result.stdout.strip() else {}
        safe_value = {k: v for k, v in value.items() if k != "Credentials"}
        self.evidence.append({"operation": list(args[:2]), "response": safe_value})
        return value


def assume_operator(api, c):
    validate_config(c)
    identity_and_optout(api, c)
    expected = f"arn:aws:iam::{c['account_id']}:role/dora-62d-{c['run_id']}-operator"
    require(c.get("outputs", {}).get("OperatorRole") == expected, "OPERATOR_NOT_BOUND")
    reply = api.call("sts", "assume-role", "--role-arn", expected, "--role-session-name", "dora62d-bounded", "--duration-seconds", "3600")
    require(reply["AssumedRoleUser"]["Arn"].startswith(f"arn:aws:sts::{c['account_id']}:assumed-role/dora-62d-{c['run_id']}-operator/"), "OPERATOR_ROLE_MISMATCH")
    session = Aws(api.binary, config_file=api.config_file)
    session._credentials = reply["Credentials"]
    actual = session.call("sts", "get-caller-identity")
    require(actual["Account"] == c["account_id"] and actual["Arn"] == reply["AssumedRoleUser"]["Arn"], "OPERATOR_IDENTITY_MISMATCH")
    return session


def identity_and_optout(api, c):
    validate_config(c)
    identity = api.call("sts", "get-caller-identity")
    require(identity["Account"] == c["account_id"], "ACCOUNT_MISMATCH")
    expected_role = c["owner_principal_arn"].split("/")[-1]
    require(identity["Arn"].startswith("arn:aws:sts::" + c["account_id"] + ":assumed-role/" + expected_role + "/"), "OWNER_ROLE_MISMATCH")
    api.call("organizations", "describe-organization")
    effective = api.call("organizations", "describe-effective-policy", "--policy-type", "AISERVICES_OPT_OUT_POLICY")["EffectivePolicy"]
    require(effective.get("TargetId") == c["account_id"], "OPT_OUT_TARGET_MISMATCH")
    require(effective_optout(json.loads(effective["PolicyContent"])), "EFFECTIVE_TRANSCRIBE_OPT_OUT_REQUIRED")
    return identity


def verify_resources(api, c):
    """Fail closed on policy, trust, configuration, schedule or deployed-code drift."""
    validate_config(c)
    require(c.get("template_sha256") == digest_json(template()), "LOCAL_TEMPLATE_CHANGED")
    deployed = api.call("cloudformation", "get-template", "--stack-name", c["stack_name"], "--template-stage", "Original")["TemplateBody"]
    if isinstance(deployed, str): deployed = json.loads(deployed)
    require(digest_json(deployed) == c["template_sha256"], "DEPLOYED_TEMPLATE_CHANGED")
    source_args = ["--role-name", c["owner_principal_arn"].split("/")[-1]]
    require(api.call("iam", "list-attached-role-policies", *source_args)["AttachedPolicies"] == [], "PROOF_ATTACHED_POLICY_DRIFT")
    names = api.call("iam", "list-role-policies", *source_args)["PolicyNames"]
    require(len(names) == 1, "PROOF_INLINE_POLICY_COUNT_DRIFT")
    exact_policy(api.call("iam", "get-role-policy", *source_args, "--policy-name", names[0])["PolicyDocument"], proof_policy(c), "PROOF_INLINE_POLICY_DRIFT")
    resources = resolved_template(c)["Resources"]
    for logical in ("DataRole", "OperatorRole", "WatchdogRole"):
        props = resources[logical]["Properties"]
        args = ["--role-name", props["RoleName"]]
        role = api.call("iam", "get-role", *args)["Role"]
        require(role["Arn"] == f"arn:aws:iam::{c['account_id']}:role/{props['RoleName']}" and role["MaxSessionDuration"] == 3600 and not role.get("PermissionsBoundary"), "IAM_ROLE_DRIFT")
        exact_policy(role["AssumeRolePolicyDocument"], props["AssumeRolePolicyDocument"], "IAM_TRUST_DRIFT")
        require(api.call("iam", "list-attached-role-policies", *args)["AttachedPolicies"] == [], "IAM_ATTACHED_POLICY_DRIFT")
        require(sorted(api.call("iam", "list-role-policies", *args)["PolicyNames"]) == sorted(p["PolicyName"] for p in props["Policies"]), "IAM_INLINE_POLICY_NAMES_DRIFT")
        for p in props["Policies"]:
            exact_policy(api.call("iam", "get-role-policy", *args, "--policy-name", p["PolicyName"])["PolicyDocument"], p["PolicyDocument"], "IAM_INLINE_POLICY_DRIFT")
    for kind in ("Input", "Output"):
        key = c["outputs"][kind + "Key"]
        exact_policy(api.call("kms", "get-key-policy", "--key-id", key, "--policy-name", "default")["Policy"], resources[kind + "Key"]["Properties"]["KeyPolicy"], "KMS_POLICY_DRIFT")
        require(api.call("kms", "get-key-rotation-status", "--key-id", key)["KeyRotationEnabled"] is True, "KMS_ROTATION_DRIFT")
        require(api.call("kms", "list-grants", "--key-id", key)["Grants"] == [], "UNEXPECTED_KMS_GRANT")
        base = ["--bucket", c["outputs"][kind + "Bucket"], "--expected-bucket-owner", c["account_id"]]
        exact_policy(api.call("s3api", "get-bucket-policy", *base)["Policy"], resources[kind + "BucketPolicy"]["Properties"]["PolicyDocument"], "S3_POLICY_DRIFT")
        life = api.call("s3api", "get-bucket-lifecycle-configuration", *base)
        # S3 readback renames ExpirationInDays to Expiration.Days.
        expected = json.loads(json.dumps(resources[kind + "Bucket"]["Properties"]["LifecycleConfiguration"]))
        for rule in expected["Rules"]:
            rule["Expiration"] = {"Days": rule.pop("ExpirationInDays")}
        def lifecycle_rules(rules):
            normalized = json.loads(json.dumps(rules))
            for rule in normalized:
                if "Id" in rule:
                    require("ID" not in rule, "S3_LIFECYCLE_DRIFT")
                    rule["ID"] = rule.pop("Id")
                if "Prefix" in rule:
                    require("Filter" not in rule, "S3_LIFECYCLE_DRIFT")
                    rule["Filter"] = {"Prefix": rule.pop("Prefix")}
            return normalized
        require(lifecycle_rules(life["Rules"]) == lifecycle_rules(expected["Rules"]), "S3_LIFECYCLE_DRIFT")
    function = f"dora-62d-{c['run_id']}-watchdog"
    reply = api.call("lambda", "get-function", "--function-name", function)
    actual, expected = reply["Configuration"], resources["Watchdog"]["Properties"]
    require(actual.get("State") == "Active" and actual.get("LastUpdateStatus") == "Successful", "WATCHDOG_NOT_ACTIVE")
    for key in ("Runtime", "Handler", "MemorySize", "Timeout", "Role", "Environment"):
        require(actual[key] == expected[key], "WATCHDOG_CONFIG_DRIFT")
    require(not actual.get("VpcConfig", {}).get("VpcId") and not actual.get("Layers"), "WATCHDOG_EXTERNAL_DEPENDENCY")
    require(api.function_source(reply).replace("\r\n", "\n") == expected["Code"]["ZipFile"].replace("\r\n", "\n"), "WATCHDOG_CODE_DRIFT")
    # New-account quotas may prohibit any reservation; zero would disable cleanup.
    # Invocation remains scoped to the single verified rule and explicit probe.
    require("ReservedConcurrentExecutions" not in api.call("lambda", "get-function-concurrency", "--function-name", function), "WATCHDOG_CONCURRENCY_DRIFT")
    name = f"dora-62d-{c['run_id']}-expiry"
    schedule = api.call("events", "describe-rule", "--name", name)
    require(schedule["State"] == "ENABLED" and schedule["ScheduleExpression"] == "rate(5 minutes)", "WATCHDOG_SCHEDULE_DRIFT")
    require(api.call("events", "list-targets-by-rule", "--rule", name)["Targets"] == resources["Schedule"]["Properties"]["Targets"], "WATCHDOG_TARGET_DRIFT")
    invocation = json.loads(api.call("lambda", "get-policy", "--function-name", function)["Policy"])
    invocation.pop("Id", None)
    for s in invocation["Statement"]: s.pop("Sid", None)
    exact_policy(invocation, policy([statement("lambda:InvokeFunction", f"arn:aws:lambda:{REGION}:{c['account_id']}:function:{function}", Principal={"Service": "events.amazonaws.com"}, Condition={"StringEquals": {"AWS:SourceAccount": c["account_id"]}, "ArnLike": {"AWS:SourceArn": f"arn:aws:events:{REGION}:{c['account_id']}:rule/{name}"}})]), "WATCHDOG_INVOKE_POLICY_DRIFT")
    return {"status": "EXACT_RESOURCE_POLICIES_AND_WATCHDOG_VERIFIED", "template_sha256": c["template_sha256"]}


def preflight(api, c):
    validate_config(c)
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
            require(len(enc) == 1 and enc[0]["ApplyServerSideEncryptionByDefault"] == {"SSEAlgorithm": "aws:kms", "KMSMasterKeyID": outputs[kind + "Key"]} and enc[0].get("BucketKeyEnabled", False) is False, "S3_KMS_MISMATCH")
            key = api.call("kms", "describe-key", "--key-id", outputs[kind + "Key"])["KeyMetadata"]
            require(key["Arn"] == outputs[kind + "Key"] and key["KeyState"] == "Enabled" and key["KeyManager"] == "CUSTOMER" and key["KeySpec"] == "SYMMETRIC_DEFAULT" and key["KeyUsage"] == "ENCRYPT_DECRYPT" and key.get("MultiRegion") is False, "KMS_PROPERTIES")
            api.call("kms", "get-key-policy", "--key-id", key["Arn"], "--policy-name", "default")
        mark(3, "PASS", "Both S3 locations and customer KMS ARNs exactly eu-central-1")
        verify_resources(api, c)
        require(datetime.now(timezone.utc) < datetime.fromisoformat(c["write_deadline"].replace("Z", "+00:00")), "WRITE_DEADLINE_EXPIRED")
        mark(6, "PASS", "S3 isolation, absence of versioning/replication/lock and exact IAM/bucket policies verified")
        mark(8, "PASS", "Exact customer keys, policy/rotation, zero grants and exact runtime roles verified")
        mark(7, "BLOCKED", "Watchdog/deadline and immediate cleanup require synthetic deletion proof")
        if c.get("retention_proof_path") and c.get("retention_proof_sha256"):
            path = private_path(c["retention_proof_path"])
            require(hashlib.sha256(path.read_bytes()).hexdigest() == c["retention_proof_sha256"], "RETENTION_PROOF_CHANGED")
            proof = json.loads(path.read_text(encoding="utf-8"))
            require(proof.get("status") == "SYNTHETIC_RETENTION_VERIFIED" and proof.get("template_sha256") == c["template_sha256"] and proof.get("resources_sha256") == digest_json(c["outputs"]) and proof.get("write_deadline") == c["write_deadline"], "RETENTION_PROOF_UNBOUND")
            mark(7, "PASS", "Actual synthetic encrypted objects/multiparts deleted by verified watchdog code; independent empty readback, fixed future deadline and schedule")
    except (KeyError, AwsError, ValueError) as e:
        mark(6, "BLOCKED", type(e).__name__ if isinstance(e, KeyError) else str(e))
    return rows


def reconcile_cleanup(api, c):
    """Independent read-only inspection of whole dedicated buckets, including surprises."""
    validate_config(c)
    failures = []
    for kind in ("Input", "Output"):
        base = ["--bucket", c["outputs"][kind + "Bucket"], "--expected-bucket-owner", c["account_id"]]
        for op, fields in (("list-objects-v2", ("Contents",)), ("list-object-versions", ("Versions", "DeleteMarkers")), ("list-multipart-uploads", ("Uploads",))):
            try:
                reply = api.call("s3api", op, *base)
                if any(reply.get(field) for field in fields): failures.append(kind.upper() + "_NOT_EMPTY")
            except (AwsError, subprocess.TimeoutExpired): failures.append(kind.upper() + "_READ_UNVERIFIED")
    try:
        if api.call("transcribe", "list-transcription-jobs", "--job-name-contains", c["job_prefix"]).get("TranscriptionJobSummaries"):
            failures.append("JOBS_REMAIN")
    except (AwsError, subprocess.TimeoutExpired): failures.append("JOB_READ_UNVERIFIED")
    return {"status": "PENDING" if failures else "VERIFIED_VISIBLE_EMPTY", "failures": failures, "scope": "WHOLE_DEDICATED_BUCKETS_AND_OWNED_JOB_PREFIX"}


def retention_probe(proof_api, runtime_api, c, folder):
    """Explicit pre-audio synthetic probe: 32 non-speech bytes and empty multiparts."""
    validate_config(c)
    require(c.get("template_sha256") == digest_json(template()), "LOCAL_TEMPLATE_CHANGED")
    require(datetime.now(timezone.utc) < datetime.fromisoformat(c["write_deadline"].replace("Z", "+00:00")), "WRITE_DEADLINE_EXPIRED")
    require(not runtime_api.call("transcribe", "list-transcription-jobs", "--job-name-contains", c["job_prefix"]).get("TranscriptionJobSummaries"), "PROBE_REQUIRES_NO_OWNED_JOBS")
    folder = private_path(folder); folder.mkdir(parents=True, exist_ok=True)
    nonce = secrets.token_hex(8)
    marker = folder / ("non-speech-" + nonce + ".bin")
    marker.write_bytes(b"DORA retention non-speech probe!")
    scopes = []
    for kind in ("Input", "Output"):
        base = ["--bucket", c["outputs"][kind + "Bucket"], "--expected-bucket-owner", c["account_id"]]
        prefix = c[kind.lower() + "_prefix"] + "__retention_probe__/"
        scopes.append((base, prefix))
        require(not runtime_api.call("s3api", "list-object-versions", *base, "--prefix", prefix).get("Versions"), "PROBE_NAMESPACE_NOT_EMPTY")
        require(not runtime_api.call("s3api", "list-multipart-uploads", *base, "--prefix", prefix).get("Uploads"), "PROBE_NAMESPACE_NOT_EMPTY")
        key, kms = prefix + nonce + ".bin", c["outputs"][kind + "Key"]
        runtime_api.call("s3api", "put-object", *base, "--key", key, "--body", str(marker), "--server-side-encryption", "aws:kms", "--ssekms-key-id", kms)
        head = runtime_api.call("s3api", "head-object", *base, "--key", key)
        require(head.get("ContentLength") == 32 and head.get("ServerSideEncryption") == "aws:kms" and head.get("SSEKMSKeyId") == kms, "PROBE_OBJECT_NOT_VERIFIED")
        upload = runtime_api.call("s3api", "create-multipart-upload", *base, "--key", key + ".multipart", "--server-side-encryption", "aws:kms", "--ssekms-key-id", kms)
        listing = runtime_api.call("s3api", "list-multipart-uploads", *base, "--prefix", prefix)
        require(any(u.get("Key") == key + ".multipart" and u.get("UploadId") == upload["UploadId"] for u in listing.get("Uploads", [])), "PROBE_MULTIPART_NOT_VERIFIED")
    response_path = folder / ("watchdog-probe-" + nonce + ".json")
    payload = base64.b64encode(b'{"probe":"non-speech-v1"}').decode()
    invocation = proof_api.call("lambda", "invoke", "--function-name", f"dora-62d-{c['run_id']}-watchdog", "--invocation-type", "RequestResponse", "--payload", payload, str(response_path))
    require(invocation.get("StatusCode") == 200 and not invocation.get("FunctionError"), "PROBE_LAMBDA_FAILED")
    require(json.loads(response_path.read_text(encoding="utf-8"))["status"] == "PROBE_DELETION_ATTEMPTED_REQUIRES_READBACK", "PROBE_LAMBDA_RESPONSE")
    for api in (runtime_api, proof_api):
        for base, prefix in scopes:
            listing = api.call("s3api", "list-object-versions", *base, "--prefix", prefix)
            require(not listing.get("Versions") and not listing.get("DeleteMarkers"), "PROBE_OBJECTS_REMAIN")
            require(not api.call("s3api", "list-multipart-uploads", *base, "--prefix", prefix).get("Uploads"), "PROBE_MULTIPART_REMAINS")
    return {"status": "SYNTHETIC_RETENTION_VERIFIED", "recorded_at": datetime.now(timezone.utc).isoformat(),
            "template_sha256": c["template_sha256"], "resources_sha256": digest_json(c["outputs"]), "write_deadline": c["write_deadline"],
            "objects_confirmed_before_delete": 2, "multipart_uploads_confirmed_before_delete": 2,
            "scope": "Fixed synthetic namespace; immediate delete path and independent readback. Deadline scheduling checked separately; no speech or Transcribe start."}


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
    parser.add_argument("command", choices=["template", "bind", "console-bundle", "readback", "preflight", "retention-probe", "deploy", "cleanup", "retire"])
    parser.add_argument("--config", type=Path)
    parser.add_argument("--aws")
    parser.add_argument("--aws-config", type=Path, help="Explicit private, validated SSO configuration")
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
        api = Aws(args.aws, config_file=args.aws_config)
        identity = api.call("sts", "get-caller-identity")
        match = re.fullmatch(r"arn:aws:sts::(\d{12}):assumed-role/([^/]+)/[^/]+", identity["Arn"])
        require(match is not None, "SHORT_LIVED_ASSUMED_ROLE_REQUIRED")
        owner = api.call("iam", "get-role", "--role-name", match.group(2))["Role"]["Arn"]
        c = new_config(identity["Account"], owner, secrets.token_hex(6))
        if args.aws_config:
            _, account, role_name = validate_sso_config(args.aws_config)
            require(account == c["account_id"] and owner.split("/")[-1].startswith("AWSReservedSSO_" + role_name + "_"), "SSO_BINDING_MISMATCH")
            c["aws_config_path"] = str(api.config_file)
            c["aws_config_sha256"] = api.config_sha256
        validate_config(c)
        cli_path = Path(args.aws).resolve()
        c["client_sha256"] = hashlib.sha256(cli_path.read_bytes()).hexdigest()
        c["client_version"] = subprocess.run([str(cli_path), "--version"], capture_output=True, text=True, check=True).stdout.strip()
        save_private(args.config, c)
        save_private(args.output, {"status": "ACCOUNT_BOUND_NOT_DEPLOYED", "private_api_evidence": api.evidence})
        print("Private account binding saved; nothing deployed.")
        return
    require(args.config and (args.aws or args.command == "console-bundle"), "PRIVATE_CONFIG_AND_CLI_REQUIRED")
    private_path(args.config); private_path(args.output)
    c = json.loads(args.config.read_text(encoding="utf-8")); validate_config(c)
    if args.command == "console-bundle":
        bundle = console_bundle(c)
        save_private(args.config, c)
        save_private(args.output, bundle)
        print("Private owner-deployment bundle saved; no AWS call was made.")
        return
    config_file = args.aws_config or c.get("aws_config_path")
    api = Aws(args.aws, config_file=config_file)
    if c.get("aws_config_sha256"):
        require(api.config_sha256 == c["aws_config_sha256"], "BOUND_SSO_CONFIG_CHANGED")
    if c.get("client_sha256"):
        require(hashlib.sha256(Path(args.aws).read_bytes()).hexdigest() == c["client_sha256"], "BOUND_CLIENT_CHANGED")
    report = {"status": "BLOCKED", "recorded_at": datetime.now(timezone.utc).isoformat()}
    try:
        if args.command == "preflight":
            report["checks"] = preflight(api, c)
        elif args.command == "readback":
            identity_and_optout(api, c)
            stack = api.call("cloudformation", "describe-stacks", "--stack-name", c["stack_name"])["Stacks"][0]
            require(stack["StackId"].startswith("arn:aws:cloudformation:" + REGION + ":" + c["account_id"] + ":stack/" + c["stack_name"] + "/"), "FOREIGN_STACK")
            require(stack["StackStatus"] == "CREATE_COMPLETE", "STACK_NOT_READY")
            parameters = {p["ParameterKey"]: p["ParameterValue"] for p in stack["Parameters"]}
            require(parameters == {"RunId": c["run_id"], "OwnerPrincipalArn": c["owner_principal_arn"], "WriteDeadline": c["write_deadline"]}, "STACK_PARAMETERS_CHANGED")
            c["outputs"] = {p["OutputKey"]: p["OutputValue"] for p in stack["Outputs"]}
            validate_config(c)
            report["resources"] = verify_resources(api, c)
            c["expected_execution_principal_arn"] = c["outputs"]["OperatorRole"]
            save_private(args.config, c)
            report["status"] = "RESOURCES_BOUND_PREFLIGHT_REQUIRED"
        elif args.command == "retention-probe":
            identity_and_optout(api, c)
            verify_resources(api, c)
            runtime_api = assume_operator(api, c)
            try:
                report.update(retention_probe(api, runtime_api, c, args.output.parent))
            finally:
                report["private_runtime_evidence"] = runtime_api.evidence
        elif args.command == "deploy":
            require(args.approved_account == c["account_id"], "EXPLICIT_ACCOUNT_APPROVAL_REQUIRED")
            identity_and_optout(api, c)
            require("outputs" not in c, "ALREADY_DEPLOYED_NO_SILENT_UPDATE")
            c["write_deadline"] = (datetime.now(timezone.utc) + timedelta(hours=23)).isoformat()
            body = json.dumps(template(), separators=(",", ":"))
            require(len(body.encode()) < 51200, "TEMPLATE_SIZE")
            c["template_sha256"] = digest_json(template())
            save_private(args.config, c)
            api.call("cloudformation", "create-stack", "--stack-name", c["stack_name"], "--template-body", body,
                     "--capabilities", "CAPABILITY_NAMED_IAM", "--parameters", json.dumps([
                         {"ParameterKey": "RunId", "ParameterValue": c["run_id"]},
                         {"ParameterKey": "OwnerPrincipalArn", "ParameterValue": c["owner_principal_arn"]},
                         {"ParameterKey": "WriteDeadline", "ParameterValue": c["write_deadline"]}]))
            report["status"] = "CREATE_REQUESTED_REQUIRES_STACK_READBACK"
        else:
            runtime_api = assume_operator(api, c)
            report.update(cleanup(runtime_api, c))
            report["independent_reconciliation"] = reconcile_cleanup(api, c)
            if report["independent_reconciliation"]["status"] != "VERIFIED_VISIBLE_EMPTY": report["status"] = "PENDING"
            report["private_runtime_evidence"] = runtime_api.evidence
            if args.command == "retire":
                require(report["status"] == "VERIFIED_VISIBLE_EMPTY", "CLEANUP_PENDING_NO_RETIRE")
                require(datetime.now(timezone.utc) >= datetime.fromisoformat(c["write_deadline"]), "WAIT_FOR_WRITE_FENCE_BEFORE_RETIRE")
                api.call("cloudformation", "delete-stack", "--stack-name", c["stack_name"])
                report["status"] = "DELETE_REQUESTED_VERIFY_STACK_ABSENT_AND_KEYS_PENDING_DELETION"
    finally:
        report["private_api_evidence"] = api.evidence
        save_private(args.output, report)
    if args.command == "retention-probe" and report["status"] == "SYNTHETIC_RETENTION_VERIFIED":
        c["retention_proof_path"] = str(private_path(args.output))
        c["retention_proof_sha256"] = hashlib.sha256(args.output.read_bytes()).hexdigest()
        save_private(args.config, c)
    print("Private AWS evidence saved; no audio upload or transcription call was made.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, AwsError, subprocess.TimeoutExpired) as error:
        print("BLOCKED: " + (str(error) if not isinstance(error, subprocess.TimeoutExpired) else "CLI_TIMEOUT"), file=sys.stderr)
        sys.exit(2)
