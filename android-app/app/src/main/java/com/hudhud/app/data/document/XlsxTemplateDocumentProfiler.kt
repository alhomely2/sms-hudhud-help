package com.hudhud.app.data.document

import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.util.XMLHelper
import org.apache.poi.xssf.eventusermodel.XSSFReader
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler
import org.xml.sax.InputSource
import java.io.InputStream
import java.util.Locale
import java.util.TreeMap

/** One spreadsheet column discovered in a template document. */
data class TemplateDocumentColumn(
    val name: String,
    val filledRows: Int,
    val distinctValues: Int,
    val sampleValues: List<String>,
) {
    /** A column that repeats one value in every filled row is inserted as a fixed value. */
    val isFixed: Boolean get() = filledRows > 0 && distinctValues == 1

    val sampleValue: String get() = sampleValues.firstOrNull().orEmpty()
}

data class TemplateDocumentProfile(
    val sheetName: String,
    val dataRows: Int,
    val columns: List<TemplateDocumentColumn>,
) {
    val fixedColumns: List<TemplateDocumentColumn> get() = columns.filter { it.isFixed }

    val variableColumns: List<TemplateDocumentColumn> get() = columns.filterNot { it.isFixed }
}

open class TemplateDocumentException(message: String) : Exception(message)

/**
 * Reads the first sheet of a template document and classifies its columns.
 *
 * The first non-empty row is the header row. Columns whose data repeats a
 * single value are offered as fixed values; the rest are offered as variables
 * that are read from each document row when a campaign is prepared.
 */
class XlsxTemplateDocumentProfiler {
    fun profile(input: InputStream): TemplateDocumentProfile {
        var profile: TemplateDocumentProfile? = null
        openWorkbook(input).use { workbook ->
            if (!workbook.sheetIterator.hasNext()) {
                throw TemplateDocumentException("ملف Excel لا يحتوي على أوراق.")
            }
            val sheetInput = workbook.sheetIterator.next()
            val sheetName = workbook.sheetIterator.sheetName
            val handler = ColumnProfileHandler()
            parseSheet(workbook.reader, sheetInput, handler)
            profile = handler.result(sheetName)
        }
        return profile ?: throw TemplateDocumentException("تعذر قراءة أعمدة المستند.")
    }

    private fun openWorkbook(input: InputStream): OpenWorkbook {
        val packageFile = OPCPackage.open(input)
        try {
            val reader = XSSFReader(packageFile).apply {
                setUseReadOnlySharedStringsTable(true)
            }
            val iterator = reader.sheetsData as? XSSFReader.SheetIterator
                ?: throw TemplateDocumentException("تعذر قراءة أوراق ملف Excel.")
            if (!iterator.hasNext()) {
                throw TemplateDocumentException("ملف Excel لا يحتوي على أوراق.")
            }
            return OpenWorkbook(packageFile, reader, iterator)
        } catch (error: Exception) {
            packageFile.close()
            throw error
        }
    }

    private fun parseSheet(
        reader: XSSFReader,
        input: InputStream,
        handler: XSSFSheetXMLHandler.SheetContentsHandler,
    ) {
        input.use { sheetInput ->
            val sheetHandler = XSSFSheetXMLHandler(
                reader.stylesTable,
                reader.sharedStringsTable,
                handler,
                DataFormatter(Locale.US),
                false,
            )
            XMLHelper.newXMLReader().apply {
                contentHandler = sheetHandler
                parse(InputSource(sheetInput))
            }
        }
    }

    private class OpenWorkbook(
        private val packageFile: OPCPackage,
        val reader: XSSFReader,
        val sheetIterator: XSSFReader.SheetIterator,
    ) : AutoCloseable {
        override fun close() = packageFile.close()
    }

    private class ColumnProfileHandler : XSSFSheetXMLHandler.SheetContentsHandler {
        private val currentRow = TreeMap<Int, String>()
        private var headers: List<String>? = null
        private var dataRows = 0
        private val filledRows = mutableMapOf<Int, Int>()
        private val distinctCounts = mutableMapOf<Int, Int>()
        private val samples = mutableMapOf<Int, MutableList<String>>()

        override fun startRow(rowNum: Int) = currentRow.clear()

        override fun cell(
            cellReference: String,
            formattedValue: String,
            comment: org.apache.poi.xssf.usermodel.XSSFComment?,
        ) {
            val value = formattedValue.trim()
            if (value.isNotEmpty()) currentRow[columnIndex(cellReference)] = value
        }

        override fun endRow(rowNum: Int) {
            if (currentRow.isEmpty()) return
            val existingHeaders = headers
            if (existingHeaders == null) {
                val width = currentRow.lastKey() + 1
                headers = (0 until width).map { index ->
                    currentRow[index] ?: "عمود ${columnLabel(index)}"
                }
                return
            }

            dataRows++
            if (currentRow.lastKey() + 1 > existingHeaders.size) {
                val width = currentRow.lastKey() + 1
                headers = existingHeaders + (existingHeaders.size until width).map {
                    "عمود ${columnLabel(it)}"
                }
            }
            currentRow.forEach { (index, value) ->
                filledRows[index] = (filledRows[index] ?: 0) + 1
                val seen = samples.getOrPut(index) { mutableListOf() }
                if (value !in seen) {
                    distinctCounts[index] = (distinctCounts[index] ?: 0) + 1
                    if (seen.size < MAX_SAMPLES) seen += value
                }
            }
        }

        fun result(sheetName: String): TemplateDocumentProfile {
            val headers = headers
                ?: throw TemplateDocumentException("لم يتم العثور على صف عناوين في المستند.")
            val columns = headers.indices.map { index ->
                TemplateDocumentColumn(
                    name = headers[index],
                    filledRows = filledRows[index] ?: 0,
                    distinctValues = distinctCounts[index] ?: 0,
                    sampleValues = samples[index].orEmpty().toList(),
                )
            }
            // Placeholder names address columns by name, so duplicated headers cannot be told apart.
            // A header without any data is not offered because it cannot fill a message.
            return TemplateDocumentProfile(
                sheetName = sheetName,
                dataRows = dataRows,
                columns = columns
                    .filter { it.filledRows > 0 }
                    .distinctBy { it.name },
            )
        }

        private companion object {
            const val MAX_SAMPLES = 3
        }
    }

    private companion object {
        fun columnIndex(cellReference: String): Int {
            var index = 0
            for (character in cellReference) {
                if (!character.isLetter()) break
                index = index * 26 + character.uppercaseChar().code - 'A'.code + 1
            }
            return index - 1
        }

        fun columnLabel(columnIndex: Int): String {
            var value = columnIndex + 1
            val label = StringBuilder()
            while (value > 0) {
                val remainder = (value - 1) % 26
                label.append(('A'.code + remainder).toChar())
                value = (value - 1) / 26
            }
            return label.reverse().toString()
        }
    }
}
