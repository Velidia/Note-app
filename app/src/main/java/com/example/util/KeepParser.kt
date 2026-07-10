package com.example.util

import android.content.Context
import com.example.data.ChecklistItem
import com.example.data.Note
import com.squareup.moshi.Moshi
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object KeepParser {

    private const val MAX_ZIP_ENTRIES = 10_000
    private const val MAX_ENTRY_NAME_LENGTH = 1_024
    private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 100L * 1024L * 1024L
    private const val MAX_JSON_BYTES = 10L * 1024L * 1024L
    private const val MAX_IMAGE_BYTES = 25L * 1024L * 1024L
    private const val BUFFER_SIZE = 8 * 1024
    private const val DEFAULT_MIME_TYPE = "application/octet-stream"

    private val jsonAdapter = Moshi.Builder()
        .build()
        .adapter(Any::class.java)
        .indent("  ")

    /**
     * Parses a single Google Keep Takeout JSON string into our local Note entity.
     */
    fun parseKeepJson(jsonStr: String, localImagesDir: File? = null): Note? {
        return parseKeepJsonInternal(jsonStr) { filePath ->
            resolveLocalImage(localImagesDir, filePath)
        }
    }

    private fun parseKeepJsonInternal(
        jsonStr: String,
        attachmentResolver: (String) -> File?
    ): Note? {
        if (jsonStr.isBlank()) return null

        return try {
            val json = jsonAdapter.fromJson(jsonStr) as? Map<*, *> ?: return null
            if (!isRecognizedKeepNote(json) || json.booleanValue("isTrashed")) {
                return null
            }
            if (!hasValidArrayValue(json, "listContent") ||
                !hasValidArrayValue(json, "attachments")
            ) {
                return null
            }

            val title = json.stringValue("title")
            val isArchived = json.booleanValue("isArchived")
            val isPinned = json.booleanValue("isPinned")

            // Map color of Keep to clean minimalist soft background color
            val colorHex = mapKeepColorToHex(json.stringValue("color", "DEFAULT"))

            val timestampUsec = json.longValue("userEditedTimestampUsec")
            val timestamp = if (timestampUsec > 0L) {
                timestampUsec / 1000L
            } else {
                System.currentTimeMillis()
            }

            val textContent = json.stringValue("textContent")
            val listContent = json["listContent"] as? List<*>
            val isChecklist = json.booleanValue("isChecklist") || !listContent.isNullOrEmpty()

            val resolvedImagePaths = mutableListOf<String>()
            val attachments = json["attachments"] as? List<*>
            attachments.orEmpty().forEach { attachmentValue ->
                val attachment = attachmentValue as? Map<*, *> ?: return@forEach
                val filePath = attachment.stringValue("filePath").trim()
                if (filePath.isEmpty()) return@forEach

                val imageFile = attachmentResolver(filePath)
                if (imageFile?.isFile == true) {
                    resolvedImagePaths.add(imageFile.absolutePath)
                }
            }
            val resolvedImagePath = resolvedImagePaths
                .distinct()
                .takeIf { it.isNotEmpty() }
                ?.joinToString(",")

            if (isChecklist) {
                val items = listContent.orEmpty().mapNotNull { itemValue ->
                    val item = itemValue as? Map<*, *> ?: return@mapNotNull null
                    ChecklistItem(
                        text = item.stringValue("text"),
                        isChecked = item.booleanValue("isChecked")
                    )
                }
                Note(
                    title = title,
                    content = Note.createFromChecklist(items),
                    isChecklist = true,
                    colorHex = colorHex,
                    userEditedTimestamp = timestamp,
                    isArchived = isArchived,
                    isPinned = isPinned,
                    imagePath = resolvedImagePath
                )
            } else {
                Note(
                    title = title,
                    content = textContent,
                    isChecklist = false,
                    colorHex = colorHex,
                    userEditedTimestamp = timestamp,
                    isArchived = isArchived,
                    isPinned = isPinned,
                    imagePath = resolvedImagePath
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Serializes a single Note entity into a Google Keep Takeout JSON compatible string.
     */
    fun exportToKeepJson(note: Note): String {
        return try {
            jsonAdapter.toJson(
                buildKeepJson(note, splitImagePaths(note.imagePath).map(::portableBaseName))
            )
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Writes notes and their existing images to a ZIP compatible with [parseKeepZip].
     *
     * The returned count is the number of note JSON entries written. The supplied
     * [outputStream] is flushed but remains open.
     */
    fun exportNotesToZip(notes: List<Note>, outputStream: OutputStream): Int {
        val attachmentsBySource = linkedMapOf<String, ExportAttachment>()
        val noteAttachmentEntries = notes.map { note ->
            splitImagePaths(note.imagePath).mapNotNull { imagePath ->
                val imageFile = File(imagePath)
                if (!imageFile.isFile) return@mapNotNull null

                val sourceKey = canonicalPath(imageFile)
                attachmentsBySource.getOrPut(sourceKey) {
                    val number = attachmentsBySource.size + 1
                    val fileName = sanitizeFileName(portableBaseName(imageFile.name))
                    ExportAttachment(
                        file = imageFile,
                        entryName = "attachments/${number.toString().padStart(5, '0')}-$fileName"
                    )
                }.entryName
            }.distinct()
        }
        val attachments = attachmentsBySource.values.toList()

        require(notes.size + attachments.size <= MAX_ZIP_ENTRIES) {
            "ZIP would exceed the $MAX_ZIP_ENTRIES entry limit"
        }

        var estimatedTotalBytes = 0L
        attachments.forEach { attachment ->
            val imageSize = attachment.file.length()
            require(imageSize <= MAX_IMAGE_BYTES) {
                "Image exceeds the $MAX_IMAGE_BYTES byte limit: ${attachment.file.name}"
            }
            estimatedTotalBytes = addWithinLimit(
                estimatedTotalBytes,
                imageSize,
                MAX_TOTAL_UNCOMPRESSED_BYTES,
                "ZIP would exceed the uncompressed size limit"
            )
        }

        val jsonEntries = notes.mapIndexed { index, note ->
            val jsonBytes = jsonAdapter
                .toJson(buildKeepJson(note, noteAttachmentEntries[index]))
                .toByteArray(Charsets.UTF_8)
            require(jsonBytes.size.toLong() <= MAX_JSON_BYTES) {
                "Note JSON exceeds the $MAX_JSON_BYTES byte limit"
            }
            estimatedTotalBytes = addWithinLimit(
                estimatedTotalBytes,
                jsonBytes.size.toLong(),
                MAX_TOTAL_UNCOMPRESSED_BYTES,
                "ZIP would exceed the uncompressed size limit"
            )
            ExportJsonEntry(
                entryName = "notes/note-${(index + 1).toString().padStart(5, '0')}.json",
                bytes = jsonBytes
            )
        }

        ZipOutputStream(NonClosingOutputStream(outputStream)).use { zipStream ->
            var actualTotalBytes = jsonEntries.sumOf { it.bytes.size.toLong() }
            attachments.forEach { attachment ->
                zipStream.putNextEntry(ZipEntry(attachment.entryName))
                try {
                    var imageBytes = 0L
                    attachment.file.inputStream().use { imageStream ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val count = imageStream.read(buffer)
                            if (count < 0) break
                            imageBytes = addWithinLimit(
                                imageBytes,
                                count.toLong(),
                                MAX_IMAGE_BYTES,
                                "Image exceeds the uncompressed size limit"
                            )
                            actualTotalBytes = addWithinLimit(
                                actualTotalBytes,
                                count.toLong(),
                                MAX_TOTAL_UNCOMPRESSED_BYTES,
                                "ZIP exceeds the uncompressed size limit"
                            )
                            zipStream.write(buffer, 0, count)
                        }
                    }
                } finally {
                    zipStream.closeEntry()
                }
            }
            jsonEntries.forEach { jsonEntry ->
                zipStream.putNextEntry(ZipEntry(jsonEntry.entryName))
                try {
                    zipStream.write(jsonEntry.bytes)
                } finally {
                    zipStream.closeEntry()
                }
            }
        }

        return notes.size
    }

    /**
     * Parses a Google Keep Takeout ZIP InputStream to bulk import all notes and extract associated images.
     */
    fun parseKeepZip(inputStream: InputStream, context: Context): List<Note> {
        val imagesDir = File(context.filesDir, "keep_images")
        val jsonEntries = mutableListOf<ImporttedJsonEntry>()
        val extractedImages = mutableListOf<ExtractedImage>()
        val createdFiles = mutableListOf<File>()
        val usedLocalNames = imagesDir.listFiles()
            ?.mapTo(mutableSetOf()) { it.name.lowercase() }
            ?: mutableSetOf()
        val budget = ZipReadBudget()

        return try {
            if (!imagesDir.exists() && !imagesDir.mkdirs()) {
                throw IOException("Unable to create Keep image directory")
            }

            ZipInputStream(inputStream).use { zipStream ->
                var entryCount = 0
                while (true) {
                    val entry = zipStream.nextEntry ?: break
                    entryCount++
                    if (entryCount > MAX_ZIP_ENTRIES) {
                        throw KeepZipLimitException("ZIP contains too many entries")
                    }

                    val entryName = normalizeZipEntryName(entry.name)
                        ?: throw IOException("ZIP contains an unsafe entry name")
                    val isJson = !entry.isDirectory &&
                        entryName.endsWith(".json", ignoreCase = true)
                    val isImage = !entry.isDirectory && isImagePath(entryName)
                    val entryLimit = when {
                        isJson -> MAX_JSON_BYTES
                        isImage -> MAX_IMAGE_BYTES
                        else -> MAX_TOTAL_UNCOMPRESSED_BYTES
                    }
                    validateDeclaredEntrySize(entry, entryLimit, budget.totalBytes)

                    when {
                        isJson -> {
                            jsonEntries.add(
                                ImporttedJsonEntry(
                                    entryName = entryName,
                                    bytes = readEntryBytes(zipStream, entryLimit, budget)
                                )
                            )
                        }
                        isImage -> {
                            val localFile = createUniqueImageFile(
                                imagesDir,
                                portableBaseName(entryName),
                                usedLocalNames
                            )
                            createdFiles.add(localFile)
                            try {
                                localFile.outputStream().use { output ->
                                    copyEntry(zipStream, output, entryLimit, budget)
                                }
                                extractedImages.add(
                                    ExtractedImage(
                                        entryName = entryName,
                                        localFile = localFile
                                    )
                                )
                            } catch (exception: Exception) {
                                localFile.delete()
                                throw exception
                            }
                        }
                        else -> copyEntry(zipStream, null, entryLimit, budget)
                    }
                    zipStream.closeEntry()
                }
            }

            val notes = jsonEntries.mapNotNull { jsonEntry ->
                val jsonString = jsonEntry.bytes.toString(Charsets.UTF_8)
                parseKeepJsonInternal(jsonString) { filePath ->
                    resolveZipAttachment(filePath, jsonEntry.entryName, extractedImages)
                }
            }
            val referencedImages = notes
                .flatMap { splitImagePaths(it.imagePath) }
                .mapNotNull { path -> runCatching { File(path).canonicalPath }.getOrNull() }
                .toSet()
            createdFiles.forEach { file ->
                val canonicalPath = runCatching { file.canonicalPath }.getOrNull()
                if (canonicalPath == null || canonicalPath !in referencedImages) {
                    file.delete()
                }
            }
            notes
        } catch (exception: Exception) {
            createdFiles.forEach { it.delete() }
            exception.printStackTrace()
            emptyList()
        }
    }

    /**
     * Maps Google Keep's string color tags to real hexadecimal soft background tones
     */
    fun mapKeepColorToHex(keepColor: String): String {
        return when (keepColor.uppercase()) {
            "DEFAULT" -> "#FFFFFF"
            "RED" -> "#FFCDD2"
            "ORANGE" -> "#FFE0B2"
            "YELLOW" -> "#FFF9C4"
            "GREEN" -> "#C8E6C9"
            "TEAL" -> "#B2DFDB"
            "BLUE" -> "#BBDEFB"
            "CERULEAN" -> "#B3E5FC"
            "PURPLE" -> "#D1C4E9"
            "PINK" -> "#F8BBD0"
            "BROWN" -> "#D7CCC8"
            "GRAY" -> "#CFD8DC"
            else -> "#FFFFFF"
        }
    }

    /**
     * Inverse mapping of Clean Minimal Hex codes to Keep string color tags.
     */
    fun mapHexToKeepColor(hex: String): String {
        return when (hex.uppercase()) {
            "#FFFFFF" -> "DEFAULT"
            "#FFCDD2" -> "RED"
            "#FFE0B2" -> "ORANGE"
            "#FFF9C4" -> "YELLOW"
            "#C8E6C9" -> "GREEN"
            "#B2DFDB" -> "TEAL"
            "#BBDEFB" -> "BLUE"
            "#B3E5FC" -> "CERULEAN"
            "#D1C4E9" -> "PURPLE"
            "#F8BBD0" -> "PINK"
            "#D7CCC8" -> "BROWN"
            "#CFD8DC" -> "GRAY"
            else -> "DEFAULT"
        }
    }

    val KEEP_COLORS_MAP = listOf(
        "#FFFFFF" to "Putih (Default)",
        "#FFCDD2" to "Merah (Red)",
        "#FFE0B2" to "Jingga (Orange)",
        "#FFF9C4" to "Kuning (Yellow)",
        "#C8E6C9" to "Hijau (Green)",
        "#B2DFDB" to "Teal",
        "#BBDEFB" to "Biru (Blue)",
        "#B3E5FC" to "Biru Muda (Cerulean)",
        "#D1C4E9" to "Ungu (Purple)",
        "#F8BBD0" to "Pink",
        "#D7CCC8" to "Cokelat (Brown)",
        "#CFD8DC" to "Abu-abu (Gray)"
    )

    private fun buildKeepJson(
        note: Note,
        attachmentPaths: List<String>
    ): Map<String, Any> {
        val json = linkedMapOf<String, Any>(
            "title" to note.title,
            "isArchived" to note.isArchived,
            "isPinned" to note.isPinned,
            "color" to mapHexToKeepColor(note.colorHex),
            "userEditedTimestampUsec" to note.userEditedTimestamp * 1000L,
            "isTrashed" to false,
            "isChecklist" to note.isChecklist
        )

        if (attachmentPaths.isNotEmpty()) {
            json["attachments"] = attachmentPaths.map { attachmentPath ->
                linkedMapOf(
                    "filePath" to attachmentPath,
                    "mimetype" to mimeTypeForPath(attachmentPath)
                )
            }
        }

        if (note.isChecklist) {
            json["textContent"] = ""
            json["listContent"] = note.getChecklistItems().map { item ->
                linkedMapOf(
                    "text" to item.text,
                    "isChecked" to item.isChecked
                )
            }
        } else {
            json["textContent"] = note.content
            json["listContent"] = emptyList<Any>()
        }
        return json
    }

    private fun isRecognizedKeepNote(json: Map<*, *>): Boolean {
        if (json.isEmpty()) return false
        return json.containsKey("title") ||
            json.containsKey("textContent") ||
            json.containsKey("listContent")
    }

    private fun hasValidArrayValue(json: Map<*, *>, key: String): Boolean {
        return !json.containsKey(key) || json[key] == null || json[key] is List<*>
    }

    private fun Map<*, *>.stringValue(key: String, defaultValue: String = ""): String {
        val value = this[key] ?: return defaultValue
        return value as? String ?: value.toString()
    }

    private fun Map<*, *>.booleanValue(key: String, defaultValue: Boolean = false): Boolean {
        return when (val value = this[key]) {
            is Boolean -> value
            is String -> value.toBooleanStrictOrNull() ?: defaultValue
            is Number -> value.toInt() != 0
            else -> defaultValue
        }
    }

    private fun Map<*, *>.longValue(key: String, defaultValue: Long = 0L): Long {
        return when (val value = this[key]) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: defaultValue
            else -> defaultValue
        }
    }

    private fun splitImagePaths(imagePath: String?): List<String> {
        return imagePath
            ?.split(',')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()
    }

    private fun resolveLocalImage(localImagesDir: File?, filePath: String): File? {
        if (localImagesDir == null) return null
        val baseName = portableBaseName(filePath)
        if (baseName.isEmpty()) return null

        val exactFile = File(localImagesDir, baseName)
        if (exactFile.isFile) return exactFile
        return localImagesDir.listFiles()?.firstOrNull {
            it.isFile && it.name.equals(baseName, ignoreCase = true)
        }
    }

    private fun resolveZipAttachment(
        filePath: String,
        jsonEntryName: String,
        images: List<ExtractedImage>
    ): File? {
        val reference = normalizeZipEntryName(filePath) ?: return null
        val jsonParent = jsonEntryName.substringBeforeLast('/', "")
        val relativeReference = if (jsonParent.isEmpty()) {
            reference
        } else {
            "$jsonParent/$reference"
        }

        val exactCandidates = if ('/' in reference) {
            listOf(reference, relativeReference)
        } else {
            listOf(relativeReference, reference)
        }
        exactCandidates.forEach { candidate ->
            images.firstOrNull { it.entryName.equals(candidate, ignoreCase = true) }
                ?.let { return it.localFile }
        }

        val baseName = portableBaseName(reference)
        val baseNameMatches = images.filter {
            portableBaseName(it.entryName).equals(baseName, ignoreCase = true)
        }
        return baseNameMatches.singleOrNull()?.localFile
    }

    private fun mimeTypeForPath(path: String): String {
        return when (portableBaseName(path).substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg", "jpe" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "bmp", "dib" -> "image/bmp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            "avif" -> "image/avif"
            "tif", "tiff" -> "image/tiff"
            "svg" -> "image/svg+xml"
            "ico" -> "image/x-icon"
            else -> DEFAULT_MIME_TYPE
        }
    }

    private fun isImagePath(path: String): Boolean {
        return mimeTypeForPath(path) != DEFAULT_MIME_TYPE
    }

    private fun portableBaseName(path: String): String {
        return path.replace('\\', '/').substringAfterLast('/')
    }

    private fun canonicalPath(file: File): String {
        return try {
            file.canonicalPath
        } catch (_: IOException) {
            file.absolutePath
        }
    }

    private fun normalizeZipEntryName(name: String): String? {
        if (name.isBlank() || name.length > MAX_ENTRY_NAME_LENGTH || '\u0000' in name) {
            return null
        }
        if (name.startsWith('/') || name.startsWith('\\') ||
            name.matches(Regex("^[A-Za-z]:.*"))
        ) {
            return null
        }

        val parts = name.replace('\\', '/')
            .split('/')
            .filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." }) return null
        return parts.joinToString("/")
    }

    private fun sanitizeFileName(fileName: String): String {
        val baseName = portableBaseName(fileName)
        val extensionIndex = baseName.lastIndexOf('.').takeIf { it > 0 }
        val rawStem = extensionIndex?.let { baseName.substring(0, it) } ?: baseName
        val rawExtension = extensionIndex?.let { baseName.substring(it + 1) }.orEmpty()
        val safeStem = rawStem
            .map { character ->
                if (character.isLetterOrDigit() || character == '-' || character == '_') {
                    character
                } else {
                    '_'
                }
            }
            .joinToString("")
            .trim('_')
            .take(100)
            .ifEmpty { "attachment" }
        val safeExtension = rawExtension
            .filter { it.isLetterOrDigit() }
            .take(16)
        return if (safeExtension.isEmpty()) safeStem else "$safeStem.$safeExtension"
    }

    private fun createUniqueImageFile(
        directory: File,
        preferredName: String,
        usedNames: MutableSet<String>
    ): File {
        val safeName = sanitizeFileName(preferredName)
        val extensionIndex = safeName.lastIndexOf('.').takeIf { it > 0 }
        val stem = extensionIndex?.let { safeName.substring(0, it) } ?: safeName
        val extension = extensionIndex?.let { safeName.substring(it) }.orEmpty()
        var suffix = 0

        while (true) {
            val candidateName = if (suffix == 0) {
                "$stem$extension"
            } else {
                "$stem-$suffix$extension"
            }
            suffix++
            if (!usedNames.add(candidateName.lowercase())) continue

            val candidate = File(directory, candidateName)
            if (candidate.createNewFile()) return candidate
        }
    }

    private fun validateDeclaredEntrySize(
        entry: ZipEntry,
        entryLimit: Long,
        currentTotalBytes: Long
    ) {
        val declaredSize = entry.size
        if (declaredSize < 0L) return
        if (declaredSize > entryLimit) {
            throw KeepZipLimitException("ZIP entry exceeds its size limit")
        }
        if (declaredSize > MAX_TOTAL_UNCOMPRESSED_BYTES - currentTotalBytes) {
            throw KeepZipLimitException("ZIP exceeds the total uncompressed size limit")
        }
    }

    private fun readEntryBytes(
        input: InputStream,
        entryLimit: Long,
        budget: ZipReadBudget
    ): ByteArray {
        val output = ByteArrayOutputStream()
        copyEntry(input, output, entryLimit, budget)
        return output.toByteArray()
    }

    private fun copyEntry(
        input: InputStream,
        output: OutputStream?,
        entryLimit: Long,
        budget: ZipReadBudget
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var entryBytes = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            entryBytes = addWithinLimit(
                entryBytes,
                count.toLong(),
                entryLimit,
                "ZIP entry exceeds its uncompressed size limit"
            )
            budget.totalBytes = addWithinLimit(
                budget.totalBytes,
                count.toLong(),
                MAX_TOTAL_UNCOMPRESSED_BYTES,
                "ZIP exceeds the total uncompressed size limit"
            )
            output?.write(buffer, 0, count)
        }
    }

    private fun addWithinLimit(
        current: Long,
        amount: Long,
        limit: Long,
        message: String
    ): Long {
        if (amount < 0L || current > limit - amount) {
            throw KeepZipLimitException(message)
        }
        return current + amount
    }

    private data class ExportAttachment(
        val file: File,
        val entryName: String
    )

    private data class ExportJsonEntry(
        val entryName: String,
        val bytes: ByteArray
    )

    private data class ImporttedJsonEntry(
        val entryName: String,
        val bytes: ByteArray
    )

    private data class ExtractedImage(
        val entryName: String,
        val localFile: File
    )

    private class ZipReadBudget(var totalBytes: Long = 0L)

    private class KeepZipLimitException(message: String) : IOException(message)

    private class NonClosingOutputStream(outputStream: OutputStream) :
        FilterOutputStream(outputStream) {
        override fun close() {
            flush()
        }
    }
}
