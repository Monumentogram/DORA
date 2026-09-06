package com.monumentogram.dora.poc.recovery.crypto

import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.StreamingAad
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RecoveryStreamingBoundProofTest {
    @Test
    fun `public parameters and open stream geometry are exact`() {
        val parameters = RecoveryTinkRuntime.parameterSnapshot()
        assertEquals(16, parameters.streamingInputKeyBytes)
        assertEquals(16, parameters.streamingDerivedKeyBytes)
        assertEquals("SHA256", parameters.streamingHkdfHash)
        assertEquals(CIPHERTEXT_SEGMENT_BYTES, parameters.streamingCiphertextSegmentBytes)

        val q1 = buildScenario(targetQ = 1, triggerBytes = 1)
        val q2 = buildScenario(targetQ = 2, triggerBytes = 1)
        val q3 = buildScenario(targetQ = 3, triggerBytes = 1)

        assertEquals(HEADER_BYTES, q1.exactFillCiphertextBytes)
        assertEquals(CIPHERTEXT_SEGMENT_BYTES, q1.actualCiphertext.size)
        assertEquals(CIPHERTEXT_SEGMENT_BYTES, q2.exactFillCiphertextBytes)
        assertEquals(2 * CIPHERTEXT_SEGMENT_BYTES, q2.actualCiphertext.size)
        assertEquals(2 * CIPHERTEXT_SEGMENT_BYTES, q3.exactFillCiphertextBytes)
        assertEquals(3 * CIPHERTEXT_SEGMENT_BYTES, q3.actualCiphertext.size)
    }

    @Test
    fun `q1 exact fill and trigger observations stay explicit`() {
        listOf(1 to 4_057, 4_080 to 8_136).forEach { (trigger, expectedA) ->
            val scenario = buildScenario(targetQ = 1, triggerBytes = trigger)
            assertEquals(FIRST_PLAINTEXT_BYTES, scenario.exactFillAcceptedWatermark)
            assertEquals(expectedA, scenario.acceptedWatermark)
            assertEquals(0, scenario.priorCommittedEnd)

            val capped = observe("q1-trigger-$trigger", scenario, TailSource.CHECKPOINT_CAPPED)
            val tail = observe("q1-trigger-$trigger", scenario, TailSource.ACTUAL_TAIL)
            assertObservation(capped, recoveredEnd = 0, successfulReads = emptyList())
            assertObservation(tail, recoveredEnd = 0, successfulReads = emptyList())
            assertEquals(expectedA, expectedA - tail.recoveredEndExclusive)
        }
    }

    @Test
    fun `q2 actual tail and checkpoint capped reads diverge`() {
        listOf(1 to 8_137, 4_080 to 12_216).forEach { (trigger, expectedA) ->
            val scenario = buildScenario(targetQ = 2, triggerBytes = trigger)
            assertEquals(8_136, scenario.exactFillAcceptedWatermark)
            assertEquals(expectedA, scenario.acceptedWatermark)
            assertEquals(0, scenario.priorCommittedEnd)

            val capped = observe("q2-trigger-$trigger", scenario, TailSource.CHECKPOINT_CAPPED)
            val tail = observe("q2-trigger-$trigger", scenario, TailSource.ACTUAL_TAIL)
            assertObservation(capped, recoveredEnd = 0, successfulReads = emptyList())
            assertObservation(tail, recoveredEnd = 4_056, successfulReads = listOf(4_056))
            assertEquals(expectedA, expectedA - capped.recoveredEndExclusive)
            assertEquals(if (trigger == 1) 4_081 else 8_160, expectedA - tail.recoveredEndExclusive)
        }
    }

    @Test
    fun `q3 actual tail preserves 8160 while checkpoint cap exposes 12240`() {
        listOf(1 to 12_217, 4_080 to 16_296).forEach { (trigger, expectedA) ->
            val scenario = buildScenario(targetQ = 3, triggerBytes = trigger)
            assertEquals(12_216, scenario.exactFillAcceptedWatermark)
            assertEquals(expectedA, scenario.acceptedWatermark)
            assertEquals(4_056, scenario.priorCommittedEnd)

            val capped = observe("q3-trigger-$trigger", scenario, TailSource.CHECKPOINT_CAPPED)
            val tail = observe("q3-trigger-$trigger", scenario, TailSource.ACTUAL_TAIL)
            assertObservation(capped, recoveredEnd = 4_056, successfulReads = listOf(4_056))
            assertObservation(tail, recoveredEnd = 8_136, successfulReads = listOf(4_056, 4_080))
            assertEquals(
                if (trigger == 1) 8_161 else 12_240,
                expectedA - capped.recoveredEndExclusive,
            )
            assertEquals(if (trigger == 1) 4_081 else 8_160, expectedA - tail.recoveredEndExclusive)
        }
    }

    @Test
    fun `checkpoint commit changes C independently from observed R`() {
        val scenario = buildScenario(targetQ = 3, triggerBytes = 1)
        val before = observe("q3-before-commit", scenario, TailSource.ACTUAL_TAIL)
        val afterC = committedEnd(3)
        val after =
            observe(
                "q3-after-commit",
                scenario.copy(priorCommittedEnd = afterC),
                TailSource.ACTUAL_TAIL,
            )

        assertEquals(4_056, scenario.priorCommittedEnd)
        assertEquals(8_136, afterC)
        assertEquals(before.recoveredEndExclusive, after.recoveredEndExclusive)
        assertEquals(8_136, after.recoveredEndExclusive)
        assertTrue(before.recoveredEndExclusive >= scenario.priorCommittedEnd)
    }

    @Test
    fun `open nonfinal failure and authenticated final EOF are distinct`() {
        val open = buildScenario(targetQ = 3, triggerBytes = 1)
        val openObservation = observe("open-q3", open, TailSource.ACTUAL_TAIL)
        assertObservation(
            openObservation,
            recoveredEnd = 8_136,
            successfulReads = listOf(4_056, 4_080),
        )
        assertEquals(TerminalReadOutcome.AUTHENTICATION_FAILURE, openObservation.terminal)

        val closed = buildClosedControl(8_136)
        val closedObservation = observe("control-closed", closed, TailSource.ACTUAL_TAIL)
        assertObservation(
            closedObservation,
            recoveredEnd = 8_136,
            successfulReads = listOf(4_056, 4_080),
            terminal = TerminalReadOutcome.AUTHENTICATED_EOF,
        )
        assertTrue(closedObservation.terminalExceptionClasses.isEmpty())
    }

    @Test
    fun `TRU03 append sizes expose lookahead ambiguity without deciding semantics`() {
        val emittedQ2 = buildScenario(targetQ = 2, triggerBytes = 1)
        val checkpointQ2 =
            emittedQ2.copy(
                priorCommittedEnd = committedEnd(2),
                checkpointCiphertextBytes = 2 * CIPHERTEXT_SEGMENT_BYTES,
            )
        val baseline = observe("tru03-baseline", checkpointQ2, TailSource.CHECKPOINT_CAPPED)
        assertObservation(baseline, recoveredEnd = 4_056, successfulReads = listOf(4_056))

        listOf(1, CIPHERTEXT_SEGMENT_BYTES, 2 * CIPHERTEXT_SEGMENT_BYTES).forEach { appendSize ->
            val appended = appendBytes(appendSize)
            val capped =
                observe(
                    "tru03-capped-append-$appendSize",
                    checkpointQ2,
                    TailSource.CHECKPOINT_CAPPED,
                    appended,
                )
            val tail =
                observe(
                    "tru03-tail-append-$appendSize",
                    checkpointQ2,
                    TailSource.ACTUAL_TAIL,
                    appended,
                )

            assertEquals(checkpointQ2.checkpointCiphertextBytes, capped.attemptedCiphertextBytes)
            assertEquals(baseline.recoveredEndExclusive, capped.recoveredEndExclusive)
            assertEquals(baseline.successfulReadSizes, capped.successfulReadSizes)
            assertObservation(tail, recoveredEnd = 8_136, successfulReads = listOf(4_056, 4_080))
            assertTrue(tail.recoveredEndExclusive <= checkpointQ2.oracle.size)
        }
    }

    @Test
    fun `Option A retains the authenticated preexisting tail after an appended authentication failure`() {
        val emittedQ2 = buildScenario(targetQ = 2, triggerBytes = 1)
        val checkpointQ2 =
            emittedQ2.copy(
                priorCommittedEnd = committedEnd(2),
                checkpointCiphertextBytes = 2 * CIPHERTEXT_SEGMENT_BYTES,
            )
        val publicRead =
            readPublic(
                checkpointQ2.keyset,
                selectSource(checkpointQ2, TailSource.ACTUAL_TAIL, appendBytes(1)),
            )

        assertTrue(publicRead.terminalError != null)
        assertEquals(listOf(4_056, 4_080), publicRead.successfulReadSizes)
        assertEquals(8_136, publicRead.recoveredBytes.size)

        val recovery =
            RecoveryStreamingAuthenticatedTailController(
                    acceptedEndExclusive = checkpointQ2.acceptedWatermark,
                    durableCheckpointEndExclusive = checkpointQ2.priorCommittedEnd,
                    oracle = checkpointQ2.oracle,
                )
                .recover(
                    completedAuthenticatedReads = listOf(publicRead.recoveredBytes),
                    terminal = AuthenticatedTailTerminal.AuthenticationFailure,
                )

        assertEquals(4_056, recovery.durableCheckpointEndExclusive)
        assertEquals(8_136, recovery.recoveredEndExclusive)
        assertEquals(4_080, recovery.recoveredBeyondCheckpointBytes)
        assertEquals(1, recovery.tailLossBytes)
        assertArrayEquals(checkpointQ2.oracle.copyOf(8_136), recovery.returnedBytes)
        assertEquals(
            BoundedRemainderClassification.AUTHENTICATION_FAILURE_QUARANTINED,
            recovery.remainder,
        )
        assertTrue(!recovery.metadataAdopted)
        assertTrue(!recovery.processingIntentAdopted)
        assertEquals(8_160, recovery.designBound.maximumBoundedTailLossBytes)
        assertEquals(255, recovery.designBound.maximumBoundedTailLossMillis)
    }

    @Test
    fun `Option A rejects a tail loss of 8161 bytes`() {
        val controller =
            RecoveryStreamingAuthenticatedTailController(
                acceptedEndExclusive = 8_161,
                durableCheckpointEndExclusive = 0,
                oracle = plaintext(8_161),
            )

        try {
            controller.recover(
                completedAuthenticatedReads = emptyList(),
                terminal = AuthenticatedTailTerminal.AuthenticationFailure,
            )
            fail("An 8,161-byte tail loss must be rejected")
        } catch (_: IllegalArgumentException) {
            // Expected: the bounded tail-loss rule rejects the result.
        }
    }

    @Test
    fun `Option A accepts the exact 8160 byte tail loss despite a larger recovered checkpoint distance`() {
        val acceptedBytes = 24_000
        val recoveredBytes = 15_840
        val recovery =
            RecoveryStreamingAuthenticatedTailController(
                    acceptedEndExclusive = acceptedBytes,
                    durableCheckpointEndExclusive = 0,
                    oracle = plaintext(acceptedBytes),
                )
                .recover(
                    completedAuthenticatedReads = listOf(plaintext(recoveredBytes)),
                    terminal = AuthenticatedTailTerminal.AuthenticationFailure,
                )

        assertEquals(recoveredBytes, recovery.recoveredEndExclusive)
        assertEquals(8_160, recovery.tailLossBytes)
        assertTrue(recovery.recoveredBeyondCheckpointBytes > 8_160)
    }

    @Test
    fun `partial lookahead and in progress calls preserve raw R at or below prior A`() {
        verifyHeldWrite(
            HeldWriteCase(
                caseId = "nearly-empty-partial",
                acceptedBeforeCall = 4_057,
                prefillAfterQ1 = 0,
                triggerBytes = 4_080,
                copiedBeforePause = 1,
                expectedExtent = 4_097,
                expectedR = 4_056,
            )
        )
        verifyHeldWrite(
            HeldWriteCase(
                caseId = "exact-full-partial-one",
                acceptedBeforeCall = 8_136,
                prefillAfterQ1 = 4_079,
                triggerBytes = 1,
                copiedBeforePause = 1,
                expectedExtent = 4_097,
                expectedR = 4_056,
            )
        )
        verifyHeldWrite(
            HeldWriteCase(
                caseId = "exact-full-complete-4080",
                acceptedBeforeCall = 8_136,
                prefillAfterQ1 = 4_079,
                triggerBytes = 4_080,
                copiedBeforePause = CIPHERTEXT_SEGMENT_BYTES,
                expectedExtent = 8_192,
                expectedR = 4_056,
            )
        )
        verifyPartialFailure()
    }

    private fun buildScenario(targetQ: Int, triggerBytes: Int): ProofScenario {
        require(targetQ in 1..3)
        require(triggerBytes == 1 || triggerBytes == LATER_PLAINTEXT_BYTES)
        val publisher = OpenPublisher()

        publisher.writeAccepted(FIRST_PLAINTEXT_BYTES)
        for (q in 1 until targetQ) {
            publisher.writeAccepted(1)
            publisher.writeAccepted(LATER_PLAINTEXT_BYTES - 1)
        }
        val exactFillA = publisher.acceptedWatermark
        val exactFillCiphertextBytes = publisher.ciphertextSize()
        publisher.writeAccepted(triggerBytes)

        val previousQ = targetQ - 1
        return publisher.snapshot(
            observedCompletedQ = targetQ,
            priorCommittedEnd = committedEnd(previousQ),
            checkpointCiphertextBytes = previousQ * CIPHERTEXT_SEGMENT_BYTES,
            exactFillAcceptedWatermark = exactFillA,
            exactFillCiphertextBytes = exactFillCiphertextBytes,
        )
    }

    private fun buildClosedControl(plaintextBytes: Int): ProofScenario {
        val publisher = OpenPublisher()
        publisher.writeAccepted(plaintextBytes)
        publisher.writer.close()
        return publisher.snapshot(
            observedCompletedQ = 1,
            priorCommittedEnd = 0,
            checkpointCiphertextBytes = publisher.ciphertextSize(),
            exactFillAcceptedWatermark = plaintextBytes,
            exactFillCiphertextBytes = publisher.ciphertextSize(),
        )
    }

    private fun observe(
        caseId: String,
        scenario: ProofScenario,
        source: TailSource,
        appended: ByteArray = byteArrayOf(),
    ): ReadObservation {
        val selected = selectSource(scenario, source, appended)
        val readResult = readPublic(scenario.keyset, selected)
        val recoveredBytes = readResult.recoveredBytes
        assertTrue(
            "Recovered bytes exceed accepted oracle",
            recoveredBytes.size <= scenario.oracle.size,
        )
        assertArrayEquals(scenario.oracle.copyOf(recoveredBytes.size), recoveredBytes)
        assertTrue(
            "Recovered R exceeds accepted A",
            recoveredBytes.size <= scenario.acceptedWatermark,
        )

        val terminalClasses = throwableClassChain(readResult.terminalError)
        val terminal =
            if (readResult.terminalError == null) {
                assertTrue("Only -1 may establish authenticated EOF", readResult.authenticatedEof)
                TerminalReadOutcome.AUTHENTICATED_EOF
            } else {
                assertTrue(
                    "Unexpected public Tink read failure: $terminalClasses",
                    isExpectedTinkCryptoReadFailure(readResult.terminalError),
                )
                TerminalReadOutcome.AUTHENTICATION_FAILURE
            }
        val observation =
            ReadObservation(
                recoveredEndExclusive = recoveredBytes.size,
                successfulReadSizes = readResult.successfulReadSizes,
                terminal = terminal,
                attemptedCiphertextBytes = selected.size,
                terminalExceptionClasses = terminalClasses,
            )
        emitObservation(caseId, source, scenario, observation)
        return observation
    }

    private fun selectSource(
        scenario: ProofScenario,
        source: TailSource,
        appended: ByteArray,
    ): ByteArray {
        val assembled = scenario.actualCiphertext + appended
        return when (source) {
            TailSource.CHECKPOINT_CAPPED -> {
                require(scenario.checkpointCiphertextBytes <= assembled.size)
                assembled.copyOfRange(0, scenario.checkpointCiphertextBytes)
            }
            TailSource.ACTUAL_TAIL -> assembled
        }
    }

    private fun readPublic(
        keyset: RecoveryStreamingKeyset,
        selected: ByteArray,
    ): PublicReadResult {
        var terminalError: Exception? = null
        val reader: InputStream? =
            try {
                keyset.newDecryptingStream(ByteArrayInputStream(selected), aad())
            } catch (error: Exception) {
                terminalError = error
                null
            }
        val recovered = ByteArrayOutputStream()
        val successfulReadSizes = mutableListOf<Int>()
        var authenticatedEof = false
        var requestSize = FIRST_PLAINTEXT_BYTES

        if (reader != null) {
            while (terminalError == null && !authenticatedEof) {
                val buffer = ByteArray(requestSize)
                val count =
                    try {
                        reader.read(buffer, 0, buffer.size)
                    } catch (error: Exception) {
                        terminalError = error
                        Int.MIN_VALUE
                    }
                when {
                    count > 0 -> {
                        successfulReadSizes += count
                        recovered.write(buffer, 0, count)
                        requestSize = LATER_PLAINTEXT_BYTES
                    }
                    count == -1 -> authenticatedEof = true
                    count == 0 -> fail("Public decrypting stream returned zero without progress")
                }
            }
        }
        return PublicReadResult(
            recoveredBytes = recovered.toByteArray(),
            successfulReadSizes = successfulReadSizes.toList(),
            authenticatedEof = authenticatedEof,
            terminalError = terminalError,
        )
    }

    private fun verifyHeldWrite(case: HeldWriteCase) {
        val destination = ControllableOutputStream()
        val publisher = OpenPublisher(destination)
        publisher.writeAccepted(FIRST_PLAINTEXT_BYTES)
        publisher.writeAccepted(1)
        if (case.prefillAfterQ1 > 0) publisher.writeAccepted(case.prefillAfterQ1)
        assertEquals(case.acceptedBeforeCall, publisher.acceptedWatermark)

        val gate = destination.pauseNextWrite(case.copiedBeforePause, failAfterPause = false)
        val threadError = AtomicReference<Throwable?>()
        val writerThread = Thread {
            try {
                publisher.writeWithoutAcceptance(case.triggerBytes)
            } catch (error: Throwable) {
                threadError.set(error)
            }
        }
        writerThread.start()
        assertTrue(
            "Timed out waiting for held downstream write",
            gate.reached.await(10, TimeUnit.SECONDS),
        )

        val held =
            publisher.snapshot(
                observedCompletedQ = 1,
                priorCommittedEnd = 0,
                checkpointCiphertextBytes = CIPHERTEXT_SEGMENT_BYTES,
                exactFillAcceptedWatermark = case.acceptedBeforeCall,
                exactFillCiphertextBytes = CIPHERTEXT_SEGMENT_BYTES,
            )
        assertEquals(case.expectedExtent, held.actualCiphertext.size)
        assertEquals(case.acceptedBeforeCall, held.acceptedWatermark)
        val observation = observe(case.caseId, held, TailSource.ACTUAL_TAIL)
        assertEquals(case.expectedR, observation.recoveredEndExclusive)
        assertTrue(observation.recoveredEndExclusive <= case.acceptedBeforeCall)

        gate.release.countDown()
        writerThread.join(10_000)
        assertTrue("Held writer did not finish", !writerThread.isAlive)
        threadError.get()?.let { throw AssertionError("Held writer failed", it) }
        publisher.accept(case.triggerBytes)
        assertEquals(case.acceptedBeforeCall + case.triggerBytes, publisher.acceptedWatermark)
    }

    private fun verifyPartialFailure() {
        val destination = ControllableOutputStream()
        val publisher = OpenPublisher(destination)
        publisher.writeAccepted(FIRST_PLAINTEXT_BYTES)
        publisher.writeAccepted(1)
        publisher.writeAccepted(LATER_PLAINTEXT_BYTES - 1)
        val priorA = publisher.acceptedWatermark

        val gate = destination.pauseNextWrite(copiedBeforePause = 1, failAfterPause = true)
        val threadError = AtomicReference<Throwable?>()
        val writerThread = Thread {
            try {
                publisher.writeWithoutAcceptance(1)
            } catch (error: Throwable) {
                threadError.set(error)
            }
        }
        writerThread.start()
        assertTrue(
            "Timed out waiting for partial downstream write",
            gate.reached.await(10, TimeUnit.SECONDS),
        )
        val partial =
            publisher.snapshot(
                observedCompletedQ = 1,
                priorCommittedEnd = 0,
                checkpointCiphertextBytes = CIPHERTEXT_SEGMENT_BYTES,
                exactFillAcceptedWatermark = priorA,
                exactFillCiphertextBytes = CIPHERTEXT_SEGMENT_BYTES,
            )
        assertEquals(4_097, partial.actualCiphertext.size)
        val observation = observe("partial-failure", partial, TailSource.ACTUAL_TAIL)
        assertEquals(4_056, observation.recoveredEndExclusive)
        assertTrue(observation.recoveredEndExclusive <= priorA)

        gate.release.countDown()
        writerThread.join(10_000)
        assertTrue("Partial writer did not finish", !writerThread.isAlive)
        assertTrue(threadError.get() is IOException)
        assertEquals(priorA, publisher.acceptedWatermark)
    }

    private fun assertObservation(
        observation: ReadObservation,
        recoveredEnd: Int,
        successfulReads: List<Int>,
        terminal: TerminalReadOutcome = TerminalReadOutcome.AUTHENTICATION_FAILURE,
    ) {
        assertEquals(recoveredEnd, observation.recoveredEndExclusive)
        assertEquals(successfulReads, observation.successfulReadSizes)
        assertEquals(terminal, observation.terminal)
    }

    private fun emitObservation(
        caseId: String,
        source: TailSource,
        scenario: ProofScenario,
        observation: ReadObservation,
    ) {
        val reads = observation.successfulReadSizes.joinToString(",", prefix = "[", postfix = "]")
        val terminalClasses =
            observation.terminalExceptionClasses.joinToString(">", prefix = "[", postfix = "]")
        println(
            "BOUND_PROOF_OBSERVATION" +
                " case=$caseId" +
                " source=$source" +
                " q=${scenario.observedCompletedQ}" +
                " attemptedCiphertextBytes=${observation.attemptedCiphertextBytes}" +
                " A=${scenario.acceptedWatermark}" +
                " C=${scenario.priorCommittedEnd}" +
                " R=${observation.recoveredEndExclusive}" +
                " reads=$reads" +
                " terminal=${observation.terminal}" +
                " terminalClasses=$terminalClasses"
        )
    }

    private fun throwableClassChain(error: Throwable?): List<String> {
        val result = mutableListOf<String>()
        var cursor = error
        while (cursor != null && result.size < 8) {
            result += cursor.javaClass.name
            cursor = cursor.cause
        }
        return result
    }

    private fun isExpectedTinkCryptoReadFailure(error: Throwable): Boolean =
        generateSequence(error as Throwable?) { it.cause }.any(::isExpectedTinkFailureSignal)

    private fun isExpectedTinkFailureSignal(error: Throwable): Boolean =
        error is GeneralSecurityException ||
            error.javaClass.name.startsWith("com.google.crypto.tink.") ||
            (error is IOException &&
                listOf("tag", "ciphertext", "decryption", "header", "segment").any {
                    error.message?.contains(it, ignoreCase = true) == true
                })

    private fun aad(): StreamingAad =
        StreamingAad(RecoveryCandidate.STREAM, RecoveryCryptoTestFixtures.RUN_ID)

    private fun plaintext(size: Int, start: Int = 0): ByteArray =
        ByteArray(size) { index -> (((start + index) * 31 + 7) and 0xff).toByte() }

    private fun appendBytes(size: Int): ByteArray =
        ByteArray(size) { index -> ((index * 17 + 0xa5) and 0xff).toByte() }

    private fun committedEnd(q: Int): Int =
        if (q < 2) 0 else FIRST_PLAINTEXT_BYTES + (q - 2) * LATER_PLAINTEXT_BYTES

    private inner class OpenPublisher(val destination: OutputStream = ByteArrayOutputStream()) {
        val keyset = RecoveryCryptoTestFixtures.preparedStreamingKeyset()
        val writer = keyset.newEncryptingStream(destination, aad())
        private val acceptedOracle = ByteArrayOutputStream()
        var acceptedWatermark: Int = 0
            private set

        fun writeAccepted(length: Int) {
            val bytes = plaintext(length, acceptedWatermark)
            writer.write(bytes)
            acceptedOracle.write(bytes)
            acceptedWatermark += length
        }

        fun writeWithoutAcceptance(length: Int) {
            writer.write(plaintext(length, acceptedWatermark))
        }

        fun accept(length: Int) {
            val bytes = plaintext(length, acceptedWatermark)
            acceptedOracle.write(bytes)
            acceptedWatermark += length
        }

        fun ciphertextSize(): Int =
            when (destination) {
                is ByteArrayOutputStream -> destination.size()
                is ControllableOutputStream -> destination.snapshot().size
                else -> error("Unsupported test destination")
            }

        fun snapshot(
            observedCompletedQ: Int,
            priorCommittedEnd: Int,
            checkpointCiphertextBytes: Int,
            exactFillAcceptedWatermark: Int,
            exactFillCiphertextBytes: Int,
        ): ProofScenario {
            val ciphertext =
                when (destination) {
                    is ByteArrayOutputStream -> destination.toByteArray()
                    is ControllableOutputStream -> destination.snapshot()
                    else -> error("Unsupported test destination")
                }
            return ProofScenario(
                observedCompletedQ = observedCompletedQ,
                acceptedWatermark = acceptedWatermark,
                priorCommittedEnd = priorCommittedEnd,
                checkpointCiphertextBytes = checkpointCiphertextBytes,
                attemptedCiphertextBytes = ciphertext.size,
                actualCiphertext = ciphertext,
                oracle = acceptedOracle.toByteArray(),
                keyset = keyset,
                exactFillAcceptedWatermark = exactFillAcceptedWatermark,
                exactFillCiphertextBytes = exactFillCiphertextBytes,
            )
        }
    }

    private class ControllableOutputStream : OutputStream() {
        private val destination = ByteArrayOutputStream()
        private val nextGate = AtomicReference<WriteGate?>()

        override fun write(value: Int) {
            write(byteArrayOf(value.toByte()), 0, 1)
        }

        override fun write(bytes: ByteArray, offset: Int, length: Int) {
            val gate = nextGate.getAndSet(null)
            if (gate == null) {
                destination.write(bytes, offset, length)
                return
            }
            require(gate.copiedBeforePause in 0..length)
            destination.write(bytes, offset, gate.copiedBeforePause)
            gate.reached.countDown()
            check(gate.release.await(10, TimeUnit.SECONDS)) { "Timed out waiting to release write" }
            if (gate.failAfterPause) throw IOException("synthetic partial downstream failure")
            destination.write(
                bytes,
                offset + gate.copiedBeforePause,
                length - gate.copiedBeforePause,
            )
        }

        fun pauseNextWrite(copiedBeforePause: Int, failAfterPause: Boolean): WriteGate {
            val gate = WriteGate(copiedBeforePause, failAfterPause)
            check(nextGate.compareAndSet(null, gate)) { "A write gate is already active" }
            return gate
        }

        fun snapshot(): ByteArray = destination.toByteArray()
    }

    private data class WriteGate(
        val copiedBeforePause: Int,
        val failAfterPause: Boolean,
        val reached: CountDownLatch = CountDownLatch(1),
        val release: CountDownLatch = CountDownLatch(1),
    )

    private companion object {
        const val HEADER_BYTES = 24
        const val FIRST_PLAINTEXT_BYTES = 4_056
        const val LATER_PLAINTEXT_BYTES = 4_080
        const val CIPHERTEXT_SEGMENT_BYTES = 4_096
    }
}

private enum class TailSource {
    CHECKPOINT_CAPPED,
    ACTUAL_TAIL,
}

private enum class TerminalReadOutcome {
    AUTHENTICATED_EOF,
    AUTHENTICATION_FAILURE,
}

private data class ReadObservation(
    val recoveredEndExclusive: Int,
    val successfulReadSizes: List<Int>,
    val terminal: TerminalReadOutcome,
    val attemptedCiphertextBytes: Int,
    val terminalExceptionClasses: List<String>,
)

private data class PublicReadResult(
    val recoveredBytes: ByteArray,
    val successfulReadSizes: List<Int>,
    val authenticatedEof: Boolean,
    val terminalError: Exception?,
)

private data class HeldWriteCase(
    val caseId: String,
    val acceptedBeforeCall: Int,
    val prefillAfterQ1: Int,
    val triggerBytes: Int,
    val copiedBeforePause: Int,
    val expectedExtent: Int,
    val expectedR: Int,
)

private data class ProofScenario(
    val observedCompletedQ: Int,
    val acceptedWatermark: Int,
    val priorCommittedEnd: Int,
    val checkpointCiphertextBytes: Int,
    val attemptedCiphertextBytes: Int,
    val actualCiphertext: ByteArray,
    val oracle: ByteArray,
    val keyset: RecoveryStreamingKeyset,
    val exactFillAcceptedWatermark: Int,
    val exactFillCiphertextBytes: Int,
)
