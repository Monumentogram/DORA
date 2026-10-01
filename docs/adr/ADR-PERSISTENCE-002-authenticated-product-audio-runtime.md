# ADR-PERSISTENCE-002: Authenticated product audio runtime

Status: Stage 8.2 implementation decision, **PENDING_FINAL_PUBLICATION** and independent review. No later stage or release acceptance is implied.

## Decision

`DoraApplication.onCreate` installs `AndroidProductAudioRuntime` before Activity resume. Its constructor has only an Application argument and always composes the reviewed internal Android App Lock, Keystore vault bootstrap, admitted SQLCipher Room journal and accepted Recovery bridge. No test delegate, plaintext fallback, caller-created authorization, raw path/key or Recovery capability is public. The empty bootstrap UI remains; no microphone permission, capture UI, account or Cloud dependency is added.

`requestOpen(Activity, CREATE_NEW | OPEN_EXISTING, completion)` launches actual Android authentication. A failed or cancelled prompt never calls storage. The mode is explicit and immutable; failed existing-open never creates a vault or replacement keys. Successful authentication captures exactly one generation-bound Authorization. It is retained by the open attempt and every resulting handle. The Android callback checks current authority again immediately before publishing a handle to its caller.

Public availability is independent of source completeness: Locked, Opening, Available(session), or a normalized typed failure. The pure product session contains synchronous writer/reader/source-state/retry ports and actual wrapping-key protection. Its disclosure scope is explicitly “Vault wrapping key protection”; SOFTWARE is described as software-backed Android Keystore, with no hardware claim or new acknowledgement gate.

## Thread and lifetime boundary

One serial worker owns vault opening, all operations and cleanup. Synchronous product ports require background callers and return BUSY on main-thread access or worker callback reentry. Each queued operation checks its captured authorization before execution and again before its final result. Plaintext delivery uses the same authorization monitor as revocation for the complete borrowed callback. Callbacks must be bounded, cannot synchronously wait for main-thread work, cannot retain/post borrowed arrays, and cannot persist plaintext. A late extraction failure invalidates the entire attempt, including earlier units.

Pause, Home, screen-off and explicit lock revoke immediately and publish Locked. Closing resources is queued behind active operations. An open that finishes after revocation closes its result without publishing Available. New authentication never revives an old handle. Failed close retains the handle for retry before a subsequent open; the runtime will not open another vault while cleanup remains unresolved. Process replacement starts with no authorization or private confirmation state.

## Source status and deletion

Only `requestAudioDeletion(Activity, session, exact AudioIdentity, completion)` can start a product deletion. It presents the unchanged English COPY-AUDIO-DELETE plus explicit external-copy/Cloud and dismissal limits. The dialog sets FLAG_SECURE before presentation and the host has a neutral DORA task description. Its private confirmation binds the exact identity and captured session, is consumed once, and is cancelled on lock or pre-confirmation dismissal. Ordinary audio-only deletion does not add a fresh-authentication requirement; it requires a currently live session.

The existing durable tombstone, frozen inventory, ordered key/file removal and verified completion protocol remains unchanged. Closing the dialog after acceptance does not cancel an operation. Revocation can stop remaining work; durable steps are retained. Public retry calls an existing-only path and cannot invoke beginDeletion or create a fresh deletion scope. No result/export prerequisite, TTL, whole-conversation deletion or overwrite guarantee is introduced.

A narrow read-only Room source query requires the matching live vault lease and exact owner/vault/recording/asset/session relationship. Uncertain or currently uncompleted transactions cannot yield a source state. USER_DELETED requires a completed tombstone, all target steps completed and verified removal of canonical source records. A pending tombstone yields Deleting with category-only remaining work. Null catalog lookup never means user deletion. Catalog presence alone does not claim readable audio: the vault authenticates the actual source through the accepted reader. Reader failure and explicit source-state queries preserve the distinction between deletion, missing, corrupt, incomplete and key failures. Private source links and identity fencing remain retained.

## Validation and limits

Task 4's report records deterministic lifecycle/confirmation tests, real synthetic credential-to-vault tests, source-query transaction/identity checks, build/lint evidence and limitations. Accepted Recovery sources and schema are unchanged. These tests do not claim physical-device support, positive API29 legacy biometric enrollment, flash erasure, root resistance, future capture behavior, or accessibility certification. Final Stage 8.2 admission remains the separate parent gate.
