plugins {
    id("dora.android.library")
}

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    namespace = "com.monumentogram.dora.audio"

    // Compile the accepted implementation in place; never fork the Recovery engine.
    sourceSets
        .getByName("main")
        .kotlin
        .directories
        .add(rootProject.file("poc/recovery/src/main/kotlin").absolutePath)
}

dependencies {
    implementation(project(":core:model"))
    implementation("com.google.crypto.tink:tink-android:1.23.0") {
        exclude(group = "com.google.code.findbugs", module = "jsr305")
    }
    testImplementation(libs.junit4)
}
