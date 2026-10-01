package com.monumentogram.dora.audio.persistence.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteStatement
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.SQLiteConnection
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteDatabaseHook
import net.zetetic.database.sqlcipher.SQLiteGlobal
import net.zetetic.database.sqlcipher.SQLiteOpenHelper

private const val DATABASE_SECRET_BYTES = 32

/** One physical connection per helper lifetime; replacement requires a new authenticated open. */
internal class SqlCipherJournalHelperFactory(
    context: Context,
    databaseFile: File,
    passphrase: ByteArray,
) : SupportSQLiteOpenHelper.Factory, AutoCloseable {
    private val databaseFile = databaseFile.canonicalFile
    private val consumed = AtomicBoolean()
    private var helper: GuardedHelper? = null
    private var closed = false

    init {
        require(!context.isDeviceProtectedStorage) { "Credential-encrypted storage required" }
        require(passphrase.size == DATABASE_SECRET_BYTES) {
            "Independent 256-bit database secret required"
        }
        val root = context.noBackupFilesDir.canonicalFile.toPath()
        require(databaseFile.isAbsolute && databaseFile.canonicalFile.toPath().startsWith(root)) {
            "Private no-backup database required"
        }
        require(databaseFile.canonicalFile.toPath() != root) { "Database leaf required" }
        SqlCipherLibrary.initialize()
    }

    // Allocate only after every constructor precondition and library admission succeeds.
    private val secret = passphrase.copyOf()

    @Synchronized
    @Suppress(
        "TooGenericExceptionCaught"
    ) // Every failed construction must retire the owned secret.
    override fun create(
        configuration: SupportSQLiteOpenHelper.Configuration
    ): SupportSQLiteOpenHelper {
        check(!closed && consumed.compareAndSet(false, true)) {
            "Database factory already consumed"
        }
        return try {
            check(configuration.name?.let { File(it).canonicalFile } == databaseFile) {
                "Database identity mismatch"
            }
            GuardedHelper(configuration, secret).also { helper = it }
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        try {
            helper?.close()
        } finally {
            secret.fill(0)
        }
    }
}

private object SqlCipherLibrary {
    private var loaded = false

    @Synchronized
    fun initialize() {
        Logger.setTarget(NoopTarget())
        if (!loaded) {
            System.loadLibrary("sqlcipher")
            SQLiteGlobal.setWALConnectionPoolSize(1)
            loaded = true
        }
        check(SQLiteGlobal.getWALConnectionPoolSize() == 1) { "Database pool policy changed" }
    }
}

private class GuardedHelper(
    private val configuration: SupportSQLiteOpenHelper.Configuration,
    private val secret: ByteArray,
) : SupportSQLiteOpenHelper {
    private val opens = AtomicInteger()
    private val fenced = AtomicBoolean()
    @Volatile private var configured = false
    @Volatile private var closed = false
    private val delegate =
        object :
            SQLiteOpenHelper(
                configuration.context,
                configuration.name,
                secret,
                null,
                configuration.callback.version,
                0,
                { _, _ -> fenced.set(true) },
                object : SQLiteDatabaseHook {
                    override fun preKey(connection: SQLiteConnection) {
                        if (opens.incrementAndGet() != 1 || closed || fenced.get()) {
                            fenced.set(true)
                            error("Replacement database connection rejected")
                        }
                    }

                    override fun postKey(connection: SQLiteConnection) = Unit
                },
                true,
            ) {
            override fun onConfigure(db: SQLiteDatabase) {
                db.setForeignKeyConstraintsEnabled(true)
                db.execSQL("PRAGMA synchronous=FULL")
                db.query("PRAGMA wal_autocheckpoint=0").use { it.moveToFirst() }
                db.execSQL("PRAGMA temp_store=MEMORY")
                verify(db)
                configured = true
                configuration.callback.onConfigure(wrap(db))
                verify(db)
            }

            override fun onCreate(db: SQLiteDatabase) = configuration.callback.onCreate(wrap(db))

            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit =
                error("Unadmitted database migration")

            override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit =
                error("Database downgrade rejected")

            override fun onOpen(db: SQLiteDatabase) {
                verify(db)
                configuration.callback.onOpen(wrap(db))
                verify(db)
            }
        }

    override val databaseName: String?
        get() = configuration.name

    override val writableDatabase: SupportSQLiteDatabase
        get() {
            check(!closed && !fenced.get()) { "Encrypted database fenced" }
            return sanitized {
                val db = delegate.writableDatabase
                check(configured) { "Database configuration missing" }
                verify(db)
                // Verification may wait for the sole physical connection. Never hold
                // a helper monitor here: Room's invalidation transaction needs this
                // getter to check inTransaction before it releases that connection.
                wrap(db)
            }
        }

    // Deliberately never call SQLiteOpenHelper.getReadableDatabase's readonly fallback.
    override val readableDatabase: SupportSQLiteDatabase
        get() = writableDatabase

    override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
        check(enabled && !closed && !fenced.get()) { "WAL policy cannot be changed" }
    }

    @Synchronized
    override fun close() {
        closed = true
        fenced.set(true)
        try {
            sanitized { delegate.close() }
        } finally {
            secret.fill(0)
        }
    }

    private fun verify(db: SQLiteDatabase) {
        check(!closed && !fenced.get() && opens.get() == 1) { "Encrypted database fenced" }
        if (SQLiteGlobal.getWALConnectionPoolSize() != 1) {
            fenced.set(true)
            error("Database pool policy changed")
        }
        for ((name, expected) in REQUIRED) {
            val actual =
                db.query("PRAGMA $name").use { if (it.moveToFirst()) it.getString(0) else null }
            if (actual != expected) {
                fenced.set(true)
                error("Encrypted database connection policy mismatch")
            }
        }
    }

    private fun wrap(db: SQLiteDatabase): SupportSQLiteDatabase =
        Proxy.newProxyInstance(
            SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(SupportSQLiteDatabase::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "toString" -> "EncryptedDatabaseHandle"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "close" -> close()
                "endTransaction" -> sanitized { invoke(db, method, args) }
                else -> {
                    sanitized {
                        verify(db)
                        check(method.name !in RECONFIGURATION) {
                            "Database reconfiguration rejected"
                        }
                        val result = invoke(db, method, args)
                        verify(db)
                        if (result is SupportSQLiteStatement) wrapStatement(db, result) else result
                    }
                }
            }
        } as SupportSQLiteDatabase

    private fun wrapStatement(
        db: SQLiteDatabase,
        statement: SupportSQLiteStatement,
    ): SupportSQLiteStatement =
        Proxy.newProxyInstance(
            SupportSQLiteStatement::class.java.classLoader,
            arrayOf(SupportSQLiteStatement::class.java),
        ) { proxy, method, args ->
            when (method.name) {
                "toString" -> "EncryptedStatementHandle"
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === args?.firstOrNull()
                "close" -> sanitized { invoke(statement, method, args) }
                else -> {
                    sanitized {
                        verify(db)
                        invoke(statement, method, args).also { verify(db) }
                    }
                }
            }
        } as SupportSQLiteStatement

    @Suppress("SpreadOperator") // Java reflection requires forwarding the exact argument vector.
    private fun invoke(target: Any, method: Method, args: Array<out Any?>?): Any? =
        try {
            method.invoke(target, *(args ?: emptyArray()))
        } catch (failure: InvocationTargetException) {
            throw failure.targetException
        }

    private inline fun <T> sanitized(operation: () -> T): T =
        try {
            operation()
        } catch (_: SQLiteConstraintException) {
            throw SQLiteConstraintException("Encrypted journal constraint rejected")
        } catch (_: Exception) {
            fenced.set(true)
            error("Encrypted database operation unavailable")
        }

    private companion object {
        val REQUIRED =
            mapOf(
                "journal_mode" to "wal",
                "synchronous" to "2",
                "foreign_keys" to "1",
                "wal_autocheckpoint" to "0",
                "temp_store" to "2",
            )
        val RECONFIGURATION =
            setOf(
                "enableWriteAheadLogging",
                "disableWriteAheadLogging",
                "setForeignKeyConstraintsEnabled",
                "setLocale",
                "setMaxSqlCacheSize",
                "setMaximumSize",
            )
    }
}
