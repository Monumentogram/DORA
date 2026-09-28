# DORA 6.2D preparation v0.1

Preparation only; Phase A remains PARTIAL and Phase B NOT_RUN.
The following is the exact public machine record. No private paths,
per-clip hashes, speech, account identifiers or credentials belong here.

```json
{
  "schema_version": "1.0",
  "package": "DORA_CLOUD_62D_PREPARATION_V0_1",
  "date": "2026-09-28",
  "source_baseline": "d8d5c90516b6a9afa96c21e703dfe7481e64e589",
  "branch": "chat/alpha-asr-runner-scope",
  "result": "PREPARATION_READY / HUMAN_ACTION_REQUIRED",
  "6.2D": "BLOCKED / LIVE_PROVIDER_EVALUATION_NOT_EXECUTED",
  "phase_a": "PARTIAL / PROTOCOL_FROZEN_DATA_AUTHORITY_AND_LIVE_OPERATOR_UNBOUND",
  "phase_a_v0_2": "NOT_CREATED_PREREQUISITES_INCOMPLETE",
  "phase_b": "NOT_RUN",
  "6.3": "NOT_RUN / BLOCKED",
  "aws": {
    "service": "Amazon Transcribe",
    "mode": "STANDARD_BATCH_FILE_ASR",
    "region": "eu-central-1",
    "fallback": false,
    "cli": "aws-cli/2.37.4",
    "installation": "OFFICIAL_SIGNATURE_VERIFIED_USER_LOCAL_NO_SYSTEM_INSTALL",
    "profile": "dora-62d-alpha",
    "account": "UNBOUND_PENDING_OWNER_LOGIN",
    "deployment": "NOT_RUN",
    "aws_service_calls": 0,
    "transcribe_attempts": 0,
    "aws_results": 0,
    "task_cloud_resources_created": 0,
    "task_asr_spend_usd": "0",
    "invoice_verification": "NOT_RUN",
    "live_operator": "NOT_BOUND; no upload/Start implementation in preparation package",
    "cleanup": "NO_TASK_AWS_RESOURCES_CREATED; future visible-copy deletion and separate retirement prepared"
  },
  "corpus": {
    "schema_version": "1.0",
    "status": "ACQUIRING",
    "inventory_sha256": "9382e335345135438ac1d8be4103f7631d5182b091bcfcd7bb3ce413006e7a4b",
    "manifest_sha256": null,
    "attestation_version": "dora-owned-corpus-attestation-v1",
    "attestation_sha256": "e5397cda98d84f558a1d9ef84bbc3711897cc7a48c9d09c749ea4e734e6acb23",
    "candidates": {
      "ru": {
        "read": 15,
        "spontaneous": 15,
        "noisy": 6
      },
      "en": {
        "read": 15,
        "spontaneous": 15,
        "noisy": 6
      }
    },
    "recorded": {
      "ru": 1,
      "en": 0
    },
    "verified_references": {
      "ru": 0,
      "en": 0
    },
    "selected_quality": {
      "ru": 0,
      "en": 0
    },
    "selected_noise": {
      "ru": 0,
      "en": 0
    },
    "timing_clips": {
      "ru": 0,
      "en": 0
    },
    "timing_words": {
      "ru": 0,
      "en": 0
    },
    "excluded_count": 0
  },
  "private_boundary": "Exact authored inventory, per-material hashes, audio, actual speech references, timing, identity/config and per-record manifest remain outside public Git. Only protocols/schemas/tools, counts and whole-inventory/manifest hashes are published.",
  "data_authority": "EXPLICIT_OWNER_TASK; local speaker confirmation required before any capture; no customer/third-party voices. MDC/Common Voice remain NOT_PROVEN for Cloud.",
  "selection": "Unchanged v0.1 SHA256(source_release + newline + source_audio_SHA256), first12 READ and12 SPONTANEOUS per language, first6 noisy per language; first6 selected READ for timing. No AWS output before selection.",
  "budget": {
    "currency": "USD",
    "hard_ceiling": 10,
    "owner_authority": "Explicit6.2D task",
    "transcribe_usd_per_second": "0.0001",
    "max_reserved_billable_seconds": 20000,
    "max_asr_reservation_usd": "2.00",
    "ancillary_and_tax_reserve_usd": "8.00",
    "max_total_attempts": 96,
    "estimate": "NOT_EVALUABLE_UNTIL_ACTUAL_MANIFEST_AND_ANCILLARY_TAX_QUOTE",
    "enforcement": "Reserve ceil(duration_s)*rate before EVERY potentially billed call incl uncertain submits/retries; cost ledger cannot release uncertain reservation without billing proof. Stop if next reservation plus ancillary upper bound exceeds10, or cannot bound charge. AWS budget alert is not a hard cap.",
    "ancillary": "Current Owner task authorizes a bounded dedicated test stack only after actual authenticated approved account scope. S3, KMS key lifetime, Lambda/watchdog schedule, transfer and tax must fit USD8; actual full quote and retirement proof remain required. Frozen v0.1 historical wording is unchanged.",
    "actual": "NO_TASK_ASR_REQUESTS; no free-credit assumption"
  },
  "preflight": [
    {
      "id": "PREFLIGHT-01",
      "requirement": "Test account and credentials",
      "status": "BLOCKED",
      "evidence": "Signed user-local AWS CLI v2 prepared; Owner account/role login required."
    },
    {
      "id": "PREFLIGHT-02",
      "requirement": "No permanent credentials in Android",
      "status": "PASS",
      "evidence": "No Android edits or permanent credentials in preparation artifacts."
    },
    {
      "id": "PREFLIGHT-03",
      "requirement": "Exact eu-central-1",
      "status": "BLOCKED",
      "evidence": "eu-central-1 fixed in code/template; actual account resources not verified."
    },
    {
      "id": "PREFLIGHT-04",
      "requirement": "No automatic fallback",
      "status": "PASS",
      "evidence": "Region and endpoint override rejection; no fallback/upload/Transcribe execution path."
    },
    {
      "id": "PREFLIGHT-05",
      "requirement": "Effective Transcribe opt-out",
      "status": "BLOCKED",
      "evidence": "Effective account Organizations policy cannot be checked before authentication."
    },
    {
      "id": "PREFLIGHT-06",
      "requirement": "Private bounded S3",
      "status": "BLOCKED",
      "evidence": "Private S3/ownership/least-privilege templates prepared; no deployed proof."
    },
    {
      "id": "PREFLIGHT-07",
      "requirement": "OD-11C-13..22 retention",
      "status": "BLOCKED",
      "evidence": "Server deadline watchdog prepared; actual expiry and immediate cleanup proof pending."
    },
    {
      "id": "PREFLIGHT-08",
      "requirement": "ADR-0011 KMS/encryption",
      "status": "BLOCKED",
      "evidence": "Separate single-region customer-managed symmetric KMS templates; no actual key/policy proof."
    },
    {
      "id": "PREFLIGHT-09",
      "requirement": "Individual dataset admission",
      "status": "BLOCKED",
      "evidence": "Owned acquisition authority/tool ready; real recordings, verified gold and manual timing not complete."
    },
    {
      "id": "PREFLIGHT-10",
      "requirement": "Budget envelope",
      "status": "BLOCKED",
      "evidence": "USD10 hard ceiling / USD2 ASR / USD8 ancillary-tax preserved; actual manifest and full quote absent."
    },
    {
      "id": "PREFLIGHT-11",
      "requirement": "Cleanup plan",
      "status": "PASS",
      "evidence": "Scoped cleanup implemented and offline tested; actual deletion NOT_RUN."
    },
    {
      "id": "PREFLIGHT-12",
      "requirement": "No user/customer audio",
      "status": "PASS",
      "evidence": "No customer/third-party audio used; Owner-only guided acquisition, no upload."
    }
  ],
  "host_validation": {
    "stage00": "7/7 PASS",
    "frozen_cloud_core": "26 tests PASS",
    "aws_offline": "13 tests PASS",
    "owned_corpus": "12 tests PASS",
    "recorder_server": "5 tests PASS",
    "preparation_guards": "5 tests PASS",
    "browser_audio_helpers": "PASS",
    "synthetic_browser_reference_timing": "PASS / NO_HARDWARE_MICROPHONE",
    "synthetic_browser_capture_conversion": "PASS / OSCILLATOR_ONLY_NO_HARDWARE_MICROPHONE",
    "gradle": "BUILD SUCCESSFUL;197tasks;5executed192up-to-date",
    "schema_validation": "JSON syntax and backend actual-inventory validation PASS; external JSON Schema library unavailable",
    "frozen_source_bindings": "17/17 PASS",
    "gate_dag": "39 gates PASS",
    "independent_adversarial_review": "PASS; corpus/recovery/locking/timing fixes re-reviewed; capture clock regression independently verified",
    "git_diff_check": "PASS",
    "json_md_parity": "EXACT_EMBEDDED_MACHINE_RECORD"
  },
  "frozen_gate_count": 39,
  "effective_gate_statuses": {
    "CLD-ADM-ARCH-001": "SATISFIED",
    "CLD-ADM-CONSENT-001": "SATISFIED",
    "CLD-ADM-DATA-001": "SATISFIED",
    "CLD-ADM-SCOPE-001": "SATISFIED",
    "CLD-ADM-GAPS-001": "SATISFIED",
    "CLD-ADM-PRIVACY-001": "SATISFIED_FOR_CLOSED_INTERNAL_ALPHA",
    "CLD-ADM-RETENTION-001": "SATISFIED",
    "CLD-ADM-CONTROL-001": "SATISFIED_FOR_CLOSED_INTERNAL_ALPHA",
    "CLD-ADM-EVALUATION-001": "OPEN",
    "CLD-ADM-PROVIDER-001": "OPEN",
    "CLD-ADM-ADMISSION-001": "BLOCKED",
    "CLD-ADM-API-001": "OPEN",
    "CLD-ADM-AUTH-001": "NOT_RUN",
    "CLD-ADM-CONSENT-RUNTIME-001": "NOT_RUN",
    "CLD-ADM-SECRETS-001": "NOT_RUN",
    "CLD-ADM-UPLOAD-001": "NOT_RUN",
    "CLD-ADM-CRYPTO-001": "NOT_RUN",
    "CLD-ADM-RETENTION-RUNTIME-001": "NOT_RUN",
    "CLD-ADM-DATA-RUNTIME-001": "NOT_RUN",
    "CLD-ADM-FAILURE-001": "NOT_RUN",
    "CLD-ADM-QUEUE-001": "NOT_RUN",
    "CLD-ADM-BACKGROUND-001": "NOT_RUN",
    "CLD-ADM-ADAPTER-001": "NOT_RUN",
    "CLD-ADM-COST-001": "NOT_RUN",
    "CLD-ADM-OBSERVABILITY-001": "NOT_RUN",
    "CLD-ADM-SECURITY-001": "NOT_RUN",
    "CLD-ADM-RESULT-001": "NOT_RUN",
    "CLD-ADM-MERGE-001": "NOT_RUN",
    "CLD-ADM-LOCAL-001": "NOT_RUN",
    "CLD-ADM-OFFLINE-001": "NOT_RUN",
    "CLD-ADM-DELETE-001": "NOT_RUN",
    "CLD-ADM-HARNESS-001": "NOT_RUN",
    "CLD-ADM-UX-001": "NOT_RUN",
    "CLD-ADM-HISTORY-001": "NOT_RUN",
    "CLD-ADM-EXPORT-001": "NOT_RUN",
    "CLD-ADM-OPERATIONS-001": "NOT_RUN",
    "CLD-ADM-SUPPLY-001": "OPEN",
    "CLD-ADM-EXIT-001": "NOT_RUN",
    "CLD-ADM-RELEASE-001": "OPEN"
  },
  "effective_status_counts": {
    "SATISFIED": 6,
    "SATISFIED_FOR_CLOSED_INTERNAL_ALPHA": 2,
    "OPEN": 5,
    "BLOCKED": 1,
    "NOT_RUN": 25,
    "PARTIALLY_SATISFIED": 0
  },
  "preserved": {
    "phase_a_v0_1": "UNCHANGED",
    "6.1": "PASS",
    "6.2": "PASS",
    "11.1C": "PASS / PRIVACY_RETENTION_CONTROL_PREREQUISITES_SATISFIED_FOR_CLOSED_INTERNAL_ALPHA",
    "android": "UNCHANGED",
    "recovery": "UNCHANGED",
    "stage5": "UNCHANGED",
    "PR86": "NOT_TOUCHED",
    "main": "NOT_CHANGED",
    "merge": "NOT_RUN"
  },
  "tool_bindings": [
    {
      "path": "docs/contracts/DORA_CLOUD_OWNED_CORPUS_INVENTORY_V1.schema.json",
      "sha256_lf_utf8": "5727bc17ba05e243c265e0d7619119e18cbfbef6789d33877dfcf291ca41a7e8"
    },
    {
      "path": "docs/contracts/DORA_CLOUD_OWNED_CORPUS_MANIFEST_V1.schema.json",
      "sha256_lf_utf8": "c89d77968369ecdcb0ff7a0fe9a5bec912519f26ff6b8c45263c45339c6bc475"
    },
    {
      "path": "docs/stage0/DORA_CLOUD_62D_AWS_PREPARATION_V0_1.md",
      "sha256_lf_utf8": "bb6ca88e3602942c06fe56ae997cd1ae33d90b4a6ebfd3750675dfb0677f55b4"
    },
    {
      "path": "docs/stage0/DORA_CLOUD_62D_PREPARATION_PLAN_V0_1.md",
      "sha256_lf_utf8": "e285d0d1b7109ddb2aa5655c144cb81c0df1b414814e8f15dda765d7d4d3d8f9"
    },
    {
      "path": "docs/stage0/DORA_CLOUD_OWNED_CORPUS_ACQUISITION_V1.md",
      "sha256_lf_utf8": "f3807993d4d6fac9f8fab71476b4ed15152e5cc1ea3c5664dd396eace63b39a8"
    },
    {
      "path": "tools/cloud62d_aws/aws_prepare.py",
      "sha256_lf_utf8": "717a898c950dc066e127a4d44b38e11d426d5811b4fbfa7f6f8063316ff04486"
    },
    {
      "path": "tools/cloud62d_aws/install-cli.ps1",
      "sha256_lf_utf8": "e2ea1d8625a11dacf15b24743286422f5453b8f3077ce599018c13d55a21a3a5"
    },
    {
      "path": "tools/cloud62d_aws/Login.cmd",
      "sha256_lf_utf8": "f942a62ca016d6a5af704adb561c221a61de3888e9dd1d8fff9b8fa0b28720d0"
    },
    {
      "path": "tools/cloud62d_aws/login.ps1",
      "sha256_lf_utf8": "c94bcbc0046879268fedd3d573e088dd61654463d004c889612efe97371dcb2b"
    },
    {
      "path": "tools/cloud62d_aws/opt-out-policy.review-only.json",
      "sha256_lf_utf8": "b232909bc1cca2193bf37e2af3521f202917088d4bc686bfd56fbf315899fc5c"
    },
    {
      "path": "tools/cloud62d_aws/test_aws.py",
      "sha256_lf_utf8": "7880a5eedceb7c4155245fab9baceb3dc856522029b64e790fde9b4184d9eb65"
    },
    {
      "path": "tools/cloud62d_aws/watchdog.py",
      "sha256_lf_utf8": "7dae15ed39b129476719aea1fefa19f3ee23e07988a4f89bbeb1e6398f90729e"
    },
    {
      "path": "tools/cloud62d_owned/audio.js",
      "sha256_lf_utf8": "8be772815b37d48ad9d4d3f03daa9ebc80e286ff59aedddf3061fd965ceb1769"
    },
    {
      "path": "tools/cloud62d_owned/capture-worklet.js",
      "sha256_lf_utf8": "c09e56c5f702e11fe0e69c386044f85d7f1ba2693fd4fe0e33766adc581cea1e"
    },
    {
      "path": "tools/cloud62d_owned/corpus.py",
      "sha256_lf_utf8": "d1ba32d6fc6f346ae8f5c6a5bdd25ef4294977100b3619e55b00fef668f36a7a"
    },
    {
      "path": "tools/cloud62d_owned/launch.cmd",
      "sha256_lf_utf8": "0367b112e8e89b7b5d6ac86c4c7c2e3d570ff76e4b2213b79a8e3e68565b9340"
    },
    {
      "path": "tools/cloud62d_owned/launch.ps1",
      "sha256_lf_utf8": "b9e76e46907a5ce043047b6dc5f9782d03eb4b64647b5226a4663e966f0ef0c3"
    },
    {
      "path": "tools/cloud62d_owned/README.md",
      "sha256_lf_utf8": "af57dca5c219653c8209297cfb64342dc176457e1166cc378799238c3cfffa5d"
    },
    {
      "path": "tools/cloud62d_owned/server.py",
      "sha256_lf_utf8": "bb1ce8d0108fe8efc4c08c95c85ad1dfc667304ff17227a194bda889922d9bb6"
    },
    {
      "path": "tools/cloud62d_owned/test_audio.mjs",
      "sha256_lf_utf8": "b5ece5a53996d38f2bd3a61ace4c35f1bcd499f5f3f8527adaa5211025525dfd"
    },
    {
      "path": "tools/cloud62d_owned/test_browser.mjs",
      "sha256_lf_utf8": "ee33e12449468e400c18eeaa9dbda220e0155705311a54125a0839f7eebd15e0"
    },
    {
      "path": "tools/cloud62d_owned/test_browser_fixture.py",
      "sha256_lf_utf8": "d803b553f3861f1f2f0092a34e5142c7b8ba3e7f3d709e4dce50634d77929e6b"
    },
    {
      "path": "tools/cloud62d_owned/test_capture_browser.mjs",
      "sha256_lf_utf8": "f1612a34c3fee6debbb17da868e92547768927567fcd99ef3aee23f406fd2cfc"
    },
    {
      "path": "tools/cloud62d_owned/test_corpus.py",
      "sha256_lf_utf8": "6651ef11d5d6fc01c2f3149019dd75ff9ff43ae91aac1ebc31b98ae728ee7790"
    },
    {
      "path": "tools/cloud62d_owned/test_server.py",
      "sha256_lf_utf8": "310244f7d5e396382c4a2e00a61b93a20dc5f2d3f1d53ab60dd51445bd93035d"
    },
    {
      "path": "tools/cloud62d_owned/ui.html",
      "sha256_lf_utf8": "8cfa7d387cd676b544d04680c85fc9b6e9d0da328d72c92dc6bea5bab6465f30"
    },
    {
      "path": "tools/cloud62d_owned/ui.js",
      "sha256_lf_utf8": "31fb9c6722ae97bddec0b7fa0f145703af88fa94d0db4baf5df58c966961507d"
    },
    {
      "path": "tools/cloud62d_prepare.py",
      "sha256_lf_utf8": "77862f21e08e328e7773b6b03b2249131ceec574edf7cb429ea0c04b535f37ff"
    },
    {
      "path": "tools/test_cloud62d_prepare.py",
      "sha256_lf_utf8": "ccefe7fd3927df190a7dd2af0209ea2c27687d095cbaed9c04d6318ba1426a23"
    }
  ],
  "publication": "Enclosing preparation commit is publication identity; push and refetch exact HEAD separately. This is not permission for AWS evaluation.",
  "resume": "After human acquisition/login validate private artifacts, actual scope/effective opt-out/permissions/retention and full cost; bind and fault-test exact live operator/journal. Only a complete successor Phase A may PASS; commit/push/refetch before first real upload/Transcribe request. Preserve all outputs/failed attempts; do not execute6.3."
}
```
