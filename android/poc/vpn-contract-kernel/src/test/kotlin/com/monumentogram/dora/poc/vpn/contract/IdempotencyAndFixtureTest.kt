package com.monumentogram.dora.poc.vpn.contract

internal object IdempotencyAndFixtureTest {
    fun run() {
        sameKeySameTargetReplaysCommittedOutcome()
        sameKeyDifferentTargetIs409WithZeroEffects()
        fixtureCatalogMatchesRepositoryOwnedBytes()
        multipartPartitionPropertiesHold()
        deletionResourceBindingIncludesRegionOnlyAsDeclared()
    }

    private fun sameKeySameTargetReplaysCommittedOutcome() {
        val scope = scope("CANCEL_JOB")
        val digest =
            Sha256Hex(ContractCatalog.trace006Digests.getValue("CANCEL_JOB/job-synthetic-a"))
        val outcome =
            CanonicalOutcome(
                202,
                Sha256Hex("c".repeat(64)),
                listOf("cancel-receipt-synthetic-a"),
                EffectVector(serverStateTransitions = 1, receipts = 1),
            )
        val committed =
            ContractOracle.applyIdempotency(IdempotencyLedger(), scope, digest, outcome)
                as IdempotencyDecision.Committed
        checkEquals(outcome.committedEffects, committed.effectDelta, "first logical effects")
        checkTrue(!committed.evidence.replayed, "first response marker")
        checkTrue(
            !committed.evidence.toString().contains(scope.idempotencyKey.value),
            "raw key excluded from evidence",
        )

        val replacement =
            CanonicalOutcome(
                599,
                Sha256Hex("d".repeat(64)),
                listOf("forbidden-replacement"),
                EffectVector(resources = 9),
            )
        val replayed =
            ContractOracle.applyIdempotency(committed.ledger, scope, digest, replacement)
                as IdempotencyDecision.Replayed
        checkEquals(outcome, replayed.canonicalOutcome, "lost response canonical outcome")
        checkEquals(EffectVector.ZERO, replayed.effectDelta, "replay effects")
        checkTrue(replayed.evidence.replayed, "replay marker")
        checkEquals(committed.ledger, replayed.ledger, "replay ledger")

        val otherOperation =
            ContractOracle.applyIdempotency(
                committed.ledger,
                scope("DELETE_CLOUD_COPY"),
                Sha256Hex("e".repeat(64)),
                outcome,
            )
        checkTrue(otherOperation is IdempotencyDecision.Committed, "operation-class scope")
    }

    private fun sameKeyDifferentTargetIs409WithZeroEffects() {
        listOf(
                Triple("CANCEL_JOB", "job-synthetic-a", "job-synthetic-b"),
                Triple(
                    "DELETE_CLOUD_COPY",
                    "conversation-synthetic-a",
                    "conversation-synthetic-b",
                ),
            )
            .forEach { (operationClass, firstTarget, secondTarget) ->
                val firstDigest =
                    Sha256Hex(
                        ContractCatalog.trace006Digests.getValue("$operationClass/$firstTarget")
                    )
                val secondDigest =
                    Sha256Hex(
                        ContractCatalog.trace006Digests.getValue("$operationClass/$secondTarget")
                    )
                val outcome =
                    CanonicalOutcome(
                        202,
                        Sha256Hex("f".repeat(64)),
                        listOf("resource-synthetic-a"),
                        EffectVector(
                            serverStateTransitions = 1,
                            receipts = 1,
                            economicEffects = 1,
                            deletionRecords = if (operationClass == "DELETE_CLOUD_COPY") 1 else 0,
                        ),
                    )
                val committed =
                    ContractOracle.applyIdempotency(
                        IdempotencyLedger(),
                        scope(operationClass),
                        firstDigest,
                        outcome,
                    ) as IdempotencyDecision.Committed
                val mismatch =
                    ContractOracle.applyIdempotency(
                        committed.ledger,
                        scope(operationClass),
                        secondDigest,
                        outcome,
                    ) as IdempotencyDecision.PayloadMismatch
                checkEquals(409, mismatch.applicationStatus, "different target status")
                checkEquals(
                    "IDEMPOTENCY_KEY_PAYLOAD_MISMATCH",
                    mismatch.errorCode,
                    "different target error",
                )
                checkEquals(EffectVector.ZERO, mismatch.effectDelta, "TRACE-006 zero effects")
                checkEquals(committed.ledger, mismatch.ledger, "TRACE-006 ledger unchanged")
            }
    }

    private fun fixtureCatalogMatchesRepositoryOwnedBytes() {
        ContractCatalog.fixtures.forEach { fixture ->
            val bytes = ContractOracle.materializeFixture(fixture)
            checkEquals(fixture.byteLength, bytes.size, "fixture length")
            checkEquals(fixture.sha256, ContractOracle.sha256Hex(bytes), "fixture digest")
            val parts = ContractOracle.fixtureParts(bytes)
            checkEquals(fixture.parts.size, parts.size, "fixture part count")
            fixture.parts.zip(parts).forEach { (expected, actual) ->
                checkEquals(
                    expected.partNumber,
                    fixture.parts.indexOf(expected) + 1,
                    "part ordinal",
                )
                checkEquals(expected.byteLength, actual.size, "part length")
                checkEquals(expected.sha256, ContractOracle.sha256Hex(actual), "part digest")
            }
        }
        val valid = ContractOracle.materializeFixture(ContractCatalog.fixtures[5])
        val corrupted = ContractOracle.materializeFixture(ContractCatalog.fixtures[6])
        checkEquals(valid.size, corrupted.size, "corruption fixture length")
        checkEquals(
            1,
            valid.indices.count { valid[it] != corrupted[it] },
            "single corruption byte",
        )
        checkEquals(
            (valid[1024].toInt() xor 0x01).toByte(),
            corrupted[1024],
            "corruption location",
        )
    }

    private fun multipartPartitionPropertiesHold() {
        for (length in 1..4097) {
            val bytes = ContractOracle.generateFixtureBytes("VPN-FIX-PROPERTY", length)
            val parts = ContractOracle.fixtureParts(bytes)
            checkEquals(length, parts.sumOf { it.size }, "partition total length")
            checkTrue(
                parts.dropLast(1).all { it.size == ContractCatalog.PART_SIZE_BYTES },
                "full parts",
            )
            checkTrue(parts.last().size in 1..ContractCatalog.PART_SIZE_BYTES, "last part")
            checkTrue(
                parts.flatMap { it.asIterable() }.toByteArray().contentEquals(bytes),
                "partition reconstruction",
            )
        }
    }

    private fun deletionResourceBindingIncludesRegionOnlyAsDeclared() {
        val digest =
            ContractOracle.deleteResourceBindingDigest(
                "tenant-synthetic-a",
                PROFILE_DIGEST,
                EndpointRegionBinding("endpoint-synthetic-a", "SYN-REGION-A"),
                "conversation-synthetic-a",
            )
        val anotherRegion =
            ContractOracle.deleteResourceBindingDigest(
                "tenant-synthetic-a",
                PROFILE_DIGEST,
                EndpointRegionBinding("endpoint-synthetic-a", "SYN-REGION-B"),
                "conversation-synthetic-a",
            )
        checkTrue(digest != anotherRegion, "region-bound deletion digest")
    }

    private fun scope(operationClass: String) =
        IdempotencyScope(
            "tenant-synthetic-a",
            PROFILE_DIGEST,
            operationClass,
            IdempotencyKey("idempotency-synthetic-a"),
        )

    private val PROFILE_DIGEST = Sha256Hex("a".repeat(64))
}
