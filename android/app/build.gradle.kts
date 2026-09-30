plugins {
    id("dora.android.application")
    alias(libs.plugins.compose.compiler)
}

val alphaUpgradeTest =
    providers.gradleProperty("doraAlphaUpgradeTest").map(String::toBoolean).orElse(false)

android {
    namespace = "com.monumentogram.dora"

    defaultConfig {
        applicationId = "com.monumentogram.dora"
        versionCode = if (alphaUpgradeTest.get()) 3 else 2
        versionName = if (alphaUpgradeTest.get()) "0.1.0-alpha.1-upgrade-test" else "0.1.0-alpha.1"
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
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
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
