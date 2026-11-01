package com.hudhud.app.data.document

import android.content.Context
import android.net.Uri
import com.hudhud.app.domain.model.ColumnInfo
import com.hudhud.app.domain.model.ColumnType
import com.hudhud.app.domain.model.DocumentData
import com.hudhud.app.domain.model.FileSchema
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts data from Excel and PDF documents and separates it into:
 * - Fixed data (constant across all clients)
 * - Variable data (changes per client)
 *
 * This is used in the new template editor workflow.
 */
class DocumentDataExtractor(private val context: Context) {

    private val excelExtractor = ExcelSchemaExtractor(context)

    /**
     * Extract all data from a document (Excel or PDF)
     * @param uri File URI
     * @param fileName File name for display
     * @return DocumentData containing all extracted information
     */
    suspend fun extractDocument(uri: Uri, fileName: String): DocumentData = withContext(Dispatchers.IO) {
        try {
            if (fileName.endsWith(".xlsx", ignoreCase = true) || 
                fileName.endsWith(".xls", ignoreCase = true)) {
                extractFromExcel(uri, fileName)
            } else if (fileName.endsWith(".pdf", ignoreCase = true)) {
                extractFromPdf(uri, fileName)
            } else {
                DocumentData(
                    fileName = fileName,
                    error = "Unsupported file format. Please use Excel (.xlsx) or PDF."
                )
            }
        } catch (e: Exception) {
            DocumentData(
                fileName = fileName,
                error = "Error reading document: ${e.message}"
            )
        }
    }

    /**
     * Extract data from Excel file
     */
    private suspend fun extractFromExcel(uri: Uri, fileName: String): DocumentData {
        val schema = excelExtractor.extractSchema(uri, 0)
        
        if (schema.error != null) {
            return DocumentData(fileName = fileName, error = schema.error)
        }

        // Separate columns into fixed and variable
        val variableColumns = schema.columns.filter { column ->
            // Variable columns: data that changes per client
            // Exclude known fixed data patterns
            !isFixedColumn(column)
        }

        val fixedColumns = schema.columns.filter { column ->
            isFixedColumn(column)
        }

        return DocumentData(
            fileName = fileName,
            sourceUri = uri.toString(),
            sheetName = schema.sheetName.orEmpty(),
            totalRows = schema.totalRows.coerceAtLeast(0),
            variableColumns = variableColumns,
            fixedColumns = fixedColumns,
            allColumns = schema.columns,
            sampleData = getSampleData(schema),
            isValid = schema.columns.isNotEmpty(),
        )
    }

    /**
     * Extract data from PDF file
     * Note: PDF extraction uses PdfReportDetector to identify the template type
     * and extract known fields from it
     */
    private suspend fun extractFromPdf(uri: Uri, fileName: String): DocumentData {
        val pdfExtractor = PdfColumnExtractor()
        
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                return DocumentData(fileName = fileName, error = "Cannot open PDF file")
            }
            
            inputStream.use { input ->
                val extracted = pdfExtractor.extract(context, input, fileName)
                val template = extracted.template
                
                // Extract known fields from template
                val columns = template.columns.map { field ->
                    ColumnInfo(
                        name = field.id,
                        displayName = field.label,
                        type = inferColumnType(field.id),
                        confidence = 0.9f,
                        sampleValues = emptyList()
                    )
                }
                
                val variableColumns = columns.filter { !isFixedColumn(it) }
                val fixedColumns = columns.filter { isFixedColumn(it) }

                DocumentData(
                    fileName = fileName,
                    sourceUri = uri.toString(),
                    totalRows = extracted.pageCount,
                    variableColumns = variableColumns,
                    fixedColumns = fixedColumns,
                    allColumns = columns,
                    isValid = columns.isNotEmpty()
                )
            }
        } catch (e: UnsupportedPdfTemplateException) {
            DocumentData(
                fileName = fileName,
                error = "Unsupported PDF template. Please use an Excel file or a known PDF format."
            )
        } catch (e: Exception) {
            DocumentData(
                fileName = fileName,
                error = "Error reading PDF: ${e.message}"
            )
        }
    }
    
    /**
     * Infer column type from field ID
     */
    private fun inferColumnType(fieldId: String): ColumnType {
        val id = fieldId.lowercase()
        return when {
            id.contains("phone") || id.contains("mobile") || id.contains("mobile_no") -> ColumnType.PHONE
            id.contains("name") || id.contains("client") || id.contains("customer") -> ColumnType.NAME
            id.contains("amount") || id.contains("balance") || id.contains("due") -> ColumnType.AMOUNT
            id.contains("date") || id.contains("day") -> ColumnType.DATE
            id.contains("email") -> ColumnType.EMAIL
            id.contains("message") || id.contains("text") || id.contains("sms") -> ColumnType.TEXT
            id.contains("number") || id.contains("no") || id.contains("id") -> ColumnType.NUMBER
            else -> ColumnType.UNKNOWN
        }
    }

    /**
     * Determine if a column likely contains fixed data (shared between all clients)
     */
    private fun isFixedColumn(column: ColumnInfo): Boolean {
        val name = column.name.lowercase()
        
        // Common fixed data patterns
        val fixedPatterns = listOf(
            "company", "شركة", "الشركة",
            "address", "عنوان", "العنوان",
            "city", "المدينة", "city_name",
            "country", "الدولة", "country_name",
            "region", "المنطقة",
            "branch", "الفرع",
            "department", "القسم",
            "company_name", "company_name_ar",
            "company_address", "company_phone",
            "tax_number", "رقم_الضريبة",
            "vat", "الضريبة",
            "currency", "العملة",
            "language", "اللغة",
            "template", "القالب",
            "category", "الفئة",
            "type", "النوع"
        )
        
        return fixedPatterns.any { pattern -> name.contains(pattern) } ||
               column.type == ColumnType.COMPANY ||
               column.type == ColumnType.ADDRESS
    }

    /**
     * Get sample data from the first data row
     */
    private fun getSampleData(schema: FileSchema): Map<String, String> {
        return schema.columns.associate { column ->
            column.name to (column.sampleValues.firstOrNull() ?: "")
        }
    }

    /**
     * Extract headers only (quick preview)
     */
    suspend fun extractHeaders(uri: Uri): List<String> = withContext(Dispatchers.IO) {
        try {
            val schema = excelExtractor.extractSchema(uri, 0)
            schema.columns.map { it.name }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get file info (column count, row count)
     */
    suspend fun getFileInfo(uri: Uri): Pair<Int, Int>? = withContext(Dispatchers.IO) {
        try {
            val schema = excelExtractor.extractSchema(uri, 0)
            val columnCount = schema.columns.size
            val rowCount = schema.totalRows ?: 0
            Pair(columnCount, rowCount)
        } catch (e: Exception) {
            null
        }
    }
}