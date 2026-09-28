plugins {
    id("dora.android.application")
}

android {
    namespace = "com.monumentogram.dora.stage0.ownedcorpus"

    defaultConfig {
        applicationId = "com.monumentogram.dora.stage0.ownedcorpus"
        versionCode = 1
        versionName = "0.1.0-owned-eight"
    }

    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    testImplementation(libs.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    constraints {
        // Match the already verified test graph used by existing Android modules.
        androidTestImplementation("androidx.annotation:annotation:1.9.1")
    }
}
