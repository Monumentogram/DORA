# Kotlinx Coroutines 1.11.0 governed admission scope — Stage 0 v0.1

Task authority: task-scoped owner instruction under `OWNER-AUTH-BATCH-20260819-01` for bounded diagnosis and remediation of stale-red PR #30. This scope record does not reinterpret the repository-wide owner record, admit any other dependency, authorize Recovery execution, or change a product/readiness state.

## Frozen input identity

- repository: `Monumentogram/DORA`
- delivery branch: `codex/deps-kotlinx-coroutines-1.11.0-admission`
- exact current-main base commit: `da1d9bd13b71d609fe7ec4ea62fe1e984f726040`
- exact current-main base tree: `925bd08802fefc314742776d147771a92edfac70`
- stale Dependabot PR: `#30`, head `46c672d68d4f8c88cd28f273d58d8f914362dc45`
- observed PR #30 failure: `tools/validate_poc_search.py` rejects a catalog SHA mismatch before Gradle verification; PR #30 changes only the catalog and therefore does not provide the required lock, verification-metadata, resolved-graph, license, provenance, authenticity, security, or protected-path successor evidence.
- current catalog SHA-256: `230e8b8f5042b5e4852aa3ad05009e5b1d1336eb467d9d89f3d37a7f5104fc4c`
- current capture lock SHA-256: `e02b8f90e83cd744bcd7703ad3b7f4b1538991d43e5ec0f5f2ec34b0a5285f3c`
- current verification metadata canonical-LF SHA-256: `2d31104754fc8df67ff14d9f8fb613782170862d421202ad3132297793357f23`
- frozen PR #52 KSP overlay evidence SHA-256: `511914d7e001c786ace199535d5e0d6f79bbae73052ae5523c9cdc817eb08b84`
- frozen Search lock SHA-256: `3e47b2a46c493245ad24399b8bb26c834bc79b52397a4e920d5895bec695ba8f`

## Versioned hypothesis

Changing the single catalog pin `kotlinxCoroutines = "1.9.0"` to `"1.11.0"` affects only the `:poc:capture` catalog-owned Coroutines dependency family and its causally required transitive closure. The expected selected Coroutines family is `kotlinx-coroutines-android`, BOM, core, core-jvm, test and test-jvm at `1.11.0` in the configurations that previously selected `1.9.0`; unrelated tooling selections at `1.6.4`, `1.7.3`, `1.8.0`, `1.8.1`, and the standalone VPN kernel pin at `1.10.2` remain unchanged. Upstream `1.11.0` metadata declares Kotlin stdlib `2.2.20`; any resulting stdlib selection change is part of the mandatory resolved closure, not an independently requested Kotlin/plugin upgrade, and must be explicitly inventoried, verified and reviewed. If any other module lock changes or any non-causal dependency/version delta appears, implementation stops fail-closed.

No direct product source edit is expected. Compatibility is accepted only if the full documented host/static Android Tier A suite, affected capture tests, packaging checks, exact graph regression, and offline reproduction all pass.

## Deterministic evidence fixtures

- exact Git base commit/tree and the three base SHA-256 pins above;
- exact upstream Git tag/release commit/tree and Apache-2.0 license bytes;
- exact Maven Central POM, Gradle module metadata and JAR bytes for every newly selected or newly verified coordinate in the affected graph;
- detached OpenPGP signatures verified cryptographically to exact primary and signing-key fingerprints, without treating signer identity as production Legal/Security approval;
- current upstream repository advisory list plus Maven-coordinate queries against the GitHub Advisory Database, captured with exact query/time/result facts and no blanket vulnerability-free claim;
- exact repository lockfile inventory, resolved configuration/component projection, Gradle verification-metadata semantic delta, archive license/notice inventory, native/JNI scan, class-file major inventory, and deterministic online/offline command outputs;
- synthetic negative mutations for catalog/version drift, partial or unexpected lock deltas, verification-metadata removal/mutation/extra component, graph drift, hash/signature/license/advisory drift, native/JNI presence, claim escalation, KSP/Search-history mutation, Recovery execution/status escalation, and dirty-path residue.

All temporary downloads, generated key material, class files, Gradle test homes, and negative-test repositories must be created under bounded temporary directories and removed before success.

## Exact non-goals and claim ceiling

- no application, Capture, Search, Recovery, VPN, model, schema, UI, backend, or other product-source changes;
- no dependency upgrade other than the Coroutines `1.11.0` family and any publisher-declared, actually selected transitive closure proven necessary by the resolved graph;
- no Kotlin compiler/plugin, AGP, KSP, Room, Tink, Spotless, Gradle-wrapper, or standalone VPN-kernel pin change;
- no changes to the PR #52 KSP evidence, Search lock/evaluated projection, Room processor API invariant, or its `BUILD_TOOL_ONLY` classification;
- no Recovery module/evidence/lock change, no Recovery execution, device/emulator run, preflight, Phase A, fault/kill campaign, measurement, `PASS`, readiness, REC-I3 activation, dependency/production-admission claim for Recovery, or GOV-OMI work;
- no real/private data, network product behavior, model weight, new runtime coordinate, new build plugin, broad trust rule, checksum removal, ignored verification failure, or `local.properties`;
- no Backlog, Stage Status, Readiness, Product Decision, Design Spec, Technical Plan, ADR, or parent PoC claim/status change;
- no Ready transition, merge, or closure/modification of PR #30 in this delivery.

The maximum delivery claim is: `KOTLINX_COROUTINES_1_11_0_EXACT_GRAPH_PROVENANCE_LICENSE_SECURITY_AND_OFFLINE_REPRODUCTION_LOCALLY_VERIFIED_PENDING_INDEPENDENT_ADMISSION_REVIEW_AND_EXACT_HEAD_CI`. It is not a production Legal/Security approval, PoC `PASS`, readiness closure, or blanket security claim.

## Exact expected delivery paths

Only these paths may differ from the exact base:

1. `.github/workflows/android-ci.yml`
2. `android/gradle/libs.versions.toml`
3. `android/gradle/verification-metadata.xml`
4. `android/poc/capture/gradle.lockfile`
5. `docs/evidence/poc-capture-001/kotlinx-coroutines-1.11.0-admission-stage0-v0.1.json`
6. `docs/stage0/DORA_MVP1_KOTLINX_COROUTINES_1_11_0_ADMISSION_SCOPE_STAGE0_V0_1.md`
7. `tools/poc_search_dependency_inventory.py`
8. `tools/validate_poc_recovery_governance.py`
9. `tools/validate_poc_search.py`
10. `tools/verify_kotlinx_coroutines_admission.py`

The scope document is committed alone before any dependency mutation. If actual resolution requires another tracked path, the work stops and the discrepancy is reported; this list is not silently widened.

## Acceptance contract

1. The catalog delta is exactly one `1.9.0` to `1.11.0` Coroutines pin; every other catalog byte is unchanged.
2. `android/poc/capture/gradle.lockfile` is the only changed lockfile, with an exact governed selected-graph delta. Every other repository lockfile is byte-identical to the base.
3. Verification metadata keeps configuration/trust semantics and every pre-existing component/artifact/checksum record byte-semantically intact, adding only exact artifacts required by the governed affected graph; no checksum or trust weakening is allowed.
4. The PR #52 KSP transition is validated historically at its integrated revision. Its evidence, Search lock, OD-13 six-configuration/66-component projection, Room processor API invariant and build-tool-only/non-admission facts remain exact. The successor catalog layer is validated separately.
5. The Recovery validator recognizes only the exact governed successor overlay while proving Recovery protected content/evidence/lock unchanged and all Recovery execution, measurement, REC-I3, `PASS`, readiness and admission flags remain false.
6. Exact Maven/source/license/release/security/signature facts, affected graph/configurations, lock/metadata hashes, native/JNI absence, archive notices and bytecode compatibility are recorded and fail-closed validated.
7. Online provenance/authenticity and graph resolution pass first; the same graph and all affected Gradle verification/build/test commands then pass with `--offline` in a clean bounded Gradle home. A second deterministic offline run produces identical canonical output.
8. Full repository governance validators/self-tests, dependency/license inventories, native/ELF and APK 16-KiB checks, formatting/static analysis, documented Tier A host/unit/Android-test compilation/lint/assembly, and affected Capture tests pass without device/emulator execution.
9. The worktree is clean, contains no `local.properties` or temporary residue, and the final diff is exactly the ten paths above.
10. Publication is Draft-only. Independent exact-head admission review with P0/P1/P2 disposition and terminal exact-head CI are required; this task does not authorize Ready, merge, or PR #30 closure.
