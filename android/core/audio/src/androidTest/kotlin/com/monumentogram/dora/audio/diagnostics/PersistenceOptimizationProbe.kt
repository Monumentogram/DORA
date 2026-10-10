package com.monumentogram.dora.audio.diagnostics

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import androidx.sqlite.db.SupportSQLiteStatement
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

/** C3 observers delegate every real call, never retain SQL values or modify a result. */
internal class PersistenceOptimizationProbe {
    private val owner = Thread.currentThread()
    private var active = false
    private var depth = 0
    private var phase: String? = null
    private var successful = false
    var fullCatalogLoads = 0
        private set

    var candidateFsyncCount = 0
        private set

    var candidateParentFsyncCount = 0
        private set

    val transactionOrder = mutableListOf<String>()

    fun start() {
        check(!active && depth == 0)
        fullCatalogLoads = 0
        candidateFsyncCount = 0
        candidateParentFsyncCount = 0
        transactionOrder.clear()
        active = true
    }

    fun finish() {
        check(depth == 0)
        active = false
    }

    fun factory(real: SupportSQLiteOpenHelper.Factory) = SupportSQLiteOpenHelper.Factory { config ->
        val helper = real.create(config)
        object : SupportSQLiteOpenHelper by helper {
            override val writableDatabase: SupportSQLiteDatabase
                get() = wrap(helper.writableDatabase, SupportSQLiteDatabase::class.java, "db")

            override val readableDatabase: SupportSQLiteDatabase
                get() = wrap(helper.readableDatabase, SupportSQLiteDatabase::class.java, "db")
        }
    }

    @Suppress("UNCHECKED_CAST", "SpreadOperator", "LongMethod", "CyclomaticComplexMethod")
    fun <T> wrap(real: T, type: Class<T>, label: String, writePhase: String? = null): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            val measured = active && Thread.currentThread() === owner
            val name = method.name
            val sql =
                if (label == "db" && name in setOf("query", "compileStatement")) {
                    when (val value = args?.firstOrNull()) {
                        is SupportSQLiteQuery -> value.sql
                        is String -> value
                        else -> null
                    }
                } else null
            if (measured && label == "db" && name.startsWith("beginTransaction")) {
                if (depth == 0) {
                    phase = null
                    successful = false
                }
                depth++
            }
            val result =
                try {
                    method.invoke(real, *(args ?: emptyArray()))
                } catch (error: InvocationTargetException) {
                    throw error.targetException
                }
            if (measured) {
                if (
                    label == "db" &&
                        name == "query" &&
                        sql == "SELECT * FROM unit_claim WHERE assetId=? ORDER BY ordinal"
                ) {
                    fullCatalogLoads++
                }
                if (label == "statement" && name.startsWith("execute") && writePhase != null) {
                    check(phase == null || phase == writePhase)
                    phase = writePhase
                }
                if (label == "db" && name == "setTransactionSuccessful" && depth == 1) {
                    successful = true
                }
                if (label == "db" && name == "endTransaction") {
                    depth--
                    check(depth >= 0)
                    if (depth == 0 && successful) phase?.let(transactionOrder::add)
                }
                if (label == "candidate" && name == "fsync") candidateFsyncCount++
                if (label == "candidate" && name == "fsyncParent") candidateParentFsyncCount++
            }
            if (result is SupportSQLiteStatement) {
                wrap(result, SupportSQLiteStatement::class.java, "statement", classify(sql))
            } else result
        } as T

    private fun classify(sql: String?): String? {
        val normalized = sql?.replace("`", "")?.uppercase() ?: return null
        return when {
            normalized.startsWith("INSERT") && normalized.contains("INTO UNIT_CLAIM ") ->
                "reservation"
            normalized.startsWith("INSERT") && normalized.contains("INTO BOOTSTRAP ") -> "bootstrap"
            normalized.startsWith("INSERT") && normalized.contains("INTO MANIFEST ") ->
                "publication"
            normalized.startsWith("UPDATE") && normalized.contains("UNIT_CLAIM SET ") ->
                "catalogCommit"
            else -> null
        }
    }
}
