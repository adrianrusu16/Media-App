package com.adrianrusu.pandawave.core.secure.storage.fake

import com.adrianrusu.pandawave.core.secure.storage.SecureSecretPurpose
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class FakeSecureSecretProtectorTest {
    private val protector = FakeSecureSecretProtector()

    @Test
    fun `decrypt returns original plaintext`() {
        val plaintext = byteArrayOf(10, 20, 30, 40)

        val encrypted = protector.encrypt(
            purpose = SecureSecretPurpose.DatabaseKey,
            plaintext = plaintext
        )

        assertContentEquals(plaintext, protector.decrypt(encrypted))
    }

    @Test
    fun `encryption carries purpose`() {
        val encrypted = protector.encrypt(
            purpose = SecureSecretPurpose.SessionSecret,
            plaintext = byteArrayOf(1)
        )

        assertEquals(SecureSecretPurpose.SessionSecret, encrypted.purpose)
    }

    @Test
    fun `ciphertext does not expose plaintext in fake`() {
        val plaintext = byteArrayOf(1, 2, 3)

        val encrypted = protector.encrypt(
            purpose = SecureSecretPurpose.DatabaseKey,
            plaintext = plaintext
        )

        assertNotEquals(plaintext.toList(), encrypted.ciphertext.toList())
    }
}
