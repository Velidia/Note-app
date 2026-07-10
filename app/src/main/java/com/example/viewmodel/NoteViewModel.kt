package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.Note
import com.example.data.NoteRepository
import com.example.util.KeepParser
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val MAX_JSON_IMPORT_BYTES = 10L * 1024L * 1024L
private const val MAX_IMAGE_BYTES = 25L * 1024L * 1024L

data class NoteEditorState(
    val note: Note,
    val pendingChecklistItem: String = "",
    val isCopyingImages: Boolean = false
)

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NoteRepository
    private val prefs = application.getSharedPreferences("keep_notes_prefs", Context.MODE_PRIVATE)
    private val dataMutex = Mutex()

    val darkModeOption = MutableStateFlow(prefs.getString("dark_mode_option", "system") ?: "system")
    val currentTab = MutableStateFlow("notes")
    val showArchived = MutableStateFlow(false)
    val searchQuery = MutableStateFlow("")
    val importResult = MutableStateFlow<String?>(null)
    val backupInProgress = MutableStateFlow(false)
    val editorState = MutableStateFlow<NoteEditorState?>(null)

    init {
        val database = AppDatabase.getDatabase(application)
        repository = NoteRepository(database.noteDao())
        viewModelScope.launch(Dispatchers.IO) {
            dataMutex.withLock {
                cleanupOrphanedImageDirectoryLocked()
            }
        }
    }

    fun setDarkModeOption(option: String) {
        darkModeOption.value = option
        prefs.edit().putString("dark_mode_option", option).apply()
    }

    fun openEditor(note: Note) {
        editorState.value = NoteEditorState(note)
    }

    fun updateEditorNote(note: Note) {
        val current = editorState.value ?: return
        editorState.value = current.copy(note = note)
    }

    fun updatePendingChecklistItem(text: String) {
        val current = editorState.value ?: return
        editorState.value = current.copy(pendingChecklistItem = text)
    }

    fun closeEditor() {
        if (editorState.value?.isCopyingImages == true) return
        editorState.value = null
    }

    fun addImagesToEditor(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val initialState = editorState.value ?: return
        if (initialState.isCopyingImages) return
        editorState.value = initialState.copy(isCopyingImages = true)

        viewModelScope.launch(Dispatchers.IO) {
            val copiedPaths = mutableListOf<String>()
            var failedCount = 0
            try {
                dataMutex.withLock {
                    val app = getApplication<Application>()
                    val resolver = app.contentResolver
                    val imagesDir = File(app.filesDir, "keep_images").apply { mkdirs() }

                    uris.forEach { uri ->
                        var localFile: File? = null
                        try {
                            val extension = MimeTypeMap.getSingleton()
                                .getExtensionFromMimeType(resolver.getType(uri))
                                ?.takeIf { it.matches(Regex("[A-Za-z0-9]{1,10}")) }
                                ?: "jpg"
                            localFile = File(
                                imagesDir,
                                "local_img_${System.currentTimeMillis()}_${java.util.UUID.randomUUID()}.$extension"
                            )
                            val copied = resolver.openInputStream(uri)?.use { input ->
                                localFile.outputStream().use { output ->
                                    copyWithLimit(input, output, MAX_IMAGE_BYTES)
                                }
                                true
                            } ?: false
                            if (copied) {
                                copiedPaths += localFile.absolutePath
                            } else {
                                localFile.delete()
                                failedCount++
                            }
                        } catch (_: Exception) {
                            localFile?.delete()
                            failedCount++
                        }
                    }

                    val current = editorState.value
                    if (current == null) {
                        copiedPaths.forEach { File(it).delete() }
                    } else {
                        val imagePaths = (current.note.imageFiles() + copiedPaths).distinct()
                        editorState.value = current.copy(
                            note = current.note.copy(
                                imagePath = imagePaths.takeIf { it.isNotEmpty() }?.joinToString(",")
                            ),
                            isCopyingImages = false
                        )
                    }
                }
                if (failedCount > 0) {
                    importResult.value = "$failedCount images failed to copy or exceeded the 25 MB limit"
                }
            } catch (exception: Exception) {
                copiedPaths.forEach { File(it).delete() }
                editorState.value = editorState.value?.copy(isCopyingImages = false)
                importResult.value = "Failed to add image: ${exception.message ?: "berkas tidak dapat diproses"}"
            }
        }
    }

    val rawNotes = repository.allNotes

    val notesState: StateFlow<List<Note>> = combine(
        rawNotes,
        showArchived,
        searchQuery,
        currentTab
    ) { notes, isArchivedState, query, tab ->
        notes.filter { note ->
            val matchesArchive = note.isArchived == isArchivedState
            val matchesTab = when (tab) {
                "tasks" -> note.isChecklist
                else -> true
            }
            val matchesSearch = query.isBlank() ||
                note.title.contains(query, ignoreCase = true) ||
                note.content.contains(query, ignoreCase = true)

            matchesArchive && matchesTab && matchesSearch
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun saveNote(note: Note) {
        if (note.id == 0 && note.isBlankDraft()) return

        viewModelScope.launch(Dispatchers.IO) {
            dataMutex.withLock {
                saveNoteLocked(note)
            }
        }
    }

    fun deleteNote(note: Note) {
        viewModelScope.launch(Dispatchers.IO) {
            dataMutex.withLock {
                val storedNote = repository.getNoteById(note.id)
                repository.deleteById(note.id)
                cleanupUnreferencedImagesLocked(storedNote?.imageFiles().orEmpty() + note.imageFiles())
            }
        }
    }

    fun togglePin(note: Note) {
        if (note.id == 0) return
        viewModelScope.launch(Dispatchers.IO) {
            dataMutex.withLock {
                saveNoteLocked(
                    note.copy(
                        isPinned = !note.isPinned,
                        userEditedTimestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun updateColor(note: Note, colorHex: String) {
        if (note.id == 0) return
        viewModelScope.launch(Dispatchers.IO) {
            dataMutex.withLock {
                saveNoteLocked(
                    note.copy(
                        colorHex = colorHex,
                        userEditedTimestamp = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    fun importKeepJsonContent(jsonStr: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (jsonStr.length > MAX_JSON_IMPORT_BYTES ||
                jsonStr.toByteArray(Charsets.UTF_8).size > MAX_JSON_IMPORT_BYTES
            ) {
                importResult.value = "JSON melebihi batas 10 MB"
                return@launch
            }

            dataMutex.withLock {
                val app = getApplication<Application>()
                val imagesDir = File(app.filesDir, "keep_images")
                val parsed = KeepParser.parseKeepJson(jsonStr, imagesDir)
                if (parsed == null) {
                    importResult.value = "Invalid Keep JSON or note is in Trash"
                    return@withLock
                }

                val inserted = repository.insertIfNew(parsed)
                if (!inserted) cleanupUnreferencedImagesLocked(parsed.imageFiles())
                importResult.value = if (inserted) {
                    "Keep note imported successfully"
                } else {
                    "This note was already imported"
                }
            }
        }
    }

    fun importFileFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dataMutex.withLock {
                    val context = getApplication<Application>()
                    val contentResolver = context.contentResolver
                    var displayName = ""
                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (cursor.moveToFirst() && nameIndex != -1) {
                            displayName = cursor.getString(nameIndex).orEmpty()
                        }
                    }
                    val mimeType = contentResolver.getType(uri).orEmpty()

                    val inputStream = contentResolver.openInputStream(uri)
                    if (inputStream == null) {
                        importResult.value = "Failed to open selected file"
                        return@withLock
                    }

                    inputStream.buffered().use { stream ->
                        stream.mark(4)
                        val signature = ByteArray(4)
                        val signatureLength = stream.read(signature)
                        stream.reset()
                        val hasZipSignature = signatureLength >= 2 &&
                            signature[0] == 'P'.code.toByte() &&
                            signature[1] == 'K'.code.toByte()
                        val isZip = hasZipSignature ||
                            displayName.endsWith(".zip", ignoreCase = true) ||
                            mimeType.equals("application/zip", ignoreCase = true) ||
                            mimeType.equals("application/x-zip-compressed", ignoreCase = true)

                        if (isZip) {
                            val importedNotes = KeepParser.parseKeepZip(stream, context)
                            val (inserted, skipped) = importNotesLocked(importedNotes)
                            importResult.value = when {
                                inserted == 0 && skipped > 0 ->
                                    "All $skipped notes were already imported"
                                inserted == 0 ->
                                    "No valid Keep notes found in the ZIP"
                                skipped > 0 ->
                                    "$inserted notes imported, $skipped duplicates skipped"
                                else ->
                                    "Successfully imported $inserted notes from ZIP"
                            }
                        } else {
                            val jsonStr = readTextWithLimit(stream, MAX_JSON_IMPORT_BYTES)
                            val note = KeepParser.parseKeepJson(
                                jsonStr,
                                File(context.filesDir, "keep_images").apply { mkdirs() }
                            )
                            if (note == null) {
                                importResult.value = "Invalid Keep JSON or note is in Trash"
                            } else {
                                val inserted = repository.insertIfNew(note)
                                if (!inserted) cleanupUnreferencedImagesLocked(note.imageFiles())
                                importResult.value = if (inserted) {
                                    "Successfully imported: '${note.title.ifBlank { "Tanpa Judul" }}'"
                                } else {
                                    "This note was already imported"
                                }
                            }
                        }
                    }
                }
            } catch (exception: Exception) {
                importResult.value = "Import error: ${exception.message ?: "berkas tidak dapat diproses"}"
            }
        }
    }

    fun exportBackupToUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            backupInProgress.value = true
            try {
                dataMutex.withLock {
                    val notes = repository.getAllNotesOnce()
                    if (notes.isEmpty()) {
                        importResult.value = "No notes to back up yet"
                        return@withLock
                    }

                    val resolver = getApplication<Application>().contentResolver
                    val outputStream = resolver.openOutputStream(uri, "w")
                    if (outputStream == null) {
                        importResult.value = "Failed to create backup file"
                        return@withLock
                    }

                    val exportedCount = outputStream.use { stream ->
                        KeepParser.exportNotesToZip(notes, stream)
                    }
                    importResult.value = "$exportedCount notes backed up successfully"
                }
            } catch (exception: Exception) {
                importResult.value = "Backup failed: ${exception.message ?: "berkas tidak dapat ditulis"}"
            } finally {
                backupInProgress.value = false
            }
        }
    }

    fun clearImporttResult() {
        importResult.value = null
    }

    fun resetDatabase() {
        viewModelScope.launch(Dispatchers.IO) {
            dataMutex.withLock {
                val imagesDir = File(getApplication<Application>().filesDir, "keep_images")
                val imageFiles = repository.getAllImagePathValues().flatMap(::splitImagePaths)
                repository.deleteAll()
                cleanupUnreferencedImagesLocked(imageFiles)
                cleanupOrphanedImageDirectoryLocked()
                importResult.value = "Local database and attachments cleared"
            }
        }
    }

    private suspend fun saveNoteLocked(note: Note) {
        if (note.id == 0) {
            repository.insert(note)
        } else {
            val previous = repository.getNoteById(note.id)
            repository.update(note)
            cleanupUnreferencedImagesLocked(
                previous?.imageFiles().orEmpty() - note.imageFiles().toSet()
            )
        }
    }

    private suspend fun importNotesLocked(notes: List<Note>): Pair<Int, Int> {
        var inserted = 0
        var skipped = 0
        val skippedImages = mutableListOf<String>()

        notes.forEach { note ->
            if (repository.insertIfNew(note)) {
                inserted++
            } else {
                skipped++
                skippedImages += note.imageFiles()
            }
        }
        cleanupUnreferencedImagesLocked(skippedImages)
        return inserted to skipped
    }

    private suspend fun cleanupUnreferencedImagesLocked(candidates: Collection<String>) {
        if (candidates.isEmpty()) return

        val referencedPaths = repository.getAllImagePathValues()
            .flatMap(::splitImagePaths)
            .mapNotNull(::canonicalPathOrNull)
            .toSet()
        val imagesDirectory = File(getApplication<Application>().filesDir, "keep_images")
            .canonicalFile

        candidates.distinct().forEach { path ->
            val imageFile = runCatching { File(path).canonicalFile }.getOrNull() ?: return@forEach
            if (imageFile.parentFile == imagesDirectory && imageFile.path !in referencedPaths) {
                imageFile.delete()
            }
        }
    }

    private suspend fun cleanupOrphanedImageDirectoryLocked() {
        val imagesDirectory = File(getApplication<Application>().filesDir, "keep_images")
        if (!imagesDirectory.isDirectory) return
        val referencedPaths = repository.getAllImagePathValues()
            .flatMap(::splitImagePaths)
            .mapNotNull(::canonicalPathOrNull)
            .toSet()
        imagesDirectory.listFiles()?.forEach { file ->
            val canonicalPath = canonicalPathOrNull(file.path)
            if (file.isFile && canonicalPath != null && canonicalPath !in referencedPaths) {
                file.delete()
            }
        }
    }

    private fun readTextWithLimit(input: InputStream, maxBytes: Long): String {
        val output = java.io.ByteArrayOutputStream()
        copyWithLimit(input, output, maxBytes)
        return output.toString(Charsets.UTF_8.name())
    }

    private fun copyWithLimit(
        input: InputStream,
        output: java.io.OutputStream,
        maxBytes: Long
    ) {
        val buffer = ByteArray(8 * 1024)
        var totalBytes = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            totalBytes += count
            if (totalBytes > maxBytes) {
                throw IllegalArgumentException("Berkas melebihi batas ${maxBytes / 1024L / 1024L} MB")
            }
            output.write(buffer, 0, count)
        }
    }

    private fun canonicalPathOrNull(path: String): String? =
        runCatching { File(path).canonicalPath }.getOrNull()

    private fun Note.imageFiles(): List<String> = splitImagePaths(imagePath)

    private fun Note.isBlankDraft(): Boolean =
        title.isBlank() && content.isBlank() && imageFiles().isEmpty()

    private fun splitImagePaths(value: String?): List<String> =
        value?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()
}
