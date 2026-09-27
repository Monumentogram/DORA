# DORA AWS selected for Alpha — owner decision v0.1

Decision ID: `AWS_SELECTED_FOR_ALPHA`

Date: 2026-09-27

State: **APPROVED_BY_PROJECT_OWNER**

Scope: **FIRST_INTERNAL_ALPHA_PLATFORM_SELECTION_ONLY**

Repository baseline: `Monumentogram/DORA`, `chat/alpha-asr-runner-scope`, `5b2a43f34ef83efb2521bcbb07613e122baa50cd`.

## Authority and decision

The Project Owner explicitly selected AWS in the 27 September 2026 task instruction after
18.1A and independently checked 18.1B. This record preserves that supplied approval; it does
not claim a new Legal/Security signature or repeat the economic model's independent audit.

`AWS = SELECTED_ALPHA_PLATFORM` (`AWS = SELECTED ALPHA PLATFORM` in the owner instruction).
One active primary provider/platform for Alpha. Use the [first Alpha scope](../product/DORA_ALPHA_SCOPE_V0_1.md)
and [ADR-0010](../adr/ADR-0010-first-alpha-scope-and-aws-platform.md).

## Rationale and evidence

- `18.1A = PASS / MARKET_AND_PRICING_DATASET_READY`.
- `18.1B = PASS / TCO_AND_ALPHA_PROVIDER_ECONOMICS_READY`.
- [Existing Alpha Sheet](https://docs.google.com/spreadsheets/d/1dgZr-BGlI1v8iPEK0ay9CI9mGhiGlJvG2-tTxyJhhOc/edit), `Этапы!A123:J124`, read on 2026-09-27, records these independently
  checked packages. 18.1A: 936 pricing rows, 182 service/model cards, 262 sources; 18.1B:
  20 sheets, 191494 live formulas, 540 BASE comparisons, 36 Alpha rows. These are inherited
  evidence facts, not fresh calculations or current-price verification in this task.
- A_AWS BASE economic reference: USD 78.82–253.14/month at 10–100 users under the model's
  assumptions. It is not an operational budget, a provider quote or an admitted configuration.
- Some multi-provider configurations have lower raw cash TCO. AWS is **not** claimed cheapest
  overall. The owner prioritizes one-platform Alpha integration/operations simplicity within
  the economic range, while preserving switchable adapters.
- Future diarization, LLM, embeddings and infrastructure needs remain architectural consumers;
  selection does not assert that AWS services for them have been evaluated or admitted.

## Decision boundary

`AWS_TECHNICAL_ADMISSION = NOT_RUN` until a separate 6.2D result. Exact AWS service/configuration,
region and model remain unadmitted. `FIRST_REAL_AUDIO_ADMISSION = NOT_READY`;
`CLOUD_RUNTIME = NOT_IMPLEMENTED`; `CLOUD_ALPHA_ACCEPTANCE = NOT_RUN`.
6.2 and 11.1C remain prerequisites; 6.3 remains BLOCKED. No runtime start, real-audio upload,
production choice, privacy/security admission, credentials or operational spending is authorized.

## Portability and change control

AWS SDK/types/errors/request schemas end inside adapter/infrastructure boundaries. Core/domain
contracts are provider-neutral for ASR, diarization, LLM and embeddings, and storage/queue/jobs
where practical. AWS -> Provider B remains possible without rewriting the core product model.
No second adapter is required for Alpha. Privileged AWS credentials are prohibited in APK.

Revisit selection on failed 6.2D quality/privacy/availability admission, material cost or terms
change, or approved scope expansion. A failing AWS admission does not silently select another
provider; a versioned owner decision and affected gate review are required.
Historical 6.2C and Stage 5 evidence are unchanged; this decision prospectively supersedes only
the platform-level NOT SELECTED / conditional economic candidate state.
