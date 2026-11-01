package com.hudhud.app.data.local

import com.hudhud.app.data.document.XlsxTemplateMessageExtractor
import com.hudhud.app.domain.model.MessageTemplate
import java.io.InputStream

class TemplateCampaignRepository(
    private val campaignStorage: CampaignStorage,
    private val extractor: XlsxTemplateMessageExtractor = XlsxTemplateMessageExtractor(),
) {
    suspend fun createFromWorkbook(
        name: String,
        input: InputStream,
        template: MessageTemplate,
    ): CampaignEntity = campaignStorage.create(name) { onMessage ->
        extractor.extractAll(input, template, onMessage)
    }

    suspend fun prepareDraftFromWorkbook(
        name: String,
        input: InputStream,
        template: MessageTemplate,
    ): CampaignEntity = campaignStorage.createDraft(name) { onMessage ->
        extractor.extractAll(input, template, onMessage)
    }

    suspend fun loadDraftMessages(
        campaignId: Long,
        page: Int,
        pageSize: Int,
    ): List<CampaignMessageEntity> =
        campaignStorage.loadMessagesPage(campaignId, page, pageSize)

    suspend fun loadDraft(campaignId: Long): CampaignEntity? =
        campaignStorage.loadDraft(campaignId)

    suspend fun confirmDraft(campaignId: Long) =
        campaignStorage.finalizeDraft(campaignId)

    suspend fun discardDraft(campaignId: Long) =
        campaignStorage.deleteDraft(campaignId)
}
