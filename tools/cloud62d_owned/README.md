# Private owner-corpus recorder

Run `launch.cmd` on the prepared Windows host. It finds the installed Python runtime,
starts a loopback-only server and opens the browser. Keep its console open while
working; closing it stops the server. The default private corpus is a sibling of
the repository, never inside Git. A frozen private inventory must already exist.
The launcher neither installs packages nor invokes AWS.

1. Explicitly confirm the displayed speaker/processor attestation.
2. Choose and test the microphone, then record each guided candidate. READ capture
   stops automatically at about 44 seconds; other capture stops at about 59 seconds.
   The stop button becomes available after 20.25 seconds of received audio samples,
   not elapsed wall time. The audio thread caps captured frames even if the UI is
   delayed; a separate 90-second wall-time watchdog ends a stalled acquisition.
   Browser processing is
   requested without echo cancellation, noise suppression or automatic gain.
3. Listen to every recording. Correct the READ text for actual deviations; enter
   and verify every word for spontaneous and diagnostic recordings.
4. Freeze selection, then annotate the twelve requested READ clips. Select a word,
   seek using the waveform, and press S/E for its start/end. Space toggles playback;
   arrows seek 10 milliseconds. Zoom and slower playback support close listening.
   Partial timing drafts remain private and are not gold references. Explicit blind
   human confirmation is required to submit complete timings.
5. Finalize the private corpus. This validates records and creates composition
   fixtures and a private manifest; it does not upload or begin Phase B.

The browser captures a mono stream at its native AudioContext sample rate. The
tool preserves a PCM16 WAV of this source and resamples that preserved source to
mono 16000 Hz signed PCM16 LE WAV. It never rewrites a saved capture. Invalid
submitted acquisitions are preserved and excluded before deterministic selection.
For the own-voice speakerphone task, the page supplies a previously recorded own
READ clip for explicit playback while the microphone records the room.

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
```

All test audio is synthetic and temporary. The capture test substitutes an oscillator
MediaStream for the microphone API; it never opens a hardware microphone. Browser
tests do not use or modify the actual private corpus. Human hardware recording,
speaker identity, transcript accuracy and independent word timing still require
the Project Owner's real local actions.
