package com.monumentogram.dora.poc.search.evidence

internal data class AndroidBuildIdentity(
    val fingerprint: String,
    val manufacturer: String,
    val model: String,
    val brand: String,
    val device: String,
    val product: String,
    val hardware: String,
)

internal object AndroidRuntimeClassifier {
    fun classify(identity: AndroidBuildIdentity): String {
        val fingerprint = identity.fingerprint.lowercase()
        val manufacturer = identity.manufacturer.lowercase()
        val model = identity.model.lowercase()
        val brand = identity.brand.lowercase()
        val device = identity.device.lowercase()
        val product = identity.product.lowercase()
        val hardware = identity.hardware.lowercase()
        val emulatorIndicators =
            listOf(
                fingerprint.startsWith("generic/"),
                fingerprint.contains("/generic"),
                fingerprint.contains("emulator"),
                fingerprint.startsWith("google/sdk_gphone"),
                manufacturer.contains("genymotion"),
                model.contains("google_sdk"),
                model.contains("android sdk built for"),
                model.contains("emulator"),
                model.startsWith("sdk_gphone"),
                device.startsWith("emu"),
                product.startsWith("sdk_gphone"),
                product.contains("emulator"),
                product.contains("vbox86"),
                hardware in setOf("goldfish", "ranchu", "vbox86"),
                brand.startsWith("generic") && device.startsWith("generic"),
            )
        val emulator = emulatorIndicators.any { it }
        return if (emulator) "emulator" else "physical"
    }
}
