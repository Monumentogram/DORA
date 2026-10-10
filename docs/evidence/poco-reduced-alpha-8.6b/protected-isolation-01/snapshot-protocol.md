# Protected state snapshot — successor protocol

Authority: OD-86B-LITE-SHARED-CATALOG-EXCEPTION / ADR-RECORDING-008.
Historical 8,550-file baseline is retained. This protocol grants no product
startup, recording, Recovery, deletion, migration or protected-source mutation.

Before any physical helper run, require exact installed product APK, no product
process or in-flight recording, microphone OFF, FGS absent, unchanged historical
file manifest, independently reviewed helper source and synthetic emulator proof.
Freeze the three exact existing canonical DB/WAL/SHM paths in the private receipt.
No wildcard exception is used for file comparison. Source IDs remain private.

The helper fences ordinary coordinator open and the independent recording-access
manager before Activity launch. It authenticates through the existing App Lock,
copies only encrypted selector/bundle/DB/WAL into a fresh private directory and
never opens original SQLCipher files. The copy uses the existing authenticated
root unwrap with no creation fallback. Direct SQLCipher OPEN_READONLY reads the
copy without Room/open-helper migrations or binding insertion. Reuse the admitted
DDL/FK verifier; require schema v3, exact table inventory, exact binding columns
and root-authenticated owner/vault values. Unknown schema/layout/type fails closed.

Read all rows of the 14 application tables and Room metadata without joins or
discovery filters. Preserve NULL, integer and text values, column order, duplicate
multiplicity, schema DDL, pending/deletion/orphan state. The accepted schema has no
FLOAT/BLOB columns; encountering either rejects the snapshot. SQLCipher integrity,
SQLite integrity and FK checks must succeed. The current admitted schema has 15
tables including room_master_table; an unexpected android_metadata table is not
silently omitted. Bound rows and serialized output. This is catalog authentication,
not a fresh claim that every historical PCM payload was read.

Inventory every app-UID Keystore alias and nonexportable key properties. For each
existing AES key, retain a fresh random non-audio AES-GCM challenge in private
diagnostic storage. After operations, decrypt the same prior challenge with the
existing alias; matching alias/KeyInfo alone is insufficient. No key generation,
replacement or deletion is allowed on POCO during snapshot collection. Missing,
unsupported or unavailable keys reject the gate. Challenge bytes, aliases and
full catalog remain private; publish only counts and aggregate hashes.

Every exit verifies all original vault file hashes and mic/FGS state. A failed
inspection never triggers repair/reconciliation, Stop, Resume, deletion, key
generation or automatic retry. A snapshot is admissible only with a COMPLETE
receipt and verified host-side preservation. No concurrent source writer is
allowed while copying; changed bytes reject the attempt.

Synthetic controls before POCO: WAL-backed committed row inclusion, read-only
write denial, full schema/binding validation, source-copy immutability, previous
forensic checks, same-alias key replacement and missing-key rejection. Host
comparison controls cover changed/removed/extra historical rows, schema changes,
namespace reuse, cross-source run references, typed values and key metadata.

This is preparation for isolation. Runtime exclusion, before/after cryptographic
preservation of the physical sources and physical recording remain separate gates.
