package com.hudhud.app.data.document

import com.hudhud.app.domain.model.MessageDraft
import com.hudhud.app.domain.ready.ReadyColumnMapping
import com.hudhud.app.domain.ready.ReadyColumnMappingException
import com.hudhud.app.domain.ready.ReadyColumnMappingResolver
import com.hudhud.app.domain.ready.ReadyMessageProcessor
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.util.XMLHelper
import org.apache.poi.xssf.eventusermodel.XSSFReader
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler
import org.xml.sax.InputSource
import java.io.InputStream
import java.util.Locale
import java.util.TreeMap

data class ReadyWorkbookStructure(
    val sheetName: String,
    val headers: List<String>,
    val suggestedMapping: ReadyColumnMapping?,
)

data class ReadyWorkbookPreview(
    val sheetName: String,
    val mapping: ReadyColumnMapping,
    val messages: List<MessageDraft>,
    val totalRows: Int,
    val validRows: Int,
    val invalidRows: Int,
    val previewLimitReached: Boolean,
)

data class ReadyWorkbookSummary(
    val sheetName: String,
    val mapping: ReadyColumnMapping,
    val totalRows: Int,
    val validRows: Int,
    val invalidRows: Int,
)

class ReadyWorkbookStructureException(message: String) : Exception(message)

class XlsxReadyMessageExtractor(
    private val mappingResolver: ReadyColumnMappingResolver = ReadyColumnMappingResolver(),
    private val messageProcessor: ReadyMessageProcessor = ReadyMessageProcessor(),
    private val previewLimit: Int = DEFAULT_PREVIEW_LIMIT,
) {
    init {
        require(previewLimit > 0) { "Preview limit must be positive" }
    }

    fun inspect(input: InputStream): ReadyWorkbookStructure {
        var fallbackStructure: ReadyWorkbookStructure? = null
        openWorkbook(input).use { workbook ->
            val reader = workbook.reader
            val sheetIterator = workbook.sheetIterator
            while (sheetIterator.hasNext()) {
                val sheetInput = sheetIterator.next()
                val sheetName = sheetIterator.sheetName
                val handler = HeaderInspectionHandler(mappingResolver)
                parseSheet(reader, sheetInput, handler)
                val headers = handler.headers ?: continue
                val structure = ReadyWorkbookStructure(sheetName, headers, handler.mapping)
                if (structure.suggestedMapping != null) return structure
                if (fallbackStructure == null) fallbackStructure = structure
            }
        }
        fallbackStructure?.let { return it }
        throw ReadyWorkbookStructureException(
            "لم يتم العثور على صف عناوين في ملف Excel.",
        )
    }

    fun extract(
        input: InputStream,
        phoneColumnName: String,
        messageColumnName: String,
    ): ReadyWorkbookPreview {
        val messages = ArrayList<MessageDraft>(previewLimit)
        val summary = extractAll(input, phoneColumnName, messageColumnName) { message ->
            if (messages.size < previewLimit) messages += message
        }
        return ReadyWorkbookPreview(
            sheetName = summary.sheetName,
            mapping = summary.mapping,
            messages = messages,
            totalRows = summary.totalRows,
            validRows = summary.validRows,
            invalidRows = summary.invalidRows,
            previewLimitReached = summary.totalRows > messages.size,
        )
    }

    fun extractAll(
        input: InputStream,
        phoneColumnName: String,
        messageColumnName: String,
        onMessage: (MessageDraft) -> Unit,
    ): ReadyWorkbookSummary {
        var totalRows = 0
        var validRows = 0
        var invalidRows = 0
        var selectedSheetName: String? = null
        var selectedMapping: ReadyColumnMapping? = null

        openWorkbook(input).use { workbook ->
            val reader = workbook.reader
            val sheetIterator = workbook.sheetIterator
            while (sheetIterator.hasNext()) {
                val sheetInput = sheetIterator.next()
                val sheetName = sheetIterator.sheetName
                val handler = MessageExtractionHandler(
                    mappingResolver = mappingResolver,
                    messageProcessor = messageProcessor,
                    phoneColumnName = phoneColumnName,
                    messageColumnName = messageColumnName,
                    onMappingFound = { mapping ->
                        if (selectedSheetName == null) {
                            selectedSheetName = sheetName
                            selectedMapping = mapping
                        }
                    },
                    onMessage = { message ->
                        totalRows++
                        if (message.isValid) validRows++ else invalidRows++
                        onMessage(message)
                    },
                )
                parseSheet(reader, sheetInput, handler)
            }
        }

        val mapping = selectedMapping
            ?: throw ReadyColumnMappingException(
                "لم يتم العثور على العمودين المحددين في صف عناوين الملف.",
            )
        return ReadyWorkbookSummary(
            sheetName = selectedSheetName.orEmpty(),
            mapping = mapping,
            totalRows = totalRows,
            validRows = validRows,
            invalidRows = invalidRows,
        )
    }

    private fun openWorkbook(input: InputStream): OpenWorkbook {
        val packageFile = OPCPackage.open(input)
        try {
            val reader = XSSFReader(packageFile).apply {
                setUseReadOnlySharedStringsTable(true)
            }
            val iterator = reader.sheetsData as? XSSFReader.SheetIterator
                ?: throw ReadyWorkbookStructureException("تعذر قراءة أوراق ملف Excel.")
            if (!iterator.hasNext()) {
                throw ReadyWorkbookStructureException("ملف Excel لا يحتوي على أوراق.")
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

    private class HeaderInspectionHandler(
        private val mappingResolver: ReadyColumnMappingResolver,
    ) : XSSFSheetXMLHandler.SheetContentsHandler {
        private val currentRow = TreeMap<Int, String>()
        var headers: List<String>? = null
            private set
        var mapping: ReadyColumnMapping? = null
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
            val rowHeaders = (0..currentRow.lastKey()).map { columnIndex ->
                currentRow[columnIndex].orEmpty()
            }
            val resolvedHeaders = rowHeaders.mapIndexed { index, value ->
                value.ifBlank { "عمود ${columnLabel(index)}" }
            }
            if (headers == null) {
                headers = resolvedHeaders
            }
            if (mapping == null && mappingResolver.hasKnownColumns(resolvedHeaders)) {
                headers = resolvedHeaders
                mapping = mappingResolver.resolve(resolvedHeaders)
            }
        }
    }

    private class MessageExtractionHandler(
        private val mappingResolver: ReadyColumnMappingResolver,
        private val messageProcessor: ReadyMessageProcessor,
        private val phoneColumnName: String,
        private val messageColumnName: String,
        private val onMappingFound: (ReadyColumnMapping) -> Unit,
        private val onMessage: (MessageDraft) -> Unit,
    ) : XSSFSheetXMLHandler.SheetContentsHandler {
        private val currentRow = TreeMap<Int, String>()
        private var mapping: ReadyColumnMapping? = null
        private var headerRow = -1

        override fun startRow(rowNum: Int) = currentRow.clear()

        override fun cell(
            cellReference: String,
            formattedValue: String,
            comment: org.apache.poi.xssf.usermodel.XSSFComment?,
        ) {
            currentRow[columnIndex(cellReference)] = formattedValue
        }

        override fun endRow(rowNum: Int) {
            if (currentRow.isEmpty()) return
            if (mapping == null) {
                val headers = (0..currentRow.lastKey()).map { currentRow[it].orEmpty().trim() }
                val resolved = try {
                    mappingResolver.resolve(headers, phoneColumnName, messageColumnName)
                } catch (_: ReadyColumnMappingException) {
                    return
                }
                mapping = resolved
                headerRow = rowNum
                onMappingFound(resolved)
                return
            }
            if (rowNum <= headerRow) return

            val selected = mapping!!
            val phone = currentRow[selected.phoneColumnIndex].orEmpty()
            val message = currentRow[selected.messageColumnIndex].orEmpty()
            if (phone.isBlank() && message.isBlank()) return

            val draft = messageProcessor.process(
                rows = listOf(listOf(phone, message)),
                phoneColumnIndex = 0,
                messageColumnIndex = 1,
            ).single().copy(sourceRow = rowNum + 1)
            onMessage(draft)
        }
    }

    private companion object {
        const val DEFAULT_PREVIEW_LIMIT = 100

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
