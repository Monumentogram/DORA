# POC-SEARCH-001 Stage 0 artifact evaluation review

Status: **EVALUATION_APPROVED — STAGE 0 ONLY**\
Date opened: 11 August 2026\
Decision date: 11 August 2026\
Scope: Stage 0 evaluation only\
Role/provenance decision: `OD-11`\
Artifact approval decision: `OD-13`

This record is the public-safe review surface for the exact dependency and Android platform
inventory. `OD-13` completes the decision block for internal synthetic Stage 0 evaluation only.
It does not authorize a measured benchmark, production use, redistribution, or dependency/schema
admission.

## Assigned roles

| Review role | Assigned reviewer | Scope | Production boundary |
|---|---|---|---|
| Product reviewer | Project owner | Stage 0 evaluation | no product/dependency admission |
| IP policy reviewer | Project owner | Stage 0 evaluation policy | production Legal remains unassigned and blocked |
| Engineering/Security reviewer | Project owner | Stage 0 provenance, digest, dependency and supply-chain evidence | does not replace independent production Security review |
| Production Legal | unassigned | none | required before production admission |
| Production Security | independent reviewer not yet assigned | none | required before production admission |

## Exact review object

- Dependency inventory: `docs/evidence/poc-search-001/dependency-inventory.json`.
- Inventory digest: `sha256:63a2a3dadfbfe072770d914a74cbd40d6adbd517548bda4ba0331dd314ca6a98`.
- Locked components: 66.
- Binary AAR/JAR artifacts with exact digests: 52.
- Maven POM records with exact digests and canonical source URLs: 66.
- License/NOTICE inventory:
  `docs/evidence/poc-search-001/license-notice-inventory.json`.
- License/NOTICE inventory digest:
  `sha256:8b80fa573a2674cb32fe08446683f5b3d05ce4721b6bcb018edec51cf9fbeb50`.
- Lock: `android/poc/search/gradle.lockfile`, exact configurations
  `_agp_internal_benchmark_kspClasspath`, `_agp_internal_debug_kspClasspath`,
  `benchmarkAndroidTestRuntimeClasspath`, `benchmarkRuntimeClasspath`,
  `debugAndroidTestRuntimeClasspath`, and `debugRuntimeClasspath`.
- Platform image: `system-images;android-36;google_apis;x86_64`, revision 7,
  `sha256:b1bb0769d0bed7698e61f203d7dc9bf6e7c37cd01a39d0d8788a11186bc78160`.

## Stage 0 SQLite provenance decision

`OD-11` approves the provenance method, not artifact rights: embedded platform SQLite is covered
by the containing system-image digest when the record also contains the exact package/image ID,
revision, runtime fingerprint, API, ABI and `sqlite_version()`. The digest is explicitly scoped to
the containing image; it is not claimed to be the digest of an extracted SQLite binary.

Recorded runtime identity:

- fingerprint:
  `google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys`;
- API: 36;
- ABI: `x86_64`;
- `sqlite_version()`: `3.44.3`;
- separate SQLite library downloaded or redistributed: no;
- separate SQLite binary digest required for this Stage 0 evaluation: no;
- mandatory reconsideration before production admission: yes.

## Exact license/NOTICE findings

The generated review inventory joins each of the 66 locked coordinates to the exact cached POM
and AAR/JAR digests already present in `dependency-inventory.json`. Effective license evidence is
now present for all 66 components:

| Effective SPDX identifier | Components carrying it |
|---|---:|
| `Apache-2.0` | 62 |
| `BSD-2-Clause` | 1 |
| `BSD-3-Clause` | 2 |
| `EPL-1.0` | 1 |
| `MIT` | 1 |

The counts are not mutually exclusive: the exact `org.xerial:sqlite-jdbc:3.41.2.2` JAR carries
both its declared Apache-2.0 license and a hashed embedded `LICENSE.zentus` classified as
BSD-2-Clause. All 19 embedded license entries are classified and hashed; this secondary license
is included in the component's effective SPDX set rather than being hidden behind the POM
declaration.

The seven former POM-declaration gaps are resolved without changing coordinates or versions:

- Guava `failureaccess`, `guava`, and both `listenablefuture` coordinates inherit the exact
  cached `guava-parent` Apache-2.0 declaration;
- `hamcrest-core:1.3` inherits the exact cached `hamcrest-parent:1.3` BSD-3-Clause declaration;
- `auto-value-annotations:1.6.3` and `commons-codec:1.15` carry the Apache-2.0 header in their
  exact cached module POMs; Auto Value is additionally bound to the official
  `auto-value-1.6.3` tag's `LICENSE.txt` URL and exact SHA-256, while Commons Codec carries an
  embedded Apache-2.0 license in the exact JAR.

The generator also inspects every exact cached AAR/JAR for embedded `LICENSE`, `NOTICE`, or
`COPYING` entries. It records 19 classified embedded license entries and three embedded NOTICE
entries with exact digests. There are no unresolved license coordinates, embedded license files,
or discovered NOTICE files. The obligation map is review-scope-specific: Apache NOTICE
preservation, BSD/MIT notice retention, and an explicit production-Legal review requirement for
EPL-1.0 distribution are recorded. This is engineering evidence, not legal advice and not an
approval by itself; the bounded owner decision is recorded below.

## Recorded Stage 0 reviewer decision

1. The Project owner approves internal synthetic Stage 0 evaluation use for the exact
   66-component graph and pinned Android platform image.
2. The recorded Apache/BSD/EPL/MIT license and NOTICE obligations are accepted for that bounded
   research scope; no component-specific exception was recorded.
3. The IP precondition is satisfied for Stage 0 only. Physical D1/D3, a fresh exact-commit
   preflight, and a later explicit measured-execution authorization remain separate blockers.

## Decision block — completed by `OD-13`

```text
Review decision: EVALUATION_APPROVED
Product reviewer: Project owner
IP policy reviewer: Project owner
Engineering/Security reviewer: Project owner (Stage 0 only)
Decision date: 2026-08-11
Reviewed inventory digest: sha256:63a2a3dadfbfe072770d914a74cbd40d6adbd517548bda4ba0331dd314ca6a98
License/NOTICE evidence locator: docs/evidence/poc-search-001/license-notice-inventory.json
License/NOTICE evidence digest: sha256:8b80fa573a2674cb32fe08446683f5b3d05ce4721b6bcb018edec51cf9fbeb50
Reviewed system-image digest: sha256:b1bb0769d0bed7698e61f203d7dc9bf6e7c37cd01a39d0d8788a11186bc78160
Restrictions/fallback: internal synthetic POC-SEARCH-001 Stage 0 research only; no measured execution by this decision; no production Legal/Security approval; no automatic FTS4, schema, or dependency admission; retain per-entity FTS or another separately reviewed fallback.
```

This completed block removes only the Stage 0 IP precondition. Measured execution remains blocked
and deferred because physical D1/D3 and a later owner authorization are absent. Production Legal,
independent production Security, SBOM/notices, ADR and admission evidence remain separate blockers.
