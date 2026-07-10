package com.example.data

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.flow.Flow

class NoteRepository(private val noteDao: NoteDao) {
    val allNotes: Flow<List<Note>> = noteDao.getAllNotes()

    fun getNotesByArchiveStatus(archived: Boolean): Flow<List<Note>> =
        noteDao.getNotesByArchiveStatus(archived)

    suspend fun getAllNotesOnce(): List<Note> = noteDao.getAllNotesOnce()

    suspend fun getNoteById(id: Int): Note? = noteDao.getNoteById(id)

    suspend fun getAllImagePathValues(): List<String> =
        noteDao.getAllImagePathValues().filterNotNull()

    suspend fun insertIfNew(note: Note): Boolean {
        val candidates = noteDao.findImportedDuplicates(
            title = note.title,
            content = note.content,
            isChecklist = note.isChecklist,
            colorHex = note.colorHex,
            userEditedTimestamp = note.userEditedTimestamp,
            isArchived = note.isArchived,
            isPinned = note.isPinned
        )
        val attachmentSignature = note.attachmentSignature()
        if (candidates.any { it.attachmentSignature() == attachmentSignature }) return false

        noteDao.insertNote(note)
        return true
    }

    suspend fun insert(note: Note): Long = noteDao.insertNote(note)

    suspend fun update(note: Note) = noteDao.updateNote(note)

    suspend fun deleteById(id: Int) = noteDao.deleteNoteById(id)

    suspend fun deleteAll() = noteDao.deleteAllNotes()

    private fun Note.attachmentSignature(): List<String> =
        imagePath.orEmpty()
            .split(',')
            .map(String::trim)
            .filter(String::isNotEmpty)
            .map { path ->
                val file = File(path)
                if (!file.isFile) {
                    "missing"
                } else {
                    val digest = MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { input ->
                        val buffer = ByteArray(8 * 1024)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            digest.update(buffer, 0, count)
                        }
                    }
                    digest.digest().joinToString("") { byte -> "%02x".format(byte) }
                }
            }
}
