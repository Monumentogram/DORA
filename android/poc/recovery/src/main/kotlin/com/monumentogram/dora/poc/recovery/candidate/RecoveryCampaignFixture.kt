package com.monumentogram.dora.poc.recovery.candidate

import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import java.security.MessageDigest

/** Deterministic synthetic input only. The host maintains the independent acceptance oracle. */
internal object RecoveryCampaignFixture {
    private const val MAX_PLAINTEXT_BYTES = 115_200_000
    private const val POSITION_MULTIPLIER = 31L
    private const val POSITION_BLOCK_SHIFT = 8
    private const val BLOCK_MULTIPLIER = 17L
    private const val BYTE_MASK = 255L
    private const val DIGEST_CHUNK_BYTES = 65_536

    fun bytes(seed: Int, start: Int, count: Int): ByteArray {
        require(
            seed >= 0 && start in 0..MAX_PLAINTEXT_BYTES && count in 0..MAX_PLAINTEXT_BYTES - start
        )
        return ByteArray(count) { offset ->
            val i = (start + offset).toLong()
            ((seed.toLong() +
                    i * POSITION_MULTIPLIER +
                    (i shr POSITION_BLOCK_SHIFT) * BLOCK_MULTIPLIER) and BYTE_MASK)
                .toByte()
        }
    }

    fun digest(seed: Int, count: Int): Sha256Value {
        require(seed >= 0 && count in 0..MAX_PLAINTEXT_BYTES)
        val digest = MessageDigest.getInstance("SHA-256")
        var start = 0
        while (start < count) {
            val size = minOf(DIGEST_CHUNK_BYTES, count - start)
            digest.update(bytes(seed, start, size))
            start += size
        }
        return Sha256Value.fromBytes(digest.digest())
    }
}
