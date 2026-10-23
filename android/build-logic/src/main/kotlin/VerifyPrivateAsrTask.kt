import groovy.json.JsonSlurper
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.security.MessageDigest
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/** Only explicit local artifacts enter this path; weights are never an input or output. */
@DisableCachingByDefault(because = "Controlled private native inputs must not enter shared caches")
abstract class VerifyPrivateAsrTask : DefaultTask() {
    @get:Input abstract val emulatorTest: Property<Boolean>
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val nativeDirectory: DirectoryProperty
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val receipt: RegularFileProperty
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val adapterSource: RegularFileProperty
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val cmakeSource: RegularFileProperty
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val retainedNotices: DirectoryProperty
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val projectNotices: DirectoryProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun verifyAndStage() {
        val test = emulatorTest.get()
        val abi = if (test) "x86_64" else "arm64-v8a"
        val root = nativeDirectory.get().asFile
        check(!Files.isSymbolicLink(root.toPath()) && root.list()?.toSet() == setOf(abi)) {
            "ASR native ABI inventory failure"
        }
        val directory = root.resolve(abi)
        val pins = if (test) TEST_RUNTIME else ACCEPTED_RUNTIME
        val names = pins.keys + "libdora-whisper-jni.so"
        check(directory.list()?.toSet() == names) { "ASR native inventory failure" }
        val data =
            JsonSlurper().parse(receipt.get().asFile) as? Map<*, *>
                ?: error("ASR receipt format failure")
        val artifacts = verifyReceipt(data, test, abi, names)
        names.forEach { name ->
            val file = directory.resolve(name)
            checkPin(file, artifacts[name])
            pins[name]?.let { (bytes, sha) ->
                check(file.length() == bytes && digest(file) == sha) {
                    "ASR pinned runtime failure"
                }
            }
            checkElf(file, if (test) ELF_X86_64 else ELF_AARCH64)
        }
        val needed =
            if (test) (artifacts["libdora-whisper-jni.so"] as Map<*, *>)["needed"]
            else data["adapterNeeded"]
        check(
            (needed as? List<*>)?.toSet() ==
                setOf("libdl.so", "libm.so", "libc++_shared.so", "libc.so")
        ) {
            "ASR eager specialized dependency failure"
        }
        stage(abi, directory, names, artifacts)
    }

    private fun verifyReceipt(
        data: Map<*, *>,
        test: Boolean,
        abi: String,
        names: Set<String>,
    ): Map<*, *> {
        checkReceiptScope(data, test, abi)
        val sourcePins = data["sourcePins"] as? Map<*, *> ?: error("ASR source receipt failure")
        check(sourcePins.keys == setOf("whisper_jni.cpp", "CMakeLists.txt")) {
            "ASR source inventory failure"
        }
        checkPin(adapterSource.get().asFile, sourcePins["whisper_jni.cpp"])
        checkPin(cmakeSource.get().asFile, sourcePins["CMakeLists.txt"])
        val artifacts = data["artifacts"] as? Map<*, *> ?: error("ASR artifact receipt failure")
        check(artifacts.keys == names) { "ASR receipt inventory failure" }
        return artifacts
    }

    private fun checkReceiptScope(data: Map<*, *>, test: Boolean, abi: String) {
        check(
            data["runtimeCommit"] == RUNTIME_COMMIT &&
                data["ndk"] == "28.2.13676358" &&
                data["abi"] == abi
        ) {
            "ASR receipt runtime failure"
        }
        check(data["modelPackaged"] == false && data["minimumApi"] == MINIMUM_API) {
            "ASR receipt scope failure"
        }
        if (test) {
            check(
                data["result"] == "PASS_TEST_BUILD_STATIC_ONLY" &&
                    data["productionAdmission"] == false &&
                    data["scope"] == "UNADMITTED_DISPOSABLE_EMULATOR_FUNCTIONAL_TEST_ONLY" &&
                    data["capabilityGuard"] == "EXPLICIT_COMPILE_TIME_TEST_VARIANT"
            ) {
                "ASR test admission failure"
            }
        } else {
            check(
                data["result"] == "PASS_BUILD_AND_STATIC_LAYOUT_ONLY" &&
                    data["runtimeRebuilt"] == false
            ) {
                "ASR product admission failure"
            }
        }
    }

    private fun stage(abi: String, directory: File, names: Set<String>, artifacts: Map<*, *>) {
        val notices = retainedNotices.get().asFile
        NOTICE_PINS.forEach { (name, sha) ->
            check(digest(notices.resolve(name)) == sha) { "ASR retained notice failure" }
        }
        val attribution = projectNotices.get().asFile
        check(
            digest(attribution.resolve("OpenAI-MIT.txt")) ==
                "b5d65a59060e68c4ff940e1eddfa6f94b2d68fdf58ed7f4dd57721c997e35e9d"
        ) {
            "ASR model notice failure"
        }
        check(
            digest(attribution.resolve("whisper-MIT.txt")) ==
                NOTICE_PINS.getValue("whisper-MIT.txt")
        ) {
            "ASR runtime notice failure"
        }
        val destination = outputDirectory.get().asFile
        val libraries = destination.resolve("jniLibs/$abi")
        val assets = destination.resolve("assets/dora-asr-notices")
        check(libraries.mkdirs() || libraries.isDirectory)
        check(assets.mkdirs() || assets.isDirectory)
        names.forEach { directory.resolve(it).copyTo(libraries.resolve(it), overwrite = true) }
        NOTICE_PINS.keys.forEach {
            notices.resolve(it).copyTo(assets.resolve(it), overwrite = true)
        }
        setOf("OpenAI-MIT.txt", "model-attribution.md").forEach {
            attribution.resolve(it).copyTo(assets.resolve(it), overwrite = true)
        }
        check(libraries.parentFile.list()?.toSet() == setOf(abi)) {
            "ASR staged ABI inventory failure"
        }
        check(
            assets.list()?.toSet() ==
                NOTICE_PINS.keys + setOf("OpenAI-MIT.txt", "model-attribution.md")
        ) {
            "ASR staged asset inventory failure"
        }
        check(libraries.list()?.toSet() == names) { "ASR staged inventory failure" }
        names.forEach { checkPin(libraries.resolve(it), artifacts[it]) }
    }

    private fun checkPin(file: File, value: Any?) {
        val expected = value as? Map<*, *> ?: error("ASR artifact pin missing")
        check(
            !Files.isSymbolicLink(file.toPath()) &&
                file.isFile &&
                file.length() == (expected["bytes"] as? Number)?.toLong() &&
                digest(file) == expected["sha256"]
        ) {
            "ASR artifact identity failure"
        }
    }

    // Fixed ELF64 ABI field offsets and identifiers, not tuning parameters.
    @Suppress("MagicNumber")
    private fun checkElf(file: File, machine: Int) {
        val bytes = file.readBytes()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        check(
            bytes.size >= 64 &&
                bytes.take(6) == listOf<Byte>(127, 69, 76, 70, 2, 1) &&
                buffer.getShort(18).toInt() == machine
        ) {
            "ASR ELF identity failure"
        }
        val offset = buffer.getLong(32)
        val size = buffer.getShort(54).toInt() and 65535
        val count = buffer.getShort(56).toInt() and 65535
        check(size == 56 && offset >= 64 && offset + size.toLong() * count <= bytes.size) {
            "ASR ELF header failure"
        }
        var loads = 0
        repeat(count) { index ->
            val start = (offset + size.toLong() * index).toInt()
            if (buffer.getInt(start) == 1) {
                loads++
                check(
                    buffer.getLong(start + 48) >= 16384 &&
                        (buffer.getLong(start + 16) - buffer.getLong(start + 8)) % 16384 == 0L
                ) {
                    "ASR ELF alignment failure"
                }
            }
        }
        check(loads > 0) { "ASR ELF missing load segments" }
    }

    private fun digest(file: File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_BYTES)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                hash.update(buffer, 0, count)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val BUFFER_BYTES = 65_536
        private const val MINIMUM_API = 28
        private const val ELF_X86_64 = 62
        private const val ELF_AARCH64 = 183
        private const val RUNTIME_COMMIT = "927cfce34f31707e17f2bff35c349632fb9e2c3a"
        private val ACCEPTED_RUNTIME =
            mapOf(
                "libc++_shared.so" to
                    (9236352L to
                        "ab4e6c71b96b851de45a8a9bd86369e7dbc2130a44b3b4520564be94847910f2"),
                "libggml-base.so" to
                    (5904504L to
                        "ef2fab9ca78cb998feed953755c394d46cd5182b355909032415e79ad522e049"),
                "libggml-cpu.so" to
                    (4699840L to
                        "ab61e958ed28b6ec9ef1c92bd141f99149bb02e6147c1d710824565bdb2c7a14"),
                "libggml.so" to
                    (533264L to "032f86f3f42ba9dedeac0f7f0b69c818917c6ddab6da98b677f2cef6dbea4f97"),
                "libwhisper.so" to
                    (7176544L to
                        "b67a219f3610f9085a75fe4dd7c2c11a18edea60068e63c2f4b63c330d5f96e3"),
            )
        private val TEST_RUNTIME =
            mapOf(
                "libc++_shared.so" to
                    (8808664L to
                        "e4cd73c8a3607269f3be58d15c21f78bff112e27f9398d6261e5f965668f8746"),
                "libggml-base.so" to
                    (5468592L to
                        "45c5229b2902747d6dce6fc9dadfe2724e0d0fa09f8667c9ed0d09eb479027e3"),
                "libggml-cpu.so" to
                    (4587072L to
                        "c2e77d61940ab49b164cdb67e8dc3874d7febec50c3856209f4ac4fed6ce1716"),
                "libggml.so" to
                    (514600L to "97acdfbe01c8938bce24094700ccef2a0f775bb913a198bd9776b57d044138b5"),
                "libwhisper.so" to
                    (6856152L to
                        "3eb3dffd880750a95c34831b9fea095c1107545afc31965689ebc3454a9cd9b8"),
            )
        private val NOTICE_PINS =
            mapOf(
                "whisper-MIT.txt" to
                    "94f29bbed6a22c35b992c5c6ebf0e7c92f13b836b90f36f461c9cf2f0f1d010d",
                "NDK-NOTICE.txt" to
                    "faf0dc52cf6ce9ab09a04b0c074a378dd2565ee1ed9bc1179d83793bcdba012e",
                "NDK-NOTICE-toolchain.txt" to
                    "789714a9076571c580cf531bdcebec776a4d889c12c3c1108adafc6e4338710a",
            )
    }
}
