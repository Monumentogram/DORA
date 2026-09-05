package com.monumentogram.dora.poc.recovery.bootstrap

import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.KeyRecoveryClassification
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAead
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

internal enum class BootstrapStep {
    KC01,
    KC02,
    KC03,
    KC04,
    KC05,
    KC06,
    KC07,
    KC08,
    KC09,
    KC10,
    KC11,
    KC12,
    KC13,
}

internal enum class BootstrapNamespaceOccupancy {
    ABSENT,
    OCCUPIED_SAFE,
    OCCUPIED_UNSAFE;

    val occupied: Boolean
        get() = this != ABSENT
}

internal data class BootstrapNamespaceState(
    val keyReferenceNamespace: BootstrapNamespaceOccupancy = BootstrapNamespaceOccupancy.ABSENT,
    val temporary: BootstrapNamespaceOccupancy = BootstrapNamespaceOccupancy.ABSENT,
    val final: BootstrapNamespaceOccupancy = BootstrapNamespaceOccupancy.ABSENT,
) {
    val anyOccupied: Boolean
        get() = keyReferenceNamespace.occupied || temporary.occupied || final.occupied
}

internal enum class KeyConfirmationState {
    VALID
}

internal enum class BootstrapAliasCreationState {
    NOT_ATTEMPTED,
    CREATION_OUTCOME_UNKNOWN,
    CONFIRMED_CREATED,
}

internal data class RecoveryBootstrapRunRow(
    val runId: String,
    val candidateId: String,
    val keyConfirmationRelativeName: String,
    val keyConfirmationBytes: Long,
    val keyConfirmationSha256: Sha256Value,
    val canonicalAliasSha256: Sha256Value,
    val keyConfirmationState: KeyConfirmationState,
)

internal data class BootstrapDurableRemainder(
    val aliasCreationState: BootstrapAliasCreationState = BootstrapAliasCreationState.NOT_ATTEMPTED,
    val temporaryCreated: Boolean = false,
    val temporaryFullyWritten: Boolean = false,
    val temporaryFileSynced: Boolean = false,
    val finalRenamed: Boolean = false,
    val finalDirectorySynced: Boolean = false,
    val runRowInserted: Boolean = false,
    val transactionMarkedSuccessful: Boolean = false,
    val transactionCommitted: Boolean = false,
    val evidenceEmitted: Boolean = false,
) {
    val aliasCreated: Boolean
        get() = aliasCreationState == BootstrapAliasCreationState.CONFIRMED_CREATED
}

private val successfulEndTransactionProof = Any()

/** Opaque authorization whose constructor rejects every caller-supplied proof. */
internal class BootstrapPublicationCapability
internal constructor(
    private val committedValue: KeyConfirmationValue,
    proof: Any,
) {
    init {
        check(proof === successfulEndTransactionProof) {
            "Publication capability requires the private successful-endTransaction proof"
        }
    }

    fun authorizes(value: KeyConfirmationValue): Boolean = committedValue == value
}

internal sealed interface BootstrapResult {
    val completedSteps: List<BootstrapStep>
    val remainder: BootstrapDurableRemainder

    data class Rejected(
        val classification: KeyRecoveryClassification,
        override val completedSteps: List<BootstrapStep>,
        override val remainder: BootstrapDurableRemainder,
    ) : BootstrapResult

    data class Failed(
        val failedStep: BootstrapStep,
        val cause: Throwable,
        override val completedSteps: List<BootstrapStep>,
        override val remainder: BootstrapDurableRemainder,
    ) : BootstrapResult

    data class ConcurrentWriter(
        val runId: RunId,
        override val completedSteps: List<BootstrapStep> = emptyList(),
        override val remainder: BootstrapDurableRemainder = BootstrapDurableRemainder(),
    ) : BootstrapResult

    data class Committed(
        val publicationCapability: BootstrapPublicationCapability,
        val evidenceEmitted: Boolean,
        val evidenceFailure: Throwable?,
        override val completedSteps: List<BootstrapStep>,
        override val remainder: BootstrapDurableRemainder,
    ) : BootstrapResult
}

internal interface RecoveryBootstrapCrypto {
    fun aliasExists(runId: RunId): Boolean

    fun createNewAlias(runId: RunId): BootstrapAliasCreation

    fun encryptConfirmation(
        runAead: RecoveryRunAead,
        value: KeyConfirmationValue,
    ): ByteArray
}

internal sealed interface BootstrapAliasCreation {
    data class Created(val runAead: RecoveryRunAead) : BootstrapAliasCreation

    data class GeneratedButOpenFailed(val cause: Throwable) : BootstrapAliasCreation
}

internal interface BootstrapWriteHandle

internal interface RecoveryBootstrapStorage {
    fun inspectNamespaces(runId: RunId): BootstrapNamespaceState

    fun openExclusiveConfirmationTemp(runId: RunId): BootstrapWriteHandle

    fun write(
        handle: BootstrapWriteHandle,
        bytes: ByteArray,
        offset: Int,
        count: Int,
    ): Int

    fun fsyncTemp(handle: BootstrapWriteHandle)

    fun closeTemp(handle: BootstrapWriteHandle)

    fun finalExists(runId: RunId): Boolean

    fun renameTempToFinal(runId: RunId)

    fun fsyncConfirmationParent(runId: RunId)
}

internal interface RecoveryRunBootstrapJournal {
    fun beginNonExclusive(): RecoveryRunBootstrapTransaction
}

internal interface RecoveryRunBootstrapTransaction {
    fun insert(value: RecoveryBootstrapRunRow)

    fun markSuccessful()

    fun end()
}

internal fun interface BootstrapEvidenceSink {
    fun emit(value: RecoveryBootstrapRunRow)
}

internal fun interface BootstrapWriterLease : AutoCloseable

internal fun interface BootstrapSingleWriterGuard {
    fun tryAcquire(runId: RunId): BootstrapWriterLease?
}

private object ProcessBootstrapSingleWriterGuard : BootstrapSingleWriterGuard {
    private val locks = ConcurrentHashMap<String, ReentrantLock>()

    override fun tryAcquire(runId: RunId): BootstrapWriterLease? {
        val lock = locks.computeIfAbsent(runId.toCanonicalString()) { ReentrantLock() }
        if (!lock.tryLock()) return null
        return BootstrapWriterLease { lock.unlock() }
    }
}

/** Executes the immutable KC01–KC13 bootstrap and is the only publication-capability issuer. */
internal class RecoveryKeyBootstrapController(
    private val crypto: RecoveryBootstrapCrypto,
    private val storage: RecoveryBootstrapStorage,
    private val journal: RecoveryRunBootstrapJournal,
    private val evidenceSink: BootstrapEvidenceSink,
    private val writerGuard: BootstrapSingleWriterGuard = ProcessBootstrapSingleWriterGuard,
) {
    fun bootstrap(value: KeyConfirmationValue): BootstrapResult {
        val lease =
            writerGuard.tryAcquire(value.runId)
                ?: return BootstrapResult.ConcurrentWriter(value.runId)
        return lease.use { bootstrapExclusively(value) }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    private fun bootstrapExclusively(value: KeyConfirmationValue): BootstrapResult {
        val progress = Progress()
        val aliasOccupied =
            attempt(BootstrapStep.KC01, progress) { crypto.aliasExists(value.runId) }
        if (aliasOccupied is Attempt.Failure) return progress.failure(aliasOccupied)
        if ((aliasOccupied as Attempt.Success).value) {
            progress.complete(BootstrapStep.KC01)
            return BootstrapResult.Rejected(
                KeyRecoveryClassification.KEY_REF_COLLISION,
                progress.steps(),
                progress.remainder(),
            )
        }
        val namespaces =
            attempt(BootstrapStep.KC01, progress) { storage.inspectNamespaces(value.runId) }
        if (namespaces is Attempt.Failure) return progress.failure(namespaces)
        progress.complete(BootstrapStep.KC01)
        if ((namespaces as Attempt.Success).value.anyOccupied) {
            return BootstrapResult.Rejected(
                KeyRecoveryClassification.KEY_REF_COLLISION,
                progress.steps(),
                progress.remainder(),
            )
        }

        val created = attempt(BootstrapStep.KC02, progress) { crypto.createNewAlias(value.runId) }
        if (created is Attempt.Failure) {
            progress.aliasCreationState = BootstrapAliasCreationState.CREATION_OUTCOME_UNKNOWN
            return progress.failure(created)
        }
        progress.aliasCreationState = BootstrapAliasCreationState.CONFIRMED_CREATED
        progress.complete(BootstrapStep.KC02)

        val runAead =
            when (val creation = (created as Attempt.Success).value) {
                is BootstrapAliasCreation.Created -> creation.runAead
                is BootstrapAliasCreation.GeneratedButOpenFailed ->
                    return progress.failure(Attempt.Failure(BootstrapStep.KC03, creation.cause))
            }
        progress.complete(BootstrapStep.KC03)

        val encrypted =
            attempt(BootstrapStep.KC04, progress) {
                crypto.encryptConfirmation(runAead, value)
            }
        if (encrypted is Attempt.Failure) return progress.failure(encrypted)
        val ciphertext = (encrypted as Attempt.Success).value.copyOf()
        progress.complete(BootstrapStep.KC04)

        val openHandle =
            attempt(BootstrapStep.KC05, progress) {
                storage.openExclusiveConfirmationTemp(value.runId)
            }
        if (openHandle is Attempt.Failure) return progress.failure(openHandle)
        val handle = (openHandle as Attempt.Success).value
        progress.temporaryCreated = true
        progress.complete(BootstrapStep.KC05)
        val writeFailure = writeAndSync(handle, ciphertext, progress)
        if (writeFailure != null) return writeFailure

        val collision = attempt(BootstrapStep.KC08, progress) { storage.finalExists(value.runId) }
        if (collision is Attempt.Failure) return progress.failure(collision)
        if ((collision as Attempt.Success).value) {
            return BootstrapResult.Rejected(
                KeyRecoveryClassification.KEY_REF_COLLISION,
                progress.steps(),
                progress.remainder(),
            )
        }
        val renamed =
            attempt(BootstrapStep.KC08, progress) { storage.renameTempToFinal(value.runId) }
        if (renamed is Attempt.Failure) return progress.failure(renamed)
        progress.finalRenamed = true
        progress.complete(BootstrapStep.KC08)

        val parentSync =
            attempt(BootstrapStep.KC09, progress) { storage.fsyncConfirmationParent(value.runId) }
        if (parentSync is Attempt.Failure) return progress.failure(parentSync)
        progress.finalDirectorySynced = true
        progress.complete(BootstrapStep.KC09)

        val row =
            RecoveryBootstrapRunRow(
                runId = value.runId.toCanonicalString(),
                candidateId = value.candidate.contractId,
                keyConfirmationRelativeName = FINAL_RELATIVE_NAME,
                keyConfirmationBytes = ciphertext.size.toLong(),
                keyConfirmationSha256 = Sha256Value.calculate(ciphertext),
                canonicalAliasSha256 = value.canonicalAliasSha256,
                keyConfirmationState = KeyConfirmationState.VALID,
            )
        val journalFailure = commitJournal(row, progress)
        if (journalFailure != null) return journalFailure

        val capability = BootstrapPublicationCapability(value, successfulEndTransactionProof)
        val evidence = attempt(BootstrapStep.KC13, progress) { evidenceSink.emit(row) }
        return if (evidence is Attempt.Success) {
            progress.evidenceEmitted = true
            progress.complete(BootstrapStep.KC13)
            BootstrapResult.Committed(
                capability,
                true,
                null,
                progress.steps(),
                progress.remainder(),
            )
        } else {
            evidence as Attempt.Failure
            BootstrapResult.Committed(
                capability,
                false,
                evidence.cause,
                progress.steps(),
                progress.remainder(),
            )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun writeAndSync(
        handle: BootstrapWriteHandle,
        ciphertext: ByteArray,
        progress: Progress,
    ): BootstrapResult? {
        var pendingFailure: Attempt.Failure? = null
        try {
            var offset = 0
            while (offset < ciphertext.size) {
                val written = storage.write(handle, ciphertext, offset, ciphertext.size - offset)
                val remaining = ciphertext.size - offset
                check(written in 1..remaining) {
                    "Bootstrap ciphertext write made invalid progress"
                }
                offset += written
            }
            progress.temporaryFullyWritten = true
            progress.complete(BootstrapStep.KC06)
            storage.fsyncTemp(handle)
            progress.temporaryFileSynced = true
            progress.complete(BootstrapStep.KC07)
        } catch (error: Throwable) {
            pendingFailure =
                Attempt.Failure(
                    if (progress.temporaryFullyWritten) BootstrapStep.KC07 else BootstrapStep.KC06,
                    error,
                )
        } finally {
            try {
                storage.closeTemp(handle)
            } catch (closeError: Throwable) {
                if (pendingFailure == null) {
                    pendingFailure = Attempt.Failure(BootstrapStep.KC07, closeError)
                } else {
                    pendingFailure.cause.addSuppressed(closeError)
                }
            }
        }
        return pendingFailure?.let(progress::failure)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun commitJournal(
        row: RecoveryBootstrapRunRow,
        progress: Progress,
    ): BootstrapResult? {
        val begun = attempt(BootstrapStep.KC10, progress) { journal.beginNonExclusive() }
        if (begun is Attempt.Failure) return progress.failure(begun)
        val transaction = (begun as Attempt.Success).value
        var failure: Attempt.Failure? = null
        try {
            transaction.insert(row)
            progress.runRowInserted = true
            progress.complete(BootstrapStep.KC10)
            transaction.markSuccessful()
            progress.transactionMarkedSuccessful = true
            progress.complete(BootstrapStep.KC11)
        } catch (error: Throwable) {
            failure =
                Attempt.Failure(
                    if (progress.contains(BootstrapStep.KC10)) BootstrapStep.KC11
                    else BootstrapStep.KC10,
                    error,
                )
        } finally {
            try {
                transaction.end()
                if (failure == null) {
                    progress.transactionCommitted = true
                    progress.complete(BootstrapStep.KC12)
                }
            } catch (endError: Throwable) {
                if (failure == null) {
                    failure = Attempt.Failure(BootstrapStep.KC12, endError)
                } else {
                    failure.cause.addSuppressed(endError)
                }
            }
        }
        return failure?.let(progress::failure)
    }

    @Suppress("TooGenericExceptionCaught")
    private fun <T> attempt(
        step: BootstrapStep,
        @Suppress("UNUSED_PARAMETER") progress: Progress,
        block: () -> T,
    ): Attempt<T> =
        try {
            Attempt.Success(block())
        } catch (error: Throwable) {
            Attempt.Failure(step, error)
        }

    private sealed interface Attempt<out T> {
        data class Success<T>(val value: T) : Attempt<T>

        data class Failure(val step: BootstrapStep, val cause: Throwable) : Attempt<Nothing>
    }

    private class Progress {
        private val completed = mutableListOf<BootstrapStep>()
        var aliasCreationState = BootstrapAliasCreationState.NOT_ATTEMPTED
        var temporaryCreated = false
        var temporaryFullyWritten = false
        var temporaryFileSynced = false
        var finalRenamed = false
        var finalDirectorySynced = false
        var runRowInserted = false
        var transactionMarkedSuccessful = false
        var transactionCommitted = false
        var evidenceEmitted = false

        fun complete(step: BootstrapStep) {
            check(completed.lastOrNull()?.ordinal?.let { step.ordinal > it } ?: true) {
                "Bootstrap steps must complete in strict order"
            }
            completed += step
        }

        fun contains(step: BootstrapStep): Boolean = step in completed

        fun steps(): List<BootstrapStep> = completed.toList()

        fun remainder(): BootstrapDurableRemainder =
            BootstrapDurableRemainder(
                aliasCreationState,
                temporaryCreated,
                temporaryFullyWritten,
                temporaryFileSynced,
                finalRenamed,
                finalDirectorySynced,
                runRowInserted,
                transactionMarkedSuccessful,
                transactionCommitted,
                evidenceEmitted,
            )

        fun failure(failure: Attempt.Failure): BootstrapResult.Failed =
            BootstrapResult.Failed(failure.step, failure.cause, steps(), remainder())
    }

    private companion object {
        const val FINAL_RELATIVE_NAME = "key-confirmation/run.kc"
    }
}
