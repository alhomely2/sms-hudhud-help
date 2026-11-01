package com.hudhud.app.data.repository

import com.hudhud.app.data.local.AppDatabase
import com.hudhud.app.data.local.TemplateEntity
import com.hudhud.app.domain.model.SavedTemplate
import com.hudhud.app.domain.model.MessageTemplate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * Repository for template operations.
 * Handles CRUD operations for templates (both built-in and user-created).
 */
class TemplateRepository(private val database: AppDatabase) {

    private val templateDao = database.templateDao()

    /**
     * Observe all templates
     */
    fun observeAll(): Flow<List<SavedTemplate>> {
        return templateDao.observeAll().map { entities ->
            entities.map { it.toDomainModel() }
        }
    }

    /**
     * Observe a specific template by ID
     */
    fun observeById(templateId: Long): Flow<SavedTemplate?> {
        return templateDao.observeById(templateId).map { it?.toDomainModel() }
    }

    /**
     * Get a template by ID
     */
    suspend fun getById(templateId: Long): SavedTemplate? {
        return templateDao.findById(templateId)?.toDomainModel()
    }

    /**
     * Get all templates
     */
    suspend fun getAll(): List<SavedTemplate> {
        return templateDao.getAll()?.map { it.toDomainModel() } ?: emptyList()
    }

    /**
     * Get templates by source type
     */
    suspend fun getBySourceType(isBuiltIn: Boolean): List<SavedTemplate> {
        return templateDao.getAll()?.filter { it.isBuiltIn() == isBuiltIn }?.map { it.toDomainModel() } 
            ?: emptyList()
    }

    /**
     * Create a new template
     */
    suspend fun create(template: SavedTemplate): Long {
        val entity = template.toEntity()
        return templateDao.insert(entity)
    }

    /**
     * Update an existing template
     */
    suspend fun update(template: SavedTemplate): Boolean {
        val entity = template.toEntity().copy(updatedAt = System.currentTimeMillis())
        return templateDao.update(entity) > 0
    }

    /**
     * Delete a template
     */
    suspend fun delete(templateId: Long): Boolean {
        return try {
            templateDao.delete(templateId)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get template count
     */
    suspend fun count(): Int {
        return templateDao.count()
    }

    /**
     * Get templates for display (user templates first, then built-in)
     */
    fun observeAllSorted(): Flow<List<SavedTemplate>> {
        return templateDao.observeAll().map { entities ->
            entities
                .sortedWith(compareBy({ !it.isBuiltIn() }, { -it.updatedAt }))
                .map { it.toDomainModel() }
        }
    }

    /**
     * Convert entity to domain model
     */
    private fun TemplateEntity.toDomainModel(): SavedTemplate {
        return SavedTemplate(
            id = id,
            name = name,
            body = body,
            phoneColumn = phoneColumn,
            variableColumns = parseVariableColumns(variableColumnsJson),
            fixedValues = parseFixedValues(fixedValuesJson),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    /**
     * Convert domain model to entity
     */
    private fun SavedTemplate.toEntity(): TemplateEntity {
        return TemplateEntity(
            id = id,
            name = name,
            body = body,
            phoneColumn = phoneColumn,
            variableColumnsJson = variableColumns.toJsonString(),
            fixedValuesJson = fixedValues.toJsonString(),
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

    private fun parseVariableColumns(json: String): Map<String, String> {
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun parseFixedValues(json: String): Map<String, String> {
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.getString(it) }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun Map<String, String>.toJsonString(): String {
        return JSONObject(this).toString()
    }

    /**
     * Extension to check if template is built-in
     */
    private fun TemplateEntity.isBuiltIn(): Boolean {
        return sourceType == TemplateEntity.SOURCE_BUILT_IN
    }
}