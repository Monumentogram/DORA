plugins {
    `kotlin-dsl`
}

group = "com.monumentogram.dora.buildlogic"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    compileOnly("com.android.tools.build:gradle:9.3.1")
}

dependencyLocking {
    lockAllConfigurations()
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = "dora.android.application"
            implementationClass = "DoraAndroidApplicationPlugin"
        }
        register("androidLibrary") {
            id = "dora.android.library"
            implementationClass = "DoraAndroidLibraryPlugin"
        }
    }
}
