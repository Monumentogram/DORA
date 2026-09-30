# 7.3 — Internal Alpha release v0.1

Status: PREPARED / ACCEPTANCE_PENDING. No release/device PASS yet.

## Scope and authority

Owner-authorized release verification on `stage/7-alpha-foundation`, starting at
`d48e41a65c377dc17d9ac3a0384d687061bc33c6`. Preserve ADR-0015 and the historical
7.1 release (alpha.1 / code 2). This next product release is
`com.monumentogram.dora`, `0.1.0-alpha.2`, code **4**. Code 3 remains a historical
disposable probe and cannot be built by the current product builder.
Debug retains its separate application ID and suffix. The frozen certificate is
`e77c9af533e603c0fa7c5c2000eb74b9514d001d98c3e5845cc2bae905be76bf`.

## Design and implementation plan

Use writing-plans, executing-plans and TDD inline under the explicit autonomous
owner request. The required two commits supersede separate design/plan commits.
Reuse the existing isolated worktree. No dependency upgrade or product runtime.

One properties file supplies the version to Gradle and the owner-local builder.
The builder checks clean Git source before and after compilation, then verifies
the signed APK before publishing it and a path-free receipt. Signing material
stays outside every checkout and never enters Gradle or CI.

An existing-toolchain Gradle init script exports the actual app release runtime
components, artifact hashes and relationships. A standard-library Python tool
reconciles those components against the app's release locks, rejects unresolved
and dynamic versions, and emits deterministic CycloneDX 1.6 JSON. The approved
graph is versioned separately from the final source-bound SBOM. No new plugin.
Release evidence validation checks relationships across source, SBOM, APK, CI,
backup restore, device identity and the explicitly limited migration disposition.

- [x] Tests first: builder rejects reused identity/dirty or changed source;
  SBOM rejects missing/extra components, changed relationships, dynamic versions,
  unresolved dependencies and wrong source/version; release rejects stale CI,
  wrong signer/SBOM/device/artifact/permissions and premature runtime claims.
- [x] Implement `android/alpha-release.properties`, builder checks,
  `tools/alpha_release_graph.init.gradle`, `tools/alpha_release_sbom.py` and
  `tools/validate_alpha_release.py`; add mandatory CI without removing checks.
- [x] Resolve and lock release configurations; review the graph and establish
  `docs/contracts/DORA_ALPHA_RELEASE_RUNTIME_GRAPH_V0_1.json`; no library upgrades.
- [ ] Run host/Gradle checks and independent review; commit implementation,
  push and require exact-SHA android-bootstrap and search-smoke success.
- [ ] Build/sign twice from clean implementation SHA; freeze SBOM; verify
  manifest, signer, native ELF/ZIP alignment and secret boundaries.
- [ ] Verify POCO M5 baseline provenance; upgrade 2 to 4, preserve UID and
  firstInstallTime, cold-launch/read back and compare pulled APK bytes. Retain v4.
- [ ] Reconcile Sheet with exact readback; publish docs/evidence-only closure;
  verify its own exact-SHA CI, final Sheet HEAD and PR #87 metadata.

Review focus: unsigned intermediate accidentally published; source changing
during build; SBOM graph plausible but not matching resolution; historical code
3 mistaken for product; self-referential final evidence SHA. A commit cannot
contain its own SHA: final evidence-head CI/Sheet reconciliation are retained in
the post-publication local receipt and final RESULT, never fabricated in Git.

Independent review found a runtime file-dependency omission. A real synthetic
Gradle fixture reproduced it for direct and local-module JAR dependencies;
the exporter now rejects both (RED then GREEN). Ordinary locked resolution
adds lock constraints to the root graph, so the approved inventory comes from
normal locked resolution after lock generation; artifact hashes and versions
were unchanged. Local detekt used relative arguments with identical input-set
assertions to avoid the Windows command-length limit; CI uses the normal task.
Deferred minor: tests cover builder helper gates, but a mocked full publication
orchestration test is not included. Actual owner builds remain mandatory.

## Signing custody and acceptance

The separate owner-local remediation must have verified an encrypted offline
backup, restoration, frozen certificate, private-key signing proof and temporary
copy cleanup before implementation. Public evidence contains outcomes and the
public certificate only. Password and backup location are never published.
Same-host signed APK repeatability and POCO acceptance are required; historical
rebuild byte identity is optional and must not be inferred from a signing proof.

## Non-execution

7.3C and Stage 8 NOT_STARTED. Recording, VAD, product audio storage, transcript
persistence, merge, auth/backend/object-storage/AWS-adapter runtime NOT_IMPLEMENTED.
Recovery integration and audio upload NOT_RUN; real product audio NOT_USED;
AWS NOT_CALLED, spend 0 BY THIS TASK; FIRST_REAL_PRODUCT_AUDIO NOT_READY.
main unchanged; PR #86 untouched; PR #87 OPEN / DRAFT / UNMERGED.
Stage 7 and Group C remain IN PROGRESS. Next separate task after PASS is 7.3C.
