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
val privateAsrDirectory = providers.gradleProperty("doraPrivateAsrDir")
val privateAsrTestDirectory = providers.gradleProperty("doraPrivateAsrTestDir")

check(!(privateAsrDirectory.isPresent && privateAsrTestDirectory.isPresent)) {
    "Product and emulator ASR artifacts are mutually exclusive"
}

check(!privateAsrTestDirectory.isPresent || !privateVadDirectory.isPresent) {
    "The x86 emulator ASR path cannot include the arm64 private VAD"
}

check(
    !(privateAsrDirectory.isPresent || privateAsrTestDirectory.isPresent) ||
        providers.environmentVariable("GITHUB_ACTIONS").orNull != "true"
) {
    "Private ASR packaging is restricted to controlled local builds"
}

check(
    !privateAsrTestDirectory.isPresent ||
        providers.gradleProperty("doraTestBuildType").orElse("debug").get() == "debug"
) {
    "The unadmitted ASR test runtime is debug-only"
}

val privateAsr =
    if (privateAsrDirectory.isPresent || privateAsrTestDirectory.isPresent)
        tasks.register<VerifyPrivateAsrTask>("verifyPrivateAsr") {
            val test = privateAsrTestDirectory.isPresent
            val directory =
                file(if (test) privateAsrTestDirectory.get() else privateAsrDirectory.get())
            emulatorTest.set(test)
            nativeDirectory.set(directory)
            receipt.set(
                directory.parentFile.resolve(
                    if (test) "test-build-receipt.json" else "build-receipt.json"
                )
            )
            retainedNotices.set(directory.parentFile.resolve("notices"))
            projectNotices.set(rootProject.file("../docs/evidence/stage9-asr"))
            adapterSource.set(file("src/main/cpp/whisper_jni.cpp"))
            cmakeSource.set(file("src/main/cpp/CMakeLists.txt"))
            outputDirectory.set(
                layout.buildDirectory.dir(if (test) "private-asr-test" else "private-asr")
            )
        }
    else null

if (privateAsr != null) tasks.configureEach { if (name == "preBuild") dependsOn(privateAsr) }

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
    if (privateAsr != null) {
        val test = privateAsrTestDirectory.isPresent
        val generated = layout.buildDirectory.dir(if (test) "private-asr-test" else "private-asr")
        val sourceSet = sourceSets.getByName(if (test) "debug" else "main")
        sourceSet.jniLibs.directories.add(generated.get().dir("jniLibs").asFile.absolutePath)
        sourceSet.assets.directories.add(generated.get().dir("assets").asFile.absolutePath)
        defaultConfig.ndk.abiFilters.add(if (test) "x86_64" else "arm64-v8a")
    }
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
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        create("alphaTest") {
            initWith(getByName("release"))
            applicationIdSuffix = ".alpha.test"
            matchingFallbacks += "release"
            if (providers.gradleProperty("doraAlphaTestInstrumentationSigning").orNull == "true") {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
        create("ownerPilot") {
            initWith(getByName("release"))
            applicationIdSuffix = ".alpha.pilot"
            matchingFallbacks += "release"
            if (providers.gradleProperty("doraOwnerPilotInstrumentationSigning").orNull == "true") {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
        create("ownerPreview") {
            initWith(getByName("release"))
            applicationIdSuffix = ".alpha.preview"
            matchingFallbacks += "release"
            if (
                providers.gradleProperty("doraOwnerPreviewInstrumentationSigning").orNull == "true"
            ) {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    sourceSets
        .getByName("androidTestOwnerPreview")
        .kotlin
        .directories
        .add("src/androidTestOwnerPilot/kotlin")

    testBuildType =
        providers.gradleProperty("doraTestBuildType").orElse("debug").get().also {
            check(it in setOf("debug", "alphaTest", "ownerPilot", "ownerPreview")) {
                "Unsupported instrumentation build type"
            }
        }

    buildFeatures {
        compose = true
        buildConfig = false
        resValues = true
    }

    packaging {
        jniLibs.keepDebugSymbols +=
            setOf(
                "**/libdora-whisper-jni.so",
                "**/libwhisper.so",
                "**/libggml.so",
                "**/libggml-base.so",
                "**/libggml-cpu.so",
                "**/libc++_shared.so",
            )
        jniLibs.keepDebugSymbols += setOf("**/libonnxruntime.so", "**/libsherpa-onnx-jni.so")
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

androidComponents {
    beforeVariants(selector().all()) {
        if (privateAsrTestDirectory.isPresent && it.buildType != "debug") it.enable = false
    }
    onVariants(selector().all()) { variant ->
        // Fail closed if split outputs ever acquire independent version identities.
        val output = variant.outputs.single()
        val ownerPilot = variant.buildType == "ownerPilot"
        val ownerPreview = variant.buildType == "ownerPreview"
        if (ownerPreview) {
            output.versionCode.set(7)
            output.versionName.set("0.1.0-owner-preview.7")
        }
        if (ownerPilot) {
            output.versionCode.set(4)
            output.versionName.set("0.1.0-owner-alpha.4")
        }
        val prefix =
            if (ownerPreview) "DORA Owner Preview"
            else if (ownerPilot) "DORA Owner Alpha" else "DORA Alpha"
        variant.resValues.put(
            variant.makeResValueKey("string", "alpha_version_label"),
            output.versionName.zip(output.versionCode) { name, code ->
                com.android.build.api.variant.ResValue(
                    "$prefix $name ($code)",
                    "Final APK identity",
                )
            },
        )
    }
    beforeVariants(selector().withBuildType("ownerPreview")) {
        it.hostTests.getValue(com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE).enable =
            true
    }
    beforeVariants(selector().withBuildType("ownerPilot")) {
        it.hostTests.getValue(com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE).enable =
            true
    }
    beforeVariants(selector().withBuildType("alphaTest")) {
        it.hostTests.getValue(com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE).enable =
            true
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
