package com.monumentogram.dora.audio.persistence

import android.content.Context
import android.content.ContextWrapper
import android.system.Os
import android.system.OsConstants
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioReadSummary
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioSourceState
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.EncryptedAudioCatalog
import com.monumentogram.dora.audio.OriginalAudioLifecycle
import com.monumentogram.dora.audio.ProductAudioReaderPort
import com.monumentogram.dora.audio.ProductAudioRecoverySource
import com.monumentogram.dora.audio.ProductAudioWriterPort
import com.monumentogram.dora.audio.RecoveryAudioBridge
import com.monumentogram.dora.audio.VaultKeyProtection
import com.monumentogram.dora.audio.persistence.auth.AppLockedException
import com.monumentogram.dora.audio.persistence.database.SqlCipherJournalHelperFactory
import com.monumentogram.dora.audio.persistence.journal.AudioDeletionTargetKind
import com.monumentogram.dora.audio.persistence.journal.RoomAudioJournal
import com.monumentogram.dora.audio.persistence.journal.UncertainAudioSourceState
import com.monumentogram.dora.audio.persistence.keys.AndroidVaultBundleStorage
import com.monumentogram.dora.audio.persistence.keys.AndroidVaultKeyBackend
import com.monumentogram.dora.audio.persistence.keys.AndroidVaultKeystoreIo
import com.monumentogram.dora.audio.persistence.keys.KeyAccess
import com.monumentogram.dora.audio.persistence.keys.KeyBoundaryException
import com.monumentogram.dora.audio.persistence.keys.KeyFailure
import com.monumentogram.dora.audio.persistence.keys.NoLogRecoveryRunAeadBackend
import com.monumentogram.dora.audio.persistence.keys.ProductRecoveryBootstrapCrypto
import com.monumentogram.dora.audio.persistence.keys.VaultKeystoreIo
import com.monumentogram.dora.audio.persistence.keys.VaultSecretStore
import com.monumentogram.dora.audio.persistence.runtime.ResourceRetirement
import com.monumentogram.dora.audio.persistence.runtime.RuntimeVault
import com.monumentogram.dora.poc.recovery.bootstrap.RecoveryKeyBootstrapController
import com.monumentogram.dora.poc.recovery.candidate.AndroidRecoveryMicrofileCrypto
import com.monumentogram.dora.poc.recovery.candidate.RecoveryArtifactContext
import com.monumentogram.dora.poc.recovery.candidate.RecoveryCandidateSnapshot
import com.monumentogram.dora.poc.recovery.candidate.RecoveryCandidateStorage
import com.monumentogram.dora.poc.recovery.candidate.RecoveryMicrofileJournal
import com.monumentogram.dora.poc.recovery.candidate.RecoveryMicrofilePublicationController
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineController
import com.monumentogram.dora.poc.recovery.candidate.RecoveryReconciliationSource
import com.monumentogram.dora.poc.recovery.contract.RecoveryQuarantineIntentInput
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.controller.ExistingRecoveryRunAeadOpener
import com.monumentogram.dora.poc.recovery.controller.RecoveryKeyConfirmationController
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadProvider
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryReconciliationSource
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryBootstrapStorage
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryCandidateStorage
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryReconciliationStorage
import java.io.File
import java.io.FileDescriptor

@Suppress("LongParameterList") // Private constructor owns the single composition resource boundary.
internal class EncryptedAudioVault
private constructor(
    private val journal: RoomAudioJournal,
    private val bridge: RecoveryAudioBridge,
    private val operationGate: () -> Unit,
    private val deliverAuthorized: (() -> Unit) -> Unit,
    private val deletionStorage: AndroidAudioDeletionStorage,
    private val deletionStep: (String) -> Unit,
    override val protection: VaultKeyProtection,
) : RuntimeVault {
    @Volatile private var closed = false

    override val originals =
        OriginalAudioLifecycle(
            journal.sourceOwner,
            journal.sourceVault,
            journal.catalog,
            journal::originalSourceState,
            journal::originalSourceLoss,
            journal::retainOriginalReference,
            bridge::extractFinalizedHeld,
            ::requireActive,
            deliverAuthorized,
        )

    /** Only the explicit confirmed-audio operation may call this internal mutation boundary. */
    fun deleteAudio(identity: AudioIdentity): AudioResult<Unit> = delete(identity, true)

    override fun deleteConfirmed(identity: AudioIdentity) = deleteAudio(identity)

    override fun retryDeletion(identity: AudioIdentity): AudioResult<Unit> = delete(identity, false)

    @Suppress(
        "CyclomaticComplexMethod"
    ) // Keep durable inventory and ordered destructive steps together.
    private fun delete(identity: AudioIdentity, allowNew: Boolean): AudioResult<Unit> = operation {
        val lease =
            journal.catalog.tryAcquire(identity)
                ?: return@operation AudioResult.Failed(AudioFailure.BUSY)
        lease.use {
            val state = journal.sourceState(identity)
            if (
                !allowNew &&
                    state !is AudioSourceState.Deleting &&
                    state != AudioSourceState.UserDeleted
            )
                return@operation AudioResult.Failed(AudioFailure.INVALID_INPUT)
            val snapshot =
                if (allowNew) journal.beginDeletion(identity, emptyList())
                else checkNotNull(journal.loadDeletion(identity))
            if (snapshot.state == "USER_DELETED") return@operation AudioResult.Value(Unit)
            deletionStep("TOMBSTONE")
            val ordered =
                snapshot.steps.sortedBy { step ->
                    when (step.target.kind) {
                        AudioDeletionTargetKind.KEY_REFERENCE -> 0
                        AudioDeletionTargetKind.ARTIFACT,
                        AudioDeletionTargetKind.QUARANTINE_ARTIFACT -> 1
                        AudioDeletionTargetKind.RUN_DIRECTORY -> 2
                    }
                }
            for (step in ordered) {
                if (step.completed) continue
                requireActive()
                if (step.target.kind != AudioDeletionTargetKind.KEY_REFERENCE) {
                    val latest = checkNotNull(journal.loadDeletion(identity))
                    check(
                        latest.steps
                            .filter { it.target.kind == AudioDeletionTargetKind.KEY_REFERENCE }
                            .all { it.completed }
                    )
                }
                deletionStorage.remove(identity, step.target)
                deletionStep(
                    if (step.target.kind == AudioDeletionTargetKind.KEY_REFERENCE) "KEY_REMOVED"
                    else "FILE_REMOVED"
                )
                journal.completeDeletionTarget(identity, step.target) {
                    deletionStorage.verifiedAbsent(step.target)
                }
            }
            deletionStep("COMPLETE")
            journal.completeDeletion(identity) {
                ordered.all { deletionStorage.verifiedAbsent(it.target) }
            }
            AudioResult.Value(Unit)
        }
    }

    override fun sourceState(identity: AudioIdentity): AudioResult<AudioSourceState> = operation {
        val lease =
            journal.catalog.tryAcquire(identity)
                ?: return@operation AudioResult.Failed(AudioFailure.BUSY)
        val state = lease.use { journal.sourceState(identity) }
        if (state != null) return@operation AudioResult.Value(state)
        when (val result = bridge.extract(identity) { _, _ -> }) {
            is AudioResult.Value -> AudioResult.Value(AudioSourceState.Readable(result.value))
            is AudioResult.Failed -> AudioResult.Value(AudioSourceState.Unavailable(result.reason))
        }
    }

    override val writer: ProductAudioWriterPort =
        object : ProductAudioWriterPort {
            override fun create(identity: AudioIdentity) = operation { bridge.create(identity) }

            override fun append(
                segment: AudioStorageUnitIdentity,
                format: AudioFormat,
                pcm: ByteArray,
            ) = operation { bridge.append(segment, format, pcm) }

            override fun finalize(identity: AudioIdentity) = operation { bridge.finalize(identity) }

            override fun reconcile(identity: AudioIdentity) = operation {
                bridge.reconcile(identity)
            }
        }
    override val reader: ProductAudioReaderPort =
        object : ProductAudioReaderPort {
            override fun extract(
                identity: AudioIdentity,
                consume: (Long, ByteArray) -> Unit,
            ): AudioResult<AudioReadSummary> = operation {
                bridge.extract(identity) { frame, bytes ->
                    deliverAuthorized {
                        requireActive()
                        consume(frame, bytes)
                    }
                }
            }
        }

    private fun requireActive() {
        check(!closed)
        operationGate()
    }

    private fun <T> operation(block: () -> AudioResult<T>): AudioResult<T> =
        try {
            requireActive()
            block().also { requireActive() }
        } catch (_: AppLockedException) {
            AudioResult.Failed(AudioFailure.LOCKED)
        } catch (_: UncertainAudioSourceState) {
            AudioResult.Failed(AudioFailure.UNCERTAIN)
        } catch (error: KeyBoundaryException) {
            AudioResult.Failed(error.failure.audioFailure())
        } catch (_: Exception) {
            AudioResult.Failed(AudioFailure.UNAVAILABLE)
        }

    @Synchronized
    override fun close() {
        if (!closed) {
            journal.close()
            closed = true
        }
    }

    /** Decorators receive real platform dependencies; production always uses these defaults. */
    internal data class Dependencies(
        val catalog: (EncryptedAudioCatalog) -> EncryptedAudioCatalog = { it },
        val runKeystore: (VaultKeystoreIo) -> VaultKeystoreIo = { it },
        val deletionStep: (String) -> Unit = {},
        val deletionFsync: ((FileDescriptor) -> Unit) -> (FileDescriptor) -> Unit = { it },
        val helperFactory: (SupportSQLiteOpenHelper.Factory) -> SupportSQLiteOpenHelper.Factory = {
            it
        },
        val candidateStorage: (RecoveryCandidateStorage) -> RecoveryCandidateStorage = { it },
        val microfileJournal: (RecoveryMicrofileJournal) -> RecoveryMicrofileJournal = { it },
    )

    companion object {
        private const val OWNER_READ_WRITE = 0x180
        private val failedOpenCleanup = ResourceRetirement()

        fun createNew(
            context: Context,
            operationGate: () -> Unit,
        ): AudioResult<EncryptedAudioVault> =
            open(
                context,
                true,
                operationGate,
                { block ->
                    operationGate()
                    block()
                },
            )

        fun openExisting(
            context: Context,
            operationGate: () -> Unit,
        ): AudioResult<EncryptedAudioVault> =
            open(
                context,
                false,
                operationGate,
                { block ->
                    operationGate()
                    block()
                },
            )

        /** Authentication composition supplies an epoch gate and serialized plaintext delivery. */
        @Suppress(
            "LongMethod",
            "TooGenericExceptionCaught",
            "ReturnCount",
        ) // One construction/cleanup boundary for all vault resources.
        fun open(
            context: Context,
            create: Boolean,
            operationGate: () -> Unit,
            deliverAuthorized: (() -> Unit) -> Unit,
            dependencies: Dependencies = Dependencies(),
        ): AudioResult<EncryptedAudioVault> {
            var journal: RoomAudioJournal? = null
            return try {
                operationGate()
                failedOpenCleanup.retry()
                if (!failedOpenCleanup.isEmpty) return AudioResult.Failed(AudioFailure.BUSY)
                val storage = AndroidVaultBundleStorage(context)
                val keyStore = VaultSecretStore(storage, AndroidVaultKeyBackend(context))
                val result = if (create) keyStore.createNew() else keyStore.openExisting()
                if (result is KeyAccess.Unavailable)
                    return AudioResult.Failed(result.failure.audioFailure())
                val secrets = (result as KeyAccess.Available).value
                operationGate()
                val file =
                    File(storage.vaultDirectory, "journal-${secrets.databaseObjectSelector}.db")
                admitJournalFile(file, create)
                val openedJournal = secrets.borrowDatabaseSecret { secret ->
                    val factory = SqlCipherJournalHelperFactory(context, file, secret)
                    try {
                        RoomAudioJournal.open(
                            context,
                            file,
                            dependencies.helperFactory(factory),
                            secrets.ownerId,
                            secrets.vaultId,
                            operationGate,
                        )
                    } catch (error: Exception) {
                        try {
                            factory.close()
                        } catch (_: Exception) {
                            /* Original open failure remains authoritative. */
                        }
                        throw error
                    }
                }
                journal = openedJournal
                val scoped = VaultStorageContext(context, storage.vaultDirectory)
                val backend =
                    NoLogRecoveryRunAeadBackend(
                        context,
                        secrets.vaultId,
                        dependencies.runKeystore(AndroidVaultKeystoreIo),
                    )
                val provider = RecoveryRunAeadProvider(backend)
                val crypto = AndroidRecoveryMicrofileCrypto(provider)
                val reconciliationStorage = AndroidOsRecoveryReconciliationStorage(scoped)
                val source =
                    AndroidRecoveryReconciliationSource(
                        loadBootstrap = openedJournal::loadBootstrapIdentity,
                        loadSnapshot = openedJournal::loadCandidateSnapshot,
                        loadPending = openedJournal::loadPendingQuarantine,
                        loadAllIntents = openedJournal::loadAllQuarantine,
                        storage = reconciliationStorage,
                        aliasExists = backend::aliasExists,
                        loadRetained = openedJournal::loadRetainedMicrofileSource,
                    )
                val productSource =
                    object : ProductAudioRecoverySource, RecoveryReconciliationSource by source {
                        override fun loadPendingQuarantine(runId: RunId) =
                            source.loadPendingQuarantine(runId)

                        override fun loadInventorySnapshot(
                            runId: RunId,
                            candidate: RecoveryCandidateSnapshot,
                        ) = source.loadInventorySnapshot(runId, candidate)

                        override fun loadRetainedArtifact(
                            original: RecoveryQuarantineIntentInput,
                            context: RecoveryArtifactContext,
                        ) = source.loadRetainedArtifact(original, context)
                    }
                val bridge =
                    RecoveryAudioBridge(
                        dependencies.catalog(openedJournal.catalog),
                        RecoveryKeyBootstrapController(
                            ProductRecoveryBootstrapCrypto(backend),
                            AndroidOsRecoveryBootstrapStorage(scoped),
                            openedJournal.bootstrapJournal,
                            {},
                        ),
                        RecoveryMicrofilePublicationController(
                            crypto,
                            dependencies.candidateStorage(
                                AndroidOsRecoveryCandidateStorage(scoped)
                            ),
                            dependencies.microfileJournal(openedJournal.microfileJournal),
                            { _, _ -> },
                        ),
                        productSource,
                        crypto,
                        RecoveryKeyConfirmationController(
                            ExistingRecoveryRunAeadOpener(provider::openExisting)
                        ),
                        RecoveryQuarantineController(
                            reconciliationStorage,
                            openedJournal.quarantineJournal,
                            {},
                        ),
                        keyFailures = backend.failureScopes,
                    )
                operationGate()
                AudioResult.Value(
                    EncryptedAudioVault(
                        openedJournal,
                        bridge,
                        operationGate,
                        deliverAuthorized,
                        AndroidAudioDeletionStorage(
                            storage.vaultDirectory,
                            backend,
                            openedJournal::hasCommittedDeletionBootstrap,
                            dependencies.deletionFsync(Os::fsync),
                        ),
                        dependencies.deletionStep,
                        VaultKeyProtection.valueOf(result.protection.name),
                    )
                )
            } catch (error: Exception) {
                journal?.let(failedOpenCleanup::retire)
                AudioResult.Failed(
                    when (error) {
                        is AppLockedException -> AudioFailure.LOCKED
                        is KeyBoundaryException -> error.failure.audioFailure()
                        else -> AudioFailure.UNAVAILABLE
                    }
                )
            }
        }

        private fun admitJournalFile(file: File, create: Boolean) {
            if (create) {
                val fd =
                    Os.open(
                        file.path,
                        OsConstants.O_WRONLY or
                            OsConstants.O_CREAT or
                            OsConstants.O_EXCL or
                            OsConstants.O_NOFOLLOW,
                        OWNER_READ_WRITE,
                    )
                try {
                    Os.fsync(fd)
                } finally {
                    Os.close(fd)
                }
                val parent =
                    Os.open(
                        checkNotNull(file.parent),
                        OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW,
                        0,
                    )
                try {
                    check(OsConstants.S_ISDIR(Os.fstat(parent).st_mode))
                    Os.fsync(parent)
                } finally {
                    Os.close(parent)
                }
            }
            val stat = Os.lstat(file.path)
            check(OsConstants.S_ISREG(stat.st_mode) && stat.st_nlink == 1L)
            if (!create) check(stat.st_size > 0) { "Incomplete encrypted journal" }
        }

        private fun KeyFailure.audioFailure() =
            when (this) {
                KeyFailure.TEMPORARILY_UNAVAILABLE -> AudioFailure.KEY_UNAVAILABLE
                KeyFailure.PERMANENTLY_MISSING_OR_INVALIDATED -> AudioFailure.KEY_INVALIDATED
                KeyFailure.AUTHENTICATION_FAILED -> AudioFailure.AUTHENTICATION_FAILED
                KeyFailure.CORRUPT_CIPHERTEXT -> AudioFailure.CORRUPT
                KeyFailure.NAMESPACE_OCCUPIED -> AudioFailure.COLLISION
                KeyFailure.INCOMPLETE_BOOTSTRAP -> AudioFailure.INCOMPLETE
                KeyFailure.STORAGE_FAILURE -> AudioFailure.UNAVAILABLE
            }
    }
}

/**
 * Every accepted storage adapter must resolve the same isolated vault, including appContext users.
 */
private class VaultStorageContext(base: Context, private val root: File) : ContextWrapper(base) {
    override fun getNoBackupFilesDir(): File = root

    override fun getApplicationContext(): Context = this
}
