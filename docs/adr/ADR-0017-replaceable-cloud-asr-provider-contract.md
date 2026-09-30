# ADR-0017: Replaceable server-side Cloud ASR provider contract

Status: ACCEPTED FOR 7.2C CONTRACT ONLY
Date: 30 September 2026

Preserve ADR-0009's DORA-controlled control-plane/worker boundary and the frozen
ASR identities, original-audio input, immutable results and user authority.
Represent CloudAsrProvider as a versioned JSON type/semantics contract, a separate
evidence-bound provider profile and deterministic offline validators. The existing
app RecognitionPort is a different abstraction and is unchanged.

No backend runtime tree exists. Creating an unused server module or treating the
Stage 0 evaluation harness as product infrastructure would overstate readiness;
embedding provider semantics into Android would violate the selected boundary.
The documentation-and-validator approach is the smallest executable check of the
contract itself. No new dependency or runtime adapter is introduced.

Separate provider observations, validated immutable output and cancellation facts.
Provider IDs are provenance, not DORA identities. Possible acceptance requires
reconciliation before retry; the historical evaluation harness's boolean retry
flag is not adopted as a product retry policy. Unknown metadata and unproven
provider mappings are explicit. Storage/auth/persistence implementations remain
outside this decision.

The final 6.2D owner-only closure supplies admission authority. Its retained
technical evidence/configuration supplies bounded mappings; earlier raw failures
are not rewritten. No second-speaker, external/public or runtime admission follows.

See [specification and plan](../stage1/DORA_ALPHA_CLOUD_ASR_PROVIDER_CONTRACT_V0_1.md),
[generic contract](../contracts/DORA_ALPHA_CLOUD_ASR_PROVIDER_CONTRACT_V0_1.json) and
[adapter profile](../contracts/DORA_ALPHA_AWS_TRANSCRIBE_ADAPTER_PROFILE_V0_1.json).
7.2D/E, 7.3/7.3C and Stage 8 remain NOT_STARTED. AWS NOT_CALLED.
