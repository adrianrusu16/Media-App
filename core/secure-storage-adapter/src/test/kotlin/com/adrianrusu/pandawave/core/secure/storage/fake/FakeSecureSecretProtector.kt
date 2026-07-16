package com.adrianrusu.pandawave.core.secure.storage.fake

import com.adrianrusu.pandawave.core.secure.storage.EncryptedSecret
import com.adrianrusu.pandawave.core.secure.storage.SecureSecretProtector
import com.adrianrusu.pandawave.core.secure.storage.SecureSecretPurpose

class FakeSecureSecretProtector : SecureSecretProtector {
    override fun encrypt(
        purpose: SecureSecretPurpose,
        plaintext: ByteArray,
        associatedData: ByteArray
    ): EncryptedSecret {
        require(plaintext.isNotEmpty()) { "Plaintext must not be empty." }

        return EncryptedSecret(
            purpose = purpose,
            iv = FAKE_IV.copyOf(),
            ciphertext = byteArrayOf(authenticationByte(purpose, associatedData)) +
                plaintext.reversedArray()
        )
    }

    override fun decrypt(secret: EncryptedSecret, associatedData: ByteArray): ByteArray {
        require(secret.ciphertext.first() == authenticationByte(secret.purpose, associatedData)) {
            "Associated data authentication failed."
        }
        return secret.ciphertext.copyOfRange(1, secret.ciphertext.size).reversedArray()
    }

    private fun authenticationByte(
        purpose: SecureSecretPurpose,
        associatedData: ByteArray
    ): Byte = associatedData.fold(purpose.ordinal.toByte()) { checksum, value ->
        (checksum.toInt() xor value.toInt()).toByte()
    }

    private companion object {
        val FAKE_IV = byteArrayOf(1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 127)
    }
}
