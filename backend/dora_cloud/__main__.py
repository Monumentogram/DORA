"""Operator entry points. None can create AWS infrastructure or synthetic jobs."""
import argparse
import base64
import json
import os
from pathlib import Path
import sys
import time

from cryptography.hazmat.primitives import serialization

from .auth import Auth
from .aws_adapter import AmazonProvider, ProviderError
from .config import LiveConfig
from .http_server import create_app
from .preflight import verify_live
from .service import Policy, Service
from .store import Store


def custody_key(config):
    path = Path(config.custody_key_path)
    if os.name != "nt" and path.stat().st_mode & 0o077:
        raise ValueError("custody key permissions")
    key = path.read_bytes()
    if len(key) != 32:
        raise ValueError("custody key size")
    return key


def clients(config):
    import boto3
    from botocore.config import Config
    session = boto3.Session(region_name="eu-central-1")
    sdk = Config(region_name="eu-central-1", connect_timeout=3, read_timeout=10,
                 retries={"total_max_attempts": 1, "mode": "standard"})
    return {name: session.client(name, config=sdk) for name in ("sts", "organizations", "s3", "kms", "transcribe")}


def main(argv=None):
    parser = argparse.ArgumentParser(description="DORA owner-only Cloud control plane")
    parser.add_argument("--config", required=True, help="private operator JSON; never commit it")
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("init-key")
    invite = commands.add_parser("invite")
    invite.add_argument("--invitation", required=True, help="public Android invitation export JSON")
    invite.add_argument("--expires-at", required=True, type=int, help="finite epoch seconds")
    revoke = commands.add_parser("revoke")
    revoke.add_argument("--installation", required=True)
    commands.add_parser("preflight")
    serve = commands.add_parser("serve")
    serve.add_argument("--port", type=int, default=8080)
    watchdog = commands.add_parser("watchdog")
    watchdog.add_argument("--once", action="store_true")
    args = parser.parse_args(argv)
    store = None
    try:
        config = LiveConfig.load(args.config)
        if args.command in ("serve", "preflight") and not config.live_enabled:
            raise ProviderError("LIVE_DISABLED")
        if args.command == "init-key":
            path = Path(config.custody_key_path)
            path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
            fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, "wb") as output:
                output.write(os.urandom(32))
                output.flush()
                os.fsync(output.fileno())
            print("CUSTODY_KEY_CREATED")
            return 0
        if args.command in ("invite", "revoke"):
            store = Store(config.ledger_path, custody_key(config))
            store.bind_namespace(config.namespace())
            auth = Auth(store)
            if args.command == "invite":
                invitation = json.loads(Path(args.invitation).read_text(encoding="utf-8"))
                if set(invitation) != {"installation_id", "public_key_spki_base64"}:
                    raise ValueError("invitation fields")
                key = serialization.load_der_public_key(base64.b64decode(invitation["public_key_spki_base64"], validate=True))
                public = key.public_bytes(serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo)
                auth.invite(invitation["installation_id"], config.owner_id, public, args.expires_at)
                print("INSTALLATION_INVITED")
            else:
                auth.revoke(args.installation)
                print("INSTALLATION_REVOKED")
            return 0
        aws = clients(config)
        verified_at = [0.0]

        def admission():
            current = LiveConfig.load(args.config)
            if not current.live_enabled or current != config:
                raise ProviderError("LIVE_CONFIGURATION_CHANGED")
            if time.monotonic() - verified_at[0] >= 60 or verified_at[0] == 0:
                verify_live(config, aws["sts"], aws["organizations"], aws["s3"], aws["kms"])
                verified_at[0] = time.monotonic()

        if args.command == "preflight":
            admission()
            print("LIVE_PREFLIGHT_VERIFIED")
            return 0
        store = Store(config.ledger_path, custody_key(config))
        store.bind_namespace(config.namespace())
        provider = AmazonProvider(config, aws["s3"], aws["transcribe"], admission)
        policy = Policy(config.daily_reserve_usd, config.per_minute_reserve_usd, config.ancillary_per_attempt_usd)
        service = Service(store, provider, policy)
        if args.command == "watchdog":
            # Cleanup must remain operable if live admission is disabled or drifts.
            # sweep only reconciles existing intent and never initiates paid work.
            while True:
                service.sweep()
                if args.once:
                    return 0
                time.sleep(15)
        admission()
        service.sweep()  # Restore-before-serve: fence revoked/expired persisted work.
        import uvicorn
        uvicorn.run(create_app(service, Auth(store)), host="127.0.0.1", port=args.port,
                    proxy_headers=True, forwarded_allow_ips="127.0.0.1", access_log=False,
                    workers=1, log_level="warning", limit_concurrency=16, timeout_keep_alive=5)
        return 0
    except ProviderError as exc:
        print(exc.code, file=sys.stderr)
        return 2
    except Exception:
        print("OPERATOR_CONFIGURATION_INVALID", file=sys.stderr)
        return 2
    finally:
        if store:
            store.close()


if __name__ == "__main__":
    raise SystemExit(main())
