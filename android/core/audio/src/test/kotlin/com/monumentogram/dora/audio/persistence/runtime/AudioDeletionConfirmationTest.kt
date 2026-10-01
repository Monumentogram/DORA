package com.monumentogram.dora.audio.persistence.runtime

import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.model.alpha.AudioAssetId
import com.monumentogram.dora.model.alpha.RecordingId
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDeletionConfirmationTest {
    private val identity =
        AudioIdentity(
            RecordingId("00000000-0000-0000-0000-000000000001"),
            AudioAssetId("00000000-0000-0000-0000-000000000002"),
            "00000000-0000-0000-0000-000000000003",
        )

    @Test
    fun exactConfirmationIsConsumedOnceAndRejectsWrongIdentity() {
        val confirmation = AudioDeletionConfirmation(identity) {}
        assertFalse(confirmation.consume(identity.copy(sessionId = "different")))
        assertTrue(confirmation.consume(identity))
        assertFalse(confirmation.consume(identity))
    }

    @Test
    fun cancellationAndRevocationNeverAuthorizeDeletion() {
        val cancelled = AudioDeletionConfirmation(identity) {}
        cancelled.cancel()
        assertFalse(cancelled.consume(identity))
        val revoked = AudioDeletionConfirmation(identity) { error("Locked") }
        assertFalse(revoked.consume(identity))
    }
}
