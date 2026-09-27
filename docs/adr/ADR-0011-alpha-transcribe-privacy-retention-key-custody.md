# ADR-0011: Internal Alpha Transcribe candidate, privacy controls and key custody

## Status and authority

APPROVED_BY_PROJECT_OWNER for bounded Alpha architecture and the exact OD-11C-01..12 decisions. Version 0.1; 2026-09-28; source baseline `cccf85f982436af0bf9675d738dfe2dd61308842`. The [v0.2 policy/evidence package](../design/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_2.md) Appendix A preserves every owner decision verbatim. This ADR records those decisions, not a new legal/security signature or provider/runtime admission.

## Context and decision

ADR-0009's DORA control boundary and ADR-0010's narrow first-Alpha scope remain. Adopt invitation-only access, exactly eu-central-1 content processing, standard Amazon Transcribe as a documentary candidate, customer S3 input/output and mandatory effective Transcribe service-improvement opt-out. Public registration, region fallback/migration and content-sharing support are not authorized. AWS global administrative IAM/Organizations are disclosed separately and never receive recording audio/transcripts.

Local original remains until explicit deletion, automatic retention OFF. Cloud audio is temporary: immediate cleanup after durable DORA result ingestion, with a 24-hour ceiling from first accepted Cloud byte for unfinished/failed/cancelled/abandoned processing. No retry or lost client ACK extends it. Explicit object/part deletion plus deadline watchdog is required; S3 lifecycle is only a backstop. No long-term audio backup, archival tier, cross-region copy or audio-preserving versioning. Accepted DORA transcript is distinct and survives audio-only deletion. Transient output is not the durable result store.

The 90-day Transcribe job-record quota is automatic and non-adjustable, but DeleteTranscriptionJob permits earlier terminal-job deletion. DORA uses that deletion; the owner conditional metadata exception does not license convenience retention. A separate immutable 90-day CloudTrail management history is independently documented, not inferred from Transcribe. Do not generalize either to audio, DORA logs or backups.

## Identity, authorization and revocation

Use invited installation proof-of-key and DORA-issued, finite, audience/action-bound access and rotating refresh credentials. Current ownership, consent and deletion epochs are checked independently for upload, processing, retrieval and deletion. Delete authorization does not require a live ASR grant. Optional accounts remain separate; no consumer IdP is selected and no Cloud account is needed for local recording/offline/installed Local ASR.

DORA's controlled ingress enforces revocation before each bounded upload part and closes/fences sessions when server revocation commits. Android receives neither persistent AWS secrets nor unrestricted S3 upload authority. Expiring signed URLs alone do not meet revocation semantics. Nonces/key proof and idempotency stop replay/cross-owner changes; finite parameter profiles and race tests are required at AUTH/UPLOAD/SECRETS activation, not falsely supplied here.

## Payload/key custody and audit

Authenticated TLS is mandatory (minimum TLS 1.2). Original local wrapped keys never leave Android. For DORA-controlled S3 use SSE-KMS with customer-managed, single-region symmetric input/output keys; output explicitly binds OutputEncryptionKMSKeyId. DORA durable result storage uses a separately scoped envelope/custody boundary. This replaces the earlier Alpha choice left open between client envelope and transport-only payload, without claiming opaque client ciphertext can be processed by Transcribe.

DORA Backend/Security is key custodian. Separate key administrators, control API, input ingress, job dispatcher, result ingester and audit roles. Only job-scoped data-access roles trusted to `transcribe.amazonaws.com` with account/source restrictions receive necessary input-read/decrypt and output-write/key permissions; result ingestion can decrypt output. Ordinary control/query roles cannot generally decrypt. Actual role ARNs/policies and parameter profiles require later exact-configuration validation.

DORA validates ledger epochs before dispatch/worker leases. Managed Transcribe itself necessarily reads audio and uses provider-controlled internal EBS encryption; a customer output key is not custody of every provider copy. No E2EE, instant stop, guaranteed memory zeroization or per-record cryptographic-erasure claim. Record KMS operations/policy/rotation and DORA authorization events with content-free metadata; no audio, transcript, token or sensitive KMS context in logs. Rotation/revocation is owned by the custodian and must preserve needed ciphertext access until controlled retirement; shared-key disable is not record deletion.

## Durable ledgers and bounded threat review

Preserve the versioned consent ledger, independent durable deletion outbox/tombstone, exact audio-only/whole-recording and local/remote axes, scoped receipts, retry reconciliation, late-callback fences and restore-before-serve suppression specified in v0.2. A failed/in-progress provider job delete remains pending; no hard-cancel API or all-copy erasure promise is invented.

Internal technical reviewer: OpenAI Codex. Architecture acceptance: Project Owner OD-11C-12. CONTROL's frozen wording requires reviewed/accepted bounded design with a reviewer and risk dispositions; it does not explicitly require an independently credentialed Security signer. RDY-012's ledger/identity design and RDY-013's custody choice are accepted. RDY-011 retains qualified privacy scope approval; RDY-013/018 retain only the cross-gate retention facts/periods in RT-01/02. This is not a penetration-test result or independent certification.

## Reconciliation and admission limits

This explicit owner amendment refines Technical Plan §§13/24/28 and the current Product Decisions Alpha overlay for installation identity, region, temporary audio and key custody. It does not rewrite DEC-001..048, historical ADR-0009/0010, accepted scope/gaps, Recovery or Stage 5.

SC-01 and PC-02 are RESOLVED. Qualified actual-scope approval (PC-01), internal-provider audio deadline and enumerated service-record periods (RT-01), non-audio restore/tombstone horizons (RT-02) remain. SC-02 follows the frozen PRIVACY/RETENTION dependencies. PRIVACY BLOCKED; RETENTION PARTIALLY_SATISFIED; CONTROL BLOCKED. EVALUATION/PROVIDER OPEN; ADMISSION BLOCKED. 6.2D/6.3 NOT_RUN; first audio NOT_READY; runtime NOT_IMPLEMENTED. No deployment, provider quality admission, resource creation or next-task execution.
