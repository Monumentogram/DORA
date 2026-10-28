# ADR-0026 — Owner Alpha live deployment

Date: 2026-10-28. Status: accepted within the owner's explicit Stage18.1E scope.

## Decision

Apply the Stage18.1D.2 package in eu-central-1 with one t3.micro, encrypted
12-GiB root plus 8-GiB persistent custody volume, two private S3 buckets and two
regional CMKs. A restricted Identity Center permission set deploys the resources;
the EC2 role runs the application. No permanent AWS access keys, runtime admin
policy or runtime PassRole are introduced. Administrative bootstrap and runtime
authority remain distinct.

Use a trusted short-lived Let's Encrypt certificate for the retained Elastic IP.
No domain or hosted zone is purchased. Optional HTTP-01 ingress serves only static
challenge files; default configuration keeps this ingress off. The actual API is
HTTPS-only behind owner /32 ingress and request-proof authorization. An optional
second verified owner-device /32 permits HTTPS only; its default is empty and it
never grants SSH. Live POCO diagnostics demonstrated distinct computer/phone
egress, so this bounded entry is necessary. Certbot's
systemd timer checks twice daily and reloads nginx after renewal. The operator must
keep challenge reachability and monitor renewal; the certificate lasts about 160h.

Permit Transcribe's exact `output/.write_access_check_file.temp` output-access
probe in the restricted output bucket. Live evidence showed that denying this key
rejects Start before acceptance despite correct KMS usage. Preserve job prefix,
account, regional KMS, TLS and public-access constraints. AWS's two explicit false
Settings defaults do not enable features; accept only those literal false values.

## Admission and evidence

Owner authorization covers live synthetic RU/EN, necessary minimal repeats and a
bounded physical POCO test. It supersedes the earlier preparation-only boundary
for these operations. It does not admit multi-user/public distribution, streaming,
diarization, unattended uploads, endurance or historical diagnostic-vault recovery.

The [live deployment result](../../STAGE18_1E_AWS_LIVE_DEPLOYMENT_RESULT.md) records
actual AWS/Android results, preflight, supervision, cleanup, reboot and cost checks.
The [runbook](../../backend/deployment/alpha/README.md) retains rollback and
custody requirements. A reboot may not regenerate the key or reset the ledger.
The workload, cleanup, original deadline and reserve survive client/server restart.

Physical acceptance uses only a newly created synthetic recording. The owner
explicitly authorized switching POCO to user 0. Historical debug-vault protection
and user 10 data remain outside the acceptance. An isolated local debug application
ID can separate this test from the historical diagnostic installation; this does
not constitute its migration or a production release.

Private endpoint, account/resource/installation identifiers, raw receipts, keys,
credentials and audio remain outside this public repository. The owner-local
report contains the operational URL. No merge of this branch, PR116 or PR117 is
authorized by this decision.
