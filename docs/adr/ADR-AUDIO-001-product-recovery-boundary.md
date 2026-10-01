# ADR-AUDIO-001: Product audio format and Recovery integration boundary

Status: DECIDED for the Stage 8.1 contract; final publication gates pending.
Date: 2026-10-01.
Baseline: f3e58b12d0e9353cdb17e986336f23f48f33511c.

## Authority and scope

No existing final ADR-AUDIO-001 was found in the baseline. References to that ID
were backlog requirements, not an accepted ADR. This decision selects the
post-evidence product route anticipated by Technical Plan section 14.3. Accepted
Recovery contracts, source bytes and historical evidence remain authoritative.
This is an integration boundary decision, not encrypted persistence runtime
acceptance. Stage 8.2 and all later stages remain NOT_STARTED.

## Audio and time

The adapter accepts the existing RecoveryPcmContract only: signed 16-bit PCM,
little-endian, 16000 frames per second, one channel, two bytes per frame. There
is no WAV header inside the encrypted payload. RIFF/WAVE remains the separate
explicit export/provider-dispatch boundary in ADR-0014. Empty, odd-length,
unsupported-rate/channel/encoding blocks are rejected before publication.

Technical Plan section 14.1 permits native-rate acquisition at 44.1/48 kHz.
Such capture must declare its actual rate and use a separately verified
deterministic normalization boundary before this adapter; it must never relabel
native bytes as 16 kHz. No resampler, microphone or native-rate support is
implemented by this decision. Native capture provenance belongs in encrypted
product metadata, with explicit source/derived identity where normalization
creates a different immutable source version.

Logical time is a nonnegative frame index. Frame n denotes the half-open
interval [n/16000,(n+1)/16000). Duration is frameCount/16000 seconds; integer
microseconds are floor(frameCount*1000000/16000), calculated without intermediate
overflow. ASR millisecond projections derive from the same origin and cannot
claim better precision than their provider supplies. Wall-clock time is display
metadata and never duration authority.

Recovery storage units form a contiguous ordered partition of logical frames.
They are distinct from capture/ASR physical segments. Technical Plan section
14.2 retains its 1.5–2 second in-memory physical-segment overlap: a unit records
the physical segment ID, its logical origin and source-frame offset, such that
physicalFirstFrame + sourceFrameOffset = firstFrame. Overlap frames already
present in the asset must be omitted before append, never duplicated in the
logical stream. Reusing a physical ID with another origin is rejected. Physical
rotation and overlap construction remain future capture/chunking work.
Pauses contribute zero
frames; any elapsed pause/wall-clock interval is separate encrypted metadata.
Silence captured as PCM contributes frames normally. No VAD, pause UX, automatic
rotation cadence or 90-second segmentation policy is introduced.

## Identities and source state

RecordingId identifies the logical recording. AudioAssetId identifies one
immutable source version for that recording. RecordingSessionId identifies the
capture session; it is a canonical opaque UUID. Capture physical segment IDs
are distinct from unique Recovery storage-unit IDs. Each storage unit occupies
one fresh Recovery run namespace with one generation-1 microfile, 2–160000 bytes.
This bounded adapter call is not a five-second physical recording rotation policy.
Mappings must be reserved uniquely and authenticated in the encrypted product
journal. A run is never reused for another asset, session or segment. Preserve
the immutable PoC AAD byte-for-byte; bind product owner/vault/recording/asset and
segment mapping in authenticated outer metadata as required by frozen 7.4.

Recovery manifest generations and ordered byte extents remain unchanged.
Product frame counts are exact byte extents divided by two. An authenticated
available prefix does not prove explicit finalization. Only an authenticated
finalization record bound to the exact ordered source, generations, manifest
digests and frame count can produce FINALIZED. Missing, stale, truncated or
recovered-prefix state remains PARTIAL_RECOVERED. Corrupt/authentication-rejected
sources are rejected; key absence/unavailability has its own outcome and never
creates replacement keys. Uncertain writes/finalization fence further writes
until reconciliation. Before publication the encrypted catalog durably stores a
typed intent with the exact unit mapping and frame count; after interruption,
reconcile authenticates the existing run and completes that mapping idempotently.
Before finalization it stores an intent bound to the exact ordered source list.
Only clean, complete verification may commit that intent; no partial result can
authorize finalization. Missing/uncommitted pending data remains fenced for later
reconciliation, without reusing its namespace or recreating its key.
Existing finalized generations cannot be overwritten.

Extraction stops at the first unavailable unit; it never skips a hole. If earlier
units authenticate and the tail is missing or its key is unavailable, return that
exact contiguous logical prefix as PARTIAL_RECOVERED, its actual frame-derived
duration and an explicit tailFailure. With no authenticated frames, return the
failure. Corrupt/authentication-invalid data or inconsistent mappings reject the
overall extraction; they are never silently relabeled as ordinary missing data.
Structural truncation follows the accepted corruption classification and cannot
be finalized. An explicit finalization record does not override missing bytes.

## Integration route

Select the accepted sealed microfile publication and reconciliation controllers.
Compile the existing Recovery sources in place behind a narrow product library;
do not duplicate or edit their engine. The writer requires the accepted
bootstrap capability and publication transaction proof. The extractor requires
the accepted authenticated-prefix capability and checks its exact byte/row
binding before exposing PCM. Input length is checked before copying. The callback
borrows a unit buffer only until return, when the adapter zeroes its copy. A late
failure invalidates the overall extraction even if earlier authenticated units
were delivered. Consumers must discard that attempt's output and may not create
plaintext files. Accepted Recovery retains its own bounded transient buffers;
this ADR does not claim zeroization of every library/VM copy. The future source
adapter must enforce the accepted descriptor and artifact-size limits before
allocation. Production code receives only product
ports, identities and normalized outcomes, not paths, keys, journal mutation
primitives or Recovery capabilities.

The historical streaming campaign writer is excluded from Alpha and deliberately
abandons the stream. The streaming reconciliation controller additionally
requires a synthetic plaintext oracle. Neither is the selected product route.
No plaintext oracle cache or weakened streaming reader is introduced.

## Encryption and API28–32 compatibility boundary

The public production composition remains unavailable until Stage 8.2 admits
the real encrypted key/storage/journal composition. There is no plaintext,
static-key or in-memory test implementation fallback in production.

The existing AndroidRecoveryJournalDatabase is excluded from product composition:
its accepted API33 guard and plaintext PoC schema are preserved. API28–32
applicability must be demonstrated for the compiled portable product bridge with
test-only collaborators, not inferred from the API34 physical device.

API28–32 = APPLICABLE to this portable integration boundary, with the required
encrypted persistence adapter below explicitly deferred and unaccepted. The
library compiles with minSdk 28, passes Android lint, and runs its controllers on
the JVM without invoking Android SQLite/Keystore. That proves the portable
composition seam, not device durability or the future database implementation.

| Platform | Boundary applicability | Required persistence composition |
| --- | --- | --- |
| API28 | Applicable; no direct platform calls in product bridge | Encrypted journal with verified per-connection properties; no PoC helper |
| API29 | Applicable; same compiled bridge | Same; cannot use API30 execPerConnectionSQL |
| API30 | Applicable; same compiled bridge | Compatible encrypted journal; API availability alone is not admission |
| API31 | Applicable; same compiled bridge | Same durability/schema/lease contract |
| API32 | Applicable; same compiled bridge | Same durability/schema/lease contract |
| API33/34+ | Applicable; same compiled bridge | PoC API33 guard does not admit its plaintext schema into product |

Platform-dependent accepted collaborators remain behind their existing ports:
AndroidKeystore/Tink key access, Android OS file/descriptor/fsync operations and
the journal. The 8.1 tests inject only volatile encrypted-artifact collaborators;
they do not claim these device operations executed. Product source requires
pending-quarantine inventory and retained-artifact access explicitly, so the
PoC's optional empty defaults cannot be inherited accidentally. Android lint and
minSdk compilation complement the preserved Recovery API/source tests. Source
token and coordinate checks are guardrails, not full call-graph or per-runtime
configuration certification.

The required 8.2 encrypted-journal adapter must provide one unified journal,
atomic unit/manifest insertion, restrictive foreign keys, WAL and FULL
synchronization on every connection, wal_autocheckpoint=0 on current and future
connections, successful-endTransaction commit semantics, exact readback/schema,
writer serialization and no-overwrite reservation. It must encrypt product
relationships, ranges, content hashes and finalization metadata, including
WAL/rollback/temp artifacts. It may not merely wrap the plaintext PoC database.

Platform OpenParams synchronization configuration exists at API28;
execPerConnectionSQL exists at API30. API28/29 need an admitted mechanism that
guarantees the same connection properties. The PoC deliberately admits only
API33+, even where individual APIs exist earlier. Removing its guard is not a
compatibility adapter. The future journal must fail closed when its connection
properties cannot be established. This document does not claim its runtime
implementation, physical-device verification or SQLCipher dependency admission.
Platform references: [OpenParams.Builder](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase.OpenParams.Builder#setSynchronousMode(java.lang.String))
and [execPerConnectionSQL](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase#execPerConnectionSQL(java.lang.String,%20java.lang.Object[])).

## K12 disposition

NOT_APPLICABLE to the selected product microfile consumer route: streaming K12
persists a sealed outcome and ACTIVE quarantine range but does not authorize
ordinary extraction from it. Its accepted source, regression tests and evidence
are retained, not retired. The microfile route still executes accepted
reconciliation/quarantine checks and cannot turn pending or invalid state into
complete audio. No K12 consumer/retirement equivalence claim is made.
The shared library includes the accepted Recovery source tree in place, including
dormant streaming types. The product bridge imports/invokes only bootstrap,
microfile publication/reconciliation and quarantine controllers; no streaming
reader, K12 consumption or synthetic plaintext oracle is reachable through its
public ports. The app release graph does not depend on this library yet.

## Acceptance

Stage 8.1 requires deterministic composition tests, negative/mutation controls,
API and source-provenance checks, full existing regressions, fresh checkout,
independent review with no unresolved P0/P1 and exact-remote-SHA CI. Until those
pass, the boundary is not READY. The final external publication receipt records
the exact remote SHA, CI and Sheet results; this source commit does not certify
its own later CI run. Encrypted product persistence runtime remains NOT_ACCEPTED.
