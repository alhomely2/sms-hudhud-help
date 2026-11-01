package com.hudhud.app.data.document

import com.hudhud.app.domain.ready.ReadyColumnMappingException
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class XlsxReadyMessageExtractorTest {
    private val extractor = XlsxReadyMessageExtractor()

    @Test
    fun `inspects sparse headers and suggests the ready message columns`() {
        val structure = ByteArrayInputStream(workbookBytes()).use(extractor::inspect)

        assertEquals("Messages", structure.sheetName)
        val mapping = requireNotNull(structure.suggestedMapping)
        assertEquals("MobileNo", mapping.phoneColumnName)
        assertEquals("sms_txt", mapping.messageColumnName)
        assertEquals(1, mapping.phoneColumnIndex)
        assertEquals(3, mapping.messageColumnIndex)
    }

    @Test
    fun `inspects unknown headers for manual column selection`() {
        val structure = ByteArrayInputStream(
            workbookBytes(phoneHeader = "destination", messageHeader = "content"),
        ).use(extractor::inspect)

        assertEquals(listOf("Index", "destination", "عمود C", "content"), structure.headers)
        assertNull(structure.suggestedMapping)

        val preview = ByteArrayInputStream(
            workbookBytes(phoneHeader = "destination", messageHeader = "content"),
        ).use {
            extractor.extract(it, phoneColumnName = "destination", messageColumnName = "content")
        }
        assertEquals("966500000001", preview.messages.first().phone)
    }

    @Test
    fun `extracts messages with original worksheet row numbers and validation results`() {
        val preview = ByteArrayInputStream(workbookBytes()).use {
            extractor.extract(it, phoneColumnName = "MobileNo", messageColumnName = "sms_txt")
        }

        assertEquals(2, preview.totalRows)
        assertEquals(1, preview.validRows)
        assertEquals(1, preview.invalidRows)
        assertEquals(2, preview.messages[0].sourceRow)
        assertEquals("966500000001", preview.messages[0].phone)
        assertEquals("Ready message", preview.messages[0].text)
        assertTrue(preview.messages[0].isValid)
        assertEquals(3, preview.messages[1].sourceRow)
        assertFalse(preview.messages[1].isValid)
    }

    @Test
    fun `streams every parsed message to the campaign writer`() {
        val streamedMessages = mutableListOf<Int>()
        val summary = ByteArrayInputStream(workbookBytes()).use {
            extractor.extractAll(
                input = it,
                phoneColumnName = "MobileNo",
                messageColumnName = "sms_txt",
                onMessage = { streamedMessages += it.sourceRow },
            )
        }

        assertEquals(2, summary.totalRows)
        assertEquals(listOf(2, 3), streamedMessages)
    }

    @Test(expected = ReadyColumnMappingException::class)
    fun `rejects selected columns that are not present in the workbook`() {
        ByteArrayInputStream(workbookBytes()).use {
            extractor.extract(it, phoneColumnName = "Unknown", messageColumnName = "sms_txt")
        }
    }

    private fun workbookBytes(
        phoneHeader: String = "MobileNo",
        messageHeader: String = "sms_txt",
    ): ByteArray {
        val output = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("Messages")
            val headers = sheet.createRow(0)
            headers.createCell(0).setCellValue("Index")
            headers.createCell(1).setCellValue(phoneHeader)
            headers.createCell(3).setCellValue(messageHeader)

            val validMessage = sheet.createRow(1)
            validMessage.createCell(0).setCellValue(1.0)
            validMessage.createCell(1).setCellValue("966500000001")
            validMessage.createCell(3).setCellValue("Ready message")

            val invalidMessage = sheet.createRow(2)
            invalidMessage.createCell(1).setCellValue("")
            invalidMessage.createCell(3).setCellValue("Message without a phone")
            workbook.write(output)
        }
        return output.toByteArray()
    }
}
