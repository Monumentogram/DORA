# ADR-0010: First internal Alpha scope and AWS platform

## Status

APPROVED FOR FIRST INTERNAL ALPHA SCOPE / PLATFORM SELECTION. Version 0.1; 2026-09-27.
Authority: explicit Project Owner instruction recorded in the
[versioned AWS owner decision](../stage0/DORA_AWS_SELECTED_FOR_ALPHA_OWNER_DECISION_V0_1.md) and
[Alpha scope](../product/DORA_ALPHA_SCOPE_V0_1.md). Baseline: `5b2a43f34ef83efb2521bcbb07613e122baa50cd`.

## Context and decision

The Technical Plan describes the broader MVP, including speakers, meeting protocol, decisions,
tasks and summaries. This explicit owner-approved amendment freezes the **first minimal internal
Alpha**: microphone Start/Pause/Resume/Stop, safe original audio and accepted Recovery scope,
RU/EN transcript with honest timestamp availability and provenance, edits/reprocessing,
local history/lexical search, explicit export and controlled deletion/privacy. Bounded VAD and
chunk identity remain subject to 6.2 evidence/gap disposition.

Cloud ASR is required; Local installation is optional and Cloud-only supported. Advanced
product/AI capabilities are explicitly DEFERRED in the capability table, including all automatic
diarization/speaker flows and LLM/embeddings for the first Alpha. They remain full-MVP/future
roadmap requirements. This narrower Alpha does not remove Technical Plan MVP obligations or
waive the 14 approved Cloud acceptance conditions.

Select **AWS = SELECTED_ALPHA_PLATFORM**, one active primary platform for Alpha, based on
18.1A/18.1B and integration/operational simplicity. Some multi-provider configurations cost less
in raw cash; this is not an absolute cheapest-provider claim. Specific AWS service, configuration,
region and model require 6.2D technical admission. Stage 18's necessary Cloud implementation and
operations feed Stage 12 acceptance; Stage 13–17 and Stage 19 remain future/full-MVP work.

## Relationship to existing authorities

ADR-0009's DORA-controlled backend, original-audio data plane and independent consent/ownership
enforcement remain intact. Its provider-not-selected statement and frozen 6.2C snapshot are
historical; this ADR resolves platform selection prospectively, without changing their bytes.
The ASR product/data contracts remain intact. The scoped first-Alpha overlay is linked in
Product Decisions; earlier DEC-009/039 local-first planning is refined only within this approved
Cloud/optional-Local scope. DEC-010/011/012 privacy/region/retention decisions remain unresolved.

## Portability and consequences

Android -> DORA backend/control plane -> AWS adapter -> AWS service. No privileged AWS credentials
in APK. AWS SDK/types/errors/request schemas/semantics end inside their adapter/infrastructure
boundary; core/domain remains provider-neutral. Preserve replaceable ASR, diarization, LLM and
embeddings ports and storage/queue/job abstraction where practical. DORA owns normalized identity,
result/error/provenance/version semantics. AWS -> Provider B must not require core product model
rewrite; a second adapter or unused module is not required now.

One platform reduces Alpha integration/operations work, at the cost of accepting a potentially
higher raw cash bill and retaining concentration risk until later replacement evidence exists.
Technical failure requires explicit redecision; no silent fallback or widening of consent.

## Admission boundary

Validated reference: POCO M5 / Android 14 API 34 / arm64-v8a / RU and EN. minSdk 28 is compatibility
target only. Bounded Stage 5 timestamps NOT_EVALUABLE and actual 16KiB runtime NOT_RUN remain.
Scope freeze proves no integrated Alpha/runtime quality. AWS_TECHNICAL_ADMISSION = NOT_RUN;
FIRST_REAL_AUDIO_ADMISSION = NOT_READY; CLOUD_RUNTIME = NOT_IMPLEMENTED;
CLOUD_ALPHA_ACCEPTANCE = NOT_RUN. No existing technical/privacy/runtime gate is passed here.

Next: 6.2 readiness/gap disposition; then 11.1C policy/design and 6.2D before blocked 6.3.
This docs-only decision starts none of them and authorizes no merge or campaign.
