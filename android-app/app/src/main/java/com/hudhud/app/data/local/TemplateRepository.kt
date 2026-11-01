package com.hudhud.app.data.local

import com.hudhud.app.domain.model.FixedData
import com.hudhud.app.domain.model.SavedTemplate
import com.hudhud.app.domain.model.TemplateField
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Read/write access to saved templates.
 *
 * Creating and updating are separate operations: an edit always targets the
 * existing row and only refreshes `updatedAt`, so the operator never ends up
 * with a duplicate template after fixing a typo.
 *
 * Supports fixed data (shared between all clients) and variable data (per client).
 */
class TemplateRepository(
    private val dao: TemplateDao,
) {
    fun observeTemplates(): Flow<List<SavedTemplate>> =
        dao.observeAll().map { templates -> templates.map { TemplateJsonConverter.entityToSavedTemplate(it) } }

    fun observeTemplate(templateId: Long): Flow<SavedTemplate?> =
        dao.observeById(templateId).map { it?.let(TemplateJsonConverter::entityToSavedTemplate) }

    suspend fun loadTemplate(templateId: Long): SavedTemplate? =
        dao.findById(templateId)?.let(TemplateJsonConverter::entityToSavedTemplate)

    /**
     * Create a new template with all data including fixed and variable data.
     */
    suspend fun createTemplate(
        name: String,
        body: String,
        phoneColumn: String = "",
        variableColumns: Map<String, String> = emptyMap(),
        fixedValues: Map<String, String> = emptyMap(),
        selectedFields: List<TemplateField> = emptyList(),
        fixedData: List<FixedData> = emptyList(),
        sourceFileName: String = "",
        documentHeaders: List<String> = emptyList(),
        documentSample: Map<String, String> = emptyMap(),
        sourceType: String = TemplateEntity.SOURCE_USER,
        sourceVersion: Int = 1,
    ): Long {
        requireDraft(name, body)
        val now = System.currentTimeMillis()
        return dao.insert(
            TemplateEntity(
                name = name.trim(),
                body = body.trim(),
                phoneColumn = phoneColumn,
                variableColumnsJson = TemplateJsonConverter.variableColumnsToJson(variableColumns),
                fixedValuesJson = TemplateJsonConverter.fixedValuesToJson(fixedValues),
                selectedFieldsJson = TemplateJsonConverter.selectedFieldsToJson(selectedFields),
                fixedDataJson = TemplateJsonConverter.fixedDataToJson(fixedData),
                sourceFileName = sourceFileName,
                documentHeadersJson = TemplateJsonConverter.documentHeadersToJson(documentHeaders),
                documentSampleJson = TemplateJsonConverter.documentSampleToJson(documentSample),
                createdAt = now,
                updatedAt = now,
                sourceType = sourceType,
                sourceVersion = sourceVersion,
            ),
        )
    }

    /** Returns false when the template no longer exists. */
    suspend fun updateTemplate(
        templateId: Long,
        name: String,
        body: String,
        phoneColumn: String = "",
        variableColumns: Map<String, String> = emptyMap(),
        fixedValues: Map<String, String> = emptyMap(),
        selectedFields: List<TemplateField> = emptyList(),
        fixedData: List<FixedData> = emptyList(),
        sourceFileName: String = "",
        documentHeaders: List<String> = emptyList(),
        documentSample: Map<String, String> = emptyMap(),
    ): Boolean {
        requireDraft(name, body)
        val existing = dao.findById(templateId) ?: return false
        return dao.update(
            existing.copy(
                name = name.trim(),
                body = body.trim(),
                phoneColumn = phoneColumn,
                variableColumnsJson = TemplateJsonConverter.variableColumnsToJson(variableColumns),
                fixedValuesJson = TemplateJsonConverter.fixedValuesToJson(fixedValues),
                selectedFieldsJson = TemplateJsonConverter.selectedFieldsToJson(selectedFields),
                fixedDataJson = TemplateJsonConverter.fixedDataToJson(fixedData),
                sourceFileName = sourceFileName,
                documentHeadersJson = TemplateJsonConverter.documentHeadersToJson(documentHeaders),
                documentSampleJson = TemplateJsonConverter.documentSampleToJson(documentSample),
                updatedAt = System.currentTimeMillis(),
            ),
        ) > 0
    }

    suspend fun duplicateTemplate(templateId: Long, copyName: String): Long? {
        require(copyName.isNotBlank()) { "Template name must not be blank" }
        val existing = dao.findById(templateId) ?: return null
        val now = System.currentTimeMillis()
        return dao.insert(
            existing.copy(
                id = 0,
                name = copyName.trim(),
                createdAt = now,
                updatedAt = now,
            ),
        )
    }

    suspend fun deleteTemplate(templateId: Long) {
        dao.delete(templateId)
    }

    /** Re-inserts a deleted template with its original id, for undo. */
    suspend fun restoreTemplate(template: SavedTemplate) {
        dao.insert(template.toEntity())
    }

    private fun requireDraft(name: String, body: String) {
        require(name.isNotBlank()) { "Template name must not be blank" }
        require(body.isNotBlank()) { "Template body must not be blank" }
    }
}

/**
 * Extension function to convert SavedTemplate to TemplateEntity for database storage.
 */
private fun SavedTemplate.toEntity() = TemplateEntity(
id = id,
name = name,
body = body,
phoneColumn = phoneColumn,
variableColumnsJson = TemplateJsonConverter.variableColumnsToJson(variableColumns),
fixedValuesJson = TemplateJsonConverter.fixedValuesToJson(fixedValues),
selectedFieldsJson = TemplateJsonConverter.selectedFieldsToJson(selectedFields),
fixedDataJson = TemplateJsonConverter.fixedDataToJson(fixedData),
sourceFileName = sourceFileName,
documentHeadersJson = TemplateJsonConverter.documentHeadersToJson(documentHeaders),
documentSampleJson = TemplateJsonConverter.documentSampleToJson(documentSample),
createdAt = createdAt,
updatedAt = updatedAt,
)
