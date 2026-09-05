package com.monumentogram.dora.poc.recovery.storage

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import com.monumentogram.dora.poc.recovery.candidate.QuarantinePathObservation
import com.monumentogram.dora.poc.recovery.candidate.QuarantinePathState
import com.monumentogram.dora.poc.recovery.candidate.RecoveryArtifactBytes
import com.monumentogram.dora.poc.recovery.candidate.RecoveryArtifactPresence
import com.monumentogram.dora.poc.recovery.candidate.RecoveryFailureCategory
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineIntentRow
import com.monumentogram.dora.poc.recovery.candidate.RecoveryQuarantineStorage
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import java.io.File
import java.io.FileDescriptor

internal interface RecoveryReconciliationDescriptor

internal data class RecoveryReconciliationStat(val type: BootstrapPathType, val size: Long = 0)

internal class RecoveryUnsafePathException(
    message: String,
    val category: RecoveryFailureCategory = RecoveryFailureCategory.UNSAFE_PARENT,
) : IllegalStateException(message)

internal class RecoveryArtifactAccessException(
    val presence: RecoveryArtifactPresence,
    val structural: Boolean,
    cause: Throwable,
) : IllegalStateException(cause.message, cause)

/** Raw Android-Os-shaped seam. read uses POSIX semantics: positive progress, zero EOF. */
internal interface RecoveryReconciliationOs {
    fun lstat(path: String): RecoveryReconciliationStat?

    fun list(path: String): List<String>

    fun mkdir(path: String, mode: Int)

    fun open(path: String, flags: Int): RecoveryReconciliationDescriptor

    fun fstat(descriptor: RecoveryReconciliationDescriptor): RecoveryReconciliationStat

    fun read(
        descriptor: RecoveryReconciliationDescriptor,
        buffer: ByteArray,
        offset: Int,
        count: Int,
    ): Int

    fun rename(source: String, destination: String)

    fun fsync(descriptor: RecoveryReconciliationDescriptor)

    fun close(descriptor: RecoveryReconciliationDescriptor)
}

internal data class RecoveryInventoryArtifact(
    val relativeName: String,
    val artifact: RecoveryArtifactBytes?,
    val pathType: BootstrapPathType,
)

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

@Suppress(
    "TooGenericExceptionCaught",
    "ThrowingExceptionFromFinally",
    "TooManyFunctions",
    "MagicNumber",
    "LongMethod",
)
internal class AndroidOsRecoveryReconciliationStorage
internal constructor(
    private val root: File,
    private val os: RecoveryReconciliationOs,
) : RecoveryQuarantineStorage {
    constructor(
        context: Context
    ) : this(context.applicationContext.noBackupFilesDir, AndroidRecoveryReconciliationOs)

    override fun prepare(runId: RunId) {
        val paths = paths(runId, "key-confirmation/run.kc", zeroDestination())
        requireActiveAncestors(paths)
        val base = File(root, "poc-recovery/v1")
        createDirectory(File(base, "quarantine"))
        createDirectory(paths.quarantineRunRoot)
        createDirectory(paths.objectsRoot)
        requireQuarantineAncestors(paths)
    }

    fun activeArtifactExists(runId: RunId, relativeName: String): Boolean {
        val paths = paths(runId, relativeName, zeroDestination())
        requireActiveAncestors(paths)
        requireDirectoryChain(paths.activeRunRoot, requireNotNull(paths.source.parentFile))
        return when (type(paths.source)) {
            BootstrapPathType.ABSENT -> false
            BootstrapPathType.REGULAR -> true
            else ->
                throw RecoveryUnsafePathException(
                    "Unsafe Recovery active artifact: $relativeName",
                    RecoveryFailureCategory.CORRUPT_LEAF,
                )
        }
    }

    fun loadActiveArtifact(
        runId: RunId,
        relativeName: String,
        maximumBytes: Long,
    ): RecoveryArtifactBytes? {
        require(maximumBytes > 0)
        val paths = paths(runId, relativeName, zeroDestination())
        requireActiveAncestors(paths)
        requireDirectoryChain(paths.activeRunRoot, requireNotNull(paths.source.parentFile))
        return when (type(paths.source)) {
            BootstrapPathType.ABSENT -> null
            BootstrapPathType.REGULAR ->
                loadRegularArtifact(paths.source, relativeName, maximumBytes)
            else ->
                throw RecoveryUnsafePathException(
                    "Unsafe Recovery active artifact: $relativeName",
                    RecoveryFailureCategory.CORRUPT_LEAF,
                )
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun loadRegularArtifact(
        source: File,
        relativeName: String,
        maximumBytes: Long,
    ): RecoveryArtifactBytes =
        try {
            RecoveryArtifactBytes(
                relativeName,
                readBounded(
                    source,
                    minOf(maximumBytes, RecoveryArtifactRoleBounds.maximumFor(relativeName)),
                ),
            )
        } catch (error: RecoveryArtifactAccessException) {
            throw error
        } catch (error: RecoveryUnsafePathException) {
            throw error
        } catch (error: Throwable) {
            throw RecoveryArtifactAccessException(RecoveryArtifactPresence.PRESENT, false, error)
        }

    fun listActiveArtifacts(runId: RunId): List<RecoveryArtifactBytes> =
        listActiveInventory(runId).map { requireNotNull(it.artifact) }

    fun listActiveInventory(runId: RunId): List<RecoveryInventoryArtifact> {
        val paths = paths(runId, "key-confirmation/run.kc", zeroDestination())
        requireActiveAncestors(paths)
        val result = mutableListOf<RecoveryInventoryArtifact>()
        visitActive(paths.activeRunRoot, paths.activeRunRoot, result)
        return java.util.Collections.unmodifiableList(result)
    }

    /** Quarantine inventory is one level only and report-only. */
    @Suppress("ReturnCount")
    fun listQuarantineInventory(runId: RunId): List<RecoveryInventoryArtifact> {
        val paths = paths(runId, "key-confirmation/run.kc", zeroDestination())
        requireActiveBaseAncestors()
        if (!optionalDirectory(File(root, "poc-recovery/v1/quarantine"))) return emptyList()
        if (!optionalDirectory(paths.quarantineRunRoot)) return emptyList()
        if (!optionalDirectory(paths.objectsRoot)) return emptyList()
        return os.list(paths.objectsRoot.path)
            .sorted()
            .map { childName ->
                requireSingleName(childName)
                val child = File(paths.objectsRoot, childName)
                when (val childType = type(child)) {
                    BootstrapPathType.REGULAR ->
                        RecoveryInventoryArtifact(
                            "objects/$childName",
                            RecoveryArtifactBytes(
                                "objects/$childName",
                                readBounded(
                                    child,
                                    RecoveryArtifactRoleBounds.maximumFor("unknown.bin"),
                                ),
                            ),
                            childType,
                        )
                    else -> RecoveryInventoryArtifact("objects/$childName", null, childType)
                }
            }
            .let { java.util.Collections.unmodifiableList(it) }
    }

    override fun inspect(row: RecoveryQuarantineIntentRow): QuarantinePathObservation {
        val paths = paths(row)
        requireAllAncestors(paths)
        return QuarantinePathObservation(
            state(
                paths.source,
                row.input.sourceRelativeName,
                row.input.sourceBytes,
                row.input.sourceSha256,
            ),
            state(
                paths.destination,
                row.input.sourceRelativeName,
                row.input.sourceBytes,
                row.input.sourceSha256,
            ),
        )
    }

    override fun renameNoOverwrite(row: RecoveryQuarantineIntentRow) {
        val paths = paths(row)
        requireAllAncestors(paths)
        check(
            state(
                paths.source,
                row.input.sourceRelativeName,
                row.input.sourceBytes,
                row.input.sourceSha256,
            ) == QuarantinePathState.EXACT
        ) {
            "Quarantine source changed"
        }
        check(type(paths.destination) == BootstrapPathType.ABSENT) {
            "Quarantine destination occupied"
        }
        os.rename(paths.source.path, paths.destination.path)
    }

    override fun fsyncSourceParent(row: RecoveryQuarantineIntentRow) {
        val paths = paths(row)
        requireAllAncestors(paths)
        syncDirectory(requireNotNull(paths.source.parentFile))
    }

    override fun fsyncDestinationParent(row: RecoveryQuarantineIntentRow) {
        val paths = paths(row)
        requireAllAncestors(paths)
        syncDirectory(paths.objectsRoot)
    }

    private fun visitActive(
        runRoot: File,
        directory: File,
        result: MutableList<RecoveryInventoryArtifact>,
    ) {
        requireDirectory(directory)
        os.list(directory.path).sorted().forEach { childName ->
            requireSingleName(childName)
            val child = File(directory, childName)
            when (val childType = type(child)) {
                BootstrapPathType.DIRECTORY -> visitActive(runRoot, child, result)
                BootstrapPathType.REGULAR -> {
                    val relative =
                        runRoot
                            .toPath()
                            .toAbsolutePath()
                            .normalize()
                            .relativize(child.toPath().toAbsolutePath().normalize())
                            .toString()
                            .replace('\\', '/')
                    result +=
                        RecoveryInventoryArtifact(
                            relative,
                            RecoveryArtifactBytes(
                                relative,
                                readBounded(child, RecoveryArtifactRoleBounds.maximumFor(relative)),
                            ),
                            childType,
                        )
                }
                else ->
                    throw RecoveryUnsafePathException(
                        "Unsafe Recovery inventory object: $childName",
                        RecoveryFailureCategory.CORRUPT_LEAF,
                    )
            }
        }
    }

    private fun state(
        file: File,
        roleName: String,
        expectedBytes: ULong,
        expectedSha256: Sha256Value,
    ): QuarantinePathState =
        when (type(file)) {
            BootstrapPathType.ABSENT -> QuarantinePathState.ABSENT
            BootstrapPathType.REGULAR -> {
                val maximum = RecoveryArtifactRoleBounds.maximumFor(roleName)
                if (expectedBytes > maximum.toULong()) QuarantinePathState.OCCUPIED
                else {
                    val bytes = readExact(file, expectedBytes.toLong(), maximum)
                    if (Sha256Value.calculate(bytes) == expectedSha256) QuarantinePathState.EXACT
                    else QuarantinePathState.OCCUPIED
                }
            }
            else -> QuarantinePathState.UNSAFE
        }

    private fun readBounded(file: File, maximumBytes: Long): ByteArray =
        withDescriptor(
            file,
            OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
        ) { descriptor ->
            val stat = os.fstat(descriptor)
            if (stat.type != BootstrapPathType.REGULAR) {
                throw RecoveryUnsafePathException(
                    "Recovery artifact changed to an unsafe leaf type",
                    RecoveryFailureCategory.CORRUPT_LEAF,
                )
            }
            if (stat.size !in 1..maximumBytes) {
                throw structuralArtifactFailure("Recovery artifact exceeds its role bound")
            }
            readExactOpened(descriptor, stat.size)
        }

    private fun readExact(file: File, expectedBytes: Long, maximumBytes: Long): ByteArray {
        require(expectedBytes in 0..maximumBytes)
        return withDescriptor(
            file,
            OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
        ) { descriptor ->
            val stat = os.fstat(descriptor)
            check(stat.type == BootstrapPathType.REGULAR && stat.size == expectedBytes) {
                "Recovery artifact identity size changed"
            }
            readExactOpened(descriptor, expectedBytes)
        }
    }

    private fun readExactOpened(
        descriptor: RecoveryReconciliationDescriptor,
        expectedBytes: Long,
    ): ByteArray {
        check(expectedBytes in 0..1_048_576L)
        val bytes = ByteArray(expectedBytes.toInt())
        var offset = 0
        while (offset < bytes.size) {
            val read = os.read(descriptor, bytes, offset, bytes.size - offset)
            if (read == 0) throw structuralArtifactFailure("Recovery artifact ended early")
            check(read > 0 && read <= bytes.size - offset) {
                "Recovery artifact read made invalid progress"
            }
            offset += read
        }
        val probe = os.read(descriptor, ByteArray(1), 0, 1)
        if (probe > 0) throw structuralArtifactFailure("Recovery artifact grew during read")
        check(probe == 0) {
            "Recovery artifact returned malformed EOF"
        }
        return bytes
    }

    private fun structuralArtifactFailure(message: String) =
        RecoveryArtifactAccessException(
            RecoveryArtifactPresence.PRESENT,
            true,
            IllegalStateException(message),
        )

    private inline fun <T> withDescriptor(
        file: File,
        flags: Int,
        block: (RecoveryReconciliationDescriptor) -> T,
    ): T {
        val descriptor = os.open(file.path, flags)
        var primary: Throwable? = null
        try {
            return block(descriptor)
        } catch (error: Throwable) {
            primary = error
            throw error
        } finally {
            try {
                os.close(descriptor)
            } catch (close: Throwable) {
                if (primary != null) primary.addSuppressed(close) else throw close
            }
        }
    }

    private fun syncDirectory(directory: File) =
        withDescriptor(directory, OsConstants.O_RDONLY or OsConstants.O_CLOEXEC) { descriptor ->
            check(os.fstat(descriptor).type == BootstrapPathType.DIRECTORY) {
                "Recovery sync target is not a directory"
            }
            os.fsync(descriptor)
        }

    private fun createDirectory(directory: File) {
        when (type(directory)) {
            BootstrapPathType.ABSENT -> {
                os.mkdir(directory.path, 0x1c0)
                requireDirectory(directory)
                syncDirectory(requireNotNull(directory.parentFile))
            }
            BootstrapPathType.DIRECTORY -> Unit
            else ->
                throw RecoveryUnsafePathException(
                    "Unsafe Recovery quarantine directory: ${directory.name}"
                )
        }
    }

    private fun requireAllAncestors(paths: RecoveryReconciliationPaths) {
        requireActiveAncestors(paths)
        requireQuarantineAncestors(paths)
        requireDirectoryChain(paths.activeRunRoot, requireNotNull(paths.source.parentFile))
        requireDirectoryChain(paths.quarantineRunRoot, requireNotNull(paths.destination.parentFile))
    }

    private fun requireActiveAncestors(paths: RecoveryReconciliationPaths) {
        requireActiveBaseAncestors()
        requireDirectory(paths.activeRunRoot)
    }

    private fun requireActiveBaseAncestors() {
        val base = File(root, "poc-recovery")
        listOf(root, base, File(base, "v1"), File(base, "v1/runs")).forEach(::requireDirectory)
    }

    private fun optionalDirectory(file: File): Boolean =
        when (type(file)) {
            BootstrapPathType.ABSENT -> false
            BootstrapPathType.DIRECTORY -> true
            else -> throw RecoveryUnsafePathException("Unsafe Recovery directory: ${file.name}")
        }

    private fun requireQuarantineAncestors(paths: RecoveryReconciliationPaths) {
        val base = File(root, "poc-recovery")
        listOf(
                root,
                base,
                File(base, "v1"),
                File(base, "v1/quarantine"),
                paths.quarantineRunRoot,
                paths.objectsRoot,
            )
            .forEach(::requireDirectory)
    }

    private fun requireDirectoryChain(rootDirectory: File, leafDirectory: File) {
        val rootPath = rootDirectory.toPath().toAbsolutePath().normalize()
        val leafPath = leafDirectory.toPath().toAbsolutePath().normalize()
        check(leafPath.startsWith(rootPath)) { "Recovery path escaped its root" }
        var current = rootDirectory
        requireDirectory(current)
        rootPath.relativize(leafPath).forEach { component ->
            current = File(current, component.toString())
            requireDirectory(current)
        }
    }

    private fun requireDirectory(file: File) {
        if (type(file) != BootstrapPathType.DIRECTORY) {
            throw RecoveryUnsafePathException("Unsafe Recovery directory: ${file.name}")
        }
    }

    private fun type(file: File): BootstrapPathType =
        os.lstat(file.path)?.type ?: BootstrapPathType.ABSENT

    private fun paths(row: RecoveryQuarantineIntentRow) =
        paths(row.input.runId, row.input.sourceRelativeName, row.destinationRelativeName)

    private fun paths(runId: RunId, source: String, destination: String) =
        RecoveryReconciliationPathPolicy.paths(root, runId, source, destination)

    private fun zeroDestination() = "objects/q-${"0".repeat(64)}.bin"

    @Suppress("ComplexCondition")
    private fun requireSingleName(value: String) {
        if (value.isEmpty() || value == "." || value == ".." || '/' in value || '\\' in value) {
            throw RecoveryUnsafePathException("Unsafe Recovery directory entry")
        }
    }
}

private class AndroidRecoveryDescriptor(val value: FileDescriptor) :
    RecoveryReconciliationDescriptor

private object AndroidRecoveryReconciliationOs : RecoveryReconciliationOs {
    override fun lstat(path: String): RecoveryReconciliationStat? =
        try {
            Os.lstat(path).let { stat ->
                RecoveryReconciliationStat(
                    when {
                        OsConstants.S_ISLNK(stat.st_mode) -> BootstrapPathType.SYMLINK
                        OsConstants.S_ISREG(stat.st_mode) -> BootstrapPathType.REGULAR
                        OsConstants.S_ISDIR(stat.st_mode) -> BootstrapPathType.DIRECTORY
                        else -> BootstrapPathType.OTHER
                    },
                    stat.st_size,
                )
            }
        } catch (error: ErrnoException) {
            if (error.errno == OsConstants.ENOENT) null else throw error
        }

    override fun list(path: String): List<String> =
        File(path).list()?.toList() ?: error("Cannot enumerate Recovery directory")

    override fun mkdir(path: String, mode: Int) = Os.mkdir(path, mode)

    override fun open(path: String, flags: Int): RecoveryReconciliationDescriptor =
        AndroidRecoveryDescriptor(Os.open(path, flags, 0))

    override fun fstat(descriptor: RecoveryReconciliationDescriptor): RecoveryReconciliationStat {
        val stat = Os.fstat(descriptor.android())
        return RecoveryReconciliationStat(
            when {
                OsConstants.S_ISREG(stat.st_mode) -> BootstrapPathType.REGULAR
                OsConstants.S_ISDIR(stat.st_mode) -> BootstrapPathType.DIRECTORY
                OsConstants.S_ISLNK(stat.st_mode) -> BootstrapPathType.SYMLINK
                else -> BootstrapPathType.OTHER
            },
            stat.st_size,
        )
    }

    override fun read(
        descriptor: RecoveryReconciliationDescriptor,
        buffer: ByteArray,
        offset: Int,
        count: Int,
    ): Int = Os.read(descriptor.android(), buffer, offset, count)

    override fun rename(source: String, destination: String) = Os.rename(source, destination)

    override fun fsync(descriptor: RecoveryReconciliationDescriptor) =
        Os.fsync(descriptor.android())

    override fun close(descriptor: RecoveryReconciliationDescriptor) =
        Os.close(descriptor.android())

    private fun RecoveryReconciliationDescriptor.android(): FileDescriptor =
        (this as? AndroidRecoveryDescriptor)?.value
            ?: throw IllegalArgumentException("Foreign Recovery descriptor")
}
