package com.monumentogram.dora.poc.recovery.storage

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.monumentogram.dora.poc.recovery.candidate.QuarantinePathObservation
import com.monumentogram.dora.poc.recovery.candidate.QuarantinePathState
import com.monumentogram.dora.poc.recovery.candidate.RecoveryArtifactBytes
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineIntentRow
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineStorage
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

internal interface RecoveryReadDescriptor : Closeable {
    val regularFile: Boolean
    val size: Long

    fun read(buffer: ByteArray, offset: Int, length: Int): Int
}

internal fun interface RecoveryDescriptorOpener {
    fun open(path: String): RecoveryReadDescriptor
}

internal class RecoveryPathSafetyValidator(private val observe: (File) -> BootstrapPathType) {
    fun requireDirectory(file: File) {
        check(observe(file) == BootstrapPathType.DIRECTORY) {
            "Unsafe Recovery directory: ${file.name}"
        }
    }

    fun validateParentChain(root: File, leaf: File) {
        requireDirectory(root)
        var current = leaf.parentFile
        while (current != null && current != root) {
            requireDirectory(current)
            current = current.parentFile
        }
        check(current == root) { "Recovery path escaped its root" }
    }
}

@Suppress("MagicNumber")
internal object RecoveryArtifactRoleBounds {
    fun maximumFor(relativeName: String): Long =
        when {
            relativeName.startsWith("key-confirmation/") -> 512L
            relativeName.startsWith("units/") -> 960_256L
            relativeName.startsWith("key-envelopes/") -> 65_536L
            relativeName.startsWith("manifests/") -> 262_144L
            else -> 1_048_576L
        }
}

@Suppress("MagicNumber")
internal class RecoveryBoundedDescriptorReader(private val opener: RecoveryDescriptorOpener) {
    fun read(path: String, maximumBytes: Long): ByteArray {
        require(maximumBytes > 0)
        return opener.open(path).use { descriptor ->
            check(descriptor.regularFile && descriptor.size in 0..maximumBytes) {
                "Recovery artifact exceeds its role bound"
            }
            val output = ByteArrayOutputStream(descriptor.size.toInt())
            val buffer = ByteArray(minOf(8192, maximumBytes.toInt()))
            var count = 0L
            while (count < descriptor.size) {
                val read =
                    descriptor.read(
                        buffer,
                        0,
                        minOf(buffer.size.toLong(), descriptor.size - count).toInt(),
                    )
                check(read > 0) { "Recovery artifact read made no progress" }
                output.write(buffer, 0, read)
                count += read
            }
            check(descriptor.read(buffer, 0, 1) == -1) {
                "Recovery artifact grew during bounded read"
            }
            output.toByteArray()
        }
    }
}

@Suppress("TooManyFunctions", "MagicNumber")
internal class AndroidOsRecoveryReconciliationStorage
private constructor(
    private val root: File,
    private val boundedReader: RecoveryBoundedDescriptorReader,
) : RecoveryQuarantineStorage {
    private val pathSafety = RecoveryPathSafetyValidator(::type)

    constructor(
        context: Context
    ) : this(
        context.applicationContext.noBackupFilesDir,
        RecoveryBoundedDescriptorReader(AndroidRecoveryDescriptorOpener),
    )

    override fun prepare(runId: RunId) {
        val paths =
            RecoveryReconciliationPathPolicy.paths(
                root,
                runId,
                "key-confirmation/run.kc",
                "objects/q-${"0".repeat(64)}.bin",
            )
        val base = File(root, "poc-recovery/v1")
        pathSafety.requireDirectory(base)
        createDirectory(File(base, "quarantine"))
        createDirectory(paths.quarantineRunRoot)
        createDirectory(paths.objectsRoot)
    }

    fun activeArtifactExists(runId: RunId, relativeName: String): Boolean {
        val paths = inspectionPaths(runId, relativeName)
        pathSafety.validateParentChain(paths.activeRunRoot, paths.source)
        return when (type(paths.source)) {
            BootstrapPathType.ABSENT -> false
            BootstrapPathType.REGULAR -> true
            else -> error("Unsafe Recovery active artifact: $relativeName")
        }
    }

    fun loadActiveArtifact(
        runId: RunId,
        relativeName: String,
        maximumBytes: Long,
    ): RecoveryArtifactBytes? {
        require(maximumBytes > 0)
        val paths = inspectionPaths(runId, relativeName)
        pathSafety.validateParentChain(paths.activeRunRoot, paths.source)
        if (type(paths.source) == BootstrapPathType.ABSENT) return null
        check(type(paths.source) == BootstrapPathType.REGULAR) { "Unsafe Recovery active artifact" }
        return RecoveryArtifactBytes(
            relativeName,
            boundedReader.read(
                paths.source.path,
                minOf(maximumBytes, RecoveryArtifactRoleBounds.maximumFor(relativeName)),
            ),
        )
    }

    fun listActiveArtifacts(runId: RunId): List<RecoveryArtifactBytes> {
        val runRoot = inspectionPaths(runId, "key-confirmation/run.kc").activeRunRoot
        val result = mutableListOf<RecoveryArtifactBytes>()
        fun visit(directory: File) {
            val children = directory.listFiles() ?: error("Cannot enumerate Recovery directory")
            for (child in children.sortedBy { it.name }) {
                when (type(child)) {
                    BootstrapPathType.DIRECTORY -> visit(child)
                    BootstrapPathType.REGULAR -> {
                        val relative = child.relativeTo(runRoot).invariantSeparatorsPath
                        result +=
                            RecoveryArtifactBytes(
                                relative,
                                boundedReader.read(
                                    child.path,
                                    RecoveryArtifactRoleBounds.maximumFor(relative),
                                ),
                            )
                    }
                    else -> error("Unsafe Recovery inventory object: ${child.name}")
                }
            }
        }
        visit(runRoot)
        return java.util.Collections.unmodifiableList(result)
    }

    private fun inspectionPaths(runId: RunId, relativeName: String): RecoveryReconciliationPaths {
        val paths =
            RecoveryReconciliationPathPolicy.paths(
                root,
                runId,
                relativeName,
                "objects/q-${"0".repeat(64)}.bin",
            )
        val base = File(root, "poc-recovery")
        listOf(root, base, File(base, "v1"), File(base, "v1/runs"), paths.activeRunRoot)
            .forEach(pathSafety::requireDirectory)
        return paths
    }

    override fun inspect(row: RecoveryQuarantineIntentRow): QuarantinePathObservation {
        val paths = paths(row)
        pathSafety.validateParentChain(paths.activeRunRoot, paths.source)
        pathSafety.validateParentChain(paths.quarantineRunRoot, paths.destination)
        return QuarantinePathObservation(state(paths.source, row), state(paths.destination, row))
    }

    override fun renameNoOverwrite(row: RecoveryQuarantineIntentRow) {
        val paths = paths(row)
        check(state(paths.source, row) == QuarantinePathState.EXACT) { "Quarantine source changed" }
        check(type(paths.destination) == BootstrapPathType.ABSENT) {
            "Quarantine destination occupied"
        }
        Os.rename(paths.source.path, paths.destination.path)
    }

    override fun fsyncSourceParent(row: RecoveryQuarantineIntentRow) =
        syncDirectory(paths(row).source.parentFile!!)

    override fun fsyncDestinationParent(row: RecoveryQuarantineIntentRow) =
        syncDirectory(paths(row).objectsRoot)

    private fun paths(row: RecoveryQuarantineIntentRow) =
        RecoveryReconciliationPathPolicy.paths(
            root,
            row.input.runId,
            row.input.sourceRelativeName,
            row.destinationRelativeName,
        )

    private fun state(file: File, row: RecoveryQuarantineIntentRow): QuarantinePathState =
        when (type(file)) {
            BootstrapPathType.ABSENT -> QuarantinePathState.ABSENT
            BootstrapPathType.REGULAR -> {
                val (bytes, digest) = identity(file)
                if (bytes == row.input.sourceBytes && digest == row.input.sourceSha256)
                    QuarantinePathState.EXACT
                else QuarantinePathState.OCCUPIED
            }
            else -> QuarantinePathState.UNSAFE
        }

    private fun identity(file: File): Pair<ULong, Sha256Value> {
        val descriptor =
            Os.open(
                file.path,
                OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
                0,
            )
        return FileInputStream(descriptor).use { input ->
            check(OsConstants.S_ISREG(Os.fstat(descriptor).st_mode)) {
                "Recovery artifact is not regular"
            }
            val digest = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(8192)
            var count = 0UL
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                check(read > 0) { "Recovery artifact read made no progress" }
                count += read.toULong()
                digest.update(buffer, 0, read)
            }
            count to Sha256Value.fromBytes(digest.digest())
        }
    }

    private fun createDirectory(directory: File) {
        when (type(directory)) {
            BootstrapPathType.ABSENT -> {
                Os.mkdir(directory.path, 0x1c0)
                pathSafety.requireDirectory(directory)
                syncDirectory(directory.parentFile!!)
            }
            BootstrapPathType.DIRECTORY -> Unit
            else -> error("Unsafe Recovery quarantine directory: ${directory.name}")
        }
    }

    private fun syncDirectory(directory: File) {
        val descriptor = Os.open(directory.path, OsConstants.O_RDONLY or OsConstants.O_CLOEXEC, 0)
        try {
            Os.fsync(descriptor)
        } finally {
            Os.close(descriptor)
        }
    }

    private fun type(file: File): BootstrapPathType =
        try {
            val mode = Os.lstat(file.path).st_mode
            when {
                OsConstants.S_ISLNK(mode) -> BootstrapPathType.SYMLINK
                OsConstants.S_ISREG(mode) -> BootstrapPathType.REGULAR
                OsConstants.S_ISDIR(mode) -> BootstrapPathType.DIRECTORY
                else -> BootstrapPathType.OTHER
            }
        } catch (error: ErrnoException) {
            if (error.errno == OsConstants.ENOENT) BootstrapPathType.ABSENT else throw error
        }
}

private object AndroidRecoveryDescriptorOpener : RecoveryDescriptorOpener {
    override fun open(path: String): RecoveryReadDescriptor {
        val descriptor =
            Os.open(
                path,
                OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
                0,
            )
        val input = FileInputStream(descriptor)
        val stat = Os.fstat(descriptor)
        return object : RecoveryReadDescriptor {
            override val regularFile = OsConstants.S_ISREG(stat.st_mode)
            override val size = stat.st_size

            override fun read(buffer: ByteArray, offset: Int, length: Int) =
                input.read(buffer, offset, length)

            override fun close() = input.close()
        }
    }
}
