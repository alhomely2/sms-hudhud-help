package com.hudhud.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "outgoing_messages",
    foreignKeys = [
        ForeignKey(
            entity = CampaignEntity::class,
            parentColumns = ["id"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("campaignId"), Index("siteProfileId", "status")],
)
data class OutgoingMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val messageId: Long,
    val siteProfileId: String,
    val status: String = STATUS_PENDING,
    val retryCount: Int = 0,
    val maxRetryAttempts: Int = 3,
    val payloadJson: String = "",
    val lastError: String = "",
    val correlationId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val lastAttemptAt: Long? = null,
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_SENDING = "SENDING"
        const val STATUS_SENT = "SENT"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_RETRY_WAITING = "RETRY_WAITING"
        const val STATUS_BLOCKED = "BLOCKED"
    }
}
