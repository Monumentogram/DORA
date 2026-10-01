package com.monumentogram.dora.audio.persistence

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.system.StructStat
import com.monumentogram.dora.audio.AudioIdentity
import com.monumentogram.dora.audio.persistence.journal.AudioDeletionTarget
import com.monumentogram.dora.audio.persistence.journal.AudioDeletionTargetKind
import com.monumentogram.dora.audio.persistence.keys.NoLogRecoveryRunAeadBackend
import com.monumentogram.dora.poc.recovery.contract.CanonicalRecoveryAlias
import com.monumentogram.dora.poc.recovery.contract.RunId
import java.io.File
import java.io.FileDescriptor

/** Used only under the vault lease after the exact durable explicit-audio deletion fence. */
internal class AndroidAudioDeletionStorage(
    private val root: File,
    private val keys: NoLogRecoveryRunAeadBackend,
    private val hasCommittedBootstrap: (AudioIdentity, RunId) -> Boolean,
    private val sync: (FileDescriptor) -> Unit = Os::fsync,
) {
    fun remove(identity: AudioIdentity, target: AudioDeletionTarget) {
        val run = RunId.fromCanonicalString(target.runId)
        if (target.kind == AudioDeletionTargetKind.KEY_REFERENCE) {
            check(
                target.relativeName.isEmpty() &&
                    target.digest == CanonicalRecoveryAlias.sha256(run).toLowercaseHex()
            )
            // A reservation alone never proves key ownership. A collision or crash before
            // bootstrap commit must preserve an existing alias and keep deletion fenced.
            if (keys.aliasExists(run)) {
                check(hasCommittedBootstrap(identity, run)) { "Unproven deletion key ownership" }
                keys.removeAlias(run)
            }
            check(!keys.aliasExists(run))
        } else {
            for (path in paths(target)) removePath(path)
        }
    }

    /** Recheck absence and synchronize its enclosing directory even after an interrupted unlink. */
    fun verifiedAbsent(target: AudioDeletionTarget): Boolean {
        if (target.kind == AudioDeletionTargetKind.KEY_REFERENCE) {
            val run = RunId.fromCanonicalString(target.runId)
            check(target.digest == CanonicalRecoveryAlias.sha256(run).toLowercaseHex())
            return !keys.aliasExists(run)
        }
        return paths(target).all { path ->
            withParent(path) { parent, name ->
                val absent = statOrAbsent(anchored(parent, name)) == null
                if (absent) sync(parent.descriptor)
                absent
            } ?: true
        }
    }

    private fun paths(target: AudioDeletionTarget): List<List<String>> {
        RunId.fromCanonicalString(target.runId)
        val active = listOf("poc-recovery", "v1", "runs", target.runId)
        val retained = listOf("poc-recovery", "v1", "quarantine", target.runId)
        return when (target.kind) {
            AudioDeletionTargetKind.RUN_DIRECTORY -> {
                check(target.relativeName.isEmpty())
                listOf(active, retained)
            }
            AudioDeletionTargetKind.ARTIFACT -> listOf(active + components(target.relativeName))
            AudioDeletionTargetKind.QUARANTINE_ARTIFACT ->
                listOf(retained + components(target.relativeName))
            AudioDeletionTargetKind.KEY_REFERENCE -> error("Key is not a file")
        }
    }

    private fun components(name: String): List<String> {
        check(name.length in 1..MAX_RELATIVE_NAME && '\\' !in name && ':' !in name)
        return name.split('/').also { parts ->
            check(parts.all { it.isNotEmpty() && it != "." && it != ".." })
        }
    }

    private fun removePath(path: List<String>) {
        var remaining = MAX_ENTRIES
        withParent(path) { parent, name ->
            fun removeNode(directory: DirectoryHandle, leaf: String, depth: Int) {
                check(depth <= MAX_DEPTH && --remaining >= 0) {
                    "Deletion inventory bound exceeded"
                }
                val selected = anchored(directory, leaf)
                val before = statOrAbsent(selected)
                if (before == null) {
                    sync(directory.descriptor)
                    return
                }
                when {
                    OsConstants.S_ISREG(before.st_mode) -> {
                        check(before.st_nlink == 1L) { "Linked audio artifact rejected" }
                        val file = Os.open(selected, FLAGS, 0)
                        try {
                            check(same(before, Os.fstat(file)))
                            check(same(before, Os.lstat(selected)))
                            Os.remove(selected)
                            sync(directory.descriptor)
                        } finally {
                            Os.close(file)
                        }
                    }
                    OsConstants.S_ISDIR(before.st_mode) ->
                        directory(selected, before) { child ->
                            val names = checkNotNull(File(anchored(child, "")).list())
                            check(names.size <= remaining)
                            for (entry in names.sorted()) {
                                check(
                                    entry.isNotEmpty() &&
                                        entry != "." &&
                                        entry != ".." &&
                                        '/' !in entry
                                )
                                removeNode(child, entry, depth + 1)
                            }
                            check(same(before, Os.lstat(selected)))
                            sync(child.descriptor)
                            Os.remove(selected)
                            sync(directory.descriptor)
                        }
                    else -> error("Unsafe deletion artifact")
                }
                check(statOrAbsent(selected) == null)
            }
            removeNode(parent, name, 0)
        }
    }

    /** The descriptor anchors each child lookup; no recursive traversal follows a symlink. */
    private fun <T> withParent(path: List<String>, action: (DirectoryHandle, String) -> T): T? {
        check(path.isNotEmpty())
        return directory(root.path, Os.lstat(root.path)) { base ->
            fun descend(parent: DirectoryHandle, index: Int): T? {
                if (index == path.lastIndex) return action(parent, path[index])
                val childPath = anchored(parent, path[index])
                val child = statOrAbsent(childPath)
                return if (child == null) {
                    sync(parent.descriptor)
                    null
                } else directory(childPath, child) { descend(it, index + 1) }
            }
            descend(base, 0)
        }
    }

    private fun <T> directory(
        path: String,
        expected: StructStat,
        block: (DirectoryHandle) -> T,
    ): T {
        check(OsConstants.S_ISDIR(expected.st_mode)) { "Unsafe deletion directory" }
        val descriptor = Os.open(path, FLAGS, 0)
        try {
            check(same(expected, Os.fstat(descriptor)))
            check(same(expected, Os.lstat(path)))
            return ParcelFileDescriptor.dup(descriptor).use { duplicate ->
                block(DirectoryHandle(duplicate.fileDescriptor, duplicate.fd))
            }
        } finally {
            Os.close(descriptor)
        }
    }

    private data class DirectoryHandle(val descriptor: FileDescriptor, val number: Int)

    private fun anchored(directory: DirectoryHandle, name: String): String =
        "/proc/self/fd/${directory.number}/$name"

    private fun statOrAbsent(path: String): StructStat? =
        try {
            Os.lstat(path)
        } catch (error: ErrnoException) {
            if (error.errno == OsConstants.ENOENT) null else throw error
        }

    private fun same(left: StructStat, right: StructStat) =
        left.st_dev == right.st_dev && left.st_ino == right.st_ino && left.st_mode == right.st_mode

    private companion object {
        const val MAX_RELATIVE_NAME = 512
        const val MAX_DEPTH = 16
        const val MAX_ENTRIES = 100_000
        val FLAGS = OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW
    }
}
