plugins {
    id("dora.android.library")
}

android {
    namespace = "com.monumentogram.dora.testing"
}

dependencies {
    implementation(project(":core:common"))
    implementation(libs.junit4)

    testImplementation(libs.junit4)
}
