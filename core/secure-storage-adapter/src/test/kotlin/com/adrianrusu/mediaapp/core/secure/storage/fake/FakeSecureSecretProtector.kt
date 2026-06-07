package com.adrianrusu.mediaapp.core.secure.storage.fake

import com.adrianrusu.mediaapp.core.secure.storage.EncryptedSecret
import com.adrianrusu.mediaapp.core.secure.storage.SecureSecretProtector
import com.adrianrusu.mediaapp.core.secure.storage.SecureSecretPurpose

class FakeSecureSecretProtector : SecureSecretProtector {
    override fun encrypt(purpose: SecureSecretPurpose, plaintext: ByteArray): EncryptedSecret {
        require(plaintext.isNotEmpty()) { "Plaintext must not be empty." }

        return EncryptedSecret(
            purpose = purpose,
            iv = FAKE_IV.copyOf(),
            ciphertext = plaintext.reversedArray()
        )
    }

    override fun decrypt(secret: EncryptedSecret): ByteArray = secret.ciphertext.reversedArray()

    private companion object {
        val FAKE_IV = byteArrayOf(1, 1, 2, 3, 5, 8, 13, 21, 34, 55, 89, 127)
    }
}
