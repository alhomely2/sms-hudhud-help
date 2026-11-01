package com.hudhud.app.data.ai

import com.hudhud.app.domain.model.SmartSuggestion
import com.hudhud.app.domain.model.SuggestionAction
import com.hudhud.app.domain.model.SuggestionType

/**
 * Provides advice on rate limiting for sending messages.
 * Helps prevent rate limiting issues with the SMS platform.
 */
class RateLimitAdvisor {

    // Recommended limits based on platform constraints
    private val recommendedBatchSize = 500
    private val recommendedDelayMs = 100L // Delay between messages
    private val maxConcurrentConnections = 3

    /**
     * Analyze batch size and provide recommendations
     */
    fun analyzeRateLimit(
        totalMessages: Int,
        estimatedDelayPerMessage: Long = 50,
    ): RateLimitAdvice {
        
        val timeToCompleteMs = totalMessages * estimatedDelayPerMessage
        val timeToCompleteMinutes = timeToCompleteMs / 60000

        // Calculate recommended actions
        val shouldSplitBatch = totalMessages > recommendedBatchSize
        val recommendedBatches = if (shouldSplitBatch) {
            (totalMessages / recommendedBatchSize) + 1
        } else 1

        val estimatedTimeMinutes = if (shouldSplitBatch) {
            (recommendedBatches * recommendedBatchSize * estimatedDelayPerMessage) / 60000
        } else {
            timeToCompleteMinutes
        }

        val needsDelay = estimatedTimeMinutes > 5 // More than 5 minutes

        return RateLimitAdvice(
            totalMessages = totalMessages,
            shouldSplitBatch = shouldSplitBatch,
            recommendedBatches = recommendedBatches,
            recommendedDelayMs = recommendedDelayMs,
            estimatedTimeMinutes = estimatedTimeMinutes.toInt(),
            needsDelay = needsDelay,
            warnings = generateWarnings(totalMessages, shouldSplitBatch, recommendedBatches),
        )
    }

    /**
     * Generate warnings based on rate limit analysis
     */
    private fun generateWarnings(
        totalMessages: Int,
        shouldSplitBatch: Boolean,
        recommendedBatches: Int,
    ): List<SmartSuggestion> {
        
        val warnings = mutableListOf<SmartSuggestion>()

        if (shouldSplitBatch) {
            warnings.add(
                SmartSuggestion(
                    id = "rate_limit_batch",
                    type = SuggestionType.RATE_LIMIT,
                    title = "Consider splitting the batch",
                    description = "With $totalMessages messages, it's recommended to split into $recommendedBatches batches " +
                            "to avoid rate limiting issues.",
                    confidence = 0.85f,
                    action = SuggestionAction.CONTINUE,
                    metadata = mapOf(
                        "totalMessages" to totalMessages,
                        "recommendedBatches" to recommendedBatches,
                    ),
                )
            )
        }

        if (totalMessages > 2000) {
            warnings.add(
                SmartSuggestion(
                    id = "rate_limit_large",
                    type = SuggestionType.RATE_LIMIT,
                    title = "Very large batch",
                    description = "Sending $totalMessages messages at once may trigger rate limits. " +
                            "Consider using batch sending with delays.",
                    confidence = 0.95f,
                    action = SuggestionAction.CONTINUE,
                    metadata = mapOf("totalMessages" to totalMessages),
                )
            )
        }

        return warnings
    }

    /**
     * Calculate optimal delay between messages
     */
    fun calculateOptimalDelay(
        totalMessages: Int,
        maxTimeMinutes: Int = 30,
    ): Long {
        if (totalMessages <= 0) return 0
        
        val maxTimeMs = maxTimeMinutes * 60 * 1000L
        val delay = maxTimeMs / totalMessages
        
        // Clamp to reasonable range
        return delay.coerceIn(50, 5000)
    }

    /**
     * Get recommended batch configuration
     */
    fun getRecommendedConfig(messageCount: Int): BatchConfig {
        return when {
            messageCount <= 100 -> BatchConfig(
                batchSize = messageCount,
                delayBetweenBatches = 0,
                delayBetweenMessages = 0,
            )
            messageCount <= 500 -> BatchConfig(
                batchSize = 100,
                delayBetweenBatches = 5000,
                delayBetweenMessages = 50,
            )
            messageCount <= 1000 -> BatchConfig(
                batchSize = 200,
                delayBetweenBatches = 10000,
                delayBetweenMessages = 100,
            )
            else -> BatchConfig(
                batchSize = 300,
                delayBetweenBatches = 15000,
                delayBetweenMessages = 150,
            )
        }
    }

    /**
     * Estimate SMS parts for a message
     */
    fun estimateSmsParts(messageText: String): Int {
        val length = messageText.length
        
        return when {
            length <= 160 -> 1
            length <= 306 -> 2 // 160 + 146
            length <= 459 -> 3 // 306 + 153
            length <= 612 -> 4
            length <= 765 -> 5
            else -> (length / 153) + 1
        }
    }

    /**
     * Calculate total SMS parts for all messages
     */
    fun calculateTotalSmsParts(messages: List<String>): Int {
        return messages.sumOf { estimateSmsParts(it) }
    }

    /**
     * Data class for rate limit advice
     */
    data class RateLimitAdvice(
        val totalMessages: Int,
        val shouldSplitBatch: Boolean,
        val recommendedBatches: Int,
        val recommendedDelayMs: Long,
        val estimatedTimeMinutes: Int,
        val needsDelay: Boolean,
        val warnings: List<SmartSuggestion>,
    )

    /**
     * Data class for batch configuration
     */
    data class BatchConfig(
        val batchSize: Int,
        val delayBetweenBatches: Long,
        val delayBetweenMessages: Long,
    ) {
        val totalBatches: Int
            get() = if (batchSize > 0) batchSize else 1
    }
}