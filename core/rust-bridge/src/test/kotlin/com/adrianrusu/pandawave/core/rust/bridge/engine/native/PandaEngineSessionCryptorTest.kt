package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import com.adrianrusu.pandawave.core.secure.storage.EncryptedSecret
import com.adrianrusu.pandawave.core.secure.storage.SecureSecretProtector
import com.adrianrusu.pandawave.core.secure.storage.SecureSecretPurpose
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class PandaEngineSessionCryptorTest {
    @Test
    fun `seal and open preserve opaque bytes and associated data`() {
        val protector = RecordingProtector()
        val cryptor = PandaEngineSessionCryptor(protector)
        val plaintext = byteArrayOf(10, 20, 30)
        val associatedData = "session-format-v1".encodeToByteArray()

        val sealed = cryptor.seal(plaintext, associatedData)

        assertEquals(3, sealed.size)
        assertContentEquals(byteArrayOf(1, 2, 3), sealed[0])
        assertContentEquals(byteArrayOf(10, 20, 30), sealed[1])
        assertContentEquals(ByteArray(16) { 0x5a }, sealed[2])
        assertContentEquals(ByteArray(3), plaintext)
        assertContentEquals(associatedData, protector.lastAssociatedData)

        val opened = cryptor.open(sealed[0], sealed[1], sealed[2], associatedData)

        assertContentEquals(byteArrayOf(10, 20, 30), opened)
        assertContentEquals(associatedData, protector.lastAssociatedData)
    }

    private class RecordingProtector : SecureSecretProtector {
        var lastAssociatedData = byteArrayOf()

        override fun encrypt(
            purpose: SecureSecretPurpose,
            plaintext: ByteArray,
            associatedData: ByteArray
        ): EncryptedSecret {
            assertEquals(SecureSecretPurpose.SessionSecret, purpose)
            lastAssociatedData = associatedData.clone()
            return EncryptedSecret(
                purpose = purpose,
                iv = byteArrayOf(1, 2, 3),
                ciphertext = plaintext + ByteArray(16) { 0x5a }
            )
        }

        override fun decrypt(secret: EncryptedSecret, associatedData: ByteArray): ByteArray {
            assertEquals(SecureSecretPurpose.SessionSecret, secret.purpose)
            lastAssociatedData = associatedData.clone()
            return secret.ciphertext.copyOfRange(0, secret.ciphertext.size - 16)
        }
    }
}
