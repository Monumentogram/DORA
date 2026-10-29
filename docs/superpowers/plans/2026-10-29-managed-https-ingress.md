# Stage9.2R managed HTTPS ingress implementation plan

> Execute inline using superpowers:executing-plans. The owner already approved the documented API Gateway choice and up to USD2/month; do not repeat that approval.

**Goal:** Complete standalone owner-only RU/EN Cloud acceptance on Preview5 after proving a stable HTTPS path.

**Architecture:** One Frankfurt HTTP API forwards seven exact DORA methods to the existing HTTPS origin. No Android/backend business code change, caches, Lambda, runtime roles or new storage. Keep source/path/body-bound owner proofs intact.

**Spec:** Owner Stage9.2R request and approved `STAGE9_2R_ALPHA_INGRESS_DECISION.md` (workspace report; private deployment identifiers stay outside Git).

- [x] Compare direct Wi-Fi/LTE with a second client and TCP evidence. Preserve uncertainty about the exact dropping component.
- [x] Prove 2/16/87KB complete requests/responses through one temporary managed ingress on both phone networks; verify proof header and body preservation.
- [x] Reimport the seven-route OpenAPI definition into that same API; set stage throttle2/s burst10; keep request/response logging and caching disabled. Verify unsigned denial before product calls.
- [x] Keep old RU audit/versions; do not rewrite consent destination. Confirm zero provider attempts, cancel the old operation before a replacement consent if it cannot resume through the new origin.
- [x] Use installed Preview5/user10 for the authorized synthetic RU/EN. Verify real provider jobs, UI results, versions/edits, active choice, historical baselines, restart and cleanup.
- [ ] Record exact configuration/receipts privately, sanitized evidence and relevant verification publicly; create Draft PR without merge. Document operational costs and direct-route limitation.

**Rollback:** Restore original endpoint only if needed, preserve all local versions and cloud audit, remove only this added gateway, and restore nginx exactly. Never disable TLS/auth or mutate stored consent silently. A new APK requires its own concrete installation approval.

**Review focus:** method/path rewriting breaks signatures; edge stripping Authorization breaks sessions; body limits must remain at the backend; stale RU consent targets the old endpoint; successful network probes alone do not establish Cloud Alpha acceptance.

## Execution rulings and evidence

- Ruling: add a minimal backend/nginx JSON-envelope correction — actual signed
  audio is121771 bytes and receives413 despite transport success. POCO platform
  JSONObject characterization reaches174808 bytes for a65536-byte slash-heavy
  chunk.192KiB bounds the raw audio JSON; decoded64KiB and metadata100KiB remain.
  Risk if wrong: increased bounded pre-auth memory, not unbounded audio admission.
- Regression first failed with413/BODY_TOO_LARGE, then the complete backend suite
  passed58 tests. Tests cover exact decoded bytes, signature tampering, unsigned
  rejection, raw overflow and65537-byte decoded rejection.
- Live source/config preimages were checked exactly; atomic replacement, nginx
  validation and service health passed with rollback on any deployment failure.
- RU resumed the same replacement action after repair, completed one paid attempt,
  and retained Local text/edits. EN completed one en-US attempt. Both Cloud results
  received manual edits; RU Cloud explicitly selected with Local edit kept in
  history. No APK update or original-record edit.
