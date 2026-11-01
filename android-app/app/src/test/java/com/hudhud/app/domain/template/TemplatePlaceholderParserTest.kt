package com.hudhud.app.domain.template

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplatePlaceholderParserTest {

    @Test
    fun `reads arabic placeholders`() {
        val names = TemplatePlaceholderParser.names("مرحباً {{الاسم}}، مبلغك {{ المبلغ }}")

        assertEquals(listOf("الاسم", "المبلغ"), names)
    }

    @Test
    fun `reads latin placeholders with underscores and digits`() {
        val names = TemplatePlaceholderParser.names("Hi {{first_name}}, order {{orderId2}}")

        assertEquals(listOf("first_name", "orderId2"), names)
    }

    @Test
    fun `trims surrounding whitespace`() {
        val names = TemplatePlaceholderParser.names("{{   الاسم   }}")

        assertEquals(listOf("الاسم"), names)
    }

    @Test
    fun `ignores unclosed placeholders`() {
        val names = TemplatePlaceholderParser.names("مرحباً {{الاسم و {{المبلغ")

        assertEquals(emptyList<String>(), names)
    }

    @Test
    fun `deduplicates repeated placeholders`() {
        val names = TemplatePlaceholderParser.names("{{الاسم}} ثم {{الاسم}}")

        assertEquals(listOf("الاسم"), names)
    }

    @Test
    fun `replaces values and reports missing names`() {
        val (text, missing) = TemplatePlaceholderParser.replace("مرحباً {{الاسم}} — {{المبلغ}}") { name ->
            if (name == "الاسم") "سارة" else null
        }

        assertEquals("مرحباً سارة — {{المبلغ}}", text)
        assertEquals(listOf("المبلغ"), missing)
    }

    @Test
    fun `treats blank values as missing so a row is never silently empty`() {
        val (text, missing) = TemplatePlaceholderParser.replace("مرحباً {{الاسم}}") { " " }

        assertEquals("مرحباً {{الاسم}}", text)
        assertTrue(missing.contains("الاسم"))
    }

    @Test
    fun `builds the editor token for a name`() {
        assertEquals("{{الاسم}}", TemplatePlaceholderParser.placeholderFor("  الاسم  "))
    }

    @Test
    fun `accepts names that the parser can read back`() {
        assertTrue(TemplatePlaceholderParser.isValidName("الشهر"))
        assertTrue(TemplatePlaceholderParser.isValidName("billing_month 2"))
    }

    @Test
    fun `rejects names with braces or line breaks`() {
        assertFalse(TemplatePlaceholderParser.isValidName(""))
        assertFalse(TemplatePlaceholderParser.isValidName("   "))
        assertFalse(TemplatePlaceholderParser.isValidName("الشهر}"))
        assertFalse(TemplatePlaceholderParser.isValidName("{الشهر"))
        assertFalse(TemplatePlaceholderParser.isValidName("الشهر\n2026"))
    }
}
