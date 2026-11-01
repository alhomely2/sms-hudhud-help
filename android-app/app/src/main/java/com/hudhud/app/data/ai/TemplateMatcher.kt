package com.hudhud.app.data.ai

import com.hudhud.app.domain.model.ColumnInfo
import com.hudhud.app.domain.model.ColumnType
import com.hudhud.app.domain.model.SavedTemplate
import com.hudhud.app.domain.model.SmartSuggestion
import com.hudhud.app.domain.model.SuggestionAction
import com.hudhud.app.domain.model.SuggestionType
import com.hudhud.app.domain.model.TemplateMatchResult
import com.hudhud.app.domain.model.VariableMatch
import com.hudhud.app.domain.template.VariableExtractor

/**
 * Matches templates to file columns and suggests the best template for a given file.
 * Uses similarity scoring and pattern matching.
 */
class TemplateMatcher {

    private val variableExtractor = VariableExtractor()
    
    // Minimum confidence threshold for suggestions
    private val minConfidenceThreshold = 0.4f
    
    // Weight factors for scoring
    private val exactMatchWeight = 1.0f
    private val partialMatchWeight = 0.7f
    private val typeMatchWeight = 0.3f
    private val usageWeight = 0.2f // Bonus for previously used templates

    /**
     * Find the best matching template for a given file schema
     * @param fileColumns Detected columns from the file
     * @param availableTemplates List of available templates
     * @param usageHistory Map of template ID to usage count (for learning)
     * @return List of template match results sorted by score
     */
    fun findBestTemplates(
        fileColumns: List<ColumnInfo>,
        availableTemplates: List<SavedTemplate>,
        usageHistory: Map<Long, Int> = emptyMap(),
    ): List<TemplateMatchResult> {
        
        if (availableTemplates.isEmpty()) return emptyList()
        
        val fileColumnNames = fileColumns.map { it.name.lowercase() }.toSet()
        val fileColumnTypes = fileColumns.groupBy { it.type }
        
        return availableTemplates.map { template ->
            calculateMatchScore(template, fileColumns, fileColumnNames, fileColumnTypes, usageHistory)
        }.filter { it.score >= minConfidenceThreshold }
         .sortedByDescending { it.score }
    }

    /**
     * Calculate match score between a template and file columns
     */
    private fun calculateMatchScore(
        template: SavedTemplate,
        fileColumns: List<ColumnInfo>,
        fileColumnNames: Set<String>,
        fileColumnTypes: Map<ColumnType, List<ColumnInfo>>,
        usageHistory: Map<Long, Int>,
    ): TemplateMatchResult {
        
        val templateVariables = variableExtractor.extractVariables(template.body)
        
        if (templateVariables.isEmpty()) {
            // Template without variables - check if file has phone column
            val hasPhone = fileColumns.any { it.type == ColumnType.PHONE }
            return TemplateMatchResult(
                template = template,
                score = if (hasPhone) 0.8f else 0.3f,
                matchedVariables = emptyList(),
                missingVariables = emptyList(),
                isMatch = hasPhone,
            )
        }
        
        val matchedVariables = mutableListOf<VariableMatch>()
        val missingVariables = mutableListOf<String>()
        
        var totalScore = 0f
        
        // Check each variable in the template
        for (variable in templateVariables) {
            val normalizedVar = variable.lowercase().replace("_", " ")
            
            // 1. Try exact name match
            val exactMatch = fileColumns.find { 
                it.name.lowercase().replace("_", " ") == normalizedVar 
            }
            
            if (exactMatch != null) {
                matchedVariables.add(
                    VariableMatch(
                        templateVariable = variable,
                        fileColumn = exactMatch.name,
                        confidence = exactMatch.confidence,
                        isMatched = true,
                    )
                )
                totalScore += exactMatchWeight * exactMatch.confidence
                continue
            }
            
            // 2. Try partial match
            val partialMatch = fileColumns.find {
                it.name.lowercase().contains(normalizedVar) ||
                normalizedVar.contains(it.name.lowercase())
            }
            
            if (partialMatch != null) {
                matchedVariables.add(
                    VariableMatch(
                        templateVariable = variable,
                        fileColumn = partialMatch.name,
                        confidence = partialMatch.confidence * partialMatchWeight,
                        isMatched = true,
                    )
                )
                totalScore += partialMatchWeight * partialMatch.confidence
                continue
            }
            
            // 3. Try type-based match (e.g., Amount -> NUMBER column)
            val typeBasedMatch = findTypeMatch(variable, fileColumnTypes)
            
            if (typeBasedMatch != null) {
                matchedVariables.add(
                    VariableMatch(
                        templateVariable = variable,
                        fileColumn = typeBasedMatch.name,
                        confidence = typeBasedMatch.confidence * typeMatchWeight,
                        isMatched = true,
                    )
                )
                totalScore += typeMatchWeight * typeBasedMatch.confidence
                continue
            }
            
            // Variable not matched
            missingVariables.add(variable)
            totalScore -= 0.1f // Penalty for missing variable
        }
        
        // Add usage bonus
        val usageCount = usageHistory[template.id] ?: 0
        val usageBonus = minOf(usageCount * 0.05f, usageWeight)
        totalScore += usageBonus
        
        // Normalize score (0-1 range)
        val maxPossibleScore = templateVariables.size * exactMatchWeight + usageWeight
        val normalizedScore = (totalScore / maxPossibleScore).coerceIn(0f, 1f)
        
        return TemplateMatchResult(
            template = template,
            score = normalizedScore,
            matchedVariables = matchedVariables,
            missingVariables = missingVariables,
            isMatch = missingVariables.isEmpty() && matchedVariables.isNotEmpty(),
        )
    }

    /**
     * Find a column that matches the variable based on type
     */
    private fun findTypeMatch(
        variable: String,
        fileColumnTypes: Map<ColumnType, List<ColumnInfo>>,
    ): ColumnInfo? {
        
        val normalizedVar = variable.lowercase()
        
        // Map variable names to expected types
        val expectedType = when {
            normalizedVar.contains("phone") || normalizedVar.contains("mobile") || normalizedVar.contains("contact") -> 
                ColumnType.PHONE
            normalizedVar.contains("name") -> ColumnType.NAME
            normalizedVar.contains("amount") || normalizedVar.contains("price") || normalizedVar.contains("total") || normalizedVar.contains("balance") ->
                ColumnType.AMOUNT
            normalizedVar.contains("date") || normalizedVar.contains("time") -> ColumnType.DATE
            normalizedVar.contains("email") -> ColumnType.EMAIL
            normalizedVar.contains("message") || normalizedVar.contains("text") || normalizedVar.contains("body") ->
                ColumnType.TEXT
            else -> null
        }
        
        if (expectedType == null) return null
        
        // Find best column of the expected type
        return fileColumnTypes[expectedType]?.maxByOrNull { it.confidence * it.quality }
    }

    /**
     * Generate smart suggestions from match results
     */
    fun generateSuggestions(matchResults: List<TemplateMatchResult>): List<SmartSuggestion> {
        return matchResults.map { result ->
            SmartSuggestion(
                id = "template_${result.template.id}",
                type = SuggestionType.TEMPLATE_MATCH,
                title = result.template.name,
                description = buildString {
                    append("Score: ${result.matchPercent}")
                    if (result.matchedVariables.isNotEmpty()) {
                        append(" • ${result.matchedVariables.size} variables matched")
                    }
                    if (result.missingVariables.isNotEmpty()) {
                        append(" • ${result.missingVariables.size} variables need mapping")
                    }
                },
                confidence = result.score,
                action = SuggestionAction.APPLY_TEMPLATE,
                metadata = mapOf(
                    "templateId" to result.template.id,
                    "matchedVariables" to result.matchedVariables.map { it.templateVariable },
                    "missingVariables" to result.missingVariables,
                ),
            )
        }
    }

    /**
     * Get auto-link suggestions based on template match
     */
    fun getAutoLinkSuggestions(
        matchResult: TemplateMatchResult,
        fileColumns: List<ColumnInfo>,
    ): List<SmartSuggestion> {
        
        return matchResult.matchedVariables.mapNotNull { variableMatch ->
            if (variableMatch.fileColumn == null) {
                SmartSuggestion(
                    id = "link_${variableMatch.templateVariable}",
                    type = SuggestionType.VARIABLE_LINK,
                    title = "Link ${variableMatch.templateVariable}",
                    description = "No matching column found for this variable",
                    confidence = 0f,
                    action = SuggestionAction.LINK_VARIABLE,
                    metadata = mapOf("variable" to variableMatch.templateVariable),
                )
            } else {
                SmartSuggestion(
                    id = "link_${variableMatch.templateVariable}",
                    type = SuggestionType.VARIABLE_LINK,
                    title = "${variableMatch.templateVariable} → ${variableMatch.fileColumn}",
                    description = "Auto-linked with ${(variableMatch.confidence * 100).toInt()}% confidence",
                    confidence = variableMatch.confidence,
                    action = SuggestionAction.LINK_VARIABLE,
                    metadata = mapOf(
                        "variable" to variableMatch.templateVariable,
                        "column" to variableMatch.fileColumn,
                    ),
                    isAutoApplied = variableMatch.confidence >= 0.8f,
                )
            }
        }
    }
}