@file:Suppress("MagicNumber")

package com.monumentogram.dora.poc.vpn.contract

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.DateTimeException
import java.time.Instant

object DoraCanonicalJson {
    fun encode(value: CanonicalValue): String = buildString { appendValue(value) }

    fun parseStrict(input: String): CanonicalValue = Parser(input).parse()

    private fun StringBuilder.appendValue(value: CanonicalValue) {
        when (value) {
            is CanonicalValue.ObjectValue -> {
                append('{')
                value.entries
                    .sortedWith { left, right -> compareCodePoints(left.first, right.first) }
                    .forEachIndexed { index, entry ->
                        if (index > 0) append(',')
                        appendEscapedString(entry.first)
                        append(':')
                        appendValue(entry.second)
                    }
                append('}')
            }

            is CanonicalValue.ArrayValue -> {
                append('[')
                value.values.forEachIndexed { index, entry ->
                    if (index > 0) append(',')
                    appendValue(entry)
                }
                append(']')
            }

            is CanonicalValue.StringValue -> appendEscapedString(value.value)
            is CanonicalValue.IntegerValue -> append(value.value)
            is CanonicalValue.BooleanValue -> append(if (value.value) "true" else "false")
            CanonicalValue.NullValue -> append("null")
        }
    }

    private fun StringBuilder.appendEscapedString(value: String) {
        requireValidUnicode(value)
        append('"')
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            when (codePoint) {
                '"'.code -> append("\\\"")
                '\\'.code -> append("\\\\")
                '\b'.code -> append("\\b")
                '\u000c'.code -> append("\\f")
                '\n'.code -> append("\\n")
                '\r'.code -> append("\\r")
                '\t'.code -> append("\\t")
                in 0..0x1f -> append("\\u%04x".format(codePoint))
                else -> appendCodePoint(codePoint)
            }
            index += Character.charCount(codePoint)
        }
        append('"')
    }

    @Suppress("TooManyFunctions")
    private class Parser(private val input: String) {
        private var index = 0

        fun parse(): CanonicalValue {
            skipWhitespace()
            val value = parseValue()
            skipWhitespace()
            require(index == input.length) { "Trailing JSON input" }
            return value
        }

        private fun parseValue(): CanonicalValue {
            require(index < input.length) { "Unexpected end of JSON" }
            return when (input[index]) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> CanonicalValue.StringValue(parseString())
                't' -> parseLiteral("true", CanonicalValue.BooleanValue(true))
                'f' -> parseLiteral("false", CanonicalValue.BooleanValue(false))
                'n' -> parseLiteral("null", CanonicalValue.NullValue)
                '-',
                in '0'..'9' -> parseInteger()
                else -> error("Unexpected JSON token at $index")
            }
        }

        private fun parseObject(): CanonicalValue.ObjectValue {
            expect('{')
            skipWhitespace()
            val entries = mutableListOf<Pair<String, CanonicalValue>>()
            val keys = mutableSetOf<String>()
            if (consume('}')) return CanonicalValue.ObjectValue(entries)
            while (true) {
                require(peek() == '"') { "Object key must be a string" }
                val key = parseString()
                require(keys.add(key)) { "Duplicate JSON object key: $key" }
                skipWhitespace()
                expect(':')
                skipWhitespace()
                entries += key to parseValue()
                skipWhitespace()
                if (consume('}')) break
                expect(',')
                skipWhitespace()
            }
            return CanonicalValue.ObjectValue(entries)
        }

        private fun parseArray(): CanonicalValue.ArrayValue {
            expect('[')
            skipWhitespace()
            val values = mutableListOf<CanonicalValue>()
            if (consume(']')) return CanonicalValue.ArrayValue(values)
            while (true) {
                values += parseValue()
                skipWhitespace()
                if (consume(']')) break
                expect(',')
                skipWhitespace()
            }
            return CanonicalValue.ArrayValue(values)
        }

        private fun parseString(): String {
            expect('"')
            val result = StringBuilder()
            while (true) {
                require(index < input.length) { "Unterminated JSON string" }
                val char = input[index++]
                when {
                    char == '"' -> break
                    char == '\\' -> appendEscape(result)
                    char.code < 0x20 -> error("Unescaped JSON control character")
                    else -> result.append(char)
                }
            }
            return result.toString().also(::requireValidUnicode)
        }

        private fun appendEscape(result: StringBuilder) {
            require(index < input.length) { "Unterminated JSON escape" }
            when (val escaped = input[index++]) {
                '"',
                '\\',
                '/' -> result.append(escaped)
                'b' -> result.append('\b')
                'f' -> result.append('\u000c')
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                't' -> result.append('\t')
                'u' -> result.append(parseUnicodeEscape())
                else -> error("Invalid JSON escape: $escaped")
            }
        }

        private fun parseUnicodeEscape(): Char {
            require(index + 4 <= input.length) { "Truncated Unicode escape" }
            val digits = input.substring(index, index + 4)
            require(digits.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                "Invalid Unicode escape"
            }
            index += 4
            return digits.toInt(16).toChar()
        }

        private fun parseInteger(): CanonicalValue.IntegerValue {
            val start = index
            consume('-')
            require(index < input.length) { "Truncated JSON integer" }
            if (input[index] == '0') {
                index++
                require(index == input.length || !input[index].isDigit()) {
                    "JSON integer has a leading zero"
                }
            } else {
                require(input[index] in '1'..'9') { "Invalid JSON integer" }
                while (index < input.length && input[index].isDigit()) index++
            }
            require(index == input.length || input[index] !in listOf('.', 'e', 'E')) {
                "Floating point JSON values are forbidden"
            }
            val token = input.substring(start, index)
            require(token != "-0") { "Negative zero is not canonical" }
            return CanonicalValue.IntegerValue(
                token.toLongOrNull() ?: error("JSON integer is outside signed 64-bit range")
            )
        }

        private fun <T : CanonicalValue> parseLiteral(expected: String, value: T): T {
            require(input.startsWith(expected, index)) { "Invalid JSON literal" }
            index += expected.length
            return value
        }

        private fun skipWhitespace() {
            while (index < input.length && input[index] in listOf(' ', '\t', '\r', '\n')) index++
        }

        private fun expect(expected: Char) {
            require(consume(expected)) { "Expected '$expected' at $index" }
        }

        private fun consume(expected: Char): Boolean {
            if (index >= input.length || input[index] != expected) return false
            index++
            return true
        }

        private fun peek(): Char? = input.getOrNull(index)
    }

    private fun compareCodePoints(left: String, right: String): Int {
        requireValidUnicode(left)
        requireValidUnicode(right)
        var leftIndex = 0
        var rightIndex = 0
        while (leftIndex < left.length && rightIndex < right.length) {
            val leftCodePoint = left.codePointAt(leftIndex)
            val rightCodePoint = right.codePointAt(rightIndex)
            if (leftCodePoint != rightCodePoint) return leftCodePoint.compareTo(rightCodePoint)
            leftIndex += Character.charCount(leftCodePoint)
            rightIndex += Character.charCount(rightCodePoint)
        }
        return (left.length - leftIndex).compareTo(right.length - rightIndex)
    }

    private fun requireValidUnicode(value: String) {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            when {
                Character.isHighSurrogate(char) -> {
                    require(
                        index + 1 < value.length && Character.isLowSurrogate(value[index + 1])
                    ) {
                        "Unpaired high surrogate"
                    }
                    index += 2
                }

                Character.isLowSurrogate(char) -> error("Unpaired low surrogate")
                else -> index++
            }
        }
    }
}

@Suppress("TooManyFunctions")
object ContractOracle {
    private const val LF = "\n"
    private val placeholderPattern = Regex("\\{([^{}]+)}")
    private val sha256Pattern = Regex("[0-9a-f]{64}")

    fun sha256Hex(bytes: ByteArray): Sha256Hex =
        Sha256Hex(MessageDigest.getInstance("SHA-256").digest(bytes).toHex())

    fun sha256Utf8(value: String): Sha256Hex = sha256Hex(value.toByteArray(StandardCharsets.UTF_8))

    fun profileBindingDigest(profile: ConsentProfileBinding): Sha256Hex =
        sha256Utf8(
            DoraCanonicalJson.encode(
                canonicalObject(
                    "profileId" to canonicalString(profile.profileId),
                    "consentReceiptId" to canonicalString(profile.consentReceiptId),
                    "policyVersion" to canonicalString(profile.policyVersion),
                    "artifactClass" to canonicalString(profile.artifactClass.name),
                    "purpose" to canonicalString(profile.purpose.name),
                    "endpointId" to canonicalString(profile.endpointId),
                    "regionCode" to canonicalString(profile.regionCode),
                    "tenantFixtureId" to canonicalString(profile.tenantFixtureId),
                    "issuedAt" to canonicalString(profile.issuedAt),
                    "expiresAt" to canonicalString(profile.expiresAt),
                    "revokedAt" to
                        (profile.revokedAt?.let(::canonicalString) ?: CanonicalValue.NullValue),
                    "endpointAllowlistDigest" to
                        canonicalString(profile.endpointAllowlistDigest.value),
                )
            )
        )

    @Suppress("ReturnCount")
    fun validateProfile(
        profile: ConsentProfileBinding,
        now: Instant,
        endpointAllowlisted: Boolean,
        expectedBinding: EndpointRegionBinding,
    ): ProfileValidation {
        val issuedAt: Instant
        val expiresAt: Instant
        try {
            issuedAt = Instant.parse(profile.issuedAt)
            expiresAt = Instant.parse(profile.expiresAt)
        } catch (_: DateTimeException) {
            return ProfileValidation.Rejected("MALFORMED_PROFILE_TIME")
        }
        if (profileBindingDigest(profile) != profile.profileBindingSha256) {
            return ProfileValidation.Rejected("PROFILE_BINDING_DIGEST_MISMATCH")
        }
        if (profile.revokedAt != null) return ProfileValidation.Rejected("PROFILE_REVOKED")
        if (issuedAt >= expiresAt || now < issuedAt || now >= expiresAt) {
            return ProfileValidation.Rejected("PROFILE_OUTSIDE_VALIDITY_WINDOW")
        }
        if (!endpointAllowlisted) return ProfileValidation.Rejected("ENDPOINT_NOT_ALLOWLISTED")
        if (profile.endpointRegionBinding != expectedBinding) {
            return ProfileValidation.Rejected("ENDPOINT_OR_REGION_MISMATCH")
        }
        return ProfileValidation.Valid
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    fun prepareRequest(
        operation: OperationDefinition,
        pathParameters: List<PathParameter>,
        body: BodyDescriptor,
        profileBindingSha256: Sha256Hex,
        resolvedRoute: String? = null,
    ): RequestPreparation {
        val placeholders =
            placeholderPattern.findAll(operation.routeTemplate).map { it.groupValues[1] }.toList()
        if (placeholders.size != placeholders.toSet().size) return RequestPreparation.Rejected()
        if (
            '{' in placeholderPattern.replace(operation.routeTemplate, "") ||
                '}' in placeholderPattern.replace(operation.routeTemplate, "")
        ) {
            return RequestPreparation.Rejected()
        }
        val schemaNames = operation.pathParameterSchema.map { it.name }
        val providedNames = pathParameters.map { it.name }
        if (
            schemaNames.size != schemaNames.toSet().size ||
                providedNames.size != providedNames.toSet().size
        ) {
            return RequestPreparation.Rejected()
        }
        if (
            placeholders.toSet() != schemaNames.toSet() ||
                schemaNames.toSet() != providedNames.toSet()
        ) {
            return RequestPreparation.Rejected()
        }
        val pathByName = pathParameters.associateBy { it.name }
        val schemaValid =
            operation.pathParameterSchema.all { definition ->
                when (pathByName.getValue(definition.name).value) {
                    is PathValue.OpaqueId -> definition.type == PathParameterType.OPAQUE_ID
                    is PathValue.PositiveInteger ->
                        definition.type == PathParameterType.POSITIVE_INTEGER
                }
            }
        if (!schemaValid) return RequestPreparation.Rejected()

        val bodyValue =
            bodyCanonicalValue(operation, body, profileBindingSha256)
                ?: return RequestPreparation.Rejected()
        if (!bodyTargetsPresent(operation, bodyValue)) return RequestPreparation.Rejected()
        if (!pathBodyBindingsMatch(operation, pathByName, bodyValue)) {
            return RequestPreparation.Rejected()
        }

        val pathObject =
            CanonicalValue.ObjectValue(pathParameters.map { it.name to it.value.canonicalValue() })
        val renderedRoute = renderRoute(operation.routeTemplate, pathByName)
        if (resolvedRoute != null && resolvedRoute != renderedRoute)
            return RequestPreparation.Rejected()
        val canonicalPathDescriptor = DoraCanonicalJson.encode(pathObject)
        val bodyDescriptor =
            if (body == BodyDescriptor.None) "NONE" else DoraCanonicalJson.encode(bodyValue)
        val digestMaterial =
            listOf(
                    ContractCatalog.CONTRACT_ID,
                    operation.method.name,
                    operation.routeTemplate,
                    canonicalPathDescriptor,
                    operation.operationClass,
                    profileBindingSha256.value,
                    bodyDescriptor,
                )
                .joinToString(LF)
        return RequestPreparation.Accepted(
            PreparedRequest(
                operation = operation,
                renderedRoute = renderedRoute,
                canonicalPathParameters = pathObject,
                canonicalPathParametersDescriptor = canonicalPathDescriptor,
                bodyDescriptor = bodyDescriptor,
                canonicalRequestDigest = sha256Utf8(digestMaterial),
            )
        )
    }

    private fun bodyCanonicalValue(
        operation: OperationDefinition,
        body: BodyDescriptor,
        profileBindingSha256: Sha256Hex,
    ): CanonicalValue.ObjectValue? =
        when (body) {
            BodyDescriptor.None ->
                if (operation.requestSchema == "NONE") CanonicalValue.ObjectValue(emptyList())
                else null
            is BodyDescriptor.Json ->
                body.nonVolatileBody.takeIf {
                    jsonRequestSchemaValid(operation.requestSchema, it, profileBindingSha256)
                }
            is BodyDescriptor.Binary ->
                if (operation.operationClass == "UPLOAD_PART") {
                    canonicalObject(
                        "byteLength" to canonicalInteger(body.byteLength),
                        "sha256" to canonicalString(body.sha256.value),
                        "uploadId" to canonicalString(body.uploadId),
                        "partNumber" to canonicalInteger(body.partNumber),
                        "planGeneration" to canonicalInteger(body.planGeneration),
                    )
                } else {
                    null
                }
        }

    private fun jsonRequestSchemaValid(
        requestSchema: String,
        body: CanonicalValue.ObjectValue,
        profileBindingSha256: Sha256Hex,
    ): Boolean =
        when (requestSchema) {
            "CreateJobRequest-v0.1" -> createJobRequestValid(body, profileBindingSha256)
            "UploadPlanRequest-v0.1" -> uploadPlanRequestValid(body)
            "CompleteUploadRequest-v0.1" -> completeUploadRequestValid(body)
            else -> false
        }

    @Suppress("ReturnCount")
    private fun createJobRequestValid(
        body: CanonicalValue.ObjectValue,
        profileBindingSha256: Sha256Hex,
    ): Boolean {
        if (
            !body.hasExactKeys(
                "schemaVersion",
                "profileBindingSha256",
                "syntheticTenantId",
                "fixtureId",
                "artifactClass",
                "purpose",
                "payloadByteLength",
                "payloadSha256",
            )
        ) {
            return false
        }
        val fixtureId = body.stringValue("fixtureId") ?: return false
        val fixture = ContractCatalog.fixtures.singleOrNull { it.id == fixtureId } ?: return false
        return body.isString("schemaVersion") &&
            body.stringValue("profileBindingSha256") == profileBindingSha256.value &&
            body.isString("syntheticTenantId") &&
            body.stringValue("artifactClass") == ArtifactClass.SYNTHETIC_BYTES.name &&
            body.stringValue("purpose") == ConsentPurpose.STAGE0_CONTRACT_TEST.name &&
            body.integerValue("payloadByteLength") == fixture.byteLength.toLong() &&
            body.stringValue("payloadSha256") == fixture.sha256.value
    }

    private fun uploadPlanRequestValid(body: CanonicalValue.ObjectValue): Boolean {
        if (
            !body.hasExactKeys(
                "schemaVersion",
                "jobId",
                "priorUploadId",
                "requestedPlanGeneration",
                "partSizeBytes",
                "totalByteLength",
                "totalSha256",
            )
        ) {
            return false
        }
        val priorUploadId = body.value("priorUploadId")
        return body.isString("schemaVersion") &&
            body.isOpaqueId("jobId") &&
            (priorUploadId == CanonicalValue.NullValue ||
                (priorUploadId is CanonicalValue.StringValue &&
                    priorUploadId.value.isNotEmpty())) &&
            body.isPositiveInteger("requestedPlanGeneration") &&
            body.integerValue("partSizeBytes") == ContractCatalog.PART_SIZE_BYTES.toLong() &&
            body.isPositiveInteger("totalByteLength") &&
            body.isSha256("totalSha256")
    }

    @Suppress("ReturnCount")
    private fun completeUploadRequestValid(body: CanonicalValue.ObjectValue): Boolean {
        if (
            !body.hasExactKeys(
                "schemaVersion",
                "jobId",
                "uploadId",
                "manifest",
                "totalByteLength",
                "totalSha256",
            )
        ) {
            return false
        }
        val manifest =
            (body.value("manifest") as? CanonicalValue.ArrayValue)?.values ?: return false
        val manifestEntries = manifest.map { it as? CanonicalValue.ObjectValue ?: return false }
        val declaredPartNumbers = manifestEntries.map { it.integerValue("partNumber") }
        val declaredByteLength = manifestEntries.sumOf { it.integerValue("byteLength") ?: 0 }
        return body.isString("schemaVersion") &&
            body.isOpaqueId("jobId") &&
            body.isOpaqueId("uploadId") &&
            manifestEntries.isNotEmpty() &&
            manifestEntries.all(::manifestEntryValid) &&
            declaredPartNumbers == (1L..manifestEntries.size.toLong()).toList() &&
            manifestPartSizesValid(manifestEntries) &&
            body.isPositiveInteger("totalByteLength") &&
            body.integerValue("totalByteLength") == declaredByteLength &&
            body.isSha256("totalSha256")
    }

    private fun manifestPartSizesValid(entries: List<CanonicalValue.ObjectValue>): Boolean {
        val partSizeBytes = ContractCatalog.PART_SIZE_BYTES.toLong()
        return entries.isNotEmpty() &&
            entries.dropLast(1).all { it.integerValue("byteLength") == partSizeBytes } &&
            entries.last().integerValue("byteLength")?.let { it in 1L..partSizeBytes } == true
    }

    private fun manifestEntryValid(value: CanonicalValue): Boolean {
        val entry = value as? CanonicalValue.ObjectValue ?: return false
        return entry.hasExactKeys("partNumber", "byteLength", "sha256", "partReceiptId") &&
            entry.isPositiveInteger("partNumber") &&
            entry.isPositiveInteger("byteLength") &&
            entry.isSha256("sha256") &&
            entry.isOpaqueId("partReceiptId")
    }

    private fun bodyTargetsPresent(
        operation: OperationDefinition,
        body: CanonicalValue.ObjectValue,
    ): Boolean =
        operation.bodyTargetFields.all { field ->
            if ("[]." !in field) {
                body.value(field) != null
            } else {
                val (arrayName, nestedName) = field.split("[].", limit = 2)
                val values = (body.value(arrayName) as? CanonicalValue.ArrayValue)?.values
                !values.isNullOrEmpty() &&
                    values.all { (it as? CanonicalValue.ObjectValue)?.value(nestedName) != null }
            }
        }

    private fun CanonicalValue.ObjectValue.hasExactKeys(vararg expected: String): Boolean =
        keys() == expected.toSet()

    private fun CanonicalValue.ObjectValue.isString(name: String): Boolean =
        value(name) is CanonicalValue.StringValue

    private fun CanonicalValue.ObjectValue.stringValue(name: String): String? =
        (value(name) as? CanonicalValue.StringValue)?.value

    private fun CanonicalValue.ObjectValue.integerValue(name: String): Long? =
        (value(name) as? CanonicalValue.IntegerValue)?.value

    private fun CanonicalValue.ObjectValue.isOpaqueId(name: String): Boolean =
        stringValue(name)?.isNotEmpty() == true

    private fun CanonicalValue.ObjectValue.isPositiveInteger(name: String): Boolean =
        (integerValue(name) ?: 0) > 0

    private fun CanonicalValue.ObjectValue.isSha256(name: String): Boolean =
        stringValue(name)?.matches(sha256Pattern) == true

    private fun pathBodyBindingsMatch(
        operation: OperationDefinition,
        pathByName: Map<String, PathParameter>,
        body: CanonicalValue.ObjectValue,
    ): Boolean =
        operation.pathBodyEqualityFields.all { field ->
            val pathValue = pathByName[field]?.value?.canonicalValue() ?: return@all false
            body.value(field) == pathValue
        }

    private fun renderRoute(
        template: String,
        pathByName: Map<String, PathParameter>,
    ): String =
        placeholderPattern.replace(template) { match ->
            val value = pathByName.getValue(match.groupValues[1]).value.routeValue()
            percentEncodePathSegment(value)
        }

    @Suppress("ComplexCondition")
    private fun percentEncodePathSegment(value: String): String {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        return buildString {
            bytes.forEach { byte ->
                val unsigned = byte.toInt() and 0xff
                if (
                    (unsigned in 'a'.code..'z'.code) ||
                        (unsigned in 'A'.code..'Z'.code) ||
                        (unsigned in '0'.code..'9'.code) ||
                        unsigned in listOf('-'.code, '.'.code, '_'.code, '~'.code)
                ) {
                    append(unsigned.toChar())
                } else {
                    append('%')
                    append(HEX[unsigned ushr 4])
                    append(HEX[unsigned and 0x0f])
                }
            }
        }
    }

    @Suppress("ReturnCount")
    fun applyIdempotency(
        ledger: IdempotencyLedger,
        scope: IdempotencyScope,
        canonicalRequestDigest: Sha256Hex,
        proposedOutcome: CanonicalOutcome,
    ): IdempotencyDecision {
        val existing = ledger.entries[scope]
        val keyDigest = sha256Utf8(scope.idempotencyKey.value)
        fun evidence(replayed: Boolean) =
            IdempotencyEvidence(
                scope.syntheticTenantId,
                scope.profileBindingSha256,
                scope.operationClass,
                keyDigest,
                canonicalRequestDigest,
                replayed,
            )
        if (existing == null) {
            val updated =
                IdempotencyLedger(
                    ledger.entries +
                        (scope to IdempotencyEntry(canonicalRequestDigest, proposedOutcome))
                )
            return IdempotencyDecision.Committed(
                updated,
                evidence(replayed = false),
                proposedOutcome,
                proposedOutcome.committedEffects,
            )
        }
        if (existing.canonicalRequestDigest == canonicalRequestDigest) {
            return IdempotencyDecision.Replayed(
                ledger,
                evidence(replayed = true),
                existing.canonicalOutcome,
            )
        }
        return IdempotencyDecision.PayloadMismatch(ledger, evidence(replayed = false))
    }

    fun deleteResourceBindingDigest(
        syntheticTenantId: String,
        profileBindingSha256: Sha256Hex,
        endpointRegionBinding: EndpointRegionBinding,
        conversationFixtureId: String,
    ): Sha256Hex =
        sha256Utf8(
            listOf(
                    ContractCatalog.CONTRACT_ID,
                    "DELETE_CLOUD_COPY",
                    syntheticTenantId,
                    profileBindingSha256.value,
                    endpointRegionBinding.endpointId,
                    endpointRegionBinding.regionCode,
                    conversationFixtureId,
                )
                .joinToString(LF)
        )

    fun selectClientTransition(
        currentState: ClientState,
        guardSatisfiedTransitionIds: Set<String>,
        persistedResumeState: ClientState? = null,
        currentVisibleSubstatus: String? = null,
    ): SelectedClientTransition? {
        val order = ContractCatalog.clientTransitionOrder
        val selected =
            ContractCatalog.clientTransitions
                .asSequence()
                .filter { it.id in guardSatisfiedTransitionIds && currentState in it.from }
                .minByOrNull { order.getValue(it.id) } ?: return null
        val priority = ContractCatalog.clientTransitionPriority.getValue(selected.id)
        val destination =
            when (val target = selected.destination) {
                is TransitionDestination.Fixed -> target.state
                TransitionDestination.SameState -> currentState
                TransitionDestination.PersistedResumeState ->
                    requireNotNull(persistedResumeState) { "Persisted resume state is required" }
            }
        val visibleSubstatus =
            when (selected.visibleSubstatusOnEnter) {
                "\$sameVisibleSubstatus" -> currentVisibleSubstatus
                else -> selected.visibleSubstatusOnEnter
            }
        return SelectedClientTransition(
            selected.id,
            priority,
            currentState,
            destination,
            selected.resumeState,
            visibleSubstatus,
            selected.preserveDeletionRecord,
        )
    }

    @Suppress("ComplexCondition", "ReturnCount")
    fun applyDeletionReceipt(
        deletion: PendingDeletion,
        receipt: DeletionReceiptEvidence,
    ): DeletionReceiptDecision {
        val receiptId = receipt.deletionReceiptId
        if (
            deletion.record.deletionId != receipt.deletionId ||
                receipt.deletionId.isEmpty() ||
                receiptId?.isEmpty() == true ||
                receipt.receiptRevision < deletion.record.lastReceiptRevision ||
                (deletion.stableDeletionReceiptId != null &&
                    receiptId != null &&
                    deletion.stableDeletionReceiptId != receiptId)
        ) {
            return DeletionReceiptDecision.RejectedNoStateChange(
                deletion,
                "DELETE_RESPONSE_INTEGRITY_REJECTED",
            )
        }
        val revised = deletion.record.copy(lastReceiptRevision = receipt.receiptRevision)
        if (receipt.state == ServerState.DELETE_PENDING && !receipt.verifiedAbsent) {
            return DeletionReceiptDecision.Pending(
                PendingDeletion(
                    revised,
                    ContractCatalog.DELETE_RECEIPT_POLL_ELIGIBLE,
                    deletion.stableDeletionReceiptId ?: receiptId,
                )
            )
        }
        if (receipt.state == ServerState.DELETED && receipt.verifiedAbsent && receiptId != null) {
            return DeletionReceiptDecision.Deleted(revised, receipt)
        }
        return DeletionReceiptDecision.RejectedNoStateChange(
            deletion,
            "DELETE_RESPONSE_INTEGRITY_REJECTED",
        )
    }

    fun generateFixtureBytes(fixtureId: String, byteLength: Int): ByteArray {
        require(byteLength >= 0) { "Fixture byte length must be non-negative" }
        val output = ByteArray(byteLength)
        var offset = 0
        var blockIndex = 0
        while (offset < output.size) {
            val material =
                "$fixturePrefix$fixtureId|block=${blockIndex.toString().padStart(8, '0')}"
            val block =
                MessageDigest.getInstance("SHA-256")
                    .digest(material.toByteArray(StandardCharsets.UTF_8))
            val count = minOf(block.size, output.size - offset)
            block.copyInto(output, offset, 0, count)
            offset += count
            blockIndex++
        }
        return output
    }

    fun materializeFixture(fixture: FixtureDefinition): ByteArray {
        if (fixture.id != "VPN-FIX-007") return generateFixtureBytes(fixture.id, fixture.byteLength)
        val bytes = generateFixtureBytes("VPN-FIX-006", fixture.byteLength)
        val corruptionOffset = ContractCatalog.PART_SIZE_BYTES
        bytes[corruptionOffset] = (bytes[corruptionOffset].toInt() xor 0x01).toByte()
        return bytes
    }

    fun fixtureParts(
        bytes: ByteArray,
        partSizeBytes: Int = ContractCatalog.PART_SIZE_BYTES,
    ): List<ByteArray> {
        require(partSizeBytes > 0) { "Part size must be positive" }
        if (bytes.isEmpty()) return emptyList()
        return bytes.asList().chunked(partSizeBytes).map { chunk -> chunk.toByteArray() }
    }

    private val fixturePrefix = "${ContractCatalog.CONTRACT_ID}|"
    private const val HEX = "0123456789ABCDEF"
}

private fun ByteArray.toHex(): String =
    joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }

fun CanonicalValue.ObjectValue.required(name: String): CanonicalValue =
    value(name) ?: error("Missing JSON field: $name")

fun CanonicalValue.ObjectValue.requiredObject(name: String): CanonicalValue.ObjectValue =
    required(name) as? CanonicalValue.ObjectValue ?: error("Expected object field: $name")

fun CanonicalValue.ObjectValue.requiredArray(name: String): List<CanonicalValue> =
    (required(name) as? CanonicalValue.ArrayValue)?.values ?: error("Expected array field: $name")

fun CanonicalValue.ObjectValue.requiredString(name: String): String =
    (required(name) as? CanonicalValue.StringValue)?.value ?: error("Expected string field: $name")

fun CanonicalValue.ObjectValue.requiredInteger(name: String): Long =
    (required(name) as? CanonicalValue.IntegerValue)?.value
        ?: error("Expected integer field: $name")
