package com.monumentogram.dora.recording

/** Synchronous lifecycle authority. Queued work cannot revive a destroyed service generation. */
internal class RecordingServiceOwner {
    class Token internal constructor()

    private var current: Token? = null

    @Synchronized fun created(): Token = Token().also { current = it }

    @Synchronized fun snapshot(): Token? = current

    @Synchronized
    fun destroyed() {
        current = null
    }

    @Synchronized fun isCurrent(token: Token?): Boolean = token != null && current === token

    @Synchronized
    fun runCurrent(token: Token?, action: () -> Unit): Boolean {
        if (!isCurrent(token)) return false
        action()
        return true
    }
}
