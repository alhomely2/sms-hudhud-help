package com.hudhud.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Template entity for storing template data in the database.
 * Supports fixed data (shared between all clients) and variable data (changes per client).
 */
@Entity(tableName = "templates")
data class TemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val body: String,
    val phoneColumn: String = "",
    // JSON map of variable column mappings: {"{{name}}": "columnName"}
    val variableColumnsJson: String = "{}",
    // JSON map of fixed values: {"{{month}}": "أكتوبر 2026"}
    val fixedValuesJson: String = "{}",
    // New: JSON array of selected field objects with formatting options
    // [{"name": "clientName", "beforeText": "عزيزي", "afterText": "", "newLineAfter": true}]
    val selectedFieldsJson: String = "[]",
    // New: JSON array of fixed data items
    // [{"name": "companyName", "value": "شركة الهدهد", "beforeText": "", "afterText": "", "newLineAfter": false}]
    val fixedDataJson: String = "[]",
    // Source-document metadata only; do not persist the original file URI/path.
    val sourceFileName: String = "",
    // New: Document column headers (JSON array)
    val documentHeadersJson: String = "[]",
    // New: Document sample data (first row, JSON object)
    val documentSampleJson: String = "{}",
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "'USER'") val sourceType: String = SOURCE_USER,
    @ColumnInfo(defaultValue = "1") val sourceVersion: Int = 1,
) {
    companion object {
        const val SOURCE_BUILT_IN = "BUILT_IN"
        const val SOURCE_USER = "USER"
    }
}
