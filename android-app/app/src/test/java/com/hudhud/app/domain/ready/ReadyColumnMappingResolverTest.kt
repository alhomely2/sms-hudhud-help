package com.hudhud.app.domain.ready

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class ReadyColumnMappingResolverTest {
    private val resolver = ReadyColumnMappingResolver()

    @Test
    fun `detects MobileNo and sms txt headers in the April workbook`() {
        val headers = listOf(
            "م",
            "MobileNo",
            "إسم_المشترك",
            "sms_txt",
            "رقم_المشترك",
        )

        val mapping = resolver.resolve(headers)

        assertEquals(1, mapping.phoneColumnIndex)
        assertEquals(3, mapping.messageColumnIndex)
        assertEquals("MobileNo", mapping.phoneColumnName)
        assertEquals("sms_txt", mapping.messageColumnName)
    }

    @Test
    fun `detects normalized Arabic column names`() {
        val mapping = resolver.resolve(listOf("الاسم", "رقم الهاتف", "نص الرسالة"))

        assertEquals(1, mapping.phoneColumnIndex)
        assertEquals(2, mapping.messageColumnIndex)
    }

    @Test
    fun `supports explicit column names when automatic aliases are unavailable`() {
        val mapping = resolver.resolve(
            headers = listOf("destination", "content"),
            phoneColumnName = "destination",
            messageColumnName = "content",
        )

        assertEquals(0, mapping.phoneColumnIndex)
        assertEquals(1, mapping.messageColumnIndex)
        assertFalse(resolver.hasKnownColumns(listOf("destination", "content")))
    }

    @Test
    fun `rejects missing and ambiguous column mappings`() {
        assertThrows(ReadyColumnMappingException::class.java) {
            resolver.resolve(listOf("MobileNo", "body"))
        }
        assertThrows(ReadyColumnMappingException::class.java) {
            resolver.resolve(listOf("MobileNo", "mobile", "sms_txt"))
        }
    }
}
