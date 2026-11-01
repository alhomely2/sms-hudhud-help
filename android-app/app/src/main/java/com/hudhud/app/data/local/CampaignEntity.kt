package com.hudhud.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "campaigns")
data class CampaignEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val status: String = STATUS_PREPARING,
    val totalMessages: Int = 0,
    val validMessages: Int = 0,
    val reviewMessages: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "'RAW_DATA'") val importMode: String = MODE_RAW_DATA,
    val batchId: Long? = null,
) {
    companion object {
        const val STATUS_PREPARING = "PREPARING"
        const val STATUS_DRAFT_REVIEW = "DRAFT_REVIEW"
        const val STATUS_READY_LOCAL = "READY_LOCAL"
        const val STATUS_NEEDS_REVIEW = "NEEDS_REVIEW"
        const val STATUS_SENDING = "SENDING"
        const val STATUS_PAUSED = "PAUSED"
        const val STATUS_COMPLETED = "COMPLETED"

        const val MODE_READY_TEXT = "READY_TEXT"
        const val MODE_RAW_DATA = "RAW_DATA"
    }
}
