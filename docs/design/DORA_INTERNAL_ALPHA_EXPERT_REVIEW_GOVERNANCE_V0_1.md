# DORA internal Alpha expert-review governance v0.1

Owner-approved prospective governance. The [v0.4 Privacy/Retention/Control overlay](DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_4.md) applies it to 11.1C. An internal decision is not a professional legal opinion.

- **schema_version**: 1.0

- **governance_version**: 0.1

- **package_id**: DORA_INTERNAL_ALPHA_EXPERT_REVIEW_GOVERNANCE_V0_1

- **assessment_date**: 2026-09-28

- **baseline_commit**: 7e05a74984a2cfbac3790b1f963b392ef7870fd8

- **status**: APPROVED_BY_PROJECT_OWNER

- **scope**: Prospective reversible bounded CLOSED_INTERNAL_ALPHA decisions only

## owner authority


- **request_sha256**: a796354591fa758e60f541435ee397211eab71a062ce016f447c479b45d4b8ca

### decisions


- OD-11C-23

- OD-11C-24

- OD-11C-25

- OD-11C-26


- **approval**: Explicit user Project Owner policy in this task


## principles


- AI expert review may supply structured technical/domain review.

- Review identifies sources, assumptions, risks, residuals and limitations.

- Project Owner explicitly accepts or rejects each decision; AI approval cannot substitute for Owner acceptance.

- AI never impersonates a legally/professionally credentialed reviewer.

- Qualified external review is required when law/contract explicitly requires it, external users/customer data enter scope, public beta/release, certification/attestation is required, or an irreversible/high-risk boundary demands escalation.

- Historical BLOCKED/FAIL evidence is immutable; a prospective rule change requires its own version and authority, never a retroactive PASS.


## appropriate domains


- Privacy/Legal

- Security design

- licensing/compliance

- provider terms

- architecture risk

- product governance


## never substitutes


- physical device evidence

- runtime measurement

- actual penetration testing

- statutory signature/certification

- external contractual acceptance

- regulated professional act


## not a claim


- attorney opinion

- statutory legal certification

- DPO opinion

- regulator approval

- external audit

- public-release legal approval


- **law_boundary**: Project governance permission cannot waive applicable law, participant rights or binding contracts. Internal scope is not a statutory exemption. Credentialed acts, where required, remain external prerequisites before the affected act.

## future external gate


- **gate_id**: PRIVACY-LEGAL-EXTERNAL-001

- **status**: DEFERRED_MANDATORY_PRE_EXTERNAL_USE

- **qualified_review_status**: DEFERRED_TO_PRE_EXTERNAL_USE

- **residual_status**: DEFERRED_MANDATORY_BEFORE_EXTERNAL_USE

- **phase**: PRE_EXTERNAL_USE_MANDATORY_GATE

- **owner**: Project Owner commissions a qualified external Privacy/Legal reviewer

- **trigger_rule**: Mandatory BEFORE the earliest applicable trigger, including law/contract qualification before internal use when required. No trigger may be waived by an internal AI review or Owner acceptance.

### triggers


#### 1


- **index**: 1

- **trigger**: first external/non-internal Alpha user


#### 2


- **index**: 2

- **trigger**: first real customer production-like recording


#### 3


- **index**: 3

- **trigger**: processing conversations involving non-Alpha third parties


#### 4


- **index**: 4

- **trigger**: public beta


#### 5


- **index**: 5

- **trigger**: public release


#### 6


- **index**: 6

- **trigger**: material provider change


#### 7


- **index**: 7

- **trigger**: processing-region change


#### 8


- **index**: 8

- **trigger**: material processing-purpose expansion


#### 9


- **index**: 9

- **trigger**: significant new personal-data category


#### 10


- **index**: 10

- **trigger**: any legal/contractual circumstance explicitly requiring professional qualification



### verification scope


- Actual operating legal entity/controller identity

- Participant jurisdictions

- Actual lawful bases

- Controller/processor roles

- AWS contractual applicability

- International-transfer requirements

- Final user disclosure/privacy notice

- Participant rights handling

- Data subject request process

- Market-specific recording/communications laws


- **execution**: NOT_RUN

- **evidence**: No external professional approval received; future attributable qualified decision must bind actual scope, sources, authority and conditions.

- **accounting**: Separate prospective governance gate; not a fortieth frozen Cloud gate.

- **unknown_applicability**: Before affected processing, establish relevant facts. If it is unknown whether professional qualification is legally/contractually required, the affected operation fails closed pending that determination. This is not an unresolved internal design approval PC-01.


- **history**: Frozen gate v0.1 and Privacy/Retention/Control v0.1/v0.2/v0.3 remain unchanged. Full MVP/public-release governance is not weakened.

## non execution


- **android_runtime**: NOT_CHANGED

- **runtime_device_tests**: NOT_RUN / NOT_REQUIRED

- **aws_runtime_api_calls**: 0

- **real_cloud_audio**: 0

- **device_campaigns**: 0

- **asr_inference**: 0

- **recovery**: NOT_TOUCHED

- **pr86**: NOT_TOUCHED

- **stage_6_2D**: NOT_RUN

- **stage_6_3**: NOT_RUN

- **ci**: NOT_RUN_IN_THIS_DOCS_TASK
