# DORA Security & Identity Architecture v0.1

PASS / SECURITY_IDENTITY_ARCHITECTURE_FROZEN_FOR_STAGE8

Contract acceptance requires independent review, deterministic preflight, exact-SHA mandatory CI and Sheet read-back; no self-certification of this commit.

## Machine-checked invariants

| Invariant | Frozen value |
| --- | --- |
| `privileged_secrets_in_apk` | `false` |
| `google_password_storage` | `false` |
| `original_audio_encryption_required` | `true` |
| `database_encryption` | `"SQLCIPHER"` |
| `local_keys_keystore_bound` | `true` |
| `provider_credentials_location` | `"SERVER_ONLY"` |
| `offline_requires_cloud_identity` | `false` |
| `offline_requires_network` | `false` |
| `offline_requires_gms` | `false` |
| `cloud_identity` | `"INVITED_INSTALLATION_PROOF_OF_KEY"` |
| `google_sign_in` | `"NOT_SELECTED"` |
| `stage8` | `"NOT_STARTED"` |
| `runtime` | `"NOT_IMPLEMENTED"` |
| `recovery_prerequisite` | `"SATISFIED"` |
| `group_c` | `"IN_PROGRESS"` |
| `internal_sensitive_content_plaintext_persistence` | `false` |
| `plaintext_export_exception` | `"EXPLICIT_DEC017_BOUNDED_TEMP_ONLY"` |
| `request_proof` | `"RFC9421_ECDSA_P256_SHA256_RFC9530_SHA256"` |
| `backup_sensitive_data` | `false` |
| `production_certification` | `false` |
| `real_credentials_accessed` | `false` |
| `real_auth_executed` | `false` |
| `real_audio_used` | `false` |
| `provider_called` | `false` |
| `new_signed_release` | `false` |
| `app_lock` | `"OPTIONAL_BIOMETRIC_PROMPT_DEVICE_CREDENTIAL"` |
| `app_lock_grace_seconds` | `0` |
| `hardware_keystore` | `"PREFERRED_SOFTWARE_KEYSTORE_ALLOWED"` |
| `strongbox_required` | `false` |
| `access_ttl_seconds` | `300` |
| `refresh_idle_seconds` | `604800` |
| `session_absolute_seconds` | `2592000` |
| `proof_nonce_ttl_seconds` | `60` |
| `application_id` | `"com.monumentogram.dora"` |
| `version_name` | `"0.1.0-alpha.2"` |
| `version_code` | `4` |

## Actors

- Local DORA user
- Android app
- Android Keystore
- DORA backend/auth
- Google identity provider (unselected future boundary only)
- Cloud provider/service
- Local Recovery subsystem

## Secret-location matrix

| Type | Android | Server/custody | Logs | Rotation/revocation |
| --- | --- | --- | --- | --- |
| Local KEK | Non-exportable Android Keystore AES-256-GCM key | PROHIBITED | PROHIBITED | Versioned rotation; delete only after verified rewrap or explicit whole-vault reset |
| Audio/manifest/checkpoint keysets | Independent random keys; only authenticated Keystore-wrapped envelopes on disk; bounded plaintext memory during use | PROHIBITED | PROHIBITED | Fresh per object/physical segment; retain old wrapping generation until recovery-safe migration verified |
| SQLCipher passphrase | Random 256-bit value; wrapped by local KEK; memory only while database open | PROHIBITED | PROHIBITED | Transactional verified rekey; never derived from user PIN |
| Installation proof private key | Non-exportable Keystore EC P-256 signing key, separate from encryption | Public key and binding only | PROHIBITED | Proof-authorized rotation or owner-assisted new enrollment; revoke old binding |
| DORA access credential | Memory only; opaque random 256-bit bearer value additionally bound to installation request proof | Credential hash and session/generation/expiry/scope | PROHIBITED | 300-second maximum; checked against live revocation before every protected action |
| DORA refresh credential | Dedicated Keystore AES-GCM wrapped app-private nonbackup blob | Hash, rotating family, replay history and current generation only | PROHIBITED | Single use; 7-day inactivity and 30-day absolute session expiry; reuse revokes family |
| Invitation/recovery capability | Memory only during enrollment; never evidence or clipboard logging | Hashed single-use capability; 10-minute expiry and attempt limit | PROHIBITED | Owner issues; consume atomically with verified proof; revoke unused invitations |
| Scoped upload authority | Memory only; no provider privileged key or unrestricted storage URL | Exact-source/job/bytes/operation/expiry/current epochs | PROHIBITED | At most access credential lifetime; server fences each bounded part/retry |
| Future Google ID token | Only if separately admitted, memory during supported client-to-backend exchange | Transient verification only; external issuer/subject mapping retained | PROHIBITED | No current Alpha flow; never used directly as DORA resource authorization |
| Provider/AWS/ASR/database/OAuth-client/service-account/backend-signing secrets | PROHIBITED | Managed secret manager and least-privilege runtime injection; workload roles for AWS | PROHIBITED | Security custodian rotates versions, invalidates compromised credentials and audits without values |
| Server encryption keys | PROHIBITED | Managed KMS with separated custodian and workload roles per ADR-0011 | PROHIBITED | Custodian rotation retains needed decrypt versions; shared-key disable is not record deletion |
| APK signing key | PROHIBITED | Not a backend runtime credential; existing owner-only offline signing custody per 7.1/7.3 | PROHIBITED | Existing accepted signing/backup process unchanged and not accessed |

## Scope, authority and implementation plan

This is one bounded architecture contract, in two equivalent representations. It freezes the security rules for Stage 8 engineers; it implements no product runtime, admits no dependency/version or production certification, and executes no real authentication or audio flow. Markdown is the deterministic rendering of the JSON. PASS means no unresolved architecture-level P0/P1 within this scope, conditional on the publication checks stated above.

Authority: Technical Plan §§14/24; DEC-015/017/038/044; ADR-0009/0011/0012/0013/0019/0020; current privacy v0.4; 7.2C/D/E; 7.3C and the accepted Recovery integration. The explicit 7.4 task authorizes the lifecycle/security details below. This is a prospective refinement of previously unselected mechanisms, not a retrospective rewrite of accepted evidence. ADR-0011's invited installation identity supersedes the earlier general OIDC recommendation; Google Sign-In is NOT SELECTED. Existing source-specific NOT_STARTED statements remain historical.

Execution plan (one gate, no new roadmap stages): freeze this pair; independently review it; add small invariant/mutation checks; preflight Stage00/release/Recovery/Cloud and all applicable deterministic CI commands; publish one atomic contract/tooling/status commit; verify exact-SHA full CI; update and read back the existing Sheet row. Exact final SHA/CI/Sheet evidence is recorded outside the self-referencing commit. A second docs-only evidence commit is allowed only if necessary. All prose, tests and evidence are English.

## Trust boundaries and data classes

Device: Android's app UID sandbox and credential-encrypted private storage separate DORA from ordinary apps. A device being unlocked does not authenticate a Cloud principal. Android Keystore holds wrapping and signing keys; DORA receives operations, not exportable private key bytes. Neither local profile IDs nor UI unlock establish server authority.

Backend: DORA alone verifies principal/session/proof, current ownership, consent, deletion epochs and operation policy. Provider adapters terminate provider formats and secrets. AWS/ASR provider infrastructure sees authorized plaintext for inference within accepted region/retention controls; no end-to-end encryption is claimed. An optional future identity provider proves an external identity, not ownership or recording consent.

Storage: authenticated envelopes/ciphertexts and encrypted SQLCipher pages form the persistent sensitive-data boundary. Filesystem paths, DB rows and client hashes are untrusted inputs until checked against authenticated objects and current authoritative state. The local Recovery subsystem operates inside this boundary; a recovery receipt is evidence of bounded integrity, never Cloud authorization.

| Data class | Classification and location |
| --- | --- |
| Original audio | Highly sensitive; authenticated encrypted app-private files; plaintext only bounded processing memory or explicit export/authorized Cloud transfer. |
| Transcript and user edits | Highly sensitive; SQLCipher including FTS, rollback/WAL and derived content; immutable version/edit semantics unchanged. |
| Account/installation identifiers | Pseudonymous confidential linkage; private encrypted DB; server binding for invited installations. No hardware ID, Google name/email or account enrollment for local operation. |
| Auth tokens | Secrets; access in memory, refresh in dedicated Keystore-wrapped blob. |
| Encryption keys | Secrets; non-exportable KEKs/proof keys; wrapped DEKs/keysets/passphrase on disk. |
| Recovery metadata | Sensitive relationship/timing/hash metadata in encrypted product DB and authenticated manifests/checkpoints; strictly bounded plaintext framing below. |
| Telemetry/logs | Content-free operational metadata only; optional crash/analytics reporting OFF by default. |

## A/B — DORA identity and the Google boundary

A local DORA user is a device-local vault profile with a cryptographically random 128-bit local_user_id. Alpha has one local profile per installation; it is not a verified natural person or a Google account. installation_id is a separate random 128-bit public identifier, never Android ID, IMEI, advertising ID or authentication evidence. Reinstall creates both anew. RecordingSessionId (capture session), RecordingId, AudioAssetId/source version and DORA processing action/job IDs retain the existing 7.2 contracts and are distinct from auth_session_id and the ephemeral app-unlock session.

For Cloud, the backend issues a stable internal principal_id of kind INSTALLATION after invited enrollment plus private-key possession proof. That is the Alpha internal DORA ownership identity; no new USER/account principal replaces ADR-0019. The device key binding and auth_session_id refer to that principal. Local recordings remain bound to local_user_id. A later explicit authorized Cloud registration creates the server-owned exact principal/RecordingId/AudioAssetId/version/digest relationship. Possession of a client resource ID or digest does not prove ownership; conflicting pre-existing bindings are denied, never reassigned. Cloud jobs derive their owner/source from the verified recording binding and prior CREATE_CLOUD_JOB decision; provider IDs are provenance only. Local-only jobs use the local profile and never pretend to be server-authorized.

Local flow: local profile → Keystore/encrypted vault → explicit recording → authenticated ciphertext/checkpoint → local processing/history. No account, invitation, network, GMS, Cloud session or auth refresh is required. Offline expiration/revocation blocks Cloud work only; queued work gains no grant on reconnect.

Google Sign-In is NOT SELECTED for current Alpha because ADR-0011 and ADR-0019 already select installation proof-of-key. Password/account-change at Google is therefore not a current DORA lifecycle event. If a later owner decision admits Google: use the supported Android Credential Manager Google flow; send the ID token over TLS to DORA; backend library verifies signature/issuer/audience/expiry and request nonce; map issuer+subject to internal DORA identity (never trust a client user ID/email); establish a DORA session; separately authorize ownership/consent. No Google password, privileged OAuth secret or Cloud provider credential enters DORA Android. Linking/transfer requires separate explicit proof and cannot inherit old installation rights. This conditional boundary does not implement or admit that future flow.

## C — Session, credential and replay lifecycle

Cloud login/enrollment: the owner issues a single-use invitation, expires in 10 minutes, maximum five failed submissions before invalidation. Android generates a separate non-exportable EC P-256 installation key and signs a backend challenge bound to the invitation, public key, installation_id and requested audience. Server atomically consumes invitation/challenge and creates principal, device binding, auth session and credential family. Public identifiers alone never enroll. No account registration is silently coupled to local recording.

Use opaque CSPRNG 256-bit access and refresh values; store only hashes server-side. Access lifetime is at most 300 seconds. Refresh expires after seven days idle or the original session's 30-day absolute deadline, whichever comes first; refresh cannot extend that absolute deadline. Every successful refresh atomically replaces the refresh value and access generation; the old value is spent. Any replay of a spent refresh token revokes the family. Concurrent refresh is serialized by the client. Lost refresh response or uncertain commit requires explicit reauthentication; never replay the spent token or silently fork a session. Previous access generations are invalid immediately.

Each protected request, including refresh, uses server-issued single-use random 256-bit nonce (60-second TTL) and the standard RFC 9421 HTTP Message Signatures profile with ecdsa-p256-sha256, RFC 9530 Content-Digest sha-256 and tag dora-alpha-v1. Cover @method, @target-uri, content-digest, content-type, authorization when present, and singleton dora-session, dora-generation, dora-audience, dora-operation, dora-action and dora-attempt headers. Signature parameters include keyid (server-resolved binding), nonce, created, expires and tag; expires cannot exceed nonce expiry and created must fall within that server-issued nonce window. Reject duplicates, missing required covered components, unknown algorithms and ambiguous URI/header normalization. For enrollment, replace absent session/credential headers with covered dora-invitation and dora-installation; for own-session revocation require covered dora-session and operation plus active installation proof without processing credentials. The signed Content-Digest must match actual received bytes before the bounded part becomes accepted; the temporary receive buffer conveys no processing authority. Use reviewed standard libraries and compatibility vectors, not bespoke signature serialization. Server recomputes all bindings and consumes the nonce atomically with the authorization/operation reservation. Upload proof additionally binds exact part offset/length/hash and source/job. Duplicate proof is denied; logical idempotency may retrieve the already committed outcome through a fresh authenticated request only for the identical operation/payload/source/configuration. Reusing an idempotency key with any changed payload or owner/source/configuration is rejected. TLS 1.2+ with normal server certificate validation is mandatory. No pinning bypass or custom certificate trust.

Server time controls expiry; client clock changes never extend authority. Every operation consults live principal/device/session status and credential generation plus the independent ownership/consent/deletion vector. Authorization and bounded part acceptance/dispatch are fenced against concurrent revocation. A cached ALLOW, self-contained unexpired credential or signed URL alone is insufficient. Server state unavailable means DENY/zero new bytes.

Reauthentication after ordinary expiry requires an active existing installation binding and fresh nonce/proof; it creates a new session without new ownership or consent. If the binding is revoked or key lost, require owner-assisted verification and a new invitation; no automatic rights transfer. Owner verifies retained nonsecret installation/consent evidence through the accepted rights route before any specific old-resource retrieval/deletion, without granting broad old ownership. No cross-device recovery or transfer is selected for Alpha.

Logout locally disables Cloud scheduling and clears access/refresh values after durably recording a content-free pending revocation action. Online, revoke the entire session family and fence ingress before confirming remote logout. Offline, show local logout complete / server revocation pending; reconnect may use only installation proof on the narrowly scoped own-session revoke endpoint, never a processing credential. Retry until receipt. Loss of device/key before that requires owner-side removal; local clearing alone cannot prove remote revocation.

Lost/stolen device or credential compromise: owner disables installation binding, revokes every session family, fences new upload/dispatch/retries, and requests supported cancellation. Existing provider processing may finish; no remote phone wipe or unsending claim. Compromised backend credentials trigger server-side rotation, role/session invalidation and incident containment. A signing-key compromise disables that binding, not merely the access token. Removal is server revocation, not local data erasure. IdP password/account change in a future admitted Google flow requires provider-event verification/re-auth policy before use; no automatic immediate Google→DORA revocation is claimed.

## D/J — App lock, sensitive screens and capture

App lock is optional, initially OFF under the existing optional local-access model; enabling it requires successful system authentication. It protects app access, not Cloud authentication or cryptographic isolation from a compromised app process. No custom PIN/password storage or cryptography. Privacy previews are ON by default independently of app lock, adopting DEC-038's proposed scoped approach for this Alpha overlay.

When enabled, launch/process death/reboot/device lock/background transition → LOCKED; there is zero background grace. Successful Android BiometricPrompt authentication → UNLOCKED for this foreground app session only. Failed/cancelled/locked-out prompt leaves LOCKED; no cached success or persisted unlocked flag. Configuration changes alone may retain the in-memory foreground state; process death never does. Export, destructive whole-vault reset, disable-lock and Cloud identity removal require fresh system authentication while app lock is enabled.

Use BIOMETRIC_STRONG or system device credential. On API30+, supported combined authenticators may be used. API28/29 use supported biometric prompt plus a distinct platform device-credential confirmation fallback because the combined authenticator configuration is unsupported there. Biometrics unavailable/lockout → device credential; no device credential → explain setup. Never silently disable an enabled lock when enrollment/security settings change. If lock cannot authenticate, sensitive UI remains inaccessible; local encrypted artifacts retained, explicit system credential setup/recovery required. With lock OFF, no secure lock-screen requirement is added to local recording.

An already user-started recording can continue under its bounded service capability when UI locks; lock never authorizes a new microphone start. Locked notification exposes generic recording state/stop control only, no titles/transcripts/account identifiers; stop is a protective action. No playback/export/content-revealing deep link bypass. After reboot/relaunch recover encrypted state after Android's first user unlock; never auto-start microphone. Close unneeded readers/DB handles and clear UI/plaintext caches on lock; an active writer retains only bounded needed buffers/keys until finalize. App lock is deliberately not a per-use authentication requirement on recording KEKs, avoiding broken screen-off writes.

Sensitive recording/history/search/transcript/edit/export-preview/account/security screens set FLAG_SECURE before rendering and retain it through transitions; screenshots, recording and nonsecure casting of these windows are prohibited. Recent Apps shows a neutral DORA cover, no content. Ordinary help/version/empty onboarding screens permit screenshots. The preview setting may control non-sensitive previews only; it cannot bypass sensitive FLAG_SECURE. Explicit export remains the supported content-sharing route. Do not claim universal OEM capture prevention or protection from cameras/root/accessibility abuse; platform behavior needs runtime tests.

## E — Keystore and key lifecycle

Create versioned non-exportable AES-256-GCM KEKs in Android Keystore, with provider-generated nonces and full authentication tags. Separate aliases for local vault envelopes and session-token wrapping; separate EC P-256 proof-of-key alias. Require Keystore successful round-trip key confirmation before persistent sensitive writes. Storage lives in credential-encrypted app-private no-backup directories, never device-protected/direct-boot storage. Master keys are never derived from app PIN, login password, account ID or installation ID.

Prefer available hardware-backed protection; inspect actual KeyInfo and disclose software-backed fallback. StrongBox is preferred where supported but neither required nor claimed available. StrongBox failure may fall back to regular hardware/software Android Keystore only after capability check and key-confirmation success; absence/failure of Android Keystore fails closed. Never fall back to a hardcoded key, plaintext keyset/file or exported raw KEK. No attestation or rooted-device defense is inferred from local KeyInfo.

Local data KEKs are not per-use biometric-auth-bound, preserving screen-off capture and local offline access; app lock is a separate UI gate. Adding/removing biometrics must not deliberately erase the audio key. Unexpected permanent invalidation, missing alias, reset or corruption yields KEY_UNAVAILABLE/KEY_INVALIDATED and retained ciphertext, never silent replacement or deletion. Transient Keystore unavailability pauses new sensitive operations; retry after system unlock/re-auth as applicable, without treating unavailable keys as bad audio.

Lifecycle: create KEK and independent random DEK/keyset/passphrase → authenticated wrap with version/purpose/vault/object AAD → persist and verify confirmation/envelope → use in bounded memory → rotate on compromise, algorithm/policy change or explicit maintenance → verified rewrap and atomic generation switch → retire old KEK only after all live, pending, quarantined and recoverable objects are inventoried. Never change keys mid-object. DB rekey requires a recoverable transactional protocol and verified reopen before old passphrase retirement; power-loss tests are runtime acceptance. Compromise may require re-encryption, not merely wrapping the same exposed DEK again.

Backup/device transfer excludes DB, WAL/SHM, audio, key envelopes, proof bindings, tokens, Recovery artifacts and private model metadata in both legacy and current Android backup/transfer rules. Platform exclusions must be tested; no automatic migration or encrypted-bundle recovery feature is admitted. Uninstall, clear-data, reinstall, factory reset or hardware failure can irreversibly lose keys. New installation cannot decrypt previous copied ciphertext and cannot inherit Cloud rights. Explain this before destructive reset; retain readable data until explicit deletion/export choice. Never synchronize local wrapped keys to a server.

## F — Local encryption and persistence boundary

Original audio must always be authenticated encrypted at rest, using the accepted public Tink constructions and exact Recovery profile parameters. Each physical AudioSegment has a fresh keyset, independent of the SQLCipher key and manifest/checkpoint keysets. Reuse accepted STREAM durable one-segment lookahead or sealed five-second MICROFILE semantics only through the later admitted product adapter; 7.4 does not select or modify that runtime adapter. Never invent a new cipher/nonce/segment format or reinterpret a generic Tink decrypt prefix as accepted Recovery evidence.

Product Room database must use SQLCipher with independently random 256-bit passphrase wrapped by Keystore; full protection covers transcript, edits, FTS, local ownership/consent/deletion data and sensitive Recovery journal relationships. Plain SQLite plus the Android sandbox is not equivalent. WAL/rollback/temp files must remain encrypted; configure memory-only temporary work and avoid plaintext debug dumps/exports. SQLCipher version/native ABI/license/migration admission remains Stage 8 implementation evidence, no dependency added now.

Authenticate format version, vault/local owner, capture session, physical segment/object identity and data role in canonical AAD for newly designed product envelopes. Preserve exact immutable PoC AAD when reading accepted objects; bind extra product identity in an outer authenticated mapping, never silently alter old bytes. Cross-role/source/vault substitutions must fail. AEAD nonces/stream internals are library-controlled; never manually reuse a nonce/key pair. No deterministic key from content/hash/name.

Plaintext lifetime: only bounded capture, decrypt, decoder/ASR and explicit transfer buffers; clear/release when no longer needed and avoid copies/dumps. Managed-runtime/native memory zeroization is best effort, not guaranteed. No persisted plaintext capture/conversion/recovery/cache files. Authorized export is the single explicit local plaintext-file exception, described below; Cloud uses a bounded authenticated read→TLS stream, not a disk staging copy.

Durability order preserves accepted Recovery: reserve immutable identifiers and journal intent; persist wrapped key material plus verified key confirmation before ciphertext can be declared recoverable; write ciphertext, flush/fsync authenticated data/checkpoints and atomic publication; commit/read back consistent encrypted DB state before announcing saved/enqueueing. Filesystem and DB are not magically one transaction: interrupted phases reconcile via journal, authenticated manifests/checkpoints and tombstones. Do not mark truncated/unverified data complete or overwrite an occupied generation. The exact product writer/storage route remains Stage 8. Crash tests must prove this boundary before recording/storage runtime acceptance.

Export follows accepted DEC-017 and the export contract: explicit selected snapshot, warning for every unencrypted transfer, separate audio opt-in (default OFF), narrow temporary URI grants, bounded app-private export-temp cleanup and retry per that contract. Never copy secret/key material; encrypted bundle remains deferred. Recipient copies/clipboard are outside DORA control after handoff. Audio-only deletion preserves transcripts/edits; no filesystem-overwrite or automatic per-record cryptographic-erasure claim.

## G — Recovery compatibility

Reuse accepted Recovery evidence and unchanged implementation. Product storage must compose encryption around journal/checkpoint ownership without weakening AEAD, key confirmation, exact AAD, generation chains, same-descriptor source witnesses, run leases, collision/no-overwrite, disposition or quarantined-range checks. Recovery needs ciphertext, wrapped keys and authenticated metadata, not plaintext persistence.

Plaintext on disk is limited to a minimal bootstrap/framing allowlist: format/schema/key-version tags, algorithm identifiers, random opaque filenames/object selectors, envelope/AEAD public nonce/header/tag bytes, file lengths and ciphertext-only integrity digests where necessary to find/authenticate objects before DB open. No names, recording titles, transcript, user edits, auth credentials, plaintext audio hashes, meaningful timestamps or Cloud account linkage in that framing. File sizes/existence/access patterns and random-ID correlation remain residual metadata leakage. Raw paths/hashes are not automatically loggable.

Product run-to-recording mappings, ranges, checkpoints, quarantine reasons, content/source digests, consent and deletion state live encrypted; authenticated checkpoint/manifest objects remain authenticated ciphertext. The accepted PoC's plaintext SQLite metadata is historical synthetic scope only and is not a production persistence template. Preserve its test/schema/hash evidence byte-for-byte; wrapping or an encrypted product journal is Stage 8 work.

Crash during encrypted write: validate key confirmation first; distinguish KEY_UNAVAILABLE from corrupt payload; authenticate exact proven prefix/whole-object and preserve accepted partial/rollback outcomes. Never infer complete audio from DB state alone, rename arbitrary files over a valid generation, bypass ACTIVE quarantine ranges, decrypt untrusted remainder or retry with guessed/recreated keys. Quarantine retains encrypted artifact, necessary envelope and minimal encrypted provenance under the same explicit local-retention/delete policy. Do not drop keys solely to tidy quarantine. Explicit scoped deletion may destroy those artifacts; no silent deletion by watchdog.

K12 consumer/retirement and API28–32 product adaptation remain bounded Stage 8 obligations, not completed by historical Recovery integration. No new device campaign or Recovery behavior change occurs in 7.4.

## H/I — Server secrets and logging

Privileged provider/API/AWS/Cloud-ASR/database/OAuth confidential-client/service-account/backend-signing credentials and server encryption secrets exist only in the server custody boundary; mobile client identifiers/public verification keys are not privileged secrets. Use managed Secret Manager/runtime injection, AWS workload roles and KMS per accepted custody; least privilege per ingress/control/worker/ingester role. No developer/prod values are needed for this task. APK signing remains separate owner-only custody.

No privileged secret in APK, Git, evidence, CI output, app/server logs, analytics or crash dumps. Secret configuration missing/invalid means dependent Cloud operation fails closed; no baked-in default, environment fallback to another account/region, or client-provider direct call. Rotation uses managed versions and bounded overlap only when still authorized; compromise immediately fences affected roles/sessions. Key disable is not proof of provider data deletion.

Logging is allowlist-based at the producer: random per-operation correlation ID, component, normalized reason/status, duration bucket and aggregate counters. Do not log authorization/cookie headers, access/refresh/ID tokens, invitation/proof payloads, passwords, raw keys/secrets, signed URLs, original audio, transcript/user edits, names/emails, request/response bodies, content hashes, file paths or full auth claims. No stable installation/account IDs in client telemetry. Server security audit may store a scoped pseudonymous principal/session reference in restricted access-controlled audit storage under the existing retention policy; never raw identity payload.

Redact before formatting/serialization at source and at network/crash-report boundaries; never rely on regex after forwarding. Exceptions must use sanitized codes, not SDK response dumps. Correlation IDs are random, nonsecret and never accepted as capabilities. Crash/analytics reporting OFF by default; explicit diagnostic consent does not authorize content/token collection. Later runtime tests must seed synthetic canaries and assert absence from logs, telemetry, traces and errors.

## K — Logout, unlink, deletion and key loss

Logout revokes/clears the Cloud session under section C; it keeps local encrypted recordings, transcripts and local_user_id. It does not delete audio, revoke recording consent implicitly, erase server data or remove the vault KEK. Local account/session reset without explicit data deletion must first use the same durable family-revocation protocol as logout, disable Cloud scheduling, and preserve the installation proof key, session identifiers and revocation outbox until server receipt or independently verified owner resolution. It then clears reusable access/refresh credentials and locks sensitive UI if enabled; old remote resources stay under the old principal. No silent assignment to a newly enrolled identity.

Cloud unlink/removal is explicit installation-binding/session revocation and prevention of further Cloud scheduling. An account-unlink UI is not introduced while accounts are unselected. Owner-assisted independently verified retrieval/deletion rights remain available after ASR consent revocation; a revoked ASR grant never authorizes processing or normal 7.2E result reads. The separately scoped rights route verifies identity/ownership and retrieval/delete permission without silently changing the frozen READ_RESULT contract.

Local audio deletion: transactional durable tombstone/fence → stop affected reads/jobs/uploads → delete audio/key envelopes/caches under explicit scope → reconcile retries and report partial failures; preserve transcript versions/edits and remote deletion outbox. Whole-recording deletion additionally removes transcript/FTS/derivatives; whole-vault reset explicitly confirms irreversible loss and preserves/settles separately scoped remote deletion tracking before removing local keys. Server deletion is separately authorized and remains pending until scoped receipt. Offline reset must warn that unresolved remote deletion needs the owner rights route, not falsely label it complete.

Destroying the only Keystore KEK makes every dependent wrapped key/ciphertext irrecoverable, including retained/quarantined artifacts. It does not erase provider copies, other recipient exports or independent keys. Deleting one audio envelope is best-effort logical deletion; recoverable flash copies of that envelope plus a live shared KEK defeat any claimed per-record crypto-erasure. No physical overwrite guarantee on flash. Tombstones/late-result fences prevent resurrection; keep required minimal deletion/consent evidence for already accepted retention periods. Automatic local retention remains OFF and original audio remains until explicit scoped deletion.

## Security failure behavior

| Failure | Required result |
| --- | --- |
| Missing/invalid/expired auth, replay, revoked session or device | DENY Cloud operation; zero new bytes/cost dispatch; fresh proof/re-auth as allowed; local core continues. |
| Ownership/source/job mismatch, missing consent/scope or stale epochs | DENY; never substitute another owner/source or accept client assertions; protective authorized cancellation/delete rights remain separate. |
| Keystore unavailable or key invalidation | Stop new sensitive writes/reads; retain ciphertext; distinguish retryable availability from permanent loss; no plaintext/fresh-key fallback. |
| Corrupted encrypted object/checkpoint | No unauthenticated plaintext release; accepted bounded Recovery prefix/quarantine rules only; truthful partial/unavailable state. |
| Missing server secret/configuration or unavailable authoritative ledger | Dependent Cloud work fails closed; no alternate provider/region or APK credential fallback. |
| App-lock cancel/failure/unavailable credential while enabled | Remain locked; active bounded recording may continue safely; no content through recents/notification/deep links. |
| Interrupted deletion/rekey/write | Reconcile durable intent and generations; retain required old keys until verified retirement; never claim completion without evidence. |

## L — Alpha threat model and residual risks

| Threat | Boundary/control | Residual risk |
| --- | --- | --- |
| Stolen unlocked phone | Optional app lock, fresh confirmation for sensitive actions when enabled, scoped secure screens and rapid server removal | Current foreground unlock or lock disabled exposes user capabilities; malicious holder may operate app. |
| Stolen locked phone | Android credential-encrypted storage, non-exportable KEKs, no backup/transfer, lock on re-entry | Device credential compromise, platform vulnerabilities, software Keystore and hardware forensics remain outside guarantees. |
| Malicious app without root | UID sandbox, restricted content URI grants, secure screens, no exported plaintext | Accessibility/overlay/social engineering and user-authorized exports can disclose content; runtime IPC review required. |
| Rooted/compromised OS | Limit persisted secrets and server authority; separate role/session revocation | May capture plaintext, invoke keys or falsify UI; no protection claim or attestation substitute. |
| Leaked APK | No privileged secrets; public IDs alone grant no authority | Reverse engineering reveals protocols; server must enforce every authorization. |
| Leaked logs | Producer allowlist, no content/credentials; restricted audit | Pseudonymous timing/correlation leakage; operational access/retention controls required. |
| Backend credential compromise | Least-privilege roles, managed rotation/KMS, live revocation | Authorized plaintext/job data may be exposed within compromised role; no E2EE claim. |
| Replayed auth credential | Request-bound one-use nonce/proof, finite access, rotating refresh, live epochs | Compromised signing-capable device remains dangerous until binding revoked. |
| Unauthorized recording/job access | Server-owned exact principal/source/job relationships and independent consent | Client validation alone is insufficient; later race/IDOR tests mandatory. |
| Exported/copied local storage | Ciphertext plus Keystore binding, authenticated object identity | Metadata leakage; copied envelopes may survive; intentional exported plaintext leaves DORA control. |
| Recovered deleted files | Tombstones, scoped key/envelope cleanup, no resurrection | Flash remnants and surviving shared keys; no per-record physical/crypto-erasure guarantee. |
| Crash during encrypted write | Key confirmation, authenticated checkpoints, durable intent, no-overwrite quarantine | Last uncommitted tail may be lost; full-plan Recovery/device acceptance not claimed. |

These are explicit Alpha residual limitations grounded in existing accepted scope. They do not waive runtime tests, expand Alpha support beyond accepted devices, or claim external security/privacy certification.

## Stage 8 admission and next product work

7.4 architecture admission requires reviewed frozen decisions for identity, app lock, Keystore, encrypted persistence, sessions, server secrets, redaction and Recovery compatibility; the invariant validator checks those declarations. No unresolved P0/P1 may remain. Architecture PASS is not runtime acceptance: Google Sign-In, app lock, product Keystore integration, encrypted audio/DB persistence and Cloud session implementation remain NOT_IMPLEMENTED.

Stage 8 = NOT_STARTED. Recovery integration prerequisite = SATISFIED. Group C = IN_PROGRESS. Main and product identity remain unchanged. No signing secrets, real credentials/auth/audio/provider calls, new release or physical device campaign are part of this gate.

Next real product task is a separately owner-authorized Stage 8 local recording/storage vertical slice. Its initial existing 8.1/ADR-AUDIO work must explicitly select the production storage/extraction reader/writer route, preserve accepted Recovery contracts, dispose API28–32 compatibility and K12 consumer/retirement limitations, and bind storage/dependency evidence to the actual product graph. These remain Stage 8 implementation/admission obligations, not new 7.4 sub-gates, and are not claimed READY here. No Cloud real-audio permission follows.

Before runtime acceptance, demonstrate offline/no-GMS local capture, SQLCipher/FTS/temp protection, encrypted crash recovery, Keystore unavailability/rotation/loss, lock/process/reboot behavior, secure previews/deep links, sanitized diagnostics, scoped export/delete and Cloud proof/replay/ownership/revocation races where Cloud is implemented. Use synthetic fixtures first; separate real-audio and provider admission remains mandatory.

## Accepted authority and platform references

- [Technical plan](../DORA_MVP1_TECHNICAL_PLAN.md), [Product decisions](../DORA_MVP1_PRODUCT_DECISIONS.md), [Alpha scope](../product/DORA_ALPHA_SCOPE_V0_1.md).
- [ADR-0019 identity](../adr/ADR-0019-cloud-identity-authorization-ownership.md), [7.2E logical contract](../stage1/DORA_ALPHA_CLOUD_IDENTITY_AUTH_OWNERSHIP_CONTRACT_V0_1.md), [ADR-0011 custody](../adr/ADR-0011-alpha-transcribe-privacy-retention-key-custody.md), [ADR-0009 Cloud boundary](../adr/ADR-0009-alpha-cloud-execution-boundary.md).
- [Privacy v0.4](../design/DORA_CLOUD_ALPHA_PRIVACY_RETENTION_CONTROL_V0_4.md), [storage/retention/delete](../design/DORA_MVP1_STORAGE_RETENTION_DELETE_CONTRACT.md), [Recovery reconciliation](../contracts/DORA_PR86_CLEAN_RECOVERY_INTEGRATION_V0_1.json), [ADR-0005](../adr/ADR-0005-poc-recovery-streaming-persistence-and-range-quarantine.md).
- Platform verification, 2026-10-01: [Android biometric authenticator compatibility](https://developer.android.com/identity/sign-in/biometric-auth), [Android Keystore](https://developer.android.com/privacy-and-security/keystore), [secure-screen behavior](https://developer.android.com/security/fraud-prevention/activities), [Google backend token validation](https://developers.google.com/identity/sign-in/android/backend-auth). Platform documentation informs implementation constraints; it does not supersede DORA owner decisions.
- Request-proof profile: [RFC 9421 HTTP Message Signatures](https://www.rfc-editor.org/rfc/rfc9421.html) and [RFC 9530 Digest Fields](https://www.rfc-editor.org/rfc/rfc9530.html); no bespoke cryptographic protocol.
