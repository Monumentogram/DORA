# Stage 8.6C.3 — Alpha persistence optimization and protection preparation

This successor starts at `9c6b7563eca5b6c003ad2e192a887b5df2630e2d` on
`stage/8.6-poco-recording-acceptance`, in draft PR99. The owner authorizes source
optimization, synthetic encrypted verification and publication; POCO access is excluded.

The [result](RESULT.md) records acceptance gates and outstanding work.
[Benchmark protocol](benchmark-protocol.md) was fixed before C3 timings.
[Reservation proof](catalog-reuse-proof.md) explains the five-to-four catalog-load
change and its invalidation boundaries. [ADR-PERSISTENCE-004](../../adr/ADR-PERSISTENCE-004-linear-order-and-reservation-snapshot.md)
records the optimization decision; [ADR-RECORDING-010](../../adr/ADR-RECORDING-010-successor-protection-readonly-acquisition.md)
records successor protection and read-only acquisition.

The successor policy preserves every one of the prior 47 protected identities and
adds exactly LONG-02. Owner-private policy bytes and source identifiers stay outside
Git. Preparation from retained, pinned offline metadata neither installs protection
nor proves live key availability, authenticated LONG-02 PCM or a portable backup.
Ciphertext remains dependent on non-exportable device Keystore keys.

Only synthetic fixtures are copied and authenticated during this task. The typed
read-only mechanism opens an acquired encrypted copy without normal Recovery or
key creation. Release builds deny diagnostic protection/acquisition. New test sources
retain ordinary Recovery; historical sources retain mutation fences.

`LONG02_ROOT_CAUSE = NOT_PROVEN`

`LONG02_PHYSICAL_PROTECTION = NOT_INSTALLED`

`STAGE_8_6 = NOT_READY`

No POCO launch/install/acquisition/readback/mutation, new 60-cycle series, battery
experiments, Sheet changes, Group D, Cloud or ASR work is part of this evidence.
CI is an engineering gate and cannot promote Stage8.6 to PASS. The PR remains draft
and unmerged. Physical protection and one control long run need a separate owner decision.
