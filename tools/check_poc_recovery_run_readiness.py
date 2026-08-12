#!/usr/bin/env python3
"""Fail closed unless POC-RECOVERY-001 v0.4 has explicit implementation and execution authority."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
STAGE0 = ROOT / "docs" / "stage0"
EVIDENCE = ROOT / "docs" / "evidence" / "poc-recovery-001"
GATE_ID = "poc-recovery-stage0-v0.4"
PROTOCOL_ID = "poc-recovery-protocol-stage0-v0.4"
POLICY_ID = "REC-JSR305-EXCLUDE-001"
TINK_COORDINATE = "com.google.crypto.tink:tink-android:1.23.0"
JSR_COORDINATE = "com.google.code.findbugs:jsr305:3.0.2"
R8_RULES = [
    "-dontwarn javax.annotation.Nullable",
    "-dontwarn javax.annotation.concurrent.GuardedBy",
    "-dontwarn javax.annotation.concurrent.ThreadSafe",
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


def validate_policy(
    gate: dict[str, Any],
    protocol: dict[str, Any],
    readiness: dict[str, Any],
    analysis: dict[str, Any],
    roles: dict[str, Any],
) -> None:
    require(gate["schemaVersion"] == 4 and gate["gateSetVersion"] == GATE_ID, "Active Gate Set is not v0.4")
    require(protocol["schemaVersion"] == 4 and protocol["protocolId"] == PROTOCOL_ID, "Active protocol is not v0.4")
    require(readiness["schemaVersion"] == 5, "Recovery readiness schema is stale")
    require(analysis["schemaVersion"] == 3, "JSR305 exclusion analysis schema is stale")
    require(roles["schemaVersion"] == 5, "Recovery review-role schema is stale")

    gate_state = gate["readinessStates"]["REC-RDY-11"]
    require(
        gate_state["prospectivePolicy"] == "CLOSED_APPROVED"
        and gate_state["governanceAuthenticityLicenseEvidence"] == "CLOSED_VERIFIED_FOR_EXACT_PACKET"
        and gate_state["actualFutureGraphVerificationAndProductIpDisposition"] == "OPEN_BLOCKED"
        and gate_state["jsr305UseOrDistributionApproved"] is False,
        "Gate Set REC-RDY-11 three-state model drift",
    )

    policy = readiness["dependencyExclusionPolicy"]
    analysis_policy = analysis["prospectivePolicy"]
    protocol_policy = protocol["dependencyBoundary"]
    require(
        policy["policyId"] == analysis_policy["policyId"] == protocol_policy["policyId"] == POLICY_ID,
        "REC-JSR305-EXCLUDE-001 identity drift",
    )
    require(
        policy["status"] == analysis_policy["status"] == "APPROVED_PROSPECTIVE_POLICY_ONLY"
        and policy["productIpAccepted"] is True
        and policy["acceptedBy"] == analysis_policy["acceptedBy"] == "Project owner"
        and policy["acceptedOn"] == analysis_policy["acceptedOn"] == "2026-08-12",
        "Prospective exclusion policy is not closed/approved",
    )
    require(
        policy["rootCoordinate"] == analysis_policy["rootCoordinate"] == TINK_COORDINATE
        and policy["forbiddenResolvedCoordinate"] == analysis_policy["forbiddenResolvedCoordinate"] == JSR_COORDINATE,
        "Prospective exclusion coordinates drift",
    )
    require(
        policy["coveredFutureModule"] == analysis_policy["coveredFutureModule"] == ":poc:recovery",
        "Exclusion policy is not bounded to future :poc:recovery",
    )
    require(policy["coveredConfigurationFamilies"] == EXPECTED_COVERED_INPUTS, "Future recovery input coverage drift")
    require(policy["allCoveredRecoveryInputsRequired"] is True, "Not every covered recovery input is mandatory")
    require(
        policy["repositoryWideAbsenceClaimed"] is False
        and analysis["currentRepositoryBoundary"]["repositoryWideAbsenceClaimed"] is False
        and protocol_policy["repositoryWideAbsenceClaimed"] is False,
        "Readiness makes a forbidden repository-wide absence claim",
    )
    require(
        policy["excludedCurrentInputsAreRecoveryAdmissionEvidence"] is False
        and analysis_policy["excludedCurrentInputsAreRecoveryAdmissionEvidence"] is False
        and protocol_policy["excludedCurrentInputsAreRecoveryAdmissionEvidence"] is False,
        "Existing other-module tooling paths were treated as recovery admission evidence",
    )
    require(
        policy["requiredResolvedComponentCount"] == analysis_policy["requiredResolvedComponentCount"]
        == protocol_policy["requiredResolvedJsr305ComponentCount"] == 0,
        "Recovery policy no longer requires zero resolved JSR305 components",
    )
    require(protocol_policy["requiredPackagedJsr305ClassDefinitionCount"] == 0, "Recovery policy no longer requires zero packaged JSR305 definitions")
    require(
        policy["compileOnlyOrAlternatePathAllowed"] is False
        and analysis_policy["compileOnlyOrAlternatePathToForbiddenCoordinateAllowed"] is False,
        "compileOnly or alternate JSR305 path was allowed",
    )
    require(
        policy["requiredR8Rules"] == analysis_policy["requiredR8Rules"] == protocol_policy["requiredR8Rules"] == R8_RULES
        and policy["broaderDontwarnAllowed"] is False
        and analysis_policy["broaderJavaxAnnotationDontwarnAllowed"] is False
        and protocol_policy["broaderDontwarnAllowed"] is False
        and policy["unresolvedR8MissingClassesAllowed"] is False
        and protocol_policy["releaseR8UnresolvedMissingClassesAllowed"] is False,
        "Exact three-rule/no-unresolved-R8 policy drift",
    )
    require(
        policy["underlyingArtifactLicenseConflictResolved"] is False
        and policy["jsr305UseOrDistributionApproved"] is False
        and protocol_policy["jsr305UseOrDistributionApproved"] is False,
        "Excluded JSR305 terms were interpreted or use/distribution was approved",
    )

    # Readiness deliberately does not require approval to use the excluded JSR305 artifact.
    # It requires proven absence in the exact recovery scope and a separate Product/IP disposition
    # for the future actual graph that contains no JSR305 component.
    product_ip = roles["roles"]["stage0ProductIp"]
    require(
        product_ip["governancePacketEvidenceDisposition"]["status"] == "CLOSED_VERIFIED"
        and product_ip["governancePacketEvidenceDisposition"]["jsr305UseOrDistributionApproved"] is False
        and product_ip["futureExactGraphDisposition"]["status"] == "OPEN_BLOCKED"
        and product_ip["futureExactGraphDisposition"]["actualGraphApproved"] is False,
        "Product/IP packet/actual-graph state split drift",
    )


def validate_future_graph(report: dict[str, Any]) -> None:
    require(report["schemaVersion"] == 2 and report["pocId"] == "POC-RECOVERY-001", "Future graph report identity drift")
    require(report["policyId"] == POLICY_ID and report["module"] == ":poc:recovery", "Future graph report boundary drift")
    require(isinstance(report["exactCommit"], str) and len(report["exactCommit"]) == 40, "Future graph report lacks exact commit")
    require(report["rootCoordinate"] == TINK_COORDINATE, "Future graph Tink coordinate drift")
    require(report["tinkLocalExclude"] == JSR_COORDINATE, "Future graph lacks exact Tink-local JSR305 exclusion")
    require(report["coveredInputFamilies"] == EXPECTED_COVERED_INPUTS, "Future graph report input coverage drift")
    require(report["allResolvableConfigurationsEnumerated"] is True, "Future graph omitted a resolvable recovery configuration")
    require(report["allPackagingRuntimeInputsEnumerated"] is True, "Future graph omitted packaging/runtime inputs")
    require(report["recoveryLocksAndVerificationMetadataInspected"] is True, "Future graph omitted recovery lock/verification inputs")
    configurations = report["configurations"]
    require(isinstance(configurations, list) and configurations, "Future graph report contains no configurations")
    require(report["configurationCount"] == len(configurations), "Future graph configuration count drift")
    require(
        all(
            item["module"] == ":poc:recovery"
            and item["canBeResolved"] is True
            and isinstance(item["name"], str)
            and bool(item["name"])
            and item["jsr305Components"] == []
            for item in configurations
        ),
        "JSR305 appears in a covered recovery configuration or evidence is incomplete",
    )
    require(report["jsr305ResolvedComponentCount"] == 0, "Future recovery graph resolves JSR305")
    require(report["packagedJsr305ClassDefinitionCount"] == 0, "Future recovery package defines JSR305 classes")
    require(report["scopedExcludeVerified"] is True, "Future graph did not verify the scoped Tink exclusion")
    shrinker = report["releaseR8"]
    require(
        shrinker["rules"] == R8_RULES
        and shrinker["broaderDontwarnRules"] == []
        and shrinker["unresolvedMissingClasses"] == []
        and shrinker["status"] == "PASSED",
        "Future release R8 evidence is incomplete, broad or unresolved",
    )
    require(report["nonMetricDebugBuildPassed"] is True and report["nonMetricReleaseBuildPassed"] is True, "Future non-metric builds did not pass")
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
        and isinstance(disposition["approvedBy"], str)
        and bool(disposition["approvedBy"])
        and isinstance(disposition["approvedOn"], str)
        and bool(disposition["approvedOn"])
        and disposition["jsr305UseOrDistributionApproved"] is False
        and disposition["dependencyAdmission"] is False
        and disposition["productionAdmission"] is False,
        "Future actual-graph Product/IP disposition is absent or overbroad",
    )


def complete_runtime_evidence(evidence: dict[str, Any]) -> bool:
    return bool(evidence) and all(value is not None for value in evidence.values())


def main() -> int:
    gate = read_json(STAGE0 / "poc-recovery-gate-set-stage0-v0.4.json")
    protocol = read_json(STAGE0 / "poc-recovery-protocol-stage0-v0.4.json")
    readiness = read_json(EVIDENCE / "readiness.json")
    analysis = read_json(EVIDENCE / "jsr305-exclusion-analysis-2026-08-12.json")
    roles = read_json(EVIDENCE / "review-roles.json")
    provenance = read_json(EVIDENCE / "sqlite-platform-provenance.json")
    validate_policy(gate, protocol, readiness, analysis, roles)

    blockers: list[str] = []
    report_path = ROOT / readiness["dependencyExclusionPolicy"]["futureResolvedGraphReportLocator"]
    report_present = report_path.is_file()
    require(
        readiness["dependencyExclusionPolicy"]["futureResolvedGraphReportPresent"] is report_present,
        "Future graph report presence claim does not match the repository",
    )
    if report_present:
        validate_future_graph(read_json(report_path))
    else:
        blockers.append("future exact :poc:recovery graph/package/R8 report is absent")

    approvals = readiness["approvals"]
    product_ip = roles["roles"]["stage0ProductIp"]
    independent = roles["roles"]["independentRecoveryEngineeringSecurity"]
    if approvals["futureActualGraphProductIpDisposition"] != "APPROVED":
        blockers.append("future actual recovery graph Product/IP disposition is not approved")
    if product_ip["futureExactGraphDisposition"]["actualGraphApproved"] is not True:
        blockers.append("review-role evidence still marks the future actual graph open/blocked")
    if independent["status"] != "APPROVED_FOR_EXACT_RECOVERY_V0_4_IMPLEMENTATION_AND_PHASE_A":
        blockers.append("distinct accountable recovery Engineering/Security approval is absent")
    if not isinstance(independent["reviewer"], str) or not independent["reviewer"]:
        blockers.append("distinct accountable recovery Engineering/Security reviewer is unassigned")
    if readiness["runtimeDependencyAdded"] is not True:
        blockers.append("separately scoped recovery runtime dependency is absent")
    if readiness["recoveryModuleExists"] is not True or readiness["harnessImplemented"] is not True:
        blockers.append("separately scoped :poc:recovery harness is absent")
    if readiness["nonMetricImplementationVerificationPassed"] is not True:
        blockers.append("non-metric v0.4 implementation verification is absent")
    if readiness["exactFutureResolvedGraphReviewed"] is not True or not report_present:
        blockers.append("exact future recovery graph review is incomplete")
    if readiness["implementationAllowedByThisPackage"] is not True:
        blockers.append("implementationAllowedByThisPackage=false")

    environments = {item["id"]: item for item in provenance["phaseA"]["environments"]}
    for environment_id in ("PINNED_API36_X86_64_EMULATOR", "PHYSICAL_D2"):
        evidence = environments[environment_id]["requiredFreshRuntimeEvidence"]
        if not complete_runtime_evidence(evidence):
            blockers.append(f"fresh {environment_id} SQLite/Keystore/filesystem preflight is absent")
        else:
            require(evidence["effectiveJournalMode"] == "WAL", f"{environment_id} journal_mode is not WAL")
            require(evidence["effectiveSynchronousMode"] == "FULL", f"{environment_id} synchronous is not FULL")
            require(evidence["effectiveWalAutocheckpoint"] == 0, f"{environment_id} wal_autocheckpoint is not zero")
            require(evidence["effectiveForeignKeys"] is True, f"{environment_id} foreign_keys is not ON")
            require(evidence["protocolId"] == PROTOCOL_ID, f"{environment_id} preflight protocol drift")

    if gate["executionAllowed"] is not True or protocol["executionAllowed"] is not True or readiness["executionAllowed"] is not True:
        blockers.append("executionAllowed=false")
    if gate["executionAuthorization"]["status"] != "AUTHORIZED_BY_PROJECT_OWNER":
        blockers.append("separate Project-owner execution authorization is absent")
    if roles["roles"]["executionAuthorizer"]["status"] != "AUTHORIZED_FOR_NAMED_PHASE_AND_COMMIT":
        blockers.append("execution-authorizer role remains withheld")
    if provenance["phaseA"]["executionAllowed"] is not True:
        blockers.append("SQLite/device provenance still withholds Phase A")

    require(readiness["killCampaignExecuted"] is False and readiness["deviceTestsExecuted"] is False and readiness["benchmarksExecuted"] is False, "Readiness evidence must precede measured/device execution")
    require(readiness["productionAppChanged"] is False, "Production :app changed")
    require(not blockers, "; ".join(blockers))
    print("POC-RECOVERY-001 v0.4 execution readiness passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, StopIteration, json.JSONDecodeError) as error:
        print(f"BLOCKED {error}", file=sys.stderr)
        raise SystemExit(1)
