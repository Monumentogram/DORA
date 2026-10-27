# Owner-only Alpha deployment package — NOT APPLIED

See the [Stage18.1D.2 report](../../../STAGE18_1D2_MINIMAL_AWS_INFRASTRUCTURE.md)
for the decision, costs, evidence and approval boundary. Every AWS write and every
host command below belongs to a separately authorized deployment task.

## Local validation (no AWS)

From repository root, using Python 3.12 and the backend's pinned environment:

```sh
python3.12 -m venv /tmp/dora-cfn
/tmp/dora-cfn/bin/pip install -r backend/deployment/alpha/requirements-tools.txt
/tmp/dora-cfn/bin/cfn-lint -t backend/deployment/alpha/stack.json -r eu-central-1
cd backend
AWS_EC2_METADATA_DISABLED=true .venv/bin/python -m unittest discover -s tests -v
```

Windows equivalents use `Scripts/python.exe` and `Scripts/cfn-lint.exe`.
Schema validation does not simulate IAM, deploy Linux, or exercise Transcribe.

## Prerequisites and private parameters

1. Record reviewed source SHA and current prices. Use an approved **non-root**
   administrator profile, separate from the new EC2 workload role. Confirm effective
   opt-out again. Do not reuse legacy proof credentials or create access keys.
2. Copy `parameters.example.json` to a private operator directory outside Git.
   Supply existing Frankfurt VPC, a public subnet in that VPC, official pinned
   Ubuntu 24.04 LTS amd64 AMI, existing SSH public-key pair name, owner/operator
   public IPv4 `/32`, private notification address and $20 monthly account budget.
   A single approved VPN egress can serve a changing owner IP; do not open `/0`.
3. Read-only `describe-subnets`, route tables, VPC DNS settings and image metadata:
   subnet/VPC must match, route `0.0.0.0/0` must target an Internet Gateway, DNS must
   resolve AWS endpoints; AMI must be official Canonical, available, amd64, EBS
   root `/dev/sda1`, <=12 GiB root, with Python 3.12. No private/NAT-only subnet.
   Confirm existing key-pair provenance and capacity quota. Pin the resolved AMI ID
   rather than silently following `latest`. Ubuntu archives must use HTTPS because
   this security group permits outbound TCP 443 only (VPC resolver DNS is separate).
4. Choose an owner-controlled DNS hostname and a trusted TLS certificate with an
   automated renewal mechanism (DNS-01 can avoid port 80). Existing DNS has no new
   AWS fixed cost. A new domain/hosted zone is **not** silently purchased or included
   in this stack. Confirm renewal cost and authority before deployment if needed.
5. The operator needs CloudFormation and creation/update permissions for exactly
   this package's resource types, plus ability to pass the new runtime role **to
   EC2 only** (`iam:PassedToService=ec2.amazonaws.com`). The runtime itself has no
   `iam:PassRole`. IAM, KMS administration and budget changes stay with the operator.
   Review SCPs, RCPs, boundaries, existing broad identity policies and key policies.
   Do not attach AdministratorAccess to the runtime role.

The KMS account-principal statement enables IAM delegation and root recovery; it
does not make root the application identity. Key administration is authorized by
the operator's existing IAM policy. The workload has only explicit usage/read
permissions on the two keys. No new admin role or Transcribe data-access role is
required. Key policies have no public or cross-account principal.

## Future resource deployment commands — require separate owner approval

Run in a private operator shell, from repository root. `PRIVATE_PARAMS` is a
private file path, `AWS_PROFILE` is an approved administrative profile; do not log
parameter files or full stack outputs publicly. The template is under 51,200 bytes
and needs no deployment artifact bucket.

```sh
export AWS_PROFILE=APPROVED_ADMIN_PROFILE
export AWS_DEFAULT_REGION=eu-central-1
STACK=dora-owner-alpha
PRIVATE_PARAMS=/private/dora/parameters.private.json
aws cloudformation create-change-set --region eu-central-1 \
  --stack-name "$STACK" --change-set-name owner-reviewed --change-set-type CREATE \
  --template-body file://backend/deployment/alpha/stack.json \
  --parameters "file://$PRIVATE_PARAMS" --capabilities CAPABILITY_IAM
aws cloudformation wait change-set-create-complete --region eu-central-1 \
  --stack-name "$STACK" --change-set-name owner-reviewed
aws cloudformation describe-change-set --region eu-central-1 \
  --stack-name "$STACK" --change-set-name owner-reviewed
# Execute only the reviewed change set under the separately approved deployment scope.
aws cloudformation execute-change-set --region eu-central-1 \
  --stack-name "$STACK" --change-set-name owner-reviewed
aws cloudformation wait stack-create-complete --region eu-central-1 --stack-name "$STACK"
aws cloudformation update-termination-protection --region eu-central-1 \
  --stack-name "$STACK" --enable-termination-protection
umask 077
aws cloudformation describe-stacks --region eu-central-1 --stack-name "$STACK" \
  --query 'Stacks[0].Outputs' --output json > /private/dora/outputs.private.json
cd backend
.venv/bin/python -m deployment.alpha.render_config \
  --outputs /private/dora/outputs.private.json --owner APPROVED_OWNER_ID \
  --destination /private/dora/config.private.json
```

The renderer has no AWS calls, refuses overwrite and refuses destinations inside
this repository. `live_enabled` is always false. Apply Windows ACLs explicitly if
using Windows: POSIX mode 0600 is not a Windows ACL guarantee. Do not commit owner
IDs, endpoints, parameter values, stack outputs, private keys or credentials.

No UserData downloads or launches application code. Infrastructure creation alone
does not start DORA, create invitations or authorize a paid job. Initial alarm may
be ALARM until setup completes: missing telemetry deliberately is not healthy.

## Future host setup — no recording data until all checks pass

1. Verify SSH host-key fingerprint out of band and connect only from OwnerCidr.
   Install Python 3.12/venv, nginx and supported TLS renewal tooling from official
   HTTPS repositories. Verify the reviewed source SHA; install only backend source,
   its locked requirements and deployment tools to `/opt/dora/backend`, excluding
   tests, fixtures, private files and the Git directory. Files owned by root,
   read-only to the dedicated `dora` OS account. Create its venv and install
   `requirements.lock.txt`. No ASGI extra workers or Docker replicas.
2. Match the **exact stack DataVolumeId** to Nitro NVMe serial/`/dev/disk/by-id/`
   with `lsblk -f` and `udevadm info`. Do not assume `/dev/sdf` equals a particular
   nvme device. Format ext4 **only for that new blank volume**, never on recovery.
   Persist the filesystem UUID mount at `/var/lib/dora` in `/etc/fstab` without
   `nofail`; mount and verify `findmnt /var/lib/dora`. Set directory owner dora:dora,
   mode 0700. The separate 8-GiB encrypted EBS holds ledger, WAL, lock and custody key.
   Disable swap and core dumps; do not snapshot/back up either volume while it may
   contain temporary audio. Root volume is 12-GiB encrypted gp3; no payload staging.
3. Install the disabled config as `/etc/dora/config.private.json`, root:dora 0640;
   `/etc/dora` 0750. From backend working directory, run as dora:
   `.venv/bin/python -m dora_cloud --config /etc/dora/config.private.json init-key`.
   The key path is `/var/lib/dora/custody.key`, mode 0600. Preserve it together with
   the ledger across host replacement. Never regenerate it for an existing ledger.
4. Install the existing `dora-api.service.example` and
   `dora-watchdog.service.example` as units. Install `persistent-data.conf` as
   `storage.conf` in **both** units' `.service.d/` directories. Install the health
   unit/timer, journald drop-in; write root:dora 0640 `/etc/dora/health.env` containing
   only `DORA_STACK=dora-owner-alpha`. Run `systemd-analyze verify` on installed units
   and `systemctl daemon-reload`. Use `systemctl enable --now dora-watchdog.service
   dora-health.timer`; keep API stopped and admission disabled during infrastructure
   setup. Watchdog only polls/cleans durable intent, never starts jobs.
5. Install TLS privately under `/etc/dora/tls`; replace nginx hostname/certificate
   paths. Remove the distribution default HTTP site; only approved 443 ingress,
   API at 127.0.0.1:8080. Use the updated nginx example: TLS1.2+, request/response
   disk buffering disabled, 100-KiB body limit, no access logs. Keep error logs
   content-free, rotate daily with 1-day retention, never collect request bodies.
   Run `nginx -t`; validate certificate chain/hostname and renewal. No self-signed
   production exception, public S3 access or exposed 8080.
6. Confirm SNS email subscription and budget recipients. Verify an alarm reaches
   the operator when the health timer is deliberately stopped; resume and observe
   recovery. Confirm watchdog automatic restart on deliberate process termination
   **before any audio**; exercise a missing-mount check using a disposable empty
   setup, never unmount an active ledger. Health reports only one boolean: watchdog
   active, EBS mounted, >512MiB free, and no remaining chunks/provider cleanup within
   one hour of deadline. Database errors -> unhealthy; missing metric -> alarm.
   A hung watchdog can remain systemd-active; deadline monitoring is the additional
   guard, not a proof of continuous progress. Owner must respond within the hour.
7. Under the EC2 workload role (no AWS_PROFILE/access keys/metadata disable), verify
   STS account and non-root role, no extra attached policies, IAM conditions and
   permission boundaries. Make a **private, service-unused** config copy with
   `live_enabled=true` solely for `python -m dora_cloud --config PATH preflight`.
   This command is Get/Describe only; it never opens Store, uploads or starts jobs.
   Keep service config false and API stopped. Inspect bucket controls/key rotation,
   effective opt-out, billing/quota and alarm status. No `Start` as a permission probe.
8. Record sanitized deployment and preflight receipts. A further explicit Stage18.1E
   approval is required before invitation/active API/synthetic uploads/paid jobs.
   Do not retarget this ledger to other buckets, keys or owners. Do not use POCO.

## Rollback and recovery

- Before content exists: failed stack rollback can leave **retained** keys,
  buckets/policies and EBS. Inventory stack events and physical IDs privately;
  do not keep retrying creation and accumulating retained resources. Inspect billing.
- During/after usage: close ingress/new admission first (`live_enabled=false`,
  stop API). **Keep watchdog, role, keys, buckets, disk and alarm operational.**
  Wait for provider terminal states, reconcile every pending attempt, confirm
  input/output/job deletion with appropriately authorized Get/List calls. A running
  Transcribe job is not cancellable by terminating EC2. Ambiguous starts stay pending.
- Recover a failed host in the **same AZ**, attaching the retained data volume to
  one replacement host only. Use the original config/namespace and custody key.
  Do not format, restore an old snapshot, run two watchdog hosts, or create a new
  ledger. Start cleanup before API; operator intervention is required within the
  remaining retention window. Lifecycle is a last-resort backstop, not a 24h SLA.
- Application rollback may use the approved previous backend SHA only after checking
  schema compatibility; retain the new equivalent lifecycle admission fix if the
  returned S3 representation requires it. Restore original state, never reset quota.
- Stack deletion is **not** a data-erasure procedure. Only after cleanup and approved
  metadata-retention disposition: disable stack termination protection and instance
  API termination protection; then delete the stack. Root EBS, VM, EIP, role/profile,
  SG, alarm/topic and budget are removed. Keys, buckets **and policies**, data EBS
  remain due to Retain/UpdateReplacePolicy; these continue costing money.
- Separately inventory/authorize retirement of retained resources. Empty buckets
  including parts, verify exact job absence, satisfy 30-day result/90-day consent and
  tombstone requirements before erasing custody. Schedule CMK deletion with the
  approved waiting period only after dependent ciphertext is gone; never use disabling
  a shared key as per-record deletion. Do not create audio backups to simplify rollback.
