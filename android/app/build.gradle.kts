import java.util.Properties

plugins {
    id("dora.android.application")
    alias(libs.plugins.compose.compiler)
}

val alphaIdentity =
    Properties().apply {
        load(
            providers
                .fileContents(rootProject.layout.projectDirectory.file("alpha-release.properties"))
                .asText
                .get()
                .reader()
        )
    }

check(!providers.gradleProperty("doraAlphaUpgradeTest").isPresent) {
    "Code 3 is a historical non-product probe; use the immutable 7.1 source only."
}

// Explicit controlled-local packaging. Normal CI has neither input nor custody credentials.
val privateVadDirectory = providers.gradleProperty("doraPrivateVadDir")

check(
    !privateVadDirectory.isPresent ||
        providers.environmentVariable("GITHUB_ACTIONS").orNull != "true"
) {
    "Private VAD packaging is restricted to controlled local builds"
}

val privateVad =
    if (privateVadDirectory.isPresent)
        tasks.register<VerifyPrivateVadTask>("verifyPrivateVad") {
            val directory = file(privateVadDirectory.get())
            aar.set(directory.resolve("sherpa-onnx-vad-1.13.8-dora.1-arm64.aar"))
            model.set(directory.resolve("silero.onnx"))
            modelNotice.set(rootProject.file("../docs/evidence/vad-8.4-runtime/silero-MIT.txt"))
            outputDirectory.set(layout.buildDirectory.dir("private-vad"))
        }
    else null

if (privateVad != null) {
    dependencies.add(
        "runtimeOnly",
        files(privateVad.map { it.outputDirectory.file("runtime.aar").get().asFile })
            .builtBy(privateVad),
    )
    tasks.configureEach { if (name == "preBuild") dependsOn(privateVad) }
}

android {
    namespace = "com.monumentogram.dora"
    if (privateVad != null) {
        sourceSets
            .getByName("main")
            .assets
            .directories
            .add(layout.buildDirectory.dir("private-vad/assets").get().asFile.absolutePath)
        defaultConfig.ndk.abiFilters.add("arm64-v8a")
    }

    defaultConfig {
        applicationId = "com.monumentogram.dora"
        versionCode = alphaIdentity.getProperty("versionCode").toInt()
        versionName = alphaIdentity.getProperty("versionName")
        resValue("string", "alpha_version_label", "DORA Alpha $versionName ($versionCode)")
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = false
        resValues = true
    }

    packaging {
        jniLibs.keepDebugSymbols += setOf("**/libonnxruntime.so", "**/libsherpa-onnx-jni.so")
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(project(":ml:vad-api"))
    implementation(project(":ml:vad-sherpa"))
    implementation(project(":core:audio"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit4)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
