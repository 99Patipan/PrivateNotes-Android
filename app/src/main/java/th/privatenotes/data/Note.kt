package th.privatenotes.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val titleCipher: ByteArray,
    val bodyCipher: ByteArray,
    val ivTitle: ByteArray,
    val ivBody: ByteArray,
    val updatedAt: Long,
    /** Index into the UI colour palette. Not secret, so stored unencrypted. */
    @ColumnInfo(defaultValue = "0") val color: Int = 0
)
