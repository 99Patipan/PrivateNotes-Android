package th.privatenotes.security

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** AES-256-GCM helpers for software keys (random 96-bit IV, optional AAD). */
internal object Gcm {
    const val IV_SIZE = 12
    const val TAG_BITS = 128
    private val rng = SecureRandom()

    fun newIv(): ByteArray = ByteArray(IV_SIZE).also { rng.nextBytes(it) }

    fun encrypt(key: ByteArray, iv: ByteArray, plain: ByteArray, aad: ByteArray?): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        if (aad != null) c.updateAAD(aad)
        return c.doFinal(plain)
    }

    fun decrypt(key: ByteArray, iv: ByteArray, cipherText: ByteArray, aad: ByteArray?): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BITS, iv))
        if (aad != null) c.updateAAD(aad)
        return c.doFinal(cipherText)
    }
}
