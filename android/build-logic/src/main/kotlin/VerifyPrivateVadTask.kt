import java.security.MessageDigest
import java.util.zip.ZipFile
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

/** Private inputs are never uploaded to a build cache or fetched with CI credentials. */
@DisableCachingByDefault(because = "Private owner-custody runtime and model")
abstract class VerifyPrivateVadTask : DefaultTask() {
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val aar: RegularFileProperty
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val model: RegularFileProperty
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val modelNotice: RegularFileProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun verifyAndStage() {
        val sourceAar = aar.get().asFile
        val sourceModel = model.get().asFile
        check(
            digest(modelNotice.get().asFile) ==
                "2e63e9a38b6e8fc0c7bc37ce174caca1862870856c6daf5697cfb785e925520b"
        ) {
            "Silero attribution identity failure"
        }
        check(sourceAar.length() == AAR_BYTES && digest(sourceAar) == AAR_SHA) {
            "Private VAD AAR integrity failure"
        }
        check(sourceModel.length() == MODEL_BYTES && digest(sourceModel) == MODEL_SHA) {
            "Private VAD model integrity failure"
        }
        ZipFile(sourceAar).use { zip ->
            val entries = zip.entries().asSequence().map { it.name }.toList()
            check(entries.size == ENTRIES.size && entries.toSet() == ENTRIES) {
                "Private VAD AAR inventory failure"
            }
        }
        val output = outputDirectory.get().asFile
        check(output.mkdirs() || output.isDirectory)
        val assetDirectory = output.resolve("assets/dora-vad")
        check(assetDirectory.mkdirs() || assetDirectory.isDirectory)
        sourceAar.copyTo(output.resolve("runtime.aar"), overwrite = true)
        sourceModel.copyTo(assetDirectory.resolve("silero.onnx"), overwrite = true)
        modelNotice.get().asFile.copyTo(assetDirectory.resolve("Silero-MIT.txt"), overwrite = true)
        ZipFile(sourceAar).use { zip ->
            zip.getInputStream(zip.getEntry("META-INF/LICENSES/NOTICE.txt")).use { input ->
                assetDirectory.resolve("Sherpa-NOTICE.txt").outputStream().use { input.copyTo(it) }
            }
        }
        check(
            digest(assetDirectory.resolve("Sherpa-NOTICE.txt")) ==
                "0caaabf2b9d78f600fd7b3392163753dd50603eee89069341ec5416fd9e4141c"
        )
        check(digest(output.resolve("runtime.aar")) == AAR_SHA)
        check(digest(assetDirectory.resolve("silero.onnx")) == MODEL_SHA)
        assetDirectory
            .resolve("identity.properties")
            .writeText("provider=REAL_SHERPA\naarSha256=$AAR_SHA\nmodelSha256=$MODEL_SHA\n")
    }

    private fun digest(file: java.io.File): String {
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val bytes = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(bytes)
                if (count < 0) break
                hash.update(bytes, 0, count)
            }
        }
        return hash.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val AAR_BYTES = 23_396_212L
        private const val MODEL_BYTES = 2_327_524L
        const val AAR_SHA = "64969d0fbf5d3936a127879684bc2f14014b99817ca3c9b2e16298c1372208db"
        const val MODEL_SHA = "1a153a22f4509e292a94e67d6f9b85e8deb25b4988682b7e174c65279d8788e3"
        private val ENTRIES =
            setOf(
                "AndroidManifest.xml",
                "META-INF/LICENSES/NOTICE.txt",
                "classes.jar",
                "jni/arm64-v8a/libonnxruntime.so",
                "jni/arm64-v8a/libsherpa-onnx-jni.so",
                "proguard.txt",
            )
    }
}
