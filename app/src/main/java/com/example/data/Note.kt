package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String, // Holds plain text or markdown checklist items format like "[ ] Milk\n[x] Bread"
    val isChecklist: Boolean = false,
    val colorHex: String = "#FFFFFF", // Theme-aligned custom or Keep note background color
    val userEditedTimestamp: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
    val isPinned: Boolean = false,
    val imagePath: String? = null
) {
    fun getChecklistItems(): List<ChecklistItem> {
        if (!isChecklist || content.isEmpty()) return emptyList()
        return content.lines().filter { it.isNotEmpty() }.map { line ->
            when {
                line.startsWith("[x] ") -> ChecklistItem(line.substring(4), true)
                line.startsWith("[ ] ") -> ChecklistItem(line.substring(4), false)
                else -> ChecklistItem(line, false) // Fallback for raw lines
            }
        }
    }

    companion object {
        fun createFromChecklist(items: List<ChecklistItem>): String {
            return items.joinToString("\n") { item ->
                if (item.isChecked) "[x] ${item.text}" else "[ ] ${item.text}"
            }
        }
    }
}

data class ChecklistItem(
    val text: String,
    val isChecked: Boolean = false
)
