import groovy.json.JsonOutput
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    id("dora.android.application")
}

val recoveryI2aGraphProbe =
    providers.gradleProperty("doraRecoveryI2aGraphProbe").map(String::toBoolean).orElse(false)
val recoveryI2aOwnedClasspath =
    Regex("^(debug|release|benchmark).*?(CompileClasspath|RuntimeClasspath)$")
val recoveryModulePath = project.path

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

    buildTypes {
        release {
            if (recoveryI2aGraphProbe.get()) {
                isMinifyEnabled = true
                isShrinkResources = false
                proguardFiles(
                    getDefaultProguardFile("proguard-android-optimize.txt"),
                    "proguard-recovery-i2a.pro",
                )
            }
        }
    }
}

dependencies {
    testImplementation(libs.junit4)

    if (recoveryI2aGraphProbe.get()) {
        implementation("com.google.crypto.tink:tink-android:1.23.0") {
            exclude(group = "com.google.code.findbugs", module = "jsr305")
        }
    }
}

tasks.register("recoveryI2aResolveOwnedConfigurations") {
    group = "verification"
    description = "Resolves and inventories the local unpublished REC-I2A graph boundary."

    val reportFile = layout.buildDirectory.file("reports/recovery-i2a/resolved-graph.json")
    outputs.file(reportFile)
    notCompatibleWithConfigurationCache(
        "REC-I2A intentionally enumerates the live Gradle resolution model"
    )

    doLast {
        check(recoveryI2aGraphProbe.get()) {
            "REC-I2A graph resolution requires -PdoraRecoveryI2aGraphProbe=true"
        }

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().buffered().use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        val allResolvable = configurations.filter { it.isCanBeResolved }.sortedBy { it.name }
        val owned = allResolvable.filter { recoveryI2aOwnedClasspath.matches(it.name) }
        check(owned.isNotEmpty()) { "No recovery-owned compile/runtime configurations found" }

        val configurationReports = owned.map { configuration ->
            val components =
                configuration.incoming.resolutionResult.allComponents
                    .mapNotNull { component ->
                        component.moduleVersion?.let {
                            "${it.group}:${it.name}:${it.version}"
                        }
                    }
                    .distinct()
                    .sorted()
            val artifacts =
                configuration.incoming
                    .artifactView {
                        componentFilter { it is ModuleComponentIdentifier }
                    }
                    .artifacts
                    .artifacts
                    .map { artifact ->
                        val component = artifact.id.componentIdentifier
                        val coordinate =
                            if (component is ModuleComponentIdentifier) {
                                "${component.group}:${component.module}:${component.version}"
                            } else {
                                component.displayName
                            }
                        val packagedJsr305Definitions =
                            if (artifact.file.extension.equals("jar", ignoreCase = true)) {
                                ZipFile(artifact.file).use { archive ->
                                    archive
                                        .entries()
                                        .asSequence()
                                        .map { it.name }
                                        .filter {
                                            it.startsWith("javax/annotation/") &&
                                                it.endsWith(".class")
                                        }
                                        .sorted()
                                        .toList()
                                }
                            } else {
                                emptyList()
                            }
                        mapOf(
                            "coordinate" to coordinate,
                            "fileName" to artifact.file.name,
                            "sha256" to sha256(artifact.file),
                            "packagedJsr305Definitions" to packagedJsr305Definitions,
                        )
                    }
                    .sortedBy { it["coordinate"].toString() }
            val jsr305Components = components.filter {
                it == "com.google.code.findbugs:jsr305:3.0.2"
            }
            val tinkComponents = components.filter {
                it.startsWith("com.google.crypto.tink:tink-android:")
            }
            val packagedJsr305Definitions = artifacts.flatMap {
                @Suppress("UNCHECKED_CAST")
                it["packagedJsr305Definitions"] as List<String>
            }

            check(jsr305Components.isEmpty()) {
                "JSR-305 reappeared in ${configuration.name}: $jsr305Components"
            }
            check(
                tinkComponents.all {
                    it == "com.google.crypto.tink:tink-android:1.23.0"
                }
            ) {
                "Tink version drift in ${configuration.name}: $tinkComponents"
            }
            if (
                configuration.name in
                    setOf(
                        "debugCompileClasspath",
                        "debugRuntimeClasspath",
                        "releaseCompileClasspath",
                        "releaseRuntimeClasspath",
                    )
            ) {
                check(tinkComponents == listOf("com.google.crypto.tink:tink-android:1.23.0")) {
                    "Exact Tink coordinate absent from ${configuration.name}: $tinkComponents"
                }
            }
            check(packagedJsr305Definitions.isEmpty()) {
                "Packaged JSR-305 definitions found in ${configuration.name}"
            }

            mapOf(
                "module" to recoveryModulePath,
                "name" to configuration.name,
                "canBeResolved" to true,
                "components" to components,
                "jsr305Components" to jsr305Components,
                "artifacts" to artifacts,
            )
        }

        val report =
            mapOf(
                "schemaVersion" to 1,
                "scope" to "REC-I2A-GRAPH-AUTH-20260817-01_LOCAL_UNPUBLISHED",
                "module" to recoveryModulePath,
                "rootCoordinate" to "com.google.crypto.tink:tink-android:1.23.0",
                "tinkLocalExclude" to "com.google.code.findbugs:jsr305:3.0.2",
                "allResolvableConfigurations" to
                    allResolvable.map {
                        mapOf(
                            "name" to it.name,
                            "recoveryOwnedClasspath" to recoveryI2aOwnedClasspath.matches(it.name),
                        )
                    },
                "configurations" to configurationReports,
                "configurationCount" to configurationReports.size,
                "jsr305ResolvedComponentCount" to 0,
                "packagedJsr305ClassDefinitionCount" to 0,
                "authority" to
                    mapOf(
                        "actualGraphProductIpDisposition" to "PENDING_SEPARATE_OWNER_DISPOSITION",
                        "runtimeCryptoImplementationAllowed" to false,
                        "deviceExecutionAllowed" to false,
                        "measuredExecutionAllowed" to false,
                        "passAllowed" to false,
                        "publicationAllowed" to false,
                    ),
            )

        val output = reportFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(report)) + "\n")
        logger.lifecycle("REC-I2A graph inventory: ${output.absolutePath}")
    }
}
