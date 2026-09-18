@file:Suppress("LongMethod", "MagicNumber", "TooGenericExceptionCaught", "MaxLineLength")

package com.monumentogram.dora.poc.recovery.candidate

import android.os.Build
import android.security.keystore.KeyInfo
import android.system.Os
import android.system.OsConstants
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.monumentogram.dora.poc.recovery.contract.CanonicalRecoveryAlias
import com.monumentogram.dora.poc.recovery.contract.KeyConfirmationValue
import com.monumentogram.dora.poc.recovery.contract.RecoveryCandidate
import com.monumentogram.dora.poc.recovery.contract.RunId
import com.monumentogram.dora.poc.recovery.contract.Sha256Value
import com.monumentogram.dora.poc.recovery.crypto.KeyConfirmationDecryption
import com.monumentogram.dora.poc.recovery.crypto.RecoveryRunAeadProvider
import com.monumentogram.dora.poc.recovery.storage.AndroidOsRecoveryCandidateStorage
import com.monumentogram.dora.poc.recovery.storage.RecoveryCandidatePathPolicy
import java.io.File
import java.security.KeyStore
import java.time.Instant
import java.util.UUID
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

/** Separately gated platform prerequisites. Successful fsync is not power-loss durability proof. */
@RunWith(AndroidJUnit4::class)
class RecoveryPlatformPrerequisitesInstrumentedTest {
    @Test
    fun syntheticKeystoreLifecycleAndFilesystemPrerequisites() {
        val arguments = InstrumentationRegistry.getArguments()
        require(arguments.getString("pocRecoveryPlatformPrerequisites") == "true")
        RecoveryPreflightInstrumentationIdentity.requireAccepted(arguments)
        val revision = requireNotNull(arguments.getString("recoveryHarnessRevision"))
        require(Regex("[0-9a-f]{40}").matches(revision))
        val instrument = InstrumentationRegistry.getInstrumentation()
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val observation =
            JSONObject()
                .put("protocolId", "poc-recovery-protocol-stage0-v0.8")
                .put("preflightScope", "SYNTHETIC_KEYSTORE_LIFECYCLE_AND_FILESYSTEM_PREREQUISITES")
                .put("harnessRevision", revision)
                .put("collectedAtUtc", Instant.now().toString())
                .put("deviceFingerprint", Build.FINGERPRINT)
                .put("api", Build.VERSION.SDK_INT)
                .put(
                    "targetApkSha256",
                    Sha256Value.calculate(File(context.applicationInfo.sourceDir).readBytes())
                        .toString(),
                )
                .put(
                    "testApkSha256",
                    Sha256Value.calculate(
                            File(instrument.context.applicationInfo.sourceDir).readBytes()
                        )
                        .toString(),
                )
                .put("powerLossDurabilityProven", false)
        var failure: Throwable? = null
        try {
            verifyKeystore(observation)
            verifyFilesystem(context, observation)
        } catch (error: Throwable) {
            failure = error
            observation.put("failureType", error.javaClass.simpleName)
        } finally {
            observation.put("status", if (failure == null) "PASS" else "FAIL")
            instrument.sendStatus(
                0,
                android.os.Bundle().apply {
                    putString(
                        "stream",
                        "INSTRUMENTATION_PLATFORM_PREREQUISITES_OBSERVATION $observation\n",
                    )
                },
            )
        }
        failure?.let { throw it }
    }

    // Cleanup only throws when there is no primary failure; otherwise it is added as suppressed.
    @Suppress("ThrowingExceptionFromFinally")
    private fun verifyKeystore(observation: JSONObject) {
        val runId = RunId.fromCanonicalString(UUID.randomUUID().toString())
        val alias = CanonicalRecoveryAlias.forRun(runId).removePrefix("android-keystore://")
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val facts = JSONObject().put("provider", keyStore.provider.name)
        observation.put("keystore", facts)
        check(!keyStore.containsAlias(alias)) { "Fresh preflight alias collision" }
        facts.put("initialAliasAbsent", true)
        val provider = RecoveryRunAeadProvider()
        var failure: Throwable? = null
        try {
            val value = KeyConfirmationValue(RecoveryCandidate.STREAM, runId)
            val created = provider.createNew(runId)
            check(keyStore.containsAlias(alias))
            facts.put("createdAliasPresent", true)
            val key = keyStore.getKey(alias, null) as SecretKey
            val info =
                SecretKeyFactory.getInstance(key.algorithm, "AndroidKeyStore")
                    .getKeySpec(key, KeyInfo::class.java) as KeyInfo
            facts
                .put("algorithm", key.algorithm)
                .put("keySizeBits", info.keySize)
                .put("securityLevel", info.securityLevel)
                .put("userAuthenticationRequired", info.isUserAuthenticationRequired)
                .put("nonExportable", key.encoded == null)
            check(key.algorithm == "AES" && info.keySize == 256 && key.encoded == null)
            val ciphertext = created.encryptKeyConfirmation(value)
            check(
                provider.openExisting(runId).decryptKeyConfirmation(ciphertext, value) ==
                    KeyConfirmationDecryption.Success(value)
            )
            facts.put("freshProviderOpenAndAuthenticatedReadback", true)
            keyStore.deleteEntry(alias)
            check(!keyStore.containsAlias(alias))
            facts.put("deletedAliasAbsent", true)
            check(runCatching { provider.openExisting(runId) }.isFailure)
            check(!keyStore.containsAlias(alias))
            facts.put("missingAliasOpenRefusedWithoutRegeneration", true)
        } catch (error: Throwable) {
            failure = error
            facts.put("failureType", error.javaClass.simpleName)
            throw error
        } finally {
            // Only this collision-checked, randomly named synthetic alias is owned by this test.
            try {
                if (keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
                check(!keyStore.containsAlias(alias))
                facts.put("cleanupAliasAbsent", true)
            } catch (cleanup: Throwable) {
                facts
                    .put("cleanupAliasAbsent", false)
                    .put("cleanupFailureType", cleanup.javaClass.simpleName)
                if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
            }
        }
    }

    // Nested descriptor cleanup preserves any primary failure and records cleanup failure
    // separately.
    @Suppress("NestedBlockDepth", "ThrowingExceptionFromFinally")
    private fun verifyFilesystem(context: android.content.Context, observation: JSONObject) {
        val runId = RunId.fromCanonicalString(UUID.randomUUID().toString())
        val name = "stream/stream.ct"
        val paths = RecoveryCandidatePathPolicy.paths(context.noBackupFilesDir, runId, name)
        check(!paths.runRoot.exists()) { "Fresh preflight filesystem namespace collision" }
        val storage = AndroidOsRecoveryCandidateStorage(context)
        val facts = JSONObject()
        observation.put("filesystem", facts)
        val stat = Os.statvfs(context.noBackupFilesDir.path)
        facts
            .put("blockSizeBytes", stat.f_bsize)
            .put("fragmentSizeBytes", stat.f_frsize)
            .put("availableBlocks", stat.f_bavail)
            .put("filesystemFlags", stat.f_flag)
        var handle: CandidateWriteHandle? = null
        var failure: Throwable? = null
        try {
            handle = storage.openExclusiveTemp(runId, "$name.tmp")
            facts.put("exclusiveTempCreated", true)
            val bytes = ByteArray(8_192) { (it * 17 + 3).toByte() }
            writeFully(storage, handle, bytes, 0, 4_096)
            storage.fsync(handle)
            facts.put("tempFsyncReturned", true)
            check(!storage.finalExists(runId, name))
            storage.renameTempToFinal(runId, "$name.tmp", name)
            facts.put("renameReturned", true)
            storage.fsyncParent(runId, name)
            facts.put("directoryFsyncReturned", true)
            writeFully(storage, handle, bytes, 4_096, 4_096)
            storage.fsync(handle)
            facts.put("sameDescriptorAppendAfterRenameAndFsyncReturned", true)
            storage.close(handle)
            handle = null
            val before = Os.lstat(paths.artifact.path)
            check(OsConstants.S_ISREG(before.st_mode) && before.st_size == bytes.size.toLong())
            val read =
                Os.open(
                    paths.artifact.path,
                    OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
                    0,
                )
            try {
                val opened = Os.fstat(read)
                check(opened.st_dev == before.st_dev && opened.st_ino == before.st_ino)
                val result = ByteArray(bytes.size)
                var offset = 0
                while (offset < result.size) {
                    val count = Os.read(read, result, offset, result.size - offset)
                    check(count > 0)
                    offset += count
                }
                check(Os.read(read, ByteArray(1), 0, 1) == 0 && result.contentEquals(bytes))
                facts.put("exactDescriptorReadback", true).put("syntheticBytes", bytes.size)
            } finally {
                Os.close(read)
            }
            check(runCatching { storage.openExclusiveTemp(runId, "$name.tmp") }.isFailure)
            facts.put("publishedFinalCollisionRefused", true)
            check(paths.artifact.readBytes().contentEquals(bytes))
        } catch (error: Throwable) {
            failure = error
            facts.put("failureType", error.javaClass.simpleName)
            throw error
        } finally {
            try {
                handle?.let(storage::close)
                // Do not recursively remove unknown objects: only this test's two exact leaves.
                for (file in listOf(File(paths.artifact.path + ".tmp"), paths.artifact)) {
                    if (file.exists()) {
                        check(OsConstants.S_ISREG(Os.lstat(file.path).st_mode))
                        Os.remove(file.path)
                    }
                }
                val directory = paths.artifact.parentFile!!
                if (directory.exists()) {
                    val descriptor =
                        Os.open(
                            directory.path,
                            OsConstants.O_RDONLY or OsConstants.O_CLOEXEC or OsConstants.O_NOFOLLOW,
                            0,
                        )
                    try {
                        Os.fsync(descriptor)
                    } finally {
                        Os.close(descriptor)
                    }
                    check(directory.delete())
                }
                if (paths.runRoot.exists()) check(paths.runRoot.delete())
                facts.put("cleanupOwnedNamespaceAbsent", !paths.runRoot.exists())
            } catch (cleanup: Throwable) {
                facts
                    .put("cleanupOwnedNamespaceAbsent", false)
                    .put("cleanupFailureType", cleanup.javaClass.simpleName)
                if (failure != null) failure.addSuppressed(cleanup) else throw cleanup
            }
        }
    }

    private fun writeFully(
        storage: AndroidOsRecoveryCandidateStorage,
        handle: CandidateWriteHandle,
        bytes: ByteArray,
        start: Int,
        length: Int,
    ) {
        var written = 0
        while (written < length) {
            val count = storage.write(handle, bytes, start + written, length - written)
            check(count in 1..(length - written))
            written += count
        }
    }
}
