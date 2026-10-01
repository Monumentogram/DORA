"""Small offline 7.4 contract checker; no runtime auth, crypto or provider calls."""
from pathlib import Path
import json

ROOT = Path(__file__).resolve().parents[1]
BASE = "1b5460d6e559292c02681e0251f5e5ef18269829"
CONTRACT = "docs/contracts/DORA_SECURITY_IDENTITY_ARCHITECTURE_V0_1.json"
DOCUMENT = "docs/security/DORA_SECURITY_IDENTITY_ARCHITECTURE_V0_1.md"
RESULT = "PASS / SECURITY_IDENTITY_ARCHITECTURE_FROZEN_FOR_STAGE8"
PATHS = frozenset({
    CONTRACT, DOCUMENT,
    "tools/validate_security_identity_contract.py", "tools/test_security_identity_contract.py",
    "tools/validate_alpha_release.py", "tools/test_validate_alpha_release.py",
    "tools/validate_poc_recovery_governance.py", "tools/test_poc_recovery_i3_governance.py",
    ".github/workflows/android-ci.yml",
    "docs/DORA_MVP1_STAGE_STATUS.md", "docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md",
    "docs/evidence/security-7.4-review-v0.1.json", "docs/evidence/security-7.4-local-v0.1.json",
})
STEP = """      - name: Validate Security and Identity architecture contract
        run: |
          python3 tools/validate_security_identity_contract.py
          python3 -m unittest discover -s tools -p test_security_identity_contract.py

"""
EXPECTED = {
    "privileged_secrets_in_apk": False,
    "google_password_storage": False,
    "original_audio_encryption_required": True,
    "database_encryption": "SQLCIPHER",
    "local_keys_keystore_bound": True,
    "provider_credentials_location": "SERVER_ONLY",
    "offline_requires_cloud_identity": False,
    "offline_requires_network": False,
    "offline_requires_gms": False,
    "cloud_identity": "INVITED_INSTALLATION_PROOF_OF_KEY",
    "google_sign_in": "NOT_SELECTED",
    "stage8": "NOT_STARTED",
    "runtime": "NOT_IMPLEMENTED",
    "recovery_prerequisite": "SATISFIED",
    "group_c": "IN_PROGRESS",
    "internal_sensitive_content_plaintext_persistence": False,
    "plaintext_export_exception": "EXPLICIT_DEC017_BOUNDED_TEMP_ONLY",
    "request_proof": "RFC9421_ECDSA_P256_SHA256_RFC9530_SHA256",
    "backup_sensitive_data": False,
    "production_certification": False,
    "real_credentials_accessed": False,
    "real_auth_executed": False,
    "real_audio_used": False,
    "provider_called": False,
    "new_signed_release": False,
    "app_lock": "OPTIONAL_BIOMETRIC_PROMPT_DEVICE_CREDENTIAL",
    "app_lock_grace_seconds": 0,
    "hardware_keystore": "PREFERRED_SOFTWARE_KEYSTORE_ALLOWED",
    "strongbox_required": False,
    "access_ttl_seconds": 300,
    "refresh_idle_seconds": 604800,
    "session_absolute_seconds": 2592000,
    "proof_nonce_ttl_seconds": 60,
    "application_id": "com.monumentogram.dora",
    "version_name": "0.1.0-alpha.2",
    "version_code": 4
}
SECTION_IDS = ["scope","boundaries","identity","sessions","lock","keys","encryption","recovery","secrets","deletion","failures","threats","admission","references"]
SECRET_TYPES = ["Local KEK","Audio/manifest/checkpoint keysets","SQLCipher passphrase","Installation proof private key","DORA access credential","DORA refresh credential","Invitation/recovery capability","Scoped upload authority","Future Google ID token","Provider/AWS/ASR/database/OAuth-client/service-account/backend-signing secrets","Server encryption keys","APK signing key"]


def require(condition, message):
    if not condition:
        raise ValueError(message)


def validate_contract(contract):
    require(contract.get("schema_version") == 1 and contract.get("task") == "7.4"
            and contract.get("baseline") == BASE and contract.get("result") == RESULT,
            "7.4 identity/result mismatch")
    actual = contract.get("invariants", {})
    require(set(actual) == set(EXPECTED), "Security invariant inventory differs")
    for name, expected in EXPECTED.items():
        require(type(actual[name]) is type(expected) and actual[name] == expected,
                "Security invariant mismatch: " + name)
    require(len(contract.get("actors", [])) == 7, "Actor boundary missing")
    require([s.get("id") for s in contract.get("sections", [])] == SECTION_IDS,
            "Architecture decision missing or reordered")
    require(all(s.get("title") and s.get("text", "").strip() for s in contract["sections"]),
            "Empty architecture decision")
    matrix = contract.get("secret_locations", [])
    require([s.get("type") for s in matrix] == SECRET_TYPES, "Secret location missing")
    require(all(s.get("logs") == "PROHIBITED" and s.get("android") and s.get("server")
                and s.get("lifecycle") for s in matrix), "Secret location/logging violation")
    require(all(matrix[i]["android"] == "PROHIBITED" for i in (9, 10, 11)),
            "Privileged secret on Android")
    require(all(matrix[i]["server"] == "PROHIBITED" for i in (0, 1, 2)),
            "Local encryption key exported to server")


def render(c):
    text = "# DORA Security & Identity Architecture v0.1\n\n" + c["result"] + "\n\n" + c["publication"] + "\n\n"
    text += "## Machine-checked invariants\n\n| Invariant | Frozen value |\n| --- | --- |\n"
    text += "\n".join("| `" + k + "` | `" + json.dumps(v) + "` |" for k, v in c["invariants"].items()) + "\n\n"
    text += "## Actors\n\n" + "\n".join("- " + x for x in c["actors"]) + "\n\n"
    text += "## Secret-location matrix\n\n| Type | Android | Server/custody | Logs | Rotation/revocation |\n| --- | --- | --- | --- | --- |\n"
    text += "\n".join("| " + " | ".join(x[k] for k in ("type", "android", "server", "logs", "lifecycle")) + " |" for x in c["secret_locations"]) + "\n\n"
    text += "\n".join("## " + s["title"] + "\n\n" + s["text"] + "\n" for s in c["sections"])
    return text


def validate_paths(paths):
    require(set(paths) <= PATHS, "7.4 bounded path inventory violation")


def validate_status(root):
    for name in ("DORA_MVP1_STAGE_STATUS.md", "DORA_MVP1_IMPLEMENTATION_BACKLOG.md"):
        latest = (root / "docs" / name).read_text(encoding="utf-8").split("\n## ", 1)[0]
        for text in (RESULT, "Stage 8 = NOT_STARTED", "Recovery integration prerequisite = SATISFIED", "Group C = IN_PROGRESS"):
            require(text in latest, "Latest status header contradicts 7.4: " + text)


def main():
    contract = json.loads((ROOT / CONTRACT).read_text(encoding="utf-8"))
    validate_contract(contract)
    require((ROOT / DOCUMENT).read_text(encoding="utf-8") == render(contract),
            "Human/machine contract drift")
    validate_status(ROOT)
    print("PASS 7.4 security architecture invariants and exact human projection; runtime NOT_IMPLEMENTED")


if __name__ == "__main__":
    main()
