# ADR-0019: Cloud identity, authorization and ownership

Status: ACCEPTED FOR 7.2E LOGICAL CONTRACT ONLY. Date: 2026-09-30.

## Context and decision

ADR-0009 makes DORA the mandatory Cloud control-plane authority. Preserve the
ADR-0011 invited installation proof-of-key, finite credential lifecycle, replay
resistance and independent ownership/consent/deletion checks. ADR-0012/0013
retention and closed internal Alpha admission are unchanged.

Cloud authorization is the intersection of authenticated principal, server-owned
recording/source relationship, current consent/scope and operation-specific policy.
No one component grants the other three. Authentication is not recording consent;
installation IDs and client-supplied resource IDs are not verified evidence.

Use the closed [logical catalogue](../contracts/DORA_ALPHA_CLOUD_IDENTITY_AUTH_OWNERSHIP_CONTRACT_V0_1.json)
and [normative semantics/plan](../stage1/DORA_ALPHA_CLOUD_IDENTITY_AUTH_OWNERSHIP_CONTRACT_V0_1.md).
Separate immutable/versioned ownership, job, grant and recording authorization
records from global preferences. Current revisions fence stale decisions before
every bounded upload part, dispatch and retry. Temporary upload authority is an
exact-source/time/operation/byte-scoped descriptor, not a selected wire format.
Protective cancellation requires authenticated ownership, independently of ASR
consent. Reads still require applicable grants but not live original audio.

## Alternatives and consequences

Credential-as-consent or client-owned resource assertions permit cross-owner and
unauthorized upload and are rejected. A new backend/auth module or vendor selection
would exceed admission and is rejected. Only offline fixture validators execute.
Future server implementation must prove authoritative resolution, atomic revocation
fencing, request proof/replay protection, quotas and truthful cancellation/deletion.
Logical test PASS does not close BE-AUTH-001 runtime or first-real-audio gates.

The 7.2C opaque authorization/scope/ownership references gain normative resolution
semantics without changing that contract. 7.2D versions/edits remain untouched.
Local capture/storage/admitted processing remain account/network/GMS independent.
Reinstall/recovery mechanisms remain unselected and cannot silently inherit rights.
7.3/7.3C/Stage 8 NOT_STARTED; Cloud auth/ownership runtime NOT_IMPLEMENTED.
