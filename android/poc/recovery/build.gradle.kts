plugins {
    id("dora.android.application")
}

android {
    namespace = "com.monumentogram.dora.poc.recovery"

    defaultConfig {
        applicationId = "com.monumentogram.dora.poc.recovery"
        versionCode = 1
        versionName = "0.1.0-poc-recovery-i1"
    }

    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    testImplementation(libs.junit4)
}
