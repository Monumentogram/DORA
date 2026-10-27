"""Content-free operational metric; never cleans up or starts recognition."""
import argparse
from contextlib import closing
import json
import os
import shutil
import sqlite3
import subprocess
import time
from pathlib import Path


def ledger_healthy(path, now):
    try:
        with closing(sqlite3.connect(Path(path).resolve().as_uri() + "?mode=ro", uri=True, timeout=3)) as db:
            db.execute("BEGIN")
            for job_id, payload in db.execute("SELECT id,payload FROM jobs"):
                deadline = json.loads(payload)["deadline"]
                if deadline is None or deadline > now + 3600:
                    continue
                if db.execute("SELECT 1 FROM chunks WHERE job=? AND length(ciphertext)>0 LIMIT 1", (job_id,)).fetchone():
                    return False
                if any(json.loads(row[0])["cleanup"] for row in db.execute("SELECT payload FROM attempts WHERE job=?", (job_id,))):
                    return False
        return True
    except (OSError, sqlite3.Error, ValueError, KeyError, TypeError):
        return False


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--stack", required=True)
    args = parser.parse_args()
    healthy = False
    try:
        active = subprocess.run(["systemctl", "is-active", "--quiet", "dora-watchdog.service"],
                                timeout=5, check=False).returncode == 0
        healthy = (active and os.path.ismount("/var/lib/dora")
                   and shutil.disk_usage("/var/lib/dora").free > 512 * 1024 * 1024
                   and ledger_healthy("/var/lib/dora/state.sqlite", time.time()))
    except (OSError, subprocess.TimeoutExpired):
        pass
    import boto3
    from botocore.config import Config
    client = boto3.client("cloudwatch", region_name="eu-central-1", config=Config(
        connect_timeout=3, read_timeout=5, retries={"total_max_attempts": 1}))
    # No identifiers, content, errors or dynamic dimensions leave the host.
    client.put_metric_data(Namespace="DORA/Alpha", MetricData=[{
        "MetricName": "CleanupHealthy", "Dimensions": [{"Name": "Stack", "Value": args.stack}],
        "Value": int(healthy), "Unit": "Count"}])


if __name__ == "__main__":
    main()
