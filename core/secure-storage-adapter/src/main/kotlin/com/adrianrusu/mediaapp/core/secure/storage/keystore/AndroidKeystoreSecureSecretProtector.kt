package com.adrianrusu.mediaapp.core.secure.storage.keystore

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.adrianrusu.mediaapp.core.secure.storage.EncryptedSecret
import com.adrianrusu.mediaapp.core.secure.storage.SecureSecretProtector
import com.adrianrusu.mediaapp.core.secure.storage.SecureSecretPurpose
import com.adrianrusu.mediaapp.core.secure.storage.SecureStorageException
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AndroidKeystoreSecureSecretProtector : SecureSecretProtector {
    override fun encrypt(purpose: SecureSecretPurpose, plaintext: ByteArray): EncryptedSecret {
        require(plaintext.isNotEmpty()) { "Plaintext must not be empty." }

        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey(purpose))

            EncryptedSecret(
                purpose = purpose,
                iv = cipher.iv.clone(),
                ciphertext = cipher.doFinal(plaintext)
            )
        } catch (exception: GeneralSecurityException) {
            throw SecureStorageException("Failed to encrypt secret.", exception)
        } catch (exception: IOException) {
            throw SecureStorageException("Failed to encrypt secret.", exception)
        }
    }

    override fun decrypt(secret: EncryptedSecret): ByteArray = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateSecretKey(secret.purpose),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, secret.iv)
        )
        cipher.doFinal(secret.ciphertext)
    } catch (exception: GeneralSecurityException) {
        throw SecureStorageException("Failed to decrypt secret.", exception)
    } catch (exception: IOException) {
        throw SecureStorageException("Failed to decrypt secret.", exception)
    }

    private fun getOrCreateSecretKey(purpose: SecureSecretPurpose): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply {
            load(null)
        }
        val existingKey = keyStore.getKey(purpose.keystoreAlias, null)
        if (existingKey is SecretKey) {
            return existingKey
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                purpose.keystoreAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_SIZE_BITS)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH_BITS = 128
        const val KEY_SIZE_BITS = 256
    }
}
