# USER0 preservation protocol validation plan

**Goal:** Establish a reviewable preservation protocol and measured limits without any physical-device action.

**Spec:** Owner request, 2026-10-21, “DORA — USER0 Historical Vault Preservation Protocol”; baseline `52e4ce8bdf074ec021ae52c3c38ad9d26c24e128`.

**Architecture:** Keep product and Recovery implementations unchanged. Exercise existing copied read-only primitives and real SQLCipher with synthetic encrypted fixtures; document unsupported states as STOP. Independent source and alternate-release audits inform the protocol.

**Constraints:** No phone queries/actions, historical storage/key/recording access, PIN/marker/user changes, APK installation on physical devices, production startup, or gate closure. Emulator-only synthetic setup may create keys/files and run Recovery to construct fixtures. No private evidence or identifiers in Git/CI. PR106 remains draft/open/unmerged.

**Review focus:** WAL/SHM side effects versus source bytes; missing WAL yielding stale catalog; incomplete/pending states rejected rather than repaired; cryptographic authentication versus real user authorization; inability of another UID to use original Keystore; no App Lock before credential; operator-free pilot is not production-equivalent.

- [ ] Trace startup, earliest writes, SQLCipher and Recovery boundaries; record exact source locations.
- [ ] Add synthetic characterization/negative tests only, including actual WAL behavior, repeated source hashes and committed-prefix oracle. Unsupported states must fail closed; do not add a diagnostic production bypass.
- [ ] Add an emulator-only runner with negative host tests and dedicated API28/36 CI. Run local host checks, formatting/static analysis, audio JVM tests and encrypted instrumentation. Preserve failed attempts.
- [ ] Write `docs/security/USER0_HISTORICAL_VAULT_PRESERVATION_PROTOCOL.md`: eleven requested physical-protocol sections, alternatives matrix, exact owner decisions and operator-free pilot evaluation.
- [ ] Independent final review, address all P0/P1/P2, scoped publication/privacy audit, exact-commit CI, draft successor PR. Record terminal result with physical evidence explicitly NOT_RUN.

Execution decision: perform the already requested investigation/tests inline with independent audit/review agents. This is a feasibility protocol task, not implementation of a new production acquisition route. Test additions characterize current behavior; unsupported behavior is evidence, not permission to loosen assertions or change Recovery.
