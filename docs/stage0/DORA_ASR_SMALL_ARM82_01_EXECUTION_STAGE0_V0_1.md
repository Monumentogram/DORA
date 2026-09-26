# ASR-SMALL-ARM82-01 — execution hard blocker

**BLOCKED / INSUFFICIENT_FRESH_HOLDOUT_AFTER_PARTICIPANT_EXCLUSION**.
The physical POCO M5 passes the ISA precondition, but the frozen source has **at most 8 eligible
new RU records**, versus 24 required. Even before duration and other eligibility checks,
only **9 RU records** remain outside previously selected participants. EN has at most 69 after the verified exclusions.
The one measured campaign was never claimed or invoked. This is a prelaunch data-contract
blocker; no measured candidate disposition exists and no ARM82 performance conclusion follows.

Authority: accepted commit `8181ca3d4ae8ad5757b4f6c4f1e1a03ec7205a7e`,
the [prospective contract](DORA_ASR_SMALL_RESOURCE_POSTMORTEM_NEXT_EXPERIMENT_STAGE0_V0_1.md)
and its [frozen JSON](../evidence/poc-asr-001/asr-small-resource-postmortem-next-experiment-stage0-v0.1.json).
Contract canonical SHA-256: `32b2e6516a600f656f4e414e291b871327821c481c57732cbb41b43b401d789a`.
The [execution evidence](../evidence/poc-asr-001/asr-small-arm82-01-execution-stage0-v0.1.json)
records aggregate audit digests, the baseline probe source/identity and every unevaluated gate.

## Proven data blocker

The ARM82 contract says: "Exclude using legitimately available pseudonymous metadata;
otherwise disclose limitation without re-identification." Its shortage rule is
`STOP_NO_SUBSTITUTION_OR_RULE_RELAXATION`. The ARM82 report explicitly applies participant-overlap
exclusion alongside exclusions of previously selected evaluation material. That experiment-specific
requirement applies here; the older SMALL development-only wording cannot silently replace it.

Both retained archives matched their previously admitted exact byte sizes and SHA-256 identities.
The complete pinned test inventory contains 385 RU and 357 EN records. Exact BASE48 and SMALL48
manifest identities were verified, including every one of the 29 unexecuted SMALL selections.
Lawfully retained source indexes contain nonempty pseudonymous participant metadata for every
test record. The selective byte parser kept only source identity, duration, split and participant
metadata; it skipped transcription, prompt and demographic fields without decoding/retaining them.
Participant comparisons occurred in memory; no individual identity or link is published.

| Audit quantity | RU | EN |
|---|---:|---:|
| Complete test inventory | 385 | 357 |
| Unique participants | 12 | 62 |
| Participants represented by BASE48 + SMALL48 | 7 | 25 |
| Records overlapping those participants | 376 | 276 |
| Records remaining before other exclusions | 9 | 81 |
| Prior selected source/audio records excluded | 48 | 48 |
| Base-ineligible records in whole inventory | 171 | 113 |
| Remaining after verified exclusions and eligibility (upper bound) | **8** | **69** |
| Required | **24** | **24** |

Reason counts overlap and must not be added. No duplicate-audio group remained in the final
pool. A separate audit of all other possible prior-use ledgers was not needed for this
impossibility proof and was not performed: 8/69 are upper bounds, not final untouched-pool
admission. Applying any additional exclusion cannot raise the RU upper bound of 9 to 24.
An independent implementation reproduced both pool digests/counts and checked all 742 source
durations; it also found zero duplicate-audio groups across the complete inventory.
This proof does not depend on model behavior, ranking, transcripts or a selected slow case.
No holdout selection, audio/reference materialization, new manifest or pre-inference freeze
was performed. No previous evaluation sample was reused. The absence of new manifest/freeze
identities is explicit in JSON, not filled with historical identities.

Rebuilding, changing paths/ACLs or recovering the same archive cannot supply missing eligible
records: the admitted complete test pool and exact archives have already been checked.
Continuing would require relaxing participant exclusion/count or changing the
admitted release/split/source authority. None is authorized inside this frozen experiment.
No such change, substitute sample or fallback experiment was made.

## ISA and build state

Physical POCO M5, model 22071219CG, device stone, Android 14/API34, arm64-v8a;
emulator property absent. A standalone baseline `armv8-a` probe called `getauxval(AT_HWCAP)`.
It used NDK 28.2.13676358 / Clang 19.0.1, API28, `-O0`, both vectorizers disabled,
and linked only `libc.so`. It loaded no ASR library or model.

`AT_HWCAP = 1155071 = 0x119fff`, errno 0; required mask 1050114 (`0x100602`):

| Capability | Observed masked value | Result |
|---|---:|---|
| HWCAP_ASIMD | 2 | PRESENT |
| HWCAP_FPHP | 512 | PRESENT |
| HWCAP_ASIMDHP | 1024 | PRESENT |
| HWCAP_ASIMDDP | 1048576 | PRESENT |

**ISA PASS**. Probe: 6600 bytes, SHA-256
`c7d09c7eb9c7779618ce559987aaa1e7e9b84e5795df031a333d24eaaa58ffb1`.
Baseline source/compiler/disassembly pins and compile arguments are recorded in JSON.
The probe ELF is AArch64 with 0x4000 LOAD alignment. Its process exited, its exact device-side
file/directory were removed, and absence was verified. This proves neither ASR binary admission
nor 16-KiB ASR runtime compatibility.

**ARM82 runtime build NOT STARTED** after the data blocker. No successor execution binding or
ARM82 binaries were created; native artifact sizes/hashes, specialized kernel emission,
dependency closure and ASR 16-KiB checks therefore remain **NOT_EVALUATED**.
The unchanged prospective build is whisper.cpp v1.9.4 at
`927cfce34f31707e17f2bff35c349632fb9e2c3a`, NDK 28.2.13676358, Clang 19.0.1,
CMake 3.22.1, Ninja 1.10.2; only `GGML_CPU_ARM_ARCH=armv8.2-a+fp16+dotprod` differs.
SMALL q5_1 model bytes, CPU-only/four threads, flash attention, decoding and all other options
remain frozen; no build/configuration sweep took place.

## Campaign, evaluation and cleanup

API invocations **0/1**; primary attempts **0**; retries **0**; RU **0/24**, EN **0/24**.
No claim, journal or measured terminal aggregate was created. Warmup, codec check and holdout
inference are all zero. The evaluator was not invoked: candidate disposition
**NOT_FORMED / EVALUATOR_NOT_INVOKED**. Campaign cleanup is not applicable; capability-probe
cleanup is **VERIFIED**, with no task-owned device artifacts remaining.

RU/EN normalized WER, weighted/p95/maximum RTF, cold load, PSS, native heap, OOM and thermal
measurements are unavailable; every gate is **NOT_EVALUABLE**. No absence-of-OOM or thermal
PASS is inferred from the lack of execution. Telemetry is **0/48**, incomplete.
Unchanged gates: RU <=20%, EN <=18%; weighted RTF <=2, p95 <=4, maximum <=6;
cold load <=15 s; PSS <=1610612736 bytes; heap <=1342177280 bytes; evidenced OOM=0;
thermal <3; complete 48-case telemetry. No acceptance criterion was changed.

Historical SMALL remains **VALID_FAIL / REJECTED_FOR_BOUNDED_ALPHA_RESOURCE_GATE** and its
19-attempt campaign remains incomplete. This blocker neither reclassifies nor reruns it.

## Publication and stage effect

Only this report, its JSON, Stage Status and Implementation Backlog change. Historical
operators/results/frozen contract and thresholds, production Android and Recovery remain
byte-identical. Earlier status/backlog bytes are preserved under an additive current entry.
Private historical campaign evidence remains unchanged. PR #86 is OPEN, DRAFT, UNMERGED,
untouched. One atomic result commit and normal push; no merge.

Validation PASS: strict JSON, contract/gate digests, privacy against 742 inventory identities,
seven local Markdown references, exact scope, `git diff --check`, three synthetic selective-parser
fixtures and all seven Stage00 checks. All 611 unchanged baseline files and 308 private historical
campaign files retain their bytes; both status/backlog additions preserve previous bytes.
Publication readback supplies the final commit/remote identity; no Android product build is
claimed or required for this documentation-only hard-blocker publication.

ASR-SMALL-ARM82-01: **BLOCKED / NOT_EXECUTED**. Stage 5 and Group B: **NOT_PASS**.
POC-ASR-001: **BLOCKED / NOT_READY**. 5.6C.2B remains **INCOMPLETE**.
Recovery remains **0D.6 = ALPHA CLOSED / FULL OPEN**.

Counts: old holdout reruns 0; configuration sweeps 0; thread sweeps 0; tuning 0;
threshold changes 0; model changes 0; decoding changes 0; unauthorized retries 0;
second campaign invocation 0; Recovery changes 0; PR #86 changes 0; merges 0.
ASR inference 0; baseline capability-probe execution 1; specialized runtime execution 0.

**Next action:** owner must prospectively revise the holdout authority to supply at least
24 eligible participant-disjoint RU records before execution can resume, keeping the model,
runtime hypothesis, decoding and numeric gates unchanged. No revision is enacted here.
