package com.monumentogram.dora.audio

import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import java.lang.reflect.InvocationTargetException
import java.util.UUID
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exercises the real validator; element reads give a deterministic bound independent of timing. */
class CatalogOrderOptimizationTest {
    private val audio = AudioIdentity(RecordingId(id(1)), AudioAssetId(id(2)), id(3))
    private val digest = Sha256Value.calculate(byteArrayOf())
    private val bridge = AudioMemoryFixture().bridge

    @Test
    fun `validation work is linear for distinct shared and interleaved physical sources`() {
        listOf(10, 100, 400, 1000, 2000).forEach { count ->
            listOf(1, 7, count).forEach { groups ->
                val source = CountedList(segments(count, groups))
                assertEquals(count * 2L, validate(source))
                assertTrue(
                    "n=$count groups=$groups reads=${source.reads}",
                    source.reads <= count * 2L,
                )
            }
        }
    }

    @Test
    fun `empty source and interleaved equal physical origins retain their accepted frames`() {
        assertEquals(0L, validate(emptyList()))
        assertEquals(12L, validate(segments(6, 2)))
        val maximum = segments(1, 1).single().copy(frames = 80_000)
        assertEquals(80_000L, validate(listOf(maximum)))
    }

    @Test
    fun `malformed identity order duplicate and frame bounds remain rejected`() {
        val source = segments(3, 2)
        val first = source[0]
        val row = source[2]
        val malformed =
            listOf(
                row.copy(identity = row.identity.copy(audio = audio.copy(sessionId = id(90)))),
                row.copy(identity = row.identity.copy(ordinal = 1)),
                row.copy(identity = row.identity.copy(firstFrame = 5, sourceFrameOffset = 5)),
                row.copy(identity = row.identity.copy(unitId = first.identity.unitId)),
                row.copy(identity = row.identity.copy(physicalSegmentId = "not-canonical")),
                row.copy(identity = row.identity.copy(sourceFrameOffset = 3)),
                row.copy(
                    identity = row.identity.copy(physicalFirstFrame = 2, sourceFrameOffset = 2)
                ),
                row.copy(frames = 0),
                row.copy(frames = -1),
                row.copy(frames = 80_001),
            )
        malformed.forEachIndexed { index, bad ->
            assertThrows("malformed case $index", IllegalStateException::class.java) {
                validate(source.take(2) + bad)
            }
        }
        assertThrows(IllegalStateException::class.java) { validate(source.reversed()) }
    }

    @Test
    fun `negative and overflowing physical arithmetic retain their rejection types`() {
        val row = segments(1, 1).single()
        listOf(
                row.identity.copy(physicalFirstFrame = -1, sourceFrameOffset = 1),
                row.identity.copy(physicalFirstFrame = 1, sourceFrameOffset = -1),
            )
            .forEach { bad ->
                assertThrows(IllegalArgumentException::class.java) {
                    validate(listOf(row.copy(identity = bad)))
                }
            }
        assertThrows(ArithmeticException::class.java) {
            validate(
                listOf(
                    row.copy(
                        identity =
                            row.identity.copy(
                                physicalFirstFrame = Long.MAX_VALUE,
                                sourceFrameOffset = 1,
                            )
                    )
                )
            )
        }
    }

    @Test
    fun `seeded valid and malformed catalogs preserve frozen baseline outcomes`() {
        val random = Random(863)
        repeat(128) {
            val count = random.nextInt(1, 65)
            val source = segments(count, random.nextInt(1, count + 1)).toMutableList()
            val index = random.nextInt(count)
            val row = source[index]
            source[index] =
                when (random.nextInt(12)) {
                    0 -> row.copy(identity = row.identity.copy(ordinal = -1))
                    1 ->
                        row.copy(
                            identity = row.identity.copy(unitId = source.first().identity.unitId)
                        )
                    2 -> row.copy(identity = row.identity.copy(physicalSegmentId = "BAD"))
                    3 -> row.copy(identity = row.identity.copy(physicalFirstFrame = -1))
                    4 -> row.copy(identity = row.identity.copy(sourceFrameOffset = -1))
                    5 ->
                        row.copy(
                            identity =
                                row.identity.copy(
                                    physicalFirstFrame = Long.MAX_VALUE,
                                    sourceFrameOffset = 1,
                                )
                        )
                    6 ->
                        row.copy(
                            identity = row.identity.copy(audio = audio.copy(sessionId = id(99)))
                        )
                    7 ->
                        row.copy(
                            identity = row.identity.copy(firstFrame = row.identity.firstFrame + 1)
                        )
                    8 -> row.copy(frames = 80_001)
                    9 -> row.copy(frames = 0)
                    else -> row
                }
            assertEquals(outcome { frozenPrefixValidator(source) }, outcome { validate(source) })
        }
    }

    // Explicit equivalence oracle pinned to 9c6b7563. Independent literal fixtures above
    // also assert the contract, so matching this oracle alone cannot certify correctness.
    private fun frozenPrefixValidator(source: List<StoredAudioSegment>): Long {
        var next = 0L
        val ids = mutableSetOf<String>()
        source.forEachIndexed { index, segment ->
            check(segment.identity.audio == audio && segment.identity.ordinal == index)
            check(segment.identity.firstFrame == next && ids.add(segment.identity.unitId))
            check(
                segment.identity.physicalSegmentId.matches(
                    Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
                )
            )
            check(
                AudioTimeline.nextFrame(
                    segment.identity.physicalFirstFrame,
                    segment.identity.sourceFrameOffset,
                ) == next
            )
            check(
                source
                    .take(index)
                    .filter { it.identity.physicalSegmentId == segment.identity.physicalSegmentId }
                    .all { it.identity.physicalFirstFrame == segment.identity.physicalFirstFrame }
            )
            check(segment.frames in 1..80_000)
            next = AudioTimeline.nextFrame(next, segment.frames)
        }
        return next
    }

    private fun outcome(block: () -> Long): Pair<Long?, Class<*>?> =
        try {
            block() to null
        } catch (failure: IllegalArgumentException) {
            null to failure.javaClass
        } catch (failure: IllegalStateException) {
            null to failure.javaClass
        } catch (failure: ArithmeticException) {
            null to failure.javaClass
        }

    private fun segments(count: Int, groups: Int) =
        List(count) { index ->
            StoredAudioSegment(
                AudioStorageUnitIdentity(
                    audio,
                    id(100L + index),
                    index,
                    index * 2L,
                    id(10_000L + index % groups),
                    0,
                    index * 2L,
                ),
                2,
                digest,
            )
        }

    private fun validate(segments: List<StoredAudioSegment>): Long {
        val method =
            RecoveryAudioBridge::class
                .java
                .getDeclaredMethod("validateOrder", StoredAudioAsset::class.java)
        method.isAccessible = true
        return try {
            method.invoke(bridge, StoredAudioAsset(audio, segments)) as Long
        } catch (failure: InvocationTargetException) {
            throw failure.targetException
        }
    }

    private class CountedList<T>(private val values: List<T>) : AbstractList<T>() {
        var reads = 0L
            private set

        override val size: Int
            get() = values.size

        override fun get(index: Int): T {
            reads++
            return values[index]
        }
    }

    private fun id(value: Long) = UUID(0L, value).toString()
}
