package com.monumentogram.dora.audio.persistence.keys

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultSecretStoreTest {
    @Test
    fun selectorChangedDuringPersistenceCannotBePublishedAsAvailable() {
        val storage = MemoryBundleStorage().apply { substituteSelector = true }
        val result = VaultSecretStore(storage, MemoryKeys()).createNew()
        assertTrue(result is KeyAccess.Unavailable)
        assertEquals(KeyFailure.CORRUPT_CIPHERTEXT, (result as KeyAccess.Unavailable).failure)
    }

    @Test
    fun reopenDoesNotGenerateAKeyAndRestoresTheSameIdentityAndSecret() {
        val storage = MemoryBundleStorage()
        val keys = MemoryKeys()
        val created = available(VaultSecretStore(storage, keys).createNew())
        val reopened = available(VaultSecretStore(storage, keys).openExisting())
        assertEquals(created.ownerId, reopened.ownerId)
        assertEquals(created.vaultId, reopened.vaultId)
        created.borrowDatabaseSecret { first ->
            reopened.borrowDatabaseSecret { assertArrayEquals(first, it) }
        }
        assertEquals(1, keys.generations)
        assertEquals(
            KeyFailure.NAMESPACE_OCCUPIED,
            failure(VaultSecretStore(storage, keys).createNew()),
        )
    }

    @Test
    fun reservationFailureNeverCreatesKey() {
        val storage = MemoryBundleStorage().apply { failReserve = true }
        val keys = MemoryKeys()
        assertEquals(
            KeyFailure.STORAGE_FAILURE,
            failure(VaultSecretStore(storage, keys).createNew()),
        )
        assertEquals(0, keys.generations)
    }

    @Test
    fun failedWriteRetainsReservationAndNeverReplacesKey() {
        val storage = MemoryBundleStorage().apply { failWrite = true }
        val keys = MemoryKeys()
        assertEquals(
            KeyFailure.STORAGE_FAILURE,
            failure(VaultSecretStore(storage, keys).createNew()),
        )
        assertEquals(
            KeyFailure.NAMESPACE_OCCUPIED,
            failure(VaultSecretStore(storage, keys).createNew()),
        )
        assertEquals(
            KeyFailure.INCOMPLETE_BOOTSTRAP,
            failure(VaultSecretStore(storage, keys).openExisting()),
        )
        assertEquals(1, keys.generations)
    }

    @Test
    fun missingKeyRetainsBundleAndCannotTriggerReplacement() {
        val storage = MemoryBundleStorage()
        val keys = MemoryKeys()
        available(VaultSecretStore(storage, keys).createNew())
        val before = storage.bundle!!.copyOf()
        keys.missing = true
        assertEquals(
            KeyFailure.PERMANENTLY_MISSING_OR_INVALIDATED,
            failure(VaultSecretStore(storage, keys).openExisting()),
        )
        assertArrayEquals(before, storage.bundle)
        assertEquals(
            KeyFailure.NAMESPACE_OCCUPIED,
            failure(VaultSecretStore(storage, keys).createNew()),
        )
        assertEquals(1, keys.generations)
    }

    @Test
    fun uncertainKeyCreationFencesNamespace() {
        val storage = MemoryBundleStorage()
        val keys = MemoryKeys().apply { failGenerate = true }
        assertEquals(
            KeyFailure.TEMPORARILY_UNAVAILABLE,
            failure(VaultSecretStore(storage, keys).createNew()),
        )
        assertEquals(
            KeyFailure.NAMESPACE_OCCUPIED,
            failure(VaultSecretStore(storage, keys).createNew()),
        )
        assertNull(storage.bundle)
    }

    private fun available(result: KeyAccess<VaultSecrets>) = (result as KeyAccess.Available).value

    private fun failure(result: KeyAccess<VaultSecrets>) = (result as KeyAccess.Unavailable).failure
}

private class MemoryBundleStorage : VaultBundleStorage {
    var substituteSelector = false
    var selector: ByteArray? = null
    var bundle: ByteArray? = null
    var failReserve = false
    var failWrite = false

    override fun reserve(selector: ByteArray) {
        if (this.selector != null) throw KeyBoundaryException(KeyFailure.NAMESPACE_OCCUPIED)
        if (failReserve) throw KeyBoundaryException(KeyFailure.STORAGE_FAILURE)
        this.selector = selector.copyOf()
    }

    override fun readSelector() =
        selector?.copyOf() ?: throw KeyBoundaryException(KeyFailure.INCOMPLETE_BOOTSTRAP)

    override fun persist(bundle: ByteArray) {
        if (substituteSelector) selector = ByteArray(32)
        if (failWrite) throw KeyBoundaryException(KeyFailure.STORAGE_FAILURE)
        this.bundle = bundle.copyOf()
    }

    override fun readBundle() =
        bundle?.copyOf() ?: throw KeyBoundaryException(KeyFailure.INCOMPLETE_BOOTSTRAP)
}

private class MemoryKeys : VaultKeyBackend {
    private val aead = TestAead()
    var generations = 0
    var missing = false
    var failGenerate = false

    override fun createNew(selector: ByteArray): ConfirmedVaultKey {
        generations++
        if (failGenerate) throw KeyBoundaryException(KeyFailure.TEMPORARILY_UNAVAILABLE)
        return ConfirmedVaultKey(aead, KeyProtection.SOFTWARE)
    }

    override fun openExisting(selector: ByteArray): ConfirmedVaultKey {
        if (missing) throw KeyBoundaryException(KeyFailure.PERMANENTLY_MISSING_OR_INVALIDATED)
        return ConfirmedVaultKey(aead, KeyProtection.SOFTWARE)
    }
}
