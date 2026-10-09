# LONG-02 fail-closed protection and backup plan

Status: **PLAN ONLY; device protection NOT extended; audio backup NOT CREATED;
authenticated PCM readback NOT_RUN.** Stage 8.6 remains NOT_READY. This document
does not authorize device access or lift the existing launch hold.

## Evidence and exact accounting

The baseline is commit `944b41532e3007ca14d1851d83c2232de1132874` and the
owner-private `LONG02-OFFLINE-20261009` evidence collection. This investigation
reads saved files only. All 19 copied evidence files are checked against their
saved byte counts and SHA-256 manifest. The private snapshot digest must match
both the saved inspection receipt and the existing private custody manifest.
These checks establish consistency of saved evidence, not current device state.

The new private companion `POCO-8.6C2-20261009/long02-custody-verification.private.json`
is an offline mapping, **not an installable policy or an audio backup**. It retains
exact source/owner/vault identities, every ordinal and run, canonical key URI,
derived physical Keystore alias, artifact role/path/digest and metadata byte count.
It must stay outside Git, PRs, CI logs and public artifacts. This public plan
deliberately carries no private identity, filename, alias or audio content.

| Saved evidence check | Required accounting |
| --- | ---: |
| Unique source selected by the failed-attempt identity fingerprint | 1 |
| Contiguous committed claim ordinals | 0 through 393 |
| Frames per claim; aggregate claimed frames | 80,000; 31,520,000 |
| Unique owned run namespaces / blocks | 394 |
| Owned physical-source rows, covering every claim | 4 |
| Key-confirmation artifacts | 394 |
| Manifest ciphertexts | 394 |
| Wrapped manifest keysets | 394 |
| Microfile ciphertexts | 394 |
| Wrapped microfile keysets | 394 |
| Total distinct source files | **1,970 = 394 x 5** |
| Derived physical run-key aliases present in saved key inventory | 394 |
| Saved key challenges for those LONG-02 aliases | **0** |
| Existing protected historical sources, preserved independently | 47 |

The verification joins `audio_asset` to `vault_binding`, checks ownership and the
exact failed source identity, matches all 394 claims to the four owned
`physical_source` rows, checks exclusive run ownership in `unit_claim`, and
joins each run to exactly one bootstrap, manifest and microfile row. It validates
ordinal/frame continuity, unfinalized state and absence of source deletion and
finalization records. Every one of the five artifact references per block must
resolve to exactly one saved newly-added file, with matching SHA-256 and no
duplicate, omitted or unattributed path. The saved before/after diagnostic file
inventories must be identical. The earlier saved comparison also retains 8,547
immutable historical files; the separately admitted shared DB/WAL/SHM lifecycle
exception must never become a general permission to alter historical state.

Key references follow two distinct source-code contracts:

1. `CanonicalRecoveryAlias` supplies the logical run-key URI; its hash must match
   the bootstrap `aliasHash`.
2. `NoLogRecoveryRunAeadBackend` derives the physical alias from the canonical
   authenticated vault ID and run ID. All 394 derived aliases must occur in the
   saved key inventory, with the expected saved AES-256 properties.

The saved snapshot contains **no key challenges for the 394 LONG-02 aliases**.
Its 1,710 verified historical key challenges must not be attributed to LONG-02.
The 1,970 artifact byte counts sum to 63,345,646 in the saved metadata; actual
audio-file bytes were not available to this offline check.

Saved alias properties, saved key challenges, catalog `VALID`/`committed` labels
and matching ciphertext hashes are **not authenticated PCM proof**. No key
challenge was executed or decrypted by this offline investigation. Metadata byte
counts are claims until compared with actual file sizes. A snapshot may be
internally consistent while ciphertext is undecryptable, keys are unavailable or
the current device differs.

## Hold and enforcement boundaries

Until separately authorized and verified, keep DORA stopped. Do not connect the
physical POCO, use ADB, launch DORA, install an APK, edit its policy, run saved
device scripts, resume recording, finalize, delete, reconcile, repair, migrate,
checkpoint the original database, quarantine or rename original artifacts, clear
data, uninstall, rotate keys or remove aliases. These actions are not part of
Stage 8.6C.2.

The existing `DiagnosticSourcePolicy` and `DiagnosticPolicyLoader` accept exactly
47 protected sources. `DiagnosticPolicyBinding` verifies a SHA-256 pin embedded
in the signed debug APK. LONG-02 is absent from that set. Appending a 48th JSON
entry fails the count contract and the installed APK digest pin; replacing one
historical source would remove its protection. Neither approach is admitted.

A future protection change must preserve every historical restriction and add
exact LONG-02 source, asset, recording, session, physical-source, run and component
namespaces plus their key references. No wildcard, UI selector, time window or
name heuristic may define ownership. Unknown or conflicting ownership fails
closed. The policy must be validated before any ordinary runtime or Room open
can cause reconciliation or migration. Missing, stale, truncated, mismatched,
removed or wrong-vault policy must deny startup rather than fall back to an
ordinary runtime. The existing no-policy/no-pin ordinary mode cannot be relied
on as a protection mechanism.

Protect the whole mutation graph, including indirect entry points:

- Startup recovery, pending intents, orphan cleanup, scheduling and service or
  receiver entry points must not mutate LONG-02 or its owned namespaces.
- Deny append/resume, finalization and original-reference publication, deletion
  intents/tombstones, retention cleanup, file quarantine/removal and key mutation
  before the first durable write or key operation.
- Preserve root-envelope and vault-key dependencies, wrapped keysets and all
  unrelated historical keys; preventing file deletion alone is insufficient.
- On any mismatch, operational failure or ambiguous commit, retain the source,
  keep the launch hold, emit a content-free failure and stop. No automatic repair,
  finalization, key recreation or retry through a mutating Recovery path.

Before physical admission, a synthetic vault must prove the expanded exact
protection, old 47-source preservation, wrong-owner/run denial, namespace collision
denial, missing/corrupt policy denial, restart and direct-entry fences, release
denial, and unchanged legitimate operations for unrelated new test sources.
Review the exact signed APK, policy pin and operator code as one admitted unit.
Preserve the package/signing/data/UID relationship that owns the retained keys;
an update must never depend on uninstalling the current application.

## Future backup procedure, separately authorized

1. Owner approves the protection contract/APK path and an isolated read-only
   acquisition entry point that cannot initialize the normal application runtime.
   Rehearse it on synthetic storage first. A normal launch to obtain the backup
   is forbidden. A failed precondition leaves the device untouched.
2. On the authorized original device, verify exact device/build/package/signature,
   APK and policy binding, user authentication and owner/vault binding. Authenticate
   the root envelope and existing key path without generating/replacing keys.
   Keep microphone, recording service and all source writers inactive. Inventory
   live sources, keys and files through the admitted read-only path, then compare
   with the private 394/1,970 mapping and all historical protections. New or missing
   files, an unexpected 395th block, duplicate ownership or hash drift stops the
   procedure for explicit adjudication; do not silently adjust the baseline.
3. Acquire encrypted artifacts into a new restricted local destination. Include
   all 1,970 files and the consistent supporting database/WAL state, root envelope,
   schema/binding and policy receipts needed to interpret them. Shared database
   artifacts require a quiescent, coherent capture; copying a changing DB/WAL/SHM
   set is not a valid backup. Use regular-file/no-follow checks, canonical paths
   and descriptor identity/size checks; reject links, path traversal and changes
   during acquisition. Do not checkpoint or repair the original to make copying
   easier. Never use move or source cleanup.
4. Record fresh source byte lengths and SHA-256 before copying. Flush the destination
   to durable storage and reopen it; recompute every destination length/hash. Rehash
   the source after acquisition and require before/copy/after equality, exact path
   cardinality and no extras. Include supporting files in a separately enumerated
   manifest, not in the 1,970 audio-artifact count. Verify the complete manifest and
   its digest after a second independent storage copy. Destination protection and
   successful readable copy verification are prerequisites for calling this an
   encrypted-artifact backup. The current same-computer diagnostic copy is not one.
5. Keep the original device, app data, key namespaces and old evidence intact.
   Record copy date, tool/APK versions, counts, digests and explicit recovery
   limitations privately. Publish only aggregate, content-free outcomes.

The repository's `NonExportableKeystore` rejects keys with an exported encoding.
Android Keystore run keys and the vault-root key are therefore not backed up by
copying alias names, metadata, wrapped Tink keysets or ciphertext files. Such a
copy is **device/key-dependent encrypted preservation**, not a guarantee of
recovery after phone loss, factory reset, uninstall, key invalidation or UID loss.
Do not attempt to export Keystore secrets or weaken encryption to remove this
limitation. A portable recovery copy would require a separately approved security
and privacy design: authenticated readback while the original keys still work,
then controlled re-encryption under an owner-approved independent backup key and
verification on a separate reader. This is neither implemented nor authorized by
the current planning task.

## Conditions for later authenticated readback

Readback requires separate physical authorization, active tested mutation fences,
fresh exact custody comparison, verified encrypted preservation and an admitted
reader that can operate on the unfinalized committed prefix without normal
Recovery, source finalization, repair, or original-reference publication. If the
existing product reader requires finalization, it is not an acceptable shortcut;
the protected read-only reader must be separately reviewed and tested.

Authenticate the root envelope/owner/vault and key confirmations; authenticate
wrapped keysets, manifests and **every** ciphertext microfile with the existing
AEAD/AAD binding and integrity checks. Verify candidate/run/generation/digest
relationships and complete frame ordering, boundaries and format against the
394 claims. Reach every authentication tag and end-of-stream; a prefix, catalog
query, successful key open or successful sample is insufficient. Only complete
success can support 31,520,000 authenticated frames for this saved prefix.

Keep plaintext off public logs and uncontrolled temporary files. A content-free
receipt records block/file counts, frame count, full-read status and failure
classification without aliases or audio. Compare original ciphertext, catalog,
key identity/challenges and protected logical state before/after, including the
47 historical sources. Any failure leaves the original source and keys intact
and the hold active. Successful readback alone does not authorize resume,
finalize, delete or ordinary startup.

## Remaining owner decisions and execution gates

- Approve the successor diagnostic protection contract for LONG-02 while retaining
  all 47 historical sources, including exact APK/policy/operator admission and
  a separate bounded physical read-only session. Existing ADR-RECORDING-009 is
  scoped to the old exact47 campaign and cannot be silently generalized.
- Choose and approve backup destination, access controls, retention and whether
  device-dependent encrypted preservation is sufficient or a portable encrypted
  recovery design is required. No export design is implicitly authorized.
- After protection and preservation verification, separately authorize complete
  authenticated readback. Keep final disposition outside that authorization.

No device, source, policy, historical evidence, key, commit or PR was modified by
this protection-planning subtask. No physical test is started and no Stage 8.6
PASS is asserted.
