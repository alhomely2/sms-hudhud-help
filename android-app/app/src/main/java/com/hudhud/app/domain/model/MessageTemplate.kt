package com.hudhud.app.domain.model

data class MessageTemplate(
    val name: String,
    val body: String,
    val phoneColumn: String,
    val variableColumns: Map<String, String>,
    /** Values inserted into every message, e.g. the billing month. */
    val fixedValues: Map<String, String> = emptyMap(),
) {
    /** Variable placeholders that must be read from a document column. */
    fun documentColumns(): List<String> = variableColumns
        .filterKeys { fixedValues[it].isNullOrBlank() }
        .values
        .filter { it.isNotBlank() }
        .distinct()
}
