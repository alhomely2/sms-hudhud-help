package com.hudhud.app.data.document

import com.hudhud.app.domain.model.MessageTemplate
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class XlsxTemplateMessageExtractorTest {
    private val extractor = XlsxTemplateMessageExtractor()

    @Test
    fun `renders template fields from each matching sheet and preserves row numbers`() {
        val messages = mutableListOf<com.hudhud.app.domain.model.MessageDraft>()
        val summary = ByteArrayInputStream(workbookBytes()).use {
            extractor.extractAll(it, template(), messages::add)
        }

        assertEquals(2, summary.matchedSheets)
        assertEquals(3, summary.totalRows)
        assertEquals(2, summary.validRows)
        assertEquals(1, summary.invalidRows)
        assertEquals(2, messages[0].sourceRow)
        assertEquals("مرحباً سارة", messages[0].text)
        assertTrue(messages[0].isValid)
        assertEquals(3, messages[1].sourceRow)
        assertFalse(messages[1].isValid)
        assertEquals(2, messages[2].sourceRow)
        assertEquals("مرحباً ليلى", messages[2].text)
    }

    @Test(expected = TemplateWorkbookException::class)
    fun `rejects workbooks without a matching phone header`() {
        ByteArrayInputStream(workbookBytes(phoneHeader = "destination")).use {
            extractor.extractAll(it, template(), {})
        }
    }

    @Test(expected = TemplateMismatchException::class)
    fun `rejects a phone-matched sheet when a template field column is missing`() {
        ByteArrayInputStream(workbookBytes(customerHeader = "Name")).use {
            extractor.extractAll(it, template(), {})
        }
    }

    @Test(expected = TemplateMismatchException::class)
    fun `rejects duplicate selected column headers`() {
        ByteArrayInputStream(workbookBytes(customerHeader = "MobileNo")).use {
            extractor.extractAll(it, template(), {})
        }
    }

    @Test
    fun `fixed values are filled without a matching document column`() {
        val messages = mutableListOf<com.hudhud.app.domain.model.MessageDraft>()
        val fixedTemplate = template().copy(
            body = "فاتورة {{Month}} — مرحباً {{Name}}",
            variableColumns = mapOf("Month" to "Month", "Name" to "Customer"),
            fixedValues = mapOf("Month" to "أكتوبر 2026"),
        )
        val summary = ByteArrayInputStream(workbookBytes()).use {
            extractor.extractAll(it, fixedTemplate, messages::add)
        }

        assertEquals(2, summary.matchedSheets)
        assertEquals("فاتورة أكتوبر 2026 — مرحباً سارة", messages[0].text)
        assertEquals("فاتورة أكتوبر 2026 — مرحباً ليلى", messages[2].text)
        assertTrue(messages[0].isValid)
    }

    private fun template() = MessageTemplate(
        name = "Greeting",
        body = "مرحباً {{Name}}",
        phoneColumn = "MobileNo",
        variableColumns = mapOf("Name" to "Customer"),
    )

    private fun workbookBytes(
        phoneHeader: String = "MobileNo",
        customerHeader: String = "Customer",
    ): ByteArray {
        val output = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("First")
            val headers = sheet.createRow(0)
            headers.createCell(0).setCellValue(phoneHeader)
            headers.createCell(1).setCellValue(customerHeader)
            sheet.createRow(1).apply {
                createCell(0).setCellValue("966500000001")
                createCell(1).setCellValue("سارة")
            }
            sheet.createRow(2).apply {
                createCell(0).setCellValue("966500000002")
                createCell(1).setCellValue("")
            }

            val secondSheet = workbook.createSheet("Second")
            secondSheet.createRow(0).apply {
                createCell(0).setCellValue(phoneHeader)
                createCell(1).setCellValue(customerHeader)
            }
            secondSheet.createRow(1).apply {
                createCell(0).setCellValue("966500000003")
                createCell(1).setCellValue("ليلى")
            }
            workbook.write(output)
        }
        return output.toByteArray()
    }
}
