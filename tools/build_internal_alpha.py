"""Build and verify an owner-only APK; secrets stay outside Git and Gradle."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[1]
ALIAS = "dora-internal-alpha"


def signing_material(directory: Path, repository: Path) -> tuple[Path, str]:
    directory = directory.resolve()
    repository = repository.resolve()
    files = [directory / name for name in ("alpha.p12", "password.txt", "certificate.sha256")]
    resolved = [directory, *(file.resolve() for file in files)]
    if any(path.is_relative_to(repository) for path in resolved) or any(
        (parent / ".git").exists()
        for path in resolved
        for parent in (path, *path.parents)
    ):
        raise ValueError("Alpha signing material must remain outside the repository")
    if not all(file.is_file() and file.stat().st_size > 0 for file in files):
        raise ValueError("Missing Alpha signing material: alpha.p12, password.txt, certificate.sha256")
    expected = files[2].read_text(encoding="ascii").strip().replace(":", "").lower()
    if not re.fullmatch(r"[0-9a-f]{64}", expected):
        raise ValueError("Invalid Alpha certificate SHA-256 pin")
    return directory, expected


def verify_certificate(output: str, expected: str) -> None:
    fingerprints = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})", output)
    if [value.lower() for value in fingerprints] != [expected]:
        raise ValueError("APK signing certificate does not match the single expected Alpha signer")


def run(command: list[str], *, private: bool = False) -> str:
    result = subprocess.run(command, cwd=ROOT / "android", capture_output=True, text=True)
    if result.returncode:
        # Signing tool diagnostics may contain private paths; never echo them.
        if not private:
            print(result.stdout, end="")
            print(result.stderr, end="", file=sys.stderr)
        raise ValueError("Alpha signing/verification failed" if private else "Alpha build/tool failed")
    return result.stdout


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--signing-dir", type=Path, required=True)
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--offline", action="store_true")
    parser.add_argument("--upgrade-test", action="store_true", help="Disposable code 3; not a product release")
    args = parser.parse_args()
    directory, expected = signing_material(args.signing_dir, ROOT)
    # The certificate is pinned by the public decision, not just by local configuration.
    contract = json.loads((ROOT / "docs/contracts/DORA_ALPHA_IDENTITY_SIGNING_INSTALL_V0_1.json").read_text(encoding="utf-8"))
    if expected != contract["signing"]["certificate_sha256"].replace(":", "").lower():
        raise ValueError("Local certificate pin differs from the frozen Alpha contract")
    sdk = args.sdk.resolve()
    build_tools = sdk / "build-tools/36.0.0"
    suffix = ".bat" if os.name == "nt" else ""
    signer = str(build_tools / ("apksigner" + suffix))
    wrapper = str(ROOT / "android" / ("gradlew" + suffix))
    command = [wrapper, "--no-daemon", "--no-configuration-cache", "--console=plain", ":app:assembleRelease"]
    if args.offline:
        command.append("--offline")
    if args.upgrade_test:
        command.append("-PdoraAlphaUpgradeTest=true")
    print(run(command), end="")
    unsigned = ROOT / "android/app/build/outputs/apk/release/app-release-unsigned.apk"
    destination = ROOT / "android/app/build/outputs/apk/internal"
    destination.mkdir(parents=True, exist_ok=True)
    version = "0.1.0-alpha.1-upgrade-test" if args.upgrade_test else "0.1.0-alpha.1"
    version_code = 3 if args.upgrade_test else 2
    output = destination / f"dora-{version}-vc{version_code}.apk"
    with tempfile.TemporaryDirectory(dir=destination) as temporary:
        signed = Path(temporary) / "signed.apk"
        run([
            signer, "sign", "--ks", str(directory / "alpha.p12"), "--ks-key-alias", ALIAS,
            "--ks-pass", f"file:{directory / 'password.txt'}",
            "--v4-signing-enabled", "false", "--out", str(signed), str(unsigned),
        ], private=True)
        verification = run([signer, "verify", "--verbose", "--print-certs", str(signed)], private=True)
        verify_certificate(verification, expected)
        aapt = str(build_tools / ("aapt.exe" if os.name == "nt" else "aapt"))
        badging = run([aapt, "dump", "badging", str(signed)])
        required = f"package: name='com.monumentogram.dora' versionCode='{version_code}' versionName='{version}'"
        if required not in badging:
            raise ValueError("Signed APK package/version differs from the requested Alpha identity")
        run([str(build_tools / ("zipalign.exe" if os.name == "nt" else "zipalign")), "-c", "-P", "16", "4", str(signed)])
        digest = hashlib.sha256(signed.read_bytes()).hexdigest()
        receipt = {
            "artifact": output.name, "sha256": digest, "application_id": "com.monumentogram.dora",
            "version_code": version_code, "version_name": version, "certificate_sha256": expected,
            "distribution": "OWNER_ONLY_CLOSED_INTERNAL_ALPHA", "upgrade_test": args.upgrade_test,
        }
        staged_receipt = Path(temporary) / "receipt.json"
        staged_receipt.write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
        signed.replace(output)
        staged_receipt.replace(output.with_suffix(".json"))
    print(json.dumps(receipt, indent=2))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ValueError as error:
        print(f"FAIL: {error}. Do not install existing output; this invocation did not complete.", file=sys.stderr)
        sys.exit(1)
    except OSError:
        # Do not print exception values that may contain private local paths.
        print("FAIL: Internal Alpha requires accessible owner-local signing material, SDK 36 and writable output. Do not install existing output; this invocation did not complete.", file=sys.stderr)
        sys.exit(1)
