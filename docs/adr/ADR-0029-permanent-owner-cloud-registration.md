# ADR-0029 — Permanent owner Cloud registration

Date: 2026-10-29. Authority: explicit owner Stage 9.3 request.

The Stage18 finite grant currently expires both access sessions and installation
authority. This would disable the registered POCO after 2026-11-04T23:59:59Z.
Stage9.1 already includes ADR-0027's permanent Local Whisper fix.

Use the owner's preferred existing key/proof architecture. Add nullable `trusted_at`
to the installation record with an additive serialized SQLite migration; NULL keeps
legacy finite-grant semantics. An operator-only `trust` command confirms the exact
existing owner/public key while the invitation is active. Expired legacy grants need
explicit `--recover-expired` after renewed owner confirmation. Revoked/unknown/retired
identities and key/owner changes fail closed. No HTTP enrollment route is introduced.
Idempotent trust preserves the UUID, key, original expiry, jobs and source bindings.

Trusted authority lasts until explicit `revoke`. Every HTTP request still proves
method/path/body/time/nonce with P-256; sessions last 900 seconds, timestamps have
60-second tolerance and durable nonces reject replay. The Android client already
renews sessions using its non-exportable Keystore key. No APK, shared API key, refresh
secret, permanent bearer, new AWS infrastructure or real-clock change is needed.

Sweep preserves permanent registrations (including revoked identity records) and
active owner source bindings. Ordinary 24-hour audio deadlines, 30-day jobs/results,
90-day consent history, quota/reserve limits, manual consent and cleanup remain.
The historical finite expiry remains evidence, not a trusted-device cutoff.

Alternatives rejected: moving the deadline only postpones failure; permanent bearer
credentials remove proof binding; replacing the existing auth protocol expands scope.
ADR-0025's finite-authority paragraph is superseded only for explicitly trusted keys.
Legacy invitation expiry still fences installations that were never confirmed.

Verification uses controlled clocks through 2100, real crypto/SQLite/HTTP with a
test provider, and separate current-time POCO/API Gateway acceptance. Future physical
clock tests are not claimed. Backend rollout must stop API/watchdog together, check
old source hashes and perform owner/key-checked trust migration before restart.
Old backend rollback would reintroduce finite authority and is recovery only.
