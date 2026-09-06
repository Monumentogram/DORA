@file:Suppress(
    "ComplexCondition",
    "LongMethod",
    "MagicNumber",
    "ReturnCount",
    "TooGenericExceptionCaught",
    "TooManyFunctions",
)

package com.monumentogram.dora.poc.recovery.storage

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournal
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalClassification
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalReadResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingPersistenceV07
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingRangeRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingRowValidation
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.coordination.RecoveryRunSingleWriterGuard
import java.io.File
import java.io.FileDescriptor
import java.io.InputStream
import java.security.MessageDigest

internal const val STREAM_SOURCE_RELATIVE_NAME = "stream/stream.ct"
internal val STREAM_OPEN_FLAGS =
    OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW

internal data class RecoveryStreamOpenRequest(
    val runId: RunId,
    val checkpointGeneration: ULong,
    val checkpointIdentity: Sha256Value,
    val sourceRelativeName: String,
    val acceptedEnd: ULong,
    val preFaultSourceBytes: ULong,
    val requestedCiphertextStart: ULong,
    val requestedCiphertextEndExclusive: ULong?,
)

internal data class RecoveryStreamReplayRequest(
    val runId: RunId,
    val outcomeId: Sha256Value,
    val sourceRelativeName: String = STREAM_SOURCE_RELATIVE_NAME,
)

internal interface RecoveryStreamingSource {
    fun <T> withSource(
        access: RecoveryStreamingSourceLeaseAccess,
        request: RecoveryStreamOpenRequest,
        block: (RecoveryOpenedStreamingSource) -> T,
    ): T

    fun verifyReplayHashOnly(
        access: RecoveryStreamingReplayAccess,
        request: RecoveryStreamReplayRequest,
    ): RecoveryReplayHashOnlyResult
}

internal interface RecoveryOpenedStreamingSource {
    val observedBytes: ULong

    fun sha256Prefix(endExclusive: ULong): Sha256Value

    fun sha256Range(startInclusive: ULong, endExclusive: ULong): Sha256Value

    fun boundedInputStream(): InputStream
}

internal sealed interface RecoveryReplayHashOnlyResult {
    data class ExactStoredSourceMetadata(
        val observedBytes: ULong,
        val sourceSha256: Sha256Value,
    ) : RecoveryReplayHashOnlyResult

    data object SourceIdentityChanged : RecoveryReplayHashOnlyResult
}

internal enum class RecoveryStreamingSourceFailure {
    LEASE_BINDING,
    INVALID_REQUEST,
    UNSAFE_PATH,
    JOURNAL,
    ACTIVE_RANGE,
    SOURCE_STRUCTURAL,
    SOURCE_CHANGED,
}

internal class RecoveryStreamingSourceException(
    val failure: RecoveryStreamingSourceFailure,
    val journalClassification: RecoveryStreamingJournalClassification? = null,
) : IllegalStateException("Recovery streaming source denied: $failure")

private class RecoveryStreamingLeaseBinding(val runId: RunId) {
    private val operationLock = Any()
    private var active = true

    fun <T> withActiveOperation(requestRunId: RunId, block: () -> T): T =
        synchronized(operationLock) {
            if (!active || runId != requestRunId) {
                deny(RecoveryStreamingSourceFailure.LEASE_BINDING)
            }
            block()
        }

    fun invalidate() {
        synchronized(operationLock) {
            active = false
        }
    }
}

internal class RecoveryStreamingSourceLeaseAccess
private constructor(private val binding: RecoveryStreamingLeaseBinding) {
    internal fun <T> withBoundTo(runId: RunId, block: () -> T): T =
        binding.withActiveOperation(runId, block)

    internal companion object {
        fun <T> withScopedAccess(
            runId: RunId,
            guard: RecoveryRunSingleWriterGuard,
            block: (RecoveryStreamingSourceLeaseAccess) -> T,
        ): T =
            RecoveryStreamingSourceAccessScope.withLease(runId, guard) { binding ->
                block(RecoveryStreamingSourceLeaseAccess(binding))
            }
    }
}

internal class RecoveryStreamingReplayAccess
private constructor(private val binding: RecoveryStreamingLeaseBinding) {
    internal fun <T> withBoundTo(runId: RunId, block: () -> T): T =
        binding.withActiveOperation(runId, block)

    internal companion object {
        fun <T> withScopedAccess(
            runId: RunId,
            guard: RecoveryRunSingleWriterGuard,
            block: (RecoveryStreamingReplayAccess) -> T,
        ): T =
            RecoveryStreamingSourceAccessScope.withLease(runId, guard) { binding ->
                block(RecoveryStreamingReplayAccess(binding))
            }
    }
}

internal object RecoveryStreamingSourceControllerAccess {
    fun <T> withNormalAccess(
        runId: RunId,
        guard: RecoveryRunSingleWriterGuard,
        block: (RecoveryStreamingSourceLeaseAccess) -> T,
    ): T = RecoveryStreamingSourceLeaseAccess.withScopedAccess(runId, guard, block)

    fun <T> withReplayAccess(
        runId: RunId,
        guard: RecoveryRunSingleWriterGuard,
        block: (RecoveryStreamingReplayAccess) -> T,
    ): T = RecoveryStreamingReplayAccess.withScopedAccess(runId, guard, block)
}

private object RecoveryStreamingSourceAccessScope {
    fun <T> withLease(
        runId: RunId,
        guard: RecoveryRunSingleWriterGuard,
        block: (RecoveryStreamingLeaseBinding) -> T,
    ): T {
        val lease = guard.tryAcquire(runId) ?: deny(RecoveryStreamingSourceFailure.LEASE_BINDING)
        val binding = RecoveryStreamingLeaseBinding(runId)
        val outcome = runCatching { block(binding) }
        binding.invalidate()
        val closeFailure = runCatching { lease.close() }.exceptionOrNull()
        val primary = outcome.exceptionOrNull()
        if (primary != null) {
            closeFailure?.let(primary::addSuppressed)
            throw primary
        }
        if (closeFailure != null) throw closeFailure
        return outcome.getOrThrow()
    }
}

internal enum class RecoveryStreamingPathType {
    REGULAR,
    DIRECTORY,
    SYMLINK,
    OTHER,
}

internal data class RecoveryStreamingStat(
    val type: RecoveryStreamingPathType,
    val size: Long,
)

internal interface RecoveryStreamingRawDescriptor

internal interface RecoveryStreamDescriptor : AutoCloseable {
    val frozenExtent: ULong

    fun readAt(
        sourceOffset: ULong,
        destination: ByteArray,
        destinationOffset: Int,
        count: Int,
    ): Int
}

internal interface RecoveryStreamingOs {
    fun lstat(path: String): RecoveryStreamingStat?

    fun open(path: String, flags: Int): RecoveryStreamingRawDescriptor

    fun fstat(descriptor: RecoveryStreamingRawDescriptor): RecoveryStreamingStat

    fun pread(
        descriptor: RecoveryStreamingRawDescriptor,
        destination: ByteArray,
        destinationOffset: Int,
        count: Int,
        sourceOffset: Long,
    ): Int

    fun close(descriptor: RecoveryStreamingRawDescriptor)
}

internal class AndroidOsRecoveryStreamingSource(
    private val noBackupRoot: File,
    private val journal: RecoveryStreamingJournal,
    private val os: RecoveryStreamingOs = AndroidRecoveryStreamingOs,
) : RecoveryStreamingSource {
    override fun <T> withSource(
        access: RecoveryStreamingSourceLeaseAccess,
        request: RecoveryStreamOpenRequest,
        block: (RecoveryOpenedStreamingSource) -> T,
    ): T =
        access.withBoundTo(request.runId) {
            requireNormalRequest(request)
            val checkpoint =
                requireCheckpoint(
                    request.runId,
                    request.checkpointGeneration,
                    request.checkpointIdentity,
                )
            if (checkpoint.streamCiphertextRelativeName != STREAM_SOURCE_RELATIVE_NAME) {
                deny(RecoveryStreamingSourceFailure.JOURNAL)
            }
            val ranges =
                readJournal(journal.activeRanges(request.runId, STREAM_SOURCE_RELATIVE_NAME))
                    .toList()
            requireStrictActiveRanges(request.runId, ranges)
            requirePermittedRange(request, ranges)
            val sourcePath = requireSafeSourcePath(request.runId, request.sourceRelativeName)

            withOpened(sourcePath) { descriptor ->
                val extent = descriptor.frozenExtent
                requireNormalExtent(request, extent)
                val ceiling = request.requestedCiphertextEndExclusive ?: extent
                if (ceiling > extent || request.requestedCiphertextStart > extent) {
                    deny(RecoveryStreamingSourceFailure.INVALID_REQUEST)
                }
                val scoped =
                    ScopedOpenedSource(
                        descriptor,
                        request.requestedCiphertextStart,
                        ceiling,
                    )
                var primary: Throwable? = null
                try {
                    val result = block(scoped)
                    scoped.probeFrozenEnd()
                    result
                } catch (failure: Throwable) {
                    primary = failure
                    throw failure
                } finally {
                    closeScoped(scoped, primary)
                }
            }
        }

    override fun verifyReplayHashOnly(
        access: RecoveryStreamingReplayAccess,
        request: RecoveryStreamReplayRequest,
    ): RecoveryReplayHashOnlyResult =
        access.withBoundTo(request.runId) {
            requireExactSourceName(request.sourceRelativeName)
            val outcome =
                readJournal(journal.outcomeById(request.outcomeId))
                    ?: deny(RecoveryStreamingSourceFailure.JOURNAL)
            requireReplayOutcome(request, outcome)
            requireCheckpoint(
                outcome.runId,
                outcome.checkpointGeneration,
                outcome.checkpointIdentity,
            )
            requireReplayRange(outcome, readJournal(journal.rangeByOutcome(outcome.outcomeId)))
            val sourcePath = requireSafeSourcePath(request.runId, request.sourceRelativeName)

            withOpened(sourcePath) { descriptor ->
                val extent = descriptor.frozenExtent
                requireReplayExtent(outcome, extent)
                val scoped = ScopedOpenedSource(descriptor, 0UL, extent)
                var primary: Throwable? = null
                try {
                    val actual = scoped.sha256Range(0UL, extent)
                    if (
                        extent != outcome.observedSourceBytes ||
                            actual != outcome.observedSourceSha256
                    ) {
                        RecoveryReplayHashOnlyResult.SourceIdentityChanged
                    } else {
                        RecoveryReplayHashOnlyResult.ExactStoredSourceMetadata(
                            outcome.observedSourceBytes,
                            outcome.observedSourceSha256,
                        )
                    }
                } catch (failure: Throwable) {
                    primary = failure
                    throw failure
                } finally {
                    closeScoped(scoped, primary)
                }
            }
        }

    private fun requireNormalRequest(request: RecoveryStreamOpenRequest) {
        requireExactSourceName(request.sourceRelativeName)
        if (
            request.checkpointGeneration == 0UL ||
                request.requestedCiphertextEndExclusive?.let {
                    it < request.requestedCiphertextStart
                } == true
        ) {
            deny(RecoveryStreamingSourceFailure.INVALID_REQUEST)
        }
        requireAbsoluteWitnessBounds(request.acceptedEnd, request.preFaultSourceBytes)
    }

    private fun requireCheckpoint(
        runId: RunId,
        generation: ULong,
        identity: Sha256Value,
    ) =
        readJournal(journal.checkpointChain(runId)).singleOrNull {
            it.generation == generation && it.checkpointIdentity == identity
        } ?: deny(RecoveryStreamingSourceFailure.JOURNAL)

    private fun requirePermittedRange(
        request: RecoveryStreamOpenRequest,
        ranges: List<RecoveryStreamingRangeRow>,
    ) {
        val end = request.requestedCiphertextEndExclusive
        if (end == null) {
            if (ranges.isNotEmpty()) deny(RecoveryStreamingSourceFailure.ACTIVE_RANGE)
            return
        }
        if (end == request.requestedCiphertextStart) return
        if (
            ranges.any {
                request.requestedCiphertextStart < it.rangeEnd && end > it.rangeStart
            }
        ) {
            deny(RecoveryStreamingSourceFailure.ACTIVE_RANGE)
        }
    }

    private fun requireStrictActiveRanges(
        runId: RunId,
        ranges: List<RecoveryStreamingRangeRow>,
    ) {
        val identities = ranges.map { it.rangeIntentId }
        if (
            identities.distinct().size != identities.size ||
                ranges.any {
                    it.runId != runId ||
                        it.candidateId != RecoveryStreamingPersistenceV07.CANDIDATE_ID ||
                        it.sourceRelativeName != STREAM_SOURCE_RELATIVE_NAME ||
                        it.state != "ACTIVE"
                }
        ) {
            deny(RecoveryStreamingSourceFailure.JOURNAL)
        }
    }

    private fun requireReplayOutcome(
        request: RecoveryStreamReplayRequest,
        outcome: RecoveryStreamingOutcomeRow,
    ) {
        if (
            outcome.outcomeId != request.outcomeId ||
                outcome.runId != request.runId ||
                outcome.candidateId != RecoveryStreamingPersistenceV07.CANDIDATE_ID ||
                outcome.sourceRelativeName != STREAM_SOURCE_RELATIVE_NAME ||
                outcome.state != "SEALED"
        ) {
            deny(RecoveryStreamingSourceFailure.JOURNAL)
        }
    }

    private fun requireReplayRange(
        outcome: RecoveryStreamingOutcomeRow,
        range: RecoveryStreamingRangeRow?,
    ) {
        if (outcome.requiredRangeStart == null) {
            if (range != null) deny(RecoveryStreamingSourceFailure.JOURNAL)
            return
        }
        val required = range ?: deny(RecoveryStreamingSourceFailure.JOURNAL)
        try {
            RecoveryStreamingRowValidation.validateParentChild(outcome, required)
        } catch (_: IllegalArgumentException) {
            deny(RecoveryStreamingSourceFailure.JOURNAL)
        }
    }

    private fun requireSafeSourcePath(runId: RunId, sourceRelativeName: String): String {
        requireExactSourceName(sourceRelativeName)
        val rootPath = noBackupRoot.toPath().toAbsolutePath()
        if (!rootPath.isAbsolute || rootPath != rootPath.normalize()) {
            deny(RecoveryStreamingSourceFailure.UNSAFE_PATH)
        }
        val base = rootPath.resolve("poc-recovery")
        val v1 = base.resolve("v1")
        val runs = v1.resolve("runs")
        val run = runs.resolve(runId.toCanonicalString())
        val stream = run.resolve("stream")
        val source = stream.resolve("stream.ct").normalize()
        if (!source.startsWith(run) || source != stream.resolve("stream.ct")) {
            deny(RecoveryStreamingSourceFailure.UNSAFE_PATH)
        }
        // This is the accepted cooperating-app PoC proof: ancestors are lstat-checked, while
        // O_NOFOLLOW protects only the leaf. It does not claim fd-anchored or cross-process safety.
        listOf(rootPath, base, v1, runs, run, stream).forEach { path ->
            if (os.lstat(path.toString())?.type != RecoveryStreamingPathType.DIRECTORY) {
                deny(RecoveryStreamingSourceFailure.UNSAFE_PATH)
            }
        }
        if (os.lstat(source.toString())?.type != RecoveryStreamingPathType.REGULAR) {
            deny(RecoveryStreamingSourceFailure.UNSAFE_PATH)
        }
        return source.toString()
    }

    private fun requireExactSourceName(sourceRelativeName: String) {
        if (
            sourceRelativeName != STREAM_SOURCE_RELATIVE_NAME ||
                sourceRelativeName.startsWith('/') ||
                '\\' in sourceRelativeName ||
                '\u0000' in sourceRelativeName ||
                sourceRelativeName.split('/').any { it.isEmpty() || it == "." || it == ".." }
        ) {
            deny(RecoveryStreamingSourceFailure.UNSAFE_PATH)
        }
    }

    private fun requireNormalExtent(request: RecoveryStreamOpenRequest, extent: ULong) {
        requireAbsoluteWitnessBounds(request.acceptedEnd, request.preFaultSourceBytes)
        if (
            extent > MAX_OBSERVED_SOURCE ||
                (extent >= request.preFaultSourceBytes &&
                    extent - request.preFaultSourceBytes > MAX_SOURCE_APPEND)
        ) {
            deny(RecoveryStreamingSourceFailure.SOURCE_STRUCTURAL)
        }
    }

    private fun requireReplayExtent(outcome: RecoveryStreamingOutcomeRow, extent: ULong) {
        requireAbsoluteWitnessBounds(outcome.acceptedEnd, outcome.preFaultSourceBytes)
        if (extent > MAX_OBSERVED_SOURCE) {
            deny(RecoveryStreamingSourceFailure.SOURCE_STRUCTURAL)
        }
    }

    private inline fun <T> withOpened(
        sourcePath: String,
        block: (OwnedRecoveryStreamDescriptor) -> T,
    ): T {
        val rawDescriptor = os.open(sourcePath, STREAM_OPEN_FLAGS)
        var ownedDescriptor: OwnedRecoveryStreamDescriptor? = null
        val outcome = runCatching {
            val stat = os.fstat(rawDescriptor)
            if (stat.type != RecoveryStreamingPathType.REGULAR || stat.size < 0L) {
                deny(RecoveryStreamingSourceFailure.SOURCE_STRUCTURAL)
            }
            val descriptor = OwnedRecoveryStreamDescriptor(os, rawDescriptor, stat.size.toULong())
            ownedDescriptor = descriptor
            block(descriptor)
        }
        return outcome.fold(
            onSuccess = { value ->
                requireNotNull(ownedDescriptor).close()
                value
            },
            onFailure = { primary ->
                try {
                    ownedDescriptor?.close() ?: os.close(rawDescriptor)
                } catch (closeFailure: Throwable) {
                    primary.addSuppressed(closeFailure)
                }
                throw primary
            },
        )
    }

    private fun requireAbsoluteWitnessBounds(acceptedEnd: ULong, preFaultSourceBytes: ULong) {
        if (acceptedEnd > MAX_ACCEPTED_END || preFaultSourceBytes > MAX_PRE_FAULT_SOURCE) {
            deny(RecoveryStreamingSourceFailure.SOURCE_STRUCTURAL)
        }
    }

    private fun closeScoped(scoped: ScopedOpenedSource, primary: Throwable?) {
        try {
            scoped.invalidateAndClose()
        } catch (closeFailure: Throwable) {
            primary?.addSuppressed(closeFailure) ?: throw closeFailure
        }
    }

    private fun <T> readJournal(result: RecoveryStreamingJournalReadResult<T>): T =
        when (result) {
            is RecoveryStreamingJournalReadResult.Value -> result.value
            is RecoveryStreamingJournalReadResult.Fatal ->
                throw RecoveryStreamingSourceException(
                    RecoveryStreamingSourceFailure.JOURNAL,
                    result.classification,
                )
            is RecoveryStreamingJournalReadResult.Retry ->
                throw RecoveryStreamingSourceException(
                    RecoveryStreamingSourceFailure.JOURNAL,
                    result.classification,
                )
        }

    private companion object {
        const val MAX_ACCEPTED_END = 115_200_000UL
        const val MAX_PRE_FAULT_SOURCE = 115_654_656UL
        const val MAX_OBSERVED_SOURCE = 115_662_848UL
        const val MAX_SOURCE_APPEND = 8_192UL
    }
}

private class OwnedRecoveryStreamDescriptor(
    private val os: RecoveryStreamingOs,
    private val rawDescriptor: RecoveryStreamingRawDescriptor,
    override val frozenExtent: ULong,
) : RecoveryStreamDescriptor {
    private var active = true

    override fun readAt(
        sourceOffset: ULong,
        destination: ByteArray,
        destinationOffset: Int,
        count: Int,
    ): Int {
        check(active) { "Recovery stream descriptor is closed" }
        requireDestination(destination, destinationOffset, count)
        if (
            sourceOffset > frozenExtent ||
                count.toULong() > frozenExtent - sourceOffset ||
                sourceOffset > Long.MAX_VALUE.toULong()
        ) {
            deny(RecoveryStreamingSourceFailure.INVALID_REQUEST)
        }
        val progress =
            os.pread(
                rawDescriptor,
                destination,
                destinationOffset,
                count,
                sourceOffset.toLong(),
            )
        if (progress < 0 || progress > count || (progress == 0 && sourceOffset < frozenExtent)) {
            deny(RecoveryStreamingSourceFailure.SOURCE_STRUCTURAL)
        }
        return progress
    }

    fun probeFrozenEnd() {
        check(active) { "Recovery stream descriptor is closed" }
        val progress = os.pread(rawDescriptor, ByteArray(1), 0, 1, frozenExtent.toLong())
        if (progress != 0) {
            deny(
                if (progress > 0) RecoveryStreamingSourceFailure.SOURCE_CHANGED
                else RecoveryStreamingSourceFailure.SOURCE_STRUCTURAL
            )
        }
    }

    override fun close() {
        if (!active) return
        active = false
        os.close(rawDescriptor)
    }

    private fun requireDestination(destination: ByteArray, offset: Int, count: Int) {
        if (offset < 0 || count < 0 || offset > destination.size - count) {
            deny(RecoveryStreamingSourceFailure.INVALID_REQUEST)
        }
    }
}

private class ScopedOpenedSource(
    private val descriptor: OwnedRecoveryStreamDescriptor,
    private val publicStart: ULong,
    private val publicEnd: ULong,
) : RecoveryOpenedStreamingSource {
    private val scopeLock = Any()
    private var active = true

    override val observedBytes: ULong
        get() =
            synchronized(scopeLock) {
                requireActiveLocked()
                descriptor.frozenExtent
            }

    override fun sha256Prefix(endExclusive: ULong): Sha256Value = sha256Range(0UL, endExclusive)

    override fun sha256Range(startInclusive: ULong, endExclusive: ULong): Sha256Value =
        synchronized(scopeLock) {
            requireActiveLocked()
            val extent = descriptor.frozenExtent
            if (endExclusive < startInclusive || endExclusive > extent) {
                deny(RecoveryStreamingSourceFailure.INVALID_REQUEST)
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(HASH_CHUNK_BYTES)
            var offset = startInclusive
            while (offset < endExclusive) {
                val remaining = endExclusive - offset
                val count = minOf(remaining, HASH_CHUNK_BYTES.toULong()).toInt()
                val progress = boundedReadLocked(offset, buffer, 0, count, endExclusive)
                if (progress <= 0) deny(RecoveryStreamingSourceFailure.SOURCE_STRUCTURAL)
                digest.update(buffer, 0, progress)
                offset += progress.toULong()
            }
            if (endExclusive == extent) descriptor.probeFrozenEnd()
            Sha256Value.fromBytes(digest.digest())
        }

    override fun boundedInputStream(): InputStream {
        return synchronized(scopeLock) {
            requireActiveLocked()
            BoundedSourceInputStream(this, publicStart, publicEnd)
        }
    }

    fun invalidateAndClose() {
        synchronized(scopeLock) {
            active = false
            descriptor.close()
        }
    }

    fun probeFrozenEnd() {
        synchronized(scopeLock) {
            requireActiveLocked()
            descriptor.probeFrozenEnd()
        }
    }

    fun streamRead(
        sourceOffset: ULong,
        destination: ByteArray,
        destinationOffset: Int,
        count: Int,
        ceiling: ULong,
    ): Int {
        return synchronized(scopeLock) {
            requireActiveLocked()
            boundedReadLocked(sourceOffset, destination, destinationOffset, count, ceiling)
        }
    }

    fun requireActive() {
        synchronized(scopeLock) { requireActiveLocked() }
    }

    private fun boundedReadLocked(
        sourceOffset: ULong,
        destination: ByteArray,
        destinationOffset: Int,
        count: Int,
        ceiling: ULong,
    ): Int {
        requireDestination(destination, destinationOffset, count)
        if (
            count < 0 ||
                ceiling > descriptor.frozenExtent ||
                sourceOffset > ceiling ||
                count.toULong() > ceiling - sourceOffset
        ) {
            deny(RecoveryStreamingSourceFailure.INVALID_REQUEST)
        }
        return descriptor.readAt(sourceOffset, destination, destinationOffset, count)
    }

    private fun requireActiveLocked() {
        check(active) { "Recovery streaming source scope is closed" }
    }

    private fun requireDestination(destination: ByteArray, offset: Int, count: Int) {
        if (offset < 0 || count < 0 || offset > destination.size - count) {
            deny(RecoveryStreamingSourceFailure.INVALID_REQUEST)
        }
    }

    private companion object {
        const val HASH_CHUNK_BYTES = 8_192
    }
}

private class BoundedSourceInputStream(
    private val source: ScopedOpenedSource,
    start: ULong,
    private val ceiling: ULong,
) : InputStream() {
    private var position = start
    private var closed = false
    private val oneByte = ByteArray(1)

    override fun read(): Int {
        val progress = read(oneByte, 0, 1)
        return if (progress == -1) -1 else oneByte[0].toInt() and 0xff
    }

    override fun read(destination: ByteArray, offset: Int, length: Int): Int {
        requireUsable()
        if (offset < 0 || length < 0 || offset > destination.size - length) {
            throw IndexOutOfBoundsException("Invalid bounded stream destination")
        }
        if (length == 0) return 0
        if (position == ceiling) return -1
        val count = minOf(length.toULong(), ceiling - position).toInt()
        val progress = source.streamRead(position, destination, offset, count, ceiling)
        if (progress == 0) deny(RecoveryStreamingSourceFailure.SOURCE_STRUCTURAL)
        position += progress.toULong()
        return progress
    }

    override fun close() {
        closed = true
    }

    private fun requireUsable() {
        check(!closed) { "Recovery bounded stream is closed" }
        source.requireActive()
    }
}

private class AndroidRecoveryStreamingDescriptor(val value: FileDescriptor) :
    RecoveryStreamingRawDescriptor

internal object AndroidRecoveryStreamingOs : RecoveryStreamingOs {
    override fun lstat(path: String): RecoveryStreamingStat? =
        try {
            Os.lstat(path).toStreamingStat()
        } catch (failure: ErrnoException) {
            if (failure.errno == OsConstants.ENOENT) null else throw failure
        }

    override fun open(path: String, flags: Int): RecoveryStreamingRawDescriptor =
        AndroidRecoveryStreamingDescriptor(Os.open(path, flags, 0))

    override fun fstat(descriptor: RecoveryStreamingRawDescriptor): RecoveryStreamingStat =
        Os.fstat(descriptor.android()).toStreamingStat()

    override fun pread(
        descriptor: RecoveryStreamingRawDescriptor,
        destination: ByteArray,
        destinationOffset: Int,
        count: Int,
        sourceOffset: Long,
    ): Int = Os.pread(descriptor.android(), destination, destinationOffset, count, sourceOffset)

    override fun close(descriptor: RecoveryStreamingRawDescriptor) = Os.close(descriptor.android())

    private fun RecoveryStreamingRawDescriptor.android(): FileDescriptor =
        (this as? AndroidRecoveryStreamingDescriptor)?.value
            ?: throw IllegalArgumentException("Foreign Recovery streaming descriptor")

    private fun android.system.StructStat.toStreamingStat() =
        RecoveryStreamingStat(
            when {
                OsConstants.S_ISREG(st_mode) -> RecoveryStreamingPathType.REGULAR
                OsConstants.S_ISDIR(st_mode) -> RecoveryStreamingPathType.DIRECTORY
                OsConstants.S_ISLNK(st_mode) -> RecoveryStreamingPathType.SYMLINK
                else -> RecoveryStreamingPathType.OTHER
            },
            st_size,
        )
}

private fun deny(failure: RecoveryStreamingSourceFailure): Nothing =
    throw RecoveryStreamingSourceException(failure)
