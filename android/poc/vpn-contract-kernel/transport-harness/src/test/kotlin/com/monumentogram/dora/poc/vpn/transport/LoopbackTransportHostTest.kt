package com.monumentogram.dora.poc.vpn.transport

import com.monumentogram.dora.poc.vpn.contract.ArtifactClass
import com.monumentogram.dora.poc.vpn.contract.CanonicalValue
import com.monumentogram.dora.poc.vpn.contract.ConsentProfileBinding
import com.monumentogram.dora.poc.vpn.contract.ConsentPurpose
import com.monumentogram.dora.poc.vpn.contract.ContractCatalog
import com.monumentogram.dora.poc.vpn.contract.ContractOracle
import com.monumentogram.dora.poc.vpn.contract.DoraCanonicalJson
import com.monumentogram.dora.poc.vpn.contract.EffectVector
import com.monumentogram.dora.poc.vpn.contract.EndpointRegionBinding
import com.monumentogram.dora.poc.vpn.contract.FixtureDefinition
import com.monumentogram.dora.poc.vpn.contract.Sha256Hex
import com.monumentogram.dora.poc.vpn.contract.canonicalInteger
import com.monumentogram.dora.poc.vpn.contract.canonicalObject
import com.monumentogram.dora.poc.vpn.contract.canonicalString
import com.monumentogram.dora.poc.vpn.contract.requiredArray
import com.monumentogram.dora.poc.vpn.contract.requiredObject
import com.monumentogram.dora.poc.vpn.contract.requiredString
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ProxySelector
import java.net.Socket
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@Suppress("LargeClass")
internal object LoopbackTransportHostTest {
    private const val CHILD_SUCCESS_MODE = "--child-termination-success"
    private const val CHILD_FAILURE_MODE = "--child-termination-failure"
    private const val MAX_CHILD_OUTPUT_BYTES = 4096
    private val activeServers = CopyOnWriteArrayList<HermeticLoopbackServer>()
    private val requestIdAllocator = HarnessRunRequestIdAllocator("i2-host-run")
    private val exactResponseSchemas =
        mapOf(
            "CreateJobResponse-v0.1" to
                SchemaShape(
                    setOf(
                        "schemaVersion",
                        "jobId",
                        "state",
                        "endpointId",
                        "regionCode",
                        "createdAt",
                        "syntheticEconomicEffectId",
                    )
                ),
            "UploadPlanResponse-v0.1" to
                SchemaShape(
                    setOf(
                        "schemaVersion",
                        "jobId",
                        "uploadId",
                        "planGeneration",
                        "partSizeBytes",
                        "expiresAt",
                        "endpointId",
                        "regionCode",
                        "parts",
                    ),
                    integerFields = setOf("planGeneration", "partSizeBytes"),
                    arrayFields = setOf("parts"),
                ),
            "UploadPartResponse-v0.1" to
                SchemaShape(
                    setOf(
                        "schemaVersion",
                        "uploadId",
                        "partNumber",
                        "byteLength",
                        "sha256",
                        "partReceiptId",
                    ),
                    integerFields = setOf("partNumber", "byteLength"),
                ),
            "CompleteUploadResponse-v0.1" to
                SchemaShape(setOf("schemaVersion", "jobId", "uploadId", "commitId", "state")),
            "JobStatusResponse-v0.1" to
                SchemaShape(
                    setOf(
                        "schemaVersion",
                        "jobId",
                        "state",
                        "revision",
                        "statusEtag",
                        "retryHintClass",
                    ),
                    integerFields = setOf("revision"),
                    nullableStringFields = setOf("retryHintClass"),
                ),
            "ResultResponse-v0.1" to
                SchemaShape(
                    setOf(
                        "schemaVersion",
                        "jobId",
                        "resultId",
                        "artifactClass",
                        "byteLength",
                        "sha256",
                        "body",
                    ),
                    integerFields = setOf("byteLength"),
                    arrayFields = setOf("body"),
                ),
            "CancelResponse-v0.1" to
                SchemaShape(
                    setOf(
                        "schemaVersion",
                        "jobId",
                        "cancelReceiptId",
                        "state",
                        "winningTerminalCommit",
                    )
                ),
            "DeleteResponse-v0.1" to
                SchemaShape(setOf("schemaVersion", "deletionId", "conversationFixtureId", "state")),
            "DeletionReceiptResponse-v0.1" to
                SchemaShape(
                    setOf(
                        "schemaVersion",
                        "deletionId",
                        "deletionReceiptId",
                        "state",
                        "verifiedAbsent",
                    ),
                    booleanFields = setOf("verifiedAbsent"),
                    nullableStringFields = setOf("deletionReceiptId"),
                ),
            "ErrorResponse-v0.1" to
                SchemaShape(
                    setOf(
                        "schemaVersion",
                        "errorCode",
                        "retryClass",
                        "operationClass",
                        "requestDigest",
                        "contentFreeDetailCode",
                    )
                ),
        )

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size == 2 && args.first() in setOf(CHILD_SUCCESS_MODE, CHILD_FAILURE_MODE)) {
            runTerminationProbeChild(
                injectFailure = args.first() == CHILD_FAILURE_MODE,
                portSink = Path.of(args.last()),
            )
            return
        }
        try {
            checkThat(args.isEmpty(), "runner arguments are frozen")
            runScenario(::preflightAndTransportBoundary)
            runScenario(::lostResponseReplayAndMismatch)
            runScenario(::multipartSnapshotCompletionAndReads)
            runScenario(::finiteRetryProfiles)
            runScenario(::cancellationAndDeletionLifecycle)
            runScenario(::constructorAndCleanupFailureBoundary)
            forkTerminationAndSocketRegression()
            assertHarnessRunRequestIds()
            machineEvidenceParity()
            println("PASS hermetic-loopback-transport-harness")
        } catch (failure: Throwable) {
            System.err.println(contentFreeFailureMarker(failure))
            kotlin.system.exitProcess(1)
        }
    }

    private fun runTerminationProbeChild(injectFailure: Boolean, portSink: Path) {
        try {
            runScenario {
                val server = trackedServer(SyntheticContractService())
                val normalizedSink = portSink.toAbsolutePath().normalize()
                val expectedParent =
                    Path.of(System.getProperty("user.dir"))
                        .resolve(
                            "poc/vpn-contract-kernel/transport-harness/build/termination-probe"
                        )
                        .toAbsolutePath()
                        .normalize()
                checkThat(normalizedSink.parent == expectedParent, "child port sink is task-owned")
                checkThat(Files.isRegularFile(normalizedSink), "child port sink exists")
                Files.writeString(normalizedSink, server.port.toString())
                if (injectFailure) {
                    throw AssertionError("Synthetic injected assertion")
                }
            }
            println("PASS hermetic-loopback-child-termination-probe")
        } catch (failure: Throwable) {
            System.err.println(contentFreeFailureMarker(failure))
            kotlin.system.exitProcess(1)
        }
    }

    private fun assertHarnessRunRequestIds() {
        val requestIds = requestIdAllocator.requestIds()
        checkThat(requestIds.size > MAX_ATTEMPTS, "multiple clients emitted request IDs")
        checkEquals(requestIds.size, requestIds.toSet().size, "harness-run request IDs unique")
        requestIds.forEachIndexed { index, requestId ->
            checkEquals(
                "client-request-i2-host-run-${(index + 1).toString().padStart(6, '0')}",
                requestId,
                "harness-run request ID sequence",
            )
        }
    }

    @Suppress("LongMethod", "NestedBlockDepth")
    private fun forkTerminationAndSocketRegression() {
        val probeDirectory =
            Path.of(System.getProperty("user.dir"))
                .resolve("poc/vpn-contract-kernel/transport-harness/build/termination-probe")
                .toAbsolutePath()
                .normalize()
        Files.createDirectories(probeDirectory)
        try {
            listOf(CHILD_SUCCESS_MODE to 0, CHILD_FAILURE_MODE to 1).forEach { (mode, expectedExit)
                ->
                val portSink = Files.createTempFile(probeDirectory, "child-", ".port")
                try {
                    val javaExecutable =
                        Path.of(
                                System.getProperty("java.home"),
                                "bin",
                                if (System.getProperty("os.name").startsWith("Windows")) {
                                    "java.exe"
                                } else {
                                    "java"
                                },
                            )
                            .toFile()
                    checkThat(javaExecutable.isFile, "child Java executable exists")
                    val process =
                        ProcessBuilder(
                                javaExecutable.absolutePath,
                                "--add-modules=java.net.http,jdk.httpserver",
                                "-cp",
                                System.getProperty("java.class.path"),
                                LoopbackTransportHostTest::class.java.name,
                                mode,
                                portSink.toString(),
                            )
                            .redirectErrorStream(true)
                            .start()
                    val processId = process.pid()
                    val knownDescendants = process.toHandle().descendants().toList()
                    try {
                        val finished =
                            process.waitFor(SCENARIO_DEADLINE.toMillis(), TimeUnit.MILLISECONDS)
                        if (!finished) {
                            process.destroyForcibly()
                            checkThat(
                                process.waitFor(2, TimeUnit.SECONDS),
                                "child force-termination completed",
                            )
                            error("Child termination hard bound exceeded")
                        }
                        val outputBytes =
                            process.inputStream.use { it.readNBytes(MAX_CHILD_OUTPUT_BYTES + 1) }
                        checkThat(
                            outputBytes.size <= MAX_CHILD_OUTPUT_BYTES,
                            "child output remained bounded",
                        )
                        val output = String(outputBytes, Charsets.UTF_8)
                        val portText = Files.readString(portSink)
                        checkThat(
                            portText.matches(Regex("[0-9]{1,5}")),
                            "one child port captured",
                        )
                        val port = portText.toInt()
                        checkThat(port in 1..65_535, "child ephemeral port is valid")
                        checkEquals(expectedExit, process.exitValue(), "child expected exit")
                        checkThat(!process.isAlive, "child process terminated")
                        checkThat(
                            !ProcessHandle.of(processId).map(ProcessHandle::isAlive).orElse(false),
                            "child process handle is not alive",
                        )
                        checkThat(
                            knownDescendants.none(ProcessHandle::isAlive),
                            "child descendants terminated",
                        )
                        val expectedMarker =
                            if (expectedExit == 0) {
                                "PASS hermetic-loopback-child-termination-probe"
                            } else {
                                "FAIL hermetic-loopback-transport-harness"
                            }
                        checkEquals(
                            expectedMarker,
                            output.trim(),
                            "child emitted constant-only result",
                        )
                        assertPortClosed(port)
                    } finally {
                        if (process.isAlive) {
                            process.destroyForcibly()
                            checkThat(
                                process.waitFor(2, TimeUnit.SECONDS),
                                "child finalizer terminated process",
                            )
                        }
                        process.inputStream.close()
                        process.outputStream.close()
                        process.errorStream.close()
                    }
                } finally {
                    Files.deleteIfExists(portSink)
                }
            }
            Files.list(probeDirectory).use { entries ->
                checkThat(entries.findAny().isEmpty, "child probe directory is empty")
            }
        } finally {
            Files.deleteIfExists(probeDirectory)
        }
    }

    @Suppress("LongMethod")
    private fun preflightAndTransportBoundary() {
        val guard = ContractPreflightGuard(Instant.parse("2026-06-01T00:00:00Z"))
        val valid = profile()
        checkThat(guard.evaluate(valid, ENDPOINT_BINDING, true).transportAllowed, "valid profile")
        checkNoSend(guard.evaluate(null, ENDPOINT_BINDING, true), "VPN-FLT-001")
        checkNoSend(
            guard.evaluate(profile(expiresAt = "2026-01-02T00:00:00Z"), ENDPOINT_BINDING, true),
            "VPN-FLT-002",
        )
        checkNoSend(
            guard.evaluate(
                valid,
                EndpointRegionBinding("endpoint-synthetic-b", "SYN-REGION-B"),
                true,
            ),
            "VPN-FLT-003",
        )
        val pending =
            guard.evaluate(
                valid,
                ENDPOINT_BINDING,
                endpointAllowlisted = false,
                deletionPending = true,
            )
        checkEquals("DELETE_PENDING", pending.clientState, "pending delete state")
        checkEquals("DELETE_REVALIDATION_REQUIRED", pending.visibleSubstatus, "pending substatus")
        checkThat(pending.preserveDeletionRecord && !pending.transportAllowed, "pending preserved")
        val dns = guard.injectedDnsUnavailable()
        checkEquals("WAIT_NETWORK", dns.retryClass, "VPN-FLT-004 semantic class")
        checkThat(!dns.transportAllowed, "semantic DNS category does not send")

        val service = SyntheticContractService()
        val server =
            trackedServer(
                service,
                listOf(
                    FaultDirective("REDIRECT-NEGATIVE", "CREATE_JOB", FaultAction.EXTERNAL_REDIRECT)
                ),
            )
        val port = server.port
        val priorDefault = ProxySelector.getDefault()
        val poison = PoisonDefaultProxySelector()
        ProxySelector.setDefault(poison)
        try {
            HermeticLoopbackClient(server.endpoint, requestIdAllocator).use { client ->
                val invalidUris =
                    listOf(
                        URI("http://localhost:$port/v1/processing-jobs"),
                        URI("http://[::1]:$port/v1/processing-jobs"),
                        URI("http://127.0.0.2:$port/v1/processing-jobs"),
                        URI("http://example.invalid:$port/v1/processing-jobs"),
                        URI("https://$LOOPBACK_HOST:$port/v1/processing-jobs"),
                        URI("http://user@$LOOPBACK_HOST:$port/v1/processing-jobs"),
                        URI("http://$LOOPBACK_HOST:${differentPort(port)}/v1/processing-jobs"),
                        URI("http://$LOOPBACK_HOST:$port/v1/processing-jobs#fragment"),
                        URI("http://$LOOPBACK_HOST:$port/v1/processing-jobs?query=forbidden"),
                    )
                invalidUris.forEach { uri ->
                    expectPreSendReject {
                        client.send(createRequest(uri, fixture(), "key-synthetic-boundary"))
                    }
                }
                BOUNDED_CREDENTIAL_HEADER_DENYLIST.forEach { header ->
                    expectPreSendReject {
                        client.send(
                            createRequest(
                                    server.endpoint.child("/v1/processing-jobs"),
                                    fixture(),
                                    "key-synthetic-boundary",
                                )
                                .copy(
                                    headers =
                                        commonHeaders("key-synthetic-boundary") +
                                            (header to "synthetic-forbidden")
                                )
                        )
                    }
                }
                SINGLETON_PROTOCOL_HEADERS.forEach { header ->
                    val withoutHeader =
                        commonHeaders("key-synthetic-boundary").filterKeys {
                            !it.equals(header, ignoreCase = true)
                        }
                    expectPreSendReject {
                        client.send(
                            createRequest(
                                    server.endpoint.child("/v1/processing-jobs"),
                                    fixture(),
                                    "key-synthetic-boundary",
                                )
                                .copy(
                                    headers =
                                        withoutHeader +
                                            mapOf(
                                                header to "synthetic-singleton-a",
                                                header.uppercase() to "synthetic-singleton-b",
                                            )
                                )
                        )
                    }
                }
                expectPreSendReject {
                    client.send(
                        createRequest(
                                server.endpoint.child("/v1/processing-jobs"),
                                fixture(),
                                "key-synthetic-boundary",
                            )
                            .copy(body = ByteArray(MAX_HTTP_BYTES + 1))
                    )
                }
                checkEquals(0, client.sendCount(), "invalid requests have zero sends")

                val directRequest =
                    createRequest(
                        server.endpoint.child("/v1/processing-jobs"),
                        fixture(),
                        "key-synthetic-missing-request-id",
                    )
                val missingRequestId =
                    service.handle(
                        "POST",
                        "/v1/processing-jobs",
                        mapOf(
                            "content-type" to "application/json",
                            "x-synthetic-tenant-id" to SYNTHETIC_TENANT,
                            "x-profile-binding-sha256" to PROFILE_DIGEST.value,
                            "idempotency-key" to "key-synthetic-missing-request-id",
                        ),
                        directRequest.body,
                    )
                checkEquals(400, missingRequestId.status, "missing request ID rejected")
                assertExactSchema(missingRequestId, "ErrorResponse-v0.1")
                val missingIdempotencyKey =
                    service.handle(
                        "POST",
                        "/v1/processing-jobs",
                        mapOf(
                            "content-type" to "application/json",
                            "x-client-request-id" to "client-request-synthetic-direct-0001",
                            "x-synthetic-tenant-id" to SYNTHETIC_TENANT,
                            "x-profile-binding-sha256" to PROFILE_DIGEST.value,
                        ),
                        directRequest.body,
                    )
                checkEquals(400, missingIdempotencyKey.status, "missing idempotency key rejected")
                assertExactSchema(missingIdempotencyKey, "ErrorResponse-v0.1")

                val redirect =
                    client.send(
                        createRequest(
                            server.endpoint.child("/v1/processing-jobs"),
                            fixture(),
                            "key-synthetic-redirect",
                        )
                    )
                checkEquals(302, redirect.status, "redirect returned without follow")
                checkEquals(1, client.sendCount(), "redirect single send")
                val crossProfile =
                    client.send(
                        createRequest(
                                server.endpoint.child("/v1/processing-jobs"),
                                fixture(),
                                "key-synthetic-cross-profile",
                            )
                            .copy(
                                headers =
                                    commonHeaders("key-synthetic-cross-profile") +
                                        ("X-Synthetic-Tenant-Id" to "tenant-synthetic-b")
                            )
                    )
                checkEquals(404, crossProfile.status, "cross-profile non-enumerating rejection")
                assertExactSchema(crossProfile, "ErrorResponse-v0.1")
                checkThat(client.explicitProxySelectionCount() > 0, "explicit NO_PROXY selected")
                checkEquals(0, poison.callCount(), "default proxy selector unused")
                checkEquals(0, service.effectTotals().resources, "redirect has no effect")
            }
        } finally {
            ProxySelector.setDefault(priorDefault)
            server.close()
        }
        assertPortClosed(port)
    }

    @Suppress("LongMethod")
    private fun cancellationAndDeletionLifecycle() {
        val service = SyntheticContractService()
        val receiptFaults =
            (1..MAX_ATTEMPTS).map {
                FaultDirective(
                    "I2-DELETE-RECEIPT-5XX-$it",
                    "POLL_DELETION_RECEIPT",
                    FaultAction.RETURN_503,
                )
            }
        val server = trackedServer(service, receiptFaults)
        val port = server.port
        HermeticLoopbackClient(server.endpoint, requestIdAllocator).use { client ->
            val firstJob = createOnly(client, server.endpoint, "cancel-a")
            val secondJob = createOnly(client, server.endpoint, "cancel-b")
            val beforeInvalidDelete = service.snapshot()
            val invalidDelete =
                client.send(
                    deleteRequest(
                        server.endpoint,
                        conversationFixtureId(secondJob),
                        "key-synthetic-delete-invalid-source",
                    )
                )
            checkEquals(409, invalidDelete.status, "first delete source state rejected")
            assertExactSchema(invalidDelete, "ErrorResponse-v0.1")
            checkEquals(
                beforeInvalidDelete.effects,
                service.effectTotals(),
                "invalid first delete zero effects",
            )
            checkEquals(
                beforeInvalidDelete.deletions,
                service.snapshot().deletions,
                "invalid first delete zero records",
            )
            checkEquals("CREATED", service.job(secondJob)?.state, "invalid delete zero state")
            val cancelKey = "key-synthetic-cancel-idempotent"
            val firstCancel = client.send(cancelRequest(server.endpoint, firstJob, cancelKey))
            checkEquals(202, firstCancel.status, "cancel committed")
            assertExactSchema(firstCancel, "CancelResponse-v0.1")
            assertReplayMarker(firstCancel, expected = false)
            val repeatedCancel = client.send(cancelRequest(server.endpoint, firstJob, cancelKey))
            assertExactSchema(repeatedCancel, "CancelResponse-v0.1")
            assertReplayMarker(repeatedCancel, expected = true)
            checkThat(firstCancel.body.contentEquals(repeatedCancel.body), "cancel replay stable")
            val beforeTargetMismatch = service.effectTotals()
            val cancelMismatch = client.send(cancelRequest(server.endpoint, secondJob, cancelKey))
            checkEquals(409, cancelMismatch.status, "cancel target mismatch")
            assertExactSchema(cancelMismatch, "ErrorResponse-v0.1")
            checkEquals(
                beforeTargetMismatch,
                service.effectTotals(),
                "cancel mismatch zero effects",
            )
            checkEquals("CREATED", service.job(secondJob)?.state, "cancel mismatch zero state")

            val cancelFirst = createAndUpload(client, server.endpoint, "cancel-first")
            checkEquals(
                202,
                client
                    .send(
                        cancelRequest(
                            server.endpoint,
                            cancelFirst.jobId,
                            "key-synthetic-cancel-first",
                        )
                    )
                    .status,
                "cancel-first committed",
            )
            val beforeLateComplete = service.effectTotals()
            val lateComplete =
                completeRequest(
                    server.endpoint,
                    cancelFirst.jobId,
                    cancelFirst.uploadId,
                    cancelFirst.parts,
                    "key-synthetic-late-complete",
                    cancelFirst.parts.sumOf { it.bytes.size },
                )
            checkEquals(409, client.send(lateComplete).status, "cancel-first wins")
            checkEquals(beforeLateComplete, service.effectTotals(), "late complete zero effects")
            checkThat(
                service.job(cancelFirst.jobId)?.resultId == null,
                "cancel-first has no result",
            )

            val resultFirst = createAndUpload(client, server.endpoint, "result-first")
            val complete =
                completeRequest(
                    server.endpoint,
                    resultFirst.jobId,
                    resultFirst.uploadId,
                    resultFirst.parts,
                    "key-synthetic-result-first-complete",
                    resultFirst.parts.sumOf { it.bytes.size },
                )
            val economicBeforeComplete = service.effectTotals().economicEffects
            val completeResponse = client.send(complete)
            checkEquals(202, completeResponse.status, "result-first committed")
            assertExactSchema(completeResponse, "CompleteUploadResponse-v0.1")
            assertReplayMarker(completeResponse, expected = false)
            checkEquals(
                economicBeforeComplete,
                service.effectTotals().economicEffects,
                "completion adds no economic event",
            )
            val beforeLateCancel = service.effectTotals()
            checkEquals(
                409,
                client
                    .send(
                        cancelRequest(
                            server.endpoint,
                            resultFirst.jobId,
                            "key-synthetic-late-cancel",
                        )
                    )
                    .status,
                "result-first wins",
            )
            checkEquals(beforeLateCancel, service.effectTotals(), "late cancel zero effects")
            checkThat(
                service.job(resultFirst.jobId)?.resultId != null,
                "result-first remains stable",
            )

            val pollBeforeDelete =
                client.send(
                    readRequest(server.endpoint, "/v1/processing-jobs/${resultFirst.jobId}")
                )
            assertExactSchema(pollBeforeDelete, "JobStatusResponse-v0.1")
            val resultRequest =
                readRequest(server.endpoint, "/v1/processing-jobs/${resultFirst.jobId}/result")
            val resultResponse = client.send(resultRequest)
            val resultBody = assertExactSchema(resultResponse, "ResultResponse-v0.1")
            val resultBytes =
                resultBody
                    .requiredArray("body")
                    .map { value ->
                        (value as CanonicalValue.IntegerValue).value.toInt().toByte()
                    }
                    .toByteArray()
            val expectedResult = ContractOracle.materializeFixture(fixture())
            checkThat(resultBytes.contentEquals(expectedResult), "exact result synthetic bytes")
            checkEquals(
                ContractOracle.sha256Hex(expectedResult).value,
                resultBody.requiredString("sha256"),
                "result digest",
            )
            val repeatedResult = client.send(resultRequest)
            assertExactSchema(repeatedResult, "ResultResponse-v0.1")
            checkThat(
                resultResponse.body.contentEquals(repeatedResult.body),
                "read-only result replay stable",
            )

            val conversation = conversationFixtureId(resultFirst.jobId)
            val delete = deleteRequest(server.endpoint, conversation, "key-synthetic-delete")
            val economicBeforeDelete = service.effectTotals().economicEffects
            val acceptedDelete = client.send(delete)
            checkEquals(202, acceptedDelete.status, "delete accepted")
            assertExactSchema(acceptedDelete, "DeleteResponse-v0.1")
            assertReplayMarker(acceptedDelete, expected = false)
            val repeatedDelete = client.send(delete)
            assertExactSchema(repeatedDelete, "DeleteResponse-v0.1")
            assertReplayMarker(repeatedDelete, expected = true)
            checkThat(
                acceptedDelete.body.contentEquals(repeatedDelete.body),
                "delete replay stable",
            )
            val deletionId = objectBody(acceptedDelete).requiredString("deletionId")
            val effectsBeforeDeleteMismatch = service.effectTotals()
            val deletionIdsBeforeMismatch = service.snapshot().deletions.keys
            val deleteMismatch =
                client.send(
                    deleteRequest(
                        server.endpoint,
                        conversationFixtureId(secondJob),
                        "key-synthetic-delete",
                    )
                )
            checkEquals(409, deleteMismatch.status, "delete target mismatch")
            assertExactSchema(deleteMismatch, "ErrorResponse-v0.1")
            checkEquals(
                effectsBeforeDeleteMismatch,
                service.effectTotals(),
                "delete mismatch zero effects",
            )
            checkEquals(
                deletionIdsBeforeMismatch,
                service.snapshot().deletions.keys,
                "delete mismatch zero records",
            )
            val newKeyDelete =
                client.send(
                    deleteRequest(
                        server.endpoint,
                        conversation,
                        "key-synthetic-delete-new-scope",
                    )
                )
            assertExactSchema(newKeyDelete, "DeleteResponse-v0.1")
            assertReplayMarker(newKeyDelete, expected = false)
            checkEquals(
                deletionId,
                objectBody(newKeyDelete).requiredString("deletionId"),
                "new-key delete converges",
            )
            checkEquals(
                economicBeforeDelete,
                service.effectTotals().economicEffects,
                "delete adds no economic event",
            )
            val afterDeleteEffects = service.effectTotals()
            val beforePendingCancel = service.snapshot()
            val faultsBeforePendingCancel = server.contentFreeFaultLedger()
            val remainingFaultsBeforePendingCancel = server.remainingFaults()
            val pendingCancel =
                client.send(
                    cancelRequest(
                        server.endpoint,
                        resultFirst.jobId,
                        "key-synthetic-cancel-pending-delete",
                    )
                )
            checkEquals(409, pendingCancel.status, "cancel during delete rejected")
            val pendingCancelBody = assertExactSchema(pendingCancel, "ErrorResponse-v0.1")
            checkEquals(
                "CANCEL_NOT_APPLICABLE_DELETE_PENDING",
                pendingCancelBody.requiredString("errorCode"),
                "pending delete cancellation code",
            )
            val afterPendingCancel = service.snapshot()
            checkEquals(
                afterDeleteEffects,
                service.effectTotals(),
                "pending delete cancel zero effects",
            )
            checkEquals(
                beforePendingCancel.ledger,
                afterPendingCancel.ledger,
                "cancel ledger stable",
            )
            checkEquals(
                beforePendingCancel.deletions,
                afterPendingCancel.deletions,
                "cancel full deletion record stable",
            )
            checkEquals(
                beforePendingCancel.jobs.getValue(resultFirst.jobId).state,
                afterPendingCancel.jobs.getValue(resultFirst.jobId).state,
                "cancel job state stable",
            )
            checkEquals(
                beforePendingCancel.jobs.getValue(resultFirst.jobId).revision,
                afterPendingCancel.jobs.getValue(resultFirst.jobId).revision,
                "cancel job revision stable",
            )
            checkEquals(
                beforePendingCancel.mutationResponses.keys,
                afterPendingCancel.mutationResponses.keys,
                "cancel response ledger scopes stable",
            )
            checkEquals(
                beforePendingCancel.mutationResponses.mapValues {
                    ContractOracle.sha256Hex(it.value)
                },
                afterPendingCancel.mutationResponses.mapValues {
                    ContractOracle.sha256Hex(it.value)
                },
                "cancel response ledger bodies stable",
            )
            checkEquals(
                beforePendingCancel.requestLedger.size + 1,
                afterPendingCancel.requestLedger.size,
                "cancel audit ledger records one rejection",
            )
            val pendingCancelLedgerEntry = afterPendingCancel.requestLedger.last()
            checkEquals(
                "REJECTED_NO_STATE_CHANGE",
                pendingCancelLedgerEntry.disposition,
                "cancel rejection disposition",
            )
            checkEquals(
                EffectVector.ZERO,
                pendingCancelLedgerEntry.effectDelta,
                "cancel ledger zero",
            )
            checkEquals(
                faultsBeforePendingCancel,
                server.contentFreeFaultLedger(),
                "cancel fault ledger stable",
            )
            checkEquals(
                remainingFaultsBeforePendingCancel,
                server.remainingFaults(),
                "cancel fault queue stable",
            )
            checkEquals(
                "DELETE_PENDING",
                service.deletion(deletionId)?.state,
                "delete record preserved",
            )
            val deletionState = checkNotNull(service.deletion(deletionId))
            checkEquals(
                PROFILE_DIGEST,
                deletionState.profileBindingSha256,
                "delete profile binding",
            )
            checkEquals(
                ENDPOINT_BINDING.endpointId,
                deletionState.endpointId,
                "delete endpoint binding",
            )
            checkEquals(
                ENDPOINT_BINDING.regionCode,
                deletionState.regionCode,
                "delete region binding",
            )
            checkEquals(
                ContractOracle.deleteResourceBindingDigest(
                    SYNTHETIC_TENANT,
                    PROFILE_DIGEST,
                    ENDPOINT_BINDING,
                    conversation,
                ),
                deletionState.deleteResourceBindingSha256,
                "delete resource binding",
            )
            checkEquals(
                setOf(
                    ContractOracle.sha256Utf8("key-synthetic-delete"),
                    ContractOracle.sha256Utf8("key-synthetic-delete-new-scope"),
                ),
                deletionState.idempotencyKeyDigests,
                "delete key digest bindings",
            )

            val receiptRequest = readRequest(server.endpoint, "/v1/deletions/$deletionId")
            val exhausted = expectBudgetExhausted {
                RetryingLoopbackTransport(client, DeterministicRetryScheduler())
                    .execute(receiptRequest)
            }
            checkEquals(MAX_ATTEMPTS, exhausted.attempts, "delete receipt finite budget")
            checkEquals(
                "DELETE_PENDING",
                service.deletion(deletionId)?.state,
                "exhaustion preserves delete",
            )
            val resumed =
                RetryingLoopbackTransport(client, DeterministicRetryScheduler())
                    .execute(receiptRequest)
            checkEquals(200, resumed.status, "explicit positive budget resumes receipt")
            assertExactSchema(resumed, "DeletionReceiptResponse-v0.1")
            checkEquals("DELETED", service.deletion(deletionId)?.state, "verified receipt deletes")
            val stableReceipt = client.send(receiptRequest)
            assertExactSchema(stableReceipt, "DeletionReceiptResponse-v0.1")
            checkThat(resumed.body.contentEquals(stableReceipt.body), "deletion receipt stable")
            checkEquals(
                afterDeleteEffects,
                service.effectTotals(),
                "receipt has no economic effect",
            )
            checkEquals(1, service.effectTotals().deletionRecords, "one deletion effect")
            val deletedPoll =
                client.send(
                    readRequest(server.endpoint, "/v1/processing-jobs/${resultFirst.jobId}")
                )
            checkEquals(404, deletedPoll.status, "deleted job poll unavailable")
            assertExactSchema(deletedPoll, "ErrorResponse-v0.1")
            val deletedFetch = client.send(resultRequest)
            checkEquals(404, deletedFetch.status, "deleted result unavailable")
            assertExactSchema(deletedFetch, "ErrorResponse-v0.1")
            val verifiedSnapshot = service.snapshot()
            val tombstone = checkNotNull(verifiedSnapshot.jobs[resultFirst.jobId])
            checkEquals("DELETED", tombstone.state, "verified tombstone state")
            checkThat(tombstone.fixtureId == null, "verified tombstone fixture purged")
            checkThat(tombstone.totalByteLength == null, "verified tombstone length purged")
            checkThat(tombstone.totalSha256 == null, "verified tombstone digest purged")
            checkThat(tombstone.uploadId == null, "verified tombstone upload purged")
            checkEquals(0, tombstone.planGeneration, "verified tombstone plan purged")
            checkThat(tombstone.parts.isEmpty(), "verified tombstone part bodies purged")
            checkThat(tombstone.commitId == null, "verified tombstone commit purged")
            checkThat(tombstone.resultId == null, "verified tombstone result purged")
            checkThat(tombstone.cancelReceiptId == null, "verified tombstone cancel purged")
            checkThat(
                service.resultBytes(resultFirst.jobId) == null,
                "result reconstruction purged",
            )

            val replayedPlan =
                client.send(
                    uploadPlanRequest(
                        server.endpoint,
                        resultFirst.jobId,
                        fixture(),
                        "key-synthetic-plan-result-first",
                    )
                )
            checkEquals(201, replayedPlan.status, "historical plan replay stable")
            assertReplayMarker(replayedPlan, expected = true)
            resultFirst.parts.forEach { part ->
                val replayedPart =
                    client.send(
                        partRequest(
                            server.endpoint,
                            resultFirst.uploadId,
                            part.ordinal,
                            part.bytes,
                            "key-synthetic-part-result-first-${part.ordinal}",
                        )
                    )
                checkEquals(200, replayedPart.status, "historical part replay stable")
                assertReplayMarker(replayedPart, expected = true)
            }
            val replayedComplete = client.send(complete)
            checkEquals(202, replayedComplete.status, "historical completion replay stable")
            assertReplayMarker(replayedComplete, expected = true)
            val replayedDeleteAfterReceipt = client.send(delete)
            checkThat(
                acceptedDelete.body.contentEquals(replayedDeleteAfterReceipt.body),
                "historical delete replay remains original",
            )
            assertReplayMarker(replayedDeleteAfterReceipt, expected = true)

            val beforeAbsorbingMutations = service.snapshot()
            val rejectedAfterDelete =
                listOf(
                    client.send(
                        uploadPlanRequest(
                            server.endpoint,
                            resultFirst.jobId,
                            fixture(),
                            "key-synthetic-plan-after-delete",
                        )
                    ),
                    client.send(
                        partRequest(
                            server.endpoint,
                            resultFirst.uploadId,
                            1,
                            resultFirst.parts.first().bytes,
                            "key-synthetic-part-after-delete",
                        )
                    ),
                    client.send(
                        complete.copy(
                            headers = commonHeaders("key-synthetic-complete-after-delete")
                        )
                    ),
                    client.send(
                        cancelRequest(
                            server.endpoint,
                            resultFirst.jobId,
                            "key-synthetic-cancel-after-delete",
                        )
                    ),
                )
            checkEquals(
                listOf(409, 404, 409, 409),
                rejectedAfterDelete.map { it.status },
                "absorbing tombstone mutation statuses",
            )
            rejectedAfterDelete.forEach { assertExactSchema(it, "ErrorResponse-v0.1") }
            val deleteAfterReceipt =
                client.send(
                    deleteRequest(
                        server.endpoint,
                        conversation,
                        "key-synthetic-delete-after-receipt",
                    )
                )
            val deleteAfterReceiptBody =
                assertExactSchema(deleteAfterReceipt, "DeleteResponse-v0.1")
            assertReplayMarker(deleteAfterReceipt, expected = false)
            checkEquals(
                deletionId,
                deleteAfterReceiptBody.requiredString("deletionId"),
                "deleted repeat ID",
            )
            checkEquals(
                "DELETED",
                deleteAfterReceiptBody.requiredString("state"),
                "deleted repeat truth",
            )
            val afterAbsorbingMutations = service.snapshot()
            checkEquals(
                beforeAbsorbingMutations.jobs.getValue(resultFirst.jobId),
                afterAbsorbingMutations.jobs.getValue(resultFirst.jobId),
                "absorbing tombstone job unchanged",
            )
            checkEquals(
                beforeAbsorbingMutations.deletions.keys,
                afterAbsorbingMutations.deletions.keys,
                "absorbing tombstone deletion records unchanged",
            )
            checkEquals(
                beforeAbsorbingMutations.deletions.getValue(deletionId).receiptRevision,
                afterAbsorbingMutations.deletions.getValue(deletionId).receiptRevision,
                "absorbing tombstone receipt unchanged",
            )
            checkEquals(
                beforeAbsorbingMutations.effects,
                afterAbsorbingMutations.effects,
                "absorbing tombstone effect totals unchanged",
            )
            val jobs = service.snapshot().jobs.values
            checkEquals(
                jobs.size,
                jobs.map { it.syntheticEconomicEffectId }.toSet().size,
                "one stable economic ID per job",
            )
            checkEquals(
                jobs.size,
                service.effectTotals().economicEffects,
                "one economic event per logical job",
            )
        }
        server.close()
        assertPortClosed(port)
    }

    @Suppress("LongMethod")
    private fun machineEvidenceParity() {
        val root = Path.of(checkNotNull(System.getProperty("dora.repo.root")))
        val evidencePath =
            root.resolve(
                "docs/evidence/poc-vpn-001/loopback-transport-implementation-evidence-stage0-v0.1.json"
            )
        val evidenceText = Files.readString(evidencePath)
        checkThat(!evidenceText.contains(LOOPBACK_HOST), "evidence excludes raw loopback address")
        val evidence = DoraCanonicalJson.parseStrict(evidenceText) as CanonicalValue.ObjectValue
        val harnessKotlinPaths =
            evidence
                .requiredArray("implementationPins")
                .map { it as CanonicalValue.ObjectValue }
                .map { it.requiredString("path") }
                .filter { it.contains("/transport-harness/") && it.endsWith(".kt") }
        checkThat(
            harnessKotlinPaths.all { path ->
                !Files.readString(root.resolve(path)).contains("@file:" + "Suppress")
            },
            "harness forbids file-level suppressions",
        )
        checkEquals(
            38,
            evidence.requiredObject("staticAnalysisSuppressions").requiredArray("entries").size,
            "suppression inventory count",
        )
        checkEquals(
            ContractCatalog.CONTRACT_ID,
            evidence.requiredString("contractId"),
            "evidence contract",
        )
        val owner = evidence.requiredObject("ownerAuthorization")
        val trueFlags =
            owner.entries
                .filter { it.second is CanonicalValue.BooleanValue }
                .filter { (it.second as CanonicalValue.BooleanValue).value }
                .map { it.first }
                .toSet()
        checkEquals(
            setOf(
                "i2ImplementationAllowed",
                "i2HarnessExecutionAllowed",
                "i2LoopbackNetworkExecutionAllowed",
            ),
            trueFlags,
            "exact I2 authority",
        )
        val broader = evidence.requiredObject("broaderAuthorityFlags")
        checkEquals(ContractCatalog.authorityFlags.keys, broader.keys(), "broader authority keys")
        checkThat(
            broader.entries.all { (_, value) ->
                value is CanonicalValue.BooleanValue && !value.value
            },
            "broader authority false",
        )
        checkThat(
            !(evidence.requiredObject("review").value("formalReviewer")
                    as CanonicalValue.BooleanValue)
                .value,
            "formal reviewer false",
        )
        checkEquals(
            ContractCatalog.operations.map { it.id.value },
            evidence.requiredArray("includedOperationIds").map {
                (it as CanonicalValue.StringValue).value
            },
            "operation ID parity",
        )
        checkEquals(
            ContractCatalog.serverTransitions
                .single { it.id == "VPN-S-TR-013" }
                .from
                .map { it.name }
                .toSet(),
            DELETE_COMMIT_SOURCE_STATES,
            "first delete exact source states",
        )
        val faultCoverage = evidence.requiredObject("faultCoverage")
        val faultIds =
            listOf(
                    "noSendSemantic",
                    "semanticCategoryOnlyNoDnsLookup",
                    "hermeticLoopbackExecuted",
                    "notRun",
                )
                .flatMap { name ->
                    faultCoverage.requiredArray(name).map {
                        (it as CanonicalValue.StringValue).value
                    }
                }
        checkEquals(
            ContractCatalog.faults.map { it.id }.toSet(),
            faultIds.toSet(),
            "fault ID parity",
        )
        checkEquals(faultIds.size, faultIds.toSet().size, "fault ID uniqueness")
        val notRunFaults =
            faultCoverage
                .requiredArray("notRun")
                .map {
                    (it as CanonicalValue.StringValue).value
                }
                .toSet()
        checkThat(
            notRunFaults.containsAll(
                setOf(
                    "VPN-FLT-015",
                    "VPN-FLT-026",
                    "VPN-FLT-027",
                    "VPN-FLT-031",
                    "VPN-FLT-034",
                )
            ),
            "partial atomic faults remain NOT_RUN",
        )
        checkThat(
            "VPN-FLT-032" in
                faultCoverage.requiredArray("hermeticLoopbackExecuted").map {
                    (it as CanonicalValue.StringValue).value
                },
            "exact result loss remains executed",
        )
        val contentBoundary = evidence.requiredObject("contentBoundary")
        checkEquals(
            BOUNDED_CREDENTIAL_HEADER_DENYLIST,
            contentBoundary
                .requiredArray("boundedCredentialHeaderDenylist")
                .map {
                    (it as CanonicalValue.StringValue).value
                }
                .toSet(),
            "bounded credential header parity",
        )
        checkEquals(
            SINGLETON_PROTOCOL_HEADERS,
            contentBoundary
                .requiredArray("singletonProtocolHeaders")
                .map {
                    (it as CanonicalValue.StringValue).value
                }
                .toSet(),
            "singleton protocol header parity",
        )
        checkThat(
            !(contentBoundary.value("credentialHeaderUniverseExhaustiveClaimed")
                    as CanonicalValue.BooleanValue)
                .value,
            "credential header universe is not overclaimed",
        )
        val contractRecord =
            DoraCanonicalJson.parseStrict(
                Files.readString(
                    root.resolve("docs/evidence/poc-vpn-001/contract-record-stage0-v0.1.json")
                )
            ) as CanonicalValue.ObjectValue
        val contractForbiddenFields =
            contractRecord
                .requiredObject("evidenceContract")
                .requiredArray("forbiddenFields")
                .map {
                    (it as CanonicalValue.StringValue).value
                }
                .toSet()
        val boundedCategories =
            contentBoundary
                .requiredArray("contractForbiddenFieldCategories")
                .map {
                    (it as CanonicalValue.StringValue).value
                }
                .toSet()
        checkEquals(setOf("authToken", "credential"), boundedCategories, "credential categories")
        checkThat(
            contractForbiddenFields.containsAll(boundedCategories),
            "credential categories derive from contract record",
        )
        listOf("contractPins", "implementationPins").forEach { pinGroup ->
            evidence.requiredArray(pinGroup).forEach { value ->
                val pin = value as CanonicalValue.ObjectValue
                val bytes = Files.readAllBytes(root.resolve(pin.requiredString("path")))
                checkEquals(
                    pin.requiredString("sha256"),
                    ContractOracle.sha256Hex(bytes).value,
                    "$pinGroup source hash",
                )
            }
        }
        val result = evidence.requiredObject("resultTaxonomy")
        checkEquals(
            "I2_IMPLEMENTED_LOCAL_CHECKS_PASS_REVIEW_PENDING",
            result.requiredString("i2ImplementationVerification"),
            "review-pending I2 taxonomy",
        )
        checkEquals("TODO", result.requiredString("pocVpnBacklogState"), "backlog truth")
        checkEquals("NOT_READY", result.requiredString("pocVpnReadiness"), "readiness truth")
        checkEquals("NOT_RUN", result.requiredString("pocVpnOverallResult"), "overall truth")
    }

    @Suppress("LongMethod")
    private fun multipartSnapshotCompletionAndReads() {
        val fixture = fixture()
        val bytes = ContractOracle.materializeFixture(fixture)
        val parts = ContractOracle.fixtureParts(bytes)
        val initialService = SyntheticContractService()
        val initialServer = trackedServer(initialService)
        val initialPort = initialServer.port
        lateinit var jobId: String
        lateinit var uploadId: String
        lateinit var secondPart: UploadedPart
        initialServer.use {
            HermeticLoopbackClient(initialServer.endpoint, requestIdAllocator).use { client ->
                val createResponse =
                    client.send(
                        createRequest(
                            initialServer.endpoint.child("/v1/processing-jobs"),
                            fixture,
                            "key-synthetic-multipart-create",
                        )
                    )
                jobId =
                    assertExactSchema(createResponse, "CreateJobResponse-v0.1")
                        .requiredString("jobId")
                val planResponse =
                    client.send(
                        uploadPlanRequest(
                            initialServer.endpoint,
                            jobId,
                            fixture,
                            "key-synthetic-plan",
                        )
                    )
                uploadId =
                    assertExactSchema(planResponse, "UploadPlanResponse-v0.1")
                        .requiredString("uploadId")

                val outOfOrder =
                    partRequest(
                        initialServer.endpoint,
                        uploadId,
                        2,
                        parts[1],
                        "key-synthetic-part-out-of-order",
                    )
                val outOfOrderResponse = client.send(outOfOrder)
                checkEquals(200, outOfOrderResponse.status, "out-of-order part accepted")
                assertExactSchema(outOfOrderResponse, "UploadPartResponse-v0.1")
                secondPart = uploadedPart(2, parts[1], outOfOrderResponse)
                val badHeader =
                    partRequest(
                        initialServer.endpoint,
                        uploadId,
                        1,
                        parts[0],
                        "key-synthetic-part-bad-checksum",
                        declaredSha256 = "0".repeat(64),
                    )
                val badHeaderResponse = client.send(badHeader)
                checkEquals(422, badHeaderResponse.status, "transmitted checksum rejected")
                assertExactSchema(badHeaderResponse, "ErrorResponse-v0.1")
                checkEquals(
                    setOf(2),
                    initialService.job(jobId)?.parts?.keys,
                    "invalid part leaves accepted out-of-order part unchanged",
                )
            }
        }
        assertPortClosed(initialPort)

        val lostPartServer =
            trackedServer(
                initialService,
                listOf(FaultDirective("VPN-FLT-011", "UPLOAD_PART", FaultAction.DROP_AFTER_COMMIT)),
            )
        val lostPartPort = lostPartServer.port
        lateinit var firstPart: UploadedPart
        lateinit var firstPartResponse: ByteArray
        val partScheduler = DeterministicRetryScheduler()
        HermeticLoopbackClient(lostPartServer.endpoint, requestIdAllocator).use { client ->
            val request =
                partRequest(
                    lostPartServer.endpoint,
                    uploadId,
                    1,
                    parts[0],
                    "key-synthetic-part-1",
                )
            val response = RetryingLoopbackTransport(client, partScheduler).execute(request)
            checkEquals(200, response.status, "lost part receipt recovered")
            firstPartResponse = response.body
            firstPart = uploadedPart(1, parts[0], response)
            checkEquals(
                listOf("UNKNOWN_COMMIT"),
                partScheduler.events().map { it.category },
                "part retry class",
            )
            val duplicate = client.send(request)
            checkThat(response.body.contentEquals(duplicate.body), "duplicate part stable receipt")
            val mutated =
                partRequest(
                    lostPartServer.endpoint,
                    uploadId,
                    1,
                    parts[0].copyOf(parts[0].size - 1),
                    "key-synthetic-part-1",
                )
            val before = initialService.snapshot()
            checkEquals(409, client.send(mutated).status, "same part key different payload")
            checkEquals(before.effects, initialService.effectTotals(), "part mismatch zero effects")
            checkEquals(
                parts[0].size,
                initialService.job(jobId)?.parts?.get(1)?.byteLength,
                "part immutable",
            )
        }

        val partSnapshot = initialService.snapshot()
        checkEquals(SNAPSHOT_KIND, partSnapshot.snapshotKind, "snapshot taxonomy")
        val restoredService = SyntheticContractService(partSnapshot)
        val completionServer = trackedServer(restoredService)
        val completionPort = completionServer.port
        checkThat(completionPort != lostPartPort, "snapshot restored on new ephemeral port")
        val uploaded = mutableListOf(firstPart, secondPart)
        lateinit var validComplete: HarnessRequest
        HermeticLoopbackClient(completionServer.endpoint, requestIdAllocator).use { client ->
            val replayedPart =
                client.send(
                    partRequest(
                        completionServer.endpoint,
                        uploadId,
                        1,
                        parts[0],
                        "key-synthetic-part-1",
                    )
                )
            checkThat(
                firstPartResponse.contentEquals(replayedPart.body),
                "part replay after restore",
            )
            for (ordinal in 3..parts.size) {
                val response =
                    client.send(
                        partRequest(
                            completionServer.endpoint,
                            uploadId,
                            ordinal,
                            parts[ordinal - 1],
                            "key-synthetic-part-$ordinal",
                        )
                    )
                checkEquals(200, response.status, "remaining part accepted")
                uploaded += uploadedPart(ordinal, parts[ordinal - 1], response)
            }

            val missing =
                completeRequest(
                    completionServer.endpoint,
                    jobId,
                    uploadId,
                    uploaded.dropLast(1),
                    "key-synthetic-complete-missing",
                    totalByteLength = uploaded.dropLast(1).sumOf { it.bytes.size },
                )
            checkEquals(422, client.send(missing).status, "missing manifest part rejected")
            val reversed =
                completeRequest(
                    completionServer.endpoint,
                    jobId,
                    uploadId,
                    uploaded.reversed(),
                    "key-synthetic-complete-order",
                    totalByteLength = bytes.size,
                )
            checkEquals(422, client.send(reversed).status, "out-of-order manifest rejected")
            val wrongOverall =
                completeRequest(
                    completionServer.endpoint,
                    jobId,
                    uploadId,
                    uploaded,
                    "key-synthetic-complete-overall",
                    totalByteLength = bytes.size,
                    totalSha256 = "0".repeat(64),
                )
            checkEquals(422, client.send(wrongOverall).status, "overall checksum rejected")
            checkEquals(
                "UPLOADING",
                restoredService.job(jobId)?.state,
                "invalid complete does not queue",
            )

            validComplete =
                completeRequest(
                    completionServer.endpoint,
                    jobId,
                    uploadId,
                    uploaded,
                    "key-synthetic-complete-valid",
                    totalByteLength = bytes.size,
                )
        }

        val lossServer =
            trackedServer(
                restoredService,
                listOf(
                    FaultDirective(
                        "I2-COMPLETE-SNAPSHOT-LOSS",
                        "COMPLETE_UPLOAD",
                        FaultAction.DROP_AFTER_COMMIT,
                    )
                ),
            )
        val lossPort = lossServer.port
        checkThat(lossPort != completionPort, "completion loss uses another ephemeral port")
        HermeticLoopbackClient(lossServer.endpoint, requestIdAllocator).use { client ->
            validComplete =
                validComplete.copy(uri = lossServer.endpoint.child(validComplete.uri.rawPath))
            expectIo { client.send(validComplete) }
            checkEquals(
                "RESULT_READY",
                restoredService.job(jobId)?.state,
                "complete committed before loss",
            )
        }
        val completeSnapshot = restoredService.snapshot()
        val readService = SyntheticContractService(completeSnapshot)
        val readServer =
            trackedServer(
                readService,
                listOf(
                    FaultDirective("I2-POLL-5XX-READ", "POLL_JOB", FaultAction.RETURN_503),
                    FaultDirective("VPN-FLT-032", "FETCH_RESULT", FaultAction.DROP_AFTER_COMMIT),
                ),
            )
        val readPort = readServer.port
        checkThat(readPort != lossPort, "complete restore uses new ephemeral port")
        readServer.use {
            HermeticLoopbackClient(readServer.endpoint, requestIdAllocator).use { client ->
                val replayRequest =
                    validComplete.copy(uri = readServer.endpoint.child(validComplete.uri.rawPath))
                val replay = client.send(replayRequest)
                checkEquals(202, replay.status, "complete replay after restore")
                val effectsAfterReplay = readService.effectTotals()
                val duplicate = client.send(replayRequest)
                checkThat(
                    replay.body.contentEquals(duplicate.body),
                    "duplicate complete stable commit",
                )
                checkEquals(
                    effectsAfterReplay,
                    readService.effectTotals(),
                    "duplicate complete zero effects",
                )
                checkEquals(
                    1,
                    readService.snapshot().jobs.values.count { it.resultId != null },
                    "one committed result",
                )

                val pollScheduler = DeterministicRetryScheduler()
                val poll =
                    RetryingLoopbackTransport(client, pollScheduler)
                        .execute(readRequest(readServer.endpoint, "/v1/processing-jobs/$jobId"))
                checkEquals(200, poll.status, "poll retry succeeds")
                checkEquals(
                    listOf("HTTP_5XX"),
                    pollScheduler.events().map { it.category },
                    "poll finite retry",
                )
                val fetchScheduler = DeterministicRetryScheduler()
                val fetchRequest =
                    readRequest(readServer.endpoint, "/v1/processing-jobs/$jobId/result")
                val fetch = RetryingLoopbackTransport(client, fetchScheduler).execute(fetchRequest)
                checkEquals(200, fetch.status, "result response loss recovered")
                checkEquals(
                    listOf("UNKNOWN_COMMIT"),
                    fetchScheduler.events().map { it.category },
                    "result retry",
                )
                checkThat(
                    fetch.body.contentEquals(client.send(fetchRequest).body),
                    "result bytes stable",
                )
                checkEquals(
                    effectsAfterReplay,
                    readService.effectTotals(),
                    "reads have zero effects",
                )
            }
        }
        assertPortClosed(readPort)
        lossServer.close()
        completionServer.close()
        lostPartServer.close()
        assertPortClosed(lossPort)
        assertPortClosed(completionPort)
        assertPortClosed(lostPartPort)
    }

    private fun finiteRetryProfiles() {
        runTransientFault(FaultAction.RETURN_429, "I2-FINITE-429-PROFILE", "HTTP_429", 1_000)
        runTransientFault(FaultAction.RETURN_503, "VPN-FLT-018", "HTTP_5XX", 100)
        runTransientFault(FaultAction.TIMEOUT_BEFORE_COMMIT, "VPN-FLT-019", "REQUEST_TIMEOUT", 100)

        val service = SyntheticContractService()
        val directives =
            (1..MAX_ATTEMPTS).map {
                FaultDirective("VPN-FLT-018-EXHAUST-$it", "CREATE_JOB", FaultAction.RETURN_503)
            }
        val server = trackedServer(service, directives)
        val port = server.port
        HermeticLoopbackClient(server.endpoint, requestIdAllocator).use { client ->
            val scheduler = DeterministicRetryScheduler()
            val request =
                createRequest(
                    server.endpoint.child("/v1/processing-jobs"),
                    fixture(),
                    "key-synthetic-retry-exhausted",
                )
            val exhausted = expectBudgetExhausted {
                RetryingLoopbackTransport(client, scheduler).execute(request)
            }
            checkEquals(MAX_ATTEMPTS, exhausted.attempts, "finite retry attempts")
            checkEquals("HTTP_5XX", exhausted.category, "finite retry category")
            checkEquals(MAX_ATTEMPTS, client.sendCount(), "finite sends")
            checkEquals(MAX_ATTEMPTS - 1, scheduler.events().size, "finite scheduled retries")
            checkEquals(0, service.effectTotals().economicEffects, "exhaustion has zero commit")
        }
        server.close()
        assertPortClosed(port)
    }

    private fun runTransientFault(
        action: FaultAction,
        faultId: String,
        category: String,
        expectedDelayMillis: Long,
    ) {
        val service = SyntheticContractService()
        val server = trackedServer(service, listOf(FaultDirective(faultId, "CREATE_JOB", action)))
        val port = server.port
        HermeticLoopbackClient(server.endpoint, requestIdAllocator).use { client ->
            val scheduler = DeterministicRetryScheduler()
            val response =
                RetryingLoopbackTransport(client, scheduler)
                    .execute(
                        createRequest(
                            server.endpoint.child("/v1/processing-jobs"),
                            fixture(),
                            "key-synthetic-retry-$faultId",
                        )
                    )
            checkEquals(201, response.status, "$faultId recovered")
            checkEquals(2, client.sendCount(), "$faultId bounded sends")
            checkEquals(
                listOf(category),
                scheduler.events().map { it.category },
                "$faultId category",
            )
            checkEquals(
                expectedDelayMillis,
                scheduler.events().single().logicalDelayMillis,
                "$faultId logical delay",
            )
            checkEquals(1, service.effectTotals().economicEffects, "$faultId one commit")
            checkThat(server.remainingFaults().isEmpty(), "$faultId consumed")
        }
        server.close()
        assertPortClosed(port)
    }

    private fun createOnly(client: HermeticLoopbackClient, endpoint: URI, suffix: String): String {
        val response =
            client.send(
                createRequest(
                    endpoint.child("/v1/processing-jobs"),
                    fixture(),
                    "key-synthetic-create-$suffix",
                )
            )
        assertReplayMarker(response, expected = false)
        return assertExactSchema(response, "CreateJobResponse-v0.1").requiredString("jobId")
    }

    private fun createAndUpload(
        client: HermeticLoopbackClient,
        endpoint: URI,
        suffix: String,
    ): UploadedLifecycle {
        val selected = fixture()
        val jobId = createOnly(client, endpoint, suffix)
        val planResponse =
            client.send(
                uploadPlanRequest(
                    endpoint,
                    jobId,
                    selected,
                    "key-synthetic-plan-$suffix",
                )
            )
        val uploadId =
            assertExactSchema(planResponse, "UploadPlanResponse-v0.1").requiredString("uploadId")
        assertReplayMarker(planResponse, expected = false)
        val uploaded =
            ContractOracle.fixtureParts(ContractOracle.materializeFixture(selected)).mapIndexed {
                index,
                part ->
                val ordinal = index + 1
                val response =
                    client.send(
                        partRequest(
                            endpoint,
                            uploadId,
                            ordinal,
                            part,
                            "key-synthetic-part-$suffix-$ordinal",
                        )
                    )
                checkEquals(200, response.status, "lifecycle part accepted")
                assertExactSchema(response, "UploadPartResponse-v0.1")
                assertReplayMarker(response, expected = false)
                uploadedPart(ordinal, part, response)
            }
        return UploadedLifecycle(jobId, uploadId, uploaded)
    }

    @Suppress("LongMethod")
    private fun lostResponseReplayAndMismatch() {
        val service = SyntheticContractService()
        val server =
            trackedServer(
                service,
                listOf(FaultDirective("VPN-FLT-007", "CREATE_JOB", FaultAction.DROP_AFTER_COMMIT)),
            )
        val port = server.port
        server.use {
            HermeticLoopbackClient(server.endpoint, requestIdAllocator).use { client ->
                val scheduler = DeterministicRetryScheduler()
                val transport = RetryingLoopbackTransport(client, scheduler)
                val create =
                    createRequest(
                        server.endpoint.child("/v1/processing-jobs"),
                        fixture(),
                        "key-synthetic-create-a",
                    )
                val recovered = transport.execute(create)
                checkEquals(201, recovered.status, "lost create recovered")
                assertExactSchema(recovered, "CreateJobResponse-v0.1")
                assertReplayMarker(recovered, expected = true)
                checkEquals(2, client.requestIds().toSet().size, "retry request IDs distinct")
                checkEquals(
                    listOf("UNKNOWN_COMMIT"),
                    scheduler.events().map { it.category },
                    "lost response retry",
                )
                val firstJobId = objectBody(recovered).requiredString("jobId")
                checkEquals(1, service.effectTotals().economicEffects, "one create economic effect")
                checkEquals(1, service.effectTotals().resources, "one create resource")

                val repeated = client.send(create)
                assertReplayMarker(repeated, expected = true)
                checkThat(
                    recovered.body.contentEquals(repeated.body),
                    "same payload response replay",
                )
                val beforeMismatch = service.snapshot()
                val differentPayload =
                    createRequest(
                        server.endpoint.child("/v1/processing-jobs"),
                        ContractCatalog.fixtures.single { it.id == "VPN-FIX-005" },
                        "key-synthetic-create-a",
                    )
                val mismatch = client.send(differentPayload)
                checkEquals(409, mismatch.status, "same key different payload")
                assertExactSchema(mismatch, "ErrorResponse-v0.1")
                checkEquals(
                    beforeMismatch.effects,
                    service.effectTotals(),
                    "payload mismatch zero effects",
                )
                checkEquals(
                    beforeMismatch.jobs.keys,
                    service.snapshot().jobs.keys,
                    "payload mismatch zero state",
                )

                val secondCreate =
                    client.send(
                        createRequest(
                            server.endpoint.child("/v1/processing-jobs"),
                            ContractCatalog.fixtures.single { it.id == "VPN-FIX-005" },
                            "key-synthetic-create-b",
                        )
                    )
                assertExactSchema(secondCreate, "CreateJobResponse-v0.1")
                assertReplayMarker(secondCreate, expected = false)
                val secondJobId = objectBody(secondCreate).requiredString("jobId")
                val cancelKey = "key-synthetic-cancel-target"
                val firstCancel = client.send(cancelRequest(server.endpoint, firstJobId, cancelKey))
                checkEquals(202, firstCancel.status, "first cancel committed")
                assertExactSchema(firstCancel, "CancelResponse-v0.1")
                assertReplayMarker(firstCancel, expected = false)
                val effectsAfterCancel = service.effectTotals()
                val secondCancel =
                    client.send(cancelRequest(server.endpoint, secondJobId, cancelKey))
                checkEquals(409, secondCancel.status, "same key different target")
                checkEquals(
                    effectsAfterCancel,
                    service.effectTotals(),
                    "target mismatch zero effects",
                )
                checkThat(
                    service.job(secondJobId)?.state == "CREATED",
                    "target mismatch zero state",
                )
                checkThat(
                    service.contentFreeRequestLedger().none {
                        it.toString().contains("key-synthetic", ignoreCase = true)
                    },
                    "request ledger excludes raw keys",
                )
                checkEquals(1, server.contentFreeFaultLedger().size, "frozen fault consumed once")
                checkThat(server.remainingFaults().isEmpty(), "fault queue exhausted exactly")
            }
        }
        assertPortClosed(port)
    }

    private fun runScenario(block: () -> Unit) {
        checkThat(activeServers.isEmpty(), "server registry starts empty")
        val threads = NamedThreadFactory("dora-vpn-loopback-scenario", daemon = false)
        val executor = Executors.newSingleThreadExecutor(threads)
        val future = executor.submit(block)
        var failure: Throwable? = null
        try {
            future.get(SCENARIO_DEADLINE.toMillis(), TimeUnit.MILLISECONDS)
        } catch (caught: Throwable) {
            failure = caught
        } finally {
            future.cancel(true)
            var cleanupFailure = closeAll(activeServers.reversed())
            activeServers.clear()
            cleanupFailure =
                captureCleanup(cleanupFailure) {
                    executor.shutdownNow()
                    checkThat(
                        executor.awaitTermination(2, TimeUnit.SECONDS),
                        "scenario executor terminated",
                    )
                }
            cleanupFailure =
                captureCleanup(cleanupFailure) {
                    checkThat(
                        threads.awaitNoLiveNonDaemonThreads(java.time.Duration.ofSeconds(2)),
                        "scenario thread cleanup",
                    )
                }
            if (failure == null) {
                failure = cleanupFailure
            } else if (cleanupFailure != null) {
                failure.addSuppressed(cleanupFailure)
            }
        }
        failure?.let { throw it }
    }

    @Suppress("LongMethod")
    private fun constructorAndCleanupFailureBoundary() {
        val invalidClient =
            runCatching {
                    HermeticLoopbackClient(URI("http://localhost:1"), requestIdAllocator)
                }
                .exceptionOrNull()
        checkThat(invalidClient is IllegalArgumentException, "invalid client rejected pre-resource")
        assertNoHarnessTransportThreads("invalid client cleanup")

        var failedStartPort = 0
        val startFailure =
            runCatching {
                    HermeticLoopbackServer.create(
                        SyntheticContractService(),
                        lifecycleProbes =
                            ServerLifecycleProbes(
                                afterBindBeforeStart = { assignedPort ->
                                    failedStartPort = assignedPort
                                    error("Synthetic pre-start probe failure")
                                }
                            ),
                    )
                }
                .exceptionOrNull()
        checkThat(startFailure is IllegalStateException, "server pre-start probe failed closed")
        checkThat(failedStartPort > 0, "server pre-start probe observed ephemeral port")
        assertPortClosed(failedStartPort)
        assertNoHarnessTransportThreads("failed server start cleanup")

        val stopFailureServer =
            trackedServer(
                SyntheticContractService(),
                lifecycleProbes =
                    ServerLifecycleProbes(beforeStop = { error("Synthetic stop-phase failure") }),
            )
        val stopFailurePort = stopFailureServer.port
        val stopFailure = runCatching { stopFailureServer.close() }.exceptionOrNull()
        checkThat(stopFailure is IllegalStateException, "stop-phase failure retained")
        assertPortClosed(stopFailurePort)
        assertNoHarnessTransportThreads("stop-phase failure cleanup")
        stopFailureServer.close()

        val oversizedService = SyntheticContractService()
        val oversizedServer =
            trackedServer(
                oversizedService,
                listOf(
                    FaultDirective(
                        "I2-OVERSIZED-RESPONSE-PROBE",
                        "CREATE_JOB",
                        FaultAction.OVERSIZED_RESPONSE,
                    )
                ),
            )
        val oversizedPort = oversizedServer.port
        HermeticLoopbackClient(oversizedServer.endpoint, requestIdAllocator).use { client ->
            expectOversizedResponse {
                client.send(
                    createRequest(
                        oversizedServer.endpoint.child("/v1/processing-jobs"),
                        fixture(),
                        "key-synthetic-oversized-response",
                    )
                )
            }
        }
        checkEquals(0, oversizedService.effectTotals().resources, "oversized response zero effects")
        oversizedServer.close()
        assertPortClosed(oversizedPort)

        val schemaService = SyntheticContractService()
        val schemaServer = trackedServer(schemaService)
        val schemaPort = schemaServer.port
        HermeticLoopbackClient(schemaServer.endpoint, requestIdAllocator).use { client ->
            val baseRequest =
                createRequest(
                    schemaServer.endpoint.child("/v1/processing-jobs"),
                    fixture(),
                    "key-synthetic-schema-base",
                )
            val baseBody = objectBody(HarnessResponse(0, body = baseRequest.body))
            val malformedBodies =
                listOf(
                    CanonicalValue.ObjectValue(
                        baseBody.entries.filterNot { it.first == "payloadSha256" }
                    ),
                    CanonicalValue.ObjectValue(
                        baseBody.entries + ("extraField" to canonicalString("synthetic-extra"))
                    ),
                    CanonicalValue.ObjectValue(
                        baseBody.entries.map { entry ->
                            if (entry.first == "payloadByteLength") {
                                entry.first to canonicalString("synthetic-wrong-type")
                            } else {
                                entry
                            }
                        }
                    ),
                )
            malformedBodies.forEachIndexed { index, malformed ->
                val response =
                    client.send(
                        baseRequest.copy(
                            headers = commonHeaders("key-synthetic-schema-$index"),
                            body = encode(malformed),
                        )
                    )
                checkEquals(422, response.status, "schema negative rejected")
                assertExactSchema(response, "ErrorResponse-v0.1")
            }
            val malformedUtf8Body = baseRequest.body.copyOf()
            val fixtureOffset = String(baseRequest.body, Charsets.UTF_8).indexOf(fixture().id)
            checkThat(fixtureOffset >= 0, "malformed UTF-8 probe marker found")
            malformedUtf8Body[fixtureOffset] = 0x80.toByte()
            val replacementDecoded = String(malformedUtf8Body, Charsets.UTF_8)
            checkThat(
                '\uFFFD' in replacementDecoded,
                "default decoding would replace malformed byte",
            )
            checkThat(
                DoraCanonicalJson.parseStrict(replacementDecoded) is CanonicalValue.ObjectValue,
                "replacement-decoded malformed bytes remain valid JSON",
            )
            val beforeMalformedUtf8 = schemaService.snapshot()
            val malformedUtf8Response =
                client.send(
                    baseRequest.copy(
                        headers = commonHeaders("key-synthetic-malformed-utf8"),
                        body = malformedUtf8Body,
                    )
                )
            checkEquals(422, malformedUtf8Response.status, "malformed UTF-8 rejected")
            val malformedUtf8Error = assertExactSchema(malformedUtf8Response, "ErrorResponse-v0.1")
            checkEquals(
                "SCHEMA_VALIDATION_FAILED",
                malformedUtf8Error.requiredString("errorCode"),
                "malformed UTF-8 error code",
            )
            val afterMalformedUtf8 = schemaService.snapshot()
            checkEquals(
                beforeMalformedUtf8.ledger,
                afterMalformedUtf8.ledger,
                "UTF-8 ledger stable",
            )
            checkEquals(beforeMalformedUtf8.jobs, afterMalformedUtf8.jobs, "UTF-8 jobs stable")
            checkEquals(
                beforeMalformedUtf8.deletions,
                afterMalformedUtf8.deletions,
                "UTF-8 deletions stable",
            )
            checkEquals(
                beforeMalformedUtf8.effects,
                afterMalformedUtf8.effects,
                "UTF-8 vectors stable",
            )
            checkEquals(
                beforeMalformedUtf8.mutationResponses.keys,
                afterMalformedUtf8.mutationResponses.keys,
                "UTF-8 response ledger stable",
            )
            checkEquals(
                beforeMalformedUtf8.requestLedger.size + 1,
                afterMalformedUtf8.requestLedger.size,
                "UTF-8 audit rejection recorded",
            )
            checkEquals(
                "REJECTED_BEFORE_IDEMPOTENCY",
                afterMalformedUtf8.requestLedger.last().disposition,
                "UTF-8 rejection disposition",
            )
            checkEquals(
                EffectVector.ZERO,
                afterMalformedUtf8.requestLedger.last().effectDelta,
                "UTF-8 rejection zero effect",
            )
        }
        checkEquals(0, schemaService.effectTotals().resources, "schema negatives zero effects")
        schemaServer.close()
        assertPortClosed(schemaPort)

        val outputProbe =
            contentFreeFailureMarker(
                IllegalStateException(
                    "https://external.invalid/forbidden Authorization token body URI 127.0.0.1"
                )
            )
        checkEquals(
            "FAIL hermetic-loopback-transport-harness",
            outputProbe,
            "content-free failure marker",
        )
        listOf("http", "127.", "authorization", "token", "body", "uri").forEach { forbidden ->
            checkThat(!outputProbe.contains(forbidden, ignoreCase = true), "output field denied")
        }

        val closeOrder = mutableListOf<String>()
        val cleanupFailure =
            closeAll(
                listOf(
                    AutoCloseable {
                        closeOrder += "first"
                        error("Synthetic first close failure")
                    },
                    AutoCloseable { closeOrder += "second" },
                    AutoCloseable {
                        closeOrder += "third"
                        error("Synthetic later close failure")
                    },
                )
            )
        checkEquals(listOf("first", "second", "third"), closeOrder, "all cleanup attempted")
        checkThat(cleanupFailure != null, "first cleanup failure retained")
        checkEquals(1, cleanupFailure?.suppressed?.size, "later cleanup failure suppressed")
    }

    private fun closeAll(resources: List<AutoCloseable>): Throwable? {
        var failure: Throwable? = null
        resources.forEach { resource ->
            failure = captureCleanup(failure) { resource.close() }
        }
        return failure
    }

    private fun captureCleanup(
        current: Throwable?,
        cleanup: () -> Unit,
    ): Throwable? =
        try {
            cleanup()
            current
        } catch (caught: Throwable) {
            if (current == null) caught else current.apply { addSuppressed(caught) }
        }

    private fun assertNoHarnessTransportThreads(label: String) {
        val live =
            Thread.getAllStackTraces().keys.filter { thread ->
                thread.isAlive &&
                    !thread.isDaemon &&
                    (thread.name.startsWith("dora-vpn-loopback-client") ||
                        thread.name.startsWith("dora-vpn-loopback-server") ||
                        thread.name == "HTTP-Dispatcher")
            }
        checkThat(live.isEmpty(), label)
    }

    private fun trackedServer(
        service: SyntheticContractService,
        directives: List<FaultDirective> = emptyList(),
        lifecycleProbes: ServerLifecycleProbes = ServerLifecycleProbes(),
    ): HermeticLoopbackServer =
        HermeticLoopbackServer.create(service, directives, lifecycleProbes).also(activeServers::add)

    private fun checkNoSend(decision: PreSendDecision, label: String) {
        checkThat(!decision.transportAllowed, "$label no send")
        checkEquals("FINAL_REJECT", decision.retryClass, "$label retry class")
    }

    private fun profile(expiresAt: String = "2027-01-01T00:00:00Z"): ConsentProfileBinding {
        val unsigned =
            ConsentProfileBinding(
                "profile-synthetic-a",
                "receipt-synthetic-a",
                "policy-synthetic-a",
                ArtifactClass.SYNTHETIC_BYTES,
                ConsentPurpose.STAGE0_CONTRACT_TEST,
                ENDPOINT_BINDING.endpointId,
                ENDPOINT_BINDING.regionCode,
                SYNTHETIC_TENANT,
                "2026-01-01T00:00:00Z",
                expiresAt,
                null,
                Sha256Hex("1".repeat(64)),
                Sha256Hex("0".repeat(64)),
            )
        return unsigned.copy(profileBindingSha256 = ContractOracle.profileBindingDigest(unsigned))
    }

    private fun fixture(): FixtureDefinition =
        ContractCatalog.fixtures.single { it.id == "VPN-FIX-006" }

    private fun createRequest(uri: URI, fixture: FixtureDefinition, key: String): HarnessRequest {
        val body =
            canonicalObject(
                "schemaVersion" to canonicalString("CreateJobRequest-v0.1"),
                "profileBindingSha256" to canonicalString(PROFILE_DIGEST.value),
                "syntheticTenantId" to canonicalString(SYNTHETIC_TENANT),
                "fixtureId" to canonicalString(fixture.id),
                "artifactClass" to canonicalString(ArtifactClass.SYNTHETIC_BYTES.name),
                "purpose" to canonicalString(ConsentPurpose.STAGE0_CONTRACT_TEST.name),
                "payloadByteLength" to canonicalInteger(fixture.byteLength.toLong()),
                "payloadSha256" to canonicalString(fixture.sha256.value),
            )
        return HarnessRequest(uri, "POST", commonHeaders(key), encode(body))
    }

    private fun cancelRequest(endpoint: URI, jobId: String, key: String): HarnessRequest =
        HarnessRequest(
            endpoint.child("/v1/processing-jobs/$jobId:cancel"),
            "POST",
            commonHeaders(key),
        )

    private fun deleteRequest(
        endpoint: URI,
        conversationFixtureId: String,
        key: String,
    ): HarnessRequest =
        HarnessRequest(
            endpoint.child("/v1/conversations/$conversationFixtureId/cloud-copy"),
            "DELETE",
            commonHeaders(key),
        )

    private fun uploadPlanRequest(
        endpoint: URI,
        jobId: String,
        fixture: FixtureDefinition,
        key: String,
    ): HarnessRequest {
        val body =
            canonicalObject(
                "schemaVersion" to canonicalString("UploadPlanRequest-v0.1"),
                "jobId" to canonicalString(jobId),
                "priorUploadId" to CanonicalValue.NullValue,
                "requestedPlanGeneration" to canonicalInteger(1),
                "partSizeBytes" to canonicalInteger(ContractCatalog.PART_SIZE_BYTES.toLong()),
                "totalByteLength" to canonicalInteger(fixture.byteLength.toLong()),
                "totalSha256" to canonicalString(fixture.sha256.value),
            )
        return HarnessRequest(
            endpoint.child("/v1/processing-jobs/$jobId/uploads"),
            "POST",
            commonHeaders(key),
            encode(body),
        )
    }

    @Suppress("LongParameterList")
    private fun partRequest(
        endpoint: URI,
        uploadId: String,
        ordinal: Int,
        bytes: ByteArray,
        key: String,
        declaredSha256: String = ContractOracle.sha256Hex(bytes).value,
    ): HarnessRequest =
        HarnessRequest(
            endpoint.child("/synthetic-upload/$uploadId/1/$ordinal"),
            "PUT",
            commonHeaders(key) +
                mapOf(
                    "Content-Type" to "application/octet-stream",
                    "X-Content-Sha256" to declaredSha256,
                ),
            bytes,
        )

    @Suppress("LongParameterList")
    private fun completeRequest(
        endpoint: URI,
        jobId: String,
        uploadId: String,
        parts: List<UploadedPart>,
        key: String,
        totalByteLength: Int,
        totalSha256: String =
            ContractOracle.sha256Hex(parts.flatMap { it.bytes.asIterable() }.toByteArray()).value,
    ): HarnessRequest {
        val manifest = parts.map { part ->
            canonicalObject(
                "partNumber" to canonicalInteger(part.ordinal.toLong()),
                "byteLength" to canonicalInteger(part.bytes.size.toLong()),
                "sha256" to canonicalString(ContractOracle.sha256Hex(part.bytes).value),
                "partReceiptId" to canonicalString(part.receiptId),
            )
        }
        val body =
            canonicalObject(
                "schemaVersion" to canonicalString("CompleteUploadRequest-v0.1"),
                "jobId" to canonicalString(jobId),
                "uploadId" to canonicalString(uploadId),
                "manifest" to CanonicalValue.ArrayValue(manifest),
                "totalByteLength" to canonicalInteger(totalByteLength.toLong()),
                "totalSha256" to canonicalString(totalSha256),
            )
        return HarnessRequest(
            endpoint.child("/v1/processing-jobs/$jobId/uploads:complete"),
            "POST",
            commonHeaders(key),
            encode(body),
        )
    }

    private fun readRequest(endpoint: URI, path: String): HarnessRequest =
        HarnessRequest(endpoint.child(path), "GET", commonHeaders())

    private fun uploadedPart(
        ordinal: Int,
        bytes: ByteArray,
        response: HarnessResponse,
    ): UploadedPart =
        UploadedPart(ordinal, bytes.copyOf(), objectBody(response).requiredString("partReceiptId"))

    private fun expectIo(block: () -> Unit) {
        try {
            block()
        } catch (_: IOException) {
            return
        }
        error("FAILED: expected bounded response loss")
    }

    private fun expectOversizedResponse(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalStateException) {
            return
        }
        error("FAILED: expected oversized response rejection")
    }

    private fun expectBudgetExhausted(block: () -> Unit): RetryBudgetExhausted {
        try {
            block()
        } catch (failure: RetryBudgetExhausted) {
            return failure
        }
        error("FAILED: expected finite retry exhaustion")
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun assertExactSchema(
        response: HarnessResponse,
        schemaVersion: String,
    ): CanonicalValue.ObjectValue {
        val body = objectBody(response)
        val shape = exactResponseSchemas.getValue(schemaVersion)
        checkEquals(shape.fields, body.keys(), "$schemaVersion exact fields")
        checkEquals(schemaVersion, body.requiredString("schemaVersion"), "$schemaVersion marker")
        shape.fields.minus("schemaVersion").forEach { field ->
            val value = checkNotNull(body.value(field))
            when (field) {
                in shape.integerFields ->
                    checkThat(value is CanonicalValue.IntegerValue, "$schemaVersion $field integer")
                in shape.arrayFields ->
                    checkThat(value is CanonicalValue.ArrayValue, "$schemaVersion $field array")
                in shape.booleanFields ->
                    checkThat(value is CanonicalValue.BooleanValue, "$schemaVersion $field boolean")
                in shape.nullableStringFields ->
                    checkThat(
                        value is CanonicalValue.StringValue || value is CanonicalValue.NullValue,
                        "$schemaVersion $field nullable string",
                    )
                else ->
                    checkThat(value is CanonicalValue.StringValue, "$schemaVersion $field string")
            }
        }
        if (schemaVersion == "UploadPlanResponse-v0.1") {
            checkEquals(
                DEFAULT_UPLOAD_EXPIRES_AT,
                Instant.parse(body.requiredString("expiresAt")),
                "deterministic upload expiry",
            )
            body.requiredArray("parts").forEach { value ->
                val part = value as CanonicalValue.ObjectValue
                checkEquals(
                    setOf("partNumber", "urlTokenId", "expectedByteLength"),
                    part.keys(),
                    "upload plan part exact fields",
                )
                checkThat(
                    part.value("partNumber") is CanonicalValue.IntegerValue &&
                        part.value("expectedByteLength") is CanonicalValue.IntegerValue &&
                        part.value("urlTokenId") is CanonicalValue.StringValue,
                    "upload plan part exact types",
                )
            }
        }
        if (schemaVersion == "CreateJobResponse-v0.1") {
            checkEquals(
                DEFAULT_CREATED_AT,
                Instant.parse(body.requiredString("createdAt")),
                "deterministic creation time",
            )
            checkEquals(
                ENDPOINT_BINDING.endpointId,
                body.requiredString("endpointId"),
                "create endpoint binding",
            )
            checkEquals(
                ENDPOINT_BINDING.regionCode,
                body.requiredString("regionCode"),
                "create region binding",
            )
        }
        if (schemaVersion == "ResultResponse-v0.1") {
            body.requiredArray("body").forEach { value ->
                checkThat(
                    value is CanonicalValue.IntegerValue && value.value in 0..255,
                    "result unsigned byte representation",
                )
            }
        }
        return body
    }

    private fun objectBody(response: HarnessResponse): CanonicalValue.ObjectValue =
        DoraCanonicalJson.parseStrict(String(response.body, Charsets.UTF_8))
            as CanonicalValue.ObjectValue

    private fun assertReplayMarker(response: HarnessResponse, expected: Boolean) {
        val values =
            response.headers.entries
                .singleOrNull { it.key.equals("Idempotency-Replayed", ignoreCase = true) }
                ?.value
        if (expected) {
            checkEquals(listOf("true"), values, "idempotency replay marker true")
        } else {
            checkThat(values.isNullOrEmpty(), "first response omits replay marker")
        }
    }

    private fun commonHeaders(key: String? = null): Map<String, String> = buildMap {
        put("Content-Type", "application/json")
        put("X-Synthetic-Tenant-Id", SYNTHETIC_TENANT)
        put("X-Profile-Binding-Sha256", PROFILE_DIGEST.value)
        if (key != null) put("Idempotency-Key", key)
    }

    private fun encode(value: CanonicalValue): ByteArray =
        DoraCanonicalJson.encode(value).toByteArray(Charsets.UTF_8)

    private fun expectPreSendReject(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        }
        error("FAILED: expected pre-send rejection")
    }

    private fun assertPortClosed(port: Int) {
        val address = InetAddress.getByAddress(byteArrayOf(127.toByte(), 0, 0, 1))
        try {
            Socket().use { it.connect(InetSocketAddress(address, port), 200) }
        } catch (_: IOException) {
            return
        }
        error("FAILED: loopback port remained open")
    }

    private fun differentPort(port: Int): Int = if (port == 65_535) 65_534 else port + 1

    private fun checkThat(condition: Boolean, label: String) {
        if (!condition) error("FAILED: $label")
    }

    private fun checkEquals(expected: Any?, actual: Any?, label: String) {
        if (expected != actual) error("FAILED: $label")
    }

    private data class UploadedPart(val ordinal: Int, val bytes: ByteArray, val receiptId: String)

    private data class SchemaShape(
        val fields: Set<String>,
        val integerFields: Set<String> = emptySet(),
        val arrayFields: Set<String> = emptySet(),
        val booleanFields: Set<String> = emptySet(),
        val nullableStringFields: Set<String> = emptySet(),
    )

    private data class UploadedLifecycle(
        val jobId: String,
        val uploadId: String,
        val parts: List<UploadedPart>,
    )
}
