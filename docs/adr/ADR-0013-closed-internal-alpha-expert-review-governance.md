# ADR-0013: Closed internal Alpha expert-review governance

- Status: ACCEPTED_BY_PROJECT_OWNER (project governance only)
- Date: 2026-09-28
- Baseline: `7e05a74984a2cfbac3790b1f963b392ef7870fd8`
- Authority: OD-11C-23..26, explicit Project Owner amendment.

## Context

Frozen Privacy criterion 5 requires “qualified owner approval for actual scope”. Historical PC-01 interpreted this as external/qualified Privacy/Legal approval and blocked PRIVACY and dependent CONTROL after retention closure. No such external approval has been received. Historical frozen and v0.1/v0.2/v0.3 records are preserved byte-for-byte.

## Decision

Adopt [DORA_INTERNAL_ALPHA_EXPERT_REVIEW_GOVERNANCE_V0_1](../design/DORA_INTERNAL_ALPHA_EXPERT_REVIEW_GOVERNANCE_V0_1.md). Only for CLOSED_INTERNAL_ALPHA, actual independent AI-assisted review plus explicit Owner risk/scope acceptance, a hard participant boundary, current AWS evidence, versioned disclosure and accepted retention/security design are sufficient for project Privacy design-entry/admission governance. The [v0.4 overlay](../design/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_4.md) supplies the five effective Privacy criteria and CONTROL dependency readback.

Allowed real audio is only the Owner and explicitly invited, informed internal participants who opt into recording/Cloud tests. Customer/production-like calls, non-Alpha third-party voices, public/anonymous users, hidden/passive capture and employee surveillance remain outside scope. Synthetic fixtures retain existing authority. Mic-only explicit Start, all ASR/versioning grants, local operation without Cloud, ADR-0011 architecture and ADR-0012 retention remain unchanged.

PC-01 becomes RESOLVED_BY_INTERNAL_ALPHA_GOVERNANCE; SC-02 becomes RESOLVED. PRIVACY and CONTROL are SATISFIED_FOR_CLOSED_INTERNAL_ALPHA; RETENTION remains SATISFIED. 11.1C is PASS / PRIVACY_RETENTION_CONTROL_PREREQUISITES_SATISFIED_FOR_CLOSED_INTERNAL_ALPHA. No statutory legal certification, attorney/DPO opinion, regulator approval, external audit or public-release approval is asserted.

## Mandatory future review and operational conditions

PRIVACY-LEGAL-EXTERNAL-001 remains DEFERRED_MANDATORY_PRE_EXTERNAL_USE, with all ten earliest-trigger conditions and ten review subjects defined in the governance contract. It is outside the frozen 39-gate inventory. It also applies before internal use whenever law/contract requires qualified acts; unknown applicability fails closed for the affected operation. Owner governance cannot waive applicable legal duties.

Before real audio, establish actual operator/contact, participant jurisdictions, lawful basis/recording duties, AWS account contractual eligibility and a usable rights/DSR channel including credential loss; provide the applicable actual notice and obtain freely given participation. All twelve OD-11C-26 runtime/configuration checks remain NOT_RUN. Accepted policy text is not an activated consent screen. No account terms/signature, effective opt-out or live retention/security configuration is inferred.

## Consequences and preserved gates

This prospective amendment does not rewrite historical BLOCKED evidence. It accepts disclosed provider physical-copy/purge limitations as bounded design risk, not erasure proof. Retention of a consent record never authorizes processing after revoked consent. General expert-review policy does not replace factual execution or required professional acts.

39 gates: 6 SATISFIED + 2 SATISFIED_FOR_CLOSED_INTERNAL_ALPHA + 0 PARTIALLY_SATISFIED + 5 OPEN + 1 BLOCKED + 25 NOT_RUN. Internal normalized satisfaction is 8, explicitly scope-qualified. Only PRIVACY/CONTROL changed. ADMISSION remains BLOCKED by EVALUATION/PROVIDER; runtime/exit gates are unchanged. 6.2D is ELIGIBLE_TO_START / NEXT with execution NOT_RUN; 6.3 is BLOCKED with execution NOT_RUN. No automatic next-stage start.
