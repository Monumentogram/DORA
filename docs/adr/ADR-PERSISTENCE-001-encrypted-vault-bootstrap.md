# ADR-PERSISTENCE-001: Encrypted local identity bootstrap and SQLCipher composition

Status: implementation decision within the owner's bounded Stage 8.2 scope;
**PENDING_FINAL_PUBLICATION**. Dependency admission, independent review and runtime
acceptance remain gates. This is not a claim that Stage 8.2 has passed.

## Authority and the missing layout

The frozen Stage 7.4 Security & Identity architecture sections A/B and E–G require
one random local profile, confidential owner/vault relationships, an independent
random SQLCipher secret wrapped by nonexportable Android Keystore, authenticated
role/object identity, and a minimal plaintext bootstrap-framing allowlist.
ADR-AUDIO-001 requires the unified encrypted product/Recovery journal. Neither
defines how to recover confidential owner/vault IDs before opening that journal.
ADR-0011 specifies local retention and custody, not this pre-database layout.
Persisting those IDs unencrypted would violate the frozen boundary; requiring a
process to remember them would prevent restart. This decision supplies only the
necessary local bootstrap format and does not change identity or retention policy.

## Immutable bootstrap bundle

Reserve a credential-encrypted private no-backup namespace durably before key
generation. Its public selector is independently random and opaque, contains no
owner/session linkage, and selects the versioned Keystore alias. Namespace,
selector, alias and bundle creation are no-overwrite operations. Existing partial
or ambiguous state never authorizes replacement keys or a new database.

Store one bounded immutable bundle containing two authenticated envelopes:

1. An identity-root envelope whose canonical AAD binds its format, role, opaque
   bootstrap selector and an explicit non-recording session marker. The encrypted
   payload contains owner and vault UUIDs, an independently random database-object
   selector, and the exact ciphertext digest of the second envelope. Owner/vault
   fields cannot be external root AAD inputs before they are decrypted; they are
   confidential authenticated root payload instead.
2. A database-secret envelope whose canonical AAD binds format, role, bootstrap
   selector, the actual decrypted owner/vault, database-object identity and the
   explicit non-recording session marker. Its plaintext is an independent random
   256-bit database secret. It shares no audio key material.

Verify both full authentication tags, canonical bounded decoding, the root's
exact inner-ciphertext digest and every binding before borrowing the secret.
This authenticates the pair without an extra completion receipt. File fsync,
directory fsync and exact authenticated readback precede successful creation.
After interruption, reopening may accept only that exact complete bundle; partial
state remains fenced and retained. No automatic repair by key regeneration or
silent deletion is admitted. Bootstrap loss remains distinct from payload damage.

The identity root is the minimal pre-identity boundary. All later database and
recording mappings bind actual owner/vault values. Accepted Recovery formats and
AAD are unchanged and gain their product binding through the encrypted journal.
Cross-role, cross-object and cross-vault substitutions must fail regression tests.

## Database and key custody

Use actual Room with an explicitly injected SQLCipher helper. No platform helper,
plaintext fallback, development secret, destructive migration or readonly fallback
is a product construction path. SQLCipher's stock Java logger is disabled before
library loading; native SQL/path/profile diagnostics require the separately
identified hardened candidate in `android/vendor/sqlcipher`.

The helper uses one physical connection per authenticated open epoch, configures
and verifies WAL, FULL, foreign keys, disabled auto-checkpointing and memory-only
temporary work before schema access, and fences unexpected physical replacement
or policy drift. Verification must never hold a helper monitor while waiting for
the connection: Room's asynchronous invalidation transaction can otherwise create
a lock cycle. Every durable catalog operation owns a top-level transaction;
nested success/readback cannot claim durability.

Data KEKs remain independent of per-use biometric authentication. App Lock is a
separate revocable authority checked by the runtime composition. StrongBox is
preferred when available, not required; actual KeyInfo protection is disclosed.
Existing alias loss, invalidation, failed authentication and temporary provider
unavailability retain ciphertext and never authorize a replacement key.

## Consequences and limits

The bootstrap selector, framing, lengths and ciphertext digests reveal bounded
existence/correlation metadata already covered by the allowlist. They are not
credentials or safe logging fields. App UID/Keystore integrity is assumed; root,
memory compromise and forensic flash erasure are not solved. Managed-memory
zeroization is best effort. No rekey, migration, reset, export, Cloud identity,
retention TTL, microphone or recording UI is added by this decision. Stage 8.2C
and 8.3 remain NOT_STARTED.
