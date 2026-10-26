# ARM82 Owner Alpha preparation evidence

The successor source implements the [v4 contract](../../contracts/owner-arm82-alpha-v1.json)
and [physical protocol](../../ARM82_OWNER_ALPHA_PHYSICAL_PROTOCOL.md). A source commit
cannot predict its own subsequent signed build or exact-head CI result. The authoritative
final verdict, exact source/APK identity, independent review and CI links are recorded in
the stacked draft PR and final Owner report; complete build receipts remain private.

Observed before the first successor commit:105 OwnerPilot JVM tests,50 owner host tests,
Spotless/Detekt/lint and ADR-0021 source/encryption/history preservation checks passed.
The exact model and six retained ARM82 native artifacts match Stage9 receipts and source
pins. Four unchanged Pilot v3 native hashes are additionally pinned in the final APK audit.
Stage9's real RU/EN x86 API28/API36 functional evidence remains separately attributed to
its source/runtime; it does not certify ARM82 or the new signed artifact on a phone.

Reproduce privately with `tools/owner_alpha_pilot_build.py` and explicit `--source-sha`,
`--sdk`, `--private-vad-dir`, `--private-asr-dir`, `--asr-model`, `--signing-dir`, pinned
diagnostic/debug exclusion certificates and a new external `--output`. The helper requires
clean exact branch ancestry, verifies existing inputs/custody, exports the actual release
dependency graph, builds twice clean/no-cache, signs outside Gradle, compares full bytes,
then audits both actual APKs. `source.json`, graph, CycloneDX and `artifact-preflight.json`
bind these observations. No build command installs or launches an Android application.

Final acceptance requires all12 jobs in the v4 contract, both complete APK hashes equal,
actual Manifest/DEX/no-PIN/backup/native/label audit and independent review without unresolved
P0/P1. A CI APK is public-input/disposable-signed synthetic evidence only; private ARM82,
VAD and owner signing are never available to Actions. ASR model import is ordinary UI;
weights are not APK or CI content. Full model hash and deadline remain in the contract.

`ARM82_DEVICE_RUNTIME = NOT_RUN`; `NO_PHYSICAL_ACTIONS_PERFORMED`.
The operation boundary is the preservation evidence: no POCO commands, no user0 access,
no LONG-01/LONG-02 access, no device install/start/credential/user change. This is not a
fresh device filesystem/content-integrity measurement. The future physical preflight
must independently confirm topology, signer/installed-v3 identity and upgrade safety.
Stage8.6 remains NOT_READY and general distribution remains blocked.
