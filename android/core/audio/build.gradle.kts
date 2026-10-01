plugins {
    id("dora.android.library")
    alias(libs.plugins.ksp)
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

ksp {
    arg("room.generateKotlin", "true")
    arg("room.schemaLocation", file("schemas").path)
}

dependencies {
    implementation(project(":core:model"))
    implementation("com.google.crypto.tink:tink-android:1.23.0") {
        exclude(group = "com.google.code.findbugs", module = "jsr305")
    }
    implementation("com.monumentogram.dora.thirdparty:sqlcipher-android:4.17.0-dora.1")
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.junit4)
    testImplementation(libs.junit4)
}
