#!/usr/bin/env python3
"""Fail closed unless POC-RECOVERY-001 v0.3 has complete, explicit execution authority."""

from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
STAGE0 = ROOT / "docs" / "stage0"
EVIDENCE = ROOT / "docs" / "evidence" / "poc-recovery-001"
GATE_V03 = "poc-recovery-stage0-v0.3"
PROTOCOL_V03 = "poc-recovery-protocol-stage0-v0.3"
JSR305_POLICY_ID = "REC-JSR305-EXCLUDE-001"
JSR305_COORDINATE = "com.google.code.findbugs:jsr305:3.0.2"
JSR305_R8_RULES = [
    "-dontwarn javax.annotation.Nullable",
    "-dontwarn javax.annotation.concurrent.GuardedBy",
    "-dontwarn javax.annotation.concurrent.ThreadSafe",
]


def read_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def complete_runtime_evidence(evidence: dict[str, Any]) -> bool:
    return bool(evidence) and all(value is not None for value in evidence.values())


def validate_jsr305_exclusion_policy(
    readiness: dict[str, Any], analysis: dict[str, Any]
) -> bool:
    require(
        analysis["status"]
        == "TECHNICAL_EXCLUSION_PROVEN_WITH_NARROW_R8_RULE_PRODUCT_IP_DECISION_PENDING",
        "JSR305 technical exclusion analysis is missing or stale",
    )
    require(
        analysis["technicalConclusion"]["bareExcludeAloneAccepted"] is False
        and analysis["technicalConclusion"]["completeArtifactExclusionFeasible"] is True,
        "JSR305 conditioned-exclusion conclusion is not intact",
    )
    analysis_policy = analysis["prospectivePolicy"]
    policy = readiness["dependencyExclusionPolicy"]
    require(policy["policyId"] == analysis_policy["policyId"] == JSR305_POLICY_ID, "JSR305 policy ID drift")
    require(
        policy["rootCoordinate"] == analysis_policy["rootCoordinate"]
        == "com.google.crypto.tink:tink-android:1.23.0",
        "JSR305 policy root coordinate drift",
    )
    require(
        policy["forbiddenResolvedCoordinate"]
        == analysis_policy["forbiddenResolvedCoordinate"]
        == JSR305_COORDINATE,
        "JSR305 forbidden coordinate drift",
    )
    require(
        policy["requiredResolvedComponentCount"]
        == analysis_policy["requiredResolvedComponentCount"]
        == 0,
        "JSR305 policy no longer requires a zero component count",
    )
    require(
        policy["compileOnlyOrAlternatePathAllowed"] is False
        and analysis_policy["compileOnlyOrAlternatePathToForbiddenCoordinateAllowed"] is False,
        "JSR305 compileOnly or alternate dependency path was allowed",
    )
    require(
        policy["allResolvableConfigurationsAndConsumersRequired"] is True,
        "JSR305 policy does not cover every resolvable configuration and consumer",
    )
    require(
        policy["exactNarrowR8RuleRequired"] is True
        and analysis_policy["requiredR8Rules"] == JSR305_R8_RULES
        and analysis_policy["broaderJavaxAnnotationDontwarnAllowed"] is False,
        "JSR305 exact narrow R8 rule policy drift",
    )
    require(
        policy["unresolvedR8MissingClassesAllowed"] is False,
        "Recovery readiness policy allows unresolved R8 missing classes",
    )

    report_locator = policy["futureResolvedGraphReportLocator"]
    require(
        report_locator == analysis_policy["futureResolvedGraphReportLocator"],
        "JSR305 future graph report locator drift",
    )
    report_path = ROOT / report_locator
    report_present = report_path.is_file()
    require(
        policy["futureResolvedGraphReportPresent"] is report_present,
        "JSR305 future graph report presence claim does not match the repository",
    )
    if not report_present:
        return False

    report = read_json(report_path)
    require(report["schemaVersion"] == 1 and report["pocId"] == "POC-RECOVERY-001", "Future recovery graph report identity drift")
    require(report["policyId"] == JSR305_POLICY_ID, "Future recovery graph report policy drift")
    require(isinstance(report["exactCommit"], str) and len(report["exactCommit"]) == 40, "Future recovery graph report lacks exact commit")
    require(report["allResolvableConfigurationsEnumerated"] is True, "Not every resolvable recovery configuration was enumerated")
    require(report["allRecoveryConsumersEnumerated"] is True, "Not every recovery consumer was enumerated")
    require(isinstance(report["coveredModules"], list) and report["coveredModules"], "Future graph report covers no modules")
    configurations = report["configurations"]
    require(isinstance(configurations, list) and configurations, "Future graph report covers no configurations")
    require(report["configurationCount"] == len(configurations), "Future graph report configuration count drift")
    require(
        all(
            item["canBeResolved"] is True
            and isinstance(item["module"], str)
            and bool(item["module"])
            and isinstance(item["name"], str)
            and bool(item["name"])
            and item["jsr305Components"] == []
            for item in configurations
        ),
        "JSR305 appears in a covered configuration or configuration evidence is incomplete",
    )
    require(report["jsr305ResolvedComponentCount"] == 0, "JSR305 resolved component count is nonzero")
    require(report["scopedExcludeVerified"] is True, "Scoped Tink JSR305 exclusion was not verified")
    shrinker = report["narrowR8Rule"]
    require(shrinker["rules"] == JSR305_R8_RULES and shrinker["status"] == "PASSED", "Exact narrow JSR305 R8 rule was not verified")
    require(report["nonMetricDebugBuildPassed"] is True, "Future non-metric debug build did not pass")
    require(report["nonMetricReleaseR8BuildPassed"] is True, "Future non-metric release/R8 build did not pass")
    require(report["unresolvedR8MissingClasses"] == [], "Future release/R8 report contains unresolved missing classes")
    require(report["packagedJsr305ClassDefinitionCount"] == 0, "Packaged output contains JSR305 class definitions")
    return True


def main() -> int:
    gate = read_json(STAGE0 / "poc-recovery-gate-set-stage0-v0.3.json")
    protocol = read_json(STAGE0 / "poc-recovery-protocol-stage0-v0.3.json")
    readiness = read_json(EVIDENCE / "readiness.json")
    roles = read_json(EVIDENCE / "review-roles.json")["roles"]
    provenance = read_json(EVIDENCE / "sqlite-platform-provenance.json")
    license_notice = read_json(EVIDENCE / "license-notice-inventory.json")
    authenticity = read_json(EVIDENCE / "dependency-ip-authenticity-v0.3.json")
    jsr305_exclusion = read_json(EVIDENCE / "jsr305-exclusion-analysis-2026-08-12.json")

    future_graph_present = validate_jsr305_exclusion_policy(readiness, jsr305_exclusion)

    require(gate["gateSetVersion"] == GATE_V03, "Active recovery Gate Set is not v0.3")
    require(protocol["protocolId"] == PROTOCOL_V03, "Active recovery protocol is not v0.3")
    require(gate["supersedes"]["gateSetVersion"] == "poc-recovery-stage0-v0.2" and gate["supersedes"]["disposition"] == "SUPERSEDED_AUDIT_ARTIFACT_NON_EXECUTABLE", "v0.2 Gate Set supersession record absent")
    require(protocol["supersedes"]["protocolId"] == "poc-recovery-protocol-stage0-v0.2" and protocol["supersedes"]["disposition"] == "SUPERSEDED_AUDIT_ARTIFACT_NON_EXECUTABLE", "v0.2 protocol supersession record absent")
    require([item["gateSetVersion"] for item in gate["retainedAuditArtifacts"]] == ["poc-recovery-stage0-v0.1", "poc-recovery-stage0-v0.2"], "v0.1/v0.2 Gate Set audit history absent")
    require([item["protocolId"] for item in protocol["retainedAuditArtifacts"]] == ["poc-recovery-protocol-stage0-v0.1", "poc-recovery-protocol-stage0-v0.2"], "v0.1/v0.2 protocol audit history absent")

    require(gate["executionAllowed"] is True, "executionAllowed=false in the recovery Gate Set v0.3")
    require(protocol["executionAllowed"] is True, "executionAllowed=false in the recovery protocol v0.3")
    require(readiness["executionAllowed"] is True, "executionAllowed=false in readiness evidence")
    require(gate["status"] == "APPROVED_FOR_AUTHORIZED_EXECUTION", "Gate Set lacks approved execution status")
    authorization = gate["executionAuthorization"]
    require(authorization["status"] == "AUTHORIZED_BY_PROJECT_OWNER", "Separate Project-owner execution authorization absent")
    require(authorization["authorizedBy"] == "Project owner", "Unexpected execution authorizer")
    require(bool(authorization["authorizedOn"]) and bool(authorization["authorizationRecord"]), "Execution authorization record incomplete")

    approvals = gate["approvalState"]
    require(approvals["productIpFinalApproval"] is True, "Final Product/IP approval absent")
    require(bool(approvals["approvedReviewer"]) and bool(approvals["approvedOn"]), "Product/IP approval identity/date absent")
    require(bool(approvals["accountableIndependentEngineeringSecurityReviewer"]), "Accountable recovery Engineering/Security reviewer absent")
    require(approvals["currentCodexReviewClaimedFormallyIndependent"] is False, "Codex remediation cannot satisfy independent review")
    require(authenticity["overallStatus"] == "AUTHENTICITY_VERIFIED_FOR_PRODUCT_IP_APPROVAL", "Supply-chain authenticity/license package is not cleared for Product/IP approval")
    verified_authenticity_states = {
        "AUTHENTICITY_VERIFIED_PUBLISHER_BOUND_SIGNATURE",
        "AUTHENTICITY_VERIFIED_EXACT_REPRODUCIBLE_SOURCE",
        "AUTHENTICITY_VERIFIED_MULTISOURCE_CORRESPONDENCE",
    }
    require(all(component["authenticityStatus"] in verified_authenticity_states for component in authenticity["components"]), "At least one coordinate is not authenticity-verified")
    require(authenticity["approvalBoundary"]["productIpFinalApproval"] is True, "Authenticity evidence lacks Product/IP approval linkage")
    require(authenticity["approvalBoundary"].get("productIpApprovalAllowedWhileLicenseConflict") is False and not authenticity["approvalBoundary"].get("blockers"), "License conflict or other Product/IP blocker remains")
    require(license_notice["evaluationApproved"] is True, "Exact Stage 0 Product/IP evaluation approval absent")
    exclusion_policy = readiness["dependencyExclusionPolicy"]
    require(exclusion_policy["productIpAccepted"] is True, "Project-owner / Product-IP acceptance of REC-JSR305-EXCLUDE-001 absent")
    require(bool(exclusion_policy["acceptedBy"]) and bool(exclusion_policy["acceptedOn"]), "JSR305 exclusion acceptance identity/date absent")

    product_ip = roles["stage0ProductIp"]
    require(product_ip["status"] == "APPROVED_FOR_EXACT_STAGE0_EVALUATION" and product_ip["finalApproved"] is True, "Product/IP role approval absent")
    require(bool(product_ip["approvedReviewer"]) and bool(product_ip["approvedOn"]), "Product/IP approval record incomplete")
    independent = roles["independentRecoveryEngineeringSecurity"]
    require(independent["status"] == "APPROVED_FOR_EXACT_RECOVERY_V0_3_IMPLEMENTATION_AND_PHASE_A", "Accountable recovery Engineering/Security approval absent")
    require(isinstance(independent["reviewer"], str) and bool(independent["reviewer"]), "Independent reviewer unassigned")
    require(bool(independent["approvedReviewer"]) and bool(independent["approvedOn"]), "Independent review record incomplete")
    require(independent["currentCodexRemediationClaimedFormallyIndependent"] is False, "Codex remediation cannot claim formal independence")
    require(independent["replacesProductionSecurity"] is False, "Recovery review cannot claim Production Security approval")

    require(readiness["status"] == "READY_FOR_AUTHORIZED_PHASE_A_EXECUTION", "Readiness status is not executable")
    require(readiness["blockers"] == [], "Readiness blockers remain")
    require(readiness["runtimeDependencyAdded"] is True, "Separately scoped recovery dependency is absent")
    require(readiness["recoveryModuleExists"] is True and readiness["harnessImplemented"] is True, "Separately scoped recovery harness is absent")
    require(readiness.get("nonMetricImplementationVerificationPassed") is True, "Non-metric v0.3 implementation verification absent")
    require(readiness.get("exactFutureResolvedGraphReviewed") is True and future_graph_present, "Exact zero-JSR305 future Gradle-resolved graph review absent")
    require(readiness["killCampaignExecuted"] is False and readiness["deviceTestsExecuted"] is False and readiness["benchmarksExecuted"] is False, "Readiness evidence must precede measured/device execution")
    require(readiness["productionAppChanged"] is False, "Production :app must remain unchanged")
    require(provenance["phaseA"]["executionAllowed"] is True, "SQLite/device provenance still withholds Phase A execution")

    candidates = {candidate["id"]: candidate for candidate in protocol["candidates"]}
    require(candidates["REC-STREAM-TINK"]["construction"]["status"] == "IMPLEMENTED_AND_NON_METRICALLY_VERIFIED", "Streaming construction implementation verification absent")
    require(candidates["REC-STREAM-TINK"]["checkpointModel"]["status"] == "IMPLEMENTED_AND_NON_METRICALLY_VERIFIED", "Streaming lookahead implementation verification absent")
    require(candidates["REC-MICROFILE-TINK"]["aeadTemplate"]["status"] == "IMPLEMENTED_AND_NON_METRICALLY_VERIFIED", "Microfile AEAD implementation verification absent")
    require(candidates["REC-MICROFILE-TINK"]["manifest"]["status"] == "IMPLEMENTED_AND_NON_METRICALLY_VERIFIED", "Manifest/parser implementation verification absent")
    require(protocol["keyProtocol"]["status"] == "IMPLEMENTED_AND_NON_METRICALLY_VERIFIED", "Key protocol implementation verification absent")

    environments = {item["id"]: item for item in provenance["phaseA"]["environments"]}
    emulator_evidence = environments["PINNED_API36_X86_64_EMULATOR"]["requiredFreshRuntimeEvidence"]
    d2_evidence = environments["PHYSICAL_D2"]["requiredFreshRuntimeEvidence"]
    require(complete_runtime_evidence(emulator_evidence), "Fresh exact-commit emulator SQLite preflight absent")
    require(complete_runtime_evidence(d2_evidence), "Fresh exact-commit D2 SQLite preflight absent")
    for environment_id, evidence in (("emulator", emulator_evidence), ("D2", d2_evidence)):
        require(evidence["effectiveJournalMode"] == "WAL", f"{environment_id} journal_mode is not WAL")
        require(evidence["effectiveSynchronousMode"] == "FULL", f"{environment_id} synchronous is not FULL")
        require(evidence["effectiveWalAutocheckpoint"] == 0, f"{environment_id} wal_autocheckpoint is not zero")
        require(evidence["effectiveForeignKeys"] is True, f"{environment_id} foreign_keys is not ON")
        require(evidence["protocolId"] == PROTOCOL_V03, f"{environment_id} preflight protocol drift")

    print("POC-RECOVERY-001 v0.3 execution readiness passed")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, KeyError, StopIteration, json.JSONDecodeError) as error:
        print(f"BLOCKED {error}", file=sys.stderr)
        raise SystemExit(1)
