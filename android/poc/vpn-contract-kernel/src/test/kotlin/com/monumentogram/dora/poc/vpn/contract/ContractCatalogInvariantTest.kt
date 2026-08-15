@file:Suppress("LongMethod", "NestedBlockDepth")

package com.monumentogram.dora.poc.vpn.contract

object ContractKernelHostTest {
    @JvmStatic
    fun main(args: Array<String>) {
        CanonicalContractTest.run()
        IdempotencyAndFixtureTest.run()
        ContractCatalogInvariantTest.run()
        MachineRecordParityTest.run()
        println("PASS contract-kernel-host-oracle")
    }
}

internal object ContractCatalogInvariantTest {
    fun run() {
        catalogRangesAndReferencesAreExact()
        stateMachinesAreReachableAndTerminalSafe()
        arbitrationIsDeterministic()
        lostResponseAndImmediateRejectPathsAreExact()
        cancelThenDeleteAndPendingDeletionAreMonotone()
    }

    private fun catalogRangesAndReferencesAreExact() {
        checkEquals(ids("VPN-OP", 9), ContractCatalog.operations.map { it.id.value }, "operations")
        checkEquals(
            ids("VPN-C-TR", 52),
            ContractCatalog.clientTransitions.map { it.id },
            "client transitions",
        )
        checkEquals(
            ids("VPN-S-TR", 20),
            ContractCatalog.serverTransitions.map { it.id },
            "server transitions",
        )
        checkEquals(ids("VPN-TRACE", 6), ContractCatalog.traces.map { it.id }, "traces")
        checkEquals(ids("VPN-FLT", 34), ContractCatalog.faults.map { it.id }, "faults")
        checkEquals(ids("VPN-FIX", 7), ContractCatalog.fixtures.map { it.id }, "fixtures")
        checkEquals(16, ClientState.entries.size, "client state count")
        checkEquals(12, ServerState.entries.size, "server state count")
        checkEquals(10, ContractCatalog.outcomeInvariantIds.size, "outcome invariant count")

        val transitionIds = ContractCatalog.clientTransitions.map { it.id }.toSet()
        val prioritized = ContractCatalog.priorityGroups.flatMap { it.transitionIds }
        checkEquals(52, prioritized.size, "priority coverage count")
        checkEquals(52, prioritized.toSet().size, "priority uniqueness")
        checkEquals(transitionIds, prioritized.toSet(), "priority coverage")
        val serverIds = ContractCatalog.serverTransitions.map { it.id }.toSet()
        val traceIds = ContractCatalog.traces.map { it.id }.toSet()
        ContractCatalog.traces.forEach { trace ->
            checkTrue(
                trace.clientTransitionIds.all { it in transitionIds },
                "trace client reference",
            )
            checkTrue(trace.serverTransitionIds.all { it in serverIds }, "trace server reference")
        }
        ContractCatalog.faults.forEach { fault ->
            checkTrue(
                fault.clientTransitionIds.all { it in transitionIds },
                "fault client reference",
            )
            checkTrue(fault.serverTransitionIds.all { it in serverIds }, "fault server reference")
            checkTrue(fault.traceIds.all { it in traceIds }, "fault trace reference")
        }
        val processDeathRules =
            listOf(
                "clientStateMachine.processDeathRule",
                "serverStateMachine.processDeathRule",
            )
        ContractCatalog.faults.forEach { fault ->
            val expectedRules =
                if (fault.id in setOf("VPN-FLT-026", "VPN-FLT-027")) processDeathRules
                else emptyList()
            checkEquals(expectedRules, fault.ruleTargets, "fault rule targets")
        }
        checkTrue(ContractCatalog.authorityFlags.values.none { it }, "authority flags false")
    }

    private fun stateMachinesAreReachableAndTerminalSafe() {
        val reachableClient = mutableSetOf(ClientState.BLOCKED_NO_PROFILE)
        var changed: Boolean
        do {
            changed = false
            ContractCatalog.clientTransitions.forEach { transition ->
                if (transition.from.any { it in reachableClient }) {
                    val destination = transition.destination as? TransitionDestination.Fixed
                    if (destination != null && reachableClient.add(destination.state))
                        changed = true
                }
            }
        } while (changed)
        checkEquals(ClientState.entries.toSet(), reachableClient, "client reachability")
        ContractCatalog.clientTerminalStates.forEach { terminal ->
            checkTrue(
                ContractCatalog.clientTransitions.none { terminal in it.from },
                "absorbing client terminal",
            )
        }
        val cancelledOutgoing =
            ContractCatalog.clientTransitions.filter { ClientState.CANCELLED in it.from }
        checkEquals(listOf("VPN-C-TR-042"), cancelledOutgoing.map { it.id }, "cancelled outgoing")

        val reachableServer = mutableSetOf(ServerState.ABSENT)
        do {
            changed = false
            ContractCatalog.serverTransitions.forEach { transition ->
                if (
                    transition.from.any { it in reachableServer } &&
                        !transition.destination.startsWith('$')
                ) {
                    if (reachableServer.add(ServerState.valueOf(transition.destination)))
                        changed = true
                }
            }
        } while (changed)
        checkEquals(ServerState.entries.toSet(), reachableServer, "server reachability")
        ContractCatalog.serverTransitions
            .filter { ServerState.DELETED in it.from }
            .forEach {
                checkTrue(
                    it.destination == ServerState.DELETED.name || it.destination == "\$sameState",
                    "server deleted absorption",
                )
            }
    }

    private fun arbitrationIsDeterministic() {
        val allCompeting =
            setOf("VPN-C-TR-004", "VPN-C-TR-001", "VPN-C-TR-035", "VPN-C-TR-029", "VPN-C-TR-006")
        checkEquals(
            "VPN-C-TR-004",
            ContractOracle.selectClientTransition(ClientState.CREATING, allCompeting)?.transitionId,
            "durable outcome priority",
        )
        checkEquals(
            "VPN-C-TR-001",
            ContractOracle.selectClientTransition(
                    ClientState.CREATING,
                    allCompeting - "VPN-C-TR-004",
                )
                ?.transitionId,
            "profile priority",
        )
        checkEquals(
            "VPN-C-TR-035",
            ContractOracle.selectClientTransition(
                    ClientState.CREATING,
                    allCompeting - setOf("VPN-C-TR-004", "VPN-C-TR-001"),
                )
                ?.transitionId,
            "budget priority",
        )
        val twice =
            List(20) {
                ContractOracle.selectClientTransition(
                        ClientState.CANCEL_PENDING,
                        setOf("VPN-C-TR-030", "VPN-C-TR-031"),
                    )
                    ?.transitionId
            }
        checkEquals(setOf("VPN-C-TR-030"), twice.toSet(), "within-group determinism")
        checkEquals(
            null,
            ContractOracle.selectClientTransition(ClientState.READY, setOf("VPN-C-TR-004")),
            "no eligible transition",
        )
    }

    private fun lostResponseAndImmediateRejectPathsAreExact() {
        val lost =
            ContractOracle.selectClientTransition(
                ClientState.RESULT_AVAILABLE,
                setOf("VPN-C-TR-036"),
            )!!
        checkEquals(ClientState.RETRY_SCHEDULED, lost.to, "lost result wrapper")
        checkEquals(ClientState.RESULT_AVAILABLE, lost.nextResumeState, "lost result resume state")
        val resumed =
            ContractOracle.selectClientTransition(
                ClientState.RETRY_SCHEDULED,
                setOf("VPN-C-TR-034"),
                persistedResumeState = ClientState.RESULT_AVAILABLE,
            )!!
        checkEquals(ClientState.RESULT_AVAILABLE, resumed.to, "lost result replay resume")
        checkEquals(
            ClientState.RESULT_VERIFIED,
            ContractOracle.selectClientTransition(
                    ClientState.RESULT_AVAILABLE,
                    setOf("VPN-C-TR-022"),
                )
                ?.to,
            "replayed result verification",
        )
        checkEquals(
            "VPN-C-TR-023",
            ContractOracle.selectClientTransition(
                    ClientState.RESULT_AVAILABLE,
                    setOf("VPN-C-TR-023", "VPN-C-TR-035", "VPN-C-TR-036"),
                )
                ?.transitionId,
            "immediate final reject priority",
        )
        val deleteReject =
            ContractOracle.selectClientTransition(
                ClientState.DELETE_PENDING,
                setOf("VPN-C-TR-046", "VPN-C-TR-051", "VPN-C-TR-027"),
            )!!
        checkEquals(ClientState.DELETE_PENDING, deleteReject.to, "delete reject primary state")
        checkEquals(
            "DELETE_USER_ACTION_REQUIRED",
            deleteReject.visibleSubstatus,
            "delete reject substatus",
        )
        checkTrue(deleteReject.preserveDeletionRecord, "delete reject record")
    }

    private fun cancelThenDeleteAndPendingDeletionAreMonotone() {
        var state = ClientState.REMOTE_PROCESSING
        listOf("VPN-C-TR-029", "VPN-C-TR-030", "VPN-C-TR-042", "VPN-C-TR-025", "VPN-C-TR-026")
            .forEach { transitionId ->
                state =
                    ContractOracle.selectClientTransition(state, setOf(transitionId))?.to
                        ?: error("FAILED: cancel-delete trace")
            }
        checkEquals(ClientState.DELETED, state, "cancel-delete terminal")

        val deleteTransitions =
            ContractCatalog.clientTransitions.filter { ClientState.DELETE_PENDING in it.from }
        checkEquals(13, deleteTransitions.size, "delete transition count")
        deleteTransitions.forEach { transition ->
            val destination = transition.destination as TransitionDestination.Fixed
            checkTrue(
                destination.state in setOf(ClientState.DELETE_PENDING, ClientState.DELETED),
                "delete monotone destination",
            )
            if (destination.state == ClientState.DELETE_PENDING) {
                checkTrue(transition.preserveDeletionRecord, "delete record preservation")
            }
        }
        checkEquals(
            listOf("VPN-C-TR-026"),
            deleteTransitions
                .filter {
                    (it.destination as TransitionDestination.Fixed).state == ClientState.DELETED
                }
                .map { it.id },
            "receipt-only delete exit",
        )

        val record = deletionRecord()
        checkEquals(
            ContractCatalog.deletionRecordFields,
            record.evidenceFields().keys().toList(),
            "deletion record fields",
        )
        checkEquals(11, record.evidenceFields().entries.size, "deletion record field count")
        checkTrue(
            record.evidenceFields().keys().none { it.contains("country", ignoreCase = true) },
            "region code only",
        )
        val pending = PendingDeletion(record, "DELETE_WAITING_NETWORK")
        val cancel =
            ContractOracle.selectClientTransition(
                ClientState.DELETE_PENDING,
                setOf("VPN-C-TR-044"),
                currentVisibleSubstatus = pending.visibleSubstatus,
            )!!
        checkEquals(ClientState.DELETE_PENDING, cancel.to, "delete cancel rejected")
        checkEquals(pending.visibleSubstatus, cancel.visibleSubstatus, "delete cancel substatus")
        checkTrue(cancel.preserveDeletionRecord, "delete cancel record")

        val stablePending = pending.copy(stableDeletionReceiptId = "deletion-receipt-synthetic-a")
        listOf(
                "missing receipt" to
                    receipt(
                        record.deletionId,
                        null,
                        ServerState.DELETED,
                        verifiedAbsent = true,
                        revision = 8,
                    ),
                "mismatched deletion" to
                    receipt(
                        "deletion-synthetic-b",
                        "deletion-receipt-synthetic-a",
                        ServerState.DELETED,
                        verifiedAbsent = true,
                        revision = 8,
                    ),
                "mismatched stable receipt" to
                    receipt(
                        record.deletionId,
                        "deletion-receipt-synthetic-b",
                        ServerState.DELETED,
                        verifiedAbsent = true,
                        revision = 8,
                    ),
                "nonterminal receipt" to
                    receipt(
                        record.deletionId,
                        "deletion-receipt-synthetic-a",
                        ServerState.PROCESSING,
                        verifiedAbsent = true,
                        revision = 8,
                    ),
                "unverified receipt" to
                    receipt(
                        record.deletionId,
                        "deletion-receipt-synthetic-a",
                        ServerState.DELETED,
                        verifiedAbsent = false,
                        revision = 8,
                    ),
                "stale receipt" to
                    receipt(
                        record.deletionId,
                        "deletion-receipt-synthetic-a",
                        ServerState.DELETED,
                        verifiedAbsent = true,
                        revision = 6,
                    ),
            )
            .forEach { (label, receipt) ->
                assertReceiptRejected(stablePending, receipt, label)
            }
        val advanced =
            ContractOracle.applyDeletionReceipt(
                stablePending,
                receipt(
                    record.deletionId,
                    "deletion-receipt-synthetic-a",
                    ServerState.DELETE_PENDING,
                    verifiedAbsent = false,
                    revision = 8,
                ),
            )
        checkTrue(advanced is DeletionReceiptDecision.Pending, "pending receipt remains pending")
        advanced as DeletionReceiptDecision.Pending
        checkEquals(
            8L,
            advanced.deletion.record.lastReceiptRevision,
            "receipt revision advance",
        )
        checkEquals(
            "deletion-receipt-synthetic-a",
            advanced.deletion.stableDeletionReceiptId,
            "stable receipt binding",
        )
        val deleted =
            ContractOracle.applyDeletionReceipt(
                advanced.deletion,
                receipt(
                    record.deletionId,
                    "deletion-receipt-synthetic-a",
                    ServerState.DELETED,
                    verifiedAbsent = true,
                    revision = 9,
                ),
            )
        checkTrue(deleted is DeletionReceiptDecision.Deleted, "verified receipt exit")
        deleted as DeletionReceiptDecision.Deleted
        checkEquals(
            "deletion-receipt-synthetic-a",
            deleted.receipt.deletionReceiptId,
            "terminal receipt represented",
        )
        checkEquals(ServerState.DELETED, deleted.receipt.state, "terminal server state")
        checkTrue(deleted.receipt.verifiedAbsent, "terminal verified absent")
        checkEquals(EffectVector.ZERO, deleted.effectDelta, "receipt poll effects")
        checkEquals(
            deleted,
            ContractOracle.applyDeletionReceipt(
                advanced.deletion,
                deleted.receipt,
            ),
            "stable terminal receipt replay",
        )
    }

    private fun assertReceiptRejected(
        pending: PendingDeletion,
        receipt: DeletionReceiptEvidence,
        label: String,
    ) {
        val rejected = ContractOracle.applyDeletionReceipt(pending, receipt)
        checkTrue(rejected is DeletionReceiptDecision.RejectedNoStateChange, label)
        rejected as DeletionReceiptDecision.RejectedNoStateChange
        checkEquals(pending, rejected.deletion, "$label preserves pending deletion")
        checkEquals(EffectVector.ZERO, rejected.effectDelta, "$label effects")
    }

    private fun receipt(
        deletionId: String,
        deletionReceiptId: String?,
        state: ServerState,
        verifiedAbsent: Boolean,
        revision: Long,
    ) =
        DeletionReceiptEvidence(
            schemaVersion = "synthetic-v0.1",
            deletionId = deletionId,
            deletionReceiptId = deletionReceiptId,
            state = state,
            verifiedAbsent = verifiedAbsent,
            receiptRevision = revision,
        )

    private fun deletionRecord(): DeletionRecord =
        DeletionRecord(
            jobId = "job-synthetic-a",
            conversationFixtureId = "conversation-synthetic-a",
            deleteResourceBindingSha256 = Sha256Hex("1".repeat(64)),
            deleteIdempotencyKeyLedgerRef = "ledger-synthetic-a",
            deleteIdempotencyKeyDigest = Sha256Hex("2".repeat(64)),
            deleteRequestDigest = Sha256Hex("3".repeat(64)),
            deletionId = "deletion-synthetic-a",
            profileBindingSha256 = Sha256Hex("4".repeat(64)),
            endpointId = "endpoint-synthetic-a",
            regionCode = "SYN-REGION-A",
            lastReceiptRevision = 7,
        )

    private fun ids(prefix: String, count: Int) =
        (1..count).map { "$prefix-${it.toString().padStart(3, '0')}" }
}
