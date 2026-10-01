package com.monumentogram.dora.audio.persistence.runtime

/** Retain failed resources so later cleanup/open can retry instead of losing the only handle. */
internal class ResourceRetirement {
    private val pending = mutableListOf<AutoCloseable>()
    val isEmpty: Boolean
        get() = synchronized(this) { pending.isEmpty() }

    @Synchronized
    fun retire(resource: AutoCloseable) {
        if (pending.none { it === resource }) pending += resource
        retry()
    }

    @Synchronized
    fun retry() {
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            try {
                iterator.next().close()
                iterator.remove()
            } catch (_: Exception) {
                /* Keep the exact resource; a future authenticated open retries. */
            }
        }
    }
}
