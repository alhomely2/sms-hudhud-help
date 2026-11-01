package com.hudhud.app.domain.model

/**
 * Represents information about a column in a source file (Excel/PDF).
 * Used for column detection and mapping.
 */
data class ColumnInfo(
    val name: String,
    val displayName: String = name,
    val type: ColumnType,
    val confidence: Float = 0f, // 0.0 to 1.0
    val sampleValues: List<String> = emptyList(),
    val validCount: Int = 0,
    val totalCount: Int = 0,
) {
    /**
     * Calculate data quality as percentage of valid values
     */
    val quality: Float
        get() = if (totalCount > 0) validCount.toFloat() / totalCount else 0f
    
    /**
     * Check if this column is likely a phone column
     */
    val isLikelyPhone: Boolean
        get() = type == ColumnType.PHONE && confidence > 0.7f
    
    /**
     * Check if this column is likely a name column
     */
    val isLikelyName: Boolean
        get() = type == ColumnType.NAME && confidence > 0.6f
}

/**
 * Types of columns detected in a file
 */
enum class ColumnType {
    PHONE,        // Phone number
    NAME,         // Person name
    AMOUNT,       // Monetary amount
    DATE,         // Date
    EMAIL,        // Email address
    TEXT,         // General text / message body
    NUMBER,       // Generic number
    COMPANY,      // Company / brand field
    ADDRESS,      // Address / location field
    UNKNOWN,      // Unknown type
    IGNORED,      // Should be ignored (e.g., notes, comments)
}

/**
 * Represents the complete schema of a source file
 */
data class FileSchema(
    val columns: List<ColumnInfo>,
    val sheetName: String? = null,
    val totalRows: Int = 0,
    val error: String? = null,
) {
    /**
     * Get the most likely phone column
     */
    val likelyPhoneColumn: ColumnInfo?
        get() = columns.maxByOrNull { 
            if (it.type == ColumnType.PHONE) it.confidence * it.quality else 0f 
        }
    
    /**
     * Get the most likely message/text column
     */
    val likelyTextColumn: ColumnInfo?
        get() = columns.maxByOrNull {
            if (it.type == ColumnType.TEXT) it.confidence * it.quality else 0f
        }
    
    /**
     * Get all columns of a specific type
     */
    fun columnsOfType(type: ColumnType): List<ColumnInfo> =
        columns.filter { it.type == type }
}