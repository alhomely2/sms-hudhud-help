package com.hudhud.app.domain.template

import com.hudhud.app.data.ai.AutoLinker
import com.hudhud.app.domain.model.ColumnInfo
import com.hudhud.app.domain.model.MappingResult
import com.hudhud.app.domain.model.SavedTemplate
import com.hudhud.app.domain.model.SmartSuggestion
import com.hudhud.app.domain.model.SuggestionAction
import com.hudhud.app.domain.model.SuggestionType

/**
 * Maps template variables to file columns.
 * Provides auto-linking and manual mapping functionality.
 */
class ColumnTemplateMapper {

    private val variableExtractor = VariableExtractor()
    private val autoLinker = AutoLinker()

    /**
     * Create auto-mapping between template variables and file columns
     * @param template The template with variables
     * @param fileColumns Available columns from the file
     * @return MappingResult with suggested mappings
     */
    fun createAutoMapping(
        template: SavedTemplate,
        fileColumns: List<ColumnInfo>,
    ): MappingResult {
        
        val templateVariables = variableExtractor.extractVariables(template.body)
        
        if (templateVariables.isEmpty()) {
            // No variables - just map phone column
            val phoneColumn = findPhoneColumn(fileColumns)
            return MappingResult(
                phoneColumn = phoneColumn?.name,
                variableMappings = emptyMap(),
                confidence = if (phoneColumn != null) 0.9f else 0.3f,
                isComplete = phoneColumn != null,
                suggestions = generateSuggestions(phoneColumn, fileColumns),
            )
        }

        // Auto-link variables to columns
        val variableMappings = mutableMapOf<String, String>()
        var totalConfidence = 0f
        val suggestions = mutableListOf<SmartSuggestion>()

        templateVariables.forEach { variable ->
            val linkResult = autoLinker.autoLinkVariable(variable, fileColumns)
            
            if (linkResult.matchedColumn != null) {
                variableMappings[variable] = linkResult.matchedColumn
                totalConfidence += linkResult.confidence
                
                suggestions.add(
                    SmartSuggestion(
                        id = "link_$variable",
                        type = SuggestionType.VARIABLE_LINK,
                        title = "$variable → ${linkResult.matchedColumn}",
                        description = "Auto-linked with ${(linkResult.confidence * 100).toInt()}% confidence",
                        confidence = linkResult.confidence,
                        action = SuggestionAction.LINK_VARIABLE,
                        metadata = mapOf(
                            "variable" to variable,
                            "column" to linkResult.matchedColumn,
                        ),
                        isAutoApplied = linkResult.confidence >= 0.8f,
                    )
                )
            } else {
                suggestions.add(
                    SmartSuggestion(
                        id = "link_$variable",
                        type = SuggestionType.VARIABLE_LINK,
                        title = "Link $variable",
                        description = "No matching column found",
                        confidence = 0f,
                        action = SuggestionAction.LINK_VARIABLE,
                        metadata = mapOf("variable" to variable),
                    )
                )
            }
        }

        // Find phone column
        val phoneColumn = findPhoneColumn(fileColumns)
        
        // Add phone column suggestion
        if (phoneColumn != null) {
            suggestions.add(
                SmartSuggestion(
                    id = "phone_column",
                    type = SuggestionType.COLUMN_PHONE,
                    title = "Phone column: ${phoneColumn.name}",
                    description = "Detected with ${(phoneColumn.confidence * 100).toInt()}% confidence",
                    confidence = phoneColumn.confidence,
                    action = SuggestionAction.SELECT_COLUMN,
                    metadata = mapOf("column" to phoneColumn.name),
                    isAutoApplied = phoneColumn.confidence >= 0.8f,
                )
            )
        }

        val avgConfidence = if (templateVariables.isNotEmpty()) {
            totalConfidence / templateVariables.size
        } else 0f

        val isComplete = variableMappings.isNotEmpty() && phoneColumn != null

        return MappingResult(
            phoneColumn = phoneColumn?.name,
            variableMappings = variableMappings,
            confidence = avgConfidence,
            isComplete = isComplete,
            suggestions = suggestions,
        )
    }

    /**
     * Validate existing mapping
     * @param mapping The mapping to validate
     * @param fileColumns Available columns
     * @return true if mapping is valid
     */
    fun validateMapping(
        mapping: Map<String, String>,
        fileColumns: List<ColumnInfo>,
    ): Boolean {
        val columnNames = fileColumns.map { it.name }.toSet()
        
        // Check all mapped columns exist
        return mapping.values.all { it in columnNames }
    }

    /**
     * Update a single variable mapping
     * @param currentMappings Current mappings
     * @param variable Variable name
     * @param columnName Column name to map to
     * @return Updated mappings
     */
    fun updateMapping(
        currentMappings: Map<String, String>,
        variable: String,
        columnName: String,
    ): Map<String, String> {
        return currentMappings.toMutableMap().apply {
            this[variable] = columnName
        }
    }

    /**
     * Remove a variable mapping
     * @param currentMappings Current mappings
     * @param variable Variable to remove
     * @return Updated mappings
     */
    fun removeMapping(
        currentMappings: Map<String, String>,
        variable: String,
    ): Map<String, String> {
        return currentMappings.toMutableMap().apply {
            remove(variable)
        }
    }

    /**
     * Find the most likely phone column from file columns
     */
    private fun findPhoneColumn(columns: List<ColumnInfo>): ColumnInfo? {
        return columns.maxByOrNull {
            if (it.type == com.hudhud.app.domain.model.ColumnType.PHONE) {
                it.confidence * it.quality
            } else 0f
        }
    }

    /**
     * Generate suggestions for missing mappings
     */
    private fun generateSuggestions(
        phoneColumn: ColumnInfo?,
        fileColumns: List<ColumnInfo>,
    ): List<SmartSuggestion> {
        
        val suggestions = mutableListOf<SmartSuggestion>()
        
        // Suggest phone column
        if (phoneColumn == null) {
            // Find any column that might be phone
            fileColumns.forEach { column ->
                if (column.name.lowercase().contains("phone") || 
                    column.name.lowercase().contains("mobile")) {
                    suggestions.add(
                        SmartSuggestion(
                            id = "suggest_phone_${column.name}",
                            type = SuggestionType.COLUMN_PHONE,
                            title = "Use '${column.name}' as phone column?",
                            description = "Column name suggests it's a phone number",
                            confidence = 0.6f,
                            action = SuggestionAction.SELECT_COLUMN,
                            metadata = mapOf("column" to column.name),
                        )
                    )
                }
            }
        }
        
        return suggestions
    }

    /**
     * Check if mapping is complete for all template variables
     */
    fun isMappingComplete(
        template: SavedTemplate,
        mappings: Map<String, String>,
    ): Boolean {
        val templateVariables = variableExtractor.extractVariables(template.body)
        
        // All template variables must be mapped
        if (templateVariables.isNotEmpty()) {
            val unmapped = templateVariables.filter { !mappings.containsKey(it) }
            if (unmapped.isNotEmpty()) return false
        }
        
        return true
    }

    /**
     * Get unmapped variables
     */
    fun getUnmappedVariables(
        template: SavedTemplate,
        mappings: Map<String, String>,
    ): List<String> {
        val templateVariables = variableExtractor.extractVariables(template.body)
        return templateVariables.filter { !mappings.containsKey(it) }
    }

    /**
     * Export mapping to JSON string
     */
    fun exportMappingToJson(mappings: Map<String, String>): String {
        return org.json.JSONObject(mappings).toString()
    }

    /**
     * Import mapping from JSON string
     */
    fun importMappingFromJson(json: String): Map<String, String> {
        return try {
            val obj = org.json.JSONObject(json)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}