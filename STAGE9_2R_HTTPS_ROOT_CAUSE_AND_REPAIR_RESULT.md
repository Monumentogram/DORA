# DORA — Stage9.2R HTTPS Upload Root Cause & Repair

2026-10-29. **PASS / STAGE9_2R_DIRECT_CLOUD_ALPHA_READY**.

Installed Owner Preview5 on POCO M5 now completes real RU/EN Amazon Cloud ASR
through the approved managed HTTPS ingress. Both results, independent Cloud edits,
Local history and explicit active choices survived a process restart. No APK
replacement, storage reset, original-audio edit or merge was performed.

## Scope and source

- Branch `stage/9.2r-https-ingress-repair`, based on Stage9.1 integration
  `7c3cabdb18869a9c441638b3094a656a62bc672f` / Draft PR120.
- This report's containing commit is the reviewed source boundary; the new Draft
  PR is stacked on `stage/9.1-unified-owner-alpha`. Publication receipt is in the
  owner-local report. No PR is merged.
- POCO M5 / Android14 / user10, package `com.monumentogram.dora.alpha.preview`,
  code5 / `0.1.0-owner-preview.5`. Installed APK remains
  `5f4edbccd4b58a0a519c79c5800886f20e289db5d537312f6cbd320fb1f0a177`.
- Only the two previously authorized synthetic 11.05-second recordings were sent.
  Historical owner recordings were not uploaded. Whisper/Recovery/Signal work is
  outside this repair; prior Local RU/EN offline qualification is unchanged.

## Diagnosis: two distinct failures

### Direct transport remains unreliable

The previous Stage9.2 observation, 24576 of87427 body bytes received followed by
nginx408, remains valid. Stage9.2R reproduced failures at still earlier phases.
Packet inspection retained headers/seq/ack/length only, without audio or tokens.
The second client used raw SSLSocket HTTP on the same platform TLS/kernel; it is
independent of HttpsURLConnection, not of Android's entire network stack.

| Client/network, intended body87427 | TLS | Body at handler | Client result | Elapsed |
|---|---|---:|---|---:|
| POCO Wi-Fi, raw SSLSocket | handshake timeout | 0 | ClientHello233 queued, retransmissions to5; EC2 sees TCP handshake only | 20150 ms |
| POCO Wi-Fi, raw + explicit SNI | handshake timeout | 0 | ClientHello reaches EC2; server handshake retransmitted without ACK | 20110 ms |
| POCO Wi-Fi, HttpsURLConnection | connect/TLS timeout | 0 | no complete TLS exchange | 20099 ms |
| POCO LTE, HttpsURLConnection | TLS1.3 succeeds | 0 | client writes87427 locally; server sees HTTP headers, then408 after15s; response read times out | 20251 ms |
| Independent PC controls, three runs | succeeds | 87427 each | HTTP200; complete2321-byte responses | 562 / 547 / 969 ms |

Phone LTE Send-Q grew to88047, with retransmissions3→5 and increasing RTO.
EC2 repeated574-byte payload without ACK. Loss also affects small233/574-byte
payloads; some1408-byte payloads pass. This does not prove a pure MTU threshold.
Earlier bounded TLS1.2 / MTU1280 / MSS1240 experiments did not fix it and were
reverted. PC and phone had different public egress even on the apparent same LAN.

Consequently, HTTP client choice, request framing, nginx request buffering and
backend authorization cannot explain the pre-HTTP failures. They are not proven
universally fault-free. Exact failing hop/filter/Xiaomi component remains unknown;
no claim blaming a carrier or universal network compatibility is made.

### Proven JSON-envelope defect, repaired

Once the managed route worked, actual signed RU audio received nginx413:
Content-Length121771 exceeded102400. Android JSONObject escapes `/` in Base64.
An on-device, synthetic-only characterization showed65536 decoded bytes →87384
Base64 characters →174808 JSON bytes, with exact round trip. The earlier fixed
87427-byte transport fixture did not represent this serializer expansion.

A signed HTTP regression first failed with413/BODY_TOO_LARGE. The minimal repair
raises only the raw audio JSON allowance to192KiB, keeps other backend routes at
100KiB, and sets nginx request size/buffer to192KiB. Decoded chunks remain64KiB;
all owner proofs, source/duration/consent limits and exact-body signatures remain.
After deployment the same RU action resumed; real bodies121771,105561,104089
returned200. Temporary metadata logging was removed immediately afterward.

## Selected repair and networks

One API Gateway HTTP API in Frankfurt forwards seven exact methods/paths to the
existing EC2 HTTPS origin. No Lambda, additional storage, domain purchase or
runtime IAM change. Normal certificate validation applies on both HTTPS legs.
HTTP_PROXY / payload1.0 /25-second timeout; `$default` manually deployed;
default route rate2/s, burst10; access logging disabled. Deployment export matches
the public origin-placeholder template. Backend authentication remains mandatory.
See [ADR-0028](docs/adr/ADR-0028-owner-alpha-managed-https-ingress.md).

Managed synthetic tests verified full body SHA-256 and full response through EOF:

| Phone network | 2KiB | 16KiB | 87427 bytes | Result |
|---|---:|---:|---:|---|
| LTE, sequential | 881 ms | 174 ms | 283 ms | all HTTP200 / full exchange |
| Wi-Fi, sequential | 272 ms | 136 ms | 199 ms | all HTTP200 / full exchange |

Separate full87427-byte probes passed LTE630ms, Wi-Fi407ms and after Preview
restart552ms. PC managed control passed688ms. Synthetic Authorization,
X-Dora-Installation/Time/Nonce/Signature, Content-Type and exact-body preservation
all passed. The first header measurement was invalid because the diagnostic
nginx handler stripped them; its corrected measurement passed and the handler
was removed. It was not treated as an API Gateway defect.

Final live negative controls: unsigned slash-escaped174808-byte audio→401;
audio196609 bytes→413; metadata102401 bytes→413; forbidden methods and removed
diagnostic route→404. No trust bypass or unauthenticated business action.

Phone default network was validated Wi-Fi or LTE, with no VPN, global proxy or
ADB reverse. USB was used for UI/diagnostic control only, not network transport.
A physically unplugged owner-operated run is not separately claimed. Actual
product RU/EN ran on Wi-Fi; LTE qualification covers the complete transport/proof
contract, not a second paid RU/EN pair.

## Real RU/EN and persistence

| Evidence | RU | EN |
|---|---|---|
| Provider/configuration | Amazon standard Batch, ru-RU | Amazon standard Batch, en-US |
| WAV accepted | 353644 bytes /6 chunks | 353644 bytes /6 chunks |
| Paid attempts | 1 | 1 |
| Completed UTC | 10:47:18.219 | 10:51:21.834 |
| Actual text received on POCO | yes | yes |
| Cloud version saved | version2 | version2 |
| Cloud manual edit survives restart | yes | yes |
| Final active version | Cloud, explicitly selected | Local, retained |
| Local original text/edits preserved | yes | yes |

The previous zero-byte RU consent targeted the direct endpoint. Cancellation was
persisted in the app; a guarded domain-service operator call completed its server
cancellation after confirming0 bytes/chunks/provider attempts/reservations. Its
audit remains. Fresh consent for the new endpoint created one replacement RU;
the413 repair resumed that same action. No ambiguous paid operation was restarted.
The historical cancellation marker can still offer a direct-endpoint check; it
does not represent a live provider job or retargeted consent.

After force-stop/relaunch, all6 documents and11 versions were reopened. The four
historical documents/seven versions match exactly, including rendered edits,
timestamps and active choices. All9 prior Local versions retain exact text/edit
hashes; both added Cloud edits match their pre-restart receipts. RU selection used
the existing conflict confirmation to keep the Local edit in history. This is
functional acceptance of known synthetic clips, not a new ASR quality benchmark.

## Cleanup and operations

Both backend jobs are COMPLETED, validated, with encrypted results retained,
zero remaining chunk ciphertext and no pending cleanup. Exact-name Transcribe
listing through the existing runtime role confirms both jobs absent. Operator
profile listing was denied and was not treated as absence.

One known zero-byte `output/.write_access_check_file.temp` remained after AWS
output-access validation. The existing Stage18 custody audit verified its exact
key, zero length and expected KMS encryption, then removed it. This shared service
canary is not audio/result content and is not cleared by per-job cleanup; later
jobs may recreate it. No new general cleanup behavior was introduced.

At10:59:21UTC both input/output buckets had0 objects,0 multipart uploads and no
truncation. Live privacy preflight passed. nginx, API, independent watchdog,
certificate-renewal timer and health timer were active. Temporary handler/unit,
metadata log and three SHA-verified phone diagnostic jars were removed. Permanent
gateway remains by owner approval. No user recordings, models or keys were deleted.

## Validation, cost and limits

- Backend complete suite:58 tests PASS. New regression covers slash escaping,
  exact decoded storage, signature tampering, missing proof, raw envelope overflow
  and65537-byte decoded rejection. No live AWS calls in that suite.
- Live: nginx validation/reload, exact source/config preimage checks, bounded
  rollback, seven-route deployment/export verification, negative HTTP controls,
  actual POCO/cloud/persistence/cleanup evidence passed.
- Android code/APK unchanged; no new Gradle/Recovery campaign was needed.
  Prior Stage9.1 [Stage18 CI](https://github.com/Monumentogram/DORA/actions/runs/40838811236)
  and [PIN-free CI](https://github.com/Monumentogram/DORA/actions/runs/40838811241)
  passed at985a160. These are inherited, not new-head CI results. New-head CI state
  belongs to the new PR. Generic CI has inherited failures documented in
  [the existing follow-up](docs/STAGE9_1_GENERIC_CI_FOLLOWUP.md); no blanket green
  CI claim is made.
- Owner approved temporary ingress testing up toUSD1, then one retained ingress
  up toUSD2/month. Frankfurt HTTP API first-tier request price observed in the
  official regional price list wasUSD1.20/million; transfer charges are separate.
  Low owner traffic is expected belowUSD0.01/month, not guaranteed. USD2 is not an
  automatic cap. [AWS pricing](https://aws.amazon.com/api-gateway/pricing/).
- Two paid attempts reservedUSD0.0175 each, USD0.035 total, against the existing
  USD0.25/day backend reserve. Reservation is not billed cost; final invoice was
  not queried. Existing infrastructure charges continue.
- Invitation remains valid until2026-11-04T23:59:59Z; later Cloud use needs ordinary
  renewal. Offline Whisper is independent. Origin certificate renewal remains an
  operational dependency; exact direct-route root cause is unresolved.

No new P0/P1 remains in this bounded owner-only acceptance. Public distribution,
network universality, long-term reliability and Signal are not admitted here.

## Changed files and evidence

Runtime delta: `backend/dora_cloud/http_server.py` and
`backend/deployment/nginx.conf.example`. Regression: `backend/tests/test_service.py`.
Gateway definition: `backend/deployment/alpha/http-api.openapi.template.json`.
Contract/runbook, ADR0028, stage status/backlog, execution plan and this report
record the decision. [Sanitized evidence](docs/evidence/stage9_2r/acceptance.json)
contains no endpoint/account/install/job IDs, tokens, transcript text or audio.
Private receipts remain owner-local outside Git. Prior untracked Stage9.2
materials were preserved and not included in this branch's change.
