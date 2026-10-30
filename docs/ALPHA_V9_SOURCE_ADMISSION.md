# Alpha v9 source and persistence-inventory accounting

Status: `SOURCE_ADMISSION_ONLY / RUNTIME_QUALIFICATION_PENDING`. Authority:
[ADR-0031](adr/ADR-0031-twenty-four-hour-recording.md),
[DEC-053](DORA_MVP1_PRODUCT_DECISIONS.md#dec-053--alpha-v9-twenty-four-hour-recording-and-resumable-transcription-2026-10-30)
and the [v9 design](ALPHA_V9_24H_DESIGN.md). This accounting is neither a device
receipt nor a physical endurance, ASR quality, historical migration or release verdict.

The unchanged historical `owner-arm82-persistence-tests-v1.json` remains pinned by
its LF-normalized SHA-256. Its three historical `NOT_QUALIFIED` cases retain their
exact disposition. `owner_arm82_inventory.project_stage9_policy` still admits only
the historical Stage9 policy and rejects the v9 source. Its regression remains.

The additive [v9 storage admission](contracts/alpha-v9-storage-admission-v1.json)
pins the reviewed SQLCipher helper, schema verifier, migration/schema/catalog/page
implementations, recording controller and published schemas 4, 5 and 6. Kotlin
digests normalize only CRLF to LF. Schema JSON digests normalize key ordering and
insignificant JSON whitespace; duplicate keys are rejected. The admission manifest
itself is pinned in `tools/owner_v9_inventory.py`. Formatting does not waive policy:
a changed Kotlin source must be reviewed before its digest and manifest pin change.
There is no automatic repinning or alternate policy wildcard.

Structural host checks additionally require all v4 entities to remain identical
in v5, exactly five additive tables, unchanged v5 tables in v6 except the exact
non-unique deletion seek index, unchanged views and the accepted version/identity
chain. Actual encrypted migration tests remain mandatory; source/schema equality
does not replace their execution. Only after the full admission succeeds does the
current gate replay the inherited helper's static checks against its immutable
accepted Git object. All other product source checks continue on current sources.

The [v9 inventory](contracts/alpha-v9-persistence-tests-v1.json) accounts for all
197 discovered persistence methods in disjoint groups: 188 ordinary methods,
three historical unqualified methods, four separate-process methods and two opt-in
qualification methods. No inherited method is removed. The two
previously deferred over-five-minute synthetic rotation methods are mandatory in
the ordinary v9 group; actual execution is required before reporting success.

Owner Preview CI selects the 188-method inventory explicitly with no expected
skips. Stage9 CI retains its existing restart phases and adds the two paged restart
phases in separate instrumentation processes. The large encrypted synthetic trial
must be selected explicitly with `dora24hSynthetic=true` and no reduced
`doraSyntheticUnits`; a reduced smoke result does not qualify the maximum. The
large trial and physical microphone endurance are separate evidence. Their absence
is not converted into a skip or a pass for the ordinary group. Current exact-head
CI, maximum-size execution, signed upgrade, physical acceptance and Stage12 security
remain separately reported gates.

The metadata-only library follow-up adds four ordinary device methods covering
zero run-key opens, corrupt-tail listing versus authenticated acquisition,
pending-prefix metadata and authority/corruption fences. Static host checks
additionally require SELECT-only scalar metadata queries and reject direct
PCM/recovery/key acquisition in the metadata methods. These checks complement
the actual encrypted no-key-open tests; they do not authenticate audio for display
or grant resume/read permission from metadata.

The WAL/deletion follow-up adds four ordinary bounded-checkpoint methods and three
ordinary crash/revocation batch-receipt methods. The malformed native checkpoint
counter and final receipt-revocation regressions follow separate reviewed findings;
their actual RED runs were explicitly waived by the parent and are not claimed.
`ArchivedStorageRecoveryTest` is a separate opt-in
method requiring `doraRecoverFailed24h=true` and the exact restored synthetic
full-disk fixture. It never creates or deletes that fixture, and cannot enter the
ordinary suite or count as an expected skip. Its successful execution would qualify
only that preserved committed prefix, not the complete maximum or arbitrary
historical-vault migration. Eighteen semantic source pins were refreshed only after
the follow-up independent review and final formatting. The 250-test host batch and
working source gate passed. Seven supplemental API28 device methods passed in
330.492 seconds; complete ordinary-inventory execution and maximum-size qualification
remain separately reported requirements.
