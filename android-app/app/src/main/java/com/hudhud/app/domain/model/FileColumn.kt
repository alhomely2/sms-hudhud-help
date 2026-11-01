package com.hudhud.app.domain.model

/**
 * Represents a column extracted from a file during import.
 * This is used during the import process to represent file structure.
 */
data class FileColumn(
    val index: Int,
    val name: String,
    val headerName: String,  // Original header from file
    val values: List<String> = emptyList(), // Sample values (first N rows)
    val isSelected: Boolean = false,
) {
    /**
     * Get a preview of the column values
     */
    val preview: String
        get() = values.take(3).joinToString(", ")
    
    /**
     * Get count of non-empty values
     */
    val nonEmptyCount: Int
        get() = values.count { it.isNotBlank() }
    
    /**
     * Check if column appears empty
     */
    val isEmpty: Boolean
        get() = values.isEmpty() || values.all { it.isBlank() }
}

/**
 * Represents the result of parsing a file
 */
data class FileParseResult(
    val fileName: String,
    val fileUri: String,
    val sheets: List<SheetInfo> = emptyList(),
    val columns: List<FileColumn> = emptyList(),
    val rows: List<Map<String, String>> = emptyList(),
    val totalRows: Int = 0,
    val error: String? = null,
    val isSuccess: Boolean = error == null,
) {
    companion object {
        fun success(
            fileName: String,
            fileUri: String,
            columns: List<FileColumn>,
            rows: List<Map<String, String>>,
            sheets: List<SheetInfo> = emptyList(),
        ): FileParseResult = FileParseResult(
            fileName = fileName,
            fileUri = fileUri,
            columns = columns,
            rows = rows,
            sheets = sheets,
            totalRows = rows.size,
        )
        
        fun failure(fileName: String, fileUri: String, error: String): FileParseResult =
            FileParseResult(fileName, fileUri, error = error)
    }
}

/**
 * Information about a sheet in an Excel file
 */
data class SheetInfo(
    val index: Int,
    val name: String,
    val rowCount: Int = 0,
    val columnCount: Int = 0,
) {
    val displayName: String
        get() = "$name ($rowCount rows)"
}