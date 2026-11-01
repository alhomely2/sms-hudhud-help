package com.hudhud.app.domain.send

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class DeliveryQueueSchedulerTest {
    @Test
    fun `scheduler polls queued dispatches until cancelled`() = runBlocking {
        val scheduler = DeliveryQueueScheduler(this)
        val calls = AtomicInteger(0)

        scheduler.schedule(
            siteProfileId = "site-1",
            maxItems = 5,
            intervalMs = 1L,
        ) { _, _ ->
            calls.incrementAndGet()
            DeliveryProcessingReport(
                siteProfileId = "site-1",
                attempted = 0,
                sent = 0,
                blocked = 0,
                detail = "polled",
            )
        }

        delay(20)
        scheduler.cancel("site-1")

        assertTrue(calls.get() > 1)
    }
}
