package th.privatenotes.security

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android Keystore keys (StrongBox when the device has it).
 *  - device key: wraps the PIN-protected key blob so a copy of the app's files is useless
 *    off-device (the PIN alone can no longer be brute-forced offline).
 *  - biometric key: requires a strong-biometric authentication for every use (CryptoObject)
 *    and is invalidated when biometrics are re-enrolled.
 */
object KeystoreVault {
    private const val PROVIDER = "AndroidKeyStore"
    private const val DEVICE_ALIAS = "pn_device_key_v2"
    private const val BIO_ALIAS = "pn_bio_key_v2"
    private const val TRANSFORM = "AES/GCM/NoPadding"
    private const val IV_SIZE = 12
    private const val TAG_BITS = 128

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    @Suppress("DEPRECATION")
    private fun generate(alias: String, auth: Boolean, strongBox: Boolean): SecretKey {
        val b = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setKeySize(256)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
        if (auth) {
            b.setUserAuthenticationRequired(true)
            b.setInvalidatedByBiometricEnrollment(true)
            if (Build.VERSION.SDK_INT >= 30) {
                b.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
            } else {
                b.setUserAuthenticationValidityDurationSeconds(-1)
            }
        }
        if (strongBox && Build.VERSION.SDK_INT >= 28) b.setIsStrongBoxBacked(true)
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        kg.init(b.build())
        return kg.generateKey()
    }

    private fun generateWithFallback(alias: String, auth: Boolean): SecretKey {
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                return generate(alias, auth, true)
            } catch (e: Exception) {
                // StrongBox unavailable on this device: fall back to the TEE-backed keystore.
            }
        }
        return generate(alias, auth, false)
    }

    @Synchronized
    private fun deviceKey(): SecretKey {
        (keyStore().getKey(DEVICE_ALIAS, null) as? SecretKey)?.let { return it }
        return generateWithFallback(DEVICE_ALIAS, auth = false)
    }

    fun deviceWrap(plain: ByteArray): ByteArray {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, deviceKey())
        val ct = c.doFinal(plain)
        return c.iv + ct
    }

    fun deviceUnwrap(blob: ByteArray): ByteArray {
        require(blob.size > IV_SIZE + TAG_BITS / 8) { "ข้อมูลคีย์เสียหาย" }
        val c = Cipher.getInstance(TRANSFORM)
        c.init(
            Cipher.DECRYPT_MODE,
            deviceKey(),
            GCMParameterSpec(TAG_BITS, blob.copyOfRange(0, IV_SIZE))
        )
        return c.doFinal(blob, IV_SIZE, blob.size - IV_SIZE)
    }

    /** Creates a fresh biometric-bound key and returns an ENCRYPT cipher for a CryptoObject. */
    fun createBiometricEncryptCipher(): Cipher {
        deleteBiometricKey()
        val key = generateWithFallback(BIO_ALIAS, auth = true)
        return Cipher.getInstance(TRANSFORM).apply { init(Cipher.ENCRYPT_MODE, key) }
    }

    /** May throw KeyPermanentlyInvalidatedException if biometrics were re-enrolled. */
    fun biometricDecryptCipher(iv: ByteArray): Cipher {
        val key = keyStore().getKey(BIO_ALIAS, null) as? SecretKey
            ?: throw IllegalStateException("ไม่พบคีย์ลายนิ้วมือ")
        return Cipher.getInstance(TRANSFORM).apply {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        }
    }

    fun deleteBiometricKey() {
        runCatching { keyStore().deleteEntry(BIO_ALIAS) }
    }
}
