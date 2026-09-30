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

from alpha_release_identity import APPLICATION_ID, DISTRIBUTION, identity


ROOT = Path(__file__).resolve().parents[1]
ALIAS = "dora-internal-alpha"


def source_identity(root: Path = ROOT, expected: str | None = None) -> dict:
    def git(*arguments: str) -> str:
        result = subprocess.run(["git", "-c", f"safe.directory={root.as_posix()}", "-C", str(root), *arguments], capture_output=True, text=True)
        if result.returncode:
            raise ValueError("Cannot verify exact Git source")
        return result.stdout.strip()
    source = git("rev-parse", "HEAD")
    branch = git("branch", "--show-current")
    if not re.fullmatch(r"[0-9a-f]{40}", source) or (expected is not None and source != expected):
        raise ValueError("Git source SHA mismatch")
    if git("status", "--porcelain=v1", "--untracked-files=all"):
        raise ValueError("Product release requires a clean source tree")
    if branch != "stage/7-alpha-foundation":
        raise ValueError("Product release requires the admitted Alpha branch")
    return {"source_sha": source, "branch": branch, "source_tree_clean": True}


def verify_apk_identity(badging: str, version_code: int, version: str) -> None:
    match = re.search(r"^package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging, re.MULTILINE)
    if not match or match.groups() != (APPLICATION_ID, str(version_code), version):
        raise ValueError("Signed APK package/version differs from the requested Alpha identity")
    permissions = re.findall(r"^uses-permission[^:]*: name='([^']+)'", badging, re.MULTILINE)
    if {"android.permission.INTERNET", "android.permission.RECORD_AUDIO"}.intersection(permissions):
        raise ValueError("Unadmitted network or microphone permission")


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
    parser.add_argument("--upgrade-test", action="store_true", help="Rejected: historical non-product code 3 requires immutable 7.1 source")
    parser.add_argument("--source-sha", help="Expected exact implementation commit")
    args = parser.parse_args()
    if args.upgrade_test:
        raise ValueError("Code 3 is historical and non-product; use immutable 7.1 source only")
    source = source_identity(expected=args.source_sha)
    version_code, version = identity()
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
    command = [wrapper, "--no-daemon", "--no-configuration-cache", "--console=plain", "clean", ":app:assembleRelease"]
    if args.offline:
        command.append("--offline")
    print(run(command), end="")
    source_identity(expected=source["source_sha"])
    unsigned = ROOT / "android/app/build/outputs/apk/release/app-release-unsigned.apk"
    destination = ROOT / "android/app/build/outputs/apk/internal"
    destination.mkdir(parents=True, exist_ok=True)
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
        verify_apk_identity(badging, version_code, version)
        run([str(build_tools / ("zipalign.exe" if os.name == "nt" else "zipalign")), "-c", "-P", "16", "4", str(signed)])
        digest = hashlib.sha256(signed.read_bytes()).hexdigest()
        receipt = {
            "artifact": output.name, "sha256": digest, "size_bytes": signed.stat().st_size, "application_id": APPLICATION_ID,
            "version_code": version_code, "version_name": version, "certificate_sha256": expected,
            "distribution": DISTRIBUTION, "upgrade_test": False, **source,
            "signer_count": 1, "zip_alignment_16k": "PASS", "signature_verification": "PASS",
        }
        staged_receipt = Path(temporary) / "receipt.json"
        staged_receipt.write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
        source_identity(expected=source["source_sha"])
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
