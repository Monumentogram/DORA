package com.monumentogram.dora.poc.capture.runtime

import java.util.concurrent.atomic.AtomicBoolean

class IdempotentStopGate {
    private val requested = AtomicBoolean(false)

    fun request(): Boolean = requested.compareAndSet(false, true)

    fun isRequested(): Boolean = requested.get()
}
