# DORA 6.2D AWS preparation v0.1

Preparation only, 2026-09-28. Frozen Phase A v0.1 at
`d8d5c90516b6a9afa96c21e703dfe7481e64e589` is unchanged. No account has
been authenticated, no AWS resource deployed, no audio uploaded and no Transcribe
job started. This is not Phase A v0.2, a provider admission, or production backend.
Actual account configuration, effective policies and billing remain unverified.

## Host and login

The inspected Windows host had no AWS CLI/Python/Terraform on PATH, no standard
AWS CLI v2 installation, and no standard AWS profile, credential file or SSO cache.
Only existence was checked; credential contents were never printed. Bundled
CPython 3.12.14 is available for the evaluation tools. Official AWS CLI v2 was
downloaded from AWS, its Amazon Web Services Authenticode signature verified,
and its MSI administratively extracted into the local workspace tool directory.
No system installation or PATH modification was performed. Verified executable:
`aws-cli/2.37.4 Python/3.14.6 Windows/11 exe/AMD64`. The CLI's embedded Python is
separate from the pinned evaluation Python. Installer/executable digests and
signature proof remain local outside public Git.

Owner action: open `tools/cloud62d_aws/Login.cmd`, select the intended test
account and an assumed role in the browser, and wait for `LOGIN_OK`. Profile is
`dora-62d-alpha`; no keys, tokens, sign-in codes or account identifiers go in chat.
The launcher rejects root and IAM-user identity. If using an existing IAM Identity
Center organization, run `login.ps1 -Method SSO` instead. Console browser login
uses short-lived credentials; it does not require creating permanent access keys.
[AWS browser-login documentation](https://docs.aws.amazon.com/cli/latest/userguide/cli-configure-sign-in.html),
[AWS CLI installation](https://docs.aws.amazon.com/cli/latest/userguide/getting-started-install.html).

## Reproducible task infrastructure

`tools/cloud62d_aws/aws_prepare.py template --output <review-file>` emits a
deterministic CloudFormation JSON template. Private bindings and AWS responses
must be outside every Git worktree. The utility rejects a `.git` directory or
worktree `.git` file in any ancestor. The selected shared private corpus directory
is also used for AWS evidence; its host-specific path must not be published.

The template provisions only the approved bounded test footprint:

- Two dedicated private buckets with `input/` and `output/` prefixes, all four
  Block Public Access flags, BucketOwnerEnforced (ACLs disabled), no versioning,
  object lock, replication or archive tiers. Bucket policies require TLS and
  explicit exact-key SSE-KMS upload headers.
- Separate customer-managed symmetric single-region KMS input/output keys with
  rotation enabled. The exact same-account bootstrap role administers keys;
  only named task operator/data roles can receive cryptographic permissions.
- A task Transcribe data role with input read/decrypt and output write/encrypt,
  trusted only to Transcribe with account and exact opaque job-prefix restrictions.
- An assumed task execution role with input upload, output retrieval, bounded
  Start/Get/Delete jobs, exact data-role PassRole, and cleanup. No permanent key.
- A delete-only Lambda watchdog, one concurrency slot, and five-minute schedule.
  No audio/transcript read permission or content-log permission. Cost tags identify
  Project, Purpose and opaque Run; no participant data appears in names/tags.

Data permissions use exact bucket/key/role ARNs and job prefix. There are no
`s3:*`, `kms:*`, `transcribe:*`, `iam:*` or `organizations:*` actions.
The sole resource-wide Transcribe permission is `ListTranscriptionJobs`, because
that listing API lacks job resource scoping. Results are additionally checked for
the exact prefix before mutation. KMS key-policy `Resource: "*"` means only the
key containing the policy; identity crypto grants name exact key ARNs.
`ListBucketMultipartUploads` is scoped to each exact dedicated bucket without a
prefix IAM condition: that API does not support `s3:prefix`. The actual request
and deletion checks still restrict the task prefix. Independent resource failures
are aggregated; cleanup continues on the other bucket and jobs, then reports
PENDING or raises a content-free watchdog failure for service error metrics.
[Transcribe resource permissions](https://docs.aws.amazon.com/es_es/service-authorization/latest/reference/list_amazontranscribe.html),
[data-role and key permissions](https://docs.aws.amazon.com/transcribe/latest/dg/security_iam_id-based-policy-examples.html),
[S3 API condition support](https://docs.aws.amazon.com/es_es/service-authorization/latest/reference/list_amazons3.html).

CloudFormation bootstrap uses the authenticated, explicitly approved account role;
the tool does not attach policies to it or mutate existing/shared IAM. Its required
creation permissions are CloudFormation Create/Describe/DeleteStack; task-scoped
IAM Create/Get/DeleteRole, Put/Get/DeleteRolePolicy, TagRole and PassRole; S3
CreateBucket, bucket configuration/read/delete actions; KMS CreateKey and exact-key
administration above; Lambda Create/Get/DeleteFunction, Add/RemovePermission,
PutFunctionConcurrency; and Events Put/Describe/DeleteRule, Put/RemoveTargets.
KMS CreateKey necessarily precedes a known key ARN. Existing role policy must be
reviewed after login; broad bootstrap access is not copied into execution policy.
IAM and Organizations are global administrative services with no recording content;
every content client, S3 bucket and KMS key is fixed to eu-central-1.

## Effective service-improvement opt-out

The review-only Organizations policy file is never applied by this tooling.
The authenticated verifier calls `DescribeOrganization`, then
`DescribeEffectivePolicy(AISERVICES_OPT_OUT_POLICY)` for the caller's own account.
It verifies the returned account ID and flattened effective `transcribe` value,
using effective `default` only when no Transcribe-specific override exists.
`optIn`, missing/denied policy, a policy draft containing `@@assign`, an absent
Organization, and malformed data all block processing. A draft's existence is
never evidence of effective opt-out.

The read permissions are `organizations:DescribeOrganization` and
`organizations:DescribeEffectivePolicy`. Parent root/OU policies and account
policies combine; a service-specific override can defeat a default opt-out.
The supplied draft locks its Transcribe decision against child overrides, but
only effective account readback is accepted. These reads do not alter governance.
[Effective-policy API](https://docs.aws.amazon.com/organizations/latest/APIReference/API_DescribeEffectivePolicy.html),
[inheritance and syntax](https://docs.aws.amazon.com/organizations/latest/userguide/orgs_manage_policies_ai-opt-out_syntax.html).

If account readback proves there is no Organization, the sole governance action is:
**Approve/create AWS Organization and AI-services opt-out policy.** Consequence:
account-level Organizations governance is introduced and the chosen policy controls
AI service-improvement use for its attachment scope. Existing Organizations may
instead need their administrator to attach/enable the Transcribe policy. The agent
must not create an Organization or change root/OU/account policy without this owner
confirmation. AccessDenied does not prove that no Organization exists.

## Retention, cleanup and cost

An immutable UTC write deadline is fixed at deployment +23 hours. All subsequent
input/output writes and new task jobs are fenced at that deadline. A server-side
watchdog removes task versions/markers, multipart uploads and terminal jobs after
the deadline, repeating every five minutes. This conservatively bounds ordinary
task objects before their own first-byte +24h ceiling; it intentionally does not
extend for retries. Begin the bounded campaign promptly after deployment. If the
window expires, prepare a new prospective run; never silently extend the deadline.
Lifecycle one-day expiry is only a fallback, not the clock enforcement mechanism.

Immediate safe deletion after durable private result ingestion remains the live
operator's duty. `cleanup` explicitly enumerates and deletes exact task versions
and parts, deletes terminal jobs, and reads back inventories. Nonterminal jobs
remain PENDING; there is no hard-cancel claim. Recheck after late completion.
`retire` requests deletion of the task stack only after the write fence and empty
inventories; customer KMS key deletion has its separate seven-day pending window.
Verify stack absence/key scheduled-deletion state separately; never delete shared
resources. Public reports contain counts/status only.

The schedule is implemented, but effective IAM, successful synthetic expiry,
immediate cleanup, monitoring/invocation readback and late-completion behavior
must be verified in the actual account before retention preflight can pass.
AWS service outages can prevent deadline execution; failures stop new work and
must be reconciled. No cloud-service availability guarantee or all-copy erasure
claim follows from this template. Provider-internal-copy limitations accepted in
ADR-0012 remain unchanged.

Hard budget: USD10 total; Transcribe reservation <=USD2; ancillary/tax reserve
USD8. No credit/free-tier assumption. Two KMS keys, Lambda, schedule, S3 requests,
storage, transfer, key lifetime and tax need a current quote from the actual
manifest/topology. The existing frozen standard-batch rate is USD0.0001 per
billable second, rounded up per attempt; no obsolete 15-second minimum is added.
Alerts are not hard caps. This preparation makes no billable ASR request and does
not infer an account invoice or spend from a price estimate.

## Autonomous continuation commands

Use the installed CLI path and bundled CPython. Every `<...>` argument below is
filled by the agent from local state, never manually constructed by the owner.

```text
aws_prepare.py bind --aws <cli> --config <private-config> --output <private-bind-evidence>
aws_prepare.py preflight --aws <cli> --config <private-config> --output <private-preflight>
aws_prepare.py deploy --aws <cli> --config <private-config> --approved-account <confirmed-account> --output <private-deployment>
aws_prepare.py readback --aws <cli> --config <private-config> --output <private-stack-evidence>
aws_prepare.py preflight --aws <cli> --config <private-config> --output <private-preflight>
aws_prepare.py cleanup --aws <cli> --config <private-config> --output <private-cleanup>
aws_prepare.py retire --aws <cli> --config <private-config> --output <private-retirement>
```

Deployment does not occur on login. Account, owner role, effective opt-out and
explicit approved-account equality must hold first. `readback` verifies exact
stack account/region/parameters and deterministic bucket/role identities. Private
configuration records actual client version/digest. AWS CLI environment credential
and endpoint overrides are removed, retries disabled, stderr reduced to error code.
All successful response bodies remain in private evidence.

The package intentionally contains no audio upload or StartTranscriptionJob call.
The live orchestration client/journal must still be bound and fault-tested in the
prospective successor; the existence of an IAM Start permission is not execution
authority. Real upload requires completed corpus, full 12/12 preflight, exact
manifest/cost/operator bindings and Phase A v0.2 commit/push/refetch proof.

## Canonical live preflight interface

The utility preserves the frozen twelve IDs. It reports individual observed
properties without claiming permission/retention review or corpus evidence exists.
Root integration combines fresh AWS evidence with private corpus and publication
evidence; no manual true/false switch makes all gates pass.

| ID | Requirement | Preparation result |
|---|---|---|
| 01 | Test identity, short-lived credentials | BLOCKED: owner login |
| 02 | No permanent credentials in Android | Source check; no Android changes |
| 03 | Exact eu-central-1 | Fixed in design; actual account/resources unverified |
| 04 | No automatic fallback | Implemented in command wrapper |
| 05 | Effective Transcribe opt-out | BLOCKED: account readback |
| 06 | Private bounded S3 | Template ready; actual policy/IAM verification pending |
| 07 | OD-11C-13..22 retention | Server watchdog ready; effective deletion proof pending |
| 08 | ADR-0011 KMS | Template ready; actual keys/policies unverified |
| 09 | Dataset admission | Supplied by private owned-corpus validator |
| 10 | Budget | Ceiling preserved; full actual quote/reservation pending |
| 11 | Cleanup | Implemented; account execution unverified |
| 12 | No unauthorized audio | Supplied by owner attestation/private manifest |

Offline tests exercise opt-out inheritance and rejection of drafts, account/region/
foreign-resource rejection, private-path guard, narrow IAM/template invariants,
CLI environment isolation and error redaction, and watchdog pre-deadline safety.
CloudFormation service validation, account access, synthetic deletion and live
Transcribe behavior are NOT_RUN, not an inferred PASS.
