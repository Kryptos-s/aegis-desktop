package com.beemdevelopment.aegis.desktop.platform.linux

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.security.SecureRandom
import java.util.UUID

/** Exercises the real Secret Service. Skipped when no keyring is running, as in most CI. */
class SecretServiceStoreTest {
    private val store = SecretServiceStore()

    @Test
    fun storeRetrieveDelete() {
        assumeTrue("No Secret Service available", store.isAvailable)

        val id = "test-${UUID.randomUUID()}"
        val key = ByteArray(32).also { SecureRandom().nextBytes(it) }

        assertFalse(store.contains(id))
        assertNull(store.retrieve(id))

        store.store(id, key.copyOf())
        try {
            assertTrue(store.contains(id))
            assertArrayEquals(key, store.retrieve(id))
        } finally {
            store.delete(id)
        }

        assertFalse(store.contains(id))
        assertNull(store.retrieve(id))
    }
}
