package com.hudhud.app.data.document

import com.hudhud.app.domain.model.MessageDraft
import com.hudhud.app.domain.model.MessageTemplate
import com.hudhud.app.domain.template.TemplateMessageProcessor
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.util.XMLHelper
import org.apache.poi.xssf.eventusermodel.XSSFReader
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler
import org.xml.sax.InputSource
import java.io.InputStream
import java.util.Locale
import java.util.TreeMap

data class TemplateWorkbookSummary(
    val matchedSheets: Int,
    val totalRows: Int,
    val validRows: Int,
    val invalidRows: Int,
)

open class TemplateWorkbookException(message: String) : Exception(message)

class TemplateMismatchException(message: String) : TemplateWorkbookException(message)

class XlsxTemplateMessageExtractor(
    private val processor: TemplateMessageProcessor = TemplateMessageProcessor(),
) {
    fun extractAll(
        input: InputStream,
        template: MessageTemplate,
        onMessage: (MessageDraft) -> Unit,
    ): TemplateWorkbookSummary {
        require(template.phoneColumn.isNotBlank()) { "Template phone column must not be blank" }
        var matchedSheets = 0
        var totalRows = 0
        var validRows = 0
        var invalidRows = 0
        val missingColumns = linkedSetOf<String>()

        openWorkbook(input).use { workbook ->
            val reader = workbook.reader
            val sheetIterator = workbook.sheetIterator
            while (sheetIterator.hasNext()) {
                val sheetInput = sheetIterator.next()
                val handler = TemplateSheetHandler(template, processor) { draft ->
                    totalRows++
                    if (draft.isValid) validRows++ else invalidRows++
                    onMessage(draft)
                }
                parseSheet(reader, sheetInput, handler)
                if (handler.hasMatchingHeader) {
                    if (handler.hasCompleteMapping) {
                        matchedSheets++
                    } else {
                        missingColumns += handler.missingColumns
                    }
                }
            }
        }

        if (matchedSheets == 0) {
            if (missingColumns.isNotEmpty()) {
                throw TemplateMismatchException(
                    "الأعمدة التالية غير موجودة معاً أو عناوينها مكررة: " +
                        missingColumns.joinToString(),
                )
            }
            throw TemplateMismatchException(
                "لم يتم العثور على عمود الهاتف «${template.phoneColumn}» في أي ورقة.",
            )
        }
        return TemplateWorkbookSummary(matchedSheets, totalRows, validRows, invalidRows)
    }

    private fun openWorkbook(input: InputStream): OpenWorkbook {
        val packageFile = OPCPackage.open(input)
        try {
            val reader = XSSFReader(packageFile).apply {
                setUseReadOnlySharedStringsTable(true)
            }
            val iterator = reader.sheetsData as? XSSFReader.SheetIterator
                ?: throw TemplateWorkbookException("تعذر قراءة أوراق ملف Excel.")
            if (!iterator.hasNext()) throw TemplateWorkbookException("ملف Excel لا يحتوي على أوراق.")
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

    private class TemplateSheetHandler(
        private val template: MessageTemplate,
        private val processor: TemplateMessageProcessor,
        private val onMessage: (MessageDraft) -> Unit,
    ) : XSSFSheetXMLHandler.SheetContentsHandler {
        private val currentRow = TreeMap<Int, String>()
        private var headers: List<String>? = null
        private var sourceRow = -1
        var hasMatchingHeader = false
            private set
        var hasCompleteMapping = false
            private set
        var missingColumns: Set<String> = emptySet()
            private set

        override fun startRow(rowNum: Int) = currentRow.clear()

        override fun cell(
            cellReference: String,
            formattedValue: String,
            comment: org.apache.poi.xssf.usermodel.XSSFComment?,
        ) {
            currentRow[columnIndex(cellReference)] = formattedValue.trim()
        }

        override fun endRow(rowNum: Int) {
            if (currentRow.isEmpty()) return
            val selectedHeaders = headers
            if (selectedHeaders == null) {
                val candidate = (0..currentRow.lastKey()).map { index ->
                    currentRow[index].orEmpty().ifBlank { "عمود ${columnLabel(index)}" }
                }
                if (candidate.none { it == template.phoneColumn }) return
                headers = candidate
                sourceRow = rowNum
                hasMatchingHeader = true
                missingColumns = buildList {
                    addAll(template.documentColumns())
                    add(template.phoneColumn)
                }.distinct().filterNot(candidate::contains).toSet()
                val requiredColumns = buildList {
                    add(template.phoneColumn)
                    addAll(template.documentColumns())
                }.distinct()
                val ambiguousColumns = requiredColumns.filter { column ->
                    candidate.count { it == column } > 1
                }
                missingColumns = (missingColumns + ambiguousColumns).toSet()
                hasCompleteMapping = missingColumns.isEmpty()
                return
            }

            if (!hasCompleteMapping) return

            val selectedColumns = buildList {
                add(template.phoneColumn)
                addAll(template.documentColumns())
            }.distinct()
            if (selectedColumns.none { column ->
                    selectedHeaders.indexOf(column).let { index ->
                        index >= 0 && currentRow[index].orEmpty().isNotBlank()
                    }
                }
            ) {
                return
            }

            val rowValues = (0..maxOf(currentRow.lastKey(), selectedHeaders.lastIndex)).map { index ->
                currentRow[index].orEmpty()
            }
            onMessage(
                processor.process(
                    headers = selectedHeaders,
                    rows = listOf(rowValues),
                    template = template,
                ).single().copy(sourceRow = rowNum + 1),
            )
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
