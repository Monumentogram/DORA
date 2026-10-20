# Stage8 limited Alpha — source checkpoint

This branch implements the owner's new five-minute acceptance profile from
`6f5e87317b163e4567ffa76da3cd3191bcfac826`. It reuses the Stage8 recording,
encrypted persistence, original-audio and Recovery components.

Capture admission trims the final PCM block before queue ownership; session input
and the exact authorized writer independently enforce 4,800,000 frames. Pause,
Resume and authenticated restart preserve cumulative duration. The native reader
stops at the boundary; the control owner confirms its release before existing
Stop/Finalize. The UI shows remaining time and claims the Alpha limit was saved
only after durable completion. Sources already at/above the limit cannot continue
capture, and existing audio is not modified by the new policy.

The new versioned contract and matrix separate:

- Limited software readiness: requires the checked-in verification receipt,
  independent review and green exact-SHA `Stage8 limited Alpha` CI (four jobs).
- Physical Alpha smoke: `NOT_RUN / SAFE_DEVICE_UNAVAILABLE`.
- Signed user Alpha: BLOCKED; `DEV-SECURITY-RESTORE-BEFORE-ALPHA-CLOSE = OPEN`.
- Stage9: independent implementation admitted on the limited source/reader
  contracts after the software gate passes. No Stage9 feature is implemented here.

This source commit cannot certify its own future CI or review. The final Draft PR
body links the exact SHA, workflow run, independent review and content-free
artifacts. If those fail, software readiness is not granted. With those green and
no safe physical smoke, the terminal result is:
`PARTIAL / STAGE8_ALPHA_SOFTWARE_READY_PHYSICAL_PENDING`.

See [verification](VERIFICATION.json), [five-minute cases](FIVE_MINUTE_RECORDING_VERIFICATION.json),
[matrix](ALPHA_ACCEPTANCE_MATRIX.md), [deferred requirements](DEFERRED_RELIABILITY_BACKLOG.md),
[Stage9 disposition](STAGE9_READINESS.md), and [release gates](SECURITY_RELEASE_GATES.md).

C3 is not repeated. No physical POCO access, APK installation on POCO, historical
vault acquisition, repeated 60 cycles, battery experiment, merge or signed release.

`LONG02_ROOT_CAUSE = NOT_PROVEN`

`LONG02_PHYSICAL_PROTECTION = NOT_INSTALLED`

`STAGE_8_6 = NOT_READY`
