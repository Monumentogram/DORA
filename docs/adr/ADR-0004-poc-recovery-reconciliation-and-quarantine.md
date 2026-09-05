# ADR-0004: PoC Recovery reconciliation proof and durable quarantine

Status: Selected for bounded REC-I3 implementation; independent/accountable review pending.

## Context

REC-I3 now has reviewed bootstrap and sequential microfile publication, but restart handling still lacks an implementation that authenticates the journal/manifest/unit intersection and durably moves uncommitted safe artifacts to quarantine. The active protocol already fixes the key taxonomy, temp/final states, quarantine order, typed crypto, and PoC-only journal. This ADR selects the minimal implementation boundaries needed to make those requirements testable without admitting a production subsystem.

## Decision

Use one reconciliation controller that owns journal, filesystem, and crypto observations and shares `ProcessRecoveryRunSingleWriterGuard` with bootstrap/publication. It evaluates confirmation priority first, then validates the maximal contiguous journal prefix and authenticates the newest usable manifest, falling back to an older valid generation. It authenticates units sequentially and returns a private-proof capability bound to the actual manifest generation, authenticated count/end and exact digests. The capability authorizes only consumption of that prefix and is not a publication capability.

Keep the existing database path and migrate the PoC journal to schema version 3. Preserve v1/v2 tables and add one quarantine-intent table. A nullable composite bootstrap binding distinguishes honest no-row orphans from row-backed artifacts without fabricating a bootstrap commit. Version 1 upgrades transactionally through exact version 2 before version 3; version 2 upgrades directly after exact-schema validation. Unsupported or malformed states fail closed; no destructive migration is allowed.

Derive quarantine intent identity from protocol, candidate, run, source relative name, artifact role, byte count and SHA-256. Do not include observed state, so the same immutable source keeps one identity as filesystem state changes during replay. Preserve the original observed state in the immutable row and record later observations separately. Derive the destination solely from the intent digest beneath a prepared `quarantine/<runId>/objects` directory.

Prepare and durably fsync the safe quarantine directory chain after confirmation priority is known and before the first intent. Then execute intent commit, immediate no-overwrite check plus rename, source directory fsync, destination directory fsync and completion commit. Resolve transaction/rename exceptions by exact readback/inspection. A completed row is a no-op only when the source is absent and exact destination exists. Never overwrite, recreate, delete, reverse, promote a temp, replace/delete an alias, or recursively quarantine destination objects.

A no-row confirmation final requires real existing-alias Tink authentication with exact AAD and exact plaintext identity before quarantine. A temp does not inherit that proof requirement. Unverifiable finals remain retained.

Evidence delivery after completion is at least once and keyed by stable intent ID. Sink failure cannot alter the durable outcome. Returned results and remainders are deep immutable and distinguish confirmed from unknown side effects.

## Consequences

The slice can prove host ordering, crypto routing, migration and replay behavior, and can compile minimal Android adapters. Immediate `lstat` plus `Os.rename` is not a kernel atomic-no-replace guarantee. Host SQLite/fakes do not prove Android durability. Streaming/checkpoint recovery, external orchestration, process-death campaign, device evidence, production schema/admission, full REC-I3 completion and Recovery preflight remain outside this decision.
