@file:Suppress("MagicNumber", "LongMethod", "TooManyFunctions", "TooGenericExceptionCaught")

package com.monumentogram.dora.poc.recovery.candidate

import android.content.Context
import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient
import com.monumentogram.dora.poc.recovery.bootstrap.AndroidRecoveryKeyBootstrap
import com.monumentogram.dora.poc.recovery.bootstrap.BootstrapResult
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeAad
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeTargetKind
import com.monumentogram.dora.poc.recovery.contract.KeyRecoveryClassification
import com.monumentogram.dora.poc.recovery.contract.MicrofileAad
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryContract
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineArtifactRole
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineObservedState
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingIdentity
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalReadResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingWitnessInput
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.contract.StreamDecision
import com.monumentogram.dora.poc.recovery.controller.ConfirmationDiagnostic
import com.monumentogram.dora.poc.recovery.controller.ConfirmationPhase
import com.monumentogram.dora.poc.recovery.controller.ConfirmationResult
import com.monumentogram.dora.poc.recovery.controller.ControlledKey04Replacement
import com.monumentogram.dora.poc.recovery.controller.RecoveryKeyConfirmationController
import com.monumentogram.dora.poc.recovery.coordination.ProcessRecoveryRunSingleWriterGuard
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryJournalDatabase
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryMicrofileJournal
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryQuarantineJournal
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryReconciliationSource
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryStreamingJournal
import com.monumentogram.dora.poc.recovery.journal.RecoveryJournalSchema
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryCandidateStorage
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryReconciliationStorage
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryStreamingSource
import java.io.File
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Explicitly selected synthetic endpoint. Host evidence determines campaign validity. */
@RunWith(AndroidJUnit4::class)
class RecoveryCampaignInstrumentedTest {
    @Test
    fun execute() {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("recoveryCampaign") == "true")
        require(
            arguments.getString("recoveryHarnessRevision")?.matches(Regex("[0-9a-f]{40}")) == true
        )
        require(
            arguments.getString("recoveryCampaignManifestSha256")?.matches(Regex("[0-9a-f]{64}")) ==
                true
        )
        val encoded = requireNotNull(arguments.getString("recoveryCampaignRequest"))
        require(encoded.length <= 32768)
        val request = JSONObject(String(Base64.decode(encoded, Base64.NO_WRAP), Charsets.UTF_8))
        val campaign = Campaign(InstrumentationRegistry.getInstrumentation().targetContext, request)
        try {
            campaign.execute()
        } catch (error: Throwable) {
            campaign.emit(
                "ERROR",
                JSONObject().put("safeExceptionType", error.javaClass.simpleName),
            )
            throw error
        }
    }
}

// Test-only dispatch keeps the finite protocol cases and their observed outcomes together.
@Suppress("LargeClass")
private class Campaign(private val context: Context, private val request: JSONObject) {
    private val attempt =
        request.getString("attemptId").also {
            require(it.matches(Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,159}")))
        }
    private val operation = request.getString("operation")
    private val harnessRevision =
        requireNotNull(InstrumentationRegistry.getArguments().getString("recoveryHarnessRevision"))
    private val manifestSha256 =
        requireNotNull(
            InstrumentationRegistry.getArguments().getString("recoveryCampaignManifestSha256")
        )
    private val candidate = RecoveryCandidate.fromContractId(request.getString("candidateId"))
    private val runHex =
        request.getString("runId").also { require(it.matches(Regex("[0-9a-f]{32}"))) }
    private val run = RunId.fromBytes(runHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
    private val confirmation = KeyConfirmationValue(candidate, run)
    private val seed = request.getLong("seed").also { require(it in 0..Int.MAX_VALUE) }.toInt()
    private val length =
        request.getLong("plaintextBytes").also { require(it in 0..115_200_000) }.toInt()
    private val directory = File(context.filesDir, "campaign/$attempt")
    private val stateFile = File(directory, "state.json")
    private val root =
        File(context.noBackupFilesDir, "poc-recovery/v1/runs/${run.toCanonicalString()}")
    private val quarantineRoot =
        File(context.noBackupFilesDir, "poc-recovery/v1/quarantine/${run.toCanonicalString()}")
    private val stream = File(root, "stream/stream.ct")
    private var captureOnly = false
    private var capturedRecovery: JSONObject? = null
    private var lastCheckpointAuthentication: RecoveryStreamingCheckpointAuthentication? = null
    private var lastStreamingEvent: RecoveryStreamingEvidenceEvent? = null
    private val case = request.optString("caseId")
    private val stratum = if (case == "EVT-01") "K10" else request.optString("stratumId")
    private val supportedFaults =
        setOf(
            "KEY-01",
            "KEY-04",
            "KEY-07",
            "KCF-01",
            "KCF-02",
            "KCF-03",
            "KCF-04",
            "KCF-05",
            "KCF-06",
            "KCF-07",
            "IDE-01",
            "IDE-02",
            "EVT-01",
            "QUA-02",
            "QUA-03",
            "RBK-01",
            "RBK-02",
            "SPL-01",
            "SPL-03",
            "SPL-04",
            "TRU-01",
            "CLN-01",
            "CLN-02",
            "CLN-03",
        ) +
            RecoveryCampaignArtifactFaults.cases +
            RecoveryCampaignPublicationFaults.cases +
            (1..6).map { "KCB-%02d".format(it) }
    private val supportedStrata =
        mapOf(
            "K01" to "PLAINTEXT-CALL-RETURN",
            "K02" to "CIPHERTEXT-BEFORE-WRITE",
            "K03" to "PLAINTEXT-CALL-RETURN",
            "K04" to "SCHK-01",
            "K05" to "SCHK-06",
            "K06" to "SCHK-07",
            "K07" to "SCHK-08",
            "K08" to "SCHK-10",
            "K09" to "SCHK-11",
            "K10" to "SCHK-12",
            "K11" to "PLAINTEXT-CALL-START",
            "K12" to "PERSISTED-RECOVERY",
        )

    @Suppress("CyclomaticComplexMethod") // Closed operation/candidate admission matrix.
    fun execute() {
        require(request.getString("schema") == "DORA_RECOVERY_CAMPAIGN_REQUEST_V1")
        require(request.getString("protocolId") == "poc-recovery-protocol-stage0-v0.8")
        require(
            RecoveryCampaignFixture.digest(seed, length).toLowercaseHex() ==
                request.getString("fixtureSha256")
        )
        require(!(candidate == RecoveryCandidate.STREAM && case in setOf("COR-04", "COR-05"))) {
            "Microfile-only recipe"
        }
        require(
            case in supportedFaults ||
                (candidate == RecoveryCandidate.STREAM && stratum in supportedStrata) ||
                (candidate == RecoveryCandidate.MICROFILE &&
                    stratum in (1..12).map { "K%02d".format(it) })
        ) {
            "Recipe implementation not admitted"
        }
        if (operation != "PREPARE" && operation != "WRITE_UNTIL_BARRIER") validateState()
        when (operation) {
            "PREPARE" -> prepare(false)
            "WRITE_UNTIL_BARRIER" ->
                if (case == "CLN-01") {
                    validateState()
                    fault()
                } else if (case in setOf("QUA-02", "QUA-03", "IDE-02")) interruptRecovery()
                else prepare(true)
            "FAULT" -> fault()
            "RECOVER" -> recover()
            "CLEANUP" -> cleanup()
            else -> error("Unsupported campaign operation")
        }
    }

    fun emit(type: String, details: JSONObject = JSONObject()) {
        if (captureOnly && type == "RESULT") {
            capturedRecovery = details
            return
        }
        if (type == "RESULT" && operation in setOf("PREPARE", "RECOVER")) addRetention(details)
        details
            .put("schema", "DORA_RECOVERY_CAMPAIGN_EVENT_V1")
            .put("eventType", type)
            .put("attemptId", attempt)
            .put("operation", operation)
            .put("candidateId", candidate.contractId)
            .put("runId", runHex)
            .put("pid", Process.myPid())
        InstrumentationRegistry.getInstrumentation()
            .sendStatus(
                0,
                Bundle().apply {
                    putString("stream", "DORA_RECOVERY_CAMPAIGN_EVENT $details\n")
                },
            )
    }

    @Suppress(
        "CyclomaticComplexMethod",
        "NestedBlockDepth",
        "ComplexCondition",
    ) // Explicit protocol barriers.
    private fun prepare(kill: Boolean) {
        check(!directory.exists() && !root.exists()) { "Fresh immutable attempt required" }
        check(directory.mkdirs())
        saveState(JSONObject().put("acceptedEnd", 0).put("committedEnd", 0))
        if (case.startsWith("KCB-")) {
            check(kill)
            RecoveryCampaignBootstrapFixtures.execute(context, confirmation, case) { barrier(0UL) }
            error("Bootstrap did not reach requested external barrier")
        }
        if (case == "KCF-06" || case == "KEY-07") {
            if (case == "KCF-06")
                RecoveryCampaignKeyConfirmationFaults.prepareCollision(
                    context,
                    confirmation,
                    request.getString("mutationVariant"),
                )
            else AndroidKeystoreKmsClient.generateNewAeadKey(confirmation.canonicalAlias)
            val before = allArtifacts()
            val result =
                AndroidRecoveryKeyBootstrap.controller(context) {
                        error("Collision must not publish evidence")
                    }
                    .bootstrap(confirmation)
            check(
                result is BootstrapResult.Rejected &&
                    result.classification == KeyRecoveryClassification.KEY_REF_COLLISION
            )
            check(before == allArtifacts())
            saveState(
                state().put("collisionObserved", true).put("collisionArtifacts", JSONObject(before))
            )
            emit("RESULT", JSONObject().put("prepared", true).put("acceptedEnd", 0))
            return
        }
        val boot = AndroidRecoveryKeyBootstrap.controller(context) {}.bootstrap(confirmation)
        check(boot is BootstrapResult.Committed) { "Real bootstrap did not commit" }
        if (candidate == RecoveryCandidate.MICROFILE) {
            val k12 = kill && stratum == "K12"
            require(if (k12) length == 360000 else length > 0 && length % 160000 == 0) {
                "Microfile fixture must contain complete five-second units"
            }
            var accepted = 0
            var committed = 0
            var armed = false
            val publisher =
                RecoveryCampaignMicrofilePublisher.create(context) { step ->
                    if (kill && armed && (step == stratum || stratum == "K11" && step == "K01")) {
                        saveState(
                            JSONObject()
                                .put("acceptedEnd", accepted)
                                .put("committedEnd", if (step == "K10") accepted else committed)
                        )
                        barrier(accepted.toULong())
                    }
                }
            val publishedLength =
                if (k12) 320000 else if (case == "TRU-01") length - 160000 else length
            for (start in 0 until publishedLength step 160000) {
                if (kill && stratum == "K11" && start == length - 160000) acknowledgeEvent()
                val input = RecoveryCampaignFixture.bytes(seed, start, 160000)
                accepted = start + input.size
                armed = accepted == length
                val result =
                    publisher.publish(
                        MicrofilePublicationInput(
                            confirmation,
                            boot.publicationCapability,
                            input,
                            5UL,
                        )
                    )
                check(result is MicrofilePublicationResult.Committed && result.evidenceEmitted)
                committed = accepted
                if (case.startsWith("RBK-") && committed == 320000) {
                    saveState(
                        JSONObject().put("acceptedEnd", accepted).put("committedEnd", committed)
                    )
                    RecoveryCampaignRunSnapshot.capture(
                        context,
                        confirmation,
                        File(directory, "older"),
                    )
                    File(directory, "older/controller-state.json").writeText(state().toString())
                }
            }
            if (k12 || case == "TRU-01") {
                // Exact seed contains one incomplete ciphertext temporary, never a final unit.
                val previous =
                    AndroidRecoveryMicrofileJournal(context)
                        .loadSnapshot(run)
                        .publications
                        .last()
                        .publicationSha256
                val crypto = AndroidRecoveryMicrofileCrypto()
                val tailPlaintextBytes = length - publishedLength
                val aad =
                    KeyEnvelopeAad(
                        candidate,
                        run,
                        KeyEnvelopeTargetKind.MICROFILE,
                        3UL,
                        2UL,
                        publishedLength.toULong(),
                        length.toULong(),
                        5UL,
                        previous,
                    )
                val prepared = crypto.createKeyset(aad, crypto.openRunAead(run))
                val ciphertext =
                    crypto.encryptMicrofile(
                        prepared.keyset,
                        RecoveryCampaignFixture.bytes(seed, publishedLength, tailPlaintextBytes),
                        MicrofileAad(
                            candidate,
                            run,
                            3UL,
                            2UL,
                            publishedLength.toULong(),
                            length.toULong(),
                            5UL,
                            previous,
                        ),
                    )
                val storage = AndroidOsRecoveryCandidateStorage(context)
                val handle = storage.openExclusiveTemp(run, "units/u-0000000002.ct.tmp")
                val writtenBytes = if (k12) 40000 else ciphertext.size
                try {
                    var offset = 0
                    while (offset < writtenBytes) {
                        val written =
                            storage.write(handle, ciphertext, offset, writtenBytes - offset)
                        check(written > 0)
                        offset += written
                    }
                    storage.fsync(handle)
                } finally {
                    storage.close(handle)
                }
                saveState(
                    JSONObject().put("acceptedEnd", length).put("committedEnd", publishedLength)
                )
                if (k12) {
                    RecoveryCampaignMicrofileRecovery.reconcile(context, run) {
                        if (it == "Q01") barrier(360000UL)
                    }
                    error("K12 quarantine persistence barrier was not observed")
                }
            }
            check(!kill) { "Requested public barrier was not reached" }
            saveState(JSONObject().put("acceptedEnd", length).put("committedEnd", publishedLength))
        } else {
            if (kill && stratum == "K12") {
                require(length == 8137 && request.getString("seedId") == "K12-STREAM-V0.7")
                AndroidRecoveryStreamingPublication.open(
                        context,
                        confirmation,
                        boot.publicationCapability,
                    ) {}
                    .use { writer ->
                        writer.write(RecoveryCampaignFixture.bytes(seed, 0, 4080))
                        writer.write(RecoveryCampaignFixture.bytes(seed, 4080, 4057))
                        val checkpoint = writer.checkpoint()
                        check(
                            checkpoint.durableNonFinalSegmentCount == 2UL &&
                                checkpoint.committedEnd == 4056UL
                        )
                        freezeStream(writer.acceptedEnd, checkpoint)
                    }
                FileOutputStream(stream, true).use {
                    it.write(0x5a)
                    it.fd.sync()
                }
                check(stream.length() == 8193L)
                recoverStream(File(directory, "recovered.pcm")) { event ->
                    check(
                        event.persistedDecision == StreamDecision.VALID &&
                            event.recoveredEnd == 8136UL &&
                            event.rangeStart == 8192UL &&
                            event.rangeEnd == 8193UL
                    )
                    barrier(8137UL)
                }
                error("K12 persistence barrier was not observed")
            }
            require(length >= 16320)
            var armed = false
            var previous: RecoveryStreamingCheckpointRow? = null
            var ciphertextBeforeActiveCall = 0UL
            lateinit var writer: RecoveryStreamingPublicationWriter
            writer =
                AndroidRecoveryStreamingPublication.open(
                    context,
                    confirmation,
                    boot.publicationCapability,
                ) { step ->
                    if (kill && armed && step == supportedStrata.getValue(stratum)) {
                        if (stratum == "K01")
                            check(writer.emittedCiphertextBytes == ciphertextBeforeActiveCall) {
                                "K01 must pause before a new segment emission"
                            }
                        if (stratum == "K03")
                            check(
                                writer.emittedCiphertextBytes >= ciphertextBeforeActiveCall + 4096UL
                            ) {
                                "K03 requires a complete new ciphertext segment"
                            }
                        val checkpoint =
                            if (stratum == "K10") checkpoint() else requireNotNull(previous)
                        freezeStream(writer.acceptedEnd, checkpoint)
                        barrier(writer.acceptedEnd)
                    }
                }
            writer.use {
                var start = 0
                val activeCallStart = length - if (stratum == "K01") 1 else 4080
                while (start < length) {
                    val remaining =
                        if ((kill || case == "TRU-01") && start < activeCallStart)
                            activeCallStart - start
                        else length - start
                    val count = minOf(4080, remaining)
                    armed = kill && start == activeCallStart
                    if (armed) ciphertextBeforeActiveCall = it.emittedCiphertextBytes
                    if (armed && stratum == "K11") acknowledgeEvent()
                    it.write(RecoveryCampaignFixture.bytes(seed, start, count))
                    start += count
                    if (!(case == "TRU-01" && start == length)) previous = it.checkpoint()
                    if (
                        case.startsWith("RBK-") &&
                            start >= length / 2 &&
                            !File(directory, "older").exists()
                    ) {
                        freezeStream(it.acceptedEnd, requireNotNull(previous))
                        RecoveryCampaignRunSnapshot.capture(
                            context,
                            confirmation,
                            File(directory, "older"),
                        )
                        File(directory, "older/controller-state.json").writeText(state().toString())
                    }
                }
                check(!kill) { "Requested public barrier was not reached" }
                freezeStream(it.acceptedEnd, requireNotNull(previous))
            }
        }
        val prepared =
            JSONObject()
                .put("prepared", true)
                .put("acceptedEnd", length)
                .put(
                    "supportedCapabilities",
                    JSONArray(
                        supportedFaults.filterNot {
                            candidate == RecoveryCandidate.STREAM && it in setOf("COR-04", "COR-05")
                        }
                    ),
                )
        if (case.startsWith("CLN-")) {
            val mutation =
                RecoveryCampaignArtifactFaults.mutate(context, confirmation, "QUA-01", "DEFAULT")
            saveState(state().put("artifactMutationFacts", mutation))
            captureOnly = true
            try {
                recover()
            } finally {
                captureOnly = false
            }
            val baseline = requireNotNull(capturedRecovery)
            FileOutputStream(File(directory, "pre-cleanup-result.json")).use {
                it.write(baseline.toString().toByteArray(Charsets.UTF_8))
                it.fd.sync()
            }
            val plan = RecoveryCampaignCleanupFaults.prepare(context, confirmation, attempt)
            saveState(state().put("cleanupPlanSha256", plan.getString("cleanupPlanSha256")))
            baseline
                .put("cleanupPlanSha256", plan.getString("cleanupPlanSha256"))
                .put("prepared", true)
            baseline.put(
                "preCleanupOutcome",
                JSONObject(File(directory, "pre-cleanup-result.json").readText(Charsets.UTF_8)),
            )
            emit("RESULT", baseline)
            return
        }
        if (case.startsWith("RBK-"))
            prepared.put(
                "externalAnchor",
                currentPublication().put("controllerEventAcknowledged", false),
            )
        if (
            candidate == RecoveryCandidate.STREAM &&
                case in setOf("KEY-05", "KEY-06", "PAR-01", "COR-06")
        )
            prepared.put("recoveryCheckpointSelection", checkpointSelection(checkpoint()))
        emit("RESULT", prepared)
    }

    private fun barrier(accepted: ULong): Nothing {
        emit(
            "BARRIER",
            JSONObject()
                .put("publicBarrier", request.optString("publicBarrier", case))
                .put("sequence", 1)
                .put("acceptedEnd", accepted.toLong())
                .put("committedEnd", state().getLong("committedEnd"))
                .put("gracefulFinalizeCompleted", false),
        )
        // This bounded wait is a public host handshake. Timeout is a failed attempt, never SIGKILL.
        val deadline = SystemClock.elapsedRealtime() + 120000
        while (SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
        error("External controller did not kill the paused process")
    }

    private fun acknowledgeEvent() {
        val relative = "campaign/$attempt/ack-1"
        val file = File(context.filesDir, relative)
        check(!file.exists())
        emit(
            "READY",
            JSONObject()
                .put("eventAcknowledgementRequired", true)
                .put("sequence", 1)
                .put("acknowledgementRelativePath", relative),
        )
        val deadline = SystemClock.elapsedRealtime() + 120000
        while (!file.exists() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
        check(file.isFile && file.length() == 0L) {
            "Durable external event acknowledgement missing"
        }
        check(file.delete())
    }

    private fun checkpoint(): RecoveryStreamingCheckpointRow =
        (AndroidRecoveryStreamingJournal(context).checkpointChain(run)
                as RecoveryStreamingJournalReadResult.Value)
            .value
            .last()

    private fun freezeStream(accepted: ULong, checkpoint: RecoveryStreamingCheckpointRow) {
        saveState(
            JSONObject()
                .put("acceptedEnd", accepted.toLong())
                .put("committedEnd", checkpoint.committedEnd.toLong())
                .put("checkpointGeneration", checkpoint.generation.toLong())
                .put("checkpointIdentity", checkpoint.checkpointIdentity.toLowercaseHex())
                .put("checkpointPrefixBytes", checkpoint.streamCiphertextPrefixBytes.toLong())
                .put("sourceBytes", stream.length())
                .put("sourceSha256", digest(stream))
        )
    }

    @Suppress("CyclomaticComplexMethod") // One explicit branch for each admitted fault recipe.
    private fun fault() {
        check(!state().optBoolean("faultInjected")) { "Fault may be injected once only" }
        val file = File(root, "key-confirmation/run.kc")
        when (case) {
            "KEY-01" -> deleteAlias()
            "KEY-04",
            "KCF-05" -> {
                val snapshot =
                    AndroidRecoveryReconciliationSource(context)
                        .loadConfirmation(run)
                        .copy(expected = confirmation)
                check(
                    RecoveryKeyConfirmationController().evaluate(snapshot)
                        is ConfirmationResult.Validated
                )
                val before = digest(file)
                deleteAlias()
                // Fault injection replaces an alias before recovery, never inside recovery access.
                AndroidKeystoreKmsClient.generateNewAeadKey(confirmation.canonicalAlias)
                check(digest(file) == before)
                val after =
                    AndroidRecoveryReconciliationSource(context)
                        .loadConfirmation(run)
                        .copy(expected = confirmation)
                check(after.durableRow == snapshot.durableRow)
                saveState(
                    state()
                        .put(
                            "controlledReplacementIdentity",
                            requireNotNull(snapshot.durableRow).ciphertextSha256.toLowercaseHex(),
                        )
                )
            }
            "KCF-01" -> check(file.delete())
            "KCF-02" ->
                java.io.RandomAccessFile(file, "rw").use {
                    it.setLength(it.length() - 1)
                    it.fd.sync()
                }
            "KCF-03" ->
                java.io.RandomAccessFile(file, "rw").use {
                    val b = it.read()
                    it.seek(0)
                    it.write(b xor 1)
                    it.fd.sync()
                }
            "KCF-04",
            "KCF-07" -> {
                val variant = if (case == "KCF-04") case else request.getString("mutationVariant")
                val facts =
                    RecoveryCampaignKeyConfirmationFaults.mutate(context, confirmation, variant)
                saveState(state().put("confirmationMutationFacts", facts))
            }
            "IDE-01",
            "KCF-06",
            "KEY-07" -> Unit
            "QUA-02",
            "QUA-03",
            "IDE-02" -> {
                val facts =
                    RecoveryCampaignArtifactFaults.mutate(
                        context,
                        confirmation,
                        "QUA-01",
                        "DEFAULT",
                    )
                saveState(state().put("artifactMutationFacts", facts))
            }
            "RBK-01",
            "RBK-02" -> rollbackFault()
            "SPL-01",
            "SPL-03",
            "SPL-04",
            "TRU-01" -> splitOrTailFault()
            "CLN-01",
            "CLN-02",
            "CLN-03" -> {
                verifyHostRetention(allowPreparedCleanup = true)
                val receipt = request.getJSONObject("hostRetentionReceipt")
                saveState(state().put("cleanupAdmissionReceipt", receipt))
                val facts =
                    RecoveryCampaignCleanupFaults.apply(
                        context,
                        confirmation,
                        attempt,
                        case,
                        state().getString("cleanupPlanSha256"),
                        receipt.getString("hostReceiptSha256"),
                        false,
                    ) {
                        barrier(state().getLong("acceptedEnd").toULong())
                    }
                saveState(state().put("cleanupFacts", facts))
            }
            else -> {
                val facts =
                    if (case in RecoveryCampaignPublicationFaults.cases)
                        RecoveryCampaignPublicationFaults.mutate(
                            context,
                            confirmation,
                            case,
                            request.getString("mutationVariant"),
                        )
                    else
                        RecoveryCampaignArtifactFaults.mutate(
                            context,
                            confirmation,
                            case,
                            request.getString("mutationVariant"),
                        )
                saveState(state().put("artifactMutationFacts", facts))
            }
        }
        saveState(state().put("faultInjected", true))
        val result = JSONObject().put("faultInjected", true)
        if (
            candidate == RecoveryCandidate.STREAM &&
                case in setOf("KEY-05", "KEY-06", "PAR-01", "COR-06")
        ) {
            val selection = checkpointSelection(checkpoint())
            saveState(state().put("recoveryCheckpointSelection", selection))
            result.put("recoveryCheckpointSelection", selection)
        }
        result
            .put(
                "artifactMutationFacts",
                state().optJSONObject("artifactMutationFacts") ?: JSONObject.NULL,
            )
            .put(
                "confirmationMutationFacts",
                state().optJSONObject("confirmationMutationFacts") ?: JSONObject.NULL,
            )
        emit("RESULT", result)
    }

    @Suppress(
        "CyclomaticComplexMethod",
        "NestedBlockDepth",
        "ComplexCondition",
    ) // Preserve typed case-specific evidence.
    private fun recover() {
        if (case == "CLN-01" && File(directory, "cleanup-retention.json").exists()) {
            val receipt = state().getJSONObject("cleanupAdmissionReceipt")
            val facts =
                RecoveryCampaignCleanupFaults.apply(
                    context,
                    confirmation,
                    attempt,
                    case,
                    state().getString("cleanupPlanSha256"),
                    receipt.getString("hostReceiptSha256"),
                    true,
                )
            val baseline =
                JSONObject(File(directory, "pre-cleanup-result.json").readText(Charsets.UTF_8))
            emit(
                "RESULT",
                baseline
                    .put("preCleanupOutcomeUsed", true)
                    .put("cleanupFacts", facts)
                    .put("caseOracleSatisfied", facts.getBoolean("cleanupComplete")),
            )
            return
        }
        if (case.startsWith("KCB-") || case == "KCF-06" || case == "KEY-07") {
            recoverBootstrap()
            return
        }
        val before = snapshot()
        val artifactsBefore = allArtifacts().values.sorted()
        val committedRowsBefore = committedRowIdentities()
        val rollbackBefore =
            if (case.startsWith("RBK-")) state().getJSONObject("rollbackObserved") else null
        val accepted = state().getLong("acceptedEnd").toULong()
        val committed = state().getLong("committedEnd").toULong()
        val output = File(directory, "recovered.pcm")
        var keySnapshot =
            AndroidRecoveryReconciliationSource(context)
                .loadConfirmation(run)
                .copy(expected = confirmation)
        if (case == "KEY-04" || case == "KCF-05") {
            check(state().getBoolean("faultInjected"))
            val row = requireNotNull(keySnapshot.durableRow)
            check(
                row.ciphertextSha256.toLowercaseHex() ==
                    state().getString("controlledReplacementIdentity")
            )
            keySnapshot =
                keySnapshot.copy(
                    controlledReplacement =
                        ControlledKey04Replacement(attempt, confirmation.canonicalAlias, row)
                )
        }
        val key = RecoveryKeyConfirmationController().evaluate(keySnapshot)
        var classification: String
        var recovered = 0UL
        var authenticated = false
        var receipt = ""
        var underlyingDiagnostic: String? = null
        var microfileControllerObservation: JSONObject? = null
        var manifestRejectionObservations = JSONArray()
        var rejectedMicrofileEnvelopeBeforePayload = false
        if (
            key is ConfirmationResult.Rejected ||
                case == "KCF-04" &&
                    key is ConfirmationResult.Unclassified &&
                    key.diagnostic == ConfirmationDiagnostic.AUTHENTICATION_WITHOUT_KEY04_PROVENANCE
        ) {
            if (key is ConfirmationResult.Rejected) {
                classification = key.classification.name
                underlyingDiagnostic = key.phase.name
            } else {
                val facts = state().getJSONObject("confirmationMutationFacts")
                check(facts.getString("recipe") == "KCF-04")
                check(
                    facts.getBoolean("sourceCandidateAuthenticated") &&
                        facts.getBoolean("targetAadAuthenticationFailed")
                )
                check(facts.getBoolean("storedCiphertextIdentityUpdatedAndReadBack"))
                check(
                    facts.getString("replacementConfirmationSha256") ==
                        requireNotNull(keySnapshot.durableRow).ciphertextSha256.toLowercaseHex()
                )
                underlyingDiagnostic = (key as ConfirmationResult.Unclassified).diagnostic.name
                classification = "KEY_UNAVAILABLE_KEY_MISMATCH"
            }
            FileOutputStream(output, false).use { it.fd.sync() }
        } else {
            check(key is ConfirmationResult.Validated) {
                "Confirmation was not classified or validated"
            }
            if (
                candidate == RecoveryCandidate.STREAM &&
                    (case in setOf("QUA-01", "QUA-02", "QUA-03", "SPL-05", "IDE-02") ||
                        case.startsWith("CLN-"))
            )
                quarantineOrphan()
            if (candidate == RecoveryCandidate.MICROFILE) {
                val publicationsBefore =
                    AndroidRecoveryMicrofileJournal(context).loadSnapshot(run).publications
                val result = RecoveryCampaignMicrofileRecovery.reconcile(context, run)
                val prefix =
                    when (result) {
                        is MicrofileReconciliationResult.AuthenticatedPrefix -> result.prefix
                        is MicrofileReconciliationResult.PartialPrefix -> result.prefix
                        else -> null
                    }
                classification =
                    when (result) {
                        is MicrofileReconciliationResult.AuthenticatedPrefix -> "VALID"
                        is MicrofileReconciliationResult.PartialPrefix ->
                            result.classification?.name ?: result.diagnostic.name
                        is MicrofileReconciliationResult.NoAuthenticatedPrefix ->
                            result.classification?.name ?: result.diagnostic?.name ?: "UNCLASSIFIED"
                        else -> "CONCURRENT_WRITER"
                    }
                underlyingDiagnostic = classification
                microfileControllerObservation =
                    JSONObject()
                        .put("resultType", result.javaClass.simpleName)
                        .put("classification", classification)
                val rejections =
                    when (result) {
                        is MicrofileReconciliationResult.AuthenticatedPrefix ->
                            result.manifestRejections
                        is MicrofileReconciliationResult.PartialPrefix -> result.manifestRejections
                        is MicrofileReconciliationResult.NoAuthenticatedPrefix ->
                            result.manifestRejections
                        else -> emptyList()
                    }
                manifestRejectionObservations =
                    JSONArray(
                        rejections.map { rejection ->
                            JSONObject()
                                .put("generation", rejection.generation.toLong())
                                .put(
                                    "classification",
                                    rejection.classification?.name ?: JSONObject.NULL,
                                )
                                .put("stage", rejection.diagnostic.stage.name)
                                .put("category", rejection.diagnostic.category.name)
                                .put("failureType", rejection.diagnostic.type)
                        }
                    )
                val mutation = state().optJSONObject("artifactMutationFacts")
                val target =
                    if (case == "KEY-02" && mutation != null) {
                        publicationsBefore.singleOrNull {
                            it.keyEnvelopeRelativeName == mutation.getString("relativeName")
                        }
                    } else if (case in setOf("KEY-05", "KEY-06", "PAR-01") && mutation != null) {
                        publicationsBefore.singleOrNull {
                            it.generation.toLong() == mutation.getLong("generation") &&
                                it.publicationRelativeName == mutation.getString("targetName")
                        }
                    } else null
                val targetRejection = target?.let { row ->
                    rejections.singleOrNull { it.generation == row.generation }
                }
                if (targetRejection != null && mutation != null) {
                    val failure = targetRejection.diagnostic
                    microfileControllerObservation.put(
                        "targetRejectedGeneration",
                        targetRejection.generation.toLong(),
                    )
                    if (
                        case in setOf("KEY-02", "KEY-05", "KEY-06") &&
                            targetRejection.classification != null
                    ) {
                        // An earlier returned prefix does not erase the actual latest-target key
                        // rejection.
                        classification = targetRejection.classification.name
                        rejectedMicrofileEnvelopeBeforePayload =
                            failure.stage in
                                setOf(
                                    RecoveryFailureStage.ENVELOPE_BINDING,
                                    RecoveryFailureStage.ENVELOPE_PARSE,
                                )
                        microfileControllerObservation.put(
                            "campaignClassificationBasis",
                            "ACTUAL_TARGET_ENVELOPE_REJECTION",
                        )
                    }
                    if (
                        case == "PAR-01" &&
                            mutation.optBoolean("faultPublicationDecryptSucceeded") &&
                            failure.category == RecoveryFailureCategory.STRUCTURAL
                    ) {
                        val plaintextBytes = mutation.getInt("faultPlaintextBytes")
                        val injectedVariant = mutation.getString("mutationVariant")
                        val parserRejected =
                            failure.stage in
                                setOf(
                                    RecoveryFailureStage.MANIFEST_PLAINTEXT,
                                    RecoveryFailureStage.MANIFEST_SEMANTICS,
                                )
                        val boundedSourceRejected =
                            failure.stage == RecoveryFailureStage.ARTIFACT_PATH
                        if (
                            plaintextBytes > RecoveryContract.MAX_MANIFEST_PLAINTEXT_BYTES &&
                                (parserRejected || boundedSourceRejected)
                        ) {
                            classification = "OVERSIZED_MANIFEST"
                            microfileControllerObservation.put(
                                "campaignClassificationBasis",
                                "ACTUAL_TARGET_SIZE_REJECTION_AND_OBSERVED_OVERSIZED_PLAINTEXT",
                            )
                        } else if (parserRejected) {
                            classification =
                                if (injectedVariant in setOf("UNSAFE_PATH", "TRAVERSAL_PATH"))
                                    "UNSAFE_PATH"
                                else "MALFORMED_MANIFEST"
                            microfileControllerObservation.put(
                                "campaignClassificationBasis",
                                "ACTUAL_TARGET_PARSER_REJECTION_AND_AUTHENTICATED_INJECTION",
                            )
                        }
                    }
                }
                val bytes = prefix?.plaintextSnapshot() ?: byteArrayOf()
                FileOutputStream(output, false).use {
                    it.write(bytes)
                    it.fd.sync()
                }
                recovered = bytes.size.toULong()
                authenticated = prefix != null
                receipt = prefix?.orderedRowsDigestOrNull()?.toLowercaseHex() ?: ""
            } else {
                var event: RecoveryStreamingEvidenceEvent? = null
                val result = recoverStream(output) { event = it }
                if (result is RecoveryStreamingReconciliationResult.PersistedValid) {
                    classification = "VALID"
                    recovered = result.recoveredEnd
                    authenticated = true
                    receipt = result.receipt.outcomeId.toLowercaseHex()
                    check(output.length().toULong() == recovered)
                } else {
                    classification =
                        event?.diagnosticClassification?.name
                            ?: event?.classification?.name
                            ?: result.javaClass.simpleName
                    underlyingDiagnostic = classification
                    if (
                        case in setOf("KEY-02", "KEY-03") &&
                            !File(
                                    root,
                                    state()
                                        .getJSONObject("artifactMutationFacts")
                                        .getString("relativeName"),
                                )
                                .exists()
                    )
                        classification = "KEY_UNAVAILABLE"
                    if (
                        case == "KEY-05" &&
                            lastCheckpointAuthentication ==
                                RecoveryStreamingCheckpointAuthentication.Structural
                    )
                        classification = "CORRUPT_KEY_ENVELOPE"
                    if (
                        case == "KEY-06" &&
                            lastCheckpointAuthentication ==
                                RecoveryStreamingCheckpointAuthentication.Rejected
                    )
                        classification = "KEY_ENVELOPE_AUTH_FAILURE"
                    if (case == "PAR-01") {
                        if (
                            classification.contains("UNSAFE") ||
                                lastCheckpointAuthentication ==
                                    RecoveryStreamingCheckpointAuthentication.UnsafePath
                        )
                            classification = "UNSAFE_PATH"
                        else if (
                            lastCheckpointAuthentication ==
                                RecoveryStreamingCheckpointAuthentication.Structural &&
                                state()
                                    .getJSONObject("artifactMutationFacts")
                                    .optBoolean("faultPublicationDecryptSucceeded")
                        )
                            classification =
                                if (request.getString("mutationVariant") == "OVERSIZED")
                                    "OVERSIZED_MANIFEST"
                                else "MALFORMED_MANIFEST"
                    }
                    receipt = event?.outcomeId?.toLowercaseHex() ?: ""
                    FileOutputStream(output, false).use { it.fd.sync() }
                }
            }
        }
        val sourceUnchanged = before == snapshot()
        var rollbackObservation: JSONObject? = null
        if (case.startsWith("RBK-")) {
            val anchor = request.getJSONObject("externalAnchor")
            val observed = requireNotNull(rollbackBefore)
            check(anchor.getLong("committedEnd") == committed.toLong())
            val detected =
                observed.getLong("generation") < anchor.getLong("generation") ||
                    observed.getString("publicationCiphertextSha256") !=
                        anchor.getString("publicationCiphertextSha256")
            underlyingDiagnostic = classification
            if (detected) classification = "ROLLBACK_DETECTED"
            rollbackObservation = observed.put("externalRollbackDetected", detected)
        }
        if (receipt.isEmpty())
            receipt =
                Sha256Value.calculate(
                        "$runHex|$classification|$committed".toByteArray(Charsets.US_ASCII)
                    )
                    .toLowercaseHex()
        val expected =
            when (case) {
                "KEY-01" -> "KEY_UNAVAILABLE"
                "KEY-04",
                "KCF-04",
                "KCF-05" -> "KEY_UNAVAILABLE_KEY_MISMATCH"
                "KCF-01" -> "KEY_CONFIRMATION_MISSING"
                "KCF-02",
                "KCF-03",
                "KCF-07" -> "CORRUPT_KEY_CONFIRMATION"
                "KEY-02",
                "KEY-03" -> "KEY_UNAVAILABLE"
                "KEY-05" -> "CORRUPT_KEY_ENVELOPE"
                "KEY-06" -> "KEY_ENVELOPE_AUTH_FAILURE"
                "CLN-03" -> if (state().optBoolean("faultInjected")) "KEY_UNAVAILABLE" else "VALID"
                else -> "VALID"
            }
        val caseSatisfied =
            when (case) {
                "COR-01",
                "COR-04",
                "TRU-02",
                "SPL-02" ->
                    recovered <=
                        state()
                            .getJSONObject("artifactMutationFacts")
                            .getLong("affectedPlaintextStart")
                            .toULong() && recovered < committed
                "COR-02" -> recovered < committed
                "COR-03",
                "COR-05",
                "COR-06" -> recovered < committed
                "PAR-01" ->
                    when (request.getString("mutationVariant")) {
                        "UNSAFE_PATH",
                        "TRAVERSAL_PATH",
                        "SYMLINK" -> classification.contains("UNSAFE")
                        "OVERSIZED" ->
                            classification.contains("OVERSIZED") ||
                                classification.contains("STRUCTURAL")
                        else ->
                            classification.contains("MALFORMED") ||
                                classification.contains("STRUCTURAL") ||
                                recovered < committed
                    }
                "TRU-03" ->
                    if (candidate == RecoveryCandidate.STREAM)
                        authenticated && recovered <= accepted
                    else recovered < committed
                "SPL-05",
                "QUA-01",
                "QUA-02",
                "QUA-03",
                "IDE-02" -> {
                    val rows = quarantineRows()
                    rows.size == 1 &&
                        rows.single().state == QuarantineIntentState.COMPLETED &&
                        recovered >= committed
                }
                "RBK-01",
                "RBK-02" -> rollbackObservation?.getBoolean("externalRollbackDetected") == true
                "SPL-01",
                "SPL-03",
                "SPL-04" -> recovered < committed
                "TRU-01" -> authenticated && recovered >= committed
                "CLN-02" ->
                    if (!state().has("cleanupFacts")) classification == "VALID"
                    else
                        state()
                            .getJSONObject("cleanupFacts")
                            .getBoolean("deletionDeniedByPlatform") && classification == "VALID"
                else ->
                    classification == expected &&
                        (expected != "VALID" || committed <= recovered && recovered <= accepted)
            }
        val intents =
            AndroidRecoveryMicrofileJournal(context).loadSnapshot(run).units.map {
                it.processingIntentId
            }
        val expectedIntents =
            if (candidate == RecoveryCandidate.MICROFILE) committed.toInt() / 160000 else 0
        emit(
            "RESULT",
            JSONObject()
                .put("acceptedEnd", accepted.toLong())
                .put("committedEnd", committed.toLong())
                .put("recoveredEnd", recovered.toLong())
                .put("authenticated", authenticated)
                .put("contiguous", authenticated || recovered == 0UL)
                .put("classification", classification)
                .put("sourceUnchanged", sourceUnchanged)
                .put(
                    "caseOracleSatisfied",
                    caseSatisfied &&
                        (candidate == RecoveryCandidate.MICROFILE &&
                            !case.startsWith("KEY-") &&
                            !case.startsWith("KCF-") || sourceUnchanged),
                )
                .put("underlyingControllerDiagnostic", underlyingDiagnostic ?: JSONObject.NULL)
                .put(
                    "microfileControllerObservation",
                    microfileControllerObservation ?: JSONObject.NULL,
                )
                .put("manifestRejections", manifestRejectionObservations)
                .put(
                    "keyAuthenticationFailureObserved",
                    key is ConfirmationResult.Rejected && key.phase == ConfirmationPhase.KEY04 ||
                        key is ConfirmationResult.Unclassified &&
                            key.diagnostic ==
                                ConfirmationDiagnostic.AUTHENTICATION_WITHOUT_KEY04_PROVENANCE,
                )
                .put(
                    "rejectedBeforeTargetDecrypt",
                    key is ConfirmationResult.Rejected &&
                        key.phase == ConfirmationPhase.STORED_IDENTITY ||
                        case == "KEY-05" &&
                            classification == "CORRUPT_KEY_ENVELOPE" &&
                            (candidate == RecoveryCandidate.STREAM &&
                                lastCheckpointAuthentication ==
                                    RecoveryStreamingCheckpointAuthentication.Structural ||
                                rejectedMicrofileEnvelopeBeforePayload),
                )
                .put("implicitCommitCount", (committedRowIdentities() - committedRowsBefore).size)
                .put("retainedForReconciliation", artifactsBefore == allArtifacts().values.sorted())
                .put(
                    "tailClassification",
                    if (case == "TRU-01")
                        lastStreamingEvent?.diagnosticClassification?.name
                            ?: lastStreamingEvent?.classification?.name
                            ?: if (quarantineRows().isNotEmpty()) "QUARANTINED_UNCOMMITTED_TEMP"
                            else JSONObject.NULL
                    else JSONObject.NULL,
                )
                .put("rollbackObservation", rollbackObservation ?: JSONObject.NULL)
                .put("cleanupFacts", state().optJSONObject("cleanupFacts") ?: JSONObject.NULL)
                .put(
                    "checkpointAuthenticationObservation",
                    lastCheckpointAuthentication?.javaClass?.simpleName ?: JSONObject.NULL,
                )
                .put(
                    "artifactMutationFacts",
                    state().optJSONObject("artifactMutationFacts") ?: JSONObject.NULL,
                )
                .put(
                    "confirmationMutationFacts",
                    state().optJSONObject("confirmationMutationFacts") ?: JSONObject.NULL,
                )
                .put("quarantineObservation", quarantineObservation())
                .put("controllerEventGapObserved", case == "EVT-01" && committed > 0UL)
                .put("rangeStart", lastStreamingEvent?.rangeStart?.toLong() ?: JSONObject.NULL)
                .put("rangeEnd", lastStreamingEvent?.rangeEnd?.toLong() ?: JSONObject.NULL)
                .put("boundaryResult", lastStreamingEvent?.boundaryResult?.name ?: JSONObject.NULL)
                .put("rangeCertainty", lastStreamingEvent?.rangeCertainty?.name ?: JSONObject.NULL)
                .put("sourceBytes", state().optLong("sourceBytes", 0))
                .put("currentSourceBytes", regularStreamBytes())
                .put("preFaultSourceBytes", state().optLong("sourceBytes", 0))
                .put("observedSourceBytes", regularStreamBytes())
                .put("processingIntentCount", intents.size)
                .put("duplicateProcessingIntents", intents.size - intents.distinct().size)
                .put("missingProcessingIntents", maxOf(0, expectedIntents - intents.size))
                .put("microphoneOpens", 0)
                .put("unsafePathOpens", 0)
                .put("receiptIdentity", receipt)
                .put("returnedPrefixArtifact", "campaign/$attempt/recovered.pcm"),
        )
    }

    private fun recoverStream(
        output: File,
        evidence: (RecoveryStreamingEvidenceEvent) -> Unit = {},
    ): RecoveryStreamingReconciliationResult {
        val s = state()
        val accepted = s.getLong("acceptedEnd").toULong()
        val hash = RecoveryCampaignFixture.digest(seed, accepted.toInt())
        var selectedIdentity = s.getString("checkpointIdentity")
        if (s.has("recoveryCheckpointSelection")) {
            val selection = request.getJSONObject("recoveryCheckpointSelection")
            val recorded = s.getJSONObject("recoveryCheckpointSelection")
            recorded.keys().forEach { key ->
                check(selection.get(key).toString() == recorded.get(key).toString())
            }
            check(selection.getString("originalCheckpointIdentity") == selectedIdentity)
            selectedIdentity = selection.getString("checkpointIdentity")
        }
        val initial =
            RecoveryStreamingWitnessInput(
                run,
                s.getLong("checkpointGeneration").toULong(),
                Sha256Value.fromLowercaseHex(selectedIdentity),
                s.getLong("checkpointPrefixBytes").toULong(),
                s.getLong("committedEnd").toULong(),
                RecoveryStreamingIdentity.oracle(accepted, hash, run),
                accepted,
                hash,
                s.getLong("sourceBytes").toULong(),
                Sha256Value.fromLowercaseHex(s.getString("sourceSha256")),
                null,
            )
        val witness =
            initial.copy(
                controllerSnapshotSha256 = RecoveryStreamingIdentity.controllerSnapshot(initial)
            )
        val journal = AndroidRecoveryStreamingJournal(context)
        val storage = AndroidOsRecoveryReconciliationStorage(context)
        val crypto = RecoveryStreamingTinkPrerequisiteCrypto()
        val exporting =
            RecoveryStreamingPrerequisiteCrypto { cp, envelope, ciphertext, streamEnvelope ->
                val authentication = crypto.authenticate(cp, envelope, ciphertext, streamEnvelope)
                if (authentication !is RecoveryStreamingCheckpointAuthentication.Ready)
                    authentication
                else
                    RecoveryStreamingCheckpointAuthentication.Ready(
                        RecoveryStreamingPublicStreamOpener { source, input ->
                            val delegate = authentication.publicStreamOpener.open(source, input)
                            val sink = FileOutputStream(output, false)
                            object : RecoveryStreamingIntentBuilder.RecoveryStreamingPublicRead {
                                override fun read(
                                    destination: ByteArray,
                                    offset: Int,
                                    count: Int,
                                ): Int {
                                    // Only successfully returned bytes cross the private export
                                    // boundary.
                                    val returned = delegate.read(destination, offset, count)
                                    if (returned > 0) sink.write(destination, offset, returned)
                                    return returned
                                }

                                override fun close() {
                                    try {
                                        delegate.close()
                                    } finally {
                                        try {
                                            sink.fd.sync()
                                        } finally {
                                            sink.close()
                                        }
                                    }
                                }
                            }
                        }
                    )
            }
        val authenticator =
            RecoveryStreamingCheckpointAuthenticatorAdapter(
                AndroidRecoveryStreamingPrerequisiteSource(storage::loadActiveArtifact),
                exporting,
            )
        val observedAuthenticator = RecoveryStreamingCheckpointAuthenticator { cp, input ->
            authenticator.authenticate(cp, input).also { lastCheckpointAuthentication = it }
        }
        val controller =
            RecoveryStreamingReconciliationController(
                journal,
                AndroidOsRecoveryStreamingSource(context.noBackupFilesDir, journal),
                ProcessRecoveryRunSingleWriterGuard,
                observedAuthenticator,
                RecoveryStreamingEvidenceSink {
                    lastStreamingEvent = it
                    evidence(it)
                },
            )
        return controller.recover(
            RecoveryStreamingControllerRequest(
                witness,
                RecoveryStreamingIntentBuilder.RecoveryStreamingOracle.from(
                    witness,
                    RecoveryCampaignFixture.bytes(seed, 0, accepted.toInt()),
                ),
            )
        )
    }

    private fun saveState(value: JSONObject) {
        value
            .put("attemptId", attempt)
            .put("runId", runHex)
            .put("candidateId", candidate.contractId)
            .put("seed", seed)
            .put("fixtureSha256", request.getString("fixtureSha256"))
            .put("harnessRevision", harnessRevision)
            .put("manifestSha256", manifestSha256)
        FileOutputStream(stateFile, false).use {
            it.write(value.toString().toByteArray(Charsets.UTF_8))
            it.fd.sync()
        }
    }

    private fun state() = JSONObject(stateFile.readText(Charsets.UTF_8))

    private fun validateState() {
        val s = state()
        for (field in listOf("attemptId", "runId", "candidateId", "fixtureSha256")) check(
            s.getString(field) == request.getString(field)
        )
        check(s.getInt("seed") == seed)
        check(
            s.getString("harnessRevision") == harnessRevision &&
                s.getString("manifestSha256") == manifestSha256
        )
    }

    private fun digest(file: File): String {
        require(file.length() <= 116000000)
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n == -1) break
                digest.update(buffer, 0, n)
            }
        }
        return Sha256Value.fromBytes(digest.digest()).toLowercaseHex()
    }

    private fun snapshot(): Map<String, String> =
        if (candidate == RecoveryCandidate.STREAM) {
            if (stream.exists()) {
                val stat = android.system.Os.lstat(stream.path)
                mapOf(
                    "stream/stream.ct" to
                        if (android.system.OsConstants.S_ISLNK(stat.st_mode))
                            "SYMLINK:${android.system.Os.readlink(stream.path)}"
                        else {
                            check(android.system.OsConstants.S_ISREG(stat.st_mode))
                            "${stat.st_size}:${digest(stream)}"
                        }
                )
            } else emptyMap()
        } else allArtifacts()

    private fun regularStreamBytes(): Any =
        if (candidate != RecoveryCandidate.STREAM) 0
        else
            try {
                val stat = android.system.Os.lstat(stream.path)
                if (android.system.OsConstants.S_ISREG(stat.st_mode)) stat.st_size
                else JSONObject.NULL
            } catch (error: android.system.ErrnoException) {
                if (error.errno == android.system.OsConstants.ENOENT) 0 else throw error
            }

    private fun committedRowIdentities(): Set<String> =
        if (candidate == RecoveryCandidate.MICROFILE) {
            AndroidRecoveryMicrofileJournal(context)
                .loadSnapshot(run)
                .units
                .map { it.processingIntentId.toString() }
                .toSet()
        } else {
            val rows =
                AndroidRecoveryStreamingJournal(context).checkpointChain(run)
                    as RecoveryStreamingJournalReadResult.Value
            rows.value.map { it.checkpointIdentity.toLowercaseHex() }.toSet()
        }

    private fun allArtifacts(): Map<String, String> = buildMap {
        fun visit(tree: File, file: File) {
            val stat = android.system.Os.lstat(file.path)
            val name =
                "${requireNotNull(tree.parentFile).name}/${file.relativeTo(tree).invariantSeparatorsPath}"
            when {
                android.system.OsConstants.S_ISDIR(stat.st_mode) ->
                    requireNotNull(file.listFiles()).forEach { visit(tree, it) }
                android.system.OsConstants.S_ISREG(stat.st_mode) -> put(name, digest(file))
                android.system.OsConstants.S_ISLNK(stat.st_mode) ->
                    put(name, "SYMLINK:${android.system.Os.readlink(file.path)}")
                else -> put(name, "NONREGULAR:${stat.st_mode}")
            }
        }
        listOf(root, quarantineRoot).filter { it.exists() }.forEach { visit(it, it) }
    }

    private fun quarantineOrphan(observer: (String) -> Unit = {}): QuarantineResult {
        val facts = state().getJSONObject("artifactMutationFacts")
        val name = facts.getString("relativeName")
        val role =
            if (name.startsWith("checkpoints/"))
                RecoveryQuarantineArtifactRole.CHECKPOINT_CIPHERTEXT
            else RecoveryQuarantineArtifactRole.UNKNOWN_REGULAR
        val input =
            RecoveryQuarantineIntentInput(
                candidate,
                run,
                name,
                role,
                facts.getLong("afterBytes").toULong(),
                Sha256Value.fromLowercaseHex(facts.getString("afterSha256")),
            )
        return RecoveryCampaignMicrofileRecovery.quarantineController(context, observer)
            .quarantine(
                input,
                if (name.endsWith(".tmp")) RecoveryQuarantineObservedState.TEMP_ONLY
                else RecoveryQuarantineObservedState.UNKNOWN_OR_NON_ALLOWLISTED_NAME,
                QuarantineBootstrapBinding.PRESENT,
            )
    }

    private fun quarantineRows(): List<RecoveryQuarantineIntentRow> {
        val journal = AndroidRecoveryQuarantineJournal(context)
        return AndroidRecoveryJournalDatabase.writable(context)
            .rawQuery(
                "SELECT hex(intent_id) FROM recovery_quarantine_intent_v4 " +
                    "WHERE run_id=? AND candidate_id=? ORDER BY intent_id",
                arrayOf(run.toCanonicalString(), candidate.contractId),
            )
            .use { cursor ->
                buildList {
                    while (cursor.moveToNext()) add(
                        requireNotNull(
                            journal.load(
                                Sha256Value.fromLowercaseHex(cursor.getString(0).lowercase())
                            )
                        )
                    )
                }
            }
    }

    private fun quarantineObservation(): JSONObject {
        val rows = quarantineRows()
        fun regular(file: File): Boolean =
            try {
                android.system.OsConstants.S_ISREG(android.system.Os.lstat(file.path).st_mode)
            } catch (error: android.system.ErrnoException) {
                if (error.errno == android.system.OsConstants.ENOENT) false else throw error
            }
        return JSONObject()
            .put("rowCount", rows.size)
            .put("completedCount", rows.count { it.state == QuarantineIntentState.COMPLETED })
            .put("uniqueIntentCount", rows.map { it.intentId }.distinct().size)
            .put("sourceItemCount", rows.count { regular(File(root, it.input.sourceRelativeName)) })
            .put(
                "destinationItemCount",
                rows.count { regular(File(quarantineRoot, it.destinationRelativeName)) },
            )
            .put(
                "terminalStates",
                JSONArray(
                    rows.map {
                        if (it.state == QuarantineIntentState.COMPLETED) "COMPLETED"
                        else "RETRY_REQUIRED"
                    }
                ),
            )
    }

    private fun currentPublication(): JSONObject {
        return if (candidate == RecoveryCandidate.MICROFILE) {
            val row = AndroidRecoveryMicrofileJournal(context).loadSnapshot(run).publications.last()
            JSONObject()
                .put("generation", row.generation.toLong())
                .put("committedEnd", row.committedEndExclusive.toLong())
                .put("publicationCiphertextSha256", digest(File(root, row.publicationRelativeName)))
        } else {
            val row = checkpoint()
            JSONObject()
                .put("generation", row.generation.toLong())
                .put("committedEnd", row.committedEnd.toLong())
                .put("publicationCiphertextSha256", digest(File(root, row.checkpointRelativeName)))
        }
    }

    private fun checkpointSelection(row: RecoveryStreamingCheckpointRow): JSONObject {
        val s = state()
        check(
            row.generation.toLong() == s.getLong("checkpointGeneration") &&
                row.streamCiphertextPrefixBytes.toLong() == s.getLong("checkpointPrefixBytes") &&
                row.committedEnd.toLong() == s.getLong("committedEnd")
        )
        return JSONObject()
            .put("originalCheckpointIdentity", s.getString("checkpointIdentity"))
            .put("checkpointIdentity", row.checkpointIdentity.toLowercaseHex())
            .put("checkpointGeneration", row.generation.toLong())
            .put("checkpointPrefixBytes", row.streamCiphertextPrefixBytes.toLong())
            .put("checkpointContextEnd", row.committedEnd.toLong())
            .put("acceptedEnd", s.getLong("acceptedEnd"))
            .put("preFaultSourceBytes", s.getLong("sourceBytes"))
            .put("preFaultSourceSha256", s.getString("sourceSha256"))
    }

    private fun rollbackFault() {
        require(request.has("externalAnchor"))
        val older = File(directory, "older")
        check(older.isDirectory)
        if (case == "RBK-02") {
            RecoveryCampaignRunSnapshot.restore(context, confirmation, older)
        } else {
            val current =
                if (candidate == RecoveryCandidate.MICROFILE)
                    AndroidRecoveryMicrofileJournal(context)
                        .loadSnapshot(run)
                        .publications
                        .last()
                        .publicationRelativeName
                else checkpoint().checkpointRelativeName
            val folder =
                File(
                    older,
                    "run/${if (candidate == RecoveryCandidate.MICROFILE) "manifests" else "checkpoints"}",
                )
            val previous =
                requireNotNull(folder.listFiles())
                    .filter { it.name.endsWith(".ct") }
                    .maxBy { it.name }
            FileOutputStream(File(root, current), false).use { output ->
                previous.inputStream().use { it.copyTo(output) }
                output.fd.sync()
            }
        }
        saveState(
            state().put("rollbackInjected", true).put("rollbackObserved", currentPublication())
        )
    }

    @Suppress("NestedBlockDepth") // Synthetic transaction retains exact before/after row evidence.
    private fun splitOrTailFault() {
        val db = AndroidRecoveryJournalDatabase.writable(context)
        if (case == "TRU-01") {
            val target =
                if (candidate == RecoveryCandidate.STREAM) stream
                else File(root, "units/u-0000000002.ct.tmp")
            // PREPARE emitted this real encrypted tail after the last semantic publication.
            if (candidate == RecoveryCandidate.STREAM)
                check(target.length() - state().getLong("checkpointPrefixBytes") >= 4096)
            java.io.RandomAccessFile(target, "rw").use {
                it.setLength(it.length() - 2000)
                it.fd.sync()
            }
            saveState(state().put("uncommittedTailTruncated", true))
            return
        }
        if (candidate == RecoveryCandidate.MICROFILE) {
            val snapshot = AndroidRecoveryMicrofileJournal(context).loadSnapshot(run)
            val unit = snapshot.units.last()
            val publication = snapshot.publications.last()
            if (case == "SPL-04") check(File(root, publication.publicationRelativeName).delete())
            else {
                db.beginTransactionNonExclusive()
                try {
                    check(
                        db.delete(
                            RecoveryJournalSchema.UNIT_TABLE,
                            "run_id=? AND unit_index=?",
                            arrayOf(run.toCanonicalString(), unit.unitIndex.toString()),
                        ) == 1
                    )
                    if (case == "SPL-03")
                        check(
                            db.delete(
                                RecoveryJournalSchema.PUBLICATION_TABLE,
                                "run_id=? AND generation=?",
                                arrayOf(run.toCanonicalString(), publication.generation.toString()),
                            ) == 1
                        )
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
            }
        } else {
            val latest = checkpoint()
            if (case == "SPL-04") check(File(root, latest.checkpointRelativeName).delete())
            else
                check(
                    db.delete(
                        RecoveryJournalSchema.STREAM_CHECKPOINT_TABLE,
                        "run_id=? AND generation=?",
                        arrayOf(run.toCanonicalString(), latest.generation.toString()),
                    ) == 1
                )
        }
        saveState(state().put("splitBrainInjected", true))
    }

    private fun recoverBootstrap() {
        val collision = case == "KEY-07" || case == "KCF-06"
        val facts: JSONObject
        val classification: String
        if (collision) {
            check(state().getBoolean("collisionObserved"))
            val before = allArtifacts()
            val result =
                AndroidRecoveryKeyBootstrap.controller(context) {
                        error("Collision evidence forbidden")
                    }
                    .bootstrap(confirmation)
            check(
                result is BootstrapResult.Rejected &&
                    result.classification == KeyRecoveryClassification.KEY_REF_COLLISION
            )
            check(before == allArtifacts())
            val original = state().getJSONObject("collisionArtifacts")
            check(original.keys().asSequence().associateWith { original.getString(it) } == before)
            classification = "KEY_REF_COLLISION"
            val rows =
                AndroidRecoveryJournalDatabase.writable(context)
                    .rawQuery(
                        "SELECT count(*) FROM recovery_run_bootstrap_v1 WHERE run_id=?",
                        arrayOf(run.toCanonicalString()),
                    )
                    .use { cursor ->
                        check(cursor.moveToFirst())
                        cursor.getInt(0)
                    }
            check(rows == 0)
            facts =
                JSONObject()
                    .put("collisionVerifiedAgain", true)
                    .put("existingArtifactsUnchanged", true)
                    .put("newPublicationCount", 0)
        } else {
            facts =
                requireNotNull(ProcessRecoveryRunSingleWriterGuard.tryAcquire(run)).use {
                    RecoveryCampaignBootstrapFixtures.recover(context, confirmation)
                }
            classification =
                if (facts.getString("classification") == "VALIDATED") "VALID_DURABLE_BOOTSTRAP"
                else facts.getString("classification")
        }
        val rows = quarantineRows()
        val stableIdentity =
            "$runHex|$classification|" + rows.joinToString(",") { it.intentId.toLowercaseHex() }
        FileOutputStream(File(directory, "recovered.pcm"), false).use { it.fd.sync() }
        val correct =
            if (collision) classification == "KEY_REF_COLLISION"
            else if (case == "KCB-06") classification == "VALID_DURABLE_BOOTSTRAP"
            else
                classification == "INCOMPLETE_KEY_BOOTSTRAP" &&
                    rows.all { it.state == QuarantineIntentState.COMPLETED }
        emit(
            "RESULT",
            facts
                .put("classification", classification)
                .put("acceptedEnd", 0)
                .put("committedEnd", 0)
                .put("recoveredEnd", 0)
                .put("authenticated", case == "KCB-06")
                .put("contiguous", true)
                .put("sourceUnchanged", true)
                .put("caseOracleSatisfied", correct)
                .put("processingIntentCount", 0)
                .put("duplicateProcessingIntents", 0)
                .put("missingProcessingIntents", 0)
                .put("microphoneOpens", 0)
                .put("unsafePathOpens", 0)
                .put(
                    "receiptIdentity",
                    Sha256Value.calculate(stableIdentity.toByteArray(Charsets.US_ASCII))
                        .toLowercaseHex(),
                )
                .put("returnedPrefixArtifact", "campaign/$attempt/recovered.pcm"),
        )
    }

    private fun interruptRecovery() {
        validateState()
        if (!state().optBoolean("faultInjected")) fault()
        val boundary =
            if (case == "QUA-03")
                request.getString("mutationVariant").also {
                    require(it in setOf("Q01", "Q02", "Q03", "Q04", "Q05"))
                }
            else "Q02"
        val observer: (String) -> Unit = {
            if (it == boundary) barrier(state().getLong("acceptedEnd").toULong())
        }
        if (candidate == RecoveryCandidate.MICROFILE)
            RecoveryCampaignMicrofileRecovery.reconcile(context, run, observer)
        else quarantineOrphan(observer)
        error("Requested quarantine/recovery interruption was not reached")
    }

    private fun deleteAlias() {
        KeyStore.getInstance("AndroidKeyStore")
            .apply { load(null) }
            .let { store ->
                val alias = confirmation.canonicalAlias.removePrefix("android-keystore://")
                check(store.containsAlias(alias))
                store.deleteEntry(alias)
                check(!store.containsAlias(alias))
            }
    }

    private fun cleanup() {
        // Host issues this explicit operation only after retaining the outcome and private prefix.
        verifyHostRetention()
        if (case == "CLN-02") {
            val receipt = state().getJSONObject("cleanupAdmissionReceipt")
            RecoveryCampaignCleanupFaults.apply(
                context,
                confirmation,
                attempt,
                case,
                state().getString("cleanupPlanSha256"),
                receipt.getString("hostReceiptSha256"),
                true,
            )
        }
        val retained = allArtifacts()
        val database = AndroidRecoveryJournalDatabase.writable(context)
        val tables =
            listOf(
                RecoveryJournalSchema.STREAM_RANGE_TABLE,
                RecoveryJournalSchema.STREAM_OUTCOME_TABLE,
                RecoveryJournalSchema.STREAM_CHECKPOINT_TABLE,
                RecoveryJournalSchema.QUARANTINE_TABLE,
                RecoveryJournalSchema.UNIT_TABLE,
                RecoveryJournalSchema.PUBLICATION_TABLE,
                RecoveryJournalSchema.RUN_TABLE,
            )
        database.beginTransactionNonExclusive()
        try {
            for (table in tables) database.delete(
                table,
                "run_id = ?",
                arrayOf(run.toCanonicalString()),
            )
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
        for (table in tables) database
            .rawQuery(
                "SELECT count(*) FROM $table WHERE run_id = ?",
                arrayOf(run.toCanonicalString()),
            )
            .use { check(it.moveToFirst() && it.getLong(0) == 0L) }
        val alias = confirmation.canonicalAlias.removePrefix("android-keystore://")
        KeyStore.getInstance("AndroidKeyStore")
            .apply { load(null) }
            .let { store ->
                store.deleteEntry(alias)
                check(!store.containsAlias(alias))
            }
        if (root.exists()) deleteOwnedTree(root)
        if (quarantineRoot.exists()) deleteOwnedTree(quarantineRoot)
        deleteOwnedTree(directory)
        emit(
            "RESULT",
            JSONObject()
                .put("cleanupComplete", !root.exists() && !directory.exists())
                .put("retainedArtifactReferences", JSONObject(retained))
                .put("removedRunRowsVerified", true),
        )
    }

    private fun addRetention(result: JSONObject) {
        val sequence = state().optInt("retentionSequence", 0) + 1
        val destination = File(directory, "retention-$sequence")
        RecoveryCampaignRunSnapshot.capture(context, confirmation, destination)
        FileOutputStream(File(destination, "controller-state.json")).use {
            it.write(stateFile.readBytes())
            it.fd.sync()
        }
        for (name in
            listOf(
                "cleanup-plan.json",
                "pre-cleanup-result.json",
                "cleanup-retention.json",
                "cleanup-barrier-issued.json",
            )) {
            val file = File(directory, name)
            if (file.isFile)
                FileOutputStream(File(destination, name)).use {
                    it.write(file.readBytes())
                    it.fd.sync()
                }
        }
        val artifacts = JSONArray()
        destination
            .walkTopDown()
            .filter { it.isFile }
            .sortedBy { it.relativeTo(context.dataDir).invariantSeparatorsPath }
            .forEach { file ->
                artifacts.put(
                    JSONObject()
                        .put(
                            "relativePath",
                            file.relativeTo(context.dataDir).invariantSeparatorsPath,
                        )
                        .put("bytes", file.length())
                        .put("sha256", digest(file))
                        .put(
                            "role",
                            if (file.name == "rows.json") "CONSISTENT_JOURNAL_SNAPSHOT"
                            else "RETAINED_RAW_ARTIFACT",
                        )
                )
            }
        val canonical =
            (0 until artifacts.length()).joinToString(
                prefix = "[",
                postfix = "]",
                separator = ",",
            ) { i ->
                val item = artifacts.getJSONObject(i)
                "{\"bytes\":${item.getLong("bytes")}," +
                    "\"relativePath\":${JSONObject.quote(item.getString("relativePath"))}," +
                    "\"role\":${JSONObject.quote(item.getString("role"))}," +
                    "\"sha256\":${JSONObject.quote(item.getString("sha256"))}}"
            }
        val hash = Sha256Value.calculate(canonical.toByteArray(Charsets.US_ASCII)).toLowercaseHex()
        val current = state()
        val history = current.optJSONObject("retentionManifests") ?: JSONObject()
        history.put(hash, JSONObject().put("count", artifacts.length()).put("operation", operation))
        saveState(
            current
                .put("retentionSequence", sequence)
                .put("artifactManifestSha256", hash)
                .put("retainedArtifactCount", artifacts.length())
                .put("retentionManifests", history)
        )
        result
            .put("retentionArtifacts", artifacts)
            .put("retentionSnapshotConsistent", true)
            .put("artifactManifestSha256", hash)
            .put("journalExportMeaning", "TRANSACTIONAL_TYPED_ROWS_FOR_EXACT_RUN_NO_DB_WAL_BYTES")
    }

    private fun verifyHostRetention(allowPreparedCleanup: Boolean = false) {
        val receipt = request.getJSONObject("hostRetentionReceipt")
        require(receipt.getString("schema") == "DORA_RECOVERY_HOST_RETENTION_V1")
        require(
            receipt.getString("attemptId") == attempt &&
                receipt.getString("manifestSha256") == manifestSha256
        )
        require(receipt.getJSONObject("source").getString("commit") == harnessRevision)
        val manifest = receipt.getString("artifactManifestSha256")
        require(state().getJSONObject("retentionManifests").has(manifest))
        val retained = state().getJSONObject("retentionManifests").getJSONObject(manifest)
        require(
            retained.getString("operation") == "RECOVER" ||
                allowPreparedCleanup &&
                    case.startsWith("CLN-") &&
                    retained.getString("operation") == "PREPARE"
        )
        require(receipt.getInt("retainedArtifactCount") == retained.getInt("count"))
        require(receipt.getString("hostReceiptSha256").matches(Regex("[0-9a-f]{64}")))
    }

    private fun deleteOwnedTree(target: File) {
        val allowed = listOf(root.absolutePath, quarantineRoot.absolutePath, directory.absolutePath)
        val path = target.absolutePath
        check(allowed.any { path == it || path.startsWith(it + File.separator) })
        val stat = android.system.Os.lstat(path)
        if (android.system.OsConstants.S_ISDIR(stat.st_mode)) {
            requireNotNull(target.listFiles()).forEach(::deleteOwnedTree)
            android.system.Os.remove(path)
        } else {
            check(
                android.system.OsConstants.S_ISREG(stat.st_mode) ||
                    android.system.OsConstants.S_ISLNK(stat.st_mode)
            ) {
                "Cleanup refuses an unexpected nonregular object"
            }
            android.system.Os.remove(path)
        }
    }
}
