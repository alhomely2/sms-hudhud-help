package com.hudhud.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing an import batch that holds imported data from a file.
 * Stores the batch metadata including import mode, source file info, and template snapshot.
 */
@Entity(tableName = "import_batches")
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    
    // Batch identification
    val name: String,
    
    // Source file information
    val fileName: String,
    val fileUri: String,
    
    // Import mode: READY_TEXT (messages ready) or RAW_DATA (needs template)
    val importMode: String = MODE_RAW_DATA,
    
    // Template information snapshot (stored at import time)
    val templateId: Long? = null,
    val templateName: String? = null,
    val templateBodySnapshot: String? = null,
    val templateVariablesSnapshot: String? = null, // JSON array of variable names
    
    // Batch status
    val status: String = STATUS_PENDING,
    
    // Statistics
    val totalRows: Int = 0,
    val validRows: Int = 0,
    val errorRows: Int = 0,
    
    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    
    // Notes
    val notes: String = "",
) {
    companion object {
        // Import modes
        const val MODE_READY_TEXT = "READY_TEXT" // File contains ready messages
        const val MODE_RAW_DATA = "RAW_DATA"     // File needs template processing
        
        // Status values
        const val STATUS_PENDING = "PENDING"
        const val STATUS_PROCESSING = "PROCESSING"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_CANCELLED = "CANCELLED"
    }
}