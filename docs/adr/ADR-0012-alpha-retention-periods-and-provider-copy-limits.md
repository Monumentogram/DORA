# ADR-0012 — Alpha retention periods and provider-copy limits

Status: APPROVED_BY_PROJECT_OWNER / OD-11C-13..22, 2026-09-28; bounded docs/design decision only.
Baseline: `99976ce21a5ee2fde408e1bee78b12902a675b81`; branch `chat/alpha-asr-runner-scope`.

## Context

[ADR-0011](ADR-0011-alpha-transcribe-privacy-retention-key-custody.md) accepted the Alpha architecture. Its retention residuals were valid in historical v0.2. New explicit owner decisions distinguish customer-controlled objects from hidden service copies and approve the missing periods. This ADR refines retention only; frozen gates and architecture remain intact.

## Decision

The [v0.3 human contract](../design/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_3.md) and [machine contract](../contracts/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_3.json) are the sole current successor for retention/dependency decisions. Their 56 stable lifecycle cells replace the earlier matrix prospectively; v0.1/v0.2 and ADR-0011 remain historical evidence. The exact supersession table preserves unaffected OD-11C-04/08/10 meaning.

DORA-controlled Cloud audio has a hard 24-hour maximum with explicit immediate safe post-success deletion, original inherited clock, multipart/version reconciliation and lifecycle fallback only. Transient output is separate from durable transcripts: safe immediate post-ingest deletion, failed/unaccepted maximum 24 hours. Provider jobs default 90 days but must be explicitly deleted earlier when terminal and safely reconciled/audited.

DORA ordinary metadata maximum 30 days; audit maximum 90 days. The authoritative consent record is retained until every related artifact AND authority is finally inactive, then history lasts 90 days. Revocation stops future unauthorized processing and record retention does not extend grant validity. Installation authority lasts while active, then nonsecret history 90 days. No reusable secret survives for audit.

No DORA Cloud audio backup/archive/cross-region replica or deleted-audio version. Approved non-audio backups have maximum restorable age 30 days, also bounded by original source expiry; stale live-replica exposure after deletion is <=30 days. Restore reconciles current deletion/authorization first. Tombstone earliest retirement is 120 days after effective logical deletion, guarded by resolved inventories/callbacks and absence of a longer incompatible documented horizon. Unknown callbacks cannot create state. Known relevant provider horizon >120 days requires exact owner review; unresolved work prevents expiry.

OD-11C-14 accepts strongest documented provider service purpose/deletion-control limitations. AWS does not publish the per-copy internal physical purge SLA in reviewed sources; FAQ purpose language is not converted into a temporal erasure guarantee. Opt-out excludes service-function copies from its deletion promise. API receipts remain scoped. This is an accepted limitation, not a fabricated numeric TTL or postponed decision.

## Consequences and verification

RT-01/RT-02 RESOLVED; RETENTION SATISFIED. PC-01 remains BLOCKED_EXTERNAL_APPROVAL; SC-02 BLOCKED_BY_PRIVACY_ONLY; CONTROL BLOCKED. The 39 gates are 6 SATISFIED, 0 PARTIALLY_SATISFIED, 5 OPEN, 3 BLOCKED, 25 NOT_RUN. 11.1C remains PARTIAL; 6.2D/6.3 NOT_RUN. Runtime enforcement, account configuration, restore/callback races and provider quality are later verification, not executed evidence. PC-02/SC-01 acceptance and ADR-0011 identity/key/control decisions remain unchanged.
