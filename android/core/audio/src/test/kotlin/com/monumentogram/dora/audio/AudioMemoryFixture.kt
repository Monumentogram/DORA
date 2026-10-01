@file:Suppress("WildcardImport", "TooManyFunctions", "LargeClass", "ReturnCount")

package com.monumentogram.dora.audio

import com.google.crypto.tink.Aead
import com.google.crypto.tink.subtle.AesGcmJce
import com.monumentogram.dora.poc.recovery.bootstrap.*
import com.monumentogram.dora.poc.recovery.candidate.*
import com.monumentogram.dora.poc.recovery.contract.*
import com.monumentogram.dora.poc.recovery.controller.*
import com.monumentogram.dora.poc.recovery.crypto.*
import java.io.ByteArrayOutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/** Test-only volatile collaborators; all audio artifacts use the real accepted Tink crypto. */
internal class AudioMemoryFixture {
    val files = mutableMapOf<String, ByteArray>()
    val keys = mutableMapOf<String, Aead>()
    val units = mutableListOf<RecoveryMicrofileUnitRow>()
    val publications = mutableListOf<RecoveryManifestPublicationRow>()
    private val rows = mutableMapOf<String, RecoveryBootstrapRunRow>()
    val catalog = MemoryAudioCatalog()
    val quarantine = MemoryQuarantineFixture(files)
    var inventory = emptyList<RecoveryInventoryEntry>()

    private val provider =
        RecoveryRunAeadProvider(
            object : RecoveryRunAeadBackend {
                override fun generateNew(keyUri: String) {
                    check(keyUri !in keys)
                    val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
                    keys[keyUri] = AesGcmJce(key)
                    key.fill(0)
                }

                override fun getAead(keyUri: String): Aead =
                    keys[keyUri] ?: throw GeneralSecurityException("Unavailable test key")
            }
        )

    private class Handle(val name: String) : BootstrapWriteHandle, CandidateWriteHandle {
        val buffer = ByteArrayOutputStream()
    }

    private fun open(name: String): Handle {
        check(name !in files)
        files[name] = byteArrayOf()
        return Handle(name)
    }

    private fun close(handle: Handle) {
        files[handle.name] = handle.buffer.toByteArray()
    }

    private fun rename(from: String, to: String) {
        check(to !in files)
        files[to] = requireNotNull(files.remove(from))
    }

    private val bootstrap =
        RecoveryKeyBootstrapController(
            object : RecoveryBootstrapCrypto {
                override fun aliasExists(runId: RunId) =
                    CanonicalRecoveryAlias.forRun(runId) in keys

                override fun createNewAlias(runId: RunId) =
                    BootstrapAliasCreation.Created(provider.createNew(runId))

                override fun encryptConfirmation(
                    runAead: RecoveryRunAead,
                    value: KeyConfirmationValue,
                ) = runAead.encryptKeyConfirmation(value)
            },
            object : RecoveryBootstrapStorage {
                override fun inspectNamespaces(runId: RunId): BootstrapNamespaceState =
                    BootstrapNamespaceState(
                        final =
                            if (files.keys.any { it.startsWith("${runId.toCanonicalString()}/") })
                                BootstrapNamespaceOccupancy.OCCUPIED_SAFE
                            else BootstrapNamespaceOccupancy.ABSENT
                    )

                override fun openExclusiveConfirmationTemp(runId: RunId) =
                    open("${runId.toCanonicalString()}/confirmation.tmp")

                override fun write(
                    handle: BootstrapWriteHandle,
                    bytes: ByteArray,
                    offset: Int,
                    count: Int,
                ): Int {
                    (handle as Handle).buffer.write(bytes, offset, count)
                    return count
                }

                override fun fsyncTemp(handle: BootstrapWriteHandle) = Unit

                override fun closeTemp(handle: BootstrapWriteHandle) = close(handle as Handle)

                override fun finalExists(runId: RunId) =
                    "${runId.toCanonicalString()}/key-confirmation/run.kc" in files

                override fun renameTempToFinal(runId: RunId) =
                    rename(
                        "${runId.toCanonicalString()}/confirmation.tmp",
                        "${runId.toCanonicalString()}/key-confirmation/run.kc",
                    )

                override fun fsyncConfirmationParent(runId: RunId) = Unit
            },
            object : RecoveryRunBootstrapJournal {
                override fun beginNonExclusive() =
                    object : RecoveryRunBootstrapTransaction {
                        private var row: RecoveryBootstrapRunRow? = null
                        private var commit = false

                        override fun insert(value: RecoveryBootstrapRunRow) {
                            check(value.runId !in rows)
                            row = value
                        }

                        override fun markSuccessful() {
                            commit = true
                        }

                        override fun end() {
                            if (commit) requireNotNull(row).let { rows[it.runId] = it }
                        }
                    }
            },
            BootstrapEvidenceSink {},
        )

    private val crypto = AndroidRecoveryMicrofileCrypto(provider)
    private val publisher =
        RecoveryMicrofilePublicationController(
            crypto,
            object : RecoveryCandidateStorage {
                override fun openExclusiveTemp(runId: RunId, temporaryRelativeName: String) =
                    open("${runId.toCanonicalString()}/$temporaryRelativeName")

                override fun write(
                    handle: CandidateWriteHandle,
                    bytes: ByteArray,
                    offset: Int,
                    count: Int,
                ): Int {
                    (handle as Handle).buffer.write(bytes, offset, count)
                    return count
                }

                override fun fsync(handle: CandidateWriteHandle) = Unit

                override fun close(handle: CandidateWriteHandle) =
                    this@AudioMemoryFixture.close(handle as Handle)

                override fun finalExists(runId: RunId, finalRelativeName: String) =
                    "${runId.toCanonicalString()}/$finalRelativeName" in files

                override fun renameTempToFinal(
                    runId: RunId,
                    temporaryRelativeName: String,
                    finalRelativeName: String,
                ) =
                    rename(
                        "${runId.toCanonicalString()}/$temporaryRelativeName",
                        "${runId.toCanonicalString()}/$finalRelativeName",
                    )

                override fun fsyncParent(runId: RunId, finalRelativeName: String) = Unit
            },
            object : RecoveryMicrofileJournal {
                override fun loadSnapshot(runId: RunId) = candidate(runId)

                override fun beginNonExclusive() =
                    object : RecoveryMicrofileTransaction {
                        private var unit: RecoveryMicrofileUnitRow? = null
                        private var publication: RecoveryManifestPublicationRow? = null
                        private var commit = false

                        override fun insert(
                            unit: RecoveryMicrofileUnitRow,
                            publication: RecoveryManifestPublicationRow,
                        ) {
                            check(
                                units.none {
                                    it.runId == unit.runId && it.unitIndex == unit.unitIndex
                                }
                            )
                            this.unit = unit
                            this.publication = publication
                        }

                        override fun markSuccessful() {
                            commit = true
                        }

                        override fun end() {
                            if (commit) {
                                units += requireNotNull(unit)
                                publications += requireNotNull(publication)
                            }
                        }
                    }
            },
            RecoveryMicrofileEvidenceSink { _, _ -> },
        )

    private fun candidate(run: RunId): RecoveryCandidateSnapshot {
        val id = run.toCanonicalString()
        return RecoveryCandidateSnapshot(
            listOfNotNull(
                rows[id]?.let { CandidateBootstrapRow(id, it.candidateId, it.keyConfirmationState) }
            ),
            units.filter { it.runId == id },
            publications.filter { it.runId == id },
        )
    }

    private val source =
        object : ProductAudioRecoverySource {
            override fun loadInventorySnapshot(runId: RunId, candidate: RecoveryCandidateSnapshot) =
                RecoveryInventorySnapshot(loadInventory(runId), emptyList())

            override fun loadRetainedArtifact(
                original: RecoveryQuarantineIntentInput,
                context: RecoveryArtifactContext,
            ): RecoveryArtifactBytes? {
                val row =
                    quarantine.rows.values.singleOrNull {
                        it.input.runId == original.runId &&
                            it.input.sourceRelativeName == original.sourceRelativeName
                    } ?: return null
                return files["${original.runId.toCanonicalString()}/${row.destinationRelativeName}"]
                    ?.let { RecoveryArtifactBytes(original.sourceRelativeName, it) }
            }

            override fun loadPendingQuarantine(runId: RunId) = quarantine.pending(runId)

            override fun loadInventory(runId: RunId) = inventory.filter {
                it.input.runId == runId &&
                    "${runId.toCanonicalString()}/${it.input.sourceRelativeName}" in files
            }

            override fun loadConfirmation(runId: RunId): KeyConfirmationSnapshot {
                val id = runId.toCanonicalString()
                val value = KeyConfirmationValue(RecoveryCandidate.MICROFILE, runId)
                val row = rows[id]
                val artifact = files["$id/key-confirmation/run.kc"]
                return KeyConfirmationSnapshot(
                    value,
                    row?.let {
                        StoredKeyConfirmationIdentity(
                            value,
                            it.keyConfirmationRelativeName,
                            it.keyConfirmationBytes,
                            it.keyConfirmationSha256,
                            it.canonicalAliasSha256,
                        )
                    },
                    artifact?.let {
                        ConfirmationArtifactSnapshot(
                            "key-confirmation/run.kc",
                            ConfirmationPathObservation(true, true, true, true),
                            it,
                        )
                    },
                    false,
                    if (value.canonicalAlias in keys) AliasObservation.PRESENT
                    else AliasObservation.ABSENT,
                )
            }

            override fun loadCandidate(runId: RunId) = candidate(runId)

            override fun loadArtifact(
                runId: RunId,
                relativeName: String,
                context: RecoveryArtifactContext,
            ) =
                files["${runId.toCanonicalString()}/$relativeName"]?.let {
                    RecoveryArtifactBytes(relativeName, it)
                }
        }

    fun newBridge() =
        RecoveryAudioBridge(
            catalog,
            bootstrap,
            publisher,
            source,
            crypto,
            RecoveryKeyConfirmationController(
                ExistingRecoveryRunAeadOpener(provider::openExisting)
            ),
            quarantine.controller,
        )

    val bridge = newBridge()
}

internal class MemoryAudioCatalog : EncryptedAudioCatalog {
    val assets = mutableMapOf<String, StoredAudioAsset>()
    private val runs = mutableSetOf<String>()
    private val busy = AtomicBoolean()
    var rejectCommit = false

    override fun create(identity: AudioIdentity): Boolean {
        if (identity.assetId.value in assets) return false
        assets[identity.assetId.value] = StoredAudioAsset(identity)
        return true
    }

    override fun tryAcquire(identity: AudioIdentity): AutoCloseable? =
        if (busy.compareAndSet(false, true)) AutoCloseable { busy.set(false) } else null

    override fun load(identity: AudioIdentity) = assets[identity.assetId.value]

    override fun reserve(expected: StoredAudioAsset, intent: AudioIntent): Boolean {
        if (load(expected.identity) != expected) return false
        if (intent is AudioIntent.Append && !runs.add(intent.identity.unitId)) return false
        assets[expected.identity.assetId.value] = expected.copy(pending = intent)
        return true
    }

    override fun compareAndSet(expected: StoredAudioAsset, next: StoredAudioAsset): Boolean {
        if (rejectCommit || load(expected.identity) != expected) return false
        assets[expected.identity.assetId.value] = next
        return true
    }
}
