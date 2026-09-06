# ADR-0007: REC-I3 proven rollback of semantic VALID

- Status: Accepted as a prospective bounded Stage 0 correction; implementation activation requires independent CLEAN review
- Date: 2026-09-06
- Decision authority: Development Governor under the owner's recorded delegation
- Applies to: the outward controller result after a proven framework rollback of a semantic VALID persistence attempt
- Decision source SHA-256: `5a1a5927cbf649ba444b042fafc1b69aa0c34d7f1804d81bce4eefec2e4e6577`

## Context

ADR-0005 transaction rule 7 returns the original non-persistable semantic outcome when the framework proves rollback and reconciliation finds no intended outcome or range. Its journal contract can therefore return `Original(PERSISTED_VALID)`.

ADR-0006 preserves exactly four outward variants and requires an exact-readback persistence receipt for `PersistedValid`. A rolled-back VALID attempt has no truthful `PersistedValid` representation: it has no exact committed readback, admitted recovery endpoint, successful persistence claim, or newly ACTIVE range. Adding a fifth variant or making that receipt optional would violate the accepted v0.8 boundary.

## Decision

After a semantic VALID persistence attempt, when and only when the framework proves rollback and required reconciliation establishes absence of the intended outcome and range, the controller returns:

`Retry(JOURNAL, JOURNAL_OPERATIONAL, SQLITE)`

This result carries no persistence receipt, attempted IDs, existing-record references, admitted recovery endpoint, success claim, or newly ACTIVE-range claim.

Row absence without proven rollback remains `Retry(JOURNAL, JOURNAL_COMMIT_STATE_UNRESOLVED, SQLITE)`. Exact complete committed readback and exact replay retain their readback-derived receipt. Collision and structural precedence remain unchanged. Proven rollback of semantic REJECTED or FATAL retains ADR-0005's original non-persistable semantic outcome.

The correction adds no variant, stage, classification, safe exception type, stored value, identity field, table, provider, scheduler, or background retry. It preserves the exact 4/6/20/4 v0.8 vocabulary and every receipt cleanup and bounded sanitized-evidence rule.

## Compatibility and artifacts

This ADR narrowly supersedes ADR-0005 transaction rule 7 for semantic VALID and clarifies how ADR-0006's existing `JOURNAL_OPERATIONAL` mapping applies. The accepted v0.7 and v0.8 Gate Set/protocol artifacts remain byte-identical historical predecessors; no pinned digest is rewritten.

The implementation mapping remains disabled until an independent focused review returns CLEAN for the immutable governance amendment. No device, emulator, preflight, campaign, production admission, consumer intent, range retirement, push, PR, or merge authority follows.
