# Stage18.1E — AWS live deployment and Cloud ASR

Date: 2026-10-28. Branch: `codex/stage18-1e-live-deployment`.
Base: `fe9eb66580d92f59604ea2d7e8c53a0313c650cf` (Draft PR117).

**PASS / STAGE18_1E_AWS_LIVE_CLOUD_ASR_READY** for the owner-only backend and
real Android RU/EN path. Physical acceptance and publication checks are recorded
separately below. This report does not admit
Stage8.6 endurance, historical-vault migration or public release.

## Resources and access actually applied

- One running Ubuntu 24.04 `t3.micro` in eu-central-1; standard CPU credits,
  IMDSv2, one retained Elastic IPv4, termination protection.
- Encrypted gp3: 12-GiB root and separate persistent 8-GiB custody volume.
  SQLite WAL and its custody key live on the required mounted filesystem.
- Two private S3 buckets, separate rotating customer CMKs, TLS-only bucket
  policies, enforced ownership, all Public Access Blocks, one-day lifecycle
  and incomplete-upload expiry. No replication, object lock or versioning.
- Restricted EC2 runtime role/profile; no attached managed policies, admin
  permission, PassRole, public or cross-account key principal.
- CloudWatch aggregate health metric/alarm, SNS topic with confirmed owner
  subscription, monthly account budget **$20** with notifications.

Deployment used temporary SSO sessions for **Dora18Deploy**. Root was used for
authorized access bootstrap; two exact CloudTrail lookups diagnosed the rejected
Start. Deployment changes and runtime operations used their separate non-root
identities. No permanent AWS access keys were created.

The backend URL is recorded in the owner-local copy of this report; infrastructure
identifiers and endpoint are intentionally absent from public Git. The API binds
to loopback behind nginx. SSH uses the operator's exact `/32`; HTTPS additionally
admits the verified owner's phone egress as one exact `/32`, through a default-off
optional parameter. No global HTTPS ingress is enabled.
Public HTTP serves only static ACME challenge files; `/v1/session` on HTTP is 404.

## Security and operational checks

| Check | Observed result |
|---|---|
| Workload identity and effective Transcribe opt-out | VERIFIED before jobs and after reboot |
| S3 privacy, ownership, encryption, lifecycle and CMK rotation | VERIFIED by runtime preflight |
| HTTPS chain/IP SAN and normal Android TLS validation | PASS; no trust override |
| Unauthenticated HTTPS request | 401 AUTH_INVALID |
| Certificate renewal | Certbot dry-run with nginx deployment hook PASS; twice-daily timer enabled |
| Config/key filesystem permissions | root:dora 0640 config; dora custody directory 0700/key 0600 |
| Swap, dumps and payload logging | Swap disabled, core limit 0, bounded volatile journal; no API access log or body logging |
| Watchdog supervision | SIGKILL followed by automatic restart PASS |
| Required custody mount | Empty disposable missing-mount probe refused startup |
| Health alarm | Real missing telemetry caused ALARM, SNS action succeeded; restart recovered OK and SNS action succeeded |
| EC2 reboot | Boot ID changed; API/watchdog/nginx/timers active and enabled; key and both result hashes identical; SQLite integrity ok |
| Source package | 23 files verified against SHA-256 manifest on host |

The alarm's missing-data transition took approximately seven minutes because
CloudWatch evaluates an extended datapoint window. The independent watchdog
remained running throughout this alarm probe. SNS action success plus confirmed
subscription proves the configured delivery path; inbox receipt of each alert was
not independently attested.

## Real recognition and transcript history

Actual Android client requests used installation proof, HTTPS, encrypted local
originals, backend custody, private SSE-KMS S3 and Amazon Transcribe Batch.
No provider stub or invented response was used for these results.

| Fixture | Duration | AWS result | Android evidence |
|---|---:|---|---|
| Russian synthetic speech, ru-RU | 16.495 s | COMPLETED, 28 segments | Cloud version stored, Local raw version and saved correction unchanged |
| English synthetic speech, en-US | 14.480 s | COMPLETED, 34 segments | Cloud version stored, Local raw version and saved correction unchanged |

RU excerpt: «это техническая проверка распознавания русской речи».
EN excerpt: “This is a technical test of English speech recognition.”
These are functional smoke results, not a new recognition-quality benchmark.
Android process restart preserved both Cloud texts/provider identities, original
availability, active Local selection and saved edits.

The first RU Start was explicitly rejected by AWS output-access validation.
Exact CloudTrail evidence, Get and List absence established non-acceptance before
the operator reconciled that one attempt. The same action's explicit attempt 2
succeeded; no uncertain Start was blindly repeated. The additional exact canary
grant fixed the rejection. A second issue rejected AWS's explicit false Settings
defaults; adapter regression tests reproduced it, and the corrected watchdog
ingested the already completed RU job without launching another job.

For RU/EN and the physical POCO test every attempt reached a terminal state.
Temporary input/output and provider job deletion were confirmed, cleanupPending=false and ledger chunk
bytes=0. The zero-byte provider write-check object was removed after terminal
reconciliation. Final operator List calls confirmed both buckets empty and zero
multipart uploads; exact provider-name List calls confirmed all four attempts absent.

## Physical POCO and publication

POCO was connected and switched to user 0 under the owner's explicit instruction.
No factory reset, clear-data or deletion of existing recordings was performed.
The old debug package was updated in place; its historical protected diagnostic
vault returned UNAVAILABLE. It was not reset or migrated. A separate local debug
application ID was built for the new synthetic Cloud test. User 10 was not modified.
**POCO_CLOUD_ACCEPTANCE = PASS / USB_TUNNEL_ASSISTED**. The new 16.495-second
synthetic recording completed real Amazon Transcribe RU recognition with 28
segments. Manual consent and the existing action's retry/result check were
exercised. Cloud text/provider identity, Local raw version, active Local selection,
saved correction and original audio availability survived application process
restart. This is a synthetic stored-recording test, not a microphone/endurance test.

The phone's HTTPS egress differed from the computer. A reviewed firewall-only
change added that verified `/32` for HTTPS. TCP SYN/SYN-ACK/ACK then reached the
host, but the phone's HTTPS POST still timed out while opening the output stream;
no TLS payload arrived in the bounded packet-header observation. A GET probe
returned 404 once. The same POST from the computer returned 401 in about 0.3s.
The owner could not change networks and requested another method. A temporary
loopback-only CONNECT relay on the computer and ADB reverse transported encrypted
TLS bytes for the exact backend destination. A private opt-in instrumentation
listener selected that proxy only in the isolated test process; product code,
endpoint, certificate trust, request signing and device-wide network settings were
unchanged. Non-target relay destinations were rejected. One accepted POCO job was
created; a transient response failure was reconciled through the existing UI action
after the backend had completed, without another Start. The secured phone window
refused the optional screenshot; the harness now tolerates that refusal. Read-only
result reconciliation and process-restart assertions both passed. No screenshot
is claimed as visual evidence. The relay and ADB mapping were removed afterward,
and the temporary background-launch app-op was restored.

Direct phone HTTPS remains **BLOCKED / HTTPS_NETWORK_TIMEOUT** with its exact
upstream cause unknown. The successful physical result therefore demonstrates the
USB-assisted path, not independent everyday phone connectivity. Future standalone
use needs a working permitted network route. No TLS trust bypass or global HTTPS
ingress was introduced. The unused invitation
for the historical debug installation was revoked; the isolated invitation remains
finite. Historical diagnostic-vault migration is not repaired or certified here.

## Cost and continuing charges

AWS Budget reported **$0.00 actual account spend at the check**. Billing is delayed;
this does not prove the deployment is free or provide an itemized test invoice.
The live ledger reserved **$0.0730** for four RU/EN/POCO attempts, including the
pre-acceptance rejection. Reserves deliberately overestimate usage and are not bills.

The current Frankfurt standard Batch price is $0.0001/second ($0.006/minute).
The three accepted fixtures total about $0.0048 ASR before rounding, taxes, credits
and ancillary requests. Fixed resource forecast remains approximately
**$17.16/month + usage**. One hundred Batch minutes add about $0.60 ASR.
Sources: [AWS Transcribe pricing](https://aws.amazon.com/transcribe/pricing/) and
the [AWS Frankfurt price catalog](https://pricing.us-east-1.amazonaws.com/offers/v1.0/aws/transcribe/current/eu-central-1/index.json).

Continuing billable resources: EC2, both EBS volumes, retained IPv4, two CMKs,
S3 storage/requests, CloudWatch metric/alarm and any SNS delivery/ASR usage.
There is no new domain, hosted zone, load balancer, NAT gateway or second EC2.
Budget notifications are not a hard spending cap. Admission also retains the
daily reserve/action/attempt bounds; no bulk campaign was run.

## Validation and handoff

- Backend regression suite: **55 tests PASS**; new tests first reproduced the
  output probe policy gap and explicit false Settings rejection.
- cfn-lint 1.58.0: **PASS**, eu-central-1.
- Android app/audio JVM checks, final Spotless/Detekt/lint and test APK build:
  **PASS**. Emulator reconciliation of an already stored real RU Cloud result:
  **PASS**, without dispatch or another paid job. Publication-SHA CI is separate.
- Physical POCO: normal-trust HTTPS probe, completed-result reconciliation and
  process-restart/version/edit assertions **PASS** through the scoped USB relay.
- Base PR117 exact-source CI was successful before deployment. New-branch CI is
  independent and must not inherit that verdict.
- Independent review found narrowly scoped production changes sound. The opt-in
  harness now checks the configured destination, recovers the actual persisted UI
  action and handles an already stored Cloud result without dispatching a new job.
- [Draft PR118](https://github.com/Monumentogram/DORA/pull/118), stacked on Draft
  PR117, is OPEN/DRAFT. Implementation commit: `930bd7eb7b40cb35635d80dcf5021bc079678b72`.
  Stage00 artifact validation passed on that clean committed source. The PR reports
  publication-head CI independently. No branch or PR has been merged.

No open P0/P1 was found in the scoped AWS/RU/EN deployment. Standalone physical
network connectivity and older Stage8.6/diagnostic/release gates remain separate.

Operational follow-up: retain the owner egress restriction, finite installation
invitations, certificate renewal, watchdog and health/budget response. Do not stop
cleanup or retire keys/storage before pending work is terminal and retention is
satisfied. See [ADR-0026](docs/adr/ADR-0026-owner-alpha-live-deployment.md) and the
[deployment runbook](backend/deployment/alpha/README.md).
