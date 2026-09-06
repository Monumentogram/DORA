@file:Suppress("LargeClass", "LongMethod", "MagicNumber")

package com.monumentogram.dora.poc.recovery.candidate

import android.os.Build
import android.security.keystore.KeyProperties
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointIdentityInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingIdentity
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalReadResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingWitnessInput
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.coordination.ProcessRecoveryRunSingleWriterGuard
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryJournalDatabase
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryStreamingJournal
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryStreamingSource
import java.io.File
import java.security.KeyStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A deliberately gated, synthetic-only E36 Google APIs preflight. It is an instrumentation entry
 * point, not an admission or measured durability campaign.
 */
@RunWith(AndroidJUnit4::class)
class RecoveryE36GapiPreflightInstrumentedTest {
    @Test
    fun syntheticFreshReadbackCleanupReplayAndIdentityDenial() {
        val arguments = InstrumentationRegistry.getArguments()
        require(arguments.getString("pocRecoveryE36GapiPreflight") == "true") {
            "pocRecoveryE36GapiPreflight=true is required"
        }
        require(Build.VERSION.SDK_INT == 36) { "API 36 is required" }
        require(Build.FINGERPRINT.contains("generic", ignoreCase = true)) {
            "E36-GAPI emulator fingerprint is required"
        }

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val journal = AndroidRecoveryStreamingJournal(context)
        val runId = RunId.fromBytes(ByteArray(16) { (it + 1).toByte() })
        val sourceBytes = ByteArray(8_192) { ((it * 31 + 9) and 0xff).toByte() }
        val oracleBytes = ByteArray(8_136) { ((it * 7 + 5) and 0xff).toByte() }
        val runDirectory =
            File(context.noBackupFilesDir, "poc-recovery/v1/runs/${runId.toCanonicalString()}")
        val sourceFile = File(runDirectory, "stream/stream.ct")
        val events = mutableListOf<RecoveryStreamingEvidenceEvent>()
        val port = CountingAuthenticator(oracleBytes)

        try {
            sourceFile.parentFile!!.mkdirs()
            sourceFile.outputStream().use { stream ->
                stream.write(sourceBytes)
                stream.fd.sync()
            }
            val checkpoint = checkpoint(runId, sourceBytes)
            assertTrue(journal.insertCheckpoint(checkpoint).isCheckpointReceipt())
            val request = request(checkpoint, runId, oracleBytes, sourceBytes)
            val controller =
                RecoveryStreamingReconciliationController(
                    journal = journal,
                    source = AndroidOsRecoveryStreamingSource(context.noBackupFilesDir, journal),
                    guard = ProcessRecoveryRunSingleWriterGuard,
                    checkpointAuthenticator = port,
                    evidenceSink = RecoveryStreamingEvidenceSink { events += it },
                )

            val fresh = controller.recover(request)
            assertTrue(fresh is RecoveryStreamingReconciliationResult.PersistedValid)
            fresh as RecoveryStreamingReconciliationResult.PersistedValid
            assertFalse(fresh.receipt.replayed)
            assertEquals(oracleBytes.size.toULong(), fresh.recoveredEnd)
            assertEquals(1, port.authenticatorCalls)
            assertEquals(1, port.publicReadCalls)
            assertNotNull(readOutcome(journal, request))
            assertTrue(events.last().sanitized())

            val replay = controller.recover(request)
            assertTrue(replay is RecoveryStreamingReconciliationResult.PersistedValid)
            replay as RecoveryStreamingReconciliationResult.PersistedValid
            assertTrue(replay.receipt.replayed)
            assertEquals(1, port.authenticatorCalls)
            assertEquals(1, port.publicReadCalls)
            // A replayed receipt is emitted only after the real source completes its hash-only
            // replay verification; the authenticator and public-read port remain untouched.

            sourceFile.outputStream().use { stream ->
                stream.write(
                    sourceBytes.copyOf().also { it[0] = (it[0].toInt() xor 0xff).toByte() }
                )
                stream.fd.sync()
            }
            val denied = controller.recover(request)
            assertTrue(denied is RecoveryStreamingReconciliationResult.Fatal)
            denied as RecoveryStreamingReconciliationResult.Fatal
            assertEquals(
                RecoveryStreamingResultClassification.STREAM_SOURCE_IDENTITY_CHANGED,
                denied.classification,
            )
            assertEquals(1, port.authenticatorCalls)
            assertEquals(1, port.publicReadCalls)

            emitStatus(arguments.getString("recoveryHarnessRevision"), context, events)
        } finally {
            deleteRunRows(context, runId)
            runDirectory.deleteRecursively()
            assertFalse(runDirectory.exists())
        }
    }

    private fun checkpoint(runId: RunId, source: ByteArray): RecoveryStreamingCheckpointRow {
        val checkpointBytes = ByteArray(128) { (it * 3 + 1).toByte() }
        val checkpointEnvelope = ByteArray(96) { (it * 5 + 2).toByte() }
        val streamEnvelope = ByteArray(96) { (it * 11 + 4).toByte() }
        val input =
            RecoveryStreamingCheckpointIdentityInput(
                runId,
                1UL,
                2UL,
                8_192UL,
                Sha256Value.calculate(source),
                4_056UL,
                "checkpoints/g-00000000000000000001.ct",
                128UL,
                Sha256Value.calculate(checkpointBytes),
                "key-envelopes/checkpoint-g-00000000000000000001.ks",
                96UL,
                Sha256Value.calculate(checkpointEnvelope),
                "stream/stream.ct",
                "key-envelopes/stream.ks",
                96UL,
                Sha256Value.calculate(streamEnvelope),
                Sha256Value.calculate(ByteArray(32)),
            )
        return RecoveryStreamingCheckpointRow(
            runId,
            1UL,
            2UL,
            8_192UL,
            input.streamCiphertextPrefixSha256,
            4_056UL,
            input.checkpointRelativeName,
            input.checkpointBytes,
            input.checkpointSha256,
            input.checkpointEnvelopeRelativeName,
            input.checkpointEnvelopeBytes,
            input.checkpointEnvelopeSha256,
            input.streamRelativeName,
            input.streamEnvelopeRelativeName,
            input.streamEnvelopeBytes,
            input.streamEnvelopeSha256,
            input.previousCheckpointSha256,
            RecoveryStreamingIdentity.checkpoint(input),
        )
    }

    private fun request(
        checkpoint: RecoveryStreamingCheckpointRow,
        runId: RunId,
        oracle: ByteArray,
        source: ByteArray,
    ): RecoveryStreamingControllerRequest {
        val oracleSha = Sha256Value.calculate(oracle)
        val base =
            RecoveryStreamingWitnessInput(
                runId,
                1UL,
                checkpoint.checkpointIdentity,
                8_192UL,
                4_056UL,
                RecoveryStreamingIdentity.oracle(oracle.size.toULong(), oracleSha, runId),
                oracle.size.toULong(),
                oracleSha,
                source.size.toULong(),
                Sha256Value.calculate(source),
                null,
            )
        val witness =
            base.copy(controllerSnapshotSha256 = RecoveryStreamingIdentity.controllerSnapshot(base))
        return RecoveryStreamingControllerRequest(
            witness,
            RecoveryStreamingIntentBuilder.RecoveryStreamingOracle.from(witness, oracle),
        )
    }

    private fun readOutcome(
        journal: AndroidRecoveryStreamingJournal,
        request: RecoveryStreamingControllerRequest,
    ) =
        when (
            val result =
                journal.outcomeByWitness(
                    request.witness.runId,
                    request.witness.checkpointIdentity,
                    RecoveryStreamingIdentity.witness(request.witness),
                )
        ) {
            is RecoveryStreamingJournalReadResult.Value -> result.value
            else -> null
        }

    private fun deleteRunRows(context: android.content.Context, runId: RunId) {
        AndroidRecoveryJournalDatabase.writable(context)
            .delete(
                "recovery_stream_range_quarantine_v4",
                "run_id=?",
                arrayOf(runId.toCanonicalString()),
            )
        AndroidRecoveryJournalDatabase.writable(context)
            .delete(
                "recovery_stream_outcome_v4",
                "run_id=?",
                arrayOf(runId.toCanonicalString()),
            )
        AndroidRecoveryJournalDatabase.writable(context)
            .delete(
                "recovery_stream_checkpoint_v4",
                "run_id=?",
                arrayOf(runId.toCanonicalString()),
            )
    }

    private fun emitStatus(
        revision: String?,
        context: android.content.Context,
        events: List<RecoveryStreamingEvidenceEvent>,
    ) {
        val sqlite = AndroidRecoveryJournalDatabase.writable(context)
        val provider = KeyStore.getInstance("AndroidKeyStore").provider.name
        val payload =
            listOf(
                    "\"integratedRuntimePin\":\"be37378ca88e0bd4aee1f2fe0c54362798bdef9d\"",
                    "\"harnessRevision\":\"${revision ?: "missing"}\"",
                    "\"sqliteVersion\":\"${sqlite.version}\"",
                    "\"keystoreProvider\":\"$provider\"",
                    "\"keyAlgorithm\":\"${KeyProperties.KEY_ALGORITHM_AES}\"",
                    "\"evidenceEvents\":${events.size}",
                )
                .joinToString(prefix = "{", postfix = "}")
        println("INSTRUMENTATION_STATUS $payload")
    }

    private fun RecoveryStreamingEvidenceEvent.sanitized(): Boolean =
        stage != null &&
            classification != null &&
            !toString().contains("stream.ct") &&
            !toString().contains("Exception")

    private class CountingAuthenticator(private val oracle: ByteArray) :
        RecoveryStreamingCheckpointAuthenticator {
        var authenticatorCalls = 0
        var publicReadCalls = 0

        override fun authenticate(
            checkpoint: RecoveryStreamingCheckpointRow,
            witness: RecoveryStreamingWitnessInput,
        ): RecoveryStreamingCheckpointAuthentication {
            authenticatorCalls += 1
            return RecoveryStreamingCheckpointAuthentication.Ready(
                RecoveryStreamingPublicStreamOpener { _, _ ->
                    publicReadCalls += 1
                    oracle.inputStream().let { input ->
                        object : RecoveryStreamingIntentBuilder.RecoveryStreamingPublicRead {
                            override fun read(destination: ByteArray, offset: Int, count: Int) =
                                input.read(destination, offset, count)

                            override fun close() = input.close()
                        }
                    }
                }
            )
        }
    }

    private fun Any.isCheckpointReceipt() = this.javaClass.simpleName == "CheckpointReceipt"
}
