import base64
import json
import subprocess
import sys
import tempfile
import time
import unittest
import uuid
from pathlib import Path

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec

from dora_cloud.store import Store
from test_aws_adapter import config


class OperatorTests(unittest.TestCase):
    def test_cli_key_creation_and_public_invitation_are_local_and_immutable(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            cfg = config(live_enabled=False, custody_key_path=str(folder / "custody.key"), ledger_path=str(folder / "state.sqlite"))
            config_file = folder / "config.json"
            config_file.write_text(cfg.model_dump_json(), encoding="utf-8")
            def run(*args):
                return subprocess.run([sys.executable, "-m", "dora_cloud", "--config", str(config_file), *args], capture_output=True, text=True)
            created = run("init-key")
            self.assertEqual(0, created.returncode, created.stderr)
            self.assertEqual(32, Path(cfg.custody_key_path).stat().st_size)
            self.assertNotEqual(0, run("init-key").returncode)
            identity = str(uuid.uuid4())
            key = ec.generate_private_key(ec.SECP256R1()).public_key().public_bytes(
                serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
            invitation = folder / "invite.json"
            invitation.write_text(json.dumps({"installation_id": identity, "public_key_spki_base64": base64.b64encode(key).decode()}))
            expiry = int(time.time())+3600
            invited = run("invite", "--invitation", str(invitation), "--expires-at", str(expiry))
            self.assertEqual(0, invited.returncode, invited.stderr)
            self.assertNotEqual(0, run("invite", "--invitation", str(invitation), "--expires-at", str(expiry)).returncode)
            trusted = run("trust", "--invitation", str(invitation))
            self.assertEqual(0, trusted.returncode, trusted.stderr)
            self.assertEqual("INSTALLATION_TRUSTED", trusted.stdout.strip())
            self.assertEqual(0, run("trust", "--invitation", str(invitation)).returncode)
            revoked = run("revoke", "--installation", identity)
            self.assertEqual(0, revoked.returncode, revoked.stderr)
            self.assertNotEqual(0, run("trust", "--invitation", str(invitation), "--recover-expired").returncode)
            store = Store(cfg.ledger_path, Path(cfg.custody_key_path).read_bytes())
            row = store.db.execute("SELECT * FROM installations WHERE id=?", (identity,)).fetchone()
            self.assertEqual("owner-a", row["owner"])
            self.assertEqual(1, row["revoked"])
            self.assertIsNotNone(row["trusted_at"])
            store.close()

    def test_disabled_serve_and_preflight_fail_before_aws_credentials_lookup(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp)/"config.json"
            path.write_text(config(live_enabled=False).model_dump_json())
            for command in ("serve", "preflight"):
                response = subprocess.run([sys.executable, "-m", "dora_cloud", "--config", str(path), command], capture_output=True, text=True, timeout=10)
                self.assertEqual(2, response.returncode)
                self.assertEqual("LIVE_DISABLED", response.stderr.strip())


if __name__ == "__main__":
    unittest.main()
