# Contributing to Dora

Dora currently uses a public, pull-request-only development workflow. Public visibility is the temporary owner-approved governance decision in `docs/adr/ADR-0002-public-repository-for-branch-protection.md`; it is not permission to publish private data, credentials or production configuration.

## Before starting

1. Read `AGENTS.md`, `docs/DORA_MVP1_STAGE_STATUS.md` and the scoped backlog item.
2. Read the technical/design source sections and linked DEC/ADR completely.
3. Confirm the worktree is understood and update local `main` without rewriting history.
4. Create one dedicated branch from `main`.

Suggested branch names:

- `stage/<number>-<slug>` for a planned stage;
- `poc/<number>-<slug>` for an isolated feasibility experiment;
- `fix/<slug>` for a scoped defect;
- `docs/<slug>` for documentation-only changes.

## Pull requests

- Keep a PR limited to one backlog objective and name its ID in the description.
- Explain what changed, why, user/developer impact, evidence and fallback.
- Include exact test commands/results and privacy/license/native implications.
- Do not merge when required CI is red or a linked P0 decision is unresolved.
- Prefer squash merge only after review if repository policy later approves it; never force-push shared work.

## Repository safety

Treat committed history, Pull Request content and GitHub Actions logs as publicly readable. Run an appropriate secret scan before publishing changes and investigate every finding without copying a suspected secret into an issue, PR or log.

Never commit:

- `local.properties`, keystores, signing credentials or service credentials;
- access/OAuth tokens, private keys or provider configuration containing secrets;
- real private meeting audio/transcripts or decrypted exports;
- model weights/native binaries without an approved admission record;
- generated build/cache/IDE files.

If unrelated local changes exist, preserve them and stage explicit task paths only.

## Build

The Android project is rooted in `android/`. The canonical commands are documented in the root `README.md` once the bootstrap is present. CI is the final shared check; device/ML/security gates remain required for their respective stages.

## Decisions and evidence

- Product choice: update `docs/DORA_MVP1_PRODUCT_DECISIONS.md`.
- Technical choice with consequences: add/supersede an ADR under `docs/adr/`.
- Execution order/status: update the implementation backlog/status.
- PoC: commit scripts/config/schema and a redacted machine-readable report; keep raw private data outside Git.
