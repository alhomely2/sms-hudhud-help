package com.hudhud.app.data.local

import com.hudhud.app.data.document.XlsxReadyMessageExtractor
import java.io.InputStream

class ReadyCampaignRepository(
    private val campaignDao: CampaignDao,
    private val extractor: XlsxReadyMessageExtractor = XlsxReadyMessageExtractor(),
) {
    suspend fun createFromWorkbook(
        name: String,
        input: InputStream,
        phoneColumnName: String,
        messageColumnName: String,
    ): CampaignEntity = CampaignStorage(campaignDao).create(name) { onMessage ->
        extractor.extractAll(
                input = input,
                phoneColumnName = phoneColumnName,
                messageColumnName = messageColumnName,
            onMessage = onMessage,
        )
    }
}
