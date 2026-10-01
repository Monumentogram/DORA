# ADR-0020: Deterministic Cloud contract composition

Status: ACCEPTED FOR 7.3C HOST HARNESS ONLY. Date: 2026-09-30.

Cloud orchestration contracts are accepted only when a deterministic,
provider-free, network-free harness demonstrates their composed safety invariants.
Reuse the frozen 7.2C/D/E validators, model events and count accepted bytes, provider
submissions, attempts, result winners and active-selection changes. Independent
unit validators alone cannot demonstrate ordering across trust boundaries.

Choose a small Python host model with manual time and in-memory fixtures. Reject
live-provider tests (nonhermetic, outside scope) and a backend scaffold (premature
runtime). Catalogue scenarios, repeat normalized traces, exercise negative controls
and require the suite in CI. No new dependency or Android modification follows.

Historical release receipts remain bound to their original source/closure and CI
inventory. Later tooling-only descendants must separately prove unchanged release
inputs; old evidence must never be relabelled as a new release qualification.

This decision proves contract composition only. Authentication cryptography,
atomic revocation enforcement, persistence, worker scheduling, actual provider
behavior and generated merge correctness require later runtime acceptance.
See [specification and plan](../stage1/DORA_ALPHA_CLOUD_CONTRACT_HARNESS_V0_1.md).
