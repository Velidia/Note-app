package com.example.util

import android.content.Context
import com.example.data.Note
import com.example.data.ChecklistItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.File
import java.util.zip.ZipInputStream

object KeepParser {

    /**
     * Parses a single Google Keep Takeout JSON string into our local Note entity.
     */
    fun parseKeepJson(jsonStr: String, localImagesDir: File? = null): Note? {
        return try {
            val json = JSONObject(jsonStr)
            val title = json.optString("title", "")
            val isArchived = json.optBoolean("isArchived", false)
            val isPinned = json.optBoolean("isPinned", false)
            
            // Map color of Keep to clean minimalist soft background color
            val color = json.optString("color", "DEFAULT")
            val colorHex = mapKeepColorToHex(color)
            
            val timestampUsec = json.optLong("userEditedTimestampUsec", 0L)
            val timestamp = if (timestampUsec > 0L) timestampUsec / 1000L else System.currentTimeMillis()
            
            val textContent = json.optString("textContent", "")
            val listContentArray = json.optJSONArray("listContent")
            
            // Extract all attachment images if available
            val attachmentsArray = json.optJSONArray("attachments")
            val resolvedImagePaths = mutableListOf<String>()
            if (attachmentsArray != null && attachmentsArray.length() > 0) {
                for (idx in 0 until attachmentsArray.length()) {
                    val attachmentObj = attachmentsArray.getJSONObject(idx)
                    val filePath = attachmentObj.optString("filePath", "")
                    if (filePath.isNotEmpty() && localImagesDir != null) {
                        val baseName = filePath.substringAfterLast('/')
                        val imageFile = File(localImagesDir, baseName)
                        if (imageFile.exists()) {
                            resolvedImagePaths.add(imageFile.absolutePath)
                        }
                    }
                }
            }
            val resolvedImagePath = if (resolvedImagePaths.isEmpty()) null else resolvedImagePaths.joinToString(",")
            
            if (listContentArray != null && listContentArray.length() > 0) {
                // Checklist note
                val items = mutableListOf<ChecklistItem>()
                for (i in 0 until listContentArray.length()) {
                    val itemDetails = listContentArray.getJSONObject(i)
                    val text = itemDetails.optString("text", "")
                    val isChecked = itemDetails.optBoolean("isChecked", false)
                    items.add(ChecklistItem(text, isChecked))
                }
                val contentString = Note.createFromChecklist(items)
                Note(
                    title = title,
                    content = contentString,
                    isChecklist = true,
                    colorHex = colorHex,
                    userEditedTimestamp = timestamp,
                    isArchived = isArchived,
                    isPinned = isPinned,
                    imagePath = resolvedImagePath
                )
            } else {
                // Regular text note
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
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Serializes a single Note entity into a Google Keep Takeout JSON compatible string.
     */
    fun exportToKeepJson(note: Note): String {
        return try {
            val json = JSONObject()
            json.put("title", note.title)
            json.put("isArchived", note.isArchived)
            json.put("isPinned", note.isPinned)
            json.put("color", mapHexToKeepColor(note.colorHex))
            json.put("userEditedTimestampUsec", note.userEditedTimestamp * 1000L)
            json.put("isTrashed", false)

            if (note.imagePath != null) {
                val attachmentsArray = JSONArray()
                val attachmentObj = JSONObject()
                val baseName = note.imagePath.substringAfterLast('/')
                attachmentObj.put("filePath", baseName)
                attachmentObj.put("mimetype", "image/jpeg")
                attachmentsArray.put(attachmentObj)
                json.put("attachments", attachmentsArray)
            }

            if (note.isChecklist) {
                json.put("textContent", "")
                val listArray = JSONArray()
                note.getChecklistItems().forEach { item ->
                    val itemObj = JSONObject()
                    itemObj.put("text", item.text)
                    itemObj.put("isChecked", item.isChecked)
                    listArray.put(itemObj)
                }
                json.put("listContent", listArray)
            } else {
                json.put("textContent", note.content)
                json.put("listContent", JSONArray())
            }
            json.toString(2)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    /**
     * Parses a Google Keep Takeout ZIP InputStream to bulk import all notes and extract associated images.
     */
    fun parseKeepZip(inputStream: InputStream, context: Context): List<Note> {
        val notes = mutableListOf<Note>()
        val jsonContents = mutableListOf<String>()
        val imagesDir = File(context.filesDir, "keep_images").apply { mkdirs() }
        
        try {
            val zipStream = ZipInputStream(inputStream)
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val baseName = entry.name.substringAfterLast('/')
                    val isJson = entry.name.endsWith(".json", ignoreCase = true)
                    val isImage = entry.name.endsWith(".jpg", ignoreCase = true) ||
                                  entry.name.endsWith(".jpeg", ignoreCase = true) ||
                                  entry.name.endsWith(".png", ignoreCase = true) ||
                                  entry.name.endsWith(".webp", ignoreCase = true) ||
                                  entry.name.endsWith(".gif", ignoreCase = true)
                    
                    if (isJson) {
                        val content = zipStream.bufferedReader().readText()
                        jsonContents.add(content)
                    } else if (isImage) {
                        try {
                            val outFile = File(imagesDir, baseName)
                            outFile.outputStream().use { outStream ->
                                zipStream.copyTo(outStream)
                            }
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }
                }
                zipStream.closeEntry()
                entry = zipStream.nextEntry
            }
            zipStream.close()
            
            // Process saved JSON contents with access to actual extracted images dir
            jsonContents.forEach { jsonStr ->
                val note = parseKeepJson(jsonStr, imagesDir)
                if (note != null) {
                    notes.add(note)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return notes
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
        "#F8BBD0" to "Merah Muda (Pink)",
        "#D7CCC8" to "Cokelat (Brown)",
        "#CFD8DC" to "Abu-abu (Gray)"
    )
}
