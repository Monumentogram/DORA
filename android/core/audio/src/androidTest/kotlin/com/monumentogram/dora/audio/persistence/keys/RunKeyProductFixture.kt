package com.monumentogram.dora.audio.persistence.keys

import android.content.Context
import android.content.ContextWrapper
import com.monumentogram.dora.audio.ProductAudioRecoverySource
import com.monumentogram.dora.audio.RecoveryAudioBridge
import com.monumentogram.dora.audio.persistence.database.SqlCipherJournalHelperFactory
import com.monumentogram.dora.audio.persistence.journal.RoomAudioJournal
import com.monumentogram.dora.poc.recovery.bootstrap.RecoveryKeyBootstrapController
import com.monumentogram.dora.poc.recovery.candidate.AndroidRecoveryMicrofileCrypto
import com.monumentogram.dora.poc.recovery.candidate.RecoveryArtifactContext
import com.monumentogram.dora.poc.recovery.candidate.RecoveryCandidateSnapshot
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
import java.util.UUID

/**
 * Faults only the platform seam; catalog, storage, crypto and controllers are production defaults.
 */
internal class RunKeyProductFixture(operations: VaultKeystoreIo) : AutoCloseable {
    private val base =
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
    private val root =
        File(base.noBackupFilesDir, "run-key-test-${UUID.randomUUID()}").apply { check(mkdir()) }
    val context =
        object : ContextWrapper(base) {
            override fun getNoBackupFilesDir() = root

            override fun getApplicationContext(): Context = this
        }
    val backend: NoLogRecoveryRunAeadBackend
    val journal: RoomAudioJournal
    val bridge: RecoveryAudioBridge

    init {
        val storage = AndroidVaultBundleStorage(context)
        val result =
            VaultSecretStore(storage, AndroidVaultKeyBackend(context)).createNew()
                as KeyAccess.Available
        val secrets = result.value
        backend = NoLogRecoveryRunAeadBackend(context, secrets.vaultId, operations)
        val file = File(storage.vaultDirectory, "journal-${secrets.databaseObjectSelector}.db")
        journal = secrets.borrowDatabaseSecret { secret ->
            RoomAudioJournal.open(
                context,
                file,
                SqlCipherJournalHelperFactory(context, file, secret),
                secrets.ownerId,
                secrets.vaultId,
            ) {}
        }
        val scoped =
            object : ContextWrapper(context) {
                override fun getNoBackupFilesDir() = storage.vaultDirectory

                override fun getApplicationContext(): Context = this
            }
        val provider = RecoveryRunAeadProvider(backend)
        val crypto = AndroidRecoveryMicrofileCrypto(provider)
        val reconciliationStorage = AndroidOsRecoveryReconciliationStorage(scoped)
        val source =
            AndroidRecoveryReconciliationSource(
                loadBootstrap = journal::loadBootstrapIdentity,
                loadSnapshot = journal::loadCandidateSnapshot,
                loadPending = journal::loadPendingQuarantine,
                loadAllIntents = journal::loadAllQuarantine,
                storage = reconciliationStorage,
                aliasExists = backend::aliasExists,
                loadRetained = journal::loadRetainedMicrofileSource,
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
        bridge =
            RecoveryAudioBridge(
                journal.catalog,
                RecoveryKeyBootstrapController(
                    ProductRecoveryBootstrapCrypto(backend),
                    AndroidOsRecoveryBootstrapStorage(scoped),
                    journal.bootstrapJournal,
                    {},
                ),
                RecoveryMicrofilePublicationController(
                    crypto,
                    AndroidOsRecoveryCandidateStorage(scoped),
                    journal.microfileJournal,
                    { _, _ -> },
                ),
                productSource,
                crypto,
                RecoveryKeyConfirmationController(
                    ExistingRecoveryRunAeadOpener(provider::openExisting)
                ),
                RecoveryQuarantineController(reconciliationStorage, journal.quarantineJournal, {}),
                keyFailures = backend.failureScopes,
            )
    }

    override fun close() = journal.close()
}
