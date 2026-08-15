@file:Suppress(
    "LargeClass",
    "LongParameterList",
    "MagicNumber",
    "MaxLineLength",
    "TooManyFunctions",
)

package com.monumentogram.dora.poc.vpn.contract

object ContractCatalog {
    const val CONTRACT_ID = "poc-vpn-synthetic-api-stage0-v0.1"
    const val MACHINE_RECORD_SHA256 =
        "5efb14b67026bed208310249c7d2e0bd03450af0069f6af8c1634c7efc4025c2"
    const val MARKDOWN_CONTRACT_SHA256 =
        "cc3e7508238564fd1346467625b3bd03c9a9ffdbf2b00d44a3b7ca67bea81ebf"
    const val PART_SIZE_BYTES = 1024
    const val DELETE_RECEIPT_POLL_ELIGIBLE = "DELETE_RECEIPT_POLL_ELIGIBLE"

    val operations =
        listOf(
            operation(
                1,
                "CREATE_JOB",
                HttpMethod.POST,
                "/v1/processing-jobs",
                body = "fixtureId",
                request = "CreateJobRequest-v0.1",
                response = "CreateJobResponse-v0.1",
                idempotency = "REQUIRED",
                status = 201,
            ),
            operation(
                2,
                "INIT_OR_REFRESH_UPLOAD",
                HttpMethod.POST,
                "/v1/processing-jobs/{jobId}/uploads",
                path = "jobId:OPAQUE_ID",
                body = "jobId,priorUploadId",
                equality = "jobId",
                request = "UploadPlanRequest-v0.1",
                response = "UploadPlanResponse-v0.1",
                idempotency = "REQUIRED_NEW_KEY_PER_PLAN_GENERATION",
                status = 201,
            ),
            operation(
                3,
                "UPLOAD_PART",
                HttpMethod.PUT,
                "/synthetic-upload/{uploadId}/{planGeneration}/{partNumber}",
                path =
                    "partNumber:POSITIVE_INTEGER,planGeneration:POSITIVE_INTEGER,uploadId:OPAQUE_ID",
                body = "uploadId,planGeneration,partNumber",
                equality = "uploadId,planGeneration,partNumber",
                request = "UploadPartRequest-v0.1",
                response = "UploadPartResponse-v0.1",
                idempotency = "REQUIRED_STABLE_PER_UPLOAD_AND_PART",
                status = 200,
            ),
            operation(
                4,
                "COMPLETE_UPLOAD",
                HttpMethod.POST,
                "/v1/processing-jobs/{jobId}/uploads:complete",
                path = "jobId:OPAQUE_ID",
                body = "jobId,uploadId,manifest[].partReceiptId",
                equality = "jobId",
                request = "CompleteUploadRequest-v0.1",
                response = "CompleteUploadResponse-v0.1",
                idempotency = "REQUIRED",
                status = 202,
            ),
            operation(
                5,
                "POLL_JOB",
                HttpMethod.GET,
                "/v1/processing-jobs/{jobId}",
                path = "jobId:OPAQUE_ID",
                request = "NONE",
                response = "JobStatusResponse-v0.1",
                idempotency = "READ_ONLY",
                status = 200,
            ),
            operation(
                6,
                "FETCH_RESULT",
                HttpMethod.GET,
                "/v1/processing-jobs/{jobId}/result",
                path = "jobId:OPAQUE_ID",
                request = "NONE",
                response = "ResultResponse-v0.1",
                idempotency = "READ_ONLY_STABLE_RESULT",
                status = 200,
            ),
            operation(
                7,
                "CANCEL_JOB",
                HttpMethod.POST,
                "/v1/processing-jobs/{jobId}:cancel",
                path = "jobId:OPAQUE_ID",
                request = "NONE",
                response = "CancelResponse-v0.1",
                idempotency = "REQUIRED",
                status = 202,
            ),
            operation(
                8,
                "DELETE_CLOUD_COPY",
                HttpMethod.DELETE,
                "/v1/conversations/{conversationFixtureId}/cloud-copy",
                path = "conversationFixtureId:OPAQUE_ID",
                request = "NONE",
                response = "DeleteResponse-v0.1",
                idempotency = "REQUIRED",
                status = 202,
            ),
            operation(
                9,
                "POLL_DELETION_RECEIPT",
                HttpMethod.GET,
                "/v1/deletions/{deletionId}",
                path = "deletionId:OPAQUE_ID",
                request = "NONE",
                response = "DeletionReceiptResponse-v0.1",
                idempotency = "READ_ONLY_STABLE_RECEIPT",
                status = 200,
            ),
        )

    val operationsByClass = operations.associateBy { it.operationClass }
    val clientTerminalStates = setOf(ClientState.DELETED, ClientState.FAILED_FINAL)

    val clientTransitions =
        listOf(
            c(
                1,
                states(
                    "READY,CREATING,WAITING_UPLOAD,UPLOADING,WAITING_NETWORK,RETRY_SCHEDULED,COMPLETING,REMOTE_PROCESSING,RESULT_AVAILABLE,RESULT_VERIFIED,CANCEL_PENDING"
                ),
                "PROFILE_ABSENT_STALE_EXPIRED_REVOKED_OR_MISMATCHED",
                fixed(ClientState.BLOCKED_NO_PROFILE),
                retry = RetryClass.FINAL_REJECT,
            ),
            c(
                2,
                state(ClientState.BLOCKED_NO_PROFILE),
                "VALID_PROFILE_DURABLY_SELECTED",
                fixed(ClientState.READY),
            ),
            c(
                3,
                state(ClientState.READY),
                "START_REMOTE_SYNTHETIC_WORK",
                fixed(ClientState.CREATING),
            ),
            c(
                4,
                state(ClientState.CREATING),
                "CREATE_COMMITTED",
                fixed(ClientState.WAITING_UPLOAD),
            ),
            c(
                5,
                state(ClientState.CREATING),
                "WAIT_NETWORK",
                fixed(ClientState.WAITING_NETWORK),
                resume = ClientState.CREATING,
            ),
            c(
                6,
                state(ClientState.CREATING),
                "BACKOFF_REPLAY",
                fixed(ClientState.RETRY_SCHEDULED),
                resume = ClientState.CREATING,
            ),
            c(
                7,
                state(ClientState.WAITING_UPLOAD),
                "UPLOAD_PLAN_READY",
                fixed(ClientState.UPLOADING),
            ),
            c(
                8,
                state(ClientState.WAITING_UPLOAD),
                "WAIT_NETWORK",
                fixed(ClientState.WAITING_NETWORK),
                resume = ClientState.WAITING_UPLOAD,
            ),
            c(
                9,
                state(ClientState.WAITING_UPLOAD),
                "BACKOFF_REPLAY",
                fixed(ClientState.RETRY_SCHEDULED),
                resume = ClientState.WAITING_UPLOAD,
            ),
            c(
                10,
                state(ClientState.UPLOADING),
                "PART_RECEIPT_VERIFIED_MORE_MISSING",
                fixed(ClientState.UPLOADING),
            ),
            c(
                11,
                state(ClientState.UPLOADING),
                "ALL_PART_RECEIPTS_VERIFIED",
                fixed(ClientState.COMPLETING),
            ),
            c(
                12,
                state(ClientState.UPLOADING),
                "WAIT_NETWORK",
                fixed(ClientState.WAITING_NETWORK),
                resume = ClientState.UPLOADING,
            ),
            c(
                13,
                state(ClientState.UPLOADING),
                "BACKOFF_REPLAY",
                fixed(ClientState.RETRY_SCHEDULED),
                resume = ClientState.UPLOADING,
            ),
            c(
                14,
                state(ClientState.UPLOADING),
                "UPLOAD_URL_EXPIRED",
                fixed(ClientState.WAITING_UPLOAD),
            ),
            c(
                15,
                state(ClientState.COMPLETING),
                "COMPLETE_COMMITTED",
                fixed(ClientState.REMOTE_PROCESSING),
            ),
            c(
                16,
                state(ClientState.COMPLETING),
                "WAIT_NETWORK",
                fixed(ClientState.WAITING_NETWORK),
                resume = ClientState.COMPLETING,
            ),
            c(
                17,
                state(ClientState.COMPLETING),
                "BACKOFF_REPLAY",
                fixed(ClientState.RETRY_SCHEDULED),
                resume = ClientState.COMPLETING,
            ),
            c(
                18,
                state(ClientState.REMOTE_PROCESSING),
                "POLL_PENDING",
                fixed(ClientState.REMOTE_PROCESSING),
            ),
            c(
                19,
                state(ClientState.REMOTE_PROCESSING),
                "POLL_RESULT_READY",
                fixed(ClientState.RESULT_AVAILABLE),
            ),
            c(
                20,
                state(ClientState.REMOTE_PROCESSING),
                "WAIT_NETWORK",
                fixed(ClientState.WAITING_NETWORK),
                resume = ClientState.REMOTE_PROCESSING,
            ),
            c(
                21,
                state(ClientState.REMOTE_PROCESSING),
                "BACKOFF_REPLAY",
                fixed(ClientState.RETRY_SCHEDULED),
                resume = ClientState.REMOTE_PROCESSING,
            ),
            c(
                22,
                state(ClientState.RESULT_AVAILABLE),
                "RESULT_CHECKSUM_VALID",
                fixed(ClientState.RESULT_VERIFIED),
            ),
            c(
                23,
                state(ClientState.RESULT_AVAILABLE),
                "RESULT_CHECKSUM_INVALID",
                fixed(ClientState.FAILED_FINAL),
                retry = RetryClass.FINAL_REJECT,
            ),
            c(
                24,
                state(ClientState.RESULT_VERIFIED),
                "DELETE_REQUESTED",
                fixed(ClientState.DELETE_PENDING),
                substatus = DELETE_RECEIPT_POLL_ELIGIBLE,
            ),
            c(
                25,
                state(ClientState.DELETE_PENDING),
                "DELETION_RECEIPT_PENDING",
                fixed(ClientState.DELETE_PENDING),
                substatus = DELETE_RECEIPT_POLL_ELIGIBLE,
                preserve = true,
            ),
            c(
                26,
                state(ClientState.DELETE_PENDING),
                "DELETION_RECEIPT_VERIFIED",
                fixed(ClientState.DELETED),
            ),
            c(
                27,
                state(ClientState.DELETE_PENDING),
                "WAIT_NETWORK",
                fixed(ClientState.DELETE_PENDING),
                retry = RetryClass.WAIT_NETWORK,
                substatus = "DELETE_WAITING_NETWORK",
                preserve = true,
            ),
            c(
                28,
                state(ClientState.DELETE_PENDING),
                "BACKOFF_REPLAY",
                fixed(ClientState.DELETE_PENDING),
                retry = RetryClass.BACKOFF_REPLAY,
                substatus = "DELETE_RETRY_SCHEDULED",
                preserve = true,
            ),
            c(
                29,
                states(
                    "CREATING,WAITING_UPLOAD,UPLOADING,WAITING_NETWORK,RETRY_SCHEDULED,COMPLETING,REMOTE_PROCESSING,RESULT_AVAILABLE,RESULT_VERIFIED"
                ),
                "CANCEL_REQUESTED",
                fixed(ClientState.CANCEL_PENDING),
            ),
            c(
                30,
                state(ClientState.CANCEL_PENDING),
                "CANCEL_COMMIT_WON",
                fixed(ClientState.CANCELLED),
            ),
            c(
                31,
                state(ClientState.CANCEL_PENDING),
                "RESULT_COMMIT_WON",
                fixed(ClientState.RESULT_AVAILABLE),
            ),
            c(
                32,
                state(ClientState.CANCEL_PENDING),
                "WAIT_NETWORK",
                fixed(ClientState.WAITING_NETWORK),
                resume = ClientState.CANCEL_PENDING,
            ),
            c(
                33,
                state(ClientState.WAITING_NETWORK),
                "NETWORK_AVAILABLE",
                TransitionDestination.PersistedResumeState,
            ),
            c(
                34,
                state(ClientState.RETRY_SCHEDULED),
                "BACKOFF_DUE",
                TransitionDestination.PersistedResumeState,
            ),
            c(
                35,
                states(
                    "CREATING,WAITING_UPLOAD,UPLOADING,WAITING_NETWORK,RETRY_SCHEDULED,COMPLETING,REMOTE_PROCESSING,RESULT_AVAILABLE,CANCEL_PENDING"
                ),
                "RETRY_BUDGET_EXHAUSTED",
                fixed(ClientState.FAILED_FINAL),
            ),
            c(
                36,
                state(ClientState.RESULT_AVAILABLE),
                "RESULT_FETCH_RESPONSE_LOST_OR_RETRYABLE",
                fixed(ClientState.RETRY_SCHEDULED),
                resume = ClientState.RESULT_AVAILABLE,
                retry = RetryClass.REPLAY_SAME_OPERATION,
            ),
            c(
                37,
                states(
                    "CREATING,WAITING_UPLOAD,UPLOADING,WAITING_NETWORK,RETRY_SCHEDULED,COMPLETING,REMOTE_PROCESSING,RESULT_AVAILABLE,CANCEL_PENDING"
                ),
                "FINAL_TLS_TRUST_OR_NAME_REJECT",
                fixed(ClientState.FAILED_FINAL),
                retry = RetryClass.FINAL_REJECT,
            ),
            c(
                38,
                states(
                    "CREATING,WAITING_UPLOAD,UPLOADING,WAITING_NETWORK,RETRY_SCHEDULED,COMPLETING,REMOTE_PROCESSING,RESULT_AVAILABLE,CANCEL_PENDING"
                ),
                "FINAL_SCHEMA_OR_UNSUPPORTED_REJECT",
                fixed(ClientState.FAILED_FINAL),
                retry = RetryClass.FINAL_REJECT,
            ),
            c(
                39,
                states("UPLOADING,COMPLETING"),
                "FINAL_CHECKSUM_OR_MANIFEST_REJECT",
                fixed(ClientState.FAILED_FINAL),
                retry = RetryClass.FINAL_REJECT,
            ),
            c(
                40,
                states("CREATING,WAITING_UPLOAD,UPLOADING,COMPLETING,CANCEL_PENDING"),
                "FINAL_IDEMPOTENCY_PAYLOAD_MISMATCH",
                fixed(ClientState.FAILED_FINAL),
                retry = RetryClass.FINAL_REJECT,
            ),
            c(
                41,
                states(
                    "CREATING,WAITING_UPLOAD,UPLOADING,WAITING_NETWORK,RETRY_SCHEDULED,COMPLETING,REMOTE_PROCESSING,RESULT_AVAILABLE,CANCEL_PENDING"
                ),
                "FINAL_CROSS_TENANT_OR_PROFILE_REJECT",
                fixed(ClientState.FAILED_FINAL),
                retry = RetryClass.FINAL_REJECT,
            ),
            c(
                42,
                state(ClientState.CANCELLED),
                "DELETE_REQUESTED",
                fixed(ClientState.DELETE_PENDING),
                substatus = DELETE_RECEIPT_POLL_ELIGIBLE,
            ),
            c(
                43,
                state(ClientState.CANCEL_PENDING),
                "BACKOFF_REPLAY",
                fixed(ClientState.RETRY_SCHEDULED),
                resume = ClientState.CANCEL_PENDING,
            ),
            c(
                44,
                state(ClientState.DELETE_PENDING),
                "CANCEL_REQUESTED",
                fixed(ClientState.DELETE_PENDING),
                substatus = "\$sameVisibleSubstatus",
                preserve = true,
            ),
            c(
                45,
                state(ClientState.DELETE_PENDING),
                "PROFILE_ABSENT_STALE_EXPIRED_REVOKED_OR_MISMATCHED",
                fixed(ClientState.DELETE_PENDING),
                retry = RetryClass.USER_ACTION_REQUIRED,
                substatus = "DELETE_REVALIDATION_REQUIRED",
                preserve = true,
            ),
            c(
                46,
                state(ClientState.DELETE_PENDING),
                "FINAL_TLS_TRUST_OR_NAME_REJECT",
                fixed(ClientState.DELETE_PENDING),
                retry = RetryClass.USER_ACTION_REQUIRED,
                substatus = "DELETE_USER_ACTION_REQUIRED",
                preserve = true,
            ),
            c(
                47,
                state(ClientState.DELETE_PENDING),
                "FINAL_SCHEMA_OR_UNSUPPORTED_REJECT",
                fixed(ClientState.DELETE_PENDING),
                retry = RetryClass.USER_ACTION_REQUIRED,
                substatus = "DELETE_USER_ACTION_REQUIRED",
                preserve = true,
            ),
            c(
                48,
                state(ClientState.DELETE_PENDING),
                "FINAL_CHECKSUM_OR_MANIFEST_REJECT",
                fixed(ClientState.DELETE_PENDING),
                retry = RetryClass.USER_ACTION_REQUIRED,
                substatus = "DELETE_USER_ACTION_REQUIRED",
                preserve = true,
            ),
            c(
                49,
                state(ClientState.DELETE_PENDING),
                "FINAL_IDEMPOTENCY_PAYLOAD_MISMATCH",
                fixed(ClientState.DELETE_PENDING),
                retry = RetryClass.USER_ACTION_REQUIRED,
                substatus = "DELETE_USER_ACTION_REQUIRED",
                preserve = true,
            ),
            c(
                50,
                state(ClientState.DELETE_PENDING),
                "FINAL_CROSS_TENANT_OR_PROFILE_REJECT",
                fixed(ClientState.DELETE_PENDING),
                retry = RetryClass.USER_ACTION_REQUIRED,
                substatus = "DELETE_REVALIDATION_REQUIRED",
                preserve = true,
            ),
            c(
                51,
                state(ClientState.DELETE_PENDING),
                "RETRY_BUDGET_EXHAUSTED",
                fixed(ClientState.DELETE_PENDING),
                retry = RetryClass.USER_ACTION_REQUIRED,
                substatus = "DELETE_MANUAL_RETRY_REQUIRED",
                preserve = true,
            ),
            c(
                52,
                state(ClientState.DELETE_PENDING),
                "DELETE_REVALIDATED_OR_USER_ACTION_CONFIRMED",
                fixed(ClientState.DELETE_PENDING),
                retry = RetryClass.REPLAY_SAME_OPERATION,
                substatus = DELETE_RECEIPT_POLL_ELIGIBLE,
                preserve = true,
            ),
        )

    val priorityGroups =
        listOf(
            priority(0, "DURABLE_SERVER_OUTCOME_RECONCILIATION", "4,7,10,11,15,19,22,26,30,31"),
            priority(10, "PROFILE_FAIL_CLOSED", "1,45"),
            priority(20, "IMMEDIATE_FINAL_REJECT", "23,37,38,39,40,41,46,47,48,49,50"),
            priority(30, "RETRY_BUDGET_EXHAUSTED", "35,51"),
            priority(40, "EXPLICIT_USER_CANCEL_OR_DELETE", "24,29,42,44,52"),
            priority(50, "NONTERMINAL_PROGRESS", "18,25"),
            priority(60, "BOUNDED_RETRY_OR_WAIT", "5,6,8,9,12,13,14,16,17,20,21,27,28,32,36,43"),
            priority(70, "WAKE_OR_RESUME", "33,34"),
            priority(80, "PROFILE_READY_OR_START", "2,3"),
        )
    val clientTransitionPriority =
        priorityGroups.flatMap { group -> group.transitionIds.map { it to group.priority } }.toMap()
    val clientTransitionOrder =
        priorityGroups
            .flatMap { group ->
                group.transitionIds.mapIndexed { index, id -> id to (group.priority * 100 + index) }
            }
            .toMap()

    val serverTransitions =
        listOf(
            s(1, "ABSENT", "VALID_CREATE_COMMIT", "CREATED"),
            s(2, "CREATED", "UPLOAD_PLAN_COMMIT", "WAITING_UPLOAD"),
            s(3, "WAITING_UPLOAD", "FIRST_PART_COMMIT", "UPLOADING"),
            s(4, "UPLOADING", "NEXT_PART_COMMIT", "UPLOADING"),
            s(5, "UPLOADING", "VALID_COMPLETE_COMMIT", "UPLOAD_COMPLETE"),
            s(6, "UPLOAD_COMPLETE", "QUEUE_COMMIT", "QUEUED"),
            s(7, "QUEUED", "PROCESSING_START", "PROCESSING"),
            s(8, "PROCESSING", "RESULT_COMMIT", "RESULT_READY"),
            s(9, "RESULT_READY", "RESULT_FETCH", "DELIVERED"),
            s(10, "DELIVERED", "RESULT_FETCH_REPEAT", "DELIVERED"),
            s(
                11,
                "CREATED,WAITING_UPLOAD,UPLOADING,UPLOAD_COMPLETE,QUEUED,PROCESSING",
                "CANCEL_COMMIT_BEFORE_RESULT",
                "CANCELLED",
            ),
            s(12, "RESULT_READY,DELIVERED", "CANCEL_AFTER_RESULT", "\$sameState"),
            s(13, "RESULT_READY,DELIVERED,CANCELLED", "DELETE_COMMIT", "DELETE_PENDING"),
            s(14, "DELETE_PENDING", "DELETION_RECEIPT_COMMIT", "DELETED"),
            s(15, "DELETED", "DELETE_REPEAT", "DELETED"),
            s(
                16,
                "CREATED,WAITING_UPLOAD,UPLOADING,UPLOAD_COMPLETE,QUEUED,PROCESSING,RESULT_READY,DELIVERED,DELETE_PENDING,DELETED,CANCELLED",
                "POLL",
                "\$sameState",
            ),
            s(
                17,
                "CREATED,WAITING_UPLOAD,UPLOADING,UPLOAD_COMPLETE,QUEUED,PROCESSING,RESULT_READY,DELIVERED,CANCELLED",
                "CREATE_REPLAY",
                "\$sameState",
            ),
            s(18, "WAITING_UPLOAD,UPLOADING", "PART_REPLAY", "\$sameState"),
            s(
                19,
                "UPLOAD_COMPLETE,QUEUED,PROCESSING,RESULT_READY,DELIVERED",
                "COMPLETE_REPLAY",
                "\$sameState",
            ),
            s(20, "WAITING_UPLOAD", "UPLOAD_PLAN_REFRESH", "WAITING_UPLOAD"),
        )

    val traces =
        listOf(
            trace(1, "LOST_RESULT_REPLAY", c = "36,34,22,35"),
            trace(2, "IMMEDIATE_FINAL_REJECT_FAMILIES", c = "37,38,39,40,41"),
            trace(3, "CANCEL_THEN_EXPLICIT_DELETE", c = "29,30,42,25,26", s = "11,13,14"),
            trace(4, "TERMINAL_PROFILE_AND_PRIORITY_OVERLAP", c = "4,1,30,23"),
            trace(
                5,
                "DELETE_PENDING_PRESERVATION_AND_RECOVERY",
                c = "44,45,52,46,47,51,25,26,48,49,50",
                s = "14",
            ),
            trace(6, "SAME_KEY_DIFFERENT_PATH_TARGET_REJECT", operations = "7,8"),
        )

    val trace006Digests =
        mapOf(
            "CANCEL_JOB/job-synthetic-a" to
                "5793cbc158297174fff8a67a22119427ae9d1dcf35a115d7be71b15b972b556d",
            "CANCEL_JOB/job-synthetic-b" to
                "7ed20fbf37fc119a28c3e9175af3e6494af211be79042fd8252dfa72b160a3a0",
            "DELETE_CLOUD_COPY/conversation-synthetic-a" to
                "af1c896acd29cf41bf635b3db1b6595ecccc29d86d5b3fbcb107941b739ec646",
            "DELETE_CLOUD_COPY/conversation-synthetic-b" to
                "f846211bba2a92c986e6f809de52de7da7a804e906ebc5a062b939d5f679e1ac",
        )

    val faults =
        listOf(
            fault(1, "PREFLIGHT", RetryClass.FINAL_REJECT, "FAKE_OR_LOOPBACK", c = "1", t = "4"),
            fault(2, "PREFLIGHT", RetryClass.FINAL_REJECT, "FAKE_OR_LOOPBACK", c = "1", t = "4"),
            fault(
                3,
                "PREFLIGHT",
                RetryClass.FINAL_REJECT,
                "FAKE_OR_LOOPBACK",
                c = "1,45",
                t = "4,5",
            ),
            fault(4, "CREATE", RetryClass.WAIT_NETWORK, "LOOPBACK_CATEGORY_SIMULATION", c = "5,33"),
            fault(
                5,
                "INIT_OR_REFRESH",
                RetryClass.WAIT_NETWORK,
                "LOOPBACK_CATEGORY_SIMULATION",
                c = "8,33",
            ),
            fault(
                6,
                "TLS",
                RetryClass.FINAL_REJECT,
                "CATEGORY_ONLY_UNTIL_SECURITY_SCOPE",
                c = "37,46",
                t = "2,5",
            ),
            fault(
                7,
                "CREATE",
                RetryClass.REPLAY_SAME_OPERATION,
                "FAKE_OR_LOOPBACK",
                c = "6,34,4",
                s = "17",
            ),
            fault(
                8,
                "CREATE",
                RetryClass.REPLAY_SAME_OPERATION,
                "FAKE_OR_LOOPBACK",
                c = "4",
                s = "17",
            ),
            fault(
                9,
                "ANY_IDEMPOTENT_MUTATION",
                RetryClass.FINAL_REJECT,
                "FAKE_OR_LOOPBACK",
                c = "40,49",
                t = "2,6",
            ),
            fault(
                10,
                "UPLOAD_PART",
                RetryClass.REPLAY_SAME_OPERATION,
                "FAKE_OR_LOOPBACK",
                c = "13,34,10",
                s = "3,4",
            ),
            fault(
                11,
                "UPLOAD_PART",
                RetryClass.REPLAY_SAME_OPERATION,
                "FAKE_OR_LOOPBACK",
                c = "13,34,10",
                s = "18",
            ),
            fault(
                12,
                "UPLOAD_PART",
                RetryClass.REPLAY_SAME_OPERATION,
                "FAKE_OR_LOOPBACK",
                c = "10",
                s = "18",
            ),
            fault(
                13,
                "UPLOAD_PART",
                RetryClass.FINAL_REJECT,
                "FAKE_OR_LOOPBACK",
                c = "40",
                t = "2",
            ),
            fault(
                14,
                "UPLOAD_PART",
                RetryClass.FINAL_REJECT,
                "FAKE_OR_LOOPBACK",
                c = "39",
                t = "2",
            ),
            fault(
                15,
                "ANY_MUTATION",
                RetryClass.BACKOFF_REPLAY,
                "FAKE_OR_LOOPBACK",
                c = retryTransitions,
            ),
            fault(
                16,
                "ANY_MUTATION",
                RetryClass.BACKOFF_REPLAY,
                "FAKE_OR_LOOPBACK",
                c = retryTransitions,
            ),
            fault(
                17,
                "ANY_MUTATION",
                RetryClass.BACKOFF_REPLAY,
                "FAKE_OR_LOOPBACK",
                c = "$retryTransitions,35,51,52",
                t = "5",
            ),
            fault(
                18,
                "ANY_RETRYABLE_OPERATION",
                RetryClass.BACKOFF_REPLAY,
                "FAKE_OR_LOOPBACK",
                c = retryTransitions,
            ),
            fault(
                19,
                "ANY_RETRYABLE_OPERATION",
                RetryClass.REPLAY_SAME_OPERATION,
                "FAKE_OR_LOOPBACK",
                c = "6,9,13,17,21,28,36,43,34,35",
                t = "1",
            ),
            fault(
                20,
                "UPLOAD_PART",
                RetryClass.REFRESH_UPLOAD_PLAN,
                "FAKE_OR_LOOPBACK",
                c = "14,7",
                s = "20",
            ),
            fault(
                21,
                "ROUTE",
                RetryClass.REPLAY_SAME_OPERATION,
                "SIMULATED_ROUTE_ONLY",
                c = "12,13,33,34,10",
                s = "18",
            ),
            fault(
                22,
                "ROUTE",
                RetryClass.WAIT_NETWORK,
                "SIMULATED_ROUTE_ONLY",
                c = routeWaitTransitions,
            ),
            fault(
                23,
                "ROUTE",
                RetryClass.WAIT_NETWORK,
                "SIMULATED_ROUTE_ONLY",
                c = routeWaitTransitions,
            ),
            fault(
                24,
                "VPN",
                RetryClass.WAIT_NETWORK,
                "SIMULATED_ROUTE_ONLY",
                c = routeWaitTransitions,
            ),
            fault(
                25,
                "VPN",
                RetryClass.WAIT_NETWORK,
                "SIMULATED_ROUTE_ONLY",
                c = routeWaitTransitions,
            ),
            fault(
                26,
                "PROCESS_DEATH",
                RetryClass.REPLAY_SAME_OPERATION,
                "FAKE_OR_LOOPBACK",
                c = "10",
                s = "18",
                rules = processDeathRuleTargets,
            ),
            fault(
                27,
                "PROCESS_DEATH",
                RetryClass.REPLAY_SAME_OPERATION,
                "FAKE_OR_LOOPBACK",
                c = "15",
                s = "19",
                rules = processDeathRuleTargets,
            ),
            fault(
                28,
                "COMPLETE",
                RetryClass.REPLAY_SAME_OPERATION,
                "FAKE_OR_LOOPBACK",
                c = "15",
                s = "19",
            ),
            fault(29, "COMPLETE", RetryClass.FINAL_REJECT, "FAKE_OR_LOOPBACK", c = "39", t = "2"),
            fault(30, "COMPLETE", RetryClass.FINAL_REJECT, "FAKE_OR_LOOPBACK", c = "39", t = "2"),
            fault(
                31,
                "POLL",
                RetryClass.BACKOFF_REPLAY,
                "FAKE_OR_LOOPBACK",
                c = "21,34,18,19",
                s = "16",
            ),
            fault(
                32,
                "RESULT",
                RetryClass.REPLAY_SAME_OPERATION,
                "FAKE_OR_LOOPBACK",
                c = "36,34,22,35",
                s = "10",
                t = "1",
            ),
            fault(
                33,
                "CANCEL",
                RetryClass.REPLAY_SAME_OPERATION,
                "FAKE_OR_LOOPBACK",
                c = "29,30,31,43",
                s = "11,12",
                t = "3",
            ),
            fault(
                34,
                "DELETE_AND_RECEIPT",
                RetryClass.REPLAY_SAME_OPERATION,
                "FAKE_OR_LOOPBACK",
                c = "24,42,44,45,46,47,48,49,50,51,52,27,28,25,26",
                s = "13,14,15,16",
                t = "3,5",
            ),
        )

    val fixtures =
        listOf(
            fixture(
                1,
                1,
                "a25513c7e0f6eaa80a3337ee18081b9e2ed09e00af8531c8f7bb2542764027e7",
                part(1, 1, "a25513c7e0f6eaa80a3337ee18081b9e2ed09e00af8531c8f7bb2542764027e7"),
            ),
            fixture(
                2,
                1023,
                "3d516a78221f801198e8a93a0b7feb28dfe68a4c9abf8ce71a0f7cd761c49883",
                part(1, 1023, "3d516a78221f801198e8a93a0b7feb28dfe68a4c9abf8ce71a0f7cd761c49883"),
            ),
            fixture(
                3,
                1024,
                "283ccdcd625d21b257d030741772652401c2a96323c38ff90db72a69d90bc1dc",
                part(1, 1024, "283ccdcd625d21b257d030741772652401c2a96323c38ff90db72a69d90bc1dc"),
            ),
            fixture(
                4,
                1025,
                "be988299821e0a1e5c034717bb16fa1599547c0be7306b1c9fea8ed1495328ab",
                part(1, 1024, "defe84dc282e8c154c909b29989cfa547cbd11f2a978fc15dc09954cd8850b6b"),
                part(2, 1, "6d90fbacc073ee0b4c43f3a3291cecda33764f6d66d14224ad60f471f2c8334b"),
            ),
            fixture(
                5,
                2048,
                "a3ee850d2dcfbca80c9d3b25c7f1ce8f9d104f9897678e6f58fb466ea289d932",
                part(1, 1024, "f5d3ef66e570ab320e8d13ccba675a4ff3b1df248aba5b6a6d4341992a7fab6c"),
                part(2, 1024, "a1d7cd6d8bcfe3ebd3dbef09bebd24a72ba5b968db76eba70d8470732bfe95c5"),
            ),
            fixture(
                6,
                2065,
                "7c532444ccdf2780a0ab7cf3e63d8d304b4bcd87296ad67ba22a14f6ebe6df29",
                part(1, 1024, "bd9b110ac865a980bc399213c439fd3c969cf368f66ac15ee84a39a545f545e3"),
                part(2, 1024, "38f00d24bfad2c1cba218150ac6f7060e8bef8ea506de3e93a630c14512576e8"),
                part(3, 17, "21311d6a193f9d83e374538449ce991e801fde60c87bd186600ba9ca42405f9f"),
            ),
            fixture(
                7,
                2065,
                "d512cdca53b76f40e9853d094b345c87e265703583cf11e8e8eed7fbd12aca38",
                part(1, 1024, "bd9b110ac865a980bc399213c439fd3c969cf368f66ac15ee84a39a545f545e3"),
                part(
                    2,
                    1024,
                    "01b894db08c783b9c4ead7636208a51bf797d31fa2176adeebfbcc8d560c3b6e",
                    "38f00d24bfad2c1cba218150ac6f7060e8bef8ea506de3e93a630c14512576e8",
                ),
                part(3, 17, "21311d6a193f9d83e374538449ce991e801fde60c87bd186600ba9ca42405f9f"),
                declared = "7c532444ccdf2780a0ab7cf3e63d8d304b4bcd87296ad67ba22a14f6ebe6df29",
            ),
        )

    val deletionRecordFields =
        listOf(
            "jobId",
            "conversationFixtureId",
            "deleteResourceBindingSha256",
            "deleteIdempotencyKeyLedgerRef",
            "deleteIdempotencyKeyDigest",
            "deleteRequestDigest",
            "deletionId",
            "profileBindingSha256",
            "endpointId",
            "regionCode",
            "lastReceiptRevision",
        )
    val deletionVisibleSubstatuses =
        listOf(
            DELETE_RECEIPT_POLL_ELIGIBLE,
            "DELETE_WAITING_NETWORK",
            "DELETE_RETRY_SCHEDULED",
            "DELETE_REVALIDATION_REQUIRED",
            "DELETE_USER_ACTION_REQUIRED",
            "DELETE_MANUAL_RETRY_REQUIRED",
        )
    val deletionErrorCodes =
        listOf(
            "CANCEL_NOT_APPLICABLE_DELETE_PENDING",
            "DELETE_PROFILE_REVALIDATION_REQUIRED",
            "DELETE_TLS_TRUST_OR_NAME_REJECTED",
            "DELETE_SCHEMA_OR_FORMAT_REJECTED",
            "DELETE_RESPONSE_INTEGRITY_REJECTED",
            "DELETE_IDEMPOTENCY_PAYLOAD_MISMATCH",
            "DELETE_SCOPE_REVALIDATION_REQUIRED",
            "DELETE_FINITE_BUDGET_EXHAUSTED",
        )
    val outcomeInvariantIds = (1..10).map { "VPN-INV-OUT-${it.toString().padStart(3, '0')}" }

    val authorityFlags =
        listOf(
                "implementationAllowed",
                "implementationAllowedByThisPackage",
                "executionAllowed",
                "measuredExecutionAllowed",
                "harnessExecutionAllowed",
                "networkExecutionAllowed",
                "deviceExecutionAllowed",
                "emulatorExecutionAllowed",
                "vpnExecutionAllowed",
                "providerExecutionAllowed",
                "recoveryExecutionAllowed",
                "dependencyAdmission",
                "productionAdmission",
                "productionApiAdmission",
                "realDataAllowed",
                "credentialsAllowed",
                "billingAllowed",
                "featureFlagActivationAllowed",
                "legalApprovalClaimed",
                "securityApprovalClaimed",
                "formalHumanApprovalClaimed",
                "mergeAuthorized",
            )
            .associateWith { false }

    private const val retryTransitions = "6,9,13,17,21,28,43,34"
    private const val routeWaitTransitions = "5,8,12,16,20,27,32,33"
    private const val processDeathRuleTargets =
        "clientStateMachine.processDeathRule,serverStateMachine.processDeathRule"

    private fun operation(
        id: Int,
        name: String,
        method: HttpMethod,
        route: String,
        path: String = "",
        body: String = "",
        equality: String = "",
        request: String,
        response: String,
        idempotency: String,
        status: Int,
    ) =
        OperationDefinition(
            OperationId("VPN-OP-${id.id()}"),
            name,
            method,
            route,
            csv(path).map {
                val (field, type) = it.split(':')
                PathParameterDefinition(field, PathParameterType.valueOf(type))
            },
            csv(body),
            csv(equality),
            request,
            response,
            idempotency,
            status,
        )

    private fun c(
        id: Int,
        from: Set<ClientState>,
        event: String,
        destination: TransitionDestination,
        resume: ClientState? = null,
        retry: RetryClass? = null,
        substatus: String? = null,
        preserve: Boolean = false,
    ) =
        ClientTransition(
            "VPN-C-TR-${id.id()}",
            from,
            event,
            destination,
            resume,
            retry,
            substatus,
            preserve,
        )

    private fun s(id: Int, from: String, event: String, destination: String) =
        ServerTransition(
            "VPN-S-TR-${id.id()}",
            csv(from).mapTo(linkedSetOf()) { ServerState.valueOf(it) },
            event,
            destination,
        )

    private fun trace(
        id: Int,
        name: String,
        operations: String = "",
        c: String = "",
        s: String = "",
    ) =
        TraceDefinition(
            "VPN-TRACE-${id.id()}",
            name,
            ids("VPN-OP", operations),
            ids("VPN-C-TR", c),
            ids("VPN-S-TR", s),
        )

    private fun fault(
        id: Int,
        boundary: String,
        retry: RetryClass,
        proof: String,
        c: String = "",
        s: String = "",
        t: String = "",
        rules: String = "",
    ) =
        FaultDefinition(
            "VPN-FLT-${id.id()}",
            boundary,
            retry,
            proof,
            ids("VPN-C-TR", c),
            ids("VPN-S-TR", s),
            ids("VPN-TRACE", t),
            csv(rules),
        )

    private fun fixture(
        id: Int,
        length: Int,
        digest: String,
        vararg parts: FixturePart,
        declared: String? = null,
    ) =
        FixtureDefinition(
            "VPN-FIX-${id.id()}",
            length,
            Sha256Hex(digest),
            parts.toList(),
            declared?.let(::Sha256Hex),
        )

    private fun part(number: Int, length: Int, digest: String, declared: String? = null) =
        FixturePart(number, length, Sha256Hex(digest), declared?.let(::Sha256Hex))

    private fun priority(priority: Int, name: String, ids: String) =
        PriorityGroup(priority, name, ids("VPN-C-TR", ids))

    private fun ids(prefix: String, values: String) =
        csv(values).map { "$prefix-${it.toInt().id()}" }

    private fun csv(value: String) = if (value.isEmpty()) emptyList() else value.split(',')

    private fun Int.id() = toString().padStart(3, '0')

    private fun state(value: ClientState) = setOf(value)

    private fun states(values: String) =
        csv(values).mapTo(linkedSetOf()) { ClientState.valueOf(it) }

    private fun fixed(value: ClientState) = TransitionDestination.Fixed(value)
}
