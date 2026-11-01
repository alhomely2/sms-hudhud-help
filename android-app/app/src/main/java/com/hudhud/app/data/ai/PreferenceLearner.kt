package com.hudhud.app.data.ai

import android.content.Context
import android.content.SharedPreferences
import com.hudhud.app.domain.model.SavedTemplate

/**
 * Learns from user preferences and improves suggestions over time.
 * Stores user decisions and uses them to improve future suggestions.
 */
class PreferenceLearner(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    // Maximum number of decisions to keep
    private val maxDecisions = 100

    /**
     * Record a template selection decision
     */
    fun recordTemplateSelection(templateId: Long, templateName: String) {
        val key = "template_$templateId"
        val count = prefs.getInt(key, 0) + 1
        prefs.edit()
            .putInt(key, count)
            .putString("template_name_$templateId", templateName)
            .apply()
        
        // Update last used timestamp
        prefs.edit()
            .putLong("last_template_$templateId", System.currentTimeMillis())
            .apply()
        
        // Keep track of recently used templates
        addToRecentTemplates(templateId)
    }

    /**
     * Record column selection for phone
     */
    fun recordPhoneColumnSelection(fileType: String, columnName: String) {
        val key = "phone_column_${fileType}_$columnName"
        val count = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, count).apply()
    }

    /**
     * Record column selection for a specific variable
     */
    fun recordVariableColumnSelection(
        fileType: String,
        templateVariable: String,
        columnName: String,
    ) {
        val key = "var_column_${fileType}_${templateVariable}_$columnName"
        val count = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, count).apply()
    }

    /**
     * Record mapping decision (variable -> column)
     */
    fun recordMappingDecision(
        templateId: Long,
        mapping: Map<String, String>,
    ) {
        // Store the mapping as a string
        val mappingStr = mapping.entries.joinToString(";") { "${it.key}=${it.value}" }
        val key = "mapping_$templateId"
        
        // Get existing mappings
        val existing = prefs.getStringSet(key, mutableSetOf()) ?: mutableSetOf()
        existing.add(mappingStr)
        
        // Keep only recent mappings
        if (existing.size > maxDecisions) {
            // Remove oldest (keep recent)
            val sorted = existing.sortedByDescending {
                prefs.getLong("mapping_time_${it.hashCode()}", 0)
            }.take(maxDecisions)
            prefs.edit().putStringSet(key, sorted.toMutableSet()).apply()
        } else {
            prefs.edit()
                .putStringSet(key, existing)
                .putLong("mapping_time_${mappingStr.hashCode()}", System.currentTimeMillis())
                .apply()
        }
    }

    /**
     * Record user's choice to use smart fill
     */
    fun recordSmartFillUsage(used: Boolean) {
        val key = "smart_fill_used"
        val count = prefs.getInt(key, 0)
        if (used) {
            prefs.edit().putInt(key, count + 1).apply()
        }
        
        // Also record timestamp
        prefs.edit()
            .putLong("last_smart_fill", System.currentTimeMillis())
            .apply()
    }

    /**
     * Record default value usage for a variable
     */
    fun recordDefaultValueUsage(
        templateId: Long,
        variable: String,
        defaultValue: String,
    ) {
        val key = "default_${templateId}_${variable}"
        prefs.edit()
            .putString(key, defaultValue)
            .putLong("default_time_${templateId}_${variable}", System.currentTimeMillis())
            .apply()
    }

    /**
     * Get template usage count for boosting suggestions
     */
    fun getTemplateUsageCount(templateId: Long): Int {
        return prefs.getInt("template_$templateId", 0)
    }

    /**
     * Get recently used templates (ordered by last use)
     */
    fun getRecentTemplates(limit: Int = 10): List<Long> {
        val allKeys = prefs.all.keys
            .filter { it.startsWith("last_template_") && it != "last_template_" }
            .map { key ->
                val templateId = key.removePrefix("last_template_").toLongOrNull() ?: 0L
                val time = prefs.getLong(key, 0)
                templateId to time
            }
            .filter { it.first > 0 }
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
        
        return allKeys
    }

    /**
     * Get most used phone column for a file type
     */
    fun getMostUsedPhoneColumn(fileType: String): String? {
        val prefix = "phone_column_${fileType}_"
        val columns = prefs.all.keys
            .filter { it.startsWith(prefix) }
            .map { key ->
                val columnName = key.removePrefix(prefix)
                val count = prefs.getInt(key, 0)
                columnName to count
            }
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }
        
        return columns.firstOrNull()
    }

    /**
     * Get most used column for a variable
     */
    fun getMostUsedVariableColumn(
        fileType: String,
        templateVariable: String,
    ): String? {
        val prefix = "var_column_${fileType}_${templateVariable}_"
        val columns = prefs.all.keys
            .filter { it.startsWith(prefix) }
            .map { key ->
                val columnName = key.removePrefix(prefix)
                val count = prefs.getInt(key, 0)
                columnName to count
            }
            .sortedByDescending { it.second }
            .take(3)
            .map { it.first }
        
        return columns.firstOrNull()
    }

    /**
     * Get default value for a variable
     */
    fun getDefaultValue(templateId: Long, variable: String): String? {
        return prefs.getString("default_${templateId}_$variable", null)
    }

    /**
     * Check if smart fill is frequently used
     */
    fun isSmartFillFrequent(): Boolean {
        val count = prefs.getInt("smart_fill_used", 0)
        return count >= 3
    }

    /**
     * Clear all preferences (for testing or reset)
     */
    fun clearAll() {
        prefs.edit().clear().apply()
    }

    /**
     * Get preference statistics for debugging
     */
    fun getStatistics(): PreferenceStats {
        val templateCount = prefs.all.keys.count { it.startsWith("template_") && !it.contains("name_") && !it.contains("last_") }
        val mappingCount = prefs.getStringSet("mapping_*", emptySet())?.size ?: 0
        
        return PreferenceStats(
            totalTemplateSelections = templateCount,
            totalMappings = mappingCount,
            lastUpdate = prefs.getLong("last_smart_fill", 0),
        )
    }

    private fun addToRecentTemplates(templateId: Long) {
        val key = "recent_templates"
        val recent = prefs.getStringSet(key, mutableSetOf())?.toMutableSet() ?: mutableSetOf()
        recent.add(templateId.toString())
        
        // Keep only recent 20
        if (recent.size > 20) {
            val sorted = recent.mapNotNull { it.toLongOrNull() }
                .sortedByDescending { prefs.getLong("last_template_$it", 0) }
                .take(20)
                .map { it.toString() }
            prefs.edit().putStringSet(key, sorted.toMutableSet()).apply()
        } else {
            prefs.edit().putStringSet(key, recent).apply()
        }
    }

    /**
     * Data class for preference statistics
     */
    data class PreferenceStats(
        val totalTemplateSelections: Int,
        val totalMappings: Int,
        val lastUpdate: Long,
    )

    companion object {
        private const val PREFS_NAME = "hudhud_preference_learner"
    }
}