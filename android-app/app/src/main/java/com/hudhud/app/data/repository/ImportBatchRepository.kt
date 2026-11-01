package com.hudhud.app.data.repository

import com.hudhud.app.data.local.AppDatabase
import com.hudhud.app.data.local.ImportBatchEntity
import com.hudhud.app.data.local.ImportMappingEntity
import com.hudhud.app.data.local.MappingConfig
import com.hudhud.app.data.local.VariableMapping
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * Repository for import batch and mapping operations.
 * Handles CRUD operations for import batches and their column mappings.
 */
class ImportBatchRepository(private val database: AppDatabase) {

    private val importBatchDao = database.importBatchDao()
    private val importMappingDao = database.importMappingDao()

    // ========== Import Batch Operations ==========

    /**
     * Observe all import batches
     */
    fun observeAllBatches(): Flow<List<ImportBatchEntity>> {
        return importBatchDao.observeAll()
    }

    /**
     * Observe batches by status
     */
    fun observeBatchesByStatus(status: String): Flow<List<ImportBatchEntity>> {
        return importBatchDao.observeByStatus(status)
    }

    /**
     * Observe batches by import mode
     */
    fun observeBatchesByMode(mode: String): Flow<List<ImportBatchEntity>> {
        return importBatchDao.observeByMode(mode)
    }

    /**
     * Observe a specific batch
     */
    fun observeBatch(batchId: Long): Flow<ImportBatchEntity?> {
        return importBatchDao.observeById(batchId)
    }

    /**
     * Get a batch by ID
     */
    suspend fun getBatchById(batchId: Long): ImportBatchEntity? {
        return importBatchDao.findById(batchId)
    }

    /**
     * Get all batches
     */
    suspend fun getAllBatches(): List<ImportBatchEntity> {
        return importBatchDao.getAll()
    }

    /**
     * Create a new import batch
     */
    suspend fun createBatch(batch: ImportBatchEntity): Long {
        return importBatchDao.insert(batch)
    }

    /**
     * Create a batch with basic info
     */
    suspend fun createBatch(
        name: String,
        fileName: String,
        fileUri: String,
        importMode: String,
        templateId: Long? = null,
        templateName: String? = null,
        templateBodySnapshot: String? = null,
        templateVariablesSnapshot: String? = null,
    ): Long {
        val batch = ImportBatchEntity(
            name = name,
            fileName = fileName,
            fileUri = fileUri,
            importMode = importMode,
            templateId = templateId,
            templateName = templateName,
            templateBodySnapshot = templateBodySnapshot,
            templateVariablesSnapshot = templateVariablesSnapshot,
            status = ImportBatchEntity.STATUS_PENDING,
        )
        return importBatchDao.insert(batch)
    }

    /**
     * Update a batch
     */
    suspend fun updateBatch(batch: ImportBatchEntity): Boolean {
        return importBatchDao.update(batch.copy(updatedAt = System.currentTimeMillis())) > 0
    }

    /**
     * Delete a batch
     */
    suspend fun deleteBatch(batchId: Long): Boolean {
        return try {
            importBatchDao.delete(batchId)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Update batch status
     */
    suspend fun updateBatchStatus(batchId: Long, status: String): Boolean {
        return try {
            importBatchDao.updateStatus(batchId, status)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Update batch statistics
     */
    suspend fun updateBatchStatistics(
        batchId: Long,
        totalRows: Int,
        validRows: Int,
        errorRows: Int,
    ): Boolean {
        return try {
            importBatchDao.updateStatistics(batchId, totalRows, validRows, errorRows)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get recent batches
     */
    fun observeRecentBatches(limit: Int = 10): Flow<List<ImportBatchEntity>> {
        return importBatchDao.observeRecent(limit)
    }

    // ========== Import Mapping Operations ==========

    /**
     * Observe mapping for a batch
     */
    fun observeMapping(batchId: Long): Flow<ImportMappingEntity?> {
        return importMappingDao.observeByBatchId(batchId)
    }

    /**
     * Get mapping for a batch
     */
    suspend fun getMapping(batchId: Long): ImportMappingEntity? {
        return importMappingDao.findByBatchId(batchId)
    }

    /**
     * Get mapping as config object
     */
    suspend fun getMappingConfig(batchId: Long): MappingConfig? {
        val mapping = importMappingDao.findByBatchId(batchId) ?: return null
        return MappingConfig(
            phoneColumn = mapping.phoneColumn,
            variableMappings = parseVariableMappings(mapping.variableMappingsJson),
            isComplete = mapping.isComplete,
        )
    }

    /**
     * Create or update mapping
     */
    suspend fun saveMapping(
        batchId: Long,
        phoneColumn: String,
        variableMappings: Map<String, String>,
    ): Long {
        val existingMapping = importMappingDao.findByBatchId(batchId)
        
        val entity = ImportMappingEntity(
            id = existingMapping?.id ?: 0,
            batchId = batchId,
            phoneColumn = phoneColumn,
            variableMappingsJson = JSONObject(variableMappings).toString(),
            isComplete = variableMappings.isNotEmpty() && phoneColumn.isNotBlank(),
        )
        
        return importMappingDao.insert(entity)
    }

    /**
     * Update phone column only
     */
    suspend fun updatePhoneColumn(batchId: Long, phoneColumn: String): Boolean {
        return try {
            importMappingDao.updatePhoneColumn(batchId, phoneColumn)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Update variable mappings only
     */
    suspend fun updateVariableMappings(
        batchId: Long,
        variableMappings: Map<String, String>,
    ): Boolean {
        return try {
            importMappingDao.updateVariableMappings(
                batchId,
                JSONObject(variableMappings).toString(),
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Mark mapping as complete
     */
    suspend fun completeMapping(batchId: Long): Boolean {
        return try {
            importMappingDao.setComplete(batchId, true)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Delete mapping for a batch
     */
    suspend fun deleteMapping(batchId: Long): Boolean {
        return try {
            importMappingDao.deleteByBatchId(batchId)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ========== Helper Functions ==========

    /**
     * Parse variable mappings from JSON
     */
    private fun parseVariableMappings(json: String): List<VariableMapping> {
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().map { variable ->
                VariableMapping(
                    templateVariable = variable,
                    fileColumn = obj.getString(variable),
                    confidence = 1.0f, // User confirmed
                    isAutoDetected = false,
                )
            }.toList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Get batch count
     */
    suspend fun getBatchCount(): Int {
        return importBatchDao.count()
    }

    /**
     * Get count by status
     */
    suspend fun getBatchCountByStatus(status: String): Int {
        return importBatchDao.countByStatus(status)
    }

    /**
     * Check if mapping exists for batch
     */
    suspend fun hasMapping(batchId: Long): Boolean {
        return importMappingDao.existsForBatch(batchId)
    }
}