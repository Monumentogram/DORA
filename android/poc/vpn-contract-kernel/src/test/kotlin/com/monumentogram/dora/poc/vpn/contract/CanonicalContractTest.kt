@file:Suppress("LongMethod")

package com.monumentogram.dora.poc.vpn.contract

import java.time.Instant

internal object CanonicalContractTest {
    fun run() {
        canonicalEncodingIsDeterministic()
        strictParserRejectsNonContractJson()
        routeAndBodyBindingFailClosed()
        requestSchemasFailClosed()
        trace006DigestsMatch()
        consentProfileUsesInjectedTimeAndFrozenBinding()
    }

    private fun canonicalEncodingIsDeterministic() {
        val privateUse = "\uE000"
        val supplementary = "\uD800\uDC00"
        val value =
            canonicalObject(
                supplementary to canonicalInteger(2),
                "z" to
                    CanonicalValue.ArrayValue(
                        listOf(CanonicalValue.BooleanValue(true), CanonicalValue.NullValue)
                    ),
                privateUse to canonicalInteger(1),
                "escaped" to canonicalString("line\n\"\\"),
            )
        checkEquals(
            "{\"escaped\":\"line\\n\\\"\\\\\",\"z\":[true,null],\"$privateUse\":1,\"$supplementary\":2}",
            DoraCanonicalJson.encode(value),
            "DORA-CJ-v1 encoding",
        )
        val reordered = CanonicalValue.ObjectValue(value.entries.reversed())
        checkEquals(
            DoraCanonicalJson.encode(value),
            DoraCanonicalJson.encode(reordered),
            "object order",
        )
    }

    private fun strictParserRejectsNonContractJson() {
        val parsed = DoraCanonicalJson.parseStrict(" { \"b\" : 2, \"a\" : [true, null] } ")
        checkEquals("{\"a\":[true,null],\"b\":2}", DoraCanonicalJson.encode(parsed), "strict parse")
        checkFails("duplicate key") { DoraCanonicalJson.parseStrict("{\"a\":1,\"a\":2}") }
        checkFails("floating point") { DoraCanonicalJson.parseStrict("{\"a\":1.0}") }
        checkFails("leading zero") { DoraCanonicalJson.parseStrict("{\"a\":01}") }
        checkFails("negative zero") { DoraCanonicalJson.parseStrict("-0") }
        checkFails("trailing input") { DoraCanonicalJson.parseStrict("{}{}") }
        checkFails("unpaired surrogate") {
            DoraCanonicalJson.encode(canonicalString("\uD800"))
        }
    }

    private fun routeAndBodyBindingFailClosed() {
        val uploadPart = ContractCatalog.operationsByClass.getValue("UPLOAD_PART")
        val binary =
            BodyDescriptor.Binary(
                3,
                ContractOracle.sha256Utf8("syn"),
                "upload synthetic/a",
                2,
                4,
            )
        val path =
            listOf(
                PathParameter("uploadId", PathValue.OpaqueId("upload synthetic/a")),
                PathParameter("partNumber", PathValue.PositiveInteger(2)),
                PathParameter("planGeneration", PathValue.PositiveInteger(4)),
            )
        val accepted = accepted(uploadPart, path, binary, PROFILE_DIGEST)
        checkEquals(
            "/synthetic-upload/upload%20synthetic%2Fa/4/2",
            accepted.renderedRoute,
            "route rendering",
        )
        checkEquals(
            "{\"partNumber\":2,\"planGeneration\":4,\"uploadId\":\"upload synthetic/a\"}",
            accepted.canonicalPathParametersDescriptor,
            "typed path map",
        )
        val reordered = accepted(uploadPart, path.reversed(), binary, PROFILE_DIGEST)
        checkEquals(
            accepted.canonicalRequestDigest,
            reordered.canonicalRequestDigest,
            "path input order",
        )

        assertPreLedgerReject(uploadPart, path.dropLast(1), binary)
        assertPreLedgerReject(
            uploadPart,
            path + PathParameter("extra", PathValue.OpaqueId("synthetic")),
            binary,
        )
        assertPreLedgerReject(uploadPart, path + path.first(), binary)
        assertPreLedgerReject(
            uploadPart,
            path.map {
                if (it.name == "partNumber") PathParameter(it.name, PathValue.OpaqueId("2")) else it
            },
            binary,
        )
        assertPreLedgerReject(
            uploadPart,
            path,
            binary.copy(uploadId = "upload-synthetic-b"),
        )
        val routeMismatch =
            ContractOracle.prepareRequest(
                uploadPart,
                path,
                binary,
                PROFILE_DIGEST,
                "/synthetic-upload/another/4/2",
            )
        checkTrue(routeMismatch is RequestPreparation.Rejected, "resolved route mismatch")

        val uploadPlan = ContractCatalog.operationsByClass.getValue("INIT_OR_REFRESH_UPLOAD")
        val bodyMismatch = BodyDescriptor.Json(uploadPlanBody("job-synthetic-b"))
        assertPreLedgerReject(
            uploadPlan,
            listOf(PathParameter("jobId", PathValue.OpaqueId("job-synthetic-a"))),
            bodyMismatch,
        )
        val noBodyOperation = ContractCatalog.operationsByClass.getValue("POLL_JOB")
        assertPreLedgerReject(
            noBodyOperation,
            listOf(PathParameter("jobId", PathValue.OpaqueId("job-synthetic-a"))),
            bodyMismatch,
        )
    }

    private fun requestSchemasFailClosed() {
        createJobSchemaCases()
        uploadPlanSchemaCases()
        completeUploadSchemaCases()
        uploadPartSchemaCases()
    }

    private fun createJobSchemaCases() {
        val operation = ContractCatalog.operationsByClass.getValue("CREATE_JOB")
        val valid = createJobBody()
        accepted(operation, emptyList(), BodyDescriptor.Json(valid), PROFILE_DIGEST)
        assertPreLedgerReject(
            operation,
            emptyList(),
            BodyDescriptor.Json(valid.without("fixtureId")),
        )
        assertPreLedgerReject(
            operation,
            emptyList(),
            BodyDescriptor.Json(valid.with("extra", canonicalString("synthetic"))),
        )
        assertPreLedgerReject(
            operation,
            emptyList(),
            BodyDescriptor.Json(valid.with("payloadByteLength", canonicalString("1"))),
        )
        assertPreLedgerReject(
            operation,
            emptyList(),
            BodyDescriptor.Json(valid.with("artifactClass", canonicalString("OTHER"))),
        )
        assertPreLedgerReject(
            operation,
            emptyList(),
            BodyDescriptor.Json(valid.with("purpose", canonicalString("OTHER"))),
        )
        assertPreLedgerReject(
            operation,
            emptyList(),
            BodyDescriptor.Json(valid.with("payloadSha256", canonicalString("f".repeat(63)))),
        )
        assertPreLedgerReject(
            operation,
            emptyList(),
            BodyDescriptor.Json(valid.with("fixtureId", canonicalString("VPN-FIX-999"))),
        )
        assertPreLedgerReject(
            operation,
            emptyList(),
            BodyDescriptor.Json(
                valid.with("profileBindingSha256", canonicalString("b".repeat(64)))
            ),
        )
    }

    private fun uploadPlanSchemaCases() {
        val operation = ContractCatalog.operationsByClass.getValue("INIT_OR_REFRESH_UPLOAD")
        val path = listOf(PathParameter("jobId", PathValue.OpaqueId("job-synthetic-a")))
        val valid = uploadPlanBody("job-synthetic-a")
        accepted(operation, path, BodyDescriptor.Json(valid), PROFILE_DIGEST)
        assertPreLedgerReject(operation, path, BodyDescriptor.Json(valid.without("priorUploadId")))
        assertPreLedgerReject(
            operation,
            path,
            BodyDescriptor.Json(valid.with("extra", canonicalString("synthetic"))),
        )
        assertPreLedgerReject(
            operation,
            path,
            BodyDescriptor.Json(valid.with("priorUploadId", CanonicalValue.BooleanValue(false))),
        )
        assertPreLedgerReject(
            operation,
            path,
            BodyDescriptor.Json(valid.with("requestedPlanGeneration", canonicalInteger(0))),
        )
        assertPreLedgerReject(
            operation,
            path,
            BodyDescriptor.Json(
                valid.with(
                    "partSizeBytes",
                    canonicalInteger(ContractCatalog.PART_SIZE_BYTES.toLong() * 2),
                )
            ),
        )
        assertPreLedgerReject(
            operation,
            path,
            BodyDescriptor.Json(valid.with("totalSha256", canonicalString("not-a-sha"))),
        )
    }

    private fun completeUploadSchemaCases() {
        val operation = ContractCatalog.operationsByClass.getValue("COMPLETE_UPLOAD")
        val path = listOf(PathParameter("jobId", PathValue.OpaqueId("job-synthetic-a")))
        val validEntry = manifestEntry()
        val valid = completeUploadBody(validEntry)
        accepted(operation, path, BodyDescriptor.Json(valid), PROFILE_DIGEST)

        val fullPart = manifestEntry(1, 1024, "part-receipt-synthetic-1")
        val lastFullPart = manifestEntry(2, 1024, "part-receipt-synthetic-2")
        val lastSingleByte = manifestEntry(2, 1, "part-receipt-synthetic-2")
        listOf(
                valid.withManifest(listOf(fullPart, lastFullPart), 2048),
                valid.withManifest(listOf(fullPart, lastSingleByte), 1025),
            )
            .forEach { multipart ->
                accepted(operation, path, BodyDescriptor.Json(multipart), PROFILE_DIGEST)
            }

        assertPreLedgerReject(operation, path, BodyDescriptor.Json(valid.without("uploadId")))
        assertPreLedgerReject(
            operation,
            path,
            BodyDescriptor.Json(valid.with("extra", canonicalString("synthetic"))),
        )
        assertPreLedgerReject(
            operation,
            path,
            BodyDescriptor.Json(valid.with("manifest", canonicalString("invalid"))),
        )
        assertPreLedgerReject(
            operation,
            path,
            BodyDescriptor.Json(valid.with("manifest", CanonicalValue.ArrayValue(emptyList()))),
        )
        listOf(
                validEntry.without("partReceiptId"),
                validEntry.with("extra", canonicalString("synthetic")),
                validEntry.with("partNumber", canonicalString("1")),
                validEntry.with("sha256", canonicalString("bad-sha")),
                validEntry.with("partReceiptId", CanonicalValue.NullValue),
            )
            .forEach { invalidEntry ->
                assertPreLedgerReject(
                    operation,
                    path,
                    BodyDescriptor.Json(
                        valid.with(
                            "manifest",
                            CanonicalValue.ArrayValue(listOf(invalidEntry)),
                        )
                    ),
                )
            }
        assertPreLedgerReject(
            operation,
            path,
            BodyDescriptor.Json(valid.with("totalByteLength", canonicalInteger(0))),
        )
        assertPreLedgerReject(
            operation,
            path,
            BodyDescriptor.Json(valid.with("totalByteLength", canonicalInteger(2))),
        )
        assertPreLedgerReject(
            operation,
            path,
            BodyDescriptor.Json(
                valid
                    .with(
                        "manifest",
                        CanonicalValue.ArrayValue(
                            listOf(
                                fullPart.with("partNumber", canonicalInteger(2)),
                                lastSingleByte.with("partNumber", canonicalInteger(1)),
                            )
                        ),
                    )
                    .with("totalByteLength", canonicalInteger(1025))
            ),
        )
        assertPreLedgerReject(
            operation,
            path,
            BodyDescriptor.Json(
                valid
                    .with(
                        "manifest",
                        CanonicalValue.ArrayValue(
                            listOf(
                                fullPart.with("partNumber", canonicalInteger(2)),
                                lastSingleByte,
                            )
                        ),
                    )
                    .with("totalByteLength", canonicalInteger(1025))
            ),
        )

        listOf(1L, 1023L, 1025L).forEach { invalidNonLastLength ->
            assertPreLedgerReject(
                operation,
                path,
                BodyDescriptor.Json(
                    valid.withManifest(
                        listOf(
                            manifestEntry(
                                1,
                                invalidNonLastLength,
                                "part-receipt-synthetic-1",
                            ),
                            lastSingleByte,
                        ),
                        invalidNonLastLength + 1,
                    )
                ),
            )
        }
        assertPreLedgerReject(
            operation,
            path,
            BodyDescriptor.Json(
                valid.withManifest(
                    listOf(
                        fullPart,
                        manifestEntry(2, 1025, "part-receipt-synthetic-2"),
                    ),
                    2049,
                )
            ),
        )
        listOf(0L, -1L).forEach { nonPositiveLastLength ->
            assertPreLedgerReject(
                operation,
                path,
                BodyDescriptor.Json(
                    valid.withManifest(
                        listOf(
                            fullPart,
                            manifestEntry(
                                2,
                                nonPositiveLastLength,
                                "part-receipt-synthetic-2",
                            ),
                        ),
                        1024 + nonPositiveLastLength,
                    )
                ),
            )
        }
    }

    private fun uploadPartSchemaCases() {
        val operation = ContractCatalog.operationsByClass.getValue("UPLOAD_PART")
        val path =
            listOf(
                PathParameter("uploadId", PathValue.OpaqueId("upload-synthetic-a")),
                PathParameter("planGeneration", PathValue.PositiveInteger(1)),
                PathParameter("partNumber", PathValue.PositiveInteger(1)),
            )
        val fixture = ContractCatalog.fixtures.first()
        accepted(
            operation,
            path,
            BodyDescriptor.Binary(
                fixture.byteLength.toLong(),
                fixture.sha256,
                "upload-synthetic-a",
                1,
                1,
            ),
            PROFILE_DIGEST,
        )
        assertPreLedgerReject(
            operation,
            path,
            BodyDescriptor.Json(
                canonicalObject(
                    "byteLength" to canonicalInteger(1),
                    "sha256" to canonicalString(fixture.sha256.value),
                    "uploadId" to canonicalString("upload-synthetic-a"),
                    "partNumber" to canonicalInteger(1),
                    "planGeneration" to canonicalInteger(1),
                )
            ),
        )
    }

    private fun createJobBody(): CanonicalValue.ObjectValue {
        val fixture = ContractCatalog.fixtures.first()
        return canonicalObject(
            "schemaVersion" to canonicalString("synthetic-v0.1"),
            "profileBindingSha256" to canonicalString(PROFILE_DIGEST.value),
            "syntheticTenantId" to canonicalString("tenant-synthetic-a"),
            "fixtureId" to canonicalString(fixture.id),
            "artifactClass" to canonicalString(ArtifactClass.SYNTHETIC_BYTES.name),
            "purpose" to canonicalString(ConsentPurpose.STAGE0_CONTRACT_TEST.name),
            "payloadByteLength" to canonicalInteger(fixture.byteLength.toLong()),
            "payloadSha256" to canonicalString(fixture.sha256.value),
        )
    }

    private fun uploadPlanBody(jobId: String): CanonicalValue.ObjectValue {
        val fixture = ContractCatalog.fixtures.first()
        return canonicalObject(
            "schemaVersion" to canonicalString("synthetic-v0.1"),
            "jobId" to canonicalString(jobId),
            "priorUploadId" to CanonicalValue.NullValue,
            "requestedPlanGeneration" to canonicalInteger(1),
            "partSizeBytes" to canonicalInteger(ContractCatalog.PART_SIZE_BYTES.toLong()),
            "totalByteLength" to canonicalInteger(fixture.byteLength.toLong()),
            "totalSha256" to canonicalString(fixture.sha256.value),
        )
    }

    private fun completeUploadBody(
        manifestEntry: CanonicalValue.ObjectValue
    ): CanonicalValue.ObjectValue {
        val fixture = ContractCatalog.fixtures.first()
        return canonicalObject(
            "schemaVersion" to canonicalString("synthetic-v0.1"),
            "jobId" to canonicalString("job-synthetic-a"),
            "uploadId" to canonicalString("upload-synthetic-a"),
            "manifest" to CanonicalValue.ArrayValue(listOf(manifestEntry)),
            "totalByteLength" to canonicalInteger(fixture.byteLength.toLong()),
            "totalSha256" to canonicalString(fixture.sha256.value),
        )
    }

    private fun manifestEntry(
        partNumber: Long = 1,
        byteLength: Long = ContractCatalog.fixtures.first().parts.single().byteLength.toLong(),
        partReceiptId: String = "part-receipt-synthetic-a",
    ): CanonicalValue.ObjectValue {
        val fixturePart = ContractCatalog.fixtures.first().parts.single()
        return canonicalObject(
            "partNumber" to canonicalInteger(partNumber),
            "byteLength" to canonicalInteger(byteLength),
            "sha256" to canonicalString(fixturePart.sha256.value),
            "partReceiptId" to canonicalString(partReceiptId),
        )
    }

    private fun CanonicalValue.ObjectValue.withManifest(
        entries: List<CanonicalValue.ObjectValue>,
        totalByteLength: Long,
    ): CanonicalValue.ObjectValue =
        with("manifest", CanonicalValue.ArrayValue(entries))
            .with("totalByteLength", canonicalInteger(totalByteLength))

    private fun CanonicalValue.ObjectValue.with(
        name: String,
        replacement: CanonicalValue,
    ): CanonicalValue.ObjectValue =
        CanonicalValue.ObjectValue(entries.filterNot { it.first == name } + (name to replacement))

    private fun CanonicalValue.ObjectValue.without(name: String): CanonicalValue.ObjectValue =
        CanonicalValue.ObjectValue(entries.filterNot { it.first == name })

    private fun trace006DigestsMatch() {
        val cases =
            listOf(
                Triple("CANCEL_JOB", "jobId", "job-synthetic-a"),
                Triple("CANCEL_JOB", "jobId", "job-synthetic-b"),
                Triple("DELETE_CLOUD_COPY", "conversationFixtureId", "conversation-synthetic-a"),
                Triple("DELETE_CLOUD_COPY", "conversationFixtureId", "conversation-synthetic-b"),
            )
        cases.forEach { (operationClass, pathName, target) ->
            val request =
                accepted(
                    ContractCatalog.operationsByClass.getValue(operationClass),
                    listOf(PathParameter(pathName, PathValue.OpaqueId(target))),
                    BodyDescriptor.None,
                    PROFILE_DIGEST,
                )
            checkEquals(
                ContractCatalog.trace006Digests.getValue("$operationClass/$target"),
                request.canonicalRequestDigest.value,
                "TRACE-006 digest",
            )
        }
    }

    private fun consentProfileUsesInjectedTimeAndFrozenBinding() {
        val unsigned =
            ConsentProfileBinding(
                profileId = "profile-synthetic-a",
                consentReceiptId = "receipt-synthetic-a",
                policyVersion = "policy-synthetic-a",
                artifactClass = ArtifactClass.SYNTHETIC_BYTES,
                purpose = ConsentPurpose.STAGE0_CONTRACT_TEST,
                endpointId = "endpoint-synthetic-a",
                regionCode = "SYN-REGION-A",
                tenantFixtureId = "tenant-synthetic-a",
                issuedAt = "2026-01-01T00:00:00Z",
                expiresAt = "2027-01-01T00:00:00Z",
                revokedAt = null,
                endpointAllowlistDigest = Sha256Hex("1".repeat(64)),
                profileBindingSha256 = Sha256Hex("0".repeat(64)),
            )
        val profile =
            unsigned.copy(profileBindingSha256 = ContractOracle.profileBindingDigest(unsigned))
        val binding = EndpointRegionBinding("endpoint-synthetic-a", "SYN-REGION-A")
        checkEquals(
            ProfileValidation.Valid,
            ContractOracle.validateProfile(
                profile,
                Instant.parse("2026-06-01T00:00:00Z"),
                true,
                binding,
            ),
            "valid consent profile",
        )
        checkTrue(
            ContractOracle.validateProfile(
                profile,
                Instant.parse("2027-01-01T00:00:00Z"),
                true,
                binding,
            ) is ProfileValidation.Rejected,
            "expired profile",
        )
        checkTrue(
            ContractOracle.validateProfile(
                profile,
                Instant.parse("2026-06-01T00:00:00Z"),
                true,
                EndpointRegionBinding("endpoint-synthetic-b", "SYN-REGION-A"),
            ) is ProfileValidation.Rejected,
            "endpoint mismatch",
        )
    }

    private fun assertPreLedgerReject(
        operation: OperationDefinition,
        path: List<PathParameter>,
        body: BodyDescriptor,
    ) {
        val decision = ContractOracle.prepareRequest(operation, path, body, PROFILE_DIGEST)
        checkTrue(decision is RequestPreparation.Rejected, "pre-idempotency rejection")
        decision as RequestPreparation.Rejected
        checkEquals("SCHEMA_VALIDATION_FAILED", decision.errorCode, "schema error")
        checkTrue(decision.rejectedBeforeIdempotency, "rejection stage")
        checkEquals(EffectVector.ZERO, decision.effectDelta, "rejection effects")
    }

    private fun accepted(
        operation: OperationDefinition,
        path: List<PathParameter>,
        body: BodyDescriptor,
        profileDigest: Sha256Hex,
    ): PreparedRequest {
        val preparation = ContractOracle.prepareRequest(operation, path, body, profileDigest)
        checkTrue(preparation is RequestPreparation.Accepted, "request accepted")
        return (preparation as RequestPreparation.Accepted).request
    }

    private val PROFILE_DIGEST = Sha256Hex("a".repeat(64))
}

internal fun checkTrue(condition: Boolean, label: String) {
    if (!condition) error("FAILED: $label")
}

internal fun checkEquals(expected: Any?, actual: Any?, label: String) {
    if (expected != actual) error("FAILED: $label")
}

internal inline fun checkFails(label: String, block: () -> Unit) {
    try {
        block()
    } catch (_: IllegalArgumentException) {
        return
    } catch (_: IllegalStateException) {
        return
    }
    error("FAILED: $label")
}
