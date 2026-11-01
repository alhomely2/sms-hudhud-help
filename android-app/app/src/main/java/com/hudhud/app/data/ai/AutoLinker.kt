package com.hudhud.app.data.ai

import com.hudhud.app.domain.model.ColumnInfo
import com.hudhud.app.domain.model.ColumnType

/**
 * Automatically links template variables to file columns.
 * Part of the Smart AI engine.
 */
class AutoLinker {

    /**
     * Auto-link a single variable to the best matching column
     * @param variableName The template variable name
     * @param fileColumns Available columns from the file
     * @return LinkResult with the best match and confidence
     */
    fun autoLinkVariable(
        variableName: String,
        fileColumns: List<ColumnInfo>,
    ): LinkResult {
        
        val normalizedVar = variableName.lowercase().replace("_", " ").replace("-", " ")
        
        // Strategy 1: Exact name match
        val exactMatch = fileColumns.find { 
            it.name.lowercase().replace("_", " ").replace("-", " ") == normalizedVar
        }
        
        if (exactMatch != null && exactMatch.confidence > 0.5f) {
            return LinkResult(
                matchedColumn = exactMatch.name,
                confidence = exactMatch.confidence,
                matchType = MatchType.EXACT,
            )
        }
        
        // Strategy 2: Partial name match (variable contains column or vice versa)
        val partialMatch = fileColumns.find {
            val normalizedCol = it.name.lowercase().replace("_", " ").replace("-", " ")
            normalizedVar.contains(normalizedCol) || normalizedCol.contains(normalizedVar)
        }
        
        if (partialMatch != null) {
            return LinkResult(
                matchedColumn = partialMatch.name,
                confidence = partialMatch.confidence * 0.7f,
                matchType = MatchType.PARTIAL,
            )
        }
        
        // Strategy 3: Type-based matching
        val typeMatch = findTypeMatch(variableName, fileColumns)
        if (typeMatch != null) {
            return LinkResult(
                matchedColumn = typeMatch.name,
                confidence = typeMatch.confidence * 0.5f,
                matchType = MatchType.TYPE_BASED,
            )
        }
        
        // Strategy 4: Fuzzy matching (similar words)
        val fuzzyMatch = findFuzzyMatch(variableName, fileColumns)
        if (fuzzyMatch != null) {
            return LinkResult(
                matchedColumn = fuzzyMatch.name,
                confidence = fuzzyMatch.confidence * 0.4f,
                matchType = MatchType.FUZZY,
            )
        }
        
        // No match found
        return LinkResult(
            matchedColumn = null,
            confidence = 0f,
            matchType = MatchType.NONE,
        )
    }

    /**
     * Find type-based match for a variable
     */
    private fun findTypeMatch(
        variableName: String,
        fileColumns: List<ColumnInfo>,
    ): ColumnInfo? {
        
        val normalizedVar = variableName.lowercase()
        
        // Determine expected type from variable name
        val expectedTypes = mutableListOf<ColumnType>()
        
        when {
            normalizedVar.contains("phone") || normalizedVar.contains("mobile") || normalizedVar.contains("contact") -> 
                expectedTypes.add(ColumnType.PHONE)
            normalizedVar.contains("name") || normalizedVar.contains("customer") || normalizedVar.contains("client") ->
                expectedTypes.add(ColumnType.NAME)
            normalizedVar.contains("amount") || normalizedVar.contains("price") || normalizedVar.contains("total") || 
                 normalizedVar.contains("balance") || normalizedVar.contains("due") ->
                expectedTypes.add(ColumnType.AMOUNT)
            normalizedVar.contains("date") || normalizedVar.contains("time") ->
                expectedTypes.add(ColumnType.DATE)
            normalizedVar.contains("email") ->
                expectedTypes.add(ColumnType.EMAIL)
            normalizedVar.contains("message") || normalizedVar.contains("text") || normalizedVar.contains("body") ->
                expectedTypes.add(ColumnType.TEXT)
        }
        
        if (expectedTypes.isEmpty()) return null
        
        // Find best column matching any of the expected types
        return fileColumns
            .filter { it.type in expectedTypes }
            .maxByOrNull { it.confidence * it.quality }
    }

    /**
     * Find fuzzy match using string similarity
     */
    private fun findFuzzyMatch(
        variableName: String,
        fileColumns: List<ColumnInfo>,
    ): ColumnInfo? {
        
        return fileColumns
            .map { column ->
                val similarity = calculateSimilarity(variableName, column.name)
                column to similarity
            }
            .filter { it.second >= 0.5f } // Minimum 50% similarity
            .maxByOrNull { it.second }
            ?.first
    }

    /**
     * Calculate string similarity (simple Levenshtein-based)
     */
    private fun calculateSimilarity(s1: String, s2: String): Float {
        val normalizedS1 = s1.lowercase().replace("_", "").replace("-", "")
        val normalizedS2 = s2.lowercase().replace("_", "").replace("-", "")
        
        if (normalizedS1 == normalizedS2) return 1f
        if (normalizedS1.isEmpty() || normalizedS2.isEmpty()) return 0f
        
        // Calculate Levenshtein distance
        val distance = levenshteinDistance(normalizedS1, normalizedS2)
        val maxLength = maxOf(normalizedS1.length, normalizedS2.length)
        
        return 1f - (distance.toFloat() / maxLength)
    }

    /**
     * Calculate Levenshtein distance between two strings
     */
    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,      // deletion
                    dp[i][j - 1] + 1,      // insertion
                    dp[i - 1][j - 1] + cost // substitution
                )
            }
        }
        
        return dp[s1.length][s2.length]
    }

    /**
     * Auto-link all variables from a template
     */
    fun autoLinkAllVariables(
        variables: List<String>,
        fileColumns: List<ColumnInfo>,
    ): Map<String, LinkResult> {
        
        val results = mutableMapOf<String, LinkResult>()
        val usedColumns = mutableSetOf<String>()
        
        variables.forEach { variable ->
            // First, try to find a column that hasn't been used
            val availableColumns = fileColumns.filter { it.name !in usedColumns }
            
            val result = if (availableColumns.isNotEmpty()) {
                autoLinkVariable(variable, availableColumns)
            } else {
                // Fall back to all columns if all are used
                autoLinkVariable(variable, fileColumns)
            }
            
            results[variable] = result
            
            if (result.matchedColumn != null) {
                usedColumns.add(result.matchedColumn)
            }
        }
        
        return results
    }

    /**
     * Result of linking a variable to a column
     */
    data class LinkResult(
        val matchedColumn: String?,
        val confidence: Float,
        val matchType: MatchType,
    )

    /**
     * Type of match found
     */
    enum class MatchType {
        EXACT,       // Exact name match
        PARTIAL,     // Partial name match
        TYPE_BASED,  // Match based on data type
        FUZZY,       // Fuzzy string matching
        NONE,        // No match found
    }
}