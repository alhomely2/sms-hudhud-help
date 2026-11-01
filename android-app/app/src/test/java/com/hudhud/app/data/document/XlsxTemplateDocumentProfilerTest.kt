package com.hudhud.app.data.document

import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class XlsxTemplateDocumentProfilerTest {
    private val profiler = XlsxTemplateDocumentProfiler()

    @Test
    fun `classifies repeated columns as fixed and varying columns as variables`() {
        val profile = ByteArrayInputStream(workbookBytes()).use(profiler::profile)

        assertEquals("العملاء", profile.sheetName)
        assertEquals(3, profile.dataRows)
        assertEquals(listOf("الاسم", "الجوال", "الشهر"), profile.columns.map { it.name })

        val fixed = profile.fixedColumns.single()
        assertEquals("الشهر", fixed.name)
        assertEquals("أكتوبر", fixed.sampleValue)
        assertEquals(3, fixed.filledRows)

        val variables = profile.variableColumns
        assertEquals(listOf("الاسم", "الجوال"), variables.map { it.name })
        assertEquals(3, variables.first().distinctValues)
        assertFalse(variables.any { it.isFixed })
    }

    @Test
    fun `labels blank headers and ignores columns without data`() {
        val profile = ByteArrayInputStream(
            workbookBytes(includeEmptyColumn = true),
        ).use(profiler::profile)

        assertTrue(profile.columns.any { it.name == "عمود D" })
        assertFalse(profile.columns.any { it.name == "عمود E" })
    }

    @Test(expected = TemplateDocumentException::class)
    fun `rejects a workbook without headers`() {
        val output = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            workbook.createSheet("فارغ")
            workbook.write(output)
        }
        ByteArrayInputStream(output.toByteArray()).use(profiler::profile)
    }

    private fun workbookBytes(includeEmptyColumn: Boolean = false): ByteArray {
        val output = ByteArrayOutputStream()
        XSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet("العملاء")
            val headers = listOf("الاسم", "الجوال", "الشهر", "", "بلا بيانات")
            val headerRow = sheet.createRow(0)
            headers.forEachIndexed { index, header ->
                if (header.isNotBlank()) {
                    headerRow.createCell(index).setCellValue(header)
                }
            }
            val names = listOf("سارة", "علي", "منى")
            val phones = listOf("0500000001", "0500000002", "0500000003")
            names.indices.forEach { row ->
                val sheetRow = sheet.createRow(row + 1)
                sheetRow.createCell(0).setCellValue(names[row])
                sheetRow.createCell(1).setCellValue(phones[row])
                sheetRow.createCell(2).setCellValue("أكتوبر")
                if (includeEmptyColumn) {
                    sheetRow.createCell(3).setCellValue("ثابت ${row + 1}")
                }
            }
            workbook.write(output)
        }
        return output.toByteArray()
    }
}
