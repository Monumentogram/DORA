# Recovery accepted squash-lineage validation v0.1

Date: 2026-09-29. Scope: CI/governance provenance recognition only.

## Observed incompatibility

The exact candidate `a7701763a6486e4f707d23a70c0cc600431a88ac` failed
[Android CI 36613265772, attempt 1](https://github.com/Monumentogram/DORA/actions/runs/36613265772)
with `REC-I3 scope-first is not an ancestor of HEAD`. The same error was
reproduced locally on the unchanged validator. The existing integrated profiles
select particular lifecycle branches. This development branch reached the
historical source-branch check instead of an accepted post-squash profile.

This is a provenance-recognition incompatibility, not evidence of invalid
Recovery runtime behavior. No Recovery implementation or campaign is changed.

## Source history and integrated history

The reviewed source is `89551b17a84bc090ccf1cd36d48aeb59afc403fa`, with parent
`2de6d8238d99e71ae573ffa29481a59e052c0efd` and tree
`4519cbf6fda95f8e39a36e3b2c0bf62fef4981db`. The scope-first commit
`455f587881cf2a32ed23105a05d53a47424f18bd` is an ancestor of that reviewed source.
This actual object-graph property was verified after fetching the pinned source.
The validator must continue to require it.

The accepted squash commit `be37378ca88e0bd4aee1f2fe0c54362798bdef9d` has
the same exact tree and parent `da1d9bd13b71d609fe7ec4ea62fe1e984f726040`.
Squashing retained the reviewed tree without retaining the source commits as
ancestors of the integrated HEAD. Its later integrated history includes:

- correction `02f71246e4024ce6a8246c853c2f08d989d99b01`;
- governance successor `56d6ac509b4ddbee5ded5b99fcbb5c1315e52c3d`;
- E36 integration `3e7ce71410b9504bd08bfbcd1407c2e1f80ab8c6`;
- immutable accepted main anchor `55940df0c95e919a00708ae57e1b8aa23d89b6de`,
  tree `e153e50b7dc8d5651c3ac136efb1bfeaa1f56b16`,
  parent `c473a6f3877f60a1c1686e676606affd4fc66334`.

The candidate descends from this anchor. No authority is derived from a mutable
`origin/main` ref, the spelling of a development branch, or PR #86.

## Protected state

The existing protected Recovery namespaces retain these exact objects at the
accepted main anchor and original candidate:

| Protected path | Git object |
| --- | --- |
| `android/poc/recovery` | tree `a51d755e894b650e6a9cd4f839287779b3902616` |
| `docs/evidence/poc-recovery-001` | tree `eb9ab8d0d81ae4987815919e750f397c2338f0df` |
| `android/build.gradle.kts` | blob `4d5420e4e7ded1e7a768b26d65f19f9e0864a78b` |
| `android/gradle/verification-metadata.xml` | blob `887c279e4e3cdccd4dd1c70c758333f27e089a11` |
| `tools/verify_poc_recovery_dependency_inventory.py` | blob `728cd9fd2fdd56d9c2b86de4ff058847d3286e3b` |

Recognition requires exact accepted anchors, source-history provenance, protected
entries including their modes, and no protected mutation in descendant history,
index, worktree or untracked paths. A protected edit followed by a revert must
still fail. An unexpected merge must not hide a protected mutation. Required
integrated corrections must remain present. A matching branch name never
substitutes for any of these checks.

## Reproducible validation and authority boundary

Android CI explicitly fetches the pinned historical source from the public
repository without changing its checked-out revision. A missing source or failed
fetch is a failure, not a waiver. The validator verifies the object identities
and scope-first ancestry offline after that fetch. All existing CI checks remain
enabled, including Recovery dependency inventory and execution fail-closed checks.

The repair affects the governance validator, its regression tests and the exact
source-fetch CI step. It grants no Recovery runtime, execution, integration or
admission authority. Historical contracts and evidence remain unchanged. PR #86
remains a separate future reconciliation task.

Historical correction self-tests use their pinned correction checkpoint rather
than a later development tree. An optional V7 selector first checks that its
base object exists, so an older object-closed fixture can reach its own profile;
this does not relax the actual V7 validation or accept missing provenance in the
new descendant profile. Existing specific lifecycle profiles keep precedence.

This document does not assert CI PASS or 6.3 admission. The repair commit must
first pass the complete mandatory Android CI, including `android-bootstrap` and
`search-smoke`, on its own exact SHA. Only a later separate 6.3 decision may freeze
that CI-validated repair SHA as the Alpha implementation baseline.
