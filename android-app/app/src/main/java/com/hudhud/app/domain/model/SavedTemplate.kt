package com.hudhud.app.domain.model

/**
 * Represents a field selected from the document to be included in the template message.
 * Each field can have formatting options like text before/after and new line option.
 */
data class TemplateField(
    val name: String,
    val beforeText: String = "",
    val afterText: String = "",
    val newLineAfter: Boolean = false
)

/**
 * Represents a fixed data item that is shared between all clients.
 * Examples: company name, address, etc.
 */
data class FixedData(
    val name: String,
    val value: String,
    val beforeText: String = "",
    val afterText: String = "",
    val newLineAfter: Boolean = false
)

/**
 * Domain model representing a saved template with support for fixed and variable data.
 */
data class SavedTemplate(
    val id: Long,
    val name: String,
    val body: String,
    val phoneColumn: String,
    val variableColumns: Map<String, String> = emptyMap(),
    val fixedValues: Map<String, String> = emptyMap(),
    // New: Selected fields from document with formatting options
    val selectedFields: List<TemplateField> = emptyList(),
    // New: Fixed data items (shared between all clients)
    val fixedData: List<FixedData> = emptyList(),
    // Source document metadata only; the original file itself is not retained.
    val sourceFileName: String = "",
    // New: Document column headers
    val documentHeaders: List<String> = emptyList(),
    // New: Document sample data (first row)
    val documentSample: Map<String, String> = emptyMap(),
    val createdAt: Long,
    val updatedAt: Long,
)
