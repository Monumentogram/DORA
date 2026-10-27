# Stage18.1D.2 minimal AWS infrastructure preparation

> **For agentic workers:** Use superpowers:executing-plans; local preparation only.

**Goal:** Prepare deployable owner-only Alpha infrastructure without applying it.

**Architecture:** Two private buckets and distinct CMKs preserve ADR-0025 and the
existing backend's custody boundaries. One EC2 t3.micro in eu-central-1, encrypted
persistent EBS, one API and a separate watchdog; no replicas, NAT or load balancer.

**Tech Stack:** CloudFormation JSON, Python 3.12, systemd/nginx, existing backend.

**Spec:** Owner's Stage18.1D.2 request; public deliverable
`STAGE18_1D2_MINIMAL_AWS_INFRASTRUCTURE.md` records every requested comparison and limit.

## Global constraints

- Base 54cd19d96b33a1c6783fe5b04da0aa27fa1f3055; separate branch/Draft PR.
- No AWS writes, deployment, audio, jobs, POCO or merge.
- No private account identifiers, credentials or owner contact data in Git.
- No runtime refactor; any compatibility fix requires regression evidence.

## Review focus

- Start permissions must actually authorize the adapter, without arbitrary output.
- Lifecycle representations must cover the whole bucket, never a restricted filter.
- Infrastructure rollback must preserve the ledger, custody key and pending cleanup.
- Loss of host, watchdog or telemetry must alert, not look healthy.
- Templates must be acyclic and produce a disabled valid backend configuration.

## Task 1: Infrastructure and operational configuration

Files: `backend/deployment/alpha/*`, existing runtime IAM template; minimal
preflight compatibility change only if required by documented S3 representations.

Interface: stack Outputs -> private disabled LiveConfig; existing services and
adapter remain the runtime. Health monitor reads metadata only and publishes one
aggregate metric, never recording identifiers or payloads.

- [x] Add negative regression tests for scoped lifecycle and invalid configuration,
  health failures, and policy authorization constraints; observe failures.
- [x] Prepare local CloudFormation, parameter example, config renderer and health
  timer; separate workload and key administration. No provisioning in helpers.
- [x] Validate with cfn-lint and run backend/deployment suites without credentials.

## Task 2: Evidence, review and handoff

- [x] Record official current prices, bounded read-only inventory, comparisons,
  deploy/verification/rollback instructions and exact future approval scope.
- [x] Fresh independent review of the branch; resolve material findings and test.
- Publication: inspect diff/privacy, then commit/push a dedicated branch and attach
  its Draft PR. Exact commit and PR state are recorded in the final handoff/GitHub.

## Execution record

Pre-flight: templates, renderer and monitor share only documented stack outputs
and the existing SQLite metadata schema. Deployment remains a separate owner gate.
Ruling: reuse the clean linked worktree on a new branch from the requested SHA;
the Stage18.1C branch reference remains untouched. This avoids another large checkout.

Task 1 complete: cfn-lint 1.58.0 clean; backend suite 52 PASS without AWS.
Task 2 preparation/review complete: report and runbook cover the owner specification;
fresh independent read-only review found no actionable Critical/Important/Minor.
Ruling: equivalent S3 empty Filter / legacy empty Prefix must pass admission; scoped
filters remain blocked, proved RED to GREEN. This avoids a representation-only
deployment failure without relaxing the retention scope.
Final review dispositions: live AWS authorization/deployment/Linux/SNS/recovery
certification is deferred to the explicitly unapproved deployment stage; current
prices were checked by the author with official catalogs/read-only Price List.
These are evidence boundaries, not waived deployment prerequisites.
