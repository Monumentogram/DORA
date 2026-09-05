package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.BoundedBinaryWriter
import com.monumentogram.dora.poc.recovery.contract.RecoveryContract
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifest
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.contract.writeSha256

internal enum class RecoveryFailureCategory {
    UNSAFE_PARENT,
    CORRUPT_LEAF,
    MISSING_ARTIFACT,
    STRUCTURAL,
    AUTHENTICATION_REJECTED,
    OPERATIONAL,
    UNKNOWN,
    UNKNOWN_OUTCOME,
}

internal enum class RecoveryFailureStage {
    ALIAS_OBSERVATION,
    ALIAS_OPEN,
    ENVELOPE_BINDING,
    ENVELOPE_PARSE,
    MANIFEST_PAYLOAD_DECRYPT,
    MANIFEST_PLAINTEXT,
    MANIFEST_SEMANTICS,
    CONFIRMATION_PAYLOAD_DECRYPT,
    CONFIRMATION_PLAINTEXT,
    UNIT_PAYLOAD_DECRYPT,
    UNIT_PLAINTEXT,
    ARTIFACT_PATH,
    ARTIFACT_IO,
    JOURNAL,
    OPERATIONAL,
}

internal data class RecoveryFailureDiagnostic(
    val category: RecoveryFailureCategory,
    val type: String,
    val message: String,
    val stage: RecoveryFailureStage = RecoveryFailureStage.OPERATIONAL,
) {
    companion object {
        fun capture(
            category: RecoveryFailureCategory,
            error: Throwable,
            stage: RecoveryFailureStage = RecoveryFailureStage.OPERATIONAL,
        ) =
            RecoveryFailureDiagnostic(
                category,
                error::class.java.name.take(MAX_TEXT),
                (error.message ?: "").take(MAX_TEXT),
                stage,
            )

        private const val MAX_TEXT = 256
    }
}

internal class RecoveryConfirmationAuthenticationException(
    val diagnostic: RecoveryFailureDiagnostic,
    cause: Throwable,
) : RuntimeException(diagnostic.message, cause)

/** Canonical bounded identity of every authenticated journal-row field. */
internal object RecoveryAuthenticatedRowsDigest {
    private const val MAX_ROW_BYTES = 4_096
    private const val MAX_NAME_BYTES = 1_024
    private const val MAX_STATE_BYTES = 32
    private const val MAX_DOMAIN_BYTES = 32
    private const val MAX_RUN_ID_BYTES = 64

    @Suppress("SwallowedException")
    fun calculateOrNull(rows: List<RecoveryMicrofileUnitRow>): Sha256Value? {
        if (rows.size > RecoveryContract.MAX_MANIFEST_ENTRIES) return null
        return try {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            digest.update(u32(rows.size))
            rows.forEach { row ->
                val encoded = encode(row)
                digest.update(u32(encoded.size))
                digest.update(encoded)
            }
            Sha256Value.fromBytes(digest.digest())
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: IllegalStateException) {
            null
        } catch (_: ArithmeticException) {
            null
        }
    }

    @Suppress("MagicNumber")
    private fun u32(value: Int): ByteArray {
        require(value >= 0)
        return byteArrayOf(
            (value ushr 24).toByte(),
            (value ushr 16).toByte(),
            (value ushr 8).toByte(),
            value.toByte(),
        )
    }

    private fun encode(row: RecoveryMicrofileUnitRow): ByteArray {
        require(row.ciphertextBytes >= 0 && row.keyEnvelopeBytes >= 0)
        return BoundedBinaryWriter(MAX_ROW_BYTES)
            .apply {
                writeLp16Ascii("REC-I3-ROW-V1", MAX_DOMAIN_BYTES)
                writeLp16Ascii(row.runId, MAX_RUN_ID_BYTES)
                writeLp16Ascii(row.candidateId, RecoveryContract.MAX_CANDIDATE_ID_BYTES)
                writeU32(row.unitIndex)
                writeU64(row.plaintextStartInclusive)
                writeU64(row.plaintextEndExclusive)
                writeU32(row.cadenceSeconds)
                writeLp16Ascii(row.ciphertextRelativeName, MAX_NAME_BYTES)
                writeU64(row.ciphertextBytes.toULong())
                writeSha256(row.ciphertextSha256)
                writeLp16Ascii(row.keyEnvelopeRelativeName, MAX_NAME_BYTES)
                writeU64(row.keyEnvelopeBytes.toULong())
                writeSha256(row.keyEnvelopeSha256)
                writeU64(row.manifestGeneration)
                writeSha256(row.processingIntentId)
                writeLp16Ascii(row.state, MAX_STATE_BYTES)
            }
            .toByteArray()
    }
}

internal sealed interface ManifestAuthenticationOutcome {
    data class Authenticated(val manifest: RecoveryManifest) : ManifestAuthenticationOutcome

    data class Rejected(val diagnostic: RecoveryFailureDiagnostic) : ManifestAuthenticationOutcome
}

internal sealed interface UnitAuthenticationOutcome {
    class Authenticated(bytes: ByteArray) : UnitAuthenticationOutcome {
        private val value = bytes.copyOf()

        fun snapshot(): ByteArray = value.copyOf()
    }

    data class Rejected(val diagnostic: RecoveryFailureDiagnostic) : UnitAuthenticationOutcome
}
