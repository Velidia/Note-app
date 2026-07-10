package com.example

import com.example.data.ChecklistItem
import com.example.data.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteModelTest {
    @Test
    fun `checklist content roundtrips without losing state`() {
        val content = Note.createFromChecklist(
            listOf(
                ChecklistItem("Belanja susu"),
                ChecklistItem("Bayar tagihan", isChecked = true)
            )
        )

        val items = Note(
            title = "Hari ini",
            content = content,
            isChecklist = true
        ).getChecklistItems()

        assertEquals(2, items.size)
        assertEquals("Belanja susu", items[0].text)
        assertFalse(items[0].isChecked)
        assertEquals("Bayar tagihan", items[1].text)
        assertTrue(items[1].isChecked)
    }
}
