package com.monumentogram.dora.poc.recovery.candidate

/** Nonpersisted fstat observation; neither a ciphertext digest nor authentication proof. */
internal data class RecoveryArtifactSizeLimitObservation(
    val relativeName: String,
    val observedBytes: Long,
    val maximumBytes: Long,
)
