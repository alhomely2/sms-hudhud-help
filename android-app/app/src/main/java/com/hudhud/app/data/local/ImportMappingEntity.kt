package com.hudhud.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing the mapping between template variables and file columns.
 * This mapping is specific to each file/batch and is not stored within the template.
 */
@Entity(
    tableName = "import_mappings",
    foreignKeys = [
        ForeignKey(
            entity = ImportBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batchId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("batchId")],
)
data class ImportMappingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    
    // Reference to the batch this mapping belongs to
    val batchId: Long,
    
    // Phone column mapping
    val phoneColumn: String, // Column name from the source file
    
    // Variable mappings (template variable -> file column)
    // Stored as JSON: {"{{Name}}": "customer_name", "{{Amount}}": "amount_due"}
    val variableMappingsJson: String = "{}",
    
    // Additional metadata
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    
    // Mapping status
    val isComplete: Boolean = false,
    
    // Notes
    val notes: String = "",
) {
    companion object {
        const val DEFAULT_PHONE_COLUMN = "phone"
    }
}

/**
 * Represents a single variable mapping entry
 */
data class VariableMapping(
    val templateVariable: String,  // e.g., "{{Name}}"
    val fileColumn: String,         // e.g., "customer_name"
    val confidence: Float = 0f,     // 0.0 to 1.0, AI confidence score
    val isAutoDetected: Boolean = false,
)

/**
 * Represents the complete mapping configuration
 */
data class MappingConfig(
    val phoneColumn: String,
    val variableMappings: List<VariableMapping>,
    val isComplete: Boolean,
)