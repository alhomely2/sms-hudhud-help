package com.hudhud.app.data.local

import com.hudhud.app.domain.model.FixedData
import com.hudhud.app.domain.model.SavedTemplate
import com.hudhud.app.domain.model.TemplateField
import org.json.JSONArray
import org.json.JSONObject

/**
 * Utility object for converting template data to/from JSON for database storage.
 */
object TemplateJsonConverter {

    // ========== Selected Fields (TemplateField) ==========

    fun selectedFieldsToJson(fields: List<TemplateField>): String {
        val jsonArray = JSONArray()
        fields.forEach { field ->
            val jsonObject = JSONObject().apply {
                put("name", field.name)
                put("beforeText", field.beforeText)
                put("afterText", field.afterText)
                put("newLineAfter", field.newLineAfter)
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }

    fun jsonToSelectedFields(json: String): List<TemplateField> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                TemplateField(
                    name = obj.getString("name"),
                    beforeText = obj.optString("beforeText", ""),
                    afterText = obj.optString("afterText", ""),
                    newLineAfter = obj.optBoolean("newLineAfter", false)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ========== Fixed Data (FixedData) ==========

    fun fixedDataToJson(data: List<FixedData>): String {
        val jsonArray = JSONArray()
        data.forEach { item ->
            val jsonObject = JSONObject().apply {
                put("name", item.name)
                put("value", item.value)
                put("beforeText", item.beforeText)
                put("afterText", item.afterText)
                put("newLineAfter", item.newLineAfter)
            }
            jsonArray.put(jsonObject)
        }
        return jsonArray.toString()
    }

    fun jsonToFixedData(json: String): List<FixedData> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { i ->
                val obj = jsonArray.getJSONObject(i)
                FixedData(
                    name = obj.getString("name"),
                    value = obj.getString("value"),
                    beforeText = obj.optString("beforeText", ""),
                    afterText = obj.optString("afterText", ""),
                    newLineAfter = obj.optBoolean("newLineAfter", false)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ========== Document Headers ==========

    fun documentHeadersToJson(headers: List<String>): String {
        val jsonArray = JSONArray()
        headers.forEach { jsonArray.put(it) }
        return jsonArray.toString()
    }

    fun jsonToDocumentHeaders(json: String): List<String> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ========== Document Sample Data ==========

    fun documentSampleToJson(sample: Map<String, String>): String {
        val jsonObject = JSONObject()
        sample.forEach { (key, value) ->
            jsonObject.put(key, value)
        }
        return jsonObject.toString()
    }

    fun jsonToDocumentSample(json: String): Map<String, String> {
        if (json.isBlank() || json == "{}") return emptyMap()
        return try {
            val jsonObject = JSONObject(json)
            val result = mutableMapOf<String, String>()
            jsonObject.keys().forEach { key ->
                result[key] = jsonObject.getString(key)
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ========== Variable Columns (Map) ==========

    fun variableColumnsToJson(columns: Map<String, String>): String {
        val jsonObject = JSONObject()
        columns.forEach { (key, value) ->
            jsonObject.put(key, value)
        }
        return jsonObject.toString()
    }

    fun jsonToVariableColumns(json: String): Map<String, String> {
        if (json.isBlank() || json == "{}") return emptyMap()
        return try {
            val jsonObject = JSONObject(json)
            val result = mutableMapOf<String, String>()
            jsonObject.keys().forEach { key ->
                result[key] = jsonObject.getString(key)
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ========== Fixed Values (Map) ==========

    fun fixedValuesToJson(values: Map<String, String>): String {
        val jsonObject = JSONObject()
        values.forEach { (key, value) ->
            jsonObject.put(key, value)
        }
        return jsonObject.toString()
    }

    fun jsonToFixedValues(json: String): Map<String, String> {
        if (json.isBlank() || json == "{}") return emptyMap()
        return try {
            val jsonObject = JSONObject(json)
            val result = mutableMapOf<String, String>()
            jsonObject.keys().forEach { key ->
                result[key] = jsonObject.getString(key)
            }
            result
        } catch (e: Exception) {
            emptyMap()
        }
    }

    // ========== Convert Entity to Domain Model ==========

    fun entityToSavedTemplate(entity: TemplateEntity): SavedTemplate {
        return SavedTemplate(
            id = entity.id,
            name = entity.name,
            body = entity.body,
            phoneColumn = entity.phoneColumn,
            variableColumns = jsonToVariableColumns(entity.variableColumnsJson),
            fixedValues = jsonToFixedValues(entity.fixedValuesJson),
            selectedFields = jsonToSelectedFields(entity.selectedFieldsJson),
            fixedData = jsonToFixedData(entity.fixedDataJson),
            sourceFileName = entity.sourceFileName,
            documentHeaders = jsonToDocumentHeaders(entity.documentHeadersJson),
            documentSample = jsonToDocumentSample(entity.documentSampleJson),
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
        )
    }
}