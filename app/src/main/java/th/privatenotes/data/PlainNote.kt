package th.privatenotes.data

/** A decrypted note held in memory only while the app is unlocked. */
data class PlainNote(
    val id: Long,
    val title: String,
    val body: String,
    val updatedAt: Long,
    val color: Int = 0
)

/** Must match the number of entries in the UI palette (ui/NoteColors.kt). */
const val NOTE_COLOR_COUNT = 8
