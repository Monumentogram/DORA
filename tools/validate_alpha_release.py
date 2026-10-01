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
import validate_security_identity_contract as security

CONTRACT = ROOT / "docs/contracts/DORA_ALPHA_INTERNAL_RELEASE_7_3_V0_1.json"
EVIDENCE = ROOT / "docs/evidence"
NON_EXECUTION = {"7.3C": "NOT_STARTED", "stage8": "NOT_STARTED", "aws": "NOT_CALLED", "audio": "NOT_USED", "runtime": "NOT_IMPLEMENTED"}
PASS = "PASS / INTERNAL_ALPHA_APK_BUILD_CI_DEVICE_READY"
CLOSURE = "1e34b9d7fe4a7f4aa43095244d75bbe3e1b49018"
HARNESS_STEP = """      - name: Run deterministic Cloud contract harness
        run: |
          python3 tools/cloud_contract_harness.py --self-test
          python3 -m unittest discover -s tools -p test_cloud_contract_harness.py
          python3 tools/cloud_contract_harness.py --verify-determinism

"""
RECOVERY_INTEGRATION_CLOSURE_RECEIPT = "docs/evidence/recovery-clean-replacement-integration-v0.1.json"
RECOVERY_FINALIZATION = "281fe6f13ca353088c731505cc8a7530c3864c7f"
RECOVERY_FINALIZATION_TREE = "15fd80667512b0700ae45e921cdafdcf00a3c574"
RECOVERY_FINALIZATION_PARENT = "358619c68368a21e0ccf4b00e7eb4662617bd00d"

SUCCESSOR_PATHS = {
    '.github/workflows/android-ci.yml',
    'tools/cloud_contract_harness.py', 'tools/cloud_contract_fixtures.py',
    'tools/test_cloud_contract_harness.py', 'tools/validate_alpha_release.py',
    'tools/test_validate_alpha_release.py',
    'docs/adr/ADR-0020-deterministic-cloud-contract-composition.md',
    'docs/contracts/DORA_ALPHA_CLOUD_CONTRACT_HARNESS_V0_1.json',
    'docs/stage1/DORA_ALPHA_CLOUD_CONTRACT_HARNESS_V0_1.md',
    'docs/DORA_MVP1_STAGE_STATUS.md', 'docs/DORA_MVP1_IMPLEMENTATION_BACKLOG.md',
    *{f'docs/evidence/alpha-7.3c-{name}-v0.1.json'
      for name in ('local', 'ci', 'scenarios', 'closure', 'sheet')},
} | security.PATHS


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


def verify_ci_step_inventory(ci: dict, workflow: str) -> None:
    for job in ("android-bootstrap", "search-smoke"):
        section = workflow.split(f"  {job}:\n", 1)[1]
        section = re.split(r"\n  [a-z][a-z-]+:\n", section, maxsplit=1)[0]
        required = set(re.findall(r"^      - name: (.+)$", section, re.MULTILINE))
        actual = {step["name"] for step in ci["jobs"][job]["steps"]}
        require(required.issubset(actual), "CI evidence omits required named steps")


def validate_successor_paths(paths) -> None:
    require(set(paths) <= SUCCESSOR_PATHS, "Product/release inputs or historical evidence changed")


def validate_recovery_closure_context(finalization, head, parents, receipt, committed_receipt, frozen_receipt):
    # Early path compatibility only; full branch/provenance/dirty-layer policy
    # remains independently enforced by the later Recovery governance gate.
    require((finalization.commit, finalization.tree, finalization.parents, finalization.is_ancestor_of_head)
            == (RECOVERY_FINALIZATION, RECOVERY_FINALIZATION_TREE, (RECOVERY_FINALIZATION_PARENT,), True),
            "Recovery closure immutable F identity mismatch")
    require(bool(re.fullmatch(r"[0-9a-f]{40}", head))
            and ((head == RECOVERY_FINALIZATION and parents == (RECOVERY_FINALIZATION_PARENT,))
                 or (head != RECOVERY_FINALIZATION and parents == (RECOVERY_FINALIZATION,))),
            "Recovery closure requires exact F or one direct child")
    require(receipt == committed_receipt == frozen_receipt,
            "Recovery closure receipt bytes changed")


def validate_recovery_closure_checkout():
    import validate_poc_recovery_governance as recovery
    prefix = ["git", "-c", f"safe.directory={ROOT.as_posix()}", "-C", str(ROOT)]
    def output(*args):
        return subprocess.check_output(prefix + list(args))
    head = output("rev-parse", "HEAD").decode().strip()
    parents = tuple(output("show", "-s", "--format=%P", "HEAD").decode().split())
    if parents in ((security.BASE,), (security.CORRECTION_BASE,)):
        # Admit only fully verified bounded 7.4 successors; historical context
        # remains the immutable direct child of F, including its receipt bytes.
        recovery.validate_rec_clean_integrated(recovery.collect_recovery_lifecycle_identity())
        head, parents = security.BASE, (RECOVERY_FINALIZATION,)
    finalization = recovery.collect_pinned_commit_identity(RECOVERY_FINALIZATION, head)
    validate_recovery_closure_context(
        finalization, head, parents, (ROOT / RECOVERY_INTEGRATION_CLOSURE_RECEIPT).read_bytes(),
        output("show", f"HEAD:{RECOVERY_INTEGRATION_CLOSURE_RECEIPT}"),
        output("show", f"{RECOVERY_FINALIZATION}:{RECOVERY_INTEGRATION_CLOSURE_RECEIPT}"),
    )


def validate_recovery_successor_paths(paths, contract_bytes: bytes) -> None:
    # This early release step checks path compatibility only. The unchanged later
    # Recovery gate verifies the already-fetched complete historical source,
    # topology, dirty layers and bounded lifecycle before CI can pass.
    import validate_poc_recovery_governance as recovery
    require(hashlib.sha256(contract_bytes).hexdigest() == recovery.REC_CLEAN_CONTRACT_SHA256,
            "Recovery preparation contract digest mismatch")
    contract = json.loads(contract_bytes)
    allowed = SUCCESSOR_PATHS | set(contract["implementation_paths"]) | set(contract["metadata_paths"])
    if RECOVERY_INTEGRATION_CLOSURE_RECEIPT in paths:
        validate_recovery_closure_checkout()
        allowed = allowed | {RECOVERY_INTEGRATION_CLOSURE_RECEIPT}
    require(set(paths) <= allowed, "Product/release inputs or historical evidence changed")


def validate_successor_workflow(original: str, current: str) -> None:
    # Only move the exact existing unconditional pinned fetch; preserve every
    # command/check and compare the remaining workflow byte-for-byte below.
    if (security.PROVENANCE_STEP in current and
            current.index(security.PROVENANCE_STEP) < current.index(
                "      - name: Validate internal Alpha release evidence and negative controls\n")):
        security.validate_ci_provenance_order(current)
        current = current.replace(security.PROVENANCE_STEP, '', 1)
        current = current.replace("      - name: Validate recovery governance package\n",
                                  security.PROVENANCE_STEP + "      - name: Validate recovery governance package\n", 1)
    if security.STEP in current:
        require(current.count(security.STEP) == 1, "Duplicate security CI gate")
        current = current.replace(security.STEP, '', 1)
    require(current.count(HARNESS_STEP) == 1 and current.replace(HARNESS_STEP, '', 1) == original,
            "Existing CI gates changed or harness gate missing")


def verify_source_ancestry(source: str) -> str:
    prefix = ["git", "-c", f"safe.directory={ROOT.as_posix()}", "-C", str(ROOT)]
    result = subprocess.run(prefix + ["merge-base", "--is-ancestor", source, "HEAD"], capture_output=True)
    require(result.returncode == 0, "Implementation is not an ancestor of evidence HEAD")
    closed = subprocess.run(prefix + ["merge-base", "--is-ancestor", CLOSURE, "HEAD"], capture_output=True)
    require(closed.returncode == 0, "Frozen release closure is not an ancestor")
    diff = subprocess.run(prefix + ["diff", "--name-only", source, CLOSURE], capture_output=True, text=True, check=True)
    require(all(path.startswith("docs/") for path in diff.stdout.splitlines()), "Evidence HEAD changes implementation")
    # Include tracked working-tree edits, not only HEAD, and fail on new files
    # outside the exact authorized successor inventory. Frozen release evidence
    # and every Android/build/signing input are excluded from that inventory.
    diff = subprocess.run(prefix + ['diff', '--name-only', CLOSURE], capture_output=True, text=True, check=True)
    untracked = subprocess.run(prefix + ['ls-files', '--others', '--exclude-standard'], capture_output=True, text=True, check=True)
    paths = diff.stdout.splitlines() + untracked.stdout.splitlines()
    finalization = subprocess.run(prefix + ["merge-base", "--is-ancestor", RECOVERY_FINALIZATION, "HEAD"],
                                  capture_output=True)
    if finalization.returncode == 0:
        require(RECOVERY_INTEGRATION_CLOSURE_RECEIPT in paths,
                "Recovery closure receipt missing from successor inventory")
    recovery_contract = ROOT / "docs/contracts/DORA_PR86_CLEAN_RECOVERY_INTEGRATION_V0_1.json"
    if recovery_contract.exists():
        validate_recovery_successor_paths(paths, recovery_contract.read_bytes())
    else:
        validate_successor_paths(paths)
    original = subprocess.run(prefix + ['show', source+':.github/workflows/android-ci.yml'],
                              capture_output=True, text=True, check=True).stdout
    validate_successor_workflow(original, (ROOT / '.github/workflows/android-ci.yml').read_text())
    return original


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
    historical_workflow = verify_source_ancestry(source)
    verify_ci_step_inventory(ci, historical_workflow)
    print("PASS 7.3 public release evidence; final publication CI/readback is a separate post-commit gate")


if __name__ == "__main__":
    main()
