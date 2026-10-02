package com.monumentogram.dora.recording

/**
 * Process-owned request identity survives UI recreation; cancellation never revives an old token.
 */
internal class ResumeRequests {
    class Request internal constructor(internal val owner: Any)

    private var pending: Request? = null

    @Synchronized
    fun begin(owner: Any): Request? {
        if (pending != null) return null
        return Request(owner).also { pending = it }
    }

    @Synchronized
    fun isCurrent(request: Request, owner: Any?): Boolean =
        pending === request && request.owner === owner

    @Synchronized
    fun complete(request: Request): Boolean {
        if (pending !== request) return false
        pending = null
        return true
    }

    @Synchronized
    fun cancel() {
        pending = null
    }
}
