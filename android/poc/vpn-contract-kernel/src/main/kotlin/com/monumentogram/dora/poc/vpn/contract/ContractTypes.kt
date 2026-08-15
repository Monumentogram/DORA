package com.monumentogram.dora.poc.vpn.contract

import java.time.Instant

@JvmInline
value class Sha256Hex(val value: String) {
    init {
        require(SHA256_HEX.matches(value)) { "Expected lowercase SHA-256 hex" }
    }

    companion object {
        private val SHA256_HEX = Regex("[0-9a-f]{64}")
    }
}

@JvmInline value class OperationId(val value: String)

@JvmInline value class LogicalRequestId(val value: String)

@JvmInline value class CanonicalResponseId(val value: String)

@JvmInline
value class IdempotencyKey(val value: String) {
    init {
        require(value.isNotEmpty()) { "Idempotency key must not be empty" }
    }
}

data class EndpointRegionBinding(val endpointId: String, val regionCode: String) {
    init {
        require(endpointId.isNotEmpty()) { "endpointId must not be empty" }
        require(regionCode.isNotEmpty()) { "regionCode must not be empty" }
    }
}

enum class ArtifactClass {
    SYNTHETIC_BYTES
}

enum class ConsentPurpose {
    STAGE0_CONTRACT_TEST
}

data class ConsentProfileBinding(
    val profileId: String,
    val consentReceiptId: String,
    val policyVersion: String,
    val artifactClass: ArtifactClass,
    val purpose: ConsentPurpose,
    val endpointId: String,
    val regionCode: String,
    val tenantFixtureId: String,
    val issuedAt: String,
    val expiresAt: String,
    val revokedAt: String?,
    val endpointAllowlistDigest: Sha256Hex,
    val profileBindingSha256: Sha256Hex,
) {
    val endpointRegionBinding: EndpointRegionBinding
        get() = EndpointRegionBinding(endpointId, regionCode)
}

sealed interface ProfileValidation {
    data object Valid : ProfileValidation

    data class Rejected(val reason: String) : ProfileValidation
}

sealed interface CanonicalValue {
    data class ObjectValue(val entries: List<Pair<String, CanonicalValue>>) : CanonicalValue {
        init {
            val keys = entries.map { it.first }
            require(keys.size == keys.toSet().size) { "Duplicate JSON object key" }
        }

        constructor(vararg entries: Pair<String, CanonicalValue>) : this(entries.toList())

        fun value(name: String): CanonicalValue? = entries.firstOrNull { it.first == name }?.second

        fun keys(): Set<String> = entries.mapTo(linkedSetOf()) { it.first }
    }

    data class ArrayValue(val values: List<CanonicalValue>) : CanonicalValue

    data class StringValue(val value: String) : CanonicalValue

    data class IntegerValue(val value: Long) : CanonicalValue

    data class BooleanValue(val value: Boolean) : CanonicalValue

    data object NullValue : CanonicalValue
}

fun canonicalObject(vararg entries: Pair<String, CanonicalValue>): CanonicalValue.ObjectValue =
    CanonicalValue.ObjectValue(*entries)

fun canonicalString(value: String): CanonicalValue = CanonicalValue.StringValue(value)

fun canonicalInteger(value: Long): CanonicalValue = CanonicalValue.IntegerValue(value)

enum class PathParameterType {
    OPAQUE_ID,
    POSITIVE_INTEGER,
}

sealed interface PathValue {
    fun canonicalValue(): CanonicalValue

    fun routeValue(): String

    data class OpaqueId(val value: String) : PathValue {
        init {
            require(value.isNotEmpty()) { "Opaque path identifier must not be empty" }
        }

        override fun canonicalValue(): CanonicalValue = CanonicalValue.StringValue(value)

        override fun routeValue(): String = value
    }

    data class PositiveInteger(val value: Long) : PathValue {
        init {
            require(value > 0) { "Positive path integer must be greater than zero" }
        }

        override fun canonicalValue(): CanonicalValue = CanonicalValue.IntegerValue(value)

        override fun routeValue(): String = value.toString()
    }
}

data class PathParameter(val name: String, val value: PathValue)

data class PathParameterDefinition(val name: String, val type: PathParameterType)

enum class HttpMethod {
    GET,
    POST,
    PUT,
    DELETE,
}

data class OperationDefinition(
    val id: OperationId,
    val operationClass: String,
    val method: HttpMethod,
    val routeTemplate: String,
    val pathParameterSchema: List<PathParameterDefinition>,
    val bodyTargetFields: List<String>,
    val pathBodyEqualityFields: List<String>,
    val requestSchema: String,
    val responseSchema: String,
    val idempotencyPolicy: String,
    val successStatus: Int,
)

sealed interface BodyDescriptor {
    data object None : BodyDescriptor

    data class Json(val nonVolatileBody: CanonicalValue.ObjectValue) : BodyDescriptor

    data class Binary(
        val byteLength: Long,
        val sha256: Sha256Hex,
        val uploadId: String,
        val partNumber: Long,
        val planGeneration: Long,
    ) : BodyDescriptor {
        init {
            require(byteLength > 0) { "Binary body length must be positive" }
            require(uploadId.isNotEmpty()) { "uploadId must not be empty" }
            require(partNumber > 0) { "partNumber must be positive" }
            require(planGeneration > 0) { "planGeneration must be positive" }
        }
    }
}

data class PreparedRequest(
    val operation: OperationDefinition,
    val renderedRoute: String,
    val canonicalPathParameters: CanonicalValue.ObjectValue,
    val canonicalPathParametersDescriptor: String,
    val bodyDescriptor: String,
    val canonicalRequestDigest: Sha256Hex,
)

sealed interface RequestPreparation {
    data class Accepted(val request: PreparedRequest) : RequestPreparation

    data class Rejected(
        val errorCode: String = "SCHEMA_VALIDATION_FAILED",
        val rejectedBeforeIdempotency: Boolean = true,
        val effectDelta: EffectVector = EffectVector.ZERO,
    ) : RequestPreparation
}

data class EffectVector(
    val serverStateTransitions: Int = 0,
    val resources: Int = 0,
    val receipts: Int = 0,
    val economicEffects: Int = 0,
    val deletionRecords: Int = 0,
) {
    init {
        require(
            serverStateTransitions >= 0 &&
                resources >= 0 &&
                receipts >= 0 &&
                economicEffects >= 0 &&
                deletionRecords >= 0
        ) {
            "Effect deltas must be non-negative"
        }
    }

    operator fun plus(other: EffectVector): EffectVector =
        EffectVector(
            serverStateTransitions + other.serverStateTransitions,
            resources + other.resources,
            receipts + other.receipts,
            economicEffects + other.economicEffects,
            deletionRecords + other.deletionRecords,
        )

    companion object {
        val ZERO = EffectVector()
    }
}

data class CanonicalOutcome(
    val applicationStatus: Int,
    val responseDigest: Sha256Hex,
    val resourceIds: List<String>,
    val committedEffects: EffectVector,
)

data class IdempotencyScope(
    val syntheticTenantId: String,
    val profileBindingSha256: Sha256Hex,
    val operationClass: String,
    val idempotencyKey: IdempotencyKey,
)

data class IdempotencyEvidence(
    val syntheticTenantId: String,
    val profileBindingSha256: Sha256Hex,
    val operationClass: String,
    val idempotencyKeyDigest: Sha256Hex,
    val canonicalRequestDigest: Sha256Hex,
    val replayed: Boolean,
)

data class IdempotencyEntry(
    val canonicalRequestDigest: Sha256Hex,
    val canonicalOutcome: CanonicalOutcome,
)

data class IdempotencyLedger(val entries: Map<IdempotencyScope, IdempotencyEntry> = emptyMap())

sealed interface IdempotencyDecision {
    val ledger: IdempotencyLedger
    val evidence: IdempotencyEvidence
    val effectDelta: EffectVector

    data class Committed(
        override val ledger: IdempotencyLedger,
        override val evidence: IdempotencyEvidence,
        val canonicalOutcome: CanonicalOutcome,
        override val effectDelta: EffectVector,
    ) : IdempotencyDecision

    data class Replayed(
        override val ledger: IdempotencyLedger,
        override val evidence: IdempotencyEvidence,
        val canonicalOutcome: CanonicalOutcome,
        override val effectDelta: EffectVector = EffectVector.ZERO,
    ) : IdempotencyDecision

    data class PayloadMismatch(
        override val ledger: IdempotencyLedger,
        override val evidence: IdempotencyEvidence,
        val applicationStatus: Int = 409,
        val errorCode: String = "IDEMPOTENCY_KEY_PAYLOAD_MISMATCH",
        override val effectDelta: EffectVector = EffectVector.ZERO,
    ) : IdempotencyDecision
}

enum class RetryClass {
    WAIT_NETWORK,
    BACKOFF_REPLAY,
    REFRESH_UPLOAD_PLAN,
    REPLAY_SAME_OPERATION,
    USER_ACTION_REQUIRED,
    FINAL_REJECT,
}

enum class ClientState {
    BLOCKED_NO_PROFILE,
    READY,
    CREATING,
    WAITING_UPLOAD,
    UPLOADING,
    WAITING_NETWORK,
    RETRY_SCHEDULED,
    COMPLETING,
    REMOTE_PROCESSING,
    RESULT_AVAILABLE,
    RESULT_VERIFIED,
    DELETE_PENDING,
    DELETED,
    CANCEL_PENDING,
    CANCELLED,
    FAILED_FINAL,
}

enum class ServerState {
    ABSENT,
    CREATED,
    WAITING_UPLOAD,
    UPLOADING,
    UPLOAD_COMPLETE,
    QUEUED,
    PROCESSING,
    RESULT_READY,
    DELIVERED,
    DELETE_PENDING,
    DELETED,
    CANCELLED,
}

sealed interface TransitionDestination {
    data class Fixed(val state: ClientState) : TransitionDestination

    data object PersistedResumeState : TransitionDestination

    data object SameState : TransitionDestination
}

data class ClientTransition(
    val id: String,
    val from: Set<ClientState>,
    val event: String,
    val destination: TransitionDestination,
    val resumeState: ClientState? = null,
    val retryClass: RetryClass? = null,
    val visibleSubstatusOnEnter: String? = null,
    val preserveDeletionRecord: Boolean = false,
)

data class PriorityGroup(val priority: Int, val name: String, val transitionIds: List<String>)

data class SelectedClientTransition(
    val transitionId: String,
    val priority: Int,
    val from: ClientState,
    val to: ClientState,
    val nextResumeState: ClientState?,
    val visibleSubstatus: String?,
    val preserveDeletionRecord: Boolean,
)

data class ServerTransition(
    val id: String,
    val from: Set<ServerState>,
    val event: String,
    val destination: String,
)

data class TraceDefinition(
    val id: String,
    val name: String,
    val operationIds: List<String> = emptyList(),
    val clientTransitionIds: List<String> = emptyList(),
    val serverTransitionIds: List<String> = emptyList(),
)

data class FaultDefinition(
    val id: String,
    val boundary: String,
    val retryClass: RetryClass,
    val proofClass: String,
    val clientTransitionIds: List<String>,
    val serverTransitionIds: List<String>,
    val traceIds: List<String>,
    val ruleTargets: List<String>,
)

data class FixturePart(
    val partNumber: Int,
    val byteLength: Int,
    val sha256: Sha256Hex,
    val declaredExpectedSha256: Sha256Hex? = null,
)

data class FixtureDefinition(
    val id: String,
    val byteLength: Int,
    val sha256: Sha256Hex,
    val parts: List<FixturePart>,
    val declaredExpectedSha256: Sha256Hex? = null,
)

data class DeletionRecord(
    val jobId: String,
    val conversationFixtureId: String,
    val deleteResourceBindingSha256: Sha256Hex,
    val deleteIdempotencyKeyLedgerRef: String,
    val deleteIdempotencyKeyDigest: Sha256Hex,
    val deleteRequestDigest: Sha256Hex,
    val deletionId: String,
    val profileBindingSha256: Sha256Hex,
    val endpointId: String,
    val regionCode: String,
    val lastReceiptRevision: Long,
) {
    init {
        require(lastReceiptRevision >= 0) { "Receipt revision must be non-negative" }
    }

    fun evidenceFields(): CanonicalValue.ObjectValue =
        canonicalObject(
            "jobId" to canonicalString(jobId),
            "conversationFixtureId" to canonicalString(conversationFixtureId),
            "deleteResourceBindingSha256" to canonicalString(deleteResourceBindingSha256.value),
            "deleteIdempotencyKeyLedgerRef" to canonicalString(deleteIdempotencyKeyLedgerRef),
            "deleteIdempotencyKeyDigest" to canonicalString(deleteIdempotencyKeyDigest.value),
            "deleteRequestDigest" to canonicalString(deleteRequestDigest.value),
            "deletionId" to canonicalString(deletionId),
            "profileBindingSha256" to canonicalString(profileBindingSha256.value),
            "endpointId" to canonicalString(endpointId),
            "regionCode" to canonicalString(regionCode),
            "lastReceiptRevision" to canonicalInteger(lastReceiptRevision),
        )
}

data class PendingDeletion(
    val record: DeletionRecord,
    val visibleSubstatus: String,
    val stableDeletionReceiptId: String? = null,
)

data class DeletionReceiptEvidence(
    val schemaVersion: String,
    val deletionId: String,
    val deletionReceiptId: String?,
    val state: ServerState,
    val verifiedAbsent: Boolean,
    val receiptRevision: Long,
)

sealed interface DeletionReceiptDecision {
    val effectDelta: EffectVector

    data class Pending(
        val deletion: PendingDeletion,
        override val effectDelta: EffectVector = EffectVector.ZERO,
    ) : DeletionReceiptDecision

    data class Deleted(
        val record: DeletionRecord,
        val receipt: DeletionReceiptEvidence,
        override val effectDelta: EffectVector = EffectVector.ZERO,
    ) : DeletionReceiptDecision

    data class RejectedNoStateChange(
        val deletion: PendingDeletion,
        val errorCode: String,
        override val effectDelta: EffectVector = EffectVector.ZERO,
    ) : DeletionReceiptDecision
}

internal fun ConsentProfileBinding.validityAt(
    now: Instant,
    endpointAllowlisted: Boolean,
    expectedBinding: EndpointRegionBinding,
): ProfileValidation =
    ContractOracle.validateProfile(this, now, endpointAllowlisted, expectedBinding)
