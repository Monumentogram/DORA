plugins {
    id("dora.android.application")
    alias(libs.plugins.compose.compiler)
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
    namespace = "com.monumentogram.dora.poc.capture"

    defaultConfig {
        applicationId = "com.monumentogram.dora.poc.capture"
        versionCode = 1
        versionName = "0.1.0-poc-capture-001"
        buildConfigField("String", "GIT_COMMIT", "\"${pocCommit.get()}\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit4)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
