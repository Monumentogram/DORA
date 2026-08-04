# Dora MVP 1 — Stage Status

Updated: 4 August 2026\
Baseline: `1be83e2940a09f7b23e33b4cdf3827de2690f3fd`\
Repository: private `Monumentogram/DORA`\
Default branch: `main`\
Active stage: `Stage 00 — GitHub, Implementation Readiness & Bootstrap`\
Active branch: `stage/00-readiness-bootstrap`\
Stage state: **READY FOR REVIEW — NOT MERGED**

## Completed evidence

- local baseline SHA/history/worktree checked;
- private GitHub repository created without an extra initial commit;
- baseline published to `origin/main` and verified by exact SHA;
- Stage 00 branch created from the baseline;
- technical plan, design spec, token JSON and screen inventory read completely;
- readiness, product decision and executable backlog records created.
- minimal JVM 17/API 28–36 Android bootstrap created with pinned dependencies and lockfiles;
- local handoff validation, four module unit-test suites, lint and debug APK assembly passed;
- transitive native inventory allowlisted; ELF and APK 16-KiB alignment checks passed;
- least-privilege, commit-SHA-pinned GitHub Actions workflow created.
- Stage 00 branch published and draft Pull Request #1 opened against `main` without merge;
- PR-triggered `android-bootstrap` workflow completed successfully;
- private visibility/default `main` reverified; squash-only/manual merge policy and repository labels configured.

## Remaining Stage 00 exit checks

- Owner: upgrade the account/repository to GitHub Pro before protection can be enforced on this private repository. GitHub returned `403`; making Dora public is prohibited.
- After upgrade, require PR + GitHub Actions app `15368` check `android-bootstrap`, linear history and conversation resolution; disable force-push and deletion for `main`.
- Owner/reviewer handles any later approval and merge. Stage 00 leaves Pull Request #1 unmerged.

## Current gates

- Only bootstrap/docs/CI changes are allowed in this branch.
- Production product functionality is not started.
- Production application ID/signing ownership remain unresolved; the bootstrap ID is explicitly non-release.
- Next safe stage after review is Stage 0 PoC, subject to backlog Definition of Ready.

## Update protocol

Every later task updates this file only when stage truth changes. PR/build live status remains authoritative in GitHub and should not be copied as a stale badge or hard-coded run ID here.
