plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.spotless)
}

val detektCli by configurations.creating

dependencies {
    detektCli(libs.detekt.cli)
}

spotless {
    kotlin {
        target("**/src/**/*.kt")
        targetExclude("**/build/**")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
    kotlinGradle {
        target("**/*.gradle.kts")
        targetExclude("**/build/**")
        ktfmt(libs.versions.ktfmt.get()).kotlinlangStyle()
    }
}

val detektSources =
    fileTree(rootDir) {
        include("**/src/**/*.kt")
        exclude("**/build/**")
    }
val detektConfig = layout.projectDirectory.file("config/detekt/detekt.yml").asFile

tasks.register<JavaExec>("detekt") {
    group = "verification"
    description = "Runs stable Detekt CLI against Kotlin source without applying an AGP plugin."
    classpath = detektCli
    mainClass.set("io.gitlab.arturbosch.detekt.cli.Main")
    inputs.files(detektSources)
    inputs.file(detektConfig)

    args(
        "--input",
        detektSources.files.joinToString(",") { it.absolutePath },
        "--config",
        detektConfig.absolutePath,
        "--build-upon-default-config",
    )
}

allprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}
