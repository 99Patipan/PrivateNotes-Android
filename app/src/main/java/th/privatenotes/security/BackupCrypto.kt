package th.privatenotes.security

import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom

/**
 * Encrypted backup container.
 *
 * v2: "PNOTES02" | iterations (int32 BE) | salt(16) | iv(12) | ciphertext+tag
 *     The first 28 bytes (magic, iterations, salt) are authenticated as AAD.
 * v1: "PNOTES01" | salt(16) | iv(12) | ciphertext+tag   (PBKDF2 310k, no AAD) - read only.
 *
 * All failures surface as IllegalArgumentException with a user-readable (Thai) message.
 */
object BackupCrypto {
    const val MIN_PASSWORD = 10

    private val MAGIC_V1 = "PNOTES01".toByteArray(Charsets.US_ASCII)
    private val MAGIC_V2 = "PNOTES02".toByteArray(Charsets.US_ASCII)
    private const val MAGIC_SIZE = 8
    private const val ITERATIONS = 600_000
    private const val LEGACY_ITERATIONS = 310_000
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 5_000_000
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val TAG_SIZE = 16
    private const val V1_HEADER = MAGIC_SIZE + SALT_SIZE + IV_SIZE
    private const val V2_AAD = MAGIC_SIZE + 4 + SALT_SIZE
    private const val V2_HEADER = V2_AAD + IV_SIZE

    private const val NOT_BACKUP = "ไฟล์นี้ไม่ใช่ไฟล์สำรองของ Private Notes"
    private const val CORRUPT = "ไฟล์สำรองเสียหาย"
    private const val WRONG = "รหัสผ่านไม่ถูกต้อง หรือไฟล์ถูกแก้ไข"

    fun encrypt(plain: ByteArray, password: CharArray): ByteArray {
        require(password.size >= MIN_PASSWORD) {
            "รหัสผ่านสำรองต้องมีอย่างน้อย $MIN_PASSWORD ตัวอักษร"
        }
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val iv = Gcm.newIv()
        val aad = ByteBuffer.allocate(V2_AAD)
            .put(MAGIC_V2).putInt(ITERATIONS).put(salt).array()
        val key = Kdf.pbkdf2(password, salt, ITERATIONS)
        try {
            return aad + iv + Gcm.encrypt(key, iv, plain, aad)
        } finally {
            key.fill(0)
        }
    }

    fun decrypt(data: ByteArray, password: CharArray): ByteArray {
        require(data.size > MAGIC_SIZE) { NOT_BACKUP }
        val magic = data.copyOfRange(0, MAGIC_SIZE)
        return when {
            magic.contentEquals(MAGIC_V2) -> decryptV2(data, password)
            magic.contentEquals(MAGIC_V1) -> decryptV1(data, password)
            else -> throw IllegalArgumentException(NOT_BACKUP)
        }
    }

    private fun decryptV2(data: ByteArray, password: CharArray): ByteArray {
        require(data.size >= V2_HEADER + TAG_SIZE) { CORRUPT }
        val iterations = ByteBuffer.wrap(data, MAGIC_SIZE, 4).getInt()
        require(iterations in MIN_ITERATIONS..MAX_ITERATIONS) { CORRUPT }
        val salt = data.copyOfRange(MAGIC_SIZE + 4, V2_AAD)
        val iv = data.copyOfRange(V2_AAD, V2_HEADER)
        val aad = data.copyOfRange(0, V2_AAD)
        val cipherText = data.copyOfRange(V2_HEADER, data.size)
        return open(password, salt, iterations, iv, cipherText, aad)
    }

    private fun decryptV1(data: ByteArray, password: CharArray): ByteArray {
        require(data.size >= V1_HEADER + TAG_SIZE) { CORRUPT }
        val salt = data.copyOfRange(MAGIC_SIZE, MAGIC_SIZE + SALT_SIZE)
        val iv = data.copyOfRange(MAGIC_SIZE + SALT_SIZE, V1_HEADER)
        val cipherText = data.copyOfRange(V1_HEADER, data.size)
        return open(password, salt, LEGACY_ITERATIONS, iv, cipherText, null)
    }

    private fun open(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        iv: ByteArray,
        cipherText: ByteArray,
        aad: ByteArray?
    ): ByteArray {
        val key = Kdf.pbkdf2(password, salt, iterations)
        try {
            return Gcm.decrypt(key, iv, cipherText, aad)
        } catch (e: GeneralSecurityException) {
            throw IllegalArgumentException(WRONG, e)
        } finally {
            key.fill(0)
        }
    }
}
