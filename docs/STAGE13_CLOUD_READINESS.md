# Stage 13 — Cloud ASR readiness

Latest checkpoint, 2026-11-03: **PASS / SAMSUNG_CLOUD_ASR_OWNER_READY for one short RU
test over Wi-Fi on installed v10**. See the dated execution and network follow-up below.
EN was declined; mobile HTTPS, live cancellation/retry and long Cloud remain unqualified.
The original software-only record follows unchanged.

Date: 2026-11-02. Branch: `codex/stage13-owner-alpha-stabilization`.
Baseline: Alpha v9, `49ca2070f15a618d145ded307fc87249f382b22c`.

**Software regression: PASS. Current-candidate Amazon live qualification: PENDING.**
No AWS call, production configuration change, paid submission, phone operation,
credential change or deployment was performed for this Cloud track. Stage 13 remains
PARTIAL, Stage 12 remains PARTIAL, and [issue #125](https://github.com/Monumentogram/DORA/issues/125)
remains OPEN. This is bounded owner Alpha stabilization under technical-plan Stage 13,
DEC-053 and ADR-0031; it is not full security or public-service admission.

## Preserved evidence and authorization

The [Stage 9.2R public receipt](evidence/stage9_2r/acceptance.json) records the historical
direct-HTTPS RU/EN short-job success, stable Local/Cloud history, two retained edits after
restart, and scoped cleanup observations. The later owner-local Stage 9.3 report records
one authorized 11.05-second EN smoke after permanent registration. Those observations
remain valid for their exact historical configurations; they do not qualify v9/current
Android, Samsung, or a 30/60-minute composed Amazon job today.

[ADR-0029](adr/ADR-0029-permanent-owner-cloud-registration.md) grants continuing
installation authority to an explicitly trusted key, with proof-bound short sessions.
It is not permission to upload a new recording, enroll another device, increase cost
limits, or deploy this patch. Historical approvals bounded actual clips/attempts.
The Stage 13 instruction preserves those limits and expressly requires separate
authorization for unavailable live scope. No new long-job authorization was found.

The unchanged controls are: long Cloud disabled by default, USD 0.25 daily reserve,
USD 5 hard daily policy ceiling, USD 20/month AWS Budget, one active owner chain,
ten new actions/day, two attempts/child, explicit consent, original first-byte
24-hour deadline and durable cancellation/cleanup. A monthly Budget is not permission
to bypass the software daily reserve. Production AWS has not been changed.

## Evidenced software repair

Before this patch, `CompositeRuns.quote` reported every existing run as affordable.
After UTC rollover, an unstarted child's allocation must move from the old aggregate
hold to the actual start day. Dispatch already checked the current day's reserve,
but the quote did not. A sibling's paid retry could leave too little money for the
remaining child: Android would accept the quote and upload before dispatch rejected it.

The quote now checks the total still-unstarted allocation against the persisted hold
and, when the hold belongs to another UTC day, current available reserve. Started or
uncertain attempts retain their existing reservation; the quote neither spends nor
refunds money. Same-day holds, child identities, manual retry rules, runtime dispatch
checks, consent and deletion are unchanged. The API shape and database schema are
unchanged. `required_micro_usd` still describes the full first-attempt plan, while
`affordable` accounts for its existing hold and current-day needs.

The failing reproduction restores a two-child durable ledger after child zero moved
to today and used its explicit second attempt. A USD 0.023002 original first-attempt
plan has USD 0.007 left today and a USD 0.011502 unstarted child. The old quote says
affordable; the corrected quote rejects it before Android's existing audio-open/upload
guard. A separate real signed upload with an uncertain provider Start verifies that a
started child is not reserved or submitted again by quoting it.

Changed implementation: `backend/dora_cloud/composite.py`.
Changed tests: `backend/tests/test_composite.py`.
No Android production code or native payload changed in this Cloud track.

## Verification and coverage

Commands from `backend/`, using the existing host Python 3.12 environment:

```powershell
& '..\..\.tools\cloud-v9-venv\Scripts\python.exe' -m unittest discover -s tests -p test_composite.py -v
& '..\..\.tools\cloud-v9-venv\Scripts\python.exe' -m unittest discover -s tests -v
```

RED: the new rollover regression failed with `True is not false` before the repair.
GREEN: composite 19/19 passed; full backend 91/91 passed in 36.178 seconds.
The full suite emitted an existing botocore `datetime.utcnow()` deprecation warning;
there were no failed tests. Fixtures use synthetic audio, real signatures, HTTP routing,
SQLite/encryption and an isolated provider/SDK transport, without live AWS credentials.

| Requirement | Available evidence | Boundary |
|---|---|---|
| Authentication and HTTPS | Backend signature/replay/session/ownership/consent tests; historical direct mobile HTTPS receipt | New candidate/device direct live path PENDING |
| Upload and result | Current backend full suite includes exact chunks, lost final replies, encrypted stable result and restart | Provider fake/SDK stubs establish software behavior |
| Cloud version and user edits | v9 API28 composed HTTPS integration preserved Local edit/version/selection; retained `CloudTranscriptPersistenceTest` and `CloudFailureRecoveryTest` | Current Android release verification is reported by the parent task; no new device execution in this track |
| Offline/retry | Same-action resume, uncertain Start reconciliation, two-attempt cap, durable results and current rollover-quote regression | Reconnect is manual; no automatic background submission is claimed |
| Cancellation/deletion | Atomic parent/child fencing, crash rollback, grant/deadline tests and persisted cleanup outbox | `cleanup_pending` is not an all-copy erasure receipt; issue #125 remains OPEN |
| Bounded audio memory | Current 30-minute signed-upload/streaming test; real SDK transport rejects unbounded reads; Android 8 MiB window plus 64 KiB request chunk | Bounds concern audio buffers, not a measured total device PSS for live 60 minutes |
| Exact composition and timestamps | 48-part manifest, exact tail coverage, deterministic IDs; retained Android `CloudCompositeRunTest` checks global times and resume skipping committed parts | No heuristic text deduplication; actual Amazon boundary words remain a live quality check |
| Aggregate cost | Current admission/rollover/retry/idempotency tests | Reserve estimates are not invoices; no live billing result |

Inherited v9 [qualification](ALPHA_V9_QUALIFICATION.md) includes the 1,801-second,
two-child Android API28 HTTPS fixture and the expected USD 43.68 rejection for a
24-hour request. This was a fake provider, not Amazon. No unchanged 24-hour synthetic
storage run was repeated for this bounded backend quote change.

## Live qualification runbook: 30 minutes, then 60 minutes

This is a reviewable procedure, not authorization to execute it. Keep the current
production long-run switch disabled until every prerequisite is explicitly satisfied.

1. Record owner authorization naming the installation, exact synthetic/consented
   source identifiers and hashes, RU or EN, endpoint, allowed operations, permitted
   attempts, spending envelope and data-retention/deletion verification. A Samsung
   installation, microphone operation or network-setting change also needs the owner's
   separate physical-device permission. Permanent registration does not cover a new key.
2. An authorized operator records the actual backend revision and compatibility,
   current proof/session behavior, HTTPS direct route, account/region, effective opt-out,
   S3/KMS controls, watchdog/health and cleanup status using the existing preflight and
   runbooks. No broad infrastructure update follows from this checklist. Resolve any
   authorization conflict before reading private deployment state or changing resources.
3. Review and separately approve any candidate deployment, long-run enablement and
   finite test reserve. Defaults must reject the proposed long cases today. Keep the
   USD 20/month Budget and operational limits intact. Reconcile current spend and
   reservations privately before each run; a software quote is advisory and dispatch
   remains authoritative. Do not substitute fictional fixture prices for live estimates.
4. Freeze exact PCM16 mono 16 kHz test sources in a controlled test installation using
   the established synthetic fixture process or explicitly authorized recording. Use
   identifiable speech at start/end and around 30:00 in the 60-minute source; retain
   legitimately repeated phrases. Keep source text/audio and provider IDs private.
   Record the actual frame count; the following exact matrix applies only to these sizes.

| Case | Frames | Composed children and ranges | Canonical uploaded WAV bytes | Existing first-attempt reserve |
|---|---:|---|---:|---:|
| 30 minutes | 28,800,000 | One: `[0, 28,800,000)` | 57,600,044 | USD 0.91 |
| 60 minutes | 57,600,000 | Two: `[0, 28,800,000)` and `[28,800,000, 57,600,000)` | 115,200,088 total | USD 1.82 |

These arithmetic reserves use the existing USD 0.03/minute plus USD 0.01/attempt
policy, not a newly asserted Amazon price. Initial 30+60 qualification allows three
first-attempt Starts only (USD 2.73 reserve total); each additional 30-minute child
attempt needs another USD 0.91 and explicit authorization. The existing USD 0.25 daily
reserve cannot admit even the first 30-minute case. Do not lower estimates or raise
limits to make a test pass without the separate review in step 3.

5. Run 30 minutes first through the current Android manual Cloud UI. Check the
   authenticated quote has one child and the exact source/range/policy, then explicitly
   grant the disclosed Cloud action. Capture only content-free timing, peak PSS/heap,
   bytes and categorical states. Verify direct HTTPS has no ADB/proxy payload relay.
   Keep the app foreground for recognition; recording screen-off endurance is a
   separate gate. Persist one complete Cloud version and verify that the prior Local
   version, edits and selected version remain unchanged until explicit user selection.
6. Restart the app, reopen the saved result, edit and save Cloud text, search/export
   the selected version, and verify the previous history and source remain accessible.
   Poll/reconcile the same action after a lost reply; provider Start count stays one.
   A foreground timeout requires manual resume of that action, not a new consent/job.
   Confirm scoped temporary cleanup and record unresolved outbox status honestly.
7. Proceed to 60 minutes only after all 30-minute correctness checks pass and its
   reservations/cleanup are reconciled. Recheck the budget and two-child quote.
   After child zero commits locally, exercise one authorized connectivity interruption
   before child one completes. Use interruption/foreground stop, not explicit consent
   cancellation, when testing resume. Reopen and manually resume the same parent.
   Child zero's local checkpoint and provider identity must be reused; no second Start
   for that child. Child one ends at global 3,600,000 ms; its non-null relative timestamps
   are offset by 1,800,000 ms and must remain within its range.
8. Compare exact source frame coverage and per-child/global provenance. Verify no
   missing or duplicate part, one final published version only after both parts, and
   preserved prior edits/selection. Review the actual boundary speech against the
   frozen reference separately: structural continuity does not prove zero ASR word
   omissions or a WER target. Record RU/EN quality only for languages actually run.
9. Test explicit cancellation only on a separately authorized synthetic action with
   its own finite reserve, or as the owner-approved disposition of a failed case.
   Verify no new bytes/Starts/publication after the durable revoke, completed local
   pages/history survive, and pending remote cleanup is visible. AWS computation
   already running may continue; poll exact scoped cleanup without promising instant
   physical erasure. Do not cancel or delete historical owner recordings for this check.
10. Stop on source/provenance mismatch, duplicate paid Start, missing committed part,
    edit loss, unexpected memory growth, quota denial, consent failure or unresolved
    cleanup. Preserve the local source and private receipts. Do not automatically
    resubmit, increase limits or scale beyond 60 minutes. Further duration requires a
    new reviewed qualification and affordable aggregate reservation.

Record exact APK/backend revision, device/API/ABI, source duration/hash, child counts,
peak memory, elapsed time, retries/reservations, retention and content-free failure
categories. Keep separate fields for `SOFTWARE`, `LIVE_30_MIN`, `LIVE_60_MIN`,
`BOUNDARY_QUALITY`, `SAMSUNG_PHYSICAL` and `SCOPED_CLEANUP`. Public evidence must omit
audio, transcript, keys, tokens, private endpoints, egress addresses and raw logs.
The existing short-clip `CloudLiveAcceptanceTest` is not a 30/60-minute runner and
writes private result receipts; do not invoke it as a substitute or publish its files.

Until authorized current execution produces those receipts, long Amazon live remains
PENDING. Neither this patch nor a successful future owner smoke closes issue #125,
the full Stage 12 security gate, the supported-device matrix or Stage 13 endurance.

## 2026-11-03 update — bounded Samsung v10 RU live PASS

The preceding 2026-11-02 sections preserve the original software-only boundary.
Separately authorized execution now establishes **PASS for one short Samsung RU
Cloud scenario** on the existing Owner Alpha v10, source
`45e1e093d437bf925b170bb291754b424df11908` ([PR #130](https://github.com/Monumentogram/DORA/pull/130)).
No APK, Cloud code or infrastructure replacement was needed. The Samsung installation
was permanently trusted using its own installation identity and public key through
the existing Stage 9.3 protocol. Other registrations, including POCO, remained unchanged;
short proof-bound sessions and replay protection were retained.

After explicit owner permission and in-app RU upload consent, one nonconfidential
microphone recording of **25.45 seconds / 407,200 frames** produced exactly **one job
and one paid attempt**. The authenticated HTTPS path accepted 814,444 WAV bytes and
stored the completed result. Backend job creation to completion took approximately
**217.7 seconds, including upload**; this is not provider processing time alone.
The app showed a separate nonempty Cloud version. Both Local and Cloud versions
survived a fresh-process restart, with the previous Local edit and Cloud text unchanged.
All nine prior backend jobs and other installation records matched the pre-test baseline.

The attempt reserved **USD 0.023**, below the separately authorized USD 0.03 cap.
The estimated Transcribe charge is **USD 0.0026**, using 26 billed seconds at the
verified Frankfurt standard-batch rate of USD 0.0001/second; this is not an invoice.
The USD 20/month Budget, USD 0.25 daily reserve and existing operation limits remain
unchanged. Fresh backend privacy preflight passed, and API, watchdog, HTTPS, certificate
renewal and health services were active. Long Cloud remains disabled.

Read-only checks after the normal cleanup mechanism confirmed absence of the exact
provider job, expected temporary S3 input/output keys and their multipart uploads,
with no truncated listings. Temporary encrypted audio bytes and pending cleanup were
zero; the retained encrypted result remained available. The monitor issued no Start,
retry or Delete calls. Object-version listing was permission-denied; privacy preflight
verified disabled bucket versioning, but version inventory and provider-internal copy
erasure are not claimed. **Issue #125 remains OPEN.**

This result does not qualify EN, an active cancellation or retry, mobile internet,
network reconnection, or operation with USB physically disconnected. Those optional
scenarios need their own owner permission and any required paid-attempt authorization.
The 30/60-minute Amazon cases remain PENDING. Stage 13 and Stage 12 remain **PARTIAL**;
the existing recording/playback latency limitation is not resolved by this Cloud smoke.
Content-free backend, cleanup and UI/restart receipts are retained privately; no audio,
transcript text, endpoint, device identity or credentials are included here.

### Owner follow-up: EN declined and network check

The owner declined the optional EN test and authorized a short mobile-network check
with Wi-Fi restored afterwards. No additional transcription or paid attempt was
started. At inspection Wi-Fi was already off; the cellular default network was not
validated by Android and a DNS lookup failed. The bounded browser probe did not
produce an attributable HTTP response. Mobile HTTPS therefore remains **NOT VERIFIED**;
these observations do not establish a certificate or application-authorization defect.
No TLS bypass, DNS/VPN change or cellular configuration change was made.

Wi-Fi was enabled and reconnection to a validated Wi-Fi default network was confirmed.
The original 30,000 ms screen timeout was restored and DORA reopened. A fresh HTTP
response after reconnection was not captured, so network reconnection is confirmed
only at the Android transport layer. The earlier successful RU upload/result remains
the direct authenticated Wi-Fi HTTPS evidence; ADB reverse mappings were empty.
Operation with the USB cable physically removed remains untested. No new APK was
needed for this Cloud activation. The bounded result is
**PASS / SAMSUNG_CLOUD_ASR_OWNER_READY for short RU over Wi-Fi**, with the network,
live cancellation/retry and long-recording limitations above retained.
