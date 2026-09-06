package com.monumentogram.dora.poc.recovery.candidate

import com.google.crypto.tink.Aead
import com.google.crypto.tink.integration.android.AndroidKeystoreKmsClient
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeAad
import com.monumentogram.dora.poc.recovery.contract.KeyEnvelopeTargetKind
import com.monumentogram.dora.poc.recovery.contract.MicrofileAad
import com.monumentogram.dora.poc.recovery.contract.PublicationAad
import com.monumentogram.dora.poc.recovery.contract.PublicationKind
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifest
import com.monumentogram.dora.poc.recovery.contract.RecoveryManifestCodec
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.crypto.KeyConfirmationDecryption
import com.monumentogram.dora.poc.recovery.crypto.RecoveryAeadKeyset
import com.monumentogram.dora.poc.recovery.crypto.RecoveryDecryptFailureSignal
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAead
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadBackend
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadProvider
import com.monumentogram.dora.poc.recovery.crypto.RecoveryTinkRuntime
import com.monumentogram.dora.poc.recovery.crypto.toRecoveryDecryptFailureSignal

/** Typed Tink/Android-Keystore adapter for the sequential candidate controller. */
@Suppress("TooGenericExceptionCaught", "TooManyFunctions")
internal class AndroidRecoveryMicrofileCrypto
internal constructor(
    private val runProvider: RecoveryRunAeadProvider =
        RecoveryRunAeadProvider(AndroidExistingRunAeadBackend)
) : RecoveryMicrofileCrypto, RecoveryReconciliationCrypto {

    override fun openRunAead(runId: RunId): RecoveryRunAead = runProvider.openExisting(runId)

    override fun authenticateConfirmationOrphan(
        expected: KeyConfirmationValue,
        ciphertext: ByteArray,
    ): KeyConfirmationDecryption {
        val runAead =
            try {
                openRunAead(expected.runId)
            } catch (error: java.security.GeneralSecurityException) {
                throw RecoveryConfirmationAuthenticationException(
                    RecoveryFailureDiagnostic.capture(
                        RecoveryFailureCategory.MISSING_ARTIFACT,
                        error,
                        RecoveryFailureStage.ALIAS_OPEN,
                    ),
                    error,
                )
            } catch (error: Throwable) {
                throw RecoveryConfirmationAuthenticationException(
                    RecoveryFailureDiagnostic.capture(
                        RecoveryFailureCategory.OPERATIONAL,
                        error,
                        RecoveryFailureStage.ALIAS_OPEN,
                    ),
                    error,
                )
            }
        return try {
            runAead.decryptKeyConfirmation(ciphertext, expected)
        } catch (error: Throwable) {
            throw RecoveryConfirmationAuthenticationException(
                RecoveryFailureDiagnostic.capture(
                    error.toRecoveryDecryptFailureSignal().category(),
                    error,
                    RecoveryFailureStage.CONFIRMATION_PAYLOAD_DECRYPT,
                ),
                error,
            )
        }
    }

    override fun createKeyset(aad: KeyEnvelopeAad, runAead: RecoveryRunAead): PreparedRecoveryAead {
        val keyset = RecoveryTinkRuntime.newAeadKeyset(aad)
        return PreparedRecoveryAead(keyset, keyset.serializeEncrypted(runAead))
    }

    override fun encryptMicrofile(
        keyset: RecoveryAeadKeyset,
        plaintext: ByteArray,
        aad: MicrofileAad,
    ): ByteArray = keyset.encryptMicrofile(plaintext, aad)

    override fun encryptManifest(
        keyset: RecoveryAeadKeyset,
        plaintext: ByteArray,
        aad: PublicationAad,
    ): ByteArray = keyset.encryptPublication(plaintext, aad)

    override fun authenticateManifest(
        runId: RunId,
        publication: RecoveryManifestPublicationRow,
        previousDigest: com.monumentogram.dora.poc.recovery.contract.Sha256Value,
        envelope: ByteArray,
        ciphertext: ByteArray,
    ): ManifestAuthenticationOutcome =
        try {
            ManifestAuthenticationOutcome.Authenticated(
                authenticateManifestRaw(runId, publication, previousDigest, envelope, ciphertext)
            )
        } catch (error: StagedCryptoFailure) {
            ManifestAuthenticationOutcome.Rejected(error.diagnostic)
        } catch (error: Throwable) {
            ManifestAuthenticationOutcome.Rejected(
                RecoveryFailureDiagnostic.capture(
                    RecoveryFailureCategory.OPERATIONAL,
                    error,
                    RecoveryFailureStage.OPERATIONAL,
                )
            )
        }

    private fun authenticateManifestRaw(
        runId: RunId,
        publication: RecoveryManifestPublicationRow,
        previousDigest: com.monumentogram.dora.poc.recovery.contract.Sha256Value,
        envelope: ByteArray,
        ciphertext: ByteArray,
    ): RecoveryManifest {
        val runAead = stagedAlias { openRunAead(runId) }
        val envelopeAad =
            KeyEnvelopeAad(
                RecoveryCandidate.MICROFILE,
                runId,
                KeyEnvelopeTargetKind.MANIFEST,
                publication.generation,
                KeyEnvelopeAad.NOT_APPLICABLE_UNIT_INDEX,
                0UL,
                publication.committedEndExclusive,
                0UL,
                previousDigest,
            )
        val keyset = stagedEnvelope {
            RecoveryTinkRuntime.parseEncryptedAeadKeyset(envelope, runAead, envelopeAad)
        }
        val aad =
            PublicationAad(
                RecoveryCandidate.MICROFILE,
                runId,
                PublicationKind.MANIFEST,
                publication.generation,
                publication.generation - 1UL,
                publication.committedEndExclusive,
                previousDigest,
            )
        val plaintext =
            stagedPayload(RecoveryFailureStage.MANIFEST_PAYLOAD_DECRYPT) {
                keyset.decryptPublication(ciphertext, aad)
            }
        return staged(RecoveryFailureStage.MANIFEST_PLAINTEXT, RecoveryFailureCategory.STRUCTURAL) {
            RecoveryManifestCodec.decode(plaintext)
        }
    }

    override fun authenticateUnit(
        runId: RunId,
        unit: RecoveryMicrofileUnitRow,
        previousDigest: com.monumentogram.dora.poc.recovery.contract.Sha256Value,
        envelope: ByteArray,
        ciphertext: ByteArray,
    ): UnitAuthenticationOutcome =
        try {
            UnitAuthenticationOutcome.Authenticated(
                authenticateUnitRaw(runId, unit, previousDigest, envelope, ciphertext)
            )
        } catch (error: StagedCryptoFailure) {
            UnitAuthenticationOutcome.Rejected(error.diagnostic)
        } catch (error: Throwable) {
            UnitAuthenticationOutcome.Rejected(
                RecoveryFailureDiagnostic.capture(
                    RecoveryFailureCategory.OPERATIONAL,
                    error,
                    RecoveryFailureStage.OPERATIONAL,
                )
            )
        }

    private fun authenticateUnitRaw(
        runId: RunId,
        unit: RecoveryMicrofileUnitRow,
        previousDigest: com.monumentogram.dora.poc.recovery.contract.Sha256Value,
        envelope: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray {
        val runAead = stagedAlias { openRunAead(runId) }
        val envelopeAad =
            KeyEnvelopeAad(
                RecoveryCandidate.MICROFILE,
                runId,
                KeyEnvelopeTargetKind.MICROFILE,
                unit.manifestGeneration,
                unit.unitIndex,
                unit.plaintextStartInclusive,
                unit.plaintextEndExclusive,
                unit.cadenceSeconds,
                previousDigest,
            )
        val keyset = stagedEnvelope {
            RecoveryTinkRuntime.parseEncryptedAeadKeyset(envelope, runAead, envelopeAad)
        }
        val aad =
            MicrofileAad(
                RecoveryCandidate.MICROFILE,
                runId,
                unit.manifestGeneration,
                unit.unitIndex,
                unit.plaintextStartInclusive,
                unit.plaintextEndExclusive,
                unit.cadenceSeconds,
                previousDigest,
            )
        return stagedPayload(RecoveryFailureStage.UNIT_PAYLOAD_DECRYPT) {
            keyset.decryptMicrofile(ciphertext, aad)
        }
    }

    private class StagedCryptoFailure(val diagnostic: RecoveryFailureDiagnostic) :
        RuntimeException()

    private inline fun <T> staged(
        stage: RecoveryFailureStage,
        category: RecoveryFailureCategory,
        block: () -> T,
    ): T =
        try {
            block()
        } catch (error: Throwable) {
            throw StagedCryptoFailure(RecoveryFailureDiagnostic.capture(category, error, stage))
        }

    private inline fun <T> stagedAlias(block: () -> T): T =
        try {
            block()
        } catch (error: java.security.GeneralSecurityException) {
            throw StagedCryptoFailure(
                RecoveryFailureDiagnostic.capture(
                    RecoveryFailureCategory.MISSING_ARTIFACT,
                    error,
                    RecoveryFailureStage.ALIAS_OPEN,
                )
            )
        } catch (error: Throwable) {
            throw StagedCryptoFailure(
                RecoveryFailureDiagnostic.capture(
                    RecoveryFailureCategory.OPERATIONAL,
                    error,
                    RecoveryFailureStage.ALIAS_OPEN,
                )
            )
        }

    private inline fun <T> stagedPayload(stage: RecoveryFailureStage, block: () -> T): T =
        try {
            block()
        } catch (error: Throwable) {
            throw StagedCryptoFailure(
                RecoveryFailureDiagnostic.capture(
                    error.toRecoveryDecryptFailureSignal().category(),
                    error,
                    stage,
                )
            )
        }

    private fun RecoveryDecryptFailureSignal.category(): RecoveryFailureCategory =
        when (this) {
            RecoveryDecryptFailureSignal.AUTHENTICATION_REJECTED ->
                RecoveryFailureCategory.AUTHENTICATION_REJECTED
            RecoveryDecryptFailureSignal.OPERATIONAL -> RecoveryFailureCategory.OPERATIONAL
            RecoveryDecryptFailureSignal.UNKNOWN -> RecoveryFailureCategory.UNKNOWN
        }

    private inline fun <T> stagedEnvelope(block: () -> T): T =
        try {
            block()
        } catch (error: Throwable) {
            throw StagedCryptoFailure(classifyEnvelope(error))
        }

    private fun classifyEnvelope(error: Throwable): RecoveryFailureDiagnostic {
        val category =
            when (error) {
                is com.monumentogram.dora.poc.recovery.crypto.RecoveryEncryptedKeysetParseException ->
                    when (error.failure) {
                        com.monumentogram.dora.poc.recovery.crypto
                            .RecoveryEncryptedKeysetParseFailure
                            .AUTHENTICATION_REJECTED ->
                            RecoveryFailureCategory.AUTHENTICATION_REJECTED
                        com.monumentogram.dora.poc.recovery.crypto
                            .RecoveryEncryptedKeysetParseFailure
                            .OPERATIONAL -> RecoveryFailureCategory.OPERATIONAL
                        com.monumentogram.dora.poc.recovery.crypto
                            .RecoveryEncryptedKeysetParseFailure
                            .UNKNOWN -> RecoveryFailureCategory.UNKNOWN
                        else -> RecoveryFailureCategory.STRUCTURAL
                    }
                is java.security.GeneralSecurityException -> RecoveryFailureCategory.UNKNOWN
                is com.monumentogram.dora.poc.recovery.contract.RecoveryContractException ->
                    RecoveryFailureCategory.STRUCTURAL
                else -> RecoveryFailureCategory.OPERATIONAL
            }
        return RecoveryFailureDiagnostic.capture(
            category,
            error,
            RecoveryFailureStage.ENVELOPE_PARSE,
        )
    }

    private object AndroidExistingRunAeadBackend : RecoveryRunAeadBackend {
        override fun generateNew(keyUri: String): Unit =
            error("Candidate publication cannot generate a run alias")

        override fun getAead(keyUri: String): Aead =
            AndroidKeystoreKmsClient.Builder().setKeyUri(keyUri).build().getAead(keyUri)
    }
}
