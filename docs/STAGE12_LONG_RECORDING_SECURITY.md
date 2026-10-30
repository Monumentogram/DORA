# Stage 12 security and 30-minute owner Alpha

Owner scope: 2026-10-30. Successor to b310283 / Preview7 / PR126. Implementation and execution receipts are separate. No production AWS change or personal-phone installation is authorized by this document.

## Security requirement matrix

| Requirement | Baseline / remaining work | Verification | PASS criterion / current disposition |
|---|---|---|---|
| Encrypted audio | Existing AEAD per-run keys, authenticated source identity, bounded5s units; preserve format | Real SQLCipher/Keystore runtime, corrupt/truncated input and long reopen tests | Exact source hash and no returned unauthenticated samples; execution receipt required |
| Transcripts and user edits | Existing encrypted append-only transcript journal; no migration | Local/Cloud failure recovery, version tests, v7→v8 fixture | Edits/history/selection survive retry and update |
| Keystore/key loss | Existing non-exportable UID keys; never replace missing key over existing ciphertext | AndroidVaultKeyTest/RunKeyProductFailureTest and upgrade proof signature | Fail closed, preserve bytes, no regeneration that destroys access |
| Backup and temporary files | Backup/device transfer excluded; app-private encrypted audio; no new plaintext temporary files | Manifest/XML and artifact audit; failed extraction zeroization | No app-managed plaintext recording/transcript backup or temp |
| Logs and secrets | Fixed error categories; no new content diagnostics; no AWS secrets/model weights in APK | Source/DEX/manifest audit, pinned build inputs, SBOM | No content or credentials in public evidence/APK |
| Local deletion | Existing explicit tombstone/cancel/scoped delete and retry | Encrypted deletion and concurrent/reopen tests | No resurrection; partial failure visible; unrelated recordings remain |
| Update continuity | Same Preview package and owner certificate; versionCode8 | v7→v8 replace without uninstall, reboot/offline, key proof | Audio/settings/edits/model remain readable; physical Samsung separate |
| Backend authorization/TLS | Proof-bound HTTPS, owner/installation/source/consent checks, finite sessions | Real crypto/HTTP/SQLite synthetic backend tests and emulator HTTPS fixture | Tamper/replay/cross-owner/revocation fail closed |
| S3/KMS/Transcribe | Existing private input/output and separate regional CMKs; provider adapter unchanged | Adapter stub tests, configuration preflight; current deployment verification absent | Software tests do not attest current IAM/KMS/S3. Live evidence BLOCKED by separate infrastructure authorization |
| Controlled Cloud deletion | Existing immediate safe ingestion cleanup; fixed first-byte24h deadline; persisted watchdog outbox | Cancellation/disconnection/expiry/revocation/deadline/provider-error tests | Scoped job/object cleanup acknowledged; issue125 remains OPEN for current deployment |
| Metadata/audit/backup | Apply ADR0012 with later ADR0025/0029 exceptions | Controlled clocks and actual inventory/restore review | Live inventory/backup/restore evidence remains OPEN; no fabricated erasure guarantee |
| Supply chain | Strict dependency verification, existing native payload/provenance, owner SBOM | Build/DEX/native16KB audit; current vulnerability disposition | No new native code; final artifact audit required; broad security sign-off not inferred |
| Signed threat model/privacy approval | Existing bounded threat records; required actual-scope approval not supplied | Review canonical privacy/control gates | BLOCKED_EXTERNAL_APPROVAL; this report is not a legal/privacy signature |

Full Stage12 status is PARTIAL until every mandatory criterion has evidence. Owner-only Alpha distribution is a separate gate; this is not admission for public/external service use.

## Cloud data lifecycle and issue125

1. Android original audio and transcript remain encrypted locally until explicit user action. Exported SAF copies are user-controlled and outside application deletion.
2. Upload chunks live encrypted in backend custody. The first accepted byte fixes the24h deadline; retry, lost acknowledgement or disconnected client cannot extend it. Revocation fences future chunks and publication.
3. Customer S3 input, transient Transcribe output and terminal job are cleaned after durable ingestion. Failed/running cleanup remains pending and is retried by the independent watchdog. S3 lifecycle is a backstop, not evidence of timely explicit deletion.
4. ADR0025 permits durable backend jobs/results30d. ADR0029 retains trusted registrations (including revoked identity records) and active owner source bindings. These accepted exceptions must not be silently deleted under a generic30d rule. Consent history is90d after retirement; unresolved callback/identity tombstones do not auto-expire.
5. ADR0012 excludes audio backup/archive/cross-region replicas. Approved non-audio backup maximum age30d, restore-before-serve suppression and guarded minimum120d tombstones require actual inventory/restore evidence. This task creates no backup or restore workflow.
6. Ordinary metadata/audit policies do not imply physical erasure from AWS internal systems. CloudTrail management history and provider records have separately disclosed service limits; customer Delete API receipts only prove their stated scope.

To close [issue125](https://github.com/Monumentogram/DORA/issues/125), an authorized synthetic current-deployment exercise must reconcile exact jobs, input/output objects and versions, multipart uploads, encrypted custody, watchdog timing, logs and any backups. Existing user artifacts and running infrastructure must remain untouched. Local deletion is never presented as an AWS erasure receipt.

## Thirty-minute implementation decision

The original300s cap was an owner Alpha scope restriction (ADR-RECORDING-011), not an Android microphone duration limit. It also bounded whole-file PCM allocations in Local ASR/playback and the Cloud API. This authorized successor raises capture to28,800,000 frames (1800s at16kHz mono), retains the256,000-frame outstanding budget and five-second encrypted writes, and exercises existing ten-minute physical rotation. It introduces no storage schema or key change.

Local Whisper remains unchanged, including its300s native input limit. The client authenticates the complete source while retaining only a requested five-minute window, decodes outside the vault callback, concatenates in source order and publishes only after all windows succeed. Failure/cancellation preserves source and existing edited versions. Window boundaries may affect recognition context; no new word-error-rate or timestamp accuracy claim is made. Long text is still subject to the existing bounded transcript format.

Playback uses a single AudioTrack and one prefetched window. At most two five-minute float windows are owned, independent of total duration. Every load verifies the complete source, a deliberate I/O tradeoff for compatibility. Keystore/disk speed can delay initial playback/recognition; full physical throughput and battery qualification remain pending. Capture itself never retains the whole meeting.

The automatic thirty-minute stop keeps existing native-release, drain and durable-finalize checks. Saved limit completion is visible in the recording UI and a completion notification (subject to Android notification permission). Storage/capture failure remains interrupted with the committed prefix recoverable; uncommitted tail is not claimed saved. No boot-time automatic microphone restart is added.

Cloud Android remains explicitly limited to five minutes. Candidate backend validation accepts up to30m /57,600,044 WAV bytes, but it is not deployed and the existing daily reserve is unchanged. At the default0.25 USD reserve, a30m request cannot dispatch (estimated reserve0.91 USD under the default rate); a future deployment needs explicit cost authorization, bounded Android upload and long result qualification. Neither schema tests nor provider quotas grant LONG_RECORDING_ALPHA_READY for Cloud.

AWS sources checked2026-10-30: the [quota table](https://docs.aws.amazon.com/general/latest/gr/transcribe.html) lists standard batch28,800s and2GB, while the [FAQ](https://aws.amazon.com/transcribe/faqs/) still states4h/2GB. Both exceed30m; the operative bottleneck here is DORA's deployed protocol/budget. The pinned native wrapper's300s input limit is verified from local C++ source; upstream [whisper.cpp](https://github.com/ggml-org/whisper.cpp) capabilities do not change that contract.

## Incident, key loss and deletion runbook

- Local key failure: stop affected access, retain ciphertext and metadata, verify package/user/signature identity. Do not clear data, reinstall, regenerate keys over old files or promise backup restoration.
- Update failure: retain installed APK/data, verify candidate signer/package/version, stop if Android asks to uninstall. A rollback is not a recovery mechanism.
- Suspected Cloud leak: suspend new manual submissions, retain content-free action identifiers privately, reconcile the scoped cancellation and watchdog outbox. Verify actual job/object absence before reporting cleanup; pending/error is not erasure.
- Key rotation: preserve required old decryption capability and exact custody metadata, test restoration and failure on synthetic copies, then obtain separate production approval. Disabling a shared KMS key is not per-record deletion.
- Recovery: reopen authenticated durable prefix; inspect source availability and user-visible interrupted state; resume only through explicit authorized action. Never auto-start the microphone after process death.

## Qualification and next stage

Executed counts, maximum duration and artifact/CI identifiers belong in the final evidence receipt, not inferred from this design. Samsung Android16 remains PENDING. Historical Stage8.6 NOT_READY/LONG02 root cause is not relabelled by these tests.

Next: approve/test a bounded long Cloud upload and cost profile on an isolated backend; close current-deployment issue125 evidence and privacy/control approvals; then physical Samsung30m screen-off, recovery and owner-signed upgrade acceptance. Extend to60–120m only after measured persistence, memory, thermal and ASR evidence.
