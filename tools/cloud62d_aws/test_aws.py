"""Offline fault tests. All account/ARN values are synthetic."""
import copy
import json
import tempfile
import unittest
from pathlib import Path
import aws_prepare as aws


class SafetyTests(unittest.TestCase):
    def test_readback_accepts_completed_update_but_rejects_inflight_or_rollback(self):
        import contextlib, io
        from unittest.mock import patch
        c = fixture_config()
        for status in ("CREATE_COMPLETE", "UPDATE_COMPLETE", "UPDATE_IN_PROGRESS", "UPDATE_ROLLBACK_COMPLETE", "ROLLBACK_COMPLETE"):
            with self.subTest(status=status), tempfile.TemporaryDirectory() as folder:
                class StackApi(PreflightApi):
                    def call(self, service, op, *args):
                        if op == "describe-stacks":
                            return {"Stacks": [{"StackId": "arn:aws:cloudformation:eu-central-1:123456789012:stack/" + c["stack_name"] + "/test", "StackStatus": status,
                                "Parameters": [{"ParameterKey": k, "ParameterValue": v} for k, v in {"RunId": c["run_id"], "OwnerPrincipalArn": c["owner_principal_arn"], "WriteDeadline": c["write_deadline"]}.items()],
                                "Outputs": [{"OutputKey": k, "OutputValue": v} for k, v in c["outputs"].items()]}]}
                        return super().call(service, op, *args)
                config, report = Path(folder) / "config.json", Path(folder) / "report.json"
                config.write_text(json.dumps(c))
                before = config.read_bytes()
                with patch("sys.argv", ["aws_prepare", "readback", "--aws", "synthetic", "--config", str(config), "--output", str(report)]), patch.object(aws, "Aws", return_value=StackApi(c)), contextlib.redirect_stdout(io.StringIO()):
                    if status in ("CREATE_COMPLETE", "UPDATE_COMPLETE"):
                        aws.main()
                        self.assertEqual(json.loads(report.read_text())["status"], "RESOURCES_BOUND_PREFLIGHT_REQUIRED")
                        self.assertEqual(json.loads(config.read_text())["expected_execution_principal_arn"], c["outputs"]["OperatorRole"])
                    else:
                        with self.assertRaisesRegex(ValueError, "STACK_NOT_READY"): aws.main()
                        self.assertEqual(config.read_bytes(), before)
                        self.assertEqual(json.loads(report.read_text())["status"], "BLOCKED")

    def test_runtime_roles_allow_exact_bucket_head_and_key_description(self):
        resources = aws.resolved_template(fixture_config())["Resources"]
        buckets = ["arn:aws:s3:::dora-62d-123456789012-a1b2c3d4-input", "arn:aws:s3:::dora-62d-123456789012-a1b2c3d4-output"]
        keys = ["arn:aws:kms:eu-central-1:123456789012:key/" + digit * 8 + "-0000-0000-0000-000000000000" for digit in ("1", "2")]
        for role in ("DataRole", "OperatorRole"):
            statements = resources[role]["Properties"]["Policies"][0]["PolicyDocument"]["Statement"]
            for action, expected in (("s3:ListBucket", buckets), ("kms:DescribeKey", keys)):
                allowed = []
                for item in statements:
                    actions = item["Action"] if isinstance(item["Action"], list) else [item["Action"]]
                    if item["Effect"] == "Allow" and action in actions and not item.get("Condition"):
                        allowed.extend(item["Resource"] if isinstance(item["Resource"], list) else [item["Resource"]])
                self.assertEqual(sorted(allowed), sorted(expected), (role, action))
            for item in statements:
                actions = item["Action"] if isinstance(item["Action"], list) else [item["Action"]]
                if "s3:PutObject" in actions:
                    self.assertIn(item["Resource"], [buckets[0] + "/input/*", buckets[1] + "/output/*"])
        for kind in ("Input", "Output"):
            denies = resources[kind + "BucketPolicy"]["Properties"]["PolicyDocument"]["Statement"]
            self.assertTrue(any(s.get("Condition", {}).get("StringNotEqualsIfExists", {}).get("s3:x-amz-server-side-encryption") == "aws:kms" for s in denies))
            self.assertTrue(any(s.get("Condition", {}).get("StringNotEqualsIfExists", {}).get("s3:x-amz-server-side-encryption-aws-kms-key-id") == keys[0 if kind == "Input" else 1] for s in denies))

    def test_preflight_rejects_runtime_head_or_describe_denial(self):
        from unittest.mock import patch
        c = fixture_config()
        for denied in (("head-bucket", "Input"), ("head-bucket", "Output"), ("describe-key", "Input"), ("describe-key", "Output")):
            with self.subTest(denied=denied):
                proof, runtime = PreflightApi(c), RuntimeReadApi(c, denied)
                with patch.object(aws, "assume_operator", return_value=runtime):
                    rows = aws.preflight(proof, c)
                self.assertEqual(rows[5]["status"], "BLOCKED")
                self.assertEqual(rows[7]["status"], "BLOCKED")
                self.assertIn("AccessDenied", rows[5]["evidence"])
                self.assertTrue(any(item.get("private_runtime_evidence") for item in proof.evidence))

    def test_preflight_requires_actual_operator_identity_and_four_read_checks(self):
        from unittest.mock import patch
        c = fixture_config()
        proof, runtime = PreflightApi(c), RuntimeReadApi(c)
        with patch.object(aws, "assume_operator", return_value=runtime):
            rows = aws.preflight(proof, c)
        self.assertEqual(rows[5]["status"], "PASS")
        self.assertEqual(rows[7]["status"], "PASS")
        self.assertEqual(runtime.checked, [("head-bucket", "Input"), ("describe-key", "Input"), ("head-bucket", "Output"), ("describe-key", "Output")])
        for fault in ("wrong-identity", "wrong-key"):
            with patch.object(aws, "assume_operator", return_value=RuntimeReadApi(c, fault)):
                self.assertEqual(aws.preflight(PreflightApi(c), c)[5]["status"], "BLOCKED")

    def test_lifecycle_aws_filter_and_id_spelling_preserves_exact_semantics(self):
        c = fixture_config()
        class ServiceShape(ResourceApi):
            def call(self, service, op, *args):
                reply = super().call(service, op, *args)
                if op == 'get-bucket-lifecycle-configuration':
                    for rule in reply['Rules']:
                        rule['ID'] = rule.pop('Id')
                        rule['Filter'] = {'Prefix': rule.pop('Prefix')}
                return reply
        aws.verify_resources(ServiceShape(c), c)
        for extra in ({'Tag': {'Key': 'bypass', 'Value': 'x'}}, {'ObjectSizeGreaterThan': 1}):
            class FilterDrift(ServiceShape):
                def call(self, service, op, *args):
                    reply = super().call(service, op, *args)
                    if op == 'get-bucket-lifecycle-configuration': reply['Rules'][0]['Filter'].update(extra)
                    return reply
            with self.assertRaisesRegex(ValueError, 'S3_LIFECYCLE_DRIFT'):
                aws.verify_resources(FilterDrift(c), c)

    def test_new_account_deployment_never_requests_reserved_concurrency(self):
        props = aws.console_bundle(aws.new_config("123456789012", "arn:aws:iam::123456789012:role/TestOwner", "a1b2c3d4"))["template"]["Resources"]["Watchdog"]["Properties"]
        # Reserving even one execution can be rejected on a new account quota;
        # setting zero is worse because it disables the deletion backstop.
        self.assertNotIn("ReservedConcurrentExecutions", props)
        self.assertEqual(props["Timeout"], 60)

    def test_unreserved_watchdog_passes_and_any_reservation_is_drift(self):
        c = fixture_config()
        aws.verify_resources(ResourceApi(c), c)
        for value in (0, 1, 10):
            with self.subTest(value=value):
                class ReservedApi(ResourceApi):
                    def call(self, service, op, *args):
                        if op == "get-function-concurrency": return {"ReservedConcurrentExecutions": value}
                        return super().call(service, op, *args)
                with self.assertRaisesRegex(ValueError, "WATCHDOG_CONCURRENCY_DRIFT"):
                    aws.verify_resources(ReservedApi(c), c)

    def test_retention_probe_requires_real_objects_and_independent_empty_readback(self):
        c = fixture_config()
        proof, runtime = ProbeApi(c), ProbeApi(c)
        proof.runtime = runtime
        with tempfile.TemporaryDirectory() as folder:
            result = aws.retention_probe(proof, runtime, c, Path(folder))
            self.assertEqual(result["status"], "SYNTHETIC_RETENTION_VERIFIED")
            self.assertEqual(result["objects_confirmed_before_delete"], 2)
            self.assertEqual(result["multipart_uploads_confirmed_before_delete"], 2)
            self.assertEqual(runtime.objects, {})
            self.assertEqual(runtime.uploads, {})
        proof, runtime = ProbeApi(c), ProbeApi(c)
        proof.runtime = runtime; proof.fail_delete = True
        with tempfile.TemporaryDirectory() as folder:
            with self.assertRaisesRegex(ValueError, "PROBE_OBJECTS_REMAIN"):
                aws.retention_probe(proof, runtime, c, Path(folder))

    def test_reconciliation_detects_unexpected_objects_and_denied_reads(self):
        from unittest.mock import Mock
        c = fixture_config()
        api = Mock(); api.call.return_value = {}
        self.assertEqual(aws.reconcile_cleanup(api, c)["status"], "VERIFIED_VISIBLE_EMPTY")
        api.call.side_effect = lambda *args: {"Contents": [{"Key": "outside-prefix"}]} if args[1] == "list-objects-v2" else {}
        self.assertEqual(aws.reconcile_cleanup(api, c)["status"], "PENDING")
        api.call.side_effect = aws.AwsError("AccessDenied")
        self.assertEqual(aws.reconcile_cleanup(api, c)["status"], "PENDING")

    def test_assume_role_credentials_never_enter_evidence(self):
        from unittest.mock import patch
        from types import SimpleNamespace
        reply = {"Credentials": {"AccessKeyId": "synthetic-secret"}, "AssumedRoleUser": {"Arn": "synthetic-role"}}
        with patch("subprocess.run", return_value=SimpleNamespace(returncode=0, stdout=json.dumps(reply))):
            api = aws.Aws("aws")
            self.assertEqual(api.call("sts", "assume-role"), reply)
            self.assertNotIn("synthetic-secret", json.dumps(api.evidence))

    def test_resource_policy_drift_never_passes_preflight(self):
        c = fixture_config()
        for failure in ("role-extra-policy", "proof-extra-policy", "key-grant", "bucket-policy", "lifecycle", "watchdog-env", "event-target"):
            with self.subTest(failure=failure):
                api = ResourceApi(c, failure)
                with self.assertRaises(ValueError): aws.verify_resources(api, c)
        aws.verify_resources(ResourceApi(c), c)

    def test_bound_config_rejects_wrong_deadline_and_execution_role(self):
        c = aws.new_config("123456789012", "arn:aws:iam::123456789012:role/TestOwner", "a1b2c3d4")
        c["write_deadline"] = "not-a-time"
        with self.assertRaises(ValueError): aws.validate_config(c)
        c.pop("write_deadline")
        c["expected_execution_principal_arn"] = "arn:aws:iam::123456789012:role/Other"
        with self.assertRaises(ValueError): aws.validate_config(c)

    def test_policy_comparison_rejects_extra_action_but_accepts_aws_singletons(self):
        wanted = {"Statement": [{"Effect": "Allow", "Action": ["s3:GetObject"], "Resource": "arn:aws:s3:::test/a"}], "Version": "2012-10-17"}
        actual = copy.deepcopy(wanted); actual["Statement"][0]["Action"] = "s3:GetObject"
        aws.exact_policy(actual, wanted, "POLICY_DRIFT")
        actual["Statement"][0]["Action"] = ["s3:GetObject", "s3:PutObject"]
        with self.assertRaisesRegex(ValueError, "POLICY_DRIFT"):
            aws.exact_policy(actual, wanted, "POLICY_DRIFT")

    def test_watchdog_probe_before_deadline_is_confined_to_synthetic_prefix(self):
        import os, sys
        from unittest.mock import patch, Mock
        import watchdog
        sdk, s3 = Mock(), Mock(); sdk.client.return_value = s3
        s3.get_paginator.return_value.paginate.return_value = [{}]
        with patch.dict(sys.modules, {"boto3": sdk}), patch.dict(os.environ, {"DEADLINE": "2999-01-01T00:00:00+00:00", "INPUT_BUCKET": "test-input", "OUTPUT_BUCKET": "test-output", "JOB_PREFIX": "d62d-test-"}):
            result = watchdog.handler({"probe": "non-speech-v1", "prefix": "input/"}, None)
        self.assertEqual(result["status"], "PROBE_DELETION_ATTEMPTED_REQUIRES_READBACK")
        prefixes = [c.kwargs["Prefix"] for c in s3.get_paginator.return_value.paginate.call_args_list]
        self.assertEqual(set(prefixes), {"input/__retention_probe__/", "output/__retention_probe__/"})
        self.assertEqual(sdk.client.call_count, 1)

    def test_console_bundle_has_owner_recovery_and_no_provisioner_grants(self):
        c = aws.new_config("123456789012", "arn:aws:iam::123456789012:role/TestOwner", "a1b2c3d4")
        bundle = aws.console_bundle(c)
        self.assertEqual(bundle["parameters"]["RunId"], "a1b2c3d4")
        self.assertEqual(c["template_sha256"], aws.digest_json(bundle["template"]))
        rules = bundle["template"]["Resources"]["InputKey"]["Properties"]["KeyPolicy"]["Statement"]
        admins = [s for s in rules if "kms:PutKeyPolicy" in s["Action"]]
        self.assertEqual(len(admins), 1)
        self.assertEqual(admins[0]["Principal"]["AWS"], {"Fn::Sub": "arn:aws:iam::${AWS::AccountId}:root"})
        actions = [a for s in bundle["proof_extension"]["Statement"] for a in ([s["Action"]] if isinstance(s["Action"], str) else s["Action"])]
        self.assertIn("sts:AssumeRole", actions)
        self.assertFalse(any(a in actions for a in ("iam:PutRolePolicy", "cloudformation:CreateStack", "kms:PutKeyPolicy", "s3:PutObject")))

    def test_explicit_sso_config_is_preserved_but_unsafe_fields_rejected(self):
        import os
        from unittest.mock import patch
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "sso.ini"
            valid = "[profile dora-62d-alpha]\nsso_session = proof\nsso_account_id = 123456789012\nsso_role_name = Dora62dProof\nregion = eu-central-1\n[sso-session proof]\nsso_start_url = https://synthetic.awsapps.com/start\nsso_region = eu-central-1\nsso_registration_scopes = sso:account:access\n"
            path.write_text(valid)
            with patch.dict(os.environ, {"AWS_ACCESS_KEY_ID": "bad"}), patch("subprocess.run", return_value=SimpleNamespace(returncode=0, stdout="{}")) as run:
                aws.Aws("aws", config_file=path).call("sts", "get-caller-identity")
                self.assertEqual(run.call_args.kwargs["env"]["AWS_CONFIG_FILE"], str(path.resolve()))
                self.assertNotIn("AWS_ACCESS_KEY_ID", run.call_args.kwargs["env"])
            path.write_text(valid.replace("sso_session = proof", "credential_process = bad\nsso_session = proof"))
            with self.assertRaisesRegex(ValueError, "SSO_CONFIG"):
                aws.Aws("aws", config_file=path)

    def test_effective_optout_inheritance(self):
        base = {"services": {"default": {"opt_out_policy": "optOut"}}}
        self.assertTrue(aws.effective_optout(base))
        base["services"]["transcribe"] = {"opt_out_policy": "optIn"}
        self.assertFalse(aws.effective_optout(base))
        self.assertFalse(aws.effective_optout({}))
        self.assertFalse(aws.effective_optout({"services": {"transcribe": {"opt_out_policy": {"@@assign": "optOut"}}}}))

    def test_account_scope_and_region_fail_closed(self):
        c = aws.new_config("123456789012", "arn:aws:iam::123456789012:role/TestOwner", "a1b2c3d4")
        aws.validate_config(c)
        for key, bad in [("region", "us-east-1"), ("account_id", "other"), ("run_id", "../prod"), ("owner_principal_arn", "arn:aws:iam::999999999999:role/Other")]:
            d = copy.deepcopy(c); d[key] = bad
            with self.assertRaises(ValueError): aws.validate_config(d)

    def test_private_path_cannot_be_in_git(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); (root / ".git").mkdir()
            with self.assertRaises(ValueError): aws.private_path(root / "evidence.json")

    def test_template_has_no_blanket_actions_or_replication(self):
        t = aws.template()
        text = json.dumps(t)
        for broad in ("s3:*", "kms:*", "iam:*", "organizations:*", "transcribe:*"):
            self.assertNotIn(broad, text)
        self.assertNotIn('"ReplicationConfiguration"', text)
        for name in ("InputBucket", "OutputBucket"):
            props = t["Resources"][name]["Properties"]
            self.assertNotIn("VersioningConfiguration", props)
            self.assertTrue(all(props["PublicAccessBlockConfiguration"].values()))
            self.assertEqual(props["OwnershipControls"]["Rules"][0]["ObjectOwnership"], "BucketOwnerEnforced")
        self.assertFalse(t["Resources"]["InputKey"]["Properties"]["MultiRegion"])
        self.assertFalse(t["Resources"]["OutputKey"]["Properties"]["MultiRegion"])

    def test_preflight_never_infers_retention_pass(self):
        report = aws.initial_preflight()
        self.assertEqual(len(report), 12)
        self.assertEqual(report[6]["status"], "BLOCKED")
        self.assertEqual(report[8]["status"], "BLOCKED")
        self.assertEqual(report[9]["status"], "BLOCKED")

    def test_cleanup_scope_rejects_unnamed_jobs(self):
        c = aws.new_config("123456789012", "arn:aws:iam::123456789012:role/TestOwner", "a1b2c3d4")
        self.assertTrue(aws.owned_job(c, "d62d-a1b2c3d4-0001"))
        self.assertFalse(aws.owned_job(c, "prod-d62d-a1b2c3d4-0001"))
        self.assertFalse(aws.owned_job(c, "d62d-other-0001"))

    def test_cleanup_rejects_foreign_bucket_config(self):
        c = aws.new_config("123456789012", "arn:aws:iam::123456789012:role/TestOwner", "a1b2c3d4")
        c["outputs"] = {"InputBucket": "production"}
        with self.assertRaises(ValueError): aws.validate_config(c)

    def test_cli_cannot_inherit_endpoint_or_permanent_credential_environment(self):
        import os
        from unittest.mock import patch
        from types import SimpleNamespace
        with patch.dict(os.environ, {"AWS_ACCESS_KEY_ID": "synthetic", "AWS_ENDPOINT_URL": "https://bad.invalid"}):
            with patch("subprocess.run", return_value=SimpleNamespace(returncode=0, stdout="{}")) as run:
                aws.Aws("aws").call("sts", "get-caller-identity")
                env = run.call_args.kwargs["env"]
                self.assertNotIn("AWS_ACCESS_KEY_ID", env)
                self.assertNotIn("AWS_ENDPOINT_URL", env)
                self.assertEqual(env["AWS_MAX_ATTEMPTS"], "1")

    def test_watchdog_does_not_delete_before_deadline(self):
        import os
        import sys
        from unittest.mock import patch, Mock
        import watchdog
        sdk = Mock()
        with patch.dict(sys.modules, {"boto3": sdk}), patch.dict(os.environ, {"DEADLINE": "2999-01-01T00:00:00+00:00"}):
            self.assertEqual(watchdog.handler({}, None)["status"], "BEFORE_DEADLINE")
            sdk.client.assert_not_called()

    def test_cli_redacts_provider_error_detail(self):
        from unittest.mock import patch
        from types import SimpleNamespace
        with patch("subprocess.run", return_value=SimpleNamespace(returncode=1, stderr="An error occurred (AccessDenied) secret-sensitive-info", stdout="")):
            api = aws.Aws("aws")
            with self.assertRaisesRegex(aws.AwsError, "^AccessDenied$") as caught:
                api.call("sts", "get-caller-identity")
            self.assertNotIn("secret-sensitive-info", json.dumps(api.evidence))
            self.assertIn("secret-sensitive-info", caught.exception.detail)
            self.assertNotIn("secret-sensitive-info", str(caught.exception))

    def test_watchdog_continues_output_and_jobs_after_input_failure(self):
        import os, sys
        from unittest.mock import patch, Mock
        import watchdog
        s3, transcribe = Mock(), Mock()
        def pages(**kwargs):
            if kwargs["Bucket"] == "test-input":
                raise RuntimeError("synthetic denied")
            return [{"Versions": [{"Key": "output/a", "VersionId": "null"}]}]
        s3.get_paginator.return_value.paginate.side_effect = pages
        transcribe.get_paginator.return_value.paginate.return_value = [{"TranscriptionJobSummaries": [
            {"TranscriptionJobName": "d62d-test-a", "TranscriptionJobStatus": "COMPLETED"},
            {"TranscriptionJobName": "d62d-test-b", "TranscriptionJobStatus": "IN_PROGRESS"},
            {"TranscriptionJobName": "other", "TranscriptionJobStatus": "COMPLETED"}]}]
        sdk = Mock(); sdk.client.side_effect = [s3, transcribe]
        with patch.dict(sys.modules, {"boto3": sdk}), patch.dict(os.environ, {"DEADLINE": "2020-01-01T00:00:00+00:00", "INPUT_BUCKET": "test-input", "OUTPUT_BUCKET": "test-output", "JOB_PREFIX": "d62d-test-"}):
            with self.assertRaisesRegex(RuntimeError, "CLEANUP_INCOMPLETE"):
                watchdog.handler({}, None)
        s3.delete_object.assert_called_once_with(Bucket="test-output", Key="output/a", VersionId="null")
        transcribe.delete_transcription_job.assert_called_once_with(TranscriptionJobName="d62d-test-a")

    def test_multipart_list_has_no_unsupported_prefix_condition(self):
        statements = aws.template()["Resources"]["WatchdogRole"]["Properties"]["Policies"][0]["PolicyDocument"]["Statement"]
        matches = [s for s in statements if s["Action"] == "s3:ListBucketMultipartUploads"]
        self.assertEqual(len(matches), 2)
        self.assertTrue(all("Condition" not in s for s in matches))

    def test_local_cleanup_attempts_output_after_input_listing_failure(self):
        from unittest.mock import Mock
        c = aws.new_config("123456789012", "arn:aws:iam::123456789012:role/TestOwner", "a1b2c3d4")
        c["outputs"] = {"InputBucket": "test-input", "OutputBucket": "test-output"}
        api = Mock()
        def call(*args):
            if "test-input" in args: raise aws.AwsError("AccessDenied")
            return {}
        api.call.side_effect = call
        result = aws.cleanup(api, c)
        self.assertEqual(result["status"], "PENDING")
        self.assertTrue(any("test-output" in call.args for call in api.call.call_args_list))


def fixture_config():
    c = aws.new_config("123456789012", "arn:aws:iam::123456789012:role/TestOwner", "a1b2c3d4")
    c["write_deadline"] = "2999-01-01T00:00:00+00:00"
    c["template_sha256"] = aws.digest_json(aws.template())
    c["outputs"] = {kind + "Bucket": "dora-62d-123456789012-a1b2c3d4-" + kind.lower() for kind in ("Input", "Output")}
    c["outputs"].update({kind + "Key": "arn:aws:kms:eu-central-1:123456789012:key/" + number * 8 + "-0000-0000-0000-000000000000" for kind, number in (("Input", "1"), ("Output", "2"))})
    c["outputs"].update({name: "arn:aws:iam::123456789012:role/dora-62d-a1b2c3d4-" + suffix for name, suffix in (("DataRole", "data"), ("OperatorRole", "operator"))})
    c["outputs"].update(Watchdog="dora-62d-a1b2c3d4-watchdog", Schedule="dora-62d-a1b2c3d4-expiry")
    return c


class ResourceApi:
    """Synthetic AWS-shaped replies; each fault changes one actual deployed property."""
    def __init__(self, c, fault=None):
        self.c, self.fault = c, fault
        self.r = aws.resolved_template(c)["Resources"]

    def function_source(self, reply):
        return (aws.HERE / "watchdog.py").read_text(encoding="utf-8")

    def call(self, service, op, *args):
        r = self.r
        if service == "cloudformation": return {"TemplateBody": aws.template()}
        if service == "iam":
            name = args[args.index("--role-name") + 1]
            if name == "TestOwner":
                if op == "list-attached-role-policies": return {"AttachedPolicies": [{"PolicyArn": "bad"}] if self.fault == "proof-extra-policy" else []}
                if op == "list-role-policies": return {"PolicyNames": ["AwsInlinePolicy"]}
                if op == "get-role-policy": return {"PolicyDocument": aws.proof_policy(self.c)}
            logical = {"data": "DataRole", "operator": "OperatorRole", "watchdog": "WatchdogRole"}[name.split("-")[-1]]
            p = r[logical]["Properties"]
            if op == "get-role": return {"Role": {"Arn": "arn:aws:iam::123456789012:role/" + name, "AssumeRolePolicyDocument": p["AssumeRolePolicyDocument"], "MaxSessionDuration": 3600}}
            if op == "list-attached-role-policies": return {"AttachedPolicies": [{"PolicyArn": "bad"}] if self.fault == "role-extra-policy" else []}
            if op == "list-role-policies": return {"PolicyNames": [p["Policies"][0]["PolicyName"]]}
            if op == "get-role-policy": return {"PolicyDocument": p["Policies"][0]["PolicyDocument"]}
        if service == "kms":
            kind = "Input" if args[1] == self.c["outputs"]["InputKey"] else "Output"
            if op == "get-key-policy": return {"Policy": json.dumps(r[kind + "Key"]["Properties"]["KeyPolicy"])}
            if op == "list-grants": return {"Grants": [{}] if self.fault == "key-grant" else []}
            if op == "get-key-rotation-status": return {"KeyRotationEnabled": True}
        if service == "s3api":
            kind = "Input" if args[1] == self.c["outputs"]["InputBucket"] else "Output"
            if op == "get-bucket-policy":
                value = copy.deepcopy(r[kind + "BucketPolicy"]["Properties"]["PolicyDocument"])
                if self.fault == "bucket-policy": value["Statement"].pop()
                return {"Policy": json.dumps(value)}
            if op == "get-bucket-lifecycle-configuration":
                value = copy.deepcopy(r[kind + "Bucket"]["Properties"]["LifecycleConfiguration"])
                for rule in value["Rules"]: rule["Expiration"] = {"Days": rule.pop("ExpirationInDays")}
                if self.fault == "lifecycle": value["Rules"][0]["Expiration"]["Days"] = 7
                return value
        if service == "lambda":
            p = r["Watchdog"]["Properties"]
            if op == "get-function":
                value = {k: copy.deepcopy(p[k]) for k in ("Runtime", "Handler", "MemorySize", "Timeout", "Role", "Environment")}
                if self.fault == "watchdog-env": value["Environment"]["Variables"]["INPUT_BUCKET"] = "other"
                value.update(State="Active", LastUpdateStatus="Successful", LoggingConfig={"LogFormat": "Text"})
                return {"Configuration": value}
            if op == "get-function-concurrency": return {}
            if op == "get-policy":
                return {"Policy": json.dumps({"Version": "2012-10-17", "Id": "default", "Statement": [{"Sid": "generated", "Effect": "Allow", "Principal": {"Service": "events.amazonaws.com"}, "Action": "lambda:InvokeFunction", "Resource": "arn:aws:lambda:eu-central-1:123456789012:function:dora-62d-a1b2c3d4-watchdog", "Condition": {"StringEquals": {"AWS:SourceAccount": "123456789012"}, "ArnLike": {"AWS:SourceArn": "arn:aws:events:eu-central-1:123456789012:rule/dora-62d-a1b2c3d4-expiry"}}}]})}
        if service == "events":
            p = r["Schedule"]["Properties"]
            if op == "describe-rule": return {"State": p["State"], "ScheduleExpression": p["ScheduleExpression"]}
            if op == "list-targets-by-rule": return {"Targets": [] if self.fault == "event-target" else p["Targets"]}
        raise AssertionError((service, op))


class PreflightApi(ResourceApi):
    def __init__(self, c):
        super().__init__(c)
        self.evidence = []

    def call(self, service, op, *args):
        if op == "get-caller-identity": return {"Account": "123456789012", "Arn": "arn:aws:sts::123456789012:assumed-role/TestOwner/test"}
        if op == "describe-organization": return {}
        if op == "describe-effective-policy": return {"EffectivePolicy": {"TargetId": "123456789012", "PolicyContent": '{"services":{"transcribe":{"opt_out_policy":"optOut"}}}'}}
        if op == "get-bucket-location": return {"LocationConstraint": "eu-central-1"}
        if op == "get-public-access-block": return {"PublicAccessBlockConfiguration": {k: True for k in ("BlockPublicAcls", "IgnorePublicAcls", "BlockPublicPolicy", "RestrictPublicBuckets")}}
        if op == "get-bucket-versioning": return {}
        if op == "get-bucket-ownership-controls": return {"OwnershipControls": {"Rules": [{"ObjectOwnership": "BucketOwnerEnforced"}]}}
        if op == "get-bucket-policy-status": return {"PolicyStatus": {"IsPublic": False}}
        if op == "get-bucket-replication": raise aws.AwsError("ReplicationConfigurationNotFoundError")
        if op == "get-object-lock-configuration": raise aws.AwsError("ObjectLockConfigurationNotFoundError")
        if op == "get-bucket-encryption":
            kind = "Input" if args[1].endswith("-input") else "Output"
            return {"ServerSideEncryptionConfiguration": {"Rules": [{"ApplyServerSideEncryptionByDefault": {"SSEAlgorithm": "aws:kms", "KMSMasterKeyID": self.c["outputs"][kind + "Key"]}, "BucketKeyEnabled": False}]}}
        if op == "describe-key": return {"KeyMetadata": {"Arn": args[1], "KeyState": "Enabled", "KeyManager": "CUSTOMER", "KeySpec": "SYMMETRIC_DEFAULT", "KeyUsage": "ENCRYPT_DECRYPT", "MultiRegion": False}}
        return super().call(service, op, *args)


class RuntimeReadApi:
    """Only accepts the exact read-only runtime probes; never accepts speech/write calls."""
    def __init__(self, c, denied=None):
        self.c, self.denied, self.evidence, self.checked = c, denied, [], []

    def call(self, service, op, *args):
        self.evidence.append({"operation": [service, op]})
        if op == "get-caller-identity":
            role = "OtherRole" if self.denied == "wrong-identity" else "dora-62d-a1b2c3d4-operator"
            return {"Account": "123456789012", "Arn": "arn:aws:sts::123456789012:assumed-role/" + role + "/test"}
        assert (service, op) in (("s3api", "head-bucket"), ("kms", "describe-key"))
        suffix = "Bucket" if op == "head-bucket" else "Key"
        kind = next(kind for kind in ("Input", "Output") if args[1] == self.c["outputs"][kind + suffix])
        assert args == (("--bucket", self.c["outputs"][kind + suffix], "--expected-bucket-owner", "123456789012") if suffix == "Bucket" else ("--key-id", self.c["outputs"][kind + suffix]))
        self.checked.append((op, kind))
        if self.denied == (op, kind): raise aws.AwsError("AccessDenied")
        if op == "head-bucket": return {"BucketRegion": "eu-central-1"}
        return {"KeyMetadata": {"Arn": "wrong" if self.denied == "wrong-key" else args[1], "KeyState": "Enabled", "KeyManager": "CUSTOMER", "KeySpec": "SYMMETRIC_DEFAULT", "KeyUsage": "ENCRYPT_DECRYPT", "MultiRegion": False}}


class ProbeApi:
    def __init__(self, c):
        self.c, self.objects, self.uploads = c, {}, {}
        self.sizes = {}
        self.fail_delete = False
        self.runtime = self

    def call(self, service, op, *args):
        if service == "transcribe": return {}
        if service == "lambda":
            if not self.fail_delete:
                self.runtime.objects.clear(); self.runtime.uploads.clear()
            Path(args[-1]).write_text('{"status":"PROBE_DELETION_ATTEMPTED_REQUIRES_READBACK"}')
            return {"StatusCode": 200}
        bucket = args[args.index("--bucket") + 1]
        if op == "put-object":
            self.objects[bucket] = args[args.index("--key") + 1]
            self.sizes[bucket] = len(Path(args[args.index("--body") + 1]).read_bytes())
            return {}
        if op == "head-object": return {"ContentLength": self.sizes[bucket], "ServerSideEncryption": "aws:kms", "SSEKMSKeyId": self.c["outputs"]["InputKey" if bucket.endswith("input") else "OutputKey"]}
        if op == "create-multipart-upload": self.uploads[bucket] = args[args.index("--key") + 1]; return {"UploadId": "synthetic"}
        if op == "list-object-versions": return {"Versions": [{"Key": self.objects[bucket], "VersionId": "null"}]} if bucket in self.objects else {}
        if op == "list-multipart-uploads": return {"Uploads": [{"Key": self.uploads[bucket], "UploadId": "synthetic"}]} if bucket in self.uploads else {}
        raise AssertionError((service, op))


if __name__ == "__main__":
    unittest.main()
