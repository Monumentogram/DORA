"""Exact SPL-01 successor applicability for the owner-authorized reduced scope.

The metadata child supplies only ALPHA_REDUCED_REPAIR_BINDING final pins. This
module derives Git/source facts; evidence booleans cannot authorize a source.
Historical preflight PASS remains evidence for its historical APK pair.
"""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import re
import runpy

ROOT = Path(__file__).resolve().parents[1]
BASELINE_COMMIT = "7f05744b83a53c6d17bccb07603b390e2e6e7292"
BASELINE_TREE = "012ddaa6a7bf90afcf5feae0e3b8c7c62ffbe092"
HISTORICAL_COMMIT = "d3bc6aca800ac0dedd82124aabd8d25eff50c262"
FROZEN_SELECTION_SHA256 = "914a73f472a4ebf84fb386d6b906c6f783002b38ab6d0efff78a45f1d2a2d87c"
SCOPE = "INTERNAL_ALPHA_E36_REDUCED_114"
PREFIX = "android/poc/recovery/src/"
PACKAGE = "kotlin/com/monumentogram/dora/poc/recovery/"
ANDROID_REPAIR_PATHS = frozenset(PREFIX + part + "/" + PACKAGE + path for part, path in (
    ("androidTest", "candidate/RecoveryCampaignInstrumentedTest.kt"),
    ("main", "candidate/AndroidRecoveryStreamingReconciliation.kt"),
    ("main", "candidate/RecoveryStreamingReconciliationController.kt"),
    ("main", "candidate/RecoveryStreamingTinkPrerequisiteCrypto.kt"),
    ("main", "candidate/AndroidRecoveryStreamingOrphanArtifacts.kt"),
    ("main", "candidate/RecoveryStreamingOrphanReconciler.kt"),
    ("main", "journal/AndroidRecoveryQuarantineJournal.kt"),
    ("main", "storage/AndroidOsRecoveryReconciliationStorage.kt"),
    ("test", "candidate/RecoveryStreamingReconciliationControllerTest.kt"),
    ("test", "candidate/RecoveryStreamingTinkPrerequisiteCryptoTest.kt"),
    ("test", "storage/AndroidOsRecoveryReconciliationStorageTest.kt"),
))
HOST_REPAIR_PATHS = frozenset({
    "tools/recovery_campaign.py", "tools/recovery_alpha_repair.py",
    "tools/test_recovery_campaign.py", "tools/test_recovery_alpha_repair.py",
    "tools/validate_recovery_0d6_candidate.py", "tools/test_validate_recovery_0d6_candidate.py",
})
PREFLIGHT_METHODS = {
    PREFIX + "androidTest/" + PACKAGE + "candidate/RecoveryE36GapiPreflightInstrumentedTest.kt":
        "supplementalCanonicalSqliteCompileOptionsPreflight",
    PREFIX + "androidTest/" + PACKAGE + "candidate/RecoveryPlatformPrerequisitesInstrumentedTest.kt":
        "syntheticKeystoreLifecycleAndFilesystemPrerequisites",
    PREFIX + "androidTest/" + PACKAGE + "journal/RecoveryJournalConnectionConfigurationTest.kt":
        "primaryAndConcurrentReaderKeepConfigurationAfterReopen",
}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def candidate_api():
    return runpy.run_path(str(ROOT / "tools/validate_recovery_0d6_candidate.py"))


def read_proof(descriptor, expected_sha=None):
    require(isinstance(descriptor, dict) and set(descriptor) == {"path", "sha256"},
            "Repair evidence descriptor must contain only path and sha256")
    require(isinstance(descriptor["path"], str) and isinstance(descriptor["sha256"], str)
            and re.fullmatch(r"[0-9a-f]{64}", descriptor["sha256"]), "Invalid repair evidence descriptor")
    require(expected_sha is None or descriptor["sha256"] == expected_sha, "Repair evidence pin mismatch")
    path = Path(descriptor["path"])
    require(path.is_file(), "Repair evidence file missing")
    data = path.read_bytes()
    require(hashlib.sha256(data).hexdigest() == descriptor["sha256"], "Repair evidence bytes mismatch")
    return data


def tree_entries(git, commit):
    entries = {}
    for line in git("ls-tree", "-r", "-z", commit, root=ROOT).strip("\0").split("\0"):
        if not line:
            continue
        record, path = line.split("\t", 1)
        mode, kind, oid = record.split()
        entries[path] = {"mode": mode, "type": kind, "object": oid}
    return entries


def selection_identity(selection):
    # Only execution namespace, run IDs and their digests may change. Completed
    # preference/reasons, original slots and every original entry remain fixed.
    volatile = {"executionId", "executionPlanManifestSha256", "executionPlanSource", "supportedAttemptIds"}
    result = {key: value for key, value in selection.items() if key not in volatile and key != "entries"}
    result["entries"] = [{key: value for key, value in entry.items()
                          if key not in {"attemptId", "runId", "executionEntrySha256"}}
                         for entry in selection["entries"]]
    return result


def applicability_facts(api, profile, binding):
    """Recompute the proof's complete source delta from accepted Git objects."""
    git = api["git"]
    require(git("rev-parse", BASELINE_COMMIT + "^{tree}", root=ROOT) == BASELINE_TREE,
            "Repair baseline tree drift")
    require(git("merge-base", BASELINE_COMMIT, profile.implementation_commit, root=ROOT) == BASELINE_COMMIT,
            "Repair implementation does not descend from baseline")
    before = tree_entries(git, BASELINE_COMMIT)
    after = tree_entries(git, profile.implementation_commit)
    paths = sorted(path for path in before.keys() | after.keys() if before.get(path) != after.get(path))
    require({path for path in paths if path.startswith("android/")} == ANDROID_REPAIR_PATHS,
            "Repair Android delta differs from the eleven reviewed files")
    require(set(paths) <= ANDROID_REPAIR_PATHS | HOST_REPAIR_PATHS,
            "Repair changes unrelated repository/build/dependency inputs")
    for path in paths:
        require(after.get(path, {}).get("mode") == "100644" and after[path]["type"] == "blob"
                and (path not in before or before[path]["mode"] == "100644" and before[path]["type"] == "blob"),
                "Repair changes non-regular or deleted source: " + path)
    historical_android = git("rev-parse", HISTORICAL_COMMIT + ":android", root=ROOT)
    require(historical_android == git("rev-parse", BASELINE_COMMIT + ":android", root=ROOT),
            "Historical preflight Android inputs differ from repair baseline")
    methods = []
    for path, method in sorted(PREFLIGHT_METHODS.items()):
        require(path in before and before[path] == after.get(path), "Retained preflight source changed")
        methods.append({"path": path, "method": method, "source": before[path]})
    return {
        "schema": "DORA_RECOVERY_REDUCED_REPAIR_APPLICABILITY_V1", "scope": SCOPE,
        "baseline": {"commit": BASELINE_COMMIT, "tree": BASELINE_TREE},
        "implementation": {"commit": profile.implementation_commit, "tree": profile.implementation_tree},
        "apkPair": {key: binding[key] for key in ("appApkSha256", "testApkSha256")},
        "sourceDelta": [{"path": path, "before": before.get(path), "after": after[path]} for path in paths],
        "retainedPreflight": {"commit": HISTORICAL_COMMIT, "androidTree": historical_android,
                              "selectedMethods": methods},
    }


def validate(plan, gate):
    decision = gate.get("alphaReduced", {})
    require(isinstance(decision, dict) and decision.get("scope") == SCOPE
            and "alphaCampaign" not in gate and "alphaPreflight" not in gate
            and "sourceEquivalence" not in decision and gate.get("supportedPayloads") == ["CAMPAIGN"]
            and gate.get("environment") == "E36-GAPI"
            and gate.get("physicalAuthorization", {}).get("authorized") is False,
            "Repair applicability is restricted to reduced E36 campaign scope")
    api = candidate_api()
    if "ALPHA_PREFIX_REPAIR_BINDING" in api:
        successor = runpy.run_path(str(ROOT / "tools/recovery_alpha_prefix_repair.py"))
        successor["validate"](plan, gate)
        return
    binding = api.get("ALPHA_REDUCED_REPAIR_BINDING")
    require(isinstance(binding, dict) and set(binding) == {"appApkSha256", "testApkSha256", "applicabilitySha256"}
            and all(isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value) for value in binding.values()),
            "Exact reduced repair profile pins are not installed")
    profile = api["active_profile"]()
    require(profile is not None, "No accepted repair implementation profile")
    api["validate"](profile, root=ROOT)
    source = {"commit": api["git"]("rev-parse", "HEAD", root=ROOT),
              "tree": api["git"]("rev-parse", "HEAD^{tree}", root=ROOT),
              "appApkSha256": binding["appApkSha256"], "testApkSha256": binding["testApkSha256"]}
    require(plan.get("source") == gate.get("source") == decision.get("source") == source,
            "Repair source/APKs differ from actual accepted profile")
    proof = json.loads(read_proof(decision.get("sourceRepair"), binding["applicabilitySha256"]))
    require(isinstance(proof, dict), "Repair applicability must be an object")
    expected = applicability_facts(api, profile, binding)
    frozen_descriptor = proof.get("frozenSelection")
    frozen = json.loads(read_proof(frozen_descriptor, FROZEN_SELECTION_SHA256))
    selection = gate.get("reducedSelection")
    require(isinstance(selection, dict) and selection_identity(selection) == selection_identity(frozen),
            "Repair selection changes original 114 bases, slots, variants or completed preference")
    require(selection.get("executionId") and selection["executionId"] != frozen.get("executionId"),
            "Repair attempts must use a fresh execution namespace")
    review = gate.get("proofs", {}).get("independentReview")
    read_proof(review)
    expected.update(frozenSelection=frozen_descriptor, independentReview=review)
    require(proof == expected, "Repair applicability does not match recomputed source/preflight facts")
