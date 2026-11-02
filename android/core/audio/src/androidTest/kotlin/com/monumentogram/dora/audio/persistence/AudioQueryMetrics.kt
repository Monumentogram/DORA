package com.monumentogram.dora.audio.persistence

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteQuery
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicLong

/** Test-only operation counters, recording SQL categories and row counts, never bound values. */
internal class AudioQueryMetrics {
    val queries = AtomicLong()
    val catalogBulkQueries = AtomicLong()
    val catalogBulkRows = AtomicLong()
    val deletionBulkQueries = AtomicLong()
    val deletionRows = AtomicLong()
    val transactions = AtomicLong()

    fun factory(delegate: SupportSQLiteOpenHelper.Factory) =
        SupportSQLiteOpenHelper.Factory { config ->
            val helper = delegate.create(config)
            fun wrapped(database: SupportSQLiteDatabase): SupportSQLiteDatabase =
                Proxy.newProxyInstance(
                    SupportSQLiteDatabase::class.java.classLoader,
                    arrayOf(SupportSQLiteDatabase::class.java),
                ) { _, method, args ->
                    val result =
                        try {
                            method.invoke(database, *(args ?: emptyArray()))
                        } catch (error: InvocationTargetException) {
                            throw error.targetException
                        }
                    if (
                        method.name == "beginTransaction" ||
                            method.name == "beginTransactionNonExclusive"
                    )
                        transactions.incrementAndGet()
                    if (method.name == "query") {
                        queries.incrementAndGet()
                        val sql =
                            when (val query = args?.firstOrNull()) {
                                is String -> query
                                is SupportSQLiteQuery -> query.sql
                                else -> ""
                            }
                        if (isCatalogBulk(sql)) {
                            catalogBulkQueries.incrementAndGet()
                            catalogBulkRows.addAndGet((result as Cursor).count.toLong())
                        }
                        if (sql.startsWith("SELECT * FROM deletion_target WHERE assetId=")) {
                            deletionRows.addAndGet((result as Cursor).count.toLong())
                            if (sql.contains("ORDER BY") && !sql.contains("LIMIT"))
                                deletionBulkQueries.incrementAndGet()
                        }
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

    private fun isCatalogBulk(sql: String): Boolean {
        val claims =
            sql.startsWith("SELECT * FROM unit_claim WHERE assetId=") &&
                sql.endsWith("ORDER BY ordinal")
        val manifests = sql.startsWith("SELECT manifest.* FROM manifest JOIN")
        val microfiles = sql.startsWith("SELECT microfile.* FROM microfile JOIN")
        val physical =
            sql.startsWith("SELECT * FROM physical_source WHERE assetId=") &&
                !sql.contains("physicalId=")
        return claims || manifests || microfiles || physical
    }
}
