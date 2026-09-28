# DORA 6.2D — prospective v4 recovery diagnostic v0.2

This publication precedes v4 deployment, its direct-S3 proof and every new Start.
It contains no evaluation result and does not admit a provider. Baseline:
`37201eee0533acab032824a4a7b7050a394ea5b4` on `chat/alpha-asr-runner-scope`.

## Independently reviewed policy repair

AWS IAM specifies that an explicit Deny with StringNotEqualsIfExists also denies
when its key is missing. Committed v3 therefore denies a PutObject authorization
context without SSE headers. Whether Transcribe used that context in the five
historical failures remains a hypothesis until live evidence isolates the path.
The preserved unpublished candidate used cross-header Null guards and already
allowed both absent; this revision makes the requested semantics explicit.

Output now uses five encryption Denies: present incorrect algorithm, present
incorrect CMK, algorithm-only, key-only, and SSE-C. Both absent and the exact full
SSE-KMS pair are allowed by these encryption conditions for an otherwise authorized
writer. Bucket default encryption remains the exact approved Output customer CMK.
TLS and the inclusive write deadline still deny independently. Input requires the
original explicit full pair. Roles, trust, keys, region, prefixes and retention
controls are unchanged. No new Allow or broad permission is introduced.

Sources: [IAM missing-key semantics](https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_elements_condition_operators.html#Conditions_IfExists),
[condition conjunction](https://docs.aws.amazon.com/IAM/latest/UserGuide/reference_policies_condition-logic-multiple-context-keys-or-values.html),
[S3 default encryption](https://docs.aws.amazon.com/AmazonS3/latest/API/API_ServerSideEncryptionByDefault.html),
[Transcribe output-key caller and DataRole requirements](https://docs.aws.amazon.com/transcribe/latest/APIReference/API_StartTranscriptionJob.html),
[S3 SSE-KMS permissions](https://docs.aws.amazon.com/AmazonS3/latest/userguide/UsingKMSEncryption.html).

## Observation barriers and ordered decision tree

1. Publish this code/protocol atomically; push, refetch and prove exact clean HEAD.
2. Deploy only this v4 template and require CloudFormation UPDATE_COMPLETE.
3. The one-shot v4_proof operator writes ONE project-authored non-personal object
   within the exact Output probe prefix without SSE headers. HeadObject must report
   aws:kms and the exact approved Output CMK. Delete it and verify absence of current
   object, versions/delete markers and multipart uploads. Failure means
   V4_DEFAULT_KMS_PATH_FAILED: STOP, no Transcribe.
4. Require fresh identity/opt-out, exact live resource policies, retention proof,
   empty task resources and both actual principal Output KMS policy proofs. Assess
   Encrypt/Decrypt/GenerateDataKey in identity AND key policy; DescribeKey alone
   is insufficient. The direct probe proves operator write use; D1 proves the
   target service path. Any remaining effective-policy boundary stays unproven.
5. Run at most THREE new synthetic Starts on the same three-second non-personal
   silent WAV, one input object, same language and authenticated bounded operator.
   D1 uses Amazon Transcribe STANDARD_BATCH_FILE_ASR in eu-central-1, exact private
   DORA input/output, exact OutputEncryptionKMSKeyId and JobExecutionSettings with
   AllowDeferredExecution=false and the exact DataAccessRoleArn. Successful D1
   immediately stops diagnostics; retrieve and validate identity/timestamps and
   clean input/output/terminal job with independent readback.
6. Only after definite D1 failure, D2 keeps input/language/caller/DataRole but omits
   OutputBucketName, OutputKey and OutputEncryptionKMSKeyId. Service-managed output
   is permitted solely for this synthetic diagnostic. Success classifies
   OUTPUT_PATH_SPECIFIC and stops; no D3 and no real voice authorization.
7. Only after definite D2 failure, D3 also omits the whole JobExecutionSettings.
   It uses the operator's exact input authority. Success classifies
   DATA_ACCESS_ROLE_PATH_SPECIFIC; failure classifies CORE_OR_INPUT_PATH_UNRESOLVED.
   No fourth speculative Start. Uncertain submission, integrity failure or pending
   cleanup stops new work and requires reconciliation without resubmission.

If D1 fails, collect exact request time/error/request ID where exposed, narrow
S3 prefix data events if necessary, existing KMS events, STS and applicable IAM
evaluation evidence. No AdministratorAccess, public bucket, encryption downgrade,
blanket KMS permission or retention relaxation. AWS Support is an escalation only
if AWS-side denial remains opaque. A further Start needs a NEW prospective version.

The four historical RU failures and one historical synthetic failure remain
immutable; accepted historical jobs/transcripts/WER measurements remain zero.
EN real audio has not been sent. Historical Phase A v0.2 and result are untouched.

## Subsequent owner-only scope

Only successful D1 with verified cleanup permits preparing a separate complete
DORA_CLOUD_EVALUATION_PHASE_A_BOUNDED8_V0_3. It must be committed/pushed/refetched
BEFORE resumed real speech; no real recovery Start occurs under this protocol.
The unchanged manifest is 27ea1593a46c9c17de0a568df6537dbe55df56c4def59559d758cb9f07ecbe65:
RU 2 READ + 2 SPONTANEOUS, 179 words/150.960 seconds; EN 4 BASIC READ,
181 words/129.560 seconds. No recording/reference replacement or best-of selection.
The first resumed primary is ru-read-01 and infrastructure failure stops the rest.

Any later admission may be only OWNER_ONLY_CLOSED_INTERNAL_ALPHA, contingent on
all eight attributable completions, RU normalized micro WER <=20%, EN READ <=18%,
mandatory integrity/technical/replaceability evidence, current privacy/security,
budget and verified cleanup. No cross-language averaging or measured-failure waiver.
EN spontaneous, noise/speakerphone, other speakers and timestamp accuracy remain
NOT_EVALUATED. Timestamps may be provider metadata with NO_WORD_LEVEL_ACCURACY_PROMISE.
PRE_ADDITIONAL_INTERNAL_TESTER_PROVIDER_REVALIDATION is mandatory before a second
real speaker. External Privacy/Legal remains unchanged and outside frozen39.

6.2D remains BLOCKED / LIVE_PROVIDER_EVALUATION_NOT_EXECUTED; bounded8 INCOMPLETE.
EVALUATION/PROVIDER OPEN; ADMISSION BLOCKED. 39 gates unchanged: 6 SATISFIED,
2 SATISFIED_FOR_CLOSED_INTERNAL_ALPHA, 0 PARTIALLY_SATISFIED, 5 OPEN, 1 BLOCKED,
25 NOT_RUN. 6.3 NOT_RUN, PR86/main/Stage5 untouched.

The following machine record is authoritative for bindings and dispatch limits.

```json
{
  "schema_version": "0.2",
  "scope": "SYNTHETIC_NON_SPEECH_INTEGRATION_DIAGNOSTIC_ONLY",
  "status": "PROSPECTIVE_SYNTHETIC_ONLY",
  "all_synthetic": true,
  "maximum_dispatches": 3,
  "maximum_duration_seconds": 3,
  "reserved_seconds": 9,
  "historical_primary_failed_starts": 4,
  "historical_synthetic_failed_starts": 1,
  "reservation": {
    "prior_failed_seconds": 152,
    "future_recovery_seconds": 3893,
    "diagnostic_envelope_seconds": 24,
    "historical_synthetic_seconds": 3,
    "new_ladder_seconds": 9,
    "total_reserved_seconds": 4069,
    "usd_per_second": "0.0001000000",
    "asr_upper_usd": "0.4069000000",
    "ancillary_tax_upper_usd": "8",
    "total_upper_usd": "8.4069000000",
    "cap_usd": "10"
  },
  "fixture": "48000 zero-valued mono PCM16 frames at 16000 Hz; WAV container",
  "same_input_object_language_and_caller": true,
  "automatic_retries": 0,
  "resume_or_fourth_start": false,
  "steps": [
    {
      "step": "D1",
      "output": "EXACT_DORA_BUCKET_KEY_CMK",
      "execution_settings": "AllowDeferredExecution=false; exact DataRole",
      "success": "DORA_TARGET_SERVICE_FLOW_VERIFIED; STOP"
    },
    {
      "step": "D2",
      "requires": "D1 definite failure",
      "output": "SERVICE_MANAGED_SYNTHETIC_ONLY",
      "execution_settings": "unchanged from D1",
      "success": "OUTPUT_PATH_SPECIFIC; STOP"
    },
    {
      "step": "D3",
      "requires": "D2 definite failure",
      "output": "SERVICE_MANAGED_SYNTHETIC_ONLY",
      "execution_settings": "OMITTED; operator input authority",
      "success": "DATA_ACCESS_ROLE_PATH_SPECIFIC; STOP",
      "failure": "CORE_OR_INPUT_PATH_UNRESOLVED"
    }
  ],
  "runtime_gates": {
    "preflight": "fresh read-only exact config/template and both principal KMS policy proofs",
    "direct_s3_checks": [
      "output_put_no_sse_headers",
      "output_head_exact_cmk",
      "output_cleanup_objects_versions_multipart"
    ],
    "proofs_created_after_publication": true
  },
  "success": "COMPLETED identity-matched retrieved result with valid timestamp structure and verified cleanup",
  "human_speech_unlocked": "ONLY_D1_SUCCESS_AND_VERIFIED_CLEANUP",
  "quality": "NOT_EVALUATED",
  "provider_admission": "NOT_ESTABLISHED",
  "binding": {
    "config_sha256": "da8ffa54edb13be5bf62e95a6f02a163a42d4c00c5622375d690732ad49df27f",
    "aws_client_sha256": "bed3d82cf8dd046d0c134b7c5510930a44118d9a27fe5af7a9e56abc7dde8bc8",
    "aws_config_sha256": "a00f70ff402c81f68f3b92d5f637a933eec2b1b954c3cbb3f2339ba8a97c7f9d",
    "template_sha256": "805bc62223d47a448ea0d1b9437c805abfe5be97c8269f9af182202a48226ce3",
    "resources_sha256": "3f38b48c243a18cc4e0262f51f853fbc15fc0a86a9c103c050d9fb26fd124a24",
    "canonical_journal_path_sha256": "f3275bbd1bfe45ff6656583486c420ec363f3870a093a5a3b8001e9540d364ba",
    "historic_synthetic_receipt_sha256": "606d1f62deb0303250c51a3b118e19f36a5c7123a8b263182627b7a8603e1941",
    "historic_synthetic_journal_sha256": "2cf23e292c07df5ea39f3191a26b0a4d8fdbc66bdad03d06fa14744cffdb26ef",
    "prior_primary_result_sha256_lf": "5602414c7c1476d973358bc16666b9f5b5ff396b5b93d0a4897d43c0104fd137",
    "source_sha256_lf": {
      "tools/cloud62d_aws/diagnostic_run.py": "fd6f08abbe7daa9063799dae5595659e5e9060ea16fcf57ccaee734d459474af",
      "tools/cloud62d_aws/live_run.py": "a3bb95b40f22f71ff6a81077b6d9f5b49943e96622214b6dcfeafa0444e2205e",
      "tools/cloud62d_aws/aws_prepare.py": "c365c50dd8332fb37d798dce1cd196db7cdec3634883b3031b0787dce6d764c6",
      "tools/cloud62d_aws/watchdog.py": "34ace6878f70be9d995e8423d97414a95b7c9f0eab2c1de24ccb8e216e3fba56",
      "tools/cloud62d_owned/corpus.py": "29dbd5d04c6cb26d210a61d09d152f76dcecc879020a1fe0618212e520bbad8a",
      "tools/cloud62d_owned/server.py": "7b13acda28242d745cf12ef736a9bdf76f0b80f75789f5bc839270aeaa898a60",
      "tools/cloud62d_prepare.py": "e5cf4f09694e2b660e3aa24c58444551a75d4a9a1d3e5036d1c26cf8682664c3",
      "tools/cloud62d_aws/diagnostic_ladder.py": "c2358812cc7686b1de106ef3128cbd9776c7c865358fe58a52872adce32e16da",
      "tools/cloud62d_aws/recovery_run.py": "26a9af60acf1e9602859b757902f939e183e24196d51933e5edae335923af6b7",
      "tools/cloud62d_aws/v4_proof.py": "b96a8e141c556cadeedc748ed0ae4e48c0458591876c1896ef2fef6c1745e440"
    }
  }
}
```
