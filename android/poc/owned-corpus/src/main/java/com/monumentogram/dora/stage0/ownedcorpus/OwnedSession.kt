package com.monumentogram.dora.stage0.ownedcorpus

import android.content.Context
import java.io.File

/** Survives Activity recreation; only a new process performs interrupted-file recovery. */
class OwnedSession private constructor(context: Context, root: File) {
    val store = CorpusStore(root)
    val capture = AudioCapture(context.applicationContext, store)

    companion object {
        private val sessions = mutableMapOf<String, OwnedSession>()

        @Synchronized
        fun obtain(context: Context, root: File): OwnedSession =
            sessions.getOrPut(root.canonicalPath) { OwnedSession(context, root) }
    }
}
