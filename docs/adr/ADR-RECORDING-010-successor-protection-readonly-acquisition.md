# Stage 8.6C.3 successor protection and isolated read-only preservation

Date: 2026-10-09. Status: owner-authorized engineering scope; physical admission
NOT_GRANTED by this record. Stage 8.6 remains NOT_READY.

The owner authorizes a successor diagnostic policy which retains every restriction
of ADR-RECORDING-008 and ADR-RECORDING-009 and adds the exact failed LONG-02 source.
The protected set is the previous exact 47 sources plus one separately identified
source, totaling 48. Replacement, wildcard ownership, heuristic source selection,
and weakening the existing shared-catalog exception are prohibited. The old policy,
old diagnostic evidence and offline LONG-02 collection remain immutable.

The successor policy has a distinct version, predecessor policy digest, exact
predecessor identities, exact added identity, owner/vault binding, and exact added
physical-source/run/key namespaces. The signed debug APK pins the complete private
policy. Missing, corrupt, truncated, mismatched, removed, or wrong-vault policy must
fail closed before Room startup. The old V1 parser remains exact47; V2 is exact47+1.
An absent, empty, linked or out-of-root database must be rejected before the
delegate helper can create or open it. V2 admits exactly the predecessor namespace
union the added namespace and requires four contiguous physical sources.
Release builds cannot activate either diagnostic policy. Ordinary unrelated new
sources retain the existing full Recovery and deletion behavior.

Protected append, resume, finalization, reference publication, deletion, quarantine,
cleanup and key mutations remain denied before their first side effect. Startup
must compare the successor's exact owned catalog claims and physical-source rows
before passing control to Room. Root keys, wrapped keysets and unrelated historical
keys remain protected; names or saved key properties do not establish decryption.

A separate read-only acquisition and reader is admitted for implementation and
synthetic verification. It must not construct the normal recording runtime or
invoke Recovery, reconciliation, repair, migration, finalization or quarantine.
Original files are opened read-only with no-follow, regular-file, descriptor and
size checks. Exact pinned ciphertext inventory, source-before/source-after equality,
exclusive destination creation, durable destination writes and reopened hash
verification precede any copy receipt. SQLCipher opens only the acquired copy,
explicitly read-only, without Room. Root envelopes and existing run keys authenticate
without key generation, replacement or export. Every committed unit's confirmation,
wrapped keysets, manifest, binding and complete microfile AEAD plaintext must verify;
partial success cannot produce a complete-read receipt. Owned PCM buffers are
cleared after use; provider-internal memory erasure is not claimed.
The acquisition caller must establish a quiescent source before capture. Matching
hashes are an integrity check, not independent proof that a live writer is stopped.

The acquisition and private-policy reader own every descriptor returned by
`Os.open`, use `Os.read` directly, and close each descriptor once in `finally`,
including rejected reads and destination-open failures. Android's
[`FileInputStream(FileDescriptor)` contract](https://developer.android.com/reference/java/io/FileInputStream#FileInputStream(java.io.FileDescriptor))
leaves the supplied descriptor owned by the caller when the stream closes; a
borrowed stream's `use` block is therefore insufficient. Synthetic repeated
inventory, read, copy and failure tests measure bounded process descriptor counts.

The API28/API36 synthetic gate must exercise actual encrypted vaults and keys,
complete readback, failures and preservation. Pure mocks alone cannot establish
read-only composition readiness. Public reports contain aggregate outcomes only;
private identities, policy contents, aliases and audio remain outside Git and logs.
The full synthetic V2 fixture has 394 encrypted blocks across four physical sources,
an independently checked known PCM input, an actual pinned-policy loader, runtime
ownership preflight, repeated isolated readback and catalog corruption negatives.
It also exercises a fresh source through append, restart, Recovery and deletion.
Test-only AssetManager resource mounting may use reflection to supply a synthetic
pin; production policy guards, acquisition and crypto composition remain typed.

Engineering completion does not mean the successor APK/policy is installed, that
LONG-02 has been read or backed up, or that any current device state is verified.
No physical POCO connection, ADB operation, installation, app launch, policy change,
LONG-02 mutation, new long run, portable key export, or readiness promotion is
included. A physical procedure requires its own exact APK/operator/policy admission
and owner-authorized bounded session. Device-dependent encrypted preservation does
not guarantee recovery after loss of the original non-exportable keys.
