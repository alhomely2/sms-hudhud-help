package com.hudhud.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OutgoingMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: OutgoingMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<OutgoingMessageEntity>): List<Long>

    @Query(
        """
        SELECT * FROM outgoing_messages
        WHERE siteProfileId = :siteProfileId
          AND status IN ('PENDING', 'RETRY_WAITING')
        ORDER BY createdAt ASC, id ASC
        LIMIT :limit
        """,
    )
    suspend fun loadPendingBatch(siteProfileId: String, limit: Int): List<OutgoingMessageEntity>

    @Query(
        """
        SELECT * FROM outgoing_messages
        WHERE campaignId = :campaignId
        ORDER BY createdAt ASC, id ASC
        """,
    )
    suspend fun loadForCampaign(campaignId: Long): List<OutgoingMessageEntity>

    @Query(
        """
        UPDATE outgoing_messages
        SET status = :status,
            retryCount = :retryCount,
            updatedAt = :updatedAt,
            lastAttemptAt = :lastAttemptAt,
            lastError = :lastError,
            correlationId = :correlationId
        WHERE id = :id
        """,
    )
    suspend fun updateStatus(
        id: Long,
        status: String,
        retryCount: Int,
        updatedAt: Long,
        lastAttemptAt: Long?,
        lastError: String,
        correlationId: String?,
    )

    @Query("SELECT COUNT(*) FROM outgoing_messages WHERE siteProfileId = :siteProfileId AND status = 'PENDING'")
    suspend fun countPending(siteProfileId: String): Int

    @Query("SELECT COUNT(*) FROM outgoing_messages WHERE siteProfileId = :siteProfileId AND status = 'RETRY_WAITING'")
    suspend fun countRetryWaiting(siteProfileId: String): Int

    @Query("SELECT COUNT(*) FROM outgoing_messages WHERE siteProfileId = :siteProfileId AND status = 'BLOCKED'")
    suspend fun countBlocked(siteProfileId: String): Int

    @Query(
        """
        UPDATE outgoing_messages
        SET status = :status, updatedAt = :updatedAt, lastError = :lastError
        WHERE id = :id
        """,
    )
    suspend fun markBlocked(id: Long, status: String, updatedAt: Long, lastError: String)
}
