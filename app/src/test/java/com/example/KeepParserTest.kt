package com.example

import com.example.data.Note
import com.example.util.KeepParser
import com.squareup.moshi.Moshi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepParserTest {

    private val jsonAdapter = Moshi.Builder().build().adapter(Any::class.java)

    @Test
    fun `parse rejects empty malformed and unknown json`() {
        assertNull(KeepParser.parseKeepJson(""))
        assertNull(KeepParser.parseKeepJson("   "))
        assertNull(KeepParser.parseKeepJson("{}"))
        assertNull(KeepParser.parseKeepJson("[]"))
        assertNull(KeepParser.parseKeepJson("not json"))
        assertNull(KeepParser.parseKeepJson("""{"unexpected":"value"}"""))

        assertNotNull(
            KeepParser.parseKeepJson(
                """{"title":"","textContent":"","listContent":[]}"""
            )
        )
    }

    @Test
    fun `checklist survives keep json roundtrip`() {
        val original = Note(
            title = "Groceries",
            content = "[ ] Milk\n[x] Bread",
            isChecklist = true,
            colorHex = "#C8E6C9",
            userEditedTimestamp = 1_725_000_123_456L,
            isArchived = true,
            isPinned = true
        )

        val parsed = KeepParser.parseKeepJson(KeepParser.exportToKeepJson(original))

        assertNotNull(parsed)
        assertEquals(original.title, parsed?.title)
        assertEquals(original.content, parsed?.content)
        assertTrue(parsed?.isChecklist == true)
        assertEquals(original.colorHex, parsed?.colorHex)
        assertEquals(original.userEditedTimestamp, parsed?.userEditedTimestamp)
        assertTrue(parsed?.isArchived == true)
        assertTrue(parsed?.isPinned == true)
        val items = parsed?.getChecklistItems().orEmpty()
        assertEquals(2, items.size)
        assertEquals("Milk", items[0].text)
        assertFalse(items[0].isChecked)
        assertEquals("Bread", items[1].text)
        assertTrue(items[1].isChecked)
    }

    @Test
    fun `empty checklist remains checklist after roundtrip`() {
        val original = Note(
            title = "Empty tasks",
            content = "",
            isChecklist = true,
            userEditedTimestamp = 1_725_000_123_456L
        )

        val parsed = KeepParser.parseKeepJson(KeepParser.exportToKeepJson(original))

        assertNotNull(parsed)
        assertTrue(parsed?.isChecklist == true)
        assertTrue(parsed?.getChecklistItems().isNullOrEmpty())
    }

    @Test
    fun `export serializes every comma separated attachment with matching mime type`() {
        val note = Note(
            title = "Images",
            content = "Three attachments",
            imagePath = "C:\\photos\\first.png,/tmp/second.webp,relative/third.jpg"
        )

        val json = jsonAdapter.fromJson(KeepParser.exportToKeepJson(note)) as Map<*, *>
        val attachments = (json["attachments"] as List<*>).map { it as Map<*, *> }

        assertEquals(3, attachments.size)
        assertEquals("first.png", attachments[0]["filePath"])
        assertEquals("image/png", attachments[0]["mimetype"])
        assertEquals("second.webp", attachments[1]["filePath"])
        assertEquals("image/webp", attachments[1]["mimetype"])
        assertEquals("third.jpg", attachments[2]["filePath"])
        assertEquals("image/jpeg", attachments[2]["mimetype"])
    }

    @Test
    fun `parse skips trashed note`() {
        val json = """
            {
              "title": "Deleted",
              "textContent": "Should not be imported",
              "isTrashed": true
            }
        """.trimIndent()

        assertNull(KeepParser.parseKeepJson(json))
    }
}
