package com.adrianrusu.mediaapp.core.secure.storage.fake

import com.adrianrusu.mediaapp.core.secure.storage.SecureSecretPurpose
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FakeSecureSecretProtectorTest {
    private val protector = FakeSecureSecretProtector()

    @Test
    fun decryptReturnsOriginalPlaintext() {
        val plaintext = byteArrayOf(10, 20, 30, 40)

        val encrypted = protector.encrypt(
            purpose = SecureSecretPurpose.DatabaseKey,
            plaintext = plaintext
        )

        assertArrayEquals(plaintext, protector.decrypt(encrypted))
    }

    @Test
    fun encryptionCarriesPurpose() {
        val encrypted = protector.encrypt(
            purpose = SecureSecretPurpose.SessionSecret,
            plaintext = byteArrayOf(1)
        )

        assertEquals(SecureSecretPurpose.SessionSecret, encrypted.purpose)
    }

    @Test
    fun ciphertextDoesNotExposePlaintextInFake() {
        val plaintext = byteArrayOf(1, 2, 3)

        val encrypted = protector.encrypt(
            purpose = SecureSecretPurpose.DatabaseKey,
            plaintext = plaintext
        )

        assertNotEquals(plaintext.toList(), encrypted.ciphertext.toList())
    }
}
