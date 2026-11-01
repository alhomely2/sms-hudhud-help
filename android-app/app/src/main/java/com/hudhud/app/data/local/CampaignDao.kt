package com.hudhud.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CampaignDao {
    @Query(
        """
        SELECT * FROM campaigns
        WHERE status NOT IN ('PREPARING', 'DRAFT_REVIEW')
        ORDER BY createdAt DESC, id DESC
        """,
    )
    fun observeAll(): Flow<List<CampaignEntity>>

    @Query("SELECT * FROM campaigns WHERE status = 'DRAFT_REVIEW' ORDER BY createdAt DESC, id DESC")
    fun observeDrafts(): Flow<List<CampaignEntity>>

    @Query("SELECT * FROM campaigns WHERE id = :campaignId AND status = 'DRAFT_REVIEW' LIMIT 1")
    fun loadDraft(campaignId: Long): CampaignEntity?

    @Query("SELECT * FROM campaigns WHERE id = :campaignId LIMIT 1")
    fun findById(campaignId: Long): CampaignEntity?

    @Query("SELECT * FROM campaigns WHERE id = :campaignId LIMIT 1")
    fun observeById(campaignId: Long): Flow<CampaignEntity?>

    @Query("SELECT COUNT(*) FROM campaigns")
    fun count(): Int

    @Insert
    fun insertCampaign(campaign: CampaignEntity): Long

    @Insert
    fun insertMessages(messages: List<CampaignMessageEntity>)

    @Query(
        """
        SELECT * FROM campaign_messages
        WHERE campaignId = :campaignId
        ORDER BY id
        LIMIT :limit OFFSET :offset
        """,
    )
    fun loadMessagesPage(campaignId: Long, limit: Int, offset: Int): List<CampaignMessageEntity>

    @Query("SELECT * FROM campaign_messages WHERE campaignId = :campaignId ORDER BY id")
    fun loadAllMessages(campaignId: Long): List<CampaignMessageEntity>

    @Query("SELECT * FROM campaign_messages WHERE campaignId = :campaignId ORDER BY id")
    fun observeMessages(campaignId: Long): Flow<List<CampaignMessageEntity>>

    @Query(
        """
        UPDATE campaign_messages
        SET sendStatus = :sendStatus,
            platformRef = COALESCE(:platformRef, platformRef),
            sentAt = COALESCE(:sentAt, sentAt)
        WHERE id = :messageId
        """,
    )
    fun updateMessageSendStatus(
        messageId: Long,
        sendStatus: String,
        platformRef: String? = null,
        sentAt: Long? = null,
    ): Int

    @Query("UPDATE campaign_messages SET sendStatus = :sendStatus WHERE id IN (:messageIds)")
    fun updateMessagesSendStatus(messageIds: List<Long>, sendStatus: String): Int

    @Query("UPDATE campaign_messages SET sendStatus = 'UNKNOWN' WHERE sendStatus = 'SENDING'")
    fun resetStuckSendingMessages(): Int

    @Query("SELECT COUNT(*) FROM campaign_messages WHERE campaignId = :campaignId AND sendStatus = :sendStatus")
    fun countMessagesBySendStatus(campaignId: Long, sendStatus: String): Int

    @Query("SELECT * FROM campaign_messages WHERE campaignId = :campaignId AND sendStatus = :sendStatus ORDER BY id")
    fun loadMessagesBySendStatus(campaignId: Long, sendStatus: String): List<CampaignMessageEntity>

    @Query("SELECT * FROM campaign_messages WHERE batchId = :batchId ORDER BY id")
    fun loadMessagesByBatch(batchId: Long): List<CampaignMessageEntity>

    @Query("UPDATE campaigns SET status = :status WHERE id = :campaignId")
    fun updateCampaignStatus(campaignId: Long, status: String): Int

    @Query(
        """
        UPDATE campaigns
        SET status = :status,
            totalMessages = :totalMessages,
            validMessages = :validMessages,
            reviewMessages = :reviewMessages
        WHERE id = :campaignId
        """,
    )
    fun completeCampaign(
        campaignId: Long,
        status: String,
        totalMessages: Int,
        validMessages: Int,
        reviewMessages: Int,
    )

    @Query(
        """
        UPDATE campaigns
        SET status = CASE
            WHEN reviewMessages > 0 THEN 'NEEDS_REVIEW'
            ELSE 'READY_LOCAL'
        END
        WHERE id = :campaignId AND status = 'DRAFT_REVIEW'
        """,
    )
    fun finalizeDraft(campaignId: Long): Int

    @Query("DELETE FROM campaigns WHERE id = :campaignId AND status = 'DRAFT_REVIEW'")
    fun deleteDraft(campaignId: Long): Int

    @Query("DELETE FROM campaigns WHERE id = :campaignId")
    fun deleteCampaign(campaignId: Long)
}
