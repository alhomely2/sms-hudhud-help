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

class XlsxTemplateSchemaReader {
    fun read(
        input: InputStream,
        phoneColumnCandidates: Set<String>,
    ): List<TemplateWorkbookSheetSchema> {
        val packageFile = OPCPackage.open(input)
        try {
            val reader = XSSFReader(packageFile).apply {
                setUseReadOnlySharedStringsTable(true)
            }
            val sheets = reader.sheetsData as? XSSFReader.SheetIterator
                ?: throw TemplateWorkbookException("تعذر قراءة أوراق ملف Excel.")
            val schemas = mutableListOf<TemplateWorkbookSheetSchema>()
            while (sheets.hasNext()) {
                val sheetInput = sheets.next()
                val sheetName = sheets.sheetName
                val handler = HeaderHandler(phoneColumnCandidates)
                sheetInput.use {
                    val sheetHandler = XSSFSheetXMLHandler(
                        reader.stylesTable,
                        reader.sharedStringsTable,
                        handler,
                        DataFormatter(Locale.US),
                        false,
                    )
                    XMLHelper.newXMLReader().apply {
                        contentHandler = sheetHandler
                        parse(InputSource(it))
                    }
                }
                handler.result()?.let { headers ->
                    schemas += TemplateWorkbookSheetSchema(sheetName, headers)
                }
            }
            if (schemas.isEmpty()) {
                throw TemplateWorkbookException("لم يتم العثور على صف عناوين في ملف Excel.")
            }
            return schemas
        } finally {
            packageFile.close()
        }
    }

    private class HeaderHandler(
        private val phoneColumnCandidates: Set<String>,
    ) : XSSFSheetXMLHandler.SheetContentsHandler {
        private val row = TreeMap<Int, String>()
        var headers: List<String>? = null
            private set
        private var firstHeaders: List<String>? = null

        override fun startRow(rowNum: Int) = row.clear()

        override fun cell(
            cellReference: String,
            formattedValue: String,
            comment: org.apache.poi.xssf.usermodel.XSSFComment?,
        ) {
            row[columnIndex(cellReference)] = formattedValue.trim()
        }

        override fun endRow(rowNum: Int) {
            if (headers != null || row.isEmpty()) return
            val candidate = (0..row.lastKey()).map { column ->
                row[column].orEmpty().ifBlank { "عمود ${columnLabel(column)}" }
            }
            if (firstHeaders == null) firstHeaders = candidate
            if (phoneColumnCandidates.isEmpty() ||
                phoneColumnCandidates.any(candidate::contains)
            ) {
                headers = candidate
            }
        }

        fun result(): List<String>? = headers ?: firstHeaders

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
}
