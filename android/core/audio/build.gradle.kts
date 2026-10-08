plugins {
    id("dora.android.library")
    alias(libs.plugins.ksp)
}

extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    namespace = "com.monumentogram.dora.audio"
    sourceSets.getByName("androidTest").assets.srcDir("schemas")

    val privatePolicyPin = providers.gradleProperty("doraProtectedPolicyPinDir")
    if (privatePolicyPin.isPresent) {
        check(providers.environmentVariable("GITHUB_ACTIONS").orNull != "true") {
            "Private diagnostic packaging is local only"
        }
        val directory = file(privatePolicyPin.get())
        check(directory.listFiles()?.map { it.name } == listOf("dora-protected-policy.sha256"))
        check(
            directory
                .resolve("dora-protected-policy.sha256")
                .readText()
                .trim()
                .matches(Regex("[a-f0-9]{64}"))
        )
        sourceSets.getByName("debug").assets.srcDir(directory)
    }

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

// Both compiled implementations must execute the no-device-lock negative tests.
extensions.configure<com.android.build.api.variant.LibraryAndroidComponentsExtension> {
    beforeVariants(selector().withBuildType("release")) {
        it.hostTests.getValue(com.android.build.api.variant.HostTestBuilder.UNIT_TEST_TYPE).enable =
            true
    }
}

dependencies {
    implementation(project(":ml:vad-api"))
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
