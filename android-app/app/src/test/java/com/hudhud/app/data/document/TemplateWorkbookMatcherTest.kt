package com.hudhud.app.data.document

import com.hudhud.app.domain.model.SavedTemplate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class TemplateWorkbookMatcherTest {
    @Test
    fun `matches template only when all required columns exist on same sheet`() {
        val result = TemplateWorkbookMatcher.match(
            sheets = listOf(
                TemplateWorkbookSheetSchema("Phones", listOf("Mobile", "Name")),
                TemplateWorkbookSheetSchema("Amounts", listOf("Amount")),
            ),
            templates = listOf(template()),
        ).single()

        assertFalse(result.isMatch)
        assertEquals(setOf("Amount"), result.missingColumns)
    }

    @Test
    fun `reports sheets that contain the complete template schema`() {
        val result = TemplateWorkbookMatcher.match(
            sheets = listOf(
                TemplateWorkbookSheetSchema("Wrong", listOf("Mobile", "Name")),
                TemplateWorkbookSheetSchema("Customers", listOf("Mobile", "Name", "Amount")),
            ),
            templates = listOf(template()),
        ).single()

        assertTrue(result.isMatch)
        assertEquals(listOf("Customers"), result.matchedSheets)
        assertTrue(result.missingColumns.isEmpty())
    }

    @Test
    fun `excludes templates whose phone header does not occur in workbook`() {
        val results = TemplateWorkbookMatcher.match(
            sheets = listOf(TemplateWorkbookSheetSchema("Customers", listOf("Phone", "Name"))),
            templates = listOf(template()),
        )

        assertTrue(results.isEmpty())
    }

    @Test
    fun `does not match a sheet with duplicate required column names`() {
        val result = TemplateWorkbookMatcher.match(
            sheets = listOf(
                TemplateWorkbookSheetSchema(
                    "Customers",
                    listOf("Mobile", "Name", "Name", "Amount"),
                ),
            ),
            templates = listOf(template()),
        ).single()

        assertFalse(result.isMatch)
        assertEquals(setOf("Name"), result.ambiguousColumns)
    }

    @Test
    fun `schema reader discovers header rows across workbook sheets`() {
        val output = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            workbook.createSheet("Invoices").apply {
                createRow(0).createCell(0).setCellValue("Monthly invoice report")
                createRow(2).apply {
                    createCell(0).setCellValue("Mobile")
                    createCell(1).setCellValue("Name")
                }
            }
            workbook.createSheet("Customers").createRow(0).apply {
                createCell(0).setCellValue("Mobile")
                createCell(1).setCellValue("Name")
                createCell(2).setCellValue("Amount")
            }
            workbook.write(output)
        }

        val schemas = ByteArrayInputStream(output.toByteArray()).use {
            XlsxTemplateSchemaReader().read(it, setOf("Mobile"))
        }

        assertEquals(2, schemas.size)
        assertEquals(listOf("Mobile", "Name"), schemas.first().columns)
        assertEquals("Customers", schemas.last().name)
        assertEquals(listOf("Mobile", "Name", "Amount"), schemas.last().columns)
    }

    private fun template() = SavedTemplate(
        id = 7L,
        name = "Reminder",
        body = "Hello {{Name}}: {{Amount}}",
        phoneColumn = "Mobile",
        variableColumns = mapOf("Name" to "Name", "Amount" to "Amount"),
        createdAt = 0L,
        updatedAt = 0L,
    )
}
