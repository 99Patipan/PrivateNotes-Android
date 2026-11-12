package th.privatenotes.ui

import androidx.compose.ui.graphics.Color

class NoteColor(
    val name: String,
    /** Solid accent: left stripe in the list, swatch in pickers. */
    val stripe: Color,
    private val light: Color,
    private val dark: Color
) {
    fun background(isDark: Boolean): Color = if (isDark) dark else light
}

/**
 * The index is what gets stored (Note.color) and exported, so only ever append to this list
 * and keep th.privatenotes.data.NOTE_COLOR_COUNT in sync.
 */
val NoteColors = listOf(
    NoteColor("เหลือง", Color(0xFFFDD835), Color(0xFFFFF9C4), Color(0xFF3F3B17)),
    NoteColor("แดง", Color(0xFFE53935), Color(0xFFFFE4E6), Color(0xFF4A2327)),
    NoteColor("ส้ม", Color(0xFFFB8C00), Color(0xFFFFE8CC), Color(0xFF4A3320)),
    NoteColor("เขียว", Color(0xFF7CB342), Color(0xFFE3F1D3), Color(0xFF2B3A22)),
    NoteColor("เขียวน้ำทะเล", Color(0xFF00ACC1), Color(0xFFD3F1F5), Color(0xFF1F3C3F)),
    NoteColor("น้ำเงิน", Color(0xFF1E88E5), Color(0xFFDCEBFA), Color(0xFF223548)),
    NoteColor("ม่วง", Color(0xFF8E6BBF), Color(0xFFE6DCF3), Color(0xFF35294A)),
    NoteColor("เทา", Color(0xFF90A4AE), Color(0xFFECEFF1), Color(0xFF2F3438))
)

fun noteColor(index: Int): NoteColor = NoteColors[index.coerceIn(0, NoteColors.lastIndex)]
