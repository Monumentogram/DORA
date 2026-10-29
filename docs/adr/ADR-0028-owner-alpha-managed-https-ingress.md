# ADR-0028 — Owner Alpha managed HTTPS ingress and JSON envelope repair

Date: 2026-10-29. Status: accepted within the owner's Stage9.2R repair scope.

## Evidence and decision

Direct POCO→EC2 connections lose payload/ACK progress on both available networks,
sometimes before HTTP. An independent HTTP implementation on the same phone also
fails; the exact dropping component is unresolved. A bounded API Gateway HTTP API
experiment passed complete 2/16/87-KB requests and responses on LTE and Wi-Fi with
normal TLS validation and preserved proof headers/body. The owner then approved
retaining that same single ingress for Alpha at an additional USD2/month operating
budget. The earlier temporary experiment had a separate USD1 authorization.

Use one regional HTTP API in eu-central-1, seven exact existing DORA routes,
HTTP_PROXY integrations to the existing trusted HTTPS origin, payload format1.0,
25-second integration timeout, manually deployed `$default` stage, stage throttle
2 requests/second and burst10. No Lambda, new storage, authorizer, runtime IAM role,
DNS purchase or cache is introduced. Access logging remains disabled. Backend
installation proofs, sessions, owner/consent gates and all paid-job limits remain.
The API Gateway endpoint itself does not confer access to audio or transcription.

Actual signed RU upload then exposed an independent application-layer defect:
nginx returned413 for a121771-byte JSON body. Android JSONObject escapes Base64
slashes; a valid65536-byte chunk can serialize to174808 bytes at offset0, versus
87427 bytes without that escaping. A POCO platform characterization reproduced
the expansion, and an HTTP integration regression failed on the former100-KiB
limit before the repair. This is a proven envelope defect, not proof of the
earlier network failure's cause.

Amend the Stage18.1C raw JSON envelope bound only: nginx accepts/buffers192KiB;
backend audio routes accept192KiB while metadata routes retain100KiB. Decoded
audio remains capped at65536 bytes, Base64 length at87384 characters, and source
duration/frame limits remain unchanged. Exact wire bytes remain signature-bound.
This avoids an APK replacement and supports the installed Preview5 serializer.
No storage schema, audio encoding, consent or paid retry behavior changes.

## Operational boundaries

The old zero-byte RU consent was pinned to the direct endpoint and could not be
silently redirected. It was cancelled through the app and its zero-byte server
cancellation completed through the guarded domain service. No provider attempt
existed. A fresh endpoint-specific consent created one replacement RU action;
all subsequent retries resumed that same action. EN uses its own manual en-US
consent. Only the two previously authorized synthetic recordings were uploaded.

Budget is operational, not an AWS spending cap. HTTP API request charges and
outbound traffic remain variable, including unauthenticated traffic. Throttling
is not a dollar ceiling. Existing backend daily reservations remain independent.
The existing short-lived origin certificate and renewal service remain required.
Permanent offline Whisper is independent of Cloud invitation expiry.

## Rollback and qualification

Keep private deployment identifiers and exported configuration outside public Git.
Restore the prior nginx and HTTP-ingress source together if the envelope repair
must be rolled back; retain ledger, custody keys, versions and cleanup services.
Removing the added API requires a deliberate endpoint/consent transition; changing
global setup cannot rewrite persisted consent destinations. Direct EC2 remains an
unreliable fallback on the measured phone networks.

The [Stage9.2R result](../../STAGE9_2R_HTTPS_ROOT_CAUSE_AND_REPAIR_RESULT.md)
records functional RU/EN, restart/history and cleanup evidence. This decision does
not qualify public distribution, universal network compatibility, ASR quality,
Stage8.6 reliability, Signal implementation or a PR merge.
