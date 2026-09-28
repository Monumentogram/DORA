"""Offline fault tests. All account/ARN values are synthetic."""
import copy
import json
import tempfile
import unittest
from pathlib import Path
import aws_prepare as aws


class SafetyTests(unittest.TestCase):
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
            with self.assertRaisesRegex(aws.AwsError, "^AccessDenied$"):
                api.call("sts", "get-caller-identity")
            self.assertNotIn("secret-sensitive-info", json.dumps(api.evidence))

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


if __name__ == "__main__":
    unittest.main()
