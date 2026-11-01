package com.hudhud.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "campaign_messages",
    foreignKeys = [
        ForeignKey(
            entity = CampaignEntity::class,
            parentColumns = ["id"],
            childColumns = ["campaignId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("campaignId"),
        Index("batchId"),
        Index("sendStatus"),
    ],
)
data class CampaignMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val campaignId: Long,
    val sourceRow: Int,
    val phone: String,
    val text: String,
    val status: String,
    val errors: String = "",
    val batchId: Long? = null,
    @ColumnInfo(defaultValue = "'PREPARED'") val sendStatus: String = SEND_STATUS_PREPARED,
    val platformRef: String? = null,
    val sentAt: Long? = null,
) {
    companion object {
        const val STATUS_READY = "READY"
        const val STATUS_NEEDS_REVIEW = "NEEDS_REVIEW"

        const val SEND_STATUS_PREPARED = "PREPARED"
        const val SEND_STATUS_SENDING = "SENDING"
        const val SEND_STATUS_ACCEPTED = "ACCEPTED"
        const val SEND_STATUS_FAILED = "FAILED"
        const val SEND_STATUS_UNKNOWN = "UNKNOWN"
    }
}
