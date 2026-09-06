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
import com.monumentogram.dora.poc.recovery.coordination.RecoveryRunSingleWriterGuard
import com.monumentogram.dora.poc.recovery.coordination.RecoveryRunWriterLease
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
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
            source.withNormalSource(request(start = 1UL, end = 4UL)) { opened ->
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
                    AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(events), os)
                        .withNormalSource(request(source = name, start = 0UL, end = 0UL)) {}
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
                    .withNormalSource(request(start = 0UL, end = 0UL), OTHER_RUN) {}
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
                                .withNormalSource(request(start = 0UL, end = 1UL)) {}
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
                    AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(events), os)
                        .withNormalSource(request(start = 0UL, end = 1UL)) {}
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
                .withNormalSource(request(start = 0UL, end = 1UL)) {}
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
                        AndroidOsRecoveryStreamingSource(ROOT, journal, os).withNormalSource(
                            request(start = start, end = end, preFault = 8_192UL)
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
                AndroidOsRecoveryStreamingSource(ROOT, journal, os).withNormalSource(
                    request(start = 0UL, end = range.rangeStart, preFault = 8_192UL)
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
                    source.withNormalSource(request(start = 1UL, end = 0UL)) {}
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
                        AndroidOsRecoveryStreamingSource(ROOT, journal, failingOs).withNormalSource(
                            request(start = 0UL, end = 1UL)
                        ) {}
                    }
                assertEquals(RecoveryStreamingSourceFailure.JOURNAL, failure.failure)
                assertTrue(failingOs.opens.isEmpty())
            }

        val mismatchEvents = mutableListOf<String>()
        val mismatchOs = FakeOs(mismatchEvents).apply { seed(byteArrayOf(1)) }
        val mismatch = FakeJournal(mismatchEvents).apply { checkpoints = emptyList() }
        assertThrows(RecoveryStreamingSourceException::class.java) {
            AndroidOsRecoveryStreamingSource(ROOT, mismatch, mismatchOs).withNormalSource(
                request(start = 0UL, end = 1UL)
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
        AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), accepted).withNormalSource(
            request(
                accepted = 115_200_000UL,
                preFault = 115_654_656UL,
                start = 0UL,
                end = 0UL,
            )
        ) {
            assertEquals(115_662_848UL, it.observedBytes)
        }
        assertEquals(1, accepted.closeCalls)

        val invalid =
            listOf(
                ExtentCase(-1L, 0UL, 0UL, 1, RecoveryStreamingSourceFailure.SOURCE_STRUCTURAL),
                ExtentCase(
                    1L,
                    0UL,
                    115_200_001UL,
                    0,
                    RecoveryStreamingSourceFailure.SOURCE_STRUCTURAL,
                ),
                ExtentCase(
                    115_654_657L,
                    115_654_657UL,
                    0UL,
                    0,
                    RecoveryStreamingSourceFailure.SOURCE_STRUCTURAL,
                ),
                ExtentCase(
                    115_662_849L,
                    115_654_656UL,
                    0UL,
                    1,
                    RecoveryStreamingSourceFailure.SOURCE_EXTENT_LIMIT,
                ),
                ExtentCase(8_193L, 0UL, 0UL, 1, RecoveryStreamingSourceFailure.SOURCE_EXTENT_LIMIT),
            )
        invalid.forEach { case ->
            val os =
                FakeOs().apply {
                    seed(ByteArray(0))
                    stats[sourcePath()] =
                        RecoveryStreamingStat(RecoveryStreamingPathType.REGULAR, case.extent)
                }
            val failure =
                assertThrows(RecoveryStreamingSourceException::class.java) {
                    AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), os).withNormalSource(
                        request(
                            accepted = case.acceptedEnd,
                            preFault = case.preFault,
                            start = 0UL,
                            end = 0UL,
                        )
                    ) {}
                }
            assertEquals(case.failure, failure.failure)
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
                AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), os).withNormalSource(
                    request(start = 0UL, end = 1UL, preFault = 1UL)
                ) {}
            }
            assertEquals(1, os.closeCalls)
        }

        val beyond = FakeOs().apply { seed(byteArrayOf(1)) }
        assertThrows(RecoveryStreamingSourceException::class.java) {
            AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), beyond).withNormalSource(
                request(start = 0UL, end = 2UL, preFault = 1UL)
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
                    AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), fstat).withNormalSource(
                        request(start = 0UL, end = 1UL, preFault = 1UL)
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
        AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), os).withNormalSource(
            request(start = 5UL, end = 11UL, preFault = 32UL)
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
        assertThrows(IllegalStateException::class.java) { leaked!!.observedBytes }
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
        AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), os).withNormalSource(
            request(start = 0UL, end = 3UL, preFault = 3UL)
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
                AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), failing).withNormalSource(
                    request(start = 0UL, end = 3UL, preFault = 3UL)
                ) {
                    it.boundedInputStream().read()
                }
            }
            assertEquals(1, failing.closeCalls)
        }

        val endpoint = FakeOs().apply { seed(byteArrayOf(1)) }
        AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), endpoint).withNormalSource(
            request(start = 1UL, end = 1UL, preFault = 1UL)
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
                AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), os).withNormalSource(
                    request(start = 0UL, end = 1UL, preFault = 1UL)
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
                    AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), os).withNormalSource(
                        request(start = 0UL, end = 3UL, preFault = 3UL)
                    ) {}
                }
                .failure,
        )
        assertEquals(1, os.closeCalls)
        assertEquals(3L, os.reads.last().offset)
        assertEquals(1, os.reads.last().count)
    }

    @Test
    fun `truncated frozen extent remains callback visible but denies reads beyond it`() {
        val os = FakeOs().apply { seed(byteArrayOf(1, 2, 3, 4)) }

        AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), os).withNormalSource(
            request(accepted = 4UL, preFault = 8UL, start = 0UL, end = 4UL)
        ) { opened ->
            assertEquals(4UL, opened.observedBytes)
            assertArrayEquals(byteArrayOf(1, 2, 3, 4), opened.boundedInputStream().readBytes())
            assertThrows(RecoveryStreamingSourceException::class.java) {
                opened.sha256Prefix(5UL)
            }
        }
        assertEquals(1, os.closeCalls)
    }

    @Test
    fun `pread and growth probe exceptions or negative progress close with primary suppression`() {
        listOf("hash", "read", "probe").forEach { stage ->
            val os =
                FakeOs().apply {
                    seed(byteArrayOf(1, 2, 3))
                    throwOnPreadOffsets += if (stage == "probe") 3L else 0L
                    failClose = true
                }
            val failure =
                assertThrows(IllegalStateException::class.java) {
                    AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), os).withNormalSource(
                        request(start = 0UL, end = 3UL, preFault = 3UL)
                    ) { opened ->
                        when (stage) {
                            "hash" -> opened.sha256Prefix(3UL)
                            "read" -> opened.boundedInputStream().read()
                            else -> Unit
                        }
                    }
                }
            assertEquals("pread", failure.message)
            assertEquals("close", failure.suppressed.single().message)
            assertEquals(1, os.closeCalls)
        }

        val negativeProbe =
            FakeOs().apply {
                seed(byteArrayOf(1, 2, 3))
                forcedProgress[3L] = -1
            }
        assertEquals(
            RecoveryStreamingSourceFailure.SOURCE_STRUCTURAL,
            assertThrows(RecoveryStreamingSourceException::class.java) {
                    AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), negativeProbe)
                        .withNormalSource(request(start = 0UL, end = 3UL, preFault = 3UL)) {}
                }
                .failure,
        )
        assertEquals(1, negativeProbe.closeCalls)
    }

    @Test
    fun `scope keeps descriptor open for an in-flight read and rejects escaped reads`() {
        val readEntered = CountDownLatch(1)
        val allowRead = CountDownLatch(1)
        val closeEntered = CountDownLatch(1)
        val allowClose = CountDownLatch(1)
        val os =
            FakeOs().apply {
                seed(byteArrayOf(1))
                beforePread = { offset ->
                    if (offset == 0L) {
                        readEntered.countDown()
                        assertTrue(allowRead.await(5, TimeUnit.SECONDS))
                    }
                }
                beforeClose = {
                    closeEntered.countDown()
                    assertTrue(allowClose.await(5, TimeUnit.SECONDS))
                }
            }
        val source = AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), os)
        val executor = Executors.newFixedThreadPool(2)
        try {
            lateinit var escaped: java.io.InputStream
            lateinit var read: java.util.concurrent.Future<Int>
            val sourceFuture =
                executor.submit<java.lang.Void> {
                    source.withNormalSource(request(start = 0UL, end = 1UL, preFault = 1UL)) {
                        opened ->
                        escaped = opened.boundedInputStream()
                        read = executor.submit<Int> { escaped.read() }
                        assertTrue(readEntered.await(5, TimeUnit.SECONDS))
                    }
                    null
                }
            assertTrue(readEntered.await(5, TimeUnit.SECONDS))
            assertEquals(0, os.closeCalls)
            allowRead.countDown()
            assertEquals(1, read.get(5, TimeUnit.SECONDS))
            assertTrue(closeEntered.await(5, TimeUnit.SECONDS))
            assertEquals(0, os.closeCalls)
            allowClose.countDown()
            sourceFuture.get(5, TimeUnit.SECONDS)
            assertEquals(1, os.closeCalls)
            assertThrows(IllegalStateException::class.java) { escaped.read() }
        } finally {
            allowClose.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `controller scope issues only live run-bound tokens and releases after descriptor close`() {
        val events = mutableListOf<String>()
        val guard = RecordingGuard(events)
        val source =
            AndroidOsRecoveryStreamingSource(
                ROOT,
                FakeJournal(),
                FakeOs(events).apply { seed(byteArrayOf(1)) },
            )
        var escaped: RecoveryStreamingSourceLeaseAccess? = null

        RecoveryStreamingSourceControllerAccess.withNormalAccess(RUN, guard) { access ->
            escaped = access
            source.withSource(access, request(start = 0UL, end = 1UL, preFault = 1UL)) {}
            assertEquals(listOf("acquire", "open", "close"), events)
            assertThrows(RecoveryStreamingSourceException::class.java) {
                source.withSource(
                    access,
                    request(start = 0UL, end = 1UL, preFault = 1UL).copy(runId = OTHER_RUN),
                ) {}
            }
        }
        assertEquals(listOf("acquire", "open", "close", "release"), events)
        assertThrows(RecoveryStreamingSourceException::class.java) {
            source.withSource(
                requireNotNull(escaped),
                request(start = 0UL, end = 1UL, preFault = 1UL),
            ) {}
        }
    }

    @Test
    fun `controller scope releases only after descriptor close on exceptional block and close paths`() {
        listOf("block", "close").forEach { failureMode ->
            val events = mutableListOf<String>()
            val os =
                FakeOs(events).apply {
                    seed(byteArrayOf(1))
                    failClose = failureMode == "close"
                }
            assertThrows(IllegalStateException::class.java) {
                RecoveryStreamingSourceControllerAccess.withNormalAccess(
                    RUN,
                    RecordingGuard(events),
                ) { access ->
                    AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), os).withSource(
                        access,
                        request(start = 0UL, end = 1UL, preFault = 1UL),
                    ) {
                        if (failureMode == "block") error("block")
                    }
                }
            }
            assertEquals(listOf("acquire", "open", "close", "release"), events)
        }
    }

    @Test
    fun `controller access shares one same-run lease across normal replay and evidence lifetime`() {
        val events = mutableListOf<String>()
        val guard = RecordingGuard(events)
        lateinit var escapedNormal: RecoveryStreamingSourceLeaseAccess
        lateinit var escapedReplay: RecoveryStreamingReplayAccess

        RecoveryStreamingSourceControllerAccess.withControllerAccess(RUN, guard) { normal, replay ->
            escapedNormal = normal
            escapedReplay = replay
            normal.withBoundTo(RUN) { events += "normal" }
            replay.withBoundTo(RUN) { events += "replay" }
            events += "evidence"
        }

        assertEquals(listOf("acquire", "normal", "replay", "evidence", "release"), events)
        assertThrows(RecoveryStreamingSourceException::class.java) {
            escapedNormal.withBoundTo(RUN) {}
        }
        assertThrows(RecoveryStreamingSourceException::class.java) {
            escapedReplay.withBoundTo(RUN) {}
        }
        assertEquals(1, events.count { it == "acquire" })
        assertEquals(1, events.count { it == "release" })
    }

    @Test
    fun `escaped token operation holds the process lease through scope invalidation`() {
        val enteredJournal = CountDownLatch(1)
        val releaseJournal = CountDownLatch(1)
        val invalidationBlocked = CountDownLatch(1)
        val journal =
            FakeJournal().apply {
                beforeCheckpoint = {
                    enteredJournal.countDown()
                    assertTrue(releaseJournal.await(5, TimeUnit.SECONDS))
                }
            }
        val source =
            AndroidOsRecoveryStreamingSource(
                ROOT,
                journal,
                FakeOs().apply { seed(byteArrayOf(1)) },
            )
        val executor = Executors.newFixedThreadPool(2)
        val competingExecutor = Executors.newSingleThreadExecutor()
        try {
            lateinit var escaped: RecoveryStreamingSourceLeaseAccess
            lateinit var operation: java.util.concurrent.Future<*>
            val scope =
                executor.submit<java.lang.Void> {
                    RecoveryStreamingSourceControllerAccess.withNormalAccessForTest(
                        RUN,
                        ProcessRecoveryRunSingleWriterGuard,
                        invalidationBlocked,
                    ) { access ->
                        escaped = access
                        operation = executor.submit {
                            source.withSource(
                                access,
                                request(start = 0UL, end = 1UL, preFault = 1UL),
                            ) {}
                        }
                        assertTrue(enteredJournal.await(5, TimeUnit.SECONDS))
                    }
                    null
                }
            assertTrue(enteredJournal.await(5, TimeUnit.SECONDS))
            assertTrue(invalidationBlocked.await(5, TimeUnit.SECONDS))
            assertFalse(scope.isDone)
            val competing =
                competingExecutor
                    .submit(
                        java.util.concurrent.Callable {
                            ProcessRecoveryRunSingleWriterGuard.tryAcquire(RUN)
                        }
                    )
                    .get()
            assertNull(competing)
            releaseJournal.countDown()
            operation.get(5, TimeUnit.SECONDS)
            scope.get(5, TimeUnit.SECONDS)
            assertThrows(RecoveryStreamingSourceException::class.java) {
                source.withSource(escaped, request(start = 0UL, end = 1UL, preFault = 1UL)) {}
            }
            requireNotNull(ProcessRecoveryRunSingleWriterGuard.tryAcquire(RUN)).close()
        } finally {
            releaseJournal.countDown()
            executor.shutdownNow()
            competingExecutor.shutdownNow()
        }
    }

    @Test
    fun `exceptional descriptor close retains same-run exclusion until scope release`() {
        val closeEntered = CountDownLatch(1)
        val releaseClose = CountDownLatch(1)
        val os =
            FakeOs().apply {
                seed(byteArrayOf(1))
                failClose = true
                beforeClose = {
                    closeEntered.countDown()
                    assertTrue(releaseClose.await(5, TimeUnit.SECONDS))
                }
            }
        val scopeExecutor = Executors.newSingleThreadExecutor()
        val competingExecutor = Executors.newSingleThreadExecutor()
        try {
            val scope =
                scopeExecutor.submit<java.lang.Void> {
                    RecoveryStreamingSourceControllerAccess.withNormalAccess(
                        RUN,
                        ProcessRecoveryRunSingleWriterGuard,
                    ) { access ->
                        AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), os).withSource(
                            access,
                            request(start = 0UL, end = 1UL, preFault = 1UL),
                        ) {}
                    }
                    null
                }
            assertTrue(closeEntered.await(5, TimeUnit.SECONDS))
            assertThrows(java.util.concurrent.TimeoutException::class.java) {
                scope.get(100, TimeUnit.MILLISECONDS)
            }
            assertNull(
                competingExecutor
                    .submit(
                        java.util.concurrent.Callable {
                            ProcessRecoveryRunSingleWriterGuard.tryAcquire(RUN)
                        }
                    )
                    .get()
            )
            releaseClose.countDown()
            assertThrows(java.util.concurrent.ExecutionException::class.java) {
                scope.get(5, TimeUnit.SECONDS)
            }
            requireNotNull(ProcessRecoveryRunSingleWriterGuard.tryAcquire(RUN)).close()
        } finally {
            releaseClose.countDown()
            scopeExecutor.shutdownNow()
            competingExecutor.shutdownNow()
        }
    }

    @Test
    fun `open and close failures preserve exact ownership and suppression`() {
        val open =
            FakeOs().apply {
                seed(byteArrayOf(1))
                failOpen = true
            }
        assertThrows(IllegalStateException::class.java) {
            AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), open).withNormalSource(
                request(start = 0UL, end = 1UL, preFault = 1UL)
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
                    AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), closeOnly)
                        .withNormalSource(request(start = 0UL, end = 1UL, preFault = 1UL)) {}
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
                AndroidOsRecoveryStreamingSource(ROOT, FakeJournal(), both).withNormalSource(
                    request(start = 0UL, end = 1UL, preFault = 1UL)
                ) {
                    throw IllegalArgumentException("block")
                }
            }
        assertEquals("block", primary.message)
        assertEquals("close", primary.suppressed.single().message)
        assertEquals(1, both.closeCalls)
    }

    @Test
    fun `controller scope retains same-run lease through gateway close and later commit boundary`() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            RecoveryStreamingSourceControllerAccess.withNormalAccess(
                RUN,
                ProcessRecoveryRunSingleWriterGuard,
            ) { access ->
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
                        assertNull(
                            executor
                                .submit(
                                    java.util.concurrent.Callable {
                                        ProcessRecoveryRunSingleWriterGuard.tryAcquire(RUN)
                                    }
                                )
                                .get()
                        )
                    }
            }
        } finally {
            executor.shutdownNow()
        }
        val reacquired = requireNotNull(ProcessRecoveryRunSingleWriterGuard.tryAcquire(RUN))
        reacquired.close()
    }

    @Test
    fun `access constructors remain private and controller closes lease after scope`() {
        val events = mutableListOf<String>()
        RecoveryStreamingSourceControllerAccess.withNormalAccess(RUN, RecordingGuard(events)) {
            access ->
            AndroidOsRecoveryStreamingSource(
                    ROOT,
                    FakeJournal(),
                    FakeOs(events).apply { seed(byteArrayOf(1)) },
                )
                .withSource(access, request(start = 0UL, end = 1UL, preFault = 1UL)) {}
            assertEquals(listOf("acquire", "open", "close"), events)
        }
        assertEquals(listOf("acquire", "open", "close", "release"), events)
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
                .withReplayAccess(RecoveryStreamReplayRequest(RUN, attempt.outcome.outcomeId))

        result as RecoveryReplayHashOnlyResult.ExactStoredSourceMetadata
        assertEquals(attempt.outcome.observedSourceBytes, result.observedBytes)
        assertEquals(attempt.outcome.observedSourceSha256, result.sourceSha256)
        assertEquals(requireNotNull(attempt.range).rangeSha256, result.retainedRangeSha256)
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
                .withReplayAccess(RecoveryStreamReplayRequest(RUN, attempt.outcome.outcomeId))
        assertEquals(RecoveryReplayHashOnlyResult.SourceIdentityChanged, changedResult)

        val shorter = value.copyOf(value.size - 1)
        val shortened =
            AndroidOsRecoveryStreamingSource(
                    ROOT,
                    changedJournal,
                    FakeOs().apply { seed(shorter) },
                )
                .withReplayAccess(RecoveryStreamReplayRequest(RUN, attempt.outcome.outcomeId))
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
                    .withReplayAccess(RecoveryStreamReplayRequest(RUN, attempt.outcome.outcomeId))
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
                .withReplayAccess(
                    RecoveryStreamReplayRequest(RUN, attempt.outcome.outcomeId),
                    OTHER_RUN,
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
                        .withReplayAccess(
                            RecoveryStreamReplayRequest(RUN, attempt.outcome.outcomeId)
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
        val failure: RecoveryStreamingSourceFailure,
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
        val throwOnPreadOffsets = mutableSetOf<Long>()
        var beforePread: ((Long) -> Unit)? = null
        var maximumRead = Int.MAX_VALUE
        var closeCalls = 0
        var failOpen = false
        var failFstat = false
        var failClose = false
        var beforeClose: (() -> Unit)? = null
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
            beforePread?.invoke(sourceOffset)
            if (sourceOffset in throwOnPreadOffsets) error("pread")
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
            beforeClose?.invoke()
            events += "close"
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
        var beforeCheckpoint: (() -> Unit)? = null
        var writeCalls = 0

        override fun checkpointChain(
            runId: RunId
        ): RecoveryStreamingJournalReadResult<List<RecoveryStreamingCheckpointRow>> {
            beforeCheckpoint?.invoke()
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

    private class RecordingGuard(private val events: MutableList<String>) :
        RecoveryRunSingleWriterGuard {
        override fun tryAcquire(runId: RunId): RecoveryRunWriterLease? {
            events += "acquire"
            return RecoveryRunWriterLease { events += "release" }
        }
    }

    private fun <T> AndroidOsRecoveryStreamingSource.withNormalSource(
        request: RecoveryStreamOpenRequest,
        scopeRunId: RunId = request.runId,
        block: (RecoveryOpenedStreamingSource) -> T,
    ): T =
        RecoveryStreamingSourceControllerAccess.withNormalAccess(scopeRunId, noOpGuard) { access ->
            withSource(access, request, block)
        }

    private fun AndroidOsRecoveryStreamingSource.withReplayAccess(
        request: RecoveryStreamReplayRequest,
        scopeRunId: RunId = request.runId,
    ): RecoveryReplayHashOnlyResult =
        RecoveryStreamingSourceControllerAccess.withReplayAccess(scopeRunId, noOpGuard) { access ->
            verifyReplayHashOnly(access, request)
        }

    private companion object {
        val ROOT = File("root").absoluteFile
        val RUN = RunId.fromCanonicalString("00112233-4455-6677-8899-aabbccddeeff")
        val OTHER_RUN = RunId.fromCanonicalString("10112233-4455-6677-8899-aabbccddeeff")

        val noOpGuard = RecoveryRunSingleWriterGuard { RecoveryRunWriterLease {} }

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
                RecoveryStreamingRangeRow.exact(
                    outcome,
                    Sha256Value.calculate(value.copyOfRange(8_192, value.size)),
                ),
                com.monumentogram.dora.poc.recovery.contract.StreamSemanticOutcome.PERSISTED_VALID,
            )
        }

        fun sha(value: String) = Sha256Value.calculate(value.toByteArray())
    }
}
