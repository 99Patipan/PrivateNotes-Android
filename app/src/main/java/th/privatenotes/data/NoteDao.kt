package th.privatenotes.data

import androidx.room.*

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun all(): List<Note>

    @Insert
    suspend fun insert(note: Note): Long

    @Insert
    suspend fun insertAll(notes: List<Note>)

    @Update
    suspend fun update(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): Note?

    @Query("DELETE FROM notes")
    suspend fun deleteAll()
}
