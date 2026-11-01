package com.hudhud.app.domain.send

import com.hudhud.app.data.local.CampaignMessageEntity
import com.hudhud.app.data.local.OutgoingMessageRepository
import com.hudhud.app.data.remote.RemoteDispatchRepository

/**
 * Coordinates local campaign rows into the delivery queue and triggers the next dispatch cycle.
 *
 * This layer stays intentionally behind the send gate so a campaign cannot be passed to the
 * remote platform before the necessary approval, config, and credential checks succeed.
 */
class DeliveryQueueCoordinator(
    private val outgoingMessageRepository: OutgoingMessageRepository,
    private val remoteDispatchRepository: RemoteDispatchRepository,
    private val queueProcessor: DeliveryQueueProcessor = DeliveryQueueProcessor(
        appSettings = remoteDispatchRepository.appSettings,
        sendGuard = remoteDispatchRepository.sendGuard,
        dispatchExecutor = remoteDispatchRepository,
        featureEnabled = { false },
    ),
    private val queueScheduler: DeliveryQueueScheduler = DeliveryQueueScheduler(),
) {
    suspend fun enqueueCampaign(
        campaignId: Long,
        siteProfileId: String,
        messages: List<CampaignMessageEntity>,
        payloadFactory: (CampaignMessageEntity) -> String = { it.text },
    ): List<Long> = outgoingMessageRepository.queueCampaign(
        campaignId = campaignId,
        siteProfileId = siteProfileId,
        messages = messages,
        payloadFactory = payloadFactory,
    )

    suspend fun processNext(siteProfileId: String, maxItems: Int = 50): RemoteDispatchRepository.DispatchSummary =
        remoteDispatchRepository.dispatchNextBatch(siteProfileId, maxItems)

    fun schedulePolling(
        siteProfileId: String,
        maxItems: Int = 50,
        intervalMs: Long = 60_000L,
    ) {
        queueScheduler.schedule(siteProfileId, maxItems, intervalMs) { profileId, batchSize ->
            queueProcessor.processNext(profileId, batchSize)
        }
    }

    fun cancelPolling(siteProfileId: String) = queueScheduler.cancel(siteProfileId)
    fun cancelAllPolling() = queueScheduler.cancelAll()
}
