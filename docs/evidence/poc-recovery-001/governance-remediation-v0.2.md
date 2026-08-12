# POC-RECOVERY-001 governance remediation v0.2

Status: **OWNER-APPROVED PROTOCOL REMEDIATION RECORDED — RE-REVIEW REQUIRED**\
Date: 12 August 2026\
Reviewed input commit: `87f8c00c6afce0f658678a7a09b1a394b89a2454`\
Base commit: `849d9d0406a619b334c9b707a4b6b42b34885b4b`\
Owner-supplied review disposition: `CHANGES_REQUIRED`\
Execution allowed: **no**

## Evidence boundary

This record traces the Project-owner remediation instructions to prospective governance protocol
v0.2. The underlying independent review record and reviewer identity were not supplied as a
repository artifact, so this file does not invent them or claim that Codex is a formally
independent reviewer. Before execution, a distinct accountable Engineering/Security reviewer must
review the exact remediation commit and record their identity, role, date and disposition.

No Tink or other dependency was added to Gradle. No `:poc:recovery`, harness, production `:app`
change, runtime SQLite result, device test, kill campaign, benchmark or recovery measurement was
created or run by this remediation.

## Remediated P0/P1 design gaps

| Area | v0.2 disposition |
|---|---|
| Streaming salvage | Selected `DURABLE_ONE_SEGMENT_LOOKAHEAD`, exact `q`/`R` math, read/EOF/exception accounting, 8160-byte bound and run/segment caps |
| Streaming key model | One fresh input key and one HKDF-derived AES-GCM key per ciphertext stream; nonce prefix/index/last flag provide segment uniqueness |
| Public construction | Non-deprecated `AesGcmHkdfStreamingParameters` and `RegistryConfiguration.get()`; deprecated template helper forbidden |
| Microfile construction | `AES256_GCM_TINK_IV12_TAG16`, fresh keyset per microfile, five-second 160000/160033-byte unit |
| Manifest | Exact `DORA_RECOVERY_MANIFEST_V1_BINARY_BE`, strict generation/entry/range/digest/name rules, 721-entry and 512-KiB caps |
| AAD | Four exact deterministic big-endian/LP16 schemas for streaming, microfile, publication and key envelope |
| Android Keystore | Exact alias and create/recover APIs, encrypted TinkProto keysets, no replacement, five exact classifications |
| Commit/oracle | Successful SQLite `endTransaction()` return is semantic commit; controller event follows as evidence; `0 <= C <= R <= A` and oracle equality fixed |
| Kill strata | Candidate-specific public K01–K12 barriers, exact microfile/streaming K02, publication K05–K08 and immutable/canonical K12 seeds |
| Durability | Exact key/ciphertext/publication `fsync`/rename/parent ordering; immutable final paths; public `android.system.Os` only |
| SQLite | WAL/FULL, `wal_autocheckpoint=0`, foreign keys, single writer, non-exclusive transaction, exact row identities and fresh runtime preflight |
| Faults | Added COR-04..06, KEY-04..05, RBK-01..02, PAR-01, QUA-03 and EVT-01 with Phase A repetitions |
| Paths/quarantine | Exact no-backup roots, containment/lstat/regular-file/no-symlink rules and intent→rename→directory-fsync→completion protocol |
| Idempotency | Exact SHA-256 `processingIntentId` encoding and mandatory SQLite `UNIQUE` constraint |

These are design selections with status
`DESIGN_SELECTED_IMPLEMENTATION_VERIFICATION_REQUIRED`, not implementation evidence or security
approval.

## Remaining blockers

- `P0`: Project-owner Product/IP final approval record is absent; `approvedReviewer` and
  `approvedOn` remain null.
- `P0`: distinct accountable recovery Engineering/Security reviewer is unassigned; exact v0.2
  re-review and approval/revision are absent.
- `P0`: no isolated implementation or non-metric implementation-verification evidence exists.
- `P0`: no exact Gradle-resolved harness graph/delta review exists.
- `P0`: fresh emulator and D2 runtime preflight facts are null.
- `P0`: a later explicit Project-owner execution authorization is absent.
- `P1`: D1/D5 remain unavailable for a full physical verdict.
- `P1`: Production Legal and Production Security remain null and separate.

## Version disposition

- Normative prospective contract: Gate Set/protocol `stage0-v0.2`.
- Gate Set/protocol v0.1: retained unchanged as `SUPERSEDED_AUDIT_ARTIFACT_NON_EXECUTABLE`.
- Measured evidence: none.
- `executionAllowed=false` in every active readiness and protocol record.
