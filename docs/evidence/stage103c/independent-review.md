# Independent Stage 10.3C review

2026-10-29. Fresh-context read-only reviewer; baseline c20b6ce, Stage10.3C working diff.
Reviewed source, tests, access ownership, paging, cancellation and privacy boundaries.
Reviewer ran no Gradle builds and made no source changes.

Initial verdict: **changes requested**, one Important issue, no Critical findings.

1. Important: case-insensitive `indexOf` could spend seconds on a long repeated-prefix
   query without checking cancellation, holding the serialized persistence worker.
   Reviewer reproduced 3.76 s on a 262144-character text / 32768-character query.
   Resolution: `LiteralSubstring`, linear Unicode code-point matching with a check
   every 1024 work steps. Regression tests include cancellation inside text and the
   exact long near-match shape, repeated prefixes, literal syntax and supplementary
   Unicode case. Tests failed before implementation and passed after the fix.
2. Minor documentation issue: at most 20 projections is a per-scan bound; explicitly
   loaded UI pages accumulate. Plan/ADR now state that distinction. No full document
   or historical-version cache was added.
3. During review, an oversized 1000-row synthetic fixture was identified; corrected
   to 2000 repeated sentences within the existing 262144-byte storage limit.

Reviewer found active/latest-edit selection, sparse cursors, error distinction,
generation invalidation, background clearing and editor-open cancellation sound.
No ordinary library/editor/player session ownership race was found.

Runtime-only items were explicitly not judged by static review: physical response
time, audio-recovery latency, UI race coverage, signing/native equivalence and build
outcomes. Those require separate evidence in the result report. Physical acceptance
remains DEVICE_VALIDATION_PENDING. No independent approval to merge is implied.
