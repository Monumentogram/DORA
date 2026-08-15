plugins {
    base
}

check(gradle.gradleVersion == "9.5.0") {
    "The transport harness compiler is pinned to Gradle 9.5.0"
}

check(JavaVersion.current() == JavaVersion.VERSION_17) {
    "The transport harness must compile and run on JVM 17"
}

val gradleDistributionLib = checkNotNull(gradle.gradleHomeDir).resolve("lib")
val compilerJarNames =
    listOf(
        "kotlin-compiler-embeddable-2.3.20.jar",
        "kotlin-stdlib-2.3.20.jar",
        "kotlin-script-runtime-2.3.20.jar",
        "kotlin-reflect-2.3.20.jar",
        "kotlinx-coroutines-core-jvm-1.10.2.jar",
        "annotations-24.0.1.jar",
    )
val compilerJars = compilerJarNames.map(gradleDistributionLib::resolve)

check(compilerJars.all { it.isFile }) {
    "Pinned Gradle-distribution Kotlin compiler jars are missing"
}

val compilerClasspath = files(compilerJars)
val kotlinStdlib = files(gradleDistributionLib.resolve("kotlin-stdlib-2.3.20.jar"))
val kernelMainClasses = rootProject.layout.buildDirectory.dir("classes/kotlin/main")
val mainSources = fileTree("src/main/kotlin") { include("**/*.kt") }
val testSources = fileTree("src/test/kotlin") { include("**/*.kt") }
val mainClasses = layout.buildDirectory.dir("classes/kotlin/main")
val testClasses = layout.buildDirectory.dir("classes/kotlin/test")
val kernelCompile = rootProject.tasks.named("compileKotlin")
val transportBuildRoot = layout.buildDirectory.get().asFile.canonicalFile
val repositoryRootPath = rootProject.projectDir.parentFile.parentFile.parentFile.absolutePath
val loopbackRuntimeClasspath = files(testClasses, mainClasses, kernelMainClasses, kotlinStdlib)

fun validatedOutputDirectory(destination: Provider<Directory>): File {
    val output = destination.get().asFile.canonicalFile
    check(output.toPath().startsWith(transportBuildRoot.toPath()) && output != transportBuildRoot) {
        "Compiler destination must be a child of the transport harness build directory"
    }
    return output
}

fun JavaExec.configureKotlinCompiler(
    sources: FileTree,
    destination: Provider<Directory>,
    compilationClasspath: FileCollection,
) {
    val output = validatedOutputDirectory(destination)
    classpath = compilerClasspath
    mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
    jvmArgs("--add-modules=java.net.http,jdk.httpserver")
    inputs.files(sources)
    inputs.files(compilationClasspath)
    outputs.dir(destination)
    outputs.upToDateWhen { false }
    doFirst {
        if (output.exists()) {
            check(output.deleteRecursively()) { "Unable to remove compiler destination" }
        }
        check(output.mkdirs() || output.isDirectory) {
            "Unable to create clean compiler destination"
        }
    }
    args(
        "-no-stdlib",
        "-no-reflect",
        "-jvm-target",
        "17",
        "-classpath",
        compilationClasspath.asPath,
        "-d",
        output.absolutePath,
    )
    args(sources.files.sortedBy { it.invariantSeparatorsPath }.map { it.absolutePath })
}

val compileKotlin by
    tasks.registering(JavaExec::class) {
        group = "build"
        description = "Compiles the isolated hermetic loopback transport harness."
        dependsOn(kernelCompile)
        configureKotlinCompiler(mainSources, mainClasses, files(kernelMainClasses, kotlinStdlib))
    }

val compileTestKotlin by
    tasks.registering(JavaExec::class) {
        group = "build"
        description = "Compiles deterministic hermetic loopback transport tests."
        dependsOn(compileKotlin)
        configureKotlinCompiler(
            testSources,
            testClasses,
            files(mainClasses, kernelMainClasses, kotlinStdlib),
        )
        args("-Xfriend-paths=${mainClasses.get().asFile.absolutePath}")
    }

val loopbackTest by
    tasks.registering(JavaExec::class) {
        group = "verification"
        description = "Runs the forked hermetic numeric-loopback transport harness."
        dependsOn(compileTestKotlin)
        classpath = loopbackRuntimeClasspath
        mainClass.set("com.monumentogram.dora.poc.vpn.transport.LoopbackTransportHostTest")
        jvmArgs("--add-modules=java.net.http,jdk.httpserver")
        systemProperty(
            "dora.repo.root",
            repositoryRootPath,
        )
    }

val dependencyBoundaryOutput = java.io.ByteArrayOutputStream()
val dependencyBoundaryError = java.io.ByteArrayOutputStream()
val boundaryProjects = listOf(rootProject, project)
val capturedRepositoryCounts = boundaryProjects.associate { inspected ->
    inspected.path to inspected.repositories.size
}
val capturedConfigurationNames =
    boundaryProjects
        .flatMap { inspected ->
            inspected.configurations.map { configuration ->
                "${inspected.path}:${configuration.name}"
            }
        }
        .sorted()
val capturedDeclaredCoordinates =
    boundaryProjects
        .flatMap { inspected ->
            inspected.configurations.flatMap { configuration ->
                configuration.allDependencies.map { dependency ->
                    "${inspected.path}:${configuration.name}:${dependency.group}:${dependency.name}:${dependency.version}"
                }
            }
        }
        .sorted()
val capturedBuildscriptCoordinates =
    boundaryProjects
        .flatMap { inspected ->
            inspected.buildscript.configurations.flatMap { configuration ->
                configuration.allDependencies.map { dependency ->
                    "${inspected.path}:${configuration.name}:${dependency.group}:${dependency.name}:${dependency.version}"
                }
            }
        }
        .sorted()
val capturedResolvedBuildscriptRequests =
    boundaryProjects
        .flatMap { inspected ->
            inspected.buildscript.configurations
                .getByName("classpath")
                .incoming
                .resolutionResult
                .allDependencies
                .map { dependency -> "${inspected.path}:${dependency.requested.displayName}" }
        }
        .sorted()
val expectedBaseConfigurations =
    setOf("::archives", "::default", ":transport-harness:archives", ":transport-harness:default")
val gradleLibRootPath = gradleDistributionLib.canonicalFile.toPath()
val capturedCompilerPaths = compilerJars.map { it.canonicalFile }.sortedBy(File::getPath)
val capturedRuntimePaths =
    loopbackRuntimeClasspath.files.map(File::getCanonicalFile).sortedBy(File::getPath)
val expectedRuntimePaths =
    listOf(
            testClasses.get().asFile.canonicalFile,
            mainClasses.get().asFile.canonicalFile,
            kernelMainClasses.get().asFile.canonicalFile,
            kotlinStdlib.singleFile.canonicalFile,
        )
        .sortedBy(File::getPath)
val verifyDependencyBoundary by
    tasks.registering(Exec::class) {
        group = "verification"
        description = "Fails closed on coordinates, repositories, classpath drift, or JDK modules."
        dependsOn(compileTestKotlin)
        val jdepsExecutable =
            File(
                System.getProperty("java.home"),
                if (System.getProperty("os.name").startsWith("Windows")) {
                    "bin/jdeps.exe"
                } else {
                    "bin/jdeps"
                },
            )
        commandLine(
            jdepsExecutable,
            "--ignore-missing-deps",
            "--multi-release",
            "17",
            "--print-module-deps",
            mainClasses.get().asFile,
            testClasses.get().asFile,
        )
        standardOutput = dependencyBoundaryOutput
        errorOutput = dependencyBoundaryError
        isIgnoreExitValue = true
        inputs.property("repositoryCounts", capturedRepositoryCounts)
        inputs.property("configurationNames", capturedConfigurationNames)
        inputs.property("declaredCoordinates", capturedDeclaredCoordinates)
        inputs.property("buildscriptCoordinates", capturedBuildscriptCoordinates)
        inputs.property("resolvedBuildscriptRequests", capturedResolvedBuildscriptRequests)
        inputs.property("compilerPaths", capturedCompilerPaths.map(File::getPath))
        inputs.property("runtimePaths", capturedRuntimePaths.map(File::getPath))
        inputs.property("expectedRuntimePaths", expectedRuntimePaths.map(File::getPath))
        inputs.files(capturedCompilerPaths)
        inputs.files(loopbackRuntimeClasspath)
        doFirst {
            dependencyBoundaryOutput.reset()
            dependencyBoundaryError.reset()
            check(capturedRepositoryCounts.values.all { it == 0 }) {
                "The loopback build boundary forbids repositories"
            }
            check(capturedConfigurationNames.toSet() == expectedBaseConfigurations) {
                "The frozen base-plugin configuration boundary changed"
            }
            check(capturedDeclaredCoordinates.isEmpty()) {
                "The loopback build boundary forbids dependency coordinates"
            }
            check(capturedBuildscriptCoordinates.isEmpty()) {
                "The loopback build boundary forbids buildscript coordinates"
            }
            check(capturedResolvedBuildscriptRequests.isEmpty()) {
                "The buildscript classpath resolved dependency graph must remain empty"
            }
            check(capturedCompilerPaths.all { it.toPath().startsWith(gradleLibRootPath) }) {
                "Compiler inputs must remain inside the pinned Gradle distribution"
            }
            check(capturedRuntimePaths == expectedRuntimePaths) {
                "The loopback runtime classpath exceeded its frozen boundary"
            }
            check(jdepsExecutable.isFile) { "The JDK 17 jdeps executable is unavailable" }
        }
        doLast {
            check(executionResult.get().exitValue == 0) {
                "JDK module boundary inspection failed"
            }
            val modules =
                dependencyBoundaryOutput
                    .toString(Charsets.UTF_8)
                    .trim()
                    .split(',')
                    .filter(String::isNotBlank)
                    .toSet()
            check(modules == setOf("java.base", "java.net.http", "jdk.httpserver")) {
                "The loopback JDK module boundary changed"
            }
        }
    }

loopbackTest {
    dependsOn(verifyDependencyBoundary)
}

tasks.check {
    dependsOn(loopbackTest)
}
