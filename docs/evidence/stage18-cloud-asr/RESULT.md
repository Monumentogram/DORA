# Stage18.1C manual Amazon Cloud ASR вЂ” software verification

Date: 2026-10-27. Branch: `codex/stage18-1c-amazon-cloud-asr`.
Accepted base: `98a975f3aff268db605573e1cd666dbc237f3123`.
Implementation commit and Draft PR: publication pending. No merge is authorized.

`STAGE18_1C_SOFTWARE_STATUS = PASS_LOCAL / PENDING_CI`

`AWS_LIVE_RUNTIME = NOT_RUN`

`CLOUD_ALPHA_PHYSICAL_ACCEPTED = NOT_RUN`

This is an implementation receipt for the Stage18.1C implementation, not a replacement for
historical Stage9/Preview or 6.2D evidence. The requested software-ready result may
be recorded only after final code, automatic checks, independent P0/P1 review and
exact-head CI are verified. No real AWS job or physical-device operation was run
by this evidence/CI task. Real AWS job count attributable to these synthetic
checks is zero; no AWS usage or invoice amount is inferred from reserve estimates.

## Implemented slice

- Existing Android transcript screen: recording-scoped manual consent, explicit
  RU/EN, progress, cancellation and same-action retry. Protected original audio,
  existing encrypted journal, immutable Cloud versions and saved edits are reused.
- Backend: proof-bound installation sessions; immutable recording/action binding;
  bounded encrypted upload; provider dispatch/reconciliation; normalized result;
  cancellation, quotas, reservation accounting and independent cleanup watchdog.
- Amazon adapter: standard Batch in eu-central-1, ru-RU/en-US; private S3 input and
  fixed output keys; explicit distinct CMKs; no arbitrary provider-URL fetch;
  Start/Get/Delete behavior with bounded uncertainty and terminal retry handling.
- Deployment preparation: safe placeholder config, read-only fail-closed preflight,
  role/bucket/proxy/service templates and operator invitation flow. No infrastructure
  is provisioned and no deployed endpoint is claimed.

Implemented routes are `POST /v1/session`, `POST /v1/recordings`, `POST /v1/jobs`,
`POST /v1/jobs/{id}/audio`, `GET /v1/jobs/{id}`, `POST /v1/jobs/{id}/retry` and
`POST /v1/jobs/{id}/cancel`. See the [API contract](../../../backend/API.md) and
[ADR-0025](../../adr/ADR-0025-stage18-manual-cloud-asr.md).

## Evidence matrix

| Check | Observed status | Scope / remaining evidence |
|---|---|---|
| Backend unittest suite | PASS, 42 tests (final source) | Synthetic HTTP/proof/SQLite/SDK Stubber; independent review also ran all 42 |
| Integration-runner host tests | PASS, 2 tests | Emulator serial restriction and exact instrumentation completion parsing; observed RED then GREEN |
| Android JVM/static/lint/build | PASS | 1,120 JVM tests, zero failures/errors/skips; spotless, detekt, app/core lint, Debug/Release and test APK builds |
| API28 encrypted Cloud storage | PASS, 2 tests | Included in consolidated eight-test runner |
| API28 full synthetic HTTPS/editor/restart/outbox | PASS, 8 tests | [Local receipt](local-api28.json); real client/proof/HTTPS/persistence, synthetic provider |
| API36 full synthetic HTTPS/editor/restart/outbox | PENDING_CI | Local launch blocked by disk headroom; CI runs isolated API36 |
| Independent backend P0/P1 review | No P0/P1 found in reviewed snapshot | Namespace/deadline guard included; partial timing compatibility corrected in Android |
| Independent Android P0/P1 review | PASS, no remaining actionable P0/P1 | Two independent reviewers rechecked original findings; repair regressions and final source inspected |
| Exact-head CI | NOT_RUN | Workflow exists; no successful remote run is claimed here |
| AWS live / current deployment preflight | NOT_RUN | No current authorized private deployment/configuration evidence |
| Physical device / new JNI inference regression | NOT_RUN | Existing accepted Stage9 evidence is preserved, not relabeled as a test of new code |

## Reproducible synthetic integration

Build the three debug/test APKs and install backend requirements in an isolated
Python environment, then run against an explicitly selected emulator:

```text
python tools/cloud18_integration.py --serial emulator-5554 --api 28 --apk-root android --python backend/.venv/Scripts/python.exe --receipt tmp/stage18-api28.json
```

On POSIX use `backend/.venv/bin/python`; repeat on an API36 emulator with `--api 36`.
The runner rejects physical/network serials and verifies both the emulator property
and expected API before mutations. Every adb call includes the selected serial.
It installs only debug/test APKs and targets individual fixture methods explicitly:

1. Export the installation public invitation from the real Android Keystore.
2. Start the local test-only HTTPS backend on host loopback; trust its fresh
   certificate only through the instrumentation client's injected connection.
3. Exercise Cloud-only RU and edited-Local EN through real signing/HTTP/vault code.
4. Force-stop the app process and verify the retained Cloud version and edits.
5. Run Cloud encrypted-persistence, consent/editor and encrypted-cancellation tests.

Fixture methods receive `cloudFixtureEnabled=true`; generic instrumentation cannot
accidentally launch the external-fixture scenario. A skipped/incorrect test count
fails the dedicated runner. No SDK AWS client is constructed by the fixture.

The content-free JSON receipt contains source SHA, dirty-tree truth, APK hashes,
API level and exact test pass/count observations. Private fixture keys, encrypted
ledger and raw diagnostics stay under ignored `tmp/cloud18-*`; they must not be
uploaded to Git/PR/CI artifacts. The workflow publishes only APKs and public receipts.
The host fixture is stopped on success or failure; private local diagnostics remain
available for review. A receipt from a dirty tree is not exact-commit evidence.

## Limits and next action

MANUAL_ONLY is the only implemented consent policy. Automatic reconnect, ALWAYS,
ASK_EACH_RECORDING, batching, mapped edit merging and public registration remain
out of scope. Existing saved edits and selections remain authoritative. Missing
provider timing/confidence is not invented, and timestamp accuracy is not measured.

The accepted historical EN limitation remains **20.4420% versus the failed 18%
benchmark**, owner-only under OD-62D-EN-01. This synthetic suite provides no new
RU/EN quality or arbitrary-speaker evidence. A second real speaker or material
provider/configuration change requires revalidation.

No POCO commands, Android user switches, Pilot v2 update, LONG recording access or
Stage8.6 endurance is part of this task. Stage8.6 remains NOT_READY.

Next action: publish the Draft PR and verify its CI, including API36 integration. Current AWS account/opt-out/TLS/IAM/cost/retention/watchdog admission and any
live synthetic smoke require their own verified authorization before execution.

## Local verification details

Full Gradle checks used strict dependency verification and the same tasks listed in
`.github/workflows/stage18-cloud-asr.yml`. JVM totals: app 114; audio Debug 264 /
Release 265; common 1; model 5; VAD API 52; VAD Sherpa 5; recovery 414. Backend
unittest 42, runner tests 2, local-access guard tests 3, versioning-contract tests
44, Whisper build guard tests 3 and Stage9 runner tests 2 passed. Native packaging
alignment passed for all eight ABI entries of the two existing allowlisted libraries.
No new native library or model was introduced.

The local API28 emulator contained an existing optional Stage9 model. The first
consolidated run passed signed HTTPS, restart and encrypted storage but correctly
failed the missing-model UI precondition. For the final run, that emulator-only
model directory was temporarily held aside and restored afterward, with its
original byte size verified. The Cloud fixture now explicitly asserts the model
is absent. All eight final tests passed. No physical device was selected.

Independent review found and corrected session-wrapper forwarding, replay of a
fully accepted final chunk before dispatch, cancellation after foreground lease
loss, idle-action cancellation before vault open, original-destination binding on
retry, cancellation checks around paid retry, authenticated cancellation-file
commit verification, and older Cloud retry after a successful Local fallback.
The v1 binary transcript fixture remains readable; partial provider timing remains
optional without fabricated counterpart values. Cancellation tombstones survive
an actual app process stop and remain independent of the original-audio lease.

The current successor device-test inventory now explicitly accounts for the two new
Cloud persistence methods (162 bulk methods total). Historical inventories, accepted
physical evidence and the existing long-test deferrals remain unchanged. The first
clean governance check caught the missing entries; both were added without hiding
any inherited test. The new workflow checks the exact requested head SHA.
