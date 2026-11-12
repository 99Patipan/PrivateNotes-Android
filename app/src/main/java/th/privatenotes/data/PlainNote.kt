package th.privatenotes.data

/** A decrypted note held in memory only while the app is unlocked. */
data class PlainNote(
    val id: Long,
    val title: String,
    val body: String,
    val updatedAt: Long
)
