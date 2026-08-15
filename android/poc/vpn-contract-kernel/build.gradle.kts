plugins {
    base
}

check(gradle.gradleVersion == "9.5.0") { "The contract kernel compiler is pinned to Gradle 9.5.0" }

check(JavaVersion.current() == JavaVersion.VERSION_17) {
    "The contract kernel must compile and run on JVM 17"
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
val mainSources = fileTree("src/main/kotlin") { include("**/*.kt") }
val testSources = fileTree("src/test/kotlin") { include("**/*.kt") }
val mainClasses = layout.buildDirectory.dir("classes/kotlin/main")
val testClasses = layout.buildDirectory.dir("classes/kotlin/test")
val staleClassRelativePath = "stale-class-self-test/SyntheticStale.class"
val deleteGeneratedOutput: (File) -> Unit = { target ->
    delete(target)
}

fun validatedOutputDirectory(destination: Provider<Directory>): File {
    val buildRoot = layout.buildDirectory.get().asFile.canonicalFile
    val output = destination.get().asFile.canonicalFile
    check(output.toPath().startsWith(buildRoot.toPath()) && output != buildRoot) {
        "Compiler destination must be a child of this module's build directory"
    }
    return output
}

fun JavaExec.configureKotlinCompiler(
    sources: FileTree,
    destination: Provider<Directory>,
    compilationClasspath: FileCollection,
) {
    classpath = compilerClasspath
    mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
    inputs.files(sources)
    inputs.files(compilationClasspath)
    outputs.dir(destination)
    outputs.upToDateWhen { false }
    doFirst {
        val output = validatedOutputDirectory(destination)
        deleteGeneratedOutput(output)
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
        destination.get().asFile.absolutePath,
    )
    args(sources.files.sortedBy { it.invariantSeparatorsPath }.map { it.absolutePath })
}

fun registerStaleClassSeed(
    taskName: String,
    destination: Provider<Directory>,
) =
    tasks.register(taskName) {
        group = "verification"
        description = "Seeds a synthetic stale class to prove fail-closed compiler cleanup."
        doLast {
            val staleClass = validatedOutputDirectory(destination).resolve(staleClassRelativePath)
            check(staleClass.parentFile.mkdirs() || staleClass.parentFile.isDirectory)
            staleClass.writeText("synthetic-stale-class", Charsets.US_ASCII)
        }
    }

val seedStaleMainClass = registerStaleClassSeed("seedStaleMainClass", mainClasses)
val seedStaleTestClass = registerStaleClassSeed("seedStaleTestClass", testClasses)

val compileKotlin by
    tasks.registering(JavaExec::class) {
        group = "build"
        description =
            "Compiles the pure Kotlin kernel with the pinned Gradle distribution compiler."
        dependsOn(seedStaleMainClass)
        configureKotlinCompiler(mainSources, mainClasses, kotlinStdlib)
    }

val compileTestKotlin by
    tasks.registering(JavaExec::class) {
        group = "build"
        description = "Compiles the dependency-free pure-host kernel tests."
        dependsOn(compileKotlin, seedStaleTestClass)
        configureKotlinCompiler(testSources, testClasses, files(mainClasses, kotlinStdlib))
    }

val verifyStaleClassCleanup by tasks.registering {
    group = "verification"
    description = "Fails if either compiler output retains a removed-source class."
    dependsOn(compileTestKotlin)
    doLast {
        check(!validatedOutputDirectory(mainClasses).resolve(staleClassRelativePath).exists()) {
            "Main compiler output retained a stale class"
        }
        check(!validatedOutputDirectory(testClasses).resolve(staleClassRelativePath).exists()) {
            "Test compiler output retained a stale class"
        }
    }
}

val hostTest by
    tasks.registering(JavaExec::class) {
        group = "verification"
        description = "Runs the deterministic dependency-free pure-host contract kernel tests."
        dependsOn(verifyStaleClassCleanup)
        classpath = files(testClasses, mainClasses, kotlinStdlib)
        mainClass.set("com.monumentogram.dora.poc.vpn.contract.ContractKernelHostTest")
        systemProperty(
            "dora.repo.root",
            rootProject.projectDir.parentFile.parentFile.parentFile.absolutePath,
        )
    }

tasks.check {
    dependsOn(hostTest)
}
