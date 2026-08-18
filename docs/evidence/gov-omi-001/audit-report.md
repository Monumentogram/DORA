# GOV-OMI-001 Phase A public-metadata audit report

Authority: `GOV-OMI-PHASE-A-PUBLIC-METADATA-AUDIT-AUTH-20260818-01`
Dora base: `97df1dc029328de16d7b2cc9f4aadcefc043bbbd` / tree `4b4ac11c07a0465c22a6f7840c40d809955c92f2`
Upstream snapshot: `BasedHardware/omi@7d99abcc4efb9e46a5853b21fc01289e4b891837` / tree `85db621ffd5dc5386bcbd7c87713cc69638be7e3`
Phase result: **metadata collection complete; full audit remains blocked**

## Outcome

The authorized Phase A pinned a canonical public Omi commit and tree and produced all five
sanitized governance evidence artifacts. It did not retrieve source or blob bytes, archives,
issue or Pull Request bodies/comments, patches, diffs, binaries, models, datasets or assets. It
did not build, execute, copy, port or admit anything.

The only responsible current conclusions are:

- repository and tree identity: `PROPOSED` metadata evidence;
- exact license, dependency and vendored-artifact rights: `BLOCKED_RIGHTS`;
- component behavior, quality, compatibility and reuse fit: `INSUFFICIENT_EVIDENCE`;
- out-of-stage product surfaces: `DEFER`;
- hazard and fix claims derived from title metadata: `INSUFFICIENT_EVIDENCE`.

No reuse, port, learning-only, rejection, evaluation-approval or admission conclusion is made.

## Collected evidence

- The default `main` ref resolved to commit `7d99abcc4efb9e46a5853b21fc01289e4b891837`; its immutable tree is
  `85db621ffd5dc5386bcbd7c87713cc69638be7e3`. Both remained unchanged through the final recheck.
- The recursive Git tree response was not truncated: 13,341 path-metadata entries and 48 root
  entries. Its deterministic metadata serialization SHA-256 is
  `007d030a21e72c07c89f36359c0e5ded355147c85b5b6284ae0858e5e5058e90`.
- Path-only classification found 21 license/NOTICE candidates, 188 manifest/control paths,
  two vendor roots, zero submodules and one symlink.
- Release metadata was complete at 998/998 records.
- Issue metadata was complete and unique at 2,953/2,953 records.
- Tag metadata stopped at the declared cap: 1,000/1,470.
- Pull Request metadata stopped at the declared cap: 5,000/8,794; 3,794 records were not
  collected.
- Live issue/PR counts each increased by one during collection. The immutable commit/tree did
  not move; the dynamic-index limitation is explicit in
  [upstream-snapshot.json](upstream-snapshot.json).

## Component shortlist

The path metadata makes four areas worth a later, separately authorized read-only review:

1. mobile capture/lifecycle paths under `app/`;
2. STT/VAD/diarization/provider paths under `backend/`;
3. protocol and regression leads under `contracts/`, `contract_tests/`, app/backend tests and
   `.github/failure-classes/`;
4. future device-protocol leads under `sdks/device/`.

This is a research shortlist only. The first two are `BLOCKED_RIGHTS`; the contract/test
surface is `PROPOSED`; the device SDK is `DEFER`. No interface, fallback implementation,
dependency or owner is selected. Full details are in
[component-matrix.json](component-matrix.json).

Desktop, web, infrastructure, plugins, MCP, OmiGlass, firmware and hardware remain `DEFER`
unless a concrete Dora contract or future port receives separate scope.

## Rights and dependency result

GitHub reports root license metadata as MIT and the tree contains a root `LICENSE` path, but
Phase A did not retrieve or hash its bytes. Nested licenses, NOTICE files, manifests, lockfiles,
vendored binaries and `.gitattributes` content were likewise not retrieved. GitHub-detected
license metadata does not establish evaluation, modification, redistribution, patent,
trademark, model, dataset or embedded-asset rights.

Therefore the exact rights and dependency result is `BLOCKED_RIGHTS`, not a legal conclusion.
See [license-surface-inventory.json](license-surface-inventory.json).

## Hazard result

A deterministic title taxonomy found metadata leads in nine Dora-relevant hazard classes. Titles
were used in memory and are not retained; the public record contains stable numbers/URLs,
timestamps and title SHA-256 values. No title proves a failure or fix. No issue/PR body, discussion,
patch, diff, upstream test or source path history was read.

Accordingly, all nine hazard classes remain `INSUFFICIENT_EVIDENCE`. The proposed fixtures in
[hazard-register.json](hazard-register.json) are Dora-owned synthetic test ideas derived from
Dora's existing safety contracts; they are not copied upstream tests and have not been added to
the Test Strategy or backlog.

## Evidence limits and next gate

A full GOV-OMI-001 audit cannot become `DONE` from Phase A. The next transition, if desired,
requires a new exact owner scope that names:

1. the exact source/license/blob paths or issue/PR bodies permitted for read-only retrieval;
2. an artifact-level IP-policy disposition before retrieval;
3. named Product/Legal/IP and Engineering/Security reviewers;
4. immutable query/capture rules and privacy controls;
5. a Draft-PR-only file scope that does not displace the active stage.

Until then, source retrieval, clone/archive download, code execution, copying, porting, dependency
admission, product implementation and PoC execution remain forbidden.

## No-admission statement

This Phase A result does not import, evaluate or admit Omi source, dependencies, binaries, models,
datasets, assets, services, schemas, tests or product behavior. It does not change Dora's Kotlin/
Compose baseline, local-first/no-account/no-network/no-GMS contract, explicit microphone Start/Stop,
authoritative user edits, privacy/deletion guarantees, active Stage 0D work or any existing PoC
verdict.
