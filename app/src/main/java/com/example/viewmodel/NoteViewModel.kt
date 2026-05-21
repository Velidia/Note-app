package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Note
import com.example.data.NoteRepository
import com.example.util.KeepParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NoteRepository
    
    // Persistent App preferences for theme selection
    private val prefs = application.getSharedPreferences("keep_notes_prefs", Context.MODE_PRIVATE)
    val darkModeOption = MutableStateFlow(prefs.getString("dark_mode_option", "system") ?: "system")

    init {
        val database = AppDatabase.getDatabase(application)
        repository = NoteRepository(database.noteDao())
    }

    fun setDarkModeOption(option: String) {
        darkModeOption.value = option
        prefs.edit().putString("dark_mode_option", option).apply()
    }

    // Active bottom navigation tab: "catatan" (Notes), "tugas" (Checklists/Tasks), "setelan" (Settings/Keep Import)
    val currentTab = MutableStateFlow("catatan")

    // Filter to show archived notes
    val showArchived = MutableStateFlow(false)

    // Live search query
    val searchQuery = MutableStateFlow("")

    // Raw notes from Room
    val rawNotes = repository.allNotes

    // Derived, filtered list of notes matching current settings/tab
    val notesState: StateFlow<List<Note>> = combine(
        rawNotes,
        showArchived,
        searchQuery,
        currentTab
    ) { notes, isArchivedState, query, tab ->
        notes.filter { note ->
            // Filter archived status
            val matchesArchive = (note.isArchived == isArchivedState)
            
            // Filter by checklist tab or general note tab
            val matchesTab = when (tab) {
                "tugas" -> note.isChecklist // only show checklists/tasks on task tab
                else -> true // show everything on notes tab
            }

            // Text search matches title or body content
            val matchesSearch = if (query.isEmpty()) {
                true
            } else {
                note.title.contains(query, ignoreCase = true) || 
                note.content.contains(query, ignoreCase = true)
            }

            matchesArchive && matchesTab && matchesSearch
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Notification toast result
    val importResult = MutableStateFlow<String?>(null)

    fun saveNote(note: Note) {
        viewModelScope.launch {
            if (note.id == 0) {
                repository.insert(note)
            } else {
                repository.update(note)
            }
        }
    }

    fun deleteNote(noteId: Int) {
        viewModelScope.launch {
            repository.deleteById(noteId)
        }
    }

    fun togglePin(note: Note) {
         viewModelScope.launch {
             repository.update(note.copy(isPinned = !note.isPinned, userEditedTimestamp = System.currentTimeMillis()))
         }
    }

    fun toggleArchive(note: Note) {
         viewModelScope.launch {
             repository.update(note.copy(isArchived = !note.isArchived, userEditedTimestamp = System.currentTimeMillis()))
         }
    }

    fun updateColor(note: Note, colorHex: String) {
        viewModelScope.launch {
            repository.update(note.copy(colorHex = colorHex))
        }
    }

    fun importKeepJsonContent(jsonStr: String): Boolean {
        val app = getApplication<Application>()
        val imagesDir = java.io.File(app.filesDir, "keep_images")
        val parsed = KeepParser.parseKeepJson(jsonStr, imagesDir)
        return if (parsed != null) {
            viewModelScope.launch {
                repository.insert(parsed)
            }
            importResult.value = "Catatan Keep berhasil diimpor!"
            true
        } else {
            importResult.value = "Format JSON Keep tidak valid"
            false
        }
    }

    fun importFileFromUri(uri: Uri) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val contentResolver = context.contentResolver
                
                // Get display name or extension of the file
                var isZip = false
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (cursor.moveToFirst() && nameIndex != -1) {
                        val displayName = cursor.getString(nameIndex)
                        if (displayName.endsWith(".zip", ignoreCase = true)) {
                            isZip = true
                        }
                    }
                }

                val inputStream = contentResolver.openInputStream(uri)
                if (inputStream != null) {
                    val imagesDir = java.io.File(context.filesDir, "keep_images").apply { mkdirs() }
                    if (isZip) {
                        val importedNotes = KeepParser.parseKeepZip(inputStream, context)
                        var count = 0
                        importedNotes.forEach { note ->
                            repository.insert(note)
                            count++
                        }
                        importResult.value = "Berhasil mengimpor $count catatan dari ZIP Google Keep!"
                    } else {
                        // Otherwise assume JSON Keep single note takeout
                        val jsonStr = inputStream.bufferedReader().readText()
                        val note = KeepParser.parseKeepJson(jsonStr, imagesDir)
                        if (note != null) {
                            repository.insert(note)
                            importResult.value = "Berhasil mengimpor: '${note.title}'"
                        } else {
                            importResult.value = "Format JSON Keep tidak sesuai atau tidak valid"
                        }
                    }
                } else {
                    importResult.value = "Gagal memproses berkas terpilih"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                importResult.value = "Kesalahan impor: ${e.message}"
            }
        }
    }

    fun clearImportResult() {
        importResult.value = null
    }

    fun resetDatabase() {
        viewModelScope.launch {
            repository.deleteAll()
            importResult.value = "Basis data lokal dikosongkan"
        }
    }
}
