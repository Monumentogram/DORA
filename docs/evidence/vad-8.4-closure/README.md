# Stage 8.4 admission closure successor

Repository disposition: **PENDING_FINAL_PUBLICATION**. This successor does not
declare terminal admission PASS. Isolated physical execution and private custody
retrieval have passed; final exact-SHA CI and review remain external gates. Production VAD integration, 8.4C and 8.5
remain NOT_STARTED; Stage 8 remains IN_PROGRESS. No Sheet write.

Baseline: `7b3a4b494df0c1d18c8f7bcd8e064c21393ea040`; accepted parent:
`23adb618a39a014e5090ee2f32e27015de485f07`. PR95 stays OPEN/DRAFT/UNMERGED.
The historical generic rejection and remediation installation failure remain
unchanged. This task does not rebuild or replace the admitted AAR/model.

## Private custody and its precise boundary

The existing owner-only DORA Google Drive now holds a separate version folder
and a new content-addressed AAR object. Metadata was read back after upload:
`shared=false`, one `user/owner` permission, no public/domain grant. No public
link sharing was enabled. IDs and exact digests are in [supply-chain.json](supply-chain.json).

Version `1.13.8-dora.1` is permanently bound to 23,396,212 bytes and SHA-256
`64969d0fbf5d3936a127879684bc2f14014b99817ca3c9b2e16298c1372208db`.
Publication is append-only: never update this object's content or substitute
another file under the same version. Different bytes require a new version and
new file ID. Existing-version publication must first verify existing bytes;
a mismatch blocks publication rather than overwriting. The retrieval verifier
has no upload, update or network API and checks the frozen version/ID/access
contract, size, SHA and exact six-entry AAR inventory before consumption.

This is **append-only publication policy with checksum-verified immutable
artifact identity**, not storage-enforced WORM. The Drive owner can technically
modify or delete objects. Such changes cannot become an accepted candidate:
retrieval fails closed. Owner deletion/account loss is an availability risk;
old Drive revision retention is not guaranteed and is not the recovery plan.
No team or CI access is implied by owner-only storage. If storage-enforced
prevention of owner modification/deletion is required, this mechanism is
insufficient and requires a separately approved storage control.

## Retrieval and workstation-loss recovery

1. Authenticate as the owner in Drive or the connected Drive tool. Download the
   exact AAR file ID from the manifest into a new empty local directory. A name,
   URL label or version alone is never sufficient. Never log temporary access URLs.
2. Run `python tools/vad_admission/custody.py --manifest docs/evidence/vad-8.4-closure/supply-chain.json --artifact <downloaded-file>`.
   A size/SHA/inventory mismatch is `VAD_BINARY_INTEGRITY_FAILURE`; stop before
   harness/build/Gradle use. Do not refresh the expected digest from Drive.
3. The separately stored recovery kit contains the exact build/harness/verifier
   sources, toolchain and dependency identities, NOTICE, SBOM, source/model
   provenance and an internal file-hash index. Download its fixed file ID and
   verify its size/SHA from the manifest before unpacking/using it. The kit's
   identity manifest can verify the AAR even without this workstation or Git checkout.
4. Follow the bundled build/harness instructions. The model remains a separate
   pinned Silero6.2.1 object; its source and SHA are in the preserved provenance.
   The current task neither substitutes nor re-downloads it.

Operational proof: the AAR was fetched from Drive into a fresh directory,
verified, and consumed by the existing isolated harness builder. The original
build-output path was not used. A fresh kit download matched all 14 indexed file
identities and its ZIP SHA. [retrieval.json](retrieval.json) records the content-free
result. Both objects survive loss of the development machine while owner-account
access and the Drive objects remain available. This is not a simulated loss of
the cloud account or a claim of an independent second-provider backup.

CI receives no permanent private credentials and does not fetch/build the AAR.
Current admission uses controlled owner retrieval; future CI consumption needs
a separately scoped least-privilege access gate and the same mandatory hash check.
No Maven coordinate or production dependency is installed by this task.

## Physical installation and native runtime succeeded

The earlier bounded install returned `INSTALL_FAILED_USER_RESTRICTED: Install
canceled by user`. USB installation was already enabled; no primary-user install
restriction was observed. Automatic navigation was rejected as potentially
changing a developer/security setting. The owner opened the second USB-install
row manually: the list showed no applications. An ordinary owner-present
`adb install --no-incremental -r <harness.apk>` then returned Success in 4.25 s.
No security setting change or automatic confirmation click was used. The exact
cause of the earlier cancellation remains unproven; do not infer an explicit
owner rejection or a proven incremental-install defect.

The installed APK was pulled back and hashed: SHA-256
`3f3b20e4c09068ba276bf747dda6734a837d3349f90ef7f1ae277f5b2946b756`,
matching the harness built from the clean custody retrieval. Its model and two
native entries matched the pinned model/AAR. Actual mapped app library files
were hashed on device and matched those entries. The only app native libraries
were `libonnxruntime.so` and `libsherpa-onnx-jni.so`; ordinary Android platform
libraries are separately inventoried. No eSpeak/piper or unexpected non-platform
library was observed.

POCO M5, Android14/API34/arm64: six measured init/inference/reset/release cycles
in one process passed. The harness was force-stopped and process absence verified before the first
launch, which is measured cycle 0; there is no outstanding warm-up worker.
Before **each** measured cycle the prior receipt was
deleted and absence verified, then `am start -f 0x10008000 -n
com.monumentogram.dora.vadadmission/.SmokeActivity` recreated the activity.
Each fresh receipt reported 100 silence windows / 0 positives, 372 synthetic
speech windows / 262 positives / 3 segments, successful reset to silence and
native release. Each cycle includes 100 post-reset silence windows: 3,432 total
inference windows. The earlier private driver failure from an unsupported
ActivityManager option was corrected without changing the APK or AAR.

Content-free receipts, exact identities, mapped library names and resource
series are in [runtime-smoke.json](runtime-smoke.json). PSS ranged from 116,312
to 120,178 KiB; FD count 153-156 and threads 33-34. No obvious unbounded resource
growth in this bounded smoke; this is not formal leak/endurance acceptance.
The process crash buffer had no observed native/FATAL/UnsatisfiedLinkError.
All measured native releases completed before the isolated package was stopped;
no process remained. The harness has no microphone/network permission. Only
synthetic speech and silence were processed; no owner audio was recorded.

Final exact-SHA CI, independent review and bounded publication audit are external
receipts after commit. Neither metadata validation nor successful APK packaging
proves physical native VAD execution. No Stage 8.4 product PASS, acoustic-quality
acceptance or physical 16-KiB runtime claim is made. Development no-PIN behavior,
the OPEN security-restoration blocker and deferred PERF-REC-001 are unchanged.
