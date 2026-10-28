# ADR-SIGNAL-001 — DORA Signal presentation migration

Date: 2026-10-28. Status: accepted direction from the owner's task; implementation pending.

The owner approved DORA Signal: dark canvas `#0B1321`, accent `#5CE8D0`,
minimal navigation, large controls, a horizontal live waveform with reference
density of 164 micropeaks, derived from actual microphone PCM.

This decision supersedes only the circular 72-bar presentation, old brand palette
and fixed 20-FPS ceiling in the existing design specification for the future
Signal presentation. It permits rendering up to 60 FPS when resources allow,
with reduced motion and load-based throttling. It does not raise capture polling
frequency or change audio timing, data retention, any storage schema, key, cloud
contract, or Recovery acceptance. Historical specifications remain intact.

Use the current Compose/Material3 presentation and existing action owners. Replace
screens progressively; do not introduce another audio engine, service or backend.
Keep the high-rate visualization state separate from RecordingViewState and
durable recording state. The only capture-adjacent addition is a bounded passive
amplitude observer before the microphone read buffer is wiped. It cannot retain
the PCM array, backpressure capture, or change recording admission.

Alternatives considered by engineering:

- Reuse the current scalar RMS stream: cheap, but its 50-ms sampling can miss
  short peaks and cannot provide the requested detailed history.
- Rewrite capture or move it into the UI: rejected because it risks working
  recording, lifecycle and persistence behavior without serving this task.
- Retain the engine and add a passive peak envelope: selected because it meets
  the signal requirement with a narrow, independently testable change.

Figma file `dkhVOt8b4PiKAv4cUBefa3` could not be inspected: the official MCP
returned Starter-plan tool quota exhaustion on 2026-10-28. No frame dimensions,
fonts, icons or unseen screens are represented as approved. The owner's supplied
colors, density and behavior are sufficient for architecture planning. Visual
parity requires accessible node references or owner-supplied exports before the
affected screen's final visual acceptance. No plan upgrade was made.

See [the implementation plan](../superpowers/plans/2026-10-28-signal-ui.md).
