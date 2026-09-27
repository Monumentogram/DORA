# Status: APPROVED FOR ALPHA ARCHITECTURE
# This contract defines intended Alpha behavior.
# It does not assert that Cloud functionality is already implemented.
# Canonical product authority: DORA_ASR_USER_SCENARIOS_V0_1.md
# Data authority: ../contracts/DORA_ASR_DATA_AND_VERSIONING_CONTRACT_V0_1.md
# Synthetic identities/audio/results only; future step bindings are not implemented here.
Feature: Authorized Cloud and optional Local recognition preserve recording and user authority
  Background:
    Given a deterministic synthetic recording "R1" with original audio version "A1"
    And a controlled clock and controllable network and ASR adapters
    And Cloud upload bytes, prompts, jobs, transcript versions and edits are observable
    And no recording authorization or user-level Cloud consent exists unless stated

  Scenario: ALWAYS while online
    Given valid informed ALWAYS consent and usable internet
    When recording "R1" is finalized
    Then one Cloud action uploads original audio "A1"
    And its authorization is INHERITED_ALWAYS
    And no additional authorization prompt appears

  Scenario: ALWAYS offline then reconnect
    Given valid informed ALWAYS consent and no internet
    When recording "R1" is finalized and usable internet later returns
    Then original audio "A1" remains intact while waiting
    And one queued Cloud action resumes without another authorization prompt

  Scenario: ASK approve
    Given ASK_EACH_RECORDING and usable internet
    When the user approves the first prompt for "R1"
    Then EXPLICITLY_APPROVED is persisted before the first audio byte uploads
    And one Cloud action uses original audio "A1"

  Scenario: ASK decline
    Given ASK_EACH_RECORDING and usable internet
    When the user selects "Not now" for "R1"
    Then authorization is DEFERRED and audio upload bytes equal zero
    And a non-blocking "Recognize in cloud" action remains available

  Scenario: ASK decline never repeatedly prompts
    Given ASK_EACH_RECORDING and "R1" was prompted once and is DEFERRED
    When connectivity is lost and restored twice and the recording is reopened
    Then the automatic prompt count for "R1" remains one
    And audio upload bytes equal zero

  Scenario: ASK manual action after decline
    Given ASK_EACH_RECORDING and "R1" is DEFERRED
    And usable internet
    When the user selects "Recognize in cloud" for "R1"
    Then authorization becomes MANUAL_USER_ACTION
    And one Cloud action starts without another authorization dialog

  Scenario: ASK offline reconnect waits for consent
    Given ASK_EACH_RECORDING and no internet
    When "R1" is finalized and usable internet returns
    Then one authorization prompt is offered for "R1"
    And audio upload bytes remain zero until approval

  Scenario: Multiple offline recordings use one batch prompt
    Given ASK_EACH_RECORDING and unprompted offline recordings "R1", "R2" and "R3"
    When usable internet returns
    Then one batch prompt offers "Process all", "Select recordings" and "Not now"
    And no separate per-recording modal appears
    And audio upload bytes remain zero until selection is confirmed

  Scenario: Batch process all authorizes only the presented snapshot
    Given an ASK batch presents "R1" and "R2"
    And "R3" is recorded after that batch was presented
    When the user selects "Process all"
    Then only "R1" and "R2" become EXPLICITLY_APPROVED
    And no audio bytes from "R3" upload without its own authorization

  Scenario: Batch selection uploads only selected recordings
    Given an ASK batch presents "R1", "R2" and "R3"
    When the user confirms only "R2" in "Select recordings"
    Then only "R2" becomes EXPLICITLY_APPROVED and uploads
    And "R1" and "R3" become DEFERRED with zero audio upload bytes

  Scenario: Batch not now does not re-prompt
    Given an ASK batch presents "R1" and "R2"
    When the user selects "Not now" and connectivity later returns again
    Then both recordings remain DEFERRED with zero audio upload bytes
    And no automatic prompt is repeated for either recording

  Scenario: Previously deferred items stay out of automatic batches
    Given ASK_EACH_RECORDING and "R1" was previously deferred
    And "R2" and "R3" are unprompted offline recordings
    When usable internet returns
    Then an automatic batch presents only "R2" and "R3"
    And "R1" remains available in the user-opened pending list

  Scenario: MANUAL_ONLY never autonomously uploads
    Given MANUAL_ONLY and usable internet
    When "R1" is finalized and connectivity later reconnects
    Then audio upload bytes equal zero
    And no automatic Cloud action is dispatched

  Scenario: MANUAL_ONLY explicit processing
    Given MANUAL_ONLY and usable internet
    When the user selects "Recognize in cloud" for "R1"
    Then authorization is MANUAL_USER_ACTION
    And one Cloud action uploads original audio "A1"

  Scenario: Cloud-only user online
    Given no Local package is installed and valid informed ALWAYS consent
    And usable internet
    When "R1" is finalized and Cloud recognition succeeds
    Then a Cloud transcript version is available
    And no Local package download is required

  Scenario: Cloud-only user offline
    Given no Local package is installed and no internet
    When the user records and finalizes "R1"
    Then original audio "A1" is durably preserved
    And the recording waits for internet without requiring Local installation

  Scenario: Cloud-only user reconnects
    Given no Local package is installed and "R1" is waiting offline
    And valid informed ALWAYS consent
    When usable internet returns
    Then one Cloud action processes original audio "A1"
    And no Local processing is required

  Scenario: Installed Local recognition offered offline
    Given an integrity-verified Local package is installed and no internet
    When "R1" is finalized
    Then "Recognize on device" and "Wait for internet" are offered
    And the user is told Local recognition may produce slightly more transcription errors

  Scenario: User chooses to wait instead of Local recognition
    Given a Local package is installed and no internet
    When the user selects "Wait for internet"
    Then original audio "A1" remains intact
    And no Local recognition starts

  Scenario: Local recognition completes without network or account
    Given a Local package is installed with no account, network or GMS
    When the user chooses on-device recognition and it succeeds
    Then immutable Local transcript "L1" references original audio "A1"
    And "L1" is active if no conflicting newer version or edit exists

  Scenario: Connectivity returns after Local success
    Given Local transcript "L1" exists and policy is MANUAL_ONLY
    When usable internet returns
    Then Cloud reprocessing is available through "Recognize in cloud"
    And the active Local transcript remains unchanged with zero uploaded bytes

  Scenario: Local to Cloud reprocessing
    Given Local transcript "L1" is active with no edits and valid informed ALWAYS consent
    When internet returns and Cloud reprocessing succeeds with "C1"
    Then "C1" may become active after source and generation validation
    And "L1" remains immutable and accessible

  Scenario: Reprocessing uses original audio
    Given Local transcript "L1" differs from the content of original audio "A1"
    And "R1" has valid Cloud authorization
    When Cloud reprocessing starts
    Then the ASR input is original audio version "A1"
    And Local transcript text is not submitted as replacement ASR input

  Scenario: Local to Cloud preserves a safely mapped user correction
    Given Local transcript "L1" is active and has correction "E1" on an audio segment
    And "R1" has valid Cloud authorization
    When Cloud version "C1" completes with a unique safe mapping for "E1"
    Then a merged proposal preserves "E1" above Cloud text
    And raw versions "L1" and "C1" and edit "E1" remain available
    And active user text remains unchanged until explicit proposal acceptance

  Scenario: Ambiguous edit mapping requires review
    Given active Local text contains correction "E1" with no unique mapping to Cloud version "C1"
    When a merged proposal is prepared
    Then "E1" is preserved and explicitly marked as a mapping conflict
    And activation requires user review without silently dropping or misapplying "E1"

  Scenario: User accepts an edit-preserving proposal
    Given a reviewed proposal based on the current active version and latest edit revision
    And all corrections are preserved or explicitly resolved by the user
    When the user accepts that proposal
    Then the active pointer changes to that version
    And raw versions and edit history remain intact

  Scenario: User rejects a proposed Cloud replacement
    Given Cloud version "C1" is proposed against current edited Local text
    When the user chooses to keep the current text
    Then the active pointer and user edits remain unchanged
    And "C1" remains available for later comparison

  Scenario: Concurrent user edit invalidates stale activation
    Given a proposal was built against edit revision "E1"
    And the user subsequently creates edit revision "E2"
    When activation is attempted using base revision "E1"
    Then the stale activation is rejected
    And the proposal requires regeneration or review against "E2"

  Scenario: Cloud failure preserves Local transcript
    Given successful Local transcript "L1" and an authorized Cloud action
    When that Cloud action fails
    Then "L1" remains byte-identical and accessible

  Scenario: Cloud failure preserves active transcript
    Given active transcript "T1" and an authorized Cloud action
    When that Cloud action fails
    Then the active pointer and text of "T1" remain unchanged

  Scenario: Cloud failure preserves user edits
    Given user edit history "E1" and an authorized Cloud action
    When that Cloud action fails
    Then every edit in "E1" remains intact with its provenance

  Scenario: Cloud failure preserves original audio
    Given original audio version "A1" and an authorized Cloud action
    When that Cloud action fails
    Then original audio "A1" remains byte-identical
    And retry remains available while source and authorization are valid

  Scenario: One logical recording has multiple technical chunks
    Given ASK_EACH_RECORDING and "R1" consists of three technical chunks
    When the user approves "R1" once
    Then that recording authorization covers all three chunks within the granted scope
    And no chunk-specific authorization dialog appears

  Scenario: Application restart preserves pending Cloud work
    Given "R1" has one authorized pending Cloud action with idempotency key "K1"
    When the application restarts and usable internet returns
    Then exactly that logical action resumes using "K1" after authorization revalidation
    And no duplicate logical job is created

  Scenario: Application restart preserves DEFERRED
    Given ASK_EACH_RECORDING and "R1" was prompted once and is DEFERRED
    When the application restarts with usable internet
    Then "R1" remains DEFERRED and its prompt count remains one
    And audio upload bytes equal zero

  Scenario: Device restart preserves queue and consent
    Given authorized pending action "K1" for "R1" and deferred recording "R2"
    When the device restarts and workers resume
    Then "K1" resumes only after authorization revalidation
    And "R2" remains DEFERRED without another automatic prompt or upload

  Scenario: No unauthorized Cloud upload
    Given "R1" has no valid inherited or recording-level grant
    When any upload worker, retry, reconnect or chunk scheduler executes
    Then audio upload bytes for "R1" equal zero

  Scenario: Global policy change does not lift deferral
    Given "R1" is DEFERRED under ASK_EACH_RECORDING
    When the user changes the global policy to ALWAYS
    Then "R1" remains DEFERRED until a recording-specific user action
    And future eligible unprompted recordings may inherit the new informed grant

  Scenario: Leaving ALWAYS invalidates unstarted inherited work
    Given pending "R1" relies solely on an ALWAYS grant and has uploaded zero bytes
    When the global policy changes to MANUAL_ONLY before dispatch
    Then no audio bytes upload without a new explicit recording action

  Scenario: Revocation prevents further bytes
    Given an authorized multipart upload is paused before its next chunk
    When the user revokes its authorization and the worker resumes
    Then no further audio bytes are uploaded
    And pending work is cancelled and remote cancellation is requested where possible
    And already-sent bytes are not claimed erased without a deletion receipt

  Scenario: Network disappears during upload
    Given an authorized upload with idempotency key "K1"
    When network loss interrupts upload and a permitted retry resumes
    Then the action retains "K1" and follows the declared resumability policy
    And original audio, active text and edits remain intact

  Scenario: Network disappears during processing
    Given a provider accepted authorized action "K1"
    When connectivity disappears before its result arrives
    Then the outcome is recorded as unknown or pending reconciliation
    And no new competing action is submitted before reconciling "K1"

  Scenario: Provider timeout is bounded
    Given an authorized Cloud request and a configured timeout and retry budget
    When the provider does not respond before the controlled timeout expires
    Then a normalized timeout state is recorded
    And retries obey that budget without changing existing data

  Scenario: Provider authorization error requires corrective action
    Given an authorized Cloud action
    When the provider returns an authentication-related 4xx error
    Then the error is normalized as requiring authorization correction
    And no blind automatic retry loop starts

  Scenario: Provider server error uses bounded retry
    Given an authorized Cloud action with idempotency key "K1"
    When the provider returns a retryable 5xx error
    Then configured backoff and retry limits apply to the same action "K1"
    And existing audio, transcripts and edits remain intact

  Scenario: Quota response cannot amplify retries
    Given an authorized action and configured retry and usage limits
    When the provider returns a rate limit with Retry-After
    Then no retry occurs before Retry-After and no retry exceeds the configured budget
    And request count and usage accounting record every actual attempt once

  Scenario: Duplicate retry cannot produce competing winners
    Given two deliveries for the same processing action "K1"
    When both return the same Cloud result identity
    Then one logical result is retained for "K1"
    And the active pointer cannot regress because of a stale callback

  Scenario: Missing original audio prevents reprocessing
    Given original audio "A1" was explicitly deleted and transcript "L1" remains
    When the user requests Cloud reprocessing
    Then source unavailable is shown and no ASR request uses "L1" as input
    And existing transcripts and edits remain intact

  Scenario: Cancelling pending Cloud work preserves data
    Given "R1" has an authorized queued action that has not uploaded audio
    When the user cancels that action
    Then it cannot start automatically on reconnect
    And original audio, all transcript versions and user edits remain intact

  Scenario: Local package removal does not prevent recording
    Given the optional Local package was removed
    When a new recording is made offline
    Then it is safely preserved for later authorized Cloud processing
    And Local installation is not required to finish the recording
