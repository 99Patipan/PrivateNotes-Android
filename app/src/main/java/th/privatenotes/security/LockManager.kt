package th.privatenotes.security

import android.content.Context
import android.util.Base64
import java.security.GeneralSecurityException
import java.security.SecureRandom

sealed interface PinResult {
    class Success(val dek: ByteArray) : PinResult
    object Wrong : PinResult
    class Locked(val remainingMs: Long) : PinResult
}

/**
 * Stores the data key (DEK) wrapped twice:
 *   DEK --AES-GCM(PBKDF2(PIN))--> inner --AES-GCM(Keystore device key)--> stored blob
 * A wrong PIN simply fails to decrypt, so there is no PIN hash to attack. Failed attempts
 * are counted persistently (before the attempt is evaluated) with exponential lockout.
 */
class LockManager(context: Context) {
    private val prefs = context.getSharedPreferences("lock_v2", Context.MODE_PRIVATE)
    private val rng = SecureRandom()

    fun isConfigured(): Boolean = prefs.contains(K_BLOB)

    fun setupPin(pin: CharArray, dek: ByteArray) {
        require(pin.size in MIN_PIN..MAX_PIN && pin.all { it.isDigit() }) {
            "PIN ต้องเป็นตัวเลข $MIN_PIN–$MAX_PIN หลัก"
        }
        val salt = ByteArray(16).also { rng.nextBytes(it) }
        val iv = Gcm.newIv()
        val kek = Kdf.pbkdf2(pin, salt, PIN_ITERATIONS)
        try {
            val inner = iv + Gcm.encrypt(kek, iv, dek, AAD)
            val outer = KeystoreVault.deviceWrap(inner)
            prefs.edit()
                .putString(K_SALT, enc(salt))
                .putInt(K_ITER, PIN_ITERATIONS)
                .putString(K_BLOB, enc(outer))
                .putInt(K_FAIL, 0)
                .putLong(K_UNTIL, 0L)
                .commit()
        } finally {
            kek.fill(0)
        }
    }

    fun unlockWithPin(pin: CharArray): PinResult {
        val remaining = remainingLockoutMs()
        if (remaining > 0) return PinResult.Locked(remaining)
        val salt = dec(K_SALT)
        val outer = dec(K_BLOB)
        if (salt == null || outer == null) return PinResult.Wrong

        // Count the attempt first so killing the app mid-check cannot skip the penalty.
        val failures = prefs.getInt(K_FAIL, 0) + 1
        prefs.edit().putInt(K_FAIL, failures).commit()

        val kek = Kdf.pbkdf2(pin, salt, prefs.getInt(K_ITER, PIN_ITERATIONS))
        try {
            val inner = KeystoreVault.deviceUnwrap(outer)
            val dek = try {
                Gcm.decrypt(
                    kek,
                    inner.copyOfRange(0, Gcm.IV_SIZE),
                    inner.copyOfRange(Gcm.IV_SIZE, inner.size),
                    AAD
                )
            } catch (e: GeneralSecurityException) {
                null
            }
            if (dek != null) {
                prefs.edit().putInt(K_FAIL, 0).putLong(K_UNTIL, 0L).commit()
                return PinResult.Success(dek)
            }
            val delay = lockoutDelayMs(failures)
            if (delay > 0) {
                prefs.edit().putLong(K_UNTIL, System.currentTimeMillis() + delay).commit()
            }
            return PinResult.Wrong
        } finally {
            kek.fill(0)
        }
    }

    fun failedAttempts(): Int = prefs.getInt(K_FAIL, 0)

    fun remainingLockoutMs(): Long {
        val until = prefs.getLong(K_UNTIL, 0L)
        val left = until - System.currentTimeMillis()
        return if (left <= 0) 0L else minOf(left, MAX_LOCKOUT_MS)
    }

    private fun lockoutDelayMs(failures: Int): Long =
        if (failures < FREE_ATTEMPTS) 0L
        else minOf(BASE_LOCKOUT_MS shl minOf(failures - FREE_ATTEMPTS, 7), MAX_LOCKOUT_MS)

    // ---- biometric blob (DEK encrypted by the biometric-bound Keystore key) ----

    fun isBiometricEnabled(): Boolean = prefs.contains(K_BIO_BLOB) && prefs.contains(K_BIO_IV)

    fun saveBiometric(iv: ByteArray, blob: ByteArray) {
        prefs.edit().putString(K_BIO_IV, enc(iv)).putString(K_BIO_BLOB, enc(blob)).commit()
    }

    fun biometricIv(): ByteArray? = dec(K_BIO_IV)
    fun biometricBlob(): ByteArray? = dec(K_BIO_BLOB)

    fun clearBiometric() {
        prefs.edit().remove(K_BIO_IV).remove(K_BIO_BLOB).commit()
    }

    private fun enc(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    private fun dec(key: String): ByteArray? =
        prefs.getString(key, null)?.let { Base64.decode(it, Base64.NO_WRAP) }

    private companion object {
        const val MIN_PIN = 6
        const val MAX_PIN = 12
        const val PIN_ITERATIONS = 310_000
        const val FREE_ATTEMPTS = 5
        const val BASE_LOCKOUT_MS = 30_000L
        const val MAX_LOCKOUT_MS = 3_600_000L
        val AAD = "pn.pin.v2".toByteArray(Charsets.US_ASCII)

        const val K_SALT = "pin_salt"
        const val K_ITER = "pin_iter"
        const val K_BLOB = "pin_blob"
        const val K_FAIL = "fail_count"
        const val K_UNTIL = "lock_until"
        const val K_BIO_IV = "bio_iv"
        const val K_BIO_BLOB = "bio_blob"
    }
}
