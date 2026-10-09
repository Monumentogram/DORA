# Catalog reservation reuse and order-validation proof

Status: implemented; final-source host checks and API28/API36 targeted verification confirmed. The complete AC paired performance gate is USEFUL on both APIs; full acceptance still requires exact-SHA CI.
Scope: Owner-authorized Stage 8.6C.3 synthetic optimization. This is not a LONG-02 root-cause claim or physical-device admission.

## Retained boundaries

The successful append retains four application durability phases: reservation, bootstrap, publication, and catalog commit. Reservation and catalog commit retain their existing full, exact, post-transaction catalog readback. Bootstrap/publication retain their exact readbacks. Recovery still authenticates the new run and compares its PCM with the submitted bytes before catalog commit. SQLCipher connection policy, file/directory synchronization, keys, tombstones, frame identities, and the 256,000-frame admission fence are unchanged.

Only the append reservation precondition may reuse the entry snapshot. The ordinary path therefore performs four full catalog loads: entry; reservation post-commit; catalog commit precondition; catalog commit post-commit. A direct reservation without an eligible observation still loads its precondition. Finalization reservation always reloads its precondition. `Catalog.load` never returns cached data. `compareAndSet` never consumes the reservation snapshot.

## Lifetime and ownership

One private nullable observation belongs to one `RoomAudioJournal` instance and one active catalog lease. Successful acquisition starts with no observation; releasing that lease clears it. The sole journal handle and its existing vault-wide, thread-owned lease exclude another admitted writer. The operation gate, exact identity/owner/vault checks, and uncertain-journal fence execute before reuse. A cached value is evidence of structural validation, never authorization.

Each full load verifies thread ownership and discards the preceding observation before the authorization/identity/database queries. A missing, tombstoned, malformed, failed, pending, or finalized load leaves no reusable observation. Only a successfully validated, unpending, unfinalized asset outside any database transaction and outside uncertain state can supply one. In particular, a read made inside a Recovery transaction cannot survive its later writes or rollback as reusable evidence. A reservation reaching its precondition consumes the observation once; mismatched caller state requires a new full load. A rejected intent and its fallback load leave no reusable evidence behind. Failures before the reservation precondition cannot authorize reuse or consume another thread's state.

The private observation owns a copied segments list. Its elements contain only immutable `val` fields: strings/value-class identities, numbers, and `Sha256Value`. The latter copies its input bytes and returns copies from `toByteArray`. Pending and finalization lists are excluded entirely because such assets are ineligible. No caller-owned nested mutable collection is retained. This is ephemeral metadata only: no PCM, key, path, new persisted state, schema, or recovery authority.

## Mutation invalidation inventory

Both existing transaction entry paths discard the observation before entering the transaction, including transactions which roll back or fail:

| Entry path | Covered mutations |
| --- | --- |
| `JournalCommitBoundary` begin | Asset/origin creation; metadata; original reference; reservation and finalization intent/source rows; append/finalization CAS; deletion tombstone and target creation; deletion target completion; final deletion and removal of intent/final sources/Recovery rows; initial vault binding |
| `WriteSession` init | Recovery bootstrap insertion; microfile/manifest publication; quarantine insertion/completion |

Closing the journal also discards the observation. Uncertain transaction completion remains fenced by `requireMutable`. Post-commit readback is always a real full load and failures propagate through the existing uncertain fence. The change does not cache publication validation across bootstrap/publication/Recovery, which is why the later CAS precondition remains a full load.

This proof relies on the admitted sole-handle, serialized-journal contract. Future mutation entry paths or asynchronous writers must participate in invalidation before their first write. A future widening of eligible states would require a new immutable-copy and invalidation proof.

## Linear order validation

The old validator rescans every previous prefix for the current physical ID. A local map records the first origin observed for each physical ID. After the unchanged canonical-ID and physical-origin-plus-offset checks, equality with that recorded origin is equivalent to equality with every preceding row of that ID: induction preserves a single equal origin for each accepted prefix. Different IDs do not interact; noncontiguous recurrence is still checked.

The outer loop and every identity, ordinal, frame-contiguity, duplicate-unit, physical-ID, nonnegative/overflow arithmetic, and frame-size check stay in their original order. The map is per invocation and cannot carry evidence between assets or calls. Original-list reads become linear in the number of segments, with O(number of distinct physical IDs) retained map entries. No latency or physical-device conclusion follows from the element-read count alone.

## Verification design

`CatalogOrderOptimizationTest` invokes the actual private production validator. Deterministic source-element counts at 10/100/400/1,000/2,000 units catch the old quadratic scan without timing noise. Literal fixtures cover distinct, shared, and interleaved physical IDs; empty and maximum units; wrong identity/order; frame gaps; duplicates; malformed physical IDs; inconsistent origins despite valid frame sums; negative and overflowing physical arithmetic; and invalid frame sizes. A separate 128-case seeded equivalence test compares results and exception classes with the frozen 9c6b7563 prefix implementation. The parent observed the expected linear-work assertion failure on the separately archived baseline; the other four host tests passed before implementation. Runtime GREEN remains a separate required observation.

`RoomCatalogReservationReuseTest` uses real Room and the admitted encrypted SQLCipher helper. Its observer forwards every operation and counts the full ordered-claims query. Entry-load plus reservation must require two full loads (baseline three). Direct reservation, released leases, an intervening rolled-back Recovery transaction, and a read inside a transaction retain fresh preconditions. Negative controls cover rejected-intent consumption, caller list mutation, tombstone precedence, revoked authorization, substituted identity, wrong thread, failure during a later full load, and injected post-commit readback failure followed by the uncertain-journal fence.

The coordinator observed the frozen baseline's API36 RED: **11 tests, one expected failure**, `validatedEntrySnapshotAvoidsOneLoadButReservationStillReadsBackAfterCommit`, because two full queries were expected and three occurred. All ten baseline negative/control cases passed. The successor adds the twelfth rejected-intent-consumption case. This baseline execution used the independently archived source and is distinct from successor GREEN verification.

Existing bridge, journal, encrypted-vault, transaction/crash, Recovery, and capture-backpressure tests remain required. Synthetic benchmark and API28/API36 results must be reported separately from this source proof; neither test preparation nor this document establishes a pass.

## Observed final-source verification

Build10 completed successfully, including the final eligibility condition order and its complexity refactor. The current XML reports show core-audio debug **254 tests, 253 passed, zero failures/errors, one inherited Windows skip** (`DevelopmentDeviceSecurityTest.symbolicMarkerCannotGrantTheException`); core-audio release **246/246 passed**; and app debug **76/76 passed**. Core-audio and app debug lint reports contain zero errors. The coordinator's exact Detekt10 invocation passed; its diagnostic log is empty.

Both order-validation classes passed all five tests each. The real validator again reported exactly **10 / 100 / 400 / 1,000 / 2,000 source-element reads**, respectively, at the five requested sizes. These are operation counts, not timing results.

The final-source API36 targeted receipt `api36-final-targeted-02` reports **44/44 passed**, including **all 12 Room reservation tests**, with the full instrumentation log ending `OK (44 tests)`. The tested APK SHA-256 is `98ec141f83d265b19c2aeb89dd256edb14c0a192fdf0986a6600723f62db4081`; the raw-log SHA-256 is `81f90c06bd519cf595e81fb9c75498f00e337890d6a3f2fb789d403468c4f5f0`. This was an emulator execution, not a physical-device result. The two-load entry-plus-reservation assertion and every reservation invalidation, ownership, authorization, mutation, tombstone, and readback control passed on this artifact.

The final-source API28 targeted receipt `api28-final-targeted-03` reports **44/44 passed** on the same APK. The raw instrumentation log confirms **12 successful Room reservation test completions**, ends `OK (44 tests)`, and reports 393.034 seconds for the full targeted suite. Its SHA-256 is `7a08dce7d6a0fe2116e2e50729a4abd3d555136f19d67546b7d9325aeceb16aa`. This completes the targeted emulator check on both APIs; the suite duration is not an append-latency benchmark.

Earlier candidate01, candidate02, and subsequent failed attempts remain part of the [result record](RESULT.md). In particular, candidate01's reservation class passed 12/12 while its combined run failed seven separate protection cases; later protection failures and descriptor-leak regressions are retained rather than relabeled as passes. The final 44-test receipt establishes the later targeted result only.

The complete AC repeated campaign is **USEFUL** on both APIs: append CPU reductions
at1000/2000 are16.16%/20.51% on API28 and21.86%/24.42% on API36. All fixed wall and
sync gates pass. Four complete runs authenticate all2007 blocks before close and after
reopen. The [result record](RESULT.md) retains the prior failed optimized API28 attempt,
all adverse measurements and limitations. This performance result is separate from
full CI admission. LONG02 root cause remains **NOT_PROVEN**, physical protection
remains **NOT_INSTALLED**, and Stage8.6 remains **NOT_READY**.
