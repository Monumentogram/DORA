# Dora MVP 1 — Stage Status

Updated: 4 August 2026\
Baseline: `1be83e2940a09f7b23e33b4cdf3827de2690f3fd`\
Repository: public `Monumentogram/DORA` by temporary owner-approved decision (ADR-0002)\
Default branch: `main`\
Active stage: `Stage 00 — GitHub, Implementation Readiness & Bootstrap`\
Active branch: `stage/00-readiness-bootstrap`\
Stage state: **READY FOR REVIEW — NOT MERGED**

## Completed evidence

- local baseline SHA/history/worktree checked;
- GitHub repository originally created private without an extra initial commit;
- baseline published to `origin/main` and verified by exact SHA;
- Stage 00 branch created from the baseline;
- technical plan, design spec, token JSON and screen inventory read completely;
- readiness, product decision, executable backlog and cross-stage Test Strategy records created;
- minimal JVM 17/API 28–36 Android bootstrap created with pinned dependencies and lockfiles;
- local handoff validation, four module unit-test suites, lint and debug APK assembly passed;
- transitive native inventory allowlisted; ELF and APK 16-KiB alignment checks passed;
- least-privilege, commit-SHA-pinned GitHub Actions workflow created.
- Stage 00 branch published and ready-for-review Pull Request #1 opened against `main` without merge;
- PR-triggered `android-bootstrap` workflow completed successfully;
- pre-public audit covered all refs/full Git history, every commit tree, tracked filenames/binaries, commit identities, PR text, Actions configuration and all existing Actions logs; checksum-verified Gitleaks 8.30.1 produced one manually verified natural-language false positive and no real secret;
- existing repository changed in place to public by explicit owner instruction; default `main`, baseline SHA, branches and PR remained unchanged;
- server-side `main` protection API-verified: PR required, strict `android-bootstrap` check from GitHub Actions app `15368`, admin enforcement, linear history and conversation resolution; force-push/delete disabled;
- GitHub secret scanning and push protection enabled; squash-only/manual merge policy and repository labels remain configured.

## Remaining Stage 00 exit checks

- Owner/reviewer handles any later approval and merge. Stage 00 leaves Pull Request #1 unmerged.
- `GOV-REPO-001` remains a non-blocking P1 owner action: decide long-term visibility/account plan and repository licensing/contribution terms before merging an external contribution or returning the repository to private visibility. On the current free plan, private visibility must not be restored until server-side `main` protection is proven to remain available.

## Current gates

- Only bootstrap/docs/CI changes are allowed in this branch.
- Production product functionality is not started.
- Production application ID/signing ownership remain unresolved; the bootstrap ID is explicitly non-release.
- Next safe stage after review is Stage 0 PoC, subject to backlog Definition of Ready.

## Update protocol

Every later task updates this file only when stage truth changes. PR/build live status remains authoritative in GitHub and should not be copied as a stale badge or hard-coded run ID here.
