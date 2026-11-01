package com.hudhud.app.data.assets

import android.content.Context
import com.hudhud.app.data.local.TemplateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Handles loading built-in templates from assets and seeding them into the database.
 */
class BuiltInTemplateSeeder(
    private val context: Context,
    private val repository: TemplateRepository,
) {
    /**
     * Seeds templates if the database is empty or according to logic defined in
     * HUDHUD-TECHNOLOGY-SPECIFICATION.md.
     */
    suspend fun seedIfNeeded() = withContext(Dispatchers.IO) {
        // Simple check: if no templates exist, seed them.
        // In a real app, we might check a version or a flag in SharedPreferences.
        val existing = repository.loadTemplate(1) // Check if at least one exists
        if (existing == null) {
            seed()
        }
    }

    private suspend fun seed() {
        val jsonString = context.assets.open("templates/built_in_templates.json")
            .bufferedReader().use { it.readText() }
        
        val jsonArray = JSONArray(jsonString)
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val name = obj.getString("name")
            val body = obj.getString("body")
            val variableColumns = mutableMapOf<String, String>()
            
            if (obj.has("variableColumns")) {
                val vars = obj.getJSONObject("variableColumns")
                val keys = vars.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    variableColumns[key] = vars.getString(key)
                }
            }
            
            repository.createTemplate(
                name = name,
                body = body,
                variableColumns = variableColumns,
                sourceType = com.hudhud.app.data.local.TemplateEntity.SOURCE_BUILT_IN,
            )
        }
    }
}
