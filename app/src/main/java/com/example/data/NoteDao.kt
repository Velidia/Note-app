package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isPinned DESC, userEditedTimestamp DESC")
    fun getAllNotes(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE isArchived = :archived ORDER BY isPinned DESC, userEditedTimestamp DESC")
    fun getNotesByArchiveStatus(archived: Boolean): Flow<List<Note>>

    @Query("SELECT * FROM notes ORDER BY isPinned DESC, userEditedTimestamp DESC")
    suspend fun getAllNotesOnce(): List<Note>

    @Query("SELECT * FROM notes WHERE id = :id LIMIT 1")
    suspend fun getNoteById(id: Int): Note?

    @Query("""
        SELECT * FROM notes
        WHERE title = :title
          AND content = :content
          AND isChecklist = :isChecklist
          AND colorHex = :colorHex
          AND userEditedTimestamp = :userEditedTimestamp
          AND isArchived = :isArchived
          AND isPinned = :isPinned
        LIMIT 1
    """)
    suspend fun findImportedDuplicates(
        title: String,
        content: String,
        isChecklist: Boolean,
        colorHex: String,
        userEditedTimestamp: Long,
        isArchived: Boolean,
        isPinned: Boolean
    ): List<Note>

    @Query("SELECT imagePath FROM notes WHERE imagePath IS NOT NULL")
    suspend fun getAllImagePathValues(): List<String?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: Note): Long

    @Update
    suspend fun updateNote(note: Note)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteById(id: Int)

    @Query("DELETE FROM notes")
    suspend fun deleteAllNotes()
}
