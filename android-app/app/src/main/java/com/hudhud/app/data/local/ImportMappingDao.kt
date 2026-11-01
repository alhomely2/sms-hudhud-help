package com.hudhud.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for ImportMapping operations
 */
@Dao
interface ImportMappingDao {
    
    /**
     * Observe mapping for a specific batch
     */
    @Query("SELECT * FROM import_mappings WHERE batchId = :batchId LIMIT 1")
    fun observeByBatchId(batchId: Long): Flow<ImportMappingEntity?>
    
    /**
     * Get mapping for a specific batch
     */
    @Query("SELECT * FROM import_mappings WHERE batchId = :batchId LIMIT 1")
    suspend fun findByBatchId(batchId: Long): ImportMappingEntity?
    
    /**
     * Get mapping by ID
     */
    @Query("SELECT * FROM import_mappings WHERE id = :mappingId LIMIT 1")
    suspend fun findById(mappingId: Long): ImportMappingEntity?
    
    /**
     * Insert a new mapping
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mapping: ImportMappingEntity): Long
    
    /**
     * Update an existing mapping
     */
    @Update
    suspend fun update(mapping: ImportMappingEntity): Int
    
    /**
     * Delete mapping by ID
     */
    @Query("DELETE FROM import_mappings WHERE id = :mappingId")
    suspend fun delete(mappingId: Long)
    
    /**
     * Delete mapping by batch ID
     */
    @Query("DELETE FROM import_mappings WHERE batchId = :batchId")
    suspend fun deleteByBatchId(batchId: Long)
    
    /**
     * Delete mapping
     */
    @Delete
    suspend fun delete(mapping: ImportMappingEntity)
    
    /**
     * Update phone column
     */
    @Query("UPDATE import_mappings SET phoneColumn = :phoneColumn, updatedAt = :updatedAt WHERE batchId = :batchId")
    suspend fun updatePhoneColumn(
        batchId: Long,
        phoneColumn: String,
        updatedAt: Long = System.currentTimeMillis()
    )
    
    /**
     * Update variable mappings
     */
    @Query("UPDATE import_mappings SET variableMappingsJson = :mappingsJson, updatedAt = :updatedAt WHERE batchId = :batchId")
    suspend fun updateVariableMappings(
        batchId: Long,
        mappingsJson: String,
        updatedAt: Long = System.currentTimeMillis()
    )
    
    /**
     * Mark mapping as complete
     */
    @Query("UPDATE import_mappings SET isComplete = :isComplete, updatedAt = :updatedAt WHERE batchId = :batchId")
    suspend fun setComplete(
        batchId: Long,
        isComplete: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    )
    
    /**
     * Check if mapping exists for batch
     */
    @Query("SELECT EXISTS(SELECT 1 FROM import_mappings WHERE batchId = :batchId)")
    suspend fun existsForBatch(batchId: Long): Boolean
    
    /**
     * Get count of mappings
     */
    @Query("SELECT COUNT(*) FROM import_mappings")
    suspend fun count(): Int
}