package com.hudhud.app.domain.send

import com.hudhud.app.data.local.AppSettingsStore
import com.hudhud.app.data.remote.RemoteDispatchRepository

fun interface DispatchBatchExecutor {
    suspend fun dispatchNextBatch(siteProfileId: String, maxItems: Int): RemoteDispatchRepository.DispatchSummary
}

/**
 * Executes the next outbound queue cycle while respecting the send gate and the feature flag.
 *
 * Sending remains intentionally disabled by default until the platform contract is approved.
 */
class DeliveryQueueProcessor(
    private val appSettings: AppSettingsStore,
    private val sendGuard: PlatformSendGuard,
    private val dispatchExecutor: DispatchBatchExecutor,
    private val featureEnabled: () -> Boolean = { false },
) {
    suspend fun processNext(siteProfileId: String? = null, maxItems: Int = 50): DeliveryProcessingReport {
        val targetProfileId = siteProfileId ?: appSettings.getActiveProfile().id

        if (!featureEnabled()) {
            return DeliveryProcessingReport(
                siteProfileId = targetProfileId,
                attempted = 0,
                sent = 0,
                blocked = 0,
                detail = "Remote sending is disabled by feature flag.",
            )
        }

        val gate = sendGuard.evaluate(targetProfileId)
        if (!gate.canProceed) {
            return DeliveryProcessingReport(
                siteProfileId = targetProfileId,
                attempted = 0,
                sent = 0,
                blocked = 0,
                detail = gate.detail ?: "Delivery blocked by send gate.",
            )
        }

        val result = dispatchExecutor.dispatchNextBatch(targetProfileId, maxItems)
        return DeliveryProcessingReport(
            siteProfileId = targetProfileId,
            attempted = result.attempted,
            sent = result.sent,
            blocked = result.blocked,
            detail = result.details,
        )
    }
}

data class DeliveryProcessingReport(
    val siteProfileId: String,
    val attempted: Int,
    val sent: Int,
    val blocked: Int,
    val detail: String,
)
