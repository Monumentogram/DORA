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
        buildConfigField("String", "POC_VERSION", "\"0.1.0-poc-search-001\"")
    }

    buildFeatures {
        buildConfig = true
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
