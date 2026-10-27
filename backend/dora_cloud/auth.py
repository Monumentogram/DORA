"""Manual invitation and body/path-bound installation proof; no self-enrollment."""
import base64
import hashlib
import secrets
import time
import uuid

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec

from .errors import ServiceError


def canonical_uuid(value):
    try:
        return str(uuid.UUID(value)) == value
    except (ValueError, TypeError, AttributeError):
        return False


class Auth:
    def __init__(self, store, clock=time.time):
        self.store, self.clock = store, clock

    def invite(self, installation, owner, public_key, expires):
        key = serialization.load_pem_public_key(public_key)
        if (not canonical_uuid(installation) or not owner or len(owner) > 128 or
                not isinstance(key, ec.EllipticCurvePublicKey) or
                not isinstance(key.curve, ec.SECP256R1) or
                type(expires) is not int or expires <= self.clock()):
            raise ValueError("invalid finite invitation")
        with self.store.serialized():
            existing = self.store.db.execute("SELECT * FROM installations WHERE id=?", (installation,)).fetchone()
            if existing or self.store.db.execute("SELECT 1 FROM tombstones WHERE id=?", (installation,)).fetchone():
                raise ValueError("installation already bound; cannot replace identity")
            self.store.db.execute("INSERT INTO installations(id,owner,public_key,expires) VALUES(?,?,?,?)",
                                  (installation, owner, public_key, expires))

    def revoke(self, installation):
        with self.store.serialized(), self.store.transaction():
            self.store.db.execute("UPDATE installations SET revoked=1 WHERE id=?", (installation,))
            self.store.db.execute("DELETE FROM sessions WHERE installation=?", (installation,))

    def verify(self, method, path, body, headers, session_required=True):
        now = int(self.clock())
        install = headers.get("x-dora-installation", "")
        stamp = headers.get("x-dora-time", "")
        nonce = headers.get("x-dora-nonce", "")
        try:
            if not canonical_uuid(install) or not canonical_uuid(nonce) or str(int(stamp)) != stamp or abs(int(stamp)-now) > 60:
                raise ValueError()
            row = self.store.db.execute("SELECT * FROM installations WHERE id=?", (install,)).fetchone()
            if not row or row["revoked"] or row["expires"] <= now:
                raise ValueError()
            key = serialization.load_pem_public_key(row["public_key"])
            signature = base64.b64decode(headers.get("x-dora-signature", ""), validate=True)
            message = f"DORA-ASR-V1\n{method}\n{path}\n{stamp}\n{nonce}\n{hashlib.sha256(body).hexdigest()}".encode()
            key.verify(signature, message, ec.ECDSA(hashes.SHA256()))
        except (ValueError, TypeError, InvalidSignature):
            raise ServiceError("AUTH_INVALID", 401) from None
        if self.store.db.execute("SELECT 1 FROM nonces WHERE installation=? AND nonce=?", (install, nonce)).fetchone():
            raise ServiceError("AUTH_REPLAY", 401)
        if session_required:
            header = headers.get("authorization", "")
            if not header.startswith("Bearer ") or len(header) > 256:
                raise ServiceError("SESSION_INVALID", 401)
            token_hash = hashlib.sha256(header[7:].encode()).hexdigest()
            token = self.store.db.execute("SELECT * FROM sessions WHERE token_hash=?", (token_hash,)).fetchone()
            if not token or token["installation"] != install or token["expires"] <= now:
                raise ServiceError("SESSION_INVALID", 401)
        self.store.db.execute("INSERT INTO nonces VALUES(?,?,?)", (install, nonce, now + 121))
        return dict(row)

    def session(self, principal):
        expires = min(int(self.clock()) + 900, principal["expires"])
        token = secrets.token_urlsafe(32)
        with self.store.transaction():
            self.store.db.execute("DELETE FROM sessions WHERE installation=?", (principal["id"],))
            self.store.db.execute("INSERT INTO sessions VALUES(?,?,?)",
                                  (hashlib.sha256(token.encode()).hexdigest(), principal["id"], expires))
        return {"access_token": token, "expires_at": expires}
