import groovy.json.JsonOutput
import java.security.MessageDigest
import java.util.zip.ZipFile
import org.gradle.api.artifacts.component.ModuleComponentIdentifier

plugins {
    id("dora.android.application")
}

val recoveryI2aGraphProbe =
    providers.gradleProperty("doraRecoveryI2aGraphProbe").map(String::toBoolean).orElse(false)
val recoveryI2aPolicyConfiguration = Regex("^(debug|release|benchmark).*")
val recoveryModulePath = project.path

android {
    namespace = "com.monumentogram.dora.poc.recovery"

    defaultConfig {
        applicationId = "com.monumentogram.dora.poc.recovery"
        versionCode = 1
        versionName = "0.1.0-poc-recovery-i2b"
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
    implementation("com.google.crypto.tink:tink-android:1.23.0") {
        exclude(group = "com.google.code.findbugs", module = "jsr305")
    }

    testImplementation(libs.junit4)
}

tasks.register("recoveryI2aResolveOwnedConfigurations") {
    group = "verification"
    description =
        "Builds both variants, then resolves the REC-I2A-approved graph used by local REC-I2B."
    dependsOn("assembleDebug", "assembleRelease")

    val reportFile = layout.buildDirectory.file("reports/recovery-i2a/resolved-graph.json")
    outputs.file(reportFile)
    outputs.upToDateWhen { false }
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
        val policyCovered = allResolvable.filter { recoveryI2aPolicyConfiguration.matches(it.name) }
        check(policyCovered.isNotEmpty()) { "No Recovery policy-covered configurations found" }

        val configurationReports = allResolvable.map { configuration ->
            val isPolicyCovered = recoveryI2aPolicyConfiguration.matches(configuration.name)
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
                it.startsWith("com.google.code.findbugs:jsr305:")
            }
            val tinkComponents = components.filter {
                it.startsWith("com.google.crypto.tink:tink-android:")
            }
            val packagedJsr305Definitions = artifacts.flatMap {
                @Suppress("UNCHECKED_CAST")
                it["packagedJsr305Definitions"] as List<String>
            }

            if (isPolicyCovered) {
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
            }

            mapOf(
                "module" to recoveryModulePath,
                "name" to configuration.name,
                "canBeResolved" to true,
                "policyCovered" to isPolicyCovered,
                "boundaryClassification" to
                    if (isPolicyCovered) {
                        "OD14_VARIANT_DEPENDENCY_CONFIGURATION"
                    } else {
                        "AGP_KOTLIN_LINT_UTP_OR_PLATFORM_TOOLING_OUTSIDE_RECOVERY_DEPENDENCY_ADMISSION"
                    },
                "components" to components,
                "jsr305Components" to jsr305Components,
                "artifacts" to artifacts,
            )
        }

        val report =
            mapOf(
                "schemaVersion" to 1,
                "scope" to "REC-I2B-RUNTIME-CRYPTO-IMPLEMENTATION_LOCAL_UNPUBLISHED",
                "module" to recoveryModulePath,
                "rootCoordinate" to "com.google.crypto.tink:tink-android:1.23.0",
                "tinkLocalExclude" to "com.google.code.findbugs:jsr305:3.0.2",
                "approvedGraphInput" to "REC-I2A-ACTUAL-GRAPH-PRODUCT-IP-DISPOSITION-20260817-01",
                "allResolvableConfigurationsEnumeratedAndResolved" to true,
                "configurations" to configurationReports,
                "configurationCount" to configurationReports.size,
                "policyCoveredConfigurationCount" to policyCovered.size,
                "outsidePolicyBoundaryConfigurationCount" to
                    configurationReports.size - policyCovered.size,
                "policyCoveredJsr305ResolvedComponentCount" to
                    configurationReports
                        .filter { it["policyCovered"] == true }
                        .sumOf {
                            @Suppress("UNCHECKED_CAST")
                            (it["jsr305Components"] as List<String>).size
                        },
                "outsidePolicyBoundaryJsr305ResolvedComponentCount" to
                    configurationReports
                        .filter { it["policyCovered"] == false }
                        .sumOf {
                            @Suppress("UNCHECKED_CAST")
                            (it["jsr305Components"] as List<String>).size
                        },
                "policyCoveredPackagedJsr305ClassDefinitionCount" to
                    configurationReports
                        .filter { it["policyCovered"] == true }
                        .sumOf {
                            @Suppress("UNCHECKED_CAST")
                            (it["artifacts"] as List<Map<String, Any>>).sumOf { artifact ->
                                @Suppress("UNCHECKED_CAST")
                                (artifact["packagedJsr305Definitions"] as List<String>).size
                            }
                        },
                "outsidePolicyBoundaryPackagedJsr305ClassDefinitionCount" to
                    configurationReports
                        .filter { it["policyCovered"] == false }
                        .sumOf {
                            @Suppress("UNCHECKED_CAST")
                            (it["artifacts"] as List<Map<String, Any>>).sumOf { artifact ->
                                @Suppress("UNCHECKED_CAST")
                                (artifact["packagedJsr305Definitions"] as List<String>).size
                            }
                        },
                "authority" to
                    mapOf(
                        "actualGraphProductIpDisposition" to
                            "REC-I2A-ACTUAL-GRAPH-PRODUCT-IP-DISPOSITION-20260817-01",
                        "conditionalOwnerAuthorization" to "STAGE0-OWNER-UNLOCK-BATCH-20260817-02",
                        "technicalDelegation" to
                            "STAGE0-TECHNICAL-REMEDIATION-DELEGATION-20260817-01",
                        "runtimeCryptoImplementationAllowed" to true,
                        "accountableEngineeringSecurityReviewCompleted" to false,
                        "recI3Allowed" to false,
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

tasks.register("recoveryI2bVerifyCryptoPolicy") {
    group = "verification"
    description = "Checks the fail-closed REC-I2B source, dependency, and exact R8 policy boundary."
    dependsOn("compileDebugKotlin", "compileReleaseKotlin")

    val cryptoSourceDirectory =
        layout.projectDirectory.dir("src/main/kotlin/com/monumentogram/dora/poc/recovery/crypto")
    val projectDirectoryFile = layout.projectDirectory.asFile
    val buildScript = layout.projectDirectory.file("build.gradle.kts")
    val r8Policy = layout.projectDirectory.file("proguard-recovery-i2a.pro")
    val reportFile = layout.buildDirectory.file("reports/recovery-i2b/crypto-policy.json")
    inputs.dir(cryptoSourceDirectory)
    inputs.file(buildScript)
    inputs.file(r8Policy)
    outputs.file(reportFile)

    doLast {
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

        val sourceFiles =
            cryptoSourceDirectory.asFile
                .walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .sortedBy { it.invariantSeparatorsPath }
                .toList()
        check(sourceFiles.isNotEmpty()) { "REC-I2B crypto source boundary is empty" }
        val sourceText = sourceFiles.joinToString("\n") { it.readText() }
        val forbiddenIdentifiers =
            listOf(
                "AndroidKeysetManager",
                "getOrGenerateNewAeadKey",
                "StreamingAeadKeyTemplates",
                "SecretKeyAccess",
                "serializeKeyset(",
                "parseKeyset(",
                "fun configuration(",
                "internal val primitive",
                "RecoveryRunAead internal constructor",
            )
        val foundForbiddenIdentifiers = forbiddenIdentifiers.filter(sourceText::contains)
        check(foundForbiddenIdentifiers.isEmpty()) {
            "Forbidden REC-I2B API identifiers found: $foundForbiddenIdentifiers"
        }

        val requiredSourceFragments =
            listOf(
                "AesGcmHkdfStreamingParameters.builder()",
                ".setKeySizeBytes(RecoveryTinkRuntime.STREAMING_INPUT_KEY_BYTES)",
                ".setDerivedAesGcmKeySizeBytes(RecoveryTinkRuntime.STREAMING_DERIVED_KEY_BYTES)",
                ".setHkdfHashType(AesGcmHkdfStreamingParameters.HashType.SHA256)",
                ".setCiphertextSegmentSizeBytes(RecoveryTinkRuntime.STREAMING_CIPHERTEXT_SEGMENT_BYTES)",
                "AesGcmParameters.builder()",
                ".setKeySizeBytes(RecoveryTinkRuntime.AEAD_KEY_BYTES)",
                ".setIvSizeBytes(RecoveryTinkRuntime.AEAD_IV_BYTES)",
                ".setTagSizeBytes(RecoveryTinkRuntime.AEAD_TAG_BYTES)",
                ".setVariant(AesGcmParameters.Variant.TINK)",
                "KeysetHandle.generateEntryFromParameters(parameters).withRandomId().makePrimary()",
                "TinkProtoKeysetFormat.serializeEncryptedKeyset(",
                "TinkProtoKeysetFormat.parseEncryptedKeyset(",
                "RegisteredRecoveryTink.configuration",
                "AndroidKeystoreKmsClient.generateNewAeadKey(keyUri)",
                "AndroidKeystoreKmsClient.Builder().setKeyUri(keyUri).build().getAead(keyUri)",
            )
        val missingSourceFragments = requiredSourceFragments.filterNot(sourceText::contains)
        check(missingSourceFragments.isEmpty()) {
            "Required REC-I2B source fragments missing: $missingSourceFragments"
        }

        val dependencyText = buildScript.asFile.readText()
        check(dependencyText.contains("com.google.crypto.tink:tink-android:1.23.0")) {
            "Exact Tink 1.23.0 coordinate is missing"
        }
        check(
            dependencyText.contains(
                "exclude(group = \"com.google.code.findbugs\", module = \"jsr305\")"
            )
        ) {
            "Exact dependency-local JSR305 exclusion is missing"
        }

        val dontWarnRules =
            r8Policy.asFile.readLines().map(String::trim).filter { it.startsWith("-dontwarn") }
        val expectedDontWarnRules =
            listOf(
                "-dontwarn javax.annotation.Nullable",
                "-dontwarn javax.annotation.concurrent.GuardedBy",
                "-dontwarn javax.annotation.concurrent.ThreadSafe",
            )
        check(dontWarnRules == expectedDontWarnRules) {
            "REC-I2B requires exactly the approved three JSR305 R8 rules: $dontWarnRules"
        }
        check(
            r8Policy.asFile
                .readLines()
                .map(String::trim)
                .contains("-keep class com.monumentogram.dora.poc.recovery.crypto.** { *; }")
        ) {
            "REC-I2B crypto boundary is not retained by the local R8 probe"
        }

        val report =
            mapOf(
                "schemaVersion" to 1,
                "scope" to "REC-I2B-RUNTIME-CRYPTO-IMPLEMENTATION_LOCAL_UNPUBLISHED",
                "claimCeiling" to
                    "IMPLEMENTED_AND_LOCALLY_VERIFIED_PENDING_ACCOUNTABLE_ENGINEERING_SECURITY_REVIEW",
                "sourceFiles" to
                    sourceFiles.associate { file ->
                        file.relativeTo(projectDirectoryFile).invariantSeparatorsPath to
                            sha256(file)
                    },
                "forbiddenIdentifiersAbsent" to true,
                "requiredPublicApiFragmentsPresent" to true,
                "cleartextSecretKeysetSerializationAbsent" to true,
                "callerSuppliedConfigurationSurfaceAbsent" to true,
                "rawPrimitiveClientSurfaceAbsent" to true,
                "exactTinkCoordinate" to "com.google.crypto.tink:tink-android:1.23.0",
                "dependencyLocalJsr305ExclusionPresent" to true,
                "exactR8DontWarnRules" to dontWarnRules,
                "cryptoBoundaryRetainedByR8Probe" to true,
                "deviceExecutionPerformed" to false,
                "measuredExecutionPerformed" to false,
                "recI3Activated" to false,
                "accountableEngineeringSecurityReviewCompleted" to false,
            )
        val output = reportFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(report)) + "\n")
        logger.lifecycle("REC-I2B crypto policy: ${output.absolutePath}")
    }
}
