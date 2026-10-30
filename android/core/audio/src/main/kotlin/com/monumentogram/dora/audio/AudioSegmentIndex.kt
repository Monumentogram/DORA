package com.monumentogram.dora.audio

/**
 * Immutable append index. Each node retains logarithmic ancestor pointers, never PCM. Appending
 * copies no historic segment list; seeking by ordinal takes O(log units). Previous snapshots stay
 * immutable across durable append/CAS. The maximum normal 24h recording has 17,280 nodes.
 */
internal class AudioSegmentIndex
private constructor(
    private val previous: AudioSegmentIndex?,
    private val value: StoredAudioSegment?,
    override val size: Int,
) : AbstractList<StoredAudioSegment>() {
    private val ancestors: Array<AudioSegmentIndex> =
        buildList {
                if (previous != null) {
                    add(previous)
                    var level = 0
                    while (get(level).ancestors.size > level) {
                        add(get(level).ancestors[level])
                        level++
                    }
                }
            }
            .toTypedArray()

    override fun get(index: Int): StoredAudioSegment {
        require(index in 0 until size)
        var node = this
        var distance = size - 1 - index
        var level = 0
        while (distance > 0) {
            if (distance and 1 != 0) node = node.ancestors[level]
            distance = distance ushr 1
            level++
        }
        return checkNotNull(node.value)
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            (other is AudioSegmentIndex &&
                size == other.size &&
                previous === other.previous &&
                value == other.value) ||
            super.equals(other)

    override fun hashCode(): Int = super.hashCode()

    companion object {
        private val EMPTY = AudioSegmentIndex(null, null, 0)

        fun empty(): AudioSegmentIndex = EMPTY

        fun from(source: List<StoredAudioSegment>): AudioSegmentIndex {
            if (source is AudioSegmentIndex) return source
            var index = EMPTY
            source.forEach { index = AudioSegmentIndex(index, it, index.size + 1) }
            return index
        }

        fun append(source: List<StoredAudioSegment>, value: StoredAudioSegment): AudioSegmentIndex {
            val index = from(source)
            return AudioSegmentIndex(index, value, Math.addExact(index.size, 1))
        }
    }
}
