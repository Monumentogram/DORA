"""Validate 7.3 public provenance; PREPARED is never release acceptance."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess

from alpha_release_identity import APPLICATION_ID, CERTIFICATE_SHA256, DISTRIBUTION, ROOT, identity
import alpha_release_sbom as sbom

CONTRACT = ROOT / "docs/contracts/DORA_ALPHA_INTERNAL_RELEASE_7_3_V0_1.json"
EVIDENCE = ROOT / "docs/evidence"
NON_EXECUTION = {"7.3C": "NOT_STARTED", "stage8": "NOT_STARTED", "aws": "NOT_CALLED", "audio": "NOT_USED", "runtime": "NOT_IMPLEMENTED"}
PASS = "PASS / INTERNAL_ALPHA_APK_BUILD_CI_DEVICE_READY"


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def validate_identity(contract: dict) -> None:
    expected = {"task": "7.3", "application_id": APPLICATION_ID, "version_code": 4, "version_name": "0.1.0-alpha.2", "certificate_sha256": CERTIFICATE_SHA256, "branch": "stage/7-alpha-foundation", "distribution": DISTRIBUTION, "migration": "N/A / NO_PRODUCT_PERSISTENCE_SCHEMA_YET", "non_execution": NON_EXECUTION}
    for key, value in expected.items():
        require(contract[key] == value, f"Release contract mismatch: {key}")
    require(identity() == (4, "0.1.0-alpha.2"), "Product identity differs from 7.3")


def validate_closed(contract: dict, build: dict, ci: dict, bom_bytes: bytes, source: str) -> None:
    validate_identity(contract)
    require(contract["status"] == PASS, "Release acceptance pending")
    require(bool(re.fullmatch(r"[0-9a-f]{40}", source)), "Invalid implementation source")
    require(contract["source_sha"] == build["source_sha"] == ci["head_sha"] == source, "Exact implementation source mismatch")
    require(build["source_tree_clean"] is True, "Unclean release source")
    for key in ("application_id", "version_code", "version_name", "certificate_sha256"):
        require(build[key] == contract[key], f"Signed APK identity mismatch: {key}")
    require(build["signer_count"] == 1, "Exactly one signer required")
    require(build["artifact"] == "dora-0.1.0-alpha.2-vc4.apk" and type(build["size_bytes"]) is int and build["size_bytes"] > 0, "Artifact identity invalid")
    digest = contract["artifact_sha256"]
    require(isinstance(digest, str) and bool(re.fullmatch(r"[0-9a-f]{64}", digest)), "Invalid APK digest")
    require(build["sha256"] == build["repeat_sha256"] == digest and build["reproducibility"] == "BYTE_IDENTICAL", "Repeat build identity not proven")
    for key in ("signature_verification", "zip_alignment_16k", "native_elf"):
        require(build[key] == "PASS", f"Signed artifact verification missing: {key}")
    require(build["secret_scan"] == "PASS_ZERO_FINDINGS", "Secret scan missing")
    require(build["debuggable"] is False and build["test_only"] is False and build["unexpected_exported_components"] == [], "Unadmitted manifest component or flags")
    require(set(build["permissions"]).issubset({APPLICATION_ID + ".DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"}), "Unexpected release permission")
    backup = build["signing_backup"]
    require(backup["backup_state"] in {"NEW_ENCRYPTED_BACKUP_CREATED_AND_RESTORE_VERIFIED", "EXISTING_ENCRYPTED_BACKUP_RESTORE_VERIFIED"}, "Signing backup unavailable")
    require(backup["encryption_verified"] is True and backup["restore_verified"] is True and backup["active_material_unchanged"] is True, "Signing restore not proven")
    require(backup["certificate_sha256"] == CERTIFICATE_SHA256 and backup["private_key_proof"] == "PASS_RSA3072_SHA256_SIGN_VERIFY_AND_NEGATIVE_CONTROL" and backup["temporary_restore_cleanup"] == "PASS_ABSENCE_VERIFIED", "Restored signer/cleanup proof missing")
    require(hashlib.sha256(bom_bytes).hexdigest() == contract["sbom_sha256"], "SBOM digest mismatch")
    bom = json.loads(bom_bytes)
    require(bom["bomFormat"] == "CycloneDX" and bom["specVersion"] == "1.6", "Wrong SBOM standard")
    require(bom["metadata"]["component"]["version"] == contract["version_name"], "SBOM product mismatch")
    require({p["name"]: p["value"] for p in bom["metadata"]["properties"]}["dora:source-sha"] == source, "SBOM source mismatch")
    require(ci["conclusion"] == "success" and type(ci["run_id"]) is int and ci["run_id"] > 0, "Implementation CI not successful")
    require(set(ci["jobs"]) == {"android-bootstrap", "search-smoke"}, "Mandatory CI job missing")
    for job in ci["jobs"].values():
        require(job["conclusion"] == "success" and bool(job["steps"]), "CI job/steps not successful")
        for step in job["steps"]:
            require(step["conclusion"] == "success" or (step["name"] == "Print emulator log on failure" and step["conclusion"] == "skipped"), "Mandatory CI step not successful")
    device = build["device"]
    expected = {"model": "POCO M5", "hardware_model": "22071219CG", "android": "14", "api": 34, "abi": "arm64-v8a", "baseline_version_code": 2, "baseline_version_name": "0.1.0-alpha.1", "baseline_certificate_sha256": CERTIFICATE_SHA256, "baseline_apk_sha256": "3cb591abc8e33a832bdf018b7cff545a8b3c39641ab74b44e8d4168cfa045d3c", "upgrade": "PASS", "final_version_code": 4, "final_version_name": "0.1.0-alpha.2", "cold_launch": "PASS", "foreground_activity": "com.monumentogram.dora/.MainActivity", "visible_version_label": "DORA Alpha 0.1.0-alpha.2 (4)", "installed_apk_sha256": digest}
    for key, value in expected.items():
        require(device[key] == value, f"Physical acceptance mismatch: {key}")
    for field in ("uid", "first_install_time"):
        require(bool(device[field + "_before"]) and device[field + "_before"] == device[field + "_after"], f"Package continuity not proven: {field}")


def verify_ci_step_inventory(ci: dict) -> None:
    workflow = (ROOT / ".github/workflows/android-ci.yml").read_text()
    for job in ("android-bootstrap", "search-smoke"):
        section = workflow.split(f"  {job}:\n", 1)[1]
        section = re.split(r"\n  [a-z][a-z-]+:\n", section, maxsplit=1)[0]
        required = set(re.findall(r"^      - name: (.+)$", section, re.MULTILINE))
        actual = {step["name"] for step in ci["jobs"][job]["steps"]}
        require(required.issubset(actual), "CI evidence omits required named steps")


def verify_source_ancestry(source: str) -> None:
    prefix = ["git", "-c", f"safe.directory={ROOT.as_posix()}", "-C", str(ROOT)]
    result = subprocess.run(prefix + ["merge-base", "--is-ancestor", source, "HEAD"], capture_output=True)
    require(result.returncode == 0, "Implementation is not an ancestor of evidence HEAD")
    diff = subprocess.run(prefix + ["diff", "--name-only", source, "HEAD"], capture_output=True, text=True, check=True)
    require(all(path.startswith("docs/") for path in diff.stdout.splitlines()), "Evidence HEAD changes implementation")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--require-closed", action="store_true")
    args = parser.parse_args()
    contract = json.loads(CONTRACT.read_text())
    validate_identity(contract)
    if contract["status"] == "PREPARED / ACCEPTANCE_PENDING":
        require(not args.require_closed, "Release acceptance has not run")
        require(contract["source_sha"] is None and contract["artifact_sha256"] is None and contract["sbom_sha256"] is None, "Prepared contract cannot assert artifact evidence")
        print("PASS prospective 7.3 contract; release/device acceptance remains PENDING")
        return
    build = json.loads((EVIDENCE / "alpha-7.3-build-device-v0.1.json").read_text())
    ci = json.loads((EVIDENCE / "alpha-7.3-ci-v0.1.json").read_text())
    bom_bytes = (EVIDENCE / "alpha-7.3-sbom-v0.1.cdx.json").read_bytes()
    source = contract["source_sha"]
    validate_closed(contract, build, ci, bom_bytes, source)
    graph = json.loads(sbom.APPROVED.read_text())
    sbom.validate(json.loads(bom_bytes), graph, source, sbom.lock_coordinates(), graph)
    verify_ci_step_inventory(ci)
    verify_source_ancestry(source)
    print("PASS 7.3 public release evidence; final publication CI/readback is a separate post-commit gate")


if __name__ == "__main__":
    main()
