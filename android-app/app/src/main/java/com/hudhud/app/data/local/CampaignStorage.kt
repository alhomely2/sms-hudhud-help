package com.hudhud.app.data.local

import com.hudhud.app.domain.model.MessageDraft
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CampaignStorage(private val campaignDao: CampaignDao) {
    suspend fun create(
        name: String,
        produceMessages: ((MessageDraft) -> Unit) -> Unit,
    ): CampaignEntity = create(name, draftOnly = false, produceMessages = produceMessages)

    suspend fun createDraft(
        name: String,
        produceMessages: ((MessageDraft) -> Unit) -> Unit,
    ): CampaignEntity = create(name, draftOnly = true, produceMessages = produceMessages)

    suspend fun loadMessagesPage(
        campaignId: Long,
        page: Int,
        pageSize: Int,
    ): List<CampaignMessageEntity> = withContext(Dispatchers.IO) {
        require(page >= 0)
        require(pageSize > 0)
        campaignDao.loadMessagesPage(campaignId, pageSize, page * pageSize)
    }

    suspend fun loadDraft(campaignId: Long): CampaignEntity? = withContext(Dispatchers.IO) {
        campaignDao.loadDraft(campaignId)
    }

    suspend fun finalizeDraft(campaignId: Long): Unit = withContext(Dispatchers.IO) {
        check(campaignDao.finalizeDraft(campaignId) == 1) {
            "مسودة الحملة غير موجودة أو لم تعد قابلة للاعتماد."
        }
    }

    suspend fun deleteDraft(campaignId: Long): Unit = withContext(Dispatchers.IO) {
        campaignDao.deleteDraft(campaignId)
    }

    private suspend fun create(
        name: String,
        draftOnly: Boolean,
        produceMessages: ((MessageDraft) -> Unit) -> Unit,
    ): CampaignEntity = withContext(Dispatchers.IO) {
        val campaignId = campaignDao.insertCampaign(
            CampaignEntity(name = name, status = CampaignEntity.STATUS_PREPARING),
        )
        var completed = false
        try {
            var totalMessages = 0
            var validMessages = 0
            var reviewMessages = 0
            val batch = ArrayList<CampaignMessageEntity>(INSERT_BATCH_SIZE)
            produceMessages { draft ->
                totalMessages++
                if (draft.isValid) validMessages++ else reviewMessages++
                batch += CampaignMessageEntity(
                    campaignId = campaignId,
                    sourceRow = draft.sourceRow,
                    phone = draft.phone,
                    text = draft.text,
                    status = if (draft.isValid) {
                        CampaignMessageEntity.STATUS_READY
                    } else {
                        CampaignMessageEntity.STATUS_NEEDS_REVIEW
                    },
                    errors = draft.errors.joinToString("\n"),
                )
                if (batch.size == INSERT_BATCH_SIZE) {
                    campaignDao.insertMessages(batch.toList())
                    batch.clear()
                }
            }
            if (batch.isNotEmpty()) campaignDao.insertMessages(batch)
            check(totalMessages > 0) { "لا توجد رسائل في الملف المحدد." }

            val status = if (draftOnly) {
                CampaignEntity.STATUS_DRAFT_REVIEW
            } else if (reviewMessages == 0) {
                CampaignEntity.STATUS_READY_LOCAL
            } else {
                CampaignEntity.STATUS_NEEDS_REVIEW
            }
            campaignDao.completeCampaign(
                campaignId = campaignId,
                status = status,
                totalMessages = totalMessages,
                validMessages = validMessages,
                reviewMessages = reviewMessages,
            )
            completed = true
            CampaignEntity(
                id = campaignId,
                name = name,
                status = status,
                totalMessages = totalMessages,
                validMessages = validMessages,
                reviewMessages = reviewMessages,
            )
        } finally {
            if (!completed) campaignDao.deleteCampaign(campaignId)
        }
    }

    private companion object {
        /** Internal Room / SQLite batch insertion chunk size. Distinct from remote network batch size. */
        const val INSERT_BATCH_SIZE = 200
    }
}
