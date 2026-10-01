package com.monumentogram.dora.audio.persistence

import android.system.ErrnoException
import android.system.OsConstants
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.monumentogram.dora.audio.AudioIntent
import com.monumentogram.dora.audio.EncryptedAudioCatalog
import com.monumentogram.dora.audio.StoredAudioAsset
import com.monumentogram.dora.poc.recovery.candidate.CandidateWriteHandle
import com.monumentogram.dora.poc.recovery.candidate.RecoveryCandidateStorage
import com.monumentogram.dora.poc.recovery.candidate.RecoveryMicrofileJournal
import com.monumentogram.dora.poc.recovery.candidate.RecoveryMicrofileTransaction
import com.monumentogram.dora.poc.recovery.contract.RunId
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

internal enum class WriteFault {
    OPEN,
    WRITE,
    FILE_SYNC,
    RENAME_BEFORE,
    RENAME_AFTER,
    PARENT_SYNC,
}

internal class CandidateFault(
    private val real: RecoveryCandidateStorage,
    private val phase: WriteFault,
) : RecoveryCandidateStorage by real {
    var fired = false
    private var selected: CandidateWriteHandle? = null
    private var wrote = false

    private fun fail() {
        fired = true
        throw ErrnoException("SYNTHETIC_STORAGE", OsConstants.ENOSPC)
    }

    override fun openExclusiveTemp(
        runId: RunId,
        temporaryRelativeName: String,
    ): CandidateWriteHandle {
        val audio = temporaryRelativeName.startsWith("units/")
        if (audio && phase == WriteFault.OPEN) fail()
        return real.openExclusiveTemp(runId, temporaryRelativeName).also {
            if (audio) selected = it
        }
    }

    override fun write(
        handle: CandidateWriteHandle,
        bytes: ByteArray,
        offset: Int,
        count: Int,
    ): Int {
        if (handle === selected && phase == WriteFault.WRITE) {
            if (wrote) fail()
            wrote = true
            return real.write(handle, bytes, offset, minOf(count, 8))
        }
        return real.write(handle, bytes, offset, count)
    }

    override fun fsync(handle: CandidateWriteHandle) {
        if (handle === selected && phase == WriteFault.FILE_SYNC) fail()
        real.fsync(handle)
    }

    override fun renameTempToFinal(
        runId: RunId,
        temporaryRelativeName: String,
        finalRelativeName: String,
    ) {
        if (finalRelativeName.startsWith("units/") && phase == WriteFault.RENAME_BEFORE) fail()
        real.renameTempToFinal(runId, temporaryRelativeName, finalRelativeName)
        if (finalRelativeName.startsWith("units/") && phase == WriteFault.RENAME_AFTER) fail()
    }

    override fun fsyncParent(runId: RunId, finalRelativeName: String) {
        if (finalRelativeName.startsWith("units/") && phase == WriteFault.PARENT_SYNC) fail()
        real.fsyncParent(runId, finalRelativeName)
    }
}

/** Semantic catalog/journal scopes select the actual SQLCipher endTransaction, never an ordinal. */
internal class TransactionFault(val phase: String, val after: Boolean) {
    private val current = ThreadLocal<String?>()
    private val depth = ThreadLocal.withInitial { 0 }
    var fired = false

    fun arm(selected: String) {
        current.set(selected)
    }

    fun <T> during(selected: String, block: () -> T): T {
        current.set(selected)
        return try {
            block()
        } finally {
            current.remove()
        }
    }

    fun catalog(real: EncryptedAudioCatalog) =
        object : EncryptedAudioCatalog by real {
            override fun reserve(expected: StoredAudioAsset, intent: AudioIntent): Boolean =
                during(if (intent is AudioIntent.Append) "APPEND_INTENT" else "FINALIZE_INTENT") {
                    real.reserve(expected, intent)
                }

            override fun compareAndSet(
                expected: StoredAudioAsset,
                next: StoredAudioAsset,
            ): Boolean =
                during(
                    if (expected.pending is AudioIntent.Append) "APPEND_CAS" else "FINALIZE_CAS"
                ) {
                    real.compareAndSet(expected, next)
                }
        }

    fun journal(real: RecoveryMicrofileJournal) =
        object : RecoveryMicrofileJournal by real {
            override fun beginNonExclusive(): RecoveryMicrofileTransaction {
                val transaction = real.beginNonExclusive()
                return object : RecoveryMicrofileTransaction by transaction {
                    override fun end() = during("PUBLICATION") { transaction.end() }
                }
            }
        }

    fun factory(real: SupportSQLiteOpenHelper.Factory) =
        SupportSQLiteOpenHelper.Factory { configuration ->
            val helper = real.create(configuration)
            fun wrapped(database: SupportSQLiteDatabase) =
                Proxy.newProxyInstance(
                    SupportSQLiteDatabase::class.java.classLoader,
                    arrayOf(SupportSQLiteDatabase::class.java),
                ) { _, method, args ->
                    val trigger =
                        method.name == "endTransaction" &&
                            depth.get() == 1 &&
                            current.get() == phase &&
                            !fired
                    if (trigger && !after) {
                        fired = true
                        error("SYNTHETIC_END_BEFORE")
                    }
                    val result =
                        try {
                            method.invoke(database, *(args ?: emptyArray()))
                        } catch (error: InvocationTargetException) {
                            throw error.targetException
                        }
                    if (method.name.startsWith("beginTransaction"))
                        depth.set(checkNotNull(depth.get()) + 1)
                    if (method.name == "endTransaction") depth.set(checkNotNull(depth.get()) - 1)
                    if (trigger) {
                        fired = true
                        error("SYNTHETIC_END_AFTER")
                    }
                    result
                } as SupportSQLiteDatabase
            object : SupportSQLiteOpenHelper by helper {
                override val writableDatabase
                    get() = wrapped(helper.writableDatabase)

                override val readableDatabase
                    get() = wrapped(helper.readableDatabase)
            }
        }

    fun dependencies() =
        EncryptedAudioVault.Dependencies(
            catalog = ::catalog,
            helperFactory = ::factory,
            microfileJournal = ::journal,
        )
}

/** The default encrypted connection is retained; only operational query failure is injected. */
internal class DatabaseProbe {
    lateinit var database: SupportSQLiteDatabase
    var failQueries = false

    fun factory(real: SupportSQLiteOpenHelper.Factory) =
        SupportSQLiteOpenHelper.Factory { configuration ->
            val helper = real.create(configuration)
            fun wrapped(value: SupportSQLiteDatabase): SupportSQLiteDatabase {
                database = value
                return Proxy.newProxyInstance(
                    SupportSQLiteDatabase::class.java.classLoader,
                    arrayOf(SupportSQLiteDatabase::class.java),
                ) { _, method, args ->
                    if (failQueries && method.name == "query") error("SYNTHETIC_DB_PATH_CANARY")
                    try {
                        method.invoke(value, *(args ?: emptyArray()))
                    } catch (error: InvocationTargetException) {
                        throw error.targetException
                    }
                } as SupportSQLiteDatabase
            }
            object : SupportSQLiteOpenHelper by helper {
                override val writableDatabase
                    get() = wrapped(helper.writableDatabase)

                override val readableDatabase
                    get() = wrapped(helper.readableDatabase)
            }
        }
}
