package com.monumentogram.dora.stage0.ownedcorpus

/** A saved take can only proceed through human verification; it can never offer a retake. */
object GuidedFlow {
    enum class Action {
        WAIT,
        START,
        STOP,
        CONFIRM,
        NEXT,
    }

    fun action(recording: Boolean, saved: Boolean, verified: Boolean, ready: Boolean): Action =
        when {
            recording -> Action.STOP
            saved && verified -> Action.NEXT
            saved -> Action.CONFIRM
            ready -> Action.START
            else -> Action.WAIT
        }

    fun next(ids: List<String>, verified: Set<String>, current: String): String? {
        val after = ids.indexOf(current) + 1
        return (ids.drop(after) + ids.take(after)).firstOrNull { it !in verified }
    }
}
