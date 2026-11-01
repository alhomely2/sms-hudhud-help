package com.hudhud.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for ImportBatch operations
 */
@Dao
interface ImportBatchDao {
    
    /**
     * Observe all batches ordered by creation date
     */
    @Query("SELECT * FROM import_batches ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<ImportBatchEntity>>
    
    /**
     * Observe pending batches
     */
    @Query("SELECT * FROM import_batches WHERE status = :status ORDER BY createdAt DESC")
    fun observeByStatus(status: String): Flow<List<ImportBatchEntity>>
    
    /**
     * Observe a specific batch by ID
     */
    @Query("SELECT * FROM import_batches WHERE id = :batchId LIMIT 1")
    fun observeById(batchId: Long): Flow<ImportBatchEntity?>
    
    /**
     * Get a batch by ID synchronously
     */
    @Query("SELECT * FROM import_batches WHERE id = :batchId LIMIT 1")
    suspend fun findById(batchId: Long): ImportBatchEntity?
    
    /**
     * Get all batches
     */
    @Query("SELECT * FROM import_batches ORDER BY createdAt DESC, id DESC")
    suspend fun getAll(): List<ImportBatchEntity>
    
    /**
     * Get batches by import mode
     */
    @Query("SELECT * FROM import_batches WHERE importMode = :mode ORDER BY createdAt DESC")
    fun observeByMode(mode: String): Flow<List<ImportBatchEntity>>
    
    /**
     * Insert a new batch
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(batch: ImportBatchEntity): Long
    
    /**
     * Update an existing batch
     */
    @Update
    suspend fun update(batch: ImportBatchEntity): Int
    
    /**
     * Delete a batch by ID
     */
    @Query("DELETE FROM import_batches WHERE id = :batchId")
    suspend fun delete(batchId: Long)
    
    /**
     * Delete a batch
     */
    @Delete
    suspend fun delete(batch: ImportBatchEntity)
    
    /**
     * Update batch status
     */
    @Query("UPDATE import_batches SET status = :status, updatedAt = :updatedAt WHERE id = :batchId")
    suspend fun updateStatus(batchId: Long, status: String, updatedAt: Long = System.currentTimeMillis())
    
    /**
     * Update batch statistics
     */
    @Query("""
        UPDATE import_batches 
        SET totalRows = :totalRows, 
            validRows = :validRows, 
            errorRows = :errorRows,
            updatedAt = :updatedAt 
        WHERE id = :batchId
    """)
    suspend fun updateStatistics(
        batchId: Long,
        totalRows: Int,
        validRows: Int,
        errorRows: Int,
        updatedAt: Long = System.currentTimeMillis()
    )
    
    /**
     * Get batch count
     */
    @Query("SELECT COUNT(*) FROM import_batches")
    suspend fun count(): Int
    
    /**
     * Get count by status
     */
    @Query("SELECT COUNT(*) FROM import_batches WHERE status = :status")
    suspend fun countByStatus(status: String): Int
    
    /**
     * Get recent batches (last 10)
     */
    @Query("SELECT * FROM import_batches ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 10): Flow<List<ImportBatchEntity>>
}