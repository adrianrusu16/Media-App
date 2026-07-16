package com.adrianrusu.pandawave.core.rust.bridge.engine.native

import com.adrianrusu.pandawave.core.secure.storage.EncryptedSecret
import com.adrianrusu.pandawave.core.secure.storage.SecureSecretProtector
import com.adrianrusu.pandawave.core.secure.storage.SecureSecretPurpose

/**
 * JNI-facing opaque cryptography callback for PandaEngine's Rust-owned session store.
 *
 * Kotlin never interprets or persists the plaintext. The method names and JVM
 * signatures are kept in consumer rules because Rust invokes them directly.
 */
class PandaEngineSessionCryptor(private val protector: SecureSecretProtector) {
    fun seal(plaintext: ByteArray, associatedData: ByteArray): Array<ByteArray> = try {
        val encrypted = protector.encrypt(
            purpose = SecureSecretPurpose.SessionSecret,
            plaintext = plaintext,
            associatedData = associatedData
        )
        require(encrypted.ciphertext.size > GCM_TAG_LENGTH_BYTES) {
            "Encrypted session payload is incomplete."
        }
        val tagStart = encrypted.ciphertext.size - GCM_TAG_LENGTH_BYTES
        arrayOf(
            encrypted.iv,
            encrypted.ciphertext.copyOfRange(0, tagStart),
            encrypted.ciphertext.copyOfRange(tagStart, encrypted.ciphertext.size)
        )
    } finally {
        plaintext.fill(0)
    }

    fun open(
        nonce: ByteArray,
        ciphertext: ByteArray,
        tag: ByteArray,
        associatedData: ByteArray
    ): ByteArray {
        require(tag.size == GCM_TAG_LENGTH_BYTES) { "Encrypted session tag is invalid." }
        val combinedCiphertext = ciphertext + tag
        return try {
            protector.decrypt(
                EncryptedSecret(
                    purpose = SecureSecretPurpose.SessionSecret,
                    iv = nonce,
                    ciphertext = combinedCiphertext
                ),
                associatedData = associatedData
            )
        } finally {
            combinedCiphertext.fill(0)
        }
    }

    private companion object {
        const val GCM_TAG_LENGTH_BYTES = 16
    }
}
