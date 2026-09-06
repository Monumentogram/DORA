# DORA MVP 1 — PoC Recovery Gate Set Stage 0 v0.8

Status: **Owner-confirmed prospective result-boundary governance; v0.8 controller implementation blocked pending independent CLEAN review**  
Decision/ADR: 'DEC-047' / 'docs/adr/ADR-0006-rec-i3-streaming-result-boundary-and-evidence-delivery.md'  
Machine pair: 'poc-recovery-gate-set-stage0-v0.8.json' / 'poc-recovery-protocol-stage0-v0.8.json'

v0.8 inherits every unchanged v0.7 semantic through exact SHA-256 pins and binds Task 5 base 'e61d9b043fe83aebb674a126ea6aebce72be085b' / tree '85db58154681b17fe5e5101629bded91d48ca59a'. All v0.1–v0.7 artifacts are immutable. Only the controller result vocabulary/mapping, strict references, receipt/evidence lifecycle, and legacy ambiguous-commit spelling are overridden.

The controller boundary has exactly four result variants, six stages, twenty classifications, twenty total mappings, and four retry-only safe exception values. 'JOURNAL_OPERATIONAL' covers known non-ambiguous SQLite failures; 'JOURNAL_COMMIT_STATE_UNRESOLVED' is reserved for ambiguous commit/readback; only the long legacy ambiguous spelling is rejected. The receipt core is fixed at exact readback and the immutable final receipt is constructed after both closes and one bounded sink attempt. PENDING is caller-retryable through exact replay; no autonomous delivery is promised.

The journal stays schema v4 at 'poc-recovery/v1/recovery-journal-v1.db' on combined baseline '3c63ab09874f4d089e4363985aa8b5c99900c122' / tree '718eae8d8d619d17c25ac9d025e0e24db3d52f9e'. Durable rows/enums, DDL/migrations, identity preimages, replay hash, source immutability, K12 and every other v0.7 semantic remain unchanged. No table/provider/dependency/outbox/scheduler/background mechanism or local-mode regression is allowed.

Campaign counts remain 46/184/138/120. 'fullRecI3Completed=false', 'campaignReady=false', 'preflightEligible=false', and 'k12ConsumerDeferred=true'. Ten blockers remain open and REC-RDY-02 remains historically closed. OD-15 remains the only implementation overlay. POC-RECOVERY-001 remains BLOCKED / NOT_READY. This gate grants no v0.8 controller implementation/evidence, execution, campaign, preflight, production, consumer, cross-process, retirement, PR, or merge authority.
