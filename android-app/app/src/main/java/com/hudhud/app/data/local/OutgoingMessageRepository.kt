package com.hudhud.app.data.local

class OutgoingMessageRepository(
    private val outgoingMessageDao: OutgoingMessageDao,
) {
    suspend fun queueCampaign(
        campaignId: Long,
        siteProfileId: String,
        messages: List<CampaignMessageEntity>,
        payloadFactory: (CampaignMessageEntity) -> String = { it.text },
    ): List<Long> {
        if (messages.isEmpty()) return emptyList()

        val now = System.currentTimeMillis()
        val rows = messages.map { message ->
            OutgoingMessageEntity(
                campaignId = campaignId,
                messageId = message.id,
                siteProfileId = siteProfileId,
                status = OutgoingMessageEntity.STATUS_PENDING,
                payloadJson = payloadFactory(message),
                createdAt = now,
                updatedAt = now,
            )
        }

        return outgoingMessageDao.insertAll(rows)
    }

    suspend fun loadPendingBatch(siteProfileId: String, limit: Int = 50): List<OutgoingMessageEntity> =
        outgoingMessageDao.loadPendingBatch(siteProfileId, limit)

    suspend fun markSent(id: Long, retryCount: Int, correlationId: String?) {
        val now = System.currentTimeMillis()
        outgoingMessageDao.updateStatus(
            id = id,
            status = OutgoingMessageEntity.STATUS_SENT,
            retryCount = retryCount,
            updatedAt = now,
            lastAttemptAt = now,
            lastError = "",
            correlationId = correlationId,
        )
    }

    suspend fun markBlocked(id: Long, reason: String) {
        outgoingMessageDao.markBlocked(
            id = id,
            status = OutgoingMessageEntity.STATUS_BLOCKED,
            updatedAt = System.currentTimeMillis(),
            lastError = reason,
        )
    }
}
