# Stage 9.1 generic CI follow-up

Date: 2026-10-29. Branch: `stage/9.1-unified-owner-alpha`.
Diagnosed baseline: `e967fa4d10f66a318b066a3fe185a1c50f879708`.
This record separates historical evidence from pending Stage 9.1 verification.

## Status and evidence boundary

| Evidence | Result | Scope |
|---|---|---|
| Generic Android CI [40752916145](https://github.com/Monumentogram/DORA/actions/runs/40752916145) | **FAIL** | Exact baseline above; release validator and persistence inventory failures |
| Dedicated PR Android CI [40752916044](https://github.com/Monumentogram/DORA/actions/runs/40752916044) | **PASS — historical baseline** | Same baseline; existing recorded evidence, not newly verified here |
| Software/backend/API28/API36 CI [40752924708](https://github.com/Monumentogram/DORA/actions/runs/40752924708) | **PASS — historical baseline** | Same baseline; existing recorded evidence, not newly verified here |
| New Stage 9.1 branch/head checks | **PENDING** | No new-run success is asserted |

The historical PASS entries are attributed to the prior session's device-branch
`docs/PERMANENT_WHISPER_DEVICE_RESULT_2026-10-29.md` and the
[PR119 description](https://github.com/Monumentogram/DORA/pull/119).
They do not supersede the generic FAIL or qualify a later APK.

## Historical release identity and SBOM

`tools/validate_alpha_release.py:55` compares the current product identity with
the frozen Stage 7.3 identity `(4, 0.1.0-alpha.2)`. Current
`android/alpha-release.properties` contains `(5, 0.1.0-alpha.3)`.
Reading that file from the release contract's pinned `source_sha` confirms the
historical `(4, 0.1.0-alpha.2)` value. The failure therefore mixes two release
contexts; it is not evidence that the historical release record changed.

Read-only reproduction from repository root, using an available Python runtime:

```text
python -B -m unittest discover -s tools -p test_validate_alpha_release.py
```

Observed: 23 tests, one error in
`test_complete_consistent_release_evidence_accepted`, with
`Product identity differs from 7.3`. In-memory substitution of only the validator's
identity reader with the historical tuple made all 23 tests pass. No source was
changed; these substitutions are diagnostic probes, not acceptance evidence.

Running `validate_alpha_release.main()` with `--require-closed` under that same
in-memory substitution exposed a second failure:
`SBOM source/version/components/relationships mismatch`.
`tools/alpha_release_sbom.py:108` independently reads current identity while
regenerating the historical SBOM. The historical dependency lock is already read
from the pinned source by `tools/validate_alpha_release.py:254–258`, but the product
version is not.

Scoped repair proposal: validate historical identity against the pinned historical
source and supply its explicitly verified version to historical SBOM validation.
Keep current identity as the default for current artifact generation. Preserve
application ID, signer, exact source, graph, dependency, ancestry and historical
evidence checks; do not rewrite release records or reset current version to 4.
Add positive historical/current separation tests and negative controls for wrong
historical version, source, signer and SBOM components/relationships.

After isolating both identity readers in memory, the next gate reported
`Recovery clean integrated identity not admitted`. Its admission cause was not
diagnosed in this bounded investigation. The identity repairs alone therefore
cannot establish generic CI success. Any later repair must retain the existing
Recovery provenance/admission checks; no benchmark, history rewrite or broad
allowlist bypass is proposed.

OwnerPreview identity is separate: the planned `5 / 0.1.0-owner-preview.5` override
requires matching artifact-validator and test expectations in
`tools/owner_preview_artifact.py` and `tools/test_owner_preview_artifact.py`.
The old test's rejection of versionCode 5 must become rejection of obsolete
versionCode 4. Shared Alpha properties and OwnerPilot identity are not changed by
that override.

## Protected vault schema incompatibility

Read-only logs from baseline jobs
[123044070848](https://github.com/Monumentogram/DORA/actions/runs/40752916145/job/123044070848)
(API28) and
[123044070591](https://github.com/Monumentogram/DORA/actions/runs/40752916145/job/123044070591)
(API36) identify
`ProtectedReadOnlyVaultTest#fullSuccessor394UsesPinnedEntryAndRejectsCatalogOwnershipDrift`.
Both logs show that test completing and later tests continuing. They do not
establish a hang or timeout. The diagnostic logger suppresses exception text and
does not persist completion status codes, so the observed generic failure message
alone cannot distinguish failure from an unexpected skip or identify its exact
assertion.

The source incompatibility is independently established:

- `ProtectedReadOnlyVaultTest.kt:38` creates a fresh current fixture, then lines
  105–106 require pinned reopen to succeed.
- `AudioJournalSchema.kt:580` defines schema 4; its exported Room identity is
  `3af41c4ca80a221a72c975d655bce119`.
- `DiagnosticJournalFactory.kt:85–93` requires schema 3 and Room identity
  `14326b90c3941099abb8dc04701d9d5c` before Room callbacks.
- `ProtectedReadOnlyVault.kt:166–177` independently requires the same schema-3
  version and identity for read-only verification.

All Kotlin paths above are under
`android/core/audio/src/`, with the test in `androidTest/kotlin/com/monumentogram/dora/audio/persistence/`
and production classes in `main/kotlin/com/monumentogram/dora/audio/persistence/`
(`AudioJournalSchema.kt` is in its `journal/` subdirectory).
A current schema-4 fixture cannot satisfy these positive schema-3 admission paths.
This is source proof of incompatibility, not a newly reproduced Android stack trace.

[ADR-0022](adr/ADR-0022-stage9-bounded-local-transcript.md) explicitly leaves
historical diagnostic-v3 gates unmigrated and requires separate qualification for
successor positive read paths. Replacing 3 with 4 or loosening a hash comparison
would not be a generic CI repair.

Safe future scope: preserve the exact historical v3 read-only profile and qualify
an explicit current-v4 profile with its exact version, Room hash and complete DDL
validation. Keep create, migration, downgrade and repair callbacks denied, and
retain source inventory, ownership, key and ciphertext verification. Record the
successor decision without relabelling historical evidence. Test unchanged v3,
exact v4, wrong version/hash, DDL drift, ownership drift and denied migration.

After that scoped repair, use the existing persistence runner's
`--diagnostic-class com.monumentogram.dora.audio.persistence.ProtectedReadOnlyVaultTest`
and `--diagnostic-method fullSuccessor394UsesPinnedEntryAndRejectsCatalogOwnershipDrift`
on disposable API28/API36 emulators, followed by the relevant protected-read-only
class and C3 historical controls. No such Android execution was performed during
this diagnosis, and no full Recovery benchmark is needed for this proposal.

## Missing C3 receipt

The persistence failure prevents the later C3 verification step from running in
`.github/workflows/android-ci.yml:465–472`. Its unconditional artifact upload at
lines 475–480 then cannot find `protected-diagnostic-c3-receipt.json`.
This is a downstream missing artifact, not an independently demonstrated C3
runtime failure. Do not fabricate a receipt or treat an upload change as runtime
acceptance.

The diagnosis used bounded CI reads and read-only Python/source checks. No test
was disabled, no security guard weakened, and no source, device, AWS resource,
Git history or CI run was mutated. Generic CI remains **FAIL** until new exact-head
evidence establishes otherwise; historical targeted PASS remains separately scoped.
