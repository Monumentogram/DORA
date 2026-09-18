package com.monumentogram.dora.poc.recovery.candidate

import android.content.Context
import com.monumentogram.dora.poc.recovery.contract.MicrofileAad
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.crypto.RecoveryAeadKeyset
import com.monumentogram.dora.poc.recovery.journal.AndroidRecoveryMicrofileJournal
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryCandidateStorage
import java.util.IdentityHashMap

/** Observes actual adapter returns; it does not simulate publication by constructing rows. */
internal object RecoveryCampaignMicrofilePublisher {
    fun create(
        context: Context,
        observer: (String) -> Unit,
    ): RecoveryMicrofilePublicationController {
        val crypto = AndroidRecoveryMicrofileCrypto()
        val observingCrypto =
            object : RecoveryMicrofileCrypto by crypto {
                override fun encryptMicrofile(
                    keyset: RecoveryAeadKeyset,
                    plaintext: ByteArray,
                    aad: MicrofileAad,
                ): ByteArray {
                    observer("K01")
                    val bytes = crypto.encryptMicrofile(keyset, plaintext, aad)
                    observer("K02")
                    return bytes
                }
            }
        val storage = AndroidOsRecoveryCandidateStorage(context)
        val handles = IdentityHashMap<CandidateWriteHandle, String>()
        val observingStorage =
            object : RecoveryCandidateStorage by storage {
                override fun openExclusiveTemp(
                    runId: RunId,
                    temporaryRelativeName: String,
                ): CandidateWriteHandle =
                    storage.openExclusiveTemp(runId, temporaryRelativeName).also {
                        handles[it] = temporaryRelativeName
                    }

                override fun write(
                    handle: CandidateWriteHandle,
                    bytes: ByteArray,
                    offset: Int,
                    count: Int,
                ): Int {
                    val returned = storage.write(handle, bytes, offset, count)
                    if (
                        handles[handle]?.startsWith("units/") == true &&
                            offset + returned == bytes.size
                    )
                        observer("K03")
                    return returned
                }

                override fun fsync(handle: CandidateWriteHandle) {
                    storage.fsync(handle)
                    when {
                        handles[handle]?.startsWith("units/") == true -> observer("K04")
                        handles[handle]?.startsWith("manifests/") == true -> observer("K07")
                    }
                }

                override fun close(handle: CandidateWriteHandle) {
                    try {
                        storage.close(handle)
                    } finally {
                        handles.remove(handle)
                    }
                }

                override fun fsyncParent(runId: RunId, finalRelativeName: String) {
                    storage.fsyncParent(runId, finalRelativeName)
                    when {
                        finalRelativeName.startsWith("units/") -> observer("K05")
                        finalRelativeName.startsWith("key-envelopes/manifest-") -> observer("K06")
                        finalRelativeName.startsWith("manifests/") -> observer("K08")
                    }
                }
            }
        val journal = AndroidRecoveryMicrofileJournal(context)
        val observingJournal =
            object : RecoveryMicrofileJournal by journal {
                override fun beginNonExclusive(): RecoveryMicrofileTransaction {
                    val transaction = journal.beginNonExclusive()
                    var successful = false
                    return object : RecoveryMicrofileTransaction by transaction {
                        override fun markSuccessful() {
                            transaction.markSuccessful()
                            successful = true
                        }

                        override fun end() {
                            var observationFailure: Throwable? = null
                            if (successful)
                                try {
                                    observer("K09")
                                } catch (failure: Throwable) {
                                    observationFailure = failure
                                }
                            try {
                                transaction.end()
                            } catch (failure: Throwable) {
                                observationFailure?.let(failure::addSuppressed)
                                throw failure
                            }
                            observationFailure?.let { throw it }
                            if (successful) observer("K10")
                        }
                    }
                }
            }
        return RecoveryMicrofilePublicationController(
            observingCrypto,
            observingStorage,
            observingJournal,
            RecoveryMicrofileEvidenceSink { _, _ -> observer("COMMITTED-EVENT") },
        )
    }
}
