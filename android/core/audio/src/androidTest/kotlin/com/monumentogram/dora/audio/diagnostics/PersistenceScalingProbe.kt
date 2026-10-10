package com.monumentogram.dora.audio.diagnostics

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.SupportSQLiteStatement
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import org.json.JSONObject

/** Test-only boundary counters. Never record SQL, arguments, keys or source identities. */
internal class PersistenceScalingProbe {
    private val owner = Thread.currentThread()
    private var active = false
    private val counts = linkedMapOf<String, Long>()
    private val nanos = linkedMapOf<String, Long>()

    fun start() {
        counts.clear()
        nanos.clear()
        active = true
    }

    fun finish(): JSONObject {
        active = false
        return JSONObject()
            .put("counts", JSONObject(counts as Map<*, *>))
            .put("nanos", JSONObject(nanos as Map<*, *>))
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

    @Suppress("UNCHECKED_CAST", "SpreadOperator")
    fun <T> wrap(real: T, type: Class<T>, label: String): T =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            val measured = active && Thread.currentThread() === owner
            val begin = if (measured) System.nanoTime() else 0L
            val result =
                try {
                    method.invoke(real, *(args ?: emptyArray()))
                } catch (error: InvocationTargetException) {
                    throw error.targetException
                } finally {
                    if (measured) {
                        val key = "$label.${method.name}"
                        counts[key] = (counts[key] ?: 0L) + 1L
                        nanos[key] = (nanos[key] ?: 0L) + System.nanoTime() - begin
                    }
                }
            if (result is SupportSQLiteStatement)
                wrap(result, SupportSQLiteStatement::class.java, "statement")
            else result
        } as T
}
