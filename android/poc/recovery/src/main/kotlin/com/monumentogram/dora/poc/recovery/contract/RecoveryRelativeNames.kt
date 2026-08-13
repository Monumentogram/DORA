package com.monumentogram.dora.poc.recovery.contract

enum class RecoveryRelativeNameState {
    FINAL,
    TEMPORARY,
}

@Suppress("TooManyFunctions")
object RecoveryRelativeNames {
    fun streamCiphertext(
        state: RecoveryRelativeNameState = RecoveryRelativeNameState.FINAL
    ): String = withState("stream/stream.ct", state)

    fun streamKeyEnvelope(
        state: RecoveryRelativeNameState = RecoveryRelativeNameState.FINAL
    ): String = withState("key-envelopes/stream.ks", state)

    fun checkpointCiphertext(
        generation: ULong,
        state: RecoveryRelativeNameState = RecoveryRelativeNameState.FINAL,
    ): String = withState("checkpoints/g-${generationText(generation)}.ct", state)

    fun checkpointKeyEnvelope(
        generation: ULong,
        state: RecoveryRelativeNameState = RecoveryRelativeNameState.FINAL,
    ): String = withState("key-envelopes/checkpoint-g-${generationText(generation)}.ks", state)

    fun microfileCiphertext(
        unitIndex: ULong,
        state: RecoveryRelativeNameState = RecoveryRelativeNameState.FINAL,
    ): String = withState("units/u-${unitIndexText(unitIndex)}.ct", state)

    fun microfileKeyEnvelope(
        unitIndex: ULong,
        state: RecoveryRelativeNameState = RecoveryRelativeNameState.FINAL,
    ): String = withState("key-envelopes/u-${unitIndexText(unitIndex)}.ks", state)

    fun manifestCiphertext(
        generation: ULong,
        state: RecoveryRelativeNameState = RecoveryRelativeNameState.FINAL,
    ): String = withState("manifests/g-${generationText(generation)}.ct", state)

    fun manifestKeyEnvelope(
        generation: ULong,
        state: RecoveryRelativeNameState = RecoveryRelativeNameState.FINAL,
    ): String = withState("key-envelopes/manifest-g-${generationText(generation)}.ks", state)

    fun validateStreamCiphertext(
        value: String,
        state: RecoveryRelativeNameState = RecoveryRelativeNameState.FINAL,
    ) {
        validateExact(value, streamCiphertext(state))
    }

    fun validateStreamKeyEnvelope(
        value: String,
        state: RecoveryRelativeNameState = RecoveryRelativeNameState.FINAL,
    ) {
        validateExact(value, streamKeyEnvelope(state))
    }

    fun validateCheckpointCiphertext(
        value: String,
        generation: ULong,
        state: RecoveryRelativeNameState = RecoveryRelativeNameState.FINAL,
    ) {
        validateExact(value, checkpointCiphertext(generation, state))
    }

    fun validateCheckpointKeyEnvelope(
        value: String,
        generation: ULong,
        state: RecoveryRelativeNameState = RecoveryRelativeNameState.FINAL,
    ) {
        validateExact(value, checkpointKeyEnvelope(generation, state))
    }

    fun validateMicrofileCiphertext(
        value: String,
        unitIndex: ULong,
        state: RecoveryRelativeNameState = RecoveryRelativeNameState.FINAL,
    ) {
        validateExact(value, microfileCiphertext(unitIndex, state))
    }

    fun validateMicrofileKeyEnvelope(
        value: String,
        unitIndex: ULong,
        state: RecoveryRelativeNameState = RecoveryRelativeNameState.FINAL,
    ) {
        validateExact(value, microfileKeyEnvelope(unitIndex, state))
    }

    fun validateManifestCiphertext(
        value: String,
        generation: ULong,
        state: RecoveryRelativeNameState = RecoveryRelativeNameState.FINAL,
    ) {
        validateExact(value, manifestCiphertext(generation, state))
    }

    fun validateManifestKeyEnvelope(
        value: String,
        generation: ULong,
        state: RecoveryRelativeNameState = RecoveryRelativeNameState.FINAL,
    ) {
        validateExact(value, manifestKeyEnvelope(generation, state))
    }

    private fun generationText(value: ULong): String {
        contractRequire(value >= FIRST_GENERATION) { "Name generation must start at one" }
        return fixedWidth(value, GENERATION_WIDTH, "generation")
    }

    private fun unitIndexText(value: ULong): String {
        contractRequire(value <= RecoveryContract.U32_MAX) { "Name unit index does not fit U32" }
        return fixedWidth(value, UNIT_INDEX_WIDTH, "unit index")
    }

    private fun fixedWidth(
        value: ULong,
        width: Int,
        subject: String,
    ): String {
        val text = value.toString()
        contractRequire(text.length <= width) { "Name $subject exceeds its canonical width" }
        return text.padStart(width, '0')
    }

    private fun withState(
        finalName: String,
        state: RecoveryRelativeNameState,
    ): String =
        when (state) {
            RecoveryRelativeNameState.FINAL -> finalName
            RecoveryRelativeNameState.TEMPORARY -> "$finalName.tmp"
        }

    private fun validateExact(
        value: String,
        expected: String,
    ) {
        contractRequire(value == expected) { "Relative name is not the exact canonical identity" }
    }

    private const val FIRST_GENERATION = 1UL
    private const val GENERATION_WIDTH = 20
    private const val UNIT_INDEX_WIDTH = 10
}
