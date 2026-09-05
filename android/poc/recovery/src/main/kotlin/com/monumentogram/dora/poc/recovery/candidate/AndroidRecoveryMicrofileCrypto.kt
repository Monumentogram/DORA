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
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAead
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadBackend
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadProvider
import com.monumentogram.dora.poc.recovery.crypto.RecoveryTinkRuntime

/** Typed Tink/Android-Keystore adapter for the sequential candidate controller. */
@Suppress("TooGenericExceptionCaught")
internal class AndroidRecoveryMicrofileCrypto :
    RecoveryMicrofileCrypto, RecoveryReconciliationCrypto {
    private val runProvider = RecoveryRunAeadProvider(AndroidExistingRunAeadBackend)

    override fun openRunAead(runId: RunId): RecoveryRunAead = runProvider.openExisting(runId)

    override fun authenticateConfirmationOrphan(
        expected: KeyConfirmationValue,
        ciphertext: ByteArray,
    ): KeyConfirmationDecryption =
        openRunAead(expected.runId).decryptKeyConfirmation(ciphertext, expected)

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
        } catch (error: Throwable) {
            ManifestAuthenticationOutcome.Rejected(classifyCrypto(error))
        }

    private fun authenticateManifestRaw(
        runId: RunId,
        publication: RecoveryManifestPublicationRow,
        previousDigest: com.monumentogram.dora.poc.recovery.contract.Sha256Value,
        envelope: ByteArray,
        ciphertext: ByteArray,
    ): RecoveryManifest {
        val runAead = openRunAead(runId)
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
        val keyset = RecoveryTinkRuntime.parseEncryptedAeadKeyset(envelope, runAead, envelopeAad)
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
        return RecoveryManifestCodec.decode(keyset.decryptPublication(ciphertext, aad))
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
        } catch (error: Throwable) {
            UnitAuthenticationOutcome.Rejected(classifyCrypto(error))
        }

    private fun authenticateUnitRaw(
        runId: RunId,
        unit: RecoveryMicrofileUnitRow,
        previousDigest: com.monumentogram.dora.poc.recovery.contract.Sha256Value,
        envelope: ByteArray,
        ciphertext: ByteArray,
    ): ByteArray {
        val runAead = openRunAead(runId)
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
        val keyset = RecoveryTinkRuntime.parseEncryptedAeadKeyset(envelope, runAead, envelopeAad)
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
        return keyset.decryptMicrofile(ciphertext, aad)
    }

    private fun classifyCrypto(error: Throwable): RecoveryFailureDiagnostic {
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
                            .OPERATIONAL,
                        com.monumentogram.dora.poc.recovery.crypto
                            .RecoveryEncryptedKeysetParseFailure
                            .UNKNOWN -> RecoveryFailureCategory.OPERATIONAL
                        else -> RecoveryFailureCategory.STRUCTURAL
                    }
                is java.security.GeneralSecurityException ->
                    RecoveryFailureCategory.AUTHENTICATION_REJECTED
                is com.monumentogram.dora.poc.recovery.contract.RecoveryContractException ->
                    RecoveryFailureCategory.STRUCTURAL
                else -> RecoveryFailureCategory.OPERATIONAL
            }
        return RecoveryFailureDiagnostic.capture(category, error)
    }

    private object AndroidExistingRunAeadBackend : RecoveryRunAeadBackend {
        override fun generateNew(keyUri: String): Unit =
            error("Candidate publication cannot generate a run alias")

        override fun getAead(keyUri: String): Aead =
            AndroidKeystoreKmsClient.Builder().setKeyUri(keyUri).build().getAead(keyUri)
    }
}
