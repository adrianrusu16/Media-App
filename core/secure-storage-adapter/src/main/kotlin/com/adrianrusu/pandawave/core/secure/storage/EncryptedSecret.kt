package com.adrianrusu.pandawave.core.secure.storage

/**
 * Encrypted secret material that is safe to persist in app-private storage.
 *
 * The plaintext is never stored here. The Android Keystore key identified by
 * [purpose] is required to decrypt [ciphertext] with [iv].
 */
class EncryptedSecret(val purpose: SecureSecretPurpose, iv: ByteArray, ciphertext: ByteArray) {
    val iv: ByteArray = iv.clone()
    val ciphertext: ByteArray = ciphertext.clone()

    init {
        require(iv.isNotEmpty()) { "IV must not be empty." }
        require(ciphertext.isNotEmpty()) { "Ciphertext must not be empty." }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EncryptedSecret) return false

        return purpose == other.purpose &&
            iv.contentEquals(other.iv) &&
            ciphertext.contentEquals(other.ciphertext)
    }

    override fun hashCode(): Int {
        var result = purpose.hashCode()
        result = 31 * result + iv.contentHashCode()
        result = 31 * result + ciphertext.contentHashCode()
        return result
    }
}
