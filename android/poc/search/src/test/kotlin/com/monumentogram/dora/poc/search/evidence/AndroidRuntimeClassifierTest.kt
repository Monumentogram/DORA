package com.monumentogram.dora.poc.search.evidence

import org.junit.Assert.assertEquals
import org.junit.Test

class AndroidRuntimeClassifierTest {
    @Test
    fun `classifies Google API 36 sdk gphone as emulator`() {
        assertEquals(
            "emulator",
            AndroidRuntimeClassifier.classify(
                AndroidBuildIdentity(
                    fingerprint =
                        "google/sdk_gphone64_x86_64/emu64xa:16/BE2A.250530.026.F3/13894323:userdebug/dev-keys",
                    manufacturer = "Google",
                    model = "sdk_gphone64_x86_64",
                    brand = "google",
                    device = "emu64xa",
                    product = "sdk_gphone64_x86_64",
                    hardware = "ranchu",
                )
            ),
        )
    }

    @Test
    fun `classifies generic AOSP image as emulator`() {
        assertEquals(
            "emulator",
            AndroidRuntimeClassifier.classify(
                AndroidBuildIdentity(
                    fingerprint = "generic/sdk/generic:36/test-keys",
                    manufacturer = "unknown",
                    model = "Android SDK built for x86_64",
                    brand = "generic",
                    device = "generic_x86_64",
                    product = "sdk_x86_64",
                    hardware = "goldfish",
                )
            ),
        )
    }

    @Test
    fun `does not relabel a physical Google Pixel`() {
        assertEquals(
            "physical",
            AndroidRuntimeClassifier.classify(
                AndroidBuildIdentity(
                    fingerprint =
                        "google/komodo/komodo:16/BP2A.250605.031.A2/13580840:user/release-keys",
                    manufacturer = "Google",
                    model = "Pixel 9 Pro XL",
                    brand = "google",
                    device = "komodo",
                    product = "komodo",
                    hardware = "komodo",
                )
            ),
        )
    }

    @Test
    fun `does not relabel a physical Samsung device`() {
        assertEquals(
            "physical",
            AndroidRuntimeClassifier.classify(
                AndroidBuildIdentity(
                    fingerprint =
                        "samsung/e3qxeea/e3q:15/AP3A.240905.015.A2/S928BXXU4:user/release-keys",
                    manufacturer = "samsung",
                    model = "SM-S928B",
                    brand = "samsung",
                    device = "e3q",
                    product = "e3qxeea",
                    hardware = "qcom",
                )
            ),
        )
    }
}
