package com.hudhud.app.domain.settings

import java.io.IOException
import javax.net.ssl.SSLException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RequestThrottlePolicyTest {

    @Test
    fun `isRetryable returns true for network and transient codes only`() {
        // Network failures
        assertTrue(RequestThrottlePolicy.isRetryable(null, IOException("Connection reset")))
        assertFalse(RequestThrottlePolicy.isRetryable(null, SSLException("certificate validation failed")))

        // Transient HTTP status codes
        assertTrue(RequestThrottlePolicy.isRetryable(429))
        assertTrue(RequestThrottlePolicy.isRetryable(502))
        assertTrue(RequestThrottlePolicy.isRetryable(503))
        assertTrue(RequestThrottlePolicy.isRetryable(504))

        // Non-retryable client errors
        assertFalse(RequestThrottlePolicy.isRetryable(400))
        assertFalse(RequestThrottlePolicy.isRetryable(401))
        assertFalse(RequestThrottlePolicy.isRetryable(403))
        assertFalse(RequestThrottlePolicy.isRetryable(404))
        assertFalse(RequestThrottlePolicy.isRetryable(422))

        // Non-retryable 500 (internal platform bug)
        assertFalse(RequestThrottlePolicy.isRetryable(500))
    }

    @Test
    fun `calculateDelayMs respects server Retry-After when higher`() {
        val profile = SiteProfile(
            id = "test",
            name = "Test",
            baseUrl = "https://example.com",
            delayBetweenMessagesMs = 2000L,
        )

        // Without server header: uses configured delay
        assertEquals(2000L, RequestThrottlePolicy.calculateDelayMs(profile, null))
        assertEquals(2000L, RequestThrottlePolicy.calculateDelayMs(profile, 1L)) // 1s < 2s

        // When server asks for 10s: respects 10s
        assertEquals(10_000L, RequestThrottlePolicy.calculateDelayMs(profile, 10L))
    }

    @Test
    fun `backoffDelayMs calculates exponential backoff with upper bound`() {
        val baseDelay = 1000L
        assertEquals(1000L, RequestThrottlePolicy.backoffDelayMs(1, baseDelay))
        assertEquals(2000L, RequestThrottlePolicy.backoffDelayMs(2, baseDelay))
        assertEquals(4000L, RequestThrottlePolicy.backoffDelayMs(3, baseDelay))
        assertEquals(8000L, RequestThrottlePolicy.backoffDelayMs(4, baseDelay))

        // Capped at maxDelayMs
        assertEquals(15_000L, RequestThrottlePolicy.backoffDelayMs(10, baseDelay, maxDelayMs = 15_000L))
        assertEquals(30_000L, RequestThrottlePolicy.backoffDelayMs(10, Long.MAX_VALUE))
        assertEquals(0L, RequestThrottlePolicy.backoffDelayMs(2, -1))
    }

    @Test
    fun `calculateDelayMs saturates unreasonable server retry values`() {
        val profile = SiteProfile(
            id = "test",
            name = "Test",
            baseUrl = "https://example.com",
            delayBetweenMessagesMs = 2000L,
        )

        assertEquals(Long.MAX_VALUE, RequestThrottlePolicy.calculateDelayMs(profile, Long.MAX_VALUE))
    }

    @Test
    fun `parseRetryAfterSeconds parses integer format`() {
        assertEquals(45L, RequestThrottlePolicy.parseRetryAfterSeconds("45"))
        assertEquals(0L, RequestThrottlePolicy.parseRetryAfterSeconds("0"))
        assertNull(RequestThrottlePolicy.parseRetryAfterSeconds(null))
        assertNull(RequestThrottlePolicy.parseRetryAfterSeconds(""))
        assertNull(RequestThrottlePolicy.parseRetryAfterSeconds("-5"))
    }

    @Test
    fun `parseRetryAfterSeconds parses HTTP date format`() {
        val fixedNow = 1700000000000L // arbitrary fixed epoch
        // Date 60 seconds in the future
        val dateHeader = "Wed, 21 Oct 2026 07:28:00 GMT"
        val parsed = RequestThrottlePolicy.parseRetryAfterSeconds(dateHeader, currentTimeMs = fixedNow)
        // If date is in the past compared to now, returns 0L or positive diff
        assertTrue(parsed != null)
    }

    @Test
    fun `preset matching maps correctly`() {
        assertEquals(ThrottlePreset.QUIET, ThrottlePreset.fromValues(5000L, 2))
        assertEquals(ThrottlePreset.BALANCED, ThrottlePreset.fromValues(2000L, 3))
        assertEquals(ThrottlePreset.FAST, ThrottlePreset.fromValues(1000L, 3))
        assertEquals(ThrottlePreset.CUSTOM, ThrottlePreset.fromValues(3500L, 5))
    }
}
