package com.hudhud.app.domain.settings

/**
 * Predefined transmission profiles providing safe delay and retry parameters
 * for connecting to platform endpoints.
 */
enum class ThrottlePreset(
    val delayMs: Long,
    val maxRetries: Int,
) {
    /** Gentle rate: 5s delay, 2 retries. Recommended for low-bandwidth or strict limits. */
    QUIET(delayMs = 5000L, maxRetries = 2),

    /** Balanced default: 2s delay, 3 retries. Safe baseline for most workflows. */
    BALANCED(delayMs = 2000L, maxRetries = 3),

    /** Faster rate: 1s delay, 3 retries. For high-throughput environments. */
    FAST(delayMs = 1000L, maxRetries = 3),

    /** User-defined delay and retries. */
    CUSTOM(delayMs = 2000L, maxRetries = 3);

    companion object {
        fun fromValues(delayMs: Long, maxRetries: Int): ThrottlePreset {
            return entries.firstOrNull { it != CUSTOM && it.delayMs == delayMs && it.maxRetries == maxRetries }
                ?: CUSTOM
        }
    }
}
