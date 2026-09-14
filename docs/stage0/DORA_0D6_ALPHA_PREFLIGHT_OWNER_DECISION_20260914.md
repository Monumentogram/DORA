# 0D.6 internal alpha preflight owner decision — 14 September 2026

Decision ID: `DORA_0D6_ALPHA_PREFLIGHT_20260914`

The project owner explicitly removes mandatory human review by Katerina or another
accountable human reviewer for the current internal alpha. This amendment implements
that instruction only for the three synthetic E36-GAPI preflight payloads below.
It supersedes the human-review prerequisite in the current execution scope and the
Recovery PoC prerequisite in section 14.3 of the technical plan for this bounded path.
No human review is asserted, generated, or replaced by an AI advisory.

Scope: `INTERNAL_ALPHA_E36_PREFLIGHT_ONLY`

- `SUPPLEMENTAL_SQLITE`
- `JOURNAL_CONNECTIONS`
- `PLATFORM_PREREQUISITES`

Use the existing exact selectors on task-owned AVD `dora_api36_recovery`, serial
`emulator-5556`, API36 Google APIs x86_64 image revision 7. The successor must descend
from packaged candidate `e8360b47c7479b6e8a2bcf3c32fd446e06291c2a`; its frozen commit,
tree, app/test APKs, manifest, driver, parser, launcher and prerequisite artifacts
must be bound in a separately versioned private execution packet. Exact-source CI,
technical/advisory review, package/build equivalence, environment ownership,
strict requested-test completion and retention-before-cleanup remain required.

The owner's actual instruction supplies authority. This file's pinned SHA-256
provides integrity only; an arbitrary file or a generic approval flag cannot grant
this exception. The original unapproved packet and historical results remain immutable.
`accountableReviewApproved` stays false and no formal reviewer identity is supplied.

The exception cannot admit `CAMPAIGN`, physical profiles, other payloads or later
production scopes. This task ends after the three preflight results and handoff.
It excludes the 414 E36 campaign entries (270 fault injections and 144 hard kills),
physical devices, PR merge, direct-main writes, force push, host reboot and personal
data. Legacy/default admission behavior remains intact outside this path.

Three passing tests do not close the fault/hard-kill campaign, full Recovery or
0D.6. Physical/API requirements, minSdk28 and the current PoC API33+ boundary remain
unchanged. Missing runtime evidence stays unpassed. A subsequent campaign requires
its own applicable execution packet and scope; this decision cannot authorize it.

Authority provenance: owner-supplied `DORA-ALPHA-0D6-PREFLIGHT-NEXT-STEP-PROMPT-3.md`,
received 14 September 2026; original instruction and delivery metadata retained in
the private work hierarchy. No private evidence is included in the public repository.
