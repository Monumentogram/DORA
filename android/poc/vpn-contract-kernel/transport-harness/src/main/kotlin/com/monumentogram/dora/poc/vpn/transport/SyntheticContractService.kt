package com.monumentogram.dora.poc.vpn.transport

import com.monumentogram.dora.poc.vpn.contract.BodyDescriptor
import com.monumentogram.dora.poc.vpn.contract.CanonicalOutcome
import com.monumentogram.dora.poc.vpn.contract.CanonicalValue
import com.monumentogram.dora.poc.vpn.contract.ConsentProfileBinding
import com.monumentogram.dora.poc.vpn.contract.ContractCatalog
import com.monumentogram.dora.poc.vpn.contract.ContractOracle
import com.monumentogram.dora.poc.vpn.contract.DoraCanonicalJson
import com.monumentogram.dora.poc.vpn.contract.EffectVector
import com.monumentogram.dora.poc.vpn.contract.EndpointRegionBinding
import com.monumentogram.dora.poc.vpn.contract.IdempotencyDecision
import com.monumentogram.dora.poc.vpn.contract.IdempotencyKey
import com.monumentogram.dora.poc.vpn.contract.IdempotencyLedger
import com.monumentogram.dora.poc.vpn.contract.IdempotencyScope
import com.monumentogram.dora.poc.vpn.contract.OperationDefinition
import com.monumentogram.dora.poc.vpn.contract.PathParameter
import com.monumentogram.dora.poc.vpn.contract.PathValue
import com.monumentogram.dora.poc.vpn.contract.PreparedRequest
import com.monumentogram.dora.poc.vpn.contract.ProfileValidation
import com.monumentogram.dora.poc.vpn.contract.RequestPreparation
import com.monumentogram.dora.poc.vpn.contract.Sha256Hex
import com.monumentogram.dora.poc.vpn.contract.canonicalInteger
import com.monumentogram.dora.poc.vpn.contract.canonicalObject
import com.monumentogram.dora.poc.vpn.contract.canonicalString
import com.monumentogram.dora.poc.vpn.contract.requiredArray
import com.monumentogram.dora.poc.vpn.contract.requiredInteger
import com.monumentogram.dora.poc.vpn.contract.requiredString
import java.net.URI
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Locale

internal const val SNAPSHOT_KIND = "IN_MEMORY_SNAPSHOT_RESTORE_SIMULATION"
internal const val SYNTHETIC_TENANT = "tenant-synthetic-a"
@Suppress("MagicNumber") internal val PROFILE_DIGEST = Sha256Hex("a".repeat(64))
internal val ENDPOINT_BINDING = EndpointRegionBinding("endpoint-synthetic-a", "SYN-REGION-A")
internal val DEFAULT_CREATED_AT: Instant = Instant.parse("2026-06-01T00:00:00Z")
internal val DEFAULT_UPLOAD_EXPIRES_AT: Instant = Instant.parse("2026-06-01T00:15:00Z")
internal const val RESULT_BODY_WIRE_ENCODING = "JSON_UNSIGNED_BYTE_ARRAY"
internal val DELETE_COMMIT_SOURCE_STATES = setOf("RESULT_READY", "DELIVERED", "CANCELLED")

internal data class PartRecord(
    val partNumber: Int,
    val byteLength: Int,
    val sha256: Sha256Hex,
    val receiptId: String,
    val bytes: ByteArray,
)

internal data class JobRecord(
    val jobId: String,
    val createdAt: String,
    val syntheticEconomicEffectId: String,
    val fixtureId: String?,
    val totalByteLength: Int?,
    val totalSha256: Sha256Hex?,
    val endpointId: String,
    val regionCode: String,
    val state: String = "CREATED",
    val revision: Long = 1,
    val uploadId: String? = null,
    val planGeneration: Int = 0,
    val parts: Map<Int, PartRecord> = emptyMap(),
    val commitId: String? = null,
    val resultId: String? = null,
    val cancelReceiptId: String? = null,
)

internal data class DeletionState(
    val deletionId: String,
    val deletionReceiptId: String,
    val conversationFixtureId: String,
    val jobId: String?,
    val profileBindingSha256: Sha256Hex,
    val endpointId: String,
    val regionCode: String,
    val deleteResourceBindingSha256: Sha256Hex,
    val idempotencyKeyDigests: Set<Sha256Hex>,
    val state: String = "DELETE_PENDING",
    val receiptRevision: Long = 1,
)

internal data class RequestLedgerEntry(
    val operationId: String,
    val operationClass: String,
    val canonicalRequestDigest: String,
    val idempotencyKeyDigest: String?,
    val disposition: String,
    val applicationStatus: Int,
    val effectDelta: EffectVector,
)

internal data class ContentFreeOperationIdentity(
    val operationClass: String,
    val canonicalRequestDigest: Sha256Hex,
    val idempotencyKeyDigest: Sha256Hex?,
    val profileBindingSha256: Sha256Hex,
    val endpointId: String,
    val regionCode: String,
)

internal data class InMemoryServiceSnapshot(
    val snapshotKind: String,
    val ledger: IdempotencyLedger,
    val jobs: Map<String, JobRecord>,
    val deletions: Map<String, DeletionState>,
    val effects: EffectVector,
    val requestLedger: List<RequestLedgerEntry>,
    val mutationResponses: Map<IdempotencyScope, ByteArray>,
    val createdAt: String,
    val uploadExpiresAt: String,
)

internal data class PreSendDecision(
    val clientState: String,
    val visibleSubstatus: String?,
    val retryClass: String,
    val transportAllowed: Boolean,
    val preserveDeletionRecord: Boolean,
)

internal class ContractPreflightGuard(private val now: Instant) {
    fun evaluate(
        profile: ConsentProfileBinding?,
        expectedBinding: EndpointRegionBinding,
        endpointAllowlisted: Boolean,
        deletionPending: Boolean = false,
    ): PreSendDecision {
        if (profile == null) return blocked(deletionPending)
        return when (
            ContractOracle.validateProfile(profile, now, endpointAllowlisted, expectedBinding)
        ) {
            ProfileValidation.Valid -> PreSendDecision("READY", null, "NONE", true, deletionPending)
            is ProfileValidation.Rejected -> blocked(deletionPending)
        }
    }

    fun injectedDnsUnavailable(): PreSendDecision =
        PreSendDecision("WAITING_NETWORK", null, "WAIT_NETWORK", false, false)

    private fun blocked(deletionPending: Boolean): PreSendDecision =
        if (deletionPending) {
            PreSendDecision(
                "DELETE_PENDING",
                "DELETE_REVALIDATION_REQUIRED",
                "USER_ACTION_REQUIRED",
                false,
                true,
            )
        } else {
            PreSendDecision("BLOCKED_NO_PROFILE", null, "FINAL_REJECT", false, false)
        }
}

@Suppress("LargeClass", "MagicNumber", "TooManyFunctions")
internal class SyntheticContractService(
    snapshot: InMemoryServiceSnapshot? = null,
    injectedCreatedAt: Instant = DEFAULT_CREATED_AT,
    injectedUploadExpiresAt: Instant = DEFAULT_UPLOAD_EXPIRES_AT,
) {
    private val createdAt = snapshot?.createdAt?.let(Instant::parse) ?: injectedCreatedAt
    private val uploadExpiresAt =
        snapshot?.uploadExpiresAt?.let(Instant::parse) ?: injectedUploadExpiresAt
    private var ledger: IdempotencyLedger = snapshot?.ledger ?: IdempotencyLedger()
    private val jobs =
        snapshot?.jobs?.mapValues { (_, job) -> job.deepCopy() }?.toMutableMap() ?: mutableMapOf()
    private val deletions = snapshot?.deletions?.toMutableMap() ?: mutableMapOf()
    private var effects: EffectVector = snapshot?.effects ?: EffectVector.ZERO
    private val requestLedger = snapshot?.requestLedger?.toMutableList() ?: mutableListOf()
    private val mutationResponses =
        snapshot?.mutationResponses?.mapValues { (_, body) -> body.copyOf() }?.toMutableMap()
            ?: mutableMapOf()

    init {
        require(snapshot == null || snapshot.snapshotKind == SNAPSHOT_KIND) {
            "Only the frozen in-memory snapshot format is accepted"
        }
        require(uploadExpiresAt.isAfter(createdAt)) { "Upload expiry must follow creation time" }
    }

    @Synchronized
    fun operationClass(method: String, path: String): String? = route(method, path)?.operationClass

    @Synchronized
    @Suppress("LongParameterList")
    fun transportError(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: ByteArray,
        status: Int,
        code: String,
    ): HarnessResponse {
        val routed = route(method, path) ?: return errorResponse(status, code)
        val operation = ContractCatalog.operationsByClass.getValue(routed.operationClass)
        val descriptor = bodyDescriptor(operation, routed, headers, body)
        val prepared = descriptor?.let {
            ContractOracle.prepareRequest(operation, routed.parameters, it, PROFILE_DIGEST, path)
        }
        val digest =
            (prepared as? RequestPreparation.Accepted)?.request?.canonicalRequestDigest
                ?: Sha256Hex("0".repeat(64))
        return errorResponse(status, code, ErrorContext(operation.operationClass, digest))
    }

    @Synchronized
    @Suppress("ReturnCount")
    fun handle(
        method: String,
        path: String,
        headers: Map<String, String>,
        body: ByteArray,
    ): HarnessResponse {
        val routed = route(method, path) ?: return errorResponse(404, "ROUTE_NOT_FOUND")
        val operation = ContractCatalog.operationsByClass.getValue(routed.operationClass)
        if (headers["x-client-request-id"].isNullOrBlank()) {
            return rejectedBeforeLedger(operation, 400, "CLIENT_REQUEST_ID_REQUIRED")
        }
        if (
            headers["x-synthetic-tenant-id"] != SYNTHETIC_TENANT ||
                headers["x-profile-binding-sha256"] != PROFILE_DIGEST.value
        ) {
            return rejectedBeforeLedger(operation, 404, "RESOURCE_NOT_FOUND_OR_NOT_AUTHORIZED")
        }
        val descriptor =
            bodyDescriptor(operation, routed, headers, body)
                ?: return rejectedBeforeLedger(operation, 422, schemaError(operation))
        val preparation =
            ContractOracle.prepareRequest(
                operation,
                routed.parameters,
                descriptor,
                PROFILE_DIGEST,
                path,
            )
        if (preparation is RequestPreparation.Rejected) {
            return rejectedBeforeLedger(operation, 422, schemaError(operation))
        }
        val prepared = (preparation as RequestPreparation.Accepted).request
        return if (operation.idempotencyPolicy.startsWith("READ_ONLY")) {
            readOnly(operation, routed, prepared)
        } else {
            mutation(operation, routed, descriptor, prepared, headers, body)
        }
    }

    @Synchronized
    fun snapshot(): InMemoryServiceSnapshot =
        InMemoryServiceSnapshot(
            SNAPSHOT_KIND,
            ledger,
            jobs.mapValues { (_, job) -> job.deepCopy() },
            deletions.toMap(),
            effects,
            requestLedger.toList(),
            mutationResponses.mapValues { (_, body) -> body.copyOf() },
            createdAt.toString(),
            uploadExpiresAt.toString(),
        )

    @Synchronized fun contentFreeRequestLedger(): List<RequestLedgerEntry> = requestLedger.toList()

    @Synchronized fun effectTotals(): EffectVector = effects

    @Synchronized fun job(jobId: String): JobRecord? = jobs[jobId]?.deepCopy()

    @Synchronized fun deletion(deletionId: String): DeletionState? = deletions[deletionId]

    @Synchronized fun resultBytes(jobId: String): ByteArray? = jobs[jobId]?.let(::resultBytes)

    @Suppress("ReturnCount")
    @Synchronized
    fun contentFreeOperationIdentity(request: HarnessRequest): ContentFreeOperationIdentity? {
        if (!isCanonicalLoopbackUri(request.uri)) return null
        val routed = route(request.method, request.uri.rawPath) ?: return null
        if (routed.operationClass != request.operationClass) return null
        val operation = ContractCatalog.operationsByClass.getValue(routed.operationClass)
        val normalizedHeaders = normalize(request.headers) ?: return null
        val descriptor =
            bodyDescriptor(operation, routed, normalizedHeaders, request.body) ?: return null
        val prepared =
            ContractOracle.prepareRequest(
                operation,
                routed.parameters,
                descriptor,
                PROFILE_DIGEST,
                request.uri.rawPath,
            ) as? RequestPreparation.Accepted ?: return null
        val keyDigest =
            request.headers.headerValue("idempotency-key")?.let(ContractOracle::sha256Utf8)
        if (!operation.idempotencyPolicy.startsWith("READ_ONLY") && keyDigest == null) return null
        return ContentFreeOperationIdentity(
            operation.operationClass,
            prepared.request.canonicalRequestDigest,
            keyDigest,
            PROFILE_DIGEST,
            ENDPOINT_BINDING.endpointId,
            ENDPOINT_BINDING.regionCode,
        )
    }

    @Suppress("LongMethod", "LongParameterList", "ReturnCount")
    private fun mutation(
        operation: OperationDefinition,
        routed: RoutedOperation,
        descriptor: BodyDescriptor,
        prepared: PreparedRequest,
        headers: Map<String, String>,
        rawBody: ByteArray,
    ): HarnessResponse {
        val errorContext = ErrorContext(operation.operationClass, prepared.canonicalRequestDigest)
        val key =
            headers["idempotency-key"]
                ?: return rejectedBeforeLedger(
                    operation,
                    400,
                    "IDEMPOTENCY_KEY_REQUIRED",
                    prepared,
                )
        val scope =
            IdempotencyScope(
                SYNTHETIC_TENANT,
                PROFILE_DIGEST,
                operation.operationClass,
                IdempotencyKey(key),
            )
        val existing = ledger.entries[scope]
        val proposed =
            if (existing == null) {
                val rejection = validateNewMutation(operation, routed, descriptor, errorContext)
                if (rejection != null) {
                    recordCancelRejection(operation, routed, prepared, key, rejection)
                    return rejection
                }
                proposedMutation(operation, routed, descriptor, prepared, key)
            } else {
                null
            }
        val decision =
            ContractOracle.applyIdempotency(
                ledger,
                scope,
                prepared.canonicalRequestDigest,
                proposed?.outcome ?: checkNotNull(existing).canonicalOutcome,
            )
        return when (decision) {
            is IdempotencyDecision.PayloadMismatch -> {
                record(
                    operation,
                    prepared,
                    decision.evidence.idempotencyKeyDigest,
                    "PAYLOAD_MISMATCH",
                    decision.applicationStatus,
                    decision.effectDelta,
                )
                errorResponse(decision.applicationStatus, decision.errorCode, errorContext)
            }
            is IdempotencyDecision.Replayed -> {
                val replayBody = checkNotNull(mutationResponses[scope])
                check(
                    decision.canonicalOutcome.responseDigest == ContractOracle.sha256Hex(replayBody)
                )
                ledger = decision.ledger
                record(
                    operation,
                    prepared,
                    decision.evidence.idempotencyKeyDigest,
                    "REPLAYED",
                    decision.canonicalOutcome.applicationStatus,
                    decision.effectDelta,
                )
                HarnessResponse(
                    decision.canonicalOutcome.applicationStatus,
                    headers = mapOf("Idempotency-Replayed" to listOf("true")),
                    body = replayBody.copyOf(),
                )
            }
            is IdempotencyDecision.Committed -> {
                checkNotNull(proposed)
                applyMutation(operation, routed, descriptor, proposed.resourceIds, rawBody, key)
                mutationResponses[scope] = proposed.body.copyOf()
                ledger = decision.ledger
                effects += decision.effectDelta
                record(
                    operation,
                    prepared,
                    decision.evidence.idempotencyKeyDigest,
                    "COMMITTED",
                    decision.canonicalOutcome.applicationStatus,
                    decision.effectDelta,
                )
                HarnessResponse(decision.canonicalOutcome.applicationStatus, body = proposed.body)
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun proposedMutation(
        operation: OperationDefinition,
        routed: RoutedOperation,
        descriptor: BodyDescriptor,
        prepared: PreparedRequest,
        key: String,
    ): ProposedMutation {
        val seed =
            ContractOracle.sha256Utf8(
                    "${prepared.canonicalRequestDigest.value}|${ContractOracle.sha256Utf8(key).value}"
                )
                .value
        val ids =
            when (operation.operationClass) {
                "CREATE_JOB" ->
                    listOf(
                        "job-synthetic-${seed.take(12)}",
                        "economic-effect-synthetic-${seed.drop(12).take(12)}",
                    )
                "INIT_OR_REFRESH_UPLOAD" -> {
                    val existingUploadId = jobs.getValue(routed.opaque("jobId")).uploadId
                    listOf(existingUploadId ?: "upload-synthetic-${seed.take(12)}")
                }
                "UPLOAD_PART" -> {
                    val uploadId = routed.opaque("uploadId")
                    val ordinal = routed.positive("partNumber").toInt()
                    val existingReceipt =
                        jobs.values.singleOrNull { it.uploadId == uploadId }?.parts?.get(ordinal)
                    listOf(existingReceipt?.receiptId ?: "part-receipt-synthetic-${seed.take(12)}")
                }
                "COMPLETE_UPLOAD" ->
                    listOf(
                        "commit-synthetic-${seed.take(12)}",
                        "result-synthetic-${seed.drop(12).take(12)}",
                    )
                "CANCEL_JOB" -> listOf("cancel-receipt-synthetic-${seed.take(12)}")
                "DELETE_CLOUD_COPY" -> {
                    val existingDeletion =
                        deletionForConversation(routed.opaque("conversationFixtureId"))
                    if (existingDeletion != null) {
                        listOf(existingDeletion.deletionId, existingDeletion.deletionReceiptId)
                    } else {
                        listOf(
                            "deletion-synthetic-${seed.take(12)}",
                            "deletion-receipt-synthetic-${seed.drop(12).take(12)}",
                        )
                    }
                }
                else -> error("Unsupported mutation operation")
            }
        val body = mutationResponseBody(operation, routed, descriptor, ids)
        val convergesOnExistingResource =
            when (operation.operationClass) {
                "UPLOAD_PART" ->
                    jobs.values
                        .singleOrNull { it.uploadId == routed.opaque("uploadId") }
                        ?.parts
                        ?.containsKey(routed.positive("partNumber").toInt()) == true
                "DELETE_CLOUD_COPY" ->
                    deletionForConversation(routed.opaque("conversationFixtureId")) != null
                else -> false
            }
        val outcome =
            CanonicalOutcome(
                operation.successStatus,
                ContractOracle.sha256Hex(body),
                ids,
                if (convergesOnExistingResource) EffectVector.ZERO
                else committedEffects(operation.operationClass),
            )
        return ProposedMutation(ids, body, outcome)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun validateNewMutation(
        operation: OperationDefinition,
        routed: RoutedOperation,
        descriptor: BodyDescriptor,
        errorContext: ErrorContext,
    ): HarnessResponse? =
        when (operation.operationClass) {
            "CREATE_JOB" -> null
            "INIT_OR_REFRESH_UPLOAD" -> {
                val job =
                    jobs[routed.opaque("jobId")]
                        ?: return errorResponse(404, "JOB_NOT_FOUND", errorContext)
                val json = (descriptor as BodyDescriptor.Json).nonVolatileBody
                val priorUploadId = json.string("priorUploadId")
                val requestedGeneration = json.requiredInteger("requestedPlanGeneration").toInt()
                val validPlanCommit =
                    when (job.state) {
                        "CREATED" -> priorUploadId == null && requestedGeneration == 1
                        "WAITING_UPLOAD" ->
                            priorUploadId == job.uploadId &&
                                requestedGeneration == job.planGeneration + 1
                        else -> false
                    }
                if (
                    !validPlanCommit ||
                        json.requiredInteger("totalByteLength") != job.totalByteLength?.toLong() ||
                        json.requiredString("totalSha256") != job.totalSha256?.value
                ) {
                    errorResponse(409, "UPLOAD_PLAN_REJECTED", errorContext)
                } else null
            }
            "UPLOAD_PART" -> validatePart(routed, descriptor as BodyDescriptor.Binary, errorContext)
            "COMPLETE_UPLOAD" ->
                validateComplete(routed, descriptor as BodyDescriptor.Json, errorContext)
            "CANCEL_JOB" -> validateCancel(routed, errorContext)
            "DELETE_CLOUD_COPY" -> validateDelete(routed, errorContext)
            else -> errorResponse(500, "UNSUPPORTED_MUTATION", errorContext)
        }

    private fun validateDelete(
        routed: RoutedOperation,
        errorContext: ErrorContext,
    ): HarnessResponse? {
        val conversationFixtureId = routed.opaque("conversationFixtureId")
        val job =
            jobs.values.singleOrNull { conversationFixtureId(it.jobId) == conversationFixtureId }
        return when {
            deletionForConversation(conversationFixtureId) != null -> null
            job == null ->
                errorResponse(
                    404,
                    "RESOURCE_NOT_FOUND_OR_NOT_AUTHORIZED",
                    errorContext,
                )
            job.state in DELETE_COMMIT_SOURCE_STATES -> null
            else -> errorResponse(409, "DELETE_STATE_REJECTED", errorContext)
        }
    }

    @Suppress("ReturnCount")
    private fun validatePart(
        routed: RoutedOperation,
        binary: BodyDescriptor.Binary,
        errorContext: ErrorContext,
    ): HarnessResponse? {
        val uploadId = routed.opaque("uploadId")
        val job =
            jobs.values.singleOrNull { it.uploadId == uploadId }
                ?: return errorResponse(404, "UPLOAD_NOT_FOUND", errorContext)
        if (job.state !in UPLOAD_PART_SOURCE_STATES) {
            return errorResponse(409, "UPLOAD_STATE_REJECTED", errorContext)
        }
        val ordinal = routed.positive("partNumber").toInt()
        if (job.planGeneration != routed.positive("planGeneration").toInt()) {
            return errorResponse(409, "UPLOAD_PLAN_GENERATION_MISMATCH", errorContext)
        }
        val existing = job.parts[ordinal]
        if (existing != null) {
            return if (
                existing.byteLength == binary.byteLength.toInt() && existing.sha256 == binary.sha256
            )
                null
            else errorResponse(409, "IDEMPOTENCY_KEY_PAYLOAD_MISMATCH", errorContext)
        }
        val fixtureId = job.fixtureId ?: return errorResponse(404, "UPLOAD_NOT_FOUND", errorContext)
        val fixture = ContractCatalog.fixtures.single { it.id == fixtureId }
        val expected =
            fixture.parts.getOrNull(ordinal - 1)
                ?: return errorResponse(422, "MANIFEST_INVALID", errorContext)
        if (binary.byteLength != expected.byteLength.toLong() || binary.sha256 != expected.sha256) {
            return errorResponse(422, "CHECKSUM_MISMATCH", errorContext)
        }
        return null
    }

    @Suppress("ComplexCondition", "CyclomaticComplexMethod", "ReturnCount")
    private fun validateComplete(
        routed: RoutedOperation,
        descriptor: BodyDescriptor.Json,
        errorContext: ErrorContext,
    ): HarnessResponse? {
        val job =
            jobs[routed.opaque("jobId")] ?: return errorResponse(404, "JOB_NOT_FOUND", errorContext)
        val body = descriptor.nonVolatileBody
        if (job.state != COMPLETE_SOURCE_STATE) {
            return errorResponse(409, "COMPLETE_STATE_REJECTED", errorContext)
        }
        if (job.uploadId != body.requiredString("uploadId"))
            return errorResponse(409, "UPLOAD_NOT_FOUND", errorContext)
        val manifest = body.requiredArray("manifest").map { it as CanonicalValue.ObjectValue }
        if (manifest.size != job.parts.size || manifest.isEmpty())
            return errorResponse(422, "MANIFEST_INVALID", errorContext)
        val manifestMatches =
            manifest.withIndex().all { (index, entry) ->
                val part = job.parts[index + 1] ?: return@all false
                entry.requiredInteger("partNumber") == part.partNumber.toLong() &&
                    entry.requiredInteger("byteLength") == part.byteLength.toLong() &&
                    entry.requiredString("sha256") == part.sha256.value &&
                    entry.requiredString("partReceiptId") == part.receiptId
            }
        if (!manifestMatches) return errorResponse(422, "MANIFEST_INVALID", errorContext)
        val reconstructed =
            job.parts.toSortedMap().values.flatMap { it.bytes.asIterable() }.toByteArray()
        if (
            body.requiredInteger("totalByteLength") != reconstructed.size.toLong() ||
                body.requiredString("totalSha256") !=
                    ContractOracle.sha256Hex(reconstructed).value ||
                reconstructed.size != job.totalByteLength ||
                ContractOracle.sha256Hex(reconstructed) != job.totalSha256
        ) {
            return errorResponse(422, "OVERALL_CHECKSUM_MISMATCH", errorContext)
        }
        return null
    }

    private fun validateCancel(
        routed: RoutedOperation,
        errorContext: ErrorContext,
    ): HarnessResponse? {
        val job =
            jobs[routed.opaque("jobId")] ?: return errorResponse(404, "JOB_NOT_FOUND", errorContext)
        return when {
            job.state in CANCEL_SOURCE_STATES -> null
            job.state == "DELETE_PENDING" ->
                errorResponse(409, "CANCEL_NOT_APPLICABLE_DELETE_PENDING", errorContext)
            job.state in setOf("RESULT_READY", "DELIVERED", "CANCELLED") ->
                errorResponse(409, "ALREADY_TERMINAL", errorContext)
            else -> errorResponse(409, "CANCEL_STATE_REJECTED", errorContext)
        }
    }

    private fun recordCancelRejection(
        operation: OperationDefinition,
        routed: RoutedOperation,
        prepared: PreparedRequest,
        key: String,
        response: HarnessResponse,
    ) {
        if (
            operation.operationClass == "CANCEL_JOB" &&
                jobs[routed.opaque("jobId")]?.state == "DELETE_PENDING"
        ) {
            record(
                operation,
                prepared,
                ContractOracle.sha256Utf8(key),
                "REJECTED_NO_STATE_CHANGE",
                response.status,
                EffectVector.ZERO,
            )
        }
    }

    @Suppress("LongMethod", "LongParameterList")
    private fun applyMutation(
        operation: OperationDefinition,
        routed: RoutedOperation,
        descriptor: BodyDescriptor,
        ids: List<String>,
        rawBody: ByteArray,
        key: String,
    ) {
        when (operation.operationClass) {
            "CREATE_JOB" -> {
                val body = (descriptor as BodyDescriptor.Json).nonVolatileBody
                val job =
                    JobRecord(
                        ids[0],
                        createdAt.toString(),
                        ids[1],
                        body.requiredString("fixtureId"),
                        body.requiredInteger("payloadByteLength").toInt(),
                        Sha256Hex(body.requiredString("payloadSha256")),
                        ENDPOINT_BINDING.endpointId,
                        ENDPOINT_BINDING.regionCode,
                    )
                jobs[job.jobId] = job
            }
            "INIT_OR_REFRESH_UPLOAD" -> {
                val jobId = routed.opaque("jobId")
                val body = (descriptor as BodyDescriptor.Json).nonVolatileBody
                val job = jobs.getValue(jobId)
                jobs[jobId] =
                    job.copy(
                        state = "WAITING_UPLOAD",
                        revision = job.revision + 1,
                        uploadId = ids.single(),
                        planGeneration = body.requiredInteger("requestedPlanGeneration").toInt(),
                    )
            }
            "UPLOAD_PART" -> {
                val uploadId = routed.opaque("uploadId")
                val job = jobs.values.single { it.uploadId == uploadId }
                val binary = descriptor as BodyDescriptor.Binary
                val ordinal = routed.positive("partNumber").toInt()
                if (ordinal in job.parts) return
                val part =
                    PartRecord(
                        ordinal,
                        binary.byteLength.toInt(),
                        binary.sha256,
                        ids.single(),
                        rawBody.copyOf(),
                    )
                jobs[job.jobId] =
                    job.copy(
                        state = "UPLOADING",
                        revision = job.revision + 1,
                        parts = job.parts + (ordinal to part),
                    )
            }
            "COMPLETE_UPLOAD" -> {
                val jobId = routed.opaque("jobId")
                val job = jobs.getValue(jobId)
                jobs[jobId] =
                    job.copy(
                        state = "RESULT_READY",
                        revision = job.revision + 1,
                        commitId = ids[0],
                        resultId = ids[1],
                    )
            }
            "CANCEL_JOB" -> {
                val jobId = routed.opaque("jobId")
                val job = jobs.getValue(jobId)
                jobs[jobId] =
                    job.copy(
                        state = "CANCELLED",
                        revision = job.revision + 1,
                        cancelReceiptId = ids.single(),
                    )
            }
            "DELETE_CLOUD_COPY" -> {
                val conversation = routed.opaque("conversationFixtureId")
                val keyDigest = ContractOracle.sha256Utf8(key)
                val existing = deletionForConversation(conversation)
                if (existing != null) {
                    deletions[existing.deletionId] =
                        existing.copy(
                            idempotencyKeyDigests = existing.idempotencyKeyDigests + keyDigest
                        )
                    return
                }
                val job =
                    jobs.values.singleOrNull { conversationFixtureId(it.jobId) == conversation }
                checkNotNull(job)
                deletions[ids[0]] =
                    DeletionState(
                        ids[0],
                        ids[1],
                        conversation,
                        job.jobId,
                        PROFILE_DIGEST,
                        ENDPOINT_BINDING.endpointId,
                        ENDPOINT_BINDING.regionCode,
                        ContractOracle.deleteResourceBindingDigest(
                            SYNTHETIC_TENANT,
                            PROFILE_DIGEST,
                            ENDPOINT_BINDING,
                            conversation,
                        ),
                        setOf(keyDigest),
                    )
                jobs[job.jobId] = job.copy(state = "DELETE_PENDING", revision = job.revision + 1)
            }
        }
    }

    @Suppress("LongMethod")
    private fun mutationResponseBody(
        operation: OperationDefinition,
        routed: RoutedOperation,
        descriptor: BodyDescriptor,
        ids: List<String>,
    ): ByteArray {
        val value =
            when (operation.operationClass) {
                "CREATE_JOB" ->
                    canonicalObject(
                        "schemaVersion" to canonicalString(operation.responseSchema),
                        "jobId" to canonicalString(ids[0]),
                        "state" to canonicalString("CREATED"),
                        "endpointId" to canonicalString(ENDPOINT_BINDING.endpointId),
                        "regionCode" to canonicalString(ENDPOINT_BINDING.regionCode),
                        "createdAt" to canonicalString(createdAt.toString()),
                        "syntheticEconomicEffectId" to canonicalString(ids[1]),
                    )
                "INIT_OR_REFRESH_UPLOAD" -> {
                    val body = (descriptor as BodyDescriptor.Json).nonVolatileBody
                    val jobId = routed.opaque("jobId")
                    val fixtureId = checkNotNull(jobs.getValue(jobId).fixtureId)
                    val fixture = ContractCatalog.fixtures.single { it.id == fixtureId }
                    val parts =
                        fixture.parts.mapIndexed { index, part ->
                            val ordinal = index + 1
                            canonicalObject(
                                "partNumber" to canonicalInteger(ordinal.toLong()),
                                "urlTokenId" to
                                    canonicalString(
                                        "url-token-synthetic-${
                                            ContractOracle.sha256Utf8("${ids[0]}|$ordinal").value.take(12)
                                        }"
                                    ),
                                "expectedByteLength" to canonicalInteger(part.byteLength.toLong()),
                            )
                        }
                    canonicalObject(
                        "schemaVersion" to canonicalString(operation.responseSchema),
                        "jobId" to canonicalString(jobId),
                        "uploadId" to canonicalString(ids[0]),
                        "planGeneration" to
                            canonicalInteger(body.requiredInteger("requestedPlanGeneration")),
                        "partSizeBytes" to
                            canonicalInteger(ContractCatalog.PART_SIZE_BYTES.toLong()),
                        "expiresAt" to canonicalString(uploadExpiresAt.toString()),
                        "endpointId" to canonicalString(ENDPOINT_BINDING.endpointId),
                        "regionCode" to canonicalString(ENDPOINT_BINDING.regionCode),
                        "parts" to CanonicalValue.ArrayValue(parts),
                    )
                }
                "UPLOAD_PART" -> {
                    val binary = descriptor as BodyDescriptor.Binary
                    canonicalObject(
                        "schemaVersion" to canonicalString(operation.responseSchema),
                        "uploadId" to canonicalString(routed.opaque("uploadId")),
                        "partNumber" to canonicalInteger(routed.positive("partNumber")),
                        "byteLength" to canonicalInteger(binary.byteLength),
                        "sha256" to canonicalString(binary.sha256.value),
                        "partReceiptId" to canonicalString(ids[0]),
                    )
                }
                "COMPLETE_UPLOAD" ->
                    canonicalObject(
                        "schemaVersion" to canonicalString(operation.responseSchema),
                        "jobId" to canonicalString(routed.opaque("jobId")),
                        "uploadId" to
                            canonicalString(
                                (descriptor as BodyDescriptor.Json)
                                    .nonVolatileBody
                                    .requiredString("uploadId")
                            ),
                        "commitId" to canonicalString(ids[0]),
                        "state" to canonicalString("RESULT_READY"),
                    )
                "CANCEL_JOB" ->
                    canonicalObject(
                        "schemaVersion" to canonicalString(operation.responseSchema),
                        "jobId" to canonicalString(routed.opaque("jobId")),
                        "cancelReceiptId" to canonicalString(ids[0]),
                        "state" to canonicalString("CANCELLED"),
                        "winningTerminalCommit" to canonicalString("CANCELLED"),
                    )
                "DELETE_CLOUD_COPY" -> {
                    val deletion = deletionForConversation(routed.opaque("conversationFixtureId"))
                    canonicalObject(
                        "schemaVersion" to canonicalString(operation.responseSchema),
                        "deletionId" to canonicalString(ids[0]),
                        "conversationFixtureId" to
                            canonicalString(routed.opaque("conversationFixtureId")),
                        "state" to canonicalString(deletion?.state ?: "DELETE_PENDING"),
                    )
                }
                else -> error("Unsupported mutation response")
            }
        return DoraCanonicalJson.encode(value).toByteArray(StandardCharsets.UTF_8)
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    private fun readOnly(
        operation: OperationDefinition,
        routed: RoutedOperation,
        prepared: PreparedRequest,
    ): HarnessResponse {
        val errorContext = ErrorContext(operation.operationClass, prepared.canonicalRequestDigest)
        val response =
            when (operation.operationClass) {
                "POLL_JOB" -> {
                    val jobId = routed.opaque("jobId")
                    if (isDeletedJob(jobId)) {
                        return errorResponse(
                            404,
                            "RESOURCE_NOT_FOUND_OR_NOT_AUTHORIZED",
                            errorContext,
                        )
                    }
                    val job =
                        jobs[jobId]
                            ?: return errorResponse(
                                404,
                                "RESOURCE_NOT_FOUND_OR_NOT_AUTHORIZED",
                                errorContext,
                            )
                    val statusEtag = "status-etag-synthetic-${job.revision}"
                    val body =
                        canonicalObject(
                            "schemaVersion" to canonicalString(operation.responseSchema),
                            "jobId" to canonicalString(job.jobId),
                            "state" to canonicalString(job.state),
                            "revision" to canonicalInteger(job.revision),
                            "statusEtag" to canonicalString(statusEtag),
                            "retryHintClass" to CanonicalValue.NullValue,
                        )
                    HarnessResponse(
                        200,
                        mapOf("ETag" to listOf("\"$statusEtag\"")),
                        encode(body),
                    )
                }
                "FETCH_RESULT" -> {
                    val jobId = routed.opaque("jobId")
                    if (isDeletedJob(jobId)) {
                        return errorResponse(
                            404,
                            "RESOURCE_NOT_FOUND_OR_NOT_AUTHORIZED",
                            errorContext,
                        )
                    }
                    val job =
                        jobs[jobId]
                            ?: return errorResponse(
                                404,
                                "RESOURCE_NOT_FOUND_OR_NOT_AUTHORIZED",
                                errorContext,
                            )
                    if (job.state !in RESULT_FETCH_SOURCE_STATES || job.resultId == null) {
                        return errorResponse(409, "RESULT_NOT_AVAILABLE", errorContext)
                    }
                    val resultBytes =
                        resultBytes(job)
                            ?: return errorResponse(409, "RESULT_NOT_AVAILABLE", errorContext)
                    if (job.state == "RESULT_READY") {
                        jobs[jobId] = job.copy(state = "DELIVERED", revision = job.revision + 1)
                    }
                    HarnessResponse(
                        200,
                        body =
                            encode(
                                canonicalObject(
                                    "schemaVersion" to canonicalString(operation.responseSchema),
                                    "jobId" to canonicalString(job.jobId),
                                    "resultId" to canonicalString(job.resultId),
                                    "artifactClass" to canonicalString("SYNTHETIC_BYTES"),
                                    "byteLength" to canonicalInteger(resultBytes.size.toLong()),
                                    "sha256" to
                                        canonicalString(
                                            ContractOracle.sha256Hex(resultBytes).value
                                        ),
                                    "body" to
                                        CanonicalValue.ArrayValue(
                                            resultBytes.map { byte ->
                                                canonicalInteger((byte.toInt() and 0xff).toLong())
                                            }
                                        ),
                                )
                            ),
                    )
                }
                "POLL_DELETION_RECEIPT" -> {
                    val deletionId = routed.opaque("deletionId")
                    val deletion =
                        deletions[deletionId]
                            ?: return errorResponse(404, "DELETION_NOT_FOUND", errorContext)
                    val terminal = verifiedDeletion(deletion)
                    HarnessResponse(
                        200,
                        body =
                            encode(
                                canonicalObject(
                                    "schemaVersion" to canonicalString(operation.responseSchema),
                                    "deletionId" to canonicalString(terminal.deletionId),
                                    "deletionReceiptId" to
                                        canonicalString(terminal.deletionReceiptId),
                                    "state" to canonicalString(terminal.state),
                                    "verifiedAbsent" to CanonicalValue.BooleanValue(true),
                                )
                            ),
                    )
                }
                else -> errorResponse(500, "UNSUPPORTED_READ", errorContext)
            }
        record(operation, prepared, null, "READ_ONLY", response.status, EffectVector.ZERO)
        return response
    }

    @Suppress("ReturnCount")
    private fun bodyDescriptor(
        operation: OperationDefinition,
        routed: RoutedOperation,
        headers: Map<String, String>,
        body: ByteArray,
    ): BodyDescriptor? {
        if (operation.operationClass == "UPLOAD_PART") {
            if (body.isEmpty()) return null
            val declared = headers["x-content-sha256"]?.let(::safeSha) ?: return null
            val actual = ContractOracle.sha256Hex(body)
            if (actual != declared) return null
            return BodyDescriptor.Binary(
                body.size.toLong(),
                actual,
                routed.opaque("uploadId"),
                routed.positive("partNumber"),
                routed.positive("planGeneration"),
            )
        }
        if (operation.requestSchema == "NONE") return BodyDescriptor.None.takeIf { body.isEmpty() }
        val decoded =
            try {
                StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body))
                    .toString()
            } catch (_: CharacterCodingException) {
                return null
            }
        val parsed =
            try {
                DoraCanonicalJson.parseStrict(decoded)
            } catch (_: IllegalArgumentException) {
                return null
            }
        val objectValue = parsed as? CanonicalValue.ObjectValue ?: return null
        if (!validateJsonRequest(operation, objectValue)) return null
        return BodyDescriptor.Json(objectValue)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun validateJsonRequest(
        operation: OperationDefinition,
        body: CanonicalValue.ObjectValue,
    ): Boolean =
        when (operation.operationClass) {
            "CREATE_JOB" ->
                body.keys() ==
                    setOf(
                        "schemaVersion",
                        "profileBindingSha256",
                        "syntheticTenantId",
                        "fixtureId",
                        "artifactClass",
                        "purpose",
                        "payloadByteLength",
                        "payloadSha256",
                    ) &&
                    body.string("schemaVersion") == operation.requestSchema &&
                    body.string("profileBindingSha256")?.let(::safeSha) != null &&
                    body.string("syntheticTenantId") != null &&
                    body.string("fixtureId") != null &&
                    body.string("artifactClass") == "SYNTHETIC_BYTES" &&
                    body.string("purpose") == "STAGE0_CONTRACT_TEST" &&
                    body.integer("payloadByteLength")?.let { it > 0 } == true &&
                    body.string("payloadSha256")?.let(::safeSha) != null
            "INIT_OR_REFRESH_UPLOAD" ->
                body.keys() ==
                    setOf(
                        "schemaVersion",
                        "jobId",
                        "priorUploadId",
                        "requestedPlanGeneration",
                        "partSizeBytes",
                        "totalByteLength",
                        "totalSha256",
                    ) &&
                    body.string("schemaVersion") == operation.requestSchema &&
                    body.string("jobId") != null &&
                    body.value("priorUploadId").let {
                        it is CanonicalValue.StringValue || it is CanonicalValue.NullValue
                    } &&
                    body.integer("requestedPlanGeneration")?.let { it > 0 } == true &&
                    body.integer("partSizeBytes") == ContractCatalog.PART_SIZE_BYTES.toLong() &&
                    body.integer("totalByteLength")?.let { it > 0 } == true &&
                    body.string("totalSha256")?.let(::safeSha) != null
            "COMPLETE_UPLOAD" -> validateCompleteRequestSchema(operation, body)
            else -> false
        }

    @Suppress("ComplexCondition", "CyclomaticComplexMethod", "ReturnCount")
    private fun validateCompleteRequestSchema(
        operation: OperationDefinition,
        body: CanonicalValue.ObjectValue,
    ): Boolean {
        if (
            body.keys() !=
                setOf(
                    "schemaVersion",
                    "jobId",
                    "uploadId",
                    "manifest",
                    "totalByteLength",
                    "totalSha256",
                ) ||
                body.string("schemaVersion") != operation.requestSchema ||
                body.string("jobId") == null ||
                body.string("uploadId") == null ||
                body.integer("totalByteLength")?.let { it > 0 } != true ||
                body.string("totalSha256")?.let(::safeSha) == null
        ) {
            return false
        }
        val manifest = body.value("manifest") as? CanonicalValue.ArrayValue ?: return false
        return manifest.values.all { value ->
            val entry = value as? CanonicalValue.ObjectValue ?: return@all false
            entry.keys() == setOf("partNumber", "byteLength", "sha256", "partReceiptId") &&
                entry.integer("partNumber")?.let { it > 0 } == true &&
                entry.integer("byteLength")?.let { it > 0 } == true &&
                entry.string("sha256")?.let(::safeSha) != null &&
                entry.string("partReceiptId") != null
        }
    }

    private fun rejectedBeforeLedger(
        operation: OperationDefinition,
        status: Int,
        error: String,
        prepared: PreparedRequest? = null,
    ): HarnessResponse {
        val requestDigest = prepared?.canonicalRequestDigest ?: Sha256Hex("0".repeat(64))
        requestLedger +=
            RequestLedgerEntry(
                operation.id.value,
                operation.operationClass,
                requestDigest.value,
                null,
                "REJECTED_BEFORE_IDEMPOTENCY",
                status,
                EffectVector.ZERO,
            )
        return errorResponse(status, error, ErrorContext(operation.operationClass, requestDigest))
    }

    @Suppress("LongParameterList")
    private fun record(
        operation: OperationDefinition,
        prepared: PreparedRequest,
        keyDigest: Sha256Hex?,
        disposition: String,
        status: Int,
        delta: EffectVector,
    ) {
        requestLedger +=
            RequestLedgerEntry(
                operation.id.value,
                operation.operationClass,
                prepared.canonicalRequestDigest.value,
                keyDigest?.value,
                disposition,
                status,
                delta,
            )
    }

    @Suppress("ReturnCount")
    private fun route(method: String, path: String): RoutedOperation? {
        ROUTES.forEach { definition ->
            if (definition.method != method) return@forEach
            val match = definition.regex.matchEntire(path) ?: return@forEach
            val parameters =
                definition.parameters.mapIndexed { index, parameter ->
                    val value = match.groupValues[index + 1]
                    PathParameter(
                        parameter.first,
                        if (parameter.second)
                            PathValue.PositiveInteger(value.toLongOrNull() ?: return null)
                        else PathValue.OpaqueId(value),
                    )
                }
            return RoutedOperation(definition.operationClass, parameters)
        }
        return null
    }

    private fun deletionForConversation(conversationFixtureId: String): DeletionState? =
        deletions.values.singleOrNull {
            it.conversationFixtureId == conversationFixtureId
        }

    private fun isDeletedJob(jobId: String): Boolean =
        deletions.values.any { it.jobId == jobId && it.state == "DELETED" }

    private fun verifiedDeletion(deletion: DeletionState): DeletionState {
        if (deletion.state == "DELETED") return deletion
        check(deletion.state == "DELETE_PENDING")
        val terminal =
            deletion.copy(state = "DELETED", receiptRevision = deletion.receiptRevision + 1)
        deletion.jobId?.let { jobId ->
            jobs[jobId]?.let { job ->
                check(job.state == "DELETE_PENDING")
                jobs[jobId] =
                    job.copy(
                        fixtureId = null,
                        totalByteLength = null,
                        totalSha256 = null,
                        state = "DELETED",
                        revision = job.revision + 1,
                        uploadId = null,
                        planGeneration = 0,
                        parts = emptyMap(),
                        commitId = null,
                        resultId = null,
                        cancelReceiptId = null,
                    )
            }
        }
        deletions[deletion.deletionId] = terminal
        return terminal
    }

    private fun resultBytes(job: JobRecord): ByteArray? {
        val expectedByteLength = job.totalByteLength
        val expectedSha256 = job.totalSha256
        if (expectedByteLength == null || expectedSha256 == null || job.parts.isEmpty()) return null
        return job.parts
            .toSortedMap()
            .values
            .flatMap { it.bytes.asIterable() }
            .toByteArray()
            .also { bytes ->
                check(bytes.size == expectedByteLength) { "Result byte length diverged" }
                check(ContractOracle.sha256Hex(bytes) == expectedSha256) {
                    "Result digest diverged"
                }
            }
    }

    private fun committedEffects(operationClass: String): EffectVector =
        when (operationClass) {
            "CREATE_JOB" ->
                EffectVector(serverStateTransitions = 1, resources = 1, economicEffects = 1)
            "INIT_OR_REFRESH_UPLOAD" -> EffectVector(serverStateTransitions = 1, resources = 1)
            "UPLOAD_PART" -> EffectVector(resources = 1, receipts = 1)
            "COMPLETE_UPLOAD" -> EffectVector(serverStateTransitions = 1, resources = 2)
            "CANCEL_JOB" -> EffectVector(serverStateTransitions = 1, receipts = 1)
            "DELETE_CLOUD_COPY" ->
                EffectVector(
                    serverStateTransitions = 1,
                    resources = 1,
                    receipts = 1,
                    deletionRecords = 1,
                )
            else -> EffectVector.ZERO
        }

    private fun schemaError(operation: OperationDefinition): String =
        if (operation.operationClass == "COMPLETE_UPLOAD") "MANIFEST_INVALID"
        else if (operation.operationClass == "UPLOAD_PART") "CHECKSUM_MISMATCH"
        else "SCHEMA_VALIDATION_FAILED"

    private fun safeSha(value: String): Sha256Hex? =
        try {
            Sha256Hex(value)
        } catch (_: IllegalArgumentException) {
            null
        }

    private fun normalize(headers: Map<String, String>): Map<String, String>? {
        val normalized =
            headers.entries.associate { (name, value) -> name.lowercase(Locale.ROOT) to value }
        return normalized.takeIf { it.size == headers.size }
    }

    private fun isCanonicalLoopbackUri(uri: URI): Boolean =
        listOf(
                uri.scheme == "http",
                uri.host == LOOPBACK_HOST,
                uri.port in 1..65_535,
                uri.rawAuthority == "$LOOPBACK_HOST:${uri.port}",
                uri.rawUserInfo == null,
                uri.rawQuery == null,
                uri.rawFragment == null,
            )
            .all { it }

    private fun Map<String, String>.headerValue(name: String): String? =
        entries.singleOrNull { it.key.equals(name, ignoreCase = true) }?.value

    private fun CanonicalValue.ObjectValue.string(name: String): String? =
        (value(name) as? CanonicalValue.StringValue)?.value

    private fun CanonicalValue.ObjectValue.integer(name: String): Long? =
        (value(name) as? CanonicalValue.IntegerValue)?.value

    private fun errorResponse(
        status: Int,
        code: String,
        context: ErrorContext = ErrorContext.UNROUTED,
    ): HarnessResponse =
        HarnessResponse(
            status,
            body =
                encode(
                    canonicalObject(
                        "schemaVersion" to canonicalString("ErrorResponse-v0.1"),
                        "errorCode" to canonicalString(code),
                        "retryClass" to
                            canonicalString(
                                when {
                                    code == "UPLOAD_URL_EXPIRED" -> "REFRESH_UPLOAD_PLAN"
                                    status == 429 || status in 500..599 -> "BACKOFF_REPLAY"
                                    else -> "FINAL_REJECT"
                                }
                            ),
                        "operationClass" to canonicalString(context.operationClass),
                        "requestDigest" to canonicalString(context.requestDigest.value),
                        "contentFreeDetailCode" to canonicalString("I2_$code"),
                    )
                ),
        )

    private fun encode(value: CanonicalValue): ByteArray =
        DoraCanonicalJson.encode(value).toByteArray(StandardCharsets.UTF_8)

    private data class ProposedMutation(
        val resourceIds: List<String>,
        val body: ByteArray,
        val outcome: CanonicalOutcome,
    )

    private data class ErrorContext(
        val operationClass: String,
        val requestDigest: Sha256Hex,
    ) {
        companion object {
            val UNROUTED = ErrorContext("UNROUTED", Sha256Hex("0".repeat(64)))
        }
    }

    private data class RoutedOperation(
        val operationClass: String,
        val parameters: List<PathParameter>,
    ) {
        fun opaque(name: String): String =
            (parameters.single { it.name == name }.value as PathValue.OpaqueId).value

        fun positive(name: String): Long =
            (parameters.single { it.name == name }.value as PathValue.PositiveInteger).value
    }

    private data class RouteDefinition(
        val operationClass: String,
        val method: String,
        val regex: Regex,
        val parameters: List<Pair<String, Boolean>>,
    )

    private companion object {
        private const val COMPLETE_SOURCE_STATE = "UPLOADING"
        private val UPLOAD_PART_SOURCE_STATES = setOf("WAITING_UPLOAD", "UPLOADING")
        private val CANCEL_SOURCE_STATES =
            setOf(
                "CREATED",
                "WAITING_UPLOAD",
                "UPLOADING",
                "UPLOAD_COMPLETE",
                "QUEUED",
                "PROCESSING",
            )
        private val RESULT_FETCH_SOURCE_STATES = setOf("RESULT_READY", "DELIVERED")
        private val ROUTES =
            listOf(
                RouteDefinition("CREATE_JOB", "POST", Regex("^/v1/processing-jobs$"), emptyList()),
                RouteDefinition(
                    "INIT_OR_REFRESH_UPLOAD",
                    "POST",
                    Regex("^/v1/processing-jobs/([A-Za-z0-9._~-]+)/uploads$"),
                    listOf("jobId" to false),
                ),
                RouteDefinition(
                    "UPLOAD_PART",
                    "PUT",
                    Regex("^/synthetic-upload/([A-Za-z0-9._~-]+)/(\\d+)/([1-9]\\d*)$"),
                    listOf("uploadId" to false, "planGeneration" to true, "partNumber" to true),
                ),
                RouteDefinition(
                    "COMPLETE_UPLOAD",
                    "POST",
                    Regex("^/v1/processing-jobs/([A-Za-z0-9._~-]+)/uploads:complete$"),
                    listOf("jobId" to false),
                ),
                RouteDefinition(
                    "POLL_JOB",
                    "GET",
                    Regex("^/v1/processing-jobs/([A-Za-z0-9._~-]+)$"),
                    listOf("jobId" to false),
                ),
                RouteDefinition(
                    "FETCH_RESULT",
                    "GET",
                    Regex("^/v1/processing-jobs/([A-Za-z0-9._~-]+)/result$"),
                    listOf("jobId" to false),
                ),
                RouteDefinition(
                    "CANCEL_JOB",
                    "POST",
                    Regex("^/v1/processing-jobs/([A-Za-z0-9._~-]+):cancel$"),
                    listOf("jobId" to false),
                ),
                RouteDefinition(
                    "DELETE_CLOUD_COPY",
                    "DELETE",
                    Regex("^/v1/conversations/([A-Za-z0-9._~-]+)/cloud-copy$"),
                    listOf("conversationFixtureId" to false),
                ),
                RouteDefinition(
                    "POLL_DELETION_RECEIPT",
                    "GET",
                    Regex("^/v1/deletions/([A-Za-z0-9._~-]+)$"),
                    listOf("deletionId" to false),
                ),
            )
    }
}

@Suppress("MagicNumber")
internal fun conversationFixtureId(jobId: String): String =
    "conversation-synthetic-${ContractOracle.sha256Utf8(jobId).value.take(12)}"

private fun JobRecord.deepCopy(): JobRecord =
    copy(parts = parts.mapValues { (_, part) -> part.copy(bytes = part.bytes.copyOf()) })
