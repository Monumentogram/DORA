@file:Suppress("LongMethod")

package com.monumentogram.dora.poc.vpn.contract

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

internal object MachineRecordParityTest {
    fun run() {
        val root = Path.of(checkNotNull(System.getProperty("dora.repo.root")))
        val machinePath = root.resolve("docs/evidence/poc-vpn-001/contract-record-stage0-v0.1.json")
        val markdownPath =
            root.resolve("docs/stage0/DORA_MVP1_POC_VPN_SYNTHETIC_CONTRACT_STAGE0_V0_1.md")
        val machineBytes = Files.readAllBytes(machinePath)
        val markdownBytes = Files.readAllBytes(markdownPath)
        checkEquals(
            ContractCatalog.MACHINE_RECORD_SHA256,
            ContractOracle.sha256Hex(machineBytes).value,
            "machine record source pin",
        )
        checkEquals(
            ContractCatalog.MARKDOWN_CONTRACT_SHA256,
            ContractOracle.sha256Hex(markdownBytes).value,
            "Markdown contract source pin",
        )
        val record =
            DoraCanonicalJson.parseStrict(String(machineBytes, StandardCharsets.UTF_8)).asObject()
        parity(record)
    }

    private fun parity(record: CanonicalValue.ObjectValue) {
        checkEquals(ContractCatalog.CONTRACT_ID, record.requiredString("contractId"), "contract id")
        checkEquals(
            "CONTRACT_COMPLETE",
            record.requiredString("contractArtifactState"),
            "artifact state",
        )
        val backlog = record.requiredObject("backlogItem")
        checkEquals("POC-VPN-001", backlog.requiredString("id"), "backlog id")
        checkEquals("TODO", backlog.requiredString("state"), "backlog state")
        checkEquals("NOT_READY", backlog.requiredString("readiness"), "backlog readiness")
        checkEquals("NOT_RUN", backlog.requiredString("executionStatus"), "execution state")
        checkEquals(
            "NOT_AUTHORIZED",
            backlog.requiredString("executionAuthorization"),
            "execution authorization",
        )
        checkTrue(
            !record.requiredObject("reviewer").requiredBoolean("formalReviewer"),
            "formal reviewer",
        )
        val authority = record.requiredObject("authorityFlags")
        checkEquals(ContractCatalog.authorityFlags.keys, authority.keys(), "authority keys")
        authority.entries.forEach { (_, value) ->
            checkTrue(value is CanonicalValue.BooleanValue && !value.value, "authority false")
        }

        operationParity(record)
        clientParity(record.requiredObject("clientStateMachine"))
        serverParity(record.requiredObject("serverStateMachine"))
        traceParity(record)
        faultParity(record)
        fixtureParity(record.requiredObject("syntheticFixtures"))
        deletionParity(record.requiredObject("clientStateMachine"))
    }

    private fun operationParity(record: CanonicalValue.ObjectValue) {
        val machine = record.requiredArray("operations").map { it.asObject() }
        checkEquals(ContractCatalog.operations.size, machine.size, "operation parity count")
        ContractCatalog.operations.zip(machine).forEach { (expected, actual) ->
            checkEquals(expected.id.value, actual.requiredString("id"), "operation id parity")
            checkEquals(
                expected.operationClass,
                actual.requiredString("name"),
                "operation name parity",
            )
            checkEquals(
                expected.method.name,
                actual.requiredString("method"),
                "operation method parity",
            )
            checkEquals(
                expected.routeTemplate,
                actual.requiredString("routeTemplate"),
                "route parity",
            )
            val actualPath = actual.requiredObject("pathParameterSchema")
            val expectedPath =
                expected.pathParameterSchema.associate {
                    it.name to
                        when (it.type) {
                            PathParameterType.OPAQUE_ID -> "opaque-id"
                            PathParameterType.POSITIVE_INTEGER -> "positive-integer"
                        }
                }
            checkEquals(expectedPath.keys, actualPath.keys(), "path schema keys")
            expectedPath.forEach { (name, type) ->
                checkEquals(type, actualPath.requiredString(name), "path schema type")
            }
            checkEquals(
                expected.bodyTargetFields,
                actual.stringArray("bodyTargetFields"),
                "body targets",
            )
            checkEquals(
                expected.pathBodyEqualityFields,
                actual.stringArray("pathBodyEqualityFields"),
                "path-body equality",
            )
            checkEquals(
                expected.requestSchema,
                actual.requiredString("requestSchema"),
                "request schema",
            )
            checkEquals(
                expected.responseSchema,
                actual.requiredString("responseSchema"),
                "response schema",
            )
            checkEquals(
                expected.idempotencyPolicy,
                actual.requiredString("idempotency"),
                "idempotency",
            )
            checkEquals(
                expected.successStatus.toLong(),
                actual.requiredInteger("successStatus"),
                "status",
            )
        }
    }

    private fun clientParity(machine: CanonicalValue.ObjectValue) {
        checkEquals(
            ClientState.entries.map { it.name },
            machine.stringArray("states"),
            "client states",
        )
        checkEquals(
            ContractCatalog.clientTerminalStates.map { it.name }.toSet(),
            machine.stringArray("terminalStates").toSet(),
            "client terminals",
        )
        val transitions = machine.requiredArray("transitions").map { it.asObject() }
        ContractCatalog.clientTransitions.zip(transitions).forEach { (expected, actual) ->
            checkEquals(expected.id, actual.requiredString("id"), "client transition id")
            checkEquals(
                expected.from.map { it.name }.toSet(),
                actual.stringOrArray("from").toSet(),
                "client from",
            )
            checkEquals(expected.event, actual.requiredString("event"), "client event")
            checkEquals(
                destination(expected.destination),
                actual.requiredString("to"),
                "client destination",
            )
            checkEquals(
                expected.resumeState?.name,
                actual.optionalString("resumeState"),
                "client resume",
            )
            checkEquals(
                expected.retryClass?.name,
                actual.optionalString("retryClass"),
                "client retry",
            )
            checkEquals(
                expected.visibleSubstatusOnEnter,
                actual.optionalString("visibleSubstatusOnEnter"),
                "client substatus",
            )
            checkEquals(
                expected.preserveDeletionRecord,
                actual.optionalBoolean("preserveDeletionRecord") ?: false,
                "client deletion preservation",
            )
        }
        checkEquals(
            ContractCatalog.clientTransitions.size,
            transitions.size,
            "client transition count",
        )
        val groups =
            machine.requiredObject("arbitration").requiredArray("priorityGroups").map {
                it.asObject()
            }
        ContractCatalog.priorityGroups.zip(groups).forEach { (expected, actual) ->
            checkEquals(expected.priority.toLong(), actual.requiredInteger("priority"), "priority")
            checkEquals(expected.name, actual.requiredString("name"), "priority name")
            checkEquals(
                expected.transitionIds,
                actual.stringArray("transitionIds"),
                "priority order",
            )
        }
        checkEquals(ContractCatalog.priorityGroups.size, groups.size, "priority group count")
    }

    private fun serverParity(machine: CanonicalValue.ObjectValue) {
        checkEquals(
            ServerState.entries.map { it.name },
            machine.stringArray("states"),
            "server states",
        )
        val transitions = machine.requiredArray("transitions").map { it.asObject() }
        ContractCatalog.serverTransitions.zip(transitions).forEach { (expected, actual) ->
            checkEquals(expected.id, actual.requiredString("id"), "server transition id")
            checkEquals(
                expected.from.map { it.name }.toSet(),
                actual.stringOrArray("from").toSet(),
                "server from",
            )
            checkEquals(expected.event, actual.requiredString("event"), "server event")
            checkEquals(expected.destination, actual.requiredString("to"), "server destination")
        }
        checkEquals(
            ContractCatalog.serverTransitions.size,
            transitions.size,
            "server transition count",
        )
    }

    private fun traceParity(record: CanonicalValue.ObjectValue) {
        val traces = record.requiredArray("transitionTraces").map { it.asObject() }
        checkEquals(
            ContractCatalog.traces.map { it.id },
            traces.map { it.requiredString("id") },
            "trace ids",
        )
        checkEquals(
            ContractCatalog.traces.map { it.name },
            traces.map { it.requiredString("name") },
            "trace names",
        )
        val trace006 = traces.last()
        val cases = trace006.requiredArray("cases").map { it.asObject() }
        cases.forEach { case ->
            val operationClass = case.requiredString("operationClass")
            val first = case.requiredObject("firstRequest")
            val replay = case.requiredObject("differentTargetReplay")
            val pathName = first.requiredObject("canonicalPathParameters").entries.single().first
            val firstTarget =
                first.requiredObject("canonicalPathParameters").requiredString(pathName)
            val secondTarget =
                replay.requiredObject("canonicalPathParameters").requiredString(pathName)
            checkEquals(
                ContractCatalog.trace006Digests.getValue("$operationClass/$firstTarget"),
                first.requiredString("canonicalRequestDigest"),
                "TRACE-006 first digest parity",
            )
            checkEquals(
                ContractCatalog.trace006Digests.getValue("$operationClass/$secondTarget"),
                replay.requiredString("canonicalRequestDigest"),
                "TRACE-006 second digest parity",
            )
            checkEquals(409L, replay.requiredInteger("responseStatus"), "TRACE-006 response")
            listOf(
                    "serverStateTransitionDelta",
                    "cancelReceiptCountDelta",
                    "economicEffectCountDelta",
                    "deletionRecordCountDelta",
                )
                .forEach { field ->
                    checkEquals(0L, replay.requiredInteger(field), "TRACE-006 zero vector")
                }
        }
    }

    private fun faultParity(record: CanonicalValue.ObjectValue) {
        val faults = record.requiredArray("faultMatrix").map { it.asObject() }
        val coverage =
            record
                .requiredArray("faultTransitionCoverage")
                .map { it.asObject() }
                .associateBy {
                    it.requiredString("faultId")
                }
        ContractCatalog.faults.zip(faults).forEach { (expected, actual) ->
            checkEquals(expected.id, actual.requiredString("id"), "fault id")
            checkEquals(expected.boundary, actual.requiredString("boundary"), "fault boundary")
            checkEquals(
                expected.retryClass.name,
                actual.requiredString("retryClass"),
                "fault retry",
            )
            checkEquals(expected.proofClass, actual.requiredString("proofClass"), "fault proof")
            val actualCoverage = coverage.getValue(expected.id)
            checkEquals(
                expected.clientTransitionIds,
                actualCoverage.stringArray("clientTransitionIds"),
                "fault client coverage",
            )
            checkEquals(
                expected.serverTransitionIds,
                actualCoverage.stringArray("serverTransitionIds"),
                "fault server coverage",
            )
            checkEquals(
                expected.traceIds,
                actualCoverage.stringArray("traceIds"),
                "fault trace coverage",
            )
            checkEquals(
                expected.ruleTargets,
                actualCoverage.optionalStringArray("ruleTargets"),
                "fault rule targets",
            )
        }
        checkEquals(ContractCatalog.faults.size, faults.size, "fault count")
        checkEquals(ContractCatalog.faults.size, coverage.size, "fault coverage count")
    }

    private fun fixtureParity(machine: CanonicalValue.ObjectValue) {
        checkEquals(
            ContractCatalog.PART_SIZE_BYTES.toLong(),
            machine.requiredInteger("partSizeBytes"),
            "part size",
        )
        val fixtures = machine.requiredArray("fixtures").map { it.asObject() }
        ContractCatalog.fixtures.zip(fixtures).forEach { (expected, actual) ->
            checkEquals(expected.id, actual.requiredString("id"), "fixture id")
            checkEquals(
                expected.byteLength.toLong(),
                actual.requiredInteger("byteLength"),
                "fixture length",
            )
            checkEquals(expected.sha256.value, actual.requiredString("sha256"), "fixture digest")
            checkEquals(
                expected.declaredExpectedSha256?.value,
                actual.optionalString("declaredExpectedSha256"),
                "fixture declared digest",
            )
            val parts = actual.requiredArray("parts").map { it.asObject() }
            expected.parts.zip(parts).forEach { (expectedPart, actualPart) ->
                checkEquals(
                    expectedPart.partNumber.toLong(),
                    actualPart.requiredInteger("partNumber"),
                    "part number",
                )
                checkEquals(
                    expectedPart.byteLength.toLong(),
                    actualPart.requiredInteger("byteLength"),
                    "part length",
                )
                checkEquals(
                    expectedPart.sha256.value,
                    actualPart.requiredString("sha256"),
                    "part digest",
                )
                checkEquals(
                    expectedPart.declaredExpectedSha256?.value,
                    actualPart.optionalString("declaredExpectedSha256"),
                    "part declared digest",
                )
            }
            checkEquals(expected.parts.size, parts.size, "part count")
        }
        checkEquals(ContractCatalog.fixtures.size, fixtures.size, "fixture count")
    }

    private fun deletionParity(machine: CanonicalValue.ObjectValue) {
        val deletion = machine.requiredObject("deletionPendingModel")
        checkEquals(
            ContractCatalog.deletionRecordFields,
            deletion.stringArray("durableRecordFields"),
            "deletion fields parity",
        )
        checkEquals(
            ContractCatalog.deletionVisibleSubstatuses,
            deletion.stringArray("visibleSubstatuses"),
            "deletion substatuses",
        )
        checkEquals(
            ContractCatalog.deletionErrorCodes,
            deletion.stringArray("contentFreeErrorCodes"),
            "deletion error codes",
        )
    }

    private fun destination(destination: TransitionDestination): String =
        when (destination) {
            is TransitionDestination.Fixed -> destination.state.name
            TransitionDestination.PersistedResumeState -> "\$persistedResumeState"
            TransitionDestination.SameState -> "\$sameState"
        }

    private fun CanonicalValue.asObject(): CanonicalValue.ObjectValue =
        this as? CanonicalValue.ObjectValue ?: error("FAILED: expected object")

    private fun CanonicalValue.ObjectValue.stringArray(name: String): List<String> =
        requiredArray(name).map { (it as CanonicalValue.StringValue).value }

    private fun CanonicalValue.ObjectValue.optionalStringArray(name: String): List<String> =
        when (val value = value(name)) {
            null -> emptyList()
            is CanonicalValue.ArrayValue ->
                value.values.map { (it as CanonicalValue.StringValue).value }
            else -> error("FAILED: expected optional string array")
        }

    private fun CanonicalValue.ObjectValue.stringOrArray(name: String): List<String> =
        when (val value = required(name)) {
            is CanonicalValue.StringValue -> listOf(value.value)
            is CanonicalValue.ArrayValue ->
                value.values.map { (it as CanonicalValue.StringValue).value }
            else -> error("FAILED: expected string or array")
        }

    private fun CanonicalValue.ObjectValue.requiredBoolean(name: String): Boolean =
        (required(name) as? CanonicalValue.BooleanValue)?.value ?: error("FAILED: expected boolean")

    private fun CanonicalValue.ObjectValue.optionalString(name: String): String? =
        when (val value = value(name)) {
            null,
            CanonicalValue.NullValue -> null
            is CanonicalValue.StringValue -> value.value
            else -> error("FAILED: expected optional string")
        }

    private fun CanonicalValue.ObjectValue.optionalBoolean(name: String): Boolean? =
        when (val value = value(name)) {
            null,
            CanonicalValue.NullValue -> null
            is CanonicalValue.BooleanValue -> value.value
            else -> error("FAILED: expected optional boolean")
        }
}
