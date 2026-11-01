package com.hudhud.app.data.document

import android.content.Context
import android.net.Uri
import com.hudhud.app.data.ai.ColumnAnalyzer
import com.hudhud.app.domain.model.ColumnInfo
import com.hudhud.app.domain.model.FileSchema
import com.hudhud.app.domain.model.SheetInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook

/**
 * Extracts schema (column information) from Excel files.
 * Used for column detection and mapping.
 */
class ExcelSchemaExtractor(private val context: Context) {

    private val columnAnalyzer = ColumnAnalyzer()
    
    // Number of sample rows to analyze for type detection
    private val sampleRowCount = 20

    /**
     * Extract schema from Excel file
     * @param uri File URI
     * @param sheetIndex Sheet index to extract from (0-based)
     * @return FileSchema with column information
     */
    suspend fun extractSchema(uri: Uri, sheetIndex: Int = 0): FileSchema = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                // Use WorkbookFactory for compatibility
                val workbook = WorkbookFactory.create(inputStream)
                val sheet = workbook.getSheetAt(sheetIndex)
                
                extractFromSheet(workbook, sheet, sheetIndex)
            } ?: FileSchema(columns = emptyList(), error = "Cannot open file")
        } catch (e: Exception) {
            FileSchema(columns = emptyList(), error = "Error reading Excel: ${e.message}")
        }
    }

    /**
     * Extract schema from all sheets
     * @param uri File URI
     * @return List of FileSchema, one per sheet
     */
    suspend fun extractAllSheets(uri: Uri): List<FileSchema> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val workbook = WorkbookFactory.create(inputStream)
                
                (0 until workbook.numberOfSheets).map { sheetIndex ->
                    val sheet = workbook.getSheetAt(sheetIndex)
                    extractFromSheet(workbook, sheet, sheetIndex)
                }
            } ?: emptyList()
        } catch (e: Exception) {
            listOf(FileSchema(columns = emptyList(), error = "Error reading Excel: ${e.message}"))
        }
    }

    /**
     * Extract schema from a specific sheet
     */
    private fun extractFromSheet(
        workbook: org.apache.poi.ss.usermodel.Workbook,
        sheet: org.apache.poi.ss.usermodel.Sheet,
        sheetIndex: Int,
    ): FileSchema {
        val sheetName = sheet.sheetName
        
        // Get header row (first row)
        val headerRow = sheet.first() ?: return FileSchema(
            columns = emptyList(),
            sheetName = sheetName,
            error = "Sheet is empty",
        )

        // Extract column names from header
        val columnNames = mutableListOf<String>()
        val headerCell = headerRow.firstOrNull() ?: return FileSchema(
            columns = emptyList(),
            sheetName = sheetName,
            error = "Cannot read header row",
        )

        // Iterate through header cells
        for (cell in headerRow) {
            val value = getCellValue(cell)
            columnNames.add(value ?: "Column_${cell.columnIndex + 1}")
        }

        // Collect sample values for each column
        val columnSamples = mutableMapOf<String, MutableList<String>>()
        columnNames.forEach { columnSamples[it] = mutableListOf() }

        // Read sample rows (skip header)
        val rows = sheet.iterator()
        var rowCount = 0
        var sampleRowsRead = 0
        
        while (rows.hasNext() && sampleRowsRead < sampleRowCount) {
            val row = rows.next()
            
            // Skip header row
            if (row.rowNum == headerRow.rowNum) continue
            
            rowCount++
            sampleRowsRead++
            
            // Collect values from each column
            for (cell in row) {
                val columnName = columnNames.getOrNull(cell.columnIndex) ?: continue
                val value = getCellValue(cell)
                columnSamples[columnName]?.add(value ?: "")
            }
        }

        // Analyze each column
        val columns = columnNames.map { columnName ->
            val samples = columnSamples[columnName] ?: emptyList()
            columnAnalyzer.analyzeColumn(columnName, samples)
        }

        return FileSchema(
            columns = columns,
            sheetName = sheetName,
            totalRows = rowCount,
        )
    }

    /**
     * Get cell value as string
     */
    private fun getCellValue(cell: org.apache.poi.ss.usermodel.Cell): String? {
        return when (cell.cellType) {
            org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue
            org.apache.poi.ss.usermodel.CellType.NUMERIC -> {
                if (org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)) {
                    cell.dateCellValue?.toString()
                } else {
                    cell.numericCellValue.toString()
                }
            }
            org.apache.poi.ss.usermodel.CellType.BOOLEAN -> cell.booleanCellValue.toString()
            org.apache.poi.ss.usermodel.CellType.FORMULA -> {
                try {
                    cell.stringCellValue
                } catch (e: Exception) {
                    cell.numericCellValue.toString()
                }
            }
            org.apache.poi.ss.usermodel.CellType.BLANK -> null
            else -> null
        }?.trim()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Get list of sheets in the workbook
     */
    suspend fun getSheets(uri: Uri): List<SheetInfo> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val workbook = WorkbookFactory.create(inputStream)
                
                (0 until workbook.numberOfSheets).map { index ->
                    val sheet = workbook.getSheetAt(index)
                    SheetInfo(
                        index = index,
                        name = sheet.sheetName,
                        rowCount = sheet.physicalNumberOfRows,
                        columnCount = sheet.getRow(0)?.lastCellNum?.toInt() ?: 0,
                    )
                }
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Quick check if file has required columns (phone column)
     */
    suspend fun hasPhoneColumn(uri: Uri, sheetIndex: Int = 0): Boolean = withContext(Dispatchers.IO) {
        val schema = extractSchema(uri, sheetIndex)
        schema.columns.any { it.type == com.hudhud.app.domain.model.ColumnType.PHONE && it.confidence > 0.7f }
    }
}