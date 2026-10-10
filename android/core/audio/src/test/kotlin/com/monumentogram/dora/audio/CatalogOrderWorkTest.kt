package com.monumentogram.dora.audio

import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import java.lang.reflect.InvocationTargetException
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Diagnostic characterization of source-list work in the real order validator. Reflection avoids
 * changing production visibility. This does not measure append latency, encryption, allocation,
 * SQL, filesystem work, capture pressure, or durability.
 */
class CatalogOrderWorkTest {
    private val identity = AudioIdentity(RecordingId(uuid(1)), AudioAssetId(uuid(2)), uuid(3))
    private val digest = Sha256Value.calculate(byteArrayOf())

    @Test
    fun `real validator reads each source once at all requested catalog sizes`() {
        val fixture = AudioMemoryFixture()
        val sizes = listOf(10, 100, 400, 1000, 2000)
        val observed = mutableListOf<Long>()
        sizes.forEach { n ->
            val counted = CountingList(segments(n, physicalGroupSize = 120))
            assertEquals(FRAMES_PER_UNIT * n, validate(fixture, counted))
            // C3 replaces the C2 prefix scan with one per-call physical-origin map.
            // Historical C2 counts remain sealed in its evidence package.
            val expectedReads = n.toLong()
            assertEquals("source reads at n=$n", expectedReads, counted.reads)
            observed += counted.reads
            println(
                "CATALOG_ORDER_WORK n=$n sourceElementReads=${counted.reads} " +
                    "validatedFrames=${FRAMES_PER_UNIT * n} physicalGroupSize=120"
            )
        }
        // Compare actual work across the entire range without wall-clock noise.
        assertTrue(observed.zipWithNext().all { (before, after) -> after > before })
        assertEquals((sizes.last() / sizes.first()).toLong(), observed.last() / observed.first())
        assertEquals(2_000L, observed.last())
        assertUntouched(fixture)
    }

    @Test
    fun `linear source reads do not depend on physical group match density`() {
        val fixture = AudioMemoryFixture()
        listOf(1, 120, 400).forEach { groupSize ->
            val counted = CountingList(segments(400, groupSize))
            assertEquals(FRAMES_PER_UNIT * 400, validate(fixture, counted))
            assertEquals(400L, counted.reads)
        }
        assertUntouched(fixture)
    }

    @Test
    fun `same physical segment with inconsistent first frame is rejected`() {
        val fixture = AudioMemoryFixture()
        val source = segments(3, physicalGroupSize = 3).toMutableList()
        val last = source.last()
        source[2] =
            last.copy(
                identity =
                    last.identity.copy(
                        physicalFirstFrame = FRAMES_PER_UNIT,
                        sourceFrameOffset = FRAMES_PER_UNIT,
                    )
            )
        // Ordinal, unit ID, firstFrame, and offset arithmetic remain valid: only
        // the repeated physical group's starting frame is inconsistent.
        assertThrows(IllegalStateException::class.java) { validate(fixture, source) }
        assertUntouched(fixture)
    }

    @Test
    fun `duplicate unit identity is rejected even with valid frame order`() {
        val fixture = AudioMemoryFixture()
        val source = segments(3, physicalGroupSize = 3).toMutableList()
        source[2] =
            source[2].copy(identity = source[2].identity.copy(unitId = source[0].identity.unitId))
        assertThrows(IllegalStateException::class.java) { validate(fixture, source) }
        assertUntouched(fixture)
    }

    @Test
    fun `out of order rows and frame gaps are rejected`() {
        val fixture = AudioMemoryFixture()
        val source = segments(3, physicalGroupSize = 3)
        assertThrows(IllegalStateException::class.java) {
            validate(fixture, listOf(source[1], source[0], source[2]))
        }
        val gap = source.toMutableList()
        gap[1] =
            gap[1].copy(
                identity =
                    gap[1]
                        .identity
                        .copy(
                            firstFrame = FRAMES_PER_UNIT + 1,
                            sourceFrameOffset = FRAMES_PER_UNIT + 1,
                        )
            )
        assertThrows(IllegalStateException::class.java) { validate(fixture, gap) }
        assertUntouched(fixture)
    }

    private fun segments(count: Int, physicalGroupSize: Int): List<StoredAudioSegment> =
        List(count) { index ->
            val firstFrame = index * FRAMES_PER_UNIT
            val physicalFirstFrame =
                (index / physicalGroupSize) * physicalGroupSize * FRAMES_PER_UNIT
            StoredAudioSegment(
                AudioStorageUnitIdentity(
                    identity,
                    uuid(10_000L + index),
                    index,
                    firstFrame,
                    uuid(20_000L + index / physicalGroupSize),
                    physicalFirstFrame,
                    firstFrame - physicalFirstFrame,
                ),
                FRAMES_PER_UNIT,
                digest,
            )
        }

    private fun validate(fixture: AudioMemoryFixture, segments: List<StoredAudioSegment>): Long {
        val method =
            RecoveryAudioBridge::class
                .java
                .getDeclaredMethod("validateOrder", StoredAudioAsset::class.java)
        method.isAccessible = true
        return try {
            method.invoke(fixture.bridge, StoredAudioAsset(identity, segments)) as Long
        } catch (failure: InvocationTargetException) {
            throw failure.targetException
        }
    }

    private fun assertUntouched(fixture: AudioMemoryFixture) {
        assertTrue(fixture.files.isEmpty())
        assertTrue(fixture.keys.isEmpty())
        assertTrue(fixture.units.isEmpty())
        assertTrue(fixture.publications.isEmpty())
        assertTrue(fixture.catalog.assets.isEmpty())
    }

    private class CountingList<T>(private val source: List<T>) : AbstractList<T>() {
        var reads = 0L
            private set

        override val size: Int
            get() = source.size

        override fun get(index: Int): T {
            reads++
            return source[index]
        }
    }

    private fun uuid(value: Long) = UUID(0L, value).toString()

    private companion object {
        const val FRAMES_PER_UNIT = 80_000L
    }
}
