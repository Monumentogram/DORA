plugins {
    id("dora.android.library")
}

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    namespace = "com.monumentogram.dora.vad"
    sourceSets
        .getByName("test")
        .resources
        .srcDir(rootProject.file("../docs/evidence/vad-8.4-runtime"))
}

dependencies {
    testImplementation(libs.junit4)
}
