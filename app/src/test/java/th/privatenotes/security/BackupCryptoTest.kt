package th.privatenotes.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupCryptoTest {
    private val pw = "correct horse battery".toCharArray()
    private val plain = "สวัสดี private notes".toByteArray(Charsets.UTF_8)

    @Test
    fun roundTrip() {
        val enc = BackupCrypto.encrypt(plain, pw)
        assertArrayEquals(plain, BackupCrypto.decrypt(enc, pw))
    }

    @Test
    fun saltAndIvDifferEachTime() {
        val a = BackupCrypto.encrypt(plain, pw)
        val b = BackupCrypto.encrypt(plain, pw)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun wrongPasswordIsRejected() {
        val enc = BackupCrypto.encrypt(plain, pw)
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(enc, "wrong password!!".toCharArray())
        }
    }

    @Test
    fun tamperedCiphertextIsRejected() {
        val enc = BackupCrypto.encrypt(plain, pw)
        enc[enc.size - 1] = (enc[enc.size - 1].toInt() xor 1).toByte()
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.decrypt(enc, pw) }
    }

    @Test
    fun tamperedHeaderIsRejected() {
        val enc = BackupCrypto.encrypt(plain, pw)
        enc[14] = (enc[14].toInt() xor 1).toByte() // inside the salt
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.decrypt(enc, pw) }
    }

    @Test
    fun shortPasswordIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.encrypt(plain, "short".toCharArray())
        }
    }

    @Test
    fun garbageAndTruncatedFilesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(ByteArray(100) { 7 }, pw)
        }
        val enc = BackupCrypto.encrypt(plain, pw)
        assertThrows(IllegalArgumentException::class.java) {
            BackupCrypto.decrypt(enc.copyOf(30), pw)
        }
    }

    @Test
    fun legacyV1FilesStillRestore() {
        val salt = ByteArray(16) { 1 }
        val iv = ByteArray(12) { 2 }
        val key = Kdf.pbkdf2(pw, salt, 310_000)
        val ct = Gcm.encrypt(key, iv, plain, null)
        val blob = "PNOTES01".toByteArray(Charsets.US_ASCII) + salt + iv + ct
        assertArrayEquals(plain, BackupCrypto.decrypt(blob, pw))
    }
}
