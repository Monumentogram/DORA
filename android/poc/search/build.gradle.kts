plugins {
    id("dora.android.library")
    alias(libs.plugins.ksp)
}

val pocCommit =
    providers
        .exec {
            workingDir = rootDir.parentFile
            commandLine("git", "rev-parse", "HEAD")
        }
        .standardOutput
        .asText
        .map { it.trim() }

android {
    namespace = "com.monumentogram.dora.poc.search"

    defaultConfig {
        buildConfigField("String", "GIT_COMMIT", "\"${pocCommit.get()}\"")
        buildConfigField("String", "POC_VERSION", "\"0.2.0-poc-search-001\"")
        buildConfigField("String", "GATE_V02_SELECTED_OPTION", "\"B\"")
        buildConfigField(
            "String",
            "GATE_V02_SHA256",
            "\"7898293eae8d896b72b04dddc222a0cf2e726afd41375ae8a8402b996ef70858\"",
        )
        buildConfigField("boolean", "GATE_V02_EXECUTION_ALLOWED", "false")
        buildConfigField(
            "String",
            "SYSTEM_IMAGE_PACKAGE",
            "\"system-images;android-36;google_apis;x86_64\"",
        )
        buildConfigField("int", "SYSTEM_IMAGE_REVISION", "7")
        buildConfigField(
            "String",
            "SYSTEM_IMAGE_ARCHIVE_SHA256",
            "\"b1bb0769d0bed7698e61f203d7dc9bf6e7c37cd01a39d0d8788a11186bc78160\"",
        )
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        create("benchmark") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
        }
    }

    testBuildType =
        if (providers.gradleProperty("pocSearchGateV02BenchmarkBuild").orNull == "true") {
            "benchmark"
        } else {
            "debug"
        }

    sourceSets {
        getByName("androidTest")
            .assets
            .directories
            .add(rootProject.file("../docs/evidence/poc-search-001").absolutePath)
    }
}

ksp {
    arg("room.generateKotlin", "true")
    arg("room.schemaLocation", file("schemas").path)
}

dependencies {
    implementation(libs.androidx.room.runtime)

    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit4)

    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit4)
}
