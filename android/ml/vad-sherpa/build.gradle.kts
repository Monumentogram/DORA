plugins {
    id("dora.android.library")
}

android {
    namespace = "com.monumentogram.dora.vad.sherpa"
    defaultConfig { consumerProguardFiles("consumer-rules.pro") }
}

dependencies {
    implementation(project(":ml:vad-api"))
    testImplementation(libs.junit4)
}
