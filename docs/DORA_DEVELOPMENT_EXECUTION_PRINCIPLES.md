# DORA Development Execution Principles

## Purpose

Apply the lessons from Recovery integration to future DORA work: deliver observable
product outcomes with rigorous, proportionate verification. This is an execution
policy, not a new stage, approval gate, validator or CI requirement. It does not
change product scope, accepted evidence, runtime, or existing stage authority.

## Ten principles

1. **Product-first execution.** An implementation stage delivers working product
   behavior or an actually integrated component. Documentation, evidence,
   governance and CI support acceptance; they cannot replace the product outcome.
2. **Reuse accepted evidence.** Reuse an accepted PASS unless a concrete factual
   change can invalidate it. Branch movement alone does not justify repeating a
   historical campaign. Verify the affected integration boundary.
3. **Distinguish lifecycle states.** RESEARCH / COMPONENT PROVEN means the component
   is demonstrated within its recorded scope; INTEGRATED means it is connected to
   the intended application path; PRODUCT-READY / ACCEPTED means the observable
   product acceptance criteria are satisfied. Never use these states as synonyms.
4. **One real risk, one gate.** Validator fixes, allowlist updates, evidence
   publication, CI metadata corrections and documentation closure belong to the
   parent task. They warrant a separate gate only if they reveal a new
   product, security or data risk requiring a distinct decision.
5. **Preflight downstream validators.** Before final candidate publication, inspect
   and run every applicable validation layer against its actual topology and path
   inventory: release, governance, architecture boundaries, evidence/path
   allowlists, dependency checks and CI workflow assumptions. Do not knowingly
   publish a candidate that a deterministic downstream validator will reject.
6. **Keep bounded infrastructure remediation in the task.** Repair directly affected
   tests, validators or CI in the parent task when all conditions hold: product
   behavior is unchanged; security/privacy/product scope does not expand; the
   authorized task introduced a deterministic incompatibility; the correction is
   narrowly bounded; negative controls remain or become stronger; and the
   correction is tested. A mechanical compatibility correction alone requires no
   separate owner decision. Escalate changes to product semantics,
   security/privacy authority, architecture, accepted evidence meaning or task scope.
7. **Validate lifecycle states, not accidental Git details.** Model the intended
   accepted lifecycle using immutable provenance and bounded state transitions
   where needed. Adding documentation, advancing an authorized integration to its
   next expected state, or renaming a branch with valid provenance should not by
   itself require validator code changes. Avoid accidental branch/SHA choreography.
8. **Keep historical evidence historical.** Preserve PASS/FAIL results, exact source
   identity, limitations and original execution context. Assess applicability to
   affected behavior; never relabel an old campaign as newly run on a composite tree.
9. **Track durable roadmap milestones.** The roadmap and Google Sheet track durable
   product/integration outcomes. Do not add permanent stage rows for diagnostic
   commits, CI failures, evidence commits or validator remediation. Keep significant
   failures in the relevant milestone's evidence/current-proof fields.
10. **Require user-visible acceptance.** Define an observable end-to-end result for
    implementation whenever technically possible. Contracts/docs alone can satisfy
    a stage only when it is explicitly an architecture/security decision gate,
    such as 7.4; they do not establish product readiness.

## Decision rules

- **New gate:** Identify the concrete new product/security/data risk and the
  distinct decision needed to control it. Create a gate only when the parent scope
  and its existing acceptance cannot resolve that decision. Otherwise keep the
  work, correction and evidence in the parent task. Escalation does not
  automatically create a permanent roadmap gate.
- **Evidence reuse:** Compare the proposed code, configuration, dependencies,
  data/security boundaries and target environment with the accepted result's exact
  source, assumptions and coverage. Reuse the PASS when applicable and no concrete
  invalidating change is identified; briefly record why. If a change invalidates
  part of it, retain the historical verdict and test only the affected behavior or
  integration boundary. Repeat a full campaign only with a factual reason that
  narrower verification cannot address.

## Standard task preflight

1. What user/product capability is being advanced?
2. What accepted evidence can be reused?
3. What code/data/security boundaries actually change?
4. Which validators will see the final candidate?
5. Can those validators be exercised before final publication?
6. What is the smallest end-to-end acceptance test?
7. What would constitute a genuinely new blocker?

Answer these in the existing task context; no separate governance artifact is
required. Resolve deterministic incompatibilities before publication. If a check
cannot run, state the missing environment/evidence and its impact; do not claim PASS.

## Definition of Done for implementation stages

- Intended product/integration behavior is implemented.
- Affected tests pass.
- Applicable existing evidence is reused correctly, with provenance and limits.
- Full applicable CI passes on the final candidate.
- No unresolved P0/P1 remains.
- Roadmap/status reflects the actual outcome and lifecycle state.
- Observable acceptance is demonstrated where applicable; any technical limitation
  is explicit and cannot silently become a product-readiness claim.

DONE does not require new governance artifacts unless they are necessary to prove
a changed risk boundary. This documentation-only policy task uses lightweight
documentation/repository consistency checks; it does not require new device or
unrelated historical campaigns and does not make this policy itself a CI gate.

## Application to the next stages

**Stage 7.4 remains NOT STARTED and mandatory.** It is a real architecture/security
gate because it changes the security boundary before real recording/storage.
Execute it as **one bounded architecture contract → independent review →
PASS/BLOCKED**. Do not split it into additional permanent gates unless an actual
new security decision requires one. This policy neither executes nor closes 7.4.

**Stage 8 remains NOT STARTED.** After its existing prerequisites and authorization,
prioritize a working local vertical slice on the physical target device. The first
meaningful product acceptance should demonstrate, on POCO M5 where applicable:

**Record → Pause/Resume → Stop → encrypted save → restart/crash → Recovery →
recording available.**

Stage 8 success cannot consist only of documents or synthetic governance evidence.
This policy does not start Stage 8 or change either stage's status.
