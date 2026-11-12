package th.privatenotes.security

import th.privatenotes.data.Note
import th.privatenotes.data.PlainNote
import java.security.SecureRandom

/**
 * Holds the random 256-bit data key (DEK) in memory while the app is unlocked and
 * encrypts / decrypts note fields with it. The DEK itself is never stored in the clear:
 * see [LockManager] (PIN path) and [KeystoreVault] (biometric path).
 */
class CryptoManager {
    class SealedNote(
        val titleCipher: ByteArray,
        val ivTitle: ByteArray,
        val bodyCipher: ByteArray,
        val ivBody: ByteArray
    )

    @Volatile private var dek: ByteArray? = null
    private val rng = SecureRandom()

    val isUnlocked: Boolean get() = dek != null

    fun newDataKey(): ByteArray = ByteArray(32).also { rng.nextBytes(it) }

    fun setDataKey(key: ByteArray) {
        val old = dek
        dek = key.copyOf()
        old?.fill(0)
    }

    fun dataKeyCopy(): ByteArray? = dek?.copyOf()

    fun lock() {
        val old = dek
        dek = null
        old?.fill(0)
    }

    private fun keyCopy(): ByteArray =
        dek?.copyOf() ?: throw IllegalStateException("แอปถูกล็อกอยู่")

    // Title and body are bound to their column by AAD, and the body is bound to the
    // title's IV, so fields cannot be swapped between columns or mixed between notes.
    private fun bodyAad(ivTitle: ByteArray): ByteArray = AAD_BODY + ivTitle

    fun sealNote(title: String, body: String): SealedNote {
        val key = keyCopy()
        try {
            val ivT = Gcm.newIv()
            val ivB = Gcm.newIv()
            val t = Gcm.encrypt(key, ivT, title.toByteArray(Charsets.UTF_8), AAD_TITLE)
            val b = Gcm.encrypt(key, ivB, body.toByteArray(Charsets.UTF_8), bodyAad(ivT))
            return SealedNote(t, ivT, b, ivB)
        } finally {
            key.fill(0)
        }
    }

    fun openNote(n: Note): PlainNote {
        val key = keyCopy()
        try {
            val t = Gcm.decrypt(key, n.ivTitle, n.titleCipher, AAD_TITLE)
            val b = Gcm.decrypt(key, n.ivBody, n.bodyCipher, bodyAad(n.ivTitle))
            return PlainNote(n.id, String(t, Charsets.UTF_8), String(b, Charsets.UTF_8), n.updatedAt)
        } finally {
            key.fill(0)
        }
    }

    private companion object {
        val AAD_TITLE = "pn.v2.title".toByteArray(Charsets.US_ASCII)
        val AAD_BODY = "pn.v2.body".toByteArray(Charsets.US_ASCII)
    }
}
