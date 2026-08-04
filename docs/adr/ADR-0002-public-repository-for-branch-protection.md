# ADR-0002: Temporary public repository for enforced main protection

Status: Accepted\
Date: 4 August 2026\
Decision owner: Project owner\
Related: S00-SEC-001, S00-GIT-004, GOV-REPO-001, Pull Request #1

## Context

Stage 00 requires development through dedicated branches and Pull Requests with server-side protection of `main`. GitHub rejected branch protection while the user-owned repository was private on the current free plan with `403 Upgrade to GitHub Pro or make this repository public`.

The owner explicitly accepted temporary public visibility for the existing `Monumentogram/DORA` repository. Creating a replacement repository, changing the baseline, rewriting history or merging Pull Request #1 were excluded.

Public visibility exposes all reachable Git history, tracked files, Pull Request content and Actions logs. It is therefore acceptable only after a full pre-public audit and with continuing controls that prohibit private audio, personal data, credentials, signing material, production access configuration and unapproved binary/model artifacts.

## Pre-public evidence

The audit was completed before the visibility change:

1. Checksum-verified Gitleaks 8.30.1 scanned `--all --full-history` and exported trees of `main` and `stage/00-readiness-bootstrap`.
2. Gitleaks reported one `generic-api-key` candidate in the immutable technical baseline. Manual redacted review proved it was natural-language OIDC architecture prose without an assigned value, credential structure or encoded blob. It is a false positive and does not require baseline modification, history rewriting or rotation.
3. An independent pattern/path scan covered every tracked text file in all three commits. It found no GitHub/provider tokens, API keys, secret assignments, passwords, private-key headers, credential-bearing URLs, personal emails/phones, private network endpoints or local user paths.
4. No tracked `.env`, local configuration, signing key, credential file, temporary artifact, APK/AAB/native library, log or backup exists. The only tracked binary is the expected checksum-controlled Gradle wrapper JAR.
5. Commit identities use a non-personal role identity on the local-only domain; no personal email is present in Git metadata.
6. GitHub repository and Dependabot secret counts, Actions variables, environments, uploaded artifacts, deploy keys, webhooks and releases are zero. Pull Request text contains no credential or local-path pattern.
7. All four existing Actions runs and their logs were scanned. Gitleaks found no secret. Independent matches were action input variable names, not values; no personal workstation path was present.
8. The local worktree was clean and the only reachable Git commits were the immutable baseline and the two Stage 00 commits.

The audit reports were kept outside the repository and contain no approved project artifact.

## Decision

1. Change the existing repository in place from private to public. Preserve repository identity, branches, commits, baseline SHA and Pull Request #1.
2. Protect `main` with:
   - strict required check `android-bootstrap` from GitHub Actions app ID `15368`;
   - Pull Request required with zero approval count while the project has a single owner/reviewer;
   - enforcement for administrators;
   - linear history and resolved review conversations;
   - force pushes and branch deletion disabled.
3. Keep squash as the only enabled merge method and do not enable auto-merge.
4. Enable GitHub secret scanning and push protection.
5. Treat every committed file, Git object, PR body/comment and Actions log as public. Continue using least-privilege workflows with commit-SHA-pinned actions and no repository secrets.
6. Do not merge Pull Request #1 as part of this decision. The baseline on `main` remains unchanged.
7. Do not interpret public visibility as product readiness, a release, dependency admission, approval of real datasets or a repository license decision.

## Consequences

Positive:

- `main` protection is enforced by GitHub instead of relying only on procedure;
- required CI provenance is bound to the GitHub Actions app;
- direct/force pushes and deletion of `main` are blocked, including for the administrator;
- GitHub can scan the public history and block supported secret patterns on future pushes.

Costs and risks:

- the technical/design baseline and bootstrap are publicly readable and may be cloned or cached; a later private switch cannot retract existing copies;
- public forks, unsolicited issues and Pull Requests become possible;
- zero required approvals prevents a single-owner deadlock but does not provide independent human review;
- long-term visibility, account plan and licensing/contribution terms remain an owner decision under `GOV-REPO-001`.

## Verification

- repository API reports `visibility: public` for the original repository ID;
- `main` still points to baseline `1be83e2940a09f7b23e33b4cdf3827de2690f3fd`;
- branch API reports `protected: true` with the exact controls above;
- secret scanning and push protection report `enabled`;
- Pull Request #1 remains open and unmerged;
- Stage 00 branch and subsequent CI remain green before handoff.

## Rollback

Returning the repository to private visibility requires a new explicit owner instruction and a fresh audit. Before the switch, the owner must provide an account/plan where the existing `main` protection remains available or explicitly accept a replacement governance control. After the switch, re-read the protection API and stop development if required PR/status checks are no longer enforced. Historical public exposure cannot be rolled back.
