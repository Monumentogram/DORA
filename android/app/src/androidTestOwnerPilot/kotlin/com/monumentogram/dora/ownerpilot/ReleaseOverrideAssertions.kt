package com.monumentogram.dora.ownerpilot

import java.io.File
import org.junit.Assert.assertEquals

@Suppress("NestedBlockDepth", "LongMethod", "MagicNumber") // Exhaustive release denial matrix.
internal fun assertReleaseOverridesDenyAllInputs(context: android.content.Context) {
    val prefix = "com.monumentogram.dora.audio.persistence.auth."
    val device = Class.forName(prefix + "DevelopmentDeviceSecurityOverride")
    val app = Class.forName(prefix + "DevelopmentAppLockOverride")
    val sessionClass = Class.forName(prefix + "AppLockSession")
    val constructor = sessionClass.declaredConstructors.single { it.parameterCount == 2 }
    val session = constructor.newInstance({ android.os.SystemClock.elapsedRealtime() }, { true })
    sessionClass.getDeclaredMethod("resume").invoke(session)
    val allowed =
        device.getDeclaredMethod(
            "allowed",
            String::class.java,
            Boolean::class.javaPrimitiveType,
            File::class.java,
        )
    val unlock =
        app.getDeclaredMethod(
            "unlock",
            sessionClass,
            String::class.java,
            Boolean::class.javaPrimitiveType,
            kotlin.jvm.functions.Function0::class.java,
        )
    val markers = listOf("development-device-no-lock", "development-app-lock-no-prompt")
    val root = File(context.cacheDir, "release-override-negative").apply { mkdirs() }
    try {
        for (bits in 0..3) {
            markers.forEachIndexed { index, name ->
                val marker = File(root, name)
                if (bits and (1 shl index) != 0) marker.writeText("") else marker.delete()
            }
            for (name in
                listOf(
                    "com.monumentogram.dora",
                    "com.monumentogram.dora.debug",
                    "com.monumentogram.dora.alpha.test",
                    "com.monumentogram.dora.alpha.pilot",
                    "other",
                )) {
                for (debuggable in listOf(false, true)) {
                    assertEquals(
                        false,
                        allowed.invoke(
                            device.getField("INSTANCE").get(null),
                            name,
                            debuggable,
                            root,
                        ),
                    )
                    val forbiddenProbe: () -> Boolean = {
                        error("Release must never inspect marker")
                    }
                    assertEquals(
                        false,
                        unlock.invoke(
                            app.getField("INSTANCE").get(null),
                            session,
                            name,
                            debuggable,
                            forbiddenProbe,
                        ),
                    )
                    assertEquals(
                        false,
                        sessionClass.getDeclaredMethod("isUnlocked").invoke(session),
                    )
                }
            }
        }
    } finally {
        markers.forEach { File(root, it).delete() }
        root.delete()
    }
}
