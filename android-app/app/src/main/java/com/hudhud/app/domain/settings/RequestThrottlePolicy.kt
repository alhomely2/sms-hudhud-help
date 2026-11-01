package com.hudhud.app.domain.settings

import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.net.ssl.SSLException
import kotlin.math.max
import kotlin.math.min

/**
 * Enforces rate limiting, backoff, and retry policies for platform HTTP requests.
 *
 * Implements requirements from SETTINGS-MANAGEMENT-PLAN.md:
 * - Respects server 429 and Retry-After signals.
 * - Restricts retries strictly to transient transport errors.
 * - Never retries authentication/client errors or ambiguous server failures.
 */
object RequestThrottlePolicy {

    private const val MAX_BACKOFF_MS = 30_000L

    /**
     * Determines whether an HTTP status code or failure is safe and eligible for retry.
     */
    fun isRetryable(httpCode: Int?, exception: Throwable? = null): Boolean {
        if (exception is SSLException) return false
        if (exception is IOException) {
            return true
        }
        if (httpCode == null) return false

        return when (httpCode) {
            429 -> true // Rate limit exceeded (transient)
            502, 503, 504 -> true // Bad Gateway / Service Unavailable / Gateway Timeout (transient)
            // 400, 401, 403, 404, 422 are permanent client/auth errors - NEVER retry
            // 500 is an unhandled internal crash (like the documented staging bug) - DO NOT retry blindly
            else -> false
        }
    }

    /**
     * Calculates the required delay before sending a request, respecting both
     * user configuration and server Retry-After directives.
     */
    fun calculateDelayMs(
        profile: SiteProfile,
        serverRetryAfterSeconds: Long? = null,
    ): Long {
        val configuredDelay = profile.delayBetweenMessagesMs.coerceAtLeast(0)
        val serverDelay = serverRetryAfterSeconds
            ?.takeIf { it > 0 }
            ?.let { seconds -> if (seconds > Long.MAX_VALUE / 1000) Long.MAX_VALUE else seconds * 1000 }
            ?: 0L
        return max(configuredDelay, serverDelay)
    }

    /**
     * Computes exponential backoff for successive retry attempts.
     */
    fun backoffDelayMs(
        attempt: Int,
        baseDelayMs: Long,
        maxDelayMs: Long = MAX_BACKOFF_MS,
    ): Long {
        val safeBaseDelayMs = baseDelayMs.coerceAtLeast(0)
        val safeMaxDelayMs = maxDelayMs.coerceAtLeast(0)
        if (attempt <= 1) return min(safeBaseDelayMs, safeMaxDelayMs)
        val shift = min(attempt - 1, 10)
        val multiplier = 1L shl shift
        if (safeBaseDelayMs > safeMaxDelayMs / multiplier) return safeMaxDelayMs
        return min(safeBaseDelayMs * multiplier, safeMaxDelayMs)
    }

    /**
     * Parses the Retry-After header, which may be an integer number of seconds
     * or an HTTP date format string (RFC 1123).
     */
    fun parseRetryAfterSeconds(headerValue: String?, currentTimeMs: Long = System.currentTimeMillis()): Long? {
        val trimmed = headerValue?.trim() ?: return null
        if (trimmed.isEmpty()) return null

        // 1. Try parsing as integer seconds
        trimmed.toLongOrNull()?.let { seconds ->
            return if (seconds >= 0) seconds else null
        }

        // 2. Try parsing as HTTP date format
        return try {
            val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT")
            }
            val date = format.parse(trimmed) ?: return null
            val diffMs = date.time - currentTimeMs
            if (diffMs > 0) (diffMs + 999) / 1000 else 0L
        } catch (e: Exception) {
            null
        }
    }
}
