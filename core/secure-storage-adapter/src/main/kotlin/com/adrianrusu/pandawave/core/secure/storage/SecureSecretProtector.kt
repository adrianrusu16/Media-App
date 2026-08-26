package com.adrianrusu.pandawave.core.secure.storage

/**
 * Protects short-lived secret material with platform-backed keys.
 *
 * The caller owns persistence of [EncryptedSecret]. Implementations should avoid
 * logging plaintext, ciphertext, IVs, aliases, or error payloads.
 * Decryption must receive the exact associated data supplied during encryption.
 */
interface SecureSecretProtector {
    fun encrypt(
        purpose: SecureSecretPurpose,
        plaintext: ByteArray,
        associatedData: ByteArray = byteArrayOf()
    ): EncryptedSecret

    fun decrypt(secret: EncryptedSecret, associatedData: ByteArray = byteArrayOf()): ByteArray
}
