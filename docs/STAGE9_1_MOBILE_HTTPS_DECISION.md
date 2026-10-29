# Stage9.1 — Owner mobile HTTPS access

Date: 2026-10-29. Decision: accepted for the existing owner Alpha under the
owner's explicit Stage9.1 §6 network-access authorization.

## Evidence and scope

Direct Android HTTPS worked on mobile data after admitting its exact egress
`/32`. An ordinary mobile-data toggle then changed that address and blocked the
same installation again. Repeated manual firewall edits would not provide
autonomous phone use. The earlier Wi-Fi TLS timeout remains a separate network
limitation; this decision does not claim to repair it.

Add the default-off `EnablePublicOwnerHttps` parameter to the existing stack.
When explicitly enabled, it admits IPv4 TCP 443 from `0.0.0.0/0` to the existing
nginx endpoint. SSH remains limited to the operator's exact `/32`; both CIDR
parameters retain their existing strict `/32` validation. No resource, service,
domain, load balancer, IAM grant, storage policy or outbound rule is added.

This scoped owner decision supersedes only the HTTPS source-address restriction
in the historical [ADR-0026](adr/ADR-0026-owner-alpha-live-deployment.md) and
Stage18 deployment guidance. Those records remain unchanged. Closed owner
enrollment, manual per-recording consent, Local offline operation and the
existing two short synthetic Cloud tests retain their separate scope.

## Controls retained and checked before preparation

Live nginx configuration retains trusted TLS 1.2/1.3, per-IP 5 requests/second
with burst 20, a 100-KiB body limit and 15-second body timeout. The API listens on
loopback with bounded concurrency; request/response disk buffering and access
logging are disabled. Certificate renewal, API, watchdog and health timers are
active. The live privacy preflight passes and the health alarm is OK.

The deployed authentication and service sources match the reviewed code. Every
request requires the enrolled installation's P-256 proof, timestamp and nonce;
content operations also require a short-lived session, ownership and applicable
manual consent. Enrollment is an operator action with finite expiry. Public TCP
reachability does not grant an invitation or permission to upload or start ASR.

The existing one-active-action, ten-actions/day and two-attempt bounds remain,
with a configured $0.25 daily reservation limit. The account-wide $20 monthly
budget and actual/forecast notifications remain unchanged. Reservations and
delayed budget notifications are not an account-wide hard spending cap. Public
traffic can cause variable costs and resource pressure; this is not a public
release or a guarantee against distributed denial of service.

## Apply and recovery

Prepare the existing template update with only the new parameter enabled, all
existing parameter values preserved, and a saved private recovery snapshot.
The reviewed change set must contain only `HostFirewall` Modify with replacement
`False`. It must not create, delete or replace resources. Execution and subsequent
direct Android/application smoke evidence belong in the Stage9.1 result report;
preparation alone is not a connectivity pass.

Disable `EnablePublicOwnerHttps` to restore the existing restricted ingress.
Do not stop watchdog cleanup, revoke unrelated installations, change TLS trust,
reset reservations or remove retained custody data as part of this rollback.
Private endpoints, egress values, installation identifiers, keys and receipts
remain outside public Git.
