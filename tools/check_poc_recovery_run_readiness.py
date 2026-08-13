#!/usr/bin/env python3
"""Fail closed unless POC-RECOVERY-001 v0.6 has explicit implementation/execution authority."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

from validate_poc_recovery_governance import (
    FORMAL_DISPOSITION,
    FORMAL_FINDINGS_LEDGER_PATH,
    FORMAL_REVIEW_PATH,
    REC_RDY_02_CLOSURE,
    REVIEWER_CAPACITY,
    REVIEWER_NAME,
    validate_formal_findings_ledger,
    validate_formal_human_review,
)


ROOT = Path(__file__).resolve().parents[1]
STAGE0 = ROOT / "docs" / "stage0"
EVIDENCE = ROOT / "docs" / "evidence" / "poc-recovery-001"
GATE_ID = "poc-recovery-stage0-v0.6"
PROTOCOL_ID = "poc-recovery-protocol-stage0-v0.6"
REVIEWED_V06_HEAD = "b5371f523e4471aca48a63a82b9ee4e1f9a7e0fd"
POST_MERGE_EVIDENCE = EVIDENCE / "post-merge-advisory-rereview-2026-08-13.json"
POLICY_ID = "REC-JSR305-EXCLUDE-001"
TINK_COORDINATE = "com.google.crypto.tink:tink-android:1.23.0"
JSR_COORDINATE = "com.google.code.findbugs:jsr305:3.0.2"
R8_RULES = [
    "-dontwarn javax.annotation.Nullable",
    "-dontwarn javax.annotation.concurrent.GuardedBy",
    "-dontwarn javax.annotation.concurrent.ThreadSafe",
]
CANONICAL_BLOCKERS = [
    "REC-RDY-01-PRODUCT-IP-FINAL-APPROVAL",
    "REC-RDY-02-ACCOUNTABLE-ENGINEERING-SECURITY-REVIEW",
    "REC-RDY-03-STREAMING-IMPLEMENTATION-VERIFICATION",
    "REC-RDY-04-MICROFILE-IMPLEMENTATION-VERIFICATION",
    "REC-RDY-05-FUTURE-RESOLVED-GRAPH",
    "REC-RDY-06-DEVICE-SQLITE-PREFLIGHT",
    "REC-RDY-07-HARNESS-ABSENT",
    "REC-RDY-08-OWNER-EXECUTION-AUTHORIZATION",
    "REC-RDY-09-D1-D5-FULL-VERDICT",
    "REC-RDY-10-PRODUCTION-LEGAL-SECURITY",
    "REC-RDY-11-SUPPLY-CHAIN-AUTHENTICITY",
]
EXPECTED_COVERED_INPUTS = [
    "all resolvable compile",
    "all resolvable runtime",
    "all resolvable unit-test",
    "all resolvable androidTest",
    "all resolvable benchmark",
    "all resolvable release",
    "all packaging/runtime artifact inputs",
    "dependency locks and dependency verification metadata",
]


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def read_json(path: Path) -> dict[str, Any]:
    require(path.is_file(), f"Missing required evidence: {path.relative_to(ROOT)}")
    return json.loads(path.read_text(encoding="utf-8"))


def validate_static_contract(
    gate: dict[str, Any],
    protocol: dict[str, Any],
    readiness: dict[str, Any],
    analysis: dict[str, Any],
    roles: dict[str, Any],
    rereview: dict[str, Any],
    formal_review: dict[str, Any],
    formal_ledger: dict[str, Any],
) -> None:
    require(gate["schemaVersion"] == 6 and gate["gateSetVersion"] == GATE_ID, "Active Gate Set is not v0.6")
    require(protocol["schemaVersion"] == 6 and protocol["protocolId"] == PROTOCOL_ID, "Active protocol is not v0.6")
    require(readiness["schemaVersion"] == 9, "Recovery readiness schema is stale")
    require(analysis["schemaVersion"] == 3, "JSR305 exclusion analysis schema is stale")
    require(roles["schemaVersion"] == 9, "Recovery review-role schema is stale")
    require(
        readiness["packageArtifacts"]["activeGateSetVersion"] == GATE_ID
        and readiness["packageArtifacts"]["activeProtocolId"] == PROTOCOL_ID
        and roles["activeGateSetVersion"] == GATE_ID
        and roles["activeProtocolId"] == PROTOCOL_ID,
        "Active v0.6 metadata mismatch",
    )
    gate_ids = gate["blockers"]
    readiness_ids = [item["id"] for item in readiness["blockers"]]
    require(len(gate_ids) == len(set(gate_ids)), "Gate Set contains duplicate blocker IDs")
    require(len(readiness_ids) == len(set(readiness_ids)), "Readiness contains duplicate blocker IDs")
    require(gate_ids == readiness_ids == roles["canonicalBlockerIds"] == CANONICAL_BLOCKERS, "Canonical blocker list mismatch")
    require(
        readiness["advisoryDocumentaryReview"]["formalReviewer"] is False
        and readiness["advisoryDocumentaryReview"]["closesRecRdy02"] is False
        and roles["roles"]["advisoryDocumentaryReviewer"]["formalReviewer"] is False,
        "Advisory review acquired formal/accountable authority",
    )
    validate_formal_human_review(formal_review)
    validate_formal_findings_ledger(formal_ledger)
    independent = roles["roles"]["independentRecoveryEngineeringSecurity"]
    formal_summary = readiness["formalAccountableEngineeringSecurityReviewEvidence"]
    rec02 = readiness["blockers"][1]
    require(
        independent["reviewer"] == REVIEWER_NAME
        and independent["capacity"] == REVIEWER_CAPACITY
        and independent["formalReviewer"] is True
        and independent["status"] == FORMAL_DISPOSITION
        and independent["formalReviewEvidenceLocator"] == FORMAL_REVIEW_PATH
        and independent["recRdy02Status"] == REC_RDY_02_CLOSURE
        and independent["closesRecRdy02"] is True
        and independent["rambusCorporateApprovalClaimed"] is False
        and independent["formalGitHubReviewClaimed"] is False
        and formal_summary["locator"] == FORMAL_REVIEW_PATH
        and formal_summary["formalReviewer"] is True
        and formal_summary["disposition"] == FORMAL_DISPOSITION
        and formal_summary["recRdy02Status"] == REC_RDY_02_CLOSURE
        and formal_summary["closesRecRdy02"] is True
        and formal_summary["rambusCorporateApprovalClaimed"] is False
        and rec02["id"] == CANONICAL_BLOCKERS[1]
        and rec02["status"] == REC_RDY_02_CLOSURE
        and all(readiness[field] is False for field in (
            "implementationAllowed", "implementationAllowedByThisPackage", "executionAllowed",
            "measuredExecutionAllowed",
        )),
        "REC-RDY-02 lacks valid distinct accountable formal human-review closure",
    )
    rereview_summary = readiness["advisoryDocumentaryReReviewEvidence"]
    rereview_record = rereview["advisoryReReview"]
    require(
        rereview["schemaVersion"] == 1
        and rereview["scope"] == "GOVERNANCE_ONLY_POST_MERGE_RECONCILIATION"
        and rereview_summary["locator"] == "docs/evidence/poc-recovery-001/post-merge-advisory-rereview-2026-08-13.json"
        and rereview_summary["reviewedCommit"] == rereview_record["reviewedCommit"] == REVIEWED_V06_HEAD
        and rereview_summary["formalReviewer"] is rereview_record["formalReviewer"] is False
        and rereview_summary["disposition"] == rereview_record["disposition"] == "NO_FURTHER_DOCUMENTARY_CHANGES_REQUIRED"
        and rereview_summary["actionableFindings"] == rereview_record["actionableFindings"] == []
        and rereview_summary["repeatAdvisoryReviewComplete"] is rereview_record["repeatAdvisoryReviewComplete"] is True
        and rereview_summary["closesRecRev2026081202"] is rereview_record["closesRecRev2026081202"] is False
        and rereview_summary["closesRecRdy02"] is rereview_record["closesRecRdy02"] is False
        and rereview["findingState"]["REC-REV-20260812-02"] == "OPEN_BLOCKING"
        and rereview["readinessBoundary"]["recRdy02Status"] == "OPEN_UNASSIGNED"
        and rereview["readinessBoundary"]["accountableEngineeringSecurityReviewer"] is None,
        "Post-merge advisory re-review acquired authority or closed a blocking finding",
    )
    rows = protocol["faultCampaign"]["activeEffectiveFaultMatrixV06"]["rows"]
    ids = [row["id"] for row in rows]
    require(
        len(rows) == len(set(ids)) == 46
        and ids.count("KEY-04") == 1
        and all(row["effective"] is True for row in rows),
        "Active fault matrix is not 46 unique effective rows with one KEY-04",
    )
    key04 = next(row for row in rows if row["id"] == "KEY-04")
    require(
        key04["decryptOutcome"] == "AUTHENTICATION_OR_AAD_FAILURE_ONLY"
        and key04["successfulDecryptAllowed"] is False
        and key04["postDecryptParserOrPlaintextMismatchAllowed"] is False
        and key04["expectedClassification"] == "KEY_UNAVAILABLE_KEY_MISMATCH"
        and key04["expectedClassificationAlternativesAllowed"] is False,
        "Effective KEY-04 oracle drift",
    )
    kcf07 = next(row for row in rows if row["id"] == "KCF-07")
    require("Aead.decrypt() succeeds" in kcf07["requiredObservation"] and kcf07["expectedClassification"] == "CORRUPT_KEY_CONFIRMATION", "KCF-07 oracle drift")

    policy = readiness["dependencyExclusionPolicy"]
    analysis_policy = analysis["prospectivePolicy"]
    product_ip = roles["roles"]["stage0ProductIp"]
    require(
        policy["policyId"] == analysis_policy["policyId"] == POLICY_ID
        and policy["status"] == analysis_policy["status"] == "APPROVED_PROSPECTIVE_POLICY_ONLY"
        and policy["productIpAccepted"] is True
        and policy["acceptedBy"] == analysis_policy["acceptedBy"] == "Project owner"
        and policy["acceptedOn"] == analysis_policy["acceptedOn"] == "2026-08-12"
        and policy["rootCoordinate"] == analysis_policy["rootCoordinate"] == TINK_COORDINATE
        and policy["forbiddenResolvedCoordinate"] == analysis_policy["forbiddenResolvedCoordinate"] == JSR_COORDINATE
        and policy["coveredFutureModule"] == analysis_policy["coveredFutureModule"] == ":poc:recovery"
        and policy["coveredConfigurationFamilies"] == EXPECTED_COVERED_INPUTS,
        "Prospective recovery exclusion policy drift",
    )
    require(
        policy["requiredResolvedComponentCount"] == analysis_policy["requiredResolvedComponentCount"] == 0
        and policy["requiredR8Rules"] == analysis_policy["requiredR8Rules"] == R8_RULES
        and policy["allCoveredRecoveryInputsRequired"] is True
        and policy["repositoryWideAbsenceClaimed"] is False
        and analysis["currentRepositoryBoundary"]["repositoryWideAbsenceClaimed"] is False
        and policy["excludedCurrentInputsAreRecoveryAdmissionEvidence"] is False
        and analysis_policy["excludedCurrentInputsAreRecoveryAdmissionEvidence"] is False
        and policy["compileOnlyOrAlternatePathAllowed"] is False
        and analysis_policy["compileOnlyOrAlternatePathToForbiddenCoordinateAllowed"] is False
        and policy["broaderDontwarnAllowed"] is False
        and analysis_policy["broaderJavaxAnnotationDontwarnAllowed"] is False
        and policy["unresolvedR8MissingClassesAllowed"] is False
        and policy["underlyingArtifactLicenseConflictResolved"] is False
        and policy["jsr305UseOrDistributionApproved"] is False,
        "Recovery zero-JSR305/R8 contract drift",
    )
    require(
        product_ip["governancePacketEvidenceDisposition"]["status"] == "CLOSED_VERIFIED"
        and product_ip["futureExactGraphDisposition"]["status"] == "OPEN_BLOCKED"
        and product_ip["futureExactGraphDisposition"]["actualGraphApproved"] is False,
        "Product/IP A/B/C state split drift",
    )


def validate_future_graph(report: dict[str, Any]) -> None:
    require(report["schemaVersion"] == 2 and report["pocId"] == "POC-RECOVERY-001", "Future graph report identity drift")
    require(report["policyId"] == POLICY_ID and report["module"] == ":poc:recovery", "Future graph boundary drift")
    require(isinstance(report["exactCommit"], str) and len(report["exactCommit"]) == 40, "Future graph lacks exact commit")
    require(report["rootCoordinate"] == TINK_COORDINATE and report["tinkLocalExclude"] == JSR_COORDINATE, "Future graph exclusion drift")
    require(report["coveredInputFamilies"] == EXPECTED_COVERED_INPUTS, "Future graph input coverage drift")
    require(report["allResolvableConfigurationsEnumerated"] is True and report["allPackagingRuntimeInputsEnumerated"] is True, "Future graph coverage incomplete")
    require(report["recoveryLocksAndVerificationMetadataInspected"] is True, "Future graph omitted locks/verification metadata")
    configurations = report["configurations"]
    require(isinstance(configurations, list) and configurations and report["configurationCount"] == len(configurations), "Future graph configurations incomplete")
    require(
        all(item["module"] == ":poc:recovery" and item["canBeResolved"] is True and item["jsr305Components"] == [] for item in configurations),
        "JSR305 appears or configuration evidence is incomplete",
    )
    require(report["jsr305ResolvedComponentCount"] == 0 and report["packagedJsr305ClassDefinitionCount"] == 0, "Future recovery graph/package contains JSR305")
    require(report["scopedExcludeVerified"] is True, "Future graph did not verify scoped Tink exclusion")
    shrinker = report["releaseR8"]
    require(shrinker["rules"] == R8_RULES and shrinker["broaderDontwarnRules"] == [] and shrinker["unresolvedMissingClasses"] == [] and shrinker["status"] == "PASSED", "Future release R8 evidence failed")
    require(report["nonMetricDebugBuildPassed"] is True and report["nonMetricReleaseBuildPassed"] is True, "Future non-metric builds failed")
    modifier = report["modifierResolution"]
    require(
        modifier["missingClass"] == "javax.lang.model.element.Modifier"
        and modifier["status"] in {"NOT_PRESENT_IN_ACTUAL_GRAPH", "RESOLVED_AND_VERIFIED"}
        and modifier["broadDontwarnUsed"] is False,
        "Future Modifier condition was not resolved narrowly",
    )
    disposition = report["actualGraphProductIpDisposition"]
    require(
        disposition["status"] == "APPROVED_FOR_EXACT_EXCLUDED_STAGE0_RECOVERY_GRAPH"
        and isinstance(disposition["approvedBy"], str) and bool(disposition["approvedBy"])
        and isinstance(disposition["approvedOn"], str) and bool(disposition["approvedOn"])
        and disposition["jsr305UseOrDistributionApproved"] is False
        and disposition["dependencyAdmission"] is False
        and disposition["productionAdmission"] is False,
        "Future actual-graph Product/IP disposition absent or overbroad",
    )


def complete_runtime_evidence(evidence: dict[str, Any]) -> bool:
    return bool(evidence) and all(value is not None for value in evidence.values())


def main() -> int:
    gate = read_json(STAGE0 / "poc-recovery-gate-set-stage0-v0.6.json")
    protocol = read_json(STAGE0 / "poc-recovery-protocol-stage0-v0.6.json")
    readiness = read_json(EVIDENCE / "readiness.json")
    analysis = read_json(EVIDENCE / "jsr305-exclusion-analysis-2026-08-12.json")
    roles = read_json(EVIDENCE / "review-roles.json")
    rereview = read_json(POST_MERGE_EVIDENCE)
    formal_review = read_json(ROOT / FORMAL_REVIEW_PATH)
    formal_ledger = read_json(ROOT / FORMAL_FINDINGS_LEDGER_PATH)
    provenance = read_json(EVIDENCE / "sqlite-platform-provenance.json")
    validate_static_contract(gate, protocol, readiness, analysis, roles, rereview, formal_review, formal_ledger)

    active_blockers = {item["id"]: item for item in readiness["blockers"]}
    blockers: list[str] = []
    report_path = ROOT / readiness["dependencyExclusionPolicy"]["futureResolvedGraphReportLocator"]
    report_present = report_path.is_file()
    require(readiness["dependencyExclusionPolicy"]["futureResolvedGraphReportPresent"] is report_present, "Future graph report presence claim mismatch")
    if report_present:
        validate_future_graph(read_json(report_path))
    else:
        blockers.append(CANONICAL_BLOCKERS[4])

    approvals = readiness["approvals"]
    product_ip = roles["roles"]["stage0ProductIp"]
    independent = roles["roles"]["independentRecoveryEngineeringSecurity"]
    if approvals["futureActualGraphProductIpDisposition"] != "APPROVED" or product_ip["futureExactGraphDisposition"]["actualGraphApproved"] is not True:
        blockers.append(CANONICAL_BLOCKERS[0])
    if (
        independent["status"] != FORMAL_DISPOSITION
        or independent["reviewer"] != REVIEWER_NAME
        or independent["capacity"] != REVIEWER_CAPACITY
        or independent["formalReviewer"] is not True
        or active_blockers[CANONICAL_BLOCKERS[1]]["status"] != REC_RDY_02_CLOSURE
    ):
        blockers.append(CANONICAL_BLOCKERS[1])
    if readiness["runtimeDependencyAdded"] is not True or readiness["nonMetricImplementationVerificationPassed"] is not True:
        blockers.append(CANONICAL_BLOCKERS[2])
    if readiness["nonMetricImplementationVerificationPassed"] is not True:
        blockers.append(CANONICAL_BLOCKERS[3])
    if (
        readiness["recoveryModuleExists"] is not True
        or readiness["harnessImplemented"] is not True
        or readiness["implementationAllowed"] is not True
        or readiness["implementationAllowedByThisPackage"] is not True
        or gate["implementationAllowed"] is not True
        or protocol["implementationAllowed"] is not True
    ):
        blockers.append(CANONICAL_BLOCKERS[6])

    environments = {item["id"]: item for item in provenance["phaseA"]["environments"]}
    preflight_ready = True
    for environment_id in ("PINNED_API36_X86_64_EMULATOR", "PHYSICAL_D2"):
        evidence = environments[environment_id]["requiredFreshRuntimeEvidence"]
        if not complete_runtime_evidence(evidence):
            preflight_ready = False
            continue
        require(evidence["effectiveJournalMode"] == "WAL", f"{environment_id} journal_mode is not WAL")
        require(evidence["effectiveSynchronousMode"] == "FULL", f"{environment_id} synchronous is not FULL")
        require(evidence["effectiveWalAutocheckpoint"] == 0, f"{environment_id} wal_autocheckpoint is not zero")
        require(evidence["effectiveForeignKeys"] is True, f"{environment_id} foreign_keys is not ON")
        require(evidence["protocolId"] == PROTOCOL_ID, f"{environment_id} protocol drift")
    if not preflight_ready:
        blockers.append(CANONICAL_BLOCKERS[5])

    if gate["executionAllowed"] is not True or protocol["executionAllowed"] is not True or readiness["executionAllowed"] is not True or provenance["phaseA"]["executionAllowed"] is not True or gate["executionAuthorization"]["status"] != "AUTHORIZED_BY_PROJECT_OWNER" or roles["roles"]["executionAuthorizer"]["status"] != "AUTHORIZED_FOR_NAMED_PHASE_AND_COMMIT":
        blockers.append(CANONICAL_BLOCKERS[7])
    if readiness["fullVerdict"]["deferred"] is True or readiness["fullVerdict"]["passAllowedWithoutCompleteD1D2D5Profile"] is not False:
        blockers.append(CANONICAL_BLOCKERS[8])
    if roles["roles"]["productionLegal"]["reviewer"] is None or roles["roles"]["productionSecurity"]["reviewer"] is None:
        blockers.append(CANONICAL_BLOCKERS[9])
    if readiness["exactFutureResolvedGraphReviewed"] is not True or not report_present:
        blockers.append(CANONICAL_BLOCKERS[10])

    require(readiness["killCampaignExecuted"] is False and readiness["deviceTestsExecuted"] is False and readiness["benchmarksExecuted"] is False, "Readiness evidence must precede measured/device execution")
    require(readiness["phaseA"]["authorizedNow"] is False and readiness["phaseA"]["authorizationGrantedByFormalReview"] is False, "Formal review must not authorize Phase A")
    require(readiness["productionAppChanged"] is False, "Production :app changed")
    ordered = [blocker_id for blocker_id in CANONICAL_BLOCKERS if blocker_id in set(blockers)]
    require(all(blocker_id in active_blockers for blocker_id in ordered), "Readiness checker emitted unknown blocker ID")
    require(not ordered, ", ".join(ordered))
    print("POC-RECOVERY-001 v0.6 execution readiness passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, StopIteration, json.JSONDecodeError) as error:
        print(f"BLOCKED {error}", file=sys.stderr)
        raise SystemExit(1)
