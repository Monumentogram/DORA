# POC-SEARCH-001 Stage 0 artifact evaluation review

Status: **REVIEWERS_ASSIGNED — REVIEW PENDING**\
Date opened: 11 August 2026\
Scope: Stage 0 evaluation only\
Owner decision: `OD-11`

This record is the public-safe review surface for the exact dependency and Android platform
inventory. Assignment of a reviewer is not an approval. No artifact changes to
`EVALUATION_APPROVED` until the decision block at the end is completed.

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
- Inventory digest: `sha256:ae182e42e773b4dd8c45f1cd77e2713ae113ba9873f69cfbfe78a7419d948fed`.
- Locked components: 66.
- Binary AAR/JAR artifacts with exact digests: 52.
- Maven POM records with exact digests and canonical source URLs: 66.
- Lock: `android/poc/search/gradle.lockfile`, exact configurations
  `_agp_internal_debug_kspClasspath`, `debugAndroidTestRuntimeClasspath` and
  `debugRuntimeClasspath`.
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

## Review still required

1. Confirm internal Stage 0 evaluation rights for every exact locked component and the pinned
   Android platform image.
2. Verify the transitive inventory, artifact digests, canonical publishers and source URLs.
3. Resolve seven components whose cached POM contains no named license declaration:
   `com.google.auto.value:auto-value-annotations:1.6.3`,
   `com.google.guava:failureaccess:1.0.2`, `com.google.guava:guava:33.2.1-jre`,
   `com.google.guava:listenablefuture:1.0`,
   `com.google.guava:listenablefuture:9999.0-empty-to-avoid-conflict-with-guava`,
   `commons-codec:commons-codec:1.15` and `org.hamcrest:hamcrest-core:1.3`.
4. Record reviewed canonical license-text digests and applicable NOTICE/attribution obligations.
5. Record restrictions and fallback for any rejected component.

## Decision block — intentionally incomplete

```text
Review decision: <EVALUATION_APPROVED | REJECTED | REMAINS_PROPOSED>
Product reviewer: Project owner
IP policy reviewer: Project owner
Engineering/Security reviewer: Project owner (Stage 0 only)
Decision date: <UTC date>
Reviewed inventory digest: sha256:ae182e42e773b4dd8c45f1cd77e2713ae113ba9873f69cfbfe78a7419d948fed
License/NOTICE evidence locator: <locator>
Restrictions/fallback: <text>
```

Until this block is complete, measured execution remains blocked. Even after a Stage 0
`EVALUATION_APPROVED` decision, production Legal, independent production Security, SBOM/notices,
ADR and admission evidence remain separate blockers.
