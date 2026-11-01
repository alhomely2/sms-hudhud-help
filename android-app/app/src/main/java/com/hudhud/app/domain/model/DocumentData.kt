package com.hudhud.app.domain.model

/**
 * Represents extracted data from a document (Excel/PDF).
 * Separates data into fixed (constant) and variable (per client) categories.
 */
data class DocumentData(
    val fileName: String,
    val sourceUri: String = "",
    val sheetName: String = "",
    val totalRows: Int = 0,
    // Columns with data that changes per client (e.g., client name, phone, amount)
    val variableColumns: List<ColumnInfo> = emptyList(),
    // Columns with fixed data shared between all clients (e.g., company name, address)
    val fixedColumns: List<ColumnInfo> = emptyList(),
    // All columns combined
    val allColumns: List<ColumnInfo> = emptyList(),
    // Sample data from first row
    val sampleData: Map<String, String> = emptyMap(),
    // Error message if extraction failed
    val error: String? = null,
    // Whether extraction was successful
    val isValid: Boolean = false
) {
    /**
     * Get formatted info string for display
     */
    fun getInfoString(): String {
        return if (isValid) {
            "$totalRows صف • ${allColumns.size} عمود"
        } else {
            ""
        }
    }

    /**
     * Get all columns as display items
     */
    fun getAllColumnNames(): List<String> = allColumns.map { it.displayName.ifBlank { it.name } }
}