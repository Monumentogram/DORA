# ADR-0015: Owner-only Alpha identity and local signing

Status: Accepted for task 7.1, OWNER_ONLY_CLOSED_INTERNAL_ALPHA
Date: 2026-09-30
Decision owner / signing owner: PROJECT_OWNER
Authority: explicit owner instruction to execute 7.1 after 6.3 admission.

## Context

The admitted source is 92f00f7dd4a18a3d4b2fdd159fdaef86fa3f8699 plus
4e7742d88377d3d915be904618fc220d479ab25d (five admission documents only).
ADR-0001 deliberately reserved `com.monumentogram.dora.bootstrap` for Stage00.
The owner explicitly authorizes choosing `com.monumentogram.dora` for internal
Alpha, with a separate durable internal signing key and installation proof.

## Decision

- Alpha applicationId and namespace: `com.monumentogram.dora`. Migrate the small
  app source/test package now; core/PoC namespaces remain unchanged. No provider,
  deep-link, Firebase or package-specific tooling conflict was found in the
  active app. POCO package inventory has no exact product-package collision.
  This is not a trademark clearance, store registration or public distribution.
- Debug verification uses `com.monumentogram.dora.debug` to avoid interfering
  with the durable signed Alpha. There are still only debug/release build types;
  no flavors or new product modules. Release assembly is unsigned intermediate
  output, not a distributable Alpha; only the explicit signing tool publishes
  the owner-local internal artifact.
- First product version: code 2, name `0.1.0-alpha.1`. Product version codes must
  increase strictly; future owner versions use code >=4 and increment alpha.N.
  Code 3 / `0.1.0-alpha.1-upgrade-test` is reserved for disposable same-key
  installation proof using `-PdoraAlphaUpgradeTest=true`; do not distribute it.
  Public release/semantic stability policy is deferred.
- `tools/build_internal_alpha.py` builds existing release and signs with SDK 36
  apksigner, then verifies the certificate, exact package/version and 16 KiB
  alignment before publishing an APK plus public receipt. Missing material or
  certificate mismatch fails closed. Secrets are not Gradle properties, command
  line password values, configuration-cache inputs, or CI inputs.
- Dedicated RSA-3072 internal Alpha key, alias `dora-internal-alpha`, separate
  from Android debug and future production/store signing. The public SHA-256
  certificate is pinned in the versioned 7.1 contract.
- Custody: owner's private local application-data signing directory, outside
  every repository, with owner/system-only ACLs. Password file and PKCS12 stay
  there. Only the owner or an explicitly authorized owner environment may sign.
  No external signer/cloud secret manager is needed. Owner must keep an encrypted
  offline backup of key, password and certificate and verify restoration;
  current backup completion is not claimed. Loss prevents same-package updates
  under this signing identity. Replacement requires separately planned signing
  lineage/rotation or uninstall and clean reinstall with possible data loss.
- Distribution: local signed APK -> explicit `adb -s <owner-device> install`
  or Android package installer -> authorized owner device only. Same-key update
  uses `install -r` and strictly higher code. Never use downgrade flags as an
  upgrade proof. Bootstrap package migration is a clean install, with no promise
  to migrate PoC state. Uninstall only the exact task-created Alpha package.
- Minimal shell shows DORA Alpha and its version. No microphone/network
  permission, recording, storage, ASR or backend functionality is added.

## Supersession and limits

Supersedes only ADR-0001's current app identity, namespace and debug-only artifact
restriction for owner-only 7.1. Historical Stage00 evidence stays unchanged.
No public-release/store authority, production key or second-user admission.
Stage 7.2 remains NOT_STARTED. PR #86 remains OPEN/DRAFT/UNMERGED and untouched.
Recovery clean replacement is required before Stage 8 recording/storage
acceptance, not before 7.1. No Recovery source or governance rule is changed.

## Evidence

See [7.1 decision and runbook](../stage1/DORA_ALPHA_IDENTITY_SIGNING_INSTALL_V0_1.md)
and its [machine-readable contract](../contracts/DORA_ALPHA_IDENTITY_SIGNING_INSTALL_V0_1.json).
PASS requires device installation/launch, same-key upgrade and exact-source CI;
build success alone does not close 7.1.
