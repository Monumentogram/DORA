import json
import sqlite3
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

import boto3
from botocore.stub import Stubber

from deployment.alpha.render_config import build_config, write_private
from deployment.alpha.health import ledger_healthy, main as health_main


class AlphaOperationsTests(unittest.TestCase):
    def outputs(self):
        values = {"AccountId": "000000000000", "Region": "eu-central-1",
                  "InputBucket": "fixture-input", "OutputBucket": "fixture-output",
                  "InputKeyArn": "arn:aws:kms:eu-central-1:000000000000:key/11111111-1111-4111-8111-111111111111",
                  "OutputKeyArn": "arn:aws:kms:eu-central-1:000000000000:key/22222222-2222-4222-8222-222222222222"}
        return [{"OutputKey": k, "OutputValue": v} for k, v in values.items()]

    def test_render_disabled_and_refuse_invalid_custody(self):
        values = build_config(self.outputs(), "fixture-owner")
        self.assertFalse(values["live_enabled"])
        self.assertEqual("/var/lib/dora/custody.key", values["custody_key_path"])
        for field in ("OutputBucket", "OutputKeyArn", "Region"):
            rows = self.outputs()
            replacement = {"OutputBucket": "fixture-input", "OutputKeyArn": rows[4]["OutputValue"], "Region": "us-east-1"}[field]
            next(r for r in rows if r["OutputKey"] == field)["OutputValue"] = replacement
            with self.subTest(field=field), self.assertRaises(ValueError):
                build_config(rows, "fixture-owner")

    def test_private_writer_never_overwrites_or_writes_in_repository(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "private.json"
            write_private(path, {"live_enabled": False})
            with self.assertRaises(FileExistsError):
                write_private(path, {"live_enabled": True})
            self.assertFalse(json.loads(path.read_text())["live_enabled"])
        with self.assertRaises(ValueError):
            write_private(Path(__file__).parent / "forbidden.private.json", {})

    def test_health_missing_ledger_does_not_create_it(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "missing.sqlite"
            self.assertFalse(ledger_healthy(path, 100000))
            self.assertFalse(path.exists())

    def test_health_emits_only_aggregate_failure_for_service_mount_disk_or_ledger_failure(self):
        for failed in (None, "service", "mount", "disk", "ledger"):
            client = boto3.client("cloudwatch", region_name="eu-central-1",
                                  aws_access_key_id="fixture", aws_secret_access_key="fixture")
            expected = {"Namespace": "DORA/Alpha", "MetricData": [{"MetricName": "CleanupHealthy",
                "Dimensions": [{"Name": "Stack", "Value": "fixture-stack"}],
                "Value": int(failed is None), "Unit": "Count"}]}
            with self.subTest(failed=failed), Stubber(client) as stub:
                stub.add_response("put_metric_data", {}, expected)
                with patch("sys.argv", ["health", "--stack", "fixture-stack"]), \
                     patch("boto3.client", return_value=client), \
                     patch("deployment.alpha.health.subprocess.run", return_value=SimpleNamespace(returncode=int(failed == "service"))), \
                     patch("deployment.alpha.health.os.path.ismount", return_value=failed != "mount"), \
                     patch("deployment.alpha.health.shutil.disk_usage", return_value=SimpleNamespace(free=0 if failed == "disk" else 1024**3)), \
                     patch("deployment.alpha.health.ledger_healthy", return_value=failed != "ledger"):
                    health_main()
                stub.assert_no_pending_responses()

    def test_health_alerts_before_deadline_for_chunks_or_pending_provider_cleanup(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "state.sqlite"
            db = sqlite3.connect(path)
            db.executescript("CREATE TABLE jobs(id TEXT, payload TEXT); CREATE TABLE attempts(job TEXT,payload TEXT); CREATE TABLE chunks(job TEXT,ciphertext BLOB);")
            db.execute("INSERT INTO jobs VALUES(?,?)", ("fixture", json.dumps({"deadline": 103600})))
            db.commit()
            self.assertTrue(ledger_healthy(path, 100000))
            db.execute("INSERT INTO chunks VALUES(?,?)", ("fixture", b"ciphertext")); db.commit()
            self.assertTrue(ledger_healthy(path, 99999))
            self.assertFalse(ledger_healthy(path, 100000))
            db.execute("DELETE FROM chunks")
            db.execute("INSERT INTO attempts VALUES(?,?)", ("fixture", json.dumps({"cleanup": True}))); db.commit()
            self.assertFalse(ledger_healthy(path, 100000))
            db.execute("UPDATE attempts SET payload=?", (json.dumps({"cleanup": False}),)); db.commit()
            self.assertTrue(ledger_healthy(path, 200000))
            db.close()
