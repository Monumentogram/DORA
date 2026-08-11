package com.monumentogram.dora.poc.search

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.poc.search.evidence.AndroidBuildIdentity
import com.monumentogram.dora.poc.search.evidence.AndroidRuntimeClassifier
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StorageUpdateGateV02InstrumentedTest {
    @Test
    fun runAuthorizedPhysicalPairedBuild() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(
            "The formal stage0-v0.2 harness is reachable only through an explicit measured run",
            arguments.getString(RUN_ARGUMENT) == "true",
        )

        check(BuildConfig.GATE_V02_SELECTED_OPTION == StorageUpdateGateV02Contract.SELECTED_OPTION)
        check(BuildConfig.GATE_V02_SHA256.matches(Regex("[0-9a-f]{64}")))
        check(BuildConfig.GATE_V02_EXECUTION_ALLOWED) {
            "Measured execution is withheld by the Project owner"
        }

        val profileId = requireNotNull(arguments.getString(PROFILE_ARGUMENT))
        val freshBuildOrdinal = requireNotNull(arguments.getString(BUILD_ARGUMENT)).toInt()
        check(arguments.getString(COMPILATION_ARGUMENT) == "full_aot_recorded")
        check(arguments.getString(COOLDOWN_ARGUMENT)?.toInt() == 10)
        val config = StorageUpdateGateV02Contract.formalConfig(profileId, freshBuildOrdinal)
        check(BuildConfig.BUILD_TYPE == "benchmark")
        check(!BuildConfig.DEBUG)
        check(runtimeKind() == "physical") {
            "D1-D3 timing evidence requires a physical Android runtime"
        }

        val targetContext = ApplicationProvider.getApplicationContext<Context>()
        val output =
            try {
                val checkpoint = StorageUpdateGateV02Harness(targetContext).run(config)
                StorageUpdateGateV02Writer.write(instrumentation.context, checkpoint).also {
                    check(checkpoint.allCorrect) {
                        "The paired build produced correctness, mapping, normalization, or crash failures"
                    }
                }
            } catch (error: Throwable) {
                StorageUpdateGateV02Writer.writeFailure(instrumentation.context, config, error)
                throw error
            }
        instrumentation.sendStatus(
            0,
            Bundle().apply { putString("pocSearchGateV02Checkpoint", output.absolutePath) },
        )
        println("POC_SEARCH_GATE_V02_CHECKPOINT=${output.absolutePath}")
    }

    private fun runtimeKind(): String =
        AndroidRuntimeClassifier.classify(
            AndroidBuildIdentity(
                fingerprint = Build.FINGERPRINT,
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                brand = Build.BRAND,
                device = Build.DEVICE,
                product = Build.PRODUCT,
                hardware = Build.HARDWARE,
            )
        )

    companion object {
        private const val RUN_ARGUMENT = "pocSearchGateV02"
        private const val PROFILE_ARGUMENT = "pocSearchProfile"
        private const val BUILD_ARGUMENT = "pocSearchFreshBuild"
        private const val COMPILATION_ARGUMENT = "pocSearchCompilationMode"
        private const val COOLDOWN_ARGUMENT = "pocSearchCooldownMinutes"
    }
}
