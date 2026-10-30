"""TEST ONLY: synthetic provider + real HTTP/proof/ledger for Android integration.

Not imported by production. No AWS SDK client or credentials are constructed.
"""
import argparse
import base64
from datetime import datetime, timedelta, timezone
import ipaddress
import json
import os
from pathlib import Path
import sys
import time

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import NameOID
import uvicorn

from dora_cloud.auth import Auth
from dora_cloud.http_server import create_app
from dora_cloud.service import Service, Policy
from dora_cloud.store import Store
from test_service import FakeAWS


class CompletingSyntheticProvider(FakeAWS):
    def status(self, attempt, job):
        if attempt["name"] in self.jobs:
            return "COMPLETED"
        return None


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--invitation", required=True)
    parser.add_argument("--state-dir", required=True)
    parser.add_argument("--port", type=int, default=8443)
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--composite", action="store_true", help="enable synthetic composite routes only")
    args = parser.parse_args()
    folder = Path(args.state_dir)
    folder.mkdir(parents=True, exist_ok=True)
    tls_key = ec.generate_private_key(ec.SECP256R1())
    subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "DORA synthetic test only")])
    cert = (x509.CertificateBuilder().subject_name(subject).issuer_name(subject).public_key(tls_key.public_key())
            .serial_number(x509.random_serial_number()).not_valid_before(datetime.now(timezone.utc)-timedelta(minutes=1))
            .not_valid_after(datetime.now(timezone.utc)+timedelta(days=1))
            .add_extension(x509.SubjectAlternativeName([x509.DNSName("localhost"), x509.IPAddress(ipaddress.ip_address("127.0.0.1")),
                x509.IPAddress(ipaddress.ip_address("10.0.2.2"))]), critical=False)
            .sign(tls_key, hashes.SHA256()))
    (folder/"tls.key").write_bytes(tls_key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
    (folder/"tls.crt").write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    custody = folder/"custody.key"
    if not custody.exists():
        custody.write_bytes(os.urandom(32))
    store = Store(folder/"state.sqlite", custody.read_bytes())
    auth = Auth(store)
    invite = json.loads(Path(args.invitation).read_text(encoding="utf-8"))
    public = serialization.load_der_public_key(base64.b64decode(invite["public_key_spki_base64"], validate=True))
    if not store.db.execute("SELECT 1 FROM installations WHERE id=?", (invite["installation_id"],)).fetchone():
        auth.invite(invite["installation_id"], "synthetic-owner", public.public_bytes(serialization.Encoding.PEM,
                    serialization.PublicFormat.SubjectPublicKeyInfo), int(time.time())+86400)
    service = Service(store, CompletingSyntheticProvider(), Policy(daily_reserve_usd="5.00", long_runs_enabled=args.composite))
    print("SYNTHETIC_ONLY_TLS_CERT=" + str((folder/"tls.crt").resolve()), flush=True)
    try:
        uvicorn.run(create_app(service, auth), host=args.host, port=args.port, access_log=False,
                    ssl_keyfile=str(folder/"tls.key"), ssl_certfile=str(folder/"tls.crt"), log_level="warning")
    finally:
        store.close()


if __name__ == "__main__":
    main()
