package com.hudhud.app.data.ai

import com.hudhud.app.domain.model.AnalysisStatistics
import com.hudhud.app.domain.model.MessageDraft
import com.hudhud.app.domain.model.SmartSuggestion
import com.hudhud.app.domain.model.SuggestionAction
import com.hudhud.app.domain.model.SuggestionType

/**
 * Generates proactive warnings before sending messages.
 * Part of the Smart AI engine.
 */
class SmartWarnings {

    // Thresholds for warnings
    private val largeBatchThreshold = 1000
    private val duplicatePhoneThreshold = 0.1f // 10% duplicates
    private val emptyMessageThreshold = 0.05f // 5% empty
    private val invalidPhoneThreshold = 0.1f // 10% invalid
    private val highErrorRateThreshold = 0.2f // 20% errors
    private val lowSuccessRateThreshold = 0.7f // Less than 70% success

    /**
     * Generate all warnings based on message statistics
     * @param messages List of messages to analyze
     * @param statistics Pre-calculated statistics
     * @return List of warning suggestions
     */
    fun generateWarnings(
        messages: List<MessageDraft>,
        statistics: AnalysisStatistics,
    ): List<SmartSuggestion> {
        
        val warnings = mutableListOf<SmartSuggestion>()

        // Check batch size
        if (statistics.totalCount > largeBatchThreshold) {
            warnings.add(
                SmartSuggestion(
                    id = "batch_large",
                    type = SuggestionType.BATCH_WARNING,
                    title = "Large batch size",
                    description = "This batch contains ${statistics.totalCount} messages. " +
                            "Processing and sending may take a long time.",
                    confidence = 0.9f,
                    action = SuggestionAction.CONTINUE,
                    metadata = mapOf("count" to statistics.totalCount),
                )
            )
        }

        // Check for duplicate phones
        if (statistics.duplicateCount > 0) {
            val duplicateRate = statistics.duplicateCount.toFloat() / statistics.totalCount
            if (duplicateRate >= duplicatePhoneThreshold) {
                warnings.add(
                    SmartSuggestion(
                        id = "duplicate_phones",
                        type = SuggestionType.MESSAGE_DUPLICATE,
                        title = "High number of duplicate phones",
                        description = "${statistics.duplicateCount} messages have duplicate phone numbers " +
                                "(${String.format("%.1f", duplicateRate * 100)}% of total)",
                        confidence = 0.85f,
                        action = SuggestionAction.CONTINUE,
                        metadata = mapOf(
                            "count" to statistics.duplicateCount,
                            "rate" to duplicateRate,
                        ),
                    )
                )
            }
        }

        // Check for invalid phones
        if (statistics.invalidPhoneCount > 0) {
            val invalidRate = statistics.invalidPhoneCount.toFloat() / statistics.totalCount
            if (invalidRate >= invalidPhoneThreshold) {
                warnings.add(
                    SmartSuggestion(
                        id = "invalid_phones",
                        type = SuggestionType.PHONE_INVALID,
                        title = "High number of invalid phones",
                        description = "${statistics.invalidPhoneCount} messages have invalid phone numbers " +
                                "(${String.format("%.1f", invalidRate * 100)}% of total)",
                        confidence = 0.95f,
                        action = SuggestionAction.CONTINUE,
                        metadata = mapOf(
                            "count" to statistics.invalidPhoneCount,
                            "rate" to invalidRate,
                        ),
                    )
                )
            }
        }

        // Check for missing values
        if (statistics.missingValueCount > 0) {
            val missingRate = statistics.missingValueCount.toFloat() / statistics.totalCount
            warnings.add(
                SmartSuggestion(
                    id = "missing_values",
                    type = SuggestionType.VALUE_MISSING,
                    title = "Messages with missing values",
                    description = "${statistics.missingValueCount} messages have missing template variables " +
                            "(${String.format("%.1f", missingRate * 100)}% of total)",
                    confidence = 0.95f,
                    action = SuggestionAction.CONTINUE,
                    metadata = mapOf(
                        "count" to statistics.missingValueCount,
                        "rate" to missingRate,
                    ),
                )
            )
        }

        // Check for empty messages
        if (statistics.emptyMessages > 0) {
            val emptyRate = statistics.emptyMessages.toFloat() / messages.size
            if (emptyRate >= emptyMessageThreshold) {
                warnings.add(
                    SmartSuggestion(
                        id = "empty_messages",
                        type = SuggestionType.VALUE_MISSING,
                        title = "Empty messages detected",
                        description = "${statistics.emptyMessages} messages are empty " +
                                "(${String.format("%.1f", emptyRate * 100)}% of total)",
                        confidence = 0.95f,
                        action = SuggestionAction.CONTINUE,
                        metadata = mapOf("count" to statistics.emptyMessages),
                    )
                )
            }
        }

        // Check overall success rate
        val successRate = if (statistics.totalCount > 0) {
            (statistics.totalCount - statistics.errorCount).toFloat() / statistics.totalCount
        } else 0f

        if (successRate < lowSuccessRateThreshold) {
            warnings.add(
                SmartSuggestion(
                    id = "low_success_rate",
                    type = SuggestionType.BATCH_WARNING,
                    title = "Low success rate",
                    description = "Only ${String.format("%.1f", successRate * 100)}% of messages are valid. " +
                            "Consider fixing the errors before sending.",
                    confidence = 0.9f,
                    action = SuggestionAction.CONTINUE,
                    metadata = mapOf(
                        "successRate" to successRate,
                        "errorCount" to statistics.errorCount,
                    ),
                )
            )
        }

        // Check for long messages
        if (statistics.longMessageCount > 0) {
            warnings.add(
                SmartSuggestion(
                    id = "long_messages",
                    type = SuggestionType.MESSAGE_LONG,
                    title = "Long messages detected",
                    description = "${statistics.longMessageCount} messages exceed normal SMS length " +
                            "and will be sent as multi-part messages.",
                    confidence = 0.8f,
                    action = SuggestionAction.CONTINUE,
                    metadata = mapOf("count" to statistics.longMessageCount),
                )
            )
        }

        return warnings
    }

    /**
     * Generate warnings based on message content
     */
    fun analyzeContentWarnings(messages: List<MessageDraft>): List<SmartSuggestion> {
        val warnings = mutableListOf<SmartSuggestion>()
        
        // Check for suspicious content
        messages.forEachIndexed { index, message ->
            val contentWarnings = analyzeSingleMessageContent(message, index)
            warnings.addAll(contentWarnings)
        }
        
        return warnings
    }

    /**
     * Analyze single message content for warnings
     */
    private fun analyzeSingleMessageContent(
        message: MessageDraft,
        index: Int,
    ): List<SmartSuggestion> {
        
        val warnings = mutableListOf<SmartSuggestion>()
        
        // Check for URLs without proper context
        if (message.text.contains("http://") || message.text.contains("https://")) {
            if (!message.text.contains("www.") && !message.text.contains(".com") && 
                !message.text.contains(".org") && !message.text.contains(".net")) {
                warnings.add(
                    SmartSuggestion(
                        id = "url_$index",
                        type = SuggestionType.VALUE_SUGGESTION,
                        title = "Row ${index + 1}: Incomplete URL",
                        description = "Message contains what looks like an incomplete URL",
                        confidence = 0.6f,
                        action = SuggestionAction.CONTINUE,
                        metadata = mapOf("row" to index),
                    )
                )
            }
        }
        
        // Check for very short messages that might be test messages
        if (message.text.length in 1..5 && message.phone.isNotBlank()) {
            warnings.add(
                SmartSuggestion(
                    id = "short_$index",
                    type = SuggestionType.VALUE_SUGGESTION,
                    title = "Row ${index + 1}: Very short message",
                    description = "Message is very short (${message.text.length} chars)",
                    confidence = 0.5f,
                    action = SuggestionAction.CONTINUE,
                    metadata = mapOf("row" to index, "length" to message.text.length),
                )
            )
        }
        
        return warnings
    }

    /**
     * Check if there are critical errors that should block sending
     */
    fun hasBlockingIssues(messages: List<MessageDraft>): Boolean {
        val validMessages = messages.count { it.isValid }
        return validMessages == 0
    }

    /**
     * Get summary of warnings for display
     */
    fun getWarningSummary(warnings: List<SmartSuggestion>): WarningSummary {
        val byType = warnings.groupBy { it.type }
        
        return WarningSummary(
            totalWarnings = warnings.size,
            criticalWarnings = warnings.count { it.confidence > 0.9f },
            batchWarnings = byType[SuggestionType.BATCH_WARNING]?.size ?: 0,
            duplicateWarnings = byType[SuggestionType.MESSAGE_DUPLICATE]?.size ?: 0,
            phoneWarnings = byType[SuggestionType.PHONE_INVALID]?.size ?: 0,
            valueWarnings = byType[SuggestionType.VALUE_MISSING]?.size ?: 0,
            lengthWarnings = byType[SuggestionType.MESSAGE_LONG]?.size ?: 0,
        )
    }

    /**
     * Warning summary data class
     */
    data class WarningSummary(
        val totalWarnings: Int,
        val criticalWarnings: Int,
        val batchWarnings: Int,
        val duplicateWarnings: Int,
        val phoneWarnings: Int,
        val valueWarnings: Int,
        val lengthWarnings: Int,
    ) {
        val hasCriticalIssues: Boolean
            get() = criticalWarnings > 0 || totalWarnings > 5
        
        val shouldBlockSending: Boolean
            get() = phoneWarnings > 10 || valueWarnings > 20
    }
}