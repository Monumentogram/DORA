@file:Suppress(
    "TooManyFunctions"
) // Serialized vault ownership and authenticated delivery remain together.

package com.monumentogram.dora.audio.persistence.runtime

import com.monumentogram.dora.audio.AudioAvailability
import com.monumentogram.dora.audio.AudioFailure
import com.monumentogram.dora.audio.AudioFormat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.AudioOpenMode
import com.monumentogram.dora.audio.AudioReadSummary
import com.monumentogram.dora.audio.AudioResult
import com.monumentogram.dora.audio.AudioSourceState
import com.monumentogram.dora.audio.AudioStorageUnitIdentity
import com.monumentogram.dora.audio.OriginalAudioPort
import com.monumentogram.dora.audio.OriginalAudioReference
import com.monumentogram.dora.audio.ProductAudioReaderPort
import com.monumentogram.dora.audio.ProductAudioSession
import com.monumentogram.dora.audio.ProductAudioWriterPort
import com.monumentogram.dora.audio.SegmentationMetadata
import com.monumentogram.dora.audio.VaultKeyProtection
import com.monumentogram.dora.audio.persistence.auth.AppLockSession
import com.monumentogram.dora.audio.persistence.auth.AppLockedException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

internal interface RuntimeVault : AutoCloseable {
    fun recordingRecoveryPage(
        after: String
    ): AudioResult<List<com.monumentogram.dora.audio.recording.RecordingRecovery>> =
        AudioResult.Failed(AudioFailure.UNAVAILABLE)

    val originals: OriginalAudioPort
    val writer: ProductAudioWriterPort
    val reader: ProductAudioReaderPort
    val protection: VaultKeyProtection

    fun sourceState(identity: AudioIdentity): AudioResult<AudioSourceState>

    fun deleteConfirmed(identity: AudioIdentity): AudioResult<Unit>

    fun retryDeletion(identity: AudioIdentity): AudioResult<Unit>
}

/** All resources and operations belong to one worker; revocation never waits for disk cleanup. */
internal class AudioRuntimeCoordinator(
    private val isMainThread: () -> Boolean,
    private val openVault:
        (AudioOpenMode, AppLockSession.Authorization) -> AudioResult<RuntimeVault>,
) : AutoCloseable {
    private companion object {
        const val CLOSE_WAIT_SECONDS = 5L
    }

    private val monitor = Any()
    @Volatile private var workerThread: Thread? = null
    private val worker: ExecutorService = Executors.newSingleThreadExecutor { task ->
        Thread(task, "Dora encrypted persistence").also {
            workerThread = it
            it.isDaemon = true
        }
    }
    private var epoch = 0L
    private var current: Handle? = null
    private val retiring = ResourceRetirement()
    @Volatile
    var availability: AudioAvailability = AudioAvailability.Locked
        private set

    /** The successful prompt result precedes this separate, revocable authority handoff. */
    fun openAuthenticated(
        mode: AudioOpenMode,
        capture: () -> AppLockSession.Authorization,
        completion: (AudioAvailability) -> Unit,
    ) {
        val authorization =
            try {
                capture()
            } catch (_: AppLockedException) {
                revoke()
                completion(AudioAvailability.Locked)
                return
            }
        open(mode, authorization, completion)
    }

    fun open(
        mode: AudioOpenMode,
        authorization: AppLockSession.Authorization,
        completion: (AudioAvailability) -> Unit,
    ) {
        val request =
            synchronized(monitor) {
                epoch++
                val previous = current
                current = null
                availability = AudioAvailability.Opening
                previous?.let { worker.execute { retire(it.vault) } }
                epoch
            }
        worker.execute {
            var candidate: RuntimeVault? = null
            var published = false
            try {
                authorization.requireActive()
                checkRequest(request)
                retiring.retry()
                if (!retiring.isEmpty) {
                    publishFailure(request, AudioFailure.BUSY)
                } else
                    when (val result = openVault(mode, authorization)) {
                        is AudioResult.Failed -> {
                            authorization.requireActive()
                            publishFailure(request, result.reason)
                        }
                        is AudioResult.Value -> {
                            candidate = result.value
                            authorization.withPlaintextDelivery {
                                synchronized(monitor) {
                                    checkRequest(request)
                                    val handle = Handle(result.value, authorization)
                                    current = handle
                                    availability = AudioAvailability.Available(handle)
                                    published = true
                                }
                            }
                        }
                    }
            } catch (_: AppLockedException) {
                synchronized(monitor) {
                    if (epoch == request) availability = AudioAvailability.Locked
                }
            } catch (_: Exception) {
                publishFailure(request, AudioFailure.UNAVAILABLE)
            } finally {
                if (!published) candidate?.let(::retire)
                completion(availability)
            }
        }
    }

    fun revoke() {
        synchronized(monitor) {
            epoch++
            availability = AudioAvailability.Locked
            current?.let { handle -> worker.execute { retire(handle.vault) } }
            current = null
        }
    }

    /** Exclusive handoff: new service composition is forbidden until retirement actually ends. */
    fun retireForRecording(completion: (Boolean) -> Unit) {
        revoke()
        worker.execute {
            retiring.retry()
            completion(retiring.isEmpty)
        }
    }

    fun recordingRecoveryPage(
        session: ProductAudioSession,
        after: String,
        completion:
            (AudioResult<List<com.monumentogram.dora.audio.recording.RecordingRecovery>>) -> Unit,
    ) {
        val handle = session as? Handle
        if (handle == null || !isCurrent(handle)) {
            completion(AudioResult.Failed(AudioFailure.LOCKED))
            return
        }
        worker.execute { completion(handle.perform { recordingRecoveryPage(after) }) }
    }

    fun isCurrent(session: ProductAudioSession): Boolean =
        synchronized(monitor) { current === session }

    fun requireCurrent(session: ProductAudioSession) {
        val handle = session as? Handle ?: throw AppLockedException()
        handle.authorization.requireActive()
        if (!isCurrent(handle)) throw AppLockedException()
    }

    fun confirmedDelete(
        session: ProductAudioSession,
        identity: AudioIdentity,
        completion: (AudioResult<Unit>) -> Unit,
    ) {
        val handle = session as? Handle
        if (handle == null || !isCurrent(handle)) {
            completion(AudioResult.Failed(AudioFailure.LOCKED))
            return
        }
        worker.execute { completion(handle.perform { deleteConfirmed(identity) }) }
    }

    private fun checkRequest(request: Long) =
        synchronized(monitor) {
            if (epoch != request) throw AppLockedException()
        }

    private fun publishFailure(request: Long, reason: AudioFailure) =
        synchronized(monitor) {
            if (epoch == request) availability = AudioAvailability.Failed(reason)
        }

    private fun retire(vault: RuntimeVault) = retiring.retire(vault)

    override fun close() {
        revoke()
        worker.execute { retiring.retry() }
        worker.shutdown()
        if (Thread.currentThread() !== workerThread)
            check(worker.awaitTermination(CLOSE_WAIT_SECONDS, TimeUnit.SECONDS))
    }

    private inner class Handle(
        val vault: RuntimeVault,
        val authorization: AppLockSession.Authorization,
    ) : ProductAudioSession {
        override val originals =
            object : OriginalAudioPort {
                override fun acquire(identity: AudioIdentity) = invoke {
                    originals.acquire(identity)
                }

                override fun inspect(reference: OriginalAudioReference) = invoke {
                    originals.inspect(reference)
                }

                override fun extract(
                    reference: OriginalAudioReference,
                    consume: (Long, ByteArray) -> Unit,
                ) = invoke {
                    originals.extract(reference) { frame, bytes ->
                        authorization.withPlaintextDelivery { consume(frame, bytes) }
                    }
                }

                override fun withAvailable(reference: OriginalAudioReference, action: () -> Unit) =
                    invoke {
                        originals.withAvailable(reference) {
                            authorization.withPlaintextDelivery {
                                requireCurrent(this@Handle)
                                action()
                            }
                        }
                    }
            }
        override val protection = vault.protection
        override val writer =
            object : ProductAudioWriterPort {
                override fun segmentation(identity: AudioIdentity, metadata: SegmentationMetadata) =
                    invoke {
                        writer.segmentation(identity, metadata)
                    }

                override fun create(identity: AudioIdentity) = invoke { writer.create(identity) }

                override fun append(
                    segment: AudioStorageUnitIdentity,
                    format: AudioFormat,
                    pcm: ByteArray,
                ) = invoke { writer.append(segment, format, pcm) }

                override fun finalize(identity: AudioIdentity) = invoke {
                    writer.finalize(identity)
                }

                override fun reconcile(identity: AudioIdentity) = invoke {
                    writer.reconcile(identity)
                }
            }
        override val reader =
            object : ProductAudioReaderPort {
                override fun segmentation(identity: AudioIdentity, afterKey: String) = invoke {
                    reader.segmentation(identity, afterKey)
                }

                override fun extract(
                    identity: AudioIdentity,
                    consume: (Long, ByteArray) -> Unit,
                ): AudioResult<AudioReadSummary> = invoke {
                    reader.extract(identity) { frame, bytes ->
                        authorization.withPlaintextDelivery { consume(frame, bytes) }
                    }
                }
            }

        override fun sourceState(identity: AudioIdentity) = invoke { sourceState(identity) }

        override fun retryRemainingDeletion(identity: AudioIdentity) = invoke {
            retryDeletion(identity)
        }

        private fun <T> invoke(block: RuntimeVault.() -> AudioResult<T>): AudioResult<T> {
            if (isMainThread() || Thread.currentThread() === workerThread)
                return AudioResult.Failed(AudioFailure.BUSY)
            return try {
                requireCurrent(this)
                if (Thread.currentThread().isInterrupted) AudioResult.Failed(AudioFailure.UNCERTAIN)
                else awaitOperation(worker.submit<AudioResult<T>> { perform(block) })
            } catch (_: AppLockedException) {
                AudioResult.Failed(AudioFailure.LOCKED)
            } catch (_: Exception) {
                AudioResult.Failed(AudioFailure.UNAVAILABLE)
            }
        }

        /** Submitted work owns caller data until completion, even if its caller is interrupted. */
        private fun <T> awaitOperation(future: Future<AudioResult<T>>): AudioResult<T> {
            var interrupted = false
            var completed: AudioResult<T>? = null
            try {
                while (completed == null) {
                    try {
                        completed = future.get()
                    } catch (_: InterruptedException) {
                        // Cancelling a future cannot stop an active callback or release its input.
                        interrupted = true
                    }
                }
            } finally {
                if (interrupted) Thread.currentThread().interrupt()
            }
            val result = checkNotNull(completed)
            return if (interrupted && result is AudioResult.Value) {
                AudioResult.Failed(AudioFailure.UNCERTAIN)
            } else result
        }

        fun <T> perform(block: RuntimeVault.() -> AudioResult<T>): AudioResult<T> =
            try {
                requireCurrent(this)
                val result = vault.block()
                authorization.withPlaintextDelivery { requireCurrent(this) }
                result
            } catch (_: AppLockedException) {
                AudioResult.Failed(AudioFailure.LOCKED)
            } catch (_: Exception) {
                AudioResult.Failed(AudioFailure.UNAVAILABLE)
            }
    }
}
