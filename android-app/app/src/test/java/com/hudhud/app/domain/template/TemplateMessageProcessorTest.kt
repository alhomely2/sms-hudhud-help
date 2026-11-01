package com.hudhud.app.domain.template

import com.hudhud.app.domain.model.MessageTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateMessageProcessorTest {
    private val processor = TemplateMessageProcessor()

    @Test
    fun `renders configured fields and phone`() {
        val result = processor.process(
            headers = listOf("Phone", "Name", "Amount"),
            rows = listOf(listOf("+966500000000", "سارة", "120")),
            template = MessageTemplate(
                name = "Invoice",
                body = "مرحباً {{Name}}، المبلغ {{Amount}}",
                phoneColumn = "Phone",
                variableColumns = mapOf("Name" to "Name", "Amount" to "Amount"),
            ),
        )

        assertEquals("+966500000000", result.single().phone)
        assertEquals("مرحباً سارة، المبلغ 120", result.single().text)
        assertTrue(result.single().isValid)
    }

    @Test
    fun `renders the recipient phone column as a template field`() {
        val result = processor.process(
            headers = listOf("Phone", "Name"),
            rows = listOf(listOf("+966500000000", "سارة")),
            template = MessageTemplate(
                name = "Phone confirmation",
                body = "رقم التواصل {{Phone}} للعميل {{Name}}",
                phoneColumn = "Phone",
                variableColumns = mapOf(
                    "Phone" to "Phone",
                    "Name" to "Name",
                ),
            ),
        )

        assertEquals("+966500000000", result.single().phone)
        assertEquals("رقم التواصل +966500000000 للعميل سارة", result.single().text)
        assertTrue(result.single().isValid)
    }

    @Test
    fun `keeps missing placeholder visible and marks row invalid`() {
        val result = processor.process(
            headers = listOf("Phone", "Name"),
            rows = listOf(listOf("+966500000000", "")),
            template = MessageTemplate(
                name = "Greeting",
                body = "مرحباً {{Name}}",
                phoneColumn = "Phone",
                variableColumns = mapOf("Name" to "Name"),
            ),
        )

        assertEquals("مرحباً {{Name}}", result.single().text)
        assertFalse(result.single().isValid)
        assertTrue(result.single().errors.any { it.contains("بيانات القالب ناقصة") })
    }

    @Test
    fun `reports missing column mapping`() {
        val result = processor.process(
            headers = listOf("Phone"),
            rows = listOf(listOf("+966500000000")),
            template = MessageTemplate(
                name = "Greeting",
                body = "مرحباً {{Name}}",
                phoneColumn = "Phone",
                variableColumns = mapOf("Name" to "Customer"),
            ),
        )

        assertFalse(result.single().isValid)
        assertTrue(result.single().errors.any { it.contains("حقول القالب غير مرتبطة") })
    }

    @Test
    fun `fixed values fill every message without needing a document column`() {
        val result = processor.process(
            headers = listOf("Phone", "Name"),
            rows = listOf(
                listOf("+966500000000", "سارة"),
                listOf("+966500000001", "ليلى"),
            ),
            template = MessageTemplate(
                name = "Invoice",
                body = "فاتورة {{Month}} للسيد {{Name}}",
                phoneColumn = "Phone",
                variableColumns = mapOf("Month" to "Month", "Name" to "Name"),
                fixedValues = mapOf("Month" to "أكتوبر 2026"),
            ),
        )

        assertEquals("فاتورة أكتوبر 2026 للسيد سارة", result[0].text)
        assertEquals("فاتورة أكتوبر 2026 للسيد ليلى", result[1].text)
        assertTrue(result.all { it.isValid })
    }

    @Test
    fun `document columns exclude fixed values`() {
        val template = MessageTemplate(
            name = "Invoice",
            body = "فاتورة {{Month}} للسيد {{Name}}",
            phoneColumn = "Phone",
            variableColumns = mapOf("Month" to "Month", "Name" to "Customer"),
            fixedValues = mapOf("Month" to "أكتوبر 2026"),
        )

        assertEquals(listOf("Customer"), template.documentColumns())
    }
}
