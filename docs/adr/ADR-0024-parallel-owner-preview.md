# ADR-0024 — Parallel Owner Preview Alpha v4

Date: 2026-10-27. Accepted for preparation by explicit Owner instruction.
Baseline: `953879250fcd2a6cb9fc4b605126a9806a641f5b`, Draft PR113, no merge.

The installed Pilot is v2/code2 in Android user10. Direct v2-to-v4 update is
NOT_QUALIFIED. Create `ownerPreview`, release-derived with release dependency
fallback, applicationId `com.monumentogram.dora.alpha.preview`, code4,
`0.1.0-owner-preview.4`, visible name `DORA Owner Preview`. Production and Pilot
identities remain unchanged. This is a temporary parallel acceptance variant,
not a replacement for the main application's accepted contract.

Use the existing Owner signing certificate. Signing equality does not share a UID:
no sharedUserId, exported private provider, cross-package grant or shared recording
storage is admitted. App-private noBackupFilesDir and UID-scoped AndroidKeyStore
give a fresh vault and key namespace even with identical internal filenames/aliases.
Keep SQLCipher, AEAD, PIN-free local authority, no backup/transfer, no diagnostic
operator or debug bypass. Main runtime code and native inputs stay byte-identical
to the verified base. Capture remains bounded to 4,800,000 frames /300seconds.

Dedicated Preview build/audit/graph/SBOM entrypoints are derived from the frozen
Pilot checks so their package, branch, ancestry, label and receipt cannot accept
the other identity. Their duplication is intentional for this temporary branch;
do not silently widen the historical Pilot acceptance boundary. Private inputs,
model weights, signing material and signed APKs never enter Git/public Actions.
Two clean strict no-cache builds must match in complete unsigned and signed bytes.

Required exact-head CI: Preview software, Preview API28/API36 UI and complete
applicable persistence inventory, parallel-package isolation API28/API36, Stage9
software/API28/API36 and eight version-label jobs for debug/alphaTest/Pilot/Preview.
Synthetic dual-package tests use current-source Pilot v4 as a peer and prove file,
UID and Keystore separation plus Pilot survival after Preview uninstall. Separately
test exact private v2 and signed Preview installation/metadata where ABI translation
allows. Neither result qualifies v2 runtime, migration or ARM82 physical execution.

The [new physical protocol](../ARM82_PARALLEL_ALPHA_PHYSICAL_PROTOCOL.md) requires
fresh explicit Owner approval. No phone commands, including read-only commands,
are authorized now. No historical user0 data, LONG-01/LONG-02 or Recovery access.
Stage8.6 remains NOT_READY; `ARM82_DEVICE_RUNTIME = NOT_RUN`.
