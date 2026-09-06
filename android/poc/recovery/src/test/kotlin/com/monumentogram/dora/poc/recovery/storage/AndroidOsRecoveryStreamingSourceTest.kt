@file:Suppress("LargeClass", "LongMethod", "MagicNumber", "TooManyFunctions")

package com.monumentogram.dora.poc.recovery.storage

import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointIdentityInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingCheckpointRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingIdentity
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournal
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalClassification
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalReadResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingJournalResult
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeAttempt
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeIdentityInput
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingOutcomeRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingRangeRow
import com.monumentogram.dora.poc.recovery.contract.RecoveryStreamingWitnessInput
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.coordination.ProcessRecoveryRunSingleWriterGuard
import com.monumentogram.dora.poc.recovery.coordination.RecoveryRunWriterLease
import java.io.File
import java.util.concurrent.Executors
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidOsRecoveryStreamingSourceTest {
    @Test
    fun `normal source validates lease journal path and opens one descriptor with exact flags`() {
        val events = mutableListOf<String>()
        val journal = FakeJournal(events)
        val os = FakeOs(events).apply { seed(byteArrayOf(1, 2, 3, 4)) }
        val source = AndroidOsRecoveryStreamingSource(ROOT, journal, os)

        val bytes =
            source.withSource(normalAccess(), request(start = 1UL, end = 4UL)) { opened ->
                assertEquals(4UL, opened.observedBytes)
                opened.boundedInputStream().readBytes()
            }

        assertArrayEquals(byteArrayOf(2, 3, 4), bytes)
        assertEquals(listOf("checkpoint", "ranges", "open"), events.take(3))
        assertEquals(1, os.opens.size)
        assertEquals(STREAM_OPEN_FLAGS, os.opens.single().flags)
        assertEquals(sourcePath(), os.opens.single().path)
        assertEquals(1, os.closeCalls)
        assertTrue(os.reads.all { it.descriptor === os.opens.single().descriptor })
    }

    @Test
    fun `cross-run access and unsafe source names deny before journal or artifact syscalls`() {
        val unsafe =
            listOf(
                "",
                "/stream/stream.ct",
                "stream//stream.ct",
                "stream/../stream.ct",
                "stream\\stream.ct",
                "stream/stream.ct\u0000",
                "stream/./stream.ct",
                "../stream/stream.ct",
            )
        unsafe.forEach { name ->
            val events = mutableListOf<String>()
            val os = FakeOs(events).apply { seed(byteArrayOf(1)) }
            val failure =
                assertThrows(RecoveryStreamingSourceException::class.java) {
                    AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(events), os).withSource(
                        normalAccess(),
                        request(source = name, start = 0UL, end = 0UL),
                    ) {}
                }
            assertEquals(RecoveryStreamingSourceFailure.UNSAFE_PATH, failure.failure)
            assertTrue(events.isEmpty())
            assertTrue(os.lstats.isEmpty())
        }

        val events = mutableListOf<String>()
        val failure =
            assertThrows(RecoveryStreamingSourceException::class.java) {
                AndroidOsRecoveryStreamingSource(
                        ROOT,
                        FakeJournal(events),
                        FakeOs(events).apply { seed(byteArrayOf(1)) },
                    )
                    .withSource(normalAccess(OTHER_RUN), request(start = 0UL, end = 0UL)) {}
            }
        assertEquals(RecoveryStreamingSourceFailure.LEASE_BINDING, failure.failure)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `every ancestor and the leaf are lstat checked before open`() {
        directoryPaths().forEach { unsafe ->
            listOf(
                    null,
                    RecoveryStreamingPathType.SYMLINK,
                    RecoveryStreamingPathType.REGULAR,
                    RecoveryStreamingPathType.OTHER,
                )
                .forEach { type ->
                    val events = mutableListOf<String>()
                    val os = FakeOs(events).apply { seed(byteArrayOf(1)) }
                    if (type == null) os.stats.remove(unsafe)
                    else os.stats[unsafe] = RecoveryStreamingStat(type, 0L)
                    val failure =
                        assertThrows(RecoveryStreamingSourceException::class.java) {
                            AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(events), os)
                                .withSource(normalAccess(), request(start = 0UL, end = 1UL)) {}
                        }
                    assertEquals(RecoveryStreamingSourceFailure.UNSAFE_PATH, failure.failure)
                    assertTrue(os.opens.isEmpty())
                }
        }
        listOf(
                null,
                RecoveryStreamingPathType.SYMLINK,
                RecoveryStreamingPathType.DIRECTORY,
                RecoveryStreamingPathType.OTHER,
            )
            .forEach { type ->
                val events = mutableListOf<String>()
                val os = FakeOs(events).apply { seed(byteArrayOf(1)) }
                if (type == null) os.stats.remove(sourcePath())
                else os.stats[sourcePath()] = RecoveryStreamingStat(type, 1L)
                assertThrows(RecoveryStreamingSourceException::class.java) {
                    AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(events), os).withSource(
                        normalAccess(),
                        request(start = 0UL, end = 1UL),
                    ) {}
                }
                assertTrue(os.opens.isEmpty())
            }

        val normalizedEvents = mutableListOf<String>()
        val normalizedOs = FakeOs(normalizedEvents).apply { seed(byteArrayOf(1)) }
        assertThrows(RecoveryStreamingSourceException::class.java) {
            AndroidOsRecoveryStreamingSource(
                    File(ROOT, "nested/.."),
                    FakeJournal(normalizedEvents),
                    normalizedOs,
                )
                .withSource(normalAccess(), request(start = 0UL, end = 1UL)) {}
        }
        assertTrue(normalizedOs.lstats.isEmpty())
        assertTrue(normalizedOs.opens.isEmpty())
    }

    @Test
    fun `active range gate permits only nonintersecting and empty bounded intervals`() {
        val range = replayAttempt().range!!
        fun invoke(start: ULong, end: ULong?): Pair<FakeOs, Throwable?> {
            val events = mutableListOf<String>()
            val journal = FakeJournal(events).apply { active = listOf(range) }
            val os = FakeOs(events).apply { seed(ByteArray(8_193)) }
            val failure =
                runCatching {
                        AndroidOsRecoveryStreamingSource(ROOT, journal, os).withSource(
                            normalAccess(),
                            request(start = start, end = end, preFault = 8_192UL),
                        ) {}
                    }
                    .exceptionOrNull()
            assertEquals(listOf("checkpoint", "ranges"), events.take(2))
            return os to failure
        }

        assertNull(invoke(0UL, range.rangeStart).second)
        assertNull(invoke(range.rangeStart, range.rangeStart).second)
        listOf(
                invoke(range.rangeStart, range.rangeEnd),
                invoke(range.rangeStart - 1UL, range.rangeStart + 1UL),
                invoke(0UL, null),
            )
            .forEach { (os, failure) ->
                assertEquals(
                    RecoveryStreamingSourceFailure.ACTIVE_RANGE,
                    (failure as RecoveryStreamingSourceException).failure,
                )
                assertTrue(os.opens.isEmpty())
            }
    }

    @Test
    fun `duplicate active range snapshot is structural journal denial before open`() {
        val range = replayAttempt().range!!
        val events = mutableListOf<String>()
        val journal = FakeJournal(events).apply { active = listOf(range, range) }
        val os = FakeOs(events).apply { seed(ByteArray(8_193)) }

        val failure =
            assertThrows(RecoveryStreamingSourceException::class.java) {
                AndroidOsRecoveryStreamingSource(ROOT, journal, os).withSource(
                    normalAccess(),
                    request(start = 0UL, end = range.rangeStart, preFault = 8_192UL),
                ) {}
            }

        assertEquals(RecoveryStreamingSourceFailure.JOURNAL, failure.failure)
        assertTrue(os.opens.isEmpty())
    }

    @Test
    fun `invalid interval and strict journal failures deny before open`() {
        val events = mutableListOf<String>()
        val os = FakeOs(events).apply { seed(byteArrayOf(1)) }
        val source = AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(events), os)
        assertEquals(
            RecoveryStreamingSourceFailure.INVALID_REQUEST,
            assertThrows(RecoveryStreamingSourceException::class.java) {
                    source.withSource(normalAccess(), request(start = 1UL, end = 0UL)) {}
                }
                .failure,
        )
        assertTrue(events.isEmpty())

        listOf(
                RecoveryStreamingJournalReadResult.Fatal(
                    RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
                    emptyList(),
                ),
                RecoveryStreamingJournalReadResult.Retry(
                    RecoveryStreamingJournalClassification.JOURNAL_OPERATIONAL
                ),
            )
            .forEach { result ->
                val failingEvents = mutableListOf<String>()
                val failingOs = FakeOs(failingEvents).apply { seed(byteArrayOf(1)) }
                val journal = FakeJournal(failingEvents).apply { checkpointResult = result }
                val failure =
                    assertThrows(RecoveryStreamingSourceException::class.java) {
                        AndroidOsRecoveryStreamingSource(ROOT, journal, failingOs).withSource(
                            normalAccess(),
                            request(start = 0UL, end = 1UL),
                        ) {}
                    }
                assertEquals(RecoveryStreamingSourceFailure.JOURNAL, failure.failure)
                assertTrue(failingOs.opens.isEmpty())
            }

        val mismatchEvents = mutableListOf<String>()
        val mismatchOs = FakeOs(mismatchEvents).apply { seed(byteArrayOf(1)) }
        val mismatch = FakeJournal(mismatchEvents).apply { checkpoints = emptyList() }
        assertThrows(RecoveryStreamingSourceException::class.java) {
            AndroidOsRecoveryStreamingSource(ROOT, mismatch, mismatchOs).withSource(
                normalAccess(),
                request(start = 0UL, end = 1UL),
            ) {}
        }
        assertTrue(mismatchOs.opens.isEmpty())
    }

    @Test
    fun `fstat freezes extent and enforces exact unsigned bounds`() {
        val accepted =
            FakeOs().apply {
                seed(ByteArray(0))
                stats[sourcePath()] =
                    RecoveryStreamingStat(RecoveryStreamingPathType.REGULAR, 115_662_848L)
            }
        AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), accepted).withSource(
            normalAccess(),
            request(
                accepted = 115_200_000UL,
                preFault = 115_654_656UL,
                start = 0UL,
                end = 0UL,
            ),
        ) {
            assertEquals(115_662_848UL, it.observedBytes)
        }
        assertEquals(1, accepted.closeCalls)

        val invalid =
            listOf(
                ExtentCase(-1L, 0UL, 0UL, 1),
                ExtentCase(1L, 0UL, 115_200_001UL, 0),
                ExtentCase(115_654_657L, 115_654_657UL, 0UL, 0),
                ExtentCase(115_662_849L, 115_654_656UL, 0UL, 1),
                ExtentCase(7L, 8UL, 0UL, 1),
                ExtentCase(8_193L, 0UL, 0UL, 1),
            )
        invalid.forEach { case ->
            val os =
                FakeOs().apply {
                    seed(ByteArray(0))
                    stats[sourcePath()] =
                        RecoveryStreamingStat(RecoveryStreamingPathType.REGULAR, case.extent)
                }
            assertThrows(RecoveryStreamingSourceException::class.java) {
                AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), os).withSource(
                    normalAccess(),
                    request(
                        accepted = case.acceptedEnd,
                        preFault = case.preFault,
                        start = 0UL,
                        end = 0UL,
                    ),
                ) {}
            }
            assertEquals(case.closeCalls, os.closeCalls)
        }
    }

    @Test
    fun `fstat type failure bounded end overflow and fstat exception close once`() {
        listOf(RecoveryStreamingPathType.DIRECTORY, RecoveryStreamingPathType.SYMLINK).forEach {
            type ->
            val os =
                FakeOs().apply {
                    seed(byteArrayOf(1))
                    fstatOverride = RecoveryStreamingStat(type, 1L)
                }
            assertThrows(RecoveryStreamingSourceException::class.java) {
                AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), os).withSource(
                    normalAccess(),
                    request(start = 0UL, end = 1UL, preFault = 1UL),
                ) {}
            }
            assertEquals(1, os.closeCalls)
        }

        val beyond = FakeOs().apply { seed(byteArrayOf(1)) }
        assertThrows(RecoveryStreamingSourceException::class.java) {
            AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), beyond).withSource(
                normalAccess(),
                request(start = 0UL, end = 2UL, preFault = 1UL),
            ) {}
        }
        assertEquals(1, beyond.closeCalls)

        val fstat =
            FakeOs().apply {
                seed(byteArrayOf(1))
                failFstat = true
            }
        assertEquals(
            "fstat",
            assertThrows(IllegalStateException::class.java) {
                    AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), fstat).withSource(
                        normalAccess(),
                        request(start = 0UL, end = 1UL, preFault = 1UL),
                    ) {}
                }
                .message,
        )
        assertEquals(1, fstat.closeCalls)
    }

    @Test
    fun `proof hashes and public reads are positional short-read safe on one descriptor`() {
        val value = ByteArray(32) { it.toByte() }
        val os =
            FakeOs().apply {
                seed(value)
                maximumRead = 2
            }
        var leaked: RecoveryOpenedStreamingSource? = null
        var stream: java.io.InputStream? = null
        AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), os).withSource(
            normalAccess(),
            request(start = 5UL, end = 11UL, preFault = 32UL),
        ) {
            assertEquals(
                Sha256Value.calculate(value.copyOfRange(3, 13)),
                it.sha256Range(3UL, 13UL),
            )
            assertEquals(Sha256Value.calculate(value.copyOfRange(0, 7)), it.sha256Prefix(7UL))
            assertEquals(
                Sha256Value.calculate(value.copyOfRange(0, 13)),
                it.sha256Prefix(13UL),
            )
            stream = it.boundedInputStream()
            assertArrayEquals(value.copyOfRange(5, 11), stream!!.readBytes())
            leaked = it
        }
        assertTrue(os.reads.map { it.offset }.containsAll(listOf(0L, 3L, 5L, 32L)))
        assertTrue(os.reads.all { it.descriptor === os.opens.single().descriptor })
        assertThrows(IllegalStateException::class.java) { leaked!!.sha256Prefix(1UL) }
        assertThrows(IllegalStateException::class.java) { stream!!.read() }
        assertFalse(
            RecoveryOpenedStreamingSource::class.java.methods.any {
                it.name.contains("probe", true)
            }
        )
    }

    @Test
    fun `bounded stream close is non-owning and zero or negative raw progress is rejected`() {
        val os = FakeOs().apply { seed(byteArrayOf(1, 2, 3)) }
        AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), os).withSource(
            normalAccess(),
            request(start = 0UL, end = 3UL, preFault = 3UL),
        ) { opened ->
            val first = opened.boundedInputStream()
            val second = opened.boundedInputStream()
            first.close()
            assertThrows(IllegalStateException::class.java) { first.read() }
            assertEquals(1, second.read())
            assertEquals(0, os.closeCalls)
        }
        assertEquals(1, os.closeCalls)

        listOf(0, -1).forEach { invalidProgress ->
            val failing =
                FakeOs().apply {
                    seed(byteArrayOf(1, 2, 3))
                    forcedProgress[0L] = invalidProgress
                }
            assertThrows(RecoveryStreamingSourceException::class.java) {
                AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), failing).withSource(
                    normalAccess(),
                    request(start = 0UL, end = 3UL, preFault = 3UL),
                ) {
                    it.boundedInputStream().read()
                }
            }
            assertEquals(1, failing.closeCalls)
        }

        val endpoint = FakeOs().apply { seed(byteArrayOf(1)) }
        AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), endpoint).withSource(
            normalAccess(),
            request(start = 1UL, end = 1UL, preFault = 1UL),
        ) {
            assertEquals(-1, it.boundedInputStream().read())
        }
        assertEquals(listOf(1L), endpoint.reads.map { it.offset })
    }

    @Test
    fun `zero mid-hash and impossible positive progress are structural and close once`() {
        listOf(0, 2).forEach { progress ->
            val os =
                FakeOs().apply {
                    seed(byteArrayOf(1))
                    forcedProgress[0L] = progress
                }
            assertThrows(RecoveryStreamingSourceException::class.java) {
                AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), os).withSource(
                    normalAccess(),
                    request(start = 0UL, end = 1UL, preFault = 1UL),
                ) {
                    it.sha256Prefix(1UL)
                }
            }
            assertEquals(1, os.closeCalls)
        }
    }

    @Test
    fun `private end probe rejects growth and closes once`() {
        val os =
            FakeOs().apply {
                seed(byteArrayOf(1, 2, 3, 4))
                stats[sourcePath()] = RecoveryStreamingStat(RecoveryStreamingPathType.REGULAR, 3L)
            }
        assertEquals(
            RecoveryStreamingSourceFailure.SOURCE_CHANGED,
            assertThrows(RecoveryStreamingSourceException::class.java) {
                    AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), os).withSource(
                        normalAccess(),
                        request(start = 0UL, end = 3UL, preFault = 3UL),
                    ) {}
                }
                .failure,
        )
        assertEquals(1, os.closeCalls)
        assertEquals(3L, os.reads.last().offset)
        assertEquals(1, os.reads.last().count)
    }

    @Test
    fun `open and close failures preserve exact ownership and suppression`() {
        val open =
            FakeOs().apply {
                seed(byteArrayOf(1))
                failOpen = true
            }
        assertThrows(IllegalStateException::class.java) {
            AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), open).withSource(
                normalAccess(),
                request(start = 0UL, end = 1UL, preFault = 1UL),
            ) {}
        }
        assertEquals(0, open.closeCalls)

        val closeOnly =
            FakeOs().apply {
                seed(byteArrayOf(1))
                failClose = true
            }
        assertEquals(
            "close",
            assertThrows(IllegalStateException::class.java) {
                    AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), closeOnly).withSource(
                        normalAccess(),
                        request(start = 0UL, end = 1UL, preFault = 1UL),
                    ) {}
                }
                .message,
        )
        assertEquals(1, closeOnly.closeCalls)

        val both =
            FakeOs().apply {
                seed(byteArrayOf(1))
                failClose = true
            }
        val primary =
            assertThrows(IllegalArgumentException::class.java) {
                AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), both).withSource(
                    normalAccess(),
                    request(start = 0UL, end = 1UL, preFault = 1UL),
                ) {
                    throw IllegalArgumentException("block")
                }
            }
        assertEquals("block", primary.message)
        assertEquals("close", primary.suppressed.single().message)
        assertEquals(1, both.closeCalls)
    }

    @Test
    fun `caller retains same-run lease through gateway close and later commit boundary`() {
        val lease = requireNotNull(ProcessRecoveryRunSingleWriterGuard.tryAcquire(RUN))
        val access = RecoveryStreamingSourceControllerAccess.normal(RUN, lease)
        val executor = Executors.newSingleThreadExecutor()
        try {
            AndroidOsRecoveryStreamingSource(
                    ROOT,
                    FakeJournal(),
                    FakeOs().apply { seed(byteArrayOf(1)) },
                )
                .withSource(access, request(start = 0UL, end = 1UL, preFault = 1UL)) {
                    assertNull(
                        executor
                            .submit(
                                java.util.concurrent.Callable {
                                    ProcessRecoveryRunSingleWriterGuard.tryAcquire(RUN)
                                }
                            )
                            .get()
                    )
                    assertTrue(
                        executor
                            .submit(
                                java.util.concurrent.Callable {
                                    ProcessRecoveryRunSingleWriterGuard.tryAcquire(OTHER_RUN)
                                        ?.also {
                                            it.close()
                                        }
                                }
                            )
                            .get() != null
                    )
                }
            assertNull(
                executor
                    .submit(
                        java.util.concurrent.Callable {
                            ProcessRecoveryRunSingleWriterGuard.tryAcquire(RUN)
                        }
                    )
                    .get()
            )
        } finally {
            lease.close()
            executor.shutdownNow()
        }
        val reacquired = requireNotNull(ProcessRecoveryRunSingleWriterGuard.tryAcquire(RUN))
        reacquired.close()
    }

    @Test
    fun `gateway never closes caller lease and access constructors remain private`() {
        var leaseCloses = 0
        val lease = RecoveryRunWriterLease { leaseCloses++ }
        AndroidOsRecoveryStreamingSource(
                ROOT,
                FakeJournal(),
                FakeOs().apply { seed(byteArrayOf(1)) },
            )
            .withSource(
                RecoveryStreamingSourceControllerAccess.normal(RUN, lease),
                request(start = 0UL, end = 1UL, preFault = 1UL),
            ) {}
        assertEquals(0, leaseCloses)
        assertTrue(
            RecoveryStreamingSourceLeaseAccess::class.java.declaredConstructors.all {
                it.isSynthetic || java.lang.reflect.Modifier.isPrivate(it.modifiers)
            }
        )
        assertTrue(
            RecoveryStreamingReplayAccess::class.java.declaredConstructors.all {
                it.isSynthetic || java.lang.reflect.Modifier.isPrivate(it.modifiers)
            }
        )
        lease.close()
        assertEquals(1, leaseCloses)
    }

    @Test
    fun `replay hashes one descriptor and returns stored metadata without public bytes or writes`() {
        val value = ByteArray(8_193) { (it % 251).toByte() }
        val attempt = replayAttempt(value)
        val events = mutableListOf<String>()
        val journal =
            FakeJournal(events).apply {
                checkpoints = listOf(checkpoint())
                outcome = attempt.outcome
                range = attempt.range
                active = listOf(requireNotNull(attempt.range))
            }
        val os =
            FakeOs(events).apply {
                seed(value)
                maximumRead = 503
            }

        val result =
            AndroidOsRecoveryStreamingSource(ROOT, journal, os)
                .verifyReplayHashOnly(
                    RecoveryStreamingSourceControllerAccess.replay(RUN, noOpLease()),
                    RecoveryStreamReplayRequest(RUN, attempt.outcome.outcomeId),
                )

        result as RecoveryReplayHashOnlyResult.ExactStoredSourceMetadata
        assertEquals(attempt.outcome.observedSourceBytes, result.observedBytes)
        assertEquals(attempt.outcome.observedSourceSha256, result.sourceSha256)
        assertEquals(1, os.opens.size)
        assertEquals(1, os.closeCalls)
        assertEquals(value.size.toLong(), os.reads.last().offset)
        assertEquals(0, journal.writeCalls)
        assertTrue(events.indexOf("outcome") < events.indexOf("open"))
    }

    @Test
    fun `replay reports internal identity change and rejects missing or unexpected child`() {
        val value = ByteArray(8_193) { (it % 251).toByte() }
        val attempt = replayAttempt(value)
        val changed = value.copyOf().also { it[0] = (it[0] + 1).toByte() }
        val changedJournal =
            FakeJournal().apply {
                outcome = attempt.outcome
                range = attempt.range
            }
        val changedResult =
            AndroidOsRecoveryStreamingSource(
                    ROOT,
                    changedJournal,
                    FakeOs().apply { seed(changed) },
                )
                .verifyReplayHashOnly(
                    RecoveryStreamingSourceControllerAccess.replay(RUN, noOpLease()),
                    RecoveryStreamReplayRequest(RUN, attempt.outcome.outcomeId),
                )
        assertEquals(RecoveryReplayHashOnlyResult.SourceIdentityChanged, changedResult)

        val shorter = value.copyOf(value.size - 1)
        val shortened =
            AndroidOsRecoveryStreamingSource(
                    ROOT,
                    changedJournal,
                    FakeOs().apply { seed(shorter) },
                )
                .verifyReplayHashOnly(
                    RecoveryStreamingSourceControllerAccess.replay(RUN, noOpLease()),
                    RecoveryStreamReplayRequest(RUN, attempt.outcome.outcomeId),
                )
        assertEquals(RecoveryReplayHashOnlyResult.SourceIdentityChanged, shortened)

        val otherValue = value.copyOf().also { it[1] = (it[1] + 1).toByte() }
        listOf(null, replayAttempt(otherValue).range).forEach { child ->
            val journal =
                FakeJournal().apply {
                    outcome = attempt.outcome
                    range = child
                }
            val os = FakeOs().apply { seed(value) }
            assertThrows(RecoveryStreamingSourceException::class.java) {
                AndroidOsRecoveryStreamingSource(ROOT, journal, os)
                    .verifyReplayHashOnly(
                        RecoveryStreamingSourceControllerAccess.replay(RUN, noOpLease()),
                        RecoveryStreamReplayRequest(RUN, attempt.outcome.outcomeId),
                    )
            }
            assertTrue(os.opens.isEmpty())
        }
    }

    @Test
    fun `replay capability and every strict journal read fail before artifact open`() {
        val value = ByteArray(8_193)
        val attempt = replayAttempt(value)
        val crossEvents = mutableListOf<String>()
        val crossOs = FakeOs(crossEvents).apply { seed(value) }
        assertThrows(RecoveryStreamingSourceException::class.java) {
            AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(crossEvents), crossOs)
                .verifyReplayHashOnly(
                    RecoveryStreamingSourceControllerAccess.replay(OTHER_RUN, noOpLease()),
                    RecoveryStreamReplayRequest(RUN, attempt.outcome.outcomeId),
                )
        }
        assertTrue(crossEvents.isEmpty())

        val fatal =
            RecoveryStreamingJournalReadResult.Fatal(
                RecoveryStreamingJournalClassification.JOURNAL_STRUCTURAL,
                emptyList(),
            )
        listOf("outcome", "checkpoint", "range").forEach { stage ->
            val events = mutableListOf<String>()
            val os = FakeOs(events).apply { seed(value) }
            val journal =
                FakeJournal(events).apply {
                    outcome = attempt.outcome
                    range = attempt.range
                    when (stage) {
                        "outcome" -> outcomeResult = fatal
                        "checkpoint" -> checkpointResult = fatal
                        "range" -> rangeResult = fatal
                    }
                }
            val failure =
                assertThrows(RecoveryStreamingSourceException::class.java) {
                    AndroidOsRecoveryStreamingSource(ROOT, journal, os)
                        .verifyReplayHashOnly(
                            RecoveryStreamingSourceControllerAccess.replay(RUN, noOpLease()),
                            RecoveryStreamReplayRequest(RUN, attempt.outcome.outcomeId),
                        )
                }
            assertEquals(RecoveryStreamingSourceFailure.JOURNAL, failure.failure)
            assertTrue(os.opens.isEmpty())
        }
    }

    @Test
    fun `Android adapter rejects a foreign descriptor before an artifact syscall`() {
        val foreign = FakeDescriptor("foreign")
        assertThrows(IllegalArgumentException::class.java) {
            AndroidRecoveryStreamingOs.fstat(foreign)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AndroidRecoveryStreamingOs.pread(foreign, ByteArray(1), 0, 1, 0L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            AndroidRecoveryStreamingOs.close(foreign)
        }
    }

    private data class OpenCall(
        val path: String,
        val flags: Int,
        val descriptor: FakeDescriptor,
    )

    private data class ReadCall(
        val descriptor: FakeDescriptor,
        val offset: Long,
        val count: Int,
    )

    private data class ExtentCase(
        val extent: Long,
        val preFault: ULong,
        val acceptedEnd: ULong,
        val closeCalls: Int,
    )

    private class FakeDescriptor(val path: String) : RecoveryStreamingRawDescriptor

    private class FakeOs(private val events: MutableList<String> = mutableListOf()) :
        RecoveryStreamingOs {
        val stats = mutableMapOf<String, RecoveryStreamingStat>()
        val bytes = mutableMapOf<String, ByteArray>()
        val lstats = mutableListOf<String>()
        val opens = mutableListOf<OpenCall>()
        val reads = mutableListOf<ReadCall>()
        val forcedProgress = mutableMapOf<Long, Int>()
        var maximumRead = Int.MAX_VALUE
        var closeCalls = 0
        var failOpen = false
        var failFstat = false
        var failClose = false
        var fstatOverride: RecoveryStreamingStat? = null

        fun seed(value: ByteArray) {
            directoryPaths().forEach {
                stats[it] = RecoveryStreamingStat(RecoveryStreamingPathType.DIRECTORY, 0L)
            }
            stats[sourcePath()] =
                RecoveryStreamingStat(RecoveryStreamingPathType.REGULAR, value.size.toLong())
            bytes[sourcePath()] = value
        }

        override fun lstat(path: String): RecoveryStreamingStat? {
            lstats += path
            return stats[path]
        }

        override fun open(path: String, flags: Int): RecoveryStreamingRawDescriptor {
            events += "open"
            if (failOpen) error("open")
            return FakeDescriptor(path).also { opens += OpenCall(path, flags, it) }
        }

        override fun fstat(descriptor: RecoveryStreamingRawDescriptor): RecoveryStreamingStat {
            if (failFstat) error("fstat")
            return fstatOverride ?: requireNotNull(stats[(descriptor as FakeDescriptor).path])
        }

        @Suppress("ReturnCount")
        override fun pread(
            descriptor: RecoveryStreamingRawDescriptor,
            destination: ByteArray,
            destinationOffset: Int,
            count: Int,
            sourceOffset: Long,
        ): Int {
            val fake = descriptor as FakeDescriptor
            reads += ReadCall(fake, sourceOffset, count)
            forcedProgress[sourceOffset]?.let {
                return it
            }
            val value = bytes[fake.path] ?: ByteArray(0)
            if (sourceOffset >= value.size) return 0
            val actual = minOf(count, maximumRead, value.size - sourceOffset.toInt())
            value.copyInto(
                destination,
                destinationOffset,
                sourceOffset.toInt(),
                sourceOffset.toInt() + actual,
            )
            return actual
        }

        override fun close(descriptor: RecoveryStreamingRawDescriptor) {
            closeCalls++
            if (failClose) error("close")
        }
    }

    private class FakeJournal(private val events: MutableList<String> = mutableListOf()) :
        RecoveryStreamingJournal {
        var checkpoints = listOf(checkpoint())
        var active = emptyList<RecoveryStreamingRangeRow>()
        var outcome: RecoveryStreamingOutcomeRow? = null
        var range: RecoveryStreamingRangeRow? = null
        var checkpointResult:
            RecoveryStreamingJournalReadResult<List<RecoveryStreamingCheckpointRow>>? =
            null
        var outcomeResult: RecoveryStreamingJournalReadResult<RecoveryStreamingOutcomeRow?>? = null
        var rangeResult: RecoveryStreamingJournalReadResult<RecoveryStreamingRangeRow?>? = null
        var writeCalls = 0

        override fun checkpointChain(
            runId: RunId
        ): RecoveryStreamingJournalReadResult<List<RecoveryStreamingCheckpointRow>> {
            events += "checkpoint"
            return checkpointResult ?: RecoveryStreamingJournalReadResult.Value(checkpoints)
        }

        override fun outcomeById(
            outcomeId: Sha256Value
        ): RecoveryStreamingJournalReadResult<RecoveryStreamingOutcomeRow?> {
            events += "outcome"
            return outcomeResult ?: RecoveryStreamingJournalReadResult.Value(outcome)
        }

        override fun outcomeByWitness(
            runId: RunId,
            checkpointIdentity: Sha256Value,
            witnessId: Sha256Value,
        ): RecoveryStreamingJournalReadResult<RecoveryStreamingOutcomeRow?> =
            RecoveryStreamingJournalReadResult.Value(null)

        override fun rangeByOutcome(
            outcomeId: Sha256Value
        ): RecoveryStreamingJournalReadResult<RecoveryStreamingRangeRow?> {
            events += "range"
            return rangeResult ?: RecoveryStreamingJournalReadResult.Value(range)
        }

        override fun activeRanges(
            runId: RunId,
            sourceRelativeName: String,
        ): RecoveryStreamingJournalReadResult<List<RecoveryStreamingRangeRow>> {
            events += "ranges"
            return RecoveryStreamingJournalReadResult.Value(active)
        }

        override fun insertCheckpoint(
            row: RecoveryStreamingCheckpointRow
        ): RecoveryStreamingJournalResult {
            writeCalls++
            error("unexpected write")
        }

        override fun persistOutcome(
            attempt: RecoveryStreamingOutcomeAttempt
        ): RecoveryStreamingJournalResult {
            writeCalls++
            error("unexpected write")
        }
    }

    private companion object {
        val ROOT = File("root").absoluteFile
        val RUN = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")
        val OTHER_RUN = RunId.fromCanonicalString("10112233-4455-6677-8899-aabbccddeeff")

        fun noOpLease() = RecoveryRunWriterLease {}

        fun normalAccess(runId: RunId = RUN) =
            RecoveryStreamingSourceControllerAccess.normal(runId, noOpLease())

        fun request(
            source: String = "stream/stream.ct",
            accepted: ULong = 0UL,
            preFault: ULong = 0UL,
            start: ULong = 0UL,
            end: ULong? = null,
        ) =
            RecoveryStreamOpenRequest(
                RUN,
                1UL,
                checkpoint().checkpointIdentity,
                source,
                accepted,
                preFault,
                start,
                end,
            )

        fun directoryPaths(): List<String> {
            val base = File(ROOT, "poc-recovery")
            val v1 = File(base, "v1")
            val runs = File(v1, "runs")
            val run = File(runs, RUN.toCanonicalString())
            return listOf(
                ROOT.path,
                base.path,
                v1.path,
                runs.path,
                run.path,
                File(run, "stream").path,
            )
        }

        fun sourcePath() = File(directoryPaths().last(), "stream.ct").path

        fun checkpoint(): RecoveryStreamingCheckpointRow {
            val input =
                RecoveryStreamingCheckpointIdentityInput(
                    RUN,
                    1UL,
                    2UL,
                    8_192UL,
                    sha("prefix"),
                    4_056UL,
                    "checkpoints/g-00000000000000000001.ct",
                    1UL,
                    sha("checkpoint"),
                    "key-envelopes/checkpoint-g-00000000000000000001.ks",
                    1UL,
                    sha("checkpoint-key"),
                    "stream/stream.ct",
                    "key-envelopes/stream.ks",
                    1UL,
                    sha("stream-key"),
                    Sha256Value.ZERO,
                )
            return RecoveryStreamingCheckpointRow(
                input.runId,
                input.generation,
                input.durableNonFinalSegmentCount,
                input.streamCiphertextPrefixBytes,
                input.streamCiphertextPrefixSha256,
                input.committedEnd,
                input.checkpointRelativeName,
                input.checkpointBytes,
                input.checkpointSha256,
                input.checkpointEnvelopeRelativeName,
                input.checkpointEnvelopeBytes,
                input.checkpointEnvelopeSha256,
                input.streamRelativeName,
                input.streamEnvelopeRelativeName,
                input.streamEnvelopeBytes,
                input.streamEnvelopeSha256,
                input.previousCheckpointSha256,
                RecoveryStreamingIdentity.checkpoint(input),
            )
        }

        fun replayAttempt(value: ByteArray = ByteArray(8_193)): RecoveryStreamingOutcomeAttempt {
            val checkpoint = checkpoint()
            val oracle = sha("oracle")
            val base =
                RecoveryStreamingWitnessInput(
                    RUN,
                    checkpoint.generation,
                    checkpoint.checkpointIdentity,
                    checkpoint.streamCiphertextPrefixBytes,
                    checkpoint.committedEnd,
                    RecoveryStreamingIdentity.oracle(8_137UL, oracle, RUN),
                    8_137UL,
                    oracle,
                    8_192UL,
                    sha("pre-fault"),
                    null,
                )
            val witness =
                base.copy(
                    controllerSnapshotSha256 = RecoveryStreamingIdentity.controllerSnapshot(base)
                )
            val outcome =
                RecoveryStreamingOutcomeRow.from(
                    RecoveryStreamingOutcomeIdentityInput.validAuthenticationFailure(
                        witness,
                        value.size.toULong(),
                        Sha256Value.calculate(value),
                        8_136UL,
                        sha("returned"),
                        8_192UL,
                    )
                )
            return RecoveryStreamingOutcomeAttempt(
                outcome,
                RecoveryStreamingRangeRow.exact(outcome, sha("range")),
                com.monumentogram.dora.poc.recovery.contract.StreamSemanticOutcome.PERSISTED_VALID,
            )
        }

        fun sha(value: String) = Sha256Value.calculate(value.toByteArray())
    }
}
