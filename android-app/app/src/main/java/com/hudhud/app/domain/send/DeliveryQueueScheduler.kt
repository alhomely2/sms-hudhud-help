package com.hudhud.app.domain.send

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Schedules periodic polling of the local outbound queue. This keeps the queue
 * processing loop active without bypassing the send gate or feature flag.
 */
class DeliveryQueueScheduler(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) {
    private val jobs = ConcurrentHashMap<String, Job>()

    fun schedule(
        siteProfileId: String,
        maxItems: Int = 50,
        intervalMs: Long = 60_000L,
        processor: suspend (siteProfileId: String, maxItems: Int) -> DeliveryProcessingReport,
    ) {
        cancel(siteProfileId)

        val job = scope.launch {
            while (isActive) {
                processor(siteProfileId, maxItems)
                delay(intervalMs)
            }
        }
        jobs[siteProfileId] = job
    }

    fun cancel(siteProfileId: String) {
        jobs.remove(siteProfileId)?.cancel()
    }

    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }

    fun shutdown() {
        cancelAll()
        scope.cancel()
    }
}
