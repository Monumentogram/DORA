# Private owner-corpus recorder — reduced eight-clip protocol

Run `launch.cmd` on the prepared Windows host. It finds the installed Python runtime,
starts a loopback-only server and opens the browser. Keep its console open while
working; closing it stops the server. The default private corpus is a sibling of
the repository, never inside Git. A frozen private inventory must already exist.
The launcher neither installs packages nor invokes AWS.

The active acquisition protocol evaluates exactly eight clips from one owner: two READ and
two SPONTANEOUS in Russian, and the same in English. It uses all eight, with no
reserves and no noisy slice. Noise and timestamps are `NOT_EVALUATED`; results
cannot establish quality for other speakers. The original protocol, private
inventory and acquisition history remain preserved by the versioned migration.

Recording is deferred. Microphone selection, testing and recording remain disabled
until the owner explicitly says “Готов записывать” in the chat and the authorized
local resume operation enables acquisition. The HTTP capture endpoint and backend
also reject deferred captures. The page has no self-service enable endpoint, and
launching the recorder does not enable recording. Existing consent and saved work
are preserved; no new AWS login is requested by this tool.

1. Retain the previously confirmed speaker/processor attestation; confirm it only
   if it has not yet been supplied.
2. After the explicit readiness step, choose and test the microphone, then record
   each guided clip. READ capture
   stops automatically at about 44 seconds; other capture stops at about 59 seconds.
   The stop button becomes available after 20.25 seconds of received audio samples,
   not elapsed wall time. The audio thread caps captured frames even if the UI is
   delayed; a separate 90-second wall-time watchdog ends a stalled acquisition.
   Browser processing is
   requested without echo cancellation, noise suppression or automatic gain.
3. Listen to every recording. Correct the READ text for actual deviations; enter
   and verify every word for spontaneous recordings. Existing recordings and
   human references remain intact.
4. Freeze all eight verified clips. Manual timing annotation is hidden and is not
   required in this reduced protocol. A missing/invalid clip blocks completion;
   there is no automatic replacement or reserve.
5. Finalize the private corpus. This validates records and creates composition
   fixtures and a private manifest; it does not upload or begin Phase B.

The browser captures a mono stream at its native AudioContext sample rate. The
tool preserves a PCM16 WAV of this source and resamples that preserved source to
mono 16000 Hz signed PCM16 LE WAV. It never rewrites a saved capture. Invalid
submitted acquisitions are preserved and excluded before deterministic selection.
Legacy acquisition and human timing controls remain available only for an original
protocol store; they are not part of the reduced eight-clip evaluation.

Only the fixed UI assets are public HTTP routes. State/audio require a random
per-launch session capability; Host and Origin checks reject non-loopback access.
No request logging, external UI assets, analytics, network ASR or upload route exists.
Private JSON, audio, transcripts, timing drafts and manifests must never be staged.
The session capability stays in browser session storage to support page refresh;
relaunching creates a new server/capability while preserving saved corpus progress.
An operating-system lifetime lock permits only one recorder writer per private
corpus. A second launcher reports `RECORDER_ALREADY_RUNNING`; use the existing
window or close its console before relaunching. Crashes release the lock automatically.

## Offline checks

Run from the repository with Python 3.12:

```text
python -m unittest tools.cloud62d_owned.test_corpus tools.cloud62d_owned.test_server
node tools/cloud62d_owned/test_audio.mjs
```

Browser checks require local Chrome and Playwright. Point `DORA_PYTHON` at Python
and `NODE_PATH` at the installed Node dependency directory, then run:

```text
node tools/cloud62d_owned/test_browser.mjs
node tools/cloud62d_owned/test_capture_browser.mjs
node tools/cloud62d_owned/test_reduced_browser.mjs
```

All test audio is synthetic and temporary. The capture test substitutes an oscillator
MediaStream for the microphone API; it never opens a hardware microphone. Browser
tests do not use or modify the actual private corpus. Real recordings and actual-word
verification remain personal Owner actions after explicit readiness. Independent
word timing is not required or evaluated in the active eight-clip protocol.

The [prospective protocol amendment](../../docs/stage0/DORA_CLOUD_62D_EIGHT_CLIP_PROTOCOL_V0_1.md)
names the replaced requirements and limitations. It is not the full Phase A successor;
AWS configuration, live preflight and publication gates remain separate.
