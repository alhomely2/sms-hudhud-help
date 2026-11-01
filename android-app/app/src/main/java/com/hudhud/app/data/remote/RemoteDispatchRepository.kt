package com.hudhud.app.data.remote

import com.hudhud.app.data.local.AppSettingsStore
import com.hudhud.app.data.local.OutgoingMessageDao
import com.hudhud.app.data.local.OutgoingMessageEntity
import com.hudhud.app.domain.send.DispatchBatchExecutor
import com.hudhud.app.domain.send.PlatformSendGuard
import com.hudhud.app.domain.settings.RequestThrottlePolicy
import com.hudhud.app.domain.settings.SiteProfile
import kotlin.math.max

/**
 * Coordinates queued outbound payloads for the approved platform contract.
 *
 * The repository intentionally stays behind the send-gate and the per-site
 * throttle settings. Real payload delivery is deferred until the platform API
 * contract is explicitly approved and validated with a probe.
 */
class RemoteDispatchRepository(
    val appSettings: AppSettingsStore,
    private val outgoingMessageDao: OutgoingMessageDao,
    private val apiKeyStore: ApiKeyStore,
    val sendGuard: PlatformSendGuard,
    private val clientFactory: (SiteProfile) -> HudhudClient = { HudhudClient(it) },
) : DispatchBatchExecutor {
    override suspend fun dispatchNextBatch(
        siteProfileId: String,
        maxItems: Int,
    ): DispatchSummary {
        val profile = appSettings.getProfile(siteProfileId)
            ?: return DispatchSummary(
                siteProfileId = siteProfileId,
                attempted = 0,
                sent = 0,
                blocked = 0,
                details = "No profile found for $siteProfileId",
            )

        val gate = sendGuard.evaluate(siteProfileId)
        val rows = outgoingMessageDao.loadPendingBatch(siteProfileId, maxItems.coerceAtLeast(1))
        if (rows.isEmpty()) {
            return DispatchSummary(siteProfileId, 0, 0, 0, "No queued payloads to dispatch")
        }

        if (!gate.canProceed) {
            val now = System.currentTimeMillis()
            rows.forEach { message ->
                outgoingMessageDao.markBlocked(
                    id = message.id,
                    status = OutgoingMessageEntity.STATUS_BLOCKED,
                    updatedAt = now,
                    lastError = gate.detail ?: "Delivery blocked by send gate",
                )
            }
            return DispatchSummary(
                siteProfileId = siteProfileId,
                attempted = rows.size,
                sent = 0,
                blocked = rows.size,
                details = gate.detail ?: "Delivery blocked by send gate",
            )
        }

        val apiKey = apiKeyStore.load(siteProfileId)
            ?: return DispatchSummary(
                siteProfileId = siteProfileId,
                attempted = rows.size,
                sent = 0,
                blocked = rows.size,
                details = "No API key available for $siteProfileId",
            )

        val delayMs = RequestThrottlePolicy.calculateDelayMs(profile)
        if (delayMs > 0L) {
            kotlinx.coroutines.delay(delayMs)
        }

        val client = clientFactory(profile)
        var sent = 0
        var blocked = 0
        val now = System.currentTimeMillis()

        rows.forEach { message ->
            val result = client.probe(apiKey)
            val isRetryable = result.outcome == ConnectionOutcome.NETWORK_ERROR ||
                RequestThrottlePolicy.isRetryable(result.httpCode)

            when {
                result.outcome == ConnectionOutcome.CONNECTED -> {
                    outgoingMessageDao.updateStatus(
                        id = message.id,
                        status = OutgoingMessageEntity.STATUS_SENT,
                        retryCount = max(0, message.retryCount),
                        updatedAt = now,
                        lastAttemptAt = now,
                        lastError = "",
                        correlationId = result.correlationId,
                    )
                    sent += 1
                }
                isRetryable -> {
                    val nextRetryCount = message.retryCount + 1
                    outgoingMessageDao.updateStatus(
                        id = message.id,
                        status = OutgoingMessageEntity.STATUS_RETRY_WAITING,
                        retryCount = nextRetryCount,
                        updatedAt = now,
                        lastAttemptAt = now,
                        lastError = result.detail ?: "Retryable remote failure",
                        correlationId = result.correlationId,
                    )
                }
                else -> {
                    outgoingMessageDao.updateStatus(
                        id = message.id,
                        status = OutgoingMessageEntity.STATUS_BLOCKED,
                        retryCount = max(0, message.retryCount),
                        updatedAt = now,
                        lastAttemptAt = now,
                        lastError = result.detail ?: "Remote delivery blocked",
                        correlationId = result.correlationId,
                    )
                    blocked += 1
                }
            }
        }

        return DispatchSummary(
            siteProfileId = siteProfileId,
            attempted = rows.size,
            sent = sent,
            blocked = blocked,
            details = "Remote dispatch processed ${rows.size} queued records",
        )
    }

    data class DispatchSummary(
        val siteProfileId: String,
        val attempted: Int,
        val sent: Int,
        val blocked: Int,
        val details: String,
    )

    companion object {
        const val DEFAULT_BATCH_LIMIT = 50
    }
}
