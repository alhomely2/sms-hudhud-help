package com.hudhud.app.data.ai

/**
 * Finds the most common values in a dataset.
 * Used for smart fill feature to suggest values for missing data.
 */
class MostCommonValueFinder {

    /**
     * Find most common values for specified columns
     * @param dataRows List of data rows
     * @param columns Column names to analyze
     * @return Map of column name -> most common value
     */
    fun findMostCommonValues(
        dataRows: List<Map<String, String>>,
        columns: List<String>,
    ): Map<String, String> {
        
        val result = mutableMapOf<String, String>()
        
        columns.forEach { column ->
            val mostCommon = findMostCommonValue(dataRows, column)
            if (mostCommon != null) {
                result[column] = mostCommon
            }
        }
        
        return result
    }

    /**
     * Find the most common value in a specific column
     * @param dataRows List of data rows
     * @param columnName Column name to analyze
     * @return Most common value or null if no valid values found
     */
    fun findMostCommonValue(
        dataRows: List<Map<String, String>>,
        columnName: String,
    ): String? {
        
        // Count occurrences of each value
        val valueCounts = mutableMapOf<String, Int>()
        
        dataRows.forEach { row ->
            val value = row[columnName]?.trim()
            // Skip empty or blank values
            if (!value.isNullOrBlank()) {
                valueCounts[value] = valueCounts.getOrDefault(value, 0) + 1
            }
        }
        
        // Return the most common value
        return valueCounts.maxByOrNull { it.value }?.key
    }

    /**
     * Find top N most common values for a column
     * @param dataRows List of data rows
     * @param columnName Column name to analyze
     * @param topCount Number of top values to return
     * @return List of pairs (value, count)
     */
    fun findTopValues(
        dataRows: List<Map<String, String>>,
        columnName: String,
        topCount: Int = 5,
    ): List<Pair<String, Int>> {
        
        val valueCounts = mutableMapOf<String, Int>()
        
        dataRows.forEach { row ->
            val value = row[columnName]?.trim()
            if (!value.isNullOrBlank()) {
                valueCounts[value] = valueCounts.getOrDefault(value, 0) + 1
            }
        }
        
        return valueCounts
            .toList()
            .sortedByDescending { it.second }
            .take(topCount)
    }

    /**
     * Calculate value frequency distribution for a column
     * @param dataRows List of data rows
     * @param columnName Column name to analyze
     * @return Map of value -> percentage
     */
    fun getValueDistribution(
        dataRows: List<Map<String, String>>,
        columnName: String,
    ): Map<String, Float> {
        
        val total = dataRows.size
        if (total == 0) return emptyMap()
        
        val valueCounts = mutableMapOf<String, Int>()
        
        dataRows.forEach { row ->
            val value = row[columnName]?.trim() ?: "EMPTY"
            valueCounts[value] = valueCounts.getOrDefault(value, 0) + 1
        }
        
        return valueCounts.mapValues { it.value.toFloat() / total }
    }

    /**
     * Check if a column has mostly the same value
     * @param dataRows List of data rows
     * @param columnName Column name to analyze
     * @param threshold Percentage threshold (0-1) to consider "mostly same"
     * @return true if more than threshold% have the same value
     */
    fun isMostlyConstant(
        dataRows: List<Map<String, String>>,
        columnName: String,
        threshold: Float = 0.9f,
    ): Boolean {
        
        val distribution = getValueDistribution(dataRows, columnName)
        
        // Check if any value exceeds threshold
        return distribution.values.any { it >= threshold }
    }
}